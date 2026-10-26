package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class PinnedFmlNewsPolicyTest {
    @Test fun bothDeliveredSidesDisableOnlyUnqualifiedUpdateNewsAndBindTheirInputs() {
        val inputs = jacksonObjectMapper().readTree(Files.readString(Path.of("gradle/active-custom-mods.json")))
            .path("verificationInputs").map { it.asText() }
        for (path in listOf("config/fml.toml", "server-config/config-overrides/fml.toml")) {
            val config = Files.readString(Path.of(path))
            assertTrue(Regex("(?m)^\\s*versionCheck\\s*=\\s*false\\s*$").containsMatchIn(config), path)
            assertFalse(Regex("(?m)^\\s*versionCheck\\s*=\\s*true\\b").containsMatchIn(config), path)
            assertTrue(path in inputs, "qualified inputs must bind $path")
        }
    }
}
