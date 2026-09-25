package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class PneumaticcraftCapabilityMatrixTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val matrix = Files.readString(root.resolve("docs/pneumaticcraft_capability_acquisition.md"))
    private val progression = Files.readString(
        root.resolve("kubejs/server_scripts/progression/81_pneumaticcraft_progression.js"),
    )

    @Test
    fun `inventory covers every required capability and identifies unresolved acquisition edges`() {
        listOf(
            "Basic compressed air",
            "Pressure chamber",
            "Thermal and fluid processing",
            "Plastic and heat chemistry",
            "PCB manufacture",
            "Assembly system and programs",
            "Sensors and logistics",
            "Drones and armor",
            "Advanced upgrades",
            "Exceptional services",
        ).forEach { capability -> assertTrue(matrix.contains("| $capability |"), capability) }

        listOf(
            "This is a source inventory; it does not prove that recipes load",
            "assembly_program_drill_laser",
            "pneumaticcraft:programmer",
            "pneumaticcraft:programmable_controller",
            "pneumaticcraft:amadron_tablet",
            "known direct Amadron Tablet entry",
            "TECH-03 or TECH-07",
        ).forEach { evidence -> assertTrue(matrix.contains(evidence), evidence) }

        assertTrue(progression.contains("global.bcPncrPressure"))
        assertTrue(progression.contains("pneumaticcraft:amadron_tablet"))
    }
}
