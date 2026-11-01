package com.bettercontent.tests

import java.time.Duration

/** Bounded, one-client Tele Soak protocol. Runtime probes must be real server/client observations. */
interface TeleSoakDriver : SessionDriver {
    /** The complete live ServerLevel registry, before applying the Tele Soak blacklist. */
    fun registeredDimensions(progress: () -> Unit): List<String>
    /** Confirm a live destination and spectator safety without generating its remote chunk. */
    fun prepareLanding(dimension: String, progress: () -> Unit)
    /** A fresh, unvisited chunk at least 10,000 blocks from the last landing in this dimension.
     * The disposable test player is a spectator; remote terrain is not pre-generated. */
    fun teleportTo(dimension: String)
    /** A live-client receipt during a hop; arrival is separately proven by a dimension-conditional marker. */
    fun heartbeatDuringArrival()
    /** Wait for both server-player dimension and a fresh receipt in the connected client; call heartbeat while waiting. */
    fun verifyArrival(dimension: String, progress: () -> Unit)
    /** Refuse a vanished player, changed dimension, or stale client receipt. */
    fun ensureInDimension(dimension: String)
    /** Actual server TPS measured over a current sample; no warmup trimming or fabricated 20 TPS. */
    fun currentTps(): Double
    fun teleSoakEvent(name: String, fields: Map<String, Any>)
}

private val SECOND = Duration.ofSeconds(1).toNanos()
// Target 12s client receipts; allow a bounded 30s delayed server console response.
// A gap >2s still resets the continuous TPS stability window.
private val HEARTBEAT = Duration.ofSeconds(12).toNanos()
private val DWELL = Duration.ofMinutes(5).toNanos()
private val RELOCATION_INTERVAL = Duration.ofSeconds(30).toNanos()
private const val DWELL_RELOCATIONS = 10
private val RECOVERY = Duration.ofSeconds(120).toNanos()
private val STABILITY = Duration.ofSeconds(30).toNanos()
private const val MIN_TPS = 19.0
private val DIMENSION_ID = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
internal val TELE_SOAK_DIMENSION_BLACKLIST = setOf("minecraft:the_end", "rats:ratlantis", "ae2:spatial_storage")

/** Every dimension has ten fresh 10k-block relocations during its responsive dwell.
 * Each relocation has its own 30s TPS stabilization within a 120s deadline; recovery
 * can extend the dwell, but no completed relocation can be omitted to shorten it. */
