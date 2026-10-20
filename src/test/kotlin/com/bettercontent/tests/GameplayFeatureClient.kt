package com.bettercontent.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.UUID

/** Calls only candidate-bound nonshipping bridges. Never injects input or changes world clocks. */
object GameplayFeatureClient {
    private val mapper = jacksonObjectMapper()
    private const val IMPACT = "better_survival_physiology:aspect.impact"

    fun run(server: DedicatedServerFixture, client: ClientFixture, evidence: EvidenceRun, nonce: UUID, visit: Int) {
        val io = Bridge(server, client, evidence, nonce, visit * 10000)
        io.client("close-menu", "v$visit-close-native-menu")
        val login = io.client("arm", "v$visit-login-window")
        if (visit == 2) {
            io.world("trader-resume", "v2-actual-trader-persistence-before-expiry")
            val ready = io.world("readiness-observe", "v2-login-ready-state")
            require(ready.path("after_proteins").asDouble() >= .3 && ready.path("cooldown").asInt(-1) == 0)
            require(impactSounds(login.path("prior_window")) == 0 && login.path("prior_window").path("native_source_events").none { it.path("id").asText() == IMPACT }) { "ready reconnect manufactured an impact cue" }
            require(login.path("prior_window").path("sounds_dropped").asLong(-1) == 0L)
            evidence.event("gameplay_silent_ready_login", mapOf("nonce" to nonce.toString(), "observation" to "v2-login-window",
                "native_sound_dispatch_count" to 0, "physical_audio" to "UNKNOWN"))
        }
        if (visit == 1) {
            io.world("empty-hand", "v1-native-empty-hotbar-selection")
            io.world("spoilage", "v1-native-compost-bobber")
            for (state in listOf("rod-no-backpack", "rod-equipped")) {
                io.client("close-menu", "v1-$state-close-before")
                var native = io.world(state, "v1-$state-open")
                if (state == "rod-equipped") {
                    val primed = native
                    // Curios 6418456 emits changed-stack sync from the next LivingTick, not
                    // setStackInSlot. Its ordered client-main-thread update must precede the
                    // later native OpenScreen packet; a delayed photo cannot repair the first init.
                    Thread.sleep(1200)
                    native = io.world(state, "v1-$state-final-open-after-native-sync")
                    require(native.path("server_tick").asLong() > primed.path("server_tick").asLong())
                    require(native.path("native_probe").path("menu_id").asInt() != primed.path("native_probe").path("menu_id").asInt())
                    evidence.event("gameplay_native_rod_equipment_sync_order", mapOf("state" to state,
                        "prime_server_tick" to primed.path("server_tick").asLong(), "final_server_tick" to native.path("server_tick").asLong(),
                        "prime_menu_id" to primed.path("native_probe").path("menu_id").asInt(),
                        "final_menu_id" to native.path("native_probe").path("menu_id").asInt(),
                        "basis" to "pinned Curios LivingTick sync precedes later native menu-open packet on the same ordered connection; no intervening client close packet",
                        "direct_client_curios_stack_sample" to "NOT_AVAILABLE in frozen supplement; native packet-ordering oracle used"))
                }
                Thread.sleep(1200)
                val capture = io.capture("v1-$state-native-frame")
                require(capture.path("menu_id").asInt(-1) == native.path("native_probe").path("menu_id").asInt(-2))
                require(capture.path("menu_class").asText() == native.path("native_probe").path("menu_class").asText())
                require(capture.path("screen_class").asText().contains("starcatcher", ignoreCase = true))
                require(capture.path("slots").any { it.path("index").asInt() == 37 &&
                    it.path("item").asText() == "better_industrial_heat:spoiled_meat" && it.path("count").asInt() == 3 })
                evidence.event("gameplay_native_rod_menu", mapOf("state" to state, "frame" to capture.path("framebuffer").asText(),
                    "menu_class" to capture.path("menu_class").asText(), "native_api_behavior" to "passed", "human_pointer" to "NOT_PERFORMED"))
                io.client("close-menu", "v1-$state-close-after")
            }
            // Keep the zero-funds negative early; do not permit a native camp until clearing fixtures finish.
            io.world("trader-funds0", "v1-trader-funds0")
        }
        io.world("campfires", "v$visit-palette-scene")
        Thread.sleep(1800) // Let real campfire/AdvancedChimneys/Weather2 render naturally.
        var palette = io.capture("v$visit-eight-palettes-native-smoke")
        if (visit == 1) Files.writeString(server.diagnosticSupport.bridge.resolve("gameplay-palette-first-phase.json"),
            mapper.writeValueAsString(mapOf("native_day" to palette.path("native_day").asBoolean(),
                "native_night" to palette.path("native_night").asBoolean(), "day_time" to palette.path("day_time").asLong(),
                "run" to evidence.config.runId, "nonce" to nonce.toString(), "player" to client.uuid.toString())), StandardOpenOption.CREATE_NEW)
        if (visit == 2) {
            val first = mapper.readTree(server.diagnosticSupport.bridge.resolve("gameplay-palette-first-phase.json").toFile())
            require(first.path("run").asText() == evidence.config.runId && first.path("nonce").asText() == nonce.toString()
                && first.path("player").asText() == client.uuid.toString())
            val deadline = System.nanoTime() + Duration.ofMinutes(12).toNanos()
            while (GameplayFeatureContract.nativeDay(palette) == GameplayFeatureContract.nativeDay(first)) {
                check(System.nanoTime() < deadline) { "native opposite day/night phase did not arrive; no world clock override permitted" }
                Thread.sleep(15000)
                val clock = io.world("readiness-observe", "v2-palette-clock-${io.next}")
                if (GameplayFeatureContract.nativeDay(clock) != GameplayFeatureContract.nativeDay(first))
                    palette = io.capture("v2-eight-palettes-opposite-native-phase-${io.next}")
            }
            GameplayFeatureContract.requireOppositeDayNight(first, palette)
        }
        evidence.event("gameplay_palette_native_frame", mapOf("visit" to visit, "day" to GameplayFeatureContract.nativeDay(palette),
            "day_time" to palette.path("day_time").asLong(), "frame" to palette.path("framebuffer").asText(),
            "visual_review" to "PENDING actual framebuffer inspection", "custom_smoke_counters" to "not equivalent to pixels"))
        if (visit == 1) {
            for (index in 0..3) {
                io.world("field", "v1-field-$index-world", mapOf("index" to index))
                Thread.sleep(1200)
                io.capture("v1-field-$index-native-frame")
                io.world("bush-harvest", "v1-field-$index-native-harvest")
                Thread.sleep(800) // native authored light propagation, never a clock override
                io.capture("v1-field-$index-harvest-frame")
                io.world("bush-regrow", "v1-field-$index-native-regrow")
                Thread.sleep(400)
                io.capture("v1-field-$index-regrow-frame")
            }
            // Finish all destructive field setup before native traders can author their post/fire.
            // Reserve the cut block first, and occupy the selected hand with its fixture axe so
            // currency grants use another empty inventory slot and survive both actual cuts.
            io.world("chopping-log", "v1-reserve-native-cut-target-before-traders")
            io.world("core", "v1-native-trader-funding-core")
            for (funds in listOf(9, 10)) io.world("trader-funds$funds", "v1-trader-funds$funds")
            io.world("trader-arm", "v1-trader-natural-arm")
            for (kind in listOf("log", "dt")) {
                val fixture = io.world("chopping-$kind", "v1-cut-$kind-fixture")
                Thread.sleep(1200)
                io.client("arm", "v1-cut-$kind-arm")
                val target = fixture.path("target").map(JsonNode::asInt)
                val block = mapOf("x" to target[0], "y" to target[1], "z" to target[2], "face" to "SOUTH")
                io.client("start-cut", "v1-cut-$kind-start", block)
                var complete = false
                for (tick in 1..1200) {
                    val step = io.client("continue-cut", "v1-cut-$kind-tick-$tick", block)
                    if (step.path("block_after").asText().contains("minecraft:air")) { complete = true; break }
                    if (tick == 4) io.capture("v1-cut-$kind-progress-frame")
                }
                io.client("stop-cut", "v1-cut-$kind-stop")
                require(complete) { "actual native $kind cut failed within 1200 client requests" }
                val cut = io.capture("v1-cut-$kind-complete-frame")
                GameplayFeatureContract.requireCut(cut.path("observations"))
                evidence.event("gameplay_native_cut", mapOf("kind" to kind, "origin" to "authored_native_block_fixture",
                    "evidence" to "v1-cut-$kind-complete-frame", "chip_rate" to "native tick/monotonic sample trace retained; not a hardware performance verdict"))
            }
            io.world("core", "v1-feedback-core")
            io.world("readiness-low", "v1-feedback-low")
            Thread.sleep(1200)
            io.client("arm", "v1-feedback-arm")
            io.world("readiness-high", "v1-feedback-high")
            Thread.sleep(100) // capture the real short-lived native dust, not a delayed empty scene
            val rising = io.capture("v1-readiness-rising-frame")
            GameplayFeatureContract.requireRising(rising.path("observations"))
            Thread.sleep(1500)
            val plateau = io.capture("v1-readiness-plateau-frame")
            require(impactSounds(plateau.path("observations")) == 1) { "ready plateau repeated impact feedback" }
            evidence.event("gameplay_native_readiness_feedback", mapOf("rising_native_sound_events" to 1,
                "plateau_native_sound_events" to 1, "native_cue_source_verdict" to "NATIVE_CUE_SOURCE_PLAYING", "physical_audio_verdict" to "UNKNOWN", "nutrition_origin" to "authored public Diet tracker fixture"))
        }
        io.world("core", "v$visit-return-core")
        io.client("close-menu", "v$visit-finish-native-menu-close")
        server.assertHashes(); client.assertHashes()
    }

