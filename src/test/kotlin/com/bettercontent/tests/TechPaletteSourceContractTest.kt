package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class TechPaletteSourceContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))

    @Test
    fun `energy acceptor has one route and it preserves its conjunctive root gate`() {
        val progression = root.resolve("kubejs/server_scripts/progression")
        val producer = Regex("event\\.(?:shaped|shapeless)\\(\\s*['\"]ae2:energy_acceptor['\"]")
        val producers = Files.walk(progression).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".js") }
                .flatMap { path ->
                    val matches = producer.findAll(Files.readString(path)).count()
                    java.util.stream.Stream.generate { path }.limit(matches.toLong())
                }
                .toList()
        }

        assertEquals(1, producers.size, "parallel Energy Acceptor recipes can bypass progression gates")
        assertEquals("20_ratlantis_logistics.js", producers.single().fileName.toString())

        val gatedRoute = Files.readString(producers.single())
        listOf(
            "powergrid:generator_housing",
            "ratlantis_logistics:arcane_logistics_core",
            "oc2r:computer",
            "ae2:sky_stone_block",
            "kubejs:impossible_support_matrix",
        ).forEach { ingredient ->
            assertTrue(gatedRoute.contains("'$ingredient'"), "missing Energy Acceptor gate: $ingredient")
        }
        assertTrue(gatedRoute.contains(".id('kubejs:ratlantis_logistics/root/ae2_powergrid_energy_acceptor')"))
    }

    @Test
    fun `occultism miner cannot bypass the Sky Stone meteor route`() {
        val removedItems = Files.readString(root.resolve("kubejs/server_scripts/policy/removed_items.js"))
        assertTrue(removedItems.contains("event.remove({ type: 'occultism:miner' })"))
        assertTrue(removedItems.contains("direct Sky Stone result that bypasses AE2's authored meteor route"))
    }

    @Test
    fun `Blood Magic cannot repeat AE2 Sky Stone meteor generation`() {
        val removedItems = Files.readString(root.resolve("kubejs/server_scripts/policy/removed_items.js"))
        val matrix = Files.readString(root.resolve("docs/tech01_component_acquisition.md"))

        assertTrue(removedItems.contains("event.remove({ id: 'bloodmagic:meteor/ae2' })"))
        assertFalse(removedItems.contains("event.remove({ type: 'bloodmagic:meteor' })"),
            "unrelated Blood Magic meteor recipes must remain available")
        assertTrue(matrix.contains("bloodmagic:meteor/ae2"))
        assertTrue(matrix.contains("Certus Quartz still has to come from finding an AE2 meteor"))
    }

    @Test
    fun `meteor component map distinguishes the early palette from the ME network gate`() {
        val palette = Files.readString(root.resolve("kubejs/server_scripts/progression/80_tech_palette.js"))
        val energyRoot = Files.readString(root.resolve("kubejs/server_scripts/progression/20_ratlantis_logistics.js"))
        val matrix = Files.readString(root.resolve("docs/tech01_component_acquisition.md"))

        assertTrue(palette.contains("type: 'pneumaticcraft:pressure_chamber', pressure: 4.0"))
        listOf(
            "ae2:sky_stone_block",
            "ae2:charged_certus_quartz_crystal",
            "ae2:engineering_processor",
            "powergrid:integrated_circuit",
            "create:electron_tube",
        ).forEach { ingredient ->
            assertTrue(palette.contains("'$ingredient'"), "controller palette lost $ingredient")
            assertTrue(matrix.contains(ingredient), "component matrix omits $ingredient")
        }
        assertTrue(matrix.contains("data/create/recipes/crafting/materials/electron_tube.json"))
        assertTrue(matrix.contains("data/powergrid/recipes/mechanical_crafting/integrated_circuit.json"))
        assertTrue(matrix.contains("Certus-only tubes were an audit proposal, not adopted policy"))
        assertTrue(energyRoot.contains("kubejs:impossible_support_matrix"))
        assertTrue(matrix.contains("Deliberately remains the later conjunctive, Ratlantis-rooted gate"))
    }

    @Test
    fun `meteor materials and gated palette outputs cannot enter from trades or loot`() {
        val tradePolicy = Files.readString(root.resolve("kubejs/server_scripts/policy/removed_items.js"))
        val lootPolicy = Files.readString(root.resolve("kubejs/server_scripts/policy/global_loot_progression.js"))
        val restricted = setOf(
            "ae2:sky_stone_block",
            "ae2:certus_quartz_crystal",
            "ae2:charged_certus_quartz_crystal",
            "ae2:quartz_glass",
            "ae2:logic_processor",
            "ae2:engineering_processor",
            "ae2:controller",
            "ae2:cell_workbench",
            "ae2:energy_acceptor",
        )

        fun configuredOutputs(source: String, declaration: String): Set<String> {
            val body = Regex("var $declaration = \\[([\\s\\S]*?)\\]")
                .find(source)?.groupValues?.get(1)
                ?: error("missing exact TECH-01 output list: $declaration")
            return Regex("'([^']+)'").findAll(body).map { it.groupValues[1] }.toSet()
        }

        assertEquals(restricted, configuredOutputs(tradePolicy, "BC_TECH01_RESTRICTED_TRADE_OUTPUTS"))
        assertEquals(restricted, configuredOutputs(lootPolicy, "BC_TECH01_LOOT_REMOVE_ITEMS"))
        assertTrue(tradePolicy.contains("event.removeTrades(MoreJS.ofTradeFilter({ firstItem: '*', secondItem: '*', outputItem: output }))"))
        assertTrue(tradePolicy.contains("bcRemoveTech01Trades(event)"), "both merchant registries must apply the restriction")
        assertTrue(tradePolicy.contains("MoreJSEvents.wandererTrades(function (event)"))
        assertTrue(tradePolicy.contains("MoreJSEvents.villagerTrades(function (event)"))
        assertTrue(lootPolicy.contains("BC_LOOT_REMOVE_ITEMS = BC_LOOT_REMOVE_ITEMS.concat(BC_TECH01_LOOT_REMOVE_ITEMS)"))
        assertTrue(lootPolicy.contains("allLoot.removeLoot(BC_LOOT_REMOVE_ITEMS[i])"))
        assertFalse(tradePolicy.contains("ae2:*"), "do not block unrelated AE2 outputs")
        assertFalse(lootPolicy.contains("ae2:*"), "do not block unrelated AE2 loot")
        assertFalse(restricted.contains("create:electron_tube"), "native Create component acquisition remains available")
        assertFalse(restricted.contains("powergrid:integrated_circuit"), "parallel PowerGrid component acquisition remains available")
    }
}
