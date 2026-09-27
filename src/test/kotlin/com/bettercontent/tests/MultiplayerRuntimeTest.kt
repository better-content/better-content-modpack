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
        var joined = false
        var dimensions = false
        var campaignReady = false
        var fontRoundTrips = false

        private val usernames = (1..3).map { "SmokeClient$it" }
        private val positions = listOf(
            0 to 0,
            10_000 to 0,
            20_000 to 0,
        )
        // A full-pack client can otherwise drive the shared smoke-test host into its
        // memory ceiling during Lost Cities reloads. Four GiB clears TACZ's initial
        // model reload while leaving headroom for the server during the single-client
        // join and Debug's dimension traversal and three-client campaign check.
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
                ClientFixture(evidence.run, server, username, index + 1,
                    if (evidence.run.tier == "debug") "$CLIENT_JVM_ARGS -Dbc.pack_test.debug=true" else CLIENT_JVM_ARGS)
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
    fun leadClientJoinsFreshDedicatedServer() = evidence.run.checkpoint("single-client server join") {
        server.waitReady()
        if (evidence.run.tier == "debug") {
            // The recipe graph exporter performs a large synchronous write on the server thread.
            // Run it before clients connect so its pause cannot trip their network heartbeat.
            val dump = server.runtimeDump()
            Files.copy(
                dump.resolve("dimensions.json"),
                evidence.run.directory.resolve("dimensions.json"),
                StandardCopyOption.REPLACE_EXISTING,
            )
            if (evidence.run.target == null) {
                // Full Debug needs distant corridors ready before clients connect. Campaign
                // targets prepare their own pads; other focused targets do not use them.
                positions.forEachIndexed { index, (x, z) -> prepareCampaignPlatform(index + 1, x, z) }
                repeat(3) { sample ->
                    server.commandResult(
                        "forge tps",
                        Regex("Overall: Mean tick time: [0-9.]+ ms\\. Mean TPS: 20\\.[0-9]+"),
                        "prewarm recovery TPS sample ${sample + 1}",
                        Duration.ofMinutes(3),
                        retryInterval = Duration.ofSeconds(30),
                    )
                    if (sample < 2) Thread.sleep(10_000)
                }
            }
        }
        val lead = clients.first()
        startClient(lead)
        requirePlayersOnline("client joined", listOf(lead))
        joined = true
        if (evidence.run.target != null) {
            server.assertHashes()
            lead.assertHashes()
        }
    }

    private fun prepareTargetJoin(needsInventory: Boolean = false) {
        if (joined || evidence.run.target == null) return
        server.waitReady()
        if (needsInventory) {
            val dump = server.runtimeDump()
            Files.copy(dump.resolve("dimensions.json"), evidence.run.directory.resolve("dimensions.json"),
                StandardCopyOption.REPLACE_EXISTING)
        }
        val lead = clients.first()
        startClient(lead)
        requirePlayersOnline("target client joined", listOf(lead))
        joined = true
    }

    @Test @Order(3)
    fun everyFontAndCreatingSpaceDimensionStabilizesAtFreshLocations() {
        if (evidence.run.tier != "debug") {
            evidence.run.event("scenario_omitted", mapOf("name" to "dimension traversal and TPS stabilization", "tier" to evidence.run.tier))
            return
        }
        prepareTargetJoin(needsInventory = true)
        assumeTrue(joined, "client join prerequisite failed")
        evidence.run.checkpoint("dimension traversal and TPS stabilization") {
            val lead = clients.first()
            requirePlayersOnline("dimension traversal start", listOf(lead))
            val inventory = evidence.run.directory.resolve("dimensions.json")
            require(Files.isRegularFile(inventory)) { "dimension inventory was not prepared before client login" }
            val discovered = DimensionSmokePlan.discover(inventory, server.server.resolve("config/dimension_drink/fonts"))
            val targets = evidence.run.target?.takeIf { it.startsWith("dimension:") }
                ?.removePrefix("dimension:")?.let { id ->
                    listOf(discovered.singleOrNull { it.id == id } ?: error("target dimension is not loaded: $id"))
                } ?: discovered
            targets.forEach { GeometrySmokePlan.isTerrain(it.id) }
            val locations = DimensionSmokePlan.positions.take(3)
            evidence.run.event("dimension_targets", mapOf(
                "count" to targets.size,
                "targets" to targets.map { mapOf("id" to it.id, "sources" to it.sources.sorted()) },
                "locations_per_target" to locations.size,
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
                val geometry = mutableListOf<GeometrySample>()
                locations.forEachIndexed { index, (x, z) ->
                    val marker = "BC_DIMENSION_HEARTBEAT_${target.id.replace(':', '_').replace('/', '_')}_${index}_${lead.username}"
                    evidence.run.event("dimension_teleport_started", mapOf(
                        "dimension" to target.id, "sources" to target.sources.sorted(), "location" to index,
                        "x" to x, "y" to 200, "z" to z,
                        "heartbeat_deadline_seconds" to 90,
                    ))
                    val clientLogOffset = clientLogOffset(lead)
                    server.send("execute in ${target.id} run tp ${lead.username} $x 200 $z")
                    server.commandResult(
                        "execute as ${lead.username} at @s if dimension ${target.id} if entity @s[x=$x,y=200,z=$z,distance=..1] run say $marker",
                        Regex(Regex.escape(marker)),
                        "dimension and destination verification ${target.id} location $index ${lead.username}",
                        Duration.ofSeconds(90),
                    )
                    waitForClientPosition(lead, target.id, clientLogOffset, x shr 4, z shr 4)
                    if (GeometrySmokePlan.isTerrain(target.id)) {
                        geometry += probeGeometry(lead, target.id, "travel_${target.id.replace(Regex("[^A-Za-z0-9]+"), "_")}_$index")
                    }
                    waitForStableTps(target, index)
                    evidence.run.event("dimension_teleport_passed", mapOf("dimension" to target.id, "location" to index))
                }
                if (GeometrySmokePlan.isTerrain(target.id)) requireGeometry(target.id, geometry)
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
            if (evidence.run.target != null) {
                server.assertHashes()
                lead.assertHashes()
            }
        }
    }

    @Test @Order(4)
    fun threeSurvivalPlayersStartCampaigns() {
        if (evidence.run.tier != "debug") {
            evidence.run.event("scenario_omitted", mapOf("name" to "three-player campaigns", "tier" to evidence.run.tier))
            return
        }
        if (evidence.run.target == "campaign" || evidence.run.target == "campaign-start") {
            server.waitReady()
            positions.forEachIndexed { index, (x, z) -> prepareCampaignPlatform(index + 1, x, z) }
            if (evidence.run.target == "campaign-start") {
                // Reproduce the missing central floor found after the full dimension pass.
                // The campaign-phase repair must restore it before the lead path proof.
                server.commandResult(
                    "execute in minecraft:overworld run fill -16 315 -5 32 315 5 minecraft:air",
                    Regex("Successfully filled [1-9][0-9]* block\\(s\\)"),
                    "remove central campaign floor for repair check",
                    Duration.ofSeconds(30),
                )
                evidence.run.event("campaign_platform_repair_probe", mapOf("index" to 1, "removed_floor" to true))
            }
            startClient(clients.first())
            requirePlayersOnline("campaign lead joined", listOf(clients.first()))
        } else {
            assumeTrue(dimensions, "dimension traversal prerequisite failed")
        }
        evidence.run.checkpoint("three-player campaigns active") {
            clients.drop(1).forEach {
                startClient(it)
                // Keep newly joined clients out of ordinary campaign eligibility
                // until all three players are ready for the synchronized fixture setup.
                server.send("gamemode spectator ${it.username}")
            }
            requireAllPlayersOnline("campaign clients joined")
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
                verifyCampaignPlatform(index + 1, x, z)
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
                val offsets = listOf(0 to 0, 16 to 0, 0 to 16, -16 to 0, 0 to -16)
                var started = false
                for ((attempt, offset) in offsets.withIndex()) {
                    val targetX = x + offset.first
                    val targetZ = z + offset.second
                    server.send("execute in minecraft:overworld run tp ${client.username} $targetX 316 $targetZ")
                    server.commandResult(
                        "execute as ${client.username} at @s run say BC_PILLAGER_POSITION_${index + 1}_$attempt",
                        Regex("BC_PILLAGER_POSITION_${index + 1}_$attempt"),
                        "position ${client.username} attempt $attempt",
                        Duration.ofSeconds(90),
                    )
                    val result = server.commandResult(
                        "pillager_campaigns harness spawn scout immediate ${client.username} 5",
                        Regex("Harness started immediate scout .*${Regex.escape(client.username)}|" +
                            "Immediate campaign lead could not validate a loaded approach after [0-9]+ candidate\\(s\\)|" +
                            "Harness could not create a campaign for the target player"),
                        "local campaign ${client.username} attempt $attempt",
                        Duration.ofSeconds(30),
                    )
                    if (result.value.contains("Harness started immediate scout")) {
                        evidence.run.event("campaign_location_accepted", mapOf(
                            "player" to client.username, "attempt" to attempt, "x" to targetX, "z" to targetZ,
                        ))
                        started = true
                        break
                    }
                    evidence.run.event("campaign_location_rejected", mapOf(
                        "player" to client.username, "attempt" to attempt, "x" to targetX, "z" to targetZ,
                        "reason" to result.value,
                    ))
                    server.commandResult(
                        "pillager_campaigns inspect ${client.username}",
                        Regex("campaign_inspect player=${Regex.escape(client.username)} encounter=none"),
                        "campaign reset after rejected position ${client.username} attempt $attempt",
                        Duration.ofSeconds(30),
                    )
                }
                check(started) { "No local campaign for ${client.username} after ${offsets.size} player positions; see ${server.log}" }
                server.commandResult(
                    "pillager_campaigns inspect ${client.username}",
                    Regex("campaign_inspect player=${Regex.escape(client.username)} encounter=.* phase=active .* live=[1-9]"),
                    "active campaign ${client.username}",
                    Duration.ofMinutes(2),
                    retryInterval = Duration.ofSeconds(10),
                )
            }

            requireAllPlayersOnline("three campaign encounters active")
            check(serverAlive()) { "server exited during three-player campaign check; see ${server.log}" }
            clients.forEach { client ->
                check(itAlive(client)) { "${client.username} client exited during campaign check; see ${client.log}" }
                server.commandResult(
                    "pillager_campaigns inspect ${client.username}",
                    Regex("campaign_inspect player=${Regex.escape(client.username)} encounter=.* phase=active .* live=[1-9]"),
                    "simultaneously active campaign ${client.username}",
                    Duration.ofSeconds(30),
                )
            }
            campaignReady = true
            evidence.run.event("campaigns_active", mapOf("players" to clients.map { it.username }))
            if (evidence.run.target == "campaign-start") {
                evidence.run.event("campaign_start_passed", mapOf("players" to clients.map { it.username }))
            }
        }
    }

    @Test @Order(5)
    fun debugServerRestartAndClientReconnectPreserveWorld() {
        if (evidence.run.tier != "debug") {
            evidence.run.event("scenario_omitted", mapOf("name" to "restart and reconnect", "tier" to evidence.run.tier))
            return
        }
        if (evidence.run.target == "restart-compat") {
            evidence.run.checkpoint("saved Flesh spread restart and Untamed whale client spawn") {
                server.waitReady()
                server.stopGracefully()
                val data = server.server.resolve("world/data/TheFleshThatHates_FleshBlocks.dat")
                Files.createDirectories(data.parent)
                val fixture = requireNotNull(javaClass.getResourceAsStream("/flesh-spread-printed-keys.dat")) {
                    "missing printed-key Flesh spread fixture"
                }
                fixture.use { Files.copy(it, data, StandardCopyOption.REPLACE_EXISTING) }
                evidence.run.event("flesh_spread_fixture_installed", mapOf("path" to data.toString()))
                server.restart()
                val lead = clients.first()
                startClient(lead)
                requirePlayersOnline("restart compatibility join", listOf(lead))
                server.send("gamemode spectator ${lead.username}")
                server.send("execute in minecraft:overworld run fill -12 98 -12 12 106 12 minecraft:water")
                val arrivalOffset = clientLogOffset(lead)
                server.send("execute in minecraft:overworld run tp ${lead.username} 0 108 0")
                waitForClientPosition(lead, "minecraft:overworld", arrivalOffset, 0, 0)
                server.send("execute in minecraft:overworld run summon untamedwilds:baleen_whale 0 101 0")
                server.commandResult(
                    "execute in minecraft:overworld if entity @e[type=untamedwilds:baleen_whale,x=0,y=101,z=0,distance=..20] run say BC_UNTAMED_WHALE_SPAWNED",
                    Regex("BC_UNTAMED_WHALE_SPAWNED"), "whale visible near reconnecting client", Duration.ofSeconds(30),
                )
                Thread.sleep(5_000)
                check(itAlive(lead)) { "client exited after Untamed whale spawn; see ${lead.log}" }
                server.assertHashes()
                lead.assertHashes()
                evidence.run.event("restart_compat_passed", mapOf("whale" to "untamedwilds:baleen_whale"))
            }
            return
        }
        assumeTrue(campaignReady, "campaign prerequisite failed")
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
            val reconnect = ClientFixture(evidence.run, server, clients.first().username, 4, "$CLIENT_JVM_ARGS -Dbc.pack_test.debug=true")
            clients = clients + reconnect
            startClient(reconnect)
            requirePlayersOnline("reconnect after restart", listOf(reconnect))
            evidence.run.event("restart_reconnect_passed", mapOf("player_uuid" to reconnect.uuid, "world_time_before" to before, "world_time_after" to gameTime()))
        }
    }

    @Test @Order(2)
    fun debugNativeFontRoundTrips() {
        if (evidence.run.tier != "debug") {
            evidence.run.event("scenario_omitted", mapOf("name" to "native Font round trip", "tier" to evidence.run.tier))
            return
        }
        prepareTargetJoin()
        assumeTrue(joined, "client join prerequisite failed")
        evidence.run.checkpoint("four native Font round trips and geometry") {
            val player = clients.first()
            server.send("gamemode spectator ${player.username}")
            server.send("clear ${player.username}")
            val fonts = linkedMapOf(
                "ratlantis" to "rats:ratlantis",
                "bumblezone" to "the_bumblezone:the_bumblezone",
                "aether" to "aether:the_aether",
                "nether" to "minecraft:the_nether",
            )
            val selectedFonts = evidence.run.target?.takeIf { it.startsWith("font:") }
                ?.removePrefix("font:")?.let { template ->
                    listOf(template to (fonts[template] ?: error("unknown Font target: $template")))
                } ?: fonts.toList()
            selectedFonts.forEachIndexed { index, (template, dimension) ->
                val originOffset = clientLogOffset(player)
                server.send("execute in minecraft:overworld run tp ${player.username} 0 320 0")
                server.commandResult(
                    "execute as ${player.username} at @s if dimension minecraft:overworld run say BC_FONT_ORIGIN_$template",
                    Regex("BC_FONT_ORIGIN_$template"), "Font origin $template", Duration.ofSeconds(90),
                )
                // Later iterations start in the same chunk just confirmed by the prior return.
                if (index == 0) waitForClientPosition(player, "minecraft:overworld", originOffset)
                val enterOffset = clientLogOffset(player)
                server.commandResult(
                    "execute as ${player.username} at @s run font harness_enter $template",
                    Regex("BC_FONT_HARNESS_ENTER player=${Regex.escape(player.username)} template=$template"),
                    "native Font activation $template", Duration.ofMinutes(5),
                )
                server.commandResult(
                    "execute as ${player.username} at @s if dimension $dimension run say BC_FONT_NATIVE_ENTERED_$template",
                    Regex("BC_FONT_NATIVE_ENTERED_$template"), "native Font destination $template",
                    Duration.ofMinutes(2), retryInterval = Duration.ofSeconds(10),
                )
                waitForClientPosition(player, dimension, enterOffset)
                requireGeometry(dimension, listOf(probeGeometry(player, dimension, "font_$template")))
                // Let the client finish decoding the destination's chunk packets before
                // switching dimensions again; otherwise a late chunk packet can be applied
                // against the next ClientLevel while traversing the native Font.
                Thread.sleep(15_000)
                val returnOffset = clientLogOffset(player)
                server.commandResult(
                    "execute as ${player.username} at @s run font harness_return",
                    Regex("BC_FONT_HARNESS_RETURN player=${Regex.escape(player.username)} template=$template seal=.* destination=minecraft:overworld"),
                    "native return Font interaction $template", Duration.ofMinutes(2),
                )
                server.commandResult(
                    "execute as ${player.username} at @s if dimension minecraft:overworld run say BC_FONT_NATIVE_RETURNED_$template",
                    Regex("BC_FONT_NATIVE_RETURNED_$template"), "native Font return $template", Duration.ofMinutes(2),
                )
                waitForClientPosition(player, "minecraft:overworld", returnOffset)
                evidence.run.event("native_font_roundtrip_passed", mapOf("player" to player.username,
                    "template" to template, "dimension" to dimension))
            }
            fontRoundTrips = true
            if (evidence.run.target != null) {
                server.assertHashes()
                player.assertHashes()
            }
        }
    }

    @Test @Order(6)
    fun multiplayerEvidenceIsCleanAndCandidatesAreUnchanged() {
        assumeTrue(
            if (evidence.run.target != null) joined
            else if (evidence.run.tier == "debug") campaignReady
            else joined,
            "multiplayer prerequisite failed",
        )
        if (evidence.run.tier == "debug" && evidence.run.target == null) {
            assumeTrue(fontRoundTrips, "Font round-trip prerequisite failed")
        }
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

    private fun clientLogOffset(client: ClientFixture): Int =
        if (Files.isRegularFile(client.log)) Files.size(client.log).toInt() else 0

    private fun waitForClientPosition(
        client: ClientFixture, dimension: String, offset: Int, chunkX: Int? = null, chunkZ: Int? = null,
    ) {
        val marker = Regex("BC_DEBUG_CLIENT_POSITION dimension=${Regex.escape(dimension)} chunk_x=(-?\\d+) chunk_z=(-?\\d+)")
        val deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos()
        while (System.nanoTime() < deadline) {
            val text = if (Files.isRegularFile(client.log)) Files.readString(client.log).drop(offset) else ""
            if (marker.findAll(text).any { (chunkX == null || it.groupValues[1].toInt() == chunkX) &&
                    (chunkZ == null || it.groupValues[2].toInt() == chunkZ) }) return
            check(client.processAlive()) { "${client.username} exited before client-side arrival in $dimension; see ${client.log}" }
            Thread.sleep(500)
        }
        error("${client.username} never reported client-side arrival in $dimension; see ${client.log}")
    }

    private fun probeGeometry(client: ClientFixture, dimension: String, id: String): GeometrySample {
        server.commandResult(
            "execute as ${client.username} at @s run runtimedata geometry $id",
            Regex("BC_GEOMETRY_PROBE id=${Regex.escape(id)} path="),
            "geometry probe $id", Duration.ofMinutes(2), retryInterval = Duration.ofSeconds(10),
        )
        val source = server.server.resolve("generated/runtime-dumps/geometry/$id.json")
        require(Files.isRegularFile(source)) { "geometry probe produced no file: $source" }
        val output = evidence.run.directory.resolve("geometry/$id.json")
        Files.createDirectories(output.parent)
        Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING)
        val sample = GeometrySmokePlan.read(output)
        require(sample.dimension == dimension) { "geometry probe $id reported ${sample.dimension} instead of $dimension" }
        evidence.run.event("geometry_probe", mapOf("dimension" to dimension, "id" to id,
            "chunks" to sample.chunks, "evidence" to output.toString()))
        return sample
    }

    private fun requireGeometry(dimension: String, samples: List<GeometrySample>) {
        val result = GeometrySmokePlan.assess(dimension, samples)
        evidence.run.event("geometry_assessment", result)
        require(result["passed"] == true) { "insufficient native block evidence in $dimension: $result" }
    }

    private fun startClient(client: ClientFixture) {
        client.prepare()
        client.launchDedicated()
        client.waitDedicatedJoin()
        // Start clients serially so one full-pack resource reload cannot starve another
        // client's network heartbeat. The join starts only the lead; the other
        // two are started once here for the separate Survival campaign check.
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
        // Four loaded corridors give the in-game route retry distinct western, northern,
        // eastern, and southern approaches when local terrain or a mob blocks one path.
        // Each strip stays narrow enough to avoid generating a large distant square.
        server.commandResult(
            "execute in minecraft:overworld run forceload add ${x - 80} ${z - 16} ${x + 80} ${z + 16}",
            Regex("Marked \\[-?[0-9]+, -?[0-9]+\\] chunks in minecraft:overworld from \\[-?[0-9]+, -?[0-9]+\\] to \\[-?[0-9]+, -?[0-9]+\\] to be force loaded"),
            "forceload east-west campaign platform $index",
            Duration.ofMinutes(2),
        )
        server.commandResult(
            "execute in minecraft:overworld run forceload add ${x - 16} ${z - 80} ${x + 16} ${z + 80}",
            Regex("Marked \\[-?[0-9]+, -?[0-9]+\\] chunks in minecraft:overworld from \\[-?[0-9]+, -?[0-9]+\\] to \\[-?[0-9]+, -?[0-9]+\\] to be force loaded"),
            "forceload north-south campaign platform $index",
            Duration.ofMinutes(2),
        )
        server.send("execute in minecraft:overworld run fill ${x - 72} 315 ${z - 5} ${x + 72} 315 ${z + 5} minecraft:grass_block")
        server.send("execute in minecraft:overworld run fill ${x - 72} 316 ${z - 5} ${x + 72} 319 ${z + 5} minecraft:air")
        server.send("execute in minecraft:overworld run fill ${x - 5} 315 ${z - 72} ${x + 5} 315 ${z + 72} minecraft:grass_block")
        server.send("execute in minecraft:overworld run fill ${x - 5} 316 ${z - 72} ${x + 5} 319 ${z + 72} minecraft:air")
        server.commandResult(
            "execute in minecraft:overworld run say BC_PILLAGER_PLATFORM_$index",
            Regex("BC_PILLAGER_PLATFORM_$index"),
            "campaign platform $index",
            Duration.ofMinutes(2),
        )
    }

    private fun verifyCampaignPlatform(index: Int, x: Int, z: Int) {
        // Spawn preparation can replace blocks in the central chunks after the early prewarm.
        // Rebuild the already loaded corridors immediately before this player's path proof.
        // Avoid another forceload here: it can stall the server long enough to lose clients.
        server.send("execute in minecraft:overworld run fill ${x - 72} 315 ${z - 5} ${x + 72} 315 ${z + 5} minecraft:grass_block")
        server.send("execute in minecraft:overworld run fill ${x - 72} 316 ${z - 5} ${x + 72} 319 ${z + 5} minecraft:air")
        server.send("execute in minecraft:overworld run fill ${x - 5} 315 ${z - 72} ${x + 5} 315 ${z + 72} minecraft:grass_block")
        server.send("execute in minecraft:overworld run fill ${x - 5} 316 ${z - 72} ${x + 5} 319 ${z + 72} minecraft:air")
        server.commandResult(
            "execute in minecraft:overworld if block $x 315 $z minecraft:grass_block run say BC_PILLAGER_PLATFORM_READY_$index",
            Regex("BC_PILLAGER_PLATFORM_READY_$index"),
            "verify repaired campaign platform $index",
            Duration.ofSeconds(30),
        )
        evidence.run.event("campaign_platform_repaired", mapOf("index" to index, "x" to x, "z" to z))
    }

    private fun waitForStableTps(target: DimensionTarget, location: Int) {
        val deadline = System.nanoTime() + Duration.ofSeconds(180).toNanos()
        var consecutive = 0
        var sample = 0
        val required = 3
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
            consecutive = if (tps >= 18.0) consecutive + 1 else 0
            evidence.run.event("dimension_tps_sample", mapOf(
                "dimension" to target.id, "location" to location, "sample" to ++sample,
                "mean_tps" to tps, "required_tps" to 18.0, "consecutive_passing" to consecutive,
                "mode" to evidence.run.tier,
            ))
        }
        require(consecutive >= required) {
            "${target.id} location $location did not produce $required consecutive >=18 TPS samples within 180 seconds"
        }
    }

}
