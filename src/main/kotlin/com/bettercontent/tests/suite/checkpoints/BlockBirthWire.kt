package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.SuiteCase
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

/** Birth/postbirth interval facts. Never a native-asserted natural or universal coverage verdict. */
internal data class CapturedBirthInterval(val scope: PrelaunchBlockBirthScope, val parent: CheckpointRef,
    val birthFile: BoundFile, val mutationFile: BoundFile, val birthRecords: Int,
    val mutationRecords: Int, val ready: Boolean,
    val birthStates: Map<ScopedBlockPosition, Pair<String, Map<String, String>>>,
    val currentStates: Map<ScopedBlockPosition, Pair<String, Map<String, String>>>,
    val changes: List<ScopedBlockPosition>, val observedPaths: List<String>) {
    init { require(birthRecords in 0..100_000 && mutationRecords in 0..100_000 &&
        birthStates.size <= 64 && changes.size == mutationRecords && observedPaths.size == mutationRecords) }
}
private val birthJson = suiteMapper.copy().apply { factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION) }
private val birthPointerKeys = setOf("schema", "scope_id", "scope_file_sha256", "lifecycle_context",
    "source_checkpoint_id", "source_checkpoint_sha256", "parent_absence_sidecar_sha256", "path", "bytes",
    "sha256", "records", "expected_birth_chunks", "observed_constructor_chunks",
    "matching_new_chunk_load_chunks", "postprocessed_chunks", "overflow", "io_failure",
    "scope_failure", "birth_observer_path", "adapter_id", "parent_absence_proven_by_native",
    "natural_origin_proven_by_native", "universal_mutation_coverage_proven_by_native")
private val mutationPointerKeys = setOf("schema", "scope_id", "scope_file_sha256", "lifecycle_context",
    "source_checkpoint_id", "source_checkpoint_sha256", "path", "bytes", "sha256", "records",
    "overflow", "io_failure", "scope_failure", "supported_scope_schema", "adapter_id",
    "supported_observed_path", "pinned_observer_paths", "reviewed_candidate_provider_pin_verified_by_native",
    "unsupported_paths", "universal_mutation_coverage_proven_by_native", "natural_origin_proven_by_native")
private val birthEventCommon = setOf("schema", "scope_id", "index", "phase", "tick", "monotonic_nanos",
    "dimension", "chunk_x", "chunk_z", "birth_identity", "preceding_mutation_bytes",
    "preceding_mutation_records", "preceding_mutation_sha256", "prior_birth_prefix_sha256")
private val birthObserver = "LevelChunk(ServerLevel,ProtoChunk,PostLoadProcessor).RETURN_before_runPostLoad_then_ChunkEvent.Load_then_LevelChunk.postProcessGeneration.RETURN"
private val birthPinnedPaths = listOf("LevelChunk.setBlockState(BlockPos,BlockState,boolean)",
    "LevelChunkSection.setBlockState(int,int,int,BlockState,boolean)",
    "PalettedContainer.getAndSet(int,Object)/set(int,Object)/read(FriendlyByteBuf)",
    "ChunkAccess.getSections()/LevelChunkSection.getStates()/ChunkEvent.Load|Unload")
private val birthUnsupported = listOf("unknown_preownership_exposure_or_direct_write_before_ChunkEvent_Load",
    "reflection_unsafe_private_PalettedContainer_data_or_section_array_mutation",
    "ProtoChunk_worldgen_and_chunk_birth_block_baselines", "block_entity_NBT_and_external_provider_state",
    "offline_or_before_scope_interval_without_independent_checkpoint_link",
    "unreviewed_provider_or_mutable_reference_exposure_blocks_supported_scope")
private fun birthHash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it) }
private fun birthDigestSnapshot(digest: MessageDigest) = (digest.clone() as MessageDigest).digest()
    .joinToString("") { "%02x".format(it) }
