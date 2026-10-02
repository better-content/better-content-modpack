package com.bettercontent.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class GunLodFallbackContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val mapper = JsonMapper.builder()
        .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .build()

    @Test
    fun `missing optional LODs fall back to the unchanged supported display assets`() {
        val expected = mapOf(
            "applied_armorer" to setOf(
                "moritz_gernade_gl3", "moritz_mg_emg_prototype",
                "niklas_pistol_semi_pride", "special_melee_task_manager",
            ),
            "create_armorer" to setOf(
                "cannon_40mm_salamander", "gl_revolver_devastator", "pistol_auto_stress",
                "rifle_assult_crane", "shotgun_db_stone", "sniper_semi_clockwork",
                "sniper_semi_m1", "special_melee_wrench",
            ),
            "immersive_armorer" to setOf("pistol_9mm", "short_smg"),
        )
        val baselineRoot = Path.of(requireNotNull(javaClass.getResource("/gun-lod-baselines")).toURI())
        val actualIds = mutableMapOf<String, MutableSet<String>>()

        for ((namespace, ids) in expected) {
            for (id in ids) {
                val baselineFile = baselineRoot.resolve("$namespace/${id}_display.json")
                val overrideFile = root.resolve("kubejs/assets/$namespace/display/guns/${id}_display.json")
                assertTrue(Files.isRegularFile(baselineFile), "missing audited ZIP baseline for $namespace:$id")
                assertTrue(Files.isRegularFile(overrideFile), "missing LOD fallback override for $namespace:$id")

                val original = mapper.readTree(baselineFile.toFile())
                val override = mapper.readTree(overrideFile.toFile())
                assertNotNull(original.get("lod"), "$namespace:$id baseline must document the absent LOD")
                assertTrue(override.get("lod") == null, "$namespace:$id must stop referencing its absent LOD")
                assertNotNull(override.get("model"), "$namespace:$id must retain the supported normal model")
                assertNotNull(override.get("texture"), "$namespace:$id must retain the supported normal texture")

                val expectedFallback = original.deepCopy<JsonNode>() as ObjectNode
                expectedFallback.remove("lod")
                assertEquals(expectedFallback, override, "$namespace:$id override may remove only the missing LOD stanza")
                actualIds.computeIfAbsent(namespace) { mutableSetOf() }.add(id)
            }
        }

        assertEquals(expected, actualIds.mapValues { it.value.toSet() })
    }
}
