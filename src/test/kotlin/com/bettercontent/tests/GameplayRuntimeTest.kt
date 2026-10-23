package com.bettercontent.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.UUID
import java.util.jar.JarFile

/** Separate from MultiplayerRuntimeTest's explicitly authored campaign history fixture. */
@Tag("gameplay")
@EnabledIfEnvironmentVariable(named = "BC_TEST_TIER", matches = "debug")
class GameplayRuntimeTest {
    companion object {
        @JvmField @RegisterExtension val evidence = EvidenceExtension("gameplay")
        private val mapper = jacksonObjectMapper()
        private val nonce = UUID.randomUUID()
        private lateinit var server: DedicatedServerFixture
        private lateinit var client: ClientFixture
        private const val CORE = 1_000_000
        @JvmStatic @BeforeAll fun start() {
            require(evidence.run.tier == "debug" && evidence.run.target in setOf(null, "gameplay")) {
                "observed gameplay requires full or targeted Debug and candidate binding"
            }
            val options = listOf(System.getenv("JAVA_TOOL_OPTIONS").orEmpty(),
                "-Dbetter_pillager_campaigns.harness=true -Dbc.pack_test.debug=true",
                "-Dbc.pack_test.observed_campaign_presence=true -Dbc.pack_test.gameplay_spoilage=true",
                "-Dbc.pack_test.gameplay_world=true -Dbc.pack_test.gameplay_world.nonce=$nonce")
                .filter(String::isNotBlank).joinToString(" ")
            server = DedicatedServerFixture(evidence.run, mapOf("JAVA_TOOL_OPTIONS" to options), allowLongClientLogin = true)
            client = ClientFixture(evidence.run, server, "SmokeClient1", 1,
                "-Xms1G -Xmx4G -Dbc.pack_test.debug=true -Dbc.pack_test.gameplay_client=true -Dbc.pack_test.gameplay_client.nonce=$nonce")
        }
        @JvmStatic @AfterAll fun stop() {
            // Run every finalizer and retain original cleanup failures. Native WAVE lengths
            // are inspected only after the client/device shutdown has been attempted.
            closeAll(
                AutoCloseable {
                    cleanupFixtures(buildList {
                        if (::client.isInitialized) add(client)
                        if (::server.isInitialized) add(server)
                    }) { evidence.run.event("process_cleanup", it) }
                },
                AutoCloseable { if (::server.isInitialized) server.diagnosticSupport.preserveBridge() },
                AutoCloseable { gameplayAudioVerifyClosed(evidence.run) },
            )
        }
    }

