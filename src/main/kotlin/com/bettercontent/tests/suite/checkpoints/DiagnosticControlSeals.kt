package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.shaPattern
import java.time.Instant

/** A disposable observed child, never a natural target or a qualified campaign output. */
internal fun sealIsolatedDiagnosticSetupTrial(store: CheckpointStore,
    reviewed: PredeclaredDiagnosticMutationSetup, outcome: EpochOutcome,
    deadline: Instant, stopSealSeconds: Long): CheckpointRef {
    require(stopSealSeconds in 1..2_592_000 &&
        outcome.trials.size == 1 && outcome.trials.single().planned.spec.id == reviewed.caseId &&
        outcome.epoch.inputCheckpoint == reviewed.origin &&
        outcome.epoch.restored?.reference == reviewed.origin &&
        outcome.safelyClosed && outcome.epoch.closure?.saveValidated == true &&
        outcome.epoch.closure?.processCleanupComplete == true &&
        outcome.epoch.closure?.survivingPids?.isEmpty() == true &&
        outcome.stopStartedAt != null &&
        !Instant.now().isAfter(outcome.stopStartedAt.plusSeconds(stopSealSeconds)) &&
        Instant.now().isBefore(deadline)) {
        "authored fixture setup requires a real independently saved/stopped fresh origin child"
    }
    store.verifyRestoreReceipt(requireNotNull(outcome.epoch.restored))
    val child = store.seal(reviewed.setupCheckpointId, outcome.roots,
        requireNotNull(outcome.epoch.closure), reviewed.origin)
    require(store.verify(child).parent == reviewed.origin &&
        store.verify(child).sourceEpoch == outcome.epoch.id &&
        !Instant.now().isAfter(outcome.stopStartedAt.plusSeconds(stopSealSeconds)))
    return child
}

internal data class DiagnosticControlSeal(val caseId: String, val controlId: String,
    val sourceEpochId: String, val setup: CheckpointRef, val child: CheckpointRef)

/** Safely retain a failed behavioral control too; do not publish it as a passed branch. */
internal fun sealIsolatedDiagnosticControlTrial(store: CheckpointStore,
    reviewed: PredeclaredDiagnosticMutationControl, outcome: EpochOutcome,
    deadline: Instant, stopSealSeconds: Long): DiagnosticControlSeal {
    val setup = reviewed.setup
    require(reviewed.manifest.sha256.matches(shaPattern) && stopSealSeconds in 1..2_592_000 &&
        store.verify(setup).bindings.purpose == ExecutionPurpose.DIAGNOSTIC.wire &&
        outcome.trials.size == 1 && outcome.trials.single().planned.spec.id == reviewed.caseId &&
        outcome.epoch.inputCheckpoint == setup && outcome.epoch.restored?.reference == setup &&
        outcome.safelyClosed && outcome.epoch.closure?.saveValidated == true &&
        outcome.epoch.closure?.processCleanupComplete == true &&
        outcome.epoch.closure?.survivingPids?.isEmpty() == true && outcome.stopStartedAt != null &&
        !Instant.now().isAfter(outcome.stopStartedAt.plusSeconds(stopSealSeconds)) &&
        Instant.now().isBefore(deadline)) {
        "diagnostic control child requires its OWN real restored, stopped, saved, safely closed process"
    }
    store.verifyRestoreReceipt(requireNotNull(outcome.epoch.restored))
    val id = "diagnostic-${reviewed.manifest.sha256.take(16)}-${outcome.epoch.id}-${reviewed.controlId}"
    val child = store.seal(id, outcome.roots, requireNotNull(outcome.epoch.closure), setup)
    require(store.verify(child).parent == setup && store.verify(child).sourceEpoch == outcome.epoch.id &&
        !Instant.now().isAfter(outcome.stopStartedAt.plusSeconds(stopSealSeconds)) &&
        Instant.now().isBefore(deadline))
    return DiagnosticControlSeal(reviewed.caseId, reviewed.controlId, outcome.epoch.id, setup, child)
}

