package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.drivers.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.UUID

/** Behavior-only fixture grant reflected in an actual joined client; NEVER natural acquisition. */
internal object InventorySyncDriver : TrialCaseDriver {
    override val caseId = "client.inventory.sync"
    override val assertionIds = setOf("client.inventory.slot-sync")
    private data class Expected(val player: String, val dimension: String, val fixtureId: String,
        val slot: Int, val itemId: String, val count: Int, val stackSha256: String,
        val foodLevel: Int, val saturation: Double, val waitTicks: Int)
    private data class Slot(val item: String, val count: Int, val sha: String)

    private fun parse(node: JsonNode): Expected {
        require(node.isObject && node.fieldNames().asSequence().toSet() == setOf("player_uuid", "dimension",
            "fixture_id", "selected_slot", "item_id", "count", "expected_stack_sha256", "food_level", "saturation", "wait_ticks")) {
            "inventory synchronization requires exact independently reviewed fixture/slot transition"
        }
        fun integer(key: String, range: IntRange): Int = node.path(key).also {
            require(it.isIntegralNumber && it.canConvertToInt() && it.asInt() in range) { "invalid $key" }
        }.asInt()
        val player = node.path("player_uuid").asText(); val dimension = node.path("dimension").asText()
        val fixture = node.path("fixture_id").asText(); val item = node.path("item_id").asText()
        require(runCatching { UUID.fromString(player).toString() == player }.getOrDefault(false) &&
            resourcePattern.matches(dimension) && resourcePattern.matches(item) && item != "minecraft:air" &&
            fixture.matches(Regex("[A-Za-z0-9._-]{1,64}")) &&
            node.path("expected_stack_sha256").asText().matches(shaPattern) &&
            node.path("saturation").isNumber && node.path("saturation").asDouble().isFinite()) {
            "inventory sync player/item/fixture/item-stack SHA invalid"
        }
        val food = integer("food_level", 0..20); val sat = node.path("saturation").asDouble()
        require(sat in 0.0..food.toDouble())
        return Expected(player, dimension, fixture, integer("selected_slot", 0..8), item,
            integer("count", 1..64), node.path("expected_stack_sha256").asText(), food, sat,
            integer("wait_ticks", 20..200))
    }
    override fun fixtureProvisionedTargets(parameters: JsonNode): Set<String> = setOf(parse(parameters).itemId)
    override fun expandTrials(parameters: JsonNode, inputCheckpointId: String): List<TrialRequest> {
        val expected = parse(parameters)
        require(inputCheckpointId.matches(Regex("[a-z0-9][a-z0-9_.-]*")))
        return listOf(TrialRequest(caseId, "slot_sync", expected.itemId, inputCheckpointId,
            parameters.deepCopy(), assertionIds, setOf(expected.itemId)))
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val e = parse(parameters)
        val player = suiteMapper.createObjectNode().put("player_uuid", e.player)
        val grant = suiteMapper.createObjectNode().put("fixture_id", e.fixtureId).put("player_uuid", e.player)
            .put("item_id", e.itemId).put("count", e.count).put("hand", "main_hand")
            .put("food_level", e.foodLevel).put("saturation", e.saturation)
        fun action(name: String, id: String, args: ObjectNode, source: String = "server", timeout: Int = 200) =
            DriverStep(name, DriverAction(id, args, timeout, source))
        return listOf(action("server_before", "player.snapshot", player.deepCopy()),
            action("client_before", "client.snapshot", suiteMapper.createObjectNode(), "client"),
            action("grant", "fixture.begin", grant, timeout = 600),
            action("settle", "wait.ticks", suiteMapper.createObjectNode().put("ticks", e.waitTicks), timeout = e.waitTicks + 40),
            action("server_after", "player.snapshot", player.deepCopy()),
            action("client_after", "client.snapshot", suiteMapper.createObjectNode(), "client"))
    }
    override fun planTrial(request: TrialRequest): List<DriverStep> {
        val e = parse(request.parameters)
        require(request.caseId == caseId && request.trialId == "slot_sync" && request.targetId == e.itemId &&
            request.assertionIds == assertionIds && request.provisionedTargets == setOf(e.itemId))
        return plan(request.parameters)
    }

