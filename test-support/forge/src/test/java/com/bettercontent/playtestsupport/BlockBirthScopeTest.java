package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Source-only pure immutable birth input parser checks; UNRUN. */
final class BlockBirthScopeTest {
    private static final String SESSION = "epoch-00000000-0000-4000-8000-000000000003";
    private static JsonObject scope() {
        JsonObject row = new JsonObject();
        row.addProperty("schema", BlockBirthScope.SCHEMA);
        row.addProperty("scope_id", "scope-00000000-0000-4000-8000-000000000001");
        row.addProperty("campaign_id", "campaign-00000000-0000-4000-8000-000000000002");
        row.addProperty("lineage_id", "00000000-0000-4000-8000-000000000004");
        row.addProperty("epoch_id", "epoch-00001"); row.addProperty("native_session_id", SESSION);
        row.addProperty("purpose", "qualification"); row.addProperty("source", "controller_prelaunch");
        row.addProperty("source_checkpoint_id", "parent-1");
        row.addProperty("source_checkpoint_sha256", "a".repeat(64));
        row.addProperty("parent_absence_sidecar_sha256", "b".repeat(64));
        JsonObject chunk = new JsonObject(); chunk.addProperty("dimension", "minecraft:overworld");
        chunk.addProperty("chunk_x", 0); chunk.addProperty("chunk_z", 0);
        JsonArray chunks = new JsonArray(); chunks.add(chunk); row.add("chunk_keys", chunks);
        JsonObject pos = new JsonObject(); pos.addProperty("dimension", "minecraft:overworld");
        pos.addProperty("x", 2); pos.addProperty("y", 64); pos.addProperty("z", 3);
        JsonArray positions = new JsonArray(); positions.add(pos); row.add("positions", positions);
        row.addProperty("max_events", 100_000); row.addProperty("max_bytes", 16_777_216);
        return row;
    }
    private static BlockBirthScope parse(String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return BlockBirthScope.parse(bytes, BlockMutationScope.sha256(bytes), SESSION,
                Path.of("/home/dev/birth-scope-review.json"));
    }
    @Test void exactParentAbsentWatchlistAndVirtualMutationScopeAreSeparate() {
        BlockBirthScope birth = parse(scope().toString());
        assertEquals(1, birth.chunkKeys.size()); assertEquals(1, birth.positions.size());
        assertEquals("b".repeat(64), birth.parentAbsenceSidecarSha256);
        BlockMutationScope related = BlockMutationScope.fromBirth(birth);
        assertEquals(birth.scopeId, related.scopeId);
        assertEquals(birth.configSha256, related.configSha256);
        assertEquals(birth.positions, related.positions);
    }
    @Test void rejectsUnsortedAliasesUnreviewedChunksAndForgedLifecycle() {
        JsonObject row = scope();
        row.getAsJsonArray("positions").get(0).getAsJsonObject().addProperty("x", 17);
        String wrongChunk = row.toString();
        assertThrows(IllegalArgumentException.class, () -> parse(wrongChunk));
        row = scope();
        row.getAsJsonArray("positions").add(row.getAsJsonArray("positions").get(0).deepCopy());
        String duplicate = row.toString();
        assertThrows(IllegalArgumentException.class, () -> parse(duplicate));
        row = scope(); row.addProperty("parent_absence_sidecar_sha256", "x".repeat(64));
        String missingParent = row.toString();
        assertThrows(IllegalArgumentException.class, () -> parse(missingParent));
        row = scope(); row.addProperty("source", "generated_by_native");
        String madeUpSource = row.toString();
        assertThrows(IllegalArgumentException.class, () -> parse(madeUpSource));
        String repeatedJsonKey = scope().toString().replace("\"max_events\":100000", "\"max_events\":100000,\"max_events\":100000");
        assertThrows(IllegalArgumentException.class, () -> parse(repeatedJsonKey));
    }
    @Test void rejectsWrongHashOrSessionEvenWithValidJson() {
        byte[] bytes = scope().toString().getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> BlockBirthScope.parse(bytes,
                "0".repeat(64), SESSION, Path.of("/home/dev/birth-scope-review.json")));
        assertThrows(IllegalArgumentException.class, () -> BlockBirthScope.parse(bytes,
                BlockMutationScope.sha256(bytes), "epoch-00000000-0000-4000-8000-000000000005",
                Path.of("/home/dev/birth-scope-review.json")));
    }
}
