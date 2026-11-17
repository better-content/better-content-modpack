package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.exclusiveJson
import com.bettercontent.tests.suite.SuiteCase
import com.bettercontent.tests.suite.jsonFingerprint
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import com.fasterxml.jackson.databind.DeserializationFeature
import java.security.MessageDigest

/** Input-checkpoint terrain absence ONLY. A zero MCA location does not prove a genuine chunk birth. */
internal data class BirthChunkKey(val dimension: String, val chunkX: Int, val chunkZ: Int)
internal data class ParentTerrainAbsence(val schema: String, val parent: CheckpointRef,
    val parentInventorySha256: String, val chunkKeys: List<BirthChunkKey>,
    val watchedPositions: List<ScopedBlockPosition>, val presentRegions: List<BoundFile>,
    val absentRegionPaths: List<String>) {
    init {
        require(schema == "bc.playtest_parent_terrain_absence.v1" &&
            parentInventorySha256.matches(shaPattern) && chunkKeys.size in 1..64 &&
            watchedPositions.size in 1..64 &&
            chunkKeys == chunkKeys.distinct().sortedWith(birthChunkOrder) &&
            watchedPositions == watchedPositions.distinct().sortedWith(birthPositionOrder) &&
            watchedPositions.map(::birthChunkKey).toSet() == chunkKeys.toSet() &&
            presentRegions.map { it.path }.distinct().size == presentRegions.size &&
            absentRegionPaths == absentRegionPaths.distinct().sorted() &&
            chunkKeys.map(::relativeBirthRegion).toSet() ==
                (absentRegionPaths + presentRegions.map { region ->
                    chunkKeys.map(::relativeBirthRegion).distinct().single { relative ->
                        region.path.endsWith("/server/$relative") }
                }).toSet() &&
            presentRegions.map { it.path }.none { path -> absentRegionPaths.any(path::endsWith) }) {
            "parent terrain absence is not a closed bounded watched chunk/position roster"
        }
    }
}
private val birthChunkOrder = compareBy(BirthChunkKey::dimension, BirthChunkKey::chunkX, BirthChunkKey::chunkZ)
private val birthPositionOrder = compareBy(ScopedBlockPosition::dimension, ScopedBlockPosition::x,
    ScopedBlockPosition::y, ScopedBlockPosition::z)
private val birthDimensions = mapOf("minecraft:overworld" to "world/region",
    "minecraft:the_nether" to "world/DIM-1/region", "minecraft:the_end" to "world/DIM1/region")
private fun relativeBirthRegion(key: BirthChunkKey): String =
    "${birthDimensions.getValue(key.dimension)}/r.${Math.floorDiv(key.chunkX, 32)}.${Math.floorDiv(key.chunkZ, 32)}.mca"
internal fun birthChunkKey(position: ScopedBlockPosition): BirthChunkKey {
    require(position.dimension in birthDimensions && position.x in -29_999_984..29_999_984 &&
        position.z in -29_999_984..29_999_984 && position.y in -2048..2048) {
        "stage2 source lacks a bounded vanilla saved-region adapter"
    }
    return BirthChunkKey(position.dimension, Math.floorDiv(position.x, 16), Math.floorDiv(position.z, 16))
}

/** Caller supplies only independently reviewed selectors, not a native-chosen source coordinate. */
internal fun reviewedBirthPositions(specs: List<SuiteCase>, parent: CheckpointRef,
    catalogueSha256: String): List<ScopedBlockPosition> {
    require(catalogueSha256.matches(shaPattern))
    val selectors = specs.flatMap { it.targetBindings }.map { raw ->
        suiteMapper.treeToValue(raw, ReviewedTargetSelector::class.java)
    }.filter { it.source == TargetSource.NEWLY_GENERATED_TERRAIN }
    if (selectors.isEmpty()) return emptyList()
    require(selectors.all { it.inputCheckpointId == parent.id && it.catalogueSha256 == catalogueSha256 &&
        it.capabilityId == "target.observe.loaded_v1" && it.bounds.dimension in birthDimensions }) {
        "stage2 watched positions are not bound to one reviewed source catalogue and parent checkpoint"
    }
    val positions = selectors.flatMap { selector ->
        val b = selector.bounds
        val size = listOf(b.maxX.toLong() - b.minX + 1, b.maxY.toLong() - b.minY + 1,
            b.maxZ.toLong() - b.minZ + 1).fold(1L) { count, width ->
            require(width in 1..64 && count <= 64 / width) { "reviewed birth box exceeds 64 positions" }
            count * width
        }
        require(size in 1..64)
        (b.minX..b.maxX).flatMap { x -> (b.minY..b.maxY).flatMap { y ->
            (b.minZ..b.maxZ).map { z -> ScopedBlockPosition(b.dimension, x, y, z) }
        } }
    }.distinct().sortedWith(birthPositionOrder)
    require(positions.size in 1..64 && positions.map(::birthChunkKey).distinct().size <= 64)
    return positions
}

