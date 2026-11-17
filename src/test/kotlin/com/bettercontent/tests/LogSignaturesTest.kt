package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LogSignaturesTest {
    @org.junit.jupiter.api.io.TempDir lateinit var directory: java.nio.file.Path

    @Test fun `repeated warning flood retains exact counts but bounded occurrence samples`() {
        val log = directory.resolve("server.log")
        java.nio.file.Files.newBufferedWriter(log).use { writer ->
            repeat(10000) { writer.write("[12:00:00] [Server thread/WARN] Repeated optional warning\n") }
        }
        val result = recordLogSignatures(directory, mapOf("server" to log))
        val doc = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(result.toFile())
        val group = doc.path("signatures").single()
        assertEquals(10000, group.path("count").asInt())
        assertTrue(group.path("occurrences").size() <= 40)
        assertEquals("unreviewed", doc.path("status").asText())
    }

    @Test fun `unique warning overflow is explicit and raw logs are retained`() {
        val log = directory.resolve("unique.log")
        java.nio.file.Files.newBufferedWriter(log).use { writer ->
            repeat(1025) { writer.write("[12:00:00] [Server thread/WARN] Distinct recipe test:item_$it\n") }
        }
        val output = recordLogSignatures(directory, mapOf("server" to log))
        val doc = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(output.toFile())
        assertFalse(doc.path("complete").asBoolean())
        assertEquals(1, doc.path("unindexed_events").asInt())
        assertEquals(1024, doc.path("signatures").size())
        assertTrue(java.nio.file.Files.exists(log))
    }

    @Test fun `multiline exceptions retain raw spans and are grouped rather than multiplied`() {
        val lines = listOf(
            "[12:00:00] [Server thread/ERROR] [example/]: Failed recipe minecraft:stick",
            "java.lang.IllegalStateException: unavailable",
            "\tat example.Owner.tick(Owner.java:25)",
            "[12:00:01] [Server thread/INFO] [example/]: still running",
            "[12:00:02] [Server thread/ERROR] [example/]: Failed recipe minecraft:stick",
            "java.lang.IllegalStateException: unavailable",
            "\tat example.Owner.tick(Owner.java:25)")
        val events = collectDiagnosticEvents("server", lines.asSequence())
        assertEquals(2, events.size); assertEquals(1L, events.first().firstLine); assertEquals(3L, events.first().lastLine)
        val groups = diagnosticSignatures(events)
        assertEquals(1, groups.size); assertEquals(2L, groups.single().count)
        assertEquals("java.lang.IllegalStateException", groups.single().exception)
        assertEquals("unreviewed", groups.single().reviewState)
    }
    @Test fun `signature normalization does not hide different recipe failures or error quantities`() {
        val events = collectDiagnosticEvents("server", sequenceOf(
            "[12:00:00] [Server thread/WARN] Failed recipe minecraft:stick code=123",
            "[12:00:01] [Server thread/WARN] Failed recipe minecraft:stone code=123",
            "[12:00:02] [Server thread/WARN] Failed recipe minecraft:stick code=124"))
        assertEquals(3, diagnosticSignatures(events).size)
    }
    @Test fun `warnings remain diagnostic records not an automatic test failure`() {
        val groups = diagnosticSignatures(collectDiagnosticEvents("client", sequenceOf("[12:00:00] [Render thread/WARN] Optional shader disabled")))
        assertEquals("WARN", groups.single().severity)
        assertEquals("unreviewed", groups.single().reviewState)
    }
}
