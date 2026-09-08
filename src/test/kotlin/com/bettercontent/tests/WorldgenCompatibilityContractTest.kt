package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPInputStream
import kotlin.io.path.writeText

@Tag("fast")
class WorldgenCompatibilityContractTest {
    @Test
    fun crashedRocketOverrideReplacesTheStaleAuthoringPositionMapWithAnEmptyMap() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val override = root.resolve("kubejs/data/creatingspace/structures/crashed_rocket.nbt")
        val compressed = Files.readAllBytes(override)
        val raw = GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readAllBytes() }

        assertEquals("9d5a714cea5f5d98d34b6210e8db4c54b0e128706e1c9fb6e818cdec08185080", sha256(compressed))
        assertEquals("48f68ff4a8c8e3deed0dadb11318b6818cf2eeb71c39e1f7d875c64fd2dbdf61", sha256(raw))

        val document = NbtReader(raw).readRootCompound()
        assertEquals(listOf(8, 4, 7), document.list("size"))
        assertEquals(22, document.list("palette").size)
        assertEquals(224, document.list("blocks").size)
        assertEquals(0, document.list("entities").size)
        assertEquals(3120, document["DataVersion"])

        val controls = document.list("blocks")
            .map { it as Map<*, *> }
            .filter { block -> (block["nbt"] as? Map<*, *>)?.get("id") == "creatingspace:controls" }
        assertEquals(1, controls.size)
        assertEquals(listOf(3, 0, 3), controls.single()["pos"])
        assertEquals(11, controls.single()["state"])
        val controlsNbt = controls.single()["nbt"] as Map<*, *>
        assertEquals(setOf("id", "initialPosMap"), controlsNbt.keys)
        assertEquals(emptyMap<String, Any?>(), controlsNbt["initialPosMap"])
        assertTrue(raw.toString(Charsets.UTF_8).contains("initialPosMap"))
        assertFalse(raw.toString(Charsets.UTF_8).contains("dimensionInitialPosOf"))

        // Swap the explicit empty map for the one stale upstream tag at its original byte offset.
        // Matching the pinned upstream raw hash proves every other byte of geometry, palette,
        // blocks, and block-entity data stayed intact.
        val emptyTag = Base64.getDecoder().decode("CgANaW5pdGlhbFBvc01hcAA=")
        val removedTag = Base64.getDecoder().decode(
            "CgANaW5pdGlhbFBvc01hcAQAT2RpbWVuc2lvbkluaXRpYWxQb3NPZjpSZXNvdXJjZUtleVttaW5lY3JhZnQ6ZGltZW5zaW9uIC8gY3JlYXRpbmdzcGFjZTp0aGVfbW9vbl3//2IAAnJQLAA=",
        )
        assertTrue(raw.copyOfRange(8409, 8409 + emptyTag.size).contentEquals(emptyTag))
        val restored = raw.copyOfRange(0, 8409) + removedTag +
            raw.copyOfRange(8409 + emptyTag.size, raw.size)
        assertEquals(14_794, restored.size)
        assertEquals("e59c4579f1954832b015a31189666d9f85df47e3a72f51f44e9d1dc16c48c692", sha256(restored))
    }

    @Test
    fun falloutTraversalRejectsEveryCityRuinFarChunkWrite(@TempDir root: Path) {
        val clean = root.resolve("clean.log").also {
            it.writeText(
                "[C2ME worker #3/ERROR] [net.minecraft.Util]: Detected setBlock in a far chunk [57, 66], " +
                    "pos: BlockPos{x=923, y=63, z=1056}, status: minecraft:features, currently generating: " +
                    "ResourceKey[minecraft:worldgen/placed_feature / natures_spirit:marsh_water_placed]\n",
            )
        }
        val clippedRuin = root.resolve("clipped-ruin.log").also {
            it.writeText(
                "[17:08:22] [C2ME worker #3/ERROR] [net.minecraft.Util]: Detected setBlock in a far chunk [62498, 62496], " +
                    "pos: BlockPos{x=999968, y=62, z=999947}, status: minecraft:features, currently generating: " +
                    "ResourceKey[minecraft:worldgen/placed_feature / fallout_wastelands_:cityruins]\n",
            )
        }

        assertEquals(0, FalloutWorldgenEvidence.cityRuinFarWriteCount(clean))
        FalloutWorldgenEvidence.requireNoCityRuinFarWrites(clean)
        assertEquals(1, FalloutWorldgenEvidence.cityRuinFarWriteCount(clippedRuin))
        assertThrows(IllegalArgumentException::class.java) {
            FalloutWorldgenEvidence.requireNoCityRuinFarWrites(clippedRuin)
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class NbtReader(bytes: ByteArray) {
        private val input = DataInputStream(ByteArrayInputStream(bytes))

        fun readRootCompound(): Map<String, Any?> {
            require(input.readUnsignedByte() == COMPOUND) { "structure NBT root must be a compound" }
            input.readUTF()
            return readCompound()
        }

        private fun readPayload(type: Int): Any? = when (type) {
            BYTE -> input.readByte()
            SHORT -> input.readShort()
            INT -> input.readInt()
            LONG -> input.readLong()
            FLOAT -> input.readFloat()
            DOUBLE -> input.readDouble()
            BYTE_ARRAY -> ByteArray(input.readInt()).also(input::readFully)
            STRING -> input.readUTF()
            LIST -> {
                val elementType = input.readUnsignedByte()
                List(input.readInt()) { readPayload(elementType) }
            }
            COMPOUND -> readCompound()
            INT_ARRAY -> IntArray(input.readInt()) { input.readInt() }
            LONG_ARRAY -> LongArray(input.readInt()) { input.readLong() }
            else -> error("unsupported NBT tag type $type")
        }

        private fun readCompound(): Map<String, Any?> = buildMap {
            while (true) {
                val type = input.readUnsignedByte()
                if (type == END) return@buildMap
                put(input.readUTF(), readPayload(type))
            }
        }

        companion object {
            const val END = 0
            const val BYTE = 1
            const val SHORT = 2
            const val INT = 3
            const val LONG = 4
            const val FLOAT = 5
            const val DOUBLE = 6
            const val BYTE_ARRAY = 7
            const val STRING = 8
            const val LIST = 9
            const val COMPOUND = 10
            const val INT_ARRAY = 11
            const val LONG_ARRAY = 12
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.list(name: String): List<Any?> = this[name] as List<Any?>
}
