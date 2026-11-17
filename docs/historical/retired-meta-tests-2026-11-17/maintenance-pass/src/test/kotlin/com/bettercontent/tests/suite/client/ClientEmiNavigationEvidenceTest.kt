package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.jsonFingerprint
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

/** Synthetic source-only independent pre-press gate; NOT RUN without human test approval. */
class ClientEmiNavigationEvidenceTest {
    private val actor = UUID.randomUUID().toString()
    private val emi = "a".repeat(64)
    private val binding = "b".repeat(64)
    private val runtime = ClientInputRuntime("bc.playtest_client_input_runtime.v1", "diagnostic",
        "campaign-${UUID.randomUUID()}", UUID.randomUUID().toString(), "epoch-00001",
        "client-${UUID.randomUUID()}", "c".repeat(64), "d".repeat(64),
        "1280x720@2", "127.0.0.1:12345", "/home/dev/.tmp/auth", "e".repeat(64),
        1001, 1234, "/usr/bin/Xvfb", "f".repeat(64), 1002, 1235, listOf(1002, 24))
    private val target = suiteMapper.valueToTree<JsonNode>(mapOf("player_uuid" to actor,
        "dimension" to "minecraft:overworld", "input_profile" to runtime.profile,
        "emi_jar_sha256" to emi, "inventory_hold_id" to "inventory-1",
        "navigation_hold_id" to "nav-1", "navigation" to "recipes", "gui_x" to 25,
        "gui_y" to 40, "hover_kind" to "stack", "hover_id" to "minecraft:stone",
        "hover_member_ids" to listOf("minecraft:stone"), "expected_category_id" to "minecraft:crafting",
        "expected_visible_recipe_ids" to listOf("minecraft:stone_recipe")))
    private fun point() = suiteMapper.valueToTree<ObjectNode>(mapOf("physical_pointer_move_issued" to true,
        "evidence_role" to "authentic_robot_os_pointer_NOT_direct_callback_injection",
        "player_uuid" to actor, "dimension" to "minecraft:overworld", "profile" to runtime.profile,
        "input_runtime_sha256" to binding, "client_namespace_pid" to 24,
        "controller_claim_host_xvfb_pid" to 1001,
        "controller_claim_host_xvfb_birth_epoch_millis" to 1234,
        "controller_claim_host_client_pid" to 1002,
        "controller_claim_host_client_birth_epoch_millis" to 1235,
        "client_session_id" to runtime.clientSessionId, "epoch_id" to runtime.epochId,
        "purpose" to runtime.purpose, "display" to runtime.display,
        "xauthority_sha256" to runtime.xauthoritySha256,
        "glfw_window" to 100L, "x11_window" to 101L,
        "screen_class" to "net.minecraft.client.gui.screens.inventory.InventoryScreen",
        "screen_width" to 1280, "screen_height" to 720, "gui_scale" to 2.0,
        "gui_x" to 25, "gui_y" to 40))
    private fun hover(): ObjectNode = suiteMapper.createObjectNode().apply {
        set<JsonNode>("member_ids", suiteMapper.valueToTree(listOf("minecraft:stone")))
        put("source_interaction_class", "dev.emi.emi.screen.EmiScreenManager\$SidebarEmiStackInteraction")
        put("hover_kind", "stack"); put("hover_id", "minecraft:stone")
        put("screen_class", "net.minecraft.client.gui.screens.inventory.InventoryScreen")
        put("gui_x", 25); put("gui_y", 40); put("emi_jar_sha256", emi)
        put("source", "pinned EmiApi.getHoveredStack(actual_pointer)/last_rendered")
        put("projection_sha256", jsonFingerprint(this))
        put("player_uuid", actor); put("dimension", "minecraft:overworld")
    }
    @Test fun `only frozen reviewed target and real point can yield projection for physical mouse`() {
        val hash = independentlyReviewedEmiHoverProjection(target, point(), hover(), actor, runtime, binding, emi)
        assertEquals(hash, hover().path("projection_sha256").asText())
        val authored = com.bettercontent.tests.suite.drivers.DriverAction("client.input_hold",
            suiteMapper.createObjectNode().put("kind", "emi_mouse_left")
                .put("hover_projection_sha256", EMI_HOVER_PROJECTION_MARKER), 30, "client")
        assertEquals(hash, resolveReviewedEmiMouseAction(authored, hash).parameters
            .path("hover_projection_sha256").asText())
        assertThrows(IllegalArgumentException::class.java) {
            independentlyReviewedEmiHoverProjection(target, point(), hover().apply { put("hover_id", "minecraft:dirt") },
                actor, runtime, binding, emi)
        }
        assertThrows(IllegalArgumentException::class.java) {
            independentlyReviewedEmiHoverProjection(target, point().apply { put("gui_x", 26) }, hover(),
                actor, runtime, binding, emi)
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveReviewedEmiMouseAction(authored, "not-a-reviewed-hash")
        }
    }
}
