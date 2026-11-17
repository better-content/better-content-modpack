package com.bettercontent.tests.suite.client

import com.fasterxml.jackson.databind.JsonNode
import kotlin.math.abs
import kotlin.math.hypot

/** Independent bounded source check; server actor/terrain and reviewed route are checked by WalkRouteControlDriver. */
internal fun validateAuthenticWalkInput(start: JsonNode, release: JsonNode, status: JsonNode,
    actor: String, dimension: String, profile: String, holdId: String, runtimeSha: String,
    maxTicks: Int): Boolean {
    val all = listOf(start, release, status)
    if (maxTicks !in 4..60 || profile !in setOf("1280x720@2", "1920x1080@3") ||
        !Regex("[A-Za-z0-9._-]{1,64}").matches(holdId) ||
        !Regex("[0-9a-f]{64}").matches(runtimeSha) ||
        runCatching { java.util.UUID.fromString(actor).toString() == actor }.getOrDefault(false).not() ||
        !all.all { row -> row.isObject && row.path("evidence_role").asText() ==
            "authentic_robot_os_input_NOT_direct_callback_injection" &&
            row.path("kind").asText() == "walk_forward" && row.path("walk_support_scope").asText() ==
            "ordinary_flat_ground_forward_only" && row.path("player_uuid").asText() == actor &&
            row.path("dimension").asText() == dimension && row.path("profile").asText() == profile &&
            row.path("hold_id").asText() == holdId && row.path("input_runtime_sha256").asText() == runtimeSha &&
            row.path("held_max_ticks").isIntegralNumber && row.path("held_max_ticks").asInt() == maxTicks &&
            row.path("screen_class").isNull }) return false
    val binding = listOf("client_namespace_pid", "controller_claim_host_xvfb_pid",
        "controller_claim_host_xvfb_birth_epoch_millis", "controller_claim_host_client_pid",
        "controller_claim_host_client_birth_epoch_millis", "glfw_window", "x11_window", "client_session_id",
        "epoch_id", "purpose", "display", "xauthority_sha256", "hold_sequence", "held_max_ticks")
    if (!all.all { row -> binding.all { row.get(it) != null && row.get(it) == start.get(it) } } ||
        binding.take(7).any { !start.path(it).isIntegralNumber || !start.path(it).canConvertToLong() ||
            start.path(it).asLong() <= 0 } ||
        !start.path("hold_sequence").isIntegralNumber || start.path("hold_sequence").asLong() <= 0 ||
        start.path("purpose").asText() !in setOf("diagnostic", "qualification") ||
        !Regex("epoch-[0-9]{5}").matches(start.path("epoch_id").asText()) ||
        !Regex("client-[a-f0-9-]{36}").matches(start.path("client_session_id").asText()) ||
        !Regex("127\\.0\\.0\\.1:[0-9]{1,5}").matches(start.path("display").asText()) ||
        !Regex("[0-9a-f]{64}").matches(start.path("xauthority_sha256").asText())) return false
    fun yes(row: JsonNode, key: String) = row.path(key).isBoolean && row.path(key).asBoolean()
    fun no(row: JsonNode, key: String) = row.path(key).isBoolean && !row.path(key).asBoolean()
    if (!yes(start, "physical_press_issued") || start.has("physical_release_issued") ||
        !yes(release, "physical_release_issued") || !yes(status, "physical_release_verified") ||
        !no(status, "physical_input_down") ||
        !yes(release, "press_callback") || !yes(status, "press_callback") || !yes(status, "release_callback") ||
        !start.path("held_state_ticks").isIntegralNumber || start.path("held_state_ticks").asInt() != 0 ||
        !release.path("held_state_ticks").isIntegralNumber || !status.path("held_state_ticks").isIntegralNumber ||
        release.path("held_state_ticks").asInt() !in 1..maxTicks ||
        status.path("held_state_ticks").asInt() != release.path("held_state_ticks").asInt() ||
        release.path("walk_start_yaw") != start.path("walk_start_yaw") ||
        status.path("walk_start_yaw") != start.path("walk_start_yaw")) return false
    val first = start.path("walk_samples"); val samples = release.path("walk_samples")
    if (!first.isArray || first.size() != 1 || !samples.isArray || samples.size() !in 2..maxTicks + 1 ||
        first[0] != samples[0] || status.path("walk_samples") != samples ||
        samples.size() - 1 != release.path("held_state_ticks").asInt()) return false
    fun num(row: JsonNode, field: String): Double? = row.path(field).takeIf { it.isNumber }?.asDouble()?.takeIf { it.isFinite() }
    val origin = samples[0]; val ox = num(origin, "x") ?: return false
    val oy = num(origin, "y") ?: return false; val oz = num(origin, "z") ?: return false
    val yaw = num(origin, "yaw") ?: return false
    if (yaw != num(start, "walk_start_yaw") || abs(yaw) > 36_000 ||
        listOf("x", "y", "z", "yaw").any { field -> num(start, field) != num(origin, field) }) return false
    var lastX = ox; var lastZ = oz; var lastTick = -1L
    for (sample in samples) {
        if (!sample.path("native_tick").isIntegralNumber || !sample.path("native_tick").canConvertToLong()) return false
        val tick = sample.path("native_tick").asLong()
        val x = num(sample, "x") ?: return false; val y = num(sample, "y") ?: return false
        val z = num(sample, "z") ?: return false; val bearing = num(sample, "yaw") ?: return false
        if (tick <= lastTick || abs(y - oy) > 0.25 ||
            abs(Math.IEEEremainder(bearing - yaw, 360.0)) > 5.0 ||
            hypot(x - lastX, z - lastZ) > 0.55 ||
            hypot(x - ox, z - oz) > maxTicks * 0.35 + 0.25 ||
            !yes(sample, "on_ground") || listOf("swimming", "in_fluid", "passenger", "sprinting",
                "crouching", "flying", "jump_down", "strafe_down", "back_down", "interact_down")
                .any { !no(sample, it) }) return false
        lastX = x; lastZ = z; lastTick = tick
    }
    return hypot(lastX - ox, lastZ - oz) >= 0.3 &&
        num(status, "x") != null && num(status, "y") != null && num(status, "z") != null &&
        hypot(num(status, "x")!! - lastX, num(status, "z")!! - lastZ) <= 0.55 &&
        abs(num(status, "y")!! - oy) <= 0.25 && no(status, "in_fluid") && yes(status, "on_ground")
}
