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

    @Test fun largeRealisticJarRemainsBoundedByEntryBudget() {
        // TConstruct in the active pack has 29,731 entries. Allow that real input,
        // but continue rejecting a JAR above the reviewed 32,000-entry ceiling.
        val client = candidate("client", "better-content-artifacts.json" to lock.toByteArray())
        fun jarWithEntries(count: Int) = zipped(*Array(count) { index ->
            "assets/demo/entry-$index.txt" to byteArrayOf()
        })
        val realistic = candidate("realistic", "mods/large.jar" to jarWithEntries(29_731))
        assertEquals(2, inspectCandidate(client, realistic).path("members").size())
        val oversized = candidate("oversized", "mods/large.jar" to jarWithEntries(32_001))
        assertThrows(IllegalArgumentException::class.java) { inspectCandidate(client, oversized) }
    }

    @Test fun documentedThirdPartyCommentIsDisclosedRatherThanCalledStrictJson() {
        val client = candidate("client", "better-content-artifacts.json" to lock.toByteArray())
        val jar = zipped("data/demo/recipes/commented.json" to "{ // resource comment\n\"type\":\"demo:recipe\", }".toByteArray())
        val server = candidate("server", "mods/commented.jar" to jar)
        val nested = inspectCandidate(client, server).path("members").last().path("nested").single()
        assertEquals("comments_or_trailing_commas", nested.path("json_syntax").asText())
        assertEquals("demo:recipe", nested.path("json").path("type").asText())
    }

    @Test fun invalidNestedDataJsonStillFailsWithItsExactArchivePath() {
        val client = candidate("client", "better-content-artifacts.json" to lock.toByteArray())
        val jar = zipped("data/demo/recipes/broken.json" to "{\"first\":1 \"second\":2}".toByteArray())
        val server = candidate("server", "mods/broken.jar" to jar)
        val failure = assertThrows(IllegalArgumentException::class.java) { inspectCandidate(client, server) }
        assertTrue(failure.message!!.contains("mods/broken.jar!/data/demo/recipes/broken.json"))
        val diagnostic = inspectCandidate(client, server, diagnostic = true)
        assertFalse(diagnostic.path("qualification_eligible").asBoolean(true))
        val invalid = diagnostic.path("invalid_data_json").single()
        assertEquals("mods/broken.jar", invalid.path("jar").asText())
        assertEquals("data/demo/recipes/broken.json", invalid.path("path").asText())
        val nested = diagnostic.path("members").last().path("nested").single()
        assertEquals("invalid_unparsed_diagnostic", nested.path("json_syntax").asText())
        assertTrue(nested.path("json").isMissingNode)
        assertEquals(nested.path("sha256").asText(), invalid.path("sha256").asText())
    }

    @Test fun aLookalikeDeepVoidJarCannotUseThePinnedResourceException() {
        val resource = "the_deep_void/weapon_attributes/penitent_cross.json"
        val overlay = "{\"attributes\":{}}".toByteArray()
        val jar = zipped("data/$resource" to "{\"id\":1 \"pitch\":2}".toByteArray())
        val client = candidate("client", "better-content-artifacts.json" to lock.toByteArray(),
            "overrides/kubejs/data/$resource" to overlay)
        val server = candidate("server", "better-content-server/mods/the_deep_void-1.98.1-forge-1.20.1.jar" to jar,
            "better-content-server/kubejs/data/$resource" to overlay)
        val failure = assertThrows(IllegalArgumentException::class.java) { inspectCandidate(client, server) }
        assertTrue(failure.message!!.contains("invalid data JSON"))
        assertTrue(failure.message!!.contains("data/$resource"))
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
