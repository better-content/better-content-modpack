package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;

/** Read-only, class-allowlisted native Thirst 1.20.1/WaterPurity adapter; never mutate capability state. */
final class WaterSnapshot {
    private static final String CAPABILITIES = "dev.ghen.thirst.foundation.common.capability.ModCapabilities";
    private static final String THIRST_INTERFACE = "dev.ghen.thirst.foundation.common.capability.IThirst";
    private static final String PURITY = "dev.ghen.thirst.content.purity.WaterPurity";
    private WaterSnapshot() {}
    static JsonObject read(ServerPlayer player, String handName) throws ReflectiveOperationException {
        InteractionHand hand = switch (handName) {
            case "main_hand" -> InteractionHand.MAIN_HAND;
            case "off_hand" -> InteractionHand.OFF_HAND;
            default -> throw new IllegalArgumentException("hand must be main_hand/off_hand");
        };
        // These are the only two allowlisted mod classes. No user-supplied class/method names.
        Class<?> capabilityClass = Class.forName(CAPABILITIES);
        Class<?> purityClass = Class.forName(PURITY);
        Object capability = capabilityClass.getField("PLAYER_THIRST").get(null);
        if (!(capability instanceof Capability<?> typed)) throw new IllegalStateException("Thirst capability signature changed");
        Object thirst = player.getCapability(typed).resolve().orElseThrow(
                () -> new IllegalStateException("real player lacks Thirst capability"));
        Class<?> api = Class.forName(THIRST_INTERFACE);
        if (!api.isInstance(thirst)) throw new IllegalStateException("Thirst capability interface mismatch");
        Method getThirst = api.getMethod("getThirst"), getQuenched = api.getMethod("getQuenched");
        if (getThirst.getReturnType() != int.class || getQuenched.getReturnType() != int.class)
            throw new IllegalStateException("Thirst getter signatures changed");
        Method itemPurity = purityClass.getMethod("getPurity", ItemStack.class);
        Method fluidPurity = purityClass.getMethod("getPurity", FluidStack.class);
        if (itemPurity.getReturnType() != int.class || fluidPurity.getReturnType() != int.class)
            throw new IllegalStateException("WaterPurity signatures changed");
        ItemStack held = player.getItemInHand(hand).copy();
        JsonObject result = new JsonObject(); result.addProperty("player_uuid", player.getUUID().toString());
        result.addProperty("hand", handName); result.addProperty("thirst", (int) getThirst.invoke(thirst));
        result.addProperty("quenched", (int) getQuenched.invoke(thirst));
        result.addProperty("held_item", held.isEmpty() ? "minecraft:air" : ForgeRegistries.ITEMS.getKey(held.getItem()).toString());
        result.addProperty("held_count", held.getCount());
        if (held.isEmpty()) result.add("item_purity", com.google.gson.JsonNull.INSTANCE);
        else result.addProperty("item_purity", (int) itemPurity.invoke(null, held.copy()));
        JsonArray fluids = new JsonArray();
        held.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).ifPresent(handler -> {
            if (handler.getTanks() > 16) throw new IllegalStateException("fluid tank cap exceeded");
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack stack = handler.getFluidInTank(tank).copy();
                if (stack.isEmpty()) continue;
                JsonObject row = new JsonObject(); row.addProperty("tank", tank);
                row.addProperty("fluid", ForgeRegistries.FLUIDS.getKey(stack.getFluid()).toString());
                row.addProperty("amount", stack.getAmount());
                try { row.addProperty("purity", (int) fluidPurity.invoke(null, stack.copy())); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException("WaterPurity fluid adapter failed", failure); }
                fluids.add(row);
            }
        });
        result.add("fluids", fluids);
        result.addProperty("capability_source", CAPABILITIES + ".PLAYER_THIRST");
        result.addProperty("purity_source", PURITY + ".getPurity");
        return result;
    }
}
