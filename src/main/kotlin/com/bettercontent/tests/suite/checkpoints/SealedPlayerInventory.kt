package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.resourcePattern
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

internal const val STACK_NBT_HASH_ALGORITHM = "bc.playtest.stack-nbt.v1"
internal const val STACK_CONTENT_HASH_ALGORITHM = "bc.playtest.stack-content.v1"
private const val MAX_COMPRESSED = 16 * 1024 * 1024
private const val MAX_INFLATED = 64 * 1024 * 1024
private const val MAX_DEPTH = 64
private const val MAX_NODES = 100_000
private const val MAX_ARRAY_ELEMENTS = 1_000_000
private const val MAX_INVENTORY_ENTRIES = 256

internal data class SealedInventoryStack(val slot: Int, val itemId: String, val count: Int,
    val hashAlgorithm: String, val canonicalStackSha256: String,
    val contentAlgorithm: String, val contentSha256: String)
/** Absence from the saved main-inventory list is observed EMPTY, never a forged air:0 hash. */
internal sealed interface SealedMainSlot {
    val slot: Int
    data class Empty(override val slot: Int) : SealedMainSlot
    data class Nonempty(override val slot: Int, val stack: SealedInventoryStack) : SealedMainSlot
}
/** Main 0..35, armor boots→helmet 36..39 from saved 100..103, offhand 40 from saved 150. */
internal sealed interface SealedVanillaSlot {
    val slot: Int
    data class Empty(override val slot: Int) : SealedVanillaSlot
    data class Nonempty(override val slot: Int, val stack: SealedInventoryStack) : SealedVanillaSlot
}
internal data class SealedPlayerInventory(val checkpointId: String, val playerUuid: String,
    val playerData: BoundFile, val stacks: List<SealedInventoryStack>, val savedGameModeId: Int?,
    val vanillaStacks: List<SealedInventoryStack>) {
    fun mainSlot(slot: Int): SealedMainSlot {
        require(slot in 0..35) { "only vanilla main inventory has this independently decoded slot" }
        val matching = stacks.singleOrNull { it.slot == slot }
        return if (matching == null) SealedMainSlot.Empty(slot) else SealedMainSlot.Nonempty(slot, matching)
    }
    fun vanillaSlot(slot: Int): SealedVanillaSlot {
        require(slot in 0..40) { "only vanilla main/armor/offhand normalized inventory is independently decoded" }
        val matching = vanillaStacks.singleOrNull { it.slot == slot }
        return if (matching == null) SealedVanillaSlot.Empty(slot) else SealedVanillaSlot.Nonempty(slot, matching)
    }
}

/** Same-captured validated playerdata yields BOTH canonical stack hashes and typed NBT, never SNBT. */
internal data class SealedSavedPlayerPosition(val dimension: String, val x: Double, val y: Double, val z: Double) {
    init { require(dimension.length in 3..256 && dimension.matches(resourcePattern) &&
        !dimension.substringAfter(':').split('/').any { it == "." || it == ".." } &&
        listOf(x, y, z).all(Double::isFinite)) {
        "generic saved player position needs a typed resource dimension and finite coordinates"
    } }
}
internal data class CapturedSealedVanillaInventory(val inventory: SealedPlayerInventory,
    val typedStacksBySlot: Map<Int, NbtValue>, val savedPosition: SealedSavedPlayerPosition? = null) {
    init { require(typedStacksBySlot.keys == inventory.vanillaStacks.map { it.slot }.toSet() &&
        typedStacksBySlot.keys.all { it in 0..40 } &&
        typedStacksBySlot.values.all { it.type == 10 }) }
    fun typedSlot(slot: Int): NbtValue? {
        require(slot in 0..40)
        return typedStacksBySlot[slot]
    }
}

/**
 * Read-only saved vanilla main/armor/offhand inventory identity, not acquisition ancestry.
 * PRECONDITION: receipt is the exact result of CheckpointStore.verify(ref) on this sealed input;
 * this helper cannot establish that a fabricated receipt has a complete file inventory.
 * A separate native event/producer must bind actual acquisition, player and stack to this input.
 * Curios, capability-contained inventory slots and external provider storage are NOT covered.
 */
internal fun readSealedPlayerInventory(receipt: CheckpointReceipt, canonicalPlayerUuid: String): SealedPlayerInventory =
    readSealedVanillaInventoryWithTags(receipt, canonicalPlayerUuid).inventory

