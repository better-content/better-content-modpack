package com.bettercontent.tests.release

import com.bettercontent.tests.CandidatePair
import com.bettercontent.tests.Hashes
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/** Independent raw-evidence gate: a one-hour Overworld run cannot inherit full-roster Tele Soak claims. */
internal fun validateOverworldTeleSoakReceipt(path: Path, runId: String, pair: CandidatePair, started: Instant) {
    require(Files.isRegularFile(path) &&
        !Files.getLastModifiedTime(path).toInstant().isBefore(started.truncatedTo(java.time.temporal.ChronoUnit.SECONDS))) {
        "missing or stale Overworld Tele Soak receipt"
    }
    val mapper = jacksonObjectMapper()
    val receipt = mapper.readTree(path.toFile())
    require(receipt.path("run_id").asText() == runId && receipt.path("tier").asText() == "overworld-tele-soak" &&
        receipt.path("status").asText() == "passed" && receipt.path("scenario_completed").asBoolean() &&
        receipt.path("dwell_seconds_per_dimension").asInt() == 3600 && receipt.path("circuits").asInt() == 0 &&
        receipt.path("registered_dimensions").map { it.asText() } == listOf("minecraft:overworld") &&
        receipt.path("dimension_results").size() == 1) { "incomplete one-hour Overworld receipt" }
    val result = receipt.path("dimension_results")[0]
    require(result.path("dimension").asText() == "minecraft:overworld" &&
        result.path("dwell_seconds").asDouble(Double.NaN).let { it.isFinite() && it >= 3600.0 } &&
        result.path("completed_circuits").isEmpty && result.path("tps_verified").asBoolean() &&
        result.path("fresh_relocations_verified").asBoolean()) { "Overworld dwell or remote proof missing" }
    val eventsPath = path.resolveSibling("events.jsonl")
    require(Files.isRegularFile(eventsPath) && receipt.path("events_sha256").asText() == Hashes.sha256(eventsPath)) {
        "Overworld receipt does not bind raw events"
    }
    require(receipt.path("candidates") == mapper.valueToTree<JsonNode>(pair.document())) { "candidate pair differs" }
    requireStableCandidates(pair)
    validateOverworldTeleSoakEvents(eventsPath)
}

