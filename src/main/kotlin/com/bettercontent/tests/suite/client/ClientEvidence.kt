package com.bettercontent.tests.suite.client

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.DeserializationFeature
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.time.Instant

/** Native client stream remains v2 transport, never a relabeled v2 campaign result. */
internal data class PlannedClientAction(val caseId: String, val driver: String, val parameters: JsonNode,
    val maxTimeoutTicks: Int)

/** Read-only replay of one actual joined client's *separate* mailbox and exact reviewed action roster. */
internal fun verifyV3ClientEvidence(base: Path, epochId: String, clientRunId: String, clientToken: String,
    serverToken: String, source: JsonNode, planned: List<PlannedClientAction>, emiJarSha256: String,
    expectedPlayerUuid: String, inputRuntime: ClientInputRuntime? = null,
    inputBinding: BoundFile? = null): List<NativeObservation> {
    require(epochId.matches(Regex("epoch-[0-9]{5}")) && planned.isNotEmpty() &&
        planned.all { it.driver in setOf("client.snapshot", "client.framebuffer", "client.emi_index",
            "client.emi_hover", "client.emi_page", "client.input_key", "client.input_click") + authenticInputDrivers } &&
        clientRunId.matches(idPattern) && clientToken.matches(shaPattern) &&
        serverToken.matches(shaPattern) && serverToken != clientToken && emiJarSha256.matches(shaPattern) &&
        runCatching { java.util.UUID.fromString(expectedPlayerUuid).toString() == expectedPlayerUuid }.getOrDefault(false)) {
        "unbound separate native client identity/plan"
    }
    val hasAuthenticInput = planned.any { it.driver in authenticInputDrivers }
    require(if (hasAuthenticInput) {
        inputRuntime != null && inputBinding != null && source.path("input_runtime") ==
            suiteMapper.valueToTree<JsonNode>(inputBinding) &&
            inputRuntime.clientSessionId == clientRunId && inputRuntime.epochId == epochId &&
            planned.filter { it.driver == "client.input_hold" }.map { it.parameters.path("profile").asText() }
                .distinct() == listOf(inputRuntime.profile) &&
            planned.filter { it.driver in authenticInputDrivers }.all {
                it.parameters.path("input_runtime_sha256").asText() == inputBinding.sha256 }
    } else inputRuntime == null && inputBinding == null &&
        (!source.has("input_runtime") || source.path("input_runtime").isNull)) {
        "authentic input missing immutable controller runtime binding or reviewed profile"
    }
    val sourceFiles = listOf("capabilities", "native_requests", "native_observations").map { key ->
        boundInput(base, source.path(key)).also { require(!Files.isSymbolicLink(it)) { "linked client source: $key" } }.toRealPath()
    }
    val capabilities = sourceFiles[0]; val requestsFile = sourceFiles[1]; val observationsFile = sourceFiles[2]
    val epoch = base.toRealPath().resolve("epochs").resolve(epochId)
    require(capabilities.parent == epoch.resolve("evidence/client-native").toRealPath() &&
        !Files.isSymbolicLink(capabilities.parent) &&
        requestsFile.parent == capabilities.parent &&
        observationsFile.parent == capabilities.parent && capabilities.fileName.toString() == "capabilities.json" &&
        requestsFile.fileName.toString() == "requests.jsonl" &&
        observationsFile.fileName.toString() == "observations.jsonl" &&
        Files.size(requestsFile) in 1..100_000_000 && Files.size(observationsFile) in 1..100_000_000) {
        "client streams not owned by this epoch"
    }
    val declared = suiteMapper.readTree(capabilities.toFile())
    val actions = setOf("client.snapshot", "client.framebuffer", "client.emi_index", "client.emi_hover",
        "client.emi_page", "client.input_key", "client.input_click") + authenticInputDrivers
    require(declared.path("schema").asText() == "bc.playtest_capabilities.v2" &&
        declared.path("source").asText() == "client" && declared.path("actual_client").isBoolean &&
        declared.path("actual_client").asBoolean() && declared.path("run_id").asText() == clientRunId &&
        declared.path("token").asText() == clientToken &&
        declared.path("supported_drivers").map { it.asText() }.toSet() == actions) {
        "server/instrumented synthetic source cannot claim real client observations"
    }
    fun rows(path: Path): List<JsonNode> = Files.readAllLines(path).also { require(it.isNotEmpty() && it.all(String::isNotBlank)) }
        .map { line -> require(line.length <= PROTOCOL_LINE_LIMIT); suiteMapper.readTree(line) }
    val requests = rows(requestsFile).map { suiteMapper.treeToValue(it, NativeRequest::class.java) }
    val observations = rows(observationsFile).map { suiteMapper.treeToValue(it, NativeObservation::class.java) }
    require(requests.size == planned.size && requests.map { it.sequence } == (1L..planned.size.toLong()).toList() &&
        requests.all { it.runId == clientRunId && it.token == clientToken } &&
        observations.map { it.sequence } == (1L..observations.size.toLong()).toList()) {
        "client stream missing/reordered or bound to a different token/session"
    }
    val validator = NativeExchangeValidator()
    var position = 0
    val terminals = mutableListOf<NativeObservation>()
    for ((request, expected) in requests.zip(planned)) {
        require(request.caseId == expected.caseId && request.driver == expected.driver &&
            request.parameters == expected.parameters && request.timeoutTicks in 1..expected.maxTimeoutTicks) {
            "unreviewed client callback or substituted parameters: ${request.driver}"
        }
        validator.begin(request)
        var terminal: NativeObservation
        do {
            require(position < observations.size) { "client action has no terminal" }
            terminal = observations[position++]
        } while (!validator.accept(terminal, "client"))
        require(terminal.kind == "completed" && terminal.tick >= 0) { "client action failed/partial" }
        verifyClientPayload(capabilities.parent, request, terminal.payload, expectedPlayerUuid, emiJarSha256,
            inputRuntime, inputBinding)
        terminals += terminal
    }
    require(position == observations.size) { "unplanned/replayed client observation" }
    if (hasAuthenticInput) verifyAuthenticInputSequence(requests, terminals, requireNotNull(inputRuntime),
        requireNotNull(inputBinding), expectedPlayerUuid)
    val inputTerminals = terminals.filter { it.driver in setOf("client.input_key", "client.input_click") + authenticInputDrivers }
    if (inputTerminals.isNotEmpty()) {
        val inputFile = boundInput(base, source.path("input_log")).also {
            require(!Files.isSymbolicLink(it)) { "linked client input log" }
        }.toRealPath()
        require(inputFile.parent == capabilities.parent && inputFile.fileName.toString() == "input-events.jsonl" &&
            Files.size(inputFile) in 1..10_000_000)
        val inputs = rows(inputFile)
        require(inputs.size == inputTerminals.size && inputs.zip(inputTerminals).all { (row, terminal) ->
            row.path("schema").asText() == "bc.playtest_client_input.v1" &&
                row.path("run_id").asText() == clientRunId && row.path("token").asText() == clientToken &&
                row.path("action").asText() == terminal.driver &&
                row.path("request_sequence").asLong() == terminal.requestSequence &&
                row.path("parameters") == requests.single { it.sequence == terminal.requestSequence }.parameters &&
                Instant.parse(row.path("time").asText()).isBefore(Instant.now()) &&
                row.path("observed_result") == terminal.payload.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
                    .apply { remove(listOf("input_log", "input_log_sha256")) }
        } && inputTerminals.last().payload.path("input_log_sha256").asText() == Hashes.sha256(inputFile)) {
            "input callback lacks bounded source-ordered client input ledger"
        }
    } else require(!source.has("input_log") || source.path("input_log").isNull) { "foreign client input log" }
    return terminals
}

