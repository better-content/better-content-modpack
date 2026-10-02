package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

@Tag("fast")
class MalumCrudeScytheAvailabilityTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val mapper = jacksonObjectMapper()

    @Test
    fun `crude scythe stays craftable and visible outside the quarantine list`() {
        val graph = mapper.readTree(root.resolve("docs/malum_acquisition_graph.json").toFile())
        val route = graph.path("routes").first { it.path("id").asText() == "malum.crude_scythe" }
        assertTrue(route.path("outputs").any { it.asText() == "malum:crude_scythe" })
        assertEquals(
            setOf("malum:processed_soulstone", "minecraft:iron_ingot", "minecraft:stick"),
            route.path("requiresAnyOf").map { it[0].asText() }.toSet(),
        )
        assertTrue(route.path("pinnedProviderResources").any { it.asText() == "data/malum/recipes/crude_scythe.json" })
        assertTrue(route.path("evidence").any { it.asText() == "mods/malum.pw.toml" })

        val quarantined = mapper.readTree(root.resolve("kubejs/config/quarantined_items.json").toFile())
            .path("items").map { it.asText() }.toSet()
        assertFalse("malum:crude_scythe" in quarantined, "The source-reachable Crude Scythe must remain visible in EMI")
    }
}
