package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Bounded existing-item selection through native server packet handlers; no item grant or menu bypass. */
final class InventorySelection {
    private InventorySelection() {}
    static JsonObject select(ServerPlayer player, JsonObject p) {
        Protocol.only(p, "player_uuid", "slot", "expected_item", "expected_count", "expected_stack_sha256", "expected_stack_canonical_sha256");
        if (player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()
                || player.isUsingItem() || player.connection == null
                || player.connection.connection.channel() == null
                || !player.connection.connection.channel().isActive()
                || !player.connection.connection.channel().isOpen()
                || player.connection.connection.channel() instanceof io.netty.channel.embedded.EmbeddedChannel)
            throw new IllegalArgumentException("actual connected idle player with empty inventory cursor required");
        long number = Protocol.number(p, "slot"), count = Protocol.number(p, "expected_count");
        if (number < 0 || number > 35 || count < 1 || count > 64)
            throw new IllegalArgumentException("selection limited to hotbar+main inventory");
        int slot = (int) number;
        ItemStack original = player.getInventory().getItem(slot);
        String id = Protocol.string(p, "expected_item");
        String expectedHash = Protocol.string(p, "expected_stack_sha256");
        Identity sourceIdentity = identity(original);
        if (!expectedHash.matches("[0-9a-f]{64}") || original.isEmpty()
                || !id.equals(ForgeRegistries.ITEMS.getKey(original.getItem()).toString())
                || original.getCount() != count || !expectedHash.equals(sourceIdentity.snbtSha256()))
            throw new IllegalArgumentException("source slot content/NBT mismatch; no substitute item permitted");
        if (p.has("expected_stack_canonical_sha256")) {
            String canonical = Protocol.string(p, "expected_stack_canonical_sha256");
            if (!canonical.matches("[0-9a-f]{64}") || !canonical.equals(sourceIdentity.canonicalSha256()))
                throw new IllegalArgumentException("source slot typed identity mismatch");
        }
        int selectedBefore = player.getInventory().selected;
        if (selectedBefore < 0 || selectedBefore > 8)
            throw new IllegalStateException("invalid current hotbar selection");
        SortedMap<String, Integer> before = multiset(player);
        String method;
        if (slot <= 8) {
            player.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(slot));
            method = "ServerGamePacketListenerImpl.handleSetCarriedItem";
        } else {
            // InventoryMenu main inventory indices 9..35 map to the same menu slot IDs.
            player.connection.handleContainerClick(new ServerboundContainerClickPacket(
                    0, player.inventoryMenu.getStateId(), slot, selectedBefore, ClickType.SWAP,
                    player.inventoryMenu.getCarried().copy(), new Int2ObjectOpenHashMap<>()));
            method = "ServerGamePacketListenerImpl.handleContainerClick_SWAP";
        }
        SortedMap<String, Integer> after = multiset(player);
        Identity selectedIdentity = identity(player.getMainHandItem());
        if (!before.equals(after) || !ItemStack.matches(original, player.getMainHandItem()) || !sourceIdentity.equals(selectedIdentity))
            throw new IllegalStateException("native selected item or inventory conservation failed");
        JsonObject row = new JsonObject();
        row.addProperty("player_uuid", player.getUUID().toString()); row.addProperty("source_slot", slot);
        row.addProperty("selected_before", selectedBefore); row.addProperty("selected_after", player.getInventory().selected);
        row.addProperty("item", id); row.addProperty("count", count);
        row.addProperty("stack_sha256", selectedIdentity.snbtSha256());
        row.addProperty("stack_canonical_sha256", selectedIdentity.canonicalSha256());
        row.addProperty("stack_canonical_algorithm", CanonicalStackIdentity.ALGORITHM);
        row.addProperty("source_slot_item_after", item(player.getInventory().getItem(slot)));
        row.addProperty("inventory_conserved", true);
        row.addProperty("inventory_multiset_before_sha256", multisetHash(before));
        row.addProperty("inventory_multiset_after_sha256", multisetHash(after));
        row.addProperty("native_packet_handler", method);
        row.addProperty("client_acknowledgment", "unverified_without_client_observer");
        row.addProperty("acquisition_origin", "preexisting_unattributed");
        row.add("fixture_mutations", new JsonArray());
        return row;
    }
    record Identity(String snbtSha256, String canonicalSha256) {}
    static Identity identity(ItemStack stack) {
        if (stack.isEmpty()) return new Identity("empty", "empty");
        CompoundTag saved = stack.save(new CompoundTag());
        return new Identity(sha256(saved.toString()), CanonicalStackIdentity.sha256(saved));
    }
    static String hash(ItemStack stack) {
        if (stack.isEmpty()) return "empty";
        return sha256(stack.save(new CompoundTag()).toString());
    }
    private static SortedMap<String, Integer> multiset(ServerPlayer player) {
        SortedMap<String, Integer> counts = new TreeMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) counts.merge(hash(stack), 1, Integer::sum);
        }
        return counts;
    }
    private static String multisetHash(SortedMap<String, Integer> map) { return sha256(map.toString()); }
    private static String item(ItemStack stack) {
        return stack.isEmpty() ? "minecraft:air" : ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
    }
    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
}