    @Test fun gameplayAcceptance() = evidence.run.checkpoint("observed gameplay two-visit native presence") {
        server.waitReady()
        validateSelectedTraderPoolAgainstFreshRegistry()
        client.prepare(); client.launchDedicated(); client.waitDedicatedJoin(); client.waitSettled()
        server.send("gamemode spectator ${client.username}")
        server.send("execute in minecraft:overworld run tp ${client.username} ${CORE - 512} 316 $CORE")
        server.send("execute in minecraft:overworld run forceload add ${CORE - 80} ${CORE - 80} ${CORE + 80} ${CORE + 80}")
        server.send("execute in minecraft:overworld run fill ${CORE - 72} 315 ${CORE - 16} ${CORE + 72} 315 ${CORE + 16} minecraft:grass_block")
        server.send("execute in minecraft:overworld run fill ${CORE - 16} 315 ${CORE - 72} ${CORE + 16} 315 ${CORE + 72} minecraft:grass_block")
        server.commandResult("execute in minecraft:overworld if block $CORE 315 $CORE minecraft:grass_block run say BC_GAMEPLAY_GROUNDED_CORE",
            Regex("BC_GAMEPLAY_GROUNDED_CORE"), "fresh grounded platform exists", Duration.ofSeconds(30))
        barrier("prepared")
        submitPresence("arm"); awaitPresence("arm", Duration.ofSeconds(30))
        server.send("execute in minecraft:overworld run tp ${client.username} $CORE 316 $CORE")
        server.send("gamemode survival ${client.username}")
        server.commandResult("better_pillager_campaigns harness protect ${client.username}",
            Regex("Protected ${Regex.escape(client.username)}.*gamemode=survival.*invulnerable=true"), "living Survival protection", Duration.ofSeconds(30))
        server.commandResult("better_pillager_campaigns harness spawn scout immediate ${client.username} 5",
            Regex("Harness could not create a campaign for the target player"), "unfamiliar campaign rejection", Duration.ofSeconds(30))
        server.commandResult("better_pillager_campaigns inspect ${client.username}",
            Regex("campaign_inspect player=${client.username} encounter=none"), "negative has no encounter", Duration.ofSeconds(30))
        // Feature callers share this exact bound client/core, never a seed-history helper.
        evidence.run.event("gameplay_observation_started", mapOf("nonce" to nonce.toString(), "player" to client.uuid,
            "history_origin" to "observed_native_online_ticks", "minimum_online_seconds" to 1800,
            "minimum_native_ticks_per_visit" to 18000, "offline_ticks_counted" to 0))
        val firstDeadline = System.nanoTime() + Duration.ofMinutes(35).toNanos()
        GameplayFeatureClient.run(server, client, evidence.run, nonce, 1)
        val half = awaitPresenceUntil("half", firstDeadline)
        require(!half.path("halfFamiliar").asBoolean(true))
        GameplayFeatureClient.finishFirstVisit(server, client, evidence.run, nonce)
        server.send("gamemode spectator ${client.username}")
        barrier("first_visit_stopped")
        GameplayFeatureClient.closeNativeClient(server, client, evidence.run, nonce, 1)
        server.send("save-all flush")
        server.stopGracefully()
        val stopped = awaitPresence("stopped", Duration.ofSeconds(30))
        evidence.run.event("gameplay_first_visit_saved", mapOf("measurement" to stopped))
        server.restart()
        submitPresence("resume")
        val resumed = awaitPresence("resume", Duration.ofSeconds(30))
        require(!resumed.path("resumeFamiliar").asBoolean(true))
        client.restartDedicated(1)
        server.send("gamemode survival ${client.username}")
        server.send("execute in minecraft:overworld run tp ${client.username} $CORE 316 $CORE")
        server.commandResult("better_pillager_campaigns harness protect ${client.username}",
            Regex("Protected ${Regex.escape(client.username)}.*gamemode=survival.*invulnerable=true"),
            "restore living Survival protection after reconnect", Duration.ofSeconds(30))
        val secondDeadline = System.nanoTime() + Duration.ofMinutes(35).toNanos()
        GameplayFeatureClient.run(server, client, evidence.run, nonce, 2)
        val complete = awaitPresenceUntil("complete", secondDeadline)
        ObservedGameplayContract.validate(complete, evidence.run.config.runId, nonce.toString(), client.uuid.toString())
        require(complete.path("candidateClient").asText() == server.pair.clientSha256
            && complete.path("candidateServer").asText() == server.pair.serverSha256) { "checkpoint candidate drift" }
        evidence.run.event("gameplay_observed_presence_complete", mapOf("measurement" to complete))
        requireNativeFamiliarCampaign()
        // Shader configuration is fixture-only and starts strictly after the native
        // two-visit/restart/familiarity/live-campaign acceptance observations above.
        // The window owns its native quit/relaunch/final native quit sequence.
        GameplayFeatureClient.runShaderWindow(server, client, evidence.run, nonce)
        server.assertHashes(); client.assertHashes(); server.auditLogs()
        val logs = collectLogs(evidence.run.directory)
        LogPolicy.requireClean(logs, runtimeLogCompatibility(evidence.run, logs))
    }

