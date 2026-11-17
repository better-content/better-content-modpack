package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.*
import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

/** Exact native framebuffer context; constructed only after independent client-stream replay. */
internal data class CapturedClientFrame(val epochId: String, val caseId: String, val trialId: String,
    val requestSequence: Long, val profile: String, val imageSha256: String, val screenClass: String,
    val menuType: String?, val dimension: String, val playerUuid: String)

/** A human-supplied review, never an agent- or instrumentation-generated approval. */
internal fun verifyClientFrameReview(document: JsonNode, frames: List<CapturedClientFrame>,
    campaignId: String, lineageId: String, catalogueSha256: String, candidate: Map<String, String>,
    authoringReviewer: String, campaignCompletedAt: Instant, guiScreens: Set<String>, profiles: Set<String>) {
    require(frames.isNotEmpty() && guiScreens.isNotEmpty() && profiles == setOf("1280x720@2", "1920x1080@3")) {
        "a framebuffer review needs both actual display profiles and reviewed GUI screen roster"
    }
    require(document.path("schema").asText() == "bc.playtest_client_frame_review.v1" &&
        document.path("status").asText() == "approved" && document.path("campaign_id").asText() == campaignId &&
        document.path("lineage_id").asText() == lineageId && document.path("catalogue_sha256").asText() == catalogueSha256 &&
        document.path("candidate").fieldNames().asSequence().toSet() == candidate.keys &&
        candidate.all { (key, hash) -> document.path("candidate").path(key).asText() == hash } &&
        document.path("reviewer").path("name").asText().isNotBlank() &&
        document.path("reviewer").path("name").asText() != authoringReviewer &&
        document.path("reviewer").path("kind").asText() == "human" &&
        document.path("reviewer").path("name").asText().lowercase() !in setOf("agent", "ai", "automated") &&
        !Instant.parse(document.path("reviewer").path("reviewed_at").asText()).isBefore(campaignCompletedAt) &&
        !Instant.parse(document.path("reviewer").path("reviewed_at").asText()).isAfter(Instant.now())) {
        "a human must explicitly review the actual v3 client frames after the campaign"
    }
    fun key(epoch: String, caseId: String, trial: String, sequence: Long): String = "$epoch/$caseId/$trial/$sequence"
    val byKey = frames.associateBy { key(it.epochId, it.caseId, it.trialId, it.requestSequence) }
    require(byKey.size == frames.size && frames.all { frame -> frame.requestSequence > 0 &&
        frame.imageSha256.matches(shaPattern) && frame.screenClass.isNotBlank() &&
        resourcePattern.matches(frame.dimension) &&
        runCatching { java.util.UUID.fromString(frame.playerUuid).toString() == frame.playerUuid }.getOrDefault(false)
    }) { "duplicate or untyped native client framebuffer" }
    val rows = document.path("frames")
    require(rows.isArray && rows.size() == frames.size) { "human frame review omitted/added captured source frames" }
    val pairs = mutableSetOf<Pair<String, String>>()
    val keys = mutableSetOf<String>()
    rows.forEach { row ->
        val identity = key(row.path("epoch_id").asText(), row.path("case_id").asText(),
            row.path("trial_id").asText(), row.path("request_sequence").asLong(-1))
        val native = byKey[identity]
        require(native != null && keys.add(identity) && row.path("screen").asText() in guiScreens &&
            row.path("profile").asText() == native.profile && row.path("profile").asText() in profiles &&
            row.path("image_sha256").asText() == native.imageSha256 &&
            row.path("screen_class").asText() == native.screenClass &&
            row.path("menu_type").let { if (native.menuType == null) it.isNull else it.asText() == native.menuType } &&
            row.path("dimension").asText() == native.dimension && row.path("player_uuid").asText() == native.playerUuid &&
            row.path("approved").isBoolean && row.path("approved").asBoolean() &&
            row.path("disposition").asText() == "approved" && row.path("rationale").asText().isNotBlank()) {
            "human review does not approve this exact bound framebuffer and actual UI/world context"
        }
        pairs += row.path("screen").asText() to native.profile
    }
    require(keys == byKey.keys && pairs == guiScreens.flatMap { screen -> profiles.map { screen to it } }.toSet()) {
        "reviewed GUI screen/profile coverage does not equal the complete actual client capture roster"
    }
}
