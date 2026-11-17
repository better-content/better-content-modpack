package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.suiteMapper
import java.nio.file.Path

/** Site facts; prelaunch authorship is proven ONLY by `readPredeclaredPhysicalWatchPlan`. */
internal data class PredeclaredWatchedBlock(val position: ScopedBlockPosition, val blockId: String) {
    init { require(blockId.matches(resourcePattern) && blockId != "minecraft:air") }
}
internal data class WatchedAncestorStep(val epoch: SessionEpoch, val sealedOutput: CheckpointRef)

/** An interval fact, NOT natural/provider coverage or permission to use a physical dig target. */
internal data class CompleteWatchedAncestorInterval(val origin: CheckpointRef,
    val preparedInput: CheckpointRef, val manifest: BoundFile,
    val predeclared: List<PredeclaredWatchedBlock>,
    val steps: List<WatchedAncestorStep>, val nativeIntervals: List<ScopedMutationPrefix>,
    val totalRawBytes: Long) {
    init { require(steps.size == nativeIntervals.size && steps.isNotEmpty() &&
        totalRawBytes == nativeIntervals.sumOf { it.byteCount.toLong() }) }
}

/**
 * Exact Store parent/restore/seal chain from player-free origin through EVERY ancestor of the
 * prepared-tool input. Requires a genuine prelaunch v2 watcher and clean STOP in EACH fresh
 * ancestor epoch; a missing old watcher CANNOT be retrofitted by equal saved terrain. Candidate
 * route completeness and final transformed-live Mixin delivery remain separate reviewed gates.
 */
