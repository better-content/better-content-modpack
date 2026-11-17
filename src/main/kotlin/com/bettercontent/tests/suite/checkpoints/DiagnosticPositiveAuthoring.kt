package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.SuiteCase
import com.bettercontent.tests.suite.drivers.CaseDriver
import com.bettercontent.tests.suite.drivers.DriverStep
import com.bettercontent.tests.suite.client.OBSERVED_ACTOR_MARKER
import com.bettercontent.tests.suite.client.CONTROLLER_CONTEXT_MARKER
import com.fasterxml.jackson.databind.JsonNode

/** Pure pre-world source/manifest check; only saved onboarding and original actions can prove readiness. */
internal fun validateAuthoredDiagnosticPositivePlan(manifest: JsonNode, spec: SuiteCase,
    branch: BranchSpec, driver: CaseDriver, parameters: JsonNode, plan: List<DriverStep>,
    purpose: ExecutionPurpose) {
    val id = requireNotNull(reviewedDiagnosticPositiveCases[spec.id])
    require(purpose == ExecutionPurpose.DIAGNOSTIC && driver.clientRequired &&
        spec.layer == "campaign" && spec.assertions.isEmpty() && spec.targetBindings.isEmpty() &&
        spec.evidenceKinds.toSet() == setOf("native-interaction") &&
        branch.caseIds == listOf(spec.id) && plan.size == 1 &&
        parameters.isObject && parameters.fieldNames().asSequence().toSet() ==
            setOf("dimension", "x", "y", "z"))
    val action = plan.single().action
    require(action.id == "block_mutation.control_positive_v1" && action.source == "server" &&
        action.parameters.isObject && action.parameters.fieldNames().asSequence().toSet() ==
            setOf("player_uuid", "control_id", "scope_id", "dimension", "x", "y", "z", "witness_context") &&
        action.parameters.path("player_uuid").asText() == OBSERVED_ACTOR_MARKER &&
        action.parameters.path("control_id").asText() == id &&
        action.parameters.path("scope_id").asText() == DIAGNOSTIC_MUTATION_SCOPE_MARKER &&
        action.parameters.path("witness_context").asText() == CONTROLLER_CONTEXT_MARKER &&
        listOf("dimension", "x", "y", "z").all { action.parameters.path(it) == parameters.path(it) })
    val roster = manifest.path("diagnostic_mutation_controls")
    require(roster.isArray && roster.size() in 3..16 &&
        roster.map { it.path("case_id").asText() }.distinct().size == roster.size())
    val records = roster.filter { it.path("setup_case_id").asText() ==
        "control.mutation.setup-delegated" }
    require(records.filter { it.path("case_id").asText() in reviewedDiagnosticPositiveCases.keys }
        .associate { it.path("case_id").asText() to it.path("control_id").asText() } ==
        reviewedDiagnosticPositiveCases.filterKeys { it != "control.mutation.setup-delegated" })
    val reference = records.first()
    require(records.all { row -> listOf("setup_checkpoint_id", "setup_parent_id", "actor_uuid",
        "position", "origin_state", "before_state").all { row.path(it) == reference.path(it) } } &&
        reference.path("position") == parameters &&
        reference.path("setup_parent_id").asText() != "origin")
    val onboarding = manifest.path("branches").filter { it.path("case_ids").any { row ->
        row.asText() == "world.onboarding" } }.single()
    require(onboarding.path("case_ids").size() == 1 &&
        onboarding.path("input_checkpoint").asText() == "origin" &&
        onboarding.path("output_checkpoint") == reference.path("setup_parent_id") &&
        manifest.path("driver_bindings").single { it.path("case_id").asText() == "world.onboarding" }
            .path("parameters").path("first_join").path("player_uuid") == reference.path("actor_uuid"))
    if (spec.id == "control.mutation.setup-delegated") require(
        branch.inputCheckpoint == reference.path("setup_parent_id").asText() &&
        branch.outputCheckpoint == reference.path("setup_checkpoint_id").asText() &&
        branch.statePolicy == "carry-forward")
    else {
        val selected = records.single { it.path("case_id").asText() == spec.id }
        require(selected.path("schema").asText() == "bc.playtest_diagnostic_mutation_plan.v2" &&
            selected.path("control_id").asText() == id &&
            branch.inputCheckpoint == reference.path("setup_checkpoint_id").asText() &&
            branch.outputCheckpoint == null && branch.statePolicy == "isolated")
    }
}
