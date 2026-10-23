package com.bettercontent.tests

import com.bettercontent.tests.release.ParallelBuildScheduler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Tag("fast")
class ParallelBuildSchedulerTest {
    @Test fun independentProvidersReallyOverlapAndConsumerWaitsForBoth() {
        val overlap = CountDownLatch(2)
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val result = ParallelBuildScheduler(2).run(linkedMapOf(
            "a" to emptyList(), "b" to emptyList(), "consumer" to listOf("a", "b"),
        )) { name, consumed ->
            if (name != "consumer") {
                peak.accumulateAndGet(active.incrementAndGet()) { left, right -> maxOf(left, right) }
                overlap.countDown()
                check(overlap.await(5, TimeUnit.SECONDS)) { "independent providers did not overlap" }
                active.decrementAndGet()
            } else {
                assertEquals(mapOf("a" to "a-bytes", "b" to "b-bytes"), consumed)
                assertEquals(0, active.get())
            }
            "$name-bytes"
        }
        assertEquals(2, peak.get())
        assertTrue(result.failures.isEmpty())
        assertEquals(3, result.completed.size)
    }

    @Test fun failureStopsAdmissionButDrainsAlreadyRunningGate() {
        val started = CountDownLatch(2)
        val drained = AtomicBoolean()
        val result = ParallelBuildScheduler(2).run(linkedMapOf(
            "bad" to emptyList(), "running" to emptyList(), "queued" to emptyList(), "consumer" to listOf("bad"),
        )) { name, _ ->
            check(name in setOf("bad", "running")) { "admitted a provider after failure: $name" }
            started.countDown()
            check(started.await(5, TimeUnit.SECONDS))
            if (name == "bad") error("synthetic owning gate failed")
            Thread.sleep(100)
            drained.set(true)
            name
        }
        assertTrue(drained.get())
        assertEquals(setOf("running"), result.completed.keys)
        assertEquals(listOf("bad: synthetic owning gate failed"), result.failures)
    }

    @Test fun cycleAndUnknownDependenciesFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            ParallelBuildScheduler(2).run(mapOf("a" to listOf("a"))) { _, _ -> "unexpected" }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ParallelBuildScheduler(2).run(mapOf("a" to listOf("missing"))) { _, _ -> "unexpected" }
        }
    }

    @Test fun cancellationWaitsForWorkerFinalizers() {
        val started = CountDownLatch(1)
        val cleaned = AtomicBoolean()
        val cancelled = AtomicBoolean()
        val coordinator = Thread {
            try {
                ParallelBuildScheduler(1).run(mapOf("a" to emptyList())) { _, _ ->
                    try { started.countDown(); Thread.sleep(30_000); "unexpected" }
                    finally { cleaned.set(true) }
                }
            } catch (_: InterruptedException) { cancelled.set(true) }
        }
        coordinator.start()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        coordinator.interrupt()
        coordinator.join(5_000)
        assertFalse(coordinator.isAlive)
        assertTrue(cancelled.get())
        assertTrue(cleaned.get())
    }
}
