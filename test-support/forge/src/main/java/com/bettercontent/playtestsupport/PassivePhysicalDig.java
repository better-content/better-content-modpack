package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.Connection;
import java.util.IdentityHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One read-only real inbound server packet + real GameMode + actual packet-path drop/pickup witness. */
final class PassivePhysicalDig {
    static final String ARM = "player.observe_dig.arm_v5", AWAIT = "player.observe_dig.await_v5";
    static final String TRACE_SCHEMA = "bc.playtest_passive_packet_dig_trace.v5";
    static final String INSTANT = "instant_start_only", PROGRESS = "progress_start_stop";
    static final String PICKUP = "one_full_pickup", NO_DROP = "no_drop_negative";
    final PassiveObserverBinding binding;
    final BlockPos target;
    final Direction face;
    final String selector, boundTargetSha, crosshairSha, packetVariant, dropOutcome;
    final boolean expectedHarvest;
    final JsonObject armBlock;
    final PhysicalToolWitness tool;
    private final Connection actorConnection;
    private record Ingress(long monotonicNanos, String threadName) {}
    private final IdentityHashMap<ServerboundPlayerActionPacket, Ingress> pendingIngress = new IdentityHashMap<>();
    final List<String> rows = new ArrayList<>();
    private String priorSha;
    private int bytes, starts, stops, aborts, gameModeCalls, destroyCalls, breakEvents;
    private int inboundBefore, inboundAfter;
    private int startPacketSequence = -1, stopPacketSequence = -1;
    private int progressCount, harvestCount;
    private float actualStartProgress = Float.NaN;
    private Boolean actualHarvest;
    private long startTick = -1, stopTick = -1, terminalTick = -1;
    private boolean destroyActive, blockDestroyed, finalized;
    private String breakBlockId, breakBlockState;
    private volatile String failure;
    private ItemEntity source;
    private JsonObject sourceInitial, pickupPre, pickupAfter, terminal;
    private JsonArray inventoryBefore, inventoryAfter;
    private JsonObject vanillaSlotsBeforePickup, vanillaSlotsAfterPickup;
    private int acquiredCount;
    private JsonObject blockAfter;

