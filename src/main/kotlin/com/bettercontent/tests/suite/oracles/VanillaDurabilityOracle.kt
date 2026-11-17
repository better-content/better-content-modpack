package com.bettercontent.tests.suite.oracles

import com.bettercontent.tests.suite.checkpoints.NbtList
import com.bettercontent.tests.suite.checkpoints.NbtValue
import com.bettercontent.tests.suite.checkpoints.CapturedSealedVanillaInventory
import com.bettercontent.tests.suite.checkpoints.SealedVanillaSlot
import com.bettercontent.tests.suite.checkpoints.ReplayedPhysicalToolBreak
import com.bettercontent.tests.suite.checkpoints.ReplayedPhysicalSelectedPickup
import com.bettercontent.tests.suite.checkpoints.STACK_CONTENT_HASH_ALGORITHM
import com.bettercontent.tests.suite.checkpoints.STACK_NBT_HASH_ALGORITHM
import java.util.UUID

/** Narrow vanilla 1.20.1 non-enchanted, non-capability-owned durability only. This compares
 * EXISTING typed NBT; it does not parse SNBT, infer damage from an opaque stack hash, or modify a stack. */
internal data class VanillaDurabilityTarget(val itemId: String, val reviewedMaxDamage: Int,
    val reviewedUnenchantedNoModifiers: Boolean, val expectedBeforeDamage: Int,
    val expectedAfterDamage: Int?, val expectBreak: Boolean)
internal data class DurabilityVerdict(val status: String, val reason: String)

internal object VanillaDurabilityOracle {
    private fun compound(value: NbtValue?): Map<String, NbtValue>? =
        if (value?.type == 10) (value.payload as? Map<*, *>)?.takeIf {
            it.keys.all { key -> key is String } && it.values.all { entry -> entry is NbtValue }
        }?.entries?.associate { (key, tag) -> key as String to tag as NbtValue } else null
    private fun same(left: NbtValue, right: NbtValue): Boolean {
        if (left.type != right.type) return false
        return when (left.type) {
            7 -> (left.payload as? ByteArray)?.contentEquals(right.payload as? ByteArray ?: return false) == true
            11 -> (left.payload as? IntArray)?.contentEquals(right.payload as? IntArray ?: return false) == true
            12 -> (left.payload as? LongArray)?.contentEquals(right.payload as? LongArray ?: return false) == true
            9 -> {
                val a = left.payload as? NbtList ?: return false
                val b = right.payload as? NbtList ?: return false
                a.type == b.type && a.values.size == b.values.size && a.values.zip(b.values).all { (x, y) -> same(x, y) }
            }
            10 -> {
                val a = compound(left) ?: return false; val b = compound(right) ?: return false
                a.keys == b.keys && a.all { (key, value) -> same(value, b.getValue(key)) }
            }
            in 1..6, 8 -> left.payload == right.payload // float/double raw IEEE754 bits preserved by NbtReader
            else -> false
        }
    }
    fun evaluate(target: VanillaDurabilityTarget, before: NbtValue?, after: NbtValue?,
        observedBreakCallback: Boolean?): DurabilityVerdict {
        fun inconclusive(reason: String) = DurabilityVerdict("inconclusive", reason)
        fun failed(reason: String) = DurabilityVerdict("failed", reason)
        if (!Regex("minecraft:[a-z0-9_./-]+").matches(target.itemId) ||
            target.reviewedMaxDamage !in 1..1_000_000 || !target.reviewedUnenchantedNoModifiers ||
            target.expectedBeforeDamage !in 0 until target.reviewedMaxDamage ||
            (target.expectBreak && target.expectedAfterDamage != null) ||
            (!target.expectBreak && target.expectedAfterDamage != target.expectedBeforeDamage + 1)) {
            return inconclusive("unenchanted vanilla item eligibility/max damage or exact one-point/break expectation not independently reviewed")
        }
        val pre = compound(before) ?: return inconclusive("typed nonempty before stack absent")
        val id = pre["id"]?.takeIf { it.type == 8 }?.payload as? String
        val count = pre["Count"]?.takeIf { it.type == 1 }?.payload as? Byte
        val metadata = compound(pre["tag"]) ?: return inconclusive("explicit vanilla tag/Damage required")
        val damage = metadata["Damage"]?.takeIf { it.type == 3 }?.payload as? Int
        if (id != target.itemId || count?.toInt() != 1 || damage == null ||
            damage != target.expectedBeforeDamage || damage !in 0 until target.reviewedMaxDamage ||
            pre.keys.any { it in setOf("ForgeCaps", "ForgeData") } ||
            metadata.keys.any { it in setOf("Enchantments", "StoredEnchantments", "ForgeCaps", "Unbreakable") }) {
            return inconclusive("different/stacked/enchantable/capability-owned or unknown pre-damage state")
        }
        if (target.expectBreak) {
            if (target.expectedBeforeDamage != target.reviewedMaxDamage - 1)
                return inconclusive("reviewed before Damage is not max minus one")
            if (observedBreakCallback == null) return inconclusive("independent break callback missing")
            if (!observedBreakCallback) return failed("complete callback contradicts expected max-damage break")
            return if (after == null) DurabilityVerdict("matched-narrow-transition", "actual known max+break, stack absent; no world/loot proof")
            else failed("expected consumed broken stack; after stack still present")
        }
        if (target.expectedBeforeDamage == target.reviewedMaxDamage - 1)
            return inconclusive("non-break was selected at reviewed maximum; exact break trial required")
        if (observedBreakCallback == null) return inconclusive("independent break callback missing")
        if (observedBreakCallback) return failed("complete callback contradicts expected non-break transition")
        if (after == null) return failed("complete observed EMPTY contradicts expected non-break one-point wear")
        val post = compound(after) ?: return inconclusive("malformed typed after stack; capture invalid")
        val postTag = compound(post["tag"]) ?: return failed("observed non-break tool lost required typed Damage tag")
        val next = postTag["Damage"]?.takeIf { it.type == 3 }?.payload as? Int
            ?: return failed("observed non-break tool lost exact typed Damage")
        if (next != target.expectedAfterDamage || next >= target.reviewedMaxDamage)
            return failed("actual damage did not match the predeclared one-point transition")
        if (post.keys != pre.keys || postTag.keys != metadata.keys ||
            pre.filterKeys { it != "tag" }.any { (key, value) -> !same(value, post.getValue(key)) } ||
            metadata.filterKeys { it != "Damage" }.any { (key, value) -> !same(value, postTag.getValue(key)) }) {
            return failed("an unrelated typed NBT field, count or item identity changed")
        }
        return DurabilityVerdict("matched-narrow-transition", "exact one-point vanilla Damage change, other typed NBT preserved")
    }

