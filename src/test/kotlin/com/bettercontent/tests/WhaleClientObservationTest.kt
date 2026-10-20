package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.UUID

@Tag("fast")
class WhaleClientObservationTest {
    private val mapper = jacksonObjectMapper()
    private val binding = WhaleClientBinding("20261020T100000Z-123", UUID.randomUUID(), UUID.randomUUID(),
        UUID.randomUUID(), "a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64))
    private fun receipt() = mapper.createObjectNode().apply {
        put("schema", "bc.whale_client_observation.v1"); put("run", binding.run)
        put("nonce", binding.nonce.toString()); put("player_uuid", binding.player.toString())
        put("entity_uuid", binding.entity.toString()); put("entity_type", "untamedwilds:baleen_whale")
        put("dimension", "minecraft:overworld"); put("join_tick", 100); put("first_tick", 101); put("last_tick", 102)
        put("client_sha256", binding.clientHash); put("server_sha256", binding.serverHash)
        put("runtime_sha256", binding.runtimeHash); put("support_sha256", binding.supportHash)
    }

    @Test fun matchingNativeReceiptIsAcceptedAndSummonUsesTheSameUuid() {
        val result = binding.validate(receipt())
        assertEquals(100L, result["join_tick"])
        val words = binding.summonNbt.removePrefix("{UUID:[I;").removeSuffix("]}").split(',').map(String::toInt)
        val bytes = ByteBuffer.allocate(16).apply { words.forEach { putInt(it) }; flip() }
        assertEquals(binding.entity, UUID(bytes.long, bytes.long))
        assertTrue(binding.properties.contains("-Dbc.pack_test.whale.player_uuid=${binding.player}"))
    }

    @Test fun changedIdentityAndHashesAndMalformedEvidenceCannotPass() {
        for (key in listOf("schema", "run", "nonce", "player_uuid", "entity_uuid", "entity_type", "dimension",
            "client_sha256", "server_sha256", "runtime_sha256", "support_sha256")) {
            assertThrows(IllegalArgumentException::class.java, { binding.validate(receipt().put(key, "different")) }, key)
            assertThrows(IllegalArgumentException::class.java, { binding.validate(receipt().also { it.remove(key) }) }, key)
        }
        assertThrows(IllegalArgumentException::class.java) { binding.validate(receipt().put("unbound", "field")) }
        assertThrows(IllegalArgumentException::class.java) { binding.validate(mapper.readTree("[]")) }
    }

    @Test fun missingRepeatedReversedNegativeAndNonIntegerTickWitnessesFail() {
        for (key in listOf("join_tick", "first_tick", "last_tick")) {
            for (bad in listOf(-1L, 100L)) {
                if (key == "join_tick" && bad == 100L) continue
                assertThrows(IllegalArgumentException::class.java) { binding.validate(receipt().put(key, bad)) }
            }
            assertThrows(IllegalArgumentException::class.java) { binding.validate(receipt().put(key, "101")) }
            assertThrows(IllegalArgumentException::class.java) { binding.validate(receipt().put(key, 100.5)) }
            assertThrows(IllegalArgumentException::class.java) { binding.validate(receipt().also { it.remove(key) }) }
        }
        assertThrows(IllegalArgumentException::class.java) { binding.validate(receipt().put("join_tick", 103)) }
        assertThrows(IllegalArgumentException::class.java) { binding.validate(receipt().put("last_tick", 101)) }
    }
}
