package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("fast")
class MoonlightClientObservationTest {
    private val binding = MoonlightClientObservation.Binding("run-20261024", UUID.randomUUID(), "SmokeClient1", "a".repeat(64), "b".repeat(64))
    private fun query(kind: MoonlightClientObservation.Kind) = MoonlightClientObservation.Query(binding, kind)
    private fun body(value: String, player: String = binding.player) = "$player has the following entity data: $value"
    private fun parse(query: MoonlightClientObservation.Query, value: String): Int =
        MoonlightClientObservation.integer(query, MoonlightClientObservation.Response(query, body(value)))

    @Test fun selectedSlotIsExactlyAnIntegerInVanillaHotbarRange() {
        val q = query(MoonlightClientObservation.Kind.SELECTED_SLOT)
        for (slot in 0..8) assertEquals(slot, parse(q, "$slot"))
        for (invalid in listOf("9", "-1", "8b", "8.0", "8L", "08", " 8", "8 ", "\"8\"", "8\n8", ""))
            assertThrows(Exception::class.java, { parse(q, invalid) }, invalid)
    }

    @Test fun mapIdIsNonnegativeNativeIntNotSnbtNumberCoercionOrOverflow() {
        val q = query(MoonlightClientObservation.Kind.MAP_ID)
        for (value in listOf(0, 1, 1234567, Int.MAX_VALUE)) assertEquals(value, parse(q, "$value"))
        for (invalid in listOf("-1", "2147483648", "99999999999999999999", "1.0d", "1b", "1L", "+1", "01", "NaN", "{map:1}", "1 trailing", "1\r\n"))
            assertThrows(Exception::class.java, { parse(q, invalid) }, invalid)
    }

    @Test fun originalGameModeIsBoundedAndRestorableWithoutGuessingCreativeBehavior() {
        val q = query(MoonlightClientObservation.Kind.GAME_MODE)
        for (mode in 0..3) assertEquals(mode, parse(q, "$mode"))
        for (invalid in listOf("4", "-1", "survival", "true", "0b")) assertThrows(Exception::class.java) { parse(q, invalid) }
    }

    @Test fun wrongPlayerExtraTextAndUnrelatedMessagesCannotSatisfyNativeResponse() {
        val q = query(MoonlightClientObservation.Kind.MAP_ID)
        for (invalid in listOf(body("1", "SmokeClient2"), body("1", "OtherSmokeClient1"), "[Server] ${body("1")}",
            "${body("1")}\n${body("1")}", "The time is 1", "Found no elements matching SelectedItem.tag.map", body("1") + " trailing"))
            assertThrows(Exception::class.java) { MoonlightClientObservation.integer(q, MoonlightClientObservation.Response(q, invalid)) }
        assertNull(MoonlightClientObservation.responsePattern(binding.player).find(body("1", "OtherSmokeClient1")))
        assertNull(MoonlightClientObservation.responsePattern(binding.player).find(body("1", "SmokeClient2")))
    }

    @Test fun staleRunNonceCandidatePlayerQueryAndKindBindingsAreRejected() {
        val q = query(MoonlightClientObservation.Kind.MAP_ID)
        val wrongBindings = listOf(binding.copy(run = "other-run"), binding.copy(nonce = UUID.randomUUID()), binding.copy(player = "SmokeClient2"),
            binding.copy(clientSha256 = "c".repeat(64)), binding.copy(serverSha256 = "d".repeat(64)))
        for (wrong in wrongBindings) assertThrows(Exception::class.java) {
            MoonlightClientObservation.integer(q, MoonlightClientObservation.Response(q.copy(binding = wrong), body("1")))
        }
        for (wrong in listOf(q.copy(nonce = UUID.randomUUID()), q.copy(kind = MoonlightClientObservation.Kind.SELECTED_SLOT)))
            assertThrows(Exception::class.java) { MoonlightClientObservation.integer(q, MoonlightClientObservation.Response(wrong, body("1"))) }
    }

    @Test fun emptySlotMarkersBindTheExactRunCandidateRequestAndOperation() {
        val nonce = UUID.randomUUID(); val expected = MoonlightClientObservation.marker(binding, "hotbar8-empty", nonce)
        MoonlightClientObservation.requireMarker(expected, expected)
        for (wrong in listOf(MoonlightClientObservation.marker(binding, "offhand-empty", nonce),
            MoonlightClientObservation.marker(binding, "hotbar8-empty", UUID.randomUUID()),
            MoonlightClientObservation.marker(binding.copy(clientSha256 = "c".repeat(64)), "hotbar8-empty", nonce), expected + " stale", "prefix $expected"))
            assertThrows(Exception::class.java) { MoonlightClientObservation.requireMarker(expected, wrong) }
        assertThrows(Exception::class.java) { MoonlightClientObservation.marker(binding, "bad\noperation", nonce) }
    }

    @Test fun creativeDestinationUsesOnlyTheVanillaMainInventoryCommandSlotMapping() {
        for (slot in 0..8) assertEquals("hotbar.$slot", MoonlightClientObservation.inventoryCommandSlot(slot))
        for (slot in 9..35) assertEquals("inventory.${slot - 9}", MoonlightClientObservation.inventoryCommandSlot(slot))
        for (slot in listOf(-106, -1, 36, 100, 150)) assertThrows(Exception::class.java) { MoonlightClientObservation.inventoryCommandSlot(slot) }
    }

    @Test fun freshSlotPresenceRequiresExactlyOneCurrentNonceBoundBranchMarker() {
        val nonce = UUID.randomUUID()
        val empty = MoonlightClientObservation.marker(binding, "slot-0-empty", nonce)
        val occupied = MoonlightClientObservation.marker(binding, "slot-0-occupied", nonce)
        assertFalse(MoonlightClientObservation.slotPresence(empty, occupied, "[Server] $empty\n"))
        assertTrue(MoonlightClientObservation.slotPresence(empty, occupied, "[Server] $occupied\n"))
        val stale = MoonlightClientObservation.marker(binding, "slot-0-empty", UUID.randomUUID())
        for (bad in listOf("", stale, "$empty\n$occupied\n", "$empty\n$empty\n", "$empty trailing"))
            assertThrows(Exception::class.java) { MoonlightClientObservation.slotPresence(empty, occupied, bad) }
    }

    @Test fun onlyFullDistAndDebugRunNeverUnrelatedTargetedScenarios() {
        assertTrue(MoonlightClientObservation.shouldRun("dist", null)); assertTrue(MoonlightClientObservation.shouldRun("debug", null))
        for (tier in listOf("dist", "debug")) for (target in listOf("restart-compat", "dimension:minecraft:overworld", "native-networked-deepvoid", "inventory"))
            assertFalse(MoonlightClientObservation.shouldRun(tier, target))
        for (tier in listOf("dev", "source", "client", "")) assertFalse(MoonlightClientObservation.shouldRun(tier, null))
        assertThrows(Exception::class.java) { binding.copy(player = "bad selector @a") }
        assertThrows(Exception::class.java) { binding.copy(clientSha256 = "ABC") }
        assertThrows(Exception::class.java) { binding.copy(run = "../other") }
    }
}
