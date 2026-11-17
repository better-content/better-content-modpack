package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.*
import com.bettercontent.tests.suite.*
import com.bettercontent.tests.suite.drivers.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.UUID

/** Explicit manifest DAG, not a user-accessible arbitrary restore mechanism. */
internal fun executeCheckpointCampaign(request: V3CampaignRequest) {
    val base = request.base; val m = base.manifest
    val started = Instant.now()
    val campaignDeadline = request.budget.campaignDeadline(started)
    val hardware = requireSuiteResources(true)
    val budget = m.path("storage").path("checkpoint_budget_bytes").asLong()
    val keepFree = 2L * 1024 * 1024 * 1024
    require(budget > 0 && Files.getFileStore(base.root).usableSpace - keepFree >= budget) {
        "insufficient disk for the reviewed retained-epochs/checkpoint budget; workload will not be reduced"
    }
    val runId = "campaign-${UUID.randomUUID()}"
    val lineage = UUID.randomUUID().toString()
    val evidence = SessionEvidence(base.root, runId, "campaign-v3", base.pair, diagnostic = base.diagnostic)
    val root = evidence.directory
    val catalogue = Hashes.sha256(base.manifestPath)
    val profile = suiteMapper.treeToValue(m.path("profile"), SuiteProfile::class.java)
    val bindings = CheckpointBindings(runId, lineage, candidateHashFields(base.pair),
        m.path("test_support").path("sha256").asText(), jsonFingerprint(jsonObject("profile" to m.path("profile"),
            "protocol" to m.path("protocol"), "tooling" to m.path("tooling"),
            "execution_budget" to m.path("execution_budget"))), request.purpose.wire)
    val storageFile = root.resolve("checkpoint-budget.json")
    exclusiveJson(storageFile, mapOf("schema" to "bc.playtest_checkpoint_budget.v1", "bindings" to bindings,
        "max_bytes" to budget, "keep_free_bytes" to keepFree))
    val timeBudgetFile = root.resolve("execution-budget.json")
    exclusiveJson(timeBudgetFile, mapOf("schema" to "bc.playtest_execution_budget_binding.v1",
        "bindings" to bindings, "manifest_sha256" to catalogue, "reviewed" to m.path("execution_budget"),
        "campaign_deadline" to campaignDeadline.toString()))
    val hardwareFile = root.resolve("hardware.json")
    exclusiveJson(hardwareFile, hardware + mapOf("schema" to "bc.playtest_hardware.v2", "profile" to profile.hardwareProfile,
        "view_distance" to 10, "simulation_distance" to 10))
    val overrideFile = root.resolve("fixture-overrides.json")
    exclusiveJson(overrideFile, mapOf("schema" to "bc.playtest_fixture_overrides.v2", "overrides" to listOf(
        mapOf("setting" to "online-mode", "value" to false, "purpose" to "loopback disposable identity; not authenticated-client scale proof", "changes_asserted_behavior" to false),
        mapOf("setting" to "test-support", "value" to bindings.supportSha256, "purpose" to "hash-bound nonshipping observation and explicit behavior fixtures", "changes_asserted_behavior" to false),
        mapOf("setting" to "offline-checkpoint-restoration", "value" to true, "purpose" to "declared independent-branch isolation, one seed/world lineage", "changes_asserted_behavior" to false))))
    val store = CheckpointStore(root, bindings)
    val ledger = SamplingLedger(root, bindings).also { it.initialize() }
    val events = root.resolve("campaign-events.jsonl")
    Files.createFile(events)
    val caseStarts = linkedMapOf<String, Instant>()
    val states = linkedMapOf<String, String>()
    val caseFiles = linkedMapOf<String, BoundFile>()
    val checkpoints = linkedMapOf<String, CheckpointRef>()
    val epochFiles = mutableListOf<BoundFile>()
    val branchResults = mutableListOf<Map<String, Any?>>()
    var failure: Throwable? = null
    var worldCreated = false
    var allClosed = true
    fun fail(error: Throwable) { if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error) }
    fun event(type: String, fields: Map<String, Any?> = emptyMap()) {
        Files.writeString(events, suiteMapper.writeValueAsString(linkedMapOf<String, Any?>("type" to type,
            "time" to Instant.now().toString(), "campaign_id" to runId, "lineage_id" to lineage).apply { putAll(fields) }) + "\n", StandardOpenOption.APPEND)
    }
    val executor = EpochExecutor(request, root, bindings, store, ledger, campaignDeadline) { kind, fields ->
        if (kind == "world_created") worldCreated = true
        if (kind == "trial_started") {
            val id = fields.getValue("case_id") as String
            if (caseStarts.putIfAbsent(id, Instant.now()) == null) event("case_started", mapOf("case_id" to id, "branch_id" to request.branches.single { id in it.caseIds }.id))
        }
        event(kind, fields)
    }
    fun recordCase(spec: SuiteCase, branch: BranchSpec, input: CheckpointRef?, executed: List<ExecutedTrial>,
        epochs: List<BoundFile>, state: String, reason: String) {
        check(spec.id !in states) { "case would execute twice: ${spec.id}" }
        val raw = executed.map { AssertionEvidence("native-interaction", it.raw) }
        val result = CaseResult("bc.playtest_case_result.v3", spec.id, caseFingerprint(spec), catalogue, profileFingerprint(profile),
            candidateHashFields(base.pair), state, base.diagnostic, runId, lineage, (caseStarts[spec.id] ?: Instant.now()).toString(),
            Instant.now().toString(), spec.assertions.map { AssertionResult(it, state, raw) }, executed.all { it.receipt.cleanupComplete },
            executed.flatMap { it.planned.request.provisionedTargets }.distinct())
        val document = suiteMapper.valueToTree<ObjectNode>(result).apply {
            put("purpose", request.purpose.wire); put("branch_id", branch.id); set<JsonNode>("input_checkpoint", suiteMapper.valueToTree(input))
            set<JsonNode>("epoch_receipts", suiteMapper.valueToTree(epochs)); set<JsonNode>("trials", suiteMapper.valueToTree(executed.map { it.file }))
            put("reason", reason)
        }
        val file = root.resolve("case-${spec.id}.json"); exclusiveJson(file, document)
        states[spec.id] = state; caseFiles[spec.id] = checkpointBound(file)
        event("case_completed", mapOf("case_id" to spec.id, "state" to state, "reason" to reason, "receipt" to checkpointBound(file)))
    }
    try {
        val diagnosticRoster = m.path("diagnostic_mutation_controls")
        if (request.purpose == ExecutionPurpose.DIAGNOSTIC && diagnosticRoster.isArray &&
            !diagnosticRoster.isEmpty) {
            require(diagnosticRoster.size() in 1..16 && Hashes.sha256(base.manifestPath) == catalogue)
            event("diagnostic_control_plan_committed", mapOf(
                "manifest" to BoundFile(base.manifestPath.toAbsolutePath().normalize().toString(), catalogue),
                "budget_binding" to checkpointBound(timeBudgetFile), "entries" to diagnosticRoster,
                "committed_before_origin" to true))
        }
        event("campaign_started", mapOf("branch_ids" to request.branches.map { it.id }, "purpose" to request.purpose.wire, "diagnostic" to base.diagnostic))
        allClosed = false
        val origin = executor.run("origin", null, emptyList())
        epochFiles += origin.reference; allClosed = origin.safelyClosed
        require(origin.safelyClosed && origin.epoch.status == "passed") { "origin bootstrap failed; no checkpoint is selectable" }
        checkpoints["origin"] = store.seal("origin", origin.roots, requireNotNull(origin.epoch.closure), null)
        require(origin.stopStartedAt != null && !Instant.now().isAfter(origin.stopStartedAt.plusSeconds(
            request.budget.epochStopSealSeconds)) && Instant.now().isBefore(campaignDeadline)) {
            "origin stop/save/checkpoint seal exceeded reviewed lifecycle or campaign budget"
        }
        event("checkpoint_sealed", mapOf("checkpoint" to checkpoints.getValue("origin"), "source_epoch" to origin.epoch.id))
        val caseMap = base.cases.associateBy { it.id }
        for (branch in request.branches) {
            val input = checkpoints[branch.inputCheckpoint]
            val specs = branch.caseIds.map(caseMap::getValue)
            if ("survival.food" in branch.caseIds && input != null) {
                val onboarding = request.branches.single { "world.onboarding" in it.caseIds }
                require(states["world.onboarding"] == "passed" &&
                    input == checkpoints[requireNotNull(onboarding.outputCheckpoint)] &&
                    store.verify(input).parent == checkpoints.getValue("origin")) {
                    "food requires the passed real onboarding output checkpoint, not sibling ordering"
                }
            }
            if (!allClosed || input == null || (branch.dependsOn + branch.orderingDependencies).any { dep -> branchResults.none { it["id"] == dep && it["state"] == "passed" } }) {
                specs.forEach { recordCase(it, branch, input, emptyList(), emptyList(), "blocked", "prerequisite checkpoint failed or prior epoch did not close safely") }
                branchResults += mapOf("id" to branch.id, "state" to "blocked", "input_checkpoint" to input, "output_checkpoint" to null,
                    "epoch_ids" to emptyList<String>(), "case_ids" to branch.caseIds)
                continue
            }
            event("branch_started", mapOf("branch_id" to branch.id, "input_checkpoint" to input))
            val branchEpochs = mutableListOf<EpochOutcome>()
            require(Instant.now().isBefore(campaignDeadline)) { "campaign hard deadline expired before branch" }
            val plans = specs.associate { spec ->
                val driver = controllerDriver(spec, request.purpose)
                val params = base.bindings.getValue(spec.id).path("parameters")
                val trials = if (driver is TrialCaseDriver) driver.expandTrials(params, input.id)
                    else listOf(TrialRequest(spec.id, "single", spec.targets.singleOrNull() ?: spec.id, input.id, params,
                        driver.assertionIds, driver.fixtureProvisionedTargets(params)))
                spec.id to trials.map { EpochTrial(spec, driver, it) }
            }
            fun launch(planned: List<EpochTrial>): EpochOutcome? = try {
                executor.run(branch.id, input, planned)
            } catch (error: Throwable) {
                // Includes pre-launch restore/copy failure: preserve completed trials and prohibit further restores.
                fail(error); allClosed = false
                event("epoch_aborted", mapOf("branch_id" to branch.id, "reason" to error.message))
                null
            }
            if (specs.size == 1 && controllerDriver(specs.single(), request.purpose) is TrialCaseDriver) {
                for (planned in plans.getValue(specs.single().id)) {
                    if (!allClosed) break
                    val outcome = launch(listOf(planned)) ?: break
                    branchEpochs += outcome; epochFiles += outcome.reference; allClosed = outcome.safelyClosed
                    outcome.failure?.let(::fail)
                }
            } else {
                launch(specs.flatMap { plans.getValue(it.id) })?.let { outcome ->
                    branchEpochs += outcome; epochFiles += outcome.reference; allClosed = outcome.safelyClosed
                    outcome.failure?.let(::fail)
                }
            }
            val diagnosticPositive = if (request.purpose != ExecutionPurpose.DIAGNOSTIC ||
                specs.size != 1) null else plans.getValue(specs.single().id).singleOrNull()?.let { planned ->
                val actions = if (planned.driver is TrialCaseDriver) planned.driver.planTrial(planned.request)
                    else planned.driver.plan(planned.request.parameters)
                actions.singleOrNull()?.action?.takeIf { it.id == "block_mutation.control_positive_v1" }
            }
            val diagnosticNegativeAction = if (request.purpose != ExecutionPurpose.DIAGNOSTIC ||
                specs.size != 1) null else plans.getValue(specs.single().id).singleOrNull()?.let { planned ->
                val actions = if (planned.driver is TrialCaseDriver) planned.driver.planTrial(planned.request)
                    else planned.driver.plan(planned.request.parameters)
                actions.singleOrNull()?.action?.takeIf { it.id in reviewedStage1NegativeActions.values }
            }
            val diagnosticSetup = diagnosticPositive?.takeIf { input != null &&
                store.verify(input).parent?.id == "origin" &&
                specs.singleOrNull()?.id == "control.mutation.setup-delegated" }?.let { action ->
                require(action.parameters.path("control_id").asText() == "level_chunk_delegated")
                loadPredeclaredDiagnosticMutationSetup(store, BoundFile(
                    base.manifestPath.toAbsolutePath().normalize().toString(), catalogue),
                    requireNotNull(input), specs.single().id)
            }
            val diagnosticPlan = diagnosticPositive?.takeIf { diagnosticSetup == null }?.let { action ->
                loadPredeclaredDiagnosticMutationControl(store, BoundFile(
                    base.manifestPath.toAbsolutePath().normalize().toString(), catalogue),
                    requireNotNull(input), specs.single().id,
                    action.parameters.path("control_id").asText())
            }
            val diagnosticNegative = diagnosticNegativeAction?.let { action ->
                require(diagnosticPositive == null && diagnosticSetup == null && branch.statePolicy == "isolated" &&
                    branch.outputCheckpoint == null && branchEpochs.size == 1)
                loadPredeclaredDiagnosticMutationNegative(store, BoundFile(
                    base.manifestPath.toAbsolutePath().normalize().toString(), catalogue),
                    requireNotNull(input), specs.single().id, action.id)
            }
            val getterCase = specs.singleOrNull()?.takeIf { spec ->
                spec.id == DIAGNOSTIC_GETTER_CASE && request.purpose == ExecutionPurpose.DIAGNOSTIC &&
                    input?.id == "origin" && branch.statePolicy == "isolated" &&
                    branch.outputCheckpoint == null && branchEpochs.size == 1 &&
                    diagnosticNegative == null && diagnosticPositive == null
            }
            val setupSealed = if (diagnosticSetup == null || branchEpochs.isEmpty()) null else {
                require(branchEpochs.size == 1 && branch.outputCheckpoint == diagnosticSetup.setupCheckpointId)
                sealIsolatedDiagnosticSetupTrial(store, diagnosticSetup, branchEpochs.single(),
                    campaignDeadline, request.budget.epochStopSealSeconds).also { child ->
                    event("diagnostic_setup_checkpoint_sealed", mapOf(
                        "manifest_sha256" to catalogue, "case_id" to diagnosticSetup.caseId,
                        "origin" to diagnosticSetup.origin, "checkpoint" to child,
                        "source_epoch" to branchEpochs.single().epoch.id,
                        "not_natural_or_published" to true))
                }
            }
            val diagnosticSeals = if (diagnosticPlan == null) emptyList() else branchEpochs.map { outcome ->
                sealIsolatedDiagnosticControlTrial(store, diagnosticPlan, outcome,
                    campaignDeadline, request.budget.epochStopSealSeconds).also { seal ->
                    event("diagnostic_control_child_sealed", mapOf("case_id" to seal.caseId,
                        "control_id" to seal.controlId, "source_epoch_id" to seal.sourceEpochId,
                        "setup" to seal.setup, "child" to seal.child,
                        "manifest_sha256" to catalogue))
                }
            }
            if (diagnosticPlan != null && diagnosticSeals.isNotEmpty())
                replayDiagnosticControlSeals(store, checkpointBound(events), diagnosticPlan,
                    branchEpochs.map { it.epoch })
            val positiveJoins = if (diagnosticPlan == null) emptyList() else diagnosticSeals.zip(branchEpochs)
                .map { (seal, outcome) -> runCatching {
                    replaySealedDiagnosticPositive(store, diagnosticPlan, seal, outcome)
                }.also { joined ->
                    event(if (joined.isSuccess) "diagnostic_positive_saved_join_bound"
                        else "diagnostic_positive_saved_join_rejected", mapOf(
                        "case_id" to diagnosticPlan.caseId, "control_id" to diagnosticPlan.controlId,
                        "setup" to diagnosticPlan.setup, "child" to seal.child,
                        "source_epoch_id" to outcome.epoch.id,
                        "manifest_sha256" to catalogue,
                        "reason" to joined.exceptionOrNull()?.message,
                        "not_natural_or_candidate_qualification" to true))
                } }
            val getterChild = if (getterCase == null) null else runCatching {
                sealIsolatedDiagnosticGetterTrial(store, requireNotNull(input), branchEpochs.single(),
                    campaignDeadline, request.budget.epochStopSealSeconds)
            }
            if (getterChild?.isSuccess == true) event("diagnostic_getter_child_sealed", mapOf(
                "case_id" to getterCase?.id, "origin" to input,
                "child" to getterChild.getOrThrow(),
                "source_epoch_id" to branchEpochs.single().epoch.id,
                "not_natural_or_published" to true))
            val getterJoin = if (getterChild?.isSuccess != true) null else runCatching {
                replaySealedDiagnosticGetter(store, requireNotNull(input), getterChild.getOrThrow(),
                    branchEpochs.single())
            }
            if (getterJoin?.isFailure == true || getterChild?.isFailure == true) event(
                "diagnostic_getter_saved_join_rejected", mapOf("case_id" to getterCase?.id,
                    "child" to getterChild?.getOrNull(),
                    "reason" to (getterJoin?.exceptionOrNull() ?: getterChild?.exceptionOrNull())?.message))
            if (getterJoin?.isSuccess == true) event("diagnostic_getter_saved_join_bound", mapOf(
                "case_id" to getterCase?.id, "child" to getterChild?.getOrNull(),
                "source_epoch_id" to branchEpochs.single().epoch.id,
                "not_natural_or_candidate_qualification" to true))
            val negativeChild = if (diagnosticNegative == null || branchEpochs.isEmpty()) null else
                runCatching { sealIsolatedDiagnosticNegativeTrial(store, diagnosticNegative,
                    branchEpochs.single(), campaignDeadline, request.budget.epochStopSealSeconds) }
            if (negativeChild?.isSuccess == true) event("diagnostic_negative_child_sealed", mapOf(
                "case_id" to diagnosticNegative?.caseId, "selector_id" to diagnosticNegative?.selectorId,
                "manifest_sha256" to catalogue, "setup" to diagnosticNegative?.setup,
                "child" to negativeChild.getOrThrow(),
                "source_epoch_id" to branchEpochs.single().epoch.id,
                "not_natural_or_published" to true))
            val negativeJoin = if (negativeChild?.isSuccess != true || diagnosticNegative == null) null else
                runCatching { replaySealedDiagnosticNegative(store, diagnosticNegative,
                    negativeChild.getOrThrow(), branchEpochs.single()) }
            if (negativeJoin?.isFailure == true || negativeChild?.isFailure == true) event(
                "diagnostic_negative_saved_join_rejected", mapOf(
                    "case_id" to diagnosticNegative?.caseId,
                    "child" to negativeChild?.getOrNull(),
                    "reason" to (negativeJoin?.exceptionOrNull() ?: negativeChild?.exceptionOrNull())?.message))
            if (negativeJoin?.isSuccess == true) event("diagnostic_negative_saved_join_bound", mapOf(
                "case_id" to diagnosticNegative?.caseId, "child" to negativeChild?.getOrNull(),
                "source_epoch_id" to branchEpochs.single().epoch.id,
                "manifest_sha256" to catalogue, "not_natural_or_candidate_qualification" to true))
            if (setupSealed != null) require(store.verify(setupSealed).parent == input)
            val setupJoin = if (setupSealed == null || diagnosticSetup == null) null else
                runCatching { replaySealedDiagnosticSetupControl(store, diagnosticSetup,
                    setupSealed, branchEpochs.single()) }
            if (setupJoin?.isFailure == true) event("diagnostic_setup_saved_join_rejected", mapOf(
                "case_id" to diagnosticSetup?.caseId, "checkpoint" to setupSealed,
                "reason" to setupJoin.exceptionOrNull()?.message))
            if (setupJoin?.isSuccess == true) event("diagnostic_setup_saved_join_bound", mapOf(
                "case_id" to diagnosticSetup?.caseId, "checkpoint" to setupSealed,
                "source_epoch" to branchEpochs.single().epoch.id,
                "not_natural" to true))
            val physicalSpec = specs.singleOrNull()?.takeIf { it.id == "primitive.harvest" &&
                it.implementation.state == "implemented" &&
                it.implementation.symbol == "PhysicalPrimitiveHarvestDriver" }
            // Exact physical expanded trials are independent siblings, never a merged branch state.
            // Even a behavior contradiction retains a safely closed disposable child for review;
            // absence of an independent saved join MUST block case success below.
            val physicalSeals = if (physicalSpec == null) emptyList() else branchEpochs.map { outcome ->
                val trialId = outcome.trials.singleOrNull()?.planned?.request?.trialId
                    ?: error("physical epoch lacked one actual trial")
                sealIsolatedPhysicalTrial(store, requireNotNull(input), outcome, trialId,
                    catalogue, campaignDeadline, request.budget.epochStopSealSeconds).also { seal ->
                    event("physical_trial_checkpoint_sealed", mapOf("case_id" to physicalSpec.id,
                        "trial_id" to seal.trialId, "input_checkpoint" to seal.input,
                        "output_checkpoint" to seal.output, "source_epoch_id" to seal.sourceEpochId,
                        "manifest_sha256" to catalogue))
                }
            }
            if (physicalSpec != null) require(physicalSeals.map { it.trialId } == listOf("correct", "wrong") &&
                physicalSeals.map { it.output }.distinct().size == 2 &&
                physicalSeals.all { it.input == input }) {
                "physical correct/wrong epochs were not two independently sealed siblings"
            }
            val pendingSavedNatural = mutableMapOf<String, Pair<List<ExecutedTrial>, String>>()
            for (spec in specs) {
                val trials = branchEpochs.flatMap { it.trials }.filter { it.planned.spec.id == spec.id }
                val ownEpochs = branchEpochs.filter { e -> e.trials.any { it.planned.spec.id == spec.id } }.map { it.reference }
                val driver = controllerDriver(spec, request.purpose)
                val verdict = if (trials.size != plans.getValue(spec.id).size)
                    MeasurementVerdict(if (trials.isEmpty()) "blocked" else "failed", "planned trials incomplete; no hidden retry")
                else if (driver is TrialCaseDriver) runCatching {
                    driver.aggregate(base.bindings.getValue(spec.id).path("parameters"), plans.getValue(spec.id).map { it.request }, trials.map { it.receipt })
                }.getOrElse { MeasurementVerdict("failed", "aggregate oracle rejected raw trials: ${it.message}") }
                else trials.single().verdict
                val preliminary = if (trials.any { it.receipt.state != "passed" } || branchEpochs.any { !it.safelyClosed })
                    "failed" else verdict.state
                // This case is NOT allowed to pass from a driver's live-only snapshot comparison.
                // Before recording success, bind the prior saved checkpoint, actual fresh process
                // birth/restore and first post-join native full41 snapshot through independent NBT.
                val reopened = if (spec.id == "control.persistence.vanilla-reopen" && preliminary == "passed")
                    runCatching {
                        require(trials.size == 1 && branchEpochs.size == 1 && input != null)
                        replayFreshVanillaReopenFromEpoch(store, input, branchEpochs.single().epoch)
                    }.exceptionOrNull() else null
                val state = if (reopened != null) "failed" else if (diagnosticPlan?.caseId == spec.id &&
                    (positiveJoins.size != branchEpochs.size || positiveJoins.isEmpty() ||
                        positiveJoins.any { it.isFailure })) "failed"
                    else if (getterCase?.id == spec.id &&
                    getterJoin?.isSuccess != true) "failed" // intentional STOP is not a saved getter receipt
                    else if (diagnosticNegative?.caseId == spec.id &&
                    negativeJoin?.isSuccess != true) "failed" // failed STOP alone is never a negative control receipt
                    else if (physicalSpec?.id == spec.id)
                    "blocked" // physical trials cannot pass from live-only facts
                    else if (diagnosticSetup?.caseId == spec.id && setupJoin?.isSuccess != true)
                    "failed" // retained sealed setup cannot publish without independent exact post-seal raw join
                    else preliminary
                if (spec.targetBindings.any { it.path("schema").asText() == "bc.playtest_target_selector.v2" }) {
                    require(spec.targetBindings.size == 1 && branch.outputCheckpoint != null &&
                        pendingSavedNatural.isEmpty()) { "one exact natural scoped source per sealed branch" }
                    pendingSavedNatural[spec.id] = trials to (if (state == "passed") verdict.reason else
                        "pre-seal reviewed source failed: ${verdict.reason}")
                    if (state != "passed") recordCase(spec, branch, input, trials, ownEpochs, state,
                        pendingSavedNatural.getValue(spec.id).second)
                } else recordCase(spec, branch, input, trials, ownEpochs, state,
                    if (physicalSpec?.id == spec.id)
                        "independent physical v5 raw/OS ancestor and each sealed saved tool/pickup join not yet installed; source-only trial outputs retained, never qualified"
                    else if (diagnosticPlan?.caseId == spec.id)
                        if (positiveJoins.isNotEmpty() && positiveJoins.all { it.isSuccess })
                            "precommitted positive diagnostic raw setter/STOP and direct Store child replayed; never natural/candidate qualification"
                        else "positive diagnostic post-seal raw/Store replay failed: ${positiveJoins.firstOrNull { it.isFailure }?.exceptionOrNull()?.message}"
                    else if (getterCase?.id == spec.id)
                        if (getterJoin?.isSuccess == true)
                            "predeclared origin getter failed only expected v4 scope, direct Store child remained unchanged; diagnostic only"
                        else "expected origin getter failed Store child/post-seal saved replay: ${getterJoin?.exceptionOrNull()?.message ?: getterChild?.exceptionOrNull()?.message}"
                    else if (diagnosticNegative?.caseId == spec.id)
                        if (negativeJoin?.isSuccess == true)
                            "precommitted expected negative, independently sealed failed STOP/raw/saved child; diagnostic only"
                        else "expected negative failed direct Store child/post-seal raw replay: ${negativeJoin?.exceptionOrNull()?.message ?: negativeChild?.exceptionOrNull()?.message}"
                    else if (diagnosticSetup?.caseId == spec.id)
                        if (setupJoin?.isSuccess == true) "precommitted real diagnostic setup setter and independent saved fixture join; never natural origin"
                        else "precommitted diagnostic setup failed independent post-seal raw/saved replay: ${setupJoin?.exceptionOrNull()?.message}"
                    else reopened?.let { "independent fresh-process saved vanilla reopen failed: ${it.message}" } ?: verdict.reason)
            }
            val beforeJoin = allClosed && branchEpochs.isNotEmpty() &&
                specs.all { states[it.id] == "passed" || (it.id in pendingSavedNatural && it.id !in states) } &&
                branchEpochs.all { it.epoch.status == "passed" && it.safelyClosed }
            if (!beforeJoin) pendingSavedNatural.forEach { (id, pair) ->
                if (id !in states) recordCase(specs.single { it.id == id }, branch, input,
                    pair.first, branchEpochs.filter { e -> e.trials.any { it.planned.spec.id == id } }
                        .map { it.reference }, "failed",
                    "reviewed scoped source incomplete or epoch unsafe; post-shutdown saved join not attempted")
            }
            var output: CheckpointRef? = null
            if (beforeJoin && branch.outputCheckpoint != null) {
                require(branchEpochs.size == 1) { "independent trial epochs cannot publish merged state" }
                val source = branchEpochs.single()
                val sealed = if (diagnosticSetup != null) {
                    require(branch.outputCheckpoint == diagnosticSetup.setupCheckpointId &&
                        setupJoin?.isSuccess == true && setupSealed != null)
                    setupSealed
                } else store.seal(branch.outputCheckpoint, source.roots, requireNotNull(source.epoch.closure), input)
                require(source.stopStartedAt != null && !Instant.now().isAfter(source.stopStartedAt.plusSeconds(
                    request.budget.epochStopSealSeconds)) && Instant.now().isBefore(campaignDeadline)) {
                    "branch stop/save/checkpoint seal exceeded reviewed lifecycle or campaign budget"
                }
                if (pendingSavedNatural.isNotEmpty()) {
                    event("checkpoint_sealed_pending_acquisition", mapOf("checkpoint" to sealed,
                        "source_epoch" to source.epoch.id))
                    val (id, pair) = pendingSavedNatural.entries.single().let { it.key to it.value }
                    val join = runCatching {
                        require(pair.first.size == 1 && input != null)
                        val trial = pair.first.single()
                        replaySealedNaturalDigJoin(store, input, sealed, source.epoch,
                            trial.planned.spec.id, trial.planned.request.trialId, trial.file, trial.raw)
                    }
                    val ownEpochs = listOf(source.reference)
                    val spec = specs.single { it.id == id }
                    if (join.isFailure) {
                        allClosed = false // Retain rejected sealed snapshot, but never publish it or restore next branch.
                        event("natural_saved_join_rejected", mapOf("checkpoint" to sealed,
                            "case_id" to id, "reason" to join.exceptionOrNull()?.message))
                        recordCase(spec, branch, input, pair.first, ownEpochs, "failed",
                            "post-shutdown independent vanilla saved acquisition join failed: ${join.exceptionOrNull()?.message}")
                    } else {
                        val receipt = root.resolve("case-${id}-natural-saved-join.json")
                        exclusiveJson(receipt, join.getOrThrow())
                        event("natural_saved_join_bound", mapOf("case_id" to id, "checkpoint" to sealed,
                            "join" to checkpointBound(receipt), "source_epoch" to source.epoch.id))
                        recordCase(spec, branch, input, pair.first, ownEpochs, "passed",
                            "reviewed scoped server-invoked acquisition passed independent saved vanilla join; not physical OS mining")
                    }
                }
                if (allClosed && specs.all { states[it.id] == "passed" }) {
                    output = sealed
                    checkpoints[sealed.id] = sealed
                    event("checkpoint_sealed", mapOf("checkpoint" to sealed, "source_epoch" to source.epoch.id))
                }
            }
            val passed = beforeJoin && specs.all { states[it.id] == "passed" } &&
                (branch.outputCheckpoint == null || output != null)
            val state = if (passed) "passed" else "failed"
            branchResults += mapOf("id" to branch.id, "state" to state, "input_checkpoint" to input, "output_checkpoint" to output,
                "epoch_ids" to branchEpochs.map { it.epoch.id }, "case_ids" to branch.caseIds)
            event("branch_completed", mapOf("branch_id" to branch.id, "state" to state, "output_checkpoint" to output))
        }
        require(Instant.now().isBefore(campaignDeadline) && states.size == base.cases.size && states.values.all { it == "passed" } && allClosed) { "campaign contains failed/blocked/inconclusive required coverage" }
    } catch (error: Throwable) { fail(error); event("failure", mapOf("message" to error.message)) }
    finally {
        // Even bootstrap/restore failures have explicit blocked case receipts; they cannot disappear from an index.
        request.branches.forEach { branch -> branch.caseIds.filter { it !in states }.forEach { id ->
            recordCase(base.cases.single { it.id == id }, branch, checkpoints[branch.inputCheckpoint], emptyList(), emptyList(),
                "blocked", "campaign aborted before this case; failure evidence retained")
        } }
        try {
            base.pair.assertUnchanged()
            require(Hashes.sha256(base.manifestPath) == catalogue && Hashes.sha256(base.support) == bindings.supportSha256 &&
                Hashes.sha256(request.launcherWheel) == request.launcherSha256 &&
                Hashes.sha256(request.sandboxExecutable) == request.sandboxSha256) { "campaign input changed" }
            ledger.verify()
        } catch (error: Throwable) { fail(error) }
        request.branches.filter { planned -> branchResults.none { it["id"] == planned.id } }.forEach { branch ->
            branchResults += mapOf("id" to branch.id, "state" to "blocked", "input_checkpoint" to checkpoints[branch.inputCheckpoint],
                "output_checkpoint" to null, "epoch_ids" to emptyList<String>(), "case_ids" to branch.caseIds)
        }
        val ledgerRef = runCatching { ledger.audit() }.onFailure(::fail).getOrNull()
        if (!Instant.now().isBefore(campaignDeadline)) fail(IllegalStateException("campaign hard deadline expired before receipt seal"))
        val status = if (failure != null || states.values.any { it != "passed" }) "failed" else if (base.diagnostic) "diagnostic" else "passed"
        event("campaign_finished", mapOf("status" to status))
        val campaign = root.resolve("campaign.json")
        exclusiveJson(campaign, mapOf("schema" to "bc.playtest_campaign.v3", "status" to status, "purpose" to request.purpose.wire,
            "diagnostic" to base.diagnostic,
            "campaign_id" to runId, "lineage_id" to lineage, "catalogue_sha256" to catalogue, "profile_sha256" to profileFingerprint(profile),
            "candidate" to candidateHashFields(base.pair), "test_support_sha256" to bindings.supportSha256, "bindings" to bindings,
            "tools" to m.path("tooling"), "branch_ids" to request.branches.map { it.id }, "branch_results" to branchResults,
            "epoch_receipts" to epochFiles, "checkpoint_receipts" to checkpoints.values.map { it.receipt }, "case_results" to caseFiles.values.toList(),
            "events" to checkpointBound(events), "ledger" to ledgerRef, "worlds_created" to if (worldCreated) 1 else 0,
            "storage_budget" to checkpointBound(storageFile), "execution_budget" to checkpointBound(timeBudgetFile),
            "campaign_deadline" to campaignDeadline.toString(), "hardware" to checkpointBound(hardwareFile), "fixture_overrides" to checkpointBound(overrideFile),
            "cleanup_complete" to allClosed, "started_at" to started.toString(), "completed_at" to Instant.now().toString(), "failure" to failure?.stackTraceToString()))
        // This is a partial index, intentionally incapable of qualifying without separately approved lifecycle/repository/GUI/log evidence.
        exclusiveJson(root.resolve("campaign-evidence-index.json"), mapOf("schema" to "bc.playtest_evidence_index.v3",
            "purpose" to request.purpose.wire, "catalogue_sha256" to catalogue, "campaign" to checkpointBound(campaign), "case_results" to caseFiles.values.toList(),
            "epochs" to epochFiles, "checkpoints" to checkpoints.values.map { it.receipt }, "lifecycle" to emptyMap<String, Any>(),
            "repository_receipts" to emptyList<Any>(), "visual_review" to null, "gui_review" to null, "client_frame_review" to null, "log_review" to null))
        evidence.finish(failure)
    }
    failure?.let { throw it }
}
