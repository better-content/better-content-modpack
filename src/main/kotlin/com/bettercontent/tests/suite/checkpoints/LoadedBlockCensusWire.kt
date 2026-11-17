package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.NativeRequest
import com.bettercontent.tests.suite.jsonFingerprint
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest

/** Loaded-only observed state: deliberately no natural-origin/unmodified acceptance field. */
internal data class LoadedBlockRow(val x: Int, val y: Int, val z: Int, val id: String,
    val properties: Map<String, String>, val actorDistanceBlocks: Double, val tick: Long,
    val monotonicNanos: Long)
internal data class LoadedBlockCensus(val raw: BoundFile, val selectorSha256: String,
    val queryTick: Long, val queryStartedNanos: Long, val queryEndedNanos: Long,
    val chunksChecked: Int, val complete: Boolean, val incompleteReason: String?,
    val candidates: List<LoadedBlockRow>) {
    init { require(selectorSha256.matches(shaPattern) && candidates.size <= 4096 && chunksChecked in 1..4096) }
    /** Deterministic loaded candidate ONLY; no natural ancestry or engagement is inferred. */
    fun reviewedLoadedCandidate(selector: ReviewedTargetSelector): LoadedBlockRow {
        require(complete && incompleteReason == null && candidates.isNotEmpty() &&
            selector.order == TargetSelectionOrder.NEAREST_THEN_POSITION &&
            selector.rule == "loaded_block_nearest_center_then_xyz_v1" &&
            selectorSha256 == jsonFingerprint(suiteMapper.valueToTree(selector))) {
            "loaded roster is not a complete reviewed selectable block census"
        }
        return candidates.filter { it.actorDistanceBlocks <= selector.bounds.maximumDistanceBlocks }
            .minWithOrNull(compareBy(LoadedBlockRow::actorDistanceBlocks, LoadedBlockRow::x,
                LoadedBlockRow::y, LoadedBlockRow::z))
            ?: throw IllegalArgumentException("no observed loaded candidate inside reviewed distance")
    }
}
private val loadedMapper = suiteMapper.copy().apply { factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION) }
private fun exactLoaded(node: JsonNode, fields: Set<String>) {
    require(node.isObject && node.fieldNames().asSequence().toSet() == fields)
}
private fun loadedState(node: JsonNode): Pair<String, Map<String, String>> {
    exactLoaded(node, setOf("id", "properties"))
    val id = node.path("id").asText(); val props = node.path("properties")
    require(node.path("id").isTextual && id.matches(resourcePattern) && props.isObject && props.size() <= 64)
    val entries = props.fields().asSequence().associate { (key, value) ->
        require(key.matches(Regex("[a-z0-9_]{1,64}")) && value.isTextual && value.asText().length in 1..128)
        key to value.asText()
    }
    return id to entries.toSortedMap()
}
private val rawKeys = setOf("schema", "witness_context", "selector_sha256", "dimension", "kind", "resource_id",
    "bounds", "max_candidates", "query_tick", "query_monotonic_nanos", "query_end_tick",
    "query_end_monotonic_nanos", "actor_at_query", "chunks_checked", "candidate_count", "complete",
    "natural_origin_proven_by_native", "natural_acquisition_proven_by_native", "candidates")
private val boundsKeys = setOf("min_x", "max_x", "min_y", "max_y", "min_z", "max_z", "maximum_distance_blocks")
private val blockRowKeys = setOf("x", "y", "z", "observed_tick", "observed_monotonic_nanos",
    "actor_distance_blocks", "block_state")

