package com.bettercontent.tests.balance

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest

/**
 * Offline arithmetic for the approved TCon reach policy, not a measurement or runtime verdict.
 * Never substitutes capability collider dimensions or weapon names for measured combo reach.
 */
object TconReachNormalization {
    const val REFERENCE_ITEM = "tconstruct:sword"
    const val MELEE_SCALE = 1.0
    const val RANGED_SCALE = 0.9
    const val REFERENCE_SPEED = 1.6

    /** Damage is frozen pre-retuning, pre-mitigation contribution, including same-strike events. */
    data class Strike(val id: String, val reach: Double, val damageWeight: Double)

    /** Bind all rows to one scene/loadout/candidate input manifest and retained raw evidence. */
    data class Measurement(
        val item: String,
        val capability: String,
        val style: String,
        val inputManifestSha256: String,
        val evidenceSha256: String,
        val strikes: List<Strike>,
    )

    fun rosterSha256(items: Set<String>): String {
        require(items.isNotEmpty() && items.all { it.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")) }) {
            "A nonempty, exact item roster is required"
        }
        return MessageDigest.getInstance("SHA-256")
            .digest((items.sorted().joinToString("\n") + "\n").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun effectiveReach(strikes: List<Strike>): Double {
        require(strikes.isNotEmpty()) { "Missing strike measurements" }
        require(strikes.all { it.id.isNotBlank() } && strikes.map { it.id }.distinct().size == strikes.size) {
            "Strike identities must be nonempty and unique"
        }
        require(strikes.all { it.reach.isFinite() && it.reach > 0.0 }) { "Invalid connection distance" }
        require(strikes.all { it.damageWeight.isFinite() && it.damageWeight >= 0.0 }) { "Invalid frozen damage weight" }
        // Normalize both terms before summation to avoid overflowing finite input products.
        val maxWeight = strikes.maxOf { it.damageWeight }
        require(maxWeight > 0.0) { "At least one positive damage contribution is required" }
        val maxReach = strikes.maxOf { it.reach }
        val weight = strikes.sumOf { it.damageWeight / maxWeight }
        val weightedReach = strikes.sumOf { (it.reach / maxReach) * (it.damageWeight / maxWeight) }
        return ((weightedReach / weight).coerceIn(0.0, 1.0) * maxReach).also {
            require(it.isFinite() && it > 0.0) { "Unrepresentable effective reach" }
        }
    }

    /** Full frozen-roster validation precedes all tier calculation; no partial recalibration. */
    fun tiers(
        expectedRoster: Set<String>,
        expectedInputManifestSha256: String,
        measurements: List<Measurement>,
    ): Map<String, Double> {
        require(REFERENCE_ITEM in expectedRoster) { "Missing Sword reference" }
        require(isSha256(expectedInputManifestSha256)) { "Missing candidate input identity" }
        require(measurements.map { it.item }.distinct().size == measurements.size) { "Duplicate item measurements" }
        require(measurements.map { it.item }.toSet() == expectedRoster) { "Measurements must cover the exact frozen melee roster" }
        val reaches = measurements.associate { measurement ->
            require(measurement.capability.isNotBlank() && measurement.style.isNotBlank()) { "Missing capability/style" }
            require(measurement.inputManifestSha256 == expectedInputManifestSha256) { "Mixed candidate/scene/loadout inputs" }
            require(isSha256(measurement.evidenceSha256)) { "Missing raw measurement evidence identity" }
            measurement.item to effectiveReach(measurement.strikes)
        }
        val sword = reaches.getValue(REFERENCE_ITEM)
        val shortest = reaches.values.min()
        val longest = reaches.values.max()
        return reaches.toSortedMap().mapValues { (_, reach) ->
            val tier = when {
                reach < sword -> 100.0 + 20.0 * ((sword - reach) / (sword - shortest))
                reach > sword -> 100.0 - 20.0 * ((reach - sword) / (longest - sword))
                else -> 100.0
            }
            roundSix(tier.coerceIn(80.0, 120.0))
        }
    }

    /** Ordinary model only: zero-head/fixed, piercing and bypass models need native solvers. */
    fun ordinaryMultiplier(tier: Double, headScale: Double, speed: Double): Double {
        require(tier.isFinite() && tier in 80.0..120.0) { "Tier outside melee policy" }
        require(headScale.isFinite() && headScale > 0.0) { "Not an ordinary positive-head model" }
        require(speed.isFinite() && speed > 0.0) { "Invalid attack speed" }
        val multiplier = (tier / 100.0) * MELEE_SCALE * REFERENCE_SPEED / headScale / speed
        require(multiplier.isFinite() && multiplier > 0.0) { "Unrepresentable multiplier" }
        return roundSix(multiplier).also { require(it > 0.0) { "Multiplier rounds to zero" } }
    }

    private fun isSha256(value: String) = value.matches(Regex("[a-f0-9]{64}"))
    private fun roundSix(value: Double) = BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).toDouble()
}
