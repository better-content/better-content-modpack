package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

@Tag("fast")
class BoundedCommandTest {
    private fun directory(): Path = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "bounded-")
    @Test fun `large successful and nonzero output cannot block on an undrained pipe`() {
        val root = directory()
        try {
            for (exit in listOf(0, 7)) {
                val log = root.resolve("output-$exit.log")
                assertEquals(exit, BoundedCommand.run(root, listOf("python3", "-c",
                    "import sys; sys.stdout.write('x'*2000000); sys.stderr.write('y'*2000000); sys.exit($exit)"), log, Duration.ofSeconds(15)))
                assertEquals(4000000, Files.size(log))
            }
        } finally { root.toFile().deleteRecursively() }
    }
    @Test fun `hang deadline preserves diagnostics and terminates parent and observed child`() {
        val root = directory()
        try {
            val failure = assertThrows(IllegalStateException::class.java) {
                BoundedCommand.run(root, listOf("python3", "-c",
                    "import subprocess,time; p=subprocess.Popen(['sleep','30']); print(p.pid,flush=True); time.sleep(30)"),
                    root.resolve("hang.log"), Duration.ofMillis(300))
            }
            assertTrue(failure.message!!.contains("timed out"))
            assertTrue(Files.isRegularFile(root.resolve("hang.log.diagnostic.txt")))
            val child = Files.readString(root.resolve("hang.log")).trim().toLong()
            assertFalse(ProcessHandle.of(child).map { it.isAlive }.orElse(false))
        } finally { root.toFile().deleteRecursively() }
    }
    @Test fun `immediately exiting parent cannot leave its inherited-group child behind`() {
        val root = directory()
        var child: ProcessHandle? = null
        try {
            assertEquals(0, BoundedCommand.run(root, listOf("python3", "-c",
                "import subprocess; p=subprocess.Popen(['sleep','30']); print(p.pid,flush=True)"),
                root.resolve("orphan.log"), Duration.ofSeconds(5)))
            child = ProcessHandle.of(Files.readString(root.resolve("orphan.log")).trim().toLong()).orElse(null)
            assertFalse(child?.isAlive == true)
        } finally { child?.destroyForcibly(); root.toFile().deleteRecursively() }
    }
    @Test fun `diagnostic failure does not replace timeout`() {
        val root = directory()
        try {
            Files.createDirectory(root.resolve("failure.log.diagnostic.txt"))
            val failure = assertThrows(IllegalStateException::class.java) {
                BoundedCommand.run(root, listOf("sleep", "30"), root.resolve("failure.log"), Duration.ofMillis(100))
            }
            assertTrue(failure.message!!.contains("timed out"))
            assertTrue(failure.suppressed.isNotEmpty())
        } finally { root.toFile().deleteRecursively() }
    }
    @Test fun `thread cancellation closes its process and preserves interruption`() {
        val root = directory()
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable>()
        val interrupted = java.util.concurrent.atomic.AtomicBoolean()
        val worker = Thread {
            try {
                BoundedCommand.run(root, listOf("python3", "-c", "import os,time; print(os.getpid(),flush=True); time.sleep(30)"),
                    root.resolve("cancel.log"), Duration.ofSeconds(30))
            } catch (error: Throwable) { failure.set(error) }
            finally { interrupted.set(Thread.currentThread().isInterrupted) }
        }
        try {
            worker.start()
            val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
            while ((!Files.exists(root.resolve("cancel.log")) || Files.size(root.resolve("cancel.log")) == 0L) && System.nanoTime() < deadline) Thread.sleep(10)
            val pid = Files.readString(root.resolve("cancel.log")).trim().toLong()
            worker.interrupt(); worker.join(10_000)
            assertFalse(worker.isAlive)
            assertTrue(failure.get() is InterruptedException)
            assertTrue(interrupted.get())
            assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
        } finally { worker.interrupt(); worker.join(10_000); root.toFile().deleteRecursively() }
    }
    @Test fun `nested deadline consumes one absolute budget`() {
        var now = 1_000L
        val deadline = ScenarioDeadline(Duration.ofNanos(180), { now })
        now += 90
        assertEquals(Duration.ofNanos(90), deadline.requireRemaining())
        now += 90
        assertEquals(Duration.ZERO, deadline.remaining())
        assertThrows(IllegalStateException::class.java) { deadline.requireRemaining() }
    }
}
