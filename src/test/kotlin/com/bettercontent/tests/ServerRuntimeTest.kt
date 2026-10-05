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
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.io.ByteArrayInputStream
import java.io.DataInputStream
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
        if (evidence.run.target != null) fixture.assertHashes()
        ready = true
    }

    @Test @Order(2)
    fun runtimeSnapshotIsCompleteAndPromoted() {
        assumeTrue(ready, "server readiness failed")
        evidence.run.checkpoint("runtime snapshot") {
            val dump = fixture.runtimeDump()
            val id = promoteSnapshot(dump, fixture.config.root.resolve("generated/runtime-dumps"), fixture.config.runId)
            Files.copy(dump.resolve("dimensions.json"), evidence.run.directory.resolve("dimensions.json"),
                StandardCopyOption.REPLACE_EXISTING)
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
            fixture.commandResult(
                "gamerule doMobSpawning false",
                Regex("Gamerule doMobSpawning is now set to: false"),
                "disable ambient mob spawning in the structure-generation fixture",
                Duration.ofSeconds(30),
            )
            evidence.run.event("cursed_pyramid_fixture_gamerule", mapOf("doMobSpawning" to false))
            // Build 341 wrote outside its writable chunk while this structure's
            // neighborhood was generated. Cover the pinned pyramid's recorded
            // piece bounds plus one chunk of margin so its adjacent start is
            // generated without ticking a much larger, unrelated area.
            fixture.send("execute in minecraft:overworld run forceload add 208 -832 335 -687")
            evidence.run.event("cursed_pyramid_generation_area", mapOf(
                "min_x" to 208, "min_z" to -832, "max_x" to 335, "max_z" to -687,
                "reason" to "pinned structure bounding box plus one chunk margin",
            ))
            fixture.commandResult(
                "execute in minecraft:overworld if loaded 208 95 -832 if loaded 271 95 -759 if loaded 335 95 -687 run say BC_CURSED_PYRAMID_AREA_LOADED",
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
            if (runCatching { decoded.use { nbt ->
                    DataInputStream(nbt).use { input -> structureStartInChunk(input, id) }
                } }.getOrDefault(false)) return true
        }
        return false
    }

    private fun structureStartInChunk(input: DataInputStream, id: String): Boolean {
        if (input.readUnsignedByte() != 10) return false
        input.readUTF()
        return findStructures(input, id, allowLevel = true)
    }

    private fun findStructures(input: DataInputStream, id: String, allowLevel: Boolean): Boolean {
        while (true) {
            val type = input.readUnsignedByte()
            if (type == 0) return false
            val name = input.readUTF()
            when {
                type == 10 && name == "structures" -> if (findStarts(input, id)) return true
                type == 10 && allowLevel && name == "Level" -> if (findStructures(input, id, false)) return true
                else -> skipNbtPayload(input, type, 0)
            }
        }
    }

    private fun findStarts(input: DataInputStream, id: String): Boolean {
        while (true) {
            val type = input.readUnsignedByte()
            if (type == 0) return false
            val name = input.readUTF()
            if (type == 10 && name == "starts") {
                if (findNamedStart(input, id)) return true
            } else skipNbtPayload(input, type, 0)
        }
    }

    private fun findNamedStart(input: DataInputStream, id: String): Boolean {
        while (true) {
            val type = input.readUnsignedByte()
            if (type == 0) return false
            val name = input.readUTF()
            if (type == 10 && name == id) {
                if (startDeclaresId(input, id)) return true
            } else skipNbtPayload(input, type, 0)
        }
    }

    private fun startDeclaresId(input: DataInputStream, id: String): Boolean {
        while (true) {
            val type = input.readUnsignedByte()
            if (type == 0) return false
            val name = input.readUTF()
            if (type == 8 && name == "id") {
                if (input.readUTF() == id) return true
            } else skipNbtPayload(input, type, 0)
        }
    }

    private fun skipNbtPayload(input: DataInputStream, type: Int, depth: Int) {
        require(depth < 64) { "NBT nesting exceeds 64" }
        fun count() = input.readInt().also { require(it in 0..1_000_000) { "invalid NBT length: $it" } }
        when (type) {
            1 -> input.readByte()
            2 -> input.readShort()
            3 -> input.readInt()
            4 -> input.readLong()
            5 -> input.readFloat()
            6 -> input.readDouble()
            7 -> input.skipNBytes(count().toLong())
            8 -> input.readUTF()
            9 -> {
                val elementType = input.readUnsignedByte()
                repeat(count()) { skipNbtPayload(input, elementType, depth + 1) }
            }
            10 -> while (true) {
                val childType = input.readUnsignedByte()
                if (childType == 0) break
                input.readUTF()
                skipNbtPayload(input, childType, depth + 1)
            }
            11 -> input.skipNBytes(count().toLong() * 4)
            12 -> input.skipNBytes(count().toLong() * 8)
            else -> error("invalid NBT tag type $type")
        }
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
