package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.common.util.FakePlayer;

import java.util.Set;

/** Disposable diagnostic ACTUAL public write/getter routes. No candidate/provider or observer success verdict. */
final class Stage1RealMutationControls {
    static final String POSITIVE = "block_mutation.control_positive_v1";
    static final String NEGATIVE_SECTIONS = "block_mutation.control_expose_sections_v1";
    static final String NEGATIVE_IO = "block_mutation.control_io_append_failure_v1";
    private static final Set<String> IDS = Set.of("level_chunk_delegated", "direct_section_setter", "transient_restoration");
    private static final String CHUNK_SET = "LevelChunk.setBlockState(BlockPos,BlockState,boolean)";
    private static final String SECTION_SET = "LevelChunkSection.setBlockState(int,int,int,BlockState,boolean)";
    private record Selected(ServerPlayer actor, BlockMutationScope.Position position, BlockPos pos,
                            LevelChunk chunk, LevelChunkSection section, JsonObject context) {}
    private Stage1RealMutationControls() {}

    private static Selected selected(Protocol.Request request, JsonObject p, ServerPlayer actor,
                                     BlockMutationTrace trace, BlockMutationOwnership ownership,
                                     JsonObject pre, boolean isolated) {
        if (!isolated || trace == null || ownership == null || pre == null ||
                !"diagnostic".equals(trace.scope.purpose) || trace.scope.positions.size() != 1)
            throw new IllegalStateException("real stage1 control needs isolated singleton diagnostic stage1 scope");
        if (actor instanceof FakePlayer || actor.connection == null || actor.connection.connection == null ||
                !actor.connection.connection.isConnected() || actor.connection.connection.isMemoryConnection() ||
                !actor.gameMode.getGameModeForPlayer().isSurvival())
            throw new IllegalArgumentException("real remote Survival control actor required");
        JsonObject context = NativeWitnessContext.validatedContext(request, p, actor.getUUID());
        if (!"diagnostic".equals(Protocol.string(context, "purpose")) ||
                !trace.scope.campaignId.equals(Protocol.string(context, "campaign_id")) ||
                !trace.scope.lineageId.equals(Protocol.string(context, "lineage_id")) ||
                !trace.scope.epochId.equals(Protocol.string(context, "epoch_id")) ||
                !trace.scope.nativeSessionId.equals(Protocol.string(context, "native_session_id")) ||
                !trace.scope.scopeId.equals(Protocol.string(p, "scope_id")) ||
                !trace.scope.nativeSessionId.equals(request.run()) ||
                pre.get("scope_failure").getAsBoolean() || pre.get("overflow").getAsBoolean() ||
                pre.get("io_failure").getAsBoolean() || pre.get("records").getAsLong() != 0 ||
                trace.recordCount() != 0)
            throw new IllegalStateException("real control pre-action scope/context not clean or predeclared");
        long x = Protocol.number(p, "x"), y = Protocol.number(p, "y"), z = Protocol.number(p, "z");
        if (x < -30_000_000 || x > 30_000_000 || z < -30_000_000 || z > 30_000_000 || y < -2048 || y > 2048)
            throw new IllegalArgumentException("control coordinates outside immutable scope bounds");
        BlockMutationScope.Position selected = new BlockMutationScope.Position(
                Protocol.string(p, "dimension"), (int) x, (int) y, (int) z);
        if (!trace.scope.positions.get(0).equals(selected) ||
                !actor.serverLevel().dimension().location().toString().equals(selected.dimension()))
            throw new IllegalArgumentException("control position differs from actual actor and singleton scope");
        BlockPos pos = new BlockPos(selected.x(), selected.y(), selected.z());
        if (!actor.serverLevel().isInWorldBounds(pos) || actor.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 32 * 32)
            throw new IllegalArgumentException("control cell outside loaded nearby actor bounds");
        LevelChunk chunk = actor.serverLevel().getChunkSource().getChunkNow(selected.x() >> 4, selected.z() >> 4);
        if (chunk == null || !ownership.watchedChunk(chunk))
            throw new IllegalStateException("control chunk not already loaded and watched by actual identity");
        int sectionIndex = chunk.getSectionIndex(pos.getY());
        if (sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount())
            throw new IllegalArgumentException("control section outside actual level height");
        LevelChunkSection section = chunk.getSection(sectionIndex);
        if (!ownership.watchedSection(section))
            throw new IllegalStateException("control section not watched by actual identity");
        return new Selected(actor, selected, pos, chunk, section, context);
    }
    static BlockState opposite(BlockState before) {
        if (before.is(Blocks.STONE)) return Blocks.AIR.defaultBlockState();
        if (before.isAir() && before.getBlock() == Blocks.AIR) return Blocks.STONE.defaultBlockState();
        throw new IllegalArgumentException("diagnostic cell must be exact vanilla stone or air, never BE/provider/terrain expansion");
    }
    private static JsonObject position(Selected s) {
        JsonObject p = new JsonObject(); p.addProperty("dimension", s.position.dimension());
        p.addProperty("x", s.position.x()); p.addProperty("y", s.position.y()); p.addProperty("z", s.position.z());
        return p;
    }
    private static void addCall(JsonArray calls, String method, BlockState requested, BlockState returned) {
        JsonObject row = new JsonObject(); row.addProperty("method", method);
        row.add("requested_state", BlockMutationTrace.state(requested));
        if (returned == null) row.add("actual_return_state", com.google.gson.JsonNull.INSTANCE);
        else row.add("actual_return_state", BlockMutationTrace.state(returned));
        calls.add(row);
    }
    static JsonObject positive(Protocol.Request request, JsonObject p, ServerPlayer actor,
                               BlockMutationTrace trace, BlockMutationOwnership ownership,
                               JsonObject pre, boolean isolated) throws java.io.IOException {
        Protocol.only(p, "player_uuid", "control_id", "scope_id", "dimension", "x", "y", "z", "witness_context");
        String id = Protocol.string(p, "control_id");
        if (!IDS.contains(id)) throw new IllegalArgumentException("unknown reviewed positive diagnostic route");
        Selected s = selected(request, p, actor, trace, ownership, pre, isolated);
        BlockState before = s.chunk.getBlockState(s.pos);
        BlockState changed = opposite(before); // exact stone ↔ air, never a production predicate
        int x = s.pos.getX() & 15, y = s.pos.getY() & 15, z = s.pos.getZ() & 15;
        JsonArray calls = new JsonArray();
        JsonObject diagnosticChunkSaveMetadata = null;
        switch (id) {
            case "level_chunk_delegated" -> addCall(calls, CHUNK_SET, changed, s.chunk.setBlockState(s.pos, changed, false));
            case "direct_section_setter" -> {
                addCall(calls, SECTION_SET, changed, s.section.setBlockState(x, y, z, changed, false));
                if (!BlockMutationTrace.state(s.chunk.getBlockState(s.pos)).equals(BlockMutationTrace.state(changed)))
                    throw new IllegalStateException("direct diagnostic Section setter did not change owning chunk live cell");
                // A direct Section setter bypasses LevelChunk's normal save-dirty mark. Only this
                // explicit DIAGNOSTIC action marks its same owning chunk; passive hooks never do.
                boolean unsavedBefore = s.chunk.isUnsaved();
                s.chunk.setUnsaved(true);
                boolean unsavedAfter = s.chunk.isUnsaved();
                if (!unsavedAfter) throw new IllegalStateException("diagnostic owner dirty mark not retained");
                diagnosticChunkSaveMetadata = new JsonObject();
                diagnosticChunkSaveMetadata.addProperty("method", "ChunkAccess.setUnsaved(boolean)");
                diagnosticChunkSaveMetadata.addProperty("owner_chunk_x", s.pos.getX() >> 4);
                diagnosticChunkSaveMetadata.addProperty("owner_chunk_z", s.pos.getZ() >> 4);
                diagnosticChunkSaveMetadata.addProperty("unsaved_before", unsavedBefore);
                diagnosticChunkSaveMetadata.addProperty("requested_unsaved", true);
                diagnosticChunkSaveMetadata.addProperty("unsaved_after", unsavedAfter);
                diagnosticChunkSaveMetadata.addProperty("diagnostic_only", true);
                diagnosticChunkSaveMetadata.addProperty("save_to_disk_proven_by_native", false);
            }
            case "transient_restoration" -> {
                addCall(calls, CHUNK_SET, changed, s.chunk.setBlockState(s.pos, changed, false));
                addCall(calls, CHUNK_SET, before, s.chunk.setBlockState(s.pos, before, false));
            }
            default -> throw new IllegalStateException("unreachable reviewed route");
        }
        BlockState after = s.chunk.getBlockState(s.pos);
        JsonObject post = trace.preActionPrefix(); // actual observed bytes forced before terminal receipt
        int expected = id.equals("transient_restoration") ? 2 : 1;
        boolean complete = !post.get("scope_failure").getAsBoolean() && !post.get("io_failure").getAsBoolean() &&
                !post.get("overflow").getAsBoolean() && post.get("records").getAsLong() == expected;
        JsonObject receipt = new JsonObject(); receipt.addProperty("schema", "bc.playtest_mutation_positive_control.v2");
        receipt.addProperty("scope_id", trace.scope.scopeId); receipt.addProperty("control_id", id);
        receipt.addProperty("actual_actor_uuid", actor.getUUID().toString()); receipt.add("position", position(s));
        receipt.add("actual_before_live_cell_state", BlockMutationTrace.state(before));
        receipt.add("actual_after_live_cell_state", BlockMutationTrace.state(after));
        receipt.add("public_method_calls", calls); receipt.addProperty("public_method_call_count", calls.size());
        receipt.add("diagnostic_chunk_save_metadata", diagnosticChunkSaveMetadata == null ?
                com.google.gson.JsonNull.INSTANCE : diagnosticChunkSaveMetadata);
        receipt.addProperty("level_chunk_setter_calls", id.equals("direct_section_setter") ? 0 : expected);
        receipt.addProperty("direct_section_setter_calls", id.equals("direct_section_setter") ? 1 : 0);
        receipt.add("actual_pre_action_mutation_prefix", pre.deepCopy());
        receipt.add("actual_post_action_mutation_prefix", post);
        receipt.add("controller_declared_witness_context", s.context.deepCopy());
        receipt.addProperty("context_certified_by_native", false);
        receipt.addProperty("observation_complete", complete);
        if (!complete) receipt.addProperty("unsupported_reason", "scope_or_observer_event_count_incomplete");
        receipt.addProperty("natural_origin_proven_by_native", false);
        receipt.addProperty("candidate_provider_certified_by_native", false);
        return receipt;
    }
    /** The identical closed-channel error from the Section fallback is NOT evidence of the private hook. */
    static boolean privateFailedAppendPath(String actualProductionIOExceptionPath) {
        return "PalettedContainer.getAndSet_private_index_return".equals(actualProductionIOExceptionPath);
    }
    static JsonObject ioAppendFailure(Protocol.Request request, JsonObject p, ServerPlayer actor,
                                      BlockMutationTrace trace, BlockMutationOwnership ownership,
                                      JsonObject pre, boolean isolated) throws java.io.IOException {
        Protocol.only(p, "player_uuid", "scope_id", "selector_id", "dimension", "x", "y", "z", "witness_context");
        if (!"cap_or_io_failure".equals(Protocol.string(p, "selector_id")))
            throw new IllegalArgumentException("IO negative selector must be the reviewed diagnostic ID");
        Selected s = selected(request, p, actor, trace, ownership, pre, isolated);
        if (pre.get("bytes").getAsInt() != 0 || !BlockMutationScope.sha256(new byte[0]).equals(pre.get("sha256").getAsString()))
            throw new IllegalStateException("IO negative needs actual empty pre-action trace SHA");
        BlockState before = s.chunk.getBlockState(s.pos), requested = opposite(before);
        // CLOSE ONLY the owned diagnostic channel. Do not assign an IO/scope flag: actual private
        // palette observer must attempt append through production BlockMutationTrace.record.
        trace.closeOwnedChannelForDiagnosticOnly();
        BlockState returned = s.chunk.setBlockState(s.pos, requested, false);
        BlockState after = s.chunk.getBlockState(s.pos);
        JsonObject resulting = trace.preClosedPointer(); // force is impossible on deliberately closed owned channel
        if (!resulting.get("io_failure").getAsBoolean() || resulting.get("overflow").getAsBoolean() ||
                resulting.get("records").getAsLong() != 0 || resulting.get("bytes").getAsLong() != 0 ||
                !BlockMutationScope.sha256(new byte[0]).equals(resulting.get("sha256").getAsString()) ||
                !"mutation_append_failed:ClosedChannelException".equals(Protocol.string(resulting, "failure_reason")) ||
                !privateFailedAppendPath(trace.ioFailedObservedPath()))
            throw new IllegalStateException("original watched palette append did not witness real ClosedChannelException");
        JsonArray calls = new JsonArray(); addCall(calls, CHUNK_SET, requested, returned);
        JsonObject receipt = new JsonObject(); receipt.addProperty("schema", "bc.playtest_mutation_io_append_negative.v2");
        receipt.addProperty("scope_id", trace.scope.scopeId); receipt.addProperty("selector_id", "cap_or_io_failure");
        receipt.addProperty("selector_reviewed_by_native", false);
        receipt.addProperty("actual_actor_uuid", actor.getUUID().toString()); receipt.add("position", position(s));
        receipt.add("actual_before_live_cell_state", BlockMutationTrace.state(before));
        receipt.add("actual_after_live_cell_state", BlockMutationTrace.state(after));
        receipt.add("public_method_call", calls.get(0));
        receipt.addProperty("actual_failed_append_observed_path", trace.ioFailedObservedPath());
        receipt.add("actual_pre_action_mutation_prefix", pre.deepCopy());
        receipt.add("resulting_prefix", resulting);
        receipt.add("controller_declared_witness_context", s.context.deepCopy());
        receipt.addProperty("context_certified_by_native", false);
        receipt.addProperty("natural_origin_proven_by_native", false);
        receipt.addProperty("negative_control_certifies_candidate_provider_by_native", false);
        receipt.addProperty("cap_overflow_proven_by_native", false);
        return receipt;
    }
    static JsonObject exposeSections(Protocol.Request request, JsonObject p, ServerPlayer actor,
                                     BlockMutationTrace trace, BlockMutationOwnership ownership,
                                     JsonObject pre, boolean isolated) throws java.io.IOException {
        Protocol.only(p, "player_uuid", "scope_id", "selector_id", "dimension", "x", "y", "z", "witness_context");
        if (!"section_array_exposed".equals(Protocol.string(p, "selector_id")))
            throw new IllegalArgumentException("section-array negative selector must be exact reviewed ID");
        Selected s = selected(request, p, actor, trace, ownership, pre, isolated);
        // Arm exactly ONE first actual alias exposure after same-owner validation, no synthetic failure.
        trace.armExpectedDiagnosticExposure("mutable_section_array_exposed");
        // ONLY external alias getter. No retained array, write, mutation or direct observer failure assignment.
        s.chunk.getSections();
        JsonObject post = trace.preActionPrefix();
        if (!post.get("scope_failure").getAsBoolean() ||
                !"mutable_section_array_exposed".equals(Protocol.string(post, "failure_reason")))
            throw new IllegalStateException("actual external section-array getter did not trip exact negative hook");
        JsonObject receipt = new JsonObject(); receipt.addProperty("schema", "bc.playtest_mutation_sections_external_negative.v1");
        receipt.addProperty("scope_id", trace.scope.scopeId); receipt.addProperty("selector_id", "section_array_exposed");
        receipt.addProperty("selector_reviewed_by_native", false);
        receipt.addProperty("actual_actor_uuid", actor.getUUID().toString()); receipt.add("position", position(s));
        receipt.add("controller_declared_witness_context", s.context.deepCopy());
        receipt.addProperty("context_certified_by_native", false);
        receipt.addProperty("actual_external_callsite", "LevelChunk.getSections() from native diagnostic action");
        receipt.addProperty("expected_scope_failure", "mutable_section_array_exposed");
        receipt.add("actual_pre_action_mutation_prefix", pre.deepCopy());
        receipt.add("resulting_prefix", post);
        receipt.addProperty("natural_origin_proven_by_native", false);
        receipt.addProperty("negative_control_certifies_candidate_provider_by_native", false);
        return receipt;
    }
}
