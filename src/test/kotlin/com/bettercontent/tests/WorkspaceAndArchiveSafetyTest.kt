package com.bettercontent.tests

import com.bettercontent.tests.release.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Protect user files and packaged bytes; no synthetic visual-approval certificates. */
@Tag("fast")
class WorkspaceAndArchiveSafetyTest {
    @TempDir lateinit var root: Path
    private val mapper = jacksonObjectMapper()

    private fun workspace(): Path {
        val project = Files.createDirectories(root.resolve("workspace"))
        val process = ProcessBuilder("git", "init", "-q").directory(project.toFile()).start()
        check(process.waitFor() == 0)
        Files.writeString(project.resolve("pack.toml"), "user pack bytes")
        Files.writeString(project.resolve("index.toml"), "user dirty index bytes")
        Files.createDirectories(project.resolve("mods"))
        Files.writeString(project.resolve("mods/tracked.jar"), "staged version")
        val add = ProcessBuilder("git", "add", "mods/tracked.jar").directory(project.toFile()).start(); check(add.waitFor() == 0)
        Files.writeString(project.resolve("mods/tracked.jar"), "user dirty tracked provider")
        Files.writeString(project.resolve("mods/untracked.jar"), "user untracked provider")
        Files.writeString(project.resolve("launch.main.kts"), "user launcher")
        return project
    }
    @Test fun userDirtyTrackedUntrackedBytesModesAndAbsenceArePreservedBeforeAdmission() {
        val project = workspace()
        Files.setAttribute(project.resolve("launch.main.kts"), "unix:mode", 0x81ed) // regular 0755
        val absent = ActiveMod("new", "new", "new.jar", emptyList())
        val receipt = preserveWorkspace(project, root.resolve("backup"), listOf(absent))
        val node = mapper.readTree(receipt.toFile())
        assertTrue(node.path("gitStatus").asText().contains("mods/tracked.jar"))
        assertEquals("user dirty tracked provider", Files.readString(receipt.parent.resolve("files/mods/tracked.jar")))
        assertEquals("user untracked provider", Files.readString(receipt.parent.resolve("files/mods/untracked.jar")))
        assertEquals(Files.getAttribute(project.resolve("launch.main.kts"), "unix:mode"), Files.getAttribute(receipt.parent.resolve("files/launch.main.kts"), "unix:mode"))
        assertTrue(node.path("items").any { it.path("path").asText() == "mods/new.jar" && it.path("kind").asText() == "absent" })
        assertDoesNotThrow { validateWorkspacePreservation(receipt, project, listOf(absent)) }
        assertThrows(Exception::class.java) { preserveWorkspace(project, receipt.parent, listOf(absent)) }
        assertEquals("user dirty index bytes", Files.readString(receipt.parent.resolve("files/index.toml")))
    }
    @Test fun changedAddedDeletedOrModeChangedUserFilesAbortBeforeOverwrite() {
        val project = workspace()
        val receipt = preserveWorkspace(project, root.resolve("backup"), emptyList())
        val jar = project.resolve("mods/untracked.jar")
        Files.writeString(jar, "concurrent user edit")
        assertThrows(IllegalArgumentException::class.java) { validateWorkspacePreservation(receipt, project) }
        Files.writeString(jar, "user untracked provider")
        Files.writeString(project.resolve("mods/new-user.jar"), "new user data")
        assertThrows(IllegalArgumentException::class.java) { validateWorkspacePreservation(receipt, project) }
        Files.delete(project.resolve("mods/new-user.jar")); Files.delete(jar)
        assertThrows(IllegalArgumentException::class.java) { validateWorkspacePreservation(receipt, project) }
        Files.writeString(jar, "user untracked provider")
        Files.setAttribute(jar, "unix:mode", 0x81c0) // regular 0700
        assertThrows(IllegalArgumentException::class.java) { validateWorkspacePreservation(receipt, project) }
        assertEquals("user untracked provider", Files.readString(receipt.parent.resolve("files/mods/untracked.jar")))
    }
    @Test fun preservationIncludesSymlinkIdentityAndActualDirtyReferentBytes() {
        val project = workspace()
        val target = Files.writeString(project.resolve("provider.jar"), "dirty linked provider bytes")
        Files.createSymbolicLink(project.resolve("mods/linked.jar"), Path.of("../provider.jar"))
        val receipt = preserveWorkspace(project, root.resolve("backup"), emptyList())
        assertEquals(Path.of("../provider.jar"), Files.readSymbolicLink(receipt.parent.resolve("files/mods/linked.jar")))
        assertEquals("dirty linked provider bytes", Files.readString(receipt.parent.resolve("referents/mods/linked.jar")))
        Files.writeString(target, "concurrently rebuilt provider")
        assertThrows(IllegalArgumentException::class.java) { validateWorkspacePreservation(receipt, project) }
        assertDoesNotThrow { validateWorkspacePreservation(receipt) }
    }
    @Test fun missingMandatoryMetadataOrCorruptedBackupBlocksWithoutRootMutation() {
        val project = workspace()
        Files.delete(project.resolve("index.toml"))
        assertThrows(IllegalArgumentException::class.java) { preserveWorkspace(project, root.resolve("missing-backup"), emptyList()) }
        assertFalse(Files.exists(root.resolve("missing-backup")))
        Files.writeString(project.resolve("index.toml"), "user index")
        val receipt = preserveWorkspace(project, root.resolve("backup"), emptyList())
        Files.writeString(receipt.parent.resolve("files/mods/tracked.jar"), "corrupted backup")
        assertThrows(IllegalArgumentException::class.java) { validateWorkspacePreservation(receipt, project) }
        assertEquals("user dirty tracked provider", Files.readString(project.resolve("mods/tracked.jar")))
    }

