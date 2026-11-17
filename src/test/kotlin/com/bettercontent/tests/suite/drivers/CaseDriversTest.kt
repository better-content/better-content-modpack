package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.SuiteCase
import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CaseDriversTest {
    private fun target() = suiteMapper.readTree("""{
      "player_uuid":"123e4567-e89b-42d3-a456-426614174000", "dimension":"minecraft:overworld",
      "x":1,"y":64,"z":2,"face":"up","hand":"main_hand",
      "block_id":"test:placed_meal","bite_property":"bites",
      "before_bites":2,"after_bites":3,"before_food":8,"after_food":12
    }""")
    private fun observed(step: String, driver: String, payload: String) =
        DriverObservation(step, driver, "server", "completed", suiteMapper.readTree(payload))
    private fun evidence(beforeFood: Int = 8, afterFood: Int = 12, afterBites: String = "3") = listOf(
        observed("before", "player.snapshot", """{"uuid":"123e4567-e89b-42d3-a456-426614174000","dimension":"minecraft:overworld","food_level":$beforeFood}"""),
        observed("bite", "player.interact", """{
          "uuid":"123e4567-e89b-42d3-a456-426614174000", "interaction_result":"CONSUME",
          "food_before":8,"food_after":12,"x":1,"y":64,"z":2,"face":"up","hand":"main_hand",
          "before_block_state":{"id":"test:placed_meal","properties":{"bites":"2"}},
          "after_block_state":{"id":"test:placed_meal","properties":{"bites":"$afterBites"}}
        }"""),
        observed("after", "player.snapshot", """{"uuid":"123e4567-e89b-42d3-a456-426614174000","dimension":"minecraft:overworld","food_level":$afterFood}""")
    )
    @Test fun `registry distinguishes bounded controls from full independent case assertions`() {
        assertEquals(setOf("control.food.placed-bite", "control.food.item-consumption", "control.primitive.tool-break",
            "control.primitive.native-dig", "control.primitive.source-attributed-dig",
            "control.world.temperate-sample", "control.world.login-landing",
            "control.water.manual-sip", "control.water.physical-manual-sip", "control.water.paid-sip-trial",
            "control.lifecycle.death-respawn-events", "control.travel.walk-route",
            "control.client.inventory-screen", "control.client.emi-navigation"), DriverRegistry.supportedControls)
        assertEquals(setOf("survival.food", "world.initial-spawn", "world.onboarding", "world.temperate", "combat.outgoing",
            "scale.8", "scale.16", "scale.32", "client.emi.public-index", "client.inventory.sync"), DriverRegistry.supportedCases)
        // The retained closeout documents14 ordinary controls; seven mutation diagnostics
        // remain opt-in only and must not leak into ordinary resolution or full scenarios.
        for (id in setOf("control.mutation.setup-delegated", "control.mutation.delegated-positive",
            "control.mutation.section-positive", "control.mutation.transient-positive",
            "control.mutation.external-getter-negative", "control.mutation.sections-negative",
            "control.mutation.io-append-negative")) {
            assertNotNull(DriverRegistry.diagnosticOnly(id))
            assertNull(DriverRegistry.resolve(id))
            assertFalse(id in DriverRegistry.supportedControls || id in DriverRegistry.supportedCases)
        }
        assertNull(DriverRegistry.resolve("client.emi")) // narrow index proof does not implement usage/navigation
        assertNull(DriverRegistry.resolve("water.purity"))
        assertNull(DriverRegistry.resolve("water.manual")) // source NBT acquisition provenance has no native proof yet
        assertThrows(IllegalArgumentException::class.java) { DriverRegistry.requireSupported(listOf("control.food.placed-bite", "survival.spoilage")) }
    }
    @Test fun `terminal source is bound to each planned server or client action`() {
        val params = suiteMapper.createObjectNode()
        val client = listOf(DriverStep("client-read", DriverAction("client.read", params, source = "client")))
        assertNull(validateTerminalSequence(client, listOf(observed("client-read", "client.read", "{}")
            .copy(source = "client"))))
        assertNotNull(validateTerminalSequence(client, listOf(observed("client-read", "client.read", "{}"))))
        assertThrows(IllegalArgumentException::class.java) { DriverAction("bad", params, source = "both") }
    }
    @Test fun `plan requires typed exact target and independent pre action post`() {
        val steps = PlacedFoodBiteDriver.plan(target())
        assertEquals(listOf("player.snapshot", "player.interact", "player.snapshot"), steps.map { it.action.id })
        assertEquals("minecraft:overworld", steps[1].action.parameters.path("target").path("dimension").asText())
        val extra = target().deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put("fake_success", true)
        assertThrows(IllegalArgumentException::class.java) { PlacedFoodBiteDriver.plan(extra) }
        val bad = target().deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put("before_food", 20)
        assertThrows(IllegalArgumentException::class.java) { PlacedFoodBiteDriver.plan(bad) }
        val same = target().deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put("after_bites", 2)
        assertThrows(IllegalArgumentException::class.java) { PlacedFoodBiteDriver.plan(same) }
    }
    @Test fun `native completed alone cannot satisfy food or block change oracle`() {
        val driver = PlacedFoodBiteDriver
        assertEquals("passed", (evaluateCase(driver, target(), evidence()) as DriverOutcome.Verdict).value.state)
        assertEquals("failed", (evaluateCase(driver, target(), evidence(afterFood = 8)) as DriverOutcome.Verdict).value.state)
        assertEquals("failed", (evaluateCase(driver, target(), evidence(afterBites = "2")) as DriverOutcome.Verdict).value.state)
        assertIsFatal(evaluateCase(driver, target(), evidence().dropLast(1)))
        assertIsFatal(evaluateCase(driver, target(), evidence().mapIndexed { i, row -> if (i == 1) row.copy(kind = "failed") else row }))
        assertIsFatal(evaluateCase(driver, target(), evidence().mapIndexed { i, row -> if (i == 1) row.copy(source = "client") else row }))
    }
    @Test fun `primitive tool requires actual block change and durability debit`() {
        val params = suiteMapper.readTree("""{
          "player_uuid":"123e4567-e89b-42d3-a456-426614174000", "dimension":"minecraft:overworld",
          "x":1,"y":64,"z":2,"block_id":"minecraft:stone","after_block_id":"minecraft:air",
          "held_tool_id":"minecraft:iron_pickaxe","damage_before":4,"damage_after":5
        }""")
        val before = observed("before", "player.snapshot", """{"uuid":"123e4567-e89b-42d3-a456-426614174000","dimension":"minecraft:overworld","held_item":"minecraft:iron_pickaxe","held_damage":4}""")
        fun action(damage: Int) = observed("break", "player.break_block", """{
          "uuid":"123e4567-e89b-42d3-a456-426614174000","dimension":"minecraft:overworld", "destroyed":true,
          "before_block_state":{"id":"minecraft:stone"},"after_block_state":{"id":"minecraft:air"},
          "held_item_before":"minecraft:iron_pickaxe","held_item_after":"minecraft:iron_pickaxe",
          "held_damage_before":4,"held_damage_after":$damage,"x":1,"y":64,"z":2
        }""")
        val after = observed("after", "player.snapshot", """{"uuid":"123e4567-e89b-42d3-a456-426614174000","dimension":"minecraft:overworld","held_item":"minecraft:iron_pickaxe","held_damage":5}""")
        assertEquals("passed", (evaluateCase(PrimitiveBreakDriver, params, listOf(before, action(5), after)) as DriverOutcome.Verdict).value.state)
        assertEquals("failed", (evaluateCase(PrimitiveBreakDriver, params, listOf(before, action(4), after)) as DriverOutcome.Verdict).value.state)
        assertIsFatal(evaluateCase(PrimitiveBreakDriver, params, listOf(before, action(5).copy(kind = "failed"), after)))
    }
    @Test fun `item use requires actual duration completion and paid item hunger transition`() {
        val params = suiteMapper.readTree("""{
          "player_uuid":"123e4567-e89b-42d3-a456-426614174000", "dimension":"minecraft:overworld",
          "hand":"main_hand","item_before":"minecraft:apple","item_after":"minecraft:apple",
          "count_before":2,"count_after":1,"food_before":8,"food_after":12
        }""")
        val before = observed("before", "player.snapshot", """{"uuid":"123e4567-e89b-42d3-a456-426614174000","dimension":"minecraft:overworld","held_item":"minecraft:apple","food_level":8}""")
        fun action(completed: Boolean) = observed("consume", "player.use_item", """{
          "uuid":"123e4567-e89b-42d3-a456-426614174000","dimension":"minecraft:overworld","hand":"main_hand",
          "consumption_completed":$completed,"item_before":"minecraft:apple","item_after":"minecraft:apple",
          "count_before":2,"count_after":1,"food_before":8,"food_after":12
        }""")
        val after = observed("after", "player.snapshot", """{"uuid":"123e4567-e89b-42d3-a456-426614174000","dimension":"minecraft:overworld","held_item":"minecraft:apple","food_level":12}""")
        assertEquals("passed", (evaluateCase(ItemConsumptionDriver, params, listOf(before, action(true), after)) as DriverOutcome.Verdict).value.state)
        assertEquals("failed", (evaluateCase(ItemConsumptionDriver, params, listOf(before, action(false), after)) as DriverOutcome.Verdict).value.state)
        assertIsFatal(evaluateCase(ItemConsumptionDriver, params, listOf(before, action(true).copy(kind = "failed"), after)))
    }
    @Test fun `composition requires closed acquired roster and never promotes partial controls`() {
        val c = suiteMapper.readValue("""{
          "id":"survival.food","note_ids":["food-sample"],"owner":"better-content-modpack","layer":"campaign",
          "context":"server","assertions":["survival.food.promise"],"targets":["test:placed_meal"],
          "dependencies":[],"stage":1,"timeout_seconds":60,"preconditions":["acquired"],"actions":["eat"],
          "expected":["nutrition and effects"],"cleanup":["owned fixtures"],"evidence_kinds":["native"],
          "existing_tests":[],"implementation":{"state":"missing","path":"","symbol":""}
        }""", SuiteCase::class.java)
        val row = ControlTarget("test:placed_meal", "control.food.placed-bite", target())
        assertEquals(3, DriverComposition.plan(c, listOf(row)).single().second.size)
        assertEquals("blocked", DriverComposition.fullCaseVerdict(c, listOf(ControlRun(row, evidence()))).state)
        assertThrows(IllegalArgumentException::class.java) { DriverComposition.plan(c, listOf(row, row)) }
        assertThrows(IllegalArgumentException::class.java) {
            DriverComposition.plan(c, listOf(row.copy(acquisitionId = "test:invented")))
        }
    }
    @Test fun `world sample is bounded loaded-only control and climate mismatch fails`() {
        val params = suiteMapper.readTree("""{
          "dimension":"minecraft:overworld","x":1,"y":64,"z":2,"radius":0,
          "expected_biome":"minecraft:plains","expected_block":"minecraft:grass_block",
          "temperature_min":0.7,"temperature_max":0.9,"downfall_min":0.3,"downfall_max":0.5,"precipitation":"RAIN"
        }""")
        val sample = observed("sample", "world.sample", """{
          "dimension":"minecraft:overworld","x":1,"y":64,"z":2,"center_biome":"minecraft:plains",
          "base_temperature":0.8,"downfall":0.4,"precipitation":"RAIN","unloaded_positions":0,
          "loaded_samples":[{"x":1,"y":63,"z":2,"block_id":"minecraft:dirt"},
                            {"x":1,"y":64,"z":2,"block_id":"minecraft:grass_block"},
                            {"x":1,"y":65,"z":2,"block_id":"minecraft:air"}]
        }""")
        assertEquals("passed", (evaluateCase(WorldTemperateProbeDriver, params, listOf(sample)) as DriverOutcome.Verdict).value.state)
        val wrong = params.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put("temperature_min", 0.9)
        assertEquals("failed", (evaluateCase(WorldTemperateProbeDriver, wrong, listOf(sample)) as DriverOutcome.Verdict).value.state)
        val extra = params.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put("natural_generated", true)
        assertIsFatal(evaluateCase(WorldTemperateProbeDriver, extra, listOf(sample)))
    }
    @Test fun `water sip reads real Thirst not vanilla hunger and rejects unpaid volume`() {
        val params = suiteMapper.readTree("""{
          "player_uuid":"123e4567-e89b-42d3-a456-426614174000","hand":"main_hand",
          "source_item":"minecraft:potion","result_item":"minecraft:glass_bottle","source_count":1,"result_count":1,
          "source_purity":3,"result_purity":0,"thirst_before":10,"thirst_after":14,
          "quenched_before":10,"quenched_after":14,"fluids_before":[],"fluids_after":[]
        }""")
        fun water(step: String, thirst: Int, item: String, purity: Int) = observed(step, "player.water_snapshot", """{
          "player_uuid":"123e4567-e89b-42d3-a456-426614174000","hand":"main_hand","thirst":$thirst,
          "quenched":$thirst,"held_item":"$item","held_count":1,"item_purity":$purity,"fluids":[],
          "capability_source":"dev.ghen.thirst.foundation.common.capability.ModCapabilities.PLAYER_THIRST",
          "purity_source":"dev.ghen.thirst.content.purity.WaterPurity.getPurity"
        }""")
        val action = observed("sip", "player.use_item", """{
          "uuid":"123e4567-e89b-42d3-a456-426614174000","hand":"main_hand","consumption_completed":true,
          "item_before":"minecraft:potion","count_before":1,"item_after":"minecraft:glass_bottle","count_after":1
        }""")
        val rows = listOf(water("before", 10, "minecraft:potion", 3), action,
            water("after", 14, "minecraft:glass_bottle", 0))
        assertEquals("passed", (evaluateCase(WaterSipControlDriver, params, rows) as DriverOutcome.Verdict).value.state)
        val stale = rows.toMutableList(); stale[2] = water("after", 10, "minecraft:glass_bottle", 0)
        assertEquals("failed", (evaluateCase(WaterSipControlDriver, params, stale) as DriverOutcome.Verdict).value.state)
        assertTrue(DriverRegistry.supportedCases.contains("survival.food"))
        assertFalse(DriverRegistry.supportedCases.contains("water.manual"))
    }
    @Test fun `water tank unchanged count requires explicit fluid debit in target`() {
        val params = suiteMapper.readTree("""{
          "player_uuid":"123e4567-e89b-42d3-a456-426614174000","hand":"main_hand",
          "source_item":"tconstruct:copper_can","result_item":"tconstruct:copper_can","source_count":1,"result_count":1,
          "source_purity":3,"result_purity":3,"thirst_before":10,"thirst_after":14,
          "quenched_before":10,"quenched_after":14,
          "fluids_before":[{"tank":0,"fluid":"minecraft:water","amount":400,"purity":3}],
          "fluids_after":[{"tank":0,"fluid":"minecraft:water","amount":150,"purity":3}]
        }""")
        assertEquals(listOf("player.water_snapshot", "player.use_item", "player.water_snapshot"), WaterSipControlDriver.plan(params).map { it.action.id })
        val unpaid = params.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        (unpaid.path("fluids_after") as com.fasterxml.jackson.databind.node.ArrayNode).get(0)
            .let { (it as com.fasterxml.jackson.databind.node.ObjectNode).put("amount", 400) }
        assertThrows(IllegalArgumentException::class.java) { WaterSipControlDriver.plan(unpaid) }
    }
    private fun assertIsFatal(value: DriverOutcome) = assertTrue(value is DriverOutcome.Fatal, "unexpected nonfatal: $value")
}
