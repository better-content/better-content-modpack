package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.CandidateLocator
import com.bettercontent.tests.Hashes
import com.bettercontent.tests.release.validateOverworldTeleSoakReceipt
import com.bettercontent.tests.release.validateSmokeEvidence
import com.bettercontent.tests.release.validateTeleSoakReceipt
import com.bettercontent.tests.release.validateVisualReview
import com.bettercontent.tests.suite.*
import com.bettercontent.tests.suite.drivers.*
import com.bettercontent.tests.suite.client.CapturedClientFrame
import com.bettercontent.tests.suite.client.PlannedClientAction
import com.bettercontent.tests.suite.client.ClientInputRuntime
import com.bettercontent.tests.suite.client.authenticInputDrivers
import com.bettercontent.tests.suite.client.independentlyReviewedEmiHoverProjection
import com.bettercontent.tests.suite.client.resolveReviewedEmiMouseAction
import com.bettercontent.tests.suite.client.passiveObservationDrivers
import com.bettercontent.tests.suite.client.PassiveObserverArm
import com.bettercontent.tests.suite.client.resolveObservedActorAction
import com.bettercontent.tests.suite.client.resolvePassiveObserverAction
import com.bettercontent.tests.suite.client.readClientInputRuntime
import com.bettercontent.tests.suite.client.resolveInputRuntimeAction
import com.bettercontent.tests.suite.client.requireReviewedInputObserverSequence
import com.bettercontent.tests.suite.client.verifyClientFrameReview
import com.bettercontent.tests.suite.client.verifyV3ClientEvidence
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.time.Instant
import java.util.UUID

