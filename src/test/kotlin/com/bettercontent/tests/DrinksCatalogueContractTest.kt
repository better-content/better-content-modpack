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
        assertTrue(catalogue.path("drinks").size() > 0)
        catalogue.path("drinks").forEach { profile ->
            val id = profile.path("id").asText()
            assertEquals(profile.path("hydration").asInt() to profile.path("quenching").asInt(), configured[id],
                "Manual and fractional consumption must agree for $id")
        }
        assertFalse(configured.containsKey("farmersrespite:stong_coffee"))
        assertFalse(configured.containsKey("farmersrespite:strong_dandelion_tea"))
        assertFalse(thirst.substringAfter("itemsBlacklist =").contains("brewinandchewin:"),
            "Verified fermented drinks hydrate while retaining their separate impairment consequences")
    }
}
