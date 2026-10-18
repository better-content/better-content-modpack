package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/** Native resource inspection, not runtime or survival-acquisition acceptance. */
@Tag("fast")
class WeaponsOfMiraclesContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root", "."))
    private val mapper = jacksonObjectMapper()
    private fun pinnedModJar(filename: String): Path =
        Path.of(System.getProperty("user.home"), ".cache/bc/packwiz-downloads/mods", filename)

    @Test fun nativeWeaponPresetsMatchTheDocumentedInventory() {
        val expected = setOf(
            "staff", "agony", "torment", "ruine", "satsujin", "ender_blaster",
            "clawed_gauntle", "antitheus", "herrscher", "gesetz", "moonless", "solar",
            "napoleon", "evil_tachi", "orbit", "nova", "blackstar"
        ).map { "wom:$it" }.toSet()
        ZipFile(pinnedModJar("WeaponsOfMiracles-2.0.171-mc1.20.1-forge.jar").toFile()).use { jar ->
            val mappings = jar.entries().asSequence()
                .filter { it.name.startsWith("data/wom/capabilities/weapons/") && it.name.endsWith(".json") }
                .associate { entry ->
                    entry.name.substringAfterLast('/').removeSuffix(".json") to
                        jar.getInputStream(entry).use { mapper.readTree(it)["type"].asText() }
                }
            assertEquals(expected, mappings.values.filter { it.startsWith("wom:") }.toSet())
            assertEquals("wom:torment", mappings["tormented_mind"])
            assertEquals("wom:clawed_gauntle", mappings["jabberwocky"])
            for (material in listOf("golden", "iron", "diamond", "netherite")) {
                assertEquals("epicfight:greatsword", mappings["${material}_greataxe"])
            }
            assertEquals("epicfight:longsword", mappings["hollow_longsword"])
            val nativeMetadata = jar.getInputStream(jar.getEntry("META-INF/mods.toml"))
                .bufferedReader().use { it.readText() }
            assertTrue(nativeMetadata.contains("versionRange=\"[20.14.3,)\""))
        }
        val policy = mapper.readTree(root.resolve("kubejs/config/crafting_policy.json").toFile())
        assertEquals("content", policy["namespaces"]["wom"]["primary_role"].asText())
        assertEquals("full", policy["namespaces"]["wom"]["support_state"].asText())
        val docs = Files.readString(root.resolve("docs/weapon_balance_philosophy.md"))
        expected.forEach { assertTrue(docs.contains("`$it`"), "Document native preset $it") }
    }
}
