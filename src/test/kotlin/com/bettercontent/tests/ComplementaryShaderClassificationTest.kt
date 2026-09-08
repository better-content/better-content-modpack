package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.zip.ZipFile

@Tag("fast")
class ComplementaryShaderClassificationTest {
    private val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
    private val archive = root.resolve("shaderpacks/ComplementaryReimagined_r5.8.1.zip")

    @Test
    fun rigidBlocksAreNotClassifiedAsWigglingGrass() {
        ZipFile(archive.toFile()).use { zip ->
            val entry = zip.getEntry("shaders/block.properties")
                ?: error("active Complementary archive lacks shaders/block.properties")
            val properties = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            val activeLine = properties.lineSequence()
                .single { it.startsWith("block.10005=") && "aether:berry_bush" in it }
            val active = activeLine.substringAfter('=').split(' ').filter(String::isNotBlank).toSet()

            val rigid = explicitRigidBlocks() + swemFlowerBoxes() + tconstructSlimeGrass()
            assertEquals(81, rigid.size, "classification audit scope changed")
            assertTrue(active.intersect(rigid).isEmpty(), "rigid blocks still use grass vertex motion")
            assertEquals(312, active.size, "unexpected active grass-class membership")

            val flexible = setOf(
                "short_grass", "fern", "sweet_berry_bush", "wheat", "torchflower_crop",
                "aether:berry_bush", "ars_nouveau:magebloom_crop", "blue_skies:brumble_vine",
                "burnt:burnt_high_grass", "deeperdarker:sculk_vines", "farmersdelight:tomatoes",
                "natures_spirit:willow_vines", "quark:water_pink_petals",
                "tconstruct:earth_slime_fern", "tconstruct:sky_slime_vine",
                "twilightforest:huge_water_lily", "undergarden:droopvine")
            assertTrue(active.containsAll(flexible), "flexible vegetation lost its motion class")

            val legacy = properties.lineSequence()
                .single { it.startsWith("block.10005=") && "double_plant:half=lower" in it }
            assertTrue("double_plant:half=lower" in legacy)
            assertFalse(zip.entries().asSequence().map { it.name }.toList().hasDuplicates(),
                "archive contains duplicate entries")
        }
    }

    private fun explicitRigidBlocks() = setOf(
        "burnt:burnt_cactus", "burnt:burnt_grass", "burnt:burnt_mangrove_roots",
        "burnt:recovering_grass", "burnt:smoldering_cactus", "burnt:smoldering_grass",
        "burnt:smoldering_grass_start", "burnt:smoldering_mangrove_roots",
        "callfromthedepth_:silenttreedoor", "deeperdarker:gloomy_cactus",
        "dtarsnouveau:blue_archwood_root", "dtarsnouveau:green_archwood_root",
        "dtarsnouveau:purple_archwood_root", "dtarsnouveau:red_archwood_root",
        "dtarsnouveau:yellow_archwood_root", "dtnatures_spirit:mahogany_root",
        "dtnatures_spirit:redwood_root", "dtquark:blossom_branch",
        "dtquark:stripped_blossom_branch", "dynamictrees:dark_oak_root",
        "dynamictrees:jungle_root", "dynamictrees:mangrove_roots",
        "dynamictreesplus:cactus_branch", "framedblocks:framed_flower_pot",
        "iceandfire:chared_grass", "iceandfire:crackled_grass", "iceandfire:frozen_grass",
        "notreepunching:clay_flower_pot", "procedural_bouquets:bouquet_grid",
        "quark:blossom_bookshelf", "quark:blue_blossom_hedge",
        "quark:flowering_azalea_hedge", "quark:lavender_blossom_hedge",
        "quark:orange_blossom_hedge", "quark:red_blossom_hedge",
        "quark:yellow_blossom_hedge", "the_finley_dimension_remastered:living_grass",
        "the_flesh_that_hates:flesh_grass", "twilightforest:mangrove_root",
        "twilightforest:root")

    private fun swemFlowerBoxes(): Set<String> = setOf(
        "black", "blue", "brown", "cyan", "gray", "green", "light_blue", "light_gray",
        "lime", "magenta", "orange", "pink", "purple", "red", "white", "yellow")
        .mapTo(linkedSetOf()) { "swem:jump_flower_box_$it" }

    private fun tconstructSlimeGrass(): Set<String> = setOf("blood", "earth", "ender", "ichor", "sky")
        .flatMapTo(linkedSetOf()) { earth ->
            setOf("earth", "ender", "ichor", "sky", "vanilla").map { slime ->
                "tconstruct:${earth}_${slime}_slime_grass"
            }
        }

    private fun <T> List<T>.hasDuplicates(): Boolean = size != toSet().size
}
