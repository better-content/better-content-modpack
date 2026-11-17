package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.client.CONTROLLER_CONTEXT_MARKER
import com.bettercontent.tests.suite.client.OBSERVED_ACTOR_MARKER
import com.bettercontent.tests.suite.drivers.DriverAction
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

internal val reviewedDiagnosticPositiveCases = mapOf(
    "control.mutation.setup-delegated" to "level_chunk_delegated",
    "control.mutation.delegated-positive" to "level_chunk_delegated",
    "control.mutation.section-positive" to "direct_section_setter",
    "control.mutation.transient-positive" to "transient_restoration")

/** Authored disposable fixture, NEVER the naturally unmodified origin or a qualified block. */
internal fun requirePredeclaredDiagnosticOrdinal(value: JsonNode, index: Int): Int {
    require(index in 0..1 && value.isIntegralNumber && value.canConvertToInt() &&
        value.asInt() == index + 1) { "reviewed diagnostic ordinal must be exact bounded integer" }
    return value.asInt()
}

internal data class PredeclaredDiagnosticMutationControl(val manifest: BoundFile,
    val setup: CheckpointRef, val caseId: String, val controlId: String, val actorUuid: String,
    val position: ScopedBlockPosition, val before: Pair<String, Map<String, String>>,
    val expected: List<ReviewedMutationTransition>) {
    init {
        require(controlId in stage1SafePositiveControlIds && setup.id != "origin" &&
            before.first in setOf("minecraft:stone", "minecraft:air") && before.second.isEmpty() &&
            expected.isNotEmpty() && expected.size == if (controlId == "transient_restoration") 2 else 1)
    }
}

/** Only controller-allocated real actor/scope/sequence replaces exact authored markers. */
internal fun resolvePredeclaredStage1DiagnosticAction(action: DriverAction, scope: BoundFile,
    actor: String, context: WitnessContext): DriverAction {
    require(action.id in setOf("block_mutation.control_positive_v1",
        "block_mutation.control_expose_sections_v1", "block_mutation.control_io_append_failure_v1") &&
        action.source == "server" && context.purpose == ExecutionPurpose.DIAGNOSTIC.wire &&
        context.source == "server" && context.capabilityId == action.id && context.actorUuid == actor &&
        action.parameters.isObject && action.parameters.path("player_uuid").asText() == OBSERVED_ACTOR_MARKER &&
        action.parameters.path("scope_id").asText() == DIAGNOSTIC_MUTATION_SCOPE_MARKER &&
        action.parameters.path("witness_context").asText() == CONTROLLER_CONTEXT_MARKER)
    val config = strictCapturedJson(capturedEvidenceBytes(scope, 1_048_576))
    require(config.path("purpose").asText() == ExecutionPurpose.DIAGNOSTIC.wire &&
        config.path("epoch_id").asText() == context.epochId &&
        config.path("native_session_id").asText() == context.nativeSessionId &&
        config.path("positions").isArray && config.path("positions").size() == 1 &&
        config.path("positions")[0].let { site ->
            action.parameters.path("dimension") == site.path("dimension") &&
                action.parameters.path("x") == site.path("x") &&
                action.parameters.path("y") == site.path("y") &&
                action.parameters.path("z") == site.path("z")
        })
    return action.copy(parameters = (action.parameters as ObjectNode).deepCopy().apply {
        put("player_uuid", actor); put("scope_id", config.path("scope_id").asText())
        set<com.fasterxml.jackson.databind.JsonNode>("witness_context", suiteMapper.valueToTree(context))
    })
}

/**
 * The reviewer must author this CLOSED roster in the exact campaign manifest BEFORE the setup
 * world and every fresh trial. There is deliberately no fallback to a selected live block, a
 * native claimed expectation or a caller-provided Boolean. An absent roster blocks the route.
 */
