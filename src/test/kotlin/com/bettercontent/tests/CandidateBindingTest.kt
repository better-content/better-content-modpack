package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/** Identity/binding contracts only; stub archive bytes never claim candidate/content acceptance. */
@Tag("fast")
class CandidateBindingTest {
    private val mapper = jacksonObjectMapper()
    private fun git(root: Path, vararg args: String) = BoundedCommand.capture(root, listOf("git") + args, Duration.ofSeconds(10)).trim()
    private data class Fixture(val workspace: Path, val root: Path, val source: Path, val binding: Path, val client: Path)
    private fun fixture(): Fixture {
        val workspace = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "binding-")
        val root = workspace.resolve("pack"); val source = workspace.resolve("mod_source/fixture")
        Files.createDirectories(root.resolve("mods")); Files.createDirectories(root.resolve("gradle")); Files.createDirectories(source)
        git(source, "init", "-q")
        Files.writeString(source.resolve("source.txt"), "authored source")
        git(source, "add", "source.txt")
        git(source, "-c", "user.email=test@example.invalid", "-c", "user.name=Test", "commit", "-qm", "fixture")
        git(root, "init", "-q")
        Files.writeString(root.resolve("mods/fixture.jar"), "stub production artifact")
        Files.writeString(root.resolve("gradle/active-custom-mods.json"), """{"mods":[{"repository":"fixture","artifact":"fixture.jar"}]}""")
        Files.writeString(root.resolve(".gitignore"), "dist/\n")
        git(root, "add", ".")
        git(root, "-c", "user.email=test@example.invalid", "-c", "user.name=Test", "commit", "-qm", "fixture")
        val client = root.resolve("dist/build-1/client/better-content.zip")
        val server = root.resolve("dist/build-1/server/better-content.zip")
        Files.createDirectories(client.parent); Files.createDirectories(server.parent)
        Files.writeString(client, "stub client archive"); Files.writeString(server, "stub server archive")
        val binding = workspace.resolve("provenance.json")
        mapper.writeValue(binding.toFile(), mapOf("schema" to "bc.fresh_dist_provenance.v1", "tests_skipped" to false,
            "runtime_input_sha256" to runtimeInputIdentity(root),
            "candidates" to mapOf("client" to client.toString(), "server" to server.toString(),
                "client_sha256" to Hashes.sha256(client), "server_sha256" to Hashes.sha256(server)),
            "mods" to listOf(mapOf("repository" to "fixture", "artifact" to "fixture.jar",
                "commit" to git(source, "rev-parse", "HEAD"), "sha256" to Hashes.sha256(root.resolve("mods/fixture.jar")), "support_hashes" to emptyMap<String, String>()))))
        return Fixture(workspace, root, source, binding, client)
    }
    @Test fun `exact binding tolerates harness edits but rejects unrefreshed production profile edits`() {
        val f = fixture()
        try {
            assertEquals(f.client, CandidateBinding.select(f.root, f.binding).client)
            val harness = f.root.resolve("src/test/new-test.txt")
            Files.createDirectories(harness.parent); Files.writeString(harness, "harness-only repair")
            assertEquals(f.client, CandidateBinding.select(f.root, f.binding).client)
            val profile = f.root.resolve("server-config/production.properties")
            Files.createDirectories(profile.parent); Files.writeString(profile, "view-distance=99")
            assertThrows(IllegalArgumentException::class.java) { CandidateBinding.select(f.root, f.binding) }
        } finally { f.workspace.toFile().deleteRecursively() }
    }
    @Test fun `source mutation or changed archive cannot authorize a retry`() {
        val f = fixture()
        try {
            Files.writeString(f.source.resolve("source.txt"), "unverified source")
            assertThrows(IllegalArgumentException::class.java) { CandidateBinding.select(f.root, f.binding) }
            Files.writeString(f.source.resolve("source.txt"), "authored source")
            Files.writeString(f.client, "changed archive")
            assertThrows(IllegalArgumentException::class.java) { CandidateBinding.select(f.root, f.binding) }
        } finally { f.workspace.toFile().deleteRecursively() }
    }
}
