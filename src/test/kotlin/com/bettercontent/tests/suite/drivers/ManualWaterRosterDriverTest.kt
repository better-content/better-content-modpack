package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManualWaterRosterDriverTest {
    private val actor = "123e4567-e89b-42d3-a456-426614174000"
    private val potionHash = "a".repeat(64)
    private val bucketHash = "b".repeat(64)
    private val hotbarHash = "c".repeat(64)
    private fun target() = suiteMapper.readTree("""{
      "player_uuid":"$actor","food_level":10,"saturation":0,"sources":[
        {"trial_id":"bottle","slot":3,"source_item":"minecraft:potion","source_count":1,
         "source_stack_sha256":"$potionHash","acquisition_case_id":"acquire.water-bottle",
         "water":{"player_uuid":"$actor","hand":"main_hand","source_item":"minecraft:potion","result_item":"minecraft:glass_bottle",
          "source_count":1,"result_count":1,"source_purity":0,"result_purity":0,"thirst_before":10,"thirst_after":14,
          "quenched_before":10,"quenched_after":14,"fluids_before":[],"fluids_after":[]}},
        {"trial_id":"bucket","slot":10,"source_item":"minecraft:water_bucket","source_count":1,
         "source_stack_sha256":"$bucketHash","acquisition_case_id":"acquire.water-bucket",
         "water":{"player_uuid":"$actor","hand":"main_hand","source_item":"minecraft:water_bucket","result_item":"minecraft:bucket",
          "source_count":1,"result_count":1,"source_purity":3,"result_purity":0,"thirst_before":4,"thirst_after":20,
          "quenched_before":4,"quenched_after":20,"fluids_before":[],"fluids_after":[]}}
      ]}""")
    private fun row(name: String, action: String, payload: String) =
        DriverObservation(name, action, "server", "completed", suiteMapper.readTree(payload))
    private fun snap(held: String, hash: String, selected: Int, inventory: String) = """{
        "uuid":"$actor","selected_slot":$selected,"held_item":"$held","held_stack_sha256":"$hash","inventory":$inventory}"""
    private fun inventory(sourceSlot: Int, sourceItem: String, sourceHash: String, selectedSlot: Int, selectedItem: String, selectedHash: String) =
        """[{"slot":$sourceSlot,"item":"$sourceItem","count":1,"stack_sha256":"$sourceHash"},
            {"slot":$selectedSlot,"item":"$selectedItem","count":1,"stack_sha256":"$selectedHash"}]"""
    private fun observations(request: TrialRequest): List<DriverObservation> {
        val bottle = request.trialId == "bottle"; val slot = if (bottle) 3 else 10
        val source = if (bottle) "minecraft:potion" else "minecraft:water_bucket"
        val sourceHash = if (bottle) potionHash else bucketHash
        val result = if (bottle) "minecraft:glass_bottle" else "minecraft:bucket"
        val thirstBefore = if (bottle) 10 else 4; val thirstAfter = if (bottle) 14 else 20
        val purity = if (bottle) 0 else 3; val route = if (bottle)
            "ServerGamePacketListenerImpl.handleSetCarriedItem" else "ServerGamePacketListenerImpl.handleContainerClick_SWAP"
        val beforeInventory = inventory(slot, source, sourceHash, 0, "minecraft:stick", hotbarHash)
        val afterInventory = if (bottle) beforeInventory else inventory(slot, "minecraft:stick", hotbarHash, 0, source, sourceHash)
        val selectedSlot = if (bottle) slot else 0
        val selectedItemAfterSlot = if (bottle) source else "minecraft:stick"
        val rows = listOf(
            row("before-selection", "player.snapshot", snap("minecraft:stick", hotbarHash, 0, beforeInventory)),
            row("select", "player.select_item", """{"player_uuid":"$actor","source_slot":$slot,"selected_before":0,
               "selected_after":$selectedSlot,"item":"$source","count":1,"stack_sha256":"$sourceHash",
               "source_slot_item_after":"$selectedItemAfterSlot","inventory_conserved":true,
               "inventory_multiset_before_sha256":"${"d".repeat(64)}","inventory_multiset_after_sha256":"${"d".repeat(64)}",
               "native_packet_handler":"$route","client_acknowledgment":"unverified_without_client_observer",
               "acquisition_origin":"preexisting_unattributed","fixture_mutations":[]}"""),
            row("after-selection", "player.snapshot", snap(source, sourceHash, selectedSlot, afterInventory)),
            row("fixture-begin", "fixture.begin_water", """{"fixture_id":"water_${request.trialId}",
              "owner_case_id":"water.manual","player_uuid":"$actor","item_id":"$source","count":1,
              "hand":"main_hand","source_origin":"preexisting_unattributed","provisioned_count":0,
              "provisioned_targets":[],"fixture_mutations":["food","thirst"],"thirst_after":$thirstBefore,"quenched_after":$thirstBefore}"""),
            row("before", "player.water_snapshot", """{"player_uuid":"$actor","hand":"main_hand","thirst":$thirstBefore,
              "quenched":$thirstBefore,"held_item":"$source","held_count":1,"item_purity":$purity,"fluids":[],
              "capability_source":"dev.ghen.thirst.foundation.common.capability.ModCapabilities.PLAYER_THIRST",
              "purity_source":"dev.ghen.thirst.content.purity.WaterPurity.getPurity"}"""),
            row("sip", "player.use_item", """{"uuid":"$actor","hand":"main_hand","item_before":"$source","item_after":"$result",
              "count_before":1,"count_after":1,"consumption_completed":true}"""),
            row("after", "player.water_snapshot", """{"player_uuid":"$actor","hand":"main_hand","thirst":$thirstAfter,
              "quenched":$thirstAfter,"held_item":"$result","held_count":1,"item_purity":0,"fluids":[],
              "capability_source":"dev.ghen.thirst.foundation.common.capability.ModCapabilities.PLAYER_THIRST",
              "purity_source":"dev.ghen.thirst.content.purity.WaterPurity.getPurity"}"""),
            row("fixture-restore", "fixture.restore", """{"fixture_id":"water_${request.trialId}",
              "owner_case_id":"water.manual","player_uuid":"$actor","thirst_restored":true,
              "cleanup_complete":true,"owned_residuals":0,"baseline_restored":false}"""),
            row("after-restoration", "player.snapshot", snap(source, sourceHash, selectedSlot, afterInventory)))
        return rows
    }
    @Test fun `manual source roster has one restored trial per acquired item and normal packet selection`() {
        val requests = ManualWaterRosterDriver.expandTrials(target(), "sealed_water_sources")
        assertEquals(2, requests.size)
        assertEquals(setOf("minecraft:potion", "minecraft:water_bucket"), ManualWaterRosterDriver.requiredAcquisitionCases(target()).keys)
        assertEquals(listOf("player.snapshot", "player.select_item", "player.snapshot", "fixture.begin_water",
            "player.water_snapshot", "player.use_item", "player.water_snapshot", "fixture.restore", "player.snapshot"),
            ManualWaterRosterDriver.planTrial(requests.first()).map { it.action.id })
        val receipts = requests.mapIndexed { index, req ->
            val raw = observations(req)
            assertEquals("passed", ManualWaterRosterDriver.evaluateTrial(req, raw).state)
            TrialReceipt(req.trialId, req.targetId, req.inputCheckpointId, "water-epoch-$index", "passed", raw, true, true)
        }
        assertEquals("passed", ManualWaterRosterDriver.aggregate(target(), requests, receipts).state)
        assertThrows(IllegalArgumentException::class.java) { ManualWaterRosterDriver.aggregate(target(), requests, receipts.map { it.copy(epochId = "same-epoch") }) }
        assertThrows(IllegalArgumentException::class.java) { ManualWaterRosterDriver.aggregate(target(), requests, receipts.map { it.copy(inputCheckpointVerified = false) }) }
    }
    @Test fun `generic server selected item success cannot replace full stack source or acquisition`() {
        val request = ManualWaterRosterDriver.expandTrials(target(), "sealed_water_sources").first()
        val spoofed = observations(request).toMutableList()
        val selection = spoofed[1]; val payload = selection.payload.deepCopy<ObjectNode>().put("stack_sha256", "e".repeat(64))
        spoofed[1] = selection.copy(payload = payload)
        assertEquals("failed", ManualWaterRosterDriver.evaluateTrial(request, spoofed).state)
        assertTrue(ManualWaterRosterDriver.fixtureProvisionedTargets(target()).isEmpty())
        assertTrue(ManualWaterRosterDriver.requiredAcquisitionCases(target()).values.all { it.startsWith("acquire.") })
    }
}
