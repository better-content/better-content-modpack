package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class PlaytestDefaultsContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))

    @Test
    fun middle_mouse_targets_installed_native_sort_providers_without_obsolete_inventorysorter() {
        val options = Files.readAllLines(root.resolve("options.txt"))
        for (key in listOf("quark.keybind.sort_player", "quark.keybind.sort_container", "key.sophisticatedcore.sort")) {
            assertEquals("key_$key:key.mouse.middle", options.single { it.startsWith("key_$key:") })
        }
        assertFalse(options.any { it.startsWith("key_key.inventorysorter.sort:") })
        val quark = Files.readString(root.resolve("config/quark-common.toml"))
        assertTrue(quark.contains("\"Inventory Sorting\" = true"))
        assertTrue(quark.contains("\"Enable Player Inventory\" = true"))
    }

    @Test
    fun recommended_controls_are_authored_directly_in_options() {
        val options = Files.readAllLines(root.resolve("options.txt"))
        val bindings = mapOf(
            "key.parcool.FastRun" to "key.keyboard.left.shift",
            "key.parcool.ClingToCliff" to "key.mouse.5",
            "key.parcool.Vault" to "key.mouse.5",
            "key.parcool.RideZipline" to "key.mouse.5",
            "key.parcool.HangDown" to "key.mouse.5",
            "key.parcool.WallSlide" to "key.mouse.5",
            "ping-wheel.key.ping-location" to "key.mouse.4",
            "key.epicfight.dodge" to "key.keyboard.unknown",
            "key.epicfight.lock_on" to "key.keyboard.caps.lock",
            "key.epicfight.lock_on_shift_freely" to "key.keyboard.caps.lock:SHIFT",
            "key.rotavision.rotate" to "key.keyboard.r:ALT",
            "key.rotavision.toggle" to "key.keyboard.t:ALT",
            "key.relics.ability_list" to "key.keyboard.a:ALT",
            "key.moreartifacts.eye.teleport" to "key.keyboard.p:ALT",
            "quark.keybind.lock_rotation" to "key.keyboard.unknown",
        )
        for ((key, binding) in bindings) {
            assertEquals("key_$key:$binding", options.single { it.startsWith("key_$key:") })
        }
    }

    @Test
    fun ordinary_emi_keeps_favorites_in_native_subpanel_without_duplicate_page() {
        val config = Files.readString(root.resolve("config/emi.css"))
        assertTrue(config.contains("right-sidebar-subpanels: favorites 3;"))
        val pages = Regex("right-sidebar-pages: ([^;]+);").find(config)!!.groupValues[1]
        assertEquals(listOf("index", "craftables"), pages.split(',').map(String::trim))
        assertFalse(pages.contains("favorites"), "Pinned EMI removes subpanels whose type is also a page")
    }

    @Test
    fun personal_marker_is_never_authored_into_any_affected_rbp_definition() {
        Files.walk(root.resolve("config/rbp/block_definitions")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".toml") }.forEach {
                assertFalse(Files.readString(it).contains("better_spawns:respawn_marker"), "Marker entered affected definition $it")
            }
        }
        val ordinary = Files.readString(root.resolve("config/rbp/block_definitions/obsidian.toml"))
        assertTrue(ordinary.contains("minecraft:crying_obsidian"))
        assertTrue(ordinary.contains("Enabled = true"))
    }
}
