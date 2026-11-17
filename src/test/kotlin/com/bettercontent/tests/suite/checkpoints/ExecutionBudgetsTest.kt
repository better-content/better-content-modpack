package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.drivers.DriverAction
import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

/** Source regressions only. No reviewed runtime calibration or test permission implied. */
class ExecutionBudgetsTest {
    @Test fun scaleWallAndNativeTickAllowancesAreIndependent() {
        val action = DriverAction("scale.begin", suiteMapper.createObjectNode(), 20_040)
        val valid = ActionAllowance("scale.begin", 900, 20_040)
        valid.check(action)
        assertEquals(900L, valid.wall.seconds)
        assertEquals(20_040, valid.nativeTimeoutTicks) // NOT 900*20; deliberately exceeds controller900s.
        assertThrows(IllegalArgumentException::class.java) { ActionAllowance("scale.begin", 1002, 20_040).check(action) }
        assertThrows(IllegalArgumentException::class.java) { ActionAllowance("scale.begin", 900, 18_000).check(action) }
        assertThrows(IllegalArgumentException::class.java) { valid.check(action.copy(timeoutTicks = 20_039)) }
    }

    @Test fun campaignDeadlineAndUnreviewedBudgetAreFailClosed() {
        val budget = ReviewedExecutionBudget(60, 120, 120, 10, 120, 30, 330, emptyMap(), emptyMap())
        assertEquals(Instant.parse("2026-11-16T00:05:30Z"), budget.campaignDeadline(Instant.parse("2026-11-16T00:00:00Z")))
        val missing = suiteMapper.createObjectNode().put("schema", "bc.playtest_execution_budget.v1")
        assertThrows(IllegalArgumentException::class.java) {
            ReviewedExecutionBudget.reviewed(missing, emptyList(),
                com.bettercontent.tests.suite.V2CampaignRequest(java.nio.file.Path.of("/home/dev"),
                    java.nio.file.Path.of("/home/dev/none"), missing,
                    com.bettercontent.tests.CandidatePair(java.nio.file.Path.of("/home/dev"),
                        java.nio.file.Path.of("/home/dev/none"), java.nio.file.Path.of("/home/dev/none"),
                        "a".repeat(64), "b".repeat(64)), emptyList(), emptyMap(), java.nio.file.Path.of("/home/dev/none"), true))
        }
    }
}
