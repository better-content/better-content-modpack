package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.NativeRequest
import com.bettercontent.tests.suite.SuiteCase
import com.bettercontent.tests.suite.drivers.DriverAction
import com.bettercontent.tests.suite.jsonFingerprint
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.Instant

/** Authoring supplies only the selector ID. Actual actor, context and request sequence are controller facts. */
internal fun reviewedLoadedSelector(spec: SuiteCase, action: DriverAction, input: CheckpointRef): ReviewedTargetSelector {
    require(action.id == "target.observe.loaded_v1" && action.source == "server" &&
        action.parameters.fieldNames().asSequence().toSet() == setOf("selector_id") &&
        action.parameters.path("selector_id").isTextual)
    val id = action.parameters.path("selector_id").asText()
    val selector = spec.targetBindings.map { suiteMapper.treeToValue(it, ReviewedTargetSelector::class.java) }
        .single { it.id == id }
    require(((selector.source == TargetSource.NATURAL_UNMODIFIED_BLOCK && input.id == "origin") ||
        selector.source == TargetSource.NEWLY_GENERATED_TERRAIN) &&
        selector.capabilityId == action.id && selector.inputCheckpointId == input.id &&
        selector.bounds.dimension in setOf("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end") &&
        selector.order == TargetSelectionOrder.NEAREST_THEN_POSITION &&
        selector.rule == "loaded_block_nearest_center_then_xyz_v1") {
        "loaded-only target request differs from exact reviewed input/selector"
    }
    return selector
}

/** Exact native numeric bounds; dimension belongs at request top level, not inside bounds. */
internal fun loadedBoundsWire(bounds: TargetBounds): ObjectNode = suiteMapper.valueToTree(linkedMapOf(
    "min_x" to bounds.minX, "max_x" to bounds.maxX,
    "min_y" to bounds.minY, "max_y" to bounds.maxY,
    "min_z" to bounds.minZ, "max_z" to bounds.maxZ,
    "maximum_distance_blocks" to bounds.maximumDistanceBlocks))

internal fun resolveLoadedTargetAction(action: DriverAction, selector: ReviewedTargetSelector,
    actor: String, context: WitnessContext): DriverAction {
    require(action.source == "server" && action.id == "target.observe.loaded_v1" &&
        context.source == "server" && context.capabilityId == action.id && context.actorUuid == actor &&
        action.parameters.fieldNames().asSequence().toSet() == setOf("selector_id") &&
        action.parameters.path("selector_id").asText() == selector.id)
    val params = suiteMapper.valueToTree<ObjectNode>(linkedMapOf(
        "player_uuid" to actor, "dimension" to selector.bounds.dimension, "kind" to "block",
        "resource_id" to selector.resourceId,
        "selector_sha256" to jsonFingerprint(suiteMapper.valueToTree<JsonNode>(selector)),
        "bounds" to loadedBoundsWire(selector.bounds), "max_candidates" to 4096,
        "witness_context" to context))
    return action.copy(parameters = params)
}

/** A selected LOADED row remains only a current-state observation, not a bound natural target. */
internal data class LoadedTargetAttempt(val schema: String, val context: WitnessContext,
    val selectorId: String, val selectorSha256: String, val inputCheckpoint: CheckpointRef,
    val censusFile: BoundFile?, val terminalObservationSha256: String,
    val candidateCount: Int, val selected: LoadedBlockRow?,
    val outcome: String, val rejectionReason: String?, val dispatchedAt: String,
    val dispatchNanos: Long, val completedNanos: Long) {
    init {
        require(schema == "bc.playtest_loaded_target_attempt.v1" &&
            selectorSha256.matches(com.bettercontent.tests.suite.shaPattern) &&
            terminalObservationSha256.matches(com.bettercontent.tests.suite.shaPattern) &&
            (censusFile != null || candidateCount == 0) &&
            (censusFile == null || censusFile.sha256.matches(com.bettercontent.tests.suite.shaPattern)) &&
            context.capabilityId == "target.observe.loaded_v1" && context.source == "server" &&
            selectorId.matches(com.bettercontent.tests.suite.idPattern) && candidateCount in 0..4096 &&
            (rejectionReason == null || rejectionReason.length <= 512) &&
            runCatching { !Instant.parse(dispatchedAt).isAfter(Instant.now()) }.getOrDefault(false) &&
            runCatching { Math.subtractExact(completedNanos, dispatchNanos) in 0L..3_600_000_000_000L }
                .getOrDefault(false) &&
            ((outcome == "loaded_only" && censusFile != null && selected != null && rejectionReason == null) ||
                (outcome == "rejected" && selected == null && !rejectionReason.isNullOrBlank()))) {
            "loaded-only attempt is not a closed observation/rejection"
        }
    }
}

