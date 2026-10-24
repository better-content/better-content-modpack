package com.bettercontent.tests

import com.bettercontent.tests.release.BuildIsolationPool
import com.bettercontent.tests.release.ParallelBuildScheduler
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Real, Minecraft-free Gradle fixture builds. Never provider qualification or a speed benchmark. */
@Tag("fast")
class RetoolingBuildIntegrationTest {
    private data class Artifact(val path: Path, val sha256: String)

    @Test fun nativeObserverRuntimePreservesRuntimeOnlyAndExcludesCompileOnlyInputs() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val gradle = Path.of(System.getProperty("bc.retool.gradle.home"), "bin", "gradle")
        assertTrue(Files.isExecutable(gradle))
        val output = root.resolve("generated/test-evidence/fresh-fixes-20261023/retool-focused")
        Files.createDirectories(output)
        val fixture = Files.createDirectory(output.resolve("native-runtime-role-${UUID.randomUUID()}"))
        val pack = Files.createDirectory(fixture.resolve("inert-pack"))
        val seed = Files.createDirectory(fixture.resolve("empty-seed"))
        val repo = Files.createDirectory(fixture.resolve("project"))
        // Execute the ACTUAL production assignments against real Gradle SourceSets. These
        // inert membership witnesses are not JARs, SDKs, native tests or provider acceptance.
        val assignments = Files.readAllLines(root.resolve("gradle/native-test-timing.gradle"))
            .map(String::trim).filter {
                it.startsWith("timing.compileClasspath =") || it.startsWith("timing.runtimeClasspath =")
            }
        assertEquals(2, assignments.size, "the production classpath assignments must be unambiguous")
        listOf("compile-only", "implementation", "runtime-only").forEach {
            Files.writeString(repo.resolve("$it.witness"), "INERT_CLASSPATH_MEMBERSHIP_WITNESS\n")
        }
        Files.writeString(repo.resolve("settings.gradle"), "rootProject.name = 'native-runtime-role'\n")
        Files.writeString(repo.resolve("build.gradle"), """
            plugins { id 'java' }
            dependencies {
                compileOnly files('compile-only.witness')
                implementation files('implementation.witness')
                runtimeOnly files('runtime-only.witness')
            }
            def main = sourceSets.main
            def timing = sourceSets.create('bcNativeTiming')
            ${assignments.joinToString("\n")}
            tasks.register('verifyRuntimeRole') {
                doLast {
                    assert timing.compileClasspath.files == main.compileClasspath.files
                    assert timing.compileClasspath.files.contains(file('compile-only.witness'))
                    assert !timing.compileClasspath.files.contains(file('runtime-only.witness'))
                    assert timing.runtimeClasspath.files == ((timing.output.files + main.runtimeClasspath.files) as Set)
                    assert !timing.runtimeClasspath.files.contains(file('compile-only.witness'))
                    assert timing.runtimeClasspath.files.contains(file('implementation.witness'))
                    assert timing.runtimeClasspath.files.contains(file('runtime-only.witness'))
                    assert timing.runtimeClasspath.files.containsAll(timing.output.files)
                    assert main.output.classesDirs.files.intersect(timing.output.classesDirs.files).empty
                    println 'ACTUAL_GRADLE_SOURCESET_RUNTIME_ROLE_PASS_NOT_NATIVE_ACCEPTANCE'
                }
            }
        """.trimIndent() + "\n")
        val pool = BuildIsolationPool(pack, fixture.resolve("worker"), 1, sharedGradleHome = seed)
        pool.prepare()
        pool.withWorker(repo) { supplied ->
            val context = requireNotNull(supplied)
            val command = context.command(repo, listOf(gradle.toString(), "--no-daemon", "--offline",
                "--max-workers=1", "--init-script", root.resolve("gradle/test-timing.init.gradle").toString(), "verifyRuntimeRole"))
            val status = BoundedCommand.run(repo, command, fixture.resolve("verification.log"), Duration.ofSeconds(90),
                context.environment() + mapOf("BC_TIMING_NATIVE" to "false",
                    "BC_TIMING_FILE" to fixture.resolve("timing.jsonl").toString(),
                    "BC_TIMING_PROFILE" to "SYNTHETIC_NATIVE_RUNTIME_ROLE_NOT_PROVIDER_ACCEPTANCE"))
            assertEquals(0, status, "actual Gradle runtime closure regression; retained evidence: $fixture")
        }
    }

    @Test fun independentGradleBuildsOverlapAndConsumerUsesCompletedProviderBytes() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val gradle = Path.of(System.getProperty("bc.retool.gradle.home"), "bin", "gradle")
        assertTrue(Files.isExecutable(gradle), "the invoking Gradle distribution must be supplied explicitly")
        val output = root.resolve("generated/test-evidence/fresh-fixes-20261023/retool-focused")
        Files.createDirectories(output)
        val fixture = Files.createDirectory(output.resolve("gradle-overlap-${UUID.randomUUID()}"))
        val barrier = Files.createDirectory(fixture.resolve("barrier"))
        val pack = Files.createDirectory(fixture.resolve("inert-pack"))
        val seed = Files.createDirectory(fixture.resolve("empty-seed"))
        val repos = listOf("a", "b", "consumer").associateWith { name ->
            Files.createDirectories(fixture.resolve("projects/$name/src/main/java/fixture"))
            fixture.resolve("projects/$name")
        }
        repos.forEach { (name, repo) ->
            Files.writeString(repo.resolve("settings.gradle"), "rootProject.name = '$name'\n")
            val compilation = if (name == "consumer") "" else """
                tasks.named('compileJava') {
                    doFirst {
                        def marker = new File('${barrier}', 'started-$name')
                        marker.text = java.time.Instant.now().toString()
                        def other = new File('${barrier}', 'started-${if (name == "a") "b" else "a"}')
                        long limit = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(35)
                        while (!other.isFile()) {
                            if (System.nanoTime() >= limit) throw new GradleException('independent build did not overlap')
                            Thread.sleep(20)
                        }
                        new File('${barrier}', 'observed-peer-$name').text = other.text
                    }
                    doLast { new File('${barrier}', 'finished-$name').text = java.time.Instant.now().toString() }
                }
            """.trimIndent()
            Files.writeString(repo.resolve("build.gradle"), "plugins { id 'java' }\n$compilation\n")
            val body = when (name) {
                "a" -> "public class A { public static int value() { return 42; } }"
                "b" -> "public class B { public static int value() { return 7; } }"
                else -> "public class Consumer { public int value() { return A.value(); } }"
            }
            val className = when (name) { "a" -> "A"; "b" -> "B"; else -> "Consumer" }
            Files.writeString(repo.resolve("src/main/java/fixture/$className.java"), "package fixture; $body\n")
        }
        val pool = BuildIsolationPool(pack, fixture.resolve("workers"), 2, sharedGradleHome = seed)
        pool.prepare() // Empty test seed, not a read/copy/population of the real shared SDK cache.
        val start = Instant.now()
        val result = ParallelBuildScheduler(2).run<Artifact>(linkedMapOf(
            "a" to emptyList(), "b" to emptyList(), "consumer" to listOf("a"),
        )) { name, dependencies ->
            val repo = repos.getValue(name)
            if (name == "consumer") {
                val provider = dependencies.getValue("a")
                assertEquals(provider.sha256, Hashes.sha256(provider.path))
                assertTrue(Files.isRegularFile(barrier.resolve("finished-a")))
                Files.writeString(repo.resolve("build.gradle"),
                    "\ndependencies { implementation files('${provider.path}') }\n",
                    java.nio.file.StandardOpenOption.APPEND)
            }
            pool.withWorker(repo) { supplied ->
                val context = requireNotNull(supplied)
                val env = context.environment() + mapOf(
                    "BC_TIMING_FILE" to fixture.resolve("$name.timing.jsonl").toString(),
                    "BC_TIMING_RUN_ID" to fixture.fileName.toString(),
                    "BC_TIMING_REVISION" to "SYNTHETIC_RETOOLING_FIXTURE",
                    "BC_TIMING_INPUT_ID" to Hashes.sha256(repo.resolve("build.gradle")),
                    "BC_TIMING_PROFILE" to "focused-gradle-fixture/$name",
                    "BC_TIMING_ENVIRONMENT_ID" to "SYNTHETIC_JAVA17_${System.getProperty("os.arch")}",
                    "BC_TIMING_CACHE_ID" to "EMPTY_PRIVATE_FIXTURE_SEED",
                    "BC_TIMING_TIMEOUT_SECONDS" to "90",
                )
                val command = context.command(repo, listOf(gradle.toString(), "--no-daemon", "--offline",
                    "--max-workers=1", "--init-script", root.resolve("gradle/test-timing.init.gradle").toString(), "build"))
                val status = BoundedCommand.run(repo, command, fixture.resolve("$name.log"), Duration.ofSeconds(90), env)
                assertEquals(0, status, "real minimal Gradle build failed; retained evidence: $fixture")
                val jar = repo.resolve("build/libs/$name.jar")
                assertTrue(Files.isRegularFile(jar))
                Artifact(jar, Hashes.sha256(jar))
            }
        }
        assertTrue(result.failures.isEmpty(), result.failures.joinToString("\n"))
        assertEquals(setOf("a", "b", "consumer"), result.completed.keys)
        // Each independent compile task saw the other task's start BEFORE proceeding.
        // This causal handshake proves overlap without comparing clocks across JVMs.
        assertTrue(Files.isRegularFile(barrier.resolve("observed-peer-a")))
        assertTrue(Files.isRegularFile(barrier.resolve("observed-peer-b")))
        assertNotEquals(repos.getValue("a"), repos.getValue("b"))
        val records = Files.readAllLines(fixture.resolve("a.timing.jsonl")).map { jacksonObjectMapper().readTree(it) }
        assertTrue(records.any { it["scope"].asText() == "gradle_task" && it["event"].asText() == "terminal" })
        assertTrue(records.any { it["scope"].asText() == "process" && it["event"].asText() == "terminal" && it["status"].asText() == "SUCCEEDED" })
        jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(fixture.resolve("overlap-proof.json").toFile(), mapOf(
            "scope" to "SYNTHETIC_MINIMAL_GRADLE_BUILD_INTEGRATION_NOT_PROVIDER_ACCEPTANCE",
            "started_utc" to start.toString(), "finished_utc" to Instant.now().toString(),
            "independent_builds" to listOf("a", "b"), "causal_overlap_handshake" to true,
            "consumer_waited_for_a" to true, "provider_sha256" to result.completed.getValue("a").sha256,
            "fixtures_retained" to true, "real_shared_sdk_cache_populated" to false,
        ))
    }
}
