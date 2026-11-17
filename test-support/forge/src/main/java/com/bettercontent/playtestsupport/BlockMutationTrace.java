package com.bettercontent.playtestsupport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Append-only bounded scoped palette transitions; NOT proof of universal block mutation coverage. */
final class BlockMutationTrace {
    static final String PREFIX_SCHEMA = "bc.playtest_block_mutation_prefix.v2";
    static final String EVENT_SCHEMA = "bc.playtest_block_mutation_event.v2";
    static final String STATUS_SCHEMA = "bc.playtest_block_mutation_status.v3";
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    final BlockMutationScope scope;
    final String relative;
    private final Path terminal;
    private final FileChannel channel;
    private final ByteArrayOutputStream written = new ByteArrayOutputStream();
    private final MessageDigest digest;
    // Original callback state may be retained in memory even when the append FAILED. Never use
    // this map as committed raw-event evidence: written/digest/records are the sole byte ledger.
    private final Map<BlockMutationScope.Position, BlockState> lastObservedStates = new HashMap<>();
    private long records;
    private boolean overflow, ioFailure, scopeFailure, closed;
    private boolean diagnosticAppendFailureControl, additionalObserverFailure;
    private String armedDiagnosticExposureReason;
    private boolean firstDiagnosticExposureObserved;
    private String failureReason;
    private String ioFailedObservedPath; // Captured only from the original production append IOException path.
    private JsonObject shutdownDrainStatus;

