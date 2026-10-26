package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@Tag("fast")
class GeneratedEvidenceIgnoreTest {
    @Test
    fun retainedComponentVerificationDoesNotEnterGitSourceInventory(@TempDir fixture: Path) {
        val root = Path.of(System.getProperty("bc.repo.root"))
        Files.copy(root.resolve(".gitignore"), fixture.resolve(".gitignore"))
        val evidence = fixture.resolve("generated/component-verification/attempt/native/latest.log")
        Files.createDirectories(evidence.parent)
        Files.writeString(evidence, "retained native evidence, not authored source")
        Files.createDirectories(fixture.resolve("src"))
        Files.writeString(fixture.resolve("src/input.txt"), "authored source")
        BoundedCommand.capture(fixture, listOf("git", "init", "--quiet"), Duration.ofSeconds(30))
        val inventory = BoundedCommand.capture(fixture,
            listOf("git", "ls-files", "--others", "--exclude-standard", "-z"), Duration.ofSeconds(30))
            .split('\u0000').filter(String::isNotBlank).toSet()
        assertEquals(setOf(".gitignore", "src/input.txt"), inventory)
        assertTrue(Files.isRegularFile(evidence), "Inventory exclusion must preserve retained evidence")
    }
}
