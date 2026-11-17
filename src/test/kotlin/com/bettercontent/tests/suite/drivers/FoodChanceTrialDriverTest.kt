package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.oracles.DistributionHypothesis
import com.bettercontent.tests.suite.oracles.ExactRational
import com.bettercontent.tests.suite.oracles.ReviewedSamplingPlan
import java.security.MessageDigest
import java.time.Instant
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Source-only action-plan regressions; not a runtime or chance-rate test. */
class FoodChanceTrialDriverTest {
    private val actor = "123e4567-e89b-42d3-a456-426614174000"
    private fun targets() = suiteMapper.readTree("""{
      "player_uuid":"$actor","dimension":"minecraft:overworld","input_profile":"1280x720@2",
      "item_id":"test:food","effect_id":"minecraft:speed","acquisition_case_id":"acquire.food",
      "samples":[{"unit_id":"food:0","fixture_id":"chance_0","observer_id":"123e4567-e89b-42d3-a456-426614174001"},
                 {"unit_id":"food:1","fixture_id":"chance_1","observer_id":"123e4567-e89b-42d3-a456-426614174002"}]}""")
    @Test fun `one identical food per epoch physically consumed and restored with no direct use mutation`() {
        val requests = FoodChanceTrialDriver.expandTrials(targets(), "sealed_chance_baseline")
        assertEquals(listOf("food:0", "food:1"), requests.map { it.trialId })
        assertEquals(setOf("test:food"), FoodChanceTrialDriver.fixtureProvisionedTargets(targets()))
        assertEquals(mapOf("test:food" to "acquire.food"), FoodChanceTrialDriver.requiredAcquisitionCases(targets()))
        val actions = FoodChanceTrialDriver.planTrial(requests.first())
        assertEquals(listOf("fixture.begin", "player.snapshot", "player.observe_item_use.arm",
            "client.input_hold", "client.input_release", "client.input_status", "player.observe_item_use.await",
            "player.snapshot", "fixture.restore"), actions.map { it.action.id })
        assertTrue(actions.none { it.action.id == "player.use_item" })
        assertEquals("@controller-witness-context-v1", actions[2].action.parameters.path("witness_context").asText())
        assertEquals("@input-runtime-sha256", actions[3].action.parameters.path("input_runtime_sha256").asText())
    }
    @Test fun `pre-capture plan membership cannot be silently replaced or rebound to another candidate`() {
        val units = listOf("food:0", "food:1")
        val frame = MessageDigest.getInstance("SHA-256").digest(suiteMapper.writeValueAsBytes(units))
            .joinToString("") { "%02x".format(it) }
        val candidate = "a".repeat(64)
        val reviewed = ReviewedSamplingPlan("bc.playtest_distribution_plan.v1", "reviewed", "human-review",
            Instant.now().minusSeconds(100).toString(), "food-batch", "survival.food", candidate,
            "isolated-food-epoch", "reviewed independent identical food trials", frame, units, units,
            listOf("present", "absent"), listOf(DistributionHypothesis("present", ExactRational(1, 4),
                ExactRational(3, 4), 10_000, "ideal-independent-bernoulli-v1")))
        FoodChanceTrialDriver.verifyPreCapturePlan(targets(), reviewed, candidate)
        assertThrows(IllegalArgumentException::class.java) {
            FoodChanceTrialDriver.verifyPreCapturePlan(targets(), reviewed.copy(selectedUnitIds = units.reversed()), candidate)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FoodChanceTrialDriver.verifyPreCapturePlan(targets(), reviewed, "b".repeat(64))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FoodChanceTrialDriver.verifyPreCapturePlan(targets(), reviewed.copy(hypotheses = listOf(
                DistributionHypothesis("present", ExactRational(1, 4), ExactRational(3, 4), 10_000,
                    "uniform-nextfloat-lt-v1"))), candidate)
        }
    }
    @Test fun `no retry replaced unit duplicated observer or heterogeneous item accepted`() {
        val repeated = targets().deepCopy<ObjectNode>()
        (repeated.path("samples") as ArrayNode)[1].let { (it as ObjectNode).put("unit_id", "food:0") }
        assertThrows(IllegalArgumentException::class.java) { FoodChanceTrialDriver.plan(repeated) }
        val changed = targets().deepCopy<ObjectNode>().put("generic_success", true)
        assertThrows(IllegalArgumentException::class.java) { FoodChanceTrialDriver.plan(changed) }
        val wrong = FoodChanceTrialDriver.expandTrials(targets(), "sealed_chance_baseline").first()
            .copy(targetId = "test:other_food")
        assertThrows(IllegalArgumentException::class.java) { FoodChanceTrialDriver.planTrial(wrong) }
    }
}
