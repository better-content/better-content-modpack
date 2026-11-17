package com.bettercontent.tests.suite.client

import com.fasterxml.jackson.databind.JsonNode

/** Pure triple consistency for a bounded genuine Shift+K pulse; controller independently verifies immutable host binding. */
internal fun validateAuthenticOnboardingInput(start: JsonNode, release: JsonNode, status: JsonNode,
    actor: String, dimension: String, profile: String, holdId: String, runtimeSha: String): Boolean {
    val all = listOf(start, release, status)
    if (profile !in setOf("1280x720@2", "1920x1080@3") ||
        !Regex("[A-Za-z0-9._-]{1,64}").matches(holdId) ||
        !Regex("[0-9a-f]{64}").matches(runtimeSha) ||
        runCatching { java.util.UUID.fromString(actor).toString() == actor }.getOrDefault(false).not() ||
        !all.all { row -> row.isObject && row.path("evidence_role").asText() ==
            "authentic_robot_os_input_NOT_direct_callback_injection" &&
            row.path("kind").asText() == "onboarding_chord" && row.path("player_uuid").asText() == actor &&
            row.path("dimension").asText() == dimension && row.path("profile").asText() == profile &&
            row.path("hold_id").asText() == holdId && row.path("input_runtime_sha256").asText() == runtimeSha &&
            row.path("held_max_ticks").isIntegralNumber && row.path("held_max_ticks").asInt(-1) in 2..40 }) return false
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
    val context = release.path("onboarding_key_press_context")
    if (!context.isObject || status.path("onboarding_key_press_context") != context ||
        !yes(context, "spectator") || !yes(context, "modifier_down") ||
        context.path("dimension").asText() != dimension ||
        !context.path("native_tick").isIntegralNumber || context.path("native_tick").asLong() < 0 ||
        listOf("block_x", "block_y", "block_z").any { !context.path(it).isIntegralNumber || !context.path(it).canConvertToInt() }) return false
    return yes(start, "physical_press_issued") && !start.has("physical_release_issued") &&
        yes(start, "client_spectator") && yes(start, "onboarding_active") &&
        yes(start, "onboarding_selection_required") && yes(start, "onboarding_mode_spawn_only") &&
        yes(release, "physical_release_issued") && !release.has("physical_release_verified") &&
        yes(status, "physical_release_verified") && no(status, "physical_input_down") &&
        all.drop(1).all { yes(it, "press_callback") && yes(it, "modifier_press_callback") } &&
        yes(status, "release_callback") && yes(status, "modifier_release_callback") &&
        start.path("held_state_ticks").isIntegralNumber && release.path("held_state_ticks").isIntegralNumber &&
        status.path("held_state_ticks").isIntegralNumber &&
        release.path("held_state_ticks").asInt() in 1..start.path("held_max_ticks").asInt() &&
        status.path("held_state_ticks").asInt() >= release.path("held_state_ticks").asInt()
}
