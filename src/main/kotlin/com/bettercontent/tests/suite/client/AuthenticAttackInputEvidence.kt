package com.bettercontent.tests.suite.client

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import kotlin.math.abs

/** Source-level same-window/OS-callback target freeze only. A separately authenticates host PID/birth,
 * client runtime, reviewed natural BoundTarget, request sequence and B's actual server packet witness. */
internal fun validateAuthenticBlockAttack(crosshair: JsonNode, hold: JsonNode, release: JsonNode, status: JsonNode,
    playerUuid: String, dimension: String, profile: String, holdId: String, expectedX: Int, expectedY: Int,
    expectedZ: Int, expectedFace: String, boundTargetSha256: String, runtimeSha256: String): Boolean {
    val sha = Regex("[0-9a-f]{64}")
    val snapshots = listOf(hold, release, status)
    if (runCatching { UUID.fromString(playerUuid).toString() == playerUuid }.getOrDefault(false).not() ||
        !Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(dimension) ||
        profile !in setOf("1280x720@2", "1920x1080@3") ||
        !Regex("[A-Za-z0-9._-]{1,64}").matches(holdId) ||
        !sha.matches(boundTargetSha256) || !sha.matches(runtimeSha256) ||
        expectedFace !in setOf("north", "south", "east", "west", "up", "down") ||
        crosshair.path("player_uuid").asText() != playerUuid ||
        crosshair.path("dimension").asText() != dimension ||
        crosshair.path("crosshair_source").asText() != "Minecraft.hitResult/BlockHitResult/read_only_existing_render_target" ||
        crosshair.path("crosshair_is_natural_bound_target").asBoolean(true) ||
        crosshair.path("crosshair_packet_transport_proven").asBoolean(true) ||
        !sha.matches(crosshair.path("crosshair_projection_sha256").asText())) return false
    val target = crosshair.path("crosshair")
    if (!target.isObject || target.fieldNames().asSequence().toSet() !=
        setOf("player_uuid", "dimension", "x", "y", "z", "face", "hit_x", "hit_y", "hit_z") ||
        target.path("player_uuid").asText() != playerUuid || target.path("dimension").asText() != dimension ||
        target.path("x").asInt(Int.MIN_VALUE) != expectedX || target.path("y").asInt(Int.MIN_VALUE) != expectedY ||
        target.path("z").asInt(Int.MIN_VALUE) != expectedZ || target.path("face").asText() != expectedFace ||
        listOf("hit_x", "hit_y", "hit_z").any { !target.path(it).isNumber || !target.path(it).asDouble().isFinite() })
        return false
    val bound = listOf("client_namespace_pid", "controller_claim_host_xvfb_pid",
        "controller_claim_host_xvfb_birth_epoch_millis", "controller_claim_host_client_pid",
        "controller_claim_host_client_birth_epoch_millis", "glfw_window", "x11_window", "client_session_id",
        "epoch_id", "purpose", "display", "xauthority_sha256", "hold_sequence")
    if (!snapshots.all { it.isObject && it.path("evidence_role").asText() ==
        "authentic_robot_os_input_NOT_direct_callback_injection" &&
        it.path("player_uuid").asText() == playerUuid && it.path("dimension").asText() == dimension &&
        it.path("profile").asText() == profile && it.path("kind").asText() == "attack_mouse" &&
        it.path("hold_id").asText() == holdId && it.path("input_runtime_sha256").asText() == runtimeSha256 &&
        it.path("attack_pre_press_crosshair_projection_sha256") == crosshair.path("crosshair_projection_sha256") &&
        it.path("attack_bound_target_sha256_unverified_by_client").asText() == boundTargetSha256 &&
        it.path("attack_support_scope").asText() == "one_frozen_in_world_block_vanilla_hitResult_only" &&
        it.path("held_max_ticks").asInt(-1) in 4..160 &&
        it.path("physical_input_down").isBoolean &&
        bound.all { field -> it.get(field) == hold.get(field) }
    } || !snapshots.all { it.path("held_max_ticks") == hold.path("held_max_ticks") } ||
        bound.take(7).any { !hold.path(it).isIntegralNumber || hold.path(it).asLong() <= 0 } ||
        !hold.path("hold_sequence").isIntegralNumber || hold.path("hold_sequence").asLong() <= 0 ||
        !sha.matches(hold.path("xauthority_sha256").asText()) ||
        !Regex("epoch-[0-9]{5}").matches(hold.path("epoch_id").asText()) ||
        hold.path("purpose").asText() !in setOf("diagnostic", "qualification") ||
        !Regex("127\\.0\\.0\\.1:[0-9]{1,5}").matches(hold.path("display").asText()) ||
        !hold.path("physical_press_issued").asBoolean(false) || hold.has("physical_release_issued") ||
        !release.path("physical_release_issued").asBoolean(false) ||
        !status.path("physical_release_verified").asBoolean(false) ||
        !release.path("press_callback").asBoolean(false) || !status.path("press_callback").asBoolean(false) ||
        !release.path("release_callback").asBoolean(false) || !status.path("release_callback").asBoolean(false) ||
        release.path("physical_input_down").asBoolean(true) || status.path("physical_input_down").asBoolean(true) ||
        !release.path("attack_auto_released_on_target_loss").asBoolean(false) ||
        !status.path("attack_auto_released_on_target_loss").asBoolean(false)) return false
    val loss = release.path("attack_target_loss_tick").asLong(-1)
    val drained = release.path("attack_key_up_drained_before_keybind_tick").asLong(-1)
    val samples = release.path("attack_samples")
    if (loss < 0 || drained < loss || status.path("attack_target_loss_tick").asLong(-1) != loss ||
        status.path("attack_key_up_drained_before_keybind_tick").asLong(-1) != drained ||
        !samples.isArray || samples.size() !in 2..162 || samples != status.path("attack_samples") ||
        !hold.path("attack_samples").isArray || hold.path("attack_samples").size() !in 1..2 ||
        release.path("held_state_ticks").asInt(0) !in 1..160 ||
        status.path("held_state_ticks") != release.path("held_state_ticks")) return false
    val ticks = samples.map { it.path("native_tick").asLong(-1) }
    val anchored = samples.first()
    val posture = samples.all { sample ->
        sample.path("on_ground").asBoolean(false) && listOf("x", "y", "z", "yaw", "pitch").all {
            sample.path(it).isNumber && sample.path(it).asDouble().isFinite() &&
                anchored.path(it).isNumber && anchored.path(it).asDouble().isFinite()
        } &&
        listOf("x", "y", "z").sumOf { coord ->
            val delta = sample.path(coord).asDouble() - anchored.path(coord).asDouble()
            delta * delta
        } <= .0225 && abs(sample.path("yaw").asDouble() - anchored.path("yaw").asDouble()) <= 1.5 &&
        abs(sample.path("pitch").asDouble() - anchored.path("pitch").asDouble()) <= 1.5
    }
    return posture && ticks.all { it >= 0 } && ticks.zipWithNext().all { (a, b) -> b >= a } &&
        ticks.first() <= loss && ticks.last() <= drained &&
        samples.any { it.path("same_existing_hit_result").asBoolean(false) &&
            it.path("press_callback").asBoolean(false) && it.path("real_glfw_left_down").asBoolean(false) &&
            it.path("attack_mapping_down").asBoolean(false) && it.path("held_state_ticks").asInt() > 0 } &&
        samples.all { it.path("real_glfw_left_down").isBoolean &&
            it.path("attack_mapping_down").isBoolean && it.path("same_existing_hit_result").isBoolean }
}
