package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.NativeRequest
import com.bettercontent.tests.suite.jsonFingerprint
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Source-only synthetic loaded observations; no server or test executed without authorization. */
class LoadedBlockCensusWireTest {
    private val actor = UUID.randomUUID().toString()
    private val ctx = WitnessContext("campaign-${UUID.randomUUID()}", UUID.randomUUID().toString(),
        "epoch-00001", "epoch-${UUID.randomUUID()}", "branch", "primitive.harvest", "trial-1",
        "server", 1, 2, actor, "target.observe.loaded_v1", "diagnostic")
    private val bounds = TargetBounds("minecraft:overworld", 0, 0, 64, 64, 0, 0, 4)
    // Independently authored native numeric-only bounds (dimension is top-level).
    private val wireBounds = mapOf("min_x" to 0, "max_x" to 0, "min_y" to 64, "max_y" to 64,
        "min_z" to 0, "max_z" to 0, "maximum_distance_blocks" to 4)
    private val selector = ReviewedTargetSelector("bc.playtest_target_selector.v1", "source.block",
        "a".repeat(64), "minecraft", "target.observe.loaded_v1", "minecraft:stone", "origin",
        bounds, TargetSource.NATURAL_UNMODIFIED_BLOCK, "loaded_block_nearest_center_then_xyz_v1",
        TargetSelectionOrder.NEAREST_THEN_POSITION, 30, 2)
    private val hash = jsonFingerprint(suiteMapper.valueToTree(selector))
    private val request = NativeRequest("bc.playtest_request.v2", ctx.nativeSessionId, ctx.caseId,
        "b".repeat(64), 2, "target.observe.loaded_v1", 200,
        suiteMapper.valueToTree(mapOf("player_uuid" to actor, "dimension" to bounds.dimension,
            "kind" to "block", "resource_id" to selector.resourceId, "selector_sha256" to hash,
            "max_candidates" to 16, "bounds" to wireBounds, "witness_context" to ctx)))
    private fun raw(rows: List<Map<String, Any>>, complete: Boolean = true): MutableMap<String, Any> =
        linkedMapOf<String, Any>("schema" to "bc.playtest_loaded_target_census.v1",
            "witness_context" to ctx, "selector_sha256" to hash,
            "dimension" to bounds.dimension, "kind" to "block", "resource_id" to selector.resourceId,
            "bounds" to wireBounds, "max_candidates" to 16,
            "query_tick" to 42, "query_monotonic_nanos" to 100L,
            "query_end_tick" to 42, "query_end_monotonic_nanos" to 200L,
            "actor_at_query" to mapOf("uuid" to actor, "x" to 0.5, "y" to 64.5, "z" to 0.5),
            "chunks_checked" to 1, "candidate_count" to rows.size, "complete" to complete,
            "natural_origin_proven_by_native" to false,
            "natural_acquisition_proven_by_native" to false, "candidates" to rows).also {
                if (!complete) { it["incomplete_reason"] = "queried_chunk_not_loaded";
                    it["unloaded_chunk_x"] = 0; it["unloaded_chunk_z"] = 0 }
            }
    private fun candidate(): Map<String, Any> = mapOf("x" to 0, "y" to 64, "z" to 0,
        "observed_tick" to 42, "observed_monotonic_nanos" to 150L,
        "actor_distance_blocks" to 0.0,
        "block_state" to mapOf("id" to "minecraft:stone", "properties" to emptyMap<String, String>()))
    private fun capture(root: Path, data: MutableMap<String, Any>,
        changeTerminal: (com.fasterxml.jackson.databind.node.ObjectNode) -> Unit = {}): LoadedBlockCensus {
        val folder = root.resolve("targets")
        Files.createDirectories(folder)
        val file = folder.resolve("000000000002.json")
        Files.writeString(file, suiteMapper.writeValueAsString(data) + "\n")
        val payload = suiteMapper.readTree(suiteMapper.writeValueAsBytes(mapOf("schema" to "bc.playtest_loaded_target_census.v1",
            "witness_context" to ctx, "selector_sha256" to hash, "complete" to data.getValue("complete"),
            "census_file" to mapOf("path" to "targets/000000000002.json", "sha256" to Hashes.sha256(file),
                "bytes" to Files.size(file), "records" to data.getValue("candidate_count")))))
        (payload as com.fasterxml.jackson.databind.node.ObjectNode).let { objectNode ->
            data["incomplete_reason"]?.let { reason -> objectNode.put("incomplete_reason", reason.toString()) }
            changeTerminal(objectNode)
        }
        return captureLoadedBlockCensus(root, request, payload, ctx, selector)
    }
    @Test fun `complete loaded candidate is selectably loaded but never natural ancestry`() {
        val root = Files.createTempDirectory(Path.of("/home/dev/.tmp"), "loaded-block-")
        try {
            val census = capture(root, raw(listOf(candidate())))
            assertEquals(true, census.complete)
            assertEquals(LoadedBlockRow(0, 64, 0, "minecraft:stone", emptyMap(), 0.0, 42, 150),
                census.reviewedLoadedCandidate(selector))
            assertThrows(IllegalArgumentException::class.java) {
                census.reviewedLoadedCandidate(selector.copy(order = TargetSelectionOrder.FIRST_CAUSAL_EVENT))
            }
            val empty = capture(root, raw(emptyList()))
            assertThrows(IllegalArgumentException::class.java) { empty.reviewedLoadedCandidate(selector) }
            val incomplete = capture(root, raw(emptyList(), complete = false))
            assertThrows(IllegalArgumentException::class.java) { incomplete.reviewedLoadedCandidate(selector) }
        } finally {
            Files.deleteIfExists(root.resolve("targets/000000000002.json"))
            Files.deleteIfExists(root.resolve("targets")); Files.deleteIfExists(root)
        }
    }
    @Test fun `raw and terminal contexts retain identity fields and numeric kinds`() {
        val root = Files.createTempDirectory(Path.of("/home/dev/.tmp"), "loaded-context-")
        try {
            val changes: List<(com.fasterxml.jackson.databind.node.ObjectNode) -> Unit> = listOf(
                { it.put("request_sequence", 2.0) }, { it.put("request_sequence", "2") },
                { it.put("request_sequence", "not-a-number") }, { it.put("request_sequence", 3L) },
                { it.put("actor_uuid", UUID.randomUUID().toString()) },
                { it.put("native_provenance_proven", true) })
            changes.forEach { change ->
                val wrongContext = suiteMapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(ctx)
                change(wrongContext)
                assertThrows(IllegalArgumentException::class.java) {
                    capture(root, raw(listOf(candidate())).apply { put("witness_context", wrongContext) })
                }
                assertThrows(IllegalArgumentException::class.java) {
                    capture(root, raw(listOf(candidate()))) { terminal ->
                        change(terminal.path("witness_context") as com.fasterxml.jackson.databind.node.ObjectNode)
                    }
                }
            }
        } finally {
            Files.deleteIfExists(root.resolve("targets/000000000002.json"))
            Files.deleteIfExists(root.resolve("targets")); Files.deleteIfExists(root)
        }
    }
    @Test fun `forged natural flag actor position and state ID reject despite newly computed file hash`() {
        val root = Files.createTempDirectory(Path.of("/home/dev/.tmp"), "loaded-block-")
        try {
            val falseClaims = raw(listOf(candidate())).apply { put("natural_origin_proven_by_native", true) }
            val wrongActor = raw(listOf(candidate())).apply {
                put("actor_at_query", mapOf("uuid" to UUID.randomUUID().toString(),
                    "x" to 0.5, "y" to 64.5, "z" to 0.5))
            }
            val wrongState = raw(listOf(candidate().toMutableMap().apply {
                put("block_state", mapOf("id" to "minecraft:dirt", "properties" to emptyMap<String, String>()))
            }))
            val nestedDimension = raw(listOf(candidate())).apply {
                put("bounds", wireBounds + ("dimension" to bounds.dimension))
            }
            listOf(falseClaims, wrongActor, wrongState, nestedDimension).forEach { row ->
                assertThrows(IllegalArgumentException::class.java) { capture(root, row) }
            }
        } finally {
            Files.deleteIfExists(root.resolve("targets/000000000002.json"))
            Files.deleteIfExists(root.resolve("targets")); Files.deleteIfExists(root)
        }
    }
}
