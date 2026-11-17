package com.bettercontent.playtestsupport;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Test-only vanilla registry setup, NOT Forge lifecycle or transformed-network readiness. */
final class PlainJvmRegistryBootstrap {
    private PlainJvmRegistryBootstrap() {}

    static void initialize() {
        net.minecraft.SharedConstants.tryDetectVersion();
        try {
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (ExceptionInInitializerError boundary) {
            // Pinned Forge 47.4.22 initializes/snapshots vanilla registries before NetworkHooks.init.
            // Its untransformed NetworkEvent lacks the generated zero-argument constructor.
            if (!knownNetworkBoundary(boundary)) throw boundary;
        }
        // isBootstrapped alone is insufficient: Minecraft sets that flag BEFORE registry work.
        assertSame(Blocks.STONE, BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft", "stone")));
        assertSame(Blocks.AIR, BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft", "air")));
        assertSame(Items.STONE, BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft", "stone")));
        // Defaulted registries can return AIR for a missing lookup: assert actual key membership too.
        assertTrue(BuiltInRegistries.BLOCK.containsKey(new ResourceLocation("minecraft", "air")));
        assertTrue(BuiltInRegistries.BLOCK.containsKey(new ResourceLocation("minecraft", "stone")));
        assertTrue(BuiltInRegistries.ITEM.containsKey(new ResourceLocation("minecraft", "stone")));
        assertEquals(new ResourceLocation("minecraft", "air"), BuiltInRegistries.BLOCK.getKey(Blocks.AIR));
        assertEquals(new ResourceLocation("minecraft", "stone"), BuiltInRegistries.BLOCK.getKey(Blocks.STONE));
        assertEquals(new ResourceLocation("minecraft", "stone"), BuiltInRegistries.ITEM.getKey(Items.STONE));
        assertSame(Blocks.STONE, Blocks.STONE.defaultBlockState().getBlock());
        assertTrue(Blocks.AIR.defaultBlockState().isAir());
        assertTrue(ItemStack.EMPTY.isEmpty());
        assertEquals(0, ItemStack.EMPTY.getCount());
    }

    static boolean knownNetworkBoundary(ExceptionInInitializerError boundary) {
        Throwable cause = boundary.getCause();
        return cause instanceof RuntimeException &&
            "Error computing listener list for net.minecraftforge.network.NetworkEvent".equals(cause.getMessage()) &&
            cause.getCause() instanceof NoSuchMethodException missing &&
            "net.minecraftforge.network.NetworkEvent.<init>()".equals(missing.getMessage()) &&
            missing.getCause() == null &&
            frame(boundary, "net.minecraftforge.network.NetworkHooks", "init") &&
            frame(cause, "net.minecraftforge.eventbus.api.EventListenerHelper", "computeListenerList") &&
            frame(cause, "net.minecraftforge.network.NetworkConstants", "<clinit>");
    }

    private static boolean frame(Throwable failure, String owner, String method) {
        return Arrays.stream(failure.getStackTrace()).anyMatch(frame ->
            frame.getClassName().equals(owner) && frame.getMethodName().equals(method));
    }
}
