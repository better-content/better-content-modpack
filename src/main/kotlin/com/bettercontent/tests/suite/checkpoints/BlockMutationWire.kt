package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest

/** A retained producer interval, deliberately WITHOUT a natural/unmodified assertion. */
internal data class ScopedMutationPrefix(val scope: BoundFile, val source: CheckpointRef,
    val file: Path, val byteCount: Int, val sha256: String, val records: Int,
    val changes: List<ScopedBlockPosition>, val observedPaths: List<String>,
    val adapterId: String, val unsupportedPaths: List<String>) {
    init { require(sha256.matches(shaPattern) && byteCount in 0..16_777_216 && records in 0..100_000 &&
        adapterId == "forge_1.20.1-47.4.22_full_chunk_palette_v1" &&
        unsupportedPaths.isNotEmpty() && changes.size == records && observedPaths.size == records) }
    val fallbackIncomplete: Boolean get() = "LevelChunkSection_palette_fallback_incomplete" in observedPaths
    fun observedChangeAt(position: ScopedBlockPosition): Boolean = position in changes
}
private val mutationJson = suiteMapper.copy().apply { factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION) }
private val scopeKeys = setOf("schema", "scope_id", "campaign_id", "lineage_id", "epoch_id", "native_session_id",
    "purpose", "source", "source_checkpoint_id", "source_checkpoint_sha256", "positions", "max_events", "max_bytes")
private val pointerKeys = setOf("schema", "scope_id", "scope_file_sha256", "lifecycle_context",
    "source_checkpoint_id", "source_checkpoint_sha256", "path", "bytes", "sha256", "records", "overflow",
    "io_failure", "scope_failure", "supported_scope_schema", "adapter_id", "supported_observed_path",
    "pinned_observer_paths", "reviewed_candidate_provider_pin_verified_by_native", "unsupported_paths",
    "universal_mutation_coverage_proven_by_native", "natural_origin_proven_by_native")
private val eventKeys = setOf("schema", "scope_id", "index", "tick", "monotonic_nanos", "dimension", "x", "y", "z",
    "observed_path", "old_state", "new_state", "prior_prefix_sha256")
internal val mutationUnsupportedPaths = listOf("unknown_preownership_exposure_or_direct_write_before_ChunkEvent_Load",
    "reflection_unsafe_private_PalettedContainer_data_or_section_array_mutation",
    "ProtoChunk_worldgen_and_chunk_birth_block_baselines", "block_entity_NBT_and_external_provider_state",
    "offline_or_before_scope_interval_without_independent_checkpoint_link",
    "unreviewed_provider_or_mutable_reference_exposure_blocks_supported_scope")
private val pinnedPaths = listOf("LevelChunk.setBlockState(BlockPos,BlockState,boolean)",
    "LevelChunkSection.setBlockState(int,int,int,BlockState,boolean)",
    "PalettedContainer.getAndSet(int,Object)/set(int,Object)/read(FriendlyByteBuf)",
    "ChunkAccess.getSections()/LevelChunkSection.getStates()/ChunkEvent.Load|Unload")
private val eventPaths = setOf("PalettedContainer.getAndSet_private_index_return",
    "PalettedContainer.set_private_index_return", "PalettedContainer.read_full_replacement",
    "LevelChunkSection_palette_fallback_incomplete")
private fun exact(row: JsonNode, fields: Set<String>) {
    require(row.isObject && row.fieldNames().asSequence().toSet() == fields) { "mutation wire field set drifted" }
}
private fun strictJson(bytes: ByteArray): JsonNode {
    val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    return mutationJson.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text)
}
private fun digestSnapshot(digest: MessageDigest): String =
    ((digest.clone() as MessageDigest).digest()).joinToString("") { "%02x".format(it) }
