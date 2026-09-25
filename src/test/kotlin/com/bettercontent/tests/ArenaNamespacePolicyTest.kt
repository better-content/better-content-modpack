package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
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

        listOf("arena_challenges", "buried_encounters").forEach { namespace ->
            assertEquals("content", namespaces.path(namespace).path("primary_role").asText(), namespace)
            assertEquals("not_applicable", namespaces.path(namespace).path("support_state").asText(), namespace)
        }
    }
}
