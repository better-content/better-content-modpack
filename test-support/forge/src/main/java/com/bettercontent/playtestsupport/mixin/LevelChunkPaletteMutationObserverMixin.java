package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import com.bettercontent.playtestsupport.BlockBirthHooks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Actual old/new palette state at write RETURN, before LevelChunk may run recursive block callbacks. */
@Mixin(LevelChunkSection.class)
public abstract class LevelChunkPaletteMutationObserverMixin {
    @Inject(method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"), require = 1)
    private void bc$paletteMutation(int x, int y, int z, BlockState requested, boolean checked,
                                    CallbackInfoReturnable<BlockState> cir) {
        NativeSupport.blockPaletteWritten((LevelChunkSection) (Object) this, x, y, z, cir.getReturnValue(), requested);
        BlockBirthHooks.sectionWritten((LevelChunkSection) (Object) this, x, y, z, cir.getReturnValue());
    }
    @Inject(method = "getStates()Lnet/minecraft/world/level/chunk/PalettedContainer;",
            at = @At("RETURN"), require = 1)
    private void bc$mutableStatesExposed(CallbackInfoReturnable<PalettedContainer<BlockState>> cir) {
        NativeSupport.blockStatesExposed((LevelChunkSection) (Object) this, cir.getReturnValue());
        BlockBirthHooks.statesExposed((LevelChunkSection) (Object) this, cir.getReturnValue());
    }
}