internal fun replayCompleteWatchedAncestorInterval(store: CheckpointStore, origin: CheckpointRef,
    preparedInput: CheckpointRef, reviewedPlan: PredeclaredPhysicalWatchPlan,
    ancestorsInOrder: List<WatchedAncestorStep>): CompleteWatchedAncestorInterval {
    require(readPredeclaredPhysicalWatchPlan(reviewedPlan.manifest) == reviewedPlan &&
        reviewedPlan.preparedInputId == preparedInput.id) {
        "physical watched sites must be in the exact frozen reviewed manifest before origin"
    }
    val predeclared = reviewedPlan.sites
    val pristine = requirePristinePackOrigin(store, origin).receipt
    require(predeclared.size in 1..16 && predeclared.map { it.position }.distinct().size == predeclared.size &&
        ancestorsInOrder.size in 1..8 && origin != preparedInput &&
        ancestorsInOrder.map { it.epoch.id }.distinct().size == ancestorsInOrder.size &&
        ancestorsInOrder.map { it.epoch.nativeSessionId }.distinct().size == ancestorsInOrder.size &&
        ancestorsInOrder.map { it.sealedOutput }.distinct().size == ancestorsInOrder.size) {
        "watched preparation chain must be bounded, fresh and predeclared"
    }
    val sorted = predeclared.sortedWith(compareBy({ it.position.dimension }, { it.position.x },
        { it.position.y }, { it.position.z }))
    require(sorted == predeclared)
    for (site in predeclared) {
        val saved = readSealedBlockBaseline(store, origin, site.position.dimension,
            site.position.x, site.position.y, site.position.z)
        require(saved.blockId == site.blockId) { "predeclared site not present in pristine origin" }
    }
    var previous = origin
    var previousReceipt = pristine
    var totalBytes = 0L
    val observed = mutableListOf<ScopedMutationPrefix>()
    val positions = predeclared.map { it.position }
    for ((epoch, output) in ancestorsInOrder) {
        val sealed = store.verify(output)
        val restored = requireNotNull(epoch.restored) { "watched ancestor lacks actual restore receipt" }
        store.verifyRestoreReceipt(restored)
        require(epoch.inputCheckpoint == previous && restored.reference == previous &&
            restored.epochId == epoch.id && sealed.parent == previous &&
            sealed.sourceEpoch == epoch.id && sealed.closedEpoch == epoch.closure &&
            sealed.bindings == pristine.bindings && previousReceipt.bindings == sealed.bindings &&
            sealed.closedEpoch.nativeSessionId == epoch.nativeSessionId &&
            sealed.closedEpoch.saveValidated && sealed.closedEpoch.processCleanupComplete &&
            sealed.closedEpoch.survivingPids.isEmpty() && epoch.status == "passed" &&
            epoch.campaignId == pristine.bindings.campaignId &&
            epoch.lineageId == pristine.bindings.lineageId && epoch.purpose == pristine.bindings.purpose) {
            "watched ancestor is not the next fresh restored, stopped and sealed source epoch"
        }
        val evidence = Path.of(epoch.eventLog.path).toAbsolutePath().normalize().parent
        require(epoch.eventLog.path == evidence.resolve("events.jsonl").toString() &&
            sealed.closedEpoch.evidence.path == evidence.resolve("epoch-closure.json").toString())
        val events = strictCapturedLines(epoch.eventLog, maxBytes = 4_194_304,
            maxRows = 8192, maxLine = 131_072)
        val ready = events.single { it.path("type").asText() == "epoch_ready" }
        val stop = events.single { it.path("type").asText() == "block_mutation_stop_bound" }
        val scope = suiteMapper.treeToValue(ready.path("block_mutation_scope"), BoundFile::class.java)
        val baseline = suiteMapper.treeToValue(ready.path("block_mutation_baseline"), BoundFile::class.java)
        val stoppedScope = suiteMapper.treeToValue(stop.path("scope"), BoundFile::class.java)
        val status = suiteMapper.treeToValue(stop.path("status"), BoundFile::class.java)
        require(scope == stoppedScope && Path.of(baseline.path) == evidence.resolve("target-mutation-baseline.json") &&
            ready.path("input_checkpoint") ==
            suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(previous) &&
            ready.path("restore_receipt") ==
            suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(restored.restoreReceipt) &&
            Path.of(scope.path).parent == evidence &&
            Path.of(status.path).parent == evidence.resolve("native/mutations")) {
            "ancestor prelaunch watcher/restore and real native STOP were not bound by this epoch"
        }
        val baselineDoc = strictCapturedJson(capturedEvidenceBytes(baseline, 1_048_576))
        require(baselineDoc.path("schema").asText() == "bc.playtest_carry_block_baseline.v1" &&
            baselineDoc.path("manifest") ==
            suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(reviewedPlan.manifest) &&
            baselineDoc.path("case_id").asText() == reviewedPlan.caseId &&
            baselineDoc.path("checkpoint") ==
            suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(previous) &&
            baselineDoc.path("positions").isArray && baselineDoc.path("positions").size() == positions.size &&
            baselineDoc.path("positions").map { row ->
                val pos = ScopedBlockPosition(row.path("dimension").asText(),
                    row.path("x").asInt(), row.path("y").asInt(), row.path("z").asInt())
                val saved = readSealedBlockBaseline(store, previous, pos.dimension, pos.x, pos.y, pos.z)
                require(row.path("block_id").asText() == saved.blockId &&
                    row.path("properties") ==
                    suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(saved.properties) &&
                    row.path("region_sha256").asText() == saved.region.sha256)
                pos
            } == positions) { "prelaunch baseline was not the sealed reviewed watched site" }
        val scopeDoc = strictCapturedJson(capturedEvidenceBytes(scope, 1_048_576))
        val recordedSites = scopeDoc.path("positions")
        require(recordedSites.isArray && recordedSites.size() == positions.size &&
            recordedSites.map { site ->
                suiteMapper.treeToValue(site, ScopedBlockPosition::class.java)
            } == positions) { "ancestor watched a different site or silently enlarged the reviewed scope" }
        val interval = verifyStoppedBlockMutationStatus(store, previous, scope,
            evidence.resolve("native"), status, pristine.bindings, epoch.id, epoch.nativeSessionId,
            pristine = previous == origin, requireShutdownDrain = true)
        require(interval.records == 0 && interval.changes.isEmpty() && !interval.fallbackIncomplete) {
            "watched ancestral position changed or could not be covered during its complete epoch"
        }
        totalBytes += interval.byteCount.toLong()
        require(totalBytes <= 33_554_432L) { "ancestral mutation trace exceeds total 32MiB cap" }
        for (site in predeclared) {
            val prior = readSealedBlockBaseline(store, previous, site.position.dimension,
                site.position.x, site.position.y, site.position.z)
            val after = readSealedBlockBaseline(store, output, site.position.dimension,
                site.position.x, site.position.y, site.position.z)
            require(prior.blockId == site.blockId && after.blockId == site.blockId &&
                prior.properties == after.properties) {
                "saved ancestry changed the watched block even if native rows look empty"
            }
        }
        observed += interval
        previous = output
        previousReceipt = sealed
    }
    require(previous == preparedInput) { "provided ancestors omit one or more direct parent branches" }
    return CompleteWatchedAncestorInterval(origin, preparedInput, reviewedPlan.manifest,
        predeclared, ancestorsInOrder, observed, totalBytes)
}
