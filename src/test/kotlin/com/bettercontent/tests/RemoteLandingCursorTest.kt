package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.floor

class RemoteLandingCursorTest {
    @Test fun fullRosterAndThreeCircuitsKeepNativeFontLandingsNovelAndFarApart() {
        val dimensions = listOf("undergarden", "twilight", "pit", "deep_void", "bumblezone") +
            (5 until 20).map { "dimension_$it" }
        val cursor = RemoteLandingCursor()
        val visited = mutableMapOf<String, MutableSet<Int>>()
        val last = mutableMapOf<String, Double>()
        var previousDimension = "overworld"
        var fontEntries = 0

        fun visit(dimension: String, relocations: Int) {
            for (relocation in 0..relocations) {
                // The real Font places the client near its origin, not at a
                // test-commanded target; this models the observed ~96-block offset.
                val nativeFont = dimension == "bumblezone" &&
                    relocation == 0 && previousDimension != dimension
                val x = if (nativeFont) cursor.furthestX() - 96.0 else cursor.nextX()
                val prior = last[dimension]
                if (prior != null) assertTrue(abs(x - prior) >= 9999.0,
                    "repeated $dimension landing too close to its prior landing")
                assertTrue(visited.getOrPut(dimension) { mutableSetOf() }.add(floor(x / 16).toInt()),
                    "revisited $dimension chunk at $x")
                cursor.observe(x)
                last[dimension] = x
                previousDimension = dimension
                if (nativeFont) fontEntries++
            }
        }

        dimensions.forEach { visit(it, relocations = 10) }
        val routes = listOf(dimensions.asReversed(), dimensions.drop(1) + dimensions.first(),
            dimensions.asReversed().drop(1) + dimensions.last())
        routes.forEach { route -> route.forEach { visit(it, relocations = 1) } }
        assertEquals(4, fontEntries) // Initial entry and one per full-roster circuit.
        assertEquals(17, visited.getValue("bumblezone").size) // Eleven initial + three circuits * two.
        assertEquals(340, visited.values.sumOf { it.size })
    }

    @Test fun observedNativeFontLandingCanAdvanceButNeverRewindFutureTargets() {
        val cursor = RemoteLandingCursor()
        cursor.observe(10000.5)
        cursor.observe(9904.5)
        assertEquals(20000.5, cursor.nextX())
        cursor.observe(25100.5)
        assertEquals(35100.5, cursor.nextX())
        assertThrows(IllegalArgumentException::class.java) { cursor.observe(Double.NaN) }
    }
}
