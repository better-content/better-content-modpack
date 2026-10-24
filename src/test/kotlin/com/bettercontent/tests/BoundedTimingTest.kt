package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Tag("fast")
class BoundedTimingTest {
    private val mapper = jacksonObjectMapper()
    private fun directory(): Path {
        val base = Path.of(System.getProperty("user.home"), ".tmp")
        Files.createDirectories(base)
        return Files.createTempDirectory(base, "bounded-timing-")
    }
    private fun terminal(log: Path) = Files.readAllLines(log.resolveSibling(log.fileName.toString() + ".timing.jsonl"))
        .map(mapper::readTree).also { assertEquals(listOf("start", "terminal"), it.map { row -> row["event"].asText() }) }.last()

    @Test fun `real successful measurement selects exact frozen cap and actually times out with child provenance`() {
        val root = directory()
        val baselineLog = root.resolve("measured.log")
        val baseline = root.resolve("measured.log.timing.jsonl")
        val metadata = mapOf("BC_TIMING_REVISION" to "controlled-fixture", "BC_TIMING_INPUT_ID" to "a".repeat(64),
            "BC_TIMING_PROFILE" to "controlled-process", "BC_TIMING_ENVIRONMENT_ID" to "inert-local",
            "BC_TIMING_CACHE_ID" to "none", "BC_TIMING_PROCESS_NAME" to "controlled-fixture",
            "BC_TIMING_BASELINES" to "")
        val command = listOf("python3", "-c", "import os,time; print(os.environ['BC_TIMING_TIMEOUT_NS'],flush=True); time.sleep(float(os.environ['CONTROLLED_SLEEP']))")
        assertEquals(0, BoundedCommand.run(root, command, baselineLog, Duration.ofSeconds(5),
            metadata + mapOf("BC_TIMING_FILE" to baseline.toString(), "CONTROLLED_SLEEP" to "0.2")))
        val measured = terminal(baselineLog)["duration_ns"].asLong()
        val actualLog = root.resolve("enforced.log")
        val actualSink = root.resolve("enforced.log.timing.jsonl")
        val started = System.nanoTime()
        val failure = assertThrows(IllegalStateException::class.java) {
            BoundedCommand.run(root, command, actualLog, Duration.ofSeconds(5), metadata + mapOf(
                "BC_TIMING_FILE" to actualSink.toString(), "CONTROLLED_SLEEP" to "10",
                "BC_TIMING_BASELINES" to mapper.writeValueAsString(listOf(baseline.toString()))))
        }
        assertTrue(failure.message!!.startsWith("command timed out after "))
        assertTrue(System.nanoTime() - started < Duration.ofSeconds(4).toNanos())
        val record = terminal(actualLog)
        assertEquals("TIMED_OUT", record["status"].asText())
        assertEquals(measured * 2, record["configured_timeout_ns"].asLong())
        assertEquals("EXACT_2X_EMPIRICAL_MEDIAN", record["timeout_policy"].asText())
        assertEquals(measured, record["expected_runtime_ns"].asLong())
        assertEquals(1, record["successful_sample_count"].asInt())
        assertTrue(record["selection_frozen_before_invocation"].asBoolean())
        assertEquals((measured * 2).toString(), Files.readString(actualLog).trim())
        assertEquals(command, record["command"].map { it.asText() })
        assertEquals(root.toString(), record["cwd"].asText())
        assertTrue(record["cleanup_complete"].asBoolean())
    }

