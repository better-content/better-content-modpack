package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.idPattern
import com.bettercontent.tests.suite.jsonFingerprint
import com.fasterxml.jackson.databind.JsonNode
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.shaPattern
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Typed nonshipping witness boundaries. Parsing a witness never qualifies it without raw source/lineage checks. */
internal data class WitnessContext(val campaignId: String, val lineageId: String, val epochId: String,
    val nativeSessionId: String, val branchId: String, val caseId: String, val trialId: String,
    val source: String, val actionOrdinal: Long, val requestSequence: Long, val actorUuid: String,
    val capabilityId: String, val purpose: String) {
    init {
        require(campaignId.startsWith("campaign-") &&
            runCatching { UUID.fromString(campaignId.removePrefix("campaign-")).toString() ==
                campaignId.removePrefix("campaign-") }.getOrDefault(false) &&
            runCatching { UUID.fromString(lineageId).toString() == lineageId &&
                UUID.fromString(actorUuid).toString() == actorUuid }.getOrDefault(false))
        require(epochId.matches(Regex("epoch-[0-9]{5}")) && nativeSessionId.startsWith("epoch-") &&
            branchId.matches(idPattern) && caseId.matches(idPattern) &&
            trialId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) &&
            source in setOf("server", "client") && actionOrdinal > 0 && requestSequence > 0 &&
            capabilityId.matches(idPattern) &&
            purpose in setOf(ExecutionPurpose.DIAGNOSTIC.wire, ExecutionPurpose.QUALIFICATION.wire))
    }
    fun sameLineage(other: WitnessContext) = campaignId == other.campaignId && lineageId == other.lineageId &&
        caseId == other.caseId && trialId == other.trialId && actorUuid == other.actorUuid && purpose == other.purpose
    fun sameAncestry(other: WitnessContext) = sameLineage(other) && branchId == other.branchId &&
        epochId == other.epochId && nativeSessionId == other.nativeSessionId
}

/** Only the controller calls this after NativeMailbox.reserveSequence and an observed actor snapshot. */
internal fun controllerWitnessContext(bindings: CheckpointBindings, epochId: String, nativeSessionId: String,
    branchId: String, caseId: String, trialId: String, source: String, actionOrdinal: Long,
    allocatedSequence: Long, actorUuid: String, capabilityId: String): WitnessContext = WitnessContext(
    bindings.campaignId, bindings.lineageId, epochId, nativeSessionId, branchId, caseId, trialId,
    source, actionOrdinal, allocatedSequence, actorUuid, capabilityId, bindings.purpose)

