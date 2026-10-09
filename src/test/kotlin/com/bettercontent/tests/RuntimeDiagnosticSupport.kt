package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

/** Non-cancelling diagnostic supplement: no inventory commands, recipes, actions or storage hooks. */
class RuntimeDiagnosticSupport(private val evidence: EvidenceRun, pair: CandidatePair, player: String) {
    private val source = evidence.config.root.parent.resolve("mod_source/better-runtime-diagnostics/build/libs/better-runtime-test-support.jar")
    private val sourceHash = Hashes.sha256(source)
    val bridge = evidence.fixture.resolve("runtime-diagnostics").toAbsolutePath().normalize().also { it.createDirectories() }
    private val installed = mutableListOf<Path>()
    private val policies = mutableMapOf<Path, String>()
    val properties: String
    init {
        RetiredInventoryArtifactExclusion.validate(pair.client)
        RetiredInventoryArtifactExclusion.validate(pair.server)
        val runtimeHash=ZipFile(pair.server.toFile()).use { zip ->
            val entries=zip.entries().asSequence().filter { it.name.substringAfterLast('/').startsWith("better-runtime-diagnostics-") && it.name.endsWith(".jar") }.toList()
            require(entries.size==1) { "candidate must contain one runtime diagnostics JAR" }
            zip.getInputStream(entries.single()).use { MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { b -> "%02x".format(b) } }
        }
        properties=mapOf("run_id" to evidence.config.runId,"player" to player,"mode" to evidence.tier,
            "client_sha256" to pair.clientSha256,"server_sha256" to pair.serverSha256,
            "runtime_sha256" to runtimeHash,"support_sha256" to sourceHash,"dir" to bridge.toString())
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
        evidence.event("diagnostic_fixture_classification",mapOf("path" to policy.toString(),"original_policy_sha256" to originalHash,
            "fixture_policy_sha256" to policies.getValue(policy),"namespace" to RuntimeDiagnosticPolicy.NAMESPACE))
        val destination=mods.resolve(source.fileName);Files.copy(source,destination);installed.add(destination)
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
