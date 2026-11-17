package com.bettercontent.tests.suite

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import com.bettercontent.tests.Hashes
import com.bettercontent.tests.collectDiagnosticEvents
import com.bettercontent.tests.diagnosticSignatures
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.file.Files
import java.nio.file.Path

class SuiteV2QualificationTest {
    private val mapper = jacksonObjectMapper()
    @TempDir lateinit var root: Path

    private fun logReview(): ObjectNode {
        val raw = root.resolve("server.log")
        Files.writeString(raw, "[12:00:00] [Server thread/WARN] sample warning\n")
        val signatures = diagnosticSignatures(collectDiagnosticEvents("server", Files.readAllLines(raw).asSequence()))
        val index = root.resolve("signatures.json")
        suiteMapper.writeValue(index.toFile(), mapOf("schema" to "bc.log_signatures.v1", "complete" to true,
            "unindexed_events" to 0, "raw_logs" to mapOf("server" to raw.toString()), "signatures" to signatures))
        return suiteMapper.valueToTree(mapOf("sources" to listOf(BoundFile(raw.toString(), Hashes.sha256(raw))),
            "signature_indexes" to listOf(BoundFile(index.toString(), Hashes.sha256(index))),
            "signatures" to listOf(mapOf("signature" to signatures.single().signature, "count" to 1,
                "owner" to "reviewer", "impact" to "none", "disposition" to "no-impact-reviewed"))))
    }

    @Test fun incompleteOrMissingLogSignaturePartitionCannotQualify() {
        val good = logReview()
        assertDoesNotThrow { validateV2LogSignatures(root, good) }
        val missing = good.deepCopy()
        (missing.path("sources") as com.fasterxml.jackson.databind.node.ArrayNode).add(
            suiteMapper.valueToTree(BoundFile(root.resolve("server.log").toString(), Hashes.sha256(root.resolve("server.log")))))
        assertThrows(IllegalArgumentException::class.java) { validateV2LogSignatures(root, missing) }
        val harmful = good.deepCopy()
        (harmful.path("signatures")[0] as ObjectNode).put("disposition", "unresolved")
        assertThrows(IllegalArgumentException::class.java) { validateV2LogSignatures(root, harmful) }
        val wrongCount = good.deepCopy()
        (wrongCount.path("signatures")[0] as ObjectNode).put("count", 2)
        assertThrows(IllegalArgumentException::class.java) { validateV2LogSignatures(root, wrongCount) }
        val alteredIndex = good.deepCopy()
        val indexFile = root.resolve("signatures.json")
        val alteredDoc = suiteMapper.readTree(indexFile.toFile()) as ObjectNode
        (alteredDoc.path("signatures")[0] as ObjectNode).put("headline", "fabricated")
        suiteMapper.writeValue(indexFile.toFile(), alteredDoc)
        (alteredIndex.path("signature_indexes")[0] as ObjectNode).put("sha256", Hashes.sha256(indexFile))
        assertThrows(IllegalArgumentException::class.java) { validateV2LogSignatures(root, alteredIndex) }
        val incomplete = good.deepCopy()
        val indexDoc = suiteMapper.readTree(indexFile.toFile()) as ObjectNode
        (indexDoc.path("signatures")[0] as ObjectNode).put("headline", "[12:00:00] [Server thread/WARN] sample warning")
        indexDoc.put("complete", false); indexDoc.put("unindexed_events", 1)
        suiteMapper.writeValue(indexFile.toFile(), indexDoc)
        (incomplete.path("signature_indexes")[0] as ObjectNode).put("sha256", Hashes.sha256(indexFile))
        assertThrows(IllegalArgumentException::class.java) { validateV2LogSignatures(root, incomplete) }
    }

