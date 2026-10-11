package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Authored active/disabled content checks, not runtime acceptance. */
@Tag("fast")
class LostCitiesDisabledContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
    private val mapper = jacksonObjectMapper()
    private fun read(path: String) = Files.readString(root.resolve(path))
    private fun json(path: String) = mapper.readTree(root.resolve(path).toFile())

    @Test
    fun `Lost Cities is absent from active manifests configuration and rocket routes`() {
        listOf(
            "mods/the-lost-cities.pw.toml", "config/lostcities",
            "defaultconfigs/lostcities-server.toml",
            "kubejs/data/lostcities/creatingspace/rocket_accessible_dimension/lostcity.json",
        ).forEach { assertFalse(Files.exists(root.resolve(it)), it) }
        val orbit = json("kubejs/data/creatingspace/creatingspace/rocket_accessible_dimension/earth_orbit.json")
        assertFalse(orbit.path("adjacentDimensions").has("lostcities:lostcity"))
        assertTrue(orbit.path("adjacentDimensions").has("twilightforest:twilight_forest"))
        assertTrue(orbit.path("adjacentDimensions").has("fallout_wastelands_:wastelands"))
        assertFalse(read("kubejs/config/crafting_policy.json").contains("\"lostcities\""))
        assertFalse(json("config/incontrol/structure.json").any { it.path("dimension").asText() == "lostcities:lostcity" })
        assertTrue(read("config/better_compat_fixes-common.toml").contains("serializeDhC2meFeaturePlacement = false"))
    }

    @Test
    fun `customizations remain as authored inputs outside active packaging roots`() {
        val retained = "disabled/lost-cities/"
        listOf(
            "mods/the-lost-cities.pw.toml.disabled", "config/lostcities/common.toml",
            "config/lostcities/profiles/wasteland.json", "defaultconfigs/lostcities-server.toml",
            "kubejs/data/lostcities/creatingspace/rocket_accessible_dimension/lostcity.json",
        ).forEach { assertTrue(Files.isRegularFile(root.resolve(retained + it)), it) }
        assertEquals(8, json(retained + "config/incontrol/structure.json").size())
        val fragments = json(retained + "integration-fragments.json")
        assertEquals(1200, fragments.path("earthOrbitAdjacentDimensions").path("lostcities:lostcity").path("deltaV").asInt())
        assertTrue(fragments.path("craftingPolicyNamespaces").has("lostcities"))
        assertTrue(read(".packwizignore").lineSequence().any { it.trim() == "/disabled/" })
        assertFalse(read("package.sh").contains("cp -a \"\$ROOT/disabled"))
        // Preserve the native implementation and the optional terrain probe, not an active route.
        assertTrue(read("src/main/kotlin/com/bettercontent/tests/GeometrySmoke.kt").contains("\"lostcities:lostcity\""))
    }
}
