package com.bettercontent.tests.suite

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.CandidatePair
import com.bettercontent.tests.suite.drivers.DriverRegistry
import com.bettercontent.tests.suite.v2.contracts.validateV2Reviewed
import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

internal data class V2CampaignRequest(val root: Path, val manifestPath: Path, val manifest: JsonNode,
    val pair: CandidatePair, val cases: List<SuiteCase>, val bindings: Map<String, JsonNode>,
    val support: Path, val diagnostic: Boolean)

/** Shared authoring gate for campaign and qualifier. It never starts a session or infers approval. */
internal fun reviewedV2Problems(root: Path, m: JsonNode): List<String> {
    val errors = validateV2Reviewed(root, m).toMutableList()
    fun need(ok: Boolean, text: String) { if (!ok) errors += text }
    need(!m.has("conversion_pending") && m.path("acquisitions").none { it.path("conversion_pending").asBoolean() }, "v1 conversion not reviewed")
    val cases = runCatching { m.path("cases").map { suiteMapper.treeToValue(it, SuiteCase::class.java) } }
        .getOrElse { errors += "case contract malformed: ${it.message}"; return errors }
    need(cases.isNotEmpty() && cases.map { it.id }.distinct().size == cases.size, "empty/duplicate cases")
    need(cases.flatMap { it.noteIds }.toSet().containsAll(requiredNotes), "required note coverage incomplete")
    val ids = cases.map { it.id }.toSet()
    val repos = m.path("repositories")
    need(repos.isArray && repos.any { it.path("id").asText() == "better-content-modpack" }, "repository scope incomplete")
    val owners = repos.map { it.path("id").asText() }.toSet()
    need(owners.size == repos.size(), "duplicate repository")
    cases.forEach { c ->
        need(c.id.matches(idPattern) && c.owner in owners && c.layer in caseLayers, "invalid case identity: ${c.id}")
        need(c.assertions.isNotEmpty() && c.assertions.distinct().size == c.assertions.size && c.evidenceKinds.isNotEmpty(), "assertion/evidence missing: ${c.id}")
        need(c.dependencies.all { it in ids && it != c.id } && c.timeoutSeconds in 1..86400, "invalid dependencies/deadline: ${c.id}")
        need(listOf(c.preconditions, c.actions, c.expected, c.cleanup).all { it.isNotEmpty() && it.all(String::isNotBlank) }, "procedure incomplete: ${c.id}")
        need(c.actions != c.expected, "action text is not an independent oracle: ${c.id}")
        need(c.implementation.state == "authored" && c.implementation.symbol.isNotBlank() &&
            c.implementation.path.isNotBlank() && Files.isRegularFile(resolveSuitePath(root, c.implementation.path)), "missing implementation: ${c.id}")
    }
    val rosters = m.path("rosters")
    need(rosters.isArray && rosters.map { it.path("kind").asText() }.toSet() == requiredRosters && rosters.size() == requiredRosters.size, "required rosters missing/duplicate")
    rosters.forEach { roster ->
        need(roster.path("complete").isBoolean && roster.path("complete").asBoolean() && roster.path("gaps").isArray && roster.path("gaps").isEmpty,
            "incomplete roster: ${roster.path("kind")}")
        need(roster.path("entries").isObject && !roster.path("entries").isEmpty && roster.path("entries").all { it.isArray && !it.isEmpty && it.all { id -> id.asText() in ids } }, "uncovered roster members")
    }
    val p = runCatching { suiteMapper.treeToValue(m.path("profile"), SuiteProfile::class.java) }.getOrNull()
    need(p != null && p.seed == 424242L && p.viewDistance == 10 && p.simulationDistance == 10 &&
        p.cohorts == listOf(8, 16, 32) && p.cohortSeconds == 600 && p.recoverySeconds == 120 &&
        p.recoveryStableSeconds == 30 && p.tpsWindowSeconds == 30 && p.minimumTps == 19.0 &&
        p.treeMinimum == 100 && p.treeMinimumSites == 5 && p.treeMaximumSites == 20 && p.treeDynamicFraction == 0.95 &&
        p.foodSampleSize == 10 && p.threatAttempts == 3 && p.visualProfiles == listOf("1280x720@2", "1920x1080@3") &&
        p.serverHeapMib == 16384 && p.hardwareProfile.isNotBlank() && p.participantKind == "server-fake-player" &&
        p.timingMode == "native-windows-and-boundaries", "fixed suite profile missing or changed")
    val assertions = cases.flatMap { c -> c.assertions.map { Triple(it, c.layer, c.context) } }
    need(assertions.distinct().size == assertions.size, "duplicate assertion ownership")
    runCatching {
        val discoveryPath = boundInput(root, m.path("discovery"))
        val discovery = suiteMapper.readTree(discoveryPath.toFile())
        need(discovery.path("schema").asText() == "bc.playtest_discovery.v2" && discovery.path("status").asText() == "observed" &&
            discovery.path("qualification_eligible").isBoolean && !discovery.path("qualification_eligible").asBoolean() &&
            discovery.path("cleanup_complete").asBoolean() && discovery.path("saved_and_validated").asBoolean(), "discovery lifecycle incomplete")
        for (side in listOf("client", "server")) need(discovery.path("candidate").path("${side}_sha256").asText() == m.path("candidate").path(side).path("sha256").asText(), "discovery candidate drift")
        need(discovery.path("test_support_sha256").asText() == m.path("test_support").path("sha256").asText(), "discovery support drift")
        val inventory = discovery.path("inventory")
        need(inventory.path("complete").isBoolean && inventory.path("complete").asBoolean() && inventory.path("gaps").isArray && inventory.path("gaps").isEmpty,
            "effective inventory is incomplete; unresolved native discovery gaps")
        need(inventory.path("rosters").isObject && inventory.path("rosters").fieldNames().asSequence().toSet() == requiredRosters, "effective roster coverage incomplete")
        rosters.forEach { roster -> need(inventory.path("rosters").path(roster.path("kind").asText()).map { it.asText() }.toSet() ==
            roster.path("entries").fieldNames().asSequence().toSet(), "effective roster drift: ${roster.path("kind")}") }
        val nativeRoot = Path.of(discovery.path("native_directory").asText()).toRealPath()
        need(inventory.path("files").isArray && !inventory.path("files").isEmpty, "discovery raw inventory missing")
        inventory.path("files").forEach { ref ->
            val file = boundInput(nativeRoot, ref)
            need(file.toRealPath().startsWith(nativeRoot), "discovery raw file outside owned native directory")
        }
    }.onFailure { errors += "discovery binding: ${it.message}" }
    return errors
}

