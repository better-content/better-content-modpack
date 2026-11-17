package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.SuiteCase
import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.drivers.CaseDriver
import com.bettercontent.tests.suite.drivers.DriverAction
import com.bettercontent.tests.suite.drivers.DriverRegistry
import com.bettercontent.tests.suite.drivers.DriverStep
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

internal const val DIAGNOSTIC_MUTATION_SCOPE_MARKER = "@controller-mutation-scope-id-v1"
internal const val DIAGNOSTIC_INPUT_SHA_MARKER = "@controller-input-checkpoint-sha256-v1"
internal const val DIAGNOSTIC_GETTER_ACTION = "block_mutation.control_expose_states_v1"
internal const val DIAGNOSTIC_GETTER_CASE = "control.mutation.external-getter-negative"
private const val GETTER_REASON = "mutable_state_container_exposed"
private const val GETTER_SELECTOR = "state_container_exposed"

/** Explicit v3 diagnostic-only lookup; never alters ordinary full-case supportedCases. */
internal fun controllerDriver(spec: SuiteCase, purpose: ExecutionPurpose): CaseDriver {
    if (spec.id in reviewedStage1NegativeCases || spec.id in reviewedDiagnosticPositiveCases) {
        require(purpose == ExecutionPurpose.DIAGNOSTIC && spec.layer == "campaign" &&
            spec.assertions.isEmpty() && spec.targetBindings.isEmpty() &&
            spec.evidenceKinds.toSet() == setOf("native-interaction") &&
            DriverRegistry.resolve(spec.id) == null && spec.id !in DriverRegistry.supportedCases)
        return requireNotNull(DriverRegistry.diagnosticOnly(spec.id))
    }
    if (spec.id != DIAGNOSTIC_GETTER_CASE) return requireNotNull(DriverRegistry.resolve(spec.id))
    require(purpose == ExecutionPurpose.DIAGNOSTIC && spec.layer == "campaign" &&
        spec.assertions.isEmpty() && spec.targetBindings.size == 1 &&
        spec.evidenceKinds.toSet() == setOf("native-interaction") &&
        DriverRegistry.resolve(spec.id) == null && spec.id !in DriverRegistry.supportedCases)
    return requireNotNull(DriverRegistry.diagnosticOnly(spec.id))
}

/** No ordinary qualification runner may treat a deliberately failed scope as clean. */
internal fun diagnosticGetterSelector(spec: SuiteCase, plan: List<DriverStep>,
    purpose: ExecutionPurpose, inputId: String?): ReviewedTargetSelector? {
    if (plan.none { it.action.id == DIAGNOSTIC_GETTER_ACTION }) return null
    require(purpose == ExecutionPurpose.DIAGNOSTIC && inputId == "origin" &&
        spec.targetBindings.size == 1 && plan.size == 1 &&
        plan.single().action.id == DIAGNOSTIC_GETTER_ACTION &&
        plan.single().action.source == "server") {
        "expected negative getter must be ONE exact disposable DIAGNOSTIC-only origin action"
    }
    val selector = suiteMapper.treeToValue(spec.targetBindings.single(), ReviewedTargetSelector::class.java)
    require(selector.schema == "bc.playtest_target_selector.v1" && selector.inputCheckpointId == inputId &&
        selector.source == TargetSource.NATURAL_UNMODIFIED_BLOCK &&
        selector.capabilityId == "target.observe.loaded_v1" &&
        selector.bounds.minX == selector.bounds.maxX &&
        selector.bounds.minY == selector.bounds.maxY &&
        selector.bounds.minZ == selector.bounds.maxZ)
    val params = plan.single().action.parameters
    require(params.fieldNames().asSequence().toSet() == setOf("scope_id", "selector_id", "dimension",
        "x", "y", "z") && params.path("scope_id").asText() == DIAGNOSTIC_MUTATION_SCOPE_MARKER &&
        params.path("selector_id").asText() == GETTER_SELECTOR &&
        params.path("dimension").asText() == selector.bounds.dimension &&
        params.path("x").isIntegralNumber && params.path("x").asInt() == selector.bounds.minX &&
        params.path("y").isIntegralNumber && params.path("y").asInt() == selector.bounds.minY &&
        params.path("z").isIntegralNumber && params.path("z").asInt() == selector.bounds.minZ) {
        "diagnostic negative must target exactly the immutable reviewed singleton watched position"
    }
    return selector
}

