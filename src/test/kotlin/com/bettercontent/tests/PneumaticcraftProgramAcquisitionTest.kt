package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class PneumaticcraftProgramAcquisitionTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val acquisition = Files.readString(
        root.resolve("kubejs/server_scripts/progression/82_pneumaticcraft_acquisition.js"),
    )
    private val progression = Files.readString(
        root.resolve("kubejs/server_scripts/progression/81_pneumaticcraft_progression.js"),
    )

    @Test
    fun `assembly programs and blueprint use explicit pressure routes without first board cycle`() {
        val routes = listOf(
            Route("assembly_program_laser", 1.5, listOf(
                "create:precision_mechanism", "create:electron_tube", "pneumaticcraft:pressure_tube", "create:brass_sheet",
            )),
            Route("assembly_program_drill", 2.5, listOf(
                "minecraft:diamond", "pneumaticcraft:pneumatic_cylinder", "pneumaticcraft:compressed_iron_gear", "create:precision_mechanism",
            )),
            Route("pcb_blueprint", 1.5, listOf(
                "pneumaticcraft:pressure_tube", "create:electron_tube", "create:brass_sheet", "minecraft:paper",
            )),
        )
        routes.forEach { route ->
            val start = acquisition.indexOf("'kubejs:tech/pneumatic/${route.output}'")
            assertTrue(start >= 0, "missing recipe id for ${route.output}")
            val end = acquisition.indexOf("])\n", start)
            assertTrue(end > start, "unterminated route for ${route.output}")
            val block = acquisition.substring(start, end)
            assertTrue(block.contains("'pneumaticcraft:${route.output}'"), "wrong output for ${route.output}")
            assertTrue(block.contains(", ${route.pressure}, ["), "wrong pressure tier for ${route.output}")
            route.inputs.forEach { input -> assertTrue(block.contains("'$input'"), "missing $input for ${route.output}") }
            assertFalse(block.contains("pneumaticcraft:printed_circuit_board"), "${route.output} must bootstrap before first PCB")
            assertFalse(block.contains("kubejs:electrical_instrumentation_module"), "${route.output} must not require instrumentation bootstrap")
        }
    }

    @Test
    fun `service offer bypasses are removed while native combination recipe stays`() {
        listOf(
            "assembly_program_drill",
            "assembly_program_laser",
            "assembly_program_drill_laser",
            "pcb_blueprint",
        ).forEach { id ->
            assertTrue(acquisition.contains("pneumaticcraft:amadron/$id"), "missing Amadron removal for $id")
            assertTrue(acquisition.contains("'pneumaticcraft:$id'"), "missing mechanic filter for $id")
        }
        assertTrue(acquisition.contains("'pneumaticcraft:drone'"), "mechanic drone sale bypass remains")
        listOf(
            "assembly_program_drill", "assembly_program_laser", "pcb_blueprint",
        ).forEach { id ->
            assertTrue(acquisition.contains("'kubejs:tech/pneumatic/$id'"), "missing authored route for $id")
        }
        assertTrue(acquisition.contains("'pneumaticcraft:printed_circuit_board'"), "mechanic board sale bypass remains")
        assertTrue(acquisition.contains("event.removeTrades(MoreJS.ofTradeFilter"))
        assertTrue(acquisition.contains("outputItem: item"))
        assertTrue(acquisition.contains("event.addLootTableModifier('pneumaticcraft:chests/mechanic_house')"))
        assertTrue(acquisition.contains(".removeLoot('pneumaticcraft:amadron_tablet')"), "native mechanic-house tablet bypass remains")
        assertTrue(acquisition.contains(".removeLoot('pneumaticcraft:advanced_pressure_tube')"), "mechanic-house advanced tube bypasses the authored thermopneumatic plant gate")
        assertFalse(acquisition.contains("'kubejs:tech/pneumatic/assembly_program_drill_laser'"), "combined program should retain native combination crafting")
        assertTrue(acquisition.contains("The combined Drill+Laser program keeps its native combination recipe"))
    }

    @Test
    fun `mechanic drone sale is filtered in favor of the five bar authored drone route`() {
        assertTrue(acquisition.contains("'pneumaticcraft:drone'"), "mechanic base-Drone sale bypass remains")
        val start = progression.indexOf("global.bcPncrPressure(event, 'kubejs:tech/pneumatic/drone'")
        assertTrue(start >= 0, "authored base-Drone pressure recipe is missing")
        val end = progression.indexOf("])\n", start)
        assertTrue(end > start, "base-Drone pressure recipe is unterminated")
        val route = progression.substring(start, end)
        assertTrue(route.contains("'pneumaticcraft:drone'"))
        assertTrue(route.contains(", 5.0, ["), "base drone should retain its 5 bar progression gate")
        listOf(
            "pneumaticcraft:printed_circuit_board", "pneumaticcraft:plastic",
            "powergrid:electric_motor", "create:precision_mechanism",
        ).forEach { input -> assertTrue(route.contains("'$input'"), "missing $input from base-Drone route") }
    }

    @Test
    fun `exceptional service acquisition recipes retain their authored electronic and pressure inputs`() {
        val routes = listOf(
            Route("programmer", null, listOf(
                "pneumaticcraft:turbine_rotor", "pneumaticcraft:printed_circuit_board", "kubejs:electrical_instrumentation_module",
            )),
            Route("programmable_controller", null, listOf(
                "pneumaticcraft:remote", "pneumaticcraft:printed_circuit_board", "pneumaticcraft:drone",
                "pneumaticcraft:advanced_pressure_tube", "kubejs:ae_logic_package",
            )),
            Route("amadron_tablet", null, listOf(
                "pneumaticcraft:plastic", "pneumaticcraft:gps_tool", "pneumaticcraft:air_canister",
            )),
        )
        routes.forEach { route ->
            val recipeStart = progression.indexOf("event.shaped('pneumaticcraft:${route.output}'")
            assertTrue(recipeStart >= 0, "authored recipe missing for ${route.output}")
            val recipeEnd = progression.indexOf(".id('kubejs:tech/pneumatic/", recipeStart)
            assertTrue(recipeEnd > recipeStart, "recipe id missing for ${route.output}")
            val recipe = progression.substring(recipeStart, recipeEnd)
            assertTrue(
                progression.substring(0, recipeStart).takeLast(100).contains("event.remove({ output: 'pneumaticcraft:${route.output}' })"),
                "native recipe bypass remains for ${route.output}",
            )
            route.inputs.forEach { input -> assertTrue(recipe.contains("'$input'"), "missing $input in ${route.output} recipe") }
        }
        assertTrue(progression.contains("native programmable behavior"))
        assertTrue(progression.contains("unrelated Amadron trade data remain untouched"))
    }

    @Test
    fun `guide recipe links follow the authored recipes that replaced native PneumaticCraft IDs`() {
        val entries = root.resolve("kubejs/assets/pneumaticcraft/patchouli_books/book/en_us/entries")
        val text = Files.walk(entries).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".json") }
                .map { Files.readString(it) }
                .toList()
                .joinToString("\n")
        }
        val replacements = listOf(
            "air_compressor_pressure_root", "thermopneumatic_processing", "assembly_controller", "drone",
            "armor_upgrade_sensors", "assembly_drill_instrumented", "universal_sensor_instrumentation",
            "inventory_upgrade", "coordinate_tracker_upgrade", "advanced/entity_tracker_upgrade",
            "advanced/range_upgrade", "advanced/magnet_upgrade", "advanced/charging_upgrade",
            "advanced/jet_boots_upgrade_3", "advanced/minigun_upgrade", "programmer_electrical_control",
            "programmable_controller_ae_logic", "amadron_tablet_pressure_service",
        )
        replacements.forEach { recipe ->
            assertTrue(text.contains("kubejs:tech/pneumatic/$recipe"), "Patchouli guide omits replacement recipe $recipe")
        }
        listOf(
            "air_compressor", "thermopneumatic_processing_plant", "assembly_controller", "drone",
            "armor_upgrade", "assembly_drill", "universal_sensor", "inventory_upgrade",
            "coordinate_tracker_upgrade", "entity_tracker_upgrade", "range_upgrade", "magnet_upgrade",
            "charging_upgrade", "jet_boots_upgrade_3", "minigun_upgrade", "programmer",
            "programmable_controller", "amadron_tablet",
        ).forEach { output ->
            assertFalse(text.contains("\"recipe\": \"pneumaticcraft:$output\""), "stale guide recipe link remains for $output")
            assertFalse(text.contains("\"recipe2\": \"pneumaticcraft:$output\""), "stale secondary guide recipe link remains for $output")
        }
    }

    private data class Route(val output: String, val pressure: Double?, val inputs: List<String>)
}
