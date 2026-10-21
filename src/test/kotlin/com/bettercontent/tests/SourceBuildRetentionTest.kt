package com.bettercontent.tests

import com.bettercontent.tests.release.retainSourceOutputsForVerification
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class SourceBuildRetentionTest {
    @TempDir lateinit var directory: Path
    private fun file(path: Path, text: String): Path { Files.createDirectories(path.parent); return Files.writeString(path, text) }
    private fun repository() = directory.resolve("source").also { Files.createDirectories(it); file(it.resolve("build.gradle.kts"), "// owned project") }

    @Test fun relativePathsCannotBypassTheOutsideSourceRetentionBoundary() {
        val evidence = directory.resolve("evidence")
        val failure = assertThrows(IllegalArgumentException::class.java) {
            retainSourceOutputsForVerification(Path.of("relative-source"), evidence, emptySet())
        }
        assertTrue(failure.message.orEmpty().contains("absolute"))
        assertFalse(Files.exists(evidence))
    }

    @Test fun existingCleanRetainsWholeBuildNativeWorldsEvidenceAndNestedProjectOutputs() {
        val repo = repository()
        file(repo.resolve("build/libs/provider.jar"), "old bytes")
        file(repo.resolve("build/failed-native/world/level.dat"), "failed world")
        file(repo.resolve("run-gametest-validation/logs/latest.log"), "native evidence")
        file(repo.resolve("modules/core/build.gradle.kts"), "// owned subproject")
        file(repo.resolve("modules/core/build/test-results/test.xml"), "old result")
        val evidence = directory.resolve("evidence")
        val moves = retainSourceOutputsForVerification(repo, evidence, setOf("build.gradle.kts", "modules/core/build.gradle.kts"))
        assertEquals(3, moves.size)
        assertFalse(Files.exists(repo.resolve("build")))
        assertEquals("failed world", Files.readString(moves.getValue(repo.resolve("build")).resolve("failed-native/world/level.dat")))
        assertEquals("native evidence", Files.readString(moves.getValue(repo.resolve("run-gametest-validation")).resolve("logs/latest.log")))
        assertEquals("old result", Files.readString(moves.getValue(repo.resolve("modules/core/build")).resolve("test-results/test.xml")))
        assertTrue(Files.isRegularFile(repo.resolve("modules/core/build.gradle.kts")))
    }

    @Test fun buildOnlyReuseKeepsCompiledOutputsWhileRetainingOldNativeInstance() {
        val repo = repository()
        file(repo.resolve("build/libs/provider.jar"), "eligible checkpoint bytes")
        file(repo.resolve("run-gametest/world/level.dat"), "old native world")
        val moves = retainSourceOutputsForVerification(repo, directory.resolve("evidence"), emptySet(), includeBuild = false)
        assertEquals(setOf(repo.resolve("run-gametest")), moves.keys)
        assertEquals("eligible checkpoint bytes", Files.readString(repo.resolve("build/libs/provider.jar")))
        assertEquals("old native world", Files.readString(moves.getValue(repo.resolve("run-gametest")).resolve("world/level.dat")))
    }

    @Test fun authoredTrackedInputsRejectEntireOperationBeforeAnyMove() {
        val repo = repository()
        file(repo.resolve("build/evidence.txt"), "keep build")
        file(repo.resolve("run-gametest/server.properties"), "authored operating input")
        assertThrows(IllegalArgumentException::class.java) {
            retainSourceOutputsForVerification(repo, directory.resolve("evidence"), setOf("run-gametest/server.properties"))
        }
        assertEquals("keep build", Files.readString(repo.resolve("build/evidence.txt")))
        assertEquals("authored operating input", Files.readString(repo.resolve("run-gametest/server.properties")))
    }

    @Test fun neverOverwritesAnEarlierRetentionDestination() {
        val repo = repository()
        file(repo.resolve("build/evidence.txt"), "current")
        val old = file(directory.resolve("evidence/prior-source-outputs/source/build/evidence.txt"), "prior")
        assertThrows(IllegalArgumentException::class.java) { retainSourceOutputsForVerification(repo, directory.resolve("evidence"), emptySet()) }
        assertEquals("prior", Files.readString(old))
        assertEquals("current", Files.readString(repo.resolve("build/evidence.txt")))
    }

    @Test fun symlinkOutputsAndInsideSourceDestinationsFailWithoutFollowingOrDeletingData() {
        val repo = repository()
        val external = directory.resolve("external").also { Files.createDirectories(it) }
        file(external.resolve("keep.txt"), "external")
        Files.createSymbolicLink(repo.resolve("build"), external)
        assertThrows(IllegalArgumentException::class.java) { retainSourceOutputsForVerification(repo, directory.resolve("evidence"), emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { retainSourceOutputsForVerification(repo, repo.resolve("evidence"), emptySet()) }
        assertEquals("external", Files.readString(external.resolve("keep.txt")))
        assertTrue(Files.isSymbolicLink(repo.resolve("build")))
    }

    @Test fun ordinaryDeveloperClientVisualAndDatagenInstancesAreNotRelocatedOrTraversed() {
        val repo = repository()
        val ordinary = file(repo.resolve("run/saves/personal/level.dat"), "developer world")
        val visual = file(repo.resolve("run-visual/screenshots/native.png"), "prior visual evidence")
        val datagen = file(repo.resolve("run-data/operating-input.txt"), "authored operating input")
        assertTrue(retainSourceOutputsForVerification(repo, directory.resolve("evidence"), emptySet()).isEmpty())
        assertEquals("developer world", Files.readString(ordinary))
        assertEquals("prior visual evidence", Files.readString(visual))
        assertEquals("authored operating input", Files.readString(datagen))
    }

    @Test fun missingOutputsNeedNoResetAndAuthoredFixtureProjectsAreNotTraversed() {
        val repo = repository()
        file(repo.resolve("src/test/resources/project/build.gradle.kts"), "// golden fixture")
        val golden = file(repo.resolve("src/test/resources/project/build/expected.txt"), "authored fixture")
        assertEquals(emptyMap<Path, Path>(), retainSourceOutputsForVerification(repo, directory.resolve("evidence"), setOf(repo.relativize(golden).toString())))
        assertEquals("authored fixture", Files.readString(golden))
    }
}
