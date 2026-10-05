#!/usr/bin/env kotlin

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

private val root = __FILE__.canonicalFile.parentFile.toPath()
private val workspace = root.parent
private val schema = "bc.test_control.v1"

private fun json(value: Any?): String = when (value) {
    null -> "null"
    is String -> "\"" + value.flatMap { char -> when (char) {
        '\\' -> "\\\\".toList()
        '"' -> "\\\"".toList()
        '\n' -> "\\n".toList()
        '\r' -> "\\r".toList()
        '\t' -> "\\t".toList()
        else -> listOf(char)
    } }.joinToString("") + "\""
    is Boolean, is Number -> value.toString()
    is Map<*, *> -> value.entries.joinToString(",", "{", "}") { json(it.key.toString()) + ":" + json(it.value) }
    is Iterable<*> -> value.joinToString(",", "[", "]") { json(it) }
    else -> json(value.toString())
}

private fun reply(fields: Map<String, Any?>) = println(json(linkedMapOf("schema" to schema) + fields))

private fun fail(message: String): Nothing {
    reply(mapOf("state" to "invalid", "error" to message))
    exitProcess(2)
}

private fun options(start: Int, allowed: Set<String>): Map<String, String> {
    val result = linkedMapOf<String, String>()
    var index = start
    while (index < args.size) {
        val key = args[index]
        if (key !in allowed || key in result) fail("invalid or repeated option: $key")
        if (key == "--all" || key == "--existing-candidate") {
            result[key] = "true"
            index++
        } else {
            result[key] = args.getOrNull(index + 1) ?: fail("$key needs a value")
            index += 2
        }
    }
    return result
}

private fun requestOptions(scope: String, start: Int): Map<String, String> {
    if (scope !in setOf("dev", "dist", "debug", "source")) fail("unknown scope: $scope")
    val source = scope == "source"
    val result = options(start, if (source) setOf("--repo", "--all")
        else setOf("--target", "--retry-of", "--existing-candidate"))
    if (source) {
        if ((result["--repo"] != null) == (result["--all"] == "true"))
            fail("source needs exactly one of --repo or --all")
        result["--repo"]?.let { if (it !in allSourceNames()) fail("unknown source repository: $it") }
    } else {
        val target = result["--target"]
        if (result["--retry-of"] != null && target == null) fail("--retry-of needs --target")
        if (result["--existing-candidate"] == "true" && (scope != "debug" || target != null))
            fail("--existing-candidate needs full debug")
        if (scope == "dev" && result.isNotEmpty()) fail("dev accepts no options")
    }
    return result
}

private data class SourceMod(val repository: String, val artifact: String, val tasks: List<String>, val dependencies: List<String>)

private fun capture(command: List<String>, cwd: Path = root): Pair<Int, String> {
    val process = ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    return process.waitFor() to output
}

private fun activeMods(): List<SourceMod> {
    val query = ".mods[] | [.repository, .artifact, (.tasks | join(\",\")), ((.dependsOn // []) | join(\",\"))] | @tsv"
    val (status, output) = capture(listOf("jq", "-r", query, root.resolve("gradle/active-custom-mods.json").toString()))
    if (status != 0) fail("active source inventory could not be read: $output")
    return output.lineSequence().filter(String::isNotBlank).map { line ->
        val parts = line.split('\t')
        SourceMod(parts[0], parts[1], parts[2].split(',').filter(String::isNotBlank),
            parts.getOrElse(3) { "" }.split(',').filter(String::isNotBlank))
    }.toList()
}

private fun allSourceNames(): List<String> = Files.list(workspace.resolve("mod_source")).use { stream ->
    stream.filter(Files::isDirectory).map { it.fileName.toString() }.sorted().toList()
}

private fun sourceTasks(name: String, active: List<SourceMod>): List<String> = when (name) {
    "burnt-grass-compat" -> listOf("verifyFull")
    "dynamic-trees-dimension-compat" -> listOf("runData", "verifyFull")
    else -> active.firstOrNull { it.repository == name }?.tasks ?: fail("unknown source repository: $name")
}

private fun runId(): String = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    .withZone(ZoneOffset.UTC).format(Instant.now()) + "-" + ProcessHandle.current().pid()

private fun controlPath(id: String): Path = root.resolve("generated/test-evidence/$id/control.json")

private fun writeControl(id: String, fields: Map<String, Any?>) {
    val path = controlPath(id)
    Files.createDirectories(path.parent)
    val temporary = path.resolveSibling(".control.json.tmp")
    Files.writeString(temporary, json(linkedMapOf("schema" to schema, "run_id" to id,
        "updated_at" to Instant.now().toString()) + fields) + "\n")
    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}

