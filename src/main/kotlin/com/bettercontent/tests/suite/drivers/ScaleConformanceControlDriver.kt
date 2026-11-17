package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.checkpoints.ExternalSamplingDriver
import com.bettercontent.tests.suite.checkpoints.SamplingIntent
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.UUID

/** Production receipt clock predicate; the shared native serializer fixture also exercises it. */
internal fun scaleReceiptClockConforms(begin: JsonNode): Boolean {
    val fields = listOf("scale_contract_version", "capture_horizon_millis", "start_wall_nanos",
        "setup_completed_wall_nanos", "setup_completed_tick", "setup_wall_nanos", "last_sample_wall_nanos",
        "last_sample_tick", "end_wall_nanos", "terminal_partial_interval_nanos", "observed_samples",
        "observation_wall_millis")
    if (!begin.isObject || fields.any { !begin.path(it).isIntegralNumber || !begin.path(it).canConvertToLong() } ||
        !begin.path("observed_samples").canConvertToInt()) return false
    val start = begin.path("start_wall_nanos").asLong()
    val setup = begin.path("setup_completed_wall_nanos").asLong()
    val last = begin.path("last_sample_wall_nanos").asLong()
    val end = begin.path("end_wall_nanos").asLong()
    return begin.path("scale_contract_version").asLong() == 2L &&
        begin.path("capture_horizon_millis").asLong() == 810_000L &&
        begin.path("receipt_order").isTextual && begin.path("receipt_order").asText() == "sample_then_receipt" &&
        begin.path("observed_samples").asInt() in 1..1000 &&
        setup >= start && last > setup && end >= last &&
        begin.path("setup_wall_nanos").asLong() == setup - start &&
        last - start >= 810_000_000_000L && end - start <= 900_000_000_000L &&
        begin.path("terminal_partial_interval_nanos").asLong() == end - last &&
        begin.path("observation_wall_millis").asLong() == (end - setup) / 1_000_000L &&
        begin.path("last_sample_tick").asLong() > begin.path("setup_completed_tick").asLong()
}

