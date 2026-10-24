package com.bettercontent.tests

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.MarkerManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.xml.XmlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class FixtureAuditLoggingTest {
    @TempDir lateinit var temp: Path
    private val repo = Path.of(System.getProperty("bc.repo.root", "."))

    private fun fixture(name: String = "fixture"): Path {
        val root = Files.createDirectory(temp.resolve(name))
        Files.createDirectories(root.resolve("config"))
        // Redirect only the copied presentation output; never let an isolated context touch repo logs.
        val xml = Files.readString(repo.resolve("config/better-content-log4j2.xml"))
            .replace("logs/latest.log", root.resolve("presentation/latest.log").toString())
            .replace("logs/%d{yyyy-MM-dd}-%i.log.gz", root.resolve("presentation/%d{yyyy-MM-dd}-%i.log.gz").toString())
        Files.writeString(root.resolve("config/better-content-log4j2.xml"), xml)
        Files.copy(repo.resolve("config/logbegone.toml"), root.resolve("config/logbegone.toml"))
        return root
    }

    private fun emit(launch: FixtureAuditLogging.Launch, action: (LoggerContext) -> Unit) {
        val context = LoggerContext("fixture-audit-isolated")
        Files.newInputStream(launch.configuration).use { input ->
            context.start(XmlConfiguration(context, ConfigurationSource(input, launch.configuration.toFile())))
        }
        try { action(context) } finally { context.stop() }
    }

    @Test fun realContextRetainsFilteredErrorsFatalWarningsTimingAndExceptionTraces() {
        val root = fixture()
        val original = Files.readAllBytes(root.resolve("config/better-content-log4j2.xml"))
        val launch = FixtureAuditLogging.prepare(root, "server")
        emit(launch) { context ->
            val logger = context.getLogger("fixture.native")
            logger.error("Recipe count failed to load fixture recipe")
            logger.fatal("Could not load /assets/iceandfire/models/tabula/firedragon/firedragon_swimming.tbl: in is null")
            logger.warn("benign compatibility notice")
            logger.warn("Recipe count failed to load fixture registry")
            logger.info(MarkerManager.getMarker("NETWORK_PACKETS"), "audit-only packet marker")
            logger.log(Level.ERROR, "Item Capability Exception: actual failure", IllegalStateException("fixture exception detail"))
            val oldName = Thread.currentThread().name
            try {
                Thread.currentThread().name = "main"
                context.getLogger("net.minecraftforge.fml.DeferredWorkQueue")
                    .warn("Mod 'cold_sweat' took 3.5 s to run a deferred task.")
            } finally { Thread.currentThread().name = oldName }
        }
        launch.requireNativeRecords()
        val raw = Files.readString(launch.auditLog)
        val presentation = Files.readString(root.resolve("presentation/latest.log"))
        listOf("Recipe count failed", "firedragon_swimming", "Item Capability Exception:", "cold_sweat", "audit-only packet marker").forEach {
            assertTrue(raw.contains(it), "missing raw native payload: $it")
            assertFalse(presentation.contains(it), "presentation filtering changed: $it")
        }
        assertTrue(presentation.contains("benign compatibility notice"))
        assertTrue(raw.contains("java.lang.IllegalStateException: fixture exception detail"))
        assertTrue(raw.contains("at com.bettercontent.tests.FixtureAuditLoggingTest"))
        val findings = LogPolicy.findings(listOf(launch.auditLog))
        assertTrue(findings.any { it.text.contains("Recipe count failed to load fixture recipe") })
        assertTrue(findings.any { it.text.contains("firedragon_swimming") })
        assertTrue(findings.any { it.text.contains("failed to load fixture registry") })
        assertFalse(findings.any { it.text.contains("benign compatibility notice") })
        assertFalse(findings.any { it.text.contains("cold_sweat") })
        assertEquals(3.5, LogPolicy.startupTimings(listOf(launch.auditLog)).single().seconds)
        assertArrayEquals(original, Files.readAllBytes(root.resolve("config/better-content-log4j2.xml")))
        assertEquals(4, launch.hashes.size)
    }

    @Test fun additivePlaytestDebugReachesFileAndAuditButNotConsoleAndCannotHideFailures() {
        val root = fixture()
        val launch = FixtureAuditLogging.prepare(root, "playtest")
        val output = ByteArrayOutputStream()
        val previous = System.out
        try {
            System.setOut(PrintStream(output, true, Charsets.UTF_8))
            emit(launch) { context ->
                val log = context.getLogger("bc.playtest.test_mod.transaction")
                log.debug("BC_PLAYTEST schema=bc.playtest.v1 event=debug_visible reason=ordinary")
                log.trace("trace_not_selected")
                log.error("Recipe count failed to load child fixture", IllegalStateException("child exception detail"))
                log.fatal("child fatal remains visible")
                context.getLogger("fixture.native").info("native readiness")
            }
        } finally { System.setOut(previous) }
        launch.requireNativeRecords()
        val raw = Files.readString(launch.auditLog)
        val file = Files.readString(root.resolve("presentation/latest.log"))
        val console = output.toString(Charsets.UTF_8)
        assertTrue(raw.contains("debug_visible"))
        assertTrue(file.contains("debug_visible"))
        assertFalse(console.contains("debug_visible"))
        assertFalse(raw.contains("trace_not_selected"))
        assertTrue(raw.contains("Recipe count failed to load child fixture"))
        assertTrue(raw.contains("child fatal remains visible"))
        assertTrue(raw.contains("java.lang.IllegalStateException: child exception detail"))
        assertFalse(file.contains("Recipe count failed to load child fixture"))
        assertTrue(console.contains("child fatal remains visible"))
        val findings = LogPolicy.findings(listOf(launch.auditLog))
        assertTrue(findings.any { it.text.contains("Recipe count failed to load child fixture") })
        assertTrue(findings.any { it.text.contains("child fatal remains visible") })
        assertFalse(findings.any { it.text.contains("debug_visible") })
    }

    @Test fun fixtureMixinDebugWitnessesAreAdditiveWithoutChangingProductionOrHidingErrors() {
        val root = fixture()
        val original = Files.readAllBytes(root.resolve("config/better-content-log4j2.xml"))
        val launch = FixtureAuditLogging.prepare(root, "mixin-witness")
        val output = ByteArrayOutputStream()
        val previous = System.out
        try {
            System.setOut(PrintStream(output, true, Charsets.UTF_8))
            emit(launch) { context ->
                assertEquals(Level.INFO, context.configuration.rootLogger.level)
                val mixin = context.getLogger("mixin")
                mixin.debug("Mixing exactFixtureMixin from fixture.mixins.json into fixture.Target")
                mixin.trace("mixin_trace_not_selected")
                mixin.error("fixture mixin structural failure")
                context.getLogger("fixture.native").info("native readiness")
            }
        } finally { System.setOut(previous) }
        launch.requireNativeRecords()
        val raw = Files.readString(launch.auditLog)
        assertTrue(raw.contains("Mixing exactFixtureMixin"))
        assertFalse(raw.contains("mixin_trace_not_selected"))
        assertTrue(raw.contains("fixture mixin structural failure"))
        assertTrue(LogPolicy.findings(listOf(launch.auditLog)).any { it.text.contains("fixture mixin structural failure") })
        assertFalse(output.toString(Charsets.UTF_8).contains("Mixing exactFixtureMixin"))
        assertArrayEquals(original, Files.readAllBytes(root.resolve("config/better-content-log4j2.xml")))
    }

    @Test fun scopedLevelsRemainAdditiveForAllAllowedOverrides() {
        val root = fixture()
        val xml = root.resolve("config/better-content-log4j2.xml")
        Files.writeString(xml, Files.readString(xml).replace("<Loggers>", """<Loggers>
            <Logger name="bc.playtest.test_mod" level="INFO" additivity="true"/>
            <Logger name="bc.playtest.test_mod.focus" level="TRACE" additivity="true"/>"""))
        val launch = FixtureAuditLogging.prepare(root, "overrides")
        emit(launch) { context ->
            context.getLogger("bc.playtest.test_mod.ordinary").debug("mod_debug_disabled")
            context.getLogger("bc.playtest.test_mod.focus").trace("explicitly_selected_trace")
            context.getLogger("bc.playtest.test_mod.ordinary").error("ordinary child error")
            context.getLogger("bc.playtest.test_mod.focus").fatal("focused child fatal")
        }
        launch.requireNativeRecords()
        val raw = Files.readString(launch.auditLog)
        assertFalse(raw.contains("mod_debug_disabled"))
        assertTrue(raw.contains("explicitly_selected_trace"))
        assertTrue(raw.contains("ordinary child error"))
        assertTrue(raw.contains("focused child fatal"))
    }

    @Test fun globalProfilePropertyCanDisableVerboseWithoutDisablingErrors() {
        val previous = System.getProperty("bc.playtest.level")
        try {
            for (level in listOf("INFO", "DEBUG", "TRACE")) {
                System.setProperty("bc.playtest.level", level)
                val root = fixture("mode-$level")
                val launch = FixtureAuditLogging.prepare(root, "mode-$level")
                emit(launch) { context ->
                    val log = context.getLogger("bc.playtest.test_mod.action")
                    log.debug("mode_debug")
                    log.trace("mode_trace")
                    log.error("mode_error")
                }
                val raw = Files.readString(launch.auditLog)
                assertEquals(level != "INFO", raw.contains("mode_debug"), level)
                assertEquals(level == "TRACE", raw.contains("mode_trace"), level)
                assertTrue(raw.contains("mode_error"), level)
                launch.requireNativeRecords()
            }
        } finally {
            if (previous == null) System.clearProperty("bc.playtest.level") else System.setProperty("bc.playtest.level", previous)
        }
    }

    @Test fun debugOnlyRecordsDoNotEstablishNativeReadiness() {
        val launch = FixtureAuditLogging.prepare(fixture(), "debug-only")
        emit(launch) { it.getLogger("bc.playtest.test_mod.action").debug("debug_only_record") }
        assertTrue(Files.readString(launch.auditLog).contains("debug_only_record"))
        assertThrows(IllegalArgumentException::class.java) { launch.requireNativeRecords() }
    }

    @Test fun privateFilteringAndUnknownLoggerRoutesAreRejectedBeforeAnyPatch() {
        val invalid = listOf(
            """<Logger name="bc.playtest.evil" level="DEBUG" additivity="false"/>""",
            """<Logger name="bc.playtest.evil" level="OFF" additivity="true"/>""",
            """<Logger name="bc.playtest.evil" level="DEBUG" additivity="true"><AppenderRef ref="File"/></Logger>""",
            """<Logger name="bc.playtest.evil" level="DEBUG" additivity="true"><Filters/></Logger>""",
            """<Logger name="bc.playtest.evil" level="DEBUG" additivity="true" includeLocation="false"/>""",
            """<Logger name="bc.playtesting.evil" level="DEBUG" additivity="true"/>""",
            """<AsyncLogger name="bc.playtest.evil" level="DEBUG" additivity="true"/>""",
            """<Logger name="bc.playtest" level="DEBUG" additivity="true"/>""",
        )
        invalid.forEachIndexed { index, node ->
            val root = fixture("invalid-$index")
            val logbegone = Files.readString(root.resolve("config/logbegone.toml"))
            val xml = root.resolve("config/better-content-log4j2.xml")
            Files.writeString(xml, Files.readString(xml).replace("<Loggers>", "<Loggers>$node"))
            assertThrows(IllegalArgumentException::class.java) { FixtureAuditLogging.prepare(root, "invalid-$index") }
            assertEquals(logbegone, Files.readString(root.resolve("config/logbegone.toml")))
            assertFalse(Files.exists(root.resolve(".fixture-audit")))
        }
    }

    @Test fun sizeRolloverRetainsMoreThanTheNativeDefaultSevenArchives() {
        val root = fixture()
        val xml = root.resolve("config/better-content-log4j2.xml")
        val production = Files.readString(xml)
        assertTrue(production.contains("SizeBasedTriggeringPolicy size=\"32 MB\""))
        assertTrue(production.contains("DefaultRolloverStrategy fileIndex=\"nomax\""))
        // Shrink only this copied fixture's trigger, not the production policy.
        Files.writeString(xml, production.replace("size=\"32 MB\"", "size=\"1 KB\""))
        val launch = FixtureAuditLogging.prepare(root, "rollover")
        emit(launch) { context ->
            val log = context.getLogger("bc.playtest.test_mod.action")
            repeat(20) { log.debug("rollover-$it " + "x".repeat(1200)) }
            context.getLogger("fixture.native").info("native ready after rollover")
        }
        launch.requireNativeRecords()
        val archives = Files.list(root.resolve("presentation")).use { paths -> paths.filter { it.fileName.toString().endsWith(".log.gz") }.count() }
        assertTrue(archives > 7, "non-deleting rollover retained only $archives archives")
        val raw = Files.readString(launch.auditLog)
        repeat(20) { assertTrue(raw.contains("rollover-$it "), "audit lost record $it") }
    }

    @Test fun restartKeepsOldAuditAndGeneratesDistinctConfigAndSink() {
        val root = fixture()
        val first = FixtureAuditLogging.prepare(root, "server")
        emit(first) { it.getLogger("native.first").info("first native startup") }
        val bytes = Files.readAllBytes(first.auditLog)
        val second = FixtureAuditLogging.prepare(root, "server-restart")
        assertNotEquals(first.configuration, second.configuration)
        assertNotEquals(first.auditLog, second.auditLog)
        emit(second) { it.getLogger("native.second").info("second native startup") }
        first.requireNativeRecords()
        second.requireNativeRecords()
        assertArrayEquals(bytes, Files.readAllBytes(first.auditLog))
        assertFalse(Files.readString(second.auditLog).contains("first native startup"))
    }

    @Test fun missingEmptyAndNonNativeOutputFailClosed() {
        val launch = FixtureAuditLogging.prepare(fixture(), "client")
        assertThrows(IllegalArgumentException::class.java) { launch.requireNativeRecords() }
        Files.writeString(launch.auditLog, "")
        assertThrows(IllegalArgumentException::class.java) { launch.requireNativeRecords() }
        Files.writeString(launch.auditLog, "[INFO] not a native record\n")
        assertThrows(IllegalArgumentException::class.java) { launch.requireNativeRecords() }
    }

    @Test fun configDriftFailsBeforeAndAfterLaunch() {
        val root = fixture()
        val launch = FixtureAuditLogging.prepare(root, "server")
        Files.writeString(root.resolve("config/logbegone.toml"), "[logbegone]\nregex = []\nphrases = [\"suppress\"]\n")
        assertThrows(IllegalArgumentException::class.java) { launch.assertStable() }
        assertThrows(IllegalArgumentException::class.java) { FixtureAuditLogging.prepare(root, "restart") }
    }

    @Test fun generatedXmlAndPackagedXmlHaveIndependentDriftChecks() {
        val root = fixture()
        val launch = FixtureAuditLogging.prepare(root, "server")
        val original = Files.readAllBytes(launch.configuration)
        Files.writeString(launch.configuration, "<Configuration/>")
        assertThrows(IllegalArgumentException::class.java) { launch.assertStable() }
        Files.write(launch.configuration, original)
        launch.assertStable()
        Files.writeString(root.resolve("config/better-content-log4j2.xml"), "<Configuration/>")
        assertThrows(IllegalArgumentException::class.java) { launch.assertStable() }
    }

    @Test fun invalidLogBegoneValuesAreRejectedRatherThanSilentlySanitized() {
        val root = fixture()
        val config = root.resolve("config/logbegone.toml")
        listOf("not_a_string", "\"bad\\q\"", "\"first\" \"missing comma\"", "true").forEach { invalid ->
            val original = "[logbegone]\nregex = [$invalid]\nphrases = []\n"
            Files.writeString(config, original)
            assertThrows(IllegalArgumentException::class.java) { FixtureAuditLogging.prepare(root, "invalid") }
            assertEquals(original, Files.readString(config))
        }
    }

    @Test fun missingPackagedConfigIsRejected() {
        val root = fixture()
        val xml = root.resolve("config/better-content-log4j2.xml")
        // Rename rather than delete the fixture input, keeping diagnosis material available.
        Files.move(xml, xml.resolveSibling("missing-original.xml"))
        assertThrows(IllegalArgumentException::class.java) { FixtureAuditLogging.prepare(root, "server") }
    }

    @Test fun malformedOrUnsupportedPackagedXmlIsRejectedWithoutPatchingLogBegone() {
        val root = fixture()
        val logbegone = Files.readString(root.resolve("config/logbegone.toml"))
        val xml = root.resolve("config/better-content-log4j2.xml")
        listOf("<Configuration>", "<Configuration/>", "<!DOCTYPE Configuration SYSTEM 'file:///unread'><Configuration/>",
            "<Configuration><Appenders/><Loggers><Root level=\"error\"/></Loggers></Configuration>").forEach {
            Files.writeString(xml, it)
            assertThrows(Exception::class.java) { FixtureAuditLogging.prepare(root, "server") }
            assertEquals(logbegone, Files.readString(root.resolve("config/logbegone.toml")))
        }
    }
}
