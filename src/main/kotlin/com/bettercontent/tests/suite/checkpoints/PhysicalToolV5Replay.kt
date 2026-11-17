package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Locale

/** Tool/pickup slice only. It does NOT verify OS input, real inbound packet, natural origin or the whole case. */
internal data class ReplayedPhysicalToolV5(val tool: ReplayedPhysicalToolBreak,
    val selectedRefill: ReplayedPhysicalSelectedPickup?)

private fun fields(node: JsonNode, keys: Set<String>) {
    require(node.isObject && node.fieldNames().asSequence().toSet() == keys)
}
private fun digest(bytes: ByteArray): String = HexFormat.of().formatHex(
    MessageDigest.getInstance("SHA-256").digest(bytes))
private fun full41(row: JsonNode): List<JsonNode> {
    fields(row, setOf("schema", "source", "slots", "external_capabilities_included",
        "sealed_playerdata_proven_by_native"))
    require(row.path("schema").asText() == "bc.playtest_vanilla_inventory_slots.v1" &&
        row.path("source").asText() == "actual_server_player_vanilla_inventory_0_40" &&
        row.path("external_capabilities_included").isBoolean &&
        !row.path("external_capabilities_included").asBoolean() &&
        row.path("sealed_playerdata_proven_by_native").isBoolean &&
        !row.path("sealed_playerdata_proven_by_native").asBoolean() &&
        row.path("slots").isArray && row.path("slots").size() == 41)
    return row.path("slots").mapIndexed { index, slot ->
        fields(slot, setOf("slot", "empty", "item", "count",
            "stack_canonical_algorithm", "stack_canonical_sha256"))
        require(slot.path("slot").isIntegralNumber && slot.path("slot").asInt() == index &&
            slot.path("empty").isBoolean && slot.path("count").isIntegralNumber)
        if (slot.path("empty").asBoolean()) require(slot.path("item").isNull &&
            slot.path("count").asInt(-1) == 0 && slot.path("stack_canonical_algorithm").isNull &&
            slot.path("stack_canonical_sha256").isNull)
        else require(slot.path("item").isTextual &&
            slot.path("item").asText().matches(resourcePattern) &&
            slot.path("count").asInt(-1) in 1..64 &&
            slot.path("stack_canonical_algorithm").asText() == STACK_NBT_HASH_ALGORITHM &&
            slot.path("stack_canonical_sha256").asText().matches(shaPattern))
        slot
    }
}

/** SHA every ORIGINAL JSON+LF byte line, not reserialized JSON or native success flags. */
private data class PhysicalRows(val records: List<JsonNode>, val byteCount: Int)
private fun physicalRows(ref: BoundFile): PhysicalRows {
    val bytes = capturedEvidenceBytes(ref, 16_000_000)
    require(bytes.last() == '\n'.code.toByte())
    val rows = mutableListOf<JsonNode>()
    var prior: String? = null
    var from = 0
    for (to in bytes.indices) if (bytes[to] == '\n'.code.toByte()) {
        require(rows.size < 256 && to > from && to - from <= 128_000)
        val row = strictCapturedJson(bytes.copyOfRange(from, to))
        require(row.path("schema").asText() == "bc.playtest_passive_packet_dig_trace.v5" &&
            row.path("event_index").isIntegralNumber && row.path("event_index").asInt() == rows.size + 1 &&
            (if (prior == null) row.path("prior_row_sha256").isNull
             else row.path("prior_row_sha256").asText() == prior)) {
            "physical v5 raw row order/prior exact-byte SHA link was broken"
        }
        prior = digest(bytes.copyOfRange(from, to + 1))
        rows.add(row)
        from = to + 1
    }
    require(from == bytes.size && rows.isNotEmpty())
    return PhysicalRows(rows, bytes.size)
}

/**
 * A saved parent/child transition and a bounded SHA-linked real physical v5 raw sidecar.
 * The caller/qualifier MUST additionally bind arm/await to their exact native request/response
 * stream, controller target/OS input and reviewed candidate; this replay alone never qualifies.
 */
