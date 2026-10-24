package com.bettercontent.tests.release

import com.bettercontent.tests.BoundedCommand
import com.bettercontent.tests.Hashes
import com.bettercontent.tests.RunnerMutex
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.time.Duration

/** Internal backend for test-control source; deliberately no release/session/package entry. */
fun main(args: Array<String>) {
    require(args.size == 3) { "SourceVerification ROOT RUN_ID REPOSITORIES" }
    val root = Path.of(args[0]).toAbsolutePath().normalize()
    require(!System.getProperty("bc.gradle.home").isNullOrBlank()) { "resolved Gradle distribution identity is required" }
    require(System.getenv("BC_VERIFICATION_SESSION").isNullOrBlank() &&
        System.getenv("BC_RELEASE_DIAGNOSTIC_CONTROLS").isNullOrBlank()) { "source control cannot consume sessions or diagnostic artifacts" }
    RunnerMutex.withLock(root) {
        val mapper = jacksonObjectMapper()
        val input = root.resolve("gradle/active-custom-mods.json")
        requireSourceOrdinary(input)
        val inputHash = Hashes.sha256(input)
        val manifest: ActiveModManifest = mapper.readValue(input.toFile())
        val result = verifySourceClosure(root, args[1], manifest, args[2].split(','), inputHash)
        println("source verification: ${result.checks}")
        check(result.checks.values.all { it == "passed" }) { "source qualification failed; see source-verification/result.json" }
    }
}

internal data class SourceVerificationResult(val checks: Map<String, String>, val built: List<BuiltMod>)

internal fun sourceVerificationClosure(manifest: ActiveModManifest, requested: List<String>): List<ActiveMod> {
    require(manifest.schema == "bc.active_custom_mods.v1" && requested.isNotEmpty()) { "invalid source inventory/request" }
    require(manifest.mods.map { it.repository }.distinct().size == manifest.mods.size) { "duplicate source repository" }
    val mods = manifest.mods.associateBy { it.repository }
    val visiting = mutableSetOf<String>()
    val ordered = linkedMapOf<String, ActiveMod>()
    fun include(name: String) {
        require(name.matches(Regex("[a-z0-9][a-z0-9-]{0,127}"))) { "unsafe source repository" }
        if (name in ordered) return
        require(visiting.add(name)) { "source dependency cycle: $name" }
        val mod = requireNotNull(mods[name]) { "unknown source repository: $name" }
        require(mod.artifact.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.+-]*\\.jar")) &&
            mod.modId.matches(Regex("[a-z][a-z0-9_]*"))) { "unsafe source artifact/mod ID" }
        mod.dependsOn.sorted().forEach(::include)
        visiting.remove(name)
        ordered[name] = mod
    }
    requested.sorted().forEach(::include)
    require(ordered.values.map { it.artifact }.distinct().size == ordered.size) { "duplicate source artifact" }
    return ordered.values.toList()
}

/** Check every ancestor, including missing leaf paths, before any source/staging admission. */
internal fun requireSourceOrdinary(path: Path) {
    var current: Path? = path.toAbsolutePath().normalize()
    while (current != null) {
        require(!Files.isSymbolicLink(current)) { "source path contains a symlink: $current" }
        current = current.parent
    }
}

private fun sourceGit(repository: Path, vararg arguments: String): String =
    BoundedCommand.capture(repository, listOf("git") + arguments, Duration.ofSeconds(30))

internal fun requireSourceClean(repository: Path): String {
    requireSourceOrdinary(repository)
    require(Files.isDirectory(repository.resolve(".git"), NOFOLLOW_LINKS)) { "missing ordinary source Git repository: $repository" }
    requireSourceOrdinary(repository.resolve(".git"))
    require(sourceGit(repository, "status", "--porcelain").isEmpty()) { "dirty source repository: $repository" }
    sourceGit(repository, "ls-files", "-z", "--cached", "--others", "--exclude-standard")
        .split('\u0000').filter(String::isNotBlank).forEach { relative ->
            val input = repository.resolve(relative).normalize()
            require(input.startsWith(repository)) { "source input escaped repository" }
            requireSourceOrdinary(input)
        }
    return sourceGit(repository, "rev-parse", "HEAD").trim().also {
        require(it.matches(Regex("[a-f0-9]{40}"))) { "invalid source HEAD" }
    }
}

