#!/usr/bin/env kotlin
@file:Import("scripts/BoundedFacade.main.kts")

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.system.exitProcess

val root = __FILE__.canonicalFile.parentFile
val usage = "usage: ./release.main.kts [--jobs 1..4] [--skip-tests|--target TARGET [--retry-of RUN_ID]|--debug] [--diagnostic-controls INPUT]"
var jobs = 2
var skipTests = false
var debugMode = false
var target: String? = null
var retryOf: String? = null
var verificationSession: String? = null
var rebuildAll = false
var diagnosticControls: String? = null
var index = 0
while (index < args.size) {
    when (args[index]) {
        "--jobs" -> {
            if (index + 1 >= args.size) {
                System.err.println(usage)
                exitProcess(2)
            }
            jobs = args[index + 1].toIntOrNull() ?: 0
            index += 2
        }
        "--verification-session" -> {
            verificationSession = args.getOrNull(index + 1) ?: error("--verification-session needs a session ID")
            index += 2
        }
        "--diagnostic-controls" -> {
            diagnosticControls = args.getOrNull(index + 1) ?: error("--diagnostic-controls needs the bound deferral JSON")
            require(Path.of(diagnosticControls!!).isAbsolute) { "diagnostic Controls input must be absolute" }
            index += 2
        }
        "--rebuild-all" -> { rebuildAll = true; index++ }
        "--skip-tests" -> {
            skipTests = true
            index++
        }
        "--debug" -> {
            debugMode = true
            index++
        }
        "--target" -> {
            target = args.getOrNull(index + 1) ?: error("--target needs a scenario")
            index += 2
        }
        "--retry-of" -> {
            retryOf = args.getOrNull(index + 1) ?: error("--retry-of needs a run ID")
            index += 2
        }
        else -> {
            System.err.println(usage)
            exitProcess(2)
        }
    }
}
if (verificationSession != null && !Regex("[a-f0-9]{32}").matches(verificationSession!!)) error("invalid verification session ID")
require(diagnosticControls == null || (!skipTests && verificationSession == null && !rebuildAll)) {
    "diagnostic Controls mode retains all other gates and cannot use verified sessions or skip-tests"
}
require(!(skipTests && (verificationSession != null || rebuildAll))) { "unverified release cannot consume verification receipts" }
if (jobs !in 1..4) {
    System.err.println("release jobs must be between 1 and 4")
    exitProcess(2)
}
val validTarget = target == null || target in setOf("join", "fonts", "dimensions", "campaign-start", "campaign", "restart-compat", "cursed-pyramid", "lineage-transition", "world-save", "world-startup", "deep-void", "gameplay", "plant-drinks", "structure-loot", "bees-cosmetics", "emi-native-order", "interactions", "structure-loot-personal", "lifecycle-interactions", "tnt-buried", "regolith-native", "wildfire-native", "machine-seals", "kinetic-loss", "oc2-provider-api", "wireless-native", "native-device-bus", "kinetic-overload") ||
    (target!!.startsWith("font:") && target!!.removePrefix("font:") in setOf("bumblezone", "aether", "nether")) ||
    (target!!.startsWith("dimension:") && Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(target!!.removePrefix("dimension:")) &&
        target!!.removePrefix("dimension:") !in setOf("the_bumblezone:the_bumblezone", "aether:the_aether", "minecraft:the_nether"))
if (!validTarget || args.count { it == "--jobs" } > 1 || args.count { it == "--skip-tests" } > 1 || args.count { it == "--debug" } > 1 ||
    args.count { it == "--target" } > 1 || args.count { it == "--retry-of" } > 1 ||
    args.count { it == "--verification-session" } > 1 || args.count { it == "--rebuild-all" } > 1 ||
    args.count { it == "--diagnostic-controls" } > 1 ||
    (skipTests && (target != null || debugMode)) || (debugMode && target != null) || (retryOf != null && target == null) ||
    (retryOf != null && (!Regex("\\d{8}T\\d{6}Z-\\d+").matches(retryOf!!) ||
        !root.resolve("generated/test-evidence/$retryOf").isDirectory))) {
    System.err.println(usage)
    exitProcess(2)
}

if (System.getenv("BC_PACK_TEST_LOCK_TOKEN").isNullOrBlank()) {
    val command = mutableListOf(
        root.resolve("pack-test-lock.main.kts").absolutePath,
        "run", "release", if (skipTests) "release-skip-tests" else if (debugMode) "debug" else target?.let { "target:$it" } ?: "dist", "--", __FILE__.absolutePath,
    )
    command.addAll(args)
    exitProcess(superviseFacade(root, command))
}

val lockVerified = superviseFacade(root, listOf("python3", "-B", root.resolve("scripts/runner_lock.py").absolutePath), budgetSeconds = 30)
if (lockVerified != 0) exitProcess(75)

val runId = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    .format(java.time.Instant.now()) + "-" + ProcessHandle.current().pid()
val forceRebuild = rebuildAll
val evidence = root.resolve("generated/test-evidence/$runId")
evidence.mkdirs()
evidence.resolve("release-request.txt").writeText(
    "run_id=$runId\njobs=$jobs\ntier=${if (debugMode) "debug" else if (target == null) "dist" else "targeted"}\ntarget=${target ?: ""}\nretry_of=${retryOf ?: ""}\nskip_tests=$skipTests\ndiagnostic_controls=${diagnosticControls ?: ""}\nforce_rebuild=$forceRebuild\ncommand=./release.main.kts --jobs $jobs${if (skipTests) " --skip-tests" else ""}${if (debugMode) " --debug" else ""}${target?.let { " --target $it" } ?: ""}${diagnosticControls?.let { " --diagnostic-controls $it" } ?: ""}\nstarted_at=${java.time.Instant.now()}\n",
)

