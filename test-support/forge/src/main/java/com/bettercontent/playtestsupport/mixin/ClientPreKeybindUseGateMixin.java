package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.client.ClientNativeSupport;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client-only noncancelable hook before vanilla reads physical keybind state; C owns opt-in OS release gate. */
@Mixin(Minecraft.class)
public abstract class ClientPreKeybindUseGateMixin {
    @Inject(method = "handleKeybinds()V", at = @At("HEAD"), require = 1)
    private void bc$beforeRealUseKeybinds(CallbackInfo ci) {
        ClientNativeSupport.beforeRealUseKeybinds((Minecraft) (Object) this);
    }
}
