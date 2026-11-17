package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

/** Observes real death/clone/respawn events; does not trigger or attribute a death stimulus. */
internal object DeathLifecycleControlDriver : CaseDriver {
    override val caseId = "control.lifecycle.death-respawn-events"
    private data class Target(val player: String, val cause: String, val dimension: String, val healthMin: Double)
    private fun parse(node: JsonNode): Target {
        require(node.isObject && node.fieldNames().asSequence().toSet() ==
            setOf("player_uuid", "death_cause", "respawn_dimension", "minimum_health")) { "death observation target fields unknown/missing" }
        fun text(key: String): String { val v = node.path(key); require(v.isTextual); return v.asText() }
        val player = text("player_uuid"); require(runCatching { UUID.fromString(player).toString() }.getOrNull() == player)
        val dimension = text("respawn_dimension"); val cause = text("death_cause")
        require(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(dimension) && cause.isNotBlank() && cause.length <= 160)
        val health = node.path("minimum_health"); require(health.isNumber && health.asDouble().isFinite() && health.asDouble() in 0.0..20.0)
        return Target(player, cause, dimension, health.asDouble())
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val p = parse(parameters)
        return listOf(DriverStep("lifecycle", DriverAction("player.lifecycle_snapshot", suiteMapper.createObjectNode().put("player_uuid", p.player))))
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val p = parse(parameters); require(observations.size == 1)
        val row = observations.single().payload
        require(row.path("uuid").isTextual && row.path("dimension").isTextual && row.path("health").isNumber &&
            listOf("death_observed", "death_canceled", "clone_observed", "clone_was_death", "respawn_observed").all { row.path(it).isBoolean } &&
            listOf("death_tick", "clone_tick", "respawn_tick").all { row.path(it).isIntegralNumber } &&
            row.path("death_cause").isTextual && row.path("native_event_source").isTextual) { "native lifecycle event history incomplete" }
        val matched = row.path("uuid").asText() == p.player && row.path("dimension").asText() == p.dimension &&
            row.path("health").asDouble().isFinite() && row.path("health").asDouble() >= p.healthMin &&
            row.path("death_observed").asBoolean() && !row.path("death_canceled").asBoolean() &&
            row.path("clone_observed").asBoolean() && row.path("clone_was_death").asBoolean() &&
            row.path("respawn_observed").asBoolean() && row.path("death_cause").asText() == p.cause &&
            row.path("native_event_source").asText() == "LivingDeathEvent/PlayerEvent.Clone/PlayerRespawnEvent" &&
            row.path("death_tick").asLong() >= 0 && row.path("clone_tick").asLong() >= row.path("death_tick").asLong() &&
            row.path("respawn_tick").asLong() >= row.path("clone_tick").asLong()
        return MeasurementVerdict(if (matched) "passed" else "failed", "native death/clone/respawn event chain only; no stimulus or retained mod-state proof")
    }
}
