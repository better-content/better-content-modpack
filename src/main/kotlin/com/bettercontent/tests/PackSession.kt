package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun main(args: Array<String>) {
    require(args.size == 6) { "usage: PackSession ROOT smoke|overworld-tele-soak|tele-soak MINUTES CLIENT_ZIP SERVER_ZIP DIAGNOSTIC_SOURCE_OR_EMPTY" }
    require(!System.getenv("BC_PACK_TEST_LOCK_TOKEN").isNullOrBlank()) { "invoke ./test.main.kts so the session mutex is held" }
    val root = Path.of(args[0]).toAbsolutePath().normalize()
    val tier = SessionTier.entries.singleOrNull { it.selector == args[1] } ?: error("select smoke, overworld-tele-soak or tele-soak")
    val request = SessionRequest(tier, args[2].toLong())
    require(args[3].isBlank() == args[4].isBlank()) { "supply both artifact paths" }
    val pair = if (args[3].isBlank()) CandidateLocator.locate(root) else CandidateLocator.locate(Path.of(args[3]), Path.of(args[4]))
    require(tier != SessionTier.OVERWORLD_TELE_SOAK || args[5].isBlank()) {
        "one-hour Overworld Tele Soak must start from the beginning; diagnostic replay is unsupported"
    }
    val replay = args[5].takeIf(String::isNotBlank)?.let { loadDiagnosticReplay(root, tier, Path.of(it)) }
    val runId = System.getenv("BC_TEST_RUN_ID")?.takeIf(String::isNotBlank)
        ?: DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now()) + "-" + ProcessHandle.current().pid()
    val evidence = SessionEvidence(root, runId, tier.selector, pair, diagnostic = replay != null)
    replay?.let { evidence.event("diagnostic_replay_source", mapOf("source_run_id" to it.sourceRunId,
        "source_evidence" to it.sourceEvidence.toString(), "source_candidate_hashes" to it.sourceCandidates,
        "failed_hop" to it.failedHop?.let { hop -> mapOf("dimension" to hop.dimension, "circuit" to hop.circuit) },
        "acceptance_eligible" to false)) }
    val session = PackSession(evidence, request, replay)
    val driver: SessionDriver = session
    require(tier == SessionTier.SMOKE || driver is TeleSoakDriver) {
        "Tele Soak requires actual instrumented server/client dimension and TPS probes; no world was created"
    }
    var failure: Throwable? = null
    try {
        session.prepare()
        runLifecycle(request, session, replay = replay)
    } catch (error: Throwable) {
        failure = error
        session.diagnostics()
    } finally {
        try { session.close() } catch (cleanup: Throwable) {
            if (failure == null) failure = cleanup else failure.addSuppressed(cleanup)
        }
        try { session.recordLogMessages() } catch (logging: Throwable) {
            if (failure == null) failure = logging else failure.addSuppressed(logging)
        }
        // Mutation is a failure even when another stage has already failed.
        try { pair.assertUnchanged() } catch (mutation: Throwable) {
            if (failure == null) failure = mutation else failure.addSuppressed(mutation)
        }
        evidence.finish(failure)
    }
    failure?.let { throw it }
}

internal fun stopAndSaveServer(server: ManagedProcess, log: Path, timeout: Duration = Duration.ofMinutes(3)) {
    val offset = Files.size(log)
    // Normal Minecraft stop performs its own complete save. A redundant console flush
    // keeps the tick watchdog active while serializing every chunk a second time.
    server.send("stop")
    check(server.waitForExit(timeout) == 0) { "server did not exit cleanly" }
    check(readLogFrom(log, offset).text.contains("ThreadedAnvilChunkStorage: All dimensions are saved")) {
        "server exited without fresh complete-save acknowledgement"
    }
}

