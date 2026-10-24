package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.LinkOption
import java.util.UUID
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
    private val seed: Long? = null,

) : AutoCloseable {
    val config = evidence.config
    val pair = CandidateLocator.locate(config.root).also { evidence.integrity.registerCandidate(it) }
    val diagnosticSupport = RuntimeDiagnosticSupport(evidence, pair, "SmokeClient1")
    val serverExtract = evidence.fixture.resolve("server-extract")
    val server: Path
    val log = evidence.directory.resolve("server.log")
    val port = evidence.nativePort()
    private var process: ManagedProcess
    private var starts = 1
    private lateinit var auditLaunch: FixtureAuditLogging.Launch

    init {
        evidence.event("candidate_selected", mapOf(
            "client" to pair.client.toString(),
            "client_sha256" to pair.clientSha256,
            "server" to pair.server.toString(),
            "server_sha256" to pair.serverSha256,
        ))
        val authoredProperties = Files.readString(config.root.resolve("server-config/production.properties"))
        CandidateStateContract.serverArchive(pair.server, authoredProperties)
        serverExtract.createDirectories()
        Commands.run(
            listOf("unzip", "-q", "--", pair.server.toString(), "-d", serverExtract.toString()),
            evidence.fixture,
            evidence.directory.resolve("server-extract.log"),
        )
        val roots = Files.list(serverExtract).use { stream -> stream.filter(Files::isDirectory).toList() }
        require(roots.size == 1) { "server candidate must extract to one top-level directory" }
        server = roots.single()
        CandidateStateContract.extractedServer(server, authoredProperties)
        val levelName = CandidateStateContract.levelName(CandidateStateContract.properties(
            Files.readString(server.resolve("server.properties"))))
        evidence.event("first_launch_freshness", mapOf("role" to "server", "level_name" to levelName,
            "world_absent" to true, "lifecycle_state_absent" to true))
        disableScheduledBackupsForRuntimeFixture(server)
        if (allowLongClientLogin) configureDedicatedServerMemory(server)
        configureFixtureServerProcessorBudget(server)
        replaceExact(server.resolve("eula.txt"), "eula=false", "eula=true")
        replaceExact(server.resolve("server.properties"), "online-mode=true", "online-mode=false")
        val properties = server.resolve("server.properties")
        val text = properties.readText()
        require(Regex("(?m)^server-port=25565$").containsMatchIn(text)) { "production server port contract is missing" }
        var runtimeProperties = text.replace(Regex("(?m)^server-port=25565$"), "server-port=$port")
        if (seed != null) {
            val seedProperty = Regex("(?m)^level-seed=.*$")
            runtimeProperties = if (seedProperty.containsMatchIn(runtimeProperties)) {
                runtimeProperties.replace(seedProperty, "level-seed=$seed")
            } else {
                runtimeProperties.trimEnd() + "\nlevel-seed=$seed\n"
            }
        }
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
        diagnosticSupport.let { support ->
            support.install(server)
            val args = server.resolve("user_jvm_args.txt")
            args.toFile().appendText("\n${support.properties}\n")
        }
        auditLaunch = prepareAudit("server-initial")
        process = ManagedProcess("server", listOf("./run.sh"), server, log, environment,
            lifecycleEvent = { evidence.event("process_lifecycle", it) }).also { evidence.own(it) }
        recordServerResourceBudget()
        evidence.event("server_started", mapOf("pid" to process.pid, "port" to port, "directory" to server.toString()))
    }

    private fun prepareAudit(label: String): FixtureAuditLogging.Launch {
        if (::auditLaunch.isInitialized) auditLaunch.assertStable()
        val launch = FixtureAuditLogging.prepare(server, label)
        val args = server.resolve("user_jvm_args.txt")
        val preserved = args.readText().lineSequence().map { line ->
            if (line.trimStart().startsWith("#")) line
            else fixtureJvmTokens(line).filterNot { it.startsWith("-Dlog4j.configurationFile=") }.joinToString(" ")
        }.joinToString("\n").trimEnd()
        Files.writeString(args, "$preserved\n${launch.jvmArgument}\n")
        evidence.registerAudit(launch)
        return launch
    }

    fun waitReady(count: Int = 1) {
        process.waitForLogCount(serverReady, count, Duration.ofMinutes(15), "server readiness $count")
        evidence.auditReady(auditLaunch)
    }

    fun assertRunning(stage: String) = process.assertRunning(stage)
    fun assertHealthyBeforeTeardown(stage: String) = process.assertHealthyBeforeTeardown(stage)
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
                process.assertRunning(description)
                pattern.find(current.drop(offset))?.let {
                    process.assertRunning(description)
                    return it
                }
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
        diagnosticSupport.assertStable()
        auditLaunch.assertStable()
    }

    fun auditLogs(extra: Collection<Path> = emptyList()) {
        preserveState()
        val logs = collectLogs(evidence.directory) + extra
        LogPolicy.requireClean(logs, runtimeLogCompatibility(evidence, logs))
    }

    fun preserveState() {
        val output = evidence.directory.resolve("server-state").also { it.createDirectories() }
        listOf(
            server.resolve(".better_world_management/perks-v2.tsv"),
            server.resolve(".better_world_management/lineage-v5.tsv"),
            server.resolve("logs/better-world-management-supervisor.log"),
        ).filter(Files::isRegularFile).forEach { source ->
            Files.copy(source, output.resolve(source.fileName.toString()), StandardCopyOption.REPLACE_EXISTING)
        }
        val archives = server.resolve(".better_world_management/archives")
        if (Files.isDirectory(archives)) {
            val hashes = Files.list(archives).use { stream ->
                stream.filter(Files::isRegularFile).sorted().map { "${Hashes.sha256(it)}  ${it.fileName}" }.toList()
            }
            output.resolve("archive-sha256.txt").toFile().writeText(hashes.joinToString("\n", postfix = if (hashes.isEmpty()) "" else "\n"))
        }
    }

    fun stopGracefully() {
        process.assertHealthyBeforeTeardown("server stop")
        if (process.alive) {
            process.expectNaturalExit("native server stop command")
            send("stop")
            process.waitForExit(Duration.ofMinutes(3), "server stop")
        }
        process.stop()
    }

    fun restart() {
        require(!process.alive) { "server must be stopped before restart" }
        process.assertHealthyBeforeTeardown("server restart")
        auditLaunch = prepareAudit("server-restart-$starts")
        process = ManagedProcess("server-restart", listOf("./run.sh"), server, log, environment,
            lifecycleEvent = { evidence.event("process_lifecycle", it) }).also { evidence.own(it) }
        recordServerResourceBudget()
        evidence.event("server_restarted", mapOf("pid" to process.pid, "port" to port, "directory" to server.toString()))
        starts++
        waitReady(starts)
    }

    private fun recordServerResourceBudget() {
        val arguments = activeFixtureServerArguments(server.resolve("user_jvm_args.txt").readText())
        evidence.event("fixture_resource_budget", fixtureResourceBudgetEvidence("dedicated-server", arguments, 8) +
            mapOf("launcher_pid" to process.pid))
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

// Supported HotSpot/Mesa scheduling budgets, not heap caps, renderer bypasses or game criteria.
// LP_NUM_THREADS: https://docs.mesa3d.org/envvars.html#envvar-LP_NUM_THREADS
private val fixtureJvmToken = Regex("""(?:[^\s"'\\]|\\.|"(?:\\.|[^"\\])*"|'[^']*')+""")
private val fixtureProcessorFlag = Regex("-XX:ActiveProcessorCount=\\S+")
private fun isFixtureProcessorFlag(token: String): Boolean = fixtureProcessorFlag.matches(
    token.removeSurrounding("\"").removeSurrounding("'"),
)

internal fun fixtureJvmTokens(arguments: String): List<String> {
    var end = 0
    val tokens = fixtureJvmToken.findAll(arguments).map { match ->
        require(arguments.substring(end, match.range.first).isBlank()) { "Malformed fixture JVM arguments" }
        end = match.range.last + 1
        match.value
    }.toList()
    require(arguments.substring(end).isBlank()) { "Malformed fixture JVM arguments" }
    return tokens
}

internal fun normalizeFixtureProcessorBudget(arguments: String, processors: Int): String {
    require(processors > 0) { "Fixture processor budget must be positive" }
    val preserved = fixtureJvmTokens(arguments).filterNot(::isFixtureProcessorFlag)
    return (preserved + "-XX:ActiveProcessorCount=$processors").joinToString(" ")
}

internal fun activeFixtureServerArguments(text: String): String = text.lineSequence()
    .filterNot { it.trimStart().startsWith("#") }.joinToString(" ")

internal fun configureFixtureServerProcessorBudget(server: Path) {
    val file = server.resolve("user_jvm_args.txt")
    require(file.isRegularFile()) { "fixture server is missing user_jvm_args.txt" }
    // Preserve comments and every non-processor argument, including the caller's existing heap contract.
    val preserved = file.readText().lineSequence().map { line ->
        if (line.trimStart().startsWith("#")) line
        else fixtureJvmTokens(line).filterNot(::isFixtureProcessorFlag).joinToString(" ")
    }.joinToString("\n").trimEnd()
    file.toFile().writeText("$preserved\n-XX:ActiveProcessorCount=8\n")
}

internal fun fixtureResourceBudgetEvidence(role: String, arguments: String, processors: Int): Map<String, Any> {
    val tokens = fixtureJvmTokens(arguments)
    require(tokens.filter(::isFixtureProcessorFlag) == listOf("-XX:ActiveProcessorCount=$processors")) {
        "Fixture JVM processor budget is missing or duplicated"
    }
    fun heap(prefix: String): String = tokens.lastOrNull { it.matches(Regex("${Regex.escape(prefix)}[0-9]+[kKmMgG]?")) }
        ?.removePrefix(prefix) ?: "unspecified"
    return mapOf(
        "role" to role, "measurement" to "launch_arguments", "jvm_processor_count" to processors,
        "minimum_heap" to heap("-Xms"), "maximum_heap" to heap("-Xmx"),
        "gc_flags" to tokens.filter { it.matches(Regex("-XX:[+-]Use[A-Za-z0-9]+GC")) }.distinct().take(4),
    )
}

internal fun disableScheduledBackupsForRuntimeFixture(server: Path) {
    val config = server.resolve("config/ftbbackups2.json")
    require(config.isRegularFile()) { "server candidate is missing FTB Backups configuration: $config" }
    replaceExact(config, "\"enabled\": true", "\"enabled\": false")
}

internal fun portableClientCommand(config: TestConfig, client: Path, pins: ClientPackImport.Pins,
    jvmArgs: String, username: String, uuid: String, connection: List<String>): List<String> {
    require(pins.coordinate == CandidateStateContract.COORDINATE) { "unsupported client runtime" }
    return listOf("pipx", "run", "--spec", "portablemc==4.4.1", "portablemc",
        "--main-dir", config.clientMain.toString(), "--work-dir", client.toString(), "--timeout", "120",
        "start", "--jvm", config.java.toString(), "--jvm-args=$jvmArgs",
        "--resolution", "1280x720", "-u", username, "-i", uuid) + connection + "forge:${pins.coordinate}"
}

internal fun quickPlayClientCommand(config: TestConfig, client: Path, pins: ClientPackImport.Pins,
    jvmArgs: String, username: String, uuid: String, world: String): List<String> {
    require(pins.coordinate == CandidateStateContract.COORDINATE) { "unsupported client runtime" }
    return listOf("pipx", "run", "--spec", "portablemc==4.4.1", "python",
        config.root.resolve("src/test/resources/quickplay_world.py").toString(), config.clientMain.toString(),
        client.toString(), config.java.toString(), jvmArgs, username, uuid, world, pins.coordinate)
}

class ClientFixture(
    private val evidence: EvidenceRun,
    private val dedicated: DedicatedServerFixture? = null,
    val username: String = evidence.config.username,
    private val slot: Int = 0,
    private val clientJvmArgs: String = DEFAULT_CLIENT_JVM_ARGS,
    private val productionWorldStartup: Boolean = false,

) : AutoCloseable {
    private val config = evidence.config
    private val pair = (dedicated?.pair ?: CandidateLocator.locate(config.root)).also { evidence.integrity.registerCandidate(it) }
    private lateinit var pins: ClientPackImport.Pins
    private var auditLaunch: FixtureAuditLogging.Launch? = null
    val diagnosticSupport = dedicated?.diagnosticSupport ?: RuntimeDiagnosticSupport(evidence, pair, username)
    private var quickPlayLogOffset = 0
    private var verifyExitBinding: FixtureVerifyExitBinding? = null
    private var lifecycleVerified = false
    private var verifyLoadedObserved = false
    val client = evidence.fixture.resolve(if (slot == 0) "client" else "client-$slot").also { it.createDirectories() }
    var log = evidence.directory.resolve(
        when {
            dedicated == null -> if (slot == 0) "singleplayer.log" else "singleplayer-$slot.log"
            slot == 0 -> "client.log"
            else -> "client-$slot.log"
        },
    )
        private set
    private val xvfbLog = evidence.directory.resolve(if (slot == 0) "xvfb.log" else "xvfb-$slot.log")
    internal val display = ":${200 + ((ProcessHandle.current().pid() + slot) % 500)}"
    private var xvfb: ManagedProcess? = null
    private var launcher: ManagedProcess? = null
    private var priorJoinCount = 0
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
        val importLogName = if (slot == 0) "client-import" else "client-$slot-import"
        CandidateStateContract.clientArchive(pair.client)
        pins = ClientPackImport.pins(com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(
            ZipContracts.entryText(pair.client, "manifest.json")))
        val initializedBaseline = ClientPackImport.initialize(pair.client, pair.clientSha256, client,
            evidence.directory.resolve(if (slot == 0) "client-init.log" else "client-$slot-init.log"))
        var imported = false
        for (attempt in 1..3) {
            val importLog = evidence.directory.resolve(
                if (attempt == 1) "$importLogName.log" else "$importLogName-attempt-$attempt.log",
            )
            try {
                Commands.run(listOf("packwiz", "curseforge", "import", pair.client.toString(), "-y"), client, importLog)
                imported = true
                break
            } catch (failure: IllegalStateException) {
                val transientHandshake = importLog.isRegularFile() &&
                    importLog.readText().contains("net/http: TLS handshake timeout")
                if (!transientHandshake || !ClientPackImport.unchanged(client, initializedBaseline) || attempt == 3) throw failure
                evidence.event("client_import_retry", mapOf(
                    "client" to username, "attempt" to attempt, "reason" to "CurseForge TLS handshake timeout",
                ))
                Thread.sleep(attempt * 2_000L)
            }
        }
        check(imported) { "Packwiz client import did not complete" }
        TaczFixtureSupport.reconcileImportedRootManifests(client, config.root)
        Commands.run(
            listOf(config.root.resolve("package.sh").toString(), "resolve", client.toString(), client.toString(), "client"),
            config.root,
            evidence.directory.resolve(if (slot == 0) "client-artifacts.log" else "client-$slot-artifacts.log"),
        )
        TaczFixtureSupport.assertResolvedArtifacts(client, config.root)
        diagnosticSupport.install(client)
        if (dedicated != null || slot == 5 || slot == 6) configureRuntimeClientProfile()
        CandidateStateContract.extractedFresh(client)
        evidence.event("first_launch_freshness", mapOf("role" to "client", "client" to username,
            "packaged_saves_absent" to true, "lifecycle_state_absent" to true))
        client.resolve("saves").createDirectories()
        xvfb = ManagedProcess("xvfb-$slot", listOf("Xvfb", display, "-screen", "0", "1280x720x24", "-nolisten", "tcp"), client, xvfbLog,
            lifecycleEvent = { evidence.event("process_lifecycle", it) }).also { evidence.own(it) }
        Thread.sleep(1000)
        check(xvfb!!.alive) { "Xvfb failed; see $xvfbLog" }
    }

    fun launchDedicated() {
        val server = requireNotNull(dedicated)
        val pattern = Regex("${Regex.escape(username)} joined the game")
        priorJoinCount = if (server.log.exists()) pattern.findAll(server.log.readText()).count() else 0
        launcher = launch(listOf("-s", "127.0.0.1", "-p", server.port.toString()))
    }

    fun restartDedicated(attempt: Int) {
        requireNotNull(dedicated) { "dedicated client restart requires a server fixture" }
        require(attempt > 0)
        launcher?.close()
        launcher = null
        log = evidence.directory.resolve("client-$slot-restart-$attempt.log")
        launchDedicated()
        waitDedicatedJoin()
        waitSettled()
        evidence.event("dedicated_client_restarted", mapOf(
            "client" to username,
            "attempt" to attempt,
            "log" to log.toString(),
        ))
    }

    fun launchSingleplayer() {
        launcher = launch(emptyList())
    }

    fun launchFreshWorld(nonce: UUID): Path {
        require(productionWorldStartup && dedicated == null) { "native creation requires a standalone production startup profile" }
        val name = "Fresh-$nonce"
        val world = client.resolve("saves/$name").toAbsolutePath().normalize()
        require(!Files.exists(world)) { "unseeded native startup cannot receive a preexisting save" }
        val args = mapOf("name" to name, "nonce" to nonce.toString(), "root" to world.toString(), "uuid" to uuid.toString())
            .entries.joinToString(" ") { (key, value) ->
                require(value.none { it.isWhitespace() || it == '\"' || it == '\'' })
                "-Dbc.pack_test.fresh.$key=$value"
            }
        evidence.event("native_fresh_world_contract", mapOf("nonce" to nonce.toString(), "world_root" to world.toString(),
            "creation_api" to "WorldOpenFlows.createFreshLevel", "preset" to "minecraft:normal", "seed" to "native_random",
            "preexisting_save" to false, "spawn_state_injected" to false, "production_spawn_search" to true,
            "onboarding" to "ClientOnboardingActions.submitSpawnOnly", "processor_count" to 8,
            "heap" to "12G", "startup_deadline_seconds" to 900, "pointer_injection" to false))
        launcher = launch(emptyList(), args)
        return world
    }

    fun launchQuickPlayWorld(world: String, mode: String) {
        require(mode == "save" || mode == "verify")
        quickPlayLogOffset = if (log.exists()) log.readText().length else 0
        lifecycleVerified = false
        verifyLoadedObserved = false
        val lifecycleDir = evidence.fixture.resolve("world-lifecycle").toAbsolutePath().also { it.createDirectories() }
        verifyExitBinding = fixtureVerifyExitBinding(mode, world, dedicated != null,
            config.runId, username, uuid, evidence.fixture, client, lifecycleDir)
        verifyExitBinding?.let { binding ->
            evidence.event("lifecycle_verify_exit_configured", mapOf("measurement" to "launch_request_binding",
                "nonce" to binding.nonce.toString(), "world_root" to binding.worldRoot.toString(),
                "request" to binding.requestFile.toString(), "native_exit_observed" to false))
        }
        val exitArgs = verifyExitBinding?.properties.orEmpty()
        val diagnosticArgs = diagnosticSupport.properties
        val logging = prepareClientAudit("quickplay-$mode")
        val jvmArgs = normalizeFixtureProcessorBudget(
            "$clientJvmArgs $diagnosticArgs $exitArgs -XX:+UseG1GC -Dfile.encoding=UTF-8 -Djava.net.preferIPv6Addresses=false " +
                "${logging.jvmArgument} -Dbc.pack_test.world=$mode",
            4,
        )
        val command = quickPlayClientCommand(config, client, pins, jvmArgs, username, uuid, world)
        launcher = ManagedProcess("minecraft-client", command, client, log, clientEnvironment(),
            lifecycleEvent = { evidence.event("process_lifecycle", it) }).also { evidence.own(it) }
        recordClientResourceBudget(jvmArgs, "quickplay", requireNotNull(launcher).pid)
    }

    private fun launch(connection: List<String>, extraArguments: String = ""): ManagedProcess {
        val environment = clientEnvironment()
        val logging = prepareClientAudit("client")
        val jvmArgs = normalizeFixtureProcessorBudget(
            "$clientJvmArgs ${diagnosticSupport.properties} $extraArguments -XX:+UseG1GC -Dfile.encoding=UTF-8 -Djava.net.preferIPv6Addresses=false ${logging.jvmArgument}",
            if (productionWorldStartup) 8 else 4,
        )
        val command = portableClientCommand(config, client, pins, jvmArgs, username, uuid, connection)
        val process = ManagedProcess("minecraft-client", command, client, log, environment,
            lifecycleEvent = { evidence.event("process_lifecycle", it) }).also { evidence.own(it) }
        recordClientResourceBudget(jvmArgs, "portablemc", process.pid)
        return process
    }

    private fun prepareClientAudit(label: String): FixtureAuditLogging.Launch {
        auditLaunch?.assertStable()
        val launch = FixtureAuditLogging.prepare(client, label)
        auditLaunch = launch
        evidence.registerAudit(launch)
        evidence.event("client_runtime_pins", mapOf("client" to username, "minecraft" to pins.minecraft,
            "forge" to pins.forge, "coordinate" to pins.coordinate))
        return launch
    }

    private fun recordClientResourceBudget(arguments: String, path: String, pid: Long) {
        evidence.event("fixture_resource_budget", fixtureResourceBudgetEvidence(
            if (productionWorldStartup) "integrated-startup-client" else "client", arguments, if (productionWorldStartup) 8 else 4) + mapOf(
            "client" to username, "launch_path" to path, "launcher_pid" to pid,
            "lp_num_threads" to 2, "software_renderer" to true,
        ))
    }

    private fun clientEnvironment() = mapOf(
            "DISPLAY" to display,
            "LIBGL_ALWAYS_SOFTWARE" to "1",
            "LP_NUM_THREADS" to "2",
            "MESA_GL_VERSION_OVERRIDE" to "4.6",
            "MESA_GLSL_VERSION_OVERRIDE" to "460",
            "ALSOFT_DRIVERS" to "null",
        ) + gameplayAudioEnvironment(evidence, log)

    /** Lifecycle oracle only; no storage checkpoint may authorize exit. */
    fun requestNormalVerifyExit(savedMarker: UUID, reopenedMarker: UUID, savedTime: Long, reopenedTime: Long) {
        require(verifyLoadedObserved && savedMarker == reopenedMarker && savedTime >= 0 && reopenedTime >= savedTime) {
            "normal verify exit requires native LOADED and matching marker/nondecreasing time"
        }
        lifecycleVerified = true
        val binding = requireNotNull(verifyExitBinding) { "normal verify exit is not configured for this fixture" }
        val request = writeFixtureVerifyExitRequest(binding, lifecycleVerified, verifyLoadedObserved)
        evidence.event("lifecycle_verify_exit_requested", mapOf("nonce" to binding.nonce.toString(),
            "request" to request.toString(), "world_root" to binding.worldRoot.toString(),
            "player_uuid" to binding.playerId.toString(), "native_exit_observed" to false))
    }

    fun waitForWorldProbe(marker: String, timeout: Duration = Duration.ofMinutes(10)) {
        val binding = verifyExitBinding
        val pattern = if (marker == "BC_DEBUG_WORLD_EXITED" && binding != null) binding.exitMarker
            else Regex(Regex.escape(marker))
        val process = requireNotNull(launcher)
        if (binding != null && marker in setOf("BC_DEBUG_WORLD_LOADED mode=verify", "BC_DEBUG_WORLD_EXITED")) {
            // A previous launch's uncorrelated LOADED must not authorize this fresh verification exit.
            val deadline = System.nanoTime() + timeout.toNanos()
            var observed = false
            while (System.nanoTime() < deadline) {
                val suffix = if (log.exists()) log.readText().drop(quickPlayLogOffset) else ""
                assertRunning("singleplayer world probe $marker")
                if (pattern.containsMatchIn(suffix)) {
                    assertRunning("singleplayer world probe $marker")
                    observed = true
                    break
                }
                Thread.sleep(250)
            }
            if (!observed) {
                process.captureDiagnostics(evidence.directory.resolve("lifecycle-verify-native-exit-timeout"))
                error("timed out waiting for fresh singleplayer world probe $marker; see $log")
            }
        } else process.waitForLog(pattern, timeout, "singleplayer world probe $marker")
        assertRunning("singleplayer world probe complete: $marker")
        evidence.auditReady(requireNotNull(auditLaunch))
        if (marker == "BC_DEBUG_WORLD_LOADED mode=verify" && binding != null) verifyLoadedObserved = true
        if (marker == "BC_DEBUG_WORLD_EXITED" && binding != null)
            evidence.event("lifecycle_verify_native_exit_observed", mapOf("nonce" to binding.nonce.toString(),
                "world_root" to binding.worldRoot.toString(), "boundary" to "native_clearLevel_return_server_terminated",
                "saved_data_acceptance" to "requires_independent_post_exit_checks"))
    }

    private fun configureRuntimeClientProfile() {
        val options = client.resolve("options.txt")
        require(options.isRegularFile()) { "runtime client is missing options.txt: $options" }
        listOf(
            "renderDistance:12" to "renderDistance:4",
            "simulationDistance:12" to "simulationDistance:5",
            "entityDistanceScaling:1.0" to "entityDistanceScaling:0.5",
            "maxFps:180" to "maxFps:30",
            "graphicsMode:1" to "graphicsMode:0",
            "ao:true" to "ao:false",
            "entityShadows:true" to "entityShadows:false",
        ).forEach { (old, replacement) -> replaceExact(options, old, replacement) }

        // Headless clients render through software OpenGL. Keep Distant Horizons installed
        // and initialized, but avoid background LOD work that competes with the server and
        // races the integrated server during the save/reopen fixture's world unload.
        val distantHorizons = client.resolve("config/DistantHorizons.toml")
        require(distantHorizons.isRegularFile()) { "runtime client is missing Distant Horizons config: $distantHorizons" }
        if (!productionWorldStartup) {
            replaceExact(distantHorizons, "numberOfThreads = 6", "numberOfThreads = 1")
            replaceExact(distantHorizons, "lodChunkRenderDistanceRadius = 32", "lodChunkRenderDistanceRadius = 4")
            replaceExact(distantHorizons, "enableDistantGeneration = true", "enableDistantGeneration = false")
            replaceExact(distantHorizons, "rendererMode = \"DEFAULT\"", "rendererMode = \"DISABLED\"")
        }
        evidence.event(if (dedicated != null) "dedicated_client_fixture_profile" else "singleplayer_client_fixture_profile", mapOf(
            "client" to username,
            "complete_modpack" to true,
            "software_rendering_limits" to mapOf(
                "render_distance" to 4,
                "simulation_distance" to 5,
                "max_fps" to 30,
                "distant_horizons_threads" to if (productionWorldStartup) 6 else 1,
                "distant_horizons_lod_radius" to if (productionWorldStartup) 32 else 4,
                "distant_horizons_generation" to productionWorldStartup,
                "distant_horizons_renderer" to if (productionWorldStartup) "DEFAULT" else "DISABLED",
            ),
        ))
    }

    fun waitDedicatedJoin() {
        val server = requireNotNull(dedicated)
        assertRunning("dedicated client join start")
        server.waitLogCount(Regex("${Regex.escape(username)} joined the game"), priorJoinCount + 1,
            "fresh dedicated client join", Duration.ofMinutes(10))
        assertRunning("dedicated client join complete")
        evidence.auditReady(requireNotNull(auditLaunch))
    }

    fun processAlive(): Boolean = launcher?.alive == true

    fun assertRunning(stage: String) {
        requireNotNull(launcher).assertRunning(stage)
        requireNotNull(xvfb).assertRunning("$stage display")
    }

    fun assertHealthyBeforeTeardown(stage: String) {
        launcher?.assertHealthyBeforeTeardown(stage)
        xvfb?.assertHealthyBeforeTeardown("$stage display")
    }

    fun expectNativeExit(reason: String) = requireNotNull(launcher).expectNaturalExit(reason)

    fun awaitNativeExit(timeout: Duration, description: String) = requireNotNull(launcher).waitForExit(timeout, description)

    fun waitSettled(timeout: Duration = Duration.ofMinutes(10)) {
        val deadline = System.nanoTime() + timeout.toNanos()
        val settleUntil = System.nanoTime() + Duration.ofSeconds(config.settleSeconds).toNanos()
        while (System.nanoTime() < deadline) {
            val text = if (log.exists()) log.readText() else ""
            assertRunning("client settling")
            if (System.nanoTime() >= settleUntil && Regex("\\[EMI] Reloaded EMI in [0-9]+ms").containsMatchIn(text) &&
                Regex("Loaded [0-9]+ advancements").containsMatchIn(text)) {
                assertRunning("client settled")
                evidence.auditReady(requireNotNull(auditLaunch))
                return
            }
            Thread.sleep(500)
        }
        launcher?.captureDiagnostics(evidence.directory.resolve("client-settle-timeout"))
        error("client settle timed out; see $log")
    }

    fun waitTitleScreen() {
        requireNotNull(launcher).waitForLog(
            Regex("ScreenCustomizationLayer registered: title_screen"), Duration.ofMinutes(10), "customized title screen",
        )
        assertRunning("title screen")
        evidence.auditReady(requireNotNull(auditLaunch))
    }

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
        diagnosticSupport.assertStable()
        auditLaunch?.assertStable()
    }

    override fun close() {
        closeAll(launcher, xvfb)
    }
}

