package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Synthetic reviewed-target shape only; no tests/GUI/capture/world/input executed. */
class EmiNavigationControlDriverTest {
    private fun target(): ObjectNode = suiteMapper.createObjectNode().apply {
        put("player_uuid", "11111111-1111-1111-1111-111111111111")
        put("dimension", "minecraft:overworld"); put("input_profile", "1280x720@2")
        put("emi_jar_sha256", "a".repeat(64)); put("inventory_hold_id", "open-inventory")
        put("navigation_hold_id", "open-recipes"); put("navigation", "recipes")
        put("gui_x", 140); put("gui_y", 70); put("hover_kind", "tag")
        put("hover_id", "minecraft:logs")
        set<JsonNode>("hover_member_ids", suiteMapper.createArrayNode().add("minecraft:oak_log"))
        put("expected_category_id", "minecraft:crafting")
        set<JsonNode>("expected_visible_recipe_ids", suiteMapper.createArrayNode().add("minecraft:oak_planks"))
    }
    @Test fun frozenTagHoverAndRealMouseNavigationOnlyArePlanned() {
        val plan = EmiNavigationControlDriver.plan(target())
        assertEquals("control.client.emi-navigation", EmiNavigationControlDriver.caseId)
        assertEquals(listOf("player.snapshot", "client.snapshot", "client.input_hold", "client.input_release",
            "client.input_status", "client.input_point", "wait.ticks", "client.emi_hover",
            "client.input_hold", "client.input_release", "client.input_status", "client.emi_page",
            "client.framebuffer", "client.emi_page", "player.snapshot"), plan.map { it.action.id })
        assertEquals(EMI_HOVER_PROJECTION_MARKER,
            plan[8].action.parameters.path("hover_projection_sha256").asText())
        assertEquals("emi_mouse_left", plan[8].action.parameters.path("kind").asText())
        assertTrue(plan.none { it.action.id in setOf("client.input_click", "client.input_key", "player.teleport",
            "fixture.begin", "player.interact", "player.use_item") })
    }
    @Test fun unreviewedCoordinateTagVariantProfileAndSelectedPageFailBeforeInput() {
        val base = target()
        fun rejected(edit: (ObjectNode) -> Unit) = assertThrows(IllegalArgumentException::class.java) {
            EmiNavigationControlDriver.plan(base.deepCopy().apply(edit))
        }
        rejected { it.put("gui_x", 640) }
        rejected { it.put("gui_y", -1) }
        rejected { it.put("input_profile", "960x600@2") }
        rejected { it.put("navigation", "displayApi") }
        rejected { it.put("hover_kind", "stack") }
        rejected { it.set<JsonNode>("expected_visible_recipe_ids", suiteMapper.createArrayNode()) }
        rejected { it.put("direct_callback", true) }
        assertThrows(IllegalArgumentException::class.java) {
            EmiNavigationControlDriver.evaluate(base, emptyList())
        }
    }
}
