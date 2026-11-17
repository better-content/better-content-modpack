package com.bettercontent.tests.suite.oracles

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

/** Preselected independent Bernoulli sampling only. The raw producer's site origin, independence,
 * classification and clock must be checked separately; this never qualifies world generation. */
internal data class ExactRational(val numerator: Int, val denominator: Int) {
    init { require(denominator in 1..16_777_216 && numerator in 0..denominator) }
    fun lessThan(other: ExactRational): Boolean =
        numerator.toLong() * other.denominator < other.numerator.toLong() * denominator
}
internal data class DistributionHypothesis(val binId: String, val nullRate: ExactRational,
    val predeterminedAlternative: ExactRational, val alphaPartsPerMillion: Int,
    val samplerSemantics: String)
internal data class ReviewedSamplingPlan(val schema: String, val status: String, val reviewer: String,
    val reviewedAt: String, val planId: String, val familyId: String, val candidateSha256: String, val samplingUnit: String,
    val independentSource: String, val samplingFrameSha256: String, val eligibleUnitIds: List<String>,
    val selectedUnitIds: List<String>, val bins: List<String>, val hypotheses: List<DistributionHypothesis>)
internal data class DistributionSample(val unitId: String, val binId: String?)
internal data class DistributionSampleReport(val schema: String, val planSha256: String,
    val firstCapturedAt: String, val completedAt: String, val samples: List<DistributionSample>)
internal data class BinomialFinding(val binId: String, val observedCount: Int, val sampleSize: Int,
    val tailNumerator: BigInteger, val tailDenominator: BigInteger, val rejectedNullInPredeterminedDirection: Boolean)
/** Not a gameplay verdict. Nonrejection NEVER proves an unobserved outcome exists. */
internal data class DistributionFinding(val status: String, val findings: List<BinomialFinding>, val reason: String)

