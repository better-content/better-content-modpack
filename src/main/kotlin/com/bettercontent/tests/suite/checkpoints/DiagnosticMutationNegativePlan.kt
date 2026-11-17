package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.NativeRequest
import com.bettercontent.tests.suite.NativeObservation
import com.bettercontent.tests.suite.SuiteCase
import com.bettercontent.tests.suite.drivers.CaseDriver
import com.bettercontent.tests.suite.drivers.DriverStep
import com.bettercontent.tests.suite.client.OBSERVED_ACTOR_MARKER
import com.bettercontent.tests.suite.client.CONTROLLER_CONTEXT_MARKER
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Path

/** A separately precommitted fixture descendant; no failed scope may become natural coverage. */
internal data class PredeclaredDiagnosticMutationNegative(val manifest: BoundFile,
    val setup: CheckpointRef, val caseId: String, val selectorId: String, val actionId: String,
    val actorUuid: String, val position: ScopedBlockPosition, val before: String,
    val after: String, val failureReason: String)

internal val reviewedStage1NegativeActions = mapOf(
    "section_array_exposed" to "block_mutation.control_expose_sections_v1",
    "cap_or_io_failure" to "block_mutation.control_io_append_failure_v1")
internal val reviewedStage1NegativeCases = mapOf(
    "control.mutation.sections-negative" to "section_array_exposed",
    "control.mutation.io-append-negative" to "cap_or_io_failure")

/** Check the author-selected control/site before creating ANY world or campaign directory. */
internal fun validateAuthoredDiagnosticNegativePlan(manifest: JsonNode, spec: SuiteCase,
    branch: BranchSpec, driver: CaseDriver, parameters: JsonNode, plan: List<DriverStep>,
    purpose: ExecutionPurpose) {
    val selector = requireNotNull(reviewedStage1NegativeCases[spec.id])
    val actionId = reviewedStage1NegativeActions.getValue(selector)
    require(purpose == ExecutionPurpose.DIAGNOSTIC && driver.clientRequired &&
        spec.layer == "campaign" && spec.targetBindings.isEmpty() && spec.assertions.isEmpty() &&
        spec.evidenceKinds.toSet() == setOf("native-interaction") && plan.size == 1 &&
        branch.caseIds == listOf(spec.id) && branch.statePolicy == "isolated" &&
        branch.outputCheckpoint == null && branch.inputCheckpoint != "origin" &&
        parameters.isObject && parameters.fieldNames().asSequence().toSet() == setOf("dimension", "x", "y", "z"))
    val action = plan.single().action
    val row = manifest.path("diagnostic_mutation_controls").takeIf { it.isArray }?.filter {
        it.path("case_id").asText() == spec.id }?.single()
        ?: error("negative action was not in the reviewed pre-origin manifest roster")
    require(row.path("schema").asText() == "bc.playtest_diagnostic_mutation_negative_plan.v2" &&
        row.path("control_id").asText() == selector && row.path("action_id").asText() == actionId &&
        row.path("setup_checkpoint_id").asText() == branch.inputCheckpoint &&
        row.path("setup_parent_id").isTextual &&
        row.path("setup_parent_id").asText() != "origin" &&
        row.path("position").isObject &&
        row.path("position").fieldNames().asSequence().toSet() == setOf("dimension", "x", "y", "z") &&
        row.path("position") == parameters &&
        action.id == actionId && action.source == "server" &&
        action.parameters.isObject && action.parameters.fieldNames().asSequence().toSet() == setOf(
            "player_uuid", "scope_id", "selector_id", "dimension", "x", "y", "z", "witness_context") &&
        action.parameters.path("player_uuid").asText() == OBSERVED_ACTOR_MARKER &&
        action.parameters.path("scope_id").asText() == DIAGNOSTIC_MUTATION_SCOPE_MARKER &&
        action.parameters.path("witness_context").asText() == CONTROLLER_CONTEXT_MARKER &&
        action.parameters.path("selector_id").asText() == selector &&
        listOf("dimension", "x", "y", "z").all {
            action.parameters.path(it) == parameters.path(it) }) {
        "negative step is not the exact immutable authored site/selector and client-joined action"
    }
}

