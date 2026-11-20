package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Requested pack membership; source presence alone does not qualify runtime behavior. */
@Tag("fast")
class PackSpaceCompositionTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))

    @Test fun newSourcesAreExplicitAndCreatingSpaceIsNotADeclaredDownload() {
        val manifest = jacksonObjectMapper().readTree(root.resolve("gradle/active-custom-mods.json").toFile())
        val mods = manifest.path("mods")
        // World Management remains excluded; Electrochemistry, Arena Trials, Airtight Machines,
        // ChemLib Hazards and Cooking are retired from the active source inventory.
        assertEquals(48, mods.size())
        val rows = mods.associateBy { it.path("repository").asText() }
        assertEquals(mods.size(), rows.size, "active repository identities must be unique")
        assertFalse(rows.containsKey("better-world-management"))
        val indexed = Files.readString(root.resolve("index.toml"))
        assertFalse(indexed.contains("mods/better-world-management-"))
        for (repository in listOf("better-electrochemistry", "better-arena-trials")) {
            assertFalse(rows.containsKey(repository), "$repository must remain inactive")
            assertFalse(indexed.contains("mods/$repository-"), "$repository must not be exported")
            Files.list(root.resolve("mods")).use { files ->
                assertFalse(files.anyMatch { it.fileName.toString().startsWith("$repository-") },
                    "$repository must not remain in the runtime mods directory")
            }
        }
        for (recipe in listOf("aqueous_copper_ii_sulfate", "copper_sheet_deposition")) {
            val relative = "kubejs/data/better_content/recipes/chemistry/electrical/$recipe.json"
            assertFalse(Files.exists(root.resolve(relative)), "retired Electrochemistry recipe: $recipe")
            assertFalse(indexed.contains(relative), "retired recipe must not be exported: $recipe")
        }
        for ((repository, id) in listOf(
            "better-abyssal-city" to "better_abyssal_city",
            "better-spaceflight" to "better_spaceflight",
            "better-manufacturing" to "better_manufacturing",
            "better-seasonal-daylight" to "better_seasonal_daylight",
            "better-seasonal-trees" to "better_seasonal_trees"
        )) {
            val row = rows.getValue(repository)
            assertEquals(id, row.path("modId").asText())
            val relative = "mods/${row.path("artifact").asText()}"
            val entry = Regex("(?m)^file = \"${Regex.escape(relative)}\"\\r?\\nhash = \"([a-f0-9]{64})\"$").find(indexed)
            assertNotNull(entry, "indexed local-cache SHA-256 is required: $repository")
            // A Git clone contains metadata, not JAR bytes. Validate cached bytes when
            // present; the packager separately requires every selected JAR to exist.
            val cached = root.resolve(relative)
            if (Files.exists(cached)) {
                assertTrue(Files.isRegularFile(cached), repository)
                val sha = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(cached))
                    .joinToString("") { "%02x".format(it) }
                assertEquals(entry!!.groupValues[1], sha, repository)
            }
        }
        assertFalse(rows.containsKey("dynamic-trees-dimension-compat"))
        assertFalse(Files.exists(root.resolve("mods/dynamic-trees-dimension-compat-0.1.1.jar")))
        assertFalse(Files.exists(root.resolve("mods/simpledaylengthextender.pw.toml")))
        assertFalse(Files.exists(root.resolve("mods/create-creating-space.pw.toml")))
        for (path in listOf("config/creatingspace-common.toml", "config/creatingspace-client.toml",
            "defaultconfigs/creatingspace-server.toml")) {
            assertFalse(Files.exists(root.resolve(path)), path)
        }
        val policy = jacksonObjectMapper().readTree(root.resolve("kubejs/config/crafting_policy.json").toFile()).path("namespaces")
        assertEquals("world", policy.path("better_abyssal_city").path("primary_role").asText())
        assertEquals("gameplay_system", policy.path("better_spaceflight").path("primary_role").asText())
        assertFalse(policy.has("creatingspace"))
        assertFalse(policy.has("better_electrochemistry"))
        assertFalse(policy.has("better_arena_trials"))
        val aerospace = Files.readAllLines(root.resolve("kubejs/server_scripts/progression/60_aerospace.js"))
        assertTrue(aerospace.all { it.isBlank() || it.startsWith("//") }, "retired aerospace placeholder must contain comments only")
    }
}
