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
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
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

        @JvmStatic @BeforeAll fun start() {
            fixture = DedicatedServerFixture(
                evidence.run,
                seed = if (evidence.run.target == "cursed-pyramid") 5532927211958736304L else null,
            )
        }
        @JvmStatic @AfterAll fun stop() {
            cleanupFixtures(if (::fixture.isInitialized) listOf(fixture) else emptyList()) {
                evidence.run.event("process_cleanup", it)
            }
        }
    }

    @Test @Order(1)
    fun packagedServerReachesReadiness() = evidence.run.checkpoint("server readiness") {
        fixture.waitReady()
        val logOffset = Files.size(fixture.log).toInt()
        val report = fixture.commandResult(
            "font find",
            Regex("Found (\\d+)/4 Font types\\."),
            "full-pack natural Font finder command",
            Duration.ofSeconds(30),
        )
        val output = Files.readString(fixture.log).drop(logOffset)
        val fontTypes = listOf("aether", "bumblezone", "nether", "ratlantis")
        fontTypes.forEach { id ->
            assertTrue(output.contains("[$id]"), "font find omitted configured type $id")
        }
        val generated = Regex("\\[([a-z]+)] in ([a-z0-9_.-]+:[a-z0-9_./-]+) at (-?\\d+), (-?\\d+), (-?\\d+)")
            .findAll(output)
            .toList()
        generated.forEachIndexed { index, match ->
            val id = match.groupValues[1]
            val dimension = match.groupValues[2]
            val x = match.groupValues[3]
            val y = match.groupValues[4]
            val z = match.groupValues[5]
            // Keep console commands short; long source lines can be truncated by the server input reader.
            val marker = "BC_FONT_$index"
            fixture.commandResult(
                "execute in $dimension if block $x $y $z better_dimension_fonts:dimensional_font run say $marker",
                Regex(Regex.escape(marker)),
                "verify generated $id Font coordinate",
                Duration.ofSeconds(30),
            )
        }
        evidence.run.event("natural_font_finder_runtime", mapOf(
            "types_found" to report.groupValues[1].toInt(),
            "types_configured" to fontTypes,
            "generated_coordinates_verified" to generated.map { it.groupValues[1] },
            "output" to output.lines().filter { "[" in it && (" at " in it || "NOT FOUND" in it) },
        ))
        if (evidence.run.target != null) fixture.assertHashes()
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
        if (evidence.run.tier != "debug") {
            evidence.run.event("scenario_omitted", mapOf("name" to "lineage transition and archive", "tier" to evidence.run.tier))
            return
        }
        if (evidence.run.target == "lineage-transition") {
            // A focused target runs this method without the earlier ordered suite
            // tests, so establish the same readiness and runtime snapshot prerequisites.
            if (!ready) packagedServerReachesReadiness()
            if (!snapshot) runtimeSnapshotIsCompleteAndPromoted()
        }
        assumeTrue(snapshot, "runtime snapshot prerequisite failed")
        evidence.run.checkpoint("lineage transition and archive") {
            lifecycle(1)
            assertTrue("perks\t-" in fixture.server.resolve(".better_world_management/perks-v2.tsv").readLines())
            assertTrue("generation\t1" in fixture.server.resolve(".better_world_management/lineage-v5.tsv").readLines())
            val archives = Files.list(fixture.server.resolve(".better_world_management/archives")).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".zip") }.sorted().toList()
            }
            assertEquals(1, archives.size)
            val lineage = fixture.server.resolve(".better_world_management/lineage-v5.tsv").readLines()
                .first { it.startsWith("lineage\t") }.substringAfter('\t')
            archives.forEach { archive ->
                assertTrue(Files.isRegularFile(archive.resolveSibling("${archive.fileName}.sha256")))
                val transaction = Regex("(transaction-[a-z0-9_-]+)\\.zip$").find(archive.fileName.toString())?.groupValues?.get(1)
                    ?: error("archive name has no transaction ID: $archive")
                Commands.run(
                    listOf("./better-world-management-server.sh", "verify-archive", archive.toString(), lineage, transaction),
                    fixture.server,
                    evidence.run.directory.resolve("archive-$transaction.log"),
                    mapOf("BC_JAVA" to fixture.config.java.toString()),
                )
            }
            assertTrue(Files.size(fixture.server.resolve("logs/better-world-management-supervisor.log")) > 0)
            first = true
        }
    }

    @Test @Order(4)
    fun serverEvidenceIsCleanAndCandidatesAreUnchanged() {
        assumeTrue(if (evidence.run.tier == "debug") first else snapshot, "server prerequisite failed")
        evidence.run.checkpoint("server log and hash audit") {
            // The successor startup can leave asynchronous Lost Cities feature work
            // queued after its readiness marker. Let the new world tick before the
            // graceful stop so C2ME does not finish that work after Lost Cities clears
            // its static dimension profile cache during shutdown.
            if (evidence.run.tier == "debug") Thread.sleep(Duration.ofSeconds(30).toMillis())
            fixture.stopGracefully()
            fixture.auditLogs()
            fixture.assertHashes()
        }
    }

    @Test @Order(5)
    fun cursedPyramidSeededGenerationAndLogAudit() {
        if (evidence.run.target != "cursed-pyramid") return
        evidence.run.checkpoint("cursed pyramid seeded chunk generation") {
            fixture.waitReady()
            // Build 341 wrote outside its writable chunk while this structure's
            // neighborhood was generated. A single loaded chunk does not trigger
            // placement from the adjacent structure start.
            fixture.send("execute in minecraft:overworld run forceload add 144 -864 336 -672")
            fixture.commandResult(
                "execute in minecraft:overworld if loaded 144 95 -864 if loaded 236 95 -776 if loaded 336 95 -672 run say BC_CURSED_PYRAMID_AREA_LOADED",
                Regex("BC_CURSED_PYRAMID_AREA_LOADED"),
                "seeded cursed pyramid area generation",
                Duration.ofMinutes(5),
                Duration.ofSeconds(5),
            )
            Thread.sleep(Duration.ofSeconds(15).toMillis())
            fixture.stopGracefully()
            val region = fixture.server.resolve("world/region/r.0.-2.mca")
            assertTrue(regionContainsStructureId(region, "cataclysm:cursed_pyramid"),
                "seeded area did not generate the cursed pyramid; see $region")
            evidence.run.event("cursed_pyramid_structure_present", mapOf("region" to region.toString()))
            fixture.auditLogs()
            fixture.assertHashes()
            evidence.run.event("cursed_pyramid_area_passed", mapOf("seed" to 5532927211958736304L, "x" to 236, "z" to -776))
        }
    }

    private fun regionContainsStructureId(region: java.nio.file.Path, id: String): Boolean {
        if (!Files.isRegularFile(region)) return false
        val bytes = Files.readAllBytes(region)
        if (bytes.size < 8192) return false
        for (entry in 0 until 1024) {
            val header = entry * 4
            val sector = ((bytes[header].toInt() and 255) shl 16) or
                ((bytes[header + 1].toInt() and 255) shl 8) or (bytes[header + 2].toInt() and 255)
            val offset = sector * 4096
            if (sector == 0 || offset + 5 > bytes.size) continue
            val length = ((bytes[offset].toInt() and 255) shl 24) or
                ((bytes[offset + 1].toInt() and 255) shl 16) or
                ((bytes[offset + 2].toInt() and 255) shl 8) or (bytes[offset + 3].toInt() and 255)
            if (length < 2 || offset + 4L + length > bytes.size) continue
            val payload = ByteArrayInputStream(bytes, offset + 5, length - 1)
            val decoded = when (bytes[offset + 4].toInt()) {
                1 -> GZIPInputStream(payload)
                2 -> InflaterInputStream(payload)
                3 -> payload
                else -> continue
            }
            if (decoded.use { it.readBytes().toString(Charsets.ISO_8859_1).contains(id) }) return true
        }
        return false
    }

    private fun lifecycle(expected: Int) {
        fixture.send("better_world_management select minecraft:plains minecraft:forest minecraft:meadow")
        fixture.waitLogCount(Regex("Selected Prestige biomes minecraft:plains > minecraft:forest > minecraft:meadow"), expected, "biome selection")
        fixture.send("better_world_management stage")
        fixture.waitLogCount(Regex("Staged prestige reset"), expected, "prestige stage")
        fixture.send("better_world_management commit")
        fixture.waitLogCount(Regex("Prestige commit accepted: .* clean shutdown is scheduled"), expected, "prestige acceptance")
        fixture.waitLogCount(Regex("committed; successor world is active"), expected, "successor activation", Duration.ofMinutes(20))
        fixture.waitReady(expected + 1)
    }
}
