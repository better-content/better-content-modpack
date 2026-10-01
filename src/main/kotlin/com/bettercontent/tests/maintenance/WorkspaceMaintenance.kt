package com.bettercontent.tests.maintenance

import com.bettercontent.tests.CandidateLocator
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

data class CandidateHashes(val clientSha256: String, val serverSha256: String)

data class SuiteReport(
    val suite: String,
    val status: String,
    val updatedAt: Instant,
    val failure: String?,
    val candidate: CandidateHashes?,
)

data class RunDecision(
    val runId: String,
    val action: String,
    val reasons: List<String>,
    val suites: Map<String, String>,
    val sizeKiB: Long = 0,
)

data class EvidencePlan(
    val currentRunId: String?,
    val decisions: List<RunDecision>,
    val reclaimableKiB: Long = 0,
) {
    val retained: List<RunDecision> get() = decisions.filter { it.action == "retain" }
    val prunable: List<RunDecision> get() = decisions.filter { it.action == "prune" }
}

private data class RunInfo(
    val runId: String,
    val reports: List<SuiteReport>,
    val malformed: Boolean,
    val releaseRequest: Boolean,
    val releaseProvenance: CandidateHashes?,
    val empty: Boolean,
)

object EvidencePlanner {
    private val mapper = jacksonObjectMapper()
    private val runIdPattern = Regex("""\d{8}T\d{6}Z-\d+""")
    private val knownSuites = setOf("candidate", "server", "multiplayer", "singleplayer")

    fun plan(evidenceRoot: Path, currentCandidate: CandidateHashes?): EvidencePlan {
        if (!evidenceRoot.isDirectory()) return EvidencePlan(null, emptyList())
        val runs = Files.list(evidenceRoot).use { stream ->
            stream.sorted().map { loadRun(evidenceRoot, it) }.toList()
        }
        val matchingProvenance = runs.filter { run ->
            currentCandidate != null && run.releaseProvenance == currentCandidate
        }
        val matchingReports = runs.filter { run ->
            currentCandidate != null && (
                run.reports.any { it.candidate == currentCandidate }
            )
        }
        val currentRun = (matchingProvenance.ifEmpty { matchingReports }).maxByOrNull { it.runId }
        val latestPassed = (knownSuites + runs.flatMap { run -> run.reports.map { it.suite } }).associateWith { suite ->
            runs.flatMap { run -> run.reports.map { run to it } }
                .filter { (_, report) -> report.suite == suite && report.status == "passed" }
                .maxWithOrNull(compareBy<Pair<RunInfo, SuiteReport>> { it.second.updatedAt }.thenBy { it.first.runId })
        }
        val newestProvenancedRelease = runs.filter { it.releaseProvenance != null }.maxByOrNull { it.runId }

        val decisions = runs.map { run ->
            val retain = linkedSetOf<String>()
            if (!runIdPattern.matches(run.runId)) retain += "unrecognized run directory name"
            if (run.malformed) retain += "malformed or unreadable run evidence"
            if (run == currentRun) retain += "matches current candidate hashes"
            latestPassed.forEach { (suite, latest) ->
                if (latest?.first == run) retain += "latest passed $suite evidence"
            }
            run.reports.filter { it.status == "failed" || it.status == "running" }.forEach { report ->
                val superseding = latestPassed[report.suite]
                if (superseding == null || superseding.second.updatedAt <= report.updatedAt) {
                    retain += "unresolved ${report.status} ${report.suite} evidence"
                }
            }

            val releaseOnlySuperseded = run.reports.isEmpty() && run.releaseRequest &&
                newestProvenancedRelease != null && newestProvenancedRelease.runId > run.runId
            val emptySuperseded = run.empty && runs.any { it.runId > run.runId }
            if (run.reports.isEmpty() && !releaseOnlySuperseded && !emptySuperseded && retain.isEmpty()) {
                retain += "unclassified evidence without suite reports"
            }
            val reasons = if (retain.isNotEmpty()) retain.toList() else buildList {
                if (releaseOnlySuperseded) add("release preparation superseded by a later packaged release")
                if (emptySuperseded) add("empty run superseded by later evidence")
                run.reports.forEach { report ->
                    when (report.status) {
                        "passed" -> add("older passed ${report.suite} evidence")
                        "failed", "running" -> add("${report.status} ${report.suite} evidence superseded by a later pass")
                        else -> add("completed ${report.suite} evidence superseded by a later retained run")
                    }
                }
            }.distinct()
            RunDecision(
                run.runId,
                if (retain.isEmpty()) "prune" else "retain",
                reasons,
                run.reports.associate { it.suite to it.status },
            )
        }
        return EvidencePlan(currentRun?.runId, decisions)
    }