internal fun replayPhysicalV5ToolSlice(transition: SealedVanillaToolTransition,
    epoch: SessionEpoch, arm: JsonNode, terminal: JsonNode, trace: BoundFile): ReplayedPhysicalToolV5 {
    val evidence = Path.of(epoch.eventLog.path).toAbsolutePath().normalize().parent
    val closure = requireNotNull(epoch.closure)
    require(epoch.inputCheckpoint == transition.beforeCheckpoint &&
        closure.epochId == epoch.id && closure.nativeSessionId == epoch.nativeSessionId &&
        closure.processCleanupComplete && closure.saveValidated &&
        closure.survivingPids.isEmpty() &&
        transition.afterCheckpoint.id != transition.beforeCheckpoint.id &&
        Path.of(trace.path).parent == evidence.resolve("native/packet-dig") &&
        trace.sha256.matches(shaPattern) &&
        arm.path("schema").asText() == "bc.playtest_passive_packet_dig_arm.v5" &&
        terminal.path("schema").asText() == "bc.playtest_passive_packet_dig.v5" &&
        arm.path("actor_uuid").asText() == transition.actorUuid &&
        terminal.path("actor_uuid") == arm.path("actor_uuid") &&
        arm.path("vanilla_tool_hotbar_slot").asInt(-1) == transition.toolSlot &&
        terminal.path("arm_request_sequence") == arm.path("arm_request_sequence") &&
        terminal.path("observer_id") == arm.path("observer_id") &&
        terminal.path("bound_target_sha256") == arm.path("bound_target_sha256") &&
        terminal.path("crosshair_projection_sha256") == arm.path("crosshair_projection_sha256") &&
        arm.path("controller_declared_arm_context").path("native_session_id").asText() == epoch.nativeSessionId &&
        terminal.path("controller_declared_arm_context") == arm.path("controller_declared_arm_context") &&
        terminal.path("observation_complete").isBoolean &&
        terminal.path("observation_complete").asBoolean() &&
        !terminal.has("unsupported_reason")) {
        "physical tool replay lacks a closed same-actor v5 epoch and bounded sidecar"
    }
    val before = transition.before.inventory.vanillaSlot(transition.toolSlot) as? SealedVanillaSlot.Nonempty
        ?: error("same-captured sealed parent selected tool absent")
    val initial = arm.path("arm_tool")
    require(initial.path("kind").asText() == "nonempty" &&
        initial.path("item_id").asText() == before.stack.itemId &&
        initial.path("count").asInt(-1) == before.stack.count &&
        initial.path("canonical_algorithm").asText() == STACK_NBT_HASH_ALGORITHM &&
        initial.path("canonical_sha256").asText() == before.stack.canonicalStackSha256 &&
        initial.path("content_algorithm").asText() == STACK_CONTENT_HASH_ALGORITHM &&
        initial.path("content_sha256").asText() == before.stack.contentSha256)
    val wear = terminal.path("tool_wear_v2")
    require(wear.path("schema").asText() == "bc.playtest_physical_tool_wear.v2" &&
        wear.path("vanilla_hotbar_slot").asInt(-1) == transition.toolSlot &&
        wear.path("expected_interaction_hand").asText() == "MAIN_HAND" &&
        wear.path("arm_tool") == initial && wear.path("before_destroy_tool") == initial &&
        wear.path("capture_complete").isBoolean && wear.path("capture_complete").asBoolean() &&
        !wear.has("unsupported_reason") &&
        wear.path("saved_tool_nbt_proven_by_native").isBoolean &&
        !wear.path("saved_tool_nbt_proven_by_native").asBoolean() &&
        wear.path("natural_tool_origin_proven_by_native").isBoolean &&
        !wear.path("natural_tool_origin_proven_by_native").asBoolean())
    val callbacks = wear.path("actual_equipment_break_callback_count").asInt(-1)
    val enter = wear.path("actual_hurt_and_break_cause_enter_count").asInt(-1)
    val returned = wear.path("actual_hurt_and_break_cause_return_count").asInt(-1)
    require(callbacks in 0..4 && enter == callbacks && returned == callbacks)
    val captured = physicalRows(trace)
    val rows = captured.records
    val armSequence = arm.path("arm_request_sequence").asLong(-1)
    val pointer = terminal.path("packet_dig_trace_file")
    fields(pointer, setOf("schema", "path", "bytes", "sha256", "records",
        "arm_request_sequence", "actual_native_session_id", "natural_acquisition_proven_by_native"))
    require(armSequence > 0 && pointer.path("schema").asText() ==
        "bc.playtest_passive_packet_dig_trace.v5" &&
        pointer.path("path").asText() == String.format(Locale.ROOT, "packet-dig/%012d.jsonl", armSequence) &&
        evidence.resolve("native").resolve(pointer.path("path").asText()).toString() == trace.path &&
        pointer.path("bytes").asInt(-1) == captured.byteCount &&
        pointer.path("records").asInt(-1) == rows.size &&
        pointer.path("sha256").asText() == trace.sha256 &&
        pointer.path("arm_request_sequence").asLong(-1) == armSequence &&
        pointer.path("actual_native_session_id").asText() == epoch.nativeSessionId &&
        pointer.path("natural_acquisition_proven_by_native").isBoolean &&
        !pointer.path("natural_acquisition_proven_by_native").asBoolean() && rows.all { row ->
        row.path("actual_native_session_id").asText() == epoch.nativeSessionId &&
            row.path("controller_declared_arm_context") == arm.path("controller_declared_arm_context") &&
            row.path("x") == arm.path("arm_block").path("x") &&
            row.path("y") == arm.path("arm_block").path("y") &&
            row.path("z") == arm.path("arm_block").path("z") &&
            row.path("face").asText() in setOf("UP", "DOWN", "NORTH", "SOUTH", "EAST", "WEST") &&
            row.path("actual_actor_uuid").asText() == transition.actorUuid &&
            row.path("arm_request_sequence").asLong(-1) == armSequence &&
            row.path("actual_capability_id").asText() == "player.observe_dig.arm_v5" &&
            row.path("bound_target_sha256") == arm.path("bound_target_sha256") &&
            row.path("crosshair_projection_sha256") == arm.path("crosshair_projection_sha256") &&
            row.path("context_certified_by_native").isBoolean &&
            !row.path("context_certified_by_native").asBoolean()
    })
    fun one(kind: String): JsonNode = rows.filter { it.path("kind").asText() == kind }.single()
    val pre = one("tool_before_actual_gamemode_destroy")
    val post = one("tool_after_actual_gamemode_destroy")
    val awaited = one("tool_at_bound_await")
    val end = rows.last()
    require(pre.path("observed").path("vanilla_hotbar_slot").asInt(-1) == transition.toolSlot &&
        pre.path("observed").path("tool") == initial &&
        post.path("observed").path("vanilla_hotbar_slot").asInt(-1) == transition.toolSlot &&
        post.path("observed").path("tool") == wear.path("after_destroy_tool") &&
        awaited.path("observed").path("vanilla_hotbar_slot").asInt(-1) == transition.toolSlot &&
        awaited.path("observed").path("tool") == wear.path("await_tool") &&
        pre.path("event_index").asInt() < one("actual_gamemode_destroy_head").path("event_index").asInt() &&
        post.path("event_index").asInt() < one("actual_gamemode_destroy_return").path("event_index").asInt() &&
        awaited.path("event_index").asInt() < end.path("event_index").asInt() &&
        end.path("kind").asText() == "packet_path_observation_terminal" &&
        end.path("observed").path("tool_capture_complete").asBoolean(false) &&
        end.path("observed").path("physical_observation_complete").asBoolean(false))
    val callbackRows = rows.filter { it.path("kind").asText() == "actual_living_entity_equipment_break_callback" }
    val causeStarts = rows.filter { it.path("kind").asText() == "actual_tool_hurt_and_break_consumer_before" }
    val causeReturns = rows.filter { it.path("kind").asText() == "actual_tool_hurt_and_break_consumer_after" }
    require(callbackRows.size == callbacks && causeStarts.size == callbacks && causeReturns.size == callbacks &&
        (callbacks == 0 || wear.path("callback_tool_before_shrink") ==
            callbackRows.first().path("observed").path("tool_before_shrink")) &&
        callbackRows.allIndexedWithCause(causeStarts, causeReturns, pre.path("event_index").asInt(),
            post.path("event_index").asInt(), transition.toolSlot))
    val destroyedEmpty = wear.path("after_destroy_tool").let {
        it.path("kind").asText() == "empty" && it.fieldNames().asSequence().toSet() == setOf("kind", "count") &&
            it.path("count").asInt(-1) == 0
    }
    val refilled = wear.path("selected_slot_refilled_by_bound_source").asBoolean(false)
    val sourceId = wear.path("selected_slot_refill_source_entity_uuid").takeIf { it.isTextual }?.asText()
    require(wear.path("selected_slot_refilled_by_bound_source").isBoolean &&
        (refilled == (sourceId != null)) && (!refilled || destroyedEmpty))
    val tool = ReplayedPhysicalToolBreak(transition.actorUuid, "MAIN_HAND", transition.toolSlot,
        before.stack.itemId, before.stack.canonicalStackSha256, callbacks, enter, returned,
        callbackRows.all { it.path("observed").path("same_hurt_and_break_item_object").asBoolean(false) },
        destroyedEmpty, true, trace, sourceId)
    val pickup = if (!refilled) null else replaySelectedRefill(rows, transition, trace, requireNotNull(sourceId))
    return ReplayedPhysicalToolV5(tool, pickup)
}

