package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** B production classifier's shared synthetic projection, NOT a native callback or raw receipt. */
class PhysicalToolProducerProjectionTest {
    @Test fun `B same production tool classifier keeps complete contradictions distinct from capture gaps`() {
        val fixture = suiteMapper.readTree(Files.readString(Path.of(
            "test-support/forge/src/test/resources/physical-tool-v5-capture-shapes.json")))
        assertEquals("bc.playtest_physical_tool_capture_shapes.v5", fixture.path("schema").asText())
        assertEquals("PhysicalToolWitness.supportedReturn(callbacks,enters,exits,afterKind,sameOriginalObject)",
            fixture.path("producer").asText())
        assertTrue(fixture.path("synthetic_unrun_not_runtime_evidence").asBoolean())
        val expected = mapOf("intact_one_wear_no_callback" to "passed",
            "no_callback_same_original_now_empty_complete_behavior_contradiction" to "failed",
            "one_callback_empty_break" to "passed",
            "one_callback_nonempty_complete_behavior_contradiction" to "failed",
            "two_fully_bound_callbacks_complete_behavior_contradiction" to "failed",
            "empty_replaced_item_object_ambiguous" to "blocked",
            "callback_without_actual_consumer_cause" to "blocked",
            "consumer_cause_unbalanced" to "blocked",
            "callback_trace_cap_exceeded" to "blocked")
        val cases = fixture.path("cases")
        assertEquals(expected.keys, cases.map { it.path("id").asText() }.toSet())
        cases.forEach { row ->
            val id = row.path("id").asText()
            val reviewedCallbacks = if (id.startsWith("one_callback") || id.startsWith("two_fully") ||
                id.startsWith("callback_")) 1 else 0
            assertEquals(expected.getValue(id), physicalToolV5DecisionProjection(reviewedCallbacks,
                row.path("callbacks").asInt(), row.path("after_kind").asText(),
                row.path("capture_complete_shape").asBoolean()), id)
        }
    }
}
