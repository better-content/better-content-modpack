package com.bettercontent.tests

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Files

@Tag("singleplayer")
@TestMethodOrder(OrderAnnotation::class)
class SingleplayerRuntimeTest {
    companion object {
        @JvmField @RegisterExtension val evidence = EvidenceExtension("singleplayer")
        lateinit var client: ClientFixture
        var seedServer: DedicatedServerFixture? = null
        val debugClients = mutableListOf<ClientFixture>()
        var title = false

        @JvmStatic
        @BeforeAll
        fun start() {
            if (evidence.run.target == "world-save") return
            client = ClientFixture(evidence.run)
            client.prepare()
            client.launchSingleplayer()
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            cleanupFixtures(buildList {
                if (::client.isInitialized) add(client)
                addAll(debugClients)
                seedServer?.let(::add)
            }) {
                evidence.run.event("process_cleanup", it)
            }
            debugClients.map { it.diagnosticSupport }.distinct().forEach { it.preserveBridge() }
        }
    }

    @Test @Order(1)
    fun clientReachesCustomizedTitleScreen() = evidence.run.checkpoint("single-player title screen") {
        client.waitTitleScreen()
        Thread.sleep(3000)
        client.capture("singleplayer-title.png")
        title = true
    }

    @Test @Order(2)
    fun debugFreshWorldBootSaveAndReopen() {
        if (evidence.run.tier != "debug") {
            evidence.run.event("scenario_omitted", mapOf("name" to "fresh world boot save reopen", "tier" to evidence.run.tier))
            return
        }
        if (evidence.run.target == null) assumeTrue(title, "title screen prerequisite failed")
        evidence.run.checkpoint("fresh world boot save and reopen") {
            if (evidence.run.target == null) client.close()
            evidence.run.event("blight_locus_seed_provenance", BlightLocusPersistence.Observation(
                BlightLocusPersistence.SEED, emptySet()).evidence("new_fixture_seed_requested"))
            val seed = DedicatedServerFixture(evidence.run, seed = BlightLocusPersistence.SEED).also { seedServer = it }
            seed.waitReady()
            seed.stopGracefully()
            val sourceWorld = seed.server.resolve("world")
            val seedLoci = BlightLocusPersistence.read(sourceWorld)
            evidence.run.event("blight_locus_claims_observed", seedLoci.evidence("seed_normal_stop"))
            require(Files.isRegularFile(sourceWorld.resolve("level.dat"))) { "seed server did not create a world save" }

            val first = ClientFixture(evidence.run, username = "SmokeWorld", slot = 5).also { debugClients += it }
            first.prepare()
            val firstSave = first.client.resolve("saves/DebugWorld")
            require(sourceWorld.toFile().copyRecursively(firstSave.toFile())) { "failed to stage fresh world" }
            // This fixture verifies world persistence, not the separate 16 km initial-spawn
            // search. That search can keep C2ME feature placement active while the probe
            // closes the integrated server, racing POI updates against world unload.
            val initialSpawnState = firstSave.resolve("data/better_world_management/initial-spawn-v1.tsv")
            Files.createDirectories(initialSpawnState.parent)
            Files.writeString(initialSpawnState, "fallback\n")
            first.launchQuickPlayWorld("DebugWorld", "save")
            first.waitForWorldProbe("BC_DEBUG_WORLD_EXITED")
            require(Files.readString(first.log).contains("BC_DEBUG_EMI_READY")) {
                "world save exited before EMI finished baking recipes"
            }
            val savedRecord = Regex("BC_DEBUG_WORLD_SAVED game_time=(\\d+) marker=([0-9a-f-]{36})")
                .find(Files.readString(first.log)) ?: error("world save has no persisted marker")
            val savedTime = savedRecord.groupValues[1].toLong()
            val savedMarker = java.util.UUID.fromString(savedRecord.groupValues[2])
            first.close()
            val savedLoci = BlightLocusPersistence.read(firstSave, seedLoci.claims)
            evidence.run.event("blight_locus_claims_observed", savedLoci.evidence("integrated_save_normal_close"))

            val reopened = ClientFixture(evidence.run, username = "SmokeWorld", slot = 6).also { debugClients += it }
            reopened.prepare()
            require(firstSave.toFile().copyRecursively(reopened.client.resolve("saves/DebugWorld").toFile())) {
                "failed to stage saved world for reopen"
            }
            val lineageState = first.client.resolve(".better_world_management")
            require(Files.isDirectory(lineageState) &&
                lineageState.toFile().copyRecursively(reopened.client.resolve(".better_world_management").toFile())) {
                "failed to stage single-player lineage state for reopen"
            }
            reopened.launchQuickPlayWorld("DebugWorld", "verify")
            reopened.waitForWorldProbe("BC_DEBUG_WORLD_LOADED mode=verify")
            val reopenedRecord = Regex("BC_DEBUG_WORLD_LOADED mode=verify game_time=(\\d+) marker=([0-9a-f-]{36})")
                .find(Files.readString(reopened.log)) ?: error("reopened world has no persisted marker")
            val reopenedTime = reopenedRecord.groupValues[1].toLong()
            val reopenedMarker = java.util.UUID.fromString(reopenedRecord.groupValues[2])
            check(reopenedMarker == savedMarker) { "reopened world lost the saved marker" }
            check(reopenedTime >= savedTime) { "reopened world lost saved game time ($reopenedTime < $savedTime)" }
            first.diagnosticSupport.assertStable()
            reopened.diagnosticSupport.assertStable()
            evidence.run.event("world_reopen_passed", mapOf("saved_game_time" to savedTime,
                "reopened_game_time" to reopenedTime, "saved_marker" to savedMarker.toString()))
            // Request native exit only after the actual checkpoint and marker/time oracles passed.
            reopened.requestNormalVerifyExit(savedMarker, reopenedMarker, savedTime, reopenedTime)
            // Process termination alone cannot prove that the reopened integrated world saved.
            reopened.waitForWorldProbe("BC_DEBUG_WORLD_EXITED")
            reopened.close()
            val reopenedLoci = BlightLocusPersistence.read(
                reopened.client.resolve("saves/DebugWorld"), savedLoci.claims)
            evidence.run.event("blight_locus_claims_observed", reopenedLoci.evidence("integrated_reopen_normal_close"))
            if (evidence.run.target != null) {
                LogPolicy.requireClean(collectLogs(evidence.run.directory))
                seed.assertHashes()
                first.assertHashes()
                reopened.assertHashes()
            }
        }
    }

    @Test @Order(3)
    fun singleplayerEvidenceIsCleanAndCandidatesAreUnchanged() {
        evidence.run.checkpoint("single-player log and hash audit") {
            if (evidence.run.target != "world-save") client.close()
            debugClients.forEach { it.close() }
            LogPolicy.requireClean(collectLogs(evidence.run.directory))
            if (evidence.run.target != "world-save") client.assertHashes()
            debugClients.forEach { it.assertHashes() }
            seedServer?.assertHashes()
        }
    }
}