    @Test fun nativeReplayRejectsFailedThenCompletedSwappedAndUnplannedActions() {
        val expected = listOf(PlannedNativeAction("case", "player.snapshot", mapper.readTree("""{"phase":"before"}"""), 200),
            PlannedNativeAction("case", "player.snapshot", mapper.readTree("""{"phase":"after"}"""), 200),
            PlannedNativeAction("case", "fixture.cleanup", mapper.readTree("{}"), 2400))
        val requests = expected.mapIndexed { index, step -> suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(
            NativeRequest("bc.playtest_action.v2", "run", step.caseId, "a".repeat(64), index.toLong() + 1,
                step.driver, step.maxTimeoutTicks, step.parameters)) }
        val observations = requests.flatMapIndexed { index, row ->
            listOf("started", "completed").mapIndexed { part, kind -> suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(
                NativeObservation("bc.playtest_observation.v2", "run", "case", "a".repeat(64),
                    index.toLong() * 2 + part + 1, index.toLong() + 1, row.path("driver").asText(),
                    "server", kind, 0, mapper.readTree("{}"))) }
        }
        assertDoesNotThrow { verifyV2NativeProtocol(requests, observations, expected) }
        val failedThenCompleted = observations.toMutableList()
        (failedThenCompleted[1].deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()).also { failed ->
            failed.put("kind", "failed"); failedThenCompleted[1] = failed
        }
        assertThrows(IllegalArgumentException::class.java) { verifyV2NativeProtocol(requests, failedThenCompleted, expected) }
        val replayedCompletion = observations[1].deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().apply { put("sequence", 3) }
        assertThrows(IllegalArgumentException::class.java) {
            verifyV2NativeProtocol(requests.take(1), listOf(observations[0], failedThenCompleted[1], replayedCompletion), expected.take(1))
        }
        assertThrows(IllegalArgumentException::class.java) { verifyV2NativeProtocol(requests.reversed(), observations, expected) }
        assertThrows(IllegalArgumentException::class.java) { verifyV2NativeProtocol(requests + requests.last(), observations, expected) }
        assertThrows(IllegalArgumentException::class.java) { verifyV2NativeProtocol(requests, observations + observations.last(), expected) }
    }

    @Test fun repeatedSnapshotActionsCannotReuseOrReorderCaseTerminals() {
        fun row(seq: Long) = mapper.readTree("""{"step":"snapshot","observation":{"request_sequence":$seq}}""")
        assertDoesNotThrow { requireCaseTerminalOrder(listOf(row(2), row(3), row(4)), listOf(2, 3, 4)) }
        assertThrows(IllegalArgumentException::class.java) {
            requireCaseTerminalOrder(listOf(row(4), row(3), row(2)), listOf(2, 3, 4))
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireCaseTerminalOrder(listOf(row(2), row(3), row(2)), listOf(2, 3, 4))
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireCaseTerminalOrder(listOf(row(2), row(4)), listOf(2, 3, 4))
        }
    }

    @Test fun visualAndRepositoryLogsCannotBeOmittedFromReviewedCorpus() {
        val review = logReview()
        for (name in listOf("visual-client.log", "repository-native.log", "repository-execution.log")) {
            val omitted = root.resolve(name)
            Files.writeString(omitted, "[12:00:00] [Server thread/ERROR] unreviewed execution output\n")
            assertThrows(IllegalArgumentException::class.java) {
                validateV2LogSignatures(root, review, setOf(omitted.toRealPath()))
            }
        }
    }

    @Test fun fixtureSuppliedFoodCannotBeItsOwnAcquisitionProof() {
        val acquired = case("acquire", listOf("test:berry"), emptyList())
        val consumed = case("eat", listOf("test:berry"), listOf("acquire"))
        val ledger = mapper.readTree("""[{"id":"test:berry","coverage":["acquire"]}]""")
        assertDoesNotThrow { validateDriverAcquisitionBoundary(consumed, setOf("test:berry"),
            mapOf("test:berry" to "acquire"), listOf(acquired, consumed), ledger) }
        assertThrows(IllegalArgumentException::class.java) { validateDriverAcquisitionBoundary(consumed,
            setOf("test:berry"), mapOf("test:berry" to "eat"), listOf(acquired, consumed), ledger) }
        assertThrows(IllegalArgumentException::class.java) { validateDriverAcquisitionBoundary(consumed,
            setOf("test:berry"), emptyMap(), listOf(acquired, consumed), ledger) }
    }

    private fun case(id: String, targets: List<String>, dependencies: List<String>) = SuiteCase(id,
        emptyList(), "better-content-modpack", "campaign", "server", listOf("$id-assertion"), targets,
        dependencies, 0, 60, listOf("pre"), listOf("act"), listOf("expect"), listOf("clean"),
        listOf("native-interaction"), emptyList(), CaseImplementation("authored", "fixture", "run"))

    @Test fun v1AndUnknownEvidenceCannotBeImplicitlyPromotedToV2() {
        for (type in listOf("evidence_index", "case_result", "campaign", "repository", "log_review")) {
            assertThrows(IllegalArgumentException::class.java) {
                requireV2ReceiptSchema(type, mapper.readTree("""{"schema":"bc.playtest_$type.v1"}"""))
            }
            assertThrows(IllegalArgumentException::class.java) {
                requireV2ReceiptSchema(type, mapper.readTree("""{"schema":"unknown"}"""))
            }
            assertDoesNotThrow {
                requireV2ReceiptSchema(type, mapper.readTree("""{"schema":"bc.playtest_$type.v2"}"""))
            }
        }
    }
}
