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
class UntamedWildsIntegrationContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val mapper = jacksonObjectMapper()
    private fun text(path: String) = Files.readString(root.resolve(path))
    private fun json(path: String) = mapper.readTree(root.resolve(path).toFile())

    @Test
    fun `published wildlife is pinned and native fauna features are gated`() {
        val descriptor = text("mods/untamed-wilds-au-naturel.pw.toml")
        assertTrue(descriptor.contains("filename = \"untamedwilds-4.0.4-b004.jar\""))
        assertTrue(descriptor.contains("file-id = 7441092"))
        assertTrue(descriptor.contains("project-id = 1399279"))
        assertTrue(text("config/untamedwilds-common.toml").contains("[mobcontrol]\nmasterspawner = false"))
        for (name in listOf("apex_predator", "burrow", "dense_water", "herbivores", "ocean_rare", "sessile")) {
            val modifier = json("kubejs/data/untamedwilds/forge/biome_modifier/$name.json")
            assertEquals("mobcontrol.masterspawner", modifier["configOption"].asText())
            assertEquals("untamedwilds:$name", modifier["feature"]["feature"]["type"].asText())
        }
    }

    @Test
    fun `only overlapping Thalassophobia wildlife and vanilla stand-ins lose natural spawns`() {
        val overlap = setOf("sunfish", "catfish", "big_catfish", "shark", "bull_shark", "goblin_shark", "hammerhead_shark", "whale_shark", "whale")
        for (name in overlap) {
            val modifier = json("kubejs/data/thalassophobia/forge/biome_modifier/${name}_biome_modifier.json")
            assertEquals("forge:remove_spawns", modifier["type"].asText())
            assertEquals(listOf("thalassophobia:$name"), modifier["entity_types"].map { it.asText() })
        }
        val denied = json("config/incontrol/spawn.json").last()
        assertEquals("natural", denied["spawntype"].asText())
        assertEquals("deny", denied["result"].asText())
        assertEquals(overlap.map { "thalassophobia:$it" }.toSet() + setOf("minecraft:camel", "minecraft:panda", "minecraft:polar_bear"), denied["mob"].map { it.asText() }.toSet())
        for (name in listOf("giant_clam_natural_spawn", "basking_shark", "frilled_shark", "cookie_cutter_shark")) {
            assertFalse(denied["mob"].any { it.asText() == "thalassophobia:$name" })
            assertFalse(Files.exists(root.resolve("kubejs/data/thalassophobia/forge/biome_modifier/${name}_biome_modifier.json")))
        }
    }

    @Test
    fun `replenishment remains bounded and pearl conversion is one way`() {
        val rules = json("config/incontrol/spawner.json").filter { it["mob"]?.asText()?.startsWith("untamedwilds:") == true }
        assertEquals(38, rules.size)
        assertFalse(rules.any { it["mob"].asText() == "untamedwilds:giant_clam" })
        assertTrue(rules.all { it["conditions"]["dimension"].asText() == "minecraft:overworld" })
        assertTrue(rules.all { it["conditions"]["maxthis"].asInt() in 1..8 && it["conditions"]["norestrictions"] == null })
        for (rule in rules) {
            val mob = rule["mob"].asText().substringAfter(':')
            val tag = "kubejs:untamed_spawn/$mob"
            assertEquals(tag, rule["conditions"]["and"]["biometags"].asText())
            val values = json("kubejs/data/kubejs/tags/worldgen/biome/untamed_spawn/$mob.json")["values"]
            assertTrue(values.isArray && values.size() > 0)
            assertFalse(values.any { it.asText() in setOf("minecraft:bamboo_jungle_hills", "minecraft:deep_warm_ocean") })
        }
        for (name in listOf("shark", "whale_shark", "catfish", "arowana")) {
            assertTrue(rules.filter { it["mob"].asText() == "untamedwilds:$name" }.all { it["conditions"]["inwater"].asBoolean() })
        }
        val recipes = text("kubejs/server_scripts/compat/reviewed/untamed_thalassophobia_pearls.js")
        assertTrue(recipes.contains("event.shapeless('untamedwilds:material_pearl', ['thalassophobia:pearl'])"))
        assertTrue(recipes.contains("event.shapeless('untamedwilds:material_giant_pearl', ['thalassophobia:black_pearl'])"))
        assertFalse(recipes.contains("event.shapeless('thalassophobia:"))
        val exclusions = json("kubejs/data/emi_loot/emi_loot_data/table_exclusions.json")["exclusions"]
        assertTrue(exclusions.any { it.asText() == "untamedwilds:entities/baleen_whale" })
    }
}
