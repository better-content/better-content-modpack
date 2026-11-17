package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Pure forged JSON regressions only. UNRUN; no Minecraft, GUI, EMI provider, Robot or image capture. */
class AuthenticEmiGuiEvidenceTest {
    private val actor = "11111111-1111-1111-1111-111111111111"
    private val sha = "a".repeat(64)
    private fun hover() = suiteMapper.createObjectNode().apply {
        put("screen_class", "net.minecraft.client.gui.screens.inventory.InventoryScreen")
        put("gui_x", 140); put("gui_y", 70); put("hover_kind", "tag")
        put("hover_id", "minecraft:logs"); set<JsonNode>("member_ids", suiteMapper.createArrayNode().add("minecraft:oak_log"))
        put("source_interaction_class", "dev.emi.emi.screen.EmiScreenManager\$SidebarEmiStackInteraction")
        put("source", "pinned EmiApi.getHoveredStack(actual_pointer)/last_rendered")
        put("emi_jar_sha256", sha); put("projection_sha256", "b".repeat(64))
    }
    private fun row(screen: String, hovered: JsonNode): ObjectNode = suiteMapper.createObjectNode().apply {
        put("evidence_role", "authentic_robot_os_input_NOT_direct_callback_injection")
        put("kind", "emi_mouse_left"); put("player_uuid", actor); put("dimension", "minecraft:overworld")
        put("profile", "1280x720@2"); put("hold_id", "emi-recipe-key"); put("input_runtime_sha256", sha)
        put("held_max_ticks", 6); put("client_namespace_pid", 3)
        put("controller_claim_host_xvfb_pid", 123); put("controller_claim_host_xvfb_birth_epoch_millis", 1000)
        put("controller_claim_host_client_pid", 124); put("controller_claim_host_client_birth_epoch_millis", 1001)
        put("glfw_window", 15); put("x11_window", 16)
        put("client_session_id", "client-11111111-1111-1111-1111-111111111111")
        put("epoch_id", "epoch-00001"); put("purpose", "diagnostic")
        put("display", "127.0.0.1:8"); put("xauthority_sha256", sha); put("hold_sequence", 7)
        put("screen_class", screen); set<JsonNode>("emi_pre_press_hover", hovered.deepCopy())
    }
    private fun receipts(): List<ObjectNode> {
        val hover = hover()
        val hold = row("net.minecraft.client.gui.screens.inventory.InventoryScreen", hover)
            .put("physical_press_issued", true).put("held_state_ticks", 0)
        val release = row("dev.emi.emi.screen.RecipeScreen", hover)
            .put("physical_release_issued", true).put("press_callback", true).put("held_state_ticks", 1)
        val status = row("dev.emi.emi.screen.RecipeScreen", hover)
            .put("physical_release_verified", true).put("press_callback", true)
            .put("release_callback", true).put("physical_input_down", false).put("held_state_ticks", 1)
        return listOf(hold, release, status, hover)
    }
    private fun valid(rows: List<ObjectNode>) = validateAuthenticEmiMouseInput(rows[0], rows[1], rows[2], rows[3],
        actor, "minecraft:overworld", "1280x720@2", "emi-recipe-key", sha, "recipes", sha)
    @Test fun actualMouseReleaseAndFrozenObservedTagAreRequired() {
        val rows = receipts(); assertTrue(valid(rows))
        fun mutate(index: Int, block: (ObjectNode) -> Unit): List<ObjectNode> = rows.mapIndexed { i, row ->
            row.deepCopy().apply { if (i == index) block(this) }
        }
        assertFalse(valid(mutate(2) { it.put("release_callback", false) }))
        assertFalse(valid(mutate(2) { it.put("physical_input_down", true) }))
        assertFalse(valid(mutate(3) { it.put("hover_id", "minecraft:planks") }))
        assertFalse(valid(mutate(0) { it.put("client_namespace_pid", 33) }))
        assertFalse(valid(mutate(0) { it.put("screen_class", "fake.Menu") }))
        assertFalse(validateAuthenticEmiMouseInput(rows[0], rows[1], rows[2], rows[3], actor,
            "minecraft:overworld", "1280x720@2", "emi-recipe-key", sha, "uses", sha))
    }
}
