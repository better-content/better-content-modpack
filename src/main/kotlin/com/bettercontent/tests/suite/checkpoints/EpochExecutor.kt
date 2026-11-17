package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.*
import com.bettercontent.tests.suite.*
import com.bettercontent.tests.suite.drivers.*
import com.bettercontent.tests.suite.client.authenticInputDrivers
import com.bettercontent.tests.suite.client.resolveInputRuntimeAction
import com.bettercontent.tests.suite.client.passiveObservationDrivers
import com.bettercontent.tests.suite.client.PassiveObserverArm
import com.bettercontent.tests.suite.client.resolveObservedActorAction
import com.bettercontent.tests.suite.client.resolvePassiveObserverAction
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import java.util.UUID

internal data class EpochTrial(val spec: SuiteCase, val driver: CaseDriver, val request: TrialRequest)
internal data class ExecutedTrial(val planned: EpochTrial, val receipt: TrialReceipt, val file: BoundFile, val raw: BoundFile,
    val verdict: MeasurementVerdict, val failure: Throwable?)
internal data class EpochOutcome(val epoch: SessionEpoch, val reference: BoundFile, val trials: List<ExecutedTrial>,
    val roots: Map<String, Path>, val failure: Throwable?, val stopStartedAt: Instant?) {
    val safelyClosed: Boolean get() = epoch.closure != null
}
internal fun checkpointBound(file: Path): BoundFile = BoundFile(file.toAbsolutePath().toString(), Hashes.sha256(file))

