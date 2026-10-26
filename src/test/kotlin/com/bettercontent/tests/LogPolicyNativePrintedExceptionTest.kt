package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@Tag("fast")
class LogPolicyNativePrintedExceptionTest {
    @TempDir lateinit var temp: Path

    @Test fun nativePrintStackTraceDoesNotBecomeSuccessAtInfoSeverity() {
        val log = temp.resolve("native.log")
        val messages = listOf(
            // Actual source-site envelope retained from the build-468 native server.
            "[19:39:56] [Server thread/INFO] [STDERR]: [mokiyoki.enhancedanimals.GeneticAnimals:addAnimalsToVillages:206]: java.lang.UnsupportedOperationException",
            "[19:39:56] [Server thread/INFO] [STDERR/]: [mokiyoki.enhancedanimals.GeneticAnimals:addAnimalsToVillages:206]: java.lang.UnsupportedOperationException: immutable list",
            "[19:39:56] [Server thread/INFO] [STDOUT]: [example.Owner:operation:12]: java.lang.IllegalStateException: failed operation",
            "[19:39:56] [Server thread/INFO] [minecraft/STDERR]: [example.Owner:operation:12]: java.lang.UnsupportedOperationException",
            "java.lang.UnsupportedOperationException",
            "com.electronwill.nightconfig.core.io.ParsingException: Invalid bare key: [\"Variant",
            "[19:39:56] [Server thread/INFO] [STDERR]: java.lang.UnsupportedOperationException",
        )
        Files.write(log, messages)
        assertEquals((1..messages.size).toList(), LogPolicy.findings(listOf(log)).map { it.line })
    }

    @Test fun ordinarySourceSiteOutputAndExceptionIdentifiersAreNotExceptions() {
        val log = temp.resolve("ordinary.log")
        Files.write(log, listOf(
            "[19:39:56] [Server thread/INFO] [STDERR]: [example.Owner:operation:12]: Registered 5 village entries",
            "[19:39:56] [main/DEBUG] [mixin]: Mixing ExceptionHandlerMixin into example.Target",
            "[19:39:56] [main/DEBUG] [mixin]: Loaded example.IllegalStateException class",
        ))
        assertTrue(LogPolicy.findings(listOf(log)).isEmpty())
    }
}
