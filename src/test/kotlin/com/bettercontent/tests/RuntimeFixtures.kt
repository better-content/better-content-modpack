package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText

private val serverReady = Regex("Done \\([0-9.]+s\\)!.*For help")

class DedicatedServerFixture(
    private val evidence: EvidenceRun,
    private val environment: Map<String, String> = emptyMap(),
    private val allowLongClientLogin: Boolean = false,
) : AutoCloseable {
    val config = evidence.config
    val pair = CandidateLocator.locate(config.root)
    val serverExtract = evidence.fixture.resolve("server-extract")
    val server: Path
    val log = evidence.directory.resolve("server.log")
    val port = freePort()
    private val process: ManagedProcess

    init {
        evidence.event("candidate_selected", mapOf(
            "client" to pair.client.toString(),
            "client_sha256" to pair.clientSha256,
            "server" to pair.server.toString(),
            "server_sha256" to pair.serverSha256,
        ))
        serverExtract.createDirectories()
        Commands.run(
            listOf("unzip", "-q", "--", pair.server.toString(), "-d", serverExtract.toString()),
            evidence.fixture,
            evidence.directory.resolve("server-extract.log"),
        )
        val roots = Files.list(serverExtract).use { stream -> stream.filter(Files::isDirectory).toList() }
        require(roots.size == 1) { "server candidate must extract to one top-level directory" }
        server = roots.single()
        disableScheduledBackupsForRuntimeFixture(server)
        if (allowLongClientLogin) configureDedicatedServerMemory(server)
        replaceExact(server.resolve("eula.txt"), "eula=false", "eula=true")
        replaceExact(server.resolve("server.properties"), "online-mode=true", "online-mode=false")
        val properties = server.resolve("server.properties")
        val text = properties.readText()
        require(Regex("(?m)^server-port=25565$").containsMatchIn(text)) { "production server port contract is missing" }
        var runtimeProperties = text.replace(Regex("(?m)^server-port=25565$"), "server-port=$port")
        if (allowLongClientLogin) {
            val watchdog = Regex("(?m)^max-tick-time=.*$")
            runtimeProperties = if (watchdog.containsMatchIn(runtimeProperties)) {
                runtimeProperties.replace(watchdog, "max-tick-time=300000")
            } else {
                runtimeProperties.trimEnd() + "\nmax-tick-time=300000\n"
            }
            require("simulation-distance=10" in runtimeProperties && "view-distance=10" in runtimeProperties) {
                "production server distance contract is missing"
            }
            runtimeProperties = runtimeProperties
                .replace("simulation-distance=10", "simulation-distance=6")
                .replace("view-distance=10", "view-distance=4")
        }
        properties.toFile().writeText(runtimeProperties)
        process = ManagedProcess("server", listOf("./run.sh"), server, log, environment)
        evidence.event("server_started", mapOf("pid" to process.pid, "port" to port, "directory" to server.toString()))
    }

    fun waitReady(count: Int = 1) = process.waitForLogCount(serverReady, count, Duration.ofMinutes(15), "server readiness $count")
    fun send(command: String) {
        evidence.event("server_command", mapOf("command" to command))
        process.send(command)
    }
    fun waitLog(pattern: Regex, description: String, timeout: Duration = Duration.ofMinutes(15)) =
        process.waitForLog(pattern, timeout, description)
    fun waitLogCount(pattern: Regex, count: Int, description: String, timeout: Duration = Duration.ofMinutes(15)) =
        process.waitForLogCount(pattern, count, timeout, description)
    fun processAlive(): Boolean = process.alive

    fun commandResult(
        command: String,
        pattern: Regex,
        description: String,
        timeout: Duration,
        retryInterval: Duration? = null,
    ): MatchResult {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            val offset = if (log.exists()) log.readText().length else 0
            send(command)
            val attemptDeadline = minOf(deadline, System.nanoTime() + (retryInterval ?: timeout).toNanos())
            val text = if (log.exists()) log.readText() else ""
            while (System.nanoTime() < attemptDeadline) {
                val current = if (log.exists()) log.readText() else text
                pattern.find(current.drop(offset))?.let { return it }
                check(process.alive) { "server exited before $description; see $log" }
                Thread.sleep(250)
            }
            if (retryInterval == null) break
        }
        process.captureDiagnostics(evidence.directory.resolve("server-${description.replace(Regex("[^a-z0-9]+", RegexOption.IGNORE_CASE), "-")}-timeout"))
        error("timed out waiting for $description; see $log")
    }

    fun runtimeDump(timeout: Duration = Duration.ofMinutes(15)): Path {
        val dump = server.resolve("generated/runtime-dumps")
        require(Files.notExists(dump)) { "fresh fixture unexpectedly contains runtime evidence" }
        send("runtimedata dump")
        val deadline = System.nanoTime() + timeout.toNanos()
        while (!Files.isRegularFile(dump.resolve("snapshot.json")) && System.nanoTime() < deadline) Thread.sleep(500)
        require(Files.isRegularFile(dump.resolve("snapshot.json"))) { "runtime snapshot timed out" }
        return dump
    }

    fun assertHashes() {
        require(Hashes.sha256(pair.client) == pair.clientSha256) { "client candidate changed during test" }
        require(Hashes.sha256(pair.server) == pair.serverSha256) { "server candidate changed during test" }
    }

    fun auditLogs(extra: Collection<Path> = emptyList()) {
        preserveState()
        LogPolicy.requireClean(collectLogs(evidence.directory) + extra)
    }

    fun preserveState() {
        val output = evidence.directory.resolve("server-state").also { it.createDirectories() }
        listOf(
            server.resolve(".world_lifecycle_manager/perks-v2.tsv"),
            server.resolve(".world_lifecycle_manager/lineage-v5.tsv"),
            server.resolve("logs/world-lifecycle-manager-supervisor.log"),
        ).filter(Files::isRegularFile).forEach { source ->
            Files.copy(source, output.resolve(source.fileName.toString()), StandardCopyOption.REPLACE_EXISTING)
        }
        val archives = server.resolve(".world_lifecycle_manager/archives")
        if (Files.isDirectory(archives)) {
            val hashes = Files.list(archives).use { stream ->
                stream.filter(Files::isRegularFile).sorted().map { "${Hashes.sha256(it)}  ${it.fileName}" }.toList()
            }
            output.resolve("archive-sha256.txt").toFile().writeText(hashes.joinToString("\n", postfix = if (hashes.isEmpty()) "" else "\n"))
        }
    }

    fun stopGracefully() {
        if (process.alive) {
            send("stop")
            runCatching { process.waitForLog(Regex("Stopping server"), Duration.ofMinutes(2), "server stop") }
        }
        process.stop()
    }

    override fun close() = process.close()
}

