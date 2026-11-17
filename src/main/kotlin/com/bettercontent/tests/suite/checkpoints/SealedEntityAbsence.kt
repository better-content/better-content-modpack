package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.resourcePattern
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.UUID

/** Vanilla saved entity-region roster only; external/provider entity persistence remains unsupported. */
internal data class SavedSourceEntityAbsence(val checkpointId: String, val treeSha256: String,
    val checkedRegionFiles: List<BoundFile>, val checkedChunks: Int, val absentUuids: List<String>,
    val storageScope: String) {
    init { require(storageScope == "vanilla_1_20_1_entity_region_storage_only" &&
        absentUuids.isNotEmpty() && checkedChunks in 0..512) }
}

/**
 * Independently enumerate every Store-verified vanilla saved entity MCA chunk, including unloaded
 * chunks and all dimension paths. Zero source UUIDs in the saved roster is NOT runtime nonexistence.
 */
internal fun verifySealedSourceEntitiesAbsent(receipt: CheckpointReceipt,
    sourceUuids: List<String>): SavedSourceEntityAbsence {
    require(receipt.schema == "bc.playtest_checkpoint.v2" && sourceUuids.isNotEmpty() &&
        sourceUuids.size <= 256 && sourceUuids.distinct().size == sourceUuids.size &&
        sourceUuids.all { runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false) })
    val snapshot = Path.of(receipt.snapshotDirectory).toAbsolutePath().normalize()
    require(snapshot.fileName.toString() == "snapshot" && snapshot.parent?.fileName?.toString() == receipt.id &&
        Files.isDirectory(snapshot, NOFOLLOW_LINKS) && !Files.isSymbolicLink(snapshot) &&
        snapshot.toRealPath() == snapshot)
    val regionName = Regex("r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca")
    val entityRegions = receipt.files.filter { it.root == "server" &&
        it.path.startsWith("world/") && it.path.substringAfterLast('/').matches(regionName) &&
        it.path.split('/').dropLast(1).lastOrNull() == "entities" && it.directory != true }
    require(receipt.files.none { it.root == "server" && it.path.startsWith("world/") &&
        (it.path.contains("/entities/") || it.path.startsWith("world/entities/")) &&
        it.directory != true && it !in entityRegions }) {
        "unrecognized saved entity-region/external encoding cannot establish source absence"
    }
    require(entityRegions.size <= 128 && entityRegions.sumOf { it.size } <= 268_435_456L)
    val source = sourceUuids.toSet()
    val seen = mutableSetOf<String>()
    var visitedEntities = 0
    fun visitLiveEntity(entry: NbtValue, depth: Int) {
        require(entry.type == 10 && depth in 0..16 && ++visitedEntities <= 16_384) {
            "saved vanilla entity/passenger roster exceeds bounded live-record scope"
        }
        val fields = entry.payload as Map<*, *>
        val id = fields["id"] as? NbtValue
        val parts = fields["UUID"] as? NbtValue
        require(id?.type == 8 && (id.payload as String).matches(resourcePattern) &&
            parts?.type == 11 && (parts.payload as IntArray).size == 4) {
            "actual saved live entity/passenger lacks typed registry id or UUID"
        }
        val ints = parts.payload as IntArray
        fun half(index: Int): Long = (ints[index].toLong() shl 32) or (ints[index + 1].toLong() and 0xffffffffL)
        val uuid = UUID(half(0), half(2)).toString()
        require(seen.add(uuid) && uuid !in source) { "source UUID remains in saved vanilla entity/passenger storage" }
        // Only the vanilla entity Passengers roster is a live nested entity. Do not confuse
        // descriptive UUIDs in Item/Owner/tag metadata with another world entity instance.
        fields["Passengers"]?.let { tag ->
            require(tag is NbtValue && tag.type == 9)
            val passengers = tag.payload as NbtList
            require((passengers.type == 10 || (passengers.type == 0 && passengers.values.isEmpty())) &&
                passengers.values.size <= 4096)
            passengers.values.forEach { visitLiveEntity(it, depth + 1) }
        }
    }
    val checked = mutableListOf<BoundFile>()
    var chunks = 0
    for (entry in entityRegions.sortedBy { it.path }) {
        require(entry.size in 8192..67_108_864 && entry.size % 4096 == 0L)
        val name = regionName.matchEntire(entry.path.substringAfterLast('/'))
            ?: throw IllegalArgumentException("saved entity region filename invalid")
        val regionX = name.groupValues[1].toInt(); val regionZ = name.groupValues[2].toInt()
        val file = snapshot.resolve("server").resolve(entry.path).normalize()
        require(file.startsWith(snapshot.resolve("server")))
        var parent = snapshot
        for (part in snapshot.relativize(file)) {
            parent = parent.resolve(part)
            require(!Files.isSymbolicLink(parent)) { "linked saved entity component" }
        }
        require(Files.isRegularFile(file, NOFOLLOW_LINKS) && file.toRealPath() == file &&
            Files.size(file) == entry.size && Hashes.sha256(file) == entry.sha256 &&
            (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1) {
            "saved entity region did not match Store-verified inventory"
        }
        val bytes = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(67_108_865) }
        require(bytes.size.toLong() == entry.size && capturedSha256(bytes) == entry.sha256 &&
            Hashes.sha256(file) == entry.sha256)
        for (localZ in 0 until 32) for (localX in 0 until 32) {
            val header = 4 * (localX + localZ * 32)
            val allocated = (bytes[header].toInt() and 0xff) != 0 ||
                (bytes[header + 1].toInt() and 0xff) != 0 ||
                (bytes[header + 2].toInt() and 0xff) != 0
            val sectors = bytes[header + 3].toInt() and 0xff
            if (!allocated && sectors == 0) continue
            require(allocated && sectors > 0 && ++chunks <= 512) {
                "entity region allocation or bounded scan budget differs"
            }
            val chunkX = Math.addExact(Math.multiplyExact(regionX, 32), localX)
            val chunkZ = Math.addExact(Math.multiplyExact(regionZ, 32), localZ)
            val root = decodePinnedMcaRoot(bytes, chunkX, chunkZ)
            val pos = root["Position"] ?: throw IllegalArgumentException("saved entity chunk lacks Position")
            require(pos.type == 11 && (pos.payload as IntArray).contentEquals(intArrayOf(chunkX, chunkZ))) {
                "saved entity chunk coordinates disagree with MCA allocation"
            }
            val entities = root["Entities"] ?: throw IllegalArgumentException("saved entity chunk lacks Entities list")
            require(entities.type == 9)
            val list = entities.payload as NbtList
            require((list.type == 10 || (list.type == 0 && list.values.isEmpty())) && list.values.size <= 4096)
            list.values.forEach { visitLiveEntity(it, 0) }
        }
        checked += BoundFile(file.toString(), entry.sha256)
    }
    return SavedSourceEntityAbsence(receipt.id, receipt.treeSha256, checked, chunks,
        sourceUuids.sorted(), "vanilla_1_20_1_entity_region_storage_only")
}
