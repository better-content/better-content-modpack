package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.resourcePattern

/** Raw typed vanilla saved-stack facts only, from one validated capture per sealed checkpoint. */
internal data class SealedVanillaToolTransition(val beforeCheckpoint: CheckpointRef,
    val afterCheckpoint: CheckpointRef, val actorUuid: String, val toolSlot: Int,
    val outputSlot: Int?, val before: CapturedSealedVanillaInventory,
    val after: CapturedSealedVanillaInventory, val beforeTool: NbtValue,
    val afterSavedSlot: NbtValue?) {
    init {
        require(toolSlot in 0..8 && (outputSlot == null || outputSlot in 0..40) &&
            beforeCheckpoint != afterCheckpoint && before.inventory.checkpointId == beforeCheckpoint.id &&
            after.inventory.checkpointId == afterCheckpoint.id &&
            before.inventory.playerUuid == actorUuid && after.inventory.playerUuid == actorUuid &&
            beforeTool.type == 10 && (afterSavedSlot == null || afterSavedSlot.type == 10) &&
            before.typedSlot(toolSlot) === beforeTool && after.typedSlot(toolSlot) === afterSavedSlot &&
            (outputSlot != toolSlot || afterSavedSlot != null))
    }
}

/**
 * Does NOT decide a wear point/break: D's oracle still requires real B actor/hand-bound callback,
 * selected tool identity, reviewed max Damage and typed NBT. A separate acquisition join must
 * validate any outputSlot. For a genuinely broken tool the acquired drop MAY refill the same
 * reviewed slot: `afterSavedSlot` then means the saved acquired item, NOT an after-tool state.
 * The D break oracle must use B's independently replayed transient EMPTY at real destroy RETURN;
 * this adapter never invents a sealed intermediate. All other 41-slot identities remain EXACT.
 */
internal fun readSealedVanillaToolTransition(store: CheckpointStore, prior: CheckpointRef,
    sealed: CheckpointRef, actorUuid: String, reviewedToolSlot: Int,
    reviewedOutputSlot: Int? = null): SealedVanillaToolTransition {
    require(reviewedToolSlot in 0..8 &&
        (reviewedOutputSlot == null || reviewedOutputSlot in 0..40))
    val input = store.verify(prior)
    val output = store.verify(sealed)
    require(output.parent == prior && output.bindings == input.bindings &&
        output.closedEpoch.saveValidated && output.closedEpoch.processCleanupComplete &&
        output.closedEpoch.survivingPids.isEmpty() &&
        output.sourceEpoch == output.closedEpoch.epochId) {
        "tool transition must come from one Store-verified closed/sealed descendant"
    }
    val before = readSealedVanillaInventoryWithTags(input, actorUuid)
    val after = readSealedVanillaInventoryWithTags(output, actorUuid)
    val tool = before.inventory.vanillaSlot(reviewedToolSlot) as? SealedVanillaSlot.Nonempty
        ?: error("reviewed vanilla hotbar tool absent from independently captured sealed input")
    require(tool.stack.itemId.matches(resourcePattern) && tool.stack.itemId.startsWith("minecraft:") &&
        tool.stack.count == 1 && before.typedSlot(reviewedToolSlot) != null)
    for (slot in 0..40) if (slot != reviewedToolSlot && slot != reviewedOutputSlot) {
        require(before.inventory.vanillaSlot(slot) == after.inventory.vanillaSlot(slot)) {
            "unreviewed vanilla slot drifted during tool break/wear: $slot"
        }
    }
    return SealedVanillaToolTransition(prior, sealed, actorUuid, reviewedToolSlot,
        reviewedOutputSlot, before, after, requireNotNull(before.typedSlot(reviewedToolSlot)),
        after.typedSlot(reviewedToolSlot))
}
