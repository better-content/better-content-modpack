package com.bettercontent.tests

import com.bettercontent.tests.release.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/** Synthetic policy fixtures ONLY: generated test PNG/JSON is NOT genuine visual/runtime acceptance. */
@Tag("fast")
class VisualReleaseGateTest {
    @TempDir lateinit var root: Path
    private val mapper = jacksonObjectMapper()
    private fun snapshot(path: Path) = FreshInputSnapshot(path.toString(), Hashes.sha256(path))
    private fun pair(): CandidatePair {
        val client = Files.writeString(root.resolve("client.zip"), "synthetic client")
        val server = Files.writeString(root.resolve("server.zip"), "synthetic server")
        return CandidatePair(root, client, server, Hashes.sha256(client), Hashes.sha256(server))
    }
    private data class Fixture(val pair: CandidatePair, val pendingAt: Instant, val reviewPath: Path,
        val sessionPath: Path, val world: Path, val session: MutableMap<String, Any>, val review: MutableMap<String, Any>,
        val screenshots: List<MutableMap<String, Any>>, val raw: MutableList<MutableMap<String, Any>>)
    private fun fixture(): Fixture {
        val pair = pair()
        val directory = Files.createDirectories(root.resolve("independent-visual"))
        val world = Files.createDirectories(directory.resolve("fixture/server/world"))
        Files.writeString(world.resolve(".bc-visual-world-id"), "af1ba3a3-aa1a-44f1-b873-28c568cace67\n")
        val start = Instant.now().minusSeconds(30)
        val finish = start.plusSeconds(10)
        val raw = mutableListOf<MutableMap<String, Any>>()
        listOf("client_joined", "client_disconnected", "server_saved_and_stopped", "validated", "process_cleanup", "session_finished").forEach { type ->
            raw += linkedMapOf<String, Any>("time" to start.toString(), "type" to type).apply {
                if (type == "validated") put("world_save", world.resolve("level.dat").toString())
                if (type == "process_cleanup") { put("complete", true); put("surviving_pids", emptyList<Long>()) }
                if (type == "session_finished") put("success", true)
            }
        }
        val events = directory.resolve("events.jsonl")
        Files.writeString(events, raw.joinToString("\n") { mapper.writeValueAsString(it) })
        val input = Files.writeString(directory.resolve("input.log"), "SYNTHETIC TEST input provenance, not actual player input")
        val sessionPath = directory.resolve("session.json")
        val session = linkedMapOf<String, Any>("schema" to "bc.in_world_visual_session.v1", "status" to "passed", "run_id" to "new-visual-session",
            "candidates" to pair.document(), "fresh_world" to true, "world_reused" to false, "synthetic_mouse_input" to false,
            "world_ownership_id" to "af1ba3a3-aa1a-44f1-b873-28c568cace67", "world_directory" to world.toString(),
            "started_at" to start.toString(), "completed_at" to finish.toString(), "input_log" to snapshot(input), "events" to snapshot(events))
        mapper.writeValue(sessionPath.toFile(), session)
        val images = listOf("plants", "trees", "bottle-fluid", "kettle-gui", "badge").mapIndexed { index, category ->
            val path = directory.resolve("$category.png")
            val image = BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB)
            image.setRGB(index, index, 0x123456 + index)
            ImageIO.write(image, "png", path.toFile())
            linkedMapOf<String, Any>("category" to category, "file" to snapshot(path), "captured_at" to start.plusSeconds(5).toString(),
                "capture_kind" to "live-client-screenshot", "context" to "in-world", "human_approved" to true)
        }
        val review = linkedMapOf<String, Any>("schema" to "bc.in_world_visual_review.v1", "status" to "passed", "release_run_id" to "release",
            "candidates" to pair.document(), "human_approved" to true, "capture_session" to snapshot(sessionPath), "screenshots" to images,
            "reviewer" to mapOf("kind" to "human", "name" to "SYNTHETIC TEST PERSON - not real acceptance", "approval_reference" to "synthetic unit fixture only",
                "reviewed_at" to finish.plusSeconds(1).toString(), "input_log_reviewed" to true, "screenshots_reviewed_in_world" to true))
        val reviewPath = directory.resolve("review.json"); mapper.writeValue(reviewPath.toFile(), review)
        return Fixture(pair, start.minusSeconds(1), reviewPath, sessionPath, world, session, review, images, raw)
    }
    private fun persist(f: Fixture) {
        val events = f.sessionPath.resolveSibling("events.jsonl")
        Files.writeString(events, f.raw.joinToString("\n") { mapper.writeValueAsString(it) })
        f.session["events"] = snapshot(events)
        mapper.writeValue(f.sessionPath.toFile(), f.session)
        f.review["capture_session"] = snapshot(f.sessionPath)
        mapper.writeValue(f.reviewPath.toFile(), f.review)
    }
    private fun validate(f: Fixture, worlds: Set<String> = emptySet()) =
        validateVisualReview(f.reviewPath, "release", f.pair, f.pendingAt, worlds)

    @Suppress("UNCHECKED_CAST")
    private fun reviewer(f: Fixture) = (f.review.getValue("reviewer") as Map<String, Any>).toMutableMap()
    private fun v2Fixture(kind: String = "agent"): Fixture = fixture().also { f ->
        f.review["schema"] = "bc.in_world_visual_review.v2"
        f.review.remove("human_approved"); f.review["review_approved"] = true
        f.screenshots.forEach { it.remove("human_approved"); it["review_approved"] = true }
        f.review["reviewer"] = reviewer(f).apply {
            put("kind", kind); put("name", "SYNTHETIC TEST $kind identity - not actual acceptance")
        }
        persist(f)
    }
    @Test fun structurallyCompleteAgentV2DoesNotItselfWriteAnyReadyMarker() {
        val f = v2Fixture()
        assertEquals("new-visual-session", validate(f).sessionId)
        assertFalse(Files.exists(root.resolve("ready.json")))
    }
    @Test fun structurallyCompleteHumanV2UsesOnlyRoleNeutralApprovals() {
        val f = v2Fixture("human")
        assertEquals("new-visual-session", validate(f).sessionId)
    }
    @Test fun v2RequiresExplicitBooleanApprovalForReviewAndEveryImage() {
        val f = v2Fixture(); validate(f)
        listOf(null, false, "true").forEach { invalid ->
            if (invalid == null) f.review.remove("review_approved") else f.review["review_approved"] = invalid
            persist(f); assertThrows(IllegalArgumentException::class.java) { validate(f) }
            f.review["review_approved"] = true
            f.screenshots.forEach { image ->
                if (invalid == null) image.remove("review_approved") else image["review_approved"] = invalid
                persist(f); assertThrows(IllegalArgumentException::class.java) { validate(f) }
                image["review_approved"] = true
            }
        }
        persist(f); validate(f)
    }
    @Test fun v2RejectsUnknownReviewerKindAndMissingIdentityOrApprovalReference() {
        val f = v2Fixture(); validate(f)
        val original = reviewer(f)
        listOf("automated", "software", "", "AGENT").forEach { kind ->
            f.review["reviewer"] = original + ("kind" to kind)
            persist(f); assertThrows(IllegalArgumentException::class.java) { validate(f) }
        }
        listOf("name", "approval_reference").forEach { field ->
            f.review["reviewer"] = original - field
            persist(f); assertThrows(IllegalArgumentException::class.java) { validate(f) }
            f.review["reviewer"] = original + (field to " ")
            persist(f); assertThrows(IllegalArgumentException::class.java) { validate(f) }
        }
    }
    @Test fun v2RejectsHumanApprovalFlagsRatherThanManufacturingHumanAttestation() {
        val f = v2Fixture(); validate(f)
        f.review["human_approved"] = true; persist(f)
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
        f.review.remove("human_approved")
        f.screenshots.first()["human_approved"] = true; persist(f)
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
        f.screenshots.first().remove("human_approved")
        f.review["reviewer"] = reviewer(f) + ("human_approved" to true); persist(f)
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
    }
    @Test fun v2AgentMustReviewInputAndActualInWorldCaptureProvenance() {
        val f = v2Fixture(); validate(f)
        val original = reviewer(f)
        listOf("input_log_reviewed", "screenshots_reviewed_in_world").forEach { field ->
            f.review["reviewer"] = original + (field to false)
            persist(f); assertThrows(IllegalArgumentException::class.java) { validate(f) }
        }
        f.review["reviewer"] = original
        f.screenshots.first()["context"] = "title-only"; persist(f)
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
        f.screenshots.first()["context"] = "in-world"
        f.session["synthetic_mouse_input"] = true; persist(f)
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
        f.session["synthetic_mouse_input"] = false; persist(f)
        Files.writeString(f.sessionPath.resolveSibling("input.log"), "changed provenance")
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
    }
    @Test fun legacyV1CannotUseAgentKindEvenWithHumanApprovalFlags() {
        val f = fixture(); validate(f)
        f.review["reviewer"] = reviewer(f) + ("kind" to "agent"); persist(f)
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
    }
    @Test fun v2AgentCannotBypassExactPairSessionCleanupOrScreenshotHash() {
        val f = v2Fixture(); validate(f)
        assertThrows(IllegalArgumentException::class.java) { validate(f, setOf(f.world.toString())) }
        assertThrows(IllegalArgumentException::class.java) {
            validateVisualReview(f.reviewPath, "wrong-release", f.pair, f.pendingAt, emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateVisualReview(f.reviewPath, "release", f.pair, Instant.now(), emptySet())
        }
        val cleanup = f.raw.single { it["type"] == "process_cleanup" }
        cleanup["complete"] = false; persist(f)
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
        cleanup["complete"] = true; persist(f)
        val image = f.screenshots.first()["file"] as FreshInputSnapshot
        Files.writeString(Path.of(image.path), "changed capture")
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
    }

    @Test fun structurallyCompleteHumanReviewStillDoesNotItselfWriteAnyReadyMarker() {
        val f = fixture()
        assertEquals("new-visual-session", validate(f).sessionId)
        assertFalse(Files.exists(root.resolve("ready.json")))
    }
    @Test fun missingHumanApprovalOrAnAutomatedReviewerCannotPass() {
        val f = fixture()
        f.review["human_approved"] = false; persist(f)
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
        f.review["human_approved"] = true
        f.review["reviewer"] = mapOf("kind" to "automated", "name" to "agent", "approval_reference" to "looks good")
        persist(f); assertThrows(IllegalArgumentException::class.java) { validate(f) }
    }
    @Test fun generatedArtTitleScreenAndMissingCategoryCannotSubstituteForInWorldViews() {
        val f = fixture()
        f.screenshots.first()["context"] = "title-only"; persist(f)
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
        f.screenshots.first()["context"] = "in-world"; f.screenshots.first()["capture_kind"] = "generated-static-art"; persist(f)
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
        f.screenshots.first()["capture_kind"] = "live-client-screenshot"; f.review["screenshots"] = f.screenshots.drop(1); persist(f)
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
    }
    @Test fun syntheticMouseInputOrMissingInputProvenanceIsRejected() {
        val f = fixture()
        f.session["synthetic_mouse_input"] = true; persist(f)
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
        f.session.remove("synthetic_mouse_input"); persist(f)
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
        f.session["synthetic_mouse_input"] = false; persist(f)
        Files.writeString(f.sessionPath.resolveSibling("input.log"), "changed input provenance")
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
    }
    @Test fun failedOrReusedWorldAndFailedThenRetriedSessionCannotPass() {
        val f = fixture()
        assertThrows(IllegalArgumentException::class.java) { validate(f, setOf(f.world.toString())) }
        f.session["world_reused"] = true; persist(f)
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
        f.session["world_reused"] = false
        f.raw.last()["success"] = false; persist(f)
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
        f.raw += linkedMapOf("type" to "session_finished", "success" to true); persist(f)
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
    }
    @Test fun staleWrongPairOrChangedScreenshotCannotPass() {
        val f = fixture()
        assertThrows(IllegalArgumentException::class.java) {
            validateVisualReview(f.reviewPath, "another-release", f.pair, f.pendingAt, emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateVisualReview(f.reviewPath, "release", f.pair, Instant.now(), emptySet())
        }
        val image = f.screenshots.first()["file"] as FreshInputSnapshot
        Files.writeString(Path.of(image.path), "modified screenshot")
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
    }
    @Test fun mutatedCandidateCannotReceiveOldHumanAttestation() {
        val f = fixture()
        Files.writeString(f.pair.client, "candidate changed")
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
    }
    @Test fun failedVisualWorldAliasesAndCopiedOwnershipMarkerCannotBePromoted() {
        val f = fixture()
        val attempts = Files.createDirectories(root.resolve("attempts"))
        val alias = root.resolve("world-alias"); Files.createSymbolicLink(alias, f.world)
        mapper.writeValue(attempts.resolve("failed.json").toFile(), mapOf("visual_session_id" to "old-session", "world_directory" to alias.toString(),
            "world_ownership_id" to "af1ba3a3-aa1a-44f1-b873-28c568cace67", "status" to "failed"))
        assertThrows(IllegalArgumentException::class.java) {
            requireUnusedVisualWorld(attempts, "new-session", f.world, "af1ba3a3-aa1a-44f1-b873-28c568cace67")
        }
        val copy = Files.createDirectories(root.resolve("copied-world"))
        Files.copy(f.world.resolve(".bc-visual-world-id"), copy.resolve(".bc-visual-world-id"))
        assertThrows(IllegalArgumentException::class.java) {
            requireUnusedVisualWorld(attempts, "new-session", copy, "af1ba3a3-aa1a-44f1-b873-28c568cace67")
        }
        Files.writeString(copy.resolve(".bc-visual-world-id"), "baa9a574-8c41-4f9a-9c41-7c653ca8e52a")
        assertDoesNotThrow { requireUnusedVisualWorld(attempts, "new-session", copy, "baa9a574-8c41-4f9a-9c41-7c653ca8e52a") }
        mapper.writeValue(attempts.resolve("missing-identity.json").toFile(), mapOf("visual_session_id" to "unknown", "world_directory" to f.world.toString()))
        assertThrows(IllegalArgumentException::class.java) {
            requireUnusedVisualWorld(attempts, "new-session", copy, "baa9a574-8c41-4f9a-9c41-7c653ca8e52a")
        }
    }

    private fun workspace(): Path {
        val project = Files.createDirectories(root.resolve("workspace"))
        val process = ProcessBuilder("git", "init", "-q").directory(project.toFile()).start()
        check(process.waitFor() == 0)
        Files.writeString(project.resolve("pack.toml"), "user pack bytes")
        Files.writeString(project.resolve("index.toml"), "user dirty index bytes")
        Files.createDirectories(project.resolve("mods"))
        Files.writeString(project.resolve("mods/tracked.jar"), "staged version")
        val add = ProcessBuilder("git", "add", "mods/tracked.jar").directory(project.toFile()).start(); check(add.waitFor() == 0)
        Files.writeString(project.resolve("mods/tracked.jar"), "user dirty tracked provider")
        Files.writeString(project.resolve("mods/untracked.jar"), "user untracked provider")
        Files.writeString(project.resolve("launch.main.kts"), "user launcher")
        return project
    }
    @Test fun userDirtyTrackedUntrackedBytesModesAndAbsenceArePreservedBeforeAdmission() {
        val project = workspace()
        Files.setAttribute(project.resolve("launch.main.kts"), "unix:mode", 0x81ed) // regular 0755
        val absent = ActiveMod("new", "new", "new.jar", emptyList())
        val receipt = preserveWorkspace(project, root.resolve("backup"), listOf(absent))
        val node = mapper.readTree(receipt.toFile())
        assertTrue(node.path("gitStatus").asText().contains("mods/tracked.jar"))
        assertEquals("user dirty tracked provider", Files.readString(receipt.parent.resolve("files/mods/tracked.jar")))
        assertEquals("user untracked provider", Files.readString(receipt.parent.resolve("files/mods/untracked.jar")))
        assertEquals(Files.getAttribute(project.resolve("launch.main.kts"), "unix:mode"), Files.getAttribute(receipt.parent.resolve("files/launch.main.kts"), "unix:mode"))
        assertTrue(node.path("items").any { it.path("path").asText() == "mods/new.jar" && it.path("kind").asText() == "absent" })
        assertDoesNotThrow { validateWorkspacePreservation(receipt, project, listOf(absent)) }
        assertThrows(Exception::class.java) { preserveWorkspace(project, receipt.parent, listOf(absent)) }
        assertEquals("user dirty index bytes", Files.readString(receipt.parent.resolve("files/index.toml")))
    }
    @Test fun changedAddedDeletedOrModeChangedUserFilesAbortBeforeOverwrite() {
        val project = workspace()
        val receipt = preserveWorkspace(project, root.resolve("backup"), emptyList())
        val jar = project.resolve("mods/untracked.jar")
        Files.writeString(jar, "concurrent user edit")
        assertThrows(IllegalArgumentException::class.java) { validateWorkspacePreservation(receipt, project) }
        Files.writeString(jar, "user untracked provider")
        Files.writeString(project.resolve("mods/new-user.jar"), "new user data")
        assertThrows(IllegalArgumentException::class.java) { validateWorkspacePreservation(receipt, project) }
        Files.delete(project.resolve("mods/new-user.jar")); Files.delete(jar)
        assertThrows(IllegalArgumentException::class.java) { validateWorkspacePreservation(receipt, project) }
        Files.writeString(jar, "user untracked provider")
        Files.setAttribute(jar, "unix:mode", 0x81c0) // regular 0700
        assertThrows(IllegalArgumentException::class.java) { validateWorkspacePreservation(receipt, project) }
        assertEquals("user untracked provider", Files.readString(receipt.parent.resolve("files/mods/untracked.jar")))
    }
    @Test fun preservationIncludesSymlinkIdentityAndActualDirtyReferentBytes() {
        val project = workspace()
        val target = Files.writeString(project.resolve("provider.jar"), "dirty linked provider bytes")
        Files.createSymbolicLink(project.resolve("mods/linked.jar"), Path.of("../provider.jar"))
        val receipt = preserveWorkspace(project, root.resolve("backup"), emptyList())
        assertEquals(Path.of("../provider.jar"), Files.readSymbolicLink(receipt.parent.resolve("files/mods/linked.jar")))
        assertEquals("dirty linked provider bytes", Files.readString(receipt.parent.resolve("referents/mods/linked.jar")))
        Files.writeString(target, "concurrently rebuilt provider")
        assertThrows(IllegalArgumentException::class.java) { validateWorkspacePreservation(receipt, project) }
        assertDoesNotThrow { validateWorkspacePreservation(receipt) }
    }
    @Test fun missingMandatoryMetadataOrCorruptedBackupBlocksWithoutRootMutation() {
        val project = workspace()
        Files.delete(project.resolve("index.toml"))
        assertThrows(IllegalArgumentException::class.java) { preserveWorkspace(project, root.resolve("missing-backup"), emptyList()) }
        assertFalse(Files.exists(root.resolve("missing-backup")))
        Files.writeString(project.resolve("index.toml"), "user index")
        val receipt = preserveWorkspace(project, root.resolve("backup"), emptyList())
        Files.writeString(receipt.parent.resolve("files/mods/tracked.jar"), "corrupted backup")
        assertThrows(IllegalArgumentException::class.java) { validateWorkspacePreservation(receipt, project) }
        assertEquals("user dirty tracked provider", Files.readString(project.resolve("mods/tracked.jar")))
    }

    private fun zip(path: Path, files: Map<String, ByteArray>) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip -> files.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
        } }
    }
    private fun rawJar(): Path {
        val path = root.resolve("fixture.jar")
        JarOutputStream(Files.newOutputStream(path)).use { jar ->
            jar.putNextEntry(JarEntry("META-INF/mods.toml")); jar.write("[[mods]]\nmodId=\"fixture\"\n".toByteArray()); jar.closeEntry()
        }
        return path
    }
    private val mod = ActiveMod("fixture", "fixture", "fixture.jar", listOf("verifyFull"))
    @Test fun fullArchiveInventoryHashesCustomVendorScriptsConfigsAndResources() {
        val client = root.resolve("inventory.zip")
        zip(client, mapOf("overrides/mods/fixture.jar" to "raw".toByteArray(), "overrides/mods/vendor.jar" to "vendor".toByteArray(),
            "overrides/run.sh" to "script".toByteArray(), "overrides/config/setting.toml" to "config".toByteArray(), "overrides/assets/icon.png" to "resource".toByteArray()))
        val members = archiveMembers(client, "client", listOf(mod))
        assertEquals(5, members.size)
        assertEquals(setOf("custom", "third-party-binary", "script", "config", "resource-or-metadata"), members.map { it.category }.toSet())
        assertTrue(members.all { it.sha256.matches(Regex("[a-f0-9]{64}")) })
    }
    @Test fun addedRemovedOrChangedThirdPartyBinaryCannotSilentlyPass() {
        val baseline = listOf(ArchiveMember("server", "root/mods/vendor.jar", 1, "a".repeat(64), "third-party-binary"))
        assertDoesNotThrow { requireUnchangedThirdParty(baseline, baseline) }
        assertThrows(IllegalArgumentException::class.java) { requireUnchangedThirdParty(baseline, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { requireUnchangedThirdParty(baseline, baseline + baseline.single().copy(path = "root/mods/new.jar")) }
        assertThrows(IllegalArgumentException::class.java) { requireUnchangedThirdParty(baseline, listOf(baseline.single().copy(sha256 = "b".repeat(64)))) }
    }
    @Test fun postQualificationMetadataDecorationIsRejectedButLegacyAnnotatorRemainsUsable() {
        val jar = rawJar(); val qualified = Hashes.sha256(jar)
        annotateJar(jar, mod, "commit", "source-fingerprint")
        assertNotEquals(qualified, Hashes.sha256(jar))
        val client = root.resolve("decorated-client.zip"); val server = root.resolve("decorated-server.zip")
        zip(client, mapOf("overrides/mods/fixture.jar" to Files.readAllBytes(jar)))
        zip(server, mapOf("root/mods/fixture.jar" to Files.readAllBytes(jar)))
        assertThrows(IllegalArgumentException::class.java) {
            requireRawCustomMembers(archiveMembers(client, "client", listOf(mod)) + archiveMembers(server, "server", listOf(mod)), mapOf("fixture.jar" to qualified))
        }
    }
    @Test fun unsafeArchiveMemberIsRejected() {
        val archive = root.resolve("unsafe.zip"); zip(archive, mapOf("../escape.jar" to byteArrayOf(1)))
        assertThrows(IllegalArgumentException::class.java) { archiveMembers(archive, "server", listOf(mod)) }
    }
    @Test fun onlySealedVisualBlockedRunCanProceedAndProofMutationFailsClosed() {
        val pair = pair()
        val proof = Files.writeString(root.resolve("proof.json"), "successful runtime proof fixture")
        val pending = root.resolve("pending.json")
        val body = mapOf("schema" to "bc.fresh_pending_visual.v1", "status" to "awaiting-visual", "run_id" to "run", "candidates" to pair.document(), "proofs" to listOf(snapshot(proof)))
        mapper.writeValue(pending.toFile(), body)
        val result = root.resolve("result.json")
        fun result(status: String) { mapper.writeValue(result.toFile(), mapOf("run_id" to "run", "status" to status,
            "blocker_code" to "visual-review-required", "pending_sha256" to Hashes.sha256(pending))) }
        result("blocked")
        assertDoesNotThrow { validatePendingVisualSeal(pending, result, pair) }
        result("failed")
        assertThrows(IllegalArgumentException::class.java) { validatePendingVisualSeal(pending, result, pair) }
        result("blocked"); Files.writeString(proof, "altered after runtime")
        assertThrows(IllegalArgumentException::class.java) { validatePendingVisualSeal(pending, result, pair) }
    }
}
