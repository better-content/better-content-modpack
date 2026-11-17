package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.shaPattern
import java.util.UUID

/** A replay result only. Constructing this data shape is NEVER itself proof of a native callback. */
internal data class ReplayedPhysicalToolBreak(val actorUuid: String, val hand: String,
    val vanillaHotbarSlot: Int, val preToolItemId: String, val preToolCanonicalSha256: String,
    val actualCallbackCount: Int, val causeEnterCount: Int, val causeReturnCount: Int,
    val callbackSameOriginalItemObject: Boolean, val destroyReturnEmpty: Boolean,
    val captureComplete: Boolean, val trace: BoundFile, val refillSourceEntityUuid: String?) {
    init {
        require(UUID.fromString(actorUuid).toString() == actorUuid && hand == "MAIN_HAND" &&
            vanillaHotbarSlot in 0..8 && preToolItemId.matches(resourcePattern) &&
            preToolCanonicalSha256.matches(shaPattern) && actualCallbackCount in 0..4 &&
            causeEnterCount in 0..4 && causeReturnCount in 0..4 &&
            (refillSourceEntityUuid == null ||
                UUID.fromString(refillSourceEntityUuid).toString() == refillSourceEntityUuid))
    }
}

/** A same-trace source pickup replay joined to actual same-captured Store-decoded saved FINAL slot. */
internal data class ReplayedPhysicalSelectedPickup(val actorUuid: String,
    val vanillaHotbarSlot: Int, val sourceEntityUuid: String, val outputSlot: Int,
    val acquiredItemId: String, val acquiredCount: Int, val acquiredCanonicalSha256: String,
    val acquiredContentSha256: String, val beforeSelectedEmpty: Boolean,
    val afterSelectedCanonicalSha256: String, val other40Exact: Boolean,
    val sourceTerminalAbsent: Boolean, val savedFinalStack: SealedInventoryStack,
    val trace: BoundFile) {
    init {
        require(UUID.fromString(actorUuid).toString() == actorUuid &&
            UUID.fromString(sourceEntityUuid).toString() == sourceEntityUuid &&
            vanillaHotbarSlot in 0..8 && outputSlot == vanillaHotbarSlot &&
            acquiredItemId.matches(resourcePattern) && acquiredCount in 1..64 &&
            acquiredCanonicalSha256.matches(shaPattern) &&
            acquiredContentSha256.matches(shaPattern) &&
            afterSelectedCanonicalSha256.matches(shaPattern) && savedFinalStack.slot == outputSlot)
    }
}
