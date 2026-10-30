package com.bettercontent.tests.release

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class TeleSoakClientEvidenceTest {
    private val mapper = jacksonObjectMapper()
    private val dimension = "custom:remote"
    private val valid = listOf(
        """{"time":"2026-01-01T00:00:00Z","type":"client_joined"}""",
        """{"time":"2026-01-01T00:00:10Z","type":"heartbeat","marker":"before","client_received":true,"server_alive":true,"verified_dimension":null}""",
        """{"time":"2026-01-01T00:00:11Z","type":"hop_started","dimension":"$dimension"}""",
        """{"time":"2026-01-01T00:00:42Z","type":"heartbeat","marker":"during","client_received":true,"server_alive":true,"verified_dimension":null}""",
        """{"time":"2026-01-01T00:00:50Z","type":"client_dimension_receipt","dimension":"$dimension","marker":"arrival","server_conditional":true,"fresh_client_log_offset":1}""",
        """{"time":"2026-01-01T00:01:14Z","type":"heartbeat","marker":"after","client_received":true,"server_alive":true,"verified_dimension":"$dimension"}""",
        """{"time":"2026-01-01T00:01:20Z","type":"tele_soak_complete"}""",
    )

    private fun check(lines: List<String>) = validateTeleClientEvidence(lines.map(mapper::readTree))

    @Test fun trueClientMarkersDuringArrivalAndDelayedForgeProbeAreNotRejected() {
        check(valid) // 32 seconds in-flight and 24 seconds after the actual arrival receipt.
    }

    @Test fun missingReceiptWrongDimensionLateProofAndForgedMarkersStillFailClosed() {
        assertThrows(IllegalArgumentException::class.java) { check(valid.filterNot { "client_dimension_receipt" in it }) }
        assertThrows(IllegalArgumentException::class.java) { check(valid.map { it.replace("\"verified_dimension\":\"$dimension\"", "\"verified_dimension\":null") }) }
        assertThrows(IllegalArgumentException::class.java) { check(valid.map { it.replace("00:01:14Z", "00:01:22Z").replace("00:01:20Z", "00:01:28Z") }) }
        assertThrows(IllegalArgumentException::class.java) { check(valid.map { it.replace("\"marker\":\"after\"", "\"marker\":\"arrival\"") }) }
        assertThrows(IllegalArgumentException::class.java) { check(valid.map { it.replace("\"server_conditional\":true", "\"server_conditional\":false") }) }
    }

    @Test fun fullRetainedEventEvidenceCanBeCheckedReadOnlyWhenProvided() {
        val supplied = System.getenv("BC_VALIDATE_TELE_EVENTS") ?: return
        val path = Path.of(supplied)
        val dimensions = Files.readAllLines(path).firstNotNullOfOrNull { line ->
            val event = mapper.readTree(line)
            if (event.path("type").asText() == "dimension_roster") event.path("dimensions").map { it.asText() } else null
        } ?: error("supplied Tele Soak evidence has no live roster")
        validateTeleSoakEvents(path, dimensions)
    }
}