/** Only the injected seam is used by pure tests. Production always delegates to the common gates. */
internal fun verifySourceClosure(
    root: Path, runId: String, manifest: ActiveModManifest, requested: List<String>,
    expectedManifestHash: String? = null,
    owningBuild: (Path, Path, Path, Path, ActiveMod, SourceUpdate, ProviderInputView) -> BuiltMod = { r, w, e, s, m, u, inputs ->
        build(r, w, e, s, m, u, skipTests = false, forceRebuild = true, inputView = inputs)
    },
): SourceVerificationResult {
    require(runId.matches(Regex("[0-9]{8}T[0-9]{6}Z-[0-9]+"))) { "unsafe source run ID" }
    val mods = sourceVerificationClosure(manifest, requested)
    val workspace = root.parent
    val evidence = root.resolve("generated/test-evidence/$runId/source-verification")
    val staging = root.resolve("generated/test-evidence/$runId/source-jars")
    requireSourceOrdinary(root)
    requireSourceOrdinary(evidence)
    requireSourceOrdinary(staging)
    // Exclusive admission: never overwrite or use an earlier run's stages/evidence.
    Files.createDirectories(evidence.parent)
    Files.createDirectory(evidence)
    Files.createDirectory(staging)
    val manifestPath = root.resolve("gradle/active-custom-mods.json")
    requireSourceOrdinary(manifestPath)
    val manifestHash = Hashes.sha256(manifestPath)
    require(expectedManifestHash == null || manifestHash == expectedManifestHash) { "source inventory changed during admission" }
    val checks = linkedMapOf<String, String>()
    val built = mutableListOf<BuiltMod>()
    val identities = linkedMapOf<String, Pair<String, String>>()
    val allowances = readReflectionAllowlist(root.resolve("gradle/reflection-allowlist.txt"))
    fun unchanged(mod: ActiveMod) {
        val repo = workspace.resolve("mod_source/${mod.repository}")
        val expected = identities.getValue(mod.repository)
        require(requireSourceClean(repo) == expected.first && sourceIdentity(repo) == expected.second) {
            "${mod.repository} source changed during qualification"
        }
        require(Hashes.sha256(manifestPath) == manifestHash) { "source verification inventory changed" }
    }
    fun receipt() {
        jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("result.json").toFile(), mapOf(
            "checks" to checks, "verification_authority" to "independent source task qualification; no canonical checkpoints",
            "runtime_acceptance" to "NOT_EVALUATED", "manifest_sha256" to manifestHash,
            "mods" to built.filter { checks[it.definition.repository] == "passed" }.map { item -> mapOf(
                "repository" to item.definition.repository, "artifact" to item.definition.artifact,
                "commit" to item.commit, "sha256" to item.sha256, "mode" to item.mode,
                "source_gate_status" to "PASSED_OWNING_GATES", "support_hashes" to item.supportHashes,
                "consumed_stages" to item.definition.dependsOn.associateWith { dep -> built.single { it.definition.repository == dep }.sha256 },
                "source_identity" to identities.getValue(item.definition.repository).second,
                "input_view_sha256" to item.inputViewSha256,
            ) },
        ))
    }
    for (mod in mods) {
        try {
            val repo = workspace.resolve("mod_source/${mod.repository}")
            identities[mod.repository] = requireSourceClean(repo) to sourceIdentity(repo)
            requireSafeBuildOutput(repo, repo.resolve("build/libs/${mod.artifact}"))
        } catch (failure: Exception) { checks[mod.repository] = "failed (${failure.message})" }
    }
    for (mod in mods) {
        if (mod.dependsOn.any { checks[it] != "passed" }) checks[mod.repository] = "blocked-by-provider"
        if (mod.repository !in checks) {
            try {
                // Recheck all admitted providers before consuming their stages, not just this HEAD.
                (built.map { it.definition } + mod).forEach(::unchanged)
                built.forEach { item ->
                    requireSourceOrdinary(item.jar)
                    require(Files.isRegularFile(item.jar, NOFOLLOW_LINKS) && Hashes.sha256(item.jar) == item.sha256 &&
                        supportArtifactHashes(workspace.resolve("mod_source/${item.definition.repository}"), item.definition) == item.supportHashes) { "source dependency stage/support changed" }
                }
                val repo = workspace.resolve("mod_source/${mod.repository}")
                require(sourceReflectionViolations(workspace, listOf(mod.repository), allowances).isEmpty()) { "forbidden source reflection" }
                val target = staging.resolve(mod.artifact)
                requireSourceOrdinary(target)
                require(!Files.exists(target, NOFOLLOW_LINKS)) { "source stage already exists" }
                val inputs = providerInputView(manifest, mod, built.associateBy { it.definition.repository },
                    workspace, evidence.resolve("provider-inputs/${mod.repository}"))
                val item = owningBuild(root, workspace, evidence, staging, mod, inspectSourceUpdate(root, workspace, mod), inputs)
                ProviderInputs.verify(inputs)
                requireSourceOrdinary(target)
                requireSafeBuildOutput(repo, repo.resolve("build/libs/${mod.artifact}"))
                unchanged(mod)
                require(item.definition == mod && item.commit == identities.getValue(mod.repository).first &&
                    item.jar == target && item.mode == "rebuilt" && Files.isRegularFile(target, NOFOLLOW_LINKS) &&
                    Hashes.sha256(target) == item.sha256 && readSourceCommit(target, mod) == item.commit &&
                    jarDeclaresMod(target, mod.modId)) { "missing/unsafe/unqualified source stage" }
                require(bytecodeReflectionViolations(mod.repository, repo, target, allowances).isEmpty()) { "forbidden staged reflection" }
                built += item
                checks[mod.repository] = "passed"
            } catch (failure: Exception) { checks[mod.repository] = "failed (${failure.message})" }
        }
        receipt()
    }
    // Changes after an earlier provider's gates invalidate it and every downstream consumer.
    mods.filter { checks[it.repository] == "passed" }.forEach { mod ->
        try {
            unchanged(mod)
            val item = built.single { it.definition.repository == mod.repository }
            requireSourceOrdinary(item.jar)
            require(Files.isRegularFile(item.jar, NOFOLLOW_LINKS) && Hashes.sha256(item.jar) == item.sha256 &&
                supportArtifactHashes(workspace.resolve("mod_source/${mod.repository}"), mod) == item.supportHashes) { "source stage/support changed after owning gates" }
        } catch (failure: Exception) { checks[mod.repository] = "failed (${failure.message})" }
        if (mod.dependsOn.any { checks[it] != "passed" }) checks[mod.repository] = "blocked-by-provider"
    }
    receipt()
    return SourceVerificationResult(checks.toMap(), built.toList())
}