internal data class TargetBounds(val dimension: String, val minX: Int, val maxX: Int,
    val minY: Int, val maxY: Int, val minZ: Int, val maxZ: Int, val maximumDistanceBlocks: Int) {
    init {
        require(dimension.matches(resourcePattern) && minX <= maxX && minY <= maxY && minZ <= maxZ &&
            listOf(minX, maxX, minZ, maxZ).all { it in -30_000_000..30_000_000 } &&
            minY >= -2048 && maxY <= 2048 && maximumDistanceBlocks in 1..2048)
    }
    fun includes(x: Int, y: Int, z: Int) = x in minX..maxX && y in minY..maxY && z in minZ..maxZ
}
internal enum class TargetSource { NATURAL_ENTITY, NATURAL_UNMODIFIED_BLOCK, NEWLY_GENERATED_TERRAIN, REVIEWED_FIXTURE }
internal enum class TargetSelectionOrder { FIRST_CAUSAL_EVENT, NEAREST_THEN_UUID }
internal data class ReviewedTargetSelector(val schema: String, val id: String, val catalogueSha256: String, val provider: String,
    val capabilityId: String, val resourceId: String, val inputCheckpointId: String,
    val bounds: TargetBounds, val source: TargetSource, val rule: String,
    val order: TargetSelectionOrder, val maximumSeconds: Long, val maximumAttempts: Int) {
    init {
        require(schema == "bc.playtest_target_selector.v1" && id.matches(idPattern) &&
            catalogueSha256.matches(shaPattern) && provider.matches(idPattern) &&
            capabilityId.matches(idPattern) && resourceId.matches(resourcePattern) &&
            inputCheckpointId.matches(idPattern) && rule.isNotBlank() && rule.length <= 512 &&
            maximumSeconds in 1..3600 && maximumAttempts in 1..100)
    }
}
internal data class TargetObservation(val schema: String, val context: WitnessContext, val selectorId: String,
    val attempt: Int, val source: TargetSource, val provider: String, val resourceId: String,
    val dimension: String, val x: Int, val y: Int, val z: Int, val observedUuid: String?,
    val actorDistanceBlocks: Double, val tick: Long, val monotonicNanos: Long, val sourceEventSha256: String,
    val preconditionSha256: String, val observedAt: Instant) {
    fun bind(selector: ReviewedTargetSelector, inputCheckpoint: CheckpointRef,
        controllerBindingSha256: String): BoundTarget {
        require(schema == "bc.playtest_target_observation.v1" && selector.id == selectorId &&
            selector.inputCheckpointId == inputCheckpoint.id &&
            selector.capabilityId == context.capabilityId && attempt in 1..selector.maximumAttempts &&
            selector.provider == provider && selector.resourceId == resourceId && selector.source == source &&
            selector.bounds.dimension == dimension && selector.bounds.includes(x, y, z) &&
            actorDistanceBlocks.isFinite() && actorDistanceBlocks in 0.0..selector.bounds.maximumDistanceBlocks.toDouble() &&
            tick >= 0 &&
            monotonicNanos > 0 && sourceEventSha256.matches(shaPattern) && preconditionSha256.matches(shaPattern) &&
            controllerBindingSha256.matches(shaPattern) &&
            !observedAt.isAfter(Instant.now()) &&
            (source != TargetSource.NATURAL_ENTITY ||
                (observedUuid != null && runCatching { UUID.fromString(observedUuid).toString() == observedUuid }.getOrDefault(false))) &&
            (source == TargetSource.NATURAL_ENTITY || observedUuid == null)) { "unreviewed or cross-checkpoint target" }
        return BoundTarget("bc.playtest_bound_target.v1", context, selectorId, source, provider,
            resourceId, observedUuid, dimension, x, y, z, controllerBindingSha256, sourceEventSha256,
            inputCheckpoint)
    }
}
/** Includes all unsuccessful candidates/engagements; a failure never silently rebinds another target. */
internal enum class TargetAttemptOutcome { REJECTED, ACCEPTED }
internal data class TargetSelectionAttempt(val index: Int, val outcome: TargetAttemptOutcome,
    val candidate: TargetObservation?, val reason: String, val eventSha256: String, val observedAt: Instant,
    val controllerObservedNanos: Long) {
    init {
        require(index > 0 && eventSha256.matches(shaPattern) && reason.length <= 512 &&
            !observedAt.isAfter(Instant.now()) && controllerObservedNanos > 0 &&
            (outcome != TargetAttemptOutcome.ACCEPTED || candidate != null) &&
            (outcome == TargetAttemptOutcome.ACCEPTED || reason.isNotBlank()) &&
            (candidate == null || (candidate.attempt == index && candidate.sourceEventSha256 == eventSha256)))
    }
}
/** Rejected/exhausted ledgers remain valid evidence; bound() is the sole acceptance transition. */
internal data class TargetSelectionLedger(val schema: String, val selector: ReviewedTargetSelector,
    val inputCheckpoint: CheckpointRef, val startedAt: Instant, val controllerStartNanos: Long,
    val attempts: List<TargetSelectionAttempt>, val controllerBindingSha256: String) {
    init {
        require(schema == "bc.playtest_target_attempts.v1" && controllerBindingSha256.matches(shaPattern) &&
            selector.inputCheckpointId == inputCheckpoint.id && !startedAt.isAfter(Instant.now()) &&
            controllerStartNanos > 0 && attempts.isNotEmpty() && attempts.size <= selector.maximumAttempts &&
            attempts.map { it.index } == (1..attempts.size).toList() &&
            attempts.dropLast(1).none { it.outcome == TargetAttemptOutcome.ACCEPTED } &&
            attempts.all { !it.observedAt.isBefore(startedAt) &&
                runCatching { Math.subtractExact(it.controllerObservedNanos, controllerStartNanos) in
                    0L..Duration.ofSeconds(selector.maximumSeconds).toNanos() }.getOrDefault(false) } &&
            attempts.map { it.controllerObservedNanos }.zipWithNext().all { (a, b) -> b >= a } &&
            attempts.map { it.observedAt }.zipWithNext().all { (a, b) -> !b.isBefore(a) }) {
            "target attempts missing or exceeded pre-reviewed selector-start deadline"
        }
    }
    fun bound(): BoundTarget {
        require(attempts.last().outcome == TargetAttemptOutcome.ACCEPTED) { "exhausted target selection is not a bound target" }
        return requireNotNull(attempts.last().candidate).also {
            require(it.attempt == attempts.last().index && it.sourceEventSha256 == attempts.last().eventSha256)
        }.bind(selector, inputCheckpoint, controllerBindingSha256)
    }
}
internal data class BoundTarget(val schema: String, val context: WitnessContext, val selectorId: String, val source: TargetSource,
    val provider: String, val resourceId: String, val observedUuid: String?, val dimension: String, val x: Int, val y: Int, val z: Int,
    val bindingSha256: String, val priorEventSha256: String, val inputCheckpoint: CheckpointRef) {
    init { require(schema == "bc.playtest_bound_target.v1" && provider.matches(idPattern) &&
        resourceId.matches(resourcePattern) && bindingSha256.matches(shaPattern) &&
        priorEventSha256.matches(shaPattern)) }
    fun requireAction(action: WitnessContext, target: BoundTarget = this) {
        require(context.sameAncestry(action) && target == this && action.actionOrdinal > context.actionOrdinal &&
            (action.source != context.source || action.requestSequence > context.requestSequence)) {
            "target reselected, spoofed or reused across epoch/trial"
        }
    }
}
internal enum class EngagementOutcome { SUCCEEDED, FAILED }
internal data class TargetEngagementAttempt(val bound: BoundTarget, val action: WitnessContext,
    val outcome: EngagementOutcome, val eventSha256: String, val observedAt: Instant) {
    init {
        bound.requireAction(action)
        require(eventSha256.matches(shaPattern) && !observedAt.isAfter(Instant.now()))
    }
}
internal data class TargetEngagementLedger(val bound: BoundTarget, val attempts: List<TargetEngagementAttempt>) {
    init {
        require(attempts.isNotEmpty() && attempts.all { it.bound == bound } &&
            attempts.zipWithNext().all { (a, b) -> b.action.actionOrdinal > a.action.actionOrdinal &&
                !b.observedAt.isBefore(a.observedAt) }) { "post-binding engagement silently reselected target" }
    }
}


