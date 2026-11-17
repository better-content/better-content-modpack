package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.shaPattern
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ

private const val SECTOR = 4096L
private const val MAX_CHUNK = 1_874_999L // x/z block bounds +/-29,999,984 / 16.
private const val MAX_REGION_SIZE = 1_100_000_000L

/**
 * Read-only proof that each complete reserved chunk rectangle was NOT generated in the verified
 * *input* checkpoint. Caller MUST first pass the exact receipt returned by CheckpointStore.verify:
 * only its fully verified sealed file inventory can prove that a missing region never existed.
 * This is independent of SamplingLedger.audit: a never-used site may still have old terrain.
 */
internal fun verifyFreshTerrain(receipt: CheckpointReceipt, sites: List<SamplingIntent.Site>): List<BoundFile> {
    require(receipt.schema == "bc.playtest_checkpoint.v2" && sites.isNotEmpty())
    val snapshot = Path.of(receipt.snapshotDirectory).toAbsolutePath().normalize()
    require(snapshot.toRealPath() == snapshot && Files.isDirectory(snapshot, NOFOLLOW_LINKS) &&
        !Files.isSymbolicLink(snapshot) && snapshot.fileName.toString() == "snapshot" &&
        snapshot.parent.fileName.toString() == receipt.id) { "unverified or escaped checkpoint snapshot" }
    val server = snapshot.resolve("server")
    require(Files.isDirectory(server, NOFOLLOW_LINKS) && !Files.isSymbolicLink(server))
    val listed = receipt.files.filter { it.root == "server" }.associateBy { it.path }
    require(listed.size == receipt.files.count { it.root == "server" }) { "duplicate sealed server inventory" }
    val requested = sortedMapOf<String, MutableSet<Int>>()
    sites.forEach { site ->
        val location = dimensionRegionRoot(site.dimension)
        val radius = site.footprintRadiusChunks
        require(radius in 0..32) { "sampling terrain radius outside 0..32" }
        val xMin = site.chunkX.toLong() - radius; val xMax = site.chunkX.toLong() + radius
        val zMin = site.chunkZ.toLong() - radius; val zMax = site.chunkZ.toLong() + radius
        require(xMin >= -MAX_CHUNK && xMax <= MAX_CHUNK && zMin >= -MAX_CHUNK && zMax <= MAX_CHUNK) {
            "sampling rectangle crosses Minecraft world bounds"
        }
        for (x in xMin.toInt()..xMax.toInt()) for (z in zMin.toInt()..zMax.toInt()) {
            val regionX = Math.floorDiv(x, 32); val regionZ = Math.floorDiv(z, 32)
            val entry = Math.floorMod(x, 32) + Math.floorMod(z, 32) * 32
            val relative = "$location/r.$regionX.$regionZ.mca"
            requested.getOrPut(relative) { mutableSetOf() } += entry
        }
    }
    val boundRegions = mutableListOf<BoundFile>()
    for ((relative, entries) in requested) {
        val path = server.resolve(relative).normalize()
        require(path.startsWith(server) && Path.of(relative).none { it.toString() in listOf(".", "..") }) {
            "region path escaped checkpoint"
        }
        var component = server
        for (part in Path.of(relative)) {
            component = component.resolve(part)
            if (Files.exists(component, NOFOLLOW_LINKS)) require(!Files.isSymbolicLink(component)) {
                "linked checkpoint region component: $component"
            }
        }
        val indexed = listed[relative]
        if (indexed == null) {
            require(!Files.exists(path, NOFOLLOW_LINKS)) { "region exists outside sealed inventory: $relative" }
            continue // Absent only because caller has already fully verified Store.verify(ref).
        }
        require(Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) &&
            path.toRealPath() == path && indexed.size == Files.size(path) && shaPattern.matches(indexed.sha256) &&
            (Files.getAttribute(path, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1) {
            "listed region missing, linked or altered: $relative"
        }
        FileChannel.open(path, READ).use { channel ->
            val size = channel.size()
            require(size in (2 * SECTOR)..MAX_REGION_SIZE && size % SECTOR == 0L) {
                "malformed/truncated/nonaligned MCA region: $relative"
            }
            val locations = ByteBuffer.allocate(SECTOR.toInt())
            readExactly(channel, locations, 0L)
            locations.flip()
            val totalSectors = size / SECTOR
            val used = mutableListOf<Pair<Long, Long>>()
            for (index in 0 until 1024) {
                val word = locations.int
                val sectorStart = (word ushr 8).toLong()
                val sectorCount = word and 0xff
                if (sectorStart == 0L && sectorCount == 0) continue
                require(sectorStart >= 2 && sectorCount > 0 && sectorStart + sectorCount <= totalSectors) {
                    "invalid or truncated MCA chunk allocation: $relative index $index"
                }
                val header = ByteBuffer.allocate(5)
                readExactly(channel, header, sectorStart * SECTOR)
                header.flip()
                val encodedLength = header.int
                val compression = header.get().toInt() and 0xff
                require(encodedLength in 1..(sectorCount * SECTOR - 4).toInt() &&
                    (compression and 0x7f) in 1..4) { "malformed MCA chunk payload header: $relative index $index" }
                used += sectorStart to (sectorStart + sectorCount)
            }
            var end = 2L
            for ((start, next) in used.sortedBy { it.first }) {
                require(start >= end) { "overlapping MCA sector allocations: $relative" }
                end = next
            }
            // A nonzero location entry is occupied regardless of its NBT content, timestamp or
            // whether a test player was ever placed by the campaign's sampling ledger.
            for (entry in entries) require(locations.getInt(entry * 4) == 0) {
                "already generated input terrain: $relative location index $entry"
            }
        }
        require(Hashes.sha256(path) == indexed.sha256) { "checkpoint MCA changed during freshness inspection" }
        boundRegions += BoundFile(path.toString(), indexed.sha256)
    }
    return boundRegions
}

private fun dimensionRegionRoot(dimension: String): String {
    require(resourcePattern.matches(dimension)) { "invalid dimension resource ID" }
    val namespace = dimension.substringBefore(':')
    val path = dimension.substringAfter(':')
    require(namespace !in listOf(".", "..") && path.split('/').all { it.isNotBlank() && it !in listOf(".", "..") }) {
        "dimension path escapes world directory"
    }
    return when (dimension) {
        "minecraft:overworld" -> "world/region"
        "minecraft:the_nether" -> "world/DIM-1/region"
        "minecraft:the_end" -> "world/DIM1/region"
        else -> "world/dimensions/$namespace/$path/region"
    }
}

private fun readExactly(channel: FileChannel, destination: ByteBuffer, offset: Long) {
    var position = offset
    while (destination.hasRemaining()) {
        val read = channel.read(destination, position)
        require(read > 0) { "truncated MCA header at byte $position" }
        position += read
    }
}