/** A failed negative remains a safely saved disposable child, never a branch output. */
internal fun sealIsolatedDiagnosticNegativeTrial(store: CheckpointStore,
    reviewed: PredeclaredDiagnosticMutationNegative, outcome: EpochOutcome,
    deadline: Instant, stopSealSeconds: Long): CheckpointRef {
    require(stopSealSeconds in 1..2_592_000 &&
        outcome.trials.size == 1 && outcome.trials.single().planned.spec.id == reviewed.caseId &&
        outcome.trials.single().receipt.state == "passed" &&
        outcome.epoch.inputCheckpoint == reviewed.setup &&
        outcome.epoch.restored?.reference == reviewed.setup &&
        outcome.safelyClosed && outcome.epoch.status == "passed" &&
        outcome.epoch.closure?.saveValidated == true &&
        outcome.epoch.closure?.processCleanupComplete == true &&
        outcome.epoch.closure?.survivingPids?.isEmpty() == true &&
        outcome.stopStartedAt != null &&
        !Instant.now().isAfter(outcome.stopStartedAt.plusSeconds(stopSealSeconds)) &&
        Instant.now().isBefore(deadline)) {
        "expected-negative child requires one independently restored/stopped/saved cleanly closed process"
    }
    store.verifyRestoreReceipt(requireNotNull(outcome.epoch.restored))
    val id = "diagnostic-negative-${reviewed.manifest.sha256.take(16)}-${outcome.epoch.id}-${reviewed.selectorId}"
    val child = store.seal(id, outcome.roots, requireNotNull(outcome.epoch.closure), reviewed.setup)
    require(store.verify(child).parent == reviewed.setup &&
        store.verify(child).sourceEpoch == outcome.epoch.id &&
        !Instant.now().isAfter(outcome.stopStartedAt.plusSeconds(stopSealSeconds)))
    return child
}

/** Origin getter also needs its own saved disposable child, not merely a failed native pointer. */
internal fun sealIsolatedDiagnosticGetterTrial(store: CheckpointStore, origin: CheckpointRef,
    outcome: EpochOutcome, deadline: Instant, stopSealSeconds: Long): CheckpointRef {
    requirePristinePackOrigin(store, origin)
    require(store.verify(origin).bindings.purpose == ExecutionPurpose.DIAGNOSTIC.wire &&
        stopSealSeconds in 1..2_592_000 && outcome.trials.size == 1 &&
        outcome.trials.single().planned.spec.id == DIAGNOSTIC_GETTER_CASE &&
        outcome.trials.single().receipt.state == "passed" &&
        outcome.epoch.inputCheckpoint == origin && outcome.epoch.restored?.reference == origin &&
        outcome.safelyClosed && outcome.epoch.status == "passed" &&
        outcome.epoch.closure?.saveValidated == true &&
        outcome.epoch.closure?.processCleanupComplete == true &&
        outcome.epoch.closure?.survivingPids?.isEmpty() == true &&
        outcome.stopStartedAt != null &&
        !Instant.now().isAfter(outcome.stopStartedAt.plusSeconds(stopSealSeconds)) &&
        Instant.now().isBefore(deadline))
    store.verifyRestoreReceipt(requireNotNull(outcome.epoch.restored))
    val id = "diagnostic-getter-${outcome.epoch.id}"
    val child = store.seal(id, outcome.roots, requireNotNull(outcome.epoch.closure), origin)
    require(store.verify(child).parent == origin && store.verify(child).sourceEpoch == outcome.epoch.id)
    return child
}

/** Must replay a manifest-bound campaign journal, not accept a caller-constructed sealed-child DTO. */
internal fun replayDiagnosticControlSeals(store: CheckpointStore, journal: BoundFile,
    reviewed: PredeclaredDiagnosticMutationControl, epochs: List<SessionEpoch>): List<DiagnosticControlSeal> {
    require(reviewed.manifest.sha256.matches(shaPattern) && epochs.size in 1..3 &&
        epochs.map { it.id }.distinct().size == epochs.size &&
        epochs.map { it.nativeSessionId }.distinct().size == epochs.size &&
        epochs.all { it.inputCheckpoint == reviewed.setup && it.restored?.reference == reviewed.setup &&
            it.closure?.saveValidated == true && it.closure?.processCleanupComplete == true &&
            it.closure?.survivingPids?.isEmpty() == true })
    val rows = strictCapturedLines(journal, maxBytes = 16_777_216, maxRows = 100_000,
        maxLine = 131_072).filter { it.path("type").asText() == "diagnostic_control_child_sealed" &&
        it.path("case_id").asText() == reviewed.caseId &&
        it.path("control_id").asText() == reviewed.controlId }
    require(rows.size == epochs.size)
    return rows.zip(epochs).map { (row, epoch) ->
        val child = com.bettercontent.tests.suite.suiteMapper.treeToValue(
            row.path("child"), CheckpointRef::class.java)
        val receipt = store.verify(child)
        require(row.path("manifest_sha256").asText() == reviewed.manifest.sha256 &&
            row.path("source_epoch_id").asText() == epoch.id &&
            row.path("setup") == com.bettercontent.tests.suite.suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(reviewed.setup) &&
            child.id == "diagnostic-${reviewed.manifest.sha256.take(16)}-${epoch.id}-${reviewed.controlId}" &&
            receipt.parent == reviewed.setup && receipt.sourceEpoch == epoch.id &&
            receipt.closedEpoch == epoch.closure && receipt.closedEpoch.nativeSessionId == epoch.nativeSessionId)
        DiagnosticControlSeal(reviewed.caseId, reviewed.controlId, epoch.id, reviewed.setup, child)
    }
}
