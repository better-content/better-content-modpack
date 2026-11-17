package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

/** One manual native use with observed Thirst meter and paid container/fluid changes, not whole water roster. */
internal object WaterSipControlDriver : CaseDriver {
    override val caseId = "control.water.manual-sip"
    private data class Fluid(val tank: Int, val id: String, val amount: Int, val purity: Int)
    private data class Target(val player: String, val hand: String, val beforeItem: String, val afterItem: String,
        val beforeCount: Int, val afterCount: Int, val beforePurity: Int, val afterPurity: Int?,
        val thirstBefore: Int, val thirstAfter: Int, val quenchedBefore: Int, val quenchedAfter: Int,
        val fluidBefore: List<Fluid>, val fluidAfter: List<Fluid>)
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private fun parse(node: JsonNode): Target {
        val fields = setOf("player_uuid", "hand", "source_item", "result_item", "source_count", "result_count",
            "source_purity", "result_purity", "thirst_before", "thirst_after", "quenched_before", "quenched_after",
            "fluids_before", "fluids_after")
        require(node.isObject && node.fieldNames().asSequence().toSet() == fields) { "water sip target fields incomplete/unknown" }
        fun text(key: String): String { val v = node.get(key); require(v != null && v.isTextual); return v.asText() }
        fun number(key: String, valid: IntRange): Int {
            val v = node.get(key); require(v != null && v.isIntegralNumber && v.canConvertToInt() && v.asInt() in valid) { "invalid $key" }
            return v.asInt()
        }
        val player = text("player_uuid"); require(runCatching { UUID.fromString(player).toString() }.getOrNull() == player)
        val hand = text("hand"); require(hand in setOf("main_hand", "off_hand"))
        val before = text("source_item"); val after = text("result_item")
        require(resource.matches(before) && before != "minecraft:air" && resource.matches(after))
        val beforeCount = number("source_count", 1..64); val afterCount = number("result_count", 0..64)
        require((before == after && afterCount in (beforeCount - 1)..beforeCount) ||
            (before != after && beforeCount == 1 && afterCount in 0..1)) {
            "single paid source debit/remainder must be explicit"
        }
        val thirstBefore = number("thirst_before", 0..19); val thirstAfter = number("thirst_after", 1..20)
        val quenchedBefore = number("quenched_before", 0..20); val quenchedAfter = number("quenched_after", 0..20)
        require(thirstAfter > thirstBefore && quenchedAfter >= quenchedBefore && quenchedAfter <= thirstAfter) { "expected native Thirst gain, not vanilla food" }
        val resultPurity = node.get("result_purity")
        require(resultPurity != null && (resultPurity.isNull || resultPurity.isIntegralNumber && resultPurity.asInt() in 0..3))
        fun fluids(key: String): List<Fluid> {
            val rows = node.get(key); require(rows != null && rows.isArray && rows.size() <= 8) { "invalid $key" }
            return rows.map { fluid ->
                require(fluid.isObject && fluid.fieldNames().asSequence().toSet() == setOf("tank", "fluid", "amount", "purity"))
                val id = fluid.path("fluid").asText(""); require(resource.matches(id))
                require(fluid.path("tank").isIntegralNumber && fluid.path("tank").asInt() in 0..15 &&
                    fluid.path("amount").isIntegralNumber && fluid.path("amount").asInt() in 1..1_000_000 &&
                    fluid.path("purity").isIntegralNumber && fluid.path("purity").asInt() in 0..3)
                Fluid(fluid.path("tank").asInt(), id, fluid.path("amount").asInt(), fluid.path("purity").asInt())
            }
        }
        val sourceFluids = fluids("fluids_before"); val resultFluids = fluids("fluids_after")
        require(sourceFluids.map { it.tank }.distinct().size == sourceFluids.size &&
            resultFluids.map { it.tank }.distinct().size == resultFluids.size) { "duplicate fluid tanks" }
        if (before == after && beforeCount == afterCount) {
            require(sourceFluids.isNotEmpty() && sourceFluids.map { it.tank to it.id } == resultFluids.map { it.tank to it.id } &&
                sourceFluids.zip(resultFluids).all { (a, b) -> b.amount <= a.amount } &&
                sourceFluids.zip(resultFluids).any { (a, b) -> b.amount < a.amount }) {
                "unchanged vessel count requires real finite fluid debit"
            }
        }
        return Target(player, hand, before, after, beforeCount, afterCount, number("source_purity", 0..3),
            resultPurity.takeUnless { it.isNull }?.asInt(), thirstBefore, thirstAfter, quenchedBefore, quenchedAfter, sourceFluids, resultFluids)
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val t = parse(parameters)
        fun sample() = suiteMapper.createObjectNode().put("player_uuid", t.player).put("hand", t.hand)
        return listOf(DriverStep("before", DriverAction("player.water_snapshot", sample())),
            DriverStep("sip", DriverAction("player.use_item", sample(), 72000)),
            DriverStep("after", DriverAction("player.water_snapshot", sample())))
    }
    private fun observed(row: JsonNode, target: Target, before: Boolean): Boolean {
        require(row.path("player_uuid").isTextual && row.path("hand").isTextual && row.path("thirst").isIntegralNumber &&
            row.path("quenched").isIntegralNumber && row.path("held_item").isTextual && row.path("held_count").isIntegralNumber &&
            row.path("fluids").isArray && row.has("item_purity") && row.path("capability_source").isTextual &&
            row.path("purity_source").isTextual) { "Thirst/purity/volume source observation missing" }
        val item = if (before) target.beforeItem else target.afterItem
        val count = if (before) target.beforeCount else target.afterCount
        val purity = if (before) target.beforePurity else target.afterPurity
        val expectedFluids = if (before) target.fluidBefore else target.fluidAfter
        val actual = row.path("fluids").map {
            require(it.path("tank").isIntegralNumber && it.path("fluid").isTextual &&
                it.path("amount").isIntegralNumber && it.path("purity").isIntegralNumber) { "fluid tank data missing" }
            Fluid(it.path("tank").asInt(), it.path("fluid").asText(), it.path("amount").asInt(), it.path("purity").asInt())
        }
        return row.path("player_uuid").asText() == target.player && row.path("hand").asText() == target.hand &&
            row.path("thirst").asInt() == (if (before) target.thirstBefore else target.thirstAfter) &&
            row.path("quenched").asInt() == (if (before) target.quenchedBefore else target.quenchedAfter) &&
            row.path("held_item").asText() == item && row.path("held_count").asInt() == count &&
            (if (purity == null) row.path("item_purity").isNull else row.path("item_purity").isIntegralNumber && row.path("item_purity").asInt() == purity) &&
            actual == expectedFluids &&
            row.path("capability_source").asText() == "dev.ghen.thirst.foundation.common.capability.ModCapabilities.PLAYER_THIRST" &&
            row.path("purity_source").asText() == "dev.ghen.thirst.content.purity.WaterPurity.getPurity"
    }
    /** Passive real client use, independent of the legacy server-requested use control. */
    internal fun evaluatePassive(parameters: JsonNode, before: JsonNode, action: JsonNode, after: JsonNode): MeasurementVerdict {
        val t = parse(parameters)
        val source = action.path("item_before_arm"); val finish = action.path("item_at_finish")
        val atStart = action.path("item_at_start"); val applied = action.path("item_after_applied")
        val pre = action.path("physiology_before"); val post = action.path("physiology_after")
        require(source.path("item_id").isTextual && source.path("count").isIntegralNumber &&
            source.path("content_algorithm").asText() == "bc.playtest.stack-content.v1" &&
            source.path("canonical_algorithm").asText() == "bc.playtest.stack-nbt.v1" &&
            Regex("[0-9a-f]{64}").matches(source.path("content_sha256").asText()) &&
            Regex("[0-9a-f]{64}").matches(source.path("canonical_sha256").asText()) &&
            atStart.path("content_sha256") == source.path("content_sha256") &&
            finish.path("content_sha256") == source.path("content_sha256") &&
            pre.path("thirst_supported").asBoolean(false) && post.path("thirst_supported").asBoolean(false) &&
            listOf("thirst", "quenched", "food_level").all { pre.path(it).isIntegralNumber && post.path(it).isIntegralNumber } &&
            pre.path("saturation").isNumber && post.path("saturation").isNumber) {
            "passive item/physiology snapshots incomplete"
        }
        val result = observed(before, t, true) && observed(after, t, false) &&
            source.path("item_id").asText() == t.beforeItem && source.path("count").asInt() == t.beforeCount &&
            (if (t.afterCount == 0 && t.afterItem == "minecraft:air") applied.isNull else
                applied.path("item_id").asText() == t.afterItem && applied.path("count").asInt(-1) == t.afterCount) &&
            pre.path("thirst").asInt() == t.thirstBefore && post.path("thirst").asInt() == t.thirstAfter &&
            pre.path("quenched").asInt() == t.quenchedBefore && post.path("quenched").asInt() == t.quenchedAfter &&
            pre.path("food_level") == post.path("food_level") &&
            kotlin.math.abs(pre.path("saturation").asDouble() - post.path("saturation").asDouble()) <= .001
        return MeasurementVerdict(if (result) "passed" else "failed",
            "real OS-held duration-drink plus passive packet/finish/applied state and independent Thirst/purity/volume; source acquisition not established")
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val t = parse(parameters); require(observations.size == 3)
        val action = observations[1].payload
        require(action.path("uuid").isTextual && action.path("hand").isTextual &&
            action.path("item_before").isTextual && action.path("item_after").isTextual &&
            action.path("count_before").isIntegralNumber && action.path("count_after").isIntegralNumber &&
            action.path("consumption_completed").isBoolean) { "normal use completion evidence missing" }
        val good = observed(observations[0].payload, t, true) && observed(observations[2].payload, t, false) &&
            action.path("uuid").asText() == t.player && action.path("hand").asText() == t.hand &&
            action.path("consumption_completed").asBoolean() &&
            action.path("item_before").asText() == t.beforeItem && action.path("item_after").asText() == t.afterItem &&
            action.path("count_before").asInt() == t.beforeCount && action.path("count_after").asInt() == t.afterCount
        return MeasurementVerdict(if (good) "passed" else "failed", "paid native use and independently read Thirst/purity/volume transition; no natural acquisition proof")
    }
}