    private fun loadRun(evidenceRoot: Path, path: Path): RunInfo {
        require(path.normalize().parent == evidenceRoot.normalize()) { "run is outside evidence root: $path" }
        if (Files.isSymbolicLink(path) || !path.isDirectory()) {
            return RunInfo(path.fileName.toString(), emptyList(), true, false, null, false)
        }
        var malformed = false
        val reports = mutableListOf<SuiteReport>()
        knownSuites.forEach { suite ->
            val reportPath = path.resolve(suite).resolve("run.json")
            if (reportPath.exists()) {
                runCatching { loadReport(reportPath) }.onSuccess { reports += it }.onFailure { malformed = true }
            }
        }
        Files.list(path).use { stream ->
            stream.filter { it.fileName.toString().startsWith("target-") }.forEach { directory ->
                val reportPath = directory.resolve("run.json")
                if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory) || !Files.isRegularFile(reportPath)) {
                    malformed = true
                } else {
                    runCatching { loadReport(reportPath) }.onSuccess { report ->
                        if (report.suite == directory.fileName.toString()) reports += report else malformed = true
                    }.onFailure { malformed = true }
                }
            }
        }
        val provenancePath = path.resolve("release/provenance.json")
        val provenance = if (provenancePath.exists()) {
            runCatching { candidateFromProvenance(mapper.readTree(provenancePath.toFile())) }
                .onFailure { malformed = true }.getOrNull()
        } else null
        val empty = Files.list(path).use { !it.findAny().isPresent }
        return RunInfo(
            path.fileName.toString(),
            reports,
            malformed,
            path.resolve("release-request.txt").exists(),
            provenance,
            empty,
        )
    }

    private fun loadReport(path: Path): SuiteReport {
        val node = mapper.readTree(path.toFile())
        val suite = node.requiredText("suite")
        val status = node.requiredText("status")
        val updated = Instant.parse(node.requiredText("updated_at"))
        val failure = node.path("failure").takeUnless { it.isMissingNode || it.isNull }?.asText()
        return SuiteReport(suite, status, updated, failure, candidateFromEvents(path.parent.resolve("events.jsonl")))
    }

    private fun candidateFromEvents(path: Path): CandidateHashes? {
        if (!path.exists()) return null
        Files.newBufferedReader(path).useLines { lines ->
            lines.forEach { line ->
                val node = runCatching { mapper.readTree(line) }.getOrNull() ?: return@forEach
                if (node.path("type").asText() == "candidate_selected") {
                    val client = node.path("client_sha256").asText()
                    val server = node.path("server_sha256").asText()
                    if (client.isNotBlank() && server.isNotBlank()) return CandidateHashes(client, server)
                }
            }
        }
        return null
    }

    private fun candidateFromProvenance(node: JsonNode): CandidateHashes? {
        val candidates = node.path("candidates")
        val client = candidates.path("client_sha256").asText()
        val server = candidates.path("server_sha256").asText()
        return if (client.isBlank() || server.isBlank()) null else CandidateHashes(client, server)
    }

    private fun JsonNode.requiredText(name: String): String = path(name).asText().takeIf { it.isNotBlank() }
        ?: error("missing $name")
}

