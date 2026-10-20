package com.bettercontent.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.UUID

/** Short independently runnable native interaction families, separate from the measured soak.
 * All mutations remain in the candidate-bound nonshipping supplement, never production content. */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Tag("interactions")
@EnabledIfEnvironmentVariable(named = "BC_TEST_TIER", matches = "debug")
class InteractionRuntimeTest {
    companion object {
        private val mapper = jacksonObjectMapper()
        @JvmField @RegisterExtension val evidence = EvidenceExtension("interactions")
        private val nonce = UUID.randomUUID()
        private lateinit var server: DedicatedServerFixture
        private lateinit var client: ClientFixture
        private lateinit var second: ClientFixture
        private lateinit var io: GameplayFeatureClient.Bridge
        @JvmStatic @BeforeAll fun start() {
            require(evidence.run.tier == "debug")
            val options = listOf(System.getenv("JAVA_TOOL_OPTIONS").orEmpty(),
                "-Dbc.pack_test.debug=true -Dbetter_pillager_campaigns.harness=true",
                "-Dbc.pack_test.gameplay_world=true -Dbc.pack_test.gameplay_world.nonce=$nonce")
                .filter(String::isNotBlank).joinToString(" ")
            server = DedicatedServerFixture(evidence.run, mapOf("JAVA_TOOL_OPTIONS" to options), allowLongClientLogin = true)
            client = ClientFixture(evidence.run, server, "SmokeClient1", 1,
                "-Xms1G -Xmx4G -Dbc.pack_test.debug=true -Dbc.pack_test.gameplay_client=true -Dbc.pack_test.gameplay_client.nonce=$nonce")
            server.waitReady()
            client.prepare(); client.launchDedicated(); client.waitDedicatedJoin(); client.waitSettled()
            server.send("gamemode spectator ${client.username}")
            server.send("execute in minecraft:overworld run tp ${client.username} 999900 316 1000000")
            server.send("execute in minecraft:overworld run fill 999970 315 999970 1000030 315 1000030 minecraft:grass_block")
            server.send("execute in minecraft:overworld run tp ${client.username} 1000000 316 1000000")
            server.send("gamemode survival ${client.username}")
            server.commandResult("better_pillager_campaigns harness protect ${client.username}",
                Regex("Protected ${Regex.escape(client.username)}.*gamemode=survival.*invulnerable=true"),
                "native interaction Survival protection", Duration.ofSeconds(30))
            io = GameplayFeatureClient.Bridge(server, client, evidence.run, nonce, 0)
            io.client("close-menu", "interactions-native-menu-close")
        }
        @JvmStatic @AfterAll fun stop() {
            closeAll(AutoCloseable {
                cleanupFixtures(buildList {
                    if (::client.isInitialized) add(client)
                    if (::second.isInitialized) add(second)
                    if (::server.isInitialized) add(server)
                }) { evidence.run.event("process_cleanup", it) }
            }, AutoCloseable { if (::server.isInitialized) server.diagnosticSupport.preserveBridge() })
        }
    }

