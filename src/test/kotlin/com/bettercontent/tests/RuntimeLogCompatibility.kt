package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path

/** Explicit candidate-scoped compatibility eligibility; absent support keeps the default strict policy. */
internal fun runtimeLogCompatibility(evidence: EvidenceRun, logs: Collection<Path>): Oc2rCompatibilityEvidence? {
    val path = System.getenv("BC_CANDIDATE_BINDING")?.takeIf(String::isNotBlank)?.let(Path::of) ?: return null
    CandidateBinding.requireOrdinary(path)
    val binding = jacksonObjectMapper().readTree(path.toFile())
    val context = Oc2rCompatibilityEvidence.candidate(evidence.config.root, binding, logs) ?: return null
    val source = evidence.config.root.parent.resolve("mod_source/better-oc2r-create-controls").resolve(Oc2rCompatibilityEvidence.SUPPORT)
    evidence.integrity.register(source, Hashes.sha256(source), ArtifactRole.SUPPORT_SOURCE)
    return context
}
