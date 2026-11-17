package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

/** Native START/progress/STOP proof; pickup is same-actor window, not block-origin loot proof. */
internal object NativeDigControlDriver : CaseDriver {
    override val caseId = "control.primitive.native-dig"
    private data class Target(val player: String, val dimension: String, val x: Int, val y: Int, val z: Int,
        val face: String, val source: String, val result: String, val tool: String, val damageBefore: Int,
        val damageAfter: Int, val pickups: Map<String, Int>, val timeoutTicks: Int)
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private fun parse(node: JsonNode): Target {
        val fields = setOf("player_uuid", "dimension", "x", "y", "z", "face", "source_block", "result_block",
            "held_tool", "damage_before", "damage_after", "expected_pickups", "timeout_ticks")
        require(node.isObject && node.fieldNames().asSequence().toSet() == fields) { "native dig control target fields invalid" }
        fun text(key: String) = node.path(key).also { require(it.isTextual) { "invalid $key" } }.asText()
        fun number(key: String, range: IntRange): Int {
            val v = node.path(key); require(v.isIntegralNumber && v.canConvertToInt() && v.asInt() in range) { "invalid $key" }
            return v.asInt()
        }
        val player = text("player_uuid"); require(runCatching { UUID.fromString(player).toString() }.getOrNull() == player)
        val dimension = text("dimension"); val source = text("source_block"); val result = text("result_block"); val tool = text("held_tool")
        require(listOf(dimension, source, result, tool).all(resource::matches) && source != result)
        val face = text("face"); require(face in setOf("up", "down", "north", "south", "east", "west"))
        val pickups = node.path("expected_pickups"); require(pickups.isObject && pickups.size() <= 16)
        val expected = pickups.fields().asSequence().associate { (id, count) ->
            require(resource.matches(id) && count.isIntegralNumber && count.asInt() in 1..64) { "invalid pickup" }
            id to count.asInt()
        }
        val before = number("damage_before", 0..100_000); val after = number("damage_after", 0..100_000)
        require(after in before..before + 1) { "unexpected tool damage target" }
        return Target(player, dimension, number("x", -30_000_000..30_000_000), number("y", -2048..2048),
            number("z", -30_000_000..30_000_000), face, source, result, tool, before, after,
            expected, number("timeout_ticks", 20..72000))
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val t = parse(parameters)
        val actor = suiteMapper.createObjectNode().put("player_uuid", t.player)
        return listOf(DriverStep("before", DriverAction("player.snapshot", actor.deepCopy())),
            DriverStep("dig", DriverAction("player.dig", actor.deepCopy().put("dimension", t.dimension)
                .put("x", t.x).put("y", t.y).put("z", t.z).put("face", t.face), t.timeoutTicks)),
            DriverStep("after", DriverAction("player.snapshot", actor.deepCopy())))
    }
    private fun inventory(row: JsonNode): Map<String, Int> {
        val entries = row.path("inventory"); require(entries.isArray && entries.size() <= 41) { "unbounded inventory snapshot" }
        val slots = entries.map { slot ->
            require(slot.path("slot").isIntegralNumber && slot.path("slot").asInt() in 0..40 &&
                slot.path("item").isTextual && resource.matches(slot.path("item").asText()) &&
                slot.path("count").isIntegralNumber && slot.path("count").asInt() in 1..64) { "invalid inventory slot" }
            slot.path("slot").asInt() to (slot.path("item").asText() to slot.path("count").asInt())
        }
        require(slots.map { it.first }.distinct().size == slots.size) { "duplicate inventory slots" }
        return slots.groupBy({ it.second.first }, { it.second.second }).mapValues { it.value.sum() }
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val t = parse(parameters); require(observations.size == 3)
        val before = observations[0].payload; val action = observations[1].payload; val after = observations[2].payload
        require(listOf(before, after).all { it.path("uuid").isTextual && it.path("dimension").isTextual &&
            it.path("held_item").isTextual && it.path("held_damage").isIntegralNumber && it.path("inventory").isArray } &&
            action.path("player_uuid").isTextual && action.path("dimension").isTextual &&
            action.path("before_block").isTextual && action.path("after_block").isTextual &&
            action.path("held_item_before").isTextual && action.path("held_item_after").isTextual &&
            action.path("held_damage_before").isIntegralNumber && action.path("held_damage_after").isIntegralNumber &&
            action.path("pickup_events").isArray && action.path("break_event_seen").isBoolean &&
            action.path("break_event_canceled").isBoolean && action.path("destroyed").isBoolean &&
            action.path("start_tick").isIntegralNumber && action.path("stop_tick").isIntegralNumber &&
            action.path("observed_tick").isIntegralNumber && action.path("native_destroy_progress_per_tick").isNumber &&
            action.path("dig_protocol").isTextual && action.path("pickup_correlation").isTextual) {
            "real native dig lifecycle or observation missing"
        }
        val actualPickups = action.path("pickup_events").groupBy({ it.path("item").asText("") }, { it.path("count").asInt(-1) })
            .mapValues { (_, counts) -> counts.sum() }
        val inventoryBefore = inventory(before); val inventoryAfter = inventory(after)
        val inventoryDelta = (inventoryBefore.keys + inventoryAfter.keys).associateWith {
            inventoryAfter.getOrDefault(it, 0) - inventoryBefore.getOrDefault(it, 0)
        }.filterValues { it != 0 }
        require(action.path("pickup_events").all { it.path("item").isTextual && it.path("count").isIntegralNumber &&
            it.path("count").asInt() in 1..64 && it.path("tick").isIntegralNumber }) { "untyped pickup event" }
        val valid = before.path("uuid").asText() == t.player && after.path("uuid").asText() == t.player &&
            before.path("dimension").asText() == t.dimension && after.path("dimension").asText() == t.dimension &&
            action.path("player_uuid").asText() == t.player && action.path("dimension").asText() == t.dimension &&
            action.path("x").asInt(Int.MIN_VALUE) == t.x && action.path("y").asInt(Int.MIN_VALUE) == t.y &&
            action.path("z").asInt(Int.MIN_VALUE) == t.z && action.path("face").asText() == t.face &&
            action.path("before_block").asText() == t.source && action.path("after_block").asText() == t.result &&
            action.path("destroyed").asBoolean() && action.path("break_event_seen").asBoolean() &&
            !action.path("break_event_canceled").asBoolean() &&
            action.path("dig_protocol").asText() == "native_game_mode_START_PROGRESS_STOP" &&
            action.path("pickup_correlation").asText() in setOf("same_actor_window_only_not_origin_proof",
                "UUID_match_only; custom_bypass_paths_uncovered") &&
            action.path("native_destroy_progress_per_tick").asDouble().isFinite() &&
            action.path("native_destroy_progress_per_tick").asDouble() > 0 &&
            action.path("start_tick").asLong() < action.path("stop_tick").asLong() &&
            action.path("stop_tick").asLong() <= action.path("observed_tick").asLong() &&
            before.path("held_item").asText() == t.tool && after.path("held_item").asText() == t.tool &&
            before.path("held_damage").asInt() == t.damageBefore && after.path("held_damage").asInt() == t.damageAfter &&
            action.path("held_item_before").asText() == t.tool && action.path("held_item_after").asText() == t.tool &&
            action.path("held_damage_before").asInt() == t.damageBefore && action.path("held_damage_after").asInt() == t.damageAfter &&
            actualPickups == t.pickups && inventoryDelta == t.pickups
        return MeasurementVerdict(if (valid) "passed" else "failed",
            "native timed mining + same-actor pickup window, not origin-proven loot or natural tool acquisition",
            mapOf("pickup_counts" to actualPickups, "inventory_delta" to inventoryDelta, "observed_stop_tick" to action.path("stop_tick").asLong()))
    }
}
