package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.CaseImplementation
import com.bettercontent.tests.suite.SuiteCase
import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BranchContractsTest {
    private fun spec(id: String, dependencies: List<String> = emptyList()): SuiteCase = SuiteCase(id,
        listOf("tools"), "better-content-modpack", "campaign", "server", listOf("$id-assertion"),
        listOf("test:$id"), dependencies, 0, 60, listOf("pre"), listOf("action"), listOf("expect"),
        listOf("cleanup"), listOf("native-interaction"), emptyList(), CaseImplementation("authored", "driver", id))
    private fun manifest(cases: List<SuiteCase>, branches: List<BranchSpec>) = suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(
        mapOf("schema" to "bc.playtest_manifest.v3", "cases" to cases, "branches" to branches))

    @Test fun producerOrderingIsTopologicalButNeverInfersSiblingAncestry() {
        val a = BranchSpec("a", "origin", listOf("gather"), "cp-a", emptyList(), "isolated", null)
        val b = BranchSpec("b", "cp-a", listOf("use"), "cp-b", listOf("a"), "isolated", null)
        val c = BranchSpec("c", "origin", listOf("side"), null, emptyList(), "isolated", null)
        val specs = listOf(spec("gather"), spec("use", listOf("gather")), spec("side"))
        assertEquals(listOf("a", "b", "c"), orderedBranches(manifest(specs, listOf(b, a, c))).map { it.id })
        val falseProof = c.copy(dependsOn = listOf("a"))
        assertThrows(IllegalArgumentException::class.java) {
            orderedBranches(manifest(listOf(spec("gather"), spec("use", listOf("gather")),
                spec("side", listOf("gather"))), listOf(a, falseProof, b)))
        }
    }

    @Test fun siblingOrderingDoesNotConferCheckpointAncestryOrAcquisition() {
        val first = BranchSpec("first", "origin", listOf("gather"), "cp-first", emptyList(), "isolated", null)
        val later = BranchSpec("later", "origin", listOf("observe"), null, emptyList(), "isolated", null,
            orderingDependencies = listOf("first"))
        assertEquals(listOf("first", "later"), orderedBranches(manifest(listOf(spec("gather"), spec("observe")),
            listOf(later, first))).map { it.id })
        assertThrows(IllegalArgumentException::class.java) {
            orderedBranches(manifest(listOf(spec("gather"), spec("observe", listOf("gather"))), listOf(later, first)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            orderedBranches(manifest(listOf(spec("gather"), spec("observe")),
                listOf(first.copy(orderingDependencies = listOf("later")), later)))
        }
    }

    @Test fun firstRealOnboardingSealsOriginAndFoodRestoresItsSurvivalCheckpoint() {
        val onboarding = BranchSpec("onboard", "origin", listOf("world.onboarding"), "after-onboarding",
            emptyList(), "carry-forward", null)
        val food = BranchSpec("food", "after-onboarding", listOf("survival.food"), null,
            listOf("onboard"), "isolated", null)
        val cases = listOf(spec("world.onboarding"), spec("survival.food", listOf("world.onboarding")))
        assertEquals(listOf("onboard", "food"), orderedBranches(manifest(cases, listOf(food, onboarding))).map { it.id })
        assertThrows(IllegalArgumentException::class.java) {
            orderedBranches(manifest(cases, listOf(onboarding.copy(inputCheckpoint = "other"), food)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            orderedBranches(manifest(cases, listOf(onboarding.copy(outputCheckpoint = null), food.copy(inputCheckpoint = "origin"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            orderedBranches(manifest(cases, listOf(onboarding, food.copy(inputCheckpoint = "origin",
                dependsOn = emptyList(), orderingDependencies = listOf("onboard")))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            orderedBranches(manifest(listOf(spec("world.onboarding"), spec("survival.food")), listOf(onboarding, food)))
        }
    }

    @Test fun cyclesDuplicateExecutionAndUnreviewedMultiCaseCarryForwardAreBlocked() {
        val cases = listOf(spec("gather"), spec("use", listOf("gather")))
        val cycle = listOf(BranchSpec("a", "cp-b", listOf("gather"), "cp-a", listOf("b"), "isolated", null),
            BranchSpec("b", "cp-a", listOf("use"), "cp-b", listOf("a"), "isolated", null))
        assertThrows(IllegalArgumentException::class.java) { orderedBranches(manifest(cases, cycle)) }
        val repeated = listOf(BranchSpec("a", "origin", listOf("gather"), "cp-a", emptyList(), "isolated", null),
            BranchSpec("b", "cp-a", listOf("gather", "use"), null, listOf("a"), "carry-forward", null))
        assertThrows(IllegalArgumentException::class.java) { orderedBranches(manifest(cases, repeated)) }
        assertThrows(IllegalArgumentException::class.java) {
            orderedBranches(manifest(cases, listOf(BranchSpec("a", "origin", listOf("gather", "use"), null,
                emptyList(), "carry-forward", null))))
        }
    }
}
