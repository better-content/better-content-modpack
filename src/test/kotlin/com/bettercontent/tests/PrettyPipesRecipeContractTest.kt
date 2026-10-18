package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Files
import java.util.concurrent.TimeUnit

@Tag("fast")
class PrettyPipesRecipeContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()

    @Test fun `executed logistics recipes put finite stone only in network devices`() {
        // Test-local bounded capture; do not depend on unrelated in-flight harness changes.
        val directory = Files.createDirectories(root.resolve("build/tmp/pretty-pipes-contract"))
        val log = Files.createTempFile(directory, "recipes-", ".json")
        val process = ProcessBuilder("node",
            root.resolve("src/test/resources/pretty-pipes-logistics-contract.cjs").toString(),
            root.resolve("kubejs/server_scripts/progression/20_logistics_roots.js").toString())
            .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start()
        val output = try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Recipe fixture exceeded its deadline")
            assertEquals(0, process.exitValue(), Files.readString(log))
            assertTrue(Files.size(log) < 1024 * 1024, "Bounded recipe fixture output")
            Files.readString(log)
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Recipe fixture process cleanup")
            }
            // Retain bounded recipe output on success and failure.
        }
        val actions = jacksonObjectMapper().readTree(output).toList()
        val pretty = actions.filter { it.path("output").asText().contains("prettypipes:") }
        assertEquals(11, pretty.size)
        fun recipe(name: String) = pretty.single { it.path("output").asText().substringAfter("x ") == "prettypipes:$name" }
        fun counts(name: String): Map<String, Int> {
            val row = recipe(name)
            val symbols = row.path("pattern").joinToString("") { it.asText() }.filter { it != ' ' }.groupingBy { it }.eachCount()
            return symbols.entries.groupBy { row.path("key").path(it.key.toString()).asText() }
                .mapValues { (_, entries) -> entries.sumOf { it.value } }
        }
        assertEquals("8x prettypipes:pipe", recipe("pipe").path("output").asText())
        assertEquals(mapOf("ae2:certus_quartz_crystal" to 1, "minecraft:redstone" to 4,
            "#forge:glass" to 2, "minecraft:iron_bars" to 2), counts("pipe"))
        assertEquals(mapOf("ae2:certus_quartz_crystal" to 4, "minecraft:redstone" to 2,
            "minecraft:stone_slab" to 2, "prettypipes:pipe" to 1), counts("blank_module"))
        val families = listOf("crafting", "extraction", "filter", "high_priority", "low_priority", "retrieval", "speed")
        families.forEach { family ->
            val ingredients = counts("high_${family}_module")
            assertEquals(1, ingredients["ae2:certus_quartz_crystal"])
            assertEquals(1, ingredients["prettypipes:medium_${family}_module"])
            assertFalse(ingredients.containsKey("ae2:sky_stone_block"))
        }
        assertEquals(mapOf("ae2:sky_stone_block" to 1, "minecraft:ender_pearl" to 2,
            "minecraft:iron_block" to 2, "prettypipes:high_retrieval_module" to 1,
            "#forge:chests" to 1, "prettypipes:high_extraction_module" to 1,
            "minecraft:diamond" to 1), counts("item_terminal"))
        assertEquals(mapOf("ae2:sky_stone_block" to 1, "minecraft:iron_ingot" to 4,
            "prettypipes:high_speed_module" to 4), counts("pressurizer"))
        assertFalse(pretty.any { it.path("output").asText() == "prettypipes:crafting_terminal" })
        pretty.forEach { row ->
            val name = row.path("output").asText().substringAfter("x ").substringAfter(":")
            val index = actions.indexOf(row)
            assertTrue(actions.take(index).any { it.path("selector").path("output").asText() == "prettypipes:$name" }, name)
            assertEquals(if (name == "pipe") "kubejs:logistics_roots/root/pretty_pipes" else
                "kubejs:logistics_roots/proto_ae2/$name", row.path("id").asText())
            assertFalse(row.path("key").toString().contains("enchanted_gravitite"))
            assertFalse(row.path("key").toString().contains("blaze_rod"))
            assertFalse(row.path("key").toString().contains("honey_crystal_shards"))
            assertFalse(row.path("key").toString().contains("processor"))
        }
        assertTrue(actions.any { it.path("selector").path("id").asText() == "prettypipes:module_clearing" })
        val other = actions.filter { it.has("output") && !it.path("output").asText().contains("prettypipes:") }
        assertEquals(5, other.size)
        assertTrue(other.single { it.path("output").asText() == "ae2:energy_acceptor" }
            .path("ingredients").any { it.asText() == "kubejs:impossible_support_matrix" })
        assertTrue(other.single { it.path("output").asText() == "create:redstone_requester" }
            .path("ingredients").any { it.asText() == "minecraft:blaze_rod" })
    }
}
