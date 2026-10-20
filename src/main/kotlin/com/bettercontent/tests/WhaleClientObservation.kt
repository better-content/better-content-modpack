package com.bettercontent.tests

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

/** Identity for one nonshipping restart-compat observation, not a general entity command API. */
data class WhaleClientBinding(
    val run: String,
    val nonce: UUID,
    val player: UUID,
    val entity: UUID,
    val clientHash: String,
    val serverHash: String,
    val runtimeHash: String,
    val supportHash: String,
) {
    init {
        require(run.matches(Regex("[0-9]{8}T[0-9]{6}Z-[0-9]+")))
        require(listOf(clientHash, serverHash, runtimeHash, supportHash).all { it.matches(Regex("[a-f0-9]{64}")) })
    }

    val properties: String get() = "-Dbc.pack_test.whale.enabled=true -Dbc.pack_test.whale.nonce=$nonce " +
        "-Dbc.pack_test.whale.entity_uuid=$entity -Dbc.pack_test.whale.player_uuid=$player"

    val summonNbt: String get() = "{UUID:[I;" + listOf((entity.mostSignificantBits shr 32).toInt(),
        entity.mostSignificantBits.toInt(), (entity.leastSignificantBits shr 32).toInt(), entity.leastSignificantBits.toInt())
        .joinToString(",") + "]}"

    fun validate(node: JsonNode): Map<String, Any> {
        val expected = linkedMapOf(
            "schema" to "bc.whale_client_observation.v1", "run" to run, "nonce" to nonce.toString(),
            "player_uuid" to player.toString(), "entity_uuid" to entity.toString(),
            "entity_type" to "untamedwilds:baleen_whale", "dimension" to "minecraft:overworld",
            "client_sha256" to clientHash, "server_sha256" to serverHash,
            "runtime_sha256" to runtimeHash, "support_sha256" to supportHash,
        )
        val tickNames = listOf("join_tick", "first_tick", "last_tick")
        require(node.isObject && node.fieldNames().asSequence().toSet() == expected.keys + tickNames) {
            "unexpected native whale observation fields"
        }
        expected.forEach { (key, value) ->
            require(node.path(key).isTextual && node.path(key).asText() == value) { "native whale observation mismatch: $key" }
        }
        val ticks = tickNames.map { key ->
            val value = node.path(key)
            require(value.isIntegralNumber && value.canConvertToLong() && value.asLong() >= 0) { "invalid native whale tick: $key" }
            value.asLong()
        }
        require(ticks[0] < ticks[1] && ticks[1] < ticks[2]) { "native whale requires a join and two later distinct ticks" }
        return expected + tickNames.zip(ticks)
    }
}
