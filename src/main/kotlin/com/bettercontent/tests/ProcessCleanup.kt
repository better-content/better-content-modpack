package com.bettercontent.tests

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProcessCleanupException(val survivingPids: List<Long>) : IllegalStateException(
    "process cleanup failed; surviving PIDs: ${survivingPids.joinToString()}",
)

/** Retains observed descendants even after their original parent exits. */
internal class ProcessTracker(root: ProcessHandle) {
    private val handles = ConcurrentHashMap<Long, ProcessHandle>().apply { put(root.pid(), root) }
    private val observer = Executors.newSingleThreadScheduledExecutor { action ->
        Thread(action, "process-tree-${root.pid()}").apply { isDaemon = true }
    }

    init {
        discover()
        observer.scheduleWithFixedDelay(::discover, 0, 25, TimeUnit.MILLISECONDS)
    }

    private fun discover() {
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
        val combined = IllegalStateException("${failures.size} fixture cleanup operation(s) failed", failures.first())
        failures.drop(1).forEach(combined::addSuppressed)
        throw combined
    }
}

fun cleanupFixtures(resources: List<AutoCloseable>, report: (Map<String, Any?>) -> Unit) {
    val failure = runCatching { closeAll(*resources.toTypedArray()) }.exceptionOrNull()
    fun survivingPids(error: Throwable?): List<Long> = when (error) {
        null -> emptyList()
        else -> (if (error is ProcessCleanupException) error.survivingPids else emptyList()) +
            survivingPids(error.cause) + error.suppressed.flatMap(::survivingPids)
    }
    report(mapOf(
        "complete" to (failure == null),
        "surviving_pids" to survivingPids(failure).distinct().sorted(),
        "error" to failure?.let { it.cause?.message ?: it.message },
    ))
    if (failure != null) throw failure
}
