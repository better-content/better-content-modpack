package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.checkpoints.SamplingIntent
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScaleConformanceControlDriverTest {
    private fun target() = suiteMapper.readTree("""{
       "cohort":8,"dimension":"minecraft:overworld","view_distance":10,"simulation_distance":10,
       "capture_horizon_millis":810000,"ledger_sha256":"${"a".repeat(64)}","sites":[
       {"x":0,"y":70,"z":0},{"x":512,"y":70,"z":0},{"x":1024,"y":70,"z":0},{"x":1536,"y":70,"z":0},
       {"x":2048,"y":70,"z":0},{"x":2560,"y":70,"z":0},{"x":3072,"y":70,"z":0},{"x":3584,"y":70,"z":0}]}
    """)
    @Test fun `typed external site claims enumerate each disjoint simulated footprint`() {
        val request = ScaleConformanceControlDriver.expandTrials(target(), "sealed_scale_input").single()
        assertEquals(8, ScaleConformanceControlDriver.samplingIntents(request).size)
        assertEquals(SamplingIntent.Site("minecraft:overworld", 0, 0, 11),
            ScaleConformanceControlDriver.samplingIntents(request).first())
        assertEquals(listOf("scale.begin", "scale.cleanup"), ScaleConformanceControlDriver.planTrial(request).map { it.action.id })
        val crowded = target().deepCopy<ObjectNode>()
        ((crowded.path("sites")[1]) as ObjectNode).put("x", 16)
        assertThrows(IllegalArgumentException::class.java) { ScaleConformanceControlDriver.plan(crowded) }
        val twoMinutes = target().deepCopy<ObjectNode>().put("capture_horizon_millis", 120_000)
        assertThrows(IllegalArgumentException::class.java) { ScaleConformanceControlDriver.plan(twoMinutes) }
        assertTrue(DriverRegistry.supportedCases.contains("scale.8")) // full raw driver registered, not this control
        assertFalse(DriverRegistry.supportedControls.contains("control.scale.native-conformance")) // uncompiled latest support, not registered
    }
    @Test fun `scale never infers capacity from native helper conformance without typed actors and raw trace`() {
        assertTrue(ScaleConformanceControlDriver.assertionIds.isEmpty())
        assertTrue(ScaleConformanceControlDriver.plan(target()).first().action.parameters.path("ledger_sha256").isTextual)
        val actions = listOf(DriverObservation("observe", "scale.begin", "server", "completed",
            suiteMapper.readTree("""{"conformant":true,"site_ledger_sha256":"${"a".repeat(64)}","actors":[]}""")),
            DriverObservation("cleanup", "scale.cleanup", "server", "completed",
                suiteMapper.readTree("""{"cleanup_complete":true,"owned_residuals":0}""")))
        assertTrue(evaluateCase(ScaleConformanceControlDriver, target(), actions) is DriverOutcome.Fatal)
    }
}
