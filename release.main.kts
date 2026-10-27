#!/usr/bin/env kotlin

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

val root = __FILE__.canonicalFile.parentFile
val usage = "usage: ./release.main.kts [--jobs 1..4] [--soak-minutes N] [--use-bundled-mods]\n       ./release.main.kts [--jobs 1..4] --fresh-source-plan FILE\n       ./release.main.kts --finalize-visual PENDING_JSON --visual-review REVIEW_JSON"
var jobs = 2
var soakMinutes = 0
var useBundledMods = false
var freshSourcePlan: String? = null
var pendingVisual: String? = null
var visualReview: String? = null
val seen = mutableSetOf<String>()
var index = 0
while (index < args.size) {
    val option = args[index]
    if (option !in setOf("--jobs", "--soak-minutes", "--use-bundled-mods", "--fresh-source-plan", "--finalize-visual", "--visual-review") || !seen.add(option)) {
        System.err.println(usage)
        exitProcess(2)
    }
    if (option in setOf("--fresh-source-plan", "--finalize-visual", "--visual-review")) {
        val value = args.getOrNull(index + 1)
        if (value.isNullOrBlank() || value.startsWith("--")) {
            System.err.println(usage)
            exitProcess(2)
        }
        val path = java.io.File(value).canonicalPath
        when (option) {
            "--fresh-source-plan" -> freshSourcePlan = path
            "--finalize-visual" -> pendingVisual = path
            "--visual-review" -> visualReview = path
        }
        index += 2
        continue
    }
    if (option == "--use-bundled-mods") {
        useBundledMods = true
        index++
        continue
    }
    val value = args.getOrNull(index + 1)?.toIntOrNull()
    if (value == null || (option == "--jobs" && value !in 1..4) ||
        (option == "--soak-minutes" && value !in 1..10080)) {
        System.err.println(usage)
        exitProcess(2)
    }
    if (option == "--jobs") jobs = value else soakMinutes = value
    index += 2
}

if ((pendingVisual != null || visualReview != null) &&
    (pendingVisual == null || visualReview == null || seen != setOf("--finalize-visual", "--visual-review"))) {
    System.err.println("visual finalization requires both receipt paths and no build/release flags\n$usage")
    exitProcess(2)
}

if (freshSourcePlan != null && (useBundledMods || "--soak-minutes" in seen)) {
    System.err.println("fresh source release requires Smoke and fixed TeleSoak; bundled/ordinary Soak flags are forbidden")
    exitProcess(2)
}

if (System.getenv("BC_PACK_TEST_LOCK_TOKEN").isNullOrBlank()) {
    val command = listOf(root.resolve("pack-test-lock.main.kts").absolutePath,
        "run", "release", "smoke", "--", __FILE__.absolutePath) + args
    exitProcess(ProcessBuilder(command).directory(root).inheritIO().start().waitFor())
}

// Finalization only reads a previously sealed successful pair; never creates/reuses a world or repackages.
if (pendingVisual != null) {
    exitProcess(ProcessBuilder(root.resolve("gradlew").absolutePath, "--no-daemon", "finalizeFreshDist",
        "-PreleaseVisualPending=$pendingVisual", "-PreleaseVisualReview=$visualReview")
        .directory(root).inheritIO().start().waitFor())
}

// A release always owns a fresh identity; stale successful evidence cannot authorize it.
val runId = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    .format(Instant.now()) + "-" + ProcessHandle.current().pid()
val evidence = root.resolve("generated/test-evidence/$runId")
check(evidence.parentFile.mkdirs() || evidence.parentFile.isDirectory) { "cannot create evidence root" }
check(evidence.mkdir()) { "release evidence already exists or cannot be created: $evidence" }
evidence.resolve("release-request.txt").writeText(
    "run_id=$runId\njobs=$jobs\nsoak_minutes=$soakMinutes\nuse_bundled_mods=$useBundledMods\nfresh_source_plan=${freshSourcePlan ?: ""}\nstarted_at=${Instant.now()}\n")
val command = mutableListOf(root.resolve("gradlew").absolutePath, "--no-daemon", "prepareFreshDist",
    "-PreleaseJobs=$jobs", "-PreleaseSoakMinutes=$soakMinutes", "-PreleaseUseBundledMods=$useBundledMods")
freshSourcePlan?.let { command += "-PreleaseFreshSourcePlan=$it" }
val status = ProcessBuilder(command)
    .directory(root).inheritIO().apply { environment()["BC_TEST_RUN_ID"] = runId }.start().waitFor()
println("release run: $runId")
println("evidence: ${evidence.absolutePath}")
exitProcess(status)
