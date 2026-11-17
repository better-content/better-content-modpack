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

/** Controller-only marker: A resolves from a *verified* preceding EMI hover, never from native self-approval. */
internal const val EMI_HOVER_PROJECTION_MARKER = "@controller-reviewed-emi-hover-projection-sha256"

/** Narrow real UI navigation control, NOT all EMI usage/tag/recipe families or independent visual approval. */
internal object EmiNavigationControlDriver : CaseDriver {
    override val caseId = "control.client.emi-navigation"
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private val sha = Regex("[0-9a-f]{64}")
    private data class Target(val actor: String, val dimension: String, val profile: String, val emiSha: String,
        val holdInventory: String, val holdNavigation: String, val action: String, val x: Int, val y: Int,
        val hoverKind: String, val hoverId: String, val members: List<String>, val category: String,
        val visibleRecipeIds: List<String>)
    private fun parse(input: JsonNode): Target {
        require(input.isObject && input.fieldNames().asSequence().toSet() == setOf("player_uuid", "dimension",
            "input_profile", "emi_jar_sha256", "inventory_hold_id", "navigation_hold_id",
            "navigation", "gui_x", "gui_y", "hover_kind", "hover_id", "hover_member_ids",
            "expected_category_id", "expected_visible_recipe_ids")) { "reviewed exact EMI GUI target required" }
        fun text(name: String): String = input.path(name).also { require(it.isTextual) }.asText()
        fun ids(name: String, max: Int): List<String> = input.path(name).also {
            require(it.isArray && it.size() in 1..max)
        }.map { row -> require(row.isTextual && resource.matches(row.asText())); row.asText() }
            .also { require(it == it.sorted()) }
        val actor = text("player_uuid"); val dim = text("dimension"); val profile = text("input_profile")
        val artifact = text("emi_jar_sha256"); val a = text("inventory_hold_id"); val b = text("navigation_hold_id")
        val action = text("navigation"); val kind = text("hover_kind"); val id = text("hover_id")
        val category = text("expected_category_id")
        val x = input.path("gui_x"); val y = input.path("gui_y")
        // Both reviewed physical profiles present the same 640×360 scaled GUI, but never the same raw framebuffer.
        val width = 640; val height = 360
        require(runCatching { java.util.UUID.fromString(actor).toString() == actor }.getOrDefault(false) &&
            resource.matches(dim) && resource.matches(category) && resource.matches(id) &&
            profile in setOf("1280x720@2", "1920x1080@3") && sha.matches(artifact) &&
            listOf(a, b).all { Regex("[A-Za-z0-9._-]{1,64}").matches(it) } && a != b &&
            action in setOf("recipes", "uses") && kind in setOf("stack", "tag") &&
            x.isIntegralNumber && x.canConvertToInt() && x.asInt() in 0 until width &&
            y.isIntegralNumber && y.canConvertToInt() && y.asInt() in 0 until height) {
            "EMI GUI profile, target, action or bound physical point unsupported"
        }
        val members = ids("hover_member_ids", 128)
        require(kind == "tag" || members == listOf(id)) { "single stack and tag alternatives are distinct" }
        return Target(actor, dim, profile, artifact, a, b, action, x.asInt(), y.asInt(), kind, id,
            members, category, ids("expected_visible_recipe_ids", 32))
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val t = parse(parameters)
        val actor = suiteMapper.createObjectNode().put("player_uuid", t.actor)
        fun shared(hold: String) = suiteMapper.createObjectNode().put("player_uuid", "@observed-player-uuid")
            .put("dimension", t.dimension).put("hold_id", hold)
            .put("input_runtime_sha256", INPUT_RUNTIME_MARKER)
        val inventory = shared(t.holdInventory)
        val navigation = shared(t.holdNavigation)
        return listOf(
            DriverStep("emi.server_before", DriverAction("player.snapshot", actor.deepCopy())),
            DriverStep("emi.client_before", DriverAction("client.snapshot", suiteMapper.createObjectNode(), 100, "client")),
            DriverStep("emi.inventory_hold", DriverAction("client.input_hold", inventory.deepCopy().apply {
                put("profile", t.profile); put("kind", "inventory"); put("max_ticks", 6)
            }, 30, "client")),
            DriverStep("emi.inventory_release", DriverAction("client.input_release", inventory.deepCopy(), 80, "client")),
            DriverStep("emi.inventory_status", DriverAction("client.input_status", inventory.deepCopy(), 80, "client")),
            DriverStep("emi.point", DriverAction("client.input_point",
                suiteMapper.createObjectNode().put("player_uuid", "@observed-player-uuid")
                    .put("dimension", t.dimension).put("profile", t.profile)
                    .put("gui_x", t.x).put("gui_y", t.y)
                    .put("input_runtime_sha256", INPUT_RUNTIME_MARKER), 80, "client")),
            DriverStep("emi.pointer_settle", DriverAction("wait.ticks", suiteMapper.createObjectNode().put("ticks", 4), 16)),
            DriverStep("emi.hover", DriverAction("client.emi_hover",
                suiteMapper.createObjectNode().put("emi_jar_sha256", t.emiSha), 120, "client")),
            DriverStep("emi.navigation_hold", DriverAction("client.input_hold", navigation.deepCopy().apply {
                put("profile", t.profile); put("kind", if (t.action == "recipes") "emi_mouse_left" else "emi_mouse_right")
                put("max_ticks", 6); put("emi_jar_sha256", t.emiSha)
                put("hover_projection_sha256", EMI_HOVER_PROJECTION_MARKER)
            }, 30, "client")),
            DriverStep("emi.navigation_release", DriverAction("client.input_release", navigation.deepCopy(), 80, "client")),
            DriverStep("emi.navigation_status", DriverAction("client.input_status", navigation.deepCopy(), 80, "client")),
            DriverStep("emi.page", DriverAction("client.emi_page",
                suiteMapper.createObjectNode().put("emi_jar_sha256", t.emiSha), 120, "client")),
            DriverStep("emi.frame", DriverAction("client.framebuffer",
                suiteMapper.createObjectNode().put("profile", t.profile), 160, "client")),
            DriverStep("emi.page_after_frame", DriverAction("client.emi_page",
                suiteMapper.createObjectNode().put("emi_jar_sha256", t.emiSha), 120, "client")),
            DriverStep("emi.server_after", DriverAction("player.snapshot", actor.deepCopy()))
        )
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val t = parse(parameters)
        require(validateTerminalSequence(plan(parameters), observations) == null) { "physical EMI GUI source sequence incomplete" }
        val beforeServer = observations[0].payload; val beforeClient = observations[1].payload
        val inventoryHold = observations[2].payload; val inventoryRelease = observations[3].payload
        val inventoryStatus = observations[4].payload; val point = observations[5].payload
        val settle = observations[6].payload; val hover = observations[7].payload
        val navHold = observations[8].payload; val navRelease = observations[9].payload
        val navStatus = observations[10].payload; val page = observations[11].payload
        val frame = observations[12].payload; val pageAfter = observations[13].payload
        val afterServer = observations[14].payload
        require(validateAuthenticInventoryInput(inventoryHold, inventoryRelease, inventoryStatus,
            t.actor, t.dimension, t.profile, t.holdInventory,
            inventoryHold.path("input_runtime_sha256").asText()) &&
            beforeClient.path("screen_class").isNull &&
            listOf(inventoryStatus, point, hover, navHold).all {
                it.path("screen_class").asText() == "net.minecraft.client.gui.screens.inventory.InventoryScreen"
            } && settle.path("ticks_observed").isIntegralNumber && settle.path("ticks_observed").asInt() == 4 &&
            point.path("evidence_role").asText() == "authentic_robot_os_pointer_NOT_direct_callback_injection" &&
            point.path("physical_pointer_move_issued").isBoolean && point.path("physical_pointer_move_issued").asBoolean() &&
            point.path("profile").asText() == t.profile && point.path("input_runtime_sha256") ==
                inventoryHold.path("input_runtime_sha256") &&
            point.path("gui_x").asInt(-1) == t.x && point.path("gui_y").asInt(-1) == t.y &&
            hover.path("gui_x").asInt(-1) == t.x && hover.path("gui_y").asInt(-1) == t.y &&
            hover.path("hover_kind").asText() == t.hoverKind && hover.path("hover_id").asText() == t.hoverId &&
            hover.path("member_ids").map { it.asText() } == t.members &&
            hover.path("source_interaction_class").asText() ==
                "dev.emi.emi.screen.EmiScreenManager\$SidebarEmiStackInteraction" &&
            hover.path("source").asText() == "pinned EmiApi.getHoveredStack(actual_pointer)/last_rendered" &&
            hover.path("emi_jar_sha256").asText() == t.emiSha &&
            sha.matches(hover.path("projection_sha256").asText()) &&
            validateAuthenticEmiMouseInput(navHold, navRelease, navStatus, hover,
                t.actor, t.dimension, t.profile, t.holdNavigation,
                inventoryHold.path("input_runtime_sha256").asText(), t.action, t.emiSha)) {
            "EMI rendered target did not match frozen independent stack/tag selector and actual OS input"
        }
        require(listOf(page, pageAfter).all { row ->
            row.path("player_uuid").asText() == t.actor && row.path("dimension").asText() == t.dimension &&
                row.path("screen_class").asText() == "dev.emi.emi.screen.RecipeScreen" &&
                row.path("emi_jar_sha256").asText() == t.emiSha &&
                row.path("source").asText() == "pinned RecipeScreen.currentPage/WidgetGroup.recipe rendered UI state" &&
                row.path("visible_recipes").isArray && row.path("visible_recipes").size() in 1..32 &&
                row.path("visible_recipes").map { it.path("recipe_id").asText() }.sorted() == t.visibleRecipeIds &&
                row.path("visible_recipes").all { it.path("category_id").asText() == t.category &&
                    it.path("widget_count").asInt(0) in 1..512 }
        } && page.path("visible_recipes") == pageAfter.path("visible_recipes") &&
            page.path("projection_sha256") == pageAfter.path("projection_sha256") &&
            frame.path("screen_class").asText() == "dev.emi.emi.screen.RecipeScreen" &&
            frame.path("player_uuid").asText() == t.actor && frame.path("dimension").asText() == t.dimension &&
            frame.path("capture_method").asText() == "Minecraft Screenshot.takeScreenshot(mainRenderTarget)" &&
            sha.matches(frame.path("image_sha256").asText()) &&
            frame.path("image_bytes").isIntegralNumber && frame.path("image_bytes").asLong() in 100..64_000_000 &&
            frame.path("screen_width").asInt(-1) == (if (t.profile == "1280x720@2") 1280 else 1920) &&
            frame.path("screen_height").asInt(-1) == (if (t.profile == "1280x720@2") 720 else 1080) &&
            frame.path("gui_scale").asDouble(Double.NaN) == (if (t.profile == "1280x720@2") 2.0 else 3.0)) {
            "rendered EMI recipe page changed or does not match independently reviewed exact visible recipe roster"
        }
        val x0 = beforeServer.path("x").asDouble(Double.NaN); val y0 = beforeServer.path("y").asDouble(Double.NaN)
        val z0 = beforeServer.path("z").asDouble(Double.NaN)
        val x1 = afterServer.path("x").asDouble(Double.NaN); val y1 = afterServer.path("y").asDouble(Double.NaN)
        val z1 = afterServer.path("z").asDouble(Double.NaN)
        require(listOf(x0, y0, z0, x1, y1, z1).all { it.isFinite() } &&
            listOf(beforeServer, afterServer).all { it.path("uuid").asText() == t.actor &&
                it.path("dimension").asText() == t.dimension && it.path("mode").asText() == "survival" } &&
            hypot(x1 - x0, z1 - z0) <= 0.5 && abs(y1 - y0) <= 0.25 &&
            beforeServer.path("inventory") == afterServer.path("inventory") &&
            beforeClient.path("inventory") == frame.path("inventory") &&
            beforeServer.path("selected_slot") == afterServer.path("selected_slot")) {
            "EMI GUI navigation changed world actor or inventory, or lost server/client source identity"
        }
        return MeasurementVerdict("passed", "bounded physical inventory key/pointer/EMI mouse recipe-or-uses navigation; pinned actual hovered stack/tag and exact rendered page/frame source; separately reviewed target, both profiles and independent post-campaign human frame review required")
    }
}
