package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

@Tag("fast")
class ProcessLifecycleTest {
    private fun start(root: Path, script: String, events: MutableList<Map<String, Any?>> = mutableListOf()) =
        ManagedProcess("lifecycle", listOf("python3", "-u", "-c", script), root, root.resolve("probe.log"),
            lifecycleEvent = { events += it })

    private fun awaitExit(process: ManagedProcess) {
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (process.alive && System.nanoTime() < deadline) Thread.sleep(10)
        assertFalse(process.alive, "bounded inert process must exit")
    }

    @Test fun staleReadyDoesNotWaiveNonzeroExitAndRepeatedCloseRetainsFailure(@TempDir root: Path) {
        val events = mutableListOf<Map<String, Any?>>()
        val process = start(root, "print('ready'); raise SystemExit(23)", events)
        awaitExit(process)
        assertThrows(ProcessCorrectnessException::class.java) {
            process.waitForLog(Regex("ready"), Duration.ofSeconds(1), "stale readiness")
        }
        repeat(2) { assertThrows(ProcessCorrectnessException::class.java) { process.close() } }
        val observation = events.single { it["exit_status"] != null }
        assertEquals(23, observation["exit_status"])
        assertEquals(true, observation["unexpected"])
        assertEquals("running", observation["expectation"])
    }

    @Test fun unsolicitedZeroExitAlsoFailsAndCannotBeRetroactivelyDeclared(@TempDir root: Path) {
        val process = start(root, "print('ready')")
        awaitExit(process)
        assertThrows(ProcessCorrectnessException::class.java) { process.expectNaturalExit("too late") }
        assertThrows(ProcessCorrectnessException::class.java) { process.close() }
    }

    @Test fun expectedZeroExitHasExplicitBoundaryAndIsIdempotentlyClosed(@TempDir root: Path) {
        val process = start(root, "print('ready'); input()")
        process.waitForLog(Regex("ready"), Duration.ofSeconds(5), "ready")
        process.expectNaturalExit("typed native exit request")
        process.send("exit")
        process.waitForExit(Duration.ofSeconds(5), "expected zero exit")
        process.close(); process.close()
    }

    @Test fun expectedNaturalExitStillRejectsNonzeroStatus(@TempDir root: Path) {
        val process = start(root, "print('ready'); input(); raise SystemExit(19)")
        process.waitForLog(Regex("ready"), Duration.ofSeconds(5), "ready")
        process.expectNaturalExit("native stop")
        process.send("exit")
        assertThrows(ProcessCorrectnessException::class.java) {
            process.waitForExit(Duration.ofSeconds(5), "nonzero stop")
        }
        assertThrows(ProcessCorrectnessException::class.java) { process.close() }
    }

    @Test fun requestedTeardownAndForcedEscalationDoNotBecomeUnexpectedExits(@TempDir root: Path) {
        val events = mutableListOf<Map<String, Any?>>()
        val process = start(root, "import signal,time; signal.signal(signal.SIGTERM, signal.SIG_IGN); print('ready'); time.sleep(60)", events)
        process.waitForLog(Regex("ready"), Duration.ofSeconds(5), "signal handler")
        process.stop(Duration.ofMillis(100)); process.close()
        assertFalse(process.alive)
        assertEquals("teardown", events.first()["expectation"])
        assertNull(events.first()["exit_status"], "teardown is recorded before signalling")
        assertEquals(false, events.single { it["exit_status"] != null }["unexpected"])
    }

    @Test fun heartbeatBoundaryRejectsExitAfterAnEarlierPositiveObservation(@TempDir root: Path) {
        val process = start(root, "print('ready'); input(); raise SystemExit(31)")
        process.waitForLog(Regex("ready"), Duration.ofSeconds(5), "ready")
        process.assertRunning("before heartbeat")
        process.send("simulate client exit during server heartbeat")
        awaitExit(process)
        assertThrows(ProcessCorrectnessException::class.java) { process.assertRunning("after heartbeat") }
        assertThrows(ProcessCorrectnessException::class.java) { process.close() }
    }

