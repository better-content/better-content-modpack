package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ExecutionPurposeTest {
    @Test fun missingUnknownOrAmbiguousPurposeNeverDefaultsToQualification() {
        listOf("{}", "{\"diagnostic\":false}", "{\"purpose\":null}", "{\"purpose\":\"qualified\"}",
            "{\"purpose\":\"QUALIFICATION\"}", "{\"purpose\":false}").forEach { text ->
            assertThrows(IllegalStateException::class.java) { ExecutionPurpose.requireExplicit(suiteMapper.readTree(text)) }
        }
        assertEquals(ExecutionPurpose.DIAGNOSTIC,
            ExecutionPurpose.requireExplicit(suiteMapper.readTree("{\"purpose\":\"diagnostic\"}")))
        assertEquals(ExecutionPurpose.QUALIFICATION,
            ExecutionPurpose.requireExplicit(suiteMapper.readTree("{\"purpose\":\"qualification\"}")))
    }

    @Test fun checkpointLineageRetainsDiagnosticPurposeEvenWhenCaseSelectionChanges() {
        val binding = CheckpointBindings("campaign-fixture", "lineage-fixture", emptyMap(), "", "",
            ExecutionPurpose.DIAGNOSTIC.wire)
        val serialized = suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(binding)
        assertEquals("diagnostic", serialized.path("purpose").asText())
        assertEquals(ExecutionPurpose.DIAGNOSTIC.wire,
            suiteMapper.treeToValue(serialized, CheckpointBindings::class.java).purpose)
        assertEquals(ExecutionPurpose.DIAGNOSTIC.wire, CheckpointBindings("campaign-fixture", "lineage-fixture", emptyMap(), "", "").purpose)
    }
}
