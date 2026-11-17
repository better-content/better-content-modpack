package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class DiagnosticInventoryTest {
    @Test fun onlyReviewedDomainsMayBeIncompleteElsewhereButRawSelectedDomainMustBeBound() {
        val native = Files.createTempDirectory(Path.of("/home/dev/.tmp"), "scoped-inventory-")
        try {
            val folder = Files.createDirectory(native.resolve("inventory"))
            val file = folder.resolve("items.jsonl")
            Files.writeString(file, "minecraft:stone\n")
            val manifest = suiteMapper.readTree("""{"cases":[{"id":"case.items","required_inventory_domains":["items"]}]}""")
            fun observed(complete: Boolean = true, hash: String = Hashes.sha256(file)) = suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(mapOf(
                "seed" to 424242L, "player_count" to 0, "player_uuids" to emptyList<String>(),
                "complete" to false, "gaps" to listOf("unrelated_client_resources"),
                "complete_by_domain" to mapOf("items" to complete, "client_resources" to false),
                "files" to listOf(mapOf("domain" to "items", "complete" to complete, "path" to "inventory/items.jsonl",
                    "sha256" to hash, "bytes" to Files.size(file)))))
            verifyLiveInventory(manifest, observed(), true, ExecutionPurpose.DIAGNOSTIC, setOf("case.items"), native)
            assertThrows(IllegalArgumentException::class.java) {
                verifyLiveInventory(manifest, observed(false), true, ExecutionPurpose.DIAGNOSTIC, setOf("case.items"), native)
            }
            assertThrows(IllegalArgumentException::class.java) {
                verifyLiveInventory(manifest, observed(hash = "a".repeat(64)), true,
                    ExecutionPurpose.DIAGNOSTIC, setOf("case.items"), native)
            }
            assertThrows(IllegalArgumentException::class.java) {
                verifyLiveInventory(manifest, observed(), true, ExecutionPurpose.QUALIFICATION, setOf("case.items"), native)
            }
        } finally {
            Files.walk(native).sorted(java.util.Comparator.reverseOrder()).use { stream ->
                stream.forEach { path -> Files.deleteIfExists(path) }
            }
        }
    }
}