/** Full roster was committed as the FIRST campaign journal event before pristine origin. */
internal fun loadPredeclaredDiagnosticMutationNegative(store: CheckpointStore,
    manifest: BoundFile, setup: CheckpointRef, caseId: String, actionId: String): PredeclaredDiagnosticMutationNegative {
    require(actionId in reviewedStage1NegativeActions.values &&
        Hashes.sha256(Path.of(manifest.path)) == manifest.sha256 && setup.id != "origin")
    val document = strictCapturedJson(capturedEvidenceBytes(manifest, 67_108_864))
    require(document.path("purpose").asText() == ExecutionPurpose.DIAGNOSTIC.wire)
    val roster = document.path("diagnostic_mutation_controls")
    require(roster.isArray && roster.size() in 2..16)
    val row = roster.filter { it.path("case_id").asText() == caseId &&
        it.path("action_id").asText() == actionId }.single()
    val selector = reviewedStage1NegativeActions.entries.single { it.value == actionId }.key
    require(row.isObject && row.fieldNames().asSequence().toSet() == setOf(
        "schema", "case_id", "control_id", "action_id", "setup_case_id", "setup_checkpoint_id",
        "setup_parent_id", "actor_uuid", "position", "origin_state", "before_state",
        "expected_after_state", "expected_failure_reason") &&
        row.path("schema").asText() == "bc.playtest_diagnostic_mutation_negative_plan.v2" &&
        row.path("control_id").asText() == selector && row.path("action_id").asText() == actionId &&
        row.path("case_id").asText() == caseId &&
        reviewedStage1NegativeCases[caseId] == selector &&
        roster.count { it.path("case_id").asText() == caseId } == 1 &&
        row.path("setup_checkpoint_id").asText() == setup.id &&
        row.path("setup_parent_id").asText() == store.verify(setup).parent?.id)
    val setupCase = row.path("setup_case_id").asText()
    val delegatedTrial = roster.single { it.path("setup_case_id").asText() == setupCase &&
        it.path("case_id").asText() == "control.mutation.delegated-positive" &&
        it.path("control_id").asText() == "level_chunk_delegated" }
    val reviewedWriter = loadPredeclaredDiagnosticMutationControl(store, manifest, setup,
        "control.mutation.delegated-positive", "level_chunk_delegated")
    require(row.path("actor_uuid").asText() == reviewedWriter.actorUuid &&
        row.path("position") == suiteMapper.valueToTree<JsonNode>(reviewedWriter.position) &&
        row.path("before_state") == suiteMapper.valueToTree<JsonNode>(mapOf(
            "id" to reviewedWriter.before.first, "properties" to reviewedWriter.before.second)) &&
        row.path("origin_state") == delegatedTrial.path("origin_state")) {
        "negative selector did not descend from the precommitted independently sealed authored fixture"
    }
    val branch = document.path("branches").filter { it.path("case_ids").any { c ->
        c.asText() == caseId } }.single()
    require(branch.path("case_ids").isArray && branch.path("case_ids").size() == 1 &&
        branch.path("input_checkpoint").asText() == setup.id &&
        branch.path("output_checkpoint").isNull && branch.path("state_policy").asText() == "isolated") {
        "real expected negative needs its own isolated fresh process with no published output"
    }
    val before = reviewedWriter.before.first
    val after = if (selector == "section_array_exposed") before
        else if (before == "minecraft:stone") "minecraft:air" else "minecraft:stone"
    val expectedReason = if (selector == "section_array_exposed") "mutable_section_array_exposed"
        else "mutation_append_failed:ClosedChannelException"
    require(row.path("expected_after_state") == suiteMapper.valueToTree<JsonNode>(mapOf(
        "id" to after, "properties" to emptyMap<String, String>())) &&
        row.path("expected_failure_reason").asText() == expectedReason &&
        row.path("position").path("dimension").asText().matches(resourcePattern))
    return PredeclaredDiagnosticMutationNegative(manifest, setup, caseId, selector,
        actionId, reviewedWriter.actorUuid, reviewedWriter.position, before, after, expectedReason)
}

