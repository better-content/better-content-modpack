package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.CaseImplementation
import com.bettercontent.tests.suite.SuiteCase
import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.drivers.Stage1ExternalGetterNegativeControlDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Source-only diagnostic boundary regression, UNRUN without human authorization. */
class DiagnosticMutationNegativeTest {
    private val selector = ReviewedTargetSelector("bc.playtest_target_selector.v1", "one.stone",
        "a".repeat(64), "minecraft", "target.observe.loaded_v1", "minecraft:stone", "origin",
        TargetBounds("minecraft:overworld", 0, 0, 64, 64, 0, 0, 5),
        TargetSource.NATURAL_UNMODIFIED_BLOCK, "loaded_block_nearest_center_then_xyz_v1",
        TargetSelectionOrder.NEAREST_THEN_POSITION, 30, 1)
    private val spec = SuiteCase("control.mutation.external-getter-negative", emptyList(), "diagnostic",
        "campaign", "diagnostic", emptyList(), emptyList(), emptyList(), 1, 30,
        emptyList(), emptyList(), emptyList(), emptyList(), listOf("native-interaction"),
        emptyList(), CaseImplementation("source-only", "unregistered", "Stage1ExternalGetterNegativeControlDriver"),
        targetBindings = listOf(suiteMapper.valueToTree(selector)))
    private val params = suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(mapOf(
        "dimension" to "minecraft:overworld", "x" to 0, "y" to 64, "z" to 0,
        "source_checkpoint_sha256" to DIAGNOSTIC_INPUT_SHA_MARKER))

    @Test fun oneOriginGetterIsNeverAQualificationOrMultiActionPlan() {
        val plan = Stage1ExternalGetterNegativeControlDriver.plan(params)
        assertEquals(selector, diagnosticGetterSelector(spec, plan, ExecutionPurpose.DIAGNOSTIC, "origin"))
        assertThrows(Exception::class.java) {
            diagnosticGetterSelector(spec, plan, ExecutionPurpose.QUALIFICATION, "origin")
        }
        assertThrows(Exception::class.java) {
            diagnosticGetterSelector(spec, plan + plan, ExecutionPurpose.DIAGNOSTIC, "origin")
        }
        assertThrows(Exception::class.java) {
            diagnosticGetterSelector(spec, plan, ExecutionPurpose.DIAGNOSTIC, "different-origin")
        }
    }
}
