package com.bettercontent.tests.suite.repositories

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Expected identity is supplied by an independently reviewed reference, never copied from the report sidecar. */
internal data class RepositoryReportExpectation(
    val referenceSha256: String, val repository: String, val task: String, val runId: String,
    val sourceSha256: String, val testsSha256: String, val providersSha256: String,
    val toolchainSha256: String, val configurationSha256: String, val artifactSha256: String?,
    val testIds: Set<String>, val startedAfter: Instant, val rawSha256: String,
    val reportZone: ZoneId = ZoneOffset.UTC, val invocationTask: String? = null,
    val artifactBinding: RepositoryArtifactBinding? = null, val executionContext: String = "source-only",
    val inputUseSha256: String? = null
)

internal data class RepositoryReportObservation(val repository: String, val task: String, val runId: String,
    val testIds: Set<String>, val rawSha256: String, val kind: String)

/**
 * Sidecars are claims about provenance, not proof of execution. Only a matching raw XML report with
 * nonzero exact discovered membership and no failures/errors/skips yields an observation. This is
 * intentionally not a repository `passed` receipt or a gameplay-promise assertion.
 */
internal object RepositoryReportImporter {
    fun importJUnit(report: Path, sidecar: Path, expected: RepositoryReportExpectation): RepositoryReportObservation =
        importXml(report, sidecar, expected, "junit")

    fun importNative(report: Path, sidecar: Path, expected: RepositoryReportExpectation): RepositoryReportObservation =
        importXml(report, sidecar, expected, "forge-gametest")