private fun closedBirthFields(row: JsonNode, keys: Set<String>) {
    require(row.isObject && row.fieldNames().asSequence().toSet() == keys) { "birth wire fields differ" }
}
private fun birthText(bytes: ByteArray): JsonNode {
    val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    return birthJson.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text)
}
private fun birthCount(row: JsonNode, key: String, cap: Int): Int {
    val value = row.path(key)
    require(value.isIntegralNumber && value.canConvertToInt() && value.asInt() in 0..cap) { "invalid $key" }
    return value.asInt()
}
private fun birthPrefixBytes(file: Path, count: Int, expectedHash: String, folder: Path): ByteArray {
    require(expectedHash.matches(shaPattern) && count in 0..16_777_216 &&
        file.parent == folder && Files.isDirectory(folder, NOFOLLOW_LINKS) && !Files.isSymbolicLink(folder) &&
        Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) &&
        Files.size(file) in count.toLong()..16_777_216L)
    val bytes = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(count) }
    require(bytes.size == count && birthHash(bytes) == expectedHash &&
        (bytes.isEmpty() || bytes.last() == '\n'.code.toByte()) && Files.size(file) >= count)
    return bytes
}
private fun birthScopeNode(bound: PrelaunchBlockBirthScope): JsonNode {
    val file = Path.of(bound.scope.path)
    require(Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) &&
        Files.size(file) in 2..1_048_576)
    val bytes = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(1_048_577) }
    require(bytes.size in 2..1_048_576 && Files.size(file) == bytes.size.toLong() &&
        birthHash(bytes) == bound.scope.sha256 && Hashes.sha256(file) == bound.scope.sha256)
    return birthText(bytes)
}
private fun birthState(row: JsonNode): Pair<String, Map<String, String>> {
    closedBirthFields(row, setOf("id", "properties"))
    val props = row.path("properties")
    require(row.path("id").isTextual &&
        row.path("id").asText().matches(com.bettercontent.tests.suite.resourcePattern) &&
        props.isObject && props.size() <= 64)
    val values = props.fields().asSequence().associate { (key, value) ->
        require(key.matches(Regex("[a-z0-9_]{1,64}")) && value.isTextual && value.asText().length in 1..128)
        key to value.asText()
    }.toSortedMap()
    return row.path("id").asText() to values
}
private fun verifyBirthLifecycle(pointer: JsonNode, scope: JsonNode, parent: CheckpointRef,
    bound: PrelaunchBlockBirthScope, bindings: CheckpointBindings, epochId: String, session: String,
    chunkCount: Int, readyRequired: Boolean, stopped: Boolean): Pair<Int, Int> {
    val extras = (if (stopped) setOf("postbirth_mutation_final_prefix", "stop_tick", "closed_monotonic_nanos")
        else setOf("postbirth_mutation_pre_action_prefix")) +
        if (pointer.has("failure_reason")) setOf("failure_reason") else emptySet()
    closedBirthFields(pointer, birthPointerKeys + extras)
    val context = pointer.path("lifecycle_context")
    closedBirthFields(context, setOf("campaign_id", "lineage_id", "epoch_id", "native_session_id", "purpose", "source"))
    val id = scope.path("scope_id").asText()
    require(pointer.path("schema").asText() == "bc.playtest_block_birth_prefix.v1" &&
        pointer.path("scope_id").asText() == id &&
        pointer.path("scope_file_sha256").asText() == bound.scope.sha256 &&
        context.path("campaign_id").asText() == bindings.campaignId &&
        context.path("lineage_id").asText() == bindings.lineageId &&
        context.path("epoch_id").asText() == epochId && context.path("native_session_id").asText() == session &&
        context.path("purpose").asText() == bindings.purpose && context.path("source").asText() == "controller_prelaunch" &&
        pointer.path("source_checkpoint_id").asText() == parent.id &&
        pointer.path("source_checkpoint_sha256").asText() == parent.receipt.sha256 &&
        pointer.path("parent_absence_sidecar_sha256").asText() == bound.parentAbsence.sha256 &&
        pointer.path("path").asText() == "births/$id.jsonl" &&
        pointer.path("expected_birth_chunks").asInt(-1) == chunkCount &&
        pointer.path("birth_observer_path").asText() == birthObserver &&
        pointer.path("adapter_id").asText() == "forge_1.20.1-47.4.22_full_chunk_birth_v1" &&
        listOf("parent_absence_proven_by_native", "natural_origin_proven_by_native",
            "universal_mutation_coverage_proven_by_native").all { key ->
            pointer.path(key).isBoolean && !pointer.path(key).asBoolean() } &&
        pointer.path("overflow").isBoolean && !pointer.path("overflow").asBoolean() &&
        pointer.path("io_failure").isBoolean && !pointer.path("io_failure").asBoolean()) {
        "birth prefix differs from exact parent/epoch/false native provenance and caps"
    }
    val constructors = birthCount(pointer, "observed_constructor_chunks", chunkCount)
    val loads = birthCount(pointer, "matching_new_chunk_load_chunks", chunkCount)
    val postprocessed = birthCount(pointer, "postprocessed_chunks", chunkCount)
    require(loads <= constructors && postprocessed <= loads &&
        pointer.path("scope_failure").isBoolean &&
        (if (readyRequired) !pointer.path("scope_failure").asBoolean() &&
            constructors == chunkCount && loads == chunkCount && postprocessed == chunkCount &&
            !pointer.has("failure_reason")
        else pointer.path("scope_failure").asBoolean() ==
            (constructors != chunkCount || loads != chunkCount || postprocessed != chunkCount) &&
            (if (pointer.has("failure_reason")) pointer.path("failure_reason").asText() ==
                "birth_constructor_load_or_postprocess_not_yet_complete" else true))) {
        "birth interval is incomplete, failed or falsely promoted"
    }
    if (stopped) require(pointer.path("stop_tick").isIntegralNumber && pointer.path("stop_tick").asLong(-1) >= 0 &&
        pointer.path("closed_monotonic_nanos").isIntegralNumber &&
        pointer.path("closed_monotonic_nanos").canConvertToLong())
    return birthCount(pointer, "bytes", 16_777_216) to birthCount(pointer, "records", 100_000)
}