    private fun serverSlots(payload: JsonNode, e: Expected): Map<Int, Slot> {
        require(payload.path("uuid").asText() == e.player && payload.path("dimension").asText() == e.dimension &&
            payload.path("mode").asText() == "survival" && payload.path("synthetic_scale_participant").isBoolean &&
            !payload.path("synthetic_scale_participant").asBoolean() &&
            payload.path("selected_slot").isIntegralNumber && payload.path("selected_slot").canConvertToInt() &&
            payload.path("selected_slot").asInt(-1) == e.slot && payload.path("inventory").isArray)
        val rows = payload.path("inventory").map { row ->
            require(row.path("slot").isIntegralNumber && row.path("slot").canConvertToInt() && row.path("slot").asInt() in 0..40 &&
                row.path("item").isTextual && resourcePattern.matches(row.path("item").asText()) &&
                row.path("count").isIntegralNumber && row.path("count").canConvertToInt() && row.path("count").asInt() in 1..64 &&
                row.path("stack_sha256").asText().matches(shaPattern)) { "server slot identity incomplete" }
            row.path("slot").asInt() to Slot(row.path("item").asText(), row.path("count").asInt(), row.path("stack_sha256").asText())
        }
        require(rows.size == rows.map { it.first }.distinct().size) { "duplicate server inventory slot" }
        return rows.toMap()
    }
    private fun clientSlots(payload: JsonNode, e: Expected): Map<Int, Slot> {
        require(payload.path("player_uuid").asText() == e.player && payload.path("dimension").asText() == e.dimension &&
            payload.path("selected_slot").isIntegralNumber && payload.path("selected_slot").canConvertToInt() &&
            payload.path("selected_slot").asInt(-1) == e.slot && payload.path("inventory").isArray)
        val rows = payload.path("inventory")
        require(rows.size() == 41 && rows.all { it.path("slot").isIntegralNumber && it.path("slot").canConvertToInt() } &&
            rows.map { it.path("slot").asInt(-1) } == (0..40).toList()) {
            "actual client player inventory slot roster incomplete or reordered"
        }
        return rows.mapNotNull { row ->
            require(row.path("item_id").isTextual && row.path("count").isIntegralNumber && row.path("count").canConvertToInt())
            val item = row.path("item_id").asText(); val count = row.path("count").asInt(-1)
            if (item == "minecraft:air") {
                require(count == 0) { "client air slot has a count" }
                null
            } else {
                require(resourcePattern.matches(item) && count in 1..64 &&
                    row.path("stack_snbt_sha256").asText().matches(shaPattern)) { "client item slot has no exact stack identity" }
                row.path("slot").asInt() to Slot(item, count, row.path("stack_snbt_sha256").asText())
            }
        }.toMap()
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val e = parse(parameters)
        require(validateTerminalSequence(plan(parameters), observations) == null) { "server/client sync action sequence incomplete" }
        val serverBefore = observations[0].payload; val clientBefore = observations[1].payload
        val grant = observations[2].payload; val settle = observations[3].payload
        val serverAfter = observations[4].payload; val clientAfter = observations[5].payload
        val beforeServer = serverSlots(serverBefore, e); val beforeClient = clientSlots(clientBefore, e)
        val afterServer = serverSlots(serverAfter, e); val afterClient = clientSlots(clientAfter, e)
        require(listOf(serverBefore, serverAfter).all { it.path("held_count").isIntegralNumber && it.path("held_count").canConvertToInt() } &&
            listOf("count", "provisioned_count").all { grant.path(it).isIntegralNumber && grant.path(it).canConvertToInt() } &&
            settle.path("ticks_observed").isIntegralNumber && settle.path("ticks_observed").canConvertToInt() &&
            beforeServer == beforeClient && e.slot !in beforeServer &&
            serverBefore.path("held_item").asText() == "minecraft:air" && serverBefore.path("held_count").asInt(-1) == 0 &&
            grant.path("fixture_id").asText() == e.fixtureId && grant.path("player_uuid").asText() == e.player &&
            grant.path("owner_case_id").asText() == caseId && grant.path("hand").asText() == "main_hand" &&
            grant.path("item_id").asText() == e.itemId &&
            grant.path("count").asInt(-1) == e.count && grant.path("source_origin").asText() == "authored_fixture_not_natural" &&
            grant.path("provisioned_item").asText() == e.itemId && grant.path("provisioned_count").asInt(-1) == e.count &&
            grant.path("provisioned_targets").map { it.asText() } == listOf(e.itemId) &&
            grant.path("fixture_mutations").map { it.asText() }.contains("inventory") &&
            settle.path("ticks_observed").asInt(-1) == e.waitTicks &&
            afterServer.filterKeys { it != e.slot } == beforeServer &&
            afterClient.filterKeys { it != e.slot } == beforeClient &&
            afterServer == afterClient &&
            afterServer[e.slot] == Slot(e.itemId, e.count, e.stackSha256) &&
            serverAfter.path("held_item").asText() == e.itemId &&
            serverAfter.path("held_count").asInt(-1) == e.count &&
            serverAfter.path("held_stack_sha256").asText() == e.stackSha256) {
            "actual server/client inventories did not converge on the independently authored single-slot fixture transition"
        }
        return MeasurementVerdict("passed", "actual joined server/client selected-slot and full occupied-slot stacks synchronized after declared behavior-only fixture grant; no natural acquisition or GUI navigation proved")
    }
    override fun evaluateTrial(request: TrialRequest, observations: List<DriverObservation>): MeasurementVerdict {
        planTrial(request)
        return evaluate(request.parameters, observations)
    }
    override fun aggregate(parameters: JsonNode, requests: List<TrialRequest>, receipts: List<TrialReceipt>): MeasurementVerdict {
        require(requests.size == 1 && receipts.size == 1 && requests == expandTrials(parameters, requests.single().inputCheckpointId))
        val receipt = receipts.single(); val request = requests.single()
        require(receipt.trialId == request.trialId && receipt.targetId == request.targetId &&
            receipt.inputCheckpointId == request.inputCheckpointId && receipt.state == "passed" &&
            receipt.epochId.isNotBlank() && receipt.inputCheckpointVerified && receipt.cleanupComplete)
        return evaluateTrial(request, receipt.observations)
    }
}
