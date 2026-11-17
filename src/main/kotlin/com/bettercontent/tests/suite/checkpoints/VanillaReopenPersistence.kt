package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.core.JsonParser
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import com.bettercontent.tests.suite.NativeRequest
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.shaPattern
import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

/** A fresh-process, real actor, VANILLA-only live-vs-independently saved equality witness. */
internal data class VerifiedVanillaReopen(val input: CheckpointRef, val savedPlayerData: BoundFile,
    val actorUuid: String, val priorNativeSessionId: String, val reopenedEpochId: String,
    val reopenedNativeSessionId: String, val savedModeId: Int, val matchedSlots: Int) {
    init { require(input.id.isNotBlank() && matchedSlots == 41 && savedModeId in 0..3 &&
        priorNativeSessionId != reopenedNativeSessionId) }
}

/**
 * PRECONDITION: caller independently validates epoch's raw native protocol + request/terminal
 * journal (including the actual completed request), and the first post-join read-only action.
 * This cannot certify external/Curios inventories, moved machinery or temporal providers.
 */
internal fun replayFreshVanillaReopenFromEpoch(store: CheckpointStore, input: CheckpointRef,
    epoch: SessionEpoch): VerifiedVanillaReopen {
    val closure = requireNotNull(epoch.closure)
    val evidence = Path.of(closure.evidence.path).toAbsolutePath().normalize()
    val native = evidence.parent.resolve("native")
    require(evidence.fileName.toString() == "epoch-closure.json" &&
        Files.isDirectory(native, NOFOLLOW_LINKS) && !Files.isSymbolicLink(native))
    val parser = suiteMapper.copy().apply { factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION) }
    fun rows(ref: BoundFile?, name: String): List<JsonNode> {
        val file = native.resolve(name)
        require(ref != null && ref.path == file.toString() &&
            Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) &&
            Files.size(file) in 2..1_048_576 && Hashes.sha256(file) == ref.sha256)
        val result = Files.readAllLines(file).also { lines ->
            require(lines.size in 2..16 && lines.all { it.length in 2..65_536 })
        }.map { parser.readTree(it) }
        require(Hashes.sha256(file) == ref.sha256)
        return result
    }
    val requests = rows(epoch.nativeRequests, "requests.jsonl")
    val observations = rows(epoch.nativeObservations, "observations.jsonl")
    require(requests.size == 3 && requests.map { it.path("driver").asText() } ==
        listOf("inventory.discover", "player.snapshot", "fixture.cleanup") &&
        requests.map { it.path("sequence").asLong(-1) } == listOf(1L, 2L, 3L) &&
        requests.all { it.path("run_id").asText() == epoch.nativeSessionId } &&
        observations.all { it.path("run_id").asText() == epoch.nativeSessionId }) {
        "reopened player snapshot was not the FIRST actual native action after real inventory discovery"
    }
    fun terminal(sequence: Long, driver: String): JsonNode = observations.filter {
        it.path("request_sequence").asLong(-1) == sequence && it.path("driver").asText() == driver &&
            it.path("kind").asText() == "completed"
    }.single().path("payload")
    val actors = terminal(1, "inventory.discover").path("player_uuids")
    require(actors.isArray && actors.size() == 1 && actors[0].isTextual)
    val request = suiteMapper.treeToValue(requests[1], NativeRequest::class.java)
    return replayFreshVanillaReopen(store, input, epoch, request,
        terminal(2, "player.snapshot"), actors[0].asText())
}

