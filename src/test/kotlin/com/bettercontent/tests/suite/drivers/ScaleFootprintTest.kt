package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Source-only geometry/coverage regression; not an observed engine or performance result. */
class ScaleFootprintTest {
    private fun actor(samples: Int = 809): ObjectNode {
        val row = suiteMapper.createObjectNode()
        row.put("footprint_view_tuple_schema", "chunk_x,chunk_z,loaded_samples,block_ticking_samples," +
            "entity_ticking_samples,natural_spawning_samples,tick_eligible_samples,tick_events,spawn_proximity_expected_0_or_1")
        val tuples = row.putArray("footprint_view_chunks")
        var eligible = 0; var events = 0
        for (cx in -11..11) for (cz in -11..11) if (scaleViewGeometry(cx, cz, 0, 0)) {
            val inner = scaleSpawnProximity(cx, cz, 0, 0)
            tuples.addArray().add(cx).add(cz).add(samples).add(if (inner) samples else 0)
                .add(if (inner) samples else 0).add(if (inner) samples else 0)
                .add(if (inner) samples else 0).add(if (inner) samples * 20 else 0)
                .add(if (inner) 1 else 0)
            if (inner) { eligible++; events += samples * 20 }
        }
        row.put("footprint_geometric_chunks_count", tuples.size())
        row.put("footprint_tick_eligible_chunks", eligible)
        row.put("footprint_tick_observed_chunks", eligible)
        row.put("footprint_distinct_chunks_ticked", eligible)
        row.put("footprint_chunk_tick_events", events)
        row.put("footprint_loaded_chunks", tuples.size())
        row.put("footprint_block_ticking_chunks", eligible)
        listOf("missing_view_loaded_chunks", "missing_inner_entity_ticking_chunks",
            "missing_inner_block_ticking_chunks", "missing_inner_natural_spawning_chunks",
            "missing_inner_tick_events_chunks", "missing_eligible_tick_events_chunks").forEach { row.put(it, 0) }
        return row
    }
    private fun sampledMask(centerOnly: Boolean = false) = suiteMapper.createObjectNode().apply {
        val view = scaleExpectedView(0, 0); val length = (view.size + 7) / 8
        val data = ByteArray(2 * length)
        for ((i, chunk) in view.withIndex()) if (scaleSpawnProximity(chunk.first, chunk.second, 0, 0)) {
            data[i shr 3] = (data[i shr 3].toInt() or (1 shl (i and 7))).toByte()
            if (!centerOnly || chunk == (0 to 0))
                data[length + (i shr 3)] = (data[length + (i shr 3)].toInt() or (1 shl (i and 7))).toByte()
        }
        put("footprint_loaded_chunks", view.size)
        put("footprint_block_ticking_chunks", view.count { (cx, cz) -> scaleSpawnProximity(cx, cz, 0, 0) })
        put("footprint_interval_masks_base64", java.util.Base64.getEncoder().encodeToString(data))
    }
    @Test fun `view predicate includes axial offset eleven but not square corner`() {
        assertTrue(scaleViewGeometry(11, 0, 0, 0))
        assertFalse(scaleViewGeometry(12, 0, 0, 0))
        assertFalse(scaleViewGeometry(11, 11, 0, 0))
        assertTrue(scaleSpawnProximity(7, 0, 0, 0))
        assertFalse(scaleSpawnProximity(8, 0, 0, 0))
        assertTrue(scaleViewGeometry(-14, 2, -3, 2))
        assertFalse(scaleViewGeometry(-15, 2, -3, 2))
        assertTrue(scaleExpectedView(-49, 32).contains(-15 to 2))
        assertFalse(scaleExpectedView(-49, 32).contains(-16 to 2))
    }
    @Test fun `full eligible footprint and tuple position are independently required`() {
        val full = actor()
        assertTrue(scaleFootprintConforms(full, 0, 0, 809))
        assertTrue(scaleIntervalFootprintConforms(sampledMask(), 0, 0))
        assertFalse(scaleIntervalFootprintConforms(sampledMask(centerOnly = true), 0, 0))
        val underloaded = sampledMask().put("footprint_loaded_chunks", scaleExpectedView(0, 0).size - 1)
        assertFalse(scaleIntervalFootprintConforms(underloaded, 0, 0))
        val noncanonical = sampledMask()
        val encoded = noncanonical.path("footprint_interval_masks_base64").asText()
        noncanonical.put("footprint_interval_masks_base64", encoded + "=")
        assertFalse(scaleIntervalFootprintConforms(noncanonical, 0, 0))
        val spare = sampledMask()
        val bytes = java.util.Base64.getDecoder().decode(spare.path("footprint_interval_masks_base64").asText())
        val view = scaleExpectedView(0, 0)
        if (view.size % 8 != 0) {
            val lastReady = (view.size + 7) / 8 - 1
            bytes[lastReady] = (bytes[lastReady].toInt() or (1 shl (view.size % 8))).toByte()
            spare.put("footprint_interval_masks_base64", java.util.Base64.getEncoder().encodeToString(bytes))
            assertFalse(scaleIntervalFootprintConforms(spare, 0, 0))
        }
        val missing = actor()
        ((missing.path("footprint_view_chunks")) as ArrayNode).remove(missing.path("footprint_view_chunks").size() - 1)
        assertFalse(scaleFootprintConforms(missing, 0, 0, 809))
        val centerOnly = actor()
        val rows = centerOnly.path("footprint_view_chunks") as ArrayNode
        val otherInner = rows.first { tuple -> tuple[0].asInt() == 1 && tuple[1].asInt() == 0 } as ArrayNode
        otherInner.set(7, 0)
        assertFalse(scaleFootprintConforms(centerOnly, 0, 0, 809))
        val duplicate = actor()
        val changed = duplicate.path("footprint_view_chunks") as ArrayNode
        changed.set(changed.size() - 1, changed[changed.size() - 2].deepCopy<com.fasterxml.jackson.databind.JsonNode>())
        assertFalse(scaleFootprintConforms(duplicate, 0, 0, 809))
        assertEquals(0, (actor().path("footprint_view_chunks") as ArrayNode).count { it[0].asInt() == 11 && it[1].asInt() == 11 })
    }
}
