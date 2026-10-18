package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Inert filesystem/evidence checks: no Minecraft or historical snapshot input. */
@Tag("fast")
class FixtureRetentionTest {
    @Test fun `passed and failed evidence runs retain their complete fixture`(@TempDir temporary: Path) {
        val root = Path.of(System.getProperty("bc.repo.root"))
        val config = TestConfig(root, "retention-fixture", temporary, 0, "Fixture", temporary,
            Path.of(System.getProperty("java.home"), "bin/java"), null)
        for (passed in listOf(true, false)) {
            val run = EvidenceRun(config, if (passed) "passed-fixture" else "failed-fixture")
            val retained = run.fixture.resolve("world/data/marker.dat")
            Files.createDirectories(retained.parent)
            Files.writeString(retained, "owned fixture bytes")
            run.finish(passed)
            assertEquals("owned fixture bytes", Files.readString(retained))
            val summary = jacksonObjectMapper().readTree(run.directory.resolve("run.json").toFile())
            assertEquals(if (passed) "passed" else "failed", summary.path("status").asText())
            assertEquals(run.fixture.toString(), summary.path("fixture").asText())
        }
    }

    private fun snapshot(path: Path, text: String): Path {
        Files.createDirectories(path)
        Files.writeString(path.resolve("snapshot-marker"), text)
        return path
    }
    private fun marker(path: Path) = Files.readString(path.resolve("snapshot-marker"))

    @Test fun `atomic snapshot promotion retains previous snapshot and unchanged source`(@TempDir root: Path) {
        val source = snapshot(root.resolve("source"), "new bytes")
        val destination = snapshot(root.resolve("runtime-dumps"), "previous bytes")
        promoteSnapshotFiles(source, destination, "successful")
        assertEquals("new bytes", marker(destination))
        assertEquals("new bytes", marker(source))
        assertEquals("previous bytes", marker(root.resolve(".runtime-dumps-successful.previous")))
        assertFalse(Files.exists(root.resolve(".runtime-dumps-successful.staging")))
        assertThrows(IllegalArgumentException::class.java) { promoteSnapshotFiles(source, destination, "successful") }
        assertEquals("new bytes", marker(destination))
    }

    @Test fun `failed promotion restores canonical previous snapshot while retaining backup and failed staging`(@TempDir root: Path) {
        val source = snapshot(root.resolve("source"), "new bytes")
        val destination = snapshot(root.resolve("runtime-dumps"), "previous bytes")
        var moves = 0
        val failure = assertThrows(IOException::class.java) {
            promoteSnapshotFiles(source, destination, "failed") { from, to ->
                if (++moves == 2) throw IOException("synthetic staging rename failure")
                Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
            }
        }
        assertEquals("synthetic staging rename failure", failure.message)
        assertEquals("previous bytes", marker(destination))
        assertEquals("previous bytes", marker(root.resolve(".runtime-dumps-failed.previous")))
        assertEquals("new bytes", marker(root.resolve(".runtime-dumps-failed.staging")))
        assertEquals("new bytes", marker(source))
        assertFalse(Files.exists(root.resolve(".runtime-dumps-failed.restore")))
    }
}
