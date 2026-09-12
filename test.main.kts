#!/usr/bin/env kotlin

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

val root = __FILE__.canonicalFile.parentFile
val selector = args.firstOrNull()
val taskBySelector = mapOf(
    "fast" to "test",
    "candidate" to "candidateTest",
    "server" to "serverTest",
    "multiplayer" to "multiplayerTest",
    "singleplayer" to "singleplayerTest",
)

fun usage(): Nothing {
    System.err.println("usage: ./test.main.kts <frugal|fast|candidate|server|multiplayer|singleplayer|all>")
    exitProcess(2)
}

if (selector == null || (selector !in taskBySelector && selector !in setOf("frugal", "all")) || args.size != 1) usage()
val selected = selector ?: usage()

if (selected !in setOf("frugal", "fast") && System.getenv("BC_PACK_TEST_LOCK_TOKEN").isNullOrBlank()) {
    val status = ProcessBuilder(
        root.resolve("pack-test-lock.main.kts").absolutePath,
        "run", "test", selected, "--", __FILE__.absolutePath, selected,
    ).directory(root).inheritIO().start().waitFor()
    exitProcess(status)
}

if (selected == "frugal") {
    fun run(vararg command: String): Int = ProcessBuilder(*command)
        .directory(root)
        .inheritIO()
        .start()
        .waitFor()

    println("frugal validation: refreshing Packwiz hashes")
    val refresh = run("packwiz", "refresh")
    if (refresh != 0) exitProcess(refresh)
    println("frugal validation: checking diff whitespace")
    exitProcess(run("git", "diff", "--check"))
}

val runId = if (selected == "fast") null else {
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

fun gradle(suite: String, task: String): Int {
    println("test suite: $task" + (runId?.let { " (run $it)" } ?: ""))
    val startedAt = System.currentTimeMillis()
    val process = ProcessBuilder(root.resolve("gradlew").absolutePath, "--no-daemon", task)
        .directory(root)
        .inheritIO()
        .apply {
            if (suite == "fast") environment().remove("BC_TEST_SELECTOR")
            else environment()["BC_TEST_SELECTOR"] = selected
            if (runId != null) environment()["BC_TEST_RUN_ID"] = runId
        }
        .start()
    val status = process.waitFor()
    if (status != 0 || suite == "fast") return status
    return if (validateFreshEvidence(suite, startedAt)) 0 else 1
}

val statuses = linkedMapOf<String, Int>()
if (selected == "all") {
    statuses["fast"] = gradle("fast", "test")
    statuses["candidate"] = gradle("candidate", "candidateTest")
    if (statuses.getValue("candidate") == 0) {
        listOf("server", "multiplayer", "singleplayer").forEach { name ->
            statuses[name] = gradle(name, taskBySelector.getValue(name))
        }
    } else {
        println("candidate validation failed; heavyweight suites were not started")
    }
} else {
    statuses[selected] = gradle(selected, taskBySelector.getValue(selected))
}

if (runId != null && evidence != null) {
    println("run: $runId")
    println("evidence: ${evidence.absolutePath}")
}
statuses.forEach { (name, status) -> println("$name: ${if (status == 0) "passed" else "failed ($status)"}") }
exitProcess(if (statuses.values.all { it == 0 }) 0 else 1)