/** A complete roster can freeze a current loaded choice; none of these fields grants natural provenance. */
internal fun loadedTargetAttempt(selector: ReviewedTargetSelector, input: CheckpointRef,
    census: LoadedBlockCensus, context: WitnessContext, terminalObservationSha256: String,
    dispatchedAt: Instant, dispatchNanos: Long, completedNanos: Long): LoadedTargetAttempt {
    require(selector.id.matches(com.bettercontent.tests.suite.idPattern) &&
        selector.inputCheckpointId == input.id && context.capabilityId == selector.capabilityId &&
        census.selectorSha256 == jsonFingerprint(suiteMapper.valueToTree<JsonNode>(selector)) &&
        runCatching { Math.subtractExact(completedNanos, dispatchNanos) in
            0L..java.time.Duration.ofSeconds(selector.maximumSeconds).toNanos() }.getOrDefault(false)) {
        "loaded target selection exceeded reviewed source/elapsed deadline"
    }
    val chosen = if (census.complete) runCatching { census.reviewedLoadedCandidate(selector) }.getOrNull() else null
    return LoadedTargetAttempt("bc.playtest_loaded_target_attempt.v1", context, selector.id,
        census.selectorSha256, input, census.raw, terminalObservationSha256,
        census.candidates.size, chosen,
        if (chosen == null) "rejected" else "loaded_only",
        if (chosen == null) (census.incompleteReason ?: "no_candidate_inside_reviewed_distance") else null,
        dispatchedAt.toString(), dispatchNanos, completedNanos)
}

internal fun failedLoadedTargetAttempt(selector: ReviewedTargetSelector, input: CheckpointRef,
    context: WitnessContext, terminalObservationSha256: String, dispatchedAt: Instant,
    dispatchNanos: Long, completedNanos: Long): LoadedTargetAttempt {
    require(selector.inputCheckpointId == input.id && context.capabilityId == selector.capabilityId)
    return LoadedTargetAttempt("bc.playtest_loaded_target_attempt.v1", context, selector.id,
        jsonFingerprint(suiteMapper.valueToTree<JsonNode>(selector)), input, null,
        terminalObservationSha256, 0, null, "rejected", "native_target_action_failed",
        dispatchedAt.toString(), dispatchNanos, completedNanos)
}

internal fun loadedCensusRequest(runId: String, caseId: String, token: String, sequence: Long,
    timeoutTicks: Int, action: DriverAction): NativeRequest = NativeRequest(
    "bc.playtest_action.v2", runId, caseId, token, sequence, action.id, timeoutTicks, action.parameters)

/** Source state and exact action-time interval are necessary, still INSUFFICIENT for natural provenance. */
internal fun requireLoadedBirthStateAndPostbirthInterval(store: CheckpointStore,
    parent: CheckpointRef, specs: List<SuiteCase>, catalogueSha256: String,
    scope: PrelaunchBlockBirthScope, evidence: java.nio.file.Path, mailbox: java.nio.file.Path,
    pointer: JsonNode, bindings: CheckpointBindings, epochId: String, nativeSessionId: String,
    dimension: String, selected: LoadedBlockRow): CapturedBirthInterval {
    val pos = ScopedBlockPosition(dimension, selected.x, selected.y, selected.z)
    val interval = replayBlockBirthPrefix(store, parent, specs, catalogueSha256, bindings,
        epochId, nativeSessionId, evidence, scope, mailbox, pointer)
    require(interval.ready && pos in interval.birthStates && pos in interval.currentStates &&
        interval.birthStates.getValue(pos) == interval.currentStates.getValue(pos) &&
        interval.changes.none { it == pos } && interval.observedPaths.none {
            it == "LevelChunkSection_palette_fallback_incomplete"
        } && selected.id == interval.birthStates.getValue(pos).first &&
        selected.properties == interval.birthStates.getValue(pos).second) {
        "loaded Stage2 state differs from first full-chunk birth or watched postbirth interval has a gap/change"
    }
    return interval // parent absence + birth facts only, NOT genuine natural/proven provider coverage.
}

internal fun requireLoadedSourceStateAndScopedInterval(store: CheckpointStore, input: CheckpointRef,
    scope: BoundFile, mailbox: java.nio.file.Path, pointer: JsonNode,
    bindings: CheckpointBindings, epochId: String, nativeSessionId: String,
    dimension: String, selected: LoadedBlockRow): SealedBlockBaseline {
    require(input.id == "origin") { "generated terrain and inherited non-origin baselines need a separate birth/interval proof" }
    val saved = readSealedBlockBaseline(store, input, dimension, selected.x, selected.y, selected.z)
    val pos = ScopedBlockPosition(dimension, selected.x, selected.y, selected.z)
    val prefix = replayInitialBlockMutationPrefix(store, input, scope, mailbox, pointer,
        bindings, epochId, nativeSessionId)
    val watched = suiteMapper.readTree(java.nio.file.Path.of(scope.path).toFile()).path("positions")
    require(com.bettercontent.tests.Hashes.sha256(java.nio.file.Path.of(scope.path)) == scope.sha256 &&
        watched.isArray && watched.any { it == suiteMapper.valueToTree<JsonNode>(pos) } &&
        selected.id == saved.blockId && selected.properties == saved.properties &&
        !prefix.fallbackIncomplete && !prefix.observedChangeAt(pos)) {
        "loaded state differs from independently decoded saved baseline or has a known watched write"
    }
    return saved
}
