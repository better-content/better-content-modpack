package com.bettercontent.tests

import com.bettercontent.tests.release.providerBuildEnvironment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class ProviderStagingTest {
    @Test
    fun changedConsumerReceivesReusedProviderDirectory(@TempDir root: Path) {
        val staged = Files.createDirectories(root.resolve("staged-jars"))
        Files.writeString(staged.resolve("provider-1.0.jar"), "reused provider fixture")
        // An unchanged provider is staged without a sibling build/libs directory.
        val process = ProcessBuilder("sh", "-c", "test -f \"\$BC_CUSTOM_MOD_JAR_DIR/provider-1.0.jar\"")
            .directory(root.toFile())
            .apply { environment().putAll(providerBuildEnvironment(staged)) }.start()
        assertEquals(0, process.waitFor())
        assertTrue(Files.notExists(root.resolve("provider/build/libs")))
    }

    @Test
    fun explicitStagingReplacesAnyAmbientProviderDirectory(@TempDir root: Path) {
        val staging = root.resolve("unused/../staged-jars")
        assertEquals(mapOf("BC_CUSTOM_MOD_JAR_DIR" to root.resolve("staged-jars").toString()),
            providerBuildEnvironment(staging))
    }
}
