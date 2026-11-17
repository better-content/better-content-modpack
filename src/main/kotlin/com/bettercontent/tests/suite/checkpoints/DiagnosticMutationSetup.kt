package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.NativeObservation
import com.bettercontent.tests.suite.NativeRequest
import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.resourcePattern
import java.util.UUID

/** Real authored setup writer: saved actor-ready onboarding parent -> authored opposite cell. */
internal data class PredeclaredDiagnosticMutationSetup(val manifest: BoundFile, val origin: CheckpointRef,
    val setupCheckpointId: String, val caseId: String, val actorUuid: String,
    val position: ScopedBlockPosition, val originState: String, val fixtureState: String)

/** Reject a post-hoc choice of site or actor, even when it matches the final saved fixture. */
internal fun loadPredeclaredDiagnosticMutationSetup(store: CheckpointStore, manifest: BoundFile,
    origin: CheckpointRef, setupCaseId: String): PredeclaredDiagnosticMutationSetup {
    val prepared = store.verify(origin)
    val pristineOrigin = requireNotNull(prepared.parent)
    requirePristinePackOrigin(store, pristineOrigin)
    require(prepared.bindings.purpose == ExecutionPurpose.DIAGNOSTIC.wire &&
        prepared.closedEpoch.saveValidated && prepared.closedEpoch.processCleanupComplete &&
        prepared.closedEpoch.survivingPids.isEmpty() &&
        Hashes.sha256(java.nio.file.Path.of(manifest.path)) == manifest.sha256)
    val doc = strictCapturedJson(capturedEvidenceBytes(manifest, 67_108_864))
    require(doc.path("purpose").asText() == ExecutionPurpose.DIAGNOSTIC.wire)
    val roster = doc.path("diagnostic_mutation_controls")
    require(roster.isArray && roster.size() in 1..16)
    val rows = roster.filter { it.path("setup_case_id").asText() == setupCaseId }
    require(setupCaseId == "control.mutation.setup-delegated" &&
        rows.filter { it.path("case_id").asText() in reviewedDiagnosticPositiveCases.keys }
            .associate { it.path("case_id").asText() to it.path("control_id").asText() } ==
                reviewedDiagnosticPositiveCases.filterKeys { it != setupCaseId } &&
        rows.map { it.path("case_id").asText() }.distinct().size == rows.size &&
        rows.all { row -> when (row.path("case_id").asText()) {
            in reviewedDiagnosticPositiveCases.keys -> row.path("schema").asText() ==
                "bc.playtest_diagnostic_mutation_plan.v2"
            in reviewedStage1NegativeCases.keys -> row.path("schema").asText() ==
                "bc.playtest_diagnostic_mutation_negative_plan.v2"
            else -> false
        } } &&
        rows.all { it.path("setup_parent_id").asText() == origin.id &&
        it.path("setup_checkpoint_id").asText() == rows.first().path("setup_checkpoint_id").asText() &&
        it.path("actor_uuid") == rows.first().path("actor_uuid") &&
        it.path("position") == rows.first().path("position") &&
        it.path("origin_state") == rows.first().path("origin_state") &&
        it.path("before_state") == rows.first().path("before_state") }) {
        "all sibling controls must share one immutable predeclared setup fixture and actor"
    }
    val journal = store.campaignPath().resolve("campaign-events.jsonl")
    val history = strictCapturedLines(com.bettercontent.tests.suite.BoundFile(journal.toString(),
        Hashes.sha256(journal)), maxBytes = 16_777_216, maxRows = 100_000, maxLine = 131_072)
    require(history.first().path("type").asText() == "diagnostic_control_plan_committed" &&
        history.first().path("committed_before_origin").asBoolean() &&
        history.first().path("manifest").path("sha256").asText() == manifest.sha256 &&
        history.first().path("entries") == roster &&
        history.indexOfFirst { it.path("type").asText() == "campaign_started" } == 1 &&
        history.any { it.path("type").asText() == "case_completed" &&
            it.path("case_id").asText() == "world.onboarding" && it.path("state").asText() == "passed" } &&
        history.any { it.path("type").asText() == "checkpoint_sealed" &&
            it.path("checkpoint") == suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(origin) }) {
        "diagnostic fixture actor preparation must be reviewed real onboarding saved after pre-origin commitment"
    }
    val onboardingBranch = doc.path("branches").filter { it.path("case_ids").any { id ->
        id.asText() == "world.onboarding" } }.single()
    require(onboardingBranch.path("case_ids").size() == 1 &&
        onboardingBranch.path("input_checkpoint").asText() == pristineOrigin.id &&
        onboardingBranch.path("output_checkpoint").asText() == origin.id &&
        prepared.parent == pristineOrigin &&
        prepared.closedEpoch.nativeSessionId.isNotBlank())
    val row = rows.first()
    val branch = doc.path("branches").filter { it.path("case_ids").any { id ->
        id.asText() == setupCaseId } }.single()
    require(branch.path("case_ids").isArray && branch.path("case_ids").size() == 1 &&
        branch.path("input_checkpoint").asText() == origin.id &&
        branch.path("output_checkpoint").asText() == row.path("setup_checkpoint_id").asText() &&
        branch.path("state_policy").asText() == "carry-forward") {
        "diagnostic fixture setup must be its own reviewed direct-origin producer branch"
    }
    val actor = row.path("actor_uuid").asText()
    require(runCatching { UUID.fromString(actor).toString() == actor }.getOrDefault(false) &&
        doc.path("driver_bindings").single { it.path("case_id").asText() == "world.onboarding" }
            .path("parameters").path("first_join").path("player_uuid").asText() == actor)
    val site = row.path("position")
    require(site.isObject && site.fieldNames().asSequence().toSet() == setOf("dimension", "x", "y", "z") &&
        site.path("dimension").asText().matches(resourcePattern))
    fun coordinate(name: String, range: IntRange): Int = site.path(name).let {
        require(it.isIntegralNumber && it.canConvertToInt() && it.asInt() in range); it.asInt()
    }
    val position = ScopedBlockPosition(site.path("dimension").asText(),
        coordinate("x", -30_000_000..30_000_000), coordinate("y", -2048..2048),
        coordinate("z", -30_000_000..30_000_000))
    fun literalState(name: String): String = row.path(name).let { state ->
        require(state.isObject && state.fieldNames().asSequence().toSet() == setOf("id", "properties") &&
            state.path("properties").isObject && state.path("properties").isEmpty &&
            state.path("id").asText() in setOf("minecraft:stone", "minecraft:air"))
        state.path("id").asText()
    }
    val before = literalState("origin_state"); val after = literalState("before_state")
    val ready = readSealedVanillaInventoryWithTags(prepared, actor)
    val savedPos = requireNotNull(ready.savedPosition) { "saved onboarding actor dimension/position absent" }
    val dx = savedPos.x - (position.x + .5)
    val dy = savedPos.y - (position.y + .5)
    val dz = savedPos.z - (position.z + .5)
    require(ready.inventory.savedGameModeId == 0 &&
        savedPos.dimension in setOf("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end") &&
        savedPos.x in -30_000_000.0..30_000_000.0 && savedPos.y in -2048.0..2048.0 &&
        savedPos.z in -30_000_000.0..30_000_000.0 &&
        savedPos.dimension == position.dimension &&
        dx * dx + dy * dy + dz * dz <= 32.0 * 32.0 && before != after &&
        listOf(pristineOrigin, origin).all { source ->
            readSealedBlockBaseline(store, source, position.dimension, position.x, position.y,
                position.z).let { it.blockId == before && it.properties.isEmpty() }
        }) {
        "actor-ready sealed Survival onboarding and immutable nearby authored setup site are required"
    }
    return PredeclaredDiagnosticMutationSetup(manifest, origin,
        row.path("setup_checkpoint_id").asText(), setupCaseId, actor, position, before, after)
}

