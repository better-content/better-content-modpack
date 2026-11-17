package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.jsonFingerprint
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode

/** Read-only pre-press gate. Never derive a reviewed target from EMI's own hover claim. */
internal fun independentlyReviewedEmiHoverProjection(target: JsonNode, point: JsonNode,
    hover: JsonNode, actualActor: String, runtime: ClientInputRuntime,
    runtimeBindingSha256: String, immutableEmiJarSha256: String): String {
    val reviewedFields = setOf("player_uuid", "dimension", "input_profile", "emi_jar_sha256",
        "inventory_hold_id", "navigation_hold_id", "navigation", "gui_x", "gui_y",
        "hover_kind", "hover_id", "hover_member_ids", "expected_category_id",
        "expected_visible_recipe_ids")
    require(target.isObject && target.fieldNames().asSequence().toSet() == reviewedFields &&
        runCatching { java.util.UUID.fromString(actualActor).toString() == actualActor }.getOrDefault(false) &&
        target.path("player_uuid").asText() == actualActor &&
        target.path("input_profile").asText() == runtime.profile &&
        target.path("emi_jar_sha256").asText() == immutableEmiJarSha256 &&
        immutableEmiJarSha256.matches(shaPattern) && runtimeBindingSha256.matches(shaPattern) &&
        target.path("dimension").asText().matches(resourcePattern) &&
        target.path("navigation").asText() in setOf("recipes", "uses") &&
        target.path("hover_kind").asText() in setOf("stack", "tag") &&
        target.path("hover_id").asText().matches(resourcePattern) &&
        target.path("hover_member_ids").isArray &&
        target.path("hover_member_ids").size() in 1..128 &&
        target.path("hover_member_ids").all { it.isTextual && it.asText().matches(resourcePattern) } &&
        target.path("hover_member_ids").map { it.asText() } ==
            target.path("hover_member_ids").map { it.asText() }.sorted() &&
        target.path("gui_x").isIntegralNumber && target.path("gui_x").asInt(-1) in 0..639 &&
        target.path("gui_y").isIntegralNumber && target.path("gui_y").asInt(-1) in 0..359) {
        "EMI hover has no independent frozen reviewed actor/stack/tag/profile/JAR/point"
    }
    val fields = listOf("member_ids", "source_interaction_class", "hover_kind", "hover_id",
        "screen_class", "gui_x", "gui_y", "emi_jar_sha256", "source")
    val projection = suiteMapper.createObjectNode().apply {
        fields.forEach { name -> set<JsonNode>(name, hover.path(name)) }
    }
    require(point.isObject && hover.isObject &&
        point.path("physical_pointer_move_issued").isBoolean &&
        point.path("physical_pointer_move_issued").asBoolean() &&
        point.path("evidence_role").asText() ==
            "authentic_robot_os_pointer_NOT_direct_callback_injection" &&
        point.path("player_uuid").asText() == actualActor &&
        point.path("dimension") == target.path("dimension") &&
        point.path("profile").asText() == runtime.profile &&
        point.path("input_runtime_sha256").asText() == runtimeBindingSha256 &&
        point.path("client_namespace_pid").asLong(-1) == runtime.clientNamespacePid &&
        point.path("controller_claim_host_xvfb_pid").asLong(-1) == runtime.hostXvfbPid &&
        point.path("controller_claim_host_xvfb_birth_epoch_millis").asLong(-1) ==
            runtime.hostXvfbBirthEpochMillis &&
        point.path("controller_claim_host_client_pid").asLong(-1) == runtime.hostClientPid &&
        point.path("controller_claim_host_client_birth_epoch_millis").asLong(-1) ==
            runtime.hostClientBirthEpochMillis &&
        point.path("client_session_id").asText() == runtime.clientSessionId &&
        point.path("epoch_id").asText() == runtime.epochId &&
        point.path("purpose").asText() == runtime.purpose &&
        point.path("display").asText() == runtime.display &&
        point.path("xauthority_sha256").asText() == runtime.xauthoritySha256 &&
        point.path("glfw_window").asLong(-1) > 0 && point.path("x11_window").asLong(-1) > 0 &&
        point.path("screen_class").asText() ==
            "net.minecraft.client.gui.screens.inventory.InventoryScreen" &&
        point.path("screen_width").asInt(-1) ==
            (if (runtime.profile == "1280x720@2") 1280 else 1920) &&
        point.path("screen_height").asInt(-1) ==
            (if (runtime.profile == "1280x720@2") 720 else 1080) &&
        point.path("gui_scale").asDouble(Double.NaN) ==
            (if (runtime.profile == "1280x720@2") 2.0 else 3.0) &&
        hover.path("screen_class") == point.path("screen_class") &&
        hover.path("player_uuid").asText() == actualActor &&
        hover.path("dimension") == target.path("dimension") &&
        hover.path("source").asText() ==
            "pinned EmiApi.getHoveredStack(actual_pointer)/last_rendered" &&
        hover.path("source_interaction_class").asText() ==
            "dev.emi.emi.screen.EmiScreenManager\$SidebarEmiStackInteraction" &&
        hover.path("emi_jar_sha256").asText() == immutableEmiJarSha256 &&
        hover.path("gui_x") == target.path("gui_x") && hover.path("gui_y") == target.path("gui_y") &&
        point.path("gui_x") == target.path("gui_x") && point.path("gui_y") == target.path("gui_y") &&
        hover.path("hover_kind") == target.path("hover_kind") &&
        hover.path("hover_id") == target.path("hover_id") &&
        hover.path("member_ids") == target.path("hover_member_ids") &&
        hover.path("projection_sha256").isTextual &&
        hover.path("projection_sha256").asText().matches(shaPattern) &&
        jsonFingerprint(projection) == hover.path("projection_sha256").asText()) {
        "actual pointer/rendered EMI hover differs from independent frozen review or producer projection bytes"
    }
    return hover.path("projection_sha256").asText()
}

internal fun resolveReviewedEmiMouseAction(action: com.bettercontent.tests.suite.drivers.DriverAction,
    reviewedProjectionSha256: String): com.bettercontent.tests.suite.drivers.DriverAction {
    if (action.id != "client.input_hold" || action.parameters.path("kind").asText() !in
        setOf("emi_mouse_left", "emi_mouse_right")) return action
    require(action.source == "client" && reviewedProjectionSha256.matches(shaPattern) &&
        action.parameters.path("hover_projection_sha256").isTextual &&
        action.parameters.path("hover_projection_sha256").asText() == EMI_HOVER_PROJECTION_MARKER)
    return action.copy(parameters = action.parameters.deepCopy().apply {
        put("hover_projection_sha256", reviewedProjectionSha256)
    })
}
