package com.bettercontent.tests.release

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Bounded admission, not an executor-sized queue of all providers. Failed providers never
 * release consumers; already running gates finish (and retain their evidence/checkpoints).
 * Interruption cancels owned tasks and waits for their cleanup before releasing the barrier. */
internal class ParallelBuildScheduler(private val jobs: Int) {
    init { require(jobs in 1..4) }

    data class Result<T>(val completed: Map<String, T>, val failures: List<String>)

    fun <T> run(dependencies: Map<String, List<String>>, action: (String, Map<String, T>) -> T): Result<T> {
        require(dependencies.values.flatten().all { it in dependencies }) { "unknown build dependency" }
        val pending = dependencies.keys.toMutableSet()
        val completed = linkedMapOf<String, T>()
        val failures = mutableListOf<String>()
        val executor = Executors.newFixedThreadPool(jobs)
        val service = ExecutorCompletionService<Pair<String, kotlin.Result<T>>>(executor)
        var running = 0
        val admissionStopped = AtomicBoolean()
        try {
            while (pending.isNotEmpty() || running > 0) {
                if (!admissionStopped.get()) {
                    val ready = pending.filter { dependencies.getValue(it).all(completed::containsKey) }
                    for (name in ready.take(jobs - running)) {
                        if (admissionStopped.get()) break
                        val consumed = dependencies.getValue(name).associateWith(completed::getValue)
                        service.submit(Callable {
                            val result = runCatching { action(name, consumed) }
                            if (result.isFailure) admissionStopped.set(true)
                            name to result
                        })
                        pending.remove(name)
                        running++
                    }
                }
                if (running == 0) {
                    require(failures.isNotEmpty() || pending.isEmpty()) { "build dependency cycle: $pending" }
                    break
                }
                val (name, result) = service.take().get()
                running--
                result.fold({ completed[name] = it }, { failures += "$name: ${it.message ?: it.javaClass.name}" })
            }
            return Result(completed.toMap(), failures.toList())
        } finally {
            // No build/deploy barrier may pass while an owned worker is still active.
            executor.shutdownNow()
            var interrupted = Thread.interrupted()
            val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(2)
            var terminated = executor.isTerminated
            while (!terminated && System.nanoTime() < deadline) {
                try { terminated = executor.awaitTermination(100, TimeUnit.MILLISECONDS) }
                catch (_: InterruptedException) { interrupted = true }
            }
            if (interrupted) Thread.currentThread().interrupt()
            check(terminated) { "parallel source worker cleanup incomplete; deployment forbidden" }
        }
    }
}
