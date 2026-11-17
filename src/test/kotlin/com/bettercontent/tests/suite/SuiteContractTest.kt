package com.bettercontent.tests.suite

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal fun fixtureProfile() = SuiteProfile(424242, 10, 10, listOf(8,16,32), 600, 120, 30, 30, 19.0,
    100, 5, 20, 0.95, 10, 3, listOf("1280x720@2", "1920x1080@3"), "native-windows-and-boundaries", "server-fake-player", 10240, "unit-fixture-not-runtime")
internal fun fixtureManifest(root: Path): SuiteManifest {
    val file = root.resolve("fixture.txt"); Files.writeString(file, "contract unit fixture, not native evidence")
    val ref = BoundFile(file.toString(), manifestFingerprint(file))
    val spec = SuiteCase("fixture", requiredNotes.sorted(), "better-content-modpack", "campaign", "fixture",
        listOf("fixture.claim"), listOf("test:root"), emptyList(), 0, 60, listOf("fixture setup"), listOf("fixture action"),
        listOf("fixture expected"), listOf("fixture cleanup"), listOf("raw"), emptyList(), CaseImplementation("authored", file.toString(), "fixture"))
    val sha = "a".repeat(64)
    return SuiteManifest("bc.playtest_manifest.v1", "unit-fixture", AuthoringReview("reviewed", "fixture-reviewer", "2026-01-01T00:00:00Z", emptyList()),
        CandidateBinding(ref, ref, ref, ref), fixtureProfile(), ref,
        listOf(RepositorySpec("better-content-modpack", "", "", sha, sha, sha, sha, sha, file.toString(), "authored", listOf("fixture"))),
        requiredRosters.sorted().map { RosterSpec(it, true, mapOf((if (it == "repositories") "better-content-modpack" else "test:root") to listOf("fixture")), emptyList()) },
        listOf(AcquisitionSpec("test:root", "worldgen", 0, listOf("fixture"), emptyList(), emptyList())), listOf(spec))
}

class SuiteContractTest {
    @TempDir lateinit var root: Path
    @Test fun `station and input cycles are detected without recursion overflow`() {
        assertEquals(listOf("station -> tool -> station"), dependencyCycles(mapOf("station" to listOf("tool"), "tool" to listOf("station"))))
        assertTrue(dependencyCycles(mapOf("ore" to emptyList(), "tool" to listOf("ore"))).isEmpty())
    }
    @Test fun `named resources cannot evade acquisition mapping`() {
        val m = fixtureManifest(root); val c = m.cases.single().copy(actions = listOf("craft minecraft:stick"))
        assertTrue(manifestProblems(m.copy(cases = listOf(c)), root).any { "undeclared resource" in it })
    }
    @Test fun `route presence is not actual execution coverage`() {
        val m = fixtureManifest(root); val c = m.cases.single().copy(targets = listOf("test:tool"))
        val target = AcquisitionSpec("test:tool", "crafted", 0, listOf(c.id), emptyList(), listOf(CraftRoute("grid", "crafting-grid", "missing", emptyList(), emptyList(), "", 0)))
        assertTrue(manifestProblems(m.copy(cases = listOf(c), acquisitions = listOf(target)), root).any { "actual route execution" in it })
    }
    @Test fun `a later dimension root cannot supply an earlier crafting stage`() {
        val m = fixtureManifest(root); val c = m.cases.single().copy(targets = listOf("test:early", "test:late"))
        val early = AcquisitionSpec("test:early", "crafted", 0, listOf(c.id), listOf("test:late"), listOf(CraftRoute("route", "grid", c.id, emptyList(), emptyList(), "", 0)))
        val late = AcquisitionSpec("test:late", "worldgen", 2, listOf(c.id), emptyList(), emptyList())
        assertTrue(manifestProblems(m.copy(cases = listOf(c), acquisitions = listOf(early,late)), root).any { "premature" in it })
    }
    @Test fun `alternative later routes and bootstrapped recycling are not false dependency cycles`() {
        val wood = AcquisitionSpec("test:wood", "worldgen", 0, listOf("root"), emptyList(), emptyList())
        val press = AcquisitionSpec("test:press", "worldgen", 2, listOf("later"), emptyList(), emptyList())
        val planks = AcquisitionSpec("test:planks", "crafted", 0, listOf("manual", "machine"), emptyList(), listOf(
            CraftRoute("manual", "grid", "manual", listOf("test:wood"), emptyList(), "", 0),
            CraftRoute("machine", "saw", "machine", listOf("test:press", "test:planks"), emptyList(), "", 2)))
        assertEquals(setOf("test:wood", "test:planks"), reachableAcquisitions(listOf(planks,press,wood), 0))
        assertEquals(setOf("test:wood", "test:press", "test:planks"), reachableAcquisitions(listOf(planks,press,wood), 2))
        assertTrue(reachableAcquisitions(listOf(planks.copy(routes = planks.routes.drop(1)),press), 2).contains("test:planks").not())
    }
}
