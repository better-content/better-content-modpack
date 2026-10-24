package com.bettercontent.tests

import com.bettercontent.tests.release.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile

/** Explicit, finite real-source adapter experiment; neither Dev nor owning-gate qualification.
 * The launching Root driver MUST NOT hold the canonical Forge cache lock: pool.prepare owns it.
 * No clean, staging, source edits, checkpoint, deployment, packaging or release acceptance.
 */
@Tag("focused-source-runtime")
class FocusedProviderRuntimeTest {
    private val mapper = jacksonObjectMapper()
    private val pins = linkedMapOf(
        "better-gameplay-notices" to "40f1699fb97e5fcc57c30ecdfd6736bad4d10a1b",
        "scalable-tnt" to "9ab603e94d1e808f47fed0496b41eb1ec343836f",
    )

    @Test fun controlledActualCompileOverlapAndNativeAdapter() {
        assumeTrue(System.getenv("BC_FOCUSED_PROVIDER_RUNTIME") == "true",
            "Explicit BC_FOCUSED_PROVIDER_RUNTIME=true required; no implicit native run")
        val root = Path.of(System.getProperty("bc.repo.root", ".")).toRealPath()
        check(root == Path.of("/home/dev/better-content-modpack").toRealPath()) { "Real Root checkout required" }
        val evidence = Path.of(checkNotNull(System.getenv("BC_FOCUSED_PROVIDER_EVIDENCE")) {
            "Driver must register BC_FOCUSED_PROVIDER_EVIDENCE with maintenance before launching producers"
        })
        check(evidence.isAbsolute && evidence == evidence.normalize() &&
            evidence.startsWith(root.resolve("generated/test-evidence"))) { "Expected fresh registered Root evidence path" }
        Files.createDirectory(evidence)
        val manifest: ActiveModManifest = mapper.readValue(Files.readString(root.resolve("gradle/active-custom-mods.json")))
        val repos = pins.keys.associateWith { root.parent.resolve("mod_source/$it").toRealPath() }
        val compileDone = CountDownLatch(2)
        val successful = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val pool = BuildIsolationPool(root, evidence.resolve("private-builds"), 2)
        val markers = Files.createDirectory(evidence.resolve("overlap"))
        val executor = Executors.newSingleThreadExecutor()
        val inputs = java.util.concurrent.ConcurrentHashMap<String, String>()
        val baseline = System.getenv("BC_TIMING_BASELINES")
        check(baseline.isNullOrBlank() || baseline == "[]") { "This fresh UNKNOWN-baseline experiment refuses inherited baselines" }
        check(System.getenv("BC_FORGE_CACHE_LOCK_HELD").isNullOrBlank()) { "Outer driver must not hold Forge cache lock" }
        fun git(repo: Path, vararg args: String): String {
            val log = evidence.resolve("git-${UUID.randomUUID()}.log")
            check(BoundedCommand.run(repo, listOf("git") + args, log, Duration.ofSeconds(30)) == 0)
            return Files.readString(log).trim()
        }
        fun checkSource(name: String) {
            val repo = repos.getValue(name)
            check(git(repo, "rev-parse", "HEAD") == pins.getValue(name)) { "Unexpected HEAD: $name" }
            check(git(repo, "status", "--porcelain=v1", "--untracked-files=all").isEmpty()) { "Dirty real source: $name" }
        }
        pins.keys.forEach { name ->
            checkSource(name)
            check(manifest.mods.single { it.repository == name }.dependsOn.isEmpty()) { "Compile-only empty view requires no SOURCE providers" }
            check(!BuildIsolationPool.requiresSharedCache(repos.getValue(name))) { "Private source isolation unavailable: $name" }
        }
        // Bind actual dirty Root inputs too; never substitute a clean HEAD for changed support bytes.
        val rootFiles = git(root, "ls-files", "--cached", "--others", "--exclude-standard").lines()
            .filter { it.isNotBlank() }.map(root::resolve).filter { Files.isRegularFile(it) }
        val rootIdentity = BuildIsolationTiming.digest(rootFiles.associate { root.relativize(it).toString() to Hashes.sha256(it) })
        Files.writeString(evidence.resolve("root-inputs.json"), mapper.writeValueAsString(mapOf(
            "head" to git(root, "rev-parse", "HEAD"), "status" to git(root, "status", "--porcelain=v1"),
            "files" to rootFiles.associate { root.relativize(it).toString() to Hashes.sha256(it) })))
        val views = pins.keys.associateWith { name ->
            ProviderInputs.snapshot(evidence.resolve("inputs-$name"), emptyList(), ProviderInputMode.EXPLICIT_UNTESTED)
        }
        fun invoke(name: String, context: BuildIsolationContext, tasks: List<String>, seconds: Long, overlap: Boolean) {
            checkSource(name)
            val repo = repos.getValue(name)
            val view = views.getValue(name)
            ProviderInputs.verify(view)
            val recipe = BuildIsolationTiming.digest(mapOf("tasks" to tasks.joinToString(" "), "mode" to "EXPLICIT_UNTESTED",
                "view" to view.digest, "controlled-overlap" to overlap.toString(),
                "hook" to Hashes.sha256(root.resolve("gradle/retool-source-overlap.init.gradle"))))
            val input = BuildIsolationTiming.inputIdentity(rootIdentity, pins.getValue(name), recipe, mapOf("empty-view" to view.digest))
            val facts = BuildIsolationTiming.environmentFacts(true)
            val environmentId = BuildIsolationTiming.digest(facts)
            val cache = context.cacheObservation()
            val label = if (overlap) "compile" else "native-jar"
            val sink = evidence.resolve("$name-$label.timing.jsonl")
            inputs["$name-$label"] = input
            Files.writeString(evidence.resolve("$name-$label-facts.json"), mapper.writeValueAsString(mapOf(
                "input" to input, "source_head" to pins.getValue(name), "recipe" to recipe,
                "view" to view.digest, "environment" to facts, "cache" to cache.facts,
                "expected_runtime" to "UNKNOWN", "acceptance" to "NOT_EVALUATED")))
            val env = providerBuildEnvironment(view.directory) + context.environment() + mapOf(
                "BC_FOCUSED_PROVIDER_RUNTIME" to "true", "BC_FOCUSED_OVERLAP_DIRECTORY" to markers.toString(),
                "BC_FOCUSED_OVERLAP_OWN" to name, "BC_FOCUSED_OVERLAP_PEER" to pins.keys.single { it != name },
                "BC_TIMING_FILE" to sink.toString(), "BC_TIMING_RUN_ID" to evidence.fileName.toString(),
                "BC_TIMING_REVISION" to pins.getValue(name), "BC_TIMING_INPUT_ID" to input,
                "BC_TIMING_PROFILE" to "focused-source-runtime-$label", "BC_TIMING_ENVIRONMENT_ID" to environmentId,
                "BC_TIMING_CACHE_ID" to cache.id, "BC_TIMING_BASELINES" to "[]",
                "BC_TIMING_TIMEOUT_SECONDS" to seconds.toString(), "BC_TIMING_NATIVE" to "true",
                "BC_TIMING_JUNIT" to "true", "BC_TIMING_PYTHON" to "true",
                "PYTHONPATH" to (root.resolve("scripts/timing-python").toString() +
                    (System.getenv("PYTHONPATH")?.takeIf { it.isNotBlank() }?.let { ":$it" } ?: "")),
            )
            val args = listOf("./gradlew", "--no-daemon", "--init-script", root.resolve("gradle/test-timing.init.gradle").toString()) +
                (if (overlap) listOf("--init-script", root.resolve("gradle/retool-source-overlap.init.gradle").toString()) else emptyList()) + tasks
            var outcome = "FAILED"
            var primary: Throwable? = null
            try {
                check(BoundedCommand.run(repo, context.command(repo, args), evidence.resolve("$name-$label.log"),
                    Duration.ofSeconds(seconds), env) == 0) { "Actual $label failed: $name" }
                outcome = "SUCCEEDED"
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally {
                try { context.recordInvocation(input, environmentId, tasks.joinToString(" "), outcome); ProviderInputs.verify(view); checkSource(name) }
                catch (failure: Throwable) { if (primary != null) primary.addSuppressed(failure) else throw failure }
            }
        }
        // Seed is raw downloads only, copied while owning the canonical cache lock.
        try {
            pool.prepare()
            val batch = executor.submit(Callable {
                ParallelBuildScheduler(2).run(pins.keys.associateWith { emptyList<String>() }) { name, _ ->
                    pool.withWorker(repos.getValue(name)) { nullable ->
                        val context = checkNotNull(nullable)
                        checkSource(name)
                        val tracked = git(repos.getValue(name), "ls-files").lines().toSet()
                        // BEFORE any Gradle/native output write; retain old builds/worlds/logs intact.
                        retainSourceOutputsForVerification(repos.getValue(name), evidence.resolve("retained-$name").also(Files::createDirectories), tracked)
                        try {
                            invoke(name, context, listOf("compileJava"), 600, true)
                            successful.add(name)
                        } finally { compileDone.countDown() }
                        if (name == "scalable-tnt") {
                            check(compileDone.await(600, TimeUnit.SECONDS)) { "Peer compile did not terminate" }
                            check(successful.size == 2) { "Native launch forbidden after peer failure" }
                            verifyOverlap(markers)
                            // Same still-locked private worker context; jar finalizes reobfJar in actual source.
                            invoke(name, context, listOf("runGameTestServer", "jar"), 300, false)
                            verifyNative(repos.getValue(name), evidence)
                            auditSourceNativeLogs(repos.getValue(name), evidence.resolve("native-log-audit"), views.getValue(name).directory)
                        }
                        name
                    }
                }
            })
            val result = try { batch.get(900, TimeUnit.SECONDS) }
                catch (failure: Throwable) { batch.cancel(true); throw failure }
            check(result.failures.isEmpty() && result.completed.size == 2) { result.failures.joinToString("\n") }
            verifyOverlap(markers)
            pins.keys.forEach(::checkSource)
            val classes = repos.mapValues { (_, repo) -> files(repo.resolve("build/classes/java/main")).filter { it.toString().endsWith(".class") } }
            check(classes.values.all { it.isNotEmpty() }) { "Actual source bytecode missing" }
            // Cache/generated-world bytes are retained, not claimed byte-identical baseline inputs.
            // Hash the experiment's immutable observations and actual compiled/shipping outputs.
            val observations = Files.list(evidence).use { paths -> paths.filter { Files.isRegularFile(it) }.toList() } +
                files(markers) + files(repos.getValue("scalable-tnt").resolve("run-gametest/logs")) +
                files(evidence.resolve("native-log-audit")) + views.values.flatMap { files(it.directory) }
            val outputs = observations + classes.values.flatten() + files(repos.getValue("scalable-tnt").resolve("build"))
                .filter { it.toString().endsWith(".jar") || it.toString().endsWith(".health.json") }
            Files.writeString(evidence.resolve("proof.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapOf(
                "schema" to "bc.focused_provider_runtime.v1", "source_heads" to pins, "root_input" to rootIdentity,
                "inputs" to inputs, "scope" to "CONTROLLED_ACTUAL_SOURCE_OVERLAP_AND_NATIVE_ADAPTER_ONLY",
                "acceptance" to "NOT_EVALUATED", "expected_runtime" to "UNKNOWN", "natural_throughput_benchmark" to false,
                "retained_cache_and_world_byte_identity" to "UNMEASURED_NOT_BASELINE_AUTHORITY",
                "outputs" to outputs.distinct().associate { it.toString() to Hashes.sha256(it) })))
        } finally {
            executor.shutdownNow()
            check(executor.awaitTermination(150, TimeUnit.SECONDS)) { "Batch cleanup incomplete; all evidence retained" }
            val retainedObservations = Files.list(evidence).use { paths -> paths.filter { Files.isRegularFile(it) }.toList() } + files(markers)
            Files.writeString(evidence.resolve("retained-observation-hashes.json"), mapper.writeValueAsString(mapOf(
                "source_heads" to pins, "acceptance" to "NOT_EVALUATED", "root_input" to rootIdentity,
                "outputs" to retainedObservations.associate { it.toString() to Hashes.sha256(it) })))
            println("Focused source runtime evidence retained: $evidence; release acceptance NOT_EVALUATED")
        }
    }

