package com.bettercontent.tests.release

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.CandidateLocator
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText

data class ActiveMod(
    val repository: String,
    val modId: String,
    val artifact: String,
    val tasks: List<String>,
    val dependsOn: List<String> = emptyList(),
    val supportArtifacts: List<String> = emptyList(),
    val verificationInputs: List<String> = emptyList(),
    val buildInputs: List<String>? = null,
    val verificationProfiles: List<VerificationProfile> = emptyList(),
) {
    init {
        if (verificationProfiles.isNotEmpty()) {
            require(verificationProfiles.map { it.name }.distinct().size == verificationProfiles.size) { "duplicate verification profile" }
            require(verificationProfiles.first().clean) { "profile verification must start with the existing clean build" }
            val ordinaryTasks = verificationProfiles.filter { it.properties.isEmpty() }.flatMap { it.tasks }
            require(tasks.all { it in ordinaryTasks }) { "profiles must retain every declared task in the ordinary/default configuration" }
            require(verificationProfiles.last().properties.isEmpty() && verificationProfiles.last().tasks.last() == tasks.last()) {
                "the final artifact must be staged in the ordinary/default configuration"
            }
        }
    }
}

data class VerificationProfile(val name: String, val tasks: List<String>,
    val properties: Map<String, String> = emptyMap(), val clean: Boolean = true) {
    init {
        require(name.matches(Regex("[a-z][a-z0-9-]{0,63}"))) { "unsafe verification profile name" }
        require(tasks.isNotEmpty() && tasks.all { it.matches(Regex(":?[A-Za-z][A-Za-z0-9_.-]*(?::[A-Za-z][A-Za-z0-9_.-]*)*")) }) { "invalid profile task selector" }
        require(properties.all { (key, value) -> key.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,127}")) &&
            value.matches(Regex("[A-Za-z0-9_.:-]{1,128}")) }) { "invalid profile project property" }
    }
}

internal fun verificationProfileIdentity(profiles: List<VerificationProfile>): String = profiles.joinToString("\n") { profile ->
    listOf(profile.name, profile.clean.toString(), profile.tasks.joinToString("\u0001"),
        profile.properties.toSortedMap().entries.joinToString("\u0001") { "${it.key}=${it.value}" }).joinToString("\u0000")
}

data class ActiveModManifest(val schema: String, val mods: List<ActiveMod>,
    val verificationInputs: List<String> = listOf("**"))
data class BuiltMod(val definition: ActiveMod, val commit: String, val jar: Path, val sha256: String, val mode: String,
    val supportHashes: Map<String, String> = emptyMap(), val buildId: String? = null)
data class SourceUpdate(
    val repository: String,
    val artifact: String,
    val localCommit: String,
    val bundledCommit: String?,
    val status: String,
)

private val mapper = jacksonObjectMapper()
private const val SOURCE_METADATA_ENTRY = "META-INF/better-content-source.properties"
private const val SOURCE_METADATA_SCHEMA = "bc.custom_mod_source.v1"

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "release root required" }
    val root = Path.of(args[0]).toAbsolutePath().normalize()
    com.bettercontent.tests.RunnerMutex.withLock(root) { prepareRelease(args) }
}

