package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Append-only observations, never a verification receipt or an inferred timeout policy.
 * Missing terminal records mean interrupted/UNKNOWN, not success. Only measured comparable
 * successful runs can supply an expected runtime; the configured cap is not that measurement.
 */
object TimingRecords {
    private val mapper = jacksonObjectMapper()
    private val metadata = listOf("RUN_ID", "REVISION", "INPUT_ID", "PROFILE", "ENVIRONMENT_ID", "CACHE_ID")
    // JVM FileLock rejects overlapping locks rather than waiting, so serialize local threads too.
    private val appendMonitor = Any()

    class Observation internal constructor(private val path: Path, private val fields: Map<String, Any?>,
                                           private val reportRoot: Path? = null) {
        private val start = System.nanoTime()
        internal var cleanupComplete: Boolean? = null
        internal var processExitCode: Int? = null
        init { append(path, fields + mapOf("event" to "start", "status" to "RUNNING", "duration_ns" to null)) }
        fun finish(status: String, exitCode: Int? = null, failure: Throwable? = null) {
            append(path, fields + mapOf("event" to "terminal", "status" to status,
                "duration_ns" to (System.nanoTime() - start).coerceAtLeast(0), "exit_code" to exitCode,
                "exception_type" to failure?.javaClass?.name, "cleanup_complete" to cleanupComplete,
                "process_exit_code" to processExitCode))
            // Explicit shared/profile sinks require a final reduction AFTER this process's
            // terminal event. Default capture-only siblings retain raw events without spawning
            // a Python interpreter for every control/Git query.
            reportRoot?.let { report(path, it) }
        }
    }

    fun process(log: Path, cwd: Path, command: List<String>, budget: Duration,
                environment: Map<String, String> = emptyMap()): Observation {
        fun env(key: String) = environment[key] ?: System.getenv(key)
        val configuredSink = env("BC_TIMING_FILE")?.takeIf(String::isNotBlank)
        val path = configuredSink?.let(Path::of) ?: log.resolveSibling(log.fileName.toString() + ".timing.jsonl")
        val fields = linkedMapOf<String, Any?>("schema" to "bc.timing.v1", "operation_id" to UUID.randomUUID().toString(),
            "scope" to "process", "units" to "nanoseconds", "clock" to "monotonic",
            "duration_boundary" to "supervision_including_cleanup",
            "configured_timeout_ns" to budget.toNanos(), "timeout_scope" to "process",
            "enclosing_process_timeout_ns" to null, "expected_runtime_ns" to null,
            "timeout_policy" to "UNKNOWN_UNTIL_COMPARABLE_SUCCESS_MEASURED", "cwd" to cwd.toAbsolutePath().toString(),
            "log" to log.toString(), "command_sha256" to MessageDigest.getInstance("SHA-256")
                .digest(command.joinToString("\u0000").toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
        metadata.forEach { key -> fields[key.lowercase()] = env("BC_TIMING_$key")?.takeIf(String::isNotBlank)
            ?: (if (key == "RUN_ID") env("BC_TEST_RUN_ID")?.takeIf(String::isNotBlank) else null) ?: "UNKNOWN" }
        environment["BC_TIMING_PROCESS_NAME"]?.takeIf(String::isNotBlank)?.let { fields["process_name"] = it }
        val output = path.toAbsolutePath()
        require(output == output.normalize()) { "timing output contains traversal components" }
        val home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize()
        val pack = Path.of(System.getProperty("bc.repo.root", ".")).toAbsolutePath().normalize()
        require(listOf(home.resolve(".tmp"), pack.parent.resolve("workspace_artifacts"),
            pack.resolve("build"), pack.resolve("generated")).any(output::startsWith)) {
            "timing output must be inside approved temporary/output roots"
        }
        return Observation(output, fields, if (!environment["BC_TIMING_FILE"].isNullOrBlank()) pack else null)
    }

    private fun report(path: Path, root: Path) {
        val reporter = root.resolve("scripts/timing_report.py")
        check(Files.isRegularFile(reporter)) { "automatic timing reporter is missing: $reporter" }
        val summary = path.resolveSibling(path.fileName.toString() + ".report.json")
        val log = path.resolveSibling(path.fileName.toString() + ".report." + UUID.randomUUID() + ".log")
        val process = ProcessBuilder("python3", "-B", reporter.toString(), "--allow-incomplete", "--output",
            summary.toString(), path.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start()
        var primary: Throwable? = null
        var interrupted = false
        try {
            // Fixed reporting safety cap, separate from every measured/configured test cap.
            check(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                "automatic timing report exceeded reporting safety cap; log=$log"
            }
            check(process.exitValue() == 0) { "automatic timing report failed (${process.exitValue()}); log=$log" }
        } catch (failure: Throwable) {
            primary = failure
            interrupted = failure is InterruptedException
            throw failure
        } finally {
            try {
                if (process.isAlive) {
                    process.destroyForcibly()
                    check(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        "automatic timing reporter cleanup incomplete; pid=${process.pid()}; log=$log"
                    }
                }
            } catch (cleanup: Throwable) {
                if (cleanup is InterruptedException) interrupted = true
                val original = primary
                if (original != null) original.addSuppressed(cleanup) else throw cleanup
            } finally { if (interrupted) Thread.currentThread().interrupt() }
        }
    }

    internal fun append(path: Path, record: Map<String, Any?>) = synchronized(appendMonitor) {
        var ancestor: Path? = path
        while (ancestor != null) {
            require(!Files.isSymbolicLink(ancestor)) { "timing output has a symlink ancestor: $ancestor" }
            ancestor = ancestor.parent
        }
        Files.createDirectories(path.parent)
        val bytes = mapper.writeValueAsBytes(record + ("observed_at" to Instant.now().toString())) + byteArrayOf(10)
        FileChannel.open(path, CREATE, WRITE, java.nio.file.LinkOption.NOFOLLOW_LINKS).use { channel ->
            channel.lock().use {
                channel.position(channel.size())
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
        }
    }
}
