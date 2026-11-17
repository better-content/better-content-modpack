package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.jsonFingerprint
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.bettercontent.tests.suite.drivers.DriverAction
import java.time.Instant

/** One accepted loaded candidate after controller reviewed-coverage + sealed origin/complete interval. */
internal data class FrozenNaturalTarget(val schema: String, val loadedAttempt: BoundFile,
    val reviewedProfile: BoundFile, val origin: CheckpointRef, val mutationScope: BoundFile,
    val mutationActionPrefixSha256: String, val ledger: TargetSelectionLedger,
    val bound: BoundTarget, val output: ReviewedScopedOutputSlot) {
    init { require(schema == "bc.playtest_frozen_natural_target.v1" &&
        ledger.bound() == bound && origin == bound.inputCheckpoint && output.inputCheckpoint == origin &&
        output.selectorId == bound.selectorId && bound.source == TargetSource.NATURAL_UNMODIFIED_BLOCK &&
        bound.context.capabilityId == "target.observe.loaded_v1" &&
        mutationActionPrefixSha256.matches(com.bettercontent.tests.suite.shaPattern)) }
}

/**
 * The accepted controller attempt is emitted AFTER exact selected candidate+provider+route controls,
 * observed loaded roster, independent pristine saved MCA and B v2 no-change action prefix.
 * No status string or `isNewChunk` alone can mint this target.
 */
internal fun freezeReviewedNaturalTarget(store: CheckpointStore, input: CheckpointRef,
    selector: ReviewedTargetSelector, reviewed: ReviewedStage1Coverage, attempt: LoadedTargetAttempt,
    attemptFile: BoundFile, scope: BoundFile, mailbox: java.nio.file.Path, terminal: JsonNode,
    bindings: CheckpointBindings, epochId: String, nativeSessionId: String,
    acceptedAt: Instant): FrozenNaturalTarget {
    require(selector.schema == "bc.playtest_target_selector.v2" && selector.source == TargetSource.NATURAL_UNMODIFIED_BLOCK &&
        reviewed.profile == selector.coverageProfile && reviewed.candidateServerSha256 == bindings.candidate["server_sha256"] &&
        reviewed.providerModId == selector.provider &&
        reviewed.selectorSha256 == jsonFingerprint(
            suiteMapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(selector)
                .apply { remove("coverage_profile") }) &&
        attempt.outcome == "loaded_only" && attempt.selectorId == selector.id &&
        attempt.inputCheckpoint == input && attempt.censusFile != null &&
        attempt.selectorSha256 == jsonFingerprint(suiteMapper.valueToTree(selector)) &&
        attemptFile.sha256.matches(com.bettercontent.tests.suite.shaPattern) &&
        !acceptedAt.isBefore(Instant.parse(attempt.dispatchedAt)) && !acceptedAt.isAfter(Instant.now()) &&
        attempt.loadedAtOrBeforeNativeAction()) {
        "candidate/profile/actual loaded attempt was not controller-frozen before server-invoked mining"
    }
    val selected = requireNotNull(attempt.selected)
    val original = requireLoadedSourceStateAndScopedInterval(store, input, scope, mailbox,
        terminal.path("block_mutation_pre_action_prefix"), bindings, epochId, nativeSessionId,
        selector.bounds.dimension, selected)
    val prefix = replayInitialBlockMutationPrefix(store, input, scope, mailbox,
        terminal.path("block_mutation_pre_action_prefix"), bindings, epochId, nativeSessionId)
    val precondition = jsonFingerprint(suiteMapper.valueToTree(mapOf(
        "origin" to input, "region" to original.region, "block_id" to original.blockId,
        "properties" to original.properties, "scope" to scope, "action_prefix_sha256" to prefix.sha256,
        "profile" to reviewed.profile, "selected" to selected)))
    val observation = TargetObservation("bc.playtest_target_observation.v1", attempt.context,
        selector.id, 1, TargetSource.NATURAL_UNMODIFIED_BLOCK, selector.provider, selector.resourceId,
        selector.bounds.dimension, selected.x, selected.y, selected.z, null,
        selected.actorDistanceBlocks, selected.tick, selected.monotonicNanos,
        requireNotNull(attempt.censusFile).sha256, precondition, acceptedAt)
    val binding = jsonFingerprint(suiteMapper.valueToTree(mapOf(
        "selector" to selector, "profile" to reviewed.profile, "origin" to input,
        "attempt" to attemptFile, "scope" to scope, "precondition" to precondition,
        "reviewed_output_slot" to selector.reviewedOutputSlot,
        "reviewed_output_item_id" to selector.reviewedOutputItemId,
        "reviewed_face" to selector.reviewedDigFace)))
    val attemptRecord = TargetSelectionAttempt(1, TargetAttemptOutcome.ACCEPTED, observation,
        "reviewed_supported_path_controller_acceptance", requireNotNull(attempt.censusFile).sha256,
        observation.observedAt, attempt.completedNanos)
    val ledger = TargetSelectionLedger("bc.playtest_target_attempts.v1", selector, input,
        Instant.parse(attempt.dispatchedAt), attempt.dispatchNanos, listOf(attemptRecord), binding)
    val bound = ledger.bound()
    val output = ReviewedScopedOutputSlot("bc.playtest_reviewed_scoped_output_slot.v2", selector.id,
        selector.catalogueSha256, input, requireNotNull(selector.reviewedOutputSlot),
        requireNotNull(selector.reviewedOutputItemId))
    return FrozenNaturalTarget("bc.playtest_frozen_natural_target.v1", attemptFile, reviewed.profile,
        input, scope, prefix.sha256, ledger, bound, output)
}