    @Test @Order(30) fun nativeEmiIngredientOrdering() = evidence.run.checkpoint("native EMI snapshot, explicit focus and transfer parity") {
        for (path in listOf("legacy-no-backpack", "legacy-equipped", "v2-no-backpack", "v2-equipped",
            "vanilla-no-backpack", "vanilla-equipped", "separate-backpack", "separate-upgraded")) {
            io.client("close-menu", "emi-$path-close-before")
            io.world("core", "emi-$path-core")
            io.world("emi-prepare", "emi-$path-native-prepare", mapOf("path" to path))
            awaitProbe("client", "emi-equipment-ready", "emi-$path-equipment", mapOf("path" to path))
            if (!path.startsWith("separate-")) io.client("emi-personal-open", "emi-$path-native-open")
            val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
            var menu = io.client("emi-menu-ready", "emi-$path-menu-${io.next}", mapOf("path" to path)).path("native_probe")
            while (!menu.path("ready").asBoolean()) {
                check(System.nanoTime() < deadline) { "native EMI menu/equipment never synchronized: $menu" }
                Thread.sleep(50)
                menu = io.client("emi-menu-ready", "emi-$path-menu-${io.next}", mapOf("path" to path)).path("native_probe")
            }
            io.capture("personal-$path-native-frame")
            io.client("emi-order", "emi-$path-native-order")
            io.capture("emi-$path-native-order-frame")
            io.world("emi-inputs-empty", "emi-$path-inputs-remove")
            val immutable = awaitProbe("client", "emi-snapshot-held", "emi-$path-snapshot-held")
            val reopened = awaitProbe("client", "emi-snapshot-reopen", "emi-$path-snapshot-reopen")
            require(immutable.path("existing_snapshot_immutable").asBoolean() && reopened.path("reopened_snapshot_fresh").asBoolean())
            io.capture("emi-$path-empty-reopen-native-frame")
            io.world("emi-inputs-full", "emi-$path-inputs-restore")
            awaitProbe("client", "emi-inputs-ready", "emi-$path-inputs-ready")
            val ordering = io.client("emi-order", "emi-$path-restored-native-order").path("native_probe")
            val focus = io.client("emi-explicit-focus", "emi-$path-native-focus").path("native_probe")
            require(focus.path("visible").asBoolean())
            val fill = io.client("emi-fill", "emi-$path-native-fill", mapOf("separate" to (path == "separate-backpack"))).path("native_probe")
            val recipe = fill.path("backing_recipe").asText()
            require(recipe.isNotBlank() && recipe == ordering.path("backing_recipe").asText())
            val transferDeadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
            var result = io.world("emi-verify-transfer", "emi-$path-native-result-${io.next}", mapOf("recipe" to recipe)).path("native_probe")
            while (!result.path("ready").asBoolean()) {
                check(System.nanoTime() < transferDeadline) { "native server never produced transferred recipe result: $result" }
                Thread.sleep(50)
                result = io.world("emi-verify-transfer", "emi-$path-native-result-${io.next}", mapOf("recipe" to recipe)).path("native_probe")
            }
            evidence.run.event("native_emi_path", mapOf("path" to path, "native_menu" to menu,
                "ordering" to ordering, "focus" to focus, "native_fill" to fill, "server_result" to result,
                "immutable_snapshot" to immutable, "fresh_reopen_snapshot" to reopened,
                "separate_backpack_installed_upgrade" to (path == "separate-upgraded")))
            io.client("close-menu", "emi-$path-native-close-after")
        }
        io.world("emi-prepare", "emi-native-restore", mapOf("path" to "restore"))
        server.assertHashes(); client.assertHashes(); server.auditLogs()
    }

    @Test @Order(20) fun nativeBeeCosmetics() = evidence.run.checkpoint("native colony cosmetics and synchronized controls") {
        server.commandResult("fillbiome 999996 312 999988 1000020 319 1000012 minecraft:plains",
            Regex("Successfully filled"), "authored native plains colony climate", Duration.ofSeconds(30))
        for (state in listOf("empty-nest", "occupied-nest", "inactive-apiary", "active-apiary")) {
            io.client("close-menu", "bees-$state-native-menu-close")
            var native = io.world("bees-setup", "bees-$state-native-world", mapOf("state" to state)).path("native_probe")
            // Real queen conditions include the unmodified day/weather clock. One natural
            // daylight cycle bounds eligibility; never fabricate isQueenSatisfied or set time.
            val eligibility = System.nanoTime() + Duration.ofMinutes(20).toNanos()
            while (!native.path("ready").asBoolean()) {
                check(System.nanoTime() < eligibility) { "native forest queen conditions never became eligible within one natural daylight cycle: $native" }
                Thread.sleep(1000)
                native = io.world("bees-observe", "bees-$state-conditions-${io.next}").path("native_probe")
            }
            for (control in if (state == "occupied-nest") listOf("normal", "reduced", "minimal", "disabled", "range") else listOf("normal")) {
                if (control == "range") io.world("bees-range", "bees-native-outside-range")
                beeWindow(state, control, "bees-$state-$control")
            }
            if (state == "occupied-nest") {
                val dense = io.world("bees-dense", "bees-native-dense-workload").path("native_probe")
                require(dense.path("native_occupied_nests").asInt() == 675)
                evidence.run.event("native_bee_dense_workload", mapOf("observation" to dense))
                beeWindow(state, "dense", "bees-dense-675")
                beeWindow(state, "reduced", "bees-dense-reduced-675")
                io.world("bees-unload", "bees-native-chunk-unload")
                val unloaded = beeWindow(state, "unload", "bees-native-unloaded")
                require(!unloaded.path("native_chunk_loaded").asBoolean())
                io.world("bees-return", "bees-native-chunk-return")
                io.world("bees-setup", "bees-native-reconnect-world", mapOf("state" to state))
                client.restartDedicated(1)
                beeWindow(state, "normal", "bees-native-reconnected")
            }
        }
        io.world("bees-setup", "bees-native-clear", mapOf("state" to "clear"))
        io.world("core", "bees-native-return-core")
        server.assertHashes(); client.assertHashes(); server.auditLogs()
    }

