package com.bettercontent.tests.balance

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/** Source-backed neutral TCon scalar model, not proof of Epic Fight hook execution. */
object TconSpecialDamage {
    data class ArmorBucket(val weight: Double, val armor: Double)
    data class Canon(val buckets: List<ArmorBucket>, val toughness: Double) {
        init {
            require(buckets.isNotEmpty() && buckets.all {
                it.weight.isFinite() && it.weight >= 0.0 && it.armor.isFinite() && it.armor >= 0.0
            })
            require(abs(buckets.sumOf { it.weight } - 1.0) < 1e-12) { "Armor weights must sum to one" }
            require(toughness.isFinite() && toughness >= 0.0)
        }
    }

    enum class Kind { ORDINARY, PIERCE, LEGACY_PIERCING, FIXED }

    /** baseDamage is the frozen pre-innate, pre-multiplier stat, including material contribution. */
    data class Model(val kind: Kind, val baseDamage: Double, val innateLevel: Int = 0) {
        init {
            require(baseDamage.isFinite() && baseDamage > 0.0)
            require(innateLevel >= 0)
            require((kind == Kind.PIERCE || kind == Kind.LEGACY_PIERCING) == (innateLevel > 0))
            require(kind != Kind.LEGACY_PIERCING || baseDamage > 0.5 * innateLevel) {
                "Outside positive main-hit legacy piercing model"
            }
        }
    }

    /** Vanilla 1.20.1 CombatRules armor-only absorption; no enchantments/resistance/EF split. */
    fun afterArmor(damage: Double, armor: Double, toughness: Double): Double {
        require(damage.isFinite() && damage >= 0.0 && armor.isFinite() && armor >= 0.0)
        require(toughness.isFinite() && toughness >= 0.0)
        val effective = (armor - damage / (2.0 + toughness / 4.0)).coerceAtLeast(armor * 0.2).coerceAtMost(20.0)
        return damage * (1.0 - effective / 25.0)
    }

    fun expectedHit(model: Model, multiplier: Double, canon: Canon): Double {
        require(multiplier.isFinite() && multiplier >= 0.0)
        val main = when (model.kind) {
            Kind.PIERCE -> (model.baseDamage + 0.5 * model.innateLevel) * multiplier
            Kind.LEGACY_PIERCING -> (model.baseDamage - 0.5 * model.innateLevel) * multiplier
            else -> model.baseDamage * multiplier
        }
        require(main.isFinite())
        // MobEffectModule computes amplifier = round(2*L)-1; -1 armor per amplifier+1.
        val armorReduction = if (model.kind == Kind.PIERCE) 2.0 * model.innateLevel else 0.0
        // PiercingModifier emits L*getMultiplier(ATTACK_DAMAGE) as armor-bypassing secondary damage.
        val bypass = if (model.kind == Kind.LEGACY_PIERCING) model.innateLevel * multiplier else 0.0
        return canon.buckets.sumOf {
            it.weight * (afterArmor(main, (it.armor - armorReduction).coerceAtLeast(0.0), canon.toughness) + bypass)
        }.also { require(it.isFinite()) }
    }

    /** Inputs may be synthetic for tests; this function never reads or rewrites production data. */
    fun solve(
        tier: Double,
        speed: Double,
        model: Model,
        canon: Canon,
        referenceBaseDamage: Double = 3.0,
        referenceSpeed: Double = 1.6,
        scale: Double = 1.0,
    ): Double {
        require(tier.isFinite() && tier in 80.0..120.0)
        require(speed.isFinite() && speed > 0.0 && referenceSpeed.isFinite() && referenceSpeed > 0.0)
        require(scale.isFinite() && scale > 0.0)
        val reference = expectedHit(Model(Kind.ORDINARY, referenceBaseDamage), 1.0, canon) * referenceSpeed
        val target = reference * (tier / 100.0) * scale / speed
        require(target.isFinite() && target > 0.0) { "Unrepresentable per-hit target" }
        var low = 0.0
        var high = 1.0
        while (expectedHit(model, high, canon) < target) {
            high *= 2.0
            require(high.isFinite()) { "No representable multiplier bracket" }
        }
        repeat(100) {
            val middle = low + (high - low) / 2.0
            if (expectedHit(model, middle, canon) < target) low = middle else high = middle
        }
        return BigDecimal.valueOf(low + (high - low) / 2.0).setScale(6, RoundingMode.HALF_UP).toDouble()
            .also { require(it.isFinite() && it > 0.0) { "Multiplier rounds to zero" } }
    }

    /**
     * Necessary relationship for an isolated pre-mitigation alternate coefficient. Call only for
     * a proven D = coefficient * chassisMultiplier * frozenBase path; not an arbitrary hook.
     * Returning throws and Bonk need a native damage-only hook before this can be installed.
     */
    fun preservingAlternateCoefficient(oldCoefficient: Double, oldMultiplier: Double, newMultiplier: Double): Double {
        require(listOf(oldCoefficient, oldMultiplier, newMultiplier).all { it.isFinite() && it > 0.0 })
        return (oldCoefficient * (oldMultiplier / newMultiplier)).also {
            require(it.isFinite() && it > 0.0) { "Unrepresentable compensation" }
        }
    }
}