internal fun replayFreshVanillaReopen(store: CheckpointStore, input: CheckpointRef,
    reopened: SessionEpoch, request: NativeRequest, snapshot: JsonNode, actorUuid: String): VerifiedVanillaReopen {
    require(runCatching { UUID.fromString(actorUuid).toString() == actorUuid }.getOrDefault(false) &&
        request.driver == "player.snapshot" && request.caseId == "control.persistence.vanilla-reopen" &&
        request.sequence == 2L && request.runId == reopened.nativeSessionId &&
        request.parameters.isObject && request.parameters.fieldNames().asSequence().toSet() == setOf("player_uuid") &&
        request.parameters.path("player_uuid").asText() == actorUuid &&
        reopened.campaignId.isNotBlank() && reopened.lineageId.isNotBlank() &&
        reopened.purpose == ExecutionPurpose.QUALIFICATION.wire && reopened.status == "passed" &&
        reopened.inputCheckpoint == input && reopened.restored?.reference == input &&
        reopened.restored.epochId == reopened.id && reopened.clientStream != null) {
        "reopen must be first actual actor read-only snapshot after a real fresh client join"
    }
    val source = store.verify(input)
    val restore = store.verifyRestoreReceipt(reopened.restored)
    val previous = source.closedEpoch
    val closure = requireNotNull(reopened.closure)
    require(restore == reopened.restored && source.bindings.campaignId == reopened.campaignId &&
        source.bindings.lineageId == reopened.lineageId && source.bindings.purpose == reopened.purpose &&
        source.sourceEpoch == previous.epochId && previous.nativeSessionId != reopened.nativeSessionId &&
        previous.saveValidated && previous.processCleanupComplete && previous.survivingPids.isEmpty() &&
        previous.ownedProcesses.isNotEmpty() && closure.ownedProcesses.isNotEmpty() &&
        closure.saveValidated && closure.processCleanupComplete && closure.survivingPids.isEmpty() &&
        closure.epochId == reopened.id && closure.nativeSessionId == reopened.nativeSessionId &&
        previous.ownedProcesses.none { it in closure.ownedProcesses } &&
        !Instant.parse(reopened.startedAt).isBefore(Instant.parse(source.sealedAt)) &&
        !Instant.parse(reopened.completedAt).isBefore(Instant.parse(reopened.startedAt))) {
        "previous shutdown/seal or fresh host server/client process and native session not independently bound"
    }
    val saved = readSealedPlayerInventory(source, actorUuid)
    verifyLiveVanillaSavedSlots(saved, snapshot, actorUuid)
    return VerifiedVanillaReopen(input, saved.playerData, actorUuid, previous.nativeSessionId,
        reopened.id, reopened.nativeSessionId, requireNotNull(saved.savedGameModeId), 41)
}

/** Immediate pre-stop saved-vs-live equality; full fresh-process/closure proof is repeated post-stop. */
internal fun verifyLiveVanillaSavedSlots(saved: SealedPlayerInventory, snapshot: JsonNode,
    actorUuid: String) {
    require(saved.playerUuid == actorUuid)
    val mode = mapOf(0 to "survival", 1 to "creative", 2 to "adventure", 3 to "spectator")
    require(saved.savedGameModeId in mode.keys && snapshot.path("uuid").asText() == actorUuid &&
        snapshot.path("mode").asText() == mode.getValue(requireNotNull(saved.savedGameModeId)) &&
        snapshot.path("synthetic_scale_participant").isBoolean &&
        !snapshot.path("synthetic_scale_participant").asBoolean()) {
        "live reopened actor/gamemode differs from independently decoded sealed playerdata"
    }
    val inventory = snapshot.path("inventory_slots_v1")
    require(inventory.isObject && inventory.fieldNames().asSequence().toSet() == setOf("schema", "source",
        "slots", "external_capabilities_included", "sealed_playerdata_proven_by_native") &&
        inventory.path("schema").asText() == "bc.playtest_vanilla_inventory_slots.v1" &&
        inventory.path("source").asText() == "actual_server_player_vanilla_inventory_0_40" &&
        inventory.path("external_capabilities_included").isBoolean &&
        !inventory.path("external_capabilities_included").asBoolean() &&
        inventory.path("sealed_playerdata_proven_by_native").isBoolean &&
        !inventory.path("sealed_playerdata_proven_by_native").asBoolean() &&
        inventory.path("slots").isArray && inventory.path("slots").size() == 41) {
        "actual live complete 41-slot vanilla provider export absent or falsely claims external/sealed proof"
    }
    inventory.path("slots").forEachIndexed { slot, row ->
        require(row.isObject && row.fieldNames().asSequence().toSet() == setOf("slot", "empty", "item",
            "count", "stack_canonical_algorithm", "stack_canonical_sha256") &&
            row.path("slot").isIntegralNumber && row.path("slot").asInt(-1) == slot &&
            row.path("empty").isBoolean && row.path("count").isIntegralNumber &&
            row.path("count").canConvertToInt()) { "native reopened vanilla slot roster omitted, duplicated or reordered" }
        when (val sealed = saved.vanillaSlot(slot)) {
            is SealedVanillaSlot.Empty -> require(row.path("empty").asBoolean() &&
                row.path("item").isNull && row.path("count").asInt(-1) == 0 &&
                row.path("stack_canonical_algorithm").isNull &&
                row.path("stack_canonical_sha256").isNull) {
                "saved empty slot not actually observed empty on fresh process"
            }
            is SealedVanillaSlot.Nonempty -> require(!row.path("empty").asBoolean() &&
                row.path("item").isTextual && row.path("item").asText().matches(resourcePattern) &&
                row.path("item").asText() == sealed.stack.itemId &&
                row.path("count").asInt(-1) == sealed.stack.count &&
                row.path("stack_canonical_algorithm").asText() == STACK_NBT_HASH_ALGORITHM &&
                row.path("stack_canonical_sha256").asText().matches(shaPattern) &&
                row.path("stack_canonical_sha256").asText() == sealed.stack.canonicalStackSha256) {
                "saved nonempty Count-sensitive NBT identity changed across actual fresh process load"
            }
        }
    }
}
