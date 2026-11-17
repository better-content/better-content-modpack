package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Unrun source-only checks: no manifest row may wrap a large integral ordinal into 1 or 2. */
class DiagnosticMutationControlPlanTest {
    @Test fun exactOneOrTwoOnly() {
        assertEquals(1, requirePredeclaredDiagnosticOrdinal(suiteMapper.readTree("1"), 0))
        assertEquals(2, requirePredeclaredDiagnosticOrdinal(suiteMapper.readTree("2"), 1))
        for (bad in listOf("4294967297", "9223372036854775807", "18446744073709551617",
            "1.0", "\"1\"", "true", "null")) {
            assertThrows(IllegalArgumentException::class.java) {
                requirePredeclaredDiagnosticOrdinal(suiteMapper.readTree(bad), 0)
            }
        }
    }
}
