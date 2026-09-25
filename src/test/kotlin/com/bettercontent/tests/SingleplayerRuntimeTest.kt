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
        assumeTrue(title, "title screen prerequisite failed")
        evidence.run.checkpoint("fresh world boot save and reopen") {
            client.close()
            val seed = DedicatedServerFixture(evidence.run).also { seedServer = it }
            seed.waitReady()
            seed.stopGracefully()
            val sourceWorld = seed.server.resolve("world")
            require(Files.isRegularFile(sourceWorld.resolve("level.dat"))) { "seed server did not create a world save" }

            val first = ClientFixture(evidence.run, username = "SmokeWorld", slot = 5).also { debugClients += it }
            first.prepare()
            val firstSave = first.client.resolve("saves/DebugWorld")
            require(sourceWorld.toFile().copyRecursively(firstSave.toFile())) { "failed to stage fresh world" }
            first.launchQuickPlayWorld("DebugWorld", "save")
            first.waitForWorldProbe("BC_DEBUG_WORLD_EXITED")
            val savedTime = Regex("BC_DEBUG_WORLD_SAVED game_time=(\\d+)").find(Files.readString(first.log))
                ?.groupValues?.get(1)?.toLong() ?: error("world save marker has no game time")
            first.close()

            val reopened = ClientFixture(evidence.run, username = "SmokeWorld", slot = 6).also { debugClients += it }
            reopened.prepare()
            require(firstSave.toFile().copyRecursively(reopened.client.resolve("saves/DebugWorld").toFile())) {
                "failed to stage saved world for reopen"
            }
            reopened.launchQuickPlayWorld("DebugWorld", "verify")
            reopened.waitForWorldProbe("BC_DEBUG_WORLD_LOADED mode=verify")
            val reopenedTime = Regex("BC_DEBUG_WORLD_LOADED mode=verify game_time=(\\d+)")
                .find(Files.readString(reopened.log))?.groupValues?.get(1)?.toLong()
                ?: error("reopened world marker has no game time")
            check(reopenedTime >= savedTime) { "reopened world lost saved game time ($reopenedTime < $savedTime)" }
            evidence.run.event("world_reopen_passed", mapOf("saved_game_time" to savedTime, "reopened_game_time" to reopenedTime))
        }
    }

    @Test @Order(3)
    fun singleplayerEvidenceIsCleanAndCandidatesAreUnchanged() {
        assumeTrue(title, "title screen prerequisite failed")
        evidence.run.checkpoint("single-player log and hash audit") {
            client.close()
            debugClients.forEach { it.close() }
            LogPolicy.requireClean(collectLogs(evidence.run.directory))
            client.assertHashes()
            debugClients.forEach { it.assertHashes() }
        }
    }
}