/** One launch, one native stream. No retries, restores, or hidden world replacement inside an epoch. */
internal class EpochExecutor(private val request: V3CampaignRequest, private val campaignRoot: Path,
    private val bindings: CheckpointBindings, private val store: CheckpointStore, private val ledger: SamplingLedger,
    private val campaignDeadline: Instant, private val campaignEvent: (String, Map<String, Any?>) -> Unit) {
    private var ordinal = 0

    fun run(branch: String, input: CheckpointRef?, trials: List<EpochTrial>): EpochOutcome {
        val epochStart = Instant.now()
        require(epochStart.isBefore(campaignDeadline)) { "campaign hard deadline expired before epoch" }
        val origin = input == null
        require(origin == (branch == "origin") && (!origin || trials.isEmpty()))
        val id = "epoch-%05d".format(ordinal++)
        val epochDirectory = campaignRoot.resolve("epochs/$id")
        Files.createDirectories(epochDirectory.parent)
        Files.createDirectory(epochDirectory)
        val freshSites = trials.filter { (it.driver as? ExternalSamplingDriver)?.requireFreshTerrain == true }
            .flatMap { (it.driver as ExternalSamplingDriver).samplingIntents(it.request).filterIsInstance<SamplingIntent.Site>() }
        val freshRegions = if (freshSites.isNotEmpty()) verifyFreshTerrain(store.verify(requireNotNull(input)), freshSites) else emptyList()
        val restored = input?.let { store.restore(it, id, epochDirectory.resolve("runtime")) }
        val nativeSession = "epoch-${UUID.randomUUID()}"
        val clientRequired = !origin && trials.any { it.driver.clientRequired }
        require(trials.all { it.driver.clientRequired == clientRequired }) { "mixed client/server-only cases require separate epochs" }
        val plannedSteps = trials.flatMap { trial -> if (trial.driver is TrialCaseDriver) trial.driver.planTrial(trial.request) else trial.driver.plan(trial.request.parameters) }
        val profiles = plannedSteps.filter { it.action.id in setOf("client.framebuffer", "client.input_hold") }
            .map { it.action.parameters.path("profile").asText() }.distinct()
        require(profiles.size <= 1) { "different visual/input profiles require separate reviewed trials/epochs" }
        val visualProfile = profiles.singleOrNull() ?: "1280x720@2"
        val inputPlanned = plannedSteps.any { it.action.id in authenticInputDrivers }
        require(!inputPlanned || (clientRequired && plannedSteps.any { it.action.id == "client.input_hold" } &&
            profiles.singleOrNull() == visualProfile)) { "authentic input requires exact reviewed hold/profile" }
        require(clientRequired || plannedSteps.none { it.action.source == "client" })
        val context = mapOf("campaign_id" to bindings.campaignId, "lineage_id" to bindings.lineageId,
            "purpose" to bindings.purpose, "epoch_id" to id, "branch_id" to branch, "native_session_id" to nativeSession)
        val evidence = SessionEvidence(request.base.root, nativeSession, "campaign-epoch", request.base.pair,
            diagnostic = true, ownedDirectory = epochDirectory.resolve("evidence"), eventContext = context)
        val mailbox = NativeMailbox(evidence.directory.resolve("native"), nativeSession)
        val clientMailbox = NativeMailbox(evidence.directory.resolve("client-native"), "client-${UUID.randomUUID()}")
        Files.createFile(mailbox.directory.resolve("requests.jsonl"))
        if (clientRequired) Files.createFile(clientMailbox.directory.resolve("requests.jsonl"))
        val expectedEmiSha256 = if (plannedSteps.any { it.action.id == "client.emi_index" })
            candidateEmiJarSha256(request.base.pair.client, request.base.pair.server) else null
        val clientArguments = clientMailbox.javaArguments() + expectedEmiSha256?.let { listOf("-Dbc.playtest.emiSha256=$it") }.orEmpty()
        val epochRuntime = SuiteEpochRuntime(epochDirectory.resolve("runtime"), request.launcherWheel,
            request.launcherSha256, request.sandboxExecutable, request.sandboxSha256, restored?.roots?.mapValues { Path.of(it.value) }, visualProfile)
        val session = PackSession(evidence, SessionRequest(SessionTier.SMOKE), instrumentation =
            SuiteInstrumentation(request.base.support, bindings.supportSha256, mailbox.javaArguments(), clientRequired), epochRuntime = epochRuntime,
            clientInstrumentation = SuiteInstrumentation(request.base.support, bindings.supportSha256, clientArguments,
                clientRequired, expectedEmiSha256, clientInstrumentation = clientRequired))
        val started = Instant.now()
        val executed = mutableListOf<ExecutedTrial>()
        var actionOrdinal = 0L
        var failure: Throwable? = null
        var saved = false
        var stopStartedAt: Instant? = null
        var closed = false
        var roots = emptyMap<String, Path>()
        var identities = emptyList<OwnedProcessIdentity>()
        fun fail(error: Throwable) { if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error) }
        try {
            evidence.event("epoch_ready", mapOf("restored_from" to input?.id,
                "restore_receipt" to restored?.restoreReceipt, "input_checkpoint" to input, "fresh_input_regions" to freshRegions))
            campaignEvent(if (origin) "epoch_prepared" else "epoch_restored", context + mapOf("input_checkpoint" to input, "restore_receipt" to restored?.restoreReceipt))
            session.prepare(); roots = session.suiteRuntimeRoots()
            if (origin) {
                val world = session.suiteWorldDirectory()
                Files.createDirectories(world)
                Files.writeString(world.resolve(".bc-suite-world-id"), bindings.lineageId + "\n", StandardOpenOption.CREATE_NEW)
                campaignEvent("world_created", context)
            }
            session.startServer(); campaignEvent("epoch_started", context); session.awaitServerReady()
            if (clientRequired) {
                session.connectClient(); session.awaitClientJoined(); session.heartbeat()
                if (inputPlanned) session.createSuiteClientInputRuntime(bindings, id, clientMailbox.runId,
                    clientMailbox.directory, visualProfile)
            }
            val lifecycleLimit = epochStart.plusSeconds(if (origin) request.budget.originSeconds else request.budget.epochStartSeconds)
            val inventoryWall = minOf(Duration.ofMinutes(10), Duration.between(Instant.now(), lifecycleLimit),
                Duration.between(Instant.now(), campaignDeadline))
            require(!inventoryWall.isNegative && !inventoryWall.isZero) { "reviewed epoch launch budget expired before inventory" }
            val inventory = mailbox.executeWithWallDeadline("campaign.inventory", "inventory.discover", jsonObject(),
                inventoryWall, 12_000, ensureAlive = if (clientRequired) session::ensureConnected else session::ensureSuiteServerAlive).payload
            verifyLiveInventory(request.base.manifest, inventory, origin, request.purpose,
                request.base.cases.map { it.id }.toSet(), mailbox.directory)
            require(!Instant.now().isAfter(epochStart.plusSeconds(
                if (origin) request.budget.originSeconds else request.budget.epochStartSeconds))) {
                "reviewed epoch launch/inventory lifecycle budget expired"
            }
            val joinedActor = inventory.path("player_uuids").takeIf { it.isArray && it.size() == 1 }
                ?.get(0)?.takeIf { it.isTextual }?.asText()
            if (clientRequired) require(joinedActor != null) { "client actions require exactly one observed joined actor" }
            for (trial in trials) {
                // Failed carry-forward state is never consumed by a later case in the same epoch.
                if (executed.any { it.receipt.state != "passed" }) break
                val result = runTrial(trial, id, branch, joinedActor, requireNotNull(input), requireNotNull(restored),
                    evidence, mailbox, clientMailbox, session) {
                    ++actionOrdinal
                }
                executed += result
                result.failure?.let(::fail)
                if (result.receipt.state != "passed" && result.failure == null) fail(IllegalStateException(result.verdict.reason))
            }
            stopStartedAt = Instant.now()
            if (!clientRequired) session.stopDiscoveryServer() else {
                session.ensureConnected(); session.disconnectClient(); session.saveAndStopServer()
            }
            session.validate(); saved = true
        } catch (error: Throwable) {
            fail(error)
            runCatching { session.diagnostics() }.exceptionOrNull()?.let(::fail)
        } finally {
            try { session.close(); closed = true } catch (error: Throwable) { fail(error) }
            try { identities = session.suiteProcessIdentities() } catch (error: Throwable) { fail(error); closed = false }
            try {
                session.recordLogMessages(); request.base.pair.assertUnchanged()
                if (clientRequired) {
                    val caps = suiteMapper.readTree(clientMailbox.directory.resolve("capabilities.json").toFile())
                    require(caps.path("schema").asText() == "bc.playtest_capabilities.v2" && caps.path("source").asText() == "client" &&
                        caps.path("run_id").asText() == clientMailbox.runId && caps.path("token").asText() == clientMailbox.token &&
                        caps.path("actual_client").isBoolean && caps.path("actual_client").asBoolean() && clientMailbox.token != mailbox.token) {
                        "authentic independent client observer did not initialize"
                    }
                }
            } catch (error: Throwable) { fail(error) }
            if (saved && closed && stopStartedAt != null && Instant.now().isAfter(stopStartedAt.plusSeconds(
                request.budget.epochStopSealSeconds))) fail(IllegalStateException("epoch save/stop lifecycle exceeded reviewed budget"))
            if (!Instant.now().isBefore(campaignDeadline)) fail(IllegalStateException("campaign hard deadline expired during epoch"))
            evidence.finish(failure)
        }
        val closure = if (saved && closed) {
            val file = evidence.directory.resolve("epoch-closure.json")
            val time = Instant.now().toString()
            exclusiveJson(file, mapOf("schema" to "bc.playtest_epoch_closure.v2", "purpose" to bindings.purpose, "epoch_id" to id,
                "native_session_id" to nativeSession, "closed_at" to time, "events" to checkpointBound(evidence.directory.resolve("events.jsonl")),
                "server_log" to checkpointBound(evidence.directory.resolve("server.log")),
                "world_marker" to checkpointBound(roots.getValue("server").resolve("world/.bc-suite-world-id")),
                "world_save" to checkpointBound(roots.getValue("server").resolve("world/level.dat"))))
            EpochClosure(id, nativeSession, time, true, true, identities, emptyList(), checkpointBound(file))
        } else null
        val epoch = SessionEpoch(id, branch, bindings.campaignId, bindings.lineageId, nativeSession, input, restored,
            started.toString(), Instant.now().toString(), if (failure == null) "passed" else "failed", closure,
            checkpointBound(evidence.directory.resolve("events.jsonl")),
            mailbox.directory.resolve("requests.jsonl").takeIf(Files::isRegularFile)?.let(::checkpointBound),
            mailbox.directory.resolve("observations.jsonl").takeIf(Files::isRegularFile)?.let(::checkpointBound), executed.map { it.file },
            if (clientRequired && Files.isRegularFile(clientMailbox.directory.resolve("capabilities.json"))) ClientStreamBinding(
                clientMailbox.runId, checkpointBound(clientMailbox.directory.resolve("capabilities.json")),
                clientMailbox.directory.resolve("requests.jsonl").takeIf { Files.isRegularFile(it) && Files.size(it) > 0 }?.let(::checkpointBound),
                clientMailbox.directory.resolve("observations.jsonl").takeIf(Files::isRegularFile)?.let(::checkpointBound),
                clientMailbox.directory.resolve("input-events.jsonl").takeIf(Files::isRegularFile)?.let(::checkpointBound),
                session.suiteClientInputRuntime()) else null,
            purpose = bindings.purpose)
        val file = epochDirectory.resolve("epoch.json")
        exclusiveJson(file, suiteMapper.valueToTree<ObjectNode>(epoch).put("schema", "bc.playtest_epoch.v3"))
        campaignEvent("epoch_closed", context + mapOf("epoch" to checkpointBound(file), "safely_closed" to (closure != null), "status" to epoch.status))
        return EpochOutcome(epoch, checkpointBound(file), executed, roots, failure, stopStartedAt)
    }

    private fun runTrial(trial: EpochTrial, epochId: String, branchId: String, joinedActor: String?,
        input: CheckpointRef, restored: RestoredCheckpoint,
        evidence: SessionEvidence, mailbox: NativeMailbox, clientMailbox: NativeMailbox, session: PackSession,
        nextActionOrdinal: () -> Long): ExecutedTrial {
        val start = Instant.now()
        val trialDeadline = start.plusSeconds(request.budget.trialSeconds(trial.spec))
        require(trialDeadline.isBefore(campaignDeadline) || trialDeadline == campaignDeadline) {
            "reviewed trial cannot fit remaining campaign hard budget"
        }
        val samplingClaims = (trial.driver as? ExternalSamplingDriver)?.samplingIntents(trial.request).orEmpty().map { intent ->
            when (intent) {
                is SamplingIntent.Site -> ledger.claimSite(intent.dimension, intent.chunkX, intent.chunkZ, trial.spec.id, intent.footprintRadiusChunks)
                SamplingIntent.ThreatAttempt -> ledger.claimThreatAttempt(trial.spec.id)
            }
        }
        val ledgerPrefix = if (samplingClaims.isNotEmpty()) {
            val original = ledger.audit()
            val snapshot = evidence.directory.resolve("trial-${trial.spec.id}-${trial.request.trialId}-ledger.jsonl")
            require(snapshot.normalize().parent == evidence.directory)
            Files.copy(Path.of(original.path), snapshot)
            require(Hashes.sha256(snapshot) == original.sha256) { "ledger changed while retaining its pre-action prefix" }
            checkpointBound(snapshot)
        } else null
        require(!Instant.now().isAfter(start.plusSeconds(request.budget.trialSetupSeconds))) {
            "reviewed per-trial pre-action setup expired"
        }
        val rawPath = evidence.directory.resolve("trial-${trial.spec.id}-${trial.request.trialId}-native.jsonl")
        require(rawPath.normalize().parent == evidence.directory) { "unsafe trial identity" }
        Files.createFile(rawPath)
        val observations = mutableListOf<DriverObservation>()
        val passiveArms = mutableMapOf<String, PassiveObserverArm>()
        var failure: Throwable? = null
        var cleanup = false
        var verdict = MeasurementVerdict("failed", "trial did not finish")
        fun fail(error: Throwable) { if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error) }
        fun record(step: String, observation: NativeObservation, ordinal: Long) {
            Files.writeString(rawPath, suiteMapper.writeValueAsString(mapOf("step" to step, "observation" to observation)) + "\n", StandardOpenOption.APPEND)
            evidence.event("native_action_completed", mapOf("case_id" to trial.spec.id, "trial_id" to trial.request.trialId,
                "step" to step, "source" to observation.source, "driver" to observation.driver, "action_ordinal" to ordinal,
                "request_sequence" to observation.requestSequence, "observation_sha256" to jsonFingerprint(suiteMapper.valueToTree(observation)),
                "monotonic_nanos" to System.nanoTime()))
            observations += DriverObservation(step, observation.driver, observation.source, observation.kind, observation.payload)
        }
        campaignEvent("trial_started", mapOf("case_id" to trial.spec.id, "trial_id" to trial.request.trialId, "epoch_id" to epochId))
        try {
            val steps = if (trial.driver is TrialCaseDriver) trial.driver.planTrial(trial.request) else trial.driver.plan(trial.request.parameters)
            for (planned in steps) {
                val prepared = planned.copy(action = resolveObservedActorAction(resolveInputRuntimeAction(
                    resolveExecutionAction(planned.action, ledgerPrefix?.sha256), session.suiteClientInputRuntime()), joinedActor))
                require(prepared.action.id != "player.dig.witness_v1") {
                    "unbound runtime target cannot be issued as controller evidence"
                }
                val remaining = minOf(Duration.between(Instant.now(), trialDeadline),
                    Duration.between(Instant.now(), campaignDeadline))
                require(!remaining.isNegative && !remaining.isZero) { "trial/campaign hard deadline expired before native action" }
                val allowance = request.budget.action(prepared.action)
                if (prepared.action.id in authenticInputDrivers || prepared.action.id in passiveObservationDrivers)
                    session.ensureConnected()
                val timeout = minOf(remaining, allowance.wall)
                val ordinal = nextActionOrdinal()
                val channel = if (prepared.action.source == "client") clientMailbox else mailbox
                val allocatedSequence = channel.reserveSequence()
                val witnessContext = if (prepared.action.id in passiveObservationDrivers)
                    controllerWitnessContext(bindings, epochId, channel.runId, branchId, trial.spec.id,
                        trial.request.trialId, "server", ordinal, allocatedSequence,
                        requireNotNull(joinedActor), prepared.action.id) else null
                val step = prepared.copy(action = resolvePassiveObserverAction(prepared.action,
                    witnessContext, passiveArms))
                val dispatchNanos = System.nanoTime()
                evidence.event("native_action_dispatched", mapOf("case_id" to trial.spec.id, "trial_id" to trial.request.trialId,
                    "step" to step.name, "source" to step.action.source, "driver" to step.action.id, "action_ordinal" to ordinal,
                    "request_sequence" to allocatedSequence, "parameters_sha256" to jsonFingerprint(step.action.parameters),
                    "monotonic_nanos" to dispatchNanos))
                try {
                    val terminal = channel.executeWithWallDeadline(trial.spec.id, step.action.id, step.action.parameters,
                        timeout, step.action.timeoutTicks, source = step.action.source,
                        ensureAlive = if (trial.driver.clientRequired) session::ensureConnected else session::ensureSuiteServerAlive,
                        allocatedSequence = allocatedSequence, dispatchNanos = dispatchNanos)
                    record(step.name, terminal, ordinal)
                    if (step.action.id == "scale.begin") require(terminal.payload.path("site_ledger_sha256").asText() == ledgerPrefix?.sha256) {
                        "native scale evidence did not bind the retained pre-action ledger prefix"
                    }
                } catch (error: NativeActionFailure) { record(step.name, error.observation, ordinal); throw error }
            }
            require(passiveArms.isEmpty()) { "passive observer armed without a later completed await" }
            val enriched = enrichRawEvidence(trial.driver, trial.request, observations,
                mapOf("server" to mailbox.directory, "client" to clientMailbox.directory))
            observations.clear(); observations.addAll(enriched)
            verdict = if (trial.driver is TrialCaseDriver) trial.driver.evaluateTrial(trial.request, observations)
                else when (val result = evaluateCase(trial.driver, trial.request.parameters, observations)) {
                    is DriverOutcome.Verdict -> result.value
                    is DriverOutcome.Fatal -> error(result.reason)
                }
            require(verdict.state in caseStates)
        } catch (error: Throwable) { fail(error); verdict = MeasurementVerdict("failed", error.message ?: error.javaClass.name) }
        finally {
            // Always attempted, even following an action exception, timeout, disconnect, or oracle failure.
            // A nonterminal protocol cannot be silently reset; cleanup failure is retained beside the original failure.
            try {
                val cleanupWindow = minOf(Duration.ofSeconds(request.budget.trialCleanupSeconds),
                    Duration.between(Instant.now(), campaignDeadline))
                require(!cleanupWindow.isNegative && !cleanupWindow.isZero) { "campaign deadline expired before fixture cleanup" }
                val payload = mailbox.executeWithWallDeadline(trial.spec.id, "fixture.cleanup", jsonObject(),
                    cleanupWindow, 2400,
                    ensureAlive = session::ensureSuiteServerAlive).payload
                require(payload.path("cleanup_complete").isBoolean && payload.path("cleanup_complete").asBoolean()) { "native owned fixture cleanup incomplete" }
                cleanup = true
            } catch (error: Throwable) { fail(error) }
        }
        val provisions = observations.flatMap { it.payload.path("provisioned_targets").map { item -> item.asText() } }.toSet()
        if (failure == null) try {
            require(provisions == trial.request.provisionedTargets && trial.spec.targets.containsAll(provisions)) { "fixture provision differs from authored trial" }
            val acquisitions = request.base.manifest.path("acquisitions").filter { row -> row.path("coverage").any { it.asText() == trial.spec.id } }.map { it.path("id").asText() }
            require(provisions.none { it in acquisitions }) { "fixture grant cannot prove natural acquisition" }
            require(Instant.now().isBefore(trialDeadline) && Instant.now().isBefore(campaignDeadline)) {
                "trial or campaign deadline expired including action/cleanup"
            }
        } catch (error: Throwable) { fail(error) }
        val state = if (failure != null) "failed" else verdict.state
        val receipt = TrialReceipt(trial.request.trialId, trial.request.targetId, input.id, epochId, state, observations,
            inputCheckpointVerified = restored.reference == input, cleanupComplete = cleanup)
        val file = evidence.directory.resolve("trial-${trial.spec.id}-${trial.request.trialId}.json")
        exclusiveJson(file, mapOf("schema" to "bc.playtest_trial.v3", "case_id" to trial.spec.id, "trial_id" to trial.request.trialId,
            "target_id" to trial.request.targetId, "epoch_id" to epochId, "native_session_id" to mailbox.runId,
            "input_checkpoint" to input, "parameters_sha256" to jsonFingerprint(trial.request.parameters), "state" to state,
            "started_at" to start.toString(), "completed_at" to Instant.now().toString(), "native_actions" to checkpointBound(rawPath),
            "provisioned_targets" to provisions.sorted(), "cleanup_complete" to cleanup,
            "sampling_claims" to samplingClaims.map { mapOf("sequence" to it.sequence, "sha256" to it.sha256) }, "sampling_ledger" to ledgerPrefix,
            "failure" to failure?.stackTraceToString(), "oracle" to verdict))
        campaignEvent("trial_completed", mapOf("case_id" to trial.spec.id, "trial_id" to trial.request.trialId, "epoch_id" to epochId,
            "state" to state, "receipt" to checkpointBound(file), "cleanup_complete" to cleanup))
        return ExecutedTrial(trial, receipt, checkpointBound(file), checkpointBound(rawPath), verdict, failure)
    }
}

