package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class HoverAnnotationLearningSurfaceTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val registry = jacksonObjectMapper().readTree(root.resolve("kubejs/config/hover_annotations.json").toFile())

    @Test
    fun `every annotation has a bounded stable concept and concise copy`() {
        assertEquals("bc.hover_annotations.v2", registry.path("schema").asText())
        val rows = registry.path("annotations")
        assertEquals(49, rows.size())
        rows.forEach { row ->
            assertTrue(row.path("concept_id").asText().matches(Regex("[a-z0-9_.]{3,96}")))
            assertTrue(row.path("owner").asText().isNotBlank())
            val lines = row.path("lines").map { it.asText() }
            assertTrue(lines.size in 1..2)
            assertTrue(lines.joinToString(" ").trim().split(Regex("\\s+")).size <= 24)
        }
    }

    @Test
    fun `Pretty Pipes annotations expose all three visible Ratlantis gates`() {
        val text = Files.readString(root.resolve("kubejs/config/hover_annotations.json"))
        assertTrue(text.contains("four lattices make the first 32 pipes"))
        assertTrue(text.contains("Oratchalcum Mechanism"))
        assertTrue(text.contains("Arcane Logistics Core"))
        listOf(
            "high_crafting_module", "high_extraction_module", "high_filter_module",
            "high_high_priority_module", "high_low_priority_module", "high_retrieval_module",
            "high_speed_module",
        ).forEach { assertTrue(text.contains("prettypipes:$it"), it) }
    }

    @Test
    fun `tooltip compiler consumes v2 concepts without changing the item-local surface`() {
        val script = Files.readString(root.resolve("kubejs/client_scripts/guidance/10_hover_annotations.js"))
        assertTrue(script.contains("bc.hover_annotations.v2"))
        assertTrue(script.contains("var conceptId = String(row.concept_id || '')"))
        assertTrue(script.contains("conceptId: conceptId"))
        assertTrue(script.contains("ItemEvents.tooltip"))
    }
}
