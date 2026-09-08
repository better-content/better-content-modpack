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
    private val magicSynthesis = read(
        "kubejs/server_scripts/compat/retained/" +
            "refactor__cross_mod_progression__59_formulaic_synthesis_magic_routes.js",
    )

    @Test
    fun `four font grouts retain origin identity and share the seared exit`() {
        val origins = mapOf(
            "nether" to "minecraft:netherrack",
            "aether" to "aether:holystone",
            "bumblezone" to "the_bumblezone:pollen_puff",
            "ratlantis" to "rats:marbled_cheese_raw",
        )
        origins.forEach { (font, binder) ->
            val grout = "kubejs:${font}_font_grout"
            assertTrue(startup.contains("['${font}_font_grout',"), grout)
            assertTrue(handWorkshop.contains("['$font', '$binder', '$grout']"), grout)
            assertTrue(handWorkshop.contains("font[0] + '_smelting'"))
            assertTrue(handWorkshop.contains("font[0] + '_blasting'"))
        }
        assertTrue(handWorkshop.contains("event.smelting('tconstruct:seared_brick', font[2])"))
        assertTrue(handWorkshop.contains("event.blasting('tconstruct:seared_brick', font[2])"))
        assertTrue(handWorkshop.contains("event.remove({ output: 'tconstruct:grout' })"))
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
        assertTrue(thermalPressure.contains("realistic_ores:rinsed_hotstone"))
        assertTrue(thermalPressure.contains("realistic_ores:rinsed_black_shale"))
        assertTrue(thermalPressure.contains("results: [{ item: 'tconstruct:scorched_brick', count: 2 }]"))
        assertFalse(thermalPressure.contains("foundry/nether_grout"))
    }

    @Test
    fun `obsolete ore sprawl and synthetic diamond bypass stay removed`() {
        assertTrue(acidChemistry.contains("pneumaticcraft:pressure_chamber/coal_to_diamond"))
        assertFalse(magicSynthesis.contains("sanguine_"))
        assertFalse(magicSynthesis.contains("latent_chemlib:sealed_chemical_cell"))
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
