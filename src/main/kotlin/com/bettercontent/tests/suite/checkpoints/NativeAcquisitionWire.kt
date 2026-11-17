package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.NativeRequest
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

/** Narrow B v2 scoped block-drop/Forge-pickup wire, not natural ancestry or a post-seal proof. */
internal data class NativeWireStack(val itemId: String, val count: Long, val canonicalSha256: String,
    val contentSha256: String, val providerScope: String) {
    init {
        require(itemId.matches(resourcePattern) && count in 1..2_147_483_647 &&
            canonicalSha256.matches(shaPattern) && contentSha256.matches(shaPattern) &&
            providerScope == "vanilla_saved_ItemStack_NBT_including_ForgeCaps_not_external_inventory_adapter")
    }
    fun sameContent(other: NativeWireStack) = itemId == other.itemId &&
        contentSha256 == other.contentSha256 && providerScope == other.providerScope
}
internal data class CapturedScopedSource(val entityUuid: String, val sourceEventSha256: String,
    val terminalEventSha256: String, val sourceStack: NativeWireStack,
    val acquiredStacks: List<NativeWireStack>, val pickupPostEventSha256: List<String>) {
    init {
        require(sourceEventSha256.matches(shaPattern) && terminalEventSha256.matches(shaPattern) &&
            acquiredStacks.isNotEmpty() && acquiredStacks.size == pickupPostEventSha256.size &&
            pickupPostEventSha256.all(shaPattern::matches) &&
            acquiredStacks.fold(0L) { total, stack -> Math.addExact(total, stack.count) } == sourceStack.count)
    }
}
internal data class CapturedScopedPickup(val schema: String, val file: BoundFile, val bytes: Long,
    val records: Int, val declaredContext: WitnessContext, val boundTarget: BoundTarget,
    val sources: List<CapturedScopedSource>) {
    init { require(schema == "bc.playtest_scoped_pickup_wire_checked.v2" &&
        bytes in 1..16_777_216 && records in 4..256 && sources.isNotEmpty()) }
    // No natural-origin, external-provider, sealed inventory, or saved-state success field.
}
private class SourceReplay(val uuid: String, val sourceHash: String, val sourceStack: NativeWireStack,
    val sourceTick: Long) {
    var priorPostHash: String? = null
    var pendingPre: Pair<JsonNode, String>? = null
    var remaining: NativeWireStack? = sourceStack // null is observed EMPTY; never hash air:0.
    var terminalHash: String? = null
    var acquiredCount = 0L
    val acquired = mutableListOf<NativeWireStack>()
    val pickupHashes = mutableListOf<String>()
}
private fun nativeDigest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it) }
private fun exact(row: JsonNode, keys: Set<String>) {
    require(row.isObject && row.fieldNames().asSequence().toSet() == keys) { "native acquisition wire fields differ" }
}
private fun asserted(row: JsonNode, key: String) = row.path(key).isBoolean && row.path(key).asBoolean()
private val wireStackKeys = setOf("item_id", "count", "canonical_algorithm", "canonical_sha256",
    "content_algorithm", "content_sha256", "provider_scope")
private fun wireStack(row: JsonNode, inventorySlot: Boolean = false): NativeWireStack {
    exact(row, wireStackKeys + if (inventorySlot) setOf("slot") else emptySet())
    val count = row.path("count")
    require(count.isIntegralNumber && count.canConvertToLong() &&
        row.path("canonical_algorithm").asText() == "bc.playtest.stack-nbt.v1" &&
        row.path("content_algorithm").asText() == "bc.playtest.stack-content.v1")
    return NativeWireStack(row.path("item_id").asText(), count.asLong(),
        row.path("canonical_sha256").asText(), row.path("content_sha256").asText(),
        row.path("provider_scope").asText())
}
private fun inventorySlots(row: JsonNode): Map<Int, NativeWireStack> {
    require(row.isArray && row.size() <= 64)
    val slots = mutableMapOf<Int, NativeWireStack>()
    row.forEach { slotRow ->
        val stack = wireStack(slotRow, true)
        val slot = slotRow.path("slot")
        require(slot.isIntegralNumber && slot.canConvertToInt() && slot.asInt() in 0..63 &&
            slots.putIfAbsent(slot.asInt(), stack) == null)
    }
    return slots
}
private fun inventoryCounts(slots: Map<Int, NativeWireStack>): Map<String, Long> {
    val totals = mutableMapOf<String, Long>()
    slots.values.forEach { stack ->
        totals[stack.contentSha256] = Math.addExact(totals.getOrDefault(stack.contentSha256, 0), stack.count)
    }
    return totals
}
private val commonWireKeys = setOf("schema", "event_index", "kind", "tick", "item_entity_uuid",
    "controller_declared_context", "actual_native_session_id", "actual_case_id", "actual_request_sequence",
    "actual_actor_uuid", "actual_capability_id", "bound_target_sha256", "context_certified_by_native")
