package com.bettercontent.tests

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration

@Tag("multiplayer")
@TestMethodOrder(OrderAnnotation::class)
class MultiplayerRuntimeTest {
    companion object {
        @JvmField @RegisterExtension val evidence = EvidenceExtension("multiplayer")
        lateinit var server: DedicatedServerFixture
        lateinit var clients: List<ClientFixture>
        var settled = false
        var dimensions = false
        var campaignSoak = false

        private val usernames = (1..3).map { "SmokeClient$it" }
        private val positions = listOf(
            0 to 0,
            10_000 to 0,
            20_000 to 0,
        )
        private const val SOAK_TICKS = 36_000L
        private const val DEFAULT_SOAK_SECONDS = 30 * 60L
        // A full-pack client can otherwise drive the shared smoke-test host into its
        // memory ceiling during Lost Cities reloads. Four GiB clears TACZ's initial
        // model reload while leaving headroom for the server during the single-client
        // dimension smoke and the separate three-client soak.
        private const val CLIENT_JVM_ARGS = "-Xms1G -Xmx4G"

        @JvmStatic
        @BeforeAll
        fun start() {
            val inheritedJavaOptions = System.getenv("JAVA_TOOL_OPTIONS")?.trim().orEmpty()
            val harnessOptions = listOf(
                inheritedJavaOptions,
                "-Dpillager_campaigns.harness=true",
                if (evidence.run.tier == "debug") "-Dbc.pack_test.debug=true" else "",
            )
                .filter(String::isNotBlank).joinToString(" ")
            server = DedicatedServerFixture(
                evidence.run,
                mapOf("JAVA_TOOL_OPTIONS" to harnessOptions),
                allowLongClientLogin = true,
            )
            clients = usernames.mapIndexed { index, username ->
                ClientFixture(evidence.run, server, username, index + 1, CLIENT_JVM_ARGS)
            }
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            cleanupFixtures(buildList {
                if (::clients.isInitialized) addAll(clients)
                if (::server.isInitialized) add(server)
            }) { evidence.run.event("process_cleanup", it) }
        }
    }

    @Test @Order(1)
    fun leadClientJoinsFreshDedicatedServer() = evidence.run.checkpoint("single-client dimension smoke join") {
        server.waitReady()
        // The recipe graph exporter performs a large synchronous write on the server thread.
        // Run it before clients connect so its unavoidable pause cannot trip their network
        // heartbeat while the dimension inventory is being prepared.
        val dump = server.runtimeDump()
        Files.copy(
            dump.resolve("dimensions.json"),
            evidence.run.directory.resolve("dimensions.json"),
            StandardCopyOption.REPLACE_EXISTING,
        )
        // Generate and force-load the three distant campaign corridors while no clients are
        // connected.  Fresh overworld chunk generation can pause the dedicated server for more
        // than a network heartbeat; doing it during the campaign phase would make a valid client
        // liveness failure look like a gameplay failure.
        positions.forEachIndexed { index, (x, z) -> prepareCampaignPlatform(index + 1, x, z) }
        repeat(3) { sample ->
            server.commandResult(
                "forge tps",
                Regex("Overall: Mean tick time: [0-9.]+ ms\\. Mean TPS: 20\\.[0-9]+"),
                "prewarm recovery TPS sample ${sample + 1}",
                // The first report after forced generation still contains the
                // generation spike. Reissue the command until its rolling
                // window reflects the recovered server instead of treating
                // that stale report as a client-startup failure.
                Duration.ofMinutes(3),
                retryInterval = Duration.ofSeconds(30),
            )
            if (sample < 2) Thread.sleep(10_000)
        }
        val lead = clients.first()
        startClient(lead)
        requirePlayersOnline("dimension client joined", listOf(lead))
        settled = true
    }

