package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Tag("fast")
class StoneCobbleCompatibilityContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
    private val script = root.resolve("kubejs/server_scripts/compat/retained/check__75_stone_cobble_tag_compat.js")

    @Test
    fun `actual recipe handler broadens only exact functional recipe ids`() {
        val fixture = root.resolve("src/test/resources/stone-cobble-compat-contract.cjs")
        val process = ProcessBuilder("node", fixture.toString(), script.toString()).redirectErrorStream(true).start()
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly().waitFor()
        assertTrue(finished, "Minecraft-free Node recipe-handler contract timed out")
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.exitValue(), output)
        val result = jacksonObjectMapper().readTree(output)
        assertTrue(result.path("functional_recipes").asInt() > 0)
        assertEquals(2 * result.path("functional_recipes").asInt(), result.path("replacements").asInt())
        assertTrue(result.path("protected_recipes").asInt() >= 25)
        assertTrue(result.path("furnace_preserved").asBoolean())
    }

    @Test
    fun `no active script restores blanket stone or cobblestone rewriting`() {
        val blanket = Regex("replaceInput\\s*\\(\\s*\\{\\s*}\\s*,\\s*['\"]minecraft:(?:stone|cobblestone)['\"]")
        Files.walk(root.resolve("kubejs/server_scripts")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".js") }.forEach { path ->
                assertFalse(blanket.containsMatchIn(Files.readString(path)), "blanket rock substitution in $path")
            }
        }
    }

    @Test
    fun `variant tags remain available for native functional tools and furnace`() {
        val tags = Files.readString(root.resolve("kubejs/server_scripts/compat/retained/check__30_stone_cobble_compat.js"))
        for (tag in listOf("forge:stone", "forge:cobblestone", "minecraft:stone_tool_materials", "minecraft:stone_crafting_materials")) {
            assertTrue(tags.contains("'$tag'"), "keep shared rock/tool tag $tag")
        }
        assertTrue(tags.contains("event.add('kubejs:furnace_materials', '#forge:stone')"))
        assertTrue(tags.contains("event.add('kubejs:furnace_materials', '#forge:cobblestone')"))
        assertTrue(tags.contains("'unearthed:cobbled_limestone'"))
        assertTrue(tags.contains("'natures_spirit:cobbled_travertine'"))
    }
}
