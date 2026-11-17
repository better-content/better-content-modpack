package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Synthetic v3/v2 STOP shape regressions. Source compilation is not a real server-stop receipt. */
class ShutdownDrainWireTest {
    private val expected = setOf(BirthChunkKey("minecraft:overworld", 1, -2))
    private val status = """{
        "stop_tick":24,"shutdown_drain":{
            "schema":"bc.playtest_mutation_shutdown_drain.v1",
            "entry_observed":true,"return_observed":true,"entry_tick":22,"return_tick":23,
            "watched_unload_count":1,"watched_unloads":[{"dimension":"minecraft:overworld","chunk_x":1,"chunk_z":-2}],
            "retained_identity_count":1,"identity_and_state_match_at_close":true,
            "unexpected_post_drain_activity":false}}
    """.trimIndent()

    @Test fun boundedRealShutdownDrainIsOnlyALifecycleMarker() {
        assertEquals(listOf(BirthChunkKey("minecraft:overworld", 1, -2)),
            replayShutdownDrain(suiteMapper.readTree(status), expected, requireEveryExpectedChunk = true).unloadedChunks)
    }

    @Test fun absentReturnDuplicateCountOutsideScopeAndBadTypesCannotQualify() {
        val corrupt = listOf(
            status.replace("\"return_observed\":true", "\"return_observed\":false"),
            status.replace("\"return_observed\":true", "\"return_observed\":\"true\""),
            status.replace("\"watched_unload_count\":1", "\"watched_unload_count\":2"),
            status.replace("\"retained_identity_count\":1", "\"retained_identity_count\":0"),
            status.replace("\"entry_tick\":22", "\"entry_tick\":25"),
            status.replace("\"chunk_x\":1", "\"chunk_x\":2"),
            status.replace("\"unexpected_post_drain_activity\":false", "\"unexpected_post_drain_activity\":true"),
            status.replace("\"identity_and_state_match_at_close\":true", "\"identity_and_state_match_at_close\":false")
        )
        for (variant in corrupt) assertThrows(IllegalArgumentException::class.java) {
            replayShutdownDrain(suiteMapper.readTree(variant), expected, requireEveryExpectedChunk = true)
        }
    }
}
