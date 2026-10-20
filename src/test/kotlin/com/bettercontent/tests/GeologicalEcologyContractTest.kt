package com.bettercontent.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Authored pack contracts, not a native registry/spawning/gameplay verdict. */
@Tag("fast")
class GeologicalEcologyContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root", ".")).toAbsolutePath().normalize()
    private val mapper = jacksonObjectMapper()
    private fun json(path: String) = mapper.readTree(root.resolve(path).toFile())
    private fun definitions(): Map<String, JsonNode> = Files.list(root.resolve("kubejs/data/better_cave_encounters/geological_ecologies")).use { paths ->
        paths.filter { it.fileName.toString().endsWith(".json") }.sorted().toList().associate {
            it.fileName.toString().removeSuffix(".json") to mapper.readTree(it.toFile())
        }
    }
    private fun ids(entries: JsonNode) = entries.map { it["entity"].asText() }.toSet()
    private val additions = setOf(
        "goety:bone_spider", "iceandfire:dread_thrall", "cataclysm:draugr", "twilightforest:slime_beetle", "twilightforest:kobold",
        "cataclysm:koboleton", "twilightforest:pinch_beetle", "alexscaves:corrodent", "goety:crypt_slime", "iceandfire:dread_beast",
        "cataclysm:the_watcher", "goety:border_wraith", "iceandfire:ghost", "cataclysm:endermaptera", "iceandfire:dread_scuttler",
        "goety:haunted_armor", "cataclysm:elite_draugr", "cataclysm:royal_draugr", "born_in_chaos_v1:fallen_chaos_knight", "iceandfire:troll"
    )
    private val rocks = mapOf(
        "ivory_ossuary" to setOf("white_granite", "quartzite"), "ochre_tombs" to setOf("beige_limestone"),
        "karst_nests" to setOf("limestone", "grey_limestone"), "rubble_burrows" to setOf("conglomerate"),
        "silt_carrion" to setOf("mudstone", "siltstone"), "red_hunting_grounds" to setOf("rhyolite", "weathered_rhyolite"),
        "banded_sentinels" to setOf("schist"), "silver_haunt" to setOf("phyllite"), "mineral_rifts" to setOf("granodiorite"),
        "black_stalking_caves" to setOf("slate", "gabbro", "pillow_basalt")
    )
    private val ordinary = mapOf(
        "ivory_ossuary" to setOf("born_in_chaos_v1:baby_skeleton", "born_in_chaos_v1:decrepit_skeleton", "goety:bone_spider", "iceandfire:dread_thrall"),
        "ochre_tombs" to setOf("goety:rattled", "goety:frayed", "cataclysm:koboleton", "cataclysm:draugr"),
        "karst_nests" to setOf("minecraft:silverfish", "minecraft:spider", "twilightforest:pinch_beetle", "twilightforest:slime_beetle"),
        "rubble_burrows" to setOf("minecraft:silverfish", "goety:web_spider", "alexscaves:corrodent", "twilightforest:kobold"),
        "silt_carrion" to setOf("born_in_chaos_v1:corpse_fly", "born_in_chaos_v1:baby_spider", "goety:crypt_slime"),
        "red_hunting_grounds" to setOf("born_in_chaos_v1:dread_hound", "born_in_chaos_v1:baby_spider", "iceandfire:dread_beast"),
        "banded_sentinels" to setOf("goety:frayed", "born_in_chaos_v1:decrepit_skeleton"),
        "silver_haunt" to setOf("goety:wraith", "born_in_chaos_v1:restless_spirit", "goety:border_wraith", "iceandfire:ghost"),
        "mineral_rifts" to setOf("endermanoverhaul:cave_enderman", "minecraft:endermite", "cataclysm:endermaptera"),
        "black_stalking_caves" to setOf("minecraft:spider", "born_in_chaos_v1:dread_hound", "alexscaves:corrodent", "iceandfire:dread_scuttler")
    )
    @Test fun tenHabitatsUseOnlyTheSixteenExplicitRawBlocks() {
        val definitions = definitions()
        assertEquals(rocks.keys, definitions.keys)
        val seen = mutableSetOf<String>()
        definitions.forEach { (habitat, definition) ->
            assertEquals(1, definition["schema_version"].asInt())
            assertEquals("better_cave_encounters:geology/$habitat", definition["raw_rock_tag"].asText())
            val tag = json("kubejs/data/better_cave_encounters/tags/blocks/geology/$habitat.json")
            val actual = tag["values"].map { if (it.isTextual) it.asText() else it["id"].asText() }.toSet()
            assertEquals(rocks.getValue(habitat).map { "unearthed:$it" }.toSet(), actual)
            actual.forEach { assertTrue(seen.add(it), "Overlapping habitat block: $it") }
            assertEquals(ordinary.getValue(habitat), ids(definition["ordinary"]))
            assertTrue(ids(definition["director"]).containsAll(ids(definition["ordinary"])))
            assertFalse(definition["ordinary"].any { it["role"].asText() == "heavy" })
            assertEquals(when (habitat) { "silver_haunt" -> .25; "mineral_rifts" -> .5; else -> 1.0 }, definition["ordinary_admission"].asDouble())
        }
        // Directional schist is one axis-valued native block, not three registry IDs.
        assertEquals(16, seen.size)
        assertFalse(seen.any { it.contains("schist_") || it.contains("weathered_granite") || it.contains("regolith") })
    }
    @Test fun exactlyTwentyNewSpeciesAugmentAllFortyExistingNonDeepVoidSpecies() {
        val historical = listOf("undead", "carrion", "spirits", "sculk", "end").flatMap {
            json("kubejs/data/better_cave_encounters/director_ecologies/$it.json")["roster"].toList()
        }.associateBy { it["entity"].asText() }
        val entries = definitions().values.flatMap { it["director"].toList() }
        val current = entries.map { it["entity"].asText() }.toSet()
        assertEquals(40, historical.size)
        assertEquals(20, additions.size)
        assertEquals(historical.keys + additions, current)
        assertEquals(60, current.size)
        assertTrue(current.contains("born_in_chaos_v1:skeleton_demoman"))
        assertFalse(current.contains("alexscaves:watcher"))
        val fields = listOf("role", "minimum_depth", "cost", "maximum_per_packet", "maximum_per_encounter", "weight")
        entries.filter { it["entity"].asText() in historical }.forEach { entry ->
            val old = historical.getValue(entry["entity"].asText())
            fields.forEach { assertEquals(old[it], entry[it], "Changed legacy $it for ${entry["entity"]}") }
        }
        entries.groupBy { it["entity"].asText() }.values.forEach { copies ->
            for (field in fields + "offspring" + "condition") assertEquals(1, copies.map { it[field] }.distinct().size)
        }
        val forbidden = json("../mod_source/better-cave-encounters/src/main/resources/data/better_cave_encounters/tags/entity_types/denied.json")["values"]
            .filter { it.isTextual || it.has("id") }.map { if (it.isTextual) it.asText() else it["id"].asText() }.toSet()
        assertTrue(current.intersect(forbidden).isEmpty())
        assertTrue(json("kubejs/data/better_cave_encounters/director_ecologies/deep_void.json")["roster"].any { it["entity"].asText() == "cataclysm:kobolediator" })
    }
    @Test fun initialNewProfilesAndOrdinaryWeightsAreExplicit() {
        val common = setOf("goety:bone_spider", "iceandfire:dread_thrall", "cataclysm:draugr", "twilightforest:slime_beetle", "twilightforest:kobold")
        val elite = setOf("goety:haunted_armor", "cataclysm:elite_draugr", "cataclysm:royal_draugr")
        val heavy = setOf("born_in_chaos_v1:fallen_chaos_knight", "iceandfire:troll")
        definitions().values.forEach { habitat ->
            habitat["director"].filter { it["entity"].asText() in additions }.forEach { entry ->
                val id = entry["entity"].asText()
                val expected = when (id) { in common -> listOf("common", .15, 3, 2, 2, 6); in elite -> listOf("line", .55, 1, 6, 1, 2); in heavy -> listOf("heavy", .68, 1, 10, 1, 1); else -> listOf("line", .35, 2, 4, 1, 4) }
                assertEquals(expected[0], entry["role"].asText()); assertEquals(expected[1], entry["minimum_depth"].asDouble())
                listOf("weight", "cost", "maximum_per_packet", "maximum_per_encounter").forEachIndexed { i, field -> assertEquals(expected[i + 2], entry[field].asInt()) }
            }
            habitat["ordinary"].forEach { entry ->
                val id = entry["entity"].asText()
                assertEquals(if (id in common) 3 else if (id in additions) 1 else 8, entry["weight"].asInt())
            }
            habitat["director"].forEach { entry ->
                when (entry["entity"].asText()) { "minecraft:drowned" -> assertEquals("water_nearby", entry["condition"].asText()); "minecraft:stray" -> assertEquals("cold", entry["condition"].asText()) }
            }
        }
    }
    @Test fun corpseAndSlimeReservationsAreAdditionalFrozenFiniteCosts() {
        val policies = definitions().values.flatMap { it["director"].toList() }.associateBy { it["entity"].asText() }
        mapOf("barrel_zombie" to 3, "door_knight" to 2, "zombie_clown" to 3, "zombie_lumberjack" to 2, "zombie_bruiser" to 4, "fallen_chaos_knight" to 2).forEach { (name, count) ->
            val offspring = policies.getValue("born_in_chaos_v1:$name")["offspring"]
            assertEquals(count, offspring["credits"].asInt())
            assertEquals(count, offspring["children"]["born_in_chaos_v1:maggot"].asInt())
        }
        for (id in listOf("goety:crypt_slime", "tconstruct:ender_slime")) {
            val offspring = policies.getValue(id)["offspring"]
            assertEquals(4, offspring["maximum_slime_size"].asInt()); assertEquals(20, offspring["credits"].asInt())
            assertEquals(20, offspring["children"][id].asInt())
        }
    }
    @Test fun nativeQualificationInputsAndFallbackPoliciesStayBoundToAuthoredPackData() {
        val source = "../mod_source/better-cave-encounters"
        val qualification = json("$source/src/worldGameTest/resources/geological-qualification.json")
        assertEquals(additions, qualification["additions"].map { it.asText() }.toSet())
        assertEquals(25, qualification["archives"].size())
        val pins = qualification["archives"].map { it["pin"].asText() }.toSet()
        assertTrue(pins.all { Files.isRegularFile(root.resolve(it)) })
        assertTrue("mods/in-control.pw.toml" in pins)
        val declared = definitions().values.flatMap { it["director"].toList() }.filter { it.has("offspring") }
            .associate { it["entity"].asText() to it["offspring"] }
        assertEquals(11, declared.size)
        assertEquals(declared, qualification["reproductive_actors"].fields().asSequence().associate { it.key to it.value })
        val frozen = json("$source/src/main/resources/data/better_cave_encounters/director_lineage_policies/native.json")["actors"]
        assertEquals(declared, frozen.fields().asSequence().associate { it.key to it.value })
        val node = json("gradle/active-custom-mods.json")["mods"].first { it["repository"].asText() == "better-cave-encounters" }
        val inputs = node["verificationInputs"].map { it.asText() }.toSet()
        assertTrue(inputs.containsAll(pins + qualification["pack_inputs"].map { it.asText() }))
        assertTrue(node["tasks"].any { it.asText() == "verifyWorld" }, "all-target native qualification must gate future staged candidates")
        assertEquals(json("kubejs/data/better_compat_fixes/tags/entity_types/legacy_underground_ambient_denied.json"),
            json("../mod_source/better-compat-fixes/src/nativeFixture/resources/data/better_compat_fixes/tags/entity_types/legacy_underground_ambient_denied.json"))
    }
    @Test fun onlyTheSupersededInControlRuleWasMigrated() {
        val expected = mapper.readTree("""[
          {"mob":"minecraft:creeper","result":"deny"},
          {"mob":["cataclysm:aptrgangr","cataclysm:coralssus","cataclysm:ignited_revenant","cataclysm:the_prowler","cataclysm:ender_golem","cataclysm:kobolediator","cataclysm:wadjet","cataclysm:amethyst_crab","cataclysm:clawdian"],"spawntype":"natural","result":"deny"},
          {"mob":["thalassophobia:sunfish","thalassophobia:catfish","thalassophobia:big_catfish","thalassophobia:shark","thalassophobia:bull_shark","thalassophobia:goblin_shark","thalassophobia:hammerhead_shark","thalassophobia:whale_shark","thalassophobia:whale","minecraft:camel","minecraft:panda","minecraft:polar_bear"],"spawntype":"natural","result":"deny"}
        ]""")
        assertEquals(expected, json("config/incontrol/spawn.json"))
        val legacy = json("kubejs/data/better_compat_fixes/tags/entity_types/legacy_underground_ambient_denied.json")["values"]
        val names = legacy.map { assertFalse(it["required"].asBoolean()); it["id"].asText() }.toSet()
        val expectedNames = setOf("born_in_chaos_v1:baby_skeleton", "born_in_chaos_v1:decrepit_skeleton", "goety:rattled", "goety:frayed", "born_in_chaos_v1:dread_hound", "born_in_chaos_v1:skeleton_demoman", "born_in_chaos_v1:barrel_zombie", "born_in_chaos_v1:door_knight", "born_in_chaos_v1:zombie_clown", "born_in_chaos_v1:zombie_lumberjack", "born_in_chaos_v1:skeleton_thrasher", "born_in_chaos_v1:zombie_bruiser", "quark:forgotten", "born_in_chaos_v1:corpse_fly", "born_in_chaos_v1:baby_spider", "goety:web_spider", "born_in_chaos_v1:bloody_gadfly", "born_in_chaos_v1:restless_spirit", "quark:wraith", "goety:wraith", "goety:muck_wraith", "born_in_chaos_v1:dark_vortex", "goety:reaper", "born_in_chaos_v1:seared_spirit", "tconstruct:ender_slime", "endermanoverhaul:cave_enderman", "endermanoverhaul:end_enderman", "endermanoverhaul:end_islands_enderman")
        assertEquals(expectedNames, names)
        assertEquals(28, names.size)
    }
}