    /** Shader-on visual-only window after unshaded gameplay proof. No world clocks or renderer internals changed. */
    fun runShaderWindow(server: DedicatedServerFixture, client: ClientFixture, evidence: EvidenceRun, nonce: UUID) {
        require(evidence.tier == "debug" && evidence.target == "gameplay")
        val io = Bridge(server, client, evidence, nonce, 40000)
        val worldBefore = io.world("readiness-observe", "shader-window-world-before")
        require(worldBefore.path("world_root").asText().isNotBlank() && worldBefore.path("world_seed").isNumber
            && worldBefore.path("server_process").asLong() > 0 && worldBefore.path("candidate_binding").isObject)
        val productionConfig = evidence.config.root.resolve("config/oculus.properties")
        val productionHash = Hashes.sha256(productionConfig)
        if (client.processAlive()) closeNativeClient(server, client, evidence, nonce, 2)
        val config = client.client.resolve("config/oculus.properties")
        require(!Files.isSymbolicLink(config) && Files.isRegularFile(config))
        val original = Files.readString(config)
        require(original.lineSequence().count { it.startsWith("enableShaders=") } == 1
            && original.lineSequence().any { it.trimEnd() == "enableShaders=false" }) { "shader fixture must begin from the default disabled profile" }
        val modified = original.replaceFirst("enableShaders=false", "enableShaders=true")
        require(modified.replaceFirst("enableShaders=true", "enableShaders=false") == original)
        Files.writeString(evidence.directory.resolve("shader-window-oculus-before.properties"), original, StandardOpenOption.CREATE_NEW)
        Files.writeString(config, modified, StandardOpenOption.TRUNCATE_EXISTING)
        Files.writeString(evidence.directory.resolve("shader-window-oculus-after.properties"), modified, StandardOpenOption.CREATE_NEW)
        val expectedPack = client.client.resolve("shaderpacks/ComplementaryReimagined_r5.8.1.zip")
        require(Files.isRegularFile(expectedPack) && !Files.isSymbolicLink(expectedPack))
        val packHash = Hashes.sha256(expectedPack)
        evidence.event("gameplay_shader_window_started", mapOf("nonce" to nonce.toString(), "changed_fixture_only" to config.toString(),
            "before_sha256" to Hashes.sha256(evidence.directory.resolve("shader-window-oculus-before.properties")),
            "after_sha256" to Hashes.sha256(config), "shader_pack_sha256" to packHash,
            "profile_scope" to "post-soak visual-only; not thirty-minute shader performance acceptance", "budget_seconds" to 1200))
        client.restartDedicated(2)
        // Existing startup/join and global four-hour budgets cover restart separately.
        // This fixed visual-cycle deadline never resets during natural phase sampling.
        val deadline = System.nanoTime() + Duration.ofMinutes(20).toNanos()
        io.client("close-menu", "shader-window-close-native-menu")
        val coverage = linkedMapOf<Triple<Int, Boolean, Boolean>, String>()
        var firstGameTime: Long? = null
        var lastGameTime = 0L
        while (coverage.size < 32) {
            check(System.nanoTime() < deadline) { "native shader palette matrix incomplete within one daylight-cycle wall budget: ${coverage.keys}" }
            val clock = io.world("readiness-observe", "shader-native-clock-${io.next}")
            val currentDay = GameplayFeatureContract.nativeDay(clock)
            var attempted = false
            for (index in 0..7) for (signal in listOf(false, true)) {
                if (Triple(index, signal, currentDay) in coverage) continue
                check(System.nanoTime() < deadline) { "native shader palette capture deadline" }
                io.world("campfire-view", "shader-view-$index-$signal-${io.next}", mapOf("index" to index, "signal" to signal))
                Thread.sleep(800) // native camera/terrain/Weather2 settle; no input synthesis
                val frame = io.capture("shader-palette-$index-$signal-${io.next}", requireShader = true)
                require(frame.path("third_party_shader_state").path("pack_sha256").asText() == packHash) { "active shader ZIP differs from unchanged candidate fixture" }
                val actualDay = GameplayFeatureContract.nativeDay(frame)
                coverage[Triple(index, signal, actualDay)] = frame.path("framebuffer").asText()
                val time = frame.path("game_time").asLong()
                if (firstGameTime == null) firstGameTime = time
                require(time >= lastGameTime) { "native shader observation clock went backwards" }; lastGameTime = time
                require(time - firstGameTime!! <= 24000) { "native shader visual window exceeded one native daylight cycle" }
                evidence.event("gameplay_shader_palette_native_frame", mapOf("palette_index" to index, "signal" to signal,
                    "native_day" to actualDay, "day_time" to frame.path("day_time").asLong(), "game_time" to time,
                    "frame" to frame.path("framebuffer").asText(), "shader_pack_sha256" to packHash,
                    "visual_review" to "PENDING actual native framebuffer inspection"))
                attempted = true
            }
            if (!attempted && coverage.size < 32) Thread.sleep(15000)
        }
        val worldAfter = io.world("readiness-observe", "shader-window-world-after")
        for (field in listOf("world_root", "world_seed", "server_process", "candidate_binding"))
            require(worldBefore.path(field) == worldAfter.path(field)) { "shader window changed native world/process/candidate: $field" }
        require(worldAfter.path("game_time").asLong() >= worldBefore.path("game_time").asLong())
        require(Hashes.sha256(productionConfig) == productionHash) { "production Oculus configuration changed" }
        io.world("core", "shader-window-return-core")
        client.expectNativeExit("bound shader-window Minecraft.stop request")
        io.client("exit", "shader-window-native-client-exit")
        awaitNativeClientExit(client, evidence, nonce, "shader-window")
        server.assertHashes(); client.assertHashes()
        evidence.event("gameplay_shader_visual_matrix_complete", mapOf("nonce" to nonce.toString(),
            "native_palette_phase_frames" to coverage.map { (key, frame) -> mapOf("palette_index" to key.first,
                "signal" to key.second, "native_day" to key.third, "frame" to frame) },
            "native_shader_in_use" to true, "pack" to "ComplementaryReimagined_r5.8.1.zip", "pack_sha256" to packHash,
            "visual_review" to "PENDING framebuffer inspection", "human_pointer" to "NOT_PERFORMED",
            "performance_scope" to "visual window only; full gameplay soak remained unshaded"))
    }