internal object ExactDistributionOracle {
    private val key = Regex("[a-z][a-z0-9._:-]{0,127}")
    private val strictJson = suiteMapper.copy().apply {
        factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
    }
    /** Count the 24-bit uniform nextFloat grid values STRICTLY below a reviewed Float threshold.
     * The binary Float is converted exactly; 0.8f is NOT the ideal mathematical fraction 4/5. */
    internal fun nextFloatLessThan(threshold: Float): ExactRational {
        require(threshold.isFinite() && threshold in 0.0f..1.0f)
        val count = BigDecimal(threshold.toDouble()).multiply(BigDecimal.valueOf(16_777_216))
            .setScale(0, RoundingMode.CEILING).intValueExact()
        return ExactRational(count, 16_777_216)
    }
    private fun bound(ref: BoundFile, maxBytes: Int): ByteArray {
        require(shaPattern.matches(ref.sha256))
        val file = Path.of(ref.path)
        require(file.isAbsolute && file.normalize() == file &&
            generateSequence(file.parent) { it.parent }.all { parent ->
                Files.isDirectory(parent, NOFOLLOW_LINKS) && !Files.isSymbolicLink(parent)
            } && Files.isRegularFile(file, NOFOLLOW_LINKS) &&
            (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1) {
            "reviewed evidence path not a unique regular file under real directories"
        }
        val bytes = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(maxBytes + 1) }
        require(bytes.isNotEmpty() && bytes.size <= maxBytes &&
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == ref.sha256) {
            "reviewed plan or raw sample evidence missing/altered/oversize"
        }
        return bytes
    }
    /** Numerator of P(X>=observed) for enrichment, or P(X<=observed) for deficit.
     * Compute exact integer powers/combinations; no CLT, floating point or adaptive stopping. */
    internal fun exactTail(n: Int, observed: Int, rate: ExactRational, enrichment: Boolean): Pair<BigInteger, BigInteger> {
        require(n in 1..512 && observed in 0..n)
        val a = BigInteger.valueOf(rate.numerator.toLong())
        val b = BigInteger.valueOf((rate.denominator - rate.numerator).toLong())
        val den = BigInteger.valueOf(rate.denominator.toLong()).pow(n)
        var combinations = BigInteger.ONE
        var sum = BigInteger.ZERO
        for (k in 0..n) {
            if ((enrichment && k >= observed) || (!enrichment && k <= observed)) {
                sum += combinations * a.pow(k) * b.pow(n - k)
            }
            if (k < n) combinations = combinations * BigInteger.valueOf((n - k).toLong()) /
                BigInteger.valueOf((k + 1).toLong())
        }
        return sum to den
    }
    /** Plan must already be independently reviewed, SHA-pinned and sealed BEFORE real samples.
     * A self-reported firstCapturedAt or category is not an independently authenticated producer. */
    fun evaluate(planRef: BoundFile, reportRef: BoundFile): DistributionFinding {
        val plan = strictJson.readerFor(ReviewedSamplingPlan::class.java)
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue<ReviewedSamplingPlan>(bound(planRef, 1_048_576))
        require(plan.schema == "bc.playtest_distribution_plan.v1" && plan.status == "reviewed" &&
            plan.reviewer.isNotBlank() && plan.planId.matches(key) && plan.familyId.matches(key) &&
            shaPattern.matches(plan.candidateSha256) && shaPattern.matches(plan.samplingFrameSha256) &&
            plan.samplingUnit.matches(key) && plan.independentSource.isNotBlank() &&
            java.security.MessageDigest.getInstance("SHA-256").digest(
                suiteMapper.writeValueAsBytes(plan.eligibleUnitIds)).joinToString("") { "%02x".format(it) } ==
                plan.samplingFrameSha256 &&
            plan.eligibleUnitIds.size in 1..8192 && plan.eligibleUnitIds.distinct().size == plan.eligibleUnitIds.size &&
            plan.eligibleUnitIds.all(key::matches) &&
            plan.selectedUnitIds.size in 1..512 && plan.selectedUnitIds.distinct().size == plan.selectedUnitIds.size &&
            plan.selectedUnitIds.all { it in plan.eligibleUnitIds } &&
            plan.bins.size in 2..32 && plan.bins.distinct().size == plan.bins.size && plan.bins.all(key::matches) &&
            plan.hypotheses.size in 1..32 && plan.hypotheses.map { it.binId }.distinct().size == plan.hypotheses.size &&
            plan.hypotheses.all { it.binId in plan.bins && it.alphaPartsPerMillion in 1..10_000 &&
                (it.samplerSemantics == "ideal-independent-bernoulli-v1" ||
                    (it.samplerSemantics == "uniform-nextfloat-lt-v1" && it.nullRate.denominator == 16_777_216)) &&
                (it.nullRate.lessThan(it.predeterminedAlternative) ||
                    it.predeterminedAlternative.lessThan(it.nullRate)) } &&
            plan.hypotheses.sumOf { it.alphaPartsPerMillion } <= 10_000 &&
            runCatching { Instant.parse(plan.reviewedAt) }.getOrNull()?.isAfter(Instant.now()) == false) {
            "closed reviewed fixed-N/sample/bin/alternative family and <=1% total false rejection budget required"
        }
        val report = runCatching {
            strictJson.readerFor(DistributionSampleReport::class.java)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue<DistributionSampleReport>(bound(reportRef, 1_048_576))
        }.getOrElse { return DistributionFinding("inconclusive", emptyList(), "missing, malformed or altered raw sample report") }
        val first = runCatching { Instant.parse(report.firstCapturedAt) }.getOrNull()
        val last = runCatching { Instant.parse(report.completedAt) }.getOrNull()
        if (report.schema != "bc.playtest_distribution_samples.v1" || report.planSha256 != planRef.sha256 ||
            first == null || last == null || first.isBefore(Instant.parse(plan.reviewedAt)) || last.isBefore(first) ||
            last.isAfter(Instant.now()) || report.samples.size != plan.selectedUnitIds.size ||
            report.samples.map { it.unitId }.distinct().size != report.samples.size ||
            report.samples.map { it.unitId }.toSet() != plan.selectedUnitIds.toSet() ||
            report.samples.any { it.binId == null || it.binId !in plan.bins }) {
            return DistributionFinding("inconclusive", emptyList(),
                "missing/ambiguous/extra sample, invalid sealed-plan linkage or outcome captured before review")
        }
        val findings = plan.hypotheses.map { hypothesis ->
            val hits = report.samples.count { it.binId == hypothesis.binId }
            val upper = hypothesis.nullRate.lessThan(hypothesis.predeterminedAlternative)
            val (numerator, denominator) = exactTail(report.samples.size, hits, hypothesis.nullRate, upper)
            BinomialFinding(hypothesis.binId, hits, report.samples.size, numerator, denominator,
                numerator * BigInteger.valueOf(1_000_000) <=
                    denominator * BigInteger.valueOf(hypothesis.alphaPartsPerMillion.toLong()))
        }
        return DistributionFinding(if (findings.any { it.rejectedNullInPredeterminedDirection })
            "at-least-one-predeclared-null-rejected" else "null-compatible-not-presence-proof", findings,
            "conditional exact binomial tails under independently justified fixed-N independent samples; " +
                "absence/nonrejection never proves an unobserved category exists")
    }
}
