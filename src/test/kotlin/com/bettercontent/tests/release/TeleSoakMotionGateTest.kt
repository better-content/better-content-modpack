package com.bettercontent.tests.release

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class TeleSoakMotionGateTest {
    @TempDir lateinit var root: Path

    @Test fun stationaryDimensionHopsCannotBePromotedAsRemoteChunkTeleSoak() {
        val events = root.resolve("stationary-events.jsonl")
        val time = "2026-01-01T00:00:00Z"
        val dimension = "minecraft:overworld"
        val lines = mutableListOf(
            """{"type":"dimension_roster","time":"$time","dimensions":["$dimension"],"registered_dimensions":["$dimension"],"excluded_dimensions":[]}""",
            """{"type":"client_joined","time":"$time"}""",
            """{"type":"heartbeat","time":"$time","marker":"live","client_received":true,"server_alive":true}""",
        )
        (0..3).forEach { circuit ->
            lines += """{"type":"hop_started","time":"$time","dimension":"$dimension","circuit":$circuit}"""
            // Supply genuine-looking distinct client receipts so this fixture
            // reaches the motion gate rather than failing earlier on the client gate.
            lines += """{"type":"client_dimension_receipt","time":"$time","dimension":"$dimension","marker":"arrival-$circuit","server_conditional":true,"fresh_client_log_offset":0}"""
            lines += """{"type":"heartbeat","time":"$time","marker":"heartbeat-$circuit","client_received":true,"server_alive":true,"verified_dimension":"$dimension"}"""
        }
        lines += """{"type":"tele_soak_complete","time":"$time"}"""
        Files.writeString(events, lines.joinToString("\n", postfix = "\n"))
        val failure = assertThrows(IllegalArgumentException::class.java) {
            validateTeleSoakEvents(events, listOf(dimension))
        }
        assertTrue(failure.message!!.contains("fresh dimension landings") && failure.message!!.contains("remote relocations"))
        val overworld = root.resolve("overworld-stationary-events.jsonl")
        Files.writeString(overworld, lines.filterNot { it.contains("tele_soak_complete") }.joinToString("\n", postfix = "\n") +
            """{"type":"overworld_tele_soak_complete","time":"$time","dimension_count":1,"dwell_seconds":3600,"fresh_relocations":10}""" + "\n")
        assertThrows(RuntimeException::class.java) { validateOverworldTeleSoakEvents(overworld) }
    }
}
