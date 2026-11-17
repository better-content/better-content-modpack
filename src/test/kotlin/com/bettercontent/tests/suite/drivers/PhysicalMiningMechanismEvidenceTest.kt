package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.VerifiedRawEvidence
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Entirely synthetic packet+client projections; no Java test, world, OS mouse, or raw file was run. */
class PhysicalMiningMechanismEvidenceTest {
    private val actor = "123e4567-e89b-42d3-a456-426614174000"
    private val observer = "123e4567-e89b-42d3-a456-426614174001"
    private val entity = "123e4567-e89b-42d3-a456-426614174002"
    private val sha = "a".repeat(64); private val cross = "b".repeat(64)
    private val bound = "c".repeat(64); private val trace = "d".repeat(64)
    private fun target(positive: Boolean = true, instant: Boolean = false,
        expectedCallbacks: Int = 0) = suiteMapper.readTree("""{
      "player_uuid":"$actor","dimension":"minecraft:overworld","selector_id":"reviewed_ore_a",
      "x":100,"y":64,"z":200,"face":"north","block_id":"minecraft:iron_ore",
      "tool_item_id":"minecraft:iron_pickaxe","tool_canonical_sha256":"$sha",
      "tool_acquisition_case_id":"acquire.iron-pickaxe","observer_id":"$observer",
      "hold_id":"mining_0","input_profile":"1280x720@2","max_ticks":120,
      "expected_packet_variant":"${if (instant) "instant_start_only" else "progress_start_stop"}",
      "expected_drop_outcome":"${if (positive) "one_full_pickup" else "no_drop_negative"}",
      "expected_drop_item_id":${if (positive) "\"minecraft:raw_iron\"" else "null"},
      "expected_drop_count":${if (positive) 1 else 0},
      "expected_tool_break_callback_count":$expectedCallbacks}""")
    private fun obj(json: String) = suiteMapper.readTree(json) as ObjectNode
    private fun full41(selected: JsonNode, output: JsonNode? = null): ObjectNode {
        val empty = obj("""{"slot":0,"empty":true,"item":null,"count":0,
            "stack_canonical_algorithm":null,"stack_canonical_sha256":null}""")
        val slots = suiteMapper.createArrayNode().apply {
            repeat(41) { index ->
                add((if (index == 0) selected else if (index == 1 && output != null) output else empty)
                    .deepCopy<ObjectNode>().put("slot", index))
            }
        }
        return suiteMapper.createObjectNode().put("schema", "bc.playtest_vanilla_inventory_slots.v1")
            .put("source", "actual_server_player_vanilla_inventory_0_40")
            .put("external_capabilities_included", false).put("sealed_playerdata_proven_by_native", false)
            .apply { set<JsonNode>("slots", slots) }
    }
    private fun client(stage: Int): ObjectNode {
        val first = """{"native_tick":9,"same_existing_hit_result":true,"press_callback":false,
            "held_state_ticks":0,"real_glfw_left_down":false,"attack_mapping_down":false,
            "on_ground":true,"x":1.0,"y":64.0,"z":1.0,"yaw":0.0,"pitch":0.0}"""
        val second = """{"native_tick":10,"same_existing_hit_result":true,"press_callback":true,
            "held_state_ticks":2,"real_glfw_left_down":true,"attack_mapping_down":true,
            "on_ground":true,"x":1.0,"y":64.0,"z":1.0,"yaw":0.0,"pitch":0.0}"""
        val rows = if (stage == 0) "[$first]" else "[$first,$second]"
        return obj("""{"evidence_role":"authentic_robot_os_input_NOT_direct_callback_injection",
          "player_uuid":"$actor","dimension":"minecraft:overworld","profile":"1280x720@2",
          "kind":"attack_mouse","hold_id":"mining_0","input_runtime_sha256":"$sha",
          "client_namespace_pid":17,"controller_claim_host_xvfb_pid":1702,
          "controller_claim_host_xvfb_birth_epoch_millis":1794818910000,
          "controller_claim_host_client_pid":1701,"controller_claim_host_client_birth_epoch_millis":1794818910001,
          "glfw_window":91,"x11_window":92,"client_session_id":"client-123e4567-e89b-12d3-a456-426614174000",
          "epoch_id":"epoch-00001","purpose":"diagnostic","display":"127.0.0.1:27111",
          "xauthority_sha256":"${"e".repeat(64)}","hold_sequence":3,
          "held_max_ticks":120,"held_state_ticks":${if (stage == 0) 0 else 2},
          "attack_support_scope":"one_frozen_in_world_block_vanilla_hitResult_only",
          "attack_pre_press_crosshair_projection_sha256":"$cross",
          "attack_bound_target_sha256_unverified_by_client":"$bound",
          "attack_target_loss_tick":${if (stage == 0) -1 else 11},
          "attack_key_up_drained_before_keybind_tick":${if (stage == 0) -1 else 11},
          "attack_auto_released_on_target_loss":${stage != 0},"attack_samples":$rows,
          "physical_input_down":${stage == 0},"press_callback":${stage != 0},
          "release_callback":${stage != 0},
          "${if (stage == 0) "physical_press_issued" else if (stage == 1) "physical_release_issued" else "physical_release_verified"}":true}""")
    }
    private fun rows(positive: Boolean, instant: Boolean, harvest: Boolean, terminalTick: Int,
        context: JsonNode, terminal: JsonNode, beforeTool: JsonNode, afterTool: JsonNode,
        awaitTool: JsonNode, callbackTool: JsonNode?, callbackCount: Int,
        refilled: Boolean, source: JsonNode, selectedBefore: JsonNode, selectedAfter: JsonNode): List<JsonNode> {
        val actions = if (instant) listOf("START_DESTROY_BLOCK") else listOf("START_DESTROY_BLOCK", "STOP_DESTROY_BLOCK")
        val kinds = buildList {
            add("decoded_remote_packet_then_actual_server_handler"); add("actual_gamemode_handle")
            add("actual_START_destroy_progress_return"); add("real_inbound_packet_after_gamemode")
            if (!instant) { add("decoded_remote_packet_then_actual_server_handler"); add("actual_gamemode_handle") }
            add("tool_before_actual_gamemode_destroy")
            add("actual_gamemode_destroy_head"); add("actual_forge_break_event")
            add("actual_gamemode_stored_harvest_return")
            if (callbackTool != null) {
                add("actual_tool_hurt_and_break_consumer_before")
                repeat(callbackCount) { add("actual_living_entity_equipment_break_callback") }
                add("actual_tool_hurt_and_break_consumer_after")
            }
            if (positive) add("source_entity_join_before_insertion")
            add("tool_after_actual_gamemode_destroy")
            add("actual_gamemode_destroy_return")
            if (!instant) add("real_inbound_packet_after_gamemode")
            if (positive) {
                add("actual_pickup_pre"); add("actual_pickup_after_inventory_add")
                if (refilled) add("tool_selected_slot_refilled_by_actual_source_pickup")
                add("actual_source_terminal_after_real_pickup_discard")
            } else add("no_scoped_packet_path_drop_through_deadline")
            add("tool_at_bound_await"); add("packet_path_observation_terminal")
        }
        val stack = source
        var beforePacket = 0; var afterPacket = 0; var gameMode = 0; var callbackIndex = 0
        return kinds.mapIndexed { index, kind ->
            suiteMapper.createObjectNode().apply {
                put("schema", "bc.playtest_passive_packet_dig_trace.v5")
                put("event_index", index + 1); put("kind", kind)
                put("tick", when (kind) {
                    "no_scoped_packet_path_drop_through_deadline" -> 166
                    "tool_at_bound_await", "packet_path_observation_terminal" -> terminalTick
                    "actual_source_terminal_after_real_pickup_discard" -> terminalTick
                    "actual_pickup_pre", "actual_pickup_after_inventory_add",
                    "tool_selected_slot_refilled_by_actual_source_pickup" -> 22
                    "decoded_remote_packet_then_actual_server_handler" -> if (beforePacket == 0) 11 else 20
                    // The synchronous START handler belongs to the same tick as its inbound
                    // packet and progress return; only the second STOP handler is at tick20.
                    "actual_gamemode_handle" -> if (gameMode == 0) 11 else 20
                    "real_inbound_packet_after_gamemode" -> if (afterPacket == 0) 11 else 20
                    else -> if (instant) 11 else if (kind == "actual_START_destroy_progress_return") 11 else 20
                })
                put("actual_native_session_id", "server-sess"); put("actual_actor_uuid", actor)
                put("arm_request_sequence", 2); set<JsonNode>("controller_declared_arm_context", context)
                put("bound_target_sha256", bound); put("crosshair_projection_sha256", cross)
                put("actual_capability_id", "player.observe_dig.arm_v5"); put("context_certified_by_native", false)
                put("x", 100); put("y", 64); put("z", 200); put("face", "NORTH")
                if (index == 0) putNull("prior_row_sha256") else put("prior_row_sha256", trace)
                val packet = when (kind) {
                    "decoded_remote_packet_then_actual_server_handler" -> beforePacket++
                    "real_inbound_packet_after_gamemode" -> afterPacket++
                    "actual_gamemode_handle" -> gameMode++
                    else -> -1
                }
                if (packet >= 0) { put("packet_action", actions[packet]); put("packet_sequence", packet + 1) }
                when (kind) {
                    "decoded_remote_packet_then_actual_server_handler" -> set<ObjectNode>("observed",
                        suiteMapper.createObjectNode().put("same_decoded_packet_object", true)
                            .put("bound_actual_remote_connection", true)
                            .put("decoded_ingress_monotonic_nanos", 10000 + index)
                            .put("decoded_ingress_thread_name", "synthetic-netty"))
                    "actual_START_destroy_progress_return" -> set<ObjectNode>("observed",
                        suiteMapper.createObjectNode().put("direct_gamemode_START_caller", true)
                            .put("actual_progress_per_tick", if (instant) 1.0 else 0.4))
                    "tool_before_actual_gamemode_destroy" -> set<ObjectNode>("observed",
                        suiteMapper.createObjectNode().put("vanilla_hotbar_slot", 0).apply {
                            set<JsonNode>("tool", beforeTool)
                        })
                    "actual_tool_hurt_and_break_consumer_before" -> set<ObjectNode>("observed",
                        suiteMapper.createObjectNode().put("same_selected_item_object", true).apply {
                            set<JsonNode>("tool", requireNotNull(callbackTool))
                        })
                    "actual_living_entity_equipment_break_callback" -> set<ObjectNode>("observed",
                        suiteMapper.createObjectNode().put("tool_callback_index", ++callbackIndex)
                            .put("equipment_slot", "MAINHAND")
                            .put("interaction_hand", "MAIN_HAND").put("vanilla_hotbar_slot", 0)
                            .put("same_hurt_and_break_item_object", true).apply {
                                set<JsonNode>("tool_before_shrink", requireNotNull(callbackTool))
                            })
                    "actual_tool_hurt_and_break_consumer_after" -> set<ObjectNode>("observed",
                        suiteMapper.createObjectNode().put("actual_callback_count", callbackCount))
                    "tool_after_actual_gamemode_destroy", "tool_at_bound_await" -> set<ObjectNode>("observed",
                        suiteMapper.createObjectNode().put("vanilla_hotbar_slot", 0).apply {
                            set<JsonNode>("tool", if (kind == "tool_at_bound_await") awaitTool else afterTool)
                        })
                    "packet_path_observation_terminal" -> set<ObjectNode>("observed",
                        suiteMapper.createObjectNode().put("physical_observation_complete", true)
                            .put("tool_capture_complete", true)
                            .put("reviewed_expectation_matches", true)) // overwritten with exact receipt below
                    "actual_gamemode_stored_harvest_return" -> set<ObjectNode>("observed",
                        suiteMapper.createObjectNode().put("actual_stored_can_harvest", harvest))
                    "actual_forge_break_event" -> set<JsonNode>("observed", obj("""{"block_id":"minecraft:iron_ore",
                        "air":false,"server_tick":10}""")) // overwritten with exact ARM below
                    "actual_gamemode_destroy_return" -> set<ObjectNode>("observed",
                        suiteMapper.createObjectNode().put("air", true))
                    "source_entity_join_before_insertion" -> set<ObjectNode>("observed",
                        suiteMapper.createObjectNode().put("item_entity_uuid", entity).apply {
                            set<JsonNode>("source_stack", stack)
                        })
                    "actual_pickup_pre" -> set<ObjectNode>("observed",
                        suiteMapper.createObjectNode().put("item_entity_uuid", entity).apply {
                            set<JsonNode>("source_before", stack); set<JsonNode>("inventory_before", suiteMapper.createArrayNode())
                            set<JsonNode>("vanilla_inventory_slots_before_v1", selectedBefore)
                        })
                    "actual_pickup_after_inventory_add" -> set<ObjectNode>("observed",
                        suiteMapper.createObjectNode().put("item_entity_uuid", entity).apply {
                            set<JsonNode>("acquired_stack", stack); set<JsonNode>("inventory_before", suiteMapper.createArrayNode())
                            set<JsonNode>("inventory_after", suiteMapper.createArrayNode())
                            set<JsonNode>("vanilla_inventory_slots_before_v1", selectedBefore)
                            set<JsonNode>("vanilla_inventory_slots_after_v1", selectedAfter)
                            set<ObjectNode>("source_after_inventory_add", suiteMapper.createObjectNode().put("kind", "empty"))
                        })
                    "tool_selected_slot_refilled_by_actual_source_pickup" -> set<ObjectNode>("observed",
                        suiteMapper.createObjectNode().put("source_entity_uuid", entity)
                            .put("vanilla_hotbar_slot", 0).apply {
                                set<JsonNode>("source_stack", stack); set<JsonNode>("acquired_stack", stack)
                                set<JsonNode>("selected_before", selectedBefore.path("slots")[0])
                                set<JsonNode>("selected_after", selectedAfter.path("slots")[0])
                                set<JsonNode>("selected_live_after", awaitTool)
                            })
                    "no_scoped_packet_path_drop_through_deadline", "actual_source_terminal_after_real_pickup_discard" ->
                        set<JsonNode>("observed", terminal)
                    else -> Unit
                }
            }
        }
    }
    private fun evidence(positive: Boolean = true, instant: Boolean = false,
        expectedPositive: Boolean = positive, expectedInstant: Boolean = instant,
        actualHarvest: Boolean = positive, broken: Boolean = false, refilled: Boolean = false,
        actualCallbacks: Int = if (broken) 1 else 0,
        expectedCallbacks: Int = if (broken) 1 else 0): Pair<JsonNode, List<DriverObservation>> {
        require(!refilled || broken && positive)
        val params = target(expectedPositive, expectedInstant, expectedCallbacks)
        val player = suiteMapper.createObjectNode().put("uuid", actor).put("dimension", "minecraft:overworld")
            .put("selected_slot", 0).put("held_item", "minecraft:iron_pickaxe")
            .put("held_stack_canonical_sha256", sha)
            .set<JsonNode>("inventory", suiteMapper.createArrayNode()) as ObjectNode
        val beforeTool = obj("""{"kind":"nonempty","item_id":"minecraft:iron_pickaxe","count":1,
            "canonical_algorithm":"bc.playtest.stack-nbt.v1","canonical_sha256":"$sha",
            "content_algorithm":"bc.playtest.stack-content.v1","content_sha256":"${"8".repeat(64)}",
            "damage_live":0,"damage_nbt_int":0}""")
        val afterTool = if (broken) obj("""{"kind":"empty","count":0}""") else
            obj("""{"kind":"nonempty","item_id":"minecraft:iron_pickaxe","count":1,
            "canonical_algorithm":"bc.playtest.stack-nbt.v1","canonical_sha256":"${"9".repeat(64)}",
            "content_algorithm":"bc.playtest.stack-content.v1","content_sha256":"${"8".repeat(64)}",
            "damage_live":1,"damage_nbt_int":1}""")
        val callbackTool = if (actualCallbacks > 0) obj("""{"kind":"nonempty","item_id":"minecraft:iron_pickaxe","count":1,
            "canonical_algorithm":"bc.playtest.stack-nbt.v1","canonical_sha256":"${"9".repeat(64)}",
            "content_algorithm":"bc.playtest.stack-content.v1","content_sha256":"${"8".repeat(64)}",
            "damage_live":250,"damage_nbt_int":250}""") else null
        val sourceStack = obj("""{"item_id":"minecraft:raw_iron","count":1,
            "canonical_algorithm":"bc.playtest.stack-nbt.v1","canonical_sha256":"${"f".repeat(64)}",
            "content_algorithm":"bc.playtest.stack-content.v1","content_sha256":"${"e".repeat(64)}",
            "provider_scope":"vanilla_saved_ItemStack_NBT_including_ForgeCaps_not_external_inventory_adapter"}""")
        val refillTool = obj("""{"kind":"nonempty","item_id":"minecraft:raw_iron","count":1,
            "canonical_algorithm":"bc.playtest.stack-nbt.v1","canonical_sha256":"${"f".repeat(64)}",
            "content_algorithm":"bc.playtest.stack-content.v1","content_sha256":"${"e".repeat(64)}",
            "damage_live":0,"damage_nbt_int":null}""")
        val awaitTool = if (refilled) refillTool else afterTool
        val emptySlot = obj("""{"slot":0,"empty":true,"item":null,"count":0,
            "stack_canonical_algorithm":null,"stack_canonical_sha256":null}""")
        val toolSlot = obj("""{"slot":0,"empty":false,"item":"minecraft:iron_pickaxe","count":1,
            "stack_canonical_algorithm":"bc.playtest.stack-nbt.v1","stack_canonical_sha256":"${"9".repeat(64)}"}""")
        val acquiredSlot = obj("""{"slot":0,"empty":false,"item":"minecraft:raw_iron","count":1,
            "stack_canonical_algorithm":"bc.playtest.stack-nbt.v1","stack_canonical_sha256":"${"f".repeat(64)}"}""")
        val armSlot = toolSlot.deepCopy().put("stack_canonical_sha256", sha)
        player.set<JsonNode>("inventory_slots_v1", full41(armSlot))
        val pre41 = full41(if (broken) emptySlot else toolSlot)
        val post41 = full41(if (refilled) acquiredSlot else if (broken) emptySlot else toolSlot,
            if (refilled) null else acquiredSlot)
        val afterPlayer = player.deepCopy().put("held_item", if (refilled) "minecraft:raw_iron"
            else if (broken) "minecraft:air" else "minecraft:iron_pickaxe")
            .put("held_stack_canonical_sha256", if (refilled) "${"f".repeat(64)}"
                else if (broken) "${"0".repeat(64)}" else "${"9".repeat(64)}")
            .put("held_count", if (broken && !refilled) 0 else 1)
            .apply { set<JsonNode>("inventory_slots_v1", if (positive) post41 else
                full41(if (broken) emptySlot else toolSlot)) }
        val blockBefore = obj("""{"dimension":"minecraft:overworld","x":100,"y":64,"z":200,
            "block_state":{"id":"minecraft:iron_ore","properties":{}}}""")
        val blockAfter = obj("""{"dimension":"minecraft:overworld","x":100,"y":64,"z":200,
            "block_state":{"id":"minecraft:air","properties":{}}}""")
        val crosshair = obj("""{"player_uuid":"$actor","dimension":"minecraft:overworld",
            "crosshair_source":"Minecraft.hitResult/BlockHitResult/read_only_existing_render_target",
            "crosshair_is_natural_bound_target":false,"crosshair_packet_transport_proven":false,
            "crosshair_projection_sha256":"$cross","crosshair":{"player_uuid":"$actor",
            "dimension":"minecraft:overworld","x":100,"y":64,"z":200,"face":"north",
            "hit_x":100.5,"hit_y":64.5,"hit_z":200.0}}""")
        val context = suiteMapper.createObjectNode().put("campaign_id", "synthetic_only")
        val armBlock = obj("""{"dimension":"minecraft:overworld","x":100,"y":64,"z":200,
            "block_id":"minecraft:iron_ore","block_state":"Block{minecraft:iron_ore}",
            "air":false,"server_tick":10}""")
        val arm = suiteMapper.createObjectNode().put("schema", "bc.playtest_passive_packet_dig_arm.v5")
            .put("vanilla_tool_hotbar_slot", 0)
            .put("observer_id", observer).put("actor_uuid", actor).put("dimension", "minecraft:overworld")
            .put("selector_id", "reviewed_ore_a").put("expected_packet_variant",
                if (expectedInstant) "instant_start_only" else "progress_start_stop")
            .put("expected_drop_outcome", if (expectedPositive) "one_full_pickup" else "no_drop_negative")
            .put("expected_harvest_result", expectedPositive).put("natural_origin_proven_by_native", false)
            .put("bound_target_sha256", bound).put("crosshair_projection_sha256", cross)
            .put("arm_request_sequence", 2).put("arm_action_ordinal", 4).put("deadline_tick", 166)
        arm.set<JsonNode>("arm_tool", beforeTool)
        arm.set<JsonNode>("arm_block", armBlock)
        arm.set<JsonNode>("controller_declared_arm_context", context)
        val terminal = if (positive) obj("""{"source_kind":"absent","removed":true,"in_world":false}""")
            else obj("""{"source_kind":"no_scoped_drop_in_observed_interval","observed_through_deadline_tick":166}""")
        val afterNative = suiteMapper.createObjectNode().put("air", true)
        val receipt = arm.deepCopy().put("schema", "bc.playtest_passive_packet_dig.v5")
            .put("observation_complete", true).put("actual_start_progress_observed_count", 1)
            .put("actual_START_destroy_progress_per_tick", if (instant) 1.0 else 0.4)
            .put("actual_stored_harvest_observed_count", 1).put("actual_stored_can_harvest", actualHarvest)
            .put("actual_packet_variant", if (instant) "instant_start_only" else "progress_start_stop")
            .put("actual_drop_outcome", if (positive) "one_full_pickup" else "no_drop_negative")
            .put("reviewed_expectation_matches", positive == expectedPositive && instant == expectedInstant &&
                actualHarvest == expectedPositive)
            .put("inbound_before_count", if (instant) 1 else 2).put("inbound_after_count", if (instant) 1 else 2)
            .put("game_mode_call_count", if (instant) 1 else 2).put("destroy_call_count", 1)
            .put("break_event_count", 1).put("aborts", 0).put("start_packet_sequence", 1)
            .put("stop_packet_sequence", if (instant) -1 else 2).put("arm_tick", 10).put("start_tick", 11)
            .put("stop_tick", if (instant) -1 else 20)
            .put("terminal_tick", if (positive && expectedPositive) 23 else 166)
            .put("acquisition_origin", "preexisting_unattributed")
            .put("packet_os_origin_proven_by_native", false).put("natural_acquisition_proven_by_native", false)
            .put("natural_origin_proven_by_native", false).put("negative_scope_proven_by_native", false)
            .put("full_primitive_provider_coverage_proven_by_native", false)
        receipt.set<JsonNode>("expectation_mismatches", suiteMapper.createArrayNode().apply {
            if (instant != expectedInstant) add("packet_variant")
            if (positive != expectedPositive) add("drop_outcome")
            if (actualHarvest != expectedPositive) add("harvest_result")
        })
        receipt.set<JsonNode>("block_after", afterNative)
        receipt.set<JsonNode>("source_terminal", terminal)
        receipt.set<JsonNode>("tool_wear_v2", suiteMapper.createObjectNode()
            .put("schema", "bc.playtest_physical_tool_wear.v2")
            .put("vanilla_hotbar_slot", 0).put("expected_interaction_hand", "MAIN_HAND")
            .put("actual_equipment_break_callback_count", actualCallbacks)
            .put("actual_hurt_and_break_cause_enter_count", if (actualCallbacks > 0) 1 else 0)
            .put("actual_hurt_and_break_cause_return_count", if (actualCallbacks > 0) 1 else 0)
            .put("selected_slot_refilled_by_bound_source", refilled).apply {
                if (refilled) put("selected_slot_refill_source_entity_uuid", entity)
                else putNull("selected_slot_refill_source_entity_uuid")
            }
            .put("capture_complete", true).put("saved_tool_nbt_proven_by_native", false)
            .put("natural_tool_origin_proven_by_native", false).apply {
                set<JsonNode>("arm_tool", beforeTool); set<JsonNode>("before_destroy_tool", beforeTool)
                if (callbackTool == null) putNull("callback_tool_before_shrink")
                else set<JsonNode>("callback_tool_before_shrink", callbackTool)
                set<JsonNode>("after_destroy_tool", afterTool); set<JsonNode>("await_tool", awaitTool)
            })
        val rowData = rows(positive, instant, actualHarvest,
            if (positive && expectedPositive) 23 else 166, context, terminal,
            beforeTool, afterTool, awaitTool, callbackTool, actualCallbacks,
            refilled, sourceStack, pre41, post41).map { it.deepCopy<JsonNode>() }
        (rowData.last() as ObjectNode).set<JsonNode>("observed", suiteMapper.createObjectNode()
            .put("physical_observation_complete", true).put("tool_capture_complete", true)
            .put("reviewed_expectation_matches", receipt.path("reviewed_expectation_matches").asBoolean()))
        rowData.single { it.path("kind").asText() == "actual_forge_break_event" }
            .let { (it as ObjectNode).set<JsonNode>("observed", armBlock) }
        rowData.single { it.path("kind").asText() == "actual_gamemode_destroy_return" }
            .let { (it as ObjectNode).set<JsonNode>("observed", afterNative) }
        val pointer = suiteMapper.createObjectNode().put("schema", "bc.playtest_passive_packet_dig_trace.v5")
            .put("path", "packet-dig/000000000002.jsonl").put("bytes", 1000).put("sha256", trace)
            .put("records", rowData.size).put("arm_request_sequence", 2)
            .put("actual_native_session_id", "server-sess").put("natural_acquisition_proven_by_native", false)
        receipt.set<JsonNode>("packet_dig_trace_file", pointer)
        val payloads = listOf(player, blockBefore, crosshair, arm, client(0), client(1), client(2), receipt,
            blockAfter, afterPlayer)
        val plan = PhysicalMiningControlDriver.plan(params)
        val observations = plan.mapIndexed { i, step -> DriverObservation(step.name, step.action.id, step.action.source,
            "completed", payloads[i], if (i == 7) mapOf("packet_dig" to
                VerifiedRawEvidence(BoundFile("/synthetic-not-a-real-file", trace), rowData)) else emptyMap()) }
        return params to observations
    }
    @Test fun `complete bounded progress pickup and instant no-drop diagnostics accept but cannot certify natural mining`() {
        val pickup = evidence(true, false)
        assertEquals("passed", PhysicalMiningControlDriver.evaluate(pickup.first, pickup.second).state)
        val instant = evidence(false, true)
        assertEquals("passed", PhysicalMiningControlDriver.evaluate(instant.first, instant.second).state)
        val instantPickup = evidence(true, true)
        assertEquals("passed", PhysicalMiningControlDriver.evaluate(instantPickup.first, instantPickup.second).state)
        val progressNegative = evidence(false, false)
        assertEquals("passed", PhysicalMiningControlDriver.evaluate(progressNegative.first, progressNegative.second).state)
    }
    @Test fun `a START handler later than its following progress return is rejected`() {
        val (target, observations) = evidence(true, false)
        val rows = observations[7].rawEvidence.getValue("packet_dig").records
        val handler = rows.first { it.path("kind").asText() == "actual_gamemode_handle" } as ObjectNode
        assertEquals(11, handler.path("tick").asInt())
        assertEquals("passed", PhysicalMiningControlDriver.evaluate(target, observations).state)
        handler.put("tick", 20)
        assertThrows(IllegalArgumentException::class.java) {
            PhysicalMiningControlDriver.evaluate(target, observations)
        }
    }
    @Test fun `real callback-shaped break is still only bounded diagnostic until sealed typed tool join`() {
        val (target, observations) = evidence(broken = true)
        assertEquals("passed", PhysicalMiningControlDriver.evaluate(target, observations).state)
        val refilled = evidence(broken = true, refilled = true)
        assertEquals("passed", PhysicalMiningControlDriver.evaluate(refilled.first, refilled.second).state)
        val extra = evidence(broken = true, actualCallbacks = 2)
        assertEquals("failed", PhysicalMiningControlDriver.evaluate(extra.first, extra.second).state)
        val emptyWithoutCallback = evidence(broken = true, actualCallbacks = 0, expectedCallbacks = 0)
        assertEquals("failed", PhysicalMiningControlDriver.evaluate(
            emptyWithoutCallback.first, emptyWithoutCallback.second).state)
        val offhand = evidence(broken = true)
        (offhand.second[7].rawEvidence.getValue("packet_dig").records.single {
            it.path("kind").asText() == "actual_living_entity_equipment_break_callback"
        }.path("observed") as ObjectNode).put("equipment_slot", "OFFHAND")
        assertThrows(IllegalArgumentException::class.java) {
            PhysicalMiningControlDriver.evaluate(offhand.first, offhand.second)
        }
    }
    @Test fun `malformed string fractional counts ticks and flags never coerce to a physical capture`() {
        val alterations: List<(List<DriverObservation>) -> Unit> = listOf(
            { rows -> (rows[7].payload as ObjectNode).put("inbound_before_count", "2") },
            { rows -> (rows[7].payload as ObjectNode).put("actual_stored_harvest_observed_count", 1.0) },
            { rows -> (rows[7].payload as ObjectNode).put("stop_tick", "22") },
            { rows -> (rows[7].payload.path("tool_wear_v2") as ObjectNode)
                .put("actual_equipment_break_callback_count", 0.0) },
            { rows -> (rows[7].payload.path("tool_wear_v2") as ObjectNode)
                .put("selected_slot_refilled_by_bound_source", "false") },
            { rows -> (rows[7].payload as ObjectNode).put("observation_complete", "true") },
            { rows -> (rows[7].payload.path("packet_dig_trace_file") as ObjectNode).put("records", 23.0) },
            { rows -> (rows[7].rawEvidence.getValue("packet_dig").records[0] as ObjectNode)
                .put("event_index", 1.0) },
            { rows -> (rows[7].rawEvidence.getValue("packet_dig").records[0] as ObjectNode)
                .put("tick", "11") },
            { rows -> (rows[7].rawEvidence.getValue("packet_dig").records[0]
                .path("observed") as ObjectNode).put("same_decoded_packet_object", "true") },
        )
        alterations.forEach { alter ->
            val (target, rows) = evidence(true, false)
            alter(rows)
            assertThrows(IllegalArgumentException::class.java) {
                PhysicalMiningControlDriver.evaluate(target, rows)
            }
        }
    }
    @Test fun `complete actual contradictory packet harvest and full-window drop retain bounded failed verdict`() {
        listOf(
            evidence(positive = true, actualHarvest = false),
            evidence(positive = true, instant = true, expectedInstant = false),
            evidence(positive = true, expectedPositive = false, actualHarvest = false),
            evidence(positive = false, expectedPositive = true, actualHarvest = true)
        ).forEach { (target, observations) ->
            assertEquals("failed", PhysicalMiningControlDriver.evaluate(target, observations).state)
        }
    }
    @Test fun `complete wrong drop is failed while missing callback or raw bytes are capture blockers`() {
        val (target, actual) = evidence(true, false)
        val rows = actual[7].rawEvidence.getValue("packet_dig").records
        (rows.single { it.path("kind").asText() == "source_entity_join_before_insertion" }
            .path("observed").path("source_stack") as ObjectNode).put("item_id", "minecraft:stone")
        (rows.single { it.path("kind").asText() == "actual_pickup_pre" }
            .path("observed").path("source_before") as ObjectNode).put("item_id", "minecraft:stone")
        (rows.single { it.path("kind").asText() == "actual_pickup_after_inventory_add" }
            .path("observed").path("acquired_stack") as ObjectNode).put("item_id", "minecraft:stone")
        assertEquals("failed", PhysicalMiningControlDriver.evaluate(target, actual).state)
        val missing = evidence(true, false)
        (missing.second[7].payload as ObjectNode).put("observation_complete", false)
        assertThrows(IllegalArgumentException::class.java) {
            PhysicalMiningControlDriver.evaluate(missing.first, missing.second)
        }
        val unverified = evidence(true, false)
        val noRaw = unverified.second.mapIndexed { i, step -> if (i == 7) step.copy(rawEvidence = emptyMap()) else step }
        assertThrows(IllegalArgumentException::class.java) {
            PhysicalMiningControlDriver.evaluate(unverified.first, noRaw)
        }
    }
}
