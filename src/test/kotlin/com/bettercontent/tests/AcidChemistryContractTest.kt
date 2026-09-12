package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class AcidChemistryContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
    private val chemistry = read("kubejs/server_scripts/progression/45_acid_chemistry.js")

    @Test
    fun `active acids have complete canonical acquisition routes`() {
        listOf(
            "acetic_acid_oxidation" to "chemlib:acetic_acid_fluid",
            "sulfuric_acid_hydration" to "chemlib:sulfuric_acid_fluid",
            "hydrochloric_acid_mannheim" to "chemlib:hydrochloric_acid_fluid",
            "nitric_acid_saltpeter" to "chemlib:nitric_acid_fluid",
        ).forEach { (route, fluid) ->
            assertTrue(chemistry.contains(route), route)
            assertTrue(chemistry.contains(fluid), fluid)
        }
        assertTrue(chemistry.contains("chemlib:vanadium"))
        assertTrue(chemistry.contains("pneumaticcraft:pressure_chamber/etching_acid"))
        assertFalse(chemistry.contains("chemlib:phosphoric_acid_fluid"))

        val hiddenForms = read("kubejs/config/chemlib_form_policy.json")
        listOf("acetic", "hydrochloric", "nitric", "phosphoric", "sulfuric").forEach { acid ->
            assertTrue(hiddenForms.contains("chemlib:${acid}_acid\""), acid)
        }
        assertTrue(hiddenForms.contains("chemlib:phosphoric_acid_bucket"))
        assertTrue(
            read("kubejs/client_scripts/compat/retained/remove__40_hide_quarantined_systems.js")
                .contains("chemlib:phosphoric_acid_fluid"),
        )
    }

    @Test
    fun `renewable acetic acid has food filtration and containment uses`() {
        assertTrue(chemistry.contains("brewinandchewin:fermenting/pickled_pickles"))
        assertTrue(chemistry.contains("kubejs:chemistry/acids/acetate_membrane"))
        assertTrue(chemistry.contains("better_content_fixes:airtight_upgrade"))
        assertTrue(chemistry.contains("kubejs:pressure_seal"))

        val filtered = listOf(
            "acetylene", "ammonia", "ammonium", "butane", "carbon_dioxide",
            "carbon_monoxide", "chlorine", "ethane", "ethylene", "fluorine",
            "hydrogen_sulfide", "methane", "nitric_oxide", "nitrogen_dioxide",
            "propane", "radon", "sulfur_dioxide",
        )
        filtered.forEach { gas ->
            assertTrue(
                read("config/adpother/Pollutants/$gas.cfg")
                    .contains("kubejs:acetate_membrane, 8, minecraft:gray_dye"),
                gas,
            )
        }
    }

    @Test
    fun `pneumatic pcb surfaces use only ChemLib nitric acid`() {
        val etchingTag = read("kubejs/data/pneumaticcraft/tags/fluids/etching_acid.json")
        assertTrue(etchingTag.contains("\"replace\": true"))
        assertTrue(etchingTag.contains("chemlib:nitric_acid_fluid"))
        assertFalse(etchingTag.contains("pneumaticcraft:etching_acid\""))

        listOf(
            "kubejs/data/pneumaticcraft/advancements/etchacid_bucket.json",
            "kubejs/data/pneumaticcraft/advancements/recipes/misc/etching_tank.json",
            "kubejs/assets/pneumaticcraft/patchouli_books/book/en_us/entries/manufacturing/etching_acid.json",
        ).forEach { path -> assertTrue(read(path).contains("chemlib:nitric_acid"), path) }
    }

    @Test
    fun `retired acid implementation assets stay absent`() {
        listOf(
            "airtight_fluid_module",
            "airtight_service_module",
            "phosphoric_acid_fluid_bucket",
        ).forEach { item ->
            assertFalse(Files.exists(root.resolve("kubejs/assets/kubejs/models/item/$item.json")), item)
            assertFalse(Files.exists(root.resolve("kubejs/assets/kubejs/textures/item/$item.png")), item)
        }
    }

    private fun read(relative: String): String = Files.readString(root.resolve(relative))
}
