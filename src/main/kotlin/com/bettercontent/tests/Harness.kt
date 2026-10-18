package com.bettercontent.tests

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.BufferedWriter
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class CandidatePair(
    val release: Path,
    val client: Path,
    val server: Path,
    val clientSha256: String,
    val serverSha256: String,
)

object Hashes {
    fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

object CandidateLocator {
    fun locate(root: Path): CandidatePair {
        System.getenv("BC_CANDIDATE_BINDING")?.takeIf(String::isNotBlank)?.let {
            val pair = CandidateBinding.select(root, Path.of(it))
            System.getenv("BC_PACK_TEST_HANDOFF")?.takeIf(String::isNotBlank)?.let { handoff ->
                PackTestHandoff.validate(Path.of(handoff), pair)
            }
            return pair
        }
        System.getenv("BC_PACK_TEST_HANDOFF")?.takeIf(String::isNotBlank)?.let {
            val pair = selectPaths(root, listOf("--handoff", it))
            PackTestHandoff.validate(Path.of(it), pair)
            return pair
        }
        return locateUnbound(root)
    }

    internal fun locateUnbound(root: Path): CandidatePair {
        require(root.toAbsolutePath() == root.toAbsolutePath().normalize()) {
            "candidate root must not contain . or .. path components"
        }
        return selectPaths(root)
    }

    private fun selectPaths(root: Path, extra: List<String> = emptyList()): CandidatePair {
        val scriptRoot = Path.of(System.getProperty("bc.repo.root", ".")).toAbsolutePath().normalize()
        val json = try { BoundedCommand.capture(scriptRoot, listOf("python3", "-B", scriptRoot.resolve("scripts/candidate_paths.py").toString(),
            "--root", root.toAbsolutePath().toString()) + extra, Duration.ofSeconds(30)) }
            catch (failure: IllegalStateException) { throw IllegalArgumentException("exact candidate selection failed: ${failure.message}", failure) }
        val value = jacksonObjectMapper().readTree(json)
        val client = Path.of(value.path("client").asText())
        val server = Path.of(value.path("server").asText())
        return CandidatePair(client.parent.parent, client, server, value.path("client_sha256").asText(), value.path("server_sha256").asText())
    }

}

object PackTestHandoff {
    private val mapper = jacksonObjectMapper()

    fun validate(path: Path, pair: CandidatePair, authorityRoot: Path = System.getenv("WORKSPACE_COORDINATION_ROOT")?.takeIf(String::isNotBlank)?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), ".worklane/coordination")): String {
        CandidateBinding.requireOrdinary(path)
        require(Files.isRegularFile(path) && Files.size(path) <= 1024 * 1024) { "pack-test handoff is missing or oversized: $path" }
        val document = mapper.readTree(path.toFile())
        require(document.path("schema").asText() == "bc.pack_test_handoff.v1") { "unexpected pack-test handoff schema" }
        val task = document.path("task_id").asText()
        val generation = document.path("task_generation")
        val taskOwner = document.path("task_owner").asText()
        val operation = document.path("selector").asText()
        require(task.isNotBlank() && generation.isIntegralNumber && generation.asLong() > 0 && taskOwner.isNotBlank() &&
            operation in setOf("dist", "debug")) { "handoff lacks registered task/owner/operation authority" }
        val root = Path.of(System.getProperty("bc.repo.root", ".")).toAbsolutePath().normalize()
        val authority = BoundedCommand.capture(root, listOf("python3", "-B", root.resolve("scripts/workspace_coordination.py").toString(),
            "--root", authorityRoot.toString(), "authority", "--task", task, "--generation", generation.asText(),
            "--owner", taskOwner, "--operation", "pack-test:$operation"), Duration.ofSeconds(30))
        require(jacksonObjectMapper().readTree(authority).path("valid").asBoolean(false)) { "stale task authority" }
        require(document.path("authorization").path("explicit").asBoolean()) { "pack-test handoff lacks explicit authorization" }
        require(document.path("callback").path("agent").asText().isNotBlank() && document.path("callback").path("pane_id").asText().isNotBlank()) {
            "pack-test handoff lacks a callback agent and pane"
        }
        require(document.path("modpack").path("head").asText().isNotBlank() && document.path("modpack").path("status").isArray) {
            "pack-test handoff lacks modpack revision/status"
        }
        listOf("repositories", "validations", "artifacts", "dependencies", "scenarios", "prior_evidence").forEach { field ->
            require(document.path(field).isArray) { "pack-test handoff field must be an array: $field" }
        }
        val requestId = document.path("request_id").asText()
        require(requestId.isNotBlank()) { "pack-test handoff has no request ID" }
        System.getenv("BC_TEST_TIER")?.takeIf { it in setOf("dist", "debug") }?.let { selector ->
            val queuedDebugDist = selector == "dist" && System.getenv("BC_DIST_PREREQUISITE") == "1" &&
                document.path("selector").asText() == "debug"
            require(document.path("selector").asText() == selector || queuedDebugDist) {
                "pack-test selector differs from the handoff"
            }
        }
        System.getenv("BC_TEST_TARGET")?.takeIf(String::isNotBlank)?.let { target ->
            require(document.path("target").asText() == target) { "pack-test target differs from the handoff" }
        }
        fun validateSide(side: String, actualPath: Path, actualHash: String) {
            val expected = document.path("candidate").path(side)
            require(Path.of(expected.path("path").asText()).toAbsolutePath().normalize() == actualPath.toAbsolutePath().normalize()) {
                "$side candidate path differs from the handoff"
            }
            require(expected.path("sha256").asText() == actualHash) { "$side candidate hash differs from the handoff" }
        }
        validateSide("client", pair.client, pair.clientSha256)
        validateSide("server", pair.server, pair.serverSha256)
        return requestId
    }
}

