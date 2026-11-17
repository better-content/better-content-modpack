package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.NativeRequest
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

/** Independently authored B v2 LF-wire bytes. Sources only, NEVER a producer run or natural/seal proof. */
class NativeAcquisitionWireTest {
    @TempDir lateinit var root: Path
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    private val actor = UUID.randomUUID().toString()
    private val run = "epoch-${UUID.randomUUID()}"
    private val entity = UUID.randomUUID().toString()
    private val context = WitnessContext("campaign-${UUID.randomUUID()}", UUID.randomUUID().toString(),
        "epoch-00001", run, "branch", "primitive.harvest", "trial-1", "server", 2, 3, actor,
        "player.dig.witness_v1", "diagnostic")
    private val binding = "d".repeat(64)
    private fun bound() = BoundTarget("bc.playtest_bound_target.v1", context.copy(actionOrdinal = 1, requestSequence = 1),
        "stone", TargetSource.NATURAL_UNMODIFIED_BLOCK, "minecraft", "minecraft:stone", null,
        "minecraft:overworld", 4, 64, 4, binding, "f".repeat(64),
        CheckpointRef("origin", BoundFile(root.resolve("origin.json").toString(), "a".repeat(64))))
    private fun request(): NativeRequest {
        val parameters = suiteMapper.createObjectNode().put("player_uuid", actor)
            .put("dimension", "minecraft:overworld").put("x", 4).put("y", 64).put("z", 4)
            .put("face", "up").put("bound_target_sha256", binding)
        parameters.set<JsonNode>("witness_context", suiteMapper.valueToTree(context))
        return NativeRequest("bc.playtest_action.v2", run, context.caseId, "e".repeat(64),
            3, "player.dig.witness_v1", 200, parameters)
    }
    private fun stack(count: Int): ObjectNode = suiteMapper.createObjectNode()
        .put("item_id", "minecraft:stone").put("count", count)
        .put("canonical_algorithm", "bc.playtest.stack-nbt.v1")
        .put("canonical_sha256", sha("full-$count".toByteArray()))
        .put("content_algorithm", "bc.playtest.stack-content.v1")
        .put("content_sha256", "c".repeat(64))
        .put("provider_scope", "vanilla_saved_ItemStack_NBT_including_ForgeCaps_not_external_inventory_adapter")
    private fun event(kind: String, index: Int): ObjectNode = suiteMapper.createObjectNode()
        .put("schema", "bc.playtest_acquisition_trace.v2").put("event_index", index)
        .put("kind", kind).put("tick", index.toLong()).put("item_entity_uuid", entity)
        .put("actual_native_session_id", run).put("actual_case_id", context.caseId)
        .put("actual_request_sequence", 3).put("actual_actor_uuid", actor)
        .put("actual_capability_id", "player.dig.witness_v1")
        .put("bound_target_sha256", binding).put("context_certified_by_native", false)
        .apply { set<JsonNode>("controller_declared_context", suiteMapper.valueToTree(context)) }
    private fun text(row: JsonNode) = suiteMapper.writeValueAsString(row) + "\n"
    private fun inventory(count: Int): ArrayNode = suiteMapper.createArrayNode().apply {
        if (count > 0) add(stack(count).put("slot", 0))
    }
    private fun wire(sourceCount: Int = 1, acquisitions: List<Int> = listOf(1),
        unexplainedShrink: Boolean = false, tamper: (MutableList<ObjectNode>) -> Unit = {}):
        Triple<Path, JsonNode, NativeRequest> {
        val rows = mutableListOf<ObjectNode>()
        val drop = event("block_drop", 1).put("source_provider", "Block.popResource(Level,BlockPos,ItemStack)")
            .put("source_block_id", "minecraft:stone").put("source_block_state", "Block{minecraft:stone}")
            .put("source_x", 4).put("source_y", 64).put("source_z", 4)
            .put("entity_join_accepted", true)
            .put("join_phase", "EntityJoinLevelEvent.MONITOR_before_actual_level_insertion")
            .apply { set<JsonNode>("source_stack", stack(sourceCount)) }
        rows += drop
        val sourceHash = sha(text(drop).toByteArray())
        var priorPostHash: String? = null
        var remaining = if (unexplainedShrink) 1 else sourceCount
        var total = 0
        acquisitions.forEach { count ->
            val pre = event("pickup_pre", rows.size + 1).put("source_event_sha256", sourceHash)
                .put("entity_inserted_in_world", true).apply {
                    if (priorPostHash == null) putNull("prior_pickup_post_event_sha256")
                    else put("prior_pickup_post_event_sha256", priorPostHash)
                    set<JsonNode>("source_stack", stack(remaining))
                    set<JsonNode>("inventory_before", inventory(total))
                }
            rows += pre
            remaining -= count
            total += count
            val afterSource = if (remaining == 0) suiteMapper.createObjectNode().put("kind", "empty").put("count", 0)
                else suiteMapper.createObjectNode().put("kind", "nonempty").apply {
                    set<JsonNode>("stack", stack(remaining))
                }
            val post = event("pickup_post", rows.size + 1).put("source_event_sha256", sourceHash)
                .put("pickup_pre_event_sha256", sha(text(pre).toByteArray()))
                .put("source_state_phase",
                    "PlayerEvent.ItemPickupEvent.MONITOR_after_Inventory.add_before_ItemEntity.discard_or_stack_reset")
                .put("inventory_vanilla_count_conserved", true).put("source_stack_content_matches", true)
                .put("source_count_conserved", true).apply {
                    set<JsonNode>("acquired_stack", stack(count))
                    set<JsonNode>("source_after_inventory_add", afterSource)
                    set<JsonNode>("inventory_before", inventory(total - count))
                    set<JsonNode>("inventory_after", inventory(total))
                }
            rows += post
            priorPostHash = sha(text(post).toByteArray())
        }
        val terminal = event("source_terminal", rows.size + 1).put("tick", rows.size + 20L)
            .put("source_event_sha256", sourceHash)
            .put("last_pickup_post_event_sha256", priorPostHash)
            .put("terminal_phase", "ServerTickEvent.END_after_ItemEntity.playerTouch_discard_or_partial_remain")
            .put("entity_removed", true).put("entity_in_world", false)
            .put("removed_entity_stack_reset_ignored", true).put("source_state_continuous", true)
            .apply { set<JsonNode>("source_terminal_state", suiteMapper.createObjectNode().put("kind", "absent")) }
        rows += terminal
        tamper(rows)
        val mailbox = root.resolve("native")
        val dir = mailbox.resolve("acquisition")
        Files.createDirectories(dir)
        val file = dir.resolve("000000000003.jsonl")
        val bytes = rows.joinToString("") { text(it) }.toByteArray()
        Files.write(file, bytes)
        val payload = suiteMapper.createObjectNode().put("acquisition_trace_complete", true)
            .put("acquisition_trace_schema", "bc.playtest_acquisition_trace.v2")
            .put("acquisition_origin", "scoped_block_drop_runtime_only_natural_block_ancestry_unverified")
            .put("unsupported_transitions", "unobserved_external_provider_split_merge_transform_curios_fluid")
            .put("sealed_state_proven_by_native", false)
        payload.putObject("acquisition_trace_file").put("schema", "bc.playtest_acquisition_trace.v2")
            .put("path", "acquisition/000000000003.jsonl").put("sha256", Hashes.sha256(file))
            .put("bytes", bytes.size).put("records", rows.size)
        return Triple(mailbox, payload, request())
    }
    private fun capture(wire: Triple<Path, JsonNode, NativeRequest>): CapturedScopedPickup =
        captureScopedPickupWire(wire.first, wire.third, wire.second, context, bound())

