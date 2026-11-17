package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.client.INPUT_RUNTIME_MARKER
import com.bettercontent.tests.suite.client.validateAuthenticHeldInput
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

/** Genuine client-held duration-based manual drinking; this does NOT establish source acquisition. */
internal object PhysicalWaterSipControlDriver : CaseDriver {
    override val caseId = "control.water.physical-manual-sip"
    private data class Target(val player: String, val dimension: String, val profile: String, val observer: String,
        val holdId: String, val water: JsonNode)
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private fun parse(parameters: JsonNode): Target {
        require(parameters.isObject && parameters.fieldNames().asSequence().toSet() ==
            setOf("dimension", "input_profile", "observer_id", "hold_id", "water")) { "physical sip parameters incomplete" }
        val water = parameters.path("water")
        WaterSipControlDriver.plan(water) // require the existing exact typed water expectations
        val player = water.path("player_uuid").asText()
        val dimension = parameters.path("dimension").asText("")
        val profile = parameters.path("input_profile").asText("")
        val observer = parameters.path("observer_id").asText("")
        val holdId = parameters.path("hold_id").asText("")
        require(resource.matches(dimension) && profile in setOf("1280x720@2", "1920x1080@3") &&
            runCatching { UUID.fromString(observer).toString() == observer }.getOrDefault(false) &&
            Regex("[A-Za-z0-9._-]{1,64}").matches(holdId) && water.path("hand").asText() == "main_hand") {
            "unsupported physical sip actor/profile/observer/hand"
        }
        return Target(player, dimension, profile, observer, holdId, water)
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val p = parse(parameters)
        val snapshot = suiteMapper.createObjectNode().put("player_uuid", p.player).put("hand", "main_hand")
        val held = suiteMapper.createObjectNode().put("hold_id", p.holdId)
            .put("player_uuid", "@observed-player-uuid").put("dimension", p.dimension)
            .put("input_runtime_sha256", INPUT_RUNTIME_MARKER)
        return listOf(
            DriverStep("before", DriverAction("player.water_snapshot", snapshot.deepCopy())),
            DriverStep("arm", DriverAction("player.observe_item_use.arm", suiteMapper.createObjectNode()
                .put("player_uuid", "@observed-player-uuid").put("dimension", p.dimension)
                .put("observer_id", p.observer).put("expected_item_id", p.water.path("source_item").asText())
                .put("expected_provider", "living_entity_use_item").put("expected_kind", "drink")
                .put("hand", "main_hand").put("deadline_ticks", 7200)
                .put("witness_context", "@controller-witness-context-v1"), 200)),
            DriverStep("hold", DriverAction("client.input_hold", held.deepCopy().put("profile", p.profile)
                .put("kind", "use").put("max_ticks", 120), 140, "client")),
            DriverStep("release", DriverAction("client.input_release", held.deepCopy(), 200, "client")),
            DriverStep("status", DriverAction("client.input_status", held.deepCopy(), 200, "client")),
            DriverStep("consume", DriverAction("player.observe_item_use.await", suiteMapper.createObjectNode()
                .put("player_uuid", "@observed-player-uuid").put("dimension", p.dimension)
                .put("observer_id", p.observer).put("arm_request_sequence", "@arm-request-sequence")
                .put("arm_action_ordinal", "@arm-action-ordinal")
                .put("witness_context", "@controller-witness-context-v1"), 7200)),
            DriverStep("after", DriverAction("player.water_snapshot", snapshot.deepCopy())))
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val p = parse(parameters)
        require(observations.size == 7) { "complete physical sip observation required" }
        val before = observations[0].payload; val arm = observations[1].payload
        val hold = observations[2].payload; val release = observations[3].payload
        val status = observations[4].payload; val action = observations[5].payload
        val after = observations[6].payload
        require(arm.path("armed_before_input").asBoolean(false) &&
            arm.path("observer_id").asText() == p.observer &&
            arm.path("acquisition_origin").asText() == "preexisting_unattributed" &&
            arm.path("natural_acquisition_proven_by_native").isBoolean &&
            !arm.path("natural_acquisition_proven_by_native").asBoolean() &&
            action.path("schema").asText() == "bc.playtest_passive_item_use.v1" &&
            action.path("observer_id").asText() == p.observer &&
            action.path("actor_uuid").asText() == p.player &&
            action.path("dimension").asText() == p.dimension &&
            action.path("expected_item_id").asText() == p.water.path("source_item").asText() &&
            action.path("expected_kind").asText() == "drink" &&
            action.path("expected_provider").asText() == "living_entity_use_item" &&
            action.path("hand").asText() == "main_hand" &&
            action.path("acquisition_origin").asText() == "preexisting_unattributed" &&
            action.path("natural_acquisition_proven_by_native").isBoolean &&
            !action.path("natural_acquisition_proven_by_native").asBoolean() &&
            action.path("packet_transport_origin_proven_by_native").isBoolean &&
            !action.path("packet_transport_origin_proven_by_native").asBoolean() &&
            action.path("narrow_event_chain_complete").asBoolean(false) &&
            action.path("packet_count").asInt(-1) == 1 && action.path("start_count").asInt(-1) == 1 &&
            action.path("finish_count").asInt(-1) == 1 &&
            arm.path("arm_request_sequence").isIntegralNumber && arm.path("arm_action_ordinal").isIntegralNumber &&
            action.path("arm_request_sequence") == arm.path("arm_request_sequence") &&
            action.path("arm_action_ordinal") == arm.path("arm_action_ordinal") &&
            action.path("packet_source").asText() == "ServerGamePacketListenerImpl.handleUseItem/server_thread_HEAD" &&
            action.path("start_source").asText() == "LivingEntityUseItemEvent.Start/MONITOR" &&
            action.path("finish_source").asText() == "LivingEntityUseItemEvent.Finish/MONITOR" &&
            action.path("final_source").asText() == "ServerTickEvent.END/actual_player_state" &&
            listOf("arm_tick", "packet_tick", "start_event_tick", "finish_event_tick", "final_applied_state_tick")
                .all { action.path(it).isIntegralNumber } &&
            listOf("arm_tick", "packet_tick", "start_event_tick", "finish_event_tick", "final_applied_state_tick")
                .map { action.path(it).asLong() }.zipWithNext().all { (a, b) -> a <= b } &&
            validateAuthenticHeldInput(hold, release, status, p.player, p.dimension, p.profile,
                p.holdId, p.water.path("source_item").asText(), hold.path("input_runtime_sha256").asText())) {
            "physical OS input and passive real-use packet chain missing"
        }
        return WaterSipControlDriver.evaluatePassive(p.water, before, action, after)
    }
}
