package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** UNRUN pure trace-bookkeeping regression: never calls a game getter, world or fixture. */
final class BlockMutationNegativeSecondaryAccountingTest {
    @TempDir Path root;
    private static final String SESSION = "epoch-123e4567-e89b-12d3-a456-426614174000";
    private static final String SCOPE = "scope-123e4567-e89b-12d3-a456-426614174001";

    private BlockMutationTrace trace(String purpose, String directory) throws Exception {
        JsonObject config = new JsonObject();
        config.addProperty("schema", BlockMutationScope.SCHEMA); config.addProperty("scope_id", SCOPE);
        config.addProperty("campaign_id", "campaign-123e4567-e89b-12d3-a456-426614174002");
        config.addProperty("lineage_id", "123e4567-e89b-12d3-a456-426614174003");
        config.addProperty("epoch_id", "epoch-00001"); config.addProperty("native_session_id", SESSION);
        config.addProperty("purpose", purpose); config.addProperty("source", "controller_prelaunch");
        config.addProperty("source_checkpoint_id", "pristine-origin");
        config.addProperty("source_checkpoint_sha256", "a".repeat(64));
        config.addProperty("max_events", BlockMutationScope.MAX_EVENTS);
        config.addProperty("max_bytes", BlockMutationScope.MAX_BYTES);
        JsonObject pos = new JsonObject(); pos.addProperty("dimension", "minecraft:overworld");
        pos.addProperty("x", 0); pos.addProperty("y", 65); pos.addProperty("z", 0);
        JsonArray positions = new JsonArray(); positions.add(pos); config.add("positions", positions);
        byte[] source = config.toString().getBytes(StandardCharsets.UTF_8);
        Path mailbox = Files.createDirectory(root.resolve(directory));
        return new BlockMutationTrace(BlockMutationScope.parse(source, BlockMutationScope.sha256(source),
                SESSION, mailbox.resolve("scope.json")), mailbox);
    }
    private JsonObject stop(BlockMutationTrace trace, String directory) throws Exception {
        trace.shutdownDrainStatus(new JsonObject()); // bookkeeping-only, NOT a genuine shutdown-drain witness
        trace.closeAtStop(1);
        return JsonParser.parseString(Files.readString(root.resolve(directory).resolve("mutations")
                .resolve(SCOPE + ".status.json"))).getAsJsonObject();
    }
    @Test void firstOriginalExposureOnlyHasNoSecondaryAndV4() throws Exception {
        BlockMutationTrace trace = trace("diagnostic", "first");
        trace.armExpectedDiagnosticExposure("mutable_state_container_exposed");
        trace.failedScope("mutable_state_container_exposed"); // simulates one delivered observer
        JsonObject result = stop(trace, "first");
        assertEquals("bc.playtest_block_mutation_status.v4", result.get("schema").getAsString());
        assertEquals("mutable_state_container_exposed", result.get("failure_reason").getAsString());
        assertFalse(result.get("additional_observer_failure").getAsBoolean());
        assertEquals(0, result.get("records").getAsLong());
    }
    @Test void secondIdenticalExposureIsStickySecondaryButPrimaryRemainsFirst() throws Exception {
        BlockMutationTrace trace = trace("diagnostic", "repeated");
        trace.armExpectedDiagnosticExposure("mutable_section_array_exposed");
        trace.failedScope("mutable_section_array_exposed");
        trace.failedScope("mutable_section_array_exposed"); // a second REAL callback would be rejected
        JsonObject result = stop(trace, "repeated");
        assertEquals("bc.playtest_block_mutation_status.v4", result.get("schema").getAsString());
        assertEquals("mutable_section_array_exposed", result.get("failure_reason").getAsString());
        assertTrue(result.get("additional_observer_failure").getAsBoolean());
    }
    @Test void laterOwnerUnloadFailureIsStickySecondaryDespiteExpectedFirstReason() throws Exception {
        BlockMutationTrace trace = trace("diagnostic", "unload");
        trace.armExpectedDiagnosticExposure("mutable_state_container_exposed");
        trace.failedScope("mutable_state_container_exposed");
        trace.failedScope("watched_chunk_unloaded_during_scope");
        JsonObject result = stop(trace, "unload");
        assertEquals("mutable_state_container_exposed", result.get("failure_reason").getAsString());
        assertTrue(result.get("additional_observer_failure").getAsBoolean());
    }
    @Test void ordinaryScopeStillUsesV3WithoutNegativeSecondaryField() throws Exception {
        BlockMutationTrace trace = trace("qualification", "ordinary");
        JsonObject result = stop(trace, "ordinary");
        assertEquals("bc.playtest_block_mutation_status.v3", result.get("schema").getAsString());
        assertFalse(result.has("additional_observer_failure"));
    }
}
