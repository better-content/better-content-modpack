package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.fasterxml.jackson.annotation.JsonInclude

/** V3 campaign identity is separate from each restored native v2 session. */
internal data class BranchSpec(val id: String, val inputCheckpoint: String, val caseIds: List<String>,
    val outputCheckpoint: String?, val dependsOn: List<String>, val statePolicy: String,
    val isolationContract: BoundFile?, val orderingDependencies: List<String> = emptyList())
internal data class CheckpointRef(val id: String, val receipt: BoundFile)
internal data class OwnedProcessIdentity(val pid: Long, val startedAt: String)
internal data class EpochClosure(val epochId: String, val nativeSessionId: String, val closedAt: String,
    val saveValidated: Boolean, val processCleanupComplete: Boolean, val ownedProcesses: List<OwnedProcessIdentity>,
    val survivingPids: List<Long>, val evidence: BoundFile)
internal data class CheckpointBindings(val campaignId: String, val lineageId: String,
    val candidate: Map<String, String>, val supportSha256: String, val configurationSha256: String,
    val purpose: String = ExecutionPurpose.DIAGNOSTIC.wire)
internal data class SnapshotFile(val root: String, val path: String, val size: Long,
    val sha256: String, val executable: Boolean,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val directory: Boolean? = null)
internal data class CheckpointReceipt(val schema: String, val id: String, val bindings: CheckpointBindings,
    val parent: CheckpointRef?, val sourceEpoch: String, val closedEpoch: EpochClosure,
    val sealedAt: String, val snapshotDirectory: String, val files: List<SnapshotFile>, val treeSha256: String)
internal data class RestoredCheckpoint(val reference: CheckpointRef, val epochId: String,
    val directory: String, val roots: Map<String, String>, val restoredAt: String, val filesVerified: Int,
    val treeSha256: String, val restoreReceipt: BoundFile)
internal data class SessionEpoch(val id: String, val branchId: String, val campaignId: String,
    val lineageId: String, val nativeSessionId: String, val inputCheckpoint: CheckpointRef?,
    val restored: RestoredCheckpoint?, val startedAt: String, val completedAt: String,
    val status: String, val closure: EpochClosure?, val eventLog: BoundFile,
    val nativeRequests: BoundFile?, val nativeObservations: BoundFile?, val caseResults: List<BoundFile>,
    val clientStream: ClientStreamBinding? = null, val purpose: String = ExecutionPurpose.DIAGNOSTIC.wire)
internal data class ClientStreamBinding(val nativeSessionId: String, val capabilities: BoundFile,
    val nativeRequests: BoundFile?, val nativeObservations: BoundFile?, val inputLog: BoundFile?,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val inputRuntime: BoundFile? = null)
