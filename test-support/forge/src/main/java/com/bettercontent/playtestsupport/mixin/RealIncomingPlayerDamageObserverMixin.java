package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pinned PLAYER override, not LivingEntity superclass: read exact existing locals; return float UNCHANGED. */
@Mixin(Player.class)
abstract class RealIncomingPlayerDamageObserverMixin {
    private static final String HURT = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V";
    @Inject(method = HURT, at = @At("HEAD"), require = 1)
    private void bc$actualIncomingHead(DamageSource source, float amount, CallbackInfo ci) {
        NativeSupport.incomingHitHead((Player) (Object) this, source, amount);
    }
    @Inject(method = HURT, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/player/Player;getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"), require = 1)
    private void bc$actualHurtReturnBeforeArmor(DamageSource source, float currentAmount, CallbackInfo ci) {
        NativeSupport.incomingBeforeArmor((Player) (Object) this, source, currentAmount);
    }
    @Inject(method = HURT, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"), require = 1)
    private void bc$actualArmorReturnBeforeMagic(DamageSource source, float currentAmount, CallbackInfo ci) {
        NativeSupport.incomingBeforeMagic((Player) (Object) this, source, currentAmount);
    }
    @ModifyVariable(method = HURT, at = @At(value = "INVOKE", target =
            "Lnet/minecraftforge/common/ForgeHooks;onLivingDamage(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F", remap = false), ordinal = 1, require = 1)
    private float bc$actualOnLivingDamageArgument(float actual) {
        NativeSupport.incomingBeforeDamageHook((Player) (Object) this, actual);
        return actual; // no re-evaluation, mutation, or fabricated damage
    }
    // Original FSTORE 3 (bytecode65) AFTER ForgeHooks is ALWAYS reached, including zero.
    // Slice excludes prior FSTORE 3 (the absorption amount) and conditional CombatTracker branch.
    @ModifyVariable(method = HURT, at = @At("STORE"), index = 3, require = 1,
            slice = @Slice(from = @At(value = "INVOKE", target =
                    "Lnet/minecraftforge/common/ForgeHooks;onLivingDamage(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F", remap = false),
                    to = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/damagesource/CombatTracker;recordDamage(Lnet/minecraft/world/damagesource/DamageSource;F)V")))
    private float bc$actualOnLivingDamageReturn(float actual) {
        NativeSupport.incomingAfterDamageHook((Player) (Object) this, actual);
        return actual;
    }
    @Inject(method = HURT, at = @At("RETURN"), require = 1)
    private void bc$actualIncomingReturn(DamageSource source, float amount, CallbackInfo ci) {
        NativeSupport.incomingHitReturn((Player) (Object) this, source);
    }
}
