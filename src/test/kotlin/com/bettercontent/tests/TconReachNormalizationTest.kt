package com.bettercontent.tests

import com.bettercontent.tests.balance.TconReachNormalization as Reach
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Synthetic arithmetic and authored-input checks only; no measured Epic Fight acceptance. */
@Tag("fast")
class TconReachNormalizationTest {
    private val mapper = jacksonObjectMapper()
    private val catalogue = mapper.readTree(Path.of("kubejs/config/tcon_edps_catalogue.json").toFile())
    private val rows: List<JsonNode> = catalogue["tools"].toList() + catalogue["weapons"].toList()
    private val melee = rows.filter { it["model"].asText() != "ranged" }
    private val sword = Reach.REFERENCE_ITEM
    private val knife = "additionalweaponry:butcher_knife"
    private val pike = "tinkersweaponry:pike"
    // Deliberately synthetic digests, not actual candidate/evidence identities.
    private val inputs = "a".repeat(64)
    private val evidence = "b".repeat(64)

    private fun measurement(item: String, reach: Double) = Reach.Measurement(
        item, "synthetic:test", "synthetic_mainhand", inputs, evidence,
        listOf(Reach.Strike("synthetic_strike", reach, 1.0)),
    )

    private fun tiers(vararg reaches: Pair<String, Double>): Map<String, Double> =
        Reach.tiers(reaches.map { it.first }.toSet(), inputs, reaches.map { measurement(it.first, it.second) })

    @Test fun frozenDamageContributionsDetermineReachNotLongestFinisherOrUnweightedMean() {
        assertEquals(2.5, Reach.effectiveReach(listOf(
            Reach.Strike("cut", 2.0, 3.0), Reach.Strike("finisher", 4.0, 1.0),
        )), 1e-12)
        // Same-strike damage events must already be aggregated into the frozen contribution.
        assertEquals(3.0, Reach.effectiveReach(listOf(
            Reach.Strike("cut", 2.0, 2.0), Reach.Strike("finisher", 4.0, 2.0),
            Reach.Strike("non_damaging", 8.0, 0.0),
        )), 1e-12)
        assertEquals(3.0, Reach.effectiveReach(listOf(
            Reach.Strike("cut", 2.0, Double.MAX_VALUE),
            Reach.Strike("finisher", 4.0, Double.MAX_VALUE),
        )), 1e-12)
    }

    @Test fun swordAnchoredLinearMappingIncludesEndpointsTiesAndFractionalTiers() {
        val result = tiers(knife to 1.0, "test:short" to 1.5, sword to 3.0,
            "test:equal" to 3.0, "test:long" to 4.0, pike to 6.0, "test:tie" to 6.0)
        assertEquals(120.0, result.getValue(knife))
        assertEquals(115.0, result.getValue("test:short"))
        assertEquals(100.0, result.getValue(sword))
        assertEquals(100.0, result.getValue("test:equal"))
        assertEquals(93.333333, result.getValue("test:long"))
        assertEquals(80.0, result.getValue(pike))
        assertEquals(80.0, result.getValue("test:tie"))
        assertTrue(result.values.all { it in 80.0..120.0 })
    }

    @Test fun allEqualAndOneSidedRostersDoNotDivideByZeroOrForceUnusedExtremes() {
        assertEquals(setOf(100.0), tiers(sword to 3.0, knife to 3.0, pike to 3.0).values.toSet())
        assertEquals(mapOf(sword to 100.0), tiers(sword to 3.0))
        assertEquals(120.0, tiers(sword to 3.0, knife to 2.0).getValue(knife))
        assertEquals(80.0, tiers(sword to 3.0, pike to 4.0).getValue(pike))
    }

    @Test fun tiersAreMonotonicAndIndependentOfRosterOrdering() {
        val entries = (1..100).map { "test:item_$it" to it / 10.0 } + (sword to 5.0)
        val result = tiers(*entries.toTypedArray())
        assertEquals(result, tiers(*entries.reversed().toTypedArray()))
        val sortedTiers = entries.sortedBy { it.second }.map { result.getValue(it.first) }
        assertTrue(sortedTiers.zipWithNext().all { (left, right) -> left >= right })
    }

