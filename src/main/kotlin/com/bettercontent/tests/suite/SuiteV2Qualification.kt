package com.bettercontent.tests.suite

import com.bettercontent.tests.CandidateLocator
import com.bettercontent.tests.Hashes
import com.bettercontent.tests.computeLogSignatures
import com.bettercontent.tests.release.validateOverworldTeleSoakReceipt
import com.bettercontent.tests.release.validateSmokeEvidence
import com.bettercontent.tests.release.validateTeleSoakReceipt
import com.bettercontent.tests.release.validateVisualReview
import com.bettercontent.tests.suite.drivers.DriverRegistry
import com.bettercontent.tests.suite.drivers.DriverObservation
import com.bettercontent.tests.suite.drivers.DriverOutcome
import com.bettercontent.tests.suite.drivers.evaluateCase
import com.bettercontent.tests.suite.repositories.RepositoryReportExpectation
import com.bettercontent.tests.suite.repositories.RepositoryReportImporter
import java.time.ZoneId
import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

/** V2 receipts are never implicitly upgraded from v1. This gate starts no Minecraft processes. */
internal fun qualifyV2Suite(root: Path, manifestPath: Path, indexPath: Path): Path {
    val catalogue = Hashes.sha256(manifestPath)
    val indexHash = Hashes.sha256(indexPath)
    val manifest = suiteMapper.readTree(manifestPath.toFile())
    val problems = reviewedV2Problems(root, manifest)
    require(problems.isEmpty()) { "v2 authoring/discovery incomplete:\n${problems.joinToString("\n")}" }
    val cases = manifest.path("cases").map { suiteMapper.treeToValue(it, SuiteCase::class.java) }
    val campaignCases = cases.filter { it.layer == "campaign" }
    val base = indexPath.toAbsolutePath().parent
    val index = suiteMapper.readTree(indexPath.toFile())
    requireV2ReceiptSchema("evidence_index", index)
    require(index.path("catalogue_sha256").asText() == catalogue) { "index bound to different catalogue" }
    // No generic support action can stand in for a missing implemented full-family case.
    DriverRegistry.requireSupported(campaignCases.map { it.id })
    val client = boundInput(root, manifest.path("candidate").path("client"))
    val server = boundInput(root, manifest.path("candidate").path("server"))
    val pair = CandidateLocator.locate(client, server)
    // Shared campaign preflight is pure: no session/world creation. Do not drift from its
    // exact roster/driver/acquisition validation when accepting a claimed campaign receipt.
    val campaignPreflight = v2CampaignRequest(root, manifestPath, client, server, emptyList())
    require(!campaignPreflight.diagnostic && campaignPreflight.cases.map { it.id }.toSet() == campaignCases.map { it.id }.toSet() &&
        campaignPreflight.pair.clientSha256 == pair.clientSha256 && campaignPreflight.pair.serverSha256 == pair.serverSha256) {
        "v2 campaign preflight differs from qualifier"
    }
    val support = boundInput(root, manifest.path("test_support"))
    val supportActions = supportCapabilities(support)
    val boundProtocol = boundInput(root, manifest.path("protocol").path("support_capabilities"))
    require(Files.isSameFile(support, boundProtocol)) { "protocol descriptor differs from support artifact" }
    val authoringTime = Instant.parse(manifest.path("authoring").path("reviewed_at").asText())
    val bindings = manifest.path("driver_bindings").associateBy { it.path("case_id").asText() }
    require(bindings.size == manifest.path("driver_bindings").size() && bindings.keys == campaignCases.map { it.id }.toSet()) { "driver bindings incomplete or duplicate" }
    campaignCases.forEach { c ->
        val binding = bindings.getValue(c.id)
        val driver = requireNotNull(DriverRegistry.resolve(c.id)) { "no independent oracle for ${c.id}" }
        require(driver.assertionIds.isNotEmpty() && driver.assertionIds == c.assertions.toSet()) { "driver assertion ownership mismatch: ${c.id}" }
        validateDriverAcquisitionBoundary(c, driver.fixtureProvisionedTargets(binding.path("parameters")),
            driver.requiredAcquisitionCases(binding.path("parameters")), cases, manifest.path("acquisitions"))
        require(binding.path("driver_id").asText() == c.id)
        val plan = driver.plan(binding.path("parameters"))
        require(plan.isNotEmpty() && plan.map { it.name }.distinct().size == plan.size &&
            plan.map { it.action.id }.toSet() == binding.path("required_actions").map { it.asText() }.toSet() &&
            supportActions.containsAll(plan.map { it.action.id })) { "unsupported native action for ${c.id}" }
    }
    val caseRefs = index.path("case_results")
    require(caseRefs.isArray && caseRefs.size() == cases.size && caseRefs.size() ==
        caseRefs.map { it.path("path").asText() }.distinct().size) { "missing/duplicate case receipts" }
    val v1Shape = v2LegacyShape(manifest, cases)
    val resultFiles = caseRefs.map { boundInput(base, it) }
    val results = resultFiles.map { path ->
        val receipt = suiteMapper.readValue(path.toFile(), CaseResult::class.java)
        requireV2ReceiptSchema("case_result", suiteMapper.readTree(path.toFile()))
        val spec = cases.singleOrNull { it.id == receipt.caseId } ?: error("unknown case receipt: ${receipt.caseId}")
        // Existing strict assertion and file-binding checks are reused only after requiring v2 on disk.
        validateCaseResult(v1Shape, spec, receipt.copy(schema = "bc.playtest_case_result.v1"), catalogue, path.parent)
        receipt
    }
    require(results.map { it.caseId }.toSet() == cases.map { it.id }.toSet() &&
        results.map { it.caseId }.distinct().size == results.size) { "partial/duplicate case coverage" }
    val resultMap = results.associateBy { it.caseId }
    cases.forEach { c -> c.dependencies.forEach { require(resultMap.getValue(it).state == "passed") } }
    campaignCases.forEach { c ->
        val driver = requireNotNull(DriverRegistry.resolve(c.id))
        val parameters = bindings.getValue(c.id).path("parameters")
        val provisioned = driver.fixtureProvisionedTargets(parameters)
        val receipt = resultMap.getValue(c.id)
        require(receipt.provisionedTargets.toSet() == provisioned && receipt.provisionedTargets.size == provisioned.size) {
            "fixture provision ledger differs from driver: ${c.id}"
        }
        driver.requiredAcquisitionCases(parameters).forEach { (target, proof) ->
            require(resultMap.getValue(proof).provisionedTargets.isEmpty()) { "fixture used in natural acquisition proof: $proof/$target" }
        }
    }

    val campaignPath = boundInput(base, index.path("campaign"))
    val campaignRaw = suiteMapper.readTree(campaignPath.toFile())
    requireV2ReceiptSchema("campaign", campaignRaw)
    val campaign = suiteMapper.treeToValue(campaignRaw.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().apply {
        remove(listOf("native_protocol", "native_requests", "driver_bindings_sha256", "case_results", "failure")); put("schema", "bc.playtest_campaign.v1")
    }, CampaignReceipt::class.java)
    val started = Instant.parse(campaign.startedAt); val completed = Instant.parse(campaign.completedAt)
    require(!started.isBefore(authoringTime) && !completed.isBefore(started) && !completed.isAfter(Instant.now()) &&
        campaign.status == "passed" && !campaign.diagnostic && campaign.instrumented && campaign.seed == 424242L &&
        campaign.catalogueSha256 == catalogue && campaign.profileSha256 == profileFingerprint(v1Shape.profile) &&
        campaign.candidate == candidateHashes(v1Shape) && campaign.testSupportSha256 == manifest.path("test_support").path("sha256").asText()) { "campaign timing/status/candidate/profile mismatch" }
    val hardware = suiteMapper.readTree(boundInput(campaignPath.parent, suiteMapper.valueToTree(campaign.hardware)).toFile())
    require(hardware.path("schema").asText() == "bc.playtest_hardware.v2" &&
        hardware.path("profile").asText() == v1Shape.profile.hardwareProfile &&
        hardware.path("server_heap_mib").asInt(-1) == v1Shape.profile.serverHeapMib &&
        hardware.path("view_distance").asInt(-1) == 10 && hardware.path("simulation_distance").asInt(-1) == 10 &&
        listOf("cpu", "jvm", "contention_notes").all { hardware.path(it).asText().isNotBlank() }) { "actual hardware/profile observation missing" }
    val overrides = suiteMapper.readTree(boundInput(campaignPath.parent, suiteMapper.valueToTree(campaign.fixtureOverrides)).toFile())
    require(overrides.path("schema").asText() == "bc.playtest_fixture_overrides.v2" &&
        overrides.path("overrides").isArray && overrides.path("overrides").all { it.path("setting").asText().isNotBlank() &&
            it.path("purpose").asText().isNotBlank() && it.has("value") &&
            it.path("changes_asserted_behavior").isBoolean && !it.path("changes_asserted_behavior").asBoolean() }) { "fixture override incomplete/outcome altering" }
    require(campaignRaw.path("driver_bindings_sha256").asText().matches(shaPattern) &&
        campaignRaw.path("driver_bindings_sha256").asText() == shaNode(manifest.path("driver_bindings"))) { "campaign driver bindings changed" }
    val nativeProtocol = boundInput(campaignPath.parent, campaignRaw.path("native_protocol"))
    val nativeRequests = boundInput(campaignPath.parent, campaignRaw.path("native_requests"))
    require(nativeProtocol.fileName.toString() == "observations.jsonl" && Files.size(nativeProtocol) in 1..100_000_000 &&
        Files.size(nativeRequests) in 1..100_000_000) { "raw native protocol/requests missing or unbounded" }
    val requestRows = Files.readAllLines(nativeRequests).also { require(it.isNotEmpty() && it.all(String::isNotBlank)) }
        .map { line -> require(line.length <= PROTOCOL_LINE_LIMIT); suiteMapper.readTree(line) }
    require(requestRows.map { it.path("sequence").asLong() } == (1L..requestRows.size.toLong()).toList() &&
        requestRows.all { it.path("schema").asText() == "bc.playtest_action.v2" && it.path("run_id").asText() == campaign.runId &&
            it.path("token").asText().matches(shaPattern) && it.path("parameters").isObject }) { "native requests not exact/ordered/run-bound" }
    val protocolRows = Files.readAllLines(nativeProtocol).also { require(it.isNotEmpty() && it.all(String::isNotBlank)) }
        .map { line -> require(line.length <= PROTOCOL_LINE_LIMIT); suiteMapper.readTree(line) }
    require(protocolRows.all { it.path("schema").asText() == "bc.playtest_observation.v2" &&
        it.path("run_id").asText() == campaign.runId && it.path("kind").asText() in setOf("started", "sample", "completed", "failed") } &&
        protocolRows.map { it.path("sequence").asLong() } == (1L..protocolRows.size.toLong()).toList()) { "native protocol not exact, ordered or run-bound" }
    val expectedNative = mutableListOf(PlannedNativeAction("campaign.inventory", "inventory.discover", suiteMapper.createObjectNode(), 12000))
    campaignPreflight.cases.forEach { c ->
        val parameters = bindings.getValue(c.id).path("parameters")
        DriverRegistry.resolve(c.id)!!.plan(parameters).forEach { step ->
            expectedNative += PlannedNativeAction(c.id, step.action.id, step.action.parameters, step.action.timeoutTicks)
        }
        expectedNative += PlannedNativeAction(c.id, "fixture.cleanup", suiteMapper.createObjectNode(), 2400)
    }
    verifyV2NativeProtocol(requestRows, protocolRows, expectedNative)
    val requests = requestRows.associateBy { it.path("sequence").asLong() }
    val observationsByRequest = protocolRows.groupBy { it.path("request_sequence").asLong() }
    require(observationsByRequest.keys == requests.keys && requests.all { (seq, request) ->
        val lines = observationsByRequest.getValue(seq)
        lines.first().path("kind").asText() == "started" && lines.last().path("kind").asText() == "completed" &&
            lines.count { it.path("kind").asText() == "started" } == 1 && lines.count { it.path("kind").asText() == "completed" } == 1 &&
            lines.all { obs -> obs.path("run_id").asText() == request.path("run_id").asText() &&
                obs.path("case_id").asText() == request.path("case_id").asText() &&
                obs.path("driver").asText() == request.path("driver").asText() &&
                obs.path("token").asText() == request.path("token").asText() && obs.path("source").asText() == "server" }
    }) { "native request/observation mismatch or failed action" }
    require(campaignRaw.path("case_results").isArray &&
        campaignRaw.path("case_results").map { boundInput(campaignPath.parent, it).toRealPath() }.toSet() ==
        resultFiles.filterIndexed { index, _ -> results[index].caseId in campaign.caseIds }.map { it.toRealPath() }.toSet()) { "campaign case receipts differ from evidence index" }
    verifyIndependentNativeCases(campaignCases, bindings, results, resultFiles, requests, observationsByRequest)
    val worldId = UUID.fromString(campaign.worldId).toString()
    require(campaign.worldId == worldId && campaign.runId.isNotBlank() && campaign.worldsCreated == 1 &&
        campaign.cleanupComplete && campaign.savedAndValidated && campaign.survivingOwnedPids.isEmpty()) { "campaign world/cleanup invalid" }
    val world = Path.of(campaign.worldDirectory).toRealPath()
    require(world.startsWith(campaignPath.parent.toRealPath())) { "unowned world" }
    val marker = boundInput(campaignPath.parent, suiteMapper.valueToTree(campaign.worldMarker))
    require(marker.toRealPath() == world.resolve(".bc-suite-world-id") && Files.readString(marker).trim() == worldId) { "world marker mismatch" }
    require(campaign.caseIds.size == campaign.caseIds.distinct().size && campaign.caseIds.toSet() == campaignCases.map { it.id }.toSet() &&
        campaign.observedRosters.keys == requiredRosters && v1Shape.rosters.all { campaign.observedRosters[it.kind]?.toSet() == it.entries.keys }) { "campaign case/roster incomplete" }
    results.filter { it.caseId in campaign.caseIds }.forEach {
        require(it.runId == campaign.runId && it.worldId == worldId &&
            !Instant.parse(it.startedAt).isBefore(started) && !Instant.parse(it.completedAt).isAfter(completed)) { "case outside campaign world/time" }
    }
    val eventsFile = boundInput(campaignPath.parent, suiteMapper.valueToTree(campaign.events))
    val events = Files.readAllLines(eventsFile).filter(String::isNotBlank).map { suiteMapper.readTree(it) }
    val lifecycle = listOf("world_created", "server_started", "client_joined", "client_disconnected",
        "server_saved_and_stopped", "world_validated", "process_cleanup", "campaign_finished")
    require(events.isNotEmpty() && events.all { it.path("run_id").asText() == campaign.runId &&
        it.path("world_id").asText() == worldId && !it.has("cycle_id") } &&
        events.none { it.path("type").asText() in setOf("failure", "candidate_mutated", "world_replaced", "server_restarted") }) { "unbound/restarted campaign event" }
    val eventTimes = events.map { Instant.parse(it.path("time").asText()) }
    require(eventTimes.zipWithNext().all { (a,b) -> !b.isBefore(a) } && eventTimes.all { !it.isBefore(started) && !it.isAfter(completed) })
    val eventPositions = lifecycle.map { kind -> events.indices.filter { events[it].path("type").asText() == kind }.single() }
    require(eventPositions.zipWithNext().all { (a,b) -> a < b }) { "campaign lifecycle out of order" }
    val permittedCases = campaignCases.map { it.id }.toSet()
    require(events.filter { it.path("type").asText() in setOf("case_started", "case_completed", "case_blocked") }
        .all { it.path("case_id").asText() in permittedCases } &&
        events.none { it.path("type").asText() == "case_blocked" }) { "unexpected/blocked campaign case event" }
    campaignCases.forEach { c ->
        val begins = events.indices.filter { events[it].path("type").asText() == "case_started" && events[it].path("case_id").asText() == c.id }
        val ends = events.indices.filter { events[it].path("type").asText() == "case_completed" && events[it].path("case_id").asText() == c.id }
        require(begins.size == 1 && ends.size == 1 && begins.single() > eventPositions[2] &&
            ends.single() < eventPositions[3] && begins.single() < ends.single() &&
            events[ends.single()].path("state").asText() == "passed" && events[ends.single()].path("source").asText() == "native") { "native case event missing: ${c.id}" }
    }
    require(index.path("lifecycle").fieldNames().asSequence().toSet() == setOf("smoke", "overworld-tele-soak", "tele-soak"))
    val lifecyclePaths = mutableMapOf<String, Path>()
    for (tier in listOf("smoke", "overworld-tele-soak", "tele-soak")) {
        val path = boundInput(base, index.path("lifecycle").path(tier))
        lifecyclePaths[tier] = path
        val doc = suiteMapper.readTree(path.toFile()); val run = doc.path("run_id").asText()
        require(run.isNotBlank() && run != campaign.runId && doc.path("status").asText() == "passed" &&
            !doc.path("profile").asText().contains("test-support")) { "instrumented/failed clean lifecycle $tier" }
        when (tier) {
            "smoke" -> validateSmokeEvidence(path, run, pair, authoringTime)
            "overworld-tele-soak" -> validateOverworldTeleSoakReceipt(path, run, pair, authoringTime)
            else -> validateTeleSoakReceipt(path, run, pair, authoringTime)
        }
    }
    val visualPath = boundInput(base, index.path("visual_review"))
    val visual = validateVisualReview(visualPath, index.path("visual_release_run_id").asText(), pair, authoringTime, setOf(world.toString()))
    val visualDoc = suiteMapper.readTree(visualPath.toFile())
    val reviewer = visualDoc.path("reviewer").path("name").asText()
    require(reviewer.isNotBlank() && reviewer != manifest.path("authoring").path("reviewer").asText()) { "visual review is not independent" }
    val session = boundInput(visualPath.parent, visualDoc.path("capture_session"))
    validateGuiChecklist(v1Shape, boundInput(base, index.path("gui_review")), catalogue, visual.sessionId, reviewer, session)
    val capture = suiteMapper.readTree(session.toFile())
    val visualLogs = mutableSetOf(boundInput(session.parent, capture.path("input_log")).toRealPath())
    for (name in listOf("server_log", "client_log")) {
        val authored = listOf(visualDoc, capture).singleOrNull { it.has(name) }
            ?: error("visual capture requires exactly one explicit bound $name")
        visualLogs.add(boundInput(session.parent, authored.path(name)).toRealPath())
    }
    val repositoryReceipts = index.path("repository_receipts")
    val repos = manifest.path("repositories")
    require(repositoryReceipts.fieldNames().asSequence().toSet() == repos.map { it.path("id").asText() }.toSet()) { "repository receipts incomplete" }
    val repositoryLogs = repos.flatMap { repo ->
        validateV2RepositoryReceipt(base, repositoryReceipts.path(repo.path("id").asText()), repo, candidateHashes(v1Shape))
    }.toSet()
    val logFile = boundInput(base, index.path("log_review"))
    val log = suiteMapper.readTree(logFile.toFile())
    requireV2ReceiptSchema("log_review", log)
    require(log.path("status").asText() == "passed" &&
        log.path("catalogue_sha256").asText() == catalogue && log.path("reviewer").asText().isNotBlank() &&
        !Instant.parse(log.path("reviewed_at").asText()).isBefore(completed) && log.path("unresolved_blockers").isArray &&
        log.path("unresolved_blockers").isEmpty && log.path("sources").isArray && !log.path("sources").isEmpty) { "log review incomplete" }
    val packLogs = (listOf(campaignPath) + lifecyclePaths.values).flatMap { receipt ->
        listOf(receipt.parent.resolve("server.log"), receipt.parent.resolve("client.log"))
    }.map { it.toRealPath() }.toSet()
    require(packLogs.size == 8 && repositoryLogs.isNotEmpty() && visualLogs.isNotEmpty()) {
        "campaign/lifecycle/repository/visual raw log corpus incomplete"
    }
    val requiredLogs = packLogs + repositoryLogs + visualLogs
    validateV2LogSignatures(logFile.parent, log, requiredLogs)
    // Final immutable-input rechecks before exclusive marker; earlier v1 evidence never qualifies v2.
    pair.assertUnchanged()
    require(Hashes.sha256(manifestPath) == catalogue && Hashes.sha256(indexPath) == indexHash)
    caseRefs.forEachIndexed { i, ref -> require(boundInput(base, ref).toRealPath() == resultFiles[i].toRealPath()) }
    listOf("campaign", "visual_review", "gui_review", "log_review").forEach { boundInput(base, index.path(it)) }
    index.path("lifecycle").forEach { boundInput(base, it) }
    index.path("repository_receipts").forEach { boundInput(base, it) }
    listOf("native_protocol", "native_requests", "events", "hardware", "fixture_overrides", "world_marker").forEach {
        boundInput(campaignPath.parent, campaignRaw.path(it))
    }
    log.path("sources").forEach { boundInput(logFile.parent, it) }
    log.path("signature_indexes").forEach { boundInput(logFile.parent, it) }
    val output = base.resolve("playtest-qualified-v2.json")
    require(!Files.exists(output)) { "v2 qualification already exists" }
    val markerDoc = mapOf("schema" to "bc.playtest_qualification.v2", "status" to "qualified",
        "catalogue_sha256" to catalogue, "candidate" to pair.document(), "evidence_index_sha256" to indexHash,
        "created_at" to Instant.now().toString())
    val pending = base.resolve(".playtest-qualified-v2-${UUID.randomUUID()}.pending")
    try {
        Files.writeString(pending, suiteMapper.writerWithDefaultPrettyPrinter().writeValueAsString(markerDoc) + "\n",
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        Files.move(pending, output, StandardCopyOption.ATOMIC_MOVE)
    } finally {
        Files.deleteIfExists(pending)
    }
    return output
}

internal fun validateV2LogSignatures(base: Path, review: JsonNode, requiredLogs: Set<Path> = emptySet()) {
    val sources = review.path("sources")
    val indexes = review.path("signature_indexes")
    val signed = review.path("signatures")
    require(sources.isArray && !sources.isEmpty && sources.size() <= 256 && indexes.isArray && !indexes.isEmpty &&
        signed.isArray) { "raw logs/signature indexes/review absent" }
    val files = sources.map { ref ->
        val file = boundInput(base, ref)
        require(Files.size(file) in 1..32_000_000) { "raw log exceeds bounded review budget" }
        file.toRealPath()
    }
    require(files.size == files.distinct().size && files.toSet().containsAll(requiredLogs)) { "duplicate/missing required raw log" }
    val claimedByIndex = mutableListOf<Path>()
    val indexCounts = mutableMapOf<String, Long>()
    indexes.forEach { reference ->
        val file = boundInput(base, reference)
        val doc = suiteMapper.readTree(file.toFile())
        require(doc.path("schema").asText() == "bc.log_signatures.v1" &&
            doc.path("complete").isBoolean && doc.path("complete").asBoolean() &&
            doc.path("unindexed_events").isIntegralNumber && doc.path("unindexed_events").asLong() == 0L &&
            doc.path("raw_logs").isObject && !doc.path("raw_logs").isEmpty && doc.path("signatures").isArray) {
            "incomplete/unbounded signature index"
        }
        val mapped = doc.path("raw_logs").fields().asSequence().map { (name, path) ->
            val supplied = Path.of(path.asText())
            name to (if (supplied.isAbsolute) supplied else file.parent.resolve(supplied)).toRealPath()
        }.toList()
        require(mapped.map { it.first }.distinct().size == mapped.size && mapped.all { it.second in files }) { "index references unbound raw logs" }
        claimedByIndex += mapped.map { it.second }
        val recomputed = computeLogSignatures(mapped.toMap())
        require(recomputed.unindexedEvents == 0L) { "recomputed log signatures overflow index budget" }
        val expectedRows = recomputed.signatures.associateBy { it.signature }
        val indexed = doc.path("signatures").associateBy { it.path("signature").asText() }
        require(indexed.size == doc.path("signatures").size() && indexed.keys == expectedRows.keys &&
            indexed.all { (signature, row) -> expectedRows[signature]?.let { expected ->
                row.path("count").asLong(-1) == expected.count && row.path("severity").asText() == expected.severity &&
                    row.path("headline").asText() == expected.headline && row.path("exception").asText() == expected.exception
            } == true }) { "signature index omits/duplicates raw events or metadata" }
        val counts = indexed.mapValues { it.value.path("count").asLong() }
        counts.forEach { (signature, count) -> indexCounts[signature] = indexCounts.getOrDefault(signature, 0L) + count }
    }
    require(claimedByIndex.size == files.size && claimedByIndex.toSet() == files.toSet()) { "raw log partition incomplete/overlapping" }
    val reviewed = signed.associate { it.path("signature").asText() to it.path("count").asLong(-1) }
    require(reviewed.size == signed.size() && reviewed == indexCounts && signed.all { row ->
        row.path("signature").asText().matches(shaPattern) && row.path("count").asLong(0) > 0 &&
            row.path("owner").asText().isNotBlank() && row.path("impact").asText().isNotBlank() &&
            row.path("disposition").asText() in setOf("resolved", "no-impact-reviewed")
    }) { "review omits/duplicates/harmfully defers log signature" }
}

internal fun validateDriverAcquisitionBoundary(spec: SuiteCase, fixtureTargets: Set<String>,
    proofCases: Map<String, String>, cases: List<SuiteCase>, acquisitions: JsonNode) {
    require(fixtureTargets.all { it in spec.targets } && proofCases.keys.containsAll(fixtureTargets)) {
        "driver fixture/acquisition targets not declared: ${spec.id}"
    }
    proofCases.forEach { (target, proof) ->
        val case = cases.singleOrNull { it.id == proof }
        val acquisition = acquisitions.firstOrNull { it.path("id").asText() == target }
        require(proof != spec.id && proof in spec.dependencies && case != null && target in case.targets &&
            acquisition != null && acquisition.path("coverage").any { it.asText() == proof }) {
            "independent acquisition prerequisite missing: ${spec.id}/$target/$proof"
        }
    }
}

internal fun requireV2ReceiptSchema(type: String, doc: JsonNode) {
    require(type in setOf("evidence_index", "case_result", "campaign", "repository", "log_review") &&
        doc.path("schema").asText() == "bc.playtest_${type}.v2") { "v1/unknown $type receipt prohibited" }
}

internal data class PlannedNativeAction(val caseId: String, val driver: String, val parameters: JsonNode,
    val maxTimeoutTicks: Int)

/** Replays the live state machine against one exact authored action roster; no unplanned action is evidence. */
internal fun verifyV2NativeProtocol(requests: List<JsonNode>, observations: List<JsonNode>,
    expected: List<PlannedNativeAction>) {
    require(requests.size == expected.size && expected.isNotEmpty()) { "missing or extra native request" }
    val state = NativeExchangeValidator()
    var offset = 0
    requests.zip(expected).forEach { (row, step) ->
        val request = suiteMapper.treeToValue(row, NativeRequest::class.java)
        require(request.caseId == step.caseId && request.driver == step.driver &&
            request.parameters == step.parameters && request.timeoutTicks in 1..step.maxTimeoutTicks) {
            "unplanned/swapped native action: ${step.caseId}/${step.driver}"
        }
        state.begin(request)
        var complete = false
        while (!complete) {
            require(offset < observations.size) { "native request lacks terminal: ${step.caseId}/${step.driver}" }
            val observation = suiteMapper.treeToValue(observations[offset++], NativeObservation::class.java)
            complete = state.accept(observation)
            require(observation.kind != "failed") { "failed native action cannot qualify: ${step.caseId}/${step.driver}" }
        }
    }
    require(offset == observations.size) { "extra/replayed native observation" }
}

internal fun requireCaseTerminalOrder(raw: List<JsonNode>, expectedSequences: List<Long>) {
    require(expectedSequences.isNotEmpty() && expectedSequences.distinct().size == expectedSequences.size &&
        expectedSequences == expectedSequences.sorted() &&
        raw.map { it.path("observation").path("request_sequence").asLong() } == expectedSequences) {
        "per-case terminal rows are reordered, reused or incomplete"
    }
}

private fun verifyIndependentNativeCases(cases: List<SuiteCase>, bindings: Map<String, JsonNode>,
    results: List<CaseResult>, files: List<Path>, requests: Map<Long, JsonNode>, observations: Map<Long, List<JsonNode>>) {
    val cleanupsByCase = mutableMapOf<String, Pair<Long, Boolean>>()
    cases.forEach { spec ->
        val index = results.indexOfFirst { it.caseId == spec.id }
        require(index >= 0) { "missing native case ${spec.id}" }
        val receipt = results[index]
        val driver = requireNotNull(DriverRegistry.resolve(spec.id))
        val parameters = bindings.getValue(spec.id).path("parameters")
        val planned = driver.plan(parameters)
        val rawFiles = receipt.assertions.flatMap { it.evidence }
            .filter { it.kind == "native-interaction" }.map { boundInput(files[index].parent, suiteMapper.valueToTree(it.file)) }.distinct()
        require(rawFiles.size == 1 && rawFiles[0].fileName.toString() == "case-${spec.id}-native.jsonl" &&
            Files.size(rawFiles[0]) in 1..100_000_000) { "case raw native observation evidence missing: ${spec.id}" }
        val raw = Files.readAllLines(rawFiles[0]).also { require(it.size == planned.size && it.all(String::isNotBlank)) }
            .map { line -> require(line.length <= PROTOCOL_LINE_LIMIT); suiteMapper.readTree(line) }
        val expectedSequences = requests.filter { (_, request) ->
            request.path("case_id").asText() == spec.id && request.path("driver").asText() != "fixture.cleanup"
        }.keys.sorted()
        require(expectedSequences.size == planned.size) { "case native request count differs from plan: ${spec.id}" }
        requireCaseTerminalOrder(raw, expectedSequences)
        val driverObservations = planned.zip(raw).map { (step, row) ->
            val observation = row.path("observation")
            require(row.path("step").asText() == step.name && observation.path("schema").asText() == "bc.playtest_observation.v2" &&
                observation.path("run_id").asText() == receipt.runId && observation.path("case_id").asText() == spec.id &&
                observation.path("driver").asText() == step.action.id && observation.path("kind").asText() == "completed" &&
                observation.path("source").asText() == "server") { "case native step mismatch: ${spec.id}/${step.name}" }
            val seq = observation.path("request_sequence").asLong()
            val request = requests[seq] ?: error("unbound native request: $seq")
            require(request.path("case_id").asText() == spec.id && request.path("driver").asText() == step.action.id &&
                request.path("parameters") == step.action.parameters &&
                observations[seq]?.last() == observation) { "native request/terminal not exact: ${spec.id}/${step.name}" }
            DriverObservation(step.name, observation.path("driver").asText(), observation.path("source").asText(),
                observation.path("kind").asText(), observation.path("payload"))
        }
        val outcome = evaluateCase(driver, parameters, driverObservations)
        require(outcome is DriverOutcome.Verdict && outcome.value.state == "passed") { "independent native oracle did not pass: ${spec.id}" }
        val cleanups = requests.filter { (_, request) -> request.path("case_id").asText() == spec.id &&
            request.path("driver").asText() == "fixture.cleanup" }
        require(cleanups.size == 1) { "missing/duplicate fixture cleanup: ${spec.id}" }
        val seq = cleanups.keys.single()
        val terminal = observations[seq]?.lastOrNull()?.path("payload")
        require(terminal != null && terminal.path("cleanup_complete").isBoolean &&
            terminal.path("cleanup_complete").asBoolean() && terminal.path("baseline_restored").isBoolean) {
            "fixture cleanup flag missing/failed: ${spec.id}"
        }
        cleanupsByCase[spec.id] = seq to terminal.path("baseline_restored").asBoolean()
    }
    // A final case may leave a changed disposable world; the next independent case may not
    // execute until the previous fixture proves complete baseline restoration.
    val firstRequestByCase = cases.associate { spec -> spec.id to requests.filter { (_, request) ->
        request.path("case_id").asText() == spec.id && request.path("driver").asText() != "fixture.cleanup"
    }.keys.minOrNull().let { requireNotNull(it) { "case has no native actions: ${spec.id}" } } }
    val ordered = firstRequestByCase.entries.sortedBy { it.value }
    ordered.zipWithNext().forEach { (previous, next) ->
        val (cleanupSeq, restored) = cleanupsByCase.getValue(previous.key)
        require(restored && cleanupSeq > previous.value && cleanupSeq < next.value) {
            "next native case executed before previous baseline restored: ${previous.key} -> ${next.key}"
        }
    }
}

private fun shaNode(node: JsonNode): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(suiteMapper.writeValueAsBytes(node)).joinToString("") { "%02x".format(it) }

internal fun v2LegacyShape(m: JsonNode, cases: List<SuiteCase>): SuiteManifest {
    val profile = suiteMapper.treeToValue(m.path("profile"), SuiteProfile::class.java)
    val review = suiteMapper.treeToValue(m.path("authoring"), AuthoringReview::class.java)
    val client = suiteMapper.treeToValue(m.path("candidate").path("client"), BoundFile::class.java)
    val server = suiteMapper.treeToValue(m.path("candidate").path("server"), BoundFile::class.java)
    val empty = BoundFile("", "")
    val rosters = m.path("rosters").map { suiteMapper.treeToValue(it, RosterSpec::class.java) }
    return SuiteManifest("bc.playtest_manifest.v1", m.path("revision").asText(), review,
        CandidateBinding(client, server, empty, empty), profile, empty,
        emptyList(), rosters, emptyList(), cases)
}

internal fun validateV2RepositoryReceipt(base: Path, reference: JsonNode, expected: JsonNode,
    candidate: Map<String, String>): Set<Path> {
    val path = boundInput(base, reference)
    val doc = suiteMapper.readTree(path.toFile())
    val id = expected.path("id").asText()
    requireV2ReceiptSchema("repository", doc)
    require(doc.path("repository").asText() == id && doc.path("status").asText() == "passed") { "failed/unbound repository receipt: $id" }
    for (field in listOf("source_sha256", "tests_sha256", "providers_sha256", "toolchain_sha256", "configuration_sha256", "artifact_sha256"))
        require(doc.path(field).asText() == expected.path(field).asText() && doc.path(field).asText().matches(shaPattern)) { "unbound repository fingerprint: $id/$field" }
    require(expected.path("case_ids").map { it.asText() }.toSet() == doc.path("case_ids").map { it.asText() }.toSet() &&
        doc.path("case_ids").size() == expected.path("case_ids").size()) { "repository promise cases missing: $id" }
    val raw = doc.path("evidence")
    require(raw.isArray && !raw.isEmpty) { "repository lacks raw test membership: $id" }
    raw.forEach { boundInput(path.parent, it) }
    val independent = expected.path("test_reports")
    val reported = doc.path("reports")
    require(independent.isArray && !independent.isEmpty && reported.isArray &&
        reported.size() == independent.size()) { "actual discovered repository tests absent: $id" }
    val reference = boundInput(base, expected.path("reference"))
    val referenceSha = Hashes.sha256(reference)
    val referenceDoc = suiteMapper.readTree(reference.toFile())
    require(referenceDoc.path("schema").asText() == "bc.playtest_reference.v1" &&
        referenceDoc.path("candidate").path("client_sha256").asText() == candidate.getValue("client_sha256") &&
        referenceDoc.path("candidate").path("server_sha256").asText() == candidate.getValue("server_sha256")) { "repository source reference differs from selected candidate: $id" }
    val source = if (id == "better-content-modpack") referenceDoc.path("pack_source")
        else referenceDoc.path("sources").firstOrNull { it.path("repository").asText() == id }
            ?: error("repository absent from independent source reference: $id")
    require(source.path("repository").asText() == id &&
        source.path("production_fingerprint").asText().matches(shaPattern) &&
        source.path("production_fingerprint").asText() == expected.path("source_sha256").asText() &&
        source.path("artifact_sha256").asText() == expected.path("artifact_sha256").asText()) {
        "reference source/artifact fingerprint unverified or stale: $id"
    }
    val expectedReports = independent.associateBy { it.path("task").asText() to it.path("kind").asText() }
    require(expectedReports.size == independent.size() && independent.all { it.path("test_ids").isArray && !it.path("test_ids").isEmpty })
    val receivedReports = reported.associateBy { it.path("task").asText() to it.path("kind").asText() }
    require(receivedReports.size == reported.size() && receivedReports.keys == expectedReports.keys) { "missing/duplicate/unexpected repository report: $id" }
    val requiredLogs = mutableSetOf<Path>()
    expectedReports.forEach { (key, authored) ->
        val receipt = receivedReports.getValue(key)
        val ids = authored.path("test_ids").map { it.asText() }.toSet()
        require(ids.size == authored.path("test_ids").size() && ids.isNotEmpty() &&
            ids == receipt.path("test_ids").map { it.asText() }.toSet() &&
            receipt.path("test_ids").size() == ids.size &&
            authored.path("run_id").asText().isNotBlank() && authored.path("run_id") == receipt.path("run_id") &&
            authored.path("started_after") == receipt.path("started_after") &&
            authored.path("report_timezone").asText("UTC") == receipt.path("report_timezone").asText("UTC")) { "unreviewed report membership/run/timezone: $id/$key" }
        val report = boundInput(path.parent, receipt.path("report"))
        val sidecar = boundInput(path.parent, receipt.path("sidecar"))
        val executionLog = boundInput(path.parent, receipt.path("execution_log")).toRealPath()
        require(Files.size(executionLog) in 1..32_000_000) { "missing/unbounded repository task stdout/stderr: $id/$key" }
        requiredLogs.add(executionLog)
        require(report.toRealPath().startsWith(path.parent.toRealPath()) && sidecar.toRealPath().startsWith(path.parent.toRealPath())) { "unowned raw report: $id" }
        val expectation = RepositoryReportExpectation(referenceSha, id, key.first, authored.path("run_id").asText(),
            expected.path("source_sha256").asText(), expected.path("tests_sha256").asText(),
            expected.path("providers_sha256").asText(), expected.path("toolchain_sha256").asText(),
            expected.path("configuration_sha256").asText(), expected.path("artifact_sha256").asText(),
            ids, Instant.parse(authored.path("started_after").asText()), receipt.path("report").path("sha256").asText(),
            ZoneId.of(authored.path("report_timezone").asText("UTC")))
        val observed = when (key.second) {
            "junit" -> RepositoryReportImporter.importJUnit(report, sidecar, expectation)
            "forge-gametest" -> {
                val sidecarDoc = suiteMapper.readTree(sidecar.toFile())
                requiredLogs.add(boundInput(sidecar.parent, sidecarDoc.path("native_log")).toRealPath())
                RepositoryReportImporter.importNative(report, sidecar, expectation)
            }
            else -> error("unsupported repository report kind: ${key.second}")
        }
        require(observed.testIds == ids && observed.rawSha256 == Hashes.sha256(report) && observed.runId == authored.path("run_id").asText())
    }
    return requiredLogs
}
