package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OutgoingCombatDriverTest {
    private fun target() = suiteMapper.readTree("""{
      "player_uuid":"123e4567-e89b-42d3-a456-426614174000","target_uuid":"123e4567-e89b-42d3-a456-426614174001",
      "target_type":"minecraft:zombie","dimension":"minecraft:overworld","held_item":"minecraft:iron_sword",
      "target_health_before":20,"target_health_after":14,"held_damage_before":4,"held_damage_after":5
    }""")
    private fun row(step: String, action: String, payload: String) =
        DriverObservation(step, action, "server", "completed", suiteMapper.readTree(payload))
    private fun observations(healthAfter: Int = 14, damageAfter: Int = 5) = listOf(
        row("before-player", "player.snapshot", """{"uuid":"123e4567-e89b-42d3-a456-426614174000","held_item":"minecraft:iron_sword","held_damage":4,"health":20}"""),
        row("before-target", "entity.snapshot", """{"entity_uuid":"123e4567-e89b-42d3-a456-426614174001","target_type":"minecraft:zombie","dimension":"minecraft:overworld","present":true,"alive":true,"health":20}"""),
        row("attack", "player.attack", """{
          "player_uuid":"123e4567-e89b-42d3-a456-426614174000","target_uuid":"123e4567-e89b-42d3-a456-426614174001",
          "target_type":"minecraft:zombie","attack_source":"ServerPlayer.attack","target_health_before":20,
          "target_health_after":$healthAfter,"target_alive_after":true,"player_health_before":20,"player_health_after":20,
          "held_damage_before":4,"held_damage_after":$damageAfter
        }"""),
        row("after-target", "entity.snapshot", """{"entity_uuid":"123e4567-e89b-42d3-a456-426614174001","target_type":"minecraft:zombie","dimension":"minecraft:overworld","present":true,"alive":true,"health":$healthAfter}"""),
        row("after-player", "player.snapshot", """{"uuid":"123e4567-e89b-42d3-a456-426614174000","held_item":"minecraft:iron_sword","held_damage":$damageAfter,"health":20}""")
    )
    @Test fun `real attack requires attributable health delta and equipment debit`() {
        assertEquals("passed", (evaluateCase(OutgoingCombatDriver, target(), observations()) as DriverOutcome.Verdict).value.state)
        assertEquals("failed", (evaluateCase(OutgoingCombatDriver, target(), observations(healthAfter = 20)) as DriverOutcome.Verdict).value.state)
        assertEquals("failed", (evaluateCase(OutgoingCombatDriver, target(), observations(damageAfter = 4)) as DriverOutcome.Verdict).value.state)
        val vanished = observations().toMutableList()
        vanished[3] = row("after-target", "entity.snapshot", """{"entity_uuid":"123e4567-e89b-42d3-a456-426614174001",
            "target_type":"minecraft:zombie","dimension":"minecraft:overworld","present":false,"absence_reason":"unknown_unload_or_removal"}""")
        assertEquals("inconclusive", (evaluateCase(OutgoingCombatDriver, target(), vanished) as DriverOutcome.Verdict).value.state)
        assertTrue(evaluateCase(OutgoingCombatDriver, target(), observations().mapIndexed { i, r -> if (i == 2) r.copy(kind = "failed") else r }) is DriverOutcome.Fatal)
    }
}
