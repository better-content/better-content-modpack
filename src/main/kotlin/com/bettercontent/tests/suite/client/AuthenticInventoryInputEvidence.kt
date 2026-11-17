package com.bettercontent.tests.suite.client

import com.fasterxml.jackson.databind.JsonNode

/** OS inventory-key press/up, not the diagnostic direct KeyboardHandler.keyPress injection. */
internal fun validateAuthenticInventoryInput(start: JsonNode, release: JsonNode, status: JsonNode,
    actor: String, dimension: String, profile: String, holdId: String, runtimeSha: String): Boolean {
    val all = listOf(start, release, status)
    if (profile !in setOf("1280x720@2", "1920x1080@3") ||
        !Regex("[A-Za-z0-9._-]{1,64}").matches(holdId) || !Regex("[0-9a-f]{64}").matches(runtimeSha) ||
        runCatching { java.util.UUID.fromString(actor).toString() == actor }.getOrDefault(false).not() ||
        !all.all { row -> row.isObject && row.path("evidence_role").asText() ==
            "authentic_robot_os_input_NOT_direct_callback_injection" && row.path("kind").asText() == "inventory_key" &&
            row.path("player_uuid").asText() == actor && row.path("dimension").asText() == dimension &&
            row.path("profile").asText() == profile && row.path("hold_id").asText() == holdId &&
            row.path("input_runtime_sha256").asText() == runtimeSha &&
            row.path("held_max_ticks").isIntegralNumber && row.path("held_max_ticks").asInt() == 6 } ||
        !start.path("screen_class").isNull || start.path("client_spectator").asBoolean(true)) return false
    val shared = listOf("client_namespace_pid", "controller_claim_host_xvfb_pid",
        "controller_claim_host_xvfb_birth_epoch_millis", "controller_claim_host_client_pid",
        "controller_claim_host_client_birth_epoch_millis", "glfw_window", "x11_window", "client_session_id",
        "epoch_id", "purpose", "display", "xauthority_sha256", "hold_sequence", "held_max_ticks")
    if (!all.all { row -> shared.all { row.get(it) != null && row.get(it) == start.get(it) } } ||
        shared.take(7).any { !start.path(it).isIntegralNumber || !start.path(it).canConvertToLong() ||
            start.path(it).asLong() <= 0 } ||
        !start.path("hold_sequence").isIntegralNumber || start.path("hold_sequence").asLong() <= 0 ||
        start.path("purpose").asText() !in setOf("diagnostic", "qualification") ||
        !Regex("epoch-[0-9]{5}").matches(start.path("epoch_id").asText()) ||
        !Regex("client-[a-f0-9-]{36}").matches(start.path("client_session_id").asText()) ||
        !Regex("127\\.0\\.0\\.1:[0-9]{1,5}").matches(start.path("display").asText()) ||
        !Regex("[0-9a-f]{64}").matches(start.path("xauthority_sha256").asText())) return false
    fun yes(row: JsonNode, name: String) = row.path(name).isBoolean && row.path(name).asBoolean()
    fun no(row: JsonNode, name: String) = row.path(name).isBoolean && !row.path(name).asBoolean()
    return yes(start, "physical_press_issued") && !start.has("physical_release_issued") &&
        yes(release, "physical_release_issued") && yes(status, "physical_release_verified") &&
        yes(release, "press_callback") && yes(status, "press_callback") && yes(status, "release_callback") &&
        no(status, "physical_input_down") &&
        start.path("held_state_ticks").isIntegralNumber && start.path("held_state_ticks").asInt() == 0 &&
        release.path("held_state_ticks").isIntegralNumber && release.path("held_state_ticks").asInt() in 1..6 &&
        status.path("held_state_ticks").isIntegralNumber &&
        status.path("held_state_ticks").asInt() == release.path("held_state_ticks").asInt() &&
        listOf(release, status).all {
            it.path("screen_class").asText() == "net.minecraft.client.gui.screens.inventory.InventoryScreen" &&
                it.path("menu_type").asText() == "net.minecraft.world.inventory.InventoryMenu" &&
                it.path("screen_title").isTextual && it.path("screen_title").asText().isNotBlank() &&
                !it.path("client_spectator").asBoolean(true)
        }
}
