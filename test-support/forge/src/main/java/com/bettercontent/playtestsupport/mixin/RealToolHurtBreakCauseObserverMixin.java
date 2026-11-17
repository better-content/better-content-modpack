package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Read-only marker around the ACTUAL Consumer.accept in vanilla ItemStack.hurtAndBreak; no damage or callback invoked. */
@Mixin(ItemStack.class)
abstract class RealToolHurtBreakCauseObserverMixin {
    private static final String CONSUMER = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V";
    @Inject(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = CONSUMER, shift = At.Shift.BEFORE), require = 1)
    private <T extends LivingEntity> void bc$beforeRealBreakConsumer(int amount, T actor, Consumer<T> callback, CallbackInfo ci) {
        NativeSupport.onToolBreakCauseBefore((ItemStack) (Object) this, actor);
    }
    @Inject(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = CONSUMER, shift = At.Shift.AFTER), require = 1)
    private <T extends LivingEntity> void bc$afterRealBreakConsumer(int amount, T actor, Consumer<T> callback, CallbackInfo ci) {
        NativeSupport.onToolBreakCauseAfter((ItemStack) (Object) this, actor);
    }
}
