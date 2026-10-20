package com.bettercontent.tests

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

/** Each method owns a fresh native world/client/server/session; no singleton probe replay. */
@Tag("interactions")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@EnabledIfEnvironmentVariable(named = "BC_TEST_TIER", matches = "debug")
class NativeInteractionFamilyRuntimeTest {
    companion object {
        @JvmField @RegisterExtension val evidence = EvidenceExtension("interactions-families", evidenceSuffix = "families")
        private val families = mapOf("nativeTntBuriedTransactions" to "tnt-buried", "nativeRegolithTransactions" to "regolith",
            "nativeWildfireTransactions" to "fire", "nativeSealTransactions" to "seals", "nativeKineticLossTransactions" to "kinetics",
            "nativeOc2ProviderApiTransactions" to "provider-api", "nativeWirelessTransactions" to "wireless",
            "nativeDeviceBusTransactions" to "nativebus", "nativeKineticOverloadTransactions" to "overload")

        internal fun deferredVerdict(result: JsonNode): String {
            val config = result.path("effective_eo_configuration")
            fun flag(key: String): Boolean { require(config.path(key).isBoolean) { "missing typed EO field $key" }; return config.path(key).booleanValue() }
            require(config.path("config_sha256").asText().matches(Regex("[a-f0-9]{64}"))
                && config.path("mod_sha256").asText().matches(Regex("[a-f0-9]{64}")) && flag("common_spec_loaded"))
            val async = flag("async_crater"); val direct = flag("direct_chunk_writes"); val destruction = flag("crater_destruction")
            require(config.path("tnt_source_mode").isTextual && config.path("tnt_source_mode").asText().isNotBlank())
            val active = async && direct && destruction && config.path("tnt_source_mode").asText() == "DEFAULT"
            require(config.path("deferred_required").isBoolean && config.path("deferred_required").booleanValue() == active)
            val verdict = if (active) "REQUIRED" else "INACTIVE_CONDITION"
            require(result.path("deferred_applicability").asText() == verdict)
            if (active) require(result.path("native_deferred_path_exercised").asBoolean(false)) { "active native deferred composition is uncovered" }
            return verdict
        }
    }

    private lateinit var child: EvidenceRun
    private lateinit var server: DedicatedServerFixture
    private lateinit var client: ClientFixture
    private lateinit var second: ClientFixture
    private lateinit var io: GameplayFeatureClient.Bridge
    private lateinit var selected: String

    @BeforeEach fun start(info: TestInfo) {
        selected = families.getValue(info.testMethod.orElseThrow().name)
        child = evidence.run.fixtureFamily(selected)
        val nonce = child.fixtureSession
        val options = listOf(System.getenv("JAVA_TOOL_OPTIONS").orEmpty(), "-Dbc.pack_test.debug=true -Dbetter_pillager_campaigns.harness=true",
            "-Dbc.pack_test.gameplay_world=true -Dbc.pack_test.gameplay_world.nonce=$nonce").filter(String::isNotBlank).joinToString(" ")
        server = DedicatedServerFixture(child, mapOf("JAVA_TOOL_OPTIONS" to options), allowLongClientLogin = true)
        client = ClientFixture(child, server, "SmokeClient1", 1,
            "-Xms1G -Xmx4G -Dbc.pack_test.debug=true -Dbc.pack_test.gameplay_client=true -Dbc.pack_test.gameplay_client.nonce=$nonce")
        server.waitReady(); client.prepare(); client.launchDedicated(); client.waitDedicatedJoin(); client.waitSettled()
        val warm = when (selected) {
            "tnt-buried" -> -20 to -64; "regolith" -> 24 to -56; "fire" -> 64 to -56
            "nativebus" -> -101 to -117; "overload" -> -108 to -48; else -> -48 to -72
        }
        park(client, warm.first, warm.second)
        io = GameplayFeatureClient.Bridge(server, client, child, nonce, 0)
        io.client("close-menu", "$selected-close-menu")
        if (selected in setOf("tnt-buried", "nativebus")) {
            second = ClientFixture(child, server, "SmokeClient2", 2,
                "-Xms1G -Xmx4G -Dbc.pack_test.debug=true -Dbc.pack_test.gameplay_client=true -Dbc.pack_test.gameplay_client.nonce=$nonce")
            second.prepare(); second.launchDedicated(); second.waitDedicatedJoin(); second.waitSettled()
            if (selected == "tnt-buried") park(second, 64, 64) else park(second, -101, -105)
        }
        child.event("independent_native_family_ready", mapOf("family" to selected, "session" to nonce.toString(), "port" to server.port,
            "server_directory" to server.server.toString(), "acceptance_scope" to "selected family only; no unselected/future contracts or visual review implied"))
    }

