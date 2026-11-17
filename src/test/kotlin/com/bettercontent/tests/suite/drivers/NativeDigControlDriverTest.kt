package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NativeDigControlDriverTest {
    private fun target() = suiteMapper.readTree("""{
      "player_uuid":"123e4567-e89b-42d3-a456-426614174000","dimension":"minecraft:overworld",
      "x":1,"y":64,"z":2,"face":"up","source_block":"minecraft:stone","result_block":"minecraft:air",
      "held_tool":"minecraft:iron_pickaxe","damage_before":4,"damage_after":5,
      "expected_pickups":{"minecraft:cobblestone":1},"timeout_ticks":200
    }""")
    private fun row(step: String, action: String, payload: String) =
        DriverObservation(step, action, "server", "completed", suiteMapper.readTree(payload))
    private fun receipts(pickup: String = "minecraft:cobblestone", protocol: String = "native_game_mode_START_PROGRESS_STOP") = listOf(
        row("before", "player.snapshot", """{"uuid":"123e4567-e89b-42d3-a456-426614174000","dimension":"minecraft:overworld",
          "held_item":"minecraft:iron_pickaxe","held_damage":4,"inventory":[]}"""),
        row("dig", "player.dig", """{"player_uuid":"123e4567-e89b-42d3-a456-426614174000",
          "dimension":"minecraft:overworld","x":1,"y":64,"z":2,"face":"up","start_tick":100,"stop_tick":110,
          "observed_tick":120,"native_destroy_progress_per_tick":0.2,"dig_protocol":"$protocol",
          "before_block":"minecraft:stone","after_block":"minecraft:air","destroyed":true,
          "held_item_before":"minecraft:iron_pickaxe","held_item_after":"minecraft:iron_pickaxe",
          "held_damage_before":4,"held_damage_after":5,"break_event_seen":true,"break_event_canceled":false,
          "pickup_events":[{"tick":115,"item":"$pickup","count":1}],
          "pickup_correlation":"same_actor_window_only_not_origin_proof"}"""),
        row("after", "player.snapshot", """{"uuid":"123e4567-e89b-42d3-a456-426614174000","dimension":"minecraft:overworld",
          "held_item":"minecraft:iron_pickaxe","held_damage":5,"inventory":[{"slot":1,"item":"$pickup","count":1}]}""")
    )
    @Test fun `native timed dig and pickup event remain control only`() {
        assertEquals("passed", (evaluateCase(NativeDigControlDriver, target(), receipts()) as DriverOutcome.Verdict).value.state)
        assertEquals("failed", (evaluateCase(NativeDigControlDriver, target(), receipts(pickup = "minecraft:dirt")) as DriverOutcome.Verdict).value.state)
        assertEquals("failed", (evaluateCase(NativeDigControlDriver, target(), receipts(protocol = "direct_destroyBlock")) as DriverOutcome.Verdict).value.state)
        assertFalse(DriverRegistry.supportedCases.contains("primitive.tools"))
    }
}
