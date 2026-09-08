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
    private val adPotherDeferredTask = Regex(
        "\\[(?:main|Render thread)/WARN] \\[net\\.minecraftforge\\.fml\\.DeferredWorkQueue]: " +
            "Mod 'adpother' took ([0-9]+(?:\\.[0-9]+)?) s to run a deferred task\\.$",
    )
    private val adChimneysDeferredTask = Regex(
        "\\[(?:main|Render thread)/WARN] \\[net\\.minecraftforge\\.fml\\.DeferredWorkQueue]: " +
            "Mod 'adchimneys' took ([0-9]+(?:\\.[0-9]+)?) s to run a deferred task\\.$",
    )
    private val earlyWorldgenBlockEntity = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[C2ME worker #[0-9]+/WARN] " +
            "\\[net\\.minecraft\\.server\\.level\\.WorldGenRegion]: " +
            "Tried to access a block entity before it was created\\. " +
            "BlockPos\\{x=-?[0-9]+, y=-?[0-9]+, z=-?[0-9]+}$",
    )
    private val distantHorizonsRecoveredPhantomArray = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] " +
            "\\[DH-Phantom Array Recycler Thread\\[[0-9]+]/WARN] " +
            "\\[DistantHorizons-DistantHorizons-com\\.seibel\\.distanthorizons\\.core\\.pooling\\." +
            "PhantomArrayListPool]: Pool: \\[Render Reducer]\\. " +
            "Unable to find checkout for phantom reference " +
            "\\[java\\.lang\\.ref\\.PhantomReference@[0-9a-f]+], arrays will need to be recreated\\.$",
    )

    data class Finding(val path: Path, val line: Int, val text: String)

    fun findings(paths: Collection<Path>): List<Finding> = buildList {
        paths.filter { Files.isRegularFile(it) }.forEach { path ->
            var acceptedEarlyBlockEntityWarnings = 0
            var acceptedRecoveredPhantomArrays = 0
            var acceptedAdChimneysDeferredTasks = 0
            Files.readAllLines(path).forEachIndexed { index, line ->
                val acceptedEarlyBlockEntity = earlyWorldgenBlockEntity.matches(line) &&
                    ++acceptedEarlyBlockEntityWarnings <= 4
                val acceptedRecoveredPhantomArray = distantHorizonsRecoveredPhantomArray.matches(line) &&
                    ++acceptedRecoveredPhantomArrays <= 1
                val adChimneysDuration = adChimneysDeferredTask.find(line)
                    ?.groupValues?.get(1)?.toDoubleOrNull()
                val acceptedAdChimneysDeferredTask = adChimneysDuration != null &&
                    adChimneysDuration <= 10.0 && ++acceptedAdChimneysDeferredTasks <= 1
                if ((fatal.containsMatchIn(line) || warningOrError.containsMatchIn(line)) &&
                    !acceptedEarlyBlockEntity && !acceptedRecoveredPhantomArray &&
                    !acceptedAdChimneysDeferredTask && !isAccepted(line)
                ) {
                    add(Finding(path, index + 1, line))
                }
            }
        }
    }

    private fun isAccepted(line: String): Boolean {
        if (accepted.any { it.containsMatchIn(line) }) return true
        val duration = adPotherDeferredTask.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: return false
        return duration <= 5.0
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

object FalloutWorldgenEvidence {
    private val cityRuinFarWrite = Regex(
        "Detected setBlock in a far chunk .*currently generating: " +
            "ResourceKey\\[minecraft:worldgen/placed_feature / fallout_wastelands_:cityruins]$",
    )

    fun cityRuinFarWriteCount(path: Path): Int = if (Files.isRegularFile(path)) {
        Files.readAllLines(path).count(cityRuinFarWrite::containsMatchIn)
    } else {
        0
    }

    fun requireNoCityRuinFarWrites(path: Path) {
        val count = cityRuinFarWriteCount(path)
        require(count == 0) { "Fallout cityruins attempted $count far-chunk writes; see $path" }
    }
}

fun collectLogs(root: Path): List<Path> = if (!root.exists()) emptyList() else Files.walk(root).use { stream ->
    stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".log") }.toList()
}
