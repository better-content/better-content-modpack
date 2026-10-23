package bc.nonshipping.nativetiming.mixin;

import bc.nonshipping.nativetiming.NativeTimingObserver;
import net.minecraft.gametest.framework.GameTestServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bootstrap health even when the original native server legitimately selects zero tests. */
@Mixin(value = GameTestServer.class, remap = false)
public abstract class GameTestServerTimingMixin {
    @Inject(method = {"initServer()Z", "m_7038_()Z"}, at = @At("HEAD"),
            require = 1, expect = 1, allow = 1, remap = false)
    private void bcNativeTimingBootstrap(CallbackInfoReturnable<Boolean> callback) {
        NativeTimingObserver.bootstrap();
    }
}
