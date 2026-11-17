package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode

/** Read-only loaded world sample: does not certify initial spawn, generated terrain or biome availability. */
internal object WorldTemperateProbeDriver : CaseDriver {
    override val caseId = "control.world.temperate-sample"
    private data class Target(val dimension: String, val x: Int, val y: Int, val z: Int, val radius: Int,
        val biome: String, val block: String, val temperatureMin: Double, val temperatureMax: Double,
        val downfallMin: Double, val downfallMax: Double, val precipitation: String)
    private fun parse(node: JsonNode): Target {
        val fields = setOf("dimension", "x", "y", "z", "radius", "expected_biome", "expected_block",
            "temperature_min", "temperature_max", "downfall_min", "downfall_max", "precipitation")
        require(node.isObject && node.fieldNames().asSequence().toSet() == fields) { "world sample requires exact typed target" }
        fun text(key: String): String {
            val n = node.get(key); require(n != null && n.isTextual) { "invalid $key" }; return n.asText()
        }
        fun number(key: String, range: IntRange): Int {
            val n = node.get(key)
            require(n != null && n.isIntegralNumber && n.canConvertToInt() && n.asInt() in range) { "invalid $key" }
            return n.asInt()
        }
        fun decimal(key: String, min: Double, max: Double): Double {
            val n = node.get(key)
            require(n != null && n.isNumber && n.asDouble().isFinite() && n.asDouble() in min..max) { "invalid $key" }
            return n.asDouble()
        }
        val dimension = text("dimension"); val biome = text("expected_biome"); val block = text("expected_block")
        require(listOf(dimension, biome, block).all { Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(it) }) { "invalid registry ID" }
        val temperatureMin = decimal("temperature_min", -10.0, 10.0); val temperatureMax = decimal("temperature_max", -10.0, 10.0)
        val downfallMin = decimal("downfall_min", 0.0, 1.0); val downfallMax = decimal("downfall_max", 0.0, 1.0)
        require(temperatureMin <= temperatureMax && downfallMin <= downfallMax) { "inverted climate bounds" }
        val precipitation = text("precipitation")
        require(precipitation in setOf("NONE", "RAIN", "SNOW")) { "unreviewed precipitation" }
        return Target(dimension, number("x", -30_000_000..30_000_000), number("y", -2048..2048),
            number("z", -30_000_000..30_000_000), number("radius", 0..4), biome, block,
            temperatureMin, temperatureMax, downfallMin, downfallMax, precipitation)
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val p = parse(parameters)
        return listOf(DriverStep("sample", DriverAction("world.sample", suiteMapper.createObjectNode()
            .put("dimension", p.dimension).put("x", p.x).put("y", p.y).put("z", p.z).put("radius", p.radius))))
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val p = parse(parameters); require(observations.size == 1) { "one bounded loaded sample required" }
        val row = observations.single().payload
        require(row.path("dimension").isTextual && row.path("x").isIntegralNumber && row.path("y").isIntegralNumber &&
            row.path("z").isIntegralNumber && row.path("center_biome").isTextual &&
            row.path("base_temperature").isNumber && row.path("downfall").isNumber && row.path("precipitation").isTextual &&
            row.path("unloaded_positions").isIntegralNumber && row.path("loaded_samples").isArray) { "native world sample incomplete" }
        val temperature = row.path("base_temperature").asDouble(); val downfall = row.path("downfall").asDouble()
        require(temperature.isFinite() && downfall.isFinite()) { "nonfinite native climate" }
        val center = row.path("loaded_samples").filter {
            it.path("x").asInt() == p.x && it.path("y").asInt() == p.y && it.path("z").asInt() == p.z
        }
        require(center.size == 1 && center.single().path("block_id").isTextual) { "center block not uniquely sampled" }
        val matching = row.path("dimension").asText() == p.dimension && row.path("x").asInt() == p.x &&
            row.path("y").asInt() == p.y && row.path("z").asInt() == p.z && row.path("center_biome").asText() == p.biome &&
            center.single().path("block_id").asText() == p.block &&
            temperature in p.temperatureMin..p.temperatureMax && downfall in p.downfallMin..p.downfallMax &&
            row.path("precipitation").asText() == p.precipitation && row.path("unloaded_positions").asInt() == 0 &&
            row.path("loaded_samples").size() == (2 * p.radius + 1) * (2 * p.radius + 1) * 3
        return MeasurementVerdict(if (matching) "passed" else "failed", "read-only loaded biome/climate sample; not generated spawn or natural acquisition",
            mapOf("observed_biome" to row.path("center_biome").asText(), "temperature" to temperature, "downfall" to downfall))
    }
}
