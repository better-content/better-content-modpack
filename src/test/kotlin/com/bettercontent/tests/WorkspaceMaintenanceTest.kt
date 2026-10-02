package com.bettercontent.tests

import com.bettercontent.tests.maintenance.CandidateHashes
import com.bettercontent.tests.maintenance.EvidencePlanner
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.writeText

@Tag("fast")
class WorkspaceMaintenanceTest {
    private val mapper = jacksonObjectMapper()
    private val current = CandidateHashes("client-current", "server-current")

    @Test
    fun protectsCurrentCandidateLatestPassesAndUnresolvedFailures(@TempDir root: Path) {
        report(root, "20260901T010000Z-1", "server", "failed", "2026-09-01T01:05:00Z", CandidateHashes("old", "old"))
        report(root, "20260901T020000Z-2", "server", "passed", "2026-09-01T02:05:00Z", current)
        report(root, "20260901T015000Z-3", "singleplayer", "passed", "2026-09-01T01:55:00Z", CandidateHashes("older", "older"))
        report(root, "20260901T030000Z-4", "multiplayer", "failed", "2026-09-01T03:05:00Z", current)

        val plan = EvidencePlanner.plan(root, current)

        assertEquals("prune", plan.decisions.single { it.runId.endsWith("-1") }.action)
        assertEquals("retain", plan.decisions.single { it.runId.endsWith("-2") }.action)
        assertEquals("retain", plan.decisions.single { it.runId.endsWith("-3") }.action)
        assertTrue(plan.decisions.single { it.runId.endsWith("-4") }.reasons.any { "unresolved" in it })
    }

    @Test
    fun prunesStaleRunningRunOnlyAfterLaterPass(@TempDir root: Path) {
        report(root, "20260901T010000Z-1", "multiplayer", "running", "2026-09-01T01:05:00Z", current)
        report(root, "20260901T020000Z-2", "multiplayer", "passed", "2026-09-01T02:05:00Z", current)

        val plan = EvidencePlanner.plan(root, current)

        assertEquals("prune", plan.decisions.single { it.runId.endsWith("-1") }.action)
        assertEquals("retain", plan.decisions.single { it.runId.endsWith("-2") }.action)
    }

    @Test
    fun targetedPassDoesNotSupersedeFullFailure(@TempDir root: Path) {
        report(root, "20260901T010000Z-1", "multiplayer", "failed", "2026-09-01T01:05:00Z", current)
        report(root, "20260901T020000Z-2", "target-font-aether", "failed", "2026-09-01T02:05:00Z", current)
        report(root, "20260901T030000Z-3", "target-font-aether", "passed", "2026-09-01T03:05:00Z", current)

        val plan = EvidencePlanner.plan(root, null)

        assertEquals("retain", plan.decisions.single { it.runId.endsWith("-1") }.action)
        assertEquals("prune", plan.decisions.single { it.runId.endsWith("-2") }.action)
        assertEquals("retain", plan.decisions.single { it.runId.endsWith("-3") }.action)
    }

    @Test
    fun retainsMalformedAndSymbolicLinkEntries(@TempDir root: Path) {
        root.resolve("20260901T010000Z-1/server").createDirectories()
        root.resolve("20260901T010000Z-1/server/run.json").writeText("not-json")
        val target = root.resolve("elsewhere").also { it.createDirectories() }
        Files.createSymbolicLink(root.resolve("20260901T020000Z-2"), target)

        val plan = EvidencePlanner.plan(root, null)

        assertTrue(plan.decisions.all { it.action == "retain" })
    }

    @Test
    fun prunesEmptyAndReleaseOnlyRootsAfterPackagedRelease(@TempDir root: Path) {
        root.resolve("20260901T010000Z-1").createDirectories()
        val partial = root.resolve("20260901T020000Z-2").also { it.createDirectories() }
        partial.resolve("release-request.txt").writeText("run_id=20260901T020000Z-2\n")
        val latest = root.resolve("20260901T030000Z-3/release").also { it.createDirectories() }
        latest.parent.resolve("release-request.txt").writeText("run_id=20260901T030000Z-3\n")
        mapper.writeValue(latest.resolve("provenance.json").toFile(), mapOf(
            "candidates" to mapOf("client_sha256" to current.clientSha256, "server_sha256" to current.serverSha256),
        ))

        val plan = EvidencePlanner.plan(root, current)

        assertEquals("prune", plan.decisions.single { it.runId.endsWith("-1") }.action)
        assertEquals("prune", plan.decisions.single { it.runId.endsWith("-2") }.action)
        assertEquals("retain", plan.decisions.single { it.runId.endsWith("-3") }.action)
    }

    @Test
    fun successfulArchiveDeletesStagingAndFailedArchiveRetainsIt(@TempDir root: Path) {
        val packageScript = Path.of(System.getProperty("bc.repo.root")).resolve("package.sh")
        val success = root.resolve("success/server-tree/better-content-server").also { it.createDirectories() }
        success.resolve("file.txt").writeText("payload")
        assertEquals(0, archive(packageScript, root.resolve("success"), null))
        assertTrue(Files.isRegularFile(root.resolve("success/better-content.zip")))
        assertTrue(!root.resolve("success/server-tree").exists())

        val failure = root.resolve("failure/server-tree/better-content-server").also { it.createDirectories() }
        failure.resolve("file.txt").writeText("payload")
        val fakeBin = root.resolve("fake-bin").also { it.createDirectories() }
        val fakeZip = fakeBin.resolve("zip").also { it.writeText("#!/bin/sh\nexit 19\n") }
        fakeZip.toFile().setExecutable(true)
        assertEquals(19, archive(packageScript, root.resolve("failure"), fakeBin))
        assertTrue(root.resolve("failure/server-tree").isDirectory())
    }

    private fun report(root: Path, runId: String, suite: String, status: String, updated: String, candidate: CandidateHashes) {
        val directory = root.resolve("$runId/$suite").also { it.createDirectories() }
        mapper.writeValue(directory.resolve("run.json").toFile(), mapOf(
            "schema" to "bc.modpack_test_run.v1",
            "run_id" to runId,
            "suite" to suite,
            "status" to status,
            "started_at" to Instant.parse(updated).minusSeconds(60).toString(),
            "updated_at" to updated,
            "failure" to if (status == "failed") "fixture failure" else null,
        ))
        directory.resolve("events.jsonl").writeText(mapper.writeValueAsString(mapOf(
            "time" to updated,
            "type" to "candidate_selected",
            "client_sha256" to candidate.clientSha256,
            "server_sha256" to candidate.serverSha256,
        )) + "\n")
    }

    private fun archive(script: Path, server: Path, fakeBin: Path?): Int {
        val process = ProcessBuilder("bash", "-c", "source \"\$PACKAGE_SCRIPT\"; archive_server_tree \"\$SERVER_DIR\"")
        process.environment()["PACKAGE_SCRIPT"] = script.toString()
        process.environment()["SERVER_DIR"] = server.toString()
        if (fakeBin != null) process.environment()["PATH"] = "$fakeBin:${System.getenv("PATH")}"
        return process.inheritIO().start().waitFor()
    }
}