/** Read-only v3 gate. V1/v2 case/campaign/index receipts cannot become v3 by renaming files. */
fun qualifyV3Suite(root: Path, manifestPath: Path, indexPath: Path): Path {
    val manifestHash = Hashes.sha256(manifestPath)
    val indexHash = Hashes.sha256(indexPath)
    val m = suiteMapper.readTree(manifestPath.toFile())
    require(ExecutionPurpose.requireExplicit(m) == ExecutionPurpose.QUALIFICATION) { "diagnostic v3 manifest cannot qualify" }
    val defects = validateV3Authoring(root, m)
    require(defects.isEmpty()) { "v3 authoring/discovery incomplete:\n${defects.joinToString("\n")}" }
    val branches = orderedBranches(m)
    val cases = m.path("cases").map { suiteMapper.treeToValue(it, SuiteCase::class.java) }
    val baseline = v2LegacyShape(m, cases)
    val legacy = baseline.copy(candidate = baseline.candidate.copy(
        provenance = suiteMapper.treeToValue(m.path("candidate").path("provenance"), BoundFile::class.java),
        inventory = suiteMapper.treeToValue(m.path("candidate").path("inventory"), BoundFile::class.java)),
        repositories = m.path("repositories").map { repo ->
            val minimal = repo.deepCopy<ObjectNode>().apply { remove(listOf("reference", "test_reports")) }
            suiteMapper.treeToValue(minimal, RepositorySpec::class.java)
        })
    validateCandidateInventory(legacy, root)
    val caseById = cases.associateBy { it.id }
    val bindings = m.path("driver_bindings").associateBy { it.path("case_id").asText() }
    val campaignCases = cases.filter { it.layer == "campaign" }
    require(bindings.size == campaignCases.size && bindings.keys == campaignCases.map { it.id }.toSet()) { "incomplete v3 driver bindings" }
    val drivers = campaignCases.associate { spec ->
        val concrete = requireNotNull(DriverRegistry.resolve(spec.id)) { "no reviewed concrete driver: ${spec.id}" }
        val driver: TrialCaseDriver = if (concrete is TrialCaseDriver) concrete else SingleV3TrialDriver(concrete,
            spec.targets.singleOrNull() ?: spec.id)
        require(spec.id in DriverRegistry.supportedCases && driver.assertionIds == spec.assertions.toSet() &&
            spec.evidenceKinds.toSet() == setOf("native-interaction") && bindings.getValue(spec.id).path("driver_id").asText() == spec.id) {
            "unsupported or mismatched full-case oracle: ${spec.id}"
        }
        spec.id to driver
    }
    val client = boundInput(root, m.path("candidate").path("client"))
    val server = boundInput(root, m.path("candidate").path("server"))
    val pair = CandidateLocator.locate(client, server)
    val preflight = v3CampaignRequest(root, manifestPath, client, server, emptyList())
    require(preflight.base.pair.clientSha256 == pair.clientSha256 &&
        preflight.base.pair.serverSha256 == pair.serverSha256 &&
        preflight.branches == branches) { "qualification bypassed reviewed full v3 execution preflight" }
    val support = boundInput(root, m.path("test_support"))
    val wheel = boundInput(root, m.path("tooling").path("portablemc"))
    val sandbox = boundInput(root, m.path("tooling").path("bubblewrap"))
    require(Files.isExecutable(sandbox) && !Files.isSymbolicLink(sandbox)) { "hash-bound bubblewrap executable absent" }
    require(wheel.fileName.toString() == "portablemc-4.4.1-py3-none-any.whl" &&
        java.util.zip.ZipFile(wheel.toFile()).use { archive ->
            val metadata = archive.getEntry("portablemc-4.4.1.dist-info/METADATA") ?: return@use false
            val wheelInfo = archive.getEntry("portablemc-4.4.1.dist-info/WHEEL") ?: return@use false
            archive.getInputStream(metadata).bufferedReader().use { it.readText() }.contains("Version: 4.4.1") &&
                archive.getInputStream(wheelInfo).bufferedReader().use { it.readText() }.contains("Root-Is-Purelib: true")
        }) { "portablemc tool wheel not reviewed immutable version" }
    require(Files.isSameFile(support, boundInput(root, m.path("protocol").path("support_capabilities"))))
    val supportActions = supportCapabilities(support)
    val clientSupportActions = java.util.zip.ZipFile(support.toFile()).use { jar ->
        val entry = requireNotNull(jar.getEntry("META-INF/bc-playtest-support.json"))
        val descriptor = jar.getInputStream(entry).use { suiteMapper.readTree(it) }
        val clientDescriptor = descriptor.path("client_observation")
        require(clientDescriptor.path("protocol").asText() == "bc.playtest_action.v2" &&
            clientDescriptor.path("source").asText() == "client" &&
            clientDescriptor.path("bootstrap_properties").map { it.asText() }.toSet() == setOf(
                "bc.playtest.client.directory", "bc.playtest.client.runId", "bc.playtest.client.token") &&
            clientDescriptor.path("optional_emi_property").asText() == "bc.playtest.client.emiSha256") {
            "separate client observer capability descriptor absent"
        }
        clientDescriptor.path("supported_drivers").map { it.asText() }.toSet()
    }
    require(clientSupportActions == setOf("client.snapshot", "client.framebuffer", "client.emi_index",
        "client.emi_hover", "client.emi_page", "client.input_key", "client.input_click") +
        authenticInputDrivers &&
        (supportActions intersect clientSupportActions).isEmpty() &&
        supportActions.containsAll(setOf("inventory.discover", "fixture.cleanup")) &&
        campaignCases.all { spec ->
            val actions = drivers.getValue(spec.id).plan(bindings.getValue(spec.id).path("parameters"))
                .map { it.action }.toSet()
            actions.all { action -> action.source in setOf("server", "client") && action.id in
                (if (action.source == "server") supportActions else clientSupportActions) } &&
                (drivers.getValue(spec.id).clientRequired || actions.none { it.source == "client" }) &&
                bindings.getValue(spec.id).path("required_actions").map { it.asText() }.toSet() == actions.map { it.id }.toSet()
        }) { "v3 native support action absent or binding differs" }
    val base = indexPath.toAbsolutePath().parent
    val index = suiteMapper.readTree(indexPath.toFile())
    requireV3("evidence_index", index)
    require(index.path("purpose").asText() == ExecutionPurpose.QUALIFICATION.wire &&
        index.path("catalogue_sha256").asText() == manifestHash) { "diagnostic or stale evidence index" }
    val campaignPath = boundInput(base, index.path("campaign"))
    val campaignRoot = campaignPath.parent.toRealPath()
    val campaign = suiteMapper.readTree(campaignPath.toFile())
    requireV3("campaign", campaign)
    require(campaign.path("status").asText() == "passed" &&
        campaign.path("purpose").asText() == ExecutionPurpose.QUALIFICATION.wire && campaign.path("diagnostic").isBoolean &&
        !campaign.path("diagnostic").asBoolean() && campaign.path("catalogue_sha256").asText() == manifestHash &&
        campaign.path("profile_sha256").asText() == profileFingerprint(v2LegacyShape(m, cases).profile) &&
        campaign.path("worlds_created").asInt(-1) == 1 && campaign.path("failure").isNull) { "v3 campaign incomplete or diagnostic" }
    val campaignWheel = boundInput(root, campaign.path("tools").path("portablemc"))
    val campaignSandbox = boundInput(root, campaign.path("tools").path("bubblewrap"))
    require(Files.isSameFile(wheel, campaignWheel) && Files.isSameFile(sandbox, campaignSandbox) &&
        campaign.path("tools").fieldNames().asSequence().toSet() == setOf("portablemc", "bubblewrap")) {
        "campaign launchers differ from authored immutable tools"
    }
    val identity = suiteMapper.treeToValue(campaign.path("bindings"), CheckpointBindings::class.java)
    val campaignId = identity.campaignId; val lineageId = identity.lineageId
    require(campaign.path("bindings").path("purpose").asText() == ExecutionPurpose.QUALIFICATION.wire &&
        identity.purpose == ExecutionPurpose.QUALIFICATION.wire && campaignId.startsWith("campaign-") &&
        UUID.fromString(campaignId.removePrefix("campaign-")).toString() == campaignId.removePrefix("campaign-") &&
        UUID.fromString(lineageId).toString() == lineageId &&
        campaign.path("campaign_id").asText() == campaignId && campaign.path("lineage_id").asText() == lineageId &&
        campaign.path("cleanup_complete").isBoolean && campaign.path("cleanup_complete").asBoolean() &&
        campaign.path("candidate") == suiteMapper.valueToTree<JsonNode>(identity.candidate) &&
        campaign.path("branch_ids").map { it.asText() } == branches.map { it.id } &&
        identity.candidate == mapOf("client_sha256" to pair.clientSha256, "server_sha256" to pair.serverSha256) &&
        identity.supportSha256 == m.path("test_support").path("sha256").asText() &&
        identity.configurationSha256.matches(shaPattern) &&
        identity.configurationSha256 == jsonFingerprint(jsonObject("profile" to m.path("profile"),
            "protocol" to m.path("protocol"), "tooling" to m.path("tooling"),
            "execution_budget" to m.path("execution_budget"))) &&
        campaign.path("test_support_sha256").asText() == identity.supportSha256) { "checkpoint/candidate lineage mismatch" }
    val started = Instant.parse(campaign.path("started_at").asText()); val completed = Instant.parse(campaign.path("completed_at").asText())
    val reviewedBudget = preflight.budget
    val budgetDeadline = reviewedBudget.campaignDeadline(started)
    require(campaign.path("campaign_deadline").asText() == budgetDeadline.toString() &&
        !completed.isAfter(budgetDeadline) &&
        !started.isBefore(Instant.parse(m.path("authoring").path("reviewed_at").asText())) &&
        !completed.isBefore(started) && !completed.isAfter(Instant.now())) { "invalid v3 campaign interval or reviewed budget" }
    val executionBudget = suiteMapper.readTree(boundInput(campaignRoot, campaign.path("execution_budget")).toFile())
    require(executionBudget.path("schema").asText() == "bc.playtest_execution_budget_binding.v1" &&
        executionBudget.path("bindings") == campaign.path("bindings") &&
        executionBudget.path("manifest_sha256").asText() == manifestHash &&
        executionBudget.path("reviewed") == m.path("execution_budget") &&
        executionBudget.path("campaign_deadline").asText() == budgetDeadline.toString()) {
        "reviewed execution budget receipt missing or changed"
    }
    val hardware = suiteMapper.readTree(boundInput(campaignRoot, campaign.path("hardware")).toFile())
    require(hardware.path("schema").asText() == "bc.playtest_hardware.v2" &&
        hardware.path("profile").asText() == legacy.profile.hardwareProfile &&
        hardware.path("server_heap_mib").asInt(-1) == legacy.profile.serverHeapMib &&
        hardware.path("view_distance").asInt(-1) == 10 && hardware.path("simulation_distance").asInt(-1) == 10 &&
        listOf("cpu", "jvm", "contention_notes").all { hardware.path(it).asText().isNotBlank() }) {
        "v3 campaign actual hardware/profile missing"
    }
    val overrides = suiteMapper.readTree(boundInput(campaignRoot, campaign.path("fixture_overrides")).toFile())
    require(overrides.path("schema").asText() == "bc.playtest_fixture_overrides.v2" &&
        overrides.path("overrides").isArray && overrides.path("overrides").all { row ->
            row.path("setting").asText().isNotBlank() && row.path("purpose").asText().isNotBlank() &&
                row.has("value") && row.path("changes_asserted_behavior").isBoolean &&
                !row.path("changes_asserted_behavior").asBoolean()
        }) { "outcome-changing fixture override or missing disclosure" }
    val budgetFile = boundInput(campaignRoot, campaign.path("storage_budget"))
    val budget = suiteMapper.readTree(budgetFile.toFile())
    require(budget.path("schema").asText() == "bc.playtest_checkpoint_budget.v1" &&
        budget.path("bindings") == campaign.path("bindings") && budget.path("max_bytes").isIntegralNumber &&
        budget.path("max_bytes").asLong() == m.path("storage").path("checkpoint_budget_bytes").asLong() &&
        budget.path("keep_free_bytes").asLong() == 2L * 1024 * 1024 * 1024) { "checkpoint disk budget not exact" }
    // Store constructor is read-only; no directory is created while qualifying.
    require(Files.isDirectory(campaignRoot.resolve("checkpoints"))) { "checkpoint store missing" }
    val store = CheckpointStore(campaignRoot, identity)
    val checkpointRefs = index.path("checkpoints").also { require(it.isArray && !it.isEmpty) }.map { boundInput(base, it) }
    require(campaign.path("checkpoint_receipts").map { boundInput(campaignRoot, it).toRealPath() } ==
        checkpointRefs.map { it.toRealPath() }) { "campaign/index checkpoint receipts differ" }
    val checkpointDocs = checkpointRefs.map { suiteMapper.readTree(it.toFile()).also { doc ->
        require(doc.path("schema").asText() == "bc.playtest_checkpoint.v2" &&
            doc.path("bindings").path("purpose").asText() == ExecutionPurpose.QUALIFICATION.wire)
    } }
    val checkpoints = checkpointDocs.map { suiteMapper.treeToValue(it, CheckpointReceipt::class.java) }
    val checkpointById = checkpoints.associateBy { it.id }
    require(checkpointById.size == checkpoints.size && checkpointById.keys ==
        branches.mapNotNull { it.outputCheckpoint }.toSet() + "origin") { "missing/foreign checkpoint" }
    val refs = checkpoints.associate { doc -> doc.id to CheckpointRef(doc.id,
        BoundFile(checkpointRefs.single { it.parent.fileName.toString() == doc.id }.toString(), Hashes.sha256(checkpointRefs.single { it.parent.fileName.toString() == doc.id }))) }
    checkpoints.forEach { doc -> require(store.verify(refs.getValue(doc.id)) == doc) { "checkpoint bytes or lineage changed: ${doc.id}" } }
    require(checkpointById.getValue("origin").parent == null && checkpointById.getValue("origin").closedEpoch.saveValidated &&
        checkpointById.getValue("origin").files.none { file -> file.root == "server" &&
            listOf("world/playerdata/", "world/advancements/", "world/stats/").any { file.path.startsWith(it) } }) {
        "origin not a player-free sealed bootstrap"
    }
    val expectedParent = branches.mapNotNull { branch -> branch.outputCheckpoint?.let { it to branch.inputCheckpoint } }.toMap()
    expectedParent.forEach { (output, input) ->
        require(checkpointById.getValue(output).parent == refs.getValue(input)) { "checkpoint parent chain differs: $output" }
    }
    val epochs = index.path("epochs").also { require(it.isArray && !it.isEmpty) }.map { ref ->
        val path = boundInput(base, ref)
        val node = suiteMapper.readTree(path.toFile()); requireV3("epoch", node)
        require(node.path("purpose").asText() == ExecutionPurpose.QUALIFICATION.wire) { "diagnostic epoch cannot qualify" }
        suiteMapper.treeToValue(node.deepCopy<ObjectNode>().apply { remove("schema") }, SessionEpoch::class.java)
    }
    require(campaign.path("epoch_receipts").map { boundInput(campaignRoot, it).toRealPath() } ==
        index.path("epochs").map { boundInput(base, it).toRealPath() }) { "campaign/index epoch receipts differ" }
    val epochById = epochs.associateBy { it.id }
    require(epochById.size == epochs.size && epochs.map { it.nativeSessionId }.distinct().size == epochs.size &&
        epochs.count { it.branchId == "origin" } == 1 && epochs.all {
            it.id.matches(Regex("epoch-[0-9]{5}")) && it.nativeSessionId.startsWith("epoch-") &&
                runCatching { UUID.fromString(it.nativeSessionId.removePrefix("epoch-")).toString() ==
                    it.nativeSessionId.removePrefix("epoch-") }.getOrDefault(false) &&
            it.campaignId == campaignId && it.lineageId == lineageId &&
                it.purpose == ExecutionPurpose.QUALIFICATION.wire && it.status == "passed" &&
                !Instant.parse(it.completedAt).isBefore(Instant.parse(it.startedAt)) &&
                !Instant.parse(it.startedAt).isBefore(started) && !Instant.parse(it.completedAt).isAfter(completed)
        }) { "duplicate/unbound epoch or failed trial" }
    val origin = epochs.single { it.branchId == "origin" }
    require(origin.inputCheckpoint == null && origin.restored == null && origin.caseResults.isEmpty() &&
        origin.closure != null && origin.closure.saveValidated && origin.closure.processCleanupComplete &&
        origin.closure.survivingPids.isEmpty() && checkpointById.getValue("origin").sourceEpoch == origin.id) {
        "origin bootstrap must be player-free and sealed before branches"
    }
    require(epochs.filter { it.branchId != "origin" }.all { epoch ->
        epoch.branchId in branches.map { it.id } && epoch.inputCheckpoint != null &&
            epoch.inputCheckpoint == refs[branches.single { it.id == epoch.branchId }.inputCheckpoint] &&
            epoch.restored != null && store.verifyRestoreReceipt(epoch.restored) == epoch.restored &&
            epoch.restored.reference == epoch.inputCheckpoint && epoch.restored.epochId == epoch.id &&
            !Instant.parse(checkpointById.getValue(epoch.inputCheckpoint.id).sealedAt).isAfter(Instant.parse(epoch.startedAt)) &&
            epoch.closure != null && epoch.closure.saveValidated && epoch.closure.processCleanupComplete &&
            epoch.closure.survivingPids.isEmpty()
    }) { "epoch restore/source/closure mismatch" }
    require(epochs.minOf { Instant.parse(it.startedAt) } == Instant.parse(origin.startedAt) &&
        branches.all { branch -> epochs.any { it.branchId == branch.id } }) { "origin not first or branch missing epoch" }
    val caseFiles = index.path("case_results").also { require(it.isArray && it.size() == cases.size) }.map { boundInput(base, it) }
    val rawCases = caseFiles.map { path -> suiteMapper.readTree(path.toFile()).also { requireV3("case_result", it) } }
    require(campaign.path("case_results").map { boundInput(campaignRoot, it).toRealPath() }.toSet() ==
        caseFiles.zip(rawCases).filter { (_, row) -> caseById[row.path("case_id").asText()]?.layer == "campaign" }
            .map { (path, _) -> path.toRealPath() }.toSet()) { "campaign/index campaign-case receipts differ" }
    require(rawCases.map { it.path("case_id").asText() }.toSet() == caseById.keys && rawCases.size ==
        rawCases.map { it.path("case_id").asText() }.distinct().size) { "missing/duplicate v3 case receipts" }
    rawCases.zip(caseFiles).forEach { (row, file) ->
        val id = row.path("case_id").asText()
        if (caseById.getValue(id).layer == "campaign") require(row.path("purpose").asText() == ExecutionPurpose.QUALIFICATION.wire) {
            "diagnostic case cannot qualify: $id"
        }
        val old = row.deepCopy<ObjectNode>().apply {
            remove(listOf("purpose", "branch_id", "input_checkpoint", "epoch_receipts", "trials", "reason")); put("schema", "bc.playtest_case_result.v1")
        }
        val result = suiteMapper.treeToValue(old, CaseResult::class.java)
        val spec = caseById.getValue(id)
        val maximum = if (spec.layer == "campaign") {
            val perEpoch = Math.addExact(Math.addExact(reviewedBudget.epochStartSeconds,
                reviewedBudget.epochStopSealSeconds), reviewedBudget.trialSeconds(spec))
            java.time.Duration.ofSeconds(Math.addExact(reviewedBudget.campaignOverheadSeconds,
                Math.multiplyExact(perEpoch, reviewedBudget.case(spec).trialCount.toLong())))
        } else java.time.Duration.ofSeconds(spec.timeoutSeconds)
        validateCaseResult(legacy, spec, result, manifestHash, file.parent, maximumDuration = maximum)
        if (caseById.getValue(id).layer == "campaign") {
            require(result.runId == campaignId && result.worldId == lineageId) { "case reused a foreign epoch/world" }
        } else {
            require(row.path("branch_id").isNull && row.path("input_checkpoint").isNull &&
                row.path("epoch_receipts").isArray && row.path("epoch_receipts").isEmpty &&
                row.path("trials").isArray && row.path("trials").isEmpty) { "local case falsely acquired campaign lineage" }
        }
    }
    val branchResults = campaign.path("branch_results")
    require(branchResults.isArray && branchResults.size() == branches.size &&
        branchResults.map { it.path("id").asText() } == branches.map { it.id }) { "branch results missing/foreign/out of order" }
    val rawByCase = rawCases.associateBy { it.path("case_id").asText() }
    if ("survival.food" in caseById) {
        val onboarding = branches.single { "world.onboarding" in it.caseIds }
        val food = branches.single { "survival.food" in it.caseIds }
        val saved = checkpointById.getValue(requireNotNull(onboarding.outputCheckpoint))
        val sourceEpoch = epochById.getValue(saved.sourceEpoch)
        val actor = bindings.getValue("world.onboarding").path("parameters").path("first_join")
            .path("player_uuid").asText()
        require(onboarding.inputCheckpoint == "origin" && saved.parent == refs.getValue("origin") &&
            readSealedPlayerInventory(store.verify(refs.getValue(saved.id)), actor).savedGameModeId == 0 &&
            sourceEpoch.branchId == onboarding.id && sourceEpoch.caseResults.size == 1 &&
            rawByCase.getValue("world.onboarding").path("state").asText() == "passed" &&
            food.inputCheckpoint == saved.id && onboarding.id in food.dependsOn &&
            "world.onboarding" in caseById.getValue("survival.food").dependencies &&
            branchResults.single { it.path("id").asText() == onboarding.id }.path("state").asText() == "passed") {
            "food cannot inherit Survival from ordering-only or unrelated first-login evidence"
        }
    }
    val ledgerFile = boundInput(campaignRoot, campaign.path("ledger"))
    val ledgerLines = Files.readAllLines(ledgerFile).also { require(it.isNotEmpty() && it.all(String::isNotBlank)) }
    val ledger = SamplingLedger(campaignRoot, identity)
    require(ledgerFile.fileName.toString() == "sampling-ledger.jsonl" &&
        ledger.audit().sha256 == Hashes.sha256(ledgerFile)) { "sampling chain/anchors changed" }
    val claims = ledger.verify().associateBy { it.sequence }
    val claimTimes = ledgerLines.map { suiteMapper.readTree(it) }
        .associate { it.path("sequence").asLong() to Instant.parse(it.path("recorded_at").asText()) }
    require(claimTimes.keys == claims.keys) { "external sampling sequence/time mismatch" }
    val consumedClaims = mutableSetOf<Long>()
    val seenTrialIds = mutableSetOf<String>(); val consumedEpochs = mutableSetOf<String>(); val seenTokens = mutableSetOf<String>()
    val allCaseTrialRefs = mutableSetOf<Path>()
    val trialWitnesses = mutableMapOf<Path, V3TrialWitness>()
    for (branch in branches) {
        val report = branchResults.single { it.path("id").asText() == branch.id }
        require(report.path("state").asText() == "passed" && report.path("case_ids").map { it.asText() } == branch.caseIds &&
            suiteMapper.treeToValue(report.path("input_checkpoint"), CheckpointRef::class.java) == refs.getValue(branch.inputCheckpoint) &&
            (if (branch.outputCheckpoint == null) report.path("output_checkpoint").isNull else
                suiteMapper.treeToValue(report.path("output_checkpoint"), CheckpointRef::class.java) == refs.getValue(branch.outputCheckpoint))) {
            "branch receipt/checkpoint mismatch: ${branch.id}"
        }
        val branchEpochs = report.path("epoch_ids").map { it.asText() }
        if (branch.outputCheckpoint != null) require(checkpointById.getValue(branch.outputCheckpoint).sourceEpoch in branchEpochs) {
            "branch output checkpoint sourced from a foreign epoch"
        }
        require(branchEpochs.isNotEmpty() && branchEpochs.distinct().size == branchEpochs.size &&
            branchEpochs.all { epochById[it]?.branchId == branch.id && consumedEpochs.add(it) }) { "duplicate/foreign branch epoch: ${branch.id}" }
        for (caseId in branch.caseIds) {
            val row = rawByCase.getValue(caseId)
            val driver = drivers.getValue(caseId)
            require(caseId != "water.manual") {
                "water.manual cannot qualify from matching item ID alone: exact natural source ItemStack SHA/count must be proven in prerequisite raw acquisition and sealed input"
            }
            val parameters = bindings.getValue(caseId).path("parameters")
            require(row.path("branch_id").asText() == branch.id &&
                suiteMapper.treeToValue(row.path("input_checkpoint"), CheckpointRef::class.java) == refs.getValue(branch.inputCheckpoint)) {
                "case bound to wrong branch/input: $caseId"
            }
            val expanded = driver.expandTrials(parameters, branch.inputCheckpoint)
            if (DriverRegistry.resolve(caseId) is TrialCaseDriver) require(
                branch.statePolicy == "isolated" && branch.caseIds.size == 1 && branch.outputCheckpoint == null) {
                "independent trial targets cannot publish carry-forward state"
            }
            if (DriverRegistry.resolve(caseId) is TrialCaseDriver) require(branchEpochs.size == expanded.size) {
                "each isolated target must have a fresh restored epoch"
            }
            require(row.path("provisioned_targets").isArray &&
                row.path("provisioned_targets").map { it.asText() }.toSet() ==
                    expanded.flatMap { it.provisionedTargets }.toSet()) { "case hid fixture-provisioned targets" }
            require(expanded.isNotEmpty() && expanded.map { it.trialId }.distinct().size == expanded.size &&
                expanded.all { it.caseId == caseId && it.inputCheckpointId == branch.inputCheckpoint &&
                    it.assertionIds == driver.assertionIds }) {
                "trial roster incomplete: $caseId"
            }
            val trialRefs = row.path("trials").also { require(it.isArray && it.size() == expanded.size) }.map { boundInput(base, it) }
            require(row.path("epoch_receipts").isArray &&
                row.path("epoch_receipts").map { boundInput(base, it).toRealPath() }.toSet() ==
                branchEpochs.map { id -> index.path("epochs").map { boundInput(base, it).toRealPath() }
                    .single { suiteMapper.readTree(it.toFile()).path("id").asText() == id } }.toSet()) {
                "case epoch bindings differ from executed branch"
            }
            val observations = expanded.zip(trialRefs).map { (expected, trialFile) ->
                require(trialFile.toRealPath().startsWith(campaignRoot.resolve("epochs")) &&
                    allCaseTrialRefs.add(trialFile.toRealPath())) { "trial receipt foreign/reused across cases" }
                val doc = suiteMapper.readTree(trialFile.toFile()); requireV3("trial", doc)
                val trialId = doc.path("trial_id").asText(); val epochId = doc.path("epoch_id").asText()
                require(trialId == expected.trialId && doc.path("case_id").asText() == caseId &&
                    doc.path("target_id").asText() == expected.targetId && seenTrialIds.add("$caseId/$trialId") &&
                    epochId in branchEpochs && doc.path("native_session_id").asText() == epochById.getValue(epochId).nativeSessionId &&
                    suiteMapper.treeToValue(doc.path("input_checkpoint"), CheckpointRef::class.java) == refs.getValue(branch.inputCheckpoint) &&
                    doc.path("parameters_sha256").asText() == hashNode(expected.parameters) &&
                    doc.path("provisioned_targets").isArray &&
                    doc.path("provisioned_targets").map { it.asText() }.toSet() == expected.provisionedTargets &&
                    doc.path("state").asText() == "passed" && doc.path("failure").isNull && doc.path("cleanup_complete").asBoolean(false)) {
                    "trial identity/provenance/state mismatch: $trialId"
                }
                val authoredClaims = doc.path("sampling_claims")
                val typedIntents = (DriverRegistry.resolve(caseId) as? ExternalSamplingDriver)?.samplingIntents(expected)
                    ?: emptyList()
                require(authoredClaims.isArray && authoredClaims.size() == typedIntents.size) {
                    "trial external sampling claims differ from typed pre-action driver intentions"
                }
                val freshSites = if ((DriverRegistry.resolve(caseId) as? ExternalSamplingDriver)?.requireFreshTerrain == true)
                    typedIntents.filterIsInstance<SamplingIntent.Site>() else emptyList()
                if (freshSites.isNotEmpty()) verifyFreshTerrain(store.verify(refs.getValue(branch.inputCheckpoint)), freshSites)
                val ledgerPrefixSha = if (typedIntents.isEmpty()) {
                    require(doc.path("sampling_ledger").isNull) { "unclaimed trial invented an external sampling prefix" }
                    null
                } else {
                    val sequence = authoredClaims.last().path("sequence").asInt(-1)
                    require(sequence in 1..ledgerLines.size &&
                        authoredClaims.map { it.path("sequence").asInt(-1) }.zipWithNext().all { (a, b) -> b == a + 1 }) {
                        "trial sampling claims are not one contiguous pre-action ledger suffix"
                    }
                    val prefixInput = boundInput(base, doc.path("sampling_ledger"))
                    require(!Files.isSymbolicLink(prefixInput)) { "linked pre-action sampling ledger" }
                    val prefix = prefixInput.toRealPath()
                    require(prefix.parent == trialFile.toRealPath().parent &&
                        (Files.getAttribute(prefix, "unix:nlink", java.nio.file.LinkOption.NOFOLLOW_LINKS) as Number).toInt() == 1 &&
                        prefix.fileName.toString() == "trial-${caseId}-${trialId}-ledger.jsonl" &&
                        Files.readString(prefix) == ledgerLines.take(sequence).joinToString("\n", postfix = "\n")) {
                        "retained pre-action sampling ledger is not the exact final append-only prefix"
                    }
                    Hashes.sha256(prefix)
                }
                authoredClaims.zip(typedIntents).forEach { (item, intent) ->
                    val sequence = item.path("sequence").asLong(-1)
                    val claim = claims[sequence]
                    require(claim != null && item.path("sha256").asText() == claim.sha256 &&
                        claim.caseId == caseId && consumedClaims.add(sequence) &&
                        !claimTimes.getValue(sequence).isAfter(Instant.parse(doc.path("started_at").asText()))) {
                        "foreign/reused/post-hoc sampling claim"
                    }
                    when (intent) {
                        is SamplingIntent.Site -> require(claim.kind == "site" &&
                            claim.dimension == intent.dimension && claim.chunkX == intent.chunkX &&
                            claim.chunkZ == intent.chunkZ && claim.footprintRadiusChunks == intent.footprintRadiusChunks) {
                            "sampling footprint differs from typed source driver intention"
                        }
                        SamplingIntent.ThreatAttempt -> require(claim.kind == "threat_attempt" &&
                            claim.attempt != null && claim.attempt in 1..3 && claim.footprintRadiusChunks == null) {
                            "threat attempt differs from typed source driver intention"
                        }
                    }
                }
                val epoch = epochById.getValue(epochId)
                require(epoch.caseResults.any { ref -> boundInput(base, suiteMapper.valueToTree(ref)).toRealPath() == trialFile.toRealPath() } &&
                    !Instant.parse(doc.path("started_at").asText()).isBefore(Instant.parse(epoch.startedAt)) &&
                    !Instant.parse(doc.path("completed_at").asText()).isBefore(Instant.parse(doc.path("started_at").asText())) &&
                    !Instant.parse(doc.path("completed_at").asText()).isAfter(Instant.parse(epoch.completedAt))) {
                    "trial outside sealed epoch or missing from epoch results: $trialId"
                }
                val actionFile = boundInput(base, doc.path("native_actions"))
                require(actionFile.toRealPath().parent ==
                    trialFile.toRealPath().parent && actionFile.toRealPath().startsWith(campaignRoot.resolve("epochs").resolve(epochId))) {
                    "trial raw actions are not owned by its sealed epoch"
                }
                val native = Files.readAllLines(actionFile).also { require(it.isNotEmpty() && it.all(String::isNotBlank)) }
                    .map { line -> require(line.length <= PROTOCOL_LINE_LIMIT); suiteMapper.readTree(line) }
                val planned = driver.planTrial(expected)
                require(planned.size == native.size && planned.map { it.name }.distinct().size == planned.size)
                require(trialWitnesses.put(trialFile.toRealPath(), V3TrialWitness(driver, expected, actionFile.toRealPath(), ledgerPrefixSha)) == null)
                val observations = planned.zip(native).map { (step, line) ->
                    val item = line.path("observation")
                    if (step.action.id == "scale.begin") require(ledgerPrefixSha != null &&
                        item.path("payload").path("site_ledger_sha256").asText() == ledgerPrefixSha) {
                        "native scale echo differs from exact pre-action ledger prefix"
                    }
                    require(line.path("step").asText() == step.name && item.path("schema").asText() == "bc.playtest_observation.v2" &&
                        item.path("case_id").asText() == caseId && item.path("run_id").asText() ==
                            (if (step.action.source == "client") requireNotNull(epoch.clientStream).nativeSessionId else epoch.nativeSessionId) &&
                        item.path("driver").asText() == step.action.id && item.path("source").asText() == step.action.source &&
                        item.path("kind").asText() == "completed") { "trial raw step/source mismatch: $trialId/${step.name}" }
                    DriverObservation(step.name, step.action.id, step.action.source, "completed", item.path("payload"))
                }
                val nativeRoots = mutableMapOf("server" to boundInput(base, suiteMapper.valueToTree(epoch.nativeObservations)).parent)
                epoch.clientStream?.nativeObservations?.let { clientNative ->
                    nativeRoots["client"] = boundInput(base, suiteMapper.valueToTree(clientNative)).parent
                }
                val enriched = enrichRawEvidence(requireNotNull(DriverRegistry.resolve(caseId)), expected,
                    observations, nativeRoots)
                requireV3TrialPassed(driver, expected, enriched, doc.path("state").asText())
                TrialReceipt(trialId, expected.targetId, branch.inputCheckpoint, epochId, "passed", enriched,
                    inputCheckpointVerified = store.verify(refs.getValue(branch.inputCheckpoint)).id == branch.inputCheckpoint &&
                        store.verifyRestoreReceipt(requireNotNull(epoch.restored)) == epoch.restored,
                    cleanupComplete = epoch.closure?.processCleanupComplete == true)
            }
            if (DriverRegistry.resolve(caseId) is TrialCaseDriver) require(observations.map { it.epochId }.toSet() == branchEpochs.toSet()) {
                "multiple isolated trials reused the same restored epoch"
            }
            val caseFile = caseFiles.single { suiteMapper.readTree(it.toFile()).path("case_id").asText() == caseId }
            val assertedNative = row.path("assertions").flatMap { assertion -> assertion.path("evidence").filter {
                it.path("kind").asText() == "native-interaction"
            }.map { evidence -> boundInput(caseFile.parent, evidence.path("file")).toRealPath() } }.toSet()
            require(assertedNative == trialRefs.map { trialWitnesses.getValue(it.toRealPath()).rawFile }.toSet()) {
                "assertion cites unrelated native raw evidence: $caseId"
            }
            if (caseId == "control.persistence.vanilla-reopen") {
                // C intentionally reports live-only INCONCLUSIVE. The independently saved,
                // first-action, fresh-process equality is replayed below from this epoch's
                // raw request/terminal and sealed input, not promoted by the driver.
                require(observations.size == 1 && observations.single().state == "passed" &&
                    driver.evaluateTrial(expanded.single(), observations.single().observations).state == "inconclusive") {
                    "reopen driver falsely certified sealed persistence from a live-only view"
                }
            } else require(driver.aggregate(parameters, expanded, observations).state == "passed") {
                "independent full-roster aggregate failed: $caseId"
            }
        }
    }
    require(consumedClaims == claims.keys && consumedEpochs == epochById.keys - origin.id && allCaseTrialRefs ==
        epochs.filter { it.branchId != "origin" }.flatMap { epoch -> epoch.caseResults.map { boundInput(base, suiteMapper.valueToTree(it)).toRealPath() } }.toSet()) {
        "orphan/extra epoch or trial"
    }
    verifyV3CampaignEvents(campaignRoot, campaign.path("events"), branches, epochs, checkpoints,
        campaignCases, reviewedBudget)
    val chronological = epochs.sortedBy { Instant.parse(it.startedAt) }
    require(chronological.first().id == origin.id && chronological.zipWithNext().all { (prior, next) ->
        !Instant.parse(prior.completedAt).isAfter(Instant.parse(next.startedAt))
    }) { "restored epochs overlapped or preceded origin" }
    chronological.forEach { epoch -> verifyV3Epoch(epoch, campaignRoot, base, m, trialWitnesses, seenTokens, store,
        client, server, support, reviewedBudget,
        checkpoints.singleOrNull { it.sourceEpoch == epoch.id }?.let { refs.getValue(it.id) }) }
    // Saved acquisition cannot be promoted by a live native success flag: compare the case's
    // independent post-shutdown Store/playerdata/source-absence replay with the sealed receipt.
    val naturalCases = campaignCases.filter { spec -> spec.targetBindings.any {
        it.path("schema").asText() == "bc.playtest_target_selector.v2" } }
    val campaignEventRows = Files.readAllLines(boundInput(campaignRoot, campaign.path("events")))
        .also { require(it.isNotEmpty() && it.all(String::isNotBlank)) }.map(suiteMapper::readTree)
    val joins = campaignEventRows.filter { it.path("type").asText() == "natural_saved_join_bound" }
    val pendingSeals = campaignEventRows.filter { it.path("type").asText() == "checkpoint_sealed_pending_acquisition" }
    require(joins.size == naturalCases.size && pendingSeals.size == joins.size)
    naturalCases.forEach { spec ->
        val joinEvent = joins.single { it.path("case_id").asText() == spec.id }
        val epoch = chronological.single { it.caseResults.any { ref ->
            val trial = suiteMapper.readTree(boundInput(base, suiteMapper.valueToTree(ref)).toFile())
            trial.path("case_id").asText() == spec.id } }
        val ref = epoch.caseResults.single { caseRef ->
            suiteMapper.readTree(boundInput(base, suiteMapper.valueToTree(caseRef)).toFile())
                .path("case_id").asText() == spec.id }
        val trial = suiteMapper.readTree(boundInput(base, suiteMapper.valueToTree(ref)).toFile())
        val output = refs.getValue(checkpoints.single { it.sourceEpoch == epoch.id }.id)
        val input = requireNotNull(epoch.inputCheckpoint)
        val joinFile = boundInput(base, joinEvent.path("join"))
        val pending = pendingSeals.single { it.path("source_epoch").asText() == epoch.id }
        val pendingIndex = campaignEventRows.indexOf(pending)
        val joinIndex = campaignEventRows.indexOf(joinEvent)
        require(joinEvent.path("checkpoint") == suiteMapper.valueToTree<JsonNode>(output) &&
            pending.path("checkpoint") == suiteMapper.valueToTree<JsonNode>(output) &&
            joinEvent.path("source_epoch").asText() == epoch.id &&
            joinFile.parent == campaignRoot &&
            joinFile.fileName.toString() == "case-${spec.id}-natural-saved-join.json" &&
            pendingIndex < joinIndex &&
            joinIndex < campaignEventRows.indexOfFirst { it.path("type").asText() == "case_completed" &&
                it.path("case_id").asText() == spec.id } &&
            joinIndex < campaignEventRows.indexOfFirst { it.path("type").asText() == "checkpoint_sealed" &&
                it.path("checkpoint").path("id").asText() == output.id })
        val saved = replaySealedNaturalDigJoin(store, input, output, epoch, spec.id,
            trial.path("trial_id").asText(), ref,
            suiteMapper.treeToValue(trial.path("native_actions"), BoundFile::class.java))
        require(suiteMapper.readTree(joinFile.toFile()) == suiteMapper.valueToTree<JsonNode>(saved)) {
            "post-shutdown saved acquisition receipt differs from independent native/playerdata replay"
        }
    }
    val verifiedClientRequests = chronological.mapNotNull { it.clientStream?.nativeRequests }.flatMap { ref ->
        Files.readAllLines(boundInput(base, suiteMapper.valueToTree(ref))).filter(String::isNotBlank).map(suiteMapper::readTree)
    }
    val observedClientActions = verifiedClientRequests.map { it.path("driver").asText() }.toSet()
    val frames = chronological.flatMap { epoch -> epoch.caseResults.flatMap { ref ->
        val witness = trialWitnesses.getValue(boundInput(base, suiteMapper.valueToTree(ref)).toRealPath())
        val lines = Files.readAllLines(witness.rawFile).map { suiteMapper.readTree(it) }
        witness.driver.planTrial(witness.request).zip(lines).mapNotNull { (step, line) ->
            if (step.action.id != "client.framebuffer") null else {
                val terminal = line.path("observation")
                val payload = terminal.path("payload")
                val capturedAt = Instant.parse(payload.path("captured_at").asText())
                require(step.action.source == "client" &&
                    !capturedAt.isBefore(Instant.parse(epoch.startedAt)) &&
                    !capturedAt.isAfter(Instant.parse(epoch.completedAt))) { "frame was not captured by joined epoch client" }
                CapturedClientFrame(epoch.id, witness.request.caseId, witness.request.trialId,
                    terminal.path("request_sequence").asLong(-1), step.action.parameters.path("profile").asText(),
                    payload.path("image_sha256").asText(), payload.path("screen_class").asText(),
                    payload.path("menu_type").takeIf { it.isTextual }?.asText(), payload.path("dimension").asText(),
                    payload.path("player_uuid").asText())
            }
        }
    } }
    if (cases.any { it.layer == "visual" }) require(
        m.path("profile").path("visual_profiles").map { it.asText() }.toSet() ==
            verifiedClientRequests.filter { it.path("driver").asText() == "client.framebuffer" }
                .map { it.path("parameters").path("profile").asText() }.toSet() &&
            "client.snapshot" in observedClientActions &&
            observedClientActions.any { it in setOf("client.input_key", "client.input_click") }) {
        "visual review cannot substitute for actual bound v3 client frames at every reviewed display profile and input callbacks"
    }
    if (cases.any { "emi" in it.noteIds }) require("client.emi_index" in observedClientActions) {
        "v3 EMI evidence requires the actual client public-API index, not an imported v2 assertion"
    }
    // Keep clean, uninstrumented lifecycle evidence separate from every v3 campaign epoch.
    val lifecycle = index.path("lifecycle"); require(lifecycle.fieldNames().asSequence().toSet() == setOf("smoke", "overworld-tele-soak", "tele-soak"))
    val lifecycleLogs = mutableSetOf<Path>()
    for (tier in listOf("smoke", "overworld-tele-soak", "tele-soak")) {
        val file = boundInput(base, lifecycle.path(tier)); val receipt = suiteMapper.readTree(file.toFile())
        val run = receipt.path("run_id").asText()
        require(run.isNotBlank() && run != campaignId && receipt.path("status").asText() == "passed" &&
            !receipt.path("profile").asText().contains("test-support"))
        when (tier) {
            "smoke" -> validateSmokeEvidence(file, run, pair, started)
            "overworld-tele-soak" -> validateOverworldTeleSoakReceipt(file, run, pair, started)
            else -> validateTeleSoakReceipt(file, run, pair, started)
        }
        lifecycleLogs.add(file.parent.resolve("server.log").toRealPath())
        lifecycleLogs.add(file.parent.resolve("client.log").toRealPath())
    }
    val visualPath = boundInput(base, index.path("visual_review"))
    val epochWorlds = epochs.mapNotNull { it.restored?.roots?.get("server")?.let { root ->
        Path.of(root).resolve("world").toRealPath().toString()
    } }.toSet() + Path.of(checkpointById.getValue("origin").snapshotDirectory)
        .resolve("server/world").toRealPath().toString()
    val visual = validateVisualReview(visualPath, index.path("visual_release_run_id").asText(), pair, started, epochWorlds)
    val visualDoc = suiteMapper.readTree(visualPath.toFile())
    require(visualDoc.path("reviewer").path("name").asText().isNotBlank() &&
        visualDoc.path("reviewer").path("name").asText() != m.path("authoring").path("reviewer").asText() &&
        cases.filter { it.layer == "visual" }.all { spec ->
            val receipt = rawByCase.getValue(spec.id)
            receipt.path("run_id").asText() == visual.sessionId && receipt.path("world_id").asText() == visual.ownershipId
        }) { "visual case or reviewer not independent from authored campaign" }
    val session = boundInput(visualPath.parent, visualDoc.path("capture_session"))
    val visualCapture = suiteMapper.readTree(session.toFile())
    validateGuiChecklist(legacy, boundInput(base, index.path("gui_review")), manifestHash, visual.sessionId,
        visualDoc.path("reviewer").path("name").asText(), session)
    val frameReview = index.path("client_frame_review")
    if (frames.isEmpty()) require(frameReview.isNull) { "unplanned client-frame approval supplied" }
    else {
        val reviewFile = boundInput(base, frameReview)
        verifyClientFrameReview(suiteMapper.readTree(reviewFile.toFile()), frames, campaignId, lineageId,
            manifestHash, identity.candidate, m.path("authoring").path("reviewer").asText(), completed,
            m.path("rosters").single { it.path("kind").asText() == "gui" }.path("entries")
                .fieldNames().asSequence().toSet(), m.path("profile").path("visual_profiles").map { it.asText() }.toSet())
    }
    val visualLogs = listOf("input_log", "server_log", "client_log").map { name ->
        val owner = listOf(visualDoc, visualCapture).singleOrNull { it.has(name) } ?: error("visual $name binding missing/ambiguous")
        boundInput(session.parent, owner.path(name)).toRealPath()
    }.toSet()
    val repoRefs = index.path("repository_receipts")
    require(repoRefs.fieldNames().asSequence().toSet() == m.path("repositories").map { it.path("id").asText() }.toSet())
    val repositoryLogs = m.path("repositories").flatMap { repo ->
        validateV2RepositoryReceipt(base, repoRefs.path(repo.path("id").asText()), repo,
            mapOf("client_sha256" to pair.clientSha256, "server_sha256" to pair.serverSha256))
    }.toSet()
    val reviewFile = boundInput(base, index.path("log_review")); val review = suiteMapper.readTree(reviewFile.toFile())
    requireV2ReceiptSchema("log_review", review)
    require(review.path("status").asText() == "passed" && review.path("catalogue_sha256").asText() == manifestHash &&
        review.path("reviewer").asText().isNotBlank() &&
        !Instant.parse(review.path("reviewed_at").asText()).isBefore(completed) &&
        !Instant.parse(review.path("reviewed_at").asText()).isAfter(Instant.now()) &&
        review.path("sources").isArray && !review.path("sources").isEmpty &&
        review.path("unresolved_blockers").isArray && review.path("unresolved_blockers").isEmpty)
    val epochLogs = epochs.flatMap { epoch ->
        val event = boundInput(base, suiteMapper.valueToTree(epoch.eventLog))
        listOf(event.parent.resolve("server.log").toRealPath()) +
            (if (epoch.clientStream == null) emptyList() else listOf(event.parent.resolve("client.log").toRealPath()))
    }.toSet()
    validateV2LogSignatures(reviewFile.parent, review, lifecycleLogs + visualLogs + repositoryLogs + epochLogs)
    // Recheck the exact bound sources after traversal; no partial/rewound receipt may slip into a ready marker.
    val refsToRecheck = listOf(index.path("campaign"), index.path("visual_review"), index.path("gui_review"),
        index.path("log_review")) + listOfNotNull(index.path("client_frame_review").takeUnless { it.isNull }) + index.path("case_results").toList() + index.path("epochs").toList() +
        index.path("checkpoints").toList() + index.path("lifecycle").elements().asSequence().toList() +
        index.path("repository_receipts").elements().asSequence().toList()
    refsToRecheck.forEach { boundInput(base, it) }
    listOf("hardware", "fixture_overrides", "storage_budget", "events", "ledger").forEach { key ->
        boundInput(campaignRoot, campaign.path(key))
    }
    rawCases.flatMap { it.path("trials").toList() }.forEach { ref ->
        val file = boundInput(base, ref)
        boundInput(base, suiteMapper.readTree(file.toFile()).path("native_actions"))
    }
    require(ledger.audit().sha256 == Hashes.sha256(ledgerFile))
    boundInput(root, m.path("tooling").path("portablemc"))
    boundInput(root, m.path("tooling").path("bubblewrap"))
    boundInput(root, m.path("test_support"))
    pair.assertUnchanged()
    require(Hashes.sha256(manifestPath) == manifestHash && Hashes.sha256(indexPath) == indexHash)
    val output = base.resolve("playtest-qualified-v3.json")
    require(!Files.exists(output)) { "v3 qualification already exists" }
    val pending = base.resolve(".playtest-qualified-v3-${UUID.randomUUID()}.pending")
    try {
        Files.writeString(pending, suiteMapper.writeValueAsString(mapOf("schema" to "bc.playtest_qualification.v3",
            "status" to "qualified", "catalogue_sha256" to manifestHash,
            "candidate" to pair.document(), "evidence_index_sha256" to indexHash,
            "created_at" to Instant.now().toString())) + "\n", CREATE_NEW)
        Files.move(pending, output)
    } finally { Files.deleteIfExists(pending) }
    return output
}

