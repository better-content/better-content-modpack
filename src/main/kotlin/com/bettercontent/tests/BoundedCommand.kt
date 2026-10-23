package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

/** One absolute monotonic budget, shared by nested operations (diagnostics/cleanup are separate). */
class ScenarioDeadline(budget: Duration, private val clock: () -> Long = System::nanoTime) {
    private val end = clock() + budget.toNanos().also { require(it > 0) }
    fun remaining(): Duration = Duration.ofNanos((end - clock()).coerceAtLeast(0))
    fun requireRemaining(): Duration = remaining().also { check(!it.isZero) { "scenario deadline exhausted" } }
    fun sleep(duration: Duration) {
        val remaining = requireRemaining()
        TimeUnit.NANOSECONDS.sleep(minOf(duration.toNanos(), remaining.toNanos()))
        requireRemaining()
    }
}

/** File-backed output avoids wait-before-drain deadlock. Never replaces the primary failure. */
object BoundedCommand {
    fun run(cwd: Path, command: List<String>, log: Path, budget: Duration,
            environment: Map<String, String> = emptyMap(), input: String? = null): Int {
        require(command.isNotEmpty() && !budget.isNegative && !budget.isZero)
        val timing = TimingRecords.process(log.toAbsolutePath(), cwd, command, budget, environment)
        var status: Int? = null
        var primary: Throwable? = null
        try {
            return runOwned(cwd, command, log, budget, environment, input, timing).also { status = it }
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            val outcome = when {
                primary is InterruptedException || primary is java.util.concurrent.CancellationException -> "CANCELLED"
                primary?.message?.startsWith("command timed out after ") == true -> "TIMED_OUT"
                primary != null -> "FAILED"
                status == 0 -> "SUCCEEDED"
                else -> "FAILED"
            }
            // FileChannel locks are interruptible. Persist cancellation before restoring the flag.
            val interrupted = Thread.interrupted()
            try { timing.finish(outcome, status, primary) }
            catch (timingFailure: Throwable) {
                val original = primary
                if (original != null) original.addSuppressed(timingFailure)
                else if (status != null && status != 0) {
                    // Preserve the exact nonzero process status; it cannot become acceptance.
                    System.err.println("timing recording failed for $log: $timingFailure")
                } else throw timingFailure
            } finally { if (interrupted) Thread.currentThread().interrupt() }
        }
    }

    private fun runOwned(cwd: Path, command: List<String>, log: Path, budget: Duration,
                         environment: Map<String, String>, input: String?, timing: TimingRecords.Observation): Int {
        val output = log.toAbsolutePath()
        require(output == output.normalize()) { "command output must not contain traversal components" }
        val home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize()
        val pack = Path.of(System.getProperty("bc.repo.root", ".")).toAbsolutePath().normalize()
        val approved = listOf(home.resolve(".tmp"), pack.parent.resolve("workspace_artifacts"),
            pack.resolve("build"), pack.resolve("generated"))
        require(approved.any(output::startsWith)) { "command output must be inside approved temporary/output roots" }
        var ancestor: Path? = output
        while (ancestor != null) {
            require(!Files.isSymbolicLink(ancestor)) { "command output has a symlink ancestor: $ancestor" }
            ancestor = ancestor.parent
        }
        Files.createDirectories(output.parent)
        val inputFile = input?.let { text -> Files.createTempFile(output.parent, ".command-input-", ".txt").also { Files.writeString(it, text) } }
        val process = try {
            ProcessBuilder(listOf("setsid", "--wait") + command).directory(cwd.toFile()).redirectErrorStream(true)
                .redirectOutput(output.toFile()).apply {
                    environment().putAll(environment)
                    // A logical selector names this invocation, never arbitrary nested commands.
                    environment().remove("BC_TIMING_PROCESS_NAME")
                    if (inputFile != null) redirectInput(inputFile.toFile())
                }.start()
        } catch (failure: Throwable) { inputFile?.let(Files::deleteIfExists); throw failure }
        val tracker = ProcessTracker(process.toHandle(), process.pid())
        var primary: Throwable? = null
        try {
            check(process.waitFor(budget.toMillis().coerceAtLeast(1), TimeUnit.MILLISECONDS)) {
                "command timed out after $budget: ${command.joinToString(" ")}; cwd=$cwd; pid=${process.pid()}; log=$output; tail=${tail(output)}"
            }
            val status = process.exitValue()
            if (status != 0) {
                val exitFailure = IllegalStateException("command failed ($status): $command; cwd=$cwd; log=$output")
                primary = exitFailure
                runCatching { Files.writeString(output.resolveSibling(output.fileName.toString() + ".diagnostic.txt"),
                    "command=$command\ncwd=$cwd\npid=${process.pid()}\nexit=$status\ntracked=${tracker.trackedPids()}\ntail=${tail(output)}\n")
                }.exceptionOrNull()?.let { exitFailure.addSuppressed(it); throw exitFailure }
            }
            return status
        } catch (failure: Throwable) {
            primary = failure
            // Direct ProcessHandle observations have no diagnostic subprocess that can hang.
            runCatching {
                val diagnostics = output.resolveSibling(output.fileName.toString() + ".diagnostic.txt")
                Files.writeString(diagnostics, "command=$command\ncwd=$cwd\npid=${process.pid()}\n" +
                    "tracked=${tracker.trackedPids()}\nalive=${process.isAlive}\n" +
                    "cpu=${process.info().totalCpuDuration().orElse(null)}\ntail=${tail(output)}\n")
            }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        } finally {
            try {
                tracker.stop(Duration.ofSeconds(2), Duration.ofSeconds(5))
                timing.cleanupComplete = true
            }
            catch (cleanup: Throwable) {
                timing.cleanupComplete = false
                val original = primary
                if (original != null) { original.addSuppressed(cleanup); throw original } else throw cleanup
            }
            finally {
                timing.processExitCode = runCatching { process.exitValue() }.getOrNull()
                runCatching { inputFile?.let(Files::deleteIfExists) }.exceptionOrNull()?.let {
                    val original = primary
                    if (original != null) original.addSuppressed(it) else throw it
                }
                if (primary is InterruptedException) Thread.currentThread().interrupt()
            }
        }
    }

    fun capture(cwd: Path, command: List<String>, budget: Duration, limit: Int = 1024 * 1024): String {
        val directory = Path.of(System.getProperty("user.home"), ".tmp", "bounded-commands")
        Files.createDirectories(directory)
        val log = Files.createTempFile(directory, "command-", ".log")
        var successful = false
        try {
            val status = run(cwd, command, log, budget)
            check(status == 0) { "command failed ($status): $command; cwd=$cwd; log=$log; tail=${tail(log)}" }
            check(Files.size(log) <= limit) { "command output exceeds capture limit $limit; log=$log" }
            val result = Files.readString(log)
            successful = true
            return result
        } finally {
            if (successful) Files.deleteIfExists(log)
        }
    }

    private fun tail(path: Path, bytes: Int = 16384): String = Files.newByteChannel(path).use { channel ->
        channel.position((channel.size() - bytes).coerceAtLeast(0))
        val buffer = java.nio.ByteBuffer.allocate(bytes)
        channel.read(buffer)
        String(buffer.array(), 0, buffer.position(), Charsets.UTF_8)
    }
}
