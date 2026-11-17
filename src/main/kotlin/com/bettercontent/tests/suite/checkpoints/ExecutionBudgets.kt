package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.SuiteCase
import com.bettercontent.tests.suite.drivers.DriverAction
import com.bettercontent.tests.suite.drivers.DriverRegistry
import com.bettercontent.tests.suite.drivers.TrialCaseDriver
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.time.Duration
import java.time.Instant

/** Reviewed wall allowances and native tick allowances are distinct quantities, never converted into one another. */
internal data class ActionAllowance(val id: String, val wallSeconds: Long, val nativeTimeoutTicks: Int) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9_.-]*")) && wallSeconds in 1..2_592_000 &&
            nativeTimeoutTicks in 1..72_000) { "invalid positive finite reviewed action allowance" }
    }
    val wall: Duration get() = Duration.ofSeconds(wallSeconds)
    fun check(action: DriverAction) {
        require(action.id == id && action.timeoutTicks == nativeTimeoutTicks) {
            "native request tick allowance differs from reviewed action: $id"
        }
        if (id == "scale.begin") require(wallSeconds == 900L && nativeTimeoutTicks >= 20_040) {
            "scale requires independent controller 900s wall deadline and native >=20040tick allowance"
        }
    }
}

internal data class CaseAllowance(val caseId: String, val trialCount: Int, val trialMaximumSeconds: Long) {
    init { require(caseId.matches(Regex("[a-z0-9][a-z0-9_.-]*")) && trialCount in 1..10_000 &&
        trialMaximumSeconds in 1..2_592_000) { "invalid finite reviewed trial count/duration" } }
}
internal data class ReviewedExecutionBudget(val originSeconds: Long, val epochStartSeconds: Long,
    val epochStopSealSeconds: Long, val trialSetupSeconds: Long, val trialCleanupSeconds: Long,
    val campaignOverheadSeconds: Long, val campaignMaximumSeconds: Long,
    val actions: Map<String, ActionAllowance>, val cases: Map<String, CaseAllowance>) {
    fun action(action: DriverAction): ActionAllowance = actions.getValue(action.id).also { it.check(action) }
    fun case(spec: SuiteCase): CaseAllowance = cases.getValue(spec.id)
    fun trialSeconds(spec: SuiteCase): Long = case(spec).trialMaximumSeconds
    fun campaignDeadline(start: Instant): Instant = start.plusSeconds(campaignMaximumSeconds)

    companion object {
        /** All counts and action rosters come from the selected, dependency-closed driver plans. */
        fun reviewed(node: JsonNode, branches: List<BranchSpec>, request: com.bettercontent.tests.suite.V2CampaignRequest): ReviewedExecutionBudget {
            fun exact(objectNode: JsonNode, keys: Set<String>, location: String) {
                require(objectNode.isObject && objectNode.fieldNames().asSequence().toSet() == keys) {
                    "unreviewed $location budget fields"
                }
            }
            fun seconds(row: JsonNode, field: String): Long {
                val value = row.path(field)
                require(value.isIntegralNumber && value.canConvertToLong() && value.asLong() in 1..2_592_000) {
                    "reviewed positive finite budget missing: $field"
                }
                return value.asLong()
            }
            exact(node, setOf("schema", "review", "origin_seconds", "epoch_start_seconds",
                "epoch_stop_seal_seconds", "trial_setup_seconds", "trial_cleanup_seconds", "campaign_overhead_seconds",
                "campaign_maximum_seconds", "actions", "cases"), "execution")
            require(node.path("schema").asText() == "bc.playtest_execution_budget.v1")
            val review = node.path("review")
            exact(review, setOf("state", "reviewer", "reviewed_at", "blockers"), "execution review")
            require(review.path("state").asText() == "reviewed" && review.path("reviewer").isTextual &&
                review.path("reviewer").asText().isNotBlank() && review.path("blockers").isArray &&
                review.path("blockers").isEmpty &&
                runCatching { Instant.parse(review.path("reviewed_at").asText()).isBefore(Instant.now()) }.getOrDefault(false)) {
                "reviewed execution budget requires human calibration before running a campaign"
            }
            val actions = node.path("actions")
            require(actions.isArray && !actions.isEmpty)
            val actionRows = actions.map { row ->
                exact(row, setOf("id", "wall_seconds", "native_timeout_ticks"), "action")
                val id = row.path("id").asText()
                val ticks = row.path("native_timeout_ticks")
                require(id.matches(Regex("[a-z0-9][a-z0-9_.-]*")) && ticks.isIntegralNumber &&
                    ticks.canConvertToInt() && ticks.asInt() in 1..72_000)
                ActionAllowance(id, seconds(row, "wall_seconds"), ticks.asInt())
            }
            require(actionRows.map { it.id }.distinct().size == actionRows.size) { "duplicate reviewed action" }
            val cases = node.path("cases")
            require(cases.isArray && !cases.isEmpty)
            val caseRows = cases.map { row ->
                exact(row, setOf("case_id", "trial_count", "trial_maximum_seconds"), "case")
                val count = row.path("trial_count")
                require(row.path("case_id").isTextual && count.isIntegralNumber && count.canConvertToInt() &&
                    count.asInt() in 1..10_000)
                CaseAllowance(row.path("case_id").asText(), count.asInt(), seconds(row, "trial_maximum_seconds"))
            }
            require(caseRows.map { it.caseId }.distinct().size == caseRows.size) { "duplicate reviewed case budget" }
            val budget = ReviewedExecutionBudget(seconds(node, "origin_seconds"), seconds(node, "epoch_start_seconds"),
                seconds(node, "epoch_stop_seal_seconds"), seconds(node, "trial_setup_seconds"),
                seconds(node, "trial_cleanup_seconds"), seconds(node, "campaign_overhead_seconds"),
                seconds(node, "campaign_maximum_seconds"), actionRows.associateBy { it.id }, caseRows.associateBy { it.caseId })
            require(budget.trialCleanupSeconds == 120L) { "existing exact native cleanup envelope remains 120s" }
            val selected = request.cases.associateBy { it.id }
            require(selected.keys == branches.flatMap { it.caseIds }.toSet() && selected.keys.all { it in budget.cases }) {
                "reviewed budget missing selected dependency-closed case"
            }
            var derived = Math.addExact(Math.addExact(budget.campaignOverheadSeconds, budget.originSeconds),
                budget.epochStopSealSeconds)
            val usedActions = mutableSetOf<String>()
            val purpose = ExecutionPurpose.requireExplicit(request.manifest)
            branches.forEach { branch ->
                val specs = branch.caseIds.map(selected::getValue)
                var trialsInBranch = 0L
                var independentEpochs = 0L
                specs.forEach { spec ->
                    val driver = controllerDriver(spec, purpose)
                    val params = request.bindings.getValue(spec.id).path("parameters")
                    val trials = if (driver is TrialCaseDriver) driver.expandTrials(params, branch.inputCheckpoint)
                        else listOf(com.bettercontent.tests.suite.drivers.TrialRequest(spec.id, "single",
                            spec.targets.singleOrNull() ?: spec.id, branch.inputCheckpoint, params,
                            driver.assertionIds, driver.fixtureProvisionedTargets(params)))
                    val allowance = budget.case(spec)
                    require(allowance.trialCount == trials.size) { "trial count differs from reviewed case budget: ${spec.id}" }
                    trialsInBranch = Math.addExact(trialsInBranch, trials.size.toLong())
                    if (specs.size == 1 && driver is TrialCaseDriver) independentEpochs = trials.size.toLong()
                    trials.forEach { trial ->
                        val steps = if (driver is TrialCaseDriver) driver.planTrial(trial) else driver.plan(params)
                        val actionSeconds = steps.fold(0L) { sum, step ->
                            val bound = budget.action(step.action)
                            usedActions += bound.id
                            Math.addExact(sum, bound.wallSeconds)
                        }
                        val maximum = Math.addExact(Math.addExact(budget.trialSetupSeconds, actionSeconds),
                            budget.trialCleanupSeconds)
                        require(allowance.trialMaximumSeconds == maximum && maximum <= spec.timeoutSeconds) {
                            "trial budget cannot fit the reviewed action/lifecycle count: ${spec.id}"
                        }
                        derived = Math.addExact(derived, maximum)
                    }
                }
                val epochs = if (independentEpochs > 0) independentEpochs else 1L
                require(epochs > 0 && trialsInBranch > 0)
                derived = Math.addExact(derived, Math.multiplyExact(epochs,
                    Math.addExact(budget.epochStartSeconds, budget.epochStopSealSeconds)))
            }
            require(derived == budget.campaignMaximumSeconds && derived in 1..2_592_000 &&
                usedActions.isNotEmpty() && usedActions.all { it in budget.actions }) {
                "campaign deadline differs from checked trial/epoch/action counts; calibration cannot be inferred"
            }
            return budget
        }
    }
}
