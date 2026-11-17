package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bracket the actual inherited magic path, not a separate protection/damage evaluation. */
@Mixin(LivingEntity.class)
abstract class RealIncomingMagicObserverMixin {
    @Inject(method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At("HEAD"), require = 1)
    private void bc$actualMagicHead(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        NativeSupport.incomingMagicHead((LivingEntity) (Object) this, source);
    }
    @Inject(method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getDamageProtection(Ljava/lang/Iterable;Lnet/minecraft/world/damagesource/DamageSource;)I"), require = 1)
    private void bc$actualHelperInvoke(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        NativeSupport.incomingEnchantmentInvoke((LivingEntity) (Object) this, source);
    }
    @Inject(method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(value = "RETURN", ordinal = 0), require = 1)
    private void bc$bypassEffectsReturn(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        NativeSupport.incomingMagicReturnBranch((LivingEntity) (Object) this, source, "bypass_effects", cir.getReturnValueF());
    }
    @Inject(method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(value = "RETURN", ordinal = 1), require = 1)
    private void bc$nonpositiveBeforeHelperReturn(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        NativeSupport.incomingMagicReturnBranch((LivingEntity) (Object) this, source, "nonpositive_before_helper", cir.getReturnValueF());
    }
    @Inject(method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(value = "RETURN", ordinal = 2), require = 1)
    private void bc$bypassEnchantmentsReturn(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        NativeSupport.incomingMagicReturnBranch((LivingEntity) (Object) this, source, "bypass_enchantments", cir.getReturnValueF());
    }
    @Inject(method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(value = "RETURN", ordinal = 3), require = 1)
    private void bc$afterHelperReturn(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        NativeSupport.incomingMagicReturnBranch((LivingEntity) (Object) this, source, "after_helper", cir.getReturnValueF());
    }
    @Inject(method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At("RETURN"), require = 1)
    private void bc$actualMagicReturn(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        NativeSupport.incomingMagicReturn((LivingEntity) (Object) this, source, cir.getReturnValueF());
    }
}
