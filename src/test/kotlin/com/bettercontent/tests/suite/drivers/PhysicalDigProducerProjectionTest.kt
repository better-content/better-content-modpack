package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Source-only: shared B production-classifier projections are NOT packet/OS/raw/runtime receipts. */
class PhysicalDigProducerProjectionTest {
    @Test fun `B production decision fixtures separate complete contradictions from incomplete callbacks`() {
        val path = Path.of("test-support/forge/src/test/resources/physical-dig-v3-contradictions.json")
        val fixture = suiteMapper.readTree(Files.readString(path))
        assertEquals("bc.playtest_passive_packet_dig_producer_projection_fixtures.v3", fixture.path("schema").asText())
        assertEquals("PassivePhysicalDig.classifyCapture(Capture,expectedVariant,expectedDrop,expectedHarvest)",
            fixture.path("source").asText())
        assertTrue(fixture.path("synthetic_unrun_not_runtime_evidence").asBoolean())
        val cases = fixture.path("cases")
        assertEquals(listOf("wrong_actual_harvest", "wrong_actual_packet_variant", "unexpected_complete_pickup",
            "incomplete_missing_callback_not_behavior"), cases.map { it.path("id").asText() })
        cases.forEach { row ->
            val verdict = physicalDigDecisionProjection(
                row.path("expected_packet_variant").asText(), row.path("expected_drop_outcome").asText(),
                row.path("expected_harvest_result").asBoolean(), row.path("decision"))
            assertEquals(if (row.path("id").asText() == "incomplete_missing_callback_not_behavior") "blocked" else "failed",
                verdict, row.path("id").asText())
            assertEquals(row.path("actual_packet_variant"), row.path("decision").path("actual_packet_variant"))
            assertEquals(row.path("actual_drop_outcome"), row.path("decision").path("actual_drop_outcome"))
            assertEquals(row.path("actual_stored_can_harvest"), row.path("decision").path("actual_stored_can_harvest"))
        }
    }
}
