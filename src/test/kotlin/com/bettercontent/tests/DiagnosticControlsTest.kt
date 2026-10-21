package com.bettercontent.tests

import com.bettercontent.tests.release.ActiveMod
import com.bettercontent.tests.release.DiagnosticControls
import com.bettercontent.tests.release.ActiveModManifest
import com.bettercontent.tests.release.diagnosticPackContracts
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/** Synthetic identity/admission controls, never a native/content acceptance claim. */
@Tag("fast")
class DiagnosticControlsTest {
    private val mapper = jacksonObjectMapper()
    private val mod = ActiveMod(DiagnosticControls.REPOSITORY, "better_oc2r_create_controls", "controls.jar", listOf("verifyFull", "stageRuntimeJar"))
    private val commit = "a".repeat(40)
    private fun git(root: Path, vararg args: String) = BoundedCommand.capture(root, listOf("git") + args, Duration.ofSeconds(10)).trim()
    private fun init(root: Path) {
        Files.createDirectories(root); git(root, "init", "-q")
        Files.writeString(root.resolve("source.txt"), "source")
        git(root, "add", "."); git(root, "-c", "user.email=test@example.invalid", "-c", "user.name=Test", "commit", "-qm", "fixture")
    }
    private fun input(base: Path): Path {
        Files.createDirectories(base)
        val artifact = base.resolve("controls.jar")
        JarOutputStream(Files.newOutputStream(artifact)).use { jar ->
            fun entry(name: String, text: String) { jar.putNextEntry(JarEntry(name)); jar.write(text.toByteArray()); jar.closeEntry() }
            entry("META-INF/mods.toml", "[[mods]]\nmodId=\"${mod.modId}\"\n")
            entry("META-INF/better-content-source.properties", "schema=bc.custom_mod_source.v1\nrepository=${mod.repository}\nmod_id=${mod.modId}\ncommit=$commit\n")
        }
        val rejection = Files.writeString(base.resolve("rejected.log"), "original rejected gate, not a pass")
        val context = Files.writeString(base.resolve("source-context.json"), "{\"artifact_origin\":\"UNKNOWN\",\"gate_head\":\"$commit\",\"dirty\":true}")
        val path = base.resolve("input.json")
        mapper.writeValue(path.toFile(), mapOf("schema" to "bc.diagnostic_controls_deferral.v1",
            "task" to "logging-implementation-20261020", "generation" to 1, "repository" to mod.repository,
            "status" to "DEFERRED_UNQUALIFIED", "artifact_source_commit" to commit,
            "artifact" to artifact.toString(), "artifact_sha256" to Hashes.sha256(artifact),
            "rejected_evidence" to rejection.toString(), "rejected_evidence_sha256" to Hashes.sha256(rejection),
            "source_context" to context.toString(), "source_context_sha256" to Hashes.sha256(context)))
        return path
    }
    @Test fun `only exact task Controls and true embedded identity may be deferred`(@TempDir base: Path) {
        val path = input(base)
        val original = mapper.readTree(path.toFile()) as ObjectNode
        assertEquals(commit, DiagnosticControls.load(path, mod).artifactCommit)
        for ((key, value) in listOf("schema" to "other", "task" to "other", "repository" to "other", "status" to "PASSED", "artifact_source_commit" to "b".repeat(40))) {
            mapper.writeValue(path.toFile(), original.deepCopy().put(key, value))
            assertThrows(IllegalArgumentException::class.java) { DiagnosticControls.load(path, mod) }
        }
        mapper.writeValue(path.toFile(), original.deepCopy().put("generation", 2))
        assertThrows(IllegalArgumentException::class.java) { DiagnosticControls.load(path, mod) }
        mapper.writeValue(path.toFile(), original)
        assertThrows(IllegalArgumentException::class.java) { DiagnosticControls.load(path, mod.copy(repository = "other")) }
        assertThrows(IllegalArgumentException::class.java) { DiagnosticControls.load(path, mod.copy(supportArtifacts = listOf("build/support.jar"))) }
    }
    @Test fun `every retained byte binding and symlink ancestry fail closed`(@TempDir base: Path) {
        for (key in listOf("artifact", "rejected_evidence", "source_context")) {
            val path = input(base.resolve(key)); val data = DiagnosticControls.load(path, mod)
            val original = mapper.readTree(path.toFile())
            Files.writeString(Path.of(original.path(key).asText()), "changed")
            assertThrows(IllegalArgumentException::class.java) { data.revalidate() }
        }
        val path = input(base.resolve("alias-test"))
        val alias = base.resolve("alias"); Files.createSymbolicLink(alias, path.parent)
        assertThrows(IllegalArgumentException::class.java) { DiagnosticControls.load(alias.resolve("input.json"), mod) }
    }
    private fun binding(base: Path): Triple<Path, Path, Path> {
        val path = input(base.resolve("retained")); val data = DiagnosticControls.load(path, mod)
        val root = base.resolve("pack"); init(root)
        val source = base.resolve("mod_source/other"); init(source)
        Files.createDirectories(root.resolve("gradle"))
        mapper.writeValue(root.resolve("gradle/active-custom-mods.json").toFile(), mapOf("mods" to listOf(mod,
            ActiveMod("other", "other", "other.jar", listOf("verifyFull")))))
        Files.writeString(root.resolve(".gitignore"), "dist/\n")
        git(root, "add", "."); git(root, "-c", "user.email=test@example.invalid", "-c", "user.name=Test", "commit", "-qm", "manifest")
        val sides = listOf("client", "server").associateWith { side ->
            root.resolve("dist/build-1/$side/better-content.zip").also { Files.createDirectories(it.parent); Files.writeString(it, "stub $side") }
        }
        val document = base.resolve("diagnostic-provenance.json")
        mapper.writeValue(document.toFile(), mapOf("schema" to DiagnosticControls.SCHEMA, "qualification" to "DIAGNOSTIC_UNQUALIFIED",
            "tests_skipped" to false, "verification_session" to null, "deferred_controls" to data.provenance(),
            "runtime_input_sha256" to runtimeInputIdentity(root), "candidates" to mapOf("client" to sides.getValue("client").toString(),
                "server" to sides.getValue("server").toString(), "client_sha256" to Hashes.sha256(sides.getValue("client")),
                "server_sha256" to Hashes.sha256(sides.getValue("server"))),
            "mods" to listOf(mapOf("repository" to mod.repository, "mode" to "DEFERRED_UNQUALIFIED", "source_gate_status" to "DEFERRED_UNQUALIFIED",
                "sha256" to data.artifactSha256, "commit" to data.artifactCommit, "verification_id" to null, "support_hashes" to emptyMap<String,String>()),
                mapOf("repository" to "other", "commit" to git(source, "rev-parse", "HEAD"), "source_gate_status" to "PASSED_OWNING_GATES",
                    "diagnostic_gate_run" to "fixture-only", "support_hashes" to emptyMap<String,String>()))))
        return Triple(root, document, path)
    }
    @Test fun `diagnostic binding needs explicit opt in and never verifies Controls`(@TempDir base: Path) {
        val (root, document, input) = binding(base)
        assertThrows(IllegalArgumentException::class.java) { CandidateBinding.select(root, document, null) }
        assertTrue(CandidateBinding.select(root, document, input).client.toString().endsWith("client/better-content.zip"))
        val original = mapper.readTree(document.toFile()) as ObjectNode
        ((original.path("mods")[0]) as ObjectNode).put("mode", "verified")
        mapper.writeValue(document.toFile(), original)
        assertThrows(IllegalArgumentException::class.java) { CandidateBinding.select(root, document, input) }
    }
    @Test fun `other providers cannot become deferred or dirty`(@TempDir base: Path) {
        val (root, document, input) = binding(base)
        val contract = root.resolve("contract.txt")
        Files.writeString(contract, "original consumed contract")
        val manifest = ActiveModManifest("bc.active_custom_mods.v1", listOf(mod), listOf("contract.txt"))
        val before = diagnosticPackContracts(root, manifest)
        Files.writeString(contract, "changed consumed contract")
        assertNotEquals(before, diagnosticPackContracts(root, manifest))
        val original = mapper.readTree(document.toFile()) as ObjectNode
        val changed = original.deepCopy()
        (changed.path("mods")[1] as ObjectNode).put("source_gate_status", "DEFERRED_UNQUALIFIED")
        mapper.writeValue(document.toFile(), changed)
        assertThrows(IllegalArgumentException::class.java) { CandidateBinding.select(root, document, input) }
        mapper.writeValue(document.toFile(), original)
        Files.writeString(base.resolve("mod_source/other/source.txt"), "dirty")
        assertThrows(IllegalArgumentException::class.java) { CandidateBinding.select(root, document, input) }
    }
}
