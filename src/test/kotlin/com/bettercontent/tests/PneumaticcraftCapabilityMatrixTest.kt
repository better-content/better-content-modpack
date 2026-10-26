package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/** Source and pinned recipe inventory only; recipe loading/acquisition require runtime acceptance. */
@Tag("fast")
class PneumaticcraftCapabilityMatrixTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private fun pinnedModJar(filename: String): Path =
        Path.of(System.getProperty("user.home"), ".cache/bc/packwiz-downloads/mods", filename)

    @Test
    fun `required capabilities retain native producers and explicit pack entry recipes`() {
        val progression = Files.readString(root.resolve("kubejs/server_scripts/progression/81_pneumaticcraft_progression.js"))
        assertTrue(progression.contains("global.bcPncrPressure"))
        // Cover the ten former prose-inventory capability families with actual native resources.
        ZipFile(pinnedModJar("pneumaticcraft-repressurized-6.0.22+mc1.20.1.jar").toFile()).use { jar ->
            listOf("air_compressor", "pressure_chamber_wall", "refinery", "heat_frame_cooling/plastic",
                "printed_circuit_board", "assembly_program_drill_laser", "universal_sensor", "drone",
                "range_upgrade", "amadron_tablet", "programmer", "programmable_controller").forEach { recipe ->
                val entry = checkNotNull(jar.getEntry("data/pneumaticcraft/recipes/$recipe.json")) {
                    "Missing native acquisition resource: $recipe"
                }
                val json = jar.getInputStream(entry).use { jacksonObjectMapper().readTree(it) }
                assertTrue(json.path("type").asText().isNotBlank(), recipe)
            }
        }
        listOf("air_compressor", "programmer", "programmable_controller", "amadron_tablet").forEach { item ->
            assertTrue(progression.contains("event.remove({ output: 'pneumaticcraft:$item' })"), item)
            assertTrue(progression.contains("event.shaped('pneumaticcraft:$item'"), item)
        }
    }
}
