package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class MalumSoulwoodBloodOriginTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))

    @Test
    fun `blighted gunk funds a tier zero Blood Altar Soulwood starter`() {
        val recipePath = root.resolve("kubejs/data/kubejs/recipes/malum/soulwood_growth_from_blighted_gunk.json")
        val recipe = jacksonObjectMapper().readTree(recipePath.toFile())

        assertEquals("bloodmagic:altar", recipe.get("type").asText())
        assertEquals("malum:blighted_gunk", recipe.get("input").get("item").asText())
        assertEquals("malum:soulwood_growth", recipe.get("output").get("item").asText())
        assertEquals(2_000, recipe.get("altarSyphon").asInt())
        assertEquals(0, recipe.get("upgradeLevel").asInt())
        assertEquals(5, recipe.get("consumptionRate").asInt())
        assertEquals(1, recipe.get("drainRate").asInt())

        val siteBootstrap = Files.readString(
            root.resolve("../mod_source/dynamic-trees-malum/src/main/java/com/bettercontent/dynamictreesmalum/BlightLocusBootstrap.java"),
        )
        assertFalse(siteBootstrap.contains("SOULWOOD_GROWTH"), "the site bootstrap must not place a guaranteed starter")
        assertTrue(siteBootstrap.contains("new ActiveBlightEvent()"), "keep Malum's native blight event as the natural route")
    }
}
