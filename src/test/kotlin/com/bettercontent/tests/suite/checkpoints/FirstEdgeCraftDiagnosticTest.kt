package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Synthetic oracle negatives only; no native click, harvested block, or reopened save was exercised. */
internal class FirstEdgeCraftDiagnosticTest {
    private val hash = "a".repeat(64)
    private val actor = "00000000-0000-0000-0000-000000000001"
    private val context = WitnessContext("campaign-$actor", actor, "epoch-00001", "epoch-00001-session",
        "main", "control.first-edge", "trial", "server", 1L, 1L, actor, "player.dig.witness_v1", "diagnostic")
    private val log = NativeWireStack("minecraft:oak_log", 1, hash, "b".repeat(64),
        "vanilla_saved_ItemStack_NBT_including_ForgeCaps_not_external_inventory_adapter")
    private fun captured(): CapturedScopedPickup = CapturedScopedPickup("bc.playtest_scoped_pickup_wire_checked.v2",
        BoundFile("/fixture/acquisition.jsonl", hash), 400, 4, context,
        BoundTarget("bc.playtest_bound_target.v1", context, "oak", TargetSource.NATURAL_UNMODIFIED_BLOCK,
            "minecraft", "minecraft:oak_log", null, "minecraft:overworld", 0, 64, 0, hash, hash,
            CheckpointRef("origin", BoundFile("/fixture/origin.json", hash))),
        listOf(CapturedScopedSource(actor, hash, hash, log, listOf(log), listOf(hash))),
        emptyMap(), mapOf(0 to log))
    private fun stack(item: String, count: Int, canonical: String, content: String,
        slot: String? = null, index: Int = 0): ObjectNode = suiteMapper.createObjectNode().apply {
        put("item_id", item); put("count", count)
        put("canonical_algorithm", "bc.playtest.stack-nbt.v1"); put("canonical_sha256", canonical)
        put("content_algorithm", "bc.playtest.stack-content.v1"); put("content_sha256", content)
        put("provider_scope", "vanilla_saved_ItemStack_NBT_including_ForgeCaps_not_external_inventory_adapter")
        if (slot != null) put(slot, index)
    }
    private fun array(vararg values: JsonNode): ArrayNode = suiteMapper.createArrayNode().apply { values.forEach(::add) }
    private fun arm(): ObjectNode = suiteMapper.createObjectNode().apply {
        put("schema", "bc.playtest_passive_craft_arm.v1"); put("actor_uuid", actor)
        put("dimension", "minecraft:overworld"); put("observer_id", actor)
        put("arm_request_sequence", 2); put("arm_action_ordinal", 2)
        put("armed_before_input", true); put("source_ancestry_proven_by_native", false)
        set<JsonNode>("controller_declared_arm_context", suiteMapper.createObjectNode().apply {
            put("campaign_id", context.campaignId); put("lineage_id", context.lineageId)
            put("epoch_id", context.epochId); put("native_session_id", context.nativeSessionId)
            put("branch_id", context.branchId); put("case_id", context.caseId)
            put("trial_id", context.trialId); put("actor_uuid", actor)
            put("source", "server"); put("purpose", "diagnostic")
            put("capability_id", "player.observe_craft.arm_v1")
            put("action_ordinal", 2); put("request_sequence", 2)
        })
    }
    private fun receipt(arm: ObjectNode): ObjectNode = suiteMapper.createObjectNode().apply {
        put("schema", "bc.playtest_passive_craft_event.v1"); put("actor_uuid", actor)
        put("dimension", "minecraft:overworld"); put("observer_id", actor)
        put("arm_request_sequence", 2); put("arm_action_ordinal", 2)
        set<JsonNode>("controller_declared_arm_context", arm.path("controller_declared_arm_context"))
        put("context_certified_by_native", false)
        put("event_source", "PlayerEvent.ItemCraftedEvent/MONITOR"); put("event_count", 1)
        put("narrow_event_observed", true); put("physical_client_click_bound", false)
        put("source_ancestry_proven_by_native", false); put("saved_reopened_acquisition_proven", false)
        put("arm_tick", 100); put("event_tick", 101); put("applied_tick", 102)
        put("expected_output_id", "minecraft:oak_planks"); put("expected_output_count", 4)
        set<JsonNode>("inventory_before", array(stack("minecraft:oak_log", 1, hash, "b".repeat(64), "slot")))
        set<JsonNode>("inventory_after_tick", array())
        set<JsonNode>("grid_at_event", array(stack("minecraft:oak_log", 1, hash, "b".repeat(64), "grid_slot")))
        set<JsonNode>("output_at_event", stack("minecraft:oak_planks", 4, "c".repeat(64), "d".repeat(64)))
        set<JsonNode>("cursor_after_tick", stack("minecraft:oak_planks", 4, "c".repeat(64), "d".repeat(64)))
    }
    @Test fun matchingSyntheticCustodyIsStillNeverQualification() {
        val arm = arm(); val result = compareFirstEdgeCraft(captured(), arm, receipt(arm))
        assertTrue(result.consistent); assertFalse(result.qualificationEligible)
        assertTrue(result.reason.contains("physical click"))
    }
    @Test fun missingSourceOrChangedGridOrCursorOrInventoryRejects() {
        val arm = arm()
        val gridWrong = receipt(arm).apply { set<JsonNode>("grid_at_event",
            array(stack("minecraft:oak_log", 1, hash, "e".repeat(64), "grid_slot"))) }
        assertFalse(compareFirstEdgeCraft(captured(), arm, gridWrong).consistent)
        val cursorWrong = receipt(arm).apply { set<JsonNode>("cursor_after_tick",
            stack("minecraft:oak_planks", 4, "f".repeat(64), "d".repeat(64))) }
        assertFalse(compareFirstEdgeCraft(captured(), arm, cursorWrong).consistent)
        val duplicate = receipt(arm).apply { set<JsonNode>("inventory_after_tick",
            array(stack("minecraft:oak_log", 1, hash, "b".repeat(64), "slot"))) }
        assertFalse(compareFirstEdgeCraft(captured(), arm, duplicate).consistent)
        val missingPrior = receipt(arm).apply { set<JsonNode>("inventory_before", array()) }
        assertFalse(compareFirstEdgeCraft(captured(), arm, missingPrior).consistent)
    }
    @Test fun identicalPriorLogMustNotSubstituteForAcquiredSlot() {
        val source = captured().copy(initialVanillaSlots = mapOf(0 to log),
            finalVanillaSlots = mapOf(0 to log, 1 to log))
        val arm = arm()
        val receipt = receipt(arm).apply {
            set<JsonNode>("inventory_before", array(
                stack("minecraft:oak_log", 1, hash, "b".repeat(64), "slot", 0),
                stack("minecraft:oak_log", 1, hash, "b".repeat(64), "slot", 1)))
            set<JsonNode>("inventory_after_tick", array(
                stack("minecraft:oak_log", 1, hash, "b".repeat(64), "slot", 1)))
        }
        assertFalse(compareFirstEdgeCraft(source, arm, receipt).consistent)
        receipt.set<JsonNode>("inventory_after_tick", array(
            stack("minecraft:oak_log", 1, hash, "b".repeat(64), "slot", 0)))
        assertTrue(compareFirstEdgeCraft(source, arm, receipt).consistent)
    }
    @Test fun forgedFlagsAndActorOrTimingReject() {
        val arm = arm()
        val flags = receipt(arm).apply { put("physical_client_click_bound", true) }
        assertFalse(compareFirstEdgeCraft(captured(), arm, flags).consistent)
        val actorWrong = receipt(arm).apply { put("actor_uuid", "00000000-0000-0000-0000-000000000002") }
        assertFalse(compareFirstEdgeCraft(captured(), arm, actorWrong).consistent)
        val late = receipt(arm).apply { put("event_tick", 99) }
        assertFalse(compareFirstEdgeCraft(captured(), arm, late).consistent)
        val duplicate = receipt(arm).apply { put("event_count", 2) }
        assertFalse(compareFirstEdgeCraft(captured(), arm, duplicate).consistent)
        val otherDimension = arm().apply { put("dimension", "minecraft:the_nether") }
        assertFalse(compareFirstEdgeCraft(captured(), otherDimension, receipt(otherDimension).apply {
            put("dimension", "minecraft:the_nether") }).consistent)
        val wrongEpochArm = arm().apply { (path("controller_declared_arm_context") as ObjectNode)
            .put("epoch_id", "epoch-00002") }
        assertFalse(compareFirstEdgeCraft(captured(), wrongEpochArm, receipt(wrongEpochArm)).consistent)
    }
}