    private fun files(root: Path): List<Path> = if (!Files.exists(root)) emptyList() else Files.walk(root).use { paths ->
        paths.filter { Files.isRegularFile(it) }.toList()
    }
    private fun json(path: Path): JsonNode = mapper.readTree(Files.readString(path))
    private fun verifyOverlap(directory: Path) {
        val records = pins.keys.associateWith { name -> listOf("entered", "peer-seen", "completed").associateWith {
            json(directory.resolve("$name.$it.json"))
        } }
        records.forEach { (name, record) ->
            check(record.getValue("entered")["monotonic_ns"].asLong() <= record.getValue("peer-seen")["monotonic_ns"].asLong())
            check(record.getValue("peer-seen")["monotonic_ns"].asLong() < record.getValue("completed")["monotonic_ns"].asLong())
            check(record.getValue("completed")["did_work"].asBoolean() && record.getValue("completed")["class_count"].asInt() > 0)
            val peer = records.getValue(pins.keys.single { it != name })
            check(peer.getValue("entered")["monotonic_ns"].asLong() <= record.getValue("peer-seen")["monotonic_ns"].asLong())
        }
        check(records.values.maxOf { it.getValue("entered")["monotonic_ns"].asLong() } <
            records.values.minOf { it.getValue("completed")["monotonic_ns"].asLong() }) { "Controlled compile action intervals did not overlap" }
    }

