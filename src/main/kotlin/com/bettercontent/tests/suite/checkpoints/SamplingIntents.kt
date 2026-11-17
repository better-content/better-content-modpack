package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.drivers.TrialRequest

/** Typed preclaims, never guessed from a note title or reconstructed after seeing a favorable sample. */
internal sealed interface SamplingIntent {
    data class Site(val dimension: String, val chunkX: Int, val chunkZ: Int, val footprintRadiusChunks: Int = 0) : SamplingIntent
    data object ThreatAttempt : SamplingIntent
}
internal interface ExternalSamplingDriver {
    val requireFreshTerrain: Boolean get() = false
    fun samplingIntents(trial: TrialRequest): List<SamplingIntent>
}
