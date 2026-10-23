package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Tag("fast")
class TimingRecordsTest {
    private val mapper = jacksonObjectMapper()
    private fun directory(): Path {
        val base = Path.of(System.getProperty("user.home"), ".tmp")
        Files.createDirectories(base)
        return Files.createTempDirectory(base, "timing-records-")
    }

    @Test fun `concurrent append retains complete distinct start and terminal pairs`() {
        val root = directory()
        val sink = root.resolve("shared.jsonl")
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..40).map { index -> pool.submit {
                TimingRecords.process(root.resolve("$index.log"), root, listOf("inert", "$index"),
                    Duration.ofSeconds(3), mapOf("BC_TIMING_FILE" to sink.toString(),
                        "BC_TIMING_REVISION" to "fixture-revision", "BC_TIMING_PROFILE" to "inert"))
                    .finish("SUCCEEDED", 0)
            } }
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally { pool.shutdownNow() }
        val records = Files.readAllLines(sink).map(mapper::readTree)
        assertEquals(80, records.size)
        val groups = records.groupBy { it["operation_id"].asText() }
        assertEquals(40, groups.size)
        groups.values.forEach { pair ->
            assertEquals(listOf("start", "terminal"), pair.map { it["event"].asText() })
            assertTrue(pair[0]["duration_ns"].isNull)
            assertTrue(pair[1]["duration_ns"].asLong() >= 0)
            assertTrue(pair[1]["expected_runtime_ns"].isNull)
            assertEquals("process", pair[1]["scope"].asText())
            assertEquals("fixture-revision", pair[1]["revision"].asText())
            assertEquals(3_000_000_000, pair[1]["configured_timeout_ns"].asLong())
        }
    }

    @Test fun `start is immediately readable and interrupted evidence is never inferred success`() {
        val root = directory()
        val sink = root.resolve("interrupted.log.timing.jsonl")
        TimingRecords.process(root.resolve("interrupted.log"), root, listOf("inert"), Duration.ofSeconds(1),
            mapOf("BC_TIMING_FILE" to sink.toString(), "UNRELATED_SECRET" to "not-recorded"))
        val record = mapper.readTree(Files.readAllLines(sink).single())
        assertEquals("RUNNING", record["status"].asText())
        assertTrue(record["duration_ns"].isNull)
        assertFalse(Files.readString(sink).contains("not-recorded"))
    }

    @Test fun `timing sink rejects symlink ancestry before append`() {
        val root = directory()
        val alias = root.resolve("alias")
        Files.createSymbolicLink(alias, root)
        assertThrows(IllegalArgumentException::class.java) {
            TimingRecords.process(root.resolve("command.log"), root, listOf("inert"), Duration.ofSeconds(1),
                mapOf("BC_TIMING_FILE" to alias.resolve("timing.jsonl").toString()))
        }
        assertFalse(Files.exists(root.resolve("timing.jsonl")))
    }
}
