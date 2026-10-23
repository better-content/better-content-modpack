package com.bettercontent.tests.release

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Timing comparability is deliberately separate from verification eligibility. No environment
 * values or command-line flags are dumped: only reviewed facts and one-way flag digests. */
internal object BuildIsolationTiming {
    private val rootInputs = ConcurrentHashMap<String, String>()
    private val hostFacts by lazy { collectHostFacts() }

    fun digest(values: Map<String, String>): String {
        val hash = MessageDigest.getInstance("SHA-256")
        values.toSortedMap().forEach { (key, value) ->
            listOf(key, value).forEach { text ->
                val bytes = text.toByteArray(Charsets.UTF_8)
                hash.update(bytes.size.toString().toByteArray()); hash.update(0.toByte()); hash.update(bytes)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    fun rootInput(root: Path, run: Path, load: () -> String): String =
        rootInputs.computeIfAbsent("${root.toAbsolutePath().normalize()}\u0000${run.toAbsolutePath().normalize()}") { load() }

    fun environmentFacts(privateHome: Boolean): Map<String, String> = hostFacts + mapOf(
        "home-mode" to if (privateHome) "private-worker-v1" else "legacy-shared-v1",
        "private-jvm-binding" to if (privateHome) "user.home+java.io.tmpdir-v1" else "UNKNOWN",
    )

    fun inputIdentity(root: String, source: String, recipe: String, dependencies: Map<String, String>): String =
        digest(mapOf("schema" to "bc.provider_timing_input.v2", "root" to root,
            "source" to source, "recipe" to recipe) + dependencies.mapKeys { "provider:${it.key}" })

    private fun fileHash(path: Path): String {
        if (!Files.isRegularFile(path)) return "UNKNOWN"
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val bytes = ByteArray(128 * 1024)
            while (true) { val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readFact(path: String): String = runCatching { Files.readString(Path.of(path)).trim() }.getOrDefault("UNKNOWN")

    private fun collectHostFacts(): Map<String, String> {
        val java = Path.of(System.getProperty("java.home"))
        val gradle = System.getProperty("bc.gradle.home")?.let(Path::of)
        val gradleLibraries = if (gradle == null || !Files.isDirectory(gradle.resolve("lib"))) "UNKNOWN" else
            Files.walk(gradle.resolve("lib")).use { paths ->
                digest(paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar") }
                    .toList().associate { gradle.relativize(it).toString() to fileHash(it) })
            }
        return mapOf(
            "schema" to "bc.build_environment.v2",
            "java-version" to System.getProperty("java.version", "UNKNOWN"),
            "java-vendor" to System.getProperty("java.vendor", "UNKNOWN"),
            "java-executable-sha256" to fileHash(java.resolve("bin/java")),
            "java-release-sha256" to fileHash(java.resolve("release")),
            "root-gradle-library-sha256" to gradleLibraries,
            "os" to System.getProperty("os.name", "UNKNOWN"),
            "os-version" to System.getProperty("os.version", "UNKNOWN"),
            "arch" to System.getProperty("os.arch", "UNKNOWN"),
            "processors" to Runtime.getRuntime().availableProcessors().toString(),
            "cpu-model" to readFact("/proc/cpuinfo").lineSequence().firstOrNull {
                it.startsWith("model name") || it.startsWith("Hardware") }?.substringAfter(':')?.trim().orEmpty().ifEmpty { "UNKNOWN" },
            "physical-memory" to readFact("/proc/meminfo").lineSequence().firstOrNull { it.startsWith("MemTotal:") }.orEmpty().ifEmpty { "UNKNOWN" },
            "cgroup-memory-max" to readFact("/sys/fs/cgroup/memory.max"),
            "cgroup-cpu-max" to readFact("/sys/fs/cgroup/cpu.max"),
            // Retain hashes, never raw flags, tokens, credentials or the complete environment.
            "inherited-flags-sha256" to digest(listOf("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS",
                "GRADLE_OPTS", "JAVA_OPTS").associateWith { System.getenv(it) ?: "ABSENT" }),
        )
    }
}

internal data class BuildIsolationCacheObservation(val id: String, val facts: Map<String, String>)
