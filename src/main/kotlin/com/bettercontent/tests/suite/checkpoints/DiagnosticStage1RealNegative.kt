package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.NativeRequest
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** One expected diagnostic failure, NOT candidate/provider coverage or cap-overflow proof. */
internal data class ReplayedStage1Negative(val selector: String, val input: CheckpointRef,
    val disposableChild: CheckpointRef, val status: BoundFile)

/** Strictly reopens a real purpose-gated negative's failed STOP and direct saved child. */
internal fun replayStage1RealNegative(store: CheckpointStore, input: CheckpointRef,
    disposableChild: CheckpointRef, scope: BoundFile, status: BoundFile, mailbox: Path,
    position: ScopedBlockPosition, reviewedActor: String, action: NativeRequest,
    terminal: JsonNode, context: WitnessContext): ReplayedStage1Negative {
    val selector = when (action.driver) {
        "block_mutation.control_expose_sections_v1" -> "section_array_exposed"
        "block_mutation.control_io_append_failure_v1" -> "cap_or_io_failure"
        else -> error("not one of the two independently reviewed diagnostic negative actions")
    }
    val reason = if (selector == "section_array_exposed") "mutable_section_array_exposed"
        else "mutation_append_failed:ClosedChannelException"
    val parent = store.verify(input); val child = store.verify(disposableChild)
    require(parent.bindings.purpose == ExecutionPurpose.DIAGNOSTIC.wire &&
        child.bindings == parent.bindings && child.parent == input &&
        child.sourceEpoch == child.closedEpoch.epochId && child.closedEpoch.saveValidated &&
        child.closedEpoch.processCleanupComplete && child.closedEpoch.survivingPids.isEmpty() &&
        context.purpose == ExecutionPurpose.DIAGNOSTIC.wire && context.actorUuid == reviewedActor &&
        context.capabilityId == action.driver && context.source == "server" &&
        context.campaignId == parent.bindings.campaignId &&
        context.lineageId == parent.bindings.lineageId &&
        context.epochId == child.sourceEpoch && context.nativeSessionId == child.closedEpoch.nativeSessionId &&
        action.runId == context.nativeSessionId && action.sequence == context.requestSequence &&
        action.caseId == context.caseId && action.parameters.isObject &&
        action.parameters.fieldNames().asSequence().toSet() == setOf(
            "player_uuid", "scope_id", "selector_id", "dimension", "x", "y", "z", "witness_context") &&
        action.parameters.path("player_uuid").asText() == reviewedActor &&
        action.parameters.path("selector_id").asText() == selector &&
        action.parameters.path("witness_context") == suiteMapper.valueToTree<JsonNode>(context))
    val config = strictCapturedJson(capturedEvidenceBytes(scope, 1_048_576))
    require(config.path("purpose").asText() == ExecutionPurpose.DIAGNOSTIC.wire &&
        config.path("source_checkpoint_id").asText() == input.id &&
        config.path("source_checkpoint_sha256").asText() == input.receipt.sha256 &&
        config.path("epoch_id").asText() == child.sourceEpoch &&
        config.path("native_session_id").asText() == context.nativeSessionId &&
        config.path("positions").isArray && config.path("positions").size() == 1 &&
        config.path("positions")[0] == suiteMapper.valueToTree<JsonNode>(position) &&
        action.parameters.path("scope_id").asText() == config.path("scope_id").asText() &&
        action.parameters.path("dimension").asText() == position.dimension &&
        listOf("x", "y", "z").all { key -> action.parameters.path(key).isIntegralNumber &&
            action.parameters.path(key).canConvertToInt() } &&
        action.parameters.path("x").asInt() == position.x &&
        action.parameters.path("y").asInt() == position.y &&
        action.parameters.path("z").asInt() == position.z)
    val before = readSealedBlockBaseline(store, input, position.dimension, position.x, position.y, position.z)
    val after = readSealedBlockBaseline(store, disposableChild, position.dimension, position.x, position.y, position.z)
    require(before.properties.isEmpty() && before.blockId in setOf("minecraft:stone", "minecraft:air"))
    val actualBefore = before.blockId to before.properties
    val actualAfter = after.blockId to after.properties
    val opposite = (if (before.blockId == "minecraft:stone") "minecraft:air" else "minecraft:stone") to emptyMap<String, String>()
    require(actualAfter == if (selector == "section_array_exposed") actualBefore else opposite)
    val expected = mailbox.toAbsolutePath().normalize().resolve("mutations/${config.path("scope_id").asText()}.status.json")
    require(Path.of(status.path).toAbsolutePath().normalize() == expected)
    val stop = strictCapturedJson(capturedEvidenceBytes(status, 1_048_576))
    val trace = expected.parent.resolve("${config.path("scope_id").asText()}.jsonl")
    require(stop.path("schema").isTextual && stop.path("schema").asText() ==
        "bc.playtest_block_mutation_status.v4" &&
        stop.path("scope_id").asText() == config.path("scope_id").asText() &&
        stop.path("scope_file_sha256").asText() == scope.sha256 &&
        stop.path("source_checkpoint_id").asText() == input.id &&
        stop.path("source_checkpoint_sha256").asText() == input.receipt.sha256 &&
        stop.path("lifecycle_context") == suiteMapper.valueToTree<JsonNode>(mapOf(
            "campaign_id" to parent.bindings.campaignId, "lineage_id" to parent.bindings.lineageId,
            "epoch_id" to child.sourceEpoch, "native_session_id" to context.nativeSessionId,
            "purpose" to ExecutionPurpose.DIAGNOSTIC.wire, "source" to "controller_prelaunch")) &&
        stop.path("path").asText() == "mutations/${config.path("scope_id").asText()}.jsonl" &&
        stop.path("failure_reason").asText() == reason &&
        stop.path("scope_failure").isBoolean && stop.path("scope_failure").asBoolean() ==
            (selector == "section_array_exposed") &&
        stop.path("io_failure").isBoolean && stop.path("io_failure").asBoolean() ==
            (selector == "cap_or_io_failure") &&
        stop.path("overflow").isBoolean && !stop.path("overflow").asBoolean() &&
        stop.path("bytes").isIntegralNumber && stop.path("bytes").asLong(-1) == 0L &&
        stop.path("records").isIntegralNumber && stop.path("records").asLong(-1) == 0L &&
        stop.path("stop_tick").isIntegralNumber && stop.path("stop_tick").asLong(-1) >= 0L &&
        Files.isRegularFile(trace, NOFOLLOW_LINKS) && !Files.isSymbolicLink(trace) &&
        Files.size(trace) == 0L && stop.path("sha256").asText() == Hashes.sha256(trace)) {
        "real diagnostic negative STOP/raw/save was not the exact source-bound failure"
    }
    val schema = if (selector == "section_array_exposed")
        "bc.playtest_mutation_sections_external_negative.v1" else "bc.playtest_mutation_io_append_negative.v2"
    val common = setOf("schema", "scope_id", "selector_id", "selector_reviewed_by_native",
        "actual_actor_uuid", "position", "controller_declared_witness_context", "context_certified_by_native",
        "actual_pre_action_mutation_prefix", "resulting_prefix", "natural_origin_proven_by_native",
        "negative_control_certifies_candidate_provider_by_native", "block_mutation_pre_action_prefix")
    val added = if (selector == "section_array_exposed")
        setOf("actual_external_callsite", "expected_scope_failure") else
        setOf("actual_before_live_cell_state", "actual_after_live_cell_state", "public_method_call",
            "actual_failed_append_observed_path", "cap_overflow_proven_by_native")
    require(terminal.isObject && terminal.fieldNames().asSequence().toSet() == common + added &&
        terminal.path("schema").asText() == schema && terminal.path("selector_id").asText() == selector &&
        terminal.path("scope_id").asText() == config.path("scope_id").asText() &&
        terminal.path("actual_actor_uuid").asText() == reviewedActor &&
        terminal.path("position") == suiteMapper.valueToTree<JsonNode>(position) &&
        terminal.path("controller_declared_witness_context") == suiteMapper.valueToTree<JsonNode>(context) &&
        terminal.path("actual_pre_action_mutation_prefix") == terminal.path("block_mutation_pre_action_prefix") &&
        listOf("selector_reviewed_by_native", "context_certified_by_native",
            "natural_origin_proven_by_native", "negative_control_certifies_candidate_provider_by_native")
            .all { terminal.path(it).isBoolean && !terminal.path(it).asBoolean() })
    val pre = terminal.path("actual_pre_action_mutation_prefix")
    val result = terminal.path("resulting_prefix")
    val preReplay = replaySavedBlockMutationPrefix(store, input, scope, mailbox, pre,
        parent.bindings, child.sourceEpoch, context.nativeSessionId, pristine = input.id == "origin")
    replayFailedDiagnosticStop(pre, result, stop, setOf(birthChunkKey(position)), reason,
        ioAppend = selector == "cap_or_io_failure")
    require(preReplay.byteCount == 0 && preReplay.records == 0 &&
        pre.path("scope_id").asText() == config.path("scope_id").asText() &&
        pre.path("scope_failure").isBoolean && !pre.path("scope_failure").asBoolean() &&
        pre.path("io_failure").isBoolean && !pre.path("io_failure").asBoolean() &&
        pre.path("records").asLong(-1) == 0L && pre.path("bytes").asLong(-1) == 0L &&
        pre.path("sha256").asText() == Hashes.sha256(trace) &&
        result.path("scope_id").asText() == config.path("scope_id").asText() &&
        result.path("scope_failure").isBoolean && result.path("scope_failure").asBoolean() ==
            (selector == "section_array_exposed") &&
        result.path("failure_reason").asText() == reason &&
        result.path("bytes").asLong(-1) == 0L && result.path("records").asLong(-1) == 0L &&
        result.path("sha256").asText() == stop.path("sha256").asText())
    if (selector == "section_array_exposed") require(
        terminal.path("actual_external_callsite").asText() ==
            "LevelChunk.getSections() from native diagnostic action" &&
        terminal.path("expected_scope_failure").asText() == reason)
    else {
        fun state(row: JsonNode): Pair<String, Map<String, String>> {
            require(row.isObject && row.fieldNames().asSequence().toSet() == setOf("id", "properties") &&
                row.path("properties").isObject && row.path("properties").isEmpty && row.path("id").isTextual)
            return row.path("id").asText() to emptyMap()
        }
        val call = terminal.path("public_method_call")
        require(terminal.path("actual_failed_append_observed_path").asText() ==
            "PalettedContainer.getAndSet_private_index_return" &&
            terminal.path("cap_overflow_proven_by_native").isBoolean &&
            !terminal.path("cap_overflow_proven_by_native").asBoolean() &&
            state(terminal.path("actual_before_live_cell_state")) == actualBefore &&
            state(terminal.path("actual_after_live_cell_state")) == actualAfter &&
            call.isObject && call.fieldNames().asSequence().toSet() ==
                setOf("method", "requested_state", "actual_return_state") &&
            call.path("method").asText() == "LevelChunk.setBlockState(BlockPos,BlockState,boolean)" &&
            state(call.path("requested_state")) == opposite &&
            !call.path("actual_return_state").isNull &&
            state(call.path("actual_return_state")) == actualBefore &&
            result.path("io_failure").isBoolean && result.path("io_failure").asBoolean() &&
            result.path("overflow").isBoolean && !result.path("overflow").asBoolean())
    }
    return ReplayedStage1Negative(selector, input, disposableChild, status)
}
