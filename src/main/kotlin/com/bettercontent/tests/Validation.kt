package com.bettercontent.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

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
        validateOreProcessing(recipes, read(directory.resolve("tags.json")), requireComplete = true)
        return snapshotId
    }

    internal fun validateOreProcessing(recipes: JsonNode, tags: JsonNode, requireComplete: Boolean = false) {
        val expectedOccultismOutputs = mapOf(
            "occultism:crushing/blaze_powder_from_rod" to ("minecraft:blaze_powder" to 1),
            "occultism:crushing/certus_quartz_dust_from_gem" to ("ae2:certus_quartz_dust" to 1),
            "occultism:crushing/coal_dust" to ("bloodmagic:coalsand" to 4),
            "occultism:crushing/datura" to ("occultism:datura_seeds" to 2),
            "occultism:crushing/end_stone_dust" to ("occultism:crushed_end_stone" to 1),
            "occultism:crushing/iesnium_dust" to ("occultism:iesnium_dust" to 2),
            "occultism:crushing/iesnium_dust_from_ingot" to ("occultism:iesnium_dust" to 1),
            "occultism:crushing/iesnium_dust_from_raw" to ("occultism:iesnium_dust" to 2),
            "occultism:crushing/iesnium_dust_from_raw_block" to ("occultism:iesnium_dust" to 18),
            "occultism:crushing/iridium_dust_from_ingot" to ("chemlib:iridium_dust" to 1),
            "occultism:crushing/redstone_dust" to ("minecraft:redstone" to 4),
            "occultism:crushing/tungsten_dust_from_ingot" to ("chemlib:tungsten_dust" to 1),
        )
        val recipeRows = recipes.path("recipes")
        if (requireComplete) {
            require(recipeRows.isArray && recipeRows.size() > 0) { "runtime recipe graph has no recipes" }
            val actualIds = recipeRows.map { it.path("id").asText() }.toSet()
            val missing = expectedOccultismOutputs.keys - actualIds
            require(missing.isEmpty()) { "runtime recipe graph is missing canonical Occultism recipes: $missing" }
        }
        recipeRows.forEach { recipe ->
            val recipeId = recipe.path("id").asText()
            val expectedOutput = expectedOccultismOutputs[recipeId] ?: return@forEach
            val outputs = recipe.path("outputs")
            require(outputs.size() == 1) { "$recipeId does not have one canonical output" }
            val output = outputs[0]
            require(
                output.path("kind").asText() == "item" &&
                    output.path("id").asText() == expectedOutput.first &&
                    output.path("count").asInt() == expectedOutput.second,
            ) { "$recipeId has a broken or non-canonical output: $outputs" }
        }

        val realisticDeposit = Regex(
            "^excavated_variants:.*_(?:black_shale|brassroot|coal_measures|copper_bloom|" +
                "evaporite_beds|hotstone|ironstone|tin_quartz)$",
        )
        listOf("forge:ores", "c:ores").forEach { tag ->
            val entries = tags.path("item_tags").path(tag)
            if (requireComplete) require(entries.isArray && entries.size() > 0) { "runtime ore tag is missing or empty: $tag" }
            val leaked = entries.map(JsonNode::asText).filter(realisticDeposit::matches)
            require(leaked.isEmpty()) { "$tag contains Realistic Ores hosted variants: ${leaked.take(10)}" }
        }
    }

    private fun read(path: Path): JsonNode {
        require(path.exists()) { "runtime snapshot is missing ${path.fileName}" }
        return mapper.readTree(path.toFile())
    }
}

object LogPolicy {
    data class Finding(val path: Path, val line: Int, val text: String)
    data class Record(val severity: String, val thread: String?, val logger: String?, val message: String)
    data class StartupTiming(
        val path: Path, val line: Int, val text: String,
        val mod: String, val thread: String, val seconds: Double,
    )

