package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.drivers.DriverObservation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Source-only plan/negative regressions. Does not execute an input, GUI, framebuffer or world. */
class InventoryScreenControlDriverTest {
    private fun target() = suiteMapper.createObjectNode().put("player_uuid", "11111111-1111-1111-1111-111111111111")
        .put("dimension", "minecraft:overworld").put("input_profile", "1280x720@2").put("hold_id", "inventory-open")
    @Test fun planOpensActualInventoryAndPlansReadOnlyFrameWithoutDirectCallbackInjection() {
        val actions = InventoryScreenControlDriver.plan(target())
        assertEquals(listOf("player.snapshot", "client.snapshot", "client.input_hold", "client.input_release",
            "client.input_status", "client.snapshot", "client.framebuffer", "player.snapshot"),
            actions.map { it.action.id })
        assertEquals(listOf("server", "client", "client", "client", "client", "client", "client", "server"),
            actions.map { it.action.source })
        assertEquals("inventory", actions[2].action.parameters.path("kind").asText())
        assertEquals(6, actions[2].action.parameters.path("max_ticks").asInt())
        assertEquals("1280x720@2", actions[6].action.parameters.path("profile").asText())
        assertTrue(actions.none { it.action.id in setOf("player.interact", "player.use_item", "client.input_key",
            "client.input_click", "fixture.begin", "player.teleport") })
    }
    @Test fun unknownProfileInjectedStepsAndMissingTerminalsCannotPass() {
        val input = target()
        assertThrows(IllegalArgumentException::class.java) {
            InventoryScreenControlDriver.plan(input.deepCopy().put("input_profile", "wrong-profile"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            InventoryScreenControlDriver.plan(input.deepCopy().put("direct_key_callback", true))
        }
        assertThrows(IllegalArgumentException::class.java) { InventoryScreenControlDriver.evaluate(input, emptyList()) }
        val incomplete = InventoryScreenControlDriver.plan(input).map { row ->
            DriverObservation(row.name, row.action.id, row.action.source, "completed", suiteMapper.createObjectNode())
        }.dropLast(1)
        assertThrows(IllegalArgumentException::class.java) { InventoryScreenControlDriver.evaluate(input, incomplete) }
    }
}
