package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.checkpoints.SamplingIntent
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Synthetic oracle checks only; no native action, checkpoint, world, or test execution authorized here. */
class PrimitiveHarvestDriverTest {
    private val player = "123e4567-e89b-42d3-a456-426614174000"
    private val drop = "123e4567-e89b-42d3-a456-426614174001"
    private val initial = "a".repeat(64)
    private val worn = "b".repeat(64)
    private val cobble = "c".repeat(64)
    private val canonical = "d".repeat(64)
    private val wornCanonical = "e".repeat(64)
    private val cobbleCanonical = "f".repeat(64)
    private fun params() = suiteMapper.readTree("""{
      "player_uuid":"$player","dimension":"minecraft:overworld","timeout_ticks":200,
      "tool":{"family":"pickaxe","item":"minecraft:wooden_pickaxe","slot":2,"count":1,
        "source_stack_snbt_sha256":"$initial","source_stack_canonical_sha256":"$canonical",
        "source_stack_canonical_algorithm":"bc.playtest.stack-nbt.v1","acquisition_case_id":"source_tool"},
      "targets":[
        {"mode":"correct","x":1,"y":64,"z":2,"face":"up",
         "source_block_state":{"id":"minecraft:stone","properties":{}},
         "result_block_state":{"id":"minecraft:air","properties":{}},
         "damage_before":4,"damage_after":5,"expected_requires_correct_tool_for_drops":true,
         "expected_can_harvest_with_held_tool":true,"expected_pickups":{"minecraft:cobblestone":1}},
        {"mode":"wrong","x":17,"y":64,"z":2,"face":"up",
         "source_block_state":{"id":"minecraft:iron_ore","properties":{}},
         "result_block_state":{"id":"minecraft:air","properties":{}},
         "damage_before":4,"damage_after":5,"expected_requires_correct_tool_for_drops":true,
         "expected_can_harvest_with_held_tool":false,"expected_pickups":{},"forbidden_drop_item":"minecraft:raw_iron"}
      ]
    }""")
    private fun snapshot(step: String, damage: Int, hash: String, selected: Boolean, loot: Boolean): DriverObservation {
        val inventory = """[{"slot":2,"item":"minecraft:wooden_pickaxe","count":1,"stack_sha256":"$hash",
             "stack_canonical_sha256":"${if (hash == initial) canonical else wornCanonical}",
             "stack_canonical_algorithm":"bc.playtest.stack-nbt.v1"}""" +
            (if (loot) """,{"slot":3,"item":"minecraft:cobblestone","count":1,"stack_sha256":"$cobble",
             "stack_canonical_sha256":"$cobbleCanonical","stack_canonical_algorithm":"bc.playtest.stack-nbt.v1"}""" else "") + "]"
        return row(step, "player.snapshot", """{"uuid":"$player","dimension":"minecraft:overworld",
          "held_item":"${if (selected) "minecraft:wooden_pickaxe" else "minecraft:air"}",
          "held_damage":$damage,"selected_slot":${if (selected) 2 else 0},
          "held_stack_sha256":"${if (selected) hash else "empty"}",
          "held_stack_canonical_sha256":"${if (selected) (if (hash == initial) canonical else wornCanonical) else "empty"}",
          "held_stack_canonical_algorithm":"bc.playtest.stack-nbt.v1","inventory":$inventory}""")
    }
    private fun row(step: String, driver: String, json: String) =
        DriverObservation(step, driver, "server", "completed", suiteMapper.readTree(json))
    private fun observations(mode: String): List<DriverObservation> {
        val correct = mode == "correct"
        val x = if (correct) 1 else 17
        val source = if (correct) "minecraft:stone" else "minecraft:iron_ore"
        val drops = if (correct) """[{"entity_uuid":"$drop","item":"minecraft:cobblestone","count":1,
             "origin_x":$x,"origin_y":64,"origin_z":2,"spawn_tick":112,"source_method":"Block.popResource(Level,BlockPos,ItemStack)",
             "entity_join_accepted":true,"pickup_observed":true,"pickup_tick":115,"pickup_count":1}]""" else "[]"
        val pickups = if (correct) """[{"tick":115,"entity_uuid":"$drop","item":"minecraft:cobblestone",
             "count":1,"scoped_block_origin_matched":true}]""" else "[]"
        return listOf(
            row("block-before", "block.snapshot", """{"dimension":"minecraft:overworld","x":$x,"y":64,"z":2,
                "block_state":{"id":"$source","properties":{}}}"""),
            snapshot("inventory-before", 0, initial, false, false),
            row("select", "player.select_item", """{"player_uuid":"$player","source_slot":2,
              "selected_before":0,"selected_after":2,"item":"minecraft:wooden_pickaxe","count":1,
              "stack_sha256":"$initial","stack_canonical_sha256":"$canonical",
              "stack_canonical_algorithm":"bc.playtest.stack-nbt.v1","inventory_conserved":true,
              "inventory_multiset_before_sha256":"$initial","inventory_multiset_after_sha256":"$initial",
              "native_packet_handler":"ServerGamePacketListenerImpl.handleSetCarriedItem",
              "client_acknowledgment":"unverified_without_client_observer","acquisition_origin":"preexisting_unattributed",
              "fixture_mutations":[]}"""),
            snapshot("inventory-selected", 4, initial, true, false),
            row("dig", "player.dig", """{"player_uuid":"$player","dimension":"minecraft:overworld",
              "x":$x,"y":64,"z":2,"face":"up","start_tick":100,"stop_tick":110,"observed_tick":120,
              "native_destroy_progress_per_tick":0.2,"dig_protocol":"native_game_mode_START_PROGRESS_STOP",
              "before_block":"$source","after_block":"minecraft:air","destroyed":true,
              "held_item_before":"minecraft:wooden_pickaxe","held_item_after":"minecraft:wooden_pickaxe",
              "held_damage_before":4,"held_damage_after":5,"held_count_before":1,"held_count_after":1,
              "break_event_seen":true,"break_event_canceled":false,
              "requires_correct_tool_for_drops":true,"can_harvest_with_held_tool":$correct,"harvest_scope_complete":true,
              "harvest_check_count":1,"harvest_check_tick":110,
              "harvest_check_source":"ServerPlayerGameMode.destroyBlock/BlockState.canHarvestBlock(actual_return)",
              "pop_resource_scope_aborted":false,"fixture_mutations":[],"drop_entities":$drops,"pickup_events":$pickups,
              "unscoped_item_join_observed":false,
              "origin_evidence":"${if (correct) "exact_Block_popResource_pos_scope_entity_join_UUID_and_pickup_UUID" else "no_scoped_native_Block_popResource_drop"}",
              "pickup_correlation":"UUID_match_only; custom_bypass_paths_uncovered"}"""),
            row("block-after", "block.snapshot", """{"dimension":"minecraft:overworld","x":$x,"y":64,"z":2,
                "block_state":{"id":"minecraft:air","properties":{}}}"""),
            snapshot("inventory-after", 5, worn, true, correct)
        )
    }
    private fun altered(mode: String, index: Int, change: (ObjectNode) -> Unit): List<DriverObservation> =
        observations(mode).toMutableList().apply {
            val current = this[index]; this[index] = current.copy(payload = current.payload.deepCopy<ObjectNode>().apply(change))
        }
    @Test fun `strict two preclaimed restored trials never become the full primitive tools promise`() {
        val driver = PrimitiveHarvestDriver
        val p = params(); val requests = driver.expandTrials(p, "sealed")
        assertEquals(listOf("correct", "wrong"), requests.map { it.trialId })
        assertEquals(2, requests.map { it.targetId }.distinct().size)
        assertEquals(listOf("block.snapshot", "player.snapshot", "player.select_item", "player.snapshot",
            "player.dig", "block.snapshot", "player.snapshot"), driver.planTrial(requests.first()).map { it.action.id })
        assertEquals(listOf(SamplingIntent.Site("minecraft:overworld", 0, 0)), driver.samplingIntents(requests.first()))
        assertEquals(listOf(SamplingIntent.Site("minecraft:overworld", 1, 0)), driver.samplingIntents(requests.last()))
        assertTrue(driver.requireFreshTerrain)
        assertEquals(mapOf("minecraft:wooden_pickaxe" to "source_tool"), driver.requiredAcquisitionCases(p))
        assertTrue(requests.all { it.provisionedTargets.isEmpty() })
        assertFalse(DriverRegistry.supportedCases.contains("primitive.harvest"))
        assertFalse(DriverRegistry.supportedCases.contains("primitive.tools"))
        assertThrows(IllegalArgumentException::class.java) {
            driver.plan(p.deepCopy<ObjectNode>().apply { (path("targets")[0] as ObjectNode).put("unexpected", true) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            driver.plan(p.deepCopy<ObjectNode>().apply { (path("targets")[1] as ObjectNode).put("x", 2) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            driver.plan(p.deepCopy<ObjectNode>().apply {
                (path("targets")[1] as ObjectNode).put("expected_can_harvest_with_held_tool", true)
            })
        }
        assertThrows(IllegalArgumentException::class.java) {
            driver.plan(p.deepCopy<ObjectNode>().apply {
                (path("tool") as ObjectNode).put("source_stack_canonical_algorithm", "legacy-snbt")
            })
        }
    }
    @Test fun `synthetic positive and zero-drop negative require independent native predicate and causal chain`() {
        val p = params(); val requests = PrimitiveHarvestDriver.expandTrials(p, "sealed")
        assertEquals("inconclusive", PrimitiveHarvestDriver.evaluate(p,
            observations("correct") + observations("wrong")).state) // flat legacy path cannot prove restored epochs
        assertEquals("passed", PrimitiveHarvestDriver.evaluateTrial(requests[0], observations("correct")).state)
        assertEquals("passed", PrimitiveHarvestDriver.evaluateTrial(requests[1], observations("wrong")).state)
        val receipts = requests.mapIndexed { index, request -> TrialReceipt(request.trialId, request.targetId,
            "sealed", "epoch-$index", "passed", observations(request.trialId), true, true) }
        assertEquals("passed", PrimitiveHarvestDriver.aggregate(p, requests, receipts).state)
        assertEquals("failed", PrimitiveHarvestDriver.evaluateTrial(requests[1], altered("wrong", 4) {
            it.put("can_harvest_with_held_tool", true)
        }).state)
        assertEquals("inconclusive", PrimitiveHarvestDriver.evaluateTrial(requests[1], altered("wrong", 4) {
            it.put("harvest_scope_complete", false); it.remove("can_harvest_with_held_tool")
        }).state)
        assertEquals("inconclusive", PrimitiveHarvestDriver.evaluateTrial(requests[0], altered("correct", 4) {
            it.put("harvest_check_count", 2)
        }).state)
        assertEquals("inconclusive", PrimitiveHarvestDriver.evaluateTrial(requests[0], altered("correct", 4) {
            it.put("unscoped_item_join_observed", true)
        }).state)
        assertEquals("inconclusive", PrimitiveHarvestDriver.evaluateTrial(requests[1], altered("wrong", 4) {
            it.put("unscoped_item_join_observed", true)
        }).state)
    }
    @Test fun `tool hash and checkpoint restoration are not inferred from item id or a stored pass`() {
        val p = params(); val requests = PrimitiveHarvestDriver.expandTrials(p, "sealed")
        assertEquals("failed", PrimitiveHarvestDriver.evaluateTrial(requests[0], altered("correct", 1) {
            (it.path("inventory")[0] as ObjectNode).put("stack_sha256", "d".repeat(64))
        }).state)
        assertEquals("failed", PrimitiveHarvestDriver.evaluateTrial(requests[0], altered("correct", 6) {
            it.put("held_stack_sha256", "d".repeat(64))
        }).state)
        assertEquals("failed", PrimitiveHarvestDriver.evaluateTrial(requests[0], altered("correct", 2) {
            it.put("stack_canonical_sha256", "0".repeat(64))
        }).state)
        assertEquals("failed", PrimitiveHarvestDriver.evaluateTrial(requests[0], altered("correct", 0) {
            (it.path("block_state") as ObjectNode).put("id", "minecraft:iron_ore")
        }).state)
        val receipts = requests.mapIndexed { index, request -> TrialReceipt(request.trialId, request.targetId,
            "sealed", "epoch-$index", "passed", observations(request.trialId), true, index == 0) }
        assertThrows(IllegalArgumentException::class.java) { PrimitiveHarvestDriver.aggregate(p, requests, receipts) }
    }
}