private fun List<JsonNode>.allIndexedWithCause(starts: List<JsonNode>, ends: List<JsonNode>,
    destroyStart: Int, destroyEnd: Int, slot: Int): Boolean = indices.all { index ->
    val begin = starts[index]; val callback = this[index]; val end = ends[index]
    val a = begin.path("event_index").asInt(-1); val b = callback.path("event_index").asInt(-1)
    val c = end.path("event_index").asInt(-1)
    destroyStart < a && a < b && b < c && c < destroyEnd &&
        begin.path("observed").path("same_selected_item_object").asBoolean(false) &&
        callback.path("observed").path("equipment_slot").asText() == "MAINHAND" &&
        callback.path("observed").path("interaction_hand").asText() == "MAIN_HAND" &&
        callback.path("observed").path("vanilla_hotbar_slot").asInt(-1) == slot &&
        callback.path("observed").path("same_hurt_and_break_item_object").asBoolean(false) &&
        end.path("observed").path("actual_callback_count").asInt(-1) == index + 1
}

private fun replaySelectedRefill(rows: List<JsonNode>, transition: SealedVanillaToolTransition,
    trace: BoundFile, sourceId: String): ReplayedPhysicalSelectedPickup {
    val slot = transition.toolSlot
    require(transition.outputSlot == slot)
    fun one(kind: String) = rows.filter { it.path("kind").asText() == kind }.single()
    val pickupPre = one("actual_pickup_pre")
    val pickupPost = one("actual_pickup_after_inventory_add")
    val refill = one("tool_selected_slot_refilled_by_actual_source_pickup")
    val absent = one("actual_source_terminal_after_real_pickup_discard")
    val before = full41(pickupPre.path("observed").path("vanilla_inventory_slots_before_v1"))
    val replayBefore = full41(pickupPost.path("observed").path("vanilla_inventory_slots_before_v1"))
    val after = full41(pickupPost.path("observed").path("vanilla_inventory_slots_after_v1"))
    val source = refill.path("observed").path("source_stack")
    val acquired = refill.path("observed").path("acquired_stack")
    val selectedBefore = before[slot]
    val selectedAfter = after[slot]
    val saved = transition.after.inventory.vanillaSlot(slot) as? SealedVanillaSlot.Nonempty
        ?: error("actual refill did not survive same-captured sealed child selected slot")
    require(pickupPre.path("event_index").asInt() < pickupPost.path("event_index").asInt() &&
        pickupPost.path("event_index").asInt() < refill.path("event_index").asInt() &&
        refill.path("event_index").asInt() < absent.path("event_index").asInt() &&
        before == replayBefore && before.indices.filter { it != slot }.all { before[it] == after[it] } &&
        refill.path("observed").path("source_entity_uuid").asText() == sourceId &&
        pickupPre.path("observed").path("item_entity_uuid").asText() == sourceId &&
        pickupPost.path("observed").path("item_entity_uuid").asText() == sourceId &&
        refill.path("observed").path("vanilla_hotbar_slot").asInt(-1) == slot &&
        refill.path("observed").path("selected_before") == selectedBefore &&
        refill.path("observed").path("selected_after") == selectedAfter &&
        selectedBefore.path("empty").asBoolean(false) &&
        !selectedAfter.path("empty").asBoolean(true) &&
        selectedAfter.path("item").asText() == acquired.path("item_id").asText() &&
        selectedAfter.path("count").asInt(-1) == acquired.path("count").asInt(-1) &&
        selectedAfter.path("stack_canonical_sha256").asText() == acquired.path("canonical_sha256").asText() &&
        source == acquired && acquired == pickupPost.path("observed").path("acquired_stack") &&
        refill.path("observed").path("selected_live_after").path("canonical_sha256") ==
            selectedAfter.path("stack_canonical_sha256") &&
        absent.path("observed").path("source_kind").asText() == "absent" &&
        absent.path("observed").path("removed").asBoolean(false) &&
        !absent.path("observed").path("in_world").asBoolean(true) &&
        saved.stack.itemId == acquired.path("item_id").asText() &&
        saved.stack.count == acquired.path("count").asInt(-1) &&
        saved.stack.canonicalStackSha256 == acquired.path("canonical_sha256").asText() &&
        saved.stack.contentSha256 == acquired.path("content_sha256").asText()) {
        "actual real selected refill/full41/source terminal did not join saved picked-up item"
    }
    return ReplayedPhysicalSelectedPickup(transition.actorUuid, slot, sourceId, slot,
        saved.stack.itemId, saved.stack.count, saved.stack.canonicalStackSha256,
        saved.stack.contentSha256, true, saved.stack.canonicalStackSha256,
        true, true, saved.stack, trace)
}
