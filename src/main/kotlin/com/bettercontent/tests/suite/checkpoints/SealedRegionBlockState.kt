package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.resourcePattern
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/** Saved origin BLOCK FACT ONLY. A baseline plus a current census is NOT an unmodified interval. */
internal data class SealedBlockBaseline(val checkpointId: String, val dimension: String,
    val x: Int, val y: Int, val z: Int, val blockId: String, val properties: Map<String, String>,
    val region: BoundFile, val chunkX: Int, val chunkZ: Int) {
    init { require(dimension in setOf("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end") &&
        blockId.matches(resourcePattern) && region.sha256.matches(com.bettercontent.tests.suite.shaPattern)) }
    // No natural/unmodified truth bit: generation provenance and all mutations require separate evidence.
}

/** Bounded MCA 4KiB-sector and modern paletted chunk-NBT decoder, independent of native target census. */
internal fun readSealedBlockBaseline(store: CheckpointStore, ref: CheckpointRef,
    dimension: String, x: Int, y: Int, z: Int): SealedBlockBaseline {
    val pathPrefix = when (dimension) {
        "minecraft:overworld" -> "world/region"
        "minecraft:the_nether" -> "world/DIM-1/region"
        "minecraft:the_end" -> "world/DIM1/region"
        else -> throw IllegalArgumentException("modded dimensions require a reviewed saved-region adapter")
    }
    require(x in -30_000_000..30_000_000 && z in -30_000_000..30_000_000 && y in -2048..2048)
    val receipt = store.verify(ref)
    val chunkX = Math.floorDiv(x, 16); val chunkZ = Math.floorDiv(z, 16)
    val regionX = Math.floorDiv(chunkX, 32); val regionZ = Math.floorDiv(chunkZ, 32)
    val relative = "$pathPrefix/r.$regionX.$regionZ.mca"
    val entry = receipt.files.singleOrNull { it.root == "server" && it.path == relative && it.directory != true }
        ?: throw IllegalArgumentException("target chunk was absent from sealed snapshot inventory")
    require(entry.size in 8_192..67_108_864) { "sealed region outside bounded MCA envelope" }
    val snapshot = Path.of(receipt.snapshotDirectory).toAbsolutePath().normalize()
    val file = snapshot.resolve("server").resolve(relative)
    var parent = snapshot
    for (part in snapshot.relativize(file)) {
        parent = parent.resolve(part)
        require(!Files.isSymbolicLink(parent)) { "linked sealed region path" }
    }
    require(Files.isRegularFile(file, NOFOLLOW_LINKS) && file.toRealPath() == file &&
        Files.size(file) == entry.size &&
        (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1 &&
        Hashes.sha256(file) == entry.sha256) { "sealed region not owned or changed" }
    val bytes = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(67_108_865) }
    require(bytes.size.toLong() == entry.size && bytes.size % 4096 == 0 &&
        capturedSha256(bytes) == entry.sha256 && Hashes.sha256(file) == entry.sha256) {
        "sealed MCA region changed during bounded read"
    }
    val block = decodePinnedMcaBlock(bytes, chunkX, chunkZ, x, y, z)
    return SealedBlockBaseline(receipt.id, dimension, x, y, z, block.first, block.second,
        BoundFile(file.toString(), entry.sha256), chunkX, chunkZ)
}

