package com.bettercontent.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

@Tag("fast")
class CategoryEraProgressionContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
    private val mapper = jacksonObjectMapper()

    @Test
    fun `executed handlers enforce family pairs bootstrap branches and structural counts`() {
        // Same bounded Node/VM mechanism as PrettyPipesRecipeContractTest. The
        // fixture asserts resulting recipes, not merely source-string presence.
        val directory = Files.createDirectories(root.resolve("build/tmp/category-era-contract"))
        val log = Files.createTempFile(directory, "handlers-", ".json")
        val process = ProcessBuilder("node", root.resolve("src/test/resources/category-era-progression-contract.cjs").toString(), root.toString())
            .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start()
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Bounded handler deadline")
            assertEquals(0, process.exitValue(), Files.readString(log))
            assertTrue(Files.size(log) < 1024 * 1024, "Bounded fixture output")
            val result = mapper.readTree(log.toFile())
            assertEquals("category-era-contract-passed", result.path("status").asText())
            assertEquals(10, result.path("refractoryPairs").asInt())
            assertEquals(listOf("creatingspace:air_liquefier"), result.path("structuralConsumers").map { it.asText() })
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Fixture process shutdown")
            }
            // Evidence is retained on success and failure, never cleaned here.
        }
    }

    @Test
    fun `pinned Unearthed acquisition edges and Schist identity match the narrow whitelist`() {
        pinnedArchive("unearthed", "5cf92a5a4fef9166501b43609b38d24f56c76dba").use { zip ->
            val families = setOf("gabbro", "slate", "cobbled_slate", "phyllite", "cobbled_phyllite", "quartzite", "schist")
            families.forEach { assertNotNull(zip.getEntry("assets/unearthed/models/item/$it.json"), it) }
            assertNull(zip.getEntry("assets/unearthed/models/item/cobbled_gabbro.json"))
            assertNull(zip.getEntry("assets/unearthed/models/item/cobbled_quartzite.json"))
            val nativeOutputs = zip.entries().asSequence().filter {
                it.name.startsWith("data/unearthed/recipes/") && it.name.endsWith(".json")
            }.mapNotNull { entry ->
                val result = read(zip, entry.name).path("result")
                val item = if (result.isTextual) result.asText() else result.path("item").asText()
                if (item.removePrefix("unearthed:") in families) entry.name.substringAfter("recipes/").removeSuffix(".json") else null
            }.toSet()
            val cuts = setOf("gabbro", "slate_from_smooth_stone", "quartzite_from_smooth_sandstone",
                "quartzite_from_smooth_red_sandstone", "phyllite_from_slate", "schist_from_phyllite")
            assertEquals(cuts + setOf("slate_from_cobbled_slate", "phyllite_from_cobbled_phyllite"), nativeOutputs,
                "Every native production edge is either an exact cut or same-family restoration")
            val hand = Files.readString(root.resolve("kubejs/server_scripts/progression/10_hand_workshop.js"))
            cuts.forEach { assertTrue(hand.contains("'unearthed:$it'"), it) }
            for (family in listOf("slate", "phyllite")) {
                val restoration = read(zip, "data/unearthed/recipes/${family}_from_cobbled_$family.json")
                assertEquals("unearthed:cobbled_$family", restoration.path("ingredient").path("item").asText())
                assertEquals("unearthed:$family", restoration.path("result").asText())
            }
            val loot = read(zip, "data/unearthed/loot_tables/blocks/schist.json")
            assertEquals(listOf("unearthed:schist"), loot.findValuesAsText("name"))
            // Axes belong to a single blockstate/loot identity, never three items.
            assertNotNull(zip.getEntry("assets/unearthed/blockstates/schist.json"))
            assertFalse(zip.entries().asSequence().any { it.name.matches(Regex("assets/unearthed/models/item/schist_[xyz]\\.json")) })
        }
    }

    @Test
    fun `pinned Create support recipes and moving models explain workshop overrides and small roots`() {
        pinnedArchive("create", "b13d912b9247a38d66d11598c121442585a1c1e9").use { zip ->
            // Native Basin AND Depot contain alloy: both must be replaced, not
            // treated as unexplained ordinary-workshop leaf items.
            val hand = Files.readString(root.resolve("kubejs/server_scripts/progression/20_powered_works.js"))
            for (support in listOf("basin", "depot")) {
                assertTrue(read(zip, "data/create/recipes/crafting/kinetics/$support.json").toString().contains("create:andesite_alloy"))
                assertTrue(hand.contains("event.remove({ output: 'create:$support' })"))
            }
            for (metal in listOf("iron", "copper")) {
                val pressing = read(zip, "data/create/recipes/pressing/${metal}_ingot.json")
                assertEquals("forge:ingots/$metal", pressing.path("ingredients")[0].path("tag").asText())
                assertEquals("create:${metal}_sheet", pressing.path("results")[0].path("item").asText())
            }
            val pipe = read(zip, "data/create/recipes/crafting/kinetics/fluid_pipe.json")
            assertEquals(setOf("forge:ingots/copper", "forge:plates/copper"), pipe.path("key").map { it.path("tag").asText() }.toSet())
            val models = mapOf(
                "deployer/horizontal" to Pair(2, 12.0), "mechanical_press/block" to Pair(1, 14.0),
                "mechanical_pump/block" to Pair(0, 12.0), "steam_engine/block" to Pair(1, 15.0),
                "track_station/block" to Pair(1, 13.0),
            )
            models.forEach { (model, expected) ->
                assertEquals(expected.second, extent(read(zip, "assets/create/models/block/$model.json"), expected.first), model)
            }
            // Moving hands, shafts, press head and millstone insert do not turn
            // a narrow housing or hollow grinding body into a full cube.
            assertTrue(extent(read(zip, "assets/create/models/block/deployer/pole.json"), 0) < 16)
            assertTrue(extent(read(zip, "assets/create/models/block/mechanical_press/head.json"), 0) < 16)
            val millBody = read(zip, "assets/create/models/block/millstone/block.json")
            assertFalse(millBody.path("elements").any(::fullCube))
            assertEquals(12.0, extent(read(zip, "assets/create/models/block/millstone/inner.json"), 1))
        }
    }

    @Test
    fun `pinned aerospace placed models distinguish full liquefier body from exposed assemblies`() {
        pinnedArchive("create-creating-space", "7b341b9c32fec9fcf020ae6d8429c59917415f7b").use { zip ->
            assertEquals(13.5, extent(read(zip, "assets/creatingspace/models/block/rocket_engineer_table.json"), 1))
            assertEquals(12.0, extent(read(zip, "assets/creatingspace/models/block/rocket_engineer_table.json"), 2))
            assertEquals(14.0, extent(read(zip, "assets/creatingspace/models/block/mechanical_electrolyzer/block.json"), 1))
            // Deliberately use the live placed model, not legacy or item models.
            for (orientation in listOf("horizontal", "vertical")) {
                val body = read(zip, "assets/creatingspace/models/block/air_liquefier/$orientation.json")
                assertTrue(body.path("elements").any(::fullCube), orientation)
            }
            val moving = read(zip, "assets/creatingspace/models/block/air_liquefier/propeller.json")
            assertFalse(moving.path("elements").any(::fullCube))
        }
    }

    private fun pinnedArchive(pinName: String, expectedSha1: String): ZipFile {
        val pin = Files.readString(root.resolve("mods/$pinName.pw.toml"))
        assertTrue(pin.contains("hash = \"$expectedSha1\""), "$pinName pin changed; re-inspect native resources")
        val filename = Regex("filename = \"([^\"]+)\"").find(pin)!!.groupValues[1]
        val home = Path.of(System.getProperty("user.home"))
        val cache = Path.of(System.getProperty("bc.native.archive.cache", home.resolve(".cache/bc/packwiz-downloads/mods").toString()))
        val candidates = listOf(cache.resolve(filename)) + if (pinName == "unearthed") listOf(home.resolve(
            ".gradle/caches/modules-2/files-2.1/curse.maven/unearthed-406825/7742307/$expectedSha1/unearthed-406825-7742307.jar",
        )) else emptyList()
        val archive = candidates.firstOrNull { Files.isRegularFile(it) }
        assumeTrue(archive != null, "Pinned archive unavailable locally; no download or native-resource acceptance claimed")
        val digest = MessageDigest.getInstance("SHA-1")
        DigestInputStream(Files.newInputStream(archive!!), digest).use { it.transferTo(OutputStream.nullOutputStream()) }
        assertEquals(expectedSha1, digest.digest().joinToString("") { "%02x".format(it) }, "$filename archive bytes")
        return ZipFile(archive.toFile())
    }

    private fun read(zip: ZipFile, entry: String): JsonNode {
        val resource = zip.getEntry(entry)
        assertNotNull(resource, entry)
        return zip.getInputStream(resource).use { mapper.readTree(it) }
    }

    private fun extent(model: JsonNode, axis: Int): Double {
        val elements = model.path("elements").toList()
        assertTrue(elements.isNotEmpty(), "Expected a placed model with explicit elements")
        return elements.maxOf { maxOf(it.path("from")[axis].asDouble(), it.path("to")[axis].asDouble()) } -
            elements.minOf { minOf(it.path("from")[axis].asDouble(), it.path("to")[axis].asDouble()) }
    }

    private fun fullCube(element: JsonNode): Boolean = element.path("from").map { it.asDouble() } == listOf(0.0, 0.0, 0.0) &&
        element.path("to").map { it.asDouble() } == listOf(16.0, 16.0, 16.0)
}
