package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.SuiteEpochRuntime
import com.bettercontent.tests.suite.*
import com.bettercontent.tests.suite.drivers.DriverRegistry
import com.bettercontent.tests.suite.drivers.TrialCaseDriver
import com.bettercontent.tests.suite.client.INPUT_RUNTIME_MARKER
import com.bettercontent.tests.suite.client.OBSERVED_ACTOR_MARKER
import com.bettercontent.tests.suite.client.CONTROLLER_CONTEXT_MARKER
import com.bettercontent.tests.suite.client.ARM_SEQUENCE_MARKER
import com.bettercontent.tests.suite.client.ARM_ORDINAL_MARKER
import com.bettercontent.tests.suite.client.authenticInputDrivers
import com.bettercontent.tests.suite.client.requireReviewedInputObserverSequence
import com.bettercontent.tests.suite.client.passiveObservationDrivers
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.file.Path

internal data class V3CampaignRequest(val base: V2CampaignRequest, val branches: List<BranchSpec>,
    val launcherWheel: Path, val launcherSha256: String, val sandboxExecutable: Path, val sandboxSha256: String,
    val purpose: ExecutionPurpose, val budget: ReviewedExecutionBudget)

/** All static checks complete before allocating a campaign directory or creating a world. */
internal fun v3CampaignRequest(root: Path, manifest: Path, client: Path, server: Path, selected: List<String>): V3CampaignRequest {
    val m = suiteMapper.readTree(manifest.toFile())
    val purpose = ExecutionPurpose.requireExplicit(m)
    require(purpose == ExecutionPurpose.DIAGNOSTIC || selected.isEmpty()) {
        "qualification cannot select a subset or reuse diagnostic selection"
    }
    val fullOrder = orderedBranches(m)
    verifyScaleBranchOrder(fullOrder)
    require(selected.distinct().size == selected.size && selected.all { id -> fullOrder.any { id in it.caseIds } }) { "invalid diagnostic selector" }
    val byId = fullOrder.associateBy { it.id }
    val producers = fullOrder.filter { it.outputCheckpoint != null }.associateBy { it.outputCheckpoint!! }
    val wanted = linkedSetOf<String>()
    fun include(branch: BranchSpec) {
        if (!wanted.add(branch.id)) return
        (branch.dependsOn + branch.orderingDependencies).forEach { include(byId.getValue(it)) }
        producers[branch.inputCheckpoint]?.let(::include)
    }
    fullOrder.filter { selected.isEmpty() || it.caseIds.any(selected::contains) }.forEach(::include)
    val branches = fullOrder.filter { it.id in wanted }
    // A diagnostic selection includes complete prerequisite branches; it never resets halfway through a group.
    val chosenCases = branches.flatMap { it.caseIds }
    val defects = validateV3Authoring(root, m, purpose, chosenCases.toSet())
    require(defects.isEmpty()) { "Campaign blocked before world creation:\n${defects.joinToString("\n")}" }
    val base = buildCampaignRequest(root, manifest, m, client, server,
        if (purpose == ExecutionPurpose.DIAGNOSTIC) chosenCases else emptyList(), checkpointTrials = true)
        .copy(diagnostic = purpose == ExecutionPurpose.DIAGNOSTIC)
    require(base.cases.map { it.id }.toSet() == chosenCases.toSet()) { "case dependency lies outside reviewed checkpoint ancestry" }
    branches.forEach { branch -> branch.caseIds.forEach { id ->
        val spec = base.cases.single { it.id == id }
        val selectors = spec.targetBindings.map { suiteMapper.treeToValue(it, ReviewedTargetSelector::class.java) }
        require(selectors.map { it.id }.distinct().size == selectors.size && selectors.all { selector ->
            selector.catalogueSha256 == m.path("authoring_catalogue").path("sha256").asText() &&
                selector.inputCheckpointId == branch.inputCheckpoint
        }) { "reviewed runtime target selectors do not bind this catalogue/checkpoint: $id" }
        val driver = requireNotNull(DriverRegistry.resolve(id))
        val params = base.bindings.getValue(id).path("parameters")
        val trialPlans = if (driver is TrialCaseDriver)
            driver.expandTrials(params, branch.inputCheckpoint).map(driver::planTrial)
            else listOf(driver.plan(params))
        trialPlans.forEach(::requireReviewedInputObserverSequence)
        val inputActions = trialPlans.flatten().map { it.action }.filter { it.id in authenticInputDrivers }
        // Native producer exists, but A has no observed reviewed-target ledger/post-seal adapter yet.
        // Never publish a driver-authored witness_context or bound_target_sha256 as controller truth.
        require(trialPlans.flatten().none { it.action.id == "player.dig.witness_v1" }) {
            "witness-v1 requires controller-allocated context, frozen observed target and post-seal adapter before dispatch"
        }
        val passiveActions = trialPlans.flatten().map { it.action }.filter { it.id in passiveObservationDrivers }
        require((passiveActions.isEmpty() || (driver.clientRequired && inputActions.isNotEmpty())) &&
            passiveActions.all { action -> action.source == "server" &&
            action.parameters.path("player_uuid").asText() == OBSERVED_ACTOR_MARKER &&
            action.parameters.path("witness_context").isTextual &&
            action.parameters.path("witness_context").asText() == CONTROLLER_CONTEXT_MARKER &&
            runCatching { java.util.UUID.fromString(action.parameters.path("observer_id").asText()).toString() ==
                action.parameters.path("observer_id").asText() }.getOrDefault(false) &&
            (!action.id.endsWith(".await") || (action.parameters.path("arm_request_sequence").asText() ==
                ARM_SEQUENCE_MARKER && action.parameters.path("arm_action_ordinal").asText() == ARM_ORDINAL_MARKER)) }) {
            "passive observer requires controller-allocated context/actual actor/arm markers"
        }
        require(inputActions.all { action -> action.source == "client" &&
            action.parameters.path("player_uuid").asText() == OBSERVED_ACTOR_MARKER &&
            action.parameters.path("input_runtime_sha256").isTextual &&
            action.parameters.path("input_runtime_sha256").asText() == INPUT_RUNTIME_MARKER } &&
            (inputActions.isEmpty() || inputActions.any { it.id == "client.input_hold" }) &&
            inputActions.filter { it.id == "client.input_hold" }.map { it.parameters.path("profile").asText() }
                .all { it in m.path("profile").path("visual_profiles").map { it.asText() } }) {
            "physical input requires immutable controller marker and reviewed display profile: $id"
        }
        if (id == "client.emi.public-index") require(params.path("emi_jar_sha256").asText() ==
            candidateEmiJarSha256(base.pair.client, base.pair.server)) {
            "reviewed EMI expectation differs from the exact candidate pair"
        }
        if (id in setOf("world.initial-spawn", "world.temperate")) require(branch.inputCheckpoint == "origin") {
            "first-join assertions require the player-free origin, not merely an offline player count"
        }
        if (driver is TrialCaseDriver) {
            val trials = driver.expandTrials(params, branch.inputCheckpoint)
            if (id in setOf("scale.8", "scale.16", "scale.32")) {
                val cohort = id.substringAfter('.').toInt()
                require(trials.size == 1 && (driver as? ExternalSamplingDriver)?.requireFreshTerrain == true) {
                    "scale cohort must reserve fresh independently reviewed input terrain"
                }
                val sites = (driver as ExternalSamplingDriver).samplingIntents(trials.single())
                    .filterIsInstance<SamplingIntent.Site>()
                require(sites.size == cohort && sites.all { it.footprintRadiusChunks == 11 }) {
                    "scale view-distance-ten geometry requires the entire +/-11 preclaim footprint"
                }
            }
            require(branch.statePolicy == "isolated" && branch.caseIds.size == 1 && branch.outputCheckpoint == null) {
                "expanded independent trials cannot publish combined state or share a carry-forward epoch: $id"
            }
            require(trials.isNotEmpty() && trials.all { it.caseId == id && it.inputCheckpointId == branch.inputCheckpoint &&
                it.assertionIds == driver.assertionIds && it.trialId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) })
        }
        driver.requiredAcquisitionCases(params).forEach { (_, proof) ->
            require(base.cases.any { it.id == proof }) { "natural acquisition proof must execute in this campaign lineage: $id/$proof" }
        }
    } }
    val wheel = boundInput(root, m.path("tooling").path("portablemc"))
    val hash = m.path("tooling").path("portablemc").path("sha256").asText()
    // Validates the immutable tool without materializing or executing it.
    val sandbox = boundInput(root, m.path("tooling").path("bubblewrap"))
    val sandboxHash = m.path("tooling").path("bubblewrap").path("sha256").asText()
    SuiteEpochRuntime(root.toAbsolutePath().normalize(), wheel, hash, sandbox, sandboxHash)
    val budget = ReviewedExecutionBudget.reviewed(m.path("execution_budget"), branches, base)
    return V3CampaignRequest(base, branches, wheel, hash, sandbox, sandboxHash, purpose, budget)
}

