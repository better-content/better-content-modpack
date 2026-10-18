package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Cross-repository authored contract; runtime decisions are tested in the owning mod. */
@Tag("fast")
class SaulomonkSpawnContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root", ".")).toAbsolutePath().normalize()

    @Test fun distanceAnchorUsesTheActualOnboardingOwnerKeysAndPinnedNativeGate() {
        val owner = Files.readString(root.resolve("../mod_source/better-spawns/src/main/kotlin/com/bettercontent/betterspawns/respawn/PersonalRespawnSystem.kt"))
        val compatRoot = root.resolve("../mod_source/better-compat-fixes")
        val helper = Files.readString(compatRoot.resolve("src/main/java/com/bettercontent/bettercompatfixes/compat/SaulomonkSpawnDistance.java"))
        for (field in listOf("dim", "x", "y", "z")) {
            val key = "better_spawns:respawn_$field"
            assertTrue(owner.contains("\"$key\""), "Onboarding must still own $key")
            assertTrue(helper.contains("\"$key\""), "Compat must read the same $key")
        }
        assertTrue(owner.contains("player.persistentData.putString(RESPAWN_DIM_TAG, point.dim)"))
        assertTrue(owner.contains("player.persistentData.putInt(RESPAWN_X_TAG, point.x)"))
        assertTrue(owner.contains("player.persistentData.putInt(RESPAWN_Z_TAG, point.z)"))
        assertFalse(helper.contains("getRespawnPosition"), "Beds/vanilla spawn must not select the anchor")
        assertFalse(helper.contains("getSharedSpawnPos"), "World spawn must not select the anchor")
        val pin = Files.readString(root.resolve("mods/weapons-of-miracles.pw.toml"))
        assertTrue(pin.contains("version = \"nf0AyjKh\""))
        val build = Files.readString(compatRoot.resolve("build.gradle.kts"))
        assertTrue(build.contains("maven.modrinth:weapons-of-miracles:nf0AyjKh"))
        assertTrue(build.contains("Required pinned WoM 2.0.171 native Saulomonk fixture is missing"))
    }
}
