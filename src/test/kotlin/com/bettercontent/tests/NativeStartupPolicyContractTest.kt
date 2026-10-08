package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Source/format guards only: no claim that these checks establish native runtime ordering. */
@Tag("fast")
class NativeStartupPolicyContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
    private val scriptPath = "kubejs/server_scripts/policy/native_material_cost_prewarm.js"
    private fun read(path: String) = Files.readString(root.resolve(path))

    @Test
    fun `Collective optional update network feature is disabled using only its native key`() {
        val json = read("config/collective.json5").lineSequence()
            .filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        val config = jacksonObjectMapper().readTree(json)
        assertEquals(setOf("enableUpdateChecker"), config.fieldNames().asSequence().toSet())
        assertFalse(config.path("enableUpdateChecker").asBoolean(true))
    }

    @Test
    fun `native cache prewarm uses supported high priority and four exact sequential filters`() {
        val script = read(scriptPath)
        assertTrue(script.startsWith("// priority: 1000\n"))
        assertEquals(1, Regex("ServerEvents\\.recipes\\(").findAll(script).count())
        val filters = Regex("event\\.forEachRecipe\\(\\{ type: '([^']+)' }, prewarm\\)")
            .findAll(script).map { it.groupValues[1] }.toList()
        assertEquals(listOf(
            "tconstruct:table_casting_material", "tconstruct:basin_casting_material",
            "tconstruct:table_casting_composite", "tconstruct:basin_casting_composite",
        ), filters)
        assertEquals(4, Regex("event\\.forEachRecipe\\(").findAll(script).count())
        assertEquals(1, Regex("recipe\\.getOriginalRecipe\\(\\)").findAll(script).count())
        assertTrue(script.contains("recipe.getOriginalRecipe() == null"))
        assertTrue(script.contains("throw new Error("), "failed native decoding must not be reported as successful prewarming")
    }

    @Test
    fun `prewarm changes construction order but never recipes costs or native cache values`() {
        val script = read(scriptPath).lineSequence()
            .filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        listOf(
            "event.custom", "event.remove", "event.replace", "recipe.json", "recipe.merge", "recipe.set",
            "item_cost", "registerItemCost", "getDeclaredField", "Java.loadClass", "serialize", "parallel",
            "allowAsyncStreams", "console.warn", "console.error",
        ).forEach { assertFalse(script.contains(it), "forbidden prewarm mutation/workaround: $it") }
        val doc = read("docs/native-material-cost-prewarm.md")
        assertTrue(doc.contains("hypothesis"))
        assertTrue(doc.contains("not runtime-proven"))
    }
}
