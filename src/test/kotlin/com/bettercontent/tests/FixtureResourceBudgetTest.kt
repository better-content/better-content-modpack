package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag

/** Minecraft-free guards. The only child VM is a bounded 64MiB JDK flag-acceptance check. */
@Tag("fast")
class FixtureResourceBudgetTest {
    @Test fun preservesHeapGcPropertiesAndQuotedPropertyContents() {
        val arguments = "-Xms1G -Xmx4G -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dnote=\"keep -XX:ActiveProcessorCount=99 inside\""
        val normalized = normalizeFixtureProcessorBudget(arguments, 4)
        assertEquals(arguments + " -XX:ActiveProcessorCount=4", normalized)
        assertEquals(normalized, normalizeFixtureProcessorBudget(normalized, 4))
    }

    @Test fun replacesAllStandaloneCountsIncludingQuotedFlagsWithoutTouchingProperties() {
        val arguments = "-Xms1G -XX:ActiveProcessorCount=24 \"-XX:ActiveProcessorCount=12\" '-XX:ActiveProcessorCount=16' -Xmx4G -Dkeep=-XX:ActiveProcessorCount=7"
        assertEquals("-Xms1G -Xmx4G -Dkeep=-XX:ActiveProcessorCount=7 -XX:ActiveProcessorCount=4",
            normalizeFixtureProcessorBudget(arguments, 4))
    }

    @Test fun rejectsInvalidBudgetAndMalformedQuotedArguments() {
        assertThrows(IllegalArgumentException::class.java) { normalizeFixtureProcessorBudget("-Xmx4G", 0) }
        assertThrows(IllegalArgumentException::class.java) { normalizeFixtureProcessorBudget("-Xmx4G", -1) }
        assertThrows(IllegalArgumentException::class.java) { normalizeFixtureProcessorBudget("-Dnote=\"unterminated", 4) }
    }

