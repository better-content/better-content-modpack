package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.NativeRequest
import com.bettercontent.tests.suite.idPattern
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

/** Covered nonexposing paths ONLY. getStates/getSections leak mutable handles and are NOT positives. */
internal val stage1SafePositiveControlIds = setOf("level_chunk_delegated", "direct_section_setter", "transient_restoration")
internal val stage1RequiredNegativeControlIds = setOf("container_indexed_getandset", "container_indexed_set",
    "container_read_replace", "birth_or_load_unowned", "chunk_unload", "section_array_exposed",
    "state_container_exposed", "palette_alias_or_identity_drift", "unsupported_provider", "cap_or_io_failure",
    "preownership_unknown")
internal data class ReviewedMutationTransition(val ordinal: Int, val observedPath: String,
    val oldState: Pair<String, Map<String, String>>, val newState: Pair<String, Map<String, String>>) {
    init { require(ordinal in 1..100_000 && observedPath in setOf(
        "PalettedContainer.getAndSet_private_index_return", "PalettedContainer.set_private_index_return",
        "PalettedContainer.read_full_replacement") && oldState != newState) }
}
internal data class VerifiedPositiveMutationControl(val controlId: String, val input: CheckpointRef,
    val output: CheckpointRef, val scope: BoundFile, val status: BoundFile, val position: ScopedBlockPosition,
    val transitions: List<ReviewedMutationTransition>, val nativeInterval: ScopedMutationPrefix) {
    init { require(controlId in stage1SafePositiveControlIds && transitions.isNotEmpty() &&
        input != output && nativeInterval.records == transitions.size) }
}
private val controlJson = suiteMapper.copy().apply { factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION) }
private fun controlRow(bytes: ByteArray): JsonNode {
    val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    return controlJson.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text)
}
private fun controlState(row: JsonNode): Pair<String, Map<String, String>> {
    require(row.isObject && row.fieldNames().asSequence().toSet() == setOf("id", "properties") &&
        row.path("id").isTextual && row.path("id").asText().matches(com.bettercontent.tests.suite.resourcePattern))
    val props = row.path("properties")
    require(props.isObject && props.size() <= 64)
    return row.path("id").asText() to props.fields().asSequence().associate { (key, value) ->
        require(key.matches(Regex("[a-z0-9_]{1,64}")) && value.isTextual && value.asText().length in 1..128)
        key to value.asText()
    }.toSortedMap()
}

/**
 * One actual disposable-world positive control: independently sealed before/after terrain and
 * exact B v2 source→mutations→stop bytes. This is necessary but NOT a complete coverage/profile
 * approval or proof of transformed live Mixin bytes. The gate must also verify installed jars,
 * runtime loaded classes, negative routes, source lifetime, and reviewer-approved provider paths.
 */
