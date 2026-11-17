package com.bettercontent.tests.suite.repositories

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.time.Instant

/** Task-produced claim checked against independent plan, actual run PID/birth and immutable input bytes.
 * It remains a SOURCE-ONLY observation, not proof that a Minecraft runtime loaded packaged JARs. */
internal object RepositoryInputUse {
    fun verify(plan: RepositoryRunPlan, root: Path, report: Path, planSha256: String,
        execution: RepositoryCommandExecution, startedAt: Instant, finishedAt: Instant): BoundFile {
        require(plan.schema == "bc.playtest_repository_run_plan.v3" && plan.executionContext == "source-only" &&
            Files.isRegularFile(report, NOFOLLOW_LINKS) && !Files.isSymbolicLink(report) &&
            report.toRealPath().startsWith(root.toRealPath()) &&
            (Files.getAttribute(report, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1 &&
            Files.size(report) in 1L..1_048_576L &&
            Files.getLastModifiedTime(report, NOFOLLOW_LINKS).toInstant().let {
                it.isAfter(startedAt.minusSeconds(2)) && !it.isAfter(finishedAt.plusSeconds(2))
            }) {
            "fresh bounded source-task input-use report absent"
        }
        val doc = suiteMapper.readTree(report.toFile())
        val producer = plan.inputs.values.flatten().singleOrNull { it.relativePath == plan.usageReportProducer }
        require(producer != null && doc.path("schema").asText() == "bc.repository_input_use.v1" &&
            doc.path("scope").asText() == "source-only-not-packaged-runtime" &&
            doc.path("repository").asText() == plan.repository && doc.path("run_id").asText() == plan.runId &&
            doc.path("reviewed_plan_sha256").asText() == planSha256 &&
            doc.path("producer_path").asText() == producer.relativePath &&
            doc.path("producer_sha256").asText() == producer.sha256 &&
            doc.path("artifact_loaded").isBoolean && !doc.path("artifact_loaded").asBoolean() &&
            doc.path("invocation_tasks").isArray &&
            doc.path("invocation_tasks").map { it.asText() } == plan.invocationTasks &&
            doc.path("report_leaf_tasks").isArray &&
            doc.path("report_leaf_tasks").map { it.asText() }.toSet() == plan.reports.map { it.task }.toSet()) {
            "source-task use claim lacks independently bound producer/plan/intent"
        }
        val birth = doc.path("observed_process_start").asText()
        require(execution.ownedProcesses.any { it.pid == doc.path("observed_process_pid").asLong(-1) &&
            it.startedAt == birth } &&
            runCatching { Instant.parse(doc.path("captured_at").asText()) }.getOrNull()?.let {
                !it.isBefore(startedAt) && !it.isAfter(finishedAt)
            } == true) { "input-use claim was not bound to this launched process birth and run window" }
        val used = doc.path("used_inputs")
        for (group in listOf("toolchain", "providers", "artifact")) {
            val rows = used.path(group)
            require(rows.isArray && rows.all { it.isObject && it.size() == 2 &&
                it.path("relative_path").isTextual && it.path("sha256").asText().matches(shaPattern) }) {
                "invalid actual-use claim: $group"
            }
            val claimed = rows.map { it.path("relative_path").asText() to it.path("sha256").asText() }
            require(claimed.distinct().size == claimed.size) { "duplicate used input: $group" }
            if (group == "artifact") {
                require(claimed.isEmpty()) { "source-only tests cannot claim to have loaded packaged artifacts" }
            } else {
                val expected = plan.inputs.getValue(group).map { it.relativePath to it.sha256 }.toSet()
                require(claimed.toSet() == expected) { "actual $group use differs from independently reviewed inputs" }
                claimed.forEach { (relative, hash) ->
                    val file = root.resolve(relative)
                    require(Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) &&
                        file.toRealPath().startsWith(root.toRealPath()) && Hashes.sha256(file) == hash) {
                        "used $group byte identity changed: $relative"
                    }
                }
            }
        }
        return BoundFile(report.toString(), Hashes.sha256(report))
    }
}
