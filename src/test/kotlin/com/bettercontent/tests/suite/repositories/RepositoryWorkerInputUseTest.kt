package com.bettercontent.tests.suite.repositories

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Pure synthetic bytes: these tests never launch a test JVM, agent, Gradle or game. */
class RepositoryWorkerInputUseTest {
    @TempDir lateinit var temporary: Path
    private data class Fixture(val root: Path, val evidence: Path, val plan: RepositoryRunPlan,
        val prepared: RepositoryWorkerInputUse.Prepared, val execution: RepositoryCommandExecution,
        val started: Instant, val ended: Instant, val worker: Path, val provider: Path)
    private fun fixture(): Fixture {
        val root = Files.createTempDirectory(temporary, "repository-")
        val evidence = Files.createTempDirectory(temporary, "evidence-")
        fun file(path: Path, value: String): Path {
            Files.createDirectories(path.parent); Files.writeString(path, value); return path
        }
        val provider = file(root.resolve("providers/actual.jar"), "real provider code")
        val artifact = file(root.resolve("candidate/packaged.jar"), "selected but not loaded")
        val source = file(temporary.resolve("scripts/playtest-repository-input-use/RepositoryInputUseAgent.java"), "source")
        val init = file(temporary.resolve("scripts/playtest-repository-input-use/worker-init.gradle"), "script")
        val agent = file(temporary.resolve("observer.jar"), "compiled observer")
        val receipt = file(temporary.resolve("receipt.json"), "build receipt")
        val java = file(temporary.resolve("java"), "actual JVM")
        val external = file(temporary.resolve("junit.jar"), "external test library")
        fun bound(path: Path) = BoundFile(path.toString(), Hashes.sha256(path))
        fun input(path: Path) = RepositoryInput(root.relativize(path).toString(), Hashes.sha256(path),
            Files.size(path), Files.isExecutable(path))
        val plan = RepositoryRunPlan("bc.playtest_repository_run_plan.v3", "reviewed", "reviewer", Instant.now().toString(),
            "run-1", "better-spawns", mapOf("client_sha256" to "a".repeat(64), "server_sha256" to "b".repeat(64)),
            bound(source), bound(receipt), bound(artifact), root.toString(), "./gradlew",
            listOf("--no-daemon", "--no-build-cache", "--rerun-tasks", "--console=plain", "test"), emptyMap(), 60,
            mapOf("source" to emptyList(), "tests" to emptyList(), "providers" to listOf(input(provider)),
                "toolchain" to emptyList(), "configuration" to emptyList(), "artifact" to listOf(input(artifact))),
            listOf(RepositoryReportPlan("junit", "test", "build/results.xml", listOf("one"), null, null,
                invocationTask = "test")), runtimeToolchainFacts = mapOf("java_major" to "21", "gradle_version" to "8.8"),
            invocationTasks = listOf("test"), artifactBinding = RepositoryArtifactBinding("mod-jar", jar = bound(artifact)),
            executionContext = "source-only", usageReport = "build/observed-workers", usageReportProducer = source.toString(),
            useHarness = RepositoryInputUseHarness(bound(source), bound(init), bound(agent), bound(receipt), bound(java),
                listOf(bound(external))))
        val prepared = RepositoryWorkerInputUse.prepare(plan, root, evidence, "f".repeat(64))
        val now = Instant.now()
        val worker = prepared.reports.values.single()
        val payload = suiteMapper.createObjectNode().put("schema", "bc.repository_input_use_worker.v2")
            .put("scope", "source-only-not-packaged-runtime").put("repository", plan.repository)
            .put("run_id", plan.runId).put("task", ":test")
            .put("reviewed_plan_sha256", "f".repeat(64)).put("producer_sha256", bound(source).sha256)
            .put("observed_process_pid", 77L).put("observed_process_start", now.toString())
            .put("captured_at", now.plusSeconds(1).toString())
            .put("java_executable", java.toString()).put("java_executable_sha256", bound(java).sha256)
            .put("java_version", "21.0.5")
        fun add(rows: com.fasterxml.jackson.databind.node.ArrayNode, path: Path, kind: String) {
            rows.addObject().put("path", path.toString()).put("kind", kind).apply {
                if (kind == "file") put("sha256", Hashes.sha256(path)) else putNull("sha256")
            }
        }
        val generated = Files.createDirectories(root.resolve("build/classes/java/test"))
        add(payload.putArray("code_sources"), provider, "file")
        add(payload.withArray("code_sources"), generated, "directory")
        add(payload.withArray("code_sources"), agent, "file")
        add(payload.putArray("class_path"), external, "file")
        Files.writeString(worker, suiteMapper.writeValueAsString(payload))
        val execution = RepositoryCommandExecution(0, false, listOf(RepositoryProcessIdentity(77L, now.toString())),
            emptyList(), emptyList(), true, null, null)
        return Fixture(root, evidence, plan, prepared, execution, now, now.plusSeconds(20), worker, provider)
    }
    private fun verify(f: Fixture) = RepositoryWorkerInputUse.verify(f.plan, f.root, f.prepared,
        "f".repeat(64), f.execution, f.started, f.ended, f.evidence)
    private fun change(f: Fixture, edit: (ObjectNode) -> Unit) {
        val doc = suiteMapper.readTree(f.worker.toFile()) as ObjectNode
        edit(doc); Files.writeString(f.worker, suiteMapper.writeValueAsString(doc))
    }
    @Test fun `real task JVM code source and JVM bytes, not listed Gradle inputs, are required`() {
        val f = fixture()
        assertEquals("bc.repository_input_use.v2", suiteMapper.readTree(Path.of(verify(f).path).toFile()).path("schema").asText())
    }
    @Test fun `same plan and Gradle parent PID do not replace an actual worker birth`() {
        val f = fixture()
        change(f) { it.put("observed_process_pid", 99L) }
        assertThrows(IllegalArgumentException::class.java) { verify(f) }
    }
    @Test fun `provider listed but not loaded and unknown external code source both fail`() {
        val f = fixture()
        change(f) { it.putArray("code_sources") }
        assertThrows(IllegalArgumentException::class.java) { verify(f) }
        val second = fixture()
        val unrelated = Files.writeString(temporary.resolve("unreviewed.jar"), "unknown")
        change(second) { it.withArray("class_path").addObject().put("path", unrelated.toString())
            .put("sha256", Hashes.sha256(unrelated)).put("kind", "file") }
        assertThrows(IllegalArgumentException::class.java) { verify(second) }
    }
    @Test fun `packaged artifact loaded by source-only worker cannot qualify`() {
        val f = fixture()
        val artifact = Path.of(requireNotNull(f.plan.artifact).path)
        change(f) { it.withArray("code_sources").addObject().put("path", artifact.toString())
            .put("sha256", Hashes.sha256(artifact)).put("kind", "file") }
        assertThrows(IllegalArgumentException::class.java) { verify(f) }
    }
    @Test fun `observer config mutation or unexpected second worker fails closed`() {
        val f = fixture()
        Files.writeString(Path.of(f.prepared.binding.path), "changed")
        assertThrows(IllegalArgumentException::class.java) { verify(f) }
        val second = fixture()
        Files.writeString(second.prepared.reportDirectory.resolve("unreviewed-worker.json"), "{}")
        assertThrows(IllegalArgumentException::class.java) { verify(second) }
    }
}
