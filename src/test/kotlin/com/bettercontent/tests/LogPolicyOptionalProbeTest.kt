package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

@Tag("fast")
class LogPolicyOptionalProbeTest {
    private fun record(message: String, logger: String, severity: String = "DEBUG", thread: String = "main") =
        "[12:00:00] [$thread/$severity] [$logger]: $message"

    private fun jarjar() = record(
        "Failed to load resource META-INF/jarjar/metadata.json from nativeFixture, it does not contain dependency information.",
        "net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileDependencyLocator/",
    )

    private fun netty(array: Boolean = false): List<String> = if (array) listOf(
        record("jdk.internal.misc.Unsafe.allocateUninitializedArray(int): unavailable", "io.netty.util.internal.PlatformDependent0/"),
        "java.lang.IllegalAccessException: class io.netty.util.internal.PlatformDependent0\$7 " +
            "(in module io.netty.common) cannot access class jdk.internal.misc.Unsafe " +
            "(in module java.base) because module java.base does not export jdk.internal.misc to module io.netty.common",
        "\tat io.netty.util.internal.PlatformDependent0\$7.run(PlatformDependent0.java:410) ~[netty-common-4.1.82.Final.jar:4.1.82.Final]",
    ) else listOf(
        record("direct buffer constructor: unavailable", "io.netty.util.internal.PlatformDependent0/"),
        "java.lang.UnsupportedOperationException: Reflective setAccessible(true) disabled",
        "\tat io.netty.util.internal.ReflectionUtil.trySetAccessible(ReflectionUtil.java:31) ~[netty-common-4.1.82.Final.jar:4.1.82.Final]",
    )

    private fun findings(root: Path, lines: List<String>): List<LogPolicy.Finding> {
        val log = root.resolve("probes.log")
        log.writeText(lines.joinToString("\n"))
        return LogPolicy.findings(listOf(log))
    }

    @Test
    fun exactCaughtNativeProbesDoNotAcquireOccurrenceBudgets(@TempDir root: Path) {
        val probes = netty() + netty(true) + jarjar()
        assertTrue(findings(root, List(25) { probes }.flatten()).isEmpty())
        // The production log formatter omits the source fixture's trailing logger slash.
        assertTrue(findings(root, probes.map { it.replace("/]:", "]:") }).isEmpty())
        assertTrue(findings(root, probes.map { it.replace("[12:00:00]", "[18Oct2026 12:00:00.123]") }).isEmpty())
    }

    @Test
    fun optionalJarjarIdentityCannotHideRequiredLoadsMalformedDataOrErrors(@TempDir root: Path) {
        val variants = listOf(
            jarjar().replace("/DEBUG]", "/ERROR]"),
            jarjar().replace("/DEBUG]", "/FATAL]"),
            jarjar().replace("main/DEBUG", "Server thread/DEBUG"),
            jarjar().replace("AbstractJarFileDependencyLocator/", "OtherLocator/"),
            jarjar().replace("metadata.json", "required.json"),
            jarjar().replace("nativeFixture", "nativeFixture AssertionError: invariant"),
            jarjar().replace("nativeFixture", "nativeFixture OutOfMemoryError: heap"),
            jarjar().replace("it does not contain dependency information.", "JSON is malformed."),
            jarjar() + " java.lang.AssertionError: failure",
            jarjar().replace("12:00:00", "99:00:00"),
        )
        variants.forEach { assertEquals(1, findings(root, listOf(it)).size, it) }
    }

    @Test
    fun caughtNettyContextDoesNotCrossRecordsOrLoseNativeStackProvenance(@TempDir root: Path) {
        listOf(false, true).forEach { array ->
            val original = netty(array)
            val variants = listOf(
                original.drop(1), original.take(2),
                listOf(original[0], "", original[1], original[2]),
                listOf(original[0], record("new record", "unrelated.Logger", "INFO"), original[1], original[2]),
                listOf(original[0], original[1], "\tat unrelated.Probe.run(Probe.java:1)"),
                listOf(original[0], original[1] + " extra payload", original[2]),
            )
            variants.forEach { assertTrue(findings(root, it).isNotEmpty(), it.toString()) }
            listOf(
                "/DEBUG]" to "/ERROR]", "/DEBUG]" to "/FATAL]", "/DEBUG]" to "/INFO]",
                "main/DEBUG" to "Server thread/DEBUG",
                "io.netty.util.internal.PlatformDependent0/" to "other.Logger/",
                "12:00:00" to "99:00:00",
            ).forEach { (old, replacement) ->
                val changed = listOf(original[0].replace(old, replacement)) + original.drop(1)
                assertTrue(findings(root, changed).isNotEmpty(), changed.toString())
            }
        }
    }

    @Test
    fun diagnosticContextsNeverSuppressUncaughtErrorsAssertionsOrFailedOperations(@TempDir root: Path) {
        listOf(
            "java.lang.AssertionError: invariant", "java.lang.OutOfMemoryError: heap",
            "java.lang.Error: catastrophic", "java.lang.Exception: uncaught",
            "Caused by: java.lang.IllegalStateException: failure",
            "Exception in thread \"server\" java.lang.IllegalStateException: uncaught",
            "Failed to load required registry data", "Error loading KubeJS script", "Corrupted world data",
        ).forEach { failure ->
            val lines = netty() + failure
            assertTrue(findings(root, lines).any { it.line == lines.size && it.text == failure }, failure)
        }
        val required = record("Failed to read required save data", "example.Logger")
        assertEquals(listOf(required), findings(root, listOf(required)).map { it.text })
        val error = record("direct buffer constructor: unavailable", "io.netty.util.internal.PlatformDependent0/", "ERROR")
        assertEquals(listOf(error), findings(root, listOf(error)).map { it.text })
    }
}
