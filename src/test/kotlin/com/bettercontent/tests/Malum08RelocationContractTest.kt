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
class Malum08RelocationContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val mapper = jacksonObjectMapper()

    @Test
    fun `all native Malum mineral modifiers are overridden by the empty biome tag`() {
        val relocationData = root.resolve("datapacks/dimension_drink_ore_relocation/data")
        val emptyTag = mapper.readTree(
            relocationData.resolve("kubejs/tags/worldgen/biome/no_biomes.json").toFile(),
        )
        assertTrue(emptyTag.path("replace").asBoolean())
        assertTrue(emptyTag.path("values").isArray)
        assertTrue(emptyTag.path("values").isEmpty)

        val expectedFeatures = mapOf(
            "blazing_quartz_ore" to "malum:blazing_quartz_ore",
            "brilliant_ore" to "malum:ore_brilliant",
            "cthonic_gold_ore" to "malum:cthonic_gold_ore",
            "deepslate_quartz_geode" to "malum:deepslate_quartz_geode",
            "natural_quartz_ore" to "malum:ore_natural_quartz",
            "quartz_geode" to "malum:quartz_geode",
            "soulstone_ore" to "malum:ore_soulstone",
        )
        expectedFeatures.forEach { (id, feature) ->
            val modifier = mapper.readTree(
                relocationData.resolve("malum/forge/biome_modifier/$id.json").toFile(),
            )
            assertEquals("forge:add_features", modifier.path("type").asText(), id)
            assertEquals("#kubejs:no_biomes", modifier.path("biomes").asText(), id)
            assertEquals(feature, modifier.path("features").asText(), id)
        }
    }

    @Test
    fun `each authored Malum relocation remains connected to its selected target tag`() {
        val data = root.resolve("datapacks/dimension_drink_ore_relocation/data")
        val destinations = mapOf(
            "dimension_drink_blazing_quartz_ore" to "nether",
            "dimension_drink_brilliant_ore" to "deep",
            "dimension_drink_cthonic_gold_ore" to "deep",
            "dimension_drink_malum_deepslate_quartz_geode" to "deep",
            "dimension_drink_malum_quartz_geode" to "sky",
            "dimension_drink_natural_quartz_ore" to "stone",
            "dimension_drink_soulstone_ore" to "deep",
        )
        destinations.forEach { (id, target) ->
            val modifier = mapper.readTree(data.resolve("kubejs/forge/biome_modifier/$id.json").toFile())
            val tagId = "#kubejs:dimension_drink_ore_targets/$target"
            assertEquals(tagId, modifier.path("biomes").asText(), "$id selected target")

            val targetTag = mapper.readTree(
                data.resolve("kubejs/tags/worldgen/biome/dimension_drink_ore_targets/$target.json").toFile(),
            )
            assertFalse(targetTag.path("replace").asBoolean(), "$target must stay additive")
            assertTrue(targetTag.path("values").isArray && !targetTag.path("values").isEmpty, "$target must resolve to authored biome tags")
        }
    }

    @Test
    fun `Malum Codex Cthonic Gold location describes the authored Deep Void relocation`() {
        val overridePath = root.resolve("kubejs/assets/malum/lang/en_us.json")
        val override = mapper.readTree(overridePath.toFile())
        val key = "malum.gui.book.entry.page.text.cthonic_gold.1"
        assertEquals(setOf(key), override.fieldNames().asSequence().toSet(), "keep this override limited to the corrected location text")

        val text = override.path(key).asText()
        assertTrue(text.contains("Deep Void"))
        assertTrue(text.contains("relocated ore generation"))
        assertFalse(text.contains("chance", ignoreCase = true))
        assertFalse(text.contains("probability", ignoreCase = true))
        assertTrue(Files.isRegularFile(overridePath))
    }
}
