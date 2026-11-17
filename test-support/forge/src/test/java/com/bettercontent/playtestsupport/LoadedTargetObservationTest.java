package com.bettercontent.playtestsupport;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure contract regressions only; no Minecraft world or runtime test execution. */
final class LoadedTargetObservationTest {
    private static JsonObject box(int x0, int x1, int y0, int y1, int z0, int z1) {
        JsonObject value = new JsonObject();
        value.addProperty("min_x", x0); value.addProperty("max_x", x1);
        value.addProperty("min_y", y0); value.addProperty("max_y", y1);
        value.addProperty("min_z", z0); value.addProperty("max_z", z1);
        value.addProperty("maximum_distance_blocks", 64);
        return value;
    }
    @Test void blockVolumeAndEntityAxisAreIndependentlyHardCapped() {
        var block = LoadedTargetObservation.parseBounds(box(-8, 7, 0, 15, -8, 7));
        assertEquals(4096, block.positions());
        assertTrue(LoadedTargetObservation.sizeValid(block, "block"));
        assertFalse(LoadedTargetObservation.sizeValid(
                LoadedTargetObservation.parseBounds(box(-8, 8, 0, 15, -8, 7)), "block"));
        assertTrue(LoadedTargetObservation.sizeValid(
                LoadedTargetObservation.parseBounds(box(0, 127, 0, 127, 0, 127)), "living_entity"));
        assertFalse(LoadedTargetObservation.sizeValid(
                LoadedTargetObservation.parseBounds(box(0, 128, 0, 0, 0, 0)), "living_entity"));
        assertFalse(LoadedTargetObservation.sizeValid(block, "unsupported_kind"));
        var enormous = LoadedTargetObservation.parseBounds(box(-30_000_000, 30_000_000,
                -2048, 2048, -30_000_000, 30_000_000));
        assertEquals(Long.MAX_VALUE, enormous.positions()); // reject overflow, never wrap into a valid scan
        assertFalse(LoadedTargetObservation.sizeValid(enormous, "block"));
        var negative = LoadedTargetObservation.parseBounds(box(-17, 16, 0, 0, -1, 0));
        assertEquals(-2, negative.minChunkX()); assertEquals(1, negative.maxChunkX());
        assertEquals(-1, negative.minChunkZ()); assertEquals(0, negative.maxChunkZ());
        assertThrows(IllegalArgumentException.class, () -> LoadedTargetObservation.parseBounds(box(1, 0, 0, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> LoadedTargetObservation.parseBounds(box(-30_000_001, 0, 0, 0, 0, 0)));
        var extra = box(0, 0, 0, 0, 0, 0); extra.addProperty("ignored_review_rule", "unsafe");
        assertThrows(IllegalArgumentException.class, () -> LoadedTargetObservation.parseBounds(extra));
    }
    @Test void missingChunkOverflowAndErrorNeverProduceCompleteCensus() {
        assertTrue(LoadedTargetObservation.complete(true, false, false)); // empty, fully inspected is valid
        assertFalse(LoadedTargetObservation.complete(false, false, false));
        assertFalse(LoadedTargetObservation.complete(true, true, false));
        assertFalse(LoadedTargetObservation.complete(true, false, true));
        assertTrue(LoadedTargetObservation.canAppend(0, 100, 0, 1));
        assertFalse(LoadedTargetObservation.canAppend(0, 100, 1, 1));
        assertFalse(LoadedTargetObservation.canAppend(LoadedTargetObservation.MAX_BYTES - 4090, 100, 0, 4096));
        assertFalse(Protocol.mayMutate(LoadedTargetObservation.DRIVER));
    }
}
