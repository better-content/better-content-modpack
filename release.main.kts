#!/usr/bin/env kotlin

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.system.exitProcess

val root = __FILE__.canonicalFile.parentFile
var jobs = 2
var skipTests = false
var debugMode = false
var target: String? = null
var retryOf: String? = null
var index = 0
while (index < args.size) {
    when (args[index]) {
        "--jobs" -> {
            if (index + 1 >= args.size) {
                System.err.println("usage: ./release.main.kts [--jobs 1..4] [--skip-tests|--target TARGET [--retry-of RUN_ID]|--debug]")
                exitProcess(2)
            }
            jobs = args[index + 1].toIntOrNull() ?: 0
            index += 2
        }
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
            System.err.println("usage: ./release.main.kts [--jobs 1..4] [--skip-tests|--target TARGET [--retry-of RUN_ID]|--debug]")
            exitProcess(2)
        }
    }
}
if (jobs !in 1..4) {
    System.err.println("release jobs must be between 1 and 4")
    exitProcess(2)
}
val validTarget = target == null || target in setOf("join", "fonts", "dimensions", "campaign-start", "campaign", "restart-compat", "cursed-pyramid", "lineage-transition", "world-save") ||
    (target!!.startsWith("font:") && target!!.removePrefix("font:") in setOf("ratlantis", "bumblezone", "aether", "nether")) ||
    (target!!.startsWith("dimension:") && Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(target!!.removePrefix("dimension:")) &&
        target!!.removePrefix("dimension:") !in setOf("rats:ratlantis", "the_bumblezone:the_bumblezone", "aether:the_aether", "minecraft:the_nether"))
if (!validTarget || args.count { it == "--jobs" } > 1 || args.count { it == "--skip-tests" } > 1 || args.count { it == "--debug" } > 1 ||
    args.count { it == "--target" } > 1 || args.count { it == "--retry-of" } > 1 ||
    (skipTests && (target != null || debugMode)) || (debugMode && target != null) || (retryOf != null && target == null) ||
    (retryOf != null && (!Regex("\\d{8}T\\d{6}Z-\\d+").matches(retryOf!!) ||
        !root.resolve("generated/test-evidence/$retryOf").isDirectory))) {
    System.err.println("usage: ./release.main.kts [--jobs 1..4] [--skip-tests|--target TARGET [--retry-of RUN_ID]|--debug]")
    exitProcess(2)
}

if (System.getenv("BC_PACK_TEST_LOCK_TOKEN").isNullOrBlank()) {
    val command = mutableListOf(
        root.resolve("pack-test-lock.main.kts").absolutePath,
        "run", "release", if (skipTests) "release-skip-tests" else if (debugMode) "debug" else target?.let { "target:$it" } ?: "dist", "--", __FILE__.absolutePath,
    )
    command.addAll(args)
    exitProcess(ProcessBuilder(command).directory(root).inheritIO().start().waitFor())
}

val runId = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    .format(java.time.Instant.now()) + "-" + ProcessHandle.current().pid()
val evidence = root.resolve("generated/test-evidence/$runId")
evidence.mkdirs()
evidence.resolve("release-request.txt").writeText(
    "run_id=$runId\njobs=$jobs\ntier=${if (debugMode) "debug" else if (target == null) "dist" else "targeted"}\ntarget=${target ?: ""}\nretry_of=${retryOf ?: ""}\nskip_tests=$skipTests\nforce_rebuild=$debugMode\ncommand=./release.main.kts --jobs $jobs${if (skipTests) " --skip-tests" else ""}${if (debugMode) " --debug" else ""}${target?.let { " --target $it" } ?: ""}\nstarted_at=${java.time.Instant.now()}\n",
)

fun run(vararg command: String): Int = ProcessBuilder(*command)
    .directory(root)
    .inheritIO()
    .apply {
        environment()["BC_TEST_RUN_ID"] = runId
        target?.let { environment()["BC_RELEASE_TARGET"] = it }
        if (debugMode) environment()["BC_RELEASE_DEBUG"] = "1"
    }
    .start()
    .waitFor()

val preparationCommand = mutableListOf(
    root.resolve("gradlew").absolutePath,
    "--no-daemon",
    "prepareFreshDist",
    "-PreleaseJobs=$jobs",
)
if (skipTests) preparationCommand += "-PreleaseSkipTests=true"
if (debugMode) preparationCommand += "-PreleaseForceRebuild=true"
val preparation = run(*preparationCommand.toTypedArray())
if (preparation != 0) {
    println("release run: $runId")
    println("evidence: ${evidence.absolutePath}")
    exitProcess(preparation)
}

if (debugMode) {
    val provenance = evidence.resolve("release/provenance.json")
    fun field(name: String): String {
        val process = ProcessBuilder("jq", "-er", ".candidates.$name", provenance.toString())
            .directory(root).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        val value = process.inputStream.bufferedReader().readText().trim()
        require(process.waitFor() == 0 && value.isNotBlank()) { "missing candidate $name in $provenance" }
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
        val command = mutableListOf(root.resolve("test.main.kts").absolutePath, tier)
        if (existingCandidate) command += "--existing-candidate"
        return ProcessBuilder(command).directory(root).inheritIO().apply {
            environment()["BC_TEST_RUN_ID"] = testRunId
        }.start().waitFor()
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
    record("passed", if (debugStatus == 0 && debugCandidateStable) "passed" else "failed ($debugStatus, candidate_stable=$debugCandidateStable)")
    if (!debugCandidateStable) {
        System.err.println("candidate hashes changed during Debug; see $summary")
        exitProcess(1)
    }
    println("release run: $runId")
    println("Dist run: $distRunId")
    println("Debug run: $debugRunId")
    println("evidence: ${evidence.absolutePath}")
    exitProcess(debugStatus)
}

val testArgs = mutableListOf(root.resolve("test.main.kts").absolutePath, if (target == null || target == "join") "dist" else "debug")
target?.let { testArgs += listOf("--target", it) }
retryOf?.let { testArgs += listOf("--retry-of", it) }
val tests = if (skipTests) 0 else ProcessBuilder(testArgs)
    .directory(root)
    .inheritIO()
    .start()
    .waitFor()
println("release run: $runId")
println("evidence: ${evidence.absolutePath}")
if (skipTests) println("tests: skipped by explicit request")
exitProcess(tests)
