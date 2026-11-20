package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** One scoped Forge craft event. Diagnostic observation only: neither physical click custody,
 * source ancestry, nor saved/reopened acquisition is inferred from this event. */
final class PassiveCraftEvent {
    static final String ARM = "player.observe_craft.arm_v1", AWAIT = "player.observe_craft.await_v1";
    static final int MAX_DEADLINE_TICKS = 1200;
    final PassiveObserverBinding binding;
    private final String expectedItem;
    private final int expectedCount;
    private final long armedTick;
    private final JsonArray inventoryBefore;
    private int eventCount;
    private long eventTick = -1, appliedTick = -1;
    private JsonObject eventOutput;
    private JsonArray eventGrid, inventoryAfter;
    private JsonObject cursorAfter;
    private String failure;

    private PassiveCraftEvent(PassiveObserverBinding binding, ServerPlayer actor, String item, int count, long tick) {
        this.binding = binding; expectedItem = item; expectedCount = count; armedTick = tick;
        inventoryBefore = NativeAcquisitionTrace.inventory(actor);
    }
    static PassiveCraftEvent arm(Protocol.Request request, ServerPlayer actor, JsonObject parameters, long tick) {
        Protocol.only(parameters, "player_uuid", "dimension", "observer_id", "expected_output_id",
            "expected_output_count", "deadline_ticks", "witness_context");
        if (!ARM.equals(request.driver()) || !request.caseId().startsWith("control.") ||
                actor.gameMode.getGameModeForPlayer() != GameType.SURVIVAL ||
                actor.connection == null || actor.containerMenu != actor.inventoryMenu ||
                !actor.inventoryMenu.getCarried().isEmpty())
            throw new IllegalArgumentException("craft diagnostic needs a joined idle Survival inventory actor");
        String item = Protocol.string(parameters, "expected_output_id");
        long count = Protocol.number(parameters, "expected_output_count");
        if (!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || count < 1 || count > 64)
            throw new IllegalArgumentException("unreviewed craft output identity/count");
        PassiveObserverBinding binding = PassiveObserverBinding.arm(request, parameters, actor.getUUID(),
            actor.serverLevel().dimension().location().toString(), tick, MAX_DEADLINE_TICKS);
        return new PassiveCraftEvent(binding, actor, item, (int) count, tick);
    }
    void crafted(ServerPlayer actor, PlayerEvent.ItemCraftedEvent event, long tick) {
        if (!binding.actorId.equals(actor.getUUID())) return;
        eventCount++;
        if (!admissibleEvent(armedTick, tick, binding.deadlineTick, eventCount) ||
                actor.containerMenu != actor.inventoryMenu || actor.connection == null) {
            fail("duplicate_late_or_unattributed_craft_event"); return;
        }
        try {
            ItemStack output = event.getCrafting();
            if (output == null || output.isEmpty() || output.getCount() != expectedCount ||
                    ForgeRegistries.ITEMS.getKey(output.getItem()) == null ||
                    !expectedItem.equals(ForgeRegistries.ITEMS.getKey(output.getItem()).toString())) {
                fail("actual_craft_event_output_differs_from_reviewed_identity"); return;
            }
            Container input = event.getInventory();
            if (input == null || input.getContainerSize() < 1 || input.getContainerSize() > 9) {
                fail("unbounded_or_missing_craft_input_grid"); return;
            }
            JsonArray observed = new JsonArray();
            for (int i = 0; i < input.getContainerSize(); i++) {
                ItemStack stack = input.getItem(i);
                if (stack == null || stack.isEmpty()) continue;
                JsonObject row = NativeAcquisitionTrace.stack(stack);
                row.addProperty("grid_slot", i); observed.add(row);
            }
            eventGrid = observed;
            eventOutput = NativeAcquisitionTrace.stack(output);
            eventTick = tick;
        } catch (Exception capture) { fail("craft_event_capture_failed:" + capture.getClass().getSimpleName()); }
    }
    void tick(ServerPlayer actor, long tick) {
        if (failure != null || appliedTick >= 0) return;
        if (actor == null || actor.connection == null ||
                !binding.dimension.equals(actor.serverLevel().dimension().location().toString())) {
            fail("craft_actor_disconnected_or_changed_dimension"); return;
        }
        if (tick > binding.deadlineTick) { fail("craft_event_deadline_elapsed"); return; }
        if (eventTick < 0) return;
        try {
            inventoryAfter = NativeAcquisitionTrace.inventory(actor);
            ItemStack carried = actor.containerMenu.getCarried();
            cursorAfter = carried.isEmpty() ? null : NativeAcquisitionTrace.stack(carried);
            appliedTick = tick;
        } catch (Exception capture) { fail("craft_applied_state_capture_failed:" + capture.getClass().getSimpleName()); }
    }
    static boolean admissibleEvent(long armed, long observed, long deadline, int count) {
        return count == 1 && observed >= armed && observed <= deadline;
    }
    void fail(String reason) { if (failure == null) failure = reason; }
    boolean terminal() { return failure != null || appliedTick >= 0; }
    boolean observedNarrow() { return failure == null && eventCount == 1 && eventTick >= armedTick &&
        appliedTick >= eventTick && eventOutput != null && eventGrid != null && inventoryAfter != null; }
    JsonObject receipt() {
        JsonObject row = binding.echo(); row.addProperty("schema", "bc.playtest_passive_craft_event.v1");
        row.addProperty("expected_output_id", expectedItem); row.addProperty("expected_output_count", expectedCount);
        row.addProperty("arm_tick", armedTick); row.addProperty("event_tick", eventTick);
        row.addProperty("applied_tick", appliedTick); row.addProperty("event_count", eventCount);
        row.addProperty("event_source", "PlayerEvent.ItemCraftedEvent/MONITOR");
        row.addProperty("narrow_event_observed", observedNarrow());
        row.addProperty("physical_client_click_bound", false);
        row.addProperty("source_ancestry_proven_by_native", false);
        row.addProperty("saved_reopened_acquisition_proven", false);
        row.add("inventory_before", inventoryBefore.deepCopy());
        row.add("grid_at_event", eventGrid == null ? JsonNull.INSTANCE : eventGrid.deepCopy());
        row.add("output_at_event", eventOutput == null ? JsonNull.INSTANCE : eventOutput.deepCopy());
        row.add("inventory_after_tick", inventoryAfter == null ? JsonNull.INSTANCE : inventoryAfter.deepCopy());
        row.add("cursor_after_tick", cursorAfter == null ? JsonNull.INSTANCE : cursorAfter.deepCopy());
        if (failure != null) row.addProperty("unsupported_reason", failure);
        return row;
    }
}
