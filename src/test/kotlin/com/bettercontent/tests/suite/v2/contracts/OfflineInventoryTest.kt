package com.bettercontent.tests.suite.v2.contracts

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OfflineInventoryTest {
    @TempDir lateinit var root: Path
    private val lock = """{"schema":"bc.client_artifacts.v1","artifacts":[{"path":"mods/remote.jar","url":"https://example.invalid/remote.jar","hash_format":"sha256","hash":"${"a".repeat(64)}"}]}"""
    private fun zipped(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip -> entries.forEach { (name, data) -> zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() } }
    }.toByteArray()
    private fun candidate(side: String, vararg entries: Pair<String, ByteArray>): Path = root.resolve("$side.zip").also {
        java.nio.file.Files.write(it, zipped(*entries))
    }

    @Test fun exactArchiveAndNestedModJsonAreBoundButRemoteArtifactIsOnlyADeclaration() {
        val jar = zipped("META-INF/mods.toml" to "modLoader=\"javafml\"".toByteArray(),
            "data/demo/recipes/item.json" to "{\"type\":\"minecraft:crafting_shapeless\"}".toByteArray())
        val client = candidate("client", "better-content-artifacts.json" to lock.toByteArray(), "mods/local.jar" to jar)
        val server = candidate("server", "mods/local.jar" to jar)
        val inventory = inspectCandidate(client, server)
        assertEquals("bc.playtest_offline_inventory.v2", inventory.path("schema").asText())
        assertEquals(2, inventory.path("members").count { it.path("path").asText() == "mods/local.jar" })
        assertEquals("mods/remote.jar", inventory.path("embedded_artifact_lock").path("artifacts")[0].path("path").asText())
        val nested = inventory.path("members").first { it.path("path").asText() == "mods/local.jar" }.path("nested")
        assertTrue(nested.any { it.path("path").asText() == "data/demo/recipes/item.json" && it.path("json").path("type").asText().isNotBlank() })
        assertFalse(inventory.path("members").any { it.path("path").asText() == "mods/remote.jar" })
    }

    @Test fun missingClientLockAndTraversalInsideJarFailClosed() {
        val server = candidate("server", "safe.txt" to byteArrayOf(1))
        val noLock = candidate("client", "safe.txt" to byteArrayOf(1))
        assertThrows(IllegalStateException::class.java) { inspectCandidate(noLock, server) }
        val evilJar = zipped("../escape.json" to "{}".toByteArray())
        val bad = candidate("client", "better-content-artifacts.json" to lock.toByteArray(), "mods/evil.jar" to evilJar)
        assertThrows(IllegalArgumentException::class.java) { inspectCandidate(bad, server) }
    }
}
