package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.NativeRequest
import com.bettercontent.tests.suite.idPattern
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.shaPattern
import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** This REVIEWED slot must be supplied from authoring BEFORE action dispatch, never selected from result bytes. */
internal data class ReviewedScopedOutputSlot(val schema: String, val selectorId: String,
    val catalogueSha256: String, val inputCheckpoint: CheckpointRef, val slot: Int, val itemId: String) {
    init { require(schema == "bc.playtest_reviewed_scoped_output_slot.v2" && selectorId.matches(idPattern) &&
        catalogueSha256.matches(shaPattern) && slot in 0..40 && itemId.matches(resourcePattern)) }
}
/** Controller source-only narrow join: exact one vanilla slot DELTA, not natural/external-provider proof. */
internal data class ScopedSavedPlayerJoin(val schema: String, val inputCheckpoint: CheckpointRef,
    val sealedCheckpoint: CheckpointRef, val nativeTrace: BoundFile, val playerData: BoundFile,
    val actorUuid: String, val selectedSlot: Int, val savedStack: SealedInventoryStack,
    val preexistingStack: SealedInventoryStack?, val newlyAcquiredCount: Long,
    val scopedEntityIds: List<String>, val savedSourceAbsence: SavedSourceEntityAbsence) {
    init { require(schema == "bc.playtest_scoped_saved_player_join.v2" &&
        scopedEntityIds.isNotEmpty() && scopedEntityIds.distinct().size == scopedEntityIds.size &&
        selectedSlot == savedStack.slot && inputCheckpoint != sealedCheckpoint &&
        newlyAcquiredCount > 0 && savedStack.count.toLong() - (preexistingStack?.count ?: 0) == newlyAcquiredCount &&
        savedSourceAbsence.checkpointId == sealedCheckpoint.id &&
        savedSourceAbsence.absentUuids == scopedEntityIds.sorted()) }
}

/**
 * Re-read Store-verified input/seal, actual native v2 bytes, and all 41 vanilla saved slots.
 * Callers MUST first verify this request/terminal against the epoch's raw request/observation stream and
 * the selected-slot catalogue SHA. Disk source-entity absence/mutation provenance remain separate gates.
 */