/** Signed nano origin; strict ordering is by request sequence and B's exact byte prefix. */
private fun LoadedTargetAttempt.loadedAtOrBeforeNativeAction(): Boolean =
    context.actionOrdinal > 0 && context.requestSequence > 0 &&
        completedNanos >= dispatchNanos && selected != null

/** The common suite mapper intentionally has no JavaTimeModule; encode ISO timestamps explicitly. */
internal fun frozenNaturalWire(value: FrozenNaturalTarget): Map<String, Any?> = mapOf(
    "schema" to value.schema, "loaded_attempt" to value.loadedAttempt,
    "reviewed_profile" to value.reviewedProfile, "origin" to value.origin,
    "mutation_scope" to value.mutationScope,
    "mutation_action_prefix_sha256" to value.mutationActionPrefixSha256,
    "bound" to value.bound, "output" to value.output,
    "ledger" to mapOf("schema" to value.ledger.schema, "selector" to value.ledger.selector,
        "input_checkpoint" to value.ledger.inputCheckpoint,
        "started_at" to value.ledger.startedAt.toString(),
        "controller_start_nanos" to value.ledger.controllerStartNanos,
        "controller_binding_sha256" to value.ledger.controllerBindingSha256,
        "attempts" to value.ledger.attempts.map { row -> mapOf(
            "index" to row.index, "outcome" to row.outcome.name,
            "reason" to row.reason, "event_sha256" to row.eventSha256,
            "observed_at" to row.observedAt.toString(),
            "controller_observed_nanos" to row.controllerObservedNanos,
            "candidate" to row.candidate?.let { candidate -> mapOf(
                "schema" to candidate.schema, "context" to candidate.context,
                "selector_id" to candidate.selectorId, "attempt" to candidate.attempt,
                "source" to candidate.source.name, "provider" to candidate.provider,
                "resource_id" to candidate.resourceId, "dimension" to candidate.dimension,
                "x" to candidate.x, "y" to candidate.y, "z" to candidate.z,
                "observed_uuid" to candidate.observedUuid,
                "actor_distance_blocks" to candidate.actorDistanceBlocks,
                "tick" to candidate.tick, "monotonic_nanos" to candidate.monotonicNanos,
                "source_event_sha256" to candidate.sourceEventSha256,
                "precondition_sha256" to candidate.preconditionSha256,
                "observed_at" to candidate.observedAt.toString())
            }) })
)

/** Qualifier reconstructs typed ledgers from sealed controller bytes, then RECOMPUTES from native source. */
internal fun resolveReviewedServerDig(action: DriverAction, frozen: FrozenNaturalTarget,
    context: WitnessContext): DriverAction {
    require(action.id == "player.dig.witness_v1" && action.source == "server" &&
        action.parameters.fieldNames().asSequence().toSet() == setOf("selector_id") &&
        action.parameters.path("selector_id").asText() == frozen.bound.selectorId &&
        context.source == "server" && context.capabilityId == action.id &&
        context.caseId == frozen.bound.context.caseId &&
        context.trialId == frozen.bound.context.trialId &&
        context.actorUuid == frozen.bound.context.actorUuid &&
        context.actionOrdinal > frozen.bound.context.actionOrdinal &&
        context.requestSequence > frozen.bound.context.requestSequence &&
        frozen.ledger.selector.serverInvokedAcquisitionOnly == true) {
        "server-forced scoped witness request must use a FROZEN reviewed target and distinct action context"
    }
    frozen.bound.requireAction(context)
    val target = frozen.bound
    val face = requireNotNull(frozen.ledger.selector.reviewedDigFace)
    return action.copy(parameters = suiteMapper.valueToTree<ObjectNode>(linkedMapOf(
        "player_uuid" to context.actorUuid, "dimension" to target.dimension,
        "x" to target.x, "y" to target.y, "z" to target.z, "face" to face,
        "witness_context" to context, "bound_target_sha256" to target.bindingSha256)))
}

