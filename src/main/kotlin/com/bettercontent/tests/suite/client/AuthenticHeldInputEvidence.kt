package com.bettercontent.tests.suite.client

import com.fasterxml.jackson.databind.JsonNode

/** Triple consistency only: A must independently replay the immutable binding and check host PID/birth.
 * Never promotes direct callback injection or these controller-claimed host values into OS proof alone. */
internal fun validateAuthenticHeldInput(start: JsonNode, release: JsonNode, status: JsonNode,
    playerUuid: String, dimension: String, profile: String, holdId: String, selectedItemId: String,
    expectedRuntimeSha256: String): Boolean {
    val snapshots = listOf(start, release, status)
    if (profile !in setOf("1280x720@2", "1920x1080@3") ||
        runCatching { java.util.UUID.fromString(playerUuid).toString() == playerUuid }.getOrDefault(false).not() ||
        !Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(dimension) ||
        !Regex("[A-Za-z0-9._-]{1,64}").matches(holdId) ||
        !Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(selectedItemId) ||
        !Regex("[0-9a-f]{64}").matches(expectedRuntimeSha256) ||
        !snapshots.all { it.isObject && it.path("evidence_role").asText() ==
            "authentic_robot_os_input_NOT_direct_callback_injection" &&
            it.path("player_uuid").asText() == playerUuid && it.path("dimension").asText() == dimension &&
            it.path("profile").asText() == profile && it.path("hold_id").asText() == holdId &&
            it.path("input_runtime_sha256").asText() == expectedRuntimeSha256 &&
            it.path("kind").asText() in setOf("use_mouse", "use_key") }) return false
    val boundFields = listOf("client_namespace_pid", "controller_claim_host_xvfb_pid",
        "controller_claim_host_xvfb_birth_epoch_millis", "controller_claim_host_client_pid",
        "controller_claim_host_client_birth_epoch_millis", "glfw_window", "x11_window",
        "client_session_id", "epoch_id", "purpose", "display", "xauthority_sha256", "hold_sequence")
    if (!snapshots.all { row -> boundFields.all { field -> row.get(field) == start.get(field) } } ||
        listOf("client_namespace_pid", "controller_claim_host_xvfb_pid", "controller_claim_host_xvfb_birth_epoch_millis",
            "controller_claim_host_client_pid", "controller_claim_host_client_birth_epoch_millis", "glfw_window",
            "x11_window", "hold_sequence").any { key ->
            !start.path(key).isIntegralNumber || !start.path(key).canConvertToLong() || start.path(key).asLong() <= 0
        } || !start.path("purpose").isTextual || start.path("purpose").asText() !in setOf("diagnostic", "qualification") ||
        !start.path("epoch_id").isTextual || !Regex("epoch-[0-9]{5}").matches(start.path("epoch_id").asText()) ||
        !start.path("client_session_id").isTextual || !Regex("client-[a-f0-9-]{36}").matches(start.path("client_session_id").asText()) ||
        !start.path("display").isTextual || !Regex("127\\.0\\.0\\.1:[0-9]{1,5}").matches(start.path("display").asText()) ||
        !start.path("xauthority_sha256").isTextual || !Regex("[0-9a-f]{64}").matches(start.path("xauthority_sha256").asText()) ||
        !start.path("held_max_ticks").isIntegralNumber || !start.path("held_max_ticks").canConvertToInt() ||
        start.path("held_max_ticks").asInt() !in 2..200 ||
        !snapshots.all { it.path("held_max_ticks") == start.path("held_max_ticks") }) return false
    val slot = start.path("selected_slot")
    if (!slot.isIntegralNumber || !slot.canConvertToInt() || slot.asInt() !in 0..8 ||
        !start.path("inventory").isArray || start.path("inventory").count { row ->
            row.path("slot").isIntegralNumber && row.path("slot").canConvertToInt() &&
                row.path("slot").asInt(-1) == slot.asInt() && row.path("item_id").asText() == selectedItemId &&
                row.path("count").isIntegralNumber && row.path("count").canConvertToInt() &&
                row.path("count").asInt() in 1..64
        } != 1) return false
    fun flag(row: JsonNode, key: String, expected: Boolean) = row.path(key).isBoolean && row.path(key).asBoolean() == expected
    fun count(row: JsonNode) = row.path("held_state_ticks").takeIf {
        it.isIntegralNumber && it.canConvertToInt() && it.asInt() >= 0
    }?.asInt()
    val beginCount = count(start) ?: return false
    val releaseCount = count(release) ?: return false
    val statusCount = count(status) ?: return false
    val useStart = release.path("client_use_start_tick")
    val useEnd = release.path("client_use_end_tick")
    if (!flag(release, "client_use_started", true) || !flag(release, "client_use_ended", true) ||
        !flag(release, "auto_released_on_first_client_use_end", true) ||
        !flag(status, "auto_released_on_first_client_use_end", true) ||
        !useStart.isIntegralNumber || !useEnd.isIntegralNumber || !useStart.canConvertToLong() ||
        !useEnd.canConvertToLong() || useStart.asLong() < 0 || useEnd.asLong() <= useStart.asLong() ||
        status.path("client_use_start_tick") != useStart || status.path("client_use_end_tick") != useEnd) return false
    return flag(start, "physical_press_issued", true) && !start.has("physical_release_issued") &&
        flag(release, "physical_release_issued", true) && !release.has("physical_release_verified") &&
        flag(status, "physical_release_verified", true) &&
        flag(release, "press_callback", true) && flag(status, "press_callback", true) &&
        flag(status, "release_callback", true) && flag(status, "physical_input_down", false) &&
        beginCount <= releaseCount && releaseCount <= statusCount && releaseCount >= 1
}