/** Recheck Store.verify(parent) before each replay; sealed inventory proves missing regions stayed absent. */
internal fun verifyParentTerrainAbsent(store: CheckpointStore, parent: CheckpointRef,
    expectedBindings: CheckpointBindings, positions: List<ScopedBlockPosition>): ParentTerrainAbsence {
    require(positions.size in 1..64 && positions == positions.distinct().sortedWith(birthPositionOrder))
    val keys = positions.map(::birthChunkKey).distinct().sortedWith(birthChunkOrder)
    require(keys.size in 1..64)
    val receipt = store.verify(parent)
    require(receipt.bindings == expectedBindings && receipt.id == parent.id) {
        "birth parent differs from the independently sealed campaign lineage"
    }
    val present = verifyFreshTerrain(receipt, keys.map { key ->
        SamplingIntent.Site(key.dimension, key.chunkX, key.chunkZ, 0)
    })
    val listed = receipt.files.filter { it.root == "server" }.map { it.path }.toSet()
    val relativeRegions = keys.map(::relativeBirthRegion).distinct().sorted()
    val absent = relativeRegions.filterNot(listed::contains)
    require(present.map { bound ->
        val snapshotServer = Path.of(receipt.snapshotDirectory).resolve("server")
        snapshotServer.relativize(Path.of(bound.path)).toString()
    }.toSet() == relativeRegions.filter(listed::contains).toSet()) {
        "fresh-region inventory did not account for every watched parent region"
    }
    return ParentTerrainAbsence("bc.playtest_parent_terrain_absence.v1", parent,
        jsonFingerprint(suiteMapper.valueToTree(receipt.files)), keys, positions,
        present.sortedBy { it.path }, absent)
}

/** Controller-owned prelaunch sidecar, not B's later native birth witness or a natural verdict. */
internal fun bindParentTerrainAbsence(evidence: Path, proven: ParentTerrainAbsence): BoundFile {
    val dir = evidence.toAbsolutePath().normalize()
    require(dir.fileName.toString() == "evidence" && Files.isDirectory(dir, NOFOLLOW_LINKS) &&
        !Files.isSymbolicLink(dir))
    val file = dir.resolve("target-birth-parent-absence.json")
    require(!Files.exists(file, NOFOLLOW_LINKS))
    exclusiveJson(file, proven)
    require(Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) &&
        Files.size(file) in 2..1_048_576)
    return BoundFile(file.toString(), Hashes.sha256(file))
}

/** Read-only qualifier reconstruction: exact captured SHA, then independent Store-verified absent slots. */
internal fun replayParentTerrainAbsence(store: CheckpointStore, parent: CheckpointRef,
    bindings: CheckpointBindings, evidence: Path, sidecar: BoundFile,
    reviewedPositions: List<ScopedBlockPosition>): ParentTerrainAbsence {
    val dir = evidence.toAbsolutePath().normalize()
    val file = dir.resolve("target-birth-parent-absence.json")
    require(dir.fileName.toString() == "evidence" && Files.isDirectory(dir, NOFOLLOW_LINKS) &&
        !Files.isSymbolicLink(dir) && sidecar.path == file.toString() &&
        sidecar.sha256.matches(shaPattern) && Files.isRegularFile(file, NOFOLLOW_LINKS) &&
        !Files.isSymbolicLink(file) && Files.size(file) in 2..1_048_576)
    val bytes = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(1_048_577) }
    val captured = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    require(bytes.size in 2..1_048_576 && captured == sidecar.sha256 &&
        Files.size(file) == bytes.size.toLong() && Hashes.sha256(file) == captured) {
        "parent-absence sidecar changed during exact byte capture"
    }
    val recorded: ParentTerrainAbsence = suiteMapper.readerFor(ParentTerrainAbsence::class.java)
        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes)
    val reconstructed = verifyParentTerrainAbsent(store, parent, bindings, reviewedPositions)
    require(recorded == reconstructed) { "claimed parent chunk absence differs from sealed MCA/inventory" }
    return reconstructed
}
