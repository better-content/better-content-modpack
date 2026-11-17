package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import kotlin.math.abs

/** Observed real login landing only; prior player-data history and terrain safety remain unproved. */
internal object WorldEntryControlDriver : CaseDriver {
    override val caseId = "control.world.login-landing"
    private data class Target(val player: String, val dimension: String, val spawnX: Int, val spawnY: Int,
        val spawnZ: Int, val maximumHorizontalDistance: Int)
    private fun parse(node: JsonNode): Target {
        require(node.isObject && node.fieldNames().asSequence().toSet() ==
            setOf("player_uuid", "dimension", "spawn_x", "spawn_y", "spawn_z", "maximum_horizontal_distance")) {
            "login landing target requires exact fields"
        }
        fun text(key: String): String { val v = node.get(key); require(v != null && v.isTextual) { "invalid $key" }; return v.asText() }
        fun int(key: String, range: IntRange): Int {
            val v = node.get(key); require(v != null && v.isIntegralNumber && v.canConvertToInt() && v.asInt() in range) { "invalid $key" }
            return v.asInt()
        }
        val uuid = text("player_uuid"); require(runCatching { UUID.fromString(uuid).toString() }.getOrNull() == uuid)
        val dimension = text("dimension"); require(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(dimension))
        return Target(uuid, dimension, int("spawn_x", -30_000_000..30_000_000), int("spawn_y", -2048..2048),
            int("spawn_z", -30_000_000..30_000_000), int("maximum_horizontal_distance", 0..128))
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val p = parse(parameters)
        return listOf(DriverStep("login", DriverAction("world.entry", suiteMapper.createObjectNode().put("player_uuid", p.player))))
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val p = parse(parameters); require(observations.size == 1)
        val row = observations.single().payload
        require(row.path("uuid").isTextual && row.path("dimension").isTextual && row.path("x").isNumber &&
            row.path("y").isNumber && row.path("z").isNumber && row.path("login_observed").isBoolean &&
            row.path("login_tick").isIntegralNumber && row.path("current_tick").isIntegralNumber &&
            row.path("login_dimension").isTextual && row.path("spawn_x").isIntegralNumber &&
            row.path("spawn_y").isIntegralNumber && row.path("spawn_z").isIntegralNumber &&
            row.path("current_chunk_loaded").isBoolean && row.path("native_login_source").isTextual &&
            row.path("first_join_status").isTextual) { "actual login observation incomplete" }
        val dx = row.path("x").asDouble() - p.spawnX; val dz = row.path("z").asDouble() - p.spawnZ
        require(dx.isFinite() && dz.isFinite()) { "nonfinite login coordinates" }
        val valid = row.path("uuid").asText() == p.player && row.path("dimension").asText() == p.dimension &&
            row.path("login_dimension").asText() == p.dimension && row.path("login_observed").asBoolean() &&
            row.path("current_chunk_loaded").asBoolean() && row.path("first_join_status").asText() in setOf("no_prior_playerdata_file_observed", "prior_playerdata_seen") &&
            row.path("native_login_source").asText() == "PlayerEvent.PlayerLoggedInEvent" &&
            row.path("spawn_x").asInt() == p.spawnX && row.path("spawn_y").asInt() == p.spawnY &&
            row.path("spawn_z").asInt() == p.spawnZ && row.path("login_tick").asLong() in 0..row.path("current_tick").asLong() &&
            dx * dx + dz * dz <= p.maximumHorizontalDistance.toDouble() * p.maximumHorizontalDistance &&
            abs(row.path("y").asDouble() - p.spawnY) <= 32.0
        return MeasurementVerdict(if (valid) "passed" else "failed", "real login event landing; never proof of first join or safe native terrain")
    }
}