    @Test @Order(2)
    fun everyFontAndCreatingSpaceDimensionStabilizesAtFreshLocations() {
        assumeTrue(settled, "client settle prerequisite failed")
        evidence.run.checkpoint("dimension traversal and TPS stabilization") {
            val lead = clients.first()
            requirePlayersOnline("dimension traversal start", listOf(lead))
            val inventory = evidence.run.directory.resolve("dimensions.json")
            require(Files.isRegularFile(inventory)) { "dimension inventory was not prepared before client login" }
            val targets = DimensionSmokePlan.discover(inventory, server.server.resolve("config/dimension_drink/fonts"))
            evidence.run.event("dimension_targets", mapOf(
                "count" to targets.size,
                "targets" to targets.map { mapOf("id" to it.id, "sources" to it.sources.sorted()) },
                "locations_per_target" to DimensionSmokePlan.positions.size,
            ))
            server.send("gamemode spectator ${lead.username}")
            targets.forEach { target ->
                if (DimensionSmokePlan.requiresFontTravel(target.id)) {
                    val marker = "BC_FONT_ONLY_DIRECT_TRAVEL_DENIED_${target.id.replace(':', '_').replace('/', '_')}_${lead.username}"
                    evidence.run.event("font_only_dimension_guard_started", mapOf(
                        "dimension" to target.id,
                        "sources" to target.sources.sorted(),
                        "direct_command" to "execute in ${target.id} run tp ${lead.username} 100000 200 100000",
                        "reason" to "Dimension Drink requires a Font authorization for this destination",
                    ))
                    server.send("execute in ${target.id} run tp ${lead.username} 100000 200 100000")
                    server.commandResult(
                        "execute as ${lead.username} at @s unless dimension ${target.id} run say $marker",
                        Regex(Regex.escape(marker)),
                        "Font-only travel authorization guard ${target.id} ${lead.username}",
                        Duration.ofSeconds(30),
                    )
                    evidence.run.event("font_only_dimension_guard_passed", mapOf("dimension" to target.id))
                    return@forEach
                }
                DimensionSmokePlan.positions.forEachIndexed { index, (x, z) ->
                    val marker = "BC_DIMENSION_HEARTBEAT_${target.id.replace(':', '_').replace('/', '_')}_${index}_${lead.username}"
                    evidence.run.event("dimension_teleport_started", mapOf(
                        "dimension" to target.id, "sources" to target.sources.sorted(), "location" to index,
                        "x" to x, "y" to 200, "z" to z,
                        "heartbeat_deadline_seconds" to 90,
                    ))
                    server.send("execute in ${target.id} run tp ${lead.username} $x 200 $z")
                    server.commandResult(
                        "execute as ${lead.username} at @s if dimension ${target.id} if entity @s[x=$x,y=200,z=$z,distance=..1] run say $marker",
                        Regex(Regex.escape(marker)),
                        "dimension and destination verification ${target.id} location $index ${lead.username}",
                        Duration.ofSeconds(90),
                    )
                    waitForStableTps(target, index)
                    evidence.run.event("dimension_teleport_passed", mapOf("dimension" to target.id, "location" to index))
                }
            }
            val falloutLog = server.server.resolve("logs/latest.log")
            val falloutFarWrites = FalloutWorldgenEvidence.cityRuinFarWriteCount(falloutLog)
            evidence.run.event("fallout_cityruins_worldgen_audit", mapOf(
                "far_chunk_writes" to falloutFarWrites,
                "required" to 0,
                "intact_ruin_probe" to "not deterministic: fresh fixtures use an unpinned world seed and cityruins is a random selector",
            ))
            FalloutWorldgenEvidence.requireNoCityRuinFarWrites(falloutLog)
            dimensions = true
        }
    }

