package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class OreLootRouteContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))

    @Test
    fun `raw metal loot is redirected into the authored processing route`() {
        val policy = Files.readString(root.resolve("kubejs/server_scripts/policy/ore_raw_material_loot.js"))
        val route = Regex("\\['([^']+)',\\s*'([^']+)'\\]")
            .findAll(policy)
            .associate { it.groupValues[1] to it.groupValues[2] }

        assertEquals(
            mapOf(
                "minecraft:raw_iron" to "realistic_ores:small_ore_chunk_ironstone",
                "minecraft:raw_copper" to "realistic_ores:small_ore_chunk_copper_bloom",
                "minecraft:raw_gold" to "realistic_ores:gold_concentrate",
                "minecraft:raw_iron_block" to "minecraft:iron_block",
                "minecraft:raw_copper_block" to "minecraft:copper_block",
                "minecraft:raw_gold_block" to "minecraft:gold_block",
            ),
            route,
        )
        assertTrue(policy.contains("event.addLootTableModifier(/^(?!minecraft:empty$).*$/)"))
        assertTrue(policy.contains("rawMetalLoot.replaceLoot(route[0], route[1], true)"))
        assertTrue(policy.contains("if (!Platform.isLoaded('realistic_ores')) return"))
    }

    @Test
    fun `raw metal recipe results are redirected without changing their feed inputs`() {
        val policy = Files.readString(root.resolve("kubejs/server_scripts/policy/ore_raw_material_recipes.js"))
        listOf(
            "quark:building/crafting/raw_iron_bricks_revert" to
                "9x realistic_ores:small_ore_chunk_ironstone",
            "quark:building/crafting/raw_copper_bricks_revert" to
                "9x realistic_ores:small_ore_chunk_copper_bloom",
            "quark:building/crafting/raw_gold_bricks_revert" to
                "9x realistic_ores:gold_concentrate",
            "goety:psgold" to "2x realistic_ores:gold_concentrate",
        ).forEach { (source, output) ->
            assertTrue(policy.contains("source: '$source'"), "missing source recipe $source")
            assertTrue(policy.contains("output: '$output'"), "missing replacement output for $source")
        }
        assertTrue(policy.contains("event.remove({ id: route.source })"))
        assertTrue(policy.contains("event.shapeless(route.output, route.ingredients)"))
        assertTrue(policy.contains("ingredients: ['#forge:raw_materials/gold', 'goety:philosophers_stone']"))
        assertTrue(policy.contains("ingredients: ['quark:raw_iron_bricks']"))
        assertTrue(policy.contains("ingredients: ['quark:raw_copper_bricks']"))
        assertTrue(policy.contains("ingredients: ['quark:raw_gold_bricks']"))
    }
}
