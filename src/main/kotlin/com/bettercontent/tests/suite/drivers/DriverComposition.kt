package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.SuiteCase
import com.fasterxml.jackson.databind.JsonNode

/** What the control actually measures. Not the parent catalogue promise. */
internal data class ControlCapability(val id: String, val measurements: Set<String>, val exclusions: Set<String>)
internal val controlCapabilities: Map<String, ControlCapability> = listOf(
    ControlCapability("control.food.placed-bite", setOf("one_block_bite_delta", "one_hunger_delta"),
        setOf("item_consumption", "nutrition", "effects", "container_returns", "natural_acquisition", "ten_food_roster")),
    ControlCapability("control.food.item-consumption", setOf("one_native_duration_completed_item", "one_hunger_delta", "one_item_or_container_transition"),
        setOf("nutrition", "effects", "hydration", "purity", "natural_acquisition", "ten_food_roster")),
    ControlCapability("control.primitive.tool-break", setOf("one_block_identity_transition", "one_tool_damage_delta"),
        setOf("all_tools", "wrong_tier", "drops", "natural_acquisition"))
).associateBy { it.id }

internal data class ControlTarget(val acquisitionId: String, val controlId: String, val parameters: JsonNode,
    val variantId: String = "baseline")
internal data class ControlRun(val target: ControlTarget, val observations: List<DriverObservation>)

/** A bounded, explicit per-target matrix. Controls with gaps stay useful as diagnostics, never full-case proof. */
internal object DriverComposition {
    fun plan(case: SuiteCase, targets: List<ControlTarget>): List<Pair<ControlTarget, List<DriverStep>>> {
        require(targets.isNotEmpty() && targets.size <= 256) { "empty/unbounded target matrix" }
        require(targets.all { Regex("[a-z0-9][a-z0-9_.-]*").matches(it.variantId) }) { "invalid control variant ID" }
        require(targets.map { Triple(it.acquisitionId, it.controlId, it.variantId) }.distinct().size == targets.size) { "duplicate control target variant" }
        require(case.targets.isNotEmpty() && case.targets.distinct().size == case.targets.size) { "case lacks reviewed acquisition targets" }
        require(targets.map { it.acquisitionId }.toSet() == case.targets.toSet()) { "target matrix differs from acquired case roster" }
        return targets.map { target ->
            require(target.acquisitionId in case.targets) { "unmapped acquisition target" }
            val control = DriverRegistry.resolve(target.controlId) ?: error("unsupported control ${target.controlId}")
            require(controlCapabilities.containsKey(control.caseId)) { "control measurements not declared" }
            target to control.plan(target.parameters)
        }
    }
    fun evaluate(case: SuiteCase, runs: List<ControlRun>): List<DriverOutcome> {
        plan(case, runs.map { it.target }) // perform all closed-roster and target checks before evaluating any row
        return runs.map { run ->
            val control = requireNotNull(DriverRegistry.resolve(run.target.controlId))
            evaluateCase(control, run.target.parameters, run.observations)
        }
    }
    fun fullCaseVerdict(case: SuiteCase, runs: List<ControlRun>): MeasurementVerdict {
        val outcomes = evaluate(case, runs)
        if (outcomes.any { it is DriverOutcome.Fatal }) return MeasurementVerdict("inconclusive", "native control action/cleanup evidence failed")
        if (outcomes.any { it is DriverOutcome.Verdict && it.value.state == "failed" })
            return MeasurementVerdict("failed", "at least one observed target violates an independent control oracle")
        // The entire matrix passing does not imply the catalogue's unrelated assertions (acquisition,
        // nutrients, drops, wrong tiers, etc.) were ever observed.
        return MeasurementVerdict("blocked", "control measurements do not own the full catalogue assertion ${case.assertions.joinToString()}")
    }
}
