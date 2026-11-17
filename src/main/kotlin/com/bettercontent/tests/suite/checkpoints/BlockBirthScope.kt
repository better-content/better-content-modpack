package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.SuiteCase
import com.bettercontent.tests.suite.exclusiveJson
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

/** Stage2's ABSENT parent, not the present-state Stage1 mutation input. Neither is natural provenance. */
internal data class PrelaunchBlockBirthScope(val scope: BoundFile, val parentAbsence: BoundFile)

/** Bind one exact Store-verified parent absence before native startup; never use Stage1's present baseline. */
internal fun createBlockBirthScope(store: CheckpointStore, parent: CheckpointRef,
    specs: List<SuiteCase>, catalogueSha256: String, bindings: CheckpointBindings,
    epochId: String, nativeSessionId: String, evidence: Path): PrelaunchBlockBirthScope? {
    val positions = reviewedBirthPositions(specs, parent, catalogueSha256)
    if (positions.isEmpty()) return null
    val verified = verifyParentTerrainAbsent(store, parent, bindings, positions)
    val parentSidecar = bindParentTerrainAbsence(evidence, verified)
    val scopeFile = evidence.resolve("target-birth-scope.json").toAbsolutePath().normalize()
    require(scopeFile.parent == evidence.toAbsolutePath().normalize() &&
        !Files.exists(scopeFile, NOFOLLOW_LINKS))
    val scopeId = "scope-${UUID.randomUUID()}"
    exclusiveJson(scopeFile, linkedMapOf(
        "schema" to "bc.playtest_block_birth_scope.v1", "scope_id" to scopeId,
        "campaign_id" to bindings.campaignId, "lineage_id" to bindings.lineageId,
        "epoch_id" to epochId, "native_session_id" to nativeSessionId,
        "purpose" to bindings.purpose, "source" to "controller_prelaunch",
        "source_checkpoint_id" to parent.id, "source_checkpoint_sha256" to parent.receipt.sha256,
        "parent_absence_sidecar_sha256" to parentSidecar.sha256,
        "chunk_keys" to verified.chunkKeys.map { key -> mapOf("dimension" to key.dimension,
            "chunk_x" to key.chunkX, "chunk_z" to key.chunkZ) },
        "positions" to positions, "max_events" to 100_000, "max_bytes" to 16_777_216))
    require(Files.isRegularFile(scopeFile, NOFOLLOW_LINKS) && !Files.isSymbolicLink(scopeFile) &&
        Files.size(scopeFile) in 2..1_048_576)
    return PrelaunchBlockBirthScope(BoundFile(scopeFile.toString(), Hashes.sha256(scopeFile)), parentSidecar)
}

/** Reconstruct controller-owned scope from reviewed selectors, SHA-bound sidecar, and the sealed parent. */
internal fun replayBlockBirthScope(store: CheckpointStore, parent: CheckpointRef,
    specs: List<SuiteCase>, catalogueSha256: String, bindings: CheckpointBindings,
    epochId: String, nativeSessionId: String, evidence: Path,
    captured: PrelaunchBlockBirthScope): ParentTerrainAbsence {
    val dir = evidence.toAbsolutePath().normalize()
    val file = dir.resolve("target-birth-scope.json")
    require(dir.fileName.toString() == "evidence" && Files.isDirectory(dir, NOFOLLOW_LINKS) &&
        !Files.isSymbolicLink(dir) && captured.scope.path == file.toString() &&
        captured.scope.sha256.matches(shaPattern) && Files.isRegularFile(file, NOFOLLOW_LINKS) &&
        !Files.isSymbolicLink(file) && Files.size(file) in 2..1_048_576)
    val bytes = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(1_048_577) }
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    require(bytes.size in 2..1_048_576 && Files.size(file) == bytes.size.toLong() &&
        digest == captured.scope.sha256 && Hashes.sha256(file) == digest)
    val scope: JsonNode = suiteMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes)
    require(scope.isObject && scope.fieldNames().asSequence().toSet() == setOf("schema", "scope_id",
        "campaign_id", "lineage_id", "epoch_id", "native_session_id", "purpose", "source",
        "source_checkpoint_id", "source_checkpoint_sha256", "parent_absence_sidecar_sha256",
        "chunk_keys", "positions", "max_events", "max_bytes") &&
        scope.path("schema").asText() == "bc.playtest_block_birth_scope.v1" &&
        scope.path("scope_id").asText().matches(Regex("scope-[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) &&
        scope.path("campaign_id").asText() == bindings.campaignId &&
        scope.path("lineage_id").asText() == bindings.lineageId &&
        scope.path("epoch_id").asText() == epochId &&
        scope.path("native_session_id").asText() == nativeSessionId &&
        scope.path("purpose").asText() == bindings.purpose &&
        scope.path("source").asText() == "controller_prelaunch" &&
        scope.path("source_checkpoint_id").asText() == parent.id &&
        scope.path("source_checkpoint_sha256").asText() == parent.receipt.sha256 &&
        scope.path("parent_absence_sidecar_sha256").asText() == captured.parentAbsence.sha256 &&
        scope.path("max_events").isIntegralNumber && scope.path("max_events").asLong() == 100_000L &&
        scope.path("max_bytes").isIntegralNumber && scope.path("max_bytes").asLong() == 16_777_216L)
    val positions = reviewedBirthPositions(specs, parent, catalogueSha256)
    val absent = replayParentTerrainAbsence(store, parent, bindings, dir, captured.parentAbsence, positions)
    require(scope.path("positions") == suiteMapper.valueToTree<JsonNode>(positions) &&
        scope.path("chunk_keys") == suiteMapper.valueToTree<JsonNode>(absent.chunkKeys.map { key ->
            mapOf("dimension" to key.dimension, "chunk_x" to key.chunkX, "chunk_z" to key.chunkZ)
        }) && Hashes.sha256(file) == captured.scope.sha256)
    return absent
}
