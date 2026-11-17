package com.bettercontent.playtestsupport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Checked-in cross-language wire fixture must be emitted by the actual production serializer. Unrun. */
final class ScaleWireContractTest {
    @Test void producerSerializerEmitsV2FixtureExactly() throws Exception {
        JsonObject sample = new JsonObject(), receipt = new JsonObject();
        ScaleWireContract.sampleClock(sample, 20, 810_000_001_000L, 1_000L,
                10_000_001_000L, 2, 809_000_001_000L, 120, 100);
        ScaleWireContract.receiptClock(receipt, 1_000L, 10_000_001_000L,
                2, 810_000_001_000L, 20, 810_001_001_000L, 1);
        try (var resource = getClass().getResourceAsStream("/scale-wire-contract-v2.json")) {
            assertNotNull(resource, "native v2 producer fixture required");
            JsonObject expected = JsonParser.parseReader(new InputStreamReader(resource, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertEquals(expected.getAsJsonObject("sample"), sample);
            assertEquals(expected.getAsJsonObject("receipt"), receipt);
            assertEquals(expected.getAsJsonArray("view_tuple"), ScaleWireContract.footprintTuple(
                    0, 0, 10, 9, 8, 8, 8, 8, ScaleWireContract.spawnProximity(0, 0, 0, 64)));
            assertEquals(expected.get("interval_masks_base64").getAsString(),
                    ScaleWireContract.intervalMasks(new byte[]{5}, new byte[]{3}));
        }
        assertEquals(sample.get("monotonic_nanos"), receipt.get("last_sample_wall_nanos"));
        assertEquals("external_setup_inclusive_recovery_then_sustained_dwell_required",
                receipt.get("tps_adjudication").getAsString());
    }

    @Test void pinnedSpatialMaskAndTupleNeverEquateViewCornersWithTickEligibility() {
        assertTrue(ScaleWireContract.spawnProximity(0, 0, 0, 0));
        assertFalse(ScaleWireContract.spawnProximity(10, 10, 0, 0));
        assertThrows(IllegalStateException.class, () -> ScaleWireContract.footprintTuple(0, 0,
                1, 1, 1, 1, -1, 0, true));
        assertThrows(IllegalStateException.class, () -> ScaleWireContract.intervalMasks(new byte[]{1}, new byte[2]));
        assertTrue(ScaleWireContract.fullIntervalTicks(140, 120, 20));
        assertFalse(ScaleWireContract.fullIntervalTicks(139, 120, 20));
        assertFalse(ScaleWireContract.fullIntervalTicks(141, 120, 20));
        assertFalse(ScaleWireContract.fullIntervalTicks(119, 120, 20));
    }

    @Test void horizonBoundaryAndSampleCapAreExact() {
        JsonObject boundary = new JsonObject();
        assertDoesNotThrow(() -> ScaleWireContract.receiptClock(boundary,
                0, 1, 1, 810_000_000_000L, 20, 810_000_000_001L, 1000));
        assertEquals(1L, boundary.get("terminal_partial_interval_nanos").getAsLong());
        assertThrows(IllegalStateException.class, () -> ScaleWireContract.receiptClock(new JsonObject(),
                0, 1, 1, 810_000_000_000L, 20, 900_000_000_001L, 1000));
    }

    @Test void rejectsPrematureReceiptOrReversedClockWithoutSyntheticSuccess() {
        JsonObject row = new JsonObject();
        assertThrows(IllegalStateException.class, () -> ScaleWireContract.receiptClock(row,
                0, 1, 1, 809_999_999_999L, 20, 810_000_000_000L, 1));
        assertThrows(IllegalStateException.class, () -> ScaleWireContract.sampleClock(row,
                20, 100, 0, 10, 1, 100, 120, 100));
        assertThrows(IllegalStateException.class, () -> ScaleWireContract.receiptClock(row,
                0, 1, 1, 810_000_000_000L, 20, 809_999_999_999L, 1));
        assertThrows(IllegalStateException.class, () -> ScaleWireContract.receiptClock(row,
                0, 1, 1, 810_000_000_000L, 20, 810_000_000_001L, 1001));
    }
}
