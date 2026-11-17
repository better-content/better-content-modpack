package com.bettercontent.tests.suite.oracles

/** Independent Float calculation for the PINNED vanilla 1.20.1 Forge 47.4.22
 * ServerPlayer → Player.actuallyHurt override → inherited armor/magic CombatRules,
 * not the LivingEntity.actuallyHurt body or a native producer's expected value.
 * Caller must independently bind the one real hit, source/provider and every gate below. */
internal object VanillaIncomingDamageOracle {
    data class Input(
        val rawHurtAfterForgeHook: Float, val armor: Float, val toughness: Float,
        val resistanceAmplifier: Int?, val healthBefore: Float, val absorptionBefore: Float,
        val sourceTagsObserved: Boolean, val bypassesArmor: Boolean, val bypassesEffects: Boolean,
        val bypassesResistance: Boolean, val bypassesEnchantments: Boolean,
        val enchantmentProtectionObserved: Int?, val externalModifiersKnownAbsent: Boolean,
        val forgeHurtUnmodified: Boolean, val forgeDamageUnmodified: Boolean
    )
    sealed interface Result {
        data class Supported(val postArmor: Float, val postResistance: Float,
            val absorbed: Float, val healthDamage: Float, val healthAfter: Float,
            val absorptionAfter: Float) : Result
        data class Unsupported(val reason: String) : Result
    }
    fun compute(input: Input): Result {
        fun unsupported(reason: String) = Result.Unsupported(reason)
        if (!input.sourceTagsObserved || input.bypassesArmor || input.bypassesEffects ||
            input.bypassesResistance || input.bypassesEnchantments)
            return unsupported("unverified_or_bypassing_damage_source")
        if (input.enchantmentProtectionObserved != 0 || !input.externalModifiersKnownAbsent ||
            !input.forgeHurtUnmodified || !input.forgeDamageUnmodified)
            return unsupported("unverified_enchantment_or_external_damage_modifier")
        if (!listOf(input.rawHurtAfterForgeHook, input.armor, input.toughness,
                input.healthBefore, input.absorptionBefore).all { it.isFinite() } ||
            input.rawHurtAfterForgeHook <= 0f || input.rawHurtAfterForgeHook > 100f ||
            input.armor !in 0f..20f || input.toughness !in 0f..20f ||
            input.healthBefore !in 0f..40f || input.absorptionBefore !in 0f..40f ||
            (input.resistanceAmplifier != null && input.resistanceAmplifier !in 0..3))
            return unsupported("outside_bounded_vanilla_nonlethal_profile")
        // CombatRules.getDamageAfterAbsorb(float,float,float), independently reproduced in Float.
        val defense = (input.armor - input.rawHurtAfterForgeHook / (2f + input.toughness / 4f))
            .coerceIn(input.armor * 0.2f, 20f)
        val armored = input.rawHurtAfterForgeHook * (1f - defense / 25f)
        // LivingEntity.getDamageAfterMagicAbsorb: Resistance amplifier 0 is level I.
        val resisted = if (input.resistanceAmplifier == null) armored else
            (armored * (25 - (input.resistanceAmplifier + 1) * 5).toFloat() / 25f).coerceAtLeast(0f)
        // Player.actuallyHurt first/ONLY setter: f3=max(f2-absorption,0), then
        // setAbsorptionAmount(absorption - (f2-f3)); do NOT algebraically simplify Float ops.
        val healthDamage = (resisted - input.absorptionBefore).coerceAtLeast(0f)
        if (healthDamage >= input.healthBefore)
            return unsupported("lethal_or_zero_health_hit_requires_separate_death_oracle")
        val absorbed = resisted - healthDamage
        val absorptionAfter = (input.absorptionBefore - absorbed).coerceAtLeast(0f)
        return Result.Supported(armored, resisted, absorbed, healthDamage,
            (input.healthBefore - healthDamage).coerceAtLeast(0f), absorptionAfter)
    }
}
