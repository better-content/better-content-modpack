package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class FireLifecycleSourceContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val mapper = jacksonObjectMapper()

    @Test
    fun `generated fire runtime has bounded spread and ecological recovery`() {
        val config = Files.readString(root.resolve("config/better_wildfire-common.toml"))
        val spread = value(config, "spreadChance").toDouble()
        val recovery = value(config, "groundRecoveryChance").toDouble()
        assertTrue(spread > 0.0 && spread <= 0.1)
        assertTrue(recovery > 0.0 && recovery < 1.0)
        assertTrue(value(config, "smolderTicks").toInt() >= 20)
        assertTrue(value(config, "maxIgnitionsPerTick").toInt() in 1..128)
        assertTrue(value(config, "playerRadius").toInt() >= 16)
    }

    @Test
    fun `new fire mod replaces Burnt and its old compatibility layer`() {
        val artifact = mapper.readTree(root.resolve("gradle/active-custom-mods.json").toFile())
            .path("mods").single { it.path("repository").asText() == "better-wildfire" }
        assertEquals("better_wildfire", artifact.path("modId").asText())
        assertEquals("better-wildfire-0.1.0.jar", artifact.path("artifact").asText())
        val bundled = root.resolve("mods/${artifact.path("artifact").asText()}")
        if (Files.isRegularFile(bundled)) {
            val packwiz = Files.readString(root.resolve("index.toml"))
            assertTrue(packwiz.contains("file = \"mods/${artifact.path("artifact").asText()}\""))
        }
        val packwiz = Files.readString(root.resolve("index.toml"))
        assertFalse(packwiz.contains("mods/burnt-basic.pw.toml"))
        assertFalse(packwiz.contains("mods/burnt-grass-compat-0.1.0.jar"))
        val namespaces = mapper.readTree(root.resolve("kubejs/config/crafting_policy.json").toFile()).path("namespaces")
        assertEquals("world", namespaces.path("better_wildfire").path("primary_role").asText())
        assertFalse(namespaces.has("burnt"))
        assertFalse(namespaces.has("burnt_grass_compat"))
        val hide = Files.readString(root.resolve("kubejs/client_scripts/policy/hide_fire_content.js"))
        assertTrue(hide.contains("@better_wildfire"))
        assertTrue(hide.contains("JEIEvents.hideItems"))
        assertTrue(hide.contains("EMIEvents.hideItems"))
    }

    private fun value(toml: String, key: String): String {
        val match = Regex("(?m)^\\s*$key\\s*=\\s*([^#\\r\\n]+)").find(toml)
        return requireNotNull(match) { "missing fire config value $key" }.groupValues[1].trim()
    }
}
