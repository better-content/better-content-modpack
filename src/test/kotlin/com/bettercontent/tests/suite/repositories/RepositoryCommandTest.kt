package com.bettercontent.tests.suite.repositories

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

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

    @Test fun `live unresolved birth snapshots stay pending while known reused identities do not`() {
        val birth = Instant.parse("2026-01-01T00:00:00Z")
        assertTrue(RepositoryCommand.needsNaturalExitWait(true, null, birth))
        assertTrue(RepositoryCommand.needsNaturalExitWait(true, birth, null))
        assertTrue(RepositoryCommand.needsNaturalExitWait(true, null, null))
        assertTrue(RepositoryCommand.needsNaturalExitWait(true, birth, birth))
        assertFalse(RepositoryCommand.needsNaturalExitWait(true, birth, birth.plusSeconds(1)))
        assertFalse(RepositoryCommand.needsNaturalExitWait(false, null, null))
        assertFalse(RepositoryCommand.needsNaturalExitWait(false, birth, birth))
    }

    @Test fun `launcher exit waits for owned child natural completion inside original deadline without TERM`() {
        Files.writeString(root.resolve("child.sh"), """
            #!/usr/bin/env bash
            set -eu
            trap 'printf terminated > child-terminated; exit 97' TERM
            printf '%s\n' "${'$'}BASHPID" > child.pid
            sleep .8
            printf natural > child-finished
        """.trimIndent() + "\n")
        val executable = script("bash child.sh &\nsleep .3\nexit 0")
        val receipt = RepositoryCommand.execute(listOf(executable.toString()), root, emptyMap(),
            root.resolve("natural-out.log"), root.resolve("natural-err.log"), Duration.ofSeconds(5))
        assertEquals(0, receipt.exitCode)
        assertFalse(receipt.timedOut)
        assertTrue(receipt.cleanupComplete)
        assertEquals(null, receipt.commandError)
        assertTrue(receipt.strayDescendantPids.isEmpty())
        assertTrue(receipt.survivingPids.isEmpty())
        assertEquals("natural", Files.readString(root.resolve("child-finished")))
        assertFalse(Files.exists(root.resolve("child-terminated")), "cleanup must not signal the naturally finishing child")
        val childPid = Files.readString(root.resolve("child.pid")).trim().toLong()
        val birth = Instant.parse(receipt.ownedProcesses.single { it.pid == childPid }.startedAt)
        assertFalse(ProcessHandle.of(childPid).map {
            it.isAlive && it.info().startInstant().orElse(Instant.MIN) == birth
        }.orElse(false), "the exact observed child birth must have exited")
    }

    @Test fun `success exit with still-live child must be observable as stray and cleaned`() {
        val executable = script("sleep 30 &\necho launched\nsleep .3\nexit 0")
        val receipt = RepositoryCommand.execute(listOf(executable.toString()), root, emptyMap(),
            root.resolve("success-out.log"), root.resolve("success-err.log"), Duration.ofSeconds(5))
        assertEquals(0, receipt.exitCode)
        assertTrue(receipt.timedOut, "the original deadline must cover the still-live descendant")
        assertEquals("verification command descendants did not exit within deadline", receipt.commandError)
        assertFalse(receipt.strayDescendantPids.isEmpty(), "passing exit cannot conceal child Java/Gradle tasks")
        assertTrue(receipt.survivingPids.isEmpty(), "stray child still needs cleanup before blocked receipt")
    }
}
