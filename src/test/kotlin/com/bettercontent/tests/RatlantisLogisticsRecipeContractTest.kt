package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class RatlantisLogisticsRecipeContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val script = Files.readString(root.resolve(
        "kubejs/server_scripts/progression/20_ratlantis_logistics.js"))

    @Test
    fun `pretty pipes has visible three tier Ratlantis gates`() {
        assertTrue(script.contains("event.shaped('8x prettypipes:pipe', ['RGR', 'ILI', 'RGR']"))
        assertTrue(script.contains("L: 'ratlantis_logistics:courier_lattice'"))
        assertTrue(script.contains("event.shaped('prettypipes:blank_module', ['QMQ', 'SPS', 'QRQ']"))
        assertTrue(script.contains("M: 'ratlantis_logistics:oratchalcum_mechanism'"))

        val expected = listOf(
            "high_crafting_module", "high_extraction_module", "high_filter_module",
            "high_high_priority_module", "high_low_priority_module", "high_retrieval_module",
            "high_speed_module",
        )
        expected.forEach { module -> assertTrue(script.contains("$module: {"), module) }
        assertEquals(7, Regex("C: 'ratlantis_logistics:arcane_logistics_core'").findAll(script).count())
        assertTrue(script.contains("event.remove({ id: 'prettypipes:module_clearing' })"))
    }

    @Test
    fun `rapid hopper is a visible Ratlantis throughput upgrade`() {
        assertTrue(script.contains("event.remove({ id: 'littlelogistics:rapid_hopper' })"))
        assertTrue(script.contains(
            "event.shaped('littlelogistics:rapid_hopper', ['GHG', ' L ', ' R ']",
        ))
        assertTrue(script.contains("L: 'ratlantis_logistics:courier_lattice'"))
        assertTrue(script.contains("R: 'minecraft:redstone_block'"))
        assertTrue(script.contains("}).id('littlelogistics:rapid_hopper')"))
    }
}
