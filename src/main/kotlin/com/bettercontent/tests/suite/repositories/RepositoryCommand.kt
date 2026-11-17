package com.bettercontent.tests.suite.repositories

import com.bettercontent.tests.ProcessCleanupException
import com.bettercontent.tests.ProcessTracker
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

internal data class RepositoryProcessIdentity(val pid: Long, val startedAt: String)
internal data class RepositoryCommandExecution(val exitCode: Int, val timedOut: Boolean,
    val ownedProcesses: List<RepositoryProcessIdentity>, val strayDescendantPids: List<Long>,
    val survivingPids: List<Long>,
    val cleanupComplete: Boolean, val cleanupFailure: String?, val commandError: String?)

/** Own every descendant, including ones still alive after Gradle/Minecraft's launcher exits. */
internal object RepositoryCommand {
    internal fun needsNaturalExitWait(alive: Boolean, recordedBirth: Instant?, currentBirth: Instant?): Boolean =
        alive && (recordedBirth == null || currentBirth == null || recordedBirth == currentBirth)

    private fun liveOwnedDescendants(tracker: ProcessTracker, rootPid: Long): List<Long> {
        val starts = tracker.processStarts()
        return tracker.trackedPids().filter { pid ->
            // Discovery records a handle before its birth. Missing either birth is pending,
            // never evidence of natural exit; a known DIFFERENT birth is not our process.
            pid != rootPid && ProcessHandle.of(pid).map {
                needsNaturalExitWait(it.isAlive, starts[pid], it.info().startInstant().orElse(null))
            }.orElse(false)
        }.sorted()
    }

    fun execute(command: List<String>, root: Path, environment: Map<String, String>, stdout: Path,
        stderr: Path, timeout: Duration): RepositoryCommandExecution {
        require(command.isNotEmpty() && timeout > Duration.ZERO && !java.nio.file.Files.exists(stdout) &&
            !java.nio.file.Files.exists(stderr)) { "fresh distinct stdout/stderr paths required" }
        val began = System.nanoTime()
        val budget = timeout.toNanos()
        fun remaining() = budget - (System.nanoTime() - began)
        var process: Process? = null
        var tracker: ProcessTracker? = null
        var exitCode = -1
        var timedOut = false
        var failure: String? = null
        var cleanupFailure: String? = null
        var cleanupSurvivors: List<Long> = emptyList()
        var strayDescendantPids: List<Long> = emptyList()
        try {
            val builder = ProcessBuilder(command).directory(root.toFile())
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile())
            val env = builder.environment()
            val path = env["PATH"] ?: ""
            val home = env["HOME"] ?: ""
            env.clear(); env["PATH"] = path; env["HOME"] = home; env["LANG"] = "C.UTF-8"; env["TZ"] = "UTC"
            env.putAll(environment)
            process = builder.start()
            val observed = ProcessTracker(process.toHandle())
            tracker = observed
            val available = remaining()
            if (available <= 0L || !process.waitFor(available, TimeUnit.NANOSECONDS)) {
                timedOut = true
                failure = "verification command timed out"
            } else {
                exitCode = process.exitValue()
                // A single-use Gradle daemon can finish normal shutdown just after its launcher.
                // Wait for natural exit only within the ORIGINAL command budget, without signals.
                while (liveOwnedDescendants(observed, process.pid()).isNotEmpty()) {
                    val left = remaining()
                    if (left <= 0L) {
                        timedOut = true
                        failure = "verification command descendants did not exit within deadline"
                        break
                    }
                    TimeUnit.NANOSECONDS.sleep(minOf(left, TimeUnit.MILLISECONDS.toNanos(25)))
                }
            }
        } catch (error: Exception) {
            failure = "verification command failed: ${error.javaClass.simpleName}: ${error.message}"
            if (error is InterruptedException) Thread.currentThread().interrupt()
        } finally {
            val launched = process
            if (tracker != null) {
                if (process?.isAlive == false) {
                    // Keep live strays even when the descendant deadline expired; forced cleanup
                    // below cannot turn that execution into a naturally completed success.
                    strayDescendantPids = liveOwnedDescendants(tracker, process.pid())
                }
                try { tracker.stop(Duration.ofSeconds(2), Duration.ofSeconds(5)) }
                catch (error: Exception) {
                    cleanupFailure = "${error.javaClass.simpleName}: ${error.message}"
                    cleanupSurvivors = (error as? ProcessCleanupException)?.survivingPids.orEmpty()
                    if (error is InterruptedException) Thread.currentThread().interrupt()
                }
            } else if (launched?.isAlive == true) {
                // Defensive fallback if tracker construction itself failed; fail closed below.
                launched.toHandle().descendants().use { descendants -> descendants.forEach { it.destroyForcibly() } }
                launched.destroyForcibly()
                runCatching { launched.waitFor(5, TimeUnit.SECONDS) }
                cleanupFailure = "process tracker unavailable; descendant cleanup not proven"
            }
        }
        if (!java.nio.file.Files.exists(stdout)) {
            java.nio.file.Files.createFile(stdout)
            if (process != null) cleanupFailure = listOfNotNull(cleanupFailure, "raw stdout was removed").joinToString("; ")
        }
        if (!java.nio.file.Files.exists(stderr)) {
            java.nio.file.Files.createFile(stderr)
            if (process != null) cleanupFailure = listOfNotNull(cleanupFailure, "raw stderr was removed").joinToString("; ")
        }
        val starts = tracker?.processStarts().orEmpty()
        val pids = tracker?.trackedPids().orEmpty()
        val identities = starts.toSortedMap().map { (pid, start) -> RepositoryProcessIdentity(pid, start.toString()) }
        val missingStarts = pids - starts.keys
        val survivors = (cleanupSurvivors + pids.filter { pid ->
            ProcessHandle.of(pid).map { it.isAlive && it.info().startInstant().orElse(Instant.MIN) == starts[pid] }.orElse(false)
        }).distinct().sorted()
        if (missingStarts.isNotEmpty()) cleanupFailure = listOfNotNull(cleanupFailure,
            "missing start identities for owned PIDs ${missingStarts.sorted()}").joinToString("; ")
        if (survivors.isNotEmpty()) cleanupFailure = listOfNotNull(cleanupFailure,
            "surviving owned PIDs $survivors").joinToString("; ")
        return RepositoryCommandExecution(exitCode, timedOut, identities, strayDescendantPids, survivors,
            cleanupFailure == null, cleanupFailure, failure)
    }
}
