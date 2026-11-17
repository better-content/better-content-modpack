package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.jsonFingerprint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class RuntimeWitnessContractsTest {
    private val campaign = "campaign-${UUID.randomUUID()}"
    private val lineage = UUID.randomUUID().toString()
    private val actor = UUID.randomUUID().toString()
    private fun context(epoch: String = "epoch-00001", native: String = "epoch-${UUID.randomUUID()}",
        ordinal: Long = 1, request: Long = 2, actorId: String = actor, purpose: String = "diagnostic") =
        WitnessContext(campaign, lineage, epoch, native, "branch", "primitive.harvest", "trial-1",
            "server", ordinal, request, actorId, "player.dig.witness_v1", purpose)
    private fun stack(count: Long, full: String, content: String = "c".repeat(64)) = CanonicalStackRef(
        "minecraft:stone", count, "bc.playtest.stack-nbt.v1", full.repeat(64),
        "bc.playtest.stack-content.v1", content, "vanilla_item_v1", emptyMap())
    private fun ref(id: String) = CheckpointRef(id, BoundFile("/home/dev/.tmp/$id.json", "a".repeat(64)))

    @Test fun countSensitiveCanonicalAndContentEquivalentSplitRemainDistinct() {
        val before = stack(2, "a"); val after = stack(1, "b")
        assertEquals(false, before.sameExact(after))
        assertEquals(true, before.sameContent(after))
        val split = TransitionWitness(context(), TransitionKind.SPLIT, "a".repeat(64), "b".repeat(64),
            "player:0", "container:0", before, after, null, after, 0, 0, 4, 2)
        assertEquals(TransitionKind.SPLIT, split.kind)
        // A full pickup observes the source slot ABSENT; it cannot invent an air:0 canonical/content hash.
        val pickup = split.copy(kind = TransitionKind.TRANSFER, afterSource = null,
            afterDestination = stack(2, "a"))
        assertEquals(null, pickup.afterSource)
        assertThrows(IllegalArgumentException::class.java) { stack(1, "a").copy(count = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            split.copy(afterDestination = stack(1, "b", "d".repeat(64)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            split.copy(afterSource = stack(3, "e"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            stack(1, "b").copy(canonicalAlgorithm = "bc.playtest.stack-content.v1")
        }
    }

    @Test fun targetAndNativeTraceCannotSmuggleForeignActorOrFixtureAsNaturalSource() {
        val ctx = context(); val selector = ReviewedTargetSelector("bc.playtest_target_selector.v1", "natural.block", "a".repeat(64),
            "minecraft", "player.dig.witness_v1", "minecraft:stone", "origin",
            TargetBounds("minecraft:overworld", 0, 32, 0, 128, 0, 32, 5),
            TargetSource.NATURAL_UNMODIFIED_BLOCK, "first reachable unmodified stone",
            TargetSelectionOrder.FIRST_CAUSAL_EVENT, 60, 2)
        val observation = TargetObservation("bc.playtest_target_observation.v1", ctx, selector.id, 1, selector.source, selector.provider,
            selector.resourceId, selector.bounds.dimension, 4, 64, 4, null, 3.0, 3, 100L,
            "b".repeat(64), "c".repeat(64), Instant.now().minusSeconds(1))
        val startNanos = 1_000L
        val startedAt = observation.observedAt.minusSeconds(2)
        val accepted = TargetSelectionAttempt(1, TargetAttemptOutcome.ACCEPTED, observation, "",
            observation.sourceEventSha256, observation.observedAt, 1_050L)
        val attempts = TargetSelectionLedger("bc.playtest_target_attempts.v1", selector, ref("origin"),
            startedAt, startNanos, listOf(accepted), "d".repeat(64))
        val bound = attempts.bound()
        val exhausted = attempts.copy(attempts = listOf(accepted.copy(outcome = TargetAttemptOutcome.REJECTED,
            reason = "candidate unreachable")))
        assertThrows(IllegalArgumentException::class.java) { exhausted.bound() }
        assertThrows(IllegalArgumentException::class.java) {
            attempts.copy(attempts = listOf(accepted.copy(index = 2,
                candidate = observation.copy(attempt = 2))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            attempts.copy(attempts = listOf(accepted.copy(controllerObservedNanos =
                startNanos + java.time.Duration.ofSeconds(61).toNanos()))) // first candidate arrived after start deadline
        }
        bound.requireAction(ctx.copy(actionOrdinal = 2, requestSequence = 3))
        bound.requireAction(ctx.copy(source = "client", actionOrdinal = 2, requestSequence = 1))
        val failed = TargetEngagementAttempt(bound, ctx.copy(actionOrdinal = 2, requestSequence = 3),
            EngagementOutcome.FAILED, "a".repeat(64), Instant.now().minusSeconds(1))
        TargetEngagementLedger(bound, listOf(failed))
        assertThrows(IllegalArgumentException::class.java) {
            TargetEngagementLedger(bound, listOf(failed,
                failed.copy(bound = bound.copy(x = 7), action = ctx.copy(actionOrdinal = 3, requestSequence = 4))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            bound.requireAction(ctx.copy(actorUuid = UUID.randomUUID().toString(), actionOrdinal = 2, requestSequence = 3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            bound.requireAction(ctx.copy(epochId = "epoch-00002", actionOrdinal = 2, requestSequence = 3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            bound.requireAction(ctx.copy(actionOrdinal = 1, requestSequence = 3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            bound.requireAction(ctx.copy(actionOrdinal = 2, requestSequence = 2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            bound.requireAction(ctx.copy(actionOrdinal = 2, requestSequence = 3), bound.copy(x = 5))
        }
        assertThrows(IllegalArgumentException::class.java) {
            observation.copy(source = TargetSource.REVIEWED_FIXTURE).bind(selector, ref("origin"), "d".repeat(64))
        }
        assertThrows(IllegalArgumentException::class.java) {
            observation.copy(x = 50).bind(selector, ref("origin"), "d".repeat(64))
        }
        val source = SourceWitness(ctx, bound, AcquisitionSource.NATURAL_BLOCK_DROP,
            "vanilla_item_v1", "e".repeat(64), "f".repeat(64), stack(2, "a"), actor, "drop:0", 4, 1)
        assertThrows(IllegalArgumentException::class.java) { source.copy(sourceOwnerUuid = UUID.randomUUID().toString()) }
        val transition = TransitionWitness(ctx.copy(actionOrdinal = 2, requestSequence = 3), TransitionKind.SPLIT,
            source.eventSha256, "1".repeat(64), "drop:0", "player:0", stack(2, "a"), stack(1, "b"),
            null, stack(1, "b"), 0, 0, 5, 2)
        val trace = DerivedAcquisitionGraph("bc.playtest_derived_acquisition_graph.v1", source,
            listOf(transition), 2, "player:0")
        assertEquals(2, trace.eventCount)
        val fullPickup = transition.copy(kind = TransitionKind.TRANSFER, afterSource = null,
            afterDestination = stack(2, "a"))
        assertEquals(2, trace.copy(transitions = listOf(fullPickup)).observedFinal.count)
        assertThrows(IllegalArgumentException::class.java) {
            fullPickup.copy(beforeSource = stack(50, "c"), afterDestination = stack(1, "b"))
        } // unexplained source 50 -> acquired 1 with absent remainder can never conserve
        assertThrows(IllegalArgumentException::class.java) {
            trace.copy(transitions = listOf(transition.copy(beforeSource = stack(2, "a", "d".repeat(64)),
                afterSource = stack(1, "b", "d".repeat(64)),
                afterDestination = stack(1, "b", "d".repeat(64))))) // linked event is not source content
        }
        assertThrows(IllegalArgumentException::class.java) {
            trace.copy(transitions = listOf(transition.copy(beforeDestination = stack(1, "b"),
                afterDestination = stack(2, "c")))) // fabricated preexisting destination
        }
        val second = transition.copy(context = ctx.copy(actionOrdinal = 3, requestSequence = 4),
            priorEventSha256 = transition.eventSha256, eventSha256 = "2".repeat(64),
            fromSlot = "player:0", toSlot = "player:1", eventIndex = 3)
        assertThrows(IllegalArgumentException::class.java) {
            trace.copy(transitions = listOf(transition, second), eventCount = 3, outputSlot = "player:1")
        } // second claims stone:2 but previous player:0 has stone:1

        assertThrows(IllegalArgumentException::class.java) {
            trace.copy(transitions = listOf(transition.copy(priorEventSha256 = "3".repeat(64))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            transition.copy(priorEventSha256 = transition.eventSha256) // circular/self-hashed event
        }
        assertThrows(IllegalArgumentException::class.java) {
            trace.copy(transitions = listOf(transition.copy(eventIndex = 1))) // reordered native event
        }
        assertThrows(IllegalArgumentException::class.java) {
            source.copy(source = AcquisitionSource.NATURAL_ENTITY_DROP)
        }
    }

    @Test fun persistenceSubjectSurvivesFreshEpochButRejectsDiskDriftAndReusedPid() {
        val initial = context()
        val reopened = initial.copy(epochId = "epoch-00002", nativeSessionId = "epoch-${UUID.randomUUID()}",
            actionOrdinal = 1, requestSequence = 2)
        val subject = PersistenceSubject(campaign, lineage, actor, "vanilla_item_v1",
            "d".repeat(64), "e".repeat(64))
        val oldPid = OwnedProcessIdentity(1001, Instant.now().minusSeconds(20).toString())
        val newPid = OwnedProcessIdentity(1002, Instant.now().minusSeconds(5).toString())
        val input = ref("origin"); val sealed = ref("sealed")
        val now = Instant.now().minusSeconds(10)
        fun rawState(domain: String, clock: Long = 100) = suiteMapper.createObjectNode().apply {
            put("schema", "bc.playtest_clock_only_provider_state.v1")
            put("adapter_id", "vanilla_item_v1")
            put("persistent_payload_sha256", "d".repeat(64))
            put("clock_ticks", clock)
            putObject("non_temporal_state").put("domain", domain)
        }
        val rawStates = mapOf("player_inventory" to rawState("player_inventory"),
            "provider_capabilities" to rawState("provider_capabilities"))
        val fingerprints = rawStates.mapValues { jsonFingerprint(it.value) }
        val payloads = rawStates.mapValues { "d".repeat(64) }
        val phases = PersistencePhase.entries.mapIndexed { index, phase ->
            val fresh = index >= PersistencePhase.FRESH_REOPEN.ordinal
            PersistenceState(if (fresh) reopened else initial, subject, phase,
                if (phase in setOf(PersistencePhase.SAVED_SHUTDOWN, PersistencePhase.SEALED))
                    PersistenceFactSource.CONTROLLER else PersistenceFactSource.NATIVE,
                if (index >= PersistencePhase.SEALED.ordinal) sealed else input,
                fingerprints, rawStates, payloads, 100 + index.toLong(), now.plusSeconds(index.toLong()),
                if (fresh) newPid else if (index <= PersistencePhase.LIVE.ordinal) oldPid else null,
                "0123456789abcdef"[index].toString().repeat(64))
        }
        val closure = EpochClosure(initial.epochId, initial.nativeSessionId, Instant.now().toString(), true,
            true, listOf(oldPid), emptyList(), BoundFile("/home/dev/.tmp/closure.json", "a".repeat(64)))
        PersistenceChain(phases, sealed, input, closure)
        val clocked = phases.map { phase ->
            if (phase.phase.ordinal < PersistencePhase.RECONNECTED.ordinal) phase else {
                val laterRaw = rawStates + ("player_inventory" to rawState("player_inventory", 105))
                phase.copy(rawDomainStates = laterRaw,
                    domainFingerprints = laterRaw.mapValues { jsonFingerprint(it.value) })
            }
        }
        assertThrows(IllegalArgumentException::class.java) { PersistenceChain(clocked, sealed, input, closure) }
        val rule = ReviewedPersistenceTimeRule("player_inventory", "vanilla_item_v1", "f".repeat(64), 5)
        PersistenceChain(clocked, sealed, input, closure, listOf(rule)) // typed clock-only model; no provider/disk adapter yet
        assertThrows(IllegalArgumentException::class.java) {
            PersistenceChain(clocked, sealed, input, closure, listOf(rule.copy(maximumAdvanceTicks = 4)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PersistenceChain(phases.toMutableList().apply { this[2] = this[2].copy(domainFingerprints =
                mapOf("player_inventory" to "f".repeat(64))) }, sealed, input, closure)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PersistenceChain(phases.toMutableList().apply { this[4] = this[4].copy(process = oldPid) }, sealed, input, closure)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PersistenceChain(phases, sealed, ref("wrong-parent"), closure)
        }
    }
}
