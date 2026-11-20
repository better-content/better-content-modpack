package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.*
import com.bettercontent.tests.suite.*
import com.bettercontent.tests.suite.drivers.*
import com.bettercontent.tests.suite.client.authenticInputDrivers
import com.bettercontent.tests.suite.client.independentlyReviewedEmiHoverProjection
import com.bettercontent.tests.suite.client.resolveReviewedEmiMouseAction
import com.bettercontent.tests.suite.client.readClientInputRuntime
import com.bettercontent.tests.suite.client.resolveInputRuntimeAction
import com.bettercontent.tests.suite.client.resolveMenuSlotProjectionAction
import com.bettercontent.tests.suite.client.passiveObservationDrivers
import com.bettercontent.tests.suite.client.PassiveObserverArm
import com.bettercontent.tests.suite.client.requireReviewedInputObserverSequence
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
    private val physicalWatch = if (request.base.cases.any { it.id == "primitive.harvest" &&
        it.implementation.symbol == "PhysicalPrimitiveHarvestDriver" && it.implementation.state == "implemented" })
        readPredeclaredPhysicalWatchPlan(BoundFile(request.base.manifestPath.toAbsolutePath().normalize().toString(),
            Hashes.sha256(request.base.manifestPath))) else null
    private val physicalAncestors = physicalWatch?.let(::physicalAncestorBranchIds).orEmpty()
    private val physicalBranch = request.branches.singleOrNull { "primitive.harvest" in it.caseIds }?.id

    fun run(branch: String, input: CheckpointRef?, trials: List<EpochTrial>): EpochOutcome {
        val epochStart = Instant.now()
        require(epochStart.isBefore(campaignDeadline)) { "campaign hard deadline expired before epoch" }
        val origin = input == null
        require(origin == (branch == "origin") && (!origin || trials.isEmpty()))
        if (trials.any { it.spec.id == "world.onboarding" }) {
            val receipt = store.verify(requireNotNull(input))
            require(branch != "origin" && trials.size == 1 && receipt.id == "origin" &&
                receipt.parent == null && receipt.files.none { file -> file.root == "server" &&
                    listOf("world/playerdata/", "world/advancements/", "world/stats/")
                        .any(file.path::startsWith) }) {
                "first real onboarding must restore a sealed player-free origin before any world/client starts"
            }
        }
        if (trials.any { it.spec.id == "survival.food" }) {
            val onboarding = request.branches.single { "world.onboarding" in it.caseIds }
            val receipt = store.verify(requireNotNull(input))
            val actor = request.base.bindings.getValue("world.onboarding").path("parameters")
                .path("first_join").path("player_uuid").asText()
            require(receipt.id == onboarding.outputCheckpoint && receipt.parent?.id == "origin" &&
                readSealedPlayerInventory(receipt, actor).savedGameModeId == 0) {
                "food must inherit independently decoded saved Survival state from real onboarding"
            }
        }
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
        val expectedEmiSha256 = if (plannedSteps.any { it.action.id in
            setOf("client.emi_index", "client.emi_hover", "client.emi_page") })
            candidateEmiJarSha256(request.base.pair.client, request.base.pair.server) else null
        val clientArguments = clientMailbox.javaArguments() + expectedEmiSha256?.let { listOf("-Dbc.playtest.emiSha256=$it") }.orEmpty()
        val carried = physicalWatch?.takeIf { branch in physicalAncestors || branch == physicalBranch }
        val diagnosticActions = plannedSteps.filter { it.action.id == "block_mutation.control_positive_v1" }
        val diagnosticSetup = if (diagnosticActions.size == 1 && !origin && input != null &&
            store.verify(input).parent?.id == "origin" &&
            trials.size == 1 && plannedSteps.size == 1 && clientRequired &&
            request.purpose == ExecutionPurpose.DIAGNOSTIC) {
            loadPredeclaredDiagnosticMutationSetup(store, BoundFile(
                request.base.manifestPath.toAbsolutePath().normalize().toString(),
                Hashes.sha256(request.base.manifestPath)), input, trials.single().spec.id)
        } else null
        val diagnosticControl = if (diagnosticActions.isEmpty() || diagnosticSetup != null) null else {
            require(!origin && request.purpose == ExecutionPurpose.DIAGNOSTIC && carried == null &&
                trials.size == 1 && plannedSteps.size == 1 && diagnosticActions.size == 1 && clientRequired &&
                store.verify(requireNotNull(store.verify(requireNotNull(input)).parent)).parent?.id == "origin") {
                "positive diagnostic needs one separate restored, saved authored fixture from actor-ready onboarding"
            }
            loadPredeclaredDiagnosticMutationControl(store, BoundFile(
                request.base.manifestPath.toAbsolutePath().normalize().toString(),
                Hashes.sha256(request.base.manifestPath)),
                requireNotNull(input), trials.single().spec.id,
                diagnosticActions.single().action.parameters.path("control_id").asText())
        }
        val negativeActions = plannedSteps.filter { it.action.id in reviewedStage1NegativeActions.values }
        val diagnosticNegative = if (negativeActions.isEmpty()) null else {
            require(!origin && request.purpose == ExecutionPurpose.DIAGNOSTIC && carried == null &&
                diagnosticSetup == null && diagnosticControl == null && trials.size == 1 &&
                plannedSteps.size == 1 && negativeActions.size == 1 && clientRequired &&
                store.verify(requireNotNull(store.verify(requireNotNull(input)).parent)).parent?.id == "origin") {
                "expected negative needs one client-joined fresh process restored from actor-ready saved fixture"
            }
            loadPredeclaredDiagnosticMutationNegative(store, BoundFile(
                request.base.manifestPath.toAbsolutePath().normalize().toString(),
                Hashes.sha256(request.base.manifestPath)), requireNotNull(input),
                trials.single().spec.id, negativeActions.single().action.id)
        }
        val mutationScope = if (origin) null else if (carried != null)
            createCarryBlockMutationScope(store, requireNotNull(input), carried, bindings,
                id, nativeSession, evidence.directory)
        else if (diagnosticSetup != null) createPrelaunchDiagnosticSetupScope(store,
            diagnosticSetup, bindings, id, nativeSession, evidence.directory)
        else if (diagnosticControl != null) createPrelaunchDiagnosticControlScope(store,
            diagnosticControl, bindings, id, nativeSession, evidence.directory)
        else if (diagnosticNegative != null) createPrelaunchDiagnosticNegativeScope(store,
            diagnosticNegative, bindings, id, nativeSession, evidence.directory)
        else createInitialBlockMutationScope(store, requireNotNull(input),
            trials, bindings, id, nativeSession, evidence.directory)
        val naturalSelectors = trials.flatMap { trial -> trial.spec.targetBindings.map { raw ->
            suiteMapper.treeToValue(raw, ReviewedTargetSelector::class.java)
        } }.filter { it.schema == "bc.playtest_target_selector.v2" }
        val reviewedNatural = naturalSelectors.associate { selector -> selector.id to
            verifyReviewedStage1Coverage(selector, request.base.pair.client, request.base.pair.server, request.base.support) }
        val getterActions = plannedSteps.filter { it.action.id == DIAGNOSTIC_GETTER_ACTION }
        val selectedOrigins = if (reviewedNatural.isEmpty() && getterActions.isEmpty()) null
            else bindings.candidate.getValue("server_sha256")
        require(selectedOrigins == null || mutationScope != null &&
            (reviewedNatural.size == 1 || reviewedNatural.isEmpty() && getterActions.size == 1 &&
                trials.size == 1 && request.purpose == ExecutionPurpose.DIAGNOSTIC)) {
            "loader-origin sidecar requires one reviewed natural scope or one disposable diagnostic getter"
        }
        val birthScope = if (origin) null else createBlockBirthScope(store, requireNotNull(input),
            trials.map { it.spec }, Hashes.sha256(request.base.manifestPath), bindings, id, nativeSession,
            evidence.directory)
        birthScope?.let { replayBlockBirthScope(store, requireNotNull(input), trials.map { it.spec },
            Hashes.sha256(request.base.manifestPath), bindings, id, nativeSession, evidence.directory, it) }
        val diagnosticGetter = if (getterActions.isEmpty()) null else {
            require(trials.size == 1 && plannedSteps.size == 1 && getterActions.size == 1 &&
                input != null && mutationScope != null && birthScope == null && clientRequired &&
                request.purpose == ExecutionPurpose.DIAGNOSTIC)
            diagnosticGetterSelector(trials.single().spec, plannedSteps, request.purpose, input.id)
        }
        val epochRuntime = SuiteEpochRuntime(epochDirectory.resolve("runtime"), request.launcherWheel,
            request.launcherSha256, request.sandboxExecutable, request.sandboxSha256, restored?.roots?.mapValues { Path.of(it.value) }, visualProfile)
        val session = PackSession(evidence, SessionRequest(SessionTier.SMOKE), instrumentation =
            SuiteInstrumentation(request.base.support, bindings.supportSha256, mailbox.javaArguments(), clientRequired,
                blockMutationScope = mutationScope, blockBirthScope = birthScope?.scope,
                runtimeClassOriginsCandidateSha256 = selectedOrigins), epochRuntime = epochRuntime,
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
                "restore_receipt" to restored?.restoreReceipt, "input_checkpoint" to input, "fresh_input_regions" to freshRegions,
                "block_mutation_scope" to mutationScope,
                "block_mutation_baseline" to mutationScope?.let {
                    checkpointBound(evidence.directory.resolve("target-mutation-baseline.json"))
                }, "block_birth_scope" to birthScope?.scope,
                "block_birth_parent_absence" to birthScope?.parentAbsence,
                "runtime_class_origins_candidate_sha256" to selectedOrigins))
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
            mutationScope?.let { bound -> replaySavedBlockMutationPrefix(store, requireNotNull(input), bound,
                mailbox.directory, inventory.path("block_mutation_pre_action_prefix"), bindings, id, nativeSession,
                pristine = input.id == "origin") }
            if (selectedOrigins != null) {
                val caps = strictCapturedJson(capturedEvidenceBytes(BoundFile(
                    mailbox.directory.resolve("capabilities.json").toAbsolutePath().normalize().toString(),
                    Hashes.sha256(mailbox.directory.resolve("capabilities.json"))), 1_048_576))
                val pointer = caps.path("runtime_class_origins")
                require(pointer.path("path").asText() == "runtime-origins/$nativeSession.json")
                val origins = checkpointBound(mailbox.directory.resolve(pointer.path("path").asText()))
                val profile = reviewedNatural.values.singleOrNull()?.profile
                if (profile != null) {
                    val pins = strictCapturedJson(capturedEvidenceBytes(profile, 1_048_576)).path("class_pins")
                    verifySelectedOriginalClassOrigins(mailbox.directory, origins, id, nativeSession,
                        selectedOrigins, requireNotNull(mutationScope), pins, exactControlPaths = false)
                } else require(diagnosticGetter != null) {
                    "original loader sidecar may only serve reviewed natural profile or one diagnostic getter"
                }
                evidence.event("runtime_original_classes_bound", mapOf("sidecar" to origins,
                    "scope" to mutationScope, "profile" to profile))
            }
            birthScope?.let { bound -> replayBlockBirthPrefix(store, requireNotNull(input),
                trials.map { it.spec }, Hashes.sha256(request.base.manifestPath), bindings, id, nativeSession,
                evidence.directory, bound, mailbox.directory, inventory.path("block_birth_pre_action_prefix"),
                readyRequired = false) }
            verifyLiveInventory(request.base.manifest, inventory, origin, request.purpose,
                request.base.cases.map { it.id }.toSet(), mailbox.directory)
            require(!Instant.now().isAfter(epochStart.plusSeconds(
                if (origin) request.budget.originSeconds else request.budget.epochStartSeconds))) {
                "reviewed epoch launch/inventory lifecycle budget expired"
            }
            val joinedActor = inventory.path("player_uuids").takeIf { it.isArray && it.size() == 1 }
                ?.get(0)?.takeIf { it.isTextual }?.asText()
            if (clientRequired) require(joinedActor != null) { "client actions require exactly one observed joined actor" }
            if (diagnosticSetup != null) require(joinedActor == diagnosticSetup.actorUuid &&
                diagnosticActions.single().action.parameters.path("control_id").asText() ==
                    "level_chunk_delegated" &&
                diagnosticActions.single().action.parameters.path("player_uuid").asText() in
                    setOf(com.bettercontent.tests.suite.client.OBSERVED_ACTOR_MARKER, diagnosticSetup.actorUuid)) {
                "actual setup actor or delegated setter differs from predeclared fixture writer"
            }
            if (diagnosticControl != null) require(joinedActor == diagnosticControl.actorUuid &&
                diagnosticControl.setup == input &&
                diagnosticActions.single().action.parameters.path("player_uuid").asText() in
                    setOf(com.bettercontent.tests.suite.client.OBSERVED_ACTOR_MARKER, diagnosticControl.actorUuid)) {
                "diagnostic actuation actor differs from sealed predeclared fixture actor"
            }
            if (diagnosticNegative != null) require(joinedActor == diagnosticNegative.actorUuid &&
                diagnosticNegative.setup == input &&
                negativeActions.single().action.parameters.path("selector_id").asText() ==
                    diagnosticNegative.selectorId &&
                negativeActions.single().action.parameters.path("player_uuid").asText() ==
                    com.bettercontent.tests.suite.client.OBSERVED_ACTOR_MARKER) {
                "expected-negative actuation actor/selector differs from immutable saved fixture plan"
            }
            for (trial in trials) {
                // Failed carry-forward state is never consumed by a later case in the same epoch.
                if (executed.any { it.receipt.state != "passed" }) break
                val result = runTrial(trial, id, branch, joinedActor, requireNotNull(input), requireNotNull(restored),
                    evidence, mailbox, clientMailbox, session, mutationScope, birthScope,
                    trials.map { it.spec }, reviewedNatural) {
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
            if (origin && saved && closed && failure == null) try {
                evidence.event("origin_source_streams_bound", mapOf(
                    "native_requests" to checkpointBound(mailbox.directory.resolve("requests.jsonl")),
                    "native_observations" to checkpointBound(mailbox.directory.resolve("observations.jsonl"))))
            } catch (error: Throwable) { fail(error) }
            if (birthScope != null && saved && closed && failure == null) try {
                val scope = suiteMapper.readTree(Path.of(birthScope.scope.path).toFile())
                val status = checkpointBound(mailbox.directory.resolve(
                    "births/${scope.path("scope_id").asText()}.status.json"))
                replayStoppedBlockBirthStatus(store, requireNotNull(input), trials.map { it.spec },
                    Hashes.sha256(request.base.manifestPath), bindings, id, nativeSession,
                    evidence.directory, birthScope, mailbox.directory, status)
                evidence.event("block_birth_stop_bound", mapOf("scope" to birthScope.scope,
                    "parent_absence" to birthScope.parentAbsence, "status" to status))
            } catch (error: Throwable) { fail(error) }
            if (mutationScope != null && saved && closed && failure == null) try {
                val scope = strictCapturedJson(capturedEvidenceBytes(mutationScope, 1_048_576))
                val status = checkpointBound(mailbox.directory.resolve(
                    "mutations/${scope.path("scope_id").asText()}.status.json"))
                if (diagnosticGetter != null) {
                    val stopped = verifyExpectedDiagnosticGetterStop(store, requireNotNull(input), mutationScope,
                        status, mailbox.directory, executed.single(), bindings, id, nativeSession)
                    evidence.event("block_mutation_expected_diagnostic_failure_bound", mapOf(
                        "scope" to mutationScope, "status" to status,
                        "failure_reason" to stopped.path("failure_reason").asText(),
                        "selector_id" to "state_container_exposed",
                        "preseal_only_not_control_acceptance" to true))
                } else if (diagnosticNegative != null) {
                    require(executed.size == 1 && executed.single().planned.spec.id == diagnosticNegative.caseId &&
                        executed.single().receipt.state == "passed" &&
                        executed.single().receipt.observations.size == 1 &&
                        executed.single().receipt.observations.single().driver == diagnosticNegative.actionId)
                    val actual = executed.single().receipt.observations.single().payload
                    val pre = actual.path("actual_pre_action_mutation_prefix")
                    val result = actual.path("resulting_prefix")
                    val saved = replaySavedBlockMutationPrefix(store, requireNotNull(input), mutationScope,
                        mailbox.directory, pre, bindings, id, nativeSession, pristine = false)
                    require(saved.byteCount == 0 && saved.records == 0 &&
                        actual.path("block_mutation_pre_action_prefix") == pre &&
                        actual.path("scope_id").asText() == scope.path("scope_id").asText() &&
                        actual.path("selector_id").asText() == diagnosticNegative.selectorId)
                    val stopped = strictCapturedJson(capturedEvidenceBytes(status, 1_048_576))
                    require(stopped.path("scope_id").asText() == scope.path("scope_id").asText() &&
                        stopped.path("scope_file_sha256").asText() == mutationScope.sha256 &&
                        stopped.path("source_checkpoint_id").asText() == input.id &&
                        stopped.path("source_checkpoint_sha256").asText() == input.receipt.sha256 &&
                        stopped.path("path").asText() == "mutations/${scope.path("scope_id").asText()}.jsonl")
                    replayFailedDiagnosticStop(pre, result, stopped,
                        setOf(birthChunkKey(diagnosticNegative.position)), diagnosticNegative.failureReason,
                        ioAppend = diagnosticNegative.selectorId == "cap_or_io_failure")
                    evidence.event("block_mutation_expected_diagnostic_failure_bound", mapOf(
                        "scope" to mutationScope, "status" to status,
                        "failure_reason" to diagnosticNegative.failureReason,
                        "selector_id" to diagnosticNegative.selectorId,
                        "preseal_only_not_control_acceptance" to true))
                } else {
                    verifyStoppedBlockMutationStatus(store, requireNotNull(input), mutationScope,
                        mailbox.directory, status, bindings, id, nativeSession,
                        pristine = input.id == "origin", requireShutdownDrain = true)
                    evidence.event("block_mutation_stop_bound", mapOf("scope" to mutationScope, "status" to status))
                }
            } catch (error: Throwable) { fail(error) }
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
        mutationScope: BoundFile?, birthScope: PrelaunchBlockBirthScope?, birthSpecs: List<SuiteCase>,
        reviewedNatural: Map<String, ReviewedStage1Coverage>, nextActionOrdinal: () -> Long): ExecutedTrial {
        val start = Instant.now()
        if (trial.spec.id in setOf("control.travel.walk-route", "control.client.inventory-screen",
            "control.client.emi-navigation"))
            require(joinedActor != null &&
                trial.request.parameters.path("player_uuid").asText() == joinedActor &&
                session.suiteClientInputRuntime() != null) {
                "reviewed physical-input control actor is not the actual joined server player or host-bound client"
            }
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
        val emiPin = if (trial.spec.id == "control.client.emi-navigation")
            candidateEmiJarSha256(request.base.pair.client, request.base.pair.server) else null
        var actualPoint: JsonNode? = null
        var actualPointSequence: Long? = null
        var reviewedEmiProjection: String? = null
        var firstMenuProjection: String? = null
        var hoveredMenuProjection: String? = null
        var firstMenuObservation: JsonNode? = null
        var menuPointed = false
        var frozenNatural: FrozenNaturalTarget? = null
        var frozenNaturalFile: BoundFile? = null
        val passiveArms = mutableMapOf<String, PassiveObserverArm>()
        var physicalHoldMayBeDown = false
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
            if (observation.source == "server") {
                mutationScope?.let { bound ->
                    replaySavedBlockMutationPrefix(store, input, bound, mailbox.directory,
                        observation.payload.path("block_mutation_pre_action_prefix"), bindings, epochId, mailbox.runId,
                        pristine = input.id == "origin")
                }
                birthScope?.let { bound -> replayBlockBirthPrefix(store, input, birthSpecs,
                    Hashes.sha256(request.base.manifestPath), bindings, epochId, mailbox.runId,
                    evidence.directory, bound, mailbox.directory, observation.payload.path("block_birth_pre_action_prefix"),
                    readyRequired = false) }
            }
        }
        campaignEvent("trial_started", mapOf("case_id" to trial.spec.id, "trial_id" to trial.request.trialId, "epoch_id" to epochId))
        try {
            val steps = if (trial.driver is TrialCaseDriver) trial.driver.planTrial(trial.request) else trial.driver.plan(trial.request.parameters)
            requireReviewedInputObserverSequence(steps, trial.spec.id)
            for (planned in steps) {
                val resolved = resolveDiagnosticGetterAction(resolveObservedActorAction(resolveInputRuntimeAction(
                    resolveExecutionAction(planned.action, ledgerPrefix?.sha256), session.suiteClientInputRuntime()), joinedActor),
                    trial.spec, request.purpose, input, mutationScope, trial.request.parameters, store)
                val prepared = planned.copy(action = if (resolved.id == "client.input_hold" &&
                    resolved.parameters.path("kind").asText() in setOf("emi_mouse_left", "emi_mouse_right")) {
                    require(trial.spec.id == "control.client.emi-navigation")
                    resolveReviewedEmiMouseAction(resolved, requireNotNull(reviewedEmiProjection) {
                        "no independently reviewed real rendered EMI hover before mouse press"
                    })
                } else resolveMenuSlotProjectionAction(resolved, firstMenuProjection, hoveredMenuProjection))
                if (prepared.action.id == "player.dig.witness_v1") require(
                    frozenNatural != null && frozenNaturalFile != null &&
                        prepared.action.parameters.path("selector_id").asText() in reviewedNatural.keys) {
                    "server-invoked witness requires a prior frozen, candidate-bound reviewed natural target"
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
                val selector = if (prepared.action.id == "target.observe.loaded_v1")
                    reviewedLoadedSelector(trial.spec, prepared.action, input) else null
                val stage1DiagnosticAction = prepared.action.id in setOf("block_mutation.control_positive_v1",
                    "block_mutation.control_expose_sections_v1", "block_mutation.control_io_append_failure_v1")
                val witnessContext = if (prepared.action.id in passiveObservationDrivers || selector != null ||
                    prepared.action.id == "player.dig.witness_v1" || stage1DiagnosticAction)
                    controllerWitnessContext(bindings, epochId, channel.runId, branchId, trial.spec.id,
                        trial.request.trialId, "server", ordinal, allocatedSequence,
                        requireNotNull(joinedActor), prepared.action.id) else null
                val passive = resolvePassiveObserverAction(prepared.action, witnessContext, passiveArms)
                val step = prepared.copy(action = when {
                    selector != null -> resolveLoadedTargetAction(passive, selector,
                        requireNotNull(joinedActor), requireNotNull(witnessContext))
                    passive.id == "player.dig.witness_v1" -> resolveReviewedServerDig(passive,
                        requireNotNull(frozenNatural), requireNotNull(witnessContext))
                    stage1DiagnosticAction -> resolvePredeclaredStage1DiagnosticAction(passive,
                        requireNotNull(mutationScope), requireNotNull(joinedActor), requireNotNull(witnessContext))
                    else -> passive
                })
                val dispatchAt = Instant.now()
                val dispatchNanos = System.nanoTime()
                evidence.event("native_action_dispatched", mapOf("case_id" to trial.spec.id, "trial_id" to trial.request.trialId,
                    "step" to step.name, "source" to step.action.source, "driver" to step.action.id, "action_ordinal" to ordinal,
                    "request_sequence" to allocatedSequence, "parameters_sha256" to jsonFingerprint(step.action.parameters),
                    "monotonic_nanos" to dispatchNanos))
                try {
                    if (step.action.id == "client.input_hold") physicalHoldMayBeDown = true
                    val terminal = channel.executeWithWallDeadline(trial.spec.id, step.action.id, step.action.parameters,
                        timeout, step.action.timeoutTicks, source = step.action.source,
                        ensureAlive = if (trial.driver.clientRequired) session::ensureConnected else session::ensureSuiteServerAlive,
                        allocatedSequence = allocatedSequence, dispatchNanos = dispatchNanos)
                    if (step.action.id == "client.input_status") {
                        require(terminal.payload.path("physical_release_verified").asBoolean(false) &&
                            terminal.payload.path("release_callback").asBoolean(false) &&
                            terminal.payload.path("physical_input_down").isBoolean &&
                            !terminal.payload.path("physical_input_down").asBoolean()) {
                            "physical input key-up not independently confirmed before continuation"
                        }
                        physicalHoldMayBeDown = false
                    }
                    record(step.name, terminal, ordinal)
                    if (step.action.id == "client.menu_slot.observe_v1") {
                        val payload = terminal.payload
                        val projection = payload.path("observation_projection_sha256")
                        val generation = payload.path("actual_original_render_generation")
                        require(payload.path("schema").asText() == "bc.playtest_client_menu_slot_observation.v1" &&
                            projection.isTextual && projection.asText().matches(shaPattern) &&
                            generation.isIntegralNumber && generation.canConvertToLong() &&
                            payload.path("geometry_complete").isBoolean &&
                            payload.path("geometry_complete").asBoolean() &&
                            payload.path("actual_slot").isObject &&
                            payload.path("actual_slot").fieldNames().asSequence().toSet() == setOf(
                                "menu_slot_index", "container_slot_index", "owning_container_class",
                                "owning_container_first_menu_slot_index", "owning_container_is_player_inventory",
                                "slot_class", "x", "y", "active", "item", "item_observation_complete",
                                "result_item_unread_due_to_lazy_provider") &&
                            payload.path("actual_slot").path("menu_slot_index").isIntegralNumber &&
                            payload.path("actual_slot").path("menu_slot_index").canConvertToInt() &&
                            payload.path("actual_slot").path("menu_slot_index").asInt() ==
                                step.action.parameters.path("reviewed_slot_index").asInt(-1) &&
                            payload.path("actual_slot").path("container_slot_index").isIntegralNumber &&
                            payload.path("actual_slot").path("container_slot_index").canConvertToInt() &&
                            payload.path("actual_slot").path("container_slot_index").asInt() in 0..1024 &&
                            payload.path("actual_slot").path("owning_container_class").isTextual &&
                            payload.path("actual_slot").path("owning_container_class").asText().isNotBlank() &&
                            payload.path("actual_slot").path("owning_container_first_menu_slot_index").isIntegralNumber &&
                            payload.path("actual_slot").path("owning_container_first_menu_slot_index").canConvertToInt() &&
                            payload.path("actual_slot").path("owning_container_first_menu_slot_index").asInt() in
                                0 until payload.path("actual_slot_count").asInt(-1) &&
                            payload.path("actual_slot").path("owning_container_is_player_inventory").isBoolean &&
                            payload.path("observation_complete").isBoolean &&
                            payload.path("unsupported_reasons").isArray) {
                            "original rendered menu geometry or unsupported lazy item status absent"
                        }
                        if (firstMenuProjection == null) {
                            require(!menuPointed && payload.path("geometry_hover_complete").isBoolean &&
                                !payload.path("geometry_hover_complete").asBoolean() &&
                                payload.path("actual_slot").path("item").isNull &&
                                payload.path("actual_slot").path("item_observation_complete").isBoolean &&
                                !payload.path("actual_slot").path("item_observation_complete").asBoolean() &&
                                payload.path("actual_original_render_item_generation").isNull &&
                                payload.path("actual_original_render_item_source").asText() == "unavailable" &&
                                !payload.path("observation_complete").asBoolean() &&
                                step.action.parameters.path("prior_observation_projection_sha256").isNull)
                            firstMenuProjection = projection.asText()
                            firstMenuObservation = payload
                        } else {
                            val first = requireNotNull(firstMenuObservation)
                            val item = payload.path("actual_slot").path("item")
                            val count = item.path("count")
                            require(menuPointed && hoveredMenuProjection == null &&
                                step.action.parameters.path("prior_observation_projection_sha256").asText() ==
                                    firstMenuProjection &&
                                payload.path("observation_complete").asBoolean() &&
                                payload.path("actual_slot").path("item_observation_complete").isBoolean &&
                                payload.path("actual_slot").path("item_observation_complete").asBoolean() &&
                                payload.path("actual_slot").path("result_item_unread_due_to_lazy_provider").isBoolean &&
                                !payload.path("actual_slot").path("result_item_unread_due_to_lazy_provider").asBoolean() &&
                                payload.path("unsupported_reasons").size() == 0 &&
                                payload.path("actual_original_render_item_generation").isIntegralNumber &&
                                payload.path("actual_original_render_item_generation").asLong() == generation.asLong() &&
                                payload.path("actual_original_render_item_source").asText() ==
                                    "original_renderSlot_first_Slot.getItem_return_local5_NOT_a_second_getter" &&
                                item.isObject && item.fieldNames().asSequence().toSet() ==
                                    setOf("item_id", "count", "stack_snbt_sha256") &&
                                item.path("item_id").isTextual &&
                                item.path("item_id").asText().matches(resourcePattern) &&
                                count.isIntegralNumber && count.canConvertToInt() && count.asInt() in 0..1_000_000 &&
                                (count.asInt() == 0) == (item.path("item_id").asText() == "minecraft:air") &&
                                item.path("stack_snbt_sha256").isTextual &&
                                item.path("stack_snbt_sha256").asText().matches(shaPattern) &&
                                payload.path("geometry_hover_complete").isBoolean &&
                                payload.path("geometry_hover_complete").asBoolean() &&
                                payload.path("original_render_hover_complete").isBoolean &&
                                payload.path("original_render_hover_complete").asBoolean() &&
                                generation.asLong() > first.path("actual_original_render_generation").asLong() &&
                                payload.path("actual_hovered_menu_slot_index").isIntegralNumber &&
                                payload.path("actual_hovered_menu_slot_index").canConvertToInt() &&
                                payload.path("actual_hovered_menu_slot_index").asInt() ==
                                    step.action.parameters.path("reviewed_slot_index").asInt(-1) &&
                                listOf("actual_screen_class", "actual_menu_class", "actual_menu_type_id",
                                    "actual_container_id", "actual_state_id", "actual_glfw_window",
                                    "actual_slot_count", "actual_slot_rect", "slot_geometry_sha256", "actor_uuid",
                                    "dimension", "input_runtime_sha256_unverified_by_client", "client_session_id",
                                    "epoch_id")
                                    .all { payload.path(it) == first.path(it) } &&
                                listOf("menu_slot_index", "container_slot_index", "slot_class", "x", "y", "active",
                                    "owning_container_class", "owning_container_first_menu_slot_index",
                                    "owning_container_is_player_inventory")
                                    .all { payload.path("actual_slot").path(it) == first.path("actual_slot").path(it) }) {
                                "second actual original-render hover differed from predeclared OS pointer route"
                            }
                            hoveredMenuProjection = projection.asText()
                        }
                    }
                    if (step.action.id == "client.menu_slot.point_v1") {
                        require(firstMenuProjection != null && !menuPointed && hoveredMenuProjection == null &&
                            terminal.payload.path("physical_pointer_move_issued").isBoolean &&
                            terminal.payload.path("physical_pointer_move_issued").asBoolean() &&
                            terminal.payload.path("observation_projection_sha256").asText() == firstMenuProjection)
                        menuPointed = true
                    }
                    if (trial.spec.id == "control.client.emi-navigation" && step.action.id == "client.input_point") {
                        require(actualPoint == null && reviewedEmiProjection == null)
                        actualPoint = terminal.payload
                        actualPointSequence = terminal.requestSequence
                    }
                    if (trial.spec.id == "control.client.emi-navigation" && step.action.id == "client.emi_hover") {
                        val runtimeBinding = requireNotNull(session.suiteClientInputRuntime())
                        val runtime = readClientInputRuntime(clientMailbox.directory,
                            runtimeBinding, runtimeBinding.sha256)
                        require(reviewedEmiProjection == null && actualPointSequence != null)
                        reviewedEmiProjection = independentlyReviewedEmiHoverProjection(
                            trial.request.parameters, requireNotNull(actualPoint), terminal.payload,
                            requireNotNull(joinedActor), runtime, runtimeBinding.sha256,
                            requireNotNull(emiPin))
                        evidence.event("emi_hover_review_bound", mapOf("case_id" to trial.spec.id,
                            "trial_id" to trial.request.trialId, "point_sequence" to actualPointSequence,
                            "hover_sequence" to terminal.requestSequence,
                            "reviewed_target_sha256" to jsonFingerprint(trial.request.parameters),
                            "hover_projection_sha256" to reviewedEmiProjection))
                    }
                    if (selector != null) {
                        val context = requireNotNull(witnessContext)
                        val census = captureLoadedBlockCensus(mailbox.directory,
                            loadedCensusRequest(mailbox.runId, trial.spec.id, mailbox.token,
                                allocatedSequence, step.action.timeoutTicks, step.action),
                            terminal.payload, context, selector)
                        val attempt = loadedTargetAttempt(selector, input, census, context,
                            jsonFingerprint(suiteMapper.valueToTree(terminal)), dispatchAt,
                            dispatchNanos, System.nanoTime())
                        val attemptFile = evidence.directory.resolve("trial-${trial.spec.id}-${trial.request.trialId}-target-${allocatedSequence}.json")
                        require(attemptFile.normalize().parent == evidence.directory)
                        exclusiveJson(attemptFile, attempt)
                        evidence.event("target_loaded_attempt_bound", mapOf("case_id" to trial.spec.id,
                            "trial_id" to trial.request.trialId, "step" to step.name,
                            "request_sequence" to allocatedSequence, "attempt" to checkpointBound(attemptFile)))
                        attempt.selected?.let { selected -> when (selector.source) {
                            TargetSource.NATURAL_UNMODIFIED_BLOCK -> requireLoadedSourceStateAndScopedInterval(
                                store, input, requireNotNull(mutationScope), mailbox.directory,
                                terminal.payload.path("block_mutation_pre_action_prefix"), bindings,
                                epochId, mailbox.runId, selector.bounds.dimension, selected)
                            TargetSource.NEWLY_GENERATED_TERRAIN -> requireLoadedBirthStateAndPostbirthInterval(
                                store, input, birthSpecs, Hashes.sha256(request.base.manifestPath),
                                requireNotNull(birthScope), evidence.directory, mailbox.directory,
                                terminal.payload.path("block_birth_pre_action_prefix"), bindings,
                                epochId, mailbox.runId, selector.bounds.dimension, selected)
                            else -> error("loaded-only selector has an unsupported source")
                        } }
                        require(attempt.outcome == "loaded_only") {
                            "reviewed loaded target query rejected; raw census/attempt retained (not natural provenance)"
                        }
                        if (selector.schema == "bc.playtest_target_selector.v2") {
                            require(frozenNatural == null && reviewedNatural.keys == setOf(selector.id))
                            val acceptedAt = Instant.now()
                            val frozen = freezeReviewedNaturalTarget(store, input, selector,
                                reviewedNatural.getValue(selector.id), attempt, checkpointBound(attemptFile),
                                requireNotNull(mutationScope), mailbox.directory, terminal.payload, bindings,
                                epochId, mailbox.runId, acceptedAt)
                            val file = evidence.directory.resolve(
                                "trial-${trial.spec.id}-${trial.request.trialId}-natural-${allocatedSequence}.json")
                            require(file.normalize().parent == evidence.directory)
                            exclusiveJson(file, frozenNaturalWire(frozen))
                            val bound = checkpointBound(file)
                            frozenNatural = frozen; frozenNaturalFile = bound
                            evidence.event("natural_target_frozen", mapOf("case_id" to trial.spec.id,
                                "trial_id" to trial.request.trialId, "target" to bound,
                                "loaded_attempt" to checkpointBound(attemptFile),
                                "profile" to frozen.reviewedProfile, "selector_id" to selector.id))
                        }
                    }
                    if (step.action.id == "player.dig.witness_v1") {
                        val frozen = requireNotNull(frozenNatural)
                        val prefix = replayInitialBlockMutationPrefix(store, input, frozen.mutationScope,
                            mailbox.directory, terminal.payload.path("block_mutation_pre_action_prefix"),
                            bindings, epochId, mailbox.runId)
                        require(prefix.sha256 == frozen.mutationActionPrefixSha256 &&
                            !prefix.fallbackIncomplete && prefix.changes.none { it ==
                                ScopedBlockPosition(frozen.bound.dimension, frozen.bound.x, frozen.bound.y, frozen.bound.z) }) {
                            "watched natural target changed after freeze but before server-invoked dig"
                        }
                        val requestRow = NativeRequest("bc.playtest_action.v2", mailbox.runId,
                            trial.spec.id, mailbox.token, allocatedSequence, step.action.id,
                            step.action.timeoutTicks, step.action.parameters)
                        captureScopedPickupWire(mailbox.directory, requestRow, terminal.payload,
                            requireNotNull(witnessContext), frozen.bound)
                        evidence.event("server_invoked_dig_scoped_capture", mapOf(
                            "case_id" to trial.spec.id, "trial_id" to trial.request.trialId,
                            "target" to requireNotNull(frozenNaturalFile),
                            "request_sequence" to allocatedSequence,
                            "source" to "server_game_mode_invoked_NOT_physical_OS_attack"))
                    }
                    if (step.action.id == "scale.begin") require(terminal.payload.path("site_ledger_sha256").asText() == ledgerPrefix?.sha256) {
                        "native scale evidence did not bind the retained pre-action ledger prefix"
                    }
                } catch (error: NativeActionFailure) {
                    record(step.name, error.observation, ordinal)
                    if (selector != null) {
                        val attempt = failedLoadedTargetAttempt(selector, input, requireNotNull(witnessContext),
                            jsonFingerprint(suiteMapper.valueToTree(error.observation)), dispatchAt,
                            dispatchNanos, System.nanoTime())
                        val file = evidence.directory.resolve("trial-${trial.spec.id}-${trial.request.trialId}-target-${allocatedSequence}.json")
                        require(file.normalize().parent == evidence.directory)
                        exclusiveJson(file, attempt)
                        evidence.event("target_loaded_attempt_bound", mapOf("case_id" to trial.spec.id,
                            "trial_id" to trial.request.trialId, "step" to step.name,
                            "request_sequence" to allocatedSequence, "attempt" to checkpointBound(file)))
                    }
                    throw error
                }
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
            if (trial.spec.id == "control.persistence.vanilla-reopen") {
                // C's live-only driver deliberately remains inconclusive. Upgrade a trial only
                // after A independently decodes Store-sealed playerdata and compares all 41
                // explicit native slots + saved game mode; process/seal proof follows below.
                require(verdict.state == "inconclusive" && trial.driver.clientRequired &&
                    steps.size == 1 && steps.single().action.id == "player.snapshot" &&
                    observations.size == 1 && observations.single().driver == "player.snapshot" &&
                    observations.single().source == "server" && observations.single().kind == "completed" &&
                    joinedActor != null)
                val saved = readSealedPlayerInventory(store.verify(input), joinedActor)
                verifyLiveVanillaSavedSlots(saved, observations.single().payload, joinedActor)
                verdict = MeasurementVerdict("passed",
                    "first native read-only full41 equals independent sealed input; fresh-process closure/qualifier still required")
            }
        } catch (error: Throwable) {
            fail(error)
            if (physicalHoldMayBeDown) runCatching { session.suiteAbortHeldClientInput() }
                .exceptionOrNull()?.let(::fail)
            verdict = MeasurementVerdict("failed", error.message ?: error.javaClass.name)
        }
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
            val byteCap = if (file.path("domain").asText() == "recipes_semantics") 50_331_648 else 25_000_000
            val relative = Path.of(file.path("path").asText())
            require(!relative.isAbsolute && relative.normalize() == relative && relative.nameCount > 1 &&
                relative.startsWith("inventory") && file.path("sha256").asText().matches(Regex("[0-9a-f]{64}")) &&
                file.path("bytes").isIntegralNumber && file.path("bytes").asLong() in 1..byteCap) {
                "diagnostic inventory file metadata invalid"
            }
            val path = directory.resolve(relative).normalize()
            require(path.startsWith(directory) && !Files.isSymbolicLink(path.parent) && !Files.isSymbolicLink(path) &&
                path.toRealPath().startsWith(directory) && Files.isRegularFile(path) &&
                Files.size(path) == file.path("bytes").asLong()) { "diagnostic inventory file location/size changed" }
            val bytes = Files.newInputStream(path, java.nio.file.LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(byteCap + 1) }
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            require(bytes.size.toLong() == file.path("bytes").asLong() && hash == file.path("sha256").asText()) {
                "diagnostic inventory file bytes changed"
            }
            val roster = observed.path("measured_domain_rosters").path(file.path("domain").asText())
            require(roster.isObject && roster.path("domain").asText() == file.path("domain").asText() &&
                roster.path("sha256").asText().matches(Regex("[0-9a-f]{64}")) &&
                roster.path("bytes").isIntegralNumber && roster.path("bytes").asLong() in 1..16_777_216) {
                "diagnostic measured roster sidecar missing or unbounded"
            }
            val rosterRelative = Path.of(roster.path("path").asText())
            require(!rosterRelative.isAbsolute && rosterRelative.normalize() == rosterRelative &&
                rosterRelative.startsWith("inventory")) { "diagnostic roster escaped inventory" }
            val rosterPath = directory.resolve(rosterRelative).normalize()
            require(rosterPath.startsWith(directory) && !Files.isSymbolicLink(rosterPath.parent) &&
                !Files.isSymbolicLink(rosterPath) && rosterPath.toRealPath().startsWith(directory) &&
                Files.isRegularFile(rosterPath) && Files.size(rosterPath) == roster.path("bytes").asLong()) {
                "diagnostic measured roster location/size changed"
            }
            val rosterBytes = Files.newInputStream(rosterPath, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                .use { it.readNBytes(16_777_217) }
            val rosterHash = java.security.MessageDigest.getInstance("SHA-256").digest(rosterBytes)
                .joinToString("") { "%02x".format(it) }
            require(rosterBytes.size.toLong() == roster.path("bytes").asLong() &&
                rosterHash == roster.path("sha256").asText()) { "diagnostic measured roster bytes changed" }
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
