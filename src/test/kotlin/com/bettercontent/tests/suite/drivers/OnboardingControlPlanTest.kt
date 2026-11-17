package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Synthetic source-only; not an observed first-join world, actual key press, provider-persistence or live terrain test. */
class OnboardingControlPlanTest {
    private val actor = "123e4567-e89b-42d3-a456-426614174000"
    private val observer = "123e4567-e89b-42d3-a456-426614174001"
    private val artifact = "a".repeat(64)
    private val config = "c".repeat(64)
    private val runtime = "b".repeat(64)
    private val bounds = """{"dimension":"minecraft:overworld","x_min":1,"x_max":3,
        "y_min":3,"y_max":6,"z_min":1,"z_max":3}"""
    private val armActor = """{"dimension":"minecraft:overworld","block_x":2,"block_y":5,"block_z":2}"""
    private val requested = """{"dimension":"minecraft:overworld","x":2,"y":5,"z":2}"""
    private val resolved = """{"dimension":"minecraft:overworld","x":2,"y":4,"z":2}"""
    private val press = """{"dimension":"minecraft:overworld","block_x":2,"block_y":5,"block_z":2,
        "spectator":true,"modifier_down":true,"native_tick":8}"""
    private fun json(text: String): ObjectNode = suiteMapper.readTree(text) as ObjectNode
    private fun authored(): ObjectNode = json("""{"player_uuid":"$actor","dimension":"minecraft:overworld",
        "observer_id":"$observer","hold_id":"initial_spawn_0","input_profile":"1280x720@2",
        "provider_artifact_sha256":"$artifact","provider_config_sha256":"$config",
        "anchor_x":2,"anchor_y":5,"anchor_z":2,"min_build_height":0,"max_build_height":8,
        "request_bounds":$bounds}""")
    private fun column(): String {
        val rows = (0..7).joinToString(",") { y -> """{"y":$y,"block_id":"${if (y == 3) "minecraft:stone" else "minecraft:air"}","properties":{}}""" }
        return """{"dimension":"minecraft:overworld","x":2,"z":2,"min_build_height":0,
            "max_build_height":8,"scan_top_y":5,"loaded_only_no_chunk_forcing":true,
            "column_complete":true,"material_classification":"none_native_raw_only","columns":[$rows]}"""
    }
    private fun client(stage: Int): ObjectNode {
        val result = json("""{"evidence_role":"authentic_robot_os_input_NOT_direct_callback_injection",
            "player_uuid":"$actor","dimension":"minecraft:overworld","profile":"1280x720@2",
            "hold_id":"initial_spawn_0","kind":"onboarding_chord","input_runtime_sha256":"$runtime",
            "client_namespace_pid":17,"controller_claim_host_xvfb_pid":1702,
            "controller_claim_host_xvfb_birth_epoch_millis":1794818910000,
            "controller_claim_host_client_pid":1701,"controller_claim_host_client_birth_epoch_millis":1794818910001,
            "glfw_window":91,"x11_window":92,"client_session_id":"client-123e4567-e89b-12d3-a456-426614174000",
            "epoch_id":"epoch-00001","purpose":"diagnostic","display":"127.0.0.1:27111",
            "xauthority_sha256":"$artifact","hold_sequence":3,"held_max_ticks":20,
            "held_state_ticks":${if (stage == 0) 0 else 2},"physical_input_down":false,
            "client_spectator":true,"onboarding_active":true,"onboarding_selection_required":true,
            "onboarding_mode_spawn_only":true,"press_callback":${stage != 0},
            "modifier_press_callback":${stage != 0},"release_callback":${stage == 2},
            "modifier_release_callback":${stage == 2},"onboarding_key_press_context":${if (stage == 0) "null" else press}}""")
        when (stage) {
            0 -> result.put("physical_press_issued", true)
            1 -> result.put("physical_release_issued", true)
            2 -> result.put("physical_release_verified", true)
        }
        return result
    }
    private fun observations(): List<DriverObservation> {
        val steps = OnboardingControlPlan.plan(authored())
        val bodies: List<JsonNode> = listOf(
            json("""{"schema":"bc.playtest_passive_onboarding_arm.v1","armed_before_input":true,
                "observer_id":"$observer","actor_uuid":"$actor","arm_request_sequence":3,
                "arm_action_ordinal":4,"request_bounds":$bounds,"actor_at_arm":$armActor}"""),
            client(0), client(1), client(2),
            json("""{"schema":"bc.playtest_passive_onboarding.v1","observer_id":"$observer",
                "actor_uuid":"$actor","dimension":"minecraft:overworld","arm_request_sequence":3,
                "arm_action_ordinal":4,"expected_provider":"better_spawns:FinalizeSelectionPacket",
                "expected_mode":"none","provider_artifact_sha256_controller_declared":"$artifact",
                "provider_config_sha256_controller_declared":"$config","provider_artifact_certified_by_native":false,
                "request_bounds":$bounds,"actor_at_arm":$armActor,"requested_site":$requested,
                "actor_at_request":$armActor,"terrain_pre_commit":${column()},
                "actual_committed_site":$resolved,"persisted_site_at_event":$resolved,
                "persisted_site":$resolved,"vanilla_respawn_site":$resolved,
                "actual_final_actor":{"dimension":"minecraft:overworld","block_x":2,"block_y":4,
                    "block_z":2,"x":2.5,"y":4.0,"z":2.5,"mode":"survival"},
                "site_after":{"base":{"y":3,"block_id":"better_spawns:respawn_marker","properties":{}},
                    "feet":{"y":4,"block_id":"minecraft:air","properties":{}},
                    "head":{"y":5,"block_id":"minecraft:air","properties":{}},
                    "loaded_only_no_chunk_forcing":true,"marker_base_observed":true,"feet_head_air_observed":true},
                "actual_kit":"spawn_only","final_onboarding_complete":true,"narrow_event_chain_complete":true,
                "arm_tick":1,"packet_handling_tick":4,"finalization_event_tick":5,
                "packet_handler_return_tick":6,"final_applied_state_tick":7,
                "packet_count":1,"finalization_event_count":1,"packet_handler_return_count":1}"""),
            json("""{"uuid":"$actor","dimension":"minecraft:overworld","mode":"survival",
                "x":2.5,"y":4.0,"z":2.5}"""))
        return steps.mapIndexed { i, step -> DriverObservation(step.name, step.action.id, step.action.source,
            "completed", bodies[i]) }
    }
    @Test fun `reviewed bounded first login plan has no hidden gamemode or held async finalization`() {
        val plan = OnboardingControlPlan.plan(authored())
        assertEquals(listOf("player.observe_onboarding.arm", "client.input_hold", "client.input_release",
            "client.input_status", "player.observe_onboarding.await", "player.snapshot"), plan.map { it.action.id })
        assertEquals("@controller-witness-context-v1", plan[0].action.parameters.path("witness_context").asText())
        assertEquals("@arm-request-sequence", plan[4].action.parameters.path("arm_request_sequence").asText())
        assertEquals("@input-runtime-sha256", plan[1].action.parameters.path("input_runtime_sha256").asText())
        assertEquals("passed", OnboardingControlPlan.evaluate(authored(), observations()).state)
    }
    @Test fun `callback spoof missing marker and actor or committed site drift refuse qualification`() {
        val callback = observations().toMutableList()
        (callback[3].payload.path("onboarding_key_press_context") as ObjectNode).put("spectator", false)
        assertThrows(IllegalArgumentException::class.java) { OnboardingControlPlan.evaluate(authored(), callback) }
        val marker = observations().toMutableList()
        (marker[4].payload.path("site_after").path("base") as ObjectNode).put("block_id", "minecraft:stone")
        assertEquals("failed", OnboardingControlPlan.evaluate(authored(), marker).state)
        val actorDrift = observations().toMutableList()
        (actorDrift[0].payload.path("actor_at_arm") as ObjectNode).put("block_y", 6)
        assertThrows(IllegalArgumentException::class.java) { OnboardingControlPlan.evaluate(authored(), actorDrift) }
        val changedSite = observations().toMutableList()
        (changedSite[4].payload.path("actual_committed_site") as ObjectNode).put("y", 5)
        assertEquals("failed", OnboardingControlPlan.evaluate(authored(), changedSite).state)
    }
    @Test fun `provider pin area drift and unsupported height fail before any input plan`() {
        val wrong = authored()
        (wrong.path("request_bounds") as ObjectNode).put("x_max", 4)
        assertThrows(IllegalArgumentException::class.java) { OnboardingControlPlan.plan(wrong) }
        val invalid = authored().put("provider_artifact_sha256", "unreviewed")
        assertThrows(IllegalArgumentException::class.java) { OnboardingControlPlan.plan(invalid) }
        val tall = authored().put("max_build_height", 1024)
        assertThrows(IllegalArgumentException::class.java) { OnboardingControlPlan.plan(tall) }
    }
    private fun fullCase() = json("""{"first_join":{"player_uuid":"$actor","dimension":"minecraft:overworld",
        "spawn_x":2,"spawn_y":5,"spawn_z":2,"maximum_horizontal_distance":1,"maximum_vertical_distance":1,
        "allowed_biomes":["minecraft:plains"],"allowed_ground_blocks":["minecraft:stone"]},
        "onboarding":${authored()}}""")
    private fun actualFirstJoin(prior: Boolean = false, mode: String = "spectator"): DriverObservation =
        DriverObservation("first-login", "world.entry", "server", "completed", json("""{
          "uuid":"$actor","dimension":"minecraft:overworld","mode":"$mode","x":2.5,"y":5.0,"z":2.5,
          "login_observed":true,"login_tick":80,"current_tick":82,"login_dimension":"minecraft:overworld",
          "login_x":2.5,"login_y":5.0,"login_z":2.5,"spawn_x":2,"spawn_y":5,"spawn_z":2,
          "first_join_status":"${if (prior) "prior_playerdata_seen" else "no_prior_playerdata_file_observed"}",
          "playerdata_preexists_at_login":$prior,"login_biome":"minecraft:plains",
          "login_ground_block":"minecraft:stone","login_feet_air":true,"login_head_air":true,
          "login_ground_solid":true,"current_chunk_loaded":true,
          "native_login_source":"PlayerEvent.PlayerLoggedInEvent"}"""))
    @Test fun `registered full onboarding requires first real spectator login and passive world finalization`() {
        val plan = WorldOnboardingDriver.plan(fullCase())
        assertEquals("world.onboarding", WorldOnboardingDriver.caseId)
        assertTrue("world.onboarding" in DriverRegistry.supportedCases)
        assertEquals(7, plan.size)
        assertEquals("world.entry", plan.first().action.id)
        assertEquals("player.observe_onboarding.arm", plan[1].action.id)
        assertEquals("passed", (evaluateCase(WorldOnboardingDriver, fullCase(),
            listOf(actualFirstJoin()) + observations()) as DriverOutcome.Verdict).value.state)
        assertTrue(evaluateCase(WorldOnboardingDriver, fullCase(),
            listOf(actualFirstJoin(mode = "survival")) + observations()) is DriverOutcome.Fatal)
        assertEquals("failed", (evaluateCase(WorldOnboardingDriver, fullCase(),
            listOf(actualFirstJoin(prior = true)) + observations()) as DriverOutcome.Verdict).value.state)
    }
    @Test fun `first-join and onboarding case cannot be drifted or passed by six-step synthetic continuation alone`() {
        assertTrue(evaluateCase(WorldOnboardingDriver, fullCase(), observations()) is DriverOutcome.Fatal)
        val wrongActor = fullCase()
        (wrongActor.path("first_join") as ObjectNode).put("player_uuid", observer)
        assertThrows(IllegalArgumentException::class.java) { WorldOnboardingDriver.plan(wrongActor) }
        val outside = actualFirstJoin().copy(payload = actualFirstJoin().payload.deepCopy<ObjectNode>().put("x", 10.5))
        assertTrue(evaluateCase(WorldOnboardingDriver, fullCase(), listOf(outside) + observations()) is DriverOutcome.Fatal)
        val fake = observations().toMutableList()
        (fake[4].payload.path("actual_committed_site") as ObjectNode).put("y", 5)
        assertEquals("failed", (evaluateCase(WorldOnboardingDriver, fullCase(),
            listOf(actualFirstJoin()) + fake) as DriverOutcome.Verdict).value.state)
    }
}