internal fun readFrozenNaturalTarget(file: BoundFile): FrozenNaturalTarget {
    val doc = strictCapturedJson(capturedEvidenceBytes(file, 1_048_576))
    require(doc.isObject && doc.fieldNames().asSequence().toSet() == setOf("schema", "loaded_attempt",
        "reviewed_profile", "origin", "mutation_scope", "mutation_action_prefix_sha256", "ledger", "bound", "output"))
    val raw = doc.path("ledger")
    require(raw.isObject && raw.fieldNames().asSequence().toSet() == setOf("schema", "selector",
        "input_checkpoint", "started_at", "controller_start_nanos", "controller_binding_sha256", "attempts") &&
        raw.path("attempts").isArray && raw.path("attempts").size() in 1..100)
    val ledger = TargetSelectionLedger(raw.path("schema").asText(),
        suiteMapper.treeToValue(raw.path("selector"), ReviewedTargetSelector::class.java),
        suiteMapper.treeToValue(raw.path("input_checkpoint"), CheckpointRef::class.java),
        Instant.parse(raw.path("started_at").asText()), raw.path("controller_start_nanos").asLong(),
        raw.path("attempts").map { row ->
            val observation = row.path("candidate").takeIf { !it.isNull }?.let { candidate ->
                TargetObservation(candidate.path("schema").asText(),
                    suiteMapper.treeToValue(candidate.path("context"), WitnessContext::class.java),
                    candidate.path("selector_id").asText(), candidate.path("attempt").asInt(),
                    TargetSource.valueOf(candidate.path("source").asText()), candidate.path("provider").asText(),
                    candidate.path("resource_id").asText(), candidate.path("dimension").asText(),
                    candidate.path("x").asInt(), candidate.path("y").asInt(), candidate.path("z").asInt(),
                    candidate.path("observed_uuid").takeIf { !it.isNull }?.asText(),
                    candidate.path("actor_distance_blocks").asDouble(), candidate.path("tick").asLong(),
                    candidate.path("monotonic_nanos").asLong(),
                    candidate.path("source_event_sha256").asText(), candidate.path("precondition_sha256").asText(),
                    Instant.parse(candidate.path("observed_at").asText()))
            }
            TargetSelectionAttempt(row.path("index").asInt(),
                TargetAttemptOutcome.valueOf(row.path("outcome").asText()), observation,
                row.path("reason").asText(), row.path("event_sha256").asText(),
                Instant.parse(row.path("observed_at").asText()), row.path("controller_observed_nanos").asLong())
        }, raw.path("controller_binding_sha256").asText())
    val value = FrozenNaturalTarget(doc.path("schema").asText(),
        suiteMapper.treeToValue(doc.path("loaded_attempt"), BoundFile::class.java),
        suiteMapper.treeToValue(doc.path("reviewed_profile"), BoundFile::class.java),
        suiteMapper.treeToValue(doc.path("origin"), CheckpointRef::class.java),
        suiteMapper.treeToValue(doc.path("mutation_scope"), BoundFile::class.java),
        doc.path("mutation_action_prefix_sha256").asText(), ledger,
        suiteMapper.treeToValue(doc.path("bound"), BoundTarget::class.java),
        suiteMapper.treeToValue(doc.path("output"), ReviewedScopedOutputSlot::class.java))
    // JSON has integral numbers, not JVM Int/Long node widths. Reparse the expected wire
    // without coercing the received tree: unknown fields and float/string substitutions still differ.
    require(doc == suiteMapper.readTree(suiteMapper.writeValueAsBytes(frozenNaturalWire(value)))) {
        "frozen target wire had extra, omitted or coerced fields" }
    return value
}
