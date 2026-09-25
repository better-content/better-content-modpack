package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class World09DeepVoidSourceContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val mapper = jacksonObjectMapper()

    @Test
    fun `pins the inspected Deep Void Forge release`() {
        val pin = Files.readString(root.resolve("mods/the-deep-void.pw.toml"))
        assertTrue(pin.contains("filename = \"the_deep_void-1.98.1-forge-1.20.1.jar\""))
        assertTrue(pin.contains("hash = \"faa95b5af7eaf398f109c3abab054a0036a18022\""))
        assertTrue(pin.contains("file-id = 8507128"))
        assertTrue(pin.contains("project-id = 982999"))
    }

    @Test
    fun `removes only the one-time origin temple and preserves native entry defaults`() {
        val config = Files.readString(root.resolve("config/Deep Void Config.toml"))
        assertTrue(config.contains("[\"Generation&World\"]"))
        assertTrue(config.contains("PlaceCultTemple = false"))
        assertTrue(config.contains("distributed native holes"))
        assertFalse(config.contains("PlayersTeleportInVoid = false"))
        assertFalse(config.contains("PlayersTeleportInEnd = false"))
        assertFalse(config.contains("SpawnAbductors = false"))
        assertFalse(config.contains("CrumblingBedrockGenerate = false"))
    }

    @Test
    fun `removes only the pinned release giant skull feature that writes across chunks`() {
        val modifier = mapper.readTree(
            root.resolve("datapacks/worldgen_compat_fixes/data/the_deep_void/forge/biome_modifier/giant_skulls_biome_modifier.json")
                .toFile(),
        )
        assertEquals("forge:remove_features", modifier.path("type").asText())
        assertEquals(listOf("the_deep_void:giant_skulls"), modifier.path("features").map { it.asText() })
        assertTrue(modifier.path("step").asText() == "surface_structures")
        assertEquals(
            setOf(
                "the_deep_void:mourning_graveyard",
                "the_deep_void:grim_canopy",
                "the_deep_void:staring_hills",
                "the_deep_void:filled_graveyard",
                "the_deep_void:gathering_grounds",
                "the_deep_void:deep_marrows",
                "the_deep_void:forgotten_valley",
            ),
            modifier.path("biomes").map { it.asText() }.toSet(),
        )
    }

    @Test
    fun `suppresses only the pinned release features that write into far chunks`() {
        val modifierDirectory = root.resolve(
            "datapacks/worldgen_compat_fixes/data/the_deep_void/forge/biome_modifier",
        )
        val expected = mapOf(
            "befouled_marsh_liquid_void_biome_modifier.json" to Triple(
                "surface_structures", "the_deep_void:befouled_marsh_liquid_void", setOf("the_deep_void:befouled_marsh"),
            ),
            "bone_floor_graveyard_biome_modifier.json" to Triple(
                "vegetal_decoration", "the_deep_void:bone_floor_graveyard", setOf(
                    "the_deep_void:mourning_graveyard", "the_deep_void:watching_undergrowth",
                ),
            ),
            "broken_bones_biome_modifier.json" to Triple(
                "local_modifications", "the_deep_void:broken_bones", setOf("the_deep_void:deep_marrows"),
            ),
            "rotten_bone_pile_generate_biome_modifier.json" to Triple(
                "local_modifications", "the_deep_void:rotten_bone_pile_generate", setOf(
                    "the_deep_void:filled_graveyard", "the_deep_void:deep_marrows",
                ),
            ),
            "flesh_stuff_biome_modifier.json" to Triple(
                "vegetal_decoration", "the_deep_void:flesh_stuff", setOf("the_deep_void:gloomy_deathgrounds"),
            ),
            "pustulent_flesh_block_generate_biome_modifier.json" to Triple(
                "vegetal_decoration",
                "the_deep_void:pustulent_flesh_block_generate",
                setOf("the_deep_void:gloomy_deathgrounds"),
            ),
            "mossy_bone_pile_generate_biome_modifier.json" to Triple(
                "local_modifications", "the_deep_void:mossy_bone_pile_generate", setOf(
                    "the_deep_void:forgotten_valley",
                    "the_deep_void:mourning_graveyard",
                    "the_deep_void:watching_undergrowth",
                    "the_deep_void:deep_marrows",
                ),
            ),
        )

        expected.forEach { (name, contract) ->
            val modifier = mapper.readTree(modifierDirectory.resolve(name).toFile())
            assertEquals("forge:remove_features", modifier.path("type").asText(), name)
            assertEquals(listOf(contract.second), modifier.path("features").map { it.asText() }, name)
            assertEquals(contract.first, modifier.path("step").asText(), name)
            val biomes = modifier.path("biomes")
            val actualBiomes = if (biomes.isTextual) setOf(biomes.asText())
            else biomes.map { it.asText() }.toSet()
            assertEquals(contract.third, actualBiomes, name)
        }
    }

    @Test
    fun `Malum deep ores target the pinned Deep Void biome family`() {
        val dataRoot = root.resolve("datapacks/dimension_drink_ore_relocation/data")
        val targets = mapper.readTree(
            dataRoot.resolve("kubejs/tags/worldgen/biome/dimension_drink_ore_targets/deep.json").toFile(),
        )
        assertFalse(targets.path("replace").asBoolean())
        assertTrue(targets.path("values").any { it.asText() == "#the_deep_void:is_deep_void" })

        listOf("soulstone_ore", "brilliant_ore", "cthonic_gold_ore").forEach { route ->
            val modifier = mapper.readTree(
                dataRoot.resolve("kubejs/forge/biome_modifier/dimension_drink_$route.json").toFile(),
            )
            assertTrue(
                modifier.path("biomes").asText() == "#kubejs:dimension_drink_ore_targets/deep",
                "$route must remain connected to the Deep Void biome tag",
            )
        }
    }

    @Test
    fun `one-way Deep Void entry removes the providers return item recipes`() {
        val policy = Files.readString(root.resolve("kubejs/server_scripts/policy/removed_items.js"))
        val start = policy.indexOf("// Deep Void access is one-way by design.")
        val end = policy.indexOf("event.remove({ type: 'bloodmagic:dimension_drink' })", start)
        assertTrue(start >= 0, "one-way policy block must remain identifiable")
        assertTrue(end > start, "one-way policy block must remain bounded")
        val oneWayPolicy = policy.substring(start, end)
        assertTrue(oneWayPolicy.contains("if (Platform.isLoaded('the_deep_void'))"))
        assertTrue(oneWayPolicy.contains(".forEach(function (id) { event.remove({ id: id }) })"))
        listOf(
            "the_deep_void:void_pendant",
            "the_deep_void:void_mirror",
            "the_deep_void:void_core_recipe",
        ).forEach { id -> assertTrue(oneWayPolicy.contains("'$id'"), "missing return recipe removal: $id") }

        val quarantined = mapper.readTree(root.resolve("kubejs/config/quarantined_items.json").toFile())
            .path("items").map { it.asText() }
        listOf(
            "the_deep_void:void_pendant",
            "the_deep_void:void_mirror",
            "the_deep_void:void_core",
        ).forEach { item -> assertTrue(item in quarantined, "alternate recipe could still produce $item") }
        assertTrue(
            policy.contains("BC_DISABLED_ITEMS.forEach(function (item) { event.remove({ output: item }) })"),
            "quarantined return items must remove all recipes that produce them",
        )
        assertTrue(policy.contains("ItemEvents.rightClicked(item, function (event) { event.cancel() })"))
        assertTrue(policy.contains("BlockEvents.rightClicked(function (event)"))
        assertTrue(policy.contains("BC_DEEP_VOID_RETURN_ITEMS.indexOf(event.item.id) >= 0) event.cancel()"))
    }

}