/** Independently replay B's two SHA-linked raw streams and same-epoch lifetime; no natural verdict. */
internal fun replayBlockBirthPrefix(store: CheckpointStore, parent: CheckpointRef,
    specs: List<SuiteCase>, catalogueSha256: String, bindings: CheckpointBindings,
    epochId: String, session: String, evidence: Path, bound: PrelaunchBlockBirthScope,
    mailbox: Path, pointer: JsonNode, readyRequired: Boolean = true,
    stopped: Boolean = false, requiredShutdownDrain: ReplayedShutdownDrain? = null): CapturedBirthInterval {
    val absence = replayBlockBirthScope(store, parent, specs, catalogueSha256, bindings,
        epochId, session, evidence, bound)
    val scope = birthScopeNode(bound)
    val id = scope.path("scope_id").asText()
    val (birthBytes, birthRecords) = verifyBirthLifecycle(pointer, scope, parent, bound, bindings,
        epochId, session, absence.chunkKeys.size, readyRequired, stopped)
    val native = mailbox.toAbsolutePath().normalize()
    require(native.parent == evidence.toAbsolutePath().normalize() && native.fileName.toString() == "native" &&
        Files.isDirectory(native, NOFOLLOW_LINKS) && !Files.isSymbolicLink(native))
    val birthFile = native.resolve("births/$id.jsonl")
    val rawBirth = birthPrefixBytes(birthFile, birthBytes, pointer.path("sha256").asText(), native.resolve("births"))
    val mutation = pointer.path(if (stopped) "postbirth_mutation_final_prefix" else "postbirth_mutation_pre_action_prefix")
    closedBirthFields(mutation, mutationPointerKeys + if (mutation.has("failure_reason")) setOf("failure_reason") else emptySet())
    closedBirthFields(mutation.path("lifecycle_context"),
        setOf("campaign_id", "lineage_id", "epoch_id", "native_session_id", "purpose", "source"))
    require(mutation.path("schema").asText() == "bc.playtest_block_mutation_prefix.v2" &&
        mutation.path("scope_id").asText() == id && mutation.path("scope_file_sha256").asText() == bound.scope.sha256 &&
        mutation.path("lifecycle_context") == pointer.path("lifecycle_context") &&
        mutation.path("source_checkpoint_id").asText() == parent.id &&
        mutation.path("source_checkpoint_sha256").asText() == parent.receipt.sha256 &&
        mutation.path("path").asText() == "mutations/$id.jsonl" &&
        mutation.path("supported_scope_schema").asText() == "bc.playtest_mutation_supported_full_chunk_palette.v1" &&
        mutation.path("adapter_id").asText() == "forge_1.20.1-47.4.22_full_chunk_palette_v1" &&
        mutation.path("supported_observed_path").asText() == "pinned_owned_full_chunk_private_palette_index_or_read" &&
        mutation.path("pinned_observer_paths").map { it.asText() } == birthPinnedPaths &&
        mutation.path("unsupported_paths").map { it.asText() } == birthUnsupported &&
        listOf("reviewed_candidate_provider_pin_verified_by_native", "universal_mutation_coverage_proven_by_native",
            "natural_origin_proven_by_native").all { key ->
            mutation.path(key).isBoolean && !mutation.path(key).asBoolean() } &&
        listOf("overflow", "io_failure", "scope_failure").all { key ->
            mutation.path(key).isBoolean && !mutation.path(key).asBoolean() } &&
        !mutation.has("failure_reason")) { "postbirth mutation prefix had a gap or false provider pin" }
    val mutationBytes = birthCount(mutation, "bytes", 16_777_216)
    val mutationRecords = birthCount(mutation, "records", 100_000)
    val mutationFile = native.resolve("mutations/$id.jsonl")
    val rawMutations = birthPrefixBytes(mutationFile, mutationBytes, mutation.path("sha256").asText(), native.resolve("mutations"))
    val birthDigest = MessageDigest.getInstance("SHA-256")
    val mutationDigest = MessageDigest.getInstance("SHA-256")
    var priorMutationBytes = 0; var priorMutationRecords = 0
    var priorTick = -1L; var priorNanos = 0L; var index = 0; var lineStart = 0
    val born = linkedMapOf<BirthChunkKey, String>()
    val loaded = mutableSetOf<BirthChunkKey>(); val postprocessed = mutableSetOf<BirthChunkKey>()
    val states = linkedMapOf<ScopedBlockPosition, Pair<String, Map<String, String>>>()
    val bornAtBytes = mutableMapOf<ScopedBlockPosition, Int>()
    for (end in rawBirth.indices) if (rawBirth[end] == '\n'.code.toByte()) {
        val line = rawBirth.copyOfRange(lineStart, end + 1)
        require(line.size in 2..65_536 && line[line.lastIndex - 1] != '\r'.code.toByte())
        val row = birthText(line)
        val phase = row.path("phase").asText()
        val fields = when (phase) {
            "full_chunk_constructor_return_baseline" -> setOf("x", "y", "z", "block_state", "proto_status", "new_chunk_event_observed")
            "matching_full_chunk_load" -> setOf("new_chunk_event_observed")
            "postprocess_generation_return" -> emptySet()
            else -> error("unsupported birth phase")
        }
        closedBirthFields(row, birthEventCommon + fields)
        require(row.path("dimension").isTextual &&
            row.path("chunk_x").isIntegralNumber && row.path("chunk_x").canConvertToInt() &&
            row.path("chunk_z").isIntegralNumber && row.path("chunk_z").canConvertToInt())
        val chunk = BirthChunkKey(row.path("dimension").asText(), row.path("chunk_x").asInt(),
            row.path("chunk_z").asInt())
        val expectedPositions = absence.watchedPositions.filter { birthChunkKey(it) == chunk }
        val nanos = row.path("monotonic_nanos")
        require(chunk in absence.chunkKeys && expectedPositions.isNotEmpty() &&
            row.path("schema").asText() == "bc.playtest_block_birth_event.v1" &&
            row.path("scope_id").asText() == id && row.path("index").isIntegralNumber &&
            row.path("index").asLong(-1) == (++index).toLong() &&
            row.path("tick").isIntegralNumber && row.path("tick").canConvertToLong() &&
            row.path("tick").asLong() >= priorTick && nanos.isIntegralNumber && nanos.canConvertToLong() &&
            (index == 1 || nanos.asLong() >= priorNanos) &&
            row.path("prior_birth_prefix_sha256").asText() == birthDigestSnapshot(birthDigest)) {
            "birth event chronology, scope, chunk or raw preceding SHA differs"
        }
        val precedingBytes = birthCount(row, "preceding_mutation_bytes", mutationBytes)
        val precedingRecords = birthCount(row, "preceding_mutation_records", mutationRecords)
        require(precedingBytes >= priorMutationBytes && precedingRecords >= priorMutationRecords &&
            (precedingBytes == 0 || rawMutations[precedingBytes - 1] == '\n'.code.toByte()))
        mutationDigest.update(rawMutations, priorMutationBytes, precedingBytes - priorMutationBytes)
        val addedRecords = (priorMutationBytes until precedingBytes).count { rawMutations[it] == '\n'.code.toByte() }
        require(priorMutationRecords + addedRecords == precedingRecords &&
            row.path("preceding_mutation_sha256").asText() == birthDigestSnapshot(mutationDigest)) {
            "birth↔postbirth mutation exact preceding prefix/record/SHA crosslink differs"
        }
        val identity = row.path("birth_identity").asText()
        when (phase) {
            "full_chunk_constructor_return_baseline" -> {
                if (chunk !in born) {
                    require(identity == "$id:birth-${born.size + 1}")
                    born[chunk] = identity
                }
                require(born.getValue(chunk) == identity && chunk !in loaded &&
                    chunk !in postprocessed && row.path("new_chunk_event_observed").isBoolean &&
                    !row.path("new_chunk_event_observed").asBoolean() &&
                    row.path("proto_status").isTextual && row.path("proto_status").asText().length in 1..128)
                val next = expectedPositions.firstOrNull { it !in states }
                    ?: error("duplicate/unreviewed birth position")
                require(row.path("x").isIntegralNumber && row.path("x").asInt() == next.x &&
                    row.path("y").isIntegralNumber && row.path("y").asInt() == next.y &&
                    row.path("z").isIntegralNumber && row.path("z").asInt() == next.z)
                states[next] = birthState(row.path("block_state"))
                bornAtBytes[next] = precedingBytes
            }
            "matching_full_chunk_load" -> require(born[chunk] == identity && chunk !in loaded &&
                expectedPositions.all { it in states } && row.path("new_chunk_event_observed").isBoolean &&
                row.path("new_chunk_event_observed").asBoolean()).also { loaded += chunk }
            "postprocess_generation_return" -> require(born[chunk] == identity && chunk in loaded &&
                postprocessed.add(chunk))
        }
        priorMutationBytes = precedingBytes; priorMutationRecords = precedingRecords
        priorTick = row.path("tick").asLong(); priorNanos = nanos.asLong()
        birthDigest.update(line); lineStart = end + 1
    }
    require(lineStart == rawBirth.size && index == birthRecords &&
        birthDigestSnapshot(birthDigest) == pointer.path("sha256").asText() &&
        born.size == birthCount(pointer, "observed_constructor_chunks", absence.chunkKeys.size) &&
        loaded.size == birthCount(pointer, "matching_new_chunk_load_chunks", absence.chunkKeys.size) &&
        postprocessed.size == birthCount(pointer, "postprocessed_chunks", absence.chunkKeys.size))
    val ready = born.size == absence.chunkKeys.size && loaded.size == born.size && postprocessed.size == born.size
    require(!readyRequired || ready && states.size == absence.watchedPositions.size)
    val current = mutableMapOf<ScopedBlockPosition, Pair<String, Map<String, String>>>()
    val observed = mutableListOf<String>()
    val changes = if (states.isEmpty()) {
        require(mutationBytes == 0 && mutationRecords == 0); emptyList()
    } else decodeScopedMutationEvents(rawMutations, id, states, mutationRecords,
        mutation.path("sha256").asText(), observed, current, bornAtBytes)
    if (stopped) {
        require(Files.size(birthFile) == birthBytes.toLong() && Files.size(mutationFile) == mutationBytes.toLong())
        val stopPath = native.resolve("mutations/$id.status.json")
        require(Files.isRegularFile(stopPath, NOFOLLOW_LINKS) && !Files.isSymbolicLink(stopPath) &&
            Files.size(stopPath) in 2..1_048_576)
        val statusBytes = Files.newInputStream(stopPath, NOFOLLOW_LINKS).use { it.readNBytes(1_048_577) }
        require(statusBytes.size in 2..1_048_576 && Files.size(stopPath) == statusBytes.size.toLong())
        val mutationStop = birthText(statusBytes)
        closedBirthFields(mutationStop, mutationPointerKeys + setOf("stop_tick", "closed_monotonic_nanos") +
            if (requiredShutdownDrain != null) setOf("shutdown_drain") else emptySet())
        if (requiredShutdownDrain != null) {
            require(mutationStop.path("schema").asText() == "bc.playtest_block_mutation_status.v3" &&
                replayShutdownDrain(mutationStop, absence.chunkKeys.toSet(), requireEveryExpectedChunk = true) ==
                requiredShutdownDrain)
        }
        require(mutationStop.path("stop_tick") == pointer.path("stop_tick") &&
            mutationStop.path("closed_monotonic_nanos").isIntegralNumber &&
            mutationStop.path("closed_monotonic_nanos").canConvertToLong() &&
            pointer.path("closed_monotonic_nanos").asLong() >=
                mutationStop.path("closed_monotonic_nanos").asLong() &&
            mutationStop.deepCopy<ObjectNode>().apply {
                remove(listOf("stop_tick", "closed_monotonic_nanos"))
                if (requiredShutdownDrain != null) {
                    remove("shutdown_drain")
                    put("schema", "bc.playtest_block_mutation_prefix.v2")
                }
            } == mutation)
    }
    require(Hashes.sha256(Path.of(bound.scope.path)) == bound.scope.sha256 &&
        Hashes.sha256(Path.of(bound.parentAbsence.path)) == bound.parentAbsence.sha256)
    return CapturedBirthInterval(bound, parent,
        BoundFile(birthFile.toString(), birthHash(rawBirth)), BoundFile(mutationFile.toString(), birthHash(rawMutations)),
        birthRecords, mutationRecords, ready, states, current, changes, observed)
}

