package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.checkpoints.CheckpointStore
import com.bettercontent.tests.suite.checkpoints.PhysicalEvidenceReplayRequest
import com.bettercontent.tests.suite.checkpoints.readPredeclaredPhysicalWatchPlan
import com.bettercontent.tests.suite.checkpoints.replayPhysicalQualificationEvidence
import com.bettercontent.tests.suite.checkpoints.capturedEvidenceBytes
import com.bettercontent.tests.suite.checkpoints.strictCapturedJson
import com.bettercontent.tests.suite.RawEvidenceRequest
import com.bettercontent.tests.suite.ExternalRawEvidenceDriver
import com.bettercontent.tests.suite.checkpoints.ExternalSamplingDriver
import com.bettercontent.tests.suite.checkpoints.SamplingIntent
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.UUID

/** UNREGISTERED conditional physical primitive case. Two separate restored epochs perform actual
 * OS attack -> decoded remote packet -> original GameMode/drop/tool v5 observation. This class
 * intentionally has NO positive full-case verdict: A has not installed byte-replayed OS/native
 * request binding, original craft/acquisition proof, or a trusted sealed-tool/D-oracle qualifier.
 * A native preexisting Count1 or a vanilla recipe assumption MUST NOT fill those gaps. */
internal object PhysicalPrimitiveHarvestDriver : TrialCaseDriver, ExternalSamplingDriver, ExternalRawEvidenceDriver {
    override val caseId = "primitive.harvest"
    override val requireFreshTerrain = true
    override val assertionIds = setOf("primitive.harvest.native-dig", "primitive.harvest.tier-gating",
        "primitive.harvest.source-attributed-drops", "primitive.harvest.tool-wear-and-conservation")
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private val key = Regex("[a-z0-9][a-z0-9_.-]*")
    private val sha = Regex("[0-9a-f]{64}")
    private data class Tool(val family: String, val item: String, val slot: Int, val canonicalSha: String,
        val acquisitionCase: String)
    private data class Target(val mode: String, val selector: String, val x: Int, val y: Int, val z: Int,
        val face: String, val block: String, val observer: String, val holdId: String,
        val packet: String, val drop: String, val dropItem: String?, val dropCount: Int,
        val callbacks: Int)
    private data class ReviewedPlan(val actor: String, val dimension: String, val profile: String,
        val maxTicks: Int, val tool: Tool, val targets: List<Target>)
    private fun fields(row: JsonNode, names: Set<String>) {
        require(row.isObject && row.fieldNames().asSequence().toSet() == names) {
            "physical primitive target must have the exact reviewed manifest fields"
        }
    }
    private fun text(row: JsonNode, name: String): String = row.path(name).let {
        require(it.isTextual) { "missing reviewed physical $name" }; it.asText()
    }
    private fun number(row: JsonNode, name: String, range: IntRange): Int = row.path(name).let {
        require(it.isIntegralNumber && it.canConvertToInt() && it.asInt() in range) {
            "physical $name not a bounded integer"
        }; it.asInt()
    }
    private fun parse(row: JsonNode): ReviewedPlan {
        fields(row, setOf("player_uuid", "dimension", "input_profile", "max_ticks", "tool", "targets"))
        val actor = text(row, "player_uuid"); val dimension = text(row, "dimension")
        val profile = text(row, "input_profile")
        require(runCatching { UUID.fromString(actor).toString() == actor }.getOrDefault(false) &&
            resource.matches(dimension) && profile in setOf("1280x720@2", "1920x1080@3")) {
            "reviewed real Survival actor/dimension and physical display profile required"
        }
        val toolRow = row.path("tool")
        fields(toolRow, setOf("family", "item", "slot", "count", "source_stack_canonical_sha256",
            "acquisition_case_id"))
        val family = text(toolRow, "family"); val item = text(toolRow, "item")
        val canonical = text(toolRow, "source_stack_canonical_sha256")
        val acquisition = text(toolRow, "acquisition_case_id")
        require(key.matches(family) && resource.matches(item) && sha.matches(canonical) &&
            key.matches(acquisition) && number(toolRow, "count", 1..1) == 1) {
            "source tool identity is a reviewed QUESTION, never natural-acquisition proof"
        }
        val tool = Tool(family, item, number(toolRow, "slot", 0..8), canonical, acquisition)
        val targets = row.path("targets")
        require(targets.isArray && targets.size() == 2) { "exactly correct and wrong isolated physical sites required" }
        val reviewed = targets.map { target ->
            fields(target, setOf("mode", "selector_id", "x", "y", "z", "face", "block_id",
                "observer_id", "hold_id", "expected_packet_variant", "expected_drop_outcome",
                "expected_drop_item_id", "expected_drop_count", "expected_tool_break_callback_count"))
            val mode = text(target, "mode"); val selector = text(target, "selector_id")
            val face = text(target, "face"); val block = text(target, "block_id")
            val observer = text(target, "observer_id"); val hold = text(target, "hold_id")
            val packet = text(target, "expected_packet_variant")
            val drop = text(target, "expected_drop_outcome")
            val dropItemNode = target.path("expected_drop_item_id")
            val dropItem = dropItemNode.takeIf { it.isTextual }?.asText()
            val dropCount = number(target, "expected_drop_count", 0..64)
            require(mode in setOf("correct", "wrong") && key.matches(selector) &&
                face in setOf("north", "south", "east", "west", "up", "down") &&
                resource.matches(block) && block != "minecraft:air" &&
                runCatching { UUID.fromString(observer).toString() == observer }.getOrDefault(false) &&
                Regex("[A-Za-z0-9._-]{1,64}").matches(hold) &&
                packet in setOf("instant_start_only", "progress_start_stop") &&
                (mode == "correct" && drop == "one_full_pickup" && dropItem != null &&
                    resource.matches(dropItem) && dropCount in 1..64 ||
                    mode == "wrong" && drop == "no_drop_negative" && dropItemNode.isNull && dropCount == 0)) {
                "actual-tier target/drop expectation lacks separately reviewed correct/wrong provenance"
            }
            Target(mode, selector, number(target, "x", -30_000_000..30_000_000),
                number(target, "y", -2048..2048), number(target, "z", -30_000_000..30_000_000),
                face, block, observer, hold, packet, drop, dropItem, dropCount,
                number(target, "expected_tool_break_callback_count", 0..1))
        }
        require(reviewed.map { it.mode } == listOf("correct", "wrong") &&
            reviewed.map { Triple(it.x, it.y, it.z) }.distinct().size == 2 &&
            reviewed.map { (it.x shr 4) to (it.z shr 4) }.distinct().size == 2 &&
            reviewed.map { it.selector }.distinct().size == 2 &&
            reviewed.map { it.holdId }.distinct().size == 2 &&
            reviewed.map { it.observer }.distinct().size == 2) {
            "physical trials must have independent predeclared chunks, selectors, input holds and observers"
        }
        return ReviewedPlan(actor, dimension, profile, number(row, "max_ticks", 4..160), tool, reviewed)
    }
    private fun trial(request: TrialRequest): Pair<ReviewedPlan, Target> {
        require(request.caseId == caseId && request.assertionIds == assertionIds &&
            request.provisionedTargets.isEmpty() && key.matches(request.inputCheckpointId)) {
            "physical trial cannot use a fixture, foreign assertion or unsealed input"
        }
        val p = parse(request.parameters)
        val target = p.targets.singleOrNull {
            it.mode == request.trialId && "${p.tool.family}.${it.mode}" == request.targetId
        } ?: error("physical trial does not select one immutable correct/wrong site")
        return p to target
    }
    /** Projection into C's already-frozen real OS/key-up + passive physical-v5 native sequence.
     * No player.dig, direct destroyBlock, fabricated craft, teleport or fixed-wait step. */
    private fun physicalControl(p: ReviewedPlan, target: Target): ObjectNode = suiteMapper.createObjectNode()
        .put("player_uuid", p.actor).put("dimension", p.dimension).put("selector_id", target.selector)
        .put("x", target.x).put("y", target.y).put("z", target.z).put("face", target.face)
        .put("block_id", target.block).put("tool_item_id", p.tool.item)
        .put("tool_canonical_sha256", p.tool.canonicalSha)
        .put("tool_acquisition_case_id", p.tool.acquisitionCase)
        .put("observer_id", target.observer).put("hold_id", target.holdId)
        .put("input_profile", p.profile).put("max_ticks", p.maxTicks)
        .put("expected_packet_variant", target.packet).put("expected_drop_outcome", target.drop)
        .apply {
            if (target.dropItem == null) putNull("expected_drop_item_id")
            else put("expected_drop_item_id", target.dropItem)
            put("expected_drop_count", target.dropCount)
            put("expected_tool_break_callback_count", target.callbacks)
        }
    override fun requiredAcquisitionCases(parameters: JsonNode): Map<String, String> = parse(parameters).tool.let {
        mapOf(it.item to it.acquisitionCase)
    }
    override fun samplingIntents(trial: TrialRequest): List<SamplingIntent> {
        val (p, target) = trial(trial)
        return listOf(SamplingIntent.Site(p.dimension, target.x shr 4, target.z shr 4))
    }
    override fun rawEvidenceRequests(trial: TrialRequest): List<RawEvidenceRequest> {
        val (p, target) = trial(trial)
        return PhysicalMiningControlDriver.rawEvidenceRequests(trial.copy(
            caseId = PhysicalMiningControlDriver.caseId, parameters = physicalControl(p, target)))
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val p = parse(parameters)
        return p.targets.flatMap { target ->
            PhysicalMiningControlDriver.plan(physicalControl(p, target)).map { it.copy(name = "${target.mode}.${it.name}") }
        }
    }
    override fun expandTrials(parameters: JsonNode, inputCheckpointId: String): List<TrialRequest> {
        require(key.matches(inputCheckpointId)) { "one real prepared sealed input checkpoint required" }
        val p = parse(parameters)
        return p.targets.map { target -> TrialRequest(caseId, target.mode,
            "${p.tool.family}.${target.mode}", inputCheckpointId, parameters.deepCopy(), assertionIds, emptySet()) }
    }
    override fun planTrial(request: TrialRequest): List<DriverStep> {
        val (p, target) = trial(request)
        return PhysicalMiningControlDriver.plan(physicalControl(p, target))
    }
    override fun evaluateTrial(request: TrialRequest, observations: List<DriverObservation>): MeasurementVerdict {
        val (p, target) = trial(request)
        val physical = PhysicalMiningControlDriver.evaluate(physicalControl(p, target), observations)
        if (physical.state == "failed") return MeasurementVerdict("failed",
            "complete ${target.mode} physical packet/harvest/drop/tool contradiction; no natural tool qualification")
        return MeasurementVerdict("inconclusive", "${target.mode} physical-v5 mechanism only: missing A-authenticated " +
            "host OS->client->decoded packet + actual craft/ingredient tool origin, watched v3 STOP ancestry, " +
            "two Store-sealed siblings, reviewed candidate/provider and D same-captured vanilla durability join")
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        parse(parameters)
        return MeasurementVerdict("inconclusive", "flat observations cannot establish independent physical " +
            "trials or natural crafted tool origin; full physical qualifier is not installed")
    }
    /** C's ordinary aggregate is intentionally NOT this method. Only A's Store-backed replay of
     * immutable manifest, actual OS/native bytes and separately sealed siblings may conditionally
     * open a whole-case verdict; caller-supplied natural/provider booleans are not an API. */
    internal fun aggregateWithReplayedEvidence(parameters: JsonNode, requests: List<TrialRequest>,
        receipts: List<TrialReceipt>, store: CheckpointStore,
        source: PhysicalEvidenceReplayRequest): MeasurementVerdict {
        val blocked = aggregate(parameters, requests, receipts)
        require(blocked.state == "inconclusive" && source.siblingEpochs.size == 2 &&
            source.siblingEpochs.map { it.id } == receipts.map { it.epochId } &&
            requests.all { it.inputCheckpointId == source.preparedInput.id }) {
            "physical evidence refs do not match the two already re-evaluated isolated trials"
        }
        val reviewed = readPredeclaredPhysicalWatchPlan(source.manifest)
        val p = parse(parameters)
        require(reviewed.preparedInputId == source.preparedInput.id &&
            reviewed.sites.zip(reviewed.selectors).map { (site, selector) ->
                listOf(site.position.dimension, site.position.x, site.position.y, site.position.z,
                    site.blockId, selector)
            }.toSet() == p.targets.map { target ->
                listOf(p.dimension, target.x, target.y, target.z, target.block, target.selector)
            }.toSet()) { "reviewed manifest watched sites differ from physical trial parameters" }
        val manifest = strictCapturedJson(capturedEvidenceBytes(source.manifest, 67_108_864))
        val binding = manifest.path("driver_bindings").single { it.path("case_id").asText() == caseId }
        require(binding.path("parameters") == parameters) {
            "physical controls do not match the immutable prelaunch manifest target and tool review"
        }
        // This A-owned function currently returns BLOCKED even when refs are supplied: exact
        // original OS->client->packet, craft/ingredient and provider wear adapters are NOT installed.
        val replay = replayPhysicalQualificationEvidence(store, source)
        require(replay.state in setOf("blocked", "failed", "passed")) {
            "unreviewed A replay state cannot become a full case outcome"
        }
        if (replay.state == "blocked") return MeasurementVerdict("inconclusive", replay.reason)
        if (replay.state == "failed") return replay
        val contradictions = requests.zip(receipts).any { (request, receipt) ->
            receipt.state == "failed" && evaluateTrial(request, receipt.observations).state == "failed"
        }
        return if (contradictions) MeasurementVerdict("failed",
            "A-authenticated original bytes contradict independently reviewed C physical tier/packet/tool expectation")
        else MeasurementVerdict("passed", "A replayed immutable reviewed origin and tool acquisition, actual " +
            "OS/client/decoded packet, two Store-sealed siblings and D provider-specific typed durability; " +
            "C independently accepted both physical-v5 tier/drop trial observations")
    }
    override fun aggregate(parameters: JsonNode, requests: List<TrialRequest>,
        receipts: List<TrialReceipt>): MeasurementVerdict {
        parse(parameters)
        require(requests.size == 2 && receipts.size == 2 &&
            requests == expandTrials(parameters, requests.first().inputCheckpointId) &&
            receipts.map { it.trialId } == listOf("correct", "wrong") &&
            receipts.map { it.targetId } == requests.map { it.targetId } &&
            receipts.map { it.inputCheckpointId } == requests.map { it.inputCheckpointId } &&
            receipts.map { it.epochId }.distinct().size == 2 && receipts.all {
                it.epochId.isNotBlank() && it.inputCheckpointVerified && it.cleanupComplete
            }) { "physical correct/wrong trials were not distinct fresh restorations of one sealed tool input" }
        val boundedContradictions = requests.zip(receipts).map { (request, receipt) ->
            val result = evaluateTrial(request, receipt.observations)
            require(receipt.state == result.state) { "physical trial stored a state unlike its raw oracle" }
            result.state == "failed"
        }.any { it }
        return MeasurementVerdict("inconclusive", "physical mechanism observations alone never qualify " +
            "primitive.harvest${if (boundedContradictions) " (bounded diagnostic contradiction present)" else ""}: " +
            "absent independently replayed origin->tool ancestry, craft result/ingredient source, actual " +
            "OS/client/packet join, two saved siblings, selected vanilla tool+D durability, reviewed " +
            "candidate and provider coverage; no full positive or full negative branch is installed")
    }
}
