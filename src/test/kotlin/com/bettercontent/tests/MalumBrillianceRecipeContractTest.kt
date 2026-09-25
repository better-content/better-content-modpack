package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class MalumBrillianceRecipeContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val script = Files.readString(root.resolve(
        "kubejs/server_scripts/progression/50_malum_soulstone_bootstrap.js",
    ))
    private val annotation = Files.readString(root.resolve("kubejs/config/hover_annotations.json"))

    @Test
    fun `brilliance emerges from heated Soulstone and Hex Ash mixing without XP inputs or outputs`() {
        val routeStart = script.indexOf("// MALUM-07:")
        assertTrue(routeStart >= 0, "MALUM-07 route is present")
        val route = script.substring(routeStart)
        val ingredientsStart = route.indexOf("ingredients: [")
        val resultsStart = route.indexOf("results: [", ingredientsStart)
        assertTrue(ingredientsStart >= 0 && resultsStart > ingredientsStart)
        val ingredientIds = Regex("item: '([^']+)'")
            .findAll(route.substring(ingredientsStart, resultsStart))
            .map { it.groupValues[1] }
            .toList()

        assertTrue(route.contains("type: 'create:mixing'"))
        assertTrue(route.contains("heatRequirement: 'heated'"))
        assertEquals(
            listOf("malum:crushed_soulstone", "malum:crushed_soulstone", "malum:hex_ash"),
            ingredientIds,
        )
        assertTrue(route.contains("results: [{ item: 'malum:crushed_brilliance', count: 1 }]"))
        assertFalse(route.contains("experience"), "the authored conversion must not create an XP rebate loop")
        assertTrue(annotation.contains("Heat crushed Soulstone with Malum Hex Ash in Create mixing"))
    }
}
