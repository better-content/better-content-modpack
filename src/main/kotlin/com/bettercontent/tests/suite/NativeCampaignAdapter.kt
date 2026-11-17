package com.bettercontent.tests.suite

import com.bettercontent.tests.*
import com.bettercontent.tests.suite.drivers.*
import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Sole native adapter. Unsupported coverage is rejected before creating a world. */
internal class NativeCampaignAdapter : CampaignAdapter {
    override val supportedCases: Set<String> get() = DriverRegistry.supportedCases
    override fun executeV3(request: com.bettercontent.tests.suite.checkpoints.V3CampaignRequest) =
        com.bettercontent.tests.suite.checkpoints.executeCheckpointCampaign(request)
    override fun execute(request: CampaignRequest) = error("v1 campaign execution is historical; explicitly convert and review a v2 draft")
    override fun executeV2(request: V2CampaignRequest) {
        val m = request.manifest
        require(request.cases.all { it.id in supportedCases }) { "native case driver unavailable" }
        val resources = requireSuiteResources(true)
        val runId = "campaign-${UUID.randomUUID()}"
        val worldId = UUID.randomUUID().toString()
        val catalogue = Hashes.sha256(request.manifestPath)
        val evidence = SessionEvidence(request.root, runId, "campaign", request.pair, diagnostic = request.diagnostic)
        val events = evidence.directory.resolve("campaign-events.jsonl")
        val mailbox = NativeMailbox(evidence.directory.resolve("native"), runId)
        val profile = suiteMapper.treeToValue(m.path("profile"), SuiteProfile::class.java)
        val supportHash = m.path("test_support").path("sha256").asText()
        val session = PackSession(evidence, SessionRequest(SessionTier.SMOKE), instrumentation =
            SuiteInstrumentation(request.support, supportHash, mailbox.javaArguments(), true))
        val started = Instant.now()
        val results = linkedMapOf<String, String>()
        val caseReceipts = mutableListOf<BoundFile>()
        var failure: Throwable? = null
        var saved = false
        var cleanup = false
        var world: Path? = null
        var observedRosters: JsonNode = suiteMapper.createObjectNode()
        fun event(kind: String, fields: Map<String, Any?> = emptyMap()) {
            val data = linkedMapOf<String, Any?>("type" to kind, "time" to Instant.now().toString(), "run_id" to runId, "world_id" to worldId)
            data.putAll(fields)
            Files.writeString(events, suiteMapper.writeValueAsString(data) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        }
        fun bound(file: Path) = BoundFile(file.toAbsolutePath().toString(), Hashes.sha256(file))
        val hardware = evidence.directory.resolve("hardware.json")
        exclusiveJson(hardware, resources + mapOf("schema" to "bc.playtest_hardware.v2", "profile" to profile.hardwareProfile,
            "view_distance" to 10, "simulation_distance" to 10))
        val overrides = evidence.directory.resolve("fixture-overrides.json")
        exclusiveJson(overrides, mapOf("schema" to "bc.playtest_fixture_overrides.v2", "overrides" to listOf(
            mapOf("setting" to "online-mode", "value" to false, "purpose" to "loopback disposable identity, not authenticated login certification", "changes_asserted_behavior" to false),
            mapOf("setting" to "test-support", "value" to supportHash, "purpose" to "disclosed native observations and bounded fixture actions", "changes_asserted_behavior" to false))))
        try {
            session.prepare()
            world = session.suiteWorldDirectory()
            Files.createDirectories(world)
            Files.writeString(world.resolve(".bc-suite-world-id"), worldId + "\n", StandardOpenOption.CREATE_NEW)
            event("world_created")
            session.startServer(); event("server_started"); session.awaitServerReady()
            session.connectClient(); session.awaitClientJoined(); session.heartbeat(); event("client_joined")
            // Re-observe the frozen roster on the actual campaign world, never reuse discovery as live proof.
            val observed = mailbox.execute("campaign.inventory", "inventory.discover", jsonObject(), Duration.ofMinutes(10),
                ensureAlive = session::ensureConnected).payload
            require(observed.path("complete").isBoolean && observed.path("complete").asBoolean() && observed.path("gaps").isArray && observed.path("gaps").isEmpty) {
                "campaign inventory remains incomplete"
            }
            val actualRosters = observed.path("rosters")
            observedRosters = actualRosters.deepCopy()
            require(actualRosters.isObject && actualRosters.fieldNames().asSequence().toSet() == requiredRosters)
            m.path("rosters").forEach { r -> require(actualRosters.path(r.path("kind").asText()).map { it.asText() }.toSet() ==
                r.path("entries").fieldNames().asSequence().toSet()) { "candidate effective roster drift" } }
            var baselineRestored = true
            for (spec in request.cases) {
                val caseStart = Instant.now()
                if (spec.dependencies.any { results[it] != "passed" }) {
                    results[spec.id] = "blocked"
                    event("case_blocked", mapOf("case_id" to spec.id, "reason" to "dependency not passed"))
                    val blocked = CaseResult("bc.playtest_case_result.v2", spec.id, caseFingerprint(spec), catalogue,
                        profileFingerprint(profile), candidateHashFields(request.pair), "blocked", request.diagnostic,
                        runId, worldId, caseStart.toString(), Instant.now().toString(),
                        spec.assertions.map { AssertionResult(it, "blocked", emptyList()) }, true, emptyList())
                    val file = evidence.directory.resolve("case-${spec.id}.json"); exclusiveJson(file, blocked); caseReceipts += bound(file)
                    continue
                }
                check(baselineRestored) { "previous case did not prove complete baseline restoration; refusing potentially contaminated next case" }
                val driver = requireNotNull(DriverRegistry.resolve(spec.id))
                val parameters = request.bindings.getValue(spec.id).path("parameters")
                val steps = driver.plan(parameters)
                val observations = mutableListOf<DriverObservation>()
                val raw = evidence.directory.resolve("case-${spec.id}-native.jsonl")
                event("case_started", mapOf("case_id" to spec.id))
                val deadline = System.nanoTime() + Duration.ofSeconds(spec.timeoutSeconds).toNanos()
                for (step in steps) {
                    val remaining = deadline - System.nanoTime()
                    check(remaining > 0) { "case deadline expired: ${spec.id}" }
                    val timeout = Duration.ofNanos(minOf(remaining, Duration.ofMillis(step.action.timeoutTicks.toLong() * 50).toNanos()))
                    val terminal = mailbox.execute(spec.id, step.action.id, step.action.parameters, timeout,
                        ensureAlive = session::ensureConnected)
                    Files.writeString(raw, suiteMapper.writeValueAsString(mapOf("step" to step.name, "observation" to terminal)) + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                    observations += DriverObservation(step.name, terminal.driver, terminal.source, terminal.kind, terminal.payload)
                }
                val verdict = when (val evaluation = evaluateCase(driver, parameters, observations)) {
                    is DriverOutcome.Fatal -> error(evaluation.reason)
                    is DriverOutcome.Verdict -> evaluation.value
                }
                require(verdict.state in caseStates)
                val cleaned = mailbox.execute(spec.id, "fixture.cleanup", jsonObject(), Duration.ofSeconds(120),
                    ensureAlive = session::ensureConnected).payload
                require(cleaned.path("cleanup_complete").isBoolean && cleaned.path("cleanup_complete").asBoolean()) { "case fixture cleanup failed" }
                baselineRestored = cleaned.path("baseline_restored").isBoolean && cleaned.path("baseline_restored").asBoolean()
                val provisions = observations.flatMap { observation -> observation.payload.path("provisioned_targets").map { it.asText() } }.distinct()
                require(provisions.toSet() == driver.fixtureProvisionedTargets(parameters)) { "fixture provision receipt differs from declared driver setup" }
                require(provisions.all { id -> id in spec.targets }) { "undeclared fixture provisions: ${spec.id}" }
                val acquisitionTargets = m.path("acquisitions").filter { a -> a.path("coverage").any { it.asText() == spec.id } }.map { it.path("id").asText() }
                require(provisions.none { it in acquisitionTargets }) { "fixture-provided resource cannot prove acquisition" }
                val rawBound = bound(raw)
                require(spec.assertions.toSet() == driver.assertionIds) { "driver does not own case assertions" }
                val assertions = spec.assertions.map { id -> AssertionResult(id, verdict.state,
                    listOf(AssertionEvidence("native-interaction", rawBound))) }
                val result = CaseResult("bc.playtest_case_result.v2", spec.id, caseFingerprint(spec), catalogue,
                    profileFingerprint(profile), candidateHashFields(request.pair), verdict.state, request.diagnostic, runId,
                    worldId, caseStart.toString(), Instant.now().toString(), assertions, true, provisions)
                val file = evidence.directory.resolve("case-${spec.id}.json"); exclusiveJson(file, result); caseReceipts += bound(file)
                results[spec.id] = verdict.state
                event("case_completed", mapOf("case_id" to spec.id, "state" to verdict.state, "source" to "native", "reason" to verdict.reason))
                if (verdict.state != "passed") {
                    require(baselineRestored) {
                        "case failed and baseline restoration is unproved; independent continuation is unsafe"
                    }
                }
            }
            session.ensureConnected(); session.disconnectClient(); event("client_disconnected")
            session.saveAndStopServer(); saved = true; event("server_saved_and_stopped")
            session.validate(); event("world_validated")
            require(results.size == request.cases.size && results.values.all { it == "passed" }) { "campaign includes required non-passing cases" }
        } catch (error: Throwable) { failure = error; event("failure", mapOf("message" to error.message)); session.diagnostics() }
        finally {
            try { session.close(); cleanup = true; event("process_cleanup") }
            catch (error: Throwable) { if (failure == null) failure = error else failure!!.addSuppressed(error) }
            try {
                session.recordLogMessages(); request.pair.assertUnchanged()
                require(Hashes.sha256(request.manifestPath) == catalogue && Hashes.sha256(request.support) == supportHash) { "campaign inputs changed" }
            } catch (error: Throwable) { if (failure == null) failure = error else failure!!.addSuppressed(error) }
            event("campaign_finished", mapOf("status" to if (failure != null) "failed" else if (request.diagnostic) "diagnostic" else "passed"))
            val nativeFile = mailbox.directory.resolve("observations.jsonl")
            val marker = world?.resolve(".bc-suite-world-id")
            val receipt = linkedMapOf<String, Any?>("schema" to "bc.playtest_campaign.v2",
                "status" to if (failure != null) "failed" else if (request.diagnostic) "diagnostic" else "passed",
                "diagnostic" to request.diagnostic, "catalogue_sha256" to catalogue,
                "profile_sha256" to profileFingerprint(profile), "candidate" to candidateHashFields(request.pair),
                "test_support_sha256" to supportHash, "instrumented" to true, "seed" to 424242,
                "world_id" to worldId, "run_id" to runId, "world_directory" to world?.toAbsolutePath()?.toString(),
                "world_marker" to marker?.takeIf(Files::isRegularFile)?.let(::bound), "worlds_created" to if (world != null) 1 else 0,
                "cleanup_complete" to cleanup, "saved_and_validated" to saved, "surviving_owned_pids" to emptyList<Long>(),
                "case_ids" to request.cases.map { it.id }, "case_results" to caseReceipts,
                "observed_rosters" to observedRosters,
                "events" to bound(events), "native_protocol" to nativeFile.takeIf(Files::isRegularFile)?.let(::bound),
                "native_requests" to mailbox.directory.resolve("requests.jsonl").takeIf(Files::isRegularFile)?.let(::bound),
                "driver_bindings_sha256" to jsonFingerprint(m.path("driver_bindings")),
                "started_at" to started.toString(), "completed_at" to Instant.now().toString(),
                "hardware" to bound(hardware), "fixture_overrides" to bound(overrides), "failure" to failure?.message)
            exclusiveJson(evidence.directory.resolve("campaign.json"), receipt)
            evidence.finish(failure)
        }
        failure?.let { throw it }
    }
}
internal fun candidateHashFields(pair: CandidatePair): Map<String, String> = mapOf("client_sha256" to pair.clientSha256, "server_sha256" to pair.serverSha256)
internal fun jsonFingerprint(value: JsonNode): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(suiteMapper.writeValueAsBytes(value)).joinToString("") { "%02x".format(it) }
