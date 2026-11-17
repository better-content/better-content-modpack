package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import kotlin.math.abs

/** Fresh first-join actual biome + loaded native climate at landing ground, not registry-only discovery. */
internal object TemperateFirstJoinDriver : CaseDriver {
    override val caseId = "world.temperate"
    override val assertionIds = setOf("world.temperate.native-biome", "world.temperate.climate", "world.temperate.first-join-site")
    private data class Target(val player: String, val dimension: String, val x: Int, val groundY: Int, val z: Int,
        val biome: String, val ground: String, val tempMin: Double, val tempMax: Double, val downfallMin: Double,
        val downfallMax: Double, val precipitation: String)
    private fun parse(node: JsonNode): Target {
        require(node.isObject && node.fieldNames().asSequence().toSet() == setOf("player_uuid", "dimension",
            "ground_x", "ground_y", "ground_z", "biome", "ground_block", "temperature_min", "temperature_max",
            "downfall_min", "downfall_max", "precipitation")) { "fresh temperate site fields unknown/missing" }
        fun text(key: String): String { val v = node.path(key); require(v.isTextual); return v.asText() }
        fun number(key: String, range: IntRange): Int { val v = node.path(key); require(v.isIntegralNumber && v.canConvertToInt() && v.asInt() in range); return v.asInt() }
        fun decimal(key: String, low: Double, high: Double): Double {
            val v = node.path(key); require(v.isNumber && v.asDouble().isFinite() && v.asDouble() in low..high); return v.asDouble()
        }
        val uuid = text("player_uuid"); require(runCatching { UUID.fromString(uuid).toString() }.getOrNull() == uuid)
        val dimension = text("dimension"); val biome = text("biome"); val ground = text("ground_block")
        require(listOf(dimension, biome, ground).all { Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(it) })
        val tempMin = decimal("temperature_min", -10.0, 10.0); val tempMax = decimal("temperature_max", -10.0, 10.0)
        val downfallMin = decimal("downfall_min", 0.0, 1.0); val downfallMax = decimal("downfall_max", 0.0, 1.0)
        require(tempMin <= tempMax && downfallMin <= downfallMax)
        val precipitation = text("precipitation"); require(precipitation in setOf("RAIN", "SNOW", "NONE"))
        return Target(uuid, dimension, number("ground_x", -30_000_000..30_000_000), number("ground_y", -2048..2048),
            number("ground_z", -30_000_000..30_000_000), biome, ground, tempMin, tempMax, downfallMin, downfallMax, precipitation)
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val t = parse(parameters)
        return listOf(DriverStep("entry", DriverAction("world.entry", suiteMapper.createObjectNode().put("player_uuid", t.player))),
            DriverStep("climate", DriverAction("world.sample", suiteMapper.createObjectNode().put("dimension", t.dimension)
                .put("x", t.x).put("y", t.groundY).put("z", t.z).put("radius", 0))))
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val t = parse(parameters); require(observations.size == 2)
        val login = observations[0].payload; val world = observations[1].payload
        require(listOf("uuid", "login_dimension", "login_biome", "login_ground_block", "first_join_status").all { login.path(it).isTextual } &&
            listOf("login_x", "login_y", "login_z").all { login.path(it).isNumber } &&
            listOf("login_observed", "playerdata_preexists_at_login", "login_ground_solid", "login_feet_air", "login_head_air").all { login.path(it).isBoolean } &&
            world.path("dimension").isTextual && world.path("center_biome").isTextual &&
            world.path("base_temperature").isNumber && world.path("downfall").isNumber &&
            world.path("precipitation").isTextual && world.path("loaded_samples").isArray &&
            world.path("unloaded_positions").isIntegralNumber) { "native firstjoin/biome/climate source absent" }
        val center = world.path("loaded_samples").filter { it.path("x").asInt() == t.x && it.path("y").asInt() == t.groundY && it.path("z").asInt() == t.z }
        require(center.size == 1 && center.single().path("block_id").isTextual) { "ground block sample missing" }
        val temp = world.path("base_temperature").asDouble(); val downfall = world.path("downfall").asDouble()
        require(temp.isFinite() && downfall.isFinite()) { "nonfinite native climate" }
        val valid = login.path("uuid").asText() == t.player && login.path("login_dimension").asText() == t.dimension &&
            login.path("login_observed").asBoolean() && !login.path("playerdata_preexists_at_login").asBoolean() &&
            login.path("first_join_status").asText() == "no_prior_playerdata_file_observed" &&
            login.path("login_biome").asText() == t.biome && login.path("login_ground_block").asText() == t.ground &&
            login.path("login_ground_solid").asBoolean() && login.path("login_feet_air").asBoolean() && login.path("login_head_air").asBoolean() &&
            abs(login.path("login_x").asDouble() - (t.x + .5)) <= 1.0 &&
            abs(login.path("login_z").asDouble() - (t.z + .5)) <= 1.0 &&
            abs(login.path("login_y").asDouble() - (t.groundY + 1.0)) <= 1.0 &&
            world.path("dimension").asText() == t.dimension && world.path("x").asInt(Int.MIN_VALUE) == t.x &&
            world.path("y").asInt(Int.MIN_VALUE) == t.groundY && world.path("z").asInt(Int.MIN_VALUE) == t.z &&
            world.path("center_biome").asText() == t.biome && center.single().path("block_id").asText() == t.ground &&
            world.path("unloaded_positions").asInt() == 0 && world.path("loaded_samples").size() == 3 &&
            temp in t.tempMin..t.tempMax && downfall in t.downfallMin..t.downfallMax &&
            world.path("precipitation").asText() == t.precipitation
        return MeasurementVerdict(if (valid) "passed" else "failed", "fresh first-join landing biome and native loaded climate agree; not universal worldgen prevalence")
    }
}
