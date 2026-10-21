package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

@Tag("fast")
class StructureLootBalanceTest {
    @Test
    fun `independent upstream table reader preserves priority and fail closed integrity checks`() {
        val root = Path.of(System.getProperty("bc.repo.root"))
        val work = Files.createDirectories(root.resolve("build/test-work/structure-loot-input-controls"))
        val evidence = Files.createTempDirectory(work, "reader-")
        val output = BoundedCommand.capture(root, listOf(
            "python3", "-B", root.resolve("src/test/resources/structure-loot-input-controls.py").toString(),
            root.toString(), evidence.toString(),
        ), Duration.ofSeconds(30))
        assertTrue(output.contains("Structure loot input-reader controls: 8 passed"))
    }

    @Test
    fun `reviewed pinned table inventory preserves protected pools and bounded quantities`() {
        val root = Path.of(System.getProperty("bc.repo.root"))
        val output = BoundedCommand.capture(root, listOf(
            "python3", "-B", root.resolve("src/test/resources/structure-loot-contract.py").toString(),
            root.toString(),
        ), Duration.ofMinutes(10))
        assertTrue(output.contains("Structure loot ownership: reviewed pinned candidate inventory"))
    }
}