    private fun park(actor: ClientFixture, x: Int, z: Int) {
        val px = 1_000_000 + x; val pz = 1_000_000 + z
        server.send("gamemode spectator ${actor.username}")
        server.send("execute in minecraft:overworld run tp ${actor.username} $px 316 $pz 180 12")
        // Authored support only in actual AIR; never clear/overwrite a foreign block or machine.
        server.send("execute in minecraft:overworld if block $px 315 $pz minecraft:air run setblock $px 315 $pz minecraft:stone")
        server.send("gamemode survival ${actor.username}")
        server.commandResult("better_pillager_campaigns harness protect ${actor.username}",
            Regex("Protected ${Regex.escape(actor.username)}.*gamemode=survival.*invulnerable=true"), "family Survival protection", Duration.ofSeconds(30))
        actor.waitSettled()
    }

    @AfterEach fun stop() {
        if (!::child.isInitialized) return
        closeAll(AutoCloseable {
            cleanupFixtures(buildList { if (::client.isInitialized) add(client); if (::second.isInitialized) add(second); if (::server.isInitialized) add(server) }) {
                child.event("process_cleanup", it)
            }
        }, AutoCloseable { if (::server.isInitialized) server.diagnosticSupport.preserveBridge() }, child)
    }

    private fun world(action: String, extra: Map<String, Any> = emptyMap()): JsonNode =
        io.world(action, "$selected-$action-${io.next}", extra).path("native_probe")
    private fun nativeClient(action: String, extra: Map<String, Any> = emptyMap()): JsonNode =
        io.client(action, "$selected-$action-${io.next}", extra).path("native_probe")
    private fun awaitWorld(action: String, timeout: Duration = Duration.ofSeconds(30), interval: Long = 500,
                           predicate: (JsonNode) -> Boolean): JsonNode {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (true) {
            if (action == "nativebus-observe") Thread.sleep(1000) // Source requires <=1Hz, including the first poll after a transition.
            val result = world(action)
            require(result.path("failure").asText("").isEmpty()) { "native family failed: $result" }
            if (predicate(result)) return result
            check(System.nanoTime() < deadline) { "native $selected/$action did not settle: $result" }
            if (action != "nativebus-observe") Thread.sleep(interval)
        }
    }
    private fun frame(action: String, label: String, extra: Map<String, Any>) {
        val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
        while (true) {
            val observation = nativeClient(action, extra)
            if (observation.path("ready").asBoolean() && observation.path("framebuffer_ready").asBoolean()) break
            check(System.nanoTime() < deadline) { "native renderer not ready: $observation" }
            Thread.sleep(100)
        }
        io.capture(label) // Actual native framebuffer; admission still requires independent LLM review.
    }
    private fun worldStart() {
        val extra = mutableMapOf<String, Any>("family" to selected)
        if (selected == "tnt-buried") extra["eo_config_sha256"] = Hashes.sha256(server.server.resolve("config/explosionoverhaul/explosionoverhaul-common.toml"))
        val result = world("world-hooks-start", extra)
        require(result.path("selected_family").asText() == selected)
    }
    private fun worldFinish(): JsonNode = world("world-hooks-finish").also {
        require(it.path("complete").asBoolean() && it.path("selected_family").asText() == selected)
        child.event("native_world_hooks_family", mapOf("family" to selected, "result" to it))
    }
    private fun systemsStart() {
        require(world("systems-start", mapOf("family" to selected)).path("selected_family").asText() == selected)
    }
    private fun systemsFinish(expected: Set<String>) {
        val result = world("systems-finish")
        require(result.path("complete").asBoolean() && result.path("passed").asBoolean()) { "$result" }
        require(result.path("required_checks").map(JsonNode::asText).toSet() == expected)
        for ((key, value) in result.path("checks").fields()) {
            if (key in expected) require(value.isBoolean && value.booleanValue()) else require(value.isNull) { "unselected family was credited: $key" }
        }
        child.event("native_systems_family", mapOf("family" to selected, "result" to result))
    }
    private fun systemsFrame(target: String, stage: String) {
        world("systems-view", mapOf("target" to target))
        frame("systems-client-observe", "systems-$selected-$stage-native-frame", mapOf("stage" to stage))
    }

