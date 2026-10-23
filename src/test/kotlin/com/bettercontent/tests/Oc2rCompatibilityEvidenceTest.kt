package com.bettercontent.tests

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Synthetic binding/oracle controls, never native gameplay acceptance. */
@Tag("fast")
class Oc2rCompatibilityEvidenceTest {
    @TempDir lateinit var temporary: Path
    private val mapper = jacksonObjectMapper()
    private val capability = "[00:00:00] [modloading-worker-0/ERROR] [li.cil.oc2.common.Main.NativeLoader/]: Loaded native library successfully but ICMP still failed, falling back to JVM UDP implementation"
    private val readme = "[00:00:00] [Worker-Main-0/ERROR] [net.minecraft.Util/]: Invalid path in pack: oc2r:file_systems/README.md, ignoring"
    private data class Fixture(val repository: Path, val staging: Path, val logs: List<Path>, val bundle: Path)
    private fun fixture(name: String, mutate: (ObjectNode) -> Unit = {}, errors: String = "$capability\n$readme"): Fixture {
        val base = temporary.resolve(name)
        val repo = base.resolve("better-oc2r-create-controls")
        val staging = base.resolve("staged"); Files.createDirectories(staging)
        val provider = staging.resolve("better-compat-fixes-0.1.9.jar"); Files.writeString(provider, "synthetic provider")
        val dir = repo.resolve("build/gametest/00000000-0000-0000-0000-000000000001"); Files.createDirectories(dir.resolve("logs"))
        val proof = mapper.createObjectNode().apply {
            put("schema", "bc.oc2r_compat_proof.v1"); put("fixture_directory", dir.toString())
            put("archive_sha256", Oc2rCompatibilityEvidence.ARCHIVE); put("target_class_sha256", Oc2rCompatibilityEvidence.TARGET)
            put("compat_provider_sha256", Hashes.sha256(provider)); put("compat_loaded_provider_sha256", "1".repeat(64))
            putObject("network").apply { listOf("echo_snapshot", "caller_consumed", "udp_roundtrip", "tcp_roundtrip").forEach { put(it, true) } }
            putObject("filesystems").apply {
                put("rootfs_sha256", "cf46784e33abfe678c5635a73deb6b3e8cf1b888492475ad8ba4eccc8902574b")
                put("scripts_zip_sha256", "34066cda83ea2b274fb0b27d03391ae190b067d6d13cc12af7795b8802cbba85")
                put("all_descriptors_loaded", true)
            }
        }
        mutate(proof)
        val receipt = mapper.createObjectNode().apply {
            put("working_directory", dir.toString()); put("compat_provider_sha256", Hashes.sha256(provider))
            put("expected_native_tests", 4); put("old_world_absent", true); put("old_latest_log_absent", true)
        }
        val proofFile = dir.resolve("oc2r-compat-proof.json"); mapper.writeValue(proofFile.toFile(), proof)
        val receiptFile = dir.resolve("source-fixture.json"); mapper.writeValue(receiptFile.toFile(), receipt)
        val text = "$errors\nStarted game test server\n========= 4 GAME TESTS COMPLETE =========\nAll 4 required tests passed :)\nGame test server shutting down\nBC_OC2R_COMPAT_V1 proof_sha256=${Hashes.sha256(proofFile)}\n"
        val logs = listOf("latest.log", "debug.log").map { dir.resolve("logs/$it").also { p -> Files.writeString(p, text) } }
        val bundle = repo.resolve(Oc2rCompatibilityEvidence.SUPPORT); Files.createDirectories(bundle.parent)
        val data = mapper.createObjectNode().apply {
            put("schema", "bc.oc2r_compatibility_bundle.v1"); set<ObjectNode>("native_proof", proof)
            putObject("proof").apply { put("path", proofFile.toString()); put("sha256", Hashes.sha256(proofFile)) }
            set<ObjectNode>("source_fixture", receipt)
            putObject("source_fixture_artifact").apply { put("path", receiptFile.toString()); put("sha256", Hashes.sha256(receiptFile)) }
            putObject("native_observations").apply {
                put("schema", "bc.oc2r_native_observations.v1"); put("expected_tests", 4)
                putArray("completed_tests").add(4); putArray("required_passed").add(4)
                put("startup_observed", true); put("shutdown_observed", true)
            }
            putArray("native_logs").apply { logs.forEach { p -> addObject().apply { put("path", p.toString()); put("sha256", Hashes.sha256(p)) } } }
        }
        mapper.writeValue(bundle.toFile(), data)
        return Fixture(repo, staging, logs, bundle)
    }
    private fun context(f: Fixture) = Oc2rCompatibilityEvidence.source(f.repository, f.staging, f.logs)

