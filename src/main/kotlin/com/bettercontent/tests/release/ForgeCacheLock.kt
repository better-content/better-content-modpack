package com.bettercontent.tests.release

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// ForgeGradle's generated MCP/mapped JARs are shared across separate Gradle builds,
// but their transformers do not atomically publish or lock those outputs. Protect
// the entire source Gradle invocation, including readers of the generated JARs.
private val sourceGradleMutex = ReentrantLock(true)

internal fun <T> withForgeCacheLock(workspace: Path, action: () -> T): T = sourceGradleMutex.withLock {
    val state = workspace.resolve(".worklane")
    require(!Files.isSymbolicLink(state)) { "unsafe Forge cache lock directory" }
    Files.createDirectories(state)
    FileChannel.open(state.resolve("forge-cache.lock"), CREATE, WRITE, NOFOLLOW_LINKS).use { channel ->
        channel.lock().use { action() }
    }
}
