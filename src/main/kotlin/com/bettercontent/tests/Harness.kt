package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.BufferedWriter
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText

/** The two immutable inputs to a session. Preparing a session never builds these. */
data class CandidatePair(
    val release: Path,
    val client: Path,
    val server: Path,
    val clientSha256: String,
    val serverSha256: String,
) {
    fun assertUnchanged() {
        check(Hashes.sha256(client) == clientSha256) { "client candidate changed: $client" }
        check(Hashes.sha256(server) == serverSha256) { "server candidate changed: $server" }
    }
    fun document(): Map<String, String> = linkedMapOf(
        "client" to client.toString(), "server" to server.toString(),
        "client_sha256" to clientSha256, "server_sha256" to serverSha256,
    )
}

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
        val dist = root.resolve("dist")
        require(dist.isDirectory()) { "dist/ is absent; supply --client ZIP --server ZIP or explicitly prepare a distribution" }
        fun candidates(side: String) = Files.walk(dist).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.endsWith("$side/better-content.zip") }.sorted().toList()
        }
        val clients = candidates("client")
        val servers = candidates("server")
        require(clients.size == 1 && servers.size == 1) {
            "dist/ must contain exactly one ZIP pair (found ${clients.size}/${servers.size}); supply explicit paths"
        }
        require(clients.single().parent.parent == servers.single().parent.parent) { "candidate ZIPs belong to different releases" }
        return locate(clients.single(), servers.single())
    }

    fun locate(client: Path, server: Path): CandidatePair {
        val c = client.toRealPath()
        val s = server.toRealPath()
        require(c != s && Files.isRegularFile(c) && Files.isRegularFile(s)) { "two distinct packaged ZIP files are required" }
        return CandidatePair(c.parent.parent, c, s, Hashes.sha256(c), Hashes.sha256(s))
    }
}

object ZipContracts {
    fun soleTopLevelDirectory(zip: Path): String = ZipFile(zip.toFile()).use { archive ->
        val roots = archive.entries().asSequence().map { it.name.substringBefore('/') }.filter(String::isNotBlank).toSet()
        require(roots.size == 1) { "server ZIP must have one top-level directory: $roots" }
        roots.single()
    }

    fun extract(zip: Path, destination: Path) {
        val base = destination.toAbsolutePath().normalize().also { it.createDirectories() }
        ZipFile(zip.toFile()).use { archive ->
            archive.entries().asSequence().forEach { entry ->
                val target = base.resolve(entry.name).normalize()
                require(target.startsWith(base) && target != base) { "unsafe ZIP entry: ${entry.name}" }
                if (entry.isDirectory) target.createDirectories() else {
                    target.parent.createDirectories()
                    archive.getInputStream(entry).use { Files.copy(it, target) }
                }
            }
        }
    }
}

/** Events and a single terminal result; logs/worlds are never pruned by the runner. */
class SessionEvidence(val root: Path, val runId: String, val tier: String, val pair: CandidatePair) {
    private val mapper = jacksonObjectMapper()
    val directory = root.resolve("generated/test-evidence/$runId/$tier")
    val fixture = directory.resolve("fixture")
    private val started = Instant.now()

    init {
        require(runId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}"))) { "unsafe run ID" }
        directory.parent.createDirectories()
        // Reusing an old run must never overwrite a previous pass or masquerade as fresh evidence.
        Files.createDirectory(directory)
        fixture.createDirectories()
        event("session_started", mapOf("tier" to tier, "candidates" to pair.document()))
        summary("running", null)
    }