    @Test @Order(3)
    fun threeSurvivalPlayersExerciseCampaignsAndSoak() {
        assumeTrue(dimensions, "dimension traversal prerequisite failed")
        evidence.run.checkpoint("three-player campaign and ${if (evidence.run.tier == "debug") "30-minute" else "two-minute"} Survival soak") {
            clients.drop(1).forEach {
                startClient(it)
                // Keep the newly joined soak client out of ordinary campaign eligibility
                // until all three players are ready for the synchronized fixture setup.
                server.send("gamemode spectator ${it.username}")
            }
            requireAllPlayersOnline("soak clients joined")
            // Dimension traversal leaves ordinary campaign pressure enabled, so a long
            // full-pack run may naturally have pending encounters by this point. Bring all
            // three clients to a confirmed spectator state before clearing the director. This
            // prevents a just-joined client's asynchronous mode transition from racing the
            // reset or the first Survival eligibility evaluation.
            clients.forEach { requireSpectator(it) }
            server.commandResult(
                "pillager_campaigns reset",
                Regex("Reset all invasion pressure"),
                "reset campaign fixture state",
                Duration.ofSeconds(30),
            )
            clients.forEachIndexed { index, client ->
                val (x, z) = positions[index]
                verifyCampaignPlatform(index + 1)
                server.commandResult(
                    "pillager_campaigns inspect ${client.username}",
                    Regex("campaign_inspect player=${Regex.escape(client.username)} encounter=none"),
                    "verify clean campaign state ${client.username}",
                    Duration.ofSeconds(30),
                )
                server.send("gamemode survival ${client.username}")
                server.commandResult(
                    "pillager_campaigns harness protect ${client.username}",
                    Regex("Protected ${Regex.escape(client.username)}.*gamemode=survival.*invulnerable=true"),
                    "protect ${client.username}",
                    Duration.ofSeconds(30),
                )
                server.send("execute in minecraft:overworld run tp ${client.username} $x 316 $z")
                server.commandResult(
                    "execute as ${client.username} at @s run say BC_PILLAGER_POSITION_${index + 1}",
                    Regex("BC_PILLAGER_POSITION_${index + 1}"),
                    "position ${client.username}",
                    Duration.ofSeconds(90),
                )
                server.commandResult(
                    "pillager_campaigns harness spawn scout immediate ${client.username} 5",
                    Regex("Harness started immediate scout .*${Regex.escape(client.username)}"),
                    "local campaign ${client.username}",
                    Duration.ofMinutes(2),
                )
                server.commandResult(
                    "pillager_campaigns inspect ${client.username}",
                    Regex("campaign_inspect player=${Regex.escape(client.username)} encounter=.* phase=active .* live=[1-9]"),
                    "active campaign ${client.username}",
                    Duration.ofMinutes(2),
                    retryInterval = Duration.ofSeconds(10),
                )
            }

            val soakSeconds = soakSecondsAtStart()
            evidence.run.event("pillager_soak_started", mapOf(
                "duration_seconds" to soakSeconds,
                "tier" to evidence.run.tier,
            ))
            val startedAt = System.nanoTime()
            val startedGameTime = gameTime()
            var nextSample = 0L
            while (System.nanoTime() - startedAt < Duration.ofSeconds(soakSeconds).toNanos()) {
                Thread.sleep(30_000)
                val elapsed = ((System.nanoTime() - startedAt) / 1_000_000_000L).coerceAtMost(soakSeconds)
                if (elapsed < nextSample) continue
                nextSample += 30
                check(serverAlive()) { "server exited during three-player soak; see ${server.log}" }
                clients.forEach { check(itAlive(it)) { "${it.username} client exited during soak; see ${it.log}" } }
                clients.forEach { client ->
                    server.commandResult(
                        "pillager_campaigns status ${client.username}",
                        Regex("eligible_ticks=[0-9]+"),
                        "campaign status ${client.username} at ${elapsed}s",
                        Duration.ofSeconds(30),
                    )
                }
                evidence.run.event("pillager_soak_sample", mapOf(
                    "elapsed_seconds" to elapsed,
                    "game_time" to gameTime(),
                    "players" to clients.map { it.username },
                ))
            }
            val elapsedTicks = gameTime() - startedGameTime
            require(elapsedTicks > 0) { "three-player soak did not advance the Overworld game clock" }
            evidence.run.event("pillager_soak_complete", mapOf(
                "duration_seconds" to soakSeconds,
                "game_ticks" to elapsedTicks,
                "nominal_game_ticks" to SOAK_TICKS,
            ))
            campaignSoak = true
        }
    }

    private fun soakSecondsAtStart(): Long = if (evidence.run.tier == "debug") DEFAULT_SOAK_SECONDS else 120L

