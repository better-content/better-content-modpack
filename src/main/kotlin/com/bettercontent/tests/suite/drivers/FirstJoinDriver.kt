package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import kotlin.math.abs

/** First login only: event-captured position, no prior player data, solid ground and two-air clearance. */
internal object FirstJoinDriver : CaseDriver {
    override val caseId = "world.initial-spawn"
    override val assertionIds = setOf("world.initial-spawn.first-login", "world.initial-spawn.safe-terrain", "world.initial-spawn.chosen-position")
    private data class Target(val player: String, val dimension: String, val x: Int, val y: Int, val z: Int,
        val horizontalLimit: Int, val verticalLimit: Int, val biomes: Set<String>, val ground: Set<String>)
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private fun parse(node: JsonNode): Target {
        require(node.isObject && node.fieldNames().asSequence().toSet() == setOf("player_uuid", "dimension",
            "spawn_x", "spawn_y", "spawn_z", "maximum_horizontal_distance", "maximum_vertical_distance",
            "allowed_biomes", "allowed_ground_blocks")) { "first-join target fields unknown/missing" }
        fun text(key: String): String { val v = node.get(key); require(v != null && v.isTextual); return v.asText() }
        fun int(key: String, range: IntRange): Int {
            val v = node.get(key); require(v != null && v.isIntegralNumber && v.canConvertToInt() && v.asInt() in range) { "invalid $key" }
            return v.asInt()
        }
        fun ids(key: String): Set<String> {
            val v = node.get(key); require(v != null && v.isArray && v.size() in 1..64) { "invalid $key" }
            val rows = v.map { require(it.isTextual && resource.matches(it.asText())); it.asText() }
            require(rows.size == rows.distinct().size) { "duplicate $key" }
            return rows.toSet()
        }
        val uuid = text("player_uuid"); val dimension = text("dimension")
        require(runCatching { UUID.fromString(uuid).toString() }.getOrNull() == uuid && resource.matches(dimension))
        return Target(uuid, dimension, int("spawn_x", -30_000_000..30_000_000), int("spawn_y", -2048..2048),
            int("spawn_z", -30_000_000..30_000_000), int("maximum_horizontal_distance", 0..128),
            int("maximum_vertical_distance", 0..32), ids("allowed_biomes"), ids("allowed_ground_blocks"))
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val t = parse(parameters)
        return listOf(DriverStep("first-login", DriverAction("world.entry", suiteMapper.createObjectNode().put("player_uuid", t.player))))
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val t = parse(parameters); require(observations.size == 1)
        val row = observations.single().payload
        val requiredText = listOf("uuid", "dimension", "login_dimension", "first_join_status", "native_login_source", "login_biome", "login_ground_block")
        val requiredNumbers = listOf("login_tick", "current_tick", "login_x", "login_y", "login_z", "spawn_x", "spawn_y", "spawn_z", "x", "y", "z")
        val requiredBooleans = listOf("login_observed", "playerdata_preexists_at_login", "login_feet_air", "login_head_air", "login_ground_solid", "current_chunk_loaded")
        require(requiredText.all { row.path(it).isTextual } && requiredNumbers.all { row.path(it).isNumber } &&
            requiredBooleans.all { row.path(it).isBoolean }) { "actual first login/terrain proof fields missing" }
        val dx = row.path("login_x").asDouble() - t.x; val dz = row.path("login_z").asDouble() - t.z
        require(dx.isFinite() && dz.isFinite()) { "nonfinite native login" }
        val good = row.path("uuid").asText() == t.player && row.path("dimension").asText() == t.dimension &&
            row.path("login_dimension").asText() == t.dimension && row.path("login_observed").asBoolean() &&
            !row.path("playerdata_preexists_at_login").asBoolean() &&
            row.path("first_join_status").asText() == "no_prior_playerdata_file_observed" &&
            row.path("native_login_source").asText() == "PlayerEvent.PlayerLoggedInEvent" &&
            row.path("login_tick").asLong() in 0..row.path("current_tick").asLong() &&
            row.path("spawn_x").asInt() == t.x && row.path("spawn_y").asInt() == t.y && row.path("spawn_z").asInt() == t.z &&
            dx * dx + dz * dz <= t.horizontalLimit.toDouble() * t.horizontalLimit &&
            abs(row.path("login_y").asDouble() - t.y) <= t.verticalLimit &&
            row.path("login_feet_air").asBoolean() && row.path("login_head_air").asBoolean() &&
            row.path("login_ground_solid").asBoolean() && row.path("login_biome").asText() in t.biomes &&
            row.path("login_ground_block").asText() in t.ground && row.path("current_chunk_loaded").asBoolean() &&
            abs(row.path("x").asDouble() - row.path("login_x").asDouble()) <= 2.0 &&
            abs(row.path("z").asDouble() - row.path("login_z").asDouble()) <= 2.0
        return MeasurementVerdict(if (good) "passed" else "failed",
            "event-captured first login, chosen spawn and safe terrain in bound candidate; no teleport substitute")
    }
}
