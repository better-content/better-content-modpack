package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class ArtifactFinalizationTest {
    private fun launch(root: Path, failScenario: Boolean, failCleanup: Boolean, drift: Boolean): SummaryGeneratingListener {
        ArtifactFinalizationProbe.config = TestConfig(Path.of(System.getProperty("bc.repo.root")),
            "single-method-probe", root, 0, "Fixture", root, Path.of(System.getProperty("java.home"), "bin", "java"), null)
        ArtifactFinalizationProbe.failScenario = failScenario
        ArtifactFinalizationProbe.failCleanup = failCleanup
        ArtifactFinalizationProbe.drift = drift
        val listener = SummaryGeneratingListener()
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
            .selectors(selectMethod(ArtifactFinalizationProbe::class.java, "onlySelectedMethod")).build(), listener)
        assertEquals(1L, listener.summary.testsFoundCount)
        return listener
    }

    @Test fun singleSelectedMethodStillAuditsAfterTeardown(@TempDir root: Path) {
        val listener = launch(root, failScenario = false, failCleanup = false, drift = true)
        assertTrue(listener.summary.totalFailureCount > 0)
        val run = ArtifactFinalizationProbe.evidence.run
        val mapper = jacksonObjectMapper()
        assertEquals("failed", mapper.readTree(run.directory.resolve("run.json").toFile()).path("status").asText())
        val events = Files.readAllLines(run.directory.resolve("events.jsonl")).map(mapper::readTree)
        val cleanup = events.indexOfFirst { it.path("type").asText() == "process_cleanup" }
        val integrity = events.indexOfFirst { it.path("type").asText() == "final_artifact_integrity" }
        assertTrue(cleanup >= 0 && integrity > cleanup)
        assertEquals(1, events[integrity].path("count").asInt())
        assertTrue(events.any { it.path("type").asText() == "startup_performance_diagnostics" })
    }

    @Test fun scenarioCleanupIntegrityAndLogFailuresAllRemainVisible(@TempDir root: Path) {
        val listener = launch(root, failScenario = true, failCleanup = true, drift = true)
        assertTrue(listener.summary.totalFailureCount >= 2)
        val run = ArtifactFinalizationProbe.evidence.run
        val mapper = jacksonObjectMapper()
        val summary = mapper.readTree(run.directory.resolve("run.json").toFile())
        assertEquals("failed", summary.path("status").asText())
        assertTrue(summary.path("failure").asText().contains("original scenario failure"))
        val events = Files.readAllLines(run.directory.resolve("events.jsonl")).map(mapper::readTree)
        assertTrue(events.any { it.path("type").asText() == "process_cleanup" && !it.path("complete").asBoolean() })
        assertTrue(events.any { it.path("type").asText() == "final_artifact_integrity" && it.path("count").asInt() == 1 })
        assertTrue(events.any { it.path("type").asText() in setOf("runtime_log_findings", "target_log_findings") && it.path("count").asInt() > 0 })
        assertTrue(events.any { it.path("type").asText() == "startup_performance_diagnostics" })
        assertTrue(Files.isRegularFile(run.fixture.resolve("client.zip")))
        assertTrue(listener.summary.failures.any { it.exception.message?.contains("original scenario failure") == true })
    }

    @Test fun successfulSingleMethodPublishesPassOnlyAfterAllFinalAudits(@TempDir root: Path) {
        val listener = launch(root, failScenario = false, failCleanup = false, drift = false)
        assertEquals(0L, listener.summary.totalFailureCount)
        val run = ArtifactFinalizationProbe.evidence.run
        assertEquals("passed", jacksonObjectMapper().readTree(run.directory.resolve("run.json").toFile()).path("status").asText())
    }
}

/** Selected explicitly by the inert launcher above, never by the ordinary fast/runtime tags. */
@Tag("artifact-finalization-fixture")
class ArtifactFinalizationProbe {
    companion object {
        lateinit var config: TestConfig
        var failScenario = false
        var failCleanup = false
        var drift = false
        @JvmField @RegisterExtension val evidence = EvidenceExtension("candidate") { config }
        @JvmStatic @BeforeAll fun setup() {
            val client = evidence.run.fixture.resolve("client.zip").also { Files.writeString(it, "exact client") }
            val server = evidence.run.fixture.resolve("server.zip").also { Files.writeString(it, "exact server") }
            evidence.run.integrity.registerCandidate(CandidatePair(evidence.run.fixture, client, server, Hashes.sha256(client), Hashes.sha256(server)))
        }
        @JvmStatic @AfterAll fun teardown() {
            cleanupFixtures(listOf(AutoCloseable {
                if (drift) Files.writeString(evidence.run.fixture.resolve("client.zip"), "changed during teardown")
                if (failCleanup) throw IllegalStateException("original cleanup failure")
            })) { evidence.run.event("process_cleanup", it) }
        }
    }
    @Test fun onlySelectedMethod() = evidence.run.checkpoint("synthetic selected scenario") {
        if (failScenario) {
            Files.writeString(evidence.run.directory.resolve("synthetic.log"), "[ERROR] synthetic actual failure\n")
            throw IllegalArgumentException("original scenario failure")
        }
    }
    @Test fun orderedAuditWasNotSelected() { error("must not run: selection is one method, not the full class") }
}
