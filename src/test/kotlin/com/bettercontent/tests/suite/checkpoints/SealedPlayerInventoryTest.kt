package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.zip.GZIPOutputStream

/** Synthetic decoder/wire fixtures only, UNRUN; none is a Store.verify(ref) or live acquisition. */
class SealedPlayerInventoryTest {
    @TempDir lateinit var root: Path
    private val uuid = "12345678-1234-1234-1234-123456789abc"
    private val relative = "world/playerdata/$uuid.dat"
    private val snapshot get() = root.resolve("checkpoints/input/snapshot")
    private val playerdata get() = snapshot.resolve("server/$relative")
    private fun writeGzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { stream ->
        GZIPOutputStream(stream).use { it.write(bytes) }
    }.toByteArray()
    private fun nbt(block: (DataOutputStream) -> Unit): ByteArray = ByteArrayOutputStream().also { stream ->
        DataOutputStream(stream).use(block)
    }.toByteArray()
    private fun named(out: DataOutputStream, type: Int, name: String, payload: (DataOutputStream) -> Unit) {
        out.writeByte(type); out.writeUTF(name); payload(out)
    }
    private fun item(out: DataOutputStream, slot: Int = 4, count: Int = 3,
        reversed: Boolean = false, withExtra: Boolean = false, nestedSlot: Int = 1,
        extraTypes: Boolean = false) {
        val base = listOf<(DataOutputStream) -> Unit>(
            { named(it, 1, "Slot") { o -> o.writeByte(slot) } },
            { named(it, 8, "id") { o -> o.writeUTF("example:filled_canteen") } },
            { named(it, 1, "Count") { o -> o.writeByte(count) } },
            { named(it, 10, "tag") { o -> named(o, 3, "Slot") { p -> p.writeInt(nestedSlot) }; o.writeByte(0) } }
        )
        val extra = if (!withExtra) emptyList() else listOf<(DataOutputStream) -> Unit>(
            { named(it, 10, "ForgeCaps") { o -> named(o, 8, "capability") { p -> p.writeUTF("water_source") }; o.writeByte(0) } }
        )
        val types = if (!extraTypes) emptyList() else listOf<(DataOutputStream) -> Unit>(
            { named(it, 2, "short") { o -> o.writeShort(-123) } },
            { named(it, 4, "long") { o -> o.writeLong(-123456789098765432L) } },
            { named(it, 5, "floatBits") { o -> o.writeInt(0x7fc00001) } },
            { named(it, 6, "doubleBits") { o -> o.writeLong(0x7ff8000000000001L) } },
            { named(it, 7, "bytes") { o -> o.writeInt(2); o.write(byteArrayOf(0, -1)) } },
            { named(it, 9, "list") { o -> o.writeByte(3); o.writeInt(2); o.writeInt(4); o.writeInt(5) } },
            { named(it, 11, "ints") { o -> o.writeInt(2); o.writeInt(1); o.writeInt(-1) } },
            { named(it, 12, "longs") { o -> o.writeInt(1); o.writeLong(-1) } },
            { named(it, 8, "é") { o -> o.writeUTF("λ") } }
        )
        val entries = base + extra + types
        (if (reversed) entries.reversed() else entries).forEach { it(out) }
        out.writeByte(0)
    }
    private fun player(vararg stacks: (DataOutputStream) -> Unit, savedUuid: String = uuid): ByteArray = nbt { out ->
        out.writeByte(10); out.writeUTF("")
        val identity = java.util.UUID.fromString(savedUuid)
        named(out, 11, "UUID") { value ->
            value.writeInt(4)
            value.writeInt((identity.mostSignificantBits ushr 32).toInt()); value.writeInt(identity.mostSignificantBits.toInt())
            value.writeInt((identity.leastSignificantBits ushr 32).toInt()); value.writeInt(identity.leastSignificantBits.toInt())
        }
        named(out, 9, "Inventory") { list ->
            list.writeByte(if (stacks.isEmpty()) 0 else 10); list.writeInt(stacks.size); stacks.forEach { it(list) }
        }
        out.writeByte(0)
    }
    private fun receipt(bytes: ByteArray, include: Boolean = true): CheckpointReceipt {
        Files.createDirectories(playerdata.parent)
        Files.write(playerdata, bytes)
        val entry = SnapshotFile("server", relative, bytes.size.toLong(), Hashes.sha256(playerdata), false)
        val closure = EpochClosure("epoch", "native", Instant.now().toString(), true, true,
            emptyList(), emptyList(), BoundFile("/unrun", "0".repeat(64)))
        return CheckpointReceipt("bc.playtest_checkpoint.v2", "input",
            CheckpointBindings("campaign", uuid, mapOf("client_sha256" to "a".repeat(64),
                "server_sha256" to "b".repeat(64)), "c".repeat(64), "d".repeat(64)),
            null, "epoch", closure, Instant.now().toString(), snapshot.toString(),
            if (include) listOf(entry) else emptyList(), "0".repeat(64))
    }
    private fun parse(bytes: ByteArray): SealedPlayerInventory = readSealedPlayerInventory(receipt(writeGzip(bytes)), uuid)
    private fun hash(bytes: ByteArray): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    @Test fun `canonical hash fixed wire excludes only root Slot and includes exact Count and id`() {
        val original = parse(player({ item(it) }))
        val row = original.stacks.single()
        val knownWire = nbt { out ->
            out.write("bc.playtest.stack-nbt.v1\u0000".toByteArray(Charsets.US_ASCII))
            out.writeByte(10); out.writeInt(3) // Count, id, tag; no top-level Slot.
            out.writeInt(5); out.write("Count".toByteArray()); out.writeByte(1); out.writeByte(3)
            out.writeInt(2); out.write("id".toByteArray()); out.writeByte(8)
            val id = "example:filled_canteen".toByteArray(); out.writeInt(id.size); out.write(id)
            out.writeInt(3); out.write("tag".toByteArray()); out.writeByte(10); out.writeInt(1)
            out.writeInt(4); out.write("Slot".toByteArray()); out.writeByte(3); out.writeInt(1)
        }
        assertEquals(hash(knownWire), row.canonicalStackSha256)
        assertEquals(STACK_NBT_HASH_ALGORITHM, row.hashAlgorithm)
        assertEquals(BoundFile(playerdata.toString(), Hashes.sha256(playerdata)), original.playerData)
        assertEquals(4, row.slot); assertEquals(3, row.count)
        assertEquals("example:filled_canteen", row.itemId)
        val changedSlot = parse(player({ item(it, slot = 5, reversed = true) })).stacks.single()
        assertEquals(row.canonicalStackSha256, changedSlot.canonicalStackSha256)
        assertNotEquals(row.slot, changedSlot.slot)
        assertNotEquals(row.canonicalStackSha256, parse(player({ item(it, count = 4) })).stacks.single().canonicalStackSha256)
        assertNotEquals(row.canonicalStackSha256, parse(player({ item(it, nestedSlot = 2) })).stacks.single().canonicalStackSha256)
        assertNotEquals(row.canonicalStackSha256, parse(player({ item(it, withExtra = true) })).stacks.single().canonicalStackSha256)
    }