private fun position(row: JsonNode): ScopedBlockPosition {
    exact(row, setOf("dimension", "x", "y", "z"))
    val dim = row.path("dimension").asText()
    val x = row.path("x"); val y = row.path("y"); val z = row.path("z")
    require(dim.matches(resourcePattern) && x.isIntegralNumber && x.canConvertToInt() &&
        y.isIntegralNumber && y.canConvertToInt() && z.isIntegralNumber && z.canConvertToInt() &&
        x.asInt() in -30_000_000..30_000_000 && z.asInt() in -30_000_000..30_000_000 &&
        y.asInt() in -2048..2048)
    return ScopedBlockPosition(dim, x.asInt(), y.asInt(), z.asInt())
}
private fun state(row: JsonNode): Pair<String, Map<String, String>> {
    exact(row, setOf("id", "properties"))
    val properties = row.path("properties")
    require(row.path("id").isTextual && row.path("id").asText().matches(resourcePattern) &&
        properties.isObject && properties.size() <= 64)
    val values = properties.fields().asSequence().associate { (name, value) ->
        require(name.matches(Regex("[a-z0-9_]{1,64}")) && value.isTextual && value.asText().length in 1..128)
        name to value.asText()
    }.toSortedMap()
    return row.path("id").asText() to values
}
private fun exactBound(file: Path, bound: BoundFile, limit: Long): ByteArray {
    require(file.toAbsolutePath().normalize().toString() == bound.path &&
        Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) &&
        Files.size(file) in 1..limit && Hashes.sha256(file) == bound.sha256)
    val bytes = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes((limit + 1).toInt()) }
    require(bytes.size.toLong() == Files.size(file) && capturedSha256(bytes) == bound.sha256 &&
        Hashes.sha256(file) == bound.sha256)
    return bytes
}
/**
 * Replays exact B bytes through a Store-verified initial state; rejects broken links and restored
 * chains even if their final state equals origin. Unsupported mutation paths remain explicit.
 * A higher-level qualifier must separately prove coverage BEFORE granting natural provenance.
 */
internal fun replayInitialBlockMutationPrefix(store: CheckpointStore, source: CheckpointRef,
    scopeFile: BoundFile, mailbox: Path, pointer: JsonNode, expectedBindings: CheckpointBindings,
    epochId: String, nativeSessionId: String): ScopedMutationPrefix = replaySavedBlockMutationPrefix(store,
    source, scopeFile, mailbox, pointer, expectedBindings, epochId, nativeSessionId, pristine = true)