/** Reopen journal events and actual native request/response bytes only AFTER the child is Store sealed. */
internal fun replaySealedDiagnosticNegative(store: CheckpointStore,
    reviewed: PredeclaredDiagnosticMutationNegative, child: CheckpointRef,
    outcome: EpochOutcome): ReplayedStage1Negative {
    require(loadPredeclaredDiagnosticMutationNegative(store, reviewed.manifest, reviewed.setup,
        reviewed.caseId, reviewed.actionId) == reviewed &&
        child.id == "diagnostic-negative-${reviewed.manifest.sha256.take(16)}-${outcome.epoch.id}-${reviewed.selectorId}" &&
        store.verify(child).parent == reviewed.setup &&
        store.verify(child).sourceEpoch == outcome.epoch.id &&
        outcome.trials.size == 1 && outcome.trials.single().planned.spec.id == reviewed.caseId &&
        outcome.trials.single().receipt.observations.size == 1)
    val epoch = outcome.epoch
    val journal = store.campaignPath().resolve("campaign-events.jsonl")
    val sealedEvent = strictCapturedLines(BoundFile(journal.toString(), Hashes.sha256(journal)),
        maxBytes = 16_777_216, maxRows = 100_000, maxLine = 131_072).single { row ->
        row.path("type").asText() == "diagnostic_negative_child_sealed" &&
            row.path("case_id").asText() == reviewed.caseId &&
            row.path("selector_id").asText() == reviewed.selectorId
    }
    require(sealedEvent.path("manifest_sha256").asText() == reviewed.manifest.sha256 &&
        sealedEvent.path("setup") == suiteMapper.valueToTree<JsonNode>(reviewed.setup) &&
        sealedEvent.path("child") == suiteMapper.valueToTree<JsonNode>(child) &&
        sealedEvent.path("source_epoch_id").asText() == epoch.id &&
        sealedEvent.path("not_natural_or_published").isBoolean &&
        sealedEvent.path("not_natural_or_published").asBoolean())
    val action = strictCapturedLines(requireNotNull(epoch.nativeRequests)).single { row ->
        row.path("driver").asText() == reviewed.actionId
    }.let { suiteMapper.treeToValue(it, NativeRequest::class.java) }
    val observation = strictCapturedLines(outcome.trials.single().raw).single { row ->
        row.path("observation").path("driver").asText() == action.driver &&
            row.path("observation").path("kind").asText() == "completed"
    }.let { suiteMapper.treeToValue(it.path("observation"), NativeObservation::class.java) }
    val context = suiteMapper.treeToValue(action.parameters.path("witness_context"), WitnessContext::class.java)
    require(context.caseId == reviewed.caseId && context.actorUuid == reviewed.actorUuid &&
        context.epochId == epoch.id && context.nativeSessionId == epoch.nativeSessionId &&
        observation.requestSequence == action.sequence && observation.runId == epoch.nativeSessionId)
    val events = strictCapturedLines(epoch.eventLog)
    val prepared = events.single { it.path("type").asText() == "epoch_ready" }
    val stopped = events.single { it.path("type").asText() == "block_mutation_expected_diagnostic_failure_bound" &&
        it.path("selector_id").asText() == reviewed.selectorId }
    val scope = suiteMapper.treeToValue(prepared.path("block_mutation_scope"), BoundFile::class.java)
    val status = suiteMapper.treeToValue(stopped.path("status"), BoundFile::class.java)
    require(stopped.path("scope") == suiteMapper.valueToTree<JsonNode>(scope) &&
        stopped.path("preseal_only_not_control_acceptance").isBoolean &&
        stopped.path("preseal_only_not_control_acceptance").asBoolean() &&
        stopped.path("failure_reason").asText() == reviewed.failureReason)
    return replayStage1RealNegative(store, reviewed.setup, child, scope, status,
        Path.of(requireNotNull(epoch.nativeRequests).path).parent, reviewed.position,
        reviewed.actorUuid, action, observation.payload, context)
}
