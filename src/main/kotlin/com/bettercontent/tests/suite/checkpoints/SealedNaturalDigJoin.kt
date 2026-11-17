package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Reopened ONLY after shutdown/save/seal; native's pickup flag is never saved-state proof. */
internal fun replaySealedNaturalDigJoin(store: CheckpointStore, input: CheckpointRef,
    output: CheckpointRef, epoch: SessionEpoch, caseId: String, trialId: String,
    trialFile: BoundFile, rawActions: BoundFile): ScopedSavedPlayerJoin {
    val trialDocument = strictCapturedJson(capturedEvidenceBytes(trialFile, 1_048_576))
    require(
        trialDocument.path("state").asText() == "passed" &&
        trialDocument.path("case_id").asText() == caseId && trialDocument.path("trial_id").asText() == trialId &&
        trialDocument.path("native_actions") == suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(rawActions) &&
        trialDocument.path("cleanup_complete").asBoolean(false) &&
        epoch.status == "passed" && epoch.inputCheckpoint == input &&
        store.verify(output).parent == input && store.verify(output).sourceEpoch == epoch.id &&
        epoch.closure?.processCleanupComplete == true && epoch.closure?.saveValidated == true &&
        epoch.caseResults.contains(trialFile)) { "only a shut down, sealed, source-epoch dig can join saved state" }
    val eventFile = Path.of(epoch.eventLog.path)
    val rawFile = Path.of(rawActions.path)
    require(Files.isRegularFile(eventFile, NOFOLLOW_LINKS) && Files.isRegularFile(rawFile, NOFOLLOW_LINKS) &&
        Hashes.sha256(eventFile) == epoch.eventLog.sha256 && Hashes.sha256(rawFile) == rawActions.sha256 &&
        rawFile.parent == eventFile.parent)
    val events = strictCapturedLines(epoch.eventLog, maxBytes = 10_000_000)
    val targetEvent = events.single { it.path("type").asText() == "natural_target_frozen" &&
        it.path("case_id").asText() == caseId &&
        it.path("trial_id").asText() == trialId }
    val targetFile = suiteMapper.treeToValue(targetEvent.path("target"), BoundFile::class.java)
    require(Path.of(targetFile.path).parent == eventFile.parent)
    val frozen = readFrozenNaturalTarget(targetFile)
    require(frozen.origin == input && frozen.bound.context.caseId == caseId &&
        frozen.bound.context.trialId == trialId &&
        frozen.bound.context.nativeSessionId == epoch.nativeSessionId &&
        targetEvent.path("profile") == suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(frozen.reviewedProfile))
    val raw = strictCapturedLines(rawActions).single { row ->
        row.path("observation").path("driver").asText() == "player.dig.witness_v1" }
    val terminal = suiteMapper.treeToValue(raw.path("observation"), NativeObservation::class.java)
    val requestRef = requireNotNull(epoch.nativeRequests)
    val requestsFile = Path.of(requestRef.path)
    val request = strictCapturedLines(requestRef).single { row ->
        row.path("sequence").asLong(-1) == terminal.requestSequence
    }.let { suiteMapper.treeToValue(it, NativeRequest::class.java) }
    val context = suiteMapper.treeToValue(request.parameters.path("witness_context"), WitnessContext::class.java)
    require(request.driver == "player.dig.witness_v1" && terminal.driver == request.driver &&
        request.runId == epoch.nativeSessionId && context.nativeSessionId == request.runId &&
        context.requestSequence == request.sequence && context.actorUuid == frozen.bound.context.actorUuid &&
        request.parameters.path("bound_target_sha256").asText() == frozen.bound.bindingSha256 &&
        request.parameters.path("face").asText() == frozen.ledger.selector.reviewedDigFace &&
        frozen.output.inputCheckpoint == input)
    val saved = replayScopedSavedPlayerJoin(store, input, output, frozen.output,
        requestsFile.parent, request, terminal.payload, context, frozen.bound)
    require(Hashes.sha256(eventFile) == epoch.eventLog.sha256 &&
        Hashes.sha256(rawFile) == rawActions.sha256 && Hashes.sha256(requestsFile) == epoch.nativeRequests.sha256)
    return saved
}
