package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

@Tag("fast")
class HoverAnnotationLearningSurfaceTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val registry = jacksonObjectMapper().readTree(root.resolve("kubejs/config/hover_annotations.json").toFile())

    @Test
    fun `every annotation has a bounded stable concept and concise copy`() {
        assertEquals("bc.hover_annotations.v2", registry.path("schema").asText())
        val rows = registry.path("annotations")
        val allowedCategories = setOf(
            "correction", "lifecycle_state", "hidden_composition", "general_uses",
            "capability_root", "process_authority", "operation_contract", "requirement_limit",
            "provenance", "scope_boundary", "persistence_consequence", "economy_semantics",
            "combat_handling",
        )
        val exactTargets = mutableListOf<String>()
        rows.forEach { row ->
            assertTrue(row.path("concept_id").asText().matches(Regex("[a-z0-9_.]{3,96}")))
            assertTrue(row.path("category").asText() in allowedCategories, row.toString())
            assertTrue(row.path("domain").asText().matches(Regex("[a-z0-9_.]{3,96}")))
            assertTrue(row.path("owner").asText().isNotBlank())
            val selector = row.path("selector")
            val selectorKinds = listOf("item", "items", "tag").count(selector::has)
            assertEquals(1, selectorKinds, row.toString())
            selector.path("item").takeIf { !it.isMissingNode }?.asText()?.let {
                assertTrue(it.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")), row.toString())
                exactTargets += it
            }
            selector.path("items").takeIf { it.isArray }?.map { it.asText() }?.let { items ->
                assertTrue(items.isNotEmpty(), row.toString())
                assertEquals(items.size, items.distinct().size, row.toString())
                assertTrue(items.all { it.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")) }, row.toString())
                exactTargets += items
            }
            selector.path("tag").takeIf { !it.isMissingNode }?.asText()?.let {
                assertTrue(it.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")), row.toString())
            }
            val lines = row.path("lines").map { it.asText() }
            assertTrue(lines.size in 1..2)
            assertTrue(lines.joinToString(" ").trim().split(Regex("\\s+")).size <= 24)
        }
        assertEquals(exactTargets.size, exactTargets.distinct().size, "duplicate exact item selectors")
    }

    @Test
    fun `every pack-created transition item has an annotation`() {
        val source = Files.readString(root.resolve("kubejs/startup_scripts/progression/20_transition_items.js"))
        val registered = Regex("\\['([a-z0-9_]+)',\\s*'[^']+'\\]")
            .findAll(source)
            .map { "kubejs:${it.groupValues[1]}" }
            .toSet()
        val missing = registered - exactTargets()
        assertTrue(registered.isNotEmpty())
        assertTrue(missing.isEmpty(), "unannotated pack-created transition items: ${missing.sorted()}")
    }

    @Test
    fun `critical curriculum systems retain natural hover anchors`() {
        val exact = exactTargets()
        val tags = tagTargets()
        val expectedExact = setOf(
            "dimension_drink:dimensional_font",
            "dimension_drink:return_seal",
            "bumblezone_cultivars:living_pollen_nursery",
            "water_survival:rain_collector",
            "mining_helmet:mining_helmet",
            "oc2r_wireless_pubsub:wireless_relay",
            "procedural_bouquets:bouquet_grid",
            "better_content_economy:sacred_reliquary",
            "malum:spirit_pouch",
            "ratlantis_logistics:courier_lattice",
            "rail_beetle:route_beacon",
            "traces:foot_traffic_probe",
            "tinkers_construct_affixes:affixed_part_cache",
            "create:schematicannon",
            "sereneseasons:calendar",
            "weather2:tornado_sensor",
            "adpother:aerometer",
            "bloodmagic:altar",
            "hexerei:mixing_cauldron",
            "ae2:blank_pattern",
            "ae2:pattern_provider",
            "creatingspace:rocket_controls",
            "iceandfire:dragonsteel_fire_ingot",
            "realistic_ores:surface_sample_hotstone",
        )
        val expectedTags = setOf(
            "bumblezone_cultivars:seeds",
            "dynamictrees:seeds",
            "realistic_ores:crushed_feeds",
            "realistic_ores:rinsed_feeds",
            "realistic_ores:radioactive_forms/uranium/hosted_ore_blocks",
            "realistic_ores:radioactive_forms/thorium/hosted_ore_blocks",
        )
        assertTrue((expectedExact - exact).isEmpty(), "missing exact anchors: ${(expectedExact - exact).sorted()}")
        assertTrue((expectedTags - tags).isEmpty(), "missing tag anchors: ${(expectedTags - tags).sorted()}")
    }

    @Test
    fun `Rail Beetle annotations teach baseline power and tier progression`() {
        val text = Files.readString(root.resolve("kubejs/config/hover_annotations.json"))
        assertTrue(text.contains("Surveys, drives, and builds rails and shallow bridges."))
        assertTrue(text.contains("Switches to the built-in coal firebox when depleted."))
        assertTrue(text.contains("Crafting Tier II consumes the matching Tier I module."))
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
    fun `early handling annotations expose the primitive and automatic boundary`() {
        val text = Files.readString(root.resolve("kubejs/config/hover_annotations.json"))
        assertTrue(text.contains("Passive vertical transfer from canvas and iron fittings"))
        assertTrue(text.contains("Automatic crafting begins after Create's Mechanical Crafter."))
        assertTrue(text.contains("High-throughput transfer requires a Courier Lattice from Ratlantis."))
    }

    @Test
    fun `tooltip compiler consumes v2 concepts without changing the item-local surface`() {
        val script = Files.readString(root.resolve("kubejs/client_scripts/guidance/10_hover_annotations.js"))
        assertTrue(script.contains("bc.hover_annotations.v2"))
        assertTrue(script.contains("var conceptId = String(row.concept_id || '')"))
        assertTrue(script.contains("conceptId: conceptId"))
        assertTrue(script.contains("ItemEvents.tooltip"))
    }

    @Test
    fun `bundled Threads exposes optional owned lessons and no obsolete dodge teaching`() {
        val jar = root.resolve("mods/better-content-threads-1.1.0.jar")
        ZipFile(jar.toFile()).use { zip ->
            fun json(path: String) = zip.getInputStream(zip.getEntry(path)).bufferedReader().use { reader ->
                jacksonObjectMapper().readTree(reader)
            }
            val lessons = json("assets/better_content_threads/loading_briefs/catalogue.json")
            val threads = json("data/better_content_threads/threads/catalogue.json")
            assertEquals("bc.loading_briefs.v3", lessons.path("schema").asText())
            assertEquals(16, lessons.path("briefs").size())
            val concepts = threads.path("threads").associate {
                it.path("id").asText() to it.path("concept_id").asText()
            }
            lessons.path("briefs").forEach { lesson ->
                assertTrue(lesson.path("concept_id").asText().matches(Regex("[a-z0-9_.]{3,80}")))
                assertTrue(lesson.path("owner").asText().isNotBlank())
                lesson.path("related_thread").asText().takeIf(String::isNotEmpty)?.let { related ->
                    assertEquals(lesson.path("concept_id").asText(), concepts[related], lesson.path("id").asText())
                }
            }
            val movement = lessons.path("briefs").first { it.path("id").asText() == "movement" }.path("body").asText()
            assertTrue(movement.contains("directional double-tap dodging is disabled"))
            assertTrue(!movement.contains("double taps also dodge"))
        }

        val client = ZipFile(jar.toFile()).use { zip ->
            val entry = zip.getEntry("com/bettercontent/threads/ThreadClient.class")
            zip.getInputStream(entry).readBytes().toString(Charsets.ISO_8859_1)
        }
        assertTrue(!client.contains("keepReading"))
    }

    private fun exactTargets(): Set<String> = registry.path("annotations").flatMap { row ->
        val selector = row.path("selector")
        when {
            selector.has("item") -> listOf(selector.path("item").asText())
            selector.has("items") -> selector.path("items").map { it.asText() }
            else -> emptyList()
        }
    }.toSet()

    private fun tagTargets(): Set<String> = registry.path("annotations")
        .map { it.path("selector") }
        .filter { it.has("tag") }
        .map { it.path("tag").asText() }
        .toSet()
}
