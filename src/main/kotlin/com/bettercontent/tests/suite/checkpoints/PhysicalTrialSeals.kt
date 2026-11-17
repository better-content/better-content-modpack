package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import java.time.Instant
import java.nio.file.Path

/** Independently sealed sibling, never a merged branch output or restore dependency. */
internal data class PhysicalTrialSeal(val trialId: String, val sourceEpochId: String,
    val input: CheckpointRef, val output: CheckpointRef)

/** A physical correct/wrong trial has its OWN fresh process, save, and disposable Store child. */
internal fun sealIsolatedPhysicalTrial(store: CheckpointStore, input: CheckpointRef,
    outcome: EpochOutcome, expectedTrialId: String, manifestSha256: String,
    deadline: Instant, stopSealSeconds: Long): PhysicalTrialSeal {
    require(expectedTrialId in setOf("correct", "wrong") &&
        manifestSha256.matches(shaPattern) && stopSealSeconds in 1..2_592_000 &&
        outcome.trials.size == 1 && outcome.trials.single().planned.spec.id == "primitive.harvest" &&
        outcome.trials.single().planned.request.trialId == expectedTrialId &&
        outcome.epoch.inputCheckpoint == input && outcome.epoch.restored?.reference == input &&
        outcome.safelyClosed && outcome.epoch.closure?.saveValidated == true &&
        outcome.epoch.closure?.processCleanupComplete == true &&
        outcome.epoch.closure?.survivingPids?.isEmpty() == true &&
        outcome.stopStartedAt != null &&
        !Instant.now().isAfter(outcome.stopStartedAt.plusSeconds(stopSealSeconds)) &&
        Instant.now().isBefore(deadline)) {
        "physical trial cannot seal without its own real stopped, saved, fresh restored epoch"
    }
    store.verifyRestoreReceipt(requireNotNull(outcome.epoch.restored))
    val id = "physical-${manifestSha256.take(16)}-${outcome.epoch.id}-$expectedTrialId"
    val output = store.seal(id, outcome.roots, requireNotNull(outcome.epoch.closure), input)
    require(store.verify(output).parent == input &&
        store.verify(output).sourceEpoch == outcome.epoch.id &&
        !Instant.now().isAfter(outcome.stopStartedAt.plusSeconds(stopSealSeconds)) &&
        Instant.now().isBefore(deadline)) {
        "physical sibling seal exceeded the reviewed stop/save/seal or campaign deadline"
    }
    return PhysicalTrialSeal(expectedTrialId, outcome.epoch.id, input, output)
}

/** Reopen the exact campaign journal and BOTH sealed sibling receipts; a case result alone is not proof. */
internal fun replayPhysicalTrialSeals(store: CheckpointStore, campaignEvents: BoundFile,
    preparedInput: CheckpointRef, epochs: List<SessionEpoch>, manifestSha256: String): List<PhysicalTrialSeal> {
    require(manifestSha256.matches(shaPattern) && epochs.size == 2 &&
        epochs.map { it.id }.distinct().size == 2 &&
        epochs.map { it.nativeSessionId }.distinct().size == 2 &&
        epochs.all { it.inputCheckpoint == preparedInput &&
            it.restored?.reference == preparedInput && it.closure?.saveValidated == true &&
            it.closure?.processCleanupComplete == true &&
            it.closure?.survivingPids?.isEmpty() == true })
    val journal = Path.of(campaignEvents.path).toAbsolutePath().normalize()
    require(journal.fileName.toString() == "campaign-events.jsonl")
    val rows = strictCapturedLines(campaignEvents, maxBytes = 16_777_216,
        maxRows = 100_000, maxLine = 131_072)
        .filter { it.path("type").asText() == "physical_trial_checkpoint_sealed" &&
            it.path("case_id").asText() == "primitive.harvest" }
    require(rows.size == 2 && rows.map { it.path("trial_id").asText() } == listOf("correct", "wrong")) {
        "physical trial siblings were not separately journaled in frozen order"
    }
    val outputs = rows.zip(epochs).map { (row, epoch) ->
        val trialId = row.path("trial_id").asText()
        val ref = suiteMapper.treeToValue(row.path("output_checkpoint"), CheckpointRef::class.java)
        val expectedId = "physical-${manifestSha256.take(16)}-${epoch.id}-$trialId"
        val receipt = store.verify(ref)
        require(row.path("manifest_sha256").asText() == manifestSha256 &&
            row.path("source_epoch_id").asText() == epoch.id &&
            row.path("input_checkpoint") ==
            suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(preparedInput) &&
            ref.id == expectedId && receipt.parent == preparedInput &&
            receipt.sourceEpoch == epoch.id && receipt.closedEpoch == epoch.closure &&
            receipt.closedEpoch.nativeSessionId == epoch.nativeSessionId &&
            receipt.bindings == store.verify(preparedInput).bindings) {
            "physical child was not the matching independently stopped and sealed sibling"
        }
        PhysicalTrialSeal(trialId, epoch.id, preparedInput, ref)
    }
    require(outputs.map { it.output }.distinct().size == 2)
    return outputs
}