/** Pure decoder for independently authored bounded region fixture bytes; never invokes a game runtime. */
internal fun decodePinnedMcaBlock(bytes: ByteArray, chunkX: Int, chunkZ: Int,
    x: Int, y: Int, z: Int): Pair<String, Map<String, String>> {
    require(Math.floorDiv(x, 16) == chunkX && Math.floorDiv(z, 16) == chunkZ)
    val root = decodePinnedMcaRoot(bytes, chunkX, chunkZ)
    require(root["xPos"]?.number(3) == chunkX && root["zPos"]?.number(3) == chunkZ &&
        root["Status"]?.type == 8 && root["Status"]?.payload in setOf("full", "minecraft:full")) {
        "pinned block chunk coordinate or FULL status differs"
    }
    // Block-entity NBT is not a block-state provider. Do not treat an initial tile-bearing
    // target as an unmodified natural block until an independent tile persistence adapter exists.
    val tiles = root["block_entities"] ?: throw IllegalArgumentException("pinned saved block-entity roster absent")
    require(tiles.type == 9)
    val tileList = tiles.payload as NbtList
    require((tileList.type == 10 || (tileList.type == 0 && tileList.values.isEmpty())) && tileList.values.size <= 4096)
    val tilePositions = mutableSetOf<Triple<Int, Int, Int>>()
    tileList.values.forEach { entry ->
        val tile = entry.compound()
        val tileId = tile["id"]
        require(tileId?.type == 8 && (tileId.payload as String).matches(resourcePattern)) {
            "saved block-entity roster lacks a typed registry ID"
        }
        val at = Triple(tile["x"]?.number(3) ?: error("tile x missing"),
            tile["y"]?.number(3) ?: error("tile y missing"),
            tile["z"]?.number(3) ?: error("tile z missing"))
        require(Math.floorDiv(at.first, 16) == chunkX && Math.floorDiv(at.third, 16) == chunkZ &&
            at.second in -2048..2048 && tilePositions.add(at)) {
            "saved block entity outside chunk or duplicated in source roster"
        }
    }
    require(Triple(x, y, z) !in tilePositions) {
        "block entity at selected initial target requires independent provider"
    }
    val sections = root["sections"] ?: throw IllegalArgumentException("modern saved section list absent")
    require(sections.type == 9)
    val sectionList = sections.payload as NbtList
    require(sectionList.type == 10 && sectionList.values.size <= 64)
    val targetSection = Math.floorDiv(y, 16)
    val sectionsByY = mutableMapOf<Int, Map<String, NbtValue>>()
    sectionList.values.forEach { row ->
        val fields = row.compound(); val height = fields["Y"]?.number(1) ?: error("section Y missing")
        require(sectionsByY.putIfAbsent(height, fields) == null) { "duplicate saved chunk section" }
    }
    val storage = sectionsByY[targetSection]?.get("block_states")
    val block = if (storage == null) "minecraft:air" to emptyMap<String, String>() else {
        val states = storage.compound()
        val paletteTag = states["palette"] ?: throw IllegalArgumentException("section block-state palette missing")
        require(paletteTag.type == 9)
        val paletteList = paletteTag.payload as NbtList
        require(paletteList.type == 10 && paletteList.values.size in 1..4096)
        val palette = paletteList.values.map { item ->
            val fields = item.compound(); val name = fields["Name"]
            require(name?.type == 8 && (name.payload as String).matches(resourcePattern))
            val properties = fields["Properties"]?.compound()?.mapValues { (_, value) ->
                require(value.type == 8 && (value.payload as String).length in 1..128)
                value.payload as String
            }.orEmpty()
            require(properties.keys.all { it.matches(Regex("[a-z0-9_]{1,64}")) })
            (name.payload as String) to properties.toSortedMap()
        }
        val index = if (palette.size == 1) {
            require(states["data"] == null) { "singleton palette must not invent block-state storage" }
            0
        } else {
            val data = states["data"] ?: throw IllegalArgumentException("non-singleton palette lacks packed data")
            require(data.type == 12)
            val packed = data.payload as LongArray
            val bits = maxOf(4, 32 - Integer.numberOfLeadingZeros(palette.size - 1))
            val valuesPerLong = 64 / bits
            val expectedLongs = (4096 + valuesPerLong - 1) / valuesPerLong
            require(bits in 4..12 && packed.size == expectedLongs)
            fun indexAt(cell: Int): Int {
                val raw = (packed[cell / valuesPerLong] ushr ((cell % valuesPerLong) * bits)) and ((1L shl bits) - 1L)
                require(raw < palette.size) { "packed palette index escapes saved block-state roster" }
                return raw.toInt()
            }
            // Every ACTUAL used cell, not only the fortunate selected target; ignore unused padding bits.
            (0 until 4096).forEach { cell -> indexAt(cell) }
            indexAt(Math.floorMod(x, 16) + 16 * Math.floorMod(z, 16) + 256 * Math.floorMod(y, 16))
        }
        palette[index]
    }
    return block
}