object ZipContracts {
    fun entryText(zip: Path, entry: String): String = ZipFile(zip.toFile()).use { archive ->
        val item = archive.getEntry(entry) ?: error("candidate is missing $entry")
        archive.getInputStream(item).bufferedReader().use { it.readText() }
    }

    fun soleTopLevelDirectory(zip: Path): String = ZipFile(zip.toFile()).use { archive ->
        val roots = archive.entries().asSequence()
            .map { it.name.substringBefore('/') }
            .filter { it.isNotBlank() }
            .toSet()
        require(roots.size == 1) { "server candidate must contain exactly one top-level directory: $roots" }
        roots.single()
    }

    fun extract(zip: Path, destination: Path) {
        destination.createDirectories()
        ZipFile(zip.toFile()).use { archive ->
            archive.entries().asSequence().forEach { entry ->
                val target = destination.resolve(entry.name).normalize()
                require(target.startsWith(destination.normalize())) { "unsafe ZIP entry: ${entry.name}" }
                if (entry.isDirectory) target.createDirectories()
                else {
                    target.parent.createDirectories()
                    archive.getInputStream(entry).use { input -> Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING) }
                }
            }
        }
    }
}

data class TestConfig(
    val root: Path,
    val runId: String,
    val runRoot: Path,
    val settleSeconds: Long,
    val username: String,
    val clientMain: Path,
    val java: Path,
    val handoff: Path?,
) {
    companion object {
        fun load(): TestConfig {
            val root = Path.of(System.getProperty("bc.repo.root", ".")).toAbsolutePath().normalize()
            val runId = System.getenv("BC_TEST_RUN_ID")?.takeIf { it.isNotBlank() }
                ?: DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
                    .format(Instant.now()) + "-" + ProcessHandle.current().pid()
            val runRoot = System.getenv("BC_TEST_RUN_ROOT")?.let { Path.of(it) }
                ?: root.resolve("generated/test-evidence")
            val settle = System.getenv("BC_TEST_SETTLE_SECONDS")?.toLongOrNull() ?: 10L
            require(settle >= 0) { "BC_TEST_SETTLE_SECONDS must be non-negative" }
            val javaCandidate = System.getenv("BC_JAVA")?.let { Path.of(it) }
                ?: System.getenv("JAVA_HOME")?.let { Path.of(it).resolve("bin/java") }
                ?: Path.of(System.getProperty("java.home")).resolve("bin/java")
            require(Files.isExecutable(javaCandidate)) { "Java executable was not found: $javaCandidate" }
            require(System.getProperty("java.specification.version") == "17") { "Java 17 is required" }
            return TestConfig(
                root,
                runId,
                runRoot.toAbsolutePath().normalize(),
                settle,
                System.getenv("BC_TEST_USERNAME") ?: "SmokeClient",
                System.getenv("BC_TEST_CLIENT_MAIN")?.let { Path.of(it) }
                    ?: Path.of(System.getProperty("user.home"), ".cache/bc/tests/client-main"),
                javaCandidate.toAbsolutePath().normalize(),
                System.getenv("BC_PACK_TEST_HANDOFF")?.takeIf(String::isNotBlank)?.let(Path::of)?.toAbsolutePath()?.normalize(),
            )
        }
    }
}

class EvidenceRun(val config: TestConfig, val suite: String, val sourceSuite: String = suite) {
    private val mapper: ObjectMapper = jacksonObjectMapper()
    val directory: Path = config.runRoot.resolve(config.runId).resolve(suite).also { it.createDirectories() }
    val fixture: Path = directory.resolve("fixture").also { it.createDirectories() }
    private val events = directory.resolve("events.jsonl")
    private val started = Instant.now()
    val tier: String = System.getenv("BC_TEST_TIER")?.takeIf { it in setOf("dist", "debug") } ?: "legacy"
    val target: String? = System.getenv("BC_TEST_TARGET")?.takeIf(String::isNotBlank)
    private val failures = mutableListOf<String>()