private fun verifyAuthenticInputSequence(requests: List<NativeRequest>, terminals: List<NativeObservation>,
    runtime: ClientInputRuntime, binding: BoundFile, expectedPlayerUuid: String) {
    data class Held(val hold: NativeRequest, val payload: JsonNode, var release: JsonNode? = null)
    var active: Held? = null
    var epochWindow: Pair<Long, Long>? = null
    val used = mutableSetOf<String>()
    var completed = 0
    var renderedEmiHover: JsonNode? = null
    requests.zip(terminals).forEach { (request, terminal) ->
        if (request.driver == "client.emi_hover") {
            require(active == null && request.caseId == "control.client.emi-navigation")
            renderedEmiHover = terminal.payload
            return@forEach
        }
        if (request.driver !in authenticInputDrivers) return@forEach
        val row = terminal.payload
        val window = row.path("glfw_window").asLong(-1) to row.path("x11_window").asLong(-1)
        require(window.first > 0 && window.second > 0 && (epochWindow == null || window == epochWindow)) {
            "client window changed between reviewed physical input actions"
        }
        epochWindow = window
        if (request.driver == "client.input_point") {
            require(active == null && request.caseId == "control.client.emi-navigation" &&
                row.path("physical_pointer_move_issued").asBoolean(false) &&
                row.path("player_uuid").asText() == expectedPlayerUuid &&
                row.path("client_namespace_pid").asLong(-1) == runtime.clientNamespacePid &&
                row.path("input_runtime_sha256").asText() == binding.sha256 &&
                row.path("gui_x") == request.parameters.path("gui_x") &&
                row.path("gui_y") == request.parameters.path("gui_y")) {
                "EMI point was not an OS move on same real input window/runtime"
            }
            return@forEach
        }
        val holdId = row.path("hold_id").asText()
        require(holdId.isNotBlank() && row.path("client_namespace_pid").asLong(-1) == runtime.clientNamespacePid &&
            row.path("input_runtime_sha256").asText() == binding.sha256 &&
            row.path("player_uuid").asText() == expectedPlayerUuid)
        when (request.driver) {
            "client.input_hold" -> {
                require(active == null && used.add(holdId) && row.path("physical_press_issued").asBoolean(false) &&
                    row.path("hold_sequence").asLong(-1) == request.sequence &&
                    row.path("profile").asText() == runtime.profile &&
                    (row.path("kind").asText() != "onboarding_chord" ||
                        (row.path("client_spectator").asBoolean(false) &&
                            row.path("onboarding_active").asBoolean(false) &&
                            row.path("onboarding_selection_required").asBoolean(false) &&
                            row.path("onboarding_mode_spawn_only").asBoolean(false)))) {
                    "repeated/unbound hold or onboarding started outside genuine spawn-only spectator mode"
                }
                active = Held(request, row)
            }
            "client.input_release" -> {
                val held = requireNotNull(active) { "physical release without a prior hold" }
                require(held.release == null && holdId == held.hold.parameters.path("hold_id").asText() &&
                    row.path("hold_sequence").asLong(-1) == held.hold.sequence &&
                    row.path("glfw_window") == held.payload.path("glfw_window") &&
                    row.path("x11_window") == held.payload.path("x11_window") &&
                    row.path("physical_release_issued").asBoolean(false) &&
                    row.path("press_callback").asBoolean(false)) { "physical input release lacks same-window press" }
                held.release = row
            }
            "client.input_status" -> {
                val held = requireNotNull(active) { "input status without a prior hold" }
                require(held.release != null && holdId == held.hold.parameters.path("hold_id").asText() &&
                    row.path("hold_sequence").asLong(-1) == held.hold.sequence &&
                    row.path("glfw_window") == held.payload.path("glfw_window") &&
                    row.path("x11_window") == held.payload.path("x11_window") &&
                    row.path("press_callback").asBoolean(false) &&
                    row.path("release_callback").asBoolean(false) &&
                    row.path("physical_release_verified").asBoolean(false) &&
                    row.path("physical_input_down").isBoolean && !row.path("physical_input_down").asBoolean()) {
                    "physical input release callback or key-up missing"
                }
                if (row.path("kind").asText() == "onboarding_chord") require(
                    row.path("modifier_press_callback").asBoolean(false) &&
                    row.path("modifier_release_callback").asBoolean(false)) {
                    "onboarding Shift+K modifier callbacks missing"
                }
                if (row.path("kind").asText() == "inventory_key") require(
                    request.caseId in setOf("control.client.inventory-screen", "control.client.emi-navigation") &&
                    held.hold.caseId == request.caseId &&
                    held.payload.path("kind").asText() == "inventory_key" &&
                    held.hold.parameters.path("kind").asText() == "inventory" &&
                    validateAuthenticInventoryInput(held.payload, requireNotNull(held.release), row,
                        expectedPlayerUuid, row.path("dimension").asText(), runtime.profile, holdId,
                        binding.sha256)) { "inventory GUI source lacks bound physical key/callback/screen transition" }
                if (row.path("kind").asText() in setOf("emi_mouse_left", "emi_mouse_right")) require(
                    request.caseId == "control.client.emi-navigation" &&
                    held.hold.caseId == request.caseId &&
                    held.hold.parameters.path("hover_projection_sha256").asText() ==
                        renderedEmiHover?.path("projection_sha256")?.asText() &&
                    validateAuthenticEmiMouseInput(held.payload, requireNotNull(held.release), row,
                        requireNotNull(renderedEmiHover), expectedPlayerUuid, row.path("dimension").asText(),
                        runtime.profile, holdId, binding.sha256,
                        if (row.path("kind").asText() == "emi_mouse_left") "recipes" else "uses",
                        held.hold.parameters.path("emi_jar_sha256").asText())) {
                    "EMI mouse lacks bound actual rendered hover and physical callback/key-up"
                }
                if (row.path("kind").asText() == "walk_forward") require(
                    request.caseId == "control.travel.walk-route" && held.hold.caseId == request.caseId &&
                    held.payload.path("kind").asText() == "walk_forward" &&
                    validateAuthenticWalkInput(held.payload, requireNotNull(held.release), row,
                        expectedPlayerUuid, row.path("dimension").asText(), runtime.profile, holdId,
                        binding.sha256, held.hold.parameters.path("max_ticks").asInt(-1))) {
                    "walk control lacks physical W callback, continuous bounded movement or key-up"
                }
                if (row.path("kind").asText() in setOf("use_mouse", "use_key")) {
                    val slot = held.payload.path("selected_slot").asInt(-1)
                    val item = held.payload.path("inventory").firstOrNull {
                        it.path("slot").asInt(-1) == slot && it.path("count").asInt(0) > 0
                    }?.path("item_id")?.asText().orEmpty()
                    require(validateAuthenticHeldInput(held.payload, requireNotNull(held.release), row,
                        expectedPlayerUuid, row.path("dimension").asText(), runtime.profile, holdId, item,
                        binding.sha256)) { "held use lacks real callback, selected stack or bounded release" }
                }
                completed++; active = null
            }
        }
    }
    require(active == null && completed > 0) { "stuck or incomplete physical input" }
}