internal fun v2CampaignRequest(root: Path, manifestPath: Path, client: Path, server: Path, selected: List<String>): V2CampaignRequest {
    val manifest = suiteMapper.readTree(manifestPath.toFile())
    val errors = reviewedV2Problems(root, manifest)
    require(errors.isEmpty()) { "Campaign blocked before world creation:\n${errors.joinToString("\n")}" }
    return buildCampaignRequest(root, manifestPath, manifest, client, server, selected, checkpointTrials = false)
}

/** Shared driver gate, not a receipt conversion. Callers first validate their real manifest version. */
internal fun buildCampaignRequest(root: Path, manifestPath: Path, manifest: JsonNode, client: Path, server: Path,
    selected: List<String>, checkpointTrials: Boolean): V2CampaignRequest {
    val pair = boundPair(root, manifest, client, server)
    val allCases = manifest.path("cases").map { suiteMapper.treeToValue(it, SuiteCase::class.java) }
    val campaignCases = allCases.filter { it.layer == "campaign" }
    require(campaignCases.isNotEmpty())
    require(selected.distinct().size == selected.size && selected.all { id -> campaignCases.any { it.id == id } }) { "invalid diagnostic case selection" }
    val requested = if (selected.isEmpty()) campaignCases.map { it.id }.toSet() else selected.toSet()
    val diagnosticGetter = checkpointTrials && manifest.path("schema").asText() ==
        "bc.playtest_manifest.v3" && manifest.path("purpose").asText() == "diagnostic" &&
        requested == setOf(com.bettercontent.tests.suite.checkpoints.DIAGNOSTIC_GETTER_CASE) &&
        selected == listOf(com.bettercontent.tests.suite.checkpoints.DIAGNOSTIC_GETTER_CASE) &&
        campaignCases.count { it.id == com.bettercontent.tests.suite.checkpoints.DIAGNOSTIC_GETTER_CASE } == 1
    require(!requested.contains(com.bettercontent.tests.suite.checkpoints.DIAGNOSTIC_GETTER_CASE) ||
        diagnosticGetter) { "diagnostic-only getter cannot enter ordinary/full campaign ordering" }
    val reviewedDiagnosticIds =
        com.bettercontent.tests.suite.checkpoints.reviewedDiagnosticPositiveCases.keys +
        com.bettercontent.tests.suite.checkpoints.reviewedStage1NegativeCases.keys
    val scopedDiagnosticIds = requested.intersect(reviewedDiagnosticIds)
    require(scopedDiagnosticIds.isEmpty() || (checkpointTrials && manifest.path("schema").asText() ==
        "bc.playtest_manifest.v3" && manifest.path("purpose").asText() == "diagnostic" &&
        selected.isNotEmpty() &&
        com.bettercontent.tests.suite.checkpoints.DIAGNOSTIC_GETTER_CASE !in requested &&
        "control.mutation.setup-delegated" in scopedDiagnosticIds)) {
        "diagnostic setup/controls cannot be promoted into ordinary/full campaign ordering"
    }
    val ordered = if (diagnosticGetter) listOf(campaignCases.single { it.id ==
        com.bettercontent.tests.suite.checkpoints.DIAGNOSTIC_GETTER_CASE })
        else if (scopedDiagnosticIds.isNotEmpty())
            DriverRegistry.ordered(campaignCases, requested - scopedDiagnosticIds) +
                listOf("control.mutation.setup-delegated", "control.mutation.delegated-positive",
                    "control.mutation.section-positive", "control.mutation.transient-positive",
                    "control.mutation.sections-negative", "control.mutation.io-append-negative")
                    .filter { it in scopedDiagnosticIds }.map { id -> campaignCases.single { it.id == id } }
        else DriverRegistry.ordered(campaignCases, requested)
    val bindings = manifest.path("driver_bindings").associateBy { it.path("case_id").asText() }
    require(bindings.size == manifest.path("driver_bindings").size()) { "duplicate driver binding" }
    val support = boundInput(root, manifest.path("test_support"))
    require(Files.isSameFile(support, boundInput(root, manifest.path("protocol").path("support_capabilities")))) {
        "protocol capability descriptor must bind its containing support artifact"
    }
    val capabilities = supportCapabilities(support)
    val clientCapabilities = if (checkpointTrials) supportCapabilities(support, "client") else emptySet()
    require("fixture.cleanup" in capabilities)
    ordered.forEach { spec ->
        val binding = requireNotNull(bindings[spec.id]) { "missing driver binding: ${spec.id}" }
        // Control drivers cannot impersonate broader, unimplemented catalogue promises.
        require(binding.path("driver_id").asText() == spec.id) { "driver/case identity mismatch: ${spec.id}" }
        val driver = if (spec.id in scopedDiagnosticIds || diagnosticGetter)
            requireNotNull(DriverRegistry.diagnosticOnly(spec.id))
            else requireNotNull(DriverRegistry.resolve(spec.id))
        require(spec.assertions.toSet() == driver.assertionIds &&
            (driver.assertionIds.isNotEmpty() || (diagnosticGetter || spec.id in scopedDiagnosticIds) &&
                spec.assertions.isEmpty())) {
            "case assertions exceed driver ownership: ${spec.id}" }
        require(spec.evidenceKinds.toSet() == setOf("native-interaction")) { "driver cannot supply requested evidence kinds: ${spec.id}" }
        val provisioned = driver.fixtureProvisionedTargets(binding.path("parameters"))
        require(spec.targets.containsAll(provisioned)) { "fixture targets undeclared: ${spec.id}" }
        val acquisitionProofs = manifest.path("acquisitions").filter { a -> a.path("coverage").any { it.asText() == spec.id } }.map { it.path("id").asText() }
        require(provisioned.none { it in acquisitionProofs }) { "provisioning cannot prove acquisition: ${spec.id}" }
        driver.requiredAcquisitionCases(binding.path("parameters")).forEach { (item, proof) ->
            val acquisition = manifest.path("acquisitions").singleOrNull { it.path("id").asText() == item }
            val proofCase = allCases.singleOrNull { it.id == proof }
            require(proof in spec.dependencies && proofCase != null && item in proofCase.targets &&
                proofCase.layer in setOf("native", "campaign") && acquisition != null &&
                acquisition.path("coverage").any { it.asText() == proof }) { "independent acquisition prerequisite missing: ${spec.id}/$item" }
        }
        if (spec.id == "survival.food") {
            val foodRoster = manifest.path("rosters").single { it.path("kind").asText() == "foods" }.path("entries")
            val candidates = binding.path("parameters").path("candidates").map { it.path("id").asText() }
            require(candidates.distinct().size == candidates.size && candidates.toSet() == foodRoster.fieldNames().asSequence().toSet()) {
                "food sample candidates differ from complete effective food roster"
            }
        }
        val plans = if (checkpointTrials && driver is com.bettercontent.tests.suite.drivers.TrialCaseDriver) {
            val trials = driver.expandTrials(binding.path("parameters"), "origin")
            require(trials.isNotEmpty() && trials.map { it.trialId }.distinct().size == trials.size)
            trials.map { driver.planTrial(it) }
        } else listOf(driver.plan(binding.path("parameters")))
        plans.forEach { steps -> require(steps.isNotEmpty() && steps.map { it.name }.distinct().size == steps.size) }
        val actions = plans.flatten().map { it.action.id }.toSet()
        require(actions == binding.path("required_actions").map { it.asText() }.toSet() && plans.flatten().all {
            it.action.id in (if (it.action.source == "server") capabilities else clientCapabilities)
        }) { "unsupported/misbound source-aware actions: ${spec.id}" }
    }
    require(Hashes.sha256(manifestPath).matches(shaPattern))
    return V2CampaignRequest(root, manifestPath, manifest, pair, ordered, bindings, support, selected.isNotEmpty())
}