    init {
        event("suite_started", mapOf("suite" to suite, "source_suite" to sourceSuite, "tier" to tier,
            "scope" to if (target == null) "full" else "targeted", "target" to target,
            "retry_of" to System.getenv("BC_TEST_RETRY_OF"), "root" to config.root.absolutePathString()))
        config.handoff?.let { source ->
            require(Files.isRegularFile(source)) { "pack-test handoff is missing: $source" }
            Files.copy(source, directory.resolve("handoff.json"), StandardCopyOption.REPLACE_EXISTING)
            event("handoff_selected", mapOf("request_id" to PackTestHandoff.validate(source, CandidateLocator.locate(config.root))))
        }
        capture("git-status.txt", listOf("git", "status", "--short"), config.root)
        capture("git-head.txt", listOf("git", "rev-parse", "HEAD"), config.root)
        capture("java-version.txt", listOf(config.java.toString(), "-version"), config.root)
    }

    @Synchronized
    fun event(type: String, fields: Map<String, Any?> = emptyMap()) {
        val document = linkedMapOf<String, Any?>("time" to Instant.now().toString(), "type" to type)
        document.putAll(fields)
        Files.writeString(
            events,
            mapper.writeValueAsString(document) + "\n",
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
        println("[pack-test:$suite] ${mapper.writeValueAsString(document)}")
        writeSummary("running")
    }

    fun checkpoint(name: String, action: () -> Unit) {
        event("checkpoint_started", mapOf("name" to name))
        try {
            action()
            event("checkpoint_passed", mapOf("name" to name))
        } catch (error: Throwable) {
            val failure = "$name: ${error.message ?: error::class.java.name}"
            failures += failure
            event("checkpoint_failed", mapOf("name" to name, "error" to failure))
            throw error
        }
    }

    fun capture(name: String, command: List<String>, cwd: Path = config.root) {
        val output = directory.resolve(name)
        try {
            val status = BoundedCommand.run(cwd, command, output, Duration.ofSeconds(30))
            check(status == 0) { "evidence capture exited $status: $name" }
        } catch (error: Exception) {
            failures += "evidence capture $name: ${error.message ?: error::class.java.name}"
            Files.writeString(output.resolveSibling(output.fileName.toString() + ".failure.txt"), "evidence capture failed: ${error.message}\n")
            throw error
        }
    }

    fun finish(success: Boolean) {
        if (!success && failures.isEmpty()) failures += "suite failed outside a named checkpoint"
        event("suite_finished", mapOf("success" to success, "failures" to failures.toList()))
        writeSummary(if (success) "passed" else "failed")
        println("evidence: ${directory.absolutePathString()}")
    }

    private fun writeSummary(status: String) {
        val summary = linkedMapOf<String, Any?>(
            "schema" to "bc.modpack_test_run.v1",
            "run_id" to config.runId,
            "suite" to suite,
            "source_suite" to sourceSuite,
            "scope" to if (target == null) "full" else "targeted",
            "target" to target,
            "retry_of" to System.getenv("BC_TEST_RETRY_OF"),
            "tier" to tier,
            "status" to status,
            "started_at" to started.toString(),
            "updated_at" to Instant.now().toString(),
            "failure" to failures.firstOrNull(),
            "failures" to failures.toList(),
            "evidence" to directory.absolutePathString(),
            "fixture" to fixture.absolutePathString(),
        )
        val temporary = directory.resolve(".run.json.tmp")
        mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), summary)
        Files.move(temporary, directory.resolve("run.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

class ManagedProcess(
    val name: String,
    command: List<String>,
    cwd: Path,
    val log: Path,
    environment: Map<String, String> = emptyMap(),
) : AutoCloseable {
    private val process: Process
    private val input: BufferedWriter
    private val tracker: ProcessTracker

    init {
        log.parent.createDirectories()
        val builder = ProcessBuilder(listOf("setsid", "--wait") + command).directory(cwd.toFile()).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
        builder.environment().putAll(environment)
        process = builder.start()
        input = process.outputStream.bufferedWriter()
        tracker = ProcessTracker(process.toHandle(), process.pid())
    }

    val alive: Boolean get() = process.isAlive
    val pid: Long get() = process.pid()
    internal val trackedPids: Set<Long> get() = tracker.trackedPids()

    fun send(line: String) {
        require(process.isAlive) { "$name is not running" }
        input.write(line)
        input.newLine()
        input.flush()
    }

    fun waitForLog(pattern: Regex, timeout: Duration, description: String) {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            val text = if (log.exists()) log.readText() else ""
            if (pattern.containsMatchIn(text)) return
            check(process.isAlive) { "$name exited before $description; see $log" }
            TimeUnit.NANOSECONDS.sleep(minOf(500_000_000, (deadline - System.nanoTime()).coerceAtLeast(0)))
        }
        captureDiagnostics(log.parent.resolve("$name-timeout"))
        error("timed out waiting for $description; see $log")
    }

    fun waitForLogCount(pattern: Regex, expected: Int, timeout: Duration, description: String) {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            val text = if (log.exists()) log.readText() else ""
            check("[net.minecraft.server.Main]: Failed to start the minecraft server" !in text) {
                "$name reported a fatal server startup failure before $description; see $log"
            }
            if (pattern.findAll(text).count() >= expected) return
            check(process.isAlive) { "$name exited before $description; see $log" }
            TimeUnit.NANOSECONDS.sleep(minOf(500_000_000, (deadline - System.nanoTime()).coerceAtLeast(0)))
        }
        captureDiagnostics(log.parent.resolve("$name-timeout"))
        error("timed out waiting for $description; see $log")
    }

    fun captureDiagnostics(prefix: Path) {
        prefix.parent.createDirectories()
        val deadline = ScenarioDeadline(Duration.ofSeconds(30))
        val errors = mutableListOf<String>()
        runCatching {
            val status = BoundedCommand.run(log.parent, listOf("ps", "-o", "pid,ppid,etime,pcpu,pmem,stat,comm", "-p",
                tracker.trackedPids().sorted().take(64).joinToString(",")),
                prefix.resolveSibling("${prefix.name}-processes.txt"), minOf(Duration.ofSeconds(5), deadline.requireRemaining()))
            check(status == 0) { "process diagnostic exited $status" }
        }.exceptionOrNull()?.let { errors += it.toString() }
        val handles = tracker.trackedPids().sorted().take(64).mapNotNull { ProcessHandle.of(it).orElse(null) }
        handles.filter { it.isAlive && it.info().command().orElse("").endsWith("/java") }.forEach { handle ->
            if (!deadline.remaining().isZero) runCatching {
                val status = BoundedCommand.run(log.parent, listOf("jcmd", handle.pid().toString(), "Thread.print"),
                    prefix.resolveSibling("${prefix.name}-${handle.pid()}-threads.txt"),
                    minOf(Duration.ofSeconds(15), deadline.requireRemaining()))
                check(status == 0) { "thread diagnostic exited $status" }
            }.exceptionOrNull()?.let { errors += it.toString() }
        }
        runCatching { Files.writeString(prefix.resolveSibling("${prefix.name}-diagnostic-status.txt"),
            "diagnostic_budget_seconds=30\ntracked=${tracker.trackedPids()}\nerrors=${errors.take(64)}\n") }
    }

    fun stop(timeout: Duration = Duration.ofSeconds(20)) {
        runCatching { input.close() }
        tracker.stop(timeout)
    }

    fun waitForExit(timeout: Duration, description: String) {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (!process.isAlive && tracker.trackedPids().none { ProcessHandle.of(it).orElse(null)?.isAlive == true }) return
            Thread.sleep(250)
        }
        captureDiagnostics(log.parent.resolve("$name-exit-timeout"))
        error("timed out waiting for $description to exit; see $log")
    }