private fun launch(command: List<String>, cwd: Path, log: Path, environment: Map<String, String> = emptyMap()): Int {
    Files.createDirectories(log.parent)
    val process = ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true)
        .redirectOutput(log.toFile()).apply { environment().putAll(environment) }.start()
    return process.waitFor()
}

private fun sourceRun(id: String, selected: String?, all: Boolean): Map<String, Any?> {
    val active = activeMods()
    val names = if (all) allSourceNames() else listOf(selected ?: fail("source run needs --repo or --all"))
    if (names.size != 47 && all) fail("source inventory must contain 47 repositories; found ${names.size}")
    val completed = linkedMapOf<String, String>()
    val staged = controlPath(id).parent.resolve("source-jars")
    Files.createDirectories(staged)
    val pending = names.toMutableSet()
    while (pending.isNotEmpty()) {
        val ready = pending.sorted().filter { name ->
            !all || active.firstOrNull { it.repository == name }?.dependencies.orEmpty().all { it in completed }
        }
        if (ready.isEmpty()) fail("source dependency graph contains a cycle or missing provider")
        ready.forEach { name ->
            val mod = active.firstOrNull { it.repository == name }
            if (all && mod != null && mod.dependencies.any { completed[it] != "passed" }) {
                completed[name] = "blocked-by-provider"
            } else {
                val repo = workspace.resolve("mod_source/$name")
                val tasks = sourceTasks(name, active)
                val status = launch(listOf(repo.resolve("gradlew").toString(), "--no-daemon") + tasks,
                    repo, controlPath(id).parent.resolve("source-$name.log"),
                    if (all) mapOf("BC_CUSTOM_MOD_JAR_DIR" to staged.toString()) else emptyMap())
                val sourceLog = controlPath(id).parent.resolve("source-$name.log")
                val output = Files.readString(sourceLog)
                val gameTestsMissing = name != "better-exploration-load-control" &&
                    output.contains(":runGameTestServer") &&
                    !Regex("All [1-9][0-9]* required tests passed").containsMatchIn(output)
                completed[name] = when {
                    status != 0 -> "failed ($status)"
                    gameTestsMissing -> "failed (GameTest run did not pass)"
                    else -> "passed"
                }
                if (completed[name] == "passed" && all && mod != null) {
                    val jar = repo.resolve("build/libs").resolve(mod.artifact)
                    if (Files.isRegularFile(jar)) Files.copy(jar, staged.resolve(mod.artifact), StandardCopyOption.REPLACE_EXISTING)
                    else completed[name] = "failed (missing staged JAR)"
                }
            }
            pending.remove(name)
            writeControl(id, mapOf("state" to "running", "scope" to "source", "checks" to completed,
                "evidence" to controlPath(id).parent.toString()))
        }
    }
    return mapOf("checks" to completed, "state" to if (completed.values.all { it == "passed" }) "passed" else "failed")
}

private fun runtimeRun(id: String, tier: String, option: Map<String, String>): Map<String, Any?> {
    if (tier !in setOf("dev", "dist", "debug")) fail("unknown tier: $tier")
    val command = mutableListOf(root.resolve("test.main.kts").toString(), tier)
    option["--target"]?.let { command += listOf("--target", it) }
    option["--retry-of"]?.let { command += listOf("--retry-of", it) }
    if (option["--existing-candidate"] == "true") command += "--existing-candidate"
    val log = controlPath(id).parent.resolve("run.log")
    val status = launch(command, root, log, mapOf("BC_TEST_RUN_ID" to id))
    val output = Files.readString(log)
    val childRuns = Regex("(?m)^(release run|Dist run|Debug run|run): ([^\\s]+)$")
        .findAll(output).associate { it.groupValues[1].lowercase().replace(' ', '_') to it.groupValues[2] }
    return mapOf("state" to if (status == 0) "passed" else "failed", "exit_code" to status,
        "child_runs" to childRuns, "log" to log.toString())
}

private fun readStatus(id: String): String {
    val control = controlPath(id)
    if (Files.isRegularFile(control)) return Files.readString(control).trim()
    val evidence = control.parent
    if (!Files.isDirectory(evidence)) fail("unknown run ID: $id")
    val reports = Files.walk(evidence, 3).use { stream ->
        stream.filter { it.fileName.toString() == "run.json" }.map(Path::toString).sorted().toList()
    }
    return json(mapOf("schema" to schema, "run_id" to id, "state" to "legacy",
        "evidence" to evidence.toString(), "reports" to reports))
}

