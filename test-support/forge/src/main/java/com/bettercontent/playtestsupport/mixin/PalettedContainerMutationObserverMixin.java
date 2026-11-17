package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import com.bettercontent.playtestsupport.BlockBirthHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Read-only innermost pinned full-chunk palette writes; wrappers are deliberately NOT also counted. */
@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMutationObserverMixin {
    @Inject(method = "getAndSet(ILjava/lang/Object;)Ljava/lang/Object;", at = @At("RETURN"), require = 1)
    private void bc$getAndSetIndex(int index, Object desired, CallbackInfoReturnable<Object> cir) {
        NativeSupport.blockContainerGetAndSet((PalettedContainer<?>) (Object) this, index, cir.getReturnValue());
        BlockBirthHooks.getAndSet((PalettedContainer<?>) (Object) this, index, cir.getReturnValue());
    }
    @Inject(method = "set(ILjava/lang/Object;)V", at = @At("HEAD"), require = 1)
    private void bc$setIndexBefore(int index, Object desired, CallbackInfo ci) {
        NativeSupport.blockContainerSetBefore((PalettedContainer<?>) (Object) this, index);
        BlockBirthHooks.setBefore((PalettedContainer<?>) (Object) this, index);
    }
    @Inject(method = "set(ILjava/lang/Object;)V", at = @At("RETURN"), require = 1)
    private void bc$setIndexAfter(int index, Object desired, CallbackInfo ci) {
        NativeSupport.blockContainerSetAfter((PalettedContainer<?>) (Object) this, index);
        BlockBirthHooks.setAfter((PalettedContainer<?>) (Object) this, index);
    }
    @Inject(method = "read(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("HEAD"), require = 1)
    private void bc$readBefore(FriendlyByteBuf bytes, CallbackInfo ci) {
        NativeSupport.blockContainerReadBefore((PalettedContainer<?>) (Object) this);
        BlockBirthHooks.readBefore((PalettedContainer<?>) (Object) this);
    }
    @Inject(method = "read(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("RETURN"), require = 1)
    private void bc$readAfter(FriendlyByteBuf bytes, CallbackInfo ci) {
        NativeSupport.blockContainerReadAfter((PalettedContainer<?>) (Object) this);
        BlockBirthHooks.readAfter((PalettedContainer<?>) (Object) this);
    }
}
