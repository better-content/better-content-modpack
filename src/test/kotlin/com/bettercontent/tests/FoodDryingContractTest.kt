package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class FoodDryingContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
    private val heatSync = root.parent.resolve("mod_source/heat-sync")

    @Test
    fun `rack recipes tag and annotation describe the same dried foods`() {
        val script = Files.readString(root.resolve("kubejs/server_scripts/progression/82_food_drying.js"))
        val catalogueBody = script.substringAfter("var catalogue = [", missingDelimiterValue = "")
            .substringBefore("\n    ]", missingDelimiterValue = "")
        assertTrue(catalogueBody.isNotEmpty(), "the authored rack catalogue must remain explicit")

        val rowPattern = Regex("(?m)^\\s*\\['([^']+)',\\s*'([^']+)',\\s*'([^']+)'\\],?\\s*$")
        val recipes = rowPattern.findAll(catalogueBody).associate {
            it.groupValues[3] to (it.groupValues[1] to it.groupValues[2])
        }
        val authoredRows = Regex("(?m)^\\s*\\[").findAll(catalogueBody).count()
        assertEquals(authoredRows, recipes.size, "every explicit catalogue row must parse uniquely")
        assertEquals(
            mapOf(
                "beef" to ("minecraft:beef" to "heat_sync:dried_beef"),
                "porkchop" to ("minecraft:porkchop" to "heat_sync:dried_porkchop"),
                "chicken" to ("minecraft:chicken" to "heat_sync:dried_chicken"),
                "mutton" to ("minecraft:mutton" to "heat_sync:dried_mutton"),
                "rabbit" to ("minecraft:rabbit" to "heat_sync:dried_rabbit"),
                "cod" to ("minecraft:cod" to "heat_sync:dried_cod"),
                "salmon" to ("minecraft:salmon" to "heat_sync:dried_salmon"),
            ),
            recipes,
            "only the authored unspoiled, uncooked food catalogue is eligible for this drying route",
        )

        val mapper = jacksonObjectMapper()
        val tag = mapper.readTree(
            heatSync.resolve("src/main/resources/data/heat_sync/tags/items/dried_foods.json").toFile(),
        )
        val taggedOutputs = tag.path("values").map { it.asText() }.toSet()
        val recipeOutputs = recipes.values.map { it.second }.toSet()
        assertEquals(recipeOutputs, taggedOutputs, "thermal preservation tag must match rack outputs")

        val annotations = mapper.readTree(root.resolve("kubejs/config/hover_annotations.json").toFile())
        val driedAnnotation = annotations.path("annotations").single {
            it.path("concept_id").asText() == "survival.food.drying_preservation"
        }
        assertEquals(
            recipeOutputs,
            driedAnnotation.path("selector").path("items").map { it.asText() }.toSet(),
            "the player-facing drying annotation must name every and only authored dried food",
        )
        val annotation = driedAnnotation.path("lines").map { it.asText() }.joinToString(" ")
        assertTrue(annotation.contains("Dry raw food on Hexerei's rack"))
        assertTrue(annotation.contains("spoils 90% slower"))
        assertTrue(annotation.contains("cannot be cooked"))

        val thermalSource = Files.readString(
            heatSync.resolve("src/main/kotlin/com/bettercontent/heatsync/food/FoodThermalService.kt"),
        )
        assertTrue(thermalSource.contains("stack.`is`(HeatSyncThermalTags.DRIED_FOODS) -> Profile(\"dried\", 1.0, null, meat)"))
        assertTrue(thermalSource.contains("profile.id == \"dried\" || profile.id == \"preserved\" -> 0.1"))
    }
}