internal data class FixtureVerifyExitBinding(
    val nonce: UUID, val runId: String, val playerName: String, val playerId: UUID,
    val fixtureRoot: Path, val worldRoot: Path, val requestDir: Path,
) {
    val requestFile: Path get() = requestDir.resolve("verify-exit-$nonce.request")
    val properties: String get() = mapOf("run_id" to runId, "player" to playerName, "nonce" to nonce.toString(), "player_uuid" to playerId.toString(),
        "fixture_root" to fixtureRoot.toString(), "world_root" to worldRoot.toString(), "request_dir" to requestDir.toString())
        .entries.joinToString(" ") { (key, value) ->
            require(value.none { it.isWhitespace() || it == '"' || it == '\'' }) { "unsafe verify exit property" }
            "-Dbc.pack_test.verify_exit.$key=$value"
        }
    val payload: String get() = linkedMapOf("schema" to "bc.pack_test.verify_exit.v1", "mode" to "verify",
        "nonce" to nonce.toString(), "run_id" to runId, "player_name" to playerName, "player_uuid" to playerId.toString(),
        "fixture_root" to fixtureRoot.toString(), "world_root" to worldRoot.toString(), "request_dir" to requestDir.toString())
        .entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" }
    val exitMarker: Regex get() = Regex(Regex.escape("BC_DEBUG_WORLD_EXITED mode=verify run_id=$runId nonce=$nonce " +
        "player=$playerName player_uuid=$playerId world_root=$worldRoot") + "(?:\\r?\\n|$)")
}

