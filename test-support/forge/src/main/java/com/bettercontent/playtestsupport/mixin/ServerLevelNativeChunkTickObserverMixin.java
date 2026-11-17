package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Read-only native active chunk tick, not ticket presence or forced chunks. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelNativeChunkTickObserverMixin {
    @Inject(method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V", at = @At("HEAD"), require = 1)
    private void bc$observedNativeChunkTick(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        NativeSupport.nativeChunkTick((ServerLevel) (Object) this, chunk);
    }
}