    fun finishFirstVisit(server: DedicatedServerFixture, client: ClientFixture, evidence: EvidenceRun, nonce: UUID) {
        Bridge(server, client, evidence, nonce, 19000).world("trader-complete", "v1-natural-trader-complete")
    }

    fun closeNativeClient(server: DedicatedServerFixture, client: ClientFixture, evidence: EvidenceRun, nonce: UUID, visit: Int) {
        client.expectNativeExit("bound gameplay visit-$visit Minecraft.stop request")
        Bridge(server, client, evidence, nonce, if (visit == 1) 19900 else 29900).client("exit", "v$visit-native-client-exit")
        awaitNativeClientExit(client, evidence, nonce, "visit-$visit")
    }

    private fun awaitNativeClientExit(client: ClientFixture, evidence: EvidenceRun, nonce: UUID, label: String) {
        client.awaitNativeExit(Duration.ofMinutes(2), "native Minecraft.stop $label")
        evidence.event("gameplay_native_client_stopped", mapOf("stage" to label, "nonce" to nonce.toString(),
            "origin" to "public Minecraft.stop after bound exit ack; no input injection"))
    }

    internal fun impactSounds(observations: JsonNode) = observations.path("sound_dispatch_events").count { it.path("id").asText() == IMPACT }

    private class Bridge(val server: DedicatedServerFixture, val clientFixture: ClientFixture, val evidence: EvidenceRun,
                         val nonce: UUID, initialSequence: Int) {
        var next = initialSequence
        fun world(action: String, label: String, extra: Map<String, Any> = emptyMap()) = request("world", action, label, extra)
        fun client(action: String, label: String, extra: Map<String, Any> = emptyMap()) = request("client", action, label, extra)
        fun capture(label: String, requireShader: Boolean = false): JsonNode {
            val node = client("capture", label, mapOf("require_shader" to requireShader))
            if (requireShader) GameplayFeatureContract.requireActiveShader(node)
            else require(!node.path("third_party_shader_state").path("enabled").asBoolean(true)
                && !node.path("third_party_shader_state").path("in_use").asBoolean(true)) { "default unshaded profile unexpectedly used shaders" }
            val image = server.diagnosticSupport.bridge.resolve(node.path("framebuffer").asText())
            require(Files.isRegularFile(image) && Files.size(image) > 1024) { "missing/empty native framebuffer" }
            evidence.event("gameplay_framebuffer", mapOf("run" to evidence.config.runId, "nonce" to nonce.toString(),
                "label" to label, "path" to image.toString(), "sha256" to Hashes.sha256(image),
                "renderer" to mapper.convertValue(node.path("renderer"), Map::class.java),
                "physical_audio" to node.path("physical_audio_verdict").asText(), "human_pointer" to "NOT_PERFORMED"))
            return node
        }
        private fun diagnosticHash(side: String): String = Regex("-Dbc\\.pack_test\\.diagnostics\\.${side}_sha256=([a-f0-9]{64})")
            .find(server.diagnosticSupport.properties)?.groupValues?.get(1) ?: error("missing exact candidate diagnostic hash")
        private fun request(side: String, action: String, label: String, extra: Map<String, Any>): JsonNode {
            val seq = ++next
            val root = server.diagnosticSupport.bridge
            val request = root.resolve("gameplay-$side.request")
            check(!Files.exists(request)) { "unconsumed gameplay $side request" }
            val fields = mapOf("schema" to "bc.gameplay_${side}_request.v1", "run" to evidence.config.runId,
                "nonce" to nonce.toString(), "player" to clientFixture.uuid.toString(), "sequence" to seq, "action" to action, "label" to label,
                "client_sha256" to diagnosticHash("client"), "server_sha256" to diagnosticHash("server")) + extra
            val pending = root.resolve("gameplay-$side-$seq-$label.pending")
            Files.writeString(pending, mapper.writeValueAsString(fields), StandardOpenOption.CREATE_NEW)
            Files.move(pending, request, StandardCopyOption.ATOMIC_MOVE)
            val output = root.resolve("gameplay-$side-$seq-$label.json")
            val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
            while (!Files.isRegularFile(output)) {
                check(server.processAlive()) { "server exited waiting for gameplay $side $action" }
                check(System.nanoTime() < deadline) { "gameplay $side $action timeout; retained $request / $output" }
                Thread.sleep(20)
            }
            val node = mapper.readTree(output.toFile())
            GameplayFeatureContract.requireBinding(node, side, evidence.config.runId, nonce.toString(), clientFixture.uuid.toString(), seq, action, label)
            return node
        }
    }
}

