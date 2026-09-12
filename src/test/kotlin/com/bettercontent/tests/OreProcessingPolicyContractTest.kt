package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class OreProcessingPolicyContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()

    @Test
    fun `silver crushed feed uses the Create item and canonical concentrate yield`() {
        val script = read("kubejs/server_scripts/processing/ore_sifting_and_spouting.js")
        assertFalse(script.contains("iceandfire:crushed_silver_ore"))
        assertTrue(script.contains("'create:crushed_raw_silver': true"))
        assertTrue(script.contains(
            "'create:crushed_raw_silver': { nugget: 'iceandfire:silver_nugget', " +
                "concentrate: 'realistic_ores:silver_concentrate', base: 2, chance: 0.25 }",
        ))
    }

    @Test
    fun `raw blocks enter the same eighteen-concentrate Occultism economy`() {
        val script = read("kubejs/server_scripts/processing/ore_sifting_and_spouting.js")
        assertTrue(script.contains("encoded.indexOf('forge:storage_blocks/raw_' + material)"))
        assertTrue(script.contains("var rawBlockTag = 'forge:storage_blocks/raw_' + material"))
        assertTrue(script.contains("if (tagHasItems(rawBlockTag))"))
        assertTrue(script.contains("result: { item: concentrate.item, count: 18 }"))
        assertTrue(script.contains("ore_magic/occultism_raw_block_"))
    }

    @Test
    fun `furnace normalization covers block results raw bricks and underscore ore tags`() {
        val script = read("kubejs/server_scripts/compat/reviewed/furnace_policy.js")
        assertTrue(script.contains("NUGGETS_ORE_RAW = 4"))
        assertTrue(script.contains("NUGGETS_RAW_BLOCK = 36"))
        assertTrue(script.contains("endsWith(res.path, '_block')"))
        assertTrue(script.contains("raw_' + base + '_bricks"))
        assertTrue(script.contains("base + '_ores'"))
        assertTrue(script.contains("ores_' + base"))
    }

    @Test
    fun `generic EV item ore tags exclude every Realistic Ores hosted family`() {
        val modifier = read(
            "defaultresources/excavated_variants/excavated_variants/modifiers/tag_attachment.json5",
        )
        val runtimePolicy = read("kubejs/server_scripts/policy/realistic_ores_tag_policy.js")
        assertTrue(modifier.contains("'forge:blocks/ores'"))
        assertTrue(modifier.contains("'c:blocks/ores'"))
        assertFalse(modifier.contains("'forge:items/ores'"))
        assertFalse(modifier.contains("'c:items/ores'"))
        assertTrue(runtimePolicy.contains("ServerEvents.tags('item'"))
        assertTrue(runtimePolicy.contains("event.remove('forge:ores', hostedFamily)"))
        assertTrue(runtimePolicy.contains("event.remove('c:ores', hostedFamily)"))
        listOf(
            "black_shale", "brassroot", "coal_measures", "copper_bloom",
            "evaporite_beds", "hotstone", "ironstone", "tin_quartz",
        ).forEach { assertTrue(runtimePolicy.contains(it), "missing hosted family $it") }
    }

    @Test
    fun `only the twelve confirmed broken Occultism outputs are overridden`() {
        val script = read("kubejs/server_scripts/compat/reviewed/occultism_crushing_outputs.js")
        val expected = listOf(
            "['blaze_powder_from_rod', 'forge:rods/blaze', 'minecraft:blaze_powder', 1, false]",
            "['certus_quartz_dust_from_gem', 'forge:gems/certus_quartz', 'ae2:certus_quartz_dust', 1, false]",
            "['coal_dust', 'forge:ores/coal', 'bloodmagic:coalsand', 4, false]",
            "['datura', 'forge:crops/datura', 'occultism:datura_seeds', 2, false]",
            "['end_stone_dust', 'forge:end_stones', 'occultism:crushed_end_stone', 1, false]",
            "['iesnium_dust', 'forge:ores/iesnium', 'occultism:iesnium_dust', 2, false]",
            "['iesnium_dust_from_ingot', 'forge:ingots/iesnium', 'occultism:iesnium_dust', 1, true]",
            "['iesnium_dust_from_raw', 'forge:raw_materials/iesnium', 'occultism:iesnium_dust', 2, false]",
            "['iesnium_dust_from_raw_block', 'forge:storage_blocks/raw_iesnium', 'occultism:iesnium_dust', 18, false]",
            "['iridium_dust_from_ingot', 'forge:ingots/iridium', 'chemlib:iridium_dust', 1, true]",
            "['redstone_dust', 'forge:ores/redstone', 'minecraft:redstone', 4, false]",
            "['tungsten_dust_from_ingot', 'forge:ingots/tungsten', 'chemlib:tungsten_dust', 1, true]",
        )
        expected.forEach { assertTrue(script.contains(it), "missing $it") }
        assertEquals(12, Regex("(?m)^\\s*\\['").findAll(script).count())
        assertFalse(script.contains("minecraft:barrier"))
    }

    @Test
    fun `runtime policy rejects barrier outputs and generic hosted ore item tags`() {
        val mapper = jacksonObjectMapper()
        val goodRecipes = mapper.readTree(
            """{"recipes":[{"id":"occultism:crushing/coal_dust","outputs":[{"kind":"item","id":"bloodmagic:coalsand","count":4}]}]}""",
        )
        val goodTags = mapper.readTree("""{"item_tags":{"forge:ores":[],"c:ores":[]}}""")
        RuntimeSnapshotValidator.validateOreProcessing(goodRecipes, goodTags)

        val barrierRecipes = mapper.readTree(
            """{"recipes":[{"id":"occultism:crushing/coal_dust","outputs":[{"kind":"item","id":"minecraft:barrier","count":4}]}]}""",
        )
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeSnapshotValidator.validateOreProcessing(barrierRecipes, goodTags)
        }

        val leakedTags = mapper.readTree(
            """{"item_tags":{"forge:ores":["excavated_variants:granite_black_shale"],"c:ores":[]}}""",
        )
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeSnapshotValidator.validateOreProcessing(goodRecipes, leakedTags)
        }
    }

    private fun read(relative: String): String = Files.readString(root.resolve(relative))
}
