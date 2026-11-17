package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/** One normal game-mode START/progress/STOP, not direct destroyBlock and not a loot oracle. */
final class NativeDig {
    final Protocol.Request request;
    final UUID playerId;
    final BlockPos pos;
    final String dimension;
    final Direction face;
    final long startedTick, stopAt, deadline;
    final String beforeBlock, heldBefore;
    final int damageBefore, countBefore;
    private final BlockState beforeState;
    final NativeAcquisitionTrace trace;
    JsonObject traceFilePointer;
    private final ItemStack beforeHeldStack;
    private final boolean requiresCorrectToolForDrops;
    private boolean nativeCallActive, harvestScopeInvalid, harvestObserverError;
    private int harvestCheckCount;
    private long harvestCheckTick = -1;
    private boolean observedCanHarvest;
    final float nativeProgress;
    final JsonArray pickups = new JsonArray(), dropEntities = new JsonArray();
    private final java.util.Map<UUID, JsonObject> scopedDrops = new java.util.LinkedHashMap<>();
    private boolean unscopedItemJoinObserved, popResourceScopeAborted;
    boolean breakEvent, breakCanceled, stopSent;
    long breakTick = -1, stopTick = -1;
    private NativeDig(Protocol.Request request, ServerPlayer player, BlockPos pos, Direction face,
                      long startedTick, long stopAt, float progress, NativeWitnessContext witness) {
        this.request = request; playerId = player.getUUID(); this.pos = pos; this.face = face;
        dimension = player.level().dimension().location().toString();
        this.startedTick = startedTick; this.stopAt = stopAt;
        deadline = Math.addExact(startedTick, request.timeout());
        nativeProgress = progress;
        beforeState = player.serverLevel().getBlockState(pos);
        trace = witness == null ? null : new NativeAcquisitionTrace(witness);
        beforeBlock = ForgeRegistries.BLOCKS.getKey(beforeState.getBlock()).toString();
        requiresCorrectToolForDrops = beforeState.requiresCorrectToolForDrops();
        beforeHeldStack = player.getMainHandItem().copy();
        heldBefore = item(player); damageBefore = player.getMainHandItem().getDamageValue();
        countBefore = player.getMainHandItem().getCount();
    }
    static NativeDig start(Protocol.Request request, ServerPlayer player, JsonObject p, long tick) {
        NativeWitnessContext witness = NativeWitnessContext.DRIVER.equals(request.driver()) ?
                NativeWitnessContext.parse(request, p, player.getUUID()) : null;
        if (witness == null) Protocol.only(p, "player_uuid", "dimension", "x", "y", "z", "face");
        if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL || player.isUsingItem())
            throw new IllegalArgumentException("idle Survival actor required");
        String dimension = Protocol.string(p, "dimension");
        if (!dimension.equals(player.level().dimension().location().toString())) throw new IllegalArgumentException("wrong dimension");
        int x = coord(p, "x"), y = coord(p, "y"), z = coord(p, "z");
        BlockPos pos = new BlockPos(x, y, z);
        Direction face = Direction.byName(Protocol.string(p, "face"));
        if (face == null || !player.serverLevel().isInWorldBounds(pos)
                || !player.serverLevel().getChunkSource().hasChunk(x >> 4, z >> 4)
                || !player.serverLevel().getWorldBorder().isWithinBounds(pos)
                || player.server.isUnderSpawnProtection(player.serverLevel(), pos, player)
                || !player.serverLevel().mayInteract(player, pos)
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > 25)
            throw new IllegalArgumentException("unloaded, protected or out-of-reach target");
        BlockState state = player.serverLevel().getBlockState(pos);
        if (state.isAir()) throw new IllegalArgumentException("target already air");
        float progress = state.getDestroyProgress(player, player.serverLevel(), pos);
        if (!Float.isFinite(progress) || progress <= 0)
            throw new IllegalArgumentException("block cannot be mined with held native tool");
        long duration = ActionLimits.digTicks(progress, request.timeout());
        NativeDig dig = new NativeDig(request, player, pos, face, tick, Math.addExact(tick, duration), progress, witness);
        return dig;
    }
    void sendStart(ServerPlayer player) { sendAction(player, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK); }
    void sendStop(ServerPlayer player) { sendAction(player, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK); }
    private void sendAction(ServerPlayer player, ServerboundPlayerActionPacket.Action action) {
        nativeCallActive = true;
        try {
            player.gameMode.handleBlockBreakAction(pos, action, face,
                    player.serverLevel().getMaxBuildHeight(), Math.toIntExact(request.sequence()));
        } finally { nativeCallActive = false; }
    }
    /** From caller-side capture of the actual canHarvestBlock result in native destroyBlock only. */
    void harvestReturned(ServerPlayer actor, BlockPos candidate, BlockState state, boolean result, long tick) {
        if (!nativeCallActive || !playerId.equals(actor.getUUID())) return;
        if (!pos.equals(candidate) || !beforeState.equals(state)
                || !beforeState.equals(actor.serverLevel().getBlockState(pos))
                || !dimension.equals(actor.serverLevel().dimension().location().toString())
                || !breakEvent || breakCanceled || breakTick != tick
                || !ItemStack.matches(beforeHeldStack, actor.getMainHandItem())) {
            harvestScopeInvalid = true; return;
        }
        if (harvestCheckCount < 64) harvestCheckCount++;
        else harvestScopeInvalid = true;
        harvestCheckTick = tick; observedCanHarvest = result;
    }
    void harvestObserverError() { harvestObserverError = true; }
    static boolean harvestScopeComplete(int count, boolean invalid, boolean observerError) {
        return count == 1 && !invalid && !observerError;
    }
    JsonObject receipt(ServerPlayer player, long now) {
        JsonObject row = new JsonObject(); row.addProperty("player_uuid", playerId.toString());
        row.addProperty("dimension", dimension); row.addProperty("x", pos.getX());
        row.addProperty("y", pos.getY()); row.addProperty("z", pos.getZ());
        row.addProperty("face", face.getName()); row.addProperty("start_tick", startedTick);
        row.addProperty("stop_tick", stopTick); row.addProperty("observed_tick", now);
        row.addProperty("elapsed_ticks", now - startedTick);
        row.addProperty("native_destroy_progress_per_tick", nativeProgress);
        row.addProperty("requires_correct_tool_for_drops", requiresCorrectToolForDrops);
        row.addProperty("harvest_check_source", "ServerPlayerGameMode.destroyBlock/BlockState.canHarvestBlock(actual_return)");
        row.addProperty("harvest_check_count", harvestCheckCount);
        row.addProperty("harvest_check_tick", harvestCheckTick);
        boolean harvestComplete = harvestScopeComplete(harvestCheckCount, harvestScopeInvalid, harvestObserverError);
        row.addProperty("harvest_scope_complete", harvestComplete);
        if (harvestComplete) row.addProperty("can_harvest_with_held_tool", observedCanHarvest);
        else row.addProperty("harvest_unsupported_reason", harvestObserverError ? "observer_error" :
                harvestScopeInvalid ? "actor_pos_state_tool_or_invocation_scope_mismatch" :
                harvestCheckCount == 0 ? "no_exact_native_harvest_check_observed" : "multiple_native_harvest_checks");
        row.addProperty("dig_protocol", "native_game_mode_START_PROGRESS_STOP");
        row.addProperty("before_block", beforeBlock);
        row.addProperty("after_block", ForgeRegistries.BLOCKS.getKey(player.serverLevel().getBlockState(pos).getBlock()).toString());
        row.addProperty("destroyed", player.serverLevel().getBlockState(pos).isAir());
        row.addProperty("held_item_before", heldBefore); row.addProperty("held_item_after", item(player));
        row.addProperty("held_damage_before", damageBefore);
        row.addProperty("held_damage_after", player.getMainHandItem().getDamageValue());
        row.addProperty("held_count_before", countBefore); row.addProperty("held_count_after", player.getMainHandItem().getCount());
        row.addProperty("break_event_seen", breakEvent); row.addProperty("break_event_canceled", breakCanceled);
        row.addProperty("break_event_tick", breakTick);
        row.add("pickup_events", pickups); row.add("drop_entities", dropEntities);
        if (trace != null) {
            row.addProperty("acquisition_trace_schema", NativeWitnessContext.TRACE_SCHEMA);
            row.addProperty("acquisition_trace_complete", trace.complete());
            row.addProperty("acquisition_origin", "scoped_block_drop_runtime_only_natural_block_ancestry_unverified");
            row.addProperty("sealed_state_proven_by_native", false);
            if (trace.unsupportedReason() != null)
                row.addProperty("acquisition_trace_unsupported_reason", trace.unsupportedReason());
            row.addProperty("unsupported_transitions", "unobserved_external_provider_split_merge_transform_curios_fluid");
        }
        row.addProperty("unscoped_item_join_observed", unscopedItemJoinObserved);
        row.addProperty("pop_resource_scope_aborted", popResourceScopeAborted);
        row.addProperty("origin_evidence", popResourceScopeAborted ? "popResource_scope_not_unwound; attribution_unavailable" :
                scopedDrops.isEmpty() ? "no_scoped_native_Block_popResource_drop" :
                "exact_Block_popResource_pos_scope_entity_join_UUID_and_pickup_UUID");
        row.addProperty("pickup_correlation", "UUID_match_only; custom_bypass_paths_uncovered");
        row.add("fixture_mutations", new JsonArray());
        return row;
    }
    void scopeAborted() {
        popResourceScopeAborted = true;
        if (trace != null) trace.unsupported("drop_source_scope_not_unwound");
    }
    void recordPickupBefore(ServerPlayer player, net.minecraft.world.entity.item.ItemEntity entity,
                            long tick, boolean canceled) {
        if (trace != null && playerId.equals(player.getUUID())) trace.pickupBefore(player, entity, tick, canceled);
    }
    void recordPickup(ServerPlayer player, net.minecraft.world.entity.item.ItemEntity entity,
                      net.minecraft.world.item.ItemStack stack, long tick) {
        if (!playerId.equals(player.getUUID())) return;
        if (pickups.size() >= 64) {
            if (trace != null) trace.unsupported("pickup_event_cap_exceeded");
            return;
        }
        JsonObject row = new JsonObject(); row.addProperty("tick", tick);
        row.addProperty("entity_uuid", entity.getUUID().toString());
        row.addProperty("item", stack.isEmpty() ? "minecraft:air" : ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
        row.addProperty("count", stack.getCount());
        JsonObject origin = scopedDrops.get(entity.getUUID());
        row.addProperty("scoped_block_origin_matched", origin != null);
        if (origin != null) {
            origin.addProperty("pickup_observed", true); origin.addProperty("pickup_tick", tick);
            origin.addProperty("pickup_count", stack.getCount());
        }
        pickups.add(row);
        if (trace != null) trace.pickupAfter(player, entity, stack, tick);
    }
    void recordDrop(net.minecraft.world.entity.item.ItemEntity entity, BlockPos source,
                    boolean accepted, long tick) {
        if (!pos.equals(source)) return;
        if (dropEntities.size() >= 64) {
            if (trace != null) trace.unsupported("drop_event_cap_exceeded");
            return;
        }
        JsonObject row = new JsonObject(); row.addProperty("entity_uuid", entity.getUUID().toString());
        row.addProperty("item", ForgeRegistries.ITEMS.getKey(entity.getItem().getItem()).toString());
        row.addProperty("count", entity.getItem().getCount());
        row.addProperty("origin_x", source.getX()); row.addProperty("origin_y", source.getY());
        row.addProperty("origin_z", source.getZ()); row.addProperty("spawn_tick", tick);
        row.addProperty("spawn_x", entity.getX()); row.addProperty("spawn_y", entity.getY());
        row.addProperty("spawn_z", entity.getZ()); row.addProperty("entity_join_accepted", accepted);
        row.addProperty("source_method", "Block.popResource(Level,BlockPos,ItemStack)");
        row.addProperty("pickup_observed", false);
        dropEntities.add(row);
        if (accepted) scopedDrops.put(entity.getUUID(), row);
        if (trace != null) trace.drop(entity, source, beforeBlock, beforeState.toString(), accepted, tick);
    }
    void unscopedJoin() {
        unscopedItemJoinObserved = true;
        if (trace != null) trace.unsupported("unscoped_item_join_during_witness");
    }
    void recordBreak(ServerPlayer player, BlockPos broken, boolean canceled, long tick) {
        if (!playerId.equals(player.getUUID()) || !pos.equals(broken)) return;
        breakEvent = true; breakCanceled = canceled; breakTick = tick;
    }
    private static String item(ServerPlayer player) {
        return player.getMainHandItem().isEmpty() ? "minecraft:air"
                : ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem()).toString();
    }
    private static int coord(JsonObject p, String key) {
        long value = Protocol.number(p, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IllegalArgumentException("invalid " + key);
        return (int) value;
    }
}