private val mapper = jacksonObjectMapper()
private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

fun main(args: Array<String>) {
    require(args.size in 3..4) { "usage: WorkspaceMaintenance ROOT <audit|prune|resume> <true|false> [TRANSACTION_ID]" }
    val root = Path.of(args[0]).toAbsolutePath().normalize()
    val mode = args[1]
    val apply = args[2].toBooleanStrict()
    require(mode == "audit" && !apply && args.size == 3 ||
        mode == "prune" && apply && args.size == 3 ||
        mode == "resume" && apply && args.size == 4) { "prune and resume require explicit apply" }
    val currentPair = if (root.resolve("dist").isDirectory()) CandidateLocator.locate(root) else null
    val currentHashes = currentPair?.let { CandidateHashes(it.clientSha256, it.serverSha256) }
    val evidenceRoot = root.resolve("generated/test-evidence")
    val plan = withSizes(evidenceRoot, EvidencePlanner.plan(evidenceRoot, currentHashes))
    if (mode == "audit") {
        println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest(root, mode, currentHashes, plan, emptyList(), "audit")))
        return
    }
    require(currentHashes != null) { "prune requires exactly one current client/server candidate pair" }
    if (mode == "resume") {
        requireNoCompetingProcesses(root)
        resumePrune(root, currentHashes, args[3])
        return
    }
    require(plan.currentRunId != null) { "current candidate has no matching retained evidence" }
    val repositoryState = repositoryState(root.parent)
    requireNoCompetingProcesses(root)
    applyPrune(root, currentHashes, plan, repositoryState)
}

private fun withSizes(evidenceRoot: Path, plan: EvidencePlan): EvidencePlan {
    val decisions = plan.decisions.map { decision ->
        decision.copy(sizeKiB = allocatedKiB(evidenceRoot.resolve(decision.runId)))
    }
    val prunable = decisions.filter { it.action == "prune" }.map { evidenceRoot.resolve(it.runId) }
    return plan.copy(decisions = decisions, reclaimableKiB = allocatedKiB(prunable))
}

private fun applyPrune(root: Path, current: CandidateHashes, plan: EvidencePlan, repositoryBaseline: Map<String, String>) {
    val stamp = timestampFormatter.format(Instant.now())
    val transactionId = "$stamp-${ProcessHandle.current().pid()}"
    val transaction = root.parent.resolve(".local/share/worklane/maintenance/$transactionId").also { it.createDirectories() }
    val quarantine = transaction.resolve("quarantine").also { it.createDirectories() }
    mapper.writerWithDefaultPrettyPrinter().writeValue(
        transaction.resolve("workspace-before.json").toFile(),
        manifest(root, "prune", current, plan, emptyList(), "planned", repositoryBaseline),
    )
    val moved = mutableListOf<Pair<Path, Path>>()
    var deletionStarted = false
    try {
        plan.prunable.forEach { decision ->
            val source = directChild(root.resolve("generated/test-evidence"), decision.runId)
            val target = quarantine.resolve("test-evidence").resolve(decision.runId)
            target.parent.createDirectories()
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
            moved += source to target
        }
        distributionServerTrees(root).forEach { source ->
            val release = source.parent.parent.fileName.toString()
            val target = quarantine.resolve("dist-server-tree").resolve(release).resolve("server-tree")
            target.parent.createDirectories()
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
            moved += source to target
        }
        require(repositoryState(root.parent) == repositoryBaseline) { "repository state changed during maintenance" }
        val afterPair = CandidateLocator.locate(root)
        val afterHashes = CandidateHashes(afterPair.clientSha256, afterPair.serverSha256)
        require(afterHashes == current) { "candidate hashes changed during maintenance" }
        plan.retained.forEach { decision ->
            require(directChild(root.resolve("generated/test-evidence"), decision.runId).isDirectory()) {
                "protected evidence disappeared: ${decision.runId}"
            }
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(
            transaction.resolve("workspace-after.json").toFile(),
            manifest(root, "prune", current, plan, moved.map { it.first.toString() }, "validated", repositoryBaseline),
        )
        deletionStarted = true
        deleteTree(quarantine)
        mapper.writerWithDefaultPrettyPrinter().writeValue(
            transaction.resolve("workspace-after.json").toFile(),
            manifest(root, "prune", current, plan, moved.map { it.first.toString() }, "completed", repositoryBaseline),
        )
    } catch (error: Throwable) {
        if (deletionStarted) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(
                transaction.resolve("workspace-after.json").toFile(),
                manifest(root, "prune", current, plan, moved.map { it.first.toString() }, "deletion_incomplete", repositoryBaseline),
            )
        } else {
            moved.asReversed().forEach { (source, target) ->
                if (target.exists() && !source.exists()) {
                    source.parent.createDirectories()
                    Files.move(target, source, StandardCopyOption.ATOMIC_MOVE)
                }
            }
        }
        throw error
    }
    println("maintenance manifest: ${transaction.resolve("workspace-after.json")}")
    println("pruned evidence runs: ${plan.prunable.size}")
    println("planned evidence reclaim: ${plan.reclaimableKiB} KiB")
}

