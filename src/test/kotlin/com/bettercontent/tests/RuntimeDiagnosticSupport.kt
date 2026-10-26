package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

/** Separate, hash-bound test supplement: read-only observers plus explicitly requested launch/UI drivers.
 * No inventory commands, recipe actions or storage hooks; compiled driver authority is source-audited. */
class RuntimeDiagnosticSupport(private val evidence: EvidenceRun, pair: CandidatePair, player: String) {
    private val source = evidence.config.root.parent.resolve("mod_source/better-runtime-diagnostics/build/libs/better-runtime-test-support.jar")
    private val sourceHash = Hashes.sha256(source)
    val bridge = evidence.fixture.resolve("runtime-diagnostics").toAbsolutePath().normalize().also { it.createDirectories() }
    private val installed = mutableListOf<Path>()
    private val policies = mutableMapOf<Path, String>()
    val properties: String
    val supportSha256: String get() = sourceHash
    val runtimeSha256: String
    init {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val binding = System.getenv("BC_CANDIDATE_BINDING")?.takeIf(String::isNotBlank)?.let { mapper.readTree(Path.of(it).toFile()) }
        val boundHash = if (binding != null) {
            binding.path("mods").single { it.path("repository").asText() == "better-runtime-diagnostics" }
                .path("support_hashes").path("build/libs/better-runtime-test-support.jar").asText()
        } else {
            val handoff = requireNotNull(evidence.config.handoff) { "runtime support needs a canonical candidate binding or explicit handoff artifact pin" }
            val artifacts = mapper.readTree(handoff.toFile()).path("artifacts")
            artifacts.singleOrNull { it.path("path").asText() == source.toString() }?.path("sha256")?.asText()
                ?: error("handoff does not pin the prepared runtime support artifact")
        }
        require(boundHash == sourceHash) { "runtime support artifact is missing/unverified for this candidate" }
        evidence.integrity.register(source, sourceHash, ArtifactRole.SUPPORT_SOURCE)
        RetiredInventoryArtifactExclusion.validate(pair.client)
        RetiredInventoryArtifactExclusion.validate(pair.server)
        runtimeSha256=ZipFile(pair.server.toFile()).use { zip ->
            val entries=zip.entries().asSequence().filter { it.name.substringAfterLast('/').startsWith("better-runtime-diagnostics-") && it.name.endsWith(".jar") }.toList()
            require(entries.size==1) { "candidate must contain one runtime diagnostics JAR" }
            zip.getInputStream(entries.single()).use { MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { b -> "%02x".format(b) } }
        }
        properties=mapOf("run_id" to evidence.config.runId,"player" to player,"mode" to evidence.tier,
            "client_sha256" to pair.clientSha256,"server_sha256" to pair.serverSha256,
            "runtime_sha256" to runtimeSha256,"support_sha256" to sourceHash,"dir" to bridge.toString())
            .entries.joinToString(" ") { (key,value) ->
                require(value.none { it.isWhitespace() || it=='\"' || it=='\'' }) { "unsafe diagnostics property" }
                "-Dbc.pack_test.diagnostics.$key=$value"
            }
        evidence.event("runtime_diagnostic_support",mapOf("source" to source.toString(),"support_sha256" to sourceHash,
            "run_id" to evidence.config.runId,"candidate_client_sha256" to pair.clientSha256,"candidate_server_sha256" to pair.serverSha256))
    }
    fun install(root:Path) {
        require(root.toAbsolutePath().normalize().startsWith(evidence.fixture.toAbsolutePath().normalize())) { "diagnostics installation is fixture-only" }
        assertStable()
        val mods=root.resolve("mods").also { it.createDirectories() }
        RetiredInventoryArtifactExclusion.validateDirectory(mods)
        val policy=root.resolve("kubejs/config/crafting_policy.json")
        val original=policy.readText();val originalHash=Hashes.sha256(policy)
        val patched=RuntimeDiagnosticPolicy.classify(original)
        evidence.directory.resolve("diagnostic-policy-${installed.size}-original.json").toFile().writeText(original)
        policy.toFile().writeText(patched);policies[policy]=Hashes.sha256(policy)
        evidence.integrity.register(policy, policies.getValue(policy), ArtifactRole.FIXTURE_POLICY)
        evidence.event("diagnostic_fixture_classification",mapOf("path" to policy.toString(),"original_policy_sha256" to originalHash,
            "fixture_policy_sha256" to policies.getValue(policy),"namespace" to RuntimeDiagnosticPolicy.NAMESPACE))
        val destination=mods.resolve(source.fileName);Files.copy(source,destination);installed.add(destination)
        evidence.integrity.register(destination, sourceHash, ArtifactRole.SUPPORT_INSTALLED)
    }
    fun assertStable() {
        require(Hashes.sha256(source)==sourceHash) { "diagnostic source changed" }
        installed.forEach { require(Hashes.sha256(it)==sourceHash) { "installed diagnostic supplement changed" } }
        policies.forEach { (path,hash) -> require(Hashes.sha256(path)==hash) { "diagnostic fixture policy changed" } }
    }
    fun preserveBridge() {
        val output=evidence.directory.resolve("runtime-diagnostic-bridge").also { it.createDirectories() }
        Files.walk(bridge).use { files -> files.filter { Files.isRegularFile(it) }.forEach { source ->
            val target=output.resolve(bridge.relativize(source));target.parent.createDirectories();Files.copy(source,target,StandardCopyOption.REPLACE_EXISTING)
        } }
    }
}