private val dropWireKeys = setOf("source_provider", "source_block_id", "source_block_state", "source_x",
    "source_y", "source_z", "entity_join_accepted", "join_phase", "source_stack")
private val preWireKeys = setOf("source_event_sha256", "prior_pickup_post_event_sha256",
    "entity_inserted_in_world", "source_stack", "inventory_before")
private val postWireKeys = setOf("source_event_sha256", "pickup_pre_event_sha256", "source_state_phase",
    "acquired_stack", "source_after_inventory_add", "inventory_before", "inventory_after",
    "inventory_vanilla_count_conserved", "source_stack_content_matches", "source_count_conserved")
private val terminalWireKeys = setOf("source_event_sha256", "terminal_phase", "entity_removed",
    "entity_in_world", "removed_entity_stack_reset_ignored", "source_terminal_state", "source_state_continuous")

/** One bounded NOFOLLOW capture, exact LF-inclusive links, per-entity source and global vanilla slot replay. */
internal fun captureScopedPickupWire(mailbox: Path, request: NativeRequest, terminal: JsonNode,
    controllerContext: WitnessContext, boundTarget: BoundTarget): CapturedScopedPickup {
    require(request.driver == "player.dig.witness_v1" && request.runId == controllerContext.nativeSessionId &&
        request.caseId == controllerContext.caseId && request.sequence == controllerContext.requestSequence &&
        controllerContext.actorUuid == request.parameters.path("player_uuid").asText() &&
        controllerContext.capabilityId == request.driver && controllerContext.source == "server" &&
        request.parameters.path("witness_context") == suiteMapper.valueToTree<JsonNode>(controllerContext) &&
        request.parameters.path("bound_target_sha256").asText() == boundTarget.bindingSha256 &&
        boundTarget.source == TargetSource.NATURAL_UNMODIFIED_BLOCK)
    boundTarget.requireAction(controllerContext)
    require(asserted(terminal, "acquisition_trace_complete") &&
        terminal.path("acquisition_trace_schema").asText() == "bc.playtest_acquisition_trace.v2" &&
        terminal.path("acquisition_origin").asText() ==
        "scoped_block_drop_runtime_only_natural_block_ancestry_unverified" &&
        terminal.path("unsupported_transitions").asText() ==
        "unobserved_external_provider_split_merge_transform_curios_fluid" &&
        terminal.path("sealed_state_proven_by_native").isBoolean &&
        !terminal.path("sealed_state_proven_by_native").asBoolean() &&
        !terminal.has("acquisition_trace_unsupported_reason")) { "native scoped pickup not complete" }
    val pointer = terminal.path("acquisition_trace_file")
    exact(pointer, setOf("schema", "path", "sha256", "bytes", "records"))
    val relative = "acquisition/%012d.jsonl".format(request.sequence)
    val recordNode = pointer.path("records")
    val bytesNode = pointer.path("bytes")
    require(pointer.path("schema").asText() == "bc.playtest_acquisition_trace.v2" &&
        pointer.path("path").asText() == relative && pointer.path("sha256").asText().matches(shaPattern) &&
        bytesNode.isIntegralNumber && bytesNode.canConvertToLong() && bytesNode.asLong() in 1..16_777_216 &&
        recordNode.isIntegralNumber && recordNode.canConvertToInt() && recordNode.asInt() in 4..256)
    val base = mailbox.toAbsolutePath().normalize()
    val parent = base.resolve("acquisition")
    val file = parent.resolve("%012d.jsonl".format(request.sequence))
    require(Files.isDirectory(base, NOFOLLOW_LINKS) && !Files.isSymbolicLink(base) &&
        Files.isDirectory(parent, NOFOLLOW_LINKS) && !Files.isSymbolicLink(parent) &&
        Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) &&
        Files.size(file) == bytesNode.asLong()) { "native pickup trace unowned or linked" }
    val bytes = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(16_777_217) }
    require(bytes.size.toLong() == bytesNode.asLong() && nativeDigest(bytes) == pointer.path("sha256").asText() &&
        bytes.isNotEmpty() && bytes.last() == '\n'.code.toByte()) { "native trace bytes/hash/terminal LF differ" }
    val lines = mutableListOf<ByteArray>()
    var begin = 0
    for (index in bytes.indices) if (bytes[index] == '\n'.code.toByte()) {
        val slice = bytes.copyOfRange(begin, index + 1)
        require(slice.size in 2..131_072 && slice[slice.lastIndex - 1] != '\r'.code.toByte() &&
            slice.dropLast(1).none { it == '\n'.code.toByte() || it == 0.toByte() })
        lines += slice; begin = index + 1
    }
    require(begin == bytes.size && lines.size == recordNode.asInt())
    val rows = lines.map { exactBytes ->
        val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(exactBytes)).toString()
        val row: JsonNode = suiteMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text)
        row
    }
    val hashes = lines.map(::nativeDigest)
    val sources = linkedMapOf<String, SourceReplay>()
    var actorInventoryAfter: JsonNode? = null
    var lastTick = -1L
    rows.forEachIndexed { index, row ->
        val kind = row.path("kind").asText()
        val uuid = row.path("item_entity_uuid").asText()
        require(runCatching { UUID.fromString(uuid).toString() == uuid }.getOrDefault(false))
        val ledger = sources[uuid]
        val terminalExtras = terminalWireKeys + if (ledger?.priorPostHash != null)
            setOf("last_pickup_post_event_sha256") else emptySet()
        val extras = when (kind) {
            "block_drop" -> dropWireKeys
            "pickup_pre" -> preWireKeys
            "pickup_post" -> postWireKeys
            "source_terminal" -> terminalExtras
            else -> error("unsupported native acquisition kind")
        }
        exact(row, commonWireKeys + extras)
        require(row.path("schema").asText() == "bc.playtest_acquisition_trace.v2" &&
            row.path("event_index").isIntegralNumber && row.path("event_index").asInt(-1) == index + 1 &&
            row.path("tick").isIntegralNumber && row.path("tick").canConvertToLong() &&
            row.path("tick").asLong() >= lastTick &&
            row.path("controller_declared_context") == suiteMapper.valueToTree<JsonNode>(controllerContext) &&
            row.path("actual_native_session_id").asText() == request.runId &&
            row.path("actual_case_id").asText() == request.caseId &&
            row.path("actual_request_sequence").isIntegralNumber &&
            row.path("actual_request_sequence").asLong(-1) == request.sequence &&
            row.path("actual_actor_uuid").asText() == controllerContext.actorUuid &&
            row.path("actual_capability_id").asText() == request.driver &&
            row.path("bound_target_sha256").asText() == boundTarget.bindingSha256 &&
            row.path("context_certified_by_native").isBoolean &&
            !row.path("context_certified_by_native").asBoolean()) {
            "native row differs from actual request/actor/context"
        }
        lastTick = row.path("tick").asLong()
        val rowHash = hashes[index]
        when (kind) {
            "block_drop" -> {
                require(ledger == null && row.path("source_provider").asText() ==
                    "Block.popResource(Level,BlockPos,ItemStack)" &&
                    row.path("source_block_id").asText() == boundTarget.resourceId &&
                    row.path("source_block_state").isTextual && row.path("source_block_state").asText().isNotBlank() &&
                    row.path("join_phase").asText() == "EntityJoinLevelEvent.MONITOR_before_actual_level_insertion" &&
                    asserted(row, "entity_join_accepted") &&
                    listOf("source_x", "source_y", "source_z").all { key ->
                        row.path(key).isIntegralNumber && row.path(key).canConvertToInt() } &&
                    row.path("source_x").asInt() == boundTarget.x &&
                    row.path("source_y").asInt() == boundTarget.y &&
                    row.path("source_z").asInt() == boundTarget.z &&
                    request.parameters.path("dimension").asText() == boundTarget.dimension &&
                    request.parameters.path("x").asInt(Int.MIN_VALUE) == boundTarget.x &&
                    request.parameters.path("y").asInt(Int.MIN_VALUE) == boundTarget.y &&
                    request.parameters.path("z").asInt(Int.MIN_VALUE) == boundTarget.z)
                sources[uuid] = SourceReplay(uuid, rowHash, wireStack(row.path("source_stack")), lastTick)
            }
            "pickup_pre" -> {
                val source = requireNotNull(ledger) { "pickup has no scoped source" }
                val prior = row.path("prior_pickup_post_event_sha256")
                require(source.pendingPre == null && source.terminalHash == null &&
                    row.path("source_event_sha256").asText() == source.sourceHash &&
                    (if (source.priorPostHash == null) prior.isNull else prior.isTextual &&
                        prior.asText() == source.priorPostHash) &&
                    asserted(row, "entity_inserted_in_world"))
                val stack = wireStack(row.path("source_stack"))
                require(stack == source.remaining &&
                    (actorInventoryAfter == null || row.path("inventory_before") == actorInventoryAfter)) {
                    "scoped source/count/identity or global actor inventory drifted before pickup"
                }
                inventorySlots(row.path("inventory_before"))
                source.pendingPre = row to rowHash
            }
            "pickup_post" -> {
                val source = requireNotNull(ledger) { "pickup post has no scoped source" }
                val (pre, preHash) = requireNotNull(source.pendingPre) { "pickup post lacks immediate pre" }
                require(source.terminalHash == null && row.path("source_event_sha256").asText() == source.sourceHash &&
                    row.path("pickup_pre_event_sha256").asText() == preHash &&
                    row.path("source_state_phase").asText() ==
                    "PlayerEvent.ItemPickupEvent.MONITOR_after_Inventory.add_before_ItemEntity.discard_or_stack_reset" &&
                    row.path("inventory_before") == pre.path("inventory_before") &&
                    asserted(row, "inventory_vanilla_count_conserved") &&
                    asserted(row, "source_stack_content_matches") && asserted(row, "source_count_conserved"))
                val before = wireStack(pre.path("source_stack"))
                val acquired = wireStack(row.path("acquired_stack"))
                val remainderNode = row.path("source_after_inventory_add")
                val remainder = when (remainderNode.path("kind").asText()) {
                    "empty" -> {
                        exact(remainderNode, setOf("kind", "count"))
                        require(remainderNode.path("count").isIntegralNumber && remainderNode.path("count").asInt(-1) == 0)
                        null
                    }
                    "nonempty" -> {
                        exact(remainderNode, setOf("kind", "stack"))
                        wireStack(remainderNode.path("stack"))
                    }
                    else -> error("unobserved or fabricated source remainder")
                }
                require(acquired.sameContent(before) &&
                    (remainder == null || remainder.sameContent(before)) &&
                    Math.addExact(acquired.count, remainder?.count ?: 0L) == before.count)
                val priorSlots = inventorySlots(pre.path("inventory_before"))
                val afterSlots = inventorySlots(row.path("inventory_after"))
                var slotDelta = 0L
                (priorSlots.keys + afterSlots.keys).forEach { slot ->
                    val prior = priorSlots[slot]; val after = afterSlots[slot]
                    if (prior != after) {
                        require(after != null && after.sameContent(acquired) &&
                            (prior == null || prior.sameContent(after) && after.count > prior.count &&
                                after.canonicalSha256 != prior.canonicalSha256)) {
                            "unaccounted vanilla slot/provider state transition"
                        }
                        slotDelta = Math.addExact(slotDelta, after.count - (prior?.count ?: 0L))
                    }
                }
                val expected = inventoryCounts(priorSlots).toMutableMap()
                expected[acquired.contentSha256] = Math.addExact(expected.getOrDefault(acquired.contentSha256, 0),
                    acquired.count)
                require(slotDelta == acquired.count && expected == inventoryCounts(afterSlots)) {
                    "independent vanilla inventory delta differs from acquired content/count"
                }
                source.acquiredCount = Math.addExact(source.acquiredCount, acquired.count)
                require(source.acquiredCount <= source.sourceStack.count)
                source.acquired += acquired; source.pickupHashes += rowHash
                source.remaining = remainder; source.priorPostHash = rowHash; source.pendingPre = null
                actorInventoryAfter = row.path("inventory_after")
            }
            "source_terminal" -> {
                val source = requireNotNull(ledger) { "terminal has no scoped source" }
                val state = row.path("source_terminal_state")
                exact(state, setOf("kind"))
                require(source.pendingPre == null && source.terminalHash == null &&
                    source.priorPostHash != null && row.path("source_event_sha256").asText() == source.sourceHash &&
                    row.path("last_pickup_post_event_sha256").asText() == source.priorPostHash &&
                    row.path("terminal_phase").asText() ==
                    "ServerTickEvent.END_after_ItemEntity.playerTouch_discard_or_partial_remain" &&
                    asserted(row, "entity_removed") && row.path("entity_in_world").isBoolean &&
                    !row.path("entity_in_world").asBoolean() &&
                    asserted(row, "removed_entity_stack_reset_ignored") &&
                    asserted(row, "source_state_continuous") && state.path("kind").asText() == "absent" &&
                    source.remaining == null && source.acquiredCount == source.sourceStack.count &&
                    lastTick >= Math.addExact(source.sourceTick, 10L)) {
                    "source terminal did not account for exact full pickup/removal"
                }
                source.terminalHash = rowHash
            }
        }
    }
    require(sources.isNotEmpty() && sources.values.all { it.terminalHash != null && it.pendingPre == null }) {
        "scoped entity has missing or duplicate terminal"
    }
    val captured = sources.values.map { source -> CapturedScopedSource(source.uuid, source.sourceHash,
        requireNotNull(source.terminalHash), source.sourceStack, source.acquired.toList(),
        source.pickupHashes.toList()) }
    return CapturedScopedPickup("bc.playtest_scoped_pickup_wire_checked.v2",
        BoundFile(file.toString(), pointer.path("sha256").asText()), bytes.size.toLong(), rows.size,
        controllerContext, boundTarget, captured)
}
