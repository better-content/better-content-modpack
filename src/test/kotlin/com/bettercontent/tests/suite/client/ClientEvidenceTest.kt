package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class ClientEvidenceTest {
    private val emiSha = "b".repeat(64)
    private fun fixture(): ByteArray = (suiteMapper.writeValueAsString(mapOf(
        "schema" to "bc.playtest_emi_public_index.v2", "emi_jar_sha256" to emiSha,
        "index_ids" to listOf("minecraft:coal"),
        "recipes" to listOf(mapOf("identity_kind" to "public_recipe_id", "recipe_id" to "emi:coal",
            "provider_class" to "dev.emi.emi.recipe.EmiFuelRecipe", "category_id" to "emi:fuel",
            "output_ids" to emptyList<String>(), "input_alternative_ids" to emptyList<String>(), "projection_sha256" to null)))) + "\n").toByteArray()
    private fun envelope(file: Path, bytes: ByteArray) = suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(mapOf(
        "file" to file.toString(), "file_sha256" to MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }, "file_bytes" to bytes.size, "index_count" to 1, "recipe_count" to 1))
    private fun inMailbox(action: (Path, Path, ByteArray) -> Unit) {
        val mailbox = Files.createTempDirectory(Path.of("/home/dev/.tmp"), "emi-capture-")
        try {
            val file = mailbox.resolve("emi-public-000000000001.json")
            val bytes = fixture(); Files.write(file, bytes)
            action(mailbox, file, bytes)
        } finally {
            Files.list(mailbox).use { stream -> stream.forEach { path -> Files.deleteIfExists(path) } }
            Files.deleteIfExists(mailbox)
        }
    }

    @Test fun validFuelHasEmptyOutputsWithoutSilentOmission() = inMailbox { mailbox, file, bytes ->
        assertEquals(1, verifyCapturedEmiPublicIndex(mailbox, 1, envelope(file, bytes), emiSha).path("recipes").size())
    }

    @Test fun trailingJsonAndTypeDriftRejectEvenWithCorrectEnvelopeHash() = inMailbox { mailbox, file, bytes ->
        listOf(bytes + "{}".toByteArray(), bytes + "null".toByteArray(),
            bytes.toString(Charsets.UTF_8).replace("\"recipe_id\":\"emi:coal\"", "\"recipe_id\":7")
                .toByteArray(Charsets.UTF_8)).forEach { changed ->
            Files.write(file, changed)
            assertThrows(Exception::class.java) { verifyCapturedEmiPublicIndex(mailbox, 1, envelope(file, changed), emiSha) }
        }
    }

    @Test fun mismatchedPathHashSizeSymlinkAndOversizeReject() = inMailbox { mailbox, file, bytes ->
        val valid = envelope(file, bytes).deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        assertThrows(Exception::class.java) { verifyCapturedEmiPublicIndex(mailbox, 2, valid, emiSha) }
        assertThrows(Exception::class.java) { verifyCapturedEmiPublicIndex(mailbox, 1, valid.deepCopy().put("file_sha256", "0".repeat(64)), emiSha) }
        assertThrows(Exception::class.java) { verifyCapturedEmiPublicIndex(mailbox, 1, valid.deepCopy().put("file_bytes", bytes.size + 1), emiSha) }
        val link = mailbox.resolve("linked.json"); Files.createSymbolicLink(link, file)
        assertThrows(Exception::class.java) { verifyCapturedEmiPublicIndex(mailbox, 1, valid.deepCopy().put("file", link.toString()), emiSha) }
        java.io.RandomAccessFile(file.toFile(), "rw").use { it.setLength(32_000_001) }
        assertThrows(Exception::class.java) { verifyCapturedEmiPublicIndex(mailbox, 1, envelope(file, bytes), emiSha) }
    }
}
