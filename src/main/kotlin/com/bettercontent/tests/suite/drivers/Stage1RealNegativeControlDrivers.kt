package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

/** UNREGISTERED expected-negative diagnostics ONLY. A owns immutable roster, joined actor,
 * true native request/context, STOP v4, real shutdown, raw0 and direct disposable Store child.
 * A producer's self-report cannot certify a candidate, provider, world origin or normal scope. */
internal sealed class Stage1RealNegativeControlDriver(
    override val caseId: String, private val selector: String, private val actionId: String,
    private val schema: String, private val reason: String
) : CaseDriver {
    override val clientRequired = true // source=server still needs a real joined remote Survival actor.
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private val sha = Regex("[0-9a-f]{64}")
    private val emptySha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private data class Site(val dimension: String, val x: Int, val y: Int, val z: Int)
    private fun site(parameters: JsonNode): Site {
        require(parameters.isObject && parameters.fieldNames().asSequence().toSet() ==
            setOf("dimension", "x", "y", "z") && parameters.path("dimension").isTextual &&
            resource.matches(parameters.path("dimension").asText())) {
            "expected-negative site is only the exact precommitted dimension/x/y/z binding"
        }
        fun coordinate(name: String, range: IntRange): Int = parameters.path(name).let { value ->
            require(value.isIntegralNumber && value.canConvertToInt() && value.asInt() in range) {
                "expected-negative $name must be a bounded signed integer"
            }
            value.asInt()
        }
        return Site(parameters.path("dimension").asText(),
            coordinate("x", -30_000_000..30_000_000), coordinate("y", -2048..2048),
            coordinate("z", -30_000_000..30_000_000))
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val s = site(parameters)
        return listOf(DriverStep("one-original-$selector-diagnostic", DriverAction(actionId,
            suiteMapper.createObjectNode().put("player_uuid", "@observed-player-uuid")
                .put("scope_id", "@controller-mutation-scope-id-v1")
                .put("selector_id", selector).put("dimension", s.dimension)
                .put("x", s.x).put("y", s.y).put("z", s.z)
                .put("witness_context", "@controller-witness-context-v1"), 100, "server")))
    }
    private fun exact(row: JsonNode, keys: Set<String>) {
        require(row.isObject && row.fieldNames().asSequence().toSet() == keys) {
            "real expected-negative native receipt has missing/unknown fields"
        }
    }
    private fun boolean(row: JsonNode, key: String): Boolean = row.path(key).let {
        require(it.isBoolean) { "real expected-negative $key is not a Boolean" }; it.asBoolean()
    }
    private fun nonnegative(row: JsonNode, key: String): Long = row.path(key).let {
        require(it.isIntegralNumber && it.canConvertToLong() && it.asLong() >= 0) {
            "real expected-negative $key is not a bounded nonnegative count"
        }; it.asLong()
    }
    private fun state(row: JsonNode): String {
        exact(row, setOf("id", "properties"))
        require(row.path("id").isTextual && row.path("id").asText() in setOf(
            "minecraft:stone", "minecraft:air") && row.path("properties").isObject &&
            row.path("properties").isEmpty) { "one original saved stone/air cell is required" }
        return row.path("id").asText()
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val s = site(parameters)
        require(validateTerminalSequence(plan(parameters), observations) == null) {
            "one original server terminal and real joined client are required"
        }
        val row = observations.single().payload
        val common = setOf("schema", "scope_id", "selector_id", "selector_reviewed_by_native",
            "actual_actor_uuid", "position", "controller_declared_witness_context",
            "context_certified_by_native", "actual_pre_action_mutation_prefix", "resulting_prefix",
            "natural_origin_proven_by_native", "negative_control_certifies_candidate_provider_by_native",
            "block_mutation_pre_action_prefix")
        val added = if (selector == "section_array_exposed")
            setOf("actual_external_callsite", "expected_scope_failure") else
            setOf("actual_before_live_cell_state", "actual_after_live_cell_state", "public_method_call",
                "actual_failed_append_observed_path", "cap_overflow_proven_by_native")
        exact(row, common + added)
        val position = row.path("position")
        exact(position, setOf("dimension", "x", "y", "z"))
        val actor = row.path("actual_actor_uuid")
        val context = row.path("controller_declared_witness_context")
        require(row.path("schema").isTextual && row.path("schema").asText() == schema &&
            row.path("selector_id").isTextual && row.path("selector_id").asText() == selector &&
            row.path("scope_id").isTextual && row.path("scope_id").asText().isNotEmpty() &&
            actor.isTextual && runCatching { UUID.fromString(actor.asText()).toString() == actor.asText() }
                .getOrDefault(false) &&
            position.path("dimension").isTextual && position.path("dimension").asText() == s.dimension &&
            listOf("x", "y", "z").zip(listOf(s.x, s.y, s.z)).all { (name, value) ->
                position.path(name).isIntegralNumber && position.path(name).canConvertToInt() &&
                    position.path(name).asInt() == value
            } && context.isObject && context.path("case_id").asText() == caseId &&
            context.path("capability_id").asText() == actionId &&
            context.path("actor_uuid").asText() == actor.asText() &&
            context.path("source").asText() == "server" && context.path("purpose").asText() == "diagnostic" &&
            !boolean(row, "selector_reviewed_by_native") && !boolean(row, "context_certified_by_native") &&
            !boolean(row, "natural_origin_proven_by_native") &&
            !boolean(row, "negative_control_certifies_candidate_provider_by_native") &&
            row.path("block_mutation_pre_action_prefix") == row.path("actual_pre_action_mutation_prefix")) {
            "native negative terminal lacks an exact factual actor/site/diagnostic source shape"
        }
        val pre = row.path("actual_pre_action_mutation_prefix")
        val after = row.path("resulting_prefix")
        require(pre.isObject && after.isObject &&
            pre.path("schema").asText() == "bc.playtest_block_mutation_prefix.v2" &&
            after.path("schema").asText() == "bc.playtest_block_mutation_prefix.v2" &&
            pre.path("scope_id") == row.path("scope_id") && after.path("scope_id") == row.path("scope_id") &&
            pre.path("scope_file_sha256").isTextual && sha.matches(pre.path("scope_file_sha256").asText()) &&
            pre.path("scope_file_sha256") == after.path("scope_file_sha256") &&
            pre.path("source_checkpoint_sha256").isTextual &&
            sha.matches(pre.path("source_checkpoint_sha256").asText()) &&
            pre.path("source_checkpoint_sha256") == after.path("source_checkpoint_sha256") &&
            pre.path("lifecycle_context") == after.path("lifecycle_context") &&
            pre.path("lifecycle_context").path("purpose").asText() == "diagnostic" &&
            pre.path("lifecycle_context").path("source").asText() == "controller_prelaunch" &&
            nonnegative(pre, "bytes") == 0L && nonnegative(pre, "records") == 0L &&
            nonnegative(after, "bytes") == 0L && nonnegative(after, "records") == 0L &&
            pre.path("sha256").asText() == emptySha && after.path("sha256").asText() == emptySha &&
            !boolean(pre, "scope_failure") && !boolean(pre, "io_failure") &&
            !boolean(pre, "overflow") && !pre.has("failure_reason") &&
            !boolean(after, "overflow") && after.path("failure_reason").isTextual &&
            after.path("scope_failure").isBoolean && after.path("io_failure").isBoolean) {
            // A's raw/STOP v4 Store replay, not this prefix echo, makes any acceptance authoritative.
            "missing clean empty pre-action prefix or typed failed-scope/IO factual outcome"
        }
        // The two failures are mutually exclusive. A complete but wrong failure is a contradiction,
        // not incomplete capture; only A can bind raw0/STOP v4 and independently sealed saved child.
        if (selector == "section_array_exposed") require(
            row.path("actual_external_callsite").isTextual &&
                row.path("expected_scope_failure").isTextual) {
            "actual sections getter callsite/expected diagnostic reason missing"
        } else require(row.path("actual_failed_append_observed_path").isTextual &&
            row.path("cap_overflow_proven_by_native").isBoolean) {
            "actual original failed append path/cap separation missing"
        }
        val observedExpected = after.path("failure_reason").asText() == reason &&
            boolean(after, "scope_failure") == (selector == "section_array_exposed") &&
            boolean(after, "io_failure") == (selector == "cap_or_io_failure") &&
            (if (selector == "section_array_exposed") {
                row.path("actual_external_callsite").isTextual &&
                    row.path("actual_external_callsite").asText() ==
                        "LevelChunk.getSections() from native diagnostic action" &&
                    row.path("expected_scope_failure").isTextual &&
                    row.path("expected_scope_failure").asText() == reason
            } else {
                val before = state(row.path("actual_before_live_cell_state"))
                val result = state(row.path("actual_after_live_cell_state"))
                val call = row.path("public_method_call")
                exact(call, setOf("method", "requested_state", "actual_return_state"))
                val returned = call.path("actual_return_state").takeUnless { it.isNull }?.let(::state)
                row.path("actual_failed_append_observed_path").isTextual &&
                    row.path("actual_failed_append_observed_path").asText() ==
                        "PalettedContainer.getAndSet_private_index_return" &&
                    !boolean(row, "cap_overflow_proven_by_native") &&
                    call.path("method").isTextual &&
                    call.path("method").asText() == "LevelChunk.setBlockState(BlockPos,BlockState,boolean)" &&
                    state(call.path("requested_state")) != before &&
                    result != before && returned == before
            })
        return MeasurementVerdict(if (observedExpected) "passed" else "failed",
            if (observedExpected) "one actual $selector expected diagnostic-negative terminal observed; " +
                "ONLY A's later STOP v4/raw0/direct saved Store child replay may accept this isolated diagnostic, never a natural candidate/provider"
            else "complete $selector terminal contradicts the predeclared expected negative; not a missing-capture pass")
    }
}

internal object Stage1SectionsNegativeControlDriver : Stage1RealNegativeControlDriver(
    "control.mutation.sections-negative", "section_array_exposed",
    "block_mutation.control_expose_sections_v1",
    "bc.playtest_mutation_sections_external_negative.v1", "mutable_section_array_exposed")

internal object Stage1IoAppendNegativeControlDriver : Stage1RealNegativeControlDriver(
    "control.mutation.io-append-negative", "cap_or_io_failure",
    "block_mutation.control_io_append_failure_v1",
    "bc.playtest_mutation_io_append_negative.v2", "mutation_append_failed:ClosedChannelException")
