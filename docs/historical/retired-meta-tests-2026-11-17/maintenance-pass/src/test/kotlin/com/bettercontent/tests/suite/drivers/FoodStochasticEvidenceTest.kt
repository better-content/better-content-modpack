package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.oracles.DistributionHypothesis
import com.bettercontent.tests.suite.oracles.DistributionSample
import com.bettercontent.tests.suite.oracles.DistributionSampleReport
import com.bettercontent.tests.suite.oracles.ExactRational
import com.bettercontent.tests.suite.oracles.ReviewedSamplingPlan
import com.bettercontent.tests.suite.suiteMapper
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Source-only negative synthetic regressions; no run permitted without separate human authorization. */
class FoodStochasticEvidenceTest {
    @TempDir lateinit var root: Path
    private val candidate = "a".repeat(64)
    private val actor = "123e4567-e89b-42d3-a456-426614174000"
    private fun bound(name: String, data: Any): BoundFile {
        val file = root.resolve(name)
        Files.write(file, suiteMapper.writeValueAsBytes(data))
        return BoundFile(file.toString(), Hashes.sha256(file))
    }
    private fun refs(): Pair<BoundFile, BoundFile> {
        val units = listOf("food:0", "food:1")
        val frame = MessageDigest.getInstance("SHA-256").digest(suiteMapper.writeValueAsBytes(units))
            .joinToString("") { "%02x".format(it) }
        val plan = bound("plan.json", ReviewedSamplingPlan("bc.playtest_distribution_plan.v1", "reviewed",
            "human-source-review", Instant.now().minusSeconds(120).toString(), "food-batch", "survival.food", candidate,
            "isolated-food-epoch", "independent distinct restored epochs and fixed identical food item", frame,
            units, units, listOf("present", "absent"), listOf(DistributionHypothesis("present",
                ExactRational(1, 4), ExactRational(3, 4), 10_000, "ideal-independent-bernoulli-v1"))))
        val report = bound("report.json", DistributionSampleReport("bc.playtest_distribution_samples.v1", plan.sha256,
            Instant.now().minusSeconds(20).toString(), Instant.now().minusSeconds(10).toString(),
            units.map { DistributionSample(it, "absent") }))
        return plan to report
    }
    private fun capture(unit: String, epoch: String, item: String = "test:food") = FoodChanceCapture(unit, epoch,
        "sealed-food", item, actor, "minecraft:overworld", "1280x720@2", "hold_${unit.replace(':', '_')}",
        "123e4567-e89b-42d3-a456-${(if (unit == "food:0") 0 else 1).toString().padStart(12, '0')}", emptyList())
    @Test fun `stochastic batch cannot substitute a duplicate or heterogeneous provider before interpreting outcome`() {
        val (plan, report) = refs()
        assertThrows(IllegalArgumentException::class.java) {
            FoodStochasticEvidence.evaluate(plan, report, candidate, "minecraft:speed",
                listOf(capture("food:0", "epoch-0"), capture("food:1", "epoch-0")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FoodStochasticEvidence.evaluate(plan, report, candidate, "minecraft:speed",
                listOf(capture("food:0", "epoch-0"), capture("food:1", "epoch-1", "test:other_food")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FoodStochasticEvidence.evaluate(plan, report, candidate, "minecraft:speed",
                listOf(capture("food:0", "epoch-0"), capture("food:unplanned", "epoch-1")))
        }
    }
    @Test fun `sealed candidate and physical evidence are required rather than report bins alone`() {
        val (plan, report) = refs()
        assertThrows(IllegalArgumentException::class.java) {
            FoodStochasticEvidence.evaluate(plan, report, "b".repeat(64), "minecraft:speed",
                listOf(capture("food:0", "epoch-0"), capture("food:1", "epoch-1")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FoodStochasticEvidence.evaluate(plan, report, candidate, "minecraft:speed",
                listOf(capture("food:0", "epoch-0"), capture("food:1", "epoch-1")))
        }
    }
}
