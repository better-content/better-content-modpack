package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

/** Explicit opt-in only; the old runtime tiers construct PackSession without this profile. */
internal data class SuiteInstrumentation(
    val supportJar: Path,
    val supportSha256: String,
    val javaArguments: List<String>,
    val clientRequired: Boolean,
    /** Only the client epoch that actually exports EMI may declare this exact candidate hash. */
    val expectedEmiSha256: String? = null,
    val clientInstrumentation: Boolean = false,
) {
    init {
        val sha = Regex("[0-9a-f]{64}")
        require(supportSha256.matches(sha))
        require(!clientInstrumentation || clientRequired)
        require(expectedEmiSha256 == null || (clientInstrumentation && expectedEmiSha256.matches(sha)))
        val properties = javaArguments.map { argument ->
            require(argument.startsWith("-Dbc.playtest.") && argument.none { it == '\n' || it == '\r' || it == '\u0000' }) {
                "invalid test support JVM property"
            }
            val separator = argument.indexOf('=')
            require(separator > 2 && separator < argument.lastIndex) { "malformed test support JVM property" }
            argument.substring(2, separator) to argument.substring(separator + 1)
        }
        val required = setOf("bc.playtest.directory", "bc.playtest.runId", "bc.playtest.token")
        val optional = if (expectedEmiSha256 != null) setOf("bc.playtest.emiSha256") else emptySet()
        require(properties.map { it.first }.toSet() == required + optional &&
            properties.size == required.size + optional.size) { "duplicate, missing or undeclared test support JVM property" }
        val values = properties.toMap()
        val directory = Path.of(values.getValue("bc.playtest.directory"))
        require(directory.isAbsolute && directory.normalize() == directory && !directory.toString().contains('"')) {
            "invalid test support mailbox directory"
        }
        require(values.getValue("bc.playtest.runId").matches(Regex("[a-z0-9][a-z0-9_.-]*")) &&
            values.getValue("bc.playtest.token").matches(sha)) { "invalid test support mailbox identity" }
        if (expectedEmiSha256 != null) require(values.getValue("bc.playtest.emiSha256") == expectedEmiSha256) {
            "EMI property does not bind the exact candidate jar"
        }
        require(Hashes.sha256(supportJar) == supportSha256) { "test support changed" }
        ZipFile(supportJar.toFile()).use { archive ->
            require(archive.getEntry("META-INF/mods.toml") != null && archive.getEntry("META-INF/bc-playtest-support.json") != null) {
                "test support lacks Forge metadata/capability descriptor"
            }
        }
    }
    fun install(serverRoot: Path, restored: Boolean = false) {
        require(Hashes.sha256(supportJar) == supportSha256) { "test support changed before installation" }
        val destination = serverRoot.resolve("mods/bc-playtest-support.jar")
        if (restored) require(Files.isRegularFile(destination) && !Files.isSymbolicLink(destination)) { "restored support missing/unsafe" }
        else {
            require(!Files.exists(destination)) { "support already present in candidate" }
            Files.copy(supportJar, destination)
        }
        require(Hashes.sha256(destination) == supportSha256)
    }
    fun jvmText(ownedRuntime: Path? = null): String {
        val stateArguments = ownedRuntime?.let {
            listOf("-Duser.home=${it.resolve(".suite-home")}", "-Djava.io.tmpdir=${it.resolve(".suite-tmp")}")
        }.orEmpty()
        return "-Xms4G\n-Xmx16G\n-XX:+UseG1GC\n-Dfile.encoding=UTF-8\n" +
            "-Xlog:gc*:file=campaign-gc.log:time,uptime,level,tags:filecount=3,filesize=10m\n" +
            (javaArguments + stateArguments).joinToString("\n", postfix = "\n") {
                require('\n' !in it && '\r' !in it && '\u0000' !in it)
                "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            }
    }
}

/** V3 keeps shipped graphics/generation settings; only the agreed distances are overridden. */
internal fun suiteClientOptions(text: String, guiScale: Int = 2): String {
    require(guiScale in setOf(2, 3))
    return text.lineSequence().filter {
        it.isNotEmpty() && it.substringBefore(':') !in setOf("renderDistance", "simulationDistance", "guiScale")
    }.joinToString("\n", postfix = "\nrenderDistance:10\nsimulationDistance:10\nguiScale:$guiScale\n")
}

/** Only a checkpoint-verified, fresh epoch may supply restored runtime roots. */
internal data class SuiteEpochRuntime(val directory: Path, val launcherWheel: Path,
    val launcherSha256: String, val sandboxExecutable: Path, val sandboxSha256: String,
    val restoredRoots: Map<String, Path>? = null, val visualProfile: String = "1280x720@2") {
    val resolution: String get() = visualProfile.substringBefore('@')
    val guiScale: Int get() = visualProfile.substringAfter('@').toInt()
    init {
        require(directory.isAbsolute && directory.normalize() == directory)
        require(visualProfile in setOf("1280x720@2", "1920x1080@3"))
        require(Hashes.sha256(launcherWheel) == launcherSha256)
        require(Files.isExecutable(sandboxExecutable) && Hashes.sha256(sandboxExecutable) == sandboxSha256) { "hash-bound bubblewrap executable required" }
        ZipFile(launcherWheel.toFile()).use { archive ->
            require(archive.getEntry("portablemc/cli/__init__.py") != null && archive.getEntry("portablemc/__init__.py") != null) {
                "a hash-bound PortableMC wheel (not mutable pipx launcher state) is required"
            }
            val metadata = archive.getEntry("portablemc-4.4.1.dist-info/METADATA") ?: error("PortableMC 4.4.1 wheel metadata missing")
            val text = archive.getInputStream(metadata).bufferedReader().use { it.readText() }
            require(text.lineSequence().none { it.startsWith("Requires-Dist:") && "extra ==" !in it }) {
                "unbound launcher dependencies are unsupported"
            }
        }
        require(restoredRoots == null || restoredRoots.keys == setOf("server", "client"))
        restoredRoots?.values?.forEach { require(it.isAbsolute && it.normalize().startsWith(directory) && !Files.isSymbolicLink(it)) }
    }

    /** Kernel-enforced write ownership: HOME overrides alone cannot contain provider-specific paths. */
    fun command(arguments: List<String>, cwd: Path, evidence: Path, stateRoot: Path, nativeDirectory: Path): List<String> {
        require(Hashes.sha256(sandboxExecutable) == sandboxSha256) { "sandbox tool changed before launch" }
        require(Files.isDirectory(directory) && Files.isDirectory(evidence) &&
            stateRoot.toRealPath().startsWith(directory) && nativeDirectory.parent == evidence &&
            nativeDirectory.fileName.toString() in setOf("native", "client-native") &&
            Files.isDirectory(nativeDirectory.resolve("requests")) &&
            (cwd.normalize().startsWith(directory) || cwd.normalize().startsWith(evidence)))
        // The parent runtime directory contains the controller's immutable restore audit. It stays read-only.
        val immutable = listOf(stateRoot.resolve("mods"), nativeDirectory.resolve("requests"), nativeDirectory.resolve("requests.jsonl")) +
            listOf(stateRoot.resolve(".suite-launcher.whl")).filter(Files::isRegularFile)
        return listOf(sandboxExecutable.toString(), "--die-with-parent", "--new-session", "--unshare-user", "--unshare-pid", "--unshare-ipc",
            "--ro-bind", "/", "/", "--proc", "/proc", "--dev", "/dev", "--bind", stateRoot.toString(), stateRoot.toString(),
            "--bind", nativeDirectory.toString(), nativeDirectory.toString()) +
            immutable.flatMap { listOf("--ro-bind", it.toString(), it.toString()) } +
            listOf("--chdir", cwd.toString(), "--") + arguments
    }
}
