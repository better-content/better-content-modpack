package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fail-soft read-only bound provider callback observer: no call to WaterDrinking.sip or WaterSources.debit. */
@Pseudo
@Mixin(targets = "com.bettercontent.betterdrinkingwater.WaterBottleCurio", remap = false)
public abstract class WaterAutoProviderTickObserverMixin {
    @Inject(method = "onPlayerTick(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            at = @At("HEAD"), remap = false, require = 0)
    private static void bc$beforeRealWaterTick(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        NativeSupport.onWaterAutoProviderBefore(event);
    }
    @Inject(method = "onPlayerTick(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            at = @At("RETURN"), remap = false, require = 0)
    private static void bc$afterRealWaterTick(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        NativeSupport.onWaterAutoProviderAfter(event);
    }
}
