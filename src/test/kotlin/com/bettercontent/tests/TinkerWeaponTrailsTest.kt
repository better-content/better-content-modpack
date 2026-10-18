package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Authored-resource contract only; blade alignment still needs native visual review. */
@Tag("fast")
class TinkerWeaponTrailsTest {
    private val mapper = jacksonObjectMapper()
    private val namespaces = setOf(
        "tconstruct", "additionalweaponry", "construct_arsenal", "tinker_rapier",
        "tinkers_battle_spades", "tinkers_katanas", "tinkers_things", "tinkersweaponry"
    )
    private val excluded = setOf(
        "epicfight:fist", "epicfight:bow", "epicfight:crossbow",
        "epicfighttinkercompat:longbow", "epicfighttinkercompat:crossbow"
    )

    @Test fun everyMappedMeleeToolHasACompleteTrailWithoutReplacingItsRenderer() {
        var checked = 0
        for (namespace in namespaces) {
            Files.list(Path.of("kubejs/data/$namespace/capabilities/weapons")).use { paths ->
                paths.filter { it.toString().endsWith(".json") }.forEach { capability ->
                    val type = mapper.readTree(capability.toFile())["type"].asText()
                    val skin = Path.of("kubejs/assets/$namespace/item_skins/${capability.fileName}")
                    if (type in excluded) {
                        assertFalse(Files.exists(skin), "No blade ribbon for ranged/fist item: $skin")
                    } else {
                        assertTrue(Files.isRegularFile(skin), "Missing melee trail: $skin")
                        val root = mapper.readTree(skin.toFile())
                        // Do not replace TCon's dynamic model, offhand transforms or sheath handling.
                        assertEquals(setOf("trail"), root.fieldNames().asSequence().toSet(), skin.toString())
                        val trail = root["trail"]
                        for (key in listOf("begin_pos", "end_pos")) {
                            val vector = trail[key]
                            assertTrue(vector.isArray && vector.size() == 3, "$skin $key")
                            vector.forEach { assertTrue(it.isNumber && it.asDouble().isFinite(), "$skin $key") }
                        }
                        assertNotEquals(trail["begin_pos"], trail["end_pos"], "Nonzero blade span: $skin")
                        assertTrue(trail["color"].isArray && trail["color"].size() == 3)
                        trail["color"].forEach { assertTrue(it.isIntegralNumber && it.asInt() in 0..255) }
                        assertTrue(trail["lifetime"].asInt() in 1..10, skin.toString())
                        assertTrue(trail["interpolations"].asInt() in 1..10, skin.toString())
                        assertEquals("epicfight:textures/particle/swing_trail.png", trail["texture_path"].asText())
                        assertEquals("epicfight:swing_trail", trail["particle_type"].asText())
                        checked++
                    }
                }
            }
        }
        assertTrue(checked >= 38, "Keep coverage of the existing melee integrations")
    }

    @Test fun bladeAndHeadGeometryIsNotOneUniversalRibbon() {
        fun span(namespace: String, item: String): Double {
            val trail = mapper.readTree(Path.of("kubejs/assets/$namespace/item_skins/$item.json").toFile())["trail"]
            return kotlin.math.abs(trail["end_pos"][2].asDouble() - trail["begin_pos"][2].asDouble())
        }
        assertTrue(span("tconstruct", "dagger") < span("tconstruct", "sword"))
        assertTrue(span("tconstruct", "sword") < span("tconstruct", "cleaver"))
        assertTrue(span("tconstruct", "hand_axe") < span("tconstruct", "broad_axe"))
        assertTrue(span("tconstruct", "dagger") < span("tinker_rapier", "rapier_tic"))
    }
}
