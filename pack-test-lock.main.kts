#!/usr/bin/env kotlin
@file:Import("scripts/BoundedFacade.main.kts")

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
var component = stateRoot.toAbsolutePath().normalize().root
for (part in stateRoot.toAbsolutePath().normalize()) {
    component = component.resolve(part)
    require(!Files.isSymbolicLink(component)) { "unsafe runner state path: $component" }
}
Files.createDirectories(stateRoot)
val lockPath = stateRoot.resolve("runner.lock")
val ownerPath = stateRoot.resolve("owner.json")
require(!Files.isSymbolicLink(lockPath) && !Files.isSymbolicLink(ownerPath)) { "unsafe runner lock/owner path" }
val inherited = System.getenv("BC_PACK_TEST_LOCK_TOKEN")?.takeIf(String::isNotBlank)

@Volatile var childSupervisor: Process? = null
@Volatile var childCleanupUnproved = true
val cleanupMutex = Any()
fun runChild(token: String): Int {
    childCleanupUnproved = true
    val status = superviseFacade(__FILE__.canonicalFile.parentFile, command,
        cwd = java.io.File(System.getProperty("user.dir")),
        environment = mapOf("BC_PACK_TEST_LOCK_TOKEN" to token, "BC_PACK_TEST_LOCK_OWNER" to ownerPath.toString()),
        onStarted = { childSupervisor = it })
    childCleanupUnproved = status == 125
    return status
}

if (inherited != null) {
    val verified = superviseFacade(__FILE__.canonicalFile.parentFile,
        listOf("python3", "-B", __FILE__.canonicalFile.parentFile.resolve("scripts/runner_lock.py").absolutePath), budgetSeconds = 30)
    if (verified != 0 || ownerToken(ownerPath) != inherited) {
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

require(!Files.exists(ownerPath)) { "prior runner owner metadata remains; reconcile its owned-process cleanup before starting another candidate" }
val token = UUID.randomUUID().toString()
val pid = ProcessHandle.current().pid()
val owner = """{
  "schema": "bc.pack_test_owner.v1",
  "token": "${json(token)}",
  "pid": $pid,
  "started_at": "${Instant.now()}",
  "kind": "${json(kind)}",
  "selector": "${json(selector)}",
  "owner_session": "${json(System.getenv("BC_OWNER_SESSION") ?: System.getenv("PI_SESSION_ID"))}",
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
fun cleanup() = synchronized(cleanupMutex) {
    if (!cleaned) {
        cleaned = true
        var complete = !childCleanupUnproved
        childSupervisor?.let { child ->
            child.destroy()
            complete = runCatching { child.waitFor(12, java.util.concurrent.TimeUnit.SECONDS) && child.exitValue() != 125 }.getOrDefault(false)
            if (!complete) System.err.println("pack-test cancellation cleanup unproved; preserve owner metadata for reconciliation")
        }
        if (complete) runCatching { if (ownerToken(ownerPath) == token) Files.deleteIfExists(ownerPath) }
        runCatching { heldLock.release() }
        runCatching { channel.close() }
    }
}
Runtime.getRuntime().addShutdownHook(Thread(::cleanup, "pack-test-lock-cleanup"))
val status = try {
    runChild(token)
} finally {
    cleanup()
}
exitProcess(status)
