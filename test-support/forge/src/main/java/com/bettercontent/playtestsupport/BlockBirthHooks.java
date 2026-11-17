package com.bettercontent.playtestsupport;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.ProtoChunk;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Separate bounded early-birth palette path, independent of present-origin stage1 scopes. */
public final class BlockBirthHooks {
    private static volatile BlockBirthTrace birth;
    private record SetBefore(PalettedContainer<?> container, int index, BlockState old) {}
    private record ReadBefore(PalettedContainer<?> container, Map<BlockMutationScope.Position, BlockState> prior) {}
    private static final ThreadLocal<Deque<SetBefore>> SETS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<ReadBefore>> READS = ThreadLocal.withInitial(ArrayDeque::new);
    private BlockBirthHooks() {}
    static void install(BlockBirthTrace source) {
        if (birth != null) throw new IllegalStateException("birth witness installed twice");
        birth = source;
    }
    static void clear() { birth = null; SETS.remove(); READS.remove(); }
    public static void fullChunkBorn(LevelChunk chunk, ServerLevel level, ProtoChunk proto) {
        BlockBirthTrace b = birth;
        if (b == null) return;
        try { b.born(chunk, level, proto, level.getGameTime()); }
        catch (Throwable error) { b.fail("full_chunk_birth_hook_failed:" + error.getClass().getSimpleName()); }
    }
    public static void postprocessed(LevelChunk chunk) {
        BlockBirthTrace b = birth;
        if (b == null || !(chunk.getLevel() instanceof ServerLevel level)) return;
        try { b.postprocess(chunk, level.getGameTime()); }
        catch (Throwable error) { b.fail("postprocess_hook_failed:" + error.getClass().getSimpleName()); }
    }
    private static BlockMutationOwnership.Owner owner(BlockBirthTrace b, PalettedContainer<?> container, int index) {
        BlockMutationOwnership.Owner owner = b.ownership.forContainer(container);
        return owner == null || owner.atIndex(index) == null ? null : owner;
    }
    public static void getAndSet(PalettedContainer<?> container, int index, Object prior) {
        BlockBirthTrace b = birth;
        if (b == null) return;
        try { record(b, container, index, prior, "PalettedContainer.getAndSet_private_index_return"); }
        catch (Throwable error) { b.fail("birth_palette_getandset_failed:" + error.getClass().getSimpleName()); }
    }
    public static void setBefore(PalettedContainer<?> container, int index) {
        BlockBirthTrace b = birth;
        if (b == null) return;
        try {
            BlockMutationOwnership.Owner owner = owner(b, container, index);
            Object old = owner == null ? null : container.get(BlockMutationOwnership.x(index),
                    BlockMutationOwnership.y(index), BlockMutationOwnership.z(index));
            SETS.get().push(new SetBefore(container, index, old instanceof BlockState state ? state : null));
        } catch (Throwable error) { b.fail("birth_palette_set_head_failed:" + error.getClass().getSimpleName()); }
    }
    public static void setAfter(PalettedContainer<?> container, int index) {
        BlockBirthTrace b = birth;
        if (b == null) return;
        Deque<SetBefore> stack = SETS.get();
        if (stack.isEmpty()) { b.fail("birth_palette_set_missing_head"); return; }
        try {
            SetBefore prior = stack.pop();
            if (prior.container != container || prior.index != index) {
                b.fail("birth_palette_set_unbalanced"); stack.clear(); return;
            }
            record(b, container, index, prior.old, "PalettedContainer.set_private_index_return");
        } catch (Throwable error) { b.fail("birth_palette_set_after_failed:" + error.getClass().getSimpleName()); }
        finally { if (stack.isEmpty()) SETS.remove(); }
    }
    private static void record(BlockBirthTrace b, PalettedContainer<?> container, int index,
                               Object old, String path) {
        BlockMutationOwnership.Owner owner = owner(b, container, index);
        if (owner == null) return;
        if (!(old instanceof BlockState prior)) { b.fail("birth_palette_prior_not_block_state"); return; }
        if (!b.ownership.active(owner)) return;
        Object now = container.get(BlockMutationOwnership.x(index), BlockMutationOwnership.y(index),
                BlockMutationOwnership.z(index));
        if (!(now instanceof BlockState actual)) { b.fail("birth_palette_result_not_block_state"); return; }
        b.mutations.record(owner.atIndex(index), prior, actual, owner.chunk.getLevel().getGameTime(), path);
    }
    public static void readBefore(PalettedContainer<?> container) {
        BlockBirthTrace b = birth;
        if (b == null) return;
        try {
            BlockMutationOwnership.Owner owner = b.ownership.forContainer(container);
            Map<BlockMutationScope.Position, BlockState> prior = new LinkedHashMap<>();
            if (owner != null && b.ownership.active(owner))
                for (BlockMutationScope.Position p : owner.positions)
                    prior.put(p, (BlockState) container.get(p.x() & 15, p.y() & 15, p.z() & 15));
            READS.get().push(new ReadBefore(container, prior));
        } catch (Throwable error) { b.fail("birth_palette_read_head_failed:" + error.getClass().getSimpleName()); }
    }
    public static void readAfter(PalettedContainer<?> container) {
        BlockBirthTrace b = birth;
        if (b == null) return;
        Deque<ReadBefore> stack = READS.get();
        if (stack.isEmpty()) { b.fail("birth_palette_read_missing_head"); return; }
        try {
            ReadBefore before = stack.pop();
            if (before.container != container) { b.fail("birth_palette_read_unbalanced"); stack.clear(); return; }
            BlockMutationOwnership.Owner owner = b.ownership.forContainer(container);
            if (owner != null && b.ownership.active(owner))
                for (var entry : before.prior.entrySet()) {
                    BlockMutationScope.Position p = entry.getKey();
                    b.mutations.record(p, entry.getValue(), (BlockState) container.get(p.x() & 15, p.y() & 15, p.z() & 15),
                            owner.chunk.getLevel().getGameTime(), "PalettedContainer.read_full_replacement");
                }
        } catch (Throwable error) { b.fail("birth_palette_read_after_failed:" + error.getClass().getSimpleName()); }
        finally { if (stack.isEmpty()) READS.remove(); }
    }
    public static void sectionWritten(LevelChunkSection section, int x, int y, int z, BlockState prior) {
        BlockBirthTrace b = birth;
        if (b == null) return;
        try {
            BlockMutationOwnership.Owner owner = b.ownership.forSection(section);
            if (owner == null) return;
            BlockMutationScope.Position pos = owner.atIndex(BlockMutationOwnership.index(x, y, z));
            if (pos == null) return;
            BlockState actual = section.getBlockState(x, y, z);
            if (!b.mutations.matchesObservedState(pos, actual)) {
                b.fail("birth_section_write_without_private_palette_event");
                b.mutations.record(pos, prior, actual, owner.chunk.getLevel().getGameTime(),
                        "LevelChunkSection_palette_fallback_incomplete");
            }
        } catch (Throwable error) { b.fail("birth_section_audit_failed:" + error.getClass().getSimpleName()); }
    }
    public static void chunkBoundary(LevelChunk chunk, BlockPos pos) {
        BlockBirthTrace b = birth;
        if (b == null || !(chunk.getLevel() instanceof ServerLevel level)) return;
        BlockMutationScope.Position p = new BlockMutationScope.Position(level.dimension().location().toString(),
                pos.getX(), pos.getY(), pos.getZ());
        if (!b.scope.positions.contains(p)) return;
        try {
            if (!b.ownership.watchedChunk(chunk) || !level.getServer().isSameThread() ||
                    !b.mutations.matchesObservedState(p, chunk.getBlockState(pos)))
                b.fail("birth_chunk_write_boundary_unowned_offthread_or_unobserved");
        } catch (Throwable error) { b.fail("birth_chunk_write_boundary_failed:" + error.getClass().getSimpleName()); }
    }
    public static void sectionsExposed(ChunkAccess chunk) {
        BlockBirthTrace b = birth;
        if (b == null || !(chunk instanceof LevelChunk full)) return;
        try {
            if (!b.ownership.watchedChunk(full) || b.ownership.internalRead()) return;
            if (ReadOnlyChunkSerializerProfile.readOnly("getSections")) {
                if (!(full.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread())
                    b.fail("birth_serializer_sections_read_off_server_thread");
                return;
            }
            b.ownership.exposedSections(full);
        }
        catch (Throwable error) { b.fail("birth_sections_exposure_check_failed:" + error.getClass().getSimpleName()); }
    }
    public static void statesExposed(LevelChunkSection section, PalettedContainer<?> states) {
        BlockBirthTrace b = birth;
        if (b == null) return;
        try {
            if (!b.ownership.watchedSection(section)) return;
            if (ReadOnlyChunkSerializerProfile.readOnly("getStates")) {
                var owner = b.ownership.forSection(section);
                if (owner.states != states) b.fail("birth_serializer_states_identity_drift");
                if (!(owner.chunk.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread())
                    b.fail("birth_serializer_states_read_off_server_thread");
                return;
            }
            b.ownership.exposedStates(section, states);
        } catch (Throwable error) { b.fail("birth_states_exposure_check_failed:" + error.getClass().getSimpleName()); }
    }
}
