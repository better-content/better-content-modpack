package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/** Inert schema/oracle fault tests ONLY. These synthetic files never become native evidence. */
class PlantRuntimeCatalogueTest {
    private val mapper = jacksonObjectMapper()
    @Test fun selectedResourceRequiresExactNativeIdsTypesAndServingYields() {
        val base = Path.of("build/tmp").toAbsolutePath().also { Files.createDirectories(it) }
        val work = Files.createTempDirectory(base, "plant-catalogue-parser-") // retained under registered build outputs
        val provider = work.resolve("inert-unit-provider.jar")
        val item = "better_drinking_water:apple_juice"
        JarOutputStream(Files.newOutputStream(provider)).use { jar ->
            fun put(path: String, value: String) { jar.putNextEntry(JarEntry(path)); jar.write(value.toByteArray()); jar.closeEntry() }
            put("plant_drinks.json", """{"version":1,"serving_mb":250,"drinks":[{"name":"apple_juice","item":"$item","mods":[]}]}""")
            for (suffix in listOf("fill", "empty", "pour")) {
                val output = if (suffix == "empty") """{"fluid":"$item","amount":250}""" else """{"item":"$item"}"""
                put("data/better_drinking_water/recipes/plant/apple_juice_$suffix.json", """{"type":"unit:$suffix","results":[$output],"conditions":[]}""")
            }
        }
        mapper.writeValue(work.resolve("mods.json").toFile(), mapOf("mods" to emptyMap<String, Any>()))
        mapper.writeValue(work.resolve("registries.json").toFile(), mapOf("items" to mapOf(item to emptyMap<String, Any>()), "fluids" to mapOf(item to emptyMap<String, Any>())))
        fun recipes(amount: Int = 250, omit: Boolean = false, partial: Boolean = false, itemCount: Int = 1, type: String = "unit:fill"): List<Map<String, Any>> =
            listOf("fill", "empty", "pour").filterNot { omit && it == "pour" }.map { suffix ->
                mapOf<String, Any>("id" to "better_drinking_water:plant/apple_juice_$suffix", "type" to if (suffix == "fill") type else "unit:$suffix",
                    "parsed" to !partial, "outputs" to if (suffix == "empty") emptyList() else listOf(mapOf("id" to item, "count" to itemCount)),
                    "fluids_out" to if (suffix == "empty") listOf(mapOf("id" to item, "count" to amount)) else emptyList(),
                    "fluids_in" to if (suffix != "empty") listOf(mapOf("id" to item, "count" to amount)) else emptyList())
            }
        fun save(rows: List<Map<String, Any>>) = mapper.writeValue(work.resolve("recipes.json").toFile(), mapOf("recipes" to rows))
        save(recipes())
        assertEquals(3, (PlantRuntimeCatalogue.verify(provider, work)["native_recipe_ids"] as List<*>).size)
        for (bad in listOf(recipes(amount = 1000), recipes(omit = true), recipes(partial = true), recipes(itemCount = 2), recipes(type = "unit:wrong"))) {
            save(bad)
            assertThrows(IllegalArgumentException::class.java) { PlantRuntimeCatalogue.verify(provider, work) }
        }
    }
}
