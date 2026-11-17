package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.client.INPUT_RUNTIME_MARKER
import com.bettercontent.tests.suite.oracles.DistributionFinding
import com.bettercontent.tests.suite.oracles.ReviewedSamplingPlan
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Source-only fixed-N identical-item physical chance trials. UNREGISTERED until A seals and checks
 * reviewed sampling-unit membership against the pre-capture plan, candidate and native epoch ingress. */
internal object FoodChanceTrialDriver : TrialCaseDriver {
    override val caseId = "control.food.chance-trials"
    private data class Sample(val unit: String, val fixture: String, val observer: String)
    private data class Plan(val player: String, val dimension: String, val profile: String, val item: String,
        val effect: String, val acquisition: String, val samples: List<Sample>)
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private val key = Regex("[a-z][a-z0-9._:-]{0,127}")
    private fun fields(row: JsonNode, names: Set<String>) {
        require(row.isObject && row.fieldNames().asSequence().toSet() == names) { "chance trial unknown/missing field" }
    }
    private fun text(row: JsonNode, name: String): String {
        val value = row.path(name); require(value.isTextual) { "missing $name" }; return value.asText()
    }
    private fun parse(parameters: JsonNode): Plan {
        fields(parameters, setOf("player_uuid", "dimension", "input_profile", "item_id", "effect_id",
            "acquisition_case_id", "samples"))
        val player = text(parameters, "player_uuid"); val dimension = text(parameters, "dimension")
        val profile = text(parameters, "input_profile"); val item = text(parameters, "item_id")
        val effect = text(parameters, "effect_id"); val acquisition = text(parameters, "acquisition_case_id")
        require(runCatching { UUID.fromString(player).toString() == player }.getOrDefault(false) &&
            resource.matches(dimension) && resource.matches(item) && resource.matches(effect) &&
            profile in setOf("1280x720@2", "1920x1080@3") && key.matches(acquisition)) {
            "unreviewed chance food identity/profile"
        }
        val rows = parameters.path("samples")
        require(rows.isArray && rows.size() in 2..512) { "frozen 2..512 food sample units required" }
        val samples = rows.map { row ->
            fields(row, setOf("unit_id", "fixture_id", "observer_id"))
            val unit = text(row, "unit_id"); val fixture = text(row, "fixture_id")
            val observer = text(row, "observer_id")
            require(key.matches(unit) && Regex("[A-Za-z0-9._-]{1,64}").matches(fixture) &&
                runCatching { UUID.fromString(observer).toString() == observer }.getOrDefault(false)) {
                "unreviewed food sample identity"
            }
            Sample(unit, fixture, observer)
        }
        require(samples.map { it.unit }.distinct().size == samples.size &&
            samples.map { it.fixture }.distinct().size == samples.size &&
            samples.map { it.observer }.distinct().size == samples.size) { "repeated or replaced chance sample" }
        return Plan(player, dimension, profile, item, effect, acquisition, samples)
    }
    override fun fixtureProvisionedTargets(parameters: JsonNode): Set<String> = setOf(parse(parameters).item)
    override fun requiredAcquisitionCases(parameters: JsonNode): Map<String, String> = parse(parameters).let {
        mapOf(it.item to it.acquisition)
    }
    private fun steps(p: Plan, sample: Sample): List<DriverStep> {
        val actor = suiteMapper.createObjectNode().put("player_uuid", p.player)
        val held = suiteMapper.createObjectNode().put("hold_id", sample.fixture)
            .put("player_uuid", "@observed-player-uuid").put("dimension", p.dimension)
            .put("input_runtime_sha256", INPUT_RUNTIME_MARKER)
        return listOf(
            DriverStep("fixture", DriverAction("fixture.begin", actor.deepCopy().put("fixture_id", sample.fixture)
                .put("item_id", p.item).put("count", 1).put("hand", "main_hand")
                .put("food_level", 0).put("saturation", 0.0))),
            DriverStep("before", DriverAction("player.snapshot", actor.deepCopy())),
            DriverStep("arm", DriverAction("player.observe_item_use.arm", suiteMapper.createObjectNode()
                .put("player_uuid", "@observed-player-uuid").put("dimension", p.dimension)
                .put("observer_id", sample.observer).put("expected_item_id", p.item)
                .put("expected_provider", "living_entity_use_item").put("expected_kind", "eat")
                .put("hand", "main_hand").put("deadline_ticks", 7200)
                .put("witness_context", "@controller-witness-context-v1"))),
            DriverStep("hold", DriverAction("client.input_hold", held.deepCopy().put("profile", p.profile)
                .put("kind", "use").put("max_ticks", 120), 140, "client")),
            DriverStep("release", DriverAction("client.input_release", held.deepCopy(), 200, "client")),
            DriverStep("status", DriverAction("client.input_status", held.deepCopy(), 200, "client")),
            DriverStep("consume", DriverAction("player.observe_item_use.await", suiteMapper.createObjectNode()
                .put("player_uuid", "@observed-player-uuid").put("dimension", p.dimension)
                .put("observer_id", sample.observer).put("arm_request_sequence", "@arm-request-sequence")
                .put("arm_action_ordinal", "@arm-action-ordinal")
                .put("witness_context", "@controller-witness-context-v1"), 7200)),
            DriverStep("after", DriverAction("player.snapshot", actor.deepCopy())),
            DriverStep("restore", DriverAction("fixture.restore", suiteMapper.createObjectNode()
                .put("fixture_id", sample.fixture))))
    }
    override fun plan(parameters: JsonNode): List<DriverStep> = parse(parameters).let { p ->
        p.samples.flatMap { sample -> steps(p, sample).map { it.copy(name = "${sample.unit}.${it.name}") } }
    }
    override fun expandTrials(parameters: JsonNode, inputCheckpointId: String): List<TrialRequest> {
        require(Regex("[a-z0-9][a-z0-9_.-]*").matches(inputCheckpointId)) { "invalid sealed chance baseline" }
        val p = parse(parameters)
        return p.samples.map { TrialRequest(caseId, it.unit, p.item, inputCheckpointId,
            parameters.deepCopy(), assertionIds, setOf(p.item)) }
    }
    private fun target(request: TrialRequest): Pair<Plan, Sample> {
        require(request.caseId == caseId && request.assertionIds == assertionIds &&
            request.provisionedTargets == setOf(request.targetId)) { "foreign food sample" }
        val p = parse(request.parameters)
        val sample = p.samples.singleOrNull { it.unit == request.trialId && p.item == request.targetId }
            ?: throw IllegalArgumentException("food unit not in preselected frame")
        return p to sample
    }
    override fun planTrial(request: TrialRequest): List<DriverStep> = target(request).let { (p, sample) -> steps(p, sample) }
    private fun capture(p: Plan, sample: Sample, request: TrialRequest,
        receipt: TrialReceipt): FoodChanceCapture = FoodChanceCapture(sample.unit, receipt.epochId, request.inputCheckpointId,
        p.item, p.player, p.dimension, p.profile, sample.fixture, sample.observer, receipt.observations.subList(1, 8))
    override fun evaluateTrial(request: TrialRequest, observations: List<DriverObservation>): MeasurementVerdict {
        val (p, sample) = target(request)
        require(validateTerminalSequence(planTrial(request), observations) == null && observations.size == 9) {
            "food chance trial incomplete or wrong action source"
        }
        val begin = observations.first().payload; val restore = observations.last().payload
        require(begin.path("fixture_id").asText() == sample.fixture && begin.path("owner_case_id").asText() == caseId &&
            begin.path("player_uuid").asText() == p.player && begin.path("provisioned_item").asText() == p.item &&
            begin.path("provisioned_count").asInt(-1) == 1 && begin.path("food_after").asInt(-1) == 0 &&
            begin.path("acquisition_origin").asText() == "authored_fixture_not_natural" &&
            restore.path("fixture_id").asText() == sample.fixture && restore.path("owner_case_id").asText() == caseId &&
            restore.path("player_uuid").asText() == p.player && restore.path("cleanup_complete").asBoolean(false) &&
            restore.path("owned_residuals").asInt(-1) == 0 && restore.path("vanilla_restored").asBoolean(false) &&
            restore.path("food_restored").asBoolean(false) && restore.path("effects_restored").asBoolean(false) &&
            restore.path("selected_slot_restored").asBoolean(false) &&
            restore.path("inventory_mismatched_slots").asInt(-1) == 0) { "isolated owned fixture/cleanup missing" }
        FoodStochasticEvidence.observedBin(FoodChanceCapture(sample.unit, "unverified-until-epoch-sealed",
            request.inputCheckpointId, p.item, p.player, p.dimension, p.profile, sample.fixture, sample.observer,
            observations.subList(1, 8)), p.effect)
        return MeasurementVerdict("passed", "one actual food consumption/outcome observed in owned fixture; NOT a chance-rate or acquisition verdict")
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val p = parse(parameters)
        require(observations.size == p.samples.size * 9) { "missing fixed food chance trial" }
        val requests = expandTrials(parameters, "ephemeral-unsealed-baseline")
        for ((i, request) in requests.withIndex()) {
            val result = evaluateTrial(request, observations.drop(i * 9).take(9))
            if (result.state != "passed") return result
        }
        return MeasurementVerdict("passed", "physical chance outcomes observed; no distribution or natural acquisition inference")
    }
    override fun aggregate(parameters: JsonNode, requests: List<TrialRequest>, receipts: List<TrialReceipt>): MeasurementVerdict {
        val p = parse(parameters)
        require(requests.size == p.samples.size && receipts.size == p.samples.size && requests.isNotEmpty() &&
            requests == expandTrials(parameters, requests.first().inputCheckpointId) &&
            receipts.map { it.trialId } == requests.map { it.trialId } &&
            receipts.map { it.epochId }.distinct().size == receipts.size &&
            receipts.all { it.epochId.isNotBlank() && it.inputCheckpointVerified && it.cleanupComplete &&
                it.inputCheckpointId == requests.first().inputCheckpointId }) {
            "missing/replayed/unsealed fixed chance sample epoch"
        }
        for ((request, receipt) in requests.zip(receipts)) {
            require(receipt.targetId == request.targetId && receipt.state == evaluateTrial(request, receipt.observations).state) {
                "stored chance trial differs from physical observations"
            }
        }
        return MeasurementVerdict("passed", "all preselected identical food behavior trials physically observed; exact D oracle still required")
    }
    /** Pre-capture membership gate: A must independently authenticate this reviewed, SHA-bound plan object,
     * candidate RNG semantics and timestamp BEFORE dispatch, never parse a driver-supplied plan substitute. */
    internal fun verifyPreCapturePlan(parameters: JsonNode, reviewedPlan: ReviewedSamplingPlan,
        candidateSha256: String) {
        val p = parse(parameters)
        val selected = p.samples.map { it.unit }
        val frameHash = MessageDigest.getInstance("SHA-256")
            .digest(suiteMapper.writeValueAsBytes(reviewedPlan.eligibleUnitIds))
            .joinToString("") { "%02x".format(it) }
        require(reviewedPlan.schema == "bc.playtest_distribution_plan.v1" &&
            reviewedPlan.status == "reviewed" && reviewedPlan.reviewer.isNotBlank() &&
            Regex("[0-9a-f]{64}").matches(candidateSha256) &&
            reviewedPlan.candidateSha256 == candidateSha256 && reviewedPlan.familyId == "survival.food" &&
            reviewedPlan.samplingUnit == "isolated-food-epoch" &&
            reviewedPlan.independentSource.isNotBlank() &&
            reviewedPlan.samplingFrameSha256 == frameHash &&
            reviewedPlan.eligibleUnitIds.size in selected.size..8192 &&
            reviewedPlan.eligibleUnitIds.distinct().size == reviewedPlan.eligibleUnitIds.size &&
            selected == reviewedPlan.selectedUnitIds &&
            selected.all { it in reviewedPlan.eligibleUnitIds } &&
            reviewedPlan.bins.toSet() == setOf("present", "absent") &&
            reviewedPlan.hypotheses.isNotEmpty() && reviewedPlan.hypotheses.map { it.binId }.distinct().size == reviewedPlan.hypotheses.size &&
            reviewedPlan.hypotheses.all {
                it.binId in setOf("present", "absent") && it.alphaPartsPerMillion in 1..10_000 &&
                (it.nullRate.lessThan(it.predeterminedAlternative) ||
                    it.predeterminedAlternative.lessThan(it.nullRate)) &&
                (it.samplerSemantics == "ideal-independent-bernoulli-v1" ||
                    (it.samplerSemantics == "uniform-nextfloat-lt-v1" && it.nullRate.denominator == 16_777_216))
            } && reviewedPlan.hypotheses.sumOf { it.alphaPartsPerMillion } <= 10_000 &&
            runCatching { Instant.parse(reviewedPlan.reviewedAt).isBefore(Instant.now()) }.getOrDefault(false)) {
            "candidate-bound independently reviewed fixed food sample membership must precede trial dispatch"
        }
    }
    /** A must call only after separately authenticating and freezing plan, candidate, raw terminal inputs and epochs. */
    internal fun evaluateReviewedBatch(parameters: JsonNode, requests: List<TrialRequest>, receipts: List<TrialReceipt>,
        sealedPlan: BoundFile, verifiedReport: BoundFile, candidateSha256: String): DistributionFinding {
        require(aggregate(parameters, requests, receipts).state == "passed") { "sample evidence not complete" }
        val p = parse(parameters)
        val samples = requests.zip(receipts).map { (request, receipt) ->
            val sample = p.samples.single { it.unit == request.trialId }
            capture(p, sample, request, receipt)
        }
        return FoodStochasticEvidence.evaluate(sealedPlan, verifiedReport, candidateSha256, p.effect, samples)
    }
}
