package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Source-only conservation boundary; no world, callbacks, or receipt qualification executed. */
final class NativeAcquisitionTraceTest {
    private static final String A = "a".repeat(64), B = "b".repeat(64), C = "c".repeat(64);
    private static JsonObject captured(String item, String content, int count) {
        JsonObject row = new JsonObject(); row.addProperty("item_id", item);
        row.addProperty("content_sha256", content); row.addProperty("content_algorithm", "bc.playtest.stack-content.v1");
        row.addProperty("count", count); row.addProperty("canonical_sha256", (count < 10 ? "a" : "b").repeat(64));
        row.addProperty("canonical_algorithm", "bc.playtest.stack-nbt.v1");
        row.addProperty("provider_scope", "vanilla_saved_ItemStack_NBT_including_ForgeCaps_not_external_inventory_adapter");
        return row;
    }
    private static JsonObject slot(int index, String content, int count) {
        JsonObject row = new JsonObject(); row.addProperty("slot", index);
        row.addProperty("content_sha256", content); row.addProperty("count", count); return row;
    }
    private static JsonArray slots(JsonObject... rows) {
        JsonArray result = new JsonArray(); for (JsonObject row : rows) result.add(row); return result;
    }
    @Test void splitAndMergeCountsRemainExactOnlyWithinSameContent() {
        JsonArray before = slots(slot(0, A, 3), slot(1, B, 1));
        JsonArray after = slots(slot(0, A, 2), slot(3, A, 3), slot(1, B, 1));
        assertTrue(NativeAcquisitionTrace.conserved(before, after, A, 2));
        assertFalse(NativeAcquisitionTrace.conserved(before, after, A, 1));
        assertFalse(NativeAcquisitionTrace.conserved(before, after, C, 2));
        assertFalse(NativeAcquisitionTrace.conserved(before, after, A, 0));
    }
    @Test void sourceReferenceHashesPriorJsonlBytesWithLfNotItsOwnFutureRow() throws Exception {
        String prior = "{\"kind\":\"block_drop\",\"event_index\":1}";
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        String expected = java.util.HexFormat.of().formatHex(digest.digest((prior + "\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(expected, NativeAcquisitionTrace.eventDigest(prior));
        assertNotEquals(java.util.HexFormat.of().formatHex(digest.digest(prior
                .getBytes(java.nio.charset.StandardCharsets.UTF_8))), expected);
    }

    @Test void unexplainedSourceShrinkCannotBeRepairedByLocallyBalancedPickup() {
        JsonObject original50 = captured("minecraft:stone", A, 50), unexplained1 = captured("minecraft:stone", A, 1);
        JsonObject acquired1 = captured("minecraft:stone", A, 1);
        assertFalse(NativeAcquisitionTrace.sameExactStack(original50, unexplained1));
        assertTrue(NativeAcquisitionTrace.sourceTransition(unexplained1, acquired1, NativeAcquisitionTrace.empty()));
        assertFalse(NativeAcquisitionTrace.fullyConsumedAtTerminal(original50, 1,
                NativeAcquisitionTrace.empty(), NativeAcquisitionTrace.absent()));
    }
    @Test void partialFiveThenFortyFiveMustExactlyConserveAndRemoveSource() {
        JsonObject source50 = captured("minecraft:stone", A, 50), picked5 = captured("minecraft:stone", A, 5);
        JsonObject remaining45 = captured("minecraft:stone", A, 45), picked45 = captured("minecraft:stone", A, 45);
        JsonObject interim = NativeAcquisitionTrace.present(remaining45);
        assertTrue(NativeAcquisitionTrace.sourceTransition(source50, picked5, interim));
        assertTrue(NativeAcquisitionTrace.sameExactStack(interim.getAsJsonObject("stack"), remaining45));
        assertTrue(NativeAcquisitionTrace.sourceTransition(remaining45, picked45, NativeAcquisitionTrace.empty()));
        assertTrue(NativeAcquisitionTrace.fullyConsumedAtTerminal(source50, 50,
                NativeAcquisitionTrace.empty(), NativeAcquisitionTrace.absent()));
        // Vanilla resets the removed entity stack AFTER discard: never use that reset as a remainder.
        assertFalse(NativeAcquisitionTrace.terminalContinuity(NativeAcquisitionTrace.empty(),
                NativeAcquisitionTrace.present(picked45)));
        assertFalse(NativeAcquisitionTrace.fullyConsumedAtTerminal(source50, 50,
                NativeAcquisitionTrace.empty(), NativeAcquisitionTrace.present(picked45)));
        assertTrue(NativeAcquisitionTrace.terminalContinuity(interim,
                NativeAcquisitionTrace.present(remaining45)));
        assertFalse(NativeAcquisitionTrace.fullyConsumedAtTerminal(source50, 5, interim,
                NativeAcquisitionTrace.present(remaining45))); // unresolved remainder needs sealed adapter
    }
    @Test void sourceNotDecrementedAndForeignContentCannotClaimAcquisition() {
        JsonObject source50 = captured("minecraft:stone", A, 50), picked5 = captured("minecraft:stone", A, 5);
        assertFalse(NativeAcquisitionTrace.sourceTransition(source50, picked5,
                NativeAcquisitionTrace.present(source50)));
        assertFalse(NativeAcquisitionTrace.sourceTransition(source50,
                captured("minecraft:gold_ingot", B, 5), NativeAcquisitionTrace.present(captured("minecraft:stone", A, 45))));
        assertFalse(NativeAcquisitionTrace.sourceTransition(source50, captured("minecraft:stone", A, 0),
                NativeAcquisitionTrace.present(source50)));
        assertFalse(NativeAcquisitionTrace.sameContent(source50, captured("minecraft:stone", C, 50)));
        assertFalse(NativeAcquisitionTrace.sameExactStack(source50, captured("minecraft:stone", A, 5)));
        assertFalse(NativeAcquisitionTrace.fullyConsumedAtTerminal(source50, 0,
                NativeAcquisitionTrace.empty(), NativeAcquisitionTrace.absent()));
    }
    @Test void hiddenOtherContentDriftOrNoSourcePickupNeverQualifies() {
        JsonArray before = slots(slot(0, A, 3), slot(1, B, 1));
        JsonArray drift = slots(slot(0, A, 5), slot(1, C, 1));
        assertFalse(NativeAcquisitionTrace.conserved(before, drift, A, 2));
    }
}
