package com.bettercontent.tests.suite

import java.security.MessageDigest

internal data class MeasurementVerdict(val state: String, val reason: String,
    val observations: Map<String, Any> = emptyMap())
internal data class TreeObservation(val dimension: String, val x: Int, val y: Int, val z: Int,
    val stratum: String, val site: String, val classification: String, val exclusion: String = "")

/** Count tree structures, never log blocks. Unknown structures are not silently excluded. */
internal fun evaluateTrees(stratum: String, observations: List<TreeObservation>, expectedSites: Set<String>): MeasurementVerdict {
    if (expectedSites.size !in 5..20) return MeasurementVerdict("inconclusive", "sampling site roster must contain 5..20 sites")
    val rows = observations.filter { it.stratum == stratum }
    val positions = rows.map { listOf(it.dimension, it.x, it.y, it.z) }
    if (positions.distinct().size != positions.size) return MeasurementVerdict("inconclusive", "duplicate sampled tree position")
    if (rows.any { it.site !in expectedSites }) return MeasurementVerdict("inconclusive", "unplanned sampling site")
    val allowed = setOf("dynamic-wild", "regular-wild", "structure", "player-built", "intentional-exception", "ambiguous")
    if (rows.any { it.classification !in allowed || it.classification == "ambiguous" })
        return MeasurementVerdict("inconclusive", "tree classification requires review")
    if (rows.any { it.classification in setOf("structure", "player-built", "intentional-exception") && it.exclusion.isBlank() })
        return MeasurementVerdict("inconclusive", "excluded tree/log structure lacks provenance")
    val wild = rows.filter { it.classification in setOf("dynamic-wild", "regular-wild") }
    val count = wild.size; val sites = wild.map { it.site }.toSet().size
    val dynamic = wild.count { it.classification == "dynamic-wild" }
    val data = mapOf("classified_wild" to count, "dynamic" to dynamic, "regular" to count - dynamic,
        "wild_sites" to sites, "excluded" to rows.size - count)
    if (count < 100 || sites < 5) return MeasurementVerdict("inconclusive", "insufficient wild-tree sample", data)
    return MeasurementVerdict(if (dynamic.toLong() * 100 >= count.toLong() * 95) "passed" else "failed",
        "empirical wild-tree fraction; structural logs excluded; not a universal worldgen guarantee", data)
}

internal data class FoodCandidate(val id: String, val category: String)
internal data class FoodSelection(val sampled: List<String>, val mandatory: List<String>)

