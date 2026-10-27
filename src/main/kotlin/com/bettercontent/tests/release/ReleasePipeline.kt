package com.bettercontent.tests.release

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.CandidateLocator
import com.bettercontent.tests.CandidatePair
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
)

data class ActiveModManifest(val schema: String, val mods: List<ActiveMod>)
data class BuiltMod(val definition: ActiveMod, val commit: String, val jar: Path, val sha256: String, val mode: String)
data class SourceUpdate(
    val repository: String,
    val artifact: String,
    val localCommit: String,
    val bundledCommit: String?,
    val status: String,
    val productionFingerprint: String? = null,
)

private val mapper = jacksonObjectMapper()
private const val SOURCE_METADATA_ENTRY = "META-INF/better-content-source.properties"
private const val SOURCE_METADATA_SCHEMA = "bc.custom_mod_source.v1"

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--finalize-visual") {
        require(args.size == 4) { "usage: ReleasePipeline --finalize-visual ROOT PENDING REVIEW" }
        finalizeVisualRelease(Path.of(args[1]).toAbsolutePath().normalize(), Path.of(args[2]), Path.of(args[3]))
        return
    }
    require(args.size in 4..5) { "usage: ReleasePipeline ROOT JOBS SOAK_MINUTES USE_BUNDLED_MODS [FRESH_SOURCE_PLAN]" }
    if (args.getOrNull(4)?.isNotBlank() == true) {
        require(args[2] == "0" && args[3] == "false") { "fresh-source release cannot use bundled inputs or ordinary Soak" }
        runFreshSourceRelease(Path.of(args[0]).toAbsolutePath().normalize(), args[1].toInt(), Path.of(args[4]))
        return
    }
    val root = Path.of(args[0]).toAbsolutePath().normalize()
    val jobs = args[1].toInt()
    val soakMinutes = args[2].toInt()
    val useBundledMods = args[3].toBooleanStrict()
    require(jobs in 1..4) { "release jobs must be between 1 and 4" }
    require(soakMinutes in 0..10080) { "soak minutes must be between 0 and 10080" }
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

    val runId = System.getenv("BC_TEST_RUN_ID")?.takeIf { it.isNotBlank() }
        ?: DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
            .format(Instant.now()) + "-" + ProcessHandle.current().pid()
    val evidence = root.resolve("generated/test-evidence/$runId/release").also { it.createDirectories() }
    val staging = evidence.resolve("staged-jars").also { it.createDirectories() }
    val smokeEvidence = root.resolve("generated/test-evidence/$runId/smoke/run.json")
    require(!smokeEvidence.exists()) { "refusing pre-existing Smoke evidence: $smokeEvidence" }
    val compileFixtures = if (useBundledMods) null else System.getenv("BC_RELEASE_COMPILE_FIXTURE_DIR")?.let {
        require(it.isNotBlank()) { "BC_RELEASE_COMPILE_FIXTURE_DIR must not be blank" }
        Path.of(it).toAbsolutePath().normalize()
    }
    val providers = seedProviderStaging(root.resolve("mods"), staging, compileFixtures)
    Files.writeString(evidence.resolve("provider-inputs.tsv"), providers.joinToString("\n", postfix = "\n") {
        "${it.name}\t${Hashes.sha256(it)}"
    })
    val sourceUpdates = manifest.mods.map { mod ->
        if (useBundledMods) SourceUpdate(mod.repository, mod.artifact, "not-inspected",
            readSourceCommit(root.resolve("mods").resolve(mod.artifact), mod), "bundled-input")
        else inspectSourceUpdate(root, workspace, mod)
    }
    val sourceUpdatesByRepository = sourceUpdates.associateBy { it.repository }
    Files.writeString(
        evidence.resolve("source-updates.tsv"),
        sourceUpdates.joinToString("\n", postfix = "\n") {
            listOf(it.repository, it.artifact, it.status, it.localCommit, it.bundledCommit ?: "-").joinToString("\t")
        },
    )
    println("custom source revision check: " + sourceUpdates.groupingBy { it.status }.eachCount().toSortedMap())

    if (!useBundledMods) {
        val dependencyWarmup = root.resolve("build/release-dependency-warmup/$runId")
        try {
            runLogged(root, packageResolveCommand(root, dependencyWarmup), evidence.resolve("dependency-cache.log"))
        } finally {
            dependencyWarmup.toFile().deleteRecursively()
        }
    }

    val built = mutableListOf<BuiltMod>()
    if (useBundledMods) manifest.mods.forEach { mod ->
        val jar = staging.resolve(mod.artifact)
        require(Files.isRegularFile(jar) && jarDeclaresMod(jar, mod.modId)) { "missing or misidentified bundled runtime input: ${mod.artifact}" }
        built += BuiltMod(mod, readSourceCommit(jar, mod) ?: "unknown", jar, Hashes.sha256(jar), "bundled-input")
    }
    val failures = mutableListOf<String>()
    val pending = (if (useBundledMods) emptyMap() else manifest.mods.associateBy { it.repository }).toMutableMap()
    while (pending.isNotEmpty() && failures.isEmpty()) {
        val completed = built.map { it.definition.repository }.toSet()
        val rebuiltRepositories = built.filter { it.mode == "rebuilt" }.map { it.definition.repository }.toSet()
        val ready = pending.values.filter { mod -> mod.dependsOn.all { it in completed } }
        require(ready.isNotEmpty()) { "active-mod release dependencies contain a cycle: ${pending.keys.sorted()}" }
        val executor = Executors.newFixedThreadPool(jobs)
        val futures = ready.associateWith { mod ->
            executor.submit(Callable {
                build(root, workspace, evidence, staging, mod, sourceUpdatesByRepository.getValue(mod.repository).let { update ->
                    if (mod.dependsOn.any { it in rebuiltRepositories })
                        update.copy(status = "dependency-changed") else update
                })
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

    sourceUpdates.filter { it.productionFingerprint != null }.forEach { update ->
        require(productionFingerprint(workspace.resolve("mod_source").resolve(update.repository)) == update.productionFingerprint) {
            "production inputs changed while staging: ${update.repository}; retry after edits settle"
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
        require(Files.isRegularFile(deployed) && jarDeclaresMod(deployed, mod.modId)) {
            "deployment lost or misidentified ${mod.artifact}"
        }
    }

    runLogged(root, listOf("packwiz", "refresh"), evidence.resolve("packwiz-refresh.log"))
    runLogged(root, listOf("git", "diff", "--check"), evidence.resolve("diff-check.log"))
    val previousReleases = releaseDirectories(root)
    runLogged(root, listOf(root.resolve("dist.sh").toString()), evidence.resolve("dist.log"))
    val candidates = locateNewRelease(root, previousReleases)
    val ready = candidates.release.resolve("ready.json")
    require(!ready.exists()) { "new packaged release unexpectedly has a ready marker" }
    val provenance = mapOf(
        "schema" to "bc.fresh_dist_provenance.v1",
        "created_at" to Instant.now().toString(),
        "test_tier" to "smoke",
        "runtime_inputs" to if (useBundledMods) "bundled" else "source-aware",
        "soak_minutes" to soakMinutes,
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
                "production_fingerprint" to it.productionFingerprint,
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
            )
        },
    )
    mapper.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("provenance.json").toFile(), provenance)
    val started = Instant.now()
    fun runTier(tier: String) {
        requireStableCandidates(candidates)
        val command = listOf(root.resolve("test.main.kts").toString(), tier,
            "--client", candidates.client.toString(), "--server", candidates.server.toString()) +
            if (tier == "soak") listOf("--minutes", soakMinutes.toString()) else emptyList()
        runLogged(root, command, evidence.resolve("$tier.log"), mapOf("BC_TEST_RUN_ID" to runId))
        requireStableCandidates(candidates)
    }
    runTier("smoke")
    validateSmokeEvidence(smokeEvidence, runId, candidates, started)
    if (soakMinutes > 0) {
        runTier("soak")
        validateSmokeEvidence(root.resolve("generated/test-evidence/$runId/soak/run.json"), runId, candidates, started, "soak")
    }
    // Recheck the gate after optional Soak; never package again after acceptance.
    validateSmokeEvidence(smokeEvidence, runId, candidates, started)
    ready.parent.createDirectories()
    val readyTemporary = ready.resolveSibling(".ready.json.tmp")
    mapper.writerWithDefaultPrettyPrinter().writeValue(readyTemporary.toFile(), mapOf(
        "schema" to "bc.release_ready.v1", "run_id" to runId, "status" to "passed",
        "created_at" to Instant.now().toString(), "smoke_evidence" to smokeEvidence.toString(),
        "soak_minutes" to soakMinutes, "candidates" to provenance.getValue("candidates")))
    Files.move(readyTemporary, ready, StandardCopyOption.ATOMIC_MOVE)
    println("release ready: $ready (exact packaged pair passed Smoke)")
    println("release evidence: $evidence")
}

internal fun releaseDirectories(root: Path): Set<Path> {
    val dist = root.resolve("dist")
    if (!Files.isDirectory(dist)) return emptySet()
    return Files.list(dist).use { paths ->
        paths.filter { Files.isDirectory(it, java.nio.file.LinkOption.NOFOLLOW_LINKS) }
            .map { it.toAbsolutePath().normalize() }.toList().toSet()
    }
}

internal fun locateNewRelease(root: Path, previous: Set<Path>): CandidatePair {
    val created = releaseDirectories(root) - previous
    require(created.size == 1) { "packaging must create exactly one new release directory: $created" }
    val release = created.single()
    val client = release.resolve("client/better-content.zip")
    val server = release.resolve("server/better-content.zip")
    require(Files.isRegularFile(client) && Files.isRegularFile(server)) { "new release has an incomplete ZIP pair: $release" }
    return CandidateLocator.locate(client, server)
}

internal fun build(
    root: Path,
    workspace: Path,
    evidence: Path,
    staging: Path,
    mod: ActiveMod,
    sourceUpdate: SourceUpdate,
    nativeLock: Path = Path.of(System.getProperty("user.home"), ".tmp", "better-content-gradle.lock"),
): BuiltMod {
    val repository = workspace.resolve("mod_source").resolve(mod.repository)
    val log = evidence.resolve("mod-${mod.repository}.log")
    val bundled = root.resolve("mods").resolve(mod.artifact)
    val staged = staging.resolve(mod.artifact)
    val fingerprint = productionFingerprint(repository)
    require(sourceUpdate.productionFingerprint == null || sourceUpdate.productionFingerprint == fingerprint) {
        "production inputs changed during release preparation: ${mod.repository}"
    }
    if (sourceUpdate.status == "same") {
        require(Files.isRegularFile(bundled) && jarDeclaresMod(bundled, mod.modId)) {
            "unchanged ${mod.repository} has no valid bundled artifact to reuse"
        }
        Files.copy(bundled, staged, StandardCopyOption.REPLACE_EXISTING)
        val commit = sourceUpdate.localCommit
        return BuiltMod(mod, commit, staged, Hashes.sha256(staged), "reused")
    }
    Files.createDirectories(nativeLock.parent)
    val buildCommand = listOf("flock", "--wait", "1800", nativeLock.toString(),
        repository.resolve("gradlew").toString(), "--no-daemon", "--max-workers=2", "stageRuntimeJar")
    runLogged(repository, buildCommand, log, providerBuildEnvironment(staging))
    val jar = repository.resolve("build/libs").resolve(mod.artifact)
    require(Files.isRegularFile(jar)) { "${mod.repository} did not stage ${mod.artifact}" }
    require(jarDeclaresMod(jar, mod.modId)) { "${mod.artifact} does not declare ${mod.modId}" }
    Files.copy(jar, staged, StandardCopyOption.REPLACE_EXISTING)
    val commit = capture(repository, listOf("git", "rev-parse", "HEAD")).trim()
    require(productionFingerprint(repository) == fingerprint) { "production inputs changed during build: ${mod.repository}" }
    annotateJar(staged, mod, commit, fingerprint)
    return BuiltMod(mod, commit, staged, Hashes.sha256(staged), "rebuilt")
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
    require(localCommit != "missing") { "missing source repository: $repository" }
    val fingerprint = productionFingerprint(repository)
    val bundledFingerprint = readSourceProperties(bundled, mod)?.getProperty("production_fingerprint")
    val status = when {
        bundledCommit == null -> "baseline-missing"
        bundledFingerprint != null -> if (bundledFingerprint == fingerprint) "same" else "changed"
        bundledCommit == localCommit && productionInputsUnchangedSinceHead(repository) -> "same"
        else -> "changed"
    }
    return SourceUpdate(mod.repository, mod.artifact, localCommit, bundledCommit, status, fingerprint)
}

internal fun readSourceCommit(path: Path, mod: ActiveMod): String? =
    readSourceProperties(path, mod)?.getProperty("commit")?.takeIf { it.isNotBlank() }

private fun readSourceProperties(path: Path, mod: ActiveMod): java.util.Properties? = runCatching {
    if (!Files.isRegularFile(path)) return@runCatching null
    JarFile(path.toFile()).use { jar ->
        val entry = jar.getJarEntry(SOURCE_METADATA_ENTRY) ?: return@use null
        val properties = java.util.Properties()
        jar.getInputStream(entry).use(properties::load)
        properties.takeIf { it.getProperty("schema") == SOURCE_METADATA_SCHEMA &&
            it.getProperty("repository") == mod.repository && it.getProperty("mod_id") == mod.modId }
    }
}.getOrNull()

internal fun annotateJar(path: Path, mod: ActiveMod, commit: String, fingerprint: String? = null) {
    val temporary = path.resolveSibling(".${path.fileName}.source-metadata.tmp")
    val metadata = """
        schema=$SOURCE_METADATA_SCHEMA
        repository=${mod.repository}
        mod_id=${mod.modId}
        commit=$commit
    """.trimIndent() + "\n" + (fingerprint?.let { "production_fingerprint=$it\n" } ?: "")
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

// Conservative production boundary: unknown build inputs count; documentation and test-only
// trees do not. Untracked, non-ignored production files participate just like tracked files.
internal fun isProductionInput(path: String): Boolean {
    val parts = path.split('/')
    val testTrees = setOf("test", "tests", "gametest", "gameTest", "testFixtures")
    // Runtime resources can themselves be Markdown or reside in a package named tests.
    if (parts.first() == "src") return parts.getOrNull(1) !in testTrees
    if (parts.first() in setOf("build", ".gradle", ".git", "docs", "generated", "logs", "run", "runs") + testTrees) return false
    return !path.endsWith(".md", ignoreCase = true) && !path.endsWith(".log") &&
        path !in setOf(".gitignore", ".gitattributes", "LICENSE", "LICENSE.txt")
}

internal fun productionFingerprint(repository: Path): String = fingerprintInputs(repository, ::isProductionInput)

internal fun sourceVerificationFingerprint(repository: Path): String = fingerprintInputs(repository, { path ->
    isProductionInput(path) || path.startsWith("src/") || path.substringBefore('/') in
        setOf("test", "tests", "gametest", "gameTest", "testFixtures") || path in setOf("AGENTS.md", ".gitignore", ".gitattributes")
}, includeIgnoredSourceInputs = true)

private fun fingerprintInputs(repository: Path, included: (String) -> Boolean, includeIgnoredSourceInputs: Boolean = false): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val listed = capture(repository, listOf("git", "ls-files", "-z", "--cached", "--others", "--exclude-standard")) +
        if (includeIgnoredSourceInputs) capture(repository, listOf("git", "ls-files", "-z", "--others", "--ignored", "--exclude-standard", "--",
            "src", "test", "tests", "gametest", "gameTest", "testFixtures", "AGENTS.md", "gradle", "gradlew", "gradlew.bat",
            "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle.properties", "scripts")) else ""
    val paths = listed.split('\u0000').filter { it.isNotEmpty() && included(it) }.distinct().sorted()
    paths.forEach { relative ->
        digest.update(relative.toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        val file = repository.resolve(relative)
        digest.update(if (Files.isExecutable(file)) 1.toByte() else 0.toByte())
        if (Files.isSymbolicLink(file)) digest.update(Files.readSymbolicLink(file).toString().toByteArray(Charsets.UTF_8))
        if (Files.isRegularFile(file)) {
            digest.update(1.toByte())
            Files.newInputStream(file).use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
        } else digest.update(2.toByte()) // A tracked deletion is itself an input change.
        digest.update(0.toByte())
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun productionInputsUnchangedSinceHead(repository: Path): Boolean {
    val changed = capture(repository, listOf("git", "diff", "--name-only", "--no-renames", "-z", "HEAD", "--")) +
        capture(repository, listOf("git", "ls-files", "-z", "--others", "--exclude-standard"))
    return changed.split('\u0000').none { it.isNotEmpty() && isProductionInput(it) }
}

internal fun requireStableCandidates(candidates: CandidatePair) {
    require(Hashes.sha256(candidates.client) == candidates.clientSha256 &&
        Hashes.sha256(candidates.server) == candidates.serverSha256) { "release candidate hashes changed" }
}

internal fun validateSmokeEvidence(path: Path, runId: String, candidates: CandidatePair, started: Instant, tier: String = "smoke") {
    require(tier in setOf("smoke", "soak")) { "unknown runtime tier" }
    require(Files.isRegularFile(path)) { "missing successful $tier evidence: $path" }
    require(!Files.getLastModifiedTime(path).toInstant().isBefore(started.truncatedTo(java.time.temporal.ChronoUnit.SECONDS))) {
        "stale $tier evidence: $path"
    }
    val result = mapper.readTree(path.toFile())
    require(result.path("run_id").asText() == runId && result.path("tier").asText() == tier &&
        result.path("status").asText() == "passed") { "$tier evidence is not a successful result for release $runId" }
    val pair = result.path("candidates")
    fun matches(name: String, expected: Path): Boolean = runCatching {
        Path.of(pair.path(name).asText()).toAbsolutePath().normalize() == expected.toAbsolutePath().normalize()
    }.getOrDefault(false)
    require(matches("client", candidates.client) && matches("server", candidates.server) &&
        pair.path("client_sha256").asText() == candidates.clientSha256 &&
        pair.path("server_sha256").asText() == candidates.serverSha256) { "$tier evidence does not match the exact release pair" }
    requireStableCandidates(candidates)
}

internal fun packageResolveCommand(root: Path, target: Path): List<String> = listOf(
    root.resolve("package.sh").toString(),
    "resolve",
    root.toString(),
    target.toString(),
    "client",
)

// Compile-only fixtures are explicit inputs, never added to the active runtime deployment list.
// Bundled providers seed the full compile classpath; ordered active builds replace their own JARs.
internal fun seedProviderStaging(bundled: Path, staging: Path, compileFixtures: Path? = null): List<Path> {
    staging.createDirectories()
    fun copyDirectory(directory: Path, optional: Boolean) {
        require(Files.isDirectory(directory)) { "missing provider directory: $directory" }
        Files.list(directory).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.name.endsWith(".jar") }.sorted().forEach { source ->
                val target = staging.resolve(source.fileName)
                require(!optional || !target.exists() || Hashes.sha256(source) == Hashes.sha256(target)) {
                    "compile-only fixture must not override bundled provider: ${source.name}"
                }
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
    copyDirectory(bundled, false)
    compileFixtures?.let { copyDirectory(it, true) }
    return Files.list(staging).use { paths -> paths.filter { it.name.endsWith(".jar") }.sorted().toList() }
}

internal fun providerBuildEnvironment(staging: Path): Map<String, String> =
    mapOf("BC_CUSTOM_MOD_JAR_DIR" to staging.toAbsolutePath().normalize().toString())

private fun runLogged(cwd: Path, command: List<String>, log: Path, environment: Map<String, String> = emptyMap()) {
    log.parent.createDirectories()
    val process = ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).redirectOutput(log.toFile())
        .apply { environment().putAll(environment) }.start()
    val status = process.waitFor()
    require(status == 0) { "command failed ($status): ${command.joinToString(" ")}; see $log" }
}

// Fresh-source releases are deliberately a separate opt-in contract. Legacy receipts and
// accepted distributions are never upgraded or reinterpreted by this path.
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy::class)
data class FreshInputSnapshot(val path: String, val sha256: String, val kind: String = "file")

@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy::class)
data class QualifiedFreshSource(
    val repository: String,
    val head: String,
    val productionFingerprint: String,
    val sourceVerificationFingerprint: String,
    val rawSha256: String,
    val qualification: FreshInputSnapshot,
    val providerSha256: Map<String, String> = emptyMap(),
    val externalInputs: List<FreshInputSnapshot> = emptyList(),
    val gradleProperties: Map<String, String> = emptyMap(),
)

@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy::class)
data class FreshSourcePlan(
    val schema: String,
    val sources: List<QualifiedFreshSource>,
    val thirdPartyPins: Map<String, String>,
    val qualificationNotBeforeUtc: String,
    val createdUtc: String,
)

internal class FreshReleaseBlocked(message: String) : IllegalStateException(message)
private const val FROZEN_WLM_REPOSITORY = "better-world-management"
private const val FROZEN_WLM_ARTIFACT = "better-world-management-0.1.0.jar"
private const val FROZEN_WLM_SHA256 = "a212e5145e138f3108442796bf5a5a73e0666d9590da20c1822e532ed5fc5a8d"
private const val FROZEN_SUPERVISOR_SHA256 = "693e9073c91f935c96b58146c515a18baf798204da76d6bc849197c4778fc381"
private const val ACCEPTED_BUILD1_SERVER_SHA256 = "90118aea757e7ee2478d43220523ac5443dbaf3a7bdcdc3106d9d6dfa1466bdf"
private val frozenLaunchers = mapOf(
    "run.sh" to "149f4b4af44cd9b3001174346a8788561c205a61014ada6df780ceb6b6e27c7b",
    "run-forge.sh" to "848479c8d43aae0a94beac540727b763826620a2920d4ac7f6738732feaaf78c",
)

/** Stable provenance reference for the user's standing full-pack EULA/world approval.
 * Approval is recorded in AGENTS.md; no per-run environment token or plan claim is required. */
internal fun requireFreshRuntimeAuthorization(@Suppress("UNUSED_PARAMETER") environment: Map<String, String>): String =
    "standing-full-pack-approval-2026-10-27"

internal fun validateFreshQualificationCohort(plan: FreshSourcePlan, environment: Map<String, String>): Pair<Instant, Instant> {
    val request = environment["BC_FRESH_SOURCE_REQUEST_NOT_BEFORE_UTC"]?.takeIf { it.isNotBlank() }
        ?: throw FreshReleaseBlocked("fresh release blocked: externally recorded current-request lower bound is absent")
    require(request.endsWith("Z") && plan.qualificationNotBeforeUtc.endsWith("Z") && plan.createdUtc.endsWith("Z")) { "fresh cohort boundaries must be ISO UTC instants" }
    val floor = Instant.parse(request)
    val start = Instant.parse(plan.qualificationNotBeforeUtc)
    val created = Instant.parse(plan.createdUtc)
    require(!start.isBefore(floor) && !created.isBefore(start) && !created.isAfter(Instant.now())) {
        "fresh qualification cohort predates the independently recorded request or has invalid chronology"
    }
    return start to created
}

internal fun freshDependencyOrder(mods: List<ActiveMod>): List<ActiveMod> {
    require(mods.map { it.repository }.toSet().size == mods.size) { "duplicate source repository" }
    val pending = mods.associateBy { it.repository }.toMutableMap()
    val ordered = mutableListOf<ActiveMod>()
    while (pending.isNotEmpty()) {
        val ready = pending.values.filter { mod -> mod.dependsOn.all { dependency -> ordered.any { it.repository == dependency } } }
        require(ready.isNotEmpty()) { "missing provider or cyclic fresh source dependency: ${pending.keys}" }
        ready.forEach { ordered += it; pending.remove(it.repository) }
    }
    return ordered
}

internal fun validateFreshSourcePopulation(manifest: ActiveModManifest, plan: FreshSourcePlan) {
    require(manifest.schema == "bc.active_custom_mods.v1" && plan.schema == "bc.fresh_custom_sources.v1") { "unsupported fresh-source manifest/plan schema" }
    require(manifest.mods.size == 49 && manifest.mods.map { it.repository }.toSet().size == 49 &&
        manifest.mods.map { it.modId }.toSet().size == 49 && manifest.mods.map { it.artifact }.toSet().size == 49) {
        "fresh source release requires exactly 49 distinct active mods (48 fresh plus frozen WLM)"
    }
    require(manifest.mods.any { it.repository == "better-spicy-farmers" }) { "Spicy Farmers must be explicitly active" }
    require(manifest.mods.none { it.repository == "better-finite-water" }) { "Finite Water is not an authorized runtime input" }
    val frozen = manifest.mods.single { it.repository == FROZEN_WLM_REPOSITORY }
    require(frozen.artifact == FROZEN_WLM_ARTIFACT && frozen.dependsOn.isEmpty()) { "frozen WLM identity/provider changed" }
    require(plan.sources.size == 48 && plan.sources.map { it.repository }.toSet() ==
        manifest.mods.filterNot { it.repository == FROZEN_WLM_REPOSITORY }.map { it.repository }.toSet()) {
        "exactly all 48 non-WLM active sources must be freshly built; no bundled source substitution"
    }
    val physiology = manifest.mods.single { it.repository == "better-survival-physiology" }
    require(physiology.dependsOn.containsAll(listOf("better-drinking-water", "better-spicy-farmers"))) {
        "Physiology must follow its typed Water and Spice providers"
    }
    freshDependencyOrder(manifest.mods)
}

internal fun validateFreshSourceBindings(manifest: ActiveModManifest, plan: FreshSourcePlan) {
    val definitions = manifest.mods.associateBy { it.repository }
    val sources = plan.sources.associateBy { it.repository }
    plan.sources.forEach { source ->
        val definition = definitions.getValue(source.repository)
        val required = definition.dependsOn.associate { dependency ->
            definitions.getValue(dependency).artifact to if (dependency == FROZEN_WLM_REPOSITORY) FROZEN_WLM_SHA256 else sources.getValue(dependency).rawSha256
        }
        require(source.providerSha256 == required) { "qualified provider bindings must exactly match the resolved fresh-source DAG: ${source.repository}" }
        freshGradleProperties(source, Path.of("qualified-staging")) // Validate all properties before creating actual staging.
    }
    val physiology = sources.getValue("better-survival-physiology")
    val spice = definitions.getValue("better-spicy-farmers").artifact
    val water = definitions.getValue("better-drinking-water").artifact
    require(physiology.gradleProperties["spicyFarmersProviderJar"] == "@staging/$spice" &&
        (physiology.gradleProperties["drinkingWaterRuntimeJar"] == null || physiology.gradleProperties["drinkingWaterRuntimeJar"] == "@staging/$water")) {
        "Physiology must bind newly qualified staged Water/Spice; no legacy or compile-only Spice substitute"
    }
    val classes = physiology.gradleProperties["typedWaterCompileClasses"]
    require(!classes.isNullOrBlank() && !classes.startsWith("@staging/") && physiology.externalInputs.any {
        it.kind == "tree" && Path.of(it.path).toAbsolutePath().normalize() == Path.of(classes).toAbsolutePath().normalize()
    }) { "Physiology requires an explicit hash-pinned typed Water compile-class tree before staging" }
}

internal fun inputSnapshotHash(path: Path, kind: String): String {
    if (kind == "file") return Hashes.sha256(path)
    require(kind == "tree" && Files.isDirectory(path)) { "unsupported or missing external input: $path ($kind)" }
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    Files.walk(path).use { paths -> paths.sorted().forEach { file ->
        require(!Files.isSymbolicLink(file)) { "external class snapshots cannot contain symlinks: $file" }
        if (Files.isDirectory(file)) return@forEach
        require(Files.isRegularFile(file)) { "external class snapshots must contain regular files: $file" }
        digest.update(path.relativize(file).toString().replace('\\', '/').toByteArray(Charsets.UTF_8))
        digest.update(0.toByte()); digest.update(Hashes.sha256(file).toByteArray(Charsets.US_ASCII)); digest.update(0.toByte())
    } }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun requireSnapshot(snapshot: FreshInputSnapshot) {
    require(Regex("[0-9a-f]{64}").matches(snapshot.sha256) &&
        inputSnapshotHash(Path.of(snapshot.path), snapshot.kind) == snapshot.sha256) { "qualified input changed: ${snapshot.path}" }
}

internal fun validateQualifiedFreshSource(
    repository: Path, mod: ActiveMod, source: QualifiedFreshSource,
    qualificationNotBefore: Instant? = null, planCreated: Instant? = null,
) {
    require(source.repository == mod.repository && source.head.isNotBlank() &&
        Regex("[0-9a-f]{64}").matches(source.productionFingerprint) && Regex("[0-9a-f]{64}").matches(source.sourceVerificationFingerprint) &&
        Regex("[0-9a-f]{64}").matches(source.rawSha256)) {
        "missing qualified source identity: ${mod.repository}"
    }
    require(capture(repository, listOf("git", "rev-parse", "HEAD")).trim() == source.head &&
        productionFingerprint(repository) == source.productionFingerprint && sourceVerificationFingerprint(repository) == source.sourceVerificationFingerprint) {
        "qualified production/verification snapshot changed: ${mod.repository}"
    }
    requireSnapshot(source.qualification)
    require(source.qualification.kind == "file") { "qualification receipt must be a file" }
    val receipt = mapper.readTree(Path.of(source.qualification.path).toFile())
    if (receipt.path("status").asText() == "blocked" || receipt.path("native_status").asText() == "blocked") {
        throw FreshReleaseBlocked("source verification blocked: ${mod.repository}; see ${source.qualification.path}")
    }
    require(receipt.path("schema").asText() == "bc.source_qualification.v1" &&
        receipt.path("status").asText() == "passed" && receipt.path("repository").asText() == mod.repository &&
        receipt.path("head").asText() == source.head &&
        receipt.path("pre_fingerprint").asText() == source.productionFingerprint &&
        receipt.path("post_fingerprint").asText() == source.productionFingerprint &&
        receipt.path("pre_source_verification_fingerprint").asText() == source.sourceVerificationFingerprint &&
        receipt.path("post_source_verification_fingerprint").asText() == source.sourceVerificationFingerprint &&
        receipt.path("raw_sha256").asText() == source.rawSha256) { "qualification is not a matching successful PRE/POST source build: ${mod.repository}" }
    val pre = Instant.parse(receipt.path("pre_captured_utc").asText())
    val post = Instant.parse(receipt.path("post_captured_utc").asText())
    require(!post.isBefore(pre) && !post.isAfter(Instant.now()) &&
        (qualificationNotBefore == null || !pre.isBefore(qualificationNotBefore)) &&
        (planCreated == null || !post.isAfter(planCreated))) { "qualification is stale/outside the fresh request cohort: ${mod.repository}" }
    val checks = receipt.path("checks").toList()
    require(checks.isNotEmpty() && checks.all { it.path("status").asText() == "passed" } &&
        (mod.tasks - "stageRuntimeJar").all { task -> checks.any { it.path("task").asText() == task } }) {
        "required repository verification did not pass: ${mod.repository}"
    }
    checks.forEach { check ->
        val evidence = mapper.treeToValue(check.path("evidence"), FreshInputSnapshot::class.java)
        requireSnapshot(evidence)
    }
    require(receipt.path("native_required").isBoolean &&
        (!(mod.tasks.any { it in setOf("verifyFull", "verifyWorld") }) || receipt.path("native_required").asBoolean()) &&
        if (receipt.path("native_required").asBoolean()) receipt.path("native_status").asText() == "passed"
        else receipt.path("native_status").asText() == "not-required" && !receipt.path("native_not_required_reason").asText().isBlank()) {
        "native verification cannot be missing, skipped or implicitly waived: ${mod.repository}"
    }
    require(mapper.convertValue(receipt.path("provider_sha256"), Map::class.java) == source.providerSha256 &&
        mapper.convertValue(receipt.path("gradle_properties"), Map::class.java) == source.gradleProperties) {
        "provider/property bindings differ from qualification: ${mod.repository}"
    }
    source.externalInputs.forEach(::requireSnapshot)
    require(mapper.convertValue(receipt.path("external_inputs"), List::class.java) ==
        mapper.convertValue(mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(source.externalInputs), List::class.java)) {
        "external inputs differ from qualification: ${mod.repository}"
    }
    val raw = repository.resolve("build/libs").resolve(mod.artifact)
    require(Hashes.sha256(raw) == source.rawSha256 && jarDeclaresMod(raw, mod.modId)) { "qualified raw runtime changed: ${mod.artifact}" }
}

internal fun freshGradleProperties(source: QualifiedFreshSource, staging: Path): List<String> {
    val allowed = setOf("drinkingWaterRuntimeJar", "spicyFarmersProviderJar", "typedWaterCompileClasses")
    require(source.gradleProperties.keys.all { it in allowed }) { "unapproved Gradle property in fresh-source plan" }
    return source.gradleProperties.toSortedMap().map { (key, value) ->
        val path = if (value.startsWith("@staging/")) {
            val name = value.removePrefix("@staging/")
            require(name == Path.of(name).fileName.toString() && name in source.providerSha256) { "unsafe/unqualified staged property: $key" }
            staging.resolve(name)
        } else {
            require(source.externalInputs.any { Path.of(it.path).toAbsolutePath().normalize() == Path.of(value).toAbsolutePath().normalize() }) {
                "unqualified external Gradle property: $key"
            }
            Path.of(value)
        }
        "-P$key=${path.toAbsolutePath().normalize()}"
    }
}

internal fun thirdPartyPins(root: Path): Map<String, String> = buildMap {
    // Packaging intentionally changes the distribution version/index, but never Forge/Minecraft.
    val versions = Regex("(?ms)^\\[versions]\\s*\\n(.*?)(?=^\\[|\\z)").find(Files.readString(root.resolve("pack.toml")))
        ?: error("missing pinned pack runtime versions")
    put("pack.toml#versions", java.security.MessageDigest.getInstance("SHA-256")
        .digest(versions.groupValues[1].toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
    listOf("mods", "resourcepacks", "shaderpacks", "tacz").forEach { folder ->
        val directory = root.resolve(folder)
        if (Files.isDirectory(directory)) Files.list(directory).use { files ->
            files.filter { it.name.endsWith(".pw.toml") && Files.isRegularFile(it) }.sorted().forEach {
                put(root.relativize(it).toString().replace('\\', '/'), Hashes.sha256(it))
            }
        }
    }
}

private fun requireFrozenWlm(root: Path) {
    require(frozenLaunchers.all { (name, hash) -> Hashes.sha256(root.resolve(name)) == hash }) { "accepted Build1 launchers changed" }
    require(Hashes.sha256(root.resolve("mods/$FROZEN_WLM_ARTIFACT")) == FROZEN_WLM_SHA256 &&
        Hashes.sha256(root.resolve("better-world-management-server.sh")) == FROZEN_SUPERVISOR_SHA256) {
        "accepted Build1 WLM binary/supervisor must remain byte-identical"
    }
}

private fun archiveMemberHash(archive: Path, suffix: String): String = java.util.zip.ZipFile(archive.toFile()).use { zip ->
    val matches = zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(suffix) }.toList()
    require(matches.size == 1) { "expected one immutable member $suffix in $archive" }
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    zip.getInputStream(matches.single()).use { input ->
        val buffer = ByteArray(65536)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun validateTeleSoakReceipt(path: Path, runId: String, pair: CandidatePair, started: Instant) {
    // Existing generic validator deliberately admits only legacy tiers; do not broaden it.
    val receipt = mapper.readTree(path.toFile())
    require(Files.getLastModifiedTime(path).toInstant() >= started.truncatedTo(java.time.temporal.ChronoUnit.SECONDS) &&
        receipt.path("run_id").asText() == runId && receipt.path("tier").asText() == "tele-soak" &&
        receipt.path("status").asText() == "passed" && receipt.path("scenario_completed").asBoolean() &&
        receipt.path("dwell_seconds_per_dimension").asInt() == 300 && receipt.path("circuits").asInt() == 3) {
        "fresh completed fixed-300-second/three-circuit TeleSoak receipt required"
    }
    val dimensions = receipt.path("registered_dimensions").map { it.asText() }
    require(dimensions.isNotEmpty() && dimensions.all { Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(it) } &&
        dimensions.toSet().size == dimensions.size && receipt.path("dimension_results").size() == dimensions.size) {
        "TeleSoak registered roster/per-dimension completion evidence missing"
    }
    val results = receipt.path("dimension_results").toList()
    require(results.map { it.path("dimension").asText() }.toSet() == dimensions.toSet() && results.all {
        it.path("dwell_seconds").asDouble().let { seconds -> seconds.isFinite() && seconds >= 300.0 } &&
            it.path("completed_circuits").map { circuit -> circuit.asInt() } == listOf(1, 2, 3) && it.path("tps_verified").asBoolean()
    }) { "TeleSoak did not complete the fixed dwell and all three circuits for every registered dimension" }
    val events = path.resolveSibling("events.jsonl")
    require(receipt.path("events_sha256").asText() == Hashes.sha256(events)) { "TeleSoak receipt is not linked to its complete raw event evidence" }
    validateTeleSoakEvents(events, dimensions)
    require(receipt.path("candidates") == mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(pair.document())) {
        "TeleSoak candidate paths/hashes do not match the exact fresh pair"
    }
    requireStableCandidates(pair)
}

internal fun validateTeleSoakEvents(path: Path, dimensions: List<String>) {
    val events = Files.readAllLines(path).filter { it.isNotBlank() }.map { mapper.readTree(it) }
    fun unique(type: String, dimension: String? = null, circuit: Int? = null): Int {
        val indices = events.indices.filter { index ->
            val event = events[index]
            event.path("type").asText() == type && (dimension == null || event.path("dimension").asText() == dimension) &&
                (circuit == null || event.path("circuit").asInt(-1) == circuit)
        }
        require(indices.size == 1) { "missing/duplicate TeleSoak $type for $dimension circuit $circuit" }
        return indices.single()
    }
    val roster = events[unique("dimension_roster")].path("dimensions").map { it.asText() }
    require(roster.size == dimensions.size && roster.toSet() == dimensions.toSet() && "minecraft:overworld" in roster && roster.size <= 64) {
        "TeleSoak summary differs from the complete live registered roster"
    }
    validateTeleClientEvidence(events)
    val hops = events.filter { it.path("type").asText() == "hop_started" }
    require(hops.size == dimensions.size * 4 && hops.all {
        it.path("dimension").asText() in dimensions && it.path("circuit").asInt(-1) in 0..3
    }) { "unexpected/retried TeleSoak hop outside the fixed itinerary" }
    dimensions.forEach { dimension ->
        (0..3).forEach { circuit ->
            val hop = unique("hop_started", dimension, circuit)
            val arrival = unique("arrival_verified", dimension, circuit)
            val stable = unique("tps_stable", dimension, circuit)
            require(hop < arrival && arrival < stable) { "TeleSoak arrival/recovery event order invalid" }
            val receipts = (hop + 1 until arrival).map { events[it] }.filter { it.path("type").asText() == "client_dimension_receipt" }
            require(receipts.size == 1 && receipts.single().path("dimension").asText() == dimension &&
                receipts.single().path("server_conditional").asBoolean() && receipts.single().path("fresh_client_log_offset").asLong(-1) >= 0) {
                "each TeleSoak arrival needs one fresh server-conditioned receipt in the actual connected client"
            }
            val hopSeconds = events[arrival].path("hop_seconds").asDouble(Double.NaN)
            val recoverySeconds = events[stable].path("recovery_seconds").asDouble(Double.NaN)
            require(hopSeconds.isFinite() && recoverySeconds.isFinite() && hopSeconds >= 0 && recoverySeconds >= 30 &&
                hopSeconds + recoverySeconds <= 120.0) { "TeleSoak hop failed its unchanged 120-second arrival/TPS budget" }
            val samples = (arrival + 1 until stable).map { events[it] }.filter {
                it.path("type").asText() == "tps_sample" && it.path("dimension").asText() == dimension && it.path("circuit").asInt(-1) == circuit
            }
            require(samples.size >= 2) { "TeleSoak lacks actual recovery TPS samples" }
            var last = -1.0
            var goodSince = Double.NaN
            samples.forEach { sample ->
                val elapsed = sample.path("elapsed_seconds").asDouble(Double.NaN)
                val tps = sample.path("tps").asDouble(Double.NaN)
                require(elapsed.isFinite() && elapsed >= 0 && elapsed > last && elapsed <= recoverySeconds &&
                    tps.isFinite() && tps in 0.0..20.0001) { "invalid or stale TeleSoak TPS sample" }
                goodSince = if (tps >= 19.0) (if (goodSince.isNaN() || (last >= 0 && elapsed - last > 2.0)) elapsed else goodSince) else Double.NaN
                last = elapsed
            }
            require(goodSince.isFinite() && last - goodSince >= 30.0 && recoverySeconds - last <= 1.0) {
                "raw TPS observations do not prove 30 continuous seconds at >=19 TPS"
            }
        }
        val dwellStart = unique("dwell_started", dimension)
        val dwellEnd = unique("dwell_completed", dimension)
        val dwell = events[dwellEnd]
        require(unique("tps_stable", dimension, 0) < dwellStart && dwellStart < dwellEnd && dwellEnd < unique("circuit_started", circuit = 1) &&
            dwell.path("actual_seconds").asDouble(Double.NaN).let { it.isFinite() && it >= 300 }) {
            "TeleSoak initial dwell must follow arrival/recovery and finish before the three circuits"
        }
    }
    (1..3).forEach { circuit ->
        val start = unique("circuit_started", circuit = circuit)
        val end = unique("circuit_completed", circuit = circuit)
        val route = events[start].path("route").map { it.asText() }
        require(start < end && route.size == dimensions.size && route.toSet() == dimensions.toSet() && dimensions.all {
            unique("hop_started", it, circuit) > start && unique("tps_stable", it, circuit) < end
        } && (circuit == 1 || unique("circuit_completed", circuit = circuit - 1) < start)) { "TeleSoak circuit route/event order incomplete" }
    }
    val complete = events[unique("tele_soak_complete")]
    require(complete.path("dimension_count").asInt() == dimensions.size && complete.path("circuits").asInt() == 3) { "TeleSoak itinerary incomplete" }
    val completeIndex = unique("tele_soak_complete")
    val disconnected = unique("client_disconnected")
    val saved = unique("server_saved_and_stopped")
    val validated = unique("validated")
    val cleanupIndex = unique("process_cleanup")
    val finished = unique("session_finished")
    require(unique("circuit_completed", circuit = 3) < completeIndex && completeIndex < disconnected && disconnected < saved && saved < validated && validated < cleanupIndex && cleanupIndex < finished) {
        "TeleSoak did not complete the save/validation/cleanup lifecycle in order"
    }
    val cleanup = events[cleanupIndex]
    require(cleanup.path("complete").asBoolean() && cleanup.path("surviving_pids").isArray && cleanup.path("surviving_pids").isEmpty) { "TeleSoak process cleanup did not complete" }
    require(events[finished].path("success").asBoolean()) { "TeleSoak session did not finish successfully" }
}

private fun validateTeleClientEvidence(events: List<com.fasterxml.jackson.databind.JsonNode>) {
    val joined = events.indices.filter { events[it].path("type").asText() == "client_joined" }
    val completed = events.indices.filter { events[it].path("type").asText() == "tele_soak_complete" }
    require(joined.size == 1 && completed.size == 1 && joined.single() < completed.single()) { "TeleSoak must have one uninterrupted joined client" }
    val from = joined.single(); val until = completed.single()
    var previousHeartbeat = Instant.parse(events[from].path("time").asText())
    var target: String? = null
    val markers = mutableSetOf<String>()
    var heartbeatCount = 0
    (from + 1..until).forEach { index ->
        val event = events[index]
        when (event.path("type").asText()) {
            "hop_started" -> target = event.path("dimension").asText()
            "client_disconnected", "client_joined", "reconnect", "failure" -> error("client connection failed/restarted inside TeleSoak")
            "client_dimension_receipt" -> require(event.path("marker").asText().isNotBlank() && markers.add(event.path("marker").asText())) { "stale/repeated client dimension receipt" }
            "heartbeat" -> {
                val at = Instant.parse(event.path("time").asText())
                require(!at.isBefore(previousHeartbeat) && java.time.Duration.between(previousHeartbeat, at) <= java.time.Duration.ofSeconds(15) &&
                    event.path("client_received").asBoolean() && event.path("server_alive").asBoolean() &&
                    event.path("marker").asText().isNotBlank() && markers.add(event.path("marker").asText()) &&
                    (target == null || event.path("verified_dimension").asText() == target)) { "missing/late/stale/wrong-dimension actual client heartbeat" }
                previousHeartbeat = at; heartbeatCount++
            }
        }
    }
    val end = Instant.parse(events[until].path("time").asText())
    require(heartbeatCount > 0 && !end.isBefore(previousHeartbeat) && java.time.Duration.between(previousHeartbeat, end) <= java.time.Duration.ofSeconds(15)) {
        "last TeleSoak heartbeat is missing or older than 15 seconds"
    }
}

private fun runFreshSourceRelease(root: Path, jobs: Int, planPath: Path) {
    require(jobs in 1..4)
    val runId = System.getenv("BC_TEST_RUN_ID")?.takeIf { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) }
        ?: error("fresh releases require a fresh BC_TEST_RUN_ID from release.main.kts")
    val evidence = root.resolve("generated/test-evidence/$runId/release").also { it.createDirectories() }
    try {
        val authorization = requireFreshRuntimeAuthorization(System.getenv())
        require(!System.getenv("BC_PACK_TEST_LOCK_TOKEN").isNullOrBlank()) { "invoke release.main.kts with the runtime mutex" }
        val manifestPath = root.resolve("gradle/active-custom-mods.json")
        val manifestHash = Hashes.sha256(manifestPath)
        val planHash = Hashes.sha256(planPath)
        val manifest: ActiveModManifest = mapper.readValue(manifestPath.toFile())
        val plan: FreshSourcePlan = mapper.readValue(planPath.toFile())
        fun requirePlanStable() {
            require(Hashes.sha256(manifestPath) == manifestHash && Hashes.sha256(planPath) == planHash) { "fresh source plan/active manifest changed during release" }
        }
        val (qualificationNotBefore, planCreated) = validateFreshQualificationCohort(plan, System.getenv())
        validateFreshSourcePopulation(manifest, plan)
        validateFreshSourceBindings(manifest, plan)
        fun validateSource(source: QualifiedFreshSource) {
            validateQualifiedFreshSource(root.parent.resolve("mod_source/${source.repository}"), manifest.mods.single { it.repository == source.repository },
                source, qualificationNotBefore, planCreated)
        }
        require(!evidence.parent.resolve("smoke/run.json").exists() && !evidence.parent.resolve("tele-soak/run.json").exists()) {
            "fresh release run ID already has runtime evidence"
        }
        require(plan.thirdPartyPins.isNotEmpty() && thirdPartyPins(root) == plan.thirdPartyPins) { "third-party pins differ from qualified plan" }
        requireFrozenWlm(root)
        val accepted = root.resolve("dist/build-1/server/better-content.zip")
        require(Hashes.sha256(accepted) == ACCEPTED_BUILD1_SERVER_SHA256 &&
            archiveMemberHash(accepted, "/mods/$FROZEN_WLM_ARTIFACT") == FROZEN_WLM_SHA256 &&
            archiveMemberHash(accepted, "/better-world-management-server.sh") == FROZEN_SUPERVISOR_SHA256) {
            "immutable accepted Build1 baseline is missing/changed"
        }
        val byRepository = plan.sources.associateBy { it.repository }
        val order = freshDependencyOrder(manifest.mods)
        plan.sources.forEach(::validateSource)
        val preservation = preserveWorkspace(root, evidence.resolve("pre-mutation-backup"), manifest.mods)
        val preservationProofs = workspacePreservationProofs(preservation)
        val planCopy = evidence.resolve("qualified-source-plan.json")
        Files.copy(planPath, planCopy)
        require(Hashes.sha256(planCopy) == planHash) { "source plan changed while preserving proof" }
        val sourceProofs = preserveQualificationProofs(plan, evidence.resolve("qualified-proof-copies")) + preservationProofs + fileSnapshot(planCopy)
        val staging = evidence.resolve("fresh-raw-jars").also { Files.createDirectory(it) }
        Files.copy(root.resolve("mods/$FROZEN_WLM_ARTIFACT"), staging.resolve(FROZEN_WLM_ARTIFACT))
        val built = mutableListOf<BuiltMod>()
        order.forEach { mod ->
            if (mod.repository == FROZEN_WLM_REPOSITORY) {
                built += BuiltMod(mod, readSourceCommit(staging.resolve(mod.artifact), mod) ?: "accepted-build1", staging.resolve(mod.artifact), FROZEN_WLM_SHA256, "frozen-build1")
            } else {
                val source = byRepository.getValue(mod.repository)
                val repository = root.parent.resolve("mod_source/${mod.repository}")
                validateSource(source)
                val requiredProviders = mod.dependsOn.map { name -> manifest.mods.single { it.repository == name }.artifact }.toSet()
                require(source.providerSha256.keys.containsAll(requiredProviders)) { "qualified dependency provider hashes missing: ${mod.repository}" }
                source.providerSha256.forEach { (name, hash) ->
                    require(name == Path.of(name).fileName.toString() && Hashes.sha256(staging.resolve(name)) == hash) { "fresh typed provider changed/not yet built: $name" }
                }
                runLogged(repository, listOf(repository.resolve("gradlew").toString(), "--no-daemon", "--max-workers=2", "stageRuntimeJar") +
                    freshGradleProperties(source, staging), evidence.resolve("fresh-${mod.repository}.log"), providerBuildEnvironment(staging))
                validateSource(source) // Includes equality to owner-qualified raw SHA.
                val jar = staging.resolve(mod.artifact)
                Files.copy(repository.resolve("build/libs/${mod.artifact}"), jar)
                require(Hashes.sha256(jar) == source.rawSha256) { "raw output changed while copying: ${mod.repository}" }
                built += BuiltMod(mod, source.head, jar, source.rawSha256, "fresh-source")
            }
        }
        // Providers retain their exact qualified RAW bytes until every consumer has compiled.
        plan.sources.forEach(::validateSource)
        requirePlanStable()
        // Qualification binds runtime bytes, not just classes. Fresh releases NEVER decorate JARs.
        val distributionMods = built
        distributionMods.forEach { mod ->
            require(Hashes.sha256(mod.jar) == if (mod.mode == "frozen-build1") FROZEN_WLM_SHA256 else byRepository.getValue(mod.definition.repository).rawSha256) {
                "qualified runtime bytes changed before deployment: ${mod.definition.repository}"
            }
        }
        requireFrozenWlm(root)
        require(thirdPartyPins(root) == plan.thirdPartyPins) { "third-party pins changed before deployment" }
        validateWorkspacePreservation(preservation, root, manifest.mods) // Last admission before ANY root overwrite.
        distributionMods.filterNot { it.mode == "frozen-build1" }.forEach {
            Files.copy(it.jar, root.resolve("mods/${it.definition.artifact}"), StandardCopyOption.REPLACE_EXISTING)
        }
        requireFrozenWlm(root)
        runLogged(root, listOf("packwiz", "refresh"), evidence.resolve("packwiz-refresh.log"))
        runLogged(root, listOf("git", "diff", "--check"), evidence.resolve("diff-check.log"))
        requirePlanStable()
        val previous = releaseDirectories(root)
        runLogged(root, listOf(root.resolve("dist.sh").toString()), evidence.resolve("dist.log"))
        val pair = locateNewRelease(root, previous)
        require(thirdPartyPins(root) == plan.thirdPartyPins) { "third-party pins changed while packaging" }
        distributionMods.forEach { mod ->
            require(archiveMemberHash(pair.client, "/mods/${mod.definition.artifact}") == mod.sha256 &&
                archiveMemberHash(pair.server, "/mods/${mod.definition.artifact}") == mod.sha256) { "packaged custom artifact differs: ${mod.definition.repository}" }
        }
        require(archiveMemberHash(pair.server, "/better-world-management-server.sh") == FROZEN_SUPERVISOR_SHA256 &&
            frozenLaunchers.all { (name, hash) -> archiveMemberHash(pair.server, "/$name") == hash }) { "packaged WLM supervisor/launcher changed" }
        val archiveInventory = evidence.resolve("archive-members.json")
        val members = validatePackagedInventory(root, pair, manifest.mods)
        writeNewJson(archiveInventory, mapOf("schema" to "bc.archive_members.v1", "candidates" to pair.document(), "members" to members))
        val provenance = mapOf("schema" to "bc.fresh_dist_provenance.v3", "run_id" to runId,
            "runtime_inputs" to "48-fresh-sources-plus-accepted-build1-wlm", "authorization_reference" to authorization,
            "observed_request_lower_bound_utc" to System.getenv("BC_FRESH_SOURCE_REQUEST_NOT_BEFORE_UTC"),
            "plan_sha256" to planHash, "manifest_sha256" to manifestHash, "plan" to plan, "mods" to distributionMods, "candidates" to pair.document(),
            "archive_inventory" to fileSnapshot(archiveInventory), "required_tiers" to listOf("smoke", "tele-soak"),
            "required_gates" to listOf("smoke", "tele-soak", "in-world-visual"))
        mapper.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("provenance.json").toFile(), provenance)
        val smoke = evidence.parent.resolve("smoke/run.json")
        val tele = evidence.parent.resolve("tele-soak/run.json")
        require(!smoke.exists() && !tele.exists()) { "fresh source release cannot reuse prior runtime evidence" }
        val started = Instant.now()
        listOf("smoke", "tele-soak").forEach { tier ->
            requireStableCandidates(pair)
            runLogged(root, listOf(root.resolve("test.main.kts").toString(), tier, "--client", pair.client.toString(), "--server", pair.server.toString()),
                evidence.resolve("$tier.log"), mapOf("BC_TEST_RUN_ID" to runId))
            if (tier == "smoke") {
                validateSmokeEvidence(smoke, runId, pair, started)
                val clientBytes = validateMaterializedClientBytes(root, runId, pair, manifest.mods)
                writeNewJson(evidence.resolve("client-binary-inventory.json"), clientBytes)
            } else validateTeleSoakReceipt(tele, runId, pair, started)
        }
        validateSmokeEvidence(smoke, runId, pair, started)
        validateTeleSoakReceipt(tele, runId, pair, started)
        requirePlanStable()
        requireFrozenWlm(root)
        val pending = createVisualPending(evidence, runId, pair, started, sourceProofs)
        throw VisualReviewRequired(pending) // A successful runtime is NOT a visual acceptance.
    } catch (error: Exception) {
        mapper.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("fresh-release-result.json").toFile(), mapOf(
            "schema" to "bc.fresh_release_result.v1", "run_id" to runId, "status" to if (error is FreshReleaseBlocked || error is VisualReviewRequired) "blocked" else "failed",
            "blocker_code" to if (error is VisualReviewRequired) "visual-review-required" else null,
            "pending_sha256" to if (error is VisualReviewRequired) Hashes.sha256(error.pending) else null,
            "failure" to error.stackTraceToString(), "updated_at" to Instant.now().toString()))
        System.err.println("fresh release failed/blocked: evidence and pre-mutation backup retained; workspace may remain staged after deployment; no automatic rollback")
        throw error
    }
}

internal data class WorkspaceItem(val path: String, val kind: String, val mode: Int? = null, val sha256: String? = null,
    val size: Long = 0, val linkTarget: String? = null, val referentSha256: String? = null, val referentMode: Int? = null)
internal data class WorkspacePreservation(val schema: String, val root: String, val capturedAt: String, val gitStatus: String, val items: List<WorkspaceItem>)
private fun unixMode(path: Path): Int = (Files.getAttribute(path, "unix:mode", java.nio.file.LinkOption.NOFOLLOW_LINKS) as Number).toInt()
private fun safeRelative(relative: String): Path = Path.of(relative).also {
    require(!it.isAbsolute && relative.isNotBlank() && it.none { part -> part.toString() in setOf(".", "..") }) { "unsafe preservation path: $relative" }
}
private fun workspacePaths(root: Path, mods: List<ActiveMod>): List<String> {
    val paths = linkedSetOf("mods", "pack.toml", "index.toml", ".packwizignore", "gradle/active-custom-mods.json", "run.sh", "run-forge.sh", "better-world-management-server.sh", "package.sh", "dist.sh")
    mods.forEach { paths += "mods/${it.artifact}" }
    Files.list(root).use { files -> files.filter { it.name.endsWith(".sh") || it.name.endsWith(".main.kts") }.forEach { paths += it.name } }
    listOf("mods", "resourcepacks", "shaderpacks", "tacz").forEach { folder ->
        val directory = root.resolve(folder)
        require(!Files.isSymbolicLink(directory)) { "preservation refuses external/symlinked content root: $folder" }
        if (Files.isDirectory(directory)) Files.walk(directory).use { files -> files.forEach { path ->
            if (folder == "mods" || path.name.endsWith(".pw.toml")) paths += root.relativize(path).toString().replace('\\', '/')
        } }
    }
    return paths.sorted()
}
private fun workspaceItems(root: Path, mods: List<ActiveMod>): List<WorkspaceItem> = workspacePaths(root, mods).map { relative ->
    val path = root.resolve(safeRelative(relative))
    when {
        Files.isSymbolicLink(path) -> {
            require(!Files.isDirectory(path)) { "cannot preserve an unbounded symlinked content directory: $relative" }
            val target = Files.readSymbolicLink(path).toString()
            WorkspaceItem(relative, "symlink", unixMode(path),
                java.security.MessageDigest.getInstance("SHA-256").digest(target.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) },
                target.toByteArray(Charsets.UTF_8).size.toLong(), target,
                if (Files.isRegularFile(path)) Hashes.sha256(path) else null, if (Files.isRegularFile(path)) unixMode(path.toRealPath()) else null)
        }
        !Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS) -> WorkspaceItem(relative, "absent")
        Files.isDirectory(path) -> WorkspaceItem(relative, "directory", unixMode(path))
        Files.isRegularFile(path) -> WorkspaceItem(relative, "file", unixMode(path), Hashes.sha256(path), Files.size(path))
        else -> error("unsupported user-owned filesystem object: $path")
    }
}
internal fun preserveWorkspace(root: Path, destination: Path, mods: List<ActiveMod>): Path {
    require(Files.isRegularFile(root.resolve("pack.toml")) && Files.isRegularFile(root.resolve("index.toml"))) { "mandatory pack/index metadata missing; preservation cannot proceed" }
    val items = workspaceItems(root, mods)
    val status = capture(root, listOf("git", "--no-optional-locks", "status", "--porcelain=v1", "-z"))
    Files.createDirectory(destination) // Never overwrite an earlier complete or failed backup.
    val receipt = WorkspacePreservation("bc.pre_mutation_workspace.v1", root.toAbsolutePath().normalize().toString(), Instant.now().toString(), status, items)
    items.forEach { item ->
        val relative = safeRelative(item.path)
        val original = root.resolve(relative)
        val copy = destination.resolve("files").resolve(relative)
        when (item.kind) {
            "directory" -> Files.createDirectories(copy)
            "file" -> {
                copy.parent.createDirectories(); Files.copy(original, copy, StandardCopyOption.COPY_ATTRIBUTES)
                Files.setAttribute(copy, "unix:mode", item.mode!!)
                require(Hashes.sha256(copy) == item.sha256 && Files.size(copy) == item.size && unixMode(copy) == item.mode) { "backup bytes/mode changed while copying ${item.path}" }
            }
            "symlink" -> {
                copy.parent.createDirectories(); Files.createSymbolicLink(copy, Path.of(item.linkTarget!!))
                require(unixMode(copy) == item.mode) { "cannot preserve symlink mode" }
                if (item.referentSha256 != null) {
                    val referent = destination.resolve("referents").resolve(relative)
                    referent.parent.createDirectories(); Files.copy(original, referent, StandardCopyOption.COPY_ATTRIBUTES)
                    Files.setAttribute(referent, "unix:mode", item.referentMode!!)
                    require(Hashes.sha256(referent) == item.referentSha256 && unixMode(referent) == item.referentMode) { "symlink referent changed during preservation" }
                }
            }
            "absent" -> Unit
            else -> error("unknown preservation object")
        }
    }
    items.filter { it.kind == "directory" }.sortedByDescending { it.path.length }.forEach {
        Files.setAttribute(destination.resolve("files").resolve(it.path), "unix:mode", it.mode!!)
    }
    val path = destination.resolve("receipt.json")
    writeNewJson(path, receipt)
    validateWorkspacePreservation(path, root, mods)
    require(items.none { it.kind == "symlink" && (it.path in setOf("pack.toml", "index.toml", ".packwizignore") || it.path.endsWith(".pw.toml")) }) {
        "packwiz-write metadata is symlinked; backup preserved, but release refuses external referent mutation"
    }
    return path
}
internal fun validateWorkspacePreservation(receiptPath: Path, originalRoot: Path? = null, mods: List<ActiveMod> = emptyList()) {
    val receipt: WorkspacePreservation = mapper.readValue(receiptPath.toFile())
    require(receipt.schema == "bc.pre_mutation_workspace.v1" && receipt.items.isNotEmpty() && receipt.items.map { it.path }.toSet().size == receipt.items.size)
    val destination = receiptPath.parent
    receipt.items.forEach { item ->
        val relative = safeRelative(item.path)
        val copy = destination.resolve("files").resolve(relative)
        when (item.kind) {
            "file" -> require(Files.isRegularFile(copy) && !Files.isSymbolicLink(copy) && Hashes.sha256(copy) == item.sha256 && Files.size(copy) == item.size && unixMode(copy) == item.mode)
            "directory" -> require(Files.isDirectory(copy) && !Files.isSymbolicLink(copy) && unixMode(copy) == item.mode)
            "symlink" -> {
                require(Files.isSymbolicLink(copy) && Files.readSymbolicLink(copy).toString() == item.linkTarget && unixMode(copy) == item.mode)
                if (item.referentSha256 != null) {
                    val referent = destination.resolve("referents").resolve(relative)
                    require(Files.isRegularFile(referent) && !Files.isSymbolicLink(referent) && Hashes.sha256(referent) == item.referentSha256 && unixMode(referent) == item.referentMode)
                }
            }
            "absent" -> require(!Files.exists(copy, java.nio.file.LinkOption.NOFOLLOW_LINKS))
            else -> error("unknown preservation kind")
        }
    }
    if (originalRoot != null) require(receipt.root == originalRoot.toAbsolutePath().normalize().toString() && workspaceItems(originalRoot, mods) == receipt.items) {
        "user-owned files/types/modes/file set changed since preservation; refusing overwrite"
    }
}
private fun workspacePreservationProofs(receipt: Path): List<FreshInputSnapshot> = listOf(fileSnapshot(receipt)) +
    Files.walk(receipt.parent).use { files -> files.filter { Files.isRegularFile(it, java.nio.file.LinkOption.NOFOLLOW_LINKS) && it != receipt }.sorted().toList().map(::fileSnapshot) }