internal fun sessionClientOptions(text: String): String {
    // Minecraft 1.20.1 accepts client simulation distances only in [5, 33].
    // Four is valid for the dedicated server, but is rejected by the client codec.
    val settings = mapOf("renderDistance" to "4", "simulationDistance" to "5", "maxFps" to "30",
        "graphicsMode" to "0", "ao" to "false", "entityShadows" to "false")
    return text.lineSequence().joinToString("\n") { line ->
        settings[line.substringBefore(':')]?.let { "${line.substringBefore(':')}:$it" } ?: line
    }
}

private class PackSession(private val evidence: SessionEvidence, private val request: SessionRequest,
    private val replay: DiagnosticReplay? = null) : TeleSoakDriver, AutoCloseable {
    private val pair = evidence.pair
    private val root = evidence.root
    private val fixture = evidence.fixture
    private val java = (System.getenv("BC_JAVA")?.let(Path::of)
        ?: System.getenv("JAVA_HOME")?.let { Path.of(it, "bin/java") }
        ?: Path.of(System.getProperty("java.home"), "bin/java")).toAbsolutePath()
    private val username = "SmokeClient"
    private val serverLog = evidence.directory.resolve("server.log")
    private val clientLog = evidence.directory.resolve("client.log")
    private val clientRoot = fixture.resolve("client")
    private lateinit var serverRoot: Path
    private var server: ManagedProcess? = null
    private var client: ManagedProcess? = null
    private var display: ManagedProcess? = null
    private var joined = false
    private var disconnected = false
    private var stopped = false
    private var connectionLogOffset = 0L
    private var connectionRemainder = ""
    private var connectedSince = -1L
    private var teleDimension: String? = null
    private var lastDimensionReceipt = -1L
    private var lastFontRefill = -1L
    private var dimensionProbeIndex = 0
    private var registeredTeleRoster: Set<String> = emptySet()
    private val port = freePort()
    private val displayNumber = freePort() - 6000
    private val displayAddress = "127.0.0.1:$displayNumber"
    private val authority = fixture.resolve("Xauthority")
    private val startupTimeout = Duration.ofMinutes(20)
    private val responseTimeout = Duration.ofSeconds(60)

    fun prepare() {
        check(System.getProperty("java.specification.version") == "17" && Files.isExecutable(java)) { "Java 17 is required" }
        val extracted = fixture.resolve("server")
        ZipContracts.extract(pair.server, extracted)
        serverRoot = extracted.resolve(ZipContracts.soleTopLevelDirectory(pair.server))
        require(Files.isRegularFile(serverRoot.resolve("run.sh"))) { "server ZIP lacks its launcher" }
        require(!serverRoot.resolve("world").exists()) { "server ZIP contains a world; sessions require a fresh disposable world" }
        listOf("run.sh", "run-forge.sh", "better-world-management-server.sh").forEach {
            check(serverRoot.resolve(it).toFile().setExecutable(true)) { "cannot enable server launcher $it" }
        }
        serverRoot.resolve("eula.txt").writeText("eula=true\n")
        val props = serverRoot.resolve("server.properties")
        val values = Properties().apply { Files.newInputStream(props).use(::load) }
        values.setProperty("online-mode", "false")
        values.setProperty("server-ip", "127.0.0.1")
        values.setProperty("server-port", port.toString())
        values.setProperty("level-name", "world")
        // Fresh remote chunks in modded Overworld can generate faster than a
        // 9x9 server view can finish; a 5x5 view still loads fresh terrain on
        // every move without pre-generating it or relaxing the TPS threshold.
        val distance = if (request.tier != SessionTier.SMOKE) "2" else "4"
        values.setProperty("view-distance", distance)
        values.setProperty("simulation-distance", distance)
        Files.newOutputStream(props).use { values.store(it, "Disposable session profile; packaged inputs remain unchanged") }
        val serverHeap = if (request.tier != SessionTier.SMOKE) "-Xms4G\n-Xmx10G\n"
            else "-Xms1G\n-Xmx6G\n"
        serverRoot.resolve("user_jvm_args.txt").writeText(serverHeap + "-XX:+UseG1GC\n-Dfile.encoding=UTF-8\n" +
            if (request.tier != SessionTier.SMOKE)
                "-Dbc.pack_test.debug=true\n-Xlog:gc*:file=tele-soak-gc.log:time,uptime,level,tags:filecount=3,filesize=10m\n"
            else "")
        val backups = serverRoot.resolve("config/ftbbackups2.json")
        if (Files.isRegularFile(backups)) backups.writeText(backups.readText().replace("\"enabled\": true", "\"enabled\": false"))
        // The materializer uses only the ZIP, its embedded artifact lock and a content-addressed cache.
        Commands.run(listOf("python3", root.resolve("src/main/resources/materialize_client.py").toString(), pair.client.toString(), clientRoot.toString()),
            fixture, evidence.directory.resolve("client-materialization.log"))
        configureClientProfile()
        Commands.run(listOf("xauth", "-f", authority.toString(), "add", displayAddress, "MIT-MAGIC-COOKIE-1", UUID.randomUUID().toString().replace("-", "")),
            fixture, evidence.directory.resolve("xauth.log"))
        evidence.event("prepared", mapOf("fresh_world" to true, "server_port" to port,
            "profile" to "loopback offline authentication, server distances 4 (both Tele Soaks 2), client render 4/simulation 5, software-rendered client; Tele Soak server heap 4-10 GiB and all client heaps 2-8 GiB; no feature scenarios",
            "requested_connected_seconds" to request.connectedDuration.seconds,
            "diagnostic_remaining_seconds" to replay?.remainingSeconds(request)))
    }

    private fun configureClientProfile() {
        val options = clientRoot.resolve("options.txt")
        if (Files.isRegularFile(options)) {
            options.writeText(sessionClientOptions(options.readText()))
        }
        val dh = clientRoot.resolve("config/DistantHorizons.toml")
        if (Files.isRegularFile(dh)) dh.writeText(dh.readText()
            .replace(Regex("(?m)^numberOfThreads = \\d+$"), "numberOfThreads = 1")
            .replace("enableDistantGeneration = true", "enableDistantGeneration = false")
            .replace("rendererMode = \"DEFAULT\"", "rendererMode = \"DISABLED\""))
    }

    override fun startServer() {
        check(server == null) { "server may only start once" }
        server = ManagedProcess("server", listOf("./run.sh", "nogui"), serverRoot, serverLog, mapOf("BC_JAVA" to java.toString()))
        evidence.event("server_started", mapOf("pid" to server!!.pid))
    }

    override fun awaitServerReady() {
        // A new Lineage world starts a closed candidate server first. Its "Done" line is
        // not the playable server: wait for the supervisor to promote/reopen the save.
        val ready = Regex("Done \\([0-9.]+s\\)!.*For help")
        server!!.waitUntil(startupTimeout, "published world server readiness") {
            val output = serverLog.readText()
            val finalLaunch = output.lastIndexOf("launching current world under lifecycle supervision")
            finalLaunch >= 0 && ready.containsMatchIn(output.substring(finalLaunch))
        }
        evidence.event("server_ready")
    }

    override fun connectClient() {
        check(client == null) { "client may only connect once; reconnect is not a recovery strategy" }
        // TCP + an explicit cookie avoids X11 sockets/lock files in /tmp.
        display = ManagedProcess("display", listOf("Xvfb", ":$displayNumber", "-screen", "0", "1280x720x24",
            "-nolisten", "unix", "-listen", "tcp", "-nolock", "-auth", authority.toString()), fixture, evidence.directory.resolve("display.log"))
        Thread.sleep(1000)
        check(display!!.alive) { "Xvfb failed; see display.log" }
        val main = System.getenv("BC_TEST_CLIENT_MAIN") ?: Path.of(System.getProperty("user.home"), ".cache/bc/client-launcher").toString()
        val manifest = jacksonObjectMapper().readTree(fixture.resolve("client-manifest.json").toFile())
        val mc = manifest.path("minecraft").path("version").asText()
        val loader = manifest.path("minecraft").path("modLoaders").single { it.path("primary").asBoolean() }.path("id").asText()
        require(mc == "1.20.1" && loader.startsWith("forge-")) { "unsupported client manifest loader: $mc/$loader" }
        val offlineId = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray()).toString().replace("-", "")
        // The full client's initial resource reload nearly exhausted the former 4 GiB Smoke heap
        // and stalled the first login until Minecraft's 30-second network read timeout fired.
        // Use the existing Tele Soak heap for every tier; no continuity or TPS gate changes.
        val jvm = "-Xms2G -Xmx8G -XX:+UseG1GC -Dfile.encoding=UTF-8 -Djava.net.preferIPv6Addresses=false"
        client = ManagedProcess("client", listOf("pipx", "run", "--spec", "portablemc==4.4.1", "portablemc",
            "--main-dir", main, "--work-dir", clientRoot.toString(), "--timeout", "120", "start", "--jvm", java.toString(),
            "--jvm-args=$jvm", "--resolution", "1280x720", "-u", username, "-i", offlineId,
            "-s", "127.0.0.1", "-p", port.toString(), "forge:$mc-${loader.removePrefix("forge-")}"), clientRoot, clientLog,
            mapOf("DISPLAY" to displayAddress, "XAUTHORITY" to authority.toString(), "LIBGL_ALWAYS_SOFTWARE" to "1",
                "MESA_GL_VERSION_OVERRIDE" to "4.6", "MESA_GLSL_VERSION_OVERRIDE" to "460", "ALSOFT_DRIVERS" to "null"))
        evidence.event("client_started", mapOf("pid" to client!!.pid, "username" to username))
    }

    override fun awaitClientJoined() {
        server!!.waitUntil(startupTimeout, "real client join") {
            check(client!!.alive) { "client exited before joining; see $clientLog" }
            Regex("${Regex.escape(username)} joined the game[^\\r\\n]*\\r?\\n").containsMatchIn(serverLog.readText())
        }
        val initial = readLogFrom(serverLog, 0)
        check(Regex("${Regex.escape(username)} joined the game").findAll(initial.text).count() == 1) { "unexpected reconnect before verification" }
        check(!Regex("${Regex.escape(username)} (?:lost connection:|left the game)").containsMatchIn(initial.text)) { "client disconnected before verification" }
        connectionLogOffset = initial.nextOffset
        connectionRemainder = initial.text.substringAfterLast('\n', "")
        joined = true
        evidence.event("client_joined")
    }

    override fun ensureConnected() {
        check(joined && !disconnected && server!!.alive && client!!.alive && display!!.alive) { "session process exited or client is not connected" }
        val slice = readLogFrom(serverLog, connectionLogOffset)
        connectionLogOffset = slice.nextOffset
        val text = connectionRemainder + slice.text
        connectionRemainder = text.substringAfterLast('\n', text)
        val complete = if ('\n' in text) text.substringBeforeLast('\n') else ""
        check(!Regex("${Regex.escape(username)} joined the game").containsMatchIn(complete)) { "unexpected reconnect" }
        check(!Regex("${Regex.escape(username)} (?:lost connection:|left the game)").containsMatchIn(complete)) { "client disconnected during the required connected interval" }
    }

    override fun heartbeat() = heartbeatFor(teleDimension)

    override fun heartbeatDuringArrival() = heartbeatFor(null)

    private fun heartbeatFor(dimension: String?) {
        ensureConnected()
        val marker = "BC_SESSION_${UUID.randomUUID().toString().replace("-", "") }"
        val offset = if (Files.isRegularFile(clientLog)) Files.size(clientLog) else 0L
        val command = if (dimension == null) "execute as $username run tellraw @s {\"text\":\"$marker\"}"
            else "execute as $username at @s if dimension $dimension run tellraw @s {\"text\":\"$marker\"}"
        server!!.send(command)
        client!!.waitUntil(if (connectedSince < 0) startupTimeout else responseTimeout, "fresh client heartbeat $marker") {
            ensureConnected()
            readLogFrom(clientLog, offset).text.contains(marker)
        }
        if (connectedSince < 0) {
            connectedSince = System.nanoTime()
            evidence.event("connected_interval_started", mapOf("required_seconds" to request.connectedDuration.seconds))
        }
        if (dimension != null) lastDimensionReceipt = System.nanoTime()
        evidence.event("heartbeat", mapOf("marker" to marker, "server_alive" to true, "client_received" to true,
            "verified_dimension" to dimension,
            "server_process_tree_rss_bytes" to processTreeMemory(server!!), "client_process_tree_rss_bytes" to processTreeMemory(client!!)))
    }

    override fun registeredDimensions(progress: () -> Unit): List<String> {
        progress()
        ensureConnected()
        if (dimensionProbeIndex > 0) {
            // A second full runtimedata dump scans the entire accumulated recipe
            // graph and can stall this long-lived server long enough to drop the
            // client. Probe each initially discovered live level on the server
            // instead; a registered level cannot be added during this session.
            val ids = registeredTeleRoster.sorted()
            ids.forEach { dimension ->
                val marker = "BC_DIM_${UUID.randomUUID().toString().replace("-", "") }"
                val offset = Files.size(serverLog)
                server!!.send("execute in $dimension run say $marker")
                server!!.waitUntil(responseTimeout, "live server level $dimension") {
                    progress()
                    ensureConnected()
                    readLogFrom(serverLog, offset).text.contains(marker)
                }
            }
            dimensionProbeIndex++
            evidence.event("live_dimension_probe", mapOf("dimensions" to ids,
                "method" to "fresh_execute_in_per_level", "levels_verified" to ids.size))
            return ids
        }
        val output = serverRoot.resolve("generated/runtime-dumps")
        val completion = output.resolve("snapshot.json")
        val previous = if (Files.isRegularFile(completion))
            jacksonObjectMapper().readTree(completion.toFile()).path("snapshot_id").asText() else ""
        // The fresh source-built diagnostics provider enumerates MinecraftServer.levelKeys(),
        // including progression-gated dimensions. No hand-maintained destination whitelist.
        server!!.send("runtimedata dump")
        server!!.waitUntil(Duration.ofMinutes(3), "fresh live dimension registry snapshot") {
            progress()
            ensureConnected()
            Files.isRegularFile(completion) && runCatching {
                jacksonObjectMapper().readTree(completion.toFile()).path("snapshot_id").asText() != previous &&
                    TeleSoakProbes.dimensions(output).isNotEmpty()
            }.getOrDefault(false)
        }
        val index = dimensionProbeIndex++
        val copy = evidence.directory.resolve("dimension-registry-$index")
        copy.createDirectories()
        Files.copy(completion, copy.resolve("snapshot.json"))
        Files.copy(output.resolve("dimensions.json"), copy.resolve("dimensions.json"))
        val ids = TeleSoakProbes.dimensions(copy)
        if (dimensionProbeIndex == 1) registeredTeleRoster = ids.toSet()
        evidence.event("live_dimension_probe", mapOf("dimensions" to ids, "snapshot" to copy.toString(),
            "snapshot_sha256" to Hashes.sha256(copy.resolve("snapshot.json")),
            "dimensions_sha256" to Hashes.sha256(copy.resolve("dimensions.json"))))
        return ids
    }

    private val preparedLandings = mutableSetOf<String>()
    private data class Landing(val x: Double, val y: Double, val z: Double) {
        val chunk: Pair<Int, Int> get() = kotlin.math.floor(x / 16).toInt() to kotlin.math.floor(z / 16).toInt()
    }
    private val lastLanding = mutableMapOf<String, Landing>()
    private val visitedChunks = mutableMapOf<String, MutableSet<Pair<Int, Int>>>()
    private val remoteCursor = RemoteLandingCursor()
    private var requestedLanding: Landing? = null

    override fun prepareLanding(dimension: String, progress: () -> Unit) {
        ensureConnected()
        require(dimension in registeredTeleRoster) { "destination not in live dimension roster: $dimension" }
        if (preparedLandings.isEmpty()) {
            server!!.send("gamemode spectator $username")
            val marker = "BC_SPECTATOR_${UUID.randomUUID().toString().replace("-", "") }"
            val offset = Files.size(serverLog)
            server!!.send("execute as $username if entity @s[gamemode=spectator] run say $marker")
            server!!.waitUntil(responseTimeout, "safe spectator mode for remote ungenerated chunk hops") {
                ensureConnected()
                readLogFrom(serverLog, offset).text.contains(marker)
            }
            evidence.event("remote_hop_profile", mapOf("gamemode" to "spectator", "terrain_pre_generated" to false,
                "target_spacing_blocks" to 10000))
        }
        // Spectators safely land in genuinely fresh terrain; no force-load/fill
        // is permitted here because it would pre-generate the stress target.
        progress()
        check(preparedLandings.add(dimension)) { "duplicate dimension preparation for $dimension" }
        evidence.event("remote_destination_eligible", mapOf("dimension" to dimension,
            "terrain_pre_generated" to false, "safe_mode" to "spectator"))
    }

    override fun teleportTo(dimension: String) {
        ensureConnected()
        require(dimension in preparedLandings) { "destination was not verified in the live dimension roster: $dimension" }
        // Bumblezone is Font-only. Unbind its test Font before leaving, without
        // returning to the already visited origin chunk. Never weaken the entry gate.
        if (teleDimension == "the_bumblezone:the_bumblezone" && dimension != teleDimension) {
            val offset = Files.size(serverLog)
            server!!.send("execute as $username run font harness_abandon")
            server!!.waitUntil(responseTimeout, "Font abandonment before fresh hop to $dimension") {
                ensureConnected()
                readLogFrom(serverLog, offset).text.contains("BC_FONT_HARNESS_ABANDON player=$username ")
            }
            evidence.event("font_abandoned", mapOf("before_dimension" to dimension, "return_teleport" to false))
        }
        val previousDimension = teleDimension
        val previous = lastLanding[dimension]
        // Per-dimension counters can reuse a Font destination reached earlier
        // from another dimension. Advance the test itinerary globally instead.
        requestedLanding = if (dimension == "the_bumblezone:the_bumblezone" && previousDimension != dimension) null
            else Landing(remoteCursor.nextX(), 91.0, previous?.z ?: 0.5)
        requestedLanding?.let { landing ->
            check(landing.chunk !in visitedChunks[dimension].orEmpty()) { "reused remote chunk in $dimension: ${landing.chunk}" }
        }
        teleDimension = dimension
        lastDimensionReceipt = -1L
        // Charge belongs to the active Font, not to each 10,000-block relocation.
        // Resetting this clock on every jump starves the one-minute refill forever.
        if (requestedLanding == null) {
            lastFontRefill = System.nanoTime()
            server!!.send("execute as $username run font harness_enter bumblezone")
            evidence.event("font_entry_requested", mapOf("dimension" to dimension, "path" to "test-gated real Font interaction"))
        } else {
            val target = requestedLanding!!
            server!!.send("execute in $dimension run tp $username ${target.x} ${target.y} ${target.z}")
        }
    }

    override fun verifyArrival(dimension: String, progress: () -> Unit) {
        require(dimension == teleDimension) { "arrival dimension changed unexpectedly" }
        val actual = readPlayerLanding()
        evidence.event("arrival_position_observed", mapOf("dimension" to dimension,
            "x" to actual.x, "y" to actual.y, "z" to actual.z,
            "requested_x" to requestedLanding?.x, "requested_y" to requestedLanding?.y,
            "requested_z" to requestedLanding?.z))
        val marker = "BC_ARRIVAL_${UUID.randomUUID().toString().replace("-", "") }"
        val offset = Files.size(clientLog)
        val positionClause = requestedLanding?.let { target ->
            " positioned ${target.x} ${target.y} ${target.z} if entity @s[distance=..1]"
        } ?: ""
        server!!.send("execute as $username at @s if dimension $dimension$positionClause run tellraw @s {\"text\":\"$marker\"}")
        client!!.waitUntil(responseTimeout, "server dimension and current client arrival in $dimension") {
            ensureConnected()
            progress()
            readLogFrom(clientLog, offset).text.contains(marker)
        }
        requestedLanding?.let { target ->
            check(kotlin.math.abs(actual.x - target.x) <= 1 && kotlin.math.abs(actual.y - target.y) <= 1 &&
                kotlin.math.abs(actual.z - target.z) <= 1) {
                "server/player position differs from the fresh requested chunk in $dimension: $actual vs $target"
            }
        }
        val previous = lastLanding[dimension]
        val distance = previous?.let { kotlin.math.hypot(actual.x - it.x, actual.z - it.z) }
        check(distance == null || distance >= 9999.0) {
            "hop moved less than 10,000 blocks in $dimension: $distance"
        }
        check(visitedChunks.getOrPut(dimension, ::mutableSetOf).add(actual.chunk)) {
            "teleported into a previously visited chunk in $dimension: ${actual.chunk}"
        }
        remoteCursor.observe(actual.x)
        lastLanding[dimension] = actual
        lastDimensionReceipt = System.nanoTime()
        evidence.event("landing_verified", mapOf("dimension" to dimension, "x" to actual.x, "y" to actual.y,
            "z" to actual.z, "chunk_x" to actual.chunk.first, "chunk_z" to actual.chunk.second,
            "distance_from_previous" to distance, "novel_chunk" to true,
            "route" to if (requestedLanding == null) "native_font" else "remote_ungenerated_chunk"))
        evidence.event("client_dimension_receipt", mapOf("dimension" to dimension, "marker" to marker,
            "server_conditional" to true, "server_position_conditional" to (requestedLanding != null),
            "fresh_client_log_offset" to offset))
    }

    private fun readPlayerLanding(): Landing {
        val offset = Files.size(serverLog)
        server!!.send("data get entity $username Pos")
        val pattern = Regex("has the following entity data: \\[([-+0-9.eE]+)d, ([-+0-9.eE]+)d, ([-+0-9.eE]+)d]")
        server!!.waitUntil(responseTimeout, "fresh server-side player position") {
            ensureConnected()
            pattern.containsMatchIn(readLogFrom(serverLog, offset).text)
        }
        val match = requireNotNull(pattern.find(readLogFrom(serverLog, offset).text))
        return Landing(match.groupValues[1].toDouble(), match.groupValues[2].toDouble(), match.groupValues[3].toDouble())
    }

    override fun ensureInDimension(dimension: String) {
        ensureConnected()
        check(teleDimension == dimension && lastDimensionReceipt >= 0 &&
            System.nanoTime() - lastDimensionReceipt <= Duration.ofSeconds(30).toNanos()) {
            "missing fresh server-conditional client receipt for $dimension within 30 seconds"
        }
        if (dimension == "the_bumblezone:the_bumblezone" &&
            System.nanoTime() - lastFontRefill >= Duration.ofSeconds(60).toNanos()) {
            val offset = Files.size(serverLog)
            server!!.send("execute as $username run font harness_refill")
            server!!.waitUntil(responseTimeout, "active Font charge replenishment in $dimension") {
                ensureConnected()
                readLogFrom(serverLog, offset).text.contains("BC_FONT_HARNESS_REFILL player=$username ")
            }
            lastFontRefill = System.nanoTime()
            evidence.event("font_refilled", mapOf("dimension" to dimension, "fixture_only" to true))
        }
    }

    override fun currentTps(): Double {
        ensureConnected()
        val offset = Files.size(serverLog)
        server!!.send("forge tps")
        // Chunk-generation pauses can delay a console response without proving a TPS
        // recovery. The 120-second hop deadline and strict <=2s stability gaps still apply.
        server!!.waitUntil(Duration.ofSeconds(30), "fresh Forge 47.4.22 overall TPS sample") {
            ensureConnected()
            runCatching { TeleSoakProbes.overallTps(readLogFrom(serverLog, offset).text) }.isSuccess
        }
        val tps = TeleSoakProbes.overallTps(readLogFrom(serverLog, offset).text)
        evidence.event("forge_tps", mapOf("tps" to tps, "source" to "Forge 47.4.22 /forge tps Overall Mean TPS"))
        return tps
    }

    override fun teleSoakEvent(name: String, fields: Map<String, Any>) { evidence.event(name, fields) }

    override fun disconnectClient() {
        ensureConnected()
        val elapsed = System.nanoTime() - connectedSince
        check(connectedSince >= 0 && elapsed >= Duration.ofSeconds(replay?.remainingSeconds(request)
            ?: request.connectedDuration.seconds).toNanos()) { "connected interval was shorter than requested" }
        evidence.event("connected_interval_complete", mapOf("actual_seconds" to elapsed / 1_000_000_000.0))
        server!!.send("kick $username Session complete")
        server!!.waitForLog(Regex("${Regex.escape(username)} left the game"), responseTimeout, "client disconnect")
        disconnected = true
        client!!.close()
        display!!.close()
        evidence.event("client_disconnected")
    }

    override fun saveAndStopServer() {
        check(disconnected) { "disconnect the client before stopping the server" }
        stopAndSaveServer(server!!, serverLog)
        stopped = true
        evidence.event("server_saved_and_stopped")
    }

    override fun validate() {
        check(disconnected && stopped) { "incomplete lifecycle" }
        val data = serverRoot.resolve("world/level.dat")
        validateWorldSave(data)
        val crashes = Files.walk(fixture).use { files ->
            files.filter { Files.isRegularFile(it) && (it.fileName.toString().startsWith("hs_err_pid") ||
                (it.parent?.fileName?.toString() == "crash-reports" && it.fileName.toString().endsWith(".txt"))) }.toList()
        }
        check(crashes.isEmpty()) { "runtime crash artifacts: $crashes" }
        pair.assertUnchanged()
        evidence.event("validated", mapOf("world_save" to data.toString(), "candidate_hashes_unchanged" to true,
            "log_policy" to "warnings and nonfatal ERROR messages retained as diagnostics, not blanket failures"))
    }

    private fun processTreeMemory(process: ManagedProcess): Long? = process.trackedPids.mapNotNull(::processMemory).takeIf { it.isNotEmpty() }?.sum()

    private fun processMemory(pid: Long): Long? = runCatching {
        Regex("(?m)^VmRSS:\\s+(\\d+) kB$").find(Path.of("/proc/$pid/status").readText())?.groupValues?.get(1)?.toLong()?.times(1024)
    }.getOrNull()

    fun diagnostics() {
        server?.captureDiagnostics(evidence.directory.resolve("server-failure"))
        client?.captureDiagnostics(evidence.directory.resolve("client-failure"))
    }

    fun recordLogMessages() {
        evidence.event("log_diagnostics", recordLogDiagnostics(evidence.directory,
            mapOf("server" to serverLog, "client" to clientLog)))
    }

    override fun close() {
        val resources = mutableListOf<AutoCloseable>()
        client?.let(resources::add)
        display?.let(resources::add)
        if (!stopped && server?.alive == true) {
            // Best effort graceful shutdown on failure, without hiding the original failure.
            runCatching { server!!.send("stop"); server!!.waitForExit(Duration.ofMinutes(2)) }
        }
        server?.let(resources::add)
        cleanupFixtures(resources) { evidence.event("process_cleanup", it) }
    }
}
