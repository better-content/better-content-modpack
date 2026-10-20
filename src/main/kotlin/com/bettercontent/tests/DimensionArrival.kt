package com.bettercontent.tests

import java.time.Duration

data class ArrivalMeasurement(val elapsedSeconds: Double, val remainingSeconds: Double, val complete: Boolean)

/** A teleport and both native arrival oracles consume one absolute allowance. */
object DimensionArrival {
    fun verify(
        teleport: () -> Unit,
        serverPosition: (Duration) -> Unit,
        clientPosition: (Duration) -> Unit,
        budget: Duration = Duration.ofSeconds(90),
        clock: () -> Long = System::nanoTime,
        report: (ArrivalMeasurement) -> Unit = {},
    ) {
        val started = clock()
        val deadline = ScenarioDeadline(budget, clock)
        var complete = false
        var primary: Throwable? = null
        try {
            deadline.requireRemaining()
            teleport()
            serverPosition(deadline.requireRemaining())
            deadline.requireRemaining()
            clientPosition(deadline.requireRemaining())
            deadline.requireRemaining()
            complete = true
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            try {
                report(ArrivalMeasurement((clock() - started) / 1_000_000_000.0,
                    deadline.remaining().toNanos() / 1_000_000_000.0, complete))
            } catch (reporting: Throwable) {
                if (primary != null) primary.addSuppressed(reporting) else throw reporting
            }
        }
    }
}
