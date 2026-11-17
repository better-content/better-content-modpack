package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Exactly 41 vanilla slots, including explicit empties. No Curios/capability/inferred sealed-state claim. */
final class VanillaInventorySlots {
    static final String SCHEMA = "bc.playtest_vanilla_inventory_slots.v1";
    private VanillaInventorySlots() {}
    static JsonObject capture(ServerPlayer player) {
        int size = player.getInventory().getContainerSize();
        if (size != 41) throw new IllegalStateException("vanilla 41-slot inventory unavailable");
        JsonObject row = new JsonObject(); row.addProperty("schema", SCHEMA);
        row.addProperty("source", "actual_server_player_vanilla_inventory_0_40");
        JsonArray slots = new JsonArray();
        for (int slot = 0; slot < 41; slot++) slots.add(slot(slot, player.getInventory().getItem(slot)));
        row.add("slots", slots);
        row.addProperty("external_capabilities_included", false);
        row.addProperty("sealed_playerdata_proven_by_native", false);
        return row;
    }
    static JsonObject slot(int index, ItemStack actual) {
        if (index < 0 || index > 40 || actual == null)
            throw new IllegalArgumentException("vanilla slot absent or outside 0..40");
        JsonObject row = new JsonObject(); row.addProperty("slot", index);
        if (actual.isEmpty()) {
            row.addProperty("empty", true);
            row.add("item", JsonNull.INSTANCE); row.addProperty("count", 0);
            row.add("stack_canonical_algorithm", JsonNull.INSTANCE);
            row.add("stack_canonical_sha256", JsonNull.INSTANCE);
            return row;
        }
        ItemStack copy = actual.copy();
        var id = ForgeRegistries.ITEMS.getKey(copy.getItem());
        if (id == null || copy.getCount() < 1 || copy.getCount() > copy.getMaxStackSize())
            throw new IllegalStateException("invalid actual vanilla stack registry/count");
        row.addProperty("empty", false); row.addProperty("item", id.toString());
        row.addProperty("count", copy.getCount());
        row.addProperty("stack_canonical_algorithm", CanonicalStackIdentity.ALGORITHM);
        row.addProperty("stack_canonical_sha256", CanonicalStackIdentity.sha256(copy.save(new net.minecraft.nbt.CompoundTag())));
        return row;
    }
}
