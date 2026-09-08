package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

@Tag("fast")
class ClientLogCompatibilityContractTest {
    @Test
    fun rapidDimensionTransitionRecoveryNoiseIsSuppressedBeforeTheStrictAudit(@TempDir root: Path) {
        val repository = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val logBegone = repository.resolve("config/logbegone.toml").toFile().readText()
        val phrases = listOf(
            "Detected ParCool Limitation is not synced. Sending synchronization request...",
            "Received passengers for unknown entity",
            "Stop: Invalid name parameter.",
        )
        phrases.forEach { phrase ->
            assertEquals(1, Regex(Regex.escape("\"$phrase\"")).findAll(logBegone).count())
        }

        val rawWarnings = root.resolve("raw-client-transition-warnings.log").also {
            it.writeText(
                "[Render thread/WARN] [com.alrex.parcool.ParCool]: ${phrases[0]}\n" +
                    "[Render thread/WARN] [net.minecraft.client.multiplayer.ClientPacketListener]: ${phrases[1]}\n" +
                    "[Render thread/ERROR] [com.mojang.blaze3d.audio.OpenAlUtil]: ${phrases[2]}\n",
            )
        }
        assertEquals(3, LogPolicy.findings(listOf(rawWarnings)).size)
    }
}
