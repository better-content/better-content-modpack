package com.bettercontent.tests

import com.bettercontent.tests.release.scopedGitInputArgs
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration

/** Enumeration/fingerprint contracts only; no source build, native world or candidate acceptance. */
@Tag("fast")
class ScopedGitInputsTest {
    private fun root(): Path = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "scoped-git-")
        .also { capture(it, listOf("init", "-q")) }
    private fun write(root: Path, name: String, value: String = name) {
        Files.createDirectories(root.resolve(name).parent)
        Files.writeString(root.resolve(name), value)
    }
    private fun capture(root: Path, args: List<String>, limit: Int = 1024 * 1024): String =
        BoundedCommand.capture(root, listOf("git") + args, Duration.ofSeconds(10), limit)
    private fun names(root: Path, args: List<String>): Set<String> = capture(root, args).split('\u0000').filter(String::isNotEmpty).toSet()
    private val full = listOf("ls-files", "-z", "--cached", "--others", "--exclude-standard")

    @Test fun `preselection preserves the original glob domain for tracked and untracked inputs`() {
        val root = root()
        for (name in listOf("contracts/One.kt", "contracts/Two.kt", "contracts/deep/Nested.kt", "config/a1.toml",
            "config/a2.toml", "config/a[1].toml", "mods/pin.pw.toml", "options.txt", "space dir/é.txt", "notes.txt",
            "workspace_artifacts/retained/world.dat")) write(root, name)
        capture(root, listOf("add", "contracts/One.kt", "options.txt"))
        val all = names(root, full)
        for (patterns in listOf(listOf("contracts/*.kt", "mods/*.pw.toml", "options.txt"),
            listOf("contracts/**/*.kt", "space dir/é.txt"), listOf("contracts/{One,Two}.kt", "config/a[12].toml"),
            listOf("**"), listOf(""), emptyList())) {
            val matchers = patterns.map { root.fileSystem.getPathMatcher("glob:$it") }
            fun select(values: Set<String>) = values.filter { name -> matchers.any { it.matches(Path.of(name)) } }.toSet()
            assertEquals(select(all), select(names(root, scopedGitInputArgs(patterns))), patterns.toString())
        }
    }

    @Test fun `retained unrelated filenames cannot overflow the unchanged input capture bound`() {
        val root = root()
        write(root, "contracts/One.kt")
        repeat(32) { write(root, "workspace_artifacts/retained/${it}-${"x".repeat(180)}.dat") }
        assertThrows(IllegalStateException::class.java) { capture(root, full, 1024) }
        val selected = capture(root, scopedGitInputArgs(listOf("contracts/*.kt")), 1024)
        assertEquals("contracts/One.kt\u0000", selected)
        assertEquals(32L, Files.list(root.resolve("workspace_artifacts/retained")).use { it.count() })
    }

    @Test fun `explicit wide and empty scopes keep semantics and unsafe scopes still fail`() {
        assertEquals(full, scopedGitInputArgs(listOf("**/*.kt")))
        assertEquals(full, scopedGitInputArgs(listOf("contracts/*.kt", "*.toml")))
        assertEquals(full + listOf("--", ":(top,literal)contracts/", ":(top,literal)options.txt"),
            scopedGitInputArgs(listOf("contracts/*.kt", "contracts/**/*.kt", "options.txt")))
        for (pattern in listOf("/config/file", "../config/file", "config/../file"))
            assertThrows(IllegalArgumentException::class.java) { scopedGitInputArgs(listOf(pattern)) }
    }

    @Test fun `runtime identity retains its exact production domain and excludes retained evidence`() {
        val root = root()
        val inputs = listOf("mods/main.jar", "config/a.toml", "kubejs/server_scripts/world.js", "options.txt")
        inputs.forEach { write(root, it) }
        write(root, "notes.txt")
        val digest = MessageDigest.getInstance("SHA-256")
        inputs.sorted().forEach { digest.update((it + "\u0000" + Hashes.sha256(root.resolve(it)) + "\u0000").toByteArray()) }
        val expected = digest.digest().joinToString("") { "%02x".format(it) }
        assertEquals(expected, runtimeInputIdentity(root))
        write(root, "workspace_artifacts/retained/world.dat")
        assertEquals(expected, runtimeInputIdentity(root))
        write(root, "config/a.toml", "changed production")
        assertNotEquals(expected, runtimeInputIdentity(root))
    }
}
