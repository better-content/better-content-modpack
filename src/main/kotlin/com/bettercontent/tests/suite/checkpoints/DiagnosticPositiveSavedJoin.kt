package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.NativeRequest
import com.bettercontent.tests.suite.NativeObservation
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Path

/** Actual diagnostic positive, not a sealed-cell equality shortcut or natural/candidate receipt. */
internal fun replaySealedDiagnosticPositive(store: CheckpointStore,
    reviewed: PredeclaredDiagnosticMutationControl, seal: DiagnosticControlSeal,
    outcome: EpochOutcome): VerifiedPositiveMutationControl {
    require(loadPredeclaredDiagnosticMutationControl(store, reviewed.manifest, reviewed.setup,
        reviewed.caseId, reviewed.controlId) == reviewed &&
        seal.setup == reviewed.setup && seal.controlId == reviewed.controlId &&
        seal.caseId == reviewed.caseId && seal.sourceEpochId == outcome.epoch.id &&
        store.verify(seal.child).parent == reviewed.setup &&
        store.verify(seal.child).sourceEpoch == outcome.epoch.id &&
        outcome.trials.size == 1 && outcome.trials.single().planned.spec.id == reviewed.caseId &&
        outcome.trials.single().receipt.observations.size == 1)
    val epoch = outcome.epoch
    val journal = store.campaignPath().resolve("campaign-events.jsonl")
    require(replayDiagnosticControlSeals(store, BoundFile(journal.toString(), Hashes.sha256(journal)),
        reviewed, listOf(epoch)) == listOf(seal))
    val action = strictCapturedLines(requireNotNull(epoch.nativeRequests)).single { row ->
        row.path("driver").asText() == "block_mutation.control_positive_v1"
    }.let { suiteMapper.treeToValue(it, NativeRequest::class.java) }
    val observed = strictCapturedLines(outcome.trials.single().raw).single { row ->
        row.path("observation").path("driver").asText() == action.driver &&
            row.path("observation").path("kind").asText() == "completed"
    }.let { suiteMapper.treeToValue(it.path("observation"), NativeObservation::class.java) }
    val context = suiteMapper.treeToValue(action.parameters.path("witness_context"), WitnessContext::class.java)
    require(context.caseId == reviewed.caseId && context.actorUuid == reviewed.actorUuid &&
        context.epochId == epoch.id && context.nativeSessionId == epoch.nativeSessionId &&
        observed.requestSequence == action.sequence && observed.runId == epoch.nativeSessionId &&
        observed.payload == outcome.trials.single().receipt.observations.single().payload)
    val events = strictCapturedLines(epoch.eventLog)
    val ready = events.single { it.path("type").asText() == "epoch_ready" }
    val stopped = events.single { it.path("type").asText() == "block_mutation_stop_bound" }
    val scope = suiteMapper.treeToValue(ready.path("block_mutation_scope"), BoundFile::class.java)
    val status = suiteMapper.treeToValue(stopped.path("status"), BoundFile::class.java)
    require(stopped.path("scope") == suiteMapper.valueToTree<JsonNode>(scope) &&
        Hashes.sha256(Path.of(scope.path)) == scope.sha256)
    return replayPositiveMutationControl(store, reviewed.setup, seal.child, scope,
        Path.of(requireNotNull(epoch.nativeRequests).path).parent, status,
        store.verify(reviewed.setup).bindings, epoch.id, epoch.nativeSessionId,
        reviewed.controlId, reviewed.position, reviewed.expected,
        action, observed.payload, context, "block_mutation.control_positive_v1", reviewed.actorUuid)
}