    /** Use only A's actual raw-chain v5 replay and SAME captured Store-verified saved child.
     * A must independently bind preToolCanonicalSha256 to the saved parent canonical stack.
     * A missing child capture is null/inconclusive, not an observed EMPTY. In the complete child,
     * absence of a typed slot is a proven saved EMPTY, NEVER a fabricated runtime transient.
     * Complete callback multiplicity 2..4 is rejected BEFORE legacy Boolean evaluation. */
    fun evaluateObserved(target: VanillaDurabilityTarget, savedBeforeTool: NbtValue?,
        witnessedNative: ReplayedPhysicalToolBreak, sealedAfter: CapturedSealedVanillaInventory?,
        toolSlot: Int): DurabilityVerdict {
        fun inconclusive(reason: String) = DurabilityVerdict("inconclusive", reason)
        fun failed(reason: String) = DurabilityVerdict("failed", reason)
        if (!Regex("minecraft:[a-z0-9_./-]+").matches(target.itemId) ||
            !target.reviewedUnenchantedNoModifiers || target.reviewedMaxDamage !in 1..1_000_000 ||
            target.expectedBeforeDamage !in 0 until target.reviewedMaxDamage ||
            (target.expectBreak && (target.expectedAfterDamage != null ||
                target.expectedBeforeDamage != target.reviewedMaxDamage - 1)) ||
            (!target.expectBreak && (target.expectedAfterDamage != target.expectedBeforeDamage + 1 ||
                target.expectedBeforeDamage >= target.reviewedMaxDamage - 1))) {
            return inconclusive("unreviewed vanilla one-point/break target")
        }
        val before = compound(savedBeforeTool) ?: return inconclusive("same-captured saved parent tool missing")
        val tags = compound(before["tag"]) ?: return inconclusive("reviewed parent typed Damage missing")
        if (before["id"]?.takeIf { it.type == 8 }?.payload != target.itemId ||
            before["Count"]?.takeIf { it.type == 1 }?.payload != 1.toByte() ||
            tags["Damage"]?.takeIf { it.type == 3 }?.payload != target.expectedBeforeDamage ||
            before.keys.any { it in setOf("ForgeCaps", "ForgeData") } ||
            tags.keys.any { it in setOf("Enchantments", "StoredEnchantments", "ForgeCaps", "Unbreakable") }) {
            return inconclusive("saved parent tool does not meet reviewed narrow vanilla eligibility")
        }
        val native = witnessedNative
        if (!native.captureComplete || toolSlot !in 0..8 || native.vanillaHotbarSlot != toolSlot ||
            native.hand != "MAIN_HAND" || native.preToolItemId != target.itemId ||
            !Regex("[0-9a-f]{64}").matches(native.preToolCanonicalSha256) ||
            !Regex("[0-9a-f]{64}").matches(native.trace.sha256) || native.trace.path.isBlank() ||
            native.actualCallbackCount !in 0..4 || native.causeEnterCount !in 0..4 ||
            native.causeEnterCount != native.causeReturnCount) {
            return inconclusive("actual original-tool callback or cause replay incomplete")
        }
        if (native.actualCallbackCount in 2..4) {
            if (!native.callbackSameOriginalItemObject || native.causeEnterCount == 0)
                return inconclusive("multiple callbacks not bound to original MAINHAND tool")
            return failed("complete bound native callbacks exceed exact reviewed zero/one expectation")
        }
        if (native.actualCallbackCount == 1 && !native.callbackSameOriginalItemObject)
            return inconclusive("callback not bound to original tool object")
        val captured = sealedAfter ?: return inconclusive("independently sealed child playerdata absent")
        if (captured.inventory.playerUuid != native.actorUuid) return inconclusive("sealed child actor not bound to native witness")
        val after = captured.typedSlot(toolSlot)
        val slot = captured.inventory.vanillaSlot(toolSlot)
        if ((slot is SealedVanillaSlot.Empty) != (after == null))
            return inconclusive("same-captured saved typed stack/slot disagrees")
        if (target.expectBreak) {
            if (native.actualCallbackCount != 1 || native.causeEnterCount != 1)
                return failed("complete break trial lacks exactly one cause-bound callback")
            if (!native.destroyReturnEmpty)
                return failed("complete break callback left original selected tool present at destroy return")
            if (native.refillSourceEntityUuid != null)
                return inconclusive("selected slot refilled by a source; use break-refill oracle")
            return evaluate(target, savedBeforeTool, after, true)
        }
        if (native.actualCallbackCount != 0 || native.causeEnterCount != 1 || native.destroyReturnEmpty ||
            native.refillSourceEntityUuid != null) {
            return failed("complete non-break trial contradicts one cause, zero callbacks, original tool retained")
        }
        return evaluate(target, savedBeforeTool, after, false)
    }

