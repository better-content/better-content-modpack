package com.bettercontent.tests.suite.repositories

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/** Synthetic process-tree regression fixtures; these tests have NOT been run or authorized to run. */
class RepositoryCommandTest {
    @TempDir lateinit var root: Path

    private fun script(text: String): Path = root.resolve("fixture.sh").also { path ->
        Files.writeString(path, "#!/usr/bin/env bash\nset -eu\n$text\n")
        assertTrue(path.toFile().setExecutable(true))
    }

    @Test fun `timeout owns and cleans launcher plus spawned child with distinct raw streams`() {
        val executable = script("sleep 30 &\necho child-started\necho child-stderr >&2\nwait")
        val receipt = RepositoryCommand.execute(listOf(executable.toString()), root, emptyMap(),
            root.resolve("stdout.log"), root.resolve("stderr.log"), Duration.ofMillis(300))
        assertTrue(receipt.timedOut)
        assertEquals(-1, receipt.exitCode)
        assertTrue(receipt.ownedProcesses.size >= 2, "launcher and child must both have start identities")
        assertTrue(receipt.survivingPids.isEmpty(), "timeout must not orphan source task descendants")
        assertTrue(Files.readString(root.resolve("stdout.log")).contains("child-started"))
        assertTrue(Files.readString(root.resolve("stderr.log")).contains("child-stderr"))
    }

    @Test fun `success exit with still-live child must be observable as stray and cleaned`() {
        val executable = script("sleep 30 &\necho launched\nsleep .3\nexit 0")
        val receipt = RepositoryCommand.execute(listOf(executable.toString()), root, emptyMap(),
            root.resolve("success-out.log"), root.resolve("success-err.log"), Duration.ofSeconds(5))
        assertEquals(0, receipt.exitCode)
        assertFalse(receipt.strayDescendantPids.isEmpty(), "passing exit cannot conceal child Java/Gradle tasks")
        assertTrue(receipt.survivingPids.isEmpty(), "stray child still needs cleanup before blocked receipt")
    }
}
