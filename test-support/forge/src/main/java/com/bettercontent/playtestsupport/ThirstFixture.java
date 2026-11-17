package com.bettercontent.playtestsupport;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;

import java.lang.reflect.Method;

/** Explicit pinned Thirst API only; snapshot native NBT before setter, never natural water evidence. */
final class ThirstFixture {
    private static final String CAP = "dev.ghen.thirst.foundation.common.capability.ModCapabilities";
    private static final String API = "dev.ghen.thirst.foundation.common.capability.IThirst";
    private ThirstFixture() {}
    private record Access(Object data, Class<?> api) {
        Object call(String name, Class<?>[] params, Object... args) throws ReflectiveOperationException {
            return api.getMethod(name, params).invoke(data, args);
        }
    }
    private static Access access(ServerPlayer player) throws ReflectiveOperationException {
        Class<?> api = Class.forName(API);
        Object field = Class.forName(CAP).getField("PLAYER_THIRST").get(null);
        if (!(field instanceof Capability<?> capability)) throw new IllegalStateException("Thirst API capability signature changed");
        Object data = player.getCapability(capability).resolve()
                .orElseThrow(() -> new IllegalStateException("connected player lacks Thirst capability"));
        if (!api.isInstance(data)) throw new IllegalStateException("Thirst capability does not implement pinned IThirst");
        return new Access(data, api);
    }
    static CompoundTag snapshot(ServerPlayer player) throws ReflectiveOperationException {
        Object tag = access(player).call("serializeNBT", new Class<?>[0]);
        if (!(tag instanceof CompoundTag original)) throw new IllegalStateException("Thirst serialized state is not CompoundTag");
        return original.copy();
    }
    static void provision(ServerPlayer player, int thirst, int quenched) throws ReflectiveOperationException {
        if (thirst < 0 || thirst > 20 || quenched < 0 || quenched > thirst)
            throw new IllegalArgumentException("Thirst fixture target out of range");
        Access api = access(player);
        api.call("setThirst", new Class<?>[]{int.class}, thirst);
        api.call("setQuenched", new Class<?>[]{int.class}, quenched);
        api.call("updateThirstData", new Class<?>[]{Player.class}, player);
        if ((int) api.call("getThirst", new Class<?>[0]) != thirst
                || (int) api.call("getQuenched", new Class<?>[0]) != quenched)
            throw new IllegalStateException("Thirst provider rejected exact authored stimulus");
    }
    static boolean restore(ServerPlayer player, CompoundTag original) throws ReflectiveOperationException {
        Access api = access(player);
        api.call("deserializeNBT", new Class<?>[]{CompoundTag.class}, original.copy());
        api.call("updateThirstData", new Class<?>[]{Player.class}, player);
        return original.equals(snapshot(player));
    }
    static int[] levels(ServerPlayer player) throws ReflectiveOperationException {
        Access api = access(player);
        return new int[]{(int) api.call("getThirst", new Class<?>[0]), (int) api.call("getQuenched", new Class<?>[0])};
    }
}
