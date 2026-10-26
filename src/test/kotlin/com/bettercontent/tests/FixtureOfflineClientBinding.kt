package com.bettercontent.tests

import java.nio.file.Path
import java.util.UUID

/** Launch input for a disposable offline client; no real token or production account is accepted. */
internal data class FixtureOfflineClientBinding(
    val runId: String,
    val nonce: UUID,
    val username: String,
    val playerId: UUID,
    val client: Path,
    val bridge: Path,
) {
    init {
        require(runId.matches(Regex("[0-9]{8}T[0-9]{6}Z-[0-9]+")))
        require(username.matches(Regex("[A-Za-z0-9_]{1,16}")))
        require(playerId == UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8)))
        require(client.fileName != null && bridge.fileName != null && client.parent != null && bridge.parent != null)
        require(client.isAbsolute && bridge.isAbsolute && client == client.normalize() && bridge == bridge.normalize())
        require(client.fileName.toString().matches(Regex("client(?:-[0-9]+)?")))
        require(bridge.fileName.toString() == "runtime-diagnostics" && client.parent == bridge.parent && client.parent.fileName.toString() == "fixture")
    }
    val properties: String get() = mapOf("run" to runId, "nonce" to nonce.toString(), "player" to username,
        "uuid" to playerId.toString(), "root" to client.toString(), "bridge" to bridge.toString()).entries.joinToString(" ") { (key, value) ->
        require(value.none { it.isWhitespace() || it == '\"' || it == '\'' }) { "unsafe offline fixture property" }
        "-Dbc.pack_test.offline_client.$key=$value"
    }
    val onboardingRequest: Path get() = bridge.resolve("onboarding-$nonce.request")
    val onboardingPayload: String get() = "$runId\n$nonce\n$playerId\n"
    val readyMarker: Regex get() = Regex(Regex.escape("BC_DEDICATED_ONBOARDING_READY run=$runId nonce=$nonce uuid=$playerId mode=survival ") +
        "dimension=[a-z0-9_.-]+:[a-z0-9_./-]+ chunk_loaded=true submitted=true")
    val offlineMarker: Regex get() = Regex(Regex.escape("BC_OFFLINE_CLIENT_BOUND run=$runId nonce=$nonce uuid=$playerId root=$client empty_token=true online_request=false"))
}
