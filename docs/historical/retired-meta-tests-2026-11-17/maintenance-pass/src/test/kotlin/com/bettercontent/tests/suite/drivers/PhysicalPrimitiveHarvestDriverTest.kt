package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.checkpoints.SamplingIntent
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Synthetic plan-shape regressions only; no candidate, recipe, sealed tool, world or mining was run. */
class PhysicalPrimitiveHarvestDriverTest {
    private val hash = "a".repeat(64)
    private fun target() = suiteMapper.readTree("""{
      "player_uuid":"123e4567-e89b-42d3-a456-426614174000",
      "dimension":"minecraft:overworld","input_profile":"1280x720@2","max_ticks":120,
      "tool":{"family":"synthetic","item":"example:unreviewed_tool","slot":2,"count":1,
        "source_stack_canonical_sha256":"$hash","acquisition_case_id":"synthetic-acquire"},
      "targets":[
        {"mode":"correct","selector_id":"synthetic-correct","x":100,"y":64,"z":200,
          "face":"north","block_id":"example:unreviewed_block",
          "observer_id":"123e4567-e89b-42d3-a456-426614174001","hold_id":"physical_correct",
          "expected_packet_variant":"progress_start_stop","expected_drop_outcome":"one_full_pickup",
          "expected_drop_item_id":"example:unreviewed_drop","expected_drop_count":1,
          "expected_tool_break_callback_count":0},
        {"mode":"wrong","selector_id":"synthetic-wrong","x":132,"y":64,"z":200,
          "face":"north","block_id":"example:unreviewed_other_block",
          "observer_id":"123e4567-e89b-42d3-a456-426614174002","hold_id":"physical_wrong",
          "expected_packet_variant":"instant_start_only","expected_drop_outcome":"no_drop_negative",
          "expected_drop_item_id":null,"expected_drop_count":0,
          "expected_tool_break_callback_count":0}
      ]}""")
    @Test fun `two isolated predeclared sites use actual OS release and v5 remote packet actions`() {
        val driver = PhysicalPrimitiveHarvestDriver
        val params = target()
        val requests = driver.expandTrials(params, "sealed-prepared-input")
        assertEquals(listOf("correct", "wrong"), requests.map { it.trialId })
        assertEquals(listOf("synthetic.correct", "synthetic.wrong"), requests.map { it.targetId })
        assertTrue(requests.all { it.inputCheckpointId == "sealed-prepared-input" && it.provisionedTargets.isEmpty() })
        assertEquals(mapOf("example:unreviewed_tool" to "synthetic-acquire"), driver.requiredAcquisitionCases(params))
        assertEquals(listOf(SamplingIntent.Site("minecraft:overworld", 6, 12)), driver.samplingIntents(requests[0]))
        assertEquals(listOf(SamplingIntent.Site("minecraft:overworld", 8, 12)), driver.samplingIntents(requests[1]))
        for (request in requests) {
            val actions = driver.planTrial(request).map { it.action.id }
            assertEquals(listOf("player.snapshot", "block.snapshot", "client.crosshair", "player.observe_dig.arm_v5",
                "client.input_hold", "client.input_release", "client.input_status", "player.observe_dig.await_v5",
                "block.snapshot", "player.snapshot"), actions)
            assertTrue(actions.none { it in setOf("player.dig", "player.dig.witness_v1", "player.break_block",
                "entity.spawn", "wait.ticks", "container.click", "player.select_item") })
            assertEquals("packet_dig", driver.rawEvidenceRequests(request).single().name)
        }
        assertEquals(20, driver.plan(params).size)
        assertTrue(driver.requireFreshTerrain)
        assertNull(DriverRegistry.resolve(driver.caseId))
        assertTrue(driver.caseId !in DriverRegistry.supportedCases)
    }
    @Test fun `bounded mechanism cannot become a whole family verdict without A origin and joined seals`() {
        val driver = PhysicalPrimitiveHarvestDriver
        val params = target()
        assertEquals("inconclusive", driver.evaluate(params, emptyList()).state)
        val requests = driver.expandTrials(params, "sealed-prepared-input")
        assertThrows(IllegalArgumentException::class.java) { driver.aggregate(params, requests, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { driver.evaluateTrial(requests.first(), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            driver.aggregate(params, requests, requests.map { request ->
                TrialReceipt(request.trialId, request.targetId, request.inputCheckpointId,
                    "same-epoch", "passed", emptyList(), true, true)
            })
        }
    }
    @Test fun `no fixture count or same-chunk posthoc site substitution enters physical plan`() {
        val driver = PhysicalPrimitiveHarvestDriver
        val count = target().deepCopy<ObjectNode>()
        (count.path("tool") as ObjectNode).put("count", 2)
        assertThrows(IllegalArgumentException::class.java) { driver.plan(count) }
        val duplicate = target().deepCopy<ObjectNode>()
        val rows = duplicate.path("targets")
        (rows[1] as ObjectNode).put("x", 101)
        assertThrows(IllegalArgumentException::class.java) { driver.plan(duplicate) }
        val forged = target().deepCopy<ObjectNode>().put("native_tool_origin_proven", true)
        assertThrows(IllegalArgumentException::class.java) { driver.plan(forged) }
    }
}
