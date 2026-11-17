package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Bounded watched full-chunk/section/container identities; no namespace-wide or mutable-reference trust. */
final class BlockMutationOwnership {
    static final String PROFILE = "bc.playtest_mutation_supported_full_chunk_palette.v1";
    record ChunkKey(String dimension, int x, int z) {}
    record SectionKey(ChunkKey chunk, int index) {}
    static final class Owner {
        final LevelChunk chunk;
        final LevelChunkSection section;
        final PalettedContainer<BlockState> states;
        final List<BlockMutationScope.Position> positions;
        final Map<Integer, BlockMutationScope.Position> positionsByIndex;
        final int sectionIndex;
        Owner(LevelChunk chunk, LevelChunkSection section, PalettedContainer<BlockState> states,
              List<BlockMutationScope.Position> positions, int sectionIndex) {
            this.chunk = chunk; this.section = section; this.states = states;
            this.positions = List.copyOf(positions); this.sectionIndex = sectionIndex;
            Map<Integer, BlockMutationScope.Position> indices = new HashMap<>();
            for (BlockMutationScope.Position p : positions) {
                BlockMutationScope.Position prior = indices.put(index(p.x() & 15, p.y() & 15, p.z() & 15), p);
                if (prior != null) throw new IllegalArgumentException("watched palette index duplicated");
            }
            this.positionsByIndex = Map.copyOf(indices);
        }
        BlockMutationScope.Position atIndex(int index) {
            return index < 0 || index >= 4096 ? null : positionsByIndex.get(index);
        }
    }
    private final Map<ChunkKey, Map<Integer, List<BlockMutationScope.Position>>> reviewed = new HashMap<>();
    private final IdentityHashMap<LevelChunk, List<Owner>> chunks = new IdentityHashMap<>();
    private final IdentityHashMap<LevelChunk, Boolean> pendingBirthPromotion = new IdentityHashMap<>();
    private final IdentityHashMap<LevelChunkSection, Owner> sections = new IdentityHashMap<>();
    private final IdentityHashMap<PalettedContainer<?>, Owner> containers = new IdentityHashMap<>();
    private final BlockMutationTrace trace;
    private final ThreadLocal<Boolean> internalRead = ThreadLocal.withInitial(() -> false);
    private int identityRows;
    private MinecraftServer shutdownServer;
    private boolean shutdownDrain, shutdownReturn, unexpectedDrainActivity;
    private long entryTick = -1, returnTick = -1;
    private int entryCallCount;
    private final IdentityHashMap<LevelChunk, Boolean> entryIdentities = new IdentityHashMap<>();
    private final IdentityHashMap<LevelChunk, Boolean> drainedIdentities = new IdentityHashMap<>();
    private final TreeSet<ChunkKey> drainedKeys = new TreeSet<>((a, b) -> {
        int d = a.dimension().compareTo(b.dimension());
        if (d != 0) return d;
        d = Integer.compare(a.x(), b.x());
        return d != 0 ? d : Integer.compare(a.z(), b.z());
    });

