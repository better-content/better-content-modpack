package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class WorldCondenserMeteorGateContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val progression = Files.readString(
        root.resolve("kubejs/server_scripts/progression/80_tech_palette.js"),
    )
    private val wlmRecipe = Files.readString(
        root.parent.resolve(
            "mod_source/better-world-management/src/main/resources/data/" +
                "better_world_management/recipes/world_condenser_interface.json",
        ),
    )

    @Test
    fun `pack removes the ungated WLM recipe and requires meteor material`() {
        assertTrue(wlmRecipe.contains("\"type\": \"minecraft:crafting_shaped\""))
        assertTrue(wlmRecipe.contains("\"item\": \"bloodmagic:reinforcedslate\""))
        assertTrue(wlmRecipe.contains("\"item\": \"create:brass_sheet\""))
        assertTrue(wlmRecipe.contains("\"item\": \"minecraft:obsidian\""))

        assertTrue(
            progression.contains(
                "event.remove({ output: 'better_world_management:world_condenser_interface' })",
            ),
            "the original mod recipe and any other output recipe must be removed",
        )
        assertTrue(
            progression.contains(
                "event.shaped('better_world_management:world_condenser_interface', " +
                    "['SBS', 'BOB', 'SBS'], {",
            ),
        )
        assertTrue(progression.contains("S: 'ae2:sky_stone_block'"))
        assertTrue(progression.contains("B: 'create:brass_sheet', O: 'minecraft:obsidian'"))
        assertTrue(
            progression.contains(
                ".id('kubejs:tech/world_condenser_interface_meteor_gate')",
            ),
        )
    }
}
