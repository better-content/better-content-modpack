package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

@Tag("fast")
class HookLootSourceContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))

    @Test
    fun `soap prototype is loot only and replaces wooden hook as iron recipe input`() {
        val crafting = Files.readString(root.resolve("kubejs/server_scripts/utility/hooks_drones_gates.js"))
        val loot = Files.readString(root.resolve("kubejs/server_scripts/utility/21_starter_hook_loot.js"))
        val tables = Regex("'((?:minecraft:chests/)[^']+)'\\s*:\\s*([0-9.]+)")
            .findAll(loot)
            .associate { it.groupValues[1] to it.groupValues[2].toDouble() }

        assertTrue(crafting.contains("event.remove({ output: 'rehooked:wood_hook' })"))
        assertFalse(crafting.contains("kubejs:rehooked/wood_hook_post_seared"))
        assertTrue(crafting.contains("H: 'better_content_fixes:soap_on_a_rope'"))
        assertTrue(loot.contains("Item.of("))
        listOf("soap_on_a_rope", "block_and_tackle", "grapnel_bundle", "climbing_vine", "ratchet_reel", "anglers_gaff")
            .forEach { assertTrue(loot.contains("$it:"), "missing loot placement for $it") }
        assertEquals(
            setOf(
                "minecraft:chests/abandoned_mineshaft",
                "minecraft:chests/simple_dungeon",
                "minecraft:chests/shipwreck_supply",
                "minecraft:chests/ruined_portal",
                "minecraft:chests/village/village_toolsmith",
                "minecraft:chests/village/village_taiga_house",
            ),
            tables.keys,
        )
        assertTrue(tables.values.all { it in 0.05..0.07 })
        assertTrue(loot.contains("!Platform.isLoaded('rehooked') || !Platform.isLoaded('better_content_fixes')"))
    }

    @Test
    fun `six intro prototypes have loot placement and accurate bounded profile lessons`() {
        val names = listOf("soap_on_a_rope", "block_and_tackle", "grapnel_bundle", "climbing_vine", "ratchet_reel", "anglers_gaff")
        val loot = Files.readString(root.resolve("kubejs/server_scripts/utility/21_starter_hook_loot.js"))
        val annotations = jacksonObjectMapper().readTree(root.resolve("kubejs/config/hover_annotations.json").toFile())
            .path("annotations").filter { row -> names.any { row.path("selector").path("item").asText() == "better_content_fixes:$it" } }
            .associateBy { it.path("selector").path("item").asText().substringAfter(':') }
        assertEquals(names.toSet(), annotations.keys)
        names.forEach { name ->
            assertTrue(Regex("$name\\s*:").containsMatchIn(loot), "missing loot placement for $name")
            val annotation = annotations.getValue(name)
            assertEquals("better_content_fixes", annotation.path("required_mod").asText())
            assertEquals("mobility.grappling", annotation.path("domain").asText())
            assertTrue(annotation.path("owner").asText().contains("21_starter_hook_loot.js"))
            assertTrue(annotation.path("lines").size() > 0)
        }
        val profileSource = Files.readString(root.resolve("../mod_source/better-content-fixes/src/main/java/com/bettercontent/bettercontentfixes/compat/rehooked/IntroHookProfile.java").normalize())
        val profilePattern = Regex("""new IntroHookProfile\("([a-z_]+)", "[a-z_]+", (\d+), ([0-9.]+)F, ([0-9.]+)F, ([0-9.]+)F\)""")
        val profiles = profilePattern.findAll(profileSource).associate { match ->
            val (name, count, range, lineSpeed, pullSpeed) = match.destructured
            name to listOf(count.toInt(), range.toFloat().toInt(), lineSpeed.toFloat().toInt(), pullSpeed.toFloat().toInt())
        }
        assertEquals(names.toSet(), profiles.keys)
        val behaviorSource = Files.readString(root.resolve("../mod_source/better-content-fixes/src/main/java/com/bettercontent/bettercontentfixes/compat/rehooked/IntroHookBehaviorPolicy.java").normalize())
        assertTrue(behaviorSource.contains("RATCHET_PULL_TICKS = 3"))
        assertTrue(behaviorSource.contains("RATCHET_HOLD_TICKS = 2"))
        assertTrue(behaviorSource.contains("RATCHET_PERIOD_TICKS = RATCHET_PULL_TICKS + RATCHET_HOLD_TICKS"))
        assertTrue(annotations.getValue("ratchet_reel").path("lines").toString().contains("three ticks, then holds for two, repeating every five ticks"))
        assertTrue(behaviorSource.contains("VINE_FOLIAGE_PULL_FACTOR = 1.0D"))
        assertTrue(behaviorSource.contains("VINE_NON_FOLIAGE_PULL_FACTOR = 0.65D"))
        val vineCopy = annotations.getValue("climbing_vine").path("lines").toString()
        assertTrue(vineCopy.contains("Full pull on leaves; 65% elsewhere"))
        assertTrue(behaviorSource.contains("GRAPNEL_EDGE_CATCH_RADIUS = 0.2D"))
        assertTrue(annotations.getValue("grapnel_bundle").path("lines").toString().contains("solid edges within 0.2 blocks"))
        assertTrue(behaviorSource.contains("GAFF_BOARDING_DISTANCE = 2.5D"))
        assertTrue(annotations.getValue("anglers_gaff").path("lines").toString().contains("Hooks boats; pull within 2.5 blocks to board"))
        assertTrue(behaviorSource.contains("SOAP_VERTICAL_PULL_FACTOR = 0.55D"))
        assertTrue(annotations.getValue("soap_on_a_rope").path("lines").toString().contains("Softer vertical pull guides surface contact"))
        profiles.forEach { (name, profile) ->
            val (count, range, lineSpeed, pullSpeed) = profile
            val countCopy = when (count) {
                1 -> "One hook;"
                2 -> "Up to two hooks;"
                else -> "Up to $count hooks;"
            }
            val statsCopy = "$range-block range, line speed $lineSpeed, and pull speed $pullSpeed"
            val copy = annotations.getValue(name).path("lines").toString()
            assertTrue(copy.contains(countCopy), "annotation capacity drifted for $name")
            assertTrue(copy.contains(statsCopy), "annotation stats drifted for $name")
            assertFalse(copy.contains("slippery", ignoreCase = true))
        }
    }

    @Test
    fun `all six ReHooked variants have item-local guidance and retain their tiered recipe routes`() {
        val hooks = listOf("iron", "diamond", "blaze", "ender", "red")
        val annotations = jacksonObjectMapper().readTree(
            root.resolve("kubejs/config/hover_annotations.json").toFile(),
        ).path("annotations").filter { row ->
            row.path("selector").path("item").asText().startsWith("rehooked:")
        }.associateBy { it.path("selector").path("item").asText() }
        val recipes = Files.readString(root.resolve("kubejs/server_scripts/utility/hooks_drones_gates.js"))
        val pin = Files.readString(root.resolve("mods/rehooked.pw.toml"))

        assertEquals(hooks.size, annotations.size)
        assertTrue(pin.contains("rehooked-1.8.3-1.20.1.jar"))
        assertTrue(pin.contains("e4fe79c242ee562364afc7e90d280d56c068ba21"))
        hooks.forEach { variant ->
            val item = "rehooked:${variant}_hook"
            val annotation = annotations[item] ?: error("Missing item-local guidance for $item")
            assertEquals("rehooked", annotation.path("required_mod").asText())
            assertEquals("mobility.grappling", annotation.path("domain").asText())
            assertTrue(annotation.path("lines").any { line ->
                line.asText().contains("fire", ignoreCase = true) ||
                    line.asText().contains("hook", ignoreCase = true)
            }, "guidance should identify the hook action for $item")
            assertTrue(recipes.contains("'$item'"), "missing authored tier recipe for $item")
            assertEquals("mods/rehooked.pw.toml", annotation.path("owner").asText())
        }
        assertTrue(annotations.getValue("rehooked:iron_hook").path("lines").toString().contains("two hooks"))
        val redConfig = Files.readString(root.resolve("defaultconfigs/rehooked-server.toml"))
            .substringAfter("[hook_stats.red]")
            .substringBefore("[hook_stats.blaze]")
        assertTrue(redConfig.contains("count = 3"))
        assertTrue(redConfig.contains("creativeFlight = false"))
        assertTrue(annotations.getValue("rehooked:red_hook").path("lines").toString().contains("creative flight is disabled", ignoreCase = true))
    }
}
