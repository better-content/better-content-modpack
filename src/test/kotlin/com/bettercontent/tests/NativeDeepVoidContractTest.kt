package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

/** Parser/correlation negative controls only, never a claim of native transfer acceptance. */
@Tag("fast")
class NativeDeepVoidContractTest {
    private val nonce = UUID.randomUUID()
    private val player = UUID.randomUUID()
    private fun before(kind: String = "entry", bottom: Int = -2032, threshold: Double = bottom - 6.0, y: Double = threshold - 0.001) =
        NativeDeepVoidContract.parseBefore("BC_DEEPVOID_BEFORE run=run-1 nonce=$nonce case=$kind uuid=$player tick=120 dimension=minecraft:overworld bottom=$bottom threshold=$threshold y=$y mode=survival natural=false void_enabled=${kind != "disabled"} other_enabled=false end_enabled=false")
    private fun after(kind: String = "entry", tick: Int = 120, dimension: String = "the_deep_void:deep_void", natural: Boolean = true) =
        NativeDeepVoidContract.parseAfter("BC_DEEPVOID_AFTER run=run-1 nonce=$nonce case=$kind uuid=$player tick=$tick dimension=$dimension bottom=-64 y=64.0 natural=$natural mode=survival")
    @Test fun `parser correlates exact native tick and refuses stale run actor and phase`() {
        NativeDeepVoidContract.requireNativeDecision(before(), after(), "run-1", nonce, player, "entry")
        assertThrows(IllegalArgumentException::class.java) { NativeDeepVoidContract.requireNativeDecision(before(), after(tick = 121), "run-1", nonce, player, "entry") }
        assertThrows(IllegalArgumentException::class.java) { NativeDeepVoidContract.requireNativeDecision(before(), after(), "run-2", nonce, player, "entry") }
        assertThrows(IllegalArgumentException::class.java) { NativeDeepVoidContract.requireNativeDecision(before(), after(), "run-1", UUID.randomUUID(), player, "entry") }
        assertThrows(IllegalArgumentException::class.java) { NativeDeepVoidContract.requireNativeDecision(before(), after(), "run-1", nonce, UUID.randomUUID(), "entry") }
    }
    @Test fun `wrong bottom literal teleport and malformed observation are not accepted`() {
        assertThrows(IllegalArgumentException::class.java) { NativeDeepVoidContract.requireNativeDecision(before(bottom = -64), after(), "run-1", nonce, player, "entry") }
        assertThrows(IllegalArgumentException::class.java) { NativeDeepVoidContract.requireNativeDecision(before(threshold = -70.0), after(), "run-1", nonce, player, "entry") }
        assertThrows(IllegalArgumentException::class.java) { NativeDeepVoidContract.requireNativeDecision(before(), after(natural = false), "run-1", nonce, player, "entry") }
        assertThrows(IllegalArgumentException::class.java) { NativeDeepVoidContract.parseAfter("BC_DEEPVOID_AFTER run=run-1 y=NaN") }
        assertThrows(IllegalArgumentException::class.java) { NativeDeepVoidContract.requireNativeDecision(before(kind = "disabled"), after(kind = "disabled"), "run-1", nonce, player, "disabled") }
        assertThrows(IllegalArgumentException::class.java) { NativeDeepVoidContract.requireNativeDecision(before(kind = "above", y = -2037.999), after(kind = "above"), "run-1", nonce, player, "above") }
    }
}