private fun configureDedicatedServerMemory(server: Path) {
    val jvmArgs = server.resolve("user_jvm_args.txt")
    require(jvmArgs.isRegularFile()) { "fixture server is missing user_jvm_args.txt: $jvmArgs" }
    val text = jvmArgs.readText()
    val replacement = text.replace("-Xms4G", "-Xms1G").replace("-Xmx16G", "-Xmx6G")
    require(replacement != text) { "production server JVM memory contract is missing" }
    jvmArgs.toFile().writeText(replacement)
}

internal fun disableScheduledBackupsForRuntimeFixture(server: Path) {
    val config = server.resolve("config/ftbbackups2.json")
    require(config.isRegularFile()) { "server candidate is missing FTB Backups configuration: $config" }
    replaceExact(config, "\"enabled\": true", "\"enabled\": false")
}

class ClientFixture(
    private val evidence: EvidenceRun,
    private val dedicated: DedicatedServerFixture? = null,
    val username: String = evidence.config.username,
    private val slot: Int = 0,
    private val clientJvmArgs: String = DEFAULT_CLIENT_JVM_ARGS,
) : AutoCloseable {
    private val config = evidence.config
    private val pair = dedicated?.pair ?: CandidateLocator.locate(config.root)
    val client = evidence.fixture.resolve(if (slot == 0) "client" else "client-$slot").also { it.createDirectories() }
    val log = evidence.directory.resolve(
        when {
            dedicated == null -> "singleplayer.log"
            slot == 0 -> "client.log"
            else -> "client-$slot.log"
        },
    )
    private val xvfbLog = evidence.directory.resolve(if (slot == 0) "xvfb.log" else "xvfb-$slot.log")
    private val display = ":${200 + ((ProcessHandle.current().pid() + slot) % 500)}"
    private var xvfb: ManagedProcess? = null
    private var launcher: ManagedProcess? = null
    val uuid = offlineUuid(username)

    init {
        evidence.event("candidate_selected", mapOf(
            "client" to pair.client.toString(),
            "client_sha256" to pair.clientSha256,
            "server" to pair.server.toString(),
            "server_sha256" to pair.serverSha256,
        ))
    }

    fun prepare() {
        Commands.run(
            listOf("packwiz", "curseforge", "import", pair.client.toString(), "-y"),
            client,
            evidence.directory.resolve(if (slot == 0) "client-import.log" else "client-$slot-import.log"),
        )
        TaczFixtureSupport.reconcileImportedRootManifests(client, config.root)
        Commands.run(
            listOf(config.root.resolve("package.sh").toString(), "resolve", client.toString(), client.toString(), "client"),
            config.root,
            evidence.directory.resolve(if (slot == 0) "client-artifacts.log" else "client-$slot-artifacts.log"),
        )
        TaczFixtureSupport.assertResolvedArtifacts(client, config.root)
        if (dedicated != null) configureDedicatedClientProfile()
        client.resolve("saves").createDirectories()
        xvfb = ManagedProcess("xvfb-$slot", listOf("Xvfb", display, "-screen", "0", "1280x720x24", "-nolisten", "tcp"), client, xvfbLog)
        Thread.sleep(1000)
        check(xvfb!!.alive) { "Xvfb failed; see $xvfbLog" }
    }

    fun launchDedicated() {
        val server = requireNotNull(dedicated)
        launcher = launch(listOf("-s", "127.0.0.1", "-p", server.port.toString()))
    }

    fun launchSingleplayer() {
        launcher = launch(emptyList())
    }

    private fun launch(connection: List<String>): ManagedProcess {
        val environment = mapOf(
            "DISPLAY" to display,
            "LIBGL_ALWAYS_SOFTWARE" to "1",
            "MESA_GL_VERSION_OVERRIDE" to "4.6",
            "MESA_GLSL_VERSION_OVERRIDE" to "460",
            "ALSOFT_DRIVERS" to "null",
        )
        val command = mutableListOf(
            "pipx", "run", "--spec", "portablemc==4.4.1", "portablemc",
            "--main-dir", config.clientMain.toString(), "--work-dir", client.toString(), "--timeout", "120",
            "start", "--jvm", config.java.toString(),
            "--jvm-args=$clientJvmArgs -XX:+UseG1GC -Dfile.encoding=UTF-8 -Djava.net.preferIPv6Addresses=false -Dlog4j.configurationFile=${client.resolve("config/better-content-log4j2.xml")}",
            "--resolution", "1280x720", "-u", username, "-i", uuid,
        )
        command += connection
        command += "forge:1.20.1-47.4.13"
        return ManagedProcess("minecraft-client", command, client, log, environment)
    }

    private fun configureDedicatedClientProfile() {
        val options = client.resolve("options.txt")
        require(options.isRegularFile()) { "dedicated client is missing options.txt: $options" }
        listOf(
            "renderDistance:12" to "renderDistance:4",
            "simulationDistance:12" to "simulationDistance:5",
            "entityDistanceScaling:1.0" to "entityDistanceScaling:0.5",
            "maxFps:180" to "maxFps:30",
            "graphicsMode:1" to "graphicsMode:0",
            "ao:true" to "ao:false",
            "entityShadows:true" to "entityShadows:false",
        ).forEach { (old, replacement) -> replaceExact(options, old, replacement) }

        // All three clients render through software OpenGL in this headless fixture. Keep
        // Distant Horizons installed and initialized, but bound its worker footprint so
        // a newly joining full-pack client cannot starve the already-connected clients.
        val distantHorizons = client.resolve("config/DistantHorizons.toml")
        require(distantHorizons.isRegularFile()) { "dedicated client is missing Distant Horizons config: $distantHorizons" }
        replaceExact(distantHorizons, "numberOfThreads = 6", "numberOfThreads = 1")
        replaceExact(distantHorizons, "lodChunkRenderDistanceRadius = 32", "lodChunkRenderDistanceRadius = 4")
        replaceExact(distantHorizons, "enableDistantGeneration = true", "enableDistantGeneration = false")
        replaceExact(distantHorizons, "rendererMode = \"DEFAULT\"", "rendererMode = \"DISABLED\"")
        evidence.event("dedicated_client_fixture_profile", mapOf(
            "client" to username,
            "complete_modpack" to true,
            "software_rendering_limits" to mapOf(
                "render_distance" to 4,
                "simulation_distance" to 5,
                "max_fps" to 30,
                "distant_horizons_threads" to 1,
                "distant_horizons_lod_radius" to 4,
                "distant_horizons_generation" to false,
                "distant_horizons_renderer" to "DISABLED",
            ),
        ))
    }

    fun waitDedicatedJoin() {
        val server = requireNotNull(dedicated)
        server.waitLog(Regex("${Regex.escape(username)} joined the game"), "dedicated client join", Duration.ofMinutes(10))
    }

    fun processAlive(): Boolean = launcher?.alive == true

    fun waitSettled() {
        val deadline = System.nanoTime() + Duration.ofMinutes(10).toNanos()
        val settleUntil = System.nanoTime() + Duration.ofSeconds(config.settleSeconds).toNanos()
        while (System.nanoTime() < deadline) {
            val text = if (log.exists()) log.readText() else ""
            if (System.nanoTime() >= settleUntil && Regex("\\[EMI] Reloaded EMI in [0-9]+ms").containsMatchIn(text) &&
                Regex("Loaded [0-9]+ advancements").containsMatchIn(text)) return
            check(launcher?.alive == true) { "client exited before settling; see $log" }
            Thread.sleep(500)
        }
        launcher?.captureDiagnostics(evidence.directory.resolve("client-settle-timeout"))
        error("client settle timed out; see $log")
    }

    fun waitTitleScreen() = requireNotNull(launcher).waitForLog(
        Regex("ScreenCustomizationLayer registered: title_screen"), Duration.ofMinutes(10), "customized title screen",
    )

    fun capture(name: String) {
        val target = evidence.directory.resolve(name)
        val jshell = config.java.parent.resolve("jshell")
        val script = """
            import java.awt.Robot;
            import java.awt.Rectangle;
            import java.awt.Toolkit;
            import java.io.File;
            import javax.imageio.ImageIO;
            var robot = new Robot();
            ImageIO.write(robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())), "png", new File("${target.toString().replace("\\", "\\\\")}"));
            /exit
        """.trimIndent()
        Commands.runWithInput(listOf(jshell.toString()), client, evidence.directory.resolve("$name.log"), script, mapOf("DISPLAY" to display))
        require(target.isRegularFile() && Files.size(target) > 0) { "screen capture was not produced: $target" }
    }

    fun assertHashes() {
        require(Hashes.sha256(pair.client) == pair.clientSha256) { "client candidate changed during test" }
        require(Hashes.sha256(pair.server) == pair.serverSha256) { "server candidate changed during test" }
    }

    override fun close() {
        closeAll(launcher, xvfb)
    }
}

private const val DEFAULT_CLIENT_JVM_ARGS = "-Xms2G -Xmx12G"

fun promoteSnapshot(source: Path, destination: Path, token: String): String {
    val snapshotId = RuntimeSnapshotValidator.validate(source)
    destination.parent.createDirectories()
    val staging = destination.parent.resolve(".runtime-dumps-$token.staging")
    val backup = destination.parent.resolve(".runtime-dumps-$token.previous")
    require(!staging.exists() && !backup.exists()) { "runtime snapshot promotion paths already exist" }
    source.toFile().copyRecursively(staging.toFile())
    val hadPrevious = destination.exists()
    try {
        if (hadPrevious) Files.move(destination, backup, StandardCopyOption.ATOMIC_MOVE)
        Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE)
    } catch (error: Throwable) {
        if (hadPrevious && backup.exists() && !destination.exists()) Files.move(backup, destination, StandardCopyOption.ATOMIC_MOVE)
        throw error
    } finally {
        staging.toFile().deleteRecursively()
    }
    backup.toFile().deleteRecursively()
    return snapshotId
}
