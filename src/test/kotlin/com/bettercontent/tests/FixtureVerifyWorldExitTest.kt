package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.FileAlreadyExistsException
import java.util.UUID
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@Tag("fast")
class FixtureVerifyWorldExitTest {
    private fun fixture(block: (Path, Path, Path) -> Unit) {
        val owner = Path.of("build/verify-exit-test", UUID.randomUUID().toString()).toAbsolutePath()
        val root = owner.resolve("20261008T170000Z-123/singleplayer/fixture")
        val client = root.resolve("client-6")
        val bridge = root.resolve("journal-contract")
        Files.createDirectories(client.resolve("saves/DebugWorld"))
        Files.createDirectories(bridge)
        try { block(root, client, bridge) } finally { owner.toFile().deleteRecursively() }
    }
    private fun binding(root: Path, client: Path, bridge: Path) = requireNotNull(fixtureVerifyExitBinding(
        "verify", "DebugWorld", false, true, "20261008T170000Z-123", "SmokeWorld", offlineUuid("SmokeWorld"), root, client, bridge))

    @Test fun `only explicit journal integrated DebugWorld verify configures signal`() {
        fixture { root, client, bridge ->
            fun result(mode: String = "verify", world: String = "DebugWorld", dedicated: Boolean = false, journal: Boolean = true) =
                fixtureVerifyExitBinding(mode, world, dedicated, journal, "20261008T170000Z-123", "SmokeWorld", offlineUuid("SmokeWorld"), root, client, bridge)
            assertNull(result(mode = "save")); assertNull(result(world = "world")); assertNull(result(dedicated = true)); assertNull(result(journal = false))
            val valid = binding(root, client, bridge)
            assertFalse(Files.exists(valid.requestFile))
            assertEquals(UUID.nameUUIDFromBytes("OfflinePlayer:SmokeWorld".toByteArray()), valid.playerId)
            assertEquals(root.resolve("client-6/saves/DebugWorld"), valid.worldRoot)
            assertTrue(valid.properties.contains("-Dbc.pack_test.verify_exit.nonce=${valid.nonce}"))
            assertNotEquals(valid.nonce, binding(root, client, bridge).nonce)
        }
    }

    @Test fun `actual verify and loaded observations both precede CREATE NEW request`() {
        fixture { root, client, bridge ->
            val valid = binding(root, client, bridge)
            for ((verified, loaded) in listOf(false to false, true to false, false to true))
                assertThrows(IllegalArgumentException::class.java) { writeFixtureVerifyExitRequest(valid, verified, loaded) }
            assertFalse(Files.exists(valid.requestFile))
            writeFixtureVerifyExitRequest(valid, true, true)
            assertEquals(valid.payload, Files.readString(valid.requestFile))
            assertTrue(Files.size(valid.requestFile) <= 2048)
            assertThrows(FileAlreadyExistsException::class.java) { writeFixtureVerifyExitRequest(valid, true, true) }
            assertEquals(valid.payload, Files.readString(valid.requestFile))
        }
    }

    @Test fun `correlated real exit marker rejects generic stale and mismatched identities`() {
        fixture { root, client, bridge ->
            val valid = binding(root, client, bridge)
            val marker = "BC_DEBUG_WORLD_EXITED mode=verify run_id=${valid.runId} nonce=${valid.nonce} player=${valid.playerName} " +
                "player_uuid=${valid.playerId} world_root=${valid.worldRoot}"
            assertTrue(valid.exitMarker.containsMatchIn("[INFO] $marker\n"))
            assertFalse(valid.exitMarker.containsMatchIn("BC_DEBUG_WORLD_EXITED\n"))
            assertFalse(valid.exitMarker.containsMatchIn(marker.replace(valid.nonce.toString(), UUID.randomUUID().toString())))
            assertFalse(valid.exitMarker.containsMatchIn(marker.replace("SmokeWorld", "OtherPlayer")))
            assertFalse(valid.exitMarker.containsMatchIn(marker + "-other"))
        }
    }

    @Test fun `canonical owned root and nonsymlink directory bindings are mandatory`() {
        fixture { root, client, bridge ->
            val link = root.resolve("linked-client")
            Files.createSymbolicLink(link, client)
            assertThrows(IllegalArgumentException::class.java) { binding(root, link, bridge) }
            assertThrows(IllegalArgumentException::class.java) { binding(root.resolve(".."), client, bridge) }
            val other = root.resolve("other-contract"); Files.createDirectory(other)
            assertThrows(IllegalArgumentException::class.java) { binding(root, client, other) }
            val valid = binding(root, client, bridge)
            val external = root.resolve("external-request"); Files.writeString(external, "untouched")
            Files.createSymbolicLink(valid.requestFile, external)
            assertThrows(FileAlreadyExistsException::class.java) { writeFixtureVerifyExitRequest(valid, true, true) }
            assertEquals("untouched", Files.readString(external))
        }
    }

    @Test fun `source lifecycle guard keeps observations timeouts and budgets independent`() {
        val source = Files.readString(Path.of("src/test/kotlin/com/bettercontent/tests/RuntimeFixtures.kt"))
        val checkpoint = source.substring(source.indexOf("fun waitJournalCheckpoint"), source.indexOf("fun requestNormalVerifyExit"))
        assertTrue(checkpoint.indexOf("validateCheckpoint") < checkpoint.indexOf("journalVerifyObserved = true"))
        val request = source.substring(source.indexOf("fun requestNormalVerifyExit"), source.indexOf("fun waitForWorldProbe"))
        assertTrue(request.contains("journalVerifyObserved, verifyLoadedObserved"))
        assertFalse(request.contains("saveEverything")); assertFalse(request.contains("level.dat")); assertFalse(request.contains("close()"))
        assertTrue(source.contains("Duration.ofMinutes(10), \"singleplayer world probe"))
        assertTrue(source.contains("binding.exitMarker"))
        assertTrue(source.contains("native_exit_observed\" to false"))
        assertTrue(source.contains("requires_independent_post_exit_checks"))
        assertTrue(source.contains("LP_NUM_THREADS\" to \"2\""))
        assertTrue(source.contains("-XX:ActiveProcessorCount=8"))
        assertTrue(source.contains("private const val DEFAULT_CLIENT_JVM_ARGS = \"-Xms2G -Xmx12G\""))
    }
}
