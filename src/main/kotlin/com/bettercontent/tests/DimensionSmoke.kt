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

    val positions = listOf(1_000_000 to 1_000_000, 1_010_000 to 1_000_000, 1_000_000 to 1_010_000)

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
        require(sources.isNotEmpty()) { "no Font or Creating Space dimension targets were discovered" }
        val unavailable = sources.keys - loaded
        require(unavailable.isEmpty()) { "configured dimension targets are not loaded: ${unavailable.sorted()}" }
        return sources.entries.sortedBy(Map.Entry<String, *>::key).map { DimensionTarget(it.key, it.value.toSet()) }
    }

    fun parseOverallTps(line: String): Double = overallTps.find(line)?.groupValues?.get(1)?.toDouble()
        ?: error("Forge TPS output has no Overall mean TPS: $line")

    private fun strings(node: JsonNode): List<String> {
        require(node.isArray) { "expected a JSON array" }
        return node.map(JsonNode::asText).onEach { require(resourceLocation.matches(it)) { "invalid dimension ID: $it" } }
    }
}
