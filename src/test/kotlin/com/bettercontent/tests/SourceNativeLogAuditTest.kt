package com.bettercontent.tests

import com.bettercontent.tests.release.auditSourceNativeLogs
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

/** Synthetic filesystem/parser controls, not native process or gameplay evidence. */
@Tag("fast")
class SourceNativeLogAuditTest {
    @TempDir lateinit var directory: Path
    private fun file(path: Path, text: String): Path {
        Files.createDirectories(path.parent)
        return Files.writeString(path, text)
    }
    private fun repository(): Path = directory.resolve("repository").also {
        file(it.resolve("build.gradle.kts"), "plugins {}")
    }
    private fun status(evidence: Path): String = jacksonObjectMapper().readTree(evidence.resolve("report.json").toFile())["status"].asText()

    @Test fun failedWarnDecodeCannotBecomeASuccessfulSourceReceipt() {
        val repo = repository()
        val raw = "[21Oct2026 11:29:15.763] [main/WARN] [net.minecraftforge.common.loot.LootModifierManager/]: " +
            "Could not decode GlobalLootModifier with json id thirst:add_loot_shipwreck_supply - error: Unknown serializer thirst:add_loot_table\n"
        val log = file(repo.resolve("build/gametest/fresh/logs/latest.log"), raw)
        val evidence = directory.resolve("audit")
        assertThrows(IllegalArgumentException::class.java) { auditSourceNativeLogs(repo, evidence) }
        assertEquals("failed", status(evidence))
        assertEquals(raw, Files.readString(log))
        assertTrue(Files.readString(evidence.resolve("report.json")).contains("Unknown serializer"))
    }

    @Test fun archivedWarnSoilFallbackCannotBecomeASuccessfulSourceReceipt() {
        val repo = repository()
        val raw = "[21Oct2026 14:17:34.428] [Worker-Main-9/WARN] " +
            "[com.ferreusveritas.dynamictrees.tree.species.Species/]: Rooty Dirt block NOT FOUND for soil minecraft:seagrass\n"
        val archive = repo.resolve("build/gametest/fresh/logs/debug-1.log.gz")
        Files.createDirectories(archive.parent)
        GZIPOutputStream(Files.newOutputStream(archive)).use { it.write(raw.toByteArray()) }
        file(archive.parent.resolve("latest.log"), "[INFO] current tail clean\n")
        val before = Hashes.sha256(archive)
        val evidence = directory.resolve("audit")
        assertThrows(IllegalArgumentException::class.java) { auditSourceNativeLogs(repo, evidence) }
        assertEquals("failed", status(evidence))
        assertEquals(before, Hashes.sha256(archive))
        assertTrue(Files.readString(evidence.resolve("report.json")).contains("Rooty Dirt block NOT FOUND"))
        assertFalse(jacksonObjectMapper().readTree(evidence.resolve("report.json").toFile())["native_readiness_claim"].asBoolean())
    }

    @Test fun errorsInDebugAndNestedProjectNativeRootsAreNotHiddenByCleanLatest() {
        val repo = repository()
        file(repo.resolve("build/gametest/fresh/logs/latest.log"), "[INFO] clean presentation\n")
        file(repo.resolve("modules/forge/build.gradle"), "plugins {}")
        val debug = file(repo.resolve("modules/forge/run-gametest/attempts/fresh/logs/debug.log"), "[ERROR] original native failure\n")
        val evidence = directory.resolve("audit")
        assertThrows(IllegalArgumentException::class.java) { auditSourceNativeLogs(repo, evidence) }
        assertEquals("failed", status(evidence))
        assertEquals("[ERROR] original native failure\n", Files.readString(debug))
    }

