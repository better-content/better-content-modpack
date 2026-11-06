package com.bettercontent.tests

import java.time.Duration

enum class SessionTier(val selector: String) {
    SMOKE("smoke"), OVERWORLD_TELE_SOAK("overworld-tele-soak"), TELE_SOAK("tele-soak")
}

data class SessionRequest(val tier: SessionTier, val minutes: Long = 0) {
    init {
        require(minutes == 0L) { "runtime tiers have fixed durations; --minutes is unsupported" }
    }
    val connectedDuration: Duration get() = if (tier == SessionTier.OVERWORLD_TELE_SOAK) Duration.ofHours(1) else Duration.ZERO
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
internal fun runLifecycle(request: SessionRequest, driver: SessionDriver, clock: SessionClock = MonotonicSessionClock,
    replay: DiagnosticReplay? = null) {
    driver.startServer()
    driver.awaitServerReady()
    driver.connectClient()
    driver.awaitClientJoined()
    driver.heartbeat()
    if (request.tier != SessionTier.SMOKE) {
        runTeleSoak(driver as? TeleSoakDriver ?: error("Tele Soak requires an instrumented real-client driver"),
            clock, replay, overworldOnly = request.tier == SessionTier.OVERWORLD_TELE_SOAK)
        driver.disconnectClient()
        driver.saveAndStopServer()
        driver.validate()
        return
    }
    // Smoke has no dwell. A diagnostic Smoke still starts a fresh lifecycle.
    driver.ensureConnected()
    driver.disconnectClient()
    driver.saveAndStopServer()
    driver.validate()
}