    @Test fun `invalid explicit baselines retain honest legacy cap and deadline arithmetic does not overflow`() {
        val root = directory()
        val log = root.resolve("unknown-baseline.log")
        val sink = root.resolve("unknown-baseline.log.timing.jsonl")
        assertEquals(0, BoundedCommand.run(root, listOf("true"), log, Duration.ofSeconds(3), mapOf(
            "BC_TIMING_FILE" to sink.toString(), "BC_TIMING_BASELINES" to "[\"relative.jsonl\"]")))
        val record = terminal(log)
        assertEquals(3_000_000_000, record["configured_timeout_ns"].asLong())
        assertEquals("UNKNOWN_UNTIL_COMPARABLE_SUCCESS_MEASURED", record["timeout_policy"].asText())
        assertTrue(record["expected_runtime_ns"].isNull)
        var now = Long.MAX_VALUE - 10
        val deadline = ScenarioDeadline(Duration.ofNanos(100)) { now }
        now += 20 // monotonic counters may wrap; elapsed subtraction remains valid
        assertEquals(Duration.ofNanos(80), deadline.remaining())
        now += 100
        assertEquals(Duration.ZERO, deadline.remaining())
        assertThrows(ArithmeticException::class.java) {
            BoundedCommand.run(root, listOf("true"), root.resolve("overflow.log"), Duration.ofSeconds(Long.MAX_VALUE))
        }
        assertFalse(Files.exists(root.resolve("overflow.log")))
    }

    @Test fun `logical selector is durable but is not inherited by nested commands`() {
        val root = directory()
        val log = root.resolve("selector.log")
        val sink = log.resolveSibling(log.fileName.toString() + ".timing.jsonl")
        assertEquals(0, BoundedCommand.run(root, listOf("python3", "-c",
            "import os; assert 'BC_TIMING_PROCESS_NAME' not in os.environ; print('unchanged')"),
            log, Duration.ofSeconds(3), mapOf("BC_TIMING_FILE" to sink.toString(),
                "BC_TIMING_INPUT_ID" to "a".repeat(64), "BC_TIMING_PROCESS_NAME" to "source:fixture:default")))
        assertEquals("unchanged\n", Files.readString(log))
        val records = Files.readAllLines(sink).map(mapper::readTree)
        assertTrue(records.all { it["process_name"].asText() == "source:fixture:default" })
        assertEquals(root.toString(), records.last()["cwd"].asText())
        assertEquals(64, records.last()["command_sha256"].asText().length)
    }

    @Test fun `successful and nonzero status and file output are unchanged`() {
        val root = directory()
        for (status in listOf(0, 7)) {
            val log = root.resolve("exit-$status.log")
            val sink = log.resolveSibling(log.fileName.toString() + ".timing.jsonl")
            assertEquals(status, BoundedCommand.run(root, listOf("python3", "-c", "print('output'); raise SystemExit($status)"),
                log, Duration.ofSeconds(3), mapOf("BC_TIMING_FILE" to sink.toString())))
            assertEquals("output\n", Files.readString(log))
            assertEquals(status, terminal(log)["exit_code"].asInt())
            assertEquals(if (status == 0) "SUCCEEDED" else "FAILED", terminal(log)["status"].asText())
            assertEquals("process", terminal(log)["scope"].asText())
            val report = mapper.readTree(Files.readString(Path.of(sink.toString() + ".report.json")))
            assertEquals("COMPLETE", report["status"].asText())
            assertEquals("NOT_EVALUATED", report["acceptance"].asText())
            assertEquals(1, report["counts"][if (status == 0) "success" else "failure"].asInt())
            assertFalse(report["groups"].flatMap { it["observations"].toList() }.any { it["status"].asText() == "RUNNING" })
        }
    }

    @Test fun `timeout retains original failure and records censored duration not an expectation`() {
        val root = directory()
        val log = root.resolve("timeout.log")
        val sink = log.resolveSibling(log.fileName.toString() + ".timing.jsonl")
        val failure = assertThrows(IllegalStateException::class.java) {
            BoundedCommand.run(root, listOf("sleep", "10"), log, Duration.ofMillis(50),
                mapOf("BC_TIMING_FILE" to sink.toString()))
        }
        assertTrue(failure.message!!.contains("command timed out"))
        val record = terminal(log)
        assertEquals("TIMED_OUT", record["status"].asText())
        assertEquals(50_000_000, record["configured_timeout_ns"].asLong())
        assertTrue(record["expected_runtime_ns"].isNull)
        assertTrue(record["duration_ns"].asLong() > 0)
    }

