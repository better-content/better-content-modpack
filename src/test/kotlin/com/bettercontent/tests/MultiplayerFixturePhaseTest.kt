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
            "debugNativeDeepVoidEntry" to 2,
            "debugNativeFontRoundTrips" to 3,
            "everyFontAndCreatingSpaceDimensionStabilizesAtFreshLocations" to 4,
            "threeSurvivalPlayersStartCampaigns" to 5,
            "debugServerRestartAndClientReconnectPreserveWorld" to 6,
            "multiplayerEvidenceIsCleanAndCandidatesAreUnchanged" to 7,
        ), orders.sortedBy { it.second })
    }

    @Test fun initialNativeFixturesDoNotPrepareCampaignTerrain() {
        for (name in listOf("start", "leadClientJoinsFreshDedicatedServer",
            "debugNativeDeepVoidEntry", "debugNativeFontRoundTrips",
            "everyFontAndCreatingSpaceDimensionStabilizesAtFreshLocations")) {
            val fixture = body(name)
            for (operation in listOf("prepareCampaignPlatform(", "prepareCampaignTerrainAndRecover(",
                "forceload", "run fill", "authorCampaignPresence(")) {
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

    @Test fun authoredCampaignHistoryIsBoundedAndFollowsGroundingOnly() {
        val campaign = body("threeSurvivalPlayersStartCampaigns")
        val grounded = campaign.indexOf("placeCampaignPlayer(client.username")
        val seed = campaign.indexOf("if (attempt == 0) authorCampaignPresence(")
        val positive = campaign.indexOf("better_pillager_campaigns harness spawn scout immediate")
        assertTrue(grounded >= 0 && seed > grounded && positive > seed)
        assertEquals(1, Regex("authorCampaignPresence\\(").findAll(campaign).count())
        val author = body("authorCampaignPresence")
        val inspect = author.indexOf("submit(\"inspect\")")
        val negative = author.indexOf("unfamiliar campaign rejection")
        val mutate = author.indexOf("submit(\"seed\")")
        assertTrue(inspect >= 0 && negative > inspect && mutate > negative)
        for (contract in listOf("if (index > 1)", "check(!before)", "encounter=none",
            "campaign_presence_before", "authored_ticks=36000 history_origin=authored_fixture",
            "\"elapsed_time_acceptance\" to false", "StandardCopyOption.ATOMIC_MOVE"))
            assertTrue(author.contains(contract), "missing authored fixture contract: $contract")
        for (forbidden in listOf("restoreSnapshot", "regionalPresence.clear", "recordOnlinePresence", "reset"))
            assertFalse(author.contains(forbidden), "fixture must preserve real history: $forbidden")
    }

    @Test fun campaignHistorySupportUsesOnlyFixedPublicApisAndBoundRequests() {
        val support = Files.readString(Path.of("../mod_source/better-runtime-diagnostics/src/testSupport/java/com/bettercontent/runtimetestsupport/CampaignPresenceFixture.java"))
        for (contract in listOf("AUTHORED_TICKS = 36_000", "tick < AUTHORED_TICKS", "prepared.size() >= 3",
            "nonces.contains(nonce)", "seeded.contains(uuid)", "prior.nonce().equals(nonce)",
            "fields.length != 9", "!run.equals(fields[0])", "bc.pack_test.campaign_presence",
            "bc.pack_test.diagnostics.mode", "GameType.SURVIVAL", "Level.OVERWORLD", "player.isAlive()",
            "Blocks.GRASS_BLOCK", "Files.isSymbolicLink", "history_origin=authored_fixture",
            "getMethod(\"recordPresence\", String.class, pointClass)",
            "getMethod(\"regionFamiliar\", String.class, pointClass)", "Files.move(request"))
            assertTrue(support.contains(contract), "missing bound support contract: $contract")
        for (forbidden in listOf("getDeclared", "setAccessible", "restoreSnapshot", "setGameMode", "setDayTime"))
            assertFalse(support.contains(forbidden), "support must not bypass production: $forbidden")
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