internal fun resolveDiagnosticGetterAction(action: DriverAction, spec: SuiteCase,
    purpose: ExecutionPurpose, input: CheckpointRef, scope: BoundFile?,
    authoring: JsonNode, store: CheckpointStore): DriverAction {
    if (action.id != DIAGNOSTIC_GETTER_ACTION) return action
    val selector = requireNotNull(diagnosticGetterSelector(spec,
        listOf(DriverStep("expected-negative-getter", action)), purpose, input.id))
    require(authoring.isObject && authoring.fieldNames().asSequence().toSet() ==
        setOf("dimension", "x", "y", "z", "source_checkpoint_sha256") &&
        authoring.path("source_checkpoint_sha256").asText() == DIAGNOSTIC_INPUT_SHA_MARKER &&
        authoring.path("dimension").asText() == selector.bounds.dimension &&
        authoring.path("x").asInt(Int.MIN_VALUE) == selector.bounds.minX &&
        authoring.path("y").asInt(Int.MIN_VALUE) == selector.bounds.minY &&
        authoring.path("z").asInt(Int.MIN_VALUE) == selector.bounds.minZ)
    val saved = readSealedBlockBaseline(store, input, selector.bounds.dimension,
        selector.bounds.minX, selector.bounds.minY, selector.bounds.minZ)
    require(saved.blockId == selector.resourceId && saved.blockId != "minecraft:air") {
        "diagnostic watched source block must be actual nonair Store-verified origin"
    }
    val bound = requireNotNull(scope)
    val document = strictCapturedJson(capturedEvidenceBytes(bound, 1_048_576))
    require(document.path("purpose").asText() == ExecutionPurpose.DIAGNOSTIC.wire &&
        document.path("source_checkpoint_id").asText() == input.id &&
        document.path("source_checkpoint_sha256").asText() == input.receipt.sha256 &&
        document.path("positions").isArray && document.path("positions").size() == 1 &&
        document.path("positions")[0].path("dimension").asText() == selector.bounds.dimension &&
        document.path("positions")[0].path("x").asInt(Int.MIN_VALUE) == selector.bounds.minX &&
        document.path("positions")[0].path("y").asInt(Int.MIN_VALUE) == selector.bounds.minY &&
        document.path("positions")[0].path("z").asInt(Int.MIN_VALUE) == selector.bounds.minZ)
    return action.copy(parameters = (action.parameters as ObjectNode).deepCopy().apply {
        put("scope_id", document.path("scope_id").asText()) })
}

