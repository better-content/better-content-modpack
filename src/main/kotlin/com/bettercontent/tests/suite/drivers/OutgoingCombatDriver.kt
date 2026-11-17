package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import kotlin.math.abs

/** Real Survival ServerPlayer.attack against a pre-existing loaded hostile; no spawned target. */
internal object OutgoingCombatDriver : CaseDriver {
    override val caseId = "combat.outgoing"
    override val assertionIds = setOf("combat.outgoing.attributed-hit", "combat.outgoing.equipment-consumption")
    private data class Target(val player: String, val target: String, val type: String, val dimension: String, val held: String,
        val healthBefore: Double, val healthAfter: Double, val damageBefore: Int, val damageAfter: Int)
    private fun parse(node: JsonNode): Target {
        require(node.isObject && node.fieldNames().asSequence().toSet() == setOf("player_uuid", "target_uuid", "target_type", "dimension",
            "held_item", "target_health_before", "target_health_after", "held_damage_before", "held_damage_after")) {
            "combat target fields unknown/missing"
        }
        fun text(key: String): String { val v = node.path(key); require(v.isTextual); return v.asText() }
        fun uuid(key: String): String { val v = text(key); require(runCatching { UUID.fromString(v).toString() }.getOrNull() == v); return v }
        fun health(key: String): Double {
            val v = node.path(key); require(v.isNumber && v.asDouble().isFinite() && v.asDouble() in 0.0..1_000_000.0)
            return v.asDouble()
        }
        fun damage(key: String): Int { val v = node.path(key); require(v.isIntegralNumber && v.asInt() in 0..100_000); return v.asInt() }
        val type = text("target_type"); val dimension = text("dimension"); val held = text("held_item")
        require(listOf(type, held, dimension).all { Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(it) })
        val before = health("target_health_before"); val after = health("target_health_after")
        require(after > 0.0 && after < before) { "nonlethal independently observable target health loss required" }
        val damageBefore = damage("held_damage_before"); val damageAfter = damage("held_damage_after")
        require(damageAfter in damageBefore..damageBefore + 1) { "held durability expectation unreasonable" }
        return Target(uuid("player_uuid"), uuid("target_uuid"), type, dimension, held, before, after, damageBefore, damageAfter)
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val t = parse(parameters); val actor = suiteMapper.createObjectNode().put("player_uuid", t.player)
        val target = suiteMapper.createObjectNode().put("entity_uuid", t.target).put("target_type", t.type).put("dimension", t.dimension)
        return listOf(DriverStep("before-player", DriverAction("player.snapshot", actor.deepCopy())),
            DriverStep("before-target", DriverAction("entity.snapshot", target.deepCopy())),
            DriverStep("attack", DriverAction("player.attack", actor.deepCopy().put("target_uuid", t.target).put("target_type", t.type))),
            DriverStep("after-target", DriverAction("entity.snapshot", target.deepCopy())),
            DriverStep("after-player", DriverAction("player.snapshot", actor.deepCopy())))
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val t = parse(parameters); require(observations.size == 5)
        val before = observations[0].payload; val targetBefore = observations[1].payload
        val hit = observations[2].payload; val targetAfter = observations[3].payload; val after = observations[4].payload
        require(listOf(before, after).all { it.path("uuid").isTextual && it.path("held_item").isTextual &&
            it.path("held_damage").isIntegralNumber && it.path("health").isNumber } &&
            listOf("player_uuid", "target_uuid", "target_type", "attack_source").all { hit.path(it).isTextual } &&
            listOf("target_health_before", "target_health_after", "player_health_before", "player_health_after").all { hit.path(it).isNumber } &&
            hit.path("held_damage_before").isIntegralNumber && hit.path("held_damage_after").isIntegralNumber &&
            hit.path("target_alive_after").isBoolean && listOf(targetBefore, targetAfter).all {
                it.path("entity_uuid").isTextual && it.path("target_type").isTextual && it.path("dimension").isTextual &&
                    it.path("present").isBoolean &&
                    (it.path("present").asBoolean() && it.path("alive").isBoolean && it.path("health").isNumber ||
                        !it.path("present").asBoolean() && it.path("absence_reason").isTextual)
            }) { "real independent target/combat/equipment evidence absent" }
        val observedBefore = hit.path("target_health_before").asDouble(); val observedAfter = hit.path("target_health_after").asDouble()
        require(observedBefore.isFinite() && observedAfter.isFinite()) { "nonfinite target health" }
        if (!targetAfter.path("present").asBoolean() && targetAfter.path("absence_reason").asText() == "unknown_unload_or_removal")
            return MeasurementVerdict("inconclusive", "target disappeared without independently observed death/health")
        val independent = listOf(targetBefore, targetAfter).all {
            it.path("entity_uuid").asText() == t.target && it.path("target_type").asText() == t.type &&
                it.path("dimension").asText() == t.dimension && it.path("present").asBoolean() && it.path("alive").asBoolean()
        } && abs(targetBefore.path("health").asDouble() - t.healthBefore) <= .001 &&
            abs(targetAfter.path("health").asDouble() - t.healthAfter) <= .001
        val good = independent && before.path("uuid").asText() == t.player && after.path("uuid").asText() == t.player &&
            hit.path("player_uuid").asText() == t.player && hit.path("target_uuid").asText() == t.target &&
            hit.path("target_type").asText() == t.type && hit.path("attack_source").asText() == "ServerPlayer.attack" &&
            before.path("held_item").asText() == t.held && after.path("held_item").asText() == t.held &&
            before.path("held_damage").asInt() == t.damageBefore && after.path("held_damage").asInt() == t.damageAfter &&
            hit.path("held_damage_before").asInt() == t.damageBefore && hit.path("held_damage_after").asInt() == t.damageAfter &&
            abs(observedBefore - t.healthBefore) <= .001 && abs(observedAfter - t.healthAfter) <= .001 &&
            observedBefore > observedAfter &&
            abs(observedBefore - targetBefore.path("health").asDouble()) <= .001 &&
            abs(observedAfter - targetAfter.path("health").asDouble()) <= .001 &&
            abs(before.path("health").asDouble() - hit.path("player_health_before").asDouble()) <= .001 &&
            abs(after.path("health").asDouble() - hit.path("player_health_after").asDouble()) <= .001
        return MeasurementVerdict(if (good) "passed" else "failed", "observed native player attack damaged same loaded hostile and debited authored equipment",
            mapOf("health_delta" to observedBefore - observedAfter))
    }
}
