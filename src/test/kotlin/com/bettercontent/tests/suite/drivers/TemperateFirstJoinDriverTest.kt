package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TemperateFirstJoinDriverTest {
    private fun target() = suiteMapper.readTree("""{
      "player_uuid":"123e4567-e89b-42d3-a456-426614174000","dimension":"minecraft:overworld",
      "ground_x":3,"ground_y":69,"ground_z":-4,"biome":"minecraft:plains","ground_block":"minecraft:grass_block",
      "temperature_min":0.7,"temperature_max":0.9,"downfall_min":0.3,"downfall_max":0.5,"precipitation":"RAIN"
    }""")
    private fun row(step: String, action: String, payload: String) =
        DriverObservation(step, action, "server", "completed", suiteMapper.readTree(payload))
    private fun evidence(prior: Boolean = false, temp: Double = 0.8) = listOf(
        row("entry", "world.entry", """{
          "uuid":"123e4567-e89b-42d3-a456-426614174000","login_dimension":"minecraft:overworld",
          "login_biome":"minecraft:plains","login_ground_block":"minecraft:grass_block","login_observed":true,
          "playerdata_preexists_at_login":$prior,"first_join_status":"${if (prior) "prior_playerdata_seen" else "no_prior_playerdata_file_observed"}",
          "login_ground_solid":true,"login_feet_air":true,"login_head_air":true,
          "login_x":3.5,"login_y":70,"login_z":-3.5
        }"""),
        row("climate", "world.sample", """{
          "dimension":"minecraft:overworld","x":3,"y":69,"z":-4,"center_biome":"minecraft:plains",
          "base_temperature":$temp,"downfall":0.4,"precipitation":"RAIN","unloaded_positions":0,
          "loaded_samples":[{"x":3,"y":68,"z":-4,"block_id":"minecraft:dirt"},
            {"x":3,"y":69,"z":-4,"block_id":"minecraft:grass_block"},
            {"x":3,"y":70,"z":-4,"block_id":"minecraft:air"}]
        }""")
    )
    @Test fun `fresh first join and independently sampled climate required`() {
        assertEquals("passed", (evaluateCase(TemperateFirstJoinDriver, target(), evidence()) as DriverOutcome.Verdict).value.state)
        assertEquals("failed", (evaluateCase(TemperateFirstJoinDriver, target(), evidence(prior = true)) as DriverOutcome.Verdict).value.state)
        assertEquals("failed", (evaluateCase(TemperateFirstJoinDriver, target(), evidence(temp = 1.7)) as DriverOutcome.Verdict).value.state)
        assertTrue(evaluateCase(TemperateFirstJoinDriver, target(), evidence().dropLast(1)) is DriverOutcome.Fatal)
    }
}