internal fun validateOverworldTeleSoakEvents(path: Path) {
    val mapper = jacksonObjectMapper()
    val events = Files.readAllLines(path).filter(String::isNotBlank).map(mapper::readTree)
    fun one(type: String, ordinal: Int? = null): Int = events.indices.singleOrNull { index ->
        events[index].path("type").asText() == type &&
            (ordinal == null || events[index].path("relocation").asInt(-1) == ordinal)
    } ?: error("missing/duplicate Overworld Tele Soak $type ($ordinal)")
    val roster = events[one("dimension_roster")]
    val registered = roster.path("registered_dimensions").map { it.asText() }
    require(roster.path("dimensions").map { it.asText() } == listOf("minecraft:overworld") &&
        registered.size in 1..64 && registered.size == registered.toSet().size &&
        "minecraft:overworld" in registered && registered.all { Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(it) } &&
        roster.path("excluded_dimensions").map { it.asText() }.toSet() == registered.toSet() - "minecraft:overworld") {
        "incomplete or misrepresented live roster"
    }
    validateTeleClientEvidence(events, "overworld_tele_soak_complete")
    val hop = one("hop_started"); val arrival = one("arrival_verified"); val stable = one("tps_stable")
    val dwellStart = one("dwell_started"); val dwellEnd = one("dwell_completed")
    val complete = one("overworld_tele_soak_complete")
    require(hop < arrival && arrival < stable && stable < dwellStart && dwellStart < dwellEnd && dwellEnd < complete &&
        events.none { it.path("type").asText() in setOf("circuit_started", "circuit_completed", "tele_soak_complete") } &&
        events[dwellStart].path("required_seconds").asLong() == 3600L &&
        events[dwellStart].path("required_fresh_relocations").asInt() == 10 &&
        events[dwellEnd].path("fresh_relocations").asInt() == 10 &&
        events[dwellEnd].path("actual_seconds").asDouble(Double.NaN).let { it.isFinite() && it >= 3600.0 } &&
        Duration.between(Instant.parse(events[dwellStart].path("time").asText()),
            Instant.parse(events[dwellEnd].path("time").asText())) >= Duration.ofHours(1) &&
        events[complete].path("dimension_count").asInt() == 1 &&
        events[complete].path("dwell_seconds").asInt() == 3600 &&
        events[complete].path("fresh_relocations").asInt() == 10) { "Overworld one-hour dwell was not completed" }
    val landings = events.indices.filter { events[it].path("type").asText() == "landing_verified" }
    require(landings.size == 11) { "Overworld Tele Soak requires 11 fresh landings" }
    val visited = mutableSetOf<Pair<Int, Int>>()
    var previous: Pair<Double, Double>? = null
    landings.forEach { index ->
        val e = events[index]; val x = e.path("x").asDouble(Double.NaN); val z = e.path("z").asDouble(Double.NaN)
        val chunk = e.path("chunk_x").asInt(Int.MIN_VALUE) to e.path("chunk_z").asInt(Int.MIN_VALUE)
        val distance = previous?.let { hypot(x - it.first, z - it.second) }
        require(e.path("dimension").asText() == "minecraft:overworld" && e.path("novel_chunk").asBoolean() &&
            e.path("route").asText() == "remote_ungenerated_chunk" && x.isFinite() && z.isFinite() &&
            chunk == (floor(x / 16).toInt() to floor(z / 16).toInt()) && visited.add(chunk) &&
            (distance == null || distance >= 9999.0 &&
                abs(distance - e.path("distance_from_previous").asDouble(Double.NaN)) <= 1.0)) {
            "reused, near or unproven Overworld landing"
        }
        previous = x to z
    }
    fun recovery(start: Int, arrived: Int, done: Int, sampleType: String, dimension: String, ordinal: Int) {
        require(start < arrived && arrived < done && events[start].path("dimension").asText() == dimension &&
            events[arrived].path("dimension").asText() == dimension &&
            events[done].path("dimension").asText() == dimension &&
            listOf(start, arrived, done).all { events[it].path("circuit").asInt(-1) == 0 &&
                events[it].path("relocation").asInt(-1) == ordinal } &&
            landings.count { it in start + 1 until arrived } == 1 &&
            (start + 1 until arrived).count { events[it].path("type").asText() == "client_dimension_receipt" &&
                events[it].path("server_conditional").asBoolean() &&
                events[it].path("server_position_conditional").asBoolean() &&
                events[it].path("fresh_client_log_offset").asLong(-1) >= 0 } == 1) { "arrival lacks fresh server/client coordinates" }
        val hopSeconds = events[arrived].path("hop_seconds").asDouble(Double.NaN)
        val recoverySeconds = events[done].path("recovery_seconds").asDouble(Double.NaN)
        val observedHop = Duration.between(Instant.parse(events[start].path("time").asText()),
            Instant.parse(events[arrived].path("time").asText())).toMillis() / 1000.0
        val observedRecovery = Duration.between(Instant.parse(events[arrived].path("time").asText()),
            Instant.parse(events[done].path("time").asText())).toMillis() / 1000.0
        require(hopSeconds.isFinite() && recoverySeconds.isFinite() && hopSeconds >= 0 && recoverySeconds >= 30 &&
            hopSeconds + recoverySeconds <= 120.0 &&
            abs(observedHop - hopSeconds) <= 1.0 && abs(observedRecovery - recoverySeconds) <= 1.0) {
            "arrival or TPS recovery exceeded two minutes or disagrees with raw timestamps"
        }
        val samples = (arrived + 1 until done).map { events[it] }.filter { it.path("type").asText() == sampleType }
        require(samples.size >= 2) { "missing actual TPS samples" }
        var last = -1.0; var goodSince = Double.NaN
        samples.forEach { sample ->
            val elapsed = sample.path("elapsed_seconds").asDouble(Double.NaN)
            val tps = sample.path("tps").asDouble(Double.NaN)
            val observed = Duration.between(Instant.parse(events[arrived].path("time").asText()),
                Instant.parse(sample.path("time").asText())).toMillis() / 1000.0
            require(sample.path("dimension").asText() == dimension && sample.path("circuit").asInt(-1) == 0 &&
                sample.path("relocation").asInt(-1) == ordinal &&
                elapsed.isFinite() && elapsed > last && elapsed in 0.0..recoverySeconds &&
                abs(observed - elapsed) <= 1.0 && tps.isFinite() && tps in 0.0..20.0001) {
                "invalid TPS sample"
            }
            goodSince = if (tps >= 19.0) (if (goodSince.isNaN() || last >= 0 && elapsed - last > 2.0) elapsed else goodSince) else Double.NaN
            last = elapsed
        }
        require(goodSince.isFinite() && last - goodSince >= 30.0 && recoverySeconds - last <= 1.0) {
            "TPS did not sustain >=19 for 30 continuous seconds within 120 seconds"
        }
    }
    recovery(hop, arrival, stable, "tps_sample", "minecraft:overworld", 0)
    var prior = dwellStart
    var priorStable = stable
    (1..10).forEach { ordinal ->
        val start = one("relocation_started", ordinal)
        val arrived = one("relocation_arrival_verified", ordinal)
        val done = one("relocation_tps_stable", ordinal)
        require(priorStable < start && start < arrived && arrived < done && done < dwellEnd &&
            Duration.between(Instant.parse(events[prior].path("time").asText()),
                Instant.parse(events[start].path("time").asText())) >= Duration.ofSeconds(359)) {
            "Overworld relocations were omitted, reordered or too close together"
        }
        recovery(start, arrived, done, "relocation_tps_sample", "minecraft:overworld", ordinal)
        prior = start; priorStable = done
    }
    require(events.count { it.path("type").asText() == "relocation_started" } == 10 &&
        events.count { it.path("type").asText() == "relocation_tps_stable" } == 10 &&
        landings.first() in hop + 1 until arrival &&
        (1..10).all { ordinal ->
            val start = one("relocation_started", ordinal); val arrived = one("relocation_arrival_verified", ordinal)
            landings.count { it in start + 1 until arrived } == 1
        }) { "unexpected or missing relocation/landing" }
    val disconnected = one("client_disconnected"); val saved = one("server_saved_and_stopped")
    val validated = one("validated"); val cleanup = one("process_cleanup"); val finished = one("session_finished")
    require(complete < disconnected && disconnected < saved && saved < validated && validated < cleanup && cleanup < finished &&
        events[validated].path("candidate_hashes_unchanged").asBoolean() &&
        events[cleanup].path("complete").asBoolean() && events[cleanup].path("surviving_pids").isArray &&
        events[cleanup].path("surviving_pids").isEmpty && events[finished].path("success").asBoolean()) {
        "Overworld Tele Soak lacks clean save, unchanged ZIPs or complete process cleanup"
    }
}
