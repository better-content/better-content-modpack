package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.ExternalRawEvidenceDriver
import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.RawEvidenceRequest
import com.bettercontent.tests.suite.client.INPUT_RUNTIME_MARKER
import com.bettercontent.tests.suite.client.validateAuthenticBlockAttack
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

/** Producer-backed capture-vs-expectation projection shared by historical v3 and v4 AWAIT. NOT raw-chain, actor, OS-input or natural-origin verification. */
internal fun physicalDigDecisionProjection(
    expectedVariant: String, expectedDrop: String, expectedHarvest: Boolean, decision: JsonNode
): String {
    require(decision.path("observation_complete").isBoolean &&
        decision.path("reviewed_expectation_matches").isBoolean &&
        decision.path("expectation_mismatches").isArray) { "physical packet decision fields missing" }
    val complete = decision.path("observation_complete").asBoolean()
    val mismatches = decision.path("expectation_mismatches").map { it.asText() }
    if (!complete) {
        require(!decision.path("reviewed_expectation_matches").asBoolean() && mismatches.isEmpty() &&
            decision.path("unsupported_reason").asText("").isNotEmpty()) { "invalid incomplete capture decision" }
        return "blocked"
    }
    val actualVariant = decision.path("actual_packet_variant").asText("")
    val actualDrop = decision.path("actual_drop_outcome").asText("")
    require(!decision.has("unsupported_reason") &&
        actualVariant in setOf("instant_start_only", "progress_start_stop") &&
        actualDrop in setOf("one_full_pickup", "no_drop_negative") &&
        decision.path("actual_stored_can_harvest").isBoolean) { "incomplete actual callback facts" }
    val independentlyCompared = buildList {
        if (actualVariant != expectedVariant) add("packet_variant")
        if (actualDrop != expectedDrop) add("drop_outcome")
        if (decision.path("actual_stored_can_harvest").asBoolean() != expectedHarvest) add("harvest_result")
    }
    require(mismatches == independentlyCompared &&
        decision.path("reviewed_expectation_matches").asBoolean() == independentlyCompared.isEmpty()) {
        "decision comparison contradicts actual callback facts"
    }
    return if (mismatches.isEmpty()) "passed" else "failed"
}

/** Independent narrow expectation on an already raw-replayed, B-classified v5 tool capture.
 * The B fixture exercises the production capture classifier, NOT a runtime tool or saved NBT. */
internal fun physicalToolV5DecisionProjection(expectedCallbacks: Int, actualCallbacks: Int,
    afterKind: String, captureComplete: Boolean): String {
    require(expectedCallbacks in 0..1) { "tool callback expectation not independently reviewed" }
    if (!captureComplete) return "blocked"
    require(actualCallbacks in 0..4 && afterKind in setOf("empty", "nonempty")) {
        "complete tool capture lacks bounded actual callback/return facts"
    }
    return if (actualCallbacks == expectedCallbacks &&
        (expectedCallbacks == 0 && afterKind == "nonempty" ||
            expectedCallbacks == 1 && afterKind == "empty")) "passed" else "failed"
}

/** UNREGISTERED diagnostic: real OS attack and existing hitResult + B's frozen v5 inbound packet+actual tool-break/source-refill observer.
 * One bounded mechanic can pass; A still independently replays raw bytes and authenticates natural
 * BoundTarget, acquired tool, OS process/client pairing, provider coverage, controls and persistence.
 * Never call player.dig, player.dig.witness_v1, direct destroyBlock, teleport or callback injection. */
