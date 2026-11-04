package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Requested pack membership; source presence alone does not qualify runtime behavior. */
@Tag("fast")
class PackSpaceCompositionTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))

    @Test fun newSourcesAreExplicitAndCreatingSpaceIsNotADeclaredDownload() {
        val manifest = jacksonObjectMapper().readTree(root.resolve("gradle/active-custom-mods.json").toFile())
        val mods = manifest.path("mods")
        assertEquals(54, mods.size())
        val rows = mods.associateBy { it.path("repository").asText() }
        for ((repository, id) in listOf(
            "better-abyssal-city" to "better_abyssal_city",
            "better-spaceflight" to "better_spaceflight",
            "better-manufacturing" to "better_manufacturing",
            "better-electrochemistry" to "better_electrochemistry",
            "better-seasonal-daylight" to "better_seasonal_daylight",
            "better-seasonal-trees" to "better_seasonal_trees"
        )) {
            val row = rows.getValue(repository)
            assertEquals(id, row.path("modId").asText())
            assertTrue(Files.isRegularFile(root.resolve("mods/${row.path("artifact").asText()}")), repository)
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
        val aerospace = Files.readAllLines(root.resolve("kubejs/server_scripts/progression/60_aerospace.js"))
        assertTrue(aerospace.all { it.isBlank() || it.startsWith("//") }, "retired aerospace placeholder must contain comments only")
    }
}
