package com.bettercontent.tests.suite.checkpoints

import com.fasterxml.jackson.databind.JsonNode

/** A diagnostic lineage is never convertible to qualification by selecting more cases later. */
internal enum class ExecutionPurpose(val wire: String) {
    QUALIFICATION("qualification"), DIAGNOSTIC("diagnostic");

    companion object {
        fun requireExplicit(node: JsonNode): ExecutionPurpose = when {
            !node.path("purpose").isTextual -> error("explicit v3 execution purpose required")
            node.path("purpose").asText() == QUALIFICATION.wire -> QUALIFICATION
            node.path("purpose").asText() == DIAGNOSTIC.wire -> DIAGNOSTIC
            else -> error("unknown v3 execution purpose")
        }
    }
}
