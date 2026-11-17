package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.VerifiedRawEvidence
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Source-only v2 clock/footprint oracles: synthetic fixtures are NOT engine measurements. */
class ScaleCapacityDriverTest {
    private val sha = "d".repeat(64)
    private val sites = (0 until 8).map { it * 512 }
    private val ids = (0 until 8).map { "123e4567-e89b-42d3-a456-${(426614174000L + it).toString().padStart(12, '0')}" }
    private val start = 100_000_000_000L
    private val setup = 101_000_000_000L
    private val schema = "chunk_x,chunk_z,loaded_samples,block_ticking_samples,entity_ticking_samples," +
        "natural_spawning_samples,tick_eligible_samples,tick_events,spawn_proximity_expected_0_or_1"
    private fun target() = suiteMapper.readTree("""{"cohort":8,"dimension":"minecraft:overworld",
      "view_distance":10,"simulation_distance":10,"capture_horizon_millis":810000,
      "ledger_sha256":"@sampling-ledger-sha256",
      "sites":[${sites.joinToString(",") { """{"x":$it,"y":70,"z":0}""" }}]}""")
    private fun view(site: Int): List<Pair<Int, Int>> = (-11..11).flatMap { dx -> (-11..11).mapNotNull { dz ->
        val cx = (site shr 4) + dx
        if (scaleViewGeometry(cx, dz, site shr 4, 0)) cx to dz else null
    } }.sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
    private fun inner(site: Int) = view(site).count { (cx, cz) -> scaleSpawnProximity(cx, cz, site, 0) }
    private fun intervalMask(site: Int, withFullTicks: Boolean = true): String {
        val view = view(site); val length = (view.size + 7) / 8
        val bytes = ByteArray(length * 2)
        for ((i, chunk) in view.withIndex()) if (scaleSpawnProximity(chunk.first, chunk.second, site, 0)) {
            bytes[i shr 3] = (bytes[i shr 3].toInt() or (1 shl (i and 7))).toByte()
            if (withFullTicks || chunk == ((site shr 4) to 0))
                bytes[length + (i shr 3)] = (bytes[length + (i shr 3)].toInt() or (1 shl (i and 7))).toByte()
        }
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }
    private fun rawActor(i: Int, n: Int, fullFootprint: Boolean = true) = suiteMapper.createObjectNode().apply {
        put("uuid", ids[i]); put("chunk_x", sites[i] shr 4); put("chunk_z", 0)
        put("footprint_interval_masks_base64", intervalMask(sites[i], fullFootprint))
        put("player_ticks", n * 20); put("native_chunk_ticks", n * 20)
        put("footprint_loaded_chunks", view(sites[i]).size)
        put("footprint_block_ticking_chunks", inner(sites[i]))
        put("registered", true); put("location_chunk_matches", true)
    }
    private fun terminalActor(i: Int, n: Int) = rawActor(i, n).apply {
        put("site_x", sites[i]); put("site_y", 70); put("site_z", 0)
        put("footprint_chunk_tick_events", inner(sites[i]) * n * 20)
        put("footprint_distinct_chunks_ticked", inner(sites[i]))
        put("footprint_view_tuple_schema", schema)
        val tuples = putArray("footprint_view_chunks")
        var eligible = 0
        for ((cx, cz) in view(sites[i])) {
            val active = scaleSpawnProximity(cx, cz, sites[i], 0)
            tuples.addArray().add(cx).add(cz).add(n).add(if (active) n else 0)
                .add(if (active) n else 0).add(if (active) n else 0)
                .add(if (active) n else 0).add(if (active) n * 20 else 0)
                .add(if (active) 1 else 0)
            if (active) eligible++
        }
        put("footprint_geometric_chunks_count", tuples.size())
        put("footprint_tick_eligible_chunks", eligible); put("footprint_tick_observed_chunks", eligible)
        listOf("missing_view_loaded_chunks", "missing_inner_entity_ticking_chunks",
            "missing_inner_block_ticking_chunks", "missing_inner_natural_spawning_chunks",
            "missing_inner_tick_events_chunks", "missing_eligible_tick_events_chunks").forEach { put(it, 0) }
        put("transport_active", true); put("synthetic_keepalive_acks", 1)
        put("synthetic_teleport_acks", 1); put("synthetic_ack_queue_overflow", false)
    }
    private fun fixture(interval: (Int) -> Long = { 1_000_000_000L },
        fullFootprintAt: (Int) -> Boolean = { true }): List<DriverObservation> {
        var monotonic = setup
        var n = 0
        val rows = mutableListOf<JsonNode>()
        do {
            n++
            val wallNanos = interval(n)
            monotonic += wallNanos
            rows.add(suiteMapper.createObjectNode().apply {
                put("tick", n * 20); put("game_time", n * 20); put("native_game_tick_delta", 20)
                put("start_wall_nanos", start); put("setup_completed_wall_nanos", setup)
                put("setup_completed_tick", 0)
                put("monotonic_nanos", monotonic)
                put("observation_wall_millis", (monotonic - setup) / 1_000_000L)
                put("end_tick_interval_nanos_includes_scheduler_wait", wallNanos)
                put("server_tick_start_to_end_nanos", 30_000_000)
                put("heap_used", 100_000_000); put("gc_metrics_complete", true)
                put("gc_count_total", 0); put("gc_millis_total", 0)
                putArray("actors").apply { (0 until 8).forEach { add(rawActor(it, n, fullFootprintAt(n))) } }
            })
        } while (monotonic - start < 810_000_000_000L && n < 1000)
        val end = monotonic + 100_000_000L // independent receipt capture jitter; no 2ms assumption
        val begin = suiteMapper.createObjectNode().apply {
            put("cohort", 8); put("roster_size", 8); put("simulation_distance", 10); put("view_distance", 10)
            put("scale_contract_version", 2); put("capture_horizon_millis", 810_000)
            put("receipt_order", "sample_then_receipt")
            put("observed_samples", n); put("observation_wall_millis", (end - setup) / 1_000_000L)
            put("start_wall_nanos", start); put("setup_completed_wall_nanos", setup)
            put("setup_completed_tick", 0); put("setup_wall_nanos", setup - start)
            put("last_sample_wall_nanos", monotonic); put("last_sample_tick", n * 20)
            put("end_wall_nanos", end); put("terminal_partial_interval_nanos", end - monotonic)
            put("site_ledger_sha256", "a".repeat(64))
            put("site_ledger_verification", "external_unverified_by_native")
            put("terrain_freshness_proven_by_native", false)
            put("actor_type", "synthetic_registered_ServerPlayer_embedded_transport_NOT_real_login")
            put("tick_observer", "required_ServerLevel.tickChunk_HEAD_native_mixin")
            put("server_tick_timing_complete", true); put("gc_metrics_complete", true); put("heap_metrics_complete", true)
            put("tps_threshold_19_met_is_diagnostic_only", true)
            put("tps_adjudication", "external_setup_inclusive_recovery_then_sustained_dwell_required")
            put("real_login_proven", false); put("fakeplayer_factory_used", false)
            put("latency_type", "local_server_tick_duration_not_network_latency")
            put("observer_overhead_unmeasured", true)
            putArray("tps_30s_windows").apply { (1..20).forEach { w -> addObject().apply {
                put("wall_nanos", 30_000_000_000L); put("game_ticks", 600)
                put("tps", 20.0); put("end_sample_tick", w * 600)
            } } }
            putObject("samples_file").apply {
                put("path", "scale/000000000001.jsonl"); put("sha256", sha)
                put("records", n); put("bytes", 1_000_000)
            }
            putArray("actors").apply { (0 until 8).forEach { add(terminalActor(it, n)) } }
        }
        val cleanup = suiteMapper.createObjectNode().apply {
            put("released", 8); putArray("released_uuids").apply { ids.forEach(::add) }
            put("owned_residuals", 0); put("cleanup_complete", true); put("cleanup_within_120_seconds", true)
            put("cleanup_wall_millis_from_cleanup_begin", 400)
            put("session_wall_millis_from_cohort_begin", (end - start) / 1_000_000L + 400)
            put("baseline_restored", false)
        }
        return listOf(DriverObservation("observe", "scale.begin", "server", "completed", begin,
            mapOf("samples" to VerifiedRawEvidence(BoundFile("/sealed/native/scale/000000000001.jsonl", sha), rows))),
            DriverObservation("cleanup", "scale.cleanup", "server", "completed", cleanup))
    }
    private fun trial(observations: List<DriverObservation>) = Scale8Driver.evaluateTrial(
        Scale8Driver.expandTrials(target(), "sealed_scale_origin").single(), observations)

