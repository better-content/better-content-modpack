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
            Files.writeString(source.resolve(".gitignore"), "build/\n")
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
            it.commit(artifacts())
        }
        val receipt: VerificationReceipt = mapper.readValue(root.resolve(".worklane/testing-sessions/$id.json").toFile())
        val snapshot = root.resolve(receipt.checkpoints.getValue("provider").staged_path)
            .resolveSibling("provider.jar.support/build/libs/support.jar")
        Files.writeString(snapshot, "corrupt retained support")
        VerificationSession(root, active, "corrupt-support", false, "conversation", id).use {
            assertEquals(setOf("provider", "consumer"), it.affected)
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