/** Stable across list ordering and JDK Random implementations. One per authored behavior stratum, then fill. */
internal fun selectFoods(candidates: List<FoodCandidate>, mandatory: List<String>, seed: Long = 424242L): FoodSelection {
    require(candidates.map { it.id }.distinct().size == candidates.size) { "duplicate food identity" }
    require(candidates.all { resourcePattern.matches(it.id) && it.category.isNotBlank() }) { "unclassified food" }
    require(candidates.size >= 10 && candidates.map { it.category }.distinct().size <= 10) { "cannot satisfy ten-food stratified sample" }
    require(mandatory.distinct().size == mandatory.size && mandatory.all { id -> candidates.any { it.id == id } }) { "mandatory food outside authored roster" }
    fun rank(food: FoodCandidate): String = MessageDigest.getInstance("SHA-256")
        .digest("$seed\u0000${food.category}\u0000${food.id}".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    val ranked = candidates.sortedWith(compareBy<FoodCandidate> { rank(it) }.thenBy { it.id })
    val chosen = ranked.groupBy { it.category }.toSortedMap().values.map { it.first().id }.toMutableList()
    ranked.forEach { if (chosen.size < 10 && it.id !in chosen) chosen += it.id }
    return FoodSelection(chosen, mandatory.sorted())
}

internal data class TickSample(val elapsedMillis: Long, val completedTicks: Long)
internal data class ChunkPosition(val dimension: String, val x: Int, val z: Int)
internal data class FakePlayerFootprint(val player: String, val registered: Boolean, val playerTicks: Long,
    val loaded: Set<ChunkPosition>, val ticking: Set<ChunkPosition>)

internal fun evaluateFootprints(players: List<FakePlayerFootprint>, expected: Int): MeasurementVerdict {
    if (expected !in setOf(8, 16, 32) || players.size != expected || players.map { it.player }.toSet().size != expected)
        return MeasurementVerdict("failed", "cohort membership mismatch")
    if (players.any { !it.registered || it.playerTicks <= 0 || it.loaded.isEmpty() || it.ticking.isEmpty() || !it.loaded.containsAll(it.ticking) })
        return MeasurementVerdict("inconclusive", "adapter has not demonstrated lifecycle and ticking footprint conformance")
    val seen = mutableSetOf<ChunkPosition>()
    if (players.any { player -> player.loaded.any { !seen.add(it) } })
        return MeasurementVerdict("failed", "actual loaded footprints overlap")
    return MeasurementVerdict("passed", "server-side fake-player footprints only; not real-client or fake-excluded mod proof")
}

/** One raw cumulative server tick counter sample per second, with no invented interpolation. */
internal fun evaluateCohortTicks(samples: List<TickSample>, arrivalMillis: Long): MeasurementVerdict {
    if (samples.size < 2 || samples.first().elapsedMillis != arrivalMillis ||
        samples.zipWithNext().any { (a, b) -> b.elapsedMillis - a.elapsedMillis !in 500L..1500L || b.completedTicks < a.completedTicks })
        return MeasurementVerdict("inconclusive", "missing/nonmonotonic raw tick samples")
    fun tps(a: TickSample, b: TickSample): Double = (b.completedTicks - a.completedTicks) * 1000.0 / (b.elapsedMillis - a.elapsedMillis)
    // Require thirty consecutive one-second intervals >=19 TPS before recovery completes.
    var stableStart = 0
    var recovered: Int? = null
    for (i in 1 until samples.size) {
        if (tps(samples[i - 1], samples[i]) < 19.0) stableStart = i
        if (samples[i].elapsedMillis - samples[stableStart].elapsedMillis >= 30_000) { recovered = i; break }
        if (samples[i].elapsedMillis - arrivalMillis > 120_000) break
    }
    val start = recovered ?: return MeasurementVerdict(
        if (samples.last().elapsedMillis - arrivalMillis >= 120_000) "failed" else "inconclusive", "no thirty-second TPS recovery within 120 seconds")
    if (samples[start].elapsedMillis - arrivalMillis > 120_000) return MeasurementVerdict("failed", "late recovery")
    val endTime = samples[start].elapsedMillis + 600_000
    if (samples.last().elapsedMillis < endTime) return MeasurementVerdict("inconclusive", "ten-minute measured dwell incomplete")
    var left = start
    var windows = 0
    var minimum = Double.POSITIVE_INFINITY
    for (right in start + 1 until samples.size) {
        if (samples[right].elapsedMillis > endTime + 1500) break
        while (left + 1 < right && samples[right].elapsedMillis - samples[left + 1].elapsedMillis >= 30_000) left++
        if (samples[right].elapsedMillis - samples[left].elapsedMillis >= 30_000) {
            val value = tps(samples[left], samples[right]); minimum = minOf(minimum, value); windows++
            if (value < 19.0) return MeasurementVerdict("failed", "TPS below19 in a measured thirty-second window", mapOf("tps" to value, "window_end_ms" to samples[right].elapsedMillis))
        }
    }
    return MeasurementVerdict("passed", "recovered and sustained measured fake-player dwell",
        mapOf("recovered_at_ms" to samples[start].elapsedMillis, "measurement_ms" to 600_000, "windows" to windows, "minimum_tps" to minimum))
}

internal data class ThreatAttempt(val outcome: String, val attribution: String, val evidence: String)
internal fun evaluateThreat(attempts: List<ThreatAttempt>, encounter: String): MeasurementVerdict {
    if (attempts.isEmpty() || attempts.size > 3 || encounter.isBlank()) return MeasurementVerdict("inconclusive", "invalid attempt budget")
    attempts.forEachIndexed { index, attempt ->
        if (attempt.evidence.isBlank()) return MeasurementVerdict("inconclusive", "death/timeout evidence missing")
        when (attempt.outcome) {
            "encounter-death" -> {
                if (attempt.attribution != encounter || index != attempts.lastIndex) return MeasurementVerdict("failed", "invalid attribution or retry after terminal result")
                return MeasurementVerdict("passed", "death attributed to required encounter")
            }
            "timeout" -> return MeasurementVerdict("failed", "encounter did not kill within authored bound")
            "unrelated-death" -> if (attempt.attribution.isBlank() || attempt.attribution == encounter)
                return MeasurementVerdict("inconclusive", "unrelated-death classification not established")
            else -> return MeasurementVerdict("inconclusive", "unknown attempt outcome")
        }
    }
    return MeasurementVerdict("inconclusive", if (attempts.size == 3) "unrelated-death retry budget exhausted" else "encounter attempt not completed")
}