    @Test fun budgetEvidenceContainsOnlyBoundedNonSecretConfigurationAndPreservesEachHeapProfile() {
        for (heap in listOf("4G", "12G", "6G", "16G")) {
            val arguments = normalizeFixtureProcessorBudget("-Xms1G -Xmx$heap -XX:+UseG1GC -Dsecret=DO_NOT_RECORD", 4)
            val evidence = fixtureResourceBudgetEvidence("client", arguments, 4)
            assertEquals("1G", evidence["minimum_heap"])
            assertEquals(heap, evidence["maximum_heap"])
            assertEquals(4, evidence["jvm_processor_count"])
            assertEquals("launch_arguments", evidence["measurement"])
            assertEquals(listOf("-XX:+UseG1GC"), evidence["gc_flags"])
            assertFalse(evidence.toString().contains("DO_NOT_RECORD"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            fixtureResourceBudgetEvidence("client", "-Xmx4G -XX:ActiveProcessorCount=4 -XX:ActiveProcessorCount=4", 4)
        }
    }

    @Test fun freshFixtureServerBudgetIsIdempotentPreservesCommentsHeapsAndProperties() = sandbox { directory ->
        val file = directory.resolve("user_jvm_args.txt")
        val original = "# Example -Xmx3G -XX:ActiveProcessorCount=42\n-Xms1G\n-Xmx6G\n-XX:+UseG1GC\n-Dfixture=unchanged\n-XX:ActiveProcessorCount=24\n"
        Files.writeString(file, original)
        configureFixtureServerProcessorBudget(directory)
        val configured = Files.readString(file)
        assertTrue(configured.startsWith("# Example -Xmx3G -XX:ActiveProcessorCount=42\n"))
        val active = activeFixtureServerArguments(configured)
        assertEquals(listOf("-Xms1G", "-Xmx6G", "-XX:+UseG1GC", "-Dfixture=unchanged", "-XX:ActiveProcessorCount=8"), fixtureJvmTokens(active))
        assertEquals("6G", fixtureResourceBudgetEvidence("dedicated-server", active, 8)["maximum_heap"])
        configureFixtureServerProcessorBudget(directory)
        assertEquals(configured, Files.readString(file))
    }

    @Test fun bothClientLaunchPathsAndServerRestartsRecordRealBudgetWithoutChangingRenderingOrCriteria() {
        val source = Files.readString(Path.of("src/test/kotlin/com/bettercontent/tests/RuntimeFixtures.kt"))
        val quickplay = source.substringAfter("fun launchQuickPlayWorld(").substringBefore("private fun launch(connection:")
        val normal = source.substringAfter("private fun launch(connection:").substringBefore("private fun recordClientResourceBudget(")
        for (path in listOf(quickplay, normal)) {
            assertTrue(path.contains("normalizeFixtureProcessorBudget("))
            assertTrue(path.contains("recordClientResourceBudget("))
        }
        assertTrue(normal.contains("\"--jvm-args=\$jvmArgs\""))
        assertTrue(source.contains("\"LP_NUM_THREADS\" to \"2\""))
        assertTrue(source.contains("\"LIBGL_ALWAYS_SOFTWARE\" to \"1\""))
        assertTrue(source.contains("\"--resolution\", \"1280x720\""))
        assertTrue(source.contains("\"maxFps:180\" to \"maxFps:30\""))
        assertTrue(source.contains("DEFAULT_CLIENT_JVM_ARGS = \"-Xms2G -Xmx12G\""))
        assertTrue(source.contains("configureFixtureServerProcessorBudget(server)"))
        assertEquals(2, Regex(Regex.escape("recordServerResourceBudget()")).findAll(source.substringBefore("private fun recordServerResourceBudget()")).count())
        for (forbidden in listOf("\"LP_NUM_THREADS\" to \"0\"", "LP_NO_RAST", "MESA_NO_ERROR", "LIBGL_NO_DRAWARRAYS"))
            assertFalse(source.contains(forbidden), forbidden)
        val multiplayer = Files.readString(Path.of("src/test/kotlin/com/bettercontent/tests/MultiplayerRuntimeTest.kt"))
        assertTrue(multiplayer.contains("CLIENT_JVM_ARGS = \"-Xms1G -Xmx4G\""))
        // Native simultaneous-player/TPS checks remain owned by the unchanged multiplayer suite.
        assertTrue(multiplayer.contains("threeSurvivalPlayersStartCampaigns"))
        assertTrue(multiplayer.contains("requirePlayersOnline"))
    }

    @Test fun actualSupportedJdkAcceptsBothSchedulingCountsWithoutLaunchingMinecraft() = sandbox { directory ->
        assertTrue(Runtime.version().feature() >= 17)
        val java = Path.of(System.getProperty("java.home"), "bin", "java")
        for (count in listOf(4, 8)) {
            val output = directory.resolve("jdk-$count.txt")
            val builder = ProcessBuilder(java.toString(), "-Xms16m", "-Xmx64m", "-XX:ActiveProcessorCount=$count", "-XX:+PrintFlagsFinal", "-version")
                .redirectErrorStream(true).redirectOutput(output.toFile())
            // Isolate this flag ABI probe, not the actual fixture JVMs/environment.
            builder.environment().remove("JAVA_TOOL_OPTIONS")
            builder.environment().remove("_JAVA_OPTIONS")
            builder.environment().remove("JDK_JAVA_OPTIONS")
            val process = builder.start()
            try {
                assertTrue(process.waitFor(15, TimeUnit.SECONDS), "bounded JDK flag acceptance timeout")
                assertEquals(0, process.exitValue(), "JDK rejected supported processor-count flag")
                assertTrue(Regex("\\bActiveProcessorCount\\s+=\\s+$count\\b").containsMatchIn(Files.readString(output)))
            } finally {
                if (process.isAlive) {
                    process.destroyForcibly()
                    assertTrue(process.waitFor(5, TimeUnit.SECONDS), "owned ABI probe did not stop")
                }
            }
        }
    }

    private fun sandbox(test: (Path) -> Unit) {
        val root = Path.of("build", "fixture-budget-test").toAbsolutePath()
        Files.createDirectories(root)
        val directory = Files.createTempDirectory(root, "owned-")
        try { test(directory) }
        finally { Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
}