    @Test @Order(4)
    fun debugServerRestartAndClientReconnectPreserveWorld() {
        if (evidence.run.tier != "debug") {
            evidence.run.event("scenario_omitted", mapOf("name" to "restart and reconnect", "tier" to evidence.run.tier))
            return
        }
        assumeTrue(campaignSoak, "campaign prerequisite failed")
        evidence.run.checkpoint("server restart and client reconnect") {
            val before = gameTime()
            server.commandResult(
                "execute in minecraft:overworld run setblock 0 100 0 minecraft:diamond_block",
                Regex("Changed the block at 0, 100, 0"),
                "persistent world marker",
                Duration.ofSeconds(30),
            )
            clients.forEach { it.close() }
            server.stopGracefully()
            server.restart()
            server.commandResult(
                "execute in minecraft:overworld if block 0 100 0 minecraft:diamond_block run say BC_RESTART_WORLD_PERSISTED",
                Regex("BC_RESTART_WORLD_PERSISTED"),
                "world marker after restart",
                Duration.ofSeconds(30),
            )
            check(gameTime() >= before) { "world game time moved backward across server restart" }
            val reconnect = ClientFixture(evidence.run, server, clients.first().username, 4, CLIENT_JVM_ARGS)
            clients = clients + reconnect
            startClient(reconnect)
            requirePlayersOnline("reconnect after restart", listOf(reconnect))
            evidence.run.event("restart_reconnect_passed", mapOf("player_uuid" to reconnect.uuid, "world_time_before" to before, "world_time_after" to gameTime()))
        }
    }

    @Test @Order(5)
    fun debugNativeFontRoundTrip() {
        if (evidence.run.tier != "debug") {
            evidence.run.event("scenario_omitted", mapOf("name" to "native Font round trip", "tier" to evidence.run.tier))
            return
        }
        assumeTrue(campaignSoak, "campaign prerequisite failed")
        evidence.run.checkpoint("native Font round trip") {
            val player = clients.last()
            server.commandResult(
                "execute as ${player.username} run font harness_enter end",
                Regex("BC_FONT_HARNESS_ENTER player=${Regex.escape(player.username)} template=end"),
                "native Font activation",
                Duration.ofMinutes(2),
            )
            server.commandResult(
                "execute as ${player.username} at @s if dimension minecraft:the_end run say BC_FONT_NATIVE_ENTERED",
                Regex("BC_FONT_NATIVE_ENTERED"),
                "native Font destination",
                Duration.ofMinutes(2),
                retryInterval = Duration.ofSeconds(10),
            )
            server.send("execute as ${player.username} run font return")
            server.commandResult(
                "execute as ${player.username} at @s if dimension minecraft:overworld run say BC_FONT_NATIVE_RETURNED",
                Regex("BC_FONT_NATIVE_RETURNED"),
                "native Font return",
                Duration.ofMinutes(2),
                retryInterval = Duration.ofSeconds(10),
            )
            evidence.run.event("native_font_roundtrip_passed", mapOf("player" to player.username, "template" to "end"))
        }
    }

    @Test @Order(6)
    fun multiplayerEvidenceIsCleanAndCandidatesAreUnchanged() {
        assumeTrue(campaignSoak, "three-player campaign soak prerequisite failed")
        evidence.run.checkpoint("multiplayer log and hash audit") {
            clients.forEach { it.close() }
            server.stopGracefully()
            server.auditLogs()
            server.assertHashes()
            clients.forEach { it.assertHashes() }
        }
    }

    private fun serverAlive(): Boolean = server.log.toFile().exists() && server.processAlive()

    private fun itAlive(client: ClientFixture): Boolean = client.processAlive()

    private fun requirePlayersOnline(stage: String, participants: List<ClientFixture>) {
        participants.forEach { client ->
            val marker = "BC_PLAYER_HEARTBEAT_${stage.replace(Regex("[^A-Za-z0-9]+"), "_")}_${client.username}"
            server.commandResult(
                "execute as ${client.username} at @s run say $marker",
                Regex(Regex.escape(marker)),
                "${stage} player heartbeat ${client.username}",
                Duration.ofSeconds(30),
            )
        }
        evidence.run.event("players_online", mapOf("stage" to stage, "players" to participants.map { it.username }))
    }

    private fun requireAllPlayersOnline(stage: String) {
        requirePlayersOnline(stage, clients)
    }

    private fun requireSpectator(client: ClientFixture) {
        val marker = "BC_PILLAGER_SPECTATOR_${client.username}"
        server.commandResult(
            "execute as ${client.username} if entity @s[gamemode=spectator] run say $marker",
            Regex(Regex.escape(marker)),
            "spectator readiness ${client.username}",
            Duration.ofSeconds(30),
        )
    }

