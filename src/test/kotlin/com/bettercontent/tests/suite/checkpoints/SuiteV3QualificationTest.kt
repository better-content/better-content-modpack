package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.drivers.*
import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Offline negative tests: no Minecraft process, candidate, or pack runtime is started. */
class SuiteV3QualificationTest {
    private val oracle = object : TrialCaseDriver {
        override val caseId = "case"
        override fun plan(parameters: JsonNode) = planTrial(request)
        override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>) =
            evaluateTrial(request, observations)
        override fun expandTrials(parameters: JsonNode, inputCheckpointId: String) = listOf(request)
        override fun planTrial(request: TrialRequest) = listOf(DriverStep("probe", DriverAction("probe.action",
            suiteMapper.createObjectNode())))
        override fun evaluateTrial(request: TrialRequest, observations: List<DriverObservation>) =
            MeasurementVerdict(if (observations.size == 1 && observations.single().payload.path("value").asInt(-1) == 7)
                "passed" else "failed", "independent sensor")
        override fun aggregate(parameters: JsonNode, requests: List<TrialRequest>, receipts: List<TrialReceipt>) =
            MeasurementVerdict(if (receipts.size == 1 && receipts.single().inputCheckpointVerified &&
                receipts.single().cleanupComplete && receipts.single().observations.single().payload.path("value").asInt(-1) == 7)
                "passed" else "failed", "independent aggregate")
    }
    private val request = TrialRequest("case", "trial", "target", "origin", suiteMapper.createObjectNode(),
        emptySet(), emptySet())
    private val valid = DriverObservation("probe", "probe.action", "server", "completed",
        suiteMapper.createObjectNode().put("value", 7))

    @Test fun selfReportedPassedCannotReplaceSensorOrNativeAction() {
        assertThrows(IllegalArgumentException::class.java) { requireV3TrialPassed(oracle, request, emptyList(), "passed") }
        assertThrows(IllegalArgumentException::class.java) {
            requireV3TrialPassed(oracle, request, listOf(valid.copy(driver = "other.action")), "passed")
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireV3TrialPassed(oracle, request, listOf(valid.copy(payload = suiteMapper.createObjectNode().put("value", 5))), "passed")
        }
        assertThrows(IllegalArgumentException::class.java) { requireV3TrialPassed(oracle, request, listOf(valid), "failed") }
        requireV3TrialPassed(oracle, request, listOf(valid), "passed")
    }

    @Test fun oldExecutionSchemaCannotBecomeV3ByClaimedPass() {
        for (kind in listOf("campaign", "case_result", "evidence_index", "epoch", "trial")) {
            assertThrows(IllegalArgumentException::class.java) {
                requireV3(kind, suiteMapper.createObjectNode().put("schema", "bc.playtest_$kind.v2").put("status", "passed"))
            }
        }
    }

    @Test fun rawNativeTokenCannotBeReusedAcrossEpochsOrSessions() {
        val token = "a".repeat(64)
        val first = suiteMapper.createObjectNode().put("token", token).put("run_id", "epoch-one")
        val seen = mutableSetOf<String>()
        requireFreshV3EpochToken(listOf(first), "epoch-one", seen)
        assertThrows(IllegalArgumentException::class.java) {
            requireFreshV3EpochToken(listOf(first.deepCopy().put("run_id", "epoch-two")), "epoch-two", seen)
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireFreshV3EpochToken(listOf(first.deepCopy().put("token", "not-a-verified-token")), "epoch-one", seen)
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireFreshV3EpochToken(listOf(first, first.deepCopy().put("token", "c".repeat(64))), "epoch-one", seen)
        }
    }

    @Test fun unverifiedRestoreOrCleanupCannotAggregateReportedPass() {
        val fake = TrialReceipt("trial", "target", "origin", "epoch", "passed", listOf(valid), false, false)
        assertThrows(IllegalArgumentException::class.java) {
            require(oracle.aggregate(request.parameters, listOf(request), listOf(fake)).state == "passed")
        }
    }
}
