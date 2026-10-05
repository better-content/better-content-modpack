package com.bettercontent.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path

data class GeometrySample(val dimension: String, val chunks: Int, val blocks: Map<String, Long>)

object GeometrySmokePlan {
    private val mapper = jacksonObjectMapper()
    private val utilityDimensions = setOf(
        "ae2:spatial_storage", "bloodmagic:dungeon",
        "creatingspace:earth_orbit", "creatingspace:mars_orbit", "creatingspace:moon_orbit",
    )
    private val ground = setOf(
        "minecraft:stone", "minecraft:deepslate", "minecraft:dirt", "minecraft:grass_block",
        "minecraft:sand", "minecraft:gravel",
    )
    private val nether = setOf(
        "minecraft:netherrack", "minecraft:basalt", "minecraft:blackstone",
        "minecraft:crimson_nylium", "minecraft:warped_nylium", "minecraft:soul_sand", "minecraft:soul_soil",
    )
    private val venus = setOf("minecraft:blackstone", "minecraft:basalt", "minecraft:smooth_basalt")
    private val terrainProfiles: Map<String, (String) -> Boolean> = mapOf(
        "minecraft:overworld" to { id -> id in ground || id.startsWith("unearthed:") },
        "lostcities:lostcity" to { id -> id in ground || id.startsWith("unearthed:") },
        "minecraft:the_nether" to { id -> id in nether },
        "minecraft:the_end" to { id -> id.startsWith("minecraft:end_stone") || id.startsWith("minecraft:purpur") },
        "aether:the_aether" to { id -> id.startsWith("aether:") },
        "twilightforest:twilight_forest" to { id ->
            id.startsWith("twilightforest:") || id.startsWith("dttwilightforest:") || id.startsWith("dynamictrees:") || id in ground
        },
        "the_bumblezone:the_bumblezone" to { id ->
            id.startsWith("the_bumblezone:") || id == "minecraft:honey_block" || id == "minecraft:honeycomb_block"
        },
        "creatingspace:the_moon" to { id -> id.startsWith("creatingspace:moon_") },
        "creatingspace:mars" to { id -> id.startsWith("creatingspace:mars_") },
        "creatingspace:venus" to { id -> id in venus },
        "fallout_wastelands_:wastelands" to { id -> id.startsWith("fallout_wastelands_:") },
        "the_deep_void:deep_void" to { id -> id.startsWith("the_deep_void:") || id.startsWith("cataclysm:") || id == "minecraft:deepslate" },
        "the_deep_void:the_pit" to { id -> id.startsWith("the_deep_void:") || id == "minecraft:deepslate" },
    )
    private val air = setOf("minecraft:air", "minecraft:cave_air", "minecraft:void_air")
    private val harnessBlocks = setOf("minecraft:bedrock", "minecraft:obsidian", "minecraft:oxidized_copper", "better_dimension_fonts:return_seal")

    fun isTerrain(dimension: String): Boolean {
        require(dimension in terrainProfiles || dimension in utilityDimensions) { "unclassified loaded dimension: $dimension" }
        return dimension in terrainProfiles
    }

    fun read(path: Path): GeometrySample {
        val node = mapper.readTree(path.toFile())
        require(node.path("schema").asText() == "bc.geometry_probe.v1") { "unexpected geometry probe schema: $path" }
        val blocks = node.path("blocks")
        require(blocks.isObject) { "geometry probe has no block histogram: $path" }
        val counts = blocks.fields().asSequence().associate { it.key to it.value.asLong() }
        require(counts.values.all { it >= 0 }) { "geometry probe has negative block counts: $path" }
        return GeometrySample(node.path("dimension").asText(), node.path("chunk_count").asInt(), counts)
    }

    fun assess(dimension: String, samples: List<GeometrySample>): Map<String, Any> {
        require(isTerrain(dimension)) { "geometry profile requested for utility dimension $dimension" }
        require(samples.isNotEmpty() && samples.all { it.dimension == dimension && it.chunks == 9 }) {
            "incomplete geometry samples for $dimension"
        }
        val histogram = samples.flatMap { it.blocks.entries }.groupingBy { it.key }
            .fold(0L) { sum, entry -> sum + entry.value }
        val nonAir = histogram.filterKeys { it !in air && it !in harnessBlocks }.values.sum()
        val native = histogram.filterKeys(terrainProfiles.getValue(dimension)).values.sum()
        // Terrain dimensions produced at least 1,532 native blocks in
        // retained Debug evidence, so a stronger floor catches mostly empty or
        // wrong-generator worlds without depending on a lucky biome.
        val requiredNative = 256L
        val requiredNonAir = 1024L
        val passed = nonAir >= requiredNonAir && native >= requiredNative
        return mapOf(
            "dimension" to dimension, "samples" to samples.size, "chunks" to samples.sumOf { it.chunks },
            "non_air_blocks" to nonAir, "expected_family_blocks" to native,
            "required_non_air_blocks" to requiredNonAir, "required_expected_family_blocks" to requiredNative,
            "passed" to passed,
        )
    }
}
