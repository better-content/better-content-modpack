package com.bettercontent.tests.suite.client

import com.fasterxml.jackson.databind.JsonNode

/** No native self-reported UI value supplies its own expected target: caller binds frozen reviewed selector first. */
internal fun validateAuthenticEmiMouseInput(start: JsonNode, release: JsonNode, status: JsonNode,
    hover: JsonNode, actor: String, dimension: String, profile: String, holdId: String,
    runtimeSha: String, action: String, emiSha: String): Boolean {
    val kind = when (action) { "recipes" -> "emi_mouse_left"; "uses" -> "emi_mouse_right"; else -> return false }
    val all = listOf(start, release, status)
    if (!Regex("[0-9a-f]{64}").matches(runtimeSha) || !Regex("[0-9a-f]{64}").matches(emiSha) ||
        profile !in setOf("1280x720@2", "1920x1080@3") ||
        !Regex("[A-Za-z0-9._-]{1,64}").matches(holdId) ||
        runCatching { java.util.UUID.fromString(actor).toString() == actor }.getOrDefault(false).not() ||
        !all.all { it.isObject && it.path("evidence_role").asText() ==
            "authentic_robot_os_input_NOT_direct_callback_injection" &&
            it.path("kind").asText() == kind && it.path("player_uuid").asText() == actor &&
            it.path("dimension").asText() == dimension && it.path("profile").asText() == profile &&
            it.path("hold_id").asText() == holdId && it.path("input_runtime_sha256").asText() == runtimeSha &&
            it.path("held_max_ticks").isIntegralNumber && it.path("held_max_ticks").asInt() == 6 }) return false
    val shared = listOf("client_namespace_pid", "controller_claim_host_xvfb_pid",
        "controller_claim_host_xvfb_birth_epoch_millis", "controller_claim_host_client_pid",
        "controller_claim_host_client_birth_epoch_millis", "glfw_window", "x11_window", "client_session_id",
        "epoch_id", "purpose", "display", "xauthority_sha256", "hold_sequence", "held_max_ticks")
    if (!all.all { row -> shared.all { row.get(it) != null && row.get(it) == start.get(it) } } ||
        shared.take(7).any { !start.path(it).isIntegralNumber || !start.path(it).canConvertToLong() ||
            start.path(it).asLong() <= 0 } ||
        !start.path("hold_sequence").isIntegralNumber || start.path("hold_sequence").asLong() <= 0 ||
        !Regex("epoch-[0-9]{5}").matches(start.path("epoch_id").asText()) ||
        !Regex("client-[a-f0-9-]{36}").matches(start.path("client_session_id").asText()) ||
        start.path("purpose").asText() !in setOf("diagnostic", "qualification") ||
        !Regex("127\\.0\\.0\\.1:[0-9]{1,5}").matches(start.path("display").asText()) ||
        !Regex("[0-9a-f]{64}").matches(start.path("xauthority_sha256").asText())) return false
    fun yes(row: JsonNode, field: String) = row.path(field).isBoolean && row.path(field).asBoolean()
    fun no(row: JsonNode, field: String) = row.path(field).isBoolean && !row.path(field).asBoolean()
    val recorded = start.path("emi_pre_press_hover")
    if (!recorded.isObject || !hover.isObject ||
        listOf("screen_class", "gui_x", "gui_y", "hover_kind", "hover_id", "member_ids",
            "source_interaction_class", "source", "emi_jar_sha256", "projection_sha256")
            .any { recorded.path(it) != hover.path(it) } ||
        recorded.path("emi_jar_sha256").asText() != emiSha ||
        recorded.path("projection_sha256").asText() == EMI_HOVER_PROJECTION_MARKER ||
        !Regex("[0-9a-f]{64}").matches(recorded.path("projection_sha256").asText()) ||
        all.drop(1).any { it.path("emi_pre_press_hover") != recorded } ||
        recorded.path("screen_class").asText() != "net.minecraft.client.gui.screens.inventory.InventoryScreen" ||
        start.path("screen_class").asText() != recorded.path("screen_class").asText()) return false
    return yes(start, "physical_press_issued") && !start.has("physical_release_issued") &&
        yes(release, "physical_release_issued") && yes(status, "physical_release_verified") &&
        yes(release, "press_callback") && yes(status, "press_callback") && yes(status, "release_callback") &&
        no(status, "physical_input_down") && start.path("held_state_ticks").isIntegralNumber &&
        start.path("held_state_ticks").asInt() == 0 && release.path("held_state_ticks").isIntegralNumber &&
        release.path("held_state_ticks").asInt() in 1..6 && status.path("held_state_ticks").isIntegralNumber &&
        status.path("held_state_ticks").asInt() == release.path("held_state_ticks").asInt() &&
        status.path("screen_class").asText() == "dev.emi.emi.screen.RecipeScreen" &&
        release.path("screen_class").asText() in setOf(
            "net.minecraft.client.gui.screens.inventory.InventoryScreen", "dev.emi.emi.screen.RecipeScreen")
}