    private fun validateSelectedTraderPoolAgainstFreshRegistry() {
        // This invocation's strict complete native dump is the oracle, never an old
        // snapshot or a sibling source checkout. Do not promote it to a global dump.
        val dump = server.runtimeDump()
        val snapshotId = RuntimeSnapshotValidator.validate(dump)
        val jar = server.server.resolve("mods/better-spirit-commerce-1.0.1.jar")
        require(Files.isRegularFile(jar) && !Files.isSymbolicLink(jar)) { "selected server lacks exact Commerce runtime JAR" }
        val jarHash = Hashes.sha256(jar)
        val poolText = JarFile(jar.toFile()).use { archive ->
            val entry = requireNotNull(archive.getJarEntry("data/better_spirit_commerce/spiritcommerce/wandering_pool.json")) {
                "selected Commerce JAR lacks its wandering pool resource"
            }
            require(entry.size in 1..1_048_576) { "unexpected packaged wandering pool resource size" }
            archive.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        val pool = mapper.readTree(poolText)
        val registries = mapper.readTree(dump.resolve("registries.json").toFile())
        val needed = GameplayPoolRegistryContract.validate(pool, registries)
        require(Hashes.sha256(jar) == jarHash) { "selected Commerce JAR changed during registry validation" }
        Files.writeString(evidence.run.directory.resolve("gameplay-wandering-pool.json"), poolText,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        server.assertHashes()
        evidence.run.event("runtime_snapshot", mapOf("snapshot_id" to snapshotId, "source" to dump.toString(),
            "scope" to "gameplay_packaged_wandering_pool_registry_resolution", "promoted_global" to false,
            "client_sha256" to server.pair.clientSha256, "server_sha256" to server.pair.serverSha256,
            "pool_jar" to jar.toString(), "pool_jar_sha256" to jarHash, "pool_rows" to pool.path("rows").size(),
            "needed_items" to needed, "needed_item_count" to needed.size))
    }

    private fun requireNativeFamiliarCampaign() {
        var accepted = false
        for ((attempt, offset) in listOf(0 to 0, 16 to 0, 0 to 16, -16 to 0, 0 to -16).withIndex()) {
            server.send("execute in minecraft:overworld run tp ${client.username} ${CORE + offset.first} 316 ${CORE + offset.second}")
            val result = server.commandResult("better_pillager_campaigns harness spawn scout immediate ${client.username} 5",
                Regex("Harness started immediate scout .*${Regex.escape(client.username)}|" +
                    "Immediate campaign lead could not validate a loaded approach after [0-9]+ candidate\\(s\\)|" +
                    "Harness could not create a campaign for the target player"),
                "observed-familiar native campaign attempt $attempt", Duration.ofSeconds(30))
            accepted = result.value.contains("Harness started immediate scout")
            evidence.run.event("gameplay_familiar_campaign_location", mapOf("attempt" to attempt,
                "x" to CORE + offset.first, "z" to CORE + offset.second, "accepted" to accepted, "result" to result.value))
            if (accepted) break
        }
        check(accepted) { "observed familiarity did not produce a native campaign at any bounded core location" }
        server.commandResult("better_pillager_campaigns inspect ${client.username}",
            Regex("campaign_inspect player=${Regex.escape(client.username)} encounter=.* phase=active .* live=[1-9]"),
            "observed-familiar campaign materializes live actors", Duration.ofMinutes(2), retryInterval = Duration.ofSeconds(10))
    }

    private fun barrier(label: String) = server.commandResult("say BC_GAMEPLAY_$label",
        Regex("BC_GAMEPLAY_$label"), "gameplay command barrier $label", Duration.ofSeconds(30))

    private fun submitPresence(action: String) {
        val request = server.diagnosticSupport.bridge.resolve("observed-presence.request")
        check(!Files.exists(request)) { "unconsumed observed presence request" }
        val pending = request.resolveSibling("observed-presence-$action-$nonce.pending")
        Files.writeString(pending, mapper.writeValueAsString(mapOf("schema" to "bc.observed_campaign_request.v1",
            "run" to evidence.run.config.runId, "nonce" to nonce.toString(), "player" to client.uuid.toString(), "action" to action)),
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        Files.move(pending, request, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun awaitPresence(kind: String, timeout: Duration): JsonNode =
        awaitPresenceUntil(kind, System.nanoTime() + timeout.toNanos())

    private fun awaitPresenceUntil(kind: String, deadline: Long): JsonNode {
        val output = server.diagnosticSupport.bridge.resolve("observed-presence-$kind.json")
        var nextReport = System.nanoTime()
        while (System.nanoTime() < deadline) {
            check(server.processAlive()) { "server exited observing $kind; retained bridge=${server.diagnosticSupport.bridge}" }
            if (kind == "half" || kind == "complete") check(client.processAlive()) { "real client exited during observed visit $kind" }
            if (Files.isRegularFile(output)) {
                val node = mapper.readTree(output.toFile())
                require(node.path("run").asText() == evidence.run.config.runId && node.path("nonce").asText() == nonce.toString()
                    && node.path("player").asText() == client.uuid.toString()) { "measurement binding mismatch" }
                return node
            }
            if (System.nanoTime() >= nextReport) {
                if (kind == "half" || kind == "complete") server.commandResult("list",
                    Regex("There are 1 of a max of [0-9]+ players online: ${Regex.escape(client.username)}"),
                    "real gameplay client remains online", Duration.ofSeconds(10))
                val progress = server.diagnosticSupport.bridge.resolve("observed-presence-progress.json")
                evidence.run.event("gameplay_observation_wait", mapOf("stage" to kind,
                    "utc" to java.time.Instant.now().toString(), "state" to if (Files.isRegularFile(progress)) Files.readString(progress) else "pending"))
                nextReport = System.nanoTime() + Duration.ofMinutes(1).toNanos()
            }
            Thread.sleep(500)
        }
        error("deadline expired for actual native presence $kind; retained measurement=$output; no elapsed/tick acceptance fabricated")
    }
}

/** Narrow current-output registry-resolution contract, not a historical pool baseline. */
object GameplayPoolRegistryContract {
    fun validate(pool: JsonNode, registries: JsonNode): List<String> {
        require(pool.path("schema").asText() == "bc.wandering_pool.v1") { "unexpected packaged wandering pool schema" }
        val rows = pool.path("rows")
        require(rows.isArray && rows.size() == 288) { "packaged wandering pool must contain 288 rows" }
        val items = registries.path("items")
        require(items.isObject) { "current native registry dump lacks the items object" }
        val ids = rows.map { row ->
            val id = row.path("result").path("id").asText()
            require(id.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")) && id != "minecraft:air") {
                "packaged pool has an invalid/air result: $id"
            }
            require(items.has(id)) { "packaged wandering pool result is absent from current native item registry: $id" }
            id
        }
        return ids.distinct().sorted()
    }
}

object ObservedGameplayContract {
    fun validate(node: JsonNode, run: String, nonce: String, player: String) {
        require(node.path("schema").asText() == "bc.observed_campaign_presence.v1")
        require(node.path("run").asText() == run && node.path("nonce").asText() == nonce && node.path("player").asText() == player)
        require(node.path("history_origin").asText() == "observed_native_online_ticks")
        require(node.path("firstTicks").asLong() in 18000 until 36000 && node.path("secondTicks").asLong() in 18000 until 36000)
        require(node.path("firstNanos").asLong() >= 900_000_000_000L && node.path("secondNanos").asLong() >= 900_000_000_000L)
        for (prefix in listOf("first", "second")) {
            require(node.path("${prefix}NativeEnd").asLong() - node.path("${prefix}NativeStart").asLong()
                >= node.path("${prefix}Ticks").asLong() - 1)
            require(node.path("${prefix}MonotonicEnd").asLong() - node.path("${prefix}MonotonicStart").asLong()
                >= node.path("${prefix}Nanos").asLong())
        }
        for (key in listOf("candidateClient", "candidateServer", "supportHash"))
            require(node.path(key).asText().matches(Regex("[a-f0-9]{64}")))
        require(node.path("offline_ticks_counted").asInt(-1) == 0)
        require(node.path("half").asBoolean() && node.path("resumed").asBoolean() && node.path("complete").asBoolean())
        require(!node.path("beforeFamiliar").asBoolean(true) && !node.path("halfFamiliar").asBoolean(true)
            && !node.path("resumeFamiliar").asBoolean(true) && node.path("finalFamiliar").asBoolean())
        require(node.path("world").asText().isNotBlank() && UUID.fromString(node.path("worldIdentity").asText()).toString() == node.path("worldIdentity").asText())
    }
}
