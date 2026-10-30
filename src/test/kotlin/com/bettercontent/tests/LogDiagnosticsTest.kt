package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class LogDiagnosticsTest {
    @TempDir lateinit var directory: Path

    @Test fun `records warning and error lines without failing on nonfatal messages`() {
        val server = directory.resolve("server.log")
        server.writeText("[01:02:03] [Server thread/WARN] [forge/]: kept warning\n" +
            "[01:02:04] [Server thread/ERROR] [mod/]: kept error\n" +
            "[01:02:05] [Server thread/INFO] [mod/]: normal\n")
        val report = recordLogDiagnostics(directory, mapOf("server" to server, "client" to directory.resolve("missing.log")))
        val rows = directory.resolve("log-diagnostics.jsonl").toFile().readLines().map {
            jacksonObjectMapper().readTree(it)
        }
        assertEquals(listOf("WARN", "ERROR"), rows.map { it.path("level").asText() })
        assertEquals(listOf(1L, 2L), rows.map { it.path("line").asLong() })
        @Suppress("UNCHECKED_CAST")
        val counts = report["counts"] as Map<String, Map<String, Int>>
        assertEquals(1, counts.getValue("server").getValue("WARN"))
        assertEquals(1, counts.getValue("server").getValue("ERROR"))
        assertEquals(0, counts.getValue("client").getValue("WARN"))
        @Suppress("UNCHECKED_CAST")
        val present = report["raw_logs_retained"] as Map<String, Boolean>
        assertFalse(present.getValue("client"))
    }
}
