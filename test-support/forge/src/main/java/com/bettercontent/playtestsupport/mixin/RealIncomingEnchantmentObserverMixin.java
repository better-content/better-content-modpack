package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.NativeSupport;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Actual existing EnchantmentHelper.getDamageProtection RETURN only; no extra helper invocation. */
@Mixin(EnchantmentHelper.class)
abstract class RealIncomingEnchantmentObserverMixin {
    @Inject(method = "getDamageProtection(Ljava/lang/Iterable;Lnet/minecraft/world/damagesource/DamageSource;)I",
            at = @At("RETURN"), require = 1)
    private static void bc$actualExistingProtection(Iterable<ItemStack> armor, DamageSource source,
                                                     CallbackInfoReturnable<Integer> cir) {
        NativeSupport.incomingEnchantmentReturn(source, cir.getReturnValueI());
    }
}
