package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Synthetic source-only OS/client triple consistency, NOT a real mouse/server/world test. */
class AuthenticAttackInputEvidenceTest {
    private val actor = "123e4567-e89b-42d3-a456-426614174000"
    private val runtime = "a".repeat(64)
    private val projection = "b".repeat(64)
    private val bound = "c".repeat(64)
    private val hit = suiteMapper.readTree("""{"player_uuid":"$actor","dimension":"minecraft:overworld",
        "crosshair_source":"Minecraft.hitResult/BlockHitResult/read_only_existing_render_target",
        "crosshair_is_natural_bound_target":false,"crosshair_packet_transport_proven":false,
        "crosshair_projection_sha256":"$projection","crosshair":{"player_uuid":"$actor",
        "dimension":"minecraft:overworld","x":1,"y":64,"z":2,"face":"north",
        "hit_x":1.5,"hit_y":64.5,"hit_z":2.0}}""")
    private fun snapshot(stage: Int): ObjectNode {
        val sample = """{"native_tick":10,"same_existing_hit_result":true,"press_callback":true,
          "held_state_ticks":2,"real_glfw_left_down":true,"attack_mapping_down":true,
          "on_ground":true,"x":1.0,"y":64.0,"z":1.0,"yaw":0.0,"pitch":0.0}"""
        val first = """{"native_tick":9,"same_existing_hit_result":true,"press_callback":false,
          "held_state_ticks":0,"real_glfw_left_down":false,"attack_mapping_down":false,
          "on_ground":true,"x":1.0,"y":64.0,"z":1.0,"yaw":0.0,"pitch":0.0}"""
        val rows = if (stage == 0) "[$first]" else "[$first,$sample]"
        val row = suiteMapper.readTree("""{"evidence_role":"authentic_robot_os_input_NOT_direct_callback_injection",
          "player_uuid":"$actor","dimension":"minecraft:overworld","profile":"1280x720@2",
          "kind":"attack_mouse","hold_id":"attack_a","input_runtime_sha256":"$runtime",
          "client_namespace_pid":17,"controller_claim_host_xvfb_pid":1702,
          "controller_claim_host_xvfb_birth_epoch_millis":1794818910000,
          "controller_claim_host_client_pid":1701,"controller_claim_host_client_birth_epoch_millis":1794818910001,
          "glfw_window":91,"x11_window":92,"client_session_id":"client-123e4567-e89b-12d3-a456-426614174000",
          "epoch_id":"epoch-00001","purpose":"diagnostic","display":"127.0.0.1:27111",
          "xauthority_sha256":"${"d".repeat(64)}","hold_sequence":3,
          "held_max_ticks":120,"held_state_ticks":${if (stage == 0) 0 else 2},
          "attack_support_scope":"one_frozen_in_world_block_vanilla_hitResult_only",
          "attack_pre_press_crosshair_projection_sha256":"$projection",
          "attack_bound_target_sha256_unverified_by_client":"$bound",
          "attack_target_loss_tick":${if (stage == 0) -1 else 11},
          "attack_key_up_drained_before_keybind_tick":${if (stage == 0) -1 else 11},
          "attack_auto_released_on_target_loss":${stage != 0},
          "attack_samples":$rows,"physical_input_down":${stage == 0},
          "press_callback":${stage != 0},"release_callback":${stage != 0}}""") as ObjectNode
        if (stage == 0) row.put("physical_press_issued", true)
        if (stage == 1) row.put("physical_release_issued", true)
        if (stage == 2) row.put("physical_release_verified", true)
        return row
    }
    private fun valid(a: ObjectNode = snapshot(0), b: ObjectNode = snapshot(1), c: ObjectNode = snapshot(2)) =
        validateAuthenticBlockAttack(hit, a, b, c, actor, "minecraft:overworld", "1280x720@2", "attack_a",
            1, 64, 2, "north", bound, runtime)
    @Test fun `one frozen crosshair OS attack callback and pre-keybind key-up are required`() {
        assertTrue(valid())
        assertFalse(valid(b = snapshot(1).put("attack_key_up_drained_before_keybind_tick", -1)))
        assertFalse(valid(b = snapshot(1).put("attack_auto_released_on_target_loss", false)))
        assertFalse(valid(a = snapshot(0).put("evidence_role", "diagnostic_direct_callback_injection_NOT_authentic_OS_input")))
        assertFalse(valid(a = snapshot(0).put("attack_pre_press_crosshair_projection_sha256", "e".repeat(64))))
    }
    @Test fun `moving actor or substituting a second target invalidates physical sample`() {
        val moving = snapshot(1)
        ((moving.path("attack_samples") as ArrayNode)[1] as ObjectNode).put("x", 2.0)
        assertFalse(valid(b = moving))
        assertFalse(validateAuthenticBlockAttack(hit, snapshot(0), snapshot(1), snapshot(2), actor,
            "minecraft:overworld", "1280x720@2", "attack_a", 1, 64, 3, "north", bound, runtime))
    }
}
