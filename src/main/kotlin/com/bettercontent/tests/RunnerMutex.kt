package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

/** Kernel ownership lasts for the complete direct preparation transaction, not a token check. */
object RunnerMutex {
    private class Busy(message: String) : IllegalArgumentException(message)
    private val localOwners = java.util.concurrent.ConcurrentHashMap.newKeySet<Path>()
    fun verifyInherited(root: Path) {
        BoundedCommand.capture(root, listOf("python3", "-B", root.resolve("scripts/runner_lock.py").toString()), java.time.Duration.ofSeconds(30))
    }

    fun <T> withLock(root: Path, action: () -> T): T {
        if (!System.getenv("BC_PACK_TEST_LOCK_TOKEN").isNullOrBlank()) {
            try { verifyInherited(root) } catch (failure: IllegalStateException) {
                System.err.println(failure.message)
                kotlin.system.exitProcess(75)
            }
            return action()
        }
        val state = System.getenv("BC_PACK_TEST_STATE_ROOT")?.let(Path::of)
            ?: Path.of(System.getProperty("user.home"), ".local/share/worklane/pack-tests")
        return try { acquire(state, action) } catch (failure: Busy) {
            System.err.println(failure.message)
            kotlin.system.exitProcess(75)
        }
    }

    internal fun <T> acquire(state: Path, action: () -> T): T {
        val key = state.toAbsolutePath().normalize()
        // POSIX record locks are process-owned: closing a second descriptor for the
        // inode can release the first channel's lock. Reject local contention before open.
        if (!localOwners.add(key)) throw Busy("runner mutex already held in this JVM: $key")
        try { return acquireKernel(state, action) } finally { localOwners.remove(key) }
    }

    private fun <T> acquireKernel(state: Path, action: () -> T): T {
        CandidateBinding.requireOrdinary(state)
        Files.createDirectories(state)
        val owner = state.resolve("owner.json")
        val lockPath = state.resolve("runner.lock")
        CandidateBinding.requireOrdinary(owner)
        CandidateBinding.requireOrdinary(lockPath)
        FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            val lock = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
            if (lock == null) throw Busy("runner mutex busy: ${if (Files.isRegularFile(owner)) Files.readString(owner) else owner}")
            lock.use {
                if (Files.exists(owner)) throw Busy("prior runner cleanup metadata requires reconciliation: $owner")
                val token = UUID.randomUUID().toString()
                val mapper = jacksonObjectMapper()
                Files.writeString(owner, mapper.writeValueAsString(mapOf("schema" to "bc.pack_test_owner.v1", "token" to token,
                    "pid" to ProcessHandle.current().pid(), "kind" to "release", "selector" to "direct-preparation")), StandardOpenOption.CREATE_NEW)
                try { return action() } finally {
                    if (mapper.readTree(owner.toFile()).path("token").asText() == token) Files.delete(owner)
                }
            }
        }
    }
}
