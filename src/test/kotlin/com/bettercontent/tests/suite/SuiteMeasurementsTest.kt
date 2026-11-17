package com.bettercontent.tests.suite

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SuiteMeasurementsTest {
    private fun trees(dynamic: Int = 95) = (0 until 100).map { i ->
        TreeObservation("overworld", i, 64, 0, "temperate", "site-${i % 5}", if (i < dynamic) "dynamic-wild" else "regular-wild")
    }
    private val sites = (0 until 5).map { "site-$it" }.toSet()
    @Test fun `structural logs are never the regular-tree denominator`() {
        val structures = (100 until 1100).map { TreeObservation("overworld", it, 64, 0, "temperate", "site-0", "structure", "retained structure-box observation") }
        assertEquals("passed", evaluateTrees("temperate", trees() + structures, sites).state)
        assertEquals("failed", evaluateTrees("temperate", trees(94) + structures, sites).state)
    }
    @Test fun `missing ambiguous duplicated and unproven excluded tree samples do not pass`() {
        assertEquals("inconclusive", evaluateTrees("temperate", trees().take(99), sites).state)
        assertEquals("inconclusive", evaluateTrees("temperate", trees() + trees().first(), sites).state)
        assertEquals("inconclusive", evaluateTrees("temperate", trees().drop(1) + trees().first().copy(classification = "ambiguous"), sites).state)
        assertEquals("inconclusive", evaluateTrees("temperate", trees() + TreeObservation("overworld", 101, 64, 0, "temperate", "site-0", "structure"), sites).state)
    }
    @Test fun `food sample is order independent stratified and keeps starter requirement separate`() {
        val candidates = (0 until 30).map { FoodCandidate("test:food_$it", "behavior-${it % 5}") }
        val a = selectFoods(candidates, listOf("test:food_0"))
        assertEquals(a, selectFoods(candidates.reversed(), listOf("test:food_0")))
        assertEquals(10, a.sampled.size); assertEquals(10, a.sampled.distinct().size)
        assertEquals(5, candidates.filter { it.id in a.sampled }.map { it.category }.distinct().size)
        assertEquals(listOf("test:food_0"), a.mandatory)
        assertThrows(IllegalArgumentException::class.java) { selectFoods(candidates + candidates.first(), emptyList()) }
    }
    @Test fun `cohort requires full recovery and ten-minute native counter window`() {
        val samples = (0..630).map { TickSample(it * 1000L, it * 20L) }
        assertEquals("passed", evaluateCohortTicks(samples, 0).state)
        assertEquals("inconclusive", evaluateCohortTicks(samples.dropLast(1), 0).state)
        assertEquals("inconclusive", evaluateCohortTicks(samples.filterIndexed { i, _ -> i != 100 }, 0).state)
        val slow = (0..630).map { TickSample(it * 1000L, if (it <= 300) it * 20L else 6000L + (it - 300) * 18L) }
        assertEquals("failed", evaluateCohortTicks(slow, 0).state)
    }
    @Test fun `never recovered cohort fails rather than gaining an unbounded warmup`() {
        assertEquals("failed", evaluateCohortTicks((0..121).map { TickSample(it * 1000L, it * 18L) }, 0).state)
    }
    @Test fun `actual chunk overlap and inert fake players cannot qualify`() {
        val players = (0 until 8).map { i ->
            val chunks = setOf(ChunkPosition("overworld", i * 100, 0))
            FakePlayerFootprint("player-$i", true, 600, chunks, chunks)
        }
        assertEquals("passed", evaluateFootprints(players, 8).state)
        assertEquals("inconclusive", evaluateFootprints(players.dropLast(1) + players.last().copy(playerTicks = 0), 8).state)
        assertEquals("failed", evaluateFootprints(players.dropLast(1) + players.last().copy(loaded = players.first().loaded, ticking = players.first().ticking), 8).state)
        assertEquals("failed", evaluateFootprints(players, 16).state)
    }
    @Test fun `unrelated deaths cannot satisfy a threat and retries are finite`() {
        val unrelated = ThreatAttempt("unrelated-death", "starvation", "raw-death-event")
        assertEquals("inconclusive", evaluateThreat(List(3) { unrelated }, "pillagers").state)
        assertEquals("inconclusive", evaluateThreat(List(4) { unrelated }, "pillagers").state)
        assertEquals("failed", evaluateThreat(listOf(ThreatAttempt("timeout", "", "tick-deadline")), "pillagers").state)
        assertEquals("passed", evaluateThreat(listOf(unrelated, ThreatAttempt("encounter-death", "pillagers", "owned-attacker-event")), "pillagers").state)
        assertEquals("failed", evaluateThreat(listOf(ThreatAttempt("encounter-death", "zombie", "event")), "pillagers").state)
    }
}
