package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

@Tag("fast")
class FoodDryingContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
    private val heatSync = root.parent.resolve("mod_source/better-industrial-heat")

    @Test
    fun `rack recipes tag and annotation describe the same dried foods`() {
        val output = BoundedCommand.capture(root, listOf(
            "node", root.resolve("src/test/resources/food-drying-contract.cjs").toString(),
            root.resolve("kubejs/server_scripts/progression/82_food_drying.js").toString(),
        ), Duration.ofSeconds(15))
        val emitted = jacksonObjectMapper().readTree(output)
        val recipes = emitted.associate {
            it.path("id").asText().removePrefix("kubejs:food/drying_rack/") to
                (it.path("ingredients")[0].path("item").asText() to it.path("output").path("item").asText())
        }
        assertEquals(emitted.size(), recipes.size, "every executed recipe must have a unique ID")
        assertEquals(
            mapOf(
                "beef" to ("minecraft:beef" to "better_industrial_heat:dried_beef"),
                "porkchop" to ("minecraft:porkchop" to "better_industrial_heat:dried_porkchop"),
                "chicken" to ("minecraft:chicken" to "better_industrial_heat:dried_chicken"),
                "mutton" to ("minecraft:mutton" to "better_industrial_heat:dried_mutton"),
                "rabbit" to ("minecraft:rabbit" to "better_industrial_heat:dried_rabbit"),
                "cod" to ("minecraft:cod" to "better_industrial_heat:dried_cod"),
                "salmon" to ("minecraft:salmon" to "better_industrial_heat:dried_salmon"),
            ),
            recipes,
            "only the authored unspoiled, uncooked food catalogue is eligible for this drying route",
        )

        val mapper = jacksonObjectMapper()
        val tag = mapper.readTree(
            heatSync.resolve("src/main/resources/data/better_industrial_heat/tags/items/dried_foods.json").toFile(),
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
        assertTrue(driedAnnotation.path("lines").isArray && !driedAnnotation.path("lines").isEmpty,
            "drying must retain player-facing guidance; wording is not an executable preservation oracle")

        val thermalSource = Files.readString(
            heatSync.resolve("src/main/kotlin/com/bettercontent/betterindustrialheat/food/FoodThermalService.kt"),
        )
        assertTrue(thermalSource.contains("stack.`is`(HeatSyncThermalTags.DRIED_FOODS) -> Profile(\"dried\", 1.0, true, meat)"))
        assertTrue(thermalSource.contains("storage == Storage.COLD || profile.preserved -> 0.1"))
    }
}