internal data class ArchiveMember(val side: String, val path: String, val size: Long, val sha256: String, val category: String)
private const val BUILD1_RUN = "20261026T130136Z-2932883"
private const val BUILD1_CLIENT_SHA = "56d082d6aa8c5b4848077fd1e8b30469be2a928a717cc707978c46db252d036b"
internal class VisualReviewRequired(val pending: Path) : IllegalStateException("blocked: real independent in-world visual review required; pending=$pending")

private fun fileSnapshot(path: Path) = FreshInputSnapshot(path.toAbsolutePath().normalize().toString(), Hashes.sha256(path))
private fun writeNewJson(path: Path, value: Any) {
    path.parent.createDirectories()
    require(!Files.exists(path)) { "immutable evidence/ready already exists: $path" }
    val temporary = Files.createTempFile(path.parent, ".${path.fileName}.", ".tmp")
    try {
        mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value)
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE)
    } finally { Files.deleteIfExists(temporary) }
}
private fun streamHash(input: java.io.InputStream, algorithm: String = "SHA-256"): String {
    val digest = java.security.MessageDigest.getInstance(algorithm)
    val buffer = ByteArray(65536)
    while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
private fun customBinary(name: String, mods: List<ActiveMod>, open: () -> java.io.InputStream): Boolean {
    if (mods.any { it.artifact == name }) return true
    // Only inspect plausible renamed custom JARs, not thousands of third-party class entries.
    if (mods.none { name.startsWith(it.repository + "-") || name.startsWith(it.artifact.replace(Regex("-[0-9].*\\.jar$"), "") + "-") }) return false
    return java.util.jar.JarInputStream(open()).use { jar ->
        while (true) {
            val entry = jar.nextJarEntry ?: break
            if (entry.name == SOURCE_METADATA_ENTRY) {
                val properties = java.util.Properties().apply { load(jar) }
                return@use mods.any { it.repository == properties.getProperty("repository") && it.modId == properties.getProperty("mod_id") }
            }
        }
        false
    }
}
internal fun archiveMembers(archive: Path, side: String, mods: List<ActiveMod>): List<ArchiveMember> = java.util.zip.ZipFile(archive.toFile()).use { zip ->
    val names = mutableSetOf<String>()
    zip.entries().asSequence().map { entry ->
        val name = entry.name
        val clean = name.removeSuffix("/")
        require(name.isNotBlank() && !name.startsWith("/") && !Regex("^[A-Za-z]:").containsMatchIn(name) && '\\' !in name && clean.split('/').none { it in setOf("", ".", "..") } && names.add(name)) {
            "duplicate/unsafe archive member: $name"
        }
        val binary = clean.lowercase().let { it.endsWith(".jar") || it.endsWith(".so") || it.endsWith(".dll") || it.endsWith(".dylib") || it.endsWith(".exe") }
        val custom = clean.contains("/mods/") && clean.endsWith(".jar") && customBinary(clean.substringAfterLast('/'), mods) { zip.getInputStream(entry) }
        val category = when {
            entry.isDirectory -> "directory"
            custom -> "custom"
            binary -> "third-party-binary"
            clean.endsWith(".sh") || clean.endsWith(".bat") || clean.endsWith(".ps1") -> "script"
            clean.split('/').any { it in setOf("config", "defaultconfigs", "server-config") } -> "config"
            else -> "resource-or-metadata"
        }
        ArchiveMember(side, name, entry.size, zip.getInputStream(entry).use { streamHash(it) }, category)
    }.sortedBy { it.path }.toList()
}
internal fun requireUnchangedThirdParty(baseline: List<ArchiveMember>, candidate: List<ArchiveMember>) {
    fun binaries(members: List<ArchiveMember>) = members.filter { it.category == "third-party-binary" }.associate { (it.side to it.path) to it.sha256 }
    require(binaries(baseline) == binaries(candidate)) { "third-party binary added/deleted/upgraded; no exception is authorized" }
}
internal fun requireRawCustomMembers(members: List<ArchiveMember>, expected: Map<String, String>) {
    listOf("client", "server").forEach { side -> expected.forEach { (artifact, sha) ->
        val matches = members.filter { it.side == side && it.path.endsWith("/mods/$artifact") }
        require(matches.size == 1 && matches.single().sha256 == sha && matches.single().category == "custom") { "packaged JAR is not the qualified RAW artifact: $side/$artifact" }
    } }
}
private fun embeddedLock(archive: Path): com.fasterxml.jackson.databind.JsonNode = java.util.zip.ZipFile(archive.toFile()).use { zip ->
    mapper.readTree(zip.getInputStream(zip.getEntry("better-content-artifacts.json") ?: error("client archive lacks immutable artifact lock")))
}
private fun validateArtifactLocks(baseline: Path, candidate: Path) {
    val before = embeddedLock(baseline); val after = embeddedLock(candidate)
    require(before.path("schema").asText() == "bc.client_artifacts.v1" && before == after) { "client remote artifact lock changed; no third-party exception is authorized" }
}
private fun validatePackagedInventory(root: Path, pair: CandidatePair, mods: List<ActiveMod>): List<ArchiveMember> {
    val oldClient = root.resolve("dist/build-1/client/better-content.zip")
    val oldServer = root.resolve("dist/build-1/server/better-content.zip")
    require(Hashes.sha256(oldClient) == BUILD1_CLIENT_SHA && Hashes.sha256(oldServer) == ACCEPTED_BUILD1_SERVER_SHA256) { "accepted Build1 archive bytes changed" }
    validateArtifactLocks(oldClient, pair.client)
    val baseline = archiveMembers(oldClient, "client", mods) + archiveMembers(oldServer, "server", mods)
    val candidate = archiveMembers(pair.client, "client", mods) + archiveMembers(pair.server, "server", mods)
    requireUnchangedThirdParty(baseline, candidate)
    return candidate
}
private fun clientJarBytes(directory: Path, mods: List<ActiveMod>): Map<String, String> {
    require(Files.isDirectory(directory)) { "actual materialized client mods missing: $directory" }
    return Files.list(directory).use { files -> files.filter { it.name.endsWith(".jar") }.sorted().toList().associate { file ->
        require(Files.isRegularFile(file) && !Files.isSymbolicLink(file)) { "client JAR must be an actual regular file" }
        file.name to Hashes.sha256(file)
    } }.filterKeys { name -> !customBinary(name, mods) { Files.newInputStream(directory.resolve(name)) } }
}
private fun validateMaterializedClientBytes(root: Path, runId: String, pair: CandidatePair, mods: List<ActiveMod>): Map<String, Any> {
    val oldPair = CandidatePair(root.resolve("dist/build-1"), root.resolve("dist/build-1/client/better-content.zip"), root.resolve("dist/build-1/server/better-content.zip"), BUILD1_CLIENT_SHA, ACCEPTED_BUILD1_SERVER_SHA256)
    validateSmokeEvidence(root.resolve("generated/test-evidence/$BUILD1_RUN/smoke/run.json"), BUILD1_RUN, oldPair, Instant.EPOCH)
    validateSmokeEvidence(root.resolve("generated/test-evidence/$runId/smoke/run.json"), runId, pair, Instant.EPOCH)
    val baselineRoot = root.resolve("generated/test-evidence/$BUILD1_RUN/smoke/fixture/client")
    val candidateRoot = root.resolve("generated/test-evidence/$runId/smoke/fixture/client")
    val lock = embeddedLock(oldPair.client)
    if (!Files.isDirectory(baselineRoot.resolve("mods")) || !Files.isDirectory(candidateRoot.resolve("mods"))) {
        throw FreshReleaseBlocked("actual accepted/candidate Smoke client byte fixture is missing; metadata alone cannot qualify client binaries")
    }
    listOf(baselineRoot, candidateRoot).forEach { client -> lock.path("artifacts").forEach { artifact ->
        val relative = artifact.path("path").asText()
        require(relative.isNotBlank() && !Path.of(relative).isAbsolute && relative.split('/').none { it == ".." }) { "unsafe client lock path" }
        val algorithm = when (artifact.path("hash_format").asText()) { "sha1" -> "SHA-1"; "sha256" -> "SHA-256"; "sha512" -> "SHA-512"; else -> error("client lock lacks cryptographic byte hash") }
        require(Files.newInputStream(client.resolve(relative)).use { streamHash(it, algorithm) } == artifact.path("hash").asText()) { "materialized client differs from accepted locked bytes: $relative" }
    } }
    val oldMembers = archiveMembers(oldPair.client, "client", mods).filter { it.path.contains("/mods/") && it.path.endsWith(".jar") }
    val lockedMods = lock.path("artifacts").map { it.path("path").asText() }.filter { it.startsWith("mods/") }.map { it.substringAfterLast('/') }.toSet()
    val oldOverrides = oldMembers.associate { it.path.substringAfterLast('/') to it.sha256 }
    Files.list(baselineRoot.resolve("mods")).use { files -> files.filter { it.name.endsWith(".jar") }.forEach { file ->
        require(file.name in lockedMods || oldOverrides[file.name] == Hashes.sha256(file)) { "baseline client JAR lacks accepted lock/archive provenance: ${file.name}" }
    } }
    oldOverrides.forEach { (name, sha) -> require(Hashes.sha256(baselineRoot.resolve("mods/$name")) == sha) { "accepted custom/direct client binary changed: $name" } }
    val custom = mods.associate { mod ->
        val sha = Hashes.sha256(candidateRoot.resolve("mods/${mod.artifact}"))
        require(sha == archiveMemberHash(pair.client, "/mods/${mod.artifact}")) { "Smoke did not use the exact packaged custom runtime: ${mod.artifact}" }
        mod.artifact to sha
    }
    val baseline = clientJarBytes(baselineRoot.resolve("mods"), mods)
    val candidate = clientJarBytes(candidateRoot.resolve("mods"), mods)
    require(baseline.isNotEmpty() && baseline == candidate) { "actual candidate client-only/third-party JAR bytes added/deleted/changed" }
    return mapOf("schema" to "bc.client_binary_inventory.v1", "run_id" to runId, "candidates" to pair.document(),
        "baseline_run_id" to BUILD1_RUN, "baseline_client" to baselineRoot.toString(), "candidate_client" to candidateRoot.toString(),
        "third_party_sha256" to candidate, "custom_sha256" to custom, "artifact_lock" to lock)
}
private fun preserveQualificationProofs(plan: FreshSourcePlan, destination: Path): List<FreshInputSnapshot> {
    val inputs = plan.sources.flatMap { source ->
        val receipt = mapper.readTree(Path.of(source.qualification.path).toFile())
        listOf(source.qualification) + receipt.path("checks").map { mapper.treeToValue(it.path("evidence"), FreshInputSnapshot::class.java) }
    }
    destination.createDirectories()
    return inputs.mapIndexed { index, input ->
        require(input.kind == "file"); requireSnapshot(input)
        val copy = destination.resolve("$index-${Path.of(input.path).fileName}")
        Files.copy(Path.of(input.path), copy)
        require(Hashes.sha256(copy) == input.sha256) { "qualification evidence changed while sealing" }
        fileSnapshot(copy)
    }
}
private fun createVisualPending(evidence: Path, runId: String, pair: CandidatePair, started: Instant, sourceProofs: List<FreshInputSnapshot>): Path {
    val smoke = evidence.parent.resolve("smoke/run.json"); val tele = evidence.parent.resolve("tele-soak/run.json")
    val proofs = sourceProofs + listOf(evidence.resolve("provenance.json"), evidence.resolve("archive-members.json"), evidence.resolve("client-binary-inventory.json"),
        smoke, smoke.resolveSibling("events.jsonl"), tele, tele.resolveSibling("events.jsonl")).map(::fileSnapshot)
    val worlds = listOf(smoke, tele).map { receipt ->
        val validated = Files.readAllLines(receipt.resolveSibling("events.jsonl")).map { mapper.readTree(it) }.single { it.path("type").asText() == "validated" }
        Path.of(validated.path("world_save").asText()).toAbsolutePath().normalize().parent.toString()
    }
    val path = evidence.resolve("pending-visual.json")
    writeNewJson(path, mapOf("schema" to "bc.fresh_pending_visual.v1", "status" to "awaiting-visual", "run_id" to runId,
        "created_at" to Instant.now().toString(), "runtime_started_at" to started.toString(), "candidates" to pair.document(),
        "proofs" to proofs, "runtime_worlds" to worlds))
    return path
}

internal data class VisualAcceptance(val sessionId: String, val world: String, val ownershipId: String, val bindings: List<FreshInputSnapshot>)
private fun visualWorldIdentity(world: Path): String {
    val marker = world.toRealPath().resolve(".bc-visual-world-id")
    require(Files.isRegularFile(marker) && !Files.isSymbolicLink(marker)) { "owned visual world lacks its immutable ownership marker" }
    return Files.readString(marker).trim().also {
        require(it.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))) { "invalid owned visual world identity" }
    }
}
internal fun requireUnusedVisualWorld(attempts: Path, sessionId: String, world: Path, ownershipId: String) {
    require(sessionId.isNotBlank() && Files.isDirectory(world))
    val actualWorld = world.toRealPath()
    require(visualWorldIdentity(actualWorld) == ownershipId) { "visual ownership marker differs from attested identity" }
    Files.list(attempts).use { files -> files.forEach { prior ->
        require(Files.isRegularFile(prior) && !Files.isSymbolicLink(prior)) { "unexpected visual attempt ledger object" }
        val old = mapper.readTree(prior.toFile())
        val oldSession = old.path("visual_session_id").asText()
        val oldIdentity = old.path("world_ownership_id").asText()
        require(oldSession.isNotBlank() && oldIdentity.isNotBlank()) { "prior attempt lacks immutable session/world identity" }
        val oldWorld = Path.of(old.path("world_directory").asText()).toRealPath()
        require(visualWorldIdentity(oldWorld) == oldIdentity) { "prior failed world was removed or its ownership marker changed" }
        require(oldSession != sessionId && oldWorld != actualWorld && oldIdentity != ownershipId) { "prior/failed visual world or copied ownership marker cannot be reused" }
    } }
}
internal fun validateVisualReview(path: Path, runId: String, pair: CandidatePair, pendingCreated: Instant, forbiddenWorlds: Set<String>): VisualAcceptance {
    val review = mapper.readTree(path.toFile())
    val schema = review.path("schema").asText()
    val roleNeutral = schema == "bc.in_world_visual_review.v2"
    require((schema == "bc.in_world_visual_review.v1" || roleNeutral) && review.path("status").asText() == "passed" &&
        review.path("release_run_id").asText() == runId && review.path("candidates") == mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(pair.document())) { "independent visual review is missing or for a different exact candidate" }
    val reviewer = review.path("reviewer")
    // v1 remains human-only. v2 records the actual authorized reviewer, never a fabricated human attestation.
    require(!roleNeutral || (!review.has("human_approved") && !reviewer.has("human_approved") &&
        review.path("screenshots").none { it.has("human_approved") })) { "v2 requires role-neutral approvals, not human approval flags" }
    val approvalField = if (roleNeutral) "review_approved" else "human_approved"
    val approved = review.path(approvalField)
    require(reviewer.path("kind").asText() in (if (roleNeutral) setOf("human", "agent") else setOf("human")) &&
        reviewer.path("name").asText().isNotBlank() && reviewer.path("approval_reference").asText().isNotBlank() &&
        (!roleNeutral || approved.isBoolean) && approved.asBoolean()) { "actual independent visual approval with reviewer identity/reference is required; receipt structure cannot infer it" }
    val sessionSnapshot = mapper.treeToValue(review.path("capture_session"), FreshInputSnapshot::class.java)
    requireSnapshot(sessionSnapshot)
    val sessionPath = Path.of(sessionSnapshot.path).toAbsolutePath().normalize()
    val session = mapper.readTree(sessionPath.toFile())
    val sessionId = session.path("run_id").asText()
    val worldPath = Path.of(session.path("world_directory").asText()).toRealPath()
    val world = worldPath.toString()
    val ownershipId = visualWorldIdentity(worldPath)
    require(ownershipId == session.path("world_ownership_id").asText()) { "visual session is not bound to its actual owned world marker" }
    val started = Instant.parse(session.path("started_at").asText())
    val finished = Instant.parse(session.path("completed_at").asText())
    val reviewed = Instant.parse(reviewer.path("reviewed_at").asText())
    require(session.path("schema").asText() == "bc.in_world_visual_session.v1" && session.path("status").asText() == "passed" &&
        session.path("candidates") == review.path("candidates") && sessionId.isNotBlank() && sessionId != runId &&
        session.path("fresh_world").asBoolean() && session.path("world_reused").isBoolean && !session.path("world_reused").asBoolean() &&
        session.path("synthetic_mouse_input").isBoolean && !session.path("synthetic_mouse_input").asBoolean() &&
        session.path("world_ownership_id").asText().matches(Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")) &&
        worldPath.startsWith(sessionPath.parent.toRealPath()) &&
        world !in forbiddenWorlds.map { Path.of(it).toRealPath().toString() } &&
        !started.isBefore(pendingCreated) && !finished.isBefore(started) && !reviewed.isBefore(finished) && !reviewed.isAfter(Instant.now())) {
        "visual session must be a new successful owned in-world client session without synthetic mouse input, never a reused/failed runtime world"
    }
    val inputLog = mapper.treeToValue(session.path("input_log"), FreshInputSnapshot::class.java)
    val events = mapper.treeToValue(session.path("events"), FreshInputSnapshot::class.java)
    require(inputLog.kind == "file" && events.kind == "file"); requireSnapshot(inputLog); requireSnapshot(events)
    require(Path.of(inputLog.path).toRealPath().startsWith(sessionPath.parent.toRealPath()) && Path.of(events.path).toRealPath().startsWith(sessionPath.parent.toRealPath()))
    val raw = Files.readAllLines(Path.of(events.path)).filter { it.isNotBlank() }.map { mapper.readTree(it) }
    require(raw.count { it.path("type").asText() == "client_joined" } == 1 &&
        raw.count { it.path("type").asText() == "session_finished" } == 1 && raw.single { it.path("type").asText() == "session_finished" }.path("success").asBoolean() &&
        raw.none { it.path("type").asText() in setOf("failure", "reconnect", "synthetic_mouse_input") } &&
        raw.any { it.path("type").asText() == "validated" && Path.of(it.path("world_save").asText()).toAbsolutePath().normalize().parent.toString() == world } &&
        raw.any { it.path("type").asText() == "process_cleanup" && it.path("complete").asBoolean() && it.path("surviving_pids").isArray && it.path("surviving_pids").isEmpty }) {
        "visual world must have actual successful join/save/validation/cleanup evidence"
    }
    val lifecycle = listOf("client_joined", "client_disconnected", "server_saved_and_stopped", "validated", "process_cleanup", "session_finished").map { type ->
        raw.indices.filter { raw[it].path("type").asText() == type }.also { require(it.size == 1) { "visual session lifecycle incomplete/retried" } }.single()
    }
    require(lifecycle.zipWithNext().all { (a, b) -> a < b }) { "visual session save/cleanup lifecycle out of order" }
    require(reviewer.path("input_log_reviewed").asBoolean() && reviewer.path("screenshots_reviewed_in_world").asBoolean()) { "reviewer must review actual screenshots AND input log provenance" }
    val screenshots = review.path("screenshots").toList()
    val required = setOf("plants", "trees", "bottle-fluid", "kettle-gui", "badge")
    require(screenshots.map { it.path("category").asText() }.toSet() == required) { "missing or unsupported required in-world visual category" }
    val screenshotBindings = screenshots.map { image ->
        val captured = Instant.parse(image.path("captured_at").asText())
        val snapshot = mapper.treeToValue(image.path("file"), FreshInputSnapshot::class.java)
        val imagePath = Path.of(snapshot.path).toRealPath()
        val imageApproved = image.path(approvalField)
        require(imagePath.startsWith(sessionPath.parent.toRealPath()) && image.path("capture_kind").asText() == "live-client-screenshot" &&
            image.path("context").asText() == "in-world" && (!roleNeutral || imageApproved.isBoolean) && imageApproved.asBoolean() &&
            !captured.isBefore(started) && !captured.isAfter(finished)) { "static art/title-only/out-of-session screenshot cannot satisfy visual acceptance" }
        requireSnapshot(snapshot)
        val decoded = javax.imageio.ImageIO.read(imagePath.toFile())
        require(imagePath.name.endsWith(".png", ignoreCase = true) && decoded != null && decoded.width >= 320 && decoded.height >= 180) { "actual full client PNG screenshot required" }
        snapshot
    }
    require(screenshotBindings.map { it.path }.toSet().size == screenshotBindings.size && screenshotBindings.map { it.sha256 }.toSet().size == screenshotBindings.size) { "each visual view must reference an independently captured screenshot file" }
    requireStableCandidates(pair)
    return VisualAcceptance(sessionId, world, ownershipId, listOf(fileSnapshot(path), sessionSnapshot, inputLog, events, fileSnapshot(worldPath.resolve(".bc-visual-world-id"))) + screenshotBindings)
}

internal fun validatePendingVisualSeal(pendingPath: Path, resultPath: Path, pair: CandidatePair): com.fasterxml.jackson.databind.JsonNode {
    val pending = mapper.readTree(pendingPath.toFile())
    val result = mapper.readTree(resultPath.toFile())
    require(pending.path("schema").asText() == "bc.fresh_pending_visual.v1" && pending.path("status").asText() == "awaiting-visual" &&
        pending.path("run_id").asText().isNotBlank() && pending.path("run_id") == result.path("run_id") &&
        pending.path("candidates") == mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(pair.document()) &&
        result.path("status").asText() == "blocked" && result.path("blocker_code").asText() == "visual-review-required" &&
        result.path("pending_sha256").asText() == Hashes.sha256(pendingPath)) { "only an immutable successful-runtime seal blocked SOLELY on visual review can be finalized" }
    require(pending.path("proofs").isArray && !pending.path("proofs").isEmpty) { "pending visual seal lacks bound source/runtime/inventory proofs" }
    pending.path("proofs").forEach { requireSnapshot(mapper.treeToValue(it, FreshInputSnapshot::class.java)) }
    requireStableCandidates(pair)
    return pending
}

private fun finalizeVisualRelease(root: Path, pendingPath: Path, reviewPath: Path) {
    requireFreshRuntimeAuthorization(System.getenv())
    require(!System.getenv("BC_PACK_TEST_LOCK_TOKEN").isNullOrBlank()) { "finalization requires release mutex" }
    val pending = mapper.readTree(pendingPath.toFile())
    val runId = pending.path("run_id").asText()
    require(runId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")))
    val evidence = root.resolve("generated/test-evidence/$runId/release").toAbsolutePath().normalize()
    require(pendingPath.toRealPath() == evidence.resolve("pending-visual.json").toRealPath() &&
        pending.path("schema").asText() == "bc.fresh_pending_visual.v1" && pending.path("status").asText() == "awaiting-visual") { "no valid successful-runtime visual-pending seal" }
    val result = mapper.readTree(evidence.resolve("fresh-release-result.json").toFile())
    require(result.path("status").asText() == "blocked" && result.path("blocker_code").asText() == "visual-review-required" &&
        result.path("pending_sha256").asText() == Hashes.sha256(pendingPath)) { "failed runtime/release cannot be promoted" }
    val candidate = pending.path("candidates")
    val pair = CandidateLocator.locate(Path.of(candidate.path("client").asText()), Path.of(candidate.path("server").asText()))
    require(candidate == mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(pair.document())) { "candidate bytes changed after runtime" }
    validatePendingVisualSeal(pendingPath, evidence.resolve("fresh-release-result.json"), pair)
    val proofs = pending.path("proofs").map { mapper.treeToValue(it, FreshInputSnapshot::class.java) }
    require(proofs.isNotEmpty()); proofs.forEach(::requireSnapshot)
    validateWorkspacePreservation(evidence.resolve("pre-mutation-backup/receipt.json"))
    val provenance = mapper.readTree(evidence.resolve("provenance.json").toFile())
    val plan = mapper.treeToValue(provenance.path("plan"), FreshSourcePlan::class.java)
    val mods = provenance.path("mods").map { mapper.treeToValue(it.path("definition"), ActiveMod::class.java) }
    val inventory = mapper.readTree(evidence.resolve("archive-members.json").toFile())
    val current = validatePackagedInventory(root, pair, mods)
    require(inventory.path("members") == mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(current)) { "full ZIP member inventory changed" }
    val currentClient = validateMaterializedClientBytes(root, runId, pair, mods)
    require(mapper.readTree(evidence.resolve("client-binary-inventory.json").toFile()) == mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(currentClient)) { "materialized client byte inventory changed since Smoke" }
    requireRawCustomMembers(current, plan.sources.associate { source -> mods.single { it.repository == source.repository }.artifact to source.rawSha256 } + (FROZEN_WLM_ARTIFACT to FROZEN_WLM_SHA256))
    val start = Instant.parse(pending.path("runtime_started_at").asText())
    validateSmokeEvidence(evidence.parent.resolve("smoke/run.json"), runId, pair, start)
    validateTeleSoakReceipt(evidence.parent.resolve("tele-soak/run.json"), runId, pair, start)
    val attempts = evidence.resolve("visual-finalizations").also { it.createDirectories() }
    val pendingSnapshot = fileSnapshot(pendingPath)
    val resultSnapshot = fileSnapshot(evidence.resolve("fresh-release-result.json"))
    val attempted = runCatching {
        val review = mapper.readTree(reviewPath.toFile())
        val snapshot = mapper.treeToValue(review.path("capture_session"), FreshInputSnapshot::class.java)
        requireSnapshot(snapshot)
        mapper.readTree(Path.of(snapshot.path).toFile())
    }.getOrNull()
    fun audit(status: String): Path {
        val path = attempts.resolve("${java.util.UUID.randomUUID()}.json")
        writeNewJson(path, mapOf("created_at" to Instant.now().toString(), "status" to status,
            "review_path" to reviewPath.toAbsolutePath().normalize().toString(),
            "visual_session_id" to attempted?.path("run_id")?.asText(),
            "world_ownership_id" to attempted?.path("world_ownership_id")?.asText(),
            "world_directory" to attempted?.path("world_directory")?.asText()?.takeIf { it.isNotBlank() }?.let {
                runCatching { Path.of(it).toRealPath().toString() }.getOrElse { _ -> Path.of(it).toAbsolutePath().normalize().toString() }
            }))
        return path
    }
    try {
        val accepted = validateVisualReview(reviewPath, runId, pair, Instant.parse(pending.path("created_at").asText()), pending.path("runtime_worlds").map { it.asText() }.toSet())
        requireUnusedVisualWorld(attempts, accepted.sessionId, Path.of(accepted.world), accepted.ownershipId)
        val allProofs = proofs + accepted.bindings + pendingSnapshot + resultSnapshot
        allProofs.forEach(::requireSnapshot); requireStableCandidates(pair)
        val attempt = audit("validated")
        writeNewJson(pair.release.resolve("ready.json"), mapOf("schema" to "bc.release_ready.v3", "run_id" to runId, "status" to "passed",
            "created_at" to Instant.now().toString(), "candidates" to pair.document(), "required_gates" to listOf("smoke", "tele-soak", "in-world-visual"),
            "pending" to pendingSnapshot, "visual_review" to fileSnapshot(reviewPath), "proofs" to allProofs + fileSnapshot(attempt),
            "fresh_source_count" to 48, "frozen_wlm_sha256" to FROZEN_WLM_SHA256, "frozen_supervisor_sha256" to FROZEN_SUPERVISOR_SHA256))
        println("fresh release ready after real in-world visual acceptance: ${pair.release.resolve("ready.json")}")
    } catch (error: Exception) {
        runCatching { audit("failed") }.exceptionOrNull()?.let(error::addSuppressed)
        throw error
    }
}

private fun capture(cwd: Path, command: List<String>): String {
    val process = ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    require(process.waitFor() == 0) { "command failed: ${command.joinToString(" ")}" }
    return output
}
