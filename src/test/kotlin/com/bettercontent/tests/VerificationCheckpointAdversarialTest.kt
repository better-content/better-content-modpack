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
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Independent synthetic repositories: no Minecraft, log adoption, or production providers. */
@Tag("fast")
class VerificationCheckpointAdversarialTest {
    private val mapper = jacksonObjectMapper()
    private val mods = listOf(
        ActiveMod("provider", "provider", "provider.jar", listOf("verifyFull", "stageRuntimeJar")),
        ActiveMod("consumer", "consumer", "consumer.jar", listOf("verifyFull", "stageRuntimeJar"), listOf("provider")),
        ActiveMod("independent", "independent", "independent.jar", listOf("verifyFull", "stageRuntimeJar")))

    private inner class Fixture : AutoCloseable {
        val root: Path = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "checkpoint-adversarial-").resolve("pack")
        val id = UUID.randomUUID().toString().replace("-", "")
        var activeRun: String = "unstarted"
        val receiptPath get() = root.resolve(".worklane/testing-sessions/$id.json")
        init {
            Files.createDirectories(root.resolve("mods"))
            repository(root)
            mods.forEach { mod ->
                repository(source(mod))
                Files.writeString(root.resolve("mods/${mod.artifact}"), mod.repository)
            }
            Files.createDirectories(receiptPath.parent)
            write(VerificationReceipt(session_id = id, owner = "owner"))
        }
        fun source(mod: ActiveMod) = root.parent.resolve("mod_source/${mod.repository}")
        fun read(): VerificationReceipt = mapper.readValue(receiptPath.toFile())
        fun write(value: VerificationReceipt) = mapper.writeValue(receiptPath.toFile(), value)
        fun session(run: String, force: Boolean = false, owner: String = "owner", definitions: List<ActiveMod> = mods): VerificationSession {
            activeRun = run
            return VerificationSession(root, definitions, run, force, owner, id)
        }
        fun item(mod: ActiveMod = mods[0]): BuiltMod {
            val path = root.resolve("generated/test-evidence/$activeRun/release/staged-jars/${mod.artifact}")
            Files.createDirectories(path.parent)
            if (!Files.exists(path)) Files.copy(root.resolve("mods/${mod.artifact}"), path)
            val commit = BoundedCommand.capture(source(mod), listOf("git", "rev-parse", "HEAD"), Duration.ofSeconds(10)).trim()
            return BuiltMod(mod, commit, path, Hashes.sha256(path), "rebuilt",
                supportArtifactHashes(source(mod), mod))
        }
        fun checkpointAll(session: VerificationSession) {
            mods.forEach { mod -> session.checkpoint(item(mod), mod.dependsOn.associateWith { dependency -> item(mods.first { it.repository == dependency }).sha256 }) }
        }
        fun partial() { session("origin").use { it.checkpoint(item(), emptyMap()) } }
        override fun close() { /* Preserve synthetic receipts, snapshots and failure state for inspection. */ }
    }
    private fun repository(path: Path) {
        Files.createDirectories(path)
        fun git(vararg args: String) = BoundedCommand.capture(path, listOf("git") + args, Duration.ofSeconds(10))
        git("init", "-q")
        Files.writeString(path.resolve("source.txt"), "source\n")
        Files.writeString(path.resolve(".gitignore"), "build/\n.worklane/\ngenerated/\nmods/\n")
        git("add", "source.txt", ".gitignore")
        git("-c", "user.email=test@example.invalid", "-c", "user.name=Test", "commit", "-qm", "fixture")
    }

    @Test fun `foreign owner cannot alter receipt or snapshot and helper preserves origin`() = Fixture().use { f ->
        f.partial()
        val before = Files.readAllBytes(f.receiptPath)
        val snapshot = f.root.resolve(f.read().checkpoints.getValue("provider").staged_path)
        val snapshotHash = Hashes.sha256(snapshot)
        assertThrows(IllegalArgumentException::class.java) { f.session("foreign", owner = "stranger") }
        assertArrayEquals(before, Files.readAllBytes(f.receiptPath))
        assertEquals(snapshotHash, Hashes.sha256(snapshot))
        f.write(f.read().copy(helpers = setOf("helper")))
        f.session("helper-run", owner = "helper").use { session ->
            assertNotNull(session.reusable(mods[0]))
            assertEquals("owner", f.read().owner)
            assertEquals("origin", f.read().checkpoints.getValue("provider").verification_id)
        }
        assertEquals(snapshotHash, Hashes.sha256(snapshot))
    }

    @Test fun `partial verification survives failure but cannot authorize incomplete deployment`() = Fixture().use { f ->
        f.partial()
        f.session("resume").use { session ->
            assertEquals(setOf("consumer", "independent"), session.affected)
            val reused = requireNotNull(session.reusable(mods[0]))
            assertThrows(IllegalArgumentException::class.java) { session.commit(listOf(reused)) }
        }
        assertEquals("failed", f.read().baseline)
        assertEquals("origin", f.read().checkpoints.getValue("provider").verification_id)
    }

    @Test fun `completed resumed baseline preserves the original provider verification id`() = Fixture().use { f ->
        f.partial()
        f.session("resume").use { s ->
            val reused = requireNotNull(s.reusable(mods[0]))
            s.checkpoint(f.item(mods[1]), mapOf("provider" to reused.sha256))
            s.checkpoint(f.item(mods[2]), emptyMap())
            s.commit(listOf(reused, f.item(mods[1]), f.item(mods[2])))
        }
        assertEquals("complete", f.read().baseline)
        assertEquals("origin", f.read().mods.getValue("provider").verification_id)
        assertEquals("resume", f.read().mods.getValue("consumer").verification_id)
        assertEquals("resume", f.read().mods.getValue("independent").verification_id)
    }

    @Test fun `force rebuild discards old complete providers across a partial failure`() = Fixture().use { f ->
        f.session("old").use { s -> f.checkpointAll(s); s.commit(mods.map(f::item)) }
        f.session("forced", force = true).use { it.checkpoint(f.item(), emptyMap()) }
        f.session("resumed").use { s ->
            assertEquals(setOf("consumer", "independent"), s.affected)
            assertNotNull(s.reusable(mods[0]))
            assertNull(s.reusable(mods[1]))
            assertNull(s.reusable(mods[2]))
        }
    }

    @Test fun `legacy failed receipt never blesses leftover jars`() = Fixture().use { f ->
        f.write(f.read().copy(baseline = "failed"))
        f.session("resume").use { s ->
            assertEquals(mods.map { it.repository }.toSet(), s.affected)
            mods.forEach { assertNull(s.reusable(it)) }
        }
    }

    @Test fun `snapshot and original artifact tampering cannot retain an old verification identity`() {
        Fixture().use { f ->
            f.partial()
            Files.writeString(f.root.resolve(f.read().checkpoints.getValue("provider").staged_path), "tampered")
            f.session("resume").use { s -> assertTrue("provider" in s.affected); assertNull(s.reusable(mods[0])) }
        }
        Fixture().use { f ->
            f.session("active").use { s ->
                val original = f.item()
                Files.writeString(original.jar, "changed after build")
                assertThrows(IllegalArgumentException::class.java) { s.checkpoint(original, emptyMap()) }
                assertTrue(f.read().checkpoints.isEmpty())
            }
        }
    }

    @Test fun `post staging tamper cannot commit with original verification identity`() = Fixture().use { f ->
        f.partial()
        f.session("resume").use { s ->
            val reused = requireNotNull(s.reusable(mods[0]))
            Files.writeString(reused.jar, "changed after checkpoint staging")
            assertThrows(IllegalArgumentException::class.java) { s.requireReusable(reused) }
            assertEquals("origin", f.read().checkpoints.getValue("provider").verification_id)
            assertNotEquals("complete", f.read().baseline)
        }
    }

    @Test fun `source shared and task changes invalidate checkpoints`() {
        for (change in listOf("source", "shared", "tasks")) Fixture().use { f ->
            f.partial()
            if (change == "source") Files.writeString(f.source(mods[0]).resolve("source.txt"), "changed")
            if (change == "shared") Files.writeString(f.root.resolve("source.txt"), "changed")
            val definitions = if (change == "tasks") mods.map { if (it.repository == "provider") it.copy(tasks = it.tasks + "extraGate") else it } else mods
            f.session("resume", definitions = definitions).use { s ->
                assertTrue(s.affected.containsAll(setOf("provider", "consumer")), change)
                assertNull(s.reusable(definitions[0]), change)
            }
        }
    }

    @Test fun `wrong dependency hash cannot checkpoint and dependency byte tampering invalidates closure`() = Fixture().use { f ->
        f.session("origin").use { s ->
            s.checkpoint(f.item(), emptyMap())
            assertThrows(IllegalArgumentException::class.java) { s.checkpoint(f.item(mods[1]), mapOf("provider" to "0".repeat(64))) }
            s.checkpoint(f.item(mods[1]), mapOf("provider" to f.item().sha256))
        }
        Files.writeString(f.root.resolve(f.read().checkpoints.getValue("provider").staged_path), "dependency tampered")
        f.session("resume").use { s ->
            assertTrue(s.affected.containsAll(setOf("provider", "consumer")))
            assertNull(s.reusable(mods[1]))
        }
    }

    @Test fun `dependency bytes changed within active run cannot authorize consumer checkpoint`() = Fixture().use { f ->
        f.session("origin").use { s ->
            val provider = f.item()
            s.checkpoint(provider, emptyMap())
            val snapshot = f.root.resolve(f.read().checkpoints.getValue("provider").staged_path)
            Files.writeString(snapshot, "changed dependency bytes")
            assertThrows(IllegalArgumentException::class.java) {
                s.checkpoint(f.item(mods[1]), mapOf("provider" to provider.sha256))
            }
            assertFalse("consumer" in f.read().checkpoints)
        }
    }

    @Test fun `support artifact tampering invalidates checkpoint`() = Fixture().use { f ->
        val provider = mods[0].copy(supportArtifacts = listOf("build/libs/support.jar"))
        val definitions = listOf(provider) + mods.drop(1)
        val support = f.source(provider).resolve("build/libs/support.jar")
        Files.createDirectories(support.parent); Files.writeString(support, "verified support")
        f.session("origin", definitions = definitions).use { it.checkpoint(f.item(provider), emptyMap()) }
        val snapshot = f.root.resolve(f.read().checkpoints.getValue("provider").staged_path)
            .resolveSibling("provider.jar.support/build/libs/support.jar")
        Files.writeString(snapshot, "tampered support")
        f.session("resume", definitions = definitions).use { s ->
            assertTrue(s.affected.containsAll(setOf("provider", "consumer")))
            assertNull(s.reusable(provider))
        }
    }

    @Test fun `receipt path escape and symlink ancestor never supply a reusable artifact`() {
        for (escape in listOf("../outside.jar", "/home/dev/.tmp/outside-checkpoint.jar")) Fixture().use { f ->
            f.partial()
            val receipt = f.read()
            f.write(receipt.copy(checkpoints = receipt.checkpoints.mapValues { (_, c) -> c.copy(staged_path = escape) }))
            f.session("resume").use { s -> assertTrue("provider" in s.affected); assertNull(s.reusable(mods[0])) }
        }
        Fixture().use { f ->
            f.partial()
            val snapshot = f.root.resolve(f.read().checkpoints.getValue("provider").staged_path)
            val moved = snapshot.parent.resolveSibling("moved-snapshots")
            Files.move(snapshot.parent, moved)
            Files.createSymbolicLink(snapshot.parent, moved)
            f.session("resume").use { s -> assertTrue("provider" in s.affected); assertNull(s.reusable(mods[0])) }
        }
    }

    @Test fun `current staged dependency corruption fails even when immutable checkpoint is intact`() = Fixture().use { f ->
        f.session("origin").use { s ->
            val provider = f.item()
            s.checkpoint(provider, emptyMap())
            val immutable = f.root.resolve(f.read().checkpoints.getValue("provider").staged_path)
            val expected = Hashes.sha256(immutable)
            Files.writeString(provider.jar, "changed stage actually consumed by compiler")
            assertEquals(expected, Hashes.sha256(immutable))
            assertThrows(IllegalArgumentException::class.java) {
                s.checkpoint(f.item(mods[1]), mapOf("provider" to provider.sha256))
            }
            assertFalse("consumer" in f.read().checkpoints)
        }
    }

    @Test fun `predeployment validates complete graph and returns original verification ids`() = Fixture().use { f ->
        f.partial()
        f.session("resume").use { s ->
            val provider = requireNotNull(s.reusable(mods[0]))
            s.checkpoint(f.item(mods[1]), mapOf("provider" to provider.sha256))
            s.checkpoint(f.item(mods[2]), emptyMap())
            val built = listOf(provider, f.item(mods[1]), f.item(mods[2]))
            assertEquals(mapOf("provider" to "origin", "consumer" to "resume", "independent" to "resume"), s.validateComplete(built))
            assertThrows(IllegalArgumentException::class.java) { s.validateComplete(built.dropLast(1)) }
            assertThrows(IllegalArgumentException::class.java) {
                s.validateComplete(built.map { if (it.definition.repository == "consumer") it.copy(definition = it.definition.copy(tasks = listOf("wrongGate"))) else it })
            }
            assertNotEquals("complete", f.read().baseline)
        }
    }

    @Test fun `predeployment refuses missing checkpoint candidate before deployment`() = Fixture().use { f ->
        f.session("origin").use { s ->
            f.checkpointAll(s)
            val built = mods.map(f::item)
            val originalDeployed = Hashes.sha256(f.root.resolve("mods/provider.jar"))
            Files.delete(f.root.resolve(f.read().checkpoints.getValue("provider").staged_path))
            assertThrows(IllegalArgumentException::class.java) { s.validateComplete(built) }
            assertEquals(originalDeployed, Hashes.sha256(f.root.resolve("mods/provider.jar")))
            assertNotEquals("complete", f.read().baseline)
        }
    }

    @Test fun `predeployment rejects forged checkpoint origin instead of propagating its id`() = Fixture().use { f ->
        f.session("origin").use { s ->
            f.checkpointAll(s)
            val built = mods.map(f::item)
            val r = f.read()
            f.write(r.copy(checkpoints = r.checkpoints + ("provider" to r.checkpoints.getValue("provider").copy(verification_id = "forged"))))
            assertThrows(IllegalArgumentException::class.java) { s.validateComplete(built) }
            assertNotEquals("complete", f.read().baseline)
        }
    }

    @Test fun `racing successful checkpoints retain both records and origins`() = Fixture().use { f ->
        f.session("parallel").use { session ->
            val executor = Executors.newFixedThreadPool(2)
            val ready = CountDownLatch(2); val start = CountDownLatch(1)
            try {
                val futures = listOf(mods[0], mods[2]).map { mod -> executor.submit(Callable {
                    ready.countDown(); check(start.await(10, TimeUnit.SECONDS))
                    session.checkpoint(f.item(mod), emptyMap())
                }) }
                assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown()
                futures.forEach { it.get(30, TimeUnit.SECONDS) }
                assertEquals(setOf("provider", "independent"), f.read().checkpoints.keys)
                assertTrue(f.read().checkpoints.values.all { it.verification_id == "parallel" })
            } finally { start.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)) }
        }
    }
}
