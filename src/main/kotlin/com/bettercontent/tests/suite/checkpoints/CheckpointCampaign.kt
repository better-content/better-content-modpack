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
                val driver = requireNotNull(DriverRegistry.resolve(spec.id))
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
            if (specs.size == 1 && requireNotNull(DriverRegistry.resolve(specs.single().id)) is TrialCaseDriver) {
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
            for (spec in specs) {
                val trials = branchEpochs.flatMap { it.trials }.filter { it.planned.spec.id == spec.id }
                val ownEpochs = branchEpochs.filter { e -> e.trials.any { it.planned.spec.id == spec.id } }.map { it.reference }
                val driver = requireNotNull(DriverRegistry.resolve(spec.id))
                val verdict = if (trials.size != plans.getValue(spec.id).size)
                    MeasurementVerdict(if (trials.isEmpty()) "blocked" else "failed", "planned trials incomplete; no hidden retry")
                else if (driver is TrialCaseDriver) runCatching {
                    driver.aggregate(base.bindings.getValue(spec.id).path("parameters"), plans.getValue(spec.id).map { it.request }, trials.map { it.receipt })
                }.getOrElse { MeasurementVerdict("failed", "aggregate oracle rejected raw trials: ${it.message}") }
                else trials.single().verdict
                val state = if (trials.any { it.receipt.state != "passed" } || branchEpochs.any { !it.safelyClosed }) "failed" else verdict.state
                recordCase(spec, branch, input, trials, ownEpochs, state, verdict.reason)
            }
            val passed = allClosed && branchEpochs.isNotEmpty() && specs.all { states[it.id] == "passed" } &&
                branchEpochs.all { it.epoch.status == "passed" && it.safelyClosed }
            var output: CheckpointRef? = null
            if (passed && branch.outputCheckpoint != null) {
                require(branchEpochs.size == 1) { "independent trial epochs cannot publish merged state" }
                val source = branchEpochs.single()
                output = store.seal(branch.outputCheckpoint, source.roots, requireNotNull(source.epoch.closure), input)
                checkpoints[output.id] = output
                require(source.stopStartedAt != null && !Instant.now().isAfter(source.stopStartedAt.plusSeconds(
                    request.budget.epochStopSealSeconds)) && Instant.now().isBefore(campaignDeadline)) {
                    "branch stop/save/checkpoint seal exceeded reviewed lifecycle or campaign budget"
                }
                event("checkpoint_sealed", mapOf("checkpoint" to output, "source_epoch" to source.epoch.id))
            }
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