    @Test fun unexpectedExitStillCleansObservedChildAndReportsActualCompleteness(@TempDir root: Path) {
        val process = start(root, "import subprocess,sys; c=subprocess.Popen([sys.executable,'-c','import time; time.sleep(60)']); print('child='+str(c.pid)); input(); raise SystemExit(17)")
        process.waitForLog(Regex("child=\\d+"), Duration.ofSeconds(5), "child")
        val child = Regex("child=(\\d+)").find(Files.readString(process.log))!!.groupValues[1].toLong()
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (child !in process.trackedPids && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(child in process.trackedPids)
        process.send("exit unexpectedly"); awaitExit(process)
        var cleanup: Map<String, Any?>? = null
        assertThrows(IllegalStateException::class.java) {
            cleanupFixtures(listOf(process)) { cleanup = it }
        }
        assertEquals(true, cleanup!!["complete"])
        assertEquals(emptyList<Long>(), cleanup!!["surviving_pids"])
        assertTrue((cleanup!!["correctness_errors"] as List<*>).isNotEmpty())
        assertFalse(ProcessHandle.of(child).orElse(null)?.isAlive == true)
    }

    @Test fun newLaunchDoesNotInheritOldExpectedExit(@TempDir root: Path) {
        val first = start(root, "print('ready'); input()")
        first.waitForLog(Regex("ready"), Duration.ofSeconds(5), "first ready")
        first.expectNaturalExit("first stop"); first.send("exit")
        first.waitForExit(Duration.ofSeconds(5), "first exit"); first.close()
        val second = start(root, "raise SystemExit(0)")
        awaitExit(second)
        assertThrows(ProcessCorrectnessException::class.java) { second.close() }
    }

    @Test fun junitAndEvidenceFailEvenWhenUnexpectedExitCleanupIsComplete() {
        val listener = SummaryGeneratingListener()
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
            .selectors(selectClass(UnexpectedExitEvidenceFixture::class.java)).build(), listener)
        assertTrue(listener.summary.totalFailureCount > 0)
        val run = UnexpectedExitEvidenceFixture.evidence.run
        val mapper = jacksonObjectMapper()
        assertEquals("failed", mapper.readTree(run.directory.resolve("run.json").toFile()).path("status").asText())
        val cleanup = Files.readAllLines(run.directory.resolve("events.jsonl")).map(mapper::readTree)
            .last { it.path("type").asText() == "process_cleanup" }
        assertTrue(cleanup.path("complete").asBoolean())
        assertFalse(cleanup.path("correctness_errors").isEmpty)
    }
}

/** Run only through the explicit nested launcher; not a pack runtime or ordinary fast test. */
@Tag("lifecycle-fixture")
class UnexpectedExitEvidenceFixture {
    companion object {
        @JvmField @RegisterExtension val evidence = EvidenceExtension("candidate")
        private lateinit var process: ManagedProcess
        @JvmStatic @BeforeAll fun setup() {
            val root = evidence.run.fixture
            val client = root.resolve("client.zip"); val server = root.resolve("server.zip")
            Files.writeString(client, "synthetic client"); Files.writeString(server, "synthetic server")
            evidence.run.integrity.registerCandidate(CandidatePair(root, client, server, Hashes.sha256(client), Hashes.sha256(server)))
            process = ManagedProcess("synthetic-client", listOf("python3", "-u", "-c", "print('ready'); input(); raise SystemExit(9)"),
                root, evidence.run.directory.resolve("synthetic.log"), lifecycleEvent = { evidence.run.event("process_lifecycle", it) })
            process.waitForLog(Regex("ready"), Duration.ofSeconds(5), "synthetic ready")
            process.send("unsolicited exit")
            val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
            while (process.alive && System.nanoTime() < deadline) Thread.sleep(10)
            check(!process.alive)
        }
        @JvmStatic @AfterAll fun cleanup() = cleanupFixtures(listOf(process)) { evidence.run.event("process_cleanup", it) }
    }
    @Test fun scenarioProducedItsPositiveMarker() { assertFalse(process.alive) }
}
