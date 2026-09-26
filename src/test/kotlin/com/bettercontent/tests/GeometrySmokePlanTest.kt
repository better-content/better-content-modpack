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
        assertTrue(GeometrySmokePlan.isTerrain("rats:ratlantis"))
        assertFalse(GeometrySmokePlan.isTerrain("creatingspace:earth_orbit"))
        assertFalse(GeometrySmokePlan.isTerrain("ae2:spatial_storage"))
        assertThrows(IllegalArgumentException::class.java) { GeometrySmokePlan.isTerrain("unknown:new_dimension") }
    }

    @Test
    fun rejectsAnEmptyWorldAndAnArtificialArrivalPlatform() {
        val dimension = "the_bumblezone:the_bumblezone"
        val empty = GeometrySample(dimension, 9, mapOf("minecraft:air" to 900_000))
        val platform = GeometrySample(dimension, 9, mapOf("minecraft:oxidized_copper" to 500,
            "minecraft:air" to 899_500, "dimension_drink:return_seal" to 1))
        assertEquals(false, GeometrySmokePlan.assess(dimension, listOf(empty))["passed"])
        assertEquals(false, GeometrySmokePlan.assess(dimension, listOf(platform))["passed"])
    }

    @Test
    fun acceptsExpectedNativeBlocksAcrossMultipleSamples() {
        val dimension = "aether:the_aether"
        val samples = listOf(
            GeometrySample(dimension, 9, mapOf("minecraft:air" to 900_000)),
            GeometrySample(dimension, 9, mapOf("aether:holystone" to 20, "minecraft:air" to 899_980)),
            GeometrySample(dimension, 9, mapOf("aether:cold_aercloud" to 50, "minecraft:air" to 899_950)),
        )
        val result = GeometrySmokePlan.assess(dimension, samples)
        assertEquals(70L, result["non_air_blocks"])
        assertEquals(70L, result["expected_family_blocks"])
        assertEquals(true, result["passed"])
    }
}
