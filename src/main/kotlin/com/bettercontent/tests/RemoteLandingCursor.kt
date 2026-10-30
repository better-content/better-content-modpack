package com.bettercontent.tests

/** Allocates fresh horizontal targets across the whole connected Tele Soak itinerary.
 * A Font entry is observed, not commanded: the next ordinary remote target must
 * advance beyond both that actual landing and every earlier dimension target. */
internal class RemoteLandingCursor {
    private var furthestX = 0.5

    fun nextX(): Double = furthestX + 10000.0

    fun observe(actualX: Double) {
        require(actualX.isFinite()) { "invalid observed remote landing X: $actualX" }
        furthestX = maxOf(furthestX, actualX)
    }

    fun furthestX(): Double = furthestX
}
