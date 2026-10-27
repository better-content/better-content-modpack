package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClientProfileTest {
    @Test fun softwareProfileUsesCodecValidSimulationDistance() {
        val result = sessionClientOptions("renderDistance:12\nsimulationDistance:12\nmaxFps:120\ngraphicsMode:1\nao:true\nentityShadows:true\n")
        val options = result.lineSequence().filter { ':' in it }.associate { it.substringBefore(':') to it.substringAfter(':') }
        assertEquals("4", options["renderDistance"])
        assertEquals("5", options["simulationDistance"])
        assertTrue(options.getValue("simulationDistance").toInt() in 5..33)
        assertEquals("30", options["maxFps"])
        assertEquals("0", options["graphicsMode"])
        assertEquals("false", options["ao"])
        assertEquals("false", options["entityShadows"])
    }

    @Test fun profilePreservesUnrelatedOptionsAndIsIdempotent() {
        val input = "# retained comment\nsimulationDistance:4\nresourcePacks:[\"vanilla\",\"mod_resources\"]\nmusicVolume:0.5\ncustom:value:with:colons\n"
        val expected = input.replace("simulationDistance:4", "simulationDistance:5")
        assertEquals(expected, sessionClientOptions(input))
        assertEquals(expected, sessionClientOptions(expected))
        assertEquals("", sessionClientOptions(""))
    }
}
