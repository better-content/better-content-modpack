package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Source-only shape regressions. Synthetic live snapshots never establish save/reopen persistence. */
class VanillaReopenControlDriverTest {
    private val actor = "123e4567-e89b-42d3-a456-426614174000"
    private val digest = "a".repeat(64)
    private val parameters = suiteMapper.readTree("""{"dimension":"minecraft:overworld"}""")
    private fun observation() : DriverObservation {
        val payload = suiteMapper.createObjectNode().put("uuid", actor).put("dimension", "minecraft:overworld")
            .put("mode", "survival").put("server_tick", 24).put("selected_slot", 0)
            .put("held_item", "test:seed").put("held_count", 1).put("held_stack_canonical_sha256", digest)
        val occupied = suiteMapper.createArrayNode().add(suiteMapper.createObjectNode().put("slot", 0)
            .put("item", "test:seed").put("count", 1)
            .put("stack_canonical_algorithm", "bc.playtest.stack-nbt.v1").put("stack_canonical_sha256", digest))
        payload.set<ArrayNode>("inventory", occupied)
        val slots = suiteMapper.createArrayNode()
        for (i in 0..40) {
            val row = suiteMapper.createObjectNode().put("slot", i).put("empty", i != 0)
                .put("count", if (i == 0) 1 else 0)
            if (i == 0) row.put("item", "test:seed").put("stack_canonical_algorithm", "bc.playtest.stack-nbt.v1")
                .put("stack_canonical_sha256", digest)
            else row.putNull("item").putNull("stack_canonical_algorithm").putNull("stack_canonical_sha256")
            slots.add(row)
        }
        payload.set<ObjectNode>("inventory_slots_v1", suiteMapper.createObjectNode()
            .put("schema", "bc.playtest_vanilla_inventory_slots.v1")
            .put("source", "actual_server_player_vanilla_inventory_0_40")
            .put("external_capabilities_included", false).put("sealed_playerdata_proven_by_native", false)
            .apply { set<ArrayNode>("slots", slots) })
        return DriverObservation("first-readonly-reopened-player", "player.snapshot", "server", "completed", payload)
    }
    @Test fun `the only first action is read-only joined actor snapshot with controller marker`() {
        val plan = VanillaReopenControlDriver.plan(parameters)
        assertEquals(1, plan.size)
        assertEquals("player.snapshot", plan.single().action.id)
        assertEquals("server", plan.single().action.source)
        assertEquals("@observed-player-uuid", plan.single().action.parameters.path("player_uuid").asText())
        assertTrue(VanillaReopenControlDriver.clientRequired)
        assertFalse("control.persistence.vanilla-reopen" in DriverRegistry.supportedControls)
        val result = (evaluateCase(VanillaReopenControlDriver, parameters, listOf(observation())) as DriverOutcome.Verdict).value
        assertEquals("inconclusive", result.state)
        assertEquals(41, (result.observations.getValue("live_vanilla_slots_v1") as List<*>).size)
    }
    @Test fun `occupied-only omissions fake empty and malformed selected stack never become persistence`() {
        val omitted = observation().payload.deepCopy<ObjectNode>()
        omitted.remove("inventory_slots_v1")
        assertTrue(evaluateCase(VanillaReopenControlDriver, parameters,
            listOf(observation().copy(payload = omitted))) is DriverOutcome.Fatal)
        val fake = observation().payload.deepCopy<ObjectNode>()
        (fake.path("inventory_slots_v1").path("slots") as ArrayNode)[1].let {
            (it as ObjectNode).put("stack_canonical_sha256", digest)
        }
        assertTrue(evaluateCase(VanillaReopenControlDriver, parameters,
            listOf(observation().copy(payload = fake))) is DriverOutcome.Fatal)
        val wrong = observation().payload.deepCopy<ObjectNode>().put("held_stack_canonical_sha256", "b".repeat(64))
        assertTrue(evaluateCase(VanillaReopenControlDriver, parameters,
            listOf(observation().copy(payload = wrong))) is DriverOutcome.Fatal)
        assertThrows(IllegalArgumentException::class.java) {
            VanillaReopenControlDriver.plan(suiteMapper.readTree("""{"dimension":"minecraft:overworld","fixture_id":"fake"}"""))
        }
    }
}
