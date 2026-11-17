package com.bettercontent.tests.suite

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.drivers.*
import com.fasterxml.jackson.databind.JsonNode
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Native terminal names a sidecar; the executor and qualifier independently verify its bytes. */
internal data class RawEvidenceRequest(val step: String, val name: String, val pointer: String,
    val maximumBytes: Int = 16 * 1024 * 1024, val maximumRecords: Int = 100_000,
    val maximumLineBytes: Int = 1024 * 1024, val countField: String = "records")
internal data class VerifiedRawEvidence(val file: BoundFile, val records: List<JsonNode>)
internal interface ExternalRawEvidenceDriver {
    fun rawEvidenceRequests(trial: TrialRequest): List<RawEvidenceRequest>
}

internal fun enrichRawEvidence(driver: CaseDriver, trial: TrialRequest, observations: List<DriverObservation>,
    nativeRoots: Map<String, Path>): List<DriverObservation> {
    val requests = (driver as? ExternalRawEvidenceDriver)?.rawEvidenceRequests(trial).orEmpty()
    require(requests.map { it.step to it.name }.distinct().size == requests.size) { "duplicate raw evidence binding" }
    require(observations.all { it.rawEvidence.isEmpty() }) { "raw evidence must be verified, not caller-provided" }
    val byStep = requests.groupBy { it.step }
    require(byStep.keys.all { name -> observations.count { it.step == name } == 1 }) { "raw evidence step missing/duplicated" }
    return observations.map { observation ->
        observation.copy(rawEvidence = byStep[observation.step].orEmpty().associate { requested ->
            require(requested.name.matches(Regex("[a-z][a-z0-9_.-]*")) && requested.pointer.startsWith("/") &&
                requested.maximumBytes in 1..16 * 1024 * 1024 && requested.maximumRecords in 1..100_000 &&
                requested.maximumLineBytes in 1..1024 * 1024 && requested.countField in setOf("records", "rows")) { "invalid bounded raw evidence contract" }
            val root = requireNotNull(nativeRoots[observation.source]) { "no authentic native stream root for ${observation.source}" }.toRealPath()
            requested.name to readRawEvidence(root, observation.payload.at(requested.pointer), requested)
        })
    }
}

internal fun readRawEvidence(root: Path, reference: JsonNode, contract: RawEvidenceRequest): VerifiedRawEvidence {
    require(reference.isObject && reference.path("path").isTextual && reference.path("sha256").asText().matches(shaPattern) &&
        reference.path("bytes").isIntegralNumber && reference.path("bytes").canConvertToInt() &&
        reference.path("bytes").asInt() in 1..contract.maximumBytes &&
        reference.path(contract.countField).isIntegralNumber && reference.path(contract.countField).canConvertToInt() &&
        reference.path(contract.countField).asInt() in 1..contract.maximumRecords) { "raw sidecar binding/size/count absent" }
    val relative = Path.of(reference.path("path").asText())
    require(!relative.isAbsolute && relative.nameCount > 0 && relative.none { it.toString() in setOf(".", "..") }) { "raw sidecar path escaped stream" }
    val base = root.toRealPath()
    var current = base
    relative.forEach { part -> current = current.resolve(part); require(!Files.isSymbolicLink(current)) { "raw sidecar symlink" } }
    require(current.toRealPath().startsWith(base) && Files.isRegularFile(current, NOFOLLOW_LINKS) &&
        Files.size(current) == reference.path("bytes").asLong()) { "raw sidecar missing or size mismatch" }
    val bytes = Files.newInputStream(current).use { it.readNBytes(contract.maximumBytes + 1) }
    require(bytes.size == reference.path("bytes").asInt() && bytes.last() == '\n'.code.toByte() &&
        Hashes.sha256(current) == reference.path("sha256").asText()) { "raw sidecar truncated, oversized, or hash drift" }
    val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    val lines = text.dropLast(1).split('\n')
    require(lines.size == reference.path(contract.countField).asInt() && lines.size <= contract.maximumRecords &&
        lines.all { it.isNotBlank() && it.toByteArray(StandardCharsets.UTF_8).size <= contract.maximumLineBytes }) { "raw record/line budget differs" }
    val records = lines.map { suiteMapper.readTree(it).also { row -> require(row.isObject) { "raw record must be an object" } } }
    require(Hashes.sha256(current) == reference.path("sha256").asText()) { "raw sidecar changed while read" }
    return VerifiedRawEvidence(BoundFile(current.toString(), reference.path("sha256").asText()), records)
}
