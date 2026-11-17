package com.bettercontent.tests.suite.repositories

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** Pure source regressions only; no Gradle/source task or test has been authorized for execution. */
class RepositoryInputUseTest {
    @TempDir lateinit var root: Path
    private data class Setup(val plan: RepositoryRunPlan, val report: Path,
        val execution: RepositoryCommandExecution, val started: Instant, val ended: Instant)
    private fun setup(): Setup {
        val tool = root.resolve("gradlew")
        val provider = root.resolve("providers/pinned.jar")
        val artifact = root.resolve("candidate/mod.jar")
        val producer = root.resolve("src/test/UsageProducer.java")
        val source = root.resolve("src/main/Source.java")
        val config = root.resolve("gradle.properties")
        for (file in listOf(tool, provider, artifact, producer, source, config)) {
            Files.createDirectories(file.parent)
            Files.writeString(file, "reviewed bytes: ${file.fileName}")
        }
        fun input(file: Path) = RepositoryInput(root.relativize(file).toString(), Hashes.sha256(file),
            Files.size(file), Files.isExecutable(file))
        val groups = mapOf("toolchain" to listOf(input(tool)), "providers" to listOf(input(provider)),
            "artifact" to listOf(input(artifact)), "tests" to listOf(input(producer)),
            "source" to listOf(input(source)), "configuration" to listOf(input(config)))
        val now = Instant.now()
        val plan = RepositoryRunPlan("bc.playtest_repository_run_plan.v3", "reviewed", "reviewer", now.toString(),
            "one-run", "better-spawns", mapOf("client_sha256" to "a".repeat(64), "server_sha256" to "b".repeat(64)),
            BoundFile("unused", "a".repeat(64)), BoundFile("unused", "a".repeat(64)),
            BoundFile(artifact.toString(), Hashes.sha256(artifact)), root.toString(), "./gradlew",
            listOf("--no-daemon", "--no-build-cache", "--rerun-tasks", "--console=plain", "test"), emptyMap(),
            60, groups, listOf(RepositoryReportPlan("junit", "test", "build/test-results/test/report.xml",
                listOf("example.Test.one"), null, null, invocationTask = "test")),
            invocationTasks = listOf("test"), artifactBinding = RepositoryArtifactBinding("mod-jar",
                jar = BoundFile(artifact.toString(), Hashes.sha256(artifact))), executionContext = "source-only",
            usageReport = "build/usage.json", usageReportProducer = "src/test/UsageProducer.java")
        val execution = RepositoryCommandExecution(0, false,
            listOf(RepositoryProcessIdentity(123L, now.toString())), emptyList(), emptyList(), true, null, null)
        val ended = now.plusSeconds(20)
        val report = root.resolve("build/usage.json")
        Files.createDirectories(report.parent)
        val payload = suiteMapper.createObjectNode().put("schema", "bc.repository_input_use.v1")
            .put("scope", "source-only-not-packaged-runtime").put("repository", plan.repository)
            .put("run_id", plan.runId).put("reviewed_plan_sha256", "c".repeat(64))
            .put("producer_path", input(producer).relativePath).put("producer_sha256", input(producer).sha256)
            .put("artifact_loaded", false).put("observed_process_pid", 123L)
            .put("observed_process_start", now.toString()).put("captured_at", now.plusSeconds(1).toString())
        payload.putArray("invocation_tasks").add("test")
        payload.putArray("report_leaf_tasks").add("test")
        payload.putObject("used_inputs").apply {
            putArray("toolchain").addObject().put("relative_path", input(tool).relativePath).put("sha256", input(tool).sha256)
            putArray("providers").addObject().put("relative_path", input(provider).relativePath).put("sha256", input(provider).sha256)
            putArray("artifact")
        }
        Files.writeString(report, suiteMapper.writeValueAsString(payload))
        return Setup(plan, report, execution, now, ended)
    }
    private fun mutate(s: Setup, edit: (ObjectNode) -> Unit) {
        val doc = suiteMapper.readTree(s.report.toFile()) as ObjectNode
        edit(doc)
        Files.writeString(s.report, suiteMapper.writeValueAsString(doc))
    }
    private fun verify(s: Setup) = RepositoryInputUse.verify(s.plan, root, s.report, "c".repeat(64),
        s.execution, s.started, s.ended)
    @Test fun `source task use claim is bounded to exact reviewed files and launched process birth`() {
        val s = setup()
        assertEquals(Hashes.sha256(s.report), verify(s).sha256)
        mutate(s) { it.put("observed_process_start", Instant.now().minusSeconds(60).toString()) }
        assertThrows(IllegalArgumentException::class.java) { verify(s) }
    }
    @Test fun `listed but unused provider or changed input cannot be reported as used`() {
        val s = setup()
        mutate(s) { (it.path("used_inputs") as ObjectNode).putArray("providers") }
        assertThrows(IllegalArgumentException::class.java) { verify(s) }
        val fresh = setup()
        val provider = root.resolve("providers/pinned.jar")
        Files.writeString(provider, "changed")
        assertThrows(IllegalArgumentException::class.java) { verify(fresh) }
    }
    @Test fun `task-produced use evidence must not be a hardlinked old report`() {
        val s = setup()
        Files.createLink(root.resolve("previous-run-use.json"), s.report)
        assertThrows(IllegalArgumentException::class.java) { verify(s) }
    }
    @Test fun `source-only observation cannot impersonate packaged runtime`() {
        val s = setup()
        mutate(s) { it.put("artifact_loaded", true) }
        assertThrows(IllegalArgumentException::class.java) { verify(s) }
        val fresh = setup()
        mutate(fresh) { (it.path("used_inputs") as ObjectNode).withArray("artifact")
            .addObject().put("relative_path", "candidate/mod.jar").put("sha256", fresh.plan.artifact?.sha256) }
        assertThrows(IllegalArgumentException::class.java) { verify(fresh) }
    }
}
