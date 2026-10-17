package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Duration

@Tag("fast")
class EndermanArmorContractTest {
    @Test
    fun `server joins set vanilla Enderman base armour without stacking or changing other entities`() {
        val root = Path.of(System.getProperty("bc.repo.root"))
        val output = BoundedCommand.capture(root, listOf(
            "node", root.resolve("src/test/resources/enderman-armor-contract.cjs").toString(),
            root.resolve("kubejs/startup_scripts/policy/enderman_armor.js").toString()
        ), Duration.ofSeconds(15))
        assertTrue(output.contains("enderman armour contract passed"))
    }
}
