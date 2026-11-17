package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ONLY the EquipmentSlot overload: InteractionHand delegates to it, so never double-count. */
@Mixin(LivingEntity.class)
abstract class RealEquipmentBreakObserverMixin {
    @Inject(method = "broadcastBreakEvent(Lnet/minecraft/world/entity/EquipmentSlot;)V",
            at = @At("HEAD"), require = 1)
    private void bc$actualEquipmentBreak(EquipmentSlot slot, CallbackInfo ci) {
        NativeSupport.onToolEquipmentBreak((LivingEntity) (Object) this, slot);
    }
}
