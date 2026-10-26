package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class LogPolicyCorruptionWordsTest {
    @TempDir lateinit var temp: Path

    // Actual successful Mixin DEBUG message forms from the retained build-468 server log.
    private val classMessages = listOf(
        "Inner class tech/alexnijjar/endermanoverhaul/mixins/forge/common/CorruptedShieldItemMixin\$1 in tech/alexnijjar/endermanoverhaul/mixins/forge/common/CorruptedShieldItemMixin on tech/alexnijjar/endermanoverhaul/common/items/tools/CorruptedShieldItem gets unique name tech/alexnijjar/endermanoverhaul/common/items/tools/CorruptedShieldItem\$Anonymous\$c97374a5923f45c892ce33d7c046380c",
        "Mixing common.CorruptedShieldItemMixin from endermanoverhaul.mixins.json into tech.alexnijjar.endermanoverhaul.common.items.tools.CorruptedShieldItem",
    )

    @Test fun corruptionInClassIdentifiersIsNotAReportedFailedOperation() {
        val log = temp.resolve("classes.log")
        Files.write(log, classMessages.map { "[19:38:49] [main/DEBUG] [mixin]: $it" })
        assertTrue(LogPolicy.findings(listOf(log)).isEmpty())
        Files.write(log, classMessages.map { "[19:38:49] [main/ERROR] [mixin]: $it" })
        assertEquals(listOf(1, 2), LogPolicy.findings(listOf(log)).map { it.line }, "ERROR severity must still fail")
    }

    @Test fun realCorruptionAndMixinFailuresRemainFatalEvenBelowErrorSeverity() {
        val messages = listOf(
            "Corrupted chunk save data",
            "World save corruption detected",
            "WORLD_DATA_CORRUPTED",
            "registry corruption detected",
            "mixin failed to apply",
            "Saved data is corrupt.",
            "Mixing CorruptedShieldItemMixin failed to load target",
        )
        val log = temp.resolve("operations.log")
        Files.write(log, messages.map { "[19:38:49] [main/WARN] [mixin]: $it" })
        assertEquals((1..messages.size).toList(), LogPolicy.findings(listOf(log)).map { it.line })
    }
}
