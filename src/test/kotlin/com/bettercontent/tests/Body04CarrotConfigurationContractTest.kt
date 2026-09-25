package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ceil

@Tag("fast")
class Body04CarrotConfigurationContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))

    @Test
    fun `Carrot whitelist and milestones match the complete runtime food census`() {
        val eligibleFoods = Files.readAllLines(root.resolve("src/test/resources/body04-eligible-foods.txt"))
            .filter(String::isNotBlank)
        assertEquals(624, eligibleFoods.size)
        assertEquals(eligibleFoods.sorted(), eligibleFoods)
        assertEquals(eligibleFoods.size, eligibleFoods.toSet().size)
        assertTrue(eligibleFoods.all { it.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")) })

        val config = Files.readString(root.resolve("defaultconfigs/solcarrot-server.toml"))
        assertTrue(config.contains("baseHearts = 5"))
        assertTrue(config.contains("heartsPerMilestone = 1"))
        assertTrue(config.contains("minimumFoodValue = 1"))
        assertTrue(config.contains("resetOnDeath = false"))
        assertTrue(config.contains("blacklist = []"))
        assertTrue(config.contains("limitProgressionToSurvival = false"))

        val whitelistText = Regex("(?s)whitelist = \\[(.*?)]").find(config)?.groupValues?.get(1)
            ?: error("Carrot whitelist is missing")
        val configuredFoods = Regex("\"([^\"]+)\"").findAll(whitelistText).map { it.groupValues[1] }.toList()
        assertEquals(eligibleFoods, configuredFoods)
        assertFalse(config.contains("whitelist = []"))

        val eligibleCount = eligibleFoods.size
        val finalMilestone = ceil(eligibleCount * 0.70).toInt()
        assertEquals(437, finalMilestone)
        val thresholds = (1..15).map { ceil(finalMilestone * it / 15.0).toInt() }
        assertEquals(
            listOf(30, 59, 88, 117, 146, 175, 204, 234, 263, 292, 321, 350, 379, 408, 437),
            thresholds,
        )
        val configuredThresholds = Regex("(?m)^\\s*milestones = \\[(.*)]$").find(config)?.groupValues?.get(1)
            ?.split(',')?.map(String::trim)?.map(String::toInt)
        assertEquals(thresholds, configuredThresholds)
    }
}
