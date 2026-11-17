package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Synthetic shapes ONLY; no server action, joined actor, scope, STOP, raw, Store, tests/world execution. */
class Stage1PositiveControlDriversTest {
    private val actor = "77777777-7777-4777-8777-777777777777"
    private val empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private fun params() = suiteMapper.createObjectNode().put("dimension", "minecraft:overworld")
        .put("x", 32).put("y", 64).put("z", 48)
    private fun state(id: String) = suiteMapper.createObjectNode().put("id", id)
        .set<ObjectNode>("properties", suiteMapper.createObjectNode())
    private val drivers = listOf(
        Stage1SetupDelegatedControlDriver to "level_chunk_delegated",
        Stage1DelegatedPositiveControlDriver to "level_chunk_delegated",
        Stage1SectionPositiveControlDriver to "direct_section_setter",
        Stage1TransientPositiveControlDriver to "transient_restoration")
    private fun prefix(count: Int = 0): ObjectNode = suiteMapper.createObjectNode()
        .put("schema", "bc.playtest_block_mutation_prefix.v2").put("scope_id", "scope-synthetic")
        .put("scope_file_sha256", "a".repeat(64)).put("source_checkpoint_sha256", "b".repeat(64))
        .put("source_checkpoint_id", "synthetic-not-a-saved-parent")
        .put("records", count).put("bytes", if (count == 0) 0 else 120 * count)
        .put("sha256", if (count == 0) empty else "c".repeat(64)).put("scope_failure", false)
        .put("overflow", false).put("io_failure", false).apply {
            set<ObjectNode>("lifecycle_context", suiteMapper.createObjectNode()
                .put("purpose", "diagnostic").put("source", "controller_prelaunch"))
        }
    private fun call(method: String, requested: String, returned: String): ObjectNode =
        suiteMapper.createObjectNode().put("method", method).apply {
            set<ObjectNode>("requested_state", state(requested))
            set<ObjectNode>("actual_return_state", state(returned))
        }
    private fun receipt(driver: Stage1PositiveControlDriver, id: String): ObjectNode {
        val direct = id == "direct_section_setter"; val transient = id == "transient_restoration"
        val before = "minecraft:stone"; val after = "minecraft:air"
        val method = if (direct) "LevelChunkSection.setBlockState(int,int,int,BlockState,boolean)"
            else "LevelChunk.setBlockState(BlockPos,BlockState,boolean)"
        val calls = suiteMapper.createArrayNode().add(call(method, after, before))
        if (transient) calls.add(call(method, before, after))
        val pre = prefix()
        return suiteMapper.createObjectNode().put("schema", "bc.playtest_mutation_positive_control.v2")
            .put("scope_id", "scope-synthetic").put("control_id", id)
            .put("actual_actor_uuid", actor).put("public_method_call_count", calls.size())
            .put("level_chunk_setter_calls", if (direct) 0 else calls.size())
            .put("direct_section_setter_calls", if (direct) 1 else 0)
            .put("context_certified_by_native", false).put("observation_complete", true)
            .put("natural_origin_proven_by_native", false).put("candidate_provider_certified_by_native", false)
            .apply {
                set<ObjectNode>("position", params())
                set<ObjectNode>("actual_before_live_cell_state", state(before))
                set<ObjectNode>("actual_after_live_cell_state", state(if (transient) before else after))
                set<JsonNode>("public_method_calls", calls)
                set<ObjectNode>("block_mutation_pre_action_prefix", pre.deepCopy())
                set<ObjectNode>("actual_pre_action_mutation_prefix", pre)
                set<ObjectNode>("actual_post_action_mutation_prefix", prefix(calls.size()))
                set<ObjectNode>("controller_declared_witness_context", suiteMapper.createObjectNode()
                    .put("case_id", driver.caseId).put("actor_uuid", actor)
                    .put("capability_id", "block_mutation.control_positive_v1")
                    .put("source", "server").put("purpose", "diagnostic"))
                if (direct) set<ObjectNode>("diagnostic_chunk_save_metadata", suiteMapper.createObjectNode()
                    .put("method", "ChunkAccess.setUnsaved(boolean)")
                    .put("owner_chunk_x", 2).put("owner_chunk_z", 3)
                    .put("unsaved_before", false).put("requested_unsaved", true)
                    .put("unsaved_after", true).put("diagnostic_only", true)
                    .put("save_to_disk_proven_by_native", false))
                else putNull("diagnostic_chunk_save_metadata")
            }
    }
    private fun observed(driver: Stage1PositiveControlDriver, id: String, row: ObjectNode = receipt(driver, id)) =
        listOf(DriverObservation("one-original-$id-diagnostic", "block_mutation.control_positive_v1",
            "server", "completed", row))

    @Test fun `four exact diagnostic-only one-action routes do not enter normal registry`() {
        for ((driver, id) in drivers) {
            assertTrue(driver.clientRequired)
            assertFalse(driver.caseId in DriverRegistry.supportedControls)
            assertFalse(driver.caseId in DriverRegistry.supportedCases)
            assertNull(DriverRegistry.resolve(driver.caseId))
            assertEquals(driver, DriverRegistry.diagnosticOnly(driver.caseId))
            val action = driver.plan(params()).single().action
            assertEquals("block_mutation.control_positive_v1", action.id)
            assertEquals("server", action.source)
            assertEquals(setOf("player_uuid", "control_id", "scope_id", "dimension", "x", "y", "z",
                "witness_context"), action.parameters.fieldNames().asSequence().toSet())
            assertEquals(id, action.parameters.path("control_id").asText())
            assertEquals("@observed-player-uuid", action.parameters.path("player_uuid").asText())
            assertEquals("@controller-mutation-scope-id-v1", action.parameters.path("scope_id").asText())
            assertEquals("@controller-witness-context-v1", action.parameters.path("witness_context").asText())
            assertEquals("passed", driver.evaluate(params(), observed(driver, id)).state)
        }
        assertNull(DriverRegistry.diagnosticOnly("control.mutation.unreviewed-positive"))
        assertThrows(IllegalArgumentException::class.java) {
            Stage1SetupDelegatedControlDriver.plan(params().put("parent_checkpoint", "invented"))
        }
    }
    @Test fun `complete wrong method return or section metadata is contradiction while missing capture blocks`() {
        val driver = Stage1SectionPositiveControlDriver
        val wrong = receipt(driver, "direct_section_setter")
        val first = wrong.path("public_method_calls")[0] as ObjectNode
        first.set<ObjectNode>("actual_return_state", state("minecraft:air"))
        assertEquals("failed", driver.evaluate(params(), observed(driver, "direct_section_setter", wrong)).state)
        val dirty = receipt(driver, "direct_section_setter")
        (dirty.path("diagnostic_chunk_save_metadata") as ObjectNode).put("owner_chunk_x", 3)
        assertEquals("failed", driver.evaluate(params(), observed(driver, "direct_section_setter", dirty)).state)
        val incomplete = receipt(driver, "direct_section_setter").put("observation_complete", false)
            .put("unsupported_reason", "scope_or_observer_event_count_incomplete")
        assertThrows(IllegalArgumentException::class.java) {
            driver.evaluate(params(), observed(driver, "direct_section_setter", incomplete))
        }
        val missing = receipt(driver, "direct_section_setter").apply { remove("actual_post_action_mutation_prefix") }
        assertThrows(IllegalArgumentException::class.java) {
            driver.evaluate(params(), observed(driver, "direct_section_setter", missing))
        }
    }
}