internal fun requireV3TrialPassed(driver: TrialCaseDriver, request: TrialRequest,
    observations: List<DriverObservation>, reportedState: String) {
    require(reportedState == "passed" &&
        validateTerminalSequence(driver.planTrial(request), observations) == null &&
        driver.evaluateTrial(request, observations).state ==
            (if (request.caseId == "control.persistence.vanilla-reopen") "inconclusive" else "passed")) {
        "self-reported pass without complete independent trial oracle: ${request.trialId}"
    }
}

private class SingleV3TrialDriver(private val source: CaseDriver, private val target: String) : TrialCaseDriver {
    override val clientRequired: Boolean get() = source.clientRequired
    override val caseId: String get() = source.caseId
    override val assertionIds: Set<String> get() = source.assertionIds
    override fun fixtureProvisionedTargets(parameters: JsonNode) = source.fixtureProvisionedTargets(parameters)
    override fun requiredAcquisitionCases(parameters: JsonNode) = source.requiredAcquisitionCases(parameters)
    override fun plan(parameters: JsonNode) = source.plan(parameters)
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>) = source.evaluate(parameters, observations)
    override fun expandTrials(parameters: JsonNode, inputCheckpointId: String): List<TrialRequest> =
        listOf(TrialRequest(caseId, "single", target, inputCheckpointId,
            parameters, assertionIds, source.fixtureProvisionedTargets(parameters)))
    override fun planTrial(request: TrialRequest): List<DriverStep> = source.plan(request.parameters)
    override fun evaluateTrial(request: TrialRequest, observations: List<DriverObservation>): MeasurementVerdict =
        source.evaluate(request.parameters, observations)
    override fun aggregate(parameters: JsonNode, requests: List<TrialRequest>, receipts: List<TrialReceipt>): MeasurementVerdict {
        require(requests.size == 1 && receipts.size == 1 && receipts.single().trialId == requests.single().trialId &&
            receipts.single().inputCheckpointId == requests.single().inputCheckpointId && receipts.single().inputCheckpointVerified &&
            receipts.single().cleanupComplete && receipts.single().state == "passed") { "single-case trial reused or unverified" }
        return evaluateTrial(requests.single(), receipts.single().observations)
    }
}

