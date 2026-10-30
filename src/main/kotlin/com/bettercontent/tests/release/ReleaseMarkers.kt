package com.bettercontent.tests.release

import com.bettercontent.tests.CandidatePair
import com.bettercontent.tests.Hashes
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant

/** Immutable, candidate-bound claims. A packaged artifact is not a runtime pass. */
internal object ReleaseMarkers {
    private val mapper = jacksonObjectMapper()
    private val allTiers = listOf("smoke", "soak", "tele-soak", "visual")

    fun packageReady(pair: CandidatePair, runId: String, provenance: Path, inventory: Path, selected: ReleaseTierSelection) {
        requireStableCandidates(pair)
        val proofs = listOf(provenance, inventory).associate { it.fileName.toString() to Hashes.sha256(it) }
        create(pair.release.resolve("package-ready.json"), mapOf(
            "schema" to "bc.package_ready.v1", "run_id" to runId, "status" to "packaged",
            "created_at" to Instant.now().toString(), "candidates" to pair.document(),
            "proofs_sha256" to proofs,
            "proofs" to listOf(provenance, inventory).associate { it.fileName.toString() to mapOf(
                "path" to it.toAbsolutePath().normalize().toString(), "sha256" to Hashes.sha256(it)) },
            "selected_runtime_tiers" to selected.tiers,
            "tiers" to allTiers.associateWith { if (it in selected.tiers) "pending" else "not_run" }))
    }

    fun ready(pair: CandidatePair, runId: String, selected: ReleaseTierSelection, receipts: Map<String, Path>) {
        require("smoke" in selected.tiers && receipts.keys == selected.tiers.toSet()) { "Smoke and every requested tier must pass before ready" }
        val validated = receipts.mapValues { (_, path) ->
            require(Files.isRegularFile(path)) { "missing runtime receipt: $path" }
            mapOf("path" to path.toAbsolutePath().normalize().toString(), "sha256" to Hashes.sha256(path))
        }
        requireStableCandidates(pair)
        val packagePath = pair.release.resolve("package-ready.json")
        val packageClaim = mapper.readTree(packagePath.toFile())
        require(packageClaim.path("schema").asText() == "bc.package_ready.v1" &&
            packageClaim.path("run_id").asText() == runId &&
            packageClaim.path("candidates") == mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(pair.document()) &&
            packageClaim.path("selected_runtime_tiers").map { it.asText() } == selected.tiers &&
            listOf("provenance.json", "archive-members.json").all { name ->
                val proof = packageClaim.path("proofs").path(name)
                val path = Path.of(proof.path("path").asText())
                path.fileName.toString() == name && proof.path("sha256").asText() == Hashes.sha256(path) &&
                    packageClaim.path("proofs_sha256").path(name).asText() == proof.path("sha256").asText()
            }) {
            "package marker, proof or selected runtime tiers do not match Smoke acceptance"
        }
        create(pair.release.resolve("ready.json"), mapOf(
            "schema" to "bc.release_ready.v4", "run_id" to runId, "status" to "passed",
            "created_at" to Instant.now().toString(), "candidates" to pair.document(),
            "package_ready_sha256" to Hashes.sha256(packagePath),
            "tiers" to allTiers.associateWith { tier ->
                mapOf("status" to if (tier in validated) "passed" else "not_run", "receipt" to validated[tier])
            }))
    }

    fun result(evidence: Path, runId: String, selected: ReleaseTierSelection, passed: Map<String, Path>, failed: String?, failure: String? = null) {
        require(failed == null || failed in selected.tiers)
        create(evidence.resolve("release-result.json"), mapOf(
            "schema" to "bc.release_result.v2", "run_id" to runId,
            "status" to if (failure != null || failed != null) "failed" else if ("smoke" in passed) "passed" else "packaged",
            "failure" to failure, "selected_runtime_tiers" to selected.tiers,
            "tiers" to allTiers.associateWith { tier ->
                mapOf("status" to when (tier) { failed -> "failed"; in passed -> "passed"; else -> "not_run" },
                    "receipt" to passed[tier]?.let { mapOf("path" to it.toAbsolutePath().normalize().toString(), "sha256" to Hashes.sha256(it)) })
            }))
    }

    private fun create(path: Path, value: Any) {
        require(!Files.exists(path)) { "refusing existing release claim: $path" }
        val temporary = path.resolveSibling(".${path.fileName}.${java.util.UUID.randomUUID()}.tmp")
        try {
            Files.write(temporary, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value), StandardOpenOption.CREATE_NEW)
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE)
        } finally { Files.deleteIfExists(temporary) }
    }
}
