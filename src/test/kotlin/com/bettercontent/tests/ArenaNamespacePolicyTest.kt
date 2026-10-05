package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import java.nio.file.Path

@Tag("fast")
class CustomModNamespacePolicyTest {
    @Test
    fun sourceModsAreClassifiedWithoutInventingCraftingSupport() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val namespaces = jacksonObjectMapper()
            .readTree(root.resolve("kubejs/config/crafting_policy.json").toFile())
            .path("namespaces")
        val active = jacksonObjectMapper()
            .readTree(root.resolve("gradle/active-custom-mods.json").toFile()).path("mods")
        active.forEach { mod ->
            val namespace = mod.path("repository").asText().replace('-', '_')
            assertTrue(namespaces.path(namespace).path("primary_role").asText().isNotBlank(), namespace)
            assertTrue(namespaces.path(namespace).path("support_state").asText().isNotBlank(), namespace)
        }

        listOf("better_arena_trials", "better_buried_encounters", "scalable_tnt").forEach { namespace ->
            assertEquals("content", namespaces.path(namespace).path("primary_role").asText(), namespace)
            assertEquals("not_applicable", namespaces.path(namespace).path("support_state").asText(), namespace)
        }
    }
}
