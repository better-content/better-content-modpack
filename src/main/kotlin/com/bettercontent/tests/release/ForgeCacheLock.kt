package com.bettercontent.tests.release

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

// ForgeGradle's generated MCP/mapped JARs are shared across separate Gradle builds,
// but their transformers do not atomically publish or lock those outputs. Protect
// the entire source Gradle invocation, including readers of the generated JARs.
// The lock belongs to the pack repository, NOT its polyrepo workspace parent.
internal fun <T> withForgeCacheLock(packRoot: Path, action: () -> T): T =
    withBuildResourceLock(packRoot.resolve(".worklane/forge-cache.lock"), action)

private val buildResourceMutexes = ConcurrentHashMap<Path, ReentrantLock>()

/** Same JVM and POSIX locks: FileChannel alone throws on overlapping JVM acquisitions.
 * These locks narrow ownership only for genuinely separate mutable cache/workspace trees. */
internal fun <T> withBuildResourceLock(path: Path, action: () -> T): T {
    val normalized = path.toAbsolutePath().normalize()
    requireBuildIsolationPath(normalized)
    val mutex = buildResourceMutexes.computeIfAbsent(normalized) { ReentrantLock(true) }
    mutex.lockInterruptibly()
    try {
        Files.createDirectories(normalized.parent)
        FileChannel.open(normalized, CREATE, WRITE, NOFOLLOW_LINKS).use { channel ->
            channel.lock().use { return action() }
        }
    } finally { mutex.unlock() }
}
