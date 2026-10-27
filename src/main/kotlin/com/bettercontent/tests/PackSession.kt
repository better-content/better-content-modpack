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
    require(args.size == 5) { "usage: PackSession ROOT smoke|soak MINUTES CLIENT_ZIP SERVER_ZIP" }
    require(!System.getenv("BC_PACK_TEST_LOCK_TOKEN").isNullOrBlank()) { "invoke ./test.main.kts so the session mutex is held" }
    val root = Path.of(args[0]).toAbsolutePath().normalize()
    val tier = SessionTier.entries.singleOrNull { it.selector == args[1] } ?: error("select smoke or soak")
    val request = SessionRequest(tier, args[2].toLong())
    require(args[3].isBlank() == args[4].isBlank()) { "supply both artifact paths" }
    val pair = if (args[3].isBlank()) CandidateLocator.locate(root) else CandidateLocator.locate(Path.of(args[3]), Path.of(args[4]))
    val runId = System.getenv("BC_TEST_RUN_ID")?.takeIf(String::isNotBlank)
        ?: DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now()) + "-" + ProcessHandle.current().pid()
    val evidence = SessionEvidence(root, runId, tier.selector, pair)
    val session = PackSession(evidence, request)
    var failure: Throwable? = null
    try {
        session.prepare()
        runLifecycle(request, session)
    } catch (error: Throwable) {
        failure = error
        session.diagnostics()
    } finally {
        try { session.close() } catch (cleanup: Throwable) {
            if (failure == null) failure = cleanup else failure.addSuppressed(cleanup)
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

private class PackSession(private val evidence: SessionEvidence, private val request: SessionRequest) : SessionDriver, AutoCloseable {
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
        values.setProperty("view-distance", "4")
        values.setProperty("simulation-distance", "4")
        Files.newOutputStream(props).use { values.store(it, "Disposable session profile; packaged inputs remain unchanged") }
        serverRoot.resolve("user_jvm_args.txt").writeText("-Xms1G\n-Xmx6G\n-XX:+UseG1GC\n-Dfile.encoding=UTF-8\n")
        val backups = serverRoot.resolve("config/ftbbackups2.json")
        if (Files.isRegularFile(backups)) backups.writeText(backups.readText().replace("\"enabled\": true", "\"enabled\": false"))
        // The materializer uses only the ZIP, its embedded artifact lock and a content-addressed cache.
        Commands.run(listOf("python3", root.resolve("src/main/resources/materialize_client.py").toString(), pair.client.toString(), clientRoot.toString()),
            fixture, evidence.directory.resolve("client-materialization.log"))
        configureClientProfile()
        Commands.run(listOf("xauth", "-f", authority.toString(), "add", displayAddress, "MIT-MAGIC-COOKIE-1", UUID.randomUUID().toString().replace("-", "")),
            fixture, evidence.directory.resolve("xauth.log"))
        evidence.event("prepared", mapOf("fresh_world" to true, "server_port" to port,
            "profile" to "loopback offline authentication, 4-chunk distances, software-rendered client; no feature scenarios",
            "requested_connected_seconds" to request.connectedDuration.seconds))
    }

    private fun configureClientProfile() {
        val options = clientRoot.resolve("options.txt")
        if (Files.isRegularFile(options)) {
            val settings = mapOf("renderDistance" to "4", "simulationDistance" to "4", "maxFps" to "30", "graphicsMode" to "0", "ao" to "false", "entityShadows" to "false")
            options.writeText(options.readText().lineSequence().joinToString("\n") { line ->
                settings[line.substringBefore(':')]?.let { "${line.substringBefore(':')}:$it" } ?: line
            })
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
        server!!.waitForLog(Regex("Done \\([0-9.]+s\\)!.*For help"), startupTimeout, "server readiness")
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
        val jvm = "-Xms1G -Xmx4G -XX:+UseG1GC -Dfile.encoding=UTF-8 -Djava.net.preferIPv6Addresses=false"
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

    override fun heartbeat() {
        ensureConnected()
        val marker = "BC_SESSION_${UUID.randomUUID().toString().replace("-", "") }"
        val offset = if (Files.isRegularFile(clientLog)) Files.size(clientLog) else 0L
        server!!.send("execute as $username run tellraw @s {\"text\":\"$marker\"}")
        client!!.waitUntil(if (connectedSince < 0) startupTimeout else responseTimeout, "fresh client heartbeat $marker") {
            ensureConnected()
            readLogFrom(clientLog, offset).text.contains(marker)
        }
        if (connectedSince < 0) {
            connectedSince = System.nanoTime()
            evidence.event("connected_interval_started", mapOf("required_seconds" to request.connectedDuration.seconds))
        }
        evidence.event("heartbeat", mapOf("marker" to marker, "server_alive" to true, "client_received" to true,
            "server_process_tree_rss_bytes" to processTreeMemory(server!!), "client_process_tree_rss_bytes" to processTreeMemory(client!!)))
    }

    override fun disconnectClient() {
        ensureConnected()
        val elapsed = System.nanoTime() - connectedSince
        check(connectedSince >= 0 && elapsed >= request.connectedDuration.toNanos()) { "connected interval was shorter than requested" }
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
