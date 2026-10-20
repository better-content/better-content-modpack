package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class MatterMetallurgyContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
    private val startup = read("kubejs/startup_scripts/progression/20_transition_items.js")
    private val handWorkshop = read("kubejs/server_scripts/progression/10_hand_workshop.js")
    private val thermalPressure = read("kubejs/server_scripts/progression/40_thermal_pressure.js")
    private val acidChemistry = read("kubejs/server_scripts/progression/45_acid_chemistry.js")
    private val oreSifting = read("kubejs/server_scripts/processing/ore_sifting_and_spouting.js")
    private val magicSynthesis = read(
        "kubejs/server_scripts/compat/retained/" +
            "refactor__cross_mod_progression__59_formulaic_synthesis_magic_routes.js",
    )

    @Test
    fun `refractory geology replaces Font entry while legacy identities remain`() {
        // Recipe execution and all pair/form combinations are checked by the
        // bounded CategoryEra fixture; these assertions keep registration and
        // teaching authority connected, rather than weakening the old gate.
        assertTrue(startup.contains("['refractory_compound', 'Refractory Compound']"))
        listOf("nether", "aether", "bumblezone").forEach { font ->
            assertTrue(startup.contains("['${font}_font_grout',"), "legacy identity $font")
        }
        assertTrue(handWorkshop.contains("BC_REFRACTORY_FAMILIES.slice(index + 1)"))
        listOf("gabbro", "slate", "phyllite", "quartzite", "schist").forEach { family ->
            assertTrue(handWorkshop.contains("'unearthed:$family'"), family)
        }
        assertTrue(handWorkshop.contains("event.smelting('tconstruct:seared_brick', 'kubejs:refractory_compound')"))
        assertTrue(handWorkshop.contains("event.blasting('tconstruct:seared_brick', 'kubejs:refractory_compound')"))
        assertFalse(handWorkshop.contains("font[2]"))
        assertTrue(handWorkshop.contains("event.remove({ output: 'tconstruct:grout' })"))
        assertTrue(handWorkshop.contains("event.remove({ input: 'tconstruct:grout' })"))
        assertTrue(handWorkshop.contains("event.remove({ input: 'kubejs:' + origin + '_font_grout' })"))
    }

    @Test
    fun `font grout inventory models use distinct dimension textures`() {
        listOf("aether", "nether", "bumblezone").forEach { font ->
            val model = root.resolve("kubejs/assets/kubejs/models/item/${font}_font_grout.json")
            assertTrue(Files.isRegularFile(model), "missing inventory model for $font Font grout")
            val contents = Files.readString(model)
            assertTrue(contents.contains("\"parent\": \"minecraft:item/generated\""), font)
            assertTrue(contents.contains("\"layer0\": \"kubejs:item/${font}_font_grout\""), font)
            assertTrue(Files.isRegularFile(root.resolve("kubejs/assets/kubejs/textures/item/${font}_font_grout.png")), font)
        }
    }

    @Test
    fun `andesite alloy keeps automation and exact melter scale casts`() {
        assertTrue(handWorkshop.contains("type: 'tconstruct:alloy'"))
        assertTrue(handWorkshop.contains("fluid: 'kubejs:molten_andesite_alloy', amount: 180"))
        assertTrue(handWorkshop.contains("type: 'tconstruct:casting_table'"))

        assertTrue(handWorkshop.contains("['iron', 'forge:molten_iron', 20]"))
        assertTrue(handWorkshop.contains("['zinc', 'forge:molten_zinc', 16]"))
        assertTrue(handWorkshop.contains("type: 'tconstruct:casting_basin'"))
        assertTrue(handWorkshop.contains("cast: { item: 'minecraft:andesite' }"))
        assertTrue(handWorkshop.contains("cast_consumed: true"))
        assertTrue(handWorkshop.contains("fluid: { tag: route[1], amount: 10 }"))
        assertTrue(handWorkshop.contains("result: 'create:andesite_alloy'"))
        assertTrue(handWorkshop.contains("andesite_alloy_' + route[0] + '_melter"))
    }

    @Test
    fun `foundry crosses processed geology without native nether grout`() {
        assertTrue(thermalPressure.contains("event.remove({ output: 'tconstruct:nether_grout' })"))
        assertTrue(thermalPressure.contains("event.remove({ input: 'tconstruct:nether_grout' })"))
        assertTrue(thermalPressure.contains("better_ore_geology:rinsed_hotstone"))
        assertTrue(thermalPressure.contains("better_ore_geology:rinsed_black_shale"))
        assertTrue(thermalPressure.contains("{ item: 'minecraft:basalt' }"))
        assertFalse(thermalPressure.contains("minecraft:gravel"))
        assertTrue(thermalPressure.contains("results: [{ item: 'tconstruct:scorched_brick', count: 2 }]"))
        assertFalse(thermalPressure.contains("foundry/nether_grout"))
    }

    @Test
    fun `outputless sugar cube washing is not migrated into a spout recipe`() {
        assertTrue(oreSifting.contains("input === 'supplementaries:sugar_cube'"))
    }

    @Test
    fun `raw metal routes require a populated tag before recipe creation`() {
        assertTrue(oreSifting.contains("function tagHasItems(tag)"))
        assertTrue(oreSifting.contains("Ingredient.of('#' + tag).itemIds"))
        assertTrue(oreSifting.contains("if (!tagHasItems(rawTag))"))
        assertTrue(oreSifting.indexOf("if (!tagHasItems(rawTag))") < oreSifting.indexOf("var raw = { tag: rawTag }"))
    }

    @Test
    fun `obsolete ore sprawl and synthetic diamond bypass stay removed`() {
        assertTrue(acidChemistry.contains("pneumaticcraft:pressure_chamber/coal_to_diamond"))
        assertFalse(magicSynthesis.contains("sanguine_"))
        assertFalse(magicSynthesis.contains("better_chemlib_hazards:sealed_chemical_cell"))
        assertFalse(magicSynthesis.contains("grinding_ball"))
        assertFalse(Files.exists(root.resolve("kubejs/data/bloodmagic/tags/items/arc/cuttingfluid.json")))

        listOf("acetic", "hydrochloric", "nitric", "phosphoric", "sulfuric").forEach { acid ->
            assertFalse(Files.exists(root.resolve(
                "kubejs/assets/kubejs/models/item/sanguine_${acid}_cutting_fluid.json",
            )))
        }

        val processingDoc = read("docs/realistic_ore_processing.md")
        assertTrue(processingDoc.contains("no diamond ore generates"))
        assertTrue(processingDoc.contains("## Four salient routes"))
        assertFalse(processingDoc.contains("## Grinding media"))
    }

    private fun read(relative: String): String = Files.readString(root.resolve(relative))
}
