package com.bettercontent.tests.suite.repositories

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.suiteMapper
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import javax.tools.ToolProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir

/** Separate future human-approved TEST phase. A Gradle Test worker uses a dynamic classloader,
 * unlike the JavaExec-only bridge. Uses a direct pinned Gradle JVM entry point, not wrapper-shell
 * identity capture or production RepositoryRunner coverage. No candidate, gameplay or production qualification. */
@Tag("repository-real-worker-bridge")
class RepositoryGradleTestWorkerBridgeTest {
    // Retain real-worker inputs/reports for the owning runner to archive before its next clean.
    @TempDir(cleanup = CleanupMode.NEVER) lateinit var temporary: Path
    private fun bound(path: Path) = BoundFile(path.toString(), Hashes.sha256(path))
    private fun bindReviewedArgs(template: List<String>, root: Path): List<String> {
        val owner = temporary.toRealPath()
        require(root == owner.resolve("tiny-gradle-test") &&
            Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) && root.toRealPath() == root) {
            "fixture root must be the exact canonical owned tiny-gradle-test directory"
        }
        require(!Files.exists(root.resolve("build"), LinkOption.NOFOLLOW_LINKS)) { "fixture build must be absent before binding" }
        val marker = "<fixture-worker-tmpdir>"
        require(template.count { it == marker } == 1 && template.none { it != marker && it.contains(marker) }) {
            "fixture tmpdir requires one whole-entry marker"
        }
        require(template.count { it == "<controller-repository-agent>" } == 1 &&
            template.count { it == "<controller-task-roster>" } == 1) { "controller markers must occur exactly once" }
        val exact = "-Dorg.gradle.internal.worker.tmpdir=${root.resolve("build/tmp/test/work")}"
        return template.map { if (it == marker) exact else it }
    }
    @Test fun `reviewed tmpdir binding rejects aliases reused roots and malformed markers`() {
        val root = Files.createDirectory(temporary.resolve("tiny-gradle-test"))
        val template = listOf("<controller-task-roster>", "<fixture-worker-tmpdir>",
            "<controller-repository-agent>", "-Xmx512m", "-Dfile.encoding=UTF-8", "-Duser.country",
            "-Duser.language=en", "-Duser.variant", "-ea")
        val expected = template.toMutableList().apply {
            this[1] = "-Dorg.gradle.internal.worker.tmpdir=$root/build/tmp/test/work"
        }
        assertEquals(expected, bindReviewedArgs(template, root))
        fun reject(args: List<String>, path: Path, message: String) {
            assertEquals(message, assertThrows(IllegalArgumentException::class.java) { bindReviewedArgs(args, path) }.message)
        }
        val malformed = "fixture tmpdir requires one whole-entry marker"
        reject(template + "<fixture-worker-tmpdir>", root, malformed)
        reject(template.filterNot { it == "<fixture-worker-tmpdir>" }, root, malformed)
        reject(template.map { if (it == "<fixture-worker-tmpdir>") "-Dextra=$it" else it }, root, malformed)
        reject(template + "-Dextra=<fixture-worker-tmpdir>", root, malformed)
        reject(template + "<controller-repository-agent>", root, "controller markers must occur exactly once")
        reject(template.filterNot { it == "<controller-task-roster>" }, root, "controller markers must occur exactly once")
        val other = Files.createDirectory(temporary.resolve("other"))
        val ownership = "fixture root must be the exact canonical owned tiny-gradle-test directory"
        reject(template, other, ownership)
        val build = Files.createDirectory(root.resolve("build"))
        reject(template, root, "fixture build must be absent before binding")
        Files.delete(build)
        Files.move(root, temporary.resolve("retained-root"))
        Files.createSymbolicLink(root, other)
        reject(template, root, ownership)
    }
    @Test fun `direct launcher pins reject missing malformed changed and nonregular tools`() {
        val tool = temporary.resolve("tool.bin")
        Files.writeString(tool, "reviewed fixture bytes")
        val sha = Hashes.sha256(tool)
        assertEquals(BoundFile(tool.toString(), sha), RepositoryBridgeGradleLaunch.verifyPin(tool, sha))
        fun reject(path: Path, pin: String?, message: String) {
            assertEquals(message, assertThrows(IllegalArgumentException::class.java) {
                RepositoryBridgeGradleLaunch.verifyPin(path, pin)
            }.message)
        }
        reject(tool, null, "explicit tool SHA-256 pin required")
        reject(tool, "unreviewed", "explicit tool SHA-256 pin required")
        reject(tool, "0".repeat(64), "pinned tool bytes changed")
        reject(temporary, sha, "pinned tool must be an ordinary canonical file")
        reject(temporary.resolve("missing"), sha, "pinned tool must be an ordinary canonical file")
        val link = Files.createSymbolicLink(temporary.resolve("tool-link"), tool)
        reject(link, sha, "pinned tool must be an ordinary canonical file")
        Files.writeString(tool, "changed after pinning")
        reject(tool, sha, "pinned tool bytes changed")
    }

    @Test fun `fixed observer sees real Gradle Test leaf or fails closed on unsupported worker scope`() {
        assumeTrue(System.getenv("BC_REPOSITORY_WORKER_TEST_BRIDGE_TEST") == "1",
            "Requires separate explicit human approval and opt-in; no automatic test/worker run")
        val pack = Path.of("").toAbsolutePath().toRealPath()
        val source = pack.resolve("scripts/playtest-repository-input-use/RepositoryInputUseAgent.java")
        val init = pack.resolve("scripts/playtest-repository-input-use/worker-init.gradle")
        fun pinned(pathVar: String, hashVar: String): Path {
            val file = Path.of(requireNotNull(System.getenv(pathVar))).toRealPath()
            require(Files.isRegularFile(file) && Hashes.sha256(file) == requireNotNull(System.getenv(hashVar)))
            return file
        }
        val gradle = pinned("BC_REPOSITORY_WORKER_GRADLE_BIN", "BC_REPOSITORY_WORKER_GRADLE_SHA256")
        val junit = pinned("BC_REPOSITORY_TEST_JUNIT4_JAR", "BC_REPOSITORY_TEST_JUNIT4_SHA256")
        val hamcrest = pinned("BC_REPOSITORY_TEST_HAMCREST_JAR", "BC_REPOSITORY_TEST_HAMCREST_SHA256")
        val argsFile = pinned("BC_REPOSITORY_TEST_REVIEWED_ARGS_JSON", "BC_REPOSITORY_TEST_REVIEWED_ARGS_SHA256")
        val argsNode = suiteMapper.readTree(argsFile.toFile())
        require(argsNode.isArray && argsNode.size() in 2..256 && argsNode.all { it.isTextual && it.asText().isNotBlank() })
        val reviewedTemplate = argsNode.map { it.asText() }
        require(reviewedTemplate.count { it == "<controller-repository-agent>" } == 1 &&
            reviewedTemplate.count { it == "<controller-task-roster>" } == 1)
        val classes = Files.createDirectory(temporary.resolve("agent-classes"))
        assertEquals(0, requireNotNull(ToolProvider.getSystemJavaCompiler()).run(null, null, null,
            "--release", "17", "-d", classes.toString(), source.toString()))
        val agent = temporary.resolve("observer.jar")
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Premain-Class", "com.bettercontent.playtest.repositoryinput.RepositoryInputUseAgent")
        }
        JarOutputStream(Files.newOutputStream(agent), manifest).use { jar ->
            Files.walk(classes, 64).use { stream -> stream.filter { Files.isRegularFile(it) }.forEach { file ->
                jar.putNextEntry(JarEntry(classes.relativize(file).toString().replace('\\', '/')))
                Files.copy(file, jar); jar.closeEntry()
            } }
        }
        val root = Files.createDirectory(temporary.resolve("tiny-gradle-test"))
        val reviewedArgs = bindReviewedArgs(reviewedTemplate, root)
        val providerSource = root.resolve("fixture-src/tiny/ProviderProof.java")
        Files.createDirectories(providerSource.parent)
        Files.writeString(providerSource, "package tiny; public final class ProviderProof { public static int used() { return 17; } }\n")
        val providerClasses = Files.createDirectory(temporary.resolve("provider-classes"))
        assertEquals(0, requireNotNull(ToolProvider.getSystemJavaCompiler()).run(null, null, null,
            "--release", "17", "-d", providerClasses.toString(), providerSource.toString()))
        val provider = root.resolve("providers/provider.jar")
        Files.createDirectories(provider.parent)
        JarOutputStream(Files.newOutputStream(provider)).use { jar ->
            jar.putNextEntry(JarEntry("tiny/ProviderProof.class"))
            Files.copy(providerClasses.resolve("tiny/ProviderProof.class"), jar); jar.closeEntry()
        }
        // A real main-to-provider call keeps the default Gradle Test classpath meaningful.
        val mainSource = root.resolve("src/main/java/tiny/ProviderAccess.java")
        Files.createDirectories(mainSource.parent)
        Files.writeString(mainSource, "package tiny; public final class ProviderAccess { public static int used() { return ProviderProof.used(); } }\n")
        val testSource = root.resolve("src/test/java/tiny/ProviderTest.java")
        Files.createDirectories(testSource.parent)
        Files.writeString(testSource, """package tiny;
            import org.junit.Test;
            public final class ProviderTest {
              @Test public void loadsProvider() throws Exception {
                if (ProviderAccess.used() != 17) throw new IllegalStateException("provider unused");
                Thread.sleep(1800L);
              }
            }
        """.trimIndent())
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'tiny-gradle-test-bridge'\n")
        Files.writeString(root.resolve("build.gradle"), """plugins { id 'java' }
            dependencies { implementation files('providers/provider.jar'); testImplementation files('${junit.toString().replace("'", "\\'")}') }
            dependencies { testRuntimeOnly files('${hamcrest.toString().replace("'", "\\'")}') }
            tasks.named('test') { useJUnit(); maxParallelForks = 1 }
        """.trimIndent())
        // Deliberately NO src/main/resources or src/test/resources. Gradle may list their
        // missing generated build/resources/{main,test} paths in the declared task.classpath;
        // these are absent allowed inputs, not evidence of loaded code or a missing provider.
        assertTrue(!Files.exists(root.resolve("src/main/resources")) &&
            !Files.exists(root.resolve("src/test/resources")) &&
            !Files.exists(root.resolve("build/resources/main")) &&
            !Files.exists(root.resolve("build/resources/test")))
        val artifact = root.resolve("candidate/mod.jar")
        Files.createDirectories(artifact.parent)
        Files.writeString(artifact, "never loaded selected package")
        val home = Path.of(System.getProperty("java.home")).toRealPath()
        val java = home.resolve("bin/java").toRealPath()
        val runtime = listOf("lib/modules", "lib/server/libjvm.so", "release").map { home.resolve(it).toRealPath() }
        val gradleLib = gradle.parent.parent.resolve("lib")
        require(Files.isDirectory(gradleLib) && !Files.isSymbolicLink(gradleLib))
        val tools = Files.walk(gradleLib, 4).use { paths -> paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".jar") }
            .limit(4097).toList().also { require(it.size <= 4096) } }
        val plan = RepositoryRunPlan("bc.playtest_repository_run_plan.v3", "reviewed", "fixture-reviewer",
            Instant.now().toString(), "test-bridge-only", "tiny-test-bridge",
            mapOf("client_sha256" to "a".repeat(64), "server_sha256" to "b".repeat(64)),
            bound(source), bound(source), bound(artifact), root.toString(), "./gradlew",
            listOf("--no-daemon", "--max-workers=2", "--offline", "--no-build-cache", "--rerun-tasks", "--console=plain", "test"),
            emptyMap(), 120,
            mapOf("source" to emptyList(), "tests" to emptyList(), "providers" to listOf(
                RepositoryInput("providers/provider.jar", Hashes.sha256(provider), Files.size(provider), false)),
                "toolchain" to emptyList(), "configuration" to emptyList(), "artifact" to emptyList()),
            listOf(RepositoryReportPlan("junit", "test", "build/test-results/test/TEST-tiny.ProviderTest.xml",
                listOf("tiny.ProviderTest.loadsProvider"), null, null, invocationTask = "test")),
            runtimeToolchainFacts = mapOf("java_major" to Runtime.version().feature().toString(), "gradle_version" to "fixture-only"),
            invocationTasks = listOf("test"), artifactBinding = RepositoryArtifactBinding("mod-jar", jar = bound(artifact)),
            executionContext = "source-only", usageReport = "build/observed-workers", usageReportProducer = source.toString(),
            useHarness = RepositoryInputUseHarness(bound(source), bound(init), bound(agent), bound(source), bound(java),
                (runtime + tools + listOf(junit, hamcrest)).distinct().map(::bound), reviewedArgs))
        val external = requireNotNull(plan.useHarness).allowedExternalClasspath
        assertTrue(external.all { Path.of(it.path).isAbsolute })
        assertEquals(listOf(BoundFile(junit.toString(), requireNotNull(System.getenv("BC_REPOSITORY_TEST_JUNIT4_SHA256")))),
            external.filter { it.path == junit.toString() })
        assertEquals(listOf(BoundFile(hamcrest.toString(), requireNotNull(System.getenv("BC_REPOSITORY_TEST_HAMCREST_SHA256")))),
            external.filter { it.path == hamcrest.toString() })
        val evidence = Files.createDirectory(temporary.resolve("evidence"))
        val argsEvidence = evidence.resolve("reviewed-vm-arguments.json")
        Files.write(argsEvidence, suiteMapper.writeValueAsBytes(reviewedArgs))
        Files.copy(argsFile, evidence.resolve("reviewed-vm-arguments.template.json"))
        println("Prelaunch retained fixture=$temporary reviewed_vm_args_sha256=${Hashes.sha256(argsEvidence)}")
        val prepared = RepositoryWorkerInputUse.prepare(plan, root, evidence, "c".repeat(64))
        val launcher = RepositoryBridgeGradleLaunch.command(gradle, java, evidence)
        val started = Instant.now()
        val execution = RepositoryCommand.execute(launcher + listOf("--init-script", init.toString()) +
            plan.arguments, root, mapOf("JAVA_HOME" to home.toString(),
                "GRADLE_USER_HOME" to temporary.resolve("isolated-gradle-home").toString(),
                "BC_REPOSITORY_USE_BINDING" to prepared.binding.path,
                "BC_REPOSITORY_USE_BINDING_SHA256" to prepared.binding.sha256),
            evidence.resolve("stdout.log"), evidence.resolve("stderr.log"), Duration.ofSeconds(120))
        val ended = Instant.now()
        Files.write(evidence.resolve("execution.json"), suiteMapper.writeValueAsBytes(execution),
            StandardOpenOption.CREATE_NEW)
        assertTrue(execution.exitCode == 0 && execution.cleanupComplete && execution.survivingPids.isEmpty())
        val report = prepared.reports.values.single()
        val observed = suiteMapper.readTree(report.toFile())
        assertEquals("bc.repository_input_use_worker.v3", observed.path("schema").asText())
        // An incomplete real Test worker is a compatibility failure, not a green bridge.
        // Do not label Gradle tool/pre-agent/transformer gaps as proven provider use.
        assertEquals("bounded-file-class-stage", observed.path("status").asText(),
            "real Gradle Test worker provenance incomplete: ${observed.path("unsupported")}")
        val use = RepositoryWorkerInputUse.verify(plan, root, prepared, "c".repeat(64), execution, started, ended, evidence)
        assertEquals("bc.repository_input_use.v3", suiteMapper.readTree(Path.of(use.path).toFile()).path("schema").asText())
        assertTrue(observed.path("class_definitions").any { it.path("source").asText() == provider.toString() })
    }
}
