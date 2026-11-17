package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.resourcePattern
import com.fasterxml.jackson.databind.JsonNode

/** B's stopServer lifecycle marker, not proof of transformed delivery or an actual disk save. */
internal data class ReplayedShutdownDrain(val entryTick: Long, val returnTick: Long,
    val unloadedChunks: List<BirthChunkKey>)

/** Strictly bound to a captured STOP, never used to turn a failed diagnostic into coverage. */
internal fun replayShutdownDrain(status: JsonNode, expectedChunks: Set<BirthChunkKey>,
    requireEveryExpectedChunk: Boolean = false): ReplayedShutdownDrain {
    val stopTick = status.path("stop_tick")
    require(stopTick.isIntegralNumber && stopTick.canConvertToLong() && stopTick.asLong() >= 0)
    val drain = status.path("shutdown_drain")
    require(drain.isObject && drain.fieldNames().asSequence().toSet() == setOf(
        "schema", "entry_observed", "return_observed", "entry_tick", "return_tick",
        "watched_unload_count", "watched_unloads", "retained_identity_count",
        "identity_and_state_match_at_close", "unexpected_post_drain_activity") &&
        drain.path("schema").isTextual &&
        drain.path("schema").asText() == "bc.playtest_mutation_shutdown_drain.v1" &&
        listOf("entry_observed", "return_observed", "identity_and_state_match_at_close")
            .all { drain.path(it).isBoolean && drain.path(it).asBoolean() } &&
        drain.path("unexpected_post_drain_activity").isBoolean &&
        !drain.path("unexpected_post_drain_activity").asBoolean()) {
        "watched shutdown drain was absent, incomplete or had unreviewed fields"
    }
    val entry = drain.path("entry_tick"); val returned = drain.path("return_tick")
    require(entry.isIntegralNumber && entry.canConvertToLong() && entry.asLong() >= 0 &&
        returned.isIntegralNumber && returned.canConvertToLong() &&
        returned.asLong() in entry.asLong()..stopTick.asLong())
    val unloads = drain.path("watched_unloads")
    val count = drain.path("watched_unload_count")
    val retained = drain.path("retained_identity_count")
    require(unloads.isArray && unloads.size() in 1..64 &&
        count.isIntegralNumber && count.canConvertToInt() && count.asInt() == unloads.size() &&
        retained.isIntegralNumber && retained.canConvertToInt() && retained.asInt() == unloads.size())
    val chunks = unloads.map { row ->
        require(row.isObject && row.fieldNames().asSequence().toSet() ==
            setOf("dimension", "chunk_x", "chunk_z"))
        val dimension = row.path("dimension")
        val x = row.path("chunk_x"); val z = row.path("chunk_z")
        require(dimension.isTextual && dimension.asText().matches(resourcePattern) &&
            x.isIntegralNumber && x.canConvertToInt() && x.asInt() in -1_875_001..1_875_001 &&
            z.isIntegralNumber && z.canConvertToInt() && z.asInt() in -1_875_001..1_875_001)
        BirthChunkKey(dimension.asText(), x.asInt(), z.asInt())
    }
    require(chunks == chunks.distinct().sortedWith(compareBy({ it.dimension }, { it.chunkX }, { it.chunkZ })) &&
        chunks.all { it in expectedChunks } &&
        (!requireEveryExpectedChunk || chunks.toSet() == expectedChunks)) {
        "watched shutdown unload was duplicate, unordered or outside the immutable scope"
    }
    return ReplayedShutdownDrain(entry.asLong(), returned.asLong(), chunks)
}
