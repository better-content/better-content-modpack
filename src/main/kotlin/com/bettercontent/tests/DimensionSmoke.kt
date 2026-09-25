package com.bettercontent.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

data class DimensionTarget(val id: String, val sources: Set<String>)

object DimensionSmokePlan {
    private val mapper = jacksonObjectMapper()
    private val resourceLocation = Regex("^[a-z0-9_.-]+:[a-z0-9_./-]+$")
    private val overallTps = Regex("Overall.*?Mean TPS:\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)

    // Keep probes well clear of spawn while avoiding a multi-second Lost Cities
    // worldgen stall between successive clients.  The campaign soak has its own
    // required 0/10,000/20,000 Overworld positions.
    val positions = listOf(100_000 to 100_000, 100_128 to 100_000, 100_000 to 100_128)

    // Dimension Drink explicitly requires its own one-call authorization for these
    // destinations. A console `execute in ... run tp` is meant to be denied.
    private val fontOnlyDimensions = setOf("rats:ratlantis", "the_bumblezone:the_bumblezone")

    fun requiresFontTravel(id: String): Boolean = id in fontOnlyDimensions

    fun discover(dimensions: Path, fonts: Path): List<DimensionTarget> {
        val snapshot = mapper.readTree(dimensions.toFile())
        require(snapshot.path("schema").asText() == "bc.dimensions.v1") { "unexpected dimension inventory schema" }
        require(snapshot.path("complete").asBoolean()) { "dimension inventory is incomplete" }
        val loaded = strings(snapshot.path("loaded_dimensions")).toSet()
        val sources = linkedMapOf<String, MutableSet<String>>()
        strings(snapshot.path("rocket_accessible_dimensions")).forEach { id ->
            sources.getOrPut(id, ::linkedSetOf).add("creatingspace")
        }
        if (Files.isDirectory(fonts)) {
            Files.list(fonts).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.extension == "json" }.sorted().forEach { path ->
                    val definition = mapper.readTree(path.toFile())
                    if (definition.path("enabled").asBoolean(true)) {
                        val id = definition.path("targetDimension").asText()
                        require(resourceLocation.matches(id)) { "invalid Font target dimension in $path: $id" }
                        sources.getOrPut(id, ::linkedSetOf).add("dimension_drink")
                    }
                }
            }
        }
        val unavailable = sources.keys - loaded
        require(unavailable.isEmpty()) { "configured dimension targets are not loaded: ${unavailable.sorted()}" }
        require(loaded.isNotEmpty()) { "runtime dimension inventory contains no loaded dimensions" }
        // Route metadata identifies how a dimension is reached in normal play. The
        // teleport smoke intentionally covers the complete runtime registry, including
        // dimensions without a Font or Creating Space route.
        return loaded.sorted().map { id -> DimensionTarget(id, sources[id]?.toSet().orEmpty()) }
    }

    fun parseOverallTps(line: String): Double = overallTps.find(line)?.groupValues?.get(1)?.toDouble()
        ?: error("Forge TPS output has no Overall mean TPS: $line")

    private fun strings(node: JsonNode): List<String> {
        require(node.isArray) { "expected a JSON array" }
        return node.map(JsonNode::asText).onEach { require(resourceLocation.matches(it)) { "invalid dimension ID: $it" } }
    }
}