    @Test fun `cancellation record survives interruptible file locks and preserves interrupt flag`() {
        val root = directory()
        val log = root.resolve("cancel.log")
        val sink = log.resolveSibling(log.fileName.toString() + ".timing.jsonl")
        val failure = AtomicReference<Throwable>()
        val interrupted = AtomicBoolean()
        val worker = Thread {
            try {
                BoundedCommand.run(root, listOf("python3", "-c", "import time; print('ready',flush=True); time.sleep(10)"),
                    log, Duration.ofSeconds(20), mapOf("BC_TIMING_FILE" to sink.toString()))
            } catch (error: Throwable) { failure.set(error) }
            finally { interrupted.set(Thread.currentThread().isInterrupted) }
        }
        try {
            worker.start()
            val end = System.nanoTime() + Duration.ofSeconds(3).toNanos()
            while ((!Files.exists(log) || Files.size(log) == 0L) && System.nanoTime() < end) Thread.sleep(5)
            worker.interrupt()
            worker.join(10_000)
            assertFalse(worker.isAlive)
            assertTrue(failure.get() is InterruptedException)
            assertTrue(interrupted.get())
            assertEquals("CANCELLED", terminal(log)["status"].asText())
        } finally { worker.interrupt(); worker.join(10_000) }
    }

    @Test fun `default capture sibling retains events without spawning report generation`() {
        val root = directory()
        val log = root.resolve("default.log")
        val sink = log.resolveSibling(log.fileName.toString() + ".timing.jsonl")
        assertEquals(0, BoundedCommand.run(root, listOf("true"), log, Duration.ofSeconds(3),
            mapOf("BC_TIMING_FILE" to "")))
        assertEquals("SUCCEEDED", terminal(log)["status"].asText())
        assertFalse(Files.exists(Path.of(sink.toString() + ".report.json")))
    }

    @Test fun `missing automatic reporter fails success but preserves exact timeout and nonzero result`() {
        val root = directory()
        val previous = System.getProperty("bc.repo.root")
        try {
            System.setProperty("bc.repo.root", root.resolve("missing-pack").toString())
            val successSink = root.resolve("success.jsonl")
            val success = assertThrows(IllegalStateException::class.java) {
                BoundedCommand.run(root, listOf("true"), root.resolve("success.log"), Duration.ofSeconds(3),
                    mapOf("BC_TIMING_FILE" to successSink.toString()))
            }
            assertTrue(success.message!!.contains("automatic timing reporter is missing"))
            assertEquals(7, BoundedCommand.run(root, listOf("python3", "-c", "raise SystemExit(7)"),
                root.resolve("nonzero.log"), Duration.ofSeconds(3),
                mapOf("BC_TIMING_FILE" to root.resolve("nonzero.jsonl").toString())))
            val timeout = assertThrows(IllegalStateException::class.java) {
                BoundedCommand.run(root, listOf("sleep", "10"), root.resolve("timeout-original.log"), Duration.ofMillis(50),
                    mapOf("BC_TIMING_FILE" to root.resolve("timeout-original.jsonl").toString()))
            }
            assertTrue(timeout.message!!.startsWith("command timed out after "))
            assertTrue(timeout.suppressed.any { it.message?.contains("automatic timing reporter is missing") == true })
        } finally {
            if (previous == null) System.clearProperty("bc.repo.root") else System.setProperty("bc.repo.root", previous)
        }
    }

    @Test fun `terminal timing failure cannot create acceptance or replace nonzero status`() {
        val root = directory()
        for (status in listOf(0, 7)) {
            val log = root.resolve("failure-$status.log")
            val sink = root.resolve("timing-$status.jsonl")
            val program = "import pathlib,os; p=pathlib.Path(os.environ['BC_TIMING_FILE']); p.rename(str(p)+'.retained'); p.mkdir(); raise SystemExit($status)"
            val invoke = { BoundedCommand.run(root, listOf("python3", "-c", program), log, Duration.ofSeconds(3),
                mapOf("BC_TIMING_FILE" to sink.toString())) }
            if (status == 0) assertThrows(java.io.IOException::class.java) { invoke() }
            else assertEquals(7, invoke())
            assertTrue(Files.isRegularFile(Path.of(sink.toString() + ".retained")))
        }
    }
}
