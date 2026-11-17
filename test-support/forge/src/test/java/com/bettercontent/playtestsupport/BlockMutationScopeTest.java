package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Pure closed immutable startup-contract regressions; never loads/edits a world or runs a fixture generator. */
final class BlockMutationScopeTest {
    private static final String SESSION = "epoch-123e4567-e89b-12d3-a456-426614174000";
    private static JsonObject config() {
        JsonObject row = new JsonObject();
        row.addProperty("schema", BlockMutationScope.SCHEMA);
        row.addProperty("scope_id", "scope-123e4567-e89b-12d3-a456-426614174001");
        row.addProperty("campaign_id", "campaign-123e4567-e89b-12d3-a456-426614174002");
        row.addProperty("lineage_id", "123e4567-e89b-12d3-a456-426614174003");
        row.addProperty("epoch_id", "epoch-00001"); row.addProperty("native_session_id", SESSION);
        row.addProperty("purpose", "qualification"); row.addProperty("source", "controller_prelaunch");
        row.addProperty("source_checkpoint_id", "pristine-origin");
        row.addProperty("source_checkpoint_sha256", "a".repeat(64));
        row.addProperty("max_events", BlockMutationScope.MAX_EVENTS);
        row.addProperty("max_bytes", BlockMutationScope.MAX_BYTES);
        JsonArray positions = new JsonArray();
        positions.add(pos("minecraft:overworld", -1, 65, -1));
        positions.add(pos("minecraft:overworld", 0, 65, 0));
        row.add("positions", positions); return row;
    }
    private static JsonObject pos(String dim, int x, int y, int z) {
        JsonObject row = new JsonObject(); row.addProperty("dimension", dim);
        row.addProperty("x", x); row.addProperty("y", y); row.addProperty("z", z); return row;
    }
    private static BlockMutationScope parse(JsonObject row) {
        byte[] bytes = row.toString().getBytes(StandardCharsets.UTF_8);
        return BlockMutationScope.parse(bytes, BlockMutationScope.sha256(bytes), SESSION, Path.of("/nonruntime/scope.json"));
    }
    @Test void requiresClosedLifecycleAndExactlySortedCanonicalPositionsBeforeWorldLoad() {
        var result = parse(config());
        assertEquals(2, result.positions.size());
        assertTrue(result.positionSet.contains(new BlockMutationScope.Position("minecraft:overworld", -1, 65, -1)));
        assertEquals("pristine-origin", result.sourceCheckpointId);
        var extras = config(); extras.addProperty("request_sequence", 1);
        assertThrows(IllegalArgumentException.class, () -> parse(extras));
        var duplicate = config(); duplicate.getAsJsonArray("positions").add(pos("minecraft:overworld", 0, 65, 0));
        assertThrows(IllegalArgumentException.class, () -> parse(duplicate));
        var reverse = config(); reverse.getAsJsonArray("positions").set(0, pos("minecraft:overworld", 2, 65, 0));
        assertThrows(IllegalArgumentException.class, () -> parse(reverse));
        var otherSession = config(); otherSession.addProperty("native_session_id", "epoch-123e4567-e89b-12d3-a456-426614174010");
        assertThrows(IllegalArgumentException.class, () -> parse(otherSession));
        var fakeRequest = config(); fakeRequest.addProperty("source", "mailbox_action");
        assertThrows(IllegalArgumentException.class, () -> parse(fakeRequest));
    }
    @Test void requiresByteExactShaAndHardRowByteCaps() {
        byte[] bytes = config().toString().getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> BlockMutationScope.parse(bytes, "b".repeat(64), SESSION, Path.of("/nonruntime/scope.json")));
        byte[] duplicate = config().toString().replace("\"max_bytes\":16777216",
                "\"max_bytes\":16777216,\"max_bytes\":16777216").getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> BlockMutationScope.parse(duplicate, BlockMutationScope.sha256(duplicate), SESSION, Path.of("/nonruntime/scope.json")));
        byte[] malformedUtf8 = config().toString().getBytes(StandardCharsets.UTF_8);
        malformedUtf8[20] = (byte) 0xff;
        assertThrows(IllegalArgumentException.class,
                () -> BlockMutationScope.parse(malformedUtf8, BlockMutationScope.sha256(malformedUtf8), SESSION, Path.of("/nonruntime/scope.json")));
        assertTrue(BlockMutationTrace.fits(0, 100, 0));
        assertFalse(BlockMutationTrace.fits(BlockMutationScope.MAX_BYTES - 5, 6, 0));
        assertFalse(BlockMutationTrace.fits(0, 1, BlockMutationScope.MAX_EVENTS));
        var oversized = config(); oversized.getAsJsonArray("positions").add(pos("minecraft:overworld", 30_000_001, 65, 0));
        assertThrows(IllegalArgumentException.class, () -> parse(oversized));
        var wrongLimit = config(); wrongLimit.addProperty("max_bytes", BlockMutationScope.MAX_BYTES + 1);
        assertThrows(IllegalArgumentException.class, () -> parse(wrongLimit));
    }
}