    @Test @Order(10) fun nativeTntBuriedTransactions() = child.checkpoint("native TNT/buried ordinary and controlled withdrawn-candidate TTL") {
        worldStart()
        for (case in 0..4) {
            world("world-hooks-tnt-ignite", mapOf("case" to case))
            awaitWorld("world-hooks-observe", Duration.ofSeconds(120), 1000) {
                it.path("case_ready").asBoolean() && it.path("next_case").asInt() == case + 1
            }
        }
        val result = worldFinish()
        child.event("native_deferred_applicability", mapOf("verdict" to deferredVerdict(result), "reason" to "actual typed effective EO configuration; inactive is not a deferred native PASS"))
    }
    @Test @Order(20) fun nativeRegolithTransactions() = child.checkpoint("native 170-case Regolith tool/resource/action outcomes") {
        worldStart(); require(world("world-hooks-till").path("regolith_cases").size() == 170); worldFinish()
    }
    @Test @Order(30) fun nativeWildfireTransactions() = child.checkpoint("native fire/protected containers/scheduled completion and source-derived charred terrain models") {
        worldStart(); world("world-hooks-fire")
        awaitWorld("world-hooks-observe") { it.path("fire_scheduled_complete").asBoolean() }
        for (case in 0..2) {
            server.send("execute in minecraft:overworld run tp ${client.username} ${1_000_064 + case * 6 - 6} 316 999944 180 12")
            frame("world-hooks-client-ready", "wildfire-charred-$case-native-frame", mapOf("case" to case))
        }
        worldFinish()
    }
    @Test @Order(40) fun nativeSealTransactions() = child.checkpoint("native seal payment/refund and actual gas denial/release") {
        systemsStart(); awaitWorld("systems-sealed-observe") { it.path("ready").asBoolean() }; systemsFrame("basin", "sealed")
        world("systems-unseal-blocked"); awaitWorld("systems-blocked-observe") { it.path("ready").asBoolean() }
        world("systems-release"); awaitWorld("systems-released-observe") { it.path("ready").asBoolean() }; systemsFrame("basin", "released")
        systemsFinish(setOf("seal_payment_noop", "sealed_gas", "blocked_gas", "released_gas"))
    }
    @Test @Order(50) fun nativeKineticLossTransactions() = child.checkpoint("native32/64RPM loss/topology, separate from real overload") {
        systemsStart(); awaitWorld("systems-kinetic32-observe") { it.path("ready").asBoolean() }; systemsFrame("motor", "kinetic32")
        world("systems-speed64"); awaitWorld("systems-kinetic64-observe") { it.path("ready").asBoolean() }; systemsFrame("motor", "kinetic64")
        world("systems-topology-remove"); awaitWorld("systems-topology-observe") { it.path("ready").asBoolean() }; systemsFrame("motor", "topology")
        systemsFinish(setOf("kinetic32", "kinetic64", "topology_invalidation"))
    }
    @Test @Order(60) fun nativeOc2ProviderApiTransactions() = child.checkpoint("registered ObjectDevice provider API committed/noop, NOT bus/wire/guestCPU") {
        systemsStart(); world("systems-rpc-wireless"); systemsFrame("sticker", "sticker"); systemsFinish(setOf("rpc_committed_noop"))
    }
    @Test @Order(70) fun nativeWirelessTransactions() = child.checkpoint("native host/provider wireless FE/Unicode/delivery/denials, NOT wire/restart") {
        systemsStart(); world("systems-rpc-wireless"); systemsFinish(setOf("wireless_fe_delivery_denials"))
    }