internal fun replayScopedSavedPlayerJoin(store: CheckpointStore, input: CheckpointRef,
    sealed: CheckpointRef, reviewed: ReviewedScopedOutputSlot, mailbox: Path,
    request: NativeRequest, terminal: JsonNode, context: WitnessContext, target: BoundTarget): ScopedSavedPlayerJoin {
    require(reviewed.selectorId == target.selectorId && reviewed.inputCheckpoint == input &&
        target.inputCheckpoint == input && target.context.sameAncestry(context) &&
        request.driver == "player.dig.witness_v1") { "saved output selection was not reviewed before runtime action" }
    val inputReceipt = store.verify(input)
    val receipt = store.verify(sealed)
    val closure = receipt.closedEpoch
    require(receipt.parent == input && receipt.sourceEpoch == context.epochId &&
        closure.epochId == context.epochId && closure.nativeSessionId == context.nativeSessionId &&
        closure.saveValidated && closure.processCleanupComplete && closure.survivingPids.isEmpty() &&
        inputReceipt.bindings == receipt.bindings &&
        receipt.bindings.campaignId == context.campaignId && receipt.bindings.lineageId == context.lineageId &&
        receipt.bindings.purpose == context.purpose) { "post-seal source epoch/candidate/lineage/closure drifted" }
    val evidence = Path.of(closure.evidence.path).toAbsolutePath().normalize()
    val nativeDirectory = mailbox.toAbsolutePath().normalize()
    require(evidence.fileName.toString() == "epoch-closure.json" &&
        nativeDirectory == evidence.parent.resolve("native") &&
        Files.isDirectory(nativeDirectory, NOFOLLOW_LINKS) && !Files.isSymbolicLink(nativeDirectory)) {
        "acquisition source escaped the sealed epoch's native mailbox"
    }
    val trace = captureScopedPickupWire(nativeDirectory, request, terminal, context, target)
    val sourceFile = Path.of(trace.file.path)
    require(sourceFile.parent == nativeDirectory.resolve("acquisition") &&
        Files.isRegularFile(sourceFile, NOFOLLOW_LINKS) && !Files.isSymbolicLink(sourceFile) &&
        Files.size(sourceFile) == trace.bytes && Hashes.sha256(sourceFile) == trace.file.sha256) {
        "acquisition trace changed after exact sealed epoch replay"
    }
    val before = readSealedPlayerInventory(inputReceipt, context.actorUuid)
    val after = readSealedPlayerInventory(receipt, context.actorUuid)
    val preexisting = (before.vanillaSlot(reviewed.slot) as? SealedVanillaSlot.Nonempty)?.stack
    val saved = (after.vanillaSlot(reviewed.slot) as? SealedVanillaSlot.Nonempty)?.stack
        ?: throw IllegalArgumentException("selected output absent from independently decoded saved vanilla inventory")
    val live = requireNotNull(trace.finalVanillaSlots[reviewed.slot]) {
        "selected output not in the last observed actor vanilla inventory"
    }
    val sourcedStacks = trace.sources.flatMap { it.acquiredStacks }
    val acquired = sourcedStacks.fold(0L) { count, stack -> Math.addExact(count, stack.count) }
    requireNewlyAcquiredSavedSlotDelta(preexisting, saved, live, sourcedStacks, reviewed.itemId, acquired)
    // Both raw snapshots span main+armor+offhand. Every pre-action slot equals the sealed input,
    // every post-action slot equals saved output, and only this reviewed slot may acquire Count.
    (0..40).forEach { slot ->
        val priorWire = trace.initialVanillaSlots[slot]
        val finalWire = trace.finalVanillaSlots[slot]
        val priorSaved = before.vanillaSlot(slot)
        val finalSaved = after.vanillaSlot(slot)
        fun matches(savedSlot: SealedVanillaSlot, wire: NativeWireStack?): Boolean =
            (wire == null && savedSlot is SealedVanillaSlot.Empty) ||
                (wire != null && savedSlot is SealedVanillaSlot.Nonempty &&
                    savedSlot.stack.count.toLong() == wire.count && savedSlot.stack.itemId == wire.itemId &&
                    savedSlot.stack.canonicalStackSha256 == wire.canonicalSha256 &&
                    savedSlot.stack.contentSha256 == wire.contentSha256)
        require(matches(priorSaved, priorWire) && matches(finalSaved, finalWire) &&
            (slot == reviewed.slot || priorSaved == finalSaved && priorWire == finalWire)) {
            "independent sealed vanilla main/armor/offhand slot differs before/after scoped acquisition: $slot"
        }
    }
    require(trace.initialVanillaSlots.keys.all { it in 0..40 } &&
        trace.finalVanillaSlots.keys.all { it in 0..40 }) { "unmapped raw player inventory provider slot" }
    val scopedIds = trace.sources.map { it.entityUuid }
    val absence = verifySealedSourceEntitiesAbsent(receipt, scopedIds)
    return ScopedSavedPlayerJoin("bc.playtest_scoped_saved_player_join.v2", input, sealed,
        trace.file, after.playerData, context.actorUuid, reviewed.slot, saved, preexisting,
        acquired, scopedIds, absence)
}

/** Count delta excludes preexisting same-content items; full NBT and all source content stay distinct. */
internal fun requireNewlyAcquiredSavedSlotDelta(before: SealedInventoryStack?, after: SealedInventoryStack,
    nativeAfter: NativeWireStack, sourced: List<NativeWireStack>, reviewedItemId: String,
    newlyAcquired: Long) {
    require(newlyAcquired > 0 && sourced.isNotEmpty() && after.itemId == reviewedItemId &&
        nativeAfter.itemId == reviewedItemId && after.count.toLong() == nativeAfter.count &&
        after.count.toLong() - (before?.count ?: 0) == newlyAcquired &&
        (before == null || (before.itemId == reviewedItemId && before.contentSha256 == after.contentSha256 &&
            before.count < after.count && before.canonicalStackSha256 != after.canonicalStackSha256)) &&
        after.canonicalStackSha256 == nativeAfter.canonicalSha256 &&
        after.contentSha256 == nativeAfter.contentSha256 &&
        after.hashAlgorithm == STACK_NBT_HASH_ALGORITHM && after.contentAlgorithm == STACK_CONTENT_HASH_ALGORITHM &&
        sourced.all { it.sameContent(nativeAfter) } &&
        sourced.fold(0L) { count, stack -> Math.addExact(count, stack.count) } == newlyAcquired) {
        "newly acquired Count/content does not equal selected saved VANILLA slot delta over preexisting identity"
    }
}
