package com.bettercontent.tests

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** A correctness failure, independent of whether every owned process was terminated. */
class ProcessCorrectnessException(message: String) : IllegalStateException(message)

private class FixtureCloseException(val failures: List<Exception>) : IllegalStateException(
    "${failures.size} fixture cleanup operation(s) failed", failures.first(),
) {
    init { failures.drop(1).forEach(::addSuppressed) }
}

class ProcessCleanupException(val survivingPids: List<Long>) : IllegalStateException(
    "process cleanup failed; surviving PIDs: ${survivingPids.joinToString()}",
)

/** Retains observed descendants even after their original parent exits. */
internal class ProcessTracker(private val root: ProcessHandle, private val ownedGroup: Long? = null) {
    private val handles = ConcurrentHashMap<Long, ProcessHandle>().apply { put(root.pid(), root) }
    private val observer = Executors.newSingleThreadScheduledExecutor { action ->
        Thread(action, "process-tree-${root.pid()}").apply { isDaemon = true }
    }

    init {
        discover()
        observer.scheduleWithFixedDelay(::discover, 0, 25, TimeUnit.MILLISECONDS)
    }

    private fun discover() {
        // setsid keeps the command's group distinct from every other lane consumer. The
        // group survives an immediately exiting parent, closing the polling race for
        // ordinary inherited-group children. Already observed new-session children remain
        // tracked by identity. This is not a claim to catch deliberately detached escapees.
        if (ownedGroup != null && !root.isAlive) {
            java.nio.file.Files.newDirectoryStream(java.nio.file.Path.of("/proc")).use { entries ->
                entries.filter { it.fileName.toString().toLongOrNull() != null }.forEach { directory ->
                    runCatching {
                        val fields = java.nio.file.Files.readString(directory.resolve("stat"))
                            .substringAfterLast(')').trim().split(' ')
                        if (fields.getOrNull(2)?.toLongOrNull() == ownedGroup) {
                            ProcessHandle.of(directory.fileName.toString().toLong()).ifPresent { handles.putIfAbsent(it.pid(), it) }
                        }
                    }
                }
            }
        }
        handles.values.toList().filter { it.isAlive }.forEach { handle ->
            handle.descendants().use { descendants -> descendants.forEach { handles.putIfAbsent(it.pid(), it) } }
        }
    }

    internal fun trackedPids(): Set<Long> = handles.keys.toSet()

    @Synchronized
    fun stop(grace: Duration, force: Duration = Duration.ofSeconds(5)) {
        require(!grace.isNegative && !force.isNegative)
        try {
            terminate(false, grace)
            terminate(true, force)
            val survivors = handles.values.filter { it.isAlive }.map { it.pid() }.sorted()
            if (survivors.isNotEmpty()) throw ProcessCleanupException(survivors)
        } finally {
            observer.shutdownNow()
        }
    }

    private fun terminate(force: Boolean, timeout: Duration) {
        val signalled = mutableSetOf<Long>()
        val deadline = System.nanoTime() + timeout.toNanos()
        do {
            discover()
            val alive = handles.values.filter { it.isAlive }.sortedByDescending { it.pid() }
            if (alive.isEmpty()) return
            alive.filter { signalled.add(it.pid()) }.forEach {
                if (force) it.destroyForcibly() else it.destroy()
            }
            if (System.nanoTime() >= deadline) return
            Thread.sleep(25)
        } while (true)
    }
}

/** Always attempts every resource; failures retain their original exception and PID details. */
fun closeAll(vararg resources: AutoCloseable?) {
    val failures = resources.filterNotNull().mapNotNull { resource ->
        try { resource.close(); null } catch (failure: Exception) { failure }
    }
    if (failures.isNotEmpty()) {
        throw FixtureCloseException(failures)
    }
}

fun cleanupFixtures(resources: List<AutoCloseable>, report: (Map<String, Any?>) -> Unit) {
    val failure = runCatching { closeAll(*resources.toTypedArray()) }.exceptionOrNull()
    fun survivingPids(error: Throwable?): List<Long> = when (error) {
        null -> emptyList()
        else -> (if (error is ProcessCleanupException) error.survivingPids else emptyList()) +
            survivingPids(error.cause) + error.suppressed.flatMap(::survivingPids)
    }
    fun leaves(error: Throwable?): List<Throwable> = when (error) {
        null -> emptyList()
        is FixtureCloseException -> error.failures.flatMap(::leaves)
        else -> listOf(error) + error.suppressed.flatMap(::leaves)
    }
    val failures = leaves(failure)
    val correctness = failures.filterIsInstance<ProcessCorrectnessException>()
    val terminationFailures = failures.filterNot { it is ProcessCorrectnessException }
    val reportingFailure = runCatching {
        report(mapOf(
            "complete" to terminationFailures.isEmpty(),
            "surviving_pids" to survivingPids(failure).distinct().sorted(),
            "error" to terminationFailures.firstOrNull()?.message,
            "correctness_errors" to correctness.map { it.message },
        ))
    }.exceptionOrNull()
    if (failure != null) {
        reportingFailure?.let(failure::addSuppressed)
        throw failure
    }
    if (reportingFailure != null) throw reportingFailure
}
