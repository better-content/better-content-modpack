package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Source-only future passive wire plan regressions, not mining/world/GUI tests. */
class PhysicalMiningControlDriverTest {
    private val actor = "123e4567-e89b-42d3-a456-426614174000"
    private fun target() = suiteMapper.readTree("""{"player_uuid":"$actor","dimension":"minecraft:overworld",
        "selector_id":"reviewed_ore_a","x":100,"y":64,"z":200,"face":"north","block_id":"minecraft:iron_ore",
        "tool_item_id":"minecraft:iron_pickaxe","tool_canonical_sha256":"${"a".repeat(64)}",
        "tool_acquisition_case_id":"acquire.iron-pickaxe","observer_id":"123e4567-e89b-42d3-a456-426614174001",
        "hold_id":"mining_0","input_profile":"1280x720@2","max_ticks":120,
        "expected_packet_variant":"progress_start_stop","expected_drop_outcome":"one_full_pickup",
        "expected_drop_item_id":"minecraft:raw_iron","expected_drop_count":1,
        "expected_tool_break_callback_count":0}""")
    @Test fun `frozen prior crosshair and passive real packet arm precede physical mouse press`() {
        val steps = PhysicalMiningControlDriver.plan(target())
        assertEquals(listOf("player.snapshot", "block.snapshot", "client.crosshair", "player.observe_dig.arm_v5",
            "client.input_hold", "client.input_release", "client.input_status", "player.observe_dig.await_v5",
            "block.snapshot", "player.snapshot"), steps.map { it.action.id })
        assertEquals(listOf("server", "server", "client", "server", "client", "client", "client", "server", "server", "server"),
            steps.map { it.action.source })
        assertTrue(steps.none { it.action.id in setOf("player.dig", "player.dig.witness_v1", "player.break_block", "player.attack") })
        assertEquals("@controller-natural-bound-target-sha256", steps[3].action.parameters.path("bound_target_sha256").asText())
        assertEquals("@controller-reviewed-crosshair-sha256", steps[4].action.parameters.path("crosshair_projection_sha256").asText())
        assertEquals("@input-runtime-sha256", steps[4].action.parameters.path("input_runtime_sha256").asText())
        assertEquals("attack_block", steps[4].action.parameters.path("kind").asText())
        assertEquals("NORTH", steps[3].action.parameters.path("expected_face").asText())
        assertEquals(156, steps[3].action.parameters.path("deadline_ticks").asInt())
        assertEquals("progress_start_stop", steps[3].action.parameters.path("expected_packet_variant").asText())
        assertEquals("one_full_pickup", steps[3].action.parameters.path("expected_drop_outcome").asText())
        assertTrue(steps[3].action.parameters.path("expected_harvest_result").asBoolean())
        assertTrue(steps.none { it.action.id == "wait.ticks" })
        assertTrue(steps.indexOfFirst { it.action.id == "client.input_status" } <
            steps.indexOfFirst { it.action.id == "player.observe_dig.await_v5" })
        assertEquals(mapOf("minecraft:iron_pickaxe" to "acquire.iron-pickaxe"),
            PhysicalMiningControlDriver.requiredAcquisitionCases(target()))
        assertTrue(PhysicalMiningControlDriver.fixtureProvisionedTargets(target()).isEmpty())
        assertFalse(PhysicalMiningControlDriver.caseId in DriverRegistry.supportedControls)
    }
    @Test fun `unknown marker source or changed target never becomes silently substituted mining`() {
        val added = target().deepCopy<ObjectNode>().put("teleport", true)
        assertThrows(IllegalArgumentException::class.java) { PhysicalMiningControlDriver.plan(added) }
        val missing = target().deepCopy<ObjectNode>().put("max_ticks", 201)
        assertThrows(IllegalArgumentException::class.java) { PhysicalMiningControlDriver.plan(missing) }
        assertTrue(evaluateCase(PhysicalMiningControlDriver, target(), emptyList()) is DriverOutcome.Fatal)
    }
}
