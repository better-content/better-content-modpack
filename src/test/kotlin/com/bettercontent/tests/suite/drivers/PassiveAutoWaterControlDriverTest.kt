package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Synthetic B-wire parser source only. No Curios callback, provider, or game was run. */
class PassiveAutoWaterControlDriverTest {
    private val actor = "123e4567-e89b-42d3-a456-426614174000"
    private val observer = "123e4567-e89b-42d3-a456-426614174001"
    private val provider = "a".repeat(64)
    private val source = "b".repeat(64)
    private fun params() = suiteMapper.createObjectNode().put("player_uuid", actor)
        .put("dimension", "minecraft:overworld").put("observer_id", observer)
        .put("provider_artifact_sha256", provider).put("source_stack_canonical_sha256", source)
    private fun cell(slot: Int, id: String? = null, hash: String = "c".repeat(64)): ObjectNode =
        suiteMapper.createObjectNode().put("slot", slot).put("present", id != null)
            .put("count", if (id == null) 0 else 1).apply {
                if (id == null) { putNull("id"); putNull("stack_canonical_sha256"); putNull("stack_content_sha256") }
                else { put("id", id); put("stack_canonical_sha256", hash);
                    put("stack_content_sha256", "d".repeat(64)) }
            }
    private fun group(kind: String, available: Boolean, vararg cells: JsonNode): ObjectNode =
        suiteMapper.createObjectNode().put("kind", kind).put("handler_present", available).apply {
            set<ArrayNode>("slots", suiteMapper.createArrayNode().addAll(cells.asList()))
        }
    private fun snapshot(tick: Int, purchased: Boolean): ObjectNode = suiteMapper.createObjectNode().apply {
        put("schema", "bc.playtest_water_auto_snapshot.v1"); put("player_uuid", actor)
        put("dimension", "minecraft:overworld"); put("provider_artifact_sha256", provider)
        put("server_tick", tick); put("server_monotonic_nanos", 100000 + tick)
        put("player_tick_count", tick); put("thirst", if (purchased) 10 else 6); put("quenched", 0)
        put("backpack_contents_complete", false); put("untracked_dropped_vessel_entities_complete", false)
        put("source_acquisition_proven_by_native", false)
        set<ObjectNode>("water_slots", group("water", true,
            if (purchased) cell(0) else cell(0, "minecraft:potion", source)))
        set<ObjectNode>("empty_bottle_slots", group("empty_bottle", true,
            if (purchased) cell(0, "minecraft:glass_bottle") else cell(0)))
        set<ObjectNode>("better_backpack_slots", group("better_backpack", false))
        set<ArrayNode>("vanilla_inventory", suiteMapper.createArrayNode().apply {
            (0..40).forEach { add(cell(it)) }
        })
        set<ObjectNode>("reserve", suiteMapper.createObjectNode().put("present", false))
    }
    private fun observations(): MutableList<DriverObservation> {
        val before = snapshot(1, false); val armed = snapshot(1, false)
        val callbackBefore = snapshot(2, false); val callbackAfter = snapshot(2, true)
        val arm = suiteMapper.createObjectNode().put("schema", "bc.playtest_passive_water_auto_arm.v1")
            .put("observer_id", observer).put("actor_uuid", actor).put("dimension", "minecraft:overworld")
            .put("provider_artifact_sha256", provider).put("arm_request_sequence", 2)
            .put("arm_action_ordinal", 2).put("armed_before_provider_tick", true)
            .put("natural_hydration_proven_by_native", false)
            .set<ObjectNode>("arm_snapshot", armed)
        val receipt = suiteMapper.createObjectNode().put("schema", "bc.playtest_passive_water_auto.v1")
            .put("observer_id", observer).put("actor_uuid", actor).put("dimension", "minecraft:overworld")
            .put("provider_artifact_sha256", provider).put("source_stack_canonical_sha256", source)
            .put("source_count", 1).put("eligible_provider_callback_count", 1).put("callback_tick", 2)
            .put("arm_request_sequence", 2).put("arm_action_ordinal", 2)
            .put("observation_complete", true).put("natural_hydration_proven_by_native", false)
            .put("source_acquisition_proven_by_native", false)
            .put("backpack_or_overflow_destination_proven_by_native", false).putNull("operation_id")
            .set<ObjectNode>("arm_snapshot", armed.deepCopy())
        receipt.set<ObjectNode>("callback_before", callbackBefore)
        receipt.set<ObjectNode>("callback_after", callbackAfter)
        val after = snapshot(3, true)
        return listOf("before", "arm", "await-first-actual-provider-callback", "after").mapIndexed { i, name ->
            DriverObservation(name, listOf("player.water_auto_snapshot_v1", "player.water_auto_observe.arm_v1",
                "player.water_auto_observe.await_v1", "player.water_auto_snapshot_v1")[i],
                "server", "completed", listOf(before, arm, receipt, after)[i])
        }.toMutableList()
    }
    @Test fun `one complete provider callback passes ONLY the bounded diagnostic not acquisition`() {
        val plan = PassiveAutoWaterControlDriver.plan(params())
        assertEquals(listOf("player.water_auto_snapshot_v1", "player.water_auto_observe.arm_v1",
            "player.water_auto_observe.await_v1", "player.water_auto_snapshot_v1"), plan.map { it.action.id })
        assertEquals("passed", PassiveAutoWaterControlDriver.evaluate(params(), observations()).state)
    }
    @Test fun `complete contradicted destination fails diagnostic but unsupported capture blocks`() {
        val wrong = observations()
        val post = wrong[2].payload.path("callback_after") as ObjectNode
        val after = wrong[3].payload as ObjectNode
        (post.path("empty_bottle_slots").path("slots")[0] as ObjectNode).put("id", "minecraft:stone")
        (after.path("empty_bottle_slots").path("slots")[0] as ObjectNode).put("id", "minecraft:stone")
        assertEquals("failed", PassiveAutoWaterControlDriver.evaluate(params(), wrong).state)
        val incomplete = observations(); (incomplete[2].payload as ObjectNode).put("observation_complete", false)
        assertThrows(IllegalArgumentException::class.java) {
            PassiveAutoWaterControlDriver.evaluate(params(), incomplete)
        }
        val invented = observations(); (invented[2].payload as ObjectNode).put("source_acquisition_proven_by_native", true)
        assertThrows(IllegalArgumentException::class.java) { PassiveAutoWaterControlDriver.evaluate(params(), invented) }
        val swapped = observations(); (swapped[0].payload as ObjectNode).put("provider_artifact_sha256", "e".repeat(64))
        assertThrows(IllegalArgumentException::class.java) { PassiveAutoWaterControlDriver.evaluate(params(), swapped) }
    }
}
