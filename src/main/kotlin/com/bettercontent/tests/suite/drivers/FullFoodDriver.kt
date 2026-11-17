package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.FoodCandidate
import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.selectFoods
import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.client.INPUT_RUNTIME_MARKER
import com.bettercontent.tests.suite.client.validateAuthenticHeldInput
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.UUID
import kotlin.math.abs

/** All selected ordinary item foods, not placed foods, selected from reviewed candidate-bound roster. */
internal object FullFoodDriver : TrialCaseDriver {
    override val caseId = "survival.food"
    override val assertionIds = setOf("survival.food.sampled-intake", "survival.food.hunger-saturation",
        "survival.food.effects", "survival.food.container-remainders")
    private data class Effect(val id: String, val amplifier: Int, val minDuration: Int, val maxDuration: Int)
    private data class Food(val id: String, val fixture: String, val hand: String, val count: Int,
        val foodBefore: Int, val foodAfter: Int, val saturationBefore: Double, val saturationAfter: Double,
        val itemAfter: String, val countAfter: Int, val inventoryDeltas: Map<String, Int>, val effects: List<Effect>,
        val acquisitionCase: String, val observerId: String, val useKind: String)
    private data class Plan(val player: String, val dimension: String, val profile: String, val foods: List<Food>)
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private val key = Regex("[a-z0-9][a-z0-9_.-]*")
    private fun fields(node: JsonNode, expected: Set<String>) {
        require(node.isObject && node.fieldNames().asSequence().toSet() == expected) { "unexpected/missing food manifest field" }
    }
    private fun string(node: JsonNode, name: String): String {
        val value = node.get(name); require(value != null && value.isTextual) { "invalid $name" }; return value.asText()
    }
    private fun integer(node: JsonNode, name: String, range: IntRange): Int {
        val value = node.get(name)
        require(value != null && value.isIntegralNumber && value.canConvertToInt() && value.asInt() in range) { "invalid $name" }
        return value.asInt()
    }
    private fun decimal(node: JsonNode, name: String): Double {
        val value = node.get(name)
        require(value != null && value.isNumber && value.asDouble().isFinite() && value.asDouble() in 0.0..20.0) { "invalid $name" }
        return value.asDouble()
    }
    private fun array(node: JsonNode, name: String): List<JsonNode> {
        val value = node.get(name); require(value != null && value.isArray && value.size() <= 512) { "invalid $name roster" }
        return value.toList()
    }
    private fun parse(input: JsonNode): Plan {
        fields(input, setOf("player_uuid", "dimension", "input_profile", "candidates", "mandatory", "targets"))
        val player = string(input, "player_uuid"); val dimension = string(input, "dimension")
        val profile = string(input, "input_profile")
        require(profile in setOf("1280x720@2", "1920x1080@3")) { "unreviewed actual client input profile" }
        require(runCatching { UUID.fromString(player).toString() }.getOrNull() == player && resource.matches(dimension)) { "invalid player/dimension" }
        val candidates = array(input, "candidates").map { row ->
            fields(row, setOf("id", "category")); val id = string(row, "id"); val category = string(row, "category")
            require(resource.matches(id) && key.matches(category)) { "unclassified food" }; FoodCandidate(id, category)
        }
        val mandatory = array(input, "mandatory").map { row -> require(row.isTextual); row.asText() }
        val selection = selectFoods(candidates, mandatory, 424242L)
        require(selection.sampled.size == 10 && mandatory.isNotEmpty()) { "ten seeded foods plus mandatory starter IDs required" }
        val expected = (selection.sampled + selection.mandatory.filterNot { it in selection.sampled }).toList()
        val foods = array(input, "targets").map { row ->
            fields(row, setOf("id", "fixture_id", "hand", "count", "food_before", "food_after", "saturation_before",
                "saturation_after", "item_after", "count_after", "inventory_deltas", "effects", "acquisition_case_id",
                "observer_id", "use_kind"))
            val id = string(row, "id"); val fixture = string(row, "fixture_id"); val hand = string(row, "hand")
            val after = string(row, "item_after"); val acquisition = string(row, "acquisition_case_id")
            val observerId = string(row, "observer_id"); val useKind = string(row, "use_kind")
            require(resource.matches(id) && resource.matches(after) && hand == "main_hand" &&
                Regex("[A-Za-z0-9._-]{1,64}").matches(fixture) && key.matches(acquisition) &&
                runCatching { UUID.fromString(observerId).toString() == observerId }.getOrDefault(false) &&
                useKind in setOf("eat", "drink")) { "unsupported item food target" }
            val count = integer(row, "count", 1..64); val countAfter = integer(row, "count_after", 0..64)
            val foodBefore = integer(row, "food_before", 0..19); val foodAfter = integer(row, "food_after", 1..20)
            val satBefore = decimal(row, "saturation_before"); val satAfter = decimal(row, "saturation_after")
            require(satBefore <= foodBefore && satAfter <= foodAfter && foodAfter > foodBefore) { "invalid independent hunger/saturation expectation" }
            require((after == id && countAfter == count - 1) ||
                (after != id && count == 1 && countAfter in 0..1)) { "item/remainder must debit exactly one food" }
            val deltas = row.path("inventory_deltas")
            require(deltas.isObject) { "explicit inventory delta map required" }
            val changes = deltas.fields().asSequence().associate { (item, n) ->
                require(resource.matches(item) && n.isIntegralNumber && n.canConvertToInt() && n.asInt() in -64..64 && n.asInt() != 0) {
                    "invalid inventory delta"
                }
                item to n.asInt()
            }
            require(changes[id] == -1 && changes.size <= 8) { "food debit and bounded remainder expectation required" }
            val effects = array(row, "effects").map { effect ->
                fields(effect, setOf("id", "amplifier", "min_duration", "max_duration"))
                val effectId = string(effect, "id")
                require(resource.matches(effectId)) { "effect ID not installed-resource spelling" }
                val min = integer(effect, "min_duration", 1..2_000_000); val max = integer(effect, "max_duration", 1..2_000_000)
                require(min <= max) { "effect duration interval inverted" }
                Effect(effectId, integer(effect, "amplifier", 0..255), min, max)
            }
            require(effects.map { it.id }.distinct().size == effects.size) { "duplicate effect" }
            Food(id, fixture, hand, count, foodBefore, foodAfter, satBefore, satAfter, after, countAfter, changes, effects,
                acquisition, observerId, useKind)
        }
        require(foods.map { it.id } == expected && foods.map { it.fixture }.distinct().size == foods.size &&
            foods.map { it.observerId }.distinct().size == foods.size) {
            "reviewed target rows must exactly match deterministic ten-plus-mandatory selection"
        }
        return Plan(player, dimension, profile, foods)
    }
    override fun fixtureProvisionedTargets(parameters: JsonNode): Set<String> = parse(parameters).foods.map { it.id }.toSet()
    override fun requiredAcquisitionCases(parameters: JsonNode): Map<String, String> = parse(parameters).foods.associate { it.id to it.acquisitionCase }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val p = parse(parameters)
        val result = mutableListOf<DriverStep>()
        for (food in p.foods) {
            val fixture = suiteMapper.createObjectNode().put("player_uuid", p.player).put("fixture_id", food.fixture)
            result += DriverStep("${food.fixture}.begin", DriverAction("fixture.begin", fixture.deepCopy().apply {
                put("item_id", food.id); put("count", food.count); put("hand", food.hand)
                put("food_level", food.foodBefore); put("saturation", food.saturationBefore)
            }))
            result += DriverStep("${food.fixture}.before", DriverAction("player.snapshot", suiteMapper.createObjectNode().put("player_uuid", p.player)))
            result += DriverStep("${food.fixture}.arm", DriverAction("player.observe_item_use.arm", suiteMapper.createObjectNode()
                .put("player_uuid", "@observed-player-uuid").put("dimension", p.dimension)
                .put("observer_id", food.observerId).put("expected_item_id", food.id)
                .put("expected_provider", "living_entity_use_item").put("expected_kind", food.useKind)
                .put("hand", food.hand).put("deadline_ticks", 7200)
                .put("witness_context", "@controller-witness-context-v1"), 200))
            val held = suiteMapper.createObjectNode().put("hold_id", food.fixture)
                .put("player_uuid", "@observed-player-uuid").put("dimension", p.dimension)
                .put("input_runtime_sha256", INPUT_RUNTIME_MARKER)
            result += DriverStep("${food.fixture}.hold", DriverAction("client.input_hold", held.deepCopy().apply {
                put("profile", p.profile); put("kind", "use"); put("max_ticks", 120)
            }, 140, "client"))
            result += DriverStep("${food.fixture}.release", DriverAction("client.input_release", held.deepCopy(), 200, "client"))
            result += DriverStep("${food.fixture}.status", DriverAction("client.input_status", held.deepCopy(), 200, "client"))
            result += DriverStep("${food.fixture}.consume", DriverAction("player.observe_item_use.await", suiteMapper.createObjectNode()
                .put("player_uuid", "@observed-player-uuid").put("dimension", p.dimension)
                .put("observer_id", food.observerId).put("arm_request_sequence", "@arm-request-sequence")
                .put("arm_action_ordinal", "@arm-action-ordinal")
                .put("witness_context", "@controller-witness-context-v1"), 7200))
            result += DriverStep("${food.fixture}.after", DriverAction("player.snapshot", suiteMapper.createObjectNode().put("player_uuid", p.player)))
            result += DriverStep("${food.fixture}.restore", DriverAction("fixture.restore", suiteMapper.createObjectNode().put("fixture_id", food.fixture)))
        }
        return result
    }
    private fun amounts(snapshot: JsonNode): Map<String, Int> {
        val inventory = snapshot.path("inventory")
        require(inventory.isArray) { "full inventory snapshot missing" }
        val slots = inventory.toList().map { row ->
            require(row.path("item").isTextual && resource.matches(row.path("item").asText()) &&
                row.path("count").isIntegralNumber && row.path("count").asInt() in 1..64 &&
                row.path("slot").isIntegralNumber && row.path("slot").asInt() in 0..40) { "invalid inventory row" }
            row.path("slot").asInt() to (row.path("item").asText() to row.path("count").asInt())
        }
        require(slots.map { it.first }.distinct().size == slots.size) { "duplicate inventory slot" }
        return slots.groupBy({ it.second.first }, { it.second.second }).mapValues { (_, counts) -> counts.sum() }
    }
    private fun evaluateFood(p: Plan, food: Food, observations: List<DriverObservation>): MeasurementVerdict {
        require(observations.size == 9) { "incomplete physical single food trial: ${food.id}" }
            val begin = observations[0].payload; val before = observations[1].payload
            val arm = observations[2].payload; val hold = observations[3].payload
            val release = observations[4].payload; val status = observations[5].payload
            val action = observations[6].payload; val after = observations[7].payload
            val restore = observations[8].payload
            require(listOf(before, after).all { it.path("uuid").isTextual && it.path("dimension").isTextual &&
                it.path("food_level").isIntegralNumber && it.path("saturation").isNumber && it.path("effects").isArray &&
                it.path("held_item").isTextual && it.path("held_count").isIntegralNumber } &&
                arm.path("armed_before_input").isBoolean && arm.path("armed_before_input").asBoolean() &&
                arm.path("observer_id").asText() == food.observerId &&
                action.path("schema").asText() == "bc.playtest_passive_item_use.v1" &&
                action.path("observer_id").asText() == food.observerId &&
                action.path("actor_uuid").asText() == p.player && action.path("dimension").asText() == p.dimension &&
                action.path("narrow_event_chain_complete").isBoolean && action.path("narrow_event_chain_complete").asBoolean() &&
                action.path("packet_transport_origin_proven_by_native").isBoolean &&
                !action.path("packet_transport_origin_proven_by_native").asBoolean() &&
                action.path("packet_count").asInt(-1) == 1 && action.path("start_count").asInt(-1) == 1 &&
                action.path("finish_count").asInt(-1) == 1 &&
                action.path("expected_provider").asText() == "living_entity_use_item" &&
                action.path("expected_kind").asText() == food.useKind &&
                action.path("expected_item_id").asText() == food.id && action.path("hand").asText() == food.hand &&
                arm.path("fixture_id").asText() == food.fixture && action.path("fixture_id").asText() == food.fixture &&
                arm.path("acquisition_origin").asText() == "authored_fixture_not_natural" &&
                action.path("acquisition_origin").asText() == "authored_fixture_not_natural" &&
                arm.path("natural_acquisition_proven_by_native").isBoolean &&
                !arm.path("natural_acquisition_proven_by_native").asBoolean() &&
                action.path("natural_acquisition_proven_by_native").isBoolean &&
                !action.path("natural_acquisition_proven_by_native").asBoolean() &&
                arm.path("arm_request_sequence").isIntegralNumber &&
                arm.path("arm_action_ordinal").isIntegralNumber &&
                action.path("arm_request_sequence") == arm.path("arm_request_sequence") &&
                action.path("arm_action_ordinal") == arm.path("arm_action_ordinal") &&
                validateAuthenticHeldInput(hold, release, status, p.player, p.dimension, p.profile,
                    food.fixture, food.id, hold.path("input_runtime_sha256").asText())) {
                "incomplete OS-input/server passive use source observation: ${food.id}"
            }
            require(begin.path("fixture_id").asText() == food.fixture && begin.path("owner_case_id").asText() == caseId &&
                begin.path("provisioned_item").asText() == food.id && begin.path("provisioned_count").asInt(-1) == food.count &&
                begin.path("food_after").asInt(-1) == food.foodBefore && begin.path("saturation_after").isNumber &&
                abs(begin.path("saturation_after").asDouble() - food.saturationBefore) <= .001) {
                "owned fixture receipt mismatch: ${food.id}"
            }
            require(restore.path("fixture_id").asText() == food.fixture && restore.path("owner_case_id").asText() == caseId &&
                restore.path("player_uuid").asText() == p.player && restore.path("owned_residuals").isIntegralNumber &&
                restore.path("owned_residuals").asInt() == 0 && restore.path("cleanup_complete").asBoolean(false) &&
                restore.path("vanilla_restored").asBoolean(false) &&
                restore.path("inventory_mismatched_slots").isIntegralNumber && restore.path("inventory_mismatched_slots").asInt() == 0 &&
                restore.path("food_restored").asBoolean(false) && restore.path("effects_restored").asBoolean(false) &&
                restore.path("selected_slot_restored").asBoolean(false) && restore.path("restored_inventory_slots").isIntegralNumber &&
                restore.path("restored_inventory_slots").asInt() == 41 &&
                restore.path("food_after_restore").isIntegralNumber && begin.path("food_before").isIntegralNumber &&
                restore.path("food_after_restore").asInt() == begin.path("food_before").asInt() &&
                restore.path("saturation_after_restore").isNumber && begin.path("saturation_before").isNumber &&
                abs(restore.path("saturation_after_restore").asDouble() - begin.path("saturation_before").asDouble()) <= .001 &&
                restore.path("acquisition_origin").asText() == "authored_fixture_not_natural" &&
                begin.path("acquisition_origin").asText() == "authored_fixture_not_natural") {
                "fixture restoration/residual receipt missing: ${food.id}"
            }
            val identities = listOf(before, after).all { it.path("uuid").asText() == p.player && it.path("dimension").asText() == p.dimension }
            val changes = (amounts(after).keys + amounts(before).keys).associateWith { item ->
                amounts(after).getOrDefault(item, 0) - amounts(before).getOrDefault(item, 0)
            }.filterValues { it != 0 }
            val expectedEffects = food.effects.associateBy { it.id }
            val effectRows = after.path("effects").toList()
            val effectsValid = before.path("effects").size() == 0 && effectRows.map { it.path("id").asText() }.toSet() == expectedEffects.keys &&
                effectRows.size == expectedEffects.size && effectRows.all { effect ->
                    val expected = expectedEffects[effect.path("id").asText()]
                    expected != null && effect.path("amplifier").isIntegralNumber && effect.path("duration").isIntegralNumber &&
                        effect.path("amplifier").asInt() == expected.amplifier && effect.path("duration").asInt() in expected.minDuration..expected.maxDuration
                }
            val armStack = action.path("item_before_arm"); val afterStack = action.path("item_after_applied")
            val atStart = action.path("item_at_start"); val atFinish = action.path("item_at_finish")
            val beforePhysiology = action.path("physiology_before"); val afterPhysiology = action.path("physiology_after")
            val stackBefore = armStack.path("item_id").asText() == food.id && armStack.path("count").asInt(-1) == food.count &&
                armStack.path("content_algorithm").asText() == "bc.playtest.stack-content.v1" &&
                armStack.path("canonical_algorithm").asText() == "bc.playtest.stack-nbt.v1" &&
                Regex("[0-9a-f]{64}").matches(armStack.path("content_sha256").asText()) &&
                Regex("[0-9a-f]{64}").matches(armStack.path("canonical_sha256").asText()) &&
                listOf(atStart, atFinish).all {
                    it.path("content_sha256") == armStack.path("content_sha256") &&
                    it.path("item_id").asText() == food.id && it.path("count").asInt(-1) == food.count
                }
            val stackAfter = if (food.countAfter == 0 && food.itemAfter == "minecraft:air") afterStack.isNull else
                afterStack.path("item_id").asText() == food.itemAfter && afterStack.path("count").asInt(-1) == food.countAfter
            val matched = identities && stackBefore && stackAfter &&
                beforePhysiology.path("thirst_supported").asBoolean(false) &&
                afterPhysiology.path("thirst_supported").asBoolean(false) &&
                beforePhysiology.path("thirst").isIntegralNumber && afterPhysiology.path("thirst") == beforePhysiology.path("thirst") &&
                beforePhysiology.path("quenched").isIntegralNumber && afterPhysiology.path("quenched") == beforePhysiology.path("quenched") &&
                action.path("arm_tick").isIntegralNumber && action.path("packet_tick").isIntegralNumber &&
                action.path("start_event_tick").isIntegralNumber && action.path("finish_event_tick").isIntegralNumber &&
                action.path("final_applied_state_tick").isIntegralNumber &&
                action.path("arm_tick").asLong() <= action.path("packet_tick").asLong() &&
                action.path("packet_tick").asLong() <= action.path("start_event_tick").asLong() &&
                action.path("start_event_tick").asLong() <= action.path("finish_event_tick").asLong() &&
                action.path("finish_event_tick").asLong() <= action.path("final_applied_state_tick").asLong() &&
                beforePhysiology.path("food_level").asInt(-1) == food.foodBefore &&
                afterPhysiology.path("food_level").asInt(-1) == food.foodAfter &&
                before.path("food_level").asInt() == food.foodBefore && after.path("food_level").asInt() == food.foodAfter &&
                beforePhysiology.path("saturation").isNumber && afterPhysiology.path("saturation").isNumber &&
                abs(beforePhysiology.path("saturation").asDouble() - food.saturationBefore) <= .001 &&
                abs(afterPhysiology.path("saturation").asDouble() - food.saturationAfter) <= .001 &&
                abs(before.path("saturation").asDouble() - food.saturationBefore) <= .001 &&
                abs(after.path("saturation").asDouble() - food.saturationAfter) <= .001 &&
                before.path("held_item").asText() == food.id && before.path("held_count").asInt() == food.count &&
                after.path("held_item").asText() == food.itemAfter && after.path("held_count").asInt() == food.countAfter &&
                action.path("packet_source").asText() == "ServerGamePacketListenerImpl.handleUseItem/server_thread_HEAD" &&
                action.path("start_source").asText() == "LivingEntityUseItemEvent.Start/MONITOR" &&
                action.path("finish_source").asText() == "LivingEntityUseItemEvent.Finish/MONITOR" &&
                action.path("final_source").asText() == "ServerTickEvent.END/actual_player_state" &&
                changes == food.inventoryDeltas && effectsValid
            if (!matched) return MeasurementVerdict("failed", "independent food/paid item/effect/remainder mismatch: ${food.id}",
                mapOf("observed_inventory_deltas" to changes, "observed_hunger" to after.path("food_level").asInt()))
        return MeasurementVerdict("passed", "OS-held first-use-end release plus passive server packet/finish/final food trial; no natural acquisition proof")
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val p = parse(parameters)
        require(observations.size == p.foods.size * 9) { "incomplete full physical food roster" }
        for ((index, food) in p.foods.withIndex()) {
            val result = evaluateFood(p, food, observations.drop(index * 9).take(9))
            if (result.state != "passed") return result
        }
        return MeasurementVerdict("passed", "full ten-plus-mandatory item food roster observed; fixtures do not prove acquisition")
    }
    override fun expandTrials(parameters: JsonNode, inputCheckpointId: String): List<TrialRequest> {
        require(Regex("[a-z0-9][a-z0-9_.-]*").matches(inputCheckpointId)) { "invalid sealed checkpoint ID" }
        val p = parse(parameters)
        return p.foods.map { food -> TrialRequest(caseId, food.fixture, food.id, inputCheckpointId,
            parameters.deepCopy(), assertionIds, setOf(food.id)) }
    }
    private fun trialFood(request: TrialRequest): Pair<Plan, Food> {
        require(request.caseId == caseId && request.assertionIds == assertionIds &&
            Regex("[a-z0-9][a-z0-9_.-]*").matches(request.inputCheckpointId) &&
            request.provisionedTargets == setOf(request.targetId)) { "food trial contract mismatch" }
        val p = parse(request.parameters)
        val food = p.foods.singleOrNull { it.id == request.targetId && it.fixture == request.trialId }
            ?: throw IllegalArgumentException("food trial not in reviewed roster")
        return p to food
    }
    override fun planTrial(request: TrialRequest): List<DriverStep> {
        val (_, food) = trialFood(request)
        return plan(request.parameters).filter { it.name.startsWith("${food.fixture}.") }.also {
            require(it.size == 9) { "one full owned fixture trial with physical input and passive observer required" }
        }
    }
    override fun evaluateTrial(request: TrialRequest, observations: List<DriverObservation>): MeasurementVerdict {
        val (p, food) = trialFood(request)
        val problem = validateTerminalSequence(planTrial(request), observations)
        require(problem == null) { "food trial native terminal failure: $problem" }
        return evaluateFood(p, food, observations)
    }
    override fun aggregate(parameters: JsonNode, requests: List<TrialRequest>, receipts: List<TrialReceipt>): MeasurementVerdict {
        val p = parse(parameters)
        require(requests.size == p.foods.size && receipts.size == requests.size) { "incomplete ten-plus-mandatory trial set" }
        val expected = expandTrials(parameters, requests.first().inputCheckpointId)
        require(requests.map { listOf(it.caseId, it.trialId, it.targetId, it.inputCheckpointId) } ==
            expected.map { listOf(it.caseId, it.trialId, it.targetId, it.inputCheckpointId) } &&
            requests.all { it.assertionIds == assertionIds && it.provisionedTargets == setOf(it.targetId) && it.parameters == parameters } &&
            receipts.map { it.trialId } == requests.map { it.trialId } &&
            receipts.map { it.epochId }.distinct().size == receipts.size &&
            receipts.all { it.epochId.isNotBlank() && it.inputCheckpointId == requests.first().inputCheckpointId }) {
            "trial identity/epoch/input-checkpoint mismatch"
        }
        for ((request, receipt) in requests.zip(receipts)) {
            require(receipt.targetId == request.targetId && receipt.trialId == request.trialId &&
                receipt.inputCheckpointVerified && receipt.cleanupComplete) {
                "trial target/sealed input verification/cleanup mismatch"
            }
            val independentlyChecked = evaluateTrial(request, receipt.observations)
            require(receipt.state == independentlyChecked.state) { "stored trial verdict differs from independent oracle" }
            if (independentlyChecked.state != "passed") return independentlyChecked
        }
        return MeasurementVerdict("passed", "exact ten seeded-plus-mandatory independent item trials from sealed checkpoint")
    }
}
