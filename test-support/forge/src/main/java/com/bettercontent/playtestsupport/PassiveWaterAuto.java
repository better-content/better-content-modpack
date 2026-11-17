package com.bettercontent.playtestsupport;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;

/** One actual server-END provider tick, never invokes sipping/debit/Curios mutation or fakes an operation ID. */
final class PassiveWaterAuto {
    static final String SNAPSHOT = "player.water_auto_snapshot_v1";
    static final String ARM = "player.water_auto_observe.arm_v1";
    static final String AWAIT = "player.water_auto_observe.await_v1";
    final PassiveObserverBinding binding;
    final String providerSha, sourceStackSha;
    final int sourceCount;
    final JsonObject armSnapshot;
    private JsonObject callbackBefore, callbackAfter;
    private long callbackTick = -1;
    private int eligibleCallbacks;
    private String failure;
    private PassiveWaterAuto(PassiveObserverBinding binding, String providerSha, String sourceSha,
                             int sourceCount, JsonObject armSnapshot) {
        this.binding = binding; this.providerSha = providerSha;
        this.sourceStackSha = sourceSha; this.sourceCount = sourceCount;
        this.armSnapshot = armSnapshot.deepCopy();
    }
    static PassiveWaterAuto arm(Protocol.Request request, JsonObject p, ServerPlayer actor, long tick) throws Exception {
        Protocol.only(p, "player_uuid", "dimension", "observer_id", "provider_artifact_sha256",
                "source_stack_canonical_sha256", "source_count", "deadline_ticks", "witness_context");
        PassiveObserverBinding bound = PassiveObserverBinding.arm(request, p, actor.getUUID(),
                actor.serverLevel().dimension().location().toString(), tick, 600);
        String pin = Protocol.string(p, "provider_artifact_sha256");
        String sourceSha = Protocol.string(p, "source_stack_canonical_sha256");
        int count = Math.toIntExact(Protocol.number(p, "source_count"));
        if (!sourceSha.matches("[0-9a-f]{64}") || count != 1 ||
                actor.isSpectator() || actor.isCreative() || actor.connection == null)
            throw new IllegalArgumentException("automatic source requires pinned existing real Survival Curios stack");
        JsonObject before = WaterAutoSnapshot.read(actor, bound.dimension, pin);
        JsonObject slots = before.getAsJsonObject("water_slots");
        var source = slots.getAsJsonArray("slots").get(0).getAsJsonObject();
        if (!source.get("present").getAsBoolean() || source.get("count").getAsInt() != count ||
                !source.get("stack_canonical_sha256").getAsString().equals(sourceSha) ||
                !source.get("id").getAsString().equals("minecraft:potion") ||
                before.getAsJsonObject("reserve").get("present").getAsBoolean() ||
                before.get("thirst").getAsInt() >= 20)
            throw new IllegalStateException("automatic source/Thirst arm prestate differs from reviewed existing source");
        return new PassiveWaterAuto(bound, pin, sourceSha, count, before);
    }
    void before(TickEvent.PlayerTickEvent event, ServerPlayer actor, long tick) {
        if (event.phase != TickEvent.Phase.END || actor.tickCount % 10 != 0 ||
                !binding.actorId.equals(actor.getUUID())) return;
        eligibleCallbacks++;
        if (eligibleCallbacks != 1 || callbackBefore != null || tick > binding.deadlineTick) {
            fail("multiple_or_late_actual_provider_tick_callbacks"); return;
        }
        try {
            callbackBefore = WaterAutoSnapshot.read(actor, binding.dimension, providerSha);
            callbackTick = tick;
            if (callbackBefore.get("server_tick").getAsLong() < armSnapshot.get("server_tick").getAsLong())
                fail("provider_callback_clock_precedes_arm");
            if (callbackBefore.getAsJsonObject("reserve").get("present").getAsBoolean())
                fail("preexisting_paid_reserve_appeared_between_arm_and_provider_tick");
            JsonObject beforeSource = callbackBefore.getAsJsonObject("water_slots").getAsJsonArray("slots")
                    .get(0).getAsJsonObject();
            if (!beforeSource.get("present").getAsBoolean() || beforeSource.get("count").getAsInt() != sourceCount ||
                    !beforeSource.get("stack_canonical_sha256").getAsString().equals(sourceStackSha))
                fail("source_changed_between_arm_and_actual_provider_tick");
        } catch (Exception | Error error) { fail("water_auto_head_snapshot_failed:" + error.getClass().getSimpleName()); }
    }
    void after(TickEvent.PlayerTickEvent event, ServerPlayer actor, long tick) {
        if (event.phase != TickEvent.Phase.END || actor.tickCount % 10 != 0 ||
                !binding.actorId.equals(actor.getUUID())) return;
        if (callbackBefore == null || callbackAfter != null || callbackTick != tick) {
            fail("water_auto_provider_return_unpaired"); return;
        }
        try {
            callbackAfter = WaterAutoSnapshot.read(actor, binding.dimension, providerSha);
            JsonObject afterSource = callbackAfter.getAsJsonObject("water_slots").getAsJsonArray("slots")
                    .get(0).getAsJsonObject();
            if (afterSource.get("present").getAsBoolean() &&
                    afterSource.get("stack_canonical_sha256").getAsString().equals(sourceStackSha))
                fail("no_actual_curios_source_debit_in_bound_callback");
            if (callbackAfter.get("thirst").getAsInt() <= callbackBefore.get("thirst").getAsInt())
                fail("no_actual_thirst_gain_in_bound_callback");
            if (callbackBefore.get("empty_bottle_slots").equals(callbackAfter.get("empty_bottle_slots")) &&
                    callbackBefore.get("vanilla_inventory").equals(callbackAfter.get("vanilla_inventory")))
                fail("bottle_destination_unobserved_or_only_unsupported_backpack_or_drop");
        }
        catch (Exception | Error error) { fail("water_auto_return_snapshot_failed:" + error.getClass().getSimpleName()); }
    }
    void fail(String reason) { if (failure == null) failure = reason; }
    boolean terminal() { return callbackAfter != null || failure != null; }
    JsonObject await(Protocol.Request request, JsonObject p, ServerPlayer actor, long tick) {
        Protocol.only(p, "observer_id", "arm_request_sequence", "arm_action_ordinal",
                "player_uuid", "dimension", "witness_context");
        binding.await(request, p, actor.getUUID(), actor.serverLevel().dimension().location().toString());
        JsonObject row = binding.echo();
        row.addProperty("schema", "bc.playtest_passive_water_auto.v1");
        row.addProperty("provider_artifact_sha256", providerSha);
        row.addProperty("source_stack_canonical_sha256", sourceStackSha);
        row.addProperty("source_count", sourceCount);
        row.addProperty("eligible_provider_callback_count", eligibleCallbacks);
        row.addProperty("callback_tick", callbackTick);
        row.add("arm_snapshot", armSnapshot.deepCopy());
        row.add("callback_before", callbackBefore == null ? com.google.gson.JsonNull.INSTANCE : callbackBefore.deepCopy());
        row.add("callback_after", callbackAfter == null ? com.google.gson.JsonNull.INSTANCE : callbackAfter.deepCopy());
        if (tick > binding.deadlineTick) fail("water_auto_observer_deadline_expired");
        if (eligibleCallbacks != 1 || callbackBefore == null || callbackAfter == null)
            fail("water_auto_single_actual_provider_callback_not_observed");
        row.addProperty("observation_complete", failure == null);
        if (failure != null) row.addProperty("unsupported_reason", failure);
        row.addProperty("operation_id", (String) null); // provider LOG may be disabled; never invent a correlation ID
        row.addProperty("source_acquisition_proven_by_native", false);
        row.addProperty("backpack_or_overflow_destination_proven_by_native", false);
        row.addProperty("natural_hydration_proven_by_native", false);
        return row;
    }
}
