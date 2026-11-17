package com.bettercontent.tests.suite

import com.bettercontent.tests.CandidatePair
import com.bettercontent.tests.Hashes
import com.bettercontent.tests.release.validateSmokeEvidence
import com.bettercontent.tests.release.validateTeleSoakReceipt
import com.bettercontent.tests.release.validateOverworldTeleSoakReceipt
import com.bettercontent.tests.release.validateVisualReview
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import java.util.UUID

internal data class AssertionEvidence(val kind: String, val file: BoundFile)
internal data class AssertionResult(val id: String, val state: String, val evidence: List<AssertionEvidence>)
internal data class CaseResult(val schema: String, val caseId: String, val caseSha256: String,
    val catalogueSha256: String, val profileSha256: String, val candidate: Map<String, String>,
    val state: String, val diagnostic: Boolean, val runId: String, val worldId: String,
    val startedAt: String, val completedAt: String, val assertions: List<AssertionResult>,
    val cleanupComplete: Boolean, val provisionedTargets: List<String>)
internal data class EvidenceIndex(val schema: String, val catalogueSha256: String,
    val caseResults: List<BoundFile>, val campaign: BoundFile, val lifecycle: Map<String, BoundFile>,
    val visualReview: BoundFile, val guiReview: BoundFile, val visualReleaseRunId: String,
    val repositoryReceipts: Map<String, BoundFile>, val logReview: BoundFile)

internal fun caseFingerprint(spec: SuiteCase): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(suiteMapper.writeValueAsBytes(spec)).joinToString("") { "%02x".format(it) }
internal fun candidateHashes(m: SuiteManifest): Map<String, String> = mapOf(
    "client_sha256" to m.candidate.client.sha256, "server_sha256" to m.candidate.server.sha256)

internal fun validateCaseResult(m: SuiteManifest, c: SuiteCase, r: CaseResult, catalogue: String,
    base: Path, now: Instant = Instant.now(), maximumDuration: Duration = Duration.ofSeconds(c.timeoutSeconds)) {
    require(r.schema == "bc.playtest_case_result.v1" && r.caseId == c.id && r.caseSha256 == caseFingerprint(c)) { "wrong case contract: ${c.id}" }
    require(r.state in caseStates && r.state == "passed" && !r.diagnostic) { "required case ${c.id} is ${r.state}/diagnostic" }
    require(r.runId.isNotBlank() && r.cleanupComplete) { "unowned or unclean case ${c.id}" }
    val local = c.layer in setOf("unit", "native")
    if (!local) require(r.catalogueSha256 == catalogue && r.profileSha256 == profileFingerprint(m.profile) && r.candidate == candidateHashes(m)) { "stale candidate/catalogue/profile: ${c.id}" }
    val started = Instant.parse(r.startedAt); val ended = Instant.parse(r.completedAt)
    require(!ended.isBefore(started) && !ended.isAfter(now) && Duration.between(started, ended) <= maximumDuration) {
        "invalid or over-budget timing: ${c.id}"
    }
    if (!local) require(!started.isBefore(Instant.parse(m.authoring.reviewedAt))) { "case predates authoring checkpoint: ${c.id}" }
    require(r.assertions.map { it.id }.size == r.assertions.map { it.id }.toSet().size &&
        r.assertions.map { it.id }.toSet() == c.assertions.toSet()) { "missing/duplicate/unexpected assertion: ${c.id}" }
    r.assertions.forEach { assertion ->
        require(assertion.state == "passed" && assertion.evidence.isNotEmpty()) { "assertion not established: ${c.id}/${assertion.id}" }
        assertion.evidence.forEach { readBoundFile(base, it.file) }
    }
    require(r.assertions.flatMap { it.evidence }.map { it.kind }.toSet().containsAll(c.evidenceKinds)) { "required evidence kinds missing: ${c.id}" }
    require(r.provisionedTargets.all { it in c.targets }) { "unclassified fixture provision: ${c.id}" }
    val acquisitionProofs = m.acquisitions.filter { c.id in it.coverage }.map { it.id }
    require(r.provisionedTargets.none { it in acquisitionProofs }) { "provisioned target cannot prove acquisition: ${c.id}" }
}