    private fun verifyNative(repo: Path, evidence: Path) {
        val nativeLog = Files.readString(repo.resolve("run-gametest/logs/latest.log"))
        listOf("1 tests are now running!", "1 GAME TESTS COMPLETE", "All 1 required tests passed :)").forEach {
            check(nativeLog.contains(it)) { "Original native acceptance marker missing: $it" }
        }
        val healthFiles = files(repo.resolve("build/native-timing")).filter { it.toString().endsWith(".health.json") }
        check(healthFiles.size == 1) { "Expected one fresh native health receipt" }
        val health = json(healthFiles.single())
        val sink = evidence.resolve("scalable-tnt-native-jar.timing.jsonl")
        check(health["schema"].asText() == "bc.native_timing_health.v1" && health["healthy"].asBoolean())
        check(!health["interrupted"].asBoolean() && !health["wall_timed_out"].asBoolean())
        check(health["sink"].asText() == sink.toString() && health["task_path"].asText() == ":runGameTestServer")
        UUID.fromString(health["invocation_id"].asText())
        check(health["started_count"].asInt() == 1 && health["terminal_count"].asInt() == 1)
        val records = Files.readAllLines(sink).filter { it.isNotBlank() }.map(mapper::readTree).filter { it["scope"]?.asText() == "native_test" }
        check(records.size == 2 && records.all { it["invocation_id"].asText() == health["invocation_id"].asText() })
        val start = records.single { it["event"].asText() == "start" }
        val terminal = records.single { it["event"].asText() == "terminal" }
        check(start["operation_id"] == terminal["operation_id"] && terminal["status"].asText() == "SUCCEEDED")
        // Pinned original class declares @PrefixGameTestTemplate(false): no class-name prefix.
        val exactCaseName = "authoredvariantsprimewithdistinctpowerandvanillaremainsuntagged"
        check(start["name"].asText() == exactCaseName && terminal["name"].asText() == exactCaseName && start["required"].asBoolean()) {
            "Native name must preserve the original unprefixed Forge registration: ${start["name"]} / ${terminal["name"]}"
        }
        check(terminal["native_duration_source"].asText() == "GameTestInfo.getRunTime_Stopwatch_elapsed_milliseconds")
        check(terminal["native_duration_ms"].isNumber && terminal["duration_ns"].isNumber)
        check(terminal["configured_timeout_ns"].isNull && terminal["expected_runtime_ns"].isNull)
        val shipping = repo.resolve("build/libs/scalable-tnt-0.1.0.jar")
        check(Files.isRegularFile(shipping))
        (listOf(shipping) + listOf(repo.resolve("build/reobfJar/output.jar")).filter(Files::exists)).forEach { jar ->
            JarFile(jar.toFile()).use { archive -> archive.entries().asSequence().forEach { entry ->
                check(!entry.name.contains("bc/nonshipping/") && !entry.name.contains("bc_native_timing") &&
                    !entry.name.contains("src/nativeTiming") && !entry.name.contains("src/junitTiming") &&
                    !(entry.name.startsWith("META-INF/services/") && archive.getInputStream(entry).use { String(it.readAllBytes()) }.contains("bc.nonshipping"))) {
                    "Nonshipping helper leaked into $jar: ${entry.name}"
                }
            } }
        }
    }
}
