package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

/** Bounded source-pinned Block.popResource proof for ONE natural dig; not full tool family/crafting. */
internal object SourceAttributedDigControlDriver : CaseDriver {
    override val caseId = "control.primitive.source-attributed-dig"
    private fun parse(node: JsonNode) {
        NativeDigControlDriver.plan(node)
        require(node.path("expected_pickups").isObject && node.path("expected_pickups").size() > 0) {
            "causal source control requires a positive expected pickup"
        }
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        parse(parameters)
        return NativeDigControlDriver.plan(parameters)
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        parse(parameters)
        val baseline = NativeDigControlDriver.evaluate(parameters, observations)
        if (baseline.state != "passed") return baseline
        val target = observations[1].payload
        require(target.path("drop_entities").isArray && target.path("origin_evidence").isTextual &&
            target.path("unscoped_item_join_observed").isBoolean) { "source-pinned native drop observer absent" }
        val rows = target.path("drop_entities").toList(); val pickupRows = target.path("pickup_events").toList()
        require(rows.size <= 64 && pickupRows.size <= 64) { "unbounded causal drop" }
        if (rows.isEmpty()) return MeasurementVerdict("inconclusive", "pickup had no scoped Block.popResource source hook; origin unsupported")
        fun uuid(row: JsonNode): String {
            val id = row.path("entity_uuid"); require(id.isTextual &&
                runCatching { UUID.fromString(id.asText()).toString() }.getOrNull() == id.asText()) { "invalid drop entity UUID" }
            return id.asText()
        }
        val drops = rows.associateBy(::uuid)
        val pickups = pickupRows.associateBy(::uuid)
        require(rows.size == drops.size && pickupRows.size == pickups.size && rows.all {
            it.path("item").isTextual && it.path("count").isIntegralNumber && it.path("count").asInt() in 1..64 &&
                it.path("origin_x").isIntegralNumber && it.path("origin_y").isIntegralNumber && it.path("origin_z").isIntegralNumber &&
                it.path("spawn_tick").isIntegralNumber && it.path("entity_join_accepted").isBoolean &&
                it.path("pickup_observed").isBoolean && it.path("pickup_tick").isIntegralNumber &&
                it.path("pickup_count").isIntegralNumber && it.path("source_method").isTextual
        } && pickupRows.all {
            it.path("item").isTextual && it.path("count").isIntegralNumber && it.path("count").asInt() in 1..64 &&
                it.path("tick").isIntegralNumber && it.path("scoped_block_origin_matched").isBoolean
        }) { "untyped causal spawn/join/pickup chain" }
        val correct = !target.path("unscoped_item_join_observed").asBoolean() &&
            target.path("origin_evidence").asText() == "exact_Block_popResource_pos_scope_entity_join_UUID_and_pickup_UUID" &&
            target.path("pickup_correlation").asText() == "UUID_match_only; custom_bypass_paths_uncovered" &&
            rows.all { drop ->
                val pickup = pickups[uuid(drop)]
                drop.path("origin_x").asInt() == parameters.path("x").asInt() &&
                    drop.path("origin_y").asInt() == parameters.path("y").asInt() &&
                    drop.path("origin_z").asInt() == parameters.path("z").asInt() &&
                    drop.path("source_method").asText() == "Block.popResource(Level,BlockPos,ItemStack)" &&
                    drop.path("entity_join_accepted").asBoolean() && drop.path("pickup_observed").asBoolean() &&
                    pickup != null && pickup.path("scoped_block_origin_matched").asBoolean() &&
                    pickup.path("item").asText() == drop.path("item").asText() &&
                    pickup.path("count").asInt() == drop.path("pickup_count").asInt() &&
                    drop.path("pickup_count").asInt() in 1..drop.path("count").asInt() &&
                    drop.path("spawn_tick").asLong() <= pickup.path("tick").asLong() &&
                    pickup.path("tick").asLong() == drop.path("pickup_tick").asLong()
            } && rows.size == pickupRows.size
        return MeasurementVerdict(if (correct) "passed" else "inconclusive",
            "one native block-origin drop chain; custom bypass path and natural tool acquisition remain unproven")
    }
}