/** Strict pinned zlib MCA chunk framing shared by independent saved block and entity domains. */
internal fun capturedSha256(bytes: ByteArray): String = HexFormat.of().formatHex(
    MessageDigest.getInstance("SHA-256").digest(bytes))

internal fun decodePinnedMcaRoot(bytes: ByteArray, chunkX: Int, chunkZ: Int): Map<String, NbtValue> {
    require(bytes.size in 8192..67_108_864 && bytes.size % 4096 == 0)
    fun unsigned(index: Int) = bytes[index].toInt() and 0xff
    val occupied = BooleanArray(bytes.size / 4096)
    occupied[0] = true; occupied[1] = true // two immutable MCA header sectors
    var selectedOffset = -1
    var selectedSectors = -1
    val headerIndex = 4 * (Math.floorMod(chunkX, 32) + 32 * Math.floorMod(chunkZ, 32))
    for (entry in 0 until 1024) {
        val index = entry * 4
        val start = (unsigned(index) shl 16) or (unsigned(index + 1) shl 8) or unsigned(index + 2)
        val count = unsigned(index + 3)
        if (start == 0 && count == 0) continue
        require(start >= 2 && count in 1..255 && (start.toLong() + count) <= occupied.size &&
            (0..3).any { unsigned(4096 + index + it) != 0 }) {
            "partial, unrecorded, header-overlapping or out-of-bounds MCA allocation"
        }
        for (sector in start until start + count) {
            require(!occupied[sector]) { "overlapping MCA chunk allocations" }
            occupied[sector] = true
        }
        if (index == headerIndex) { selectedOffset = start; selectedSectors = count }
    }
    val offset = selectedOffset; val sectors = selectedSectors
    require(offset >= 2 && sectors in 1..255) { "selected saved chunk absent from MCA header" }
    val start = offset * 4096
    val length = (unsigned(start).toLong() shl 24) or (unsigned(start + 1).toLong() shl 16) or
        (unsigned(start + 2).toLong() shl 8) or unsigned(start + 3).toLong()
    require(length in 2..(sectors * 4096L - 4) && length <= 16_777_216 &&
        unsigned(start + 4) == 2) { "chunk length or pinned zlib compression unsupported" }
    val encoded = bytes.copyOfRange(start + 5, start + 4 + length.toInt())
    val inflated = Inflater(false).let { inflater ->
        try {
            inflater.setInput(encoded)
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = try { inflater.inflate(buffer) }
                    catch (e: DataFormatException) { throw IllegalArgumentException("invalid sealed chunk zlib", e) }
                require(count > 0 || inflater.finished()) { "truncated sealed chunk or dictionary" }
                require(out.size().toLong() + count <= 16_777_216) { "inflated chunk exceeds 16MiB" }
                out.write(buffer, 0, count)
            }
            require(inflater.remaining == 0) { "trailing compressed chunk bytes" }
            out.toByteArray()
        } finally { inflater.end() }
    }
    val input = DataInputStream(ByteArrayInputStream(inflated))
    val parser = NbtReader(input)
    require(input.readUnsignedByte() == 10 && parser.readString().isEmpty()) { "chunk root not unnamed compound" }
    val root = parser.readValue(10, 0).compound()
    require(input.available() == 0 && root["DataVersion"]?.type == 3 &&
        root["DataVersion"]?.payload == 3465) {
        "trailing NBT or unsupported saved Minecraft 1.20.1 chunk DataVersion"
    }
    return root
}

private fun NbtValue.compound(): Map<String, NbtValue> {
    require(type == 10)
    val raw = payload as Map<*, *>
    return raw.mapKeys { (key, _) -> key as String }.mapValues { (_, value) -> value as NbtValue }
}
private fun NbtValue.number(expected: Int): Int {
    require(type == expected)
    return when (expected) {
        1 -> (payload as Byte).toInt()
        3 -> payload as Int
        else -> error("unsupported saved block-state numeric tag")
    }
}
