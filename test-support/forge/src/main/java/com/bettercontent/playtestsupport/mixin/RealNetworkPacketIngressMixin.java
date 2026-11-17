package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Read-only decoded inbound packet identity before actual listener dispatch; does not send or handle packet. */
@Mixin(Connection.class)
public abstract class RealNetworkPacketIngressMixin {
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), require = 1)
    private void bc$decodedInbound(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        NativeSupport.realNetworkPacketIngress((Connection) (Object) this, packet);
    }
}
