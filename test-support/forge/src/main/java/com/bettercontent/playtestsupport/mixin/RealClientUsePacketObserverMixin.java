package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Read-only genuine network packet handler attribution; no synthetic useItem invocation. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RealClientUsePacketObserverMixin {
    @Inject(method = "handleUseItem(Lnet/minecraft/network/protocol/game/ServerboundUseItemPacket;)V",
            at = @At("HEAD"), require = 1)
    private void bc$usePacket(ServerboundUseItemPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        NativeSupport.onRealClientUsePacket(listener.player, packet.getHand());
    }
}