    @Test fun exactDiagnosticsNeedCompleteBoundFunctionalEvidence() {
        val f = fixture("valid")
        assertEquals(4, LogPolicy.findings(f.logs).size)
        assertTrue(LogPolicy.findings(f.logs, context(f)).isEmpty())
    }
    @Test fun missingPartialOrDifferentCapabilitiesAndProviderNeverClassify() {
        val mutations: List<(ObjectNode) -> Unit> = listOf(
            { it.put("archive_sha256", "2".repeat(64)); Unit },
            { it.put("target_class_sha256", "2".repeat(64)); Unit },
            { it.put("compat_provider_sha256", "2".repeat(64)); Unit },
            { (it.path("network") as ObjectNode).put("echo_snapshot", false); Unit },
            { (it.path("network") as ObjectNode).remove("tcp_roundtrip"); Unit },
            { (it.path("filesystems") as ObjectNode).put("all_descriptors_loaded", false); Unit },
            { (it.path("filesystems") as ObjectNode).put("rootfs_sha256", "2".repeat(64)); Unit },
        )
        mutations.forEachIndexed { index, mutation ->
            val f = fixture("invalid-$index", mutation)
            assertThrows(IllegalArgumentException::class.java) { context(f) }
        }
    }
    @Test fun malformedFallbackResourceOtherLoggerAndFatalRemainFailures() {
        val invalid = listOf(
            capability.replace("NativeLoader", "Other"), capability.replace("still failed", "data does not match"),
            capability.replace("/ERROR", "/FATAL"), readme.replace("README.md", "rootfs.json"),
            "[00:00:00] [main/ERROR] [li.cil.oc2.common.inet.DefaultSessionLayer/]: Failed to get echo response",
        )
        val f = fixture("other-errors", errors = invalid.joinToString("\n"))
        assertEquals(invalid.size * 2, LogPolicy.findings(f.logs, context(f)).size)
    }
    @Test fun repeatsChangedLogsChangedProofOrUndeclaredLogsFailClosed() {
        val repeated = fixture("repeat", errors = "$capability\n$capability")
        assertThrows(IllegalArgumentException::class.java) { context(repeated) }
        val changed = fixture("changed"); val context = context(changed)
        Files.writeString(changed.logs.first(), "changed")
        assertThrows(IllegalArgumentException::class.java) { LogPolicy.findings(changed.logs, context) }
        val proof = fixture("proof"); Files.writeString(proof.logs.first().parent.parent.resolve("oc2r-compat-proof.json"), "{}")
        assertThrows(IllegalArgumentException::class.java) { context(proof) }
        val extra = fixture("extra"); val other = extra.logs.first().parent.resolve("unbound.log"); Files.writeString(other, capability)
        assertThrows(IllegalArgumentException::class.java) { Oc2rCompatibilityEvidence.source(extra.repository, extra.staging, extra.logs + other) }
    }
    @Test fun missingOriginalDebugLogCannotQualify() {
        val f = fixture("missing-debug")
        val data = mapper.readTree(f.bundle.toFile()) as ObjectNode
        (data.path("native_logs") as com.fasterxml.jackson.databind.node.ArrayNode).remove(1)
        mapper.writeValue(f.bundle.toFile(), data)
        assertThrows(IllegalArgumentException::class.java) { context(f) }
    }
    @Test fun liveSnapshotPermitsInfoAppendsButNotRepeatedDiagnosticsOrNewErrors() {
        val f = fixture("live")
        // Reflect a synthetic live context only to test scanning; this is not a public eligibility factory.
        val constructor = Oc2rCompatibilityEvidence::class.java.getDeclaredConstructor(Map::class.java, Boolean::class.javaPrimitiveType!!)
        constructor.isAccessible = true
        val live = constructor.newInstance(f.logs.associate { it.toAbsolutePath().normalize() to Hashes.sha256(it) }, false)
        Files.writeString(f.logs.first(), "[00:00:01] [main/INFO] [fixture/]: still running\n", java.nio.file.StandardOpenOption.APPEND)
        assertTrue(LogPolicy.findings(f.logs, live).isEmpty())
        Files.writeString(f.logs.first(), "$capability\n[00:00:02] [main/ERROR] [fixture/]: unexpected operation error\n", java.nio.file.StandardOpenOption.APPEND)
        assertEquals(2, LogPolicy.findings(f.logs, live).size)
    }
    @Test fun invalidCompletionAndUnqualifiedCandidateCannotImportSourceVerdict() {
        val f = fixture("completion")
        val data = mapper.readTree(f.bundle.toFile()) as ObjectNode
        (data.path("native_observations") as ObjectNode).putArray("required_passed").add(3)
        mapper.writeValue(f.bundle.toFile(), data)
        assertThrows(IllegalArgumentException::class.java) { context(f) }
        val binding = mapper.createObjectNode().apply {
            putArray("mods").addObject().apply {
                put("repository", "better-oc2r-create-controls"); put("source_gate_status", "NOT_VERIFIED")
                putObject("support_hashes").put(Oc2rCompatibilityEvidence.SUPPORT, "0".repeat(64))
            }
        }
        assertThrows(IllegalArgumentException::class.java) { Oc2rCompatibilityEvidence.candidate(temporary, binding, emptyList()) }
    }
}
