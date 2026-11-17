package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.ExternalRawEvidenceDriver
import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.RawEvidenceRequest
import com.bettercontent.tests.suite.checkpoints.ExternalSamplingDriver
import com.bettercontent.tests.suite.checkpoints.SamplingIntent
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

/** Full raw-sample oracle; external sealed terrain/ledger ancestry is required separately. */
internal sealed class ScaleCapacityDriver(private val count: Int) : TrialCaseDriver, ExternalRawEvidenceDriver, ExternalSamplingDriver {
    override val caseId: String = "scale.$count"
    override val clientRequired = false // synthetic exact cohort; never smuggle a real observer into its roster
    override val requireFreshTerrain = true // independently verified sealed-input MCA regions before native launch
    override val assertionIds: Set<String> = setOf("$caseId.cohort-ticks", "$caseId.tps-recovery",
        "$caseId.sustained-600s", "$caseId.resource-trace", "$caseId.owned-cleanup")
    override fun plan(parameters: JsonNode): List<DriverStep> {
        checked(parameters)
        return ScaleConformanceControlDriver.plan(parameters)
    }
    private fun checked(parameters: JsonNode) {
        ScaleConformanceControlDriver.plan(parameters) // exact closed native target, site spacing, fixed distance
        require(parameters.path("cohort").asInt() == count &&
            parameters.path("capture_horizon_millis").asLong() == 810_000L &&
            parameters.path("ledger_sha256").asText() == "@sampling-ledger-sha256") {
            "full scale cohort requires exact size, preclaim-ledger binding and setup-inclusive 810s capture horizon"
        }
    }
    override fun expandTrials(parameters: JsonNode, inputCheckpointId: String): List<TrialRequest> {
        checked(parameters)
        require(Regex("[a-z0-9][a-z0-9_.-]*").matches(inputCheckpointId))
        return listOf(TrialRequest(caseId, "cohort_$count", "cohort_$count", inputCheckpointId,
            parameters.deepCopy(), assertionIds, emptySet()))
    }
    private fun validate(trial: TrialRequest) {
        require(trial.caseId == caseId && trial.trialId == "cohort_$count" && trial.targetId == "cohort_$count" &&
            trial.assertionIds == assertionIds && trial.provisionedTargets.isEmpty() &&
            Regex("[a-z0-9][a-z0-9_.-]*").matches(trial.inputCheckpointId)) { "foreign scale cohort trial" }
        checked(trial.parameters)
    }
    override fun samplingIntents(trial: TrialRequest): List<SamplingIntent> {
        validate(trial)
        val dimension = trial.parameters.path("dimension").asText()
        return trial.parameters.path("sites").map { site ->
            SamplingIntent.Site(dimension, site.path("x").asInt() shr 4, site.path("z").asInt() shr 4, 11)
        }
    }
    override fun rawEvidenceRequests(trial: TrialRequest): List<RawEvidenceRequest> {
        validate(trial)
        return listOf(RawEvidenceRequest("observe", "samples", "/samples_file", maximumBytes = 16 * 1024 * 1024,
            maximumRecords = 1000, maximumLineBytes = 128 * 1024))
    }
    override fun planTrial(request: TrialRequest): List<DriverStep> {
        validate(request)
        return plan(request.parameters)
    }
    private data class Sample(val tick: Long, val gameTime: Long, val nativeGameTickDelta: Long,
        val wallMillis: Long, val wallNanos: Long, val monotonicNanos: Long,
        val startWallNanos: Long, val setupCompletedNanos: Long, val setupCompletedTick: Long,
        val serverTickNanos: Long, val heap: Long, val gcCount: Long, val gcMillis: Long, val actors: List<JsonNode>)
    private fun sample(row: JsonNode, uuids: List<String>, sites: List<Pair<Int, Int>>): Sample {
        fun number(key: String): Long {
            val value = row.path(key); require(value.isIntegralNumber && value.canConvertToLong()) { "missing scale raw $key" }
            return value.asLong()
        }
        require(row.isObject && row.path("gc_metrics_complete").isBoolean && row.path("gc_metrics_complete").asBoolean() &&
            row.path("actors").isArray && row.path("actors").size() == count) { "scale raw actor/GC observation absent" }
        val actors = row.path("actors").toList()
        require(actors.zip(uuids.zip(sites)).all { (actor, identity) ->
            actor.path("uuid").isTextual && actor.path("uuid").asText() == identity.first &&
                actor.path("chunk_x").isIntegralNumber && actor.path("chunk_x").asInt() == identity.second.first &&
                actor.path("chunk_z").isIntegralNumber && actor.path("chunk_z").asInt() == identity.second.second &&
                actor.path("player_ticks").isIntegralNumber && actor.path("player_ticks").canConvertToLong() &&
                actor.path("native_chunk_ticks").isIntegralNumber && actor.path("native_chunk_ticks").canConvertToLong() &&
                actor.path("footprint_loaded_chunks").isIntegralNumber &&
                actor.path("footprint_loaded_chunks").canConvertToInt() &&
                actor.path("footprint_block_ticking_chunks").isIntegralNumber &&
                actor.path("footprint_block_ticking_chunks").canConvertToInt() &&
                actor.path("footprint_interval_masks_base64").isTextual &&
                actor.path("registered").isBoolean && actor.path("location_chunk_matches").isBoolean
        }) { "scale raw actor identity/footprint columns missing" }
        val out = Sample(number("tick"), number("game_time"), number("native_game_tick_delta"),
            number("observation_wall_millis"), number("end_tick_interval_nanos_includes_scheduler_wait"), number("monotonic_nanos"),
            number("start_wall_nanos"), number("setup_completed_wall_nanos"), number("setup_completed_tick"),
            number("server_tick_start_to_end_nanos"), number("heap_used"), number("gc_count_total"),
            number("gc_millis_total"), actors)
        require(out.nativeGameTickDelta in 1L..20L && out.wallNanos > 0 && out.serverTickNanos >= 0 &&
            out.setupCompletedNanos >= out.startWallNanos &&
            out.monotonicNanos > out.setupCompletedNanos && out.setupCompletedTick >= 0 &&
            out.actors.all { it.path("footprint_loaded_chunks").asLong() in 0L..529L &&
                it.path("footprint_block_ticking_chunks").asLong() in 0L..529L &&
                it.path("player_ticks").asLong() in 0L..20_040L &&
                it.path("native_chunk_ticks").asLong() in 0L..20_040L } &&
            out.heap >= 0 && out.gcCount >= 0 && out.gcMillis >= 0 && out.wallMillis >= 0) {
            "raw scale tick/heap/GC values invalid"
        }
        return out
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        checked(parameters); require(observations.size == 2)
        val control = ScaleConformanceControlDriver.evaluate(parameters, observations)
        require(observations[1].rawEvidence.isEmpty() && observations[0].rawEvidence.keys == setOf("samples")) {
            "scale raw samples missing or foreign sidecar attached"
        }
        val verified = observations[0].rawEvidence.getValue("samples")
        val pointer = observations[0].payload.path("samples_file")
        require(verified.file.sha256 == pointer.path("sha256").asText() &&
            verified.records.size == pointer.path("records").asInt() && verified.records.size in 1..1000) {
            "scale raw sidecar content/terminal hash/count mismatch"
        }
        // Even a failed measured-control verdict needs an authenticated last-sample anchor.
        // Do not turn missing or contradictory raw capture into an ordinary TPS failure.
        val lastRaw = verified.records.last()
        val terminal = observations[0].payload
        for ((rawKey, terminalKey) in listOf("monotonic_nanos" to "last_sample_wall_nanos",
            "tick" to "last_sample_tick")) {
            val actual = lastRaw.path(rawKey); val claimed = terminal.path(terminalKey)
            require(actual.isIntegralNumber && actual.canConvertToLong() &&
                claimed.isIntegralNumber && claimed.canConvertToLong() && actual.asLong() == claimed.asLong()) {
                "scale terminal differs from its authenticated last raw sample"
            }
        }
        if (control.state != "passed") return control
        require(observations[0].payload.path("latency_type").asText() == "local_server_tick_duration_not_network_latency" &&
            observations[0].payload.path("observer_overhead_unmeasured").isBoolean &&
            observations[0].payload.path("observer_overhead_unmeasured").asBoolean()) {
            "synthetic transport/observer limits not disclosed"
        }
        val begin = observations[0].payload
        require(begin.path("start_wall_nanos").isIntegralNumber && begin.path("setup_completed_wall_nanos").isIntegralNumber &&
            begin.path("setup_completed_tick").isIntegralNumber) { "setup-inclusive recovery anchors absent" }
        val uuids = begin.path("actors").map { it.path("uuid").asText() }
        require(uuids.distinct().size == count && uuids.all { runCatching { UUID.fromString(it).toString() }.getOrNull() == it })
        val siteRows = parameters.path("sites").toList()
        val sites = siteRows.map { it.path("x").asInt() shr 4 to (it.path("z").asInt() shr 4) }
        val raw = verified.records.map { sample(it, uuids, sites) }
        val start = begin.path("start_wall_nanos").asLong()
        val setup = begin.path("setup_completed_wall_nanos").asLong()
        val last = raw.last()
        val end = begin.path("end_wall_nanos").asLong()
        require(raw.size == begin.path("observed_samples").asInt() &&
            raw.first().wallNanos == raw.first().monotonicNanos - setup &&
            raw.first().tick - raw.first().setupCompletedTick in 1L..20L &&
            last.monotonicNanos == begin.path("last_sample_wall_nanos").asLong() &&
            last.tick == begin.path("last_sample_tick").asLong() &&
            last.monotonicNanos - start >= 810_000_000_000L &&
            // The first sampled tick crossing the horizon is the only valid terminal sample.
            raw.dropLast(1).all { it.monotonicNanos - start < 810_000_000_000L } &&
            end >= last.monotonicNanos && end - start <= 900_000_000_000L &&
            begin.path("terminal_partial_interval_nanos").asLong() == end - last.monotonicNanos &&
            begin.path("observation_wall_millis").asLong() == (end - setup) / 1_000_000L &&
            raw.all { it.startWallNanos == start && it.setupCompletedNanos == setup &&
                it.setupCompletedTick == begin.path("setup_completed_tick").asLong() &&
                it.wallMillis == (it.monotonicNanos - setup) / 1_000_000L } &&
            raw.zipWithNext().all { (a, b) ->
                b.tick - a.tick == 20L && b.nativeGameTickDelta == 20L &&
                    b.gameTime - a.gameTime == b.nativeGameTickDelta &&
                    b.wallMillis >= a.wallMillis && b.monotonicNanos > a.monotonicNanos &&
                b.wallNanos == b.monotonicNanos - a.monotonicNanos &&
                b.gcCount >= a.gcCount && b.gcMillis >= a.gcMillis &&
                    b.actors.zip(a.actors).all { (later, earlier) ->
                        later.path("player_ticks").asLong() >= earlier.path("player_ticks").asLong() &&
                            later.path("native_chunk_ticks").asLong() >= earlier.path("native_chunk_ticks").asLong()
                    }
            } && begin.path("actors").toList().zip(last.actors).zip(siteRows).all { (rows, site) ->
                scaleFootprintConforms(rows.first, site.path("x").asInt(), site.path("z").asInt(), raw.size, rows.second) &&
                    rows.first.path("player_ticks").asLong() == rows.second.path("player_ticks").asLong() &&
                    rows.first.path("native_chunk_ticks").asLong() == rows.second.path("native_chunk_ticks").asLong()
            }) { "scale raw series clock, full eligible footprint or GC counter discontinuity" }
        fun readyForRecovery(row: Sample): Boolean = row.actors.zip(siteRows).all { (actor, site) ->
            actor.path("registered").asBoolean() && actor.path("location_chunk_matches").asBoolean() &&
                scaleIntervalFootprintConforms(actor, site.path("x").asInt(), site.path("z").asInt())
        }
        // First *qualified* 30s recovery: fast TPS before all intended chunks are ready is
        // setup, not a recovery anchor. Once found, NEVER retry a later dwell to make it pass.
        val recovery = raw.indices.firstOrNull { candidate ->
            var nanos = 0L; var end = candidate
            while (end < raw.size && nanos < 30_000_000_000L) {
                val row = raw[end]
                if (row.nativeGameTickDelta * 1_000_000_000.0 / row.wallNanos < 19.0 ||
                    !readyForRecovery(row)) break
                nanos += row.wallNanos; end++
            }
            nanos >= 30_000_000_000L && raw[end - 1].monotonicNanos - start <= 120_000_000_000L
        }
        if (recovery == null) return MeasurementVerdict("failed", "no full-footprint continuous >=19 TPS 30s recovery within 120s")
        var recoverNanos = 0L; var cursor = recovery
        while (recoverNanos < 30_000_000_000L) recoverNanos += raw[cursor++].wallNanos
        val recoveredAt = raw[cursor - 1].monotonicNanos
        val recoveryEndWallMillis = (recoveredAt - raw.first().startWallNanos) / 1_000_000
        var dwellNanos = 0L; val windowRates = mutableListOf<Double>()
        repeat(20) {
            var nanos = 0L; var ticks = 0L
            while (nanos < 30_000_000_000L && cursor < raw.size) {
                nanos += raw[cursor].wallNanos; ticks += raw[cursor].nativeGameTickDelta; cursor++
            }
            if (nanos < 30_000_000_000L) return MeasurementVerdict("failed", "fewer than 20 full post-recovery 30s windows")
            val rate = ticks * 1_000_000_000.0 / nanos
            if (rate < 19.0) return MeasurementVerdict("failed", "post-recovery 30s TPS below 19")
            windowRates += rate; dwellNanos += nanos
        }
        if (dwellNanos < 600_000_000_000L || raw[cursor - 1].monotonicNanos - recoveredAt < 600_000_000_000L)
            return MeasurementVerdict("failed", "post-recovery 600s wallclock dwell not complete")
        val actorSeries = raw.take(cursor).drop(recovery).flatMap { it.actors }
        if (actorSeries.any { !it.path("registered").asBoolean() || !it.path("location_chunk_matches").asBoolean() })
            return MeasurementVerdict("failed", "synthetic actor stopped registered/located in claimed native site")
        if (raw.take(cursor).drop(recovery).any { row -> row.actors.zip(siteRows).any { (actor, site) ->
                !scaleIntervalFootprintConforms(actor, site.path("x").asInt(), site.path("z").asInt())
            } }) return MeasurementVerdict("failed", "view load or full engine-eligible tick footprint missing during accepted recovery/dwell")
        // Continuous center and independently decoded all-inner full-interval tick masks are both required.
        if (raw.drop(recovery + 1).take(cursor - recovery - 1).zip(raw.drop(recovery)).any { (later, earlier) ->
                later.actors.zip(earlier.actors).any { (a, b) ->
                    a.path("player_ticks").asLong() <= b.path("player_ticks").asLong() ||
                        a.path("native_chunk_ticks").asLong() <= b.path("native_chunk_ticks").asLong()
                }
            }) return MeasurementVerdict("failed", "cohort actor/center chunk did not tick throughout recovery and dwell")
        fun percentile(values: List<Long>, fraction: Double) = values[((values.size - 1) * fraction).toInt()]
        val durations = raw.map { it.serverTickNanos }.sorted()
        val heap = raw.map { it.heap }.sorted()
        val gcIntervals = raw.zipWithNext().map { (a, b) -> b.gcMillis - a.gcMillis }.sorted()
        return MeasurementVerdict("passed", "raw same-cohort recovery + sustained 600s, native actor/chunk ticks, heap/GC and cleanup independently checked; externally sealed fresh-site ledger still mandatory",
            mapOf("cohort" to count, "recovery_end_wall_ms_from_pre_join" to recoveryEndWallMillis,
                "dwell_wall_seconds" to dwellNanos / 1_000_000_000.0,
                "minimum_window_tps" to windowRates.min(), "maximum_heap_used_bytes" to heap.last(),
                "sampled_heap_used_bytes_p50" to percentile(heap, .50), "sampled_heap_used_bytes_p95" to percentile(heap, .95),
                "gc_count_delta" to raw.last().gcCount - raw.first().gcCount,
                "gc_millis_delta" to raw.last().gcMillis - raw.first().gcMillis,
                "sampled_gc_interval_millis_p95" to percentile(gcIntervals, .95),
                "sampled_server_tick_nanos_p50" to percentile(durations, .50),
                "sampled_server_tick_nanos_p95" to percentile(durations, .95)))
    }
    override fun evaluateTrial(request: TrialRequest, observations: List<DriverObservation>): MeasurementVerdict {
        validate(request)
        val failure = validateTerminalSequence(planTrial(request), observations)
        require(failure == null) { "scale source terminal failure: $failure" }
        return evaluate(request.parameters, observations)
    }
    override fun aggregate(parameters: JsonNode, requests: List<TrialRequest>, receipts: List<TrialReceipt>): MeasurementVerdict {
        require(requests.size == 1 && receipts.size == 1 && requests == expandTrials(parameters, requests.first().inputCheckpointId))
        val request = requests.single(); val receipt = receipts.single()
        require(receipt.trialId == request.trialId && receipt.targetId == request.targetId &&
            receipt.inputCheckpointId == request.inputCheckpointId && receipt.epochId.isNotBlank() &&
            receipt.inputCheckpointVerified && receipt.cleanupComplete)
        val verdict = evaluateTrial(request, receipt.observations)
        require(verdict.state == receipt.state) { "stored scale verdict differs from raw independent oracle" }
        return verdict
    }
}
internal object Scale8Driver : ScaleCapacityDriver(8)
internal object Scale16Driver : ScaleCapacityDriver(16)
internal object Scale32Driver : ScaleCapacityDriver(32)