    BlockMutationTrace(BlockMutationScope scope, Path mailbox) throws IOException {
        this.scope = scope;
        if (!Files.isDirectory(mailbox, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(mailbox))
            throw new IOException("mutation mailbox must exist as ordinary directory before world loading");
        Path folder = mailbox.resolve("mutations");
        if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(folder);
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(folder))
            throw new IOException("unsafe block mutation output directory");
        relative = "mutations/" + scope.scopeId + ".jsonl";
        Path output = mailbox.resolve(relative);
        terminal = mailbox.resolve("mutations/" + scope.scopeId + ".status.json");
        if (Files.exists(terminal, LinkOption.NOFOLLOW_LINKS)) throw new IOException("stale mutation terminal");
        channel = FileChannel.open(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException unavailable) { throw new IOException("SHA-256 unavailable", unavailable); }
    }
    /** Actual palette old/new states must differ; even a later restoration is another transition. */
    static boolean changed(BlockState old, BlockState actual) { return old != null && actual != null && !old.equals(actual); }
    synchronized void initialState(BlockMutationScope.Position pos, BlockState old) {
        if (pos != null && old != null) lastObservedStates.putIfAbsent(pos, old);
    }
    /** Arm only immediately before the verified same-owner original diagnostic getter call. */
    synchronized void armExpectedDiagnosticExposure(String reason) {
        if (!"diagnostic".equals(scope.purpose) || closed || scopeFailure || ioFailure || overflow ||
                diagnosticAppendFailureControl || armedDiagnosticExposureReason != null ||
                records != 0 || written.size() != 0 ||
                !("mutable_state_container_exposed".equals(reason) ||
                        "mutable_section_array_exposed".equals(reason)))
            throw new IllegalStateException("expected diagnostic exposure must arm on clean owned zero-row scope");
        armedDiagnosticExposureReason = reason; // no synthetic failure or raw event
    }
    synchronized boolean matchesObservedState(BlockMutationScope.Position pos, BlockState actual) {
        BlockState recorded = lastObservedStates.get(pos);
        return recorded != null && recorded.equals(actual);
    }
    static boolean fits(int bytes, int rowBytes, long records) {
        return rowBytes > 0 && records < BlockMutationScope.MAX_EVENTS &&
                (long) bytes + rowBytes <= BlockMutationScope.MAX_BYTES;
    }
    synchronized void record(BlockMutationScope.Position pos, BlockState old, BlockState actual, long tick,
                             String observedPath) {
        if (!observedPath.equals("PalettedContainer.getAndSet_private_index_return") &&
                !observedPath.equals("PalettedContainer.set_private_index_return") &&
                !observedPath.equals("PalettedContainer.read_full_replacement") &&
                !observedPath.equals("LevelChunkSection_palette_fallback_incomplete"))
            throw new IllegalArgumentException("unreviewed mutation path");
        if (closed || !scope.positionSet.contains(pos)) return;
        // Even a no-op original watched callback AFTER the first expected alias exposure
        // is unexpected extra activity in this isolated read-only negative scope.
        if (firstDiagnosticExposureObserved) additionalObserverFailure = true;
        if (!changed(old, actual)) return;
        if (ioFailure) {
            if (diagnosticAppendFailureControl) {
                additionalObserverFailure = true; // never silently accept a second original transition
                failedScope("additional_changed_original_observer_after_failed_append");
            }
            return;
        }
        if (overflow) return;
        JsonObject row = new JsonObject();
        row.addProperty("schema", EVENT_SCHEMA);
        row.addProperty("scope_id", scope.scopeId);
        row.addProperty("index", records + 1);
        row.addProperty("tick", tick); row.addProperty("monotonic_nanos", System.nanoTime());
        row.addProperty("dimension", pos.dimension());
        row.addProperty("x", pos.x()); row.addProperty("y", pos.y()); row.addProperty("z", pos.z());
        row.addProperty("observed_path", observedPath);
        row.add("old_state", state(old)); row.add("new_state", state(actual));
        row.addProperty("prior_prefix_sha256", currentSha256());
        byte[] bytes = (JSON.toJson(row) + "\n").getBytes(StandardCharsets.UTF_8);
        if (!fits(written.size(), bytes.length, records)) {
            overflow = true;
            if (failureReason == null) failureReason = "mutation_event_or_byte_cap_exceeded";
            return;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            written.writeBytes(bytes); digest.update(bytes); records++;
            lastObservedStates.put(pos, actual);
        } catch (IOException error) {
            ioFailure = true; ioFailedObservedPath = observedPath;
            // Exact FIRST original observer return is a true in-memory physical observation,
            // even though its append failed. Do not increment bytes/records or digest.
            lastObservedStates.put(pos, actual);
            if (failureReason == null) failureReason = "mutation_append_failed:" + error.getClass().getSimpleName();
        }
    }
    synchronized void failedScope(String reason) {
        if (!firstDiagnosticExposureObserved && armedDiagnosticExposureReason != null &&
                armedDiagnosticExposureReason.equals(reason) && !scopeFailure && !ioFailure && !overflow &&
                failureReason == null) {
            firstDiagnosticExposureObserved = true; // only the FIRST actual matching callback is exempt
        } else if (firstDiagnosticExposureObserved || (diagnosticAppendFailureControl && ioFailure)) {
            additionalObserverFailure = true; // includes repeated SAME exposure and unrelated failures
        }
        scopeFailure = true;
        if (failureReason == null) failureReason = reason;
    }
    synchronized int byteCount() { return written.size(); }
    synchronized long recordCount() { return records; }
    synchronized String ioFailedObservedPath() { return ioFailedObservedPath; }
    synchronized String digestSha256() { return currentSha256(); }
    static JsonObject state(BlockState block) {
        JsonObject row = new JsonObject(), properties = new JsonObject();
        row.addProperty("id", ForgeRegistries.BLOCKS.getKey(block.getBlock()).toString());
        block.getProperties().stream().sorted(Comparator.comparing(Property::getName))
                .forEach(property -> properties.addProperty(property.getName(), block.getValue(property).toString()));
        row.add("properties", properties); return row;
    }
    private String currentSha256() {
        try {
            byte[] hash = ((MessageDigest) digest.clone()).digest();
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) builder.append(Character.forDigit((b >>> 4) & 0xf, 16))
                    .append(Character.forDigit(b & 0xf, 16));
            return builder.toString();
        } catch (CloneNotSupportedException unavailable) {
            return BlockMutationScope.sha256(written.toByteArray());
        }
    }
    /** Force the exact already-observed byte prefix before any action can change the world. */
    synchronized JsonObject preActionPrefix() throws IOException {
        if (closed) throw new IOException("mutation stream already closed");
        if (!ioFailure) {
            try { channel.force(true); }
            catch (IOException failure) { ioFailure = true; failureReason = "mutation_force_failed"; }
        }
        return pointer();
    }
    synchronized JsonObject preClosedPointer() { return pointer(); }
    /** Diagnostic-only: close ONLY this trace's channel; actual later append sets the IO failure, never this method. */
    synchronized void closeOwnedChannelForDiagnosticOnly() throws IOException {
        if (!"diagnostic".equals(scope.purpose) || closed || ioFailure || overflow || scopeFailure ||
                records != 0 || written.size() != 0 || !channel.isOpen())
            throw new IOException("owned diagnostic trace channel not clean before IO negative");
        channel.close();
        diagnosticAppendFailureControl = true; // action marker only; NO synthetic IO/scope flag
    }
    private JsonObject pointer() {
        JsonObject row = new JsonObject(), context = new JsonObject();
        row.addProperty("schema", PREFIX_SCHEMA);
        row.addProperty("scope_id", scope.scopeId);
        row.addProperty("scope_file_sha256", scope.configSha256);
        context.addProperty("campaign_id", scope.campaignId); context.addProperty("lineage_id", scope.lineageId);
        context.addProperty("epoch_id", scope.epochId); context.addProperty("native_session_id", scope.nativeSessionId);
        context.addProperty("purpose", scope.purpose); context.addProperty("source", "controller_prelaunch");
        row.add("lifecycle_context", context);
        row.addProperty("source_checkpoint_id", scope.sourceCheckpointId);
        row.addProperty("source_checkpoint_sha256", scope.sourceCheckpointSha256);
        row.addProperty("path", relative);
        row.addProperty("bytes", written.size());
        row.addProperty("sha256", currentSha256());
        row.addProperty("records", records);
        row.addProperty("overflow", overflow); row.addProperty("io_failure", ioFailure);
        row.addProperty("scope_failure", scopeFailure);
        if (failureReason != null) row.addProperty("failure_reason", failureReason);
        row.addProperty("supported_scope_schema", BlockMutationOwnership.PROFILE);
        row.addProperty("adapter_id", "forge_1.20.1-47.4.22_full_chunk_palette_v1");
        row.addProperty("supported_observed_path", "pinned_owned_full_chunk_private_palette_index_or_read");
        JsonArray targets = new JsonArray();
        targets.add("LevelChunk.setBlockState(BlockPos,BlockState,boolean)");
        targets.add("LevelChunkSection.setBlockState(int,int,int,BlockState,boolean)");
        targets.add("PalettedContainer.getAndSet(int,Object)/set(int,Object)/read(FriendlyByteBuf)");
        targets.add("ChunkAccess.getSections()/LevelChunkSection.getStates()/ChunkEvent.Load|Unload");
        row.add("pinned_observer_paths", targets);
        row.addProperty("reviewed_candidate_provider_pin_verified_by_native", false);
        JsonArray unsupported = new JsonArray();
        unsupported.add("unknown_preownership_exposure_or_direct_write_before_ChunkEvent_Load");
        unsupported.add("reflection_unsafe_private_PalettedContainer_data_or_section_array_mutation");
        unsupported.add("ProtoChunk_worldgen_and_chunk_birth_block_baselines");
        unsupported.add("block_entity_NBT_and_external_provider_state");
        unsupported.add("offline_or_before_scope_interval_without_independent_checkpoint_link");
        unsupported.add("unreviewed_provider_or_mutable_reference_exposure_blocks_supported_scope");
        row.add("unsupported_paths", unsupported);
        row.addProperty("universal_mutation_coverage_proven_by_native", false);
        row.addProperty("natural_origin_proven_by_native", false);
        return row;
    }
    /** Exact independently observed original shutdown boundary; do not turn a missing callback into success. */
    synchronized void shutdownDrainStatus(JsonObject actual) {
        if (actual == null || shutdownDrainStatus != null || closed) {
            failedScope("missing_duplicate_or_late_shutdown_drain_status"); return;
        }
        shutdownDrainStatus = actual.deepCopy();
    }
    /** Terminal status is separate from any pre-action prefix, so action-time evidence cannot be rewritten. */
    synchronized void closeAtStop(long tick) throws IOException {
        if (closed) return;
        closed = true;
        try { channel.force(true); }
        catch (IOException error) {
            // Exactly this diagnostic deliberately closed its own channel before the genuine
            // append; forcing that already-closed channel is an EXPECTED ClosedChannelException.
            boolean expectedClosedForce = diagnosticAppendFailureControl && ioFailure &&
                    error instanceof java.nio.channels.ClosedChannelException && !channel.isOpen() &&
                    "mutation_append_failed:ClosedChannelException".equals(failureReason);
            if (!expectedClosedForce) {
                if (diagnosticAppendFailureControl || armedDiagnosticExposureReason != null)
                    additionalObserverFailure = true;
                scopeFailure = true;
            }
            ioFailure = true; if (failureReason == null) failureReason = "mutation_stop_force_failed";
        }
        try { channel.close(); }
        catch (IOException error) {
            if (diagnosticAppendFailureControl || armedDiagnosticExposureReason != null)
                additionalObserverFailure = true;
            scopeFailure = true; ioFailure = true;
            if (failureReason == null) failureReason = "mutation_close_failed";
        }
        if (shutdownDrainStatus == null) failedScope("shutdown_drain_status_not_observed_before_stop");
        boolean diagnosticNegative = diagnosticAppendFailureControl || armedDiagnosticExposureReason != null;
        JsonObject row = pointer(); row.addProperty("schema", diagnosticNegative ?
                "bc.playtest_block_mutation_status.v4" : STATUS_SCHEMA);
        if (diagnosticNegative)
            row.addProperty("additional_observer_failure", additionalObserverFailure);
        row.add("shutdown_drain", shutdownDrainStatus == null ? com.google.gson.JsonNull.INSTANCE : shutdownDrainStatus.deepCopy());
        row.addProperty("stop_tick", tick);
        row.addProperty("closed_monotonic_nanos", System.nanoTime());
        byte[] bytes = (JSON.toJson(row) + "\n").getBytes(StandardCharsets.UTF_8);
        Files.write(terminal, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    }
}
