#!/usr/bin/env kotlin

import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.UUID
import kotlin.system.exitProcess

fun usage(): Nothing {
    System.err.println("usage: ./pack-test-lock.main.kts run <kind> <selector> -- <command> [args...]")
    exitProcess(2)
}

fun json(value: String?): String = value.orEmpty()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")

fun ownerToken(owner: Path): String? = if (!Files.isRegularFile(owner)) null else
    Regex("\\\"token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(Files.readString(owner))?.groupValues?.get(1)

fun writeAtomic(target: Path, content: String) {
    val temporary = target.resolveSibling(".${target.fileName}.${ProcessHandle.current().pid()}.tmp")
    Files.writeString(temporary, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)
    try {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
    }
}

if (args.size < 5 || args[0] != "run" || args[3] != "--") usage()
val kind = args[1]
val selector = args[2]
val command = args.drop(4)
if (kind !in setOf("test", "release") || command.isEmpty()) usage()

val stateRoot = System.getenv("BC_PACK_TEST_STATE_ROOT")?.takeIf(String::isNotBlank)?.let(Path::of)
    ?: Path.of(System.getProperty("user.home"), ".local/share/worklane/pack-tests")
Files.createDirectories(stateRoot)
val lockPath = stateRoot.resolve("runner.lock")
val ownerPath = stateRoot.resolve("owner.json")
val inherited = System.getenv("BC_PACK_TEST_LOCK_TOKEN")?.takeIf(String::isNotBlank)

fun runChild(token: String): Int {
    val builder = ProcessBuilder(command).inheritIO()
    builder.environment()["BC_PACK_TEST_LOCK_TOKEN"] = token
    builder.environment()["BC_PACK_TEST_LOCK_OWNER"] = ownerPath.toString()
    return builder.start().waitFor()
}

if (inherited != null) {
    if (ownerToken(ownerPath) != inherited) {
        System.err.println("pack-test lock token does not match the active owner: $ownerPath")
        exitProcess(75)
    }
    exitProcess(runChild(inherited))
}

val channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
val lock = try {
    channel.tryLock()
} catch (_: OverlappingFileLockException) {
    null
}
val heldLock = lock ?: run {
    val owner = if (Files.isRegularFile(ownerPath)) Files.readString(ownerPath).trim() else "owner metadata unavailable"
    System.err.println("pack-test runner is busy: $owner")
    channel.close()
    exitProcess(75)
}

val token = UUID.randomUUID().toString()
val pid = ProcessHandle.current().pid()
val owner = """{
  "schema": "bc.pack_test_owner.v1",
  "token": "${json(token)}",
  "pid": $pid,
  "started_at": "${Instant.now()}",
  "kind": "${json(kind)}",
  "selector": "${json(selector)}",
  "agent": "${json(System.getenv("HERDR_AGENT_NAME") ?: System.getenv("CODEX_AGENT_NAME"))}",
  "herdr_session": "${json(System.getenv("HERDR_SESSION"))}",
  "herdr_pane_id": "${json(System.getenv("HERDR_PANE_ID"))}",
  "handoff": "${json(System.getenv("BC_PACK_TEST_HANDOFF"))}",
  "client_sha256": "${json(System.getenv("BC_TEST_CLIENT_SHA256"))}",
  "server_sha256": "${json(System.getenv("BC_TEST_SERVER_SHA256"))}",
  "command": "${json(command.joinToString(" "))}"
}
"""
writeAtomic(ownerPath, owner)

var cleaned = false
fun cleanup() {
    if (cleaned) return
    cleaned = true
    runCatching {
        if (ownerToken(ownerPath) == token) Files.deleteIfExists(ownerPath)
    }
    runCatching { heldLock.release() }
    runCatching { channel.close() }
}
Runtime.getRuntime().addShutdownHook(Thread(::cleanup, "pack-test-lock-cleanup"))
val status = try {
    runChild(token)
} finally {
    cleanup()
}
exitProcess(status)
