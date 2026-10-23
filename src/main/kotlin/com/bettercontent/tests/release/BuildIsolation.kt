package com.bettercontent.tests.release

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue

internal fun requireBuildIsolationPath(path: Path) {
    var current: Path? = path.toAbsolutePath().normalize()
    while (current != null) {
        require(!Files.isSymbolicLink(current)) { "isolated build path contains a symlink: $current" }
        current = current.parent
    }
}

internal data class BuildIsolationContext(val directory: Path, val seedSha256: String = "UNKNOWN") {
    val home: Path = directory.resolve("home")
    val gradleHome: Path = home.resolve(".gradle")
    val temporary: Path = directory.resolve("tmp")
    private var invocations = 0L
    private var history = BuildIsolationTiming.digest(mapOf("seed" to seedSha256, "history" to "empty"))

    // Accessed only while the pool owns this worker's serial resource lock. This describes
    // known seed/history conditions, NOT a byte hash or proof of generated-cache equality.
    fun cacheObservation(): BuildIsolationCacheObservation {
        val facts = mapOf("schema" to "bc.worker_cache_condition.v2", "raw-seed-sha256" to seedSha256,
            "generated-state" to if (invocations == 0L) "fresh-private-generated-cache" else "serially-reused-private-cache",
            "prior-invocations" to invocations.toString(), "prior-history-sha256" to history,
            "generated-bytes" to "UNMEASURED")
        return BuildIsolationCacheObservation(if (seedSha256 == "UNKNOWN") "UNKNOWN" else BuildIsolationTiming.digest(facts), facts)
    }

