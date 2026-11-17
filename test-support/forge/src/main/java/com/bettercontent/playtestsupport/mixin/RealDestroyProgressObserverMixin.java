package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Read one ACTUAL gameplay return. Does not call getDestroyProgress, modify result, or add a policy evaluation. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class RealDestroyProgressObserverMixin {
    @Inject(method = "getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F",
            at = @At("RETURN"), require = 1)
    private void bc$actualDestroyProgress(Player player, BlockGetter world, BlockPos pos,
                                          CallbackInfoReturnable<Float> cir) {
        NativeSupport.realDestroyProgressReturned((BlockState) (Object) this, player, world, pos, cir.getReturnValueF());
    }
}
