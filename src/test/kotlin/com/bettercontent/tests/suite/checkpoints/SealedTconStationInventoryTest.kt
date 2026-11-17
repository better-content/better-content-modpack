package com.bettercontent.tests.suite.checkpoints

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Unrun synthetic typed-NBT regressions, NOT a saved TCon station or recipe receipt. */
class SealedTconStationInventoryTest {
    private fun item(slot: Byte, count: Byte = 1): NbtValue = NbtValue(10, mapOf(
        "Slot" to NbtValue(1, slot), "id" to NbtValue(8, "tconstruct:pickaxe"),
        "Count" to NbtValue(1, count)))
    private fun items(vararg entries: NbtValue) = NbtValue(9, NbtList(10, entries.toList()))

    @Test fun explicitEmptySlotsAndCountIncludingHash() {
        val slots = decodePinnedTconStationSlots(items(item(1)), 2)
        assertEquals(null, slots[0].typed)
        assertEquals("tconstruct:pickaxe", slots[1].itemId)
        assertEquals(1, slots[1].count)
        val doubled = decodePinnedTconStationSlots(items(item(1, 2)), 2)
        assertEquals(false, slots[1].stackSha256 == doubled[1].stackSha256)
    }

    @Test fun rejectDuplicateOutOfRangeInvalidByteAndWrongListType() {
        for (bad in listOf(items(item(0), item(0)), items(item(2)), items(item((-128).toByte())),
            items(item(0, 0)), NbtValue(9, NbtList(8, listOf(NbtValue(8, "wrong")))))) {
            assertThrows(IllegalArgumentException::class.java) { decodePinnedTconStationSlots(bad, 2) }
        }
    }
}
