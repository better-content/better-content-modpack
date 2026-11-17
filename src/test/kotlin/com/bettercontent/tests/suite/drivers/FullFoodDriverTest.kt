package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Synthetic source-only regressions; do not run without separate human test permission. */
class FullFoodDriverTest {
    private val actor = "123e4567-e89b-42d3-a456-426614174000"
    private val hash = "a".repeat(64)
    private val runtime = "b".repeat(64)
    private fun observer(index: Int) = "123e4567-e89b-42d3-a456-${index.toString().padStart(12, '0')}"
    private fun authored(): JsonNode {
        val candidates = (0..9).joinToString(",") { """{"id":"test:food_$it","category":"c$it"}""" }
        val targets = (0..9).joinToString(",") { i -> """{"id":"test:food_$i","fixture_id":"food_$i",
            "hand":"main_hand","count":1,"food_before":0,"food_after":4,
            "saturation_before":0,"saturation_after":2,"item_after":"minecraft:air","count_after":0,
            "inventory_deltas":{"test:food_$i":-1},"effects":[],"acquisition_case_id":"food${i}_acquired",
            "observer_id":"${observer(i)}","use_kind":"eat"}""" }
        return suiteMapper.readTree("""{"player_uuid":"$actor","dimension":"minecraft:overworld",
            "input_profile":"1280x720@2","candidates":[$candidates],"mandatory":["test:food_0"],"targets":[$targets]}""")
    }
    @Test fun `full food plan replaces server forced use with arm and physical hold release status await`() {
        val plan = FullFoodDriver.plan(authored())
        assertEquals(90, plan.size)
        assertEquals(listOf("fixture.begin", "player.snapshot", "player.observe_item_use.arm",
            "client.input_hold", "client.input_release", "client.input_status", "player.observe_item_use.await",
            "player.snapshot", "fixture.restore"), plan.take(9).map { it.action.id })
        assertEquals(listOf("server", "server", "server", "client", "client", "client", "server", "server", "server"),
            plan.take(9).map { it.action.source })
        assertTrue(plan.none { it.action.id == "player.use_item" })
        assertEquals("@controller-witness-context-v1", plan[2].action.parameters.path("witness_context").asText())
        assertEquals("@input-runtime-sha256", plan[3].action.parameters.path("input_runtime_sha256").asText())
        assertEquals((0..9).map { "test:food_$it" }.toSet(), FullFoodDriver.fixtureProvisionedTargets(authored()))
        assertFalse("survival.food.promise" in FullFoodDriver.assertionIds)
    }
    private fun parsed(text: String): ObjectNode = suiteMapper.readTree(text) as ObjectNode
    private fun client(index: Int, stage: Int): ObjectNode {
        val result = parsed("""{"evidence_role":"authentic_robot_os_input_NOT_direct_callback_injection",
          "player_uuid":"$actor","dimension":"minecraft:overworld","profile":"1280x720@2",
          "hold_id":"food_$index","kind":"use_mouse","client_namespace_pid":17,
          "controller_claim_host_xvfb_pid":1702,"controller_claim_host_xvfb_birth_epoch_millis":1794818910000,
          "controller_claim_host_client_pid":1701,"controller_claim_host_client_birth_epoch_millis":1794818910001,
          "glfw_window":91,"x11_window":92,"client_session_id":"client-123e4567-e89b-12d3-a456-426614174000",
          "epoch_id":"epoch-00001","purpose":"diagnostic","input_runtime_sha256":"$runtime",
          "display":"127.0.0.1:27111","xauthority_sha256":"$hash","hold_sequence":3,
          "selected_slot":0,"inventory":[{"slot":0,"item_id":"test:food_$index","count":1}],
          "held_max_ticks":120,"held_state_ticks":0,"press_callback":false,"release_callback":false,
          "physical_input_down":false}""")
        when (stage) {
            0 -> result.put("physical_press_issued", true)
            1 -> {
                result.put("physical_release_issued", true); result.put("press_callback", true)
                result.put("held_state_ticks", 32); result.put("client_use_started", true)
                result.put("client_use_ended", true); result.put("client_use_start_tick", 10)
                result.put("client_use_end_tick", 43); result.put("auto_released_on_first_client_use_end", true)
            }
            2 -> {
                result.put("physical_release_verified", true); result.put("press_callback", true)
                result.put("release_callback", true); result.put("held_state_ticks", 32)
                result.put("client_use_started", true); result.put("client_use_ended", true)
                result.put("client_use_start_tick", 10); result.put("client_use_end_tick", 43)
                result.put("auto_released_on_first_client_use_end", true)
            }
        }
        return result
    }
    private fun receipts(extraEffect: Boolean = false): List<DriverObservation> {
        val plan = FullFoodDriver.plan(authored())
        return plan.mapIndexed { index, step ->
            val i = index / 9; val id = "test:food_$i"; val fixture = "food_$i"; val o = observer(i)
            val effects = if (extraEffect && i == 0) """[{"id":"minecraft:speed","duration":40,"amplifier":0}]""" else "[]"
            val stack = """{"item_id":"$id","count":1,"content_algorithm":"bc.playtest.stack-content.v1",
              "canonical_algorithm":"bc.playtest.stack-nbt.v1","content_sha256":"$hash","canonical_sha256":"$runtime"}"""
            val beforePhys = """{"food_level":0,"saturation":0,"thirst_supported":true,"thirst":10,"quenched":0}"""
            val afterPhys = """{"food_level":4,"saturation":2,"thirst_supported":true,"thirst":10,"quenched":0}"""
            val payload = when (index % 9) {
                0 -> parsed("""{"fixture_id":"$fixture","owner_case_id":"survival.food","player_uuid":"$actor",
                    "provisioned_item":"$id","provisioned_count":1,"food_before":20,"saturation_before":0,
                    "food_after":0,"saturation_after":0,"acquisition_origin":"authored_fixture_not_natural"}""")
                1 -> parsed("""{"uuid":"$actor","dimension":"minecraft:overworld","food_level":0,
                    "saturation":0,"held_item":"$id","held_count":1,"effects":[],"inventory":[{"slot":0,"item":"$id","count":1}]}""")
                2 -> parsed("""{"schema":"bc.playtest_passive_item_use_arm.v1","armed_before_input":true,
                    "observer_id":"$o","actor_uuid":"$actor","dimension":"minecraft:overworld",
                    "arm_request_sequence":2,"arm_action_ordinal":3,"fixture_id":"$fixture",
                    "acquisition_origin":"authored_fixture_not_natural","natural_acquisition_proven_by_native":false}""")
                3 -> client(i, 0)
                4 -> client(i, 1)
                5 -> client(i, 2)
                6 -> parsed("""{"schema":"bc.playtest_passive_item_use.v1","observer_id":"$o",
                    "actor_uuid":"$actor","dimension":"minecraft:overworld","arm_request_sequence":2,
                    "arm_action_ordinal":3,"fixture_id":"$fixture","acquisition_origin":"authored_fixture_not_natural",
                    "natural_acquisition_proven_by_native":false,"arm_tick":4,"packet_tick":5,"start_event_tick":5,
                    "finish_event_tick":38,"final_applied_state_tick":39,"narrow_event_chain_complete":true,
                    "packet_transport_origin_proven_by_native":false,"packet_count":1,"start_count":1,"finish_count":1,
                    "expected_provider":"living_entity_use_item","expected_kind":"eat","expected_item_id":"$id",
                    "hand":"main_hand","item_before_arm":$stack,"item_at_start":$stack,"item_at_finish":$stack,
                    "item_after_applied":null,"physiology_before":$beforePhys,"physiology_after":$afterPhys,
                    "packet_source":"ServerGamePacketListenerImpl.handleUseItem/server_thread_HEAD",
                    "start_source":"LivingEntityUseItemEvent.Start/MONITOR",
                    "finish_source":"LivingEntityUseItemEvent.Finish/MONITOR",
                    "final_source":"ServerTickEvent.END/actual_player_state"}""")
                7 -> parsed("""{"uuid":"$actor","dimension":"minecraft:overworld","food_level":4,
                    "saturation":2,"held_item":"minecraft:air","held_count":0,"effects":$effects,"inventory":[]}""")
                else -> parsed("""{"fixture_id":"$fixture","owner_case_id":"survival.food","player_uuid":"$actor",
                    "restored_inventory_slots":41,"owned_residuals":0,"cleanup_complete":true,"vanilla_restored":true,
                    "inventory_mismatched_slots":0,"food_restored":true,"effects_restored":true,"selected_slot_restored":true,
                    "food_after_restore":20,"saturation_after_restore":0,
                    "acquisition_origin":"authored_fixture_not_natural"}""")
            }
            DriverObservation(step.name, step.action.id, step.action.source, "completed", payload)
        }
    }
    @Test fun `full roster rejects unexpected effects server repeat premature release injection and failed restoration`() {
        assertEquals("passed", (evaluateCase(FullFoodDriver, authored(), receipts()) as DriverOutcome.Verdict).value.state)
        assertEquals("failed", (evaluateCase(FullFoodDriver, authored(), receipts(extraEffect = true)) as DriverOutcome.Verdict).value.state)
        val repeat = receipts().toMutableList()
        (repeat[6].payload as ObjectNode).put("packet_count", 2)
        assertTrue(evaluateCase(FullFoodDriver, authored(), repeat) is DriverOutcome.Fatal)
        val early = receipts().toMutableList()
        (early[4].payload as ObjectNode).put("auto_released_on_first_client_use_end", false)
        assertTrue(evaluateCase(FullFoodDriver, authored(), early) is DriverOutcome.Fatal)
        val injected = receipts().toMutableList()
        (injected[3].payload as ObjectNode).put("evidence_role", "diagnostic_direct_callback_injection_NOT_authentic_OS_input")
        assertTrue(evaluateCase(FullFoodDriver, authored(), injected) is DriverOutcome.Fatal)
        assertTrue(evaluateCase(FullFoodDriver, authored(), receipts().dropLast(1)) is DriverOutcome.Fatal)
        val failedRestore = receipts().mapIndexed { i, row -> if (i == 8) row.copy(kind = "failed") else row }
        assertTrue(evaluateCase(FullFoodDriver, authored(), failedRestore) is DriverOutcome.Fatal)
    }
    @Test fun `ten food trials require distinct epochs with independent raw observations`() {
        val requests = FullFoodDriver.expandTrials(authored(), "sealed-food-baseline")
        assertEquals(10, requests.size)
        val all = receipts()
        val trials = requests.mapIndexed { index, request ->
            val actual = all.drop(index * 9).take(9)
            assertEquals(9, FullFoodDriver.planTrial(request).size)
            assertEquals("passed", FullFoodDriver.evaluateTrial(request, actual).state)
            TrialReceipt(request.trialId, request.targetId, request.inputCheckpointId, "food-epoch-$index", "passed", actual, true, true)
        }
        assertEquals("passed", FullFoodDriver.aggregate(authored(), requests, trials).state)
        assertThrows(IllegalArgumentException::class.java) {
            FullFoodDriver.aggregate(authored(), requests, trials.mapIndexed { i, row ->
                if (i == 1) row.copy(epochId = "food-epoch-0") else row })
        }
        assertThrows(IllegalArgumentException::class.java) {
            FullFoodDriver.aggregate(authored(), requests, trials.mapIndexed { i, row ->
                if (i == 0) row.copy(state = "passed", observations = receipts(extraEffect = true).take(9)) else row })
        }
        assertThrows(IllegalArgumentException::class.java) {
            FullFoodDriver.aggregate(authored(), requests, trials.mapIndexed { i, row ->
                if (i == 1) row.copy(inputCheckpointVerified = false) else row })
        }
        assertThrows(IllegalArgumentException::class.java) {
            FullFoodDriver.aggregate(authored(), requests, trials.mapIndexed { i, row ->
                if (i == 1) row.copy(cleanupComplete = false) else row })
        }
    }
    @Test fun `missing food undocumented field or repeated observer refuses full coverage`() {
        val missing = authored().deepCopy<ObjectNode>()
        (missing.path("targets") as ArrayNode).remove(9)
        assertThrows(IllegalArgumentException::class.java) { FullFoodDriver.plan(missing) }
        val extra = authored().deepCopy<ObjectNode>().put("generic_success", true)
        assertThrows(IllegalArgumentException::class.java) { FullFoodDriver.plan(extra) }
        val duplicate = authored().deepCopy<ObjectNode>()
        (duplicate.path("targets")[1] as ObjectNode).put("observer_id", observer(0))
        assertThrows(IllegalArgumentException::class.java) { FullFoodDriver.plan(duplicate) }
    }
}
