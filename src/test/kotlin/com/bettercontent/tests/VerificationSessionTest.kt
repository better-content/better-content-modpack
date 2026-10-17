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
        val id = mapper.readValue<VerificationReceipt>(root.resolve(".worklane/testing-sessions")
            .toFile().listFiles()!!.single { it.extension == "json" }).active_run!!
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
        root.parent.toFile().deleteRecursively()
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
        root.parent.toFile().deleteRecursively()
    }
    @Test fun `source and provider hash invalidate consumers while independent remains reusable`() {
        val root = fixture(); val id = begin(root); verify(root, id)
        Files.writeString(root.parent.resolve("mod_source/provider/source.txt"), "changed")
        assertEquals(setOf("provider", "consumer"), verify(root, id))
        Files.writeString(root.resolve("mods/provider.jar"), "corrupt")
        assertEquals(setOf("provider", "consumer"), verify(root, id))
        Files.delete(root.resolve("mods/independent.jar"))
        VerificationSession(root, mods, "missing", false, "conversation", id).use {
            assertEquals(setOf("independent"), it.affected)
            Files.writeString(root.resolve("mods/independent.jar"), "new verified jar")
            val items = built(root); checkpointAffected(it, items); it.commit(items)
        }
        root.parent.toFile().deleteRecursively()
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
        root.parent.toFile().deleteRecursively()
    }
    @Test fun `no session is full baseline and receipt is invocation scoped`() {
        val root = fixture()
        var id = ""
        VerificationSession(root, mods, "ephemeral", false, "conversation", null).use {
            id = it.id; assertTrue(it.fullBaseline)
            val items = built(root); checkpointAffected(it, items); it.commit(items)
        }
        assertFalse(Files.exists(root.resolve(".worklane/testing-sessions/$id.json")))
        root.parent.toFile().deleteRecursively()
    }
    @Test fun `artifact mutation after reuse decision fails rather than gaining an old verification id`() {
        val root = fixture(); val id = begin(root); verify(root, id)
        VerificationSession(root, mods, "reuse-race", false, "conversation", id).use {
            assertTrue(it.affected.isEmpty())
            Files.writeString(root.resolve("mods/provider.jar"), "unverified replacement")
            assertThrows(IllegalArgumentException::class.java) { it.commit(built(root)) }
        }
        root.parent.toFile().deleteRecursively()
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
            assertEquals(setOf("provider", "consumer"), it.affected)
            Files.writeString(support, "newly verified support")
            val items = artifacts(); checkpointAffected(it, items); it.commit(items)
        }
        Files.writeString(support, "unverified replacement")
        VerificationSession(root, active, "corrupt-support", false, "conversation", id).use {
            assertEquals(setOf("provider", "consumer"), it.affected)
        }
        root.parent.toFile().deleteRecursively()
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
