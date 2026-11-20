package com.bettercontent.tests.suite

import com.bettercontent.tests.*
import com.bettercontent.tests.suite.v2.contracts.validateV2Draft
import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipFile

internal fun jsonObject(vararg entries: Pair<String, Any?>): JsonNode = suiteMapper.valueToTree(linkedMapOf(*entries))
internal fun boundInput(root: Path, node: JsonNode): Path {
    require(node.isObject && node.path("path").isTextual && node.path("sha256").asText().matches(shaPattern)) { "unbound file" }
    return readBoundFile(root, BoundFile(node.path("path").asText(), node.path("sha256").asText()))
}
internal fun boundPair(root: Path, manifest: JsonNode, client: Path, server: Path): CandidatePair {
    val c = boundInput(root, manifest.path("candidate").path("client"))
    val s = boundInput(root, manifest.path("candidate").path("server"))
    require(c.toRealPath() == client.toRealPath() && s.toRealPath() == server.toRealPath()) { "explicit candidate differs from manifest" }
    return CandidateLocator.locate(c, s)
}
internal fun supportCapabilities(jar: Path, source: String = "server"): Set<String> = ZipFile(jar.toFile()).use { archive ->
    require(source in setOf("server", "client"))
    val entry = requireNotNull(archive.getEntry("META-INF/bc-playtest-support.json")) { "support descriptor missing" }
    require(entry.size in 1..65536) { "support descriptor too large" }
    val descriptor = archive.getInputStream(entry).use { suiteMapper.readTree(it) }
    require(descriptor.path("schema").asText() == "bc.playtest_support.v2" &&
        descriptor.path("protocol").asText() == "bc.playtest_action.v2") { "unsupported native support protocol" }
    val scoped = if (source == "server") descriptor else descriptor.path("client_observation").also {
        require(it.path("source").asText() == "client" && it.path("protocol").asText() == "bc.playtest_action.v2")
    }
    val ids = scoped.path("supported_drivers")
    require(ids.isArray && !ids.isEmpty && ids.all { it.isTextual && it.asText().matches(idPattern) })
    require(ids.map { it.asText() }.distinct().size == ids.size()) { "duplicate support driver" }
    ids.map { it.asText() }.toSet()
}

/** Budget the fixed maximums, not a silently reduced profile. A recorded snapshot is not a reservation. */
internal fun requireSuiteResources(clientRequired: Boolean): Map<String, Any> {
    val memory = Files.readString(Path.of("/proc/meminfo"))
    val available = requireNotNull(Regex("(?m)^MemAvailable:\\s+(\\d+) kB$").find(memory)).groupValues[1].toLong() * 1024
    val cgroup = Path.of("/sys/fs/cgroup")
    val limit = runCatching { Files.readString(cgroup.resolve("memory.max")).trim().toLongOrNull() }.getOrNull()
    val used = runCatching { Files.readString(cgroup.resolve("memory.current")).trim().toLong() }.getOrNull()
    val effective = if (limit != null && used != null) minOf(available, (limit - used).coerceAtLeast(0)) else available
    val needed = (16L + (if (clientRequired) 8 else 0) + 2) * 1024 * 1024 * 1024
    require(effective >= needed) { "fixed suite resource budget unavailable: need $needed bytes free, have $effective; profile was not reduced" }
    return mapOf("available_bytes" to effective, "required_bytes" to needed,
        "processors" to Runtime.getRuntime().availableProcessors(), "server_heap_mib" to 16384,
        "client_heap_mib" to if (clientRequired) 8192 else 0,
        "cpu" to Files.readString(Path.of("/proc/cpuinfo")).lineSequence().firstOrNull { it.startsWith("model name") }.orEmpty(),
        "jvm" to System.getProperty("java.runtime.version"), "contention_notes" to "availability snapshot; no resource reservation")
}

internal fun exclusiveJson(path: Path, node: Any) {
    val data = suiteMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node) + "\n"
    Files.writeString(path, data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
}