/** Native and source receipts must bind to the same selected production bytes, not merely a repo name. */
internal fun validateCandidateInventory(m: SuiteManifest, root: Path): CandidatePair {
    val client = readBoundFile(root, m.candidate.client); val server = readBoundFile(root, m.candidate.server)
    val provenance = suiteMapper.readTree(readBoundFile(root, m.candidate.provenance).toFile())
    val inventory = suiteMapper.readTree(readBoundFile(root, m.candidate.inventory).toFile())
    require(provenance.path("schema").asText() == "bc.fresh_dist_provenance.v1" && inventory.path("schema").asText() == "bc.archive_members.v1") { "unsupported candidate provenance" }
    listOf(provenance, inventory).forEach { doc -> candidateHashes(m).forEach { (key, value) -> require(doc.path("candidates").path(key).asText() == value) { "provenance candidate mismatch" } } }
    val mods = provenance.path("mods").toList()
    require(mods.isNotEmpty() && mods.map { it.path("repository").asText() }.distinct().size == mods.size) { "missing/duplicate source inventory" }
    val members = inventory.path("members").toList()
    val included = mods.filter { mod -> members.any { it.path("path").asText().substringAfterLast('/') == mod.path("artifact").asText() } }
    require(included.size == mods.size) { "provenance contains source artifact absent from archives" }
    require(m.repositories.map { it.id }.toSet() == included.map { it.path("repository").asText() }.toSet() + "better-content-modpack") { "manifest repository scope differs from packaged provenance" }
    included.forEach { mod ->
        val repo = m.repositories.single { it.id == mod.path("repository").asText() }
        require(repo.artifact == mod.path("artifact").asText() && repo.artifactSha256 == mod.path("sha256").asText()) { "wrong source-owned artifact: ${repo.id}" }
        val matching = members.filter { it.path("path").asText().substringAfterLast('/') == repo.artifact }
        require(matching.isNotEmpty() && matching.all { it.path("sha256").asText() == repo.artifactSha256 }) { "archive source artifact differs: ${repo.id}" }
        val source = provenance.path("source_updates").single { it.path("repository").asText() == repo.id }
        require(source.path("production_fingerprint").asText() == repo.sourceSha256) { "source qualification differs from packaged source: ${repo.id}" }
    }
    return CandidatePair(client.parent.parent, client, server, m.candidate.client.sha256, m.candidate.server.sha256)
}

