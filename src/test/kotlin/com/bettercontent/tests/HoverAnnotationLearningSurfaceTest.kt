package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

@Tag("fast")
class HoverAnnotationLearningSurfaceTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val registry = jacksonObjectMapper().readTree(root.resolve("kubejs/config/hover_annotations.json").toFile())

    @Test
    fun `every annotation has a bounded stable concept and concise copy`() {
        assertEquals("bc.hover_annotations.v2", registry.path("schema").asText())
        val rows = registry.path("annotations")
        val allowedCategories = setOf(
            "correction", "lifecycle_state", "hidden_composition", "general_uses",
            "capability_root", "process_authority", "operation_contract", "requirement_limit",
            "provenance", "scope_boundary", "persistence_consequence", "economy_semantics",
            "combat_handling",
        )
        val exactTargets = mutableListOf<String>()
        rows.forEach { row ->
            assertTrue(row.path("concept_id").asText().matches(Regex("[a-z0-9_.]{3,96}")))
            assertTrue(row.path("category").asText() in allowedCategories, row.toString())
            assertTrue(row.path("domain").asText().matches(Regex("[a-z0-9_.]{3,96}")))
            assertTrue(row.path("owner").asText().isNotBlank())
            val selector = row.path("selector")
            val selectorKinds = listOf("item", "items", "tag").count(selector::has)
            assertEquals(1, selectorKinds, row.toString())
            selector.path("item").takeIf { !it.isMissingNode }?.asText()?.let {
                assertTrue(it.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")), row.toString())
                exactTargets += it
            }
            selector.path("items").takeIf { it.isArray }?.map { it.asText() }?.let { items ->
                assertTrue(items.isNotEmpty(), row.toString())
                assertEquals(items.size, items.distinct().size, row.toString())
                assertTrue(items.all { it.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")) }, row.toString())
                exactTargets += items
            }
            selector.path("tag").takeIf { !it.isMissingNode }?.asText()?.let {
                assertTrue(it.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")), row.toString())
            }
            val lines = row.path("lines").map { it.asText() }
            assertTrue(lines.size in 1..2)
            assertTrue(lines.joinToString(" ").trim().split(Regex("\\s+")).size <= 24)
        }
        assertEquals(exactTargets.size, exactTargets.distinct().size, "duplicate exact item selectors")
    }

    @Test
    fun `every path-backed annotation owner and evidence reference resolves`() {
        registry.path("annotations").forEach { row ->
            row.path("owner").asText().split(';').map(String::trim).forEach { owner ->
                if (owner.contains('/')) {
                    assertTrue(
                        Files.exists(root.resolve(owner).normalize()),
                        "${row.path("concept_id").asText()} points to missing owner $owner",
                    )
                }
            }
            row.path("evidence").takeIf { it.isArray }?.forEach { evidence ->
                val path = evidence.asText()
                assertTrue(
                    Files.exists(root.resolve(path).normalize()),
                    "${row.path("concept_id").asText()} points to missing evidence $path",
                )
            }
        }
    }

    @Test
    fun `chunk anchor hovers match variant inputs shared area and redstone pause`() {
        val sourceRoot = root.resolve("../mod_source/arcane-chunk-loaders/src/main/java/com/bettercontent/arcanechunkloaders").normalize()
        val shared = Files.readString(sourceRoot.resolve("blockentity/ArcaneAnchorBlockEntity.java"))
        val anchorData = Files.readString(sourceRoot.resolve("blockentity/AnchorData.java"))
        val kineticBlock = Files.readString(sourceRoot.resolve("block/KineticAnchorBlock.java"))
        val kineticEntity = Files.readString(sourceRoot.resolve("blockentity/KineticAnchorBlockEntity.java"))
        val math = Files.readString(sourceRoot.resolve("AnchorMath.java"))
        val sourceRows = registry.path("annotations").filter { it.path("selector").path("item").asText().contains("chunk_anchor") }
        assertEquals(7, sourceRows.size)

        val expectations = mapOf(
            "arcane_chunk_loaders:flux_chunk_anchor" to listOf("Accepts FE/RF from any side."),
            "arcane_chunk_loaders:kinetic_chunk_anchor" to listOf("Accepts Create rotation through its vertical shaft."),
            "arcane_chunk_loaders:source_chunk_anchor" to listOf("Accepts Source through its Ars Source interface."),
            "arcane_chunk_loaders:lifeforce_chunk_anchor" to listOf("Accepts Blood Magic life essence from any side."),
            "arcane_chunk_loaders:pressure_chunk_anchor" to listOf("Accepts PneumaticCraft air through pressure tubes."),
            "arcane_chunk_loaders:soul_chunk_anchor" to listOf("Sneak-use empty-handed to transfer Goety soul energy."),
            "arcane_chunk_loaders:spirit_chunk_anchor" to listOf("Accepts Malum spirits manually or through automation."),
        )
        expectations.forEach { (id, expectedLines) ->
            val row = sourceRows.single { it.path("selector").path("item").asText() == id }
            val owner = row.path("owner").asText()
            assertTrue(owner.contains("ArcaneAnchorBlockEntity.java") || owner.contains("KineticAnchorBlockEntity.java"))
            val copy = row.path("lines").map { it.asText() }
            expectedLines.forEach { expected -> assertTrue(copy.contains(expected), "$id missing $expected") }
            assertTrue(copy.contains("Loads and ticks centered 3×3; redstone disables loading."), id)
        }

        assertTrue(shared.contains("cap == ForgeCapabilities.ENERGY"))
        assertTrue(shared.contains("cap == ForgeCapabilities.FLUID_HANDLER"))
        assertTrue(shared.contains("cap == PNCCapabilities.AIR_HANDLER_CAPABILITY"))
        assertTrue(shared.contains("cap == ForgeCapabilities.ITEM_HANDLER"))
        assertTrue(shared.contains("implements AnchorAccess, ISourceTile, IAirHandler"))
        assertTrue(shared.contains("player.isShiftKeyDown() && held.isEmpty() && variant == AnchorVariant.SOUL"))
        assertTrue(shared.contains("variant == AnchorVariant.SPIRIT && !held.isEmpty() && held.is(MALUM_SPIRITS)"))
        assertTrue(kineticBlock.contains("Direction.Axis.Y"))
        assertTrue(kineticEntity.contains("Math.abs(getSpeed()) >= AnchorConfig.KINETIC_MIN_RPM.get()"))
        assertTrue(anchorData.contains("AnchorMath.centered3x3()"))
        assertTrue(anchorData.contains("if (!redstoneDisabled) active = owner.consumePower(level.getGameTime())"))
        assertTrue(math.contains("for (int x = -1; x <= 1; x++)") && math.contains("for (int z = -1; z <= 1; z++)"))
    }

    @Test
    fun `schematicannon hover matches per cannon persistence and native first fallback`() {
        val sourceRoot = root.resolve("../mod_source/world-lifecycle-manager/src/main/java/com/bettercontent/worldlifecyclemanager").normalize()
        val substitutions = Files.readString(sourceRoot.resolve("SchematicannonSubstitutions.java"))
        val mixin = Files.readString(sourceRoot.resolve("mixin/SchematicannonBlockEntityMixin.java"))
        val row = registry.path("annotations").single {
            it.path("selector").path("item").asText() == "create:schematicannon"
        }
        val owner = row.path("owner").asText()
        val copy = row.path("lines").map { it.asText() }.single()

        assertTrue(owner.contains("SchematicannonSubstitutions.java"))
        assertTrue(owner.contains("SchematicannonBlockEntityMixin.java"))
        assertEquals("Remembers substitutions per cannon. Uses original schematic materials before substitutes.", copy)
        assertTrue(mixin.contains("private final LinkedHashMap<ResourceLocation, ResourceLocation> worldLifecycleManager\$rules"))
        assertTrue(mixin.contains("tag.put(WORLD_LIFECYCLE_MANAGER_RULES, list)"))
        assertTrue(mixin.contains("if (cannon.hasCreativeCrate || SchematicannonSubstitutions.available(cannon, originalStack)) return original"))
        assertTrue(mixin.contains("< reservedForNativeBlocks + replacementStack.stack.getCount()) return original"))
        assertTrue(substitutions.contains("public static void validateRule"))
    }

    @Test
    fun `lineage sync and world condenser hovers match collision and reset boundaries`() {
        val sourceRoot = root.resolve("../mod_source/world-lifecycle-manager/src/main/java/com/bettercontent/worldlifecyclemanager").normalize()
        val downloadStore = Files.readString(sourceRoot.resolve("SchematicDownloadStore.java"))
        val interfaceBlock = Files.readString(sourceRoot.resolve("WorldCondenserInterfaceBlock.java"))
        val prestigeService = Files.readString(sourceRoot.resolve("PrestigeService.java"))
        val downloadRow = registry.path("annotations").single {
            it.path("selector").path("item").asText() == "create:empty_schematic"
        }
        val condenserRow = registry.path("annotations").single {
            it.path("selector").path("item").asText() == "world_lifecycle_manager:world_condenser_interface"
        }

        assertTrue(downloadRow.path("owner").asText().contains("SchematicDownloadStore.java"))
        assertEquals("Published Lineage plans sync here without overwriting local edits.", downloadRow.path("lines").single().asText())
        assertTrue(downloadStore.contains("if (!digest.equals(expectedHash)) throw new IOException(\"download hash mismatch\")"))
        assertTrue(downloadStore.contains("return write(alternate, data)"))
        assertTrue(downloadStore.contains("Files.write(target, data, StandardOpenOption.CREATE_NEW)"))
        assertTrue(downloadStore.contains("if (hash(Files.readAllBytes(primary)).equals(digest)) return new Result(Status.PRESENT, primary)"))

        val condenserOwners = condenserRow.path("owner").asText()
        assertTrue(condenserOwners.contains("WorldCondenserInterfaceBlock.java"))
        assertTrue(condenserOwners.contains("PrestigeService.java"))
        val condenserCopy = condenserRow.path("lines").map { it.asText() }
        assertEquals(listOf("Only server operators can use this interface.", "Permanently resets the active world through a staged process."), condenserCopy)
        assertTrue(interfaceBlock.contains("serverPlayer.hasPermissions(4)"))
        assertTrue(interfaceBlock.contains("supportsPrestigeReset(serverPlayer.server)"))
        assertTrue(prestigeService.contains("public static void stage(MinecraftServer server)"))
        assertTrue(prestigeService.contains("public static String commit(MinecraftServer server)"))
        assertTrue(prestigeService.contains("no staged prestige request exists"))
        assertTrue(prestigeService.contains("PrestigeCoordinator.scheduleStop()"))
    }

    @Test
    fun `hosted uranium and thorium hover matches inert worldgen and active lifecycle`() {
        val base = "../mod_source/latent-chemlib/src/main/"
        val formsPath = base + "resources/data/latent_chemlib/nuclear_forms/realistic_ores.json"
        val forms = jacksonObjectMapper().readTree(root.resolve(formsPath).normalize().toFile()).path("forms")
        val lifecycle = Files.readString(root.resolve(base + "java/com/bettercontent/latentchemlib/sim/PlacedNuclearLifecycle.java").normalize())
        val scanner = Files.readString(root.resolve(base + "java/com/bettercontent/latentchemlib/sim/NuclearSurfaceScanner.java").normalize())
        val simulation = Files.readString(root.resolve(base + "java/com/bettercontent/latentchemlib/sim/NuclearSimulationService.java").normalize())
        assertTrue(lifecycle.contains("if (fixed.get().form().naturalWorldgenInert())"))
        assertTrue(lifecycle.contains("trackDisturbed(level, event.getPos(), fixed.get())"))
        assertTrue(lifecycle.contains("RADIOACTIVE_ACTIVATED"))
        assertTrue(lifecycle.contains("NuclearSurfaceScanner.unmarkPlaced(level, event.getPos())"))
        assertTrue(scanner.contains("emitFixedProfile(level, pos, resolved.get().form(), 1)"))
        assertTrue(scanner.contains("processPlayerStack"))
        assertTrue(simulation.contains("RadioactiveFormResolver.INSTANCE.resolve(stack)"))

        listOf("uranium", "thorium").forEach { family ->
            val tag = "realistic_ores:radioactive_forms/$family/hosted_ore_blocks"
            val row = registry.path("annotations").single { it.path("selector").path("tag").asText() == tag }
            val copy = row.path("lines").map { it.asText() }.joinToString(" ")
            val form = forms.single { it.path("item_tag").asText() == tag }
            assertTrue(form.path("natural_worldgen_inert").asBoolean())
            assertTrue(form.path("placed_always_active").asBoolean())
            assertTrue(form.path("radiation_strength").asDouble() > 0.0)
            assertTrue(form.path("heat_strength").asDouble() > 0.0)
            assertTrue(row.path("owner").asText().contains("PlacedNuclearLifecycle.java"))
            assertTrue(row.path("owner").asText().contains("NuclearSimulationService.java"))
            assertTrue(copy.contains("Natural hosted $family is inert; mining or replacing it activates its radiation and heat."))
        }
    }

    @Test
    fun `affixed part cache hover follows dimension origin and reward production`() {
        val base = "../mod_source/tinkers-construct-affixes/src/main/kotlin/com/bettercontent/tinkersconstructaffixes/"
        val item = Files.readString(root.resolve(base + "AffixItems.kt").normalize())
        val origins = Files.readString(root.resolve(base + "AffixOrigins.kt").normalize())
        val rewards = Files.readString(root.resolve(base + "TConAffixRewards.kt").normalize())
        val row = registry.path("annotations").single { it.path("selector").path("item").asText() == "tinkers_construct_affixes:affixed_part_cache" }
        val copy = row.path("lines").map { it.asText() }.joinToString(" ")
        assertTrue(item.contains("AffixOrigins.fromDimension(level.dimension().location())"))
        assertTrue(item.contains("TConAffixRewards.rollAffixedPart(level.random, origin, \"cache\")"))
        assertTrue(item.contains("held.shrink(1)"))
        assertTrue(origins.contains("fun fromDimension(dimension: ResourceLocation): AffixOrigin"))
        assertTrue(rewards.contains("weightedPick(candidates, random)"))
        assertTrue(rewards.contains("candidate.item.withMaterial(material.identifier)"))
        assertTrue(rewards.contains("writeToolAffixes(stack, affixes)"))
        listOf("AffixItems.kt", "AffixOrigins.kt", "TConAffixRewards.kt").forEach { assertTrue(row.path("owner").asText().contains(it)) }
        assertTrue(copy.contains("one randomly affixed tool part"))
        assertTrue(copy.contains("origins depend on the dimension"))
    }

    @Test
    fun `Dynamic Trees seed hover follows the pinned planting contract`() {
        val pin = Files.readString(root.resolve("mods/dynamictrees.pw.toml"))
        val row = registry.path("annotations").single { it.path("selector").path("tag").asText() == "dynamictrees:seeds" }
        val copy = row.path("lines").map { it.asText() }.joinToString(" ")
        val auditPath = row.path("evidence").single().asText()
        val audit = Files.readString(root.resolve(auditPath).normalize())
        assertTrue(pin.contains("filename = \"DynamicTrees-1.20.1-1.4.10.jar\""))
        assertTrue(pin.contains("file-id = 7502488"))
        assertTrue(row.path("owner").asText().contains("config/dynamictrees-common.toml"))
        assertTrue(audit.contains("Seed#doPlanting"))
        assertTrue(audit.contains("plantSapling"))
        assertTrue(audit.contains("biomeSuitability"))
        assertTrue(audit.contains("No stump lookup or stump-targeted planting occurs"))
        assertTrue(copy.contains("suitable soil and biome"))
        assertTrue(!copy.contains("stump"))
    }

    @Test
    fun `Soulspring Lamp guidance follows configured fuel and Nether restriction`() {
        val config = Files.readString(root.resolve("config/coldsweat/item.toml"))
        val row = registry.path("annotations").single { it.path("selector").path("item").asText() == "cold_sweat:soulspring_lamp" }
        val copy = row.path("lines").map { it.asText() }.joinToString(" ")
        val section = config.substringAfter("[\"Soulspring Lamp\"]").substringBefore("[Insulation]")
        assertTrue(section.contains("[\"cold_sweat:soul_sprout\", 4]"))
        assertTrue(section.contains("\"minecraft:the_nether\""))
        assertTrue(row.path("owner").asText().contains("config/coldsweat/item.toml"))
        assertTrue(copy.contains("Nether only"))
        assertTrue(copy.contains("each Soul Sprout supplies four fuel units"))
    }

    @Test
    fun `Dragonsteel hover equality matches all three active Tinkers material stats`() {
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("items").any { it.asText() == "iceandfire:dragonsteel_fire_ingot" }
        }
        val variants = listOf("fire", "ice", "lightning")
        val stats = variants.map { variant ->
            val path = "kubejs/data/kubejs/tinkering/materials/stats/dragonsteel_$variant.json"
            assertTrue(row.path("owner").asText().contains(path))
            val recipePath = "kubejs/data/kubejs/recipes/tconstruct/materials/dragonsteel_$variant.json"
            assertTrue(row.path("owner").asText().contains(recipePath))
            val recipe = jacksonObjectMapper().readTree(root.resolve(recipePath).toFile())
            assertEquals("iceandfire:dragonsteel_${variant}_ingot", recipe.path("ingredient").path("item").asText())
            jacksonObjectMapper().readTree(root.resolve(path).toFile()).path("stats")
        }
        val conventional = Files.readString(root.resolve("kubejs/server_scripts/policy/conventional_tool_authority.js"))
        listOf("dragonsteel_fire", "dragonsteel_ice", "dragonsteel_lightning").forEach { material ->
            assertTrue(conventional.contains("'$material'"))
        }
        assertEquals(stats.first(), stats[1])
        assertEquals(stats.first(), stats[2])
        assertEquals(1500, stats.first().path("tconstruct:head").path("durability").asInt())
        assertEquals(8.0, stats.first().path("tconstruct:head").path("mining_speed").asDouble())
        assertEquals(3.0, stats.first().path("tconstruct:head").path("melee_attack").asDouble())
        assertEquals("minecraft:netherite", stats.first().path("tconstruct:head").path("mining_tier").asText())
        assertTrue(row.path("lines").map { it.asText() }.joinToString(" ").contains("equal stats across variants"))
    }

    @Test
    fun `powered train fuel guidance follows the active speed and stopped-cost policy`() {
        val policy = Files.readString(root.resolve("config/create_train_fuel_scaling-common.toml"))
        val row = registry.path("annotations").single { it.path("selector").path("item").asText() == "create:controls" }
        val copy = row.path("lines").map { it.asText() }.joinToString(" ")
        assertTrue(policy.contains("baseCost = 1.0"))
        assertTrue(policy.contains("k = 2.0"))
        assertTrue(policy.contains("consumeWhileStopped = false"))
        assertTrue(row.path("owner").asText().contains("create_train_fuel_scaling-common.toml"))
        assertTrue(copy.contains("rises sharply with speed"))
        assertTrue(copy.contains("stopped trains do not drain fuel"))
    }

    @Test
    fun `player traces probe annotation uses its registered item namespace`() {
        val mod = Files.readString(root.resolve("../mod_source/player-traces/src/main/kotlin/com/bettercontent/playertraces/TracesMod.kt").normalize())
        val items = Files.readString(root.resolve("../mod_source/player-traces/src/main/kotlin/com/bettercontent/playertraces/item/TracesItems.kt").normalize())
        assertTrue(mod.contains("const val MOD_ID = \"player_traces\""))
        assertTrue(items.contains("REGISTRY.register(\"foot_traffic_probe\")"))
        assertTrue(exactTargets().contains("player_traces:foot_traffic_probe"))
        assertTrue(!exactTargets().contains("traces:foot_traffic_probe"))
    }

    @Test
    fun `foot traffic probe reports read-only local regional and server totals`() {
        val itemPath = "../mod_source/player-traces/src/main/kotlin/com/bettercontent/playertraces/item/FootTrafficProbeItem.kt"
        val queryPath = "../mod_source/player-traces/src/main/kotlin/com/bettercontent/playertraces/logic/TraceQueryService.kt"
        val item = Files.readString(root.resolve(itemPath).normalize())
        val query = Files.readString(root.resolve(queryPath).normalize())
        val row = registry.path("annotations").single { it.path("selector").path("item").asText() == "player_traces:foot_traffic_probe" }
        val copy = row.path("lines").map { it.asText() }.joinToString(" ")

        assertTrue(item.contains("TraceQueryService().trafficPotential(level, player.blockPosition())"))
        assertTrue(item.contains("player.getItemInHand(hand)"))
        assertTrue(!item.contains("shrink(") && !item.contains("clear"))
        assertTrue(query.contains("localSurvivingStrength = localSurvivingStrength"))
        assertTrue(query.contains("regionalSurvivingStrength = regionalSurvivingStrength"))
        assertTrue(query.contains("serverSurvivingStrength = serverSurvivingStrength"))
        assertTrue(row.path("owner").asText().contains("FootTrafficProbeItem.kt"))
        assertTrue(row.path("owner").asText().contains("TraceQueryService.kt"))
        assertTrue(copy.contains("local, regional, and server-wide"))
        assertTrue(copy.contains("without consuming or clearing traces"))
    }

    @Test
    fun `wireless hover states the payload-scaled sender relay cost`() {
        val device = Files.readString(
            root.resolve("../mod_source/oc2r-wireless-pubsub/src/main/java/com/bettercontent/oc2rwirelesspubsub/device/WirelessCardItemDevice.java").normalize(),
        )
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("items").any { it.asText() == "oc2r_wireless_pubsub:wireless_relay" }
        }
        val copy = row.path("lines").map { it.asText() }.joinToString(" ")
        assertTrue(device.contains("BASE_ENERGY_COST = 4"))
        assertTrue(device.contains("ENERGY_PER_32_BYTES = 1"))
        assertTrue(device.contains("((payloadBytes + 31) / 32) * ENERGY_PER_32_BYTES"))
        assertTrue(copy.contains("4 energy plus 1 per started 32 payload bytes"))
        assertTrue(row.path("owner").asText().contains("WirelessCardItemDevice.java"))
    }

    @Test
    fun `Cold Sweat waterskin hover distinguishes body cooling from configured thirst drinks`() {
        val coldSweat = Files.readString(root.resolve("config/coldsweat/item.toml"))
        val thirst = Files.readString(root.resolve("config/thirst/item_settings.toml"))
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("items").any { it.asText() == "cold_sweat:waterskin" }
        }
        val copy = row.path("lines").map { it.asText() }.joinToString(" ")
        assertTrue(coldSweat.contains("Defines how much a waterskin will change the player's body temperature"))
        assertTrue(coldSweat.contains("\"Waterskin Strength\" = 50"))
        assertTrue(thirst.contains("Defines items that will recover thirst when drunk"))
        assertTrue(!thirst.contains("cold_sweat:waterskin") && !thirst.contains("cold_sweat:filled_waterskin"))
        assertTrue(row.path("owner").asText().contains("config/coldsweat/item.toml"))
        assertTrue(row.path("evidence").any { it.asText() == "config/thirst/item_settings.toml" })
        assertTrue(copy.contains("Changes body temperature; does not restore thirst"))
    }

    @Test
    fun `rain collector hover follows the configured Thirst default purity`() {
        val collector = Files.readString(
            root.resolve("../mod_source/water-survival/src/main/java/com/bettercontent/watersurvival/RainCollectorBlock.java").normalize(),
        )
        val thirstConfig = Files.readString(root.resolve("config/thirst/common.toml"))
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("item").asText() == "water_survival:rain_collector"
        }
        val copy = row.path("lines").map { it.asText() }.joinToString(" ")
        assertTrue(collector.contains("public static final int CAPACITY = 4"))
        assertTrue(collector.contains("level.canSeeSky(collectionPos)"))
        assertTrue(collector.contains("level.isRainingAt(collectionPos) || Weather2RainCompat.isPrecipitatingAt(level, collectionPos)"))
        assertTrue(collector.contains("WaterPurity.getFilledContainer(held, false)"))
        assertTrue(collector.contains("WaterPurity.addPurity(filled, pos, level)"))
        assertTrue(collector.contains("thirst.drink(player, 3, 2)"))
        assertTrue(thirstConfig.contains("defaultPurity = 2"))
        assertTrue(copy.contains("Collects four rain portions under open sky"))
        assertTrue(copy.contains("Drink directly or bottle it"))
        assertTrue(copy.contains("bottled rain uses Thirst's configured default purity"))
        assertTrue(!copy.contains("purity-three"), "the custom collector block has no Thirst water block state")
        assertTrue(row.path("owner").asText().contains("config/thirst/common.toml"))
    }

    @Test
    fun `purity three safety guidance follows active Thirst risk settings`() {
        val commonConfig = Files.readString(root.resolve("config/thirst/common.toml"))
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("item").asText() == "thirst:terracotta_water_bowl"
        }

        assertEquals("config/thirst/common.toml", row.path("owner").asText())
        assertTrue(row.path("lines").map { it.asText() }.single().contains("Purity 3 is safe"))
        assertTrue(commonConfig.contains("purifiedPoisonPercentage = 0"))
        assertTrue(commonConfig.contains("purifiedNauseaPercentage = 0"))
    }

    @Test
    fun `Heart Fragment hover states the per-fragment altar rate`() {
        val deathEvents = Files.readString(
            root.resolve("../mod_source/rpg-stats/src/main/kotlin/com/bettercontent/rpgstats/common/event/CommonForgeEvents.kt").normalize(),
        )
        val entitlements = Files.readString(
            root.resolve("../mod_source/rpg-stats/src/main/kotlin/com/bettercontent/rpgstats/common/item/HeartFragmentEntitlements.kt").normalize(),
        )
        val fragmentData = Files.readString(
            root.resolve("../mod_source/rpg-stats/src/main/kotlin/com/bettercontent/rpgstats/common/item/HeartFragmentData.kt").normalize(),
        )
        val heartBlock = Files.readString(
            root.resolve("../mod_source/rpg-stats/src/main/kotlin/com/bettercontent/rpgstats/common/block/entity/HeartBlockEntity.kt").normalize(),
        )
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("item").asText() == "rpg_stats:heart_fragment"
        }
        val copy = row.path("lines").map { it.asText() }.joinToString(" ")

        assertTrue(fragmentData.contains("LP_PER_FRAGMENT_PER_TICK: Int = 1"))
        assertTrue(fragmentData.contains("installedFragments, LP_PER_FRAGMENT_PER_TICK.toLong()"))
        assertTrue(heartBlock.contains("HeartBlockEmission.fill(heart.installedFragments, altar::fillMainTank)"))
        assertTrue(deathEvents.contains("HeartFragmentEntitlements.captureFinalDeath(player.persistentData, player.experienceLevel)"))
        assertTrue(deathEvents.contains("HeartFragmentEntitlements.finalizeCapturedDeath(player.persistentData)"))
        assertTrue(entitlements.contains("fun captureFinalDeath(data: CompoundTag, heldLevel: Int"))
        assertTrue(entitlements.contains("HeartFragmentData.fragmentsForLevelBig(capture.getInt(LEVEL_TAG))"))
        assertTrue(copy.contains("Each installed fragment supplies 1 LP/t to an adjacent Blood Altar."))
        assertTrue(copy.contains("Final death grants fragments based on XP held then."))
        assertTrue(row.path("owner").asText().contains("CommonForgeEvents.kt"))
        assertTrue(row.path("owner").asText().contains("HeartFragmentEntitlements.kt"))
        assertTrue(row.path("owner").asText().contains("HeartBlockEntity.kt"))
    }

    @Test
    fun `bouquet hover controls match the empty-hand editor and rotation keys`() {
        val block = Files.readString(
            root.resolve("../mod_source/procedural-bouquets/src/main/java/com/bettercontent/proceduralbouquets/block/BouquetGridBlock.java").normalize(),
        )
        val screen = Files.readString(
            root.resolve("../mod_source/procedural-bouquets/src/main/java/com/bettercontent/proceduralbouquets/client/BouquetGridScreen.java").normalize(),
        )
        val menu = Files.readString(
            root.resolve("../mod_source/procedural-bouquets/src/main/java/com/bettercontent/proceduralbouquets/menu/BouquetGridMenu.java").normalize(),
        )
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("items").any { it.asText() == "procedural_bouquets:bouquet_grid" }
        }
        val copy = row.path("lines").map { it.asText() }.joinToString(" ")

        assertTrue(block.contains("player.isShiftKeyDown() && emptyHand"))
        assertTrue(screen.contains("keyCode == GLFW.GLFW_KEY_R && rotateHoveredFlower(hasShiftDown())"))
        assertTrue(screen.contains("BouquetGridMenu.ROTATE_COUNTERCLOCKWISE_BUTTON"))
        assertTrue(screen.contains("BouquetGridMenu.ROTATE_CLOCKWISE_BUTTON"))
        assertTrue(menu.contains("GRID_COLUMNS = 16"))
        assertTrue(menu.contains("GRID_ROWS = 16"))
        assertTrue(copy.contains("Sneak-use empty-handed"))
        assertTrue(copy.contains("R and Shift-R rotate the hovered flower"))
        assertTrue(row.path("owner").asText().contains("BouquetGridScreen.java"))
    }

    @Test
    fun `Font grout hover matches all authored dimension materials and shared smelting output`() {
        val workshop = Files.readString(root.resolve("kubejs/server_scripts/progression/10_hand_workshop.js"))
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("items").any { it.asText() == "kubejs:nether_font_grout" }
        }
        val selectedItems = row.path("selector").path("items").map { it.asText() }.toSet()
        val expected = mapOf(
            "nether" to "minecraft:netherrack",
            "aether" to "aether:holystone",
            "bumblezone" to "the_bumblezone:pollen_puff",
            "ratlantis" to "rats:marbled_cheese_raw",
        )

        assertTrue(expected.all { (dimension, material) ->
            workshop.contains("['$dimension', '$material', 'kubejs:${dimension}_font_grout']")
        })
        assertEquals(expected.keys.map { "kubejs:${it}_font_grout" }.toSet(), selectedItems)
        assertTrue(workshop.contains("event.smelting('tconstruct:seared_brick', font[2])"))
        assertTrue(row.path("lines").map { it.asText() }.single().contains("All four Font variants smelt into the same Seared Brick"))
    }

    @Test
    fun `Boiler Heater hover costs match configured heat per delivery strength`() {
        val heatLogic = Files.readString(
            root.resolve("../mod_source/heat-sync/src/main/kotlin/com/bettercontent/heatsync/content/heat/BoilerHeaterLogic.kt").normalize(),
        )
        val activeConfig = Files.readString(root.resolve("config/heat_sync-common.toml"))
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("item").asText() == "heat_sync:boiler_heater"
        }
        val copy = row.path("lines").map { it.asText() }.single()

        assertTrue(heatLogic.contains("1 -> settings.firstCost"))
        assertTrue(heatLogic.contains("2 -> settings.secondCost"))
        assertTrue(heatLogic.contains("3 -> settings.thirdCost"))
        assertTrue(activeConfig.contains("strength_1_cost_per_tick = 1.0"))
        assertTrue(activeConfig.contains("strength_2_cost_per_tick = 2.0"))
        assertTrue(activeConfig.contains("strength_3_cost_per_tick = 3.0"))
        assertTrue(copy.contains("Consumes 1/2/3 heat per tick at delivery strength 1/2/3."))
        assertTrue(row.path("owner").asText().contains("config/heat_sync-common.toml"))
    }

    @Test
    fun `mining helmet light guidance follows optional Dynamic Lights registration`() {
        val item = Files.readString(
            root.resolve("../mod_source/mining-helmet/src/main/java/com/bettercontent/mininghelmet/MiningHelmetItem.java").normalize(),
        )
        val client = Files.readString(
            root.resolve("../mod_source/mining-helmet/src/main/java/com/bettercontent/mininghelmet/client/MiningHelmetClient.java").normalize(),
        )
        val lightHandler = Files.readString(
            root.resolve("../mod_source/mining-helmet/src/main/java/com/bettercontent/mininghelmet/client/DynamicLightsCompat.java").normalize(),
        )
        val lightConfig = Files.readString(
            root.resolve("../mod_source/mining-helmet/src/main/resources/assets/mining_helmet/dynamiclights/item/mining_helmet.json").normalize(),
        )
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("item").asText() == "mining_helmet:mining_helmet"
        }
        val copy = row.path("lines").map { it.asText() }.joinToString(" ")

        assertTrue(client.contains("if (ModList.get().isLoaded(\"sodiumdynamiclights\")) DynamicLightsCompat.register()"))
        assertTrue(lightHandler.contains("MiningHelmetItem.isWorn(player) ? 15 : 0"))
        assertTrue(lightConfig.contains("\"luminance\": 15"))
        assertTrue(item.contains("extends ArmorItem") && item.contains("implements ICurioItem"))
        assertTrue(copy.contains("With Sodium Dynamic Lights, a worn helmet emits level 15"))
        assertTrue(copy.contains("Curios head slot provides light only"))
        assertEquals("mining_helmet", row.path("required_mod").asText())
        assertTrue(row.path("owner").asText().contains("DynamicLightsCompat.java"))
    }

    @Test
    fun `acetate membrane hover describes its upgrade recipe and retention role`() {
        val recipe = Files.readString(root.resolve("kubejs/server_scripts/progression/45_acid_chemistry.js"))
        val interaction = Files.readString(
            root.resolve("../mod_source/airtight-machinery/src/main/java/com/bettercontent/airtightmachinery/chemistry/AirtightUpgradeInteraction.java").normalize(),
        )
        val escape = Files.readString(
            root.resolve("../mod_source/latent-chemlib/src/main/java/com/bettercontent/latentchemlib/sim/GasEscapeHandler.java").normalize(),
        )
        val row = registry.path("annotations").single { it.path("selector").path("item").asText() == "kubejs:acetate_membrane" }
        val owner = row.path("owner").asText()
        val copy = row.path("lines").map { it.asText() }.single()

        assertEquals(2, recipe.split("{ item: 'chemlib:cellulose' }").size - 1)
        assertTrue(recipe.contains("{ fluid: 'chemlib:acetic_acid_fluid', amount: 250 }"))
        assertTrue(recipe.contains("results: [{ item: 'kubejs:acetate_membrane', count: 4 }]"))
        assertTrue(recipe.contains("M: 'kubejs:acetate_membrane'"))
        assertTrue(recipe.contains("event.shaped('airtight_machinery:airtight_upgrade'"))
        assertTrue(interaction.contains("held.is(ChemistryContent.AIRTIGHT_UPGRADE.get())"))
        assertTrue(interaction.contains("holder.airtightMachinery" + "$" + "setAirtight(true)"))
        assertTrue(escape.contains("if (isAirtight(holder)) return;"))
        assertTrue(owner.contains("AirtightUpgradeInteraction.java"))
        assertTrue(owner.contains("GasEscapeHandler.java"))
        assertEquals(
            "Used to craft an Airtight Upgrade that retains ChemLib gases in supported machines. Renewably made from cellulose and acetic acid.",
            copy,
        )
        assertTrue(!copy.contains("filters") && !copy.contains("AdPother vents"))
    }

    @Test
    fun `Airtight Upgrade targets match the registered gas-retention holders`() {
        val interaction = Files.readString(
            root.resolve("../mod_source/airtight-machinery/src/main/java/com/bettercontent/airtightmachinery/chemistry/AirtightUpgradeInteraction.java").normalize(),
        )
        val mixins = Files.readString(root.resolve("../mod_source/airtight-machinery/src/main/resources/airtight_machinery.mixins.json").normalize())
        val basin = Files.readString(
            root.resolve("../mod_source/airtight-machinery/src/main/java/com/bettercontent/airtightmachinery/mixin/chemistry/create/BasinAirtightMixin.java").normalize(),
        )
        val mixer = Files.readString(
            root.resolve("../mod_source/airtight-machinery/src/main/java/com/bettercontent/airtightmachinery/mixin/chemistry/pneumaticcraft/FluidMixerAirtightMixin.java").normalize(),
        )
        val plant = Files.readString(
            root.resolve("../mod_source/airtight-machinery/src/main/java/com/bettercontent/airtightmachinery/mixin/chemistry/pneumaticcraft/ThermopneumaticPlantAirtightMixin.java").normalize(),
        )
        val escape = Files.readString(
            root.resolve("../mod_source/latent-chemlib/src/main/java/com/bettercontent/latentchemlib/sim/GasEscapeHandler.java").normalize(),
        )
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("item").asText() == "airtight_machinery:airtight_upgrade"
        }
        val copy = row.path("lines").map { it.asText() }.joinToString(" ")

        assertTrue(interaction.contains("held.is(ChemistryContent.AIRTIGHT_UPGRADE.get())"))
        assertTrue(interaction.contains("held.isEmpty() && event.getEntity().isShiftKeyDown()"))
        assertTrue(mixins.contains("chemistry.create.BasinAirtightMixin"))
        assertTrue(mixins.contains("chemistry.pneumaticcraft.FluidMixerAirtightMixin"))
        assertTrue(mixins.contains("chemistry.pneumaticcraft.ThermopneumaticPlantAirtightMixin"))
        assertTrue(basin.contains("implements AirtightUpgradeHolder"))
        assertTrue(mixer.contains("implements AirtightUpgradeHolder"))
        assertTrue(plant.contains("implements AirtightUpgradeHolder"))
        assertTrue(escape.contains("if (isAirtight(holder)) return;"))
        assertTrue(copy.contains("Seal a Create Basin or PneumaticCraft Mixer/Plant"))
        assertTrue(copy.contains("Sneak-use empty-handed to remove the upgrade"))
        assertTrue(row.path("owner").asText().contains("GasEscapeHandler.java"))
    }

    @Test
    fun `spirit market workstation hover matches its profession and currency catalogue`() {
        val economy = Files.readString(
            root.resolve("../mod_source/better-content-economy/src/main/java/com/bettercontent/economy/BetterContentEconomy.java").normalize(),
        )
        val professions = Files.readString(
            root.resolve("../mod_source/better-content-economy/src/main/java/com/bettercontent/economy/registry/SpiritProfessions.java").normalize(),
        )
        val catalogue = Files.readString(
            root.resolve("../mod_source/better-content-economy/src/main/java/com/bettercontent/economy/trader/VillagerCatalogue.java").normalize(),
        )
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("items").any { it.asText() == "better_content_economy:sacred_reliquary" }
        }
        val copy = row.path("lines").map { it.asText() }.single()
        val selectedItems = row.path("selector").path("items").map { it.asText() }.toSet()

        assertTrue(economy.contains("SpiritProfessions.register(modBus)"))
        assertTrue(economy.contains("MinecraftForge.EVENT_BUS.register(VillagerCatalogue.class)"))
        assertTrue(professions.contains("block.get()") && professions.contains("new VillagerProfession"))
        assertTrue(catalogue.contains("SpiritProfessions.kindOf(event.getType())"))
        assertTrue(catalogue.contains("CurrencyItems.item(kind.currencyIdentity())"))
        assertTrue(copy.contains("Assigns a villager profession"))
        assertTrue(copy.contains("only the matching spirit color"))
        assertEquals(7, selectedItems.size)
        assertTrue(row.path("owner").asText().contains("VillagerCatalogue.java"))
    }

    @Test
    fun `Bumblezone nursery hover matches one-time worldgen and seed recovery`() {
        val base = "../mod_source/bumblezone-cultivars/src/main/java/com/bettercontent/bumblezonecultivars/"
        val mod = Files.readString(root.resolve(base + "BumblezoneCultivars.java").normalize())
        val finalizer = Files.readString(root.resolve(base + "CultivarChunkFinalizer.java").normalize())
        val nursery = Files.readString(root.resolve(base + "LivingPollenNurseryBlock.java").normalize())
        val loot = Files.readString(root.resolve(base + "CultivarLootModifier.java").normalize())
        val row = registry.path("annotations").single { it.path("selector").path("item").asText() == "bumblezone_cultivars:living_pollen_nursery" }
        val copy = row.path("lines").map { it.asText() }.joinToString(" ")
        assertTrue(mod.contains("LIVING_POLLEN_NURSERY = BLOCKS.register(\"living_pollen_nursery\""))
        assertTrue(finalizer.contains("if (!event.isNewChunk()) return"))
        assertTrue(finalizer.contains("if (!level.dimension().location().equals(BUMBLEZONE)) return"))
        assertTrue(finalizer.contains("POLLEN.equals(below)"))
        assertTrue(finalizer.contains("placed < 24"))
        assertTrue(finalizer.contains("chosen.seedItem()"))
        assertTrue(nursery.contains("new ItemStack(item,4)"))
        assertTrue(loot.contains("cultivar.maturityRule()"))
        assertTrue(loot.contains("if (!inOrigin && persistentHarvest) return 0;"))
        assertTrue(row.path("owner").asText().contains("CultivarChunkFinalizer.java"))
        assertTrue(row.path("owner").asText().contains("LivingPollenNurseryBlock.java"))
        assertTrue(copy.contains("Find these crop nurseries in the Bumblezone"))
        assertTrue(copy.contains("Mature plants provide seeds or other planting material"))
    }

    @Test
    fun `cultivar seed hover preserves the persistent-harvest off-origin exception`() {
        val loot = Files.readString(
            root.resolve("../mod_source/bumblezone-cultivars/src/main/java/com/bettercontent/bumblezonecultivars/CultivarLootModifier.java").normalize(),
        )
        val catalogue = Files.readString(
            root.resolve("../mod_source/bumblezone-cultivars/src/main/resources/defaults/cultivars.json").normalize(),
        )
        val seedTag = Files.readString(
            root.resolve("../mod_source/bumblezone-cultivars/src/main/resources/data/bumblezone_cultivars/tags/items/seeds.json").normalize(),
        )
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("tag").asText() == "bumblezone_cultivars:seeds"
        }
        val copy = row.path("lines").map { it.asText() }.joinToString(" ")

        assertTrue(loot.contains("if (!inOrigin && persistentHarvest) return 0;"))
        assertTrue(loot.contains("return offOriginRoll < 0.10F ? 2 : 1;"))
        assertTrue(catalogue.contains("\"growthForm\":\"persistent-harvest\""))
        assertTrue(seedTag.contains("bumblezone_cultivars:minecraft_glow_berry_seeds"))
        assertTrue(copy.contains("Mature crops return off-origin seeds, sometimes two"))
        assertTrue(copy.contains("Persistent-harvest types do not"))
        assertTrue(row.path("owner").asText().contains("CultivarLootModifier.java"))
    }

    @Test
    fun `native Malum spirit hover distinguishes recipe reagents from Economy currencies`() {
        val acquisition = Files.readString(
            root.resolve("../mod_source/better-content-economy/src/main/java/com/bettercontent/economy/spirit/SpiritAcquisition.java").normalize(),
        )
        val allocation = Files.readString(
            root.resolve("../mod_source/better-content-economy/src/main/java/com/bettercontent/economy/spirit/SpiritCreditAllocation.java").normalize(),
        )
        val identity = Files.readString(
            root.resolve("../mod_source/better-content-economy/src/main/java/com/bettercontent/economy/spirit/CurrencyIdentity.java").normalize(),
        )
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("items").any { it.asText() == "malum:aerial_spirit" }
        }
        val copy = row.path("lines").map { it.asText() }.joinToString(" ")
        assertTrue(acquisition.contains("SpiritCreditAllocation.fromNative(nativeDrops"))
        assertTrue(acquisition.contains("CurrencyItems.item(grant.identity())"))
        assertTrue(acquisition.contains("CurrencyIdentity.fromLegacyNativeSpirit(id) == null"))
        assertTrue(allocation.contains("Maps Malum's ordinary drops"))
        assertTrue(identity.contains("Native Malum spirits remain crafting reagents"))
        assertTrue(copy.contains("These Malum spirits remain recipe reagents"))
        assertTrue(copy.contains("credited kills issue separate Better Content currencies"))
        assertTrue(copy.contains("Eldritch and Umbral do not map to village-market currencies"))
        assertTrue(!copy.contains("Matching village markets accept this color"))
        assertEquals("better_content_economy", row.path("required_mod").asText())
        assertTrue(row.path("owner").asText().contains("SpiritCreditAllocation.java"))
        assertTrue(row.path("owner").asText().contains("CurrencyIdentity.java"))
    }

    @Test
    fun `spirit pouch currency claim is gated by the registered Economy slot mixin`() {
        val mixin = Files.readString(
            root.resolve("../mod_source/better-content-economy/src/main/java/com/bettercontent/economy/mixin/SpiritPouchCurrencySlotMixin.java").normalize(),
        )
        val mixinConfig = Files.readString(
            root.resolve("../mod_source/better-content-economy/src/main/resources/better_content_economy.mixins.json").normalize(),
        )
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("item").asText() == "malum:spirit_pouch"
        }
        assertTrue(mixinConfig.contains("SpiritPouchCurrencySlotMixin"))
        assertTrue(mixin.contains("CurrencyIdentity.fromItemId(ForgeRegistries.ITEMS.getKey(stack.getItem())) != null"))
        assertTrue(row.path("lines").map { it.asText() }.single().contains("with Economy installed"))
        assertEquals("better_content_economy", row.path("required_mod").asText())
        assertTrue(row.path("owner").asText().contains("SpiritPouchCurrencySlotMixin.java"))
    }

    @Test
    fun `every pack-created transition item has an annotation`() {
        val source = Files.readString(root.resolve("kubejs/startup_scripts/progression/20_transition_items.js"))
        val registered = Regex("\\['([a-z0-9_]+)',\\s*'[^']+'\\]")
            .findAll(source)
            .map { "kubejs:${it.groupValues[1]}" }
            .toSet()
        val missing = registered - exactTargets()
        assertTrue(registered.isNotEmpty())
        assertTrue(missing.isEmpty(), "unannotated pack-created transition items: ${missing.sorted()}")
    }

    @Test
    fun `cross-tradition component hover follows its actual Blood Magic and Ars routes`() {
        val recipe = Files.readString(root.resolve("kubejs/server_scripts/compat/retained/refactor__balance__166_tome_of_blood_post_ae2_gates.js"))
        val row = registry.path("annotations").single { it.path("concept_id").asText() == "magic.transition.cross_tradition" }
        val targets = row.path("selector").path("items").map { it.asText() }.toSet()
        val copy = row.path("lines").map { it.asText() }.single()

        assertEquals(setOf("kubejs:purified_blood_catalyst", "kubejs:purified_source_core", "kubejs:living_binding"), targets)
        assertTrue(recipe.contains("bcTobAlchemy(event, 'kubejs:purified_blood_catalyst'"))
        assertTrue(recipe.contains("{ item: 'bloodmagic:reinforcedslate' }"))
        assertTrue(recipe.contains("{ item: 'ars_nouveau:source_gem' }"))
        assertTrue(recipe.contains("output: { item: 'kubejs:purified_source_core' }"))
        assertTrue(recipe.contains("type: 'ars_nouveau:enchanting_apparatus'"))
        assertTrue(recipe.contains("bcTobAlchemy(event, 'kubejs:living_binding'"))
        assertTrue(recipe.contains("{ item: 'ars_nouveau:magebloom_fiber' }"))
        assertTrue(recipe.contains("'kubejs:tomeofblood/alchemytable/novice_tome_post_ae2'"))
        assertTrue(row.path("owner").asText().contains("refactor__balance__166_tome_of_blood_post_ae2_gates.js"))
        assertTrue(copy.contains("Blood Magic alchemy") && copy.contains("Ars Nouveau Source"))
        assertTrue(!copy.contains("Occultism"))
    }

    @Test
    fun `andesite machine block owner matches its direct machine roots`() {
        val powered = Files.readString(root.resolve("kubejs/server_scripts/progression/20_powered_works.js"))
        val row = registry.path("annotations").single { it.path("selector").path("item").asText() == "kubejs:andesite_machine_block" }
        val owner = row.path("owner").asText()
        val copy = row.path("lines").map { it.asText() }.single()

        assertTrue(powered.contains("event.shaped('4x kubejs:andesite_machine_block'"))
        assertTrue(powered.contains("event.shaped('create:millstone'"))
        assertTrue(powered.contains("M: 'kubejs:andesite_machine_block'"))
        assertTrue(powered.contains("event.shaped('create:mechanical_press'"))
        assertTrue(owner == "kubejs/server_scripts/progression/20_powered_works.js")
        assertTrue(copy.contains("Millstone") && copy.contains("Mechanical Press"))
    }

    @Test
    fun `machine block hovers follow their authored direct roots`() {
        val contracts = listOf(
            Triple("kubejs:copper_machine_block", "20_powered_works.js", listOf("create:water_wheel", "create:large_water_wheel", "create:windmill_bearing", "create:mechanical_pump")),
            Triple("kubejs:brass_machine_block", "30_precision_factory.js", listOf("create:deployer", "create:mechanical_crafter", "create:steam_engine", "create:track_station")),
            Triple("kubejs:airtight_machine_block", "40_thermal_pressure.js", listOf("compressedcreativity:rotational_compressor", "pneumaticcraft:pressure_chamber_interface")),
            Triple("kubejs:electrical_machine_block", "50_electrical_control.js", listOf("powergrid:generator_housing", "morered:soldering_table")),
            Triple("kubejs:space_machine_block", "60_aerospace.js", listOf("creatingspace:rocket_engineer_table", "creatingspace:mechanical_electrolyzer", "creatingspace:air_liquefier")),
        )
        contracts.forEach { (item, ownerFile, roots) ->
            val row = registry.path("annotations").single { it.path("selector").path("item").asText() == item }
            val owner = row.path("owner").asText()
            val sourcePath = "kubejs/server_scripts/progression/$ownerFile"
            val source = Files.readString(root.resolve(sourcePath))
            assertTrue(owner == sourcePath, "$item owner mismatch")
            assertTrue(source.contains(item), "$ownerFile does not produce or consume $item")
            roots.forEach { rootItem -> assertTrue(source.contains(rootItem), "$ownerFile missing direct root $rootItem") }
            val copy = row.path("lines").map { it.asText() }.joinToString(" ")
            val copyHints = mapOf(
                "kubejs:copper_machine_block" to listOf("water wheels", "wind power", "Mechanical Pump"),
                "kubejs:brass_machine_block" to listOf("Deployer", "Mechanical Crafter", "steam engine", "Track Station"),
                "kubejs:airtight_machine_block" to listOf("Rotational Compressor", "Pressure Chamber interface"),
                "kubejs:electrical_machine_block" to listOf("PowerGrid stationary generation", "Soldering Table"),
                "kubejs:space_machine_block" to listOf("rocket engineering", "electrolysis", "air liquefaction"),
            ).getValue(item)
            copyHints.forEach { hint -> assertTrue(copy.contains(hint), "$item copy missing $hint") }
        }

        val pressure = registry.path("annotations").single { it.path("selector").path("item").asText() == "kubejs:pressure_seal" }
        val pressureSource = Files.readString(root.resolve("kubejs/server_scripts/progression/40_thermal_pressure.js"))
        assertTrue(pressureSource.contains("event.shaped('2x kubejs:pressure_seal'"))
        assertTrue(pressureSource.contains("S: { item: 'kubejs:pressure_seal' }"))
        assertTrue(pressure.path("owner").asText().contains("40_thermal_pressure.js"))
        assertTrue(pressure.path("lines").toString().contains("first Airtight and pressure machines"))
    }

    @Test
    fun `brass utility assembly owner includes its TaCZ manufacturing bench consumers`() {
        val producer = Files.readString(root.resolve("kubejs/server_scripts/progression/70_transition_components.js"))
        val consumers = Files.readString(root.resolve("kubejs/server_scripts/compat/retained/refactor__balance__171_tacz_manufacturing_gates.js"))
        val row = registry.path("annotations").single { it.path("selector").path("item").asText() == "kubejs:brass_utility_assembly" }
        val owner = row.path("owner").asText()
        val copy = row.path("lines").map { it.asText() }.single()

        assertTrue(producer.contains("event.shaped('2x kubejs:brass_utility_assembly'"))
        assertTrue(consumers.contains("A: 'kubejs:brass_utility_assembly'"))
        assertTrue(consumers.contains("tacz:workbench_a") && consumers.contains("tacz:attachment_workbench"))
        assertTrue(owner.contains("70_transition_components.js"))
        assertTrue(owner.contains("refactor__balance__171_tacz_manufacturing_gates.js"))
        assertTrue(copy.contains("manufacturing benches") && copy.contains("precision assembly"))
    }

    @Test
    fun `vanadium catalyst hovers follow the reusable contact process state transition`() {
        val source = Files.readString(root.resolve("kubejs/server_scripts/progression/45_acid_chemistry.js"))
        val base = registry.path("annotations").single { it.path("selector").path("item").asText() == "kubejs:vanadium_contact_catalyst" }
        val active = registry.path("annotations").single { it.path("selector").path("item").asText() == "kubejs:oxygenated_vanadium_contact_catalyst" }
        val baseCopy = base.path("lines").map { it.asText() }.single()
        val activeCopy = active.path("lines").map { it.asText() }.single()

        assertTrue(source.contains("event.custom({") && source.contains("results: [{ item: 'kubejs:vanadium_contact_catalyst' }]"))
        assertTrue(source.contains("bcAcidThermo(event, 'contact_catalyst_oxygenation'"))
        assertTrue(source.contains("{ item: 'kubejs:vanadium_contact_catalyst' }, bcAcidFluid('chemlib:oxygen_fluid', 250)"))
        assertTrue(source.contains("{ item: 'kubejs:oxygenated_vanadium_contact_catalyst' }, null, 2.5, 673)"))
        assertTrue(source.contains("bcAcidThermo(event, 'sulfur_trioxide_contact'"))
        assertTrue(source.contains("{ item: 'kubejs:oxygenated_vanadium_contact_catalyst' }, bcAcidFluid('chemlib:sulfur_dioxide_fluid', 500)"))
        assertTrue(source.contains("{ item: 'kubejs:vanadium_contact_catalyst' }, bcAcidFluid('chemlib:sulfur_trioxide_fluid', 500), 3.0, 723)"))
        assertTrue(baseCopy.contains("Reusable") && baseCopy.contains("oxygenate it"))
        assertTrue(activeCopy.contains("Active") && activeCopy.contains("returns to its reusable base state"))
        assertTrue(base.path("owner").asText().contains("45_acid_chemistry.js"))
        assertTrue(active.path("owner").asText().contains("45_acid_chemistry.js"))
    }

    @Test
    fun `transition component annotations cite their authored downstream consumers`() {
        val expectedOwners = mapOf(
            "kubejs:sky_steel_sheet" to listOf(
                "kubejs/server_scripts/utility/hooks_drones_gates.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__169_backpack_post_ae2_utility_gates.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__171_tacz_manufacturing_gates.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__165_protection_pixel_post_ae2_gates.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__166_tome_of_blood_post_ae2_gates.js",
            ),
            "kubejs:electrical_control_module" to listOf(
                "kubejs/server_scripts/progression/70_transition_components.js",
            ),
            "kubejs:electrical_instrumentation_module" to listOf(
                "kubejs/server_scripts/progression/81_pneumaticcraft_progression.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__171_tacz_manufacturing_gates.js",
            ),
            "kubejs:ae_logic_package" to listOf(
                "kubejs/server_scripts/progression/81_pneumaticcraft_progression.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__169_backpack_post_ae2_utility_gates.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__171_tacz_manufacturing_gates.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__165_protection_pixel_post_ae2_gates.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__166_tome_of_blood_post_ae2_gates.js",
                "kubejs/server_scripts/utility/hooks_drones_gates.js",
            ),
            "kubejs:impossible_support_matrix" to listOf(
                "kubejs/server_scripts/progression/20_ratlantis_logistics.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__166_tome_of_blood_post_ae2_gates.js",
            ),
            "kubejs:mountain_beryl_lens" to listOf(
                "kubejs/server_scripts/compat/retained/refactor__balance__110_extreme_y_band_reward_gates.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__171_tacz_manufacturing_gates.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__166_tome_of_blood_post_ae2_gates.js",
            ),
            "kubejs:corundum_lapping_grit" to listOf(
                "kubejs/server_scripts/compat/retained/refactor__balance__110_extreme_y_band_reward_gates.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__166_tome_of_blood_post_ae2_gates.js",
            ),
            "kubejs:kimberlite_diamond_seed" to listOf(
                "kubejs/server_scripts/compat/retained/refactor__balance__110_extreme_y_band_reward_gates.js",
            ),
            "kubejs:tungsten_carbide_insert" to listOf(
                "kubejs/server_scripts/compat/retained/refactor__balance__110_extreme_y_band_reward_gates.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__165_protection_pixel_post_ae2_gates.js",
            ),
            "kubejs:titanium_thermal_plate" to listOf(
                "kubejs/server_scripts/progression/60_aerospace.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__110_extreme_y_band_reward_gates.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__165_protection_pixel_post_ae2_gates.js",
            ),
            "kubejs:soulstone_carbon_matrix" to listOf(
                "kubejs/server_scripts/compat/retained/refactor__balance__110_extreme_y_band_reward_gates.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__165_protection_pixel_post_ae2_gates.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__166_tome_of_blood_post_ae2_gates.js",
            ),
            "kubejs:platinum_group_residue" to listOf(
                "kubejs/server_scripts/compat/retained/refactor__balance__110_extreme_y_band_reward_gates.js",
                "kubejs/server_scripts/compat/retained/refactor__balance__165_protection_pixel_post_ae2_gates.js",
            ),
        )

        expectedOwners.forEach { (item, consumers) ->
            val row = registry.path("annotations").single { annotation ->
                annotation.path("selector").path("item").asText() == item ||
                    annotation.path("selector").path("items").any { it.asText() == item }
            }
            val ownerPaths = row.path("owner").asText().split(';').map(String::trim)
            consumers.forEach { consumer ->
                assertTrue(ownerPaths.contains(consumer), "$item is missing owner $consumer")
                assertTrue(Files.readString(root.resolve(consumer)).contains(item), "$consumer no longer consumes $item")
            }
        }
        assertTrue(registry.path("annotations").single { it.path("selector").path("item").asText() == "kubejs:electrical_control_module" }.path("lines").toString().contains("AE logic package"))
        assertTrue(registry.path("annotations").single { it.path("selector").path("item").asText() == "kubejs:electrical_instrumentation_module" }.path("lines").toString().contains("sensors"))
        assertTrue(registry.path("annotations").single { it.path("selector").path("item").asText() == "kubejs:ae_logic_package" }.path("lines").toString().contains("AE2-dependent equipment"))
        assertTrue(registry.path("annotations").single { it.path("selector").path("item").asText() == "kubejs:impossible_support_matrix" }.path("lines").toString().contains("late-game AE2 storage"))
    }

    @Test
    fun `Sky Steel hover describes the actual heated mixing and pressing stages`() {
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("items").any { it.asText() == "kubejs:sky_steel_ingot" }
        }
        val source = Files.readString(root.resolve("kubejs/server_scripts/progression/70_transition_components.js"))
        val copy = row.path("lines").single().asText()

        assertEquals(setOf("kubejs:sky_steel_ingot", "kubejs:sky_steel_sheet"),
            row.path("selector").path("items").map { it.asText() }.toSet())
        assertTrue(source.contains("type: 'create:mixing'"))
        assertTrue(source.contains("heatRequirement: 'heated'"))
        assertTrue(source.contains("results: [{ item: 'kubejs:sky_steel_ingot', count: 2 }]"))
        assertTrue(source.contains("type: 'create:pressing'"))
        assertTrue(source.contains("ingredients: [{ item: 'kubejs:sky_steel_ingot' }]"))
        assertTrue(source.contains("results: [{ item: 'kubejs:sky_steel_sheet' }]"))
        assertTrue(copy.contains("Heated mixing makes Sky Steel ingots"))
        assertTrue(copy.contains("press them into sheets"))
        assertTrue(!copy.contains("electrical processing"))
    }

    @Test
    fun `hand crank power hover follows the pre Nether machine progression`() {
        val hand = Files.readString(root.resolve("kubejs/server_scripts/progression/10_hand_workshop.js"))
        val powered = Files.readString(root.resolve("kubejs/server_scripts/progression/20_powered_works.js"))
        val progression = Files.readString(root.resolve("docs/progression.md"))
        val row = registry.path("annotations").single { it.path("selector").path("item").asText() == "create:hand_crank" }
        val copy = row.path("lines").map { it.asText() }.single()

        assertTrue(hand.contains("event.shaped('create:hand_crank'"))
        assertTrue(hand.contains("A: 'create:andesite_alloy'"))
        assertTrue(powered.contains("{ item: 'minecraft:nether_brick' }"))
        assertTrue(powered.contains("M: 'kubejs:copper_machine_block'"))
        assertTrue(powered.contains("'create:water_wheel'"))
        assertTrue(powered.contains("'create:large_water_wheel'"))
        assertTrue(powered.contains("'create:windmill_bearing'"))
        assertTrue(copy.contains("16 SU") && copy.contains("only positive SU source before the Nether"))
        assertTrue(progression.replace(Regex("\\s+"), " ").contains("The Hand Crank is the only positive SU source before the Nether."))
    }

    @Test
    fun `metallurgy hovers follow the authored Tinkers alloy and Font grout routes`() {
        val hand = Files.readString(root.resolve("kubejs/server_scripts/progression/10_hand_workshop.js"))
        val precision = Files.readString(root.resolve("kubejs/server_scripts/progression/30_precision_factory.js"))
        val andesite = registry.path("annotations").single { it.path("selector").path("item").asText() == "create:andesite_alloy" }
        val brass = registry.path("annotations").single { it.path("selector").path("item").asText() == "create:brass_ingot" }
        val grout = registry.path("annotations").single { it.path("selector").path("item").asText() == "tconstruct:grout" }
        val fontGrouts = registry.path("annotations").single { it.path("concept_id").asText() == "matter.metallurgy.provenance" && it.path("selector").has("items") }
        val fontIds = setOf(
            "kubejs:nether_font_grout", "kubejs:aether_font_grout",
            "kubejs:bumblezone_font_grout", "kubejs:ratlantis_font_grout",
        )

        assertTrue(hand.contains("event.remove({ type: 'create:mixing', output: 'create:andesite_alloy' })"))
        assertTrue(hand.contains("type: 'tconstruct:alloy'"))
        assertTrue(hand.contains("type: 'tconstruct:casting_table'"))
        assertTrue(andesite.path("lines").single().asText().contains("Alloy and cast with Tinkers' Construct"))
        assertTrue(precision.contains("event.remove({ type: 'create:mixing', output: 'create:brass_ingot' })"))
        assertTrue(brass.path("lines").single().asText().contains("Alloy with Tinkers' Construct"))
        assertTrue(hand.contains("event.remove({ output: 'tconstruct:grout' })"))
        assertTrue(hand.contains("#kubejs:ordinary_sand"))
        assertTrue(hand.contains("'minecraft:gravel'"))
        assertTrue(hand.contains("event.smelting('tconstruct:seared_brick', font[2])"))
        assertEquals(fontIds, fontGrouts.path("selector").path("items").map { it.asText() }.toSet())
        assertTrue(grout.path("lines").single().asText().contains("material from any active Font"))
        assertTrue(fontGrouts.path("lines").single().asText().contains("All four Font variants smelt into the same Seared Brick"))
    }

    @Test
    fun `Smeltery controller hover follows the pinned first Brass alloy route`() {
        val pin = Files.readString(root.resolve("mods/tinkers-construct.pw.toml"))
        val precision = Files.readString(root.resolve("kubejs/server_scripts/progression/30_precision_factory.js"))
        val audit = Files.readString(root.resolve("../workspace_artifacts/evidence/better-content-v8-20260919/ui10-brass-smeltery-audit.md").normalize())
        val row = registry.path("annotations").single { it.path("selector").path("item").asText() == "tconstruct:smeltery_controller" }
        val copy = row.path("lines").map { it.asText() }.single()

        assertTrue(pin.contains("TConstruct-1.20.1-3.11.2.166.jar"))
        assertTrue(pin.contains("94d07ec0ff45c49f5908beff7acb9fbec43fdde5"))
        assertTrue(precision.contains("event.remove({ type: 'create:mixing', output: 'create:brass_ingot' })"))
        assertTrue(audit.contains("data/tconstruct/recipes/smeltery/alloys/molten_brass.json"))
        assertTrue(audit.contains("90 mB molten copper") && audit.contains("90 mB molten zinc"))
        assertTrue(audit.contains("605 K") && audit.contains("180 mB molten brass"))
        assertTrue(row.path("owner").asText() == "kubejs/server_scripts/progression/30_precision_factory.js")
        assertTrue(copy.contains("full Smeltery") && copy.contains("first Brass"))
    }

    @Test
    fun `geological feed hovers follow all authored milling sifting and assay routes`() {
        val dataRoot = root.resolve("../mod_source/realistic-ores/src/main/resources/data/realistic_ores").normalize()
        fun json(path: String) = jacksonObjectMapper().readTree(dataRoot.resolve(path).toFile())
        fun tagValues(name: String) = json("tags/items/$name.json").path("values").map { it.asText() }.toSet()
        val chunks = tagValues("deposit_chunks")
        val crushed = tagValues("crushed_feeds")
        val rinsed = tagValues("rinsed_feeds")
        val families = setOf("coal_measures", "ironstone", "copper_bloom", "tin_quartz", "brassroot", "evaporite_beds", "hotstone", "black_shale")
        assertEquals(families.map { "realistic_ores:ore_chunk_$it" }.toSet(), chunks)
        assertEquals(families.map { "realistic_ores:crushed_$it" }.toSet(), crushed)
        assertEquals(families.map { "realistic_ores:rinsed_$it" }.toSet(), rinsed)

        families.forEach { family ->
            val chunk = "realistic_ores:ore_chunk_$family"
            val crushedFeed = "realistic_ores:crushed_$family"
            val rinsedFeed = "realistic_ores:rinsed_$family"
            val stringRoot = "recipes/compat/createsifter/sifting/$family/string"
            val dry = json("${stringRoot}_dry.json")
            val wet = json("${stringRoot}_wet.json")
            val milling = json("recipes/compat/create/milling/ore_chunks/$family.json")
            val rinsing = json("recipes/compat/create/rinsing/$family.json")
            val separation = json("recipes/compat/pneumaticcraft/separation/$family.json")

            assertEquals("createsifter:sifting", dry.path("type").asText())
            assertTrue(dry.path("ingredients").any { it.path("item").asText() == chunk })
            assertTrue(dry.path("ingredients").any { it.path("item").asText() == "createsifter:string_mesh" })
            assertTrue(dry.path("waterlogged").isMissingNode || !dry.path("waterlogged").asBoolean())
            assertTrue(wet.path("waterlogged").asBoolean())
            assertEquals("create:milling", milling.path("type").asText())
            assertTrue(milling.path("ingredients").any { it.path("item").asText() == chunk })
            assertTrue(milling.path("results").any { it.path("item").asText() == crushedFeed })
            assertEquals("create:filling", rinsing.path("type").asText())
            assertTrue(rinsing.path("ingredients").any { it.path("item").asText() == crushedFeed })
            assertTrue(rinsing.path("ingredients").any { it.path("fluid").asText() == "minecraft:water" && it.path("amount").asInt() == 250 })
            assertTrue(rinsing.path("results").any { it.path("item").asText() == rinsedFeed })
            assertEquals("pneumaticcraft:pressure_chamber", separation.path("type").asText())
            assertEquals(2.0, separation.path("pressure").asDouble())
            assertTrue(separation.path("inputs").any { it.path("item").asText() == rinsedFeed && it.path("count").asInt() == 4 })
            assertTrue(separation.path("results").isArray && separation.path("results").size() >= 2, "$family should expose assay outputs")
        }

        val registryRows = registry.path("annotations")
        val chunksHover = registryRows.single { it.path("selector").path("tag").asText() == "realistic_ores:deposit_chunks" }
        val crushedHover = registryRows.single { it.path("selector").path("tag").asText() == "realistic_ores:crushed_feeds" }
        val rinsedHover = registryRows.single { it.path("selector").path("tag").asText() == "realistic_ores:rinsed_feeds" }
        val fanHover = registryRows.single { it.path("selector").path("item").asText() == "create:encased_fan" }
        val processing = Files.readString(root.resolve("kubejs/server_scripts/processing/ore_sifting_and_spouting.js"))
        assertTrue(chunksHover.path("lines").toString().contains("String Mesh") && chunksHover.path("lines").toString().contains("milling creates more feed"))
        assertTrue(crushedHover.path("lines").toString().contains("waterlogged") && crushedHover.path("lines").toString().contains("Spout with water"))
        assertTrue(rinsedHover.path("lines").toString().contains("four matching feeds at 2 bar"))
        assertTrue(fanHover.path("lines").toString().contains("Water streams no longer process items"))
        assertTrue(processing.contains("event.remove({ type: 'create:splashing' })"))
    }

    @Test
    fun `surface sample hover describes collection without claiming deposit detection`() {
        val base = "../mod_source/realistic-ores/src/main/java/com/bettercontent/realisticores/"
        val blocks = Files.readString(root.resolve(base + "registry/ModBlocks.java").normalize())
        val sample = Files.readString(root.resolve(base + "block/SurfaceSampleBlock.java").normalize())
        val useBody = sample.substringAfter("public InteractionResult use(").substringBefore("    @Override\n    protected void createBlockStateDefinition")
        val definition = Files.readString(root.resolve(base + "ore/OreDefinition.java").normalize())
        val row = registry.path("annotations").single { it.path("concept_id").asText() == "geology.deposits.surface_sign" }
        val targets = row.path("selector").path("items").map { it.asText() }.toSet()
        val expectedTargets = setOf(
            "realistic_ores:surface_sample_black_shale", "realistic_ores:surface_sample_brassroot",
            "realistic_ores:surface_sample_coal_measures", "realistic_ores:surface_sample_copper_bloom",
            "realistic_ores:surface_sample_evaporite_beds", "realistic_ores:surface_sample_hotstone",
            "realistic_ores:surface_sample_ironstone", "realistic_ores:surface_sample_tin_quartz",
        )
        val owner = row.path("owner").asText()
        val copy = row.path("lines").map { it.asText() }.single()

        assertEquals(expectedTargets, targets)
        assertTrue(owner.contains("ModBlocks.java"))
        assertTrue(owner.contains("SurfaceSampleBlock.java"))
        assertTrue(owner.contains("OreDefinition.java"))
        assertTrue(definition.contains("small_ore_chunk_"))
        assertTrue(definition.contains("surface_sample_"))
        assertTrue(blocks.contains("definition.smallOreChunkItemId()"))
        assertTrue(blocks.contains("newSurfaceSample(collectedItemId, definition.id())"))
        assertTrue(sample.contains("if (collectedItemId == null || !player.mayBuild())"))
        assertTrue(sample.contains("ForgeRegistries.ITEMS.getValue(collectedItemId)"))
        assertTrue(sample.contains("level.setBlock(position, replacement, Block.UPDATE_ALL)"))
        assertTrue(sample.contains("new ItemStack(collectedItem)"))
        assertTrue(sample.contains("DepositSurveyEpisodes.sampleRead(serverPlayer, depositFamily)"))
        assertTrue(useBody.contains("level.setBlock(position, replacement, Block.UPDATE_ALL)"))
        assertTrue(!useBody.contains("getBlockState") && !useBody.contains("findDeposit") && !useBody.contains("scanDeposit"))
        assertEquals("Right-click to collect one matching small ore chunk; this sample does not locate a deposit.", copy)
    }

    @Test
    fun `critical curriculum systems retain natural hover anchors`() {
        val exact = exactTargets()
        val tags = tagTargets()
        val expectedExact = setOf(
            "dimension_drink:dimensional_font",
            "dimension_drink:return_seal",
            "bumblezone_cultivars:living_pollen_nursery",
            "water_survival:rain_collector",
            "mining_helmet:mining_helmet",
            "oc2r_wireless_pubsub:wireless_relay",
            "procedural_bouquets:bouquet_grid",
            "better_content_economy:sacred_reliquary",
            "malum:spirit_pouch",
            "ratlantis_logistics:courier_lattice",
            "rail_beetle:route_beacon",
            "player_traces:foot_traffic_probe",
            "tinkers_construct_affixes:affixed_part_cache",
            "create:schematicannon",
            "sereneseasons:calendar",
            "weather2:tornado_sensor",
            "adpother:aerometer",
            "bloodmagic:altar",
            "hexerei:mixing_cauldron",
            "ae2:blank_pattern",
            "ae2:pattern_provider",
            "creatingspace:rocket_controls",
            "iceandfire:dragonsteel_fire_ingot",
            "realistic_ores:surface_sample_hotstone",
        )
        val expectedTags = setOf(
            "bumblezone_cultivars:seeds",
            "dynamictrees:seeds",
            "realistic_ores:crushed_feeds",
            "realistic_ores:rinsed_feeds",
            "realistic_ores:radioactive_forms/uranium/hosted_ore_blocks",
            "realistic_ores:radioactive_forms/thorium/hosted_ore_blocks",
        )
        assertTrue((expectedExact - exact).isEmpty(), "missing exact anchors: ${(expectedExact - exact).sorted()}")
        assertTrue((expectedTags - tags).isEmpty(), "missing tag anchors: ${(expectedTags - tags).sorted()}")
    }

    @Test
    fun `Serene Seasons calendar and sensor hovers match pinned season state`() {
        val pin = Files.readString(root.resolve("mods/serene-seasons.pw.toml"))
        val auditPath = root.resolve("../workspace_artifacts/evidence/better-content-v8-20260919/ui10-season-sensor-audit.md").normalize()
        val audit = Files.readString(auditPath)
        val jarPath = root.resolve("generated/test-evidence/20260912T234942Z-4177725/multiplayer/fixture/server-extract/better-content-server/mods/SereneSeasons-forge-1.20.1-9.1.0.2.jar")
        val sha1 = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(jarPath)).joinToString("") { "%02x".format(it) }
        val calendarRow = registry.path("annotations").single { it.path("selector").path("item").asText() == "sereneseasons:calendar" }
        val row = registry.path("annotations").single { it.path("selector").path("item").asText() == "sereneseasons:season_sensor" }

        assertTrue(pin.contains("SereneSeasons-forge-1.20.1-9.1.0.2.jar"))
        assertTrue(pin.contains("706d3eaf79ce5b33a30f3ad1aaa88a2a4483abf6"))
        assertEquals("706d3eaf79ce5b33a30f3ad1aaa88a2a4483abf6", sha1)
        assertTrue(row.path("owner").asText().contains("mods/serene-seasons.pw.toml"))
        assertTrue(row.path("evidence").any { it.asText().endsWith("ui10-season-sensor-audit.md") })
        assertEquals("Reads the current season; crop suitability and weather still change over the seasonal cycle.", calendarRow.path("lines").single().asText())
        assertEquals("Outputs a 0–15 redstone signal that tracks progress through the current season.", row.path("lines").single().asText())
        assertTrue(audit.contains("CalendarItem.class") && audit.contains("getSeasonCycleTicks") && audit.contains("0–15"))

        ZipFile(jarPath.toFile()).use { jar ->
            val recipe = jacksonObjectMapper().readTree(jar.getInputStream(jar.getEntry("data/sereneseasons/recipes/season_sensor.json")))
            assertEquals("sereneseasons:season_sensor", recipe.path("result").path("item").asText())
            assertEquals("sereneseasons:calendar", recipe.path("key").path("C").path("item").asText())
            assertEquals("forge:gems/quartz", recipe.path("key").path("Q").path("tag").asText())
            assertEquals("forge:glass/colorless", recipe.path("key").path("G").path("tag").asText())
            val classEntry = jar.getInputStream(jar.getEntry("sereneseasons/block/SeasonSensorBlock.class")).use { it.readBytes() }
            val constants = classEntry.toString(Charsets.ISO_8859_1)
            listOf("getSeasonCycleTicks", "getSeasonDuration", "updatePower", "POWER", "SEASON").forEach {
                assertTrue(constants.contains(it), "pinned season sensor is missing $it")
            }
            val calendarClass = jar.getInputStream(jar.getEntry("sereneseasons/item/CalendarItem.class")).use { it.readBytes() }
            val calendarConstants = calendarClass.toString(Charsets.ISO_8859_1)
            listOf("getSeasonCycleTicks", "getSubSeason", "getTropicalSeason", "getDay").forEach {
                assertTrue(calendarConstants.contains(it), "pinned calendar is missing $it")
            }
        }
    }

    @Test
    fun `pollution hovers distinguish personal protection from local measurement`() {
        val pin = Files.readString(root.resolve("mods/pollution-of-the-realms.pw.toml"))
        val jarPath = root.resolve("generated/test-evidence/20260912T234942Z-4177725/multiplayer/fixture/server-extract/better-content-server/mods/AdPother-1.20.1-8.1.49.0-build.2294.jar")
        val sha1 = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(jarPath)).joinToString("") { "%02x".format(it) }
        val copy = "Protects the wearer from breathing pollution; it does not remove contamination from the world."
        val respiratorRow = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("items").any { it.asText() == "adpother:iron_respirator" }
        }
        val aerometerRow = registry.path("annotations").single { it.path("selector").path("item").asText() == "adpother:aerometer" }

        assertTrue(pin.contains("AdPother-1.20.1-8.1.49.0-build.2294.jar"))
        assertTrue(pin.contains("fa630492fadb60027176fd73a3a1a95c295f295d"))
        assertEquals("fa630492fadb60027176fd73a3a1a95c295f295d", sha1)
        assertEquals(copy, respiratorRow.path("lines").single().asText())
        assertEquals("Measures local airborne pollution; contamination still moves and must be filtered or cleaned.", aerometerRow.path("lines").single().asText())

        ZipFile(jarPath.toFile()).use { jar ->
            fun constants(path: String): String {
                val entry = checkNotNull(jar.getEntry(path)) { "Pinned AdPother artifact is missing $path" }
                return jar.getInputStream(entry).use { it.readBytes().toString(Charsets.ISO_8859_1) }
            }
            val aerometer = constants("com/endertech/minecraft/mods/adpother/items/Aerometer.class")
            val respirators = constants("com/endertech/minecraft/mods/adpother/init/Respirators.class")
            val builtIns = constants("com/endertech/minecraft/mods/adpother/init/Respirators\$BuiltIn.class")
            assertTrue(aerometer.contains("EntityPollution") && aerometer.contains("Pollutant"))
            assertTrue(aerometer.contains("GAS_DETECTION_RADIUS"))
            assertTrue(respirators.contains("findOn") && respirators.contains("updateEffectFor"))
            listOf("adpother\$iron_respirator", "adpother\$gold_respirator", "adpother\$diamond_respirator").forEach {
                assertTrue(builtIns.contains(it), "pinned respiratory registry is missing $it")
                assertTrue(respiratorRow.path("selector").path("items").any { item -> item.asText() == it.replace('$', ':') })
            }
        }
    }

    @Test
    fun `AdPother hovers follow pinned local readings and wearer protection`() {
        val pin = Files.readString(root.resolve("mods/pollution-of-the-realms.pw.toml"))
        val auditPath = root.resolve("../workspace_artifacts/evidence/better-content-v8-20260919/ui10-pollution-audit.md").normalize()
        val audit = Files.readString(auditPath)
        val jarPath = root.resolve("generated/test-evidence/20260912T234942Z-4177725/multiplayer/fixture/server-extract/better-content-server/mods/AdPother-1.20.1-8.1.49.0-build.2294.jar")
        val sha1 = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(jarPath)).joinToString("") { "%02x".format(it) }
        val aerometerRow = registry.path("annotations").single { it.path("selector").path("item").asText() == "adpother:aerometer" }
        val respiratorRow = registry.path("annotations").single {
            it.path("selector").path("items").any { item -> item.asText() == "adpother:iron_respirator" }
        }

        assertTrue(pin.contains("AdPother-1.20.1-8.1.49.0-build.2294.jar"))
        assertTrue(pin.contains("fa630492fadb60027176fd73a3a1a95c295f295d"))
        assertEquals("fa630492fadb60027176fd73a3a1a95c295f295d", sha1)
        assertEquals("Measures local airborne pollution; contamination still moves and must be filtered or cleaned.", aerometerRow.path("lines").single().asText())
        assertEquals("Protects the wearer from breathing pollution; it does not remove contamination from the world.", respiratorRow.path("lines").single().asText())
        assertTrue(audit.contains("EntityPollution") && audit.contains("RespiratorEffect"))

        ZipFile(jarPath.toFile()).use { jar ->
            fun constants(path: String): String {
                val entry = checkNotNull(jar.getEntry(path)) { "Pinned AdPother artifact is missing $path" }
                return jar.getInputStream(entry).use { it.readBytes().toString(Charsets.ISO_8859_1) }
            }
            val aerometer = constants("com/endertech/minecraft/mods/adpother/items/Aerometer.class")
            val respirators = constants("com/endertech/minecraft/mods/adpother/init/Respirators.class")
            val builtIns = constants("com/endertech/minecraft/mods/adpother/init/Respirators\$BuiltIn.class")
            assertTrue(aerometer.contains("EntityPollution") && aerometer.contains("getInfos") && aerometer.contains("getInfluenceOf"))
            assertTrue(respirators.contains("findOn") && respirators.contains("updateEffectFor") && respirators.contains("getEffectDurationFor"))
            listOf("adpother\$iron_respirator", "adpother\$gold_respirator", "adpother\$diamond_respirator").forEach { registryName ->
                assertTrue(builtIns.contains(registryName), "pinned respiratory registry is missing $registryName")
                assertTrue(respiratorRow.path("selector").path("items").any { it.asText() == registryName.replace('$', ':') })
            }
        }
    }

    @Test
    fun `Weather2 alerts and forecast hovers match pinned registered behavior`() {
        val pin = Files.readString(root.resolve("mods/weather-storms-tornadoes.pw.toml"))
        val auditPath = root.resolve("../workspace_artifacts/evidence/better-content-v8-20260919/ui10-weather2-audit.md").normalize()
        val audit = Files.readString(auditPath)
        val jarPath = root.resolve("generated/test-evidence/20260912T234942Z-4177725/multiplayer/fixture/server-extract/better-content-server/mods/weather2-1.20.1-2.8.3.jar")
        val sha1 = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(jarPath)).joinToString("") { "%02x".format(it) }
        val rows = registry.path("annotations")
        fun row(id: String) = rows.single { it.path("selector").path("item").asText() == id }

        assertTrue(pin.contains("weather2-1.20.1-2.8.3.jar"))
        assertTrue(pin.contains("4ec7a372945874dd734a20fa61574f08d2b6763a"))
        assertEquals("4ec7a372945874dd734a20fa61574f08d2b6763a", sha1)
        assertEquals("Outputs redstone while a forming tornado is within the configured siren distance.", row("weather2:tornado_sensor").path("lines").single().asText())
        assertEquals("Sounds nearby forming tornadoes and intense sand or snow storms; range is configurable.", row("weather2:tornado_siren").path("lines").single().asText())
        assertEquals("Right-click to inspect local deadly-storm odds; this is probability, not a live storm warning.", row("weather2:weather_forecast").path("lines").single().asText())
        assertTrue(rows.none { it.path("selector").path("item").asText() == "weather2:tornado_siren_manual" })
        assertTrue(audit.contains("tornado_siren_manual") && audit.contains("no corresponding registered object"))

        ZipFile(jarPath.toFile()).use { jar ->
            fun constants(path: String): String {
                val entry = checkNotNull(jar.getEntry(path)) { "Pinned Weather2 artifact is missing $path" }
                return jar.getInputStream(entry).use { it.readBytes().toString(Charsets.ISO_8859_1) }
            }
            val blocks = constants("weather2/WeatherBlocks.class")
            val items = constants("weather2/WeatherItems.class")
            val sensor = constants("weather2/blockentity/SensorBlockEntity.class")
            val siren = constants("weather2/blockentity/SirenBlockEntity.class")
            val forecast = constants("weather2/block/ForecastBlock.class")
            assertTrue(blocks.contains("BLOCK_TORNADO_SENSOR") && blocks.contains("BLOCK_TORNADO_SIREN") && blocks.contains("BLOCK_FORECAST"))
            assertTrue(!blocks.contains("BLOCK_TORNADO_SIREN_MANUAL"))
            assertTrue(items.contains("BLOCK_TORNADO_SENSOR_ITEM") && items.contains("BLOCK_TORNADO_SIREN_ITEM") && items.contains("BLOCK_FORECAST_ITEM"))
            assertTrue(!items.contains("BLOCK_TORNADO_SIREN_MANUAL_ITEM"))
            assertTrue(sensor.contains("getClosestStorm") && sensor.contains("STATE_FORMING") && sensor.contains("sirenActivateDistance"))
            assertTrue(siren.contains("streaming.siren") && siren.contains("STATE_FORMING") && siren.contains("getClosestParticleStormByIntensity"))
            assertTrue(forecast.contains("getBiomeBasedStormSpawnChanceInArea") && forecast.contains("Chance of a deadly storm here every:"))
            listOf("tornado_sensor", "tornado_siren", "weather_forecast").forEach { name ->
                val recipe = jacksonObjectMapper().readTree(jar.getInputStream(checkNotNull(jar.getEntry("data/weather2/recipes/$name.json"))))
                assertEquals("weather2:$name", recipe.path("result").path("item").asText())
            }
        }
    }

    @Test
    fun `AE2 pattern hover distinguishes blank encoding from external processing`() {
        val pin = Files.readString(root.resolve("mods/applied-energistics-2.pw.toml"))
        val audit = Files.readString(root.resolve("../workspace_artifacts/evidence/better-content-v8-20260919/ae2-pattern-role-audit.md").normalize())
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("items").any { it.asText() == "ae2:blank_pattern" }
        }
        val items = row.path("selector").path("items").map { it.asText() }.toSet()
        val copy = row.path("lines").map { it.asText() }.single()

        assertEquals(setOf("ae2:blank_pattern", "ae2:crafting_pattern", "ae2:processing_pattern"), items)
        assertTrue(pin.contains("appliedenergistics2-forge-15.4.10.jar"))
        assertTrue(pin.contains("ac255a120499f79f8deade474da09e3c00ff9bad"))
        assertTrue(audit.contains("`BLANK_PATTERN` is `ItemDefinition<MaterialItem>`"))
        assertTrue(audit.contains("`CRAFTING_PATTERN` is `ItemDefinition<CraftingPatternItem>`"))
        assertTrue(audit.contains("`PROCESSING_PATTERN` is `ItemDefinition<ProcessingPatternItem>`"))
        assertTrue(copy.contains("Encode a Blank Pattern"))
        assertTrue(copy.contains("processing sends inputs to an external machine"))
        assertTrue(!copy.contains("execute the craft"))
        assertEquals("mods/applied-energistics-2.pw.toml", row.path("owner").asText())
        assertEquals("ae2", row.path("required_mod").asText())
    }

    @Test
    fun `native AE2 and rocket annotations name their active providers`() {
        val ae2Pin = Files.readString(root.resolve("mods/applied-energistics-2.pw.toml"))
        val rocketPin = Files.readString(root.resolve("mods/create-creating-space.pw.toml"))
        val orbit = jacksonObjectMapper().readTree(
            root.resolve("kubejs/data/creatingspace/creatingspace/rocket_accessible_dimension/earth_orbit.json").toFile(),
        )
        val providers = registry.path("annotations").single { row ->
            row.path("selector").path("items").any { it.asText() == "ae2:pattern_provider" }
        }
        val controls = registry.path("annotations").single { row ->
            row.path("selector").path("item").asText() == "creatingspace:rocket_controls"
        }

        assertEquals("mods/applied-energistics-2.pw.toml", providers.path("owner").asText())
        assertEquals("mods/create-creating-space.pw.toml; kubejs/data/creatingspace/creatingspace/rocket_accessible_dimension/earth_orbit.json", controls.path("owner").asText())
        assertTrue(ae2Pin.contains("file-id = 7148487"))
        assertTrue(rocketPin.contains("file-id = 7850072"))
        assertTrue(orbit.path("adjacentDimensions").size() > 0)
        assertTrue(orbit.path("adjacentDimensions").all { it.path("deltaV").asInt() > 0 })
        assertTrue(controls.path("lines").single().asText().contains("destination, thrust, and delta-v"))
    }

    @Test
    fun `AE2 autocrafting and native ReHooked hovers stay within pinned behavior`() {
        val ae2Pin = Files.readString(root.resolve("mods/applied-energistics-2.pw.toml"))
        val rehookedPin = Files.readString(root.resolve("mods/rehooked.pw.toml"))
        val ae2 = registry.path("annotations").single { row ->
            row.path("selector").path("items").any { it.asText() == "ae2:pattern_provider" }
        }
        val hooks = mapOf(
            "rehooked:iron_hook" to "Can fire two hooks; retract to recall a deployed hook.",
            "rehooked:diamond_hook" to "Can fire four hooks; retract to recall a deployed hook.",
            "rehooked:blaze_hook" to "Blaze hooks can set nearby entities on fire; retract to recall a deployed hook.",
            "rehooked:ender_hook" to "The Ender Hook has a 96-block range in default settings; retract to recall it.",
            "rehooked:red_hook" to "Can fire three hooks; retract to recall a deployed hook.",
        )

        assertTrue(ae2Pin.contains("file-id = 7148487"))
        assertTrue(ae2Pin.contains("ac255a120499f79f8deade474da09e3c00ff9bad"))
        assertEquals("mods/applied-energistics-2.pw.toml", ae2.path("owner").asText())
        assertEquals(
            "Autocrafting needs patterns, ingredients, and powered crafting machinery; storage alone is not automation.",
            ae2.path("lines").single().asText(),
        )

        assertTrue(rehookedPin.contains("file-id = 6341096"))
        assertTrue(rehookedPin.contains("e4fe79c242ee562364afc7e90d280d56c068ba21"))
        hooks.forEach { (item, expectedCopy) ->
            val row = registry.path("annotations").single { it.path("selector").path("item").asText() == item }
            assertEquals("mods/rehooked.pw.toml", row.path("owner").asText(), item)
            assertTrue(row.path("lines").any { it.asText() == expectedCopy }, item)
            assertEquals("rehooked", row.path("required_mod").asText(), item)
        }
    }

    @Test
    fun `Rail Beetle annotations teach baseline power and tier progression`() {
        val sourceRoot = root.resolve("../mod_source/rail-beetle/src/main/java/com/bettercontent/railbeetle/entity").normalize()
        val entity = Files.readString(sourceRoot.resolve("RailBeetleEntity.java"))
        val power = Files.readString(sourceRoot.resolve("BeetlePower.java"))
        val engineKinds = Files.readString(root.resolve("../mod_source/rail-beetle/src/main/java/com/bettercontent/railbeetle/upgrade/EngineKind.java").normalize())
        val planner = Files.readString(root.resolve("../mod_source/rail-beetle/src/main/java/com/bettercontent/railbeetle/navigation/TerrainRoutePlanner.java").normalize())
        val recipes = Files.readString(root.resolve("kubejs/server_scripts/transport/10_optional_engineering_roots.js"))
        val vehicle = registry.path("annotations").single { it.path("selector").path("item").asText() == "rail_beetle:rail_beetle" }
        val beacon = registry.path("annotations").single { it.path("selector").path("item").asText() == "rail_beetle:route_beacon" }
        val engines = registry.path("annotations").single { it.path("selector").path("items").any { item -> item.asText() == "rail_beetle:steam_drive" } }
        val tierTwo = registry.path("annotations").single { it.path("selector").path("items").any { item -> item.asText() == "rail_beetle:high_speed_governor_2" } }

        assertTrue(vehicle.path("lines").any { it.asText() == "Surveys, drives, and builds rails and shallow bridges." })
        assertTrue(vehicle.path("lines").any { it.asText() == "Uses engine power first, then fuel from its working inventory." })
        assertTrue(engines.path("lines").any { it.asText() == "Stores fuel or energy on the engine item." })
        assertTrue(engines.path("lines").any { it.asText() == "Switches to the built-in coal firebox when depleted." })
        assertTrue(tierTwo.path("lines").any { it.asText() == "Crafting Tier II consumes the matching Tier I module." })
        val expectedEngineIds = setOf(
            "steam_drive", "flux_traction_motor", "source_impeller", "lifeforce_ram",
            "pneumatic_expansion_motor", "soul_combustor", "spirit_warping_engine",
        )
        val annotatedEngineIds = engines.path("selector").path("items").map { it.asText().substringAfter(':') }.toSet()
        val engineKindIds = Regex("\\w+\\(\"([a-z0-9_]+)\"")
            .findAll(engineKinds).map { it.groupValues[1] }.toSet()
        assertEquals(expectedEngineIds, annotatedEngineIds)
        assertTrue(engineKindIds.containsAll(expectedEngineIds), "every annotated engine must have an EngineKind")
        val expectedTierTwo = mapOf(
            "high_speed_governor_2" to "high_speed_governor_1",
            "brake_manifold_2" to "brake_manifold_1",
            "adhesion_sanders_2" to "adhesion_sanders_1",
            "compound_torque_clutch_2" to "compound_torque_clutch_1",
            "reinforced_drawgear_2" to "reinforced_drawgear_1",
            "trestle_erector_2" to "trestle_erector_1",
            "exhaust_recuperator_2" to "exhaust_recuperator_1",
            "telescopic_survey_array_2" to "telescopic_survey_array_1",
            "dispatch_receiver_2" to "dispatch_receiver_1",
        )
        val tierTwoRecipes = Regex("railBeetle(?:Electrical)?TierTwo\\('([^']+)', '([^']+)'")
            .findAll(recipes).associate { it.groupValues[1] to it.groupValues[2] }
        val annotatedTierTwoIds = tierTwo.path("selector").path("items").map { it.asText().substringAfter(':') }.toSet()
        assertEquals(expectedTierTwo.keys, annotatedTierTwoIds)
        assertEquals(expectedTierTwo, tierTwoRecipes.filterKeys(expectedTierTwo.keys::contains))
        assertTrue(beacon.path("lines").any { it.asText() == "Place beside a reachable rail endpoint to add a Beetle route; it does not control other trains." })
        assertTrue(beacon.path("owner").asText().contains("navigation/TerrainRoutePlanner.java"))
        assertTrue(planner.contains("if (state.is(RailBeetleRegistries.ROUTE_BEACON.get())) beacons.add(immutable)"))
        assertTrue(planner.contains(".limit(MAX_BEACON_ROUTES)"))
        assertTrue(planner.contains("RouteKind.BEACON, path.beacon"))
        assertTrue(planner.contains("!level.getBlockState(proposal.beaconTarget()).is(RailBeetleRegistries.ROUTE_BEACON.get())"))
        assertTrue(planner.contains("if (beacon == null || proposal.endpoint().getY() != beacon.getY()"))
        assertTrue(planner.contains("Math.abs(proposal.endpoint().getX() - beacon.getX())"))
        assertTrue(entity.contains("private boolean consumeWork(WorkAction action, int rawWork)"))
        assertTrue(entity.contains("if (!BeetlePower.consumeEngine(engineStack, inventory, work)) return false"))
        assertTrue(entity.contains("int refill = BeetleSupplies.takeFuel(inventory)"))
        assertTrue(power.contains("static boolean consumeEngine(ItemStack engineStack"))
        assertTrue(power.contains("case STEAM -> refillBurnable"))
        assertTrue(recipes.contains("'rail_beetle:' + prior"))
        assertTrue(recipes.contains("railBeetleTierTwo('high_speed_governor_2', 'high_speed_governor_1'"))
        assertTrue(recipes.contains("railBeetleElectricalTierTwo('dispatch_receiver_2', 'dispatch_receiver_1'"))
    }

    @Test
    fun `optional engineering hovers match authored capability roots`() {
        val roots = Files.readString(root.resolve("kubejs/server_scripts/transport/10_optional_engineering_roots.js"))
        val rows = registry.path("annotations")
        val eurekaHelms = setOf(
            "vs_eureka:oak_ship_helm",
            "vs_eureka:spruce_ship_helm",
            "vs_eureka:birch_ship_helm",
            "vs_eureka:jungle_ship_helm",
            "vs_eureka:acacia_ship_helm",
            "vs_eureka:dark_oak_ship_helm",
        )
        val expected = mapOf(
            "vs_eureka:oak_ship_helm" to "Use to pilot a ship. Ship travel is optional.",
            "vs_clockwork:andesite_flap_bearing" to "Use for moving parts on Clockwork vehicles.",
            "vs_clockwork:brass_propeller_bearing" to "Adds propeller propulsion to Clockwork vehicles.",
            "vs_clockwork:air_compressor" to "Use compressed air for Clockwork flight.",
            "vs_clockwork:gyro" to "Adds active stabilization to advanced Clockwork vehicles.",
            "vs_clockwork:gas_thruster" to "Adds gas-thruster propulsion to Clockwork vehicles.",
            "trackwork:simple_wheel" to "Wheel for basic Trackwork vehicles. Vehicle building is optional.",
            "trackwork:track_level_controller" to "Adds active track-level control to Trackwork vehicles.",
        )
        expected.forEach { (id, copy) ->
            val row = rows.single { annotation ->
                annotation.path("selector").path("item").asText() == id ||
                    annotation.path("selector").path("items").any { it.asText() == id }
            }
            assertTrue(row.path("owner").asText().contains("10_optional_engineering_roots.js"), id)
            assertTrue(row.path("lines").any { it.asText() == copy }, id)
        }

        val eurekaHover = rows.single { annotation ->
            annotation.path("concept_id").asText() == "transport.optional_engineering.capability_root" &&
                annotation.path("required_mod").asText() == "vs_eureka"
        }
        assertEquals(eurekaHelms, eurekaHover.path("selector").path("items").map { it.asText() }.toSet())

        assertTrue(roots.contains("if (Platform.isLoaded('vs_eureka'))"))
        assertTrue(roots.contains("event.remove({ id: helm })"))
        assertTrue(roots.contains(";['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'crimson', 'warped'].forEach(function (wood) {"))
        assertTrue(roots.contains(";['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak'].forEach(function (wood) {"))
        assertTrue(roots.contains("kubejs:transport/hand_workshop/eureka/"))
        assertTrue(roots.contains("if (Platform.isLoaded('vs_clockwork'))"))
        assertTrue(roots.contains("kubejs:copper_machine_block"))
        assertTrue(roots.contains("kubejs:brass_machine_block"))
        assertTrue(roots.contains("kubejs:airtight_machine_block"))
        assertTrue(roots.contains("kubejs:electrical_machine_block"))
        assertTrue(roots.contains("if (Platform.isLoaded('trackwork'))"))
        assertTrue(roots.contains("event.shaped('trackwork:simple_wheel'"))
        assertTrue(roots.contains("event.shaped('trackwork:track_level_controller'"))
    }

    @Test
    fun `Heat Sync hovers match network firebox coolant and transducer behavior`() {
        val sourceRoot = root.resolve("../mod_source/heat-sync/src/main/kotlin/com/bettercontent/heatsync").normalize()
        val pipe = Files.readString(sourceRoot.resolve("content/heat/HeatPipeBlockEntity.kt"))
        val controller = Files.readString(sourceRoot.resolve("HeatSyncPipeThermalController.kt"))
        val firebox = Files.readString(sourceRoot.resolve("content/heat/ThermalFireboxBlockEntity.kt"))
        val exchanger = Files.readString(sourceRoot.resolve("content/coolant/CoolantExchangerBlockEntity.kt"))
        val transducer = Files.readString(sourceRoot.resolve("content/energy/ImpossibleMatterTransducerBlockEntity.kt"))
        val expectations = mapOf(
            "heat_sync:heat_pipe" to "Stores and equalizes network heat while exchanging with its surroundings.",
            "heat_sync:thermal_firebox" to "Burns ordinary furnace fuel into network heat; it does not generate rotation.",
            "heat_sync:coolant_exchanger" to "Transfers heat by converting hot and cold fluids. Hold the Ponder key to see the setup.",
            "heat_sync:impossible_matter_transducer" to "Unbinds adjacent Impossible Matter into AE power and heat; requires FE containment and a working heat sink.",
        )
        expectations.forEach { (id, copy) ->
            val row = registry.path("annotations").single {
                it.path("selector").path("item").asText() == id
            }
            assertTrue(row.path("lines").any { it.asText() == copy }, id)
        }
        assertTrue(registry.path("annotations").single { it.path("selector").path("item").asText() == "heat_sync:heat_pipe" }
            .path("owner").asText().contains("HeatSyncPipeThermalController.kt"))

        assertTrue(pipe.contains("fun addHeat(heat: Float)"))
        assertTrue(pipe.contains("HeatBlockEntity.transferAround(this)"))
        assertTrue(controller.contains("PipeThermalMath.step("))
        assertTrue(controller.contains("AmbientHeatSampling.samplePipeHeat(level, pipe.blockPos)"))
        assertTrue(controller.contains("resolveNeighborAverage(level, pipe.blockPos, pipe, snapshot)"))
        assertTrue(firebox.contains("ForgeHooks.getBurnTime(stack, RecipeType.SMELTING)"))
        assertTrue(firebox.contains("HeatBlockEntity.transferAround(this)"))
        assertTrue(firebox.contains("burnTicks = duration"))
        assertTrue(exchanger.contains("CoolantExchangeLogic.computeExchange("))
        assertTrue(exchanger.contains("tank.setFluid(FluidStack(converted, amount))"))
        assertTrue(exchanger.contains("heat = exchange.resultingHeat"))
        assertTrue(transducer.contains("feStorage.extractEnergy(plan.containmentFe, false)"))
        assertTrue(transducer.contains("source.unbind(plan.units, false)"))
        assertTrue(transducer.contains("pendingAe += actual.ae()"))
        assertTrue(transducer.contains("if (heat > maxHeat())"))
    }

    @Test
    fun `wood storage hovers follow distinct barrel and chest recipe costs`() {
        val source = Files.readString(root.resolve("kubejs/server_scripts/compat/retained/refactor__balance__172_storage_recipe_distinction.js"))
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("items").any { it.asText() == "sophisticatedstorage:oak_barrel" }
        }
        val targets = row.path("selector").path("items").map { it.asText() }.toSet()
        val copy = row.path("lines").map { it.asText() }.single()
        val woods = listOf("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped")

        assertEquals(setOf("sophisticatedstorage:oak_barrel", "sophisticatedstorage:oak_chest"), targets)
        assertEquals("Wood barrels use a plank ring; wood chests use a vanilla chest. Storage capacity stays native.", copy)
        assertTrue(row.path("owner").asText().contains("refactor__balance__172_storage_recipe_distinction.js"))
        assertTrue(source.contains("if (!Platform.isLoaded('sophisticatedstorage')) return"))
        assertTrue(source.contains("event.remove({ id: barrelId })") && source.contains("event.remove({ id: chestId })"))
        assertTrue(source.contains("['PPP', 'PLP', 'PPP']"))
        assertTrue(source.contains("P: 'minecraft:' + wood + '_planks', L: 'minecraft:lever'"))
        assertTrue(source.contains("[' C ', ' L ', '   ']"))
        assertTrue(source.contains("C: 'minecraft:chest', L: 'minecraft:lever'"))
        woods.forEach { wood ->
            assertTrue(source.contains("'" + wood + "'"), "missing wood recipe for $wood")
        }
        assertTrue(!source.contains("setSlotCount") && !source.contains("setStackSize"))
    }

    @Test
    fun `bed and campfire hovers follow permanent spawn and local heat rules`() {
        val respawn = Files.readString(root.resolve("../mod_source/class-selector/src/main/kotlin/com/bettercontent/classselector/respawn/PersonalRespawnEvents.kt").normalize())
        val snowMelt = Files.readString(root.resolve("../mod_source/water-survival/src/main/java/com/bettercontent/watersurvival/SnowMeltHandler.java").normalize())
        val coldSweat = Files.readString(root.resolve("config/coldsweat/world.toml"))
        val bedRow = registry.path("annotations").single { it.path("selector").path("tag").asText() == "minecraft:beds" }
        val campfireRow = registry.path("annotations").single { it.path("selector").path("item").asText() == "minecraft:campfire" }

        assertEquals(listOf("Skips the night; does not change your permanent spawn."), bedRow.path("lines").map { it.asText() })
        assertTrue(respawn.contains("if (!event.isForced && event.newSpawn != null)"))
        assertTrue(respawn.contains("event.isCanceled = true"))
        assertEquals(listOf("Melts nearby snow; its warmth reaches only a local area."), campfireRow.path("lines").map { it.asText() })
        assertTrue(campfireRow.path("owner").asText().contains("SnowMeltHandler.java"))
        assertTrue(campfireRow.path("owner").asText().contains("config/coldsweat/world.toml"))
        assertTrue(snowMelt.contains("private static final long MELT_DELAY_TICKS = 5L * 20L"))
        assertTrue(snowMelt.contains("campfirePos.offset(1, 1, 1)"))
        assertTrue(snowMelt.contains("state.getValue(CampfireBlock.LIT)"))
        assertTrue(coldSweat.contains("[\"#minecraft:campfires\", 8, 7, \"c\", 25, \"lit=true\""))
    }

    @Test
    fun `thermometer hover reflects configured access to exact temperature readings`() {
        val config = Files.readString(root.resolve("config/coldsweat/item.toml"))
        val row = registry.path("annotations").single { it.path("selector").path("item").asText() == "cold_sweat:thermometer" }
        assertEquals("Body temperature and world temperature are separate readings.", row.path("lines").single().asText())
        assertEquals("config/coldsweat/item.toml", row.path("owner").asText())
        assertTrue(config.contains("\"Require Thermometer\" = true"))
        assertTrue(config.contains("exact world and body temperature"))
    }

    @Test
    fun `Dimension Font hover matches empty hand activation and return seal behavior`() {
        val source = Files.readString(root.resolve("../mod_source/dimension-drink/src/main/kotlin/com/bettercontent/dimensiondrink/content/ObeliskBlock.kt").normalize())
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("items").any { it.asText() == "dimension_drink:return_seal" }
        }
        val targets = row.path("selector").path("items").map { it.asText() }.toSet()

        assertEquals(setOf("dimension_drink:dimensional_font", "dimension_drink:return_seal"), targets)
        assertEquals(listOf("Use empty-handed for Font travel; the seal ends the current round trip early."), row.path("lines").map { it.asText() })
        assertTrue(row.path("owner").asText().contains("ObeliskBlock.kt"))
        assertTrue(source.contains("if (returnOnly)"))
        assertTrue(source.contains("if (!held.isEmpty || player.isShiftKeyDown) return InteractionResult.PASS"))
        assertTrue(source.contains("RunRegistry.drinkReturnFont(serverPlayer)"))
        assertTrue(source.contains("if (!held.isEmpty)"))
        assertTrue(source.contains("RunRegistry.activateObelisk(serverPlayer, obelisk, pos)"))
    }

    @Test
    fun `Malum natural quartz hover follows the pinned Forge gem tag`() {
        val pin = Files.readString(root.resolve("mods/malum.pw.toml"))
        val jar = root.resolve("generated/test-evidence/20260912T234942Z-4177725/multiplayer/fixture/server-extract/better-content-server/mods/malum-1.20.1-1.6.7.jar")
        val digest = java.security.MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(jar))
            .joinToString("") { "%02x".format(it) }
        val row = registry.path("annotations").single { it.path("selector").path("item").asText() == "malum:natural_quartz" }

        assertTrue(pin.contains("filename = \"malum-1.20.1-1.6.7.jar\""))
        assertTrue(pin.contains("hash = \"ecf1d9a6e4e69119f9583f90b9587107a75d8195\""))
        assertEquals("ecf1d9a6e4e69119f9583f90b9587107a75d8195", digest)
        assertEquals("Native Malum quartz joins the Forge quartz gem tag for compatible industrial recipes.", row.path("lines").single().asText())
        assertEquals("mods/malum.pw.toml", row.path("owner").asText())
        ZipFile(jar.toFile()).use { archive ->
            val tagEntry = archive.getEntry("data/forge/tags/items/gems/quartz.json")
            assertTrue(tagEntry != null, "pinned Malum quartz must contribute to the Forge quartz tag")
            val tagJson = archive.getInputStream(tagEntry).bufferedReader().use { it.readText() }
            assertTrue(tagJson.contains("malum:natural_quartz"))
        }
    }

    @Test
    fun `Create transmission hover follows configured part cost and speed scaling`() {
        val config = Files.readString(root.resolve("config/create_transmission_loss-common.toml"))
        val sourceRoot = root.resolve("../mod_source/create-transmission-loss/src/main/kotlin/com/bettercontent/createtransmissionloss").normalize()
        val scanner = Files.readString(sourceRoot.resolve("network/NetworkScanner.kt"))
        val configSource = Files.readString(sourceRoot.resolve("config/Config.kt"))
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("items").any { it.asText() == "create:shaft" }
        }
        val targets = row.path("selector").path("items").map { it.asText() }.toSet()

        assertEquals(setOf("create:shaft", "create:cogwheel", "create:large_cogwheel", "create:gearbox", "create:vertical_gearbox", "create:belt_connector"), targets)
        assertEquals("Transmission parts consume SU; larger, faster networks cost more capacity.", row.path("lines").single().asText())
        assertTrue(row.path("owner").asText().contains("create_transmission_loss-common.toml"))
        assertTrue(row.path("owner").asText().contains("NetworkScanner.kt"))
        assertTrue(config.contains("enabled = true"))
        assertTrue(config.contains("speedMode = \"LINEAR\""))
        assertTrue(config.contains("cogwheel = 0.16") && config.contains("largeCogwheel = 0.24"))
        assertTrue(config.contains("gearbox = 0.32"))
        assertTrue(scanner.contains("breakdown.cogwheels * TransmissionLossConfig.cogwheelValue()"))
        assertTrue(scanner.contains("breakdown.largeCogwheels * TransmissionLossConfig.largeCogwheelValue()"))
        assertTrue(scanner.contains("return base * TransmissionLossConfig.speedMultiplier(breakdown.rpm)"))
        assertTrue(configSource.contains("SpeedMode.LINEAR -> 1.0 + valueOrDefault(k, DEFAULT_K) * normalized"))
    }

    @Test
    fun `Ratlantis bait hover distinguishes taming from ordinary cheese`() {
        val source = Files.readString(root.resolve("../mod_source/ratlantis-logistics/src/main/java/com/bettercontent/ratlantislogistics/RatlantisLogistics.java").normalize())
        val row = registry.path("annotations").single { it.path("selector").path("item").asText() == "ratlantis_logistics:ratlantean_bait" }
        assertEquals("Use to tame wild rats. Ordinary cheese cannot tame them.", row.path("lines").single().asText())
        assertTrue(row.path("owner").asText().contains("RatlantisLogistics.java"))
        assertTrue(source.contains("heldId.equals(new ResourceLocation(\"rats\", \"cheese\"))"))
        assertTrue(source.contains("if (held.getItem() != RATLANTEAN_BAIT.get()) return"))
        assertTrue(source.contains("rat.wildTrust += 10 + rat.getRandom().nextInt(10)"))
        assertTrue(source.contains("rat.cheeseFeedings >= 15"))
        assertTrue(source.contains("RatUtils.tameRat(rat, rat.level())"))
    }

    @Test
    fun `Heart Block hover matches fragment installation and adjacent altar output`() {
        val sourceRoot = root.resolve("../mod_source/rpg-stats/src/main/kotlin/com/bettercontent/rpgstats/common").normalize()
        val block = Files.readString(sourceRoot.resolve("block/HeartBlock.kt"))
        val entity = Files.readString(sourceRoot.resolve("block/entity/HeartBlockEntity.kt"))
        val row = registry.path("annotations").single { it.path("selector").path("item").asText() == "rpg_stats:heart_block" }

        assertEquals("Place beside a Blood Altar; use Heart Fragments to increase LP production.", row.path("lines").single().asText())
        assertTrue(row.path("owner").asText().contains("block/HeartBlock.kt"))
        assertTrue(row.path("owner").asText().contains("block/entity/HeartBlockEntity.kt"))
        assertTrue(block.contains("held.`is`(ModItems.HEART_FRAGMENT.get())"))
        assertTrue(block.contains("heart.installOneFragment()"))
        assertTrue(block.contains("held.shrink(1)"))
        assertTrue(entity.contains("as? IBloodAltar"))
        assertTrue(entity.contains("HeartBlockEmission.fill(heart.installedFragments, altar::fillMainTank)"))
    }

    @Test
    fun `Blood Altar and Hexerei cauldron hovers match authored bootstrap routes`() {
        val altarSource = Files.readString(root.resolve("kubejs/server_scripts/compat/retained/refactor__balance__82_blood_magic_lifeforce_rework.js"))
        val cauldronSource = Files.readString(root.resolve("kubejs/server_scripts/compat/retained/refactor__balance__63_fonts_hexerei_occultism_chalk.js"))
        val altar = registry.path("annotations").single { it.path("selector").path("item").asText() == "bloodmagic:altar" }
        val cauldron = registry.path("annotations").single { it.path("selector").path("item").asText() == "hexerei:mixing_cauldron" }

        assertEquals("Craft from ordinary materials; a Heart Block beside it supplies LP.", altar.path("lines").single().asText())
        assertTrue(altar.path("owner").asText().contains("refactor__balance__82_blood_magic_lifeforce_rework.js"))
        assertTrue(altarSource.contains("event.remove({ id: 'bloodmagic:blood_altar' })"))
        assertTrue(altarSource.contains("event.shaped('bloodmagic:altar'"))
        listOf("minecraft:obsidian", "minecraft:gold_block", "minecraft:bone_block", "minecraft:furnace", "minecraft:deepslate", "minecraft:cauldron")
            .forEach { assertTrue(altarSource.contains(it), it) }
        assertEquals("Craft from iron and a cauldron. Four Font trophies are for impure chalk.", cauldron.path("lines").single().asText())
        assertTrue(cauldron.path("owner").asText().contains("refactor__balance__63_fonts_hexerei_occultism_chalk.js"))
        assertTrue(cauldronSource.contains("event.shaped('hexerei:mixing_cauldron'"))
        assertTrue(cauldronSource.contains("I: 'minecraft:iron_ingot',"))
        assertTrue(cauldronSource.contains("C: 'minecraft:cauldron'"))
        assertTrue(cauldronSource.contains("Every impure chalk preparation proves all four expedition Fonts equally"))
        listOf("aether:ambrosium_shard", "minecraft:blaze_powder", "the_bumblezone:honey_crystal_shards", "rats:gem_of_ratlantis")
            .forEach { assertTrue(cauldronSource.contains(it), it) }
    }

    @Test
    fun `Pretty Pipes annotations expose all three visible Ratlantis gates`() {
        val text = Files.readString(root.resolve("kubejs/config/hover_annotations.json"))
        val recipes = Files.readString(root.resolve("kubejs/server_scripts/progression/20_ratlantis_logistics.js"))
        assertTrue(text.contains("four lattices make the first 32 pipes"))
        assertTrue(text.contains("Oratchalcum Mechanism"))
        assertTrue(text.contains("Arcane Logistics Core"))
        val highModules = listOf(
            "high_crafting_module", "high_extraction_module", "high_filter_module",
            "high_high_priority_module", "high_low_priority_module", "high_retrieval_module",
            "high_speed_module",
        )
        highModules.forEach { assertTrue(text.contains("prettypipes:$it"), it) }

        val pipe = registry.path("annotations").single { it.path("selector").path("item").asText() == "prettypipes:pipe" }
        val blank = registry.path("annotations").single { it.path("selector").path("item").asText() == "prettypipes:blank_module" }
        val advanced = registry.path("annotations").single { it.path("selector").path("items").any { item -> item.asText() == "prettypipes:high_speed_module" } }
        val mechanism = registry.path("annotations").single { it.path("selector").path("item").asText() == "ratlantis_logistics:oratchalcum_mechanism" }
        val logisticsCore = registry.path("annotations").single { it.path("selector").path("item").asText() == "ratlantis_logistics:arcane_logistics_core" }
        assertTrue(pipe.path("owner").asText().contains("20_ratlantis_logistics.js"))
        assertTrue(blank.path("owner").asText().contains("20_ratlantis_logistics.js"))
        assertTrue(advanced.path("owner").asText().contains("20_ratlantis_logistics.js"))
        assertTrue(mechanism.path("owner").asText().contains("20_ratlantis_logistics.js"))
        assertTrue(logisticsCore.path("owner").asText().contains("20_ratlantis_logistics.js"))
        assertTrue(pipe.path("lines").single().asText().contains("four lattices make the first 32 pipes"))
        assertTrue(blank.path("lines").single().asText().contains("Oratchalcum Mechanism"))
        assertTrue(advanced.path("lines").single().asText().contains("Arcane Logistics Core"))
        assertTrue(mechanism.path("lines").single().asText().contains("ordinary pipe modules and Create request-network integration"))
        assertTrue(logisticsCore.path("lines").single().asText().contains("high-tier pipe modules and advanced logistics automation"))
        assertTrue(recipes.contains("event.shaped('8x prettypipes:pipe'"))
        assertTrue(recipes.contains("L: 'ratlantis_logistics:courier_lattice'"))
        assertTrue(recipes.contains("M: 'ratlantis_logistics:oratchalcum_mechanism'"))
        assertTrue(recipes.contains("C: 'ratlantis_logistics:arcane_logistics_core'"))
        assertTrue(recipes.contains("event.shapeless('create:redstone_requester'"))
        assertTrue(recipes.contains("event.remove({ output: 'ae2:energy_acceptor' })"))
        highModules.forEach { assertTrue(recipes.contains("$it: {"), it) }
    }

    @Test
    fun `early handling annotations expose the primitive and automatic boundary`() {
        val text = Files.readString(root.resolve("kubejs/config/hover_annotations.json"))
        val primitive = Files.readString(root.resolve("kubejs/server_scripts/progression/00_primitive_workshop.js"))
        val ratlantis = Files.readString(root.resolve("kubejs/server_scripts/progression/20_ratlantis_logistics.js"))
        val precision = Files.readString(root.resolve("kubejs/server_scripts/progression/30_precision_factory.js"))
        assertTrue(text.contains("Passive vertical transfer from canvas and iron fittings"))
        assertTrue(text.contains("Automatic crafting begins after Create's Mechanical Crafter."))
        val chute = registry.path("annotations").single { it.path("selector").path("item").asText() == "create:chute" }
        assertTrue(chute.path("owner").asText().contains("00_primitive_workshop.js"))
        assertTrue(primitive.contains("event.shaped('4x create:chute'"))
        assertTrue(primitive.contains("N: '#forge:nuggets/iron', C: 'farmersdelight:canvas'"))
        val crafter = registry.path("annotations").single { it.path("selector").path("item").asText() == "quark:crafter" }
        assertEquals("Automatic crafting begins after Create's Mechanical Crafter.", crafter.path("lines").single().asText())
        assertTrue(crafter.path("owner").asText().contains("30_precision_factory.js"))
        assertTrue(precision.contains("event.remove({ id: 'quark:automation/crafting/crafter' })"))
        assertTrue(precision.contains("M: 'create:mechanical_crafter'"))
        val rapidHopper = registry.path("annotations").single { row ->
            row.path("selector").path("item").asText() == "littlelogistics:rapid_hopper"
        }
        val copy = rapidHopper.path("lines").map { it.asText() }.joinToString(" ")
        assertTrue(ratlantis.contains("event.remove({ id: 'littlelogistics:rapid_hopper' })"))
        assertTrue(ratlantis.contains("event.shaped('littlelogistics:rapid_hopper'"))
        assertTrue(ratlantis.contains("L: 'ratlantis_logistics:courier_lattice'"))
        assertTrue(rapidHopper.path("owner").asText().contains("20_ratlantis_logistics.js"))
        assertTrue(copy.contains("requires a Courier Lattice from Ratlantis"))
    }

    @Test
    fun `pressure chamber interface has one annotation that preserves both route instructions`() {
        val row = registry.path("annotations").single { annotation ->
            annotation.path("selector").path("item").asText() == "pneumaticcraft:pressure_chamber_interface"
        }
        val lines = row.path("lines").map { it.asText() }
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("PneumaticCraft, not Create"))
        assertTrue(lines[1].contains("air path"))
        assertTrue(lines[1].contains("native machine rules"))
        assertTrue(row.path("owner").asText().contains("45_acid_chemistry.js"))
        assertTrue(row.path("owner").asText().contains("81_pneumaticcraft_progression.js"))
    }

    @Test
    fun `Malum bootstrap annotations follow the authored Black Shale and gold ritual routes`() {
        val routeScript = Files.readString(root.resolve("kubejs/server_scripts/progression/50_malum_soulstone_bootstrap.js"))
        assertTrue(routeScript.contains("realistic_ores:black_shale"))
        assertTrue(routeScript.contains("heatRequirement: 'heated'"))
        assertTrue(routeScript.contains("output: { item: 'malum:raw_soulstone' }"))

        val soulstone = registry.path("annotations").single { row ->
            row.path("selector").path("items").any { it.asText() == "malum:raw_soulstone" }
        }
        val soulstoneCopy = soulstone.path("lines").map { it.asText() }.joinToString(" ")
        assertEquals("kubejs/server_scripts/progression/50_malum_soulstone_bootstrap.js", soulstone.path("owner").asText())
        assertTrue(soulstoneCopy.contains("Black Shale"))
        assertTrue(soulstoneCopy.contains("heated Hexerei cauldron"))
        assertTrue(soulstoneCopy.contains("smelt it into processed Soulstone"))

        val ritual = jacksonObjectMapper().readTree(root.resolve("kubejs/data/malum/ritual_recipes/cthonic_conversion.json").toFile())
        assertEquals("malum:cthonic_conversion", ritual.path("ritual_identifier").asText())
        assertEquals("minecraft:gold_block", ritual.path("input").path("item").asText())
        val cthonic = registry.path("annotations").single { row ->
            row.path("selector").path("item").asText() == "malum:cthonic_gold"
        }
        val cthonicCopy = cthonic.path("lines").map { it.asText() }.joinToString(" ")
        assertEquals("kubejs/data/malum/ritual_recipes/cthonic_conversion.json", cthonic.path("owner").asText())
        assertTrue(cthonicCopy.contains("ordinary gold blocks"))
        assertTrue(cthonicCopy.contains("exact catalysts"))

        val growth = registry.path("annotations").single { row ->
            row.path("selector").path("item").asText() == "malum:soulwood_growth"
        }
        val growthCopy = growth.path("lines").single().asText()
        val growthOwner = "kubejs/data/kubejs/recipes/malum/soulwood_growth_from_blighted_gunk.json"
        val growthRecipe = jacksonObjectMapper().readTree(root.resolve(growthOwner).toFile())
        assertTrue(growth.path("owner").asText().contains(growthOwner))
        assertTrue(growthCopy.contains("Blighted Gunk") && growthCopy.contains("tier-zero Blood Altar"))
        assertTrue(growthCopy.contains("2,000 LP"))
        assertEquals("malum:blighted_gunk", growthRecipe.path("input").path("item").asText())
        assertEquals("malum:soulwood_growth", growthRecipe.path("output").path("item").asText())
        assertEquals(2000, growthRecipe.path("altarSyphon").asInt())
    }

    @Test
    fun `tooltip compiler consumes v2 concepts without changing the item-local surface`() {
        val script = Files.readString(root.resolve("kubejs/client_scripts/guidance/10_hover_annotations.js"))
        assertTrue(script.contains("bc.hover_annotations.v2"))
        assertTrue(script.contains("var conceptId = String(row.concept_id || '')"))
        assertTrue(script.contains("conceptId: conceptId"))
        assertTrue(script.contains("ItemEvents.tooltip"))
    }

    @Test
    fun `bundled Threads exposes optional owned lessons and no obsolete dodge teaching`() {
        val learningSurfaceGuide = Files.readString(root.resolve("docs/learning_surfaces.md"))
        val jar = root.resolve("mods/better-content-threads-1.1.0.jar")
        ZipFile(jar.toFile()).use { zip ->
            fun json(path: String) = zip.getInputStream(zip.getEntry(path)).bufferedReader().use { reader ->
                jacksonObjectMapper().readTree(reader)
            }
            val lessons = json("assets/better_content_threads/loading_briefs/catalogue.json")
            val threads = json("data/better_content_threads/threads/catalogue.json")
            assertEquals("bc.loading_briefs.v3", lessons.path("schema").asText())
            assertEquals(18, lessons.path("briefs").size())
            assertEquals(17, lessons.path("briefs").map { it.path("art").asText() }.distinct().size)
            assertTrue(learningSurfaceGuide.contains("rotation share ${lessons.path("briefs").size()} lessons"))
            assertTrue(Files.readString(root.resolve("docs/threads.md")).contains("${lessons.path("briefs").size()} loading/Lessons entries"))
            val sourceLessons = jacksonObjectMapper().readTree(
                root.resolve("../mod_source/better-content-threads/src/main/resources/assets/better_content_threads/loading_briefs/catalogue.json")
                    .normalize().toFile(),
            )
            assertEquals(sourceLessons.path("briefs").size(), lessons.path("briefs").size())
            assertEquals(17, sourceLessons.path("briefs").map { it.path("art").asText() }.distinct().size)
            val concepts = threads.path("threads").associate {
                it.path("id").asText() to it.path("concept_id").asText()
            }
            lessons.path("briefs").forEach { lesson ->
                assertTrue(lesson.path("concept_id").asText().matches(Regex("[a-z0-9_.]{3,80}")))
                assertTrue(lesson.path("owner").asText().isNotBlank())
                lesson.path("related_thread").asText().takeIf(String::isNotEmpty)?.let { related ->
                    assertEquals(lesson.path("concept_id").asText(), concepts[related], lesson.path("id").asText())
                }
            }
            val movement = lessons.path("briefs").first { it.path("id").asText() == "movement" }.path("body").asText()
            assertTrue(movement.contains("directional double-tap dodging is disabled"))
            assertTrue(!movement.contains("double taps also dodge"))
        }

        val client = ZipFile(jar.toFile()).use { zip ->
            val entry = zip.getEntry("com/bettercontent/threads/ThreadClient.class")
            zip.getInputStream(entry).readBytes().toString(Charsets.ISO_8859_1)
        }
        assertTrue(!client.contains("keepReading"))
    }

    private fun exactTargets(): Set<String> = registry.path("annotations").flatMap { row ->
        val selector = row.path("selector")
        when {
            selector.has("item") -> listOf(selector.path("item").asText())
            selector.has("items") -> selector.path("items").map { it.asText() }
            else -> emptyList()
        }
    }.toSet()

    private fun tagTargets(): Set<String> = registry.path("annotations")
        .map { it.path("selector") }
        .filter { it.has("tag") }
        .map { it.path("tag").asText() }
        .toSet()
}
