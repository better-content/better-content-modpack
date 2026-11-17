package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.MeasurementVerdict

/** References only: caller cannot provide natural/tool/OS/provider Boolean verdicts. */
internal data class PhysicalEvidenceReplayRequest(val manifest: BoundFile,
    val origin: CheckpointRef, val preparedInput: CheckpointRef,
    val ancestors: List<WatchedAncestorStep>, val siblingEpochs: List<SessionEpoch>,
    val campaignJournal: BoundFile, val hostOsInputJournal: BoundFile?,
    val decodedRemotePacketTrace: BoundFile?, val craftOriginTrace: BoundFile?)

/**
 * A's single independent qualification entry point. A positive result is deliberately impossible
 * until the original host→client→packet, acquired tool origin, final transformed route/profile,
 * saved sibling tool/pickup and D typed wear verdict all have bounded replay adapters. Missing
 * evidence is BLOCKED, never a caller-supplied success claim or a fabricated Boolean DTO.
 */
internal fun replayPhysicalQualificationEvidence(store: CheckpointStore,
    source: PhysicalEvidenceReplayRequest): MeasurementVerdict {
    val plan = readPredeclaredPhysicalWatchPlan(source.manifest)
    require(plan.preparedInputId == source.preparedInput.id)
    if (source.ancestors.isEmpty() || source.siblingEpochs.size != 2 ||
        source.hostOsInputJournal == null || source.decodedRemotePacketTrace == null ||
        source.craftOriginTrace == null) return MeasurementVerdict("blocked",
        "missing immutable ancestor/sibling or original OS/client packet and natural tool craft-origin raw trace")
    replayCompleteWatchedAncestorInterval(store, source.origin, source.preparedInput,
        plan, source.ancestors)
    replayPhysicalTrialSeals(store, source.campaignJournal, source.preparedInput,
        source.siblingEpochs, source.manifest.sha256)
    // These are only references. Neither an attached file nor a selected tool Count1 authenticates
    // its original OS event/remote packet, complete native request, or real craft provenance.
    // The producer adapters are not installed: DO NOT inspect these optional refs as pass flags.
    return MeasurementVerdict("blocked",
        "OS/native/craft refs supplied but independent exact-byte authenticated joins, effective provider route and typed saved wear verdict are not installed")
}