/** Native continuous-cohort conformance control; no raw-sidecar replay, real login or fresh-terrain proof. */
internal object ScaleConformanceControlDriver : TrialCaseDriver, ExternalSamplingDriver {
    override val caseId = "control.scale.native-conformance"
    override val clientRequired = false
    private data class Site(val x: Int, val y: Int, val z: Int) { val cx get() = x shr 4; val cz get() = z shr 4 }
    private data class Target(val cohort: Int, val dimension: String, val ledger: String, val sites: List<Site>)
    private fun parse(node: JsonNode): Target {
        require(node.isObject && node.fieldNames().asSequence().toSet() == setOf("cohort", "dimension", "view_distance",
            "simulation_distance", "capture_horizon_millis", "sites", "ledger_sha256")) { "scale conformance target fields invalid" }
        val count = node.path("cohort"); val horizon = node.path("capture_horizon_millis")
        require(count.isIntegralNumber && count.asInt() in setOf(8, 16, 32) &&
            horizon.isIntegralNumber && horizon.canConvertToLong() && horizon.asLong() == 810_000L &&
            node.path("view_distance").isIntegralNumber && node.path("view_distance").asInt() == 10 &&
            node.path("simulation_distance").isIntegralNumber && node.path("simulation_distance").asInt() == 10) {
            "only fixed 8/16/32 cohort and production distance ten permitted"
        }
        val dimension = node.path("dimension"); val ledger = node.path("ledger_sha256")
        require(dimension.isTextual && Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(dimension.asText()) &&
            ledger.isTextual && (Regex("[0-9a-f]{64}").matches(ledger.asText()) ||
                ledger.asText() == "@sampling-ledger-sha256")) { "external site ledger binding required" }
        val rows = node.path("sites"); require(rows.isArray && rows.size() == count.asInt()) { "exact site count required" }
        val sites = mutableListOf<Site>()
        rows.forEach { site ->
            require(site.isObject && site.fieldNames().asSequence().toSet() == setOf("x", "y", "z") &&
                listOf("x", "y", "z").all { site.path(it).isIntegralNumber && site.path(it).canConvertToInt() }) {
                "invalid scale footprint coordinate"
            }
            val point = Site(site.path("x").asInt(), site.path("y").asInt(), site.path("z").asInt())
            require(point.x in -30_000_000..30_000_000 && point.z in -30_000_000..30_000_000 && point.y in -2048..2048)
            require(sitesFarEnough(point, sites)) { "scale sites overlap native simulation footprints" }
            sites += point
        }
        return Target(count.asInt(), dimension.asText(), ledger.asText(), sites)
    }
    private fun sitesFarEnough(next: Site, prior: List<Site>): Boolean = prior.all {
        kotlin.math.abs(next.cx.toLong() - it.cx) >= 32 || kotlin.math.abs(next.cz.toLong() - it.cz) >= 32
    }
    override fun samplingIntents(trial: TrialRequest): List<SamplingIntent> {
        val t = trialTarget(trial)
        return t.sites.map { SamplingIntent.Site(t.dimension, it.cx, it.cz, 11) }
    }
    override fun expandTrials(parameters: JsonNode, inputCheckpointId: String): List<TrialRequest> {
        val t = parse(parameters)
        require(Regex("[a-z0-9][a-z0-9_.-]*").matches(inputCheckpointId))
        return listOf(TrialRequest(caseId, "scale_${t.cohort}", "cohort_${t.cohort}", inputCheckpointId,
            parameters.deepCopy(), assertionIds, emptySet()))
    }
    private fun trialTarget(request: TrialRequest): Target {
        require(request.caseId == caseId && request.assertionIds.isEmpty() && request.provisionedTargets.isEmpty() &&
            Regex("[a-z0-9][a-z0-9_.-]*").matches(request.inputCheckpointId)) { "invalid scale trial identity" }
        val t = parse(request.parameters)
        require(request.trialId == "scale_${t.cohort}" && request.targetId == "cohort_${t.cohort}") { "scale cohort trial mismatch" }
        return t
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        parse(parameters)
        return listOf(
            DriverStep("observe", DriverAction("scale.begin", parameters.deepCopy<ObjectNode>(), 20_040)),
            DriverStep("cleanup", DriverAction("scale.cleanup", suiteMapper.createObjectNode(), 2400))
        )
    }
    override fun planTrial(request: TrialRequest): List<DriverStep> {
        trialTarget(request)
        return plan(request.parameters)
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val t = parse(parameters); require(observations.size == 2)
        val begin = observations[0].payload; val cleanup = observations[1].payload
        require(begin.path("cohort").isIntegralNumber && begin.path("roster_size").isIntegralNumber &&
            begin.path("simulation_distance").isIntegralNumber && begin.path("view_distance").isIntegralNumber &&
            begin.path("scale_contract_version").isIntegralNumber &&
            begin.path("capture_horizon_millis").isIntegralNumber &&
            begin.path("receipt_order").isTextual &&
            begin.path("start_wall_nanos").isIntegralNumber &&
            begin.path("setup_completed_wall_nanos").isIntegralNumber &&
            begin.path("last_sample_wall_nanos").isIntegralNumber &&
            begin.path("last_sample_tick").isIntegralNumber &&
            begin.path("setup_completed_tick").isIntegralNumber &&
            begin.path("setup_wall_nanos").isIntegralNumber &&
            begin.path("end_wall_nanos").isIntegralNumber &&
            begin.path("terminal_partial_interval_nanos").isIntegralNumber &&
            begin.path("observed_samples").isIntegralNumber &&
            begin.path("observation_wall_millis").isIntegralNumber && begin.path("tps_30s_windows").isArray &&
            begin.path("site_ledger_sha256").isTextual && begin.path("site_ledger_verification").isTextual &&
            begin.path("terrain_freshness_proven_by_native").isBoolean && begin.path("actor_type").isTextual &&
            begin.path("tick_observer").isTextual && begin.path("server_tick_timing_complete").isBoolean &&
            begin.path("gc_metrics_complete").isBoolean && begin.path("heap_metrics_complete").isBoolean &&
            begin.path("tps_threshold_19_met_is_diagnostic_only").isBoolean && begin.path("tps_adjudication").isTextual &&
            begin.path("real_login_proven").isBoolean &&
            begin.path("fakeplayer_factory_used").isBoolean && begin.path("actors").isArray &&
            begin.path("samples_file").path("sha256").isTextual && begin.path("samples_file").path("path").isTextual &&
            begin.path("samples_file").path("records").isIntegralNumber &&
            cleanup.path("released").isIntegralNumber && cleanup.path("released_uuids").isArray &&
            cleanup.path("owned_residuals").isIntegralNumber && cleanup.path("cleanup_complete").isBoolean &&
            cleanup.path("cleanup_within_120_seconds").isBoolean &&
            cleanup.path("cleanup_wall_millis_from_cleanup_begin").isIntegralNumber &&
            cleanup.path("session_wall_millis_from_cohort_begin").isIntegralNumber && cleanup.path("baseline_restored").isBoolean) {
            "native cohort actor/sampling/cleanup receipts missing"
        }
        val actors = begin.path("actors").toList()
        require(actors.size == t.cohort && actors.all { row ->
            row.path("uuid").isTextual && runCatching { UUID.fromString(row.path("uuid").asText()).toString() }.getOrNull() == row.path("uuid").asText() &&
                row.path("site_x").isIntegralNumber && row.path("site_y").isIntegralNumber && row.path("site_z").isIntegralNumber &&
                row.path("chunk_x").isIntegralNumber && row.path("chunk_z").isIntegralNumber &&
                row.path("player_ticks").isIntegralNumber && row.path("native_chunk_ticks").isIntegralNumber &&
                row.path("footprint_chunk_tick_events").isIntegralNumber &&
                row.path("footprint_view_tuple_schema").isTextual && row.path("footprint_view_chunks").isArray &&
                row.path("footprint_geometric_chunks_count").isIntegralNumber &&
                row.path("footprint_tick_eligible_chunks").isIntegralNumber &&
                row.path("footprint_tick_observed_chunks").isIntegralNumber &&
                row.path("footprint_distinct_chunks_ticked").isIntegralNumber &&
                row.path("footprint_loaded_chunks").isIntegralNumber &&
                row.path("footprint_block_ticking_chunks").isIntegralNumber &&
                listOf("missing_view_loaded_chunks", "missing_inner_entity_ticking_chunks",
                    "missing_inner_block_ticking_chunks", "missing_inner_natural_spawning_chunks",
                    "missing_inner_tick_events_chunks", "missing_eligible_tick_events_chunks").all {
                    row.path(it).isIntegralNumber && row.path(it).canConvertToInt() && row.path(it).asInt() == 0
                } && row.path("footprint_unsupported_condition").isMissingNode &&
                row.path("synthetic_keepalive_acks").isIntegralNumber && row.path("synthetic_teleport_acks").isIntegralNumber &&
                row.path("synthetic_ack_queue_overflow").isBoolean &&
                row.path("registered").isBoolean &&
                row.path("location_chunk_matches").isBoolean && row.path("transport_active").isBoolean
        }) { "native actor identity/tick/footprint rows missing" }
        val uuids = actors.map { it.path("uuid").asText() }
        val windows = begin.path("tps_30s_windows").toList()
        require(windows.size in 1..30 && windows.all { row ->
            row.path("wall_nanos").isIntegralNumber && row.path("game_ticks").isIntegralNumber &&
                row.path("tps").isNumber && row.path("tps").asDouble().isFinite() &&
                row.path("end_sample_tick").isIntegralNumber
        }) { "native thirty-second window observations missing" }
        val windowsValid = windows.all { row ->
            val wall = row.path("wall_nanos").asLong(); val ticks = row.path("game_ticks").asLong()
            wall >= 30_000_000_000L && ticks in 0L..20_000L &&
                kotlin.math.abs(row.path("tps").asDouble() - ticks * 1_000_000_000.0 / wall) <= 0.001
        } && windows.zipWithNext().all { (a, b) ->
            b.path("end_sample_tick").asLong() - a.path("end_sample_tick").asLong() ==
                b.path("game_ticks").asLong()
        } && windows.sumOf { it.path("wall_nanos").asLong() } >= 600_000_000_000L
        val observedSamples = begin.path("observed_samples").asInt()
        val start = begin.path("start_wall_nanos").asLong()
        val setup = begin.path("setup_completed_wall_nanos").asLong()
        val last = begin.path("last_sample_wall_nanos").asLong()
        val end = begin.path("end_wall_nanos").asLong()
        val anchorsValid = scaleReceiptClockConforms(begin)
        val matched = anchorsValid && windowsValid &&
            windows.sumOf { it.path("wall_nanos").asLong() } <= last - setup &&
            windows.sumOf { it.path("game_ticks").asLong() } <= observedSamples * 20L &&
            windows.last().path("end_sample_tick").asLong() <= begin.path("last_sample_tick").asLong() &&
            actors.zip(t.sites).all { (actor, site) ->
            actor.path("site_x").asInt() == site.x && actor.path("site_y").asInt() == site.y &&
                actor.path("site_z").asInt() == site.z && actor.path("chunk_x").asInt() == site.cx &&
                actor.path("chunk_z").asInt() == site.cz && actor.path("player_ticks").asInt() >= observedSamples * 10 &&
                actor.path("native_chunk_ticks").asInt() >= observedSamples * 10 &&
                scaleFootprintConforms(actor, site.x, site.z, observedSamples) &&
                actor.path("footprint_chunk_tick_events").asInt() >= actor.path("native_chunk_ticks").asInt() &&
                actor.path("registered").asBoolean() && actor.path("location_chunk_matches").asBoolean() &&
                actor.path("transport_active").asBoolean() &&
                actor.path("synthetic_keepalive_acks").asInt() > 0 && actor.path("synthetic_teleport_acks").asInt() > 0 &&
                !actor.path("synthetic_ack_queue_overflow").asBoolean()
        } && uuids.distinct().size == t.cohort && begin.path("cohort").asInt() == t.cohort &&
            begin.path("roster_size").asInt() == t.cohort &&
            begin.path("observation_wall_millis").asLong() >= 600_000 &&
            begin.path("simulation_distance").asInt() == 10 && begin.path("view_distance").asInt() == 10 &&
            (if (t.ledger == "@sampling-ledger-sha256")
                Regex("[0-9a-f]{64}").matches(begin.path("site_ledger_sha256").asText())
            else begin.path("site_ledger_sha256").asText() == t.ledger) &&
            begin.path("site_ledger_verification").asText() == "external_unverified_by_native" &&
            !begin.path("terrain_freshness_proven_by_native").asBoolean() &&
            begin.path("actor_type").asText() == "synthetic_registered_ServerPlayer_embedded_transport_NOT_real_login" &&
            begin.path("tick_observer").asText() == "required_ServerLevel.tickChunk_HEAD_native_mixin" &&
            begin.path("server_tick_timing_complete").asBoolean() && begin.path("heap_metrics_complete").asBoolean() &&
            begin.path("gc_metrics_complete").asBoolean() &&
            begin.path("tps_threshold_19_met_is_diagnostic_only").asBoolean() &&
            begin.path("tps_adjudication").asText() == "external_setup_inclusive_recovery_then_sustained_dwell_required" &&
            !begin.path("real_login_proven").asBoolean() && !begin.path("fakeplayer_factory_used").asBoolean() &&
            begin.path("samples_file").path("records").asInt() == observedSamples &&
            Regex("[0-9a-f]{64}").matches(begin.path("samples_file").path("sha256").asText()) &&
            cleanup.path("released").asInt() == t.cohort && cleanup.path("released_uuids").map { it.asText() }.toSet() == uuids.toSet() &&
            cleanup.path("owned_residuals").asInt() == 0 && cleanup.path("cleanup_complete").asBoolean() &&
            cleanup.path("cleanup_within_120_seconds").asBoolean() &&
            cleanup.path("cleanup_wall_millis_from_cleanup_begin").asLong() in 0L..120_000L &&
            cleanup.path("session_wall_millis_from_cohort_begin").asLong() >=
                (end - start) / 1_000_000L + cleanup.path("cleanup_wall_millis_from_cleanup_begin").asLong() - 1L &&
            !cleanup.path("baseline_restored").asBoolean()
        return MeasurementVerdict(if (matched) "passed" else "failed",
            "native synthetic cohort receipt ONLY; raw 600-second tick/heap/GC sidecar, external fresh-site ledger and real-client proof absent")
    }
    override fun evaluateTrial(request: TrialRequest, observations: List<DriverObservation>): MeasurementVerdict {
        trialTarget(request)
        val problem = validateTerminalSequence(planTrial(request), observations)
        require(problem == null) { "scale native terminal failure: $problem" }
        return evaluate(request.parameters, observations)
    }
    override fun aggregate(parameters: JsonNode, requests: List<TrialRequest>, receipts: List<TrialReceipt>): MeasurementVerdict {
        require(requests.size == 1 && receipts.size == 1 && requests == expandTrials(parameters, requests.first().inputCheckpointId))
        val receipt = receipts.single(); val request = requests.single()
        require(receipt.trialId == request.trialId && receipt.targetId == request.targetId &&
            receipt.inputCheckpointId == request.inputCheckpointId && receipt.epochId.isNotBlank() &&
            receipt.inputCheckpointVerified && receipt.cleanupComplete)
        val result = evaluateTrial(request, receipt.observations)
        require(receipt.state == result.state)
        return result
    }
}