    @Test fun fullAndChainedPartialPickupsReplayPerSourceAndExactRawLfbasedHashes() {
        val one = capture(wire())
        assertEquals("bc.playtest_scoped_pickup_wire_checked.v2", one.schema)
        assertEquals(4, one.records)
        assertEquals(1, one.sources.single().acquiredStacks.single().count)
        val partial = capture(wire(50, listOf(5, 45)))
        assertEquals(6, partial.records)
        assertEquals(listOf(5L, 45L), partial.sources.single().acquiredStacks.map { it.count })
    }
    @Test fun nativeCompleteFlagCannotHideSilentShrinkOrUnobservedRemainder() {
        assertThrows(IllegalArgumentException::class.java) { capture(wire(50, listOf(1), unexplainedShrink = true)) }
        assertThrows(IllegalArgumentException::class.java) { capture(wire(50, listOf(5))) }
    }
    @Test fun danglingPriorHashWrongActorAndSlotContentReject() {
        assertThrows(IllegalArgumentException::class.java) {
            capture(wire(tamper = { rows -> rows[1].put("source_event_sha256", "f".repeat(64)) }))
        }
        assertThrows(IllegalArgumentException::class.java) {
            capture(wire(tamper = { rows -> rows[2].put("actual_actor_uuid", UUID.randomUUID().toString()) }))
        }
        assertThrows(IllegalArgumentException::class.java) {
            capture(wire(tamper = { rows ->
                (rows[2].path("inventory_after")[0] as ObjectNode).put("content_sha256", "b".repeat(64))
            }))
        }
        assertThrows(IllegalArgumentException::class.java) {
            capture(wire(tamper = { rows -> rows[3].put("source_terminal_state", "minecraft:air:0") }))
        }
    }
}
