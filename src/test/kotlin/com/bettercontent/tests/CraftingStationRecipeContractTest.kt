package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.zip.ZipFile

@Tag("fast")
class CraftingStationRecipeContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val mapper = jacksonObjectMapper()
    private fun pinnedModJar(filename: String): Path =
        Path.of(System.getProperty("user.home"), ".cache/bc/packwiz-downloads/mods", filename)

    @Test
    fun `station exchanges are single item shapeless and remove exactly native alternatives`() {
        val output = BoundedCommand.capture(root, listOf(
            "node", root.resolve("src/test/resources/crafting-station-recipe-contract.cjs").toString(),
            root.resolve("kubejs/server_scripts/progression/83_station_crafting.js").toString(),
        ), Duration.ofSeconds(15))
        val removed = mapper.readTree(output).path("removed").map { it.asText() }.toSet()
        val stationCrafts = mutableSetOf<String>()
        ZipFile(pinnedModJar("TConstruct-1.20.1-3.11.2.166.jar").toFile()).use { jar ->
            jar.entries().asSequence().filter {
                it.name.startsWith("data/tconstruct/recipes/") && it.name.endsWith(".json")
            }.forEach { entry ->
                val recipe = jar.getInputStream(entry).use { mapper.readTree(it) }
                val type = recipe.path("type").asText()
                if (type.startsWith("minecraft:crafting_") || type.startsWith("mantle:crafting_")) {
                    if (recipe.path("result").path("item").asText() == "tconstruct:crafting_station") {
                        stationCrafts += "tconstruct:" + entry.name.removePrefix("data/tconstruct/recipes/").removeSuffix(".json")
                    }
                    // Fail if a new upstream reverse conversion needs conflict handling.
                    if (recipe.path("result").path("item").asText() == "minecraft:crafting_table") {
                        assertFalse(recipe.toString().contains("tconstruct:crafting_station"), entry.name)
                    }
                }
            }
            // The armor modifier consumes a station but is not a crafting-table recipe cut.
            assertTrue(jar.getEntry("data/tconstruct/recipes/tools/modifiers/ability/crafting_table.json") != null)
        }
        assertEquals(setOf(
            "tconstruct:tables/crafting_station",
            "tconstruct:tables/crafting_station_from_logs",
            "tconstruct:tables/crafting_station_from_tables",
        ), stationCrafts)
        assertEquals(stationCrafts, removed)
    }

    @Test
    fun `downstream Quark crafter still consumes a vanilla table`() {
        val precision = Files.readString(root.resolve("kubejs/server_scripts/progression/30_precision_factory.js"))
        assertTrue(precision.contains("I: '#forge:ingots/iron', C: 'minecraft:crafting_table'"))
        assertTrue(precision.contains(".id('kubejs:precision_factory/quark_crafter')"))
    }
}
