package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** MCA decoder fixtures only; a real caller MUST first use CheckpointStore.verify(ref). Unrun. */
class CheckpointTerrainTest {
    @TempDir lateinit var root: Path
    private val binding = CheckpointBindings("campaign", "lineage",
        mapOf("client_sha256" to "a".repeat(64), "server_sha256" to "b".repeat(64)),
        "c".repeat(64), "d".repeat(64))
    private val site = SamplingIntent.Site("minecraft:overworld", 0, 0, 11)

    private fun snapshot() = Files.createDirectories(root.resolve("checkpoints/origin/snapshot/server/world"))
    private fun receipt(files: List<Path> = emptyList()): CheckpointReceipt {
        val snapshot = root.resolve("checkpoints/origin/snapshot")
        val server = snapshot.resolve("server")
        val listed = files.map { path -> SnapshotFile("server", server.relativize(path).toString(),
            Files.size(path), Hashes.sha256(path), false) }
        val closure = EpochClosure("epoch", "native", Instant.now().toString(), true, true,
            emptyList(), emptyList(), BoundFile("/unrun/fixture", "0".repeat(64)))
        return CheckpointReceipt("bc.playtest_checkpoint.v2", "origin", binding, null,
            "epoch", closure, Instant.now().toString(), snapshot.toString(), listed, "0".repeat(64))
    }
    private fun region(relative: String, bytes: ByteArray): Path = root.resolve("checkpoints/origin/snapshot/server/$relative").also {
        Files.createDirectories(it.parent); Files.write(it, bytes)
    }
    private fun sectorImage(index: Int? = null): ByteArray = ByteArray(4096 * if (index == null) 2 else 3).also { raw ->
        if (index != null) {
            ByteBuffer.wrap(raw).putInt(index * 4, (2 shl 8) or 1)
            ByteBuffer.wrap(raw).putInt(8192, 6)
            raw[8196] = 2 // zlib; nonempty chunk payload within sector allocation.
        }
    }

    @Test fun `radius eleven inspects inclusive outer view chunks not merely the site center`() {
        snapshot()
        val path = region("world/region/r.0.0.mca", sectorImage(11 + 11 * 32))
        assertThrows(IllegalArgumentException::class.java) { verifyFreshTerrain(receipt(listOf(path)), listOf(site)) }
        Files.write(path, sectorImage(12 + 12 * 32)) // outside [-11,11], despite being same region.
        assertEquals(Hashes.sha256(path), verifyFreshTerrain(receipt(listOf(path)), listOf(site)).single().sha256)
    }

    @Test fun `negative chunk uses floorDiv region and floorMod location index`() {
        snapshot()
        val path = region("world/region/r.-1.-1.mca", sectorImage(1023))
        assertThrows(IllegalArgumentException::class.java) {
            verifyFreshTerrain(receipt(listOf(path)), listOf(SamplingIntent.Site("minecraft:overworld", -1, -1)))
        }
        Files.write(path, sectorImage())
        assertEquals(path.toString(), verifyFreshTerrain(receipt(listOf(path)),
            listOf(SamplingIntent.Site("minecraft:overworld", -1, -1))).single().path)
    }

    @Test fun `missing region needs complete caller inventory and custom dimensions use safe path`() {
        snapshot()
        assertTrue(verifyFreshTerrain(receipt(), listOf(site)).isEmpty())
        val custom = region("world/dimensions/example/deep/region/r.0.0.mca", sectorImage())
        assertEquals(custom.toString(), verifyFreshTerrain(receipt(listOf(custom)),
            listOf(SamplingIntent.Site("example:deep", 0, 0))).single().path)
        assertThrows(IllegalArgumentException::class.java) {
            verifyFreshTerrain(receipt(), listOf(SamplingIntent.Site("example:../deep", 0, 0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            verifyFreshTerrain(receipt(), listOf(SamplingIntent.Site("minecraft:overworld", Int.MAX_VALUE, 0, 32)))
        }
    }

    @Test fun `truncated nonaligned sector overlap or missing inventory never count as fresh`() {
        snapshot()
        val path = region("world/region/r.0.0.mca", ByteArray(4096))
        assertThrows(IllegalArgumentException::class.java) { verifyFreshTerrain(receipt(listOf(path)), listOf(site)) }
        Files.write(path, ByteArray(8200))
        assertThrows(IllegalArgumentException::class.java) { verifyFreshTerrain(receipt(listOf(path)), listOf(site)) }
        val overlap = sectorImage(12 + 12 * 32)
        ByteBuffer.wrap(overlap).putInt(13 + 13 * 32, (2 shl 8) or 1)
        Files.write(path, overlap)
        assertThrows(IllegalArgumentException::class.java) { verifyFreshTerrain(receipt(listOf(path)), listOf(site)) }
        Files.write(path, sectorImage())
        assertThrows(IllegalArgumentException::class.java) { verifyFreshTerrain(receipt(), listOf(site)) }
    }

    @Test fun `symlinked region and ancestor are rejected without following`() {
        snapshot()
        val path = region("world/region/r.0.0.mca", sectorImage())
        val alias = path.resolveSibling("alias.mca")
        Files.createSymbolicLink(alias, path)
        Files.delete(path)
        Files.createSymbolicLink(path, alias)
        assertThrows(IllegalArgumentException::class.java) { verifyFreshTerrain(receipt(), listOf(site)) }
    }
}