private fun canonicalVerifyExitDirectory(path: Path): Path {
    require(path.isAbsolute && path.normalize() == path && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) &&
        path.toRealPath() == path) { "verify exit requires canonical nonsymlink owned directories" }
    var ancestor: Path? = path
    while (ancestor != null) {
        require(!Files.isSymbolicLink(ancestor)) { "verify exit directory ancestry must not contain symlinks" }
        ancestor = ancestor.parent
    }
    return path
}

internal fun fixtureVerifyExitBinding(mode: String, world: String, dedicated: Boolean,
    runId: String, player: String, playerUuid: String, fixture: Path, client: Path, lifecycleDir: Path,
): FixtureVerifyExitBinding? {
    if (mode != "verify" || world != "DebugWorld" || dedicated) return null
    require(runId.matches(Regex("[0-9]{8}T[0-9]{6}Z-[0-9]+")) && player.matches(Regex("[A-Za-z0-9_]{1,16}")))
    require(playerUuid.matches(Regex("[0-9a-f]{32}"))) { "verify exit requires the actual fixture launch UUID" }
    val dashedUuid = "${playerUuid.substring(0, 8)}-${playerUuid.substring(8, 12)}-${playerUuid.substring(12, 16)}-" +
        "${playerUuid.substring(16, 20)}-${playerUuid.substring(20)}"
    val root = canonicalVerifyExitDirectory(fixture.toAbsolutePath())
    val worldRoot = canonicalVerifyExitDirectory(client.toAbsolutePath().resolve("saves/DebugWorld"))
    val dir = canonicalVerifyExitDirectory(lifecycleDir)
    require(root.fileName.toString() == "fixture" && root.parent?.parent?.fileName?.toString() == runId &&
        dir == root.resolve("world-lifecycle") && worldRoot.startsWith(root)) { "verify exit must bind this new owned run fixture" }
    val binding = FixtureVerifyExitBinding(UUID.randomUUID(), runId, player, UUID.fromString(dashedUuid), root, worldRoot, dir)
    require(!Files.exists(binding.requestFile, LinkOption.NOFOLLOW_LINKS)) { "stale verify exit request" }
    require(binding.payload.toByteArray(Charsets.UTF_8).size <= 2048)
    binding.properties // Validate launch encoding before any process starts.
    return binding
}

