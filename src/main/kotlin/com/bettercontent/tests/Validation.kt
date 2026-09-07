package com.bettercontent.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

object RuntimeSnapshotValidator {
    private val mapper = jacksonObjectMapper()
    private val expected = setOf(
        "recipes.json", "registries.json", "tags.json", "mods.json",
        "loot.json", "trades.json", "worldgen.json", "dimensions.json", "lighting.json",
    )

    fun validate(directory: Path): String {
        val snapshot = read(directory.resolve("snapshot.json"))
        require(snapshot.path("schema").asText() == "bc.runtime_dump_completion.v3") { "unexpected runtime snapshot schema" }
        require(snapshot.path("complete").asBoolean() && snapshot.path("evidence_state").asText() == "complete") {
            "runtime snapshot is incomplete"
        }
        val files = snapshot.path("files").map(JsonNode::asText).toSet()
        require(files == expected && snapshot.path("files").size() == expected.size) { "unexpected runtime snapshot file set: $files" }
        require(snapshot.path("surfaces").path("recipes").path("complete_for_contract").asBoolean()) {
            "runtime recipe surface is incomplete"
        }
        val snapshotId = snapshot.path("snapshot_id").asText()
        require(snapshotId.isNotBlank()) { "runtime snapshot has no ID" }
        expected.forEach { name ->
            require(read(directory.resolve(name)).path("snapshot_id").asText() == snapshotId) { "mixed snapshot ID in $name" }
        }
        val recipes = read(directory.resolve("recipes.json"))
        require(recipes.path("complete").asBoolean()) { "runtime recipe graph is incomplete" }
        require(recipes.path("partial_count").asInt(-1) == 0 && recipes.path("error_count").asInt(-1) == 0) {
            "runtime recipe graph contains partial or errored entries"
        }
        return snapshotId
    }

    private fun read(path: Path): JsonNode {
        require(path.exists()) { "runtime snapshot is missing ${path.fileName}" }
        return mapper.readTree(path.toFile())
    }
}

object LogPolicy {
    private val fatal = Regex(
        "OutOfMemoryError|fatal error has been detected|crash report|Error loading KubeJS script|" +
            "(?:\\[|/)ERROR] \\[KubeJS(?: Startup| Client| Server)?/]|KubeJS errors found \\[[1-9][0-9]*]|" +
            "ThreadingDetector:|ReportedException:",
        RegexOption.IGNORE_CASE,
    )
    private val warningOrError = Regex("(?:/WARN]|/ERROR]|\\[(?:WARN|ERROR)])")
    private val accepted = listOf(
        Regex("\\[xbigellx\\.realisticphysics\\.RealisticPhysics]: Forcing chunk load: \\[-?[0-9]+, -?[0-9]+]$"),
        Regex("Detected setBlock in a far chunk .*currently generating: ResourceKey\\[minecraft:worldgen/placed_feature / natures_spirit:marsh_water_placed]$"),
        Regex("\\[Server thread/WARN] \\[net\\.minecraft\\.network\\.Connection]: handleDisconnection\\(\\) called twice$"),
    )

    data class Finding(val path: Path, val line: Int, val text: String)

    fun findings(paths: Collection<Path>): List<Finding> = buildList {
        paths.filter { Files.isRegularFile(it) }.forEach { path ->
            Files.readAllLines(path).forEachIndexed { index, line ->
                if ((fatal.containsMatchIn(line) || warningOrError.containsMatchIn(line)) && accepted.none { it.containsMatchIn(line) }) {
                    add(Finding(path, index + 1, line))
                }
            }
        }
    }

    fun requireClean(paths: Collection<Path>) {
        val found = findings(paths)
        require(found.isEmpty()) {
            found.take(25).joinToString(prefix = "fatal, warning, or error log records detected:\n", separator = "\n") {
                "${it.path}:${it.line}: ${it.text}"
            }
        }
    }
}

fun collectLogs(root: Path): List<Path> = if (!root.exists()) emptyList() else Files.walk(root).use { stream ->
    stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".log") }.toList()
}
