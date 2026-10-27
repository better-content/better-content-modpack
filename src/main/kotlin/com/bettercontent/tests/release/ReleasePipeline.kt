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
    require(args.size == 4) { "usage: ReleasePipeline ROOT JOBS SOAK_MINUTES USE_BUNDLED_MODS" }
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
    val nativeLock = Path.of(System.getProperty("user.home"), ".tmp", "better-content-gradle.lock")
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

internal fun productionFingerprint(repository: Path): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val paths = capture(repository, listOf("git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"))
        .split('\u0000').filter { it.isNotEmpty() && isProductionInput(it) }.distinct().sorted()
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

private fun capture(cwd: Path, command: List<String>): String {
    val process = ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    require(process.waitFor() == 0) { "command failed: ${command.joinToString(" ")}" }
    return output
}