/** Actual producer failure and stop are proven from same bounded native files; no other negative route granted. */
internal fun verifyExpectedDiagnosticGetterStop(store: CheckpointStore, input: CheckpointRef,
    scope: BoundFile, status: BoundFile, mailbox: Path, trial: ExecutedTrial,
    bindings: CheckpointBindings, epochId: String, nativeSessionId: String): JsonNode {
    require(bindings.purpose == ExecutionPurpose.DIAGNOSTIC.wire && trial.receipt.state == "passed" &&
        trial.receipt.observations.size == 1 && trial.planned.driver.clientRequired &&
        trial.planned.spec.targetBindings.size == 1)
    val scopeDoc = strictCapturedJson(capturedEvidenceBytes(scope, 1_048_576))
    val watched = scopeDoc.path("positions")
    require(watched.isArray && watched.size() == 1 &&
        scopeDoc.path("source_checkpoint_id").asText() == input.id &&
        scopeDoc.path("source_checkpoint_sha256").asText() == input.receipt.sha256 &&
        scopeDoc.path("purpose").asText() == bindings.purpose &&
        scopeDoc.path("epoch_id").asText() == epochId &&
        scopeDoc.path("native_session_id").asText() == nativeSessionId)
    val id = scopeDoc.path("scope_id").asText()
    val expected = mailbox.resolve("mutations/$id.status.json").toAbsolutePath().normalize()
    require(Path.of(status.path) == expected)
    val row = strictCapturedJson(capturedEvidenceBytes(status, 1_048_576))
    val payload = trial.receipt.observations.single().payload
    val pre = payload.path("block_mutation_pre_action_prefix")
    val after = payload.path("resulting_prefix")
    val trace = mailbox.resolve("mutations/$id.jsonl")
    require(trial.receipt.observations.single().driver == DIAGNOSTIC_GETTER_ACTION &&
        payload.path("schema").asText() == "bc.playtest_mutation_external_getter_negative.v1" &&
        payload.path("selector_id").asText() == GETTER_SELECTOR &&
        payload.path("selector_reviewed_by_native").isBoolean &&
        !payload.path("selector_reviewed_by_native").asBoolean() &&
        payload.path("scope_id").asText() == id &&
        payload.path("source_checkpoint_sha256").asText() == input.receipt.sha256 &&
        payload.path("epoch_id").asText() == epochId &&
        payload.path("native_session_id").asText() == nativeSessionId &&
        payload.path("real_callsite").asText() ==
            "LevelChunkSection.getStates() from native diagnostic action" &&
        payload.path("expected_scope_failure").asText() == GETTER_REASON &&
        payload.path("dimension") == watched[0].path("dimension") &&
        payload.path("x") == watched[0].path("x") && payload.path("y") == watched[0].path("y") &&
        payload.path("z") == watched[0].path("z") &&
        payload.path("natural_origin_proven_by_native").isBoolean &&
        !payload.path("natural_origin_proven_by_native").asBoolean() &&
        payload.path("negative_control_certifies_candidate_provider_by_native").isBoolean &&
        !payload.path("negative_control_certifies_candidate_provider_by_native").asBoolean() &&
        pre.path("scope_failure").isBoolean && !pre.path("scope_failure").asBoolean() &&
        pre.path("io_failure").isBoolean && !pre.path("io_failure").asBoolean() &&
        pre.path("overflow").isBoolean && !pre.path("overflow").asBoolean() &&
        pre.path("bytes").isIntegralNumber && pre.path("bytes").asLong(-1) == 0L &&
        pre.path("records").isIntegralNumber && pre.path("records").asInt(-1) == 0 &&
        after.path("scope_failure").isBoolean && after.path("scope_failure").asBoolean() &&
        after.path("io_failure").isBoolean && !after.path("io_failure").asBoolean() &&
        after.path("overflow").isBoolean && !after.path("overflow").asBoolean() &&
        after.path("bytes").isIntegralNumber && after.path("bytes").asLong(-1) == 0L &&
        after.path("records").isIntegralNumber && after.path("records").asInt(-1) == 0 &&
        after.path("failure_reason").asText() == GETTER_REASON &&
        after.path("scope_id").asText() == id &&
        row.path("schema").isTextual &&
        row.path("schema").asText() == "bc.playtest_block_mutation_status.v4" &&
        row.path("scope_failure").isBoolean && row.path("scope_failure").asBoolean() &&
        row.path("io_failure").isBoolean && !row.path("io_failure").asBoolean() &&
        row.path("overflow").isBoolean && !row.path("overflow").asBoolean() &&
        row.path("failure_reason").asText() == GETTER_REASON &&
        row.path("scope_id").asText() == id &&
        row.path("scope_file_sha256").asText() == scope.sha256 &&
        row.path("source_checkpoint_id").asText() == input.id &&
        row.path("source_checkpoint_sha256").asText() == input.receipt.sha256 &&
        row.path("lifecycle_context") == suiteMapper.valueToTree<JsonNode>(mapOf(
            "campaign_id" to bindings.campaignId, "lineage_id" to bindings.lineageId,
            "epoch_id" to epochId, "native_session_id" to nativeSessionId,
            "purpose" to bindings.purpose, "source" to "controller_prelaunch")) &&
        row.path("path").asText() == "mutations/$id.jsonl" &&
        Files.isRegularFile(trace, NOFOLLOW_LINKS) && !Files.isSymbolicLink(trace) &&
        Files.size(trace) == 0L && row.path("bytes").asLong(-1) == 0L &&
        row.path("sha256").asText() == Hashes.sha256(trace) &&
        row.path("records").asInt(-1) == 0 &&
        row.path("stop_tick").asLong(-1) >= 0 &&
        row.path("closed_monotonic_nanos").isIntegralNumber) {
        "diagnostic getter was not the exact real native failed scope at clean stop"
    }
    val position = suiteMapper.treeToValue(watched[0], ScopedBlockPosition::class.java)
    require(watched[0] == suiteMapper.valueToTree<JsonNode>(position))
    val replayed = replaySavedBlockMutationPrefix(store, input, scope, mailbox, pre,
        bindings, epochId, nativeSessionId, pristine = input.id == "origin")
    require(replayed.records == 0 && replayed.byteCount == 0)
    replayFailedDiagnosticStop(pre, after, row, setOf(birthChunkKey(position)), GETTER_REASON)
    return row
}
