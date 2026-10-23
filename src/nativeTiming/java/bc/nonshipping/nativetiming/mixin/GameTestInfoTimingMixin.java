package bc.nonshipping.nativetiming.mixin;

import bc.nonshipping.nativetiming.NativeTimingObserver;
import net.minecraft.gametest.framework.GameTestInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge 1.20.1 source SDK only (official and Parchment named namespaces).
 * startExecution/m_127616_ only sets startTick and starts Stopwatch; original body dispatch is
 * in startTest/m_127649_, reached later from tickInternal. The mapped compile ABI is intentional;
 * the SRG selector is documented/reviewed, not permission to ship this named-class supplement.
 */
@Mixin(value = GameTestInfo.class, remap = false)
public abstract class GameTestInfoTimingMixin {
    @Inject(method = {"startExecution()V", "m_127616_()V"},
            at = @At(value = "INVOKE", target = "Lcom/google/common/base/Stopwatch;start()Lcom/google/common/base/Stopwatch;",
                    shift = At.Shift.AFTER, remap = false),
            require = 1, expect = 1, allow = 1, remap = false)
    private void bcNativeTimingStarted(CallbackInfo callback) {
        NativeTimingObserver.started((GameTestInfo) (Object) this);
    }
}
