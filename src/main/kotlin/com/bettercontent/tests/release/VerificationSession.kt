package com.bettercontent.tests.release

import com.bettercontent.tests.Hashes
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID

internal data class VerifiedArtifact(
    val source: String,
    val sha256: String,
    val dependencies: Map<String, String>,
    val tasks: List<String>,
    val verification_id: String,
    val support_hashes: Map<String, String> = emptyMap(),
)
internal data class ProviderCheckpoint(
    val source: String,
    val sha256: String,
    val dependencies: Map<String, String>,
    val tasks: List<String>,
    val verification_id: String,
    val support_hashes: Map<String, String>,
    val shared_fingerprint: String,
    val owner: String,
    val staged_path: String,
)
internal data class VerificationReceipt(
    val schema: String = "bc.testing_session.v1",
    val session_id: String,
    val owner: String,
    val generation: Int = 0,
    val baseline: String = "pending",
    val shared_fingerprint: String? = null,
    val mods: Map<String, VerifiedArtifact> = emptyMap(),
    val checkpoints: Map<String, ProviderCheckpoint> = emptyMap(),
    val invalidations: List<String> = listOf("baseline not started"),
    val active_run: String? = null,
    val helpers: Set<String> = emptySet(),
    val shared_context: Map<String, String> = emptyMap(),
)

/** Conservative invalidation, including the full transitive consumer closure. */
internal fun affectedProviders(mods: List<ActiveMod>, invalid: Set<String>): Set<String> {
    val names = mods.map { it.repository }.toSet()
    require(names.size == mods.size && mods.all { it.dependsOn.all(names::contains) }) { "invalid provider graph" }
    val pending = mods.associateBy { it.repository }.toMutableMap()
    val visited = mutableSetOf<String>()
    while (pending.isNotEmpty()) {
        val ready = pending.values.filter { it.dependsOn.all(visited::contains) }
        require(ready.isNotEmpty()) { "provider graph contains a cycle" }
        ready.forEach { visited += it.repository; pending.remove(it.repository) }
    }
    val result = invalid.toMutableSet()
    do {
        val size = result.size
        mods.filter { it.dependsOn.any(result::contains) }.forEach { result += it.repository }
    } while (size != result.size)
    return result
}

