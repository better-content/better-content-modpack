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
    fun execute(command: List<String>, root: Path, environment: Map<String, String>, stdout: Path,
        stderr: Path, timeout: Duration): RepositoryCommandExecution {
        require(command.isNotEmpty() && timeout > Duration.ZERO && !java.nio.file.Files.exists(stdout) &&
            !java.nio.file.Files.exists(stderr)) { "fresh distinct stdout/stderr paths required" }
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
            tracker = ProcessTracker(process.toHandle())
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                timedOut = true
                failure = "verification command timed out"
            } else exitCode = process.exitValue()
        } catch (error: Exception) {
            failure = "verification command failed: ${error.javaClass.simpleName}: ${error.message}"
            if (error is InterruptedException) Thread.currentThread().interrupt()
        } finally {
            val launched = process
            if (tracker != null) {
                val rootPid = process?.pid()
                if (!timedOut && process?.isAlive == false) {
                    strayDescendantPids = tracker.trackedPids().filter { pid ->
                        pid != rootPid && ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
                    }.sorted()
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
