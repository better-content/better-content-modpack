package com.bettercontent.tests

import com.bettercontent.gameplaynotices.diagnostics.PlaytestLog
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class PlaytestLoggingAuditTest {
    @TempDir lateinit var directory: Path
    private val process = "11111111-1111-4111-8111-111111111111"
    private val head = "a".repeat(40)
    private val expected get() = mapOf("test_mod" to head)
    private fun record(event: String, extra: String = "", level: String = "DEBUG") =
        "[00:00:00] [Server thread/$level] [bc.playtest.test_mod.transaction]: BC_PLAYTEST schema=bc.playtest.v1 mod=test_mod event=$event side=server process=$process result=observed reason=ok$extra"
    private fun status() = record("logging.status", " source=$head detail_per_minute=120 burst=20 max_keys=256 max_record=2048 summary_seconds=30", "INFO")
    private fun scan(vararg lines: String, activation: Boolean = false): PlaytestLoggingAudit.Report {
        val path = directory.resolve("raw-${Files.list(directory).use { it.count() }}.log")
        Files.writeString(path, lines.joinToString("\n", postfix = "\n"))
        return PlaytestLoggingAudit.scan(listOf(path), expected, activation)
    }

    @Test fun actualEnvelopeCountsNativeBytesAndEscapedFieldsWithoutClaimingGameplayCoverage() {
        val lines = arrayOf(status(), record("transaction.commit", " note=\"space \\\"quote\\\" literal\\nline\""),
            record("logging.summary", " attempted=176 emitted=20 suppressed=156 overflow=0 keys=6"))
        val report = scan(*lines, activation = true)
        assertEquals(emptyList<String>(), report.findings)
        assertEquals(3, report.records)
        assertEquals(2, report.detailRecords)
        assertEquals(lines.sumOf { it.toByteArray().size.toLong() + 1 }, report.nativeUtf8Bytes)
        assertEquals(mapOf("test_mod" to setOf(process)), report.activations)
        assertEquals(PlaytestLoggingAudit.Totals(176, 20, 156, 0), report.summaries["test_mod"])
    }

    @Test fun actualPortableProducerStatusTruncationAndSummaryRoundTripThroughAudit() {
        val lines = mutableListOf<String>()
        val log = PlaytestLog("test_mod", object : PlaytestLog.Sink {
            override fun enabled(category: String, level: String) = true
            override fun write(category: String, level: String, message: String) {
                lines += "[00:00:00] [Server thread/$level] [bc.playtest.test_mod.$category]: $message"
            }
        }, java.util.function.LongSupplier { 0L }, java.util.Properties())
        log.status("server", "1.0.0", head) { PlaytestLog.fields("enabled", true) }
        val values = buildList<Any> {
            for (i in 0..39) { add("detail.$i"); add("quoted \\\"value\\\" " + "x".repeat(120)) }
        }.toTypedArray()
        log.debug("transaction", "transaction.commit", PlaytestLog.Context.server("minecraft:overworld", 8L), "observed", "native_outcome") {
            PlaytestLog.fields(*values)
        }
        log.reset("server")
        assertTrue(lines.any { it.endsWith(" truncated=true") })
        val report = scan(*lines.toTypedArray(), activation = true)
        assertEquals(emptyList<String>(), report.findings)
        assertEquals(3L, report.records)
        assertEquals(PlaytestLoggingAudit.Totals(1, 1, 0, 0), report.summaries["test_mod"])
    }

    @Test fun messageQuotedInOtherLoggerCannotEstablishVisibility() {
        val report = scan("[00:00:00] [Server thread/INFO] [native]: ${status()}", activation = true)
        assertEquals(0, report.records)
        assertTrue(report.findings.any { "no playtest activation" in it })
    }

    @Test fun rejectsUnknownSchemaSourceMismatchAndWrongLogger() {
        for (line in listOf(status().replace("bc.playtest.v1", "bc.playtest.v2"),
            status().replace("source=$head", "source=UNKNOWN"), status().replace("[bc.playtest.test_mod.", "[bc.playtest.other."))) {
            assertTrue(scan(line).findings.isNotEmpty(), line)
        }
    }

    @Test fun requiredFieldsDuplicateActivationAndPrivateFieldsAreBlocking() {
        assertTrue(scan(status(), status()).findings.any { "duplicate per-process activation" in it })
        assertTrue(scan(record("x").replace(" reason=ok", "")).findings.any { "missing required" in it })
        assertTrue(scan(record("x", " reason=changed")).findings.any { "duplicate playtest field" in it })
        assertTrue(scan(record("x", " seed=1")).findings.any { "private default" in it })
        assertTrue(scan(record("x").replace(process, "1-1-1-1-1")).findings.any { "process identity" in it })
    }

    @Test fun rejectsSummaryConservationCardinalityNegativeAndOverflowViolations() {
        for (fields in listOf("attempted=20 emitted=20 suppressed=1 overflow=0 keys=1", "attempted=1 emitted=1 suppressed=0 overflow=0 keys=257",
            "attempted=1 emitted=1 suppressed=0 overflow=2 keys=1", "attempted=1 emitted=-1 suppressed=2 overflow=0 keys=1")) {
            assertTrue(scan(record("logging.summary", " $fields")).findings.any { "aggregation" in it })
        }
    }

    @Test fun everyBoundaryOfQuotedEscapedAndUnquotedOptionalFieldsCanBeTruncatedWithoutRegexRecursion() {
        val prefix = "[00:00:00] [Server thread/DEBUG] [bc.playtest.test_mod.transaction]: "
        val envelope = LogPolicy.parseRecord(record("x"))!!.message
        val optional = (0..25).joinToString("") { " k$it=\"word ${"x".repeat(100)} \\\"quote\\\" back\\\\slash\" n$it=123" }
        for (padding in 0..150) {
            val message = (envelope + " padding=\"" + "x".repeat(padding) + "\"" + optional).take(2033) + " truncated=true"
            assertEquals(emptyList<String>(), scan(prefix + message).findings, "padding=$padding")
        }
    }

    private fun causalProducerLines(): List<String> {
        val lines = mutableListOf<String>()
        fun producer(mod: String) = PlaytestLog(mod, object : PlaytestLog.Sink {
            override fun enabled(category: String, level: String) = true
            override fun write(category: String, level: String, message: String) {
                lines += "[00:00:00] [Server thread/$level] [bc.playtest.$mod.$category]: $message"
            }
        }, java.util.function.LongSupplier { 0L }, java.util.Properties())
        val tnt = producer("scalable_tnt")
        val buried = producer("better_buried_encounters")
        val entity = "22222222-2222-4222-8222-222222222222"
        val mob = "33333333-3333-4333-8333-333333333333"
        fun context(tick: Long, subject: String = "", pos: String = "") = PlaytestLog.Context("server", "minecraft:overworld", tick, subject, pos)
        tnt.debug("priming", "tnt.prime", context(10, entity), "inserted", "native_insertion", null)
        tnt.debug("explosion", "tnt.explosion_handoff", context(12, entity), "handed_off", "authored_power", null)
        buried.debug("removal", "sealed_monster.removal_watch", context(10, pos = "1,2,3"), "admitted", "watch_registered", null)
        buried.debug("removal", "sealed_monster.removal_watch", context(12, pos = "1,2,3"), "consumed", "native_callback", null)
        buried.debug("release", "sealed_monster.release", context(12, mob, "1,2,3"), "inserted", "native_insertion", null)
        buried.debug("removal", "sealed_monster.removal_watch", context(10, pos = "4,5,6"), "admitted", "watch_registered", null)
        buried.debug("removal", "sealed_monster.removal_watch", context(100, pos = "4,5,6"), "expired", "timeout", null)
        return lines
    }
    private fun causal(lines: List<String>): Map<String, Any> {
        val path = directory.resolve("causal-${Files.list(directory).use { it.count() }}.log")
        Files.write(path, lines)
        return PlaytestLoggingAudit.tntBuriedCausality(listOf(path))
    }

    @Test fun actualProducerCausalFieldsLinkNativeStagesButDoNotClaimSyntheticGameplay() {
        val result = causal(causalProducerLines())
        assertEquals(setOf("22222222-2222-4222-8222-222222222222"), result["prime_handoff_subjects"])
        assertEquals(setOf("1,2,3"), result["watch_release_origins"])
        assertEquals(setOf("4,5,6"), result["expired_origins"])
        assertEquals(7, result["sampled_details"])
    }

    @Test fun activationInfoOtherLoggerAndCrossProcessCannotSatisfyNativeCausality() {
        val lines = causalProducerLines()
        assertThrows(IllegalArgumentException::class.java) { causal(lines.map { it.replace("/DEBUG]", "/INFO]") }) }
        assertThrows(IllegalArgumentException::class.java) { causal(lines.map { it.replace("[bc.playtest.", "[other.") }) }
        val wrongProcess = lines.map { line -> if (!line.contains("event=tnt.explosion_handoff")) line else
            line.replace(Regex("process=([0-9a-f-]{36})")) { match ->
                val original = match.groupValues[1]
                "process=" + original.dropLast(1) + if (original.last() == '0') "1" else "0"
            }
        }
        assertThrows(IllegalArgumentException::class.java) { causal(wrongProcess) }
    }

    @Test fun wrongOriginMissingActualMobIdentityAndMissingExpiryFailClosed() {
        val lines = causalProducerLines()
        assertThrows(IllegalArgumentException::class.java) { causal(lines.map { if (it.contains("event=sealed_monster.release")) it.replace("pos=1,2,3", "pos=7,8,9") else it }) }
        assertThrows(IllegalArgumentException::class.java) { causal(lines.map { if (it.contains("event=sealed_monster.release")) it.replace(Regex(" subject=[^ ]+"), "") else it }) }
        assertThrows(IllegalArgumentException::class.java) { causal(lines.filterNot { it.contains("result=expired") }) }
    }

    @Test fun reversedNativeTickCannotInventPrimeToHandoffCausality() {
        assertThrows(IllegalArgumentException::class.java) {
            causal(causalProducerLines().map { if (it.contains("event=tnt.explosion_handoff")) it.replace("tick=12", "tick=9") else it })
        }
    }

    @Test fun rendererMayTruncateOnlyFinalOptionalFieldWithinExactProducerBound() {
        val initial = record("x", " detail=\"")
        val message = LogPolicy.parseRecord(initial)!!.message
        val truncated = initial + "x".repeat(2048 - message.length - 15) + " truncated=true"
        assertEquals(emptyList<String>(), scan(truncated).findings)
        assertTrue(scan(truncated + "x").findings.any { "exceeds producer bound" in it })
        assertTrue(scan(record("x", " invalid tail")).findings.any { "malformed" in it })
    }
}