    override fun close() = stop()
}

object Commands {
    fun run(command: List<String>, cwd: Path, log: Path, environment: Map<String, String> = emptyMap()) {
        val status = BoundedCommand.run(cwd, command, log, Duration.ofMinutes(30), environment)
        check(status == 0) { "command failed ($status): ${command.joinToString(" ")}; see $log" }
    }

    fun runWithInput(command: List<String>, cwd: Path, log: Path, input: String, environment: Map<String, String> = emptyMap()) {
        val status = BoundedCommand.run(cwd, command, log, Duration.ofMinutes(30), environment, input)
        check(status == 0) { "command failed ($status): ${command.joinToString(" ")}; see $log" }
    }
}

fun freePort(): Int = ServerSocket(0).use { it.localPort }

fun replaceExact(path: Path, old: String, replacement: String) {
    val text = path.readText()
    require(text.windowed(old.length).count { it == old } == 1 && replacement !in text) {
        "$path does not contain exactly one expected setting $old"
    }
    path.writeText(text.replace(old, replacement))
}

fun offlineUuid(username: String): String {
    val bytes = MessageDigest.getInstance("MD5").digest("OfflinePlayer:$username".toByteArray())
    bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x30).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
    val value = java.nio.ByteBuffer.wrap(bytes)
    return UUID(value.long, value.long).toString().replace("-", "")
}
