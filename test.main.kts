#!/usr/bin/env kotlin
@file:Import("scripts/BoundedFacade.main.kts")

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

val root = __FILE__.canonicalFile.parentFile
if (args.firstOrNull() in setOf("list", "plan", "run", "submit", "status", "wait", "evidence", "retry")) {
    exitProcess(superviseFacade(root, listOf(root.resolve("test-control.main.kts").absolutePath) + args))
}
val selector = args.firstOrNull()
var target: String? = null
var retryOf: String? = null
var existingCandidate = false
var verificationSession: String? = null
var candidateBinding: String? = null
val handoff = System.getenv("BC_PACK_TEST_HANDOFF")?.takeIf(String::isNotBlank)
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
val validTarget = target == null || target in setOf("server-ready", "cursed-pyramid", "lineage-transition", "join", "fonts", "dimensions", "campaign-start", "campaign", "restart", "restart-compat", "world-save", "world-startup", "deep-void", "gameplay") ||
    (target!!.startsWith("font:") && target!!.removePrefix("font:") in fontTargets) ||
    (target!!.startsWith("dimension:") && Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(target!!.removePrefix("dimension:")) &&
        target!!.removePrefix("dimension:") !in fontDimensions)
val targetSuite = when {
    target == null -> null
    target == "server-ready" || target == "cursed-pyramid" || target == "lineage-transition" -> "server"
    target in setOf("world-save", "world-startup") -> "singleplayer"
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
    target == "world-startup" -> "debugNativeUnseededWorldStartup"
    target == "deep-void" -> "debugNativeDeepVoidEntry"
    target == "gameplay" -> "gameplayAcceptance"
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
    (retryOf != null && candidateBinding == null && handoff == null) ||
    (target != null && candidateBinding == null && System.getenv("BC_PACK_TEST_HANDOFF").isNullOrBlank()) ||
    (candidateBinding != null && (selector == "dev" || !java.io.File(candidateBinding!!).isFile)) ||
    (existingCandidate && (selector != "debug" || target != null || args.count { it == "--existing-candidate" } != 1)) ||
    (retryOf != null && !Regex("\\d{8}T\\d{6}Z-\\d+").matches(retryOf!!)) ||
    (retryOf != null && !root.resolve("generated/test-evidence/$retryOf").isDirectory)) usage()
val selected = selector ?: usage()
if ((candidateBinding != null || handoff != null) && selected == "debug" && target == null) existingCandidate = true
verificationSession?.let {
    if (!Regex("[a-f0-9]{32}").matches(it)) usage()
    val status = superviseFacade(root, listOf("python3", "-B", root.resolve("scripts/testing_session.py").absolutePath, "status", it), budgetSeconds = 60)
    if (status != 0) exitProcess(status)
}
val sessionArgs = verificationSession?.let { listOf("--verification-session", it) } ?: emptyList()

if (selected == "debug" && target == null && !existingCandidate) {
    exitProcess(superviseFacade(root, listOf(root.resolve("release.main.kts").absolutePath, "--debug") + sessionArgs))
}

if (selected == "dist" && target == null && candidateBinding == null && handoff == null && System.getenv("BC_RELEASE_PREPARED") != "1") {
    exitProcess(superviseFacade(root, listOf(root.resolve("release.main.kts").absolutePath) + sessionArgs))
}

if (selected != "dev" && System.getenv("BC_PACK_TEST_LOCK_TOKEN").isNullOrBlank()) {
    val status = superviseFacade(root, listOf(root.resolve("pack-test-lock.main.kts").absolutePath,
        "run", "test", if (target == null) selected else "$selected:$target", "--", __FILE__.absolutePath) + args)
    exitProcess(status)
}

if (selected != "dev") {
    val verified = superviseFacade(root, listOf("python3", "-B", root.resolve("scripts/runner_lock.py").absolutePath), budgetSeconds = 30)
    if (verified != 0) exitProcess(75)
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
        val testClass = when {
            target == "gameplay" -> "GameplayRuntimeTest"
            suite == "server" -> "ServerRuntimeTest"
            suite == "multiplayer" -> "MultiplayerRuntimeTest"
            else -> "SingleplayerRuntimeTest"
        }
        command += listOf("--tests", "com.bettercontent.tests.$testClass.$method")
    }
    val environment = buildMap {
        if (suite != "fast") { put("BC_TEST_SELECTOR", suite); put("BC_TEST_TIER", selected) }
        if (runId != null) put("BC_TEST_RUN_ID", runId)
        candidateBinding?.let { put("BC_CANDIDATE_BINDING", java.io.File(it).absolutePath) }
        retryOf?.let { put("BC_TEST_RETRY_OF", it) }
        if (method != null) { put("BC_TEST_TARGET", target!!); put("BC_TEST_EVIDENCE_SUITE", targetEvidenceSuite!!) }
    }
    val status = superviseFacade(root, command, budgetSeconds = if (suite == "fast") 1200 else 14400,
        environment = environment, removedEnvironment = if (suite == "fast") setOf("BC_TEST_SELECTOR", "BC_TEST_TIER") else emptySet())
    if (status != 0 || suite == "fast") return status
    return if (validateFreshEvidence(evidenceSuite, startedAt)) 0 else 1
}

val statuses = linkedMapOf<String, Int>()
if (selected == "dev") {
    statuses["fast"] = gradle("fast", "test")
    if (statuses.getValue("fast") == 0) {
        statuses["diff"] = superviseFacade(root, listOf("git", "diff", "--check"), budgetSeconds = 30)
    }
} else {
    var priorDist = if (selected == "debug" && existingCandidate) System.getenv("BC_PRIOR_DIST_RUN_ID") else null
    if (selected == "debug" && existingCandidate && priorDist == null) {
        val prerequisiteId = "${runId}1"
        val prerequisite = superviseFacade(root, listOf(__FILE__.absolutePath, "dist") +
            (candidateBinding?.let { listOf("--candidate-binding", it) } ?: emptyList()) +
            (retryOf?.let { listOf("--retry-of", it) } ?: emptyList()),
            environment = mapOf("BC_RELEASE_PREPARED" to "1", "BC_DIST_PREREQUISITE" to "1", "BC_TEST_RUN_ID" to prerequisiteId))
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
            val selectionArgs = listOf("python3", "-B", root.resolve("scripts/candidate_paths.py").absolutePath, "--root", root.absolutePath) +
                (candidateBinding?.let { listOf("--binding", it) } ?: emptyList()) +
                (handoff?.let { listOf("--handoff", it) } ?: emptyList())
            val (code, selection) = captureFacade(root, selectionArgs)
            require(code == 0) { "exact candidate selection failed" }
            fun selectedField(name: String): String = Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(selection)
                ?.groupValues?.get(1) ?: error("missing selected $name")
            listOf("client", "server", "client_sha256", "server_sha256").all { selectedField(it) == field(it) }
        }.getOrDefault(false)
        statuses["prior-dist"] = if (reportsPassed && candidateMatches) 0 else 1
    } else if (statuses["dist-prerequisite"] == null) {
        statuses["fast"] = gradle("fast", "test")
        statuses["diff"] = superviseFacade(root, listOf("git", "diff", "--check"), budgetSeconds = 30)
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
