package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class CheckpointStoreTest {
    @TempDir lateinit var campaign: Path
    private val lineage = UUID.randomUUID().toString()
    private val sha = "a".repeat(64)
    private val bindings = CheckpointBindings("fixture-campaign", lineage,
        mapOf("client_sha256" to sha, "server_sha256" to "b".repeat(64)), "c".repeat(64), "d".repeat(64))

    private data class Setup(val store: CheckpointStore, val roots: Map<String, Path>, val closure: EpochClosure)
    private fun bound(path: Path) = BoundFile(path.toString(), Hashes.sha256(path))
    private fun setup(maxBytes: Long = 100_000_000L): Setup {
        Files.writeString(campaign.resolve("checkpoint-budget.json"), suiteMapper.writeValueAsString(mapOf(
            "schema" to "bc.playtest_checkpoint_budget.v1", "bindings" to bindings,
            "max_bytes" to maxBytes, "keep_free_bytes" to 2L * 1024 * 1024 * 1024)))
        val epoch = Files.createDirectories(campaign.resolve("epochs/epoch-one"))
        val server = Files.createDirectories(epoch.resolve("server/world"))
        Files.writeString(server.resolve(".bc-suite-world-id"), lineage + "\n")
        val save = server.resolve("level.dat")
        Files.writeString(save, "fixture world save\n")
        val client = Files.createDirectories(epoch.resolve("client"))
        Files.writeString(client.resolve("options.txt"), "guiScale:2\n")
        val evidence = Files.createDirectories(epoch.resolve("evidence"))
        val log = evidence.resolve("server.log")
        Files.writeString(log, "[Server thread/INFO] Saving chunks for level minecraft:overworld\n")
        val events = evidence.resolve("events.jsonl")
        // Pure fixture metadata: not an actual launched process or runtime evidence.
        val process = OwnedProcessIdentity(Long.MAX_VALUE, Instant.now().minusSeconds(20).toString())
        Files.writeString(events, listOf(
            mapOf("type" to "server_started", "pid" to process.pid, "started_at" to process.startedAt),
            mapOf("type" to "server_saved_and_stopped"),
            mapOf("type" to "validated", "world_save" to save.toString(), "candidate_hashes_unchanged" to true),
            mapOf("type" to "process_cleanup", "complete" to true, "surviving_pids" to emptyList<Long>(),
                "owned_processes" to listOf(process))
        ).joinToString("\n") { suiteMapper.writeValueAsString(it) } + "\n")
        val closed = Instant.now().minusSeconds(2).toString()
        val receipt = evidence.resolve("closure.json")
        Files.writeString(receipt, suiteMapper.writeValueAsString(mapOf(
            "schema" to "bc.playtest_epoch_closure.v2", "purpose" to bindings.purpose, "epoch_id" to "epoch-one",
            "native_session_id" to "native-one", "closed_at" to closed,
            "events" to bound(events), "server_log" to bound(log),
            "world_marker" to bound(server.resolve(".bc-suite-world-id")), "world_save" to bound(save))))
        return Setup(CheckpointStore(campaign, bindings), mapOf("server" to epoch.resolve("server"),
            "client" to client), EpochClosure("epoch-one", "native-one", closed, true, true,
            listOf(process), emptyList(), bound(receipt)))
    }

    @Test fun `offline seal verifies every file and restore is independent and receipt bound`() {
        val fixture = setup()
        val reference = fixture.store.seal("first", fixture.roots, fixture.closure, null)
        assertEquals(4, fixture.store.verify(reference).files.size)
        val restored = fixture.store.restore(reference, "epoch-two", campaign.resolve("epochs/epoch-two"))
        assertEquals(4, fixture.store.verifyRestore(restored).filesVerified)
        Files.writeString(Path.of(restored.roots.getValue("client")).resolve("options.txt"), "changed\n")
        assertThrows(IllegalArgumentException::class.java) { fixture.store.verifyRestore(restored) }
        assertEquals(restored, fixture.store.verifyRestoreReceipt(restored)) // Legitimate post-epoch writes do not rewrite the pre-run receipt.
        assertEquals("guiScale:2\n", Files.readString(fixture.roots.getValue("client").resolve("options.txt")))
        assertEquals(4, fixture.store.verify(reference).files.size)
    }

    @Test fun `intentionally empty runtime directories are sealed copied restored and checked`() {
        val fixture = setup()
        val client = fixture.roots.getValue("client")
        for (name in listOf(".suite-home", ".suite-tmp", ".suite-cache", ".suite-data", ".suite-config")) {
            Files.createDirectories(client.resolve(name))
        }
        val reference = fixture.store.seal("origin", fixture.roots, fixture.closure, null)
        val sealed = fixture.store.verify(reference)
        assertEquals(5, sealed.files.count { it.root == "client" && it.directory == true })
        assertTrue(sealed.treeSha256.matches(Regex("[0-9a-f]{64}")))
        val restored = fixture.store.restore(reference, "epoch-two", campaign.resolve("epochs/epoch-two"))
        for (name in listOf(".suite-home", ".suite-tmp", ".suite-cache", ".suite-data", ".suite-config")) {
            assertTrue(Files.isDirectory(Path.of(restored.roots.getValue("client")).resolve(name)))
        }
        Files.delete(Path.of(restored.roots.getValue("client")).resolve(".suite-cache"))
        assertThrows(IllegalArgumentException::class.java) { fixture.store.verifyRestore(restored) }
        assertEquals(restored, fixture.store.verifyRestoreReceipt(restored))
    }

    @Test fun `old file-only receipt still verifies when every directory is a required parent`() {
        val fixture = setup()
        val reference = fixture.store.seal("first", fixture.roots, fixture.closure, null)
        val receiptFile = Path.of(reference.receipt.path)
        val modern = fixture.store.verify(reference)
        val legacyFiles = modern.files.filter { it.directory == null }
        val hash = MessageDigest.getInstance("SHA-256").digest(suiteMapper.writeValueAsBytes(legacyFiles))
            .joinToString("") { "%02x".format(it) }
        Files.writeString(receiptFile, suiteMapper.writeValueAsString(modern.copy(files = legacyFiles, treeSha256 = hash)) + "\n")
        val legacyRef = CheckpointRef(reference.id, bound(receiptFile))
        assertEquals(legacyFiles, fixture.store.verify(legacyRef).files)
        val restored = fixture.store.restore(legacyRef, "epoch-two", campaign.resolve("epochs/epoch-two"))
        assertEquals(legacyFiles.size, fixture.store.verifyRestore(restored).filesVerified)
    }

    @Test fun `post-epoch restore audit rejects rewritten receipt and removed exclusive claim`() {
        val fixture = setup()
        val reference = fixture.store.seal("first", fixture.roots, fixture.closure, null)
        val restored = fixture.store.restore(reference, "epoch-two", campaign.resolve("epochs/epoch-two"))
        val report = Path.of(restored.directory).resolve("restore.json")
        val original = Files.readString(report)
        Files.writeString(report, original.replace("epoch-two", "epoch-three"))
        assertThrows(IllegalArgumentException::class.java) { fixture.store.verifyRestoreReceipt(restored) }
        Files.writeString(report, original)
        Files.delete(campaign.resolve("epochs/.epoch-two.claim"))
        assertThrows(IllegalArgumentException::class.java) { fixture.store.verifyRestoreReceipt(restored) }
    }

    @Test fun `altered bytes or changed candidate cannot be verified`() {
        val fixture = setup()
        val reference = fixture.store.seal("first", fixture.roots, fixture.closure, null)
        val other = CheckpointStore(campaign, bindings.copy(supportSha256 = "e".repeat(64)))
        assertThrows(IllegalArgumentException::class.java) { other.verify(reference) }
        val promoted = CheckpointStore(campaign, bindings.copy(purpose = ExecutionPurpose.QUALIFICATION.wire))
        assertThrows(IllegalArgumentException::class.java) { promoted.verify(reference) }
        val sealed = campaign.resolve("checkpoints/first/snapshot/client/options.txt")
        Files.writeString(sealed, "tampered\n")
        assertThrows(IllegalArgumentException::class.java) { fixture.store.verify(reference) }
    }

    @Test fun `missing or relabeled diagnostic source closure cannot seal qualifying ancestry`() {
        val fixture = setup()
        val closureFile = Path.of(fixture.closure.evidence.path)
        val closure = suiteMapper.readTree(closureFile.toFile()) as ObjectNode
        closure.remove("purpose")
        Files.writeString(closureFile, suiteMapper.writeValueAsString(closure))
        val updated = fixture.closure.copy(evidence = bound(closureFile))
        assertThrows(IllegalArgumentException::class.java) { fixture.store.seal("missing-purpose", fixture.roots, updated, null) }
        closure.put("purpose", ExecutionPurpose.QUALIFICATION.wire)
        Files.writeString(closureFile, suiteMapper.writeValueAsString(closure))
        assertThrows(IllegalArgumentException::class.java) { fixture.store.seal("relabelled-purpose", fixture.roots,
            updated.copy(evidence = bound(closureFile)), null) }
    }

    @Test fun `duplicate seals and nonfresh restore targets never replace state`() {
        val fixture = setup()
        val reference = fixture.store.seal("first", fixture.roots, fixture.closure, null)
        assertThrows(IllegalArgumentException::class.java) { fixture.store.seal("first", fixture.roots, fixture.closure, null) }
        Files.createDirectory(campaign.resolve("existing"))
        assertThrows(IllegalArgumentException::class.java) {
            fixture.store.restore(reference, "epoch-two", campaign.resolve("existing"))
        }
        assertTrue(Files.exists(campaign.resolve("checkpoints/first/receipt.json")))
    }

    @Test fun `symlink and hardlinked source trees fail before seal`() {
        val fixture = setup()
        val source = fixture.roots.getValue("client").resolve("options.txt")
        val link = fixture.roots.getValue("client").resolve("alias.txt")
        Files.createSymbolicLink(link, source)
        assertThrows(IllegalArgumentException::class.java) { fixture.store.seal("linked", fixture.roots, fixture.closure, null) }
        Files.delete(link)
        Files.createLink(link, source)
        assertThrows(IllegalArgumentException::class.java) { fixture.store.seal("linked", fixture.roots, fixture.closure, null) }
    }

    @Test fun `empty or mismatched raw process roster cannot seal a checkpoint`() {
        val fixture = setup()
        assertThrows(IllegalArgumentException::class.java) {
            fixture.store.seal("missing-process", fixture.roots, fixture.closure.copy(ownedProcesses = emptyList()), null)
        }
        val eventFile = campaign.resolve("epochs/epoch-one/evidence/events.jsonl")
        val lines = Files.readAllLines(eventFile).toMutableList()
        val launch = suiteMapper.readTree(lines.first()) as ObjectNode
        launch.put("pid", Long.MAX_VALUE - 1)
        lines[0] = suiteMapper.writeValueAsString(launch)
        Files.writeString(eventFile, lines.joinToString("\n") + "\n")
        val closureFile = Path.of(fixture.closure.evidence.path)
        val closureDoc = suiteMapper.readTree(closureFile.toFile()) as ObjectNode
        closureDoc.set<JsonNode>("events", suiteMapper.valueToTree(bound(eventFile)))
        Files.writeString(closureFile, suiteMapper.writeValueAsString(closureDoc) + "\n")
        val rebound = fixture.closure.copy(evidence = bound(closureFile))
        assertThrows(IllegalArgumentException::class.java) {
            fixture.store.seal("wrong-raw-launch", fixture.roots, rebound, null)
        }
    }

    @Test fun `lineage closure save log and live PID cannot be asserted by flags alone`() {
        val fixture = setup()
        val marker = fixture.roots.getValue("server").resolve("world/.bc-suite-world-id")
        Files.writeString(marker, "wrong-world\n")
        assertThrows(IllegalArgumentException::class.java) { fixture.store.seal("wrong", fixture.roots, fixture.closure, null) }
        Files.writeString(marker, lineage + "\n")
        val current = ProcessHandle.current()
        val started = current.info().startInstant().orElseThrow().toString()
        val live = fixture.closure.copy(ownedProcesses = listOf(OwnedProcessIdentity(current.pid(), started)))
        assertThrows(IllegalArgumentException::class.java) { fixture.store.seal("live", fixture.roots, live, null) }
        val log = campaign.resolve("epochs/epoch-one/evidence/server.log")
        Files.writeString(log, "INFO no save acknowledgment\n")
        assertThrows(IllegalArgumentException::class.java) { fixture.store.seal("no-ack", fixture.roots, fixture.closure, null) }
    }

    @Test fun `explicit budget binds campaign and rejects oversize before copying`() {
        val fixture = setup(100)
        assertThrows(IllegalArgumentException::class.java) { fixture.store.seal("oversized", fixture.roots, fixture.closure, null) }
        assertFalse(Files.exists(campaign.resolve("checkpoints/oversized")))
    }
}
