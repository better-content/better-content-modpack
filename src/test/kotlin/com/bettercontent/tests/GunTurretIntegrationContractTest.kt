package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class GunTurretIntegrationContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val mapper = jacksonObjectMapper()

    @Test
    fun `TaCZ Turrets pin selects the verified Forge 1_20_1 release`() {
        val pin = Files.readString(root.resolve("mods/tacz-turrets.pw.toml"))
        assertTrue(pin.contains("filename = \"tacz_turrets-2.0.0-all.jar\""))
        assertTrue(pin.contains("hash-format = \"sha1\""))
        assertTrue(pin.contains("hash = \"501ddfe9d7d52a55de608a889eb46a890a4504bb\""))
        assertTrue(pin.contains("file-id = 8834767"))
        assertTrue(pin.contains("project-id = 1376660"))
    }

    @Test
    fun `automated turret acquisition replaces the default iron recipe with electrical components`() {
        val gate = Files.readString(
            root.resolve("kubejs/server_scripts/compat/retained/refactor__balance__174_tacz_turrets_electrical_gate.js"),
        )
        assertTrue(gate.contains("Platform.isLoaded('tacz_turrets')"))
        assertTrue(gate.contains("event.remove({ id: 'tacz_turrets:turret' })"))
        assertTrue(gate.contains("'kubejs:electrical_instrumentation_module'"))
        assertTrue(gate.contains("'powergrid:conductive_casing'"))
        assertTrue(gate.contains("'powergrid:redstone_relay'"))
        assertTrue(gate.contains("'powergrid:electric_motor'"))
        assertTrue(gate.contains("'kubejs:ae_logic_package'"))

        val policy = mapper.readTree(root.resolve("kubejs/config/crafting_policy.json").toFile())
        assertEquals("content", policy.path("namespaces").path("tacz_turrets").path("primary_role").asText())
    }

    @Test
    fun `turret hover teaches placement and ammunition behavior`() {
        val annotations = mapper.readTree(root.resolve("kubejs/config/hover_annotations.json").toFile())
            .path("annotations")
        val turret = annotations.first { it.path("selector").path("item").asText() == "tacz_turrets:turret" }
        val copy = turret.path("lines").first().asText()
        assertEquals("tacz_turrets", turret.path("required_mod").asText())
        assertTrue(turret.path("owner").asText().contains("tacz-turrets.pw.toml"))
        assertTrue(turret.path("owner").asText().contains("refactor__balance__174_tacz_turrets_electrical_gate.js"))
        assertTrue(copy.contains("place on a chest"))
        assertTrue(copy.contains("TaCZ gun"))
        assertTrue(copy.contains("ammo"))
        assertTrue(copy.split(Regex("\\s+")).size <= 24)
    }
}
