package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DiagnosticReplayTest {
    @TempDir lateinit var root: Path

    @Test fun failedHopAndRemainingSoakTimeAreReadOnlyDiagnosticCheckpoints() {
        val evidence = root.resolve("generated/test-evidence/old/tele-soak")
        Files.createDirectories(evidence)
        val hash = "a".repeat(64)
        Files.writeString(evidence.resolve("run.json"), """{"run_id":"old","tier":"tele-soak","status":"failed","candidates":{"client_sha256":"$hash","server_sha256":"$hash"}}""")
        Files.writeString(evidence.resolve("events.jsonl"), listOf(
            """{"time":"2026-01-01T00:00:00Z","type":"prepared","requested_connected_seconds":120}""",
            """{"time":"2026-01-01T00:00:01Z","type":"connected_interval_started"}""",
            """{"time":"2026-01-01T00:00:50Z","type":"heartbeat"}""",
            """{"time":"2026-01-01T00:00:51Z","type":"dimension_roster","dimensions":["minecraft:overworld","minecraft:the_end"]}""",
            """{"time":"2026-01-01T00:00:52Z","type":"hop_started","dimension":"minecraft:the_end","circuit":2}""",
            """{"time":"2026-01-01T00:00:53Z","type":"session_finished","success":false}""",
        ).joinToString("\n", postfix = "\n"))
        val replay = loadDiagnosticReplay(root, SessionTier.TELE_SOAK, evidence.resolve("run.json"))
        assertEquals(HopCursor("minecraft:the_end", 2), replay.failedHop)
        assertEquals(49, replay.completedConnectedSeconds)
        assertEquals(120, replay.requestedSeconds)
        assertEquals(hash, replay.sourceCandidates["client_sha256"])
        Files.writeString(evidence.resolve("run.json"), Files.readString(evidence.resolve("run.json"))
            .replace("\"failed\"", "\"passed\""))
        assertThrows(IllegalArgumentException::class.java) {
            loadDiagnosticReplay(root, SessionTier.TELE_SOAK, evidence.resolve("run.json"))
        }
    }

    @Test fun diagnosticCompletionIsNeverAnAcceptanceReceipt() {
        val pair = CandidatePair(root, root.resolve("client.zip"), root.resolve("server.zip"),
            "a".repeat(64), "b".repeat(64))
        val evidence = SessionEvidence(root, "diagnostic-test", "tele-soak", pair, diagnostic = true)
        evidence.event("diagnostic_itinerary_complete", mapOf("circuits" to 3))
        evidence.finish(null)
        val receipt = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .readTree(evidence.directory.resolve("run.json").toFile())
        assertEquals("diagnostic", receipt.path("status").asText())
        assertFalse(receipt.path("acceptance_eligible").asBoolean(true))
        assertFalse(receipt.path("scenario_completed").asBoolean(false))
        assertEquals("tele-soak-diagnostic", evidence.directory.fileName.toString())
    }

    @Test fun diagnosticSmokeCannotBorrowCompletedHistoricalSoakTime() {
        val replay = DiagnosticReplay("old", Path.of("old/run.json"), emptyMap(), null, null, 120, 73)
        assertEquals(0, replay.remainingSeconds(SessionRequest(SessionTier.SMOKE)))
        assertThrows(IllegalArgumentException::class.java) { SessionRequest(SessionTier.OVERWORLD_TELE_SOAK, 60) }
    }
}
