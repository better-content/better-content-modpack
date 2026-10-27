package com.bettercontent.tests

import com.bettercontent.tests.release.validateSmokeEvidence
import com.bettercontent.tests.release.releaseDirectories
import com.bettercontent.tests.release.locateNewRelease
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.Duration

@Tag("fast")
class ReleaseResultGateTest {
    @TempDir lateinit var root: Path
    private val mapper = jacksonObjectMapper()

    private fun pair(): CandidatePair {
        val client = Files.writeString(root.resolve("client.zip"), "client fixture")
        val server = Files.writeString(root.resolve("server.zip"), "server fixture")
        return CandidatePair(root, client, server, Hashes.sha256(client), Hashes.sha256(server))
    }

    private fun evidence(pair: CandidatePair, run: String = "fresh", status: String = "passed", tier: String = "smoke"): Path =
        root.resolve("run.json").also {
            mapper.writeValue(it.toFile(), mapOf("run_id" to run, "status" to status, "tier" to tier,
                "candidates" to pair.document()))
        }

    private fun release(name: String): Path {
        val directory = root.resolve("dist/$name")
        Files.createDirectories(directory.resolve("client"))
        Files.createDirectories(directory.resolve("server"))
        Files.writeString(directory.resolve("client/better-content.zip"), "client-$name")
        Files.writeString(directory.resolve("server/better-content.zip"), "server-$name")
        return directory.toAbsolutePath()
    }

    @Test fun freshSelectionRetainsAndNeverSelectsOlderReleases() {
        val old = release("older")
        val partial = Files.createDirectories(root.resolve("dist/previous-failed"))
        Files.writeString(partial.resolve("failure.log"), "retained")
        val previous = releaseDirectories(root)
        val fresh = release("new")
        val selected = locateNewRelease(root, previous)
        assertEquals(fresh.resolve("client/better-content.zip"), selected.client)
        assertEquals("client-older", Files.readString(old.resolve("client/better-content.zip")))
        assertEquals("retained", Files.readString(partial.resolve("failure.log")))
    }

    @Test fun freshSelectionRejectsNoNewPartialAndAmbiguousOutputs() {
        release("older")
        val previous = releaseDirectories(root)
        assertThrows(IllegalArgumentException::class.java) { locateNewRelease(root, previous) }
        val partial = Files.createDirectories(root.resolve("dist/new/client"))
        Files.writeString(partial.resolve("better-content.zip"), "partial")
        assertThrows(IllegalArgumentException::class.java) { locateNewRelease(root, previous) }
        release("new")
        release("another-new")
        assertThrows(IllegalArgumentException::class.java) { locateNewRelease(root, previous) }
    }

    @Test fun actualPackagerFailureCannotPrunePreviousArtifactsOrEvidence() {
        val repo = Path.of(System.getProperty("bc.repo.root"))
        Files.copy(repo.resolve("package.sh"), root.resolve("package.sh"))
        Files.writeString(root.resolve("pack.toml"), "name = \"fixture\"\nversion = \"build-1\"\n")
        val old = release("build-1")
        val original = Files.readAllBytes(old.resolve("client/better-content.zip"))
        val partial = Files.createDirectories(root.resolve("dist/previous-failed"))
        Files.writeString(partial.resolve("failure.log"), "retain on failure")
        val bin = Files.createDirectories(root.resolve("bin"))
        Files.writeString(bin.resolve("packwiz"), "#!/bin/sh\nexit 71\n").toFile().setExecutable(true)
        ManagedProcess("packager-fixture", listOf("bash", root.resolve("package.sh").toString(), "dist"), root,
            root.resolve("packager.log"), mapOf("PATH" to "$bin:${System.getenv("PATH")}")).use {
            assertEquals(71, it.waitForExit(Duration.ofSeconds(10)))
        }
        assertArrayEquals(original, Files.readAllBytes(old.resolve("client/better-content.zip")))
        assertEquals("server-build-1", Files.readString(old.resolve("server/better-content.zip")))
        assertEquals("retain on failure", Files.readString(partial.resolve("failure.log")))
    }

    @Test fun matchingFreshSmokeAuthorizesExactPair() {
        val pair = pair()
        val started = Instant.now().minusSeconds(1)
        assertDoesNotThrow { validateSmokeEvidence(evidence(pair), "fresh", pair, started) }
    }

    @Test fun missingFailedWrongRunAndWrongTierCannotAuthorize() {
        val pair = pair()
        val started = Instant.now().minusSeconds(1)
        assertThrows(IllegalArgumentException::class.java) {
            validateSmokeEvidence(root.resolve("missing.json"), "fresh", pair, started)
        }
        listOf(Triple("fresh", "failed", "smoke"), Triple("old", "passed", "smoke"),
            Triple("fresh", "passed", "soak")).forEach { (run, status, tier) ->
            assertThrows(IllegalArgumentException::class.java) {
                validateSmokeEvidence(evidence(pair, run, status, tier), "fresh", pair, started)
            }
        }
    }

    @Test fun optionalSoakRequiresItsOwnMatchingFreshResult() {
        val pair = pair()
        val started = Instant.now().minusSeconds(1)
        assertThrows(IllegalArgumentException::class.java) {
            validateSmokeEvidence(evidence(pair), "fresh", pair, started, "soak")
        }
        assertDoesNotThrow { validateSmokeEvidence(evidence(pair, tier = "soak"), "fresh", pair, started, "soak") }
    }

    @Test fun staleEvidenceCannotAuthorize() {
        val pair = pair()
        val path = evidence(pair)
        Files.setLastModifiedTime(path, FileTime.from(Instant.EPOCH))
        assertThrows(IllegalArgumentException::class.java) { validateSmokeEvidence(path, "fresh", pair, Instant.now()) }
    }

    @Test fun changedHashOrDifferentArtifactPathCannotAuthorize() {
        val pair = pair()
        val path = evidence(pair)
        val alternate = Files.writeString(root.resolve("alternate.zip"), "client fixture")
        assertThrows(IllegalArgumentException::class.java) {
            validateSmokeEvidence(path, "fresh", pair.copy(client = alternate), Instant.EPOCH)
        }
        Files.writeString(pair.client, "changed after Smoke")
        assertThrows(IllegalArgumentException::class.java) { validateSmokeEvidence(path, "fresh", pair, Instant.EPOCH) }
    }
}
