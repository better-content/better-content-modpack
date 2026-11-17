package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceAttributedDigControlDriverTest {
    private val drop = "123e4567-e89b-42d3-a456-426614174001"
    private fun target() = suiteMapper.readTree("""{
      "player_uuid":"123e4567-e89b-42d3-a456-426614174000","dimension":"minecraft:overworld",
      "x":1,"y":64,"z":2,"face":"up","source_block":"minecraft:stone","result_block":"minecraft:air",
      "held_tool":"minecraft:iron_pickaxe","damage_before":4,"damage_after":5,
      "expected_pickups":{"minecraft:cobblestone":1},"timeout_ticks":200
    }""")
    private fun row(step: String, action: String, payload: String) =
        DriverObservation(step, action, "server", "completed", suiteMapper.readTree(payload))
    private fun receipts() = listOf(
        row("before", "player.snapshot", """{"uuid":"123e4567-e89b-42d3-a456-426614174000","dimension":"minecraft:overworld",
          "held_item":"minecraft:iron_pickaxe","held_damage":4,"inventory":[]}"""),
        row("dig", "player.dig", """{"player_uuid":"123e4567-e89b-42d3-a456-426614174000",
          "dimension":"minecraft:overworld","x":1,"y":64,"z":2,"face":"up","start_tick":100,"stop_tick":110,
          "observed_tick":120,"native_destroy_progress_per_tick":0.2,"dig_protocol":"native_game_mode_START_PROGRESS_STOP",
          "before_block":"minecraft:stone","after_block":"minecraft:air","destroyed":true,
          "held_item_before":"minecraft:iron_pickaxe","held_item_after":"minecraft:iron_pickaxe",
          "held_damage_before":4,"held_damage_after":5,"break_event_seen":true,"break_event_canceled":false,
          "pickup_events":[{"tick":115,"entity_uuid":"$drop","item":"minecraft:cobblestone","count":1,"scoped_block_origin_matched":true}],
          "drop_entities":[{"entity_uuid":"$drop","item":"minecraft:cobblestone","count":1,
             "origin_x":1,"origin_y":64,"origin_z":2,"spawn_tick":112,"source_method":"Block.popResource(Level,BlockPos,ItemStack)",
             "entity_join_accepted":true,"pickup_observed":true,"pickup_tick":115,"pickup_count":1}],
          "unscoped_item_join_observed":false,
          "origin_evidence":"exact_Block_popResource_pos_scope_entity_join_UUID_and_pickup_UUID",
          "pickup_correlation":"UUID_match_only; custom_bypass_paths_uncovered"}"""),
        row("after", "player.snapshot", """{"uuid":"123e4567-e89b-42d3-a456-426614174000","dimension":"minecraft:overworld",
          "held_item":"minecraft:iron_pickaxe","held_damage":5,"inventory":[{"slot":1,"item":"minecraft:cobblestone","count":1}]}""")
    )
    private fun altered(f: (ObjectNode) -> Unit): List<DriverObservation> = receipts().toMutableList().apply {
        val action = this[1]; val payload = action.payload.deepCopy<ObjectNode>(); f(payload)
        this[1] = action.copy(payload = payload)
    }
    @Test fun `pinned origin requires native source block and entity UUID chain`() {
        assertEquals("passed", (evaluateCase(SourceAttributedDigControlDriver, target(), receipts()) as DriverOutcome.Verdict).value.state)
        assertEquals("inconclusive", (evaluateCase(SourceAttributedDigControlDriver, target(), altered {
            (it.path("drop_entities")[0] as ObjectNode).put("origin_x", 8)
        }) as DriverOutcome.Verdict).value.state)
        assertEquals("inconclusive", (evaluateCase(SourceAttributedDigControlDriver, target(), altered {
            (it.path("pickup_events")[0] as ObjectNode).put("scoped_block_origin_matched", false)
        }) as DriverOutcome.Verdict).value.state)
        assertEquals("inconclusive", (evaluateCase(SourceAttributedDigControlDriver, target(), altered {
            it.put("unscoped_item_join_observed", true)
        }) as DriverOutcome.Verdict).value.state)
        assertFalse(DriverRegistry.supportedCases.contains("primitive.tools"))
    }
    @Test fun `same cell pickup without hook receipt cannot prove causal acquisition`() {
        assertTrue(evaluateCase(SourceAttributedDigControlDriver, target(), altered { it.remove("drop_entities") }) is DriverOutcome.Fatal)
        val unsupported = altered { it.put("origin_evidence", "same_actor_window_only_not_origin_proof") }
        assertEquals("inconclusive", (evaluateCase(SourceAttributedDigControlDriver, target(), unsupported) as DriverOutcome.Verdict).value.state)
        val bypass = altered {
            (it.path("drop_entities") as com.fasterxml.jackson.databind.node.ArrayNode).removeAll()
            it.put("origin_evidence", "no_scoped_native_Block_popResource_drop")
            it.put("unscoped_item_join_observed", true)
        }
        assertEquals("inconclusive", (evaluateCase(SourceAttributedDigControlDriver, target(), bypass) as DriverOutcome.Verdict).value.state)
    }
}