/** A draft-only discovery session is always diagnostic and can never issue qualification. */
internal fun discoverSuite(root: Path, manifestPath: Path, client: Path, server: Path, output: Path): Path {
    val manifest = suiteMapper.readTree(manifestPath.toFile())
    val problems = validateV2Draft(root, manifest)
    require(problems.isEmpty()) { "Discovery draft invalid:\n${problems.joinToString("\n")}" }
    val pair = boundPair(root, manifest, client, server)
    val support = boundInput(root, manifest.path("test_support"))
    val supported = supportCapabilities(support)
    require("inventory.discover" in supported && "fixture.cleanup" in supported)
    val resources = requireSuiteResources(false)
    require(!Files.exists(output)) { "discovery output already exists" }
    Files.createDirectories(output.toAbsolutePath().parent)
    Files.createDirectory(output)
    val catalogue = Hashes.sha256(manifestPath)
    val runId = "discovery-${UUID.randomUUID()}"
    val evidence = SessionEvidence(root, runId, "discovery", pair, diagnostic = true)
    val mailbox = NativeMailbox(evidence.directory.resolve("native"), runId)
    val session = PackSession(evidence, SessionRequest(SessionTier.SMOKE), instrumentation =
        SuiteInstrumentation(support, manifest.path("test_support").path("sha256").asText(), mailbox.javaArguments(), false))
    val started = Instant.now()
    var failure: Throwable? = null
    var inventory: JsonNode? = null
    var cleanupComplete = false
    var saved = false
    try {
        exclusiveJson(evidence.directory.resolve("hardware.json"), resources)
        session.prepare(); session.startServer(); session.awaitServerReady()
        inventory = mailbox.execute("discovery.inventory", "inventory.discover", jsonObject(), Duration.ofMinutes(10),
            ensureAlive = session::ensureSuiteServerAlive).payload
        require(inventory.path("files").isArray && !inventory.path("files").isEmpty) { "native inventory returned no raw files" }
        val rawFiles = inventory.path("files").toList()
        val rosters = inventory.path("measured_domain_rosters")
        require(rosters.isObject && rosters.size() == rawFiles.size &&
            rawFiles.all { rosters.path(it.path("domain").asText()).isObject }) {
            "native inventory missing a measured roster sidecar"
        }
        val rosterFiles = rosters.fields().asSequence().map { (domain, file) ->
            require(file.path("domain").asText() == domain) { "native inventory roster domain mismatch" }
            file
        }.toList()
        (rawFiles + rosterFiles).forEach { file ->
            val relative = Path.of(file.path("path").asText())
            val byteCap = if (file in rawFiles && file.path("domain").asText() == "recipes_semantics")
                50_331_648 else 16_777_216
            require(!relative.isAbsolute && !relative.normalize().startsWith("..") &&
                relative.startsWith("inventory")) { "native inventory escaped mailbox" }
            require(file.path("bytes").isIntegralNumber && file.path("bytes").asLong() in 1..byteCap &&
                Files.size(mailbox.directory.resolve(relative)) == file.path("bytes").asLong()) {
                "native inventory file exceeds its bounded domain or changed size"
            }
            val actual = boundInput(mailbox.directory, file)
            require(actual.toRealPath().startsWith(mailbox.directory.toRealPath())) { "native inventory symlink escaped mailbox" }
        }
        val cleanup = mailbox.execute("discovery.cleanup", "fixture.cleanup", jsonObject(), Duration.ofSeconds(120),
            ensureAlive = session::ensureSuiteServerAlive).payload
        require(cleanup.path("cleanup_complete").isBoolean && cleanup.path("cleanup_complete").asBoolean()) { "native discovery cleanup was not established" }
        cleanupComplete = true
        session.stopDiscoveryServer(); session.validate(); saved = true
    } catch (error: Throwable) { failure = error; session.diagnostics() }
    finally {
        try { session.close() } catch (error: Throwable) { if (failure == null) failure = error else failure!!.addSuppressed(error) }
        try { session.recordLogMessages(); pair.assertUnchanged()
            require(Hashes.sha256(manifestPath) == catalogue && Hashes.sha256(support) == manifest.path("test_support").path("sha256").asText())
        } catch (error: Throwable) { if (failure == null) failure = error else failure!!.addSuppressed(error) }
        evidence.finish(failure)
        exclusiveJson(output.resolve("discovery.json"), mapOf("schema" to "bc.playtest_discovery.v2",
            "status" to if (failure == null) "observed" else "failed", "qualification_eligible" to false,
            "run_id" to runId, "candidate" to pair.document(), "catalogue_sha256" to catalogue,
            "test_support_sha256" to manifest.path("test_support").path("sha256").asText(),
            "started_at" to started.toString(), "completed_at" to Instant.now().toString(),
            "evidence_directory" to evidence.directory.toAbsolutePath().toString(), "inventory" to inventory,
            "native_directory" to mailbox.directory.toAbsolutePath().toString(),
            "cleanup_complete" to cleanupComplete, "saved_and_validated" to saved,
            "failure" to failure?.message))
    }
    failure?.let { throw it }
    return output.resolve("discovery.json")
}
