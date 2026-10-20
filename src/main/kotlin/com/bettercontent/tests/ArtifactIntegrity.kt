package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

enum class ArtifactRole { CANDIDATE_CLIENT, CANDIDATE_SERVER, SUPPORT_SOURCE, SUPPORT_INSTALLED, FIXTURE_POLICY }

data class IntegrityFinding(val path: Path?, val roles: Set<ArtifactRole>, val error: String)

/** Invocation-local expectations, never a reusable receipt or a substitute for candidate binding. */
class ArtifactIntegrity {
    private data class Expected(val hash: String, val fileKey: Any, val roles: MutableSet<ArtifactRole>)
    private val artifacts = linkedMapOf<Path, Expected>()

    @Synchronized
    fun register(path: Path, sha256: String, role: ArtifactRole) {
        require(sha256.matches(Regex("[a-f0-9]{64}"))) { "invalid expected artifact SHA-256: $path" }
        val absolute = path.toAbsolutePath()
        require(absolute == absolute.normalize()) { "artifact path must be normalized: $path" }
        val key = ordinaryFileKey(absolute)
        val prior = artifacts[absolute]
        if (prior == null) artifacts[absolute] = Expected(sha256, key, linkedSetOf(role))
        else {
            require(prior.hash == sha256 && prior.fileKey == key) { "conflicting artifact expectation: $absolute" }
            prior.roles += role
        }
    }

    fun registerCandidate(pair: CandidatePair) {
        register(pair.client, pair.clientSha256, ArtifactRole.CANDIDATE_CLIENT)
        register(pair.server, pair.serverSha256, ArtifactRole.CANDIDATE_SERVER)
    }

    /** Returns every finding so one missing artifact cannot conceal another changed artifact. */
    @Synchronized
    fun audit(runtimeSupport: Boolean): List<IntegrityFinding> = buildList {
        val required = linkedSetOf(ArtifactRole.CANDIDATE_CLIENT, ArtifactRole.CANDIDATE_SERVER)
        if (runtimeSupport) required += listOf(ArtifactRole.SUPPORT_SOURCE, ArtifactRole.SUPPORT_INSTALLED, ArtifactRole.FIXTURE_POLICY)
        val registered = artifacts.values.flatMap { it.roles }.toSet()
        for (role in required - registered) add(IntegrityFinding(null, setOf(role), "required artifact role was not registered"))
        for ((path, expected) in artifacts) {
            try {
                require(ordinaryFileKey(path) == expected.fileKey) { "artifact file was replaced" }
                require(Hashes.sha256(path) == expected.hash) { "artifact SHA-256 changed" }
                // Also reject replacement during the digest read.
                require(ordinaryFileKey(path) == expected.fileKey) { "artifact file was replaced during audit" }
            } catch (failure: Exception) {
                add(IntegrityFinding(path, expected.roles.toSet(), failure.message ?: failure.javaClass.name))
            }
        }
    }

    private fun ordinaryFileKey(path: Path): Any {
        CandidateBinding.requireOrdinary(path)
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        require(attributes.isRegularFile) { "artifact is not an ordinary file: $path" }
        return requireNotNull(attributes.fileKey()) { "artifact filesystem supplies no stable file identity: $path" }
    }
}
