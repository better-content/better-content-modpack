package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe genuine original shutdown BEFORE ticket drain/unload and AFTER original save completion. */
@Mixin(MinecraftServer.class)
public abstract class RealShutdownDrainObserverMixin {
    @Inject(method = "stopServer()V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerChunkCache;removeTicketsOnClosing()V"), require = 1)
    private void bc$shutdownDrainEntry(CallbackInfo ci) {
        NativeSupport.shutdownDrainEntry((MinecraftServer) (Object) this);
    }
    @Inject(method = "stopServer()V", at = @At("RETURN"), require = 1)
    private void bc$shutdownDrainReturn(CallbackInfo ci) {
        NativeSupport.shutdownDrainReturn((MinecraftServer) (Object) this);
    }
}
