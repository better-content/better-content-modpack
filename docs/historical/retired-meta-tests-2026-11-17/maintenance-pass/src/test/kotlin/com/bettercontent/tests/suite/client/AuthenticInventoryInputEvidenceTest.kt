package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Synthetic OS-input receipts only; no tests executed, no GUI or screenshot opened. */
class AuthenticInventoryInputEvidenceTest {
    private val actor = "11111111-1111-1111-1111-111111111111"
    private val sha = "a".repeat(64)
    private fun row(screen: Boolean): ObjectNode = suiteMapper.createObjectNode().apply {
        put("evidence_role", "authentic_robot_os_input_NOT_direct_callback_injection")
        put("kind", "inventory_key"); put("player_uuid", actor); put("dimension", "minecraft:overworld")
        put("profile", "1280x720@2"); put("hold_id", "inventory-open"); put("input_runtime_sha256", sha)
        put("held_max_ticks", 6); put("client_namespace_pid", 3)
        put("controller_claim_host_xvfb_pid", 123); put("controller_claim_host_xvfb_birth_epoch_millis", 1000)
        put("controller_claim_host_client_pid", 124); put("controller_claim_host_client_birth_epoch_millis", 1001)
        put("glfw_window", 15); put("x11_window", 16)
        put("client_session_id", "client-11111111-1111-1111-1111-111111111111")
        put("epoch_id", "epoch-00001"); put("purpose", "diagnostic")
        put("display", "127.0.0.1:8"); put("xauthority_sha256", sha); put("hold_sequence", 1)
        put("client_spectator", false)
        put("screen_class", if (screen) "net.minecraft.client.gui.screens.inventory.InventoryScreen" else null as String?)
        put("menu_type", if (screen) "net.minecraft.world.inventory.InventoryMenu" else null as String?)
        put("screen_title", if (screen) "Crafting" else null as String?)
    }
    private fun receipts(): Triple<ObjectNode, ObjectNode, ObjectNode> {
        val start = row(false).put("physical_press_issued", true).put("held_state_ticks", 0)
        val released = row(true).put("physical_release_issued", true).put("press_callback", true)
            .put("held_state_ticks", 1)
        val status = row(true).put("physical_release_verified", true).put("press_callback", true)
            .put("release_callback", true).put("physical_input_down", false).put("held_state_ticks", 1)
        return Triple(start, released, status)
    }
    private fun valid(start: ObjectNode, release: ObjectNode, status: ObjectNode) =
        validateAuthenticInventoryInput(start, release, status, actor, "minecraft:overworld",
            "1280x720@2", "inventory-open", sha)
    @Test fun genuineBoundSameWindowKeyDownAndKeyUpMustOpenActualInventory() {
        val (start, release, status) = receipts()
        assertTrue(valid(start, release, status))
        assertFalse(valid(start, release, status.deepCopy().put("release_callback", false)))
        assertFalse(valid(start, release.deepCopy().put("screen_class", "fake.Menu"), status))
        assertFalse(valid(start, release, status.deepCopy().put("glfw_window", 99)))
        assertFalse(valid(start, release, status.deepCopy().put("physical_input_down", true)))
        assertFalse(valid(start.deepCopy().put("screen_class", "fake.Screen"), release, status))
    }
}
