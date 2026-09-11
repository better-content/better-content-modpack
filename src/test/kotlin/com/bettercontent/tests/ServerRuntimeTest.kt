package com.bettercontent.tests

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Files
import java.time.Duration
import kotlin.io.path.readLines
import kotlin.io.path.readText

@Tag("server")
@TestMethodOrder(OrderAnnotation::class)
class ServerRuntimeTest {
    companion object {
        @JvmField @RegisterExtension val evidence = EvidenceExtension("server")
        lateinit var fixture: DedicatedServerFixture
        var ready = false
        var snapshot = false
        var first = false

        @JvmStatic @BeforeAll fun start() { fixture = DedicatedServerFixture(evidence.run) }
        @JvmStatic @AfterAll fun stop() {
            cleanupFixtures(if (::fixture.isInitialized) listOf(fixture) else emptyList()) {
                evidence.run.event("process_cleanup", it)
            }
        }
    }

    @Test @Order(1)
    fun packagedServerReachesReadiness() = evidence.run.checkpoint("server readiness") {
        fixture.waitReady()
        ready = true
    }

    @Test @Order(2)
    fun runtimeSnapshotIsCompleteAndPromoted() {
        assumeTrue(ready, "server readiness failed")
        evidence.run.checkpoint("runtime snapshot") {
            val dump = fixture.runtimeDump()
            val id = promoteSnapshot(dump, fixture.config.root.resolve("generated/runtime-dumps"), fixture.config.runId)
            evidence.run.event("runtime_snapshot", mapOf("snapshot_id" to id))
            snapshot = true
        }
    }

    @Test @Order(3)
    fun oneLineageTransitionCommitsAndArchivesCleanly() {
        assumeTrue(snapshot, "runtime snapshot prerequisite failed")
        evidence.run.checkpoint("lineage transition and archive") {
            lifecycle(1)
            assertTrue("perks\t-" in fixture.server.resolve(".world_lifecycle_manager/perks-v2.tsv").readLines())
            assertTrue("generation\t1" in fixture.server.resolve(".world_lifecycle_manager/lineage-v5.tsv").readLines())
            val archives = Files.list(fixture.server.resolve(".world_lifecycle_manager/archives")).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".zip") }.sorted().toList()
            }
            assertEquals(1, archives.size)
            val lineage = fixture.server.resolve(".world_lifecycle_manager/lineage-v5.tsv").readLines()
                .first { it.startsWith("lineage\t") }.substringAfter('\t')
            archives.forEach { archive ->
                assertTrue(Files.isRegularFile(archive.resolveSibling("${archive.fileName}.sha256")))
                val transaction = Regex("(transaction-[a-z0-9_-]+)\\.zip$").find(archive.fileName.toString())?.groupValues?.get(1)
                    ?: error("archive name has no transaction ID: $archive")
                Commands.run(
                    listOf("./world-lifecycle-manager-server.sh", "verify-archive", archive.toString(), lineage, transaction),
                    fixture.server,
                    evidence.run.directory.resolve("archive-$transaction.log"),
                    mapOf("BC_JAVA" to fixture.config.java.toString()),
                )
            }
            assertTrue(Files.size(fixture.server.resolve("logs/world-lifecycle-manager-supervisor.log")) > 0)
            first = true
        }
    }

    @Test @Order(4)
    fun serverEvidenceIsCleanAndCandidatesAreUnchanged() {
        assumeTrue(first, "lineage transition prerequisite failed")
        evidence.run.checkpoint("server log and hash audit") {
            fixture.stopGracefully()
            fixture.auditLogs()
            fixture.assertHashes()
        }
    }

    private fun lifecycle(expected: Int) {
        fixture.send("world_lifecycle_manager select minecraft:plains minecraft:forest minecraft:meadow")
        fixture.waitLogCount(Regex("Selected Prestige biomes minecraft:plains > minecraft:forest > minecraft:meadow"), expected, "biome selection")
        fixture.send("world_lifecycle_manager stage")
        fixture.waitLogCount(Regex("Staged prestige reset"), expected, "prestige stage")
        fixture.send("world_lifecycle_manager commit")
        fixture.waitLogCount(Regex("Prestige commit accepted: .* clean shutdown is scheduled"), expected, "prestige acceptance")
        fixture.waitLogCount(Regex("committed; successor world is active"), expected, "successor activation", Duration.ofMinutes(20))
        fixture.waitReady(expected + 1)
    }
}
