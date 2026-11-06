package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SessionLifecycleTest {
    private class Clock : SessionClock {
        var now = 0L
        override fun nanos() = now
        override fun sleep(duration: Duration) { now += duration.toNanos() }
    }
    private class Driver(val clock: Clock, val failAt: String? = null) : SessionDriver {
        val calls = mutableListOf<String>()
        var verifiedAt = -1L
        var disconnectedAt = -1L
        private fun action(name: String) { calls += name; check(name != failAt) { "failed $name" } }
        override fun startServer() = action("start")
        override fun awaitServerReady() { action("ready"); clock.now += Duration.ofMinutes(3).toNanos() }
        override fun connectClient() = action("connect")
        override fun awaitClientJoined() { action("joined"); clock.now += Duration.ofMinutes(2).toNanos() }
        override fun heartbeat() { action("heartbeat"); if (verifiedAt < 0) verifiedAt = clock.now }
        override fun ensureConnected() { action("connected") }
        override fun disconnectClient() { action("disconnect"); disconnectedAt = clock.now }
        override fun saveAndStopServer() = action("save-stop")
        override fun validate() = action("validate")
    }

    @Test fun smokeIsOneLifecycleWithoutFeatureScenarios() {
        val clock = Clock(); val driver = Driver(clock)
        runLifecycle(SessionRequest(SessionTier.SMOKE), driver, clock)
        assertEquals(listOf("start", "ready", "connect", "joined", "heartbeat", "connected", "disconnect", "save-stop", "validate"), driver.calls)
        assertEquals(driver.verifiedAt, driver.disconnectedAt)
    }

    @Test fun longRunRequiresInstrumentedWorldDriver() {
        val clock = Clock(); val driver = Driver(clock)
        assertThrows(IllegalStateException::class.java) {
            runLifecycle(SessionRequest(SessionTier.OVERWORLD_TELE_SOAK), driver, clock)
        }
        assertEquals(1, driver.calls.count { it == "connect" })
        assertFalse("save-stop" in driver.calls)
    }

    @Test fun lostConnectionFailsInsteadOfReconnectingOrPassing() {
        val clock = Clock(); val driver = Driver(clock, "connected")
        assertThrows(IllegalStateException::class.java) { runLifecycle(SessionRequest(SessionTier.SMOKE), driver, clock) }
        assertEquals(1, driver.calls.count { it == "connect" })
        assertFalse("validate" in driver.calls)
    }

    @Test fun heartbeatAndSaveFailuresCannotPass() {
        for (stage in listOf("heartbeat", "save-stop")) {
            val clock = Clock(); val driver = Driver(clock, stage)
            assertThrows(IllegalStateException::class.java) { runLifecycle(SessionRequest(SessionTier.SMOKE), driver, clock) }
            assertFalse("validate" in driver.calls)
        }
    }

    @Test fun runtimeDurationsAreFixed() {
        for (tier in SessionTier.entries) for (minutes in listOf(-1L, 1L, 60L))
            assertThrows(IllegalArgumentException::class.java) { SessionRequest(tier, minutes) }
        assertEquals(0L, SessionRequest(SessionTier.OVERWORLD_TELE_SOAK).minutes)
    }

    private fun zip(path: Path, entry: String = "file.txt"): Path {
        path.parent?.let(Files::createDirectories)
        ZipOutputStream(Files.newOutputStream(path)).use { it.putNextEntry(ZipEntry(entry)); it.write("packaged".toByteArray()); it.closeEntry() }
        return path
    }

    @Test fun candidatesAreExplicitAndMutationIsDetected(@TempDir root: Path) {
        val client = zip(root.resolve("c.zip")); val server = zip(root.resolve("s.zip"))
        val pair = CandidateLocator.locate(client, server)
        pair.assertUnchanged()
        Files.writeString(client, "changed")
        assertThrows(IllegalStateException::class.java) { pair.assertUnchanged() }
    }

    @Test fun defaultDiscoveryNeverPairsDifferentReleases(@TempDir root: Path) {
        zip(root.resolve("dist/a/client/better-content.zip")); zip(root.resolve("dist/b/server/better-content.zip"))
        assertThrows(IllegalArgumentException::class.java) { CandidateLocator.locate(root) }
    }

    @Test fun zipTraversalIsRejected(@TempDir root: Path) {
        val archive = zip(root.resolve("unsafe.zip"), "../escaped")
        assertThrows(IllegalArgumentException::class.java) { ZipContracts.extract(archive, root.resolve("extract")) }
        assertFalse(Files.exists(root.resolve("escaped")))
    }

    @Test fun evidenceCannotOverwriteAnEarlierResult(@TempDir root: Path) {
        val pair = CandidateLocator.locate(zip(root.resolve("c.zip")), zip(root.resolve("s.zip")))
        SessionEvidence(root, "unit-run", "smoke", pair).finish(null)
        assertThrows(java.nio.file.FileAlreadyExistsException::class.java) { SessionEvidence(root, "unit-run", "smoke", pair) }
    }

    @Test fun freshLogWindowsUseByteOffsetsAndRejectTruncation(@TempDir root: Path) {
        val log = root.resolve("server.log")
        Files.writeString(log, "old 世界 🐀 Saved the game\n")
        val offset = Files.size(log)
        assertEquals("", readLogFrom(log, offset).text)
        Files.writeString(log, "new Saved the game\n", java.nio.file.StandardOpenOption.APPEND)
        val fresh = readLogFrom(log, offset)
        assertEquals("new Saved the game\n", fresh.text)
        assertEquals(Files.size(log), fresh.nextOffset)
        Files.writeString(log, "truncated")
        assertThrows(IllegalStateException::class.java) { readLogFrom(log, fresh.nextOffset) }
    }

    @Test fun normalStopRequiresFreshCompleteSaveAndZeroExit(@TempDir root: Path) {
        val marker = "ThreadedAnvilChunkStorage: All dimensions are saved"
        val cases = listOf(
            "printf '%s\\n' '$marker'; exit 0" to true,
            "printf '%s\\n' '$marker'; exit 7" to false,
            "echo 'Saved the game'; exit 0" to false,
            "exit 0" to false,
        )
        cases.forEachIndexed { index, (finish, succeeds) ->
            val log = root.resolve("stop-$index.log")
            val script = "echo '$marker'; echo ready; read -r command; [ \"\$command\" = stop ] || exit 8; $finish"
            ManagedProcess("fake-server", listOf("bash", "-c", script), root, log).use { server ->
                server.waitForLog(Regex("ready"), Duration.ofSeconds(3), "fake readiness")
                if (succeeds) stopAndSaveServer(server, log, Duration.ofSeconds(3))
                else assertThrows(IllegalStateException::class.java) { stopAndSaveServer(server, log, Duration.ofSeconds(3)) }
            }
        }
    }

    @Test fun invalidPublicRequestsStopBeforeAcquiringTheRuntimeLock(@TempDir root: Path) {
        val repo = Path.of(System.getProperty("bc.repo.root"))
        val badRequests = listOf(
            listOf("test.main.kts", "dev"), listOf("test.main.kts", "smoke", "--minutes", "1"),
            listOf("test.main.kts", "soak", "--minutes", "0"),
            listOf("test.main.kts", "overworld-tele-soak", "--minutes", "60"),
            listOf("test.main.kts", "overworld-tele-soak", "--diagnose-from", "old/run.json"),
            listOf("test.main.kts", "smoke", "--client", "one.zip"),
            listOf("release.main.kts", "--skip-tests"), listOf("release.main.kts", "--soak-minutes", "10081"),
            listOf("release.main.kts", "--runtime-tiers", "soak"),
            listOf("release.main.kts", "--use-bundled-mods", "--skip-tests"),
        )
        badRequests.forEachIndexed { index, args ->
            val command = listOf(repo.resolve(args.first()).toString()) + args.drop(1)
            ManagedProcess("invalid-cli", command, repo, root.resolve("invalid-$index.log"),
                mapOf("BC_PACK_TEST_STATE_ROOT" to root.resolve("lock-state").toString())).use {
                assertEquals(2, it.waitForExit(Duration.ofSeconds(30)))
            }
        }
        assertFalse(Files.exists(root.resolve("lock-state")))
    }

    @Test fun materializerBoundaryChecks(@TempDir root: Path) {
        val repo = Path.of(System.getProperty("bc.repo.root"))
        Commands.run(listOf("python3", "-m", "unittest", "discover", "-s", "src/test/python", "-p", "test_materialize_client.py", "-v"), repo, root.resolve("python.log"),
            mapOf("TMPDIR" to root.toString()), Duration.ofSeconds(30))
    }

    @Test fun worldAxiomSupervisorBoundaryChecks(@TempDir root: Path) {
        val repo = Path.of(System.getProperty("bc.repo.root"))
        // Separate local shell-transaction suite, not part of the materializer's
        // 30-second budget and not a change to any runtime acceptance deadline.
        Commands.run(listOf("python3", "-m", "unittest", "discover", "-s", "src/test/python", "-p", "test_world_axiom_supervisor.py", "-v"), repo, root.resolve("supervisor-python.log"),
            mapOf("TMPDIR" to root.toString()), Duration.ofMinutes(3))
    }
}
