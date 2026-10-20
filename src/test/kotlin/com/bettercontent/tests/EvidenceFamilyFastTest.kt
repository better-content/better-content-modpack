package com.bettercontent.tests

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.UUID

/** Inert ownership/fixture fault laws. No Minecraft, synthetic input or native acceptance. */
@Tag("fast")
class EvidenceFamilyFastTest {
    private val mapper = jacksonObjectMapper()
    private fun parent(): EvidenceRun {
        val config = TestConfig.load()
        return EvidenceRun(config.copy(runId = "inert-family-${UUID.randomUUID()}",
            runRoot = config.root.resolve("build/inert-family-evidence"), handoff = null), "inert")
    }

    @Test fun familiesShareAuditAndIntegrityButNeverSessionPortOrWorldPaths() {
        val parent = parent()
        val first = parent.fixtureFamily("first")
        val firstPort = first.nativePort()
        assertSame(parent.integrity, first.integrity)
        first.event("inert_fixture_marker", mapOf("note" to "unit fixture, not native evidence"))
        assertThrows(IllegalStateException::class.java) { parent.fixtureFamily("overlapping") }
        assertTrue(parent.auditLogging().any { it.contains("not completely cleaned") })
        first.close()
        val second = parent.fixtureFamily("second")
        assertNotEquals(first.fixtureSession, second.fixtureSession)
        assertNotEquals(firstPort, second.nativePort())
        assertNotEquals(first.fixture, second.fixture)
        assertNotEquals(first.directory, second.directory)
        assertSame(parent.integrity, second.integrity)
        second.close()
        assertThrows(IllegalArgumentException::class.java) { parent.fixtureFamily("../escape") }
        assertThrows(IllegalArgumentException::class.java) { parent.fixtureFamily("first") }
        assertTrue(parent.auditLogging().isEmpty())
        assertFalse(Files.exists(first.directory.resolve("events.jsonl")))
        val rows = Files.readAllLines(parent.directory.resolve("events.jsonl")).map(mapper::readTree)
        assertTrue(rows.any { it.path("type").asText() == "inert_fixture_marker" && it.path("fixture_family").asText() == "first" })
        assertEquals(2, rows.count { it.path("type").asText() == "fixture_family_cleanup_finished" })
    }

    @Test fun partialStartupClosesEveryAlreadyOwnedResourceInReverseOrderAndRetainsFiles() {
        val parent = parent(); val child = parent.fixtureFamily("partial-startup")
        val closed = mutableListOf<Int>()
        val retained = child.fixture.resolve("retained-inert-sentinel.txt")
        Files.writeString(retained, "INERT UNIT FIXTURE, NOT A WORLD OR ACCEPTANCE")
        try {
            child.own(AutoCloseable { closed += 1 })
            child.own(AutoCloseable { closed += 2 })
            throw IllegalStateException("inert partial startup failure")
        } catch (expected: IllegalStateException) {
            assertEquals("inert partial startup failure", expected.message)
        } finally { child.close() }
        assertEquals(listOf(2, 1), closed)
        assertTrue(Files.isRegularFile(retained))
        assertTrue(parent.auditLogging().isEmpty())
        assertThrows(IllegalStateException::class.java) { child.own(AutoCloseable {}) }
        assertThrows(IllegalStateException::class.java) { child.finish(true) }
    }

    @Test fun failedCleanupIsStickyAndCannotPermitAnotherFamilyOrHideRemainingClosure() {
        val parent = parent(); val child = parent.fixtureFamily("failed-cleanup")
        var otherClosed = false
        child.own(AutoCloseable { otherClosed = true })
        child.own(AutoCloseable { throw IllegalStateException("inert cleanup fault") })
        val first = assertThrows(IllegalStateException::class.java) { child.close() }
        assertTrue(otherClosed)
        assertSame(first, assertThrows(IllegalStateException::class.java) { child.close() })
        assertThrows(IllegalStateException::class.java) { parent.fixtureFamily("must-not-start") }
        assertTrue(parent.auditLogging().any { it.contains("not completely cleaned") })
    }

    @Test fun activeDeferredRequirementWithoutNativeWitnessFailsAndInactiveIsNotPass() {
        val config = mapper.createObjectNode().put("common_spec_loaded", true).put("async_crater", false)
            .put("direct_chunk_writes", true).put("crater_destruction", true).put("tnt_source_mode", "DEFAULT")
            .put("deferred_required", false).put("config_sha256", "a".repeat(64)).put("mod_sha256", "b".repeat(64))
        val result = mapper.createObjectNode().set<ObjectNode>("effective_eo_configuration", config)
            .put("deferred_applicability", "INACTIVE_CONDITION").put("native_deferred_path_exercised", false)
        assertEquals("INACTIVE_CONDITION", NativeInteractionFamilyRuntimeTest.deferredVerdict(result))
        config.put("async_crater", true).put("deferred_required", true); result.put("deferred_applicability", "REQUIRED")
        assertThrows(IllegalArgumentException::class.java) { NativeInteractionFamilyRuntimeTest.deferredVerdict(result) }
        result.put("native_deferred_path_exercised", true)
        assertEquals("REQUIRED", NativeInteractionFamilyRuntimeTest.deferredVerdict(result))
        config.remove("direct_chunk_writes")
        assertThrows(IllegalArgumentException::class.java) { NativeInteractionFamilyRuntimeTest.deferredVerdict(result) }
    }
}
