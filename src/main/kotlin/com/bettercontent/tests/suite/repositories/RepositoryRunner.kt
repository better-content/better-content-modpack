package com.bettercontent.tests.suite.repositories

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Explicit run-only entry point; never called from campaign, discovery, qualification or class initialization. */
internal object RepositoryRunner {
    fun runApproved(campaignRoot: Path, planPath: Path, expectedPlanSha256: String,
        externalApproval: BoundFile): RepositoryRunObservation {
        require(shaPattern.matches(expectedPlanSha256) && Hashes.sha256(planPath) == expectedPlanSha256)
        val campaign = campaignRoot.toRealPath()
        val plan = suiteMapper.readValue(planPath.toFile(), RepositoryRunPlan::class.java)
        val fingerprints = RepositoryRunContracts.validate(plan, campaign)
        val v3 = plan.schema == "bc.playtest_repository_run_plan.v3"
        val artifactIdentity = if (v3) requireNotNull(plan.artifactBinding) else null
        require(shaPattern.matches(externalApproval.sha256))
        val approvalPath = Path.of(externalApproval.path).toRealPath()
        require(Files.isRegularFile(approvalPath, NOFOLLOW_LINKS) && !Files.isSymbolicLink(approvalPath) &&
            Hashes.sha256(approvalPath) == externalApproval.sha256 && approvalPath != planPath.toRealPath())
        val approval = suiteMapper.readTree(approvalPath.toFile())
        require(approval.path("schema").asText() == "bc.playtest_human_run_approval.v1" &&
            approval.path("status").asText() == "approved" && approval.path("approver").asText().isNotBlank() &&
            approval.path("run_id").asText() == plan.runId &&
            approval.path("repository").asText() == plan.repository &&
            approval.path("plan_sha256").asText() == expectedPlanSha256 &&
            plan.candidate.all { (key, value) -> approval.path("candidate").path(key).asText() == value }) {
            "a separately obtained, exact human run approval is required; a plan is not approval"
        }
        val root = RepositoryRunContracts.checkedSource(Path.of(plan.sourceRoot))
        val prereq = plan.reports.flatMap { listOfNotNull(it.relativePath, it.nativeRegistration, it.nativeLog, it.nativeOutcomes) } +
            listOfNotNull(plan.usageReport)
        require(prereq.distinct().size == prereq.size && prereq.all { !Files.exists(root.resolve(it), NOFOLLOW_LINKS) }) {
            "report path already exists: never accept preexisting or up-to-date test evidence"
        }
        val runs = campaign.resolve("repository-runs")
        if (!Files.exists(runs, NOFOLLOW_LINKS)) Files.createDirectory(runs)
        require(Files.isDirectory(runs, NOFOLLOW_LINKS) && !Files.isSymbolicLink(runs))
        val evidence = Files.createDirectory(runs.resolve(plan.runId))
        val reviewedPlan = evidence.resolve("reviewed-plan.json")
        Files.copy(planPath, reviewedPlan)
        require(Hashes.sha256(reviewedPlan) == expectedPlanSha256) { "reviewed source task attribution plan changed during capture" }
        val stdout = evidence.resolve("stdout.log")
        val stderr = evidence.resolve("stderr.log")
        val probeStdout = evidence.resolve("toolchain-stdout.log")
        val probeStderr = evidence.resolve("toolchain-stderr.log")
        val sourceExecutable = root.resolve(plan.executable.removePrefix("./"))
        val worker = if (v3) RepositoryWorkerInputUse.prepare(plan, root, evidence, expectedPlanSha256) else null
        val isolatedGradleHome = if (v3) Files.createDirectory(evidence.resolve("gradle-user-home")) else null
        val verificationCommand = listOf(sourceExecutable.toString()) +
            (if (v3) listOf("--init-script", requireNotNull(plan.useHarness).initScript.path) else emptyList()) +
            plan.arguments
        val probeEnvironment = plan.environment + (if (v3) mapOf(
            "GRADLE_USER_HOME" to requireNotNull(isolatedGradleHome).toString()) else emptyMap())
        val verificationEnvironment = probeEnvironment + (if (v3) mapOf(
            "BC_REPOSITORY_USE_BINDING" to requireNotNull(worker).binding.path,
            "BC_REPOSITORY_USE_BINDING_SHA256" to worker.binding.sha256) else emptyMap())
        val started = Instant.now()
        val blockers = mutableListOf<String>()
        // A reviewed source/toolchain file copy is not execution evidence. Run the exact reviewed
        // wrapper first, using its actual JVM/Gradle version response, before any verification task.
        val probe = RepositoryCommand.execute(listOf(sourceExecutable.toString(), "--version", "--offline",
            "--no-daemon", "--no-build-cache"), root, probeEnvironment, probeStdout, probeStderr,
            Duration.ofSeconds(minOf(plan.timeoutSeconds, 90)))
        val actual = Files.readString(probeStdout)
        val observed = mapOf(
            "gradle_version" to (Regex("(?m)^Gradle ([0-9]+(?:\\.[0-9]+){1,3})\\s*$").find(actual)?.groupValues?.get(1) ?: ""),
            "java_major" to (Regex("(?m)^(?:Launcher JVM|JVM):\\s*(?:[a-zA-Z ]*)?(\\d+)").find(actual)?.groupValues?.get(1) ?: ""))
        if (probe.exitCode != 0 || !probe.cleanupComplete || probe.strayDescendantPids.isNotEmpty() ||
            probe.survivingPids.isNotEmpty() || observed != plan.runtimeToolchainFacts) {
            blockers += "launched Gradle/JVM toolchain facts differ from reviewed facts or probe cleanup failed: " +
                "exit=${probe.exitCode} observed=$observed expected=${plan.runtimeToolchainFacts} " +
                "error=${probe.commandError} cleanup=${probe.cleanupFailure} " +
                "stray=${probe.strayDescendantPids} survivors=${probe.survivingPids}"
        }
        val verification = if (blockers.isEmpty()) RepositoryCommand.execute(
            verificationCommand, root, verificationEnvironment, stdout, stderr,
            Duration.ofSeconds(plan.timeoutSeconds)) else null
        if (verification == null) {
            Files.writeString(stdout, "", CREATE_NEW)
            Files.writeString(stderr, "", CREATE_NEW)
        } else if (verification.exitCode != 0 || !verification.cleanupComplete ||
            verification.strayDescendantPids.isNotEmpty() || verification.survivingPids.isNotEmpty()) {
            blockers += "verification command failed or left owned descendants: exit=${verification.exitCode} " +
                "timeout=${verification.timedOut} error=${verification.commandError} cleanup=${verification.cleanupFailure} " +
                "stray=${verification.strayDescendantPids} survivors=${verification.survivingPids}"
        }
        val completed = Instant.now()
        if (Hashes.sha256(planPath) != expectedPlanSha256) blockers += "reviewed invocation/leaf attribution plan changed during run"
        val exitCode = verification?.exitCode ?: -1
        val reportFiles = mutableListOf<RepositoryRunReportBinding>()
        val nativeFiles = mutableListOf<BoundFile>()
        var observedInputUse: BoundFile? = null
        if (exitCode == 0 && blockers.isEmpty()) {
            runCatching {
                require(RepositoryRunContracts.inventory(plan, root) == fingerprints) { "source or toolchain mutated during verification" }
                RepositoryRunContracts.requireObservedTaskExecution(Files.readString(stdout),
                    RepositoryRunContracts.requireExactTaskSelection(plan), plan.invocationTasks.toSet())
                if (v3) {
                    observedInputUse = RepositoryWorkerInputUse.verify(plan, root, requireNotNull(worker),
                        expectedPlanSha256, requireNotNull(verification), started, completed, evidence)
                }
                plan.reports.forEachIndexed { index, report ->
                    fun capture(relative: String, label: String): Path {
                        val original = root.resolve(relative)
                        require(Files.isRegularFile(original, NOFOLLOW_LINKS) && !Files.isSymbolicLink(original) &&
                            original.toRealPath().startsWith(root) &&
                            Files.getLastModifiedTime(original, NOFOLLOW_LINKS).toInstant().isAfter(started.minusSeconds(2))) {
                            "stale or missing raw $label report: $relative"
                        }
                        val target = evidence.resolve("$index-$label-${original.fileName}")
                        Files.copy(original, target, COPY_ATTRIBUTES)
                        require(Hashes.sha256(original) == Hashes.sha256(target)) { "raw report changed during capture" }
                        return target
                    }
                    val xml = capture(report.relativePath, "junit")
                    val ids = report.expectedTestIds.toSet()
                    val log = if (report.kind == "forge-gametest") capture(requireNotNull(report.nativeLog), "native-log") else null
                    if (report.kind == "forge-gametest") {
                        val registration = capture(requireNotNull(report.nativeRegistration), "registration")
                        val registry = suiteMapper.readTree(registration.toFile())
                        require(registry.path("schema").asText() == "bc.repository_native_registration.v1" &&
                            registry.path("run_id").asText() == plan.runId && registry.path("repository").asText() == plan.repository &&
                            registry.path("source").asText() == "GameTestRegistry.getAllTestFunctions" &&
                            registry.path("registered_test_ids").map { it.asText() }.toSet() == ids &&
                            registry.path("registered_test_ids").size() == ids.size) { "actual native registry differs from independently expected IDs" }
                        nativeFiles += bound(registration)
                        val outcomesFile = capture(requireNotNull(report.nativeOutcomes), "outcomes")
                        val outcomes = suiteMapper.readTree(outcomesFile.toFile())
                        require(outcomes.path("schema").asText() == "bc.repository_native_outcomes.v1" &&
                            outcomes.path("run_id").asText() == plan.runId && outcomes.path("repository").asText() == plan.repository &&
                            outcomes.path("registered_test_ids").map { it.asText() }.toSet() == ids &&
                            outcomes.path("outcomes").fieldNames().asSequence().toSet() == ids &&
                            ids.all { outcomes.path("outcomes").path(it).asText() == "passed" }) {
                            "actual native callback results missing, failing or differ from registration"
                        }
                        nativeFiles += bound(outcomesFile)
                    }
                    val claim = linkedMapOf<String, Any>(
                        "schema" to (if (v3) "bc.repository_report_binding.v3" else "bc.repository_report_binding.v2"),
                        "kind" to report.kind,
                        "invocation_task" to requireNotNull(report.invocationTask),
                        "reference_sha256" to plan.reference.sha256, "repository" to plan.repository,
                        "task" to report.task, "run_id" to plan.runId,
                        "source_sha256" to fingerprints.getValue("source"), "tests_sha256" to fingerprints.getValue("tests"),
                        "providers_sha256" to fingerprints.getValue("providers"), "toolchain_sha256" to fingerprints.getValue("toolchain"),
                        "configuration_sha256" to fingerprints.getValue("configuration"),
                        "raw_sha256" to Hashes.sha256(xml),
                        "completed_at" to completed.toString(), "report_timezone" to report.reportTimezone,
                        "test_ids" to report.expectedTestIds.sorted())
                    if (log != null) claim["native_log"] = bound(log)
                    if (v3) {
                        claim["artifact_binding"] = requireNotNull(artifactIdentity)
                        claim["execution_context"] = "source-only"
                        claim["observed_input_use"] = requireNotNull(observedInputUse)
                    } else claim["artifact_sha256"] = requireNotNull(plan.artifact).sha256
                    val sidecar = evidence.resolve("$index-binding.json")
                    Files.writeString(sidecar, suiteMapper.writeValueAsString(claim) + "\n", CREATE_NEW)
                    val expected = RepositoryReportExpectation(plan.reference.sha256, plan.repository,
                        report.task, plan.runId, fingerprints.getValue("source"), fingerprints.getValue("tests"),
                        fingerprints.getValue("providers"), fingerprints.getValue("toolchain"), fingerprints.getValue("configuration"),
                        plan.artifact?.sha256, ids, started, Hashes.sha256(xml), ZoneId.of(report.reportTimezone),
                        report.invocationTask, artifactIdentity, "source-only", observedInputUse?.sha256)
                    if (report.kind == "junit") RepositoryReportImporter.importJUnit(xml, sidecar, expected)
                    else RepositoryReportImporter.importNative(xml, sidecar, expected)
                    reportFiles += RepositoryRunReportBinding(report.kind, report.task, plan.runId,
                        ids.sorted(), started.toString(), report.reportTimezone, bound(xml), bound(sidecar), report.invocationTask)
                }
            }.onFailure { blockers += "raw membership/fingerprint validation failed: ${it.javaClass.simpleName}: ${it.message}" }
        }
        // No passing repository promise is manufactured. A later independent qualifier consumes
        // bound raw reports, reviewed per-test membership, and per-assertion gameplay evidence.
        val observation = RepositoryRunObservation(if (v3) "bc.playtest_repository_run.v3" else "bc.playtest_repository_run.v2",
            if (blockers.isEmpty()) "observed" else "blocked", plan.runId, plan.repository, started.toString(),
            completed.toString(), exitCode, plan.sourceAttestation, plan.reference,
            verificationCommand, bound(stdout), bound(stderr),
            bound(probeStdout), bound(probeStderr), observed, probe, verification, fingerprints,
            reportFiles, nativeFiles, blockers, bound(reviewedPlan), plan.reports,
            artifactIdentity, "source-only", observedInputUse)
        Files.writeString(evidence.resolve("observation.json"), suiteMapper.writeValueAsString(observation) + "\n", CREATE_NEW)
        return observation
    }

    private fun bound(path: Path) = BoundFile(path.toString(), Hashes.sha256(path))
}
