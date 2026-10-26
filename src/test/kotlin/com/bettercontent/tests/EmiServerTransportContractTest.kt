package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/** Fast packaging/pin guard only: never launches Forge or claims a successful handshake/fill. */
@Tag("fast")
class EmiServerTransportContractTest {
    private val metadata = Path.of("mods/emi.pw.toml")

    private fun scalar(section: String, key: String): String {
        var active = ""
        val values = mutableListOf<String>()
        for (line in Files.readAllLines(metadata)) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[")) {
                active = trimmed.removePrefix("[").removeSuffix("]")
            } else if (active == section) {
                val match = Regex("^${Regex.escape(key)}\\s*=\\s*(.*?)\\s*$").matchEntire(trimmed)
                if (match != null) values.add(match.groupValues[1])
            }
        }
        assertEquals(1, values.size, "Expected exactly one [$section] $key in $metadata")
        return values.single()
    }

    @Test fun samePinnedEmiJarIsInstalledOnBothSidesWithoutUpgradeOrReplacement() {
        assertEquals("\"EMI\"", scalar("", "name"))
        assertEquals("\"both\"", scalar("", "side"))
        assertEquals("\"emi-1.1.24+1.20.1+forge.jar\"", scalar("", "filename"))
        assertEquals("\"sha1\"", scalar("download", "hash-format"))
        assertEquals("\"ea320200878e4a49196760234a22da763671520a\"", scalar("download", "hash"))
        assertEquals("\"metadata:curseforge\"", scalar("download", "mode"))
        assertEquals("8081375", scalar("update.curseforge", "file-id"))
        assertEquals("580555", scalar("update.curseforge", "project-id"))
    }

    private fun pinnedModJar(filename: String): Path =
        Path.of(System.getProperty("user.home"), ".cache/bc/packwiz-downloads/mods", filename)

    @Test fun pinnedNativeTransportRetainsLoginPingAndClientFill() {
        // Native archive inspection is not proof of handshake, menu fill or persistence at runtime.
        ZipFile(pinnedModJar("emi-1.1.24+1.20.1+forge.jar").toFile()).use { jar ->
            mapOf(
                "platform/forge/EmiForge" to listOf("PlayerLoggedInEvent", "PingS2CPacket"),
                "platform/EmiClient" to listOf("onServer", "FillRecipeC2SPacket"),
                "platform/forge/EmiPacketHandler" to listOf("FillRecipeC2SPacket", "PingS2CPacket"),
                "registry/EmiRecipeFiller" to listOf("clientFill"),
            ).forEach { (owner, symbols) ->
                val entry = checkNotNull(jar.getEntry("dev/emi/emi/$owner.class"))
                val constants = jar.getInputStream(entry).use { it.readBytes().toString(Charsets.ISO_8859_1) }
                symbols.forEach { assertTrue(constants.contains(it), "Pinned EMI lost $owner#$it") }
            }
            assertTrue(jar.getEntry("dev/emi/emi/network/FillRecipeC2SPacket.class") != null)
        }
    }
}
