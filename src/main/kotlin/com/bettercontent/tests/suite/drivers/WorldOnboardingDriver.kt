package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.fasterxml.jackson.databind.JsonNode
import kotlin.math.floor

/** Registered full case, not a qualification claim: first real login + spectator Shift+K + passive finalized site.
 * Reviewed authoring and an origin-epoch prerequisite still gate execution before any world is created.
 */
internal object WorldOnboardingDriver : CaseDriver {
    override val caseId = "world.onboarding"
    override val assertionIds = setOf("world.onboarding.first-join-spectator",
        "world.onboarding.genuine-spawn-only-input", "world.onboarding.safe-final-survival-site")
    private fun split(parameters: JsonNode): Pair<JsonNode, JsonNode> {
        require(parameters.isObject && parameters.fieldNames().asSequence().toSet() == setOf("first_join", "onboarding")) {
            "first login and genuine onboarding require distinct reviewed source targets"
        }
        val first = parameters.path("first_join"); val onboarding = parameters.path("onboarding")
        require(first.isObject && onboarding.isObject && first.path("player_uuid").isTextual &&
            first.path("player_uuid") == onboarding.path("player_uuid") &&
            first.path("dimension").isTextual && first.path("dimension") == onboarding.path("dimension")) {
            "first login and onboarding cannot drift actor or dimension"
        }
        return first to onboarding
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val (first, onboarding) = split(parameters)
        return FirstJoinDriver.plan(first) + OnboardingControlPlan.plan(onboarding)
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val (first, onboarding) = split(parameters)
        require(observations.size == 7 && validateTerminalSequence(plan(parameters), observations) == null) {
            "first login and complete real onboarding observations required"
        }
        val firstRow = observations[0].payload
        require(firstRow.path("mode").isTextual && firstRow.path("mode").asText() == "spectator" &&
            firstRow.path("uuid").asText() == first.path("player_uuid").asText() &&
            firstRow.path("dimension") == onboarding.path("dimension") &&
            listOf("x", "y", "z").all { firstRow.path(it).isNumber && firstRow.path(it).asDouble().isFinite() }) {
            "actual first-login actor was not an observed joined spectator"
        }
        val area = onboarding.path("request_bounds")
        for (axis in listOf("x", "y", "z")) {
            val block = floor(firstRow.path(axis).asDouble())
            val low = area.path("${axis}_min"); val high = area.path("${axis}_max")
            require(block.isFinite() && block >= low.asDouble(Double.NaN) && block <= high.asDouble(Double.NaN)) {
                "first-login spectator did not land within the pre-reviewed onboarding arm bounds"
            }
        }
        val initial = FirstJoinDriver.evaluate(first, observations.take(1))
        if (initial.state != "passed") return initial
        val result = OnboardingControlPlan.evaluate(onboarding, observations.drop(1))
        return if (result.state != "passed") result else MeasurementVerdict("passed",
            "genuine first login spectator, OS Shift+K callback/key-up, passive server finalization and independent raw terrain; " +
            "provider artifact/reopen persistence and human-reviewed case metadata remain independent gates")
    }
}
