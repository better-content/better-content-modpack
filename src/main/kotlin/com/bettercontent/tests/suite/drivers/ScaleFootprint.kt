package com.bettercontent.tests.suite.drivers

import com.fasterxml.jackson.databind.JsonNode
import java.util.Base64
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Independent pinned Forge 47.4.22 ChunkMap.isChunkInRange geometry, never its enclosing square. */
internal fun scaleViewGeometry(cx: Int, cz: Int, siteCx: Int, siteCz: Int): Boolean {
    val a = max(0L, abs(cx.toLong() - siteCx) - 1L)
    val b = max(0L, abs(cz.toLong() - siteCz) - 1L)
    val lo = min(a, b)
    val hi = max(0L, max(a, b) - 1L)
    return lo * lo + hi * hi < 100L
}

/** Separate from view geometry: pinned tickChunks proximity is <128 blocks to chunk center. */
internal fun scaleSpawnProximity(cx: Int, cz: Int, siteX: Int, siteZ: Int): Boolean {
    val dx = cx * 16.0 + 8.0 - (siteX + 0.5)
    val dz = cz * 16.0 + 8.0 - (siteZ + 0.5)
    return dx * dx + dz * dz < 16_384.0
}

private const val SCALE_TUPLE_SCHEMA = "chunk_x,chunk_z,loaded_samples,block_ticking_samples," +
    "entity_ticking_samples,natural_spawning_samples,tick_eligible_samples,tick_events,spawn_proximity_expected_0_or_1"

internal fun scaleExpectedView(siteX: Int, siteZ: Int): List<Pair<Int, Int>> {
    val siteCx = siteX shr 4; val siteCz = siteZ shr 4
    // Pinned range predicate admits axial ±11 at distance 10; ±12 cannot satisfy it.
    return (-11..11).flatMap { dx -> (-11..11).mapNotNull { dz ->
        val cx = siteCx + dx; val cz = siteCz + dz
        if (scaleViewGeometry(cx, cz, siteCx, siteCz)) cx to cz else null
    } }.sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
}

/** Two LSB-first canonical masks: ready view chunks then full-interval tickChunk calls. */
internal fun scaleIntervalFootprintConforms(actor: JsonNode, siteX: Int, siteZ: Int): Boolean {
    val view = scaleExpectedView(siteX, siteZ)
    val text = actor.path("footprint_interval_masks_base64")
    if (!text.isTextual || text.asText().length !in 2..256 ||
        !actor.path("footprint_loaded_chunks").isIntegralNumber ||
        !actor.path("footprint_loaded_chunks").canConvertToInt() ||
        actor.path("footprint_loaded_chunks").asInt() != view.size ||
        !actor.path("footprint_block_ticking_chunks").isIntegralNumber ||
        !actor.path("footprint_block_ticking_chunks").canConvertToInt()) return false
    val bytes = runCatching { Base64.getDecoder().decode(text.asText()) }.getOrNull() ?: return false
    val maskBytes = (view.size + 7) / 8
    if (bytes.size != maskBytes * 2 || Base64.getEncoder().encodeToString(bytes) != text.asText()) return false
    val inner = view.count { (cx, cz) -> scaleSpawnProximity(cx, cz, siteX, siteZ) }
    if (inner < 2 || actor.path("footprint_block_ticking_chunks").asInt() < inner) return false
    for ((i, chunk) in view.withIndex()) if (scaleSpawnProximity(chunk.first, chunk.second, siteX, siteZ)) {
        val bit = 1 shl (i and 7)
        if ((bytes[i shr 3].toInt() and bit) == 0 ||
            (bytes[maskBytes + (i shr 3)].toInt() and bit) == 0) return false
    }
    val lastByteBits = view.size and 7
    if (lastByteBits != 0) {
        val unused = (0xff shl lastByteBits) and 0xff
        if ((bytes[maskBytes - 1].toInt() and unused) != 0 ||
            (bytes[bytes.size - 1].toInt() and unused) != 0) return false
    }
    return true
}

/** Full per-chunk roster and engine-eligible coverage, not an aggregate/center-only native claim. */
internal fun scaleFootprintConforms(actor: JsonNode, siteX: Int, siteZ: Int, sampleCount: Int,
    lastRawActor: JsonNode? = null): Boolean {
    if (sampleCount !in 1..1000 || actor.path("footprint_view_tuple_schema").asText() != SCALE_TUPLE_SCHEMA ||
        !actor.path("footprint_view_chunks").isArray ||
        !actor.path("footprint_unsupported_condition").isMissingNode ||
        listOf("missing_view_loaded_chunks", "missing_inner_entity_ticking_chunks",
            "missing_inner_block_ticking_chunks", "missing_inner_natural_spawning_chunks",
            "missing_inner_tick_events_chunks", "missing_eligible_tick_events_chunks").any {
            !actor.path(it).isIntegralNumber || !actor.path(it).canConvertToInt() || actor.path(it).asInt() != 0
        }) return false
    val expected = scaleExpectedView(siteX, siteZ)
    val rows = actor.path("footprint_view_chunks").toList()
    if (rows.size != expected.size || expected.size <= 1 ||
        !actor.path("footprint_geometric_chunks_count").isIntegralNumber ||
        !actor.path("footprint_geometric_chunks_count").canConvertToInt() ||
        actor.path("footprint_geometric_chunks_count").asInt() != rows.size) return false
    var eligible = 0; var observed = 0; var touched = 0; var events = 0L
    for ((i, row) in rows.withIndex()) {
        if (!row.isArray || row.size() != 9 || row.any { !it.isIntegralNumber || !it.canConvertToInt() }) return false
        val values = row.map { it.asInt() }
        if (values[0] != expected[i].first || values[1] != expected[i].second ||
            values.slice(2..7).any { it < 0 } || values.slice(2..6).any { it > sampleCount } ||
            values[8] !in 0..1) return false
        val inner = scaleSpawnProximity(values[0], values[1], siteX, siteZ)
        if (values[8] != (if (inner) 1 else 0) || values[2] == 0) return false
        if (inner && (values[3] == 0 || values[4] == 0 || values[5] == 0 || values[6] == 0 || values[7] == 0))
            return false
        if (values[6] > 0) {
            eligible++
            if (values[3] == 0 || values[7] == 0) return false
            observed++
        }
        if (values[7] > 0) touched++
        events += values[7]
    }
    fun count(key: String, value: Long): Boolean = actor.path(key).isIntegralNumber &&
        actor.path(key).canConvertToLong() && actor.path(key).asLong() == value
    if (!count("footprint_tick_eligible_chunks", eligible.toLong()) ||
        !count("footprint_tick_observed_chunks", observed.toLong()) ||
        !count("footprint_distinct_chunks_ticked", touched.toLong()) ||
        !count("footprint_chunk_tick_events", events) || eligible < 2 || observed != eligible) return false
    if (lastRawActor != null && (!count("footprint_loaded_chunks",
            lastRawActor.path("footprint_loaded_chunks").asLong()) ||
        !count("footprint_block_ticking_chunks", lastRawActor.path("footprint_block_ticking_chunks").asLong())))
        return false
    return true
}
