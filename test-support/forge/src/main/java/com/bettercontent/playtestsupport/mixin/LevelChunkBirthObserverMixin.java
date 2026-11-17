package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.BlockBirthHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Generation→full-chunk identity capture BEFORE ChunkMap.runPostLoad, Forge Load or first ticking. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkBirthObserverMixin {
    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V",
            at = @At("RETURN"), require = 1)
    private void bc$bornFromProto(ServerLevel level, ProtoChunk proto, LevelChunk.PostLoadProcessor postLoad,
                                   CallbackInfo ci) {
        BlockBirthHooks.fullChunkBorn((LevelChunk) (Object) this, level, proto);
    }
    @Inject(method = "postProcessGeneration()V", at = @At("RETURN"), require = 1)
    private void bc$postprocessComplete(CallbackInfo ci) {
        BlockBirthHooks.postprocessed((LevelChunk) (Object) this);
    }
}