/** Independently captured native stop bytes, not a mere pointer in an unsealed action. */
internal fun replayStoppedBlockBirthStatus(store: CheckpointStore, parent: CheckpointRef,
    specs: List<SuiteCase>, catalogueSha256: String, bindings: CheckpointBindings,
    epochId: String, session: String, evidence: Path, bound: PrelaunchBlockBirthScope,
    mailbox: Path, statusFile: BoundFile): CapturedBirthInterval {
    val scope = birthScopeNode(bound)
    val id = scope.path("scope_id").asText()
    val path = mailbox.toAbsolutePath().normalize().resolve("births/$id.status.json")
    require(statusFile.path == path.toString() && statusFile.sha256.matches(shaPattern) &&
        Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) &&
        Files.size(path) in 2..1_048_576)
    val bytes = Files.newInputStream(path, NOFOLLOW_LINKS).use { it.readNBytes(1_048_577) }
    require(bytes.size in 2..1_048_576 && birthHash(bytes) == statusFile.sha256 &&
        Files.size(path) == bytes.size.toLong() && Hashes.sha256(path) == statusFile.sha256)
    val status = birthText(bytes)
    closedBirthFields(status, birthPointerKeys + setOf("postbirth_mutation_final_prefix", "stop_tick",
        "closed_monotonic_nanos", "shutdown_drain"))
    require(status.path("schema").isTextual &&
        status.path("schema").asText() == "bc.playtest_block_birth_status.v2")
    val absence = replayBlockBirthScope(store, parent, specs, catalogueSha256, bindings,
        epochId, session, evidence, bound)
    val drain = replayShutdownDrain(status, absence.chunkKeys.toSet(), requireEveryExpectedChunk = true)
    val prefix = status.deepCopy<ObjectNode>().apply {
        remove("shutdown_drain")
        put("schema", "bc.playtest_block_birth_prefix.v1")
    }
    return replayBlockBirthPrefix(store, parent, specs, catalogueSha256, bindings,
        epochId, session, evidence, bound, mailbox, prefix, stopped = true,
        requiredShutdownDrain = drain)
}
