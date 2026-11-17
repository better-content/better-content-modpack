package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Synthetic data only. No test was run, no window, GUI, Robot, or world is started by this source. */
class AuthenticWalkInputEvidenceTest {
    private val actor = "11111111-1111-1111-1111-111111111111"
    private val digest = "a".repeat(64)
    private fun sample(tick: Int, z: Double): ObjectNode = suiteMapper.createObjectNode().apply {
        put("native_tick", tick); put("x", 1.5); put("y", 65.0); put("z", z); put("yaw", 0.0)
        put("on_ground", true)
        listOf("swimming", "in_fluid", "passenger", "sprinting", "crouching", "flying", "jump_down",
            "strafe_down", "back_down", "interact_down").forEach { put(it, false) }
    }
    private fun base(): ObjectNode = suiteMapper.createObjectNode().apply {
        put("evidence_role", "authentic_robot_os_input_NOT_direct_callback_injection")
        put("kind", "walk_forward"); put("walk_support_scope", "ordinary_flat_ground_forward_only")
        put("player_uuid", actor); put("dimension", "minecraft:overworld"); put("profile", "1280x720@2")
        put("hold_id", "walk-1"); put("input_runtime_sha256", digest); put("held_max_ticks", 24)
        put("client_namespace_pid", 31); put("controller_claim_host_xvfb_pid", 101)
        put("controller_claim_host_xvfb_birth_epoch_millis", 1000)
        put("controller_claim_host_client_pid", 111); put("controller_claim_host_client_birth_epoch_millis", 1001)
        put("glfw_window", 44); put("x11_window", 45)
        put("client_session_id", "client-11111111-1111-1111-1111-111111111111")
        put("epoch_id", "epoch-00001"); put("purpose", "diagnostic")
        put("display", "127.0.0.1:7"); put("xauthority_sha256", digest)
        put("hold_sequence", 7); put("screen_class", null as String?)
        put("walk_start_yaw", 0.0); put("x", 1.5); put("y", 65.0); put("z", 2.5); put("yaw", 0.0)
    }
    private fun receipts(): Triple<ObjectNode, ObjectNode, ObjectNode> {
        val first = sample(10, 2.5); val next = sample(11, 2.9)
        val start = base().apply {
            put("physical_press_issued", true); put("held_state_ticks", 0)
            set<com.fasterxml.jackson.databind.JsonNode>("walk_samples", suiteMapper.createArrayNode().add(first))
        }
        val release = base().apply {
            put("physical_release_issued", true); put("press_callback", true); put("held_state_ticks", 1)
            set<com.fasterxml.jackson.databind.JsonNode>("walk_samples", suiteMapper.createArrayNode().add(first).add(next))
        }
        val status = release.deepCopy().apply {
            remove("physical_release_issued"); put("physical_release_verified", true)
            put("release_callback", true); put("physical_input_down", false); put("z", 2.9)
            put("on_ground", true); put("in_fluid", false)
        }
        return Triple(start, release, status)
    }
    private fun valid(start: ObjectNode, release: ObjectNode, status: ObjectNode) =
        validateAuthenticWalkInput(start, release, status, actor, "minecraft:overworld", "1280x720@2",
            "walk-1", digest, 24)
    @Test fun physicalKeyboardPressBoundedSampleAndCallbackReleaseAreAllNecessary() {
        val (start, release, status) = receipts()
        assertTrue(valid(start, release, status))
        assertFalse(valid(start, release, status.deepCopy().apply { put("release_callback", false) }))
        assertFalse(valid(start, release, status.deepCopy().apply { put("physical_input_down", true) }))
        assertFalse(valid(start, release, status.deepCopy().apply { put("client_namespace_pid", 999) }))
        assertFalse(valid(start, release, status.deepCopy().apply { remove("on_ground") }))
        assertFalse(valid(start, release, status.deepCopy().apply { remove("in_fluid") }))
        assertFalse(valid(start, release, status.deepCopy().apply { put("on_ground", "true") }))
        assertFalse(valid(start, release, status.deepCopy().apply { put("in_fluid", "false") }))
        assertFalse(valid(start, release, status.deepCopy().apply { put("on_ground", false) }))
        assertFalse(valid(start, release, status.deepCopy().apply { put("in_fluid", true) }))
    }
    @Test fun yawDriftStrafeTeleportFakeSampleAndWrongActorFail() {
        val (start, release, status) = receipts()
        fun mutated(mutate: (ObjectNode) -> Unit): Boolean {
            val r = release.deepCopy(); val s = status.deepCopy()
            mutate(r.path("walk_samples")[1] as ObjectNode); mutate(s.path("walk_samples")[1] as ObjectNode)
            return valid(start, r, s)
        }
        assertFalse(mutated { it.put("yaw", 30.0) })
        assertFalse(mutated { it.put("strafe_down", true) })
        assertFalse(mutated { it.put("z", 99.0) })
        assertFalse(valid(start, release, status.deepCopy().apply { put("player_uuid", "other") }))
    }
}
