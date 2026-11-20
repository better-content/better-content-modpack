package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server-thread only actual inbound packet handler, never calls game-mode/changes packet flow. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RealPacketDigObserverMixin {
    @Inject(method = "handlePlayerAction(Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;)V",
            at = @At("HEAD"), require = 1)
    private void bc$packetBefore(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        NativeSupport.realPacketDigBefore(listener.player, packet);
    }
    @Inject(method = "handlePlayerAction(Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;)V",
            at = @At("RETURN"), require = 1)
    private void bc$packetAfter(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        NativeSupport.realPacketDigAfter(listener.player, packet);
    }
}
