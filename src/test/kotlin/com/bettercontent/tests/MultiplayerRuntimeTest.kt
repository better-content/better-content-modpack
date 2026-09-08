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
        lateinit var client: ClientFixture
        var joined = false
        var settled = false
        var dimensions = false

        @JvmStatic
        @BeforeAll
        fun start() {
            server = DedicatedServerFixture(evidence.run)
            client = ClientFixture(evidence.run, server)
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            if (::client.isInitialized) client.close()
            if (::server.isInitialized) server.close()
            evidence.run.event("process_cleanup", mapOf("complete" to true))
        }
    }

    @Test @Order(1)
    fun exactClientJoinsFreshDedicatedServer() = evidence.run.checkpoint("dedicated client join") {
        server.waitReady()
        client.prepare()
        client.launchDedicated()
        client.waitDedicatedJoin()
        joined = true
    }

    @Test @Order(2)
    fun joinedClientSettlesContent() {
        assumeTrue(joined, "client join prerequisite failed")
        evidence.run.checkpoint("client settle") {
            client.waitSettled()
            settled = true
        }
    }

    @Test @Order(3)
    fun everyFontAndCreatingSpaceDimensionStabilizesAtFreshLocations() {
        assumeTrue(settled, "client settle prerequisite failed")
        evidence.run.checkpoint("dimension traversal and TPS stabilization") {
            val dump = server.runtimeDump()
            val inventory = dump.resolve("dimensions.json")
            Files.copy(inventory, evidence.run.directory.resolve("dimensions.json"), StandardCopyOption.REPLACE_EXISTING)
            val targets = DimensionSmokePlan.discover(inventory, server.server.resolve("config/dimension_drink/fonts"))
            evidence.run.event("dimension_targets", mapOf(
                "count" to targets.size,
                "targets" to targets.map { mapOf("id" to it.id, "sources" to it.sources.sorted()) },
                "locations_per_target" to DimensionSmokePlan.positions.size,
            ))
            server.send("gamemode spectator ${server.config.username}")
            targets.forEach { target ->
                DimensionSmokePlan.positions.forEachIndexed { index, (x, z) ->
                    val marker = "BC_DIMENSION_HEARTBEAT_${target.id.replace(':', '_').replace('/', '_')}_$index"
                    evidence.run.event("dimension_teleport_started", mapOf(
                        "dimension" to target.id, "sources" to target.sources.sorted(), "location" to index,
                        "x" to x, "y" to 200, "z" to z,
                        "heartbeat_deadline_seconds" to 90,
                    ))
                    server.send("execute in ${target.id} run tp ${server.config.username} $x 200 $z")
                    server.commandResult(
                        "execute as ${server.config.username} at @s run say $marker",
                        Regex(Regex.escape(marker)),
                        "dimension heartbeat ${target.id} location $index",
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

    @Test @Order(4)
    fun multiplayerEvidenceIsCleanAndCandidatesAreUnchanged() {
        assumeTrue(dimensions, "dimension traversal prerequisite failed")
        evidence.run.checkpoint("multiplayer log and hash audit") {
            client.close()
            server.stopGracefully()
            server.auditLogs()
            server.assertHashes()
            client.assertHashes()
        }
    }

    private fun waitForStableTps(target: DimensionTarget, location: Int) {
        val deadline = System.nanoTime() + Duration.ofSeconds(180).toNanos()
        var consecutive = 0
        var sample = 0
        while (System.nanoTime() < deadline && consecutive < 3) {
            Thread.sleep(10_000)
            val result = server.commandResult(
                "forge tps",
                Regex("Overall.*?Mean TPS:\\s*[0-9]+(?:\\.[0-9]+)?", RegexOption.IGNORE_CASE),
                "TPS sample ${target.id} location $location",
                Duration.ofSeconds(30),
            )
            val tps = DimensionSmokePlan.parseOverallTps(result.value)
            consecutive = if (tps >= 18.0) consecutive + 1 else 0
            evidence.run.event("dimension_tps_sample", mapOf(
                "dimension" to target.id, "location" to location, "sample" to ++sample,
                "mean_tps" to tps, "required_tps" to 18.0, "consecutive_passing" to consecutive,
            ))
        }
        require(consecutive >= 3) {
            "${target.id} location $location did not produce three consecutive >=18 TPS samples within 180 seconds"
        }
    }
}
