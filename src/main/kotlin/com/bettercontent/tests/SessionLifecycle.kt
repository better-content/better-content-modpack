package com.bettercontent.tests

import java.time.Duration

enum class SessionTier(val selector: String) { SMOKE("smoke"), SOAK("soak") }

data class SessionRequest(val tier: SessionTier, val minutes: Long = 0) {
    init {
        require((tier == SessionTier.SMOKE && minutes == 0L) || (tier == SessionTier.SOAK && minutes in 1..10080)) {
            "Smoke takes no duration; Soak requires --minutes 1..10080"
        }
    }
    val connectedDuration: Duration get() = Duration.ofMinutes(minutes)
}

interface SessionDriver {
    fun startServer()
    fun awaitServerReady()
    fun connectClient()
    fun awaitClientJoined()
    /** Must verify current server membership AND fresh receipt in the real client, not a stale log marker. */
    fun heartbeat()
    fun ensureConnected()
    fun disconnectClient()
    fun saveAndStopServer()
    fun validate()
}

interface SessionClock {
    fun nanos(): Long
    fun sleep(duration: Duration)
}

object MonotonicSessionClock : SessionClock {
    override fun nanos(): Long = System.nanoTime()
    override fun sleep(duration: Duration) = Thread.sleep(duration.toMillis())
}

/** The only runtime campaign: one start, one connection, one disconnect, one save/stop. */
fun runLifecycle(request: SessionRequest, driver: SessionDriver, clock: SessionClock = MonotonicSessionClock) {
    driver.startServer()
    driver.awaitServerReady()
    driver.connectClient()
    driver.awaitClientJoined()
    driver.heartbeat()
    // Loading/preparation and attempted login never count toward Soak.
    val verifiedJoin = clock.nanos()
    val deadline = verifiedJoin + request.connectedDuration.toNanos()
    var nextHeartbeat = verifiedJoin + Duration.ofSeconds(15).toNanos()
    while (clock.nanos() < deadline) {
        driver.ensureConnected()
        val remaining = deadline - clock.nanos()
        if (remaining > 0) clock.sleep(Duration.ofNanos(minOf(remaining, Duration.ofSeconds(1).toNanos())))
        driver.ensureConnected()
        if (clock.nanos() >= nextHeartbeat || clock.nanos() >= deadline) {
            driver.heartbeat()
            nextHeartbeat = clock.nanos() + Duration.ofSeconds(15).toNanos()
        }
    }
    driver.ensureConnected()
    driver.disconnectClient()
    driver.saveAndStopServer()
    driver.validate()
}