private fun prepareRelease(args: Array<String>) {
    require(args.size == 4) { "usage: ReleasePipeline ROOT JOBS SKIP_TESTS FORCE_REBUILD" }
    val root = Path.of(args[0]).toAbsolutePath().normalize()
    require(!System.getProperty("bc.gradle.home").isNullOrBlank()) { "resolved Gradle distribution identity is required" }
    val jobs = args[1].toInt()
    val skipTests = args[2].toBooleanStrict()
    val forceRebuild = args[3].toBooleanStrict()
    require(jobs in 1..4) { "release jobs must be between 1 and 4" }
    require(!skipTests || !forceRebuild) { "forced rebuild requires source verification" }
    val workspace = root.parent
    val manifest: ActiveModManifest = mapper.readValue(root.resolve("gradle/active-custom-mods.json").toFile())
    require(manifest.schema == "bc.active_custom_mods.v1") { "unexpected active-mod manifest schema" }
    require(manifest.mods.isNotEmpty()) { "active-mod manifest must contain at least one mod" }
    val repositories = manifest.mods.map { it.repository }.toSet()
    require(repositories.size == manifest.mods.size) { "duplicate repository in active-mod manifest" }
    require(manifest.mods.map { it.modId }.toSet().size == manifest.mods.size) { "duplicate mod ID in active-mod manifest" }
    manifest.mods.forEach { mod ->
        require(mod.repository !in mod.dependsOn) { "${mod.repository} cannot depend on itself" }
        require(mod.dependsOn.all { it in repositories }) { "${mod.repository} has an unknown release dependency" }
    }

    val diagnostic = System.getenv(DiagnosticControls.ENV)?.let { input ->
        require(!skipTests && System.getenv("BC_VERIFICATION_SESSION").isNullOrBlank()) {
            "diagnostic release cannot skip other gates or consume a verified session"
        }
        DiagnosticControls.load(Path.of(input), manifest.mods.single { it.repository == DiagnosticControls.REPOSITORY })
    }
    val diagnosticSources = if (diagnostic == null) emptyMap() else manifest.mods
        .filterNot { it.repository == DiagnosticControls.REPOSITORY }
        .associate { it.repository to sourceIdentity(workspace.resolve("mod_source/${it.repository}")) }
    val diagnosticInputs = if (diagnostic == null) null else com.bettercontent.tests.runtimeInputIdentity(root)
    val diagnosticContracts = if (diagnostic == null) null else diagnosticPackContracts(root, manifest)

    val runId = System.getenv("BC_TEST_RUN_ID")?.takeIf { it.isNotBlank() }
        ?: DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
            .format(Instant.now()) + "-" + ProcessHandle.current().pid()
    val evidence = root.resolve("generated/test-evidence/$runId/release").also { it.createDirectories() }
    val staging = evidence.resolve("staged-jars").also { it.createDirectories() }

    val sourceUpdates = manifest.mods.map { mod -> inspectSourceUpdate(root, workspace, mod) }
    val sourceUpdatesByRepository = sourceUpdates.associateBy { it.repository }
    Files.writeString(
        evidence.resolve("source-updates.tsv"),
        sourceUpdates.joinToString("\n", postfix = "\n") {
            listOf(it.repository, it.artifact, it.status, it.localCommit, it.bundledCommit ?: "-").joinToString("\t")
        },
    )
    println("custom source revision check: " + sourceUpdates.groupingBy { it.status }.eachCount().toSortedMap())

    val preflight = manifest.mods.map { mod ->
        val repository = workspace.resolve("mod_source").resolve(mod.repository)
        if (diagnostic != null && mod.repository == DiagnosticControls.REPOSITORY) "${mod.repository}\tDEFERRED_UNQUALIFIED"
        else if (!repository.resolve(".git").exists()) "${mod.repository}\tmissing" else {
            val status = capture(repository, listOf("git", "status", "--porcelain"))
            if (status.isBlank()) "${mod.repository}\tclean" else "${mod.repository}\tdirty\t${status.replace('\n', ' ')}"
        }
    }
    Files.writeString(evidence.resolve("preflight.tsv"), preflight.joinToString("\n", postfix = "\n"))
    require(preflight.all { it.endsWith("\tclean") || (diagnostic != null && it == "${DiagnosticControls.REPOSITORY}\tDEFERRED_UNQUALIFIED") }) { "all active repositories must exist and be clean; see ${evidence.resolve("preflight.tsv")}" }

    val reflectionAllowances = readReflectionAllowlist(root.resolve("gradle/reflection-allowlist.txt"))
    val customRepositories = Files.list(workspace.resolve("mod_source")).use { stream ->
        stream.filter { Files.isDirectory(it) && it.resolve(".git").exists() }
            .map { it.fileName.toString() }.sorted().toList()
    }
    val unknownReflectionRepositories = reflectionAllowances
        .filterNot { it.repository in customRepositories }
        .sortedWith(compareBy(ReflectionAllowance::repository, ReflectionAllowance::sourcePath))
    require(unknownReflectionRepositories.isEmpty()) {
        "reflection allowlist names repositories outside the custom-mod workspace: " +
            unknownReflectionRepositories.joinToString { "${it.repository}:${it.sourcePath}" }
    }
    val staleReflectionAllowances = reflectionAllowances.filterNot { allowance ->
        Files.isRegularFile(workspace.resolve("mod_source").resolve(allowance.repository).resolve(allowance.sourcePath))
    }.sortedWith(compareBy(ReflectionAllowance::repository, ReflectionAllowance::sourcePath))
    require(staleReflectionAllowances.isEmpty()) {
        "reflection allowlist contains stale source paths: " +
            staleReflectionAllowances.joinToString { "${it.repository}:${it.sourcePath}" }
    }
    val unusedReflectionAllowances = unusedReflectionAllowances(workspace, reflectionAllowances)
    require(unusedReflectionAllowances.isEmpty()) {
        "reflection allowlist contains source paths without reflection: " +
            unusedReflectionAllowances.joinToString { "${it.repository}:${it.sourcePath}" }
    }
    val sourceReflection = sourceReflectionViolations(workspace,
        customRepositories.filterNot { diagnostic != null && it == DiagnosticControls.REPOSITORY }, reflectionAllowances)
    Files.writeString(
        evidence.resolve("reflection-source-audit.tsv"),
        if (sourceReflection.isEmpty()) "PASS\n" else sourceReflection.joinToString("\n", postfix = "\n"),
    )
    require(sourceReflection.isEmpty()) {
        "custom source uses forbidden reflection; see ${evidence.resolve("reflection-source-audit.tsv")}"
    }

    val verification = if (skipTests || diagnostic != null) null else VerificationSession(root, manifest.mods, runId, forceRebuild, sharedInputs = manifest.verificationInputs)
    try {
    val dependencyWarmup = root.resolve("build/release-dependency-warmup/$runId")
    // Run-scoped dependency preparation is retained on success and failure alike.
    runLogged(root, packageResolveCommand(root, dependencyWarmup), evidence.resolve("dependency-cache.log"))

    val built = mutableListOf<BuiltMod>()
    val failures = mutableListOf<String>()
    val pending = manifest.mods.associateBy { it.repository }.toMutableMap()
    while (pending.isNotEmpty() && failures.isEmpty()) {
        val completed = built.map { it.definition.repository }.toSet()
        val completedHashes = built.associate { it.definition.repository to it.sha256 }
        val ready = pending.values.filter { mod -> mod.dependsOn.all { it in completed } }
        require(ready.isNotEmpty()) { "active-mod release dependencies contain a cycle: ${pending.keys.sorted()}" }
        val executor = Executors.newFixedThreadPool(jobs)
        val futures = ready.associateWith { mod ->
            executor.submit(Callable {
                val update = sourceUpdatesByRepository.getValue(mod.repository)
                // Diagnostic mode performs EVERY ordinary/profile gate for all other providers.
                val mustBuild = diagnostic != null || (verification?.affected?.contains(mod.repository) ?: forceRebuild)
                val consumed = mod.dependsOn.associateWith { dependency ->
                    completedHashes.getValue(dependency)
                }
                verification?.requireConsumedStages(mod, consumed)
                val item = if (diagnostic != null && mod.repository == DiagnosticControls.REPOSITORY) {
                    diagnostic.revalidate()
                    val staged = staging.resolve(mod.artifact)
                    Files.copy(diagnostic.artifact, staged)
                    require(Hashes.sha256(staged) == diagnostic.artifactSha256) { "deferred Controls copy changed" }
                    BuiltMod(mod, diagnostic.artifactCommit, staged, diagnostic.artifactSha256, "DEFERRED_UNQUALIFIED")
                } else if (verification != null && !mustBuild) verification.reusable(mod) ?:
                    build(root, workspace, evidence, staging, mod, update.copy(status = "same"), skipTests, false)
                else build(root, workspace, evidence, staging, mod, update, skipTests, mustBuild,
                    if (verification != null && mustBuild) verification.reusableBuild(mod, consumed) else null)
                verification?.requireConsumedStages(mod, consumed)
                if (verification != null && !mustBuild) verification.requireReusable(item)
                val reflection = bytecodeReflectionViolations(mod.repository,
                    workspace.resolve("mod_source/${mod.repository}"), item.jar, reflectionAllowances)
                require(reflection.isEmpty()) {
                    "${mod.repository} staged bytecode uses forbidden reflection: ${reflection.joinToString()}"
                }
                if (verification != null && mustBuild) {
                    verification.checkpoint(item, consumed)
                }
                item
            })
        }
        executor.shutdown()
        futures.forEach { (mod, future) ->
            try {
                built += future.get()
                pending.remove(mod.repository)
            } catch (error: Exception) {
                failures += "${mod.repository}: ${error.cause?.message ?: error.message}"
            }
        }
    }
    if (failures.isNotEmpty()) Files.writeString(evidence.resolve("failures.txt"), failures.joinToString("\n", postfix = "\n"))
    require(failures.isEmpty()) { "custom-mod validation failed; nothing was deployed:\n${failures.joinToString("\n")}" }

    val bytecodeReflection = built.flatMap { item ->
        bytecodeReflectionViolations(
            item.definition.repository,
            workspace.resolve("mod_source").resolve(item.definition.repository),
            item.jar,
            reflectionAllowances,
        )
    }
    Files.writeString(
        evidence.resolve("reflection-bytecode-audit.tsv"),
        if (bytecodeReflection.isEmpty()) "PASS\n" else bytecodeReflection.joinToString("\n", postfix = "\n"),
    )
    require(bytecodeReflection.isEmpty()) {
        "staged custom-mod bytecode uses forbidden reflection; see ${evidence.resolve("reflection-bytecode-audit.tsv")}"
    }

    val verificationOrigins = verification?.validateComplete(built)
    if (diagnostic != null) {
        diagnostic.revalidate()
        require(built.size == manifest.mods.size && built.map { it.definition.repository }.toSet() == repositories) {
            "diagnostic provider set incomplete"
        }
        require(com.bettercontent.tests.runtimeInputIdentity(root) == diagnosticInputs &&
            diagnosticPackContracts(root, manifest) == diagnosticContracts) { "diagnostic consumed pack inputs/contracts changed during preparation" }
        diagnosticSources.forEach { (name, identity) ->
            require(sourceIdentity(workspace.resolve("mod_source/$name")) == identity) { "$name changed during diagnostic preparation" }
        }
    }
    built.forEach { item ->
        require(Hashes.sha256(item.jar) == item.sha256) { "verified staged artifact changed: ${item.jar}" }
        if (!skipTests) require(supportArtifactHashes(workspace.resolve("mod_source/${item.definition.repository}"), item.definition) == item.supportHashes) {
            "verified support artifact changed: ${item.definition.repository}"
        }
    }
    val modsDirectory = root.resolve("mods")
    built.forEach { item ->
        Files.list(modsDirectory).use { stream ->
            stream.filter { it.name.endsWith(".jar") && jarDeclaresMod(it, item.definition.modId) }
                .forEach { existing -> if (existing.fileName.toString() != item.definition.artifact) Files.delete(existing) }
        }
        Files.copy(item.jar, modsDirectory.resolve(item.definition.artifact), StandardCopyOption.REPLACE_EXISTING)
    }
    manifest.mods.forEach { mod ->
        val deployed = modsDirectory.resolve(mod.artifact)
        require(Files.isRegularFile(deployed) && jarDeclaresMod(deployed, mod.modId) &&
            Hashes.sha256(deployed) == built.single { it.definition == mod }.sha256) {
            "deployment lost or misidentified ${mod.artifact}"
        }
    }

    runLogged(root, listOf("packwiz", "refresh"), evidence.resolve("packwiz-refresh.log"))
    runLogged(root, listOf("git", "diff", "--check"), evidence.resolve("diff-check.log"))
    runLogged(root, listOf(root.resolve("dist.sh").toString()), evidence.resolve("dist.log"))
    val candidates = CandidateLocator.locate(root)
    val provenance = mapOf(
        "schema" to if (diagnostic == null) "bc.fresh_dist_provenance.v1" else DiagnosticControls.SCHEMA,
        "qualification" to when { diagnostic != null -> "DIAGNOSTIC_UNQUALIFIED"; skipTests -> "UNTESTED"; else -> "VERIFIED_SOURCE_PENDING_RUNTIME" },
        "deferred_controls" to diagnostic?.provenance(),
        "created_at" to Instant.now().toString(),
        "test_tier" to if (skipTests) "none" else if (System.getenv("BC_RELEASE_DEBUG") == "1") "debug" else if (System.getenv("BC_RELEASE_TARGET") != null) "targeted" else "dist",
        "target" to System.getenv("BC_RELEASE_TARGET"),
        "tests_skipped" to skipTests,
        "force_rebuild" to forceRebuild,
        "verification_session" to verification?.id,
        "full_session_baseline" to verification?.fullBaseline,
        "artifact_reuse" to "exact immutable bytes, source and build identity",
        "verification_reuse" to "original provider verification IDs; current contracts revalidated",
        "runtime_acceptance" to "pending; historical runtime verdicts are never imported",
        "runtime_input_sha256" to com.bettercontent.tests.runtimeInputIdentity(root),
        "candidates" to mapOf(
            "client" to candidates.client.toString(),
            "client_sha256" to candidates.clientSha256,
            "server" to candidates.server.toString(),
            "server_sha256" to candidates.serverSha256,
        ),
        "source_updates" to sourceUpdates.map {
            mapOf(
                "repository" to it.repository,
                "artifact" to it.artifact,
                "status" to it.status,
                "local_commit" to it.localCommit,
                "bundled_commit" to it.bundledCommit,
            )
        },
        "mods" to built.sortedBy { it.definition.repository }.map {
            mapOf(
                "repository" to it.definition.repository,
                "mod_id" to it.definition.modId,
                "commit" to it.commit,
                "artifact" to it.definition.artifact,
                "sha256" to it.sha256,
                "mode" to it.mode,
                "verification_id" to verificationOrigins?.getValue(it.definition.repository),
                "source_gate_status" to if (it.mode == "DEFERRED_UNQUALIFIED") "DEFERRED_UNQUALIFIED" else if (skipTests) "NOT_VERIFIED" else "PASSED_OWNING_GATES",
                "diagnostic_gate_run" to if (diagnostic != null && it.mode != "DEFERRED_UNQUALIFIED") runId else null,
                "build_id" to (it.buildId ?: if (it.mode == "rebuilt") runId else null),
                "support_hashes" to it.supportHashes,
            )
        },
    )
    mapper.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("provenance.json").toFile(), provenance)
    verification?.commit(built)
    println(if (diagnostic == null) "fresh candidate prepared once; tests have not run yet"
        else "DIAGNOSTIC candidate packaged once; Controls DEFERRED_UNQUALIFIED; no complete verified baseline or acceptance")
    println("verification session: ${verification?.id ?: "unverified release"}")
    println("release evidence: $evidence")
    } finally { verification?.close() }
}