/** Count-independent identity must be computed by an explicit native provider adapter. */
internal data class CanonicalStackRef(val itemId: String, val count: Long,
    val canonicalAlgorithm: String, val canonicalNbtSha256: String,
    val contentAlgorithm: String, val contentSha256: String,
    val adapterId: String, val capabilityFingerprints: Map<String, String>) {
    init {
        require(itemId.matches(resourcePattern) && count in 1L..2_147_483_647L &&
            canonicalAlgorithm == "bc.playtest.stack-nbt.v1" && canonicalNbtSha256.matches(shaPattern) &&
            contentAlgorithm == "bc.playtest.stack-content.v1" && contentSha256.matches(shaPattern) &&
            adapterId.matches(idPattern) && adapterId !in setOf("generic", "legacy_snbt") &&
            capabilityFingerprints.keys.all(idPattern::matches) &&
            capabilityFingerprints.values.all(shaPattern::matches))
    }
    /** Exact save identity includes Count; same content alone can only aid conservation, not persistence. */
    fun sameExact(other: CanonicalStackRef) = this == other
    fun sameContent(other: CanonicalStackRef) = itemId == other.itemId &&
        contentAlgorithm == other.contentAlgorithm && contentSha256 == other.contentSha256 &&
        adapterId == other.adapterId && capabilityFingerprints == other.capabilityFingerprints
}
internal enum class AcquisitionSource { NATURAL_BLOCK_DROP, NATURAL_ENTITY_DROP, CRAFTED, TRADE, LOOT,
    FLUID_EXTRACTION, REVIEWED_FIXTURE }
