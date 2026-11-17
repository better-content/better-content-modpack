package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded read-only source→pickup→terminal causal facts; neither empty nor absence gets an invented stack hash. */
final class NativeAcquisitionTrace {
    static final int MAX_ROWS = 256, MAX_ROW_BYTES = 128 * 1024, MAX_BYTES = 16 * 1024 * 1024;
    private final NativeWitnessContext context;
    private final List<String> rows = new ArrayList<>();
    private final Map<UUID, Drop> sources = new LinkedHashMap<>();
    private final Map<UUID, BeforePickup> pending = new HashMap<>();
    private final Map<UUID, JsonObject> expectedSourceState = new HashMap<>();
    private final Map<UUID, Integer> acquiredCounts = new HashMap<>();
    private final Map<UUID, String> lastPickupHashes = new HashMap<>();
    private final Map<UUID, JsonObject> terminalStates = new HashMap<>();
    private JsonArray lastActorInventoryAfter;
    private int bytes;
    private String unsupportedReason;
    private boolean terminalFinalized;
    private record Drop(String hash, JsonObject stack, ItemEntity entity, String dimension) {}
    private record BeforePickup(String hash, JsonObject sourceStack, JsonArray inventory) {}

    NativeAcquisitionTrace(NativeWitnessContext context) { this.context = context; }
    boolean complete() {
        return unsupportedReason == null && terminalFinalized && !sources.isEmpty() && pending.isEmpty() &&
                sources.keySet().stream().allMatch(id -> acquiredCounts.containsKey(id) && terminalStates.containsKey(id) &&
                        fullyConsumedAtTerminal(sources.get(id).stack(), acquiredCounts.get(id),
                                expectedSourceState.get(id), terminalStates.get(id)));
    }
    String unsupportedReason() { return unsupportedReason; }
    List<String> rows() { return List.copyOf(rows); }
    void unsupported(String reason) { if (unsupportedReason == null) unsupportedReason = reason; }

    void drop(ItemEntity entity, BlockPos source, String blockId, String blockState, boolean accepted, long tick) {
        try {
            JsonObject row = event("block_drop", tick, entity.getUUID());
            row.addProperty("source_provider", "Block.popResource(Level,BlockPos,ItemStack)");
            row.addProperty("source_block_id", blockId); row.addProperty("source_block_state", blockState);
            row.addProperty("source_x", source.getX()); row.addProperty("source_y", source.getY());
            row.addProperty("source_z", source.getZ()); row.addProperty("entity_join_accepted", accepted);
            row.addProperty("join_phase", "EntityJoinLevelEvent.MONITOR_before_actual_level_insertion");
            JsonObject original = stack(entity.getItem()); row.add("source_stack", original.deepCopy());
            String hash = append(row);
            if (!accepted) { unsupported("scoped_drop_entity_join_canceled"); return; }
            Drop drop = new Drop(hash, original, entity, entity.level().dimension().location().toString());
            if (sources.putIfAbsent(entity.getUUID(), drop) != null) {
                unsupported("duplicate_drop_entity_uuid"); return;
            }
            expectedSourceState.put(entity.getUUID(), present(original));
        } catch (Exception error) { unsupported("drop_capture:" + error.getClass().getSimpleName()); }
    }

    void pickupBefore(ServerPlayer actor, ItemEntity entity, long tick, boolean canceled) {
        try {
            Drop source = sources.get(entity.getUUID());
            if (source == null) { unsupported("pickup_without_scoped_source"); return; }
            if (canceled) { unsupported("pickup_pre_event_canceled"); return; }
            if (pending.containsKey(entity.getUUID())) { unsupported("duplicate_pickup_pre_event"); return; }
            if (!source.dimension().equals(actor.serverLevel().dimension().location().toString()) ||
                    source.entity() != entity || entity.isRemoved() ||
                    actor.serverLevel().getEntity(entity.getUUID()) != entity) {
                unsupported("scoped_entity_not_actually_inserted_before_pickup"); return;
            }
            JsonObject observed = stack(entity.getItem());
            JsonObject prior = expectedSourceState.get(entity.getUUID());
            if (prior == null || !"nonempty".equals(prior.get("kind").getAsString()) ||
                    !sameExactStack(observed, prior.getAsJsonObject("stack"))) {
                unsupported("source_stack_changed_without_observed_pickup_transition"); return;
            }
            JsonArray inventory = inventory(actor);
            if (lastActorInventoryAfter != null && !lastActorInventoryAfter.equals(inventory))
                unsupported("actor_vanilla_inventory_changed_between_observed_pickups");
            JsonObject row = event("pickup_pre", tick, entity.getUUID());
            row.addProperty("source_event_sha256", source.hash());
            row.addProperty("prior_pickup_post_event_sha256", lastPickupHashes.get(entity.getUUID()));
            row.addProperty("entity_inserted_in_world", true);
            row.add("source_stack", observed.deepCopy()); row.add("inventory_before", inventory.deepCopy());
            pending.put(entity.getUUID(), new BeforePickup(append(row), observed, inventory));
        } catch (Exception error) { unsupported("pickup_pre_capture:" + error.getClass().getSimpleName()); }
    }