    private PassivePhysicalDig(PassiveObserverBinding binding, BlockPos target, Direction face, String selector,
                               String targetSha, String crosshairSha, String packetVariant, String dropOutcome,
                               boolean expectedHarvest, JsonObject armBlock, Connection actorConnection,
                               PhysicalToolWitness tool) {
        this.binding = binding; this.target = target; this.face = face; this.selector = selector;
        this.boundTargetSha = targetSha; this.crosshairSha = crosshairSha; this.armBlock = armBlock;
        this.packetVariant = packetVariant; this.dropOutcome = dropOutcome; this.expectedHarvest = expectedHarvest;
        this.actorConnection = actorConnection; this.tool = tool;
    }
    static PassivePhysicalDig arm(Protocol.Request request, JsonObject p, ServerPlayer actor, long tick) {
        Protocol.only(p, "player_uuid", "dimension", "observer_id", "selector_id", "expected_x", "expected_y",
                "expected_z", "expected_face", "bound_target_sha256", "crosshair_projection_sha256",
                "expected_packet_variant", "expected_drop_outcome", "expected_harvest_result",
                "deadline_ticks", "witness_context");
        if (actor instanceof FakePlayer || actor.connection == null || actor.connection.connection == null ||
                !actor.connection.connection.isConnected() || actor.connection.connection.isMemoryConnection() ||
                !actor.gameMode.getGameModeForPlayer().isSurvival())
            throw new IllegalArgumentException("packet observer requires real remote connected Survival player");
        String dimension = Protocol.string(p, "dimension");
        PassiveObserverBinding binding = PassiveObserverBinding.arm(request, p, actor.getUUID(),
                actor.serverLevel().dimension().location().toString(), tick, 200);
        if (Protocol.number(p, "deadline_ticks") < 2)
            throw new IllegalArgumentException("packet dig requires minimum two-tick observer window");
        String selector = Protocol.string(p, "selector_id"), targetSha = Protocol.string(p, "bound_target_sha256"),
                crosshairSha = Protocol.string(p, "crosshair_projection_sha256");
        if (!selector.matches("[A-Za-z0-9._:-]{1,128}") || !targetSha.matches("[0-9a-f]{64}") ||
                !crosshairSha.matches("[0-9a-f]{64}") || !dimension.equals(binding.dimension))
            throw new IllegalArgumentException("unbound target/crosshair selector");
        long x = Protocol.number(p, "expected_x"), y = Protocol.number(p, "expected_y"), z = Protocol.number(p, "expected_z");
        if (x < -30_000_000 || x > 30_000_000 || z < -30_000_000 || z > 30_000_000 ||
                y < actor.serverLevel().getMinBuildHeight() || y >= actor.serverLevel().getMaxBuildHeight())
            throw new IllegalArgumentException("expected physical target outside world");
        Direction face;
        try { face = Direction.valueOf(Protocol.string(p, "expected_face")); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("invalid exact hit face", invalid); }
        BlockPos target = new BlockPos((int) x, (int) y, (int) z);
        JsonObject before = loadedBlock(actor, target);
        if (before.get("air").getAsBoolean()) throw new IllegalArgumentException("cannot arm physical mining on air");
        String variant = Protocol.string(p, "expected_packet_variant"), outcome = Protocol.string(p, "expected_drop_outcome");
        if (p.get("expected_harvest_result") == null ||
                !p.get("expected_harvest_result").isJsonPrimitive() ||
                !p.get("expected_harvest_result").getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("typed reviewed actual-harvest expectation required");
        boolean harvest = p.get("expected_harvest_result").getAsBoolean();
        if (!supportedBranch(variant, outcome, harvest))
            throw new IllegalArgumentException("reviewed exact packet/drop/harvest branch unsupported");
        before.addProperty("server_tick", tick);
        return new PassivePhysicalDig(binding, target, face, selector, targetSha, crosshairSha,
                variant, outcome, harvest, before, actor.connection.connection, PhysicalToolWitness.arm(actor));
    }
    static boolean supportedBranch(String variant, String outcome, boolean harvest) {
        return (INSTANT.equals(variant) || PROGRESS.equals(variant)) &&
                (PICKUP.equals(outcome) && harvest || NO_DROP.equals(outcome) && !harvest);
    }
    static JsonObject loadedBlock(ServerPlayer actor, BlockPos p) {
        LevelChunk chunk = actor.serverLevel().getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
        if (chunk == null) throw new IllegalStateException("physical mining target chunk not already loaded");
        BlockState state = chunk.getBlockState(p);
        JsonObject result = new JsonObject();
        result.addProperty("dimension", actor.serverLevel().dimension().location().toString());
        result.addProperty("x", p.getX()); result.addProperty("y", p.getY()); result.addProperty("z", p.getZ());
        result.addProperty("block_id", ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString());
        result.addProperty("block_state", state.toString()); result.addProperty("air", state.isAir());
        return result;
    }
    void fail(String why) { if (failure == null) failure = why; }
    private boolean same(ServerPlayer actor, BlockPos p) {
        return actor != null && actor.getUUID().equals(binding.actorId) && actor.serverLevel().dimension().location().toString().equals(binding.dimension)
                && target.equals(p);
    }
    /** Read only the same actual direct GameMode START return; no extra break-speed evaluation. */
    void progressReturned(ServerPlayer actor, BlockPos pos, BlockState state, float result,
                          long tick, boolean directGameModeCaller) {
        if (!actor.getUUID().equals(binding.actorId)) return;
        if (starts != 1 || stops != 0 || !same(actor, pos)) return;
        progressCount++;
        if (progressCount != 1 || !directGameModeCaller ||
                !armBlock.get("block_state").getAsString().equals(state.toString()) ||
                !Float.isFinite(result) || result <= 0) { fail("actual_START_progress_return_unbound_or_invalid"); return; }
        actualStartProgress = result;
        JsonObject observation = new JsonObject(); observation.addProperty("actual_progress_per_tick", result);
        observation.addProperty("direct_gamemode_START_caller", true);
        row("actual_START_destroy_progress_return", tick, null, null, observation);
    }
    /** Actual GameMode stored Forge harvest result; does not invoke canHarvestBlock again. */
    void harvestReturned(ServerPlayer actor, BlockPos pos, BlockState state, boolean result, long tick) {
        if (!actor.getUUID().equals(binding.actorId)) return;
        harvestCount++;
        if (harvestCount != 1 || !destroyActive || breakEvents != 1 || !same(actor, pos) ||
                !armBlock.get("block_state").getAsString().equals(state.toString()))
            fail("actual_stored_harvest_result_unbound_or_invalid");
        actualHarvest = result;
        JsonObject observation = new JsonObject(); observation.addProperty("actual_stored_can_harvest", result);
        row("actual_gamemode_stored_harvest_return", tick, null, null, observation);
    }
    /** Netty-thread decoded inbound identity; only same captured real remote Connection is accepted. */
    synchronized void ingress(Connection connection, ServerboundPlayerActionPacket packet) {
        if (connection != actorConnection || connection.isMemoryConnection()) return;
        if (pendingIngress.size() >= 4 || pendingIngress.containsKey(packet)) {
            fail("duplicate_or_excess_decoded_network_packet_ingress"); return;
        }
        pendingIngress.put(packet, new Ingress(System.nanoTime(), Thread.currentThread().getName()));
    }
    void packetBefore(ServerPlayer actor, ServerboundPlayerActionPacket packet, long tick) {
        if (!actor.getUUID().equals(binding.actorId)) return;
        ServerboundPlayerActionPacket.Action action = packet.getAction();
        if (action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK &&
                action != ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK &&
                action != ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) return;
        inboundBefore++;
        Ingress decoded;
        synchronized (this) { decoded = pendingIngress.remove(packet); }
        if (decoded == null || actor.connection == null || actor.connection.connection != actorConnection)
            fail("server_handler_packet_not_bound_to_actual_decoded_remote_connection_ingress");
        boolean exact = same(actor, packet.getPos()) && face == packet.getDirection();
        if (!exact) fail("other_dig_packet_during_bound_interval");
        if (action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) { aborts++; fail("real_client_abort_packet"); }
        if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            starts++; if (starts != 1 || stops > 0 || tick > binding.deadlineTick) fail("duplicate_or_late_start");
            startPacketSequence = packet.getSequence(); startTick = tick;
            JsonObject armOriginal = armBlock.deepCopy(); armOriginal.remove("server_tick");
            if (!armOriginal.equals(loadedBlock(actor, target))) fail("loaded_block_changed_before_real_packet_start");
        }
        if (action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
            stops++;
            if (progressCount == 1 && actualStartProgress >= 1.0f) fail("unexpected_STOP_for_actual_instant_START_only");
            if (starts != 1 || stops != 1 || packet.getSequence() <= startPacketSequence)
                fail("missing_or_duplicate_start_stop_or_ack_sequence");
            stopPacketSequence = packet.getSequence(); stopTick = tick;
        }
        JsonObject ingress = new JsonObject();
        if (decoded != null) {
            ingress.addProperty("decoded_ingress_monotonic_nanos", decoded.monotonicNanos());
            ingress.addProperty("decoded_ingress_thread_name", decoded.threadName());
            ingress.addProperty("same_decoded_packet_object", true);
        } else ingress.addProperty("same_decoded_packet_object", false);
        ingress.addProperty("bound_actual_remote_connection", decoded != null && actor.connection != null &&
                actor.connection.connection == actorConnection);
        row("decoded_remote_packet_then_actual_server_handler", tick, action.name(), packet.getSequence(), ingress);
    }
    void packetAfter(ServerPlayer actor, ServerboundPlayerActionPacket packet, long tick) {
        if (!actor.getUUID().equals(binding.actorId)) return;
        ServerboundPlayerActionPacket.Action action = packet.getAction();
        if (action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK &&
                action != ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK &&
                action != ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) return;
        inboundAfter++; row("real_inbound_packet_after_gamemode", tick, action.name(), packet.getSequence(), null);
        if (inboundAfter > inboundBefore) fail("packet_return_without_head");
    }
    void gameMode(ServerPlayer actor, BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction actualFace,
                  int sequence, long tick) {
        if (!actor.getUUID().equals(binding.actorId)) return;
        gameModeCalls++;
        if (!same(actor, pos) || actualFace != face || (action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK &&
                action != ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) ||
                (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK && sequence != startPacketSequence) ||
                (action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK && sequence != stopPacketSequence))
            fail("gamemode_action_not_bound_to_real_inbound_packet");
        row("actual_gamemode_handle", tick, action.name(), sequence, null);
    }
    void destroyBefore(ServerPlayer actor, BlockPos pos, long tick) {
        if (!actor.getUUID().equals(binding.actorId)) return;
        destroyActive = true; destroyCalls++;
        tool.destroyBefore(actor, this, tick);
        if (!same(actor, pos) || starts != 1 || stops > 1 || destroyCalls != 1)
            fail("unbound_or_duplicate_actual_gamemode_destroy");
        row("actual_gamemode_destroy_head", tick, null, null, loadedBlock(actor, pos));
    }
    void destroyAfter(ServerPlayer actor, BlockPos pos, boolean result, long tick) {
        if (!actor.getUUID().equals(binding.actorId)) return;
        if (!destroyActive || !same(actor, pos)) fail("unbalanced_gamemode_destroy");
        destroyActive = false; blockDestroyed = result;
        tool.destroyAfter(actor, this, tick);
        blockAfter = loadedBlock(actor, target);
        if (!result || !blockAfter.get("air").getAsBoolean()) fail("actual_destroy_did_not_remove_bound_block");
        row("actual_gamemode_destroy_return", tick, null, null, blockAfter);
    }
    boolean dropScoped(BlockPos pos, String dimension) {
        return destroyActive && starts == 1 && target.equals(pos) && binding.dimension.equals(dimension);
    }
    void breakEvent(ServerPlayer actor, BlockPos pos, BlockState state, boolean canceled, long tick) {
        if (!actor.getUUID().equals(binding.actorId)) return;
        if (!same(actor, pos) || !destroyActive || canceled) { fail("unbound_or_canceled_real_break_event"); return; }
        breakEvents++;
        if (breakEvents != 1) fail("multiple_real_break_events");
        breakBlockId = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
        breakBlockState = state.toString();
        if (!armBlock.get("block_id").getAsString().equals(breakBlockId) ||
                !armBlock.get("block_state").getAsString().equals(breakBlockState)) fail("break_event_state_differs_from_arm");
        row("actual_forge_break_event", tick, null, null, armBlock);
    }
    void drop(ItemEntity item, BlockPos pos, boolean accepted, long tick) {
        if (source != null || !destroyActive || !target.equals(pos) || !accepted || breakEvents != 1 ||
                !binding.dimension.equals(item.level().dimension().location().toString())) {
            fail("drop_not_unique_bound_accepted_break_source"); return;
        }
        source = item; sourceInitial = NativeAcquisitionTrace.stack(item.getItem());
        JsonObject detail = new JsonObject(); detail.add("source_stack", sourceInitial.deepCopy());
        detail.addProperty("item_entity_uuid", item.getUUID().toString());
        detail.addProperty("source_block_id", breakBlockId); detail.addProperty("source_block_state", breakBlockState);
        detail.addProperty("source_provider", "actual_packet_path_Block.popResource_then_EntityJoinLevelEvent_MONITOR");
        row("source_entity_join_before_insertion", tick, null, null, detail);
    }
    void unscopedJoin(ItemEntity item, long tick) {
        if (!binding.dimension.equals(item.level().dimension().location().toString())) return;
        if (Math.abs(item.getX() - (target.getX() + 0.5)) > 2 ||
                Math.abs(item.getY() - (target.getY() + 0.5)) > 2 ||
                Math.abs(item.getZ() - (target.getZ() + 0.5)) > 2) return;
        if (starts == 1 && tick <= binding.deadlineTick) {
            fail("unscoped_near_target_item_entity_join_during_packet_interval");
            JsonObject observed = new JsonObject();
            observed.addProperty("item_entity_uuid", item.getUUID().toString());
            observed.addProperty("loaded_from_disk_or_external_origin", true);
            row("unscoped_near_target_item_entity_join", tick, null, null, observed);
        }
    }
    void pickupBefore(ServerPlayer actor, ItemEntity item, boolean canceled, long tick) {
        if (!actor.getUUID().equals(binding.actorId)) return;
        if (source == null || source != item) { fail("unscoped_item_pickup_pre"); return; }
        if (pickupPre != null || canceled || item.isRemoved() || actor.serverLevel().getEntity(item.getUUID()) != item) {
            fail("pickup_pre_not_unique_inserted_source"); return;
        }
        pickupPre = NativeAcquisitionTrace.stack(item.getItem()); inventoryBefore = NativeAcquisitionTrace.inventory(actor);
        vanillaSlotsBeforePickup = VanillaInventorySlots.capture(actor);
        if (!NativeAcquisitionTrace.sameExactStack(sourceInitial, pickupPre)) fail("source_changed_before_pickup");
        JsonObject detail = new JsonObject(); detail.add("source_before", pickupPre.deepCopy());
        detail.add("inventory_before", inventoryBefore.deepCopy());
        detail.add("vanilla_inventory_slots_before_v1", vanillaSlotsBeforePickup.deepCopy());
        detail.addProperty("item_entity_uuid", item.getUUID().toString());
        row("actual_pickup_pre", tick, null, null, detail);
    }
    void pickupAfter(ServerPlayer actor, ItemEntity item, ItemStack acquired, long tick) {
        if (!actor.getUUID().equals(binding.actorId)) return;
        if (source == null || source != item || pickupPre == null || pickupAfter != null) {
            fail("pickup_after_without_unique_matching_pre"); return;
        }
        JsonObject gained = NativeAcquisitionTrace.stack(acquired);
        inventoryAfter = NativeAcquisitionTrace.inventory(actor);
        vanillaSlotsAfterPickup = VanillaInventorySlots.capture(actor);
        ItemStack remainder = item.getItem();
        JsonObject sourceAfter = remainder.isEmpty() ? NativeAcquisitionTrace.empty() :
                NativeAcquisitionTrace.present(NativeAcquisitionTrace.stack(remainder));
        boolean conserved = NativeAcquisitionTrace.sameContent(pickupPre, gained) &&
                NativeAcquisitionTrace.sourceTransition(pickupPre, gained, sourceAfter) &&
                NativeAcquisitionTrace.conserved(inventoryBefore, inventoryAfter,
                        gained.get("content_sha256").getAsString(), gained.get("count").getAsInt());
        if (!conserved) fail("packet_path_source_inventory_conservation_unproven");
        acquiredCount = gained.get("count").getAsInt(); pickupAfter = sourceAfter;
        JsonObject detail = new JsonObject(); detail.add("acquired_stack", gained);
        detail.add("source_after_inventory_add", sourceAfter.deepCopy());
        detail.add("inventory_before", inventoryBefore.deepCopy()); detail.add("inventory_after", inventoryAfter.deepCopy());
        detail.add("vanilla_inventory_slots_before_v1", vanillaSlotsBeforePickup.deepCopy());
        detail.add("vanilla_inventory_slots_after_v1", vanillaSlotsAfterPickup.deepCopy());
        detail.addProperty("item_entity_uuid", item.getUUID().toString());
        row("actual_pickup_after_inventory_add", tick, null, null, detail);
        if (conserved) tool.boundPickup(actor, item.getUUID(), sourceInitial, gained,
                vanillaSlotsBeforePickup, vanillaSlotsAfterPickup, this, tick);
    }
    void tick(ServerPlayer actor, long tick) {
        if (finalized) return;
        if (tick > binding.deadlineTick) { fail("physical_packet_observer_deadline_exceeded"); return; }
        boolean actualInstant = progressCount == 1 && Float.isFinite(actualStartProgress) && actualStartProgress >= 1.0f;
        boolean packetTerminal = actualInstant ? starts == 1 && inboundAfter == 1 && stops == 0 :
                starts == 1 && stops == 1 && inboundAfter == 2;
        if (!packetTerminal || !blockDestroyed || harvestCount != 1) return;
        if (source == null) {
            // Actual absence, even if reviewed expectation was pickup, requires the FULL interval.
            if (tick < binding.deadlineTick) return;
            finalized = true; terminalTick = tick;
            terminal = new JsonObject(); terminal.addProperty("source_kind", "no_scoped_drop_in_observed_interval");
            terminal.addProperty("observed_through_deadline_tick", tick);
            if (pickupPre != null || pickupAfter != null) fail("pickup_seen_without_bound_drop");
            row("no_scoped_packet_path_drop_through_deadline", tick, null, null, terminal);
            return;
        }
        if (pickupAfter == null) return; // Genuine pickup delay/approach, bounded by arm deadline.
        boolean removed = source.isRemoved(), inserted = actor.serverLevel().getEntity(source.getUUID()) == source;
        if (!removed || inserted) return;
        if (dropOutcome.equals(NO_DROP) && tick < binding.deadlineTick) return; // Contradictory positive needs full negative window too.
        finalized = true; terminalTick = tick;
        terminal = new JsonObject(); terminal.addProperty("removed", removed);
        terminal.addProperty("in_world", inserted); terminal.addProperty("source_kind", "absent");
        if (!"empty".equals(pickupAfter.get("kind").getAsString()) ||
                acquiredCount != sourceInitial.get("count").getAsInt()) fail("source_terminal_full_conservation_unproven");
        row("actual_source_terminal_after_real_pickup_discard", tick, null, null, terminal);
    }
    JsonObject await(Protocol.Request request, JsonObject p, ServerPlayer actor, long tick) {
        Protocol.only(p, "player_uuid", "dimension", "observer_id", "arm_request_sequence",
                "arm_action_ordinal", "witness_context");
        binding.await(request, p, actor.getUUID(), actor.serverLevel().dimension().location().toString());
        tick(actor, tick);
        JsonObject result = binding.echo(); result.addProperty("schema", "bc.playtest_passive_packet_dig.v5");
        result.addProperty("expected_packet_variant", packetVariant);
        result.addProperty("expected_drop_outcome", dropOutcome);
        result.addProperty("expected_harvest_result", expectedHarvest);
        result.addProperty("actual_start_progress_observed_count", progressCount);
        if (progressCount == 1 && Float.isFinite(actualStartProgress))
            result.addProperty("actual_START_destroy_progress_per_tick", actualStartProgress);
        else result.add("actual_START_destroy_progress_per_tick", JsonNull.INSTANCE);
        result.addProperty("actual_stored_harvest_observed_count", harvestCount);
        if (harvestCount == 1 && actualHarvest != null)
            result.addProperty("actual_stored_can_harvest", actualHarvest);
        else result.add("actual_stored_can_harvest", JsonNull.INSTANCE);
        result.addProperty("selector_id", selector); result.addProperty("bound_target_sha256", boundTargetSha);
        result.addProperty("crosshair_projection_sha256", crosshairSha);
        result.addProperty("arm_tick", armBlock.get("server_tick") == null ? -1 : armBlock.get("server_tick").getAsLong());
        result.addProperty("start_tick", startTick); result.addProperty("stop_tick", stopTick);
        result.addProperty("terminal_tick", terminalTick); result.addProperty("start_packet_sequence", startPacketSequence);
        result.addProperty("stop_packet_sequence", stopPacketSequence); result.addProperty("inbound_before_count", inboundBefore);
        result.addProperty("inbound_after_count", inboundAfter); result.addProperty("game_mode_call_count", gameModeCalls);
        result.addProperty("destroy_call_count", destroyCalls); result.addProperty("break_event_count", breakEvents);
        result.addProperty("aborts", aborts); result.add("arm_block", armBlock.deepCopy());
        result.add("block_after", blockAfter == null ? JsonNull.INSTANCE : blockAfter.deepCopy());
        result.add("source_terminal", terminal == null ? JsonNull.INSTANCE : terminal.deepCopy());
        String actualVariant = progressCount == 1 && Float.isFinite(actualStartProgress) && actualStartProgress > 0 ?
                (actualStartProgress >= 1.0f ? INSTANT : PROGRESS) : null;
        String actualDrop = finalized && terminal != null ?
                (source == null ? NO_DROP : PICKUP) : null;
        boolean sourceComplete = actualDrop != null && (source == null ?
                pickupPre == null && pickupAfter == null && terminalTick == binding.deadlineTick :
                sourceInitial != null && pickupPre != null && pickupAfter != null && terminal != null &&
                        "absent".equals(terminal.get("source_kind").getAsString()) &&
                        (!dropOutcome.equals(NO_DROP) || terminalTick == binding.deadlineTick));
        Capture captured = new Capture(actualVariant, actualDrop, actualHarvest, starts, stops, aborts,
                inboundBefore, inboundAfter, gameModeCalls, destroyCalls, breakEvents, blockDestroyed,
                harvestCount, finalized, sourceComplete, failure, tick, binding.deadlineTick);
        JsonObject decision = classifyCapture(captured, packetVariant, dropOutcome, expectedHarvest);
        for (var entry : decision.entrySet()) result.add(entry.getKey(), entry.getValue());
        JsonObject wear = tool.await(actor, this, tick);
        result.add("tool_wear_v2", wear);
        JsonObject conclusion = new JsonObject();
        conclusion.addProperty("physical_observation_complete", decision.get("observation_complete").getAsBoolean());
        conclusion.addProperty("tool_capture_complete", wear.get("capture_complete").getAsBoolean());
        conclusion.addProperty("reviewed_expectation_matches", decision.get("reviewed_expectation_matches").getAsBoolean());
        row("packet_path_observation_terminal", tick, null, null, conclusion);
        if (failure != null || !wear.get("capture_complete").getAsBoolean() ||
                rows.isEmpty() || !rows.get(rows.size() - 1).contains("packet_path_observation_terminal")) {
            result.addProperty("observation_complete", false);
            result.addProperty("reviewed_expectation_matches", false);
            result.add("expectation_mismatches", new JsonArray());
            result.addProperty("unsupported_reason", failure == null ? "physical_tool_wear_or_trace_terminal_incomplete" : failure);
        }
        result.addProperty("acquisition_origin", "preexisting_unattributed");
        result.addProperty("negative_scope_proven_by_native", false);
        result.addProperty("full_primitive_provider_coverage_proven_by_native", false);
        result.addProperty("packet_os_origin_proven_by_native", false);
        result.addProperty("natural_acquisition_proven_by_native", false);
        result.addProperty("natural_origin_proven_by_native", false);
        return result;
    }
    /** The same pure capture decision runs in await and in the unrun producer→consumer fixture regressions. */
    record Capture(String actualVariant, String actualDrop, Boolean actualHarvest,
                   int starts, int stops, int aborts, int inboundBefore, int inboundAfter,
                   int gameModeCalls, int destroyCalls, int breakEvents, boolean blockDestroyed,
                   int harvestCount, boolean finalized, boolean sourceComplete, String failure,
                   long tick, long deadline) {}
    static JsonObject classifyCapture(Capture c, String expectedVariant, String expectedDrop, boolean expectedHarvest) {
        int packets = INSTANT.equals(c.actualVariant) ? 1 : 2;
        boolean packetComplete = c.actualVariant != null && c.starts == 1 && c.stops == packets - 1 && c.aborts == 0 &&
                c.inboundBefore == packets && c.inboundAfter == packets && c.gameModeCalls == packets;
        boolean complete = c.failure == null && c.tick <= c.deadline && packetComplete &&
                c.destroyCalls == 1 && c.breakEvents == 1 && c.blockDestroyed && c.harvestCount == 1 &&
                c.actualHarvest != null && c.finalized && c.sourceComplete && c.actualDrop != null;
        JsonObject result = new JsonObject();
        if (c.actualVariant == null) result.add("actual_packet_variant", JsonNull.INSTANCE);
        else result.addProperty("actual_packet_variant", c.actualVariant);
        if (c.actualDrop == null) result.add("actual_drop_outcome", JsonNull.INSTANCE);
        else result.addProperty("actual_drop_outcome", c.actualDrop);
        if (c.actualHarvest == null) result.add("actual_stored_can_harvest", JsonNull.INSTANCE);
        else result.addProperty("actual_stored_can_harvest", c.actualHarvest);
        result.addProperty("observation_complete", complete);
        // A reviewed mismatch must NEVER masquerade as a missing callback or suppress the drop source.
        JsonArray mismatches = expectationMismatches(complete, expectedVariant, c.actualVariant,
                expectedDrop, c.actualDrop, expectedHarvest, c.actualHarvest);
        result.addProperty("reviewed_expectation_matches", complete && mismatches.isEmpty());
        result.add("expectation_mismatches", mismatches);
        if (!complete) result.addProperty("unsupported_reason", c.failure == null ?
                "incomplete_real_packet_source_or_terminal_interval" : c.failure);
        return result;
    }
    /** Only compare after complete capture; never manufacture a reviewed verdict from missing callbacks. */
    static JsonArray expectationMismatches(boolean complete, String expectedVariant, String actualVariant,
                                           String expectedDrop, String actualDrop, boolean expectedHarvest, Boolean actualHarvest) {
        JsonArray mismatches = new JsonArray();
        if (!complete) return mismatches;
        if (!expectedVariant.equals(actualVariant)) mismatches.add("packet_variant");
        if (!expectedDrop.equals(actualDrop)) mismatches.add("drop_outcome");
        if (actualHarvest == null || expectedHarvest != actualHarvest) mismatches.add("harvest_result");
        return mismatches;
    }
    void toolCauseBefore(ItemStack stack, ServerPlayer actor, long tick) { tool.causeBefore(stack, actor, this, tick); }
    void toolCauseAfter(ItemStack stack, ServerPlayer actor, long tick) { tool.causeAfter(stack, actor, this, tick); }
    void toolBreakCallback(ServerPlayer actor, net.minecraft.world.entity.EquipmentSlot slot, long tick) {
        tool.callback(actor, slot, this, tick);
    }
    void toolTickEnd() { tool.tickEnd(); }
    boolean terminal() { return finalized || failure != null; }
    List<String> rows() { return List.copyOf(rows); }
    void scopeAborted() { fail("packet_path_drop_scope_unbalanced_at_tick_end"); }
    void row(String kind, long tick, String action, Integer sequence, JsonObject detail) {
        if (rows.size() >= 256 || bytes > 16_000_000) { fail("packet_dig_trace_cap_exceeded"); return; }
        JsonObject event = new JsonObject(); event.addProperty("schema", TRACE_SCHEMA);
        event.addProperty("event_index", rows.size() + 1);
        event.addProperty("kind", kind); event.addProperty("tick", tick);
        event.addProperty("actual_native_session_id", binding.declared.get("native_session_id").getAsString());
        event.addProperty("actual_actor_uuid", binding.actorId.toString());
        event.addProperty("arm_request_sequence", binding.arm.sequence());
        event.add("controller_declared_arm_context", binding.declared.deepCopy());
        event.addProperty("bound_target_sha256", boundTargetSha);
        event.addProperty("crosshair_projection_sha256", crosshairSha);
        event.addProperty("actual_capability_id", ARM);
        event.addProperty("context_certified_by_native", false);
        event.addProperty("x", target.getX()); event.addProperty("y", target.getY()); event.addProperty("z", target.getZ());
        event.addProperty("face", face.name());
        if (action != null) event.addProperty("packet_action", action);
        if (sequence != null) event.addProperty("packet_sequence", sequence);
        if (detail != null) event.add("observed", detail.deepCopy());
        if (priorSha == null) event.add("prior_row_sha256", JsonNull.INSTANCE);
        else event.addProperty("prior_row_sha256", priorSha);
        String encoded = event + "\n";
        byte[] raw = encoded.getBytes(StandardCharsets.UTF_8);
        if (raw.length > 128_000 || bytes + raw.length > 16_000_000) { fail("packet_dig_row_byte_cap_exceeded"); return; }
        try { priorSha = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)); }
        catch (Exception error) { fail("packet_dig_hash_failed"); return; }
        rows.add(event.toString()); bytes += raw.length;
    }
}
