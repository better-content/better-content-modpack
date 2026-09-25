package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class DragonScaleEconomyContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))

    @Test
    fun `explosion damage cannot repeatedly create scales used by Elytra duplication`() {
        val tconConfig = Files.readString(root.resolve("config/tconstruct-common.toml"))
        val dropSetting = Regex("(?m)^\\s*drop_dragon_Scales\\s*=\\s*(true|false)\\s*$")
            .find(tconConfig)
        assertTrue(dropSetting != null, "pinned TCon configuration must retain the dragon-scale policy key")
        assertFalse(dropSetting!!.groupValues[1].toBoolean(), "explosion hits must not mint repeatable dragon scales")

        val dragonPolicy = Files.readString(root.resolve("kubejs/server_scripts/policy/closed_end_dragon_replacements.js"))
        assertTrue(dragonPolicy.contains("event.shaped('2x minecraft:elytra'"))
        assertTrue(dragonPolicy.contains("S: '#forge:scales/dragon', E: 'minecraft:elytra'"))
    }

    @Test
    fun `single Ender Dragon reward supplies TConstruct scale without restoring Quark Elytra duplication`() {
        val dragonPolicy = Files.readString(root.resolve("kubejs/server_scripts/policy/closed_end_dragon_replacements.js"))
        val elytraRemoval = dragonPolicy.indexOf("event.remove({ output: 'minecraft:elytra' })")
        val scaleExchange = dragonPolicy.indexOf("event.shapeless('tconstruct:dragon_scale', ['quark:dragon_scale'])")
        val replacement = dragonPolicy.indexOf("event.shaped('2x minecraft:elytra'")

        assertTrue(elytraRemoval >= 0, "native Quark Elytra duplication must be removed")
        assertTrue(scaleExchange > elytraRemoval, "only the Quark scale should become the bounded TConstruct scale input")
        assertTrue(replacement > scaleExchange, "the retained Elytra duplication recipe must stay a separate scale sink")
        assertTrue(dragonPolicy.contains("kubejs:dragon_ecology/tconstruct_scale_from_quark"))
    }
}