/** A disposable CONTROL may start at a saved non-pristine fixture; it never upgrades the main origin. */
internal fun replaySavedBlockMutationPrefix(store: CheckpointStore, source: CheckpointRef,
    scopeFile: BoundFile, mailbox: Path, pointer: JsonNode, expectedBindings: CheckpointBindings,
    epochId: String, nativeSessionId: String, pristine: Boolean): ScopedMutationPrefix {
    val receipt = if (pristine) requirePristinePackOrigin(store, source).receipt else store.verify(source)
    require(receipt.bindings == expectedBindings)
    val configPath = Path.of(scopeFile.path).toAbsolutePath().normalize()
    val native = mailbox.toAbsolutePath().normalize()
    require(configPath.fileName.toString() == "target-mutation-scope.json" &&
        configPath.parent.fileName.toString() == "evidence" && configPath.parent == native.parent &&
        native.fileName.toString() == "native" && Files.isDirectory(native, NOFOLLOW_LINKS) &&
        !Files.isSymbolicLink(native))
    val scope = strictJson(exactBound(configPath, scopeFile, 1_048_576))
    exact(scope, scopeKeys)
    val scopeId = scope.path("scope_id").asText()
    require(scope.path("schema").asText() == "bc.playtest_block_mutation_scope.v1" &&
        scopeId.matches(Regex("scope-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) &&
        scope.path("campaign_id").asText() == expectedBindings.campaignId &&
        scope.path("lineage_id").asText() == expectedBindings.lineageId &&
        scope.path("epoch_id").asText() == epochId &&
        scope.path("native_session_id").asText() == nativeSessionId &&
        scope.path("purpose").asText() == expectedBindings.purpose &&
        scope.path("source").asText() == "controller_prelaunch" &&
        scope.path("source_checkpoint_id").asText() == source.id &&
        scope.path("source_checkpoint_sha256").asText() == source.receipt.sha256 &&
        scope.path("max_events").asLong(-1) == 100_000L &&
        scope.path("max_bytes").asLong(-1) == 16_777_216L)
    val scoped = scope.path("positions")
    require(scoped.isArray && scoped.size() in 1..64)
    val positions = scoped.map(::position)
    val ordered = positions.sortedWith(compareBy(ScopedBlockPosition::dimension, ScopedBlockPosition::x,
        ScopedBlockPosition::y, ScopedBlockPosition::z))
    require(positions == ordered && positions.distinct().size == positions.size)
    val saved = positions.associateWith { pos ->
        val block = readSealedBlockBaseline(store, source, pos.dimension, pos.x, pos.y, pos.z)
        block.blockId to block.properties
    }.toMutableMap()
    exact(pointer, pointerKeys)
    val lifecycle = pointer.path("lifecycle_context")
    exact(lifecycle, setOf("campaign_id", "lineage_id", "epoch_id", "native_session_id", "purpose", "source"))
    require(pointer.path("schema").asText() == "bc.playtest_block_mutation_prefix.v2" &&
        pointer.path("scope_id").asText() == scopeId &&
        pointer.path("scope_file_sha256").asText() == scopeFile.sha256 &&
        lifecycle.path("campaign_id").asText() == expectedBindings.campaignId &&
        lifecycle.path("lineage_id").asText() == expectedBindings.lineageId &&
        lifecycle.path("epoch_id").asText() == epochId &&
        lifecycle.path("native_session_id").asText() == nativeSessionId &&
        lifecycle.path("purpose").asText() == expectedBindings.purpose &&
        lifecycle.path("source").asText() == "controller_prelaunch" &&
        pointer.path("source_checkpoint_id").asText() == source.id &&
        pointer.path("source_checkpoint_sha256").asText() == source.receipt.sha256 &&
        pointer.path("path").asText() == "mutations/$scopeId.jsonl" &&
        pointer.path("supported_scope_schema").asText() ==
            "bc.playtest_mutation_supported_full_chunk_palette.v1" &&
        pointer.path("adapter_id").asText() == "forge_1.20.1-47.4.22_full_chunk_palette_v1" &&
        pointer.path("supported_observed_path").asText() ==
            "pinned_owned_full_chunk_private_palette_index_or_read" &&
        pointer.path("pinned_observer_paths").isArray &&
        pointer.path("pinned_observer_paths").map { it.asText() } == pinnedPaths &&
        pointer.path("reviewed_candidate_provider_pin_verified_by_native").isBoolean &&
        !pointer.path("reviewed_candidate_provider_pin_verified_by_native").asBoolean() &&
        pointer.path("overflow").isBoolean && !pointer.path("overflow").asBoolean() &&
        pointer.path("io_failure").isBoolean && !pointer.path("io_failure").asBoolean() &&
        pointer.path("scope_failure").isBoolean && !pointer.path("scope_failure").asBoolean() &&
        pointer.path("universal_mutation_coverage_proven_by_native").isBoolean &&
        !pointer.path("universal_mutation_coverage_proven_by_native").asBoolean() &&
        pointer.path("natural_origin_proven_by_native").isBoolean &&
        !pointer.path("natural_origin_proven_by_native").asBoolean() &&
        pointer.path("unsupported_paths").map { it.asText() } == mutationUnsupportedPaths) {
        "native mutation pointer did not bind successful exact declared lifecycle/prefix limits"
    }
    val length = pointer.path("bytes"); val count = pointer.path("records"); val expectedHash = pointer.path("sha256").asText()
    require(length.isIntegralNumber && length.canConvertToInt() && length.asInt() in 0..16_777_216 &&
        count.isIntegralNumber && count.canConvertToInt() && count.asInt() in 0..100_000 &&
        expectedHash.matches(shaPattern))
    val folder = native.resolve("mutations")
    val file = folder.resolve("$scopeId.jsonl")
    require(Files.isDirectory(folder, NOFOLLOW_LINKS) && !Files.isSymbolicLink(folder) &&
        Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) &&
        Files.size(file) in length.asLong()..16_777_216L)
    val prefix = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(length.asInt()) }
    val paths = mutableListOf<String>()
    val changes = decodeScopedMutationEvents(prefix, scopeId, saved, count.asInt(), expectedHash, paths)
    require(Files.size(file) in length.asLong()..16_777_216L &&
        Hashes.sha256(configPath) == scopeFile.sha256) { "mutation file or scope changed during replay" }
    return ScopedMutationPrefix(scopeFile, source, file, length.asInt(), expectedHash,
        changes.size, changes, paths, pointer.path("adapter_id").asText(), mutationUnsupportedPaths)
}

