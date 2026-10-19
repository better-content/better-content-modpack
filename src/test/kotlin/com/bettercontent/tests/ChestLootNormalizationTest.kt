package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Duration

@Tag("fast")
class ChestLootNormalizationTest {
    @Test
    fun `native loot quantities survive filtering without blanket normalization or compensation`() {
        val root = Path.of(System.getProperty("bc.repo.root"))
        val output = BoundedCommand.capture(root, listOf(
            "node", root.resolve("src/test/resources/chest-loot-normalization-contract.cjs").toString(),
            root.toString(),
        ), Duration.ofSeconds(30))
        assertTrue(output.contains("Chest loot ownership: native quantities"))
    }
}
