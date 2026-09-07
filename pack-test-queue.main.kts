#!/usr/bin/env kotlin

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.isRegularFile
import kotlin.system.exitProcess

fun usage(): Nothing {
    System.err.println("usage: ./pack-test-queue.main.kts <request HANDOFF.json|status|run-next>")
    exitProcess(2)
}

val root = __FILE__.canonicalFile.parentFile.toPath()
val stateRoot = System.getenv("BC_PACK_TEST_STATE_ROOT")?.takeIf(String::isNotBlank)?.let(Path::of)
    ?: Path.of(System.getProperty("user.home"), ".local/share/worklane/pack-tests")
val directories = listOf("queued", "running", "completed", "failed").associateWith {
    stateRoot.resolve(it).also(Files::createDirectories)
}

fun jq(path: Path, expression: String): String {
    val process = ProcessBuilder("jq", "-er", expression, path.toString()).redirectError(ProcessBuilder.Redirect.INHERIT).start()
    val output = process.inputStream.bufferedReader().readText().trim()
    require(process.waitFor() == 0) { "invalid pack-test handoff: $path" }
    return output
}

fun manifests(directory: Path): List<Path> = Files.list(directory).use { stream ->
    stream.filter { it.isRegularFile() && it.fileName.toString().endsWith(".json") }.sorted().toList()
}

when (args.firstOrNull()) {
    "request" -> {
        if (args.size != 2) usage()
        val source = Path.of(args[1]).toAbsolutePath().normalize()
        require(source.isRegularFile()) { "handoff does not exist: $source" }
        jq(source, "select(.schema == \"bc.pack_test_handoff.v1\") | select(.producer.agent == \"workspace_coord\") | select(.authorization.explicit == true) | select(.request_id | type == \"string\" and length > 0) | select(.callback.agent and .callback.pane_id) | select(.modpack.head and (.modpack.status | type == \"array\")) | select((.repositories | type == \"array\") and (.validations | type == \"array\") and (.artifacts | type == \"array\") and (.dependencies | type == \"array\") and (.scenarios | type == \"array\") and (.prior_evidence | type == \"array\")) | select(.selector == \"candidate\" or .selector == \"server\" or .selector == \"multiplayer\" or .selector == \"singleplayer\" or .selector == \"all\") | select(.candidate.client.path and .candidate.client.sha256 and .candidate.server.path and .candidate.server.sha256) | .request_id")
        val requestId = jq(source, ".request_id")
        val all = directories.values.flatMap(::manifests)
        require(all.none { runCatching { jq(it, ".request_id") }.getOrNull() == requestId }) { "request is already registered: $requestId" }
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC).format(Instant.now())
        val safeId = requestId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = directories.getValue("queued").resolve("$stamp-$safeId.json")
        val temporary = target.resolveSibling(".${target.fileName}.tmp")
        Files.copy(source, temporary)
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
        println("queued: $requestId")
        println("handoff: $target")
    }
    "status" -> {
        if (args.size != 1) usage()
        directories.forEach { (state, directory) ->
            manifests(directory).forEach { path ->
                println(listOf(state, jq(path, ".request_id"), jq(path, ".selector"), path.toString()).joinToString("\t"))
            }
        }
    }
    "run-next" -> {
        if (args.size != 1) usage()
        val queued = manifests(directories.getValue("queued")).firstOrNull()
            ?: error("pack-test queue is empty")
        val running = directories.getValue("running").resolve(queued.fileName)
        Files.move(queued, running, StandardCopyOption.ATOMIC_MOVE)
        val selector = jq(running, ".selector")
        val builder = ProcessBuilder(root.resolve("test.main.kts").toString(), selector).directory(root.toFile()).inheritIO()
        builder.environment()["BC_PACK_TEST_HANDOFF"] = running.toString()
        builder.environment()["BC_PACK_TEST_STATE_ROOT"] = stateRoot.toString()
        builder.environment()["BC_TEST_CLIENT_SHA256"] = jq(running, ".candidate.client.sha256")
        builder.environment()["BC_TEST_SERVER_SHA256"] = jq(running, ".candidate.server.sha256")
        val status = builder.start().waitFor()
        if (status == 75) {
            Files.move(running, queued, StandardCopyOption.ATOMIC_MOVE)
            println("request: ${jq(queued, ".request_id")}")
            println("state: queued (runner busy)")
            println("handoff: $queued")
            exitProcess(status)
        }
        val destination = directories.getValue(if (status == 0) "completed" else "failed").resolve(running.fileName)
        Files.move(running, destination, StandardCopyOption.ATOMIC_MOVE)
        println("request: ${jq(destination, ".request_id")}")
        println("state: ${if (status == 0) "completed" else "failed"}")
        println("handoff: $destination")
        exitProcess(status)
    }
    else -> usage()
}
