package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.oracles.VanillaIncomingDamageOracle
import java.util.UUID
import kotlin.math.abs

/** Source-only normalized consumer for ONE passive actual incoming hit. B's arm/await wire and raw
 * replay have NOT been frozen; this is deliberately not a CaseDriver or a trusted wire parser.
 * A must bind real actor/source/weapon/provider and raw bytes BEFORE constructing Witness. */
internal object IncomingDamageControlEvidence {
    data class Binding(val playerUuid: String, val dimension: String, val attackerUuid: String,
        val attackerType: String, val weaponItemId: String, val providerId: String,
        val sourceKind: String, val reviewedSourceWeaponTargetProvider: Boolean)
    data class Witness(val playerUuid: String, val dimension: String, val attackerUuid: String,
        val attackerType: String, val weaponItemId: String, val providerId: String,
        val armTick: Long, val hitTick: Long, val terminalTick: Long, val deadlineTick: Long,
        val actualHurtCount: Int, val hurtHookCount: Int, val damageHookCount: Int,
        val rawServerChainIndependentlyVerified: Boolean, val oneHitPrePostUninterleaved: Boolean,
        val healthAfterObserved: Float, val absorptionAfterObserved: Float,
        val livingDamageHookAmountObserved: Float, val vanillaInput: VanillaIncomingDamageOracle.Input)
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private fun uuid(value: String): Boolean = runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
    fun evaluate(binding: Binding, observed: Witness): MeasurementVerdict {
        require(uuid(binding.playerUuid) && uuid(binding.attackerUuid) &&
            binding.playerUuid != binding.attackerUuid &&
            listOf(binding.dimension, binding.attackerType, binding.weaponItemId, binding.providerId)
                .all(resource::matches) && binding.sourceKind in setOf("bounded_real_ai_mechanism_fixture",
                "reviewed_natural_threat_candidate") && binding.reviewedSourceWeaponTargetProvider) {
            "actual attacker/weapon/target/provider binding missing or unreviewed"
        }
        require(observed.playerUuid == binding.playerUuid && observed.dimension == binding.dimension &&
            observed.attackerUuid == binding.attackerUuid && observed.attackerType == binding.attackerType &&
            observed.weaponItemId == binding.weaponItemId && observed.providerId == binding.providerId &&
            observed.armTick >= 0 && observed.armTick <= observed.hitTick &&
            observed.hitTick <= observed.terminalTick && observed.terminalTick <= observed.deadlineTick &&
            observed.deadlineTick - observed.armTick in 2..200 &&
            observed.actualHurtCount == 1 && observed.hurtHookCount == 1 && observed.damageHookCount == 1 &&
            observed.rawServerChainIndependentlyVerified && observed.oneHitPrePostUninterleaved &&
            listOf(observed.healthAfterObserved, observed.absorptionAfterObserved,
                observed.livingDamageHookAmountObserved).all { it.isFinite() && it >= 0f }) {
            "actual single-hit, timed and independently verified passive raw witness missing"
        }
        val predicted = VanillaIncomingDamageOracle.compute(observed.vanillaInput)
        require(predicted is VanillaIncomingDamageOracle.Result.Supported) {
            "unsupported vanilla incoming-damage profile: ${(predicted as VanillaIncomingDamageOracle.Result.Unsupported).reason}"
        }
        // The hook amount is an ACTUAL observed post-absorption amount, never an expected value.
        val consistent = abs(predicted.healthDamage - observed.livingDamageHookAmountObserved) <= 0.0001f &&
            abs(predicted.healthAfter - observed.healthAfterObserved) <= 0.0001f &&
            abs(predicted.absorptionAfter - observed.absorptionAfterObserved) <= 0.0001f
        return MeasurementVerdict(if (consistent) "passed" else "failed",
            if (consistent) "one bounded incoming vanilla hit diagnostic; no natural threat/family qualification"
            else "complete same-source incoming hit contradicts independent vanilla health/absorption oracle",
            mapOf("diagnostic_only_not_natural_threat" to true, "source_kind" to binding.sourceKind))
    }
}
