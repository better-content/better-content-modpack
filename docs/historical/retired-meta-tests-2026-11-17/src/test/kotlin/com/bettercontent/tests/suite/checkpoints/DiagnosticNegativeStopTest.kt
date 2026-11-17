package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Unrun regression source: a negative's failed STOP cannot strip an unverified shutdown field. */
class DiagnosticNegativeStopTest {
    private val chunk = BirthChunkKey("minecraft:overworld", 0, 0)
    private fun rows(): Triple<ObjectNode, ObjectNode, ObjectNode> {
        val pre = suiteMapper.createObjectNode().apply {
            put("schema", "bc.playtest_block_mutation_prefix.v2")
            put("scope_id", "scope-11111111-1111-1111-1111-111111111111")
            put("scope_failure", false); put("io_failure", false); put("overflow", false)
            put("bytes", 0); put("records", 0); put("sha256", "a".repeat(64))
            put("lifecycle_context", "pinned")
        }
        val result = pre.deepCopy().apply {
            put("scope_failure", true); put("failure_reason", "mutable_section_array_exposed")
        }
        val stop = result.deepCopy().apply {
            put("schema", "bc.playtest_block_mutation_status.v4")
            put("stop_tick", 20); put("closed_monotonic_nanos", 30)
            put("additional_observer_failure", false)
            set<ObjectNode>("shutdown_drain", suiteMapper.createObjectNode().apply {
                put("schema", "bc.playtest_mutation_shutdown_drain.v1")
                put("entry_observed", true); put("return_observed", true)
                put("entry_tick", 18); put("return_tick", 19)
                put("watched_unload_count", 1); put("retained_identity_count", 1)
                put("identity_and_state_match_at_close", true)
                put("unexpected_post_drain_activity", false)
                set<com.fasterxml.jackson.databind.node.ArrayNode>("watched_unloads",
                    suiteMapper.createArrayNode().add(suiteMapper.createObjectNode().apply {
                        put("dimension", chunk.dimension); put("chunk_x", 0); put("chunk_z", 0)
                    }))
            })
        }
        return Triple(pre, result, stop)
    }
    @Test fun nullAndWrongIdentityDoNotSatisfyNegativeStop() {
        val (pre, result, stop) = rows()
        replayFailedDiagnosticStop(pre, result, stop, setOf(chunk), "mutable_section_array_exposed")
        assertThrows(IllegalArgumentException::class.java) {
            replayFailedDiagnosticStop(pre, result, stop.deepCopy().apply { putNull("shutdown_drain") },
                setOf(chunk), "mutable_section_array_exposed")
        }
        assertThrows(IllegalArgumentException::class.java) {
            replayFailedDiagnosticStop(pre, result, stop.deepCopy().apply {
                path("shutdown_drain").let { (it as ObjectNode).put("identity_and_state_match_at_close", false) }
            }, setOf(chunk), "mutable_section_array_exposed")
        }
        assertThrows(IllegalArgumentException::class.java) {
            replayFailedDiagnosticStop(pre, result, stop.deepCopy().apply { put("scope_id", "other") },
                setOf(chunk), "mutable_section_array_exposed")
        }
        // A second identical exposure must not be hidden behind the preserved first reason.
        assertThrows(IllegalArgumentException::class.java) {
            replayFailedDiagnosticStop(pre, result, stop.deepCopy().apply {
                put("additional_observer_failure", true)
            }, setOf(chunk), "mutable_section_array_exposed")
        }
    }
}
