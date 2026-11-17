package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Authored regressions only: these tests are NOT authorized to run in this worker phase. */
class AuthenticHeldInputEvidenceTest {
    private val player = "123e4567-e89b-12d3-a456-426614174000"
    private val sha = "a".repeat(64)
    private fun start(): ObjectNode = suiteMapper.readTree("""
        {"evidence_role":"authentic_robot_os_input_NOT_direct_callback_injection",
         "player_uuid":"$player","dimension":"minecraft:overworld","profile":"1280x720@2","hold_id":"food-test",
         "kind":"use_mouse","client_namespace_pid":17,"controller_claim_host_xvfb_pid":1702,
         "controller_claim_host_xvfb_birth_epoch_millis":1794818910000,"controller_claim_host_client_pid":1701,
         "controller_claim_host_client_birth_epoch_millis":1794818910001,
         "glfw_window":91,"x11_window":92,"client_session_id":"client-123e4567-e89b-12d3-a456-426614174000",
         "epoch_id":"epoch-00001","purpose":"diagnostic","input_runtime_sha256":"$sha",
         "display":"127.0.0.1:27111","xauthority_sha256":"$sha","hold_sequence":2,"held_max_ticks":120,
         "selected_slot":0,"inventory":[{"slot":0,"item_id":"minecraft:bread","count":1}],
         "held_state_ticks":0,"press_callback":false,"release_callback":false,"physical_input_down":false,
         "key_up_drained_before_next_use_handling":false,"first_use_end_before_keybind_tick":-1,
         "key_up_drained_before_keybind_tick":-1,"physical_press_issued":true}
    """.trimIndent()) as ObjectNode
    private fun rows(): Triple<ObjectNode, ObjectNode, ObjectNode> {
        val begin = start()
        val release = begin.deepCopy().apply {
            remove("physical_press_issued"); put("physical_release_issued", true)
            put("held_state_ticks", 6); put("press_callback", true); put("release_callback", true)
            put("physical_input_down", false)
            put("client_use_started", true); put("client_use_ended", true)
            put("client_use_start_tick", 30); put("client_use_end_tick", 64)
            put("auto_released_on_first_client_use_end", true)
            put("key_up_drained_before_next_use_handling", true)
            put("first_use_end_before_keybind_tick", 64)
            put("key_up_drained_before_keybind_tick", 64)
        }
        val status = release.deepCopy().apply {
            remove("physical_release_issued"); put("physical_release_verified", true)
            put("release_callback", true)
        }
        return Triple(begin, release, status)
    }
    private fun check(rows: Triple<ObjectNode, ObjectNode, ObjectNode>) = validateAuthenticHeldInput(
        rows.first, rows.second, rows.third, player, "minecraft:overworld", "1280x720@2", "food-test", "minecraft:bread", sha)
    @Test fun boundedNativeHoldWithCallbackStateAndReleaseCanBeAccepted() {
        assertTrue(check(rows()))
    }
    @Test fun injectionOrMissingCallbackStateReleaseCannotImpersonateOSInput() {
        val rows = rows(); rows.first.put("evidence_role", "diagnostic_direct_callback_injection_NOT_authentic_OS_input")
        assertFalse(check(rows))
        val noCallback = rows(); noCallback.second.put("press_callback", false)
        assertFalse(check(noCallback))
        val noHeldState = rows(); noHeldState.second.put("held_state_ticks", 0)
        assertFalse(check(noHeldState))
        val prematureRelease = rows(); prematureRelease.second.put("auto_released_on_first_client_use_end", false)
        assertFalse(check(prematureRelease))
        val repeatBeforeEnd = rows(); repeatBeforeEnd.second.put("client_use_end_tick", 30)
        assertFalse(check(repeatBeforeEnd))
        val noReleaseCallback = rows(); noReleaseCallback.third.put("release_callback", false)
        assertFalse(check(noReleaseCallback))
        val callbackAfterReleaseReceipt = rows(); callbackAfterReleaseReceipt.second.put("release_callback", false)
        assertFalse(check(callbackAfterReleaseReceipt))
        val undrained = rows(); undrained.second.put("key_up_drained_before_next_use_handling", false)
        assertFalse(check(undrained))
        val lateDrain = rows(); lateDrain.second.put("key_up_drained_before_keybind_tick", 63)
        assertFalse(check(lateDrain))
        val inconsistentStatus = rows(); inconsistentStatus.third.put("key_up_drained_before_keybind_tick", 65)
        assertFalse(check(inconsistentStatus))
        val stillDown = rows(); stillDown.third.put("physical_input_down", true)
        assertFalse(check(stillDown))
    }
    @Test fun wrongActorWindowDisplayProfileItemAndSequenceAreRejected() {
        val playerSwap = rows(); playerSwap.third.put("player_uuid", "someone_else")
        assertFalse(check(playerSwap))
        val windowSwap = rows(); windowSwap.second.put("glfw_window", 42)
        assertFalse(check(windowSwap))
        val authoritySwap = rows(); authoritySwap.third.put("xauthority_sha256", "b".repeat(64))
        assertFalse(check(authoritySwap))
        val profileSwap = rows(); profileSwap.third.put("profile", "1920x1080@3")
        assertFalse(check(profileSwap))
        val sequenceSwap = rows(); sequenceSwap.second.put("hold_sequence", 3)
        assertFalse(check(sequenceSwap))
        val birthSwap = rows(); birthSwap.second.put("controller_claim_host_xvfb_birth_epoch_millis", 1794818910001L)
        assertFalse(check(birthSwap))
        val namespaceSwap = rows(); namespaceSwap.second.put("client_namespace_pid", 19)
        assertFalse(check(namespaceSwap))
        val bindingSwap = rows(); bindingSwap.second.put("input_runtime_sha256", "b".repeat(64))
        assertFalse(check(bindingSwap))
        val itemSwap = rows(); (itemSwap.first.path("inventory")[0] as ObjectNode).put("item_id", "minecraft:stone")
        assertFalse(check(itemSwap))
    }
}