    // Lazy thread capture permits native names such as DH-LOD Builder Thread[0]
    // without letting a quoted record in the payload replace the outer severity.
    private val nativeRecord = Regex("^(?:\\[(?:[0-9]{2}:[0-9]{2}:[0-9]{2}|[0-9]{2}[A-Z][a-z]{2}[0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3})] )?\\[(.+?)/(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)] \\[([^]]+)]: (.*)$")
    private val simpleRecord = Regex("^\\[(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)]\\s*(.*)$")
    private val errorEnvelope = Regex("(?:/|\\[)(?:ERROR|FATAL)]")
    private val failedOperation = Regex(
        "OutOfMemoryError|fatal error has been detected|crash report|ReportedException:|AssertionError|" +
            "ThreadingDetector:|Exception in server tick loop|Failed to complete lifecycle event|" +
            "Errors in currently selected datapacks|Error loading KubeJS script|KubeJS errors found \\[[1-9][0-9]*]|" +
            "(?:failed to|error (?:while|when)) (?:load|register|parse|execute|save|read|write|start|join)|" +
            "Registry Object not present:|(?:registry|registries|script|mixin)[^\\n]*(?:failed|failure|corrupt)|" +
            "(?:corrupt(?:ed|ion)?)[^\\n]*(?:chunk|world|save|data)|" +
            "(?:chunk|world|save|data)[^\\n]*corrupt(?:ed|ion)?|" +
            "^\\s*(?:Caused by: |Suppressed: |Exception in thread \"[^\"]+\" )?" +
            "(?:[a-zA-Z_$][\\w$]*\\.)*(?:\\w*Exception|\\w*Error):",
        RegexOption.IGNORE_CASE,
    )

    fun parseRecord(text: String): Record? {
        nativeRecord.matchEntire(text)?.let {
            return Record(it.groupValues[2], it.groupValues[1], it.groupValues[3], it.groupValues[4])
        }
        simpleRecord.matchEntire(text)?.let { return Record(it.groupValues[1], null, null, it.groupValues[2]) }
        return null
    }

    // This exact native WARN is elapsed startup work, never a failed operation.
    private val startupTiming = Regex(
        "^\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[(main|Render thread)/WARN] " +
            "\\[net\\.minecraftforge\\.fml\\.DeferredWorkQueue]: " +
            "Mod '([a-z][a-z0-9_]*)' took ([0-9]+(?:\\.[0-9]+)?) (ms|s|min|h) to run a deferred task\\.$",
    )

    private fun startupTiming(line: String): Pair<MatchResult, Double>? {
        val match = startupTiming.matchEntire(line) ?: return null
        val value = match.groupValues[3].toDoubleOrNull() ?: return null
        val seconds = value * when (match.groupValues[4]) {
            "ms" -> 0.001
            "s" -> 1.0
            "min" -> 60.0
            "h" -> 3600.0
            else -> return null
        }
        return if (seconds.isFinite() && seconds > 0.0) match to seconds else null
    }

    fun startupTimings(paths: Collection<Path>): List<StartupTiming> = buildList {
        paths.distinct().filter { Files.isRegularFile(it) }.forEach { path ->
            Files.readAllLines(path).forEachIndexed { index, text ->
                val (match, seconds) = startupTiming(text) ?: return@forEachIndexed
                add(StartupTiming(path, index + 1, text, match.groupValues[2], match.groupValues[1], seconds))
            }
        }
    }

    private val optionalJarjar = Regex(
        "Failed to load resource META-INF/jarjar/metadata\\.json from [^,\\r\\n]*, " +
            "it does not contain dependency information\\.",
    )
    private val stackFrame = Regex("\\s+at [\\w.$/<>]+\\([^()\\r\\n]*\\)(?: ~\\[[^\\]\\r\\n]*\\])?")
    private val nettyProbes = mapOf(
        "direct buffer constructor: unavailable" to (
            "java.lang.UnsupportedOperationException: Reflective setAccessible(true) disabled" to
                "io.netty.util.internal.ReflectionUtil.trySetAccessible"
            ),
        "jdk.internal.misc.Unsafe.allocateUninitializedArray(int): unavailable" to (
            "java.lang.IllegalAccessException: class io.netty.util.internal.PlatformDependent0\$7 " +
                "(in module io.netty.common) cannot access class jdk.internal.misc.Unsafe " +
                "(in module java.base) because module java.base does not export jdk.internal.misc to module io.netty.common" to
                "io.netty.util.internal.PlatformDependent0\$7.run"
            ),
    )

