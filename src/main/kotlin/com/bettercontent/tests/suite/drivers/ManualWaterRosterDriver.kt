package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.UUID

/** Isolated paid manual item-drinking trials from ONE genuinely acquired union-inventory checkpoint. */
internal object ManualWaterRosterDriver : TrialCaseDriver {
    override val caseId = "water.manual"
    override val assertionIds = setOf("water.manual.native-sip", "water.manual.paid-volume", "water.manual.thirst-and-remainder")
    private data class Source(val trialId: String, val slot: Int, val item: String, val count: Int,
        val hash: String, val acquisition: String, val water: JsonNode)
    private data class Plan(val player: String, val food: Int, val saturation: Double, val sources: List<Source>)
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private val key = Regex("[a-z0-9][a-z0-9_.-]*")
    private val hashPattern = Regex("[0-9a-f]{64}")
    private fun fields(row: JsonNode, keys: Set<String>) {
        require(row.isObject && row.fieldNames().asSequence().toSet() == keys) { "unexpected/missing water source field" }
    }
    private fun text(row: JsonNode, key: String): String {
        val value = row.path(key); require(value.isTextual) { "invalid $key" }; return value.asText()
    }
    private fun integer(row: JsonNode, key: String, range: IntRange): Int {
        val value = row.path(key); require(value.isIntegralNumber && value.canConvertToInt() && value.asInt() in range) { "invalid $key" }
        return value.asInt()
    }
    private fun parse(node: JsonNode): Plan {
        fields(node, setOf("player_uuid", "food_level", "saturation", "sources"))
        val player = text(node, "player_uuid"); require(runCatching { UUID.fromString(player).toString() }.getOrNull() == player)
        val food = integer(node, "food_level", 0..20)
        val sat = node.path("saturation"); require(sat.isNumber && sat.asDouble().isFinite() && sat.asDouble() in 0.0..food.toDouble())
        val rows = node.path("sources"); require(rows.isArray && rows.size() in 2..32) { "closed multi-source manual water roster required" }
        val sources = rows.map { row ->
            fields(row, setOf("trial_id", "slot", "source_item", "source_count", "source_stack_sha256", "acquisition_case_id", "water"))
            val trial = text(row, "trial_id"); val item = text(row, "source_item")
            val hash = text(row, "source_stack_sha256"); val acquisition = text(row, "acquisition_case_id")
            require(key.matches(trial) && trial.length <= 58 && resource.matches(item) && hashPattern.matches(hash) && key.matches(acquisition)) {
                "invalid reviewed water source identity/acquisition"
            }
            val slot = integer(row, "slot", 0..35); val count = integer(row, "source_count", 1..64)
            val water = row.path("water"); WaterSipControlDriver.plan(water)
            require(water.path("player_uuid").asText() == player && water.path("hand").asText() == "main_hand" &&
                water.path("source_item").asText() == item && water.path("source_count").asInt() == count) {
                "manual water expectation differs from selected checkpoint source"
            }
            Source(trial, slot, item, count, hash, acquisition, water)
        }
        require(sources.map { it.trialId }.distinct().size == sources.size &&
            sources.map { it.slot }.distinct().size == sources.size &&
            sources.map { it.item }.distinct().size == sources.size) {
            "one source cannot stand in for complete reviewed multi-source water roster"
        }
        return Plan(player, food, sat.asDouble(), sources)
    }
    override fun requiredAcquisitionCases(parameters: JsonNode): Map<String, String> = parse(parameters).sources.associate {
        it.item to it.acquisition
    }
    override fun plan(parameters: JsonNode): List<DriverStep> = parse(parameters).sources.flatMap { source ->
        val steps = sourceSteps(parse(parameters), source)
        steps.map { it.copy(name = "${source.trialId}.${it.name}") }
    }
    private fun paidTarget(p: Plan, source: Source): ObjectNode = suiteMapper.createObjectNode()
        .put("fixture_id", "water_${source.trialId}").put("food_level", p.food).put("saturation", p.saturation)
        .apply { set<JsonNode>("water", source.water.deepCopy()) }
    private fun sourceSteps(p: Plan, source: Source): List<DriverStep> {
        val actor = suiteMapper.createObjectNode().put("player_uuid", p.player)
        val select = actor.deepCopy().put("slot", source.slot).put("expected_item", source.item)
            .put("expected_count", source.count).put("expected_stack_sha256", source.hash)
        return listOf(DriverStep("before-selection", DriverAction("player.snapshot", actor.deepCopy())),
            DriverStep("select", DriverAction("player.select_item", select)),
            DriverStep("after-selection", DriverAction("player.snapshot", actor.deepCopy()))) +
            PaidWaterTrialControlDriver.plan(paidTarget(p, source)) +
            DriverStep("after-restoration", DriverAction("player.snapshot", actor.deepCopy()))
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val p = parse(parameters); require(observations.size == p.sources.size * 9)
        for ((index, source) in p.sources.withIndex()) {
            val verdict = checkSource(p, source, observations.drop(index * 9).take(9))
            if (verdict.state != "passed") return verdict
        }
        return MeasurementVerdict("passed", "all reviewed manual source trials observed; natural acquisition proven only by prerequisite case receipts")
    }
    private fun inventory(row: JsonNode): Map<Int, Triple<String, Int, String>> {
        require(row.path("uuid").isTextual && row.path("held_item").isTextual && row.path("held_stack_sha256").isTextual &&
            row.path("selected_slot").isIntegralNumber && row.path("selected_slot").asInt() in 0..8 &&
            hashPattern.matches(row.path("held_stack_sha256").asText()) &&
            row.path("inventory").isArray && row.path("inventory").size() <= 41) {
            "independent hashed inventory snapshot missing"
        }
        val entries = row.path("inventory").map { entry ->
            val slot = entry.path("slot"); val id = entry.path("item"); val count = entry.path("count"); val hash = entry.path("stack_sha256")
            require(slot.isIntegralNumber && slot.asInt() in 0..40 && id.isTextual && resource.matches(id.asText()) &&
                count.isIntegralNumber && count.asInt() in 1..64 && hash.isTextual && hashPattern.matches(hash.asText())) {
                "untyped hashed inventory entry"
            }
            slot.asInt() to Triple(id.asText(), count.asInt(), hash.asText())
        }
        require(entries.map { it.first }.distinct().size == entries.size) { "duplicate inventory slot" }
        return entries.toMap()
    }
    private fun checkSource(p: Plan, source: Source, observations: List<DriverObservation>): MeasurementVerdict {
        require(observations.size == 9)
        val pre = observations[0].payload; val selected = observations[1].payload
        val post = observations[2].payload; val restored = observations[8].payload
        val before = inventory(pre); val after = inventory(post); val afterRestore = inventory(restored)
        require(selected.path("player_uuid").isTextual && selected.path("source_slot").isIntegralNumber &&
            selected.path("selected_before").isIntegralNumber && selected.path("selected_after").isIntegralNumber &&
            selected.path("item").isTextual && selected.path("count").isIntegralNumber &&
            selected.path("stack_sha256").isTextual && selected.path("source_slot_item_after").isTextual &&
            selected.path("inventory_conserved").isBoolean && selected.path("inventory_multiset_before_sha256").isTextual &&
            selected.path("inventory_multiset_after_sha256").isTextual && selected.path("native_packet_handler").isTextual &&
            selected.path("client_acknowledgment").isTextual && selected.path("acquisition_origin").isTextual &&
            selected.path("fixture_mutations").isArray && selected.path("selected_before").asInt() in 0..8 &&
            selected.path("selected_after").asInt() in 0..8 &&
            hashPattern.matches(selected.path("stack_sha256").asText()) &&
            hashPattern.matches(selected.path("inventory_multiset_before_sha256").asText()) &&
            hashPattern.matches(selected.path("inventory_multiset_after_sha256").asText())) {
            "native packet selection/provenance receipt missing"
        }
        val selectedBefore = pre.path("selected_slot").asInt(); val nativeRoute = if (source.slot <= 8)
            "ServerGamePacketListenerImpl.handleSetCarriedItem" else "ServerGamePacketListenerImpl.handleContainerClick_SWAP"
        val sourceIdentity = Triple(source.item, source.count, source.hash)
        val selectedValid = listOf(pre, post, restored).all { it.path("uuid").asText() == p.player } &&
            before[source.slot] == sourceIdentity && selected.path("player_uuid").asText() == p.player &&
            selected.path("source_slot").asInt() == source.slot && selected.path("selected_before").asInt() == selectedBefore &&
            selected.path("selected_after").asInt() == (if (source.slot <= 8) source.slot else selectedBefore) &&
            selected.path("item").asText() == source.item && selected.path("count").asInt() == source.count &&
            selected.path("stack_sha256").asText() == source.hash &&
            selected.path("source_slot_item_after").asText() == (after[source.slot]?.first ?: "minecraft:air") &&
            selected.path("inventory_conserved").asBoolean() &&
            selected.path("inventory_multiset_before_sha256").asText() == selected.path("inventory_multiset_after_sha256").asText() &&
            selected.path("native_packet_handler").asText() == nativeRoute &&
            selected.path("client_acknowledgment").asText() == "unverified_without_client_observer" &&
            selected.path("acquisition_origin").asText() == "preexisting_unattributed" &&
            selected.path("fixture_mutations").size() == 0 &&
            post.path("held_item").asText() == source.item && post.path("held_stack_sha256").asText() == source.hash &&
            post.path("selected_slot").asInt() == selected.path("selected_after").asInt() &&
            after[post.path("selected_slot").asInt()] == sourceIdentity && afterRestore == after &&
            restored.path("held_stack_sha256").asText() == source.hash &&
            before.values.groupingBy { it.third }.eachCount() == after.values.groupingBy { it.third }.eachCount()
        if (!selectedValid) return MeasurementVerdict("failed", "existing source selection/hash/conservation differs from reviewed checkpoint slot")
        val water = PaidWaterTrialControlDriver.evaluateForOwner(paidTarget(p, source), observations.drop(3).take(5), caseId)
        return water.copy(reason = "${source.trialId}: ${water.reason}; server packet route only, no client UI ACK")
    }
    override fun expandTrials(parameters: JsonNode, inputCheckpointId: String): List<TrialRequest> {
        require(key.matches(inputCheckpointId)) { "invalid sealed source checkpoint" }
        val p = parse(parameters)
        return p.sources.map { source -> TrialRequest(caseId, source.trialId, source.item,
            inputCheckpointId, parameters.deepCopy(), assertionIds, emptySet()) }
    }
    private fun source(request: TrialRequest): Pair<Plan, Source> {
        require(request.caseId == caseId && request.assertionIds == assertionIds && key.matches(request.inputCheckpointId) &&
            request.provisionedTargets.isEmpty()) { "manual water trial contract mismatch" }
        val p = parse(request.parameters)
        return p to (p.sources.singleOrNull { it.trialId == request.trialId && it.item == request.targetId }
            ?: throw IllegalArgumentException("source not in reviewed water roster"))
    }
    override fun planTrial(request: TrialRequest): List<DriverStep> {
        val (p, selected) = source(request)
        return sourceSteps(p, selected)
    }
    override fun evaluateTrial(request: TrialRequest, observations: List<DriverObservation>): MeasurementVerdict {
        val (p, selected) = source(request)
        val problem = validateTerminalSequence(planTrial(request), observations)
        require(problem == null) { "manual water native terminal failure: $problem" }
        return checkSource(p, selected, observations)
    }
    override fun aggregate(parameters: JsonNode, requests: List<TrialRequest>, receipts: List<TrialReceipt>): MeasurementVerdict {
        val p = parse(parameters); require(requests.size == p.sources.size && requests.size == receipts.size)
        val expected = expandTrials(parameters, requests.first().inputCheckpointId)
        require(requests == expected && receipts.map { it.trialId } == requests.map { it.trialId } &&
            receipts.map { it.epochId }.distinct().size == receipts.size &&
            receipts.all { it.epochId.isNotBlank() && it.inputCheckpointId == requests.first().inputCheckpointId }) {
            "water source roster/epoch/checkpoint mismatch"
        }
        for ((request, receipt) in requests.zip(receipts)) {
            require(receipt.targetId == request.targetId && receipt.trialId == request.trialId &&
                receipt.inputCheckpointVerified && receipt.cleanupComplete) { "water source epoch/checkpoint/cleanup unverified" }
            val result = evaluateTrial(request, receipt.observations)
            require(receipt.state == result.state) { "stored manual water result differs from raw independent oracle" }
            if (result.state != "passed") return result
        }
        return MeasurementVerdict("passed", "every acquired manual source separately measured in restored checkpoint epochs")
    }
}
