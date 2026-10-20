package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Cross-owner authored input contract; native consumption is verified in the owning mod gates. */
@Tag("fast")
class DrinksCatalogueContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))

    @Test
    fun `every fractional drink matches its manual thirst configuration without the old alcohol blacklist`() {
        val catalogue = jacksonObjectMapper().readTree(root.resolve(
            "../mod_source/better-drinking-water/src/main/resources/drinks.json").normalize().toFile())
        val thirst = Files.readString(root.resolve("config/thirst/item_settings.toml"))
        // Inspect the native numeric item rows in Drinks only, not Foods or illustrative comments.
        val rows = Regex("""\["([a-z0-9_.-]+:[a-z0-9_./-]+)",\s*(\d+),\s*(\d+)\s*]""")
            .findAll(thirst.substringBefore("[Foods]")).map {
                Triple(it.groupValues[1], it.groupValues[2].toInt(), it.groupValues[3].toInt())
            }.toList()
        assertEquals(rows.size, rows.map { it.first }.distinct().size, "Duplicate native drink configuration")
        val configured = rows.associate { it.first to (it.second to it.third) }
        val provider = root.resolve("../mod_source/better-drinking-water").normalize()
        val plantStock = jacksonObjectMapper().readTree(provider.resolve("src/main/resources/plant_drinks.json").toFile())
        val customItems = plantStock.path("drinks").map { it.path("item").asText() }
            .filter { it.startsWith("better_drinking_water:") }.toSet()
        val customProfiles = catalogue.path("drinks").map { it.path("id").asText() }
            .filter { it.startsWith("better_drinking_water:") }.toSet()
        assertTrue(customItems.size > 30, "The generated stock is not empty conditional metadata")
        assertEquals(customItems, customProfiles, "Every custom manual item has exactly its fractional catalogue profile")
        val manualSource = Files.readString(provider.resolve(
            "src/main/java/com/bettercontent/betterdrinkingwater/PlantDrinkItem.java"))
        assertTrue(manualSource.contains("var drink = DrinkCatalogue.find(stack);"))
        assertTrue(manualSource.contains("thirst.drink(player, drink.hydration(), drink.quenching());"))
        assertFalse(manualSource.contains("super.finishUsingItem("), "No second native food/drink completion path")
        val nativeTests = Files.readString(provider.resolve(
            "src/main/java/com/bettercontent/betterdrinkingwater/PlantDrinkGameTests.java"))
        assertTrue(nativeTests.contains("everyActivePlantManualFinishUsesProfileThirstExactlyOnce"))
        assertTrue(nativeTests.contains("LivingEntityUseItemEvent.Finish(player, original, 32, result)"),
            "The owning gate must bind explicit manual awards to the actual native Thirst Finish listener")
        assertTrue(catalogue.path("drinks").size() > 0)
        catalogue.path("drinks").forEach { profile ->
            val id = profile.path("id").asText()
            if (id in customItems) {
                assertFalse(configured.containsKey(id),
                    "PlantDrinkItem already awards catalogue hydration; a Thirst config row would double-award $id")
            } else {
                assertEquals(profile.path("hydration").asInt() to profile.path("quenching").asInt(), configured[id],
                    "Native-item manual and fractional consumption must agree for $id")
            }
        }
        assertFalse(configured.containsKey("farmersrespite:stong_coffee"))
        assertFalse(configured.containsKey("farmersrespite:strong_dandelion_tea"))
        assertFalse(thirst.substringAfter("itemsBlacklist =").contains("brewinandchewin:"),
            "Verified fermented drinks hydrate while retaining their separate impairment consequences")
    }
}
