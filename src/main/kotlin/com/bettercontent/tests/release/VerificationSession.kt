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
    val input_view_sha256: String? = null,
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
    val build_fingerprint: String? = null,
    val build_id: String? = null,
    val input_view_sha256: String? = null,
)
internal data class VerificationReceipt(
    val schema: String = "bc.testing_session.v2",
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
    val state: String = "open",
    val reuse_history: Boolean = true,
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
    private val contracts = mods.associate { it.repository to contractIdentity(it) }
    private val builds = mods.associate { it.repository to buildIdentity(it) }
    private val buildCandidates: Map<String, ProviderCheckpoint>
    @Volatile var affected: Set<String>
        private set
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
            require(receipt.active_run == null && receipt.state == "open") { "verification session is active or ended" }
        }
        // Cross-conversation reuse reads finalized immutable attempts, never active controls.
        val historical = if (rebuildAll || !receipt.reuse_history) emptyMap() else historicalCheckpoints()
        buildCandidates = if (rebuildAll) emptyMap() else
            (if (receipt.reuse_history) historicalCheckpoints(buildOnly = true) else emptyMap()) + receipt.checkpoints
        val prior = if (rebuildAll) receipt.copy(mods = emptyMap(), checkpoints = emptyMap())
            else receipt.copy(checkpoints = historical + receipt.checkpoints)
        fullBaseline = rebuildAll || (prior.mods.isEmpty() && prior.checkpoints.isEmpty())
        val invalid = mods.filter { mod ->
            val checkpoint = prior.checkpoints[mod.repository]
            val dependencies = mod.dependsOn.associateWith { dependency ->
                prior.checkpoints[dependency]?.sha256 ?: prior.mods[dependency]?.sha256 ?: "missing"
            }
            when {
                checkpoint != null -> !checkpointValid(mod, checkpoint, dependencies)
                else -> true
            }
        }.map { it.repository }.toSet()
        affected = affectedProviders(mods, if (fullBaseline) mods.map { it.repository }.toSet() else invalid)
        locked {
            require(read() == receipt) { "session changed while preparing verification" }
            receipt = prior.copy(baseline = "verifying", active_run = runId,
                reuse_history = prior.reuse_history && !rebuildAll,
                shared_fingerprint = shared, shared_context = sharedContext(),
                checkpoints = prior.checkpoints.filterKeys { it !in affected },
                invalidations = if (fullBaseline) listOf("full baseline required") else affected.sorted())
            save(receipt)
            acquired = true
        }
    }

    /** Admit the exact compiler/audit view before the DAG selects reuse versus fresh verification. */
    @Synchronized fun admitInputView(mod: ActiveMod, digest: String) {
        require(mod in mods && Regex("[a-f0-9]{64}").matches(digest)) { "invalid provider input-view admission" }
        if (mod.repository in affected) return
        val checkpoint = receipt.checkpoints[mod.repository]
        val savedDigest = if (checkpoint != null) checkpoint.input_view_sha256
            else receipt.mods[mod.repository]?.input_view_sha256
        if (savedDigest == digest) return
        val promoted = affectedProviders(mods, setOf(mod.repository))
        val nextAffected = (affected + promoted).toSet()
        locked {
            val current = read()
            require(current == receipt && current.active_run == runId && current.owner == receipt.owner) {
                "session ownership changed before input-view admission"
            }
            val updated = current.copy(
                checkpoints = current.checkpoints.filterKeys { it !in promoted },
                invalidations = (current.invalidations + promoted.sorted() +
                    "input view changed: ${mod.repository} (${savedDigest ?: "missing"} -> $digest)").distinct(),
            )
            save(updated)
            receipt = updated
            affected = nextAffected
        }
    }

    fun requireReusable(item: BuiltMod) {
        val checkpoint = receipt.checkpoints[item.definition.repository]
        val expectedSha = checkpoint?.sha256 ?: receipt.mods.getValue(item.definition.repository).sha256
        val expectedSupport = checkpoint?.support_hashes ?: receipt.mods.getValue(item.definition.repository).support_hashes
        require(item.sha256 == expectedSha && item.supportHashes == expectedSupport &&
            item.inputViewSha256 == (if (checkpoint != null) checkpoint.input_view_sha256
                else receipt.mods.getValue(item.definition.repository).input_view_sha256) &&
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
        require(item.inputViewSha256 == null || Regex("[a-f0-9]{64}").matches(item.inputViewSha256)) { "invalid input view digest" }
        require(item.mode in setOf("rebuilt", "build-reused-verified")) { "only a freshly verified provider can be checkpointed" }
        if (item.mode == "build-reused-verified") {
            val origin = buildCandidates[mod.repository]
            require(origin != null && item.buildId == origin.build_id && item.sha256 == origin.sha256 &&
                item.supportHashes == origin.support_hashes && checkpointBuildValid(mod, origin, dependencyHashes)) {
                "retained build provenance no longer matches exact bytes and inputs"
            }
        } else require(item.buildId == null) { "changed build bytes cannot retain an older build identity" }
        require(item.commit == git(root.parent.resolve("mod_source/${mod.repository}"), "rev-parse", "HEAD").trim() &&
            sourceIdentity(root.parent.resolve("mod_source/${mod.repository}")) == sources.getValue(mod.repository)) {
            "provider source changed before checkpoint: ${mod.repository}"
        }
        require(contractIdentity(mod) == contracts.getValue(mod.repository)) { "verification contracts changed before checkpoint" }
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
        item.supportHashes.forEach { (relative, hash) ->
            val support = supportPath(mod, runId, relative)
            requireNoSymlink(support)
            Files.createDirectories(support.parent)
            val input = root.parent.resolve("mod_source/${mod.repository}/$relative")
            if (!Files.exists(support, LinkOption.NOFOLLOW_LINKS)) ProviderInputs.publish(input, support, hash,
                strictGate = { require(Hashes.sha256(input) == hash) { "support input changed" } },
                checkActive = { require(read() == receipt && receipt.active_run == runId) { "session ownership changed" } })
            require(Files.isRegularFile(support, LinkOption.NOFOLLOW_LINKS) &&
                Hashes.sha256(input) == hash && Hashes.sha256(support) == hash) { "support snapshot changed" }
        }
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
                dependencyHashes, mod.tasks, runId, item.supportHashes, contracts.getValue(mod.repository),
                current.owner, root.relativize(destination).toString(), builds.getValue(mod.repository), item.buildId ?: runId,
                item.inputViewSha256)
            val strictGate = {
                require(read() == current && current.active_run == runId) { "session ownership changed before publication" }
                require(sourceIdentity(root.parent.resolve("mod_source/${mod.repository}")) == sources.getValue(mod.repository) &&
                    contractIdentity(mod) == contracts.getValue(mod.repository)) { "provider inputs changed before publication" }
                require(Files.isRegularFile(item.jar, LinkOption.NOFOLLOW_LINKS) && Hashes.sha256(item.jar) == item.sha256) {
                    "provider bytes changed before publication"
                }
                require(supportArtifactHashes(root.parent.resolve("mod_source/${mod.repository}"), mod) == item.supportHashes &&
                    item.supportHashes.all { (relative, hash) ->
                        val support = supportPath(mod, runId, relative)
                        requireNoSymlink(support)
                        Files.isRegularFile(support, LinkOption.NOFOLLOW_LINKS) && Hashes.sha256(support) == hash
                    }) { "support snapshot changed before publication" }
                requireConsumedStages(mod, dependencyHashes)
            }
            strictGate()
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                require(current.checkpoints[mod.repository] == entry && checkpointValid(mod, entry, dependencyHashes)) {
                    "existing checkpoint is not this exact qualified entry"
                }
            } else ProviderInputs.publish(item.jar, destination, item.sha256, strictGate,
                checkActive = { require(read() == current && current.active_run == runId) { "session ownership changed" } })
            require(checkpointValid(mod, entry, dependencyHashes)) { "checkpoint failed final validation" }
            receipt = current.copy(checkpoints = current.checkpoints + (mod.repository to entry))
            save(receipt)
        }
    }

    /** Validate the current-qualified publication store; compiler/audit consume a separate immutable input view. */
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
    fun reusable(mod: ActiveMod, expectedInputViewSha256: String? = null): BuiltMod? {
        if (mod.repository in affected) return null
        val checkpoint = receipt.checkpoints[mod.repository] ?: return null
        if (expectedInputViewSha256 != null && checkpoint.input_view_sha256 != expectedInputViewSha256) return null
        val dependencies = mod.dependsOn.associateWith { dependency ->
            receipt.checkpoints[dependency]?.sha256 ?: receipt.mods[dependency]?.sha256 ?: "missing"
        }
        require(checkpointValid(mod, checkpoint, dependencies)) { "checkpoint invalidated before reuse: ${mod.repository}" }
        return restoreCheckpoint(mod, checkpoint, publishQualified = true)
    }

    /** Build reuse is not verification reuse: the caller must rerun every required task. */
    fun reusableBuild(mod: ActiveMod, dependencies: Map<String, String>, expectedInputViewSha256: String? = null): BuiltMod? {
        val checkpoint = buildCandidates[mod.repository] ?: return null
        if (expectedInputViewSha256 != null && checkpoint.input_view_sha256 != expectedInputViewSha256) return null
        if (!checkpointBuildValid(mod, checkpoint, dependencies)) return null
        require(buildIdentity(mod) == builds.getValue(mod.repository)) { "build inputs changed before restore" }
        return restoreCheckpoint(mod, checkpoint, publishQualified = false)
    }

    private fun restoreCheckpoint(mod: ActiveMod, checkpoint: ProviderCheckpoint, publishQualified: Boolean): BuiltMod {
        val source = checkpointPath(mod, checkpoint.verification_id)
        val repository = root.parent.resolve("mod_source/${mod.repository}")
        if (checkpoint.support_hashes.isNotEmpty()) withBuildResourceLock(repository.resolve(".gradle/bc-source-workspace.lock")) {
          locked {
            require(read() == receipt && receipt.active_run == runId) { "session ownership changed before support restoration" }
            checkpoint.support_hashes.forEach { (relative, hash) ->
            val output = repository.resolve(relative)
            require(relative in mod.supportArtifacts && !Files.isSymbolicLink(output)) { "unsafe support output" }
            var ancestor = output.toAbsolutePath().normalize()
            val workspace = root.parent.toAbsolutePath().normalize()
            require(ancestor.startsWith(workspace)) { "support output escaped workspace" }
            while (ancestor != workspace) {
                require(!Files.isSymbolicLink(ancestor)) { "support output contains a symlink" }
                ancestor = ancestor.parent
            }
            val snapshot = supportPath(mod, checkpoint.verification_id, relative)
            requireNoSymlink(snapshot)
            require(Files.isRegularFile(snapshot, LinkOption.NOFOLLOW_LINKS) && Hashes.sha256(snapshot) == hash) {
                "support snapshot changed before restoration"
            }
            Files.createDirectories(output.parent)
            if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
                require(Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS) && !Files.isSameFile(output, snapshot)) {
                    "support output is not an independent ordinary file"
                }
                val previousHash = Hashes.sha256(output)
                if (previousHash != hash) {
                    val retainedRoot = root.resolve("generated/test-evidence/$runId/release/retained-support/${mod.repository}")
                    requireNoSymlink(retainedRoot)
                    Files.createDirectories(retainedRoot)
                    val retained = Files.createDirectory(retainedRoot.resolve(UUID.randomUUID().toString())).resolve(output.fileName)
                    require(read() == receipt && receipt.active_run == runId) { "session ownership changed before support retention" }
                    Files.move(output, retained, StandardCopyOption.ATOMIC_MOVE) // Same filesystem only; no overwrite/delete fallback.
                    require(Files.isRegularFile(retained, LinkOption.NOFOLLOW_LINKS) && Hashes.sha256(retained) == previousHash) {
                        "retained support output changed"
                    }
                }
            }
            if (!Files.exists(output, LinkOption.NOFOLLOW_LINKS)) Files.copy(snapshot, output)
            val permissions = Files.getPosixFilePermissions(output, LinkOption.NOFOLLOW_LINKS).toMutableSet()
            permissions.add(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE)
            Files.setPosixFilePermissions(output, permissions) // Mutable build copy only, never the immutable snapshot.
            require(Hashes.sha256(output) == hash) { "support snapshot changed during staging" }
            }
          }
        }
        val destination = if (publishQualified) {
            val staged = root.resolve("generated/test-evidence/$runId/release/staged-jars/${mod.artifact}")
            requireNoSymlink(staged)
            Files.createDirectories(staged.parent)
            ProviderInputs.publish(source, staged, checkpoint.sha256, strictGate = {
                require(sourceIdentity(root.parent.resolve("mod_source/${mod.repository}")) == sources.getValue(mod.repository) &&
                    contractIdentity(mod) == contracts.getValue(mod.repository)) { "provider inputs changed before qualified restore" }
                require(checkpointValid(mod, checkpoint, mod.dependsOn.associateWith { dependency ->
                    receipt.checkpoints[dependency]?.sha256 ?: receipt.mods[dependency]?.sha256 ?: "missing"
                })) { "checkpoint changed before qualified publication" }
                require(supportArtifactHashes(root.parent.resolve("mod_source/${mod.repository}"), mod) == checkpoint.support_hashes) {
                    "restored support changed before qualified publication"
                }
            }, checkActive = { require(read() == receipt && receipt.active_run == runId) { "session ownership changed" } })
        } else source // Unrequalified build restoration must never occupy the qualified final name.
        return BuiltMod(mod, git(root.parent.resolve("mod_source/${mod.repository}"), "rev-parse", "HEAD").trim(),
            destination, checkpoint.sha256, "checkpoint-reused", checkpoint.support_hashes, checkpoint.build_id,
            checkpoint.input_view_sha256)
    }

    private fun checkpointPath(mod: ActiveMod, originRun: String): Path {
        require(Regex("[A-Za-z0-9_-]{1,80}").matches(originRun)) { "invalid checkpoint origin" }
        require(Regex("[A-Za-z0-9._-]+\\.jar").matches(mod.artifact)) { "invalid checkpoint artifact name" }
        return root.resolve(".worklane/testing-sessions/artifacts/$originRun/${mod.artifact}")
    }

    private fun supportPath(mod: ActiveMod, origin: String, relative: String): Path {
        require(relative in mod.supportArtifacts && !Path.of(relative).isAbsolute && ".." !in Path.of(relative).map { it.toString() }) {
            "unsafe support artifact path"
        }
        return checkpointPath(mod, origin).resolveSibling(mod.artifact + ".support").resolve(relative)
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

    private fun checkpointBuildValid(mod: ActiveMod, checkpoint: ProviderCheckpoint,
        dependencies: Map<String, String>): Boolean = runCatching {
        require(checkpoint.build_fingerprint == builds.getValue(mod.repository) && !checkpoint.build_id.isNullOrBlank())
        checkpointValid(mod, checkpoint.copy(tasks = mod.tasks, shared_fingerprint = contracts.getValue(mod.repository)), dependencies)
    }.getOrDefault(false)

    private fun checkpointValid(mod: ActiveMod, checkpoint: ProviderCheckpoint,
        dependencies: Map<String, String>): Boolean = runCatching {
        val candidate = checkpointPath(mod, checkpoint.verification_id)
        require(checkpoint.owner.isNotBlank() && checkpoint.shared_fingerprint == contracts.getValue(mod.repository) &&
            checkpoint.source == sources.getValue(mod.repository) && checkpoint.tasks == mod.tasks &&
            checkpoint.dependencies == dependencies &&
            (checkpoint.input_view_sha256 == null || Regex("[a-f0-9]{64}").matches(checkpoint.input_view_sha256)) &&
            checkpoint.staged_path == root.relativize(candidate).toString())
        requireNoSymlink(candidate)
        Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS) && Hashes.sha256(candidate) == checkpoint.sha256 &&
            checkpoint.support_hashes.keys == mod.supportArtifacts.toSet() && checkpoint.support_hashes.all { (relative, hash) ->
                val support = supportPath(mod, checkpoint.verification_id, relative)
                requireNoSymlink(support)
                Files.isRegularFile(support, LinkOption.NOFOLLOW_LINKS) && Hashes.sha256(support) == hash
            }
    }.getOrDefault(false)

    fun checkInputs() {
        locked { require(read() == receipt && receipt.active_run == runId) {
            "session receipt changed before deployment"
        } }
        require(sharedIdentity() == shared) { "shared verification inputs changed during verification" }
        mods.forEach { mod ->
            require(contractIdentity(mod) == contracts.getValue(mod.repository)) { "provider verification contracts changed: ${mod.repository}" }
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
                    checkpoint.support_hashes == item.supportHashes && checkpoint.input_view_sha256 == item.inputViewSha256 &&
                    checkpointValid(mod, checkpoint, dependencies)) {
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
                origins.getValue(mod.repository), item.supportHashes, item.inputViewSha256)
        }
        locked {
            require(read() == receipt && receipt.active_run == runId) {
                "session receipt changed during verification"
            }
            save(receipt.copy(generation = receipt.generation + if (receipt.mods.isEmpty()) 1 else 0,
                baseline = "complete", shared_fingerprint = shared, shared_context = sharedContext(), mods = verified,
                checkpoints = receipt.checkpoints, invalidations = emptyList(), active_run = null))
            publish(read())
        }
        completed = true
    }

    override fun close() {
        if (!acquired) return
        locked {
            val current = read()
            if (!completed && current.active_run == runId) {
                val ended = current.copy(baseline = "failed", active_run = null)
                save(ended)
                publish(ended)
            }
            if (supplied == null) save(read().copy(state = "ended"))
        }
    }

    private fun read(): VerificationReceipt {
        require(Files.size(path) <= 1024 * 1024) { "oversized session receipt" }
        val value: VerificationReceipt = mapper.readValue(path.toFile())
        require(value.schema == "bc.testing_session.v2" && (value.owner == owner || owner in value.helpers) && value.session_id == id) {
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
        "_patterns" to sharedInputs.joinToString("\u0000"), "_java_home" to System.getProperty("java.home"),
        "_gradle_home" to (System.getProperty("bc.gradle.home") ?: ""),
        "_kotlin_path" to Path.of(kotlin.KotlinVersion::class.java.protectionDomain.codeSource.location.toURI()).toString())

    private fun contractIdentity(mod: ActiveMod): String = fingerprint(sharedEntries() + scopedEntries(mod) + listOf(
        "build_identity" to buildIdentity(mod), "repository" to mod.repository, "artifact" to mod.artifact, "mod_id" to mod.modId,
        "tasks" to mod.tasks.joinToString("\u0000"), "profiles" to verificationProfileIdentity(mod.verificationProfiles),
        "dependencies" to mod.dependsOn.joinToString("\u0000"),
        "support" to mod.supportArtifacts.joinToString("\u0000")))

    private fun buildIdentity(mod: ActiveMod): String = fingerprint(sharedEntries() +
        scopedEntries(mod, mod.buildInputs ?: mod.verificationInputs) + listOf(
            "source" to sources.getValue(mod.repository), "repository" to mod.repository,
            "artifact" to mod.artifact, "mod_id" to mod.modId,
            "dependencies" to mod.dependsOn.joinToString("\u0000"), "support" to mod.supportArtifacts.joinToString("\u0000")))

    private fun scopedEntries(mod: ActiveMod, patterns: List<String> = mod.verificationInputs): List<Pair<String, String>> {
        require(patterns.all { !it.startsWith("/") && ".." !in it.split('/') }) { "unsafe scoped verification input" }
        if (patterns.isEmpty()) return listOf("scoped_patterns" to "")
        val matchers = patterns.map { root.fileSystem.getPathMatcher("glob:$it") }
        return git(root, *scopedGitInputArgs(patterns).toTypedArray())
            .split('\u0000').filter { it.isNotBlank() }.distinct()
            .filter { name -> matchers.any { it.matches(Path.of(name)) } }
            .map { it to fileIdentity(root.resolve(it)) } + ("scoped_patterns" to patterns.joinToString("\u0000"))
    }

    private fun resolvedGradleIdentity(): String {
        val home = System.getProperty("bc.gradle.home") ?: return "unresolved"
        val lib = Path.of(home).resolve("lib")
        require(Files.isDirectory(lib)) { "resolved Gradle distribution is missing" }
        return Files.walk(lib).use { paths -> fingerprint(paths.filter { Files.isRegularFile(it) }
            .map { lib.relativize(it).toString() to fileIdentity(it) }.toList()) }
    }

    private fun historicalCheckpoints(buildOnly: Boolean = false): Map<String, ProviderCheckpoint> {
        val archive = directory.resolve("completed")
        requireNoSymlink(archive)
        if (!Files.isDirectory(archive)) return emptyMap()
        val entries = mutableMapOf<String, ProviderCheckpoint>()
        Files.list(archive).use { paths -> paths.sorted().forEach { candidate ->
            if (Regex("[A-Za-z0-9_-]{1,80}\\.json").matches(candidate.fileName.toString()) &&
                Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS) && Files.size(candidate) <= 1024 * 1024) {
                val historical = runCatching { mapper.readValue<VerificationReceipt>(candidate.toFile()) }.getOrNull()
                if (historical?.schema == "bc.testing_session.v2" && historical.active_run == null) {
                    historical.checkpoints.forEach { (name, checkpoint) ->
                        val mod = mods.singleOrNull { it.repository == name }
                        if (mod != null && (if (buildOnly) checkpointBuildValid(mod, checkpoint, checkpoint.dependencies)
                            else checkpointValid(mod, checkpoint, checkpoint.dependencies))) entries[name] = checkpoint
                    }
                }
            }
        } }
        return entries
    }

    /** An immutable attempt snapshot; old schemas remain untouched and cannot be promoted. */
    private fun publish(value: VerificationReceipt) {
        val archive = directory.resolve("completed")
        requireNoSymlink(archive)
        Files.createDirectories(archive)
        val destination = archive.resolve("$runId.json")
        requireNoSymlink(destination)
        val bytes = mapper.writeValueAsBytes(value.copy(state = "ended", active_run = null))
        require(bytes.size <= 1024 * 1024) { "completed receipt exceeds bounded size" }
        if (Files.exists(destination)) require(Files.readAllBytes(destination).contentEquals(bytes)) {
            "immutable completed receipt already exists with different content"
        } else {
            val temporary = Files.createTempFile(archive, ".completed-", ".json")
            try {
                Files.write(temporary, bytes)
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE)
            } finally { Files.deleteIfExists(temporary) }
        }
    }

    private fun sharedIdentity(): String = fingerprint(sharedEntries())

    private fun sharedEntries(): List<Pair<String, String>> {
        // Pack contracts are external verification inputs. Release-produced JAR/index files
        // cannot be included: deployment changes them by design. All other tracked pack inputs
        // conservatively require a fresh baseline, rather than silently bypassing a custom gate.
        require(sharedInputs.isNotEmpty() && sharedInputs.all { !it.startsWith("/") && ".." !in it.split('/') }) {
            "shared verification inputs must be nonempty workspace-relative patterns"
        }
        val matchers = sharedInputs.map { root.fileSystem.getPathMatcher("glob:$it") }
        val names = git(root, *scopedGitInputArgs(sharedInputs).toTypedArray())
            .split('\u0000').filter { it.isNotBlank() }.distinct()
            .filter { name -> matchers.any { it.matches(Path.of(name)) } }
            .filterNot { it == "gradle/active-custom-mods.json" || it == "index.toml" || (it.startsWith("mods/") && it.endsWith(".jar")) || it.startsWith(".worklane/") }
        return names.map { it to fileIdentity(root.resolve(it)) } + listOf(
            "input_patterns" to sharedInputs.joinToString("\u0000"),
            "java" to (System.getProperty("java.version") + ":" + System.getProperty("java.home")),
            "toolchain" to fileIdentity(Path.of(System.getProperty("java.home")).resolve("release")),
            "java_binary" to fileIdentity(Path.of(System.getProperty("java.home")).resolve("bin/java")),
            "kotlin" to fileIdentity(Path.of(kotlin.KotlinVersion::class.java.protectionDomain.codeSource.location.toURI())),
            "gradle" to resolvedGradleIdentity(),
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
