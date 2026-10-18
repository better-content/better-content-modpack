package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

@Tag("fast")
class RunnerMutexTest {
    @Test fun `direct transaction holds a kernel mutex until its action returns`(@TempDir state: Path) {
        val root = Path.of(System.getProperty("bc.repo.root"))
        val probe = "import fcntl,sys; f=open(sys.argv[1],'w'); fcntl.lockf(f,fcntl.LOCK_EX|fcntl.LOCK_NB)"
        RunnerMutex.acquire(state) {
            assertTrue(Files.isRegularFile(state.resolve("owner.json")))
            assertThrows(IllegalArgumentException::class.java) { RunnerMutex.acquire(state) { fail("contending action ran") } }
            assertThrows(IllegalStateException::class.java) {
                BoundedCommand.capture(root, listOf("python3", "-c", probe, state.resolve("runner.lock").toString()), Duration.ofSeconds(10))
            }
        }
        assertFalse(Files.exists(state.resolve("owner.json")))
        assertEquals("", BoundedCommand.capture(root, listOf("python3", "-c", probe, state.resolve("runner.lock").toString()), Duration.ofSeconds(10)))
    }

    @Test fun `action failure releases direct lock but stale metadata blocks preparation`(@TempDir state: Path) {
        assertThrows(IllegalStateException::class.java) { RunnerMutex.acquire(state) { error("synthetic failure") } }
        assertFalse(Files.exists(state.resolve("owner.json")))
        assertEquals(42, RunnerMutex.acquire(state) { 42 })
        Files.writeString(state.resolve("owner.json"), "{}")
        assertThrows(IllegalArgumentException::class.java) { RunnerMutex.acquire(state) { fail("stale owner was ignored") } }
    }

    @Test fun `direct state ancestor symlink cannot redirect mutex`(@TempDir root: Path) {
        val actual = Files.createDirectory(root.resolve("actual"))
        Files.createSymbolicLink(root.resolve("alias"), actual)
        assertThrows(IllegalArgumentException::class.java) { RunnerMutex.acquire(root.resolve("alias/state")) { fail("unsafe state accepted") } }
    }
}
