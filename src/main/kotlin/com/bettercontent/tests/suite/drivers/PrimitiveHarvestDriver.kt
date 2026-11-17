package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.checkpoints.ExternalSamplingDriver
import com.bettercontent.tests.suite.checkpoints.SamplingIntent
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.UUID

/** Two independently restored, naturally generated mining sites with ONE carried, never provisioned tool.
 * This does not implement the catalogue's all-families crafting/acquisition `primitive.tools.promise`.
 * The sealed tool's natural source and the pre-action terrain ledger must be verified outside this driver.
 */
internal object PrimitiveHarvestDriver : TrialCaseDriver, ExternalSamplingDriver {
    override val caseId = "primitive.harvest"
    override val assertionIds = setOf("primitive.harvest.native-dig", "primitive.harvest.tier-gating",
        "primitive.harvest.source-attributed-drops", "primitive.harvest.tool-wear-and-conservation")
    override val requireFreshTerrain = true
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private val hash = Regex("[0-9a-f]{64}")
    private val key = Regex("[a-z0-9][a-z0-9_.-]*")
    private data class Tool(val family: String, val id: String, val slot: Int, val count: Int,
        val sha: String, val canonicalSha: String, val acquisitionCase: String)
    private data class Target(val mode: String, val x: Int, val y: Int, val z: Int, val face: String,
        val before: JsonNode, val after: JsonNode, val damageBefore: Int, val damageAfter: Int,
        val pickups: Map<String, Int>, val expectedRequiresTool: Boolean,
        val expectedCanHarvest: Boolean, val forbidden: String?)
    private data class Plan(val player: String, val dimension: String, val tool: Tool,
        val targets: List<Target>, val timeoutTicks: Int)
    private fun fields(row: JsonNode, names: Set<String>) {
        require(row.isObject && row.fieldNames().asSequence().toSet() == names) { "unreviewed/missing harvest target fields" }
    }
    private fun text(row: JsonNode, name: String): String {
        val value = row.path(name); require(value.isTextual) { "invalid $name" }; return value.asText()
    }
    private fun integer(row: JsonNode, name: String, range: IntRange): Int {
        val value = row.path(name)
        require(value.isIntegralNumber && value.canConvertToInt() && value.asInt() in range) { "invalid $name" }
        return value.asInt()
    }
    private fun state(row: JsonNode, name: String): JsonNode {
        val value = row.path(name)
        fields(value, setOf("id", "properties"))
        require(resource.matches(text(value, "id")) && value.path("properties").isObject && value.path("properties").size() <= 32 &&
            value.path("properties").fields().asSequence().all { (k, v) -> key.matches(k) && v.isTextual && key.matches(v.asText()) }) {
            "invalid full block state"
        }
        return value.deepCopy()
    }
    private fun parse(node: JsonNode): Plan {
        fields(node, setOf("player_uuid", "dimension", "tool", "targets", "timeout_ticks"))
        val player = text(node, "player_uuid"); val dimension = text(node, "dimension")
        require(runCatching { UUID.fromString(player).toString() }.getOrNull() == player && resource.matches(dimension))
        val toolNode = node.path("tool")
        fields(toolNode, setOf("family", "item", "slot", "count", "source_stack_snbt_sha256",
            "source_stack_canonical_sha256", "source_stack_canonical_algorithm", "acquisition_case_id"))
        val family = text(toolNode, "family"); val item = text(toolNode, "item")
        val stackHash = text(toolNode, "source_stack_snbt_sha256")
        val canonical = text(toolNode, "source_stack_canonical_sha256")
        val acquisition = text(toolNode, "acquisition_case_id")
        require(key.matches(family) && resource.matches(item) && hash.matches(stackHash) && hash.matches(canonical) &&
            text(toolNode, "source_stack_canonical_algorithm") == "bc.playtest.stack-nbt.v1" && key.matches(acquisition))
        // Legacy SNBT is used only by native player.select_item; canonical source hash must be
        // independently bound to sealed playerdata and a real acquisition event before registration.
        val tool = Tool(family, item, integer(toolNode, "slot", 0..35), integer(toolNode, "count", 1..1),
            stackHash, canonical, acquisition)
        val rows = node.path("targets"); require(rows.isArray && rows.size() == 2) { "one correct and one wrong-tier target required" }
        val targets = rows.map { row ->
            val mode = text(row, "mode")
            fields(row, setOf("mode", "x", "y", "z", "face", "source_block_state", "result_block_state",
                "damage_before", "damage_after", "expected_pickups", "expected_requires_correct_tool_for_drops",
                "expected_can_harvest_with_held_tool") + if (mode == "wrong") setOf("forbidden_drop_item") else emptySet())
            require(mode in setOf("correct", "wrong"))
            val face = text(row, "face"); require(face in setOf("up", "down", "north", "south", "east", "west"))
            val before = state(row, "source_block_state"); val after = state(row, "result_block_state")
            require(before != after && before.path("id").asText() != after.path("id").asText() &&
                after.path("id").asText() == "minecraft:air" && after.path("properties").size() == 0) {
                "native dig must remove one exact solid target"
            }
            val damageBefore = integer(row, "damage_before", 0..100_000)
            val damageAfter = integer(row, "damage_after", damageBefore..damageBefore + 1)
            val requiresTool = row.path("expected_requires_correct_tool_for_drops")
            val canHarvest = row.path("expected_can_harvest_with_held_tool")
            require(requiresTool.isBoolean && requiresTool.asBoolean() && canHarvest.isBoolean &&
                canHarvest.asBoolean() == (mode == "correct")) {
                "reviewed eligibility expectation must distinguish required-tool correct/wrong tiers"
            }
            val pickupNode = row.path("expected_pickups"); require(pickupNode.isObject && pickupNode.size() <= 16)
            val pickups = pickupNode.fields().asSequence().associate { (id, count) ->
                require(resource.matches(id) && count.isIntegralNumber && count.canConvertToInt() && count.asInt() in 1..64)
                id to count.asInt()
            }
            val forbidden = if (mode == "wrong") text(row, "forbidden_drop_item").also {
                require(resource.matches(it) && pickups.isEmpty()) { "wrong-tier target must have zero pickup and one forbidden output" }
            } else null
            require(mode != "correct" || pickups.isNotEmpty()) { "correct tool requires positive source-pinned pickup" }
            Target(mode, integer(row, "x", -30_000_000..30_000_000), integer(row, "y", -2048..2048),
                integer(row, "z", -30_000_000..30_000_000), face, before, after, damageBefore, damageAfter,
                pickups, requiresTool.asBoolean(), canHarvest.asBoolean(), forbidden)
        }
        require(targets.map { it.mode } == listOf("correct", "wrong") &&
            targets.map { Triple(it.x, it.y, it.z) }.distinct().size == 2 &&
            targets.map { (it.x shr 4) to (it.z shr 4) }.distinct().size == 2) {
            "two distinct preclaimed chunk sites in correct/wrong order required"
        }
        return Plan(player, dimension, tool, targets, integer(node, "timeout_ticks", 20..72000))
    }
    override fun requiredAcquisitionCases(parameters: JsonNode): Map<String, String> = parse(parameters).tool.let {
        mapOf(it.id to it.acquisitionCase)
    }
    override fun samplingIntents(trial: TrialRequest): List<SamplingIntent> {
        val (p, target) = trial(trial)
        return listOf(SamplingIntent.Site(p.dimension, target.x shr 4, target.z shr 4))
    }
    private fun blockTarget(p: Plan, target: Target) = suiteMapper.createObjectNode()
        .put("dimension", p.dimension).put("x", target.x).put("y", target.y).put("z", target.z)
    private fun digTarget(p: Plan, target: Target) = blockTarget(p, target).put("player_uuid", p.player).put("face", target.face)
    private fun steps(p: Plan, target: Target): List<DriverStep> {
        val player = suiteMapper.createObjectNode().put("player_uuid", p.player)
        val selection = player.deepCopy().put("slot", p.tool.slot).put("expected_item", p.tool.id)
            .put("expected_count", p.tool.count).put("expected_stack_sha256", p.tool.sha)
            .put("expected_stack_canonical_sha256", p.tool.canonicalSha)
        return listOf(DriverStep("block-before", DriverAction("block.snapshot", blockTarget(p, target))),
            DriverStep("inventory-before", DriverAction("player.snapshot", player.deepCopy())),
            DriverStep("select", DriverAction("player.select_item", selection)),
            DriverStep("inventory-selected", DriverAction("player.snapshot", player.deepCopy())),
            DriverStep("dig", DriverAction("player.dig", digTarget(p, target), p.timeoutTicks)),
            DriverStep("block-after", DriverAction("block.snapshot", blockTarget(p, target))),
            DriverStep("inventory-after", DriverAction("player.snapshot", player.deepCopy())))
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val p = parse(parameters)
        return p.targets.flatMap { target -> steps(p, target).map { it.copy(name = "${target.mode}.${it.name}") } }
    }
    override fun expandTrials(parameters: JsonNode, inputCheckpointId: String): List<TrialRequest> {
        require(key.matches(inputCheckpointId)) { "unreviewed input checkpoint" }
        val p = parse(parameters)
        return p.targets.map { TrialRequest(caseId, it.mode, "${p.tool.family}.${it.mode}", inputCheckpointId,
            parameters.deepCopy(), assertionIds, emptySet()) }
    }
    private fun trial(request: TrialRequest): Pair<Plan, Target> {
        require(request.caseId == caseId && request.assertionIds == assertionIds && request.provisionedTargets.isEmpty() &&
            key.matches(request.inputCheckpointId)) { "fixture-provisioned or foreign harvest trial" }
        val p = parse(request.parameters)
        val target = p.targets.singleOrNull { it.mode == request.trialId && "${p.tool.family}.${it.mode}" == request.targetId }
            ?: throw IllegalArgumentException("missing reviewed harvest target")
        return p to target
    }
    override fun planTrial(request: TrialRequest): List<DriverStep> {
        val (p, target) = trial(request)
        return steps(p, target)
    }
    private fun inventory(snapshot: JsonNode): Map<Int, Triple<String, Int, String>> {
        val rows = snapshot.path("inventory")
        require(rows.isArray && rows.size() <= 41 && snapshot.path("selected_slot").isIntegralNumber &&
            snapshot.path("selected_slot").asInt() in 0..8 && snapshot.path("held_stack_sha256").isTextual) {
            "full hashed native inventory missing"
        }
        val pairs = rows.map { row ->
            val slot = integer(row, "slot", 0..40); val id = text(row, "item")
            val stackHash = text(row, "stack_sha256")
            require(resource.matches(id) && hash.matches(stackHash))
            slot to Triple(id, integer(row, "count", 1..64), stackHash)
        }
        require(pairs.map { it.first }.distinct().size == pairs.size) { "duplicate inventory slot" }
        return pairs.toMap()
    }
    private fun canonicalInventory(snapshot: JsonNode): Map<Int, String> {
        val rows = snapshot.path("inventory")
        require(rows.isArray && rows.size() <= 41 && rows.all {
            it.path("slot").isIntegralNumber && it.path("slot").asInt() in 0..40 &&
                it.path("stack_canonical_sha256").isTextual && hash.matches(it.path("stack_canonical_sha256").asText()) &&
                it.path("stack_canonical_algorithm").asText() == "bc.playtest.stack-nbt.v1"
        }) { "same-stack typed canonical inventory identity missing" }
        val entries = rows.map { it.path("slot").asInt() to it.path("stack_canonical_sha256").asText() }
        require(entries.map { it.first }.distinct().size == entries.size)
        return entries.toMap()
    }
    private fun check(p: Plan, target: Target, observations: List<DriverObservation>): MeasurementVerdict {
        require(observations.size == 7)
        val blockBefore = observations[0].payload; val before = observations[1].payload
        val select = observations[2].payload; val selected = observations[3].payload
        val dig = observations[4].payload; val blockAfter = observations[5].payload; val after = observations[6].payload
        require(listOf(blockBefore, blockAfter).all { it.path("dimension").isTextual &&
            it.path("x").isIntegralNumber && it.path("y").isIntegralNumber && it.path("z").isIntegralNumber &&
            it.path("block_state").isObject } && dig.path("requires_correct_tool_for_drops").isBoolean &&
            dig.path("harvest_check_source").isTextual && dig.path("harvest_check_count").isIntegralNumber &&
            dig.path("harvest_check_tick").isIntegralNumber && dig.path("harvest_scope_complete").isBoolean &&
            dig.path("pop_resource_scope_aborted").isBoolean && dig.path("fixture_mutations").isArray &&
            dig.path("drop_entities").isArray && dig.path("unscoped_item_join_observed").isBoolean &&
            select.path("fixture_mutations").isArray && select.path("inventory_conserved").isBoolean &&
            select.path("source_slot").isIntegralNumber && select.path("selected_after").isIntegralNumber &&
            select.path("item").isTextual && select.path("count").isIntegralNumber &&
            select.path("stack_sha256").isTextual && select.path("stack_canonical_sha256").isTextual &&
            select.path("stack_canonical_algorithm").isTextual && select.path("native_packet_handler").isTextual &&
            select.path("acquisition_origin").isTextual && select.path("client_acknowledgment").isTextual &&
            select.path("inventory_multiset_before_sha256").isTextual &&
            select.path("inventory_multiset_after_sha256").isTextual) { "missing independent harvest/tier/selection observation" }
        fun blockAt(row: JsonNode, expected: JsonNode) = row.path("dimension").asText() == p.dimension &&
            row.path("x").asInt() == target.x && row.path("y").asInt() == target.y &&
            row.path("z").asInt() == target.z && row.path("block_state") == expected
        if (!dig.path("harvest_scope_complete").asBoolean() || dig.path("pop_resource_scope_aborted").asBoolean() ||
            dig.path("harvest_check_source").asText() !=
                "ServerPlayerGameMode.destroyBlock/BlockState.canHarvestBlock(actual_return)" ||
            dig.path("harvest_check_count").asInt() != 1 ||
            dig.path("harvest_check_tick").asLong() !in dig.path("start_tick").asLong()..dig.path("observed_tick").asLong() ||
            !dig.path("can_harvest_with_held_tool").isBoolean) {
            return MeasurementVerdict("inconclusive", "native harvest/drop observation scope unavailable; no second policy query allowed")
        }
        val pre = inventory(before); val postSelection = inventory(selected); val postDig = inventory(after)
        val preCanonical = canonicalInventory(before); val selectedCanonical = canonicalInventory(selected)
        val afterCanonical = canonicalInventory(after)
        val identity = Triple(p.tool.id, p.tool.count, p.tool.sha)
        val beforeSlot = before.path("selected_slot").asInt()
        val selectedSlot = if (p.tool.slot <= 8) p.tool.slot else beforeSlot
        val expectedRoute = if (p.tool.slot <= 8) "ServerGamePacketListenerImpl.handleSetCarriedItem"
            else "ServerGamePacketListenerImpl.handleContainerClick_SWAP"
        val selection = blockAt(blockBefore, target.before) && blockAt(blockAfter, target.after) &&
            listOf(before, selected, after).all { it.path("uuid").asText() == p.player &&
                it.path("dimension").asText() == p.dimension } &&
            pre[p.tool.slot] == identity && preCanonical[p.tool.slot] == p.tool.canonicalSha &&
            selected.path("selected_slot").asInt() == selectedSlot &&
            postSelection[selectedSlot] == identity && selectedCanonical[selectedSlot] == p.tool.canonicalSha &&
            selected.path("held_stack_sha256").asText() == p.tool.sha &&
            selected.path("held_stack_canonical_sha256").asText() == p.tool.canonicalSha &&
            selected.path("held_stack_canonical_algorithm").asText() == "bc.playtest.stack-nbt.v1" &&
            postDig[selectedSlot]?.first == p.tool.id && postDig[selectedSlot]?.second == p.tool.count &&
            after.path("selected_slot").asInt() == selectedSlot &&
            after.path("held_stack_sha256").asText() == postDig[selectedSlot]?.third &&
            hash.matches(afterCanonical[selectedSlot] ?: "") &&
            after.path("held_stack_canonical_sha256").asText() == afterCanonical[selectedSlot] &&
            after.path("held_stack_canonical_algorithm").asText() == "bc.playtest.stack-nbt.v1" &&
            selectedCanonical.filterKeys { it != selectedSlot && (postSelection[it]?.first ?: "") !in target.pickups } ==
                afterCanonical.filterKeys { it != selectedSlot && (postDig[it]?.first ?: "") !in target.pickups } &&
            postSelection.filter { (slot, stack) -> slot != selectedSlot && stack.first !in target.pickups } ==
                postDig.filter { (slot, stack) -> slot != selectedSlot && stack.first !in target.pickups } &&
            before.path("held_item").isTextual && selected.path("held_item").asText() == p.tool.id &&
            pre.values.groupingBy { it.third }.eachCount() == postSelection.values.groupingBy { it.third }.eachCount() &&
            select.path("player_uuid").asText() == p.player && select.path("source_slot").asInt() == p.tool.slot &&
            select.path("selected_before").asInt() == beforeSlot && select.path("selected_after").asInt() == selectedSlot &&
            select.path("item").asText() == p.tool.id && select.path("count").asInt() == p.tool.count &&
            select.path("stack_sha256").asText() == p.tool.sha &&
            select.path("stack_canonical_sha256").asText() == p.tool.canonicalSha &&
            select.path("stack_canonical_algorithm").asText() == "bc.playtest.stack-nbt.v1" &&
            select.path("inventory_conserved").asBoolean() &&
            select.path("inventory_multiset_before_sha256").asText() == select.path("inventory_multiset_after_sha256").asText() &&
            select.path("native_packet_handler").asText() == expectedRoute &&
            select.path("client_acknowledgment").asText() == "unverified_without_client_observer" &&
            select.path("acquisition_origin").asText() == "preexisting_unattributed" &&
            select.path("fixture_mutations").size() == 0 &&
            dig.path("fixture_mutations").size() == 0 &&
            dig.path("held_count_before").isIntegralNumber && dig.path("held_count_before").asInt() == p.tool.count &&
            dig.path("held_count_after").isIntegralNumber && dig.path("held_count_after").asInt() == p.tool.count &&
            dig.path("requires_correct_tool_for_drops").asBoolean() == target.expectedRequiresTool &&
            dig.path("can_harvest_with_held_tool").asBoolean() == target.expectedCanHarvest
        if (!selection) return MeasurementVerdict("failed", "full source tool hash/normal selection or native tier/terrain mismatch")
        val nativeTarget: ObjectNode = digTarget(p, target).put("source_block", target.before.path("id").asText())
            .put("result_block", target.after.path("id").asText()).put("held_tool", p.tool.id)
            .put("damage_before", target.damageBefore).put("damage_after", target.damageAfter)
            .put("timeout_ticks", p.timeoutTicks).apply {
                set<JsonNode>("expected_pickups", suiteMapper.valueToTree(target.pickups))
            }
        val triplet = listOf(observations[3], observations[4], observations[6])
        val base = NativeDigControlDriver.evaluate(nativeTarget, triplet)
        if (base.state != "passed") return base
        val source = if (target.mode == "correct") SourceAttributedDigControlDriver.evaluate(nativeTarget, triplet) else {
            val noDrops = dig.path("drop_entities").size() == 0 && dig.path("pickup_events").size() == 0 &&
                !dig.path("unscoped_item_join_observed").asBoolean() &&
                dig.path("origin_evidence").asText() == "no_scoped_native_Block_popResource_drop" &&
                dig.path("pickup_correlation").asText() == "UUID_match_only; custom_bypass_paths_uncovered" &&
                target.forbidden != null && dig.path("pickup_events").none {
                    it.path("item").asText() == target.forbidden
                }
            MeasurementVerdict(if (noDrops) "passed" else "inconclusive",
                "wrong-tier native harvest predicate and zero observed source drops/pickups; bypass and delayed drops uncovered")
        }
        return source.copy(reason = "${target.mode}: ${source.reason}; source tool origin requires separate checkpoint ancestry proof")
    }
    override fun evaluateTrial(request: TrialRequest, observations: List<DriverObservation>): MeasurementVerdict {
        val (p, target) = trial(request)
        require(validateTerminalSequence(steps(p, target), observations) == null) { "harvest native terminal mismatch" }
        return check(p, target, observations)
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        parse(parameters)
        return MeasurementVerdict("inconclusive", "flat observations cannot prove two distinct restored epochs; use trial receipts and aggregate")
    }
    override fun aggregate(parameters: JsonNode, requests: List<TrialRequest>, receipts: List<TrialReceipt>): MeasurementVerdict {
        val p = parse(parameters); require(requests.size == 2 && receipts.size == 2)
        require(requests == expandTrials(parameters, requests.first().inputCheckpointId) &&
            requests.map { it.trialId } == p.targets.map { it.mode } && receipts.map { it.trialId } == requests.map { it.trialId } &&
            receipts.map { it.epochId }.distinct().size == 2 && receipts.all { it.epochId.isNotBlank() &&
                it.inputCheckpointId == requests.first().inputCheckpointId && it.inputCheckpointVerified && it.cleanupComplete }) {
            "harvest trials not isolated restorations of one sealed input or cleanup incomplete"
        }
        requests.zip(receipts).forEach { (request, receipt) ->
            require(receipt.targetId == request.targetId) { "foreign harvest trial" }
            val result = evaluateTrial(request, receipt.observations)
            require(receipt.state == result.state) { "stored harvest result differs from raw oracle" }
            if (result.state != "passed") return result
        }
        return MeasurementVerdict("passed", "one reviewed family, two independent natural-site tier trials; tool ancestry remains external prerequisite")
    }
}