internal fun build(
    root: Path,
    workspace: Path,
    evidence: Path,
    staging: Path,
    mod: ActiveMod,
    sourceUpdate: SourceUpdate,
    skipTests: Boolean,
    forceRebuild: Boolean,
    reusableBuild: BuiltMod? = null,
): BuiltMod {
    val repository = workspace.resolve("mod_source").resolve(mod.repository)
    val log = evidence.resolve("mod-${mod.repository}.log")
    val bundled = root.resolve("mods").resolve(mod.artifact)
    val staged = staging.resolve(mod.artifact)
    if (sourceUpdate.status == "same" && !forceRebuild) {
        require(Files.isRegularFile(bundled) && jarDeclaresMod(bundled, mod.modId)) {
            "unchanged ${mod.repository} has no valid bundled artifact to reuse"
        }
        Files.copy(bundled, staged, StandardCopyOption.REPLACE_EXISTING)
        val commit = sourceUpdate.localCommit
        return BuiltMod(mod, commit, staged, Hashes.sha256(staged), "reused",
            if (skipTests) emptyMap() else supportArtifactHashes(repository, mod))
    }
    val initialSource = sourceIdentity(repository)
    val expectedCommit = sourceUpdate.localCommit
    val reusable = reusableBuild.takeIf { mod.verificationProfiles.isEmpty() }
    val profiles = if (!skipTests && mod.verificationProfiles.isNotEmpty()) mod.verificationProfiles else
        listOf(VerificationProfile("default", if (skipTests) listOf("stageRuntimeJar") else mod.tasks, clean = reusable == null))
    val jar = repository.resolve("build/libs").resolve(mod.artifact)
    withForgeCacheLock(root) {
        val tracked = capture(repository, listOf("git", "ls-files", "-z")).split('\u0000').filter(String::isNotBlank).toSet()
        for (profile in profiles) {
            val explicit = !skipTests && mod.verificationProfiles.isNotEmpty()
            val scope = if (explicit) evidence.resolve("source-profiles/${mod.repository}/${profile.name}") else evidence
            Files.createDirectories(scope)
            retainSourceOutputsForVerification(repository, scope, tracked, includeBuild = profile.clean)
            // Simple build-only checkpoints retain their existing restoration path; multi-profile
            // recipes deliberately rebuild and preserve each preceding profile before another clean.
            if (reusable != null) {
                require(!skipTests && reusable.commit == expectedCommit && reusable.buildId != null) {
                    "build reuse requires exact source provenance and fresh verification"
                }
                requireSafeBuildOutput(repository, jar)
                Files.createDirectories(jar.parent)
                Files.copy(reusable.jar, jar, StandardCopyOption.REPLACE_EXISTING)
                require(Hashes.sha256(jar) == reusable.sha256) { "restored build bytes changed" }
            }
            val profileLog = if (explicit) scope.resolve("verification.log") else log
            val command = providerVerificationCommand(repository, profile.tasks, !profile.clean, profile.properties)
            runLogged(repository, if (skipTests) command else cropsSourceInputCommand(root, repository, command),
                profileLog, providerBuildEnvironment(staging))
            auditSourceNativeLogs(repository, scope.resolve("native-log-audits/${mod.repository}/${profile.name}"))
            val output = Files.readString(profileLog)
            if (!skipTests && mod.repository != "better-exploration-load-control" && output.contains(":runGameTestServer")) {
                require(Regex("All [1-9][0-9]* required tests passed").containsMatchIn(output)) {
                    "${mod.repository}/${profile.name} did not report a passing GameTest run; see $profileLog"
                }
            }
            if (explicit) Files.writeString(log, "# verification_profile=${profile.name}\n$output\n",
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
        }
    }
    require(Files.isRegularFile(jar)) { "${mod.repository} did not stage ${mod.artifact}" }
    require(jarDeclaresMod(jar, mod.modId)) { "${mod.artifact} does not declare ${mod.modId}" }
    Files.copy(jar, staged, StandardCopyOption.REPLACE_EXISTING)
    val commit = capture(repository, listOf("git", "rev-parse", "HEAD")).trim()
    require(commit == expectedCommit && sourceIdentity(repository) == initialSource) {
        "${mod.repository} source changed during verification; refusing later-HEAD annotation"
    }
    if (readSourceCommit(staged, mod) != expectedCommit) annotateJar(staged, mod, expectedCommit)
    val sha = Hashes.sha256(staged)
    val support = if (skipTests) emptyMap() else supportArtifactHashes(repository, mod)
    val retained = reusable != null && sha == reusable.sha256 && support == reusable.supportHashes
    return BuiltMod(mod, commit, staged, sha, if (retained) "build-reused-verified" else "rebuilt",
        support, if (retained) reusable!!.buildId else null)
}

internal fun providerVerificationCommand(repository: Path, tasks: List<String>, restoredBuild: Boolean,
    properties: Map<String, String> = emptyMap()): List<String> =
    listOf(repository.resolve("gradlew").toString(), "--no-daemon") +
        (if (restoredBuild) emptyList() else listOf("clean")) +
        properties.toSortedMap().map { (key, value) -> "-P$key=$value" } + tasks

internal fun requireSafeBuildOutput(repository: Path, output: Path) {
    val base = repository.toAbsolutePath().normalize()
    var current = output.toAbsolutePath().normalize()
    require(current.startsWith(base)) { "build output escaped source repository" }
    while (current != base) {
        require(!Files.isSymbolicLink(current)) { "build output contains a symlink" }
        current = current.parent
    }
    require(!Files.isSymbolicLink(base)) { "source repository is a symlink" }
}

internal fun sourceUpdateStatus(localCommit: String, bundledCommit: String?): String = when {
    bundledCommit == null -> "baseline-missing"
    localCommit == bundledCommit -> "same"
    else -> "changed"
}

internal fun inspectSourceUpdate(root: Path, workspace: Path, mod: ActiveMod): SourceUpdate {
    val repository = workspace.resolve("mod_source").resolve(mod.repository)
    val localCommit = if (repository.resolve(".git").exists()) {
        capture(repository, listOf("git", "rev-parse", "HEAD")).trim()
    } else {
        "missing"
    }
    val bundled = root.resolve("mods").resolve(mod.artifact)
    val bundledCommit = readSourceCommit(bundled, mod)
    return SourceUpdate(mod.repository, mod.artifact, localCommit, bundledCommit, sourceUpdateStatus(localCommit, bundledCommit))
}

internal fun readSourceCommit(path: Path, mod: ActiveMod): String? = runCatching {
    if (!Files.isRegularFile(path)) return@runCatching null
    JarFile(path.toFile()).use { jar ->
        val entry = jar.getJarEntry(SOURCE_METADATA_ENTRY) ?: return@use null
        val properties = java.util.Properties()
        jar.getInputStream(entry).use(properties::load)
        if (properties.getProperty("schema") != SOURCE_METADATA_SCHEMA ||
            properties.getProperty("repository") != mod.repository ||
            properties.getProperty("mod_id") != mod.modId
        ) return@use null
        properties.getProperty("commit")?.takeIf { it.isNotBlank() }
    }
}.getOrNull()

internal fun annotateJar(path: Path, mod: ActiveMod, commit: String) {
    val temporary = path.resolveSibling(".${path.fileName}.source-metadata.tmp")
    val metadata = """
        schema=$SOURCE_METADATA_SCHEMA
        repository=${mod.repository}
        mod_id=${mod.modId}
        commit=$commit
    """.trimIndent() + "\n"
    JarFile(path.toFile()).use { source ->
        JarOutputStream(Files.newOutputStream(temporary)).use { output ->
            source.entries().asSequence()
                .filter { it.name != SOURCE_METADATA_ENTRY }
                .forEach { entry ->
                    output.putNextEntry(JarEntry(entry))
                    source.getInputStream(entry).use { it.copyTo(output) }
                    output.closeEntry()
                }
            output.putNextEntry(java.util.jar.JarEntry(SOURCE_METADATA_ENTRY))
            output.write(metadata.toByteArray(Charsets.UTF_8))
            output.closeEntry()
        }
    }
    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
}

internal fun jarDeclaresMod(path: Path, modId: String): Boolean = runCatching {
    JarFile(path.toFile()).use { jar ->
        val entry = jar.getJarEntry("META-INF/mods.toml") ?: return@use false
        jar.getInputStream(entry).bufferedReader().use { reader ->
            var inModDeclaration = false
            reader.lineSequence().any { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("[[") && trimmed.endsWith("]]")) {
                    inModDeclaration = trimmed == "[[mods]]"
                    false
                } else {
                    inModDeclaration && Regex("""modId\s*=\s*[\"']${Regex.escape(modId)}[\"']""").matches(trimmed)
                }
            }
        }
    }
}.getOrDefault(false)

