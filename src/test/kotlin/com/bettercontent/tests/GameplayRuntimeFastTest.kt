package com.bettercontent.tests

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import java.nio.file.Files
import java.nio.file.Path

/** Minecraft-free contract checks; synthetic records never establish native acceptance. */
@Tag("fast")
class GameplayRuntimeFastTest {
    private val mapper = jacksonObjectMapper()
    private val nonce = "8bf5d06b-06a4-401d-8d30-80d6604e6db0"
    private val player = "cb950f1e-f009-4b8b-94ce-747b141adfb1"
    private fun valid(): ObjectNode = mapper.readTree("""
        {"schema":"bc.observed_campaign_presence.v1","run":"synthetic","nonce":"$nonce","player":"$player",
         "world":"/synthetic/world","worldIdentity":"$nonce","history_origin":"observed_native_online_ticks",
         "firstTicks":18010,"secondTicks":18001,"firstNanos":900100000000,"secondNanos":900010000000,
         "firstNativeStart":100,"firstNativeEnd":18109,"secondNativeStart":18200,"secondNativeEnd":36200,
         "firstMonotonicStart":0,"firstMonotonicEnd":900100000000,"secondMonotonicStart":0,"secondMonotonicEnd":900010000000,
         "candidateClient":"${"a".repeat(64)}","candidateServer":"${"b".repeat(64)}","supportHash":"${"c".repeat(64)}",
         "offline_ticks_counted":0,"half":true,"resumed":true,"complete":true,
         "beforeFamiliar":false,"halfFamiliar":false,"resumeFamiliar":false,"finalFamiliar":true}
    """.trimIndent()) as ObjectNode
    private fun verify(node: ObjectNode) = ObservedGameplayContract.validate(node, "synthetic", nonce, player)

    @Test fun acceptsActualCountsRatherThanFabricatingExactTicks() { verify(valid()) }
    @Test fun rejectsInsufficientElapsedOrNativeTicksIndependently() {
        for (key in listOf("firstTicks", "secondTicks", "firstNanos", "secondNanos")) {
            val node = valid().put(key, if (key.endsWith("Ticks")) 17999L else 899_999_999_999L)
            assertThrows(IllegalArgumentException::class.java) { verify(node) }
        }
    }
    @Test fun eitherHalfAloneMustBeInsufficient() {
        for (key in listOf("firstTicks", "secondTicks"))
            assertThrows(IllegalArgumentException::class.java) { verify(valid().put(key, 36000)) }
    }
    @Test fun rejectsAuthoredHistoryOfflineCreditMissingRestartAndWrongIdentity() {
        for ((key, value) in listOf("run" to "wrong", "nonce" to player, "player" to nonce,
                "history_origin" to "authored_fixture"))
            assertThrows(IllegalArgumentException::class.java) { verify(valid().put(key, value)) }
        assertThrows(IllegalArgumentException::class.java) { verify(valid().put("offline_ticks_counted", 1)) }
        for (key in listOf("half", "resumed", "complete", "finalFamiliar"))
            assertThrows(IllegalArgumentException::class.java) { verify(valid().put(key, false)) }
        for (key in listOf("beforeFamiliar", "halfFamiliar", "resumeFamiliar"))
            assertThrows(IllegalArgumentException::class.java) { verify(valid().put(key, true)) }
        val missing = valid().also { it.remove("resumeFamiliar") }
        assertThrows(IllegalArgumentException::class.java) { verify(missing) }
    }
    @Test fun rejectsMissingNativeWitnessAndUnboundCandidate() {
        for (key in listOf("firstNativeEnd", "secondNativeEnd", "firstMonotonicEnd", "secondMonotonicEnd", "supportHash")) {
            val node = valid().also { it.remove(key) }
            assertThrows(IllegalArgumentException::class.java) { verify(node) }
        }
    }
    @Test fun traderWitnessCannotCreateArrivalsOrAdvanceClock() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val source = Files.readString(root.parent.resolve("mod_source/better-runtime-diagnostics/src/testSupport/java/com/bettercontent/runtimetestsupport/GameplayTraderProbe.java"))
        require(source.contains("EventPriority.LOWEST") && source.contains("trader-complete"))
        require(source.contains("visits.size() < 2") && source.contains("countdown > visit.lastCountdown"))
        require(source.contains("oldPostRetired") && source.contains("oldFireRetired"))
        require(source.contains("trader-resume") && source.contains("actual_server_process_restart")
            && source.contains("saved.process == ProcessHandle.current().pid()")
            && source.contains("same owned native post/fire did not survive process restart"))
        require(!source.contains("TraderCampData.load(") && !source.contains("setTarget("))
        require(!source.contains("new WanderingTraderSpawner") && !source.contains("tickScheduledVisit(")
            && !source.contains("setDespawnDelay") && !source.contains("setGameTime") && !source.contains("setDayTime"))
    }
    @Test fun observerOnlyReadsPublicProductionFamiliarityAndPersistsSameWorld() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val source = Files.readString(root.parent.resolve("mod_source/better-runtime-diagnostics/src/testSupport/java/com/bettercontent/runtimetestsupport/ObservedCampaignPresenceProbe.java"))
        require(source.contains("EventPriority.LOWEST") && source.contains("TickEvent.Phase.END"))
        require(source.contains("getMethod(\"regionFamiliar\"") && source.contains("LevelResource.ROOT"))
        require(source.contains("ServerStoppingEvent") && source.contains("previousNanos = 0"))
        require(!source.contains("getMethod(\"recordPresence\"") && !source.contains("setGameTime")
            && !source.contains("setDayTime") && !source.contains("setGameMode") && !source.contains("setAccessible"))
        require(source.contains("VISIT_NANOS = 900_000_000_000L"))
        require(source.contains("state.firstTicks >= 36000") && source.contains("resume before reconnect"))
    }
}