fun run(vararg command: String): Int = superviseFacade(root, command.toList(), environment = buildMap {
    put("BC_TEST_RUN_ID", runId)
    diagnosticControls?.let { put("BC_RELEASE_DIAGNOSTIC_CONTROLS", it) }
    verificationSession?.let { put("BC_VERIFICATION_SESSION", it) }
    target?.let { put("BC_RELEASE_TARGET", it) }
    if (debugMode) put("BC_RELEASE_DEBUG", "1")
})

val preparationCommand = mutableListOf(
    root.resolve("gradlew").absolutePath,
    "--no-daemon",
    "prepareFreshDist",
    "-PreleaseJobs=$jobs",
)
if (skipTests) preparationCommand += "-PreleaseSkipTests=true"
if (forceRebuild) preparationCommand += "-PreleaseForceRebuild=true"
val preparation = run(*preparationCommand.toTypedArray())
if (preparation != 0) {
    println("release run: $runId")
    println("evidence: ${evidence.absolutePath}")
    exitProcess(preparation)
}

if (debugMode) {
    val provenance = evidence.resolve("release/provenance.json")
    fun field(name: String): String {
        val (status, output) = captureFacade(root, listOf("jq", "-er", ".candidates.$name", provenance.toString()))
        val value = output.trim()
        require(status == 0 && value.isNotBlank()) { "missing candidate $name in $provenance" }
        return value
    }
    fun sha256(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(Path.of(path)).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    val client = field("client")
    val server = field("server")
    val clientHash = field("client_sha256")
    val serverHash = field("server_sha256")
    val distRunId = "${runId}1"
    val debugRunId = "${runId}2"
    val summary = evidence.resolve("release/debug-runs.tsv")
    fun record(distStatus: String, debugStatus: String) {
        Files.writeString(summary.toPath(), "tier\trun_id\tstatus\tclient_sha256\tserver_sha256\n" +
            "dist\t$distRunId\t$distStatus\t$clientHash\t$serverHash\n" +
            "debug\t$debugRunId\t$debugStatus\t$clientHash\t$serverHash\n")
    }
    fun candidateUnchanged(): Boolean = runCatching {
        sha256(client) == clientHash && sha256(server) == serverHash
    }.getOrDefault(false)
    fun testTier(tier: String, testRunId: String, existingCandidate: Boolean): Int {
        val command = mutableListOf(root.resolve("test.main.kts").absolutePath, tier,
            "--candidate-binding", provenance.absolutePath)
        if (existingCandidate) command += "--existing-candidate"
        return superviseFacade(root, command, environment = buildMap {
            put("BC_TEST_RUN_ID", testRunId)
            diagnosticControls?.let { put("BC_RELEASE_DIAGNOSTIC_CONTROLS", it) }
            put("BC_RELEASE_PREPARED", "1")
            if (existingCandidate) put("BC_PRIOR_DIST_RUN_ID", distRunId)
        })
    }
    record("pending", "pending")
    require(candidateUnchanged()) { "fresh candidate changed before Dist; see $summary" }
    val distStatus = testTier("dist", distRunId, false)
    val distCandidateStable = candidateUnchanged()
    record(if (distStatus == 0 && distCandidateStable) "passed" else "failed ($distStatus, candidate_stable=$distCandidateStable)", "pending")
    if (distStatus != 0 || !distCandidateStable) {
        System.err.println("Dist failed or candidate hashes changed; Debug was not started. See $summary")
        exitProcess(1)
    }
    val debugStatus = testTier("debug", debugRunId, true)
    val debugCandidateStable = candidateUnchanged()
    record("passed", if (debugStatus == 0 && debugCandidateStable) "passed" else if (debugStatus == 4 && debugCandidateStable) "awaiting_review" else "failed ($debugStatus, candidate_stable=$debugCandidateStable)")
    if (!debugCandidateStable) {
        System.err.println("candidate hashes changed during Debug; see $summary")
        exitProcess(1)
    }
    println("release run: $runId")
    println("Dist run: $distRunId")
    println("Debug run: $debugRunId")
    println("evidence: ${evidence.absolutePath}")
    if (diagnosticControls != null) println("DIAGNOSTIC_UNQUALIFIED: Controls deferred; no full release acceptance")
    exitProcess(if (diagnosticControls != null && debugStatus == 0) 6 else debugStatus)
}

val testArgs = mutableListOf(root.resolve("test.main.kts").absolutePath, if (target == null || target == "join") "dist" else "debug")
if (!skipTests) testArgs += listOf("--candidate-binding", evidence.resolve("release/provenance.json").absolutePath)
target?.let { testArgs += listOf("--target", it) }
retryOf?.let { testArgs += listOf("--retry-of", it) }
val tests = if (skipTests) 0 else superviseFacade(root, testArgs, environment = buildMap {
    put("BC_RELEASE_PREPARED", "1")
    diagnosticControls?.let { put("BC_RELEASE_DIAGNOSTIC_CONTROLS", it) }
})
println("release run: $runId")
println("evidence: ${evidence.absolutePath}")
if (skipTests) println("tests: skipped by explicit request")
if (diagnosticControls != null) println("DIAGNOSTIC_UNQUALIFIED: Controls deferred; runtime result=$tests; no complete46 acceptance")
exitProcess(if (diagnosticControls != null && tests == 0) 6 else tests)