internal fun verifyScaleBranchOrder(branches: List<BranchSpec>) {
    val cohorts = listOf("scale.8", "scale.16", "scale.32").map { id ->
        requireNotNull(branches.singleOrNull { id in it.caseIds }) { "missing unique scale branch: $id" }.also {
            require(it.caseIds == listOf(id) && it.inputCheckpoint == "origin" && it.statePolicy == "isolated" && it.outputCheckpoint == null) {
                "scale cohorts require independent origin restorations"
            }
        }
    }
    cohorts.zipWithNext().forEach { (before, after) ->
        require(before.id in after.orderingDependencies && branches.indexOf(before) < branches.indexOf(after)) {
            "scale must execute sequentially 8, 16, 32, with failed prerequisites blocking later cohorts"
        }
    }
}

/** Draft conversion only: the old document stays intact and no receipt/review becomes v3 evidence. */
internal fun convertCheckpointDraft(original: JsonNode): ObjectNode {
    require(original.path("schema").asText() in setOf("bc.playtest_manifest.v1", "bc.playtest_manifest.v2"))
    val source = if (original.path("schema").asText().endsWith("v1"))
        com.bettercontent.tests.suite.v2.contracts.convertV1Draft(original) else original.deepCopy<ObjectNode>()
    return source.deepCopy<ObjectNode>().apply {
        put("schema", "bc.playtest_manifest.v3")
        set<JsonNode>("original_pre_checkpoint_manifest", original.deepCopy())
        put("conversion_pending", true)
        set<ObjectNode>("authoring", suiteMapper.createObjectNode().put("state", "draft").apply {
            putArray("blockers").add("Review branch dependencies, complete all drivers and bind a fresh source-attested candidate; historical execution is not v3 evidence")
        })
        putArray("branches")
        putObject("storage").putNull("checkpoint_budget_bytes")
        putObject("tooling").putNull("portablemc").putNull("bubblewrap")
    }
}
