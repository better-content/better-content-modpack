package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.extension.RegisterExtension
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.security.MessageDigest
import java.util.zip.ZipFile

@Tag("candidate")
@TestMethodOrder(OrderAnnotation::class)
class CandidateContractTest {
    companion object {
        @JvmField
        @RegisterExtension
        val evidence = EvidenceExtension("candidate")

        lateinit var pair: CandidatePair

        @JvmStatic
        @BeforeAll
        fun locate() {
            pair = CandidateLocator.locate(TestConfig.load().root)
            evidence.run.event("candidate_selected", mapOf(
                "client" to pair.client.toString(), "server" to pair.server.toString(),
                "client_sha256" to pair.clientSha256, "server_sha256" to pair.serverSha256,
            ))
        }
    }

    @Test
    @Order(1)
    fun candidatesShareOneReleaseAndHaveStableHashes() {
        assertEquals(pair.release, pair.client.parent.parent)
        assertEquals(pair.release, pair.server.parent.parent)
        assertEquals(64, pair.clientSha256.length)
        assertEquals(64, pair.serverSha256.length)
    }

    @Test
    @Order(2)
    fun serverArchiveHasProductionDefaults() {
        val root = ZipContracts.soleTopLevelDirectory(pair.server)
        val eula = ZipContracts.entryText(pair.server, "$root/eula.txt")
        val properties = ZipContracts.entryText(pair.server, "$root/server.properties")
        val jvm = ZipContracts.entryText(pair.server, "$root/user_jvm_args.txt")
        assertEquals("eula=false\n", eula)
        assertTrue("online-mode=true" in properties)
        assertTrue("server-port=25565" in properties)
        assertTrue("level-type=minecraft\\:normal" in properties || "level-type=minecraft:normal" in properties)
        assertTrue("-Xms4G" in jvm && "-Xmx16G" in jvm)
    }

    @Test
    @Order(3)
    fun candidatesDoNotShipGeneratedRuntimeEvidence() {
        ZipFile(pair.server.toFile()).use { archive ->
            assertFalse(archive.entries().asSequence().any { "/generated/runtime-dumps/" in "/${it.name}" })
        }
        ZipFile(pair.client.toFile()).use { archive ->
            assertFalse(archive.entries().asSequence().any { "generated/runtime-dumps" in it.name })
        }
    }

    @Test
    @Order(4)
    fun contractChecksDoNotMutateCandidates() {
        assertEquals(pair.clientSha256, Hashes.sha256(pair.client))
        assertEquals(pair.serverSha256, Hashes.sha256(pair.server))
    }

    @Test
    @Order(5)
    fun taczClientManifestServerManifestsAndGunPacksAreOneToOneAndHashConsistent() {
        val root = TestConfig.load().root
        val canonical = TaczFixtureSupport.canonical(root)
        val clientManifest = jacksonObjectMapper().readTree(ZipContracts.entryText(pair.client, "manifest.json"))
        val occurrences = clientManifest.path("files").map { file ->
            file.path("projectID").asLong() to file.path("fileID").asLong()
        }.filter { it in canonical.keys }.groupingBy { it }.eachCount()
        assertEquals(canonical.keys, occurrences.keys)
        assertTrue(occurrences.values.all { it == 1 })

        val serverManifests = TaczFixtureSupport.serverCandidateManifests(pair.server)
        assertEquals(canonical.keys, serverManifests.keys)
        val top = ZipContracts.soleTopLevelDirectory(pair.server)
        ZipFile(pair.server.toFile()).use { archive ->
            val artifacts = archive.entries().asSequence()
                .filter { it.name.startsWith("$top/tacz/") && it.name.endsWith(".zip") && !it.isDirectory }
                .map { it.name.substringAfterLast('/') }
                .toSet()
            assertEquals(serverManifests.values.map { it.filename }.toSet(), artifacts)
            serverManifests.values.forEach { manifest ->
                val entry = archive.getEntry("$top/tacz/${manifest.filename}")
                    ?: error("server candidate is missing TACZ artifact ${manifest.filename}")
                val algorithm = when (manifest.hashFormat.lowercase().replace("-", "")) {
                    "sha1" -> "SHA-1"
                    "sha256" -> "SHA-256"
                    "sha512" -> "SHA-512"
                    else -> manifest.hashFormat
                }
                val digest = MessageDigest.getInstance(algorithm)
                archive.getInputStream(entry).use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                assertEquals(manifest.hash, digest.digest().joinToString("") { "%02x".format(it) })
            }
        }
    }
}
