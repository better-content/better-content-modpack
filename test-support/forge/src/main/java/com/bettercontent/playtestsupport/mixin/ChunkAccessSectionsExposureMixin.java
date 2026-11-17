package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import com.bettercontent.playtestsupport.BlockBirthHooks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Direct mutable section-array exposure is a coverage gap, not evidence of unchanged storage. */
@Mixin(ChunkAccess.class)
public abstract class ChunkAccessSectionsExposureMixin {
    @Inject(method = "getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;",
            at = @At("RETURN"), require = 1)
    private void bc$sectionsExposed(CallbackInfoReturnable<LevelChunkSection[]> cir) {
        NativeSupport.blockSectionsExposed((ChunkAccess) (Object) this);
        BlockBirthHooks.sectionsExposed((ChunkAccess) (Object) this);
    }
}
