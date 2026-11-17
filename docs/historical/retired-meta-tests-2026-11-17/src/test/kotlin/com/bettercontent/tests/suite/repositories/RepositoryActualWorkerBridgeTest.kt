package com.bettercontent.tests.suite.repositories

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.suiteMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import javax.tools.ToolProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir

/** Future human-approved TEST phase only. Unlike hand-authored JSON fixtures this builds the
 * exact repository agent source, invokes its fixed init script in a tiny real JavaExec worker,
 * and consumes its actual worker bytes in RepositoryWorkerInputUse + the v3 XML importer.
 * Direct pinned Gradle JVM entry point only: no wrapper-shell identity capture or production
 * RepositoryRunner coverage. No selected candidate, production receipt, Minecraft or repository qualification here. */
@Tag("repository-real-worker-bridge")
class RepositoryActualWorkerBridgeTest {
    // Retain real-worker inputs/reports for the owning runner to archive before its next clean.
    @TempDir(cleanup = CleanupMode.NEVER) lateinit var temporary: Path
    private fun bound(file: Path) = BoundFile(file.toString(), Hashes.sha256(file))
    private fun jar(classes: Path, destination: Path, premain: Boolean) {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            if (premain) mainAttributes.putValue("Premain-Class",
                "com.bettercontent.playtest.repositoryinput.RepositoryInputUseAgent")
        }
        JarOutputStream(Files.newOutputStream(destination, CREATE_NEW), manifest).use { output ->
            Files.walk(classes).use { stream -> stream.filter { Files.isRegularFile(it) }.forEach { file ->
                val entry = JarEntry(classes.relativize(file).toString().replace('\\', '/'))
                output.putNextEntry(entry)
                Files.copy(file, output)
                output.closeEntry()
            } }
        }
    }
    private fun compile(classes: Path, source: Path) {
        Files.createDirectories(classes)
        val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler()) { "future bridge needs a JDK 17+" }
        assertEquals(0, compiler.run(null, null, null, "--release", "17", "-d", classes.toString(), source.toString()))
    }
    @Test fun `fixed observer output from a genuine provider-loading JavaExec worker reaches verifier and v3 importer`() {
        assumeTrue(System.getenv("BC_REPOSITORY_WORKER_BRIDGE_TEST") == "1",
            "Requires separate human test-run approval and explicit future bridge test opt-in")
        val pack = Path.of("").toAbsolutePath().toRealPath()
        val agentSource = pack.resolve("scripts/playtest-repository-input-use/RepositoryInputUseAgent.java")
        val init = pack.resolve("scripts/playtest-repository-input-use/worker-init.gradle")
        val gradleBin = Path.of(requireNotNull(System.getenv("BC_REPOSITORY_WORKER_GRADLE_BIN")) {
            "future approved bridge needs a pre-existing local Gradle 8.8 binary; never download/provision one"
        }).toRealPath()
        require(Files.isExecutable(gradleBin) && Hashes.sha256(gradleBin) ==
            requireNotNull(System.getenv("BC_REPOSITORY_WORKER_GRADLE_SHA256"))) {
            "future test-phase Gradle binary must be independently pinned"
        }
        require(listOf(agentSource, init).all { Files.isRegularFile(it) })
        val agentClasses = temporary.resolve("agent-classes")
        compile(agentClasses, agentSource) // Future test fixture only, never a production helper build receipt.
        val agentJar = temporary.resolve("worker-agent.jar")
        jar(agentClasses, agentJar, true)
        val root = Files.createDirectory(temporary.resolve("tiny-project"))
        val providerSource = root.resolve("fixture-src/tiny/ProviderProof.java")
        Files.createDirectories(providerSource.parent)
        Files.writeString(providerSource, "package tiny; public final class ProviderProof { public static String value() { return \"used\"; } }\n")
        val providerClasses = temporary.resolve("provider-classes")
        compile(providerClasses, providerSource)
        val provider = root.resolve("providers/actual.jar")
        Files.createDirectories(provider.parent)
        jar(providerClasses, provider, false)
        val workerSource = root.resolve("src/main/java/tiny/WorkerMain.java")
        Files.createDirectories(workerSource.parent)
        Files.writeString(workerSource, """package tiny;
            public final class WorkerMain {
              public static void main(String[] args) throws Exception {
                if (!"used".equals(ProviderProof.value())) throw new IllegalStateException("provider not loaded");
                Thread.sleep(1800L); // allow owned descendant PID/birth tracking, not gameplay.
                String stamp = java.time.Instant.now().toString();
                String xml = "<testsuite tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" timestamp=\"" + stamp +
                    "\"><testcase classname=\"tiny.WorkerFixture\" name=\"loadsProvider\"/></testsuite>";
                java.nio.file.Files.writeString(java.nio.file.Path.of(System.getenv("BC_TINY_XML")), xml);
              }
            }
        """.trimIndent())
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'tiny-repository-worker-bridge'\n")
        Files.writeString(root.resolve("build.gradle"), """plugins { id 'java' }
            dependencies { implementation files('providers/actual.jar') }
            tasks.register('verifyWorker', JavaExec) {
                dependsOn tasks.named('classes')
                classpath = files(sourceSets.main.output.classesDirs, file('providers/actual.jar'))
                mainClass = 'tiny.WorkerMain'
                environment 'BC_TINY_XML', file('build/tiny-report.xml').absolutePath
            }
        """.trimIndent())
        val artifact = root.resolve("candidate/mod.jar")
        Files.createDirectories(artifact.parent)
        Files.writeString(artifact, "not on worker classpath")
        val home = Path.of(System.getProperty("java.home")).toRealPath()
        val java = home.resolve("bin/java").toRealPath()
        val jdkComponents = listOf("lib/modules", "lib/server/libjvm.so", "release").map { home.resolve(it).toRealPath() }
        // Independently reviewed Gradle 8.8 / JDK 17 UTF-8/en profile, not observed worker output.
        val reviewedArgs = listOf("<controller-task-roster>", "<controller-repository-agent>",
            "-Dfile.encoding=UTF-8", "-Duser.country", "-Duser.language=en", "-Duser.variant")
        val plan = RepositoryRunPlan("bc.playtest_repository_run_plan.v3", "reviewed", "future-test-fixture",
            Instant.now().toString(), "bridge-only", "tiny-repository-worker-bridge",
            mapOf("client_sha256" to "a".repeat(64), "server_sha256" to "b".repeat(64)),
            bound(agentSource), bound(agentSource), bound(artifact), root.toString(), "./gradlew",
            listOf("--no-daemon", "--max-workers=2", "--offline", "--no-build-cache", "--rerun-tasks", "--console=plain", "verifyWorker"),
            emptyMap(), 120,
            mapOf("source" to emptyList(), "tests" to emptyList(), "providers" to listOf(
                RepositoryInput("providers/actual.jar", Hashes.sha256(provider), Files.size(provider), false)),
                "toolchain" to emptyList(), "configuration" to emptyList(), "artifact" to listOf(
                    RepositoryInput("candidate/mod.jar", Hashes.sha256(artifact), Files.size(artifact), false))),
            listOf(RepositoryReportPlan("junit", "verifyWorker", "build/tiny-report.xml",
                listOf("tiny.WorkerFixture.loadsProvider"), null, null, invocationTask = "verifyWorker")),
            runtimeToolchainFacts = mapOf("java_major" to Runtime.version().feature().toString(), "gradle_version" to "fixture-only"),
            invocationTasks = listOf("verifyWorker"), artifactBinding = RepositoryArtifactBinding("mod-jar", jar = bound(artifact)),
            executionContext = "source-only", usageReport = "build/observed-workers", usageReportProducer = agentSource.toString(),
            useHarness = RepositoryInputUseHarness(bound(agentSource), bound(init), bound(agentJar),
                bound(agentSource), bound(java), jdkComponents.map(::bound),
                reviewedArgs))
        val evidence = Files.createDirectory(temporary.resolve("evidence"))
        val argsEvidence = evidence.resolve("reviewed-vm-arguments.json")
        Files.write(argsEvidence, suiteMapper.writeValueAsBytes(reviewedArgs))
        println("Prelaunch retained fixture=$temporary reviewed_vm_args_sha256=${Hashes.sha256(argsEvidence)}")
        val prepared = RepositoryWorkerInputUse.prepare(plan, root, evidence, "c".repeat(64))
        val output = evidence.resolve("stdout.log")
        val errors = evidence.resolve("stderr.log")
        val launcher = RepositoryBridgeGradleLaunch.command(gradleBin, java, evidence)
        val started = Instant.now()
        val execution = RepositoryCommand.execute(launcher + listOf("--init-script", init.toString()) +
            plan.arguments, root, mapOf("JAVA_HOME" to Path.of(System.getProperty("java.home")).toString(),
                "GRADLE_USER_HOME" to temporary.resolve("isolated-gradle-home").toString(),
                "BC_REPOSITORY_USE_BINDING" to prepared.binding.path,
                "BC_REPOSITORY_USE_BINDING_SHA256" to prepared.binding.sha256), output, errors, Duration.ofSeconds(120))
        val ended = Instant.now()
        Files.write(evidence.resolve("execution.json"), suiteMapper.writeValueAsBytes(execution),
            StandardOpenOption.CREATE_NEW)
        assertTrue(execution.exitCode == 0 && execution.cleanupComplete && execution.survivingPids.isEmpty(),
            "tiny future worker execution failed (not a pack result): ${Files.readString(errors)}")
        val use = RepositoryWorkerInputUse.verify(plan, root, prepared, "c".repeat(64), execution, started, ended, evidence)
        val worker = suiteMapper.readTree(prepared.reports.values.single().toFile())
        assertEquals("bc.repository_input_use_worker.v3", worker.path("schema").asText())
        assertEquals("bounded-file-class-stage", worker.path("status").asText())
        assertTrue(worker.path("class_definitions").any { it.path("source").asText() == provider.toRealPath().toString() },
            "Actual provider class definition bytes must be observed, not inferred from a declared jar")
        assertTrue(worker.path("code_sources").any { it.path("path").asText() == provider.toRealPath().toString() })
        val rawXml = root.resolve("build/tiny-report.xml")
        val xml = evidence.resolve("tiny-report.xml")
        Files.copy(rawXml, xml)
        val fingerprints = mapOf("reference_sha256" to Hashes.sha256(agentSource),
            "source_sha256" to Hashes.sha256(workerSource), "tests_sha256" to Hashes.sha256(providerSource),
            "providers_sha256" to Hashes.sha256(provider), "toolchain_sha256" to Hashes.sha256(java),
            "configuration_sha256" to Hashes.sha256(init), "artifact_sha256" to Hashes.sha256(artifact))
        val sidecar = evidence.resolve("binding.json")
        val claim = linkedMapOf<String, Any>("schema" to "bc.repository_report_binding.v3", "kind" to "junit",
            "repository" to plan.repository, "task" to "verifyWorker", "invocation_task" to "verifyWorker",
            "run_id" to plan.runId, "raw_sha256" to Hashes.sha256(xml),
            "artifact_binding" to requireNotNull(plan.artifactBinding), "execution_context" to "source-only",
            "observed_input_use" to use, "test_ids" to listOf("tiny.WorkerFixture.loadsProvider"),
            "completed_at" to Instant.now().toString(), "report_timezone" to "UTC")
        claim.putAll(fingerprints)
        Files.writeString(sidecar, suiteMapper.writeValueAsString(claim), CREATE_NEW)
        val expected = RepositoryReportExpectation(Hashes.sha256(agentSource), plan.repository, "verifyWorker", plan.runId,
            Hashes.sha256(workerSource), Hashes.sha256(providerSource), Hashes.sha256(provider), Hashes.sha256(java),
            Hashes.sha256(init), Hashes.sha256(artifact), setOf("tiny.WorkerFixture.loadsProvider"), started,
            Hashes.sha256(xml), ZoneId.of("UTC"), "verifyWorker", plan.artifactBinding, "source-only", use.sha256)
        assertEquals(setOf("tiny.WorkerFixture.loadsProvider"),
            RepositoryReportImporter.importJUnit(xml, sidecar, expected).testIds)
    }
}
