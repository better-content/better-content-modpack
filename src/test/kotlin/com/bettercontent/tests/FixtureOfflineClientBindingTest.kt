package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import java.nio.file.Path
import java.util.UUID

@Tag("fast")
class FixtureOfflineClientBindingTest {
    private val nonce = UUID.fromString("2a1c3406-1041-4f5c-aa00-222222222222")
    private val player = UUID.nameUUIDFromBytes("OfflinePlayer:SmokeClient1".toByteArray(Charsets.UTF_8))
    private fun binding(client: Path = Path.of("/home/dev/probe/fixture/client-1"),
        bridge: Path = Path.of("/home/dev/probe/fixture/runtime-diagnostics")) =
        FixtureOfflineClientBinding("20261025T234000Z-123", nonce, "SmokeClient1", player, client, bridge)

    @Test fun `properties bind the real offline identity and contain no token`() {
        val value = binding()
        assertTrue(value.properties.contains("-Dbc.pack_test.offline_client.uuid=$player"))
        assertTrue(value.properties.contains("-Dbc.pack_test.offline_client.nonce=$nonce"))
        assertTrue(value.properties.contains("-Dbc.pack_test.offline_client.root=${value.client}"))
        assertFalse(value.properties.contains("accessToken"))
        assertFalse(value.properties.contains("access_token"))
        assertEquals(value.client.parent, value.bridge.parent)
    }
    @Test fun `cross-fixture paths are not launcher inputs`() {
        for (path in listOf(Path.of("relative/fixture/client-1"), Path.of("/home/dev/probe/normal/client-1"),
            Path.of("/home/dev/probe/fixture/server"), Path.of("/home/dev/probe/fixture/client-1/.."))) {
            assertThrows(IllegalArgumentException::class.java) { binding(client = path) }
        }
        assertThrows(IllegalArgumentException::class.java) { binding(bridge = Path.of("/home/dev/other/fixture/runtime-diagnostics")) }
        assertThrows(IllegalArgumentException::class.java) { binding(bridge = Path.of("/home/dev/probe/fixture/not-the-bridge")) }
    }
    @Test fun `native readiness and onboarding request are nonce and player bound`() {
        val value = binding()
        assertEquals("${value.runId}\n$nonce\n$player\n", value.onboardingPayload)
        val marker = "BC_DEDICATED_ONBOARDING_READY run=${value.runId} nonce=$nonce uuid=$player mode=survival dimension=minecraft:overworld chunk_loaded=true submitted=true"
        assertTrue(value.readyMarker.matches(marker))
        for (invalid in listOf(marker.replace(nonce.toString(), UUID.randomUUID().toString()),
            marker.replace(player.toString(), UUID.randomUUID().toString()), marker.replace("mode=survival", "mode=spectator"),
            marker.replace("submitted=true", "submitted=false"), marker.replace("chunk_loaded=true", "chunk_loaded=false"))) {
            assertFalse(value.readyMarker.matches(invalid), invalid)
        }
        val offline = "BC_OFFLINE_CLIENT_BOUND run=${value.runId} nonce=$nonce uuid=$player root=${value.client} empty_token=true online_request=false"
        assertTrue(value.offlineMarker.matches(offline))
        assertFalse(value.offlineMarker.matches(offline.replace("online_request=false", "online_request=true")))
    }
}