/** Pure raw-byte linked transition replay, permitting narrow source-only synthetic regressions. */
internal fun decodeScopedMutationEvents(prefix: ByteArray, scopeId: String,
    initial: Map<ScopedBlockPosition, Pair<String, Map<String, String>>>,
    expectedRecords: Int, expectedSha256: String,
    observedPaths: MutableList<String>? = null,
    finalStates: MutableMap<ScopedBlockPosition, Pair<String, Map<String, String>>>? = null,
    bornAtBytes: Map<ScopedBlockPosition, Int> = emptyMap()): List<ScopedBlockPosition> {
    require(prefix.size <= 16_777_216 && expectedRecords in 0..100_000 && expectedSha256.matches(shaPattern) &&
        initial.isNotEmpty() && initial.size <= 4096)
    val saved = initial.toMutableMap()
    val digest = MessageDigest.getInstance("SHA-256")
    val changes = mutableListOf<ScopedBlockPosition>()
    var priorTick = -1L
    var priorNanos = 0L // arbitrary signed System.nanoTime origin; only later rows compare
    var start = 0
    for (end in prefix.indices) if (prefix[end] == '\n'.code.toByte()) {
        val line = prefix.copyOfRange(start, end + 1)
        require(line.size in 2..65_536 && line[line.lastIndex - 1] != '\r'.code.toByte())
        val row = strictJson(line)
        exact(row, eventKeys)
        val pos = position(mutationJson.createObjectNode().apply {
            set<JsonNode>("dimension", row.path("dimension")); set<JsonNode>("x", row.path("x"))
            set<JsonNode>("y", row.path("y")); set<JsonNode>("z", row.path("z"))
        })
        val nanos = row.path("monotonic_nanos")
        require(pos in saved && (bornAtBytes.isEmpty() || (pos in bornAtBytes && end + 1 > bornAtBytes.getValue(pos))) &&
            row.path("schema").asText() == "bc.playtest_block_mutation_event.v2" &&
            row.path("scope_id").asText() == scopeId && row.path("index").asLong(-1) == changes.size + 1L &&
            row.path("tick").asLong(-1) >= 0L && row.path("tick").asLong(-1) >= priorTick &&
            nanos.isIntegralNumber && nanos.canConvertToLong() &&
            (changes.isEmpty() || nanos.asLong() >= priorNanos) &&
            row.path("observed_path").isTextual && row.path("observed_path").asText() in eventPaths &&
            row.path("prior_prefix_sha256").asText() == digestSnapshot(digest)) {
            "native mutation prefix/position/index link drifted"
        }
        val old = state(row.path("old_state")); val current = state(row.path("new_state"))
        require(old == saved.getValue(pos) && current != old) {
            "mutation old/new state did not continue independent saved initial block state"
        }
        saved[pos] = current
        changes += pos
        observedPaths?.add(row.path("observed_path").asText())
        priorTick = row.path("tick").asLong()
        priorNanos = nanos.asLong()
        digest.update(line)
        start = end + 1
    }
    require(start == prefix.size && changes.size == expectedRecords &&
        digest.digest().joinToString("") { "%02x".format(it) } == expectedSha256) {
        "mutation prefix bytes/hash/record count drifted"
    }
    finalStates?.putAll(saved)
    return changes
}

/** The native server-stop status is hash-bound in closure events; zero bytes is valid only for zero mutations. */
internal fun verifyStoppedBlockMutationStatus(store: CheckpointStore, source: CheckpointRef,
    scopeFile: BoundFile, mailbox: Path, statusFile: BoundFile, expectedBindings: CheckpointBindings,
    epochId: String, nativeSessionId: String, pristine: Boolean = true,
    requireShutdownDrain: Boolean = false): ScopedMutationPrefix {
    val statusPath = Path.of(statusFile.path).toAbsolutePath().normalize()
    val expectedFolder = mailbox.toAbsolutePath().normalize().resolve("mutations")
    require(statusPath.parent == expectedFolder &&
        statusPath.fileName.toString().matches(Regex("scope-[0-9a-f-]{36}\\.status\\.json")))
    val status = strictJson(exactBound(statusPath, statusFile, 1_048_576))
    exact(status, pointerKeys + setOf("stop_tick", "closed_monotonic_nanos") +
        if (requireShutdownDrain) setOf("shutdown_drain") else emptySet())
    require(status.path("schema").isTextual && status.path("schema").asText() ==
        if (requireShutdownDrain) "bc.playtest_block_mutation_status.v3"
        else "bc.playtest_block_mutation_prefix.v2")
    if (requireShutdownDrain) {
        val scope = strictJson(exactBound(Path.of(scopeFile.path), scopeFile, 1_048_576))
        require(scope.path("positions").isArray)
        val expectedChunks = scope.path("positions").map { birthChunkKey(position(it)) }.toSet()
        require(expectedChunks.isNotEmpty() && expectedChunks.size <= 64)
        replayShutdownDrain(status, expectedChunks)
    }
    require(statusPath.fileName.toString() == "${status.path("scope_id").asText()}.status.json" &&
        status.path("stop_tick").isIntegralNumber && status.path("stop_tick").asLong(-1) >= 0 &&
        status.path("closed_monotonic_nanos").isIntegralNumber &&
        status.path("closed_monotonic_nanos").canConvertToLong())
    val pointer = (status.deepCopy<JsonNode>() as ObjectNode).apply {
        remove("stop_tick"); remove("closed_monotonic_nanos")
        if (requireShutdownDrain) {
            remove("shutdown_drain")
            put("schema", "bc.playtest_block_mutation_prefix.v2")
        }
    }
    val replay = replaySavedBlockMutationPrefix(store, source, scopeFile, mailbox, pointer,
        expectedBindings, epochId, nativeSessionId, pristine)
    require(Files.size(replay.file) == replay.byteCount.toLong() && Hashes.sha256(replay.file) == replay.sha256) {
        "sealed native mutation file changed beyond server-stop pointer"
    }
    return replay
}
