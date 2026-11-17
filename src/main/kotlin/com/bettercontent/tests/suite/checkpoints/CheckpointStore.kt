package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.idPattern
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Offline, copy-only checkpoint store. Never snapshots a running process or reuses an operator world. */
internal class CheckpointStore(campaignRoot: Path, private val bindings: CheckpointBindings) {
    private val campaign = campaignRoot.toRealPath()
    private val checkpointBase = campaign.resolve("checkpoints")
    private val partialBase = campaign.resolve("checkpoint-partials")
    private val markerName = ".bc-suite-world-id"
    private val maximumFiles = 250_000 // Files AND directories, including intentionally empty roots.
    private val emptyDirectorySha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private val minimumFreeBytes = 2L * 1024 * 1024 * 1024

    init {
        UUID.fromString(bindings.lineageId)
        require(bindings.purpose in setOf(ExecutionPurpose.DIAGNOSTIC.wire, ExecutionPurpose.QUALIFICATION.wire) &&
            bindings.campaignId.isNotBlank() && bindings.lineageId.isNotBlank() &&
            bindings.candidate.keys == setOf("client_sha256", "server_sha256") &&
            (bindings.candidate.values + bindings.supportSha256 + bindings.configurationSha256).all(shaPattern::matches)) {
            "checkpoint requires exact campaign, lineage, candidate, support and configuration fingerprints"
        }
    }

    private fun ensureStorage() {
        listOf(checkpointBase, partialBase).forEach { path ->
            if (Files.exists(path, NOFOLLOW_LINKS)) require(Files.isDirectory(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path))
            else Files.createDirectory(path)
        }
    }

    fun seal(id: String, roots: Map<String, Path>, closure: EpochClosure, parent: CheckpointRef?): CheckpointRef {
        require(idPattern.matches(id) && roots.keys == setOf("server", "client")) { "checkpoint identity or roots invalid" }
        ensureStorage()
        require(!Files.exists(checkpointBase.resolve(id), NOFOLLOW_LINKS) &&
            !Files.exists(checkpointBase.resolve("$id.claim"), NOFOLLOW_LINKS)) { "checkpoint already sealed or reserved: $id" }
        require(closure.epochId.isNotBlank() && closure.nativeSessionId.isNotBlank() && closure.saveValidated &&
            closure.processCleanupComplete && closure.survivingPids.isEmpty() && !Instant.parse(closure.closedAt).isAfter(Instant.now())) {
            "epoch not cleanly saved and stopped"
        }
        checkProcessesStopped(closure)
        val evidence = checkedBound(closure.evidence)
        require(evidence.startsWith(campaign) && !evidence.startsWith(checkpointBase) &&
            !evidence.startsWith(partialBase)) { "closure evidence must predate and live outside checkpoints" }
        val closureDoc = suiteMapper.readTree(evidence.toFile())
        require(closureDoc.path("schema").asText() == "bc.playtest_epoch_closure.v2" &&
            closureDoc.path("purpose").asText() == bindings.purpose && closureDoc.path("epoch_id").asText() == closure.epochId &&
            closureDoc.path("native_session_id").asText() == closure.nativeSessionId &&
            closureDoc.path("closed_at").asText() == closure.closedAt) { "unbound closure identity" }
        parent?.let { require(it.id != id); verify(it) }
        val sourceRoots = roots.mapValues { (_, path) -> requireOwnedSource(path) }
        require(sourceRoots.getValue("server") != sourceRoots.getValue("client") &&
            !sourceRoots.getValue("server").startsWith(sourceRoots.getValue("client")) &&
            !sourceRoots.getValue("client").startsWith(sourceRoots.getValue("server"))) { "overlapping source roots" }
        val marker = sourceRoots.getValue("server").resolve("world").resolve(markerName)
        require(Files.isRegularFile(marker, NOFOLLOW_LINKS) && !Files.isSymbolicLink(marker) &&
            Files.readString(marker).trim() == bindings.lineageId) { "server world lineage marker absent or different" }
        validateClosureArtifacts(closureDoc, evidence, closure, marker, sourceRoots.getValue("server").resolve("world/level.dat"))
        val original = inventory(sourceRoots)
        require(original.isNotEmpty()) { "empty checkpoint" }
        val bytes = Math.addExact(original.sumOf { it.size }, 1024L * 1024L)
        checkDiskBudget(bytes)
        Files.writeString(checkpointBase.resolve("$id.claim"), closure.epochId + "\n", CREATE_NEW)
        val stage = Files.createDirectory(partialBase.resolve("$id-${UUID.randomUUID()}.partial"))
        val final = checkpointBase.resolve(id)
        try {
            val snapshots = stage.resolve("snapshot")
            Files.createDirectory(snapshots)
            for ((name, source) in sourceRoots) copyTree(source, snapshots.resolve(name))
            require(inventory(mapOf("server" to snapshots.resolve("server"), "client" to snapshots.resolve("client"))) == original) {
                "snapshot differs from source before seal"
            }
            require(inventory(sourceRoots) == original) { "source changed during snapshot" }
            val receipt = CheckpointReceipt("bc.playtest_checkpoint.v2", id, bindings, parent, closure.epochId, closure,
                Instant.now().toString(), final.resolve("snapshot").toString(), original, treeHash(original))
            Files.writeString(stage.resolve("receipt.json"), suiteMapper.writeValueAsString(receipt) + "\n", CREATE_NEW)
            require(!Files.exists(final, NOFOLLOW_LINKS)) { "checkpoint target appeared during staging" }
            Files.move(stage, final, ATOMIC_MOVE)
            val ref = CheckpointRef(id, BoundFile(final.resolve("receipt.json").toString(), Hashes.sha256(final.resolve("receipt.json"))))
            verify(ref)
            return ref
        } catch (error: Exception) {
            // Keep partial bytes for diagnosis. Never reuse or overwrite an old checkpoint.
            throw IllegalStateException("checkpoint seal failed; retained staging directory $stage", error)
        }
    }

