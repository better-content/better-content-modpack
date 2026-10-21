package com.bettercontent.tests

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Narrow source-copy invariant, not evidence that any gameplay path or native logger ran. */
@Tag("fast")
class PlaytestLoggingContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root", ".")).toAbsolutePath().normalize()
    private val sources = root.parent.resolve("mod_source")
    private fun normalizePackage(text: String) = text.replaceFirst(Regex("(?m)^package [^;]+;"), "package OWNER;")
    private fun files(repository: String, area: String, filename: String): List<Path> {
        val directory = sources.resolve(repository).resolve("src/$area")
        assertTrue(Files.isDirectory(directory), "missing source area: $directory")
        return Files.walk(directory).use { paths -> paths.filter { Files.isRegularFile(it) && it.fileName.toString() == filename }.toList() }
    }

    @Test fun everyActiveModOwnsTheSameTestedHelperWithDistinctRuntimeClassIdentity() {
        val canonical = files("better-gameplay-notices", "main", "PlaytestLog.java").single()
        val canonicalTest = files("better-gameplay-notices", "test", "PlaytestLogContractTest.java").single()
        val implementation = normalizePackage(Files.readString(canonical))
        val contract = normalizePackage(Files.readString(canonicalTest))
        val packages = mutableSetOf<String>()
        val manifest = ObjectMapper().readTree(root.resolve("gradle/active-custom-mods.json").toFile()).path("mods")
        assertTrue(manifest.isArray && !manifest.isEmpty)
        for (mod in manifest) {
            val repo = mod.path("repository").asText()
            val helpers = files(repo, "main", "PlaytestLog.java")
            val tests = files(repo, "test", "PlaytestLogContractTest.java")
            assertEquals(1, helpers.size, "$repo must own exactly one helper source")
            assertEquals(1, tests.size, "$repo must own its actual helper contract test")
            val source = Files.readString(helpers.single())
            assertEquals(implementation, normalizePackage(source), "$repo silently forked the shared bounded behavior")
            assertEquals(contract, normalizePackage(Files.readString(tests.single())), "$repo helper tests diverged")
            val owner = requireNotNull(Regex("(?m)^package ([^;]+);").find(source)).groupValues[1]
            assertNotEquals("com.bettercontent.playtest", owner, "$repo ships the template package")
            assertTrue(packages.add(owner), "$repo shares helper FQCN with another mod: $owner.PlaytestLog")
        }
    }

    @Test fun productionProfileKeepsScopedRoutingAndNonDeletingRollover() {
        val xml = Files.readString(root.resolve("config/better-content-log4j2.xml"))
        assertTrue(xml.contains("""<Logger name="bc.playtest" level="${'$'}{sys:bc.playtest.level:-DEBUG}" additivity="true"/>"""))
        assertTrue(xml.contains("""<Root level="info">"""))
        assertTrue(xml.contains("""<ThresholdFilter level="INFO" onMatch="NEUTRAL" onMismatch="DENY"/>"""))
        assertTrue(xml.contains("""<SizeBasedTriggeringPolicy size="32 MB"/>"""))
        assertTrue(xml.contains("""<DefaultRolloverStrategy fileIndex="nomax"/>"""))
        assertFalse(xml.contains("<Delete"), "logging profile cannot dispose of retained archives")
        assertTrue(Files.readString(root.resolve("user_jvm_args.txt")).contains("-Dlog4j.configurationFile=config/better-content-log4j2.xml"))
    }
}
