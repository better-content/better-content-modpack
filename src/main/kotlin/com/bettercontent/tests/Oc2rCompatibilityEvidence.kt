package com.bettercontent.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path

/** Eligibility for two exact upstream diagnostics, never a blanket networking or runtime verdict. */
class Oc2rCompatibilityEvidence private constructor(private val logs: Map<Path, String>) {
    fun classifies(path: Path, record: LogPolicy.Record?): Boolean =
        path.toAbsolutePath().normalize() in logs && exactDiagnostic(record)

    fun assertStable() = logs.forEach { (path, hash) -> require(Hashes.sha256(path) == hash) { "OC2R classified log changed: $path" } }

    companion object {
        const val SUPPORT = "build/compatibility/oc2r-compatibility.json"
        const val ARCHIVE = "d6c42cffda0fa16734e14e26013320b4f1a05278544dd5c5b8d05914bfbb8632"
        const val TARGET = "6f4a8b9c92b79f26695b3f21525e3c6c7649f16bb05903c11c8ce0e6d74bf0b8"
        private val mapper = jacksonObjectMapper()
        private val hex = Regex("[a-f0-9]{64}")
        private const val CAPABILITY = "Loaded native library successfully but ICMP still failed, falling back to JVM UDP implementation"
        private const val README = "Invalid path in pack: oc2r:file_systems/README.md, ignoring"

        private fun exactDiagnostic(r: LogPolicy.Record?): Boolean {
            if (r == null || r.severity != "ERROR") return false
            val logger = r.logger?.removeSuffix("/")
            return r.message == CAPABILITY && logger == "li.cil.oc2.common.Main.NativeLoader" &&
                r.thread?.matches(Regex("modloading-worker-[0-9]+")) == true ||
                r.message == README && logger == "net.minecraft.Util" &&
                r.thread?.matches(Regex("Worker-Main-[0-9]+")) == true
        }

        private fun ordinary(path: Path): Path = path.toAbsolutePath().normalize().also {
            CandidateBinding.requireOrdinary(it)
            require(Files.isRegularFile(it)) { "Missing OC2R evidence input: $it" }
        }

        private fun validateBundle(bundle: Path, providerHash: String): JsonNode {
            val data = mapper.readTree(ordinary(bundle).toFile())
            require(data.path("schema").asText() == "bc.oc2r_compatibility_bundle.v1") { "Unknown OC2R support schema" }
            val p = data.path("native_proof")
            require(p.path("schema").asText() == "bc.oc2r_compat_proof.v1" && p.path("archive_sha256").asText() == ARCHIVE &&
                p.path("target_class_sha256").asText() == TARGET && hex.matches(providerHash) &&
                p.path("compat_provider_sha256").asText() == providerHash &&
                hex.matches(p.path("compat_loaded_provider_sha256").asText())) { "OC2R upstream/provider proof identity differs" }
            require(data.path("source_fixture").path("compat_provider_sha256").asText() == providerHash &&
                data.path("source_fixture").path("expected_native_tests").asInt() == 4 &&
                data.path("source_fixture").path("old_world_absent").asBoolean() &&
                data.path("source_fixture").path("old_latest_log_absent").asBoolean()) { "OC2R source fixture binding/freshness differs" }
            val n = p.path("network")
            require(n.fieldNames().asSequence().toSet() == setOf("echo_snapshot", "caller_consumed", "udp_roundtrip", "tcp_roundtrip") &&
                n.all { it.isBoolean && it.booleanValue() }) { "Incomplete OC2R native network controls" }
            val fs = p.path("filesystems")
            require(fs.path("rootfs_sha256").asText() == "cf46784e33abfe678c5635a73deb6b3e8cf1b888492475ad8ba4eccc8902574b" &&
                fs.path("scripts_zip_sha256").asText() == "34066cda83ea2b274fb0b27d03391ae190b067d6d13cc12af7795b8802cbba85" &&
                fs.path("all_descriptors_loaded").asBoolean()) { "Incomplete OC2R native filesystem controls" }
            val o = data.path("native_observations")
            require(o.path("schema").asText() == "bc.oc2r_native_observations.v1" && o.path("expected_tests").asInt() == 4 &&
                o.path("completed_tests").map { it.asInt() } == listOf(4) && o.path("required_passed").map { it.asInt() } == listOf(4) &&
                o.path("startup_observed").asBoolean() && o.path("shutdown_observed").asBoolean()) { "Incomplete OC2R native population/lifecycle" }
            return data
        }

        private fun bind(paths: Collection<Path>): Oc2rCompatibilityEvidence {
            val logs = paths.distinct().filter(Files::isRegularFile).associate { path ->
                val p = ordinary(path)
                val records = Files.readAllLines(p).mapNotNull(LogPolicy::parseRecord).filter(::exactDiagnostic)
                require(records.count { it.message == CAPABILITY } <= 1 && records.count { it.message == README } <= 1) {
                    "Repeated OC2R capability/resource diagnostic remains a correctness failure: $p"
                }
                p to Hashes.sha256(p)
            }
            return Oc2rCompatibilityEvidence(logs)
        }

        /** Called only after the complete successful source invocation, before checkpoint admission. */
        fun source(repository: Path, staging: Path, paths: Collection<Path>): Oc2rCompatibilityEvidence? {
            if (repository.fileName.toString() != "better-oc2r-create-controls" || paths.isEmpty()) return null
            val provider = ordinary(staging.resolve("better-compat-fixes-0.1.9.jar"))
            val data = validateBundle(repository.resolve(SUPPORT), Hashes.sha256(provider))
            val fixture = Path.of(data.path("native_proof").path("fixture_directory").asText()).toAbsolutePath().normalize()
            require(fixture.startsWith(repository.resolve("build/gametest")) &&
                fixture.toString() == data.path("source_fixture").path("working_directory").asText()) { "OC2R fixture escaped source scope" }
            val proof = ordinary(Path.of(data.path("proof").path("path").asText()))
            val receipt = ordinary(Path.of(data.path("source_fixture_artifact").path("path").asText()))
            require(proof == fixture.resolve("oc2r-compat-proof.json") && receipt == fixture.resolve("source-fixture.json") &&
                Hashes.sha256(proof) == data.path("proof").path("sha256").asText() &&
                Hashes.sha256(receipt) == data.path("source_fixture_artifact").path("sha256").asText() &&
                mapper.readTree(proof.toFile()) == data.path("native_proof") && mapper.readTree(receipt.toFile()) == data.path("source_fixture")) {
                "OC2R original proof/receipt changed"
            }
            val declared = data.path("native_logs").associate { row -> ordinary(Path.of(row.path("path").asText())) to row.path("sha256").asText() }
            require(declared.isNotEmpty() && declared.keys.all { it.parent == fixture.resolve("logs") }) { "OC2R native log scope differs" }
            for ((log, hash) in declared) {
                require(Hashes.sha256(log) == hash) { "OC2R native source log changed: $log" }
                val text = Files.readString(log)
                require(text.lineSequence().count { it.endsWith("BC_OC2R_COMPAT_V1 proof_sha256=" + Hashes.sha256(proof)) } == 1 &&
                    text.contains("Started game test server") && text.contains("Game test server shutting down") &&
                    Regex("All 4 required tests passed").findAll(text).count() == 1 &&
                    Regex("4 GAME TESTS COMPLETE").findAll(text).count() == 1) { "OC2R source native completion/marker differs" }
            }
            require(paths.all { it.toAbsolutePath().normalize() in declared }) { "OC2R undeclared native audit log" }
            return bind(paths)
        }

        /** Explicit exact-candidate support binding; source proof supplies no imported runtime verdict. */
        fun candidate(root: Path, binding: JsonNode, paths: Collection<Path>): Oc2rCompatibilityEvidence? {
            val row = binding.path("mods").singleOrNull { it.path("repository").asText() == "better-oc2r-create-controls" } ?: return null
            val expected = row.path("support_hashes").path(SUPPORT).asText()
            if (expected.isEmpty()) return null
            require(hex.matches(expected) && row.path("source_gate_status").asText() == "PASSED_OWNING_GATES") { "Unqualified OC2R candidate support" }
            val bundle = ordinary(root.parent.resolve("mod_source/better-oc2r-create-controls").resolve(SUPPORT))
            require(Hashes.sha256(bundle) == expected) { "OC2R candidate support changed" }
            val compat = binding.path("mods").single { it.path("repository").asText() == "better-compat-fixes" }
            require(compat.path("source_gate_status").asText() == "PASSED_OWNING_GATES") { "Unqualified OC2R repair provider" }
            val provider = ordinary(root.resolve("mods").resolve(compat.path("artifact").asText()))
            require(Hashes.sha256(provider) == compat.path("sha256").asText()) { "OC2R candidate repair provider changed" }
            validateBundle(bundle, Hashes.sha256(provider))
            val pair = CandidateLocator.locate(root)
            require(binding.path("candidates").path("client_sha256").asText() == pair.clientSha256 &&
                binding.path("candidates").path("server_sha256").asText() == pair.serverSha256) { "OC2R candidate binding differs" }
            for (archive in listOf(pair.client, pair.server)) java.util.zip.ZipFile(archive.toFile()).use { zip ->
                val entries = zip.entries().asSequence().filter { it.name.substringAfterLast('/') == provider.fileName.toString() }.toList()
                require(entries.size == 1) { "Candidate must contain one exact OC2R repair provider" }
                val digest = zip.getInputStream(entries.single()).use { input ->
                    java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readBytes()))
                }
                require(digest == Hashes.sha256(provider)) { "Candidate packaged OC2R repair provider differs" }
            }
            val upstream = ordinary(root.parent.resolve(".cache/bc/packwiz-downloads/mods/oc2r-1.20.1-forge-2.2.12-all.jar"))
            require(Hashes.sha256(upstream) == ARCHIVE) { "OC2R candidate upstream archive differs" }
            return bind(paths)
        }
    }
}