    fun verify(ref: CheckpointRef): CheckpointReceipt = verifyChain(ref, mutableSetOf())

    private fun verifyChain(ref: CheckpointRef, visited: MutableSet<String>): CheckpointReceipt {
        require(idPattern.matches(ref.id) && shaPattern.matches(ref.receipt.sha256))
        require(visited.add(ref.id) && visited.size <= 10_000) { "checkpoint parent cycle or excessive chain" }
        val directory = checkpointBase.resolve(ref.id)
        val receiptFile = directory.resolve("receipt.json")
        require(Files.isRegularFile(checkpointBase.resolve("${ref.id}.claim"), NOFOLLOW_LINKS) &&
            Files.readString(checkpointBase.resolve("${ref.id}.claim")).trim().isNotBlank() &&
            Files.isRegularFile(receiptFile, NOFOLLOW_LINKS) && !Files.isSymbolicLink(receiptFile) &&
            Path.of(ref.receipt.path).toAbsolutePath().normalize() == receiptFile && Hashes.sha256(receiptFile) == ref.receipt.sha256) {
            "stale or altered checkpoint receipt"
        }
        val receipt = suiteMapper.readValue(receiptFile.toFile(), CheckpointReceipt::class.java)
        require(suiteMapper.readTree(receiptFile.toFile()).path("bindings").path("purpose").asText() == bindings.purpose) {
            "checkpoint purpose missing or changed"
        }
        require(receipt.schema == "bc.playtest_checkpoint.v2" && receipt.id == ref.id && receipt.bindings == bindings &&
            Files.readString(checkpointBase.resolve("${ref.id}.claim")).trim() == receipt.sourceEpoch &&
            receipt.snapshotDirectory == directory.resolve("snapshot").toString() &&
            receipt.treeSha256 == treeHash(receipt.files) && receipt.files.isNotEmpty() &&
            receipt.closedEpoch.epochId == receipt.sourceEpoch && receipt.closedEpoch.saveValidated &&
            receipt.closedEpoch.processCleanupComplete && receipt.closedEpoch.survivingPids.isEmpty()) { "checkpoint receipt binding invalid" }
        require(receipt.parent?.id != ref.id)
        receipt.parent?.let { parent -> require(parent.id != ref.id); verifyChain(parent, visited) }
        checkProcessesStopped(receipt.closedEpoch)
        val evidence = checkedBound(receipt.closedEpoch.evidence)
        val closureDoc = suiteMapper.readTree(evidence.toFile())
        val recordedMarker = Path.of(receipt.snapshotDirectory).resolve("server/world/$markerName")
        // Closure artifacts belong to the original epoch, not to the copied snapshot.
        validateClosureArtifacts(closureDoc, evidence, receipt.closedEpoch,
            Path.of(closureDoc.path("world_marker").path("path").asText()),
            Path.of(closureDoc.path("world_save").path("path").asText()))
        require(Files.readString(recordedMarker).trim() == bindings.lineageId)
        val snapshot = directory.resolve("snapshot")
        require(inventoryMatches(mapOf("server" to snapshot.resolve("server"), "client" to snapshot.resolve("client")), receipt.files)) {
            "sealed inventory mutated, missing or acquired extra files"
        }
        val marker = snapshot.resolve("server/world/$markerName")
        require(Files.isRegularFile(marker, NOFOLLOW_LINKS) && Files.readString(marker).trim() == bindings.lineageId)
        return receipt
    }

