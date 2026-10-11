package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

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

    @Test fun supportedPackagingRequirementSeparatesNativeTransportFromBehavioralAcceptance() {
        val proof = Files.readString(Path.of("docs/native-emi-server-transport.md"))
            .replace(Regex("\\s+"), " ")
        for (required in listOf(
            "supported packaging requirement", "not fixture spoofing", "1.1.24+1.20.1+forge",
            "8081375", "ea320200878e4a49196760234a22da763671520a",
            "EmiForge", "PlayerLoggedInEvent", "PingS2CPacket", "EmiClient.onServer",
            "FillRecipeC2SPacket", "EmiRecipeFiller.clientFill", "quickMoveStack",
            "does not mutate EMI bytecode", "native menu", "not current requirements",
            "runtime acceptance", "persistence", "strict log audit", "cleanup",
            "inventory-parity.md", "generated-data.md"
        )) assertTrue(proof.contains(required), "Missing supported-transport contract statement: $required")
    }
}
