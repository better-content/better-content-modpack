package com.bettercontent.tests.suite.checkpoints

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Pure source-only Count/content/merge regressions. No tests or worlds executed. */
class ScopedSavedAcquisitionTest {
    private val scope = "vanilla_saved_ItemStack_NBT_including_ForgeCaps_not_external_inventory_adapter"
    private fun saved(count: Int, full: Char, content: Char = 'c') = SealedInventoryStack(4,
        "minecraft:stone", count, STACK_NBT_HASH_ALGORITHM, full.toString().repeat(64),
        STACK_CONTENT_HASH_ALGORITHM, content.toString().repeat(64))
    private fun wire(count: Long, full: Char, content: Char = 'c') = NativeWireStack("minecraft:stone",
        count, full.toString().repeat(64), content.toString().repeat(64), scope)
    @Test fun `merged final stack counts only newly acquired source quantity over sealed prior`() {
        requireNewlyAcquiredSavedSlotDelta(saved(10, 'a'), saved(15, 'b'), wire(15, 'b'),
            listOf(wire(2, 'd'), wire(3, 'e')), "minecraft:stone", 5)
        requireNewlyAcquiredSavedSlotDelta(null, saved(5, 'b'), wire(5, 'b'),
            listOf(wire(5, 'd')), "minecraft:stone", 5)
    }
    @Test fun `count inflation content drift unchanged full hash and forged source total fail`() {
        val sources = listOf(wire(2, 'd'), wire(3, 'e'))
        assertThrows(IllegalArgumentException::class.java) {
            requireNewlyAcquiredSavedSlotDelta(saved(10, 'a'), saved(15, 'b'), wire(15, 'b'),
                sources, "minecraft:stone", 15)
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireNewlyAcquiredSavedSlotDelta(saved(10, 'a'), saved(15, 'b'), wire(15, 'b'),
                sources, "minecraft:stone", 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireNewlyAcquiredSavedSlotDelta(saved(10, 'a', 'f'), saved(15, 'b'), wire(15, 'b'),
                sources, "minecraft:stone", 5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireNewlyAcquiredSavedSlotDelta(saved(10, 'b'), saved(15, 'b'), wire(15, 'b'),
                sources, "minecraft:stone", 5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireNewlyAcquiredSavedSlotDelta(saved(10, 'a'), saved(15, 'b'), wire(15, 'b'),
                listOf(wire(5, 'd', 'f')), "minecraft:stone", 5)
        }
    }
}
