package com.bettercontent.playtestsupport;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.*;

/** UNRUN regression source for explicit empty/slot bounds; full server integration remains separate. */
final class VanillaInventorySlotsTest {
    @BeforeAll static void bootstrapMinecraft() {
        PlainJvmRegistryBootstrap.initialize();
    }
    @Test void typedEmptyHasNoFabricatedAirIdentityOrCanonicalHash() {
        var row = VanillaInventorySlots.slot(40, ItemStack.EMPTY);
        assertEquals(40, row.get("slot").getAsInt());
        assertTrue(row.get("empty").getAsBoolean());
        assertEquals(0, row.get("count").getAsInt());
        assertTrue(row.get("item").isJsonNull());
        assertTrue(row.get("stack_canonical_algorithm").isJsonNull());
        assertTrue(row.get("stack_canonical_sha256").isJsonNull());
        assertThrows(IllegalArgumentException.class, () -> VanillaInventorySlots.slot(41, ItemStack.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> VanillaInventorySlots.slot(-1, ItemStack.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> VanillaInventorySlots.slot(0, null));
    }
}
