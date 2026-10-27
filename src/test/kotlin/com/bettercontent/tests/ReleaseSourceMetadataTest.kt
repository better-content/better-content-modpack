package com.bettercontent.tests

import com.bettercontent.tests.release.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

@Tag("fast")
class ReleaseSourceMetadataTest {
    @TempDir lateinit var root: Path
    private val mod = ActiveMod("fixture", "fixture", "fixture.jar", listOf("test", "verifyFull"))

    private fun git(repository: Path, vararg args: String): String {
        val process = ProcessBuilder(listOf("git") + args).directory(repository.toFile()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { output }
        return output.trim()
    }

    private fun fixture(): Path {
        val repository = Files.createDirectories(root.resolve("mod_source/fixture"))
        git(repository, "init", "-q")
        git(repository, "config", "user.email", "fixture@example.invalid")
        git(repository, "config", "user.name", "Fixture")
        Files.createDirectories(repository.resolve("src/main/java"))
        Files.writeString(repository.resolve("src/main/java/Runtime.java"), "class Runtime {}")
        Files.writeString(repository.resolve("build.gradle"), "// production build input")
        git(repository, "add", ".")
        git(repository, "commit", "-qm", "baseline")
        return repository
    }

    private fun jar(path: Path): Path {
        Files.createDirectories(path.parent)
        JarOutputStream(Files.newOutputStream(path)).use {
            it.putNextEntry(JarEntry("META-INF/mods.toml"))
            it.write("[[mods]]\nmodId=\"fixture\"\n".toByteArray())
            it.closeEntry()
        }
        return path
    }

    @Test fun productionBoundaryIncludesRuntimeResourcesAndBuildInputsButNotLocalTests() {
        listOf("src/main/resources/guide.md", "src/main/java/example/tests/Probe.java", "build.gradle",
            "gradle/wrapper/gradle-wrapper.jar", "settings.gradle.kts").forEach { assertTrue(isProductionInput(it), it) }
        listOf("src/test/java/Probe.java", "src/gametest/java/Probe.java", "docs/guide.md",
            "build/libs/runtime.jar", "README.md").forEach { assertFalse(isProductionInput(it), it) }
    }

    @Test fun baselineWithoutFingerprintOnlyReusesUnchangedProductionInputs() {
        val repository = fixture()
        val pack = Files.createDirectories(root.resolve("pack"))
        val bundled = jar(pack.resolve("mods/fixture.jar"))
        val commit = git(repository, "rev-parse", "HEAD")
        annotateJar(bundled, mod, commit)
        assertEquals(commit, readSourceCommit(bundled, mod))
        assertTrue(jarDeclaresMod(bundled, "fixture"))
        assertEquals("same", inspectSourceUpdate(pack, root, mod).status)
        Files.createDirectories(repository.resolve("src/test/java"))
        Files.writeString(repository.resolve("src/test/java/LocalTest.java"), "test-only dirty input")
        assertEquals("same", inspectSourceUpdate(pack, root, mod).status)
        Files.writeString(repository.resolve("src/main/java/Runtime.java"), "class Runtime { int changed; }")
        assertEquals("changed", inspectSourceUpdate(pack, root, mod).status)
    }

    @Test fun fingerprintAllowsDirtySnapshotButDetectsSubsequentProductionChanges() {
        val repository = fixture()
        val pack = Files.createDirectories(root.resolve("pack"))
        Files.writeString(repository.resolve("src/main/java/Runtime.java"), "class Runtime { int dirty; }")
        val bundled = jar(pack.resolve("mods/fixture.jar"))
        annotateJar(bundled, mod, git(repository, "rev-parse", "HEAD"), productionFingerprint(repository))
        assertEquals("same", inspectSourceUpdate(pack, root, mod).status)
        Files.writeString(repository.resolve("src/main/java/New.java"), "class New {}")
        assertEquals("changed", inspectSourceUpdate(pack, root, mod).status)
        Files.delete(repository.resolve("src/main/java/New.java"))
        Files.delete(repository.resolve("build.gradle"))
        assertEquals("changed", inspectSourceUpdate(pack, root, mod).status)
    }

    @Test fun changedSourceBuildsOnlyStageTaskAndAnnotatesTheStagedCopy() {
        val repository = fixture()
        val pack = Files.createDirectories(root.resolve("pack"))
        val artifact = jar(repository.resolve("build/libs/fixture.jar"))
        Files.writeString(repository.resolve("gradlew"), "#!/bin/sh\nprintf '%s\\n' \"\$@\" > build/invoked-args\n")
        repository.resolve("gradlew").toFile().setExecutable(true)
        val staged = Files.createDirectories(root.resolve("staging"))
        val evidence = Files.createDirectories(root.resolve("evidence"))
        val update = inspectSourceUpdate(pack, root, mod)
        val built = build(pack, root, evidence, staged, mod, update, nativeLock = root.resolve("fixture-gradle.lock"))
        assertEquals(listOf("--no-daemon", "--max-workers=2", "stageRuntimeJar"), Files.readAllLines(repository.resolve("build/invoked-args")))
        assertEquals("rebuilt", built.mode)
        assertEquals(git(repository, "rev-parse", "HEAD"), readSourceCommit(built.jar, mod))
        assertNull(readSourceCommit(artifact, mod))
    }

    @Test fun unchangedProviderReusesWithoutInvokingBuild() {
        val repository = fixture()
        val pack = Files.createDirectories(root.resolve("pack"))
        val bundled = jar(pack.resolve("mods/fixture.jar"))
        annotateJar(bundled, mod, git(repository, "rev-parse", "HEAD"), productionFingerprint(repository))
        val staged = Files.createDirectories(root.resolve("staging"))
        val evidence = Files.createDirectories(root.resolve("evidence"))
        val built = build(pack, root, evidence, staged, mod, inspectSourceUpdate(pack, root, mod))
        assertEquals("reused", built.mode)
        assertEquals(Hashes.sha256(bundled), built.sha256)
        assertFalse(Files.exists(repository.resolve("gradlew")))
    }
}
