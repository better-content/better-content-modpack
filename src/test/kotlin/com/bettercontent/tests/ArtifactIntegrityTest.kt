package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Tag("fast")
class ArtifactIntegrityTest {
    private fun prepare(root: Path): Pair<ArtifactIntegrity, Map<ArtifactRole, Path>> {
        val paths = ArtifactRole.entries.associateWith { role ->
            root.resolve(role.name).also { Files.writeString(it, "exact ${role.name} bytes") }
        }
        val registry = ArtifactIntegrity()
        paths.forEach { (role, path) -> registry.register(path, Hashes.sha256(path), role) }
        return registry to paths
    }

    @Test fun unchangedAndDuplicateRegistrationsPass(@TempDir root: Path) {
        val (registry, paths) = prepare(root)
        paths.forEach { (role, path) -> registry.register(path, Hashes.sha256(path), role) }
        assertTrue(registry.audit(runtimeSupport = true).isEmpty())
    }

    @Test fun detectsChangedAndDeletedArtifactsOfEveryRole(@TempDir root: Path) {
        for (role in ArtifactRole.entries) for (delete in listOf(false, true)) {
            val dir = Files.createDirectory(root.resolve("$role-$delete"))
            val (registry, paths) = prepare(dir)
            if (delete) Files.delete(paths.getValue(role)) else Files.writeString(paths.getValue(role), "changed")
            val findings = registry.audit(runtimeSupport = true)
            assertEquals(1, findings.size)
            assertEquals(paths.getValue(role), findings.single().path)
            assertEquals(setOf(role), findings.single().roles)
        }
    }

    @Test fun collectsAllFailuresAndRequiresEveryRuntimeRole(@TempDir root: Path) {
        val (registry, paths) = prepare(root)
        paths.values.forEach { Files.delete(it) }
        assertEquals(ArtifactRole.entries.size, registry.audit(true).size)
        assertEquals(ArtifactRole.entries.toSet(), ArtifactIntegrity().audit(true).flatMap { it.roles }.toSet())
        assertEquals(setOf(ArtifactRole.CANDIDATE_CLIENT, ArtifactRole.CANDIDATE_SERVER),
            ArtifactIntegrity().audit(false).flatMap { it.roles }.toSet())
    }

    @Test fun candidatesAloneAreSufficientOnlyForCandidateSuite(@TempDir root: Path) {
        val file = root.resolve("candidate.zip").also { Files.writeString(it, "candidate bytes") }
        val registry = ArtifactIntegrity()
        registry.registerCandidate(CandidatePair(root, file, file, Hashes.sha256(file), Hashes.sha256(file)))
        assertTrue(registry.audit(false).isEmpty())
        assertEquals(setOf(ArtifactRole.SUPPORT_SOURCE, ArtifactRole.SUPPORT_INSTALLED, ArtifactRole.FIXTURE_POLICY),
            registry.audit(true).flatMap { it.roles }.toSet())
    }

    @Test fun conflictingExpectationsAndUnsafeInitialPathsAreRejected(@TempDir root: Path) {
        val (registry, paths) = prepare(root)
        val file = paths.getValue(ArtifactRole.CANDIDATE_CLIENT)
        assertThrows(IllegalArgumentException::class.java) { registry.register(file, "0".repeat(64), ArtifactRole.CANDIDATE_CLIENT) }
        assertThrows(IllegalArgumentException::class.java) { registry.register(file, "bad-hash", ArtifactRole.CANDIDATE_CLIENT) }
        val link = root.resolve("alias")
        Files.createSymbolicLink(link, file)
        assertThrows(IllegalArgumentException::class.java) { registry.register(link, Hashes.sha256(file), ArtifactRole.CANDIDATE_CLIENT) }
        assertThrows(IllegalArgumentException::class.java) {
            registry.register(root.resolve("../${root.fileName}/${file.fileName}"), Hashes.sha256(file), ArtifactRole.CANDIDATE_CLIENT)
        }
    }

    @Test fun sameByteReplacementAndSymlinkSubstitutionFailClosed(@TempDir root: Path) {
        for (symlink in listOf(false, true)) {
            val dir = Files.createDirectory(root.resolve("case-$symlink"))
            val (registry, paths) = prepare(dir)
            val file = paths.getValue(ArtifactRole.SUPPORT_INSTALLED)
            val replacement = dir.resolve("replacement").also { Files.copy(file, it) }
            if (symlink) {
                Files.delete(file)
                Files.createSymbolicLink(file, replacement)
            } else Files.move(replacement, file, StandardCopyOption.REPLACE_EXISTING)
            val findings = registry.audit(true)
            assertEquals(1, findings.size)
            assertEquals(file, findings.single().path)
        }
    }

    @Test fun ancestorSymlinkSubstitutionIsRejected(@TempDir root: Path) {
        val original = Files.createDirectory(root.resolve("original"))
        val (registry, _) = prepare(original)
        val moved = root.resolve("moved")
        Files.move(original, moved)
        Files.createSymbolicLink(original, moved)
        assertEquals(ArtifactRole.entries.size, registry.audit(true).size)
    }
}
