package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

class WorldSaveTest {
    @TempDir lateinit var root: Path
    private fun compressed(bytes: ByteArray): Path = root.resolve("level.dat").also { path ->
        GZIPOutputStream(Files.newOutputStream(path)).use { it.write(bytes) }
    }
    private fun valid(): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use {
            it.writeByte(10); it.writeUTF("")
            it.writeByte(10); it.writeUTF("Data")
            it.writeByte(4); it.writeUTF("Time"); it.writeLong(42)
            it.writeByte(8); it.writeUTF("LevelName"); it.writeUTF("世界")
            it.writeByte(9); it.writeUTF("EmptyList"); it.writeByte(0); it.writeInt(0)
            it.writeByte(0); it.writeByte(0)
        }
    }.toByteArray()

    @Test fun completeWorldMetadataParses() = assertDoesNotThrow { validateWorldSave(compressed(valid())) }

    @Test fun gzipHeaderAndNonemptyPayloadAreNotEnough() {
        assertThrows(Exception::class.java) { validateWorldSave(compressed(byteArrayOf(10, 0))) }
    }

    @Test fun truncatedCompoundCannotPass() {
        assertThrows(Exception::class.java) { validateWorldSave(compressed(valid().dropLast(1).toByteArray())) }
    }

    @Test fun missingWorldDataAndTrailingJunkCannotPass() {
        assertThrows(Exception::class.java) { validateWorldSave(compressed(byteArrayOf(10, 0, 0, 0))) }
        assertThrows(Exception::class.java) { validateWorldSave(compressed(valid() + byteArrayOf(1))) }
    }
}
