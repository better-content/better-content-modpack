#!/usr/bin/env kotlin

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

val root = __FILE__.canonicalFile.parentFile
val selector = args.firstOrNull()
var target: String? = null
var retryOf: String? = null
var existingCandidate = false
var argument = 1
while (argument < args.size) {
    when (args[argument]) {
        "--target" -> { target = args.getOrNull(argument + 1) ?: usage(); argument += 2 }
        "--retry-of" -> { retryOf = args.getOrNull(argument + 1) ?: usage(); argument += 2 }
        "--existing-candidate" -> { existingCandidate = true; argument++ }
        else -> usage()
    }
}
val fontTargets = setOf("ratlantis", "bumblezone", "aether", "nether")
val fontDimensions = setOf("rats:ratlantis", "the_bumblezone:the_bumblezone", "aether:the_aether", "minecraft:the_nether")
val validTarget = target == null || target in setOf("server-ready", "cursed-pyramid", "lineage-transition", "join", "fonts", "dimensions", "campaign-start", "campaign", "restart-compat", "world-save") ||
    (target!!.startsWith("font:") && target!!.removePrefix("font:") in fontTargets) ||
    (target!!.startsWith("dimension:") && Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(target!!.removePrefix("dimension:")) &&
        target!!.removePrefix("dimension:") !in fontDimensions)
val targetSuite = when {
    target == null -> null
    target == "server-ready" || target == "cursed-pyramid" || target == "lineage-transition" -> "server"
    target == "world-save" -> "singleplayer"
    else -> "multiplayer"
}
val targetMethod = when {
    target == null -> null
    target == "server-ready" -> "packagedServerReachesReadiness"
    target == "cursed-pyramid" -> "cursedPyramidSeededGenerationAndLogAudit"
    target == "lineage-transition" -> "oneLineageTransitionCommitsAndArchivesCleanly"
    target == "join" -> "leadClientJoinsFreshDedicatedServer"
    target == "campaign" || target == "campaign-start" -> "threeSurvivalPlayersStartCampaigns"
    target == "restart-compat" -> "debugServerRestartAndClientReconnectPreserveWorld"
    target == "world-save" -> "debugFreshWorldBootSaveAndReopen"
    target == "fonts" || target?.startsWith("font:") == true -> "debugNativeFontRoundTrips"
    else -> "everyFontAndCreatingSpaceDimensionStabilizesAtFreshLocations"
}
val targetEvidenceSuite = target?.let { "target-" + it.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-') }
val taskBySelector = mapOf(
    "candidate" to "candidateTest",
    "server" to "serverTest",
    "multiplayer" to "multiplayerTest",
    "singleplayer" to "singleplayerTest",
)

fun usage(): Nothing {
    System.err.println("usage: ./test.main.kts <dev|dist|debug> [--target server-ready|cursed-pyramid|lineage-transition|join|fonts|font:NAME|dimensions|dimension:ID|campaign-start|campaign|restart-compat|world-save] [--retry-of RUN_ID] [--existing-candidate]")
    exitProcess(2)
}

if (selector !in setOf("dev", "dist", "debug") || !validTarget ||
    (target != null && (selector == "dev" || (selector == "dist" && target != "join"))) ||
    (retryOf != null && target == null) ||
    (existingCandidate && (selector != "debug" || target != null || args.count { it == "--existing-candidate" } != 1)) ||
    (retryOf != null && !Regex("\\d{8}T\\d{6}Z-\\d+").matches(retryOf!!)) ||
    (retryOf != null && !root.resolve("generated/test-evidence/$retryOf").isDirectory)) usage()
val selected = selector ?: usage()

if (selected == "debug" && target == null && !existingCandidate) {
    exitProcess(ProcessBuilder(root.resolve("release.main.kts").absolutePath, "--debug")
        .directory(root).inheritIO().start().waitFor())
}

if (selected != "dev" && System.getenv("BC_PACK_TEST_LOCK_TOKEN").isNullOrBlank()) {
    val status = ProcessBuilder(
        root.resolve("pack-test-lock.main.kts").absolutePath,
        "run", "test", if (target == null) selected else "$selected:$target", "--", __FILE__.absolutePath, *args,
    ).directory(root).inheritIO().start().waitFor()
    exitProcess(status)
}

val runId = if (selected == "dev") null else {
    System.getenv("BC_TEST_RUN_ID")?.takeIf { it.isNotBlank() }
        ?: DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
            .format(java.time.Instant.now()) + "-" + ProcessHandle.current().pid()
}
val evidence = runId?.let { root.resolve("generated/test-evidence/$it") }
evidence?.mkdirs()

fun validateFreshEvidence(suite: String, startedAt: Long): Boolean {
    val expectedRunId = runId ?: return true
    val report = evidence!!.resolve("$suite/run.json")
    if (!report.isFile || report.lastModified() < startedAt) {
        System.err.println("$suite: Gradle returned success without fresh evidence at ${report.absolutePath}")
        return false
    }
    val document = report.readText()
    val expectedFields = mapOf("run_id" to expectedRunId, "suite" to suite, "status" to "passed")
    val mismatch = expectedFields.entries.firstOrNull { (name, value) ->
        !Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*\\\"${Regex.escape(value)}\\\"").containsMatchIn(document)
    }
    if (mismatch != null) {
        System.err.println("$suite: evidence does not report ${mismatch.key}=${mismatch.value}: ${report.absolutePath}")
        return false
    }
    return true
}

