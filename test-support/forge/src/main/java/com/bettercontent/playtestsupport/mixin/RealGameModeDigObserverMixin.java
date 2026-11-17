package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe the real packet-driven GameMode path, not an extra START/STOP or destroy call. */
@Mixin(ServerPlayerGameMode.class)
public abstract class RealGameModeDigObserverMixin {
    @Shadow @Final protected ServerPlayer player;
    @Inject(method = "handleBlockBreakAction(Lnet/minecraft/core/BlockPos;Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;Lnet/minecraft/core/Direction;II)V",
            at = @At("HEAD"), require = 1)
    private void bc$actualBreakAction(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction face,
                                      int maxHeight, int packetSequence, CallbackInfo ci) {
        NativeSupport.realGameModeDigAction(player, pos, action, face, packetSequence);
    }
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), require = 1)
    private void bc$actualDestroyHead(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        NativeSupport.realGameModeDestroyBefore(player, pos);
    }
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z", at = @At("RETURN"), require = 1)
    private void bc$actualDestroyReturn(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        NativeSupport.realGameModeDestroyAfter(player, pos, cir.getReturnValueZ());
    }
}