    @Test fun `independent native shared vector for minecraft stone count one matches exact typed bytes`() {
        val saved = player({ out ->
            named(out, 1, "Slot") { it.writeByte(0) }
            named(out, 8, "id") { it.writeUTF("minecraft:stone") }
            named(out, 1, "Count") { it.writeByte(1) }
            out.writeByte(0)
        })
        // Hand-authored root compound, Count Byte=1 and id String=minecraft:stone; root Slot omitted.
        // Identical fixed vector is tested independently by nativeCanonicalStackIdentityTest.
        val body = HexFormat.of().parseHex("0a0000000200000005436f756e740101000000026964080000000f6d696e6563726166743a73746f6e65")
        val expected = hash("bc.playtest.stack-nbt.v1\u0000".toByteArray(Charsets.US_ASCII) + body)
        assertEquals(expected, parse(saved).stacks.single().canonicalStackSha256)
    }

    @Test fun `all vanilla payload types parse and compound order does not affect typed hash`() {
        val a = parse(player({ item(it, extraTypes = true) })).stacks.single().canonicalStackSha256
        val b = parse(player({ item(it, reversed = true, extraTypes = true) })).stacks.single().canonicalStackSha256
        assertEquals(a, b)
        assertNotEquals(a, parse(player({ item(it) })).stacks.single().canonicalStackSha256)
    }

