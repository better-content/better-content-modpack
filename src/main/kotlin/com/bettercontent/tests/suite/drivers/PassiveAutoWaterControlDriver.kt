package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

/** UNREGISTERED: one naturally scheduled provider callback, source *already* in Curios water slot0.
 * This observes a one-bottle/empty-bottle-slot route, not acquisition, backpack, overflow, purity,
 * or source-paid transaction qualification. No use/debit/sip/Curios mutation is invoked by this driver. */
internal object PassiveAutoWaterControlDriver : CaseDriver {
    override val caseId = "control.water.passive-auto-single-bottle"
    private val sha = Regex("[0-9a-f]{64}")
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private data class Target(val player: String, val dimension: String, val observer: String,
                              val provider: String, val source: String)
    private fun parse(p: JsonNode): Target {
        require(p.isObject && p.fieldNames().asSequence().toSet() == setOf("player_uuid", "dimension", "observer_id",
            "provider_artifact_sha256", "source_stack_canonical_sha256")) { "closed automatic-water target required" }
        fun uuid(s: String) = runCatching { UUID.fromString(s).toString() == s }.getOrDefault(false)
        val t = Target(p.path("player_uuid").asText(""), p.path("dimension").asText(""),
            p.path("observer_id").asText(""), p.path("provider_artifact_sha256").asText(""),
            p.path("source_stack_canonical_sha256").asText(""))
        require(uuid(t.player) && uuid(t.observer) && resource.matches(t.dimension) && sha.matches(t.provider) &&
            sha.matches(t.source)) { "unreviewed automatic-water actor/provider/source" }
        return t
    }
    private fun actor(t: Target) = suiteMapper.createObjectNode().put("player_uuid", "@observed-player-uuid")
        .put("dimension", t.dimension).put("provider_artifact_sha256", t.provider)
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val t = parse(parameters)
        return listOf(
            DriverStep("before", DriverAction("player.water_auto_snapshot_v1", actor(t))),
            DriverStep("arm", DriverAction("player.water_auto_observe.arm_v1", actor(t)
                .put("observer_id", t.observer).put("source_stack_canonical_sha256", t.source)
                .put("source_count", 1).put("deadline_ticks", 30)
                .put("witness_context", "@controller-witness-context-v1"), 80)),
            DriverStep("await-first-actual-provider-callback", DriverAction("player.water_auto_observe.await_v1",
                suiteMapper.createObjectNode().put("observer_id", t.observer)
                    .put("arm_request_sequence", "@arm-request-sequence")
                    .put("arm_action_ordinal", "@arm-action-ordinal")
                    .put("player_uuid", "@observed-player-uuid").put("dimension", t.dimension)
                    .put("witness_context", "@controller-witness-context-v1"), 80)),
            DriverStep("after", DriverAction("player.water_auto_snapshot_v1", actor(t))))
    }
    private fun slots(snapshot: JsonNode, key: String, kind: String, mandatory: Boolean): List<JsonNode> {
        val group = snapshot.path(key)
        require(group.isObject && group.path("kind").asText() == kind && group.path("handler_present").isBoolean &&
            group.path("slots").isArray && group.path("slots").size() <= 16 &&
            (!mandatory || group.path("handler_present").asBoolean()) &&
            (group.path("handler_present").asBoolean() || group.path("slots").size() == 0)) {
            "actual bounded Curios $kind handler missing"
        }
        val entries = group.path("slots").toList()
        entries.forEachIndexed { index, entry -> cell(entry, index) }
        return entries
    }
    private fun cell(row: JsonNode, index: Int) {
        require(row.isObject && row.path("slot").isIntegralNumber && row.path("slot").asInt() == index &&
            row.path("present").isBoolean && row.path("count").isIntegralNumber) { "unordered typed inventory slot" }
        if (row.path("present").asBoolean()) require(row.path("count").asInt() in 1..64 &&
            resource.matches(row.path("id").asText("")) &&
            sha.matches(row.path("stack_canonical_sha256").asText("")) &&
            sha.matches(row.path("stack_content_sha256").asText(""))) { "untyped occupied stack" }
        else require(row.path("count").asInt() == 0 && row.path("id").isNull &&
            row.path("stack_canonical_sha256").isNull && row.path("stack_content_sha256").isNull) {
            "empty slot must not fabricate an item/hash"
        }
    }
    private fun snapshot(row: JsonNode, t: Target): Pair<List<JsonNode>, List<JsonNode>> {
        require(row.path("schema").asText() == "bc.playtest_water_auto_snapshot.v1" &&
            row.path("player_uuid").asText() == t.player && row.path("dimension").asText() == t.dimension &&
            row.path("provider_artifact_sha256").asText() == t.provider &&
            row.path("server_tick").isIntegralNumber && row.path("server_tick").asLong() >= 0 &&
            row.path("player_tick_count").isIntegralNumber && row.path("player_tick_count").asLong() >= 0 &&
            row.path("server_monotonic_nanos").isIntegralNumber &&
            row.path("thirst").isIntegralNumber && row.path("thirst").asInt() in 0..20 &&
            row.path("quenched").isIntegralNumber && row.path("quenched").asInt() in 0..128 &&
            row.path("backpack_contents_complete").isBoolean && !row.path("backpack_contents_complete").asBoolean() &&
            row.path("untracked_dropped_vessel_entities_complete").isBoolean &&
            !row.path("untracked_dropped_vessel_entities_complete").asBoolean() &&
            row.path("source_acquisition_proven_by_native").isBoolean &&
            !row.path("source_acquisition_proven_by_native").asBoolean()) { "unsupported auto-water snapshot/claim" }
        val water = slots(row, "water_slots", "water", true)
        val empties = slots(row, "empty_bottle_slots", "empty_bottle", false)
        slots(row, "better_backpack_slots", "better_backpack", false)
        val vanilla = row.path("vanilla_inventory")
        require(water.isNotEmpty() && empties.isNotEmpty() && vanilla.isArray && vanilla.size() == 41 &&
            row.path("reserve").path("present").isBoolean) { "explicit bottle destination/full41/reserve unavailable" }
        vanilla.forEachIndexed { index, entry -> cell(entry, index) }
        return water to empties
    }
    private fun sameState(a: JsonNode, b: JsonNode): Boolean =
        listOf("thirst", "quenched", "water_slots", "empty_bottle_slots", "better_backpack_slots",
            "vanilla_inventory", "reserve").all { key -> a.path(key) == b.path(key) }
    /** Strict bounded callback replay, still intentionally NOT a natural-hydration pass. */
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val t = parse(parameters)
        require(observations.size == 4 && observations.map { it.driver } ==
            listOf("player.water_auto_snapshot_v1", "player.water_auto_observe.arm_v1",
                "player.water_auto_observe.await_v1", "player.water_auto_snapshot_v1") &&
            observations.all { it.source == "server" && it.kind == "completed" }) { "four completed passive steps required" }
        val before = observations[0].payload; val arm = observations[1].payload
        val receipt = observations[2].payload; val after = observations[3].payload
        val armState = arm.path("arm_snapshot"); val callbackBefore = receipt.path("callback_before")
        val callbackAfter = receipt.path("callback_after"); val receiptArm = receipt.path("arm_snapshot")
        listOf(before, armState, callbackBefore, callbackAfter, receiptArm, after).forEach { snapshot(it, t) }
        require(arm.path("schema").asText() == "bc.playtest_passive_water_auto_arm.v1" &&
            receipt.path("schema").asText() == "bc.playtest_passive_water_auto.v1" &&
            arm.path("observer_id").asText() == t.observer && receipt.path("observer_id").asText() == t.observer &&
            arm.path("provider_artifact_sha256").asText() == t.provider &&
            receipt.path("provider_artifact_sha256").asText() == t.provider &&
            receipt.path("source_stack_canonical_sha256").asText() == t.source &&
            receipt.path("source_count").asInt(-1) == 1 &&
            arm.path("armed_before_provider_tick").asBoolean(false) &&
            receipt.path("eligible_provider_callback_count").asInt(-1) == 1 &&
            receipt.path("observation_complete").asBoolean(false) && !receipt.has("unsupported_reason") &&
            receipt.path("operation_id").isNull &&
            listOf(arm, receipt).all { row -> row.path("natural_hydration_proven_by_native").isBoolean &&
                !row.path("natural_hydration_proven_by_native").asBoolean() } &&
            receipt.path("source_acquisition_proven_by_native").isBoolean &&
            !receipt.path("source_acquisition_proven_by_native").asBoolean() &&
            receipt.path("backpack_or_overflow_destination_proven_by_native").isBoolean &&
            !receipt.path("backpack_or_overflow_destination_proven_by_native").asBoolean() &&
            arm.path("arm_request_sequence").isIntegralNumber && arm.path("arm_action_ordinal").isIntegralNumber &&
            receipt.path("arm_request_sequence") == arm.path("arm_request_sequence") &&
            receipt.path("arm_action_ordinal") == arm.path("arm_action_ordinal") &&
            receiptArm == armState && sameState(before, armState) && sameState(armState, callbackBefore) &&
            sameState(callbackAfter, after) &&
            before.path("server_tick").asLong() <= armState.path("server_tick").asLong() &&
            armState.path("server_tick").asLong() <= callbackBefore.path("server_tick").asLong() &&
            callbackBefore.path("server_tick") == callbackAfter.path("server_tick") &&
            callbackAfter.path("server_tick").asLong() <= after.path("server_tick").asLong() &&
            receipt.path("callback_tick").isIntegralNumber && receipt.path("callback_tick").asLong() >= 0) {
            "actual single bound callback/source chronology incomplete"
        }
        val (sourceBefore, emptiesBefore) = snapshot(callbackBefore, t)
        val (sourceAfter, emptiesAfter) = snapshot(callbackAfter, t)
        require(sourceBefore[0].path("id").asText() == "minecraft:potion" &&
            sourceBefore[0].path("count").asInt(-1) == 1 &&
            sourceBefore[0].path("stack_canonical_sha256").asText() == t.source &&
            sourceBefore.drop(1) == sourceAfter.drop(1) &&
            !callbackBefore.path("reserve").path("present").asBoolean(true) &&
            !emptiesBefore[0].path("present").asBoolean(true) &&
            emptiesBefore.drop(1) == emptiesAfter.drop(1) &&
            callbackBefore.path("better_backpack_slots") == callbackAfter.path("better_backpack_slots") &&
            callbackBefore.path("vanilla_inventory") == callbackAfter.path("vanilla_inventory")) {
            "actor/source/unsupported destination changed outside one reviewed provider callback"
        }
        // The diagnostic asserts ONLY the observed single-bottle mechanic. A separate preflight/qualifier
        // must authenticate the acquired filled source and provider/Curios semantics before gameplay use.
        val correct = !sourceAfter[0].path("present").asBoolean(true) &&
            callbackAfter.path("thirst").asInt() > callbackBefore.path("thirst").asInt() &&
            emptiesAfter[0].path("id").asText() == "minecraft:glass_bottle" &&
            emptiesAfter[0].path("count").asInt(-1) == 1
        return MeasurementVerdict(if (correct) "passed" else "failed",
            if (correct) "bounded single real provider callback: one source debit, Thirst gain, one Curios empty-bottle-slot0 destination; natural source/purity/persistence NOT asserted"
            else "bounded automatic-water mechanism contradicted: source debit, Thirst gain or exclusive empty-bottle-slot0 destination differs",
            mapOf("diagnostic_only_not_natural_hydration" to true,
                "provider_artifact_sha256" to t.provider, "source_stack_canonical_sha256" to t.source,
                "callback_tick" to receipt.path("callback_tick").asLong(), "actual_thirst_delta" to
                    callbackAfter.path("thirst").asInt() - callbackBefore.path("thirst").asInt()))
    }
}
