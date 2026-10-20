package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Authored default contract, not native rendering or existing-client migration acceptance. */
@Tag("fast")
class PingWheelDefaultContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root", "."))

    @Test
    fun `only the native always show names setting is overridden`() {
        val config = jacksonObjectMapper().readTree(root.resolve("config/ping-wheel.json").toFile())
        assertEquals(setOf("nameLabelForced"), config.fieldNames().asSequence().toSet())
        assertTrue(config["nameLabelForced"].isBoolean)
        assertTrue(config["nameLabelForced"].booleanValue())
        val pin = Files.readString(root.resolve("mods/ping-wheel.pw.toml"))
        assertTrue(pin.contains("filename = \"Ping-Wheel-1.10.1-forge-1.20.1.jar\""))
        assertTrue(pin.contains("hash = \"d18d22732ab4b8a60098efd666eb4a87b2ddc666\""))
    }
}
