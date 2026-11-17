package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.client.ClientAwtHeadlessBootstrap;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client-only early Main.main hook; opt-in validation and fail-closed AWT readiness belong to C's helper. */
@Mixin(Main.class)
public abstract class ClientAwtBootstrapMixin {
    @Inject(method = "main([Ljava/lang/String;)V", at = @At("HEAD"), require = 1)
    private static void bc$beforeClientMain(String[] arguments, CallbackInfo ci) {
        ClientAwtHeadlessBootstrap.beforeMain();
    }
}
