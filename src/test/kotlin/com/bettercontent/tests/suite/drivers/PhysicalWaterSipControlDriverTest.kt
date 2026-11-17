package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Source-only contract regressions; no test execution is authorized by authoring these. */
class PhysicalWaterSipControlDriverTest {
    private val actor = "123e4567-e89b-42d3-a456-426614174000"
    private val observer = "123e4567-e89b-42d3-a456-426614174001"
    private fun target() = suiteMapper.readTree("""{
      "dimension":"minecraft:overworld","input_profile":"1280x720@2","observer_id":"$observer","hold_id":"water_bottle_0",
      "water":{"player_uuid":"$actor","hand":"main_hand","source_item":"minecraft:potion",
        "result_item":"minecraft:glass_bottle","source_count":1,"result_count":1,
        "source_purity":0,"result_purity":0,"thirst_before":8,"thirst_after":12,
        "quenched_before":8,"quenched_after":12,"fluids_before":[],"fluids_after":[]}}""")
    @Test fun `duration drinking uses physical input and passive real packet no server forced use`() {
        val steps = PhysicalWaterSipControlDriver.plan(target())
        assertEquals(listOf("player.water_snapshot", "player.observe_item_use.arm", "client.input_hold",
            "client.input_release", "client.input_status", "player.observe_item_use.await", "player.water_snapshot"),
            steps.map { it.action.id })
        assertEquals(listOf("server", "server", "client", "client", "client", "server", "server"),
            steps.map { it.action.source })
        assertTrue(steps.none { it.action.id == "player.use_item" })
        assertEquals("@controller-witness-context-v1", steps[1].action.parameters.path("witness_context").asText())
        assertEquals("@input-runtime-sha256", steps[2].action.parameters.path("input_runtime_sha256").asText())
    }
    private fun physical(stage: Int): ObjectNode {
        val row = suiteMapper.readTree("""{"evidence_role":"authentic_robot_os_input_NOT_direct_callback_injection",
          "player_uuid":"$actor","dimension":"minecraft:overworld","profile":"1280x720@2",
          "hold_id":"water_bottle_0","kind":"use_mouse","client_namespace_pid":17,
          "controller_claim_host_xvfb_pid":1702,"controller_claim_host_xvfb_birth_epoch_millis":1794818910000,
          "controller_claim_host_client_pid":1701,"controller_claim_host_client_birth_epoch_millis":1794818910001,
          "glfw_window":91,"x11_window":92,"client_session_id":"client-123e4567-e89b-12d3-a456-426614174000",
          "epoch_id":"epoch-00001","purpose":"diagnostic","input_runtime_sha256":"${"a".repeat(64)}",
          "display":"127.0.0.1:27111","xauthority_sha256":"${"b".repeat(64)}","hold_sequence":3,
          "selected_slot":0,"inventory":[{"slot":0,"item_id":"minecraft:potion","count":1}],
          "held_max_ticks":120,"held_state_ticks":0,"press_callback":false,"release_callback":false,
          "physical_input_down":false,"key_up_drained_before_next_use_handling":false,
          "first_use_end_before_keybind_tick":-1,"key_up_drained_before_keybind_tick":-1}""") as ObjectNode
        if (stage == 0) row.put("physical_press_issued", true) else {
            row.put("press_callback", true).put("release_callback", true).put("held_state_ticks", 32)
                .put("client_use_started", true).put("client_use_ended", true)
                .put("client_use_start_tick", 10).put("client_use_end_tick", 43)
                .put("auto_released_on_first_client_use_end", true)
                .put("key_up_drained_before_next_use_handling", true)
                .put("first_use_end_before_keybind_tick", 43).put("key_up_drained_before_keybind_tick", 43)
            if (stage == 1) row.put("physical_release_issued", true) else row.put("physical_release_verified", true)
        }
        return row
    }
    private fun observations(): List<DriverObservation> {
        val sourceHash = "a".repeat(64)
        val sourceStack = """{"item_id":"minecraft:potion","count":1,"content_algorithm":"bc.playtest.stack-content.v1",
          "canonical_algorithm":"bc.playtest.stack-nbt.v1","content_sha256":"$sourceHash","canonical_sha256":"${"b".repeat(64)}"}"""
        val resultStack = """{"item_id":"minecraft:glass_bottle","count":1,"content_sha256":"${"c".repeat(64)}"}"""
        val pre = """{"player_uuid":"$actor","hand":"main_hand","thirst":8,"quenched":8,
          "held_item":"minecraft:potion","held_count":1,"item_purity":0,"fluids":[],
          "capability_source":"dev.ghen.thirst.foundation.common.capability.ModCapabilities.PLAYER_THIRST",
          "purity_source":"dev.ghen.thirst.content.purity.WaterPurity.getPurity"}"""
        val post = """{"player_uuid":"$actor","hand":"main_hand","thirst":12,"quenched":12,
          "held_item":"minecraft:glass_bottle","held_count":1,"item_purity":0,"fluids":[],
          "capability_source":"dev.ghen.thirst.foundation.common.capability.ModCapabilities.PLAYER_THIRST",
          "purity_source":"dev.ghen.thirst.content.purity.WaterPurity.getPurity"}"""
        val arm = """{"armed_before_input":true,"observer_id":"$observer","arm_request_sequence":3,
          "arm_action_ordinal":2,"acquisition_origin":"preexisting_unattributed",
          "natural_acquisition_proven_by_native":false}"""
        val event = """{"schema":"bc.playtest_passive_item_use.v1","observer_id":"$observer",
          "actor_uuid":"$actor","dimension":"minecraft:overworld","expected_item_id":"minecraft:potion",
          "expected_provider":"living_entity_use_item","expected_kind":"drink","hand":"main_hand",
          "acquisition_origin":"preexisting_unattributed","natural_acquisition_proven_by_native":false,
          "packet_transport_origin_proven_by_native":false,"narrow_event_chain_complete":true,
          "packet_count":1,"start_count":1,"finish_count":1,"arm_request_sequence":3,"arm_action_ordinal":2,
          "packet_source":"ServerGamePacketListenerImpl.handleUseItem/server_thread_HEAD",
          "start_source":"LivingEntityUseItemEvent.Start/MONITOR","finish_source":"LivingEntityUseItemEvent.Finish/MONITOR",
          "final_source":"ServerTickEvent.END/actual_player_state","arm_tick":10,"packet_tick":11,
          "start_event_tick":12,"finish_event_tick":40,"final_applied_state_tick":41,
          "item_before_arm":$sourceStack,"item_at_start":$sourceStack,"item_at_finish":$sourceStack,
          "item_after_applied":$resultStack,
          "physiology_before":{"thirst_supported":true,"thirst":8,"quenched":8,"food_level":10,"saturation":0},
          "physiology_after":{"thirst_supported":true,"thirst":12,"quenched":12,"food_level":10,"saturation":0}}"""
        val data = listOf(pre, arm, "", "", "", event, post)
        return PhysicalWaterSipControlDriver.plan(target()).mapIndexed { index, step ->
            val payload = if (index in 2..4) physical(index - 2) else suiteMapper.readTree(data[index])
            DriverObservation(step.name, step.action.id, step.action.source, "completed", payload)
        }
    }
    @Test fun `complete physical sip requires passive server receipt and independent water snapshots`() {
        val evidence = observations()
        assertEquals("passed", (evaluateCase(PhysicalWaterSipControlDriver, target(), evidence) as DriverOutcome.Verdict).value.state)
        val repeated = evidence.toMutableList()
        repeated[5] = repeated[5].copy(payload = (repeated[5].payload as ObjectNode).deepCopy().put("packet_count", 2))
        assertTrue(evaluateCase(PhysicalWaterSipControlDriver, target(), repeated) is DriverOutcome.Fatal)
        val fake = evidence.toMutableList()
        fake[2] = fake[2].copy(payload = physical(0).put("evidence_role", "diagnostic_direct_callback_injection_NOT_authentic_OS_input"))
        assertTrue(evaluateCase(PhysicalWaterSipControlDriver, target(), fake) is DriverOutcome.Fatal)
    }
    @Test fun `unsupported instant-use and unreviewed profile fail closed`() {
        val extra = target().deepCopy<ObjectNode>().put("server_force", true)
        assertThrows(IllegalArgumentException::class.java) { PhysicalWaterSipControlDriver.plan(extra) }
        val wrong = target().deepCopy<ObjectNode>().put("input_profile", "unknown")
        assertThrows(IllegalArgumentException::class.java) { PhysicalWaterSipControlDriver.plan(wrong) }
        val offhand = target().deepCopy<ObjectNode>()
        (offhand.path("water") as ObjectNode).put("hand", "off_hand")
        assertThrows(IllegalArgumentException::class.java) { PhysicalWaterSipControlDriver.plan(offhand) }
    }
}
