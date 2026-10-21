package com.bettercontent.tests.release

import com.bettercontent.tests.CandidateBinding
import com.bettercontent.tests.Hashes
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path

/** Freeze only declared pack-side contracts, not unrelated Root HEAD or generated evidence. */
internal fun diagnosticPackContracts(root: Path, manifest: ActiveModManifest): Map<String, String> {
    val patterns = (manifest.verificationInputs + manifest.mods.flatMap { it.verificationInputs }).distinct()
    require(patterns.isNotEmpty() && patterns.all { !it.startsWith("/") && ".." !in it.split('/') }) { "unsafe diagnostic verification inputs" }
    val matchers = patterns.map { root.fileSystem.getPathMatcher("glob:$it") }
    val names = com.bettercontent.tests.BoundedCommand.capture(root,
        listOf("git") + scopedGitInputArgs(patterns),
        java.time.Duration.ofSeconds(30), 4 * 1024 * 1024).split('\u0000').filter { it.isNotBlank() }
        .filter { name -> matchers.any { it.matches(Path.of(name)) } }
        .filterNot { it == "index.toml" || (it.startsWith("mods/") && it.endsWith(".jar")) || it.startsWith(".worklane/") }
    return (names + "gradle/active-custom-mods.json").distinct().associateWith { name ->
        val path = root.resolve(name)
        if (Files.isSymbolicLink(path)) "link:" + Files.readSymbolicLink(path)
        else if (Files.isRegularFile(path)) Hashes.sha256(path) else "missing"
    }
}

/** Explicit task-local exception, never a verified checkpoint or an artifact rewrite. */
internal data class DiagnosticControls(
    val input: Path, val inputSha256: String,
    val artifact: Path, val artifactSha256: String,
    val rejectedEvidence: Path, val rejectedSha256: String,
    val sourceContext: Path, val contextSha256: String,
    val artifactCommit: String,
) {
    fun revalidate() {
        listOf(input to inputSha256, artifact to artifactSha256,
            rejectedEvidence to rejectedSha256, sourceContext to contextSha256).forEach { (path, sha) ->
            CandidateBinding.requireOrdinary(path)
            require(Files.isRegularFile(path) && Hashes.sha256(path) == sha) { "diagnostic Controls input changed: $path" }
        }
    }

    fun provenance(): Map<String, String> = mapOf(
        "repository" to REPOSITORY, "status" to "DEFERRED_UNQUALIFIED",
        "input" to input.toString(), "input_sha256" to inputSha256,
        "artifact" to artifact.toString(), "artifact_sha256" to artifactSha256,
        "artifact_source_commit" to artifactCommit,
        "rejected_evidence" to rejectedEvidence.toString(), "rejected_sha256" to rejectedSha256,
        "source_context" to sourceContext.toString(), "context_sha256" to contextSha256,
    )

    companion object {
        const val REPOSITORY = "better-oc2r-create-controls"
        const val ENV = "BC_RELEASE_DIAGNOSTIC_CONTROLS"
        const val SCHEMA = "bc.diagnostic_dist_provenance.v1"

        fun load(input: Path, mod: ActiveMod): DiagnosticControls {
            require(mod.repository == REPOSITORY && mod.supportArtifacts.isEmpty()) { "only Controls can be deferred" }
            require(input.isAbsolute) { "diagnostic input must be absolute" }
            CandidateBinding.requireOrdinary(input)
            require(Files.isRegularFile(input) && Files.size(input) <= 65536) { "invalid diagnostic input" }
            val before = Hashes.sha256(input)
            val node = jacksonObjectMapper().readTree(input.toFile())
            require(node.path("schema").asText() == "bc.diagnostic_controls_deferral.v1" &&
                node.path("task").asText() == "logging-implementation-20261020" &&
                node.path("generation").isIntegralNumber && node.path("generation").intValue() == 1 &&
                node.path("repository").asText() == REPOSITORY &&
                node.path("status").asText() == "DEFERRED_UNQUALIFIED") { "invalid task-scoped Controls deferral" }
            fun file(key: String): Pair<Path, String> {
                require(node.path(key).isTextual && node.path(key + "_sha256").isTextual) { "missing diagnostic $key binding" }
                val path = Path.of(node.path(key).textValue())
                val sha = node.path(key + "_sha256").textValue()
                require(path.isAbsolute && sha.matches(Regex("[a-f0-9]{64}"))) { "invalid diagnostic $key binding" }
                CandidateBinding.requireOrdinary(path)
                require(Files.isRegularFile(path) && Hashes.sha256(path) == sha) { "diagnostic $key hash mismatch" }
                return path to sha
            }
            val (artifact, artifactSha) = file("artifact")
            val (evidence, evidenceSha) = file("rejected_evidence")
            val (context, contextSha) = file("source_context")
            require(jarDeclaresMod(artifact, mod.modId)) { "deferred artifact is not Controls" }
            val commit = readSourceCommit(artifact, mod)?.takeIf { it.matches(Regex("[a-f0-9]{40}|[a-f0-9]{64}")) } ?: "UNKNOWN"
            require(node.path("artifact_source_commit").asText() == commit) { "deferred artifact cannot acquire a later source identity" }
            return DiagnosticControls(input, before, artifact, artifactSha, evidence, evidenceSha, context, contextSha, commit)
                .also { it.revalidate() }
        }
    }
}
