package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Synthetic source regressions only, NEVER actual world or GUI qualification. Not run. */
class OnboardingTerrainOracleTest {
    private fun bounds() = suiteMapper.readTree("""{"dimension":"minecraft:overworld","x_min":1,"x_max":3,
        "y_min":3,"y_max":6,"z_min":1,"z_max":3}""") as ObjectNode
    private fun column(): ObjectNode = suiteMapper.readTree("""{
      "dimension":"minecraft:overworld","x":2,"z":2,"min_build_height":0,"max_build_height":8,"scan_top_y":5,
      "loaded_only_no_chunk_forcing":true,"column_complete":true,"material_classification":"none_native_raw_only",
      "columns":[{"y":0,"block_id":"minecraft:air","properties":{}},
        {"y":1,"block_id":"minecraft:air","properties":{}},
        {"y":2,"block_id":"minecraft:air","properties":{}},
        {"y":3,"block_id":"minecraft:stone","properties":{}},
        {"y":4,"block_id":"minecraft:air","properties":{}},
        {"y":5,"block_id":"minecraft:air","properties":{}},
        {"y":6,"block_id":"minecraft:air","properties":{}},
        {"y":7,"block_id":"minecraft:air","properties":{}}]}
    """.trimIndent()) as ObjectNode
    private fun arm(): ObjectNode = suiteMapper.readTree("""{"observer_id":"123e4567-e89b-12d3-a456-426614174000",
      "actor_uuid":"123e4567-e89b-12d3-a456-426614174000",
      "actor_at_arm":{"dimension":"minecraft:overworld","block_x":2,"block_y":5,"block_z":2},
      "request_bounds":${bounds()}}
    """.trimIndent()) as ObjectNode
    private fun clientKey(): ObjectNode = suiteMapper.readTree("""{"dimension":"minecraft:overworld","block_x":2,
      "block_y":5,"block_z":2,"spectator":true,"modifier_down":true,"native_tick":8}
    """.trimIndent()) as ObjectNode
    private fun receipt(): ObjectNode = suiteMapper.readTree("""{
      "schema":"bc.playtest_passive_onboarding.v1","request_bounds":${bounds()},
      "observer_id":"123e4567-e89b-12d3-a456-426614174000",
      "actor_uuid":"123e4567-e89b-12d3-a456-426614174000",
      "actor_at_arm":{"dimension":"minecraft:overworld","block_x":2,"block_y":5,"block_z":2},
      "requested_site":{"dimension":"minecraft:overworld","x":2,"y":5,"z":2},
      "actor_at_request":{"dimension":"minecraft:overworld","block_x":2,"block_y":5,"block_z":2},
      "terrain_pre_commit":${column()},
      "actual_committed_site":{"dimension":"minecraft:overworld","x":2,"y":4,"z":2},
      "persisted_site_at_event":{"dimension":"minecraft:overworld","x":2,"y":4,"z":2},
      "persisted_site":{"dimension":"minecraft:overworld","x":2,"y":4,"z":2},
      "vanilla_respawn_site":{"dimension":"minecraft:overworld","x":2,"y":4,"z":2},
      "actual_final_actor":{"dimension":"minecraft:overworld","block_x":2,"block_y":4,"block_z":2,
        "x":2.5,"y":4.0,"z":2.5,"mode":"survival"},
      "site_after":{"base":{"y":3,"block_id":"better_spawns:respawn_marker","properties":{}},
        "feet":{"y":4,"block_id":"minecraft:air","properties":{}},
        "head":{"y":5,"block_id":"minecraft:air","properties":{}},
        "loaded_only_no_chunk_forcing":true,"marker_base_observed":true,"feet_head_air_observed":true},
      "actual_kit":"spawn_only","final_onboarding_complete":true,"narrow_event_chain_complete":true,
      "arm_tick":1,"packet_handling_tick":4,"finalization_event_tick":5,
      "packet_handler_return_tick":6,"final_applied_state_tick":7,
      "packet_count":1,"finalization_event_count":1,"packet_handler_return_count":1
    }""".trimIndent()) as ObjectNode
    @Test fun adjustedRequestedYResolvesToFirstSturdyVanillaBaseRatherThanEqualsRequest() {
        assertEquals(4, OnboardingTerrainOracle.firstSupportingFeet(receipt().path("requested_site"), column()))
        assertTrue(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), receipt(), clientKey()))
    }
    @Test fun unsupportedShapeAboveFirstKnownBaseCannotBeTreatedAsAir() {
        val unknown = column()
        (unknown.path("columns")[4] as ObjectNode).put("block_id", "modded:slab")
        assertNull(OnboardingTerrainOracle.firstSupportingFeet(receipt().path("requested_site"), unknown))
        val r = receipt(); (r.path("terrain_pre_commit").path("columns")[4] as ObjectNode).put("block_id", "modded:slab")
        assertFalse(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), r, clientKey()))
    }
    @Test fun noFutureCommitCanBeInferredFromEarlySurvivalOrMissingEvent() {
        val early = receipt(); early.put("packet_handler_return_tick", 9)
        assertFalse(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), early, clientKey()))
        val missing = receipt(); missing.put("narrow_event_chain_complete", false)
        assertFalse(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), missing, clientKey()))
        val duplicated = receipt(); duplicated.put("packet_count", 2)
        assertFalse(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), duplicated, clientKey()))
    }
    @Test fun packetRequestMustRemainInsideReviewedBoundsAndCurrentActualActorDistance() {
        val moved = receipt(); (moved.path("actor_at_request") as ObjectNode).put("block_y", 6)
        assertTrue(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), moved, clientKey())) // Shift movement is real.
        (moved.path("actor_at_request") as ObjectNode).put("block_y", 8)
        assertFalse(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), moved, clientKey()))
        val outOfArea = receipt(); (outOfArea.path("requested_site") as ObjectNode).put("x", 4)
        assertFalse(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), outOfArea, clientKey()))
        val wrongDimension = receipt(); (wrongDimension.path("requested_site") as ObjectNode).put("dimension", "minecraft:nether")
        assertFalse(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), wrongDimension, clientKey()))
    }
    @Test fun missingColumnRowsWrongResolutionOrMissingMarkerCannotPass() {
        val missing = receipt(); (missing.path("terrain_pre_commit").path("columns") as com.fasterxml.jackson.databind.node.ArrayNode).remove(0)
        assertFalse(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), missing, clientKey()))
        val wrongY = receipt(); (wrongY.path("actual_committed_site") as ObjectNode).put("y", 5)
        assertFalse(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), wrongY, clientKey()))
        val noMarker = receipt(); (noMarker.path("site_after").path("base") as ObjectNode).put("block_id", "minecraft:stone")
        assertFalse(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), noMarker, clientKey()))
        val forgedClient = clientKey(); forgedClient.put("modifier_down", false)
        assertFalse(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), receipt(), forgedClient))
    }
    @Test fun preInputActorRequestAndResolvedSiteCannotDriftIndependently() {
        val changedArm = arm(); (changedArm.path("actor_at_arm") as ObjectNode).put("block_x", 3)
        assertFalse(OnboardingTerrainOracle.matchesReviewedSite(bounds(), changedArm, receipt(), clientKey()))
        val displacedPacket = receipt(); (displacedPacket.path("requested_site") as ObjectNode).put("x", 3)
        assertFalse(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), displacedPacket, clientKey()))
        val mismatchedActor = receipt(); (mismatchedActor.path("actor_at_request") as ObjectNode).put("block_z", 1)
        // One-block shift is permitted only when actual bounded client callback context and request remain consistent.
        assertTrue(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), mismatchedActor, clientKey()))
        (mismatchedActor.path("actor_at_request") as ObjectNode).put("block_z", 3)
        assertTrue(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), mismatchedActor, clientKey()))
        (mismatchedActor.path("actor_at_request") as ObjectNode).put("block_z", 4)
        assertFalse(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), mismatchedActor, clientKey()))
        val changedCommitted = receipt(); (changedCommitted.path("actual_committed_site") as ObjectNode).put("z", 3)
        assertFalse(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), changedCommitted, clientKey()))
        val changedFinalActor = receipt(); (changedFinalActor.path("actual_final_actor") as ObjectNode).put("block_y", 5)
        assertFalse(OnboardingTerrainOracle.matchesReviewedSite(bounds(), arm(), changedFinalActor, clientKey()))
    }
}
