package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** Pure synthetic wire regression only; does not run a test or world without approval. */
class FrozenNaturalTargetTest {
    @Test fun frozenLedgerWireRoundTripsAndRejectsUnknownNestedClaim() = verifyWire(100L)
    @Test fun frozenLedgerWireRoundTripsLargeIntegralTicks() = verifyWire(Int.MAX_VALUE.toLong() + 1L)

    private fun verifyWire(tick: Long) {
        val directory = Files.createTempDirectory(Path.of("/home/dev/.tmp"), "frozen-target-")
        try {
            val actor = UUID.randomUUID().toString()
            val ctx = WitnessContext("campaign-${UUID.randomUUID()}", UUID.randomUUID().toString(),
                "epoch-00001", "epoch-${UUID.randomUUID()}", "branch", "primitive.harvest", "trial-1",
                "server", 1, 2, actor, "target.observe.loaded_v1", "qualification")
            val input = CheckpointRef("origin", BoundFile(directory.resolve("origin.json").toString(), "a".repeat(64)))
            val profile = BoundFile(directory.resolve("reviewed.json").toString(), "b".repeat(64))
            val selector = ReviewedTargetSelector("bc.playtest_target_selector.v2", "natural.stone",
                "c".repeat(64), "minecraft", "target.observe.loaded_v1", "minecraft:stone", "origin",
                TargetBounds("minecraft:overworld", 0, 64, 0, 64, 0, 64, 5),
                TargetSource.NATURAL_UNMODIFIED_BLOCK, "loaded_block_nearest_center_then_xyz_v1",
                TargetSelectionOrder.NEAREST_THEN_POSITION, 30, 1, profile,
                0, "minecraft:cobblestone", "north", true)
            val now = Instant.now()
            val observation = TargetObservation("bc.playtest_target_observation.v1", ctx, selector.id,
                1, selector.source, selector.provider, selector.resourceId, selector.bounds.dimension,
                0, 64, 0, null, 1.0, tick, 1L, "d".repeat(64), "e".repeat(64), now)
            val attempt = TargetSelectionAttempt(1, TargetAttemptOutcome.ACCEPTED, observation,
                "reviewed_supported_path_controller_acceptance", "d".repeat(64), now, 20L)
            val ledger = TargetSelectionLedger("bc.playtest_target_attempts.v1", selector,
                input, now, 10L, listOf(attempt), "f".repeat(64))
            val frozen = FrozenNaturalTarget("bc.playtest_frozen_natural_target.v1",
                BoundFile(directory.resolve("loaded.json").toString(), "1".repeat(64)), profile,
                input, BoundFile(directory.resolve("scope.json").toString(), "2".repeat(64)),
                "3".repeat(64), ledger, ledger.bound(),
                ReviewedScopedOutputSlot("bc.playtest_reviewed_scoped_output_slot.v2", selector.id,
                    selector.catalogueSha256, input, 0, "minecraft:cobblestone"))
            val path = directory.resolve("target.json")
            Files.writeString(path, suiteMapper.writeValueAsString(frozenNaturalWire(frozen)))
            assertEquals(frozen, readFrozenNaturalTarget(BoundFile(path.toString(), Hashes.sha256(path))))
            val mutated = suiteMapper.readTree(path.toFile())
            (mutated.path("ledger").path("attempts")[0] as com.fasterxml.jackson.databind.node.ObjectNode)
                .put("native_provenance_proven", true)
            Files.writeString(path, suiteMapper.writeValueAsString(mutated))
            assertThrows(Exception::class.java) {
                readFrozenNaturalTarget(BoundFile(path.toString(), Hashes.sha256(path)))
            }
            val changes: List<(com.fasterxml.jackson.databind.node.ObjectNode) -> Unit> = listOf(
                { it.put("tick", tick.toDouble()) },
                { it.put("tick", tick.toString()) },
                { it.put("tick", "not-a-number") })
            changes.forEach { change ->
                val wrongKind = suiteMapper.readTree(suiteMapper.writeValueAsBytes(frozenNaturalWire(frozen)))
                change(wrongKind.path("ledger").path("attempts")[0].path("candidate") as
                    com.fasterxml.jackson.databind.node.ObjectNode)
                Files.writeString(path, suiteMapper.writeValueAsString(wrongKind))
                assertThrows(Exception::class.java) {
                    readFrozenNaturalTarget(BoundFile(path.toString(), Hashes.sha256(path)))
                }
            }
        } finally {
            Files.list(directory).use { it.forEach(Files::delete) }
            Files.delete(directory)
        }
    }
}
