package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional fail-soft read-only hook into pinned Better Spawns enqueued server handler, not packet decode. */
@Pseudo
@Mixin(targets = "com.bettercontent.betterspawns.network.FinalizeSelectionPacket$Companion", remap = false)
public abstract class BetterSpawnsFinalizeObserverMixin {
    @Inject(method = "handle$lambda$0(Lnet/minecraftforge/network/NetworkEvent$Context;Lcom/bettercontent/betterspawns/network/FinalizeSelectionPacket;)V",
            at = @At("HEAD"), remap = false, require = 0)
    private static void bc$before(NetworkEvent.Context context, @Coerce Object packet, CallbackInfo ci) {
        NativeSupport.onBetterSpawnsFinalizeBefore(context, packet);
    }
    @Inject(method = "handle$lambda$0(Lnet/minecraftforge/network/NetworkEvent$Context;Lcom/bettercontent/betterspawns/network/FinalizeSelectionPacket;)V",
            at = @At("RETURN"), remap = false, require = 0)
    private static void bc$after(NetworkEvent.Context context, @Coerce Object packet, CallbackInfo ci) {
        NativeSupport.onBetterSpawnsFinalizeAfter(context, packet);
    }
}
