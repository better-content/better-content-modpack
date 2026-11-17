package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import com.bettercontent.playtestsupport.BlockBirthHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Read-only scope for pinned LevelChunk palette write; does not change return or invoke write/policy. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMutationObserverMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"), require = 1)
    private void bc$blockMutationEnter(BlockPos pos, BlockState state, boolean moved,
                                       CallbackInfoReturnable<BlockState> cir) {
        NativeSupport.blockMutationEnter((LevelChunk) (Object) this, pos);
        BlockBirthHooks.chunkBoundary((LevelChunk) (Object) this, pos);
    }
    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"), require = 1)
    private void bc$blockMutationExit(BlockPos pos, BlockState state, boolean moved,
                                      CallbackInfoReturnable<BlockState> cir) {
        NativeSupport.blockMutationExit((LevelChunk) (Object) this, pos);
        BlockBirthHooks.chunkBoundary((LevelChunk) (Object) this, pos);
    }
}
