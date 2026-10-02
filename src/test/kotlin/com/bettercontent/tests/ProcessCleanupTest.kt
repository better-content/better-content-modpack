package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream
import kotlin.io.path.readText

@Tag("fast")
class ProcessCleanupTest {
    private fun start(root: Path, script: String) = ManagedProcess(
        "cleanup-probe", listOf("python3", "-u", "-c", script), root, root.resolve("probe.log"),
    )

    private fun await(description: String, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (!predicate() && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue(predicate(), description)
    }

    @Test
    fun closesAnOrdinaryProcessAndCanCloseAgain(@TempDir root: Path) {
        val process = start(root, "import time; print('ready'); time.sleep(60)")
        try {
            process.waitForLog(Regex("ready"), Duration.ofSeconds(5), "probe readiness")
            process.stop(Duration.ofSeconds(1))
            assertFalse(process.alive)
            process.close()
        } finally { ProcessHandle.of(process.pid).ifPresent { it.destroyForcibly() } }
    }

    @Test
    fun escalatesWhenTheProcessIgnoresTermination(@TempDir root: Path) {
        val process = start(root, "import signal,time; signal.signal(signal.SIGTERM, signal.SIG_IGN); print('ready'); time.sleep(60)")
        try {
            process.waitForLog(Regex("ready"), Duration.ofSeconds(5), "signal handler installation")
            assertTimeoutPreemptively(Duration.ofSeconds(8)) { process.stop(Duration.ofMillis(100)) }
            assertFalse(process.alive)
        } finally { ProcessHandle.of(process.pid).ifPresent { it.destroyForcibly() } }
    }

    @Test
    fun retainsAndTerminatesAChildAfterItsParentExits(@TempDir root: Path) {
        val process = start(root, """
            import subprocess,sys
            child = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(60)'])
            print('child=' + str(child.pid))
            input()
        """.trimIndent())
        var child: ProcessHandle? = null
        try {
            process.waitForLog(Regex("child=\\d+"), Duration.ofSeconds(5), "child creation")
            val pid = Regex("child=(\\d+)").find(process.log.readText())!!.groupValues[1].toLong()
            child = ProcessHandle.of(pid).orElseThrow()
            await("child must be observed before the parent exits") { pid in process.trackedPids }
            process.send("exit")
            await("parent must exit independently") { !process.alive }
            assertTrue(child.isAlive)
            process.stop(Duration.ofMillis(100))
            assertFalse(child.isAlive)
        } finally {
            child?.destroyForcibly()
            ProcessHandle.of(process.pid).ifPresent { it.destroyForcibly() }
        }
    }

    @Test
    fun cleanupAttemptsEveryFixtureAndReportsSurvivingPids() {
        val closed = mutableListOf<String>()
        val original = ProcessCleanupException(listOf(123L, 456L))
        val later = IllegalStateException("display cleanup failed")
        var event: Map<String, Any?>? = null
        val error = assertThrows(IllegalStateException::class.java) {
            cleanupFixtures(listOf(
                AutoCloseable { closed.add("client"); throw original },
                AutoCloseable { closed.add("server") },
                AutoCloseable { closed.add("display"); throw later },
            )) { event = it }
        }
        assertEquals(listOf("client", "server", "display"), closed)
        assertSame(original, error.cause)
        assertSame(later, error.suppressed.single())
        assertEquals(false, event!!["complete"])
        assertEquals(listOf(123L, 456L), event["surviving_pids"])
    }

    @Test
    fun cleanupReportsCompletionOnlyAfterEveryFixtureClosed() {
        var closed = 0
        cleanupFixtures(listOf(AutoCloseable { closed++ }, AutoCloseable { closed++ })) { event ->
            assertEquals(2, closed)
            assertEquals(true, event["complete"])
            assertEquals(emptyList<Long>(), event["surviving_pids"])
        }
    }

    @Test
    fun evidenceWriteFailureDoesNotMaskCleanupFailure() {
        val cleanup = ProcessCleanupException(listOf(123L))
        val reporting = java.io.IOException("evidence directory is full")
        val error = assertThrows(IllegalStateException::class.java) {
            cleanupFixtures(listOf(AutoCloseable { throw cleanup })) { throw reporting }
        }
        assertSame(cleanup, error.cause)
        assertSame(reporting, error.suppressed.single())
        assertEquals(listOf(123L), (error.cause as ProcessCleanupException).survivingPids)
    }

    @Test
    fun terminationDeadlineFailsWhenAnOperatingSystemHandleRemainsAlive() {
        // A refused signal is an OS boundary condition; do not leave a real unkillable process behind.
        val refusing = object : ProcessHandle {
            override fun pid() = 987L
            override fun isAlive() = true
            override fun destroy() = false
            override fun destroyForcibly() = false
            override fun supportsNormalTermination() = true
            override fun children(): Stream<ProcessHandle> = Stream.empty()
            override fun descendants(): Stream<ProcessHandle> = Stream.empty()
            override fun parent(): Optional<ProcessHandle> = Optional.empty()
            override fun onExit(): CompletableFuture<ProcessHandle> = CompletableFuture()
            override fun info(): ProcessHandle.Info = ProcessHandle.current().info()
            override fun compareTo(other: ProcessHandle) = pid().compareTo(other.pid())
        }
        val tracker = ProcessTracker(refusing)
        val error = assertThrows(ProcessCleanupException::class.java) {
            tracker.stop(Duration.ZERO, Duration.ZERO)
        }
        assertEquals(listOf(987L), error.survivingPids)
    }
}