    @Test fun `full scale verifies variable sample count and entire engine eligible footprint`() {
        val req = Scale8Driver.expandTrials(target(), "sealed_scale_origin").single()
        assertEquals(8, Scale8Driver.samplingIntents(req).size)
        assertEquals(11, (Scale8Driver.samplingIntents(req).first() as
            com.bettercontent.tests.suite.checkpoints.SamplingIntent.Site).footprintRadiusChunks)
        assertEquals(1000, Scale8Driver.rawEvidenceRequests(req).single().maximumRecords)
        assertEquals("passed", trial(fixture()).state)
        assertTrue(!Scale8Driver.clientRequired && Scale8Driver.requireFreshTerrain)
        assertTrue(DriverRegistry.supportedCases.contains("scale.8")) // authored executable only, not qualified
    }
    @Test fun `receipt capture jitter is not rejected by an arbitrary two millisecond equality`() {
        val original = fixture().toMutableList()
        val observed = original[0]
        val begin = observed.payload.deepCopy<ObjectNode>()
        begin.put("end_wall_nanos", begin.path("end_wall_nanos").asLong() + 24_000_000L)
        begin.put("terminal_partial_interval_nanos", begin.path("terminal_partial_interval_nanos").asLong() + 24_000_000L)
        begin.put("observation_wall_millis", begin.path("observation_wall_millis").asLong() + 24)
        original[0] = observed.copy(payload = begin)
        val cleanup = original[1]
        original[1] = cleanup.copy(payload = cleanup.payload.deepCopy<ObjectNode>().put(
            "session_wall_millis_from_cohort_begin", cleanup.payload.path("session_wall_millis_from_cohort_begin").asLong() + 24))
        assertEquals("passed", trial(original).state)
    }
    @Test fun `late recovery after 120 seconds and sub nineteen TPS cannot pass`() {
        assertEquals("failed", trial(fixture(interval = { if (it <= 100) 2_000_000_000L else 1_000_000_000L })).state)
        assertEquals("failed", trial(fixture(interval = { 1_054_000_000L })).state)
    }
    @Test fun `fast TPS before full chunk readiness is setup but full ready recovery by 120s passes`() {
        assertEquals("passed", trial(fixture(fullFootprintAt = { it > 30 })).state)
        assertEquals("failed", trial(fixture(fullFootprintAt = { it > 100 })).state)
    }
    @Test fun `warmup full footprint then center only dwell is rejected despite cumulative receipt coverage`() {
        val partial = fixture(fullFootprintAt = { it <= 100 })
        assertEquals("failed", trial(partial).state)
    }
    @Test fun `missing or center only footprint and tampered sample anchor cannot pass`() {
        val missing = fixture().toMutableList(); missing[0] = missing[0].copy(rawEvidence = emptyMap())
        assertThrows(IllegalArgumentException::class.java) { trial(missing) }
        val center = fixture().toMutableList(); val original = center[0]
        val begin = original.payload.deepCopy<ObjectNode>()
        val actor = begin.path("actors")[0] as ObjectNode
        val other = (actor.path("footprint_view_chunks") as ArrayNode).first { row ->
            row[0].asInt() == 1 && row[1].asInt() == 0
        } as ArrayNode
        other.set(7, 0)
        center[0] = original.copy(payload = begin)
        assertEquals("failed", trial(center).state)
        val wrongAnchor = fixture().toMutableList(); val raw = wrongAnchor[0]
        wrongAnchor[0] = raw.copy(payload = raw.payload.deepCopy<ObjectNode>().put(
            "last_sample_wall_nanos", raw.payload.path("last_sample_wall_nanos").asLong() + 3_000_000L))
        assertThrows(IllegalArgumentException::class.java) { trial(wrongAnchor) }
    }
    @Test fun `raw actor numeric overflow cannot masquerade as bounded load or valid ticks`() {
        val countOverflow = fixture().toMutableList(); val observed = countOverflow[0]
        val rows = observed.rawEvidence.getValue("samples").records.toMutableList()
        val row = rows[0].deepCopy<ObjectNode>()
        (row.path("actors")[0] as ObjectNode).put("footprint_loaded_chunks", 4_294_967_749L)
        rows[0] = row
        countOverflow[0] = observed.copy(rawEvidence = mapOf("samples" to
            observed.rawEvidence.getValue("samples").copy(records = rows)))
        assertThrows(IllegalArgumentException::class.java) { trial(countOverflow) }
        val tickOverflow = fixture().toMutableList(); val source = tickOverflow[0]
        val rawRows = source.rawEvidence.getValue("samples").records.toMutableList()
        val huge = rawRows[0].deepCopy<ObjectNode>()
        (huge.path("actors")[0] as ObjectNode).put("player_ticks", java.math.BigInteger("18446744073709551616"))
        rawRows[0] = huge
        tickOverflow[0] = source.copy(rawEvidence = mapOf("samples" to source.rawEvidence.getValue("samples").copy(records = rawRows)))
        assertThrows(IllegalArgumentException::class.java) { trial(tickOverflow) }
    }
    @Test fun `rounding cannot accept shortened twenty window dwell or missing terminal sample`() {
        val shortened = fixture().toMutableList(); val begin = shortened[0]
        val rows = begin.rawEvidence.getValue("samples").records.dropLast(250)
        shortened[0] = begin.copy(rawEvidence = mapOf("samples" to begin.rawEvidence.getValue("samples").copy(records = rows)))
        assertThrows(IllegalArgumentException::class.java) { trial(shortened) }
        val insufficient = fixture(interval = { 1_050_000_000L })
        assertEquals("passed", trial(insufficient).state) // 20*ceil(30/1.05) samples remain within 810s
    }
}