private fun resumePrune(root: Path, current: CandidateHashes, transactionId: String) {
    require(Regex("""\d{8}T\d{6}Z-\d+""").matches(transactionId)) { "invalid maintenance transaction ID" }
    val transaction = root.parent.resolve(".local/share/worklane/maintenance").resolve(transactionId)
    require(transaction.isDirectory() && !Files.isSymbolicLink(transaction)) { "missing maintenance transaction" }
    val record = mapper.readTree(transaction.resolve("workspace-after.json").toFile())
    require(record.path("schema").asText() == "bc.workspace_maintenance.v1")
    require(record.path("mode").asText() == "prune")
    require(record.path("status").asText() in setOf("validated", "deletion_incomplete"))
    require(record.path("repository").asText() == root.toString())
    require(record.path("current_candidate").path("clientSha256").asText() == current.clientSha256 &&
        record.path("current_candidate").path("serverSha256").asText() == current.serverSha256)
    require(record.path("repository_state") == mapper.valueToTree<JsonNode>(repositoryState(root.parent))) {
        "repository state changed since maintenance; inspect transaction before resuming"
    }
    val quarantine = transaction.resolve("quarantine")
    require(!Files.isSymbolicLink(quarantine)) { "unsafe maintenance quarantine" }
    deleteTree(quarantine)
    (record as com.fasterxml.jackson.databind.node.ObjectNode).put("status", "completed")
    mapper.writerWithDefaultPrettyPrinter().writeValue(transaction.resolve("workspace-after.json").toFile(), record)
    println("resumed maintenance transaction: " + transactionId)
}

private fun manifest(
    root: Path,
    mode: String,
    current: CandidateHashes?,
    plan: EvidencePlan,
    removedTargets: List<String>,
    status: String,
    repositoryState: Map<String, String>? = null,
): Map<String, Any?> = linkedMapOf(
    "schema" to "bc.workspace_maintenance.v1",
    "created_at" to Instant.now().toString(),
    "status" to status,
    "mode" to mode,
    "repository" to root.toString(),
    "current_candidate" to current,
    "current_evidence_run" to plan.currentRunId,
    "planned_reclaim_kib" to plan.reclaimableKiB,
    "decisions" to plan.decisions,
    "removed_targets" to removedTargets,
    "repository_state" to repositoryState,
)

private fun directChild(parent: Path, name: String): Path {
    val child = parent.resolve(name).normalize()
    require(child.parent == parent.normalize()) { "unsafe child path: $name" }
    require(!Files.isSymbolicLink(child)) { "refusing symbolic-link target: $child" }
    return child
}

