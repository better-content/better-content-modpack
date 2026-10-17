package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Exact path selection tests use stub bytes; archive content is checked by candidate contracts. */
@Tag("fast")
class CandidateLocatorCurrentBuildTest {
    private fun pack(root: Path, version: String = "Build 444") {
        Files.createDirectories(root)
        Files.writeString(root.resolve("pack.toml"), "name = \"Fixture\"\nversion = \"$version\"\n[versions]\nminecraft = \"1.20.1\"\n")
    }

    private fun pair(root: Path, build: String, client: Boolean = true, server: Boolean = true) {
        if (client) root.resolve("dist/build-$build/client/better-content.zip").also {
            Files.createDirectories(it.parent); Files.writeString(it, "client $build")
        }
        if (server) root.resolve("dist/build-$build/server/better-content.zip").also {
            Files.createDirectories(it.parent); Files.writeString(it, "server $build")
        }
    }

    @Test fun `declared build selects its exact pair among retained older builds`(@TempDir root: Path) {
        pack(root); pair(root, "443"); pair(root, "443a"); pair(root, "444")
        val chosen = CandidateLocator.locateUnbound(root)
        assertEquals(root.resolve("dist/build-444"), chosen.release)
        assertEquals(Hashes.sha256(chosen.client), chosen.clientSha256)
        assertEquals(Hashes.sha256(chosen.server), chosen.serverSha256)
        pack(root, "Build 445"); pair(root, "445")
        assertEquals(root.resolve("dist/build-445"), CandidateLocator.locateUnbound(root).release)
    }

    @Test fun `lettered build selects its own pair after its unsuffixed predecessor`(@TempDir root: Path) {
        pack(root, "Build 443a"); pair(root, "443"); pair(root, "443a")
        assertEquals(root.resolve("dist/build-443a"), CandidateLocator.locateUnbound(root).release)
    }

    @Test fun `current missing or partial pair never borrows an older complete release`(@TempDir root: Path) {
        pack(root); pair(root, "443a")
        assertThrows(IllegalArgumentException::class.java) { CandidateLocator.locateUnbound(root) }
        pair(root, "444", server = false)
        assertThrows(IllegalArgumentException::class.java) { CandidateLocator.locateUnbound(root) }
        pair(root, "444", client = false)
        assertEquals(root.resolve("dist/build-444"), CandidateLocator.locateUnbound(root).release)
    }

    @Test fun `newer release contradicts declared production version`(@TempDir root: Path) {
        pack(root); pair(root, "444")
        Files.createDirectories(root.resolve("dist/build-444a"))
        assertThrows(IllegalArgumentException::class.java) { CandidateLocator.locateUnbound(root) }
        root.resolve("dist/build-444a").toFile().deleteRecursively()
        Files.createDirectories(root.resolve("dist/build-445"))
        assertThrows(IllegalArgumentException::class.java) { CandidateLocator.locateUnbound(root) }
    }

    @Test fun `malformed duplicate or missing production version fails closed`(@TempDir root: Path) {
        pair(root, "444")
        assertThrows(IllegalArgumentException::class.java) { CandidateLocator.locateUnbound(root) }
        for (text in listOf("version = \"Build ../443\"\n", "version = \"Build /444\"\n",
            "version = \"Build 443\"\nversion = \"Build 444\"\n",
            "version = \"Build 444\"\n\"version\" = \"Build 443\"\n", "name = \"Fixture\"\n")) {
            Files.writeString(root.resolve("pack.toml"), text)
            assertThrows(IllegalArgumentException::class.java) { CandidateLocator.locateUnbound(root) }
        }
    }

    @Test fun `pack dist release side and archive symlinks cannot become candidates`(@TempDir root: Path) {
        val target = root.resolve("target")
        pack(target); pair(target, "444")
        val cases = listOf("pack", "dist", "release", "side", "archive")
        for (name in cases) {
            val fixture = root.resolve(name)
            pack(fixture); pair(fixture, "444")
            when (name) {
                "pack" -> { Files.delete(fixture.resolve("pack.toml")); Files.createSymbolicLink(fixture.resolve("pack.toml"), target.resolve("pack.toml")) }
                "dist" -> { fixture.resolve("dist").toFile().deleteRecursively(); Files.createSymbolicLink(fixture.resolve("dist"), target.resolve("dist")) }
                "release" -> { fixture.resolve("dist/build-444").toFile().deleteRecursively(); Files.createSymbolicLink(fixture.resolve("dist/build-444"), target.resolve("dist/build-444")) }
                "side" -> { fixture.resolve("dist/build-444/client").toFile().deleteRecursively(); Files.createSymbolicLink(fixture.resolve("dist/build-444/client"), target.resolve("dist/build-444/client")) }
                "archive" -> { Files.delete(fixture.resolve("dist/build-444/client/better-content.zip")); Files.createSymbolicLink(fixture.resolve("dist/build-444/client/better-content.zip"), target.resolve("dist/build-444/client/better-content.zip")) }
            }
            assertThrows(IllegalArgumentException::class.java) { CandidateLocator.locateUnbound(fixture) }
        }
    }

    @Test fun `archive path must be a regular file`(@TempDir root: Path) {
        pack(root); pair(root, "444")
        Files.delete(root.resolve("dist/build-444/client/better-content.zip"))
        Files.createDirectories(root.resolve("dist/build-444/client/better-content.zip"))
        assertTrue(Files.isDirectory(root.resolve("dist/build-444/client/better-content.zip")))
        assertThrows(IllegalArgumentException::class.java) { CandidateLocator.locateUnbound(root) }
    }

    @Test fun `pack root symlink ancestor is rejected`(@TempDir root: Path) {
        val actual = root.resolve("actual/pack")
        pack(actual); pair(actual, "444")
        Files.createSymbolicLink(root.resolve("alias"), root.resolve("actual"))
        assertThrows(IllegalArgumentException::class.java) {
            CandidateLocator.locateUnbound(root.resolve("alias/pack"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CandidateLocator.locateUnbound(root.resolve("alias/../actual/pack"))
        }
    }
}
