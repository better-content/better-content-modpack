package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Support status is retained; this is not permission to ignore failed mixins. */
@Tag("fast")
class EmbeddiumSupportPolicyTest {
    @TempDir lateinit var temp: Path

    private val triad = listOf(
        "[08:30:52] [Render thread/ERROR] [Embeddium-MixinTaintDetector]: Mod mixin into Embeddium internals detected. This instance is now tainted. The Embeddium team does not provide any guarantee of support for issues encountered while such mods are installed.",
        "[08:30:52] [Render thread/WARN] [Embeddium-MixinTaintDetector]: Mod(s) [oculus] are modifying Embeddium class me.jellysquid.mods.sodium.client.gui.SodiumGameOptions, which may cause instability.",
        "[08:30:52] [Render thread/DEBUG] [mixin]: Mixing options.MixinSodiumGameOptions from mixins.oculus.compat.sodium.json into me.jellysquid.mods.sodium.client.gui.SodiumGameOptions",
    )

    private fun log(lines: List<String>): Path = temp.resolve("case.log").also { Files.write(it, lines) }

    @Test fun detectorAndOculusPinsRequireExplicitSupportPolicyRequalification() {
        // Reviewed MixinTaintDetector.class SHA256:
        // 7f69a86616f322eddf554ad8d7c095da3bd2603b573bfc5ab46328b3dbdafe19
        // preApply: error(String) at79; warn at129; CRASH-only throw at158;
        // default enforcement WARN. Other error(String,Throwable) paths still fail.
        val pins = mapOf(
            "embeddium" to "bb2fa8f3e493af16af9160d049f96c614a1faf2f",
            "oculus" to "984f774e71790deaec674c7587bd24e0711871b2",
        )
        for ((mod, sha1) in pins) {
            val manifest = Files.readString(Path.of("mods/$mod.pw.toml"))
            assertTrue(manifest.contains("hash = \"$sha1\""), "$mod support semantics need requalification")
        }
    }

    @Test fun exactNativeTriadRetainsSupportEvidenceWithoutClaimingFailure() {
        val path = log(listOf("[08:30:51] [main/INFO] [loader]: Starting") + triad)
        assertTrue(LogPolicy.findings(listOf(path)).isEmpty())
        val support = LogPolicy.supportPolicies(listOf(path, path)).single()
        assertEquals(path, support.path)
        assertEquals(2, support.line)
        assertEquals("embeddium_oculus_support_warranty", support.policy)
        assertEquals(triad, support.records)
        assertEquals(triad, Files.readAllLines(path).drop(1))
    }

    @Test fun incompleteReorderedInterleavedOrUnconfirmedHeadersFail() {
        val variants = listOf(
            triad.take(1), triad.take(2), listOf(triad[0], triad[2], triad[1]),
            listOf(triad[0], "[08:30:52] [Render thread/INFO] [other]: Interleaved", triad[1], triad[2]),
            listOf(triad[0], "", triad[1], triad[2]),
            listOf(triad[0], triad[1], "[08:30:52] [Render thread/DEBUG] [mixin]: Applying another mixin"),
        )
        for (variant in variants) {
            val path = log(variant)
            assertTrue(LogPolicy.supportPolicies(listOf(path)).isEmpty(), variant.toString())
            assertTrue(LogPolicy.findings(listOf(path)).any { it.line == 1 }, variant.toString())
        }
    }

    @Test fun everyEnvelopeMessageAndContextMutationRejectsSupportClassification() {
        val mutations = listOf(
            0 to ("/ERROR]" to "/FATAL]"),
            0 to ("Embeddium-MixinTaintDetector" to "other-detector"),
            0 to ("installed." to "installed. Failed to load mixin"),
            0 to ("now tainted." to "now TAINTED."),
            1 to ("[oculus]" to "[unknown]"),
            1 to ("[oculus]" to "[oculus, other]"),
            1 to ("SodiumGameOptions" to "SodiumWorldRenderer"),
            1 to ("/WARN]" to "/ERROR]"),
            1 to ("Embeddium-MixinTaintDetector" to "other-detector"),
            2 to ("/DEBUG]" to "/WARN]"),
            2 to ("[mixin]" to "[other]"),
            2 to ("options.MixinSodiumGameOptions" to "options.UnknownMixin"),
            2 to ("mixins.oculus.compat.sodium.json" to "mixins.other.json"),
            2 to ("SodiumGameOptions" to "SodiumWorldRenderer"),
            2 to ("Mixing " to "Failed to load "),
            2 to ("08:30:52" to "08:30:53"),
        )
        for ((index, replacement) in mutations) {
            val variant = triad.toMutableList()
            variant[index] = variant[index].replace(replacement.first, replacement.second)
            val path = log(variant)
            assertTrue(LogPolicy.supportPolicies(listOf(path)).isEmpty(), "$index $replacement")
            assertTrue(LogPolicy.findings(listOf(path)).any { it.line == 1 }, "$index $replacement")
        }
        for (mutation in listOf(
            triad.map { it.replace("Render thread", "main") },
            triad.map { it.replace("08:30:52", "99:99:99") },
            triad.map { it.removePrefix("[08:30:52] ") },
            triad.map { "prefix $it" },
            triad.map { "$it " },
        )) {
            val path = log(mutation)
            assertTrue(LogPolicy.supportPolicies(listOf(path)).isEmpty(), mutation.toString())
            assertFalse(LogPolicy.findings(listOf(path)).isEmpty(), mutation.toString())
        }
    }

    @Test fun rawThrowableOrFrameOnAnyTriadRecordPreventsClassification() {
        for (after in 0..2) {
            for (tail in listOf("java.lang.IllegalStateException: taint rejected",
                "\tat example.Mixin.apply(Mixin.java:42)", "Caused by: java.lang.LinkageError: failed mixin")) {
                val variant = triad.toMutableList().apply { add(after + 1, tail) }
                val path = log(variant)
                assertTrue(LogPolicy.supportPolicies(listOf(path)).isEmpty(), variant.toString())
                assertTrue(LogPolicy.findings(listOf(path)).any { it.line == 1 }, variant.toString())
            }
        }
    }

    @Test fun confirmedSupportNeverConcealsAnySubsequentFailure() {
        for (failure in listOf(
            "[08:30:52] [Render thread/ERROR] [mixin]: Failed to load required mixin",
            "[08:30:52] [Render thread/FATAL] [Embeddium-MixinTaintDetector]: Mod rejected",
            "[08:30:52] [Render thread/ERROR] [Embeddium-MixinTaintDetector]: Error encountered getting mixins for class info",
            "[08:30:52] [Render thread/INFO] [STDERR]: [example.Mixin:apply:42]: java.lang.IllegalStateException: taint rejected",
            "[08:30:52] [Render thread/WARN] [mixin]: Mixin apply failed",
            triad[0], // a second unpaired warranty header is still an ERROR
        )) {
            val path = log(triad + failure)
            assertEquals(listOf(4), LogPolicy.findings(listOf(path)).map { it.line }, failure)
        }
    }
}