    /** Break/refill is NOT a saved empty slot. The EMPTY is an actual native destroy-return
     * observation; the independently saved child can contain the acquired source item in that
     * very slot. These DTOs must come from A's exact raw-chain replay and same-captured Store
     * read, not a scenario's diagnostic booleans or user-constructed proof. This method checks
     * the narrow vanilla wear/cause/source/saved typed facts; it does not authenticate a DTO.
     * A complete 2..4 callback contradiction needs neither a pickup nor a saved final stack. */
    fun evaluateBreakRefill(target: VanillaDurabilityTarget, savedBeforeTool: NbtValue?,
        witnessedNative: ReplayedPhysicalToolBreak, replayedPickup: ReplayedPhysicalSelectedPickup?,
        savedFinalSlot: NbtValue?): DurabilityVerdict {
        fun inconclusive(reason: String) = DurabilityVerdict("inconclusive", reason)
        fun failed(reason: String) = DurabilityVerdict("failed", reason)
        fun canonicalUuid(value: String): Boolean =
            runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
        fun hash(value: String) = Regex("[0-9a-f]{64}").matches(value)
        if (!target.expectBreak || target.expectedAfterDamage != null ||
            !target.reviewedUnenchantedNoModifiers || target.reviewedMaxDamage !in 1..1_000_000 ||
            !Regex("minecraft:[a-z0-9_./-]+").matches(target.itemId) ||
            target.expectedBeforeDamage != target.reviewedMaxDamage - 1) {
            return inconclusive("reviewed vanilla max-damage break target missing or not eligible")
        }
        val pre = compound(savedBeforeTool) ?: return inconclusive("typed saved parent tool absent")
        val preTag = compound(pre["tag"]) ?: return inconclusive("saved parent tool has no typed Damage tag")
        if (pre["id"]?.takeIf { it.type == 8 }?.payload != target.itemId ||
            pre["Count"]?.takeIf { it.type == 1 }?.payload != 1.toByte() ||
            preTag["Damage"]?.takeIf { it.type == 3 }?.payload != target.expectedBeforeDamage ||
            pre.keys.any { it in setOf("ForgeCaps", "ForgeData") } ||
            preTag.keys.any { it in setOf("Enchantments", "StoredEnchantments", "ForgeCaps", "Unbreakable") }) {
            return inconclusive("saved parent is not the reviewed single unmodified vanilla near-break tool")
        }
        val native = witnessedNative
        if (!native.captureComplete || !canonicalUuid(native.actorUuid) || native.hand != "MAIN_HAND" ||
            native.vanillaHotbarSlot !in 0..8 || native.preToolItemId != target.itemId ||
            !hash(native.preToolCanonicalSha256) ||
            native.actualCallbackCount !in 0..4 || native.causeEnterCount !in 0..4 ||
            native.causeReturnCount != native.causeEnterCount ||
            !hash(native.trace.sha256) || native.trace.path.isBlank()) {
            return inconclusive("native callback identity/cause/actor or raw replay is incomplete")
        }
        if (native.actualCallbackCount == 0)
            return failed("complete native observation contradicts required max-damage break callback")
        if (!native.callbackSameOriginalItemObject || native.causeEnterCount == 0)
            return inconclusive("native callback not bound to original tool object and balanced cause")
        if (native.actualCallbackCount in 2..4)
            return failed("complete original MAINHAND tool has multiple bound break callbacks")
        if (native.causeEnterCount != 1 || !native.destroyReturnEmpty)
            return failed("complete break callback did not consume exactly one cause and empty the original slot")
        val pickup = replayedPickup ?: return inconclusive("actual selected-slot refill replay absent")
        val saved = compound(savedFinalSlot) ?: return inconclusive("independently saved final selected slot absent")
        val savedId = saved["id"]?.takeIf { it.type == 8 }?.payload as? String
        val savedCount = saved["Count"]?.takeIf { it.type == 1 }?.payload as? Byte
        val sealed = pickup.savedFinalStack
        if (!canonicalUuid(pickup.actorUuid) || !canonicalUuid(pickup.sourceEntityUuid) ||
            !hash(pickup.acquiredCanonicalSha256) || !hash(pickup.acquiredContentSha256) ||
            !hash(pickup.afterSelectedCanonicalSha256) || !hash(sealed.canonicalStackSha256) ||
            !hash(sealed.contentSha256) || pickup.acquiredCount !in 1..64 ||
            !Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(pickup.acquiredItemId) ||
            sealed.hashAlgorithm != STACK_NBT_HASH_ALGORITHM ||
            sealed.contentAlgorithm != STACK_CONTENT_HASH_ALGORITHM ||
            native.refillSourceEntityUuid == null || !canonicalUuid(native.refillSourceEntityUuid) ||
            native.trace != pickup.trace || savedId != sealed.itemId ||
            savedCount?.toInt() != sealed.count) {
            return inconclusive("same-captured saved typed/hash stack or bound raw pickup identity missing")
        }
        if (native.actorUuid != pickup.actorUuid || native.vanillaHotbarSlot != pickup.vanillaHotbarSlot ||
            native.vanillaHotbarSlot != pickup.outputSlot ||
            native.refillSourceEntityUuid != pickup.sourceEntityUuid ||
            !pickup.beforeSelectedEmpty || !pickup.other40Exact || !pickup.sourceTerminalAbsent ||
            pickup.afterSelectedCanonicalSha256 != pickup.acquiredCanonicalSha256 ||
            sealed.slot != native.vanillaHotbarSlot || sealed.itemId != pickup.acquiredItemId ||
            sealed.count != pickup.acquiredCount ||
            sealed.canonicalStackSha256 != pickup.acquiredCanonicalSha256 ||
            sealed.contentSha256 != pickup.acquiredContentSha256) {
            return failed("actual source refill or independently saved selected slot contradicts complete break/pickup replay")
        }
        return DurabilityVerdict("matched-transient-break-with-saved-anchor",
            "actual cause-bound break/destroy-return EMPTY, genuine source refill, and separately saved typed final slot; EMPTY is not a sealed checkpoint")
    }
}