/** One bounded NOFOLLOW captured playerdata parse for both Count-sensitive hashes and D's typed NBT oracle. */
internal fun readSealedVanillaInventoryWithTags(receipt: CheckpointReceipt,
    canonicalPlayerUuid: String): CapturedSealedVanillaInventory {
    require(receipt.schema == "bc.playtest_checkpoint.v2" &&
        runCatching { UUID.fromString(canonicalPlayerUuid).toString() == canonicalPlayerUuid }.getOrDefault(false)) {
        "verified checkpoint receipt and canonical player UUID required"
    }
    val snapshot = Path.of(receipt.snapshotDirectory).toAbsolutePath().normalize()
    require(snapshot.fileName.toString() == "snapshot" && snapshot.parent?.fileName?.toString() == receipt.id &&
        Files.isDirectory(snapshot, NOFOLLOW_LINKS) && !Files.isSymbolicLink(snapshot) &&
        snapshot.toRealPath() == snapshot) { "checkpoint snapshot path escaped or changed" }
    val relative = "world/playerdata/$canonicalPlayerUuid.dat"
    val listed = receipt.files.filter { it.root == "server" && it.path == relative }
    require(listed.size == 1 && listed.single().directory != true &&
        listed.single().size in 18L..MAX_COMPRESSED.toLong() && shaPattern.matches(listed.single().sha256)) {
        "exact sealed playerdata file absent or outside size/hash limits"
    }
    val file = snapshot.resolve("server").resolve(relative)
    var component = snapshot
    for (part in snapshot.relativize(file)) {
        component = component.resolve(part)
        require(!Files.isSymbolicLink(component)) { "linked playerdata component: $component" }
    }
    require(Files.isRegularFile(file, NOFOLLOW_LINKS) && file.toRealPath() == file &&
        Files.size(file) == listed.single().size &&
        (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1 &&
        Hashes.sha256(file) == listed.single().sha256) { "sealed playerdata altered or linked" }
    val compressed = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(MAX_COMPRESSED + 1) }
    require(compressed.size.toLong() == listed.single().size &&
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(compressed)) == listed.single().sha256 &&
        Hashes.sha256(file) == listed.single().sha256) { "sealed playerdata changed during read" }
    val raw = inflateOnePinnedGzip(compressed)
    val input = DataInputStream(ByteArrayInputStream(raw))
    val parser = NbtReader(input)
    require(input.readUnsignedByte() == 10 && parser.readString().isEmpty()) { "playerdata root must be unnamed compound" }
    val root = parser.readValue(10, 0).payload as Map<*, *>
    require(input.available() == 0) { "trailing decompressed playerdata" }
    val savedUuid = root["UUID"] as? NbtValue
    require(savedUuid?.type == 11) { "saved player UUID missing or not an int array" }
    val uuidParts = savedUuid.payload as IntArray
    require(uuidParts.size == 4) { "saved player UUID must contain four ints" }
    fun uuidHalf(index: Int): Long = (uuidParts[index].toLong() shl 32) or (uuidParts[index + 1].toLong() and 0xffffffffL)
    require(UUID(uuidHalf(0), uuidHalf(2)).toString() == canonicalPlayerUuid) {
        "playerdata contents do not belong to the requested player"
    }
    val inventory = root["Inventory"] as? NbtValue
        ?: throw IllegalArgumentException("vanilla player Inventory missing")
    require(inventory.type == 9) { "vanilla player Inventory must be list" }
    val list = inventory.payload as NbtList
    require((list.type == 10 || (list.type == 0 && list.values.isEmpty())) &&
        list.values.size <= MAX_INVENTORY_ENTRIES) { "vanilla Inventory compound list invalid" }
    val seen = mutableSetOf<Int>()
    val typedStacks = linkedMapOf<Int, NbtValue>()
    val vanillaStacks = list.values.map { entry ->
        val contents = entry.payload as Map<*, *>
        val slotTag = contents["Slot"] as? NbtValue
            ?: throw IllegalArgumentException("saved ItemStack has no Slot")
        require(slotTag.type == 1) { "saved ItemStack Slot not byte" }
        val rawSlot = (slotTag.payload as Byte).toInt() and 0xff
        require(seen.add(rawSlot)) { "duplicate saved inventory slot: $rawSlot" }
        val slot = when (rawSlot) {
            in 0..35 -> rawSlot
            in 100..103 -> rawSlot - 64 // saved armor slot100=normalized boots36, ...103=helmet39
            150 -> 40 // saved signed byte -106 is normalized offhand40
            else -> throw IllegalArgumentException("unmapped vanilla saved inventory Slot: $rawSlot")
        }
        val idTag = contents["id"] as? NbtValue
            ?: throw IllegalArgumentException("saved ItemStack has no id")
        val countTag = contents["Count"] as? NbtValue
            ?: throw IllegalArgumentException("saved ItemStack has no Count")
        require(idTag.type == 8 && countTag.type == 1 && (countTag.payload as Byte).toInt() > 0) {
            "saved ItemStack id or Count type/value invalid"
        }
        val itemId = idTag.payload as String
        require(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(itemId) &&
            itemId.split(':').size == 2 && !itemId.substringAfter(':').split('/').any { it == "." || it == ".." }) {
            "invalid saved ItemStack registry id"
        }
        val saved = contents.mapKeys { it.key as String }
        val exact = NbtValue(10, saved.filterKeys { it != "Slot" })
        val content = NbtValue(10, saved.filterKeys { it != "Slot" && it != "Count" })
        require(typedStacks.put(slot, exact) == null) { "duplicate normalized saved vanilla slot" }
        SealedInventoryStack(slot, itemId, (countTag.payload as Byte).toInt(),
            STACK_NBT_HASH_ALGORITHM, canonicalStackHash(exact, STACK_NBT_HASH_ALGORITHM),
            STACK_CONTENT_HASH_ALGORITHM, canonicalStackHash(content, STACK_CONTENT_HASH_ALGORITHM))
    }.sortedBy { it.slot }
    val gameModeTag = root["playerGameType"] as? NbtValue
    require(gameModeTag == null || gameModeTag.type == 3) { "saved player game mode has invalid NBT type" }
    val dimensionTag = root["Dimension"] as? NbtValue
    val positionTag = root["Pos"] as? NbtValue
    require(dimensionTag == null || dimensionTag.type == 8)
    require(positionTag == null || positionTag.type == 9)
    val savedPosition = if (dimensionTag == null || positionTag == null) null else {
        val coords = positionTag.payload as NbtList
        require(coords.type == 6 && coords.values.size == 3 && coords.values.all { it.type == 6 })
        SealedSavedPlayerPosition(dimensionTag.payload as String,
            Double.fromBits(coords.values[0].payload as Long),
            Double.fromBits(coords.values[1].payload as Long),
            Double.fromBits(coords.values[2].payload as Long))
    }
    return CapturedSealedVanillaInventory(SealedPlayerInventory(receipt.id, canonicalPlayerUuid,
        BoundFile(file.toString(), listed.single().sha256), vanillaStacks.filter { it.slot <= 35 },
        gameModeTag?.payload as? Int, vanillaStacks), typedStacks.toSortedMap(), savedPosition)
}

