package com.bettercontent.tests.suite.repositories

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
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