internal fun loadPredeclaredDiagnosticMutationControl(store: CheckpointStore, manifest: BoundFile,
    setup: CheckpointRef, caseId: String, controlId: String): PredeclaredDiagnosticMutationControl {
    require(manifest.sha256.matches(shaPattern) && controlId in stage1SafePositiveControlIds &&
        reviewedDiagnosticPositiveCases[caseId] == controlId &&
        caseId != "control.mutation.setup-delegated")
    val document = strictCapturedJson(capturedEvidenceBytes(manifest, 67_108_864))
    val purpose = document.path("purpose")
    // This field must be present in the REVIEWED authoring manifest; never synthesize it here.
    require(purpose.isTextual && purpose.asText() == ExecutionPurpose.DIAGNOSTIC.wire)
    val roster = document.path("diagnostic_mutation_controls")
    require(roster.isArray && roster.size() in 1..16)
    val authored = roster.filter { it.path("case_id").asText() == caseId &&
        it.path("control_id").asText() == controlId }.single()
    require(authored.isObject && authored.fieldNames().asSequence().toSet() == setOf(
        "schema", "case_id", "control_id", "setup_case_id", "setup_checkpoint_id", "setup_parent_id",
        "actor_uuid", "position", "origin_state", "before_state", "transitions") &&
        authored.path("schema").asText() == "bc.playtest_diagnostic_mutation_plan.v2" &&
        authored.path("setup_checkpoint_id").isTextual &&
        authored.path("setup_checkpoint_id").asText() == setup.id &&
        authored.path("setup_case_id").isTextual &&
        authored.path("setup_case_id").asText().startsWith("control.mutation.setup-") &&
        authored.path("setup_parent_id").asText() == store.verify(setup).parent?.id &&
        caseId in reviewedDiagnosticPositiveCases.keys)
    val receipt = store.verify(setup)
    val campaignRoot = store.campaignPath()
    val events = campaignRoot.resolve("campaign-events.jsonl")
    val rows = strictCapturedLines(BoundFile(events.toString(), Hashes.sha256(events)),
        maxBytes = 16_777_216, maxRows = 100_000, maxLine = 131_072)
    val commitment = rows.first()
    val budgetFile = campaignRoot.resolve("execution-budget.json")
    val budget = strictCapturedJson(capturedEvidenceBytes(BoundFile(
        budgetFile.toString(), Hashes.sha256(budgetFile)), 1_048_576))
    require(commitment.path("type").asText() == "diagnostic_control_plan_committed" &&
        commitment.path("committed_before_origin").isBoolean &&
        commitment.path("committed_before_origin").asBoolean() &&
        commitment.path("manifest").path("sha256").asText() == manifest.sha256 &&
        commitment.path("manifest").path("path").asText() == manifest.path &&
        commitment.path("budget_binding").path("path").asText() == budgetFile.toString() &&
        commitment.path("budget_binding").path("sha256").asText() == Hashes.sha256(budgetFile) &&
        budget.path("manifest_sha256").asText() == manifest.sha256 &&
        commitment.path("entries") == roster &&
        rows.indexOfFirst { it.path("type").asText() == "campaign_started" } == 1 &&
        rows.indexOfFirst { it.path("type").asText() == "diagnostic_setup_checkpoint_sealed" &&
            it.path("checkpoint").path("id").asText() == setup.id &&
            it.path("checkpoint").path("receipt").path("sha256").asText() == setup.receipt.sha256 &&
            it.path("source_epoch").asText() == receipt.sourceEpoch &&
            it.path("manifest_sha256").asText() == manifest.sha256 &&
            it.path("not_natural_or_published").isBoolean &&
            it.path("not_natural_or_published").asBoolean() } > 1) {
        "diagnostic fixture/transitions were not committed by the reviewed manifest before setup"
    }
    val prepared = requireNotNull(receipt.parent)
    val setupRole = loadPredeclaredDiagnosticMutationSetup(store, manifest, prepared,
        authored.path("setup_case_id").asText())
    require(setupRole.setupCheckpointId == setup.id && setupRole.actorUuid == authored.path("actor_uuid").asText() &&
        receipt.bindings.purpose == ExecutionPurpose.DIAGNOSTIC.wire &&
        receipt.closedEpoch.saveValidated && receipt.closedEpoch.processCleanupComplete &&
        receipt.closedEpoch.survivingPids.isEmpty()) {
        "diagnostic setup is not a separately sealed, safely closed child of pristine origin"
    }
    val actor = authored.path("actor_uuid")
    require(actor.isTextual && runCatching { UUID.fromString(actor.asText()).toString() == actor.asText() }.getOrDefault(false))
    val site = authored.path("position")
    require(site.isObject && site.fieldNames().asSequence().toSet() == setOf("dimension", "x", "y", "z") &&
        site.path("dimension").isTextual && site.path("dimension").asText().matches(resourcePattern))
    fun coordinate(name: String, range: IntRange): Int = site.path(name).let {
        require(it.isIntegralNumber && it.canConvertToInt() && it.asInt() in range)
        it.asInt()
    }
    val position = ScopedBlockPosition(site.path("dimension").asText(),
        coordinate("x", -30_000_000..30_000_000), coordinate("y", -2048..2048),
        coordinate("z", -30_000_000..30_000_000))
    fun state(row: JsonNode): Pair<String, Map<String, String>> {
        require(row.isObject && row.fieldNames().asSequence().toSet() == setOf("id", "properties") &&
            row.path("id").isTextual && row.path("properties").isObject && row.path("properties").isEmpty)
        return row.path("id").asText() to emptyMap()
    }
    val before = state(authored.path("before_state"))
    val originState = state(authored.path("origin_state"))
    require(before.first in setOf("minecraft:stone", "minecraft:air") &&
        originState.first in setOf("minecraft:stone", "minecraft:air") &&
        before.first != originState.first)
    val pristineBlock = readSealedBlockBaseline(store, prepared, position.dimension,
        position.x, position.y, position.z)
    require((pristineBlock.blockId to pristineBlock.properties) == originState) {
        "authored fixture setup input differs from pristine sealed origin"
    }
    val saved = readSealedBlockBaseline(store, setup, position.dimension, position.x, position.y, position.z)
    require((saved.blockId to saved.properties) == before) {
        "authored fixture does not match independently sealed saved setup cell"
    }
    readSealedVanillaInventoryWithTags(receipt, actor.asText()) // actual saved actor, not a chosen native echo
    val expectedCount = if (controlId == "transient_restoration") 2 else 1
    val raw = authored.path("transitions")
    require(raw.isArray && raw.size() == expectedCount)
    val expected = raw.mapIndexed { index, row ->
        require(row.isObject && row.fieldNames().asSequence().toSet() ==
            setOf("ordinal", "observed_path", "old_state", "new_state") &&
            row.path("observed_path").asText() == "PalettedContainer.getAndSet_private_index_return")
        requirePredeclaredDiagnosticOrdinal(row.path("ordinal"), index)
        ReviewedMutationTransition(index + 1, row.path("observed_path").asText(),
            state(row.path("old_state")), state(row.path("new_state")))
    }
    val opposite = (if (before.first == "minecraft:stone") "minecraft:air" else "minecraft:stone") to emptyMap<String, String>()
    require(expected.first().oldState == before && expected.first().newState == opposite &&
        (expectedCount == 1 || expected[1].oldState == opposite && expected[1].newState == before)) {
        "authored expectation is not the literal predeclared stone/air public setter route"
    }
    return PredeclaredDiagnosticMutationControl(manifest, setup, caseId, controlId,
        actor.asText(), position, before, expected)
}