    void pickupAfter(ServerPlayer actor, ItemEntity entity, ItemStack acquired, long tick) {
        try {
            Drop source = sources.get(entity.getUUID());
            BeforePickup before = pending.remove(entity.getUUID());
            if (source == null || before == null) { unsupported("pickup_post_without_scoped_pre_and_source"); return; }
            JsonObject acquiredStack = stack(acquired), sourceAfter = observedSourceAfterInventoryAdd(entity);
            JsonArray inventory = inventory(actor);
            boolean sameContent = sameContent(acquiredStack, before.sourceStack());
            boolean sourceConserved = sourceTransition(before.sourceStack(), acquiredStack, sourceAfter);
            JsonObject row = event("pickup_post", tick, entity.getUUID());
            row.addProperty("source_event_sha256", source.hash());
            row.addProperty("pickup_pre_event_sha256", before.hash());
            row.addProperty("source_state_phase", "PlayerEvent.ItemPickupEvent.MONITOR_after_Inventory.add_before_ItemEntity.discard_or_stack_reset");
            row.add("acquired_stack", acquiredStack);
            row.add("source_after_inventory_add", sourceAfter.deepCopy());
            row.add("inventory_before", before.inventory().deepCopy()); row.add("inventory_after", inventory);
            row.addProperty("inventory_vanilla_count_conserved", conserved(before.inventory(), inventory,
                    acquiredStack.get("content_sha256").getAsString(), acquiredStack.get("count").getAsInt()));
            row.addProperty("source_stack_content_matches", sameContent);
            row.addProperty("source_count_conserved", sourceConserved);
            String hash = append(row);
            lastPickupHashes.put(entity.getUUID(), hash);
            expectedSourceState.put(entity.getUUID(), sourceAfter.deepCopy());
            lastActorInventoryAfter = inventory.deepCopy();
            int cumulative = acquiredCounts.merge(entity.getUUID(), acquiredStack.get("count").getAsInt(), Math::addExact);
            if (!sameContent || !sourceConserved || cumulative > source.stack().get("count").getAsInt() ||
                    !row.get("inventory_vanilla_count_conserved").getAsBoolean())
                unsupported("pickup_source_or_vanilla_inventory_conservation_unproven");
        } catch (Exception error) { unsupported("pickup_post_capture:" + error.getClass().getSimpleName()); }
    }

    /** Observe after vanilla discard/reset at terminal tick, not the reset ItemStack of a removed entity. */
    void terminalCheck(ServerPlayer actor, long tick) {
        if (terminalFinalized) return;
        terminalFinalized = true;
        for (Map.Entry<UUID, Drop> entry : sources.entrySet()) {
            UUID id = entry.getKey(); Drop source = entry.getValue();
            try {
                boolean inserted = actor.serverLevel().getEntity(id) == source.entity();
                boolean removed = source.entity().isRemoved();
                JsonObject terminal;
                if (removed && !inserted) terminal = absent();
                else if (!removed && inserted && !source.entity().getItem().isEmpty())
                    terminal = present(stack(source.entity().getItem()));
                else {
                    terminal = new JsonObject(); terminal.addProperty("kind", "unresolved");
                    unsupported("terminal_entity_insertion_or_removal_ambiguous");
                }
                JsonObject prior = expectedSourceState.get(id);
                boolean continuous = terminalContinuity(prior, terminal);
                JsonObject row = event("source_terminal", tick, id);
                row.addProperty("source_event_sha256", source.hash());
                if (lastPickupHashes.containsKey(id)) row.addProperty("last_pickup_post_event_sha256", lastPickupHashes.get(id));
                row.addProperty("terminal_phase", "ServerTickEvent.END_after_ItemEntity.playerTouch_discard_or_partial_remain");
                row.addProperty("entity_removed", removed); row.addProperty("entity_in_world", inserted);
                row.addProperty("removed_entity_stack_reset_ignored", removed);
                row.add("source_terminal_state", terminal.deepCopy());
                row.addProperty("source_state_continuous", continuous);
                append(row); terminalStates.put(id, terminal);
                if (!continuous) unsupported("source_terminal_drift_or_unobserved_removal");
                if ("nonempty".equals(terminal.get("kind").getAsString()))
                    unsupported("partial_source_remainder_requires_sealed_accounting_adapter");
                if (!acquiredCounts.containsKey(id)) unsupported("scoped_drop_not_acquired_by_bound_actor");
            } catch (Exception error) { unsupported("source_terminal_capture:" + error.getClass().getSimpleName()); }
        }
    }

