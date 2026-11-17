package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure source regressions; no Minecraft, provider resolver, GUI, tests executed or terrain generated. */
final class PassiveOnboardingReviewedBoundsTest {
    @Test void fixedBeforeInputReviewAreaCanAdmitGenuineMovementButNeverUnboundedSelection() {
        assertTrue(PassiveOnboarding.boundedArea(3, 4, 3)); // C's reviewed arm-centered 3x3x4
        assertTrue(PassiveOnboarding.boundedArea(5, 16, 5)); // native hard limit, not an auto-widen rule
        assertFalse(PassiveOnboarding.boundedArea(6, 4, 3));
        assertFalse(PassiveOnboarding.boundedArea(3, 17, 3));
        assertFalse(PassiveOnboarding.boundedArea(0, 1, 1));
        assertTrue(PassiveOnboarding.requestNearActor(11, 69, -3, 10, 70, -4));
        assertFalse(PassiveOnboarding.requestNearActor(12, 69, -3, 10, 70, -4));
        assertFalse(PassiveOnboarding.requestNearActor(11, 67, -3, 10, 70, -4));
        assertFalse(PassiveOnboarding.requestNearActor(Integer.MAX_VALUE, 70, 0, Integer.MIN_VALUE, 70, 0));
    }
    @Test void providerMayResolveDifferentYButNeverChangedColumnOrDimension() {
        assertEquals(70, PassiveOnboarding.scanTopY(70, -64, 320));
        assertEquals(317, PassiveOnboarding.scanTopY(319, -64, 320));
        assertEquals(-63, PassiveOnboarding.scanTopY(-80, -64, 320));
        // Requested feet y=70 in an actually solid supported base can produce committed feet y=71.
        assertTrue(PassiveOnboarding.sameRequestedColumn("minecraft:overworld@12,71,-9", "minecraft:overworld", 12, -9));
        assertFalse(PassiveOnboarding.sameRequestedColumn("minecraft:the_nether@12,71,-9", "minecraft:overworld", 12, -9));
        assertFalse(PassiveOnboarding.sameRequestedColumn("minecraft:overworld@13,71,-9", "minecraft:overworld", 12, -9));
        assertFalse(PassiveOnboarding.sameRequestedColumn("minecraft:overworld@12,71,-10", "minecraft:overworld", 12, -9));
        assertFalse(PassiveOnboarding.sameRequestedColumn("minecraft:overworld@12,+71,-9", "minecraft:overworld", 12, -9));
    }
    @Test void rawColumnCaptureMustBeCompleteAndOrderedBeforeProviderMutation() {
        JsonArray rows = new JsonArray();
        for (int y = -64; y < 320; y++) {
            JsonObject row = new JsonObject(); row.addProperty("y", y); row.addProperty("block_id", "minecraft:air");
            row.add("properties", new JsonObject()); rows.add(row);
        }
        assertTrue(PassiveOnboarding.columnComplete(rows, -64, 320));
        rows.remove(120);
        assertFalse(PassiveOnboarding.columnComplete(rows, -64, 320));
        JsonArray tooTall = new JsonArray();
        for (int y = 0; y < 385; y++) { JsonObject row = new JsonObject(); row.addProperty("y", y); tooTall.add(row); }
        assertFalse(PassiveOnboarding.columnComplete(tooTall, 0, 385));
    }
}
