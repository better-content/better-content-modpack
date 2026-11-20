package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Read the three ORIGINAL CombatRules arguments after hurtArmor without changing any of them.
 * Separate identity @ModifyArg sites avoid the generated Args$1 class that is unavailable
 * in the pinned production Forge/Mixin classloader during instrumented server startup. */
@Mixin(LivingEntity.class)
public abstract class RealIncomingArmorOperandsObserverMixin {
    @Unique private static final ThreadLocal<float[]> bc$armorOperands = new ThreadLocal<>();
    @Unique private static final ThreadLocal<DamageSource> bc$armorSource = new ThreadLocal<>();

    @Inject(method = "getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At("HEAD"), require = 1)
    private void bc$beginArmorOperands(DamageSource source, float amount, CallbackInfoReturnable<Float> ci) {
        bc$armorOperands.set(new float[4]);
        bc$armorSource.set(source);
    }

    @ModifyArg(method = "getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(FFF)F"),
            index = 0, require = 1)
    private float bc$actualDamageOperand(float original) {
        bc$captureOperand(0, original); return original;
    }

    @ModifyArg(method = "getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(FFF)F"),
            index = 1, require = 1)
    private float bc$actualArmorOperand(float original) {
        bc$captureOperand(1, original); return original;
    }

    @ModifyArg(method = "getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(FFF)F"),
            index = 2, require = 1)
    private float bc$actualToughnessOperand(float original) {
        bc$captureOperand(2, original); return original;
    }

    @Unique private void bc$captureOperand(int index, float value) {
        float[] captured = bc$armorOperands.get();
        DamageSource source = bc$armorSource.get();
        if (captured == null || source == null || ((int) captured[3] & (1 << index)) != 0) {
            NativeSupport.incomingArmorCaptureUnavailable((LivingEntity) (Object) this,
                "duplicate_or_missing_original_armor_operand");
            return;
        }
        captured[index] = value;
        captured[3] = (int) captured[3] | (1 << index);
        if ((int) captured[3] == 7)
            NativeSupport.incomingArmorOperands((LivingEntity) (Object) this, source,
                captured[0], captured[1], captured[2]);
    }

    @Inject(method = "getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At("RETURN"), require = 1)
    private void bc$endArmorOperands(DamageSource source, float amount, CallbackInfoReturnable<Float> ci) {
        float[] captured = bc$armorOperands.get();
        if (captured != null && (int) captured[3] != 0 && (int) captured[3] != 7)
            NativeSupport.incomingArmorCaptureUnavailable((LivingEntity) (Object) this,
                "partial_original_armor_operands");
        bc$armorOperands.remove();
        bc$armorSource.remove();
    }
}
