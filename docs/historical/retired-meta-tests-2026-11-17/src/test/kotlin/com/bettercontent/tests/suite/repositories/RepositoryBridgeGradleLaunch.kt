package com.bettercontent.tests.suite.repositories

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.suiteMapper
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** TEST FIXTURE ONLY: independently pinned Gradle 8.8/JDK 17 Linux JVM entry point.
 * Does not establish shell-wrapper identity capture or production RepositoryRunner coverage.
 * The real Gradle daemon/leaf worker, fork policy and strict ownership verifier are unchanged.
 */
internal object RepositoryBridgeGradleLaunch {
    internal fun verifyPin(file: Path, expected: String?): BoundFile {
        require(expected != null && expected.matches(Regex("[0-9a-f]{64}"))) { "explicit tool SHA-256 pin required" }
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) && file == file.toRealPath()) {
            "pinned tool must be an ordinary canonical file"
        }
        require(Hashes.sha256(file) == expected) { "pinned tool bytes changed" }
        return BoundFile(file.toString(), expected)
    }

    fun command(script: Path, java: Path, evidence: Path): List<String> {
        require(Runtime.version().feature() == 17 && System.getProperty("os.name") == "Linux") {
            "only the reviewed JDK 17 Linux direct-launch fixture profile is supported"
        }
        for (name in listOf("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "JAVA_OPTS", "GRADLE_OPTS")) {
            require(System.getenv(name) == null) { "unreviewed launcher environment: $name" }
        }
        require(script == script.toRealPath() && script.fileName.toString() == "gradle" &&
            java == Path.of(System.getProperty("java.home")).toRealPath().resolve("bin/java").toRealPath()) {
            "direct-launch fixture requires the pinned canonical distribution and current JDK executable"
        }
        // The reviewed POSIX script raises its soft descriptor limit to the hard limit.
        // Our enclosing Gradle launcher already did so; do not silently change this setup.
        val descriptorLimit = Files.readAllLines(Path.of("/proc/self/limits"))
            .single { it.startsWith("Max open files") }
        val limits = descriptorLimit.trim().split(Regex("\\s+"))
        require(limits.size == 6 && limits[3] == limits[4]) { "direct-launch descriptor-limit profile differs from script" }
        val home = script.parent.parent
        val launcher = home.resolve("lib/gradle-launcher-8.8.jar")
        val agent = home.resolve("lib/agents/gradle-instrumentation-agent-8.8.jar")
        val inputs = linkedMapOf(
            "script" to verifyPin(script, System.getenv("BC_REPOSITORY_WORKER_GRADLE_SHA256")),
            "java" to verifyPin(java, System.getenv("BC_REPOSITORY_WORKER_JAVA_SHA256")),
            "launcher" to verifyPin(launcher, System.getenv("BC_REPOSITORY_WORKER_GRADLE_LAUNCHER_SHA256")),
            "instrumentation_agent" to verifyPin(agent, System.getenv("BC_REPOSITORY_WORKER_GRADLE_AGENT_SHA256")))
        val command = listOf(java.toString(), "-Xmx64m", "-Xms64m", "-javaagent:$agent",
            "-Dorg.gradle.appname=gradle", "-classpath", launcher.toString(), "org.gradle.launcher.GradleMain")
        Files.write(evidence.resolve("direct-gradle-launch.json"), suiteMapper.writeValueAsBytes(linkedMapOf(
            "profile" to "gradle-8.8-jdk17-linux-direct-jvm-fixture-only",
            "wrapper_shell_coverage" to false, "production_repository_runner_coverage" to false,
            "inputs" to inputs, "command_prefix" to command, "descriptor_limit" to descriptorLimit)), StandardOpenOption.CREATE_NEW)
        return command
    }
}
