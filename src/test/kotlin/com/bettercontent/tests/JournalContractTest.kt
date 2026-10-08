package com.bettercontent.tests

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.ByteArrayOutputStream

@Tag("fast")
class JournalContractTest {
    @Test fun persistenceObservationIsNotShortCircuitedByTheStillMandatoryLogAudit() {
        val source = Files.readString(java.nio.file.Path.of("src/test/kotlin/com/bettercontent/tests/MultiplayerRuntimeTest.kt"))
        val method = source.substringAfter("fun journalInventoryContractPasses()").substringBefore("private fun journalCheckpoint")
        org.junit.jupiter.api.Assertions.assertTrue(method.indexOf("journalDedicatedPersistence()") >= 0)
        org.junit.jupiter.api.Assertions.assertTrue(method.indexOf("server.auditLogs()") > method.indexOf("journalDedicatedPersistence()"))
    }

    private val mapper = jacksonObjectMapper()
    private val identity = JournalContractIdentity("20261007T230000Z-1", "SmokeClient1", "sentinel",
        "a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64))
    private val expected = setOf(JournalScenario("output", "server"), JournalScenario("dispatch", "client"))
    private fun valid(): ObjectNode = mapper.valueToTree(mapOf(
        "schema" to "bc.journal.contract.v1", "run_id" to identity.runId, "player" to identity.player,
        "mode" to identity.mode, "candidate_client_sha256" to identity.clientSha256,
        "candidate_server_sha256" to identity.serverSha256, "journal_sha256" to identity.journalSha256,
        "support_sha256" to identity.supportSha256, "status" to "passed", "cleanup" to "passed",
        "expected_total" to 2, "total" to 2, "passed" to 2,
        "rows" to expected.map { mapOf("id" to it.id, "layer" to it.layer, "status" to "passed", "detail" to "observed") },
    ))
    private fun reject(report: ObjectNode) {
        assertThrows(Exception::class.java) { JournalContractValidator.validate(report.toString(), identity, expected) }
    }

    @Test fun acceptsExactCompleteReport() {
        assertEquals("passed", JournalContractValidator.validate(valid().toString(), identity, expected).path("status").asText())
    }
    @Test fun rejectsEveryMissingIdentityAndMismatchedIdentity() {
        listOf("schema", "run_id", "player", "mode", "candidate_client_sha256", "candidate_server_sha256",
            "journal_sha256", "support_sha256").forEach { field ->
            reject(valid().also { it.remove(field) })
            reject(valid().also { it.put(field, "wrong") })
            reject(valid().also { it.put(field, 1) })
        }
    }
    @Test fun rejectsFailedPendingAndMalformedRows() {
        listOf("failed", "pending", "skipped").forEach { status ->
            reject(valid().also { (it.path("rows")[0] as ObjectNode).put("status", status) })
        }
        listOf("id", "layer", "status", "detail").forEach { field ->
            reject(valid().also { (it.path("rows")[0] as ObjectNode).remove(field) })
        }
        reject(valid().also { (it.path("rows")[0] as ObjectNode).put("id", "unknown") })
        reject(valid().also { (it.path("rows")[0] as ObjectNode).put("layer", "client") })
        reject(valid().also { (it.path("rows")[0] as ObjectNode).put("detail", " ") })
        reject(valid().also { it.withArray("rows").set(1, it.path("rows")[0]) })
        reject(valid().also { it.withArray("rows").remove(1) })
        reject(valid().also { it.withArray("rows").add(it.path("rows")[0]) })
        reject(valid().also { it.put("rows", "passed") })
    }
    @Test fun rejectsCountsStatusAndCleanup() {
        listOf("expected_total", "passed", "total").forEach { field ->
            reject(valid().also { it.remove(field) })
            reject(valid().also { it.put(field, 1) })
            reject(valid().also { it.put(field, "2") })
            reject(valid().also { it.put(field, 2.0) })
        }
        listOf("status", "cleanup").forEach { field ->
            reject(valid().also { it.remove(field) })
            reject(valid().also { it.put(field, "failed") })
            reject(valid().also { it.put(field, true) })
        }
    }
    @Test fun rejectsDuplicateJsonKeysTrailingTextAndStringOnlyMarkers() {
        listOf("journal-contract-report passed", "{", "[]", valid().toString() + " {}",
            valid().toString().replaceFirst("{", "{\"status\":\"failed\",")).forEach { json ->
            assertThrows(Exception::class.java) { JournalContractValidator.validate(json, identity, expected) }
        }
    }
    @Test fun manifestIsStrictAndFullIncludesSentinel() {
        val json = """{"schema":"bc.journal.manifest.v1","sentinel":[{"id":"output","layer":"server"}],"full":[{"id":"output","layer":"server"},{"id":"save","layer":"persistence"}]}"""
        assertEquals(1, JournalContractValidator.manifest(json, "sentinel").size)
        assertEquals(2, JournalContractValidator.manifest(json, "full").size)
        listOf("{}", "[]", json.replace("persistence", "unknown"), json.replace("save", "output"),
            json.replace("\"full\"", "\"other\""), json.replace("\"id\":\"output\"", "\"id\":\"\""),
            """{"schema":"bc.journal.manifest.v1","sentinel":[],"full":[]}""",
            """{"schema":"bc.journal.manifest.v1","sentinel":[{"id":"a","layer":"server"}],"full":[{"id":"b","layer":"server"}]}""").forEach {
            assertThrows(Exception::class.java) { JournalContractValidator.manifest(it, "full") }
        }
    }

    @Test fun checkpointRequiresFreshIdentityOperationSuccessAndDetail() {
        fun report(): ObjectNode = mapper.valueToTree(mapOf("schema" to "bc.journal.checkpoint.v1",
            "run_id" to identity.runId, "player" to identity.player, "operation" to "verify",
            "status" to "passed", "detail" to "matched persisted bag, hotbar, NBT and backpack identity"))
        assertEquals("passed", JournalContractValidator.validateCheckpoint(report().toString(), identity, "verify").path("status").asText())
        listOf("schema", "run_id", "player", "operation", "status", "detail").forEach { field ->
            assertThrows(Exception::class.java) {
                JournalContractValidator.validateCheckpoint(report().also { it.remove(field) }.toString(), identity, "verify")
            }
            assertThrows(Exception::class.java) {
                JournalContractValidator.validateCheckpoint(report().also { it.put(field, 1) }.toString(), identity, "verify")
            }
            assertThrows(Exception::class.java) {
                JournalContractValidator.validateCheckpoint(report().also { it.put(field, "") }.toString(), identity, "verify")
            }
        }
        listOf("schema", "run_id", "player", "operation", "status").forEach { field ->
            assertThrows(Exception::class.java) {
                JournalContractValidator.validateCheckpoint(report().also { it.put(field, "wrong") }.toString(), identity, "verify")
            }
        }
        assertThrows(Exception::class.java) { JournalContractValidator.validateCheckpoint(report().toString(), identity, "save") }
        assertThrows(Exception::class.java) { JournalContractValidator.validateCheckpoint(report().toString(), identity, "other") }
        listOf("journal-checkpoint-report passed", report().toString() + " {}",
            report().toString().replaceFirst("{", "{\"status\":\"failed\",")).forEach { json ->
            assertThrows(Exception::class.java) { JournalContractValidator.validateCheckpoint(json, identity, "verify") }
        }
    }

    @Test fun persistenceHarnessDoesNotRetryCheckpointAndKeepsBridgeIdentityOnReconnect() {
        val root = TestConfig.load().root
        val multiplayer = Files.readString(root.resolve("src/test/kotlin/com/bettercontent/tests/MultiplayerRuntimeTest.kt"))
        val checkpoint = multiplayer.substringAfter("private fun journalCheckpoint(").substringBefore("private fun journalDedicatedPersistence()")
        org.junit.jupiter.api.Assertions.assertFalse("retryInterval" in checkpoint)
        val persistence = multiplayer.substringAfter("private fun journalDedicatedPersistence()").substringBefore("private fun prepareTargetJoin")
        org.junit.jupiter.api.Assertions.assertTrue("journalCheckpoint(\"save\", \"initial\")" in persistence)
        org.junit.jupiter.api.Assertions.assertTrue("journalCheckpoint(\"verify\", \"client-reconnect\")" in persistence)
        org.junit.jupiter.api.Assertions.assertTrue("journalCheckpoint(\"verify\", \"server-restart\")" in persistence)
        org.junit.jupiter.api.Assertions.assertTrue("server.stopGracefully()" in persistence && "server.restart()" in persistence)
        val fixtures = Files.readString(root.resolve("src/test/kotlin/com/bettercontent/tests/RuntimeFixtures.kt"))
        org.junit.jupiter.api.Assertions.assertTrue("priorJoinCount + 1" in fixtures)
        org.junit.jupiter.api.Assertions.assertTrue("journalSupport?.properties.orEmpty()" in fixtures)
    }

    @Test fun fixturePolicyAddsOnlySupportClassificationWithoutWeakeningEnforcement() {
        val source = Files.readString(TestConfig.load().root.resolve("kubejs/config/crafting_policy.json"))
        val original = mapper.readTree(source)
        val patched = mapper.readTree(JournalFixturePolicy.classify(source)) as ObjectNode
        val supplement = patched.path("namespaces").path(JournalFixturePolicy.NAMESPACE)
        assertEquals(mapper.readTree("""{"primary_role":"infrastructure","support_state":"not_applicable"}"""), supplement)
        assertEquals("blocking", patched.path("enforcement").path("unknown_loaded_namespaces").asText())
        (patched.path("namespaces") as ObjectNode).remove(JournalFixturePolicy.NAMESPACE)
        assertEquals(original, patched)
        org.junit.jupiter.api.Assertions.assertFalse(original.path("namespaces").has(JournalFixturePolicy.NAMESPACE))
        assertThrows(Exception::class.java) { JournalFixturePolicy.classify(source.replace("\"blocking\"", "\"report\"")) }
        assertThrows(Exception::class.java) { JournalFixturePolicy.classify(JournalFixturePolicy.classify(source)) }
        assertThrows(Exception::class.java) { JournalFixturePolicy.classify("{}") }
        assertThrows(Exception::class.java) { JournalFixturePolicy.classify(source + " {}") }
    }

    @Test fun integratedPersistenceUsesSameSupplementAcrossWorldSaveAndReopen() {
        val root = TestConfig.load().root
        val singleplayer = Files.readString(root.resolve("src/test/kotlin/com/bettercontent/tests/SingleplayerRuntimeTest.kt"))
        org.junit.jupiter.api.Assertions.assertTrue("enableJournalContract = true" in singleplayer)
        org.junit.jupiter.api.Assertions.assertTrue("journalContractSupport = requireNotNull(first.journalSupport)" in singleplayer)
        org.junit.jupiter.api.Assertions.assertTrue("first.waitJournalCheckpoint(\"save\")" in singleplayer)
        org.junit.jupiter.api.Assertions.assertTrue("reopened.waitJournalCheckpoint(\"verify\")" in singleplayer)
        val fixtures = Files.readString(root.resolve("src/test/kotlin/com/bettercontent/tests/RuntimeFixtures.kt"))
        org.junit.jupiter.api.Assertions.assertTrue("-Dbc.journal.contract.singleplayer=\$mode" in fixtures)
        org.junit.jupiter.api.Assertions.assertTrue("log.readText().drop(quickPlayLogOffset)" in fixtures)
        val install = Files.readString(root.resolve("src/test/kotlin/com/bettercontent/tests/JournalFixtureSupport.kt"))
        org.junit.jupiter.api.Assertions.assertTrue("root.toAbsolutePath().normalize().startsWith(evidence.fixture.toAbsolutePath().normalize())" in install)
        org.junit.jupiter.api.Assertions.assertTrue("JournalFixturePolicy.classify(original)" in install)
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
        } }
    }.toByteArray()

    @Test fun excludesNamedRenamedAndManifestSupportFromCandidates() {
        val dir = Files.createTempDirectory(TestConfig.load().root.resolve("build/tmp/tests"), "journal-exclusion-")
        try {
            val support = zip(mapOf("META-INF/mods.toml" to "modId=\"better_journal_test_support\"".toByteArray()))
            val invalid = listOf(
                mapOf("overrides/mods/better-journal-test-support.jar" to support),
                mapOf("server/mods/renamed.jar" to support),
                mapOf("overrides/mods/support.pw.toml" to "name = 'better_journal_test_support'".toByteArray()),
            )
            invalid.forEachIndexed { i, entries ->
                val path = dir.resolve("bad$i.zip"); Files.write(path, zip(entries))
                assertThrows(IllegalArgumentException::class.java) { JournalCandidateExclusion.validate(path) }
            }
            val path = dir.resolve("good.zip")
            Files.write(path, zip(mapOf("mods/journal.jar" to zip(mapOf("META-INF/mods.toml" to "modId=\"better_journal_inventory\"".toByteArray())))))
            JournalCandidateExclusion.validate(path)
        } finally {
            Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
}
