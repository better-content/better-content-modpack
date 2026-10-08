package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/** Minecraft-free phase guards; runtime terrain and restoration remain separate gates. */
@Tag("fast")
class MultiplayerFixturePhaseTest {
    private val source = Files.readString(Path.of(
        "src/test/kotlin/com/bettercontent/tests/MultiplayerRuntimeTest.kt"))

    private fun body(name: String): String {
        val begin = source.indexOf("fun $name(")
        require(begin >= 0) { "missing fixture method $name" }
        val next = Regex("(?m)^    (?:private )?fun |^    @Test")
            .find(source, begin + 1)?.range?.first ?: source.length
        return source.substring(begin, next)
    }

    @Test fun phasesHaveExplicitDistinctOrders() {
        val orders = Regex("@Test @Order\\((\\d+)\\)\\s+fun (\\w+)")
            .findAll(source).map { it.groupValues[2] to it.groupValues[1].toInt() }.toList()
        assertEquals(listOf(
            "leadClientJoinsFreshDedicatedServer" to 1,
            "journalInventoryContractPasses" to 2,
            "debugNativeFontRoundTrips" to 3,
            "everyFontAndCreatingSpaceDimensionStabilizesAtFreshLocations" to 4,
            "threeSurvivalPlayersStartCampaigns" to 5,
            "debugServerRestartAndClientReconnectPreserveWorld" to 6,
            "multiplayerEvidenceIsCleanAndCandidatesAreUnchanged" to 7,
        ), orders.sortedBy { it.second })
    }

    @Test fun initialNativeFixturesDoNotPrepareCampaignTerrain() {
        for (name in listOf("start", "leadClientJoinsFreshDedicatedServer",
            "journalInventoryContractPasses", "debugNativeFontRoundTrips",
            "everyFontAndCreatingSpaceDimensionStabilizesAtFreshLocations")) {
            val fixture = body(name)
            for (operation in listOf("prepareCampaignPlatform(", "prepareCampaignTerrainAndRecover(",
                "forceload", "run fill")) {
                assertFalse(fixture.contains(operation), "$name contains $operation")
            }
        }
    }

    @Test fun fullCampaignPrewarmOccursAfterServerResetBeforeFreshLeadLaunch() {
        val campaign = body("threeSurvivalPlayersStartCampaigns")
        val restart = campaign.indexOf("server.restart()")
        val prewarm = campaign.indexOf("prepareCampaignTerrainAndRecover()")
        val launch = campaign.indexOf("lead.restartDedicated(++clientRestartAttempt)")
        assertTrue(restart >= 0 && prewarm > restart && launch > prewarm)
        assertEquals(1, Regex("prepareCampaignTerrainAndRecover\\(\\)").findAll(campaign).count())
        assertTrue(campaign.contains("requireAllPlayersOnline(\"campaign clients joined\")"))
        assertTrue(campaign.contains("verifyCampaignPlatform(index + 1, x, z)"))
    }

    @Test fun prewarmKeepsAllPlatformsAndThreeRecoverySamples() {
        val prewarm = body("prepareCampaignTerrainAndRecover")
        assertTrue(prewarm.contains("positions.forEachIndexed"))
        assertTrue(prewarm.contains("prepareCampaignPlatform(index + 1, x, z)"))
        assertTrue(prewarm.contains("repeat(3)"))
        assertTrue(prewarm.contains("\"forge tps\""))
        assertTrue(prewarm.contains("Duration.ofMinutes(3)"))
        assertTrue(prewarm.contains("retryInterval = Duration.ofSeconds(30)"))
        assertTrue(prewarm.contains("if (sample < 2) Thread.sleep(10_000)"))
    }
}
