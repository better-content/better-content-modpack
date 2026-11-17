package com.bettercontent.playtestsupport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;

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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Early full-chunk generation facts, never parent absence or natural origin certification. */
final class BlockBirthTrace {
    static final String EVENT = "bc.playtest_block_birth_event.v1";
    static final String PREFIX = "bc.playtest_block_birth_prefix.v1";
    static final String STATUS = "bc.playtest_block_birth_status.v2";
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    final BlockBirthScope scope;
    final BlockMutationTrace mutations;
    final BlockMutationOwnership ownership;
    private final FileChannel channel;
    private final Path terminal;
    private final String relative;
    private final ByteArrayOutputStream written = new ByteArrayOutputStream();
    private final Map<BlockBirthScope.ChunkKey, LevelChunk> chunks = new HashMap<>();
    private final Map<LevelChunk, String> identities = new IdentityHashMap<>();
    private final Map<BlockBirthScope.ChunkKey, Boolean> loaded = new HashMap<>();
    private final Map<BlockBirthScope.ChunkKey, Boolean> postprocessed = new HashMap<>();
    private int records;
    private boolean overflow, ioFailure, scopeFailure, closed;
    private String reason;
    BlockBirthTrace(BlockBirthScope scope, Path mailbox) throws IOException {
        this.scope = scope;
        mutations = new BlockMutationTrace(BlockMutationScope.fromBirth(scope), mailbox);
        ownership = new BlockMutationOwnership(mutations);
        Path folder = mailbox.resolve("births");
        if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(folder);
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(folder))
            throw new IOException("unsafe birth sidecar directory");
        relative = "births/" + scope.scopeId + ".jsonl";
        terminal = folder.resolve(scope.scopeId + ".status.json");
        if (Files.exists(terminal, LinkOption.NOFOLLOW_LINKS)) throw new IOException("stale birth terminal");
        channel = FileChannel.open(mailbox.resolve(relative), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    }
    private BlockBirthScope.ChunkKey key(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) return null;
        return new BlockBirthScope.ChunkKey(level.dimension().location().toString(), chunk.getPos().x, chunk.getPos().z);
    }
    synchronized void born(LevelChunk chunk, ServerLevel level, ProtoChunk proto, long tick) {
        BlockBirthScope.ChunkKey key = key(chunk);
        if (key == null || !scope.chunkSet.contains(key)) return;
        if (chunk.getLevel() != level || proto instanceof ImposterProtoChunk ||
                !level.getServer().isSameThread() || proto.getPos().x != key.chunkX() ||
                proto.getPos().z != key.chunkZ() || !proto.getStatus().isOrAfter(ChunkStatus.SPAWN) ||
                chunks.containsKey(key) || closed) {
            fail("watched_birth_not_single_server_thread_new_full_proto_promotion"); return;
        }
        // LevelChunk(ServerLevel,ProtoChunk,PostLoadProcessor) RETURN occurs before runPostLoad and Forge Load.
        // Own the same section identities immediately, not at a later ChunkEvent.Load.
        try {
            chunks.put(key, chunk);
            String identity = scope.scopeId + ":birth-" + chunks.size();
            identities.put(chunk, identity);
            ownership.birth(chunk);
            for (BlockMutationScope.Position pos : scope.positions) {
                if (!key.equals(new BlockBirthScope.ChunkKey(pos.dimension(), pos.x() >> 4, pos.z() >> 4))) continue;
                BlockState actual = chunk.getBlockState(new net.minecraft.core.BlockPos(pos.x(), pos.y(), pos.z()));
                JsonObject row = event("full_chunk_constructor_return_baseline", key, identity, tick);
                row.addProperty("x", pos.x()); row.addProperty("y", pos.y()); row.addProperty("z", pos.z());
                row.add("block_state", BlockMutationTrace.state(actual));
                row.addProperty("proto_status", proto.getStatus().toString());
                row.addProperty("new_chunk_event_observed", false);
                append(row);
            }
        } catch (Throwable failure) { fail("birth_capture_failed:" + failure.getClass().getSimpleName()); }
    }
    synchronized void load(LevelChunk chunk, boolean newChunk, long tick) {
        BlockBirthScope.ChunkKey key = key(chunk);
        if (key == null || !scope.chunkSet.contains(key)) return;
        if (chunks.get(key) != chunk || !newChunk || loaded.containsKey(key)) {
            fail("birth_load_without_matching_constructor_or_new_chunk_diagnostic"); return;
        }
        try {
            ownership.promoted(chunk);
            JsonObject row = event("matching_full_chunk_load", key, identities.get(chunk), tick);
            row.addProperty("new_chunk_event_observed", newChunk); append(row);
            loaded.put(key, true);
        } catch (Throwable failure) { fail("birth_load_capture_failed:" + failure.getClass().getSimpleName()); }
    }
    synchronized void postprocess(LevelChunk chunk, long tick) {
        BlockBirthScope.ChunkKey key = key(chunk);
        if (key == null || !scope.chunkSet.contains(key)) return;
        if (chunks.get(key) != chunk || !loaded.containsKey(key) || postprocessed.containsKey(key)) {
            fail("birth_postprocess_without_matching_single_loaded_chunk"); return;
        }
        try {
            ownership.audit();
            JsonObject row = event("postprocess_generation_return", key, identities.get(chunk), tick);
            append(row); postprocessed.put(key, true);
        } catch (Throwable failure) { fail("birth_postprocess_capture_failed:" + failure.getClass().getSimpleName()); }
    }
    synchronized void unload(LevelChunk chunk) {
        BlockBirthScope.ChunkKey key = key(chunk);
        if (key != null && scope.chunkSet.contains(key) &&
                (!ownership.expectedShutdownUnload(chunk) || !ready()))
            fail("birth_chunk_unloaded_outside_complete_genuine_shutdown_drain");
        ownership.unloaded(chunk); // retains exact identity ONLY on bound shutdown drain, otherwise fails/drops
    }
    private JsonObject event(String phase, BlockBirthScope.ChunkKey key, String identity, long tick) {
        JsonObject row = new JsonObject();
        row.addProperty("schema", EVENT); row.addProperty("scope_id", scope.scopeId);
        row.addProperty("index", records + 1); row.addProperty("phase", phase);
        row.addProperty("tick", tick); row.addProperty("monotonic_nanos", System.nanoTime());
        row.addProperty("dimension", key.dimension()); row.addProperty("chunk_x", key.chunkX()); row.addProperty("chunk_z", key.chunkZ());
        row.addProperty("birth_identity", identity);
        row.addProperty("preceding_mutation_bytes", mutations.byteCount());
        row.addProperty("preceding_mutation_records", mutations.recordCount());
        row.addProperty("preceding_mutation_sha256", mutations.digestSha256());
        row.addProperty("prior_birth_prefix_sha256", BlockMutationScope.sha256(written.toByteArray()));
        return row;
    }
    private void append(JsonObject row) {
        if (closed || ioFailure || overflow) return;
        byte[] bytes = (JSON.toJson(row) + "\n").getBytes(StandardCharsets.UTF_8);
        if (records >= BlockMutationScope.MAX_EVENTS || bytes.length > 65_536 ||
                (long) written.size() + bytes.length > BlockMutationScope.MAX_BYTES) {
            overflow = true; fail("birth_event_or_byte_cap_exceeded"); return;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            written.writeBytes(bytes); records++;
        } catch (IOException failure) { ioFailure = true; fail("birth_append_failed:" + failure.getClass().getSimpleName()); }
    }
    synchronized void fail(String issue) {
        scopeFailure = true;
        if (reason == null) reason = issue;
        mutations.failedScope("birth_" + issue);
    }
    private boolean ready() {
        return chunks.size() == scope.chunkKeys.size() && loaded.size() == scope.chunkKeys.size() &&
                postprocessed.size() == scope.chunkKeys.size();
    }
    private JsonObject pointer() {
        JsonObject row = new JsonObject(), lifecycle = new JsonObject();
        row.addProperty("schema", PREFIX); row.addProperty("scope_id", scope.scopeId);
        row.addProperty("scope_file_sha256", scope.configSha256);
        lifecycle.addProperty("campaign_id", scope.campaignId); lifecycle.addProperty("lineage_id", scope.lineageId);
        lifecycle.addProperty("epoch_id", scope.epochId); lifecycle.addProperty("native_session_id", scope.nativeSessionId);
        lifecycle.addProperty("purpose", scope.purpose); lifecycle.addProperty("source", "controller_prelaunch");
        row.add("lifecycle_context", lifecycle);
        row.addProperty("source_checkpoint_id", scope.sourceCheckpointId);
        row.addProperty("source_checkpoint_sha256", scope.sourceCheckpointSha256);
        row.addProperty("parent_absence_sidecar_sha256", scope.parentAbsenceSidecarSha256);
        row.addProperty("path", relative); row.addProperty("bytes", written.size());
        row.addProperty("sha256", BlockMutationScope.sha256(written.toByteArray()));
        row.addProperty("records", records); row.addProperty("expected_birth_chunks", scope.chunkKeys.size());
        row.addProperty("observed_constructor_chunks", chunks.size());
        row.addProperty("matching_new_chunk_load_chunks", loaded.size());
        row.addProperty("postprocessed_chunks", postprocessed.size());
        row.addProperty("overflow", overflow); row.addProperty("io_failure", ioFailure);
        row.addProperty("scope_failure", scopeFailure || !ready());
        if (reason != null) row.addProperty("failure_reason", reason);
        else if (!ready()) row.addProperty("failure_reason", "birth_constructor_load_or_postprocess_not_yet_complete");
        row.addProperty("birth_observer_path", "LevelChunk(ServerLevel,ProtoChunk,PostLoadProcessor).RETURN_before_runPostLoad_then_ChunkEvent.Load_then_LevelChunk.postProcessGeneration.RETURN");
        row.addProperty("adapter_id", "forge_1.20.1-47.4.22_full_chunk_birth_v1");
        row.addProperty("parent_absence_proven_by_native", false);
        row.addProperty("natural_origin_proven_by_native", false);
        row.addProperty("universal_mutation_coverage_proven_by_native", false);
        return row;
    }
    synchronized JsonObject beforeAction() throws IOException {
        // Incomplete EARLY action prefixes cannot qualify, but a harmless pre-birth read must not
        // poison later fully observed births in the same predeclared epoch.
        if (ready()) ownership.audit();
        try { channel.force(true); }
        catch (IOException error) { ioFailure = true; fail("birth_prefix_force_failed"); }
        JsonObject row = pointer();
        row.add("postbirth_mutation_pre_action_prefix", mutations.preActionPrefix());
        return row;
    }
    synchronized void closeAtStop(long tick) throws IOException {
        if (closed) return;
        JsonObject shutdownDrain = ownership.shutdownStatus(tick);
        mutations.shutdownDrainStatus(shutdownDrain);
        JsonObject row = beforeAction();
        closed = true;
        try { channel.force(true); } catch (IOException error) { ioFailure = true; fail("birth_stop_force_failed"); }
        try { channel.close(); } catch (IOException error) { ioFailure = true; fail("birth_stop_close_failed"); }
        // Nested mutation stop status independently seals the exact same mutation byte stream.
        mutations.closeAtStop(tick);
        row = pointer(); row.addProperty("schema", STATUS);
        row.add("shutdown_drain", shutdownDrain);
        row.add("postbirth_mutation_final_prefix", mutations.preClosedPointer());
        row.addProperty("stop_tick", tick); row.addProperty("closed_monotonic_nanos", System.nanoTime());
        Files.write(terminal, (JSON.toJson(row) + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    }
}
