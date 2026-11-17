package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.zip.DeflaterOutputStream

/** Synthetic receipt fixture tests only the independent MCA decoder, not Store.verify or a world. UNRUN. */
class SealedEntityAbsenceTest {
    private val storedUuid = UUID.fromString("12345678-1234-1234-1234-123456789abc")
    private fun writeUuid(out: DataOutputStream, value: UUID) {
        out.writeByte(11); out.writeUTF("UUID"); out.writeInt(4)
        out.writeInt((value.mostSignificantBits ushr 32).toInt())
        out.writeInt(value.mostSignificantBits.toInt())
        out.writeInt((value.leastSignificantBits ushr 32).toInt())
        out.writeInt(value.leastSignificantBits.toInt())
    }
    private fun entityNbt(positionX: Int = 0, passengerUuid: UUID? = null,
        descriptiveUuid: UUID? = null): ByteArray = ByteArrayOutputStream().also { stream ->
        DataOutputStream(stream).use { out ->
            out.writeByte(10); out.writeUTF("") // unnamed root compound
            out.writeByte(3); out.writeUTF("DataVersion"); out.writeInt(3465)
            out.writeByte(11); out.writeUTF("Position"); out.writeInt(2); out.writeInt(positionX); out.writeInt(0)
            out.writeByte(9); out.writeUTF("Entities"); out.writeByte(10); out.writeInt(1)
            out.writeByte(8); out.writeUTF("id"); out.writeUTF("minecraft:item")
            writeUuid(out, storedUuid)
            descriptiveUuid?.let { value ->
                out.writeByte(10); out.writeUTF("Item")
                writeUuid(out, value) // descriptive item metadata is NOT another live entity
                out.writeByte(0)
            }
            passengerUuid?.let { value ->
                out.writeByte(9); out.writeUTF("Passengers"); out.writeByte(10); out.writeInt(1)
                out.writeByte(8); out.writeUTF("id"); out.writeUTF("minecraft:item")
                writeUuid(out, value)
                out.writeByte(0) // real passenger entity compound
            }
            out.writeByte(0) // entity compound terminator
            out.writeByte(0) // root compound terminator
        }
    }.toByteArray()
    private fun region(nbt: ByteArray): ByteArray {
        val compressed = ByteArrayOutputStream().also { DeflaterOutputStream(it).use { zipped -> zipped.write(nbt) } }.toByteArray()
        val length = compressed.size + 1
        return ByteArray(12288).apply {
            this[2] = 2; this[3] = 1; this[4099] = 1
            this[8192] = (length ushr 24).toByte(); this[8193] = (length ushr 16).toByte()
            this[8194] = (length ushr 8).toByte(); this[8195] = length.toByte()
            this[8196] = 2; compressed.copyInto(this, 8197)
        }
    }
    private fun receipt(root: Path, bytes: ByteArray): CheckpointReceipt {
        val snapshot = Files.createDirectories(root.resolve("unit-snapshot/snapshot"))
        val file = snapshot.resolve("server/world/entities/r.0.0.mca")
        Files.createDirectories(file.parent); Files.write(file, bytes)
        return CheckpointReceipt("bc.playtest_checkpoint.v2", "unit-snapshot",
            CheckpointBindings("campaign-${UUID.randomUUID()}", UUID.randomUUID().toString(), emptyMap(),
                "a".repeat(64), "b".repeat(64)), null, "epoch-00000",
            EpochClosure("epoch-00000", "epoch-${UUID.randomUUID()}", "2026-11-16T00:00:00Z", true, true,
                emptyList(), emptyList(), BoundFile(root.resolve("dummy.json").toString(), "c".repeat(64))),
            "2026-11-16T00:00:00Z", snapshot.toString(),
            listOf(SnapshotFile("server", "world/entities/r.0.0.mca", Files.size(file), Hashes.sha256(file), false)),
            "d".repeat(64))
    }
    @Test fun `actual saved source UUID is not absent while unrelated saved entity is allowed`() {
        val root = Files.createTempDirectory(Path.of("/home/dev/.tmp"), "saved-entity-")
        try {
            val checked = receipt(root, region(entityNbt()))
            val other = UUID.randomUUID().toString()
            assertEquals(listOf(other), verifySealedSourceEntitiesAbsent(checked, listOf(other)).absentUuids)
            assertEquals(1, verifySealedSourceEntitiesAbsent(checked, listOf(other)).checkedChunks)
            assertThrows(IllegalArgumentException::class.java) {
                verifySealedSourceEntitiesAbsent(checked, listOf(storedUuid.toString()))
            }
            assertThrows(IllegalArgumentException::class.java) {
                verifySealedSourceEntitiesAbsent(checked.copy(files = checked.files +
                    SnapshotFile("server", "world/entities/c.0.0.mcc", 1, "e".repeat(64), false)), listOf(other))
            }
        } finally {
            Files.deleteIfExists(root.resolve("unit-snapshot/snapshot/server/world/entities/r.0.0.mca"))
            Files.deleteIfExists(root.resolve("unit-snapshot/snapshot/server/world/entities"))
            Files.deleteIfExists(root.resolve("unit-snapshot/snapshot/server/world"))
            Files.deleteIfExists(root.resolve("unit-snapshot/snapshot/server"))
            Files.deleteIfExists(root.resolve("unit-snapshot/snapshot"))
            Files.deleteIfExists(root.resolve("unit-snapshot")); Files.deleteIfExists(root)
        }
    }
    @Test fun `real passenger UUID remains live but descriptive nested UUID does not invent an entity`() {
        val root = Files.createTempDirectory(Path.of("/home/dev/.tmp"), "saved-entity-")
        try {
            val source = UUID.randomUUID()
            val descriptive = receipt(root, region(entityNbt(descriptiveUuid = source)))
            assertEquals(listOf(source.toString()),
                verifySealedSourceEntitiesAbsent(descriptive, listOf(source.toString())).absentUuids)
            val passenger = receipt(root, region(entityNbt(passengerUuid = source)))
            assertThrows(IllegalArgumentException::class.java) {
                verifySealedSourceEntitiesAbsent(passenger, listOf(source.toString()))
            }
        } finally {
            Files.deleteIfExists(root.resolve("unit-snapshot/snapshot/server/world/entities/r.0.0.mca"))
            Files.deleteIfExists(root.resolve("unit-snapshot/snapshot/server/world/entities"))
            Files.deleteIfExists(root.resolve("unit-snapshot/snapshot/server/world"))
            Files.deleteIfExists(root.resolve("unit-snapshot/snapshot/server"))
            Files.deleteIfExists(root.resolve("unit-snapshot/snapshot"))
            Files.deleteIfExists(root.resolve("unit-snapshot")); Files.deleteIfExists(root)
        }
    }
    @Test fun `saved entity chunk claiming another coordinate cannot establish absence`() {
        val root = Files.createTempDirectory(Path.of("/home/dev/.tmp"), "saved-entity-")
        try {
            val receipt = receipt(root, region(entityNbt(positionX = 1)))
            assertThrows(IllegalArgumentException::class.java) {
                verifySealedSourceEntitiesAbsent(receipt, listOf(UUID.randomUUID().toString()))
            }
        } finally {
            Files.deleteIfExists(root.resolve("unit-snapshot/snapshot/server/world/entities/r.0.0.mca"))
            Files.deleteIfExists(root.resolve("unit-snapshot/snapshot/server/world/entities"))
            Files.deleteIfExists(root.resolve("unit-snapshot/snapshot/server/world"))
            Files.deleteIfExists(root.resolve("unit-snapshot/snapshot/server"))
            Files.deleteIfExists(root.resolve("unit-snapshot/snapshot"))
            Files.deleteIfExists(root.resolve("unit-snapshot")); Files.deleteIfExists(root)
        }
    }
}
