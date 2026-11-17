package com.bettercontent.tests.suite

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** These generated PNGs test the contract parser only; they are never native visual acceptance. */
class SuiteGuiReviewTest {
    @TempDir lateinit var root: Path
    private val catalogue = "c".repeat(64)
    private fun writeFixture(): Triple<SuiteManifest, GuiReviewReceipt, Path> {
        val manifest = fixtureManifest(root)
        val input = root.resolve("input.log"); Files.writeString(input, "unit fixture input one\nunit fixture input two\n")
        val inputRef = BoundFile(input.toString(), manifestFingerprint(input))
        val session = root.resolve("session.json")
        suiteMapper.writeValue(session.toFile(), mapOf("started_at" to "2026-01-02T00:00:00Z", "completed_at" to "2026-01-02T00:01:00Z", "input_log" to inputRef))
        val captures = manifest.profile.visualProfiles.mapIndexed { i, profile ->
            val width = if (i == 0) 1280 else 1920; val height = if (i == 0) 720 else 1080
            val file = root.resolve("unit-parser-image-$i.png")
            ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", file.toFile())
            GuiCapture("test:root", profile, BoundFile(file.toString(), manifestFingerprint(file)), "2026-01-02T00:00:30Z", i + 2,
                "live-client-screenshot", "authentic", true,
                listOf("no_clipping", "no_overlap", "textures_present", "labels_readable", "tooltips_readable", "navigation_works", "controls_work").associateWith { true }, inputRef, 1, 2)
        }
        return Triple(manifest, GuiReviewReceipt("bc.playtest_gui_review.v1", "passed", catalogue, "unit-session", "independent-fixture-reviewer", captures), session)
    }
    private fun check(manifest: SuiteManifest, receipt: GuiReviewReceipt, session: Path) {
        val path = root.resolve("gui.json"); suiteMapper.writeValue(path.toFile(), receipt)
        validateGuiChecklist(manifest, path, catalogue, "unit-session", "independent-fixture-reviewer", session)
    }
    @Test fun `complete parser fixture does not itself create visual acceptance`() {
        val (manifest, receipt, session) = writeFixture(); check(manifest, receipt, session)
        assertFalse(Files.exists(root.resolve("playtest-qualified.json")))
    }
    @Test fun `missing screen profile cannot reuse another screenshot`() {
        val (manifest, receipt, session) = writeFixture()
        assertThrows(IllegalArgumentException::class.java) { check(manifest, receipt.copy(captures = receipt.captures.take(1)), session) }
        assertThrows(IllegalArgumentException::class.java) { check(manifest, receipt.copy(captures = receipt.captures.map { it.copy(screenshot = receipt.captures.first().screenshot) }), session) }
    }
    @Test fun `out of session capture incorrect GUI scale or invented input range fails`() {
        val (manifest, receipt, session) = writeFixture()
        listOf(receipt.captures.first().copy(capturedAt = "2026-01-03T00:00:00Z"),
            receipt.captures.first().copy(observedGuiScale = 1), receipt.captures.first().copy(inputLastLine = 999)).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { check(manifest, receipt.copy(captures = listOf(bad, receipt.captures.last())), session) }
        }
    }
}
