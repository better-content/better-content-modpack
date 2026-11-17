package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.drivers.CaseDriver
import com.bettercontent.tests.suite.drivers.DriverAction
import com.bettercontent.tests.suite.drivers.DriverObservation
import com.bettercontent.tests.suite.drivers.DriverStep
import com.bettercontent.tests.suite.drivers.validateTerminalSequence
import com.fasterxml.jackson.databind.JsonNode
import kotlin.math.abs
import kotlin.math.hypot

/** One real Survival inventory-open + typed framebuffer source per epoch/profile. Not visual approval. */
internal object InventoryScreenControlDriver : CaseDriver {
    override val caseId = "control.client.inventory-screen"
    private data class Target(val actor: String, val dimension: String, val profile: String, val holdId: String)
    private fun parse(input: JsonNode): Target {
        require(input.isObject && input.fieldNames().asSequence().toSet() == setOf("player_uuid", "dimension",
            "input_profile", "hold_id")) { "exact reviewed inventory screen source fields required" }
        fun text(name: String): String = input.path(name).also { require(it.isTextual) }.asText()
        val actor = text("player_uuid"); val dimension = text("dimension")
        val profile = text("input_profile"); val holdId = text("hold_id")
        require(runCatching { java.util.UUID.fromString(actor).toString() == actor }.getOrDefault(false) &&
            Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(dimension) &&
            profile in setOf("1280x720@2", "1920x1080@3") &&
            Regex("[A-Za-z0-9._-]{1,64}").matches(holdId)) {
            "inventory screen actor/profile/input hold unreviewed"
        }
        return Target(actor, dimension, profile, holdId)
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val t = parse(parameters)
        val actor = suiteMapper.createObjectNode().put("player_uuid", t.actor)
        val binding = suiteMapper.createObjectNode().put("player_uuid", "@observed-player-uuid")
            .put("dimension", t.dimension).put("hold_id", t.holdId)
            .put("input_runtime_sha256", INPUT_RUNTIME_MARKER)
        return listOf(
            DriverStep("gui.server_before", DriverAction("player.snapshot", actor.deepCopy())),
            DriverStep("gui.client_before", DriverAction("client.snapshot", suiteMapper.createObjectNode(), 100, "client")),
            DriverStep("gui.inventory_hold", DriverAction("client.input_hold", binding.deepCopy().apply {
                put("kind", "inventory"); put("profile", t.profile); put("max_ticks", 6)
            }, 30, "client")),
            DriverStep("gui.inventory_release", DriverAction("client.input_release", binding.deepCopy(), 80, "client")),
            DriverStep("gui.inventory_status", DriverAction("client.input_status", binding.deepCopy(), 80, "client")),
            DriverStep("gui.client_open", DriverAction("client.snapshot", suiteMapper.createObjectNode(), 100, "client")),
            DriverStep("gui.frame", DriverAction("client.framebuffer",
                suiteMapper.createObjectNode().put("profile", t.profile), 160, "client")),
            DriverStep("gui.server_after", DriverAction("player.snapshot", actor.deepCopy())))
    }
    private fun position(row: JsonNode): Triple<Double, Double, Double> {
        val nums = listOf("x", "y", "z").map { row.path(it).also { n -> require(n.isNumber && n.asDouble().isFinite()) }.asDouble() }
        return Triple(nums[0], nums[1], nums[2])
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val t = parse(parameters)
        require(validateTerminalSequence(plan(parameters), observations) == null) {
            "genuine inventory keyboard + independent server, client, frame source chain incomplete"
        }
        val beforeServer = observations[0].payload; val beforeClient = observations[1].payload
        val hold = observations[2].payload; val release = observations[3].payload
        val status = observations[4].payload; val opened = observations[5].payload
        val frame = observations[6].payload; val afterServer = observations[7].payload
        require(validateAuthenticInventoryInput(hold, release, status, t.actor, t.dimension,
            t.profile, t.holdId, hold.path("input_runtime_sha256").asText())) {
            "inventory screen was not opened by bound real OS keyboard input and key-up"
        }
        val before = position(beforeServer); val after = position(afterServer)
        require(listOf(beforeServer, afterServer).all { row -> row.path("uuid").asText() == t.actor &&
            row.path("dimension").asText() == t.dimension && row.path("mode").asText() == "survival" &&
            row.path("on_ground").isBoolean && row.path("on_ground").asBoolean() &&
            row.path("server_tick").isIntegralNumber && row.path("server_tick").asLong() >= 0 } &&
            afterServer.path("server_tick").asLong() >= beforeServer.path("server_tick").asLong() &&
            afterServer.path("server_tick").asLong() - beforeServer.path("server_tick").asLong() <= 100 &&
            hypot(after.first - before.first, after.third - before.third) <= 0.5 &&
            abs(after.second - before.second) <= 0.25 &&
            beforeServer.path("inventory") == afterServer.path("inventory") &&
            beforeServer.path("selected_slot") == afterServer.path("selected_slot") &&
            beforeServer.path("held_stack_sha256") == afterServer.path("held_stack_sha256")) {
            "server actor/inventory changed while opening the read-only GUI"
        }
        val profile = when (t.profile) {
            "1280x720@2" -> Triple(1280, 720, 2)
            "1920x1080@3" -> Triple(1920, 1080, 3)
            else -> error("unreviewed capture profile")
        }
        require(listOf(beforeClient, opened, frame).all { row ->
            row.path("player_uuid").asText() == t.actor && row.path("dimension").asText() == t.dimension &&
                row.path("screen_width").asInt(-1) == profile.first &&
                row.path("screen_height").asInt(-1) == profile.second &&
                row.path("gui_scale").isNumber && row.path("gui_scale").asDouble() == profile.third.toDouble() &&
                row.path("selected_slot") == beforeServer.path("selected_slot") &&
                row.path("inventory").isArray && row.path("inventory").size() == 41 &&
                row.path("inventory") == beforeClient.path("inventory")
        } && beforeClient.path("screen_class").isNull && beforeClient.path("menu_type").isNull &&
            beforeClient.path("menu_slots").isArray && beforeClient.path("menu_slots").size() == 0 &&
            listOf(status, opened, frame).all { row ->
                row.path("screen_class").asText() == "net.minecraft.client.gui.screens.inventory.InventoryScreen" &&
                    row.path("menu_type").asText() == "net.minecraft.world.inventory.InventoryMenu" &&
                    row.path("screen_title").isTextual && row.path("screen_title").asText().isNotBlank()
            } && opened.path("screen_title") == frame.path("screen_title") &&
            opened.path("menu_slots").isArray && opened.path("menu_slots").size() in 1..512 &&
            opened.path("menu_slots") == frame.path("menu_slots") &&
            frame.path("capture_method").asText() == "Minecraft Screenshot.takeScreenshot(mainRenderTarget)" &&
            Regex("[0-9a-f]{64}").matches(frame.path("image_sha256").asText()) &&
            frame.path("image_bytes").isIntegralNumber && frame.path("image_bytes").asLong() in 100..64_000_000 &&
            frame.path("image_path").isTextual && frame.path("image_path").asText().isNotBlank()) {
            "framebuffer/UI or server/client inventory diverged from reviewed same-player profile"
        }
        return MeasurementVerdict("passed", "actual bounded physical inventory key-up and source-bound single-profile framebuffer; separate immutable PNG replay and post-campaign independent human review of both profiles still required")
    }
}
