package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.CaseImplementation
import com.bettercontent.tests.suite.SuiteCase
import com.bettercontent.tests.suite.drivers.DriverAction
import com.bettercontent.tests.suite.jsonFingerprint
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Source-only conditional controller boundary; these tests are NOT authorized to run. */
class LoadedTargetRoutingTest {
    private val actor = UUID.randomUUID().toString()
    private val input = CheckpointRef("origin", BoundFile("/unread/source", "a".repeat(64)))
    private val selector = ReviewedTargetSelector("bc.playtest_target_selector.v1", "stone.target",
        "b".repeat(64), "minecraft", "target.observe.loaded_v1", "minecraft:stone", "origin",
        TargetBounds("minecraft:overworld", 0, 0, 64, 64, 0, 0, 4),
        TargetSource.NATURAL_UNMODIFIED_BLOCK, "loaded_block_nearest_center_then_xyz_v1",
        TargetSelectionOrder.NEAREST_THEN_POSITION, 30, 2)
    private val context = WitnessContext("campaign-${UUID.randomUUID()}", UUID.randomUUID().toString(),
        "epoch-00001", "epoch-${UUID.randomUUID()}", "branch", "primitive.harvest", "trial-1",
        "server", 1, 2, actor, "target.observe.loaded_v1", "diagnostic")
    private val authored = DriverAction("target.observe.loaded_v1",
        suiteMapper.valueToTree<ObjectNode>(mapOf("selector_id" to selector.id)), 200, "server")
    private val spec = SuiteCase("primitive.harvest", emptyList(), "reviewed", "campaign", "",
        emptyList(), emptyList(), emptyList(), 1, 30, emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), CaseImplementation("authored", "", ""),
        targetBindings = listOf(suiteMapper.valueToTree(selector)))
    @Test fun `controller derives exact native request only after allocated sequence and observed actor`() {
        val review = reviewedLoadedSelector(spec, authored, input)
        val derived = resolveLoadedTargetAction(authored, review, actor, context)
        assertEquals(setOf("player_uuid", "dimension", "kind", "resource_id", "selector_sha256",
            "bounds", "max_candidates", "witness_context"), derived.parameters.fieldNames().asSequence().toSet())
        assertEquals(actor, derived.parameters.path("player_uuid").asText())
        assertEquals("minecraft:overworld", derived.parameters.path("dimension").asText())
        // Independently authored native seven-field contract, not another call to the projection.
        assertEquals(suiteMapper.readTree("""{"min_x":0,"max_x":0,"min_y":64,"max_y":64,
            "min_z":0,"max_z":0,"maximum_distance_blocks":4}"""), derived.parameters.path("bounds"))
        assertEquals(context.requestSequence, derived.parameters.path("witness_context").path("request_sequence").asLong())
        assertEquals(jsonFingerprint(suiteMapper.valueToTree(selector)),
            derived.parameters.path("selector_sha256").asText())
        assertEquals("bc.playtest_action.v2", loadedCensusRequest(context.nativeSessionId,
            context.caseId, "c".repeat(64), 2, 200, derived).schema)
        assertThrows(IllegalArgumentException::class.java) {
            resolveLoadedTargetAction(authored, review, UUID.randomUUID().toString(), context)
        }
        assertThrows(IllegalArgumentException::class.java) {
            reviewedLoadedSelector(spec, authored.copy(parameters = suiteMapper.valueToTree<ObjectNode>(
                mapOf("selector_id" to selector.id, "player_uuid" to actor))), input)
        }
    }
    @Test fun `loaded selection never serializes a natural bound target and retains rejected empty roster`() {
        val file = BoundFile("/unread/targets/000000000002.json", "d".repeat(64))
        val hash = jsonFingerprint(suiteMapper.valueToTree(selector))
        val candidate = LoadedBlockRow(0, 64, 0, "minecraft:stone", emptyMap(), 0.0, 42, -10)
        val census = LoadedBlockCensus(file, hash, 42, -20, -1, 1, true, null, listOf(candidate))
        val chosen = loadedTargetAttempt(selector, input, census, context, "e".repeat(64),
            Instant.now(), -100, -100)
        assertEquals("loaded_only", chosen.outcome)
        assertEquals(candidate, chosen.selected)
        assertEquals(chosen, suiteMapper.readValue(suiteMapper.writeValueAsString(chosen),
            LoadedTargetAttempt::class.java)) // receipt uses ISO String, not unconfigured Instant module
        assertEquals(false, suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(chosen)
            .has("bound_target"))
        val empty = loadedTargetAttempt(selector, input, census.copy(candidates = emptyList()), context,
            "e".repeat(64), Instant.now(), -100, -100)
        assertEquals("rejected", empty.outcome)
        assertEquals(null, empty.selected)
        val nativeFailed = failedLoadedTargetAttempt(selector, input, context, "f".repeat(64),
            Instant.now(), -100, -100)
        assertEquals("rejected", nativeFailed.outcome)
        assertEquals(null, nativeFailed.censusFile) // failed native terminal never invents a census
        assertThrows(IllegalArgumentException::class.java) {
            loadedTargetAttempt(selector, input, census, context, "e".repeat(64), Instant.now(), -100,
                31_000_000_000L)
        }
    }
}