object GameplayFeatureContract {
    fun nativeDay(node: JsonNode): Boolean {
        require(node.path("native_day").isBoolean && node.path("native_night").isBoolean)
        val day = node.path("native_day").asBoolean(); val night = node.path("native_night").asBoolean()
        require(day != night) { "native Overworld day/night classification missing or contradictory" }
        return day
    }
    fun requireOppositeDayNight(first: JsonNode, second: JsonNode) {
        require(nativeDay(first) != nativeDay(second)) { "visit labels/time offsets do not prove native day and night" }
    }
    fun requireActiveShader(frame: JsonNode) {
        val shader = frame.path("third_party_shader_state")
        require(shader.path("enabled").asBoolean() && shader.path("in_use").asBoolean()
            && !shader.path("fallback").asBoolean(true) && !shader.path("stored_error").asBoolean(true)
            && !shader.path("shadow_pass").asBoolean(true) && shader.path("native_pack_present").asBoolean())
        require(shader.path("native_pack_name").asText() == "ComplementaryReimagined_r5.8.1.zip"
            && shader.path("pack_version").asText() == "5.8.1" && shader.path("pack_sha256").asText().matches(Regex("[a-f0-9]{64}")))
        require(shader.path("verdict").asText() == "ACTIVE_NATIVE_SHADERPACK") { "native shader request silently fell back to disabled" }
    }
    fun requireBinding(node: JsonNode, side: String, run: String, nonce: String, player: String, sequence: Int, action: String, label: String) {
        require(node.path("schema").asText() == "bc.gameplay_${side}_observation.v1")
        require(node.path("run").asText() == run && node.path("nonce").asText() == nonce && node.path("player").asText() == player)
        require(node.path("sequence").asInt(-1) == sequence && node.path("action").asText() == action && node.path("label").asText() == label)
    }
    fun requireRising(node: JsonNode) {
        require(node.path("sounds_dropped").asLong(-1) == 0L)
        require(GameplayFeatureClient.impactSounds(node) == 1) { "real rising edge must dispatch exactly one impact sound" }
        require(node.path("native_sources_dropped").asLong(-1) == 0L)
        val playing = node.path("native_source_events").filter { it.path("id").asText() == "better_survival_physiology:aspect.impact"
            && it.path("channel_playing_on_native_event_thread").asBoolean() && it.path("resolved_asset_path").asText().isNotBlank() }
        require(playing.size == 1) { "readiness cue was dispatched but not observed playing on its actual native audio channel" }
        require(node.path("native_particle_counts").path("minecraft:dust").asLong() >= 9) { "real rising edge missing native dust particles" }
    }
    fun requireCut(node: JsonNode) {
        require(node.path("cut_calls_dropped").asLong(-1) == 0L)
        val calls = node.path("native_cut_calls").toList()
        require(calls.any { it.path("progress_after_native_call").asDouble() > 0 }) { "no actual native cut progress" }
        require(calls.zipWithNext().all { (a, b) -> a.path("nanos").asLong() <= b.path("nanos").asLong() })
        val intermediate = calls.filter { it.path("progress_after_native_call").asDouble() > 0 && it.path("progress_after_native_call").asDouble() < 1 }
        require(intermediate.size >= 2) { "insufficient actual intermediate native cutting samples" }
        val ticks = intermediate.map { it.path("tick").asLong() }.toSet()
        require(ticks.size == intermediate.size) { "native cutting called more than once per actual client tick" }
        val chips = node.path("compat_chip_samples").filter { it.path("tick").asLong() in ticks }
        require(node.path("compat_chip_samples_dropped").asLong(-1) == 0L)
        val expected = kotlin.math.floor(intermediate.sumOf { (2.0 + 16.0 * it.path("progress_after_native_call").asDouble()) * .05 } + 1e-6).toInt()
        require(expected > 0 && chips.size == expected) { "actual Compat native chip rate differs from 2..18/sec completion policy: observed=${chips.size} expected=$expected" }
        require(chips.all { it.path("x").asDouble() in 999999.8..1000001.2 && it.path("y").asDouble() in 315.8..317.3 && it.path("z").asDouble() in 1000019.8..1000021.2 }) {
            "attributed native wood chips were not at the actual authored cut block"
        }
    }
}