private fun distributionServerTrees(root: Path): List<Path> {
    val dist = root.resolve("dist")
    if (!dist.isDirectory()) return emptyList()
    return Files.list(dist).use { releases ->
        releases.filter { it.isDirectory() && !Files.isSymbolicLink(it) }
            .map { it.resolve("server/server-tree") }
            .filter { it.isDirectory() && !Files.isSymbolicLink(it) }
            .toList()
    }
}

private fun repositoryState(workspace: Path): Map<String, String> {
    val repositories = buildList {
        add(workspace.resolve("better-content-modpack"))
        val source = workspace.resolve("mod_source")
        if (source.isDirectory()) Files.list(source).use { stream ->
            stream.filter { it.resolve(".git").isDirectory() }.sorted().forEach { add(it) }
        }
    }
    return repositories.associate { repository ->
        val digest = MessageDigest.getInstance("SHA-256")
        fun git(vararg args: String): ByteArray {
            val process = ProcessBuilder(listOf("git") + args).directory(repository.toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT).start()
            val bytes = process.inputStream.use { it.readAllBytes() }
            require(process.waitFor() == 0) { "Git state inspection failed: $repository" }
            return bytes
        }
        digest.update(git("rev-parse", "HEAD"))
        digest.update(git("status", "--porcelain=v1", "-z", "--untracked-files=all"))
        val diff = ProcessBuilder("git", "diff", "HEAD", "--binary", "--no-ext-diff")
            .directory(repository.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        diff.inputStream.use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        require(diff.waitFor() == 0) { "Git diff inspection failed: $repository" }
        val untracked = git("ls-files", "--others", "--exclude-standard", "-z")
            .toString(Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() }.sorted()
        untracked.forEach { relative ->
            val file = repository.resolve(relative).normalize()
            require(file.startsWith(repository)) { "untracked path escaped repository: $relative" }
            digest.update(relative.toByteArray())
            when {
                Files.isSymbolicLink(file) -> digest.update(Files.readSymbolicLink(file).toString().toByteArray())
                Files.isRegularFile(file) -> Files.newInputStream(file).use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
            }
        }
        repository.toString() to digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private fun requireNoCompetingProcesses(root: Path) {
    val ignored = buildSet {
        add(ProcessHandle.current().pid())
        var parent = ProcessHandle.current().parent()
        while (parent.isPresent) {
            val handle = parent.get()
            add(handle.pid())
            parent = handle.parent()
        }
    }
    val markers = listOf("gradle", "minecraft", "forge", "packwiz", "portablemc")
    val competing = ProcessHandle.allProcesses().filter { process ->
        val command = process.info().commandLine().orElse("")
        val cwd = runCatching { Files.readSymbolicLink(Path.of("/proc", process.pid().toString(), "cwd")) }
            .getOrNull()
        process.pid() !in ignored && markers.any { it in command.lowercase() } &&
            (root.toString() in command || cwd?.startsWith(root) == true)
    }.map { it.pid() to it.info().commandLine().orElse("") }.toList()
    require(competing.isEmpty()) { "competing pack processes are active: $competing" }
}

private fun allocatedKiB(path: Path): Long {
    if (!path.exists()) return 0
    val process = ProcessBuilder("du", "-sk", "--", path.toString()).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    require(process.waitFor() == 0) { "du failed for $path" }
    return output.substringBefore('\t').trim().toLong()
}

private fun allocatedKiB(paths: List<Path>): Long {
    if (paths.isEmpty()) return 0
    val process = ProcessBuilder(listOf("du", "-skc", "--") + paths.map { it.toString() }).start()
    val output = process.inputStream.bufferedReader().readLines()
    require(process.waitFor() == 0) { "du failed for evidence prune set" }
    return output.last().substringBefore('\t').trim().toLong()
}

private fun deleteTree(root: Path) {
    if (!root.exists()) return
    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            Files.delete(file)
            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(dir: Path, error: java.io.IOException?): FileVisitResult {
            if (error != null) throw error
            Files.delete(dir)
            return FileVisitResult.CONTINUE
        }
    })
}
