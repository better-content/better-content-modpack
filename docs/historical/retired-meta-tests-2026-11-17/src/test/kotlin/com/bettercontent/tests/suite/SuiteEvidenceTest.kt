package com.bettercontent.tests.suite

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class SuiteEvidenceTest {
    @TempDir lateinit var root: Path
    private val now = Instant.parse("2026-11-09T00:00:00Z")
    private fun result(m: SuiteManifest): CaseResult {
        val c = m.cases.single()
        val raw = root.resolve("raw.jsonl"); Files.writeString(raw, "unit fixture; not real gameplay evidence")
        return CaseResult("bc.playtest_case_result.v1", c.id, caseFingerprint(c), "b".repeat(64), profileFingerprint(m.profile), candidateHashes(m),
            "passed", false, "run-1", "fixture-world", "2026-11-08T00:00:00Z", "2026-11-08T00:00:01Z",
            listOf(AssertionResult(c.assertions.single(), "passed", listOf(AssertionEvidence("raw", BoundFile(raw.toString(), manifestFingerprint(raw)))))), true, emptyList())
    }
    @Test fun `only passed nondiagnostic fully bound results satisfy a case`() {
        val m = fixtureManifest(root); val c = m.cases.single(); val r = result(m)
        validateCaseResult(m, c, r, "b".repeat(64), root, now)
        for (state in listOf("failed", "blocked", "inconclusive", "not_run", "skipped"))
            assertThrows(IllegalArgumentException::class.java) { validateCaseResult(m, c, r.copy(state = state), "b".repeat(64), root, now) }
        assertThrows(IllegalArgumentException::class.java) { validateCaseResult(m, c, r.copy(diagnostic = true), "b".repeat(64), root, now) }
        assertThrows(IllegalArgumentException::class.java) { validateCaseResult(m, c, r.copy(cleanupComplete = false), "b".repeat(64), root, now) }
    }
    @Test fun `missing diagnostic flag never becomes an implicit qualifying false`() {
        val m = fixtureManifest(root)
        val node = suiteMapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(result(m))
        node.remove("diagnostic")
        assertThrows(Exception::class.java) { suiteMapper.treeToValue(node, CaseResult::class.java) }
    }
    @Test fun `candidate catalogue profile and case revisions cannot be mixed`() {
        val m = fixtureManifest(root); val c = m.cases.single(); val r = result(m)
        listOf(r.copy(candidate = mapOf("client_sha256" to "c".repeat(64))), r.copy(catalogueSha256 = "c".repeat(64)),
            r.copy(profileSha256 = "c".repeat(64)), r.copy(caseSha256 = "c".repeat(64))).forEach { stale ->
            assertThrows(IllegalArgumentException::class.java) { validateCaseResult(m, c, stale, "b".repeat(64), root, now) }
        }
    }
    @Test fun `missing duplicate failed assertions and mutated raw evidence are rejected`() {
        val m = fixtureManifest(root); val c = m.cases.single(); val r = result(m)
        for (assertions in listOf(emptyList(), r.assertions + r.assertions, listOf(r.assertions.single().copy(state = "failed"))))
            assertThrows(IllegalArgumentException::class.java) { validateCaseResult(m, c, r.copy(assertions = assertions), "b".repeat(64), root, now) }
        Files.writeString(root.resolve("raw.jsonl"), "changed")
        assertThrows(IllegalArgumentException::class.java) { validateCaseResult(m, c, r, "b".repeat(64), root, now) }
    }
    @Test fun `provisioned targets never prove survival acquisition`() {
        val base = fixtureManifest(root); val c = base.cases.single().copy(targets = listOf("test:ore"))
        val m = base.copy(cases = listOf(c), acquisitions = listOf(AcquisitionSpec("test:ore", "worldgen", 0, listOf(c.id), emptyList(), emptyList())))
        val r = result(m).copy(provisionedTargets = listOf("test:ore"))
        assertThrows(IllegalArgumentException::class.java) { validateCaseResult(m, c, r, "b".repeat(64), root, now) }
    }
    @Test fun `future timing and timeouts cannot pass`() {
        val m = fixtureManifest(root); val c = m.cases.single(); val r = result(m)
        for (bad in listOf(r.copy(completedAt = "2027-01-01T00:00:00Z"), r.copy(completedAt = "2026-11-08T00:02:00Z"), r.copy(completedAt = "2026-11-07T00:00:00Z")))
            assertThrows(IllegalArgumentException::class.java) { validateCaseResult(m, c, bad, "b".repeat(64), root, now) }
    }
    @Test fun `qualify cannot bless a draft or create a marker`() {
        val m = fixtureManifest(root).copy(authoring = AuthoringReview("draft", "", "", listOf("native adapter missing")))
        val file = root.resolve("manifest.json"); suiteMapper.writeValue(file.toFile(), m)
        assertThrows(IllegalArgumentException::class.java) { qualifySuite(root, file, root.resolve("missing-index.json")) }
        assertFalse(Files.exists(root.resolve("playtest-qualified.json")))
    }
    @Test fun `campaign blocks a draft before attempting candidate paths`() {
        val m = fixtureManifest(root).copy(authoring = AuthoringReview("draft", "", "", listOf("missing authored routes")))
        val file = root.resolve("manifest.json"); suiteMapper.writeValue(file.toFile(), m)
        assertThrows(IllegalArgumentException::class.java) { campaignRequest(root, file, root.resolve("no-client.zip"), root.resolve("no-server.zip"), emptyList()) }
        assertFalse(Files.exists(root.resolve("world")))
    }
}
