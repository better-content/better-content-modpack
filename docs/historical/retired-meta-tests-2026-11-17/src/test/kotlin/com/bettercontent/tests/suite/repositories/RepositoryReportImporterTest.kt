package com.bettercontent.tests.suite.repositories

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.suiteMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows

class RepositoryReportImporterTest {
    private fun fixture(xml: String): Triple<Path, Path, RepositoryReportExpectation> {
        val root = Files.createTempDirectory(Files.createDirectories(Path.of("build", "repository-report-fixtures")), "case-")
        val report = root.resolve("TEST-Native.xml")
        val sidecar = root.resolve("binding.json")
        Files.writeString(report, if ("timestamp=" in xml) xml else xml.replaceFirst("<testsuite ", "<testsuite timestamp=\"${Instant.now()}\" "))
        val expected = RepositoryReportExpectation("a".repeat(64), "better-spawns", "verifyFast", "unique-run-1",
            "b".repeat(64), "c".repeat(64), "d".repeat(64), "e".repeat(64), "f".repeat(64), "1".repeat(64),
            setOf("example.Native.ok"), Instant.now().minusSeconds(60), Hashes.sha256(report))
        val claim = mapOf("schema" to "bc.repository_report_binding.v1", "kind" to "junit",
            "reference_sha256" to expected.referenceSha256, "repository" to expected.repository,
            "task" to expected.task, "run_id" to expected.runId, "source_sha256" to expected.sourceSha256,
            "tests_sha256" to expected.testsSha256, "providers_sha256" to expected.providersSha256,
            "toolchain_sha256" to expected.toolchainSha256, "configuration_sha256" to expected.configurationSha256,
            "artifact_sha256" to expected.artifactSha256, "raw_sha256" to Hashes.sha256(report),
            "completed_at" to Instant.now().toString(), "test_ids" to listOf("example.Native.ok"))
        Files.writeString(sidecar, suiteMapper.writeValueAsString(claim))
        return Triple(report, sidecar, expected)
    }

    private val passing = """<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase classname="example.Native" name="ok"/></testsuite>"""

    @Test fun importsExactNonemptyRuntimeDiscoveryOnly() {
        val (report, sidecar, expected) = fixture(passing)
        val result = RepositoryReportImporter.importJUnit(report, sidecar, expected)
        assertEquals(setOf("example.Native.ok"), result.testIds)
    }

    @Test fun zeroTestsAndDiscoveredWrongMembershipFailClosed() {
        for (xml in listOf("<testsuite tests=\"0\" failures=\"0\" errors=\"0\" skipped=\"0\"/>",
            "<testsuite tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"><testcase classname=\"example.Native\" name=\"different\"/></testsuite>")) {
            val (report, sidecar, expected) = fixture(xml)
            assertThrows(IllegalArgumentException::class.java) { RepositoryReportImporter.importJUnit(report, sidecar, expected) }
        }
    }

    @Test fun failureAndSkipCannotBecomeRepositorySuccess() {
        for (tag in listOf("failure", "error", "skipped")) {
            val xml = "<testsuite tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"><testcase classname=\"example.Native\" name=\"ok\"><$tag/></testcase></testsuite>"
            val (report, sidecar, expected) = fixture(xml)
            assertThrows(IllegalArgumentException::class.java) { RepositoryReportImporter.importJUnit(report, sidecar, expected) }
        }
    }

    @Test fun localGradleXmlTimestampNeedsIndependentlyBoundTimezone() {
        val local = "<testsuite timestamp=\"${java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)}\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"><testcase classname=\"example.Native\" name=\"ok\"/></testsuite>"
        val (report, sidecar, expected) = fixture(local)
        assertThrows(IllegalArgumentException::class.java) { RepositoryReportImporter.importJUnit(report, sidecar, expected) }
        val claim = suiteMapper.readTree(sidecar.toFile()) as com.fasterxml.jackson.databind.node.ObjectNode
        claim.put("report_timezone", "Z")
        Files.writeString(sidecar, suiteMapper.writeValueAsString(claim))
        assertEquals(setOf("example.Native.ok"), RepositoryReportImporter.importJUnit(report, sidecar, expected).testIds)
    }

    @Test fun aggregateXmlCountersCannotContradictExactDiscoveredMembership() {
        for (attributes in listOf("tests=\"2\" failures=\"0\" errors=\"0\" skipped=\"0\"",
            "tests=\"1\" failures=\"1\" errors=\"0\" skipped=\"0\"",
            "tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"1\"")) {
            val xml = "<testsuites $attributes><testsuite timestamp=\"${Instant.now()}\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"><testcase classname=\"example.Native\" name=\"ok\"/></testsuite></testsuites>"
            val (report, sidecar, expected) = fixture(xml)
            assertThrows(IllegalArgumentException::class.java) { RepositoryReportImporter.importJUnit(report, sidecar, expected) }
        }
    }

