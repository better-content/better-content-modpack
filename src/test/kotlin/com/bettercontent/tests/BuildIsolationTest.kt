package com.bettercontent.tests

import com.bettercontent.tests.release.BuildIsolationPool
import com.bettercontent.tests.release.ParallelBuildScheduler
import com.bettercontent.tests.release.withBuildResourceLock
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.io.CleanupMode
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Tag("fast")
class BuildIsolationTest {
    private fun file(root: Path, relative: String, content: String): Path = root.resolve(relative).also {
        Files.createDirectories(it.parent); Files.writeString(it, content)
    }

    @Test fun privateHomesActuallyOverlapWithoutMutableAliases(@TempDir(cleanup = CleanupMode.NEVER) root: Path) {
        val shared = root.resolve("shared")
        val seed = file(shared, "caches/modules-2/files-2.1/group/a.jar", "original")
        file(shared, "caches/forge_gradle/deobf.jar", "MUST NOT COPY")
        file(shared, "gradle.properties", "MUST NOT COPY SECRETS")
        val pool = BuildIsolationPool(root.resolve("pack"), root.resolve("run"), 2, shared)
        val together = CountDownLatch(2)
        val homes = java.util.concurrent.ConcurrentHashMap.newKeySet<Path>()
        val result = ParallelBuildScheduler(2).run(mapOf("a" to emptyList(), "b" to emptyList())) { name, _ ->
            pool.withWorker(root.resolve("repos/$name")) { context ->
                val worker = requireNotNull(context)
                assertTrue(homes.add(worker.home))
                assertEquals(worker.home.toString(), worker.environment()["HOME"])
                assertTrue(worker.environment().getValue("JAVA_TOOL_OPTIONS").contains("-Duser.home=${worker.home}"))
                val copy = worker.gradleHome.resolve("caches/modules-2/files-2.1/group/a.jar")
                assertFalse(Files.isSameFile(seed, copy))
                assertEquals("original", Files.readString(copy))
                assertFalse(Files.exists(worker.gradleHome.resolve("caches/forge_gradle")))
                assertFalse(Files.exists(worker.gradleHome.resolve("gradle.properties")))
                Files.writeString(copy, name)
                together.countDown()
                check(together.await(5, TimeUnit.SECONDS)) { "private cache actions serialized" }
                assertEquals(name, Files.readString(copy))
                name
            }
        }
        assertTrue(result.failures.isEmpty(), result.failures.toString())
        assertEquals("original", Files.readString(seed))
        assertTrue(Files.exists(root.resolve("run/seed-identity.tsv")))
        assertTrue(Files.exists(root.resolve("run/ready")))
    }

    @Test fun sameResourceNeverOverlapsAndFailureReleasesMutex(@TempDir(cleanup = CleanupMode.NEVER) root: Path) {
        val active = AtomicInteger()
        val lock = root.resolve("workspace.lock")
        val result = ParallelBuildScheduler(2).run(mapOf("a" to emptyList(), "b" to emptyList())) { name, _ ->
            withBuildResourceLock(lock) {
                assertEquals(1, active.incrementAndGet())
                try { Thread.sleep(30); name } finally { active.decrementAndGet() }
            }
        }
        assertTrue(result.failures.isEmpty())
        assertThrows(IllegalStateException::class.java) { withBuildResourceLock(lock) { error("inert failure") } }
        assertEquals("released", withBuildResourceLock(lock) { "released" })
    }

    @Test fun symlinksAndExpiredCopyBudgetsFailRetainingPartialOutputs(@TempDir(cleanup = CleanupMode.NEVER) root: Path) {
        val source = root.resolve("source")
        file(source, "file.jar", "bytes")
        Files.createSymbolicLink(source.resolve("alias.jar"), source.resolve("file.jar"))
        assertThrows(IllegalArgumentException::class.java) {
            BuildIsolationPool.copyTree(source, root.resolve("partial"), System.nanoTime() + Duration.ofSeconds(5).toNanos())
        }
        assertTrue(Files.isDirectory(root.resolve("partial")))
        assertThrows(IllegalStateException::class.java) {
            BuildIsolationPool.copyTree(source, root.resolve("expired"), System.nanoTime() - 1)
        }
        assertTrue(Files.isDirectory(root.resolve("expired")))
    }

