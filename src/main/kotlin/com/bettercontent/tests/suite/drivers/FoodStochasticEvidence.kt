package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.client.validateAuthenticHeldInput
import com.bettercontent.tests.suite.oracles.DistributionFinding
import com.bettercontent.tests.suite.oracles.DistributionSampleReport
import com.bettercontent.tests.suite.oracles.ExactDistributionOracle
import com.bettercontent.tests.suite.oracles.ReviewedSamplingPlan
import com.bettercontent.tests.suite.suiteMapper
import java.nio.file.Path

/** Post-batch consumer, not an acquisition, sampling-frame or independence producer.
 * A must seal and authenticate the plan BEFORE world creation and verify all raw trial terminals.
 * No report-provided bin, claimed sample ID or plan timestamp is accepted as a player outcome. */
internal data class FoodChanceCapture(val unitId: String, val epochId: String, val checkpointId: String,
    val itemId: String, val actorUuid: String, val dimension: String, val inputProfile: String,
    val holdId: String, val observerId: String, val observations: List<DriverObservation>)
internal object FoodStochasticEvidence {
    private val sha = Regex("[0-9a-f]{64}")
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    /** Exactly one genuine physically consumed instance of the SAME item per independently isolated epoch.
     * The exact-binomial model is invalid for heterogeneous food items with different chance rates. */
    fun evaluate(sealedPlan: BoundFile, verifiedReport: BoundFile, candidateSha256: String,
        effectId: String, captures: List<FoodChanceCapture>): DistributionFinding {
        require(sha.matches(candidateSha256) && resource.matches(effectId)) { "candidate or effect identity invalid" }
        val finding = ExactDistributionOracle.evaluate(sealedPlan, verifiedReport)
        if (finding.status == "inconclusive") return finding
        // The D oracle verified file hashes and the complete closed reviewed plan before this read.
        val planPath = Path.of(sealedPlan.path); val reportPath = Path.of(verifiedReport.path)
        require(Hashes.sha256(planPath) == sealedPlan.sha256 && Hashes.sha256(reportPath) == verifiedReport.sha256) {
            "plan/report changed after exact oracle evaluation"
        }
        val plan = suiteMapper.readValue(planPath.toFile(), ReviewedSamplingPlan::class.java)
        val report = suiteMapper.readValue(reportPath.toFile(), DistributionSampleReport::class.java)
        require(plan.familyId == "survival.food" && plan.candidateSha256 == candidateSha256 &&
            plan.samplingUnit == "isolated-food-epoch" && plan.bins.toSet() == setOf("present", "absent") &&
            captures.size == plan.selectedUnitIds.size && captures.size in 2..512 &&
            captures.map { it.unitId }.distinct().size == captures.size &&
            captures.map { it.unitId }.toSet() == plan.selectedUnitIds.toSet() &&
            captures.map { it.epochId }.distinct().size == captures.size &&
            captures.map { it.checkpointId }.distinct().size == 1 &&
            captures.map { it.itemId }.distinct().size == 1 &&
            captures.map { it.actorUuid }.distinct().size == 1 &&
            captures.map { it.dimension }.distinct().size == 1 &&
            captures.map { it.observerId }.distinct().size == captures.size &&
            captures.map { it.holdId }.distinct().size == captures.size &&
            captures.all { it.inputProfile in setOf("1280x720@2", "1920x1080@3") } &&
            plan.hypotheses.all { it.binId in setOf("present", "absent") }) {
            "fixed preselected same-item sample membership, disjoint epochs and predeclared bin family required"
        }
        val actual = captures.associate { capture -> capture.unitId to observedBin(capture, effectId) }
        require(report.samples.all { sample -> actual[sample.unitId] == sample.binId } &&
            Hashes.sha256(planPath) == sealedPlan.sha256 && Hashes.sha256(reportPath) == verifiedReport.sha256) {
            "raw physical consumption outcomes differ from pinned distribution report, or evidence changed"
        }
        return finding // Null compatibility never proves a random effect exists or is balanced.
    }
    internal fun observedBin(sample: FoodChanceCapture, effectId: String): String {
        require(sample.observations.size == 7) { "one frozen pre/post/arm/hold/release/status/await capture required" }
        val observations = sample.observations
        require(observations.map { it.driver } == listOf("player.snapshot", "player.observe_item_use.arm",
            "client.input_hold", "client.input_release", "client.input_status", "player.observe_item_use.await", "player.snapshot") &&
            observations.map { it.source } == listOf("server", "server", "client", "client", "client", "server", "server") &&
            observations.all { it.kind == "completed" && it.payload.isObject }) { "failed/replaced physical sample step" }
        val before = observations[0].payload; val arm = observations[1].payload
        val hold = observations[2].payload; val release = observations[3].payload
        val status = observations[4].payload; val event = observations[5].payload
        val after = observations[6].payload
        require(listOf(before, after).all {
            it.path("uuid").asText() == sample.actorUuid && it.path("dimension").asText() == sample.dimension &&
                it.path("effects").isArray && it.path("food_level").isIntegralNumber &&
                it.path("saturation").isNumber && it.path("inventory").isArray &&
                it.path("held_item").isTextual && it.path("held_count").isIntegralNumber
        } && before.path("effects").size() == 0 &&
            before.path("held_item").asText() == sample.itemId && before.path("held_count").asInt() == 1 &&
            before.path("inventory").sumOf { if (it.path("item").asText() == sample.itemId) it.path("count").asInt(0) else 0 } == 1 &&
            after.path("inventory").none { it.path("item").asText() == sample.itemId } &&
            after.path("held_item").asText() != sample.itemId &&
            after.path("effects").all { it.path("id").asText() == effectId } &&
            arm.path("armed_before_input").asBoolean(false) &&
            arm.path("observer_id").asText() == sample.observerId &&
            arm.path("arm_request_sequence").isIntegralNumber && arm.path("arm_action_ordinal").isIntegralNumber &&
            event.path("schema").asText() == "bc.playtest_passive_item_use.v1" &&
            event.path("observer_id").asText() == sample.observerId &&
            event.path("actor_uuid").asText() == sample.actorUuid && event.path("dimension").asText() == sample.dimension &&
            event.path("expected_item_id").asText() == sample.itemId &&
            event.path("expected_kind").asText() == "eat" &&
            event.path("expected_provider").asText() == "living_entity_use_item" &&
            event.path("packet_source").asText() == "ServerGamePacketListenerImpl.handleUseItem/server_thread_HEAD" &&
            event.path("start_source").asText() == "LivingEntityUseItemEvent.Start/MONITOR" &&
            event.path("finish_source").asText() == "LivingEntityUseItemEvent.Finish/MONITOR" &&
            event.path("final_source").asText() == "ServerTickEvent.END/actual_player_state" &&
            event.path("item_before_arm").path("item_id").asText() == sample.itemId &&
            event.path("item_before_arm").path("count").asInt(-1) == before.path("held_count").asInt() &&
            event.path("item_at_start").path("count") == event.path("item_before_arm").path("count") &&
            event.path("item_at_finish").path("count") == event.path("item_before_arm").path("count") &&
            sha.matches(event.path("item_before_arm").path("content_sha256").asText()) &&
            event.path("item_at_start").path("content_sha256") == event.path("item_before_arm").path("content_sha256") &&
            event.path("item_at_finish").path("content_sha256") == event.path("item_before_arm").path("content_sha256") &&
            event.path("item_after_applied").path("content_sha256") != event.path("item_before_arm").path("content_sha256") &&
            event.path("narrow_event_chain_complete").asBoolean(false) &&
            event.path("packet_count").asInt(-1) == 1 && event.path("start_count").asInt(-1) == 1 &&
            event.path("finish_count").asInt(-1) == 1 &&
            event.path("arm_request_sequence") == arm.path("arm_request_sequence") &&
            event.path("arm_action_ordinal") == arm.path("arm_action_ordinal") &&
            event.path("acquisition_origin").asText() == "authored_fixture_not_natural" &&
            !event.path("natural_acquisition_proven_by_native").asBoolean(true) &&
            validateAuthenticHeldInput(hold, release, status, sample.actorUuid, sample.dimension,
                sample.inputProfile, sample.holdId, sample.itemId, hold.path("input_runtime_sha256").asText())) {
            "chance outcome lacks one authentic single-use chain and a fresh pre-use baseline"
        }
        val effectRows = after.path("effects").filter { it.path("id").asText() == effectId }
        require(effectRows.size <= 1 && effectRows.all { it.path("amplifier").isIntegralNumber &&
            it.path("duration").isIntegralNumber && it.path("duration").asInt() > 0 } &&
            after.path("food_level").asInt() > before.path("food_level").asInt() &&
            event.path("physiology_before").path("food_level") == before.path("food_level") &&
            event.path("physiology_after").path("food_level") == after.path("food_level") &&
            event.path("physiology_before").path("thirst") == event.path("physiology_after").path("thirst") &&
            event.path("physiology_before").path("quenched") == event.path("physiology_after").path("quenched")) {
            "food not consumed or ambiguous/untyped chance outcome"
        }
        return if (effectRows.size == 1) "present" else "absent"
    }
}
