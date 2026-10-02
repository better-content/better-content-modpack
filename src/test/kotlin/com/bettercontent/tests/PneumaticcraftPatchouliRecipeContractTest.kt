package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

@Tag("fast")
class PneumaticcraftPatchouliRecipeContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val mapper = jacksonObjectMapper()

    @Test
    fun `pressure chamber recipes use PneumaticCraft recipe pages and real item icons`() {
        val book = root.resolve("kubejs/assets/pneumaticcraft/patchouli_books/book/en_us/entries")
        val expected = setOf(
            "kubejs:tech/pneumatic/assembly_controller",
            "kubejs:tech/pneumatic/drone",
            "kubejs:tech/pneumatic/inventory_upgrade",
            "kubejs:tech/pneumatic/coordinate_tracker_upgrade",
            "kubejs:tech/pneumatic/advanced/entity_tracker_upgrade",
            "kubejs:tech/pneumatic/advanced/range_upgrade",
            "kubejs:tech/pneumatic/advanced/magnet_upgrade",
            "kubejs:tech/pneumatic/advanced/charging_upgrade",
            "kubejs:tech/pneumatic/advanced/jet_boots_upgrade_3",
            "kubejs:tech/pneumatic/advanced/minigun_upgrade",
        )
        val found = mutableSetOf<String>()
        java.nio.file.Files.walk(book).use { files ->
            files.filter { it.fileName.toString().endsWith(".json") }.forEach { path ->
                val pages = mapper.readTree(path.toFile()).path("pages")
                pages.forEach { page ->
                    val recipe = page.path("recipe").asText("")
                    if (recipe in expected) {
                        assertEquals(
                            "pneumaticcraft:pressure_chamber",
                            page.path("type").asText(),
                            "$recipe in $path",
                        )
                        found += recipe
                    }
                }
            }
        }
        assertEquals(expected, found)

        val compressor = mapper.readTree(book.resolve("compressors/air_compressor.json").toFile())
        assertEquals("pneumaticcraft:air_compressor", compressor.path("icon").asText())

        val acquisition = java.nio.file.Files.readString(
            root.resolve("kubejs/server_scripts/progression/82_pneumaticcraft_acquisition.js"),
        )
        assertEquals(1, Regex("type: 'pneumaticcraft:amadron'").findAll(acquisition).count())
        assertEquals(1, Regex("id: 'pneumaticcraft:amadron/pcb_blueprint'").findAll(acquisition).count())
        assertEquals(1, Regex("amount: 4, id: 'pneumaticcraft:pressure_tube'").findAll(acquisition).count())
    }
}
