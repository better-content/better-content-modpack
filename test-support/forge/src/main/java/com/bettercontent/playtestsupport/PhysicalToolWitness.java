package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/** Passive same-actor/mainhand tool snapshots + actual callback cause. Never invokes durability policy. */
final class PhysicalToolWitness {
    private record Cause(UUID actorId, ItemStack stack) {}
    private static final ThreadLocal<Cause> CAUSE = new ThreadLocal<>();
    final UUID actorId;
    final int selectedSlot;
    final ItemStack original;
    final JsonObject arm;
    private JsonObject beforeDestroy, callback, afterDestroy, atAwait;
    private JsonObject pickupSlotsAfter, selectedRefill;
    private UUID boundPickupSourceId;
    private String failure;
    private int callbacks, causeEnters, causeExits;
    private boolean destroying, destroyReturned;

    private PhysicalToolWitness(ServerPlayer actor) {
        actorId = actor.getUUID(); selectedSlot = actor.getInventory().selected;
        if (selectedSlot < 0 || selectedSlot > 8) throw new IllegalArgumentException("tool selected slot outside hotbar");
        original = actor.getMainHandItem();
        arm = snapshot(original);
    }
    static PhysicalToolWitness arm(ServerPlayer actor) { return new PhysicalToolWitness(actor); }
    static JsonObject snapshot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            JsonObject empty = new JsonObject(); empty.addProperty("kind", "empty"); empty.addProperty("count", 0);
            return empty; // No invented empty stack hash or item ID.
        }
        CompoundTag saved = stack.save(new CompoundTag());
        JsonObject result = new JsonObject(); result.addProperty("kind", "nonempty");
        result.addProperty("item_id", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
        result.addProperty("count", stack.getCount());
        result.addProperty("canonical_algorithm", CanonicalStackIdentity.ALGORITHM);
        result.addProperty("canonical_sha256", CanonicalStackIdentity.sha256(saved));
        result.addProperty("content_algorithm", CanonicalStackIdentity.CONTENT_ALGORITHM);
        result.addProperty("content_sha256", CanonicalStackIdentity.contentSha256(saved));
        result.addProperty("damage_live", stack.getDamageValue()); // read-only existing item state, NOT a damage predicate
        CompoundTag tag = saved.contains("tag", Tag.TAG_COMPOUND) ? saved.getCompound("tag") : null;
        if (tag != null && tag.contains("Damage", Tag.TAG_INT)) result.addProperty("damage_nbt_int", tag.getInt("Damage"));
        else result.add("damage_nbt_int", JsonNull.INSTANCE);
        return result;
    }
    private void fail(String reason) { if (failure == null) failure = reason; }
    private boolean same(ServerPlayer actor) { return actor != null && actorId.equals(actor.getUUID()); }
    void destroyBefore(ServerPlayer actor, PassivePhysicalDig dig, long tick) {
        if (!same(actor)) return;
        if (destroying || destroyReturned || actor.getInventory().selected != selectedSlot ||
                actor.getMainHandItem() != original || original.isEmpty()) fail("tool_destroy_pre_identity_slot_or_nonempty_unbound");
        destroying = true; beforeDestroy = snapshot(actor.getMainHandItem());
        if (!arm.equals(beforeDestroy)) fail("tool_changed_between_arm_and_real_destroy");
        JsonObject detail = new JsonObject(); detail.addProperty("vanilla_hotbar_slot", selectedSlot);
        detail.add("tool", beforeDestroy.deepCopy());
        dig.row("tool_before_actual_gamemode_destroy", tick, null, null, detail);
    }
    void causeBefore(ItemStack stack, ServerPlayer actor, PassivePhysicalDig dig, long tick) {
        if (!same(actor)) return;
        if (CAUSE.get() != null || !destroying || destroyReturned || actor.getInventory().selected != selectedSlot ||
                stack != original || actor.getMainHandItem() != original) fail("tool_hurt_and_break_cause_unbound_or_nested");
        if (CAUSE.get() == null) CAUSE.set(new Cause(actorId, stack));
        causeEnters++;
        JsonObject detail = new JsonObject(); detail.addProperty("same_selected_item_object", stack == original);
        detail.add("tool", snapshot(stack));
        dig.row("actual_tool_hurt_and_break_consumer_before", tick, null, null, detail);
    }
    void callback(ServerPlayer actor, EquipmentSlot slot, PassivePhysicalDig dig, long tick) {
        if (!same(actor)) return;
        callbacks++;
        Cause cause = CAUSE.get();
        if (!destroying || destroyReturned || callbacks > 4 || slot != EquipmentSlot.MAINHAND ||
                actor.getInventory().selected != selectedSlot || cause == null ||
                !actorId.equals(cause.actorId()) || cause.stack() != original ||
                actor.getMainHandItem() != original) fail("actual_equipment_break_callback_not_bound_to_selected_item_cause");
        JsonObject observed = snapshot(actor.getMainHandItem()); // actual callback runs BEFORE shrink/clearDamage
        if (callback == null) callback = observed.deepCopy(); // first callback; additional fully bound ones retain distinct raw rows
        JsonObject detail = new JsonObject(); detail.addProperty("tool_callback_index", callbacks);
        detail.addProperty("equipment_slot", slot.name());
        detail.addProperty("interaction_hand", "MAIN_HAND");
        detail.addProperty("vanilla_hotbar_slot", selectedSlot);
        detail.addProperty("same_hurt_and_break_item_object", cause != null && cause.stack() == original);
        detail.add("tool_before_shrink", observed.deepCopy());
        dig.row("actual_living_entity_equipment_break_callback", tick, null, null, detail);
    }
    void causeAfter(ItemStack stack, ServerPlayer actor, PassivePhysicalDig dig, long tick) {
        if (!same(actor)) return;
        Cause cause = CAUSE.get();
        if (cause == null || !actorId.equals(cause.actorId()) || cause.stack() != stack || stack != original)
            fail("tool_hurt_and_break_cause_return_unbalanced");
        CAUSE.remove(); causeExits++;
        JsonObject detail = new JsonObject(); detail.addProperty("actual_callback_count", callbacks);
        dig.row("actual_tool_hurt_and_break_consumer_after", tick, null, null, detail);
    }
    void destroyAfter(ServerPlayer actor, PassivePhysicalDig dig, long tick) {
        if (!same(actor)) return;
        if (!destroying || destroyReturned || CAUSE.get() != null || actor.getInventory().selected != selectedSlot)
            fail("tool_destroy_return_cause_or_selected_slot_unbalanced");
        destroying = false; destroyReturned = true;
        afterDestroy = snapshot(actor.getMainHandItem());
        if (!supportedReturn(callbacks, causeEnters, causeExits,
                afterDestroy.get("kind").getAsString(), actor.getMainHandItem() == original))
            fail("tool_callback_count_or_return_presence_unsupported");
        if (callbacks == 0 && actor.getMainHandItem() != original)
            fail("intact_tool_item_object_replaced_during_destroy");
        JsonObject detail = new JsonObject(); detail.addProperty("vanilla_hotbar_slot", selectedSlot);
        detail.add("tool", afterDestroy.deepCopy());
        dig.row("tool_after_actual_gamemode_destroy", tick, null, null, detail);
    }
    /** Mechanical capture ONLY; fully bound extra callbacks are facts, not missing evidence or expected wear math. */
    static boolean supportedReturn(int callbacks, int enters, int exits, String afterKind, boolean sameOriginalObject) {
        if (callbacks == 0) return enters == 0 && exits == 0 && sameOriginalObject &&
                ("nonempty".equals(afterKind) || "empty".equals(afterKind));
        return callbacks >= 1 && callbacks <= 4 && enters >= 1 && enters <= callbacks && exits == enters &&
                ("empty".equals(afterKind) || "nonempty".equals(afterKind) && sameOriginalObject);
    }
    /** Only an actual single scoped, fully conserved pickup may fill the just-broken selected slot. */
    void boundPickup(ServerPlayer actor, UUID entityId, JsonObject source, JsonObject acquired,
                     JsonObject before41, JsonObject after41, PassivePhysicalDig dig, long tick) {
        if (!same(actor)) return;
        if (boundPickupSourceId != null || actor.getInventory().selected != selectedSlot || !destroyReturned ||
                before41 == null || after41 == null || !"bc.playtest_vanilla_inventory_slots.v1".equals(before41.get("schema").getAsString()) ||
                !"bc.playtest_vanilla_inventory_slots.v1".equals(after41.get("schema").getAsString())) {
            fail("tool_pickup_not_unique_bound_actual_vanilla41"); return;
        }
        boundPickupSourceId = entityId;
        pickupSlotsAfter = after41.deepCopy();
        JsonArray before = before41.getAsJsonArray("slots"), after = after41.getAsJsonArray("slots");
        if (before.size() != 41 || after.size() != 41) { fail("tool_pickup_full41_size_unbound"); return; }
        JsonObject priorSlot = before.get(selectedSlot).getAsJsonObject();
        JsonObject postSlot = after.get(selectedSlot).getAsJsonObject();
        boolean changed = !priorSlot.equals(postSlot);
        if (!"empty".equals(afterDestroy.get("kind").getAsString()) || !changed) return;
        selectedRefill = snapshot(actor.getMainHandItem());
        if (!exactSelectedRefill(before41, after41, selectedSlot, source, acquired, selectedRefill)) {
            fail("tool_refill_not_exact_bound_full_source_stack_or_other_slot_changed"); return;
        }
        JsonObject detail = new JsonObject(); detail.addProperty("source_entity_uuid", entityId.toString());
        detail.addProperty("vanilla_hotbar_slot", selectedSlot);
        detail.add("source_stack", source.deepCopy()); detail.add("acquired_stack", acquired.deepCopy());
        detail.add("selected_before", priorSlot.deepCopy()); detail.add("selected_after", postSlot.deepCopy());
        detail.add("selected_live_after", selectedRefill.deepCopy());
        dig.row("tool_selected_slot_refilled_by_actual_source_pickup", tick, null, null, detail);
    }
    /** Same pure bound-source/full41 decision used by the real pickup observer and unrun mechanism regressions. */
    static boolean exactSelectedRefill(JsonObject before41, JsonObject after41, int selected,
                                       JsonObject source, JsonObject acquired, JsonObject liveSelected) {
        if (selected < 0 || selected > 8 || before41 == null || after41 == null ||
                source == null || acquired == null || liveSelected == null ||
                !"bc.playtest_vanilla_inventory_slots.v1".equals(before41.get("schema").getAsString()) ||
                !"bc.playtest_vanilla_inventory_slots.v1".equals(after41.get("schema").getAsString())) return false;
        JsonArray before = before41.getAsJsonArray("slots"), after = after41.getAsJsonArray("slots");
        if (before == null || after == null || before.size() != 41 || after.size() != 41) return false;
        JsonObject prior = before.get(selected).getAsJsonObject(), post = after.get(selected).getAsJsonObject();
        if (!prior.get("empty").getAsBoolean() || post.get("empty").getAsBoolean() ||
                !"nonempty".equals(liveSelected.get("kind").getAsString()) ||
                source.get("count").getAsInt() != acquired.get("count").getAsInt() ||
                !source.get("canonical_sha256").equals(acquired.get("canonical_sha256")) ||
                !source.get("content_sha256").equals(acquired.get("content_sha256")) ||
                post.get("count").getAsInt() != acquired.get("count").getAsInt() ||
                !post.get("stack_canonical_sha256").equals(acquired.get("canonical_sha256")) ||
                !post.get("item").equals(acquired.get("item_id")) ||
                !liveSelected.get("canonical_sha256").equals(acquired.get("canonical_sha256")) ||
                !liveSelected.get("content_sha256").equals(acquired.get("content_sha256"))) return false;
        for (int slot = 0; slot < 41; slot++) if (slot != selected && !before.get(slot).equals(after.get(slot))) return false;
        return true;
    }
    JsonObject await(ServerPlayer actor, PassivePhysicalDig dig, long tick) {
        if (!same(actor) || actor.getInventory().selected != selectedSlot || CAUSE.get() != null)
            fail("tool_await_actor_slot_or_cause_unbound");
        try { atAwait = snapshot(actor.getMainHandItem()); }
        catch (RuntimeException capture) { fail("tool_await_snapshot_failed:" + capture.getClass().getSimpleName()); }
        boolean unchanged = atAwait != null && afterDestroy != null && afterDestroy.equals(atAwait);
        boolean boundRefill = atAwait != null && afterDestroy != null &&
                "empty".equals(afterDestroy.get("kind").getAsString()) && boundPickupSourceId != null &&
                selectedRefill != null && selectedRefill.equals(atAwait) && pickupSlotsAfter != null;
        if (boundRefill) try {
            if (!pickupSlotsAfter.equals(VanillaInventorySlots.capture(actor))) {
                fail("tool_refill_full41_drift_after_bound_pickup"); boundRefill = false;
            }
        } catch (RuntimeException capture) {
            fail("tool_refill_await_full41_capture_failed:" + capture.getClass().getSimpleName()); boundRefill = false;
        }
        if (!unchanged && !boundRefill) fail("tool_changed_or_unbound_refill_after_destroy_before_await");
        JsonObject detail = new JsonObject(); detail.addProperty("vanilla_hotbar_slot", selectedSlot);
        detail.add("tool", atAwait == null ? JsonNull.INSTANCE : atAwait.deepCopy());
        dig.row("tool_at_bound_await", tick, null, null, detail);
        JsonObject result = new JsonObject(); result.addProperty("schema", "bc.playtest_physical_tool_wear.v2");
        result.addProperty("vanilla_hotbar_slot", selectedSlot);
        result.addProperty("expected_interaction_hand", "MAIN_HAND");
        result.add("arm_tool", arm.deepCopy());
        result.add("before_destroy_tool", beforeDestroy == null ? JsonNull.INSTANCE : beforeDestroy.deepCopy());
        result.add("callback_tool_before_shrink", callback == null ? JsonNull.INSTANCE : callback.deepCopy());
        result.add("after_destroy_tool", afterDestroy == null ? JsonNull.INSTANCE : afterDestroy.deepCopy());
        result.add("await_tool", atAwait == null ? JsonNull.INSTANCE : atAwait.deepCopy());
        result.addProperty("selected_slot_refilled_by_bound_source", boundRefill);
        if (boundRefill) result.addProperty("selected_slot_refill_source_entity_uuid", boundPickupSourceId.toString());
        else result.add("selected_slot_refill_source_entity_uuid", JsonNull.INSTANCE);
        result.addProperty("actual_equipment_break_callback_count", callbacks);
        result.addProperty("actual_hurt_and_break_cause_enter_count", causeEnters);
        result.addProperty("actual_hurt_and_break_cause_return_count", causeExits);
        boolean complete = failure == null && destroyReturned && beforeDestroy != null &&
                supportedReturn(callbacks, causeEnters, causeExits,
                        afterDestroy == null ? "missing" : afterDestroy.get("kind").getAsString(),
                        actor.getMainHandItem() == original) && (callbacks == 0 || callback != null);
        result.addProperty("capture_complete", complete);
        if (!complete) result.addProperty("unsupported_reason", failure == null ? "tool_destroy_or_callback_capture_missing" : failure);
        result.addProperty("saved_tool_nbt_proven_by_native", false);
        result.addProperty("natural_tool_origin_proven_by_native", false);
        return result;
    }
    void tickEnd() { if (CAUSE.get() != null) { fail("tool_callback_cause_scope_unbalanced_at_tick_end"); CAUSE.remove(); } }
    void serverStop() { if (CAUSE.get() != null) { fail("tool_callback_cause_scope_unbalanced_at_server_stop"); CAUSE.remove(); } }
}