internal fun writeFixtureVerifyExitRequest(binding: FixtureVerifyExitBinding, verified: Boolean, loaded: Boolean): Path {
    require(verified && loaded) { "normal verify exit requires lifecycle marker/time verification and matching native LOADED" }
    canonicalVerifyExitDirectory(binding.fixtureRoot)
    canonicalVerifyExitDirectory(binding.worldRoot)
    canonicalVerifyExitDirectory(binding.requestDir)
    require(binding.requestDir == binding.fixtureRoot.resolve("world-lifecycle") && binding.worldRoot.startsWith(binding.fixtureRoot))
    val bytes = binding.payload.toByteArray(Charsets.UTF_8)
    require(bytes.size <= 2048)
    Files.write(binding.requestFile, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    return binding.requestFile
}

private const val DEFAULT_CLIENT_JVM_ARGS = "-Xms2G -Xmx12G"

fun promoteSnapshot(source: Path, destination: Path, token: String): String {
    val snapshotId = RuntimeSnapshotValidator.validate(source)
    promoteSnapshotFiles(source, destination, token)
    return snapshotId
}

/** Promotion mechanics separated from snapshot validation for inert restoration tests. */
internal fun promoteSnapshotFiles(source: Path, destination: Path, token: String,
    move: (Path, Path) -> Unit = { from, to -> Files.move(from, to, StandardCopyOption.ATOMIC_MOVE); Unit }) {
    require(Regex("[A-Za-z0-9_.-]+").matches(token)) { "invalid snapshot promotion token" }
    destination.parent.createDirectories()
    val staging = destination.parent.resolve(".runtime-dumps-$token.staging")
    val backup = destination.parent.resolve(".runtime-dumps-$token.previous")
    val restore = destination.parent.resolve(".runtime-dumps-$token.restore")
    require(!staging.exists() && !backup.exists() && !restore.exists()) { "runtime snapshot promotion paths already exist" }
    check(source.toFile().copyRecursively(staging.toFile())) { "incomplete runtime snapshot staging: $staging" }
    val hadPrevious = destination.exists()
    try {
        if (hadPrevious) move(destination, backup)
        move(staging, destination)
    } catch (error: Throwable) {
        if (hadPrevious && backup.exists() && !destination.exists()) {
            try {
                // Restore atomically from a copy so the original diagnostic backup survives.
                check(backup.toFile().copyRecursively(restore.toFile())) { "incomplete snapshot restoration staging: $restore" }
                Files.move(restore, destination, StandardCopyOption.ATOMIC_MOVE)
            } catch (restoration: Throwable) { error.addSuppressed(restoration) }
        }
        // Failed staging, backup and any failed restore staging remain for diagnosis.
        throw error
    }
    // Successful promotion consumes staging by atomic rename; previous snapshots remain.
}