internal fun packageResolveCommand(root: Path, target: Path): List<String> = listOf(
    root.resolve("package.sh").toString(),
    "resolve",
    root.toString(),
    target.toString(),
    "client",
)

internal fun providerBuildEnvironment(staging: Path): Map<String, String> =
    mapOf("BC_CUSTOM_MOD_JAR_DIR" to staging.toAbsolutePath().normalize().toString())

internal fun cropsSourceInputCommand(root: Path, repository: Path, command: List<String>): List<String> =
    if (repository.fileName.toString() != "better-bumblezone-crops") command else
        listOf("python3", "-B", root.resolve("scripts/crops_source_inputs.py").toString(),
            "--root", root.toAbsolutePath().normalize().toString(),
            "--repository", repository.toAbsolutePath().normalize().toString(), "--") + command

private fun runLogged(cwd: Path, command: List<String>, log: Path, environment: Map<String, String> = emptyMap()) {
    log.parent.createDirectories()
    val status = com.bettercontent.tests.BoundedCommand.run(cwd, command, log,
        java.time.Duration.ofMinutes(45), environment)
    require(status == 0) { "command failed ($status): ${command.joinToString(" ")}; see $log" }
}

private fun capture(cwd: Path, command: List<String>): String {
    return com.bettercontent.tests.BoundedCommand.capture(cwd, command, java.time.Duration.ofSeconds(30))
}
