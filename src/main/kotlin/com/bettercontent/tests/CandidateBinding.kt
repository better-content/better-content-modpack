package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration

/** Production inputs only. A harness-only repair must not silently select another candidate. */
fun runtimeInputIdentity(root: Path): String {
    val directories = listOf("mods/", "config/", "defaultconfigs/", "datapacks/", "defaultresources/", "globalresources/",
        "kubejs/", "resourcepacks/", "shaderpacks/", "tacz/", "server-config/")
    val files = setOf("options.txt", "server.properties", "pack.toml", "index.toml", "user_jvm_args.txt",
        "run.sh", "run-forge.sh", "better-world-management-server.sh", "package.sh", "dist.sh")
    val names = BoundedCommand.capture(root, listOf("git") +
        com.bettercontent.tests.release.scopedGitInputArgs(directories + files),
        Duration.ofSeconds(30), 4 * 1024 * 1024).split('\u0000').filter { it.isNotBlank() }.distinct().sorted()
        .filter { name -> directories.any(name::startsWith) || name in files }
    val digest = MessageDigest.getInstance("SHA-256")
    names.forEach { name ->
        val path = root.resolve(name)
        val value = if (Files.isSymbolicLink(path)) "link:" + Files.readSymbolicLink(path)
            else if (Files.isRegularFile(path)) Hashes.sha256(path) else "missing"
        digest.update("$name\u0000$value\u0000".toByteArray())
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

object CandidateBinding {
    fun requireOrdinary(path: Path) {
        val absolute = path.toAbsolutePath()
        require(absolute == absolute.normalize()) { "non-normalized path: $path" }
        var ancestor = absolute.root
        for (part in absolute) {
            ancestor = ancestor.resolve(part)
            require(!Files.isSymbolicLink(ancestor)) { "symlink path component: $ancestor" }
        }
    }

    fun select(root: Path, binding: Path, diagnosticInput: Path? =
        System.getenv(com.bettercontent.tests.release.DiagnosticControls.ENV)?.let { Path.of(it) }): CandidatePair {
        requireOrdinary(root)
        requireOrdinary(binding)
        require(!Files.isSymbolicLink(binding) && Files.isRegularFile(binding) && Files.size(binding) <= 1024 * 1024) {
            "invalid candidate binding: $binding"
        }
        val document = jacksonObjectMapper().readTree(binding.toFile())
        if (diagnosticInput == null) require(document.path("schema").asText() == "bc.fresh_dist_provenance.v1" &&
            !document.path("tests_skipped").asBoolean(true)) { "candidate binding must be canonical verified release provenance" }
        val manifest = jacksonObjectMapper().readTree(root.resolve("gradle/active-custom-mods.json").toFile())
        val diagnostic = diagnosticInput?.let { input ->
            val declared = manifest.path("mods").single {
                it.path("repository").asText() == com.bettercontent.tests.release.DiagnosticControls.REPOSITORY
            }
            com.bettercontent.tests.release.DiagnosticControls.load(input,
                jacksonObjectMapper().treeToValue(declared, com.bettercontent.tests.release.ActiveMod::class.java))
        }
        if (diagnostic != null) {
            require(document.path("schema").asText() == com.bettercontent.tests.release.DiagnosticControls.SCHEMA &&
                document.path("qualification").asText() == "DIAGNOSTIC_UNQUALIFIED" &&
                !document.path("tests_skipped").asBoolean(true) && document.path("verification_session").isNull &&
                document.path("deferred_controls") == jacksonObjectMapper().valueToTree<com.fasterxml.jackson.databind.JsonNode>(diagnostic.provenance())) {
                "diagnostic candidate must preserve the exact unqualified Controls binding"
            }
            println("DIAGNOSTIC_UNQUALIFIED candidate: Controls deferred; runtime results cannot establish complete release acceptance")
        }
        require(document.path("runtime_input_sha256").asText() == runtimeInputIdentity(root)) {
            "production/runtime inputs changed; prepare a new canonical candidate"
        }
        val candidates = document.path("candidates")
        fun side(name: String): Path {
            val path = Path.of(candidates.path(name).asText())
            require(path.isAbsolute) { "$name candidate path must be absolute" }
            requireOrdinary(path)
            val relative = root.toAbsolutePath().resolve("dist").relativize(path)
            require(path.startsWith(root.toAbsolutePath().resolve("dist")) && relative.nameCount == 3 &&
                Regex("build-[0-9]+[a-z]?").matches(relative.getName(0).toString()) &&
                relative.getName(1).toString() == name && relative.fileName.toString() == "better-content.zip" &&
                Files.isRegularFile(path) && !Files.isSymbolicLink(path)) {
                "$name candidate is outside canonical dist or missing"
            }
            require(Hashes.sha256(path) == candidates.path(name + "_sha256").asText()) { "$name candidate hash mismatch" }
            return path
        }
        val client = side("client"); val server = side("server")
        require(client.parent.parent == server.parent.parent) { "candidate sides belong to different releases" }
        require(document.path("mods").isArray && document.path("mods").map { it.path("repository").asText() }.toSet() ==
            manifest.path("mods").map { it.path("repository").asText() }.toSet()) { "candidate binding has an incomplete provider set" }
        document.path("mods").forEach { mod ->
            val name = mod.path("repository").asText()
            require(Regex("[a-z0-9-]+").matches(name)) { "invalid repository in candidate binding" }
            if (diagnostic != null) {
                require(document.path("mods").size() == manifest.path("mods").size()) { "duplicate diagnostic provider" }
                if (name == com.bettercontent.tests.release.DiagnosticControls.REPOSITORY) {
                    require(mod.path("mode").asText() == "DEFERRED_UNQUALIFIED" &&
                        mod.path("source_gate_status").asText() == "DEFERRED_UNQUALIFIED" &&
                        mod.path("sha256").asText() == diagnostic.artifactSha256 &&
                        mod.path("commit").asText() == diagnostic.artifactCommit &&
                        mod.path("verification_id").isNull && mod.path("support_hashes").size() == 0) {
                        "Controls cannot be promoted from deferred to verified"
                    }
                    return@forEach
                }
                require(mod.path("source_gate_status").asText() == "PASSED_OWNING_GATES" &&
                    mod.path("diagnostic_gate_run").asText().isNotBlank()) { "$name was not verified for this diagnostic candidate" }
            }
            val source = root.parent.resolve("mod_source/$name")
            require(BoundedCommand.capture(source, listOf("git", "rev-parse", "HEAD"), Duration.ofSeconds(30)).trim() == mod.path("commit").asText() &&
                BoundedCommand.capture(source, listOf("git", "status", "--porcelain"), Duration.ofSeconds(30)).isBlank()) {
                "$name changed since candidate preparation"
            }
            val declared = manifest.path("mods").single { it.path("repository").asText() == name }
                .path("supportArtifacts").map { it.asText() }.toSet()
            val hashes = mod.path("support_hashes")
            require(hashes.fieldNames().asSequence().toSet() == declared) { "$name support artifact binding is incomplete" }
            declared.forEach { relative ->
                val path = source.resolve(relative).normalize()
                requireOrdinary(path)
                require(path.startsWith(source.resolve("build")) && !Files.isSymbolicLink(path) &&
                    Files.isRegularFile(path) && Hashes.sha256(path) == hashes.path(relative).asText()) {
                    "$name support artifact is missing or unverified; canonical preparation is required"
                }
            }
        }
        return CandidatePair(client.parent.parent, client, server,
            candidates.path("client_sha256").asText(), candidates.path("server_sha256").asText())
    }
}
