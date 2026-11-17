package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.UUID

/** Controlled direct gameMode.destroyBlock; not Survival packet mining progression or natural acquisition. */
internal object PrimitiveBreakDriver : CaseDriver {
    override val caseId = "control.primitive.tool-break"
    private data class Target(val player: String, val dimension: String, val x: Int, val y: Int, val z: Int,
        val block: String, val afterBlock: String, val tool: String, val damageBefore: Int, val damageAfter: Int)
    private fun target(node: JsonNode): Target {
        val keys = setOf("player_uuid", "dimension", "x", "y", "z", "block_id", "after_block_id", "held_tool_id", "damage_before", "damage_after")
        require(node.isObject && node.fieldNames().asSequence().toSet() == keys) { "primitive target must have exact fields" }
        fun text(key: String): String { val n = node.get(key); require(n != null && n.isTextual) { "invalid $key" }; return n.asText() }
        fun integer(key: String, min: Int, max: Int): Int {
            val n = node.get(key); require(n != null && n.isIntegralNumber && n.canConvertToInt() && n.asInt() in min..max) { "invalid $key" }
            return n.asInt()
        }
        val player = text("player_uuid")
        require(runCatching { UUID.fromString(player).toString() }.getOrNull() == player) { "invalid UUID" }
        val dimension = text("dimension"); val block = text("block_id"); val after = text("after_block_id"); val tool = text("held_tool_id")
        require(listOf(dimension, block, after, tool).all { Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(it) } && block != after) {
            "invalid resource identities or unchanged target"
        }
        val beforeDamage = integer("damage_before", 0, 100_000)
        val afterDamage = integer("damage_after", 0, 100_000)
        require(afterDamage == beforeDamage + 1) { "expected exactly one real tool-durability debit" }
        return Target(player, dimension, integer("x", -30_000_000, 30_000_000), integer("y", -2048, 2048),
            integer("z", -30_000_000, 30_000_000), block, after, tool, beforeDamage, afterDamage)
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val t = target(parameters)
        fun objectNode(vararg pairs: Pair<String, String>) = suiteMapper.createObjectNode().apply { pairs.forEach { (key, value) -> put(key, value) } }
        val snapshot = objectNode("player_uuid" to t.player)
        val breakParams: ObjectNode = objectNode("player_uuid" to t.player, "dimension" to t.dimension).apply {
            put("x", t.x); put("y", t.y); put("z", t.z)
        }
        return listOf(DriverStep("before", DriverAction("player.snapshot", snapshot.deepCopy())),
            DriverStep("break", DriverAction("player.break_block", breakParams)),
            DriverStep("after", DriverAction("player.snapshot", snapshot.deepCopy())))
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val t = target(parameters)
        require(observations.size == 3) { "three ordered terminal observations required" }
        val before = observations[0].payload; val action = observations[1].payload; val after = observations[2].payload
        require(before.path("uuid").isTextual && after.path("uuid").isTextual &&
            before.path("dimension").isTextual && after.path("dimension").isTextual &&
            action.path("uuid").isTextual && action.path("destroyed").isBoolean &&
            action.path("before_block_state").path("id").isTextual && action.path("after_block_state").path("id").isTextual &&
            action.path("held_item_before").isTextual && action.path("held_item_after").isTextual &&
            action.path("held_damage_before").isIntegralNumber && action.path("held_damage_after").isIntegralNumber &&
            before.path("held_item").isTextual && after.path("held_item").isTextual &&
            before.path("held_damage").isIntegralNumber && after.path("held_damage").isIntegralNumber &&
            action.path("x").isIntegralNumber && action.path("y").isIntegralNumber && action.path("z").isIntegralNumber) {
            "tool or block native observations missing"
        }
        val identity = listOf(before, action, after).all { it.path("uuid").asText() == t.player && it.path("dimension").asText() == t.dimension }
        val consistent = identity && action.path("x").asInt() == t.x && action.path("y").asInt() == t.y &&
            action.path("z").asInt() == t.z && action.path("destroyed").asBoolean() &&
            action.path("before_block_state").path("id").asText() == t.block &&
            action.path("after_block_state").path("id").asText() == t.afterBlock &&
            action.path("held_item_before").asText() == t.tool && action.path("held_item_after").asText() == t.tool &&
            action.path("held_damage_before").asInt() == t.damageBefore && action.path("held_damage_after").asInt() == t.damageAfter &&
            before.path("held_item").asText() == t.tool && after.path("held_item").asText() == t.tool &&
            before.path("held_damage").asInt() == t.damageBefore && after.path("held_damage").asInt() == t.damageAfter
        return MeasurementVerdict(if (consistent) "passed" else "failed",
            "controlled destroyBlock and held tool durability transition; natural mining/drops/acquisition not certified",
            mapOf("observed_block_after" to action.path("after_block_state").path("id").asText(),
                "observed_damage_after" to action.path("held_damage_after").asInt()))
    }
}