private fun verifyClientPayload(mailbox: Path, request: NativeRequest, payload: JsonNode,
    expectedPlayer: String, emiJarSha256: String, runtime: ClientInputRuntime?, binding: BoundFile?) {
    require(payload.isObject && payload.path("player_uuid").asText() == expectedPlayer &&
        payload.path("selected_slot").isIntegralNumber && payload.path("selected_slot").asInt() in 0..8 &&
        payload.path("dimension").isTextual && resourcePattern.matches(payload.path("dimension").asText()) &&
        listOf("x", "y", "z").all { payload.path(it).isNumber && payload.path(it).asDouble().isFinite() } &&
        payload.path("inventory").isArray &&
        (request.driver in authenticInputDrivers || !payload.path("inventory").isEmpty) &&
        payload.path("inventory").size() <= 64 && payload.path("menu_slots").isArray &&
        payload.path("menu_slots").size() <= 512 && payload.path("screen_width").asInt() in 640..8192 &&
        payload.path("screen_height").asInt() in 480..4320 && payload.path("gui_scale").asDouble() in 1.0..8.0) {
        "client terminal is not a typed actual-player/UI state snapshot"
    }
    val rows = payload.path("inventory").toList() + payload.path("menu_slots").toList()
    require(rows.all { row -> row.path("slot").isIntegralNumber && row.path("slot").asInt() in 0..512 &&
        row.path("item_id").isTextual && resourcePattern.matches(row.path("item_id").asText()) &&
        row.path("count").isIntegralNumber && row.path("count").asInt() in 0..64 &&
        row.path("stack_nbt").isTextual && row.path("stack_snbt_sha256").asText().matches(shaPattern) &&
        row.path("stack_snbt_sha256").asText() == hashBytes(row.path("stack_nbt").asText().toByteArray(Charsets.UTF_8))
    }) { "client inventory/menu slot lacks independently hashed item NBT" }
    when (request.driver) {
        "client.framebuffer" -> {
            val profile = request.parameters.path("profile").asText()
            val dimensions = when (profile) { "1280x720@2" -> Triple(1280, 720, 2); "1920x1080@3" -> Triple(1920, 1080, 3)
                else -> error("unreviewed GUI profile") }
            require(payload.path("screen_width").asInt() == dimensions.first &&
                payload.path("screen_height").asInt() == dimensions.second &&
                payload.path("gui_scale").asDouble() == dimensions.third.toDouble() &&
                payload.path("capture_method").asText() == "Minecraft Screenshot.takeScreenshot(mainRenderTarget)")
            val imagePath = Path.of(payload.path("image_path").asText())
            require(!Files.isSymbolicLink(imagePath)) { "linked framebuffer image" }
            val image = imagePath.toRealPath()
            require(image.parent == mailbox && image.fileName.toString() == "frame-%012d.png".format(request.sequence) &&
                Files.size(image) in 100..64_000_000 && payload.path("image_bytes").asLong() == Files.size(image) &&
                payload.path("image_sha256").asText() == Hashes.sha256(image) &&
                pngSize(image) == (dimensions.first to dimensions.second)) { "framebuffer PNG is missing/foreign/wrong profile" }
        }
        "client.emi_hover" -> require(request.caseId == "control.client.emi-navigation" &&
            payload.path("source").asText() == "pinned EmiApi.getHoveredStack(actual_pointer)/last_rendered" &&
            payload.path("source_interaction_class").asText() ==
                "dev.emi.emi.screen.EmiScreenManager\$SidebarEmiStackInteraction" &&
            payload.path("screen_class").asText() ==
                "net.minecraft.client.gui.screens.inventory.InventoryScreen" &&
            payload.path("emi_jar_sha256").asText() == emiJarSha256 &&
            payload.path("projection_sha256").asText().matches(shaPattern) &&
            payload.path("hover_kind").asText() in setOf("stack", "tag") &&
            payload.path("hover_id").asText().matches(resourcePattern) &&
            payload.path("member_ids").isArray && payload.path("member_ids").size() in 1..128 &&
            payload.path("member_ids").all { it.isTextual && it.asText().matches(resourcePattern) } &&
            payload.path("gui_x").isIntegralNumber && payload.path("gui_y").isIntegralNumber) {
            "EMI hovered ingredient is not a pinned actual rendered source"
        }
        "client.emi_page" -> require(request.caseId == "control.client.emi-navigation" &&
            payload.path("source").asText() ==
                "pinned RecipeScreen.currentPage/WidgetGroup.recipe rendered UI state" &&
            payload.path("screen_class").asText() == "dev.emi.emi.screen.RecipeScreen" &&
            payload.path("emi_jar_sha256").asText() == emiJarSha256 &&
            payload.path("projection_sha256").asText().matches(shaPattern) &&
            payload.path("visible_recipes").isArray && payload.path("visible_recipes").size() in 1..32 &&
            payload.path("visible_recipes").all { row ->
                row.isObject && row.fieldNames().asSequence().toSet() ==
                    setOf("recipe_id", "category_id", "x", "y", "width", "height", "widget_count") &&
                row.path("recipe_id").asText().matches(resourcePattern) &&
                row.path("category_id").asText().matches(resourcePattern) &&
                row.path("widget_count").isIntegralNumber && row.path("widget_count").asInt(0) in 1..512 &&
                row.path("x").isIntegralNumber && row.path("x").asInt(-1) in 0..639 &&
                row.path("y").isIntegralNumber && row.path("y").asInt(-1) in 0..359 &&
                row.path("width").isIntegralNumber && row.path("width").asInt(-1) in 1..640 &&
                row.path("height").isIntegralNumber && row.path("height").asInt(-1) in 1..360 &&
                row.path("x").asInt() + row.path("width").asInt() <= 640 &&
                row.path("y").asInt() + row.path("height").asInt() <= 360 }) {
            "EMI page did not expose bounded pinned actual rendered recipes"
        }
        "client.emi_index" -> {
            require(payload.path("source").asText() == "pinned dev.emi.emi.api.EmiApi:getIndexStacks/getRecipeManager" &&
                payload.path("complete").isBoolean && payload.path("complete").asBoolean() &&
                payload.path("emi_jar_sha256").asText() == emiJarSha256)
            verifyCapturedEmiPublicIndex(mailbox, request.sequence, payload, emiJarSha256)
        }
        "client.input_key" -> require(payload.path("callback").asText() == "KeyboardHandler.keyPress" &&
            payload.path("key") == request.parameters.path("key") &&
            payload.path("before_screen").asText().isNotBlank() && payload.path("input_log_sha256").asText().matches(shaPattern)) {
            "keyboard handler callback was not observed"
        }
        "client.input_click" -> require(payload.path("callback").asText() == "Screen.mouseClicked/mouseReleased" &&
            listOf("x", "y", "button").all { payload.path(it) == request.parameters.path(it) } &&
            payload.path("handled").isBoolean && payload.path("before_screen").asText().isNotBlank() &&
            payload.path("input_log_sha256").asText().matches(shaPattern)) {
            "screen mouse callback was not observed"
        }
        in authenticInputDrivers -> require(runtime != null && binding != null &&
            payload.path("evidence_role").asText() == (if (request.driver == "client.input_point")
                "authentic_robot_os_pointer_NOT_direct_callback_injection"
                else "authentic_robot_os_input_NOT_direct_callback_injection") &&
            payload.path("input_runtime_sha256").asText() == binding.sha256 &&
            payload.path("client_session_id").asText() == runtime.clientSessionId &&
            payload.path("epoch_id").asText() == runtime.epochId && payload.path("purpose").asText() == runtime.purpose &&
            payload.path("profile").asText() == runtime.profile && payload.path("display").asText() == runtime.display &&
            payload.path("xauthority_sha256").asText() == runtime.xauthoritySha256 &&
            payload.path("client_namespace_pid").asLong(-1) == runtime.clientNamespacePid &&
            payload.path("controller_claim_host_xvfb_pid").asLong(-1) == runtime.hostXvfbPid &&
            payload.path("controller_claim_host_xvfb_birth_epoch_millis").asLong(-1) == runtime.hostXvfbBirthEpochMillis &&
            payload.path("controller_claim_host_client_pid").asLong(-1) == runtime.hostClientPid &&
            payload.path("controller_claim_host_client_birth_epoch_millis").asLong(-1) == runtime.hostClientBirthEpochMillis &&
            payload.path("glfw_window").asLong(-1) > 0 && payload.path("x11_window").asLong(-1) > 0 &&
            (if (request.driver == "client.input_point")
                payload.path("physical_pointer_move_issued").asBoolean(false) &&
                    payload.path("gui_x") == request.parameters.path("gui_x") &&
                    payload.path("gui_y") == request.parameters.path("gui_y") &&
                    request.caseId == "control.client.emi-navigation"
                else payload.path("hold_id").asText() == request.parameters.path("hold_id").asText()) &&
            payload.path("input_log_sha256").asText().matches(shaPattern)) {
            "physical input fact differs from immutable host controller binding or actual window"
        }
    }
}