/** Qualification is a read-only evidence check followed by one exclusive-create marker. Never starts a JVM session. */
internal fun qualifySuite(root: Path, manifestPath: Path, indexPath: Path): Path {
    val catalogue = manifestFingerprint(manifestPath)
    val manifest = loadSuiteManifest(manifestPath)
    val problems = manifestProblems(manifest, root)
    require(problems.isEmpty()) { "Authoring checkpoint incomplete:\n${problems.joinToString("\n")}" }
    val indexHash = Hashes.sha256(indexPath)
    val index = suiteMapper.readValue(indexPath.toFile(), EvidenceIndex::class.java)
    require(index.schema == "bc.playtest_evidence_index.v1" && index.catalogueSha256 == catalogue) { "wrong evidence index/catalogue" }
    val pair = validateCandidateInventory(manifest, root)
    readBoundFile(root, manifest.testSupport)
    val base = indexPath.toAbsolutePath().parent
    require(index.caseResults.map { resolveSuitePath(base, it.path) }.distinct().size == index.caseResults.size) { "duplicate case receipt file" }
    val resultFiles = index.caseResults.map { readBoundFile(base, it) }
    val results = resultFiles.map { suiteMapper.readValue(it.toFile(), CaseResult::class.java) }
    require(results.map { it.caseId }.distinct().size == results.size && results.map { it.caseId }.toSet() == manifest.cases.map { it.id }.toSet()) { "partial/duplicate/unexpected case coverage" }
    results.zip(resultFiles).forEach { (result, file) -> validateCaseResult(manifest, manifest.cases.single { it.id == result.caseId }, result, catalogue, file.parent) }
    val resultMap = results.associateBy { it.caseId }
    manifest.cases.forEach { c -> c.dependencies.forEach { dep -> require(resultMap.getValue(dep).state == "passed") { "dependency not passed: $dep" } } }

    val campaignPath = readBoundFile(base, index.campaign)
    val campaignReceipt = suiteMapper.readValue(campaignPath.toFile(), CampaignReceipt::class.java)
    val campaign = suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(campaignReceipt)
    val campaignStarted = Instant.parse(campaignReceipt.startedAt)
    val campaignCompleted = Instant.parse(campaignReceipt.completedAt)
    require(!campaignStarted.isBefore(Instant.parse(manifest.authoring.reviewedAt)) && !campaignCompleted.isBefore(campaignStarted) &&
        !campaignCompleted.isAfter(Instant.now())) { "invalid campaign time interval" }
    val hardware = suiteMapper.readTree(readBoundFile(campaignPath.parent, campaignReceipt.hardware).toFile())
    require(hardware.path("schema").asText() == "bc.playtest_hardware.v1" &&
        hardware.path("profile").asText() == manifest.profile.hardwareProfile &&
        hardware.path("server_heap_mib").isIntegralNumber && hardware.path("server_heap_mib").asInt() == manifest.profile.serverHeapMib &&
        hardware.path("view_distance").asInt() == 10 && hardware.path("simulation_distance").asInt() == 10 &&
        listOf("cpu", "jvm", "contention_notes").all { hardware.path(it).asText().isNotBlank() }) { "actual reference hardware/profile observation missing" }
    val overrides = suiteMapper.readTree(readBoundFile(campaignPath.parent, campaignReceipt.fixtureOverrides).toFile())
    require(overrides.path("schema").asText() == "bc.playtest_fixture_overrides.v1" && overrides.path("overrides").isArray &&
        overrides.path("overrides").all { it.path("setting").asText().isNotBlank() && it.path("purpose").asText().isNotBlank() &&
            it.has("value") && it.path("changes_asserted_behavior").isBoolean && !it.path("changes_asserted_behavior").asBoolean() }) { "undeclared or outcome-altering fixture overrides" }
    require(campaign.path("schema").asText() == "bc.playtest_campaign.v1" && campaign.path("status").asText() == "passed" &&
        campaign.path("diagnostic").isBoolean && !campaign.path("diagnostic").asBoolean() &&
        campaign.path("catalogue_sha256").asText() == catalogue && campaign.path("profile_sha256").asText() == profileFingerprint(manifest.profile)) { "campaign is incomplete, diagnostic or stale" }
    candidateHashes(manifest).forEach { (key, value) -> require(campaign.path("candidate").path(key).asText() == value) { "campaign candidate mismatch" } }
    require(campaign.path("test_support_sha256").asText() == manifest.testSupport.sha256 && campaign.path("instrumented").asBoolean()) { "instrumented campaign support unbound" }
    require(campaign.path("seed").isIntegralNumber && campaign.path("seed").asLong() == 424242L) { "wrong campaign seed" }
    val worldId = campaign.path("world_id").asText(); UUID.fromString(worldId)
    val campaignRun = campaign.path("run_id").asText(); require(campaignRun.isNotBlank())
    val world = Path.of(campaign.path("world_directory").asText()).toRealPath()
    require(world.startsWith(campaignPath.parent.toRealPath())) { "campaign world is not owned by receipt fixture" }
    val worldMarker = readBoundFile(campaignPath.parent, suiteMapper.treeToValue(campaign.path("world_marker"), BoundFile::class.java))
    require(worldMarker.toRealPath() == world.resolve(".bc-suite-world-id") && Files.readString(worldMarker).trim() == worldId) { "world identity not established" }
    require(campaign.path("worlds_created").asInt() == 1 && campaign.path("cleanup_complete").asBoolean() &&
        campaign.path("saved_and_validated").asBoolean() && campaign.path("surviving_owned_pids").isArray && campaign.path("surviving_owned_pids").isEmpty) { "campaign lifecycle incomplete" }
    val campaignCases = manifest.cases.filter { it.layer == "campaign" }.map { it.id }.toSet()
    require(campaignReceipt.caseIds.distinct().size == campaignReceipt.caseIds.size && campaign.path("case_ids").map { it.asText() }.toSet() == campaignCases) { "campaign cases incomplete" }
    results.filter { it.caseId in campaignCases }.forEach {
        require(it.worldId == worldId && it.runId == campaignRun) { "case used a different campaign/world" }
        require(!Instant.parse(it.startedAt).isBefore(campaignStarted) && !Instant.parse(it.completedAt).isAfter(campaignCompleted)) { "case ran outside campaign interval" }
    }
    val observedRosters = campaign.path("observed_rosters")
    require(observedRosters.fieldNames().asSequence().toSet() == requiredRosters) { "runtime inventory incomplete" }
    manifest.rosters.forEach { r -> require(observedRosters.path(r.kind).map { it.asText() }.toSet() == r.entries.keys) { "runtime roster changed: ${r.kind}" } }
    val eventsPath = readBoundFile(campaignPath.parent, suiteMapper.treeToValue(campaign.path("events"), BoundFile::class.java))
    val events = Files.readAllLines(eventsPath).filter(String::isNotBlank).map { suiteMapper.readTree(it) }
    require(events.isNotEmpty() && events.all { it.path("run_id").asText() == campaignRun && it.path("world_id").asText() == worldId }) { "unbound campaign events" }
    val eventTimes = events.map { Instant.parse(it.path("time").asText()) }
    require(eventTimes.zipWithNext().all { (a,b) -> !b.isBefore(a) } && eventTimes.all { !it.isBefore(campaignStarted) && !it.isAfter(campaignCompleted) }) { "invalid native event chronology" }
    val lifecycle = listOf("world_created", "server_started", "client_joined", "client_disconnected", "server_saved_and_stopped", "world_validated", "process_cleanup", "campaign_finished")
    val order = lifecycle.map { kind -> events.indices.filter { events[it].path("type").asText() == kind }.also { require(it.size == 1) { "missing/repeated campaign lifecycle: $kind" } }.single() }
    require(order.zipWithNext().all { (a,b) -> a < b } && events.none { it.path("type").asText() in setOf("failure", "candidate_mutated", "world_replaced") }) { "invalid campaign event sequence" }
    campaignCases.forEach { id ->
        val starts = events.indices.filter { events[it].path("type").asText() == "case_started" && events[it].path("case_id").asText() == id }
        val ends = events.indices.filter { events[it].path("type").asText() == "case_completed" && events[it].path("case_id").asText() == id }
        require(starts.size == 1 && ends.size == 1 && starts.single() < ends.single() &&
            events[ends.single()].path("state").asText() == "passed" && events[ends.single()].path("source").asText() == "native") { "no unique native case start/completion: $id" }
    }

    require(index.lifecycle.keys == setOf("smoke", "overworld-tele-soak", "tele-soak")) { "all existing runtime tiers required" }
    val reviewTime = Instant.parse(manifest.authoring.reviewedAt)
    index.lifecycle.forEach { (tier, ref) ->
        val path = readBoundFile(base, ref); val doc = suiteMapper.readTree(path.toFile()); val run = doc.path("run_id").asText()
        require(run.isNotBlank() && run != campaignRun && doc.path("status").asText() == "passed")
        when(tier) {
            "smoke" -> validateSmokeEvidence(path, run, pair, reviewTime)
            "overworld-tele-soak" -> validateOverworldTeleSoakReceipt(path, run, pair, reviewTime)
            "tele-soak" -> validateTeleSoakReceipt(path, run, pair, reviewTime)
        }
        require(doc.path("profile").asText().contains("test-support").not()) { "instrumented evidence cannot be clean lifecycle proof" }
    }
    val visualPath = readBoundFile(base, index.visualReview)
    val visual = validateVisualReview(visualPath, index.visualReleaseRunId, pair, reviewTime, setOf(world.toString()))
    val visualDoc = suiteMapper.readTree(visualPath.toFile())
    require(visualDoc.path("reviewer").path("name").asText() != manifest.authoring.reviewer) { "visual reviewer must be independent of suite authoring reviewer" }
    require(results.filter { manifest.cases.single { c -> c.id == it.caseId }.layer == "visual" }.all { it.runId == visual.sessionId && it.worldId == visual.ownershipId }) { "GUI cases must use the independently reviewed session/world" }
    val visualSession = visualDoc.path("capture_session")
    val visualSessionPath = readBoundFile(visualPath.parent, BoundFile(visualSession.path("path").asText(), visualSession.path("sha256").asText()))
    validateGuiChecklist(manifest, readBoundFile(base, index.guiReview), catalogue, visual.sessionId,
        visualDoc.path("reviewer").path("name").asText(), visualSessionPath)

    require(index.repositoryReceipts.keys == manifest.repositories.map { it.id }.toSet()) { "repository promise receipt missing" }
    manifest.repositories.forEach { repo ->
        val path = readBoundFile(base, index.repositoryReceipts.getValue(repo.id))
        val receipt = suiteMapper.readValue(path.toFile(), RepositoryReceipt::class.java)
        val doc = suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(receipt)
        require(doc.path("schema").asText() == "bc.playtest_repository.v1" && doc.path("repository").asText() == repo.id && doc.path("status").asText() == "passed") { "repository is not qualified: ${repo.id}" }
        val fields = mapOf("source_sha256" to repo.sourceSha256, "tests_sha256" to repo.testsSha256,
            "providers_sha256" to repo.providersSha256, "toolchain_sha256" to repo.toolchainSha256, "configuration_sha256" to repo.configurationSha256)
        fields.forEach { (key, expected) -> require(doc.path(key).asText() == expected) { "stale repository qualification: ${repo.id}/$key" } }
        if (repo.id != "better-content-modpack") require(doc.path("artifact_sha256").asText() == repo.artifactSha256)
        require(doc.path("case_ids").map { it.asText() }.toSet() == repo.caseIds.toSet()) { "repository promise coverage differs" }
        val raw = doc.path("evidence").toList(); require(raw.isNotEmpty()) { "repository receipt lacks raw evidence" }
        raw.forEach { readBoundFile(path.parent, suiteMapper.treeToValue(it, BoundFile::class.java)) }
    }
    val logPath = readBoundFile(base, index.logReview)
    val logReceipt = suiteMapper.readValue(logPath.toFile(), LogReviewReceipt::class.java)
    require(logReceipt.reviewer.isNotBlank() && !Instant.parse(logReceipt.reviewedAt).isBefore(campaignCompleted) &&
        !Instant.parse(logReceipt.reviewedAt).isAfter(Instant.now())) { "log impact review identity/time missing" }
    require(logReceipt.signatures.map { it.signature }.distinct().size == logReceipt.signatures.size &&
        logReceipt.signatures.all { shaPattern.matches(it.signature) && it.count > 0 && it.impact.isNotBlank() }) { "unclassified/duplicate log signature" }
    val logs = suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(logReceipt)
    require(logs.path("schema").asText() == "bc.playtest_log_review.v1" && logs.path("catalogue_sha256").asText() == catalogue &&
        logs.path("status").asText() == "passed" && logs.path("unresolved_blockers").isArray && logs.path("unresolved_blockers").isEmpty) { "log impact review incomplete" }
    val signatures = logs.path("signatures").toList()
    require(signatures.all { it.path("owner").asText().isNotBlank() && it.path("disposition").asText().isNotBlank() }) { "untriaged log signature" }
    val logSources = logs.path("sources").toList(); require(logSources.isNotEmpty()) { "missing raw log bindings" }
    logSources.forEach { readBoundFile(logPath.parent, suiteMapper.treeToValue(it, BoundFile::class.java)) }

    // Recheck bindings after all potentially slow evidence validation. Never overwrite earlier qualification.
    pair.assertUnchanged()
    require(manifestFingerprint(manifestPath) == catalogue && Hashes.sha256(indexPath) == indexHash) { "manifest/index changed while qualifying" }
    results.zip(resultFiles).forEach { (result, file) -> result.assertions.flatMap { it.evidence }.distinctBy { it.file }.forEach { readBoundFile(file.parent, it.file) } }
    readBoundFile(campaignPath.parent, campaignReceipt.hardware)
    readBoundFile(campaignPath.parent, campaignReceipt.fixtureOverrides)
    readBoundFile(campaignPath.parent, campaignReceipt.events)
    readBoundFile(campaignPath.parent, campaignReceipt.worldMarker)
    (index.caseResults + index.lifecycle.values + index.repositoryReceipts.values + listOf(index.campaign, index.visualReview, index.guiReview, index.logReview)).forEach { readBoundFile(base, it) }
    val output = base.resolve("playtest-qualified.json")
    require(!Files.exists(output)) { "qualification marker already exists; use a fresh evidence directory" }
    val document = mapOf("schema" to "bc.playtest_qualification.v1", "status" to "qualified", "created_at" to Instant.now().toString(),
        "catalogue_sha256" to catalogue, "candidate" to candidateHashes(manifest), "profile_sha256" to profileFingerprint(manifest.profile),
        "evidence_index" to BoundFile(indexPath.toAbsolutePath().toString(), indexHash),
        "limitations" to listOf("server-side fake players, not 32 real-client logins", "one seed and finite worldgen samples", "native timing windows and boundary fixtures, not full-duration observation"))
    val pending = base.resolve(".playtest-qualified-${UUID.randomUUID()}.pending")
    try {
        Files.writeString(pending, suiteMapper.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        // Same-directory rename; no REPLACE_EXISTING and no partially written ready marker.
        Files.move(pending, output)
    } finally {
        Files.deleteIfExists(pending)
    }
    return output
}
