package com.bettercontent.playtestsupport.mixin;

import com.bettercontent.playtestsupport.client.ClientNativeSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Set;

/** Client-only passive original render/slot-click observations. Never computes a hit test or clicks. */
@Mixin(AbstractContainerScreen.class)
public abstract class RealContainerScreenObserverMixin {
    @Shadow private Slot clickedSlot;
    @Shadow private ItemStack draggingItem;
    @Shadow private boolean isSplittingStack;
    @Shadow protected boolean isQuickCrafting;
    @Shadow protected Set<Slot> quickCraftSlots;
    @Shadow private ItemStack snapbackItem;

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), require = 1)
    private void bc$beforeOriginalContainerRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                                  CallbackInfo ci) {
        ClientNativeSupport.observedOriginalContainerRenderBegin(Minecraft.getInstance(),
                (AbstractContainerScreen<?>) (Object) this);
    }

    /** Captures original Slot.getItem return already stored in renderSlot local 5; never calls it again. */
    @Inject(method = "renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V",
            at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;", ordinal = 0),
            locals = LocalCapture.CAPTURE_FAILSOFT, require = 1)
    private void bc$originalInFlightRenderedSlotItem(GuiGraphics graphics, Slot slot, CallbackInfo ci,
                                                      int originalX, int originalY, ItemStack originalInFlight) {
        ClientNativeSupport.observedOriginalRenderedSlotItem(Minecraft.getInstance(),
                (AbstractContainerScreen<?>) (Object) this, slot, originalX, originalY, originalInFlight,
                clickedSlot == null, draggingItem != null && draggingItem.isEmpty(), !isSplittingStack,
                !isQuickCrafting, quickCraftSlots != null && quickCraftSlots.isEmpty(),
                snapbackItem != null && snapbackItem.isEmpty());
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("RETURN"), require = 1)
    private void bc$afterOriginalContainerRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                                 CallbackInfo ci) {
        ClientNativeSupport.observedOriginalContainerRender(Minecraft.getInstance(),
                (AbstractContainerScreen<?>) (Object) this);
    }

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V",
            at = @At("HEAD"), require = 1)
    private void bc$originalSlotClicked(Slot slot, int slotId, int button, ClickType type, CallbackInfo ci) {
        ClientNativeSupport.observedOriginalContainerSlotClick(Minecraft.getInstance(),
                (AbstractContainerScreen<?>) (Object) this, slot, slotId, button, type);
    }
}