/** One NOFOLLOW bounded read supplies both the exact terminal hash and the strictly parsed document. */
internal fun verifyCapturedEmiPublicIndex(mailbox: Path, sequence: Long, payload: JsonNode,
    emiJarSha256: String): JsonNode {
    require(sequence > 0 && emiJarSha256.matches(shaPattern) && payload.path("file").isTextual &&
        payload.path("file_sha256").isTextual && payload.path("file_sha256").asText().matches(shaPattern) &&
        payload.path("file_bytes").isIntegralNumber && payload.path("file_bytes").canConvertToLong() &&
        payload.path("file_bytes").asLong() in 1..32_000_000 &&
        listOf("index_count", "recipe_count").all { payload.path(it).isIntegralNumber && payload.path(it).canConvertToInt() &&
            payload.path(it).asInt() in 1..100_000 }) { "EMI export envelope malformed" }
    val sourceFile = Path.of(payload.path("file").asText())
    require(sourceFile.isAbsolute && sourceFile.normalize() == sourceFile &&
        !Files.isSymbolicLink(sourceFile.parent) && Files.isRegularFile(sourceFile, NOFOLLOW_LINKS) &&
        !Files.isSymbolicLink(sourceFile)) {
        "linked or missing EMI export"
    }
    val file = sourceFile.toRealPath()
    require(file.parent == mailbox.toRealPath() && file.fileName.toString() == "emi-public-%012d.json".format(sequence) &&
        Files.size(file) == payload.path("file_bytes").asLong()) { "EMI public export not owned by client mailbox" }
    val bytes = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(32_000_001) }
    require(bytes.size in 1..32_000_000 && bytes.size.toLong() == payload.path("file_bytes").asLong() &&
        hashBytes(bytes) == payload.path("file_sha256").asText()) { "EMI public export differs from bound terminal bytes" }
    val document: JsonNode = suiteMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes)
    require(document.isObject && document.fieldNames().asSequence().toSet() ==
        setOf("schema", "emi_jar_sha256", "index_ids", "recipes") &&
        document.path("schema").asText() == "bc.playtest_emi_public_index.v2" &&
        document.path("emi_jar_sha256").isTextual && document.path("emi_jar_sha256").asText() == emiJarSha256 &&
        document.path("index_ids").isArray && document.path("recipes").isArray &&
        document.path("index_ids").size() == payload.path("index_count").asInt() &&
        document.path("recipes").size() == payload.path("recipe_count").asInt()) {
        "EMI public index schema/candidate/count/shape differs from terminal"
    }
    validateEmiPublicDocument(document, emiJarSha256, payload.path("index_count").asInt(), payload.path("recipe_count").asInt())
    return document
}

private fun hashBytes(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

internal fun pngSize(path: Path): Pair<Int, Int> {
    Files.newInputStream(path).use { stream ->
        val prefix = stream.readNBytes(24)
        require(prefix.size == 24 && prefix.take(8).map { it.toInt() and 255 } == listOf(137, 80, 78, 71, 13, 10, 26, 10) &&
            prefix.copyOfRange(12, 16).contentEquals("IHDR".toByteArray(Charsets.US_ASCII))) { "not a PNG framebuffer" }
        fun dimension(offset: Int): Int = ((prefix[offset].toInt() and 255) shl 24) or
            ((prefix[offset + 1].toInt() and 255) shl 16) or ((prefix[offset + 2].toInt() and 255) shl 8) or
            (prefix[offset + 3].toInt() and 255)
        val declared = dimension(16) to dimension(20)
        require(declared.first in 640..8192 && declared.second in 480..4320) { "PNG dimensions exceed reviewed client bounds" }
        val decoded = javax.imageio.ImageIO.read(path.toFile()) ?: error("framebuffer PNG cannot be decoded")
        require((decoded.width to decoded.height) == declared) { "framebuffer PNG IHDR differs from decoded pixels" }
        return declared
    }
}
