package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** UNRUN v5 real-producer pure refill decision regressions; no Minecraft/player/world, no fixture generation. */
final class PhysicalToolPickupRefillTest {
    private static final String COUNT_SHA = "a".repeat(64), CONTENT_SHA = "b".repeat(64);
    private static JsonObject empty(int slot) {
        JsonObject row = new JsonObject(); row.addProperty("slot", slot);
        row.addProperty("empty", true); row.add("item", JsonNull.INSTANCE); row.addProperty("count", 0);
        row.add("stack_canonical_algorithm", JsonNull.INSTANCE);
        row.add("stack_canonical_sha256", JsonNull.INSTANCE); return row;
    }
    private static JsonObject full41() {
        JsonObject row = new JsonObject(); row.addProperty("schema", "bc.playtest_vanilla_inventory_slots.v1");
        row.addProperty("source", "actual_server_player_vanilla_inventory_0_40");
        JsonArray slots = new JsonArray(); for (int slot = 0; slot < 41; slot++) slots.add(empty(slot));
        row.add("slots", slots); row.addProperty("external_capabilities_included", false);
        row.addProperty("sealed_playerdata_proven_by_native", false); return row;
    }
    private static JsonObject acquired() {
        JsonObject value = new JsonObject(); value.addProperty("item_id", "minecraft:cobblestone");
        value.addProperty("count", 1); value.addProperty("canonical_sha256", COUNT_SHA);
        value.addProperty("content_sha256", CONTENT_SHA); return value;
    }
    private static JsonObject fullSelected(int slot) {
        JsonObject value = new JsonObject(); value.addProperty("slot", slot); value.addProperty("empty", false);
        value.addProperty("item", "minecraft:cobblestone"); value.addProperty("count", 1);
        value.addProperty("stack_canonical_algorithm", CanonicalStackIdentity.ALGORITHM);
        value.addProperty("stack_canonical_sha256", COUNT_SHA); return value;
    }
    private static JsonObject live() {
        JsonObject value = new JsonObject(); value.addProperty("kind", "nonempty");
        value.addProperty("canonical_sha256", COUNT_SHA); value.addProperty("content_sha256", CONTENT_SHA);
        return value;
    }
    @Test void actualFullyConsumedSourceCanFillExactlyVacatedSelectedSlotAndNoOtherForty() {
        JsonObject prior = full41(), after = full41(), source = acquired(), acquired = acquired();
        after.getAsJsonArray("slots").set(3, fullSelected(3));
        assertTrue(PhysicalToolWitness.exactSelectedRefill(prior, after, 3, source, acquired, live()));
        JsonObject wrongOther = after.deepCopy(); wrongOther.getAsJsonArray("slots").set(9, fullSelected(9));
        assertFalse(PhysicalToolWitness.exactSelectedRefill(prior, wrongOther, 3, source, acquired, live()));
        JsonObject wrongHash = after.deepCopy(); wrongHash.getAsJsonArray("slots").get(3).getAsJsonObject()
                .addProperty("stack_canonical_sha256", "c".repeat(64));
        assertFalse(PhysicalToolWitness.exactSelectedRefill(prior, wrongHash, 3, source, acquired, live()));
        JsonObject partial = acquired.deepCopy(); partial.addProperty("count", 2);
        assertFalse(PhysicalToolWitness.exactSelectedRefill(prior, after, 3, source, partial, live()));
        JsonObject changedBefore = prior.deepCopy(); changedBefore.getAsJsonArray("slots").set(3, fullSelected(3));
        assertFalse(PhysicalToolWitness.exactSelectedRefill(changedBefore, after, 3, source, acquired, live()));
        JsonObject wrongLive = live(); wrongLive.addProperty("content_sha256", "c".repeat(64));
        assertFalse(PhysicalToolWitness.exactSelectedRefill(prior, after, 3, source, acquired, wrongLive));
    }
}