internal object PhysicalMiningControlDriver : CaseDriver, ExternalRawEvidenceDriver {
    override val caseId = "control.mining.physical-single-block"
    private const val BOUND = "@controller-natural-bound-target-sha256"
    private const val CROSSHAIR = "@controller-reviewed-crosshair-sha256"
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private val sha = Regex("[0-9a-f]{64}")
    private val key = Regex("[a-z0-9][a-z0-9_.-]*")
    private fun integer(node: JsonNode, range: IntRange): Boolean =
        node.isIntegralNumber && node.canConvertToInt() && node.asInt() in range
    private fun integer(node: JsonNode, expected: Int): Boolean = integer(node, expected..expected)
    private fun long(node: JsonNode): Boolean = node.isIntegralNumber && node.canConvertToLong()
    private fun flag(node: JsonNode, value: Boolean): Boolean = node.isBoolean && node.asBoolean() == value
    private fun toolSnapshot(row: JsonNode, requireExplicitDamageTag: Boolean = true,
        expectedCount: IntRange = 1..1): Boolean {
        if (!row.isObject) return false
        val names = row.fieldNames().asSequence().toSet()
        if (row.path("kind").asText() == "empty")
            return names == setOf("kind", "count") && integer(row.path("count"), 0)
        return row.path("kind").asText() == "nonempty" && names == setOf("kind", "item_id", "count",
            "canonical_algorithm", "canonical_sha256", "content_algorithm", "content_sha256",
            "damage_live", "damage_nbt_int") && resource.matches(row.path("item_id").asText("")) &&
            integer(row.path("count"), expectedCount) &&
            row.path("canonical_algorithm").asText() == "bc.playtest.stack-nbt.v1" &&
            row.path("content_algorithm").asText() == "bc.playtest.stack-content.v1" &&
            sha.matches(row.path("canonical_sha256").asText("")) &&
            sha.matches(row.path("content_sha256").asText("")) &&
            integer(row.path("damage_live"), 0..1_000_000) &&
            (!requireExplicitDamageTag && row.path("damage_nbt_int").isNull ||
                integer(row.path("damage_nbt_int"), 0..1_000_000))
    }
    private fun actualVanillaFull41(row: JsonNode): List<JsonNode>? {
        if (!row.isObject || row.fieldNames().asSequence().toSet() !=
            setOf("schema", "source", "slots", "external_capabilities_included", "sealed_playerdata_proven_by_native") ||
            row.path("schema").asText() != "bc.playtest_vanilla_inventory_slots.v1" ||
            row.path("source").asText() != "actual_server_player_vanilla_inventory_0_40" ||
            !row.path("external_capabilities_included").isBoolean ||
            row.path("external_capabilities_included").asBoolean() ||
            !row.path("sealed_playerdata_proven_by_native").isBoolean ||
            row.path("sealed_playerdata_proven_by_native").asBoolean() ||
            !row.path("slots").isArray || row.path("slots").size() != 41) return null
        val slots = row.path("slots").toList()
        if (!slots.withIndex().all { (index, slot) ->
            slot.isObject && slot.fieldNames().asSequence().toSet() ==
                setOf("slot", "empty", "item", "count", "stack_canonical_algorithm", "stack_canonical_sha256") &&
                integer(slot.path("slot"), index) &&
                slot.path("empty").isBoolean &&
                (slot.path("empty").asBoolean() && slot.path("item").isNull &&
                    integer(slot.path("count"), 0) &&
                    slot.path("stack_canonical_algorithm").isNull &&
                    slot.path("stack_canonical_sha256").isNull ||
                    !slot.path("empty").asBoolean() && resource.matches(slot.path("item").asText("")) &&
                    integer(slot.path("count"), 1..64) &&
                    slot.path("stack_canonical_algorithm").asText() == "bc.playtest.stack-nbt.v1" &&
                    sha.matches(slot.path("stack_canonical_sha256").asText("")))
        }) return null
        return slots
    }
    private data class Target(val player: String, val dimension: String, val selector: String,
        val x: Int, val y: Int, val z: Int, val face: String, val block: String,
        val toolItem: String, val toolCanonical: String, val acquisition: String,
        val observer: String, val hold: String, val profile: String, val maxTicks: Int,
        val packetVariant: String, val dropOutcome: String, val dropItem: String?, val dropCount: Int,
        val expectedToolCallbacks: Int)
    private fun parse(parameters: JsonNode): Target {
        require(parameters.isObject && parameters.fieldNames().asSequence().toSet() ==
            setOf("player_uuid", "dimension", "selector_id", "x", "y", "z", "face", "block_id",
                "tool_item_id", "tool_canonical_sha256", "tool_acquisition_case_id", "observer_id", "hold_id",
                "input_profile", "max_ticks", "expected_packet_variant", "expected_drop_outcome",
                "expected_drop_item_id", "expected_drop_count", "expected_tool_break_callback_count")) { "physical mining target not independently frozen" }
        fun text(name: String): String {
            val v = parameters.path(name); require(v.isTextual) { "invalid $name" }; return v.asText()
        }
        fun number(name: String, range: IntRange): Int {
            val v = parameters.path(name)
            require(v.isIntegralNumber && v.canConvertToInt() && v.asInt() in range) { "invalid $name" }
            return v.asInt()
        }
        val player = text("player_uuid"); val observer = text("observer_id")
        val dim = text("dimension"); val selector = text("selector_id"); val block = text("block_id")
        val tool = text("tool_item_id"); val canonical = text("tool_canonical_sha256")
        val acquisition = text("tool_acquisition_case_id"); val hold = text("hold_id")
        val face = text("face"); val profile = text("input_profile")
        require(listOf(player, observer).all { runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false) } &&
            listOf(dim, block, tool).all(resource::matches) && block != "minecraft:air" &&
            key.matches(selector) && key.matches(acquisition) && sha.matches(canonical) &&
            Regex("[A-Za-z0-9._-]{1,64}").matches(hold) &&
            face in setOf("north", "south", "east", "west", "up", "down") &&
            profile in setOf("1280x720@2", "1920x1080@3")) { "unsupported unreviewed mining actor/site/tool/input" }
        val packet = text("expected_packet_variant"); val drop = text("expected_drop_outcome")
        val dropItem = parameters.path("expected_drop_item_id").takeIf { it.isTextual }?.asText()
        val dropCount = number("expected_drop_count", 0..64)
        require(packet in setOf("instant_start_only", "progress_start_stop") &&
            drop in setOf("one_full_pickup", "no_drop_negative") &&
            (drop == "one_full_pickup" && dropItem != null && resource.matches(dropItem) && dropCount in 1..64 ||
                drop == "no_drop_negative" && parameters.path("expected_drop_item_id").isNull &&
                dropItem == null && dropCount == 0)) { "reviewed mining variant/drop target incomplete" }
        return Target(player, dim, selector, number("x", -30_000_000..30_000_000), number("y", -2048..2048),
            number("z", -30_000_000..30_000_000), face, block, tool, canonical, acquisition,
            observer, hold, profile, number("max_ticks", 4..160), packet, drop, dropItem, dropCount,
            number("expected_tool_break_callback_count", 0..1))
    }
    override fun requiredAcquisitionCases(parameters: JsonNode): Map<String, String> = parse(parameters).let {
        mapOf(it.toolItem to it.acquisition)
    }
    override fun rawEvidenceRequests(trial: TrialRequest): List<RawEvidenceRequest> {
        require(trial.caseId == caseId && trial.provisionedTargets.isEmpty()) { "foreign mining raw-evidence trial" }
        parse(trial.parameters)
        return listOf(RawEvidenceRequest("await-real-packet-and-source-terminal", "packet_dig",
            "/packet_dig_trace_file", maximumBytes = 16_000_000, maximumRecords = 256,
            maximumLineBytes = 128_000))
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val p = parse(parameters)
        val player = suiteMapper.createObjectNode().put("player_uuid", "@observed-player-uuid")
        val block = suiteMapper.createObjectNode().put("dimension", p.dimension).put("x", p.x).put("y", p.y).put("z", p.z)
        val arm = player.deepCopy().put("dimension", p.dimension).put("observer_id", p.observer)
            .put("selector_id", p.selector).put("expected_x", p.x).put("expected_y", p.y)
            .put("expected_z", p.z).put("expected_face", p.face.uppercase())
            .put("bound_target_sha256", BOUND).put("crosshair_projection_sha256", CROSSHAIR)
            .put("expected_packet_variant", p.packetVariant).put("expected_drop_outcome", p.dropOutcome)
            .put("expected_harvest_result", p.dropOutcome == "one_full_pickup")
            .put("deadline_ticks", p.maxTicks + 36).put("witness_context", "@controller-witness-context-v1")
        val await = player.deepCopy().put("dimension", p.dimension).put("observer_id", p.observer)
            .put("arm_request_sequence", "@arm-request-sequence").put("arm_action_ordinal", "@arm-action-ordinal")
            .put("witness_context", "@controller-witness-context-v1")
        val held = player.deepCopy().put("dimension", p.dimension).put("hold_id", p.hold)
            .put("input_runtime_sha256", INPUT_RUNTIME_MARKER)
        return listOf(
            DriverStep("before-player", DriverAction("player.snapshot", player.deepCopy())),
            DriverStep("before-block", DriverAction("block.snapshot", block.deepCopy())),
            DriverStep("frozen-existing-crosshair", DriverAction("client.crosshair", suiteMapper.createObjectNode(), 200, "client")),
            DriverStep("arm-passive-real-packet", DriverAction("player.observe_dig.arm_v5", arm, 200)),
            DriverStep("os-left-press", DriverAction("client.input_hold", held.deepCopy().put("profile", p.profile)
                .put("kind", "attack_block").put("max_ticks", p.maxTicks)
                .put("crosshair_projection_sha256", CROSSHAIR).put("bound_target_sha256", BOUND), p.maxTicks + 20, "client")),
            DriverStep("os-left-release", DriverAction("client.input_release", held.deepCopy(), 200, "client")),
            DriverStep("os-left-up-status", DriverAction("client.input_status", held.deepCopy(), 200, "client")),
            DriverStep("await-real-packet-and-source-terminal", DriverAction("player.observe_dig.await_v5", await, 200)),
            DriverStep("after-block", DriverAction("block.snapshot", block.deepCopy())),
            DriverStep("after-player", DriverAction("player.snapshot", player.deepCopy())))
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val p = parse(parameters)
        require(validateTerminalSequence(plan(parameters), observations) == null) {
            "missing real-client OS or passive server mining terminal"
        }
        val beforePlayer = observations[0].payload; val beforeBlock = observations[1].payload
        val crosshair = observations[2].payload; val arm = observations[3].payload
        val hold = observations[4].payload; val release = observations[5].payload
        val status = observations[6].payload; val server = observations[7].payload
        val afterBlock = observations[8].payload; val afterPlayer = observations[9].payload
        val actualPacket = server.path("actual_packet_variant").asText("")
        val actualDrop = server.path("actual_drop_outcome").asText("")
        require(actualPacket in setOf("instant_start_only", "progress_start_stop") &&
            actualDrop in setOf("one_full_pickup", "no_drop_negative") &&
            server.path("actual_stored_can_harvest").isBoolean) {
            "actual packet/harvest/drop callback capture missing: not a gameplay contradiction"
        }
        val expectedPackets = if (actualPacket == "instant_start_only") 1 else 2
        val positive = actualDrop == "one_full_pickup"
        val mismatches = buildList {
            if (actualPacket != p.packetVariant) add("packet_variant")
            if (actualDrop != p.dropOutcome) add("drop_outcome")
            if (server.path("actual_stored_can_harvest").asBoolean() != (p.dropOutcome == "one_full_pickup"))
                add("harvest_result")
        }
        require(server.path("reviewed_expectation_matches").isBoolean &&
            server.path("reviewed_expectation_matches").asBoolean() == mismatches.isEmpty() &&
            server.path("expectation_mismatches").isArray &&
            server.path("expectation_mismatches").map { it.asText() } == mismatches) {
            "native comparison metadata differs from actual observed variant/harvest/drop"
        }
        require(listOf(beforePlayer, afterPlayer).all {
            it.path("uuid").asText() == p.player && it.path("dimension").asText() == p.dimension &&
                it.path("inventory").isArray && actualVanillaFull41(it.path("inventory_slots_v1")) != null
        } && listOf(beforeBlock, afterBlock).all { row ->
            row.path("dimension").asText() == p.dimension && listOf("x", "y", "z").zip(listOf(p.x, p.y, p.z))
                .all { (field, value) -> integer(row.path(field), value) } &&
                row.path("block_state").isObject
        } && beforeBlock.path("block_state").path("id").asText() == p.block &&
            afterBlock.path("block_state").path("id").asText() == "minecraft:air" &&
            arm.path("schema").asText() == "bc.playtest_passive_packet_dig_arm.v5" &&
            integer(arm.path("vanilla_tool_hotbar_slot"), 0..8) &&
            beforePlayer.path("selected_slot") == arm.path("vanilla_tool_hotbar_slot") &&
            beforePlayer.path("held_item").asText() == p.toolItem &&
            beforePlayer.path("held_stack_canonical_sha256").asText() == p.toolCanonical &&
            toolSnapshot(arm.path("arm_tool")) &&
            arm.path("arm_tool").path("kind").asText() == "nonempty" &&
            arm.path("arm_tool").path("item_id").asText() == p.toolItem &&
            arm.path("arm_tool").path("canonical_sha256").asText() == p.toolCanonical &&
            arm.path("arm_tool").path("damage_live") == arm.path("arm_tool").path("damage_nbt_int") &&
            arm.path("observer_id").asText() == p.observer && arm.path("actor_uuid").asText() == p.player &&
            arm.path("dimension").asText() == p.dimension && arm.path("selector_id").asText() == p.selector &&
            arm.path("arm_block").path("block_id").asText() == p.block &&
            arm.path("arm_block").path("air").isBoolean && !arm.path("arm_block").path("air").asBoolean() &&
            arm.path("expected_packet_variant").asText() == p.packetVariant &&
            arm.path("expected_drop_outcome").asText() == p.dropOutcome &&
            arm.path("expected_harvest_result").isBoolean && arm.path("expected_harvest_result").asBoolean() == (p.dropOutcome == "one_full_pickup") &&
            arm.path("natural_origin_proven_by_native").isBoolean && !arm.path("natural_origin_proven_by_native").asBoolean() &&
            arm.path("bound_target_sha256").asText() == hold.path("attack_bound_target_sha256_unverified_by_client").asText() &&
            arm.path("crosshair_projection_sha256").asText() == crosshair.path("crosshair_projection_sha256").asText() &&
            long(arm.path("arm_request_sequence")) && arm.path("arm_request_sequence").asLong() > 0 &&
            integer(arm.path("arm_action_ordinal"), 1..Int.MAX_VALUE) && long(arm.path("deadline_tick")) &&
            long(arm.path("arm_block").path("server_tick")) &&
            arm.path("arm_block").path("server_tick").asLong() in 0..(Long.MAX_VALUE - p.maxTicks - 36L) &&
            arm.path("deadline_tick").asLong() == arm.path("arm_block").path("server_tick").asLong() + p.maxTicks + 36 &&
            server.path("schema").asText() == "bc.playtest_passive_packet_dig.v5" &&
            server.path("observer_id").asText() == p.observer && server.path("actor_uuid").asText() == p.player &&
            server.path("dimension").asText() == p.dimension && server.path("selector_id").asText() == p.selector &&
            listOf("arm_request_sequence", "arm_action_ordinal", "bound_target_sha256", "crosshair_projection_sha256",
                "expected_packet_variant", "expected_drop_outcome", "expected_harvest_result", "deadline_tick",
                "controller_declared_arm_context").all { server.path(it) == arm.path(it) } &&
            server.path("observation_complete").isBoolean && server.path("observation_complete").asBoolean() &&
            !server.has("unsupported_reason") &&
            server.path("tool_wear_v2").path("schema").asText() == "bc.playtest_physical_tool_wear.v2" &&
            server.path("tool_wear_v2").path("vanilla_hotbar_slot") == arm.path("vanilla_tool_hotbar_slot") &&
            server.path("tool_wear_v2").path("expected_interaction_hand").asText() == "MAIN_HAND" &&
            server.path("tool_wear_v2").path("arm_tool") == arm.path("arm_tool") &&
            flag(server.path("tool_wear_v2").path("capture_complete"), true) &&
            !server.path("tool_wear_v2").has("unsupported_reason") &&
            listOf("saved_tool_nbt_proven_by_native", "natural_tool_origin_proven_by_native").all {
                server.path("tool_wear_v2").path(it).isBoolean &&
                    !server.path("tool_wear_v2").path(it).asBoolean()
            } &&
            integer(server.path("actual_start_progress_observed_count"), 1) &&
            server.path("actual_START_destroy_progress_per_tick").isNumber &&
            server.path("actual_START_destroy_progress_per_tick").asDouble().isFinite() &&
            (server.path("actual_START_destroy_progress_per_tick").asDouble() >= 1.0) == (expectedPackets == 1) &&
            server.path("actual_START_destroy_progress_per_tick").asDouble() > 0.0 &&
            integer(server.path("actual_stored_harvest_observed_count"), 1) &&
            server.path("actual_stored_can_harvest").isBoolean &&
            listOf("inbound_before_count", "inbound_after_count", "game_mode_call_count")
                .all { integer(server.path(it), expectedPackets) } &&
            listOf("destroy_call_count", "break_event_count").all { integer(server.path(it), 1) } &&
            integer(server.path("aborts"), 0) &&
            integer(server.path("start_packet_sequence"), 0..Int.MAX_VALUE) &&
            (expectedPackets == 1 && integer(server.path("stop_packet_sequence"), -1) &&
                long(server.path("stop_tick")) && server.path("stop_tick").asLong() == -1L ||
                expectedPackets == 2 && integer(server.path("stop_packet_sequence"), 1..Int.MAX_VALUE) &&
                server.path("stop_packet_sequence").asInt() > server.path("start_packet_sequence").asInt() &&
                long(server.path("stop_tick")) && server.path("stop_tick").asLong() >= server.path("start_tick").asLong()) &&
            listOf("arm_tick", "start_tick", "terminal_tick").all { long(server.path(it)) } &&
            server.path("arm_tick").asLong() <= server.path("start_tick").asLong() &&
            server.path("start_tick").asLong() <= server.path("terminal_tick").asLong() &&
            server.path("terminal_tick").asLong() <= arm.path("deadline_tick").asLong() &&
            server.path("arm_block") == arm.path("arm_block") &&
            flag(server.path("block_after").path("air"), true) &&
            server.path("acquisition_origin").asText() == "preexisting_unattributed" &&
            listOf("packet_os_origin_proven_by_native", "natural_acquisition_proven_by_native",
                "natural_origin_proven_by_native", "negative_scope_proven_by_native",
                "full_primitive_provider_coverage_proven_by_native").all {
                server.path(it).isBoolean && !server.path(it).asBoolean() } &&
            validateAuthenticBlockAttack(crosshair, hold, release, status, p.player, p.dimension, p.profile,
                p.hold, p.x, p.y, p.z, p.face, hold.path("attack_bound_target_sha256_unverified_by_client").asText(),
                hold.path("input_runtime_sha256").asText())) { "missing/incomplete real packet, actor or OS key-up capture" }
        val wear = server.path("tool_wear_v2")
        val beforeTool = wear.path("before_destroy_tool")
        val afterTool = wear.path("after_destroy_tool")
        val atAwait = wear.path("await_tool")
        val callbackTool = wear.path("callback_tool_before_shrink")
        val callbackNode = wear.path("actual_equipment_break_callback_count")
        val causeNode = wear.path("actual_hurt_and_break_cause_enter_count")
        require(integer(callbackNode, 0..4) && integer(causeNode, 0..4)) { "tool cause/callback count not integral" }
        val callbackCount = callbackNode.asInt()
        val causeCount = causeNode.asInt()
        val refilled = wear.path("selected_slot_refilled_by_bound_source").asBoolean(false)
        val refillSource = wear.path("selected_slot_refill_source_entity_uuid")
        require(wear.path("selected_slot_refilled_by_bound_source").isBoolean &&
            toolSnapshot(beforeTool) && beforeTool == arm.path("arm_tool") &&
            toolSnapshot(afterTool) && toolSnapshot(atAwait, requireExplicitDamageTag = !refilled,
                expectedCount = if (refilled) 1..64 else 1..1) &&
            afterPlayer.path("selected_slot") == arm.path("vanilla_tool_hotbar_slot") &&
            callbackCount in 0..4 && causeCount in (if (callbackCount == 0) 0..0 else 1..callbackCount) &&
            integer(wear.path("actual_hurt_and_break_cause_return_count"), causeCount) &&
            (callbackCount == 0 && callbackTool.isNull ||
                callbackCount in 1..4 && toolSnapshot(callbackTool) &&
                callbackTool.path("item_id").asText() == p.toolItem) &&
            (refilled && positive && afterTool.path("kind").asText() == "empty" &&
                atAwait.path("kind").asText() == "nonempty" &&
                runCatching { UUID.fromString(refillSource.asText()).toString() == refillSource.asText() }
                    .getOrDefault(false) ||
                !refilled && refillSource.isNull && afterTool == atAwait) &&
            (atAwait.path("kind").asText() == "empty" &&
                afterPlayer.path("held_item").asText() == "minecraft:air" &&
                integer(afterPlayer.path("held_count"), 0) ||
                atAwait.path("kind").asText() == "nonempty" &&
                afterPlayer.path("held_item") == atAwait.path("item_id") &&
                afterPlayer.path("held_count").isIntegralNumber &&
                afterPlayer.path("held_count") == atAwait.path("count") &&
                afterPlayer.path("held_stack_canonical_sha256") == atAwait.path("canonical_sha256"))) {
            "real bound MAINHAND tool callback/return or post-await source capture incomplete"
        }
        val pointer = server.path("packet_dig_trace_file")
        require(pointer.path("schema").asText() == "bc.playtest_passive_packet_dig_trace.v5" &&
            pointer.path("arm_request_sequence") == arm.path("arm_request_sequence") &&
            pointer.path("path").asText() == "packet-dig/${arm.path("arm_request_sequence").asLong().toString().padStart(12, '0')}.jsonl" &&
            long(pointer.path("bytes")) && pointer.path("bytes").asLong() in 1L..16_000_000L &&
            integer(pointer.path("records"), 1..256) &&
            sha.matches(pointer.path("sha256").asText("")) &&
            pointer.path("actual_native_session_id").asText("").isNotEmpty() &&
            pointer.path("natural_acquisition_proven_by_native").isBoolean &&
            !pointer.path("natural_acquisition_proven_by_native").asBoolean()) { "unbound packet dig raw sidecar pointer" }
        val raw = observations[7].rawEvidence["packet_dig"]
        require(raw != null && raw.file.sha256 == pointer.path("sha256").asText() &&
            raw.records.size == pointer.path("records").asInt()) { "verified raw packet-dig bytes absent" }
        val rows = raw.records
        require(rows.withIndex().all { (index, row) ->
            row.path("schema").asText() == "bc.playtest_passive_packet_dig_trace.v5" &&
            integer(row.path("event_index"), index + 1) && row.path("actual_actor_uuid").asText() == p.player &&
            row.path("actual_native_session_id") == pointer.path("actual_native_session_id") &&
            row.path("arm_request_sequence") == arm.path("arm_request_sequence") &&
            row.path("actual_capability_id").asText() == "player.observe_dig.arm_v5" &&
            row.path("bound_target_sha256") == arm.path("bound_target_sha256") &&
            row.path("crosshair_projection_sha256") == arm.path("crosshair_projection_sha256") &&
            row.path("controller_declared_arm_context") == arm.path("controller_declared_arm_context") &&
            row.path("context_certified_by_native").isBoolean && !row.path("context_certified_by_native").asBoolean() &&
            integer(row.path("x"), p.x) && integer(row.path("y"), p.y) &&
            integer(row.path("z"), p.z) && row.path("face").asText() == p.face.uppercase() &&
            long(row.path("tick")) && row.path("tick").asLong() in
                arm.path("arm_block").path("server_tick").asLong()..server.path("terminal_tick").asLong() &&
            (index == 0 && row.path("prior_row_sha256").isNull ||
                index > 0 && sha.matches(row.path("prior_row_sha256").asText("")))
        } && rows.map { it.path("tick").asLong() }.zipWithNext().all { (a, b) -> b >= a }) {
            "raw packet rows unbound/unsorted; A must independently verify exact SHA chain"
        }
        fun count(kind: String) = rows.count { it.path("kind").asText() == kind }
        val shared = mapOf("decoded_remote_packet_then_actual_server_handler" to expectedPackets,
            "real_inbound_packet_after_gamemode" to expectedPackets, "actual_gamemode_handle" to expectedPackets,
            "actual_START_destroy_progress_return" to 1, "actual_gamemode_stored_harvest_return" to 1,
            "actual_gamemode_destroy_head" to 1, "actual_gamemode_destroy_return" to 1,
            "actual_forge_break_event" to 1)
        val sourceKinds = if (positive) listOf("source_entity_join_before_insertion", "actual_pickup_pre",
            "actual_pickup_after_inventory_add", "actual_source_terminal_after_real_pickup_discard")
            else listOf("no_scoped_packet_path_drop_through_deadline")
        val toolKinds = listOf("tool_before_actual_gamemode_destroy", "tool_after_actual_gamemode_destroy",
            "tool_at_bound_await", "packet_path_observation_terminal")
        val callbackKinds = if (callbackCount > 0) listOf("actual_tool_hurt_and_break_consumer_before",
            "actual_living_entity_equipment_break_callback", "actual_tool_hurt_and_break_consumer_after")
            else emptyList()
        require((shared.keys + sourceKinds + toolKinds + callbackKinds +
            if (refilled) listOf("tool_selected_slot_refilled_by_actual_source_pickup") else emptyList()).toSet() ==
                rows.map { it.path("kind").asText() }.toSet() &&
            shared.all { (kind, expected) -> count(kind) == expected } &&
            (sourceKinds + toolKinds).all { count(it) == 1 } &&
            (!refilled || count("tool_selected_slot_refilled_by_actual_source_pickup") == 1) &&
            (callbackCount == 0 ||
                count("actual_tool_hurt_and_break_consumer_before") == causeCount &&
                count("actual_living_entity_equipment_break_callback") == callbackCount &&
                count("actual_tool_hurt_and_break_consumer_after") == causeCount) &&
            rows.size >= 3 && rows[rows.lastIndex - 2].path("kind").asText() == sourceKinds.last() &&
            rows[rows.lastIndex - 2].path("tick") == server.path("terminal_tick") &&
            rows[rows.lastIndex - 2].path("observed") == server.path("source_terminal") &&
            rows[rows.lastIndex - 1].path("kind").asText() == "tool_at_bound_await" &&
            rows.last().path("kind").asText() == "packet_path_observation_terminal" &&
            rows.last().path("observed").let {
                flag(it.path("physical_observation_complete"), true) &&
                    flag(it.path("tool_capture_complete"), true) &&
                    it.path("reviewed_expectation_matches") == server.path("reviewed_expectation_matches")
            }) { "incomplete/unscoped packet, tool callback or drop/pickup terminal raw chain" }
        fun ordered(kind: String) = rows.filter { it.path("kind").asText() == kind }
        fun at(kind: String) = rows.indexOfFirst { it.path("kind").asText() == kind }
        val callbackRows = ordered("actual_living_entity_equipment_break_callback")
        val causeBeforeRows = ordered("actual_tool_hurt_and_break_consumer_before")
        val causeAfterRows = ordered("actual_tool_hurt_and_break_consumer_after")
        val causeWindows = causeBeforeRows.zip(causeAfterRows).map { (start, end) ->
            rows.indexOf(start)..rows.indexOf(end)
        }
        require(at("tool_before_actual_gamemode_destroy") < at("actual_gamemode_destroy_head") &&
            at("actual_gamemode_destroy_head") < at("tool_after_actual_gamemode_destroy") &&
            at("tool_after_actual_gamemode_destroy") < at("actual_gamemode_destroy_return") &&
            at("actual_gamemode_destroy_return") < at("tool_at_bound_await") &&
            ordered("tool_before_actual_gamemode_destroy").single().path("observed").let {
                it.path("vanilla_hotbar_slot") == arm.path("vanilla_tool_hotbar_slot") &&
                    it.path("tool") == beforeTool
            } && ordered("tool_after_actual_gamemode_destroy").single().path("observed").let {
                it.path("vanilla_hotbar_slot") == arm.path("vanilla_tool_hotbar_slot") &&
                    it.path("tool") == afterTool
            } && ordered("tool_at_bound_await").single().path("observed").let {
                it.path("vanilla_hotbar_slot") == arm.path("vanilla_tool_hotbar_slot") &&
                    it.path("tool") == atAwait
            } && causeWindows.all { window ->
                window.first > at("actual_gamemode_destroy_head") &&
                    window.last < at("tool_after_actual_gamemode_destroy") && window.first < window.last
            } && causeWindows.zipWithNext().all { (left, right) -> left.last < right.first } &&
            causeBeforeRows.all { row ->
                flag(row.path("observed").path("same_selected_item_object"), true) &&
                    toolSnapshot(row.path("observed").path("tool"))
            } && callbackRows.withIndex().all { (index, row) ->
                val event = row.path("observed")
                integer(event.path("tool_callback_index"), index + 1) &&
                    event.path("equipment_slot").asText() == "MAINHAND" &&
                    event.path("interaction_hand").asText() == "MAIN_HAND" &&
                    event.path("vanilla_hotbar_slot") == arm.path("vanilla_tool_hotbar_slot") &&
                    flag(event.path("same_hurt_and_break_item_object"), true) &&
                    toolSnapshot(event.path("tool_before_shrink")) &&
                    causeWindows.any { rows.indexOf(row) in (it.first + 1) until it.last } &&
                    (index != 0 || event.path("tool_before_shrink") == callbackTool)
            } && (callbackCount == 0 && causeAfterRows.isEmpty() || callbackCount > 0 &&
                causeAfterRows.lastOrNull()?.path("observed")?.path("actual_callback_count")
                    ?.let { integer(it, callbackCount) } == true)) {
            "real original selected ItemStack, callback cause/MAINHAND identity or v5 event ordering unproven"
        }
        val actions = if (expectedPackets == 1) listOf("START_DESTROY_BLOCK")
            else listOf("START_DESTROY_BLOCK", "STOP_DESTROY_BLOCK")
        val packetSequence = if (expectedPackets == 1) listOf(server.path("start_packet_sequence").asInt())
            else listOf(server.path("start_packet_sequence").asInt(), server.path("stop_packet_sequence").asInt())
        require(ordered("decoded_remote_packet_then_actual_server_handler").all { row ->
            flag(row.path("observed").path("same_decoded_packet_object"), true) &&
                flag(row.path("observed").path("bound_actual_remote_connection"), true) &&
                long(row.path("observed").path("decoded_ingress_monotonic_nanos")) &&
                row.path("observed").path("decoded_ingress_thread_name").asText("").isNotEmpty()
        } && listOf("decoded_remote_packet_then_actual_server_handler", "real_inbound_packet_after_gamemode",
            "actual_gamemode_handle").all { kind ->
                ordered(kind).map { it.path("packet_action").asText() } == actions &&
                    ordered(kind).zip(packetSequence).all { (row, sequence) ->
                        integer(row.path("packet_sequence"), sequence) }
            } && ordered("actual_START_destroy_progress_return").single().path("observed").let {
                flag(it.path("direct_gamemode_START_caller"), true) &&
                    it.path("actual_progress_per_tick").isNumber &&
                    it.path("actual_progress_per_tick").asDouble() ==
                    server.path("actual_START_destroy_progress_per_tick").asDouble()
            } && ordered("actual_gamemode_stored_harvest_return").single().path("observed")
                .path("actual_stored_can_harvest") == server.path("actual_stored_can_harvest") &&
            ordered("actual_gamemode_destroy_return").single().path("observed") == server.path("block_after") &&
            ordered("actual_forge_break_event").single().path("observed") == arm.path("arm_block")) {
            "raw packet/progress/stored harvest/BreakEvent contradict terminal callback witness"
        }
        val reviewedDropMatches = if (positive) {
            val sourceRow = rows.single { it.path("kind").asText() == sourceKinds.first() }.path("observed")
            val source = sourceRow.path("source_stack")
            val acquired = rows.single { it.path("kind").asText() == "actual_pickup_after_inventory_add" }
                .path("observed").path("acquired_stack")
            val pickupPre = rows.single { it.path("kind").asText() == "actual_pickup_pre" }.path("observed")
            val pickupPost = rows.single { it.path("kind").asText() == "actual_pickup_after_inventory_add" }.path("observed")
            val pre41 = actualVanillaFull41(pickupPre.path("vanilla_inventory_slots_before_v1"))
            val post41 = actualVanillaFull41(pickupPost.path("vanilla_inventory_slots_after_v1"))
            val slot = arm.path("vanilla_tool_hotbar_slot").asInt()
            require(pre41 != null && post41 != null &&
                pickupPost.path("vanilla_inventory_slots_before_v1") ==
                    pickupPre.path("vanilla_inventory_slots_before_v1") &&
                afterPlayer.path("inventory_slots_v1") == pickupPost.path("vanilla_inventory_slots_after_v1") &&
                (!refilled || flag(pre41[slot].path("empty"), true) &&
                    flag(post41[slot].path("empty"), false) &&
                    (0..40).filter { it != slot }.all { pre41[it] == post41[it] })) {
                "actual live full41 pickup pre/post identity missing or unbound selected refill"
            }
            if (refilled) {
                val refill = rows.single { it.path("kind").asText() ==
                    "tool_selected_slot_refilled_by_actual_source_pickup" }.path("observed")
                require(rows.indexOfFirst { it.path("kind").asText() == "actual_pickup_after_inventory_add" } <
                    rows.indexOfFirst { it.path("kind").asText() == "tool_selected_slot_refilled_by_actual_source_pickup" } &&
                    refill.path("source_entity_uuid") == sourceRow.path("item_entity_uuid") &&
                    refill.path("source_entity_uuid") == refillSource &&
                    refill.path("vanilla_hotbar_slot") == arm.path("vanilla_tool_hotbar_slot") &&
                    refill.path("source_stack") == source && refill.path("acquired_stack") == acquired &&
                    refill.path("selected_before") == pre41[slot] &&
                    refill.path("selected_after") == post41[slot] &&
                    refill.path("selected_live_after") == atAwait &&
                    source == acquired && afterTool.path("kind").asText() == "empty" &&
                    post41[slot].path("item") == source.path("item_id") &&
                    post41[slot].path("count") == source.path("count") &&
                    post41[slot].path("stack_canonical_sha256") == source.path("canonical_sha256") &&
                    atAwait.path("content_sha256") == source.path("content_sha256")) {
                    "broken selected slot not refilled by exactly the same full actual scoped acquired stack"
                }
            }
            require(sourceRow.path("item_entity_uuid").asText("").isNotEmpty() &&
                pickupPre.path("item_entity_uuid") == sourceRow.path("item_entity_uuid") &&
                pickupPost.path("item_entity_uuid") == sourceRow.path("item_entity_uuid") &&
                pickupPre.path("source_before") == source &&
                pickupPre.path("inventory_before").isArray && pickupPost.path("inventory_before").isArray &&
                pickupPre.path("inventory_before") == pickupPost.path("inventory_before") &&
                pickupPost.path("inventory_after").isArray &&
                pickupPost.path("source_after_inventory_add").path("kind").asText() == "empty" &&
                integer(source.path("count"), 1..64) &&
                source.path("canonical_algorithm").asText() == "bc.playtest.stack-nbt.v1" &&
                source.path("content_algorithm").asText() == "bc.playtest.stack-content.v1" &&
                source.path("provider_scope").asText() ==
                    "vanilla_saved_ItemStack_NBT_including_ForgeCaps_not_external_inventory_adapter" &&
                sha.matches(source.path("canonical_sha256").asText("")) &&
                sha.matches(source.path("content_sha256").asText("")) && acquired == source &&
                server.path("source_terminal").path("source_kind").asText() == "absent" &&
                flag(server.path("source_terminal").path("removed"), true) &&
                server.path("source_terminal").path("in_world").isBoolean &&
                !server.path("source_terminal").path("in_world").asBoolean() &&
                (p.dropOutcome != "no_drop_negative" ||
                    server.path("terminal_tick") == arm.path("deadline_tick"))) {
                "raw source/pickup conservation or unexpected-drop full deadline incomplete"
            }
            source.path("item_id").asText() == p.dropItem && source.path("count").asInt() == p.dropCount
        } else {
            require(server.path("terminal_tick") == arm.path("deadline_tick") &&
                server.path("source_terminal").path("source_kind").asText() == "no_scoped_drop_in_observed_interval" &&
                server.path("source_terminal").path("observed_through_deadline_tick") == arm.path("deadline_tick")) {
                "negative drop absence not observed through full arm deadline"
            }
            true
        }
        require(physicalDigDecisionProjection(p.packetVariant, p.dropOutcome,
            p.dropOutcome == "one_full_pickup", server) != "blocked") { "incomplete physical packet capture" }
        val callbackDecision = physicalToolV5DecisionProjection(p.expectedToolCallbacks, callbackCount,
            afterTool.path("kind").asText(), wear.path("capture_complete").asBoolean())
        require(callbackDecision != "blocked") { "tool capture incomplete, not a gameplay failure" }
        val callbackMatches = callbackDecision == "passed" &&
            (p.expectedToolCallbacks != 0 || !refilled && afterTool.path("item_id").asText() == p.toolItem)
        val outcome = mismatches.isEmpty() && reviewedDropMatches && callbackMatches
        // Only the bounded mechanism verdict. A independently authenticates the SHA chain, origin,
        // real host OS→client/server chronology, tool ancestry and selected provider controls.
        return MeasurementVerdict(if (outcome) "passed" else "failed",
            if (outcome) "one bounded physical packet/tool callback/drop diagnostic observed; saved wear, natural origin and full primitive coverage NOT asserted"
            else "complete bounded physical mining diagnostic contradicted reviewed packet, stored harvest, drop or bound callback expectation",
            mapOf("diagnostic_only_not_natural_mining" to true,
                "actual_packet_variant" to actualPacket, "actual_drop_outcome" to actualDrop,
                "actual_tool_break_callback_count" to callbackCount, "selected_slot_refilled_by_bound_source" to refilled,
                "reviewed_expectation_mismatches" to mismatches,
                "packet_trace_sha256" to raw.file.sha256))
    }
}
