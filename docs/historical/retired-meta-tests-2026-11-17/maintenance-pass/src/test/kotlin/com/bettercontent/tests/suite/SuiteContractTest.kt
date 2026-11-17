package com.bettercontent.tests.suite

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

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
    @Test fun `optional v3 case domain fields do not rewrite historical v1 v2 case fingerprints`() {
        val old = fixtureManifest(root).cases.single()
        val prior = suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(old)
        for (field in listOf("domain", "required_capabilities", "required_inventory_domains", "target_bindings"))
            assertFalse(prior.has(field), "historical case unexpectedly gained $field")
        val enriched = old.copy(domain = "survival.food", requiredCapabilities = listOf("client.snapshot"),
            requiredInventoryDomains = listOf("items"))
        val serialized = suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(enriched)
        assertEquals(enriched, suiteMapper.treeToValue(serialized, SuiteCase::class.java))
        assertEquals("items", serialized.path("required_inventory_domains")[0].asText())
    }
    @Test fun `complete unit fixture is structurally reviewed but not a runtime qualification`() {
        assertEquals(emptyList<String>(), manifestProblems(fixtureManifest(root), root))
    }
    @Test fun `draft blocks even when declarations otherwise complete`() {
        val m = fixtureManifest(root)
        assertTrue(manifestProblems(m.copy(authoring = m.authoring.copy(state = "draft")), root).any { "not reviewed" in it })
    }
    @Test fun `a note cannot disappear from the qualification gate`() {
        val m = fixtureManifest(root); val c = m.cases.single()
        assertTrue(manifestProblems(m.copy(cases = listOf(c.copy(noteIds = c.noteIds - "tacz"))), root).any { "uncovered notes" in it })
    }
    @Test fun `same assertion different layers is intentional but same layer context is duplicate`() {
        val m = fixtureManifest(root); val first = m.cases.single(); val second = first.copy(id = "second")
        assertTrue(manifestProblems(m.copy(cases = listOf(first, second)), root).any { "duplicate assertion ownership" in it })
        assertFalse(manifestProblems(m.copy(cases = listOf(first, second.copy(layer = "unit"))), root).any { "duplicate assertion ownership" in it })
    }
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
    @Test fun `unknown JSON fields and duplicate keys fail closed`() {
        val path = root.resolve("manifest.json"); val m = fixtureManifest(root)
        val node = suiteMapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(m)
        node.put("pretend_passed", true); Files.writeString(path, suiteMapper.writeValueAsString(node))
        assertThrows(Exception::class.java) { loadSuiteManifest(path) }
        assertThrows(Exception::class.java) { suiteMapper.readTree("{\"state\":\"failed\",\"state\":\"passed\"}") }
    }
    @Test fun `future review and weakened scale profile cannot pass`() {
        val m = fixtureManifest(root)
        val errors = manifestProblems(m.copy(authoring = m.authoring.copy(reviewedAt = "2099-01-01T00:00:00Z"), profile = m.profile.copy(viewDistance = 4)), root,
            Instant.parse("2026-11-09T00:00:00Z"))
        assertTrue(errors.any { "review time" in it }); assertTrue(errors.any { "distances" in it })
    }
}
