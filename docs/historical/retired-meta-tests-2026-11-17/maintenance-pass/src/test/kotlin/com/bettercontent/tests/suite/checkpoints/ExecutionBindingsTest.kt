package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.drivers.DriverAction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Pure authoring regressions; no native processes or test runs authorized. */
class ExecutionBindingsTest {
    @Test fun `scale resolves only the retained ledger prefix and does not mutate reviewed input`() {
        val action = DriverAction("scale.begin", suiteMapper.createObjectNode().put("ledger_sha256", SAMPLING_LEDGER_BINDING), 18000)
        val resolved = resolveExecutionAction(action, "a".repeat(64))
        assertEquals("a".repeat(64), resolved.parameters.path("ledger_sha256").asText())
        assertEquals(SAMPLING_LEDGER_BINDING, action.parameters.path("ledger_sha256").asText())
        assertThrows(IllegalArgumentException::class.java) { resolveExecutionAction(action, null) }
        assertThrows(IllegalArgumentException::class.java) {
            resolveExecutionAction(action.copy(parameters = suiteMapper.createObjectNode().put("ledger_sha256", "b".repeat(64))), "a".repeat(64))
        }
    }

    private fun branches() = listOf(8, 16, 32).mapIndexed { index, size ->
        BranchSpec("cohort-$size", "origin", listOf("scale.$size"), null, emptyList(), "isolated", null,
            orderingDependencies = if (index == 0) emptyList() else listOf("cohort-${listOf(8, 16, 32)[index - 1]}"))
    }

    @Test fun `cohorts require ordered isolated origin epochs`() {
        verifyScaleBranchOrder(branches())
        assertThrows(IllegalArgumentException::class.java) { verifyScaleBranchOrder(branches().reversed()) }
        assertThrows(IllegalArgumentException::class.java) { verifyScaleBranchOrder(branches().dropLast(1)) }
        assertThrows(IllegalArgumentException::class.java) {
            verifyScaleBranchOrder(branches().map { if (it.id == "cohort-16") it.copy(orderingDependencies = emptyList()) else it })
        }
        assertThrows(IllegalArgumentException::class.java) {
            verifyScaleBranchOrder(branches().map { if (it.id == "cohort-32") it.copy(inputCheckpoint = "after-16") else it })
        }
    }
}