when (args.firstOrNull()) {
    "list" -> {
        if (args.size != 1) fail("list accepts no options")
        val active = activeMods()
        reply(mapOf("state" to "ready", "tiers" to listOf("dev", "dist", "debug"),
            "source_repositories" to allSourceNames().map { name ->
                mapOf("repository" to name, "active" to active.any { it.repository == name },
                    "tasks" to sourceTasks(name, active))
            }))
    }
    "plan" -> {
        val scope = args.getOrNull(1) ?: fail("plan needs dev, dist, debug, or source")
        val opt = requestOptions(scope, 2)
        val active = activeMods()
        if (scope !in setOf("dev", "dist", "debug", "source")) fail("unknown scope: $scope")
        reply(mapOf("state" to "ready", "scope" to scope,
            "tiers" to when {
                scope == "source" -> emptyList()
                scope == "dev" -> listOf("dev")
                scope == "dist" -> listOf("dev", "dist")
                opt["--target"] != null -> listOf("dev", "debug-target")
                opt["--existing-candidate"] == "true" -> listOf("dist-prerequisite-or-receipt", "debug")
                else -> listOf("dev", "dist", "debug")
            },
            "fresh_source_builds" to if (scope in setOf("dist", "debug") &&
                opt["--target"] == null && opt["--existing-candidate"] != "true") active.size else 0,
            "source_repositories" to if (scope == "source")
                (if (opt["--all"] == "true") allSourceNames() else listOf(opt["--repo"] ?: fail("source plan needs --repo or --all")))
                else active.map { it.repository },
            "target" to opt["--target"], "candidate_policy" to when {
                scope == "source" || scope == "dev" -> "none"
                opt["--existing-candidate"] == "true" -> "existing-full-tier"
                opt["--target"] != null -> "existing-targeted"
                else -> "fresh-full-tier"
            }))
    }
    "run", "retry" -> {
        val scope = args.getOrNull(1) ?: fail("run needs dev, dist, debug, or source")
        val opt = requestOptions(scope, 2)
        if (args[0] == "retry" && scope == "source") fail("source runs cannot be retried by pack run ID")
        if (args[0] == "retry" && (opt["--target"] == null || opt["--retry-of"] == null))
            fail("retry needs --target and --retry-of")
        val id = runId()
        writeControl(id, mapOf("state" to "running", "scope" to scope, "target" to opt["--target"],
            "retry_of" to opt["--retry-of"], "evidence" to controlPath(id).parent.toString()))
        println(readStatus(id))
        System.out.flush()
        val result = try {
            if (scope == "source") sourceRun(id, opt["--repo"], opt["--all"] == "true")
            else runtimeRun(id, scope, opt)
        } catch (failure: Exception) {
            mapOf("state" to "failed", "error" to (failure.message ?: failure.javaClass.simpleName))
        }
        writeControl(id, mapOf("scope" to scope, "target" to opt["--target"],
            "retry_of" to opt["--retry-of"], "evidence" to controlPath(id).parent.toString()) + result)
        println(readStatus(id))
        exitProcess(if (result["state"] == "passed") 0 else 1)
    }
    "submit" -> {
        val opt = options(1, setOf("--handoff"))
        val handoff = opt["--handoff"] ?: fail("submit needs --handoff PATH")
        val (status, output) = capture(listOf(root.resolve("pack-test-queue.main.kts").toString(), "request", handoff))
        reply(mapOf("state" to if (status == 0) "queued" else "failed", "handoff" to handoff,
            "details" to output.trim(), "exit_code" to status))
        exitProcess(if (status == 0) 0 else 1)
    }
    "status", "evidence", "wait" -> {
        val id = args.getOrNull(1) ?: fail("${args[0]} needs a run ID")
        if (!Regex("[A-Za-z0-9._-]+").matches(id)) fail("invalid run ID")
        val opt = options(2, if (args[0] == "wait") setOf("--timeout-ms") else emptySet())
        if (args[0] == "status") println(readStatus(id))
        if (args[0] == "evidence") {
            val evidence = controlPath(id).parent
            if (!Files.isDirectory(evidence)) fail("unknown run ID: $id")
            val files = Files.walk(evidence, 3).use { stream ->
                stream.filter(Files::isRegularFile).map(Path::toString).sorted().toList()
            }
            reply(mapOf("state" to "ready", "run_id" to id, "evidence" to evidence.toString(), "files" to files))
        }
        if (args[0] == "wait") {
            val timeout = opt["--timeout-ms"]?.toLongOrNull() ?: 30_000L
            if (timeout !in 1..60_000) fail("wait timeout must be 1..60000 ms")
            val deadline = System.nanoTime() + timeout * 1_000_000
            var status = readStatus(id)
            while ("\"state\":\"running\"" in status && System.nanoTime() < deadline) {
                Thread.sleep(250)
                status = readStatus(id)
            }
            println(status)
            exitProcess(if ("\"state\":\"passed\"" in status) 0 else if ("\"state\":\"running\"" in status) 4 else 1)
        }
    }
    else -> fail("usage: test.main.kts <list|plan|run|submit|status|wait|evidence|retry> ...")
}
