package com.bettercontent.tests.release

import com.bettercontent.tests.Hashes
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.channels.FileChannel
import java.nio.file.Files
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
internal data class VerificationReceipt(
    val schema: String = "bc.testing_session.v1",
    val session_id: String,
    val owner: String,
    val generation: Int = 0,
    val baseline: String = "pending",
    val shared_fingerprint: String? = null,
    val mods: Map<String, VerifiedArtifact> = emptyMap(),
    val invalidations: List<String> = listOf("baseline not started"),
    val active_run: String? = null,
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
        require(!Files.isSymbolicLink(directory) && !Files.isSymbolicLink(directory.parent)) { "session directory is a symlink" }
        Files.createDirectories(directory)
        path = directory.resolve("$id.json")
        locked {
            require(!Files.isSymbolicLink(path)) { "session receipt is a symlink" }
            if (supplied == null) save(VerificationReceipt(session_id = id, owner = owner))
            receipt = read()
            require(receipt.active_run == null) { "verification session is already in use by ${receipt.active_run}" }
        }
        fullBaseline = rebuildAll || receipt.baseline != "complete" || receipt.shared_fingerprint != shared
        val invalid = mods.filter { mod ->
            val prior = receipt.mods[mod.repository]
            val jar = root.resolve("mods/${mod.artifact}")
            prior == null || prior.source != sources.getValue(mod.repository) || prior.tasks != mod.tasks ||
                !Files.isRegularFile(jar) || Hashes.sha256(jar) != prior.sha256 ||
                prior.dependencies != mod.dependsOn.associateWith { receipt.mods[it]?.sha256 ?: "missing" } ||
                prior.support_hashes != supportArtifactHashes(root.parent.resolve("mod_source/${mod.repository}"), mod, allowMissing = true)
        }.map { it.repository }.toSet()
        affected = affectedProviders(mods, if (fullBaseline) mods.map { it.repository }.toSet() else invalid)
        locked {
            require(read() == receipt) { "session changed while preparing verification" }
            receipt = receipt.copy(baseline = "verifying", active_run = runId,
                invalidations = if (fullBaseline) listOf("full baseline required") else affected.sorted())
            save(receipt)
            acquired = true
        }
    }

    fun requireReusable(item: BuiltMod) {
        val expected = receipt.mods.getValue(item.definition.repository)
        require(item.sha256 == expected.sha256 && item.supportHashes == expected.support_hashes) {
            "artifact changed after reuse decision: ${item.definition.repository}"
        }
    }

    fun checkInputs() {
        locked { require(read().active_run == runId) { "session ownership changed before deployment" } }
        require(sharedIdentity() == shared) { "shared verification inputs changed during verification" }
        mods.forEach { mod ->
            require(sourceIdentity(root.parent.resolve("mod_source/${mod.repository}")) == sources.getValue(mod.repository)) {
                "${mod.repository} source changed during verification; refusing deployment/provenance"
            }
        }
    }

    fun commit(built: List<BuiltMod>) {
        checkInputs()
        val hashes = built.associate { it.definition.repository to it.sha256 }
        require(hashes.keys == mods.map { it.repository }.toSet()) { "incomplete baseline cannot authorize reuse" }
        val verified = built.associate { item ->
            val mod = item.definition
            require(Hashes.sha256(item.jar) == item.sha256) { "staged verified artifact changed" }
            require(supportArtifactHashes(root.parent.resolve("mod_source/${mod.repository}"), mod) == item.supportHashes) { "verified support artifact changed" }
            if (mod.repository !in affected) requireReusable(item)
            mod.repository to VerifiedArtifact(sources.getValue(mod.repository), item.sha256,
                mod.dependsOn.associateWith { hashes.getValue(it) }, mod.tasks,
                if (mod.repository in affected) runId else receipt.mods.getValue(mod.repository).verification_id,
                item.supportHashes)
        }
        locked {
            require(read().active_run == runId) { "session ownership changed during verification" }
            save(receipt.copy(generation = receipt.generation + if (fullBaseline) 1 else 0,
                baseline = "complete", shared_fingerprint = shared, mods = verified,
                invalidations = emptyList(), active_run = null))
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
        require(value.schema == "bc.testing_session.v1" && value.owner == owner && value.session_id == id) {
            "verification session belongs to another conversation"
        }
        return value
    }

    private fun save(value: VerificationReceipt) {
        val temporary = Files.createTempFile(directory, ".receipt-", ".json")
        try {
            mapper.writeValue(temporary.toFile(), value)
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

    private fun sharedIdentity(): String {
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
        return fingerprint(names.map { it to fileIdentity(root.resolve(it)) } + listOf(
            "input_patterns" to sharedInputs.joinToString("\u0000"),
            "manifest" to fileIdentity(root.resolve("gradle/active-custom-mods.json")),
            "java" to (System.getProperty("java.version") + ":" + System.getProperty("java.home")),
            "toolchain" to fileIdentity(Path.of(System.getProperty("java.home")).resolve("release")),
            "sdk" to fileIdentity(root.resolve("docs/toolchains.md")),
        ))
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
