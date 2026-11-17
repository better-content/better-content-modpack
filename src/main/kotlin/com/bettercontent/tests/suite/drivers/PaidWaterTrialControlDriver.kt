package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode

/** One checkpoint-provided source, fixture-authored Thirst ONLY; never acquisition/purity-route proof. */
internal object PaidWaterTrialControlDriver : CaseDriver {
    override val caseId = "control.water.paid-sip-trial"
    private data class Target(val fixture: String, val food: Int, val saturation: Double, val water: JsonNode)
    private fun parse(node: JsonNode): Target {
        require(node.isObject && node.fieldNames().asSequence().toSet() == setOf("fixture_id", "food_level", "saturation", "water")) {
            "paid water control fields unknown/missing"
        }
        val fixture = node.path("fixture_id"); require(fixture.isTextual && Regex("[A-Za-z0-9._-]{1,64}").matches(fixture.asText()))
        val food = node.path("food_level"); require(food.isIntegralNumber && food.asInt() in 0..20)
        val saturation = node.path("saturation"); require(saturation.isNumber && saturation.asDouble().isFinite() && saturation.asDouble() in 0.0..food.asInt().toDouble())
        val water = node.path("water"); WaterSipControlDriver.plan(water) // closed typed water target validation
        return Target(fixture.asText(), food.asInt(), saturation.asDouble(), water)
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val t = parse(parameters); val steps = WaterSipControlDriver.plan(t.water)
        val player = t.water.path("player_uuid").asText(); val source = t.water.path("source_item").asText()
        val count = t.water.path("source_count").asInt(); val hand = t.water.path("hand").asText()
        val begin = suiteMapper.createObjectNode().put("fixture_id", t.fixture).put("player_uuid", player)
            .put("item_id", source).put("count", count).put("hand", hand)
            .put("food_level", t.food).put("saturation", t.saturation)
            .put("thirst_level", t.water.path("thirst_before").asInt())
            .put("quenched_level", t.water.path("quenched_before").asInt())
            .put("preserve_held", true)
        return listOf(DriverStep("fixture-begin", DriverAction("fixture.begin_water", begin))) + steps +
            DriverStep("fixture-restore", DriverAction("fixture.restore", suiteMapper.createObjectNode().put("fixture_id", t.fixture)))
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict =
        evaluateForOwner(parameters, observations, caseId)
    fun evaluateForOwner(parameters: JsonNode, observations: List<DriverObservation>, ownerCaseId: String): MeasurementVerdict {
        val t = parse(parameters); require(observations.size == 5)
        val begin = observations.first().payload; val restore = observations.last().payload
        require(begin.path("fixture_id").isTextual && begin.path("owner_case_id").isTextual &&
            begin.path("source_origin").isTextual && begin.path("provisioned_targets").isArray &&
            begin.path("fixture_mutations").isArray &&
            begin.path("count").isIntegralNumber && begin.path("provisioned_count").isIntegralNumber &&
            begin.path("player_uuid").isTextual && begin.path("hand").isTextual &&
            begin.path("thirst_after").isIntegralNumber && begin.path("quenched_after").isIntegralNumber &&
            restore.path("fixture_id").isTextual && restore.path("owner_case_id").isTextual &&
            restore.path("player_uuid").isTextual && restore.path("thirst_restored").isBoolean && restore.path("cleanup_complete").isBoolean &&
            restore.path("owned_residuals").isIntegralNumber && restore.path("baseline_restored").isBoolean) {
            "owned Thirst fixture begin/restore evidence missing"
        }
        val held = t.water.path("source_item").asText()
        val fixtureKnown = begin.path("fixture_id").asText() == t.fixture && restore.path("fixture_id").asText() == t.fixture &&
            begin.path("owner_case_id").asText() == ownerCaseId && restore.path("owner_case_id").asText() == ownerCaseId &&
            begin.path("item_id").asText() == held && begin.path("count").asInt() == t.water.path("source_count").asInt() &&
            begin.path("hand").asText() == t.water.path("hand").asText() &&
            begin.path("player_uuid").asText() == t.water.path("player_uuid").asText() &&
            restore.path("player_uuid").asText() == t.water.path("player_uuid").asText() &&
            begin.path("source_origin").asText() == "preexisting_unattributed" &&
            begin.path("provisioned_count").asInt() == 0 && begin.path("provisioned_targets").size() == 0 &&
            begin.path("fixture_mutations").map { it.asText() }.toSet() == setOf("food", "thirst") && begin.path("thirst_after").asInt() == t.water.path("thirst_before").asInt() &&
            begin.path("quenched_after").asInt() == t.water.path("quenched_before").asInt() &&
            restore.path("thirst_restored").asBoolean() && restore.path("cleanup_complete").asBoolean() &&
            restore.path("owned_residuals").asInt() == 0 && !restore.path("baseline_restored").asBoolean()
        val measured = WaterSipControlDriver.evaluate(t.water, observations.drop(1).take(3))
        return if (!fixtureKnown) MeasurementVerdict("failed", "owned Thirst fixture or restoration mismatch")
            else measured.copy(reason = measured.reason + "; held source preexisting, independent acquisition not established")
    }
}