    @Test fun staleReplayOrMutatedRawBytesAreRejected() {
        val (report, sidecar, expected) = fixture(passing)
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryReportImporter.importJUnit(report, sidecar, expected.copy(runId = "different-run"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryReportImporter.importJUnit(report, sidecar, expected.copy(providersSha256 = "2".repeat(64)))
        }
        Files.writeString(report, passing + "<!--changed-->")
        assertThrows(IllegalArgumentException::class.java) { RepositoryReportImporter.importJUnit(report, sidecar, expected) }
    }

    @Test fun nonfatalSeverityRemainsForMandatoryImpactReviewRatherThanImporterBlanketFailure() {
        val (report, sidecar, expected) = fixture(passing)
        val log = sidecar.parent.resolve("review-required.log")
        Files.writeString(log, "[12:00:00] [Server thread/INFO] GameTest unique-run-1 discovered example.Native.ok\n" +
            "[12:00:01] [Server thread/ERROR] Optional integration unavailable; test process completed\n")
        val claim = suiteMapper.readTree(sidecar.toFile()) as com.fasterxml.jackson.databind.node.ObjectNode
        claim.put("kind", "forge-gametest")
        claim.set<com.fasterxml.jackson.databind.JsonNode>("native_log", suiteMapper.valueToTree(mapOf(
            "path" to log.toAbsolutePath().toString(), "sha256" to Hashes.sha256(log))))
        Files.writeString(sidecar, suiteMapper.writeValueAsString(claim))
        // An observation is not a qualified repository result; qualifier must review this exact log.
        assertEquals("forge-gametest", RepositoryReportImporter.importNative(report, sidecar, expected).kind)
    }

    @Test fun aggregateReportSidecarMustVersionAndBindReviewedInvocationSeparatelyFromLeaf() {
        val (report, sidecar, expected) = fixture(passing)
        val aggregate = expected.copy(invocationTask = "verifyFast", task = "test")
        assertThrows(IllegalArgumentException::class.java) { RepositoryReportImporter.importJUnit(report, sidecar, aggregate) }
        val claim = suiteMapper.readTree(sidecar.toFile()) as com.fasterxml.jackson.databind.node.ObjectNode
        claim.put("schema", "bc.repository_report_binding.v2")
        claim.put("invocation_task", "verifyFast")
        claim.put("task", "test")
        Files.writeString(sidecar, suiteMapper.writeValueAsString(claim))
        assertEquals(setOf("example.Native.ok"), RepositoryReportImporter.importJUnit(report, sidecar, aggregate).testIds)
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryReportImporter.importJUnit(report, sidecar, aggregate.copy(invocationTask = "differentRoot"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryReportImporter.importJUnit(report, sidecar, aggregate.copy(task = "differentLeaf"))
        }
    }
    @Test fun v3PackPairSidecarCannotCollapseClientAndServerToOneJarOrClaimRuntime() {
        val (report, sidecar, original) = fixture(passing)
        val used = sidecar.parent.resolve("used.json")
        Files.writeString(used, "source-only use claim")
        val pair = RepositoryArtifactBinding("pack-zip-pair", clientZip =
            com.bettercontent.tests.suite.BoundFile("candidate/client.zip", "a".repeat(64)), serverZip =
            com.bettercontent.tests.suite.BoundFile("candidate/server.zip", "b".repeat(64)))
        val expected = original.copy(artifactSha256 = null, invocationTask = "test", artifactBinding = pair,
            executionContext = "source-only", inputUseSha256 = Hashes.sha256(used))
        val claim = suiteMapper.readTree(sidecar.toFile()) as com.fasterxml.jackson.databind.node.ObjectNode
        claim.put("schema", "bc.repository_report_binding.v3")
        claim.put("invocation_task", "test")
        claim.remove("artifact_sha256")
        claim.put("execution_context", "source-only")
        claim.set<com.fasterxml.jackson.databind.JsonNode>("artifact_binding", suiteMapper.valueToTree(pair))
        claim.set<com.fasterxml.jackson.databind.JsonNode>("observed_input_use", suiteMapper.valueToTree(mapOf(
            "path" to used.toString(), "sha256" to Hashes.sha256(used))))
        Files.writeString(sidecar, suiteMapper.writeValueAsString(claim))
        assertEquals("junit", RepositoryReportImporter.importJUnit(report, sidecar, expected).kind)
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryReportImporter.importJUnit(report, sidecar, expected.copy(artifactBinding = pair.copy(serverZip = pair.clientZip)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryReportImporter.importJUnit(report, sidecar, expected.copy(executionContext = "packaged-runtime"))
        }
        Files.writeString(used, "tampered use")
        assertThrows(IllegalArgumentException::class.java) { RepositoryReportImporter.importJUnit(report, sidecar, expected) }
    }
    @Test fun nativeRequiresBoundActualLogAndNativeKind() {
        val (report, sidecar, expected) = fixture(passing)
        assertThrows(IllegalArgumentException::class.java) { RepositoryReportImporter.importNative(report, sidecar, expected) }
        val log = sidecar.parent.resolve("latest.log")
        Files.writeString(log, "[12:00:00] [Server thread/INFO] GameTest unique-run-1 discovered example.Native.ok\n")
        val claim = suiteMapper.readTree(sidecar.toFile()) as com.fasterxml.jackson.databind.node.ObjectNode
        claim.put("kind", "forge-gametest")
        claim.set<com.fasterxml.jackson.databind.JsonNode>("native_log", suiteMapper.valueToTree(mapOf(
            "path" to log.toAbsolutePath().toString(), "sha256" to Hashes.sha256(log))))
        Files.writeString(sidecar, suiteMapper.writeValueAsString(claim))
        assertEquals("forge-gametest", RepositoryReportImporter.importNative(report, sidecar, expected).kind)
        Files.writeString(log, "[12:00:00] [Server thread/ERROR] GameTest unique-run-1 failed\n")
        assertThrows(IllegalArgumentException::class.java) { RepositoryReportImporter.importNative(report, sidecar, expected) }
    }
}
