package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

@Tag("fast")
class GunManufacturingContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val mapper = jacksonObjectMapper()
    private fun pinnedGunPack(filename: String): Path =
        Path.of(System.getProperty("user.home"), ".cache/bc/packwiz-downloads/tacz", filename)
    private val gunPacks = mapOf(
        "applied_armorer" to pinnedGunPack("Applied Armorer-v1.1.4.1-for114+.zip"),
        "create_armorer" to pinnedGunPack("Create Armorer-v1.2.0.1-for117.zip"),
        "immersive_armorer" to pinnedGunPack("Immersive Armorer-v1.2.1-for117.zip"),
    )

    private fun assertNativeUncraftedItem(namespace: String, category: String, id: String) {
        ZipFile(gunPacks.getValue(namespace).toFile()).use { archive ->
            val entry = archive.getEntry("data/$namespace/index/$category/$id.json")
            assertNotNull(entry)
            val index = mapper.readTree(archive.getInputStream(entry))
            assertFalse(index.path("hidden").asBoolean(false), "$namespace:$id must be visible")
            val display = index.path("display").asText()
            assertTrue(display.isNotBlank(), "$namespace:$id must have a native display")
            assertNotNull(archive.getEntry("assets/${display.substringBefore(':')}/display/$category/${display.substringAfter(':')}.json"))
            if (category == "attachments") {
                val data = index.path("data").asText()
                assertNotNull(archive.getEntry("data/${data.substringBefore(':')}/data/$category/${data.substringAfter(':')}.json"))
            }
            assertNull(archive.getEntry("data/$namespace/recipes/$category/$id.json"), "$namespace:$id should lack a native recipe")
        }
    }

    @Test
    fun `gun component families have Create pressure and AE manufacturing paths`() {
        val components = Files.readString(root.resolve("kubejs/server_scripts/progression/70_transition_components.js"))
        val recipeHelper = Files.readString(root.resolve("kubejs/server_scripts/utility/10_recipe_surface_helpers.js"))

        assertTrue(components.contains("event.shaped('2x kubejs:brass_utility_assembly'"))
        assertTrue(components.contains("P: 'create:precision_mechanism'"))
        assertTrue(components.contains("'kubejs:electrical_instrumentation_module', 2, 3.5"))
        assertTrue(components.contains("'kubejs:ae_logic_package', 1, 4.0"))
        assertTrue(components.contains("'ae2:logic_processor'"))
        assertTrue(components.contains("'oc2r:circuit_board'"))
        assertTrue(recipeHelper.contains("type: 'pneumaticcraft:pressure_chamber'"))
    }

    @Test
    fun `TaCZ manufacturing turns staged components into guns ammunition and attachments`() {
        val script = Files.readString(
            root.resolve("kubejs/server_scripts/compat/retained/refactor__balance__171_tacz_manufacturing_gates.js"),
        )

        assertTrue(script.contains("type: 'tacz:gun_smith_table_crafting'"))
        assertTrue(script.contains("result: { type: 'gun', id: 'tacz:deagle' }"))
        assertTrue(script.contains("result: { type: 'ammo', group: 'lc_specialized', id: 'tacz:338', count: 18 }"))
        assertTrue(script.contains("result: { type: 'attachment', id: 'tacz:scope_vudu' }"))
        assertTrue(script.contains("item: 'kubejs:electrical_instrumentation_module'"))
        assertTrue(script.contains("item: 'kubejs:brass_utility_assembly'"))
        assertTrue(script.contains("item: 'kubejs:ae_logic_package'"))
    }

    @Test
    fun `visible functional Armorer muzzles have staged gunsmith acquisition`() {
        assertNativeUncraftedItem("applied_armorer", "attachments", "muzzle_commander")
        assertNativeUncraftedItem("create_armorer", "attachments", "muzzle_refit_ap_grenade")

        val script = Files.readString(
            root.resolve("kubejs/server_scripts/compat/retained/refactor__balance__171_tacz_manufacturing_gates.js"),
        )
        val appliedStart = script.indexOf("event.remove({ id: 'applied_armorer:attachments/muzzle_commander' })")
        val createStart = script.indexOf("event.remove({ id: 'create_armorer:attachments/muzzle_refit_ap_grenade' })")
        assertTrue(appliedStart >= 0)
        assertTrue(createStart > appliedStart)
        val applied = script.substring(appliedStart, createStart)
        val create = script.substring(createStart, script.indexOf("event.remove(", createStart + 1))

        assertTrue(applied.contains("type: 'tacz:gun_smith_table_crafting'"))
        assertTrue(applied.contains("item: 'kubejs:ae_logic_package'"))
        assertTrue(applied.contains("item: 'ae2:engineering_processor'"))
        assertTrue(applied.contains("result: { type: 'attachment', id: 'applied_armorer:muzzle_commander' }"))
        assertTrue(create.contains("type: 'tacz:gun_smith_table_crafting'"))
        assertTrue(create.contains("item: 'kubejs:electrical_instrumentation_module'"))
        assertTrue(create.contains("item: 'minecraft:netherite_scrap'"))
        assertTrue(create.contains("result: { type: 'attachment', id: 'create_armorer:muzzle_refit_ap_grenade' }"))
    }

    @Test
    fun `melee gun firing ammunition has authored workbench supply`() {
        assertNativeUncraftedItem("applied_armorer", "ammo", "melee")
        assertNativeUncraftedItem("create_armorer", "ammo", "melee_weapon")

        val script = Files.readString(
            root.resolve("kubejs/server_scripts/compat/retained/refactor__balance__171_tacz_manufacturing_gates.js"),
        )
        assertTrue(script.contains("event.remove({ id: 'applied_armorer:ammo/melee' })"))
        assertTrue(script.contains("result: { type: 'ammo', id: 'applied_armorer:melee', count: 1 }"))
        assertTrue(script.contains("item: 'kubejs:ae_logic_package'"))
        assertTrue(script.contains("event.remove({ id: 'create_armorer:ammo/melee_weapon' })"))
        assertTrue(script.contains("result: { type: 'ammo', id: 'create_armorer:melee_weapon', count: 1 }"))
        assertTrue(script.contains("item: 'kubejs:brass_utility_assembly'"))
    }

    @Test
    fun `all external gun recipes retain native results and require their pack component`() {
        val externalGuns = gunPacks.flatMap { (namespace, path) ->
            ZipFile(path.toFile()).use { archive ->
                val prefix = "data/$namespace/index/guns/"
                archive.entries().asSequence().map { it.name }
                    .filter { it.startsWith(prefix) && it.endsWith(".json") }
                    .map { "$namespace:gun/${it.removePrefix(prefix).removeSuffix(".json")}" }.toList()
            }
        }.toSet()
        assertEquals(39, externalGuns.size)

        val script = Files.readString(
            root.resolve("kubejs/server_scripts/compat/retained/refactor__balance__171_tacz_manufacturing_gates.js"),
        )
        val scriptGunResults = Regex("result:\\s*\\{\\s*type:\\s*'gun',\\s*id:\\s*'([^']+)'", RegexOption.DOT_MATCHES_ALL)
            .findAll(script)
            .map { it.groupValues[1] }
            .toSet()
        val componentByNamespace = mapOf(
            "applied_armorer" to "kubejs:ae_logic_package",
            "create_armorer" to "kubejs:brass_utility_assembly",
            "immersive_armorer" to "kubejs:electrical_instrumentation_module",
        )
        val covered = mutableSetOf<String>()
        var dataOverrides = 0

        for (id in externalGuns) {
            val namespace = id.substringBefore(':')
            val fileName = id.substringAfter(":gun/") + ".json"
            val override = root.resolve("kubejs/data/$namespace/recipes/gun/$fileName")
            if (Files.isRegularFile(override)) {
                val recipe = mapper.readTree(override.toFile())
                assertEquals("$namespace:${id.substringAfter(":gun/")}", recipe.path("result").path("id").asText())
                assertEquals("gun", recipe.path("result").path("type").asText())
                assertEquals("tacz:gun_smith_table_crafting", recipe.path("type").asText())
                val materials = recipe.path("materials").toList()
                assertTrue(materials.size >= 2, "$id must keep native materials alongside its manufactured part")
                assertEquals(
                    1,
                    materials.count { it.path("item").path("item").asText() == componentByNamespace[namespace] },
                    "$id must consume its pack's manufactured component exactly once",
                )
                if (id == "applied_armorer:gun/niklas_pistol_double_win_win") {
                    assertEquals(
                        2,
                        materials.single { it.path("item").path("item").asText() == "tacz:modern_kinetic_gun" }
                            .path("count").asInt(),
                        "the double-gun recipe must retain both native gun inputs",
                    )
                    val sourceGun = materials.single { it.path("item").path("item").asText() == "tacz:modern_kinetic_gun" }
                    assertEquals("forge:partial_nbt", sourceGun.path("item").path("type").asText())
                    assertEquals(
                        "applied_armorer:niklas_pistol_semi_union",
                        sourceGun.path("item").path("nbt").path("GunId").asText(),
                        "both native input guns must retain their authored gun identity",
                    )
                }
                dataOverrides++
                covered += id
            } else {
                assertTrue(scriptGunResults.contains("$namespace:${id.substringAfter(":gun/")}"), "$id needs an authored workbench override")
                covered += id
            }
        }

        assertEquals(externalGuns, covered)
        assertEquals(35, dataOverrides, "the remaining external gun recipes must have same-ID data overrides")
    }
}
