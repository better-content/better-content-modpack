package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

@Tag("fast")
class TconWoodMaterialContractTest {
    @Test
    fun `wood explicitly removes head stats while retaining other part types`() {
        val root = Path.of(System.getProperty("bc.repo.root"))
        val stats = jacksonObjectMapper().readTree(root.resolve(
            "kubejs/data/tconstruct/tinkering/materials/stats/wood.json",
        ).toFile()).path("stats")

        // Native MaterialStatsManager merges entries; absence does not delete upstream stats.
        assertTrue(stats.has("tconstruct:head"), "Head removal must be explicit, not omitted")
        assertTrue(stats.path("tconstruct:head").isNull, "JSON null removes the upstream head stat")
        val retained = setOf(
            "tconstruct:arrow_shaft", "tconstruct:binding", "tconstruct:grip",
            "tconstruct:handle", "tconstruct:limb", "tconstruct:shield_core",
        )
        assertEquals(retained + "tconstruct:head", stats.fieldNames().asSequence().toSet())
        retained.forEach { id -> assertTrue(stats.path(id).isObject, "$id remains available") }
    }
}