    BlockMutationOwnership(BlockMutationTrace trace) {
        this.trace = trace;
        for (BlockMutationScope.Position p : trace.scope.positions) {
            ChunkKey key = new ChunkKey(p.dimension(), p.x() >> 4, p.z() >> 4);
            reviewed.computeIfAbsent(key, ignored -> new HashMap<>())
                    .computeIfAbsent(Math.floorDiv(p.y(), 16), ignored -> new ArrayList<>()).add(p);
        }
    }
    boolean internalRead() { return internalRead.get(); }
    private void withInternalRead(Runnable action) {
        boolean prior = internalRead.get(); internalRead.set(true);
        try { action.run(); } finally { internalRead.set(prior); }
    }
    synchronized void loaded(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) return;
        ChunkKey key = new ChunkKey(level.dimension().location().toString(), chunk.getPos().x, chunk.getPos().z);
        Map<Integer, List<BlockMutationScope.Position>> region = reviewed.get(key);
        if (region == null) return;
        if (shutdownDrain) { unexpected("watched_chunk_load_during_shutdown_drain"); return; }
        if (chunks.containsKey(chunk)) { trace.failedScope("duplicate_relevant_chunk_load"); return; }
        List<Owner> owned = new ArrayList<>();
        withInternalRead(() -> {
            for (var entry : region.entrySet()) {
                int sectionIndex = chunk.getSectionIndex(entry.getValue().get(0).y());
                if (sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()) {
                    trace.failedScope("reviewed_section_outside_runtime_build_height"); continue;
                }
                LevelChunkSection section = chunk.getSection(sectionIndex);
                PalettedContainer<BlockState> states = section.getStates();
                if (++identityRows > BlockMutationScope.MAX_POSITIONS * 3 ||
                        sections.containsKey(section) || containers.containsKey(states)) {
                    trace.failedScope("watched_chunk_section_or_container_alias_or_cap"); continue;
                }
                Owner owner = new Owner(chunk, section, states, entry.getValue(), sectionIndex);
                owned.add(owner); sections.put(section, owner); containers.put(states, owner);
                for (BlockMutationScope.Position p : entry.getValue()) {
                    BlockState actual = section.getBlockState(p.x() & 15, p.y() & 15, p.z() & 15);
                    trace.initialState(p, actual);
                    if (!trace.matchesObservedState(p, actual)) trace.failedScope("reloaded_block_state_drift");
                }
            }
        });
        chunks.put(chunk, List.copyOf(owned));
    }
    synchronized void birth(LevelChunk chunk) {
        loaded(chunk); // The actual full-chunk ctor RETURN, before runPostLoad/ChunkEvent.Load.
        if (!chunks.containsKey(chunk)) trace.failedScope("birth_constructor_did_not_attach_owned_chunk");
        else pendingBirthPromotion.put(chunk, true);
    }
    synchronized void promoted(LevelChunk chunk) {
        if (pendingBirthPromotion.remove(chunk) == null || !chunks.containsKey(chunk))
            trace.failedScope("birth_load_does_not_match_preload_full_chunk_owner");
    }
    synchronized void unloaded(LevelChunk chunk) {
        if (shutdownDrain) {
            if (!(chunk.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
                unexpected("watched_shutdown_unload_off_server_thread"); return;
            }
            if (shutdownReturn) { unexpected("watched_chunk_unload_after_shutdown_return"); return; }
            if (entryIdentities.containsKey(chunk)) {
                if (drainedIdentities.put(chunk, true) != null) {
                    unexpected("duplicate_watched_shutdown_unload"); return;
                }
                ChunkKey key = key(chunk);
                if (key == null || !drainedKeys.add(key) || drainedKeys.size() > 64)
                    unexpected("watched_shutdown_unload_duplicate_key_or_cap");
                verifyRetained(chunk);
                return; // retain EXACT chunk/section/container identities through save and terminal seal
            }
            ChunkKey key = key(chunk);
            if (key != null && reviewed.containsKey(key)) unexpected("unowned_or_replaced_watched_chunk_unloaded_during_shutdown");
            return;
        }
        pendingBirthPromotion.remove(chunk);
        List<Owner> owners = chunks.remove(chunk);
        if (owners == null) return;
        trace.failedScope("relevant_chunk_unloaded_during_scope_interval");
        owners.forEach(o -> { sections.remove(o.section); containers.remove(o.states); });
    }
    synchronized Owner forContainer(PalettedContainer<?> container) { return containers.get(container); }
    synchronized boolean active(Owner owner) {
        if (shutdownDrain) { unexpected("watched_palette_write_during_or_after_shutdown_drain"); return false; }
        boolean prior = internalRead.get(); internalRead.set(true);
        try {
            boolean valid = owner.chunk.getSection(owner.sectionIndex) == owner.section &&
                    owner.section.getStates() == owner.states &&
                    owner.chunk.getLevel() instanceof ServerLevel level &&
                    level.getServer().isSameThread() &&
                    (pendingBirthPromotion.containsKey(owner.chunk) ||
                            level.getChunkSource().getChunkNow(owner.chunk.getPos().x, owner.chunk.getPos().z) == owner.chunk);
            if (!valid) trace.failedScope("watched_section_or_container_detached_during_write");
            return valid;
        } finally { internalRead.set(prior); }
    }
    synchronized Owner forSection(LevelChunkSection section) { return sections.get(section); }
    synchronized boolean watchedChunk(LevelChunk chunk) { return chunks.containsKey(chunk); }
    synchronized boolean watchedSection(LevelChunkSection section) { return sections.containsKey(section); }
    synchronized void exposedSections(LevelChunk chunk) {
        if (!internalRead() && chunks.containsKey(chunk)) trace.failedScope("mutable_section_array_exposed");
    }
    synchronized void exposedStates(LevelChunkSection section, PalettedContainer<?> states) {
        Owner owner = sections.get(section);
        if (owner != null && (!internalRead() || owner.states != states))
            trace.failedScope(owner.states == states ? "mutable_state_container_exposed" : "section_states_container_replaced");
    }
    synchronized void audit() {
        if (shutdownDrain) {
            for (LevelChunk chunk : entryIdentities.keySet()) verifyRetained(chunk);
            return; // cache membership intentionally ends at real Unload; exact owned identity remains mandatory
        }
        withInternalRead(() -> {
            for (var entry : chunks.entrySet()) {
                LevelChunk chunk = entry.getKey();
                if (!(chunk.getLevel() instanceof ServerLevel level) || pendingBirthPromotion.containsKey(chunk) ||
                        level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) != chunk) {
                    trace.failedScope("relevant_chunk_lifetime_not_loaded_at_action"); continue;
                }
                for (Owner owner : entry.getValue()) {
                    if (chunk.getSection(owner.sectionIndex) != owner.section ||
                            owner.section.getStates() != owner.states)
                        trace.failedScope("watched_section_or_container_identity_replaced");
                    for (BlockMutationScope.Position p : owner.positions) {
                        BlockState actual = owner.section.getBlockState(p.x() & 15, p.y() & 15, p.z() & 15);
                        if (!trace.matchesObservedState(p, actual)) trace.failedScope("watched_state_drift_at_action_boundary");
                    }
                }
            }
        });
    }
    private ChunkKey key(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) return null;
        return new ChunkKey(level.dimension().location().toString(), chunk.getPos().x, chunk.getPos().z);
    }
    private void unexpected(String reason) {
        unexpectedDrainActivity = true; trace.failedScope(reason);
    }
    private void verifyRetained(LevelChunk chunk) {
        List<Owner> owners = chunks.get(chunk);
        if (owners == null) { unexpected("shutdown_watched_chunk_identity_missing"); return; }
        withInternalRead(() -> {
            for (Owner owner : owners) {
                if (owner.chunk != chunk || chunk.getSection(owner.sectionIndex) != owner.section ||
                        owner.section.getStates() != owner.states) {
                    unexpected("shutdown_section_or_container_identity_drift"); continue;
                }
                for (BlockMutationScope.Position p : owner.positions)
                    if (!trace.matchesObservedState(p, owner.section.getBlockState(p.x() & 15, p.y() & 15, p.z() & 15)))
                        unexpected("shutdown_watched_state_changed_without_observed_transition");
            }
        });
    }
    /** Only the exact original MinecraftServer.stopServer ticket-removal callsite may enter drain. */
    synchronized void shutdownDrainEntry(MinecraftServer server, long tick) {
        if (!server.isSameThread() || (shutdownServer != null && shutdownServer != server) || shutdownReturn) {
            unexpected("shutdown_drain_entry_offthread_other_server_or_after_return"); return;
        }
        if (!shutdownDrain) {
            shutdownDrain = true; shutdownServer = server; entryTick = tick;
            if (chunks.isEmpty() || chunks.size() > 64 || !pendingBirthPromotion.isEmpty())
                unexpected("shutdown_drain_missing_or_oversized_watched_owner_or_unpromoted_birth");
            for (LevelChunk chunk : chunks.keySet()) {
                entryIdentities.put(chunk, true);
                verifyRetained(chunk);
            }
        }
        if (++entryCallCount > 1_000_000) unexpected("shutdown_drain_entry_loop_cap_exceeded");
    }
    synchronized void shutdownDrainReturn(MinecraftServer server, long tick) {
        if (!server.isSameThread() || !shutdownDrain || shutdownServer != server || shutdownReturn) {
            unexpected("shutdown_drain_return_unbalanced_or_offthread"); return;
        }
        shutdownReturn = true; returnTick = tick;
        for (LevelChunk chunk : entryIdentities.keySet()) verifyRetained(chunk);
        if (entryIdentities.size() != drainedIdentities.size() || drainedIdentities.size() != drainedKeys.size())
            unexpected("shutdown_drain_missing_or_duplicate_watched_unload");
    }
    synchronized void tickDuringDrain() {
        if (shutdownDrain) unexpected("server_tick_during_or_after_shutdown_drain");
    }
    synchronized boolean expectedShutdownUnload(LevelChunk chunk) {
        return shutdownDrain && !shutdownReturn && entryIdentities.containsKey(chunk);
    }
    synchronized JsonObject shutdownStatus(long stopTick) {
        if (!shutdownDrain || !shutdownReturn || entryTick < 0 || returnTick < entryTick || stopTick < returnTick ||
                entryIdentities.size() != drainedIdentities.size() || drainedIdentities.size() != drainedKeys.size())
            unexpected("shutdown_drain_missing_entry_return_or_identity_closure");
        for (LevelChunk chunk : entryIdentities.keySet()) verifyRetained(chunk);
        JsonObject row = new JsonObject(); row.addProperty("schema", "bc.playtest_mutation_shutdown_drain.v1");
        row.addProperty("entry_observed", shutdownDrain); row.addProperty("return_observed", shutdownReturn);
        row.addProperty("entry_tick", entryTick); row.addProperty("return_tick", returnTick);
        JsonArray unloads = new JsonArray();
        for (ChunkKey key : drainedKeys) {
            JsonObject observed = new JsonObject(); observed.addProperty("dimension", key.dimension());
            observed.addProperty("chunk_x", key.x()); observed.addProperty("chunk_z", key.z());
            unloads.add(observed);
        }
        row.addProperty("watched_unload_count", drainedIdentities.size()); row.add("watched_unloads", unloads);
        row.addProperty("retained_identity_count", chunks.size());
        row.addProperty("identity_and_state_match_at_close", !unexpectedDrainActivity && shutdownDrain && shutdownReturn &&
                !entryIdentities.isEmpty() && chunks.size() == drainedIdentities.size());
        row.addProperty("unexpected_post_drain_activity", unexpectedDrainActivity);
        return row;
    }
    static int index(int x, int y, int z) { return (y << 8) | (z << 4) | x; }
    static int x(int index) { return index & 15; }
    static int y(int index) { return (index >>> 8) & 15; }
    static int z(int index) { return (index >>> 4) & 15; }
}
