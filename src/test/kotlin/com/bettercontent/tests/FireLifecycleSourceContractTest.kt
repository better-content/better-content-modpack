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
class FireLifecycleSourceContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val mapper = jacksonObjectMapper()

    @Test
    fun `Burnt owns enabled ignition wetness staged burn and ecological recovery policies`() {
        val config = Files.readString(root.resolve("config/burnt_basic_config.toml"))
        val global = section(config, "global options")
        val burn = section(config, "burn settings")
        val blocks = section(config, "block settings")
        val recovery = section(config, "recovery settings")

        assertEquals("true", value(global, "burntOn"))
        assertEquals("true", value(global, "rainExtinguish"))
        assertEquals("true", value(global, "vanillaBurn"))
        assertEquals("true", value(global, "lightningBurn"))
        assertEquals("true", value(global, "biomeSpecific"))
        assertEquals("false", value(global, "lavaBurn"), "lava has no separate uncontrolled ignition path")
        assertEquals("true", value(blocks, "craftedBlocksBurn"), "established fires may burn ordinary player-built materials")
        assertEquals("false", value(blocks, "burnBarrels"), "fire must not destroy stored player inventory")

        assertInRange(value(burn, "burnSpread"), 0.0, 100.0)
        assertInRange(value(burn, "groundSpread"), 0.0, 100.0)
        assertInRange(value(burn, "burnSpeed"), 0.0, 100.0)
        assertEquals("true", value(recovery, "grassRecovery"))
        assertEquals("true", value(recovery, "forestRecovery"))
        assertInRange(value(recovery, "grassRecoverSpeed"), 0.0, 100.0)
    }

    @Test
    fun `burnable ecology and recovering Dynamic Trees substrates share Burnt transitions`() {
        val burnedPlants = mapper.readTree(
            root.resolve("kubejs/data/burnt/tags/blocks/plants_will_burn.json").toFile(),
        ).path("values").map { it.asText() }.toSet()
        val grassySubstrates = mapper.readTree(
            root.resolve("kubejs/data/burnt/tags/blocks/grass_blocks.json").toFile(),
        ).path("values").map { it.asText() }.toSet()

        assertTrue(burnedPlants.contains("natures_spirit:melic_grass"))
        assertTrue(burnedPlants.contains("natures_spirit:scorched_grass"))
        assertTrue(grassySubstrates.contains("immersive_weathering:rooted_grass_block"))
        assertTrue(grassySubstrates.contains("dynamictrees:rooty_grass_block"))

        val soilDirectory = root.resolve("datapacks/dt_soil_compat/trees/kubejs/soil_properties")
        val burnStages = listOf("burnt_burnt_grass", "burnt_recovering_grass", "burnt_semi_burnt_dirt")
        for (name in burnStages) {
            val soil = mapper.readTree(soilDirectory.resolve("$name.json").toFile())
            assertEquals("burnt", soil.path("only_if_loaded").asText(), "$name must be gated by its owning mod")
            assertTrue(soil.path("primitive_soil").asText().startsWith("burnt:"))
            assertFalse(soil.path("acceptable_soils").isEmpty)
        }

        val packwiz = Files.readString(root.resolve("index.toml"))
        assertTrue(packwiz.contains("file = \"mods/burnt-basic.pw.toml\""))
        assertTrue(packwiz.contains("file = \"config/burnt_basic_config.toml\""))
        assertTrue(packwiz.contains("file = \"kubejs/data/burnt/tags/blocks/plants_will_burn.json\""))
    }

    private fun section(toml: String, name: String): String {
        val match = Regex("(?s)\\[\\\"$name\\\"]\\s*(.*?)(?=\\n\\[|$)").find(toml)
        return requireNotNull(match) { "missing config section [$name]" }.groupValues[1]
    }

    private fun value(section: String, key: String): String {
        val match = Regex("(?m)^\\s*$key\\s*=\\s*([^#\\r\\n]+)").find(section)
        return requireNotNull(match) { "missing config value $key" }.groupValues[1].trim()
    }

    private fun assertInRange(raw: String, minimum: Double, maximum: Double) {
        val value = raw.toDouble()
        assertTrue(value > minimum && value < maximum, "$value must be gradual, inside ($minimum, $maximum)")
    }
}
