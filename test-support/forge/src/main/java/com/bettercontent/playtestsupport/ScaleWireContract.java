package com.bettercontent.playtestsupport;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

/** Production serializer for the versioned native scale clock/ordering contract. */
final class ScaleWireContract {
    static final String ADJUDICATION = "external_setup_inclusive_recovery_then_sustained_dwell_required";
    static final String RECEIPT_ORDER = "sample_then_receipt";
    static final long CAPTURE_HORIZON_NANOS = 810_000_000_000L;
    static final long HARD_DEADLINE_NANOS = 900_000_000_000L;
    static final int MAX_SAMPLES = 1000;
    static final int VERSION = 2;
    static final long CAPTURE_HORIZON_MILLIS = CAPTURE_HORIZON_NANOS / 1_000_000L;

    private ScaleWireContract() {}

    static void sampleClock(JsonObject row, long tick, long now, long start, long setup, long setupTick,
                            long previous, long gameTime, long previousGameTime) {
        if (now <= previous || previous < setup || setup < start)
            throw new IllegalStateException("scale monotonic sample anchors out of order");
        row.addProperty("tick", tick);
        row.addProperty("monotonic_nanos", now);
        row.addProperty("start_wall_nanos", start);
        row.addProperty("setup_completed_wall_nanos", setup);
        row.addProperty("setup_completed_tick", setupTick);
        row.addProperty("end_tick_interval_nanos_includes_scheduler_wait", now - previous);
        row.addProperty("game_time", gameTime);
        row.addProperty("observation_wall_millis", (now - setup) / 1_000_000L);
        row.addProperty("native_game_tick_delta", gameTime - previousGameTime);
    }

    static boolean spawnProximity(int chunkX, int chunkZ, int siteX, int siteZ) {
        double dx = chunkX * 16.0 + 8.0 - (siteX + .5);
        double dz = chunkZ * 16.0 + 8.0 - (siteZ + .5);
        return dx * dx + dz * dz < 16_384.0;
    }

    static boolean fullIntervalTicks(int currentEvents, int previousEvents, long gameTicks) {
        if (currentEvents < previousEvents || gameTicks < 1) return false;
        return currentEvents - previousEvents == gameTicks;
    }

    /** Two equal fixed-length bit masks: view-ready bytes, then per-chunk full-interval tick bytes.
     * Each byte is LSB-first across numeric (chunk_x,chunk_z) sorted view candidates. */
    static String intervalMasks(byte[] ready, byte[] ticks) {
        if (ready.length == 0 || ready.length != ticks.length)
            throw new IllegalStateException("scale view and tick mask lengths differ");
        byte[] joined = new byte[ready.length * 2];
        System.arraycopy(ready, 0, joined, 0, ready.length);
        System.arraycopy(ticks, 0, joined, ready.length, ticks.length);
        return java.util.Base64.getEncoder().encodeToString(joined);
    }

    static JsonArray footprintTuple(int chunkX, int chunkZ, int loaded, int blockTicking,
                                    int entityTicking, int natural, int eligible, int events, boolean proximity) {
        if (loaded < 0 || blockTicking < 0 || entityTicking < 0 || natural < 0 || eligible < 0 || events < 0)
            throw new IllegalStateException("negative scale footprint count");
        JsonArray tuple = new JsonArray();
        tuple.add(chunkX); tuple.add(chunkZ); tuple.add(loaded); tuple.add(blockTicking);
        tuple.add(entityTicking); tuple.add(natural); tuple.add(eligible); tuple.add(events);
        tuple.add(proximity ? 1 : 0);
        return tuple;
    }

    static void receiptClock(JsonObject row, long start, long setup, long setupTick, long lastSample,
                             long lastTick, long receiptEnd, int sampleCount) {
        if (setup < start || lastSample < setup || receiptEnd < lastSample ||
                lastSample - start < CAPTURE_HORIZON_NANOS || receiptEnd - start > HARD_DEADLINE_NANOS ||
                sampleCount < 1 || sampleCount > MAX_SAMPLES)
            throw new IllegalStateException("scale receipt anchors, horizon or sample cap invalid");
        row.addProperty("scale_contract_version", VERSION);
        row.addProperty("start_wall_nanos", start);
        row.addProperty("setup_completed_wall_nanos", setup);
        row.addProperty("setup_completed_tick", setupTick);
        row.addProperty("setup_wall_nanos", setup - start);
        row.addProperty("last_sample_wall_nanos", lastSample);
        row.addProperty("last_sample_tick", lastTick);
        row.addProperty("end_wall_nanos", receiptEnd);
        row.addProperty("terminal_partial_interval_nanos", receiptEnd - lastSample);
        row.addProperty("observation_wall_millis", (receiptEnd - setup) / 1_000_000L);
        row.addProperty("capture_horizon_millis", CAPTURE_HORIZON_MILLIS);
        row.addProperty("receipt_order", RECEIPT_ORDER);
        row.addProperty("observed_samples", sampleCount);
        row.addProperty("tps_adjudication", ADJUDICATION);
    }
}
