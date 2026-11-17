package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode

/** UNREGISTERED single diagnostic CaseDriver. A binds a real joined client, exact prelaunch origin
 * scope and STOP+separate disposable output seal. This EXPECTED failure NEVER enters normal qualification. */
internal object Stage1ExternalGetterNegativeControlDriver : CaseDriver {
    override val caseId = "control.mutation.external-getter-negative"
    override val clientRequired = true
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private val sha = Regex("[0-9a-f]{64}")
    private const val SELECTOR = "state_container_exposed"
    private const val SCOPE = "@controller-mutation-scope-id-v1"
    private const val CHECKPOINT = "@controller-input-checkpoint-sha256-v1"
    private data class Target(val dimension: String, val x: Int, val y: Int, val z: Int)
    private fun parse(parameters: JsonNode): Target {
        require(parameters.isObject && parameters.fieldNames().asSequence().toSet() ==
            setOf("dimension", "x", "y", "z", "source_checkpoint_sha256")) {
            "one exact immutable diagnostic origin target required"
        }
        val dim = parameters.path("dimension").asText("")
        val checkpointMarker = parameters.path("source_checkpoint_sha256").asText("")
        fun number(name: String, range: IntRange): Int = parameters.path(name).let { v ->
            require(v.isIntegralNumber && v.canConvertToInt() && v.asInt() in range) { "unreviewed $name" }
            v.asInt()
        }
        require(resource.matches(dim) && checkpointMarker == CHECKPOINT) {
            "diagnostic input checkpoint must be resolved by controller after Store seal, not preauthored"
        }
        return Target(dim, number("x", -30_000_000..30_000_000), number("y", -2048..2048),
            number("z", -30_000_000..30_000_000))
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val p = parse(parameters)
        return listOf(DriverStep("one-actual-external-states-getter", DriverAction(
            "block_mutation.control_expose_states_v1",
            suiteMapper.createObjectNode().put("scope_id", SCOPE).put("selector_id", SELECTOR)
                .put("dimension", p.dimension).put("x", p.x).put("y", p.y).put("z", p.z), 100)))
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val p = parse(parameters)
        require(validateTerminalSequence(plan(parameters), observations) == null) {
            "one exact diagnostic real-getter terminal required; A separately authenticates joined client"
        }
        val row = observations.single().payload
        val prefix = row.path("resulting_prefix"); val context = prefix.path("lifecycle_context")
        require(row.path("schema").asText() == "bc.playtest_mutation_external_getter_negative.v1" &&
            row.path("selector_id").asText() == SELECTOR &&
            row.path("selector_reviewed_by_native").isBoolean &&
            !row.path("selector_reviewed_by_native").asBoolean() &&
            row.path("scope_id").asText("").isNotEmpty() &&
            row.path("epoch_id").asText("").isNotEmpty() &&
            row.path("native_session_id").asText("").isNotEmpty() &&
            sha.matches(row.path("source_checkpoint_sha256").asText("")) &&
            row.path("dimension").asText() == p.dimension &&
            listOf("x", "y", "z").zip(listOf(p.x, p.y, p.z))
                .all { (field, value) -> row.path(field).isIntegralNumber && row.path(field).asInt() == value } &&
            row.path("real_callsite").asText() == "LevelChunkSection.getStates() from native diagnostic action" &&
            row.path("expected_scope_failure").asText() == "mutable_state_container_exposed" &&
            row.path("natural_origin_proven_by_native").isBoolean &&
            !row.path("natural_origin_proven_by_native").asBoolean() &&
            row.path("negative_control_certifies_candidate_provider_by_native").isBoolean &&
            !row.path("negative_control_certifies_candidate_provider_by_native").asBoolean() &&
            prefix.path("schema").asText() == "bc.playtest_block_mutation_prefix.v2" &&
            prefix.path("scope_id") == row.path("scope_id") &&
            prefix.path("source_checkpoint_sha256") == row.path("source_checkpoint_sha256") &&
            sha.matches(prefix.path("scope_file_sha256").asText("")) &&
            context.path("epoch_id") == row.path("epoch_id") &&
            context.path("native_session_id") == row.path("native_session_id") &&
            context.path("purpose").asText() == "diagnostic" &&
            prefix.path("scope_failure").isBoolean &&
            prefix.path("overflow").isBoolean && !prefix.path("overflow").asBoolean() &&
            prefix.path("io_failure").isBoolean && !prefix.path("io_failure").asBoolean()) {
            "isolated diagnostic scope/projection binding incomplete"
        }
        val expectedFailure = prefix.path("scope_failure").asBoolean() &&
            prefix.path("failure_reason").asText() == "mutable_state_container_exposed"
        return MeasurementVerdict(if (expectedFailure) "passed" else "failed",
            if (expectedFailure) "one actual external getter triggered ONLY expected diagnostic stage1 scope failure; candidate and normal qualifier NOT certified"
            else "complete diagnostic getter receipt contradicts exact expected mutable alias scope failure",
            mapOf("diagnostic_negative_only" to true, "scope_id" to row.path("scope_id").asText(),
                "native_reported_source_checkpoint_sha256_NOT_controller_certified" to row.path("source_checkpoint_sha256").asText(),
                "actual_epoch_id" to row.path("epoch_id").asText(),
                "expected_scope_failure" to "mutable_state_container_exposed"))
    }
}