    private fun startClient(client: ClientFixture) {
        client.prepare()
        client.launchDedicated()
        client.waitDedicatedJoin()
        // Start clients serially so one full-pack resource reload cannot starve another
        // client's network heartbeat. The dimension phase starts only the lead; the other
        // two are started once here for the separate Survival soak.
        client.waitSettled()
    }

    private fun gameTime(): Long {
        val result = server.commandResult(
            "time query gametime",
            Regex("The time is ([0-9]+)"),
            "Overworld game time",
            Duration.ofSeconds(30),
        )
        return result.groupValues[1].toLong()
    }

    private fun prepareCampaignPlatform(index: Int, x: Int, z: Int) {
        // The deterministic lead starts at the first ordered local offset (-72, -2). Keep the
        // forced area and block edits to the loaded approach corridor so setup cannot stall the
        // full-pack clients while a second 10,000-block-away pad is being prepared.
        val minX = x - 80
        val maxX = x + 16
        val minZ = z - 16
        val maxZ = z + 16
        // The production world remains untouched: this fixture-only pad gives the routed
        // campaign a deterministic, fully traversable local surface at each soak checkpoint.
        server.commandResult(
            "execute in minecraft:overworld run forceload add $minX $minZ $maxX $maxZ",
            Regex("Marked \\[-?[0-9]+, -?[0-9]+\\] chunks in minecraft:overworld from \\[-?[0-9]+, -?[0-9]+\\] to \\[-?[0-9]+, -?[0-9]+\\] to be force loaded"),
            "forceload campaign platform $index",
            Duration.ofMinutes(2),
        )
        server.send("execute in minecraft:overworld run fill ${x - 72} 315 ${z - 5} $x 315 ${z + 5} minecraft:grass_block")
        server.send("execute in minecraft:overworld run fill ${x - 72} 316 ${z - 5} $x 319 ${z + 5} minecraft:air")
        server.commandResult(
            "execute in minecraft:overworld run say BC_PILLAGER_PLATFORM_$index",
            Regex("BC_PILLAGER_PLATFORM_$index"),
            "campaign platform $index",
            Duration.ofMinutes(2),
        )
    }

    private fun verifyCampaignPlatform(index: Int) {
        // The pads were generated and force-loaded before client login. Re-running forceload
        // here can synchronously revisit a distant chunk set and exceed the client heartbeat;
        // the marker preserves an explicit campaign-phase audit without mutating the world.
        server.commandResult(
            "execute in minecraft:overworld run say BC_PILLAGER_PLATFORM_READY_$index",
            Regex("BC_PILLAGER_PLATFORM_READY_$index"),
            "verify campaign platform $index",
            Duration.ofSeconds(30),
        )
    }

    private fun waitForStableTps(target: DimensionTarget, location: Int) {
        val deadline = System.nanoTime() + Duration.ofSeconds(180).toNanos()
        var consecutive = 0
        var sample = 0
        var required = if (evidence.run.tier == "debug") 3 else 1
        while (System.nanoTime() < deadline && consecutive < required) {
            Thread.sleep(10_000)
            val result = server.commandResult(
                "forge tps",
                Regex("Overall.*?Mean TPS:\\s*[0-9]+(?:\\.[0-9]+)?", RegexOption.IGNORE_CASE),
                "TPS sample ${target.id} location $location",
                // A newly generated dimension can take longer than one
                // sampling interval to publish its next report. Keep the
                // sample open long enough to observe recovery instead of
                // converting that expected generation pause into a timeout.
                Duration.ofSeconds(90),
            )
            val tps = DimensionSmokePlan.parseOverallTps(result.value)
            if (tps < 18.0) required = 3
            consecutive = if (tps >= 18.0) consecutive + 1 else 0
            evidence.run.event("dimension_tps_sample", mapOf(
                "dimension" to target.id, "location" to location, "sample" to ++sample,
                "mean_tps" to tps, "required_tps" to 18.0, "consecutive_passing" to consecutive,
                "mode" to if (required == 3 && evidence.run.tier == "dist") "recovery" else evidence.run.tier,
            ))
        }
        require(consecutive >= required) {
            "${target.id} location $location did not produce $required consecutive >=18 TPS samples within 180 seconds"
        }
    }

}
