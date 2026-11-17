package com.bettercontent.tests.suite.oracles

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.suiteMapper
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Synthetic exact-math/source-contract tests only. No random generator, world or observed outcome is run. */
class ExactDistributionOracleTest {
    @TempDir lateinit var root: Path
    private fun frame(ids: List<String>) = MessageDigest.getInstance("SHA-256")
        .digest(suiteMapper.writeValueAsBytes(ids)).joinToString("") { "%02x".format(it) }
    private fun fixture(hypotheses: List<DistributionHypothesis>, categories: List<String>): Pair<BoundFile, BoundFile> {
        val ids = (0 until 16).map { "site:$it" }
        val plan = ReviewedSamplingPlan("bc.playtest_distribution_plan.v1", "reviewed", "external-human-review",
            Instant.now().minusSeconds(100).toString(), "fixed-batch", "worldgen-family", "a".repeat(64), "generated-site",
            "reviewed independent sites without replacement and fixed strata before generation", frame(ids),
            ids, ids, listOf("A", "B", "none").map { it.lowercase() }, hypotheses)
        val path = root.resolve("reviewed-plan-${System.nanoTime()}.json")
        Files.writeString(path, suiteMapper.writeValueAsString(plan))
        val planRef = BoundFile(path.toString(), Hashes.sha256(path))
        val report = DistributionSampleReport("bc.playtest_distribution_samples.v1", planRef.sha256,
            Instant.now().minusSeconds(10).toString(), Instant.now().toString(),
            ids.zip(categories).map { (id, result) -> DistributionSample(id, result.takeUnless { it == "ambiguous" }) })
        val result = root.resolve("sample-${System.nanoTime()}.json")
        Files.writeString(result, suiteMapper.writeValueAsString(report))
        return planRef to BoundFile(result.toString(), Hashes.sha256(result))
    }
    private fun one(bin: String = "a", alpha: Int = 5_000) = DistributionHypothesis(bin,
        ExactRational(1, 4), ExactRational(3, 4), alpha, "ideal-independent-bernoulli-v1")
    @Test fun `exact upper and lower binomial tails have integer fractions including degenerate endpoints`() {
        assertEquals(BigInteger.valueOf(5) to BigInteger.valueOf(16),
            ExactDistributionOracle.exactTail(4, 3, ExactRational(1, 2), true))
        assertEquals(BigInteger.valueOf(5) to BigInteger.valueOf(16),
            ExactDistributionOracle.exactTail(4, 1, ExactRational(1, 2), false))
        assertEquals(BigInteger.ZERO to BigInteger.ONE,
            ExactDistributionOracle.exactTail(4, 1, ExactRational(0, 1), true))
        assertEquals(BigInteger.ONE to BigInteger.ONE,
            ExactDistributionOracle.exactTail(4, 0, ExactRational(1, 1), true))
    }
    @Test fun `nextFloat exact grid is not the idealized decimal threshold`() {
        val threshold = ExactDistributionOracle.nextFloatLessThan(0.8f)
        assertEquals(ExactRational(13_421_773, 16_777_216), threshold)
        assertTrue(ExactRational(4, 5).lessThan(threshold))
        assertEquals(BigInteger.valueOf(13_421_773) to BigInteger.valueOf(16_777_216),
            ExactDistributionOracle.exactTail(1, 1, threshold, true))
        assertThrows(IllegalArgumentException::class.java) { ExactDistributionOracle.nextFloatLessThan(Float.NaN) }
    }
    @Test fun `predeclared all-success batch rejects exactly without adaptive retry`() {
        val (plan, report) = fixture(listOf(one()), List(16) { "a" })
        val result = ExactDistributionOracle.evaluate(plan, report)
        assertEquals("at-least-one-predeclared-null-rejected", result.status)
        assertEquals(16, result.findings.single().sampleSize)
        assertEquals(16, result.findings.single().observedCount)
        assertTrue(result.findings.single().rejectedNullInPredeterminedDirection)
    }
    @Test fun `zero observed category and compatible null cannot prove unseen category exists`() {
        val (plan, report) = fixture(listOf(one()), List(16) { "none" })
        val result = ExactDistributionOracle.evaluate(plan, report)
        assertEquals("null-compatible-not-presence-proof", result.status)
        assertEquals(0, result.findings.single().observedCount)
        assertFalse(result.findings.single().rejectedNullInPredeterminedDirection)
    }
    @Test fun `missing ambiguous extra and repeated samples are inconclusive before any tail`() {
        val (plan, complete) = fixture(listOf(one()), List(16) { "a" })
        fun changed(transform: (DistributionSampleReport) -> DistributionSampleReport): BoundFile {
            val record = suiteMapper.readValue(Path.of(complete.path).toFile(), DistributionSampleReport::class.java)
            val file = root.resolve("mutated-${System.nanoTime()}.json")
            Files.writeString(file, suiteMapper.writeValueAsString(transform(record)))
            return BoundFile(file.toString(), Hashes.sha256(file))
        }
        val normal = suiteMapper.readValue(Path.of(complete.path).toFile(), DistributionSampleReport::class.java)
        for (samples in listOf(normal.samples.dropLast(1), normal.samples.mapIndexed { i, s ->
            if (i == 0) s.copy(binId = null) else s }, normal.samples + normal.samples.first(),
            normal.samples.mapIndexed { i, s -> if (i == 0) s.copy(unitId = "site:unplanned") else s })) {
            assertEquals("inconclusive", ExactDistributionOracle.evaluate(plan, changed { it.copy(samples = samples) }).status)
        }
        assertEquals("inconclusive", ExactDistributionOracle.evaluate(plan, changed {
            it.copy(firstCapturedAt = Instant.now().minusSeconds(200).toString()) }).status)
        val mutated = Path.of(complete.path)
        Files.writeString(mutated, Files.readString(mutated) + "{}")
        assertEquals("inconclusive", ExactDistributionOracle.evaluate(plan, BoundFile(mutated.toString(), Hashes.sha256(mutated))).status)
        Files.writeString(mutated, "x".repeat(1_048_577))
        assertEquals("inconclusive", ExactDistributionOracle.evaluate(plan, BoundFile(mutated.toString(), Hashes.sha256(mutated))).status)
        Files.delete(mutated)
        assertEquals("inconclusive", ExactDistributionOracle.evaluate(plan, complete).status)
    }
    @Test fun `family allocations above one percent and altered reviewed membership fail closed`() {
        val (bad, report) = fixture(listOf(one("a", 6000), one("b", 5000)), List(16) { "a" })
        assertThrows(IllegalArgumentException::class.java) { ExactDistributionOracle.evaluate(bad, report) }
        val (good, obs) = fixture(listOf(one()), List(16) { "a" })
        Files.writeString(Path.of(good.path), "{}")
        assertThrows(IllegalArgumentException::class.java) { ExactDistributionOracle.evaluate(good, obs) }
    }
}
