#!/usr/bin/env kotlin

import java.nio.file.Files
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

val root = __FILE__.canonicalFile.parentFile
if (args.firstOrNull() in setOf("list", "plan", "run", "submit", "status", "wait", "evidence", "retry")) {
    exitProcess(ProcessBuilder(root.resolve("test-control.main.kts").absolutePath, *args)
        .directory(root).inheritIO().start().waitFor())
}
val selector = args.firstOrNull()
var target: String? = null
var retryOf: String? = null
var existingCandidate = false
var verificationSession: String? = null
var candidateBinding: String? = null
var argument = 1
while (argument < args.size) {
    when (args[argument]) {
        "--target" -> { target = args.getOrNull(argument + 1) ?: usage(); argument += 2 }
        "--retry-of" -> { retryOf = args.getOrNull(argument + 1) ?: usage(); argument += 2 }
        "--existing-candidate" -> { existingCandidate = true; argument++ }
        "--verification-session" -> { verificationSession = args.getOrNull(argument + 1) ?: usage(); argument += 2 }
        "--candidate-binding" -> { candidateBinding = args.getOrNull(argument + 1) ?: usage(); argument += 2 }
        else -> usage()
    }
}
val fontTargets = setOf("bumblezone", "aether", "nether")
val fontDimensions = setOf("the_bumblezone:the_bumblezone", "aether:the_aether", "minecraft:the_nether")
val validTarget = target == null || target in setOf("server-ready", "cursed-pyramid", "lineage-transition", "join", "fonts", "dimensions", "campaign-start", "campaign", "restart", "restart-compat", "world-save") ||
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
    target == "restart" || target == "restart-compat" -> "debugServerRestartAndClientReconnectPreserveWorld"
    target == "world-save" -> "debugSeededWorldSaveAndReopen"
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
    System.err.println("usage: ./test.main.kts <dev|dist|debug> [--verification-session SESSION_ID] [--target TARGET] [--retry-of RUN_ID] [--candidate-binding /absolute/path/to/release/provenance.json] [--existing-candidate]; targets/retries require candidate binding or a pinned queue handoff")
    exitProcess(2)
}

if (selector !in setOf("dev", "dist", "debug") || !validTarget ||
    (target != null && (selector == "dev" || (selector == "dist" && target != "join"))) ||
    (retryOf != null && candidateBinding == null) ||
    (target != null && candidateBinding == null && System.getenv("BC_PACK_TEST_HANDOFF").isNullOrBlank()) ||
    (candidateBinding != null && (selector == "dev" || !java.io.File(candidateBinding!!).isFile)) ||
    (existingCandidate && (selector != "debug" || target != null || args.count { it == "--existing-candidate" } != 1)) ||
    (retryOf != null && !Regex("\\d{8}T\\d{6}Z-\\d+").matches(retryOf!!)) ||
    (retryOf != null && !root.resolve("generated/test-evidence/$retryOf").isDirectory)) usage()
val selected = selector ?: usage()
if (candidateBinding != null && selected == "debug" && target == null) existingCandidate = true
verificationSession?.let {
    if (!Regex("[a-f0-9]{32}").matches(it)) usage()
    val status = ProcessBuilder("python3", "-B", root.resolve("scripts/testing_session.py").absolutePath, "status", it)
        .directory(root).inheritIO().start().waitFor()
    if (status != 0) exitProcess(status)
}
val sessionArgs = verificationSession?.let { listOf("--verification-session", it) } ?: emptyList()

if (selected == "debug" && target == null && !existingCandidate) {
    exitProcess(ProcessBuilder(listOf(root.resolve("release.main.kts").absolutePath, "--debug") + sessionArgs)
        .directory(root).inheritIO().start().waitFor())
}

if (selected == "dist" && target == null && candidateBinding == null && System.getenv("BC_RELEASE_PREPARED") != "1") {
    exitProcess(ProcessBuilder(listOf(root.resolve("release.main.kts").absolutePath) + sessionArgs)
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
            if (suite == "fast") {
                environment().remove("BC_TEST_SELECTOR")
                environment().remove("BC_TEST_TIER")
            } else {
                environment()["BC_TEST_SELECTOR"] = suite
                environment()["BC_TEST_TIER"] = selected
            }
            if (runId != null) environment()["BC_TEST_RUN_ID"] = runId
            candidateBinding?.let { environment()["BC_CANDIDATE_BINDING"] = java.io.File(it).absolutePath }
            retryOf?.let { environment()["BC_TEST_RETRY_OF"] = it }
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
    var priorDist = if (selected == "debug" && existingCandidate) System.getenv("BC_PRIOR_DIST_RUN_ID") else null
    if (selected == "debug" && existingCandidate && priorDist == null) {
        val prerequisiteId = "${runId}1"
        val prerequisite = ProcessBuilder(listOf(__FILE__.absolutePath, "dist") +
            (candidateBinding?.let { listOf("--candidate-binding", it) } ?: emptyList()))
            .directory(root).inheritIO().apply {
                environment()["BC_RELEASE_PREPARED"] = "1"
                environment()["BC_DIST_PREREQUISITE"] = "1"
                environment()["BC_TEST_RUN_ID"] = prerequisiteId
            }.start().waitFor()
        statuses["dist-prerequisite"] = prerequisite
        if (prerequisite == 0) priorDist = prerequisiteId
    }
    if (priorDist != null) {
        val distEvidence = root.resolve("generated/test-evidence/$priorDist")
        val required = listOf("candidate", "multiplayer")
        val reportsPassed = required.all { suite ->
                val report = distEvidence.resolve("$suite/run.json")
                report.isFile && Regex("\"tier\"\\s*:\\s*\"dist\"").containsMatchIn(report.readText()) &&
                    Regex("\"status\"\\s*:\\s*\"passed\"").containsMatchIn(report.readText()) &&
                    Regex("\"run_id\"\\s*:\\s*\"${Regex.escape(priorDist!!)}\"").containsMatchIn(report.readText())
            }
        val candidateMatches = runCatching {
            val selected = distEvidence.resolve("candidate/events.jsonl").readLines()
                .first { "\"type\":\"candidate_selected\"" in it }
            fun field(name: String): String = Regex("\"$name\":\"([^\"]+)\"").find(selected)
                ?.groupValues?.get(1) ?: error("missing $name in Dist candidate receipt")
            fun sha256(file: java.io.File): String {
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        digest.update(buffer, 0, size)
                    }
                }
                return digest.digest().joinToString("") { "%02x".format(it) }
            }
            val candidates = Files.walk(root.resolve("dist").toPath()).use { stream ->
                stream.filter { it.endsWith("better-content.zip") }.toList()
            }
            val client = candidates.single { it.parent.fileName.toString() == "client" }.toFile()
            val server = candidates.single { it.parent.fileName.toString() == "server" }.toFile()
            client.absolutePath == field("client") && server.absolutePath == field("server") &&
                sha256(client) == field("client_sha256") && sha256(server) == field("server_sha256")
        }.getOrDefault(false)
        statuses["prior-dist"] = if (reportsPassed && candidateMatches) 0 else 1
    } else if (statuses["dist-prerequisite"] == null) {
        statuses["fast"] = gradle("fast", "test")
        statuses["diff"] = ProcessBuilder("git", "diff", "--check")
            .directory(root).inheritIO().start().waitFor()
    }
    if (statuses.values.all { it == 0 }) {
        statuses["candidate"] = if (priorDist == null) gradle("candidate", "candidateTest") else 0
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
