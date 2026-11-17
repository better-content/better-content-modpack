package com.bettercontent.tests.suite.oracles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/** Fixed independent arithmetic examples; do not run without explicit human test authorization. */
class VanillaIncomingDamageOracleTest {
    private fun hit() = VanillaIncomingDamageOracle.Input(
        rawHurtAfterForgeHook = 10f, armor = 10f, toughness = 0f,
        resistanceAmplifier = 0, healthBefore = 20f, absorptionBefore = 2f,
        sourceTagsObserved = true, bypassesArmor = false, bypassesEffects = false,
        bypassesResistance = false, bypassesEnchantments = false,
        enchantmentProtectionObserved = 0, externalModifiersKnownAbsent = true,
        forgeHurtUnmodified = true, forgeDamageUnmodified = true)
    private fun near(expected: Float, actual: Float) = assertEquals(expected, actual, 0.0001f)
    @Test fun `armor resistance and absorption produce independent Float health delta`() {
        val result = assertInstanceOf(VanillaIncomingDamageOracle.Result.Supported::class.java,
            VanillaIncomingDamageOracle.compute(hit()))
        near(8f, result.postArmor)
        near(6.4f, result.postResistance)
        near(2f, result.absorbed)
        near(4.4f, result.healthDamage)
        near(15.6f, result.healthAfter)
        near(0f, result.absorptionAfter)
    }
    @Test fun `armor toughness and remaining absorption can consume the entire hit`() {
        val result = assertInstanceOf(VanillaIncomingDamageOracle.Result.Supported::class.java,
            VanillaIncomingDamageOracle.compute(hit().copy(rawHurtAfterForgeHook = 8f, armor = 15f,
                toughness = 8f, resistanceAmplifier = 1, absorptionBefore = 3f)))
        near(3.84f, result.postArmor)
        near(2.304f, result.postResistance)
        near(0f, result.healthDamage)
        near(0.696f, result.absorptionAfter)
    }
    @Test fun `unknown provider enchantment bypass and lethal damage remain unsupported not guesses`() {
        listOf(hit().copy(enchantmentProtectionObserved = null),
            hit().copy(externalModifiersKnownAbsent = false),
            hit().copy(bypassesArmor = true),
            hit().copy(forgeDamageUnmodified = false),
            hit().copy(healthBefore = 2f, absorptionBefore = 0f)).forEach { input ->
            assertInstanceOf(VanillaIncomingDamageOracle.Result.Unsupported::class.java,
                VanillaIncomingDamageOracle.compute(input))
        }
        assertEquals("unverified_or_bypassing_damage_source",
            (VanillaIncomingDamageOracle.compute(hit().copy(sourceTagsObserved = false)) as
                VanillaIncomingDamageOracle.Result.Unsupported).reason)
    }
}
