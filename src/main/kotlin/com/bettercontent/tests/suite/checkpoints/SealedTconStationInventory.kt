package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.resourcePattern
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Typed MCA saved station slots; empty is explicit absence from the BE Items list. */
internal data class SealedTconStationInventory(val checkpoint: CheckpointRef,
    val dimension: String, val x: Int, val y: Int, val z: Int, val tileId: String,
    val region: BoundFile, val slots: List<SealedStationSlot>) {
    init { require(tileId in setOf("tconstruct:part_builder", "tconstruct:tinker_station") &&
        slots.indices.all { slots[it].slot == it } &&
        slots.size == if (tileId == "tconstruct:part_builder") 2 else 6) }
}
internal data class SealedStationSlot(val slot: Int, val itemId: String?, val count: Int?,
    val typed: NbtValue?, val stackSha256: String?) {
    init { require(slot in 0..63 && (itemId == null) == (typed == null) &&
        (itemId == null) == (count == null) && (itemId == null) == (stackSha256 == null)) }
}

/** Same independently Store-verified, NOFOLLOW single captured MCA file feeds every station slot. */
internal fun readSealedTconStationInventory(store: CheckpointStore, ref: CheckpointRef,
    dimension: String, x: Int, y: Int, z: Int, expectedTileId: String,
    reviewedSlotCount: Int): SealedTconStationInventory {
    require(expectedTileId in setOf("tconstruct:part_builder", "tconstruct:tinker_station") &&
        reviewedSlotCount == if (expectedTileId == "tconstruct:part_builder") 2 else 6)
    require(x in -30_000_000..30_000_000 && z in -30_000_000..30_000_000 &&
        y in -2048..2048)
    val prefix = when (dimension) {
        "minecraft:overworld" -> "world/region"
        "minecraft:the_nether" -> "world/DIM-1/region"
        "minecraft:the_end" -> "world/DIM1/region"
        else -> error("station tile requires reviewed vanilla dimension MCA decoder")
    }
    val receipt = store.verify(ref)
    val chunkX = Math.floorDiv(x, 16); val chunkZ = Math.floorDiv(z, 16)
    val regionX = Math.floorDiv(chunkX, 32); val regionZ = Math.floorDiv(chunkZ, 32)
    val relative = "$prefix/r.$regionX.$regionZ.mca"
    val entry = receipt.files.singleOrNull { it.root == "server" && it.path == relative && it.directory != true }
        ?: error("reviewed station has no Store-inventoried saved region")
    require(entry.size in 8192..67_108_864)
    val snapshot = Path.of(receipt.snapshotDirectory).toAbsolutePath().normalize()
    val file = snapshot.resolve("server").resolve(relative)
    var parent = snapshot
    for (part in snapshot.relativize(file)) {
        parent = parent.resolve(part)
        require(!Files.isSymbolicLink(parent))
    }
    require(Files.isRegularFile(file, NOFOLLOW_LINKS) && file.toRealPath() == file &&
        Files.size(file) == entry.size &&
        (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1 &&
        Hashes.sha256(file) == entry.sha256)
    val bytes = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(67_108_865) }
    require(bytes.size.toLong() == entry.size && bytes.size % 4096 == 0 &&
        capturedSha256(bytes) == entry.sha256 && Hashes.sha256(file) == entry.sha256)
    val root = decodePinnedMcaRoot(bytes, chunkX, chunkZ)
    require(root["xPos"].stationNumber(3) == chunkX && root["zPos"].stationNumber(3) == chunkZ &&
        root["Status"]?.type == 8 && root["Status"]?.payload in setOf("full", "minecraft:full"))
    val tileRoster = root["block_entities"]?.stationList(10, 4096)
        ?: error("saved station chunk lacks typed block-entity roster")
    val seen = mutableSetOf<Triple<Int, Int, Int>>()
    var target: Map<String, NbtValue>? = null
    for (raw in tileRoster) {
        val tile = raw.stationCompound()
        val id = tile["id"]
        val at = Triple(tile["x"].stationNumber(3), tile["y"].stationNumber(3), tile["z"].stationNumber(3))
        require(id?.type == 8 && (id.payload as String).matches(resourcePattern) &&
            Math.floorDiv(at.first, 16) == chunkX && Math.floorDiv(at.third, 16) == chunkZ &&
            at.second in -2048..2048 && seen.add(at)) { "duplicate/invalid saved block entity" }
        if (at == Triple(x, y, z)) {
            require(id.payload == expectedTileId) { "saved station tile registry differs from reviewed provider" }
            target = tile
        }
    }
    require(target != null) { "reviewed station tile absent from sealed chunk" }
    val inventory = requireNotNull(target["Items"]) {
        "saved TCon station Items list absent"
    }
    require("InventorySize" !in target) {
        "pinned TCon TableBlockEntity passes saveSizeToNBT=false; saved size tag is not valid"
    }
    return SealedTconStationInventory(ref, dimension, x, y, z, expectedTileId,
        BoundFile(file.toString(), entry.sha256), decodePinnedTconStationSlots(inventory, reviewedSlotCount))
}

/** Pure typed slot decoder, usable by unrun malformed-NBT regression sources. */
internal fun decodePinnedTconStationSlots(items: NbtValue, reviewedSlotCount: Int): List<SealedStationSlot> {
    require(reviewedSlotCount in setOf(2, 6))
    val inventory = items.stationList(10, 128)
    val indexed = mutableMapOf<Int, SealedStationSlot>()
    for (row in inventory) {
        val stack = row.stationCompound()
        val slotTag = stack["Slot"]
        require(slotTag?.type == 1)
        val slot = (slotTag.payload as Byte).toInt() and 0xff
        require(slot in 0 until reviewedSlotCount && slot !in indexed)
        val id = stack["id"]; val count = stack["Count"]
        require(id?.type == 8 && (id.payload as String).matches(resourcePattern) &&
            count?.type == 1 && (count.payload as Byte).toInt() in 1..64)
        stack["tag"]?.let { require(it.type == 10) }
        val exact = NbtValue(10, stack.filterKeys { it != "Slot" })
        indexed[slot] = SealedStationSlot(slot, id.payload as String, (count.payload as Byte).toInt(),
            exact, canonicalStackHash(exact, STACK_NBT_HASH_ALGORITHM))
    }
    require(indexed.size == inventory.size)
    return (0 until reviewedSlotCount).map { slot ->
        indexed[slot] ?: SealedStationSlot(slot, null, null, null, null)
    }
}
private fun NbtValue?.stationNumber(expected: Int): Int {
    val tag = requireNotNull(this)
    require(tag.type == expected)
    return when (expected) { 1 -> (tag.payload as Byte).toInt(); 3 -> tag.payload as Int
        else -> error("unreviewed station numeric tag") }
}
private fun NbtValue.stationCompound(): Map<String, NbtValue> {
    require(type == 10)
    @Suppress("UNCHECKED_CAST")
    return payload as Map<String, NbtValue>
}
private fun NbtValue.stationList(expected: Int, cap: Int): List<NbtValue> {
    require(type == 9)
    val list = payload as NbtList
    require(list.type == expected && list.values.size <= cap)
    return list.values
}
