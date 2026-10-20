package com.bettercontent.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path
import java.util.jar.JarFile

/** Selected-candidate authored recipes vs actual full-pack RecipeManager exports.
 * This checks loaded serializers and exact effective output yields, not authored JSON alone. */
internal object PlantRuntimeCatalogue {
    private val mapper = jacksonObjectMapper()
    fun verify(provider: Path, dump: Path): Map<String, Any> {
        val mods = mapper.readTree(dump.resolve("mods.json").toFile()).path("mods")
        val registries = mapper.readTree(dump.resolve("registries.json").toFile())
        val items = registries.path("items").fieldNames().asSequence().toSet()
        val fluids = registries.path("fluids").fieldNames().asSequence().toSet()
        val recipes = mapper.readTree(dump.resolve("recipes.json").toFile()).path("recipes").associateBy { it.path("id").asText() }
        JarFile(provider.toFile()).use { jar ->
            val matrix = jar.getInputStream(requireNotNull(jar.getJarEntry("plant_drinks.json"))).use { mapper.readTree(it) }
            require(matrix.path("version").asInt() == 1 && matrix.path("serving_mb").asInt() == 250)
            val active = matrix.path("drinks").filter { row -> row.path("mods").all { mods.has(it.asText()) } }
            require(active.isNotEmpty())
            for (row in active) {
                require(row.path("item").asText() in items) { "active native plant item absent: $row" }
                require("better_drinking_water:${row.path("name").asText()}" in fluids) { "active native plant fluid absent: $row" }
            }
            val checked = mutableListOf<String>()
            for (entry in jar.entries().asSequence().filter { it.name.startsWith("data/better_drinking_water/recipes/plant/") && it.name.endsWith(".json") }) {
                val authored = jar.getInputStream(entry).use { mapper.readTree(it) }
                val enabled = authored.path("conditions").map { condition ->
                    require(condition.path("type").asText() == "forge:mod_loaded") { "unreviewed native recipe condition: $condition" }
                    mods.has(condition.path("modid").asText())
                }.all { it }
                if (!enabled) continue
                val id = "better_drinking_water:" + entry.name.removePrefix("data/better_drinking_water/recipes/").removeSuffix(".json")
                val native = requireNotNull(recipes[id]) { "effective native plant recipe missing: $id" }
                require(native.path("parsed").asBoolean() && native.path("type").asText() == authored.path("type").asText()) { "native plant serializer/type incomplete: $id: $native" }
                val outputs = when {
                    authored.has("results") -> authored.path("results").toList()
                    authored.has("result") -> listOf(authored.path("result"))
                    authored.has("output") -> listOf(authored.path("output"))
                    else -> error("unsupported authored native plant output schema: $id")
                }
                require(outputs.isNotEmpty() && outputs.all { it.has("item") || it.has("fluid") })
                for ((kind, field) in listOf("item" to "outputs", "fluid" to "fluids_out")) {
                    fun vector(rows: List<JsonNode>, authored: Boolean): Map<String, Long> {
                        val values = rows.map { row ->
                            require(row.path("chance").asDouble(1.0) == 1.0) { "unreviewed probabilistic native plant output: $id" }
                            (if (authored) row.path(kind).asText() else row.path("id").asText()) to
                                (if (authored) row.path("amount").asLong(row.path("count").asLong(1)) else row.path("count").asLong())
                        }
                        require(values.map { it.first }.distinct().size == values.size) { "duplicate native plant output requires explicit normalization: $id" }
                        return values.toMap()
                    }
                    val expectedRows = outputs.filter { it.has(kind) }
                    val nativeRows = native.path(field).toList()
                    require(vector(expectedRows, true) == vector(nativeRows, false) && expectedRows.size == nativeRows.size) { "effective native plant output yield changed: $id/$kind: $native" }
                }
                if (id.endsWith("_fill") || id.endsWith("_pour")) {
                    require(native.path("fluids_in").size() == 1 && native.path("fluids_in")[0].path("count").asLong() == 250L) { "native plant serving input is not250mB: $id" }
                }
                if (id.endsWith("_empty")) require(native.path("fluids_out").size() == 1 && native.path("fluids_out")[0].path("count").asLong() == 250L)
                checked += id
            }
            for (row in active) for (suffix in listOf("fill", "empty", "pour"))
                require("better_drinking_water:plant/${row.path("name").asText()}_$suffix" in checked) { "active native serving exchange absent: $row/$suffix" }
            return mapOf("active_profiles" to active.size, "native_recipe_ids" to checked.sorted(),
                "provider_sha256" to Hashes.sha256(provider), "origin" to "selected-candidate resources vs native full-pack registry/RecipeManager snapshot",
                "limits" to "exact loaded IDs/types/output yields and250mB exchanges; not a machine-time/throughput or every automatic drink behavioral claim")
        }
    }
}
