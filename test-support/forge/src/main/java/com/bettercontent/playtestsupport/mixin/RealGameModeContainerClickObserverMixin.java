package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.client.ClientNativeSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client-only passive callsite: original menu click executed and original packet constructed, before send. */
@Mixin(MultiPlayerGameMode.class)
public abstract class RealGameModeContainerClickObserverMixin {
    @Inject(method = "handleInventoryMouseClick(IIILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"),
            require = 1)
    private void bc$actualOriginalClientContainerSend(int containerId, int slotId, int button, ClickType type,
                                                      Player player, CallbackInfo ci) {
        ClientNativeSupport.observedOriginalGameModeInventoryClick(Minecraft.getInstance(),
                containerId, slotId, button, type, player);
    }
}
