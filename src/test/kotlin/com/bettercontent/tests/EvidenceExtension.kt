package com.bettercontent.tests

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher

class EvidenceExtension(private val suite: String) : BeforeAllCallback, AfterAllCallback, TestWatcher {
    lateinit var run: EvidenceRun
        private set
    private var successful = true

    override fun beforeAll(context: ExtensionContext) {
        run = EvidenceRun(TestConfig.load(), System.getenv("BC_TEST_EVIDENCE_SUITE")?.takeIf(String::isNotBlank) ?: suite, suite)
    }

    override fun testFailed(context: ExtensionContext, cause: Throwable) {
        successful = false
        run.event("test_failed", mapOf("test" to context.displayName, "error" to (cause.message ?: cause.javaClass.name)))
    }

    override fun testAborted(context: ExtensionContext, cause: Throwable?) {
        successful = false
        run.event("test_aborted", mapOf("test" to context.displayName, "error" to cause?.message))
    }

    override fun testSuccessful(context: ExtensionContext) {
        run.event("test_passed", mapOf("test" to context.displayName))
    }

    override fun afterAll(context: ExtensionContext) {
        context.executionException.ifPresent { error ->
            successful = false
            run.event("suite_fixture_failed", mapOf("error" to (error.message ?: error.javaClass.name)))
        }
        if (run.target != null) {
            val findings = LogPolicy.findings(collectLogs(run.directory))
            run.event("target_log_findings", mapOf("count" to findings.size,
                "first" to findings.take(5).map { "${it.path}:${it.line}: ${it.text}" }))
            if (findings.isNotEmpty()) successful = false
        }
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
        run.finish(successful)
    }
}
