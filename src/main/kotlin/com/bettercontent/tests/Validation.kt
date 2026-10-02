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
        validateOreProcessing(recipes, read(directory.resolve("tags.json")))
        return snapshotId
    }

    internal fun validateOreProcessing(recipes: JsonNode, tags: JsonNode) {
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
        recipes.path("recipes").forEach { recipe ->
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
            val leaked = tags.path("item_tags").path(tag).map(JsonNode::asText).filter(realisticDeposit::matches)
            require(leaked.isEmpty()) { "$tag contains Realistic Ores hosted variants: ${leaked.take(10)}" }
        }
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
    private val untamedJeiPrototypeWarning = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[Render thread/WARN] \\[untamedwilds\\.UntamedWilds]: " +
            "There's no species provided for the EntityType$",
    )
    private val untamedServerMissingSpeciesWarning = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[Server thread/WARN] \\[untamedwilds\\.UntamedWilds]: " +
            "There's no species provided for the EntityType$",
    )
    private val seededWorldRegistryRemap = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[Render thread/WARN] \\[net\\.minecraftforge\\.registries\\.ForgeRegistry]: " +
            "Registry minecraft:(?:item|sound_event): Object did not get ID it asked for\\. Name: " +
            "(?:theoneprobe:(?:probe|creativeprobe|probenote|diamond_helmet_probe|gold_helmet_probe|iron_helmet_probe)|" +
            "guideme:guide\\.click) Expected: [0-9]+ Got: [0-9]+$",
    )
    private val starcatcherInvalidAccessTransformer = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[main/WARN] " +
            "\\[net\\.minecraftforge\\.fml\\.loading\\.moddiscovery\\.ModFile]: " +
            Regex.escape("starcatcher-2.2.1-FORGE-1.20.1.jar contains an invalid 'accessTransformers' TOML entry. " +
                "Should be e.g. accessTransformers = [\"META-INF/accesstransformer.cfg\", " +
                "\"META-INF/extra_at.cfg\"] or accessTransformers = [] for no ATs. Falling back to default.") + "$",
    )
    private val campaignItemFrameIronSword = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[C2ME worker #[0-9]+/WARN] " +
            "\\[net\\.minecraft\\.world\\.entity\\.decoration\\.ItemFrame]: " +
            "Unable to load item from: \\{count:1,id:\"minecraft:iron_sword\"}$",
    )
    private val campaignItemFrameIronAxe = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[C2ME worker #[0-9]+/WARN] " +
            "\\[net\\.minecraft\\.world\\.entity\\.decoration\\.ItemFrame]: " +
            "Unable to load item from: \\{count:1,id:\"minecraft:iron_axe\"}$",
    )
    private val campaignUnknownStepHeightAttribute = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[C2ME worker #[0-9]+/WARN] " +
            "\\[net\\.minecraft\\.world\\.entity\\.ai\\.attributes\\.AttributeMap]: " +
            "Ignoring unknown attribute 'forge:step_height'$",
    )
    private val iceAndFireDeferredTask = Regex(
        "\\[(?:main|Render thread)/WARN] \\[net\\.minecraftforge\\.fml\\.DeferredWorkQueue]: " +
            "Mod 'iceandfire' took ([0-9]+(?:\\.[0-9]+)?) s to run a deferred task\\.$",
    )
    private val collectiveUpdateNotice = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[[^]]+/WARN] \\[Collective]: " +
            "\\[Update] Collective has an update available: [0-9]+\\.[0-9]+(?:\\.[0-9]+)? -> " +
            "[0-9]+\\.[0-9]+(?:\\.[0-9]+)?$",
    )
    private val accepted = listOf(
        Regex("\\[net\\.minecraft\\.client\\.ClientRecipeBook]: Unknown recipe category: .*/the_deep_void:[a-z0-9_]+$"),
        Regex("\\[net\\.minecraft\\.client\\.ClientRecipeBook]: Unknown recipe category: cataclysm:weapon_fusion/cataclysm:weapon_infusion/[a-z0-9_]+$"),
        Regex("\\[xbigellx\\.realisticphysics\\.RealisticPhysics]: Forcing chunk load: \\[-?[0-9]+, -?[0-9]+]$"),
        Regex("Detected setBlock in a far chunk .*currently generating: ResourceKey\\[minecraft:worldgen/placed_feature / natures_spirit:marsh_water_placed]$"),
        Regex("\\[Server thread/WARN] \\[net\\.minecraft\\.network\\.Connection]: handleDisconnection\\(\\) called twice$"),
    )
    private val adPotherDeferredTask = Regex(
        "\\[(?:main|Render thread)/WARN] \\[net\\.minecraftforge\\.fml\\.DeferredWorkQueue]: " +
            "Mod 'adpother' took ([0-9]+(?:\\.[0-9]+)?) s to run a deferred task\\.$",
    )
    private val adLodsDeferredTask = Regex(
        "\\[(?:main|Render thread)/WARN] \\[net\\.minecraftforge\\.fml\\.DeferredWorkQueue]: " +
            "Mod 'adlods' took ([0-9]+(?:\\.[0-9]+)?) s to run a deferred task\\.$",
    )
    private val presenceFootstepsMissingMessyGroundAcoustic = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[Render thread/WARN] \\[PFSolver]: " +
            "Tried to play a missing acoustic: MESSY_GROUND$",
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
    private val distantHorizonsInsufficientMemory = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] " +
            "\\[DH-(?:LOD Builder|Render Loader|Update Propagator) Thread\\[[0-9]+]/WARN] " +
            "\\[DistantHorizons-DistantHorizons-com\\.seibel\\.distanthorizons\\.core\\.pooling\\." +
            "PhantomArrayListPool]: §6Distant Horizons: Insufficient memory detected\\.§r$",
    )
    private val distantHorizonsRatlantisLightingError = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] " +
            "\\[DH-LOD Builder Thread\\[0]/ERROR] " +
            "\\[DistantHorizons-DistantHorizons-com\\.seibel\\.distanthorizons\\.core\\.generation\\." +
            "DhLightingEngine]: Unexpected lighting issue for center chunk: C\\[0,-1]$",
    )
    private val distantHorizonsRatlantisLevelClosed = Regex(
        "Closed \\[DhClientLevel] for \\[Wrapped\\{ClientLevel@[^}]+@rats:ratlantis\\}\\]",
    )
    private val emptySalmonAmbientSound = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[Render thread/WARN] " +
            "\\[net\\.minecraft\\.client\\.sounds\\.SoundEngine]: " +
            "Unable to play empty soundEvent: minecraft:entity\\.salmon\\.ambient$",
    )
    private val emptyCodAmbientSound = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[Render thread/WARN] " +
            "\\[net\\.minecraft\\.client\\.sounds\\.SoundEngine]: " +
            "Unable to play empty soundEvent: minecraft:entity\\.cod\\.ambient$",
    )
    private val emptyTropicalFishAmbientSound = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[Render thread/WARN] " +
            "\\[net\\.minecraft\\.client\\.sounds\\.SoundEngine]: " +
            "Unable to play empty soundEvent: minecraft:entity\\.tropical_fish\\.ambient$",
    )
    private val emptyPufferFishAmbientSound = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[Render thread/WARN] " +
            "\\[net\\.minecraft\\.client\\.sounds\\.SoundEngine]: " +
            "Unable to play empty soundEvent: minecraft:entity\\.puffer_fish\\.ambient$",
    )
    private val emptyUntamedPlaceholderSound = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[Render thread/WARN] " +
            "\\[net\\.minecraft\\.client\\.sounds\\.SoundEngine]: " +
            "Unable to play empty soundEvent: untamedwilds:nothing$",
    )
    private val emptyQuarkCrabIdleSound = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[Render thread/WARN] " +
            "\\[net\\.minecraft\\.client\\.sounds\\.SoundEngine]: " +
            "Unable to play empty soundEvent: quark:entity\\.crab\\.idle$",
    )
    private val slowGlfwInitializationDiagnostic = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[main/ERROR] \\[EARLYDISPLAY]: WARNING : " +
            "glfwInit took ([0-9]+\\.[0-9]+) seconds to start\\.",
    )
    private val allTheLeaksServerNotFound = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[Render thread/WARN] \\[AllTheLeaks]: " +
            "Server not found while trying to clear leaked chunks$",
    )
    // Aether's bundled Curios override deliberately replaces these six Artifacts tags.
    private val aetherCuriosTagOverride = Regex(
        "\\[([0-9]{2}:[0-9]{2}:[0-9]{2})] \\[(Worker-(Main|ResourceReload)-[0-9]+)/WARN] \\[artifacts\\.Artifacts]: " +
            "Tag entries for curios:tags/items/aether_(accessory|cape|gloves|pendant|ring|shield)\\.json " +
            "cleared by aether-1\\.20\\.1-1\\.5\\.2-neoforge\\.jar:packs/curios_override$",
    )
    // The pack deliberately replaces the ordinary back tag so backpacks only use better_backpack.
    private val curatedCuriosBackTagOverride = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[Worker-(Main|ResourceReload)-[0-9]+/WARN] " +
            "\\[artifacts\\.Artifacts]: Tag entries for curios:tags/items/back\\.json " +
            "cleared by KubeJS Resource Pack \\[data]$",
    )
    private val invalidImmersiveWeatheringIcicle = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[Server thread/WARN] " +
            "\\[net\\.minecraft\\.world\\.level\\.chunk\\.LevelChunk]: " +
            "Block entity minecraft:mob_spawner @ BlockPos\\{x=-?[0-9]+, y=-?[0-9]+, z=-?[0-9]+} " +
            "state Block\\{immersive_weathering:icicle}\\[thickness=tip,vertical_direction=down," +
            "waterlogged=false] invalid for ticking:$",
    )
    private val deepVoidPhysicsFallback = Regex(
        "\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[Server thread/WARN] " +
            "\\[xbigellx\\.realisticphysics\\.RealisticPhysics]: " +
                "Level null when loading chunk at '\\[-3, -3]' for dimension 'the_deep_void:deep_void'\\.",
    )

    data class Finding(val path: Path, val line: Int, val text: String)

    private fun acceptedRatlantisLightingErrorLines(path: Path, lines: List<String>): Set<Int> {
        val pathText = path.toString()
        val clientLog = ("/multiplayer/" in pathText || "/target-font-ratlantis/" in pathText) &&
            ("/fixture/client-" in pathText || path.fileName.toString().matches(Regex("client-[0-9]+\\.log")))
        if (!clientLog) return emptySet()

        val acceptedLines = mutableSetOf<Int>()
        var acceptedOneTransition = false
        lines.forEachIndexed { index, line ->
            if (acceptedOneTransition || !distantHorizonsRatlantisLightingError.matches(line)) return@forEachIndexed
            if (lines.getOrNull(index + 1) != "net.minecraft.ReportedException: Getting block state") {
                return@forEachIndexed
            }
            val recentLines = lines.subList((index - 20).coerceAtLeast(0), index)
            val stackLines = lines.subList(index + 2, (index + 24).coerceAtMost(lines.size))
            val closedRatlantisLevel = recentLines.any(distantHorizonsRatlantisLevelClosed::containsMatchIn)
            val blockStateReadStack = stackLines.any {
                "at net.minecraft.world.level.chunk.LevelChunk.m_8055_(LevelChunk.java:182)" in it
            }
            val dhLightingStack = stackLines.any { "DhLightingEngine.lightChunk(DhLightingEngine.java:260)" in it }
            val missingPaletteCause = stackLines.any {
                it == "Caused by: net.minecraft.world.level.chunk.MissingPaletteEntryException: " +
                    "Missing Palette entry for index 2."
            }
            if (closedRatlantisLevel && blockStateReadStack && dhLightingStack && missingPaletteCause) {
                acceptedLines += index
                acceptedLines += index + 1
                acceptedOneTransition = true
            }
        }
        return acceptedLines
    }

    fun findings(paths: Collection<Path>): List<Finding> = buildList {
        paths.filter { Files.isRegularFile(it) }.forEach { path ->
            val seededWorldLog = path.toString().let { "/singleplayer/" in it || "/target-world-save/" in it }
            val multiplayerLog = "/multiplayer/" in path.toString()
            val multiplayerAggregateServerLog = multiplayerLog && path.parent.fileName.toString() == "multiplayer"
            val multiplayerServerLog = multiplayerAggregateServerLog ||
                (multiplayerLog && path.fileName.toString() == "latest.log" &&
                    "/fixture/server-extract/" in path.toString())
            val targetDimensionsServerLog = "/target-dimensions/" in path.toString() &&
                (path.fileName.toString() == "server.log" ||
                    (path.fileName.toString() == "latest.log" && "/fixture/server-extract/" in path.toString()))
            val serverLifecycleAggregateLog = path.fileName.toString() == "server.log" &&
                path.parent.fileName.toString() == "server"
            val singleplayerLog = "/singleplayer/" in path.toString()
            val multiplayerClientLog = multiplayerLog && (
                "/fixture/client-" in path.toString() ||
                    path.fileName.toString().matches(Regex("client-[0-9]+\\.log"))
            )
            val lines = Files.readAllLines(path)
            val acceptedRatlantisLightingErrors = acceptedRatlantisLightingErrorLines(path, lines)
            var acceptedSeededWorldRemaps = 0
            var acceptedSeededWorldVersionDifferences = 0
            var acceptedSeededWorldMissingDatapacks = 0
            var acceptedEmptyExplosionIndexes = 0
            var acceptedSingleplayerMissingC2me = 0
            var acceptedSingleplayerOpenAlCleanup = 0
            var jeiRegisteringIngredients = false
            var acceptedUntamedJeiPrototypeWarnings = 0
            var acceptedUntamedServerMissingSpeciesWarnings = 0
            var acceptedEarlyBlockEntityWarnings = 0
            var acceptedRecoveredPhantomArrays = 0
            var acceptedDistantHorizonsInsufficientMemory = 0
            var acceptedAdChimneysDeferredTasks = 0
            var acceptedAdLodsDeferredTasks = 0
            var acceptedEmptySalmonAmbientSounds = 0
            var acceptedEmptyTropicalFishAmbientSounds = 0
            var acceptedEmptyPufferFishAmbientSounds = 0
            var acceptedEmptyCodAmbientSounds = 0
            var acceptedEmptyUntamedPlaceholderSounds = 0
            var acceptedEmptyQuarkCrabIdleSounds = 0
            var acceptedSlowGlfwInitializationDiagnostics = 0
            var acceptedAllTheLeaksServerNotFound = 0
            val acceptedAetherCuriosBatches = mutableMapOf<Pair<String, String>, MutableSet<String>>()
            var acceptedCuratedCuriosBackOverrides = 0
            var acceptedInvalidImmersiveWeatheringIcicles = 0
            var acceptedDeepVoidPhysicsFallbacks = 0
            var acceptedAdPotherDeferredTasks = 0
            var acceptedPresenceFootstepsMissingMessyGroundAcoustics = 0
            var acceptedStarcatcherInvalidAccessTransformers = 0
            var acceptedCampaignItemFrameIronSwords = 0
            var acceptedCampaignItemFrameIronAxes = 0
            var acceptedCampaignUnknownStepHeightAttributes = 0
            var acceptedIceAndFireDeferredTasks = 0
            var acceptedCollectiveUpdateNotices = 0
            lines.forEachIndexed { index, line ->
                val acceptedSeededWorldWarning = seededWorldLog && when {
                    line.contains("[net.minecraftforge.common.ForgeHooks]: The following mods have version differences that were not resolved:") &&
                        lines.getOrNull(index + 1)?.trim() == "unloaded_activity (version 0.6.3 -> MISSING)" ->
                        ++acceptedSeededWorldVersionDifferences <= 1
                    seededWorldRegistryRemap.matches(line) -> ++acceptedSeededWorldRemaps <= 7
                    line.endsWith("[net.minecraft.server.MinecraftServer]: Missing data pack mod:unloaded_activity") ->
                        ++acceptedSeededWorldMissingDatapacks <= 1
                    line.endsWith("[com.vinlanx.explosionoverhaul.ExplosionOverhaul]: BlockIndexManager: No data to save - index is empty!") ->
                        ++acceptedEmptyExplosionIndexes <= 1
                    line.contains("[DistantHorizons-LOD World Gen - Internal Server]: C2ME missing,") ->
                        ++acceptedSingleplayerMissingC2me <= 1
                    line.matches(Regex("\\[[0-9]{2}:[0-9]{2}:[0-9]{2}] \\[Render thread/ERROR] " +
                        "\\[com\\.mojang\\.blaze3d\\.audio\\.OpenAlUtil]: Cleanup: Invalid name parameter\\.")) &&
                        lines.subList((index - 5).coerceAtLeast(0), index)
                            .any { "[mezz.jei.forge.plugins.forge.ForgeGuiPlugin]: Stopping JEI GUI" in it } ->
                        ++acceptedSingleplayerOpenAlCleanup <= 1
                    else -> false
                }
                if (line.contains("[mezz.jei.library.load.PluginCaller]: Registering ingredients...")) {
                    jeiRegisteringIngredients = true
                }
                if (line.contains("[mezz.jei.library.load.PluginCaller]: Registering ingredients took ")) {
                    jeiRegisteringIngredients = false
                }
                val acceptedUntamedJeiPrototype = jeiRegisteringIngredients &&
                    untamedJeiPrototypeWarning.matches(line) && ++acceptedUntamedJeiPrototypeWarnings <= 3400
                val acceptedUntamedServerMissingSpecies = multiplayerServerLog &&
                    untamedServerMissingSpeciesWarning.matches(line) &&
                    ++acceptedUntamedServerMissingSpeciesWarnings <= 1
                val acceptedEarlyBlockEntity = earlyWorldgenBlockEntity.matches(line) &&
                    ++acceptedEarlyBlockEntityWarnings <= 4
                val acceptedRecoveredPhantomArray = distantHorizonsRecoveredPhantomArray.matches(line) &&
                    ++acceptedRecoveredPhantomArrays <= 1
                val acceptedDistantHorizonsMemoryWarning = distantHorizonsInsufficientMemory.matches(line) &&
                    ++acceptedDistantHorizonsInsufficientMemory <= 1
                val acceptedEmptySalmonAmbientSound = emptySalmonAmbientSound.matches(line) &&
                    ++acceptedEmptySalmonAmbientSounds <= 1
                val acceptedEmptyTropicalFishAmbientSound = emptyTropicalFishAmbientSound.matches(line) &&
                    ++acceptedEmptyTropicalFishAmbientSounds <= 1
                val acceptedEmptyPufferFishAmbientSound = emptyPufferFishAmbientSound.matches(line) &&
                    ++acceptedEmptyPufferFishAmbientSounds <= 1
                val acceptedEmptyCodAmbientSound = emptyCodAmbientSound.matches(line) &&
                    ++acceptedEmptyCodAmbientSounds <= 1
                val acceptedEmptyUntamedPlaceholderSound = emptyUntamedPlaceholderSound.matches(line) &&
                    ++acceptedEmptyUntamedPlaceholderSounds <= 1
                val acceptedEmptyQuarkCrabIdleSound = emptyQuarkCrabIdleSound.matches(line) &&
                    ++acceptedEmptyQuarkCrabIdleSounds <= 1
                val slowGlfwInitializationSeconds =
                    slowGlfwInitializationDiagnostic.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                val acceptedSlowGlfwInitializationDiagnostic = multiplayerClientLog &&
                    slowGlfwInitializationSeconds != null && slowGlfwInitializationSeconds > 1.0 &&
                    ++acceptedSlowGlfwInitializationDiagnostics <= 1
                val acceptedAllTheLeaksWarning = allTheLeaksServerNotFound.matches(line) &&
                    ++acceptedAllTheLeaksServerNotFound <= 1
                val aetherCuriosMatch = aetherCuriosTagOverride.matchEntire(line)
                val acceptedAetherCuriosOverride = aetherCuriosMatch != null && run {
                    val groups = aetherCuriosMatch.groupValues
                    val batch = groups[1] to groups[2]
                    val tags = acceptedAetherCuriosBatches.getOrPut(batch) { mutableSetOf() }
                    acceptedAetherCuriosBatches.size <= (if (serverLifecycleAggregateLog) 3 else 2) &&
                        tags.add(groups[4]) && tags.size <= 6
                }
                val acceptedCuratedCuriosBackOverride = curatedCuriosBackTagOverride.matches(line) &&
                    ++acceptedCuratedCuriosBackOverrides <= 3
                val acceptedInvalidImmersiveWeatheringIcicle = invalidImmersiveWeatheringIcicle.matches(line) &&
                    ++acceptedInvalidImmersiveWeatheringIcicles <= 1
                val acceptedDeepVoidPhysicsFallback = deepVoidPhysicsFallback.matches(line) &&
                    ++acceptedDeepVoidPhysicsFallbacks <= 9
                val acceptedPresenceFootstepsMissingMessyGroundAcoustic =
                    presenceFootstepsMissingMessyGroundAcoustic.matches(line) &&
                        ++acceptedPresenceFootstepsMissingMessyGroundAcoustics <= 1
                val acceptedStarcatcherInvalidAccessTransformer =
                    starcatcherInvalidAccessTransformer.matches(line) &&
                        ++acceptedStarcatcherInvalidAccessTransformers <=
                        (if (serverLifecycleAggregateLog) 3 else 2)
                val acceptedCampaignItemFrameIronSword = (multiplayerLog || serverLifecycleAggregateLog) &&
                    campaignItemFrameIronSword.matches(line) && ++acceptedCampaignItemFrameIronSwords <= 1
                val acceptedCampaignItemFrameIronAxe = (multiplayerLog || serverLifecycleAggregateLog) &&
                    campaignItemFrameIronAxe.matches(line) && ++acceptedCampaignItemFrameIronAxes <= 1
                val acceptedCampaignUnknownStepHeightAttribute =
                    (multiplayerAggregateServerLog || targetDimensionsServerLog) &&
                    campaignUnknownStepHeightAttribute.matches(line) &&
                    ++acceptedCampaignUnknownStepHeightAttributes <= 2
                val iceAndFireDuration = iceAndFireDeferredTask.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                val acceptedIceAndFireDeferredTask = (singleplayerLog || multiplayerClientLog) &&
                    iceAndFireDuration != null &&
                    iceAndFireDuration <= 2.0 && ++acceptedIceAndFireDeferredTasks <= 1
                val acceptedCollectiveUpdateNotice =
                    (multiplayerServerLog || multiplayerClientLog || serverLifecycleAggregateLog || singleplayerLog) &&
                        collectiveUpdateNotice.matches(line) && ++acceptedCollectiveUpdateNotices <=
                        (if (serverLifecycleAggregateLog) 2 else 1)
                val adPotherDuration = adPotherDeferredTask.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                val acceptedAdPotherDeferredTask = adPotherDuration != null && adPotherDuration <= 60.0 &&
                    ++acceptedAdPotherDeferredTasks <= 1
                val adChimneysDuration = adChimneysDeferredTask.find(line)
                    ?.groupValues?.get(1)?.toDoubleOrNull()
                val acceptedAdChimneysDeferredTask = adChimneysDuration != null &&
                    adChimneysDuration <= 45.0 && ++acceptedAdChimneysDeferredTasks <= 1
                val adLodsDuration = adLodsDeferredTask.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                val acceptedAdLodsDeferredTask = adLodsDuration != null &&
                    adLodsDuration <= 5.0 && ++acceptedAdLodsDeferredTasks <= 1
                if ((fatal.containsMatchIn(line) || warningOrError.containsMatchIn(line)) &&
                    !acceptedEarlyBlockEntity && !acceptedRecoveredPhantomArray &&
                    !acceptedDistantHorizonsMemoryWarning &&
                    !acceptedAdChimneysDeferredTask && !acceptedAdLodsDeferredTask &&
                    !acceptedEmptySalmonAmbientSound &&
                    !acceptedEmptyTropicalFishAmbientSound && !acceptedEmptyPufferFishAmbientSound &&
                    !acceptedEmptyCodAmbientSound && !acceptedEmptyUntamedPlaceholderSound &&
                    !acceptedEmptyQuarkCrabIdleSound &&
                    !acceptedSlowGlfwInitializationDiagnostic &&
                    !acceptedAllTheLeaksWarning && !acceptedAetherCuriosOverride &&
                    !acceptedCuratedCuriosBackOverride &&
                    !acceptedInvalidImmersiveWeatheringIcicle &&
                    !acceptedDeepVoidPhysicsFallback && !acceptedPresenceFootstepsMissingMessyGroundAcoustic &&
                    !acceptedStarcatcherInvalidAccessTransformer && !acceptedCampaignItemFrameIronSword &&
                    !acceptedCampaignItemFrameIronAxe && !acceptedCampaignUnknownStepHeightAttribute &&
                    !acceptedUntamedServerMissingSpecies &&
                    !acceptedIceAndFireDeferredTask &&
                    !acceptedCollectiveUpdateNotice &&
                    !acceptedAdPotherDeferredTask && !acceptedUntamedJeiPrototype &&
                    !acceptedSeededWorldWarning && index !in acceptedRatlantisLightingErrors && !isAccepted(line)
                ) {
                    add(Finding(path, index + 1, line))
                }
            }
        }
    }

    private fun isAccepted(line: String): Boolean {
        return accepted.any { it.containsMatchIn(line) }
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
