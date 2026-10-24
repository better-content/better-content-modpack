package com.bettercontent.tests

import com.bettercontent.tests.release.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

@Tag("fast")
class VerificationSessionTest {
    private val mapper = jacksonObjectMapper()
    private val mods = listOf(ActiveMod("provider", "provider", "provider.jar", listOf("verifyFull", "stageRuntimeJar")),
        ActiveMod("consumer", "consumer", "consumer.jar", listOf("verifyFull", "stageRuntimeJar"), listOf("provider")),
        ActiveMod("independent", "independent", "independent.jar", listOf("verifyFull", "stageRuntimeJar")))

    private fun git(root: Path, vararg args: String) = BoundedCommand.capture(root, listOf("git") + args, Duration.ofSeconds(10))
    private fun fixture(): Path {
        val root = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "session-").resolve("pack")
        Files.createDirectories(root.resolve("mods"))
        git(root, "init", "-q")
        Files.writeString(root.resolve("contract.txt"), "authored contract\n")
        Files.writeString(root.resolve(".gitignore"), "generated/\n.worklane/\nmods/\n")
        git(root, "add", "contract.txt", ".gitignore")
        git(root, "-c", "user.email=test@example.invalid", "-c", "user.name=Test", "commit", "-qm", "fixture")
        mods.forEach { mod ->
            val source = root.parent.resolve("mod_source/${mod.repository}")
            Files.createDirectories(source)
            git(source, "init", "-q")
            Files.writeString(source.resolve("source.txt"), mod.repository)
            Files.writeString(source.resolve(".gitignore"), "build/\n.gradle/\n")
            git(source, "add", "source.txt", ".gitignore")
            git(source, "-c", "user.email=test@example.invalid", "-c", "user.name=Test", "commit", "-qm", "fixture")
            Files.writeString(root.resolve("mods/${mod.artifact}"), mod.repository)
        }
        return root
    }
    private fun begin(root: Path, owner: String = "conversation"): String {
        val id = java.util.UUID.randomUUID().toString().replace("-", "")
        val path = root.resolve(".worklane/testing-sessions/$id.json")
        Files.createDirectories(path.parent)
        mapper.writeValue(path.toFile(), VerificationReceipt(session_id = id, owner = owner))
        return id
    }
    private fun built(root: Path) = mods.map { mod ->
        val id = root.resolve(".worklane/testing-sessions").toFile().listFiles()!!
            .filter { it.extension == "json" }.map { mapper.readValue<VerificationReceipt>(it) }
            .single { it.active_run != null }.active_run!!
        val path = root.resolve("generated/test-evidence/$id/release/staged-jars/${mod.artifact}")
        Files.createDirectories(path.parent)
        Files.copy(root.resolve("mods/${mod.artifact}"), path, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        val commit = git(root.parent.resolve("mod_source/${mod.repository}"), "rev-parse", "HEAD").trim()
        BuiltMod(mod, commit, path, Hashes.sha256(path), "rebuilt")
    }
    private fun checkpointAffected(session: VerificationSession, items: List<BuiltMod>) {
        items.forEach { item ->
            if (item.definition.repository in session.affected) session.checkpoint(item,
                item.definition.dependsOn.associateWith { dependency ->
                    items.single { it.definition.repository == dependency }.sha256
                })
        }
    }
    private fun verify(root: Path, id: String, force: Boolean = false): Set<String> =
        VerificationSession(root, mods, "verification-${java.util.UUID.randomUUID().toString().replace("-", "")}", force,
            "conversation", id).use { session ->
            val items = built(root)
            checkpointAffected(session, items)
            session.commit(items); session.affected
        }

    @Test fun `new baseline then same conversation reuse and explicit rebuild`() {
        val root = fixture(); val id = begin(root)
        assertEquals(mods.map { it.repository }.toSet(), verify(root, id))
        assertEquals(emptySet<String>(), verify(root, id))
        assertEquals(mods.map { it.repository }.toSet(), verify(root, id, true))
        assertThrows(IllegalArgumentException::class.java) {
            VerificationSession(root, mods, "wrong-owner", false, "other-conversation", id)
        }
    }
    @Test fun `only owner-admitted helper shares baseline without replacing coordinator identity`() {
        val root = fixture(); val id = begin(root); verify(root, id)
        val path = root.resolve(".worklane/testing-sessions/$id.json")
        val receipt: VerificationReceipt = mapper.readValue(path.toFile())
        mapper.writeValue(path.toFile(), receipt.copy(helpers = setOf("admitted-helper")))
        VerificationSession(root, mods, "helper-reuse", false, "admitted-helper", id).use {
            assertTrue(it.affected.isEmpty()); it.commit(built(root))
        }
        val after: VerificationReceipt = mapper.readValue(path.toFile())
        assertEquals("conversation", after.owner)
        assertTrue(after.shared_context.isNotEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            VerificationSession(root, mods, "unadmitted", false, "unadmitted-helper", id)
        }
    }
    @Test fun `source and provider hash invalidate consumers while independent remains reusable`() {
        val root = fixture(); val id = begin(root); verify(root, id)
        Files.writeString(root.parent.resolve("mod_source/provider/source.txt"), "changed")
        assertEquals(setOf("provider", "consumer"), verify(root, id))
        fun snapshot(repository: String): Path {
            val receipt: VerificationReceipt = mapper.readValue(root.resolve(".worklane/testing-sessions/$id.json").toFile())
            return root.resolve(receipt.checkpoints.getValue(repository).staged_path)
        }
        assertTrue(snapshot("provider").toFile().setWritable(true)) // Deliberate adversarial tamper of sealed bytes.
        Files.writeString(snapshot("provider"), "corrupt")
        assertEquals(setOf("provider", "consumer"), verify(root, id))
        Files.delete(snapshot("independent"))
        VerificationSession(root, mods, "missing", false, "conversation", id).use {
            assertEquals(setOf("independent"), it.affected)
            Files.writeString(root.resolve("mods/independent.jar"), "new verified jar")
            val items = built(root); checkpointAffected(it, items); it.commit(items)
        }
    }
    @Test fun `external contract mutation partial baseline and during-build mutation fail closed`() {
        val root = fixture(); val id = begin(root); verify(root, id)
        Files.writeString(root.resolve("contract.txt"), "changed pack contract")
        assertEquals(mods.map { it.repository }.toSet(), verify(root, id))
        VerificationSession(root, mods, "partial", false, "conversation", id).use { /* intentionally incomplete */ }
        assertEquals(emptySet<String>(), verify(root, id))
        VerificationSession(root, mods, "mutation", false, "conversation", id).use {
            Files.writeString(root.parent.resolve("mod_source/consumer/source.txt"), "mutated while building")
            assertThrows(IllegalArgumentException::class.java) { it.commit(built(root)) }
        }
        val receipt: VerificationReceipt = mapper.readValue(root.resolve(".worklane/testing-sessions/$id.json").toFile())
        assertEquals("failed", receipt.baseline)
    }
    @Test fun `invocation closes activity and preserves immutable completed history`() {
        val root = fixture()
        var id = ""
        VerificationSession(root, mods, "ephemeral", false, "conversation", null).use {
            id = it.id; assertTrue(it.fullBaseline)
            val items = built(root); checkpointAffected(it, items); it.commit(items)
        }
        val receipt: VerificationReceipt = mapper.readValue(root.resolve(".worklane/testing-sessions/$id.json").toFile())
        assertEquals("ended", receipt.state)
        assertTrue(Files.exists(root.resolve(".worklane/testing-sessions/completed/ephemeral.json")))
        VerificationSession(root, mods, "new-conversation", false, "other-conversation", null).use {
            assertTrue(it.affected.isEmpty())
            mods.forEach { mod -> assertNotNull(it.reusable(mod)) }
            it.commit(built(root))
        }
    }
    @Test fun `artifact mutation after reuse decision fails rather than gaining an old verification id`() {
        val root = fixture(); val id = begin(root); verify(root, id)
        VerificationSession(root, mods, "reuse-race", false, "conversation", id).use {
            assertTrue(it.affected.isEmpty())
            Files.writeString(root.resolve("mods/provider.jar"), "unverified replacement")
            assertThrows(IllegalArgumentException::class.java) { it.commit(built(root)) }
        }
    }
    @Test fun `missing or corrupt auxiliary gate artifact invalidates its provider and consumers`() {
        val root = fixture(); val id = begin(root)
        val active = mods.map { if (it.repository == "provider") it.copy(supportArtifacts = listOf("build/libs/support.jar")) else it }
        val support = root.parent.resolve("mod_source/provider/build/libs/support.jar")
        Files.createDirectories(support.parent); Files.writeString(support, "verified support")
        fun artifacts() = built(root).map { item ->
            val mod = active.single { it.repository == item.definition.repository }
            item.copy(definition = mod, supportHashes = supportArtifactHashes(root.parent.resolve("mod_source/${mod.repository}"), mod))
        }
        VerificationSession(root, active, "initial", false, "conversation", id).use {
            val items = artifacts(); checkpointAffected(it, items); it.commit(items)
        }
        Files.delete(support)
        VerificationSession(root, active, "missing-support", false, "conversation", id).use {
            assertTrue(it.affected.isEmpty())
            assertNotNull(it.reusable(active.first()))
            assertEquals("verified support", Files.readString(support))
            assertTrue(Files.getPosixFilePermissions(support).contains(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE))
            it.commit(artifacts())
        }
        val receipt: VerificationReceipt = mapper.readValue(root.resolve(".worklane/testing-sessions/$id.json").toFile())
        val snapshot = root.resolve(receipt.checkpoints.getValue("provider").staged_path)
            .resolveSibling("provider.jar.support/build/libs/support.jar")
        assertTrue(snapshot.toFile().setWritable(true)) // Deliberate adversarial tamper of sealed support.
        Files.writeString(snapshot, "corrupt retained support")
        VerificationSession(root, active, "corrupt-support", false, "conversation", id).use {
            assertEquals(setOf("provider", "consumer"), it.affected)
        }
    }
    @Test fun `support restore retains different failed bytes and makes only mutable copy writable`() {
        val root = fixture(); val id = begin(root)
        val active = mods.map { if (it.repository == "provider") it.copy(supportArtifacts = listOf("build/support.json")) else it }
        val output = root.parent.resolve("mod_source/provider/build/support.json")
        Files.createDirectories(output.parent); Files.writeString(output, "verified support")
        VerificationSession(root, active, "support-origin", false, "conversation", id).use { session ->
            val items = built(root).map { item ->
                val mod = active.single { it.repository == item.definition.repository }
                item.copy(definition = mod, supportHashes = supportArtifactHashes(root.parent.resolve("mod_source/${mod.repository}"), mod))
            }
            checkpointAffected(session, items); session.commit(items)
        }
        val snapshot = root.resolve(".worklane/testing-sessions/artifacts/support-origin/provider.jar.support/build/support.json")
        assertFalse(Files.getPosixFilePermissions(snapshot).contains(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE))
        Files.writeString(output, "failed retry evidence")
        VerificationSession(root, active, "support-restore", false, "conversation", id).use { session ->
            assertNotNull(session.reusableBuild(active[0], emptyMap()))
            assertEquals("verified support", Files.readString(output))
            assertTrue(Files.getPosixFilePermissions(output).contains(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE))
            val retained = root.resolve("generated/test-evidence/support-restore/release/retained-support/provider")
            Files.walk(retained).use { paths ->
                val files = paths.filter { Files.isRegularFile(it) }.toList()
                assertEquals(1, files.size)
                assertEquals("failed retry evidence", Files.readString(files.single()))
            }
            val key = Files.readAttributes(output, java.nio.file.attribute.BasicFileAttributes::class.java).fileKey()
            assertNotNull(session.reusableBuild(active[0], emptyMap()))
            assertEquals(key, Files.readAttributes(output, java.nio.file.attribute.BasicFileAttributes::class.java).fileKey(), "same hash avoids rewriting")
            Files.writeString(output, "mutable gate rerun")
            assertEquals("verified support", Files.readString(snapshot))
            assertFalse(Files.getPosixFilePermissions(snapshot).contains(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE))
        }
    }

    @Test fun `scoped pack contracts invalidate provider closure but unrelated prose does not`() {
        val root = fixture(); val id = begin(root)
        val active = mods.map { if (it.repository == "provider") it.copy(verificationInputs = listOf("contract.txt")) else it }
        val sharedInputs = mapper.readValue<ActiveModManifest>(Path.of("gradle/active-custom-mods.json").toFile()).verificationInputs
        fun attempt(run: String): Set<String> = VerificationSession(root, active, run, false,
            "conversation", id, sharedInputs = sharedInputs).use { session ->
            val items = built(root).map { it.copy(definition = active.single { mod -> mod.repository == it.definition.repository }) }
            checkpointAffected(session, items)
            session.commit(items)
            session.affected
        }
        assertEquals(active.map { it.repository }.toSet(), attempt("scoped-initial"))
        Files.writeString(root.resolve("unrelated.md"), "prose change")
        Files.writeString(root.resolve("dist.sh"), "packaging-only change")
        val harness = root.resolve("src/main/kotlin/com/bettercontent/tests/Harness.kt")
        Files.createDirectories(harness.parent); Files.writeString(harness, "runtime harness-only change")
        Files.createDirectories(root.resolve("gradle"))
        Files.writeString(root.resolve("gradle/active-custom-mods.json"), "other provider declaration changed")
        assertTrue(attempt("scoped-prose").isEmpty())
        Files.writeString(root.resolve("contract.txt"), "changed relevant contract")
        assertEquals(setOf("provider", "consumer"), attempt("scoped-contract"))
    }

    @Test fun `fresh epoch does not resurrect historical checkpoints after interrupted rebuild all`() {
        val root = fixture(); val id = begin(root); verify(root, id)
        VerificationSession(root, mods, "fresh-epoch", true, "conversation", id).use { session ->
            val items = built(root)
            session.checkpoint(items.first(), emptyMap())
        }
        VerificationSession(root, mods, "resume-epoch", false, "conversation", id).use { session ->
            assertEquals(setOf("consumer", "independent"), session.affected)
            val items = built(root)
            checkpointAffected(session, items)
            session.commit(items)
        }
        val receipt: VerificationReceipt = mapper.readValue(root.resolve(".worklane/testing-sessions/$id.json").toFile())
        assertEquals("fresh-epoch", receipt.mods.getValue("provider").verification_id)
        assertEquals("resume-epoch", receipt.mods.getValue("consumer").verification_id)
    }

    @Test fun `verification only contract reruns gates without unconditional clean and preserves exact build origin`() {
        val root = fixture(); val id = begin(root)
        val active = mods.map { if (it.repository == "provider") it.copy(
            verificationInputs = listOf("contract.txt"), buildInputs = emptyList()) else it }
        fun items() = built(root).map { item -> item.copy(definition = active.single { it.repository == item.definition.repository }) }
        VerificationSession(root, active, "original-build", false, "conversation", id,
            sharedInputs = listOf("gate.txt")).use { session ->
            val initial = items(); checkpointAffected(session, initial); session.commit(initial)
        }
        Files.writeString(root.resolve("contract.txt"), "verification-only changed contract")
        VerificationSession(root, active, "new-verification", false, "conversation", id,
            sharedInputs = listOf("gate.txt")).use { session ->
            assertEquals(setOf("provider", "consumer"), session.affected)
            val provider = requireNotNull(session.reusableBuild(active[0], emptyMap()))
            assertEquals("original-build", provider.buildId)
            assertEquals(root.resolve(".worklane/testing-sessions/artifacts/original-build/provider.jar"), provider.jar)
            val qualified = root.resolve("generated/test-evidence/new-verification/release/staged-jars/provider.jar")
            assertFalse(Files.exists(qualified), "build-only restoration must not publish qualified bytes")
            val repository = root.parent.resolve("mod_source/provider")
            assertFalse("clean" in providerVerificationCommand(repository, active[0].tasks, true))
            assertTrue(providerVerificationCommand(repository, active[0].tasks, true).containsAll(active[0].tasks))
            assertTrue("clean" in providerVerificationCommand(repository, active[0].tasks, false))
            assertThrows(IllegalArgumentException::class.java) {
                session.checkpoint(provider.copy(mode = "build-reused-verified", sha256 = "0".repeat(64)), emptyMap())
            }
            assertThrows(IllegalArgumentException::class.java) {
                session.checkpoint(provider.copy(mode = "build-reused-verified", buildId = "forged-newer-origin"), emptyMap())
            }
            val freshlyVerified = provider.copy(mode = "build-reused-verified")
            session.checkpoint(freshlyVerified, emptyMap())
            assertFalse(Files.exists(qualified), "checkpoint storage is not global qualified publication")
            Files.createDirectories(qualified.parent)
            ProviderInputs.publish(freshlyVerified.jar, qualified, freshlyVerified.sha256, strictGate = {})
            val consumer = requireNotNull(session.reusableBuild(active[1], mapOf("provider" to provider.sha256)))
                .copy(mode = "build-reused-verified")
            session.checkpoint(consumer, mapOf("provider" to provider.sha256))
            val independent = requireNotNull(session.reusable(active[2]))
            session.commit(listOf(freshlyVerified, consumer, independent))
        }
        val receipt: VerificationReceipt = mapper.readValue(root.resolve(".worklane/testing-sessions/$id.json").toFile())
        assertEquals("new-verification", receipt.checkpoints.getValue("provider").verification_id)
        assertEquals("original-build", receipt.checkpoints.getValue("provider").build_id)
        Files.writeString(root.parent.resolve("mod_source/provider/source.txt"), "production source changed")
        VerificationSession(root, active, "production-change", false, "conversation", id,
            sharedInputs = listOf("gate.txt")).use { session ->
            assertNull(session.reusableBuild(active[0], emptyMap()))
        }
    }

    @Test fun `build contract and resolved toolchain identity changes invalidate build reuse`() {
        val root = fixture(); val id = begin(root)
        val active = mods.map { if (it.repository == "provider") it.copy(buildInputs = listOf("contract.txt")) else it }
        VerificationSession(root, active, "build-input-origin", false, "conversation", id).use { session ->
            val items = built(root).map { item -> item.copy(definition = active.single { it.repository == item.definition.repository }) }
            checkpointAffected(session, items); session.commit(items)
        }
        Files.writeString(root.resolve("contract.txt"), "production build input changed")
        VerificationSession(root, active, "build-input-change", false, "conversation", id).use { session ->
            assertNull(session.reusableBuild(active[0], emptyMap()))
        }
        Files.writeString(root.resolve("contract.txt"), "authored contract\n")
        val oldVersion = System.getProperty("java.version")
        try {
            System.setProperty("java.version", "synthetic-toolchain-change")
            VerificationSession(root, active, "toolchain-change", false, "conversation", id).use { session ->
                assertNull(session.reusableBuild(active[0], emptyMap()))
            }
        } finally { System.setProperty("java.version", oldVersion) }
    }

    @Test fun `qualified restore publishes exact immutable bytes without overwrite or symlink following`() {
        val root = fixture(); val id = begin(root); verify(root, id)
        VerificationSession(root, mods, "atomic-restore", false, "conversation", id).use { session ->
            val provider = requireNotNull(session.reusable(mods[0]))
            assertEquals(root.resolve("generated/test-evidence/atomic-restore/release/staged-jars/provider.jar").toAbsolutePath(), provider.jar)
            assertEquals(provider.sha256, Hashes.sha256(provider.jar))
            assertThrows(IllegalArgumentException::class.java) { session.reusable(mods[0]) }
            assertEquals(provider.sha256, Hashes.sha256(provider.jar))
            val blocked = provider.jar.resolveSibling("consumer.jar")
            Files.writeString(blocked, "existing final")
            assertThrows(IllegalArgumentException::class.java) { session.reusable(mods[1]) }
            assertEquals("existing final", Files.readString(blocked))
            val linked = provider.jar.resolveSibling("independent.jar")
            Files.createSymbolicLink(linked, provider.jar)
            assertThrows(IllegalArgumentException::class.java) { session.reusable(mods[2]) }
            assertTrue(Files.isSymbolicLink(linked))
            Files.list(provider.jar.parent).use { paths ->
                assertEquals(setOf("provider.jar", "consumer.jar", "independent.jar"),
                    paths.filter { it.fileName.toString().endsWith(".jar") }.map { it.fileName.toString() }.toList().toSet())
            }
        }
    }

    @Test fun `checkpoint publication is qualified immutable idempotent and preserves input view provenance`() {
        val root = fixture(); val id = begin(root)
        val digest = "a".repeat(64)
        VerificationSession(root, mods, "checkpoint-atomic", false, "conversation", id).use { session ->
            val items = built(root).map { it.copy(inputViewSha256 = digest) }
            checkpointAffected(session, items)
            session.checkpoint(items[0], emptyMap()) // Exact current qualified entry is idempotent.
            val checkpoint = root.resolve(".worklane/testing-sessions/artifacts/checkpoint-atomic/provider.jar")
            assertEquals(items[0].sha256, Hashes.sha256(checkpoint))
            assertThrows(IllegalArgumentException::class.java) {
                session.checkpoint(items[0].copy(inputViewSha256 = "b".repeat(64)), emptyMap())
            }
            session.commit(items)
        }
        val receipt: VerificationReceipt = mapper.readValue(root.resolve(".worklane/testing-sessions/$id.json").toFile())
        assertEquals(digest, receipt.checkpoints.getValue("provider").input_view_sha256)
        assertEquals(digest, receipt.mods.getValue("provider").input_view_sha256)
        VerificationSession(root, mods, "digest-restore", false, "conversation", id).use { session ->
            assertNull(session.reusable(mods[0], "b".repeat(64)))
            assertNull(session.reusableBuild(mods[0], emptyMap(), "b".repeat(64)))
            assertFalse(Files.exists(root.resolve("generated/test-evidence/digest-restore/release/staged-jars/provider.jar")))
            val restored = requireNotNull(session.reusable(mods[0], digest))
            assertEquals(digest, restored.inputViewSha256)
            assertEquals(digest, requireNotNull(session.reusableBuild(mods[0], emptyMap(), digest)).inputViewSha256)
            assertThrows(IllegalArgumentException::class.java) { session.requireReusable(restored.copy(inputViewSha256 = null)) }
        }
    }

    @Test fun `input view admission promotes dependent closure durably without changing epoch or unrelated checkpoints`() {
        val root = fixture(); val id = begin(root)
        val digest = "a".repeat(64)
        VerificationSession(root, mods, "admission-origin", false, "conversation", id).use { session ->
            val items = built(root).map { it.copy(inputViewSha256 = digest) }
            checkpointAffected(session, items); session.commit(items)
        }
        val path = root.resolve(".worklane/testing-sessions/$id.json")
        val original: VerificationReceipt = mapper.readValue(path.toFile())
        mapper.writeValue(path.toFile(), original.copy(reuse_history = false))
        VerificationSession(root, mods, "admission-new", false, "conversation", id).use { session ->
            assertTrue(session.affected.isEmpty())
            val before: VerificationReceipt = mapper.readValue(path.toFile())
            session.admitInputView(mods[0], digest)
            assertEquals(before, mapper.readValue<VerificationReceipt>(path.toFile()))
            session.admitInputView(mods[0], "b".repeat(64))
            assertEquals(setOf("provider", "consumer"), session.affected)
            val after: VerificationReceipt = mapper.readValue(path.toFile())
            assertEquals(setOf("independent"), after.checkpoints.keys)
            assertEquals(before.checkpoints.getValue("independent"), after.checkpoints.getValue("independent"))
            assertEquals(before.generation, after.generation)
            assertEquals(before.session_id, after.session_id)
            assertEquals(before.owner, after.owner)
            assertEquals(before.active_run, after.active_run)
            assertEquals(before.baseline, after.baseline)
            assertEquals(before.reuse_history, after.reuse_history)
            assertEquals(before.mods, after.mods)
            assertTrue(after.invalidations.any { it.startsWith("input view changed: provider") })
            assertNull(session.reusable(mods[0], "b".repeat(64)))
            assertNull(session.reusableBuild(mods[0], emptyMap(), "b".repeat(64)))
            assertNotNull(session.reusableBuild(mods[0], emptyMap(), digest), "build candidates survive admission")
            session.admitInputView(mods[1], "c".repeat(64)) // Already-affected nodes do not mutate the receipt.
            assertEquals(after, mapper.readValue<VerificationReceipt>(path.toFile()))
            assertNotNull(session.reusable(mods[2], digest))
        }
        VerificationSession(root, mods, "admission-resume", false, "conversation", id).use { session ->
            assertEquals(setOf("provider", "consumer"), session.affected)
        }
    }

    @Test fun `missing legacy input view promotes instead of authorizing unchanged bundled bytes`() {
        val root = fixture(); val id = begin(root); verify(root, id)
        VerificationSession(root, mods, "legacy-view-admit", false, "conversation", id).use { session ->
            assertTrue(session.affected.isEmpty())
            session.admitInputView(mods[0], "a".repeat(64))
            assertEquals(setOf("provider", "consumer"), session.affected)
            assertNull(session.reusable(mods[0]))
            assertNull(session.reusableBuild(mods[0], emptyMap(), "a".repeat(64)))
        }
    }

    @Test fun `unowned existing checkpoint final is never overwritten or adopted`() {
        val root = fixture(); val id = begin(root)
        VerificationSession(root, mods, "collision", false, "conversation", id).use { session ->
            val items = built(root)
            val target = root.resolve(".worklane/testing-sessions/artifacts/collision/provider.jar")
            Files.createDirectories(target.parent)
            Files.writeString(target, "collision")
            assertThrows(IllegalArgumentException::class.java) { session.checkpoint(items[0], emptyMap()) }
            assertEquals("collision", Files.readString(target))
            val receipt: VerificationReceipt = mapper.readValue(root.resolve(".worklane/testing-sessions/$id.json").toFile())
            assertFalse("provider" in receipt.checkpoints)
        }
    }

    @Test fun `legacy receipts are preserved without promotion`() {
        val root = fixture(); val id = begin(root)
        val path = root.resolve(".worklane/testing-sessions/$id.json")
        val receipt: VerificationReceipt = mapper.readValue(path.toFile())
        mapper.writeValue(path.toFile(), receipt.copy(schema = "bc.testing_session.v1"))
        val before = Files.readAllBytes(path)
        assertThrows(IllegalArgumentException::class.java) {
            VerificationSession(root, mods, "legacy", false, "conversation", id)
        }
        assertArrayEquals(before, Files.readAllBytes(path))
    }

    @Test fun `cycles and unknown providers cannot establish a baseline`() {
        assertThrows(IllegalArgumentException::class.java) {
            affectedProviders(listOf(mods[0].copy(dependsOn = listOf("consumer")), mods[1]), emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            affectedProviders(listOf(mods[0].copy(dependsOn = listOf("absent"))), emptySet())
        }
    }
}