/** Reopen original action/STOP bytes and independent saved setup child before publishing fixture. */
internal fun replaySealedDiagnosticSetupControl(store: CheckpointStore,
    reviewed: PredeclaredDiagnosticMutationSetup, child: CheckpointRef,
    outcome: EpochOutcome): VerifiedPositiveMutationControl {
    require(outcome.trials.size == 1 && outcome.trials.single().planned.spec.id == reviewed.caseId &&
        store.verify(child).parent == reviewed.origin && child.id == reviewed.setupCheckpointId)
    val epoch = outcome.epoch
    val action = strictCapturedLines(requireNotNull(epoch.nativeRequests)).single { row ->
        row.path("driver").asText() == "block_mutation.control_positive_v1"
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
    val stopped = events.single { it.path("type").asText() == "block_mutation_stop_bound" }
    val scope = suiteMapper.treeToValue(prepared.path("block_mutation_scope"), BoundFile::class.java)
    val status = suiteMapper.treeToValue(stopped.path("status"), BoundFile::class.java)
    require(stopped.path("scope") == suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(scope) &&
        Hashes.sha256(java.nio.file.Path.of(scope.path)) == scope.sha256)
    val origin = reviewed.originState to emptyMap<String, String>()
    val fixture = reviewed.fixtureState to emptyMap<String, String>()
    return replayPositiveMutationControl(store, reviewed.origin, child, scope,
        java.nio.file.Path.of(requireNotNull(epoch.nativeRequests).path).parent, status,
        store.verify(reviewed.origin).bindings, epoch.id, epoch.nativeSessionId,
        "level_chunk_delegated", reviewed.position,
        listOf(ReviewedMutationTransition(1, "PalettedContainer.getAndSet_private_index_return",
            origin, fixture)), action, observation.payload, context,
        "block_mutation.control_positive_v1", reviewed.actorUuid)
}