    private fun importXml(report: Path, sidecar: Path, expected: RepositoryReportExpectation, kind: String): RepositoryReportObservation {
        val sha = Regex("[0-9a-f]{64}")
        require(listOf(expected.referenceSha256, expected.sourceSha256, expected.testsSha256,
            expected.providersSha256, expected.toolchainSha256, expected.configurationSha256,
            expected.rawSha256).all(sha::matches) &&
            (when (val binding = expected.artifactBinding) {
                null -> expected.artifactSha256?.let(sha::matches) == true
                else -> when (binding.kind) {
                    "pack-zip-pair" -> expected.artifactSha256 == null && binding.jar == null &&
                        binding.clientZip?.sha256 != binding.serverZip?.sha256 &&
                        listOf(binding.clientZip?.sha256, binding.serverZip?.sha256).all { it != null && sha.matches(it) }
                    "mod-jar" -> expected.artifactSha256?.let(sha::matches) == true &&
                        binding.jar?.sha256 == expected.artifactSha256 &&
                        binding.clientZip == null && binding.serverZip == null
                    else -> false
                }
            })) {
            "unbound or missing candidate/source/test/provider/toolchain/configuration/artifact fingerprint"
        }
        require(expected.repository.isNotBlank() && expected.task.isNotBlank() && expected.runId.isNotBlank() && expected.testIds.isNotEmpty())
        require(Files.isRegularFile(report) && Files.isRegularFile(sidecar) && Files.size(report) in 1L..10_000_000L) { "missing or unbounded raw report" }
        require(Hashes.sha256(report) == expected.rawSha256) { "raw report not independently bound to this run" }
        val claim = suiteMapper.readTree(sidecar.toFile())
        val schema = when { expected.artifactBinding != null -> "bc.repository_report_binding.v3"
            expected.invocationTask != null -> "bc.repository_report_binding.v2"
            else -> "bc.repository_report_binding.v1" }
        require(claim.path("schema").asText() == schema && claim.path("kind").asText() == kind)
        if (expected.artifactBinding != null) {
            require(expected.executionContext == "source-only" &&
                claim.path("execution_context").asText() == "source-only" &&
                claim.path("artifact_binding") == suiteMapper.valueToTree<JsonNode>(expected.artifactBinding)) {
                "v3 mod JAR/pack ZIP pair identity or source-only context differs"
            }
            val used = claim.path("observed_input_use")
            val useFile = runCatching { Path.of(used.path("path").asText()).toRealPath() }.getOrNull()
            require(expected.inputUseSha256?.let(sha::matches) == true &&
                used.path("sha256").asText() == expected.inputUseSha256 &&
                useFile != null && useFile.startsWith(sidecar.toAbsolutePath().parent.toRealPath()) &&
                Files.isRegularFile(useFile, java.nio.file.LinkOption.NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(useFile) && Files.size(useFile) in 1L..1_048_576L &&
                Hashes.sha256(useFile) == expected.inputUseSha256) { "v3 bound actual-use observation missing or replaced" }
        }
        if (expected.invocationTask != null) require(claim.path("invocation_task").asText() == expected.invocationTask &&
            claim.path("invocation_task").asText().isNotBlank()) { "aggregate invocation task not independently bound" }
        val matched = mapOf("reference_sha256" to expected.referenceSha256, "repository" to expected.repository,
            "task" to expected.task, "run_id" to expected.runId, "source_sha256" to expected.sourceSha256,
            "tests_sha256" to expected.testsSha256, "providers_sha256" to expected.providersSha256,
            "toolchain_sha256" to expected.toolchainSha256, "configuration_sha256" to expected.configurationSha256,
            "raw_sha256" to Hashes.sha256(report)) +
            (if (expected.artifactSha256 == null) emptyMap() else mapOf("artifact_sha256" to expected.artifactSha256))
        matched.forEach { (field, value) -> require(claim.path(field).asText() == value) { "stale or replayed report: $field" } }
        val time = Instant.parse(claim.path("completed_at").asText())
        require(!time.isBefore(expected.startedAfter) && !time.isAfter(Instant.now())) { "report predates binding or lies in future" }
        require(claim.path("test_ids").isArray && claim.path("test_ids").map { it.asText() }.toSet() == expected.testIds &&
            claim.path("test_ids").size() == expected.testIds.size) { "sidecar claims wrong/duplicate tests" }
        if (kind == "forge-gametest") {
            val log = claim.path("native_log")
            require(log.path("path").asText().isNotBlank() && sha.matches(log.path("sha256").asText())) { "native log binding missing" }
            val logFile = Path.of(log.path("path").asText()).toRealPath()
            require(logFile.startsWith(sidecar.toAbsolutePath().parent.toRealPath()) && Files.size(logFile) in 1L..10_000_000L &&
                Hashes.sha256(logFile) == log.path("sha256").asText()) { "native log was replaced, oversized or escaped evidence directory" }
            val text = Files.readString(logFile)
            require(text.contains("GameTest") && text.contains(expected.runId)) { "missing run-bound native discovery" }
            // This importer establishes report membership, not runtime health. Severity alone
            // cannot veto or approve an observation: the qualifier mandates an impact review
            // of this exact log, including nonfatal ERROR records and fatal-operation evidence.
        }
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.setXIncludeAware(false)
        factory.isExpandEntityReferences = false
        val xml = Files.newInputStream(report).use { factory.newDocumentBuilder().parse(it) }
        val root = xml.documentElement
        require(root.tagName in setOf("testsuite", "testsuites")) { "not a JUnit XML report" }
        val suites = xml.getElementsByTagName("testsuite")
        require(suites.length > 0) { "zero discovered suites" }
        val cases = xml.getElementsByTagName("testcase")
        val ids = (0 until cases.length).map { index ->
            val node = cases.item(index) as Element
            require(node.getElementsByTagName("failure").length == 0 &&
                node.getElementsByTagName("error").length == 0 &&
                node.getElementsByTagName("skipped").length == 0) { "failed/error/skipped test" }
            require(node.getAttribute("classname").isNotBlank() && node.getAttribute("name").isNotBlank())
            "${node.getAttribute("classname")}.${node.getAttribute("name")}"
        }
        require(ids.isNotEmpty() && ids.size == ids.distinct().size && ids.toSet() == expected.testIds) { "missing/duplicate/unexpected runtime tests" }
        if (root.tagName == "testsuites") {
            val count = root.getAttribute("tests").takeIf { it.isNotBlank() }
            require(count == null || count.toIntOrNull() == ids.size) { "aggregate report test count differs from exact discovered cases" }
            for (failure in listOf("failures", "errors", "skipped")) {
                val value = root.getAttribute(failure).takeIf { it.isNotBlank() }
                require(value == null || value.toIntOrNull() == 0) { "aggregate report declares $failure" }
            }
        }
        for (index in 0 until suites.length) {
            val suite = suites.item(index) as Element
            val tests = suite.getAttribute("tests").toIntOrNull()
            val stamp = suite.getAttribute("timestamp")
            val suiteTime = runCatching { Instant.parse(stamp) }.getOrElse {
                require(claim.path("report_timezone").asText() == expected.reportZone.id) { "unbound local XML timestamp timezone" }
                LocalDateTime.parse(stamp).atZone(expected.reportZone).toInstant()
            }
            require(!suiteTime.isBefore(expected.startedAfter) && !suiteTime.isAfter(time)) { "replayed or future XML suite" }
            require(tests != null && tests > 0 && suite.getAttribute("failures").toIntOrNull() == 0 &&
                suite.getAttribute("errors").toIntOrNull() == 0 && suite.getAttribute("skipped").toIntOrNull() == 0) { "zero/failed/skipped suite" }
            require(suite.getElementsByTagName("testcase").length == tests) { "suite count disagrees with discovered cases" }
        }
        return RepositoryReportObservation(expected.repository, expected.task, expected.runId, ids.toSet(), Hashes.sha256(report), kind)
    }
}