    /**
     * Post-epoch immutable pre-run audit: the real world and client are supposed to have changed.
     * Validate the exclusive restore claim, SHA-bound original restore receipt and sealed parent,
     * but do NOT hash the now-mutable epoch roots as if no trial had occurred.
     */
    fun verifyRestoreReceipt(restored: RestoredCheckpoint): RestoredCheckpoint {
        require(idPattern.matches(restored.epochId) && shaPattern.matches(restored.restoreReceipt.sha256))
        val destination = Path.of(restored.directory).toAbsolutePath().normalize()
        require(destination.startsWith(campaign) && destination != campaign && !destination.startsWith(checkpointBase) &&
            !destination.startsWith(partialBase) && Files.isDirectory(destination, NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(destination) && destination.toRealPath() == destination)
        val receipt = verify(restored.reference)
        val claim = destination.parent.resolve(".${destination.fileName}.claim")
        val report = destination.resolve("restore.json")
        require(Files.isRegularFile(claim, NOFOLLOW_LINKS) && !Files.isSymbolicLink(claim) &&
            (Files.getAttribute(claim, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1 &&
            Files.readString(claim).trim() == restored.epochId &&
            Files.isRegularFile(report, NOFOLLOW_LINKS) && !Files.isSymbolicLink(report) &&
            (Files.getAttribute(report, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1 &&
            Path.of(restored.restoreReceipt.path).toAbsolutePath().normalize() == report &&
            Hashes.sha256(report) == restored.restoreReceipt.sha256) { "restore receipt/claim changed since pre-run audit" }
        val stored = suiteMapper.readValue(report.toFile(), RestoredCheckpoint::class.java)
        require(stored == restored.copy(restoreReceipt = BoundFile(report.toString(), "")) &&
            restored.roots == mapOf("server" to destination.resolve("server").toString(),
                "client" to destination.resolve("client").toString()) &&
            !Instant.parse(restored.restoredAt).isBefore(Instant.parse(receipt.sealedAt)) &&
            !Instant.parse(restored.restoredAt).isAfter(Instant.now()) &&
            restored.filesVerified == receipt.files.size && restored.treeSha256 == receipt.treeSha256) {
            "recorded pre-run inventory does not bind sealed checkpoint"
        }
        return restored
    }

    /** Strict pre-run check only: never use this after legitimate native epoch writes. */
    fun verifyRestore(restored: RestoredCheckpoint): RestoredCheckpoint {
        verifyRestoreReceipt(restored)
        val receipt = verify(restored.reference)
        val destination = Path.of(restored.directory).toAbsolutePath().normalize()
        require(inventoryMatches(mapOf("server" to destination.resolve("server"), "client" to destination.resolve("client")), receipt.files) &&
            Files.readString(destination.resolve("server/world/$markerName")).trim() == bindings.lineageId) {
            "restored epoch differs from sealed checkpoint before trial"
        }
        return restored
    }

    fun restore(ref: CheckpointRef, epochId: String, destination: Path): RestoredCheckpoint {
        require(idPattern.matches(epochId))
        val receipt = verify(ref)
        val target = destination.toAbsolutePath().normalize()
        require(target.startsWith(campaign) && target != campaign && !target.startsWith(checkpointBase) &&
            !target.startsWith(partialBase) && !Files.exists(target, NOFOLLOW_LINKS)) { "restore destination not fresh and campaign-owned" }
        val parent = target.parent.toRealPath()
        require(parent.startsWith(campaign) && target.parent == parent && !Files.isSymbolicLink(parent))
        checkDiskBudget(Math.addExact(receipt.files.sumOf { it.size }, 1024L * 1024L))
        Files.writeString(parent.resolve(".${target.fileName}.claim"), epochId + "\n", CREATE_NEW)
        val stage = Files.createDirectory(parent.resolve(".${target.fileName}-$epochId-${UUID.randomUUID()}.partial"))
        try {
            val snapshot = Path.of(receipt.snapshotDirectory)
            for (name in listOf("server", "client")) copyTree(snapshot.resolve(name), stage.resolve(name))
            require(inventoryMatches(mapOf("server" to stage.resolve("server"), "client" to stage.resolve("client")), receipt.files)) {
                "restored copy differs from sealed inventory"
            }
            require(Files.readString(stage.resolve("server/world/$markerName")).trim() == bindings.lineageId)
            val result = RestoredCheckpoint(ref, epochId, target.toString(),
                mapOf("server" to target.resolve("server").toString(), "client" to target.resolve("client").toString()),
                Instant.now().toString(), receipt.files.size, receipt.treeSha256,
                BoundFile(target.resolve("restore.json").toString(), ""))
            Files.writeString(stage.resolve("restore.json"), suiteMapper.writeValueAsString(result.copy(restoreReceipt =
                BoundFile(target.resolve("restore.json").toString(), ""))) + "\n", CREATE_NEW)
            require(!Files.exists(target, NOFOLLOW_LINKS)) { "restore target appeared during staging" }
            Files.move(stage, target, ATOMIC_MOVE)
            val restored = result.copy(restoreReceipt = BoundFile(target.resolve("restore.json").toString(), Hashes.sha256(target.resolve("restore.json"))))
            return verifyRestore(restored)
        } catch (error: Exception) {
            throw IllegalStateException("restore failed; retained partial directory $stage", error)
        }
    }

    private fun validateClosureArtifacts(doc: JsonNode, receipt: Path, closure: EpochClosure, marker: Path, save: Path) {
        require(doc.path("schema").asText() == "bc.playtest_epoch_closure.v2" &&
            doc.path("purpose").asText() == bindings.purpose && doc.path("epoch_id").asText() == closure.epochId &&
            doc.path("native_session_id").asText() == closure.nativeSessionId &&
            doc.path("closed_at").asText() == closure.closedAt) { "closure changed after seal" }
        fun artifact(key: String): Path {
            val node = doc.path(key)
            require(node.path("path").asText().isNotBlank() && shaPattern.matches(node.path("sha256").asText()))
            return checkedBound(suiteMapper.treeToValue(node, BoundFile::class.java)).also {
                require(it.startsWith(campaign) && it != receipt && !it.startsWith(checkpointBase) &&
                    !it.startsWith(partialBase)) { "closure artifact escaped source epoch" }
            }
        }
        require(artifact("world_marker").toRealPath() == marker.toRealPath() &&
            artifact("world_save").toRealPath() == save.toRealPath() &&
            save.fileName.toString() == "level.dat" && Files.readString(marker).trim() == bindings.lineageId) {
            "closure world identity/save differs from owned source"
        }
        val eventFile = artifact("events")
        require(Files.size(eventFile) in 1L..25_000_000L)
        val events = Files.readAllLines(eventFile).filter(String::isNotBlank).map { suiteMapper.readTree(it) }
        val kinds = listOf("server_saved_and_stopped", "validated", "process_cleanup")
        val positions = kinds.map { kind -> events.indices.filter { events[it].path("type").asText() == kind }.also {
            require(it.size == 1) { "missing/repeated closure event: $kind" }
        }.single() }
        require(positions.zipWithNext().all { (a, b) -> a < b } &&
            Path.of(events[positions[1]].path("world_save").asText()).toRealPath() == save.toRealPath() &&
            events[positions[1]].path("candidate_hashes_unchanged").asBoolean(false) &&
            events[positions[2]].path("complete").asBoolean(false) &&
            events[positions[2]].path("surviving_pids").isArray &&
            events[positions[2]].path("surviving_pids").isEmpty &&
            events.none { it.path("type").asText() in setOf("failure", "world_replaced", "candidate_mutated") }) {
            "actual ordered save/validation/process closure absent"
        }
        fun processIdentity(node: JsonNode): OwnedProcessIdentity {
            require(node.path("pid").isIntegralNumber && node.path("pid").asLong() > 0 &&
                node.path("started_at").isTextual) { "missing raw PID/start identity" }
            val start = node.path("started_at").asText()
            require(runCatching { Instant.parse(start) }.isSuccess) { "invalid raw process birthtime" }
            return OwnedProcessIdentity(node.path("pid").asLong(), start)
        }
        val cleanupRoster = events[positions[2]].path("owned_processes")
        require(cleanupRoster.isArray && cleanupRoster.size() > 0 && closure.ownedProcesses.isNotEmpty()) {
            "empty/unbound raw process cleanup roster"
        }
        val tracked = cleanupRoster.map(::processIdentity)
        require(tracked.map { it.pid }.distinct().size == tracked.size &&
            closure.ownedProcesses.map { it.pid }.distinct().size == closure.ownedProcesses.size &&
            tracked.toSet() == closure.ownedProcesses.toSet()) { "closure omitted or changed tracked child PID/start identity" }
        val launches = events.withIndex().filter { (_, event) ->
            event.path("type").asText() in setOf("server_started", "client_started", "display_started")
        }
        require(launches.count { it.value.path("type").asText() == "server_started" } == 1 &&
            launches.all { (index, _) -> index < positions[0] } &&
            launches.map { processIdentity(it.value) }.distinct().size == launches.size &&
            launches.map { processIdentity(it.value) }.all { it in tracked }) {
            "raw server/client/display launch not accounted for by exact cleanup PID/birthtime"
        }
        val log = artifact("server_log")
        require(Files.size(log) in 1L..25_000_000L && Regex("(?i)(saving chunks|all chunks are saved|saved the game|saved chunks)")
            .containsMatchIn(Files.readString(log))) { "missing raw server save acknowledgment" }
    }

    private fun checkDiskBudget(additionalBytes: Long) {
        val budgetFile = campaign.resolve("checkpoint-budget.json")
        require(Files.isRegularFile(budgetFile, NOFOLLOW_LINKS) && !Files.isSymbolicLink(budgetFile)) { "explicit storage budget missing" }
        val budget = suiteMapper.readTree(budgetFile.toFile())
        require(budget.path("schema").asText() == "bc.playtest_checkpoint_budget.v1" &&
            suiteMapper.treeToValue(budget.path("bindings"), CheckpointBindings::class.java) == bindings &&
            budget.path("max_bytes").isIntegralNumber && budget.path("keep_free_bytes").isIntegralNumber &&
            budget.path("keep_free_bytes").asLong() >= minimumFreeBytes) { "unbound checkpoint storage budget" }
        val maximum = budget.path("max_bytes").asLong()
        require(maximum > 0 && additionalBytes in 1L..maximum)
        var existing = 0L
        Files.walk(campaign).use { paths -> paths.forEach { path ->
            if (Files.isRegularFile(path, NOFOLLOW_LINKS)) existing = Math.addExact(existing, Files.size(path))
        } }
        require(Math.addExact(existing, additionalBytes) <= maximum &&
            Files.getFileStore(campaign).usableSpace >= Math.addExact(additionalBytes, budget.path("keep_free_bytes").asLong())) {
            "insufficient campaign disk budget or filesystem headroom"
        }
    }

    private fun checkedBound(ref: BoundFile): Path {
        require(shaPattern.matches(ref.sha256))
        val file = Path.of(ref.path).toAbsolutePath().normalize()
        require(Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) &&
            Hashes.sha256(file) == ref.sha256) { "missing or stale bound evidence" }
        return file
    }

    private fun checkProcessesStopped(closure: EpochClosure) {
        require(closure.ownedProcesses.isNotEmpty() &&
            closure.ownedProcesses.map { it.pid }.distinct().size == closure.ownedProcesses.size) {
            "closed epoch has no complete owned-process roster"
        }
        closure.ownedProcesses.forEach { owned ->
            require(owned.pid > 0 && runCatching { Instant.parse(owned.startedAt) }.isSuccess)
            val handle = ProcessHandle.of(owned.pid)
            val observedStart = if (handle.isPresent && handle.get().isAlive) handle.get().info().startInstant().orElse(null) else null
            require(handle.isEmpty || !handle.get().isAlive ||
                (observedStart != null && observedStart != Instant.parse(owned.startedAt))) {
                "source epoch process still running: ${owned.pid}"
            }
        }
    }

    private fun requireOwnedSource(path: Path): Path {
        require(!Files.isSymbolicLink(path))
        val root = path.toRealPath()
        require(root == path.toAbsolutePath().normalize() && root.startsWith(campaign) && root != campaign && !root.startsWith(checkpointBase) &&
            !root.startsWith(partialBase) && Files.isDirectory(root, NOFOLLOW_LINKS)) { "non-owned source root" }
        return root
    }

    private fun inventory(roots: Map<String, Path>): List<SnapshotFile> {
        val entries = mutableListOf<SnapshotFile>()
        roots.toSortedMap().forEach { (name, root) ->
            require(Files.isDirectory(root, NOFOLLOW_LINKS) && !Files.isSymbolicLink(root))
            Files.walk(root).use { paths -> paths.forEach { path ->
                val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                require(!attributes.isSymbolicLink && !attributes.isOther) { "symlink/special filesystem entry: $path" }
                require(attributes.isRegularFile || attributes.isDirectory) { "nonregular checkpoint entry: $path" }
                if (path == root) return@forEach // Root is implicit; every nested directory is explicit.
                require(entries.size < maximumFiles) { "checkpoint file/directory count exceeds safe bound" }
                val relative = root.relativize(path).toString()
                require('\\' !in relative && '\n' !in relative && '\r' !in relative) { "ambiguous file path" }
                if (attributes.isDirectory) {
                    entries += SnapshotFile(name, relative, 0L, emptyDirectorySha256, Files.isExecutable(path), true)
                } else {
                    require((Files.getAttribute(path, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1) {
                        "hardlinked checkpoint source: $path"
                    }
                    entries += SnapshotFile(name, relative, attributes.size(), Hashes.sha256(path), Files.isExecutable(path))
                }
            } }
        }
        return entries.sortedWith(compareBy(SnapshotFile::root, SnapshotFile::path))
    }

    /** Old sealed receipts omitted directory records; they could not contain an empty directory. */
    private fun inventoryMatches(roots: Map<String, Path>, recorded: List<SnapshotFile>): Boolean {
        val actual = inventory(roots)
        if (recorded.any { it.directory == true }) return actual == recorded
        if (recorded.any { it.directory != null }) return false
        val requiredDirectories = recorded.flatMap { file ->
            val root = roots.getValue(file.root)
            val parents = mutableListOf<Pair<String, String>>()
            var parent = root.resolve(file.path).parent
            while (parent != null && parent != root) {
                parents += file.root to root.relativize(parent).toString()
                parent = parent.parent
            }
            parents
        }.toSet()
        return actual.filter { it.directory != true } == recorded &&
            actual.filter { it.directory == true }.all { (it.root to it.path) in requiredDirectories }
    }

    private fun copyTree(source: Path, destination: Path) {
        require(Files.isDirectory(source, NOFOLLOW_LINKS))
        Files.createDirectory(destination)
        Files.walk(source).use { paths -> paths.forEach { path ->
            if (path == source) return@forEach
            val target = destination.resolve(source.relativize(path).toString())
            val attr = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            require(!attr.isSymbolicLink && !attr.isOther)
            if (attr.isDirectory) Files.createDirectory(target)
            else {
                require(attr.isRegularFile && (Files.getAttribute(path, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1)
                // GNU cp's reflink=auto clones CoW extents where supported; no-dereference forbids link following.
                val reflink = runCatching { ProcessBuilder("cp", "--reflink=auto", "--no-dereference", "--", path.toString(), target.toString())
                    .redirectErrorStream(true).start().also { it.inputStream.use { stream -> stream.transferTo(java.io.OutputStream.nullOutputStream()) } }.waitFor() == 0 }.getOrDefault(false)
                if (!reflink) {
                    Files.deleteIfExists(target) // cp may have left a partial; retain staging on fallback failure.
                    Files.copy(path, target, NOFOLLOW_LINKS, COPY_ATTRIBUTES)
                }
                require(!Files.isSymbolicLink(target) && (Files.getAttribute(target, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1)
            }
        } }
    }

    private fun treeHash(files: List<SnapshotFile>): String {
        // Keep pre-directory receipt hashes stable, regardless of mapper null-inclusion policy:
        // old file records had exactly five fields and no `directory` JSON key.
        val canonical = files.sortedWith(compareBy(SnapshotFile::root, SnapshotFile::path)).map { entry ->
            suiteMapper.valueToTree<ObjectNode>(entry).also { if (entry.directory == null) it.remove("directory") }
        }
        return MessageDigest.getInstance("SHA-256").digest(suiteMapper.writeValueAsBytes(canonical))
            .joinToString("") { "%02x".format(it) }
    }
}
