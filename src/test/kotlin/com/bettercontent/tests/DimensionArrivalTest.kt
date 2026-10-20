package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Duration

@Tag("fast")
class DimensionArrivalTest {
    @Test fun serverTimeReducesClientAllowance() {
        var now = 0L
        var measured: ArrivalMeasurement? = null
        DimensionArrival.verify(
            teleport = {},
            serverPosition = { remaining -> assertEquals(Duration.ofSeconds(90), remaining); now += Duration.ofSeconds(80).toNanos() },
            clientPosition = { remaining -> assertEquals(Duration.ofSeconds(10), remaining); now += Duration.ofSeconds(9).toNanos() },
            clock = { now }, report = { measured = it },
        )
        assertEquals(ArrivalMeasurement(89.0, 1.0, true), measured)
    }

    @Test fun exactExpiryAndLateClientResponsesFailEvenIfObservationReturnsSuccessfully() {
        for (seconds in listOf(90L, 91L, 160L)) {
            var now = 0L
            var measured: ArrivalMeasurement? = null
            assertThrows(IllegalStateException::class.java) {
                DimensionArrival.verify({}, { now = Duration.ofSeconds(80).toNanos() },
                    { now = Duration.ofSeconds(seconds).toNanos() }, clock = { now }, report = { measured = it })
            }
            assertEquals(false, measured!!.complete)
            assertEquals(0.0, measured!!.remainingSeconds)
        }
    }

    @Test fun serverFailureOrExpiryPreventsClientWaiting() {
        for (expired in listOf(false, true)) {
            var now = 0L
            var waited = false
            val original = IllegalArgumentException("server confirmation failed")
            val failure = assertThrows(RuntimeException::class.java) {
                DimensionArrival.verify({}, {
                    if (expired) now = Duration.ofSeconds(90).toNanos() else throw original
                }, { waited = true }, clock = { now })
            }
            if (!expired) assertSame(original, failure)
            assertFalse(waited)
        }
    }

    @Test fun teleportTimeIsPartOfBudgetAndJustUnderExpiryPasses() {
        var now = 0L
        DimensionArrival.verify({ now = Duration.ofSeconds(89).toNanos() },
            { assertEquals(Duration.ofSeconds(1), it) }, { now = Duration.ofSeconds(90).toNanos() - 1 }, clock = { now })
    }

    @Test fun reportingFailureDoesNotMaskOriginalFailure() {
        val primary = IllegalArgumentException("server failed")
        val secondary = IllegalStateException("report failed")
        val failure = assertThrows(IllegalArgumentException::class.java) {
            DimensionArrival.verify({}, { throw primary }, {}, clock = { 0L }, report = { throw secondary })
        }
        assertSame(primary, failure)
        assertEquals(listOf(secondary), failure.suppressed.toList())
    }
}
