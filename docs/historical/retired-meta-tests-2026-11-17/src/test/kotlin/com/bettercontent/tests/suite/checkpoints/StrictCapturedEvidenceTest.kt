package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Source-only parser regressions; NOT executed without human approval. */
class StrictCapturedEvidenceTest {
    @Test fun oneBoundedCapturedJsonRejectsMalformedTrailingDuplicateAndSymlinks() {
        val directory = Files.createTempDirectory(Path.of("/home/dev/.tmp"), "strict-captured-")
        try {
            val source = directory.resolve("row.json")
            fun capture(bytes: ByteArray): ByteArray {
                Files.write(source, bytes)
                return capturedEvidenceBytes(BoundFile(source.toString(), Hashes.sha256(source)), 1024)
            }
            assertEquals(1, strictCapturedJson(capture("{\"x\":1}".toByteArray())).path("x").asInt())
            listOf("{\"x\":1}{\"x\":2}", "{\"x\":1,\"x\":2}").forEach { text ->
                assertThrows(Exception::class.java) { strictCapturedJson(capture(text.toByteArray())) }
            }
            assertThrows(Exception::class.java) {
                strictCapturedJson(capture(byteArrayOf(0x7b, 0x22, 0x78, 0x22, 0x3a, 0xc3.toByte(), 0x28, 0x7d)))
            }
            val link = directory.resolve("link.json")
            Files.createSymbolicLink(link, source)
            assertThrows(Exception::class.java) {
                capturedEvidenceBytes(BoundFile(link.toString(), Hashes.sha256(source)), 1024)
            }
        } finally {
            Files.list(directory).use { it.forEach(Files::delete) }
            Files.delete(directory)
        }
    }

    @Test fun journalRejectsIncompleteLastLineAndDuplicateRowKeys() {
        val directory = Files.createTempDirectory(Path.of("/home/dev/.tmp"), "strict-journal-")
        try {
            val source = directory.resolve("raw.jsonl")
            fun rows(text: String) = Files.writeString(source, text).let {
                strictCapturedLines(BoundFile(source.toString(), Hashes.sha256(source)), 1024, 8, 128)
            }
            assertEquals(2, rows("{\"n\":1}\n{\"n\":2}\n").size)
            assertThrows(Exception::class.java) { rows("{\"n\":1}\n{\"n\":2}") }
            assertThrows(Exception::class.java) { rows("{\"n\":1,\"n\":2}\n") }
        } finally {
            Files.deleteIfExists(directory.resolve("raw.jsonl"))
            Files.delete(directory)
        }
    }
}