    private fun validProbeTimestamp(text: String): Boolean {
        val stamp = Regex("^\\[([^]]+)] ").find(text)?.groupValues?.get(1) ?: return false
        return runCatching {
            if (stamp.length == 8) java.time.LocalTime.parse(stamp) else {
                val format = java.time.format.DateTimeFormatter.ofPattern("ddMMMuuuu HH:mm:ss.SSS", java.util.Locale.ENGLISH)
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT)
                java.time.LocalDateTime.parse(stamp, format)
            }
        }.isSuccess
    }

    private fun optionalProbeLines(group: List<Pair<Int, String>>): Set<Int> {
        if (group.isEmpty()) return emptySet()
        val (number, text) = group.first()
        val record = parseRecord(text) ?: return emptySet()
        if (record.thread != "main" || record.severity != "DEBUG" || !validProbeTimestamp(text)) return emptySet()
        // Only the native optional FileNotFoundException branch emits this DEBUG
        // metadata message; Forge's malformed/required-load branch emits ERROR.
        val logger = record.logger?.removeSuffix("/")
        if (logger == "net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileDependencyLocator" &&
            optionalJarjar.matches(record.message) &&
            !failedOperation.containsMatchIn(record.message.removePrefix("Failed to load resource "))
        ) return setOf(number)
        val probe = if (logger == "io.netty.util.internal.PlatformDependent0") nettyProbes[record.message] else null
        if (probe == null) return emptySet()
        val continuation = group.drop(1).dropLastWhile { it.second.isBlank() }
        if (continuation.size < 2 || continuation.first().second != probe.first) return emptySet()
        val frames = continuation.drop(1).map { it.second }
        val catchSite = Regex("\\s+at " + Regex.escape(probe.second) + "\\(")
        if (!frames.all(stackFrame::matches) || frames.none { catchSite.containsMatchIn(it) }) return emptySet()
        // Netty logs this caught capability probe before selecting a fallback. Do
        // not inherit its DEBUG context across another record or raw exception.
        return setOf(continuation.first().first)
    }

    // Epic Fight 20.14.17 probes this one WOM animation asset as a datapack animation.
    // Its JSON is a model keyframe clip with no `constructor`; readResourcepackAnimation
    // logs this exact message and returns before registering or loading anything.
    // Keep the compatibility classification exact: other Epic Fight errors still fail.
    private fun ignoredWomAnimationSchemaProbe(text: String): Boolean {
        val record = parseRecord(text) ?: return false
        return record.severity == "ERROR" && record.thread == "Render thread" &&
            record.logger == "epicfight" &&
            record.message == "Datapack animation reading failed: No constructor information has provided: " +
                "wom:valkyrie/valkyrie_idle"
    }

    private fun operationFailed(text: String): Boolean {
        val record = parseRecord(text)
        val failedLootDecode = record?.logger?.removeSuffix("/") == "net.minecraftforge.common.loot.LootModifierManager" &&
            record.message.startsWith("Could not decode GlobalLootModifier with json id ")
        val failedBlockEntityLoad = record != null && record.logger?.removeSuffix("/") in setOf("net.minecraft.world.level.chunk.LevelChunk", "minecraft/LevelChunk") &&
            (record.message.startsWith("Tried to load a block entity for block ") && record.message.contains(" but failed at location ") ||
                record.message.startsWith("Tried to load a DUMMY block entity @ ") && record.message.contains(" but found not block entity block "))
        // Species reports an unsupported original soil and returns false after
        // attempting its dirt fallback. This is not proof the whole tree aborted,
        // nor may a later GameTest count conceal the failed original operation.
        val unsupportedRootySoil = record?.logger?.removeSuffix("/") ==
            "com.ferreusveritas.dynamictrees.tree.species.Species" &&
            record.message.startsWith("Rooty Dirt block NOT FOUND for soil ")
        return record?.severity in setOf("ERROR", "FATAL") || failedLootDecode || failedBlockEntityLoad || unsupportedRootySoil ||
            (record == null && errorEnvelope.containsMatchIn(text)) ||
            failedOperation.containsMatchIn(record?.message ?: text)
    }

    fun findings(paths: Collection<Path>): List<Finding> = buildList {
        paths.distinct().filter { Files.isRegularFile(it) }.forEach { path ->
            val group = mutableListOf<Pair<Int, String>>()
            fun flush() {
                val diagnostics = optionalProbeLines(group)
                group.forEach { (number, text) ->
                    if (number !in diagnostics && !ignoredWomAnimationSchemaProbe(text) && operationFailed(text))
                        add(Finding(path, number, text))
                }
            }
            Files.readAllLines(path).forEachIndexed { index, text ->
                if (parseRecord(text) != null) {
                    flush()
                    group.clear()
                }
                group += index + 1 to text
            }
            flush()
        }
    }

    fun requireClean(paths: Collection<Path>) {
        val found = findings(paths)
        require(found.isEmpty()) {
            found.take(25).joinToString(prefix = "correctness log findings detected:\n", separator = "\n") {
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
