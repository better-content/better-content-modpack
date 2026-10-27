package com.bettercontent.tests

import java.time.Duration

/** Bounded, one-client Tele Soak protocol. Runtime probes must be real server/client observations. */
interface TeleSoakDriver : SessionDriver {
    /** The complete live ServerLevel registry, not a curated progression whitelist. */
    fun registeredDimensions(progress: () -> Unit): List<String>
    /** A controlled teleport of the sole connected test player to an already verified safe landing site. */
    fun teleportTo(dimension: String)
    /** Wait for both server-player dimension and a fresh receipt in the connected client; call heartbeat while waiting. */
    fun verifyArrival(dimension: String, progress: () -> Unit)
    /** Refuse a vanished player, changed dimension, or stale client receipt. */
    fun ensureInDimension(dimension: String)
    /** Actual server TPS measured over a current sample; no warmup trimming or fabricated 20 TPS. */
    fun currentTps(): Double
    fun teleSoakEvent(name: String, fields: Map<String, Any>)
}

private val SECOND = Duration.ofSeconds(1).toNanos()
// Polling/TPS command latency needs margin: target 12s, fail if actual receipts ever age past 15s.
private val HEARTBEAT = Duration.ofSeconds(12).toNanos()
private val DWELL = Duration.ofMinutes(5).toNanos()
private val RECOVERY = Duration.ofSeconds(120).toNanos()
private val STABILITY = Duration.ofSeconds(30).toNanos()
private const val MIN_TPS = 19.0
private val DIMENSION_ID = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")

/** Preparation, arrival and TPS recovery do not count toward the uninterrupted five-minute dwell. */
fun runTeleSoak(driver: TeleSoakDriver, clock: SessionClock) {
    var nextHeartbeat = clock.nanos() + HEARTBEAT
    fun maintainConnection() {
        driver.ensureConnected()
        if (clock.nanos() >= nextHeartbeat) {
            driver.heartbeat()
            nextHeartbeat = clock.nanos() + HEARTBEAT
        }
    }
    val dimensions = driver.registeredDimensions(::maintainConnection).also { discovered ->
        require(discovered.size in 1..64 && discovered.size == discovered.toSet().size &&
            "minecraft:overworld" in discovered && discovered.all(DIMENSION_ID::matches)) {
            "invalid or incomplete live dimension roster: $discovered"
        }
    }.sorted()
    driver.teleSoakEvent("dimension_roster", mapOf("dimensions" to dimensions, "count" to dimensions.size))
    fun tick(dimension: String) {
        driver.ensureConnected()
        driver.ensureInDimension(dimension)
        if (clock.nanos() >= nextHeartbeat) {
            driver.heartbeat()
            driver.ensureInDimension(dimension)
            nextHeartbeat = clock.nanos() + HEARTBEAT
        }
    }
    fun visit(dimension: String, dwell: Boolean, circuit: Int) {
        driver.ensureConnected()
        val hopStarted = clock.nanos()
        driver.teleSoakEvent("hop_started", mapOf("dimension" to dimension, "circuit" to circuit))
        driver.teleportTo(dimension)
        driver.verifyArrival(dimension) {
            driver.ensureConnected()
            if (clock.nanos() >= nextHeartbeat) {
                driver.heartbeat()
                nextHeartbeat = clock.nanos() + HEARTBEAT
            }
        }
        tick(dimension)
        val arrival = clock.nanos()
        driver.teleSoakEvent("arrival_verified", mapOf("dimension" to dimension, "circuit" to circuit,
            "hop_seconds" to (arrival - hopStarted) / 1e9))
        var stableSince = -1L
        var priorSample = -1L
        val deadline = hopStarted + RECOVERY
        while (true) {
            tick(dimension)
            val tps = driver.currentTps()
            require(tps.isFinite() && tps >= 0.0 && tps <= 20.0001) { "invalid TPS sample for $dimension: $tps" }
            val at = clock.nanos()
            check(at <= deadline) { "TPS recovery deadline exceeded after hop to $dimension" }
            driver.teleSoakEvent("tps_sample", mapOf("dimension" to dimension, "circuit" to circuit,
                "elapsed_seconds" to (at - arrival) / 1e9, "tps" to tps))
            stableSince = if (tps >= MIN_TPS) {
                if (stableSince < 0 || (priorSample >= 0 && at - priorSample > 2 * SECOND)) at else stableSince
            } else -1L
            priorSample = at
            if (stableSince >= 0 && at - stableSince >= STABILITY) break
            check(at + SECOND <= deadline) { "TPS failed to recover to $MIN_TPS for 30 continuous seconds within 120 seconds in $dimension" }
            clock.sleep(Duration.ofSeconds(1))
        }
        driver.teleSoakEvent("tps_stable", mapOf("dimension" to dimension, "circuit" to circuit,
            "recovery_seconds" to (clock.nanos() - arrival) / 1e9))
        if (dwell) {
            val from = clock.nanos()
            val until = from + DWELL
            driver.teleSoakEvent("dwell_started", mapOf("dimension" to dimension, "required_seconds" to 300))
            while (clock.nanos() < until) {
                tick(dimension)
                clock.sleep(Duration.ofNanos(minOf(SECOND, until - clock.nanos())))
            }
            tick(dimension)
            driver.teleSoakEvent("dwell_completed", mapOf("dimension" to dimension,
                "actual_seconds" to (clock.nanos() - from) / 1e9))
        }
    }
    dimensions.forEach { visit(it, dwell = true, circuit = 0) }
    // Distinct orders for three or more destinations; every circuit visits the complete roster.
    val routes = listOf(dimensions.asReversed(), dimensions.drop(1) + dimensions.first(),
        dimensions.asReversed().drop(1) + dimensions.last())
    check(dimensions.size < 3 || routes.toSet().size == 3) { "Tele Soak circuit order was not varied" }
    routes.forEachIndexed { circuit, route ->
        driver.teleSoakEvent("circuit_started", mapOf("circuit" to circuit + 1, "route" to route))
        route.forEach { visit(it, dwell = false, circuit = circuit + 1) }
        driver.teleSoakEvent("circuit_completed", mapOf("circuit" to circuit + 1))
    }
    check(driver.registeredDimensions(::maintainConnection).toSet() == dimensions.toSet()) {
        "live dimension roster changed during Tele Soak"
    }
    driver.ensureConnected()
    driver.heartbeat()
    driver.teleSoakEvent("tele_soak_complete", mapOf("dimension_count" to dimensions.size, "circuits" to 3))
}
