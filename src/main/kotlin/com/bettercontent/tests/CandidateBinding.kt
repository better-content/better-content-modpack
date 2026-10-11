package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration

/** Production inputs only. A harness-only repair must not silently select another candidate. */
fun runtimeInputIdentity(root: Path): String {
    val names = BoundedCommand.capture(root, listOf("git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"),
        Duration.ofSeconds(30), 4 * 1024 * 1024).split('\u0000').filter { it.isNotBlank() }.distinct().sorted()
        .filter { name ->
            listOf("mods/", "config/", "defaultconfigs/", "datapacks/", "defaultresources/", "globalresources/",
                "kubejs/", "resourcepacks/", "shaderpacks/", "tacz/", "server-config/").any(name::startsWith) ||
                name in setOf("options.txt", "server.properties", "pack.toml", "index.toml", "user_jvm_args.txt",
                    "run.sh", "run-forge.sh", "better-world-management-server.sh", "package.sh", "dist.sh")
        }
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
    fun select(root: Path, binding: Path): CandidatePair {
        require(!Files.isSymbolicLink(binding) && Files.isRegularFile(binding) && Files.size(binding) <= 1024 * 1024) {
            "invalid candidate binding: $binding"
        }
        val document = jacksonObjectMapper().readTree(binding.toFile())
        require(document.path("schema").asText() == "bc.fresh_dist_provenance.v1" && !document.path("tests_skipped").asBoolean(true)) {
            "candidate binding must be canonical verified release provenance"
        }
        require(document.path("runtime_input_sha256").asText() == runtimeInputIdentity(root)) {
            "production/runtime inputs changed; prepare a new canonical candidate"
        }
        val candidates = document.path("candidates")
        fun side(name: String): Path {
            val path = Path.of(candidates.path(name).asText()).toAbsolutePath().normalize()
            require(path.startsWith(root.resolve("dist")) && Files.isRegularFile(path) && !Files.isSymbolicLink(path)) {
                "$name candidate is outside canonical dist or missing"
            }
            require(Hashes.sha256(path) == candidates.path(name + "_sha256").asText()) { "$name candidate hash mismatch" }
            return path
        }
        val client = side("client"); val server = side("server")
        require(client.parent.parent == server.parent.parent) { "candidate sides belong to different releases" }
        val manifest = jacksonObjectMapper().readTree(root.resolve("gradle/active-custom-mods.json").toFile())
        require(document.path("mods").isArray && document.path("mods").map { it.path("repository").asText() }.toSet() ==
            manifest.path("mods").map { it.path("repository").asText() }.toSet()) { "candidate binding has an incomplete provider set" }
        document.path("mods").forEach { mod ->
            val name = mod.path("repository").asText()
            require(Regex("[a-z0-9-]+").matches(name)) { "invalid repository in candidate binding" }
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