    @Test @Order(80) fun nativeDeviceBusTransactions() = child.checkpoint("actual two-owner native bus/card/interface discovery/removal and current-bound callbacks, NOT guest wire") {
        fun b(action: String) {
            val bridge = GameplayFeatureClient.Bridge(server, second, child, child.fixtureSession, io.next)
            bridge.world(action, "$selected-$action-${io.next}"); io.next = bridge.next
        }
        fun observe(phase: String) {
            val result = awaitWorld("nativebus-observe", Duration.ofSeconds(45), 1000) { it.path("ready").asBoolean() }
            require(result.path("phase").asText() == phase && result.path("phase_passed").asBoolean()) { "$result" }
        }
        world("nativebus-start"); world("nativebus-place-a"); b("nativebus-place-b"); observe("empty")
        world("nativebus-insert-a"); b("nativebus-insert-b"); observe("cards")
        world("nativebus-add-interface"); observe("bound"); world("nativebus-callbacks")
        world("nativebus-remove-sticker"); observe("removed"); world("nativebus-remove-card-a"); observe("done")
        server.send("execute in minecraft:overworld if block 999904 315 999896 minecraft:air run setblock 999904 315 999896 minecraft:stone")
        server.send("execute in minecraft:overworld run tp ${client.username} 999904 316 999896 180 12")
        io.capture("nativebus-controller-removal-native-frame")
        val result = world("nativebus-finish", mapOf("capture_complete" to true))
        require(result.path("complete").asBoolean() && result.path("passed").asBoolean()) { "$result" }
        child.event("native_bus_family", mapOf("result" to result, "wire_guest_cpu_verdict" to "NOT_PERFORMED"))
    }
    @Test @Order(90) fun nativeKineticOverloadTransactions() = child.checkpoint("actual native crank/overstress/event boundary/repeat/base-excess/recovery/reentry") {
        val initial = world("overload-start")
        require(initial.path("critical_loads").isInt)
        nativeClient("overload-client-start", mapOf("critical_loads" to initial.path("critical_loads").asInt()))
        for ((index, stage) in listOf("below", "boundary", "repeat", "base_excess", "recovered", "reentry").withIndex()) {
            if (index > 0) world(when(stage) { "base_excess" -> "overload-base-excess"; "recovered" -> "overload-recover"; else -> "overload-$stage" })
            val observation = awaitWorld("overload-observe", Duration.ofSeconds(10), 50) { it.path("ready").asBoolean() }
            require(observation.path("passed").asBoolean() && observation.path("phase").asText() == stage) { "$observation" }
            frame("overload-client-observe", "overload-$stage-native-frame", mapOf("stage" to stage))
        }
        nativeClient("overload-client-finish", mapOf("capture_complete" to true))
        val result = world("overload-finish", mapOf("capture_complete" to true))
        require(result.path("complete").asBoolean() && result.path("passed").asBoolean()) { "$result" }
        child.event("native_overload_family", mapOf("result" to result, "visual_verdict" to "PENDING_LLM_REVIEW"))
    }
}
