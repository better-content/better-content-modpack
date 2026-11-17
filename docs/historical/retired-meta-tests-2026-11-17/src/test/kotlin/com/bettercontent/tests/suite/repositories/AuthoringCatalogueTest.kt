package com.bettercontent.tests.suite.repositories

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Authored-only source regressions; no test execution has been approved. */
class AuthoringCatalogueTest {
    @TempDir lateinit var root: Path
    private fun inputs(): Pair<ObjectNode, Path> {
        val historical = Path.of("src/test/resources/playtest/suite-v3-authoring.json")
        val catalogue = root.resolve("catalogue.json")
        Files.copy(historical, catalogue)
        val source = suiteMapper.readTree(catalogue.toFile())
        val selected = source.path("cases").first { it.path("id").asText() == "survival.food" }
        val manifest = suiteMapper.createObjectNode().put("purpose", "diagnostic")
        manifest.set<ArrayNode>("repositories", suiteMapper.createArrayNode().apply {
            source.path("owners").forEach { addObject().put("id", it.path("id").asText()) }
        })
        manifest.set<ArrayNode>("cases", suiteMapper.createArrayNode().apply {
            addObject().put("id", "survival.food").put("domain", "survival.food").apply {
                set<ArrayNode>("assertions", selected.path("required_assertions").deepCopy())
                set<ArrayNode>("required_capabilities", selected.path("required_capabilities").deepCopy())
                set<ArrayNode>("required_inventory_domains", suiteMapper.createArrayNode())
                set<ArrayNode>("target_bindings", suiteMapper.createArrayNode())
                putObject("implementation").put("path", selected.path("implementation_path").asText()).put("state", "authored")
            }
        })
        manifest.set<ArrayNode>("driver_bindings", suiteMapper.createArrayNode().apply {
            addObject().put("case_id", "survival.food").apply {
                set<ArrayNode>("required_actions", selected.path("required_capabilities").deepCopy())
            }
        })
        manifest.putObject("authoring_catalogue").put("path", catalogue.toString()).put("sha256", Hashes.sha256(catalogue))
        return manifest to catalogue
    }
    @Test fun `new registered onboarding row requires seven ordered actions and one reviewed input profile`() {
        val (manifest, catalogue) = inputs()
        val rows = suiteMapper.readTree(catalogue.toFile()).path("cases")
        assertEquals(206, rows.size())
        val onboarding = rows.single { it.path("id").asText() == "world.onboarding" }
        assertEquals(listOf("world.entry", "player.observe_onboarding.arm", "client.input_hold", "client.input_release",
            "client.input_status", "player.observe_onboarding.await", "player.snapshot"),
            onboarding.path("required_capabilities").map { it.asText() })
        assertEquals(listOf("1280x720@2", "1920x1080@3"),
            onboarding.path("supported_input_profiles").map { it.asText() })
        val active = manifest.path("cases")[0] as ObjectNode
        active.put("id", "world.onboarding").put("domain", "world.onboarding")
        active.set<ArrayNode>("assertions", onboarding.path("required_assertions").deepCopy())
        active.set<ArrayNode>("required_capabilities", onboarding.path("required_capabilities").deepCopy())
        active.putObject("implementation").put("path", onboarding.path("implementation_path").asText()).put("state", "authored")
        val binding = manifest.path("driver_bindings")[0] as ObjectNode
        binding.put("case_id", "world.onboarding")
        binding.set<ArrayNode>("required_actions", onboarding.path("required_capabilities").deepCopy())
        binding.putObject("parameters").putObject("onboarding").put("input_profile", "1280x720@2")
        manifest.putObject("profile").putArray("visual_profiles").add("1280x720@2").add("1920x1080@3")
        assertFalse(validateAuthoringCatalogue(root, manifest, setOf("world.onboarding"), false)
            .any { "onboarding needs exact seven" in it })
        binding.withArray("required_actions").remove(4)
        assertTrue(validateAuthoringCatalogue(root, manifest, setOf("world.onboarding"), false)
            .any { "onboarding needs exact seven" in it })
        binding.set<ArrayNode>("required_actions", onboarding.path("required_capabilities").deepCopy())
        (binding.path("parameters").path("onboarding") as ObjectNode).put("input_profile", "unreviewed")
        assertTrue(validateAuthoringCatalogue(root, manifest, setOf("world.onboarding"), false)
            .any { "reviewed client input profile" in it })
    }
    @Test fun `registered controls remain metadata only with exact bounded client chains and no review pins`() {
        val (manifest, catalogue) = inputs()
        val doc = suiteMapper.readTree(catalogue.toFile()) as ObjectNode
        val controls = doc.path("control_metadata") as ArrayNode
        assertEquals(setOf("control.travel.walk-route", "control.client.inventory-screen"),
            controls.map { it.path("id").asText() }.toSet())
        (controls.first { it.path("id").asText() == "control.travel.walk-route" } as ObjectNode)
            .put("reviewed_profile", "1280x720@2")
        Files.writeString(catalogue, suiteMapper.writeValueAsString(doc))
        (manifest.path("authoring_catalogue") as ObjectNode).put("sha256", Hashes.sha256(catalogue))
        assertTrue(validateAuthoringCatalogue(root, manifest, setOf("survival.food"), false)
            .any { "walk control metadata" in it })
    }
    @Test fun `dormant diagnostic row does not by itself block the full qualification roster`() {
        val (manifest, _) = inputs()
        val problems = validateAuthoringCatalogue(root, manifest, setOf("survival.food"), true)
        assertFalse(problems.any { "getter negative must remain" in it })
        assertTrue(problems.any { "qualification requires complete" in it }) // fixture selects only one of 206
    }
    @Test fun `getter negative is diagnostic-only empty assertion obligation outside 206 full cases`() {
        val (manifest, catalogue) = inputs()
        val doc = suiteMapper.readTree(catalogue.toFile())
        assertEquals(206, doc.path("cases").size())
        val negative = doc.path("diagnostic_obligations").single()
        assertEquals("control.mutation.external-getter-negative", negative.path("id").asText())
        assertTrue(negative.path("required_assertions").isEmpty)
        assertTrue(negative.path("required_inventory_domains").isEmpty)
        val selected = manifest.withArray("cases")[0] as ObjectNode
        selected.put("id", negative.path("id").asText()).put("layer", "campaign")
            .putArray("assertions")
        selected.putArray("required_inventory_domains")
        selected.set<ArrayNode>("required_capabilities", negative.path("required_capabilities").deepCopy())
        selected.putObject("implementation").put("state", "authored")
            .put("path", negative.path("implementation_path").asText())
        val binding = manifest.withArray("driver_bindings")[0] as ObjectNode
        binding.put("case_id", negative.path("id").asText()).put("driver_id", negative.path("id").asText())
        binding.set<ArrayNode>("required_actions", negative.path("required_capabilities").deepCopy())
        val selectedId = setOf(negative.path("id").asText())
        val diagnostic = validateAuthoringCatalogue(root, manifest, selectedId, false)
        assertFalse(diagnostic.any { "selected obligation missing" in it })
        assertTrue(diagnostic.any { "draft/unbound" in it || "unreviewed/incorrect diagnostic getter" in it })
        val qualifier = validateAuthoringCatalogue(root, manifest, selectedId, true)
        assertTrue(qualifier.any { "qualification requires complete catalogue of exactly the 206 full cases" in it })
        binding.putArray("required_actions").add("block_mutation.control_expose_states_v1").add("unreviewed.extra")
        assertTrue(validateAuthoringCatalogue(root, manifest, selectedId, false)
            .any { "diagnostic getter source, candidate, scope or single native action" in it })
    }
    @Test fun `missing or changed catalogue cannot authorize diagnostic`() {
        val (manifest, catalogue) = inputs()
        manifest.remove("authoring_catalogue")
        assertTrue(validateAuthoringCatalogue(root, manifest, setOf("survival.food"), false).any { "unbound" in it })
        manifest.putObject("authoring_catalogue").put("path", catalogue.toString()).put("sha256", "0".repeat(64))
        assertTrue(validateAuthoringCatalogue(root, manifest, setOf("survival.food"), false).any { "unbound" in it })
    }
    @Test fun `unbound draft rejects promotion even with exact selected driver actions`() {
        val (manifest, _) = inputs()
        val problems = validateAuthoringCatalogue(root, manifest, setOf("survival.food"), false)
        assertTrue(problems.any { "draft/unbound" in it })
        assertTrue(problems.any { "reviewed inventory scope" in it })
        assertTrue(problems.any { "target/acquisition/persistence" in it })
    }
    @Test fun `selected assertion divergence and missing repository test membership fail closed`() {
        val (manifest, _) = inputs()
        (manifest.path("cases")[0] as ObjectNode).putArray("assertions").add("survival.food.promise")
        (manifest.path("driver_bindings")[0] as ObjectNode).putArray("required_actions").add("unsupported.fake_action")
        val problems = validateAuthoringCatalogue(root, manifest, setOf("survival.food"), true)
        assertTrue(problems.any { "assertion contract" in it })
        assertTrue(problems.any { "native action contract" in it })
        assertTrue(problems.any { "zero exact expected test membership" in it })
        assertTrue(problems.any { "qualification requires complete" in it })
    }
}
