package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.fasterxml.jackson.databind.JsonNode

/** V3: each trial runs on a distinct restored epoch from the same sealed checkpoint. */
internal data class TrialRequest(val caseId: String, val trialId: String, val targetId: String,
    val inputCheckpointId: String, val parameters: JsonNode, val assertionIds: Set<String>,
    val provisionedTargets: Set<String>)
internal data class TrialReceipt(val trialId: String, val targetId: String, val inputCheckpointId: String,
    val epochId: String, val state: String, val observations: List<DriverObservation>,
    val inputCheckpointVerified: Boolean, val cleanupComplete: Boolean)
internal interface TrialCaseDriver : CaseDriver {
    fun expandTrials(parameters: JsonNode, inputCheckpointId: String): List<TrialRequest>
    /** Run just this target in this restored epoch; never a partial whole-roster assertion. */
    fun planTrial(request: TrialRequest): List<DriverStep>
    fun evaluateTrial(request: TrialRequest, observations: List<DriverObservation>): MeasurementVerdict
    /** Re-evaluate raw observations and roster/epoch identity, never trust supplied receipt.state. */
    fun aggregate(parameters: JsonNode, requests: List<TrialRequest>, receipts: List<TrialReceipt>): MeasurementVerdict
}
