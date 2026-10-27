package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class TeleSoakProbesTest {
    @TempDir lateinit var output: Path
    private val mapper = jacksonObjectMapper()

    @Test fun actualRegistryRosterNeedsMatchingCompleteSnapshot() {
        fun fixture(ids: List<String>, registered: List<String> = ids, complete: Boolean = true,
                    snapshot: String = "fresh", available: Boolean = true, matching: Boolean = true,
                    registeredCount: Int = registered.size, includeRegistry: Boolean = true) {
            output.resolve("snapshot.json").writeText(mapper.writeValueAsString(mapOf("snapshot_id" to "fresh")))
            val fields = mutableMapOf<String, Any>(
                "schema" to "bc.dimensions.v1", "snapshot_id" to snapshot, "complete" to complete,
                "error_count" to 0, "loaded_dimension_count" to ids.size, "loaded_dimensions" to ids)
            if (includeRegistry) fields += mapOf(
                "registered_dimension_registry" to "minecraft:dimension",
                "registered_dimensions" to registered,
                "registered_dimension_count" to registeredCount,
                "registered_dimension_registry_available" to available,
                "registered_dimension_count_matches_ids" to (registeredCount == registered.size && registered.size == registered.toSet().size),
                "registered_loaded_dimensions_match" to matching,
                "registered_not_loaded_dimensions" to (registered.toSet() - ids.toSet()).sorted(),
                "loaded_not_registered_dimensions" to (ids.toSet() - registered.toSet()).sorted())
            output.resolve("dimensions.json").writeText(mapper.writeValueAsString(fields))
        }
        val all = listOf("minecraft:overworld", "minecraft:the_end", "custom:gated")
        fixture(all)
        assertEquals(all, TeleSoakProbes.dimensions(output))
        fixture(listOf("minecraft:overworld"), registered = all, matching = false)
        assertThrows(IllegalArgumentException::class.java) { TeleSoakProbes.dimensions(output) }
        fixture(all, includeRegistry = false)
        assertThrows(IllegalArgumentException::class.java) { TeleSoakProbes.dimensions(output) }
        fixture(all, available = false)
        assertThrows(IllegalArgumentException::class.java) { TeleSoakProbes.dimensions(output) }
        fixture(all, registeredCount = 2)
        assertThrows(IllegalArgumentException::class.java) { TeleSoakProbes.dimensions(output) }
        fixture(all, matching = false)
        assertThrows(IllegalArgumentException::class.java) { TeleSoakProbes.dimensions(output) }
        fixture(all, registered = listOf("minecraft:overworld", "minecraft:the_nether", "custom:gated"))
        assertThrows(IllegalArgumentException::class.java) { TeleSoakProbes.dimensions(output) }
        fixture(listOf("minecraft:overworld"), complete = false)
        assertThrows(IllegalArgumentException::class.java) { TeleSoakProbes.dimensions(output) }
        fixture(listOf("minecraft:overworld"), snapshot = "stale")
        assertThrows(IllegalArgumentException::class.java) { TeleSoakProbes.dimensions(output) }
        fixture(listOf("minecraft:overworld", "minecraft:overworld"))
        assertThrows(IllegalArgumentException::class.java) { TeleSoakProbes.dimensions(output) }
        fixture(listOf("custom:gated"))
        assertThrows(IllegalArgumentException::class.java) { TeleSoakProbes.dimensions(output) }
    }

    @Test fun measuredOverallTpsNeverFallsBackToFictionalTwenty() {
        assertEquals(19.087, TeleSoakProbes.overallTps("[Server thread/INFO]: Overall: Mean tick time: 52.391 ms. Mean TPS: 19.087"))
        assertThrows(IllegalArgumentException::class.java) { TeleSoakProbes.overallTps("/forge tps launched but timed out") }
        assertThrows(IllegalArgumentException::class.java) { TeleSoakProbes.overallTps(
            "Overall: Mean tick time: 52.3 ms. Mean TPS: 19.1\nOverall: Mean tick time: 50.0 ms. Mean TPS: 20.0") }
        assertThrows(IllegalArgumentException::class.java) { TeleSoakProbes.overallTps("Overall: Mean tick time: 1.0 ms. Mean TPS: 200.0") }
    }
}