internal fun verifyLiveInventory(manifest: JsonNode, observed: JsonNode, origin: Boolean,
    purpose: ExecutionPurpose = ExecutionPurpose.QUALIFICATION, selectedCaseIds: Set<String> = emptySet(),
    nativeDirectory: Path? = null) {
    require(observed.path("seed").isIntegralNumber && observed.path("seed").asLong() == 424242L) { "native world seed differs from fixed profile" }
    if (origin) require(observed.path("player_count").isIntegralNumber && observed.path("player_count").asInt() == 0 &&
        observed.path("player_uuids").isArray && observed.path("player_uuids").isEmpty) { "origin must precede every player login" }
    if (purpose == ExecutionPurpose.DIAGNOSTIC) {
        require(selectedCaseIds.isNotEmpty() && observed.path("gaps").isArray &&
            observed.path("gaps").all { it.isTextual && it.asText().isNotBlank() }) { "diagnostic inventory/gaps malformed" }
        val required = manifest.path("cases").filter { it.path("id").asText() in selectedCaseIds }
            .flatMap { c -> c.path("required_inventory_domains").map { it.asText() } }.toSet()
        require(required.isNotEmpty() && observed.path("gaps").none { it.asText() in required } &&
            required.all { domain ->
            observed.path("complete_by_domain").path(domain).isBoolean &&
                observed.path("complete_by_domain").path(domain).asBoolean()
        }) { "selected diagnostic inventory domains incomplete" }
        val files = observed.path("files")
        require(files.isArray && files.map { it.path("domain").asText() }.distinct().size == files.size() &&
            required.all { domain -> files.count { it.path("domain").asText() == domain &&
                it.path("complete").isBoolean && it.path("complete").asBoolean() } == 1 }) {
            "selected diagnostic inventory raw domain missing"
        }
        val directory = requireNotNull(nativeDirectory) { "diagnostic raw inventory directory missing" }.toRealPath()
        files.filter { it.path("domain").asText() in required }.forEach { file ->
            val relative = Path.of(file.path("path").asText())
            require(!relative.isAbsolute && relative.normalize() == relative && relative.nameCount > 1 &&
                relative.startsWith("inventory") && file.path("sha256").asText().matches(Regex("[0-9a-f]{64}")) &&
                file.path("bytes").isIntegralNumber && file.path("bytes").asLong() in 1..25_000_000) {
                "diagnostic inventory file metadata invalid"
            }
            val path = directory.resolve(relative).normalize()
            require(path.startsWith(directory) && !Files.isSymbolicLink(path.parent) && !Files.isSymbolicLink(path) &&
                path.toRealPath().startsWith(directory) && Files.isRegularFile(path) &&
                Files.size(path) == file.path("bytes").asLong()) { "diagnostic inventory file location/size changed" }
            val bytes = Files.newInputStream(path, java.nio.file.LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(25_000_001) }
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            require(bytes.size.toLong() == file.path("bytes").asLong() && hash == file.path("sha256").asText()) {
                "diagnostic inventory file bytes changed"
            }
        }
        return
    }
    require(observed.path("complete").isBoolean && observed.path("complete").asBoolean() && observed.path("gaps").isArray && observed.path("gaps").isEmpty) {
        "effective native inventory incomplete; a checkpoint cannot upgrade discovery gaps"
    }
    val rosters = observed.path("rosters")
    require(rosters.isObject && rosters.fieldNames().asSequence().toSet() == requiredRosters)
    manifest.path("rosters").forEach { r -> require(rosters.path(r.path("kind").asText()).map { it.asText() }.toSet() ==
        r.path("entries").fieldNames().asSequence().toSet()) { "effective roster changed across epochs" } }
}
