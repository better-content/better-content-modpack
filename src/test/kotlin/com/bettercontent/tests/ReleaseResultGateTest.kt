package com.bettercontent.tests

import com.bettercontent.tests.release.validateSmokeEvidence
import com.bettercontent.tests.release.requireAmbientAttributionEnabled
import com.bettercontent.tests.release.validateOverworldTeleSoakReceipt
import com.bettercontent.tests.release.releaseDirectories
import com.bettercontent.tests.release.locateNewRelease
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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
            if (tier == "smoke") {
                Files.writeString(root.resolve("client.log"), "[client] real raw client log fixture\n")
                Files.writeString(root.resolve("server.log"), "[server] real raw server log fixture\n")
            }
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

    @Test fun emiRecipeScreenMixinWarningCannotAuthorizeSmoke() {
        val pair = pair()
        val receipt = evidence(pair)
        Files.writeString(root.resolve("client.log"),
            "[12:00:00] [Thread-35] [WARN] Mixin apply failed better_compat_fixes.mixins.json:emi.RecipeScreenMixin -> dev.emi.emi.screen.RecipeScreen\n")
        assertThrows(IllegalArgumentException::class.java) {
            validateSmokeEvidence(receipt, "fresh", pair, Instant.EPOCH)
        }
        Files.delete(root.resolve("client.log"))
        assertThrows(IllegalArgumentException::class.java) {
            validateSmokeEvidence(receipt, "fresh", pair, Instant.EPOCH)
        }
    }

    @Test fun kubeJsEmptyRecipeOutputOrMissingRawServerLogCannotAuthorizeSmoke() {
        val pair = pair()
        val receipt = evidence(pair)
        Files.writeString(root.resolve("server.log"),
            "[19:58:39] [main/ERROR] [KubeJS Server/]: progression/80_tech_palette.js#68: " +
                "Failed to create recipe for type 'kubejs:shaped': ItemStack 'result' can't be empty!\n")
        val malformed = assertThrows(IllegalArgumentException::class.java) {
            validateSmokeEvidence(receipt, "fresh", pair, Instant.EPOCH)
        }
        assertTrue(malformed.message.orEmpty().contains("KubeJS recipe has an empty result"))
        Files.delete(root.resolve("server.log"))
        val missing = assertThrows(IllegalArgumentException::class.java) {
            validateSmokeEvidence(receipt, "fresh", pair, Instant.EPOCH)
        }
        assertTrue(missing.message.orEmpty().contains("raw Smoke server log is missing"))
    }

    @Test fun ambientRateDisabledWarningCannotAuthorizeSmoke() {
        val pair = pair()
        val receipt = evidence(pair)
        Files.writeString(root.resolve("server.log"),
            "[Server thread/WARN] [AmbientRateSeam/]: Ambient rate attribution disabled: " +
                "unknown/interleaved WORLD: InventoryItemsTempModifier\n")
        val rejected = assertThrows(IllegalArgumentException::class.java) {
            validateSmokeEvidence(receipt, "fresh", pair, Instant.EPOCH)
        }
        assertTrue(rejected.message.orEmpty().contains("ambient temperature rate attribution disabled"))
    }

    @Test fun ambientRateWarningsInAnyNativeTierCannotAuthorizeRelease() {
        val serverLog = root.resolve("server.log")
        Files.writeString(serverLog, "[Server thread/WARN] Ambient rate attribution disabled: duplicate ambient RATE modifiers\n")
        val rejected = assertThrows(IllegalArgumentException::class.java) {
            requireAmbientAttributionEnabled(serverLog)
        }
        assertTrue(rejected.message.orEmpty().contains("ambient temperature rate attribution disabled"))
        Files.writeString(serverLog, "[Server thread/INFO] native source and ambient probes intact\n")
        assertDoesNotThrow { requireAmbientAttributionEnabled(serverLog) }
        Files.delete(serverLog)
        assertThrows(IllegalArgumentException::class.java) { requireAmbientAttributionEnabled(serverLog) }
        System.getenv("BC_VALIDATE_TELE_AMBIENT_DISABLED")?.let { prior ->
            assertThrows(IllegalArgumentException::class.java) {
                requireAmbientAttributionEnabled(Path.of(prior))
            }
        }
    }

    @Test fun suppliedExactArtifactSmokeEvidenceCanBeRecheckedReadOnly() {
        val good = System.getenv("BC_VALIDATE_SMOKE_GOOD") ?: return
        val bad = System.getenv("BC_VALIDATE_SMOKE_BAD") ?: return
        fun replay(path: Path) {
            val receipt = mapper.readTree(path.toFile())
            val candidates = receipt.path("candidates")
            val client = Path.of(candidates.path("client").asText())
            val server = Path.of(candidates.path("server").asText())
            val pair = CandidatePair(path.parent, client, server,
                candidates.path("client_sha256").asText(), candidates.path("server_sha256").asText())
            validateSmokeEvidence(path, receipt.path("run_id").asText(), pair, Instant.EPOCH)
        }
        assertDoesNotThrow { replay(Path.of(good)) }
        val rejected = assertThrows(IllegalArgumentException::class.java) { replay(Path.of(bad)) }
        assertTrue(rejected.message.orEmpty().contains("KubeJS recipe has an empty result"))
        System.getenv("BC_VALIDATE_SMOKE_AMBIENT_DISABLED")?.let { prior ->
            val ambient = assertThrows(IllegalArgumentException::class.java) { replay(Path.of(prior)) }
            assertTrue(ambient.message.orEmpty().contains("ambient temperature rate attribution disabled"))
        }
    }

    @Test fun missingFailedWrongRunAndWrongTierCannotAuthorize() {
        val pair = pair()
        val started = Instant.now().minusSeconds(1)
        assertThrows(IllegalArgumentException::class.java) {
            validateSmokeEvidence(root.resolve("missing.json"), "fresh", pair, started)
        }
        listOf(Triple("fresh", "failed", "smoke"), Triple("old", "passed", "smoke"),
            Triple("fresh", "passed", "overworld-tele-soak")).forEach { (run, status, tier) ->
            assertThrows(IllegalArgumentException::class.java) {
                validateSmokeEvidence(evidence(pair, run, status, tier), "fresh", pair, started)
            }
        }
    }

    @Test fun overworldTeleSoakCannotBorrowSmokeEvidence() {
        val pair = pair()
        val started = Instant.now().minusSeconds(1)
        assertThrows(IllegalArgumentException::class.java) {
            validateOverworldTeleSoakReceipt(evidence(pair), "fresh", pair, started)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateSmokeEvidence(evidence(pair, tier = "overworld-tele-soak"), "fresh", pair, started, "overworld-tele-soak")
        }
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
