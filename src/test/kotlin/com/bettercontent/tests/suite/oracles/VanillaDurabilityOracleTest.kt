package com.bettercontent.tests.suite.oracles

import com.bettercontent.tests.suite.checkpoints.NbtList
import com.bettercontent.tests.suite.checkpoints.NbtValue
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.checkpoints.ReplayedPhysicalToolBreak
import com.bettercontent.tests.suite.checkpoints.ReplayedPhysicalSelectedPickup
import com.bettercontent.tests.suite.checkpoints.SealedInventoryStack
import com.bettercontent.tests.suite.checkpoints.SealedPlayerInventory
import com.bettercontent.tests.suite.checkpoints.CapturedSealedVanillaInventory
import com.bettercontent.tests.suite.checkpoints.STACK_CONTENT_HASH_ALGORITHM
import com.bettercontent.tests.suite.checkpoints.STACK_NBT_HASH_ALGORITHM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Existing typed NBT representation only; no duplicate SNBT/NBT parser or game runtime. */
class VanillaDurabilityOracleTest {
    private fun stack(damage: Int, count: Byte = 1, hint: ByteArray = byteArrayOf(4, 7),
        extra: Map<String, NbtValue> = emptyMap()): NbtValue {
        val metadata = linkedMapOf("Damage" to NbtValue(3, damage),
            "Lore" to NbtValue(9, NbtList(8, listOf(NbtValue(8, "owner:alpha")))),
            "Bytes" to NbtValue(7, hint)) + extra
        return NbtValue(10, linkedMapOf("id" to NbtValue(8, "minecraft:iron_pickaxe"),
            "Count" to NbtValue(1, count), "Slot" to NbtValue(1, 0.toByte()),
            "tag" to NbtValue(10, metadata)))
    }
    private val one = VanillaDurabilityTarget("minecraft:iron_pickaxe", 250, true, 10, 11, false)
    @Test fun `one known vanilla damage point preserves every other typed field including array content`() {
        assertEquals("matched-narrow-transition", VanillaDurabilityOracle.evaluate(one, stack(10), stack(11), false).status)
        assertEquals("failed", VanillaDurabilityOracle.evaluate(one, stack(10), stack(11, hint = byteArrayOf(4, 8)), false).status)
        assertEquals("failed", VanillaDurabilityOracle.evaluate(one, stack(10), stack(11, count = 2), false).status)
        assertEquals("failed", VanillaDurabilityOracle.evaluate(one, stack(10), stack(12), false).status)
    }
    @Test fun `break requires exact known max and independent callback, no post stack`() {
        val atMax = VanillaDurabilityTarget("minecraft:iron_pickaxe", 250, true, 249, null, true)
        assertEquals("matched-narrow-transition", VanillaDurabilityOracle.evaluate(atMax, stack(249), null, true).status)
        assertEquals("inconclusive", VanillaDurabilityOracle.evaluate(atMax, stack(249), null, null).status)
        assertEquals("failed", VanillaDurabilityOracle.evaluate(atMax, stack(249), stack(249), true).status)
        assertEquals("failed", VanillaDurabilityOracle.evaluate(atMax, stack(249), null, false).status)
        assertEquals("failed", VanillaDurabilityOracle.evaluate(one, stack(10), stack(11), true).status)
        assertEquals("inconclusive", VanillaDurabilityOracle.evaluate(atMax.copy(expectedBeforeDamage = 248),
            stack(248), null, true).status)
    }
    @Test fun `transient actual break and real same-slot refill use separately saved source stack not a false sealed EMPTY`() {
        val reviewed = VanillaDurabilityTarget("minecraft:iron_pickaxe", 250, true, 249, null, true)
        val trace = BoundFile("reviewed-raw-chain.json", "d".repeat(64))
        val actor = "00000000-0000-4000-8000-000000000001"
        val source = "00000000-0000-4000-8000-000000000002"
        val native = ReplayedPhysicalToolBreak(actor, "MAIN_HAND", 0, "minecraft:iron_pickaxe",
            "c".repeat(64), 1, 1, 1, true, true, true, trace, source)
        val saved = SealedInventoryStack(0, "minecraft:cobblestone", 2, STACK_NBT_HASH_ALGORITHM,
            "a".repeat(64), STACK_CONTENT_HASH_ALGORITHM, "b".repeat(64))
        val pickup = ReplayedPhysicalSelectedPickup(actor, 0, source, 0, "minecraft:cobblestone", 2,
            "a".repeat(64), "b".repeat(64), true, "a".repeat(64), true, true, saved, trace)
        fun final(id: String = "minecraft:cobblestone", count: Byte = 2) = NbtValue(10,
            mapOf("id" to NbtValue(8, id), "Count" to NbtValue(1, count)))
        val verdict = VanillaDurabilityOracle.evaluateBreakRefill(reviewed, stack(249), native, pickup, final())
        assertEquals("matched-transient-break-with-saved-anchor", verdict.status)
        assertEquals("inconclusive", VanillaDurabilityOracle.evaluateBreakRefill(reviewed, stack(249), native,
            null, final()).status)
        assertEquals("inconclusive", VanillaDurabilityOracle.evaluateBreakRefill(reviewed, stack(249), native,
            pickup, final(count = 1)).status) // same-captured typed/hash adapter conflict, not gameplay
        assertEquals("failed", VanillaDurabilityOracle.evaluateBreakRefill(reviewed, stack(249), native,
            pickup.copy(afterSelectedCanonicalSha256 = "e".repeat(64)), final()).status)
        assertEquals("failed", VanillaDurabilityOracle.evaluateBreakRefill(reviewed, stack(249), native,
            pickup.copy(savedFinalStack = saved.copy(canonicalStackSha256 = "e".repeat(64))), final()).status)
        assertEquals("failed", VanillaDurabilityOracle.evaluateBreakRefill(reviewed, stack(249), native,
            pickup.copy(other40Exact = false), final()).status)
    }
    @Test fun `count-aware saved nonbreak distinguishes missing seal from proven EMPTY and contradictory typed child`() {
        val actor = "00000000-0000-4000-8000-000000000001"
        val trace = BoundFile("reviewed-raw-chain.json", "d".repeat(64))
        val native = ReplayedPhysicalToolBreak(actor, "MAIN_HAND", 0, "minecraft:iron_pickaxe",
            "c".repeat(64), 0, 1, 1, false, false, true, trace, null)
        fun captured(typed: NbtValue?): CapturedSealedVanillaInventory {
            val fields = (typed?.payload as? Map<*, *>)
            val item = (fields?.get("id") as? NbtValue)?.payload as? String ?: "minecraft:iron_pickaxe"
            val count = ((fields?.get("Count") as? NbtValue)?.payload as? Byte)?.toInt() ?: 1
            val sealed = if (typed == null) emptyList() else listOf(SealedInventoryStack(0,
                item, count, STACK_NBT_HASH_ALGORITHM, "a".repeat(64),
                STACK_CONTENT_HASH_ALGORITHM, "b".repeat(64)))
            return CapturedSealedVanillaInventory(SealedPlayerInventory("child", actor, trace, sealed,
                null, sealed), if (typed == null) emptyMap() else mapOf(0 to typed))
        }
        assertEquals("matched-narrow-transition", VanillaDurabilityOracle.evaluateObserved(one, stack(10),
            native, captured(stack(11)), 0).status)
        assertEquals("inconclusive", VanillaDurabilityOracle.evaluateObserved(one, stack(10),
            native, null, 0).status) // no sealed child is NOT a saved empty slot
        assertEquals("failed", VanillaDurabilityOracle.evaluateObserved(one, stack(10),
            native, captured(null), 0).status) // verified saved child slot is actually EMPTY
        val lostTag = NbtValue(10, mapOf("id" to NbtValue(8, "minecraft:iron_pickaxe"),
            "Count" to NbtValue(1, 1.toByte())))
        assertEquals("failed", VanillaDurabilityOracle.evaluateObserved(one, stack(10),
            native, captured(lostTag), 0).status)
        val wrong = NbtValue(10, mapOf("id" to NbtValue(8, "minecraft:cobblestone"),
            "Count" to NbtValue(1, 1.toByte()), "tag" to NbtValue(10, mapOf("Damage" to NbtValue(3, 11)))))
        assertEquals("failed", VanillaDurabilityOracle.evaluateObserved(one, stack(10),
            native, captured(wrong), 0).status)
        assertEquals("failed", VanillaDurabilityOracle.evaluateObserved(one, stack(10),
            native.copy(actualCallbackCount = 2, causeEnterCount = 2, causeReturnCount = 2,
                callbackSameOriginalItemObject = true), null, 0).status)
        assertEquals("inconclusive", VanillaDurabilityOracle.evaluateObserved(one, stack(10),
            native.copy(captureComplete = false), captured(null), 0).status)
    }
    @Test fun `complete duplicate or missing bound callback is a contradiction before optional pickup exists`() {
        val reviewed = VanillaDurabilityTarget("minecraft:iron_pickaxe", 250, true, 249, null, true)
        val trace = BoundFile("reviewed-raw-chain.json", "d".repeat(64))
        val actor = "00000000-0000-4000-8000-000000000001"
        val native = ReplayedPhysicalToolBreak(actor, "MAIN_HAND", 0, "minecraft:iron_pickaxe",
            "c".repeat(64), 2, 2, 2, true, false, true, trace, null)
        assertEquals("failed", VanillaDurabilityOracle.evaluateBreakRefill(reviewed, stack(249), native,
            null, null).status)
        assertEquals("failed", VanillaDurabilityOracle.evaluateBreakRefill(reviewed, stack(249),
            native.copy(actualCallbackCount = 0, causeEnterCount = 0, causeReturnCount = 0,
                callbackSameOriginalItemObject = false), null, null).status)
        assertEquals("inconclusive", VanillaDurabilityOracle.evaluateBreakRefill(reviewed, stack(249),
            native.copy(captureComplete = false), null, null).status)
    }
    @Test fun `enchantment opaque capability and missing damage never assume a vanilla debit`() {
        assertEquals("inconclusive", VanillaDurabilityOracle.evaluate(one.copy(reviewedUnenchantedNoModifiers = false),
            stack(10), stack(11), false).status)
        assertEquals("inconclusive", VanillaDurabilityOracle.evaluate(one, stack(10, extra = mapOf(
            "Enchantments" to NbtValue(9, NbtList(10, emptyList())))), stack(11), false).status)
        assertEquals("inconclusive", VanillaDurabilityOracle.evaluate(one, stack(10, extra = mapOf(
            "ForgeCaps" to NbtValue(10, emptyMap<String, NbtValue>()))), stack(11), false).status)
        val noDamage = NbtValue(10, linkedMapOf("id" to NbtValue(8, "minecraft:iron_pickaxe"),
            "Count" to NbtValue(1, 1.toByte()), "tag" to NbtValue(10, emptyMap<String, NbtValue>())))
        assertEquals("inconclusive", VanillaDurabilityOracle.evaluate(one, noDamage, stack(11), false).status)
        assertEquals("inconclusive", VanillaDurabilityOracle.evaluate(one, stack(10), stack(11), null).status)
    }
}
