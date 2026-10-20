package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class GeometrySmokePlanTest {
    @Test
    fun classifiesTheLoadedTerrainAndUtilityDimensions() {
        assertTrue(GeometrySmokePlan.isTerrain("the_bumblezone:the_bumblezone"))
        assertTrue(GeometrySmokePlan.isTerrain("aether:the_aether"))
        assertFalse(GeometrySmokePlan.isTerrain("creatingspace:earth_orbit"))
        assertFalse(GeometrySmokePlan.isTerrain("ae2:spatial_storage"))
        assertThrows(IllegalArgumentException::class.java) { GeometrySmokePlan.isTerrain("unknown:new_dimension") }
    }

    @Test
    fun rejectsAnEmptyWorldAndAnArtificialArrivalPlatform() {
        val dimension = "the_bumblezone:the_bumblezone"
        val empty = GeometrySample(dimension, 9, mapOf("minecraft:air" to 900_000))
        val platform = GeometrySample(dimension, 9, mapOf("minecraft:oxidized_copper" to 500,
            "minecraft:air" to 899_500, "better_dimension_fonts:return_seal" to 1))
        assertEquals(false, GeometrySmokePlan.assess(dimension, listOf(empty))["passed"])
        assertEquals(false, GeometrySmokePlan.assess(dimension, listOf(platform))["passed"])
    }

    @Test
    fun exactThresholdsAndAdjacentFailures() {
        val dimension = "aether:the_aether"
        fun sample(nonAir: Long, native: Long) = GeometrySample(dimension, 9,
            mapOf("aether:holystone" to native, "minecraft:dirt" to nonAir - native))
        assertEquals(true, GeometrySmokePlan.assess(dimension, listOf(sample(1024, 256)))["passed"])
        assertEquals(false, GeometrySmokePlan.assess(dimension, listOf(sample(1023, 256)))["passed"])
        assertEquals(false, GeometrySmokePlan.assess(dimension, listOf(sample(1024, 255)))["passed"])
        val combined = GeometrySmokePlan.assess(dimension, listOf(sample(512, 128), sample(512, 128)))
        assertEquals(true, combined["passed"])
        assertEquals(GeometrySmokePlan.REQUIRED_NON_AIR_BLOCKS, combined["required_non_air_blocks"])
        assertEquals(GeometrySmokePlan.REQUIRED_EXPECTED_FAMILY_BLOCKS, combined["required_expected_family_blocks"])
    }

    @Test
    fun airHarnessBlocksAndIncompleteChunksDoNotSatisfyThresholds() {
        val dimension = "aether:the_aether"
        val artificial = GeometrySample(dimension, 9, mapOf("aether:holystone" to 256,
            "minecraft:air" to 100_000, "minecraft:cave_air" to 100_000, "minecraft:void_air" to 100_000,
            "minecraft:bedrock" to 100_000, "minecraft:obsidian" to 100_000,
            "minecraft:oxidized_copper" to 100_000, "better_dimension_fonts:return_seal" to 100_000))
        val result = GeometrySmokePlan.assess(dimension, listOf(artificial))
        assertEquals(256L, result["non_air_blocks"])
        assertEquals(false, result["passed"])
        assertThrows(IllegalArgumentException::class.java) {
            GeometrySmokePlan.assess(dimension, listOf(artificial.copy(chunks = 8)))
        }
    }

    @Test
    fun acceptsExpectedNativeBlocksAcrossMultipleSamples() {
        val dimension = "aether:the_aether"
        val samples = listOf(
            GeometrySample(dimension, 9, mapOf("minecraft:air" to 900_000)),
            GeometrySample(dimension, 9, mapOf("aether:holystone" to 500, "minecraft:air" to 899_500)),
            GeometrySample(dimension, 9, mapOf("aether:cold_aercloud" to 600, "minecraft:air" to 899_400)),
        )
        val result = GeometrySmokePlan.assess(dimension, samples)
        assertEquals(1100L, result["non_air_blocks"])
        assertEquals(1100L, result["expected_family_blocks"])
        assertEquals(true, result["passed"])
    }
}