    @Test fun absoluteLegacyCacheScriptsKeepGlobalSerialization(@TempDir(cleanup = CleanupMode.NEVER) root: Path) {
        val repository = root.resolve("repo")
        file(repository, "scripts/proof.py", "Path('/home/dev/.gradle/caches/forge_gradle')")
        assertTrue(BuildIsolationPool.requiresSharedCache(repository))
        val pool = BuildIsolationPool(root.resolve("pack"), root.resolve("unused-run"), 2, root.resolve("empty"))
        pool.withWorker(repository) { assertNull(it) }
        assertFalse(Files.exists(root.resolve("unused-run")))
    }

    @Test fun actualChildJavaAndPythonResolveThePrivateHome(@TempDir(cleanup = CleanupMode.NEVER) root: Path) {
        val pool = BuildIsolationPool(root.resolve("pack"), root.resolve("run"), 1, root.resolve("empty"))
        pool.withWorker(root.resolve("repo")) { context ->
            val worker = requireNotNull(context)
            val java = Path.of(System.getProperty("java.home"), "bin/java").toString()
            val output = root.resolve("java-home.log")
            val builder = ProcessBuilder(java, "-XshowSettings:properties", "-version")
                .redirectErrorStream(true).redirectOutput(output.toFile())
            builder.environment().putAll(worker.environment())
            val child = builder.start()
            try {
                assertTrue(child.waitFor(10, TimeUnit.SECONDS))
                assertEquals(0, child.exitValue())
                val text = Files.readString(output)
                assertTrue(text.contains("user.home = ${worker.home}"), text)
                assertTrue(text.contains("java.io.tmpdir = ${worker.temporary}"), text)
            } finally { if (child.isAlive) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS) } }
            val pythonOutput = root.resolve("python-home.log")
            val python = ProcessBuilder("python3", "-c", "from pathlib import Path; print(Path.home())")
                .redirectErrorStream(true).redirectOutput(pythonOutput.toFile())
            python.environment().putAll(worker.environment())
            val process = python.start()
            try {
                assertTrue(process.waitFor(10, TimeUnit.SECONDS))
                assertEquals(0, process.exitValue())
                assertEquals(worker.home.toString(), Files.readString(pythonOutput).trim())
            } finally { if (process.isAlive) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS) } }
        }
    }

    @Test fun resourceMutexIsAlsoPosixNotJustJvmLocal(@TempDir(cleanup = CleanupMode.NEVER) root: Path) {
        val lock = root.resolve("resource.lock")
        withBuildResourceLock(lock) {
            val output = root.resolve("posix-lock.log")
            val process = ProcessBuilder("python3", "-c", """
                import fcntl, sys
                with open(sys.argv[1], 'a') as file:
                    try:
                        fcntl.lockf(file, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    except BlockingIOError:
                        print('locked')
                    else:
                        raise AssertionError('resource not locked cross-process')
            """.trimIndent(), lock.toString()).redirectErrorStream(true).redirectOutput(output.toFile()).start()
            try {
                assertTrue(process.waitFor(10, TimeUnit.SECONDS))
                assertEquals(0, process.exitValue(), Files.readString(output))
                assertEquals("locked", Files.readString(output).trim())
            } finally { if (process.isAlive) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS) } }
        }
    }

    @Test fun workerReturnsAfterFailureAndReusesHomeSerially(@TempDir(cleanup = CleanupMode.NEVER) root: Path) {
        val pool = BuildIsolationPool(root.resolve("pack"), root.resolve("run"), 1, root.resolve("empty"))
        var home: Path? = null
        assertThrows(IllegalStateException::class.java) {
            pool.withWorker(root.resolve("repo")) { context -> home = context!!.home; error("fixture") }
        }
        pool.withWorker(root.resolve("repo")) { assertEquals(home, it!!.home) }
    }
}
