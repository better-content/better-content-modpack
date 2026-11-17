package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

/** Opt-in DIAGNOSTIC source only. The parent, roster, actor, raw STOP v3 and saved child are
 * authenticated by A, never by this producer echo or this local verdict. */
internal sealed class Stage1PositiveControlDriver(
    override val caseId: String, private val controlId: String
) : CaseDriver {
    override val clientRequired = true
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private val sha = Regex("[0-9a-f]{64}")
    private val emptySha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private data class Site(val dimension: String, val x: Int, val y: Int, val z: Int)
    private fun site(parameters: JsonNode): Site {
        require(parameters.isObject && parameters.fieldNames().asSequence().toSet() ==
            setOf("dimension", "x", "y", "z") && parameters.path("dimension").isTextual &&
            resource.matches(parameters.path("dimension").asText())) {
            "diagnostic positive site must have only four precommitted dimension/x/y/z fields"
        }
        fun coordinate(name: String, range: IntRange): Int = parameters.path(name).let { value ->
            require(value.isIntegralNumber && value.canConvertToInt() && value.asInt() in range) {
                "diagnostic positive $name is not a bounded integer"
            }
            value.asInt()
        }
        return Site(parameters.path("dimension").asText(), coordinate("x", -30_000_000..30_000_000),
            coordinate("y", -2048..2048), coordinate("z", -30_000_000..30_000_000))
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val target = site(parameters)
        return listOf(DriverStep("one-original-$controlId-diagnostic", DriverAction(
            "block_mutation.control_positive_v1", suiteMapper.createObjectNode()
                .put("player_uuid", "@observed-player-uuid").put("control_id", controlId)
                .put("scope_id", "@controller-mutation-scope-id-v1")
                .put("dimension", target.dimension).put("x", target.x).put("y", target.y).put("z", target.z)
                .put("witness_context", "@controller-witness-context-v1"), 100, "server")))
    }
    private fun exact(row: JsonNode, keys: Set<String>) {
        require(row.isObject && row.fieldNames().asSequence().toSet() == keys) {
            "diagnostic positive terminal has missing/unknown fields"
        }
    }
    private fun bool(row: JsonNode, key: String): Boolean = row.path(key).let {
        require(it.isBoolean) { "positive $key is not a Boolean" }; it.asBoolean()
    }
    private fun count(row: JsonNode, key: String, maximum: Int = 100_000): Int = row.path(key).let {
        require(it.isIntegralNumber && it.canConvertToInt() && it.asInt() in 0..maximum) {
            "positive $key is not a bounded count"
        }; it.asInt()
    }
    private fun state(row: JsonNode): String {
        exact(row, setOf("id", "properties"))
        require(row.path("id").isTextual && resource.matches(row.path("id").asText()) &&
            row.path("properties").isObject && row.path("properties").size() <= 64 &&
            row.path("properties").fields().asSequence().all { (key, value) ->
                key.matches(Regex("[a-z0-9_]{1,64}")) && value.isTextual && value.asText().length in 1..128
            }) { "positive state shape is malformed" }
        return row.path("id").asText() + ":" + row.path("properties").toString()
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val target = site(parameters)
        require(validateTerminalSequence(plan(parameters), observations) == null) {
            "one actual positive native terminal with joined remote Survival actor required"
        }
        val row = observations.single().payload
        val required = setOf("schema", "scope_id", "control_id", "actual_actor_uuid", "position",
            "actual_before_live_cell_state", "actual_after_live_cell_state", "public_method_calls",
            "public_method_call_count", "diagnostic_chunk_save_metadata", "level_chunk_setter_calls",
            "direct_section_setter_calls", "actual_pre_action_mutation_prefix", "actual_post_action_mutation_prefix",
            "controller_declared_witness_context", "context_certified_by_native", "observation_complete",
            "natural_origin_proven_by_native", "candidate_provider_certified_by_native",
            "block_mutation_pre_action_prefix")
        val complete = bool(row, "observation_complete")
        exact(row, required + if (complete) emptySet() else setOf("unsupported_reason"))
        if (!complete) {
            require(row.path("unsupported_reason").isTextual && row.path("unsupported_reason").asText().isNotBlank())
            throw IllegalArgumentException("positive native capture incomplete: ${row.path("unsupported_reason").asText()}")
        }
        val actor = row.path("actual_actor_uuid")
        val position = row.path("position")
        exact(position, setOf("dimension", "x", "y", "z"))
        val context = row.path("controller_declared_witness_context")
        require(row.path("schema").asText() == "bc.playtest_mutation_positive_control.v2" &&
            row.path("control_id").asText() == controlId && row.path("scope_id").isTextual &&
            row.path("scope_id").asText().isNotEmpty() && actor.isTextual &&
            runCatching { UUID.fromString(actor.asText()).toString() == actor.asText() }.getOrDefault(false) &&
            position.path("dimension").asText() == target.dimension &&
            listOf("x", "y", "z").zip(listOf(target.x, target.y, target.z)).all { (key, value) ->
                position.path(key).isIntegralNumber && position.path(key).canConvertToInt() &&
                    position.path(key).asInt() == value
            } && context.isObject && context.path("case_id").asText() == caseId &&
            context.path("capability_id").asText() == "block_mutation.control_positive_v1" &&
            context.path("actor_uuid").asText() == actor.asText() &&
            context.path("source").asText() == "server" && context.path("purpose").asText() == "diagnostic" &&
            !bool(row, "context_certified_by_native") && !bool(row, "natural_origin_proven_by_native") &&
            !bool(row, "candidate_provider_certified_by_native") &&
            row.path("actual_pre_action_mutation_prefix") == row.path("block_mutation_pre_action_prefix")) {
            "positive terminal lacks an exact factual actor/site/context receipt"
        }
        val pre = row.path("actual_pre_action_mutation_prefix")
        val post = row.path("actual_post_action_mutation_prefix")
        require(pre.isObject && post.isObject &&
            pre.path("schema").asText() == "bc.playtest_block_mutation_prefix.v2" &&
            post.path("schema").asText() == "bc.playtest_block_mutation_prefix.v2" &&
            pre.path("scope_id") == row.path("scope_id") && post.path("scope_id") == row.path("scope_id") &&
            pre.path("scope_file_sha256").isTextual && sha.matches(pre.path("scope_file_sha256").asText()) &&
            pre.path("scope_file_sha256") == post.path("scope_file_sha256") &&
            pre.path("source_checkpoint_sha256").isTextual &&
            sha.matches(pre.path("source_checkpoint_sha256").asText()) &&
            pre.path("source_checkpoint_sha256") == post.path("source_checkpoint_sha256") &&
            pre.path("lifecycle_context") == post.path("lifecycle_context") &&
            pre.path("lifecycle_context").path("purpose").asText() == "diagnostic" &&
            pre.path("lifecycle_context").path("source").asText() == "controller_prelaunch" &&
            count(pre, "bytes") == 0 && count(pre, "records") == 0 &&
            pre.path("sha256").asText() == emptySha &&
            !bool(pre, "scope_failure") && !bool(pre, "overflow") && !bool(pre, "io_failure") &&
            !pre.has("failure_reason") && post.path("sha256").isTextual &&
            sha.matches(post.path("sha256").asText())) {
            "positive terminal lacks clean pre-action scope or typed post-action prefix"
        }
        val before = state(row.path("actual_before_live_cell_state"))
        val after = state(row.path("actual_after_live_cell_state"))
        val stone = "minecraft:stone:{}"; val air = "minecraft:air:{}"
        val opposite = if (before == stone) air else stone
        val transient = controlId == "transient_restoration"
        val direct = controlId == "direct_section_setter"
        val expectedCount = if (transient) 2 else 1
        val calls = row.path("public_method_calls")
        require(calls.isArray && calls.size() <= 2)
        val requested = if (transient) listOf(opposite, before) else listOf(opposite)
        val prior = if (transient) listOf(before, opposite) else listOf(before)
        val route = if (direct) "LevelChunkSection.setBlockState(int,int,int,BlockState,boolean)"
            else "LevelChunk.setBlockState(BlockPos,BlockState,boolean)"
        val callsMatch = calls.mapIndexed { index, call ->
            exact(call, setOf("method", "requested_state", "actual_return_state"))
            val requestedState = state(call.path("requested_state"))
            val returned = call.path("actual_return_state").takeUnless { it.isNull }?.let(::state)
            index < expectedCount && call.path("method").isTextual && call.path("method").asText() == route &&
                requestedState == requested[index] && returned == prior[index]
        }.all { it } && calls.size() == expectedCount
        val dirty = row.path("diagnostic_chunk_save_metadata")
        val dirtyMatch = if (!direct) dirty.isNull else {
            exact(dirty, setOf("method", "owner_chunk_x", "owner_chunk_z", "unsaved_before",
                "requested_unsaved", "unsaved_after", "diagnostic_only", "save_to_disk_proven_by_native"))
            require(dirty.path("owner_chunk_x").isIntegralNumber && dirty.path("owner_chunk_x").canConvertToInt() &&
                dirty.path("owner_chunk_z").isIntegralNumber && dirty.path("owner_chunk_z").canConvertToInt() &&
                dirty.path("unsaved_before").isBoolean && dirty.path("method").isTextual)
            val requestedUnsaved = bool(dirty, "requested_unsaved")
            val unsavedAfter = bool(dirty, "unsaved_after")
            val diagnosticOnly = bool(dirty, "diagnostic_only")
            val saveProven = bool(dirty, "save_to_disk_proven_by_native")
            dirty.path("method").asText() == "ChunkAccess.setUnsaved(boolean)" &&
                dirty.path("owner_chunk_x").asInt() == target.x shr 4 &&
                dirty.path("owner_chunk_z").asInt() == target.z shr 4 &&
                requestedUnsaved && unsavedAfter && diagnosticOnly && !saveProven
        }
        // Parse the whole terminal before comparison: missing capture is not a contradiction.
        val publicCount = count(row, "public_method_call_count")
        val chunkCount = count(row, "level_chunk_setter_calls")
        val sectionCount = count(row, "direct_section_setter_calls")
        val records = count(post, "records")
        val bytes = count(post, "bytes", 16_777_216)
        val scopeFailure = bool(post, "scope_failure")
        val ioFailure = bool(post, "io_failure")
        val overflow = bool(post, "overflow")
        val matched = before in setOf(stone, air) && after == (if (transient) before else opposite) &&
            callsMatch && dirtyMatch && publicCount == expectedCount &&
            chunkCount == (if (direct) 0 else expectedCount) &&
            sectionCount == (if (direct) 1 else 0) && records == expectedCount && bytes > 0 &&
            post.path("sha256").asText() != emptySha && !scopeFailure && !ioFailure && !overflow &&
            !post.has("failure_reason")
        return MeasurementVerdict(if (matched) "passed" else "failed",
            if (matched) "one factual $controlId positive diagnostic route locally matched; ONLY A independent STOP v3/raw/Store saved child and preorigin roster replay can accept this disposable control, never a natural provider"
            else "complete $controlId positive diagnostic terminal contradicts the predeclared route; no missing-capture pass")
    }
}

internal object Stage1SetupDelegatedControlDriver : Stage1PositiveControlDriver(
    "control.mutation.setup-delegated", "level_chunk_delegated")
internal object Stage1DelegatedPositiveControlDriver : Stage1PositiveControlDriver(
    "control.mutation.delegated-positive", "level_chunk_delegated")
internal object Stage1SectionPositiveControlDriver : Stage1PositiveControlDriver(
    "control.mutation.section-positive", "direct_section_setter")
internal object Stage1TransientPositiveControlDriver : Stage1PositiveControlDriver(
    "control.mutation.transient-positive", "transient_restoration")
