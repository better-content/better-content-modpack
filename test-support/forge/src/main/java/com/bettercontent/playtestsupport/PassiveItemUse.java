package com.bettercontent.playtestsupport;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** One real client packet, real Forge use start/finish, final applied state; never invokes item use. */
final class PassiveItemUse {
    static final String ARM = "player.observe_item_use.arm", AWAIT = "player.observe_item_use.await";
    static final int MAX_DEADLINE_TICKS = 7200;
    final PassiveObserverBinding binding;
    final InteractionHand hand;
    final String expectedItem, expectedKind;
    final JsonObject beforeStack, beforePhysiology;
    final String fixtureId;
    final long armTick;
    private long packetTick = -1, startTick = -1, finishTick = -1, finalTick = -1;
    private int packetCount, startCount, finishCount;
    private String failure;
    private JsonObject startedStack, finishedStack, resultStack, afterStack, afterPhysiology;

    private PassiveItemUse(PassiveObserverBinding binding, ServerPlayer player, String item, String kind,
                           InteractionHand hand, long tick, String fixtureId) {
        this.binding = binding; this.hand = hand; expectedItem = item; expectedKind = kind; armTick = tick;
        this.fixtureId = fixtureId;
        beforeStack = NativeAcquisitionTrace.stack(player.getItemInHand(hand));
        beforePhysiology = physiology(player);
        if (!beforePhysiology.get("thirst_supported").getAsBoolean())
            throw new IllegalStateException("pinned Thirst physiology adapter unavailable at arm");
    }
    static PassiveItemUse arm(Protocol.Request request, ServerPlayer actor, JsonObject p, long tick,
                              OwnedFixture fixture) {
        Protocol.only(p, "player_uuid", "dimension", "observer_id", "expected_item_id",
                "expected_provider", "expected_kind", "hand", "deadline_ticks", "witness_context");
        if (!ARM.equals(request.driver()) || actor.gameMode.getGameModeForPlayer() != GameType.SURVIVAL ||
                actor.isUsingItem() || actor.connection == null)
            throw new IllegalArgumentException("passive item observer needs idle joined Survival actor");
        String provider = Protocol.string(p, "expected_provider");
        if (!provider.equals("living_entity_use_item"))
            throw new IllegalArgumentException("instant/placed-food/external item provider needs separate adapter");
        String item = Protocol.string(p, "expected_item_id"), kind = Protocol.string(p, "expected_kind");
        if (!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !(kind.equals("eat") || kind.equals("drink")))
            throw new IllegalArgumentException("item ID or expected use kind invalid");
        InteractionHand hand = switch (Protocol.string(p, "hand")) {
            case "main_hand" -> InteractionHand.MAIN_HAND;
            case "off_hand" -> InteractionHand.OFF_HAND;
            default -> throw new IllegalArgumentException("unknown observed use hand");
        };
        ItemStack stack = actor.getItemInHand(hand);
        if (stack.isEmpty() || ForgeRegistries.ITEMS.getKey(stack.getItem()) == null ||
                !item.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString()) ||
                stack.getUseDuration() <= 1 ||
                stack.getUseAnimation() != (kind.equals("eat") ? UseAnim.EAT : UseAnim.DRINK))
            throw new IllegalArgumentException("actual held item is not reviewed duration-based eating/drinking provider");
        if (fixture != null && (!fixture.provisionedItem() || !fixture.playerId.equals(actor.getUUID()) ||
                !fixture.hand.equals(Protocol.string(p, "hand")) || !fixture.suppliedItem.equals(item) ||
                fixture.suppliedCount != stack.getCount()))
            throw new IllegalArgumentException("item observer fixture is not the exact same-owner authored held stack");
        PassiveObserverBinding binding = PassiveObserverBinding.arm(request, p, actor.getUUID(),
                actor.serverLevel().dimension().location().toString(), tick, MAX_DEADLINE_TICKS);
        return new PassiveItemUse(binding, actor, item, kind, hand, tick, fixture == null ? null : fixture.fixtureId);
    }
    void packet(ServerPlayer player, InteractionHand actualHand, long tick) {
        if (!binding.actorId.equals(player.getUUID())) return;
        packetCount++;
        if (packetCount != 1 || tick > binding.deadlineTick || hand != actualHand ||
                player.getItemInHand(hand).isEmpty() ||
                !expectedItem.equals(ForgeRegistries.ITEMS.getKey(player.getItemInHand(hand).getItem()).toString())) {
            fail("duplicate_or_unmatched_real_client_use_packet"); return;
        }
        packetTick = tick;
    }
    void start(ServerPlayer player, LivingEntityUseItemEvent.Start event, long tick) {
        if (!binding.actorId.equals(player.getUUID())) return;
        startCount++;
        // Forge Start is fired BEFORE LivingEntity sets the used-hand flag; bind the packet hand now,
        // then inspect the applied used-hand state at server tick END rather than misread this callback.
        if (!allowedStart(startCount, packetCount, packetTick, armTick, event.isCanceled())) {
            fail("unattributed_or_canceled_item_use_start"); return;
        }
        try {
            JsonObject captured = NativeAcquisitionTrace.stack(event.getItem());
            if (!beforeStack.get("content_sha256").equals(captured.get("content_sha256"))) {
                fail("item_start_content_differs_from_arm"); return;
            }
            startedStack = captured; startTick = tick;
        }
        catch (Exception capture) { fail("start_stack_capture_failed:" + capture.getClass().getSimpleName()); }
    }
    void finish(ServerPlayer player, LivingEntityUseItemEvent.Finish event, long tick) {
        if (!binding.actorId.equals(player.getUUID())) return;
        finishCount++;
        if (finishCount != 1 || startCount != 1 || packetCount != 1 || startTick < packetTick ||
                player.getUsedItemHand() != hand) {
            fail("unattributed_or_duplicate_item_use_finish"); return;
        }
        try {
            JsonObject captured = NativeAcquisitionTrace.stack(event.getItem());
            if (!beforeStack.get("content_sha256").equals(captured.get("content_sha256"))) {
                fail("item_finish_content_differs_from_arm"); return;
            }
            finishedStack = captured;
            if (!event.getResultStack().isEmpty()) resultStack = NativeAcquisitionTrace.stack(event.getResultStack());
            finishTick = tick;
        } catch (Exception capture) { fail("finish_stack_capture_failed:" + capture.getClass().getSimpleName()); }
    }
    void stop(ServerPlayer player, long tick) {
        if (binding.actorId.equals(player.getUUID()) && finishTick < 0) fail("item_use_stopped_before_finish");
    }
    void tick(ServerPlayer player, long tick) {
        if (failure != null) return;
        if (player == null || player.connection == null ||
                !binding.dimension.equals(player.serverLevel().dimension().location().toString())) {
            fail("item_use_actor_disconnected_or_changed_dimension"); return;
        }
        if (tick > binding.deadlineTick) { fail("item_use_observer_deadline_elapsed"); return; }
        if (startTick >= 0 && finishTick < 0 && (!player.isUsingItem() || player.getUsedItemHand() != hand)) {
            fail("use_start_not_applied_with_reviewed_hand"); return;
        }
        if (finishTick < 0 || finalTick >= 0) return;
        try {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.isEmpty()) afterStack = NativeAcquisitionTrace.stack(stack);
            afterPhysiology = physiology(player);
            finalTick = tick; // ServerTick END after actual operation; never the Finish callback's projected result.
            if (player.isUsingItem() || (afterStack != null &&
                    afterStack.get("content_sha256").equals(beforeStack.get("content_sha256")) &&
                    afterStack.get("count").getAsInt() >= beforeStack.get("count").getAsInt()))
                fail("final_applied_state_does_not_prove_single_consumption");
            if (afterPhysiology.get("thirst_supported").getAsBoolean() != beforePhysiology.get("thirst_supported").getAsBoolean())
                fail("physiology_adapter_changed_during_use");
        } catch (Exception capture) { fail("final_state_capture_failed:" + capture.getClass().getSimpleName()); }
    }
    void fail(String reason) { if (failure == null) failure = reason; }
    boolean terminal() { return failure != null || finalTick >= 0; }
    static boolean allowedStart(int starts, int packets, long packet, long arm, boolean canceled) {
        return starts == 1 && packets == 1 && packet >= arm && !canceled;
    }
    static boolean validChain(long arm, long packet, long start, long finish, long applied,
                              int packets, int starts, int finishes) {
        return packets == 1 && starts == 1 && finishes == 1 && packet >= arm && start >= packet &&
                finish >= start && applied >= finish;
    }
    boolean qualifiedNarrow() { return failure == null && validChain(armTick, packetTick, startTick, finishTick,
            finalTick, packetCount, startCount, finishCount) && beforePhysiology.get("thirst_supported").getAsBoolean(); }
    JsonObject receipt() {
        JsonObject row = binding.echo(); row.addProperty("schema", "bc.playtest_passive_item_use.v1");
        row.addProperty("expected_item_id", expectedItem); row.addProperty("expected_kind", expectedKind);
        row.addProperty("expected_provider", "living_entity_use_item"); row.addProperty("hand", hand == InteractionHand.MAIN_HAND ? "main_hand" : "off_hand");
        row.addProperty("acquisition_origin", fixtureId == null ? "preexisting_unattributed" : "authored_fixture_not_natural");
        row.addProperty("natural_acquisition_proven_by_native", false);
        if (fixtureId != null) row.addProperty("fixture_id", fixtureId);
        row.addProperty("arm_tick", armTick); row.addProperty("packet_tick", packetTick);
        row.addProperty("start_event_tick", startTick); row.addProperty("finish_event_tick", finishTick);
        row.addProperty("final_applied_state_tick", finalTick); row.addProperty("packet_count", packetCount);
        row.addProperty("start_count", startCount); row.addProperty("finish_count", finishCount);
        row.addProperty("packet_source", "ServerGamePacketListenerImpl.handleUseItem/server_thread_HEAD");
        row.addProperty("packet_transport_origin_proven_by_native", false);
        row.addProperty("start_source", "LivingEntityUseItemEvent.Start/MONITOR");
        row.addProperty("finish_source", "LivingEntityUseItemEvent.Finish/MONITOR");
        row.addProperty("final_source", "ServerTickEvent.END/actual_player_state");
        row.add("item_before_arm", beforeStack.deepCopy()); row.add("item_at_start", startedStack == null ? JsonNull.INSTANCE : startedStack.deepCopy());
        row.add("item_at_finish", finishedStack == null ? JsonNull.INSTANCE : finishedStack.deepCopy());
        row.add("finish_result_stack", resultStack == null ? JsonNull.INSTANCE : resultStack.deepCopy());
        row.add("item_after_applied", afterStack == null ? JsonNull.INSTANCE : afterStack.deepCopy());
        row.add("physiology_before", beforePhysiology.deepCopy());
        row.add("physiology_after", afterPhysiology == null ? JsonNull.INSTANCE : afterPhysiology.deepCopy());
        row.addProperty("narrow_event_chain_complete", qualifiedNarrow());
        row.addProperty("external_provider_transition_supported", false);
        if (failure != null) row.addProperty("unsupported_reason", failure);
        return row;
    }
    private static JsonObject physiology(ServerPlayer player) {
        JsonObject row = new JsonObject(); row.addProperty("food_level", player.getFoodData().getFoodLevel());
        row.addProperty("saturation", player.getFoodData().getSaturationLevel());
        try {
            int[] thirst = ThirstFixture.levels(player);
            row.addProperty("thirst_supported", true);
            row.addProperty("thirst", thirst[0]); row.addProperty("quenched", thirst[1]);
        } catch (Exception unsupported) {
            row.addProperty("thirst_supported", false);
            row.addProperty("thirst_unsupported", unsupported.getClass().getSimpleName());
        }
        return row;
    }
}
