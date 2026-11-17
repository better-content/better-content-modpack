package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.oracles.VanillaIncomingDamageOracle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Synthetic normalized arithmetic only. Never asserts that B's wire, attacker or natural AI exists. */
class IncomingDamageControlEvidenceTest {
    private val player = "123e4567-e89b-42d3-a456-426614174000"
    private val attacker = "123e4567-e89b-42d3-a456-426614174002"
    private fun binding() = IncomingDamageControlEvidence.Binding(player, "minecraft:overworld", attacker,
        "minecraft:zombie", "minecraft:air", "minecraft:vanilla", "bounded_real_ai_mechanism_fixture", true)
    private fun witness() = IncomingDamageControlEvidence.Witness(player, "minecraft:overworld", attacker,
        "minecraft:zombie", "minecraft:air", "minecraft:vanilla", 10, 11, 12, 166,
        1, 1, 1, true, true, 15.6f, 0f, 4.4f,
        VanillaIncomingDamageOracle.Input(10f, 10f, 0f, 0, 20f, 2f,
            true, false, false, false, false, 0, true, true, true))
    @Test fun `one bound complete real AI mechanism arithmetic is diagnostic only`() {
        val verdict = IncomingDamageControlEvidence.evaluate(binding(), witness())
        assertEquals("passed", verdict.state)
        assertEquals(true, verdict.observations["diagnostic_only_not_natural_threat"])
    }
    @Test fun `complete health contradiction fails while source ambiguity is technical blocker`() {
        assertEquals("failed", IncomingDamageControlEvidence.evaluate(binding(),
            witness().copy(healthAfterObserved = 15f)).state)
        assertThrows(IllegalArgumentException::class.java) {
            IncomingDamageControlEvidence.evaluate(binding(), witness().copy(rawServerChainIndependentlyVerified = false))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IncomingDamageControlEvidence.evaluate(binding(), witness().copy(attackerUuid = player))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IncomingDamageControlEvidence.evaluate(binding().copy(reviewedSourceWeaponTargetProvider = false), witness())
        }
        assertThrows(IllegalArgumentException::class.java) {
            IncomingDamageControlEvidence.evaluate(binding(), witness().copy(
                vanillaInput = witness().vanillaInput.copy(externalModifiersKnownAbsent = false)))
        }
    }
}
