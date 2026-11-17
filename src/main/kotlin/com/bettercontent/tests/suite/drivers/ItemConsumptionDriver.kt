package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

/** One normal duration-completed item intake; not the ten-food or water-purity campaign. */
internal object ItemConsumptionDriver : CaseDriver {
    override val caseId = "control.food.item-consumption"
    private data class Target(val player: String, val dimension: String, val hand: String, val itemBefore: String,
        val itemAfter: String, val countBefore: Int, val countAfter: Int, val foodBefore: Int, val foodAfter: Int)
    private fun parse(node: JsonNode): Target {
        val fields = setOf("player_uuid", "dimension", "hand", "item_before", "item_after", "count_before", "count_after", "food_before", "food_after")
        require(node.isObject && node.fieldNames().asSequence().toSet() == fields) { "item consumption requires exact fields" }
        fun string(key: String): String { val value = node.get(key); require(value != null && value.isTextual) { "invalid $key" }; return value.asText() }
        fun integer(key: String, range: IntRange): Int {
            val value = node.get(key)
            require(value != null && value.isIntegralNumber && value.canConvertToInt() && value.asInt() in range) { "invalid $key" }
            return value.asInt()
        }
        val uuid = string("player_uuid")
        require(runCatching { UUID.fromString(uuid).toString() }.getOrNull() == uuid) { "noncanonical UUID" }
        val dimension = string("dimension"); val before = string("item_before"); val after = string("item_after")
        require(listOf(dimension, before, after).all { Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(it) } && before != "minecraft:air") { "invalid item/dimension" }
        val hand = string("hand"); require(hand in setOf("main_hand", "off_hand")) { "invalid hand" }
        val countBefore = integer("count_before", 1..64); val countAfter = integer("count_after", 0..64)
        require((before == after && countAfter == countBefore - 1) || (before != after && countBefore == 1 && countAfter in 0..1)) {
            "expected one item debit or an explicit single replacement container"
        }
        val foodBefore = integer("food_before", 0..19); val foodAfter = integer("food_after", 1..20)
        require(foodAfter > foodBefore) { "this food control requires independently measured hunger restoration" }
        return Target(uuid, dimension, hand, before, after, countBefore, countAfter, foodBefore, foodAfter)
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val t = parse(parameters)
        val actor = suiteMapper.createObjectNode().put("player_uuid", t.player)
        return listOf(DriverStep("before", DriverAction("player.snapshot", actor.deepCopy())),
            DriverStep("consume", DriverAction("player.use_item", actor.deepCopy().put("hand", t.hand), 72000)),
            DriverStep("after", DriverAction("player.snapshot", actor.deepCopy())))
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val t = parse(parameters)
        require(observations.size == 3) { "three ordered terminal observations required" }
        val before = observations[0].payload; val action = observations[1].payload; val after = observations[2].payload
        require(listOf(before, after).all { it.path("uuid").isTextual && it.path("dimension").isTextual &&
            it.path("food_level").isIntegralNumber && it.path("held_item").isTextual } &&
            action.path("uuid").isTextual && action.path("dimension").isTextual && action.path("hand").isTextual &&
            action.path("consumption_completed").isBoolean && action.path("food_before").isIntegralNumber && action.path("food_after").isIntegralNumber &&
            action.path("item_before").isTextual && action.path("item_after").isTextual &&
            action.path("count_before").isIntegralNumber && action.path("count_after").isIntegralNumber) { "native intake observation incomplete" }
        val identity = listOf(before, action, after).all { it.path("uuid").asText() == t.player && it.path("dimension").asText() == t.dimension }
        val valid = identity && action.path("hand").asText() == t.hand && action.path("consumption_completed").asBoolean() &&
            before.path("held_item").asText() == t.itemBefore && after.path("held_item").asText() == t.itemAfter &&
            action.path("item_before").asText() == t.itemBefore && action.path("item_after").asText() == t.itemAfter &&
            action.path("count_before").asInt() == t.countBefore && action.path("count_after").asInt() == t.countAfter &&
            before.path("food_level").asInt() == t.foodBefore && after.path("food_level").asInt() == t.foodAfter &&
            action.path("food_before").asInt() == t.foodBefore && action.path("food_after").asInt() == t.foodAfter
        return MeasurementVerdict(if (valid) "passed" else "failed", "normal duration-completed use and independently sampled hunger/item transition",
            mapOf("food_after" to after.path("food_level").asInt(), "item_after" to after.path("held_item").asText()))
    }
}