internal class VerificationSession(
    private val root: Path,
    private val mods: List<ActiveMod>,
    private val runId: String,
    private val rebuildAll: Boolean,
    private val owner: String = System.getenv("PI_SESSION_ID") ?: System.getenv("BC_OWNER_SESSION")
        ?: if (System.getenv("BC_VERIFICATION_SESSION") == null) "invocation:${UUID.randomUUID()}"
        else error("stable conversation identity unavailable; set BC_OWNER_SESSION explicitly"),
    private val supplied: String? = System.getenv("BC_VERIFICATION_SESSION"),
    private val sharedInputs: List<String> = listOf("**"),
) : AutoCloseable {
    private val mapper = jacksonObjectMapper()
    private val directory = root.resolve(".worklane/testing-sessions")
    val id: String = supplied ?: UUID.randomUUID().toString().replace("-", "")
    private val path: Path
    private lateinit var receipt: VerificationReceipt
    private var acquired = false
    private var completed = false
    private val sources = mods.associate { it.repository to sourceIdentity(root.parent.resolve("mod_source/${it.repository}")) }
    private val shared = sharedIdentity()
    val affected: Set<String>
    val fullBaseline: Boolean

    init {
        affectedProviders(mods, emptySet()) // Reject cycles before creating an invocation receipt.
        require(Regex("[a-f0-9]{32}").matches(id)) { "invalid verification session ID" }
        require(Regex("[A-Za-z0-9_-]{1,80}").matches(runId)) { "invalid verification run ID" }
        require(!Files.isSymbolicLink(directory) && !Files.isSymbolicLink(directory.parent)) { "session directory is a symlink" }
        Files.createDirectories(directory)
        path = directory.resolve("$id.json")
        locked {
            require(!Files.isSymbolicLink(path)) { "session receipt is a symlink" }
            if (supplied == null) save(VerificationReceipt(session_id = id, owner = owner))
            receipt = read()
            require(receipt.active_run == null) { "verification session is already in use by ${receipt.active_run}" }
        }
        fullBaseline = rebuildAll || receipt.shared_fingerprint != shared ||
            (receipt.mods.isEmpty() && receipt.checkpoints.isEmpty())
        val prior = if (rebuildAll || receipt.shared_fingerprint != shared) {
            receipt.copy(mods = emptyMap(), checkpoints = emptyMap())
        } else receipt
        val invalid = mods.filter { mod ->
            val checkpoint = prior.checkpoints[mod.repository]
            val verified = prior.mods[mod.repository]
            val dependencies = mod.dependsOn.associateWith { dependency ->
                prior.checkpoints[dependency]?.sha256 ?: prior.mods[dependency]?.sha256 ?: "missing"
            }
            when {
                checkpoint != null -> !checkpointValid(mod, checkpoint, dependencies)
                verified != null -> {
                    val jar = root.resolve("mods/${mod.artifact}")
                    verified.source != sources.getValue(mod.repository) || verified.tasks != mod.tasks ||
                        !Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) || Hashes.sha256(jar) != verified.sha256 ||
                        verified.dependencies != dependencies ||
                        verified.support_hashes != supportArtifactHashes(
                            root.parent.resolve("mod_source/${mod.repository}"), mod, allowMissing = true)
                }
                else -> true
            }
        }.map { it.repository }.toSet()
        affected = affectedProviders(mods, if (fullBaseline) mods.map { it.repository }.toSet() else invalid)
        locked {
            require(read() == receipt) { "session changed while preparing verification" }
            receipt = prior.copy(baseline = "verifying", active_run = runId,
                shared_fingerprint = shared,
                checkpoints = prior.checkpoints.filterKeys { it !in affected },
                invalidations = if (fullBaseline) listOf("full baseline required") else affected.sorted())
            save(receipt)
            acquired = true
        }
    }

    fun requireReusable(item: BuiltMod) {
        val checkpoint = receipt.checkpoints[item.definition.repository]
        val expectedSha = checkpoint?.sha256 ?: receipt.mods.getValue(item.definition.repository).sha256
        val expectedSupport = checkpoint?.support_hashes ?: receipt.mods.getValue(item.definition.repository).support_hashes
        require(item.sha256 == expectedSha && item.supportHashes == expectedSupport &&
            Hashes.sha256(item.jar) == expectedSha) {
            "artifact changed after reuse decision: ${item.definition.repository}"
        }
        if (checkpoint != null) require(checkpointValid(item.definition, checkpoint,
            item.definition.dependsOn.associateWith { dependency ->
                receipt.checkpoints[dependency]?.sha256 ?: receipt.mods[dependency]?.sha256 ?: "missing"
            })) { "checkpoint changed after reuse decision: ${item.definition.repository}" }
    }

    /** Called only after this provider's complete tasks and bytecode gate have succeeded. */
    @Synchronized fun checkpoint(item: BuiltMod, dependencyHashes: Map<String, String>) {
        val mod = item.definition
        require(mod in mods && mod.repository in affected) { "provider is not scheduled for verification" }
        require(item.mode == "rebuilt") { "only a freshly verified provider can be checkpointed" }
        require(item.commit == git(root.parent.resolve("mod_source/${mod.repository}"), "rev-parse", "HEAD").trim() &&
            sourceIdentity(root.parent.resolve("mod_source/${mod.repository}")) == sources.getValue(mod.repository)) {
            "provider source changed before checkpoint: ${mod.repository}"
        }
        require(sharedIdentity() == shared) { "shared inputs changed before checkpoint" }
        require(dependencyHashes.keys == mod.dependsOn.toSet()) { "checkpoint dependency set differs from manifest" }
        requireConsumedStages(mod, dependencyHashes)
        require(Files.isRegularFile(item.jar, LinkOption.NOFOLLOW_LINKS) && Hashes.sha256(item.jar) == item.sha256) {
            "staged provider bytes changed before checkpoint"
        }
        require(supportArtifactHashes(root.parent.resolve("mod_source/${mod.repository}"), mod) == item.supportHashes) {
            "support artifact changed before checkpoint"
        }
        val destination = checkpointPath(mod, runId)
        requireNoSymlink(destination)
        Files.createDirectories(destination.parent)
        val temporary = Files.createTempFile(destination.parent, ".checkpoint-", ".jar")
        try {
            Files.copy(item.jar, temporary, StandardCopyOption.REPLACE_EXISTING)
            require(Hashes.sha256(temporary) == item.sha256 && Hashes.sha256(item.jar) == item.sha256) {
                "provider bytes changed during checkpoint copy"
            }
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                require(Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) &&
                    Hashes.sha256(destination) == item.sha256) { "divergent checkpoint candidate" }
            } else Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE)
        } finally { Files.deleteIfExists(temporary) }
        require(Hashes.sha256(destination) == item.sha256) { "checkpoint candidate changed" }
        requireConsumedStages(mod, dependencyHashes)
        locked {
            val current = read()
            require(current.active_run == runId && current.owner == receipt.owner) { "session ownership changed before checkpoint" }
            require(current == receipt) { "session receipt changed outside this verification run" }
            require(dependencyHashes == mod.dependsOn.associateWith { dependency ->
                val provider = mods.single { it.repository == dependency }
                val checkpoint = current.checkpoints[dependency]
                if (dependency in affected) {
                    require(checkpoint?.verification_id == runId) { "affected dependency has no current checkpoint: $dependency" }
                }
                if (checkpoint != null) {
                    val upstream = provider.dependsOn.associateWith { upstreamName ->
                        current.checkpoints[upstreamName]?.sha256 ?: current.mods[upstreamName]?.sha256 ?: "missing"
                    }
                    require(checkpointValid(provider, checkpoint, upstream)) {
                        "consumed dependency checkpoint bytes or inputs changed: $dependency"
                    }
                } else {
                    val verified = current.mods[dependency]
                    val bundled = root.resolve("mods/${provider.artifact}")
                    require(verified != null && verified.source == sources.getValue(dependency) &&
                        verified.tasks == provider.tasks && Files.isRegularFile(bundled, LinkOption.NOFOLLOW_LINKS) &&
                        Hashes.sha256(bundled) == verified.sha256 &&
                        supportArtifactHashes(root.parent.resolve("mod_source/$dependency"), provider) == verified.support_hashes) {
                        "consumed bundled dependency changed: $dependency"
                    }
                }
                checkpoint?.sha256 ?: current.mods[dependency]?.sha256 ?: "missing"
            }) { "checkpoint consumed different dependency bytes" }
            val entry = ProviderCheckpoint(sources.getValue(mod.repository), item.sha256,
                dependencyHashes, mod.tasks, runId, item.supportHashes, shared,
                current.owner, root.relativize(destination).toString())
            require(checkpointValid(mod, entry, dependencyHashes)) { "checkpoint failed final validation" }
            receipt = current.copy(checkpoints = current.checkpoints + (mod.repository to entry))
            save(receipt)
        }
    }

    /** The compiler consumes these current-run staged bytes, not the immutable snapshots. */
    fun requireConsumedStages(mod: ActiveMod, dependencyHashes: Map<String, String>) {
        require(dependencyHashes.keys == mod.dependsOn.toSet()) { "staged dependency set differs from manifest" }
        mod.dependsOn.forEach { dependency ->
            val provider = mods.single { it.repository == dependency }
            val staged = root.resolve("generated/test-evidence/$runId/release/staged-jars/${provider.artifact}")
            requireNoSymlink(staged)
            require(Files.isRegularFile(staged, LinkOption.NOFOLLOW_LINKS) &&
                Hashes.sha256(staged) == dependencyHashes.getValue(dependency)) {
                "consumed current-run staged dependency changed: $dependency"
            }
        }
    }

    /** Returns only byte-validated checkpoint output; bundled complete-baseline reuse stays in build(). */
    fun reusable(mod: ActiveMod): BuiltMod? {
        if (mod.repository in affected) return null
        val checkpoint = receipt.checkpoints[mod.repository] ?: return null
        val dependencies = mod.dependsOn.associateWith { dependency ->
            receipt.checkpoints[dependency]?.sha256 ?: receipt.mods[dependency]?.sha256 ?: "missing"
        }
        require(checkpointValid(mod, checkpoint, dependencies)) { "checkpoint invalidated before reuse: ${mod.repository}" }
        val source = checkpointPath(mod, checkpoint.verification_id)
        val destination = root.resolve("generated/test-evidence/$runId/release/staged-jars/${mod.artifact}")
        requireNoSymlink(destination)
        Files.createDirectories(destination.parent)
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
        require(Hashes.sha256(source) == checkpoint.sha256 && Hashes.sha256(destination) == checkpoint.sha256) {
            "checkpoint bytes changed during staging"
        }
        return BuiltMod(mod, git(root.parent.resolve("mod_source/${mod.repository}"), "rev-parse", "HEAD").trim(),
            destination, checkpoint.sha256, "checkpoint-reused", checkpoint.support_hashes)
    }

    private fun checkpointPath(mod: ActiveMod, originRun: String): Path {
        require(Regex("[A-Za-z0-9_-]{1,80}").matches(originRun)) { "invalid checkpoint origin" }
        require(Regex("[A-Za-z0-9._-]+\\.jar").matches(mod.artifact)) { "invalid checkpoint artifact name" }
        return root.resolve("generated/test-evidence/$originRun/release/verified-checkpoints/${mod.artifact}")
    }

    private fun requireNoSymlink(path: Path) {
        val base = root.toAbsolutePath().normalize()
        val target = path.toAbsolutePath().normalize()
        require(target.startsWith(base)) { "checkpoint path escaped pack root" }
        var current = base
        require(!Files.isSymbolicLink(current)) { "pack root is a symlink" }
        base.relativize(target).forEach { part ->
            current = current.resolve(part)
            require(!Files.isSymbolicLink(current)) { "checkpoint path contains a symlink: $current" }
        }
    }

    private fun checkpointValid(mod: ActiveMod, checkpoint: ProviderCheckpoint,
        dependencies: Map<String, String>): Boolean = runCatching {
        val candidate = checkpointPath(mod, checkpoint.verification_id)
        require(checkpoint.owner == receipt.owner && checkpoint.shared_fingerprint == shared &&
            checkpoint.source == sources.getValue(mod.repository) && checkpoint.tasks == mod.tasks &&
            checkpoint.dependencies == dependencies &&
            checkpoint.staged_path == root.relativize(candidate).toString())
        requireNoSymlink(candidate)
        Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS) && Hashes.sha256(candidate) == checkpoint.sha256 &&
            supportArtifactHashes(root.parent.resolve("mod_source/${mod.repository}"), mod, allowMissing = true) == checkpoint.support_hashes
    }.getOrDefault(false)

    fun checkInputs() {
        locked { require(read() == receipt && receipt.active_run == runId) {
            "session receipt changed before deployment"
        } }
        require(sharedIdentity() == shared) { "shared verification inputs changed during verification" }
        mods.forEach { mod ->
            require(sourceIdentity(root.parent.resolve("mod_source/${mod.repository}")) == sources.getValue(mod.repository)) {
                "${mod.repository} source changed during verification; refusing deployment/provenance"
            }
        }
    }

    /** Full-graph deployment gate; returns original per-provider verification identities. */
    @Synchronized fun validateComplete(built: List<BuiltMod>): Map<String, String> {
        checkInputs()
        val hashes = built.associate { it.definition.repository to it.sha256 }
        require(built.size == mods.size && hashes.keys == mods.map { it.repository }.toSet()) {
            "incomplete or duplicate baseline cannot authorize reuse"
        }
        return built.associate { item ->
            val mod = item.definition
            require(mod in mods) { "unrecognized provider in baseline" }
            require(Hashes.sha256(item.jar) == item.sha256) { "staged verified artifact changed" }
            require(supportArtifactHashes(root.parent.resolve("mod_source/${mod.repository}"), mod) == item.supportHashes) { "verified support artifact changed" }
            val checkpoint = receipt.checkpoints[mod.repository]
            val dependencies = mod.dependsOn.associateWith { hashes.getValue(it) }
            if (mod.repository in affected) {
                require(checkpoint != null && checkpoint.verification_id == runId && checkpoint.sha256 == item.sha256 &&
                    checkpoint.support_hashes == item.supportHashes && checkpointValid(mod, checkpoint, dependencies)) {
                    "rebuilt provider lacks a valid current-run checkpoint: ${mod.repository}"
                }
            } else requireReusable(item)
            mod.repository to (checkpoint?.verification_id ?: receipt.mods.getValue(mod.repository).verification_id)
        }
    }

    @Synchronized fun commit(built: List<BuiltMod>) {
        val origins = validateComplete(built)
        val hashes = built.associate { it.definition.repository to it.sha256 }
        val verified = built.associate { item ->
            val mod = item.definition
            mod.repository to VerifiedArtifact(sources.getValue(mod.repository), item.sha256,
                mod.dependsOn.associateWith { hashes.getValue(it) }, mod.tasks,
                origins.getValue(mod.repository), item.supportHashes)
        }
        locked {
            require(read() == receipt && receipt.active_run == runId) {
                "session receipt changed during verification"
            }
            save(receipt.copy(generation = receipt.generation + if (receipt.mods.isEmpty()) 1 else 0,
                baseline = "complete", shared_fingerprint = shared, shared_context = sharedContext(), mods = verified,
                checkpoints = emptyMap(), invalidations = emptyList(), active_run = null))
        }
        completed = true
    }

    override fun close() {
        if (!acquired) return
        locked {
            val current = read()
            if (!completed && current.active_run == runId) save(current.copy(baseline = "failed", active_run = null))
            if (supplied == null) Files.deleteIfExists(path)
        }
    }

    private fun read(): VerificationReceipt {
        require(Files.size(path) <= 1024 * 1024) { "oversized session receipt" }
        val value: VerificationReceipt = mapper.readValue(path.toFile())
        require(value.schema == "bc.testing_session.v1" && (value.owner == owner || owner in value.helpers) && value.session_id == id) {
            "verification session belongs to another conversation"
        }
        return value
    }

    private fun save(value: VerificationReceipt) {
        val temporary = Files.createTempFile(directory, ".receipt-", ".json")
        try {
            mapper.writeValue(temporary.toFile(), value)
            require(Files.size(temporary) <= 1024 * 1024) { "session receipt exceeds bounded size" }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun <T> locked(action: () -> T): T {
        val lock = directory.resolve(".lock")
        require(!Files.isSymbolicLink(lock)) { "session lock is a symlink" }
        return FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use { action() }
        }
    }

    private fun sharedContext(): Map<String, String> = sharedEntries().toMap() + mapOf(
        "_patterns" to sharedInputs.joinToString("\u0000"), "_java_home" to System.getProperty("java.home"))

    private fun sharedIdentity(): String = fingerprint(sharedEntries())

    private fun sharedEntries(): List<Pair<String, String>> {
        // Pack contracts are external verification inputs. Release-produced JAR/index files
        // cannot be included: deployment changes them by design. All other tracked pack inputs
        // conservatively require a fresh baseline, rather than silently bypassing a custom gate.
        require(sharedInputs.isNotEmpty() && sharedInputs.all { !it.startsWith("/") && ".." !in it.split('/') }) {
            "shared verification inputs must be nonempty workspace-relative patterns"
        }
        val matchers = sharedInputs.map { root.fileSystem.getPathMatcher("glob:$it") }
        val names = git(root, "ls-files", "-z", "--cached", "--others", "--exclude-standard")
            .split('\u0000').filter { it.isNotBlank() }.distinct()
            .filter { name -> matchers.any { it.matches(Path.of(name)) } }
            .filterNot { it == "index.toml" || (it.startsWith("mods/") && it.endsWith(".jar")) || it.startsWith(".worklane/") }
        return names.map { it to fileIdentity(root.resolve(it)) } + listOf(
            "input_patterns" to sharedInputs.joinToString("\u0000"),
            "manifest" to fileIdentity(root.resolve("gradle/active-custom-mods.json")),
            "java" to (System.getProperty("java.version") + ":" + System.getProperty("java.home")),
            "toolchain" to fileIdentity(Path.of(System.getProperty("java.home")).resolve("release")),
            "sdk" to fileIdentity(root.resolve("docs/toolchains.md")),
        )
    }
}

internal fun sourceIdentity(repo: Path): String {
    val names = git(repo, "ls-files", "-z", "--cached", "--others", "--exclude-standard")
        .split('\u0000').filter { it.isNotBlank() }.distinct().sorted()
    return fingerprint(names.map { it to fileIdentity(repo.resolve(it)) } + listOf(
        "HEAD" to git(repo, "rev-parse", "HEAD"), "status" to git(repo, "status", "--porcelain")))
}
private fun fileIdentity(path: Path): String = when {
    Files.isSymbolicLink(path) -> "link:" + Files.readSymbolicLink(path)
    Files.isRegularFile(path) -> Hashes.sha256(path)
    else -> "missing"
}
private fun fingerprint(entries: List<Pair<String, String>>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    entries.sortedBy { it.first }.forEach { (name, value) -> digest.update("$name\u0000$value\u0000".toByteArray()) }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
private fun git(repo: Path, vararg args: String): String {
    return com.bettercontent.tests.BoundedCommand.capture(repo, listOf("git") + args, java.time.Duration.ofSeconds(30))
}
