package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class ToggleCrouchAndCrawlDefaultsTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))

    @Test
    fun authored_defaults_toggle_crouch_and_put_toggle_crawl_on_z_without_same_chord_conflicts() {
        val options = Files.readAllLines(root.resolve("options.txt"))
        fun binding(id: String): String = options.single { it.startsWith("key_$id:") }.substringAfter(':')

        assertTrue(options.contains("toggleCrouch:true"))
        assertEquals("key.keyboard.z", binding("key.tacz.crawl.desc"))
        assertEquals("key.keyboard.semicolon", binding("key.ars_nouveau.previous_slot"))
        assertEquals("key.keyboard.grave.accent", binding("key.goety.wand"))
        assertEquals("key.keyboard.z:CONTROL", binding("key.hexerei.glasses_zoom"))
        assertEquals("key.keyboard.z:ALT", binding("key.sophisticatedbackpacks.toggle_upgrade_1"))
        assertEquals(1, options.count { it.startsWith("key_") && it.substringAfter(':') == "key.keyboard.z" })

        val tacz = Files.readString(root.resolve("config/tacz-client.toml"))
        assertTrue(Regex("(?m)^\\s*HoldToCrawl\\s*=\\s*false\\s*$").containsMatchIn(tacz))
    }
}
