package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Reads the ORIGINAL CombatRules arguments AFTER hurtArmor; never sets/recomputes any argument. */
@Mixin(LivingEntity.class)
public abstract class RealIncomingArmorOperandsObserverMixin {
    @ModifyArgs(method = "getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(FFF)F"),
            require = 1)
    private void bc$actualArmorOperands(Args args, DamageSource source, float amount) {
        NativeSupport.incomingArmorOperands((LivingEntity) (Object) this, source,
                args.get(0), args.get(1), args.get(2));
    }
}