internal fun runTeleSoak(driver: TeleSoakDriver, clock: SessionClock, replay: DiagnosticReplay? = null) {
    var nextHeartbeat = clock.nanos() + HEARTBEAT
    fun maintainConnection() {
        driver.ensureConnected()
        if (clock.nanos() >= nextHeartbeat) {
            driver.heartbeat()
            nextHeartbeat = clock.nanos() + HEARTBEAT
        }
    }
    val registered = driver.registeredDimensions(::maintainConnection)
    require(registered.size in 1..64 && registered.size == registered.toSet().size &&
        "minecraft:overworld" in registered && registered.all(DIMENSION_ID::matches)) {
        "invalid or incomplete live dimension roster: $registered"
    }
    val dimensions = registered.filterNot { it in TELE_SOAK_DIMENSION_BLACKLIST }.sortedDescending().let { discovered ->
        replay?.roster?.let { previous ->
            require(previous.size == discovered.size && previous.toSet() == discovered.toSet()) {
                "diagnostic replay roster changed; skipped dimensions cannot be trusted"
            }
            previous
        } ?: discovered
    }
    driver.teleSoakEvent("dimension_roster", mapOf("dimensions" to dimensions, "count" to dimensions.size,
        "registered_dimensions" to registered.sorted(),
        "excluded_dimensions" to registered.filter { it in TELE_SOAK_DIMENSION_BLACKLIST }.sorted()))
    // Register destinations without generating or pre-loading remote target chunks.
    dimensions.forEach { driver.prepareLanding(it, ::maintainConnection) }
    fun tick(dimension: String) {
        driver.ensureConnected()
        // A delayed /forge tps result can outlive the prior 30s arrival receipt.
        // Refresh the server-conditional client receipt before enforcing its age;
        // the >2s TPS sample gap still resets stability independently.
        if (clock.nanos() >= nextHeartbeat) {
            driver.heartbeat()
            nextHeartbeat = clock.nanos() + HEARTBEAT
        }
        driver.ensureInDimension(dimension)
    }
    fun visit(dimension: String, circuit: Int, relocation: Int = 0) {
        driver.ensureConnected()
        val started = clock.nanos()
        val fields = mapOf("dimension" to dimension, "circuit" to circuit, "relocation" to relocation)
        driver.teleSoakEvent(if (relocation == 0) "hop_started" else "relocation_started", fields)
        driver.teleportTo(dimension)
        driver.verifyArrival(dimension) {
            driver.ensureConnected()
            if (clock.nanos() >= nextHeartbeat) {
                driver.heartbeatDuringArrival()
                nextHeartbeat = clock.nanos() + HEARTBEAT
            }
        }
        tick(dimension)
        val arrival = clock.nanos()
        driver.teleSoakEvent(if (relocation == 0) "arrival_verified" else "relocation_arrival_verified",
            fields + ("hop_seconds" to (arrival - started) / 1e9))
        var stableSince = -1L
        var priorSample = -1L
        val deadline = started + RECOVERY
        while (true) {
            tick(dimension)
            val tps = driver.currentTps()
            require(tps.isFinite() && tps >= 0.0 && tps <= 20.0001) { "invalid TPS sample for $dimension: $tps" }
            val at = clock.nanos()
            check(at <= deadline) { "TPS recovery deadline exceeded after hop to $dimension (relocation $relocation)" }
            driver.teleSoakEvent(if (relocation == 0) "tps_sample" else "relocation_tps_sample",
                fields + mapOf("elapsed_seconds" to (at - arrival) / 1e9, "tps" to tps))
            stableSince = if (tps >= MIN_TPS) {
                if (stableSince < 0 || (priorSample >= 0 && at - priorSample > 2 * SECOND)) at else stableSince
            } else -1L
            priorSample = at
            if (stableSince >= 0 && at - stableSince >= STABILITY) break
            check(at + SECOND <= deadline) {
                "TPS failed to recover to $MIN_TPS for 30 continuous seconds within 120 seconds in $dimension (relocation $relocation)"
            }
            clock.sleep(Duration.ofSeconds(1))
        }
        driver.teleSoakEvent(if (relocation == 0) "tps_stable" else "relocation_tps_stable",
            fields + ("recovery_seconds" to (clock.nanos() - arrival) / 1e9))
    }
    fun dwell(dimension: String) {
        visit(dimension, circuit = 0)
        val from = clock.nanos()
        driver.teleSoakEvent("dwell_started", mapOf("dimension" to dimension, "required_seconds" to 300,
            "required_fresh_relocations" to DWELL_RELOCATIONS))
        (1..DWELL_RELOCATIONS).forEach { relocation ->
            val next = from + relocation * RELOCATION_INTERVAL
            while (clock.nanos() < next) {
                tick(dimension)
                val remaining = next - clock.nanos()
                if (remaining > 0) clock.sleep(Duration.ofNanos(minOf(SECOND, remaining)))
            }
            visit(dimension, circuit = 0, relocation = relocation)
        }
        tick(dimension)
        check(clock.nanos() - from >= DWELL) { "ten relocations did not cover the five-minute dwell" }
        driver.teleSoakEvent("dwell_completed", mapOf("dimension" to dimension,
            "actual_seconds" to (clock.nanos() - from) / 1e9, "fresh_relocations" to DWELL_RELOCATIONS))
    }
    val cursor = replay?.failedHop
    if (cursor != null) driver.teleSoakEvent("diagnostic_checkpoint", mapOf(
        "source_run_id" to replay.sourceRunId, "dimension" to cursor.dimension,
        "circuit" to cursor.circuit, "acceptance_eligible" to false))
    val dwellRoute = if (cursor != null && cursor.circuit > 0) emptyList() else
        cursor?.let { dimensions.dropWhile { dimension -> dimension != it.dimension } } ?: dimensions
    check(cursor == null || cursor.circuit > 0 || dwellRoute.isNotEmpty()) { "missing diagnostic dwell checkpoint" }
    dwellRoute.forEach(::dwell)
    // Distinct orders for three or more destinations; every circuit visits the complete roster.
    val routes = listOf(dimensions.asReversed(), dimensions.drop(1) + dimensions.first(),
        dimensions.asReversed().drop(1) + dimensions.last())
    check(dimensions.size < 3 || routes.toSet().size == 3) { "Tele Soak circuit order was not varied" }
    routes.forEachIndexed { circuit, route ->
        val circuitIndex = circuit + 1
        if (cursor != null && circuitIndex < cursor.circuit) return@forEachIndexed
        val segment = if (cursor?.circuit == circuitIndex) route.dropWhile { it != cursor.dimension } else route
        check(segment.isNotEmpty()) { "missing diagnostic circuit checkpoint" }
        driver.teleSoakEvent("circuit_started", mapOf("circuit" to circuitIndex, "route" to route,
            "diagnostic_segment" to (replay != null && segment != route)))
        segment.forEach { dimension ->
            visit(dimension, circuit = circuitIndex)
            visit(dimension, circuit = circuitIndex, relocation = 1)
        }
        driver.teleSoakEvent(if (replay == null) "circuit_completed" else "diagnostic_circuit_segment_completed",
            mapOf("circuit" to circuitIndex, "visited" to segment))
    }
    check(driver.registeredDimensions(::maintainConnection).toSet() == registered.toSet()) {
        "live dimension roster changed during Tele Soak"
    }
    driver.ensureConnected()
    driver.heartbeat()
    driver.teleSoakEvent(if (replay == null) "tele_soak_complete" else "diagnostic_itinerary_complete",
        mapOf("dimension_count" to dimensions.size, "circuits" to 3))
}
