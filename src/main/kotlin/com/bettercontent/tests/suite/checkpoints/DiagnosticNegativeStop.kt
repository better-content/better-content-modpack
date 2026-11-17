package com.bettercontent.tests.suite.checkpoints

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

/** A failed diagnostic STOP still requires the real same-owner shutdown, not merely a present field. */
internal fun replayFailedDiagnosticStop(pre: JsonNode, result: JsonNode, stop: JsonNode,
    watched: Set<BirthChunkKey>, reason: String, ioAppend: Boolean = false): ReplayedShutdownDrain {
    require(pre.isObject && result.isObject && stop.isObject && watched.size == 1)
    val baseline = pre.fieldNames().asSequence().toSet()
    require("failure_reason" !in baseline && "shutdown_drain" !in baseline &&
        result.fieldNames().asSequence().toSet() == baseline + "failure_reason" &&
        stop.fieldNames().asSequence().toSet() == baseline +
            setOf("failure_reason", "shutdown_drain", "stop_tick", "closed_monotonic_nanos",
                "additional_observer_failure") &&
        pre.path("schema").asText() == "bc.playtest_block_mutation_prefix.v2" &&
        result.path("schema").asText() == "bc.playtest_block_mutation_prefix.v2" &&
        stop.path("schema").asText() == "bc.playtest_block_mutation_status.v4" &&
        result.path("failure_reason").asText() == reason &&
        stop.path("failure_reason").asText() == reason &&
        stop.path("closed_monotonic_nanos").isIntegralNumber &&
        stop.path("closed_monotonic_nanos").canConvertToLong()) {
        "diagnostic failed STOP/prefix had missing or additional fields or reason drift"
    }
    require(stop.path("additional_observer_failure").isBoolean &&
        !stop.path("additional_observer_failure").asBoolean() &&
        (!ioAppend || (result.path("io_failure").isBoolean && result.path("io_failure").asBoolean() &&
            stop.path("io_failure").isBoolean && stop.path("io_failure").asBoolean()))) {
        "expected diagnostic had a secondary delivered observer/scope/STOP failure or missing sticky IO flag"
    }
    val changed = setOf("schema", "scope_failure", "io_failure", "overflow", "bytes", "sha256", "records")
    require((baseline - changed).all { stop.path(it) == pre.path(it) && result.path(it) == pre.path(it) }) {
        "diagnostic failure changed immutable lifecycle, ownership, path or adapter between pre and STOP"
    }
    val stopAsPrefix = stop.deepCopy<ObjectNode>().apply {
        remove("shutdown_drain"); remove("stop_tick"); remove("closed_monotonic_nanos")
        remove("additional_observer_failure")
        put("schema", "bc.playtest_block_mutation_prefix.v2")
    }
    require(stopAsPrefix == result &&
        pre.path("scope_failure").isBoolean && !pre.path("scope_failure").asBoolean() &&
        result.path("scope_failure").isBoolean && result.path("scope_failure").asBoolean() == !ioAppend &&
        stop.path("scope_failure").isBoolean && stop.path("scope_failure").asBoolean() == !ioAppend) {
        "diagnostic failed STOP differs from the captured resulting prefix"
    }
    return replayShutdownDrain(stop, watched, requireEveryExpectedChunk = true)
}
