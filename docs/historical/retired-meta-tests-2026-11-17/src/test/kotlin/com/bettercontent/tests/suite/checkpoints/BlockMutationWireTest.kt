package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.security.MessageDigest

/** Synthetic only; tests and fixture generators remain UNRUN without separate human approval. */
class BlockMutationWireTest {
    private val at = ScopedBlockPosition("minecraft:overworld", 0, 64, 0)
    private val scope = "scope-12345678-1234-1234-1234-123456789abc"
    private val initial = mapOf(at to ("minecraft:stone" to emptyMap<String, String>()))
    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    private fun row(index: Int, old: String, new: String, prior: String, scopeId: String = scope,
        schema: String = "bc.playtest_block_mutation_event.v2",
        path: String = "PalettedContainer.getAndSet_private_index_return",
        nanos: Long = 1234): ByteArray =
        (suiteMapper.writeValueAsString(mapOf("schema" to schema,
            "scope_id" to scopeId, "index" to index, "tick" to 45,
            "monotonic_nanos" to nanos, "dimension" to at.dimension, "x" to at.x, "y" to at.y, "z" to at.z,
            "observed_path" to path,
            "old_state" to mapOf("id" to old, "properties" to emptyMap<String, String>()),
            "new_state" to mapOf("id" to new, "properties" to emptyMap<String, String>()),
            "prior_prefix_sha256" to prior)) + "\n").toByteArray(Charsets.UTF_8)

    @Test fun `real two transition restoration remains modified instead of being erased by final equality`() {
        val first = row(1, "minecraft:stone", "minecraft:air", sha(byteArrayOf()))
        val second = row(2, "minecraft:air", "minecraft:stone", sha(first),
            path = "PalettedContainer.read_full_replacement")
        val full = first + second
        val paths = mutableListOf<String>()
        assertEquals(listOf(at, at), decodeScopedMutationEvents(full, scope, initial, 2, sha(full), paths))
        assertEquals(listOf("PalettedContainer.getAndSet_private_index_return",
            "PalettedContainer.read_full_replacement"), paths)
        val negative = row(1, "minecraft:stone", "minecraft:air", sha(byteArrayOf()), nanos = -100)
        val tied = row(2, "minecraft:air", "minecraft:stone", sha(negative), nanos = -100)
        assertEquals(listOf(at, at), decodeScopedMutationEvents(negative + tied, scope, initial,
            2, sha(negative + tied))) // System.nanoTime may be negative and consecutive calls may tie
        assertEquals(emptyList<ScopedBlockPosition>(),
            decodeScopedMutationEvents(byteArrayOf(), scope, initial, 0, sha(byteArrayOf())))
    }

    @Test fun `forged prior hash old state index scope count and incomplete LF fail closed`() {
        val first = row(1, "minecraft:stone", "minecraft:air", sha(byteArrayOf()))
        val tampered = listOf(
            row(1, "minecraft:stone", "minecraft:air", "0".repeat(64)),
            row(1, "minecraft:dirt", "minecraft:air", sha(byteArrayOf())),
            row(2, "minecraft:stone", "minecraft:air", sha(byteArrayOf())),
            row(1, "minecraft:stone", "minecraft:air", sha(byteArrayOf()), scopeId = "scope-other"),
            row(1, "minecraft:stone", "minecraft:air", sha(byteArrayOf()),
                schema = "bc.playtest_block_mutation_event.v1"),
            first.dropLast(1).toByteArray())
        tampered.forEach { bytes -> assertThrows(IllegalArgumentException::class.java) {
            decodeScopedMutationEvents(bytes, scope, initial, 1, sha(bytes))
        } }
        assertThrows(IllegalArgumentException::class.java) {
            decodeScopedMutationEvents(first, scope, initial, 2, sha(first))
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodeScopedMutationEvents(first, scope, initial, 1, sha(byteArrayOf()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodeScopedMutationEvents(first + row(2, "minecraft:air", "minecraft:stone", "0".repeat(64)),
                scope, initial, 2, sha(first + row(2, "minecraft:air", "minecraft:stone", "0".repeat(64))))
        }
    }
}