    @Test fun `empty native inventory is valid but another players payload is not`() {
        assertEquals(emptyList<SealedInventoryStack>(), parse(player()).stacks)
        assertThrows(IllegalArgumentException::class.java) {
            parse(player({ item(it) }, savedUuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
        }
    }

    @Test fun `only main inventory slots are claimed and duplicate source slots fail closed`() {
        val inventory = parse(player({ item(it, slot = 2) }, { item(it, slot = 100) },
            { item(it, slot = 150) }))
        assertEquals(listOf(2), inventory.stacks.map { it.slot })
        assertThrows(IllegalArgumentException::class.java) {
            parse(player({ item(it, slot = 2) }, { item(it, slot = 2) }))
        }
    }

    @Test fun `missing altered linked or unbound playerdata and noncanonical UUID fail`() {
        val raw = player({ item(it) }); val gz = writeGzip(raw)
        assertThrows(IllegalArgumentException::class.java) { readSealedPlayerInventory(receipt(gz, include = false), uuid) }
        val ref = receipt(gz)
        Files.write(playerdata, gz + byteArrayOf(0))
        assertThrows(IllegalArgumentException::class.java) { readSealedPlayerInventory(ref, uuid) }
        assertThrows(IllegalArgumentException::class.java) { readSealedPlayerInventory(ref, uuid.uppercase()) }
        Files.delete(playerdata)
        val alternate = root.resolve("alternate.dat"); Files.write(alternate, gz)
        Files.createSymbolicLink(playerdata, alternate)
        assertThrows(IllegalArgumentException::class.java) { readSealedPlayerInventory(ref, uuid) }
        Files.delete(playerdata)
        Files.createLink(playerdata, alternate)
        assertThrows(IllegalArgumentException::class.java) { readSealedPlayerInventory(ref, uuid) }
    }

    @Test fun `single gzip member rejects trailing garbage second member corrupted trailer and truncation`() {
        val gz = writeGzip(player({ item(it) }))
        listOf(gz + byteArrayOf(1, 2, 3), gz + gz, gz.copyOfRange(0, gz.size - 2),
            gz.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }).forEach { bytes ->
            assertThrows(IllegalArgumentException::class.java) { readSealedPlayerInventory(receipt(bytes), uuid) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            readSealedPlayerInventory(receipt(writeGzip(player({ item(it) }) + byteArrayOf(1))), uuid)
        }
    }

    @Test fun `malformed UTF16 surrogate cannot collide through replacement UTF8`() {
        val malformed = player({ out ->
            named(out, 1, "Slot") { it.writeByte(4) }
            named(out, 1, "Count") { it.writeByte(1) }
            named(out, 8, "id") { it.writeUTF("example:tool") }
            named(out, 8, "bad") { it.writeUTF("\ud800") }
            out.writeByte(0)
        })
        assertThrows(Exception::class.java) { parse(malformed) }
    }

    @Test fun `malformed NBT lengths depth duplicate keys and unsupported tag never become stack proof`() {
        val hugeArray = player({ out ->
            named(out, 1, "Slot") { it.writeByte(4) }
            named(out, 1, "Count") { it.writeByte(1) }
            named(out, 8, "id") { it.writeUTF("example:tool") }
            named(out, 7, "huge") { it.writeInt(1_000_001) }
            out.writeByte(0)
        })
        assertThrows(IllegalArgumentException::class.java) { parse(hugeArray) }
        val tooManyNodes = player({ out ->
            named(out, 9, "oversized_list") { it.writeByte(1); it.writeInt(100_001) }
            out.writeByte(0)
        })
        assertThrows(IllegalArgumentException::class.java) { parse(tooManyNodes) }
        val malformedDuplicate = player({ out ->
            named(out, 1, "Slot") { it.writeByte(1) }
            named(out, 1, "Slot") { it.writeByte(1) }
            out.writeByte(0)
        })
        assertThrows(IllegalArgumentException::class.java) { parse(malformedDuplicate) }
        val unknownType = player({ out -> out.writeByte(13); out.writeUTF("unknown"); out.writeByte(0) })
        assertThrows(IllegalArgumentException::class.java) { parse(unknownType) }
        val deep = player({ out ->
            repeat(65) { named(out, 10, "nested") {} }
            repeat(66) { out.writeByte(0) }
        })
        assertThrows(IllegalArgumentException::class.java) { parse(deep) }
    }
}