/** Reads the exact native sidecar once; emptiness and partial rosters never become selected targets. */
internal fun captureLoadedBlockCensus(mailbox: Path, request: NativeRequest, terminal: JsonNode,
    controller: WitnessContext, selector: ReviewedTargetSelector): LoadedBlockCensus {
    val selectorSha = jsonFingerprint(suiteMapper.valueToTree(selector))
    val controllerWire = suiteMapper.readTree(suiteMapper.writeValueAsBytes(controller))
    require(request.driver == "target.observe.loaded_v1" &&
        request.runId == controller.nativeSessionId && request.caseId == controller.caseId &&
        request.sequence == controller.requestSequence && controller.capabilityId == request.driver &&
        controller.source == "server" && controller.actorUuid == request.parameters.path("player_uuid").asText() &&
        controller.purpose in setOf("diagnostic", "qualification") &&
        selector.source in setOf(TargetSource.NATURAL_UNMODIFIED_BLOCK, TargetSource.NEWLY_GENERATED_TERRAIN) &&
        selector.capabilityId == "target.observe.loaded_v1" &&
        selector.bounds.dimension == request.parameters.path("dimension").asText() &&
        selector.resourceId == request.parameters.path("resource_id").asText() &&
        selectorSha == request.parameters.path("selector_sha256").asText() &&
        request.parameters.path("witness_context") == suiteMapper.valueToTree<JsonNode>(controller) &&
        request.parameters.path("kind").asText() == "block")
    exactLoaded(request.parameters, setOf("player_uuid", "dimension", "kind", "resource_id", "selector_sha256",
        "max_candidates", "bounds", "witness_context"))
    val requestBounds = request.parameters.path("bounds")
    exactLoaded(requestBounds, boundsKeys)
    require(requestBounds == loadedBoundsWire(selector.bounds) &&
        request.parameters.path("max_candidates").isIntegralNumber &&
        request.parameters.path("max_candidates").asInt(-1) in 1..4096)
    val b = selector.bounds
    val volume = listOf(b.maxX.toLong() - b.minX + 1, b.maxY.toLong() - b.minY + 1,
        b.maxZ.toLong() - b.minZ + 1).fold(1L) { total, width ->
        require(width in 1..4096 && total <= 4096 / width); total * width
    }
    require(volume in 1..4096)
    exactLoaded(terminal, setOf("schema", "witness_context", "selector_sha256", "complete", "census_file") +
        (if (terminal.has("incomplete_reason")) setOf("incomplete_reason") else emptySet()) +
        (if (terminal.has("block_mutation_pre_action_prefix")) setOf("block_mutation_pre_action_prefix") else emptySet()))
    val pointer = terminal.path("census_file")
    exactLoaded(pointer, setOf("path", "sha256", "bytes", "records"))
    val relative = "targets/%012d.json".format(request.sequence)
    val bytesNode = pointer.path("bytes"); val recordsNode = pointer.path("records")
    require(terminal.path("schema").asText() == "bc.playtest_loaded_target_census.v1" &&
        terminal.path("witness_context") == controllerWire &&
        terminal.path("selector_sha256").asText() == selectorSha &&
        terminal.path("complete").isBoolean &&
        pointer.path("path").asText() == relative && pointer.path("sha256").asText().matches(shaPattern) &&
        bytesNode.isIntegralNumber && bytesNode.canConvertToInt() && bytesNode.asInt() in 2..16_777_216 &&
        recordsNode.isIntegralNumber && recordsNode.canConvertToInt() && recordsNode.asInt() in 0..4096)
    val base = mailbox.toAbsolutePath().normalize()
    val folder = base.resolve("targets")
    val file = folder.resolve("%012d.json".format(request.sequence))
    require(Files.isDirectory(base, NOFOLLOW_LINKS) && !Files.isSymbolicLink(base) &&
        Files.isDirectory(folder, NOFOLLOW_LINKS) && !Files.isSymbolicLink(folder) &&
        Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) &&
        Files.size(file) == bytesNode.asLong())
    val bytes = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(16_777_217) }
    val actualSha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    require(bytes.size == bytesNode.asInt() && bytes.lastOrNull() == '\n'.code.toByte() &&
        actualSha == pointer.path("sha256").asText() && Hashes.sha256(file) == actualSha)
    val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    val raw: JsonNode = loadedMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text)
    val complete = raw.path("complete")
    val extra = when {
        complete.isBoolean && complete.asBoolean() -> emptySet()
        raw.has("unloaded_chunk_x") || raw.has("unloaded_chunk_z") ->
            setOf("incomplete_reason", "unloaded_chunk_x", "unloaded_chunk_z")
        else -> setOf("incomplete_reason")
    }
    exactLoaded(raw, rawKeys + extra)
    val startTick = raw.path("query_tick"); val endTick = raw.path("query_end_tick")
    val started = raw.path("query_monotonic_nanos"); val ended = raw.path("query_end_monotonic_nanos")
    val actor = raw.path("actor_at_query")
    exactLoaded(actor, setOf("uuid", "x", "y", "z"))
    val ax = actor.path("x").asDouble(Double.NaN)
    val ay = actor.path("y").asDouble(Double.NaN)
    val az = actor.path("z").asDouble(Double.NaN)
    val xChunks = Math.floorDiv(b.maxX, 16) - Math.floorDiv(b.minX, 16) + 1
    val zChunks = Math.floorDiv(b.maxZ, 16) - Math.floorDiv(b.minZ, 16) + 1
    val checked = raw.path("chunks_checked")
    require(raw.path("schema").asText() == "bc.playtest_loaded_target_census.v1" &&
        raw.path("witness_context") == controllerWire &&
        raw.path("selector_sha256").asText() == selectorSha &&
        raw.path("dimension").asText() == selector.bounds.dimension &&
        raw.path("kind").asText() == "block" && raw.path("resource_id").asText() == selector.resourceId &&
        raw.path("bounds") == requestBounds && raw.path("max_candidates") == request.parameters.path("max_candidates") &&
        actor.path("uuid").asText() == controller.actorUuid &&
        listOf(ax, ay, az).all(Double::isFinite) &&
        startTick.isIntegralNumber && startTick.asLong(-1) >= 0 &&
        endTick.isIntegralNumber && endTick.asLong(-1) == startTick.asLong() &&
        started.isIntegralNumber && started.canConvertToLong() &&
        ended.isIntegralNumber && ended.canConvertToLong() && ended.asLong() >= started.asLong() &&
        checked.isIntegralNumber && checked.asInt(-1) in 1..(xChunks * zChunks) &&
        complete.isBoolean && complete == terminal.path("complete") &&
        raw.path("natural_origin_proven_by_native").isBoolean &&
        !raw.path("natural_origin_proven_by_native").asBoolean() &&
        raw.path("natural_acquisition_proven_by_native").isBoolean &&
        !raw.path("natural_acquisition_proven_by_native").asBoolean() &&
        (!complete.asBoolean() || checked.asInt() == xChunks * zChunks) &&
        (!complete.asBoolean() || !raw.has("incomplete_reason")) &&
        raw.path("incomplete_reason") == terminal.path("incomplete_reason")) {
        "loaded census identity/chunks/producer limitation or lifecycle drifted"
    }
    val reason = raw.path("incomplete_reason").takeIf { it.isTextual }?.asText()
    require((complete.asBoolean() && reason == null) || (!complete.asBoolean() && !reason.isNullOrBlank()))
    if (raw.has("unloaded_chunk_x")) require(raw.path("unloaded_chunk_x").isIntegralNumber &&
        raw.path("unloaded_chunk_z").isIntegralNumber)
    val candidateRows = raw.path("candidates")
    require(candidateRows.isArray && candidateRows.size() == recordsNode.asInt() &&
        candidateRows.size() == raw.path("candidate_count").asInt(-1) &&
        candidateRows.size() <= request.parameters.path("max_candidates").asInt())
    val candidates = candidateRows.map { row ->
        exactLoaded(row, blockRowKeys)
        val x = row.path("x"); val y = row.path("y"); val z = row.path("z")
        require(x.isIntegralNumber && x.canConvertToInt() && y.isIntegralNumber && y.canConvertToInt() &&
            z.isIntegralNumber && z.canConvertToInt() && b.includes(x.asInt(), y.asInt(), z.asInt()))
        val state = loadedState(row.path("block_state"))
        val distance = row.path("actor_distance_blocks").asDouble(Double.NaN)
        val expectedDistance = Math.sqrt(Math.pow(x.asInt() + 0.5 - ax, 2.0) +
            Math.pow(y.asInt() + 0.5 - ay, 2.0) + Math.pow(z.asInt() + 0.5 - az, 2.0))
        require(state.first == selector.resourceId && distance.isFinite() && distance >= 0.0 &&
            kotlin.math.abs(distance - expectedDistance) <= 0.000001 &&
            row.path("observed_tick").isIntegralNumber && row.path("observed_tick").asLong(-1) == startTick.asLong() &&
            row.path("observed_monotonic_nanos").isIntegralNumber &&
            row.path("observed_monotonic_nanos").canConvertToLong() &&
            row.path("observed_monotonic_nanos").asLong() in started.asLong()..ended.asLong())
        LoadedBlockRow(x.asInt(), y.asInt(), z.asInt(), state.first, state.second,
            distance, startTick.asLong(), row.path("observed_monotonic_nanos").asLong())
    }
    require(candidates.map { Triple(it.x, it.y, it.z) }.distinct().size == candidates.size &&
        candidates.zipWithNext().all { (before, after) ->
            compareValuesBy(before, after, LoadedBlockRow::x, LoadedBlockRow::z, LoadedBlockRow::y) <= 0 &&
                before.monotonicNanos <= after.monotonicNanos
        } && (complete.asBoolean() || reason != null))
    return LoadedBlockCensus(BoundFile(file.toString(), actualSha), selectorSha, startTick.asLong(),
        started.asLong(), ended.asLong(), checked.asInt(), complete.asBoolean(), reason, candidates)
}
