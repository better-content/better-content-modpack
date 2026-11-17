package com.bettercontent.tests.suite.repositories

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import java.io.ByteArrayInputStream
import java.nio.MappedByteBuffer
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.FileSystems
import java.net.URI
import java.lang.module.ModuleFinder
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.time.Instant
import java.util.Properties
import java.util.UUID
import java.util.jar.Manifest
import java.util.zip.ZipInputStream

/** Bounded actual class-definition-stage observation in Test/JavaExec. Not a syscall/resource
 * access audit, Forge union adapter, later-transformer proof, or packaged-runtime observation. */
internal object RepositoryWorkerInputUse {
    internal data class Prepared(val binding: BoundFile, val configs: Map<String, BoundFile>,
        val reportDirectory: Path, val reports: Map<String, Path>, val streams: Map<String, Path>,
        val healthPages: Map<String, Path>, val healthNonces: Map<String, String>,
        val retainedHealth: Map<String, MappedByteBuffer>)
    private val strict = suiteMapper.copy().apply { factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION) }
    private val fields = setOf("schema", "scope", "observation_scope", "status", "run_id", "repository", "task", "reviewed_plan_sha256",
        "producer_sha256", "config_sha256", "event_stream_path", "event_stream_nonce", "health_page_path", "health_nonce", "snapshot_stream_sequence", "observed_process_pid", "observed_process_start", "captured_at",
        "java_executable", "java_executable_sha256", "java_version", "java_runtime_version", "java_vendor",
        "java_home", "java_vm_name", "java_vm_version", "java_vm_vendor", "jar_mr_policy", "jar_version_override", "jar_runtime_feature", "bootstrap_pre_agent", "jvm_arguments", "unsupported",
        "instrumentation_agents", "module_access_changes", "union_sources", "task_roster_path", "task_roster_sha256", "task_execution_policy", "task_class_path", "jdk_components", "class_path", "module_path", "code_sources", "class_definitions")
    private val entryFields = setOf("path", "sha256", "kind", "files", "size_bytes")
    private val definitionFields = setOf("name", "loader_id", "source", "bytes_sha256", "selected_entry")
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    private fun key(task: String) = digest(task.toByteArray(Charsets.UTF_8))
    private fun absolute(task: String) = if (task.startsWith(':')) task else ":$task"

    /** Capture once, never parse a later reopening of the pathname. */
    private fun capture(path: Path, max: Int, sha: String? = null, allowEmpty: Boolean = false): ByteArray {
        require(path.isAbsolute && path.normalize() == path &&
            generateSequence(path.parent) { it.parent }.all { Files.isDirectory(it, NOFOLLOW_LINKS) && !Files.isSymbolicLink(it) } &&
            Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) &&
            (Files.getAttribute(path, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1) {
            "linked/nonregular worker input"
        }
        val bytes = Files.newInputStream(path, NOFOLLOW_LINKS).use { it.readNBytes(max + 1) }
        require((allowEmpty || bytes.isNotEmpty()) && bytes.size <= max && (sha == null || digest(bytes) == sha)) {
            "missing, oversized or altered worker input"
        }
        return bytes
    }
    private fun closed(bytes: ByteArray, allowed: Set<String>): JsonNode {
        val doc: JsonNode = strict.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes)
        require(doc.isObject && doc.fieldNames().asSequence().toSet() == allowed) { "worker report has missing/unknown fields" }
        return doc
    }
    private fun config(bytes: ByteArray): Properties {
        val names = mutableSetOf<String>()
        bytes.toString(Charsets.ISO_8859_1).lineSequence().forEach { line ->
            if (line.isBlank() || line.startsWith('#') || line.startsWith('!')) return@forEach
            require(!line.endsWith('\\')) { "continued worker properties unsupported" }
            val one = Properties().apply { load(ByteArrayInputStream((line + "\n").toByteArray(Charsets.ISO_8859_1))) }
            require(one.size == 1 && names.add(one.stringPropertyNames().single())) { "duplicate worker property" }
        }
        val properties = Properties().apply { load(ByteArrayInputStream(bytes)) }
        require(properties.stringPropertyNames() == names) { "ambiguous worker properties" }
        return properties
    }
    fun prepare(plan: RepositoryRunPlan, root: Path, evidence: Path, planSha: String): Prepared {
        require(System.getProperty("os.name") == "Linux" && System.getProperty("os.arch") == "amd64") {
            "worker health page requires reviewed Linux/amd64 shared mapping semantics"
        }
        val harness = requireNotNull(plan.useHarness)
        val relative = Path.of(requireNotNull(plan.usageReport))
        require(relative.nameCount >= 2 && relative.first().toString() == "build" &&
            !relative.isAbsolute && relative.normalize() == relative && !Files.exists(root.resolve(relative), NOFOLLOW_LINKS)) {
            "worker-use reports must be fresh inside an exact generated build root"
        }
        val reportDir = root.resolve(relative)
        Files.createDirectories(reportDir)
        val configDir = Files.createDirectory(evidence.resolve("input-use-controller"))
        val tasks = plan.reports.map { absolute(it.task) }.distinct()
        val reports = tasks.associateWith { reportDir.resolve("${key(it)}.json") }
        val streams = tasks.associateWith { reportDir.resolve("${key(it)}.events") }
        val healthPages = tasks.associateWith { configDir.resolve("${key(it)}.health") }
        val healthNonces = tasks.associateWith { java.util.UUID.randomUUID().toString() }
        val configs = tasks.associateWith { task ->
            val output = reports.getValue(task)
            val file = configDir.resolve("${key(task)}.properties")
            val properties = Properties().apply {
                setProperty("output", output.toString()); setProperty("event_stream", streams.getValue(task).toString())
                setProperty("health_page", healthPages.getValue(task).toString())
                setProperty("health_nonce", healthNonces.getValue(task))
                setProperty("task", task)
                setProperty("run_id", plan.runId); setProperty("repository", plan.repository)
                setProperty("reviewed_plan_sha256", planSha)
                setProperty("producer_sha256", harness.producerSource.sha256)
                harness.unionHandler?.let { handler ->
                    setProperty("union_handler", Path.of(handler.path).toRealPath().toString())
                    setProperty("union_handler_sha256", handler.sha256)
                    setProperty("union_access_profile", requireNotNull(harness.unionAccessProfile))
                }
            }
            Files.newOutputStream(file, CREATE_NEW).use { properties.store(it, "fixed reviewed worker binding") }
            BoundFile(file.toString(), Hashes.sha256(file))
        }
        // Preallocate/map each controller-owned health page BEFORE the worker starts. Retain the
        // live mapping (not just a pathname) through the exact owned-process exit and comparison.
        val retained = configs.mapValues { (task, config) ->
            val health = healthPages.getValue(task)
            FileChannel.open(health, CREATE_NEW, READ, WRITE).use { channel ->
                channel.position(4095); channel.write(ByteBuffer.wrap(byteArrayOf(0)))
                val page = channel.map(FileChannel.MapMode.READ_WRITE, 0, 4096)
                page.put("BC_REPO_HEALTH1!".toByteArray(Charsets.US_ASCII)) // bytes 0..15
                page.position(16); page.put(healthNonces.getValue(task).toByteArray(Charsets.US_ASCII))
                page.position(64); page.put(config.sha256.toByteArray(Charsets.US_ASCII))
                page.force(); page
            }
        }
        val bindingFile = configDir.resolve("binding.properties")
        val binding = Properties().apply {
            setProperty("agent", Path.of(harness.agentJar.path).toRealPath().toString())
            setProperty("agent_sha256", harness.agentJar.sha256)
            configs.forEach { (task, file) ->
                setProperty("task.${key(task)}", file.path)
                setProperty("task_sha256.${key(task)}", file.sha256)
            }
        }
        Files.newOutputStream(bindingFile, CREATE_NEW).use { binding.store(it, "fixed reviewed observer injection") }
        return Prepared(BoundFile(bindingFile.toString(), Hashes.sha256(bindingFile)), configs, reportDir, reports, streams,
            healthPages, healthNonces, retained)
    }

    private data class Entry(val path: Path, val kind: String, val sha: String, val files: Int,
        val sizeBytes: Long)
    private fun inventory(path: Path, allowAbsent: Boolean = false): Entry {
        require(path.isAbsolute && path.normalize() == path)
        if (Files.notExists(path, NOFOLLOW_LINKS)) {
            require(allowAbsent && generateSequence(path.parent) { it.parent }.all { ancestor ->
                !Files.isSymbolicLink(ancestor) && (!Files.exists(ancestor, NOFOLLOW_LINKS) ||
                    Files.isDirectory(ancestor, NOFOLLOW_LINKS))
            }) { "unreviewed/link-ambiguous missing declared resource" }
            return Entry(path, "absent", digest(("bc.repository_absent.v1:" + path)
                .toByteArray(Charsets.UTF_8)), 0, 0L)
        }
        require(Files.exists(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) &&
            path.toRealPath() == path) { "unknown/linked classpath path state" }
        if (Files.isRegularFile(path, NOFOLLOW_LINKS)) {
            require(Files.size(path) in 0..268_435_456)
            val bytes = capture(path, 268_435_456, allowEmpty = true)
            require(Files.size(path) == bytes.size.toLong()) { "file changed during bounded inventory capture" }
            return Entry(path, "file", digest(bytes), 1, bytes.size.toLong())
        }
        require(Files.isDirectory(path, NOFOLLOW_LINKS))
        val all = mutableListOf<Path>()
        Files.walk(path, 64).use { stream ->
            val iterator = stream.iterator()
            while (iterator.hasNext()) {
                val child = iterator.next()
                if (child == path) continue
                require(path.relativize(child).nameCount < 64 || !Files.isDirectory(child, NOFOLLOW_LINKS)) {
                    "unbounded generated directory depth"
                }
                require(all.size < 65_536 && !Files.isSymbolicLink(child)) { "unbounded/linked generated directory" }
                all.add(child)
            }
        }
        all.sort()
        val hash = MessageDigest.getInstance("SHA-256")
        var count = 0
        var totalBytes = 0L
        all.filter { !Files.isDirectory(it, NOFOLLOW_LINKS) }.forEach { file ->
            require(Files.isRegularFile(file, NOFOLLOW_LINKS)) { "nonregular directory entry" }
            hash.update(path.relativize(file).toString().toByteArray(Charsets.UTF_8)); hash.update(0.toByte())
            val bytes = capture(file, 16_777_216, allowEmpty = true)
            hash.update(digest(bytes).toByteArray(Charsets.US_ASCII)); hash.update(0.toByte())
            totalBytes += bytes.size; count++
        }
        return Entry(path, "directory", hash.digest().joinToString("") { "%02x".format(it) }, count, totalBytes)
    }
    private fun entry(row: JsonNode, allowAbsent: Boolean = false): Entry {
        require(row.isObject && row.fieldNames().asSequence().toSet() == entryFields &&
            row.path("path").isTextual && row.path("sha256").isTextual && row.path("kind").isTextual &&
            row.path("files").isIntegralNumber && row.path("size_bytes").isIntegralNumber &&
            row.path("size_bytes").asLong() >= 0 && row.path("sha256").asText().matches(shaPattern)) {
            "malformed worker classpath/code-source row"
        }
        val path = Path.of(row.path("path").asText())
        require(path.isAbsolute && path.normalize() == path)
        val actual = inventory(path, allowAbsent)
        require(row.path("kind").asText() == actual.kind && row.path("sha256").asText() == actual.sha &&
            row.path("files").asInt() == actual.files && row.path("size_bytes").asLong() == actual.sizeBytes) {
            "worker input changed/declared-absent path became present or changed bytes"
        }
        return actual
    }
    private fun classBytes(source: Entry, name: String, selectedEntry: String, runtimeFeature: Int,
        mrPolicy: String): ByteArray {
        require(Regex("[A-Za-z0-9_$/-]+\\.class").matches(name) && !name.contains(".."))
        val verifierModules = Path.of(System.getProperty("java.home")).toRealPath().resolve("lib/modules")
        if (source.path == verifierModules) {
            val slash = selectedEntry.indexOf('/')
            require(slash > 0 && selectedEntry.substring(slash + 1) == name &&
                ModuleFinder.ofSystem().find(selectedEntry.substring(0, slash)).isPresent) {
                "JRT module identity not in this exact pinned verifier JDK image"
            }
            val jrtPath = FileSystems.getFileSystem(URI.create("jrt:/"))
                .getPath("modules", selectedEntry.substring(0, slash), name)
            Files.newInputStream(jrtPath).use { input ->
                val original = input.readNBytes(16_777_217)
                require(original.isNotEmpty() && original.size <= 16_777_216) { "unbounded JRT class" }
                return original
            }
        }
        if (source.kind == "directory") {
            require(selectedEntry == name)
            return capture(source.path.resolve(name).normalize(), 16_777_216)
        }
        require(source.path.fileName.toString().endsWith(".jar") && runtimeFeature in 8..99 &&
            mrPolicy in setOf("<unset>", "true", "false", "force")) { "unknown class source/runtime version policy" }
        val bytes = capture(source.path, 268_435_456, source.sha)
        var base: ByteArray? = null
        var manifest: ByteArray? = null
        val variants = mutableMapOf<Int, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entries = 0
            var expanded = 0L
            while (true) {
                val next = zip.nextEntry ?: break
                require(++entries <= 65_536) { "unbounded JAR entries" }
                // Drain every member explicitly; ZipInputStream.nextEntry otherwise inflates
                // skipped zip-bomb entries with no bound. Total expanded data <=256MiB.
                val remaining = (268_435_456L - expanded).toInt()
                require(remaining > 0) { "unbounded expanded JAR source" }
                val content = zip.readNBytes(remaining + 1)
                expanded += content.size
                require(expanded <= 268_435_456L) { "unbounded expanded JAR source" }
                if (next.name.equals("META-INF/MANIFEST.MF", ignoreCase = true)) {
                    require(manifest == null && content.size <= 65_536) { "ambiguous/oversized JAR manifest" }
                    manifest = content
                }
                if (next.name == name) {
                    require(base == null && content.isNotEmpty() && content.size <= 16_777_216)
                    base = content
                }
                val variant = Regex("META-INF/versions/([0-9]+)/" + Regex.escape(name)).matchEntire(next.name)
                if (variant != null) {
                    val version = variant.groupValues[1].toIntOrNull()
                    require(version != null && version >= 9 && variants.put(version, content) == null &&
                        content.isNotEmpty() && content.size <= 16_777_216) { "duplicate/invalid selected MR class entry" }
                }
            }
        }
        val advertised = manifest?.let { Manifest(ByteArrayInputStream(it)).mainAttributes
            .getValue("Multi-Release")?.equals("true", ignoreCase = true) } == true
        val enabled = mrPolicy == "force" || mrPolicy != "false" && advertised
        val version = if (enabled) variants.keys.filter { it <= runtimeFeature }.maxOrNull() else null
        val expected = version?.let { "META-INF/versions/$it/$name" } ?: name
        require(selectedEntry == expected) { "worker selected a different MR runtime entry than pinned policy" }
        return (version?.let(variants::get) ?: base) ?: error("Observed class absent from exact selected JAR entry")
    }
    private fun rows(array: JsonNode, min: Int, max: Int, allowAbsent: Boolean = false): List<Entry> {
        require(array.isArray && array.size() in min..max)
        return array.map { entry(it, allowAbsent) }.also { result -> require(result.map { it.path }.distinct().size == result.size) {
            "duplicate worker source row"
        } }
    }
    /** The report hook is a snapshot, never a terminal class definition. Parse the independently
     * bounded, unbuffered append stream only AFTER verified owned-worker exit; later hook class
     * definitions remain in this stream and must be checked against reviewed source bytes. */
    private fun streamDefinitions(raw: ByteArray, report: ByteArray, configSha: String, nonce: String,
        pid: Long, birth: String, snapshotSequence: Int, snapshot: JsonNode): List<JsonNode> {
        val text = raw.toString(Charsets.UTF_8)
        require(text.toByteArray(Charsets.UTF_8).contentEquals(raw) && text.endsWith("\n")) {
            "worker event stream truncated/noncanonical UTF-8"
        }
        val lines = text.dropLast(1).split('\n')
        require(lines.size in 3..65_540 && lines.all { it.toByteArray(Charsets.UTF_8).size in 1..4096 }) {
            "unbounded or empty worker event frame"
        }
        val definitions = mutableListOf<JsonNode>()
        var hooks = 0
        lines.forEachIndexed { index, line ->
            val row: JsonNode = strict.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(line.toByteArray(Charsets.UTF_8))
            val expected = when (row.path("kind").asText()) {
                "start" -> setOf("sequence", "kind", "nonce", "config_sha256", "pid", "birth")
                "definition" -> setOf("sequence", "kind", "name", "loader_id", "source", "bytes_sha256", "selected_entry")
                "hook_report" -> setOf("sequence", "kind", "report_sha256", "snapshot_stream_sequence")
                "error" -> setOf("sequence", "kind", "reason")
                else -> error("unknown worker event kind")
            }
            require(row.isObject && row.fieldNames().asSequence().toSet() == expected) {
                "unknown or missing worker event field"
            }
            require(row.path("sequence").isIntegralNumber && row.path("sequence").asInt() == index) {
                "worker event sequence gap/duplicate"
            }
            when (row.path("kind").asText()) {
                "start" -> require(index == 0 && row.path("nonce").asText() == nonce &&
                    row.path("config_sha256").asText() == configSha && row.path("pid").asLong(-1) == pid &&
                    row.path("birth").asText() == birth) { "unbound worker event start" }
                "definition" -> {
                    require(index > 0 && row.path("name").isTextual && row.path("source").isTextual &&
                        row.path("selected_entry").isTextual &&
                        row.path("loader_id").isIntegralNumber && row.path("loader_id").asInt() in -1..65_536 &&
                        row.path("loader_id").asInt() != 0 &&
                        row.path("bytes_sha256").asText().matches(shaPattern)) { "malformed class-definition event" }
                    // The stream envelope is checked above; downstream source-byte validation
                    // consumes only the five definition fields. Never mutate the captured event.
                    definitions.add(row.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().apply {
                        remove("sequence"); remove("kind")
                    })
                    require(definitions.size <= 65_536) { "class-definition budget exceeded" }
                }
                "hook_report" -> {
                    require(index > 0 && ++hooks == 1 && row.path("report_sha256").asText() == digest(report) &&
                        row.path("snapshot_stream_sequence").isIntegralNumber &&
                        row.path("snapshot_stream_sequence").asInt() == snapshotSequence &&
                        snapshotSequence in 1..index) { "hook snapshot not bound to exact report bytes" }
                }
                "error" -> error("worker event collector reported incomplete: ${row.path("reason").asText()}")
                else -> error("unknown worker event kind")
            }
        }
        require(hooks == 1 && snapshot.isArray && snapshot.size() <= definitions.size &&
            snapshot.size() == snapshotSequence - 1 && snapshot.withIndex().all { (index, row) ->
                val event = definitions[index]
                row.isObject && row.fieldNames().asSequence().toSet() == definitionFields &&
                    row.path("name") == event.path("name") && row.path("loader_id") == event.path("loader_id") &&
                    row.path("source") == event.path("source") && row.path("bytes_sha256") == event.path("bytes_sha256") &&
                    row.path("selected_entry") == event.path("selected_entry")
            }) { "hook snapshot is not an exact prefix of owned-worker definition events" }
        return definitions
    }

    fun verify(plan: RepositoryRunPlan, root: Path, prepared: Prepared, planSha: String,
        execution: RepositoryCommandExecution, started: Instant, ended: Instant, evidence: Path): BoundFile {
        val harness = requireNotNull(plan.useHarness)
        require(digest(capture(Path.of(prepared.binding.path), 65_536, prepared.binding.sha256)) == prepared.binding.sha256 &&
            prepared.configs.values.all { digest(capture(Path.of(it.path), 16_384, it.sha256)) == it.sha256 } &&
            listOf(harness.producerSource, harness.initScript, harness.agentJar, harness.agentBuildReceipt,
                harness.javaExecutable).plus(listOfNotNull(harness.unionHandler)).all { ref ->
                digest(capture(Path.of(ref.path), 268_435_456, ref.sha256)) == ref.sha256
            } && harness.allowedExternalClasspath.all { ref ->
                digest(capture(Path.of(ref.path), 268_435_456, ref.sha256, allowEmpty = true)) == ref.sha256
            }) { "observer, JDK, classpath or controller config changed" }
        val binding = config(capture(Path.of(prepared.binding.path), 65_536, prepared.binding.sha256))
        require(binding.getProperty("agent") == Path.of(harness.agentJar.path).toRealPath().toString() &&
            binding.getProperty("agent_sha256") == harness.agentJar.sha256 &&
            binding.stringPropertyNames() == (setOf("agent", "agent_sha256") + prepared.configs.keys.flatMap { task ->
                listOf("task.${key(task)}", "task_sha256.${key(task)}") }).toSet())
        for ((task, ref) in prepared.configs) {
            val config = config(capture(Path.of(ref.path), 16_384, ref.sha256))
            val unionKeys = if (harness.unionHandler == null) emptySet() else
                setOf("union_handler", "union_handler_sha256", "union_access_profile")
            require(config.stringPropertyNames() == setOf("output", "event_stream", "health_page", "health_nonce", "task", "run_id", "repository", "reviewed_plan_sha256", "producer_sha256") + unionKeys &&
                config.getProperty("event_stream") == prepared.streams.getValue(task).toString() &&
                config.getProperty("health_page") == prepared.healthPages.getValue(task).toString() &&
                config.getProperty("health_nonce") == prepared.healthNonces.getValue(task) &&
                (harness.unionHandler == null ||
                    config.getProperty("union_handler") == Path.of(harness.unionHandler.path).toRealPath().toString() &&
                    config.getProperty("union_handler_sha256") == harness.unionHandler.sha256 &&
                    config.getProperty("union_access_profile") == harness.unionAccessProfile) &&
                config.getProperty("output") == prepared.reports.getValue(task).toString() &&
                config.getProperty("task") == task && config.getProperty("run_id") == plan.runId &&
                config.getProperty("repository") == plan.repository &&
                config.getProperty("reviewed_plan_sha256") == planSha &&
                config.getProperty("producer_sha256") == harness.producerSource.sha256 &&
                binding.getProperty("task.${key(task)}") == ref.path &&
                binding.getProperty("task_sha256.${key(task)}") == ref.sha256) { "controller worker config not same bound bytes" }
        }
        val expectedFiles = (prepared.reports.values + prepared.streams.values).map { it.fileName.toString() }.toSet()
        require(Files.isDirectory(prepared.reportDirectory, NOFOLLOW_LINKS) && !Files.isSymbolicLink(prepared.reportDirectory) &&
            Files.list(prepared.reportDirectory).use { it.map { file -> file.fileName.toString() }.toList().toSet() } == expectedFiles) {
            "missing/extra actual worker reports"
        }
        val expectedProvider = plan.inputs.getValue("providers").associate { root.resolve(it.relativePath).toRealPath() to it.sha256 }
        val allowedExternal = harness.allowedExternalClasspath.associate { Path.of(it.path).toRealPath() to it.sha256 } +
            (Path.of(harness.agentJar.path).toRealPath() to harness.agentJar.sha256)
        val artifacts = plan.inputs.getValue("artifact").map { root.resolve(it.relativePath).toRealPath() }.toSet()
        val java = Path.of(harness.javaExecutable.path).toRealPath()
        val generated = (listOf(root.resolve("build")) + plan.generatedModuleRoots.map { root.resolve(it).resolve("build") })
            .map { it.toAbsolutePath().normalize() }
        val streamRefs = mutableMapOf<String, BoundFile>()
        val healthRefs = mutableMapOf<String, BoundFile>()
        val reportRefs = prepared.reports.map { (task, report) ->
            require(execution.exitCode == 0 && !execution.timedOut && execution.cleanupComplete &&
                execution.strayDescendantPids.isEmpty() && execution.survivingPids.isEmpty()) {
                "owned worker process exit/cleanup not independently verified"
            }
            require(Files.getLastModifiedTime(report, NOFOLLOW_LINKS).toInstant().let {
                it.isAfter(started.minusSeconds(2)) && !it.isAfter(ended.plusSeconds(2))
            }) { "stale actual worker observation" }
            val raw = capture(report, 33_554_432)
            val doc = closed(raw, fields)
            fun text(name: String): String = doc.path(name).takeIf { it.isTextual }?.asText()
                ?: error("worker field $name not a string")
            require(text("schema") == "bc.repository_input_use_worker.v3" &&
                text("scope") == "source-only-not-packaged-runtime" &&
                text("observation_scope") == "class-definition-stage-only-resource-access-unobserved" &&
                text("status") == "bounded-file-class-stage" && doc.path("unsupported").isArray &&
                doc.path("unsupported").size() == 0 &&
                text("task") == task && text("repository") == plan.repository && text("run_id") == plan.runId &&
                text("reviewed_plan_sha256") == planSha && text("producer_sha256") == harness.producerSource.sha256 &&
                text("config_sha256") == prepared.configs.getValue(task).sha256 &&
                text("java_executable") == java.toString() && text("java_executable_sha256") == harness.javaExecutable.sha256 &&
                listOf("java_version", "java_runtime_version", "java_vendor", "java_home", "java_vm_name", "java_vm_version", "java_vm_vendor")
                    .all { text(it).isNotBlank() } &&
                Path.of(text("java_home")) == java.parent.parent &&
                Path.of(text("java_home")).toRealPath() == Path.of(System.getProperty("java.home")).toRealPath() &&
                text("java_version").substringBefore('.').let { version ->
                    version == plan.runtimeToolchainFacts.getValue("java_major") || version == "1" &&
                        text("java_version").split('.').getOrNull(1) == plan.runtimeToolchainFacts.getValue("java_major")
                }) { "unbound worker identity, JDK version, config or incomplete observation" }
            require(doc.path("observed_process_pid").isIntegralNumber &&
                doc.path("bootstrap_pre_agent").isIntegralNumber && doc.path("bootstrap_pre_agent").asInt() >= 0)
            val pid = doc.path("observed_process_pid").asLong()
            val birth = text("observed_process_start")
            require(pid > 0 && execution.ownedProcesses.any { it.pid == pid && it.startedAt == birth } &&
                ProcessHandle.of(pid).map { live ->
                    !live.isAlive || live.info().startInstant().map { it.toString() != birth }.orElse(false)
                }.orElse(true) &&
                runCatching { Instant.parse(text("captured_at")) }.getOrNull()?.let {
                    !it.isBefore(started) && !it.isAfter(ended.plusSeconds(2))
                } == true) { "no owned task JVM birth/worker shutdown" }
            val streamPath = prepared.streams.getValue(task)
            require(text("event_stream_path") == streamPath.toString() &&
                runCatching { UUID.fromString(text("event_stream_nonce")).toString() }.getOrNull() ==
                    text("event_stream_nonce") && doc.path("snapshot_stream_sequence").isIntegralNumber &&
                Files.getLastModifiedTime(streamPath, NOFOLLOW_LINKS).toInstant().let {
                    !it.isBefore(started.minusSeconds(2)) && !it.isAfter(ended.plusSeconds(2))
                }) { "worker event stream not bound to exact owned exit" }
            val streamRaw = capture(streamPath, 67_108_864)
            val definitions = streamDefinitions(streamRaw, raw, prepared.configs.getValue(task).sha256,
                text("event_stream_nonce"), pid, birth, doc.path("snapshot_stream_sequence").asInt(),
                doc.path("class_definitions"))
            val streamEvidence = evidence.resolve("worker-${key(task)}.events")
            Files.write(streamEvidence, streamRaw, CREATE_NEW)
            streamRefs[task] = BoundFile(streamEvidence.toString(), digest(streamRaw))
            val healthPath = prepared.healthPages.getValue(task)
            require(text("health_page_path") == healthPath.toString() &&
                text("health_nonce") == prepared.healthNonces.getValue(task) &&
                System.getProperty("os.name") == "Linux" && System.getProperty("os.arch") == "amd64") {
                "unreviewed shared mapping platform or page identity"
            }
            prepared.retainedHealth.getValue(task).force() // once, after verified owned exit; never per callback
            val retained = ByteArray(4096)
            prepared.retainedHealth.getValue(task).duplicate().apply { position(0); get(retained) }
            val pageBytes = capture(healthPath, 4096)
            require(pageBytes.size == 4096 && retained.contentEquals(pageBytes) &&
                String(pageBytes, 0, 16, Charsets.US_ASCII) == "BC_REPO_HEALTH1!" &&
                String(pageBytes, 16, 36, Charsets.US_ASCII) == text("health_nonce") &&
                String(pageBytes, 64, 64, Charsets.US_ASCII) == prepared.configs.getValue(task).sha256 &&
                pageBytes.sliceArray(52..63).all { it == 0.toByte() } &&
                pageBytes.sliceArray(188..4095).all { it == 0.toByte() }) {
                "missing/corrupt controller-retained worker health mapping"
            }
            val counters = ByteBuffer.wrap(pageBytes)
            val attempted = counters.getLong(144); val committed = counters.getLong(152)
            val errors = counters.getLong(160); val overflow = counters.getLong(168)
            val typedBootstrapExclusions = counters.getLong(176)
            val parsedFrames = streamRaw.count { it == '\n'.code.toByte() }.toLong()
            require(counters.getLong(128) == pid && counters.getLong(136) == Instant.parse(birth).toEpochMilli() &&
                counters.getInt(184) == 1 && attempted == parsedFrames && committed == parsedFrames &&
                errors == 0L && overflow == 0L && typedBootstrapExclusions >= 0L) {
                "collector attempted/committed/error counters disagree with complete post-exit stream"
            }
            val healthEvidence = evidence.resolve("worker-${key(task)}.health")
            Files.write(healthEvidence, pageBytes, CREATE_NEW)
            healthRefs[task] = BoundFile(healthEvidence.toString(), digest(pageBytes))
            val jdk = rows(doc.path("jdk_components"), 4, 4)
            val home = Path.of(text("java_home"))
            require(jdk.map { it.path }.toSet() == setOf(java, home.resolve("lib/modules"),
                home.resolve("lib/server/libjvm.so"), home.resolve("release")) && jdk.all { it.kind == "file" &&
                (if (it.path == java) harness.javaExecutable.sha256 else allowedExternal[it.path]) == it.sha }) {
                "worker runtime image or VM binary not independently pinned"
            }
            val args = doc.path("jvm_arguments")
            require(args.isArray && args.size() in 1..256 && args.all { it.isTextual && it.asText().isNotBlank() })
            val rosterPath = Path.of(prepared.configs.getValue(task).path).parent.resolve("${key(task)}-task-inputs.txt")
            require(text("task_roster_path") == rosterPath.toString() &&
                text("task_roster_sha256").matches(shaPattern)) { "worker task input roster not controlled by owner" }
            val rosterBytes = capture(rosterPath, 1_048_576, text("task_roster_sha256"))
            val rosterLines = rosterBytes.toString(Charsets.UTF_8)
            require(rosterLines.endsWith("\n") && rosterLines.toByteArray(Charsets.UTF_8).contentEquals(rosterBytes))
            val lines = rosterLines.dropLast(1).split('\n')
            val actualPolicy = when (lines.firstOrNull()) {
                "policy\tTest\t1\t0" -> "Test/maxParallelForks=1/forkEvery=0"
                "policy\tJavaExec\tnot-applicable\tnot-applicable" -> "JavaExec/one-task-worker"
                else -> error("unsupported original Gradle Test/JavaExec worker fork policy")
            }
            require(text("task_execution_policy") == actualPolicy) {
                "worker policy not backed by pre-fork SHA-bound original Gradle task settings"
            }
            val absentResourceRoots = generated.map { it.resolve("resources") }.toSet()
            val taskRoots = lines.drop(1).map { line ->
                val parts = line.split('\t')
                require(parts.size == 5 && parts[1].matches(shaPattern)) { "malformed pre-worker task input row" }
                val path = Path.of(parts[4])
                if (parts[0] == "absent") require(path.parent in absentResourceRoots &&
                    path.fileName.toString().matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,63}"))) {
                    "absent declaration is not an exact generated resources output"
                }
                val node = entry(suiteMapper.createObjectNode().put("path", parts[4]).put("kind", parts[0])
                    .put("sha256", parts[1]).put("files", parts[2].toInt())
                    .put("size_bytes", parts[3].toLong()), allowAbsent = true)
                require(node.path.toString() == parts[4] && node.sha == parts[1] && node.kind == parts[0] &&
                    node.files.toString() == parts[2] && node.sizeBytes.toString() == parts[3]) {
                    "task input roster changed/declared-absent path became present"
                }
                node
            }
            require(taskRoots.size in 1..4096 && taskRoots == rows(doc.path("task_class_path"), 1, 4096,
                allowAbsent = true)) {
                "pre-worker allowed-input inventory is not same observed worker bytes"
            }
            val actualArgs = args.map { arg ->
                val value = arg.asText()
                when (value) {
                    "-javaagent:${Path.of(harness.agentJar.path).toRealPath()}=" +
                        "${prepared.configs.getValue(task).path}@${prepared.configs.getValue(task).sha256}" ->
                        "<controller-repository-agent>"
                    "-Dbc.repository.task.roster=$rosterPath@${text("task_roster_sha256")}" ->
                        "<controller-task-roster>"
                    else -> value
                }
            }
            require(actualArgs == harness.reviewedJvmArguments &&
                args.count { it.asText().startsWith("-javaagent:") } in 1..16 &&
                args.none { it.asText().startsWith("-agentpath:") || it.asText().startsWith("-agentlib:") } &&
                args.none { it.asText().startsWith("--patch-module") || it.asText().startsWith("--upgrade-module-path") }) {
                "unreviewed JVM arguments, agents, instrumentation order or module patching"
            }
            val mrPolicy = text("jar_mr_policy")
            val jarOverride = text("jar_version_override")
            val runtimeFeature = doc.path("jar_runtime_feature")
            require(mrPolicy in setOf("<unset>", "true", "false", "force") &&
                (jarOverride == "<unset>" || jarOverride.matches(Regex("[0-9]{1,2}"))) &&
                runtimeFeature.isIntegralNumber && runtimeFeature.asInt() in 8..99 &&
                runtimeFeature.asInt() <= plan.runtimeToolchainFacts.getValue("java_major").toInt() &&
                (jarOverride == "<unset>" || runtimeFeature.asInt() == jarOverride.toInt()) &&
                args.count { it.asText().startsWith("-Djdk.util.jar.enableMultiRelease=") } ==
                    (if (mrPolicy == "<unset>") 0 else 1) &&
                args.count { it.asText().startsWith("-Djdk.util.jar.version=") } ==
                    (if (jarOverride == "<unset>") 0 else 1) &&
                (mrPolicy == "<unset>" || args.any { it.asText() == "-Djdk.util.jar.enableMultiRelease=$mrPolicy" }) &&
                (jarOverride == "<unset>" || args.any { it.asText() == "-Djdk.util.jar.version=$jarOverride" })) {
                "effective MR-JAR runtime version/enablement not bound to reviewed worker JVM arguments"
            }
            val agents = rows(doc.path("instrumentation_agents"), 1, 16)
            val declaredAgentPaths = args.filter { it.asText().startsWith("-javaagent:") }.map { arg ->
                val binding = arg.asText().removePrefix("-javaagent:").substringBefore('=')
                Path.of(binding).toRealPath()
            }
            require(agents.map { it.path } == declaredAgentPaths && agents.all { it.kind == "file" &&
                allowedExternal[it.path] == it.sha }) { "ordered instrumentation agents not independently pinned" }
            val loaded = rows(doc.path("code_sources"), 1, 4096)
            val classPath = rows(doc.path("class_path"), 1, 4096)
            val modulePath = rows(doc.path("module_path"), 0, 4096)
            val union = rows(doc.path("union_sources"), 0, 4096)
            val access = doc.path("module_access_changes")
            val handler = harness.unionHandler
            require(access.isArray && access.size() == (if (union.isEmpty()) 0 else 1)) {
                "union module access must be explicit and tied to actual union definitions"
            }
            if (union.isNotEmpty()) {
                require(handler != null && harness.unionAccessProfile == "securejarhandler-2.1.10-single-base-open-v1")
                val change = access.single()
                require(change.isObject && change.fieldNames().asSequence().toSet() ==
                    setOf("module", "package", "opened_to", "handler_path", "handler_sha256", "module_instances") &&
                    change.path("module").asText() == "cpw.mods.securejarhandler" &&
                    change.path("package").asText() == "cpw.mods.niofs.union" &&
                    change.path("opened_to").asText() == "observer-unnamed-module" &&
                    change.path("handler_path").asText() == Path.of(handler.path).toRealPath().toString() &&
                    change.path("handler_sha256").asText() == handler.sha256 &&
                    change.path("module_instances").isIntegralNumber && change.path("module_instances").asInt() == 1) {
                    "union provider access not independently reviewed and hash-bound"
                }
            }
            require(union.all { it.kind == "file" && it.path !in artifacts && expectedProvider[it.path] == it.sha }) {
                "union single backing must be an exact independently reviewed source provider JAR"
            }
            val availableSources = classPath + modulePath + taskRoots + union +
                jdk.filter { it.path == home.resolve("lib/modules") }
            val available = availableSources.associateBy { it.path }
            require(available.size == availableSources.map { it.path }.distinct().size &&
                loaded.all { available[it.path] == it } &&
                union.all { row -> loaded.any { it.path == row.path } }) {
                "observed loaded source not in actual class/module/opt-in union roster or changed bytes"
            }
            for (row in (classPath + modulePath + taskRoots)) {
                require(row.path !in artifacts) { "packaged artifact loaded by source-only worker" }
                when (row.kind) {
                    "absent" -> require(row.path.parent in absentResourceRoots && row.files == 0 &&
                        row.sizeBytes == 0L && row.path !in expectedProvider && row.path !in allowedExternal) {
                        "missing provider or non-resource path cannot count as a declared absent input"
                    }
                    "file" -> require(expectedProvider[row.path] == row.sha || allowedExternal[row.path] == row.sha) {
                        "unreviewed file on JVM/classpath"
                    }
                    "directory" -> require(generated.any { row.path.startsWith(it) } && row.files in 1..65_536) {
                        "unreviewed or empty generated class directory"
                    }
                    else -> error("unknown JVM input kind")
                }
            }
            require(expectedProvider.keys.all { path -> definitions.any { it.path("source").asText() == path.toString() } }) {
                "provider listed but never actually defined a worker class"
            }
            require(definitions.size in 1..65_536)
            val loadedPaths = loaded.map { it.path.toString() }.toSet()
            val fullSourcePaths = definitions.map { it.path("source").asText() }.toSet()
            val seen = mutableSetOf<Pair<String, Int>>()
            definitions.forEach { row ->
                require(row.isObject && row.fieldNames().asSequence().toSet() == definitionFields &&
                    row.path("name").isTextual && row.path("loader_id").isIntegralNumber &&
                    row.path("loader_id").asInt() in -1..65_536 && row.path("loader_id").asInt() != 0 &&
                    row.path("source").isTextual &&
                    row.path("bytes_sha256").isTextual && row.path("bytes_sha256").asText().matches(shaPattern) &&
                    row.path("selected_entry").isTextual)
                val source = row.path("source").asText()
                val name = row.path("name").asText()
                val loader = row.path("loader_id").asInt()
                require(Path.of(source) in available && available.getValue(Path.of(source)).kind != "absent" &&
                    seen.add(name to loader) &&
                    (loader != -1 || source == home.resolve("lib/modules").toString()) &&
                    digest(classBytes(available.getValue(Path.of(source)), name,
                        row.path("selected_entry").asText(), runtimeFeature.asInt(), mrPolicy)) ==
                            row.path("bytes_sha256").asText()) {
                    "class definition bytes not backed by exact pinned source"
                }
            }
            require(loadedPaths == doc.path("class_definitions").map { it.path("source").asText() }.toSet() &&
                loadedPaths.all { it in fullSourcePaths }) {
                "hook code-source snapshot not backed by stream definition prefix"
            }
            val captured = evidence.resolve("worker-${key(task)}.json")
            Files.write(captured, raw, CREATE_NEW)
            BoundFile(captured.toString(), digest(raw))
        }
        val aggregate = evidence.resolve("input-use.json")
        Files.writeString(aggregate, suiteMapper.writeValueAsString(mapOf(
            "schema" to "bc.repository_input_use.v3", "scope" to "source-only-not-packaged-runtime",
            "observation_scope" to "class-definition-stage-only-resource-access-unobserved",
            "repository" to plan.repository, "run_id" to plan.runId, "reviewed_plan_sha256" to planSha,
            "java_executable" to harness.javaExecutable, "agent" to harness.agentJar,
            "controller_binding" to prepared.binding, "worker_configs" to prepared.configs,
            "actual_worker_reports" to reportRefs, "actual_worker_event_streams" to streamRefs,
            "actual_worker_health_pages" to healthRefs)) + "\n", CREATE_NEW)
        return BoundFile(aggregate.toString(), Hashes.sha256(aggregate))
    }
}