    /** Nonempty is the only stack hashed under canonical v1. Empty/absent have no invented digest. */
    static JsonObject present(JsonObject stack) {
        JsonObject row = new JsonObject(); row.addProperty("kind", "nonempty"); row.add("stack", stack.deepCopy()); return row;
    }
    static JsonObject empty() { JsonObject row = new JsonObject(); row.addProperty("kind", "empty"); row.addProperty("count", 0); return row; }
    static JsonObject absent() { JsonObject row = new JsonObject(); row.addProperty("kind", "absent"); return row; }
    static int count(JsonObject state) {
        return switch (state.get("kind").getAsString()) {
            case "nonempty" -> state.getAsJsonObject("stack").get("count").getAsInt();
            case "empty" -> 0;
            default -> throw new IllegalArgumentException("non-present source has no pickup count");
        };
    }
    static boolean sameContent(JsonObject a, JsonObject b) {
        return a.get("item_id").equals(b.get("item_id")) &&
                a.get("content_sha256").equals(b.get("content_sha256")) &&
                a.get("content_algorithm").equals(b.get("content_algorithm")) &&
                a.get("provider_scope").equals(b.get("provider_scope"));
    }
    static boolean sameExactStack(JsonObject a, JsonObject b) {
        return sameContent(a, b) && a.get("count").equals(b.get("count")) &&
                a.get("canonical_sha256").equals(b.get("canonical_sha256")) &&
                a.get("canonical_algorithm").equals(b.get("canonical_algorithm"));
    }
    static boolean terminalContinuity(JsonObject expected, JsonObject actual) {
        if (expected == null || actual == null) return false;
        String kind = expected.get("kind").getAsString(), actualKind = actual.get("kind").getAsString();
        return kind.equals("empty") ? actualKind.equals("absent") :
                kind.equals("nonempty") && actualKind.equals("nonempty") &&
                sameExactStack(expected.getAsJsonObject("stack"), actual.getAsJsonObject("stack"));
    }
    static boolean fullyConsumedAtTerminal(JsonObject source, Integer acquired, JsonObject expected, JsonObject terminal) {
        return acquired != null && source.get("count").getAsInt() == acquired &&
                expected != null && "empty".equals(expected.get("kind").getAsString()) &&
                terminal != null && "absent".equals(terminal.get("kind").getAsString()) &&
                terminalContinuity(expected, terminal);
    }
    static boolean sourceTransition(JsonObject before, JsonObject acquired, JsonObject after) {
        if (!sameContent(before, acquired) || acquired.get("count").getAsInt() <= 0) return false;
        long expected = (long) before.get("count").getAsInt() - acquired.get("count").getAsInt();
        if (expected < 0) return false;
        if (expected == 0) return "empty".equals(after.get("kind").getAsString()) &&
                after.get("count").getAsInt() == 0 && !after.has("stack");
        return "nonempty".equals(after.get("kind").getAsString()) &&
                count(after) == expected && sameContent(before, after.getAsJsonObject("stack"));
    }
    static boolean conserved(JsonArray before, JsonArray after, String acquiredContent, int acquiredCount) {
        if (acquiredCount <= 0 || !acquiredContent.matches("[0-9a-f]{64}")) return false;
        try {
            Map<String, Long> prior = totals(before), later = totals(after);
            prior.merge(acquiredContent, (long) acquiredCount, Long::sum);
            return prior.equals(later);
        } catch (RuntimeException malformed) { return false; }
    }
    private static Map<String, Long> totals(JsonArray inventory) {
        Map<String, Long> totals = new HashMap<>(); java.util.Set<Integer> slots = new java.util.HashSet<>();
        for (var value : inventory) {
            JsonObject row = value.getAsJsonObject();
            int slot = row.get("slot").getAsInt(), count = row.get("count").getAsInt();
            String content = row.get("content_sha256").getAsString();
            if (slot < 0 || slot >= 64 || !slots.add(slot) || count <= 0 || !content.matches("[0-9a-f]{64}"))
                throw new IllegalStateException("invalid inventory slot/count/content identity");
            totals.merge(content, (long) count, Long::sum);
        }
        return totals;
    }
    private static JsonObject observedSourceAfterInventoryAdd(ItemEntity entity) {
        ItemStack after = entity.getItem();
        if (after.isEmpty()) {
            if (after.getCount() != 0) throw new IllegalStateException("empty source stack has invalid count");
            return empty();
        }
        return present(stack(after));
    }
    private JsonObject event(String kind, long tick, UUID entity) {
        JsonObject row = new JsonObject(); row.addProperty("schema", NativeWitnessContext.TRACE_SCHEMA);
        row.addProperty("event_index", rows.size() + 1); row.addProperty("kind", kind);
        row.addProperty("tick", tick); row.addProperty("item_entity_uuid", entity.toString());
        row.add("controller_declared_context", context.declared.deepCopy());
        row.addProperty("actual_native_session_id", context.actualSession);
        row.addProperty("actual_case_id", context.actualCase);
        row.addProperty("actual_request_sequence", context.actualSequence);
        row.addProperty("actual_actor_uuid", context.actualActor.toString());
        row.addProperty("actual_capability_id", NativeWitnessContext.DRIVER);
        row.addProperty("bound_target_sha256", context.boundTargetSha256);
        row.addProperty("context_certified_by_native", false);
        return row;
    }
    private String append(JsonObject row) throws NoSuchAlgorithmException {
        String text = row.toString(); byte[] encoded = (text + "\n").getBytes(StandardCharsets.UTF_8);
        if (rows.size() >= MAX_ROWS || encoded.length > MAX_ROW_BYTES || bytes + encoded.length > MAX_BYTES)
            throw new IllegalStateException("acquisition trace row/file cap exceeded");
        rows.add(text); bytes += encoded.length; return eventDigest(text);
    }
    /** Reference hashes exact prior UTF-8 JSONL bytes, including LF, never the referencing row. */
    static String eventDigest(String priorRow) throws NoSuchAlgorithmException {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((priorRow + "\n").getBytes(StandardCharsets.UTF_8)));
    }
    static JsonObject stack(ItemStack original) {
        if (original == null || original.isEmpty() || original.getCount() <= 0)
            throw new IllegalStateException("nonempty exact stack required");
        ItemStack value = original.copy(); CompoundTag saved = value.save(new CompoundTag());
        String item = ForgeRegistries.ITEMS.getKey(value.getItem()).toString();
        JsonObject row = new JsonObject(); row.addProperty("item_id", item);
        row.addProperty("count", value.getCount());
        row.addProperty("canonical_algorithm", CanonicalStackIdentity.ALGORITHM);
        row.addProperty("canonical_sha256", CanonicalStackIdentity.sha256(saved));
        row.addProperty("content_algorithm", CanonicalStackIdentity.CONTENT_ALGORITHM);
        row.addProperty("content_sha256", CanonicalStackIdentity.contentSha256(saved));
        row.addProperty("provider_scope", "vanilla_saved_ItemStack_NBT_including_ForgeCaps_not_external_inventory_adapter");
        return row;
    }
    static JsonArray inventory(ServerPlayer actor) {
        int size = actor.getInventory().getContainerSize();
        if (size < 1 || size > 64) throw new IllegalStateException("vanilla inventory slot budget invalid");
        JsonArray rows = new JsonArray();
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = actor.getInventory().getItem(slot);
            if (item.isEmpty()) continue;
            JsonObject row = stack(item); row.addProperty("slot", slot); rows.add(row);
        }
        return rows;
    }
}