/** Strict one-member gzip: pinned vanilla 10-byte header, flags=0, raw DEFLATE, CRC32 and ISIZE. */
private fun inflateOnePinnedGzip(bytes: ByteArray): ByteArray {
    require(bytes.size in 18..MAX_COMPRESSED && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte() &&
        bytes[2] == 8.toByte() && bytes[3] == 0.toByte()) { "unsupported gzip header or extensions" }
    val inflater = Inflater(true)
    return try {
        inflater.setInput(bytes, 10, bytes.size - 18)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val count = try { inflater.inflate(buffer) }
                catch (e: DataFormatException) { throw IllegalArgumentException("malformed playerdata DEFLATE", e) }
            require(count > 0 || inflater.finished()) { "truncated playerdata DEFLATE or dictionary required" }
            require(out.size().toLong() + count <= MAX_INFLATED) { "playerdata inflation budget exceeded" }
            out.write(buffer, 0, count)
        }
        require(inflater.remaining == 0) { "trailing compressed bytes or concatenated gzip member" }
        val raw = out.toByteArray()
        val crc = CRC32().apply { update(raw) }.value
        fun trailerWord(start: Int): Long = (0 until 4).fold(0L) { value, index ->
            value or ((bytes[start + index].toLong() and 0xff) shl (8 * index))
        }
        require(trailerWord(bytes.size - 8) == crc && trailerWord(bytes.size - 4) == raw.size.toLong()) {
            "playerdata gzip CRC32 or ISIZE mismatch"
        }
        raw
    } finally { inflater.end() }
}

internal data class NbtValue(val type: Int, val payload: Any)
internal data class NbtList(val type: Int, val values: List<NbtValue>)

private fun utf8(text: String): ByteArray {
    val buffer = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text))
    return ByteArray(buffer.remaining()).also { buffer.get(it) }
}

