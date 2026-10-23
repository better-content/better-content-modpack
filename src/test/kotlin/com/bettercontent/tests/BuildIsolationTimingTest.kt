package com.bettercontent.tests

import com.bettercontent.tests.release.BuildIsolationContext
import com.bettercontent.tests.release.BuildIsolationTiming
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

@Tag("fast")
class BuildIsolationTimingTest {
    @Test fun factualHashesAreOrderStableButSensitiveToConditionsAndNeverDumpFlags() {
        val secretFlag = "-Dexample.token=synthetic-not-a-real-secret"
        val first = BuildIsolationTiming.digest(linkedMapOf("flags" to secretFlag, "arch" to "aarch64", "memory" to "8GiB"))
        val reordered = BuildIsolationTiming.digest(linkedMapOf("memory" to "8GiB", "arch" to "aarch64", "flags" to secretFlag))
        assertEquals(first, reordered)
        assertTrue(first.matches(Regex("[a-f0-9]{64}")))
        assertFalse(first.contains(secretFlag))
        assertNotEquals(first, BuildIsolationTiming.digest(mapOf("flags" to secretFlag, "arch" to "x86_64", "memory" to "8GiB")))
    }

    @Test fun cacheConditionExcludesRunPathsAndIncludesSeedAndSerialHistory(@TempDir(cleanup = CleanupMode.NEVER) root: Path) {
        val a = BuildIsolationContext(root.resolve("run-a/worker-0"), "abc-seed")
        val b = BuildIsolationContext(root.resolve("run-b/worker-1"), "abc-seed")
        Files.createDirectories(a.directory); Files.createDirectories(b.directory)
        val fresh = a.cacheObservation()
        assertEquals(fresh.id, b.cacheObservation().id)
        assertEquals("fresh-private-generated-cache", fresh.facts["generated-state"])
        assertEquals("UNMEASURED", fresh.facts["generated-bytes"])
        assertFalse(fresh.facts.values.any { it.contains(root.toString()) })
        assertNotEquals(fresh.id, BuildIsolationContext(root.resolve("other"), "different-seed").cacheObservation().id)
        a.recordInvocation("input", "environment", "provider/profile", "PROCESS_PASSED")
        b.recordInvocation("input", "environment", "provider/profile", "PROCESS_PASSED")
        assertEquals(a.cacheObservation().id, b.cacheObservation().id)
        assertNotEquals(fresh.id, a.cacheObservation().id)
        assertEquals("1", a.cacheObservation().facts["prior-invocations"])
        assertEquals("serially-reused-private-cache", a.cacheObservation().facts["generated-state"])
        assertTrue(Files.isRegularFile(a.directory.resolve("cache-history.tsv")))
        assertFalse(Files.readString(a.directory.resolve("cache-history.tsv")).contains(root.toString()))
    }

    @Test fun failedOrDifferentInvocationsDoNotPretendCacheEquivalence(@TempDir(cleanup = CleanupMode.NEVER) root: Path) {
        val a = BuildIsolationContext(root.resolve("a"), "seed")
        val b = BuildIsolationContext(root.resolve("b"), "seed")
        Files.createDirectories(a.directory); Files.createDirectories(b.directory)
        a.recordInvocation("input", "environment", "provider/profile", "PROCESS_PASSED")
        b.recordInvocation("input", "environment", "provider/profile", "PROCESS_FAILED_OR_INTERRUPTED")
        assertNotEquals(a.cacheObservation().id, b.cacheObservation().id)
        assertEquals("UNKNOWN", BuildIsolationContext(root.resolve("unidentified")).cacheObservation().id)
    }

    @Test fun rootIdentityIsLoadedOnlyOnceForEachRun(@TempDir(cleanup = CleanupMode.NEVER) root: Path) {
        val calls = AtomicInteger()
        val a = BuildIsolationTiming.rootInput(root, root.resolve("run-a")) { "root-${calls.incrementAndGet()}" }
        val again = BuildIsolationTiming.rootInput(root, root.resolve("run-a")) { "root-${calls.incrementAndGet()}" }
        val other = BuildIsolationTiming.rootInput(root, root.resolve("run-b")) { "root-${calls.incrementAndGet()}" }
        assertEquals(a, again)
        assertNotEquals(a, other)
        assertEquals(2, calls.get())
    }

    @Test fun timingInputBindsRootProviderRecipeAndConsumedProviderBytes() {
        fun identity(root: String = "root", source: String = "source", recipe: String = "recipe", sha: String = "sha") =
            BuildIsolationTiming.inputIdentity(root, source, recipe, mapOf("provider" to sha))
        val baseline = identity()
        assertNotEquals(baseline, identity(root = "changed-root"))
        assertNotEquals(baseline, identity(source = "changed-source"))
        assertNotEquals(baseline, identity(recipe = "changed-profile-properties-or-tasks"))
        assertNotEquals(baseline, identity(sha = "changed-provider-stage-bytes"))
        assertEquals(baseline, identity())
    }
}
