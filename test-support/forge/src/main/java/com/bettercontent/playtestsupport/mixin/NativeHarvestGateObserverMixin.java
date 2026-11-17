package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/** Observe the stored result of the actual native canHarvestBlock call; never invoke policy again. */
@Mixin(ServerPlayerGameMode.class)
public abstract class NativeHarvestGateObserverMixin {
    @Shadow @Final protected ServerPlayer player;

    // Pinned Forge 47.4.22 destroyBlock bytecode: BlockState.canHarvestBlock at 163,
    // ISTORE 8 at 166 (flag1), then ItemStack.mineBlock at 180. Inject immediately
    // BEFORE mineBlock, while the saved harvest flag and the original tool are intact.
    // If a transformed caller loses these locals, fail softly: no observation means
    // harvest_scope_complete=false, not a guessed value or changed gameplay outcome.
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/world/item/ItemStack;mineBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V"),
            locals = LocalCapture.CAPTURE_FAILSOFT, require = 0)
    private void bc$actualHarvestGate(BlockPos pos, CallbackInfoReturnable<Boolean> cir,
                                      BlockState state, int experience, BlockEntity blockEntity,
                                      Block block, ItemStack held, ItemStack heldCopy,
                                      boolean canHarvest) {
        try {
            NativeSupport.harvestCheckReturn(player.serverLevel(), pos, player, state, canHarvest);
        } catch (Throwable ignored) {
            // An observer may fail; the production harvest flag and call remain untouched.
            NativeSupport.harvestObserverFailed();
        }
    }
}