    fun recordInvocation(input: String, environment: String, selector: String, outcome: String) {
        val observation = cacheObservation()
        history = BuildIsolationTiming.digest(mapOf("prior" to history, "input" to input,
            "environment" to environment, "selector" to selector, "outcome" to outcome))
        val ordinal = invocations++
        Files.writeString(directory.resolve("cache-history.tsv"),
            "$ordinal\t${observation.id}\t$input\t$environment\t${BuildIsolationTiming.digest(mapOf("selector" to selector))}\t$outcome\t$history\n",
            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
    }

    fun environment(): Map<String, String> = mapOf(
        "HOME" to home.toString(), "GRADLE_USER_HOME" to gradleHome.toString(),
        "XDG_CACHE_HOME" to home.resolve(".cache").toString(),
        "XDG_CONFIG_HOME" to home.resolve(".config").toString(),
        "XDG_DATA_HOME" to home.resolve(".local/share").toString(),
        "TMPDIR" to temporary.toString(), "TMP" to temporary.toString(), "TEMP" to temporary.toString(),
        "BC_PACKAGE_ARTIFACT_CACHE" to home.resolve(".cache/bc/packwiz-downloads").toString(),
        // Applied to Gradle's single-use daemon and native child JVMs too. The wrapper's
        // HOME alone does not change Java user.home (nor Forge's independently chosen cache).
        "JAVA_TOOL_OPTIONS" to ((System.getenv("JAVA_TOOL_OPTIONS") ?: "") +
            " -Duser.home=$home -Djava.io.tmpdir=$temporary").trim(),
        "BC_BUILD_ISOLATION" to directory.toString(),
    )

    fun command(repository: Path, command: List<String>): List<String> = command.take(1) + listOf(
        "--gradle-user-home", gradleHome.toString(),
        "--project-cache-dir", directory.resolve("projects/${repository.fileName}").toString(),
        "-Duser.home=$home", "-Djava.io.tmpdir=$temporary",
    ) + command.drop(1)
}

/** Run-local, serially reused worker homes. Only immutable dependency downloads/wrapper
 * distributions are seeded. In particular NO Forge deobf/MCP cache, transforms, daemon state,
 * compiled scripts or project caches are copied: those may embed absolute mutable aliases.
 * No cleanup deletes this run's partial or complete resources. */
internal class BuildIsolationPool(
    private val packRoot: Path,
    private val directory: Path,
    private val workers: Int,
    private val sharedGradleHome: Path = Path.of(System.getenv("GRADLE_USER_HOME")
        ?: Path.of(System.getProperty("user.home"), ".gradle").toString()),
) {
    private val slots = ArrayBlockingQueue<BuildIsolationContext>(workers)
    private var initialized = false
    private val seedPaths = listOf("caches/modules-2/files-2.1", "wrapper/dists")

    init {
        require(workers in 1..4)
        require(!directory.toAbsolutePath().normalize().toString().any { it.isWhitespace() || it == '"' || it == '\'' }) {
            "build isolation JVM option paths must not contain whitespace or quotes"
        }
    }

    @Synchronized private fun initialize() {
        if (initialized) return
        requireBuildIsolationPath(directory)
        require(!Files.exists(directory, NOFOLLOW_LINKS)) { "build isolation run directory already exists: $directory" }
        Files.createDirectories(directory)
        val seed = directory.resolve("seed")
        val deadline = System.nanoTime() + Duration.ofMinutes(10).toNanos()
        var seedSha256 = "UNKNOWN"
        withForgeCacheLock(packRoot) {
            val bytes = seedPaths.sumOf { relative -> treeBytes(sharedGradleHome.resolve(relative), deadline) }
            // Seed + each worker copy + bounded generation headroom. This is filesystem
            // feasibility, not a claim about a separately imposed lane quota.
            val required = Math.addExact(Math.multiplyExact(bytes, (workers + 1).toLong()), workers * 4L * 1024 * 1024 * 1024)
            require(Files.getFileStore(directory).usableSpace >= required) {
                "insufficient filesystem headroom for $workers isolated homes: require $required bytes"
            }
            Files.createDirectories(seed)
            val identity = MessageDigest.getInstance("SHA-256")
            seedPaths.forEach { relative ->
                identity.update((relative + "\u0000").toByteArray())
                copyTree(sharedGradleHome.resolve(relative), seed.resolve(relative), deadline, identity)
            }
            seedSha256 = identity.digest().joinToString("") { "%02x".format(it) }
            Files.writeString(directory.resolve("seed-identity.tsv"),
                "schema\tbc.build_isolation_seed.v1\nsource\t${sharedGradleHome.toAbsolutePath().normalize()}\n" +
                "paths\t${seedPaths.joinToString(",")}\nbytes\t$bytes\nworkers\t$workers\n" +
                "sha256\t$seedSha256\n")
        }
        // The seed is now exclusively owned by this pool and never mutated again.
        repeat(workers) { index ->
            val context = BuildIsolationContext(directory.resolve("worker-$index").toAbsolutePath().normalize(), seedSha256)
            Files.createDirectories(context.temporary)
            copyTree(seed, context.gradleHome, System.nanoTime() + Duration.ofMinutes(10).toNanos())
            slots.add(context)
        }
        Files.writeString(directory.resolve("ready"), "workers=$workers\n")
        initialized = true
    }

    fun prepare() = initialize()

    fun <T> withWorker(repository: Path, action: (BuildIsolationContext?) -> T): T {
        // Scripts with absolute legacy cache paths cannot safely run against private caches.
        // Preserve the whole-invocation global lock rather than silently sharing deobf state.
        if (requiresSharedCache(repository)) return withForgeCacheLock(packRoot) {
            withBuildResourceLock(repository.resolve(".gradle/bc-source-workspace.lock")) { action(null) }
        }
        initialize()
        val context = slots.take()
        try {
            return withBuildResourceLock(context.directory.resolve("cache.lock")) {
                withBuildResourceLock(repository.resolve(".gradle/bc-source-workspace.lock")) { action(context) }
            }
        } finally { check(slots.offer(context)) { "isolated worker returned twice" } }
    }

    companion object {
        internal fun requiresSharedCache(repository: Path): Boolean = sharedCacheReasons(repository).isNotEmpty()

        internal fun sharedCacheReasons(repository: Path): List<String> {
            val candidates = listOf("scripts", "gradle", "build.gradle", "build.gradle.kts", "settings.gradle",
                "settings.gradle.kts", "gradle.properties").map(repository::resolve)
            return candidates.flatMap { root ->
                if (!Files.exists(root, NOFOLLOW_LINKS)) emptyList() else Files.walk(root).use { paths ->
                    paths.map { path ->
                        when {
                            Files.isSymbolicLink(path) -> "${repository.relativize(path)}:symlink"
                            Files.isRegularFile(path, NOFOLLOW_LINKS) &&
                                path.fileName.toString().endsWithAny(".py", ".gradle", ".kts", ".sh", ".properties") &&
                                Files.readString(path).contains(Regex("/home/[^/\\s'\"]+/(?:\\.(?:gradle|cache)(?:/|['\"]|$)|workspace_artifacts/)")) ->
                                "${repository.relativize(path)}:absolute-shared-cache-or-output"
                            else -> null
                        }
                    }.filter { it != null }.map { it!! }.toList()
                }
            }.sorted()
        }

        private fun String.endsWithAny(vararg suffixes: String) = suffixes.any(::endsWith)

        private fun checkBudget(deadline: Long) {
            check(!Thread.currentThread().isInterrupted) { "build cache seed cancelled" }
            check(System.nanoTime() < deadline) { "build cache seed exceeded bounded copy budget" }
        }

        private fun treeBytes(source: Path, deadline: Long): Long {
            requireBuildIsolationPath(source)
            if (!Files.exists(source, NOFOLLOW_LINKS)) return 0
            return Files.walk(source).use { paths -> paths.mapToLong { path ->
                checkBudget(deadline)
                require(!Files.isSymbolicLink(path)) { "cache seed symlink refused: $path" }
                if (Files.isRegularFile(path, NOFOLLOW_LINKS) && !isLock(path)) Files.size(path) else 0
            }.sum() }
        }

        private fun isLock(path: Path) = path.fileName.toString().let { it.endsWith(".lock") || it.endsWith(".lck") }

        internal fun copyTree(source: Path, target: Path, deadline: Long, digest: MessageDigest? = null) {
            requireBuildIsolationPath(source)
            requireBuildIsolationPath(target)
            Files.createDirectories(target)
            if (!Files.exists(source, NOFOLLOW_LINKS)) return
            Files.walk(source).use { paths -> paths.sorted().forEach { path ->
                checkBudget(deadline)
                require(!Files.isSymbolicLink(path)) { "cache seed symlink refused: $path" }
                val destination = target.resolve(source.relativize(path).toString())
                if (Files.isDirectory(path, NOFOLLOW_LINKS)) Files.createDirectories(destination)
                else if (!isLock(path)) {
                    require(Files.isRegularFile(path, NOFOLLOW_LINKS)) { "cache seed special file refused: $path" }
                    val relative = source.relativize(path).toString().toByteArray()
                    digest?.update(relative.size.toString().toByteArray())
                    digest?.update(0.toByte())
                    digest?.update(relative)
                    digest?.update(Files.size(path).toString().toByteArray())
                    digest?.update(0.toByte())
                    Files.newInputStream(path).use { input -> Files.newOutputStream(destination).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            checkBudget(deadline)
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest?.update(buffer, 0, count)
                        }
                    } }
                    if (Files.isExecutable(path)) check(destination.toFile().setExecutable(true)) { "cannot preserve seed executable" }
                }
            } }
        }
    }
}