    private fun beeWindow(state: String, control: String, label: String): JsonNode {
        io.client("bee-window-start", "$label-arm", mapOf("state" to state, "control" to control))
        val observed = awaitProbe("client", "bee-window-observe", label)
        evidence.run.event("native_bee_window", mapOf("state" to state, "control" to control, "observation" to observed))
        // 100 actual native ticks drains the production particles' <=80-tick lifetime.
        // Keep tested settings installed until the native framebuffer is captured.
        io.capture("$label-native-frame")
        io.client("bee-window-finish", "$label-settings-restore")
        return observed
    }

    @Test @Order(10) fun nativePlantDrinkUse() = evidence.run.checkpoint("native timed plant drink consumption and negatives") {
        for (kind in listOf("plain", "full", "partial", "bogclear", "suncrest")) {
            io.world("core", "plant-$kind-return-core")
            io.world("plant-drink-start", "plant-$kind-native-start", mapOf("case" to kind))
            val deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos()
            var observed = io.world("plant-drink-observe", "plant-$kind-native-observe-${io.next}").path("native_probe")
            while (!observed.path("ready").asBoolean()) {
                check(System.nanoTime() < deadline) { "native plant drink did not complete within its bounded tick window" }
                Thread.sleep(50) // Poll observable completion; never assume elapsed sleep implies native readiness.
                observed = io.world("plant-drink-observe", "plant-$kind-native-observe-${io.next}").path("native_probe")
            }
            require(observed.path("case").asText() == kind && observed.path("elapsed_native_ticks").asInt() >= 40)
            evidence.run.event("native_plant_drink", mapOf("case" to kind, "observation" to observed,
                "origin" to "native_use_item_and_actual_player_ticks", "manual_only" to true))
        }
        val dump = server.runtimeDump()
        val snapshotId = RuntimeSnapshotValidator.validate(dump)
        val active = mapper.readTree(evidence.run.config.root.resolve("gradle/active-custom-mods.json").toFile()).path("mods")
        val artifact = active.single { it.path("repository").asText() == "better-drinking-water" }.path("artifact").asText()
        val catalogue = PlantRuntimeCatalogue.verify(server.server.resolve("mods").resolve(artifact), dump)
        evidence.run.event("native_plant_recipe_catalogue", catalogue + mapOf("runtime_snapshot_id" to snapshotId,
            "snapshot_files" to listOf("snapshot.json", "recipes.json", "registries.json", "mods.json").associateWith { Hashes.sha256(dump.resolve(it)) }))
        for (page in 0..3) {
            val stock = io.world("plant-stock-page", "plant-stock-page-$page", mapOf("page" to page)).path("native_probe")
            require(stock.path("active_profiles").asInt() == catalogue["active_profiles"])
            awaitProbe("client", "plant-stock-ready", "plant-stock-page-$page-sync",
                mapOf("items" to stock.path("items").map { it.asText() }))
            io.client("emi-personal-open", "plant-stock-page-$page-native-open")
            awaitProbe("client", "emi-menu-ready", "plant-stock-page-$page-native-menu", mapOf("path" to "no-backpack"))
            io.capture("plant-stock-page-$page-native-frame")
            evidence.run.event("native_plant_stock_page", mapOf("observation" to stock))
            io.client("close-menu", "plant-stock-page-$page-native-close")
        }
        require(io.world("plant-stock-finish", "plant-stock-native-restore").path("native_probe").path("restored").asBoolean())
        server.assertHashes(); client.assertHashes(); server.auditLogs()
    }

    @Test @Order(40) fun nativeStructureChestLoot() = evidence.run.checkpoint("native selected-table ordinary chest rolls") {
        val result = io.world("structure-loot", "selected-structure-native-chests").path("native_probe")
        val selected = mapper.readTree(evidence.run.config.root.resolve("src/test/resources/structure-loot-review.json").toFile())
            .path("native_probe").path("tables").map { it.asText() }.toSet()
        require(result.path("tables").map { it.asText() }.toSet() == selected && result.path("samples").size() == selected.size * 16)
        require(result.path("samples").all { it.path("native_second_open_unchanged").asBoolean() })
        evidence.run.event("native_structure_chest_rolls", mapOf("tables" to selected.size, "samples" to selected.size * 16,
            "origin" to "authored_ordinary_native_chest", "generated_structure_reachability" to "NOT_VERIFIED",
            "lootr_per_player_cache" to "NOT_VERIFIED", "complete_progression_forbidden_matrix" to "NOT_VERIFIED"))
        server.assertHashes(); client.assertHashes(); server.auditLogs()
    }