fun gradle(suite: String, task: String, method: String? = null, evidenceSuite: String = suite): Int {
    println("test suite: $task" + (runId?.let { " (run $it)" } ?: ""))
    val startedAt = System.currentTimeMillis()
    val command = mutableListOf(root.resolve("gradlew").absolutePath, "--no-daemon", task)
    if (method != null) {
        val testClass = when (suite) {
            "server" -> "ServerRuntimeTest"
            "multiplayer" -> "MultiplayerRuntimeTest"
            else -> "SingleplayerRuntimeTest"
        }
        command += listOf("--tests", "com.bettercontent.tests.$testClass.$method")
    }
    val process = ProcessBuilder(command)
        .directory(root)
        .inheritIO()
        .apply {
            if (suite == "fast") environment().remove("BC_TEST_SELECTOR")
            else environment()["BC_TEST_SELECTOR"] = suite
            environment()["BC_TEST_TIER"] = selected
            if (runId != null) environment()["BC_TEST_RUN_ID"] = runId
            if (method != null) {
                environment()["BC_TEST_TARGET"] = target!!
                environment()["BC_TEST_EVIDENCE_SUITE"] = targetEvidenceSuite!!
                retryOf?.let { environment()["BC_TEST_RETRY_OF"] = it }
            }
        }
        .start()
    val status = process.waitFor()
    if (status != 0 || suite == "fast") return status
    return if (validateFreshEvidence(evidenceSuite, startedAt)) 0 else 1
}

val statuses = linkedMapOf<String, Int>()
if (selected == "dev") {
    statuses["fast"] = gradle("fast", "test")
    if (statuses.getValue("fast") == 0) {
        statuses["diff"] = ProcessBuilder("git", "diff", "--check")
            .directory(root).inheritIO().start().waitFor()
    }
} else {
    statuses["fast"] = gradle("fast", "test")
    statuses["diff"] = ProcessBuilder("git", "diff", "--check")
        .directory(root).inheritIO().start().waitFor()
    if (statuses.values.all { it == 0 }) {
        statuses["candidate"] = gradle("candidate", "candidateTest")
        if (statuses.getValue("candidate") == 0) {
            val runtimeSuites = if (targetSuite != null) listOf(targetSuite) else if (selected == "debug") listOf("server", "multiplayer", "singleplayer")
                else listOf("multiplayer")
            for (name in runtimeSuites) {
                val evidenceSuite = if (targetSuite == name) targetEvidenceSuite!! else name
                statuses[evidenceSuite] = gradle(name, taskBySelector.getValue(name), if (targetSuite == name) targetMethod else null, evidenceSuite)
                if (selected == "dist" && statuses.getValue(evidenceSuite) != 0) break
            }
        } else {
            println("candidate validation failed; heavyweight suites were not started")
        }
    } else {
        println("Dev validation failed; candidate and heavyweight suites were not started")
    }
}

if (runId != null && evidence != null) {
    println("run: $runId")
    println("evidence: ${evidence.absolutePath}")
}
statuses.forEach { (name, status) -> println("$name: ${if (status == 0) "passed" else "failed ($status)"}") }
exitProcess(if (statuses.values.all { it == 0 }) 0 else 1)
