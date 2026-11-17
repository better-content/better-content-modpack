package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PaidWaterTrialControlDriverTest {
    private val player = "123e4567-e89b-42d3-a456-426614174000"
    private fun target() = suiteMapper.readTree("""{
       "fixture_id":"water_sip_1","food_level":10,"saturation":0,"water":{
       "player_uuid":"$player","hand":"main_hand","source_item":"minecraft:potion",
       "result_item":"minecraft:glass_bottle","source_count":1,"result_count":1,
       "source_purity":0,"result_purity":0,"thirst_before":10,"thirst_after":14,
       "quenched_before":10,"quenched_after":14,"fluids_before":[],"fluids_after":[]}}
    """)
    private fun row(i: Int, json: String): DriverObservation {
        val step = PaidWaterTrialControlDriver.plan(target())[i]
        return DriverObservation(step.name, step.action.id, "server", "completed", suiteMapper.readTree(json))
    }
    private fun observations(restored: Boolean = true): List<DriverObservation> = listOf(
        row(0, """{"fixture_id":"water_sip_1","owner_case_id":"control.water.paid-sip-trial","player_uuid":"$player",
          "item_id":"minecraft:potion","count":1,"hand":"main_hand","source_origin":"preexisting_unattributed",
          "provisioned_count":0,"provisioned_targets":[],"fixture_mutations":["food","thirst"],"thirst_after":10,"quenched_after":10}"""),
        row(1, """{"player_uuid":"$player","hand":"main_hand","thirst":10,"quenched":10,
          "held_item":"minecraft:potion","held_count":1,"item_purity":0,
          "purity_source":"dev.ghen.thirst.content.purity.WaterPurity.getPurity",
          "fluids":[],"capability_source":"dev.ghen.thirst.foundation.common.capability.ModCapabilities.PLAYER_THIRST"}"""),
        row(2, """{"uuid":"$player","hand":"main_hand","item_before":"minecraft:potion",
          "item_after":"minecraft:glass_bottle","count_before":1,"count_after":1,
          "consumption_completed":true,"use_item_event_observed":true}"""),
        row(3, """{"player_uuid":"$player","hand":"main_hand","thirst":14,"quenched":14,
          "held_item":"minecraft:glass_bottle","held_count":1,"item_purity":0,
          "purity_source":"dev.ghen.thirst.content.purity.WaterPurity.getPurity",
          "fluids":[],"capability_source":"dev.ghen.thirst.foundation.common.capability.ModCapabilities.PLAYER_THIRST"}"""),
        row(4, """{"fixture_id":"water_sip_1","owner_case_id":"control.water.paid-sip-trial","player_uuid":"$player",
          "thirst_restored":$restored,"cleanup_complete":$restored,"owned_residuals":${if (restored) 0 else 1},
          "baseline_restored":false}""")
    )
    @Test fun `paid trial preserves preexisting water source and restores authored Thirst`() {
        val steps = PaidWaterTrialControlDriver.plan(target())
        assertEquals(listOf("fixture.begin_water", "player.water_snapshot", "player.use_item", "player.water_snapshot", "fixture.restore"),
            steps.map { it.action.id })
        assertTrue(steps.first().action.parameters.path("preserve_held").asBoolean())
        assertEquals("passed", (evaluateCase(PaidWaterTrialControlDriver, target(), observations()) as DriverOutcome.Verdict).value.state)
        assertEquals("failed", (evaluateCase(PaidWaterTrialControlDriver, target(), observations(false)) as DriverOutcome.Verdict).value.state)
    }
    @Test fun `water control refuses fixture-authored inventory as source of acquisition`() {
        val changed = observations().toMutableList()
        changed[0] = row(0, """{"fixture_id":"water_sip_1","owner_case_id":"control.water.paid-sip-trial","player_uuid":"$player",
          "item_id":"minecraft:potion","count":1,"hand":"main_hand","source_origin":"authored_fixture_not_natural",
          "provisioned_count":1,"provisioned_targets":["minecraft:potion"],"fixture_mutations":["inventory","food","thirst"],"thirst_after":10,"quenched_after":10}""")
        assertEquals("failed", (evaluateCase(PaidWaterTrialControlDriver, target(), changed) as DriverOutcome.Verdict).value.state)
    }
}