    @Test fun missingMixedDuplicateOrUnidentifiedEvidenceBlocksTheWholeRoster() {
        val roster = setOf(sword, knife, pike)
        val valid = listOf(measurement(sword, 3.0), measurement(knife, 2.0), measurement(pike, 5.0))
        assertEquals(3, Reach.tiers(roster, inputs, valid).size)
        val invalid = listOf(
            valid.dropLast(1), valid + valid.first(),
            valid + measurement("test:extra", 7.0),
            valid.map { if (it.item == knife) it.copy(inputManifestSha256 = "c".repeat(64)) else it },
            valid.map { if (it.item == knife) it.copy(evidenceSha256 = "unknown") else it },
            valid.map { if (it.item == knife) it.copy(style = "") else it },
            valid.map { if (it.item == knife) it.copy(capability = "") else it },
            valid.map { if (it.item == knife) it.copy(strikes = emptyList()) else it },
        )
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { Reach.tiers(roster, inputs, it) } }
        assertThrows(IllegalArgumentException::class.java) { Reach.tiers(roster, "unknown", valid) }
        assertThrows(IllegalArgumentException::class.java) { Reach.tiers(setOf(knife), inputs, listOf(valid[1])) }
    }

    @Test fun invalidDistancesWeightsAndStrikeIdentitiesFailClosed() {
        val invalid = listOf(
            emptyList(), listOf(Reach.Strike("hit", 0.0, 1.0)), listOf(Reach.Strike("hit", -1.0, 1.0)),
            listOf(Reach.Strike("hit", Double.NaN, 1.0)), listOf(Reach.Strike("hit", Double.POSITIVE_INFINITY, 1.0)),
            listOf(Reach.Strike("hit", 2.0, 0.0)), listOf(Reach.Strike("hit", 2.0, -1.0)),
            listOf(Reach.Strike("hit", 2.0, Double.NaN)), listOf(Reach.Strike("hit", 2.0, Double.POSITIVE_INFINITY)),
            listOf(Reach.Strike("", 2.0, 1.0)),
            listOf(Reach.Strike("hit", 2.0, 1.0), Reach.Strike("hit", 3.0, 1.0)),
        )
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { Reach.effectiveReach(it) } }
    }

    @Test fun ordinarySolvingRestoresTheOldSwordBaselineAndRetainsSpeedAndHeadScaling() {
        assertEquals(1.0, Reach.ordinaryMultiplier(100.0, 1.0, 1.6))
        assertEquals(0.8, Reach.ordinaryMultiplier(80.0, 1.0, 1.6))
        assertEquals(1.2, Reach.ordinaryMultiplier(120.0, 1.0, 1.6))
        assertEquals(2.4, Reach.ordinaryMultiplier(120.0, 1.0, 0.8))
        assertEquals(0.6, Reach.ordinaryMultiplier(120.0, 2.0, 1.6))
        assertEquals(1.530435, Reach.ordinaryMultiplier(110.0, 1.0, 1.15))
        for (tier in listOf(80.0, 93.333333, 100.0, 120.0)) {
            for (speed in listOf(0.5, 1.15, 1.6, 2.75)) {
                val multiplier = Reach.ordinaryMultiplier(tier, 2.0, speed)
                assertEquals(tier / 100.0, multiplier * 2.0 * speed / 1.6, 0.000002)
            }
        }
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertThrows(IllegalArgumentException::class.java) { Reach.ordinaryMultiplier(100.0, it, 1.6) }
            assertThrows(IllegalArgumentException::class.java) { Reach.ordinaryMultiplier(100.0, 1.0, it) }
        }
        listOf(79.0, 121.0, Double.NaN).forEach {
            assertThrows(IllegalArgumentException::class.java) { Reach.ordinaryMultiplier(it, 1.0, 1.6) }
        }
    }

    @Test fun pendingPolicyDoesNotPretendToHaveMeasurementsOrActivateNewDamage() {
        val policy = catalogue["reach_normalization"]
        assertEquals("bc.tcon_edps_catalogue.v1", catalogue["schema"].asText())
        assertEquals("awaiting_complete_measurements", policy["status"].asText())
        assertTrue(policy["measurements"].isArray && policy["measurements"].isEmpty)
        assertEquals(Reach.rosterSha256(melee.map { it["item"].asText() }.toSet()), policy["frozen_melee_roster_sha256"].asText())
        assertEquals(1.0, policy["requested_melee_scale"].asDouble())
        assertEquals(Reach.RANGED_SCALE, policy["retained_ranged_scale"].asDouble())
        assertEquals(0.9, catalogue["reference"]["global_scale"].asDouble())
        assertEquals(3.0, catalogue["reference"]["current_damage"].asDouble())
        assertEquals(Reach.REFERENCE_SPEED, catalogue["reference"]["attack_speed"].asDouble())
        assertEquals(0.9, melee.single { it["item"].asText() == sword }["multiplier"].asDouble())
        assertEquals(1.314783, melee.single { it["item"].asText() == knife }["multiplier"].asDouble())
    }

    @Test fun activeCatalogueStillMatchesAuthoredDamageWhileRecalibrationIsBlocked() {
        for (row in melee) {
            val item = row["item"].asText()
            val (namespace, name) = item.split(":", limit = 2)
            val definition = mapper.readTree(Path.of("kubejs/data/$namespace/tinkering/tool_definitions/$name.json").toFile())
            val damage = definition["modules"].single { it["type"].asText() == "tconstruct:multiply_stats" }
                .get("multipliers").get("tconstruct:attack_damage").asDouble()
            assertEquals(row["multiplier"].asDouble(), damage, 0.0000005, item)
            if (row["model"].asText() == "ordinary") {
                val expected = row["tier"].asDouble() / 100.0 * 0.9 * 1.6 /
                    row["head_scale"].asDouble() / row["speed"].asDouble()
                assertEquals(expected, damage, 0.0000005, item)
            }
        }
        val ranged = rows.filter { it["model"].asText() == "ranged" }
        assertEquals(setOf("tinkers_things:blowpipe", "tinkers_things:shortbow", "tconstruct:swasher",
            "tconstruct:longbow", "tconstruct:crossbow"), ranged.map { it["item"].asText() }.toSet())
        ranged.forEach {
            assertEquals(it["tier"].asDouble() / 100.0 * 0.9, it["projectile_multiplier"].asDouble(), 1e-12)
        }
    }
}