private fun verifyV3CampaignEvents(root: Path, binding: JsonNode, branches: List<BranchSpec>,
    epochs: List<SessionEpoch>, checkpoints: List<CheckpointReceipt>, cases: List<SuiteCase>,
    budget: ReviewedExecutionBudget) {
    val eventFile = boundInput(root, binding)
    require(Files.size(eventFile) in 1..10_000_000)
    val rows = Files.readAllLines(eventFile).also { require(it.isNotEmpty() && it.all(String::isNotBlank)) }
        .map { line -> require(line.length <= PROTOCOL_LINE_LIMIT); suiteMapper.readTree(line) }
    val timestamps = rows.map { Instant.parse(it.path("time").asText()) }
    val campaignId = rows.first().path("campaign_id").asText()
    val lineageId = rows.first().path("lineage_id").asText()
    require(campaignId == epochs.first().campaignId && lineageId == epochs.first().lineageId &&
        rows.first().path("type").asText() == "campaign_started" &&
        rows.last().path("type").asText() == "campaign_finished" &&
        rows.last().path("status").asText() == "passed" &&
        rows.all { it.path("campaign_id").asText() == campaignId && it.path("lineage_id").asText() == lineageId } &&
        timestamps.zipWithNext().all { (left, right) -> !right.isBefore(left) }) { "campaign event identity/time/closure changed" }
    fun one(type: String, idField: String, id: String): Int = rows.indices.filter { index ->
        rows[index].path("type").asText() == type && rows[index].path(idField).asText() == id
    }.single()
    branches.forEach { branch ->
        val start = one("branch_started", "branch_id", branch.id)
        val end = one("branch_completed", "branch_id", branch.id)
        require(start < end && rows[end].path("state").asText() == "passed" &&
            (branch.dependsOn + branch.orderingDependencies).all { dependency ->
                one("branch_completed", "branch_id", dependency) < start
            }) { "branch ran before its ancestral or ordering-only prerequisite passed" }
        epochs.filter { it.branchId == branch.id }.forEach { epoch ->
            val restored = one("epoch_restored", "epoch_id", epoch.id)
            val began = one("epoch_started", "epoch_id", epoch.id)
            val closed = one("epoch_closed", "epoch_id", epoch.id)
            require(start < restored && restored < began && began < closed && closed < end &&
                rows[closed].path("status").asText() == "passed" && rows[closed].path("safely_closed").asBoolean(false)) {
                "epoch restore/start/closure not bracketed by branch: ${epoch.id}"
            }
        }
        branch.caseIds.forEach { caseId ->
            require(start < one("case_started", "case_id", caseId) &&
                one("case_started", "case_id", caseId) < one("case_completed", "case_id", caseId) &&
                one("case_completed", "case_id", caseId) < end &&
                rows[one("case_completed", "case_id", caseId)].path("state").asText() == "passed") {
                "case not completed with a passing source receipt within branch" }
        }
    }
    require(branches.zipWithNext().all { (prior, next) ->
        one("branch_completed", "branch_id", prior.id) < one("branch_started", "branch_id", next.id)
    }) { "v3 branches did not execute in exact reviewed topological order" }
    val origin = epochs.single { it.branchId == "origin" }
    val prepared = one("epoch_prepared", "epoch_id", origin.id)
    val started = one("epoch_started", "epoch_id", origin.id)
    val closed = one("epoch_closed", "epoch_id", origin.id)
    fun sealed(id: String): Int = rows.indices.filter { rows[it].path("type").asText() == "checkpoint_sealed" &&
        rows[it].path("checkpoint").path("id").asText() == id }.single()
    val created = one("world_created", "epoch_id", origin.id)
    require(prepared < created && created < started && started < closed && closed < sealed("origin") &&
        rows[closed].path("status").asText() == "passed" && rows[closed].path("safely_closed").asBoolean(false) &&
        branches.all { closed < one("branch_started", "branch_id", it.id) }) { "origin sealed after branch or before clean closure" }
    checkpoints.forEach { cp ->
        val sealed = sealed(cp.id)
        require(cp.id == "origin" || epochs.any { it.id == cp.sourceEpoch &&
            one("epoch_closed", "epoch_id", it.id) < sealed }) { "checkpoint before its source epoch closed" }
    }
    val expectedTrials = epochs.sortedBy { Instant.parse(it.startedAt) }.flatMap { epoch ->
        epoch.caseResults.map { ref ->
            val receipt = suiteMapper.readTree(boundInput(root, suiteMapper.valueToTree(ref)).toFile())
            require(receipt.path("schema").asText() == "bc.playtest_trial.v3" &&
                receipt.path("epoch_id").asText() == epoch.id && receipt.path("state").asText() == "passed" &&
                receipt.path("cleanup_complete").asBoolean(false) && receipt.path("failure").isNull) {
                "campaign trial receipt cannot hide a raw failed action"
            }
            Triple(epoch, ref, receipt)
        }
    }
    val startedTrials = rows.withIndex().filter { it.value.path("type").asText() == "trial_started" }
    val completedTrials = rows.withIndex().filter { it.value.path("type").asText() == "trial_completed" }
    require(startedTrials.size == expectedTrials.size && completedTrials.size == expectedTrials.size)
    expectedTrials.forEachIndexed { ordinal, (epoch, ref, receipt) ->
        val startedEvent = startedTrials[ordinal]; val completedEvent = completedTrials[ordinal]
        val caseId = receipt.path("case_id").asText(); val trialId = receipt.path("trial_id").asText()
        val spec = cases.single { it.id == caseId }
        require(!Instant.parse(receipt.path("completed_at").asText()).isAfter(
            Instant.parse(receipt.path("started_at").asText()).plusSeconds(budget.trialSeconds(spec))) &&
            listOf(startedEvent.value, completedEvent.value).all { row ->
            row.path("case_id").asText() == caseId && row.path("trial_id").asText() == trialId &&
                row.path("epoch_id").asText() == epoch.id
        } && one("epoch_started", "epoch_id", epoch.id) < startedEvent.index &&
            startedEvent.index < completedEvent.index && completedEvent.index < one("epoch_closed", "epoch_id", epoch.id) &&
            completedEvent.value.path("state").asText() == "passed" &&
            completedEvent.value.path("cleanup_complete").isBoolean &&
            completedEvent.value.path("cleanup_complete").asBoolean() &&
            completedEvent.value.path("receipt") == suiteMapper.valueToTree<JsonNode>(ref)) {
            "trial completion event is reordered, failed, lacks cleanup, or differs from the exact bound trial receipt"
        }
    }
    require(rows.none { it.path("type").asText() in setOf("failure", "case_blocked", "case_failed", "branch_failed", "epoch_failed",
        "restore_failed", "epoch_aborted", "branch_aborted", "campaign_blocked", "campaign_failed") } &&
        rows.count { it.path("type").asText() == "case_started" } == cases.size &&
        rows.count { it.path("type").asText() == "case_completed" } == cases.size &&
        rows.count { it.path("type").asText() == "branch_started" } == branches.size &&
        rows.count { it.path("type").asText() == "branch_completed" } == branches.size &&
        rows.count { it.path("type").asText() == "checkpoint_sealed" } == checkpoints.size &&
        rows.count { it.path("type").asText() == "world_created" } == 1 &&
        rows.count { it.path("type").asText() == "epoch_prepared" } == 1 &&
        rows.count { it.path("type").asText() == "epoch_restored" } == epochs.size - 1 &&
        rows.count { it.path("type").asText() == "epoch_started" } == epochs.size &&
        rows.count { it.path("type").asText() == "epoch_closed" } == epochs.size &&
        rows.count { it.path("type").asText() == "trial_started" } == epochs.sumOf { it.caseResults.size } &&
        rows.count { it.path("type").asText() == "trial_completed" } == epochs.sumOf { it.caseResults.size }) {
        "missing/extra v3 campaign lifecycle event"
    }
}

