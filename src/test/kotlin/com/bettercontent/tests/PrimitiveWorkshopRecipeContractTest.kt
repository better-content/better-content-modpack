package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class PrimitiveWorkshopRecipeContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val primitive = Files.readString(root.resolve(
        "kubejs/server_scripts/progression/00_primitive_workshop.js",
    ))
    private val precision = Files.readString(root.resolve(
        "kubejs/server_scripts/progression/30_precision_factory.js",
    ))

    @Test
    fun `passive handling uses canvas and simple fittings before plates`() {
        assertTrue(primitive.contains("event.remove({ id: 'create:crafting/kinetics/chute' })"))
        assertTrue(primitive.contains("event.shaped('4x create:chute', ['NCN', 'C C', 'NCN']"))
        assertTrue(primitive.contains("N: '#forge:nuggets/iron', C: 'farmersdelight:canvas'"))
        assertFalse(primitive.contains("forge:plates"))
    }

    @Test
    fun `cooking pot no longer consumes a removed conventional tool`() {
        assertTrue(primitive.contains("event.remove({ id: 'farmersdelight:cooking_pot' })"))
        assertTrue(primitive.contains("event.shaped('farmersdelight:cooking_pot', ['bRb', 'iWi', 'iii']"))
        assertTrue(primitive.contains("R: '#forge:rods/wooden'"))
        assertFalse(primitive.contains("'minecraft:wooden_shovel'"))
    }

    @Test
    fun `generic rustic utilities have additive straw canvas and coal routes`() {
        val expectedIds = listOf(
            "sack_from_canvas", "awning_from_canvas", "doormat_from_canvas",
            "fire_pit_from_coal", "feeding_trough_from_straw", "quark_thatch_from_straw",
            "dawn_thatch_from_straw", "dawn_wattle_and_daub_from_straw",
            "dawn_white_wattle_and_daub_from_straw",
        )
        expectedIds.forEach { id ->
            assertTrue(primitive.contains("kubejs:primitive_workshop/$id"), id)
        }
        assertTrue(primitive.contains("F: '#minecraft:coals'"))
        assertTrue(primitive.contains("S: 'farmersdelight:straw'"))
        assertFalse(primitive.contains("event.remove({ id: 'supplementaries:sack' })"))
        assertFalse(primitive.contains("event.remove({ id: 'supplementaries:awnings/awning' })"))
        assertFalse(primitive.contains("event.remove({ id: 'quark:building/crafting/thatch' })"))
    }

    @Test
    fun `automatic crafting derives from the Precision Factory root`() {
        assertTrue(precision.contains("event.remove({ id: 'quark:automation/crafting/crafter' })"))
        assertTrue(precision.contains("event.shaped('quark:crafter', ['III', 'ICI', 'RMR']"))
        assertTrue(precision.contains("M: 'create:mechanical_crafter'"))
        assertTrue(precision.contains("kubejs:precision_factory/quark_crafter"))
    }
}
