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
)

data class ActiveModManifest(val schema: String, val mods: List<ActiveMod>)
data class BuiltMod(val definition: ActiveMod, val commit: String, val jar: Path, val sha256: String, val mode: String)
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
    require(args.size == 3) { "usage: ReleasePipeline ROOT JOBS SKIP_TESTS" }
    val root = Path.of(args[0]).toAbsolutePath().normalize()
    val jobs = args[1].toInt()
    val skipTests = args[2].toBooleanStrict()
    require(jobs in 1..4) { "release jobs must be between 1 and 4" }
    val workspace = root.parent
    val manifest: ActiveModManifest = mapper.readValue(root.resolve("gradle/active-custom-mods.json").toFile())
    require(manifest.schema == "bc.active_custom_mods.v1") { "unexpected active-mod manifest schema" }
    require(manifest.mods.size == 33) { "fresh dist requires exactly 33 active custom mods" }
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
        if (!repository.resolve(".git").exists()) "${mod.repository}\tmissing" else {
            val status = capture(repository, listOf("git", "status", "--porcelain"))
            if (status.isBlank()) "${mod.repository}\tclean" else "${mod.repository}\tdirty\t${status.replace('\n', ' ')}"
        }
    }
    Files.writeString(evidence.resolve("preflight.tsv"), preflight.joinToString("\n", postfix = "\n"))
    require(preflight.all { it.endsWith("\tclean") }) { "all active repositories must exist and be clean; see ${evidence.resolve("preflight.tsv")}" }

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
    val sourceReflection = sourceReflectionViolations(workspace, customRepositories, reflectionAllowances)
    Files.writeString(
        evidence.resolve("reflection-source-audit.tsv"),
        if (sourceReflection.isEmpty()) "PASS\n" else sourceReflection.joinToString("\n", postfix = "\n"),
    )
    require(sourceReflection.isEmpty()) {
        "custom source uses forbidden reflection; see ${evidence.resolve("reflection-source-audit.tsv")}"
    }

    val dependencyWarmup = root.resolve("build/release-dependency-warmup/$runId")
    try {
        runLogged(root, packageResolveCommand(root, dependencyWarmup), evidence.resolve("dependency-cache.log"))
    } finally {
        dependencyWarmup.toFile().deleteRecursively()
    }

    val built = mutableListOf<BuiltMod>()
    val failures = mutableListOf<String>()
    val pending = manifest.mods.associateBy { it.repository }.toMutableMap()
    while (pending.isNotEmpty() && failures.isEmpty()) {
        val completed = built.map { it.definition.repository }.toSet()
        val ready = pending.values.filter { mod -> mod.dependsOn.all { it in completed } }
        require(ready.isNotEmpty()) { "active-mod release dependencies contain a cycle: ${pending.keys.sorted()}" }
        val executor = Executors.newFixedThreadPool(jobs)
        val futures = ready.associateWith { mod ->
            executor.submit(Callable {
                build(root, workspace, evidence, staging, mod, sourceUpdatesByRepository.getValue(mod.repository), skipTests)
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

    runLogged(root, listOf(root.resolve("test.main.kts").toString(), "frugal"), evidence.resolve("frugal.log"))
    runLogged(root, listOf(root.resolve("dist.sh").toString()), evidence.resolve("dist.log"))
    val candidates = CandidateLocator.locate(root)
    val provenance = mapOf(
        "schema" to "bc.fresh_dist_provenance.v1",
        "created_at" to Instant.now().toString(),
        "tests_skipped" to skipTests,
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
            )
        },
    )
    mapper.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("provenance.json").toFile(), provenance)
    println("fresh candidate prepared once; tests have not run yet")
    println("release evidence: $evidence")
}

private fun build(
    root: Path,
    workspace: Path,
    evidence: Path,
    staging: Path,
    mod: ActiveMod,
    sourceUpdate: SourceUpdate,
    skipTests: Boolean,
): BuiltMod {
    val repository = workspace.resolve("mod_source").resolve(mod.repository)
    val log = evidence.resolve("mod-${mod.repository}.log")
    val bundled = root.resolve("mods").resolve(mod.artifact)
    val staged = staging.resolve(mod.artifact)
    if (sourceUpdate.status == "same") {
        require(Files.isRegularFile(bundled) && jarDeclaresMod(bundled, mod.modId)) {
            "unchanged ${mod.repository} has no valid bundled artifact to reuse"
        }
        Files.copy(bundled, staged, StandardCopyOption.REPLACE_EXISTING)
        val commit = sourceUpdate.localCommit
        return BuiltMod(mod, commit, staged, Hashes.sha256(staged), "reused")
    }
    val tasks = if (skipTests) listOf("stageRuntimeJar") else mod.tasks
    val buildCommand = listOf(repository.resolve("gradlew").toString(), "--no-daemon", "clean") + tasks
    runLogged(repository, buildCommand, log, providerBuildEnvironment(staging))
    val jar = repository.resolve("build/libs").resolve(mod.artifact)
    require(Files.isRegularFile(jar)) { "${mod.repository} did not stage ${mod.artifact}" }
    require(jarDeclaresMod(jar, mod.modId)) { "${mod.artifact} does not declare ${mod.modId}" }
    Files.copy(jar, staged, StandardCopyOption.REPLACE_EXISTING)
    val commit = capture(repository, listOf("git", "rev-parse", "HEAD")).trim()
    annotateJar(staged, mod, commit)
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