internal fun replayPositiveMutationControl(store: CheckpointStore, input: CheckpointRef,
    output: CheckpointRef, scope: BoundFile, mailbox: Path, status: BoundFile,
    bindings: CheckpointBindings, epochId: String, nativeSessionId: String, controlId: String,
    position: ScopedBlockPosition, expected: List<ReviewedMutationTransition>,
    action: NativeRequest, terminal: JsonNode, context: WitnessContext,
    reviewedActionId: String, reviewedActorUuid: String): VerifiedPositiveMutationControl {
    require(reviewedActionId.matches(idPattern) && reviewedActionId == "block_mutation.control_positive_v1" &&
        action.driver == reviewedActionId && action.parameters.isObject &&
        action.parameters.fieldNames().asSequence().toSet() == setOf("player_uuid", "control_id", "scope_id",
            "dimension", "x", "y", "z", "witness_context") &&
        action.runId == nativeSessionId && action.caseId == context.caseId &&
        action.sequence == context.requestSequence && context.epochId == epochId &&
        context.nativeSessionId == nativeSessionId && context.actorUuid == reviewedActorUuid &&
        context.capabilityId == reviewedActionId && context.source == "server" &&
        context.purpose == bindings.purpose && action.parameters.path("player_uuid").asText() == reviewedActorUuid &&
        action.parameters.path("control_id").isTextual &&
        action.parameters.path("control_id").asText() == controlId &&
        action.parameters.path("dimension").isTextual &&
        action.parameters.path("dimension").asText() == position.dimension &&
        action.parameters.path("x").isIntegralNumber && action.parameters.path("x").canConvertToInt() &&
        action.parameters.path("x").asInt() == position.x &&
        action.parameters.path("y").isIntegralNumber && action.parameters.path("y").canConvertToInt() &&
        action.parameters.path("y").asInt() == position.y &&
        action.parameters.path("z").isIntegralNumber && action.parameters.path("z").canConvertToInt() &&
        action.parameters.path("z").asInt() == position.z &&
        action.parameters.path("witness_context") == suiteMapper.valueToTree<JsonNode>(context) &&
        controlId in stage1SafePositiveControlIds && expected.size in 1..64 &&
        expected.map { it.ordinal } == (1..expected.size).toList() &&
        bindings.purpose == ExecutionPurpose.DIAGNOSTIC.wire && scope.sha256.matches(shaPattern) &&
        status.sha256.matches(shaPattern)) { "control needs a reviewed ordered safe-route expectation, not a producer success flag" }
    val source = store.verify(input)
    val final = store.verify(output)
    require(source.bindings == bindings && final.bindings == bindings && final.parent == input &&
        final.sourceEpoch == epochId && final.closedEpoch.epochId == epochId &&
        final.closedEpoch.nativeSessionId == nativeSessionId && final.closedEpoch.saveValidated &&
        final.closedEpoch.processCleanupComplete && final.closedEpoch.survivingPids.isEmpty() &&
        source.closedEpoch.processCleanupComplete && source.closedEpoch.survivingPids.isEmpty()) {
        "control output was not saved/shut down/sealed from precisely this disposable source epoch"
    }
    val original = readSealedBlockBaseline(store, input, position.dimension, position.x, position.y, position.z)
    val persisted = readSealedBlockBaseline(store, output, position.dimension, position.x, position.y, position.z)
    require((original.blockId to original.properties) == expected.first().oldState &&
        (persisted.blockId to persisted.properties) == expected.last().newState &&
        expected.zipWithNext().all { (left, right) -> left.newState == right.oldState }) {
        "reviewed transition source/final or internal continuity differs from independent saved blocks"
    }
    val config = Path.of(scope.path)
    require(config.parent == mailbox.toAbsolutePath().normalize().parent &&
        config.fileName.toString() == "target-mutation-scope.json" &&
        Files.isRegularFile(config, NOFOLLOW_LINKS) && !Files.isSymbolicLink(config) &&
        Files.size(config) in 2..1_048_576 && Hashes.sha256(config) == scope.sha256)
    val configBytes = Files.newInputStream(config, NOFOLLOW_LINKS).use { it.readNBytes(1_048_577) }
    require(configBytes.size in 2..1_048_576 &&
        MessageDigest.getInstance("SHA-256").digest(configBytes).joinToString("") { "%02x".format(it) } == scope.sha256)
    val configNode = controlRow(configBytes)
    require(configNode.path("scope_id").isTextual &&
        action.parameters.path("scope_id").isTextual &&
        action.parameters.path("scope_id").asText() == configNode.path("scope_id").asText() &&
        configNode.path("positions").isArray && configNode.path("positions").size() == 1 &&
        configNode.path("positions")[0] == suiteMapper.valueToTree<JsonNode>(position) &&
        configNode.path("source_checkpoint_id").asText() == input.id &&
        configNode.path("source_checkpoint_sha256").asText() == input.receipt.sha256 &&
        Hashes.sha256(config) == scope.sha256)
    val originalState = original.blockId to original.properties
    val opposite = (if (originalState.first == "minecraft:stone") "minecraft:air" else "minecraft:stone") to
        emptyMap<String, String>()
    val expectedCount = if (controlId == "transient_restoration") 2 else 1
    require(originalState.second.isEmpty() && originalState.first in setOf("minecraft:stone", "minecraft:air") &&
        expected.size == expectedCount && expected.first().newState == opposite &&
        (if (expectedCount == 2) expected[1].newState == originalState else expected.last().newState == opposite) &&
        expected.all { it.observedPath == "PalettedContainer.getAndSet_private_index_return" }) {
        "reviewed positive route is not an exact stone/air original public-setter transition"
    }
    val terminalKeys = setOf("schema", "scope_id", "control_id", "actual_actor_uuid", "position",
        "actual_before_live_cell_state", "actual_after_live_cell_state", "public_method_calls",
        "public_method_call_count", "level_chunk_setter_calls", "direct_section_setter_calls",
        "actual_pre_action_mutation_prefix", "actual_post_action_mutation_prefix",
        "controller_declared_witness_context", "context_certified_by_native", "observation_complete",
        "diagnostic_chunk_save_metadata", "natural_origin_proven_by_native", "candidate_provider_certified_by_native",
        "block_mutation_pre_action_prefix")
    require(terminal.isObject && terminal.fieldNames().asSequence().toSet() == terminalKeys &&
        terminal.path("schema").isTextual &&
        terminal.path("schema").asText() == "bc.playtest_mutation_positive_control.v2" &&
        terminal.path("scope_id").isTextual &&
        terminal.path("scope_id").asText() == configNode.path("scope_id").asText() &&
        terminal.path("control_id").isTextual && terminal.path("control_id").asText() == controlId &&
        terminal.path("actual_actor_uuid").isTextual &&
        terminal.path("actual_actor_uuid").asText() == reviewedActorUuid &&
        terminal.path("position") == suiteMapper.valueToTree<JsonNode>(position) &&
        controlState(terminal.path("actual_before_live_cell_state")) == originalState &&
        controlState(terminal.path("actual_after_live_cell_state")) == expected.last().newState &&
        terminal.path("controller_declared_witness_context") == suiteMapper.valueToTree<JsonNode>(context) &&
        terminal.path("actual_pre_action_mutation_prefix") ==
            terminal.path("block_mutation_pre_action_prefix") &&
        terminal.path("context_certified_by_native").isBoolean &&
        !terminal.path("context_certified_by_native").asBoolean() &&
        terminal.path("observation_complete").isBoolean && terminal.path("observation_complete").asBoolean() &&
        listOf("natural_origin_proven_by_native", "candidate_provider_certified_by_native").all {
            terminal.path(it).isBoolean && !terminal.path(it).asBoolean()
        }) { "positive producer terminal is not the exact bound, complete factual action receipt" }
    fun exactCount(key: String, value: Int): Boolean = terminal.path(key).let {
        it.isIntegralNumber && it.canConvertToInt() && it.asInt() == value
    }
    require(exactCount("public_method_call_count", expectedCount) &&
        exactCount("level_chunk_setter_calls", if (controlId == "direct_section_setter") 0 else expectedCount) &&
        exactCount("direct_section_setter_calls", if (controlId == "direct_section_setter") 1 else 0))
    val dirty = terminal.path("diagnostic_chunk_save_metadata")
    if (controlId == "direct_section_setter") require(dirty.isObject &&
        dirty.fieldNames().asSequence().toSet() == setOf("method", "owner_chunk_x", "owner_chunk_z",
            "unsaved_before", "requested_unsaved", "unsaved_after", "diagnostic_only",
            "save_to_disk_proven_by_native") &&
        dirty.path("method").asText() == "ChunkAccess.setUnsaved(boolean)" &&
        dirty.path("owner_chunk_x").isIntegralNumber && dirty.path("owner_chunk_x").canConvertToInt() &&
        dirty.path("owner_chunk_x").asInt() == position.x shr 4 &&
        dirty.path("owner_chunk_z").isIntegralNumber && dirty.path("owner_chunk_z").canConvertToInt() &&
        dirty.path("owner_chunk_z").asInt() == position.z shr 4 &&
        dirty.path("unsaved_before").isBoolean &&
        listOf("requested_unsaved", "unsaved_after", "diagnostic_only")
            .all { dirty.path(it).isBoolean && dirty.path(it).asBoolean() } &&
        dirty.path("save_to_disk_proven_by_native").isBoolean &&
        !dirty.path("save_to_disk_proven_by_native").asBoolean()) {
        "direct section setter lacks exact same-owner diagnostic dirty-mark fact"
    } else require(dirty.isNull) {
        "delegated/transient setter cannot claim a separate diagnostic dirty-mark action"
    }
    val calls = terminal.path("public_method_calls")
    require(calls.isArray && calls.size() == expectedCount)
    calls.forEachIndexed { index, call ->
        val route = if (controlId == "direct_section_setter")
            "LevelChunkSection.setBlockState(int,int,int,BlockState,boolean)"
        else "LevelChunk.setBlockState(BlockPos,BlockState,boolean)"
        require(call.isObject && call.fieldNames().asSequence().toSet() ==
            setOf("method", "requested_state", "actual_return_state") &&
            call.path("method").isTextual && call.path("method").asText() == route &&
            controlState(call.path("requested_state")) == expected[index].newState &&
            !call.path("actual_return_state").isNull &&
            controlState(call.path("actual_return_state")) == expected[index].oldState) {
            "public setter method/request/actual old-state return contradicts reviewed raw transition"
        }
    }
    val actionPrefix = replaySavedBlockMutationPrefix(store, input, scope, mailbox,
        terminal.path("block_mutation_pre_action_prefix"), bindings, epochId, nativeSessionId, pristine = false)
    require(actionPrefix.records == 0 && !actionPrefix.fallbackIncomplete) {
        "control actuation lacks a clean exact B v2 pre-dispatch prefix"
    }
    val observed = verifyStoppedBlockMutationStatus(store, input, scope, mailbox, status,
        bindings, epochId, nativeSessionId, pristine = false, requireShutdownDrain = true)
    val post = terminal.path("actual_post_action_mutation_prefix")
    val postReplay = replaySavedBlockMutationPrefix(store, input, scope, mailbox,
        post, bindings, epochId, nativeSessionId, pristine = false)
    val stop = controlRow(capturedEvidenceBytes(status, 1_048_576))
    val stopAsPrefix = stop.deepCopy<ObjectNode>().apply {
        remove("shutdown_drain"); remove("stop_tick"); remove("closed_monotonic_nanos")
        put("schema", "bc.playtest_block_mutation_prefix.v2")
    }
    require(stopAsPrefix == post && postReplay.records == expected.size &&
        postReplay.sha256 == observed.sha256 && postReplay.byteCount == observed.byteCount &&
        observed.records == expected.size && observed.changes.all { it == position } &&
        !observed.fallbackIncomplete && Files.size(observed.file) == observed.byteCount.toLong() &&
        Hashes.sha256(observed.file) == observed.sha256) {
        "raw stopped positive control has an omitted/restored/unsupported transition"
    }
    val bytes = Files.newInputStream(observed.file, NOFOLLOW_LINKS).use { it.readNBytes(16_777_217) }
    require(bytes.size == observed.byteCount &&
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == observed.sha256)
    val lines = mutableListOf<ByteArray>(); var start = 0
    bytes.indices.filter { bytes[it] == '\n'.code.toByte() }.forEach { end ->
        val line = bytes.copyOfRange(start, end + 1)
        require(line.size in 2..65_536 && line[line.lastIndex - 1] != '\r'.code.toByte())
        lines += line; start = end + 1
    }
    require(start == bytes.size && lines.size == expected.size)
    lines.zip(expected).forEach { (line, reviewed) ->
        val event = controlRow(line)
        require(event.path("index").isIntegralNumber && event.path("index").asInt(-1) == reviewed.ordinal &&
            event.path("observed_path").asText() == reviewed.observedPath &&
            controlState(event.path("old_state")) == reviewed.oldState &&
            controlState(event.path("new_state")) == reviewed.newState &&
            event.path("dimension").asText() == position.dimension &&
            event.path("x").asInt(Int.MIN_VALUE) == position.x &&
            event.path("y").asInt(Int.MIN_VALUE) == position.y &&
            event.path("z").asInt(Int.MIN_VALUE) == position.z) {
            "actual B v2 path/old/new row differs from independently reviewed positive route"
        }
    }
    require(Hashes.sha256(observed.file) == observed.sha256 && Hashes.sha256(config) == scope.sha256)
    return VerifiedPositiveMutationControl(controlId, input, output, scope, status, position, expected, observed)
}
