package com.bettercontent.tests.suite

/** Strict shapes for native receipts; absent primitive flags must not become implicit false/zero. */
internal data class CampaignReceipt(val schema: String, val status: String, val diagnostic: Boolean,
    val catalogueSha256: String, val profileSha256: String, val candidate: Map<String, String>,
    val testSupportSha256: String, val instrumented: Boolean, val seed: Long, val worldId: String,
    val runId: String, val worldDirectory: String, val worldMarker: BoundFile, val worldsCreated: Int,
    val cleanupComplete: Boolean, val savedAndValidated: Boolean, val survivingOwnedPids: List<Long>,
    val caseIds: List<String>, val observedRosters: Map<String, List<String>>, val events: BoundFile,
    val startedAt: String, val completedAt: String, val hardware: BoundFile, val fixtureOverrides: BoundFile)
internal data class RepositoryReceipt(val schema: String, val repository: String, val status: String,
    val sourceSha256: String, val testsSha256: String, val providersSha256: String,
    val toolchainSha256: String, val configurationSha256: String, val artifactSha256: String,
    val caseIds: List<String>, val evidence: List<BoundFile>)
internal data class ReviewedLogSignature(val signature: String, val count: Long, val owner: String,
    val impact: String, val disposition: String)
internal data class LogReviewReceipt(val schema: String, val catalogueSha256: String, val status: String,
    val reviewer: String, val reviewedAt: String, val unresolvedBlockers: List<String>,
    val signatures: List<ReviewedLogSignature>, val sources: List<BoundFile>)
internal data class GuiCapture(val screen: String, val profile: String, val screenshot: BoundFile,
    val capturedAt: String, val observedGuiScale: Int, val captureKind: String, val inputKind: String, val approved: Boolean,
    val checks: Map<String, Boolean>, val inputLog: BoundFile, val inputFirstLine: Long, val inputLastLine: Long)
internal data class GuiReviewReceipt(val schema: String, val status: String, val catalogueSha256: String,
    val sessionId: String, val reviewer: String, val captures: List<GuiCapture>)