    @Synchronized fun event(type: String, fields: Map<String, Any?> = emptyMap()) {
        val event = linkedMapOf<String, Any?>("time" to Instant.now().toString(), "type" to type).apply { putAll(fields) }
        val line = mapper.writeValueAsString(event)
        Files.writeString(directory.resolve("events.jsonl"), "$line\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        println("[$tier] $line")
    }

    fun finish(failure: Throwable?) {
        event("session_finished", mapOf("success" to (failure == null), "failure" to failure?.stackTraceToString()))
        summary(if (failure == null) "passed" else "failed", failure?.message)
        println("evidence: $directory")
    }

    private fun summary(status: String, failure: String?) {
        val temp = directory.resolve(".run.json.tmp")
        mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), linkedMapOf(
            "schema" to "bc.session_run.v2", "run_id" to runId, "tier" to tier, "status" to status,
            "started_at" to started.toString(), "updated_at" to Instant.now().toString(),
            "candidates" to pair.document(), "failure" to failure, "fixture" to fixture.toString(),
        ))
        Files.move(temp, directory.resolve("run.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
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
        val builder = ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
        builder.environment().putAll(environment)
        process = builder.start()
        input = process.outputStream.bufferedWriter()
        tracker = ProcessTracker(process.toHandle())
    }

    val alive: Boolean get() = process.isAlive
    val pid: Long get() = process.pid()
    internal val trackedPids: Set<Long> get() = tracker.trackedPids()

    fun send(line: String) {
        check(process.isAlive) { "$name is not running; see $log" }
        input.write(line); input.newLine(); input.flush()
    }

    fun waitForLog(pattern: Regex, timeout: Duration, description: String) =
        waitUntil(timeout, description) { pattern.containsMatchIn(if (log.exists()) log.readText() else "") }

    fun waitUntil(timeout: Duration, description: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (true) {
            if (condition()) return
            check(alive) { "$name exited before $description; see $log" }
            if (System.nanoTime() >= deadline) {
                captureDiagnostics(log.parent.resolve("$name-timeout"))
                error("timed out waiting for $description; see $log")
            }
            Thread.sleep(250)
        }
    }

    fun waitForExit(timeout: Duration): Int {
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            captureDiagnostics(log.parent.resolve("$name-stop-timeout"))
            error("$name did not exit within $timeout; see $log")
        }
        return process.exitValue()
    }

    fun captureDiagnostics(prefix: Path) {
        runCatching {
            ProcessBuilder("ps", "-ef", "--forest").redirectOutput(prefix.resolveSibling("${prefix.fileName}-processes.txt").toFile())
                .start().waitFor(10, TimeUnit.SECONDS)
        }
        (sequenceOf(process.toHandle()) + process.descendants().toList().asSequence()).filter { it.isAlive }.forEach { handle ->
            runCatching {
                val probe = ProcessBuilder("jcmd", handle.pid().toString(), "Thread.print").redirectErrorStream(true)
                    .redirectOutput(prefix.resolveSibling("${prefix.fileName}-${handle.pid()}-threads.txt").toFile()).start()
                if (!probe.waitFor(10, TimeUnit.SECONDS)) probe.destroyForcibly()
            }
        }
    }

    fun stop(timeout: Duration = Duration.ofSeconds(20)) {
        runCatching { input.close() }
        tracker.stop(timeout)
    }
    override fun close() = stop()
}

object Commands {
    fun run(command: List<String>, cwd: Path, log: Path, environment: Map<String, String> = emptyMap(), timeout: Duration = Duration.ofMinutes(30)) {
        ManagedProcess("preparation", command, cwd, log, environment).use { process ->
            check(process.waitForExit(timeout) == 0) { "command failed: ${command.joinToString(" ")}; see $log" }
        }
    }
}

/** Byte offsets stay byte offsets even when earlier log records contain Unicode. */
data class LogSlice(val text: String, val nextOffset: Long)
fun readLogFrom(path: Path, offset: Long): LogSlice {
    require(offset >= 0)
    Files.newByteChannel(path).use { channel ->
        check(channel.size() >= offset) { "log was truncated during the session: $path" }
        channel.position(offset)
        val bytes = java.io.ByteArrayOutputStream()
        val buffer = java.nio.ByteBuffer.allocate(8192)
        while (channel.read(buffer) > 0) {
            bytes.write(buffer.array(), 0, buffer.position())
            buffer.clear()
        }
        return LogSlice(bytes.toString(Charsets.UTF_8), channel.position())
    }
}

fun freePort(): Int = ServerSocket(0).use { it.localPort }