    @Test fun compressedArchiveFailureIsAuditedWithoutChangingItsOriginalBytes() {
        val repo = repository()
        val archive = repo.resolve("run-gametest-validation/logs/2026-10-21-1.log.gz")
        Files.createDirectories(archive.parent)
        GZIPOutputStream(Files.newOutputStream(archive)).use { it.write("[ERROR] archived native failure\n".toByteArray()) }
        val before = Hashes.sha256(archive)
        file(archive.parent.resolve("latest.log"), "[INFO] current tail clean\n")
        val evidence = directory.resolve("audit")
        assertThrows(IllegalArgumentException::class.java) { auditSourceNativeLogs(repo, evidence) }
        assertEquals("failed", status(evidence))
        assertEquals(before, Hashes.sha256(archive))
        assertEquals("[ERROR] archived native failure\n", Files.readString(evidence.resolve("expanded/run-gametest-validation/logs/2026-10-21-1.log.gz.decoded.log")))
    }

    @Test fun ordinaryDeveloperInstancesAndAuthoredFixtureProjectsAreNotAuditedAsCurrentNativeRuns() {
        val repo = repository()
        val developer = file(repo.resolve("run/logs/latest.log"), "[ERROR] unrelated developer history\n")
        file(repo.resolve("src/test/fixtures/project/build.gradle"), "plugins {}")
        file(repo.resolve("src/test/fixtures/project/build/gametest/fake/logs/debug.log"), "[ERROR] synthetic negative control\n")
        val evidence = directory.resolve("audit")
        auditSourceNativeLogs(repo, evidence)
        assertEquals("no-native-logs-observed", status(evidence))
        assertFalse(jacksonObjectMapper().readTree(evidence.resolve("report.json").toFile())["native_readiness_claim"].asBoolean())
        assertEquals("[ERROR] unrelated developer history\n", Files.readString(developer))
    }

    @Test fun cleanLogsRemainSupplementaryNotNativeReadinessAndEvidenceCannotOverwrite() {
        val repo = repository()
        file(repo.resolve("run-gametest/logs/latest.log"), "[INFO] no failed operation observed\n")
        val evidence = directory.resolve("audit")
        auditSourceNativeLogs(repo, evidence)
        assertEquals("clean", status(evidence))
        assertFalse(jacksonObjectMapper().readTree(evidence.resolve("report.json").toFile())["native_readiness_claim"].asBoolean())
        val before = Hashes.sha256(evidence.resolve("report.json"))
        assertThrows(java.nio.file.FileAlreadyExistsException::class.java) { auditSourceNativeLogs(repo, evidence) }
        assertEquals(before, Hashes.sha256(evidence.resolve("report.json")))
    }

    @Test fun relativePathsCannotBypassTheOutsideSourceEvidenceBoundary() {
        val evidence = directory.resolve("audit")
        val failure = assertThrows(IllegalArgumentException::class.java) {
            auditSourceNativeLogs(Path.of("relative-source"), evidence)
        }
        assertTrue(failure.message.orEmpty().contains("absolute"))
        assertFalse(Files.exists(evidence))
    }

    @Test fun linkedLogDirectoriesCannotProduceACleanOrEmptyAudit() {
        val repo = repository()
        val outside = file(directory.resolve("other/logs/debug.log"), "[ERROR] hidden native failure\n")
        val link = repo.resolve("build/gametest/fresh/logs")
        Files.createDirectories(link.parent)
        Files.createSymbolicLink(link, outside.parent)
        val evidence = directory.resolve("audit")
        val failure = assertThrows(IllegalArgumentException::class.java) { auditSourceNativeLogs(repo, evidence) }
        assertTrue(failure.message.orEmpty().contains("symlink"))
        assertFalse(Files.exists(evidence.resolve("report.json")))
        assertEquals("[ERROR] hidden native failure\n", Files.readString(outside))
    }

    @Test fun sourceLogLinksAndInsideSourceEvidenceFailClosed() {
        val repo = repository()
        val target = file(directory.resolve("outside.log"), "[ERROR] outside\n")
        val log = repo.resolve("build/gametest/fresh/logs/latest.log")
        Files.createDirectories(log.parent)
        Files.createSymbolicLink(log, target)
        assertThrows(IllegalArgumentException::class.java) { auditSourceNativeLogs(repo, directory.resolve("audit")) }
        assertThrows(IllegalArgumentException::class.java) { auditSourceNativeLogs(repo, repo.resolve("evidence")) }
        assertEquals("[ERROR] outside\n", Files.readString(target))
    }
}
