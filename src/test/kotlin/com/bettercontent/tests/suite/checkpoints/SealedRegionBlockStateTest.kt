package com.bettercontent.tests.suite.checkpoints

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.DeflaterOutputStream

/** Independently authored MCA/NBT bytes in TEST SOURCE only; no generator/test/world was executed. */
class SealedRegionBlockStateTest {
    private fun nbt(palette: List<String> = listOf("minecraft:stone"),
        packed: LongArray? = null, status: String = "minecraft:full", xPos: Int = 0,
        selectedBlockEntity: Boolean = false, malformedOtherTile: Boolean = false): ByteArray =
        ByteArrayOutputStream().also { stream -> DataOutputStream(stream).use { out ->
            fun named(type: Int, name: String, payload: () -> Unit) {
                out.writeByte(type); out.writeUTF(name); payload()
            }
            out.writeByte(10); out.writeUTF("")
            named(3, "DataVersion") { out.writeInt(3465) }
            named(3, "xPos") { out.writeInt(xPos) }
            named(3, "zPos") { out.writeInt(0) }
            named(8, "Status") { out.writeUTF(status) }
            named(9, "block_entities") {
                out.writeByte(10); out.writeInt(if (selectedBlockEntity || malformedOtherTile) 1 else 0)
                if (selectedBlockEntity || malformedOtherTile) {
                    if (!malformedOtherTile) named(8, "id") { out.writeUTF("minecraft:chest") }
                    named(3, "x") { out.writeInt(if (selectedBlockEntity) 0 else 1) }
                    named(3, "y") { out.writeInt(64) }
                    named(3, "z") { out.writeInt(0) }
                    out.writeByte(0)
                }
            }
            named(9, "sections") {
                out.writeByte(10); out.writeInt(1)
                named(1, "Y") { out.writeByte(4) }
                named(10, "block_states") {
                    named(9, "palette") {
                        out.writeByte(10); out.writeInt(palette.size)
                        palette.forEach { id ->
                            named(8, "Name") { out.writeUTF(id) }
                            out.writeByte(0)
                        }
                    }
                    packed?.let { values -> named(12, "data") {
                        out.writeInt(values.size); values.forEach(out::writeLong)
                    } }
                    out.writeByte(0)
                }
                out.writeByte(0) // one section compound
            }
            out.writeByte(0) // root compound
        } }.toByteArray()
    private fun region(chunk: ByteArray): ByteArray {
        val compressed = ByteArrayOutputStream().also { DeflaterOutputStream(it).use { zipper -> zipper.write(chunk) } }.toByteArray()
        val length = compressed.size + 1
        val sectors = (length + 4 + 4095) / 4096
        require(sectors in 1..255)
        return ByteArray((2 + sectors) * 4096).apply {
            this[2] = 2; this[3] = sectors.toByte() // chunk at header index0, sector offset2
            this[4099] = 1 // nonzero timestamp
            val start = 8192
            this[start] = (length ushr 24).toByte(); this[start + 1] = (length ushr 16).toByte()
            this[start + 2] = (length ushr 8).toByte(); this[start + 3] = length.toByte()
            this[start + 4] = 2 // zlib, not external stream/gzip
            compressed.copyInto(this, start + 5)
        }
    }
    @Test fun `pinned singleton and packed palette return exact saved block without origin claim`() {
        assertEquals("minecraft:stone" to emptyMap<String, String>(),
            decodePinnedMcaBlock(region(nbt()), 0, 0, 0, 64, 0))
        val packed = LongArray(256)
        val cell = 1 + 16 * 1 // x=1 z=1 y=64; 4 bits/state, 16 per Long
        packed[cell / 16] = 1L shl ((cell % 16) * 4)
        val raw = region(nbt(listOf("minecraft:stone", "minecraft:grass_block"), packed))
        assertEquals("minecraft:grass_block" to emptyMap<String, String>(),
            decodePinnedMcaBlock(raw, 0, 0, 1, 64, 1))
        assertEquals("minecraft:stone" to emptyMap<String, String>(),
            decodePinnedMcaBlock(raw, 0, 0, 0, 64, 0))
        assertEquals("minecraft:air" to emptyMap<String, String>(),
            decodePinnedMcaBlock(raw, 0, 0, 0, 80, 0)) // no section Y5 is explicit saved air
    }
    @Test fun `missing chunk bad compression and changing saved coordinates or status reject`() {
        val valid = region(nbt())
        assertThrows(IllegalArgumentException::class.java) { decodePinnedMcaBlock(valid, 1, 0, 0, 64, 0) }
        assertThrows(IllegalArgumentException::class.java) {
            decodePinnedMcaBlock(valid.copyOf().apply { this[3] = 0 }, 0, 0, 0, 64, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodePinnedMcaBlock(valid.copyOf().apply { this[8196] = 3 }, 0, 0, 0, 64, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodePinnedMcaBlock(region(nbt(xPos = 1)), 0, 0, 0, 64, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodePinnedMcaBlock(region(nbt(status = "minecraft:empty")), 0, 0, 0, 64, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodePinnedMcaBlock(region(nbt(selectedBlockEntity = true)), 0, 0, 0, 64, 0)
        } // no independent saved block-entity provider
        assertThrows(IllegalArgumentException::class.java) {
            decodePinnedMcaBlock(region(nbt(malformedOtherTile = true)), 0, 0, 0, 64, 0)
        } // malformed non-target tile cannot escape via nullable coordinate comparison
    }
    @Test fun `palette counts and packed indices cannot invent a target block`() {
        assertThrows(IllegalArgumentException::class.java) {
            decodePinnedMcaBlock(region(nbt(palette = emptyList())), 0, 0, 0, 64, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodePinnedMcaBlock(region(nbt(listOf("minecraft:stone", "minecraft:dirt"), LongArray(1))), 0, 0, 0, 64, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodePinnedMcaBlock(region(nbt(listOf("minecraft:stone"), LongArray(256))), 0, 0, 0, 64, 0)
        }
        val invalid = LongArray(256); invalid[0] = 3L
        assertThrows(IllegalArgumentException::class.java) {
            decodePinnedMcaBlock(region(nbt(listOf("minecraft:stone", "minecraft:dirt"), invalid)), 0, 0, 0, 64, 0)
        }
        val distantInvalid = LongArray(256); distantInvalid[0] = 3L // selected cell17 is valid0
        assertThrows(IllegalArgumentException::class.java) {
            decodePinnedMcaBlock(region(nbt(listOf("minecraft:stone", "minecraft:dirt"), distantInvalid)),
                0, 0, 1, 64, 1)
        }
    }
    @Test fun `all MCA allocation headers reject overlap partial and out-of-region aliases`() {
        val valid = region(nbt())
        val alias = valid.copyOf().apply { this[6] = 2; this[7] = 1; this[4103] = 1 }
        assertThrows(IllegalArgumentException::class.java) { decodePinnedMcaBlock(alias, 0, 0, 0, 64, 0) }
        val partial = valid.copyOf().apply { this[7] = 1 }
        assertThrows(IllegalArgumentException::class.java) { decodePinnedMcaBlock(partial, 0, 0, 0, 64, 0) }
        val escaped = valid.copyOf().apply { this[6] = 30; this[7] = 1; this[4103] = 1 }
        assertThrows(IllegalArgumentException::class.java) { decodePinnedMcaBlock(escaped, 0, 0, 0, 64, 0) }
        val headerAlias = valid.copyOf().apply { this[6] = 1; this[7] = 1; this[4103] = 1 }
        assertThrows(IllegalArgumentException::class.java) { decodePinnedMcaBlock(headerAlias, 0, 0, 0, 64, 0) }
    }
}