internal data class SourceWitness(val context: WitnessContext, val target: BoundTarget?,
    val source: AcquisitionSource, val providerAdapterId: String, val eventSha256: String,
    val sourceStateSha256: String, val stack: CanonicalStackRef, val sourceOwnerUuid: String,
    val sourceSlot: String, val tick: Long, val eventIndex: Long) {
    init {
        require(providerAdapterId.matches(idPattern) && eventSha256.matches(shaPattern) &&
            sourceStateSha256.matches(shaPattern) && stack.count > 0 && sourceSlot.isNotBlank() &&
            tick >= 0 && eventIndex > 0 &&
            context.actorUuid == sourceOwnerUuid)
        if (source in setOf(AcquisitionSource.NATURAL_BLOCK_DROP, AcquisitionSource.NATURAL_ENTITY_DROP))
            require(target != null && target.source == (if (source == AcquisitionSource.NATURAL_BLOCK_DROP)
                TargetSource.NATURAL_UNMODIFIED_BLOCK else TargetSource.NATURAL_ENTITY) &&
                context.sameAncestry(target.context)) {
                "fixture grant cannot prove natural acquisition"
            }
    }
}
internal enum class TransitionKind { TRANSFER, SPLIT, MERGE, CONSUME, TRANSFORM }
internal data class TransitionWitness(val context: WitnessContext, val kind: TransitionKind,
    val priorEventSha256: String, val eventSha256: String, val fromSlot: String, val toSlot: String,
    val beforeSource: CanonicalStackRef, val afterSource: CanonicalStackRef?,
    val beforeDestination: CanonicalStackRef?, val afterDestination: CanonicalStackRef?,
    val consumedCount: Long, val producedCount: Long, val tick: Long, val eventIndex: Long) {
    init {
        // null is an explicitly observed ABSENT slot, never an invented air:0 identity/hash.
        val remainingCount = afterSource?.count ?: 0L
        val sourceRemains = afterSource == null || beforeSource.sameContent(afterSource)
        require(priorEventSha256.matches(shaPattern) && eventSha256.matches(shaPattern) &&
            priorEventSha256 != eventSha256 && fromSlot.isNotBlank() && toSlot.isNotBlank() &&
            tick >= 0 && eventIndex > 0 && consumedCount >= 0 && producedCount >= 0)
        when (kind) {
            TransitionKind.TRANSFER, TransitionKind.SPLIT, TransitionKind.MERGE -> {
                require(sourceRemains && afterDestination != null &&
                    (beforeDestination == null || beforeDestination.sameContent(afterDestination)) &&
                    afterDestination.sameContent(beforeSource) && consumedCount == 0L && producedCount == 0L &&
                    Math.addExact(beforeSource.count, beforeDestination?.count ?: 0L) ==
                    Math.addExact(remainingCount, afterDestination.count)) { "item split/merge/transfer not conserved" }
            }
            TransitionKind.CONSUME -> require(sourceRemains &&
                beforeSource.count - remainingCount == consumedCount && consumedCount > 0 &&
                producedCount == 0L) { "consumption count or identity differs" }
            TransitionKind.TRANSFORM -> require(consumedCount > 0 && producedCount > 0 &&
                sourceRemains && beforeSource.count - remainingCount == consumedCount &&
                afterDestination != null && (beforeDestination == null ||
                    beforeDestination.sameContent(afterDestination)) &&
                afterDestination.count - (beforeDestination?.count ?: 0L) == producedCount) {
                "transformation input/output counts differ from causal witness"
            }
        }
    }
}
/** Native bytes contain only observed past events; no future checkpoint or circular self-digest. */
/** DERIVED graph ONLY, never parse native B JSONL as this aggregate schema. Wire adapter not yet authored. */
internal data class DerivedAcquisitionGraph(val schema: String, val source: SourceWitness,
    val transitions: List<TransitionWitness>, val eventCount: Int, val outputSlot: String) {
    init {
        require(schema == "bc.playtest_derived_acquisition_graph.v1" && transitions.isNotEmpty() &&
            eventCount in 2..256 && eventCount >= transitions.size + 1 &&
            outputSlot.isNotBlank() && outputSlot == transitions.last().toSlot &&
            transitions.first().priorEventSha256 == source.eventSha256 &&
            transitions.first().eventIndex > source.eventIndex &&
            transitions.zipWithNext().all { (a, b) -> a.eventSha256 == b.priorEventSha256 &&
                b.eventIndex > a.eventIndex && a.context.sameAncestry(b.context) &&
                b.context.actionOrdinal >= a.context.actionOrdinal &&
                (b.context.source != a.context.source || b.context.requestSequence >= a.context.requestSequence) } &&
            transitions.all { it.context.sameAncestry(source.context) } &&
            transitions.last().eventIndex <= eventCount) { "missing, reordered or foreign causal event" }
        val slots = mutableMapOf(source.sourceSlot to source.stack)
        val proven = mutableMapOf(source.sourceSlot to source.stack.count)
        transitions.forEach { transition ->
            require(transition.kind in setOf(TransitionKind.TRANSFER, TransitionKind.SPLIT, TransitionKind.MERGE) &&
                transition.fromSlot != transition.toSlot &&
                slots[transition.fromSlot]?.sameExact(transition.beforeSource) == true &&
                ((transition.beforeDestination == null && transition.toSlot !in slots) ||
                    transition.beforeDestination?.let { slots[transition.toSlot]?.sameExact(it) } == true)) {
                "source/slot before-state missing, cross-content or external transformation adapter unsupported"
            }
            val moved = transition.beforeSource.count - (transition.afterSource?.count ?: 0L)
            require(moved > 0 && proven.getOrDefault(transition.fromSlot, 0L) == transition.beforeSource.count) {
                "partial merged source has ambiguous provenance; explicit adapter required"
            }
            if (transition.afterSource == null) {
                slots.remove(transition.fromSlot); proven.remove(transition.fromSlot)
            } else {
                proven[transition.fromSlot] = transition.afterSource.count
                slots[transition.fromSlot] = transition.afterSource
            }
            proven[transition.toSlot] = Math.addExact(proven.getOrDefault(transition.toSlot, 0L), moved)
            slots[transition.toSlot] = requireNotNull(transition.afterDestination)
        }
        require(slots[outputSlot] != null && slots[outputSlot]!!.count > 0 &&
            proven[outputSlot] == slots[outputSlot]!!.count) {
            "selected output includes unproven initial contents or wrong causal owner/slot"
        }
    }
    val observedFinal: CanonicalStackRef get() = requireNotNull(transitions.last().afterDestination)
}
/** Controller-created ONLY after validated save/stop/PID exit and exact checkpoint seal. */
internal data class SealedAcquisitionBinding(val schema: String, val trace: DerivedAcquisitionGraph,
    val traceBytes: Long, val traceFile: BoundFile, val sealedCheckpoint: CheckpointRef,
    val closure: EpochClosure, val savedOutput: CanonicalStackRef, val savedStateSha256: String) {
    init {
        require(schema == "bc.playtest_sealed_acquisition_binding.v1" && traceBytes in 1..32_000_000 &&
            traceFile.sha256.matches(shaPattern) && savedStateSha256.matches(shaPattern) &&
            closure.saveValidated && closure.processCleanupComplete && closure.survivingPids.isEmpty() &&
            sealedCheckpoint.id != trace.source.target?.inputCheckpoint?.id &&
            trace.observedFinal.sameExact(savedOutput)) { "unsealed or identity-drifted acquisition cannot qualify" }
    }
}

