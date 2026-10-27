package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path

/** Parse fresh, actual Forge/diagnostics output; missing or partial probes are failures, never default 20 TPS. */
internal object TeleSoakProbes {
    private val mapper = jacksonObjectMapper()
    private val tpsLine = Regex("Overall: Mean tick time: ([0-9]+(?:\\.[0-9]+)?) ms\\. Mean TPS: ([0-9]+(?:\\.[0-9]+)?)")

    fun dimensions(output: Path): List<String> {
        val snapshot = mapper.readTree(Files.readAllBytes(output.resolve("snapshot.json")))
        val result = mapper.readTree(Files.readAllBytes(output.resolve("dimensions.json")))
        require(snapshot.path("snapshot_id").asText().isNotBlank() &&
            snapshot.path("snapshot_id").asText() == result.path("snapshot_id").asText()) {
            "dimension roster and completion must share a fresh snapshot ID"
        }
        require(result.path("schema").asText() == "bc.dimensions.v1" && result.path("complete").asBoolean(false) &&
            result.path("error_count").asInt(-1) == 0) { "live dimension probe is incomplete" }
        val values = result.path("loaded_dimensions")
        require(values.isArray && values.size() == result.path("loaded_dimension_count").asInt(-1)) {
            "live dimension roster count mismatch"
        }
        val registered = result.path("registered_dimensions")
        require(result.path("registered_dimension_registry").asText() == "minecraft:dimension" &&
            result.path("registered_dimension_registry_available").asBoolean(false) &&
            result.path("registered_dimension_count_matches_ids").asBoolean(false) &&
            result.path("registered_loaded_dimensions_match").asBoolean(false) &&
            registered.isArray && registered.size() == result.path("registered_dimension_count").asInt(-1) &&
            result.path("registered_not_loaded_dimensions").isArray &&
            result.path("registered_not_loaded_dimensions").size() == 0 &&
            result.path("loaded_not_registered_dimensions").isArray &&
            result.path("loaded_not_registered_dimensions").size() == 0) {
            "actual LevelStem registered/loaded dimension roster is absent or incomplete"
        }
        val ids = values.map { it.asText() }
        val registeredIds = registered.map { it.asText() }
        require(ids.isNotEmpty() && ids.size == ids.toSet().size && "minecraft:overworld" in ids &&
            registeredIds.isNotEmpty() && registeredIds.size == registeredIds.toSet().size &&
            registeredIds.toSet() == ids.toSet()) {
            "actual LevelStem and live loaded dimension IDs differ, duplicate or omit dimensions"
        }
        return ids
    }

    /** Forge 47.4.22 /forge tps overall mean of its actual server tick-time ring. */
    fun overallTps(consoleAfterCommand: String): Double {
        val samples = consoleAfterCommand.lineSequence().mapNotNull { tpsLine.find(it)?.groupValues?.get(2)?.toDoubleOrNull() }.toList()
        require(samples.size == 1 && samples.single().isFinite() && samples.single() in 0.0..20.0001) {
            "one fresh Forge overall TPS result required; observed ${samples.size}"
        }
        return samples.single()
    }
}
