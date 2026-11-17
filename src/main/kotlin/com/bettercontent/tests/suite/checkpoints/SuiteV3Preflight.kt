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
    if (purpose == ExecutionPurpose.QUALIFICATION || fullOrder.any { branch ->
        branch.caseIds.any { it in setOf("scale.8", "scale.16", "scale.32") } })
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
        val approvedProfiles = selectors.filter { it.schema == "bc.playtest_target_selector.v2" }.associate { selector ->
            require(purpose == ExecutionPurpose.QUALIFICATION && branch.outputCheckpoint != null &&
                branch.caseIds == listOf(spec.id) && branch.inputCheckpoint == "origin" &&
                spec.targetBindings.size == 1) {
                "server-invoked natural acquisition requires one reviewed origin selector and sealed output branch"
            }
            selector.id to verifyReviewedStage1Coverage(selector, base.pair.client, base.pair.server, base.support)
        }
        val driver = controllerDriver(spec, purpose)
        val params = base.bindings.getValue(id).path("parameters")
        val trialPlans = if (driver is TrialCaseDriver)
            driver.expandTrials(params, branch.inputCheckpoint).map(driver::planTrial)
            else listOf(driver.plan(params))
        trialPlans.forEach { plan ->
            requireReviewedInputObserverSequence(plan, spec.id)
            if (spec.id in reviewedDiagnosticPositiveCases)
                validateAuthoredDiagnosticPositivePlan(m, spec, branch, driver, params, plan, purpose)
            if (spec.id in reviewedStage1NegativeCases)
                validateAuthoredDiagnosticNegativePlan(m, spec, branch, driver, params, plan, purpose)
            val diagnosticNegative = diagnosticGetterSelector(spec, plan, purpose, branch.inputCheckpoint)
            if (diagnosticNegative != null) require(branch.caseIds == listOf(spec.id) &&
                branch.outputCheckpoint == null && branch.statePolicy == "isolated" &&
                driver !is TrialCaseDriver && driver.clientRequired &&
                params.path("source_checkpoint_sha256").asText() == DIAGNOSTIC_INPUT_SHA_MARKER &&
                base.diagnostic) {
                "only one isolated client-joined diagnostic getter scope may seal an expected-negative output"
            }
            val observations = plan.filter { it.action.id == "target.observe.loaded_v1" }
            val digs = plan.filter { it.action.id == "player.dig.witness_v1" }
            require(digs.size <= 1 && (approvedProfiles.isEmpty() || digs.size == 1) &&
                (digs.isEmpty() || (digs.size == 1 && observations.size == 1 &&
                plan.indexOf(digs.single()) == plan.indexOf(observations.single()) + 1 &&
                digs.single().action.source == "server" &&
                digs.single().action.parameters.fieldNames().asSequence().toSet() == setOf("selector_id") &&
                digs.single().action.parameters.path("selector_id").isTextual &&
                digs.single().action.parameters.path("selector_id").asText() in approvedProfiles.keys &&
                driver !is TrialCaseDriver))) {
                "witness-v1 requires exactly one pre-reviewed natural target and subsequent sealed output action"
            }
            require((observations.isEmpty() || purpose == ExecutionPurpose.DIAGNOSTIC ||
                observations.single().action.parameters.path("selector_id").asText() in approvedProfiles.keys) &&
                observations.size <= 1 && observations.all { step ->
                val action = step.action
                val id = action.parameters.path("selector_id").asText()
                action.source == "server" && action.parameters.fieldNames().asSequence().toSet() ==
                    setOf("selector_id") && action.parameters.path("selector_id").isTextual &&
                    selectors.count { selector -> selector.id == id &&
                        selector.capabilityId == action.id && selector.inputCheckpointId == branch.inputCheckpoint &&
                        ((selector.source == TargetSource.NATURAL_UNMODIFIED_BLOCK && branch.inputCheckpoint == "origin") ||
                            selector.source == TargetSource.NEWLY_GENERATED_TERRAIN) &&
                        (purpose == ExecutionPurpose.DIAGNOSTIC || selector.id in approvedProfiles.keys) &&
                        selector.bounds.dimension in setOf("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end") &&
                        selector.order == TargetSelectionOrder.NEAREST_THEN_POSITION &&
                        selector.rule == "loaded_block_nearest_center_then_xyz_v1" } == 1
            }) { "loaded-only census is diagnostic until reviewed natural scope, controls and sealed acquisition join exist" }
        }
        val inputActions = trialPlans.flatten().map { it.action }.filter { it.id in authenticInputDrivers }
        // A v1 loaded-only selector can never dispatch dig. V2 requires the actual reviewed
        // candidate/provider/control inputs and a sealed output before any world is created;
        // controller derives context/target/output slot and qualifier independently replays.
        require(trialPlans.flatten().filter { it.action.id == "player.dig.witness_v1" }
            .all { it.action.parameters.path("selector_id").asText() in approvedProfiles.keys })
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
        if (id in setOf("world.initial-spawn", "world.temperate", "world.onboarding")) require(branch.inputCheckpoint == "origin") {
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
