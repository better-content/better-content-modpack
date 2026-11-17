package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Read-only exact Block.popResource(BlockPos) native call scope; never cancel, redirect or change drop RNG. */
@Mixin(Block.class)
public abstract class BlockPopResourceObserverMixin {
    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), require = 1)
    private static void bc$enter(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
        NativeSupport.popResourceEnter(level, pos, stack);
    }
    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("RETURN"), require = 1)
    private static void bc$exit(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
        NativeSupport.popResourceExit();
    }
}
