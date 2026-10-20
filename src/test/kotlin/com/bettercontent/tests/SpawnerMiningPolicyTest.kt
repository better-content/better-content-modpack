package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/** Minecraft-free pack ownership checks, not native mining acceptance. */
@Tag("fast")
class SpawnerMiningPolicyTest {
    private val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()

    @Test
    fun diamondTagAddsOnlyVanillaSpawnerWithoutReplacingUpstream() {
        val tag = jacksonObjectMapper().readTree(Files.readString(root.resolve("kubejs/data/minecraft/tags/blocks/needs_diamond_tool.json")))
        assertEquals(setOf("replace", "values"), tag.fieldNames().asSequence().toSet())
        assertFalse(tag.path("replace").asBoolean(true))
        assertEquals(listOf("minecraft:spawner"), tag.path("values").map { it.asText() })
        val pickaxe = jacksonObjectMapper().readTree(Files.readString(root.resolve("kubejs/data/minecraft/tags/blocks/mineable/pickaxe.json")))
        assertFalse(pickaxe.path("replace").asBoolean(true), "Upstream pickaxe classification must remain additive")
        assertFalse(Files.exists(root.resolve("kubejs/data/minecraft/loot_tables/blocks/spawner.json")), "No spawner collection loot override")
        Files.walk(root.resolve("kubejs/startup_scripts")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".js") }.forEach {
                assertFalse(Files.readString(it).contains("minecraft:spawner"), "No startup spawner hardness modification: $it")
            }
        }
    }

    @Test
    fun gateRemainsOwnedByCompatAndUsesNativeStackCapability() {
        val compat = root.parent.resolve("mod_source/better-compat-fixes")
        val source = Files.readString(compat.resolve("src/main/java/com/bettercontent/bettercompatfixes/compat/SpawnerMiningPolicy.java"))
        assertTrue(source.contains("state.is(Blocks.SPAWNER)"))
        assertTrue(source.contains("stack.isCorrectToolForDrops(state)"))
        assertTrue(source.contains("player.isCreative()"))
        assertFalse(source.contains("Items.DIAMOND_PICKAXE"))
        val build = Files.readString(compat.resolve("build.gradle.kts"))
        assertTrue(build.contains("from(authoredSpawnerTier) { into(\"data/minecraft/tags/blocks\") }"), "Native fixture must consume authored pack tag")
        assertTrue(Files.isRegularFile(compat.resolve("src/nativeFixture/java/com/bettercontent/bettercompatfixes/gametest/SpawnerMiningGameTests.java")))
        val manifest = jacksonObjectMapper().readTree(Files.readString(root.resolve("gradle/active-custom-mods.json")))
        val owner = manifest.path("mods").first { it.path("repository").asText() == "better-compat-fixes" }
        assertTrue(owner.path("verificationInputs").any { it.asText() == "kubejs/data/minecraft/tags/blocks/needs_diamond_tool.json" },
            "Source verification reuse must be invalidated when the pack-owned tier changes")
    }
}