internal class NbtReader(private val input: DataInputStream) {
    private var nodes = 0
    fun readString(): String = input.readUTF().also { utf8(it) } // MUTF-8 on disk; reject malformed UTF-16.
    private fun countNode(depth: Int) {
        require(depth <= MAX_DEPTH && ++nodes <= MAX_NODES) { "NBT depth/node budget exceeded" }
    }
    private fun countArray(): Int = input.readInt().also {
        require(it in 0..MAX_ARRAY_ELEMENTS) { "NBT array exceeds element budget" }
    }
    fun readValue(type: Int, depth: Int): NbtValue {
        require(type in 1..12) { "unknown/End NBT payload type: $type" }
        countNode(depth)
        val value: Any = when (type) {
            1 -> input.readByte()
            2 -> input.readShort()
            3 -> input.readInt()
            4 -> input.readLong()
            5 -> input.readInt() // Preserve raw IEEE754 bits, even NaN payloads.
            6 -> input.readLong()
            7 -> ByteArray(countArray()).also(input::readFully)
            8 -> readString()
            9 -> {
                val childType = input.readUnsignedByte()
                val length = input.readInt()
                require(length in 0..MAX_NODES && childType in 0..12 && (length == 0 || childType != 0)) {
                    "malformed NBT list"
                }
                NbtList(childType, (0 until length).map { readValue(childType, depth + 1) })
            }
            10 -> {
                val children = linkedMapOf<String, NbtValue>()
                while (true) {
                    val childType = input.readUnsignedByte()
                    if (childType == 0) break
                    require(childType in 1..12) { "unknown compound child type" }
                    val name = readString()
                    require(!children.containsKey(name)) { "duplicate NBT compound key" }
                    children[name] = readValue(childType, depth + 1)
                }
                children
            }
            11 -> IntArray(countArray()) { input.readInt() }
            12 -> LongArray(countArray()) { input.readLong() }
            else -> error("NBT type validation failed")
        }
        return NbtValue(type, value)
    }
}

/** Same byte wire must be independently emitted by nativeCanonicalStackIdentity.java. Never hash SNBT. */
internal fun canonicalStackHash(root: NbtValue, algorithm: String): String {
    require(root.type == 10 && algorithm in setOf(STACK_NBT_HASH_ALGORITHM, STACK_CONTENT_HASH_ALGORITHM))
    val digest = MessageDigest.getInstance("SHA-256")
    // Stream into the digest so a large on-disk NBT cannot double memory through canonical output.
    val bounded = object : OutputStream() {
        private var written = 0L
        override fun write(value: Int) { checkBudget(1); digest.update(value.toByte()) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
            checkBudget(length); digest.update(bytes, offset, length)
        }
        private fun checkBudget(length: Int) {
            require(written + length <= MAX_INFLATED) { "canonical stack NBT exceeds 64MiB wire budget" }
            written += length
        }
    }
    DataOutputStream(bounded).use { out ->
        out.write("$algorithm\u0000".toByteArray(Charsets.US_ASCII))
        canonicalValue(out, root)
    }
    return HexFormat.of().formatHex(digest.digest())
}

private fun canonicalValue(out: DataOutputStream, tag: NbtValue) {
    out.writeByte(tag.type)
    canonicalPayload(out, tag)
}

private fun canonicalPayload(out: DataOutputStream, tag: NbtValue) {
    when (tag.type) {
        1 -> out.writeByte((tag.payload as Byte).toInt())
        2 -> out.writeShort((tag.payload as Short).toInt())
        3, 5 -> out.writeInt(tag.payload as Int)
        4, 6 -> out.writeLong(tag.payload as Long)
        7 -> (tag.payload as ByteArray).let { out.writeInt(it.size); out.write(it) }
        8 -> utf8(tag.payload as String).let { out.writeInt(it.size); out.write(it) }
        9 -> (tag.payload as NbtList).let { list ->
            out.writeByte(list.type); out.writeInt(list.values.size)
            list.values.forEach { require(it.type == list.type); canonicalPayload(out, it) }
        }
        10 -> {
            val children = tag.payload as Map<*, *>
            val names = children.keys.map { key -> (key as String).let { it to utf8(it) } }.sortedWith { left, right ->
                val a = left.second; val b = right.second
                var result = 0
                for (index in 0 until minOf(a.size, b.size)) {
                    result = (a[index].toInt() and 0xff).compareTo(b[index].toInt() and 0xff)
                    if (result != 0) break
                }
                if (result != 0) result else a.size.compareTo(b.size)
            }
            out.writeInt(names.size)
            for ((name, encoded) in names) {
                out.writeInt(encoded.size); out.write(encoded)
                canonicalValue(out, children[name] as? NbtValue ?: throw IllegalArgumentException("missing canonical compound child"))
            }
        }
        11 -> (tag.payload as IntArray).let { out.writeInt(it.size); it.forEach(out::writeInt) }
        12 -> (tag.payload as LongArray).let { out.writeInt(it.size); it.forEach(out::writeLong) }
        else -> throw IllegalArgumentException("unknown canonical NBT type")
    }
}
