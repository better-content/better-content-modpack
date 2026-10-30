package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** A failed run is a checkpoint for investigation, never a source of accepted test evidence. */
internal data class DiagnosticReplay(
    val sourceRunId: String,
    val sourceEvidence: Path,
    val sourceCandidates: Map<String, String>,
    val roster: List<String>?,
    val failedHop: HopCursor?,
    val requestedSeconds: Long?,
    val completedConnectedSeconds: Long,
) {
    fun remainingSeconds(request: SessionRequest): Long {
        if (request.tier != SessionTier.SOAK) return request.connectedDuration.seconds
        check(requestedSeconds == request.connectedDuration.seconds) {
            "diagnostic Soak minutes must match the failed run's requested interval"
        }
        return (request.connectedDuration.seconds - completedConnectedSeconds).coerceAtLeast(1)
    }
}

internal data class HopCursor(val dimension: String, val circuit: Int)

internal fun loadDiagnosticReplay(root: Path, tier: SessionTier, source: Path): DiagnosticReplay {
    val path = source.toRealPath()
    val evidenceRoot = root.resolve("generated/test-evidence").toRealPath()
    require(path.startsWith(evidenceRoot) && path.fileName.toString() == "run.json" &&
        path.parent.fileName.toString() in setOf(tier.selector, "${tier.selector}-diagnostic")) {
        "diagnostic source must be retained $tier evidence under generated/test-evidence"
    }
    val mapper = jacksonObjectMapper()
    val receipt = mapper.readTree(path.toFile())
    require(receipt.path("status").asText() == "failed" && receipt.path("tier").asText() == tier.selector) {
        "only a failed $tier run may be investigated; passed or running evidence cannot be replayed"
    }
    val eventsPath = path.parent.resolve("events.jsonl")
    require(Files.isRegularFile(eventsPath)) { "diagnostic source lacks raw events" }
    receipt.path("events_sha256").asText().takeIf(String::isNotBlank)?.let { expected ->
        require(Hashes.sha256(eventsPath) == expected) { "diagnostic source events changed after failure" }
    }
    val events = Files.readAllLines(eventsPath).map { mapper.readTree(it) }
    require(events.any { it.path("type").asText() == "session_finished" && !it.path("success").asBoolean(true) }) {
        "diagnostic source lacks a terminal failure event"
    }
    val roster = events.firstOrNull { it.path("type").asText() == "dimension_roster" }
        ?.path("dimensions")?.takeIf { it.isArray }?.map { it.asText() }
    val hop = events.lastOrNull { it.path("type").asText() == "hop_started" }?.let {
        HopCursor(it.path("dimension").asText(), it.path("circuit").asInt(-1))
    }
    if (tier == SessionTier.TELE_SOAK) {
        require(hop == null || (roster != null && hop.dimension in roster && hop.circuit in 0..3)) {
            "failed Tele Soak checkpoint has no valid dimension/circuit"
        }
    }
    val candidates = receipt.path("candidates")
    require(candidates.path("client_sha256").asText().matches(Regex("[a-f0-9]{64}")) &&
        candidates.path("server_sha256").asText().matches(Regex("[a-f0-9]{64}"))) {
        "failed run lacks candidate digests"
    }
    val started = events.firstOrNull { it.path("type").asText() == "connected_interval_started" }
        ?.path("time")?.asText()?.let(Instant::parse)
    val lastReceipt = events.lastOrNull { it.path("type").asText() in
        setOf("heartbeat", "connected_interval_complete") }?.path("time")?.asText()?.let(Instant::parse)
    val elapsed = if (started != null && lastReceipt != null && lastReceipt.isAfter(started))
        java.time.Duration.between(started, lastReceipt).seconds else 0L
    val requested = events.firstOrNull { it.path("type").asText() == "prepared" }
        ?.path("requested_connected_seconds")?.asLong()
    return DiagnosticReplay(receipt.path("run_id").asText(), path, mapOf(
        "client_sha256" to candidates.path("client_sha256").asText(),
        "server_sha256" to candidates.path("server_sha256").asText()), roster, hop, requested, elapsed)
}
