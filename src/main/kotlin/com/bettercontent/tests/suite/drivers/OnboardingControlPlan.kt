package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.client.INPUT_RUNTIME_MARKER
import com.bettercontent.tests.suite.client.OnboardingTerrainOracle
import com.bettercontent.tests.suite.client.validateAuthenticOnboardingInput
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import kotlin.math.abs

/** Authored, not registered: D/A must review the case, roster, prerequisite and host-binding source before execution. */
internal object OnboardingControlPlan {
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private val sha = Regex("[0-9a-f]{64}")
    private data class Target(val actor: String, val dimension: String, val observer: String, val holdId: String,
        val profile: String, val providerSha: String, val configSha: String, val bounds: JsonNode,
        val anchorX: Int, val anchorY: Int, val anchorZ: Int)
    private fun parse(input: JsonNode): Target {
        require(input.isObject && input.fieldNames().asSequence().toSet() == setOf("player_uuid", "dimension",
            "observer_id", "hold_id", "input_profile", "provider_artifact_sha256", "provider_config_sha256",
            "anchor_x", "anchor_y", "anchor_z", "min_build_height", "max_build_height", "request_bounds")) {
            "reviewed first-login onboarding target fields required"
        }
        fun text(name: String): String = input.path(name).also { require(it.isTextual) }.asText()
        fun number(name: String): Int = input.path(name).also { require(it.isIntegralNumber && it.canConvertToInt()) }.asInt()
        val actor = text("player_uuid"); val observer = text("observer_id")
        val dim = text("dimension"); val holdId = text("hold_id"); val profile = text("input_profile")
        val artifact = text("provider_artifact_sha256"); val config = text("provider_config_sha256")
        require(listOf(actor, observer).all { runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false) } &&
            resource.matches(dim) && Regex("[A-Za-z0-9._-]{1,64}").matches(holdId) &&
            profile in setOf("1280x720@2", "1920x1080@3") && sha.matches(artifact) && sha.matches(config)) {
            "unreviewed actor/provider/client input context"
        }
        val x = number("anchor_x"); val y = number("anchor_y"); val z = number("anchor_z")
        val min = number("min_build_height"); val max = number("max_build_height")
        require(x in -29_999_999..29_999_999 && z in -29_999_999..29_999_999 && y in -2048..2048 &&
            max.toLong() - min.toLong() in 4..384 && y in min until max) { "unsupported pinned build height/actor anchor" }
        val bounds = input.path("request_bounds")
        require(bounds.isObject && bounds.fieldNames().asSequence().toSet() ==
            setOf("dimension", "x_min", "x_max", "y_min", "y_max", "z_min", "z_max") &&
            bounds.path("dimension").asText() == dim) { "unreviewed request area" }
        fun b(name: String): Int = bounds.path(name).also { require(it.isIntegralNumber && it.canConvertToInt()) }.asInt()
        require(b("x_min") == x - 1 && b("x_max") == x + 1 && b("z_min") == z - 1 && b("z_max") == z + 1 &&
            b("y_min") == (y - 2).coerceAtLeast(min) && b("y_max") == (y + 1).coerceAtMost(max - 1)) {
            "onboarding bounds must be arm-centered x/z±1, y−2..+1 clipped to build height"
        }
        return Target(actor, dim, observer, holdId, profile, artifact, config, bounds, x, y, z)
    }
    fun plan(input: JsonNode): List<DriverStep> {
        val t = parse(input)
        val arm = suiteMapper.createObjectNode().put("player_uuid", "@observed-player-uuid")
            .put("dimension", t.dimension).put("observer_id", t.observer)
            .put("expected_provider", "better_spawns:FinalizeSelectionPacket").put("expected_mode", "none")
            .put("provider_artifact_sha256", t.providerSha).put("provider_config_sha256", t.configSha)
            .put("deadline_ticks", 12000).put("witness_context", "@controller-witness-context-v1")
        arm.set<JsonNode>("request_bounds", t.bounds.deepCopy())
        val held = suiteMapper.createObjectNode().put("player_uuid", "@observed-player-uuid")
            .put("dimension", t.dimension).put("hold_id", t.holdId)
            .put("input_runtime_sha256", INPUT_RUNTIME_MARKER)
        val pulse = held.deepCopy().apply { put("profile", t.profile); put("kind", "onboarding"); put("max_ticks", 20) }
        val await = suiteMapper.createObjectNode().put("player_uuid", "@observed-player-uuid")
            .put("dimension", t.dimension).put("observer_id", t.observer)
            .put("arm_request_sequence", "@arm-request-sequence").put("arm_action_ordinal", "@arm-action-ordinal")
            .put("witness_context", "@controller-witness-context-v1")
        return listOf(
            DriverStep("onboarding.arm", DriverAction("player.observe_onboarding.arm", arm, 200)),
            DriverStep("onboarding.hold", DriverAction("client.input_hold", pulse, 40, "client")),
            DriverStep("onboarding.release", DriverAction("client.input_release", held.deepCopy(), 80, "client")),
            DriverStep("onboarding.status", DriverAction("client.input_status", held.deepCopy(), 80, "client")),
            DriverStep("onboarding.await", DriverAction("player.observe_onboarding.await", await, 12000)),
            DriverStep("onboarding.after", DriverAction("player.snapshot",
                suiteMapper.createObjectNode().put("player_uuid", t.actor), 200)))
    }
    fun evaluate(input: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val t = parse(input)
        require(observations.size == 6 && observations.map { it.driver } ==
            listOf("player.observe_onboarding.arm", "client.input_hold", "client.input_release",
                "client.input_status", "player.observe_onboarding.await", "player.snapshot") &&
            observations.all { it.kind == "completed" } &&
            observations.map { it.source } == listOf("server", "client", "client", "client", "server", "server")) {
            "incomplete true-input onboarding source"
        }
        val arm = observations[0].payload; val hold = observations[1].payload
        val release = observations[2].payload; val status = observations[3].payload
        val receipt = observations[4].payload; val after = observations[5].payload
        require(arm.path("schema").asText() == "bc.playtest_passive_onboarding_arm.v1" &&
            arm.path("armed_before_input").isBoolean && arm.path("armed_before_input").asBoolean() &&
            arm.path("observer_id").asText() == t.observer && arm.path("actor_uuid").asText() == t.actor &&
            receipt.path("observer_id").asText() == t.observer && receipt.path("actor_uuid").asText() == t.actor &&
            arm.path("arm_request_sequence").isIntegralNumber && arm.path("arm_action_ordinal").isIntegralNumber &&
            receipt.path("arm_request_sequence") == arm.path("arm_request_sequence") &&
            receipt.path("arm_action_ordinal") == arm.path("arm_action_ordinal") &&
            receipt.path("expected_provider").asText() == "better_spawns:FinalizeSelectionPacket" &&
            receipt.path("expected_mode").asText() == "none" &&
            receipt.path("provider_artifact_sha256_controller_declared").asText() == t.providerSha &&
            receipt.path("provider_config_sha256_controller_declared").asText() == t.configSha &&
            receipt.path("provider_artifact_certified_by_native").isBoolean &&
            !receipt.path("provider_artifact_certified_by_native").asBoolean() &&
            receipt.path("actor_at_arm") == arm.path("actor_at_arm") &&
            arm.path("actor_at_arm").path("block_x").asInt(Int.MIN_VALUE) == t.anchorX &&
            arm.path("actor_at_arm").path("block_y").asInt(Int.MIN_VALUE) == t.anchorY &&
            arm.path("actor_at_arm").path("block_z").asInt(Int.MIN_VALUE) == t.anchorZ &&
            validateAuthenticOnboardingInput(hold, release, status, t.actor, t.dimension, t.profile,
                t.holdId, hold.path("input_runtime_sha256").asText())) {
            "missing bound real Shift+K actor/input/arm source"
        }
        val site = receipt.path("actual_committed_site")
        val afterSite = listOf("x", "y", "z").map { after.path(it) }
        val good = OnboardingTerrainOracle.matchesReviewedSite(t.bounds, arm, receipt,
                status.path("onboarding_key_press_context")) &&
            after.path("uuid").asText() == t.actor && after.path("dimension").asText() == t.dimension &&
            after.path("mode").asText() == "survival" &&
            afterSite.all { it.isNumber && it.asDouble().isFinite() } &&
            abs(after.path("x").asDouble() - site.path("x").asDouble()) <= 2.0 &&
            abs(after.path("y").asDouble() - site.path("y").asDouble()) <= 4.0 &&
            abs(after.path("z").asDouble() - site.path("z").asDouble()) <= 2.0
        return MeasurementVerdict(if (good) "passed" else "failed",
            "genuine bounded spectator Shift+K, passive finalized site and independent raw vanilla terrain subset; " +
            "controller artifact pin/reopen persistence separately required; no hidden gamemode action")
    }
}
