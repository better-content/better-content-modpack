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

@Tag("singleplayer")
@TestMethodOrder(OrderAnnotation::class)
class SingleplayerRuntimeTest {
    companion object {
        @JvmField @RegisterExtension val evidence = EvidenceExtension("singleplayer")
        lateinit var client: ClientFixture
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
            cleanupFixtures(if (::client.isInitialized) listOf(client) else emptyList()) {
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
    fun singleplayerEvidenceIsCleanAndCandidatesAreUnchanged() {
        assumeTrue(title, "title screen prerequisite failed")
        evidence.run.checkpoint("single-player log and hash audit") {
            client.close()
            LogPolicy.requireClean(collectLogs(evidence.run.directory))
            client.assertHashes()
        }
    }
}
