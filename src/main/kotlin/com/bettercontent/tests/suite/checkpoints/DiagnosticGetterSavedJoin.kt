package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.NativeRequest
import com.bettercontent.tests.suite.NativeObservation
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Path

/** The origin-only external getter's saved state is checked AFTER real STOP and Store sealing. */
internal fun replaySealedDiagnosticGetter(store: CheckpointStore, origin: CheckpointRef,
    child: CheckpointRef, outcome: EpochOutcome): CheckpointRef {
    requirePristinePackOrigin(store, origin)
    val epoch = outcome.epoch
    val trial = outcome.trials.single()
    require(trial.planned.spec.id == DIAGNOSTIC_GETTER_CASE &&
        epoch.inputCheckpoint == origin && epoch.restored?.reference == origin &&
        store.verify(child).parent == origin && child.id == "diagnostic-getter-${epoch.id}" &&
        store.verify(child).sourceEpoch == epoch.id &&
        store.verify(child).bindings.purpose == ExecutionPurpose.DIAGNOSTIC.wire)
    val selector = suiteMapper.treeToValue(trial.planned.spec.targetBindings.single(),
        ReviewedTargetSelector::class.java)
    val bounds = selector.bounds
    require(selector.schema == "bc.playtest_target_selector.v1" &&
        selector.inputCheckpointId == origin.id &&
        selector.source == TargetSource.NATURAL_UNMODIFIED_BLOCK &&
        bounds.minX == bounds.maxX && bounds.minY == bounds.maxY && bounds.minZ == bounds.maxZ)
    val site = ScopedBlockPosition(bounds.dimension, bounds.minX, bounds.minY, bounds.minZ)
    val pre = readSealedBlockBaseline(store, origin, site.dimension, site.x, site.y, site.z)
    val post = readSealedBlockBaseline(store, child, site.dimension, site.x, site.y, site.z)
    require(pre.blockId == selector.resourceId && pre.blockId != "minecraft:air" &&
        post.blockId == pre.blockId && post.properties == pre.properties) {
        "expected getter must not mutate independently saved origin block state"
    }
    val journal = store.campaignPath().resolve("campaign-events.jsonl")
    val sealedEvent = strictCapturedLines(BoundFile(journal.toString(), Hashes.sha256(journal)),
        maxBytes = 16_777_216, maxRows = 100_000, maxLine = 131_072).single { row ->
        row.path("type").asText() == "diagnostic_getter_child_sealed" &&
            row.path("case_id").asText() == DIAGNOSTIC_GETTER_CASE
    }
    require(sealedEvent.path("origin") == suiteMapper.valueToTree<JsonNode>(origin) &&
        sealedEvent.path("child") == suiteMapper.valueToTree<JsonNode>(child) &&
        sealedEvent.path("source_epoch_id").asText() == epoch.id &&
        sealedEvent.path("not_natural_or_published").isBoolean &&
        sealedEvent.path("not_natural_or_published").asBoolean())
    val request = strictCapturedLines(requireNotNull(epoch.nativeRequests)).single { row ->
        row.path("driver").asText() == DIAGNOSTIC_GETTER_ACTION
    }.let { suiteMapper.treeToValue(it, NativeRequest::class.java) }
    val observed = strictCapturedLines(trial.raw).single { row ->
        row.path("observation").path("driver").asText() == DIAGNOSTIC_GETTER_ACTION &&
            row.path("observation").path("kind").asText() == "completed"
    }.let { suiteMapper.treeToValue(it.path("observation"), NativeObservation::class.java) }
    require(request.runId == epoch.nativeSessionId && observed.runId == request.runId &&
        observed.requestSequence == request.sequence &&
        observed.payload == trial.receipt.observations.single().payload &&
        request.parameters.isObject && request.parameters.fieldNames().asSequence().toSet() ==
            setOf("scope_id", "selector_id", "dimension", "x", "y", "z") &&
        request.parameters.path("selector_id").asText() == "state_container_exposed" &&
        request.parameters.path("dimension").asText() == site.dimension &&
        listOf("x", "y", "z").zip(listOf(site.x, site.y, site.z)).all { (name, value) ->
            request.parameters.path(name).isIntegralNumber &&
                request.parameters.path(name).canConvertToInt() &&
                request.parameters.path(name).asInt() == value })
    val events = strictCapturedLines(epoch.eventLog)
    val prepared = events.single { it.path("type").asText() == "epoch_ready" }
    val stopped = events.single { it.path("type").asText() ==
        "block_mutation_expected_diagnostic_failure_bound" &&
        it.path("selector_id").asText() == "state_container_exposed" }
    val scope = suiteMapper.treeToValue(prepared.path("block_mutation_scope"), BoundFile::class.java)
    val status = suiteMapper.treeToValue(stopped.path("status"), BoundFile::class.java)
    require(stopped.path("scope") == suiteMapper.valueToTree<JsonNode>(scope) &&
        stopped.path("preseal_only_not_control_acceptance").isBoolean &&
        stopped.path("preseal_only_not_control_acceptance").asBoolean() &&
        stopped.path("failure_reason").asText() == "mutable_state_container_exposed" &&
        request.parameters.path("scope_id").asText() ==
            strictCapturedJson(capturedEvidenceBytes(scope, 1_048_576)).path("scope_id").asText())
    verifyExpectedDiagnosticGetterStop(store, origin, scope, status,
        Path.of(requireNotNull(epoch.nativeRequests).path).parent, trial,
        store.verify(origin).bindings, epoch.id, epoch.nativeSessionId)
    return child
}