    private fun zip(path: Path, files: Map<String, ByteArray>) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip -> files.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
        } }
    }
    private fun rawJar(): Path {
        val path = root.resolve("fixture.jar")
        JarOutputStream(Files.newOutputStream(path)).use { jar ->
            jar.putNextEntry(JarEntry("META-INF/mods.toml")); jar.write("[[mods]]\nmodId=\"fixture\"\n".toByteArray()); jar.closeEntry()
        }
        return path
    }
    private val mod = ActiveMod("fixture", "fixture", "fixture.jar", listOf("verifyFull"))
    @Test fun fullArchiveInventoryHashesCustomVendorScriptsConfigsAndResources() {
        val client = root.resolve("inventory.zip")
        zip(client, mapOf("overrides/mods/fixture.jar" to "raw".toByteArray(), "overrides/mods/vendor.jar" to "vendor".toByteArray(),
            "overrides/run.sh" to "script".toByteArray(), "overrides/config/setting.toml" to "config".toByteArray(), "overrides/assets/icon.png" to "resource".toByteArray()))
        val members = archiveMembers(client, "client", listOf(mod))
        assertEquals(5, members.size)
        assertEquals(setOf("custom", "third-party-binary", "script", "config", "resource-or-metadata"), members.map { it.category }.toSet())
        assertTrue(members.all { it.sha256.matches(Regex("[a-f0-9]{64}")) })
    }
    @Test fun addedRemovedOrChangedThirdPartyBinaryCannotSilentlyPass() {
        val baseline = listOf(ArchiveMember("server", "root/mods/vendor.jar", 1, "a".repeat(64), "third-party-binary"))
        assertDoesNotThrow { requireUnchangedThirdParty(baseline, baseline) }
        assertThrows(IllegalArgumentException::class.java) { requireUnchangedThirdParty(baseline, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { requireUnchangedThirdParty(baseline, baseline + baseline.single().copy(path = "root/mods/new.jar")) }
        assertThrows(IllegalArgumentException::class.java) { requireUnchangedThirdParty(baseline, listOf(baseline.single().copy(sha256 = "b".repeat(64)))) }
    }
    @Test fun postQualificationMetadataDecorationIsRejectedButLegacyAnnotatorRemainsUsable() {
        val jar = rawJar(); val qualified = Hashes.sha256(jar)
        annotateJar(jar, mod, "commit", "source-fingerprint")
        assertNotEquals(qualified, Hashes.sha256(jar))
        val client = root.resolve("decorated-client.zip"); val server = root.resolve("decorated-server.zip")
        zip(client, mapOf("overrides/mods/fixture.jar" to Files.readAllBytes(jar)))
        zip(server, mapOf("root/mods/fixture.jar" to Files.readAllBytes(jar)))
        assertThrows(IllegalArgumentException::class.java) {
            requireRawCustomMembers(archiveMembers(client, "client", listOf(mod)) + archiveMembers(server, "server", listOf(mod)), mapOf("fixture.jar" to qualified))
        }
    }
    @Test fun unsafeArchiveMemberIsRejected() {
        val archive = root.resolve("unsafe.zip"); zip(archive, mapOf("../escape.jar" to byteArrayOf(1)))
        assertThrows(IllegalArgumentException::class.java) { archiveMembers(archive, "server", listOf(mod)) }
    }
}
