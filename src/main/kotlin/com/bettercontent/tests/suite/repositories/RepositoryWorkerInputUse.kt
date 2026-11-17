package com.bettercontent.tests.suite.repositories

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.time.Instant
import java.util.Properties

/** Only actual instrumented Test/JavaExec worker JVMs count. The Gradle launcher, a plan's
 * file inventory and a JSON claim naming an owned PID are not evidence of worker input use. */
internal object RepositoryWorkerInputUse {
    internal data class Prepared(val binding: BoundFile, val configs: Map<String, BoundFile>,
        val reportDirectory: Path, val reports: Map<String, Path>)
    private fun key(task: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(task.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun absolute(task: String) = if (task.startsWith(':')) task else ":$task"

    fun prepare(plan: RepositoryRunPlan, root: Path, evidence: Path, planSha: String): Prepared {
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
        val configs = tasks.associateWith { task ->
            val output = reports.getValue(task)
            val config = configDir.resolve("${key(task)}.properties")
            val properties = Properties().apply {
                setProperty("output", output.toString()); setProperty("task", task)
                setProperty("run_id", plan.runId); setProperty("repository", plan.repository)
                setProperty("reviewed_plan_sha256", planSha)
                setProperty("producer_sha256", harness.producerSource.sha256)
            }
            Files.newOutputStream(config, CREATE_NEW).use { properties.store(it, "fixed reviewed worker binding") }
            BoundFile(config.toString(), Hashes.sha256(config))
        }
        val bindingFile = configDir.resolve("binding.properties")
        val binding = Properties().apply {
            setProperty("agent", Path.of(harness.agentJar.path).toRealPath().toString())
            setProperty("agent_sha256", harness.agentJar.sha256)
            configs.forEach { (task, file) -> setProperty("task.${key(task)}", file.path) }
        }
        Files.newOutputStream(bindingFile, CREATE_NEW).use { binding.store(it, "fixed reviewed observer injection") }
        return Prepared(BoundFile(bindingFile.toString(), Hashes.sha256(bindingFile)), configs, reportDir, reports)
    }

    fun verify(plan: RepositoryRunPlan, root: Path, prepared: Prepared, planSha: String,
        execution: RepositoryCommandExecution, started: Instant, ended: Instant, evidence: Path): BoundFile {
        val harness = requireNotNull(plan.useHarness)
        fun unchanged(ref: BoundFile) = Files.isRegularFile(Path.of(ref.path), NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(Path.of(ref.path)) && Hashes.sha256(Path.of(ref.path)) == ref.sha256
        require(unchanged(prepared.binding) && prepared.configs.values.all(::unchanged) &&
            listOf(harness.producerSource, harness.initScript, harness.agentJar, harness.agentBuildReceipt,
                harness.javaExecutable).plus(harness.allowedExternalClasspath).all(::unchanged)) {
            "observer source, JAR, init script, JVM, classpath or controller config changed"
        }
        val expectedFiles = prepared.reports.values.map { it.fileName.toString() }.toSet()
        require(Files.isDirectory(prepared.reportDirectory, NOFOLLOW_LINKS) && !Files.isSymbolicLink(prepared.reportDirectory) &&
            Files.list(prepared.reportDirectory).use { it.map { file -> file.fileName.toString() }.toList().toSet() } == expectedFiles) {
            "missing/extra task worker reports"
        }
        val expectedProvider = plan.inputs.getValue("providers").associate { root.resolve(it.relativePath).toRealPath() to it.sha256 }
        val allowedExternal = harness.allowedExternalClasspath.associate { Path.of(it.path).toRealPath() to it.sha256 } +
            (Path.of(harness.agentJar.path).toRealPath() to harness.agentJar.sha256)
        val artifacts = plan.inputs.getValue("artifact").map { root.resolve(it.relativePath).toRealPath() }.toSet()
        val java = Path.of(harness.javaExecutable.path).toRealPath()
        val generated = (listOf(root.resolve("build")) + plan.generatedModuleRoots.map { root.resolve(it).resolve("build") })
            .map { it.toAbsolutePath().normalize() }
        val reportRefs = prepared.reports.map { (task, report) ->
            require(Files.isRegularFile(report, NOFOLLOW_LINKS) && !Files.isSymbolicLink(report) &&
                report.toRealPath().startsWith(prepared.reportDirectory.toRealPath()) &&
                (Files.getAttribute(report, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1 &&
                Files.size(report) in 1L..8_388_608L &&
                Files.getLastModifiedTime(report, NOFOLLOW_LINKS).toInstant().let {
                    it.isAfter(started.minusSeconds(2)) && !it.isAfter(ended.plusSeconds(2))
                }) { "missing or stale actual $task JVM observation" }
            val doc = suiteMapper.readTree(report.toFile())
            require(doc.path("schema").asText() == "bc.repository_input_use_worker.v2" &&
                doc.path("scope").asText() == "source-only-not-packaged-runtime" &&
                doc.path("task").asText() == task && doc.path("repository").asText() == plan.repository &&
                doc.path("run_id").asText() == plan.runId &&
                doc.path("reviewed_plan_sha256").asText() == planSha &&
                doc.path("producer_sha256").asText() == harness.producerSource.sha256 &&
                doc.path("java_executable").asText() == java.toString() &&
                doc.path("java_executable_sha256").asText() == harness.javaExecutable.sha256 &&
                doc.path("java_version").asText().substringBefore('.').let { version ->
                    version == plan.runtimeToolchainFacts.getValue("java_major") ||
                        version == "1" && doc.path("java_version").asText().split('.').getOrNull(1) == plan.runtimeToolchainFacts.getValue("java_major")
                }) { "worker task, reviewed plan, actual JVM or producer differs" }
            val pid = doc.path("observed_process_pid").asLong(-1)
            val birth = doc.path("observed_process_start").asText()
            require(pid > 0 && execution.ownedProcesses.any { it.pid == pid && it.startedAt == birth } &&
                runCatching { Instant.parse(doc.path("captured_at").asText()) }.getOrNull()?.let {
                    !it.isBefore(started) && !it.isAfter(ended.plusSeconds(2))
                } == true) { "no owned actual task JVM birth/worker shutdown in run window" }
            val loaded = doc.path("code_sources")
            val classPath = doc.path("class_path")
            require(loaded.isArray && loaded.size() in 1..4096 && classPath.isArray && classPath.size() in 1..4096)
            val loadedPaths = mutableSetOf<Path>()
            for (rows in listOf(loaded, classPath)) rows.forEach { row ->
                val raw = row.path("path").asText()
                val path = Path.of(raw)
                require(path.isAbsolute && path.normalize() == path && !Files.isSymbolicLink(path) &&
                    path.toRealPath() == path && path !in artifacts) { "untrusted or packaged code source" }
                val file = Files.isRegularFile(path, NOFOLLOW_LINKS)
                val directory = Files.isDirectory(path, NOFOLLOW_LINKS)
                require((file && row.path("kind").asText() == "file" &&
                    row.path("sha256").asText().matches(shaPattern) && Hashes.sha256(path) == row.path("sha256").asText()) ||
                    (directory && row.path("kind").asText() == "directory" && row.path("sha256").isNull)) {
                    "worker code source was not measured from current bytes"
                }
                if (file) require(expectedProvider[path] == row.path("sha256").asText() ||
                    allowedExternal[path] == row.path("sha256").asText()) {
                    "undeclared file on worker classpath or loaded code source: $path"
                } else require(generated.any { path.startsWith(it) }) {
                    "unreviewed external or source directory in worker classpath: $path"
                }
                if (rows === loaded) loadedPaths.add(path)
            }
            require(expectedProvider.keys.all { it in loadedPaths }) {
                "listed provider not actually loaded by reviewed task JVM"
            }
            val captured = evidence.resolve("worker-${key(task)}.json")
            Files.copy(report, captured)
            require(Hashes.sha256(captured) == Hashes.sha256(report)) { "worker report changed during capture" }
            BoundFile(captured.toString(), Hashes.sha256(captured))
        }
        // Aggregate only independently checked worker reports. No user-supplied used_inputs list.
        val aggregate = evidence.resolve("input-use.json")
        Files.writeString(aggregate, suiteMapper.writeValueAsString(mapOf(
            "schema" to "bc.repository_input_use.v2", "scope" to "source-only-not-packaged-runtime",
            "repository" to plan.repository, "run_id" to plan.runId, "reviewed_plan_sha256" to planSha,
            "java_executable" to harness.javaExecutable, "agent" to harness.agentJar,
            "controller_binding" to prepared.binding, "worker_configs" to prepared.configs,
            "actual_worker_reports" to reportRefs)) + "\n", CREATE_NEW)
        return BoundFile(aggregate.toString(), Hashes.sha256(aggregate))
    }
}