private data class V3TrialWitness(val driver: TrialCaseDriver, val request: TrialRequest,
    val rawFile: Path, val samplingLedgerSha256: String?)

private fun verifyV3Epoch(epoch: SessionEpoch, campaignRoot: Path, indexBase: Path, manifest: JsonNode,
    witnesses: Map<Path, V3TrialWitness>, seenTokens: MutableSet<String>, store: CheckpointStore,
    clientCandidate: Path, serverCandidate: Path, supportCandidate: Path,
    budget: ReviewedExecutionBudget, sealedOutput: CheckpointRef?) {
    val eventFile = boundInput(indexBase, suiteMapper.valueToTree(epoch.eventLog)).toRealPath()
    require(eventFile.startsWith(campaignRoot.resolve("epochs").resolve(epoch.id)) &&
        eventFile.fileName.toString() == "events.jsonl" && Files.size(eventFile) in 1..10_000_000) {
        "epoch event log unowned/unbounded"
    }
    val events = Files.readAllLines(eventFile).filter(String::isNotBlank).map { suiteMapper.readTree(it) }
    val eventTimes = events.map { Instant.parse(it.path("time").asText()) }
    require(eventTimes.zipWithNext().all { (before, after) -> !after.isBefore(before) }) {
        "epoch event timestamp regressed across native actions"
    }
    val lifecycle = listOf("session_started", "epoch_ready", "prepared", "server_started", "server_ready",
        "server_saved_and_stopped", "validated", "process_cleanup", "log_diagnostics", "session_finished")
    val indices = lifecycle.map { kind -> events.indices.filter { events[it].path("type").asText() == kind }.single() }
    val ready = events[indices[1]]
    val freshSites = epoch.caseResults.flatMap { ref ->
        val trial = witnesses.getValue(boundInput(indexBase, suiteMapper.valueToTree(ref)).toRealPath())
        (DriverRegistry.resolve(trial.request.caseId) as? ExternalSamplingDriver)?.takeIf { it.requireFreshTerrain }
            ?.samplingIntents(trial.request)?.filterIsInstance<SamplingIntent.Site>().orEmpty()
    }
    val expectedFreshRegions = if (freshSites.isEmpty()) emptyList() else
        verifyFreshTerrain(store.verify(requireNotNull(epoch.inputCheckpoint)), freshSites)
    require(ready.path("fresh_input_regions") == suiteMapper.valueToTree<JsonNode>(expectedFreshRegions)) {
        "epoch launch did not independently prove untouched sealed-input sampling terrain"
    }
    require(indices.zipWithNext().all { (left, right) -> left < right } &&
        events.none { it.path("type").asText() in setOf("failure", "world_replaced", "candidate_mutated", "server_restarted") } &&
        events.all { row -> row.path("epoch_id").asText() == epoch.id &&
            row.path("native_session_id").asText() == epoch.nativeSessionId &&
            row.path("branch_id").asText() == epoch.branchId &&
            row.path("campaign_id").asText() == epoch.campaignId &&
            row.path("lineage_id").asText() == epoch.lineageId } &&
        ready.path("epoch_id").asText() == epoch.id && ready.path("native_session_id").asText() == epoch.nativeSessionId &&
        ready.path("branch_id").asText() == epoch.branchId &&
        ready.path("input_checkpoint") == suiteMapper.valueToTree<JsonNode>(epoch.inputCheckpoint) &&
        ready.path("restore_receipt") == suiteMapper.valueToTree<JsonNode>(epoch.restored?.restoreReceipt) &&
        (if (epoch.branchId == "origin") ready.path("restored_from").isNull
            else ready.path("restored_from").asText() == epoch.inputCheckpoint?.id)) {
        "epoch lifecycle/restart identity differs"
    }
    val finished = events[indices.last()]
    require(finished.path("diagnostic_complete").isBoolean && finished.path("diagnostic_complete").asBoolean() &&
        finished.path("success").isBoolean && !finished.path("success").asBoolean() &&
        finished.path("failure").isNull &&
        events.none { it.path("type").asText() in setOf("failure", "diagnostic_failed", "epoch_aborted", "session_failed") }) {
        "diagnostic epoch failed despite a successful wrapper receipt"
    }
    val cleanup = events[indices[lifecycle.indexOf("process_cleanup")]]
    val closure = requireNotNull(epoch.closure) { "closed epoch has no source-bound closure" }
    val closureFile = boundInput(eventFile.parent, suiteMapper.valueToTree(closure.evidence)).toRealPath()
    require(suiteMapper.readTree(closureFile.toFile()).path("purpose").asText() == ExecutionPurpose.QUALIFICATION.wire &&
        closureFile.parent == eventFile.parent && closureFile.fileName.toString() == "epoch-closure.json" &&
        closure.epochId == epoch.id && closure.nativeSessionId == epoch.nativeSessionId &&
        closure.saveValidated && closure.processCleanupComplete && closure.survivingPids.isEmpty()) {
        "epoch closure not owned/complete"
    }
    require(closure.ownedProcesses.size >= (if (epoch.clientStream == null) 1 else 2))
    require(closure.ownedProcesses.map { it.pid }.distinct().size == closure.ownedProcesses.size)
    closure.ownedProcesses.forEach { owned ->
        val processStart = Instant.parse(owned.startedAt)
        require(owned.pid > 0 && !processStart.isBefore(Instant.parse(epoch.startedAt)) &&
            !processStart.isAfter(Instant.parse(epoch.completedAt)))
        val live = ProcessHandle.of(owned.pid)
        val matchingStart = if (live.isPresent && live.get().isAlive) live.get().info().startInstant().orElse(null) else null
        require(live.isEmpty || !live.get().isAlive ||
            (matchingStart != null && matchingStart != processStart)) {
            "closed epoch still owns live process: ${owned.pid}"
        }
    }
    val launches = events.filter { it.path("type").asText() in setOf("server_started", "client_started", "display_started") }
    val launchKinds = launches.groupBy { it.path("type").asText() }
    require(launchKinds["server_started"]?.size == 1 &&
        (launchKinds["client_started"]?.size ?: 0) == (if (epoch.clientStream == null) 0 else 1) &&
        (launchKinds["display_started"]?.size ?: 0) == (if (epoch.clientStream == null) 0 else 1)) {
        "epoch raw launched server/client/display process roster incomplete or extra"
    }
    val ownedSet = closure.ownedProcesses.map { it.pid to it.startedAt }.toSet()
    require(launches.all { row -> row.path("pid").isIntegralNumber && row.path("pid").asLong() > 0 &&
        (row.path("pid").asLong() to row.path("started_at").asText()) in ownedSet } &&
        cleanup.path("owned_processes").isArray &&
        cleanup.path("owned_processes").map { it.path("pid").asLong() to it.path("started_at").asText() }.toSet() == ownedSet &&
        cleanup.path("owned_processes").size() == ownedSet.size) {
        "post-run PID roster diverges from real launched process births or process cleanup"
    }
    val closureDoc = suiteMapper.readTree(closureFile.toFile())
    require(closureDoc.path("schema").asText() == "bc.playtest_epoch_closure.v2" &&
        closureDoc.path("epoch_id").asText() == epoch.id &&
        closureDoc.path("native_session_id").asText() == epoch.nativeSessionId &&
        closureDoc.path("closed_at").asText() == closure.closedAt &&
        !Instant.parse(closure.closedAt).isBefore(Instant.parse(epoch.startedAt)) &&
        !Instant.parse(closure.closedAt).isAfter(Instant.parse(epoch.completedAt)) &&
        boundInput(eventFile.parent, closureDoc.path("events")).toRealPath() == eventFile) {
        "epoch closure JSON does not bind raw lifecycle"
    }
    val runtime = campaignRoot.resolve("epochs").resolve(epoch.id).resolve("runtime").toRealPath()
    val marker = boundInput(eventFile.parent, closureDoc.path("world_marker")).toRealPath()
    val save = boundInput(eventFile.parent, closureDoc.path("world_save")).toRealPath()
    val log = boundInput(eventFile.parent, closureDoc.path("server_log")).toRealPath()
    val serverRoot = epoch.restored?.roots?.get("server")?.let { Path.of(it).toRealPath() } ?: marker.parent.parent
    require(serverRoot.startsWith(runtime.resolve("server")) &&
        (if (epoch.restored == null) serverRoot.parent == runtime.resolve("server")
            else serverRoot == runtime.resolve("server")) &&
        marker == serverRoot.resolve("world/.bc-suite-world-id").toRealPath() &&
        save == serverRoot.resolve("world/level.dat").toRealPath() &&
        log == eventFile.parent.resolve("server.log").toRealPath() &&
        Files.readString(marker).trim() == epoch.lineageId && Files.size(log) in 1..25_000_000 &&
        Regex("(?i)(saving chunks|all chunks are saved|saved the game|saved chunks)").containsMatchIn(Files.readString(log)) &&
        events[indices[lifecycle.indexOf("validated")]].path("candidate_hashes_unchanged").asBoolean(false) &&
        Path.of(events[indices[lifecycle.indexOf("validated")]].path("world_save").asText()).toRealPath() == save &&
        cleanup.path("complete").asBoolean(false) && cleanup.path("surviving_pids").isArray &&
        cleanup.path("surviving_pids").isEmpty) { "raw save/marker/log/process closure not independently verified" }
    val requestsFile = boundInput(indexBase, suiteMapper.valueToTree(epoch.nativeRequests)).toRealPath()
    val observationsFile = boundInput(indexBase, suiteMapper.valueToTree(epoch.nativeObservations)).toRealPath()
    require(requestsFile.parent == eventFile.parent.resolve("native") &&
        observationsFile.parent == requestsFile.parent &&
        requestsFile.fileName.toString() == "requests.jsonl" && observationsFile.fileName.toString() == "observations.jsonl" &&
        Files.size(requestsFile) in 1..100_000_000 && Files.size(observationsFile) in 1..100_000_000) {
        "epoch raw native streams missing/unbounded"
    }
    fun rows(path: Path): List<JsonNode> = Files.readAllLines(path).also { require(it.isNotEmpty() && it.all(String::isNotBlank)) }
        .map { line -> require(line.length <= PROTOCOL_LINE_LIMIT); suiteMapper.readTree(line) }
    val requests = rows(requestsFile); val observations = rows(observationsFile)
    val inputBinding = epoch.clientStream?.inputRuntime
    val inputRuntime: ClientInputRuntime? = inputBinding?.let { binding ->
        readClientInputRuntime(eventFile.parent.resolve("client-native"), binding, binding.sha256)
    }
    val inventoryActor = observations.filter { it.path("request_sequence").asLong(-1) == 1L &&
        it.path("driver").asText() == "inventory.discover" && it.path("kind").asText() == "completed" }
        .lastOrNull()?.path("payload")?.path("player_uuids")?.takeIf { it.isArray && it.size() == 1 }
        ?.get(0)?.takeIf { it.isTextual }?.asText()
    val expected = mutableListOf(PlannedNativeAction("campaign.inventory", "inventory.discover", suiteMapper.createObjectNode(), 12000))
    val clientPlanned = mutableListOf<PlannedClientAction>()
    val expectedActions = mutableListOf<Triple<V3TrialWitness, DriverStep, JsonNode>>()
    val naturalEvents = events.filter { it.path("type").asText() == "natural_target_frozen" }
    val frozenByTrial = naturalEvents.associate { event ->
        val file = suiteMapper.treeToValue(event.path("target"), BoundFile::class.java)
        require(boundInput(indexBase, event.path("target")) == eventFile.parent.resolve(
            "trial-${event.path("case_id").asText()}-${event.path("trial_id").asText()}-natural-${
                readFrozenNaturalTarget(file).bound.context.requestSequence}.json"))
        (event.path("case_id").asText() to event.path("trial_id").asText()) to readFrozenNaturalTarget(file)
    }
    require(frozenByTrial.size == naturalEvents.size)
    var expectedOrdinal = 0L
    var expectedServerSequence = 2L // inventory.discover occupies server request 1
    var expectedClientSequence = 1L
    epoch.caseResults.forEach { bound ->
        val trial = witnesses[boundInput(indexBase, suiteMapper.valueToTree(bound)).toRealPath()]
            ?: error("epoch trial absent from authored case roster: ${epoch.id}")
        if (trial.request.caseId in setOf("control.travel.walk-route", "control.client.inventory-screen",
            "control.client.emi-navigation"))
            require(inventoryActor != null && trial.request.parameters.path("player_uuid").asText() == inventoryActor &&
                trial.request.parameters.path("input_profile").asText() == inputRuntime?.profile) {
                "real input control actor/profile does not match raw joined player and host-bound runtime"
            }
        val reviewedSteps = trial.driver.planTrial(trial.request)
        requireReviewedInputObserverSequence(reviewedSteps, trial.request.caseId)
        val rawSteps = rows(trial.rawFile)
        require(rawSteps.size == reviewedSteps.size) { "raw trial/action count differs from independent plan" }
        val arms = mutableMapOf<String, PassiveObserverArm>()
        var actualEmiPoint: JsonNode? = null
        var reviewedEmiProjection: String? = null
        reviewedSteps.zip(rawSteps).forEach { (authored, raw) ->
            expectedOrdinal++
            val resolved = resolveObservedActorAction(resolveInputRuntimeAction(
                resolveExecutionAction(authored.action, trial.samplingLedgerSha256), inputBinding), inventoryActor)
            val base = if (resolved.id == "client.input_hold" &&
                resolved.parameters.path("kind").asText() in setOf("emi_mouse_left", "emi_mouse_right")) {
                require(trial.request.caseId == "control.client.emi-navigation")
                resolveReviewedEmiMouseAction(resolved, requireNotNull(reviewedEmiProjection) {
                    "EMI mouse press lacks earlier independently reviewed actual hover"
                })
            } else resolved
            val sequence = when (base.source) {
                "server" -> expectedServerSequence++
                "client" -> expectedClientSequence++
                else -> error("unreviewed native source: ${base.source}")
            }
            val selector = if (base.id == "target.observe.loaded_v1") {
                val spec = suiteMapper.treeToValue(manifest.path("cases").single { it.path("id").asText() ==
                    trial.request.caseId }, SuiteCase::class.java)
                reviewedLoadedSelector(spec, base, requireNotNull(epoch.inputCheckpoint))
            } else null
            val context = if (base.id in passiveObservationDrivers || selector != null ||
                base.id == "player.dig.witness_v1") WitnessContext(
                epoch.campaignId, epoch.lineageId, epoch.id, epoch.nativeSessionId, epoch.branchId,
                trial.request.caseId, trial.request.trialId, "server", expectedOrdinal, sequence,
                requireNotNull(inventoryActor), base.id, epoch.purpose) else null
            val passive = resolvePassiveObserverAction(base, context, arms)
            val step = authored.copy(action = when {
                selector != null -> resolveLoadedTargetAction(passive, selector,
                    requireNotNull(inventoryActor), requireNotNull(context))
                passive.id == "player.dig.witness_v1" -> resolveReviewedServerDig(passive,
                    frozenByTrial.getValue(trial.request.caseId to trial.request.trialId), requireNotNull(context))
                else -> passive
            })
            expectedActions += Triple(trial, step, raw)
            if (trial.request.caseId == "control.client.emi-navigation" && step.action.id == "client.input_point") {
                require(actualEmiPoint == null)
                actualEmiPoint = raw.path("observation").path("payload")
            }
            if (trial.request.caseId == "control.client.emi-navigation" && step.action.id == "client.emi_hover") {
                val runtime = requireNotNull(inputRuntime)
                val binding = requireNotNull(inputBinding)
                reviewedEmiProjection = independentlyReviewedEmiHoverProjection(trial.request.parameters,
                    requireNotNull(actualEmiPoint), raw.path("observation").path("payload"),
                    requireNotNull(inventoryActor), runtime, binding.sha256,
                    candidateEmiJarSha256(clientCandidate, serverCandidate))
            }
            when (step.action.source) {
                "server" -> expected += PlannedNativeAction(trial.request.caseId, step.action.id,
                    step.action.parameters, step.action.timeoutTicks)
                "client" -> clientPlanned += PlannedClientAction(trial.request.caseId, step.action.id,
                    step.action.parameters, step.action.timeoutTicks)
            }
        }
        require(arms.isEmpty()) { "trial ended with passive observer still armed" }
        expected += PlannedNativeAction(trial.request.caseId, "fixture.cleanup", suiteMapper.createObjectNode(), 2400)
        expectedServerSequence++
    }
    verifyV2NativeProtocol(requests, observations, expected)
    if (epoch.caseResults.any { ref ->
        witnesses.getValue(boundInput(indexBase, suiteMapper.valueToTree(ref)).toRealPath())
            .request.caseId == "control.persistence.vanilla-reopen"
    }) {
        require(epoch.caseResults.size == 1) { "fresh vanilla reopen must occupy one isolated real-client epoch" }
        replayFreshVanillaReopenFromEpoch(store, requireNotNull(epoch.inputCheckpoint), epoch)
    }
    // The raw protocol is verified first; only then replay each producer prefix against an
    // independent sealed origin. A prefix is a scoped interval fact, NOT natural coverage.
    val scopeEvent = ready.path("block_mutation_scope")
    val stopEvents = events.filter { it.path("type").asText() == "block_mutation_stop_bound" }
    if (scopeEvent.isNull || scopeEvent.isMissingNode) {
        require(ready.path("block_mutation_baseline").isNull &&
            stopEvents.isEmpty() && observations.none { it.path("payload").has("block_mutation_pre_action_prefix") }) {
            "mutation evidence appeared without a controller prelaunch scope"
        }
    } else {
        val input = requireNotNull(epoch.inputCheckpoint)
        val scopeBinding = suiteMapper.treeToValue(scopeEvent, BoundFile::class.java)
        val scopePath = boundInput(indexBase, scopeEvent)
        require(scopePath == eventFile.parent.resolve("target-mutation-scope.json") && stopEvents.size == 1 &&
            events.indexOf(stopEvents.single()) > indices[lifecycle.indexOf("log_diagnostics")] &&
            events.indexOf(stopEvents.single()) < indices[lifecycle.indexOf("session_finished")]) {
            "server-only mutation scope/status not bound in sealed epoch chronology"
        }
        val scope = suiteMapper.readTree(scopePath.toFile())
        val scopedSelectors = epoch.caseResults.flatMap { ref ->
            val trial = witnesses.getValue(boundInput(indexBase, suiteMapper.valueToTree(ref)).toRealPath())
            val spec = suiteMapper.treeToValue(manifest.path("cases").single { it.path("id").asText() == trial.request.caseId },
                SuiteCase::class.java)
            spec.targetBindings.map { suiteMapper.treeToValue(it, ReviewedTargetSelector::class.java) }
        }.filter { it.source == TargetSource.NATURAL_UNMODIFIED_BLOCK }
        require(scopedSelectors.isNotEmpty() && scopedSelectors.all {
            it.inputCheckpointId == "origin" && it.catalogueSha256 == manifest.path("authoring_catalogue").path("sha256").asText() &&
                it.capabilityId == "target.observe.loaded_v1"
        }) { "prelaunch source did not derive from reviewed block selectors" }
        val independentlyReviewedPositions = scopedSelectors.flatMap { selector ->
            val bounds = selector.bounds
            val span = listOf(bounds.maxX.toLong() - bounds.minX + 1, bounds.maxY.toLong() - bounds.minY + 1,
                bounds.maxZ.toLong() - bounds.minZ + 1)
            require(span.all { it in 1..64 } && span.fold(1L) { n, size ->
                require(n <= 64 / size); n * size
            } <= 64L)
            (bounds.minX..bounds.maxX).flatMap { x -> (bounds.minY..bounds.maxY).flatMap { y ->
                (bounds.minZ..bounds.maxZ).map { z -> ScopedBlockPosition(bounds.dimension, x, y, z) }
            } }
        }.distinct().sortedWith(compareBy(ScopedBlockPosition::dimension, ScopedBlockPosition::x,
            ScopedBlockPosition::y, ScopedBlockPosition::z))
        require(scope.path("positions") == suiteMapper.valueToTree<JsonNode>(independentlyReviewedPositions)) {
            "native mutation scope did not enumerate exact reviewed prelaunch positions"
        }
        val baselinePath = boundInput(indexBase, ready.path("block_mutation_baseline"))
        require(baselinePath == eventFile.parent.resolve("target-mutation-baseline.json") &&
            Files.size(baselinePath) in 1..1_048_576)
        val baseline = suiteMapper.readTree(baselinePath.toFile())
        require(baseline.isObject && baseline.fieldNames().asSequence().toSet() == setOf("schema", "checkpoint",
            "pristine_events", "pristine_inventory_request", "pristine_inventory_observation", "positions") &&
            baseline.path("schema").asText() == "bc.playtest_initial_block_baseline.v1" &&
            baseline.path("checkpoint") == suiteMapper.valueToTree<JsonNode>(input))
        val initial = requirePristinePackOrigin(store, input)
        require(baseline.path("pristine_events") == suiteMapper.valueToTree<JsonNode>(initial.rawEvents) &&
            baseline.path("pristine_inventory_request") == suiteMapper.valueToTree<JsonNode>(initial.rawInventoryRequest) &&
            baseline.path("pristine_inventory_observation") == suiteMapper.valueToTree<JsonNode>(initial.rawInventoryObservation) &&
            baseline.path("positions").isArray && baseline.path("positions").size() == independentlyReviewedPositions.size)
        independentlyReviewedPositions.zip(baseline.path("positions").toList()).forEach { (pos, row) ->
            val sealed = readSealedBlockBaseline(store, input, pos.dimension, pos.x, pos.y, pos.z)
            require(row.isObject && row.fieldNames().asSequence().toSet() == setOf("dimension", "x", "y", "z",
                "block_id", "properties", "region_sha256") &&
                row.path("dimension").asText() == pos.dimension && row.path("x").asInt() == pos.x &&
                row.path("y").asInt() == pos.y && row.path("z").asInt() == pos.z &&
                row.path("block_id").asText() == sealed.blockId &&
                row.path("properties") == suiteMapper.valueToTree<JsonNode>(sealed.properties) &&
                row.path("region_sha256").asText() == sealed.region.sha256) {
                "prelaunch block-state facts differ from Store-verified pristine MCA/NBT"
            }
        }
        val sourceBindings = initial.receipt.bindings
        val prefixes = requests.map { requestRow ->
            val sequence = requestRow.path("sequence").asLong(-1)
            val terminal = observations.filter { it.path("request_sequence").asLong(-1) == sequence &&
                it.path("kind").asText() in setOf("completed", "failed") }.single()
            replayInitialBlockMutationPrefix(store, input, scopeBinding, requestsFile.parent,
                terminal.path("payload").path("block_mutation_pre_action_prefix"), sourceBindings,
                epoch.id, epoch.nativeSessionId)
        }
        require(prefixes.map { it.byteCount }.zipWithNext().all { (a, b) -> b >= a } &&
            prefixes.map { it.records }.zipWithNext().all { (a, b) -> b >= a }) {
            "observed mutation prefixes regressed across native request stream"
        }
        val stopped = stopEvents.single()
        require(stopped.path("scope") == scopeEvent)
        val final = verifyStoppedBlockMutationStatus(store, input, scopeBinding, requestsFile.parent,
            suiteMapper.treeToValue(stopped.path("status"), BoundFile::class.java), sourceBindings,
            epoch.id, epoch.nativeSessionId, requireShutdownDrain = true)
        require(prefixes.isNotEmpty() && final.byteCount >= prefixes.last().byteCount &&
            final.records >= prefixes.last().records) { "server-stop mutation status dropped action-time evidence" }
    }
    val naturalSelectors = epoch.caseResults.flatMap { ref ->
        val trial = witnesses.getValue(boundInput(indexBase, suiteMapper.valueToTree(ref)).toRealPath())
        val spec = suiteMapper.treeToValue(manifest.path("cases").single { it.path("id").asText() ==
            trial.request.caseId }, SuiteCase::class.java)
        spec.targetBindings.map { suiteMapper.treeToValue(it, ReviewedTargetSelector::class.java) }
    }.filter { it.schema == "bc.playtest_target_selector.v2" }
    val originalEvents = events.filter { it.path("type").asText() == "runtime_original_classes_bound" }
    if (naturalSelectors.isEmpty()) require(originalEvents.isEmpty() &&
        ready.path("runtime_class_origins_candidate_sha256").isNull) {
        "unreviewed current runtime-class origin sidecar injected into epoch"
    } else {
        require(naturalSelectors.size == 1 && originalEvents.size == 1 &&
            ready.path("runtime_class_origins_candidate_sha256").asText() == Hashes.sha256(serverCandidate))
        val reviewed = verifyReviewedStage1Coverage(naturalSelectors.single(), clientCandidate, serverCandidate,
            supportCandidate)
        val recorded = originalEvents.single()
        require(recorded.path("profile") == suiteMapper.valueToTree<JsonNode>(reviewed.profile) &&
            recorded.path("scope") == scopeEvent &&
            events.indexOf(recorded) > indices[lifecycle.indexOf("server_ready")] &&
            events.indexOf(recorded) < events.indexOfFirst { it.path("type").asText() == "native_action_dispatched" })
        verifySelectedOriginalClassOrigins(requestsFile.parent,
            suiteMapper.treeToValue(recorded.path("sidecar"), BoundFile::class.java),
            epoch.id, epoch.nativeSessionId, Hashes.sha256(serverCandidate),
            suiteMapper.treeToValue(scopeEvent, BoundFile::class.java),
            strictCapturedJson(capturedEvidenceBytes(reviewed.profile, 1_048_576)).path("class_pins"),
            exactControlPaths = false)
    }
    // Stage2 has a DISTINCT parent-absent scope. Independently replay constructor→Load→postprocess,
    // per-event raw birth↔postbirth mutation prefixes, stop status, and actual saved poststate.
    // This still does not certify a candidate/provider path or natural origin by itself.
    val birthScopeNode = ready.path("block_birth_scope")
    val birthSidecarNode = ready.path("block_birth_parent_absence")
    val birthStopEvents = events.filter { it.path("type").asText() == "block_birth_stop_bound" }
    if (birthScopeNode.isNull || birthScopeNode.isMissingNode) {
        require(birthSidecarNode.isNull && birthStopEvents.isEmpty() &&
            observations.none { it.path("payload").has("block_birth_pre_action_prefix") }) {
            "unscoped native birth evidence cannot acquire parent ancestry"
        }
    } else {
        val input = requireNotNull(epoch.inputCheckpoint)
        val sourceBindings = store.verify(input).bindings
        val captured = PrelaunchBlockBirthScope(
            suiteMapper.treeToValue(birthScopeNode, BoundFile::class.java),
            suiteMapper.treeToValue(birthSidecarNode, BoundFile::class.java))
        require(boundInput(indexBase, birthScopeNode) == eventFile.parent.resolve("target-birth-scope.json") &&
            boundInput(indexBase, birthSidecarNode) == eventFile.parent.resolve("target-birth-parent-absence.json") &&
            birthStopEvents.size == 1 &&
            events.indexOf(birthStopEvents.single()) > indices[lifecycle.indexOf("log_diagnostics")] &&
            events.indexOf(birthStopEvents.single()) < indices[lifecycle.indexOf("session_finished")]) {
            "Stage2 source/stop was not controller-owned and sealed after server shutdown"
        }
        val specs = epoch.caseResults.map { ref ->
            val trial = witnesses.getValue(boundInput(indexBase, suiteMapper.valueToTree(ref)).toRealPath())
            suiteMapper.treeToValue(manifest.path("cases").single { it.path("id").asText() ==
                trial.request.caseId }, SuiteCase::class.java)
        }
        val catalogue = manifest.path("authoring_catalogue").path("sha256").asText()
        val absence = replayBlockBirthScope(store, input, specs, catalogue, sourceBindings,
            epoch.id, epoch.nativeSessionId, eventFile.parent, captured)
        val prefixes = requests.map { requestRow ->
            val sequence = requestRow.path("sequence").asLong(-1)
            val terminal = observations.filter { it.path("request_sequence").asLong(-1) == sequence &&
                it.path("kind").asText() in setOf("completed", "failed") }.single()
            replayBlockBirthPrefix(store, input, specs, catalogue, sourceBindings,
                epoch.id, epoch.nativeSessionId, eventFile.parent, captured, requestsFile.parent,
                terminal.path("payload").path("block_birth_pre_action_prefix"), readyRequired = false)
        }
        require(prefixes.isNotEmpty() && prefixes.map { it.birthRecords }.zipWithNext().all { (a, b) -> b >= a } &&
            prefixes.map { it.mutationRecords }.zipWithNext().all { (a, b) -> b >= a }) {
            "Stage2 birth/postbirth mutation prefixes regressed across source actions"
        }
        val stopped = birthStopEvents.single()
        require(stopped.path("scope") == birthScopeNode && stopped.path("parent_absence") == birthSidecarNode)
        val final = replayStoppedBlockBirthStatus(store, input, specs, catalogue, sourceBindings,
            epoch.id, epoch.nativeSessionId, eventFile.parent, captured, requestsFile.parent,
            suiteMapper.treeToValue(stopped.path("status"), BoundFile::class.java))
        require(final.ready && final.birthStates.keys == absence.watchedPositions.toSet() &&
            final.birthRecords >= prefixes.last().birthRecords &&
            final.mutationRecords >= prefixes.last().mutationRecords) {
            "Stage2 stopped stream did not preserve complete watched action interval"
        }
        val saved = store.verify(requireNotNull(sealedOutput) {
            "newly generated Stage2 interval has no sealed output checkpoint for independent saved-state join"
        })
        require(saved.parent == input && saved.sourceEpoch == epoch.id && saved.bindings == sourceBindings)
        final.currentStates.forEach { (position, state) ->
            val persisted = readSealedBlockBaseline(store, sealedOutput, position.dimension,
                position.x, position.y, position.z)
            require(persisted.blockId == state.first && persisted.properties == state.second) {
                "postbirth mutation terminal state differs from saved postseal MCA block state"
            }
        }
    }
    val inputActions = clientPlanned.filter { it.driver in authenticInputDrivers }
    if (inputActions.isEmpty()) require(inputRuntime == null && inputBinding == null &&
        events.none { it.path("type").asText() == "client_input_runtime_bound" }) {
        "unreviewed runtime input binding attached to non-input epoch"
    } else {
        val runtime = requireNotNull(inputRuntime)
        val binding = requireNotNull(inputBinding)
        val candidateSha = jsonFingerprint(suiteMapper.valueToTree(linkedMapOf(
            "client_sha256" to Hashes.sha256(clientCandidate), "server_sha256" to Hashes.sha256(serverCandidate))))
        val binderIndices = events.indices.filter { events[it].path("type").asText() == "client_input_runtime_bound" }
        require(binderIndices.size == 1) { "missing or duplicate immutable input-runtime birth event" }
        val bindingEvent = events[binderIndices.single()]
        val displayArgs = bindingEvent.path("host_display_args").map { it.asText() }
        val reviewedDisplayArgs = listOf(":${runtime.display.substringAfter(':')}", "-screen", "0",
            "${runtime.profile.substringBefore('@')}x24", "-nolisten", "unix", "-listen", "tcp",
            "-nolock", "-auth", runtime.xauthorityPath)
        require(bindingEvent.path("host_display_args").isArray && displayArgs == reviewedDisplayArgs &&
            jsonFingerprint(suiteMapper.valueToTree<JsonNode>(displayArgs)) == runtime.hostXvfbArgsSha256 &&
            bindingEvent.path("host_display_executable").asText() == runtime.hostXvfbExecutable &&
            Path.of(bindingEvent.path("host_client_executable").asText()).fileName.toString() == "java" &&
            bindingEvent.path("host_client_args_sha256").asText().matches(shaPattern) &&
            Instant.parse(bindingEvent.path("host_client_started_at").asText()).toEpochMilli() ==
                runtime.hostClientBirthEpochMillis && runtime.campaignId == epoch.campaignId &&
            runtime.lineageId == epoch.lineageId && runtime.epochId == epoch.id &&
            runtime.purpose == epoch.purpose && runtime.clientSessionId == epoch.clientStream?.nativeSessionId &&
            runtime.candidateSha256 == candidateSha &&
            runtime.supportSha256 == manifest.path("test_support").path("sha256").asText() &&
            inputActions.first().parameters.path("input_runtime_sha256").asText() == binding.sha256 &&
            inputActions.filter { it.driver == "client.input_hold" }.map { it.parameters.path("profile").asText() }
                .distinct() == listOf(runtime.profile) &&
            binderIndices.single() > indices[lifecycle.indexOf("server_ready")] &&
            bindingEvent.path("binding") == suiteMapper.valueToTree<JsonNode>(binding) &&
            bindingEvent.path("host_client_pid").asLong(-1) == runtime.hostClientPid &&
            bindingEvent.path("namespace_client_pid").asLong(-1) == runtime.clientNamespacePid &&
            bindingEvent.path("host_display_pid").asLong(-1) == runtime.hostXvfbPid &&
            events.indices.any { it < binderIndices.single() && events[it].path("type").asText() == "client_joined" } &&
            events.indices.any { it > binderIndices.single() &&
                events[it].path("type").asText() == "native_action_dispatched" &&
                events[it].path("driver").asText() in authenticInputDrivers }) {
            "client runtime binding not attached after real join/before reviewed physical input"
        }
        val owned = events[indices[lifecycle.indexOf("process_cleanup")]].path("owned_processes")
        val xvfbBorn = owned.any { row -> row.path("pid").asLong(-1) == runtime.hostXvfbPid &&
            Instant.parse(row.path("started_at").asText()).toEpochMilli() == runtime.hostXvfbBirthEpochMillis }
        val clientBorn = owned.any { row -> row.path("pid").asLong(-1) == runtime.hostClientPid &&
            Instant.parse(row.path("started_at").asText()).toEpochMilli() == runtime.hostClientBirthEpochMillis }
        val cookie = eventFile.parent.resolve("fixture/Xauthority")
        require(xvfbBorn && clientBorn && owned.isArray &&
            Path.of(runtime.xauthorityPath) == cookie &&
            Files.isRegularFile(cookie, java.nio.file.LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(cookie) && Files.size(cookie) in 1..4096 &&
            Hashes.sha256(cookie) == runtime.xauthoritySha256) {
            "host PID birth or private cookie differs from retained process/fixture evidence"
        }
    }
    requireFreshV3EpochToken(requests, epoch.nativeSessionId, seenTokens)
    val grouped = observations.groupBy { it.path("request_sequence").asLong() }
    val inventory = grouped[1L]?.lastOrNull()?.path("payload")
    require(inventory != null && inventory.path("complete").isBoolean && inventory.path("complete").asBoolean() &&
        inventory.path("gaps").isArray && inventory.path("gaps").isEmpty &&
        inventory.path("seed").isIntegralNumber && inventory.path("seed").asLong() == 424242L) { "epoch effective inventory incomplete" }
    val roster = inventory.path("rosters")
    require(roster.isObject && roster.fieldNames().asSequence().toSet() == requiredRosters &&
        manifest.path("rosters").all { review ->
            roster.path(review.path("kind").asText()).map { it.asText() }.toSet() ==
                review.path("entries").fieldNames().asSequence().toSet()
        }) { "native effective rosters differ from reviewed catalogue" }
    val inventoryFiles = inventory.path("files")
    require(inventoryFiles.isArray && !inventoryFiles.isEmpty && inventoryFiles.all { file ->
        file.path("complete").isBoolean && file.path("complete").asBoolean() &&
            runCatching { boundInput(eventFile.parent, file) }.isSuccess
    }) { "unbound/incomplete effective native inventory exports" }
    val epochTrials = epoch.caseResults.map { witnesses.getValue(boundInput(indexBase, suiteMapper.valueToTree(it)).toRealPath()) }
    val clientTerminals: List<NativeObservation> = if (clientPlanned.isEmpty()) {
        if (epochTrials.any { it.driver.clientRequired }) {
            require(epochTrials.all { it.driver.clientRequired } && events.any { it.path("type").asText() == "client_joined" } &&
                inventory.path("player_count").asInt(-1) == 1 && inventory.path("player_uuids").size() == 1) {
                "client-required server actions lack a real joined client"
            }
            val stream = requireNotNull(epoch.clientStream) { "real login has no independent client capability stream" }
            val capability = boundInput(indexBase, suiteMapper.valueToTree(stream.capabilities)).toRealPath()
            val mailbox = eventFile.parent.resolve("client-native").toRealPath()
            val journal = mailbox.resolve("requests.jsonl")
            val requestDirectory = mailbox.resolve("requests")
            require(Files.isRegularFile(journal, java.nio.file.LinkOption.NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(journal) && Files.size(journal) == 0L &&
                Files.isDirectory(requestDirectory, java.nio.file.LinkOption.NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(requestDirectory) &&
                Files.list(requestDirectory).use { it.count() } == 0L &&
                Files.list(mailbox).use { files -> files.map { it.fileName.toString() }.toList().toSet() } ==
                    setOf("capabilities.json", "requests.jsonl", "requests")) {
                "capabilities-only client mailbox contains hidden request, observation, input or screenshot files"
            }
            val cap = suiteMapper.readTree(capability.toFile())
            require(capability.parent == mailbox &&
                capability.fileName.toString() == "capabilities.json" && stream.nativeSessionId.matches(idPattern) &&
                stream.nativeSessionId != epoch.nativeSessionId && cap.path("schema").asText() == "bc.playtest_capabilities.v2" &&
                cap.path("source").asText() == "client" && cap.path("actual_client").asBoolean(false) &&
                cap.path("supported_drivers").map { it.asText() }.toSet() == setOf("client.snapshot", "client.framebuffer",
                    "client.emi_index", "client.input_key", "client.input_click") + authenticInputDrivers &&
                cap.path("run_id").asText() == stream.nativeSessionId && cap.path("token").asText().matches(shaPattern) &&
                seenTokens.add(cap.path("token").asText()) &&
                stream.nativeRequests == null && stream.nativeObservations == null &&
                stream.inputLog == null && stream.inputRuntime == null) {
                "capabilities-only joined client was replayed, synthetic or carried unreviewed actions"
            }
        } else require(epoch.clientStream == null) { "server-only epoch carries synthetic/unplanned client evidence" }
        emptyList()
    } else {
        require(epochTrials.isNotEmpty() && epochTrials.all { it.driver.clientRequired } &&
            inventory.path("player_count").asInt(-1) == 1 && inventory.path("player_uuids").isArray &&
            inventory.path("player_uuids").size() == 1) { "client stream lacks one joined server-observed player" }
        val stream = requireNotNull(epoch.clientStream) { "reviewed client actions have no independent client mailbox" }
        val capability = boundInput(indexBase, suiteMapper.valueToTree(stream.capabilities))
        val clientCaps = suiteMapper.readTree(capability.toFile())
        val clientToken = clientCaps.path("token").asText()
        require(clientToken.matches(shaPattern) && clientToken !in seenTokens &&
            stream.nativeSessionId.matches(idPattern) && stream.nativeSessionId != epoch.nativeSessionId &&
            stream.nativeRequests != null && stream.nativeObservations != null) {
            "client session/token reused or raw streams missing"
        }
        val verified = verifyV3ClientEvidence(indexBase, epoch.id, stream.nativeSessionId, clientToken,
            requests.first().path("token").asText(), suiteMapper.valueToTree(stream), clientPlanned,
            candidateEmiJarSha256(clientCandidate, serverCandidate), inventory.path("player_uuids").single().asText(),
            inputRuntime, inputBinding)
        require(seenTokens.add(clientToken)) { "client token reused across epochs" }
        verified
    }
    if (epoch.branchId == "origin") {
        require(events.none { it.path("type").asText() in setOf("client_started", "client_joined", "player_joined", "first_join") } &&
            requests.size == 1 && epoch.caseResults.isEmpty() &&
            inventory.path("player_count").isIntegralNumber && inventory.path("player_count").asInt() == 0 &&
            inventory.path("player_uuids").isArray && inventory.path("player_uuids").isEmpty) {
            "origin checkpoint not sealed from player-free world"
        }
    }
    val actionEvents = events.filter { it.path("type").asText() in setOf("native_action_dispatched", "native_action_completed") }
    require(actionEvents.size == expectedActions.size * 2) { "cross-mailbox action-order events incomplete or extra" }
    var priorNanos: Long? = null
    expectedActions.forEachIndexed { index, (witness, step, raw) ->
        val dispatched = actionEvents[index * 2]; val completed = actionEvents[index * 2 + 1]
        val observed = suiteMapper.treeToValue(raw.path("observation"), NativeObservation::class.java)
        val common = listOf(dispatched, completed)
        val allowance = budget.action(step.action)
        val interval = runCatching { Math.subtractExact(completed.path("monotonic_nanos").asLong(),
            dispatched.path("monotonic_nanos").asLong()) }.getOrDefault(Long.MAX_VALUE)
        require(interval in 0..allowance.wall.toNanos() && dispatched.path("type").asText() == "native_action_dispatched" &&
            completed.path("type").asText() == "native_action_completed" && common.all { event ->
                event.path("case_id").asText() == witness.request.caseId &&
                    event.path("trial_id").asText() == witness.request.trialId &&
                    event.path("step").asText() == step.name && event.path("source").asText() == step.action.source &&
                    event.path("driver").asText() == step.action.id &&
                    event.path("action_ordinal").isIntegralNumber && event.path("action_ordinal").asLong() == index + 1L &&
                    event.path("monotonic_nanos").isIntegralNumber && event.path("monotonic_nanos").canConvertToLong()
            } && dispatched.path("parameters_sha256").asText() == jsonFingerprint(step.action.parameters) &&
                dispatched.path("request_sequence").isIntegralNumber &&
                dispatched.path("request_sequence").asLong(-1) == observed.requestSequence &&
                completed.path("request_sequence").asLong(-1) == observed.requestSequence &&
                completed.path("observation_sha256").asText() ==
                    jsonFingerprint(suiteMapper.valueToTree(observed)) &&
                completed.path("monotonic_nanos").asLong() >= dispatched.path("monotonic_nanos").asLong() &&
                (priorNanos == null || dispatched.path("monotonic_nanos").asLong() >= priorNanos!!)) {
            "native action dispatch/completion events do not witness exact cross-server/client execution order"
        }
        priorNanos = completed.path("monotonic_nanos").asLong()
    }
    // Independently reconstruct every controller-selected loaded row from the actual raw
    // producer sidecar. No selected row is a natural target until a separate reviewed
    // supported-scope/real-path-controls verifier grants that stronger claim.
    val loadedActions = expectedActions.withIndex().filter { it.value.second.action.id == "target.observe.loaded_v1" }
    val loadedEvents = events.filter { it.path("type").asText() == "target_loaded_attempt_bound" }
    require(loadedEvents.size == loadedActions.size) { "loaded-target attempt ledger missing or injected" }
    loadedActions.zip(loadedEvents).forEach { (indexed, event) ->
        val (trial, step, raw) = indexed.value
        val actionEvent = actionEvents[indexed.index * 2]
        val completionEvent = actionEvents[indexed.index * 2 + 1]
        val eventIndex = events.indexOf(event)
        require(event.fieldNames().asSequence().toSet() == setOf("time", "type", "campaign_id",
            "lineage_id", "purpose", "epoch_id", "branch_id", "native_session_id",
            "case_id", "trial_id", "step", "request_sequence", "attempt") &&
            event.path("campaign_id").asText() == epoch.campaignId &&
            event.path("lineage_id").asText() == epoch.lineageId &&
            event.path("epoch_id").asText() == epoch.id &&
            event.path("native_session_id").asText() == epoch.nativeSessionId &&
            event.path("branch_id").asText() == epoch.branchId &&
            event.path("purpose").asText() == epoch.purpose &&
            eventIndex > events.indexOf(completionEvent) &&
            (indexed.index + 1 >= expectedActions.size || eventIndex <
                events.indexOf(actionEvents[(indexed.index + 1) * 2])) &&
            event.path("case_id").asText() == trial.request.caseId &&
            event.path("trial_id").asText() == trial.request.trialId &&
            event.path("step").asText() == step.name &&
            event.path("request_sequence").asLong(-1) == raw.path("observation").path("request_sequence").asLong(-2)) {
            "loaded-target attempt not between its own native terminal and next dispatch"
        }
        val selector = suiteMapper.treeToValue(manifest.path("cases").single { it.path("id").asText() ==
            trial.request.caseId }, SuiteCase::class.java).let { spec ->
            reviewedLoadedSelector(spec, trial.driver.planTrial(trial.request).single {
                it.name == step.name }.action, requireNotNull(epoch.inputCheckpoint))
        }
        val context = suiteMapper.treeToValue(step.action.parameters.path("witness_context"), WitnessContext::class.java)
        val sequence = raw.path("observation").path("request_sequence").asLong()
        val request = loadedCensusRequest(epoch.nativeSessionId, trial.request.caseId,
            requests.first().path("token").asText(), sequence, step.action.timeoutTicks, step.action)
        val census = captureLoadedBlockCensus(requestsFile.parent, request,
            raw.path("observation").path("payload"), context, selector)
        val boundPath = boundInput(indexBase, event.path("attempt"))
        require(boundPath.parent == eventFile.parent && Files.size(boundPath) in 2..1_048_576 &&
            boundPath.fileName.toString() ==
            "trial-${trial.request.caseId}-${trial.request.trialId}-target-$sequence.json") {
            "loaded-only attempt file moved outside exact epoch trial"
        }
        val attempt = suiteMapper.readValue(boundPath.toFile(), LoadedTargetAttempt::class.java)
        val reconstructed = loadedTargetAttempt(selector, requireNotNull(epoch.inputCheckpoint), census,
            context, jsonFingerprint(raw.path("observation")), Instant.parse(attempt.dispatchedAt),
            actionEvent.path("monotonic_nanos").asLong(), attempt.completedNanos)
        require(attempt == reconstructed && attempt.outcome == "loaded_only" &&
            attempt.completedNanos >= completionEvent.path("monotonic_nanos").asLong() &&
            (indexed.index + 1 >= expectedActions.size || attempt.completedNanos <=
                actionEvents[(indexed.index + 1) * 2].path("monotonic_nanos").asLong()) &&
            !Instant.parse(attempt.dispatchedAt).isAfter(Instant.parse(actionEvent.path("time").asText())) &&
            !Instant.parse(event.path("time").asText()).isBefore(Instant.parse(attempt.dispatchedAt))) {
            "controller's loaded choice differs from independently decoded native roster/selector"
        }
        val input = requireNotNull(epoch.inputCheckpoint)
        when (selector.source) {
            TargetSource.NATURAL_UNMODIFIED_BLOCK -> {
                val reviewedScope = suiteMapper.treeToValue(ready.path("block_mutation_scope"), BoundFile::class.java)
                requireLoadedSourceStateAndScopedInterval(store, input, reviewedScope, requestsFile.parent,
                    raw.path("observation").path("payload").path("block_mutation_pre_action_prefix"),
                    store.verify(input).bindings, epoch.id, epoch.nativeSessionId,
                    selector.bounds.dimension, requireNotNull(reconstructed.selected))
                if (selector.schema == "bc.playtest_target_selector.v2") {
                    val key = trial.request.caseId to trial.request.trialId
                    val frozen = frozenByTrial.getValue(key)
                    val naturalEvent = naturalEvents.single { it.path("case_id").asText() == key.first &&
                        it.path("trial_id").asText() == key.second }
                    require(naturalEvent.fieldNames().asSequence().toSet() == setOf("time", "type",
                        "campaign_id", "lineage_id", "purpose", "epoch_id", "branch_id",
                        "native_session_id", "case_id", "trial_id", "target", "loaded_attempt",
                        "profile", "selector_id") &&
                        events.indexOf(naturalEvent) > eventIndex &&
                        (indexed.index + 1 >= expectedActions.size || events.indexOf(naturalEvent) <
                            events.indexOf(actionEvents[(indexed.index + 1) * 2])) &&
                        naturalEvent.path("selector_id").asText() == selector.id &&
                        !frozen.ledger.attempts.single().observedAt.isBefore(
                            Instant.parse(completionEvent.path("time").asText())) &&
                        !frozen.ledger.attempts.single().observedAt.isAfter(
                            Instant.parse(naturalEvent.path("time").asText())) &&
                        naturalEvent.path("loaded_attempt") == event.path("attempt") &&
                        naturalEvent.path("profile") == suiteMapper.valueToTree<JsonNode>(selector.coverageProfile) &&
                        naturalEvent.path("target") == suiteMapper.valueToTree<JsonNode>(
                            eventFile.parent.resolve("trial-${key.first}-${key.second}-natural-$sequence.json")
                                .let { path -> BoundFile(path.toString(), Hashes.sha256(path)) })) {
                        "controller natural-target freeze did not follow exact loaded attempt before next dispatch"
                    }
                    val profile = verifyReviewedStage1Coverage(selector, clientCandidate, serverCandidate,
                        supportCandidate)
                    val rebuilt = freezeReviewedNaturalTarget(store, input, selector, profile,
                        reconstructed, suiteMapper.treeToValue(event.path("attempt"), BoundFile::class.java),
                        reviewedScope, requestsFile.parent, raw.path("observation").path("payload"),
                        store.verify(input).bindings, epoch.id, epoch.nativeSessionId,
                        frozen.ledger.attempts.single().observedAt)
                    require(rebuilt == frozen && frozen.bound.context == context &&
                        frozen.output.catalogueSha256 == selector.catalogueSha256) {
                        "frozen natural target differs from independently reconstructed provider/roster/MCA/interval"
                    }
                }
            }
            TargetSource.NEWLY_GENERATED_TERRAIN -> {
                val scope = PrelaunchBlockBirthScope(
                    suiteMapper.treeToValue(ready.path("block_birth_scope"), BoundFile::class.java),
                    suiteMapper.treeToValue(ready.path("block_birth_parent_absence"), BoundFile::class.java))
                val specs = epoch.caseResults.map { ref ->
                    val witness = witnesses.getValue(boundInput(indexBase, suiteMapper.valueToTree(ref)).toRealPath())
                    suiteMapper.treeToValue(manifest.path("cases").single { it.path("id").asText() ==
                        witness.request.caseId }, SuiteCase::class.java)
                }
                requireLoadedBirthStateAndPostbirthInterval(store, input, specs,
                    manifest.path("authoring_catalogue").path("sha256").asText(), scope,
                    eventFile.parent, requestsFile.parent,
                    raw.path("observation").path("payload").path("block_birth_pre_action_prefix"),
                    store.verify(input).bindings, epoch.id, epoch.nativeSessionId,
                    selector.bounds.dimension, requireNotNull(reconstructed.selected))
            }
            else -> error("unreviewed loaded-only target source")
        }
    }
    require(naturalEvents.size == loadedActions.count { (_, action) ->
        val trial = action.first
        val spec = suiteMapper.treeToValue(manifest.path("cases").single { it.path("id").asText() ==
            trial.request.caseId }, SuiteCase::class.java)
        spec.targetBindings.any { it.path("schema").asText() == "bc.playtest_target_selector.v2" }
    }) { "natural target freeze missing or injected" }
    val reviewedHovers = expectedActions.withIndex().filter { indexed ->
        indexed.value.first.request.caseId == "control.client.emi-navigation" &&
            indexed.value.second.action.id == "client.emi_hover"
    }
    val hoverEvents = events.filter { it.path("type").asText() == "emi_hover_review_bound" }
    require(hoverEvents.size == reviewedHovers.size) { "EMI actual hover review event missing or injected" }
    reviewedHovers.zip(hoverEvents).forEach { (indexed, event) ->
        val (trial, step, raw) = indexed.value
        val pointIndex = (0 until indexed.index).lastOrNull { at ->
            expectedActions[at].first.request == trial.request &&
                expectedActions[at].second.action.id == "client.input_point"
        } ?: error("EMI hover lacked a prior real OS pointer move")
        val following = requireNotNull(expectedActions.getOrNull(indexed.index + 1)).second.action
        val projection = raw.path("observation").path("payload").path("projection_sha256").asText()
        require(following.id == "client.input_hold" && following.parameters.path("kind").asText() in
            setOf("emi_mouse_left", "emi_mouse_right") &&
            following.parameters.path("hover_projection_sha256").asText() == projection &&
            event.fieldNames().asSequence().toSet() == setOf("time", "type", "campaign_id",
                "lineage_id", "purpose", "epoch_id", "branch_id", "native_session_id",
                "case_id", "trial_id", "point_sequence", "hover_sequence",
                "reviewed_target_sha256", "hover_projection_sha256") &&
            event.path("campaign_id").asText() == epoch.campaignId &&
            event.path("lineage_id").asText() == epoch.lineageId &&
            event.path("epoch_id").asText() == epoch.id &&
            event.path("native_session_id").asText() == epoch.nativeSessionId &&
            event.path("purpose").asText() == epoch.purpose &&
            event.path("branch_id").asText() == epoch.branchId &&
            event.path("case_id").asText() == trial.request.caseId &&
            event.path("trial_id").asText() == trial.request.trialId &&
            event.path("point_sequence").asLong(-1) ==
                expectedActions[pointIndex].third.path("observation").path("request_sequence").asLong(-2) &&
            event.path("hover_sequence").asLong(-1) ==
                raw.path("observation").path("request_sequence").asLong(-2) &&
            event.path("reviewed_target_sha256").asText() == jsonFingerprint(trial.request.parameters) &&
            event.path("hover_projection_sha256").asText() == projection &&
            events.indexOf(event) > events.indexOf(actionEvents[indexed.index * 2 + 1]) &&
            events.indexOf(event) < events.indexOf(actionEvents[(indexed.index + 1) * 2])) {
            "EMI hover review was not between actual rendered source and physical mouse press"
        }
    }
    var serverSeq = 2L
    var clientSeq = 1L
    epoch.caseResults.forEach { ref ->
        val trial = witnesses.getValue(boundInput(indexBase, suiteMapper.valueToTree(ref)).toRealPath())
        val raw = rows(trial.rawFile)
        val plan = trial.driver.planTrial(trial.request)
        require(plan.size == raw.size) { "trial raw action roster incomplete: ${trial.request.trialId}" }
        plan.zip(raw).forEach { (step, row) ->
            val observed = row.path("observation")
            when (step.action.source) {
                "server" -> {
                    require(observed.path("request_sequence").asLong(-1) == serverSeq &&
                        grouped[serverSeq]?.lastOrNull() == observed) { "trial server terminal differs from epoch stream" }
                    serverSeq++
                }
                "client" -> {
                    require(observed.path("request_sequence").asLong(-1) == clientSeq &&
                        suiteMapper.treeToValue(observed, NativeObservation::class.java) == clientTerminals[(clientSeq - 1).toInt()]) {
                        "trial client terminal differs from separately verified client stream"
                    }
                    clientSeq++
                }
                else -> error("unknown native source")
            }
        }
        val cleanup = grouped[serverSeq]?.lastOrNull()
        require(cleanup != null && cleanup.path("driver").asText() == "fixture.cleanup" && cleanup.path("case_id").asText() == trial.request.caseId &&
            cleanup.path("payload").path("cleanup_complete").isBoolean && cleanup.path("payload").path("cleanup_complete").asBoolean()) {
            "trial cleanup not an observed terminal"
        }
        serverSeq++
    }
    require(serverSeq == requests.size.toLong() + 1 && clientSeq == clientTerminals.size.toLong() + 1) {
        "unaccounted server/client epoch request"
    }
}

internal fun requireFreshV3EpochToken(requests: List<JsonNode>, nativeSessionId: String,
    seenTokens: MutableSet<String>) {
    require(requests.isNotEmpty() && nativeSessionId.isNotBlank())
    val token = requests.first().path("token").asText()
    require(shaPattern.matches(token) && token !in seenTokens && requests.all {
        it.path("token").asText() == token && it.path("run_id").asText() == nativeSessionId
    }) { "replayed/cross-epoch token or session" }
    seenTokens.add(token)
}

internal fun requireV3(kind: String, node: JsonNode) {
    require(kind in setOf("evidence_index", "campaign", "epoch", "case_result", "trial") &&
        node.path("schema").asText() == "bc.playtest_$kind.v3") { "v1/v2/unknown $kind receipt cannot qualify v3" }
}
internal fun candidateEmiJarSha256(clientCandidate: Path, serverCandidate: Path): String {
    java.util.zip.ZipFile(clientCandidate.toFile()).use { client ->
        val entry = requireNotNull(client.getEntry("manifest.json")) { "reviewed client CurseForge manifest missing" }
        require(entry.size in 1..2_000_000)
        val manifest = client.getInputStream(entry).use { suiteMapper.readTree(it) }
        val emiFiles = manifest.path("files").filter { it.path("projectID").asInt(-1) == 580555 }
        require(manifest.path("files").isArray && emiFiles.size == 1 &&
            emiFiles.single().path("fileID").asInt(-1) == 8081375 &&
            emiFiles.single().path("required").asBoolean(false)) {
            "client does not uniquely select the reviewed EMI 1.1.24 CurseForge artifact"
        }
    }
    return java.util.zip.ZipFile(serverCandidate.toFile()).use { server ->
        val jar = requireNotNull(server.getEntry("better-content-server/mods/emi-1.1.24+1.20.1+forge.jar")) {
            "selected server candidate lacks the exact client-selected EMI JAR"
        }
        val metadata = requireNotNull(server.getEntry("better-content-server/mods/emi.pw.toml"))
        require(jar.size in 1..50_000_000 && metadata.size in 1..16_384)
        val lines = server.getInputStream(metadata).bufferedReader().use { it.readLines().map { row -> row.trim() } }
        for (expected in listOf("filename = \"emi-1.1.24+1.20.1+forge.jar\"", "side = \"both\"",
            "hash-format = \"sha1\"", "hash = \"ea320200878e4a49196760234a22da763671520a\"",
            "file-id = 8081375", "project-id = 580555")) {
            require(lines.count { it == expected } == 1) { "reviewed EMI pack metadata differs: $expected" }
        }
        val bytes = server.getInputStream(jar).use { it.readNBytes(50_000_001) }
        require(bytes.size.toLong() == jar.size &&
            java.security.MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) } ==
                "ea320200878e4a49196760234a22da763671520a") {
            "selected EMI bytes differ from the reviewed client/server pack SHA-1 pin"
        }
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

private fun hashNode(node: JsonNode): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(suiteMapper.writeValueAsBytes(node)).joinToString("") { "%02x".format(it) }