    @Test @Order(50) fun nativeLootrPersonalAndGeneratedConsumers() = evidence.run.checkpoint("native Lootr personal caches and generated consumers") {
        io.client("close-menu", "lootr-native-close")
        io.world("core", "lootr-native-core")
        val review = evidence.run.config.root.resolve("src/test/resources/structure-loot-review.json")
        val reviewHash = Hashes.sha256(review)
        Files.copy(review, server.diagnosticSupport.bridge.resolve("structure-loot-review.json"), StandardCopyOption.REPLACE_EXISTING)
        second = ClientFixture(evidence.run, server, "SmokeClient2", 2, "-Xms1G -Xmx4G -Dbc.pack_test.debug=true")
        second.prepare(); second.launchDedicated(); second.waitDedicatedJoin(); second.waitSettled()
        server.send("gamemode survival ${second.username}")
        server.send("execute in minecraft:overworld run tp ${second.username} 1000002 316 1000000")
        server.commandResult("better_pillager_campaigns harness protect ${second.username}",
            Regex("Protected ${Regex.escape(second.username)}.*gamemode=survival.*invulnerable=true"),
            "native second Lootr player protection", Duration.ofSeconds(30))
        // New bounded workload: native personal caches for 15 tables plus a radius-64
        // locate/FULL-chunk generated-structure witness; never retries a failed roll.
        val result = io.world("structure-loot-personal", "lootr-personal-generated-native",
            mapOf("review_sha256" to reviewHash), Duration.ofMinutes(10)).path("native_probe")
        evidence.run.event("native_lootr_and_generated_consumers", mapOf("review_sha256" to reviewHash, "observation" to result))
        require(Hashes.sha256(review) == reviewHash) { "review input drift during native loot workload" }
        require(result.path("complete").asBoolean()) { "native Lootr/generated-consumer qualification incomplete: $result" }
        server.assertHashes(); client.assertHashes(); second.assertHashes(); server.auditLogs()
        second.close()
    }

    @Test @Order(60) fun nativeLifecycleInteractions() = evidence.run.checkpoint("native replacement clone Arena ledger and late cancellation") {
        io.client("close-menu", "lifecycle-native-close")
        io.world("core", "lifecycle-native-core")
        io.world("emi-prepare", "lifecycle-native-equipped-backpack", mapOf("path" to "legacy-equipped"))
        awaitProbe("client", "emi-equipment-ready", "lifecycle-native-equipment-sync", mapOf("path" to "legacy-equipped"))
        val start = io.world("lifecycle-start", "lifecycle-native-start").path("native_probe")
        evidence.run.event("native_lifecycle_started", mapOf("observation" to start))
        awaitProbe("world", "lifecycle-observe", "lifecycle-native-arena-ready")
        val death = io.world("lifecycle-kill", "lifecycle-native-kill").path("native_probe")
        evidence.run.event("native_lifecycle_death", mapOf("observation" to death))
        val requested = awaitProbe("client", "lifecycle-respawn", "lifecycle-native-respawn-request",
            ready = { it.path("respawn_requested").asBoolean() })
        val serverReplacement = awaitProbe("world", "lifecycle-observe", "lifecycle-native-server-replacement")
        val clientReplacement = awaitProbe("client", "lifecycle-observe", "lifecycle-native-client-replacement")
        evidence.run.event("native_lifecycle_replacement", mapOf("request" to requested,
            "server" to serverReplacement, "client" to clientReplacement))
        require(serverReplacement.path("passed").asBoolean() && clientReplacement.path("passed").asBoolean())
        io.world("core", "lifecycle-native-negative-core")
        val negative = io.world("lifecycle-cancel-negative", "lifecycle-native-late-cancel").path("native_probe")
        evidence.run.event("native_lifecycle_cancel_negative", mapOf("observation" to negative))
        require(negative.path("passed").asBoolean()) { "late native LivingDeath cancellation leaked accepted side effects: $negative" }
        val completed = io.world("lifecycle-finish", "lifecycle-native-complete").path("native_probe")
        require(completed.path("passed").asBoolean())
        io.world("emi-prepare", "lifecycle-native-equipment-restore", mapOf("path" to "restore"))
        server.assertHashes(); client.assertHashes(); server.auditLogs()
    }

    private fun awaitProbe(side: String, action: String, label: String, extra: Map<String, Any> = emptyMap(),
                           timeout: Duration = Duration.ofSeconds(30), ready: (JsonNode) -> Boolean = { it.path("ready").asBoolean() }): JsonNode {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (true) {
            val node = (if (side == "world") io.world(action, "$label-${io.next}", extra)
                else io.client(action, "$label-${io.next}", extra)).path("native_probe")
            if (ready(node)) return node
            check(System.nanoTime() < deadline) { "native $side/$action readiness deadline: $node" }
            Thread.sleep(50)
        }
    }
}
