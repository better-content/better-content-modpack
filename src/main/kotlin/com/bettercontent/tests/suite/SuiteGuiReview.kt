package com.bettercontent.tests.suite

import java.nio.file.Path
import java.nio.file.Files
import java.time.Instant
import javax.imageio.ImageIO

/** Additional screen coverage; does not replace the existing independent visual-session contract. */
internal fun validateGuiChecklist(m: SuiteManifest, path: Path, catalogue: String,
    sessionId: String, reviewer: String, sessionPath: Path) {
    val receipt = suiteMapper.readValue(path.toFile(), GuiReviewReceipt::class.java)
    val doc = suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(receipt)
    val session = suiteMapper.readTree(sessionPath.toFile())
    val started = Instant.parse(session.path("started_at").asText())
    val completed = Instant.parse(session.path("completed_at").asText())
    val input = session.path("input_log")
    val inputPath = readBoundFile(sessionPath.parent, BoundFile(input.path("path").asText(), input.path("sha256").asText())).toRealPath()
    val inputLines = Files.lines(inputPath).use { it.count() }
    val fixture = sessionPath.parent.toRealPath()
    require(doc.path("schema").asText() == "bc.playtest_gui_review.v1" && doc.path("status").asText() == "passed" &&
        doc.path("catalogue_sha256").asText() == catalogue && doc.path("session_id").asText() == sessionId &&
        doc.path("reviewer").asText() == reviewer && reviewer.isNotBlank()) { "GUI review is not bound to independent visual session" }
    val screens = m.rosters.single { it.kind == "gui" }.entries.keys
    val expected = screens.flatMap { screen -> m.profile.visualProfiles.map { screen to it } }.toSet()
    val captures = doc.path("captures").toList()
    val keys = captures.map { it.path("screen").asText() to it.path("profile").asText() }
    require(keys.size == keys.distinct().size && keys.toSet() == expected) { "GUI screen/profile coverage incomplete or duplicated" }
    val screenshots = mutableSetOf<String>()
    captures.forEach { capture ->
        val dimensions = when (capture.path("profile").asText()) {
            "1280x720@2" -> 1280 to 720
            "1920x1080@3" -> 1920 to 1080
            else -> error("unsupported GUI profile")
        }
        val snapshot = suiteMapper.treeToValue(capture.path("screenshot"), BoundFile::class.java)
        require(screenshots.add(snapshot.sha256)) { "GUI views must be independently captured" }
        val imagePath = readBoundFile(path.parent, snapshot).toRealPath()
        require(imagePath.startsWith(fixture) && imagePath.fileName.toString().endsWith(".png", ignoreCase = true)) { "GUI image is outside the owned visual fixture" }
        val image = ImageIO.read(imagePath.toFile())
        require(image != null && image.width == dimensions.first && image.height == dimensions.second &&
            capture.path("observed_gui_scale").asInt() == capture.path("profile").asText().substringAfter('@').toInt()) { "wrong real-client capture dimensions or observed GUI scale" }
        val captured = Instant.parse(capture.path("captured_at").asText())
        require(!captured.isBefore(started) && !captured.isAfter(completed)) { "GUI image is outside capture session" }
        require(capture.path("capture_kind").asText() == "live-client-screenshot" &&
            capture.path("input_kind").asText() == "authentic" && capture.path("approved").isBoolean && capture.path("approved").asBoolean()) { "screenshot or interaction not actually reviewed" }
        val checks = capture.path("checks")
        listOf("no_clipping", "no_overlap", "textures_present", "labels_readable", "tooltips_readable", "navigation_works", "controls_work").forEach {
            require(checks.path(it).isBoolean && checks.path(it).asBoolean()) { "GUI check missing/failed: $it" }
        }
        val captureInput = readBoundFile(path.parent, suiteMapper.treeToValue(capture.path("input_log"), BoundFile::class.java)).toRealPath()
        require(captureInput == inputPath) { "GUI interaction log differs from independently reviewed session" }
        require(capture.path("input_first_line").asLong() > 0 && capture.path("input_last_line").asLong() >= capture.path("input_first_line").asLong() &&
            capture.path("input_last_line").asLong() <= inputLines) { "GUI input evidence range missing or outside raw log" }
    }
}
