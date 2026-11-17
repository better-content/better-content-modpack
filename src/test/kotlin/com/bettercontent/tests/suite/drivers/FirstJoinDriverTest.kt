package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FirstJoinDriverTest {
    private fun parameters() = suiteMapper.readTree("""{
      "player_uuid":"123e4567-e89b-42d3-a456-426614174000","dimension":"minecraft:overworld",
      "spawn_x":3,"spawn_y":70,"spawn_z":-4,"maximum_horizontal_distance":8,"maximum_vertical_distance":16,
      "allowed_biomes":["minecraft:plains"],"allowed_ground_blocks":["minecraft:grass_block"]
    }""")
    private fun row(feetAir: Boolean = true, prior: Boolean = false, x: Int = 3) = DriverObservation("first-login", "world.entry", "server", "completed", suiteMapper.readTree("""{
      "uuid":"123e4567-e89b-42d3-a456-426614174000","dimension":"minecraft:overworld","x":$x,"y":70,"z":-4,
      "login_observed":true,"login_tick":80,"current_tick":82,"login_dimension":"minecraft:overworld",
      "login_x":3.5,"login_y":70,"login_z":-3.5,"spawn_x":3,"spawn_y":70,"spawn_z":-4,
      "first_join_status":"${if (prior) "prior_playerdata_seen" else "no_prior_playerdata_file_observed"}",
      "playerdata_preexists_at_login":$prior,"login_biome":"minecraft:plains","login_ground_block":"minecraft:grass_block",
      "login_feet_air":$feetAir,"login_head_air":true,"login_ground_solid":true,"current_chunk_loaded":true,
      "native_login_source":"PlayerEvent.PlayerLoggedInEvent"
    }"""))
    @Test fun `first login requires native event and actual safe terrain not a later teleport`() {
        assertEquals("passed", (evaluateCase(FirstJoinDriver, parameters(), listOf(row())) as DriverOutcome.Verdict).value.state)
        assertEquals("failed", (evaluateCase(FirstJoinDriver, parameters(), listOf(row(feetAir = false))) as DriverOutcome.Verdict).value.state)
        assertEquals("failed", (evaluateCase(FirstJoinDriver, parameters(), listOf(row(prior = true))) as DriverOutcome.Verdict).value.state)
        assertEquals("failed", (evaluateCase(FirstJoinDriver, parameters(), listOf(row(x = 50))) as DriverOutcome.Verdict).value.state)
        assertTrue(evaluateCase(FirstJoinDriver, parameters(), listOf(row().copy(kind = "failed"))) is DriverOutcome.Fatal)
    }
}
