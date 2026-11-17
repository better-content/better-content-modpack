package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.util.UUID

private fun exclusiveAtomicScopeFile(file: Path, data: Any) {
    val parent = file.parent
    require(Files.isDirectory(parent, NOFOLLOW_LINKS) && !Files.exists(file, NOFOLLOW_LINKS))
    val staged = parent.resolve(".${file.fileName}.${UUID.randomUUID()}.stage")
    try {
        Files.writeString(staged, suiteMapper.writeValueAsString(data) + "\n", CREATE_NEW)
        require(Files.size(staged) in 1..1_048_576 && !Files.isSymbolicLink(staged))
        Files.move(staged, file, ATOMIC_MOVE)
        require(Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file))
    } finally { Files.deleteIfExists(staged) }
}

internal data class ScopedBlockPosition(val dimension: String, val x: Int, val y: Int, val z: Int)
internal data class PristineOriginWitness(val receipt: CheckpointReceipt, val rawEvents: BoundFile,
    val rawInventoryRequest: BoundFile, val rawInventoryObservation: BoundFile)

/** Initial pack world, not a native engine genesis assertion: reject copied/player/fixture/command epochs. */
internal fun requirePristinePackOrigin(store: CheckpointStore, origin: CheckpointRef): PristineOriginWitness {
    val receipt = store.verify(origin)
    require(origin.id == "origin" && receipt.id == "origin" && receipt.parent == null &&
        receipt.files.none { file -> file.root == "server" &&
            listOf("world/playerdata/", "world/advancements/", "world/stats/").any(file.path::startsWith) }) {
        "pristine origin cannot contain prior player history"
    }
    val closurePath = Path.of(receipt.closedEpoch.evidence.path).toAbsolutePath().normalize()
    val evidenceDir = closurePath.parent
    val closure = suiteMapper.readTree(closurePath.toFile())
    val events = evidenceDir.resolve("events.jsonl")
    val requests = evidenceDir.resolve("native/requests.jsonl")
    val observations = evidenceDir.resolve("native/observations.jsonl")
    require(closurePath.fileName.toString() == "epoch-closure.json" &&
        listOf(events, requests, observations).all { Files.isRegularFile(it, NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(it) && Files.size(it) in 1..16_777_216 } &&
        closure.path("events").path("path").asText() == events.toString() &&
        closure.path("events").path("sha256").asText() == Hashes.sha256(events) &&
        closure.path("epoch_id").asText() == receipt.sourceEpoch &&
        closure.path("native_session_id").asText() == receipt.closedEpoch.nativeSessionId) {
        "initial-world closure lacks bounded exact original source evidence"
    }
    fun lines(file: Path): List<JsonNode> = Files.readAllLines(file).also { rows ->
        require(rows.isNotEmpty() && rows.size <= 4096 && rows.all { it.isNotBlank() && it.length <= 1_048_576 })
    }.map(suiteMapper::readTree)
    val eventRows = lines(events)
    val prepared = eventRows.filter { it.path("type").asText() == "prepared" }.single()
    val requestRows = lines(requests)
    val observationRows = lines(observations)
    val bound = eventRows.filter { it.path("type").asText() == "origin_source_streams_bound" }.single()
    require(bound.path("native_requests").path("path").asText() == requests.toString() &&
        bound.path("native_requests").path("sha256").asText() == Hashes.sha256(requests) &&
        bound.path("native_observations").path("path").asText() == observations.toString() &&
        bound.path("native_observations").path("sha256").asText() == Hashes.sha256(observations) &&
        eventRows.count { it.path("type").asText() == "session_started" } == 1 &&
        eventRows.count { it.path("type").asText() == "server_started" } == 1 &&
        prepared.path("fresh_world").isBoolean && prepared.path("fresh_world").asBoolean() &&
        prepared.path("restored_epoch").isBoolean && !prepared.path("restored_epoch").asBoolean() &&
        eventRows.none { it.path("type").asText() in setOf("client_started", "client_joined", "player_joined",
            "first_join", "fixture_begin", "fixture_started", "native_action_dispatched", "failure") } &&
        requestRows.size == 1 && requestRows.single().path("driver").asText() == "inventory.discover" &&
        requestRows.single().path("case_id").asText() == "campaign.inventory" &&
        requestRows.single().path("sequence").asLong(-1) == 1L &&
        requestRows.single().path("run_id").asText() == receipt.closedEpoch.nativeSessionId &&
        observationRows.isNotEmpty() && observationRows.size <= 16 && observationRows.all {
            it.path("request_sequence").asLong(-1) == 1L &&
                it.path("run_id").asText() == receipt.closedEpoch.nativeSessionId &&
                it.path("driver").asText() == "inventory.discover" &&
                it.path("case_id").asText() == "campaign.inventory"
        } && observationRows.filter { it.path("kind").asText() == "started" }.size == 1 &&
        observationRows.filter { it.path("request_sequence").asLong(-1) == 1L &&
            it.path("kind").asText() == "completed" }.single().path("payload").let { payload ->
            payload.path("player_count").isIntegralNumber && payload.path("player_count").asInt(-1) == 0 &&
                payload.path("player_uuids").isArray && payload.path("player_uuids").isEmpty
        }) { "pristine pack origin had unaccounted actor, action or copied world" }
    require(Hashes.sha256(events) == closure.path("events").path("sha256").asText() &&
        Hashes.sha256(requests) == bound.path("native_requests").path("sha256").asText() &&
        Hashes.sha256(observations) == bound.path("native_observations").path("sha256").asText())
    return PristineOriginWitness(receipt, BoundFile(events.toString(), Hashes.sha256(events)),
        BoundFile(requests.toString(), Hashes.sha256(requests)),
        BoundFile(observations.toString(), Hashes.sha256(observations)))
}

/** Source-only controller scope. It is not a natural verdict until B's complete interval is replayed. */
internal fun createInitialBlockMutationScope(store: CheckpointStore, input: CheckpointRef,
    trials: List<EpochTrial>, bindings: CheckpointBindings, epochId: String,
    nativeSessionId: String, evidence: Path): BoundFile? {
    val selectors = trials.flatMap { trial -> trial.spec.targetBindings.map { raw ->
        suiteMapper.treeToValue(raw, ReviewedTargetSelector::class.java)
    } }.filter { it.source == TargetSource.NATURAL_UNMODIFIED_BLOCK }
    if (selectors.isEmpty()) return null
    val origin = requirePristinePackOrigin(store, input)
    require(selectors.all { it.inputCheckpointId == "origin" &&
        it.catalogueSha256.matches(shaPattern) && it.capabilityId == "target.observe.loaded_v1" } &&
        origin.receipt.bindings == bindings && input.id == "origin" &&
        evidence.fileName.toString() == "evidence" && Files.isDirectory(evidence, NOFOLLOW_LINKS)) {
        "stage1 mutation scope requires exact reviewed pristine source before server launch"
    }
    // Small Stage1 subset: remaining terrain/large rosters wait for generation-baseline adapters.
    val positions = selectors.flatMap { selector ->
        val bounds = selector.bounds
        val volume = listOf(bounds.maxX.toLong() - bounds.minX + 1,
            bounds.maxY.toLong() - bounds.minY + 1, bounds.maxZ.toLong() - bounds.minZ + 1)
            .fold(1L) { count, size ->
                require(size in 1..64 && count <= 64 / size) {
                    "reviewed initial block mutation scope exceeds narrow 64-position cap"
                }
                count * size
            }
        require(volume in 1..64)
        (bounds.minX..bounds.maxX).flatMap { x -> (bounds.minY..bounds.maxY).flatMap { y ->
            (bounds.minZ..bounds.maxZ).map { z -> ScopedBlockPosition(bounds.dimension, x, y, z) }
        } }
    }.distinct().sortedWith(compareBy(ScopedBlockPosition::dimension, ScopedBlockPosition::x,
        ScopedBlockPosition::y, ScopedBlockPosition::z))
    // Intentional controller subset of B's 1..4096 wire cap: saved MCA decoding is
    // bounded to <=64 reviewed positions, not a claim of full-chunk mutation coverage.
    require(positions.isNotEmpty() && positions.size <= 64)
    val baselines = positions.map { position ->
        val saved = readSealedBlockBaseline(store, input, position.dimension, position.x, position.y, position.z)
        mapOf("dimension" to position.dimension, "x" to position.x, "y" to position.y, "z" to position.z,
            "block_id" to saved.blockId, "properties" to saved.properties,
            "region_sha256" to saved.region.sha256)
    }
    val baselineFile = evidence.resolve("target-mutation-baseline.json")
    exclusiveAtomicScopeFile(baselineFile, mapOf(
        "schema" to "bc.playtest_initial_block_baseline.v1", "checkpoint" to input,
        "pristine_events" to origin.rawEvents, "pristine_inventory_request" to origin.rawInventoryRequest,
        "pristine_inventory_observation" to origin.rawInventoryObservation, "positions" to baselines))
    val baseline = BoundFile(baselineFile.toString(), Hashes.sha256(baselineFile))
    val scopeId = "scope-${UUID.randomUUID()}"
    val file = evidence.resolve("target-mutation-scope.json")
    val scope = linkedMapOf<String, Any>(
        "schema" to "bc.playtest_block_mutation_scope.v1", "scope_id" to scopeId,
        "campaign_id" to bindings.campaignId, "lineage_id" to bindings.lineageId,
        "epoch_id" to epochId, "native_session_id" to nativeSessionId,
        "purpose" to bindings.purpose, "source" to "controller_prelaunch",
        "source_checkpoint_id" to input.id, "source_checkpoint_sha256" to input.receipt.sha256,
        "positions" to positions, "max_events" to 100_000, "max_bytes" to 16_777_216)
    exclusiveAtomicScopeFile(file, scope)
    require(Files.size(file) in 1..1_048_576 && !Files.isSymbolicLink(file))
    val bound = BoundFile(file.toString(), Hashes.sha256(file))
    require(bound.sha256.matches(shaPattern) && Hashes.sha256(baselineFile) == baseline.sha256)
    return bound
}

/** Source-only prelaunch carrier for exactly the reviewed physical two-site manifest plan.
 * Never accepts a post-hoc saved-equal site; the caller must additionally check the branch
 * is on the direct origin→prepared-tool ancestry and replay every predecessor interval. */
internal fun createPrelaunchDiagnosticSetupScope(store: CheckpointStore,
    reviewed: PredeclaredDiagnosticMutationSetup, bindings: CheckpointBindings,
    epochId: String, nativeSessionId: String, evidence: Path): BoundFile {
    require(loadPredeclaredDiagnosticMutationSetup(store, reviewed.manifest, reviewed.origin,
        reviewed.caseId) == reviewed && store.verify(reviewed.origin).bindings == bindings &&
        bindings.purpose == ExecutionPurpose.DIAGNOSTIC.wire &&
        evidence.fileName.toString() == "evidence" && Files.isDirectory(evidence, NOFOLLOW_LINKS))
    val site = reviewed.position
    val saved = readSealedBlockBaseline(store, reviewed.origin,
        site.dimension, site.x, site.y, site.z)
    val baselineFile = evidence.resolve("target-mutation-baseline.json")
    exclusiveAtomicScopeFile(baselineFile, mapOf(
        "schema" to "bc.playtest_diagnostic_setup_baseline.v2", "source" to "authored_fixture_setup_not_natural",
        "manifest" to reviewed.manifest, "prepared_onboarding_parent" to reviewed.origin,
        "setup_checkpoint_id" to reviewed.setupCheckpointId,
        "case_id" to reviewed.caseId, "actor_uuid" to reviewed.actorUuid,
        "position" to site, "origin_block_id" to reviewed.originState,
        "fixture_block_id" to reviewed.fixtureState, "region_sha256" to saved.region.sha256))
    val file = evidence.resolve("target-mutation-scope.json")
    exclusiveAtomicScopeFile(file, linkedMapOf<String, Any>(
        "schema" to "bc.playtest_block_mutation_scope.v1", "scope_id" to "scope-${UUID.randomUUID()}",
        "campaign_id" to bindings.campaignId, "lineage_id" to bindings.lineageId,
        "epoch_id" to epochId, "native_session_id" to nativeSessionId,
        "purpose" to bindings.purpose, "source" to "controller_prelaunch",
        "source_checkpoint_id" to reviewed.origin.id,
        "source_checkpoint_sha256" to reviewed.origin.receipt.sha256,
        "positions" to listOf(site), "max_events" to 100_000, "max_bytes" to 16_777_216))
    return BoundFile(file.toString(), Hashes.sha256(file))
}

internal fun createPrelaunchDiagnosticControlScope(store: CheckpointStore,
    reviewed: PredeclaredDiagnosticMutationControl, bindings: CheckpointBindings,
    epochId: String, nativeSessionId: String, evidence: Path): BoundFile {
    val plan = loadPredeclaredDiagnosticMutationControl(store, reviewed.manifest, reviewed.setup,
        reviewed.caseId, reviewed.controlId)
    require(plan == reviewed && bindings.purpose == ExecutionPurpose.DIAGNOSTIC.wire &&
        store.verify(reviewed.setup).bindings == bindings &&
        epochId != store.verify(reviewed.setup).sourceEpoch &&
        evidence.fileName.toString() == "evidence" && Files.isDirectory(evidence, NOFOLLOW_LINKS)) {
        "real diagnostic control requires its own fresh restored epoch from a sealed authored fixture"
    }
    val site = reviewed.position
    val saved = readSealedBlockBaseline(store, reviewed.setup, site.dimension, site.x, site.y, site.z)
    val baselineFile = evidence.resolve("target-mutation-baseline.json")
    exclusiveAtomicScopeFile(baselineFile, mapOf(
        "schema" to "bc.playtest_diagnostic_fixture_baseline.v1", "source" to "authored_fixture_not_natural",
        "manifest" to reviewed.manifest, "setup_checkpoint" to reviewed.setup,
        "actor_uuid" to reviewed.actorUuid, "case_id" to reviewed.caseId,
        "control_id" to reviewed.controlId, "position" to site,
        "block_id" to saved.blockId, "properties" to saved.properties,
        "region_sha256" to saved.region.sha256, "reviewed_transitions" to reviewed.expected))
    val file = evidence.resolve("target-mutation-scope.json")
    exclusiveAtomicScopeFile(file, linkedMapOf<String, Any>(
        "schema" to "bc.playtest_block_mutation_scope.v1", "scope_id" to "scope-${UUID.randomUUID()}",
        "campaign_id" to bindings.campaignId, "lineage_id" to bindings.lineageId,
        "epoch_id" to epochId, "native_session_id" to nativeSessionId,
        "purpose" to bindings.purpose, "source" to "controller_prelaunch",
        "source_checkpoint_id" to reviewed.setup.id,
        "source_checkpoint_sha256" to reviewed.setup.receipt.sha256,
        "positions" to listOf(site), "max_events" to 100_000, "max_bytes" to 16_777_216))
    require(Hashes.sha256(baselineFile).matches(shaPattern))
    return BoundFile(file.toString(), Hashes.sha256(file))
}

internal fun createPrelaunchDiagnosticNegativeScope(store: CheckpointStore,
    reviewed: PredeclaredDiagnosticMutationNegative, bindings: CheckpointBindings,
    epochId: String, nativeSessionId: String, evidence: Path): BoundFile {
    require(loadPredeclaredDiagnosticMutationNegative(store, reviewed.manifest, reviewed.setup,
        reviewed.caseId, reviewed.actionId) == reviewed &&
        bindings.purpose == ExecutionPurpose.DIAGNOSTIC.wire &&
        store.verify(reviewed.setup).bindings == bindings &&
        epochId != store.verify(reviewed.setup).sourceEpoch &&
        evidence.fileName.toString() == "evidence" && Files.isDirectory(evidence, NOFOLLOW_LINKS))
    val site = reviewed.position
    val saved = readSealedBlockBaseline(store, reviewed.setup, site.dimension, site.x, site.y, site.z)
    require(saved.blockId == reviewed.before && saved.properties.isEmpty())
    val baseline = evidence.resolve("target-mutation-baseline.json")
    exclusiveAtomicScopeFile(baseline, mapOf(
        "schema" to "bc.playtest_diagnostic_negative_fixture_baseline.v1",
        "source" to "authored_fixture_not_natural", "manifest" to reviewed.manifest,
        "setup_checkpoint" to reviewed.setup, "actor_uuid" to reviewed.actorUuid,
        "case_id" to reviewed.caseId, "selector_id" to reviewed.selectorId,
        "action_id" to reviewed.actionId, "position" to site,
        "block_id" to saved.blockId, "properties" to saved.properties,
        "region_sha256" to saved.region.sha256, "expected_after_state" to reviewed.after,
        "expected_failure_reason" to reviewed.failureReason))
    val file = evidence.resolve("target-mutation-scope.json")
    exclusiveAtomicScopeFile(file, linkedMapOf<String, Any>(
        "schema" to "bc.playtest_block_mutation_scope.v1", "scope_id" to "scope-${UUID.randomUUID()}",
        "campaign_id" to bindings.campaignId, "lineage_id" to bindings.lineageId,
        "epoch_id" to epochId, "native_session_id" to nativeSessionId,
        "purpose" to bindings.purpose, "source" to "controller_prelaunch",
        "source_checkpoint_id" to reviewed.setup.id,
        "source_checkpoint_sha256" to reviewed.setup.receipt.sha256,
        "positions" to listOf(site), "max_events" to 100_000, "max_bytes" to 16_777_216))
    require(Hashes.sha256(baseline).matches(shaPattern))
    return BoundFile(file.toString(), Hashes.sha256(file))
}

internal fun createCarryBlockMutationScope(store: CheckpointStore, input: CheckpointRef,
    reviewed: PredeclaredPhysicalWatchPlan, bindings: CheckpointBindings,
    epochId: String, nativeSessionId: String, evidence: Path): BoundFile {
    require(readPredeclaredPhysicalWatchPlan(reviewed.manifest) == reviewed &&
        reviewed.sites.size == 2 && reviewed.sites.map { it.position }.distinct().size == 2 &&
        evidence.fileName.toString() == "evidence" && Files.isDirectory(evidence, NOFOLLOW_LINKS))
    val source = store.verify(input)
    require(source.bindings == bindings && bindings.purpose == ExecutionPurpose.QUALIFICATION.wire)
    if (input.id == "origin") requirePristinePackOrigin(store, input)
    val positions = reviewed.sites.map { it.position }
    val baseline = reviewed.sites.map { site ->
        val saved = readSealedBlockBaseline(store, input, site.position.dimension,
            site.position.x, site.position.y, site.position.z)
        require(saved.blockId == site.blockId) { "reviewed watched block absent at prelaunch ancestor" }
        mapOf("dimension" to site.position.dimension, "x" to site.position.x,
            "y" to site.position.y, "z" to site.position.z,
            "block_id" to saved.blockId, "properties" to saved.properties,
            "region_sha256" to saved.region.sha256)
    }
    val baselineFile = evidence.resolve("target-mutation-baseline.json")
    exclusiveAtomicScopeFile(baselineFile, mapOf(
        "schema" to "bc.playtest_carry_block_baseline.v1", "manifest" to reviewed.manifest,
        "case_id" to reviewed.caseId, "checkpoint" to input, "positions" to baseline))
    val scopeFile = evidence.resolve("target-mutation-scope.json")
    exclusiveAtomicScopeFile(scopeFile, linkedMapOf<String, Any>(
        "schema" to "bc.playtest_block_mutation_scope.v1",
        "scope_id" to "scope-${UUID.randomUUID()}", "campaign_id" to bindings.campaignId,
        "lineage_id" to bindings.lineageId, "epoch_id" to epochId,
        "native_session_id" to nativeSessionId, "purpose" to bindings.purpose,
        "source" to "controller_prelaunch", "source_checkpoint_id" to input.id,
        "source_checkpoint_sha256" to input.receipt.sha256,
        "positions" to positions, "max_events" to 100_000,
        "max_bytes" to 16_777_216))
    return BoundFile(scopeFile.toString(), Hashes.sha256(scopeFile))
}