internal data class PersistenceSubject(val campaignId: String, val lineageId: String, val actorUuid: String,
    val providerAdapterId: String, val targetBindingSha256: String, val provenanceRootSha256: String) {
    init {
        require(runCatching { UUID.fromString(campaignId.removePrefix("campaign-")).toString() ==
            campaignId.removePrefix("campaign-") && UUID.fromString(lineageId).toString() == lineageId &&
            UUID.fromString(actorUuid).toString() == actorUuid }.getOrDefault(false) &&
            campaignId.startsWith("campaign-") && providerAdapterId.matches(idPattern) &&
            targetBindingSha256.matches(shaPattern) && provenanceRootSha256.matches(shaPattern))
    }
}
internal enum class PersistencePhase { PREPARE, LIVE, SAVED_SHUTDOWN, SEALED, FRESH_REOPEN, RECONNECTED, REOPENED_ASSERTION }
internal enum class PersistenceFactSource { NATIVE, CONTROLLER }
/** Declared bounded clock-only rule; qualifier must independently bind reviewSha256 to an approved manifest. */
internal data class ReviewedPersistenceTimeRule(val domain: String, val adapterId: String,
    val reviewSha256: String, val maximumAdvanceTicks: Long) {
    init { require(domain.matches(idPattern) && adapterId.matches(idPattern) && reviewSha256.matches(shaPattern) &&
        maximumAdvanceTicks in 1..72_000) }
}
internal data class PersistenceState(val context: WitnessContext, val subject: PersistenceSubject,
    val phase: PersistencePhase, val source: PersistenceFactSource, val checkpoint: CheckpointRef,
    val domainFingerprints: Map<String, String>, val rawDomainStates: Map<String, JsonNode>,
    val persistentPayloadSha256: Map<String, String>, val tick: Long, val observedAt: Instant,
    val process: OwnedProcessIdentity?, val rawSha256: String) {
    init {
        require(context.campaignId == subject.campaignId && context.lineageId == subject.lineageId &&
            context.actorUuid == subject.actorUuid && domainFingerprints.isNotEmpty() &&
            domainFingerprints.keys == rawDomainStates.keys &&
            domainFingerprints.keys == persistentPayloadSha256.keys &&
            domainFingerprints.keys.all(idPattern::matches) && domainFingerprints.values.all(shaPattern::matches) &&
            persistentPayloadSha256.values.all(shaPattern::matches) &&
            rawDomainStates.all { (domain, raw) -> raw.isObject && raw.size() <= 32 &&
                jsonFingerprint(raw) == domainFingerprints.getValue(domain) } &&
            tick >= 0 && rawSha256.matches(shaPattern) && !observedAt.isAfter(Instant.now()) &&
            (phase !in setOf(PersistencePhase.SAVED_SHUTDOWN, PersistencePhase.SEALED) ||
                source == PersistenceFactSource.CONTROLLER))
    }
}
/** No generic ignore-timestamps path: only an exact closed clock envelope may advance. */
private fun persistenceTransition(before: PersistenceState, after: PersistenceState,
    rules: List<ReviewedPersistenceTimeRule>): Boolean {
    if (before.domainFingerprints.keys != after.domainFingerprints.keys) return false
    return before.domainFingerprints.keys.all { domain ->
        if (before.domainFingerprints.getValue(domain) == after.domainFingerprints.getValue(domain)) true
        else {
            val rule = rules.singleOrNull { it.domain == domain } ?: return@all false
            val prior = before.rawDomainStates.getValue(domain)
            val later = after.rawDomainStates.getValue(domain)
            val fields = setOf("schema", "adapter_id", "persistent_payload_sha256", "non_temporal_state", "clock_ticks")
            val earlyTick = prior.path("clock_ticks"); val lateTick = later.path("clock_ticks")
            prior.fieldNames().asSequence().toSet() == fields && later.fieldNames().asSequence().toSet() == fields &&
                prior.path("schema").asText() == "bc.playtest_clock_only_provider_state.v1" &&
                later.path("schema") == prior.path("schema") &&
                prior.path("adapter_id").asText() == rule.adapterId &&
                later.path("adapter_id") == prior.path("adapter_id") &&
                rule.adapterId == before.subject.providerAdapterId &&
                prior.path("persistent_payload_sha256").asText() == before.persistentPayloadSha256.getValue(domain) &&
                later.path("persistent_payload_sha256").asText() == after.persistentPayloadSha256.getValue(domain) &&
                prior.path("non_temporal_state") == later.path("non_temporal_state") &&
                before.persistentPayloadSha256.getValue(domain) == after.persistentPayloadSha256.getValue(domain) &&
                earlyTick.isIntegralNumber && earlyTick.canConvertToLong() &&
                lateTick.isIntegralNumber && lateTick.canConvertToLong() &&
                lateTick.asLong() >= earlyTick.asLong() &&
                lateTick.asLong() - earlyTick.asLong() in 1..rule.maximumAdvanceTicks
        }
    }
}
internal data class PersistenceChain(val phases: List<PersistenceState>, val sealedCheckpoint: CheckpointRef,
    val sealedParent: CheckpointRef, val closure: EpochClosure,
    val reviewedTimeRules: List<ReviewedPersistenceTimeRule> = emptyList()) {
    init {
        require(phases.map { it.phase } == PersistencePhase.entries &&
            phases.all { it.subject == phases.first().subject && it.context.purpose == phases.first().context.purpose } &&
            phases.map { it.observedAt }.zipWithNext().all { (before, after) -> !after.isBefore(before) } &&
            phases[PersistencePhase.SEALED.ordinal].checkpoint == sealedCheckpoint &&
            phases[PersistencePhase.FRESH_REOPEN.ordinal].checkpoint == sealedCheckpoint &&
            phases[PersistencePhase.LIVE.ordinal].checkpoint == sealedParent &&
            closure.saveValidated && closure.processCleanupComplete && closure.survivingPids.isEmpty() &&
            closure.epochId == phases[PersistencePhase.LIVE.ordinal].context.epochId &&
            closure.nativeSessionId == phases[PersistencePhase.LIVE.ordinal].context.nativeSessionId &&
            phases[PersistencePhase.FRESH_REOPEN.ordinal].context.epochId != phases[PersistencePhase.LIVE.ordinal].context.epochId &&
            phases[PersistencePhase.FRESH_REOPEN.ordinal].context.nativeSessionId !=
                phases[PersistencePhase.LIVE.ordinal].context.nativeSessionId &&
            reviewedTimeRules.map { it.domain }.distinct().size == reviewedTimeRules.size &&
            reviewedTimeRules.all { it.domain in phases.first().domainFingerprints &&
                it.adapterId == phases.first().subject.providerAdapterId } &&
            phases.zipWithNext().all { (before, after) ->
                persistenceTransition(before, after, reviewedTimeRules) } &&
            phases.slice(PersistencePhase.SAVED_SHUTDOWN.ordinal..PersistencePhase.FRESH_REOPEN.ordinal)
                .map { it.persistentPayloadSha256 }.distinct().size == 1 &&
            phases.map { it.rawSha256 }.distinct().size == phases.size &&
            phases[PersistencePhase.FRESH_REOPEN.ordinal].process != null &&
            phases[PersistencePhase.LIVE.ordinal].process != null &&
            phases[PersistencePhase.LIVE.ordinal].process in closure.ownedProcesses &&
            phases[PersistencePhase.FRESH_REOPEN.ordinal].process!!.pid != phases[PersistencePhase.LIVE.ordinal].process!!.pid &&
            phases[PersistencePhase.FRESH_REOPEN.ordinal].process!!.startedAt != phases[PersistencePhase.LIVE.ordinal].process!!.startedAt) {
            "live/disk/reopened state or fresh process identity not preserved"
        }
    }
}
