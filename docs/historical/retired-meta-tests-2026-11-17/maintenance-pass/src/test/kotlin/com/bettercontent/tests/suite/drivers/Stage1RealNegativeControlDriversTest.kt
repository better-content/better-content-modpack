package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Synthetic factual terminal SHAPES only. No original action, prelaunch scope, STOP, Store, GUI or world runs. */
class Stage1RealNegativeControlDriversTest {
    private val actor = "77777777-7777-4777-8777-777777777777"
    private val empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private fun params() = suiteMapper.createObjectNode().put("dimension", "minecraft:overworld")
        .put("x", 32).put("y", 64).put("z", 48)
    private fun state(id: String) = suiteMapper.createObjectNode().put("id", id)
        .set<ObjectNode>("properties", suiteMapper.createObjectNode())
    private fun context(driver: Stage1RealNegativeControlDriver, action: String) = suiteMapper.createObjectNode()
        .put("campaign_id", "synthetic-not-controller-authority")
        .put("lineage_id", "synthetic-not-controller-authority")
        .put("epoch_id", "epoch-00001").put("native_session_id", "epoch-00001-native")
        .put("branch_id", "synthetic-branch").put("case_id", driver.caseId)
        .put("trial_id", "synthetic-trial").put("source", "server")
        .put("action_ordinal", 1).put("request_sequence", 1).put("actor_uuid", actor)
        .put("capability_id", action).put("purpose", "diagnostic")
    private fun prefix(scopeFailure: Boolean = false, ioFailure: Boolean = false, reason: String? = null) =
        suiteMapper.createObjectNode().put("schema", "bc.playtest_block_mutation_prefix.v2")
            .put("scope_id", "scope-synthetic").put("scope_file_sha256", "a".repeat(64))
            .put("source_checkpoint_id", "setup-synthetic")
            .put("source_checkpoint_sha256", "b".repeat(64)).put("records", 0)
            .put("bytes", 0).put("sha256", empty).put("overflow", false)
            .put("scope_failure", scopeFailure).put("io_failure", ioFailure)
            .apply {
                set<ObjectNode>("lifecycle_context", suiteMapper.createObjectNode()
                    .put("purpose", "diagnostic").put("source", "controller_prelaunch"))
                if (reason != null) put("failure_reason", reason)
            }
    private fun receipt(driver: Stage1RealNegativeControlDriver): ObjectNode {
        val sections = driver === Stage1SectionsNegativeControlDriver
        val action = if (sections) "block_mutation.control_expose_sections_v1"
            else "block_mutation.control_io_append_failure_v1"
        val selector = if (sections) "section_array_exposed" else "cap_or_io_failure"
        val reason = if (sections) "mutable_section_array_exposed"
            else "mutation_append_failed:ClosedChannelException"
        val pre = prefix()
        return suiteMapper.createObjectNode()
            .put("schema", if (sections) "bc.playtest_mutation_sections_external_negative.v1"
                else "bc.playtest_mutation_io_append_negative.v2")
            .put("scope_id", "scope-synthetic").put("selector_id", selector)
            .put("selector_reviewed_by_native", false).put("actual_actor_uuid", actor)
            .put("context_certified_by_native", false)
            .put("natural_origin_proven_by_native", false)
            .put("negative_control_certifies_candidate_provider_by_native", false)
            .apply {
                set<ObjectNode>("position", params())
                set<ObjectNode>("controller_declared_witness_context", context(driver, action))
                set<ObjectNode>("block_mutation_pre_action_prefix", pre.deepCopy())
                set<ObjectNode>("actual_pre_action_mutation_prefix", pre)
                set<ObjectNode>("resulting_prefix", prefix(sections, !sections, reason))
                if (sections) {
                    put("actual_external_callsite", "LevelChunk.getSections() from native diagnostic action")
                    put("expected_scope_failure", reason)
                } else {
                    set<ObjectNode>("actual_before_live_cell_state", state("minecraft:stone"))
                    set<ObjectNode>("actual_after_live_cell_state", state("minecraft:air"))
                    set<ObjectNode>("public_method_call", suiteMapper.createObjectNode()
                        .put("method", "LevelChunk.setBlockState(BlockPos,BlockState,boolean)")
                        .apply {
                            set<ObjectNode>("requested_state", state("minecraft:air"))
                            set<ObjectNode>("actual_return_state", state("minecraft:stone"))
                        })
                    put("actual_failed_append_observed_path", "PalettedContainer.getAndSet_private_index_return")
                    put("cap_overflow_proven_by_native", false)
                }
            }
    }
    private fun observed(driver: Stage1RealNegativeControlDriver, row: ObjectNode = receipt(driver)) =
        listOf(DriverObservation("one-original-${if (driver === Stage1SectionsNegativeControlDriver)
            "section_array_exposed" else "cap_or_io_failure"}-diagnostic",
            if (driver === Stage1SectionsNegativeControlDriver)
                "block_mutation.control_expose_sections_v1" else "block_mutation.control_io_append_failure_v1",
            "server", "completed", row))
    @Test fun `two unregistered server-source one-action plans need joined client and exact controller markers`() {
        for ((driver, selector, action) in listOf(
            Triple(Stage1SectionsNegativeControlDriver, "section_array_exposed", "block_mutation.control_expose_sections_v1"),
            Triple(Stage1IoAppendNegativeControlDriver, "cap_or_io_failure", "block_mutation.control_io_append_failure_v1"))) {
            assertTrue(driver.clientRequired)
            assertFalse(driver.caseId in DriverRegistry.supportedControls)
            assertFalse(driver.caseId in DriverRegistry.supportedCases)
            assertNull(DriverRegistry.resolve(driver.caseId))
            assertEquals(driver, DriverRegistry.diagnosticOnly(driver.caseId))
            val step = driver.plan(params()).single()
            assertEquals(action, step.action.id)
            assertEquals("server", step.action.source)
            assertEquals(setOf("player_uuid", "scope_id", "selector_id", "dimension", "x", "y", "z",
                "witness_context"), step.action.parameters.fieldNames().asSequence().toSet())
            assertEquals(selector, step.action.parameters.path("selector_id").asText())
            assertEquals("@observed-player-uuid", step.action.parameters.path("player_uuid").asText())
            assertEquals("@controller-mutation-scope-id-v1", step.action.parameters.path("scope_id").asText())
            assertEquals("@controller-witness-context-v1", step.action.parameters.path("witness_context").asText())
            assertEquals("passed", driver.evaluate(params(), observed(driver)).state)
        }
    }
    @Test fun `complete wrong failure or fallback is contradiction but missing capture blocks`() {
        val wrong = receipt(Stage1SectionsNegativeControlDriver)
        (wrong.path("resulting_prefix") as ObjectNode).put("failure_reason", "another_complete_reason")
        assertEquals("failed", Stage1SectionsNegativeControlDriver.evaluate(params(),
            observed(Stage1SectionsNegativeControlDriver, wrong)).state)
        val fallback = receipt(Stage1IoAppendNegativeControlDriver)
            .put("actual_failed_append_observed_path", "LevelChunkSection.setBlockState_return")
        assertEquals("failed", Stage1IoAppendNegativeControlDriver.evaluate(params(),
            observed(Stage1IoAppendNegativeControlDriver, fallback)).state)
        val missing = receipt(Stage1IoAppendNegativeControlDriver).apply {
            remove("actual_failed_append_observed_path")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Stage1IoAppendNegativeControlDriver.evaluate(params(), observed(Stage1IoAppendNegativeControlDriver, missing))
        }
        val cap = receipt(Stage1IoAppendNegativeControlDriver).put("cap_overflow_proven_by_native", true)
        assertEquals("failed", Stage1IoAppendNegativeControlDriver.evaluate(params(),
            observed(Stage1IoAppendNegativeControlDriver, cap)).state)
        assertThrows(IllegalArgumentException::class.java) {
            Stage1SectionsNegativeControlDriver.plan(params().put("scope_id", "invented-scope"))
        }
    }
}
