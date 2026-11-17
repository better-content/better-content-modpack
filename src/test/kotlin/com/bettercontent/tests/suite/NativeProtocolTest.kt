package com.bettercontent.tests.suite

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.UUID

class NativeProtocolTest {
    @TempDir lateinit var temp: Path
    private val token = "a".repeat(64)
    private fun request(seq: Long = 1) = NativeRequest("bc.playtest_action.v2", "test-run", "case.one", token,
        seq, "player.snapshot", 200, jsonObject("player_uuid" to "test"))
    private fun observation(seq: Long, kind: String, source: String = "server", requestSequence: Long = 1) =
        NativeObservation("bc.playtest_observation.v2", "test-run", "case.one", token,
            seq, requestSequence, "player.snapshot", source, kind, seq, jsonObject())

    @Test fun `complete ordered exchange is accepted without interpreting action as assertion pass`() {
        val validator = NativeExchangeValidator(); validator.begin(request())
        assertFalse(validator.accept(observation(1, "started")))
        assertFalse(validator.accept(observation(2, "sample")))
        assertTrue(validator.accept(observation(3, "completed")))
        validator.begin(request(2))
        assertFalse(validator.accept(observation(4, "started", requestSequence = 2)))
    }
    @Test fun `terminal before start fails`() {
        val validator = NativeExchangeValidator(); validator.begin(request())
        assertThrows(IllegalArgumentException::class.java) { validator.accept(observation(1, "completed")) }
    }
    @Test fun `wrong token run case and request cannot be replayed`() {
        listOf(observation(1, "started").copy(token = "b".repeat(64)),
            observation(1, "started").copy(runId = "another-run"),
            observation(1, "started").copy(caseId = "case.two"),
            observation(1, "started", requestSequence = 2)).forEach { invalid ->
            val validator = NativeExchangeValidator(); validator.begin(request())
            assertThrows(IllegalArgumentException::class.java) { validator.accept(invalid) }
        }
    }
    @Test fun `duplicate and reordered output fails`() {
        val validator = NativeExchangeValidator(); validator.begin(request())
        validator.accept(observation(1, "started"))
        assertThrows(IllegalArgumentException::class.java) { validator.accept(observation(1, "sample")) }
        assertThrows(IllegalArgumentException::class.java) { validator.accept(observation(3, "sample")) }
    }
    @Test fun `server report cannot be client proof`() {
        val validator = NativeExchangeValidator(); validator.begin(request())
        assertThrows(IllegalArgumentException::class.java) { validator.accept(observation(1, "started"), "client") }
    }
    @Test fun `duplicate terminal and overlapping requests fail`() {
        val validator = NativeExchangeValidator(); validator.begin(request())
        assertThrows(IllegalArgumentException::class.java) { validator.begin(request(2)) }
        validator.accept(observation(1, "started")); validator.accept(observation(2, "completed"))
        assertThrows(IllegalArgumentException::class.java) { validator.accept(observation(3, "completed")) }
    }
    @Test fun `controller allocated sequence is exact and stale sequence cannot publish`() {
        val mailbox = NativeMailbox(temp.resolve("mailbox"), "epoch-${UUID.randomUUID()}")
        assertEquals(1L, mailbox.reserveSequence())
        assertEquals(2L, mailbox.reserveSequence())
        assertThrows(IllegalArgumentException::class.java) {
            mailbox.execute("case.one", "player.snapshot", jsonObject(), Duration.ofMillis(1), allocatedSequence = 1)
        }
        assertFalse(Files.exists(mailbox.directory.resolve("requests.jsonl")))
    }
    @Test fun `partial line is retained but never returned as evidence`() {
        val log = temp.resolve("observations.jsonl")
        val json = suiteMapper.writeValueAsString(observation(1, "started"))
        Files.writeString(log, json.take(12))
        val reader = NativeObservationReader(log)
        assertTrue(reader.readAvailable().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { reader.requireComplete() }
        Files.writeString(log, json.drop(12) + "\n", StandardOpenOption.APPEND)
        assertEquals(listOf(observation(1, "started")), reader.readAvailable())
        reader.requireComplete()
    }
    @Test fun `oversized lines and truncation fail closed`() {
        val oversized = temp.resolve("oversized.jsonl")
        Files.writeString(oversized, "x".repeat(PROTOCOL_LINE_LIMIT + 1))
        assertThrows(IllegalArgumentException::class.java) { NativeObservationReader(oversized).readAvailable() }
        val log = temp.resolve("observations.jsonl")
        Files.writeString(log, suiteMapper.writeValueAsString(observation(1, "started")) + "\n")
        val reader = NativeObservationReader(log); reader.readAvailable()
        Files.writeString(log, "")
        assertThrows(IllegalArgumentException::class.java) { reader.readAvailable() }
    }
    @Test fun `malformed UTF8 and unknown JSON fields are not silently accepted`() {
        val invalid = temp.resolve("bad.jsonl")
        Files.write(invalid, byteArrayOf(0xc3.toByte(), 0x28, 10))
        assertThrows(Exception::class.java) { NativeObservationReader(invalid).readAvailable() }
        val extra = temp.resolve("extra.jsonl")
        Files.writeString(extra, suiteMapper.writeValueAsString(observation(1, "started")).dropLast(1) + ",\"passed\":true}\n")
        assertThrows(Exception::class.java) { NativeObservationReader(extra).readAvailable() }
    }
}
