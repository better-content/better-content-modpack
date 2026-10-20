package com.bettercontent.tests

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher

class EvidenceExtension(
    private val suite: String,
    private val evidenceSuffix: String? = null,
    private val config: () -> TestConfig = TestConfig::load,
) : BeforeAllCallback, AfterAllCallback, TestWatcher {
    lateinit var run: EvidenceRun
        private set
    private var successful = true
    private var priorFailure: Throwable? = null

    override fun beforeAll(context: ExtensionContext) {
        successful = true
        priorFailure = null
        val selected = System.getenv("BC_TEST_EVIDENCE_SUITE")?.takeIf(String::isNotBlank)
        val directory = selected?.let { if (evidenceSuffix == null) it else "$it-$evidenceSuffix" } ?: suite
        run = EvidenceRun(config(), directory, suite)
    }

    override fun testFailed(context: ExtensionContext, cause: Throwable) {
        successful = false
        if (priorFailure == null) priorFailure = cause
        run.event("test_failed", mapOf("test" to context.displayName, "error" to (cause.message ?: cause.javaClass.name)))
    }

    override fun testAborted(context: ExtensionContext, cause: Throwable?) {
        successful = false
        run.event("test_aborted", mapOf("test" to context.displayName, "error" to cause?.message))
    }

    override fun testSuccessful(context: ExtensionContext) {
        run.event("test_passed", mapOf("test" to context.displayName,
            "class" to context.requiredTestClass.simpleName, "method" to context.requiredTestMethod.name))
    }

    override fun afterAll(context: ExtensionContext) {
        val finalFailures = mutableListOf<Throwable>()
        fun finalizeStep(name: String, action: () -> Unit) {
            try { action() } catch (failure: Throwable) {
                successful = false
                finalFailures += failure
                run.recordFailure("final $name: ${failure.message ?: failure.javaClass.name}")
                runCatching { run.event("final_audit_failed", mapOf("audit" to name,
                    "error" to (failure.message ?: failure.javaClass.name))) }
            }
        }
        finalizeStep("fixture_result") {
            context.executionException.ifPresent { error ->
                successful = false
                if (priorFailure == null) priorFailure = error
                run.recordFailure("suite fixture: ${error.message ?: error.javaClass.name}")
                run.event("suite_fixture_failed", mapOf("error" to (error.message ?: error.javaClass.name)))
            }
        }
        finalizeStep("artifact_integrity") {
            val findings = run.integrity.audit(runtimeSupport = suite != "candidate")
            run.event("final_artifact_integrity", mapOf("count" to findings.size,
                "findings" to findings.map { mapOf("path" to it.path?.toString(),
                    "roles" to it.roles.map { role -> role.name }, "error" to it.error) }))
            check(findings.isEmpty()) { "final artifact integrity audit failed: $findings" }
        }
        finalizeStep("fixture_audit_logging") {
            val findings = run.auditLogging()
            run.event("final_fixture_audit_logging", mapOf("count" to findings.size, "findings" to findings))
            check(findings.isEmpty()) { "fixture audit logging integrity failed: $findings" }
        }
        finalizeStep("strict_logs") {
            val findings = LogPolicy.findings(collectLogs(run.directory))
            run.event(if (run.target != null) "target_log_findings" else "runtime_log_findings",
                mapOf("count" to findings.size,
                    "first" to findings.take(25).map { "${it.path}:${it.line}: ${it.text}" }))
            check(findings.isEmpty()) { "correctness log findings detected: ${findings.take(25)}" }
        }
        finalizeStep("startup_diagnostics") {
            val timings = LogPolicy.startupTimings(collectLogs(run.directory))
            run.event("startup_performance_diagnostics", mapOf(
                "verdict" to "diagnostic_only",
                "observation_count" to timings.size,
                "per_mod_max_seconds" to timings.groupBy { it.mod }.mapValues { (_, rows) -> rows.maxOf { it.seconds } },
                "note" to "Raw observations may repeat across aggregate/native logs; durations are not summed. " +
                    "Startup/join timeouts and explicit runtime performance assertions remain blocking.",
                "samples" to timings.take(25).map { timing -> mapOf(
                    "path" to timing.path.toString(), "line" to timing.line,
                    "mod" to timing.mod, "thread" to timing.thread,
                    "seconds" to timing.seconds, "text" to timing.text,
                ) },
            ))
        }
        finalizeStep("audited_log_hashes") {
            val logs = collectLogs(run.directory)
            run.event("final_audited_logs", mapOf("logs" to logs.map { path ->
                mapOf("path" to path.toString(), "sha256" to Hashes.sha256(path))
            }))
        }
        try { run.finish(successful) } catch (failure: Throwable) { finalFailures += failure }
        if (finalFailures.isNotEmpty()) {
            val original = priorFailure ?: context.executionException.orElse(null)
            if (original != null) finalFailures.forEach(original::addSuppressed)
            else throw AssertionError("${finalFailures.size} final runtime audit(s) failed", finalFailures.first()).also {
                finalFailures.drop(1).forEach(it::addSuppressed)
            }
        }
    }
}
