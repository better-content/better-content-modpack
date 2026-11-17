package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Synthetic receipt-shape only; never invokes getter, Minecraft, stage1 scope, or STOP/seal. */
class Stage1ExternalGetterNegativeControlDriverTest {
    private val checkpoint = "a".repeat(64)
    private fun params() = suiteMapper.createObjectNode().put("dimension", "minecraft:overworld")
        .put("x", 32).put("y", 64).put("z", 48)
        .put("source_checkpoint_sha256", "@controller-input-checkpoint-sha256-v1")
    private fun receipt(): ObjectNode {
        val prefix = suiteMapper.createObjectNode().put("schema", "bc.playtest_block_mutation_prefix.v2")
            .put("scope_id", "diagnostic-scope-1").put("scope_file_sha256", "b".repeat(64))
            .put("source_checkpoint_sha256", checkpoint).put("scope_failure", true)
            .put("failure_reason", "mutable_state_container_exposed").put("overflow", false).put("io_failure", false)
        prefix.set<ObjectNode>("lifecycle_context", suiteMapper.createObjectNode().put("purpose", "diagnostic")
            .put("epoch_id", "epoch-00001").put("native_session_id", "native-session-1"))
        val row = suiteMapper.createObjectNode().put("schema", "bc.playtest_mutation_external_getter_negative.v1")
            .put("scope_id", "diagnostic-scope-1").put("selector_id", "state_container_exposed")
            .put("selector_reviewed_by_native", false).put("epoch_id", "epoch-00001")
            .put("native_session_id", "native-session-1").put("source_checkpoint_sha256", checkpoint)
            .put("dimension", "minecraft:overworld").put("x", 32).put("y", 64).put("z", 48)
            .put("real_callsite", "LevelChunkSection.getStates() from native diagnostic action")
            .put("expected_scope_failure", "mutable_state_container_exposed")
            .put("natural_origin_proven_by_native", false)
            .put("negative_control_certifies_candidate_provider_by_native", false)
        row.set<ObjectNode>("resulting_prefix", prefix)
        return row
    }
    private fun observed(row: ObjectNode = receipt()) = listOf(DriverObservation(
        "one-actual-external-states-getter", "block_mutation.control_expose_states_v1", "server", "completed", row))
    @Test fun `single diagnostic getter plan is unregistered and cannot certify normal positive scope`() {
        val plan = Stage1ExternalGetterNegativeControlDriver.plan(params())
        assertEquals(1, plan.size)
        assertEquals("block_mutation.control_expose_states_v1", plan.single().action.id)
        assertEquals("@controller-mutation-scope-id-v1", plan.single().action.parameters.path("scope_id").asText())
        assertEquals("state_container_exposed", plan.single().action.parameters.path("selector_id").asText())
        assertEquals("@controller-input-checkpoint-sha256-v1", params().path("source_checkpoint_sha256").asText())
        assertFalse(plan.single().action.parameters.has("source_checkpoint_sha256"))
        assertTrue(Stage1ExternalGetterNegativeControlDriver.clientRequired)
        assertFalse(Stage1ExternalGetterNegativeControlDriver.caseId in DriverRegistry.supportedControls)
        assertFalse(Stage1ExternalGetterNegativeControlDriver.caseId in DriverRegistry.supportedCases)
        assertNull(DriverRegistry.resolve(Stage1ExternalGetterNegativeControlDriver.caseId))
        assertEquals(Stage1ExternalGetterNegativeControlDriver,
            DriverRegistry.diagnosticOnly(Stage1ExternalGetterNegativeControlDriver.caseId))
        assertNull(DriverRegistry.diagnosticOnly("control.mutation.other-negative"))
        assertEquals("passed", Stage1ExternalGetterNegativeControlDriver.evaluate(params(), observed()).state)
    }
    @Test fun `complete wrong failure contradicts negative control and incomplete scope blocks`() {
        val contradicted = receipt()
        (contradicted.path("resulting_prefix") as ObjectNode).put("failure_reason", "other_failure")
        assertEquals("failed", Stage1ExternalGetterNegativeControlDriver.evaluate(params(), observed(contradicted)).state)
        val incomplete = receipt()
        (incomplete.path("resulting_prefix") as ObjectNode).remove("overflow")
        assertThrows(IllegalArgumentException::class.java) {
            Stage1ExternalGetterNegativeControlDriver.evaluate(params(), observed(incomplete))
        }
        val foreign = params().put("selector_id", "alternate")
        assertThrows(IllegalArgumentException::class.java) {
            Stage1ExternalGetterNegativeControlDriver.plan(foreign)
        }
    }
}
