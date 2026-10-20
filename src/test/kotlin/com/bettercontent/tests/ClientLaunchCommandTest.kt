package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

@Tag("fast")
class ClientLaunchCommandTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val config = TestConfig(root, "synthetic", root.resolve("build/launch-command-tests"), 0,
        "Fixture", root.resolve("build/client-main"), Path.of(System.getProperty("java.home"), "bin/java"), null)
    private val client = root.resolve("build/client fixture with spaces")
    private val pins = ClientPackImport.Pins("Better Content", "", "Build 1", "1.20.1", "47.4.22")

    @Test fun normalCommandUsesValidatedPinsAndKeepsJvmArgumentAsOneToken() {
        val args = "-Xmx4G -Dlog4j.configurationFile=/fixture/raw.xml"
        val command = portableClientCommand(config, client, pins, args, "Fixture", "uuid", listOf("-s", "127.0.0.1", "-p", "1234"))
        assertEquals("forge:${pins.coordinate}", command.last())
        assertEquals(client.toString(), command[command.indexOf("--work-dir") + 1])
        assertEquals(listOf("--jvm-args=$args"), command.filter { it.startsWith("--jvm-args=") })
        assertEquals(listOf("-s", "127.0.0.1", "-p", "1234"), command.takeLast(5).dropLast(1))
    }

    @Test fun quickPlayCommandExplicitlySuppliesSameCoordinateAfterWorldName() {
        val command = quickPlayClientCommand(config, client, pins, "-Xmx4G", "Fixture", "uuid", "DebugWorld")
        assertEquals(listOf("Fixture", "uuid", "DebugWorld", pins.coordinate), command.takeLast(4))
        assertEquals(1, command.count { it == pins.coordinate })
        assertTrue(command.contains(root.resolve("src/test/resources/quickplay_world.py").toString()))
    }

    @Test fun bothBuildersRefuseUnsupportedPinsEvenIfConstructedWithoutManifestParser() {
        val bad = pins.copy(forge = "47.4.23")
        assertThrows(IllegalArgumentException::class.java) { portableClientCommand(config, client, bad, "", "p", "u", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { quickPlayClientCommand(config, client, bad, "", "p", "u", "world") }
    }
}
