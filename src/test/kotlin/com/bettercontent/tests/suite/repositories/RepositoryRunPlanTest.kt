package com.bettercontent.tests.suite.repositories

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** Static preflight tests only. Never invokes RepositoryRunner.runApproved or any source task. */
class RepositoryRunPlanTest {
    @TempDir lateinit var campaign: Path
    private fun setup(): Pair<RepositoryRunPlan, Path> {
        val root = Files.createDirectories(campaign.resolve("repository-inputs/better-spawns"))
        val byGroup = mapOf("source" to "src/Source.kt", "tests" to "src/test/Test.kt",
            "toolchain" to "gradlew", "configuration" to "gradle.properties", "artifact" to "candidate/mod.jar")
        val groups = byGroup.mapValues { (_, relative) ->
            val file = root.resolve(relative)
            Files.createDirectories(file.parent)
            Files.writeString(file, "fixture content: $relative\n")
            if (relative == "gradlew") file.toFile().setExecutable(true)
            listOf(RepositoryInput(relative, Hashes.sha256(file), Files.size(file), Files.isExecutable(file)))
        } + mapOf("providers" to emptyList())
        val artifactFile = root.resolve("candidate/mod.jar")
        val artifact = BoundFile(artifactFile.toString(), Hashes.sha256(artifactFile))
        val reference = campaign.resolve("reference.json")
        Files.writeString(reference, suiteMapper.writeValueAsString(mapOf("schema" to "bc.playtest_reference.v1",
            "candidate" to mapOf("client_sha256" to "a".repeat(64), "server_sha256" to "b".repeat(64)),
            "sources" to listOf(mapOf("repository" to "better-spawns", "production_fingerprint" to null,
                "artifact_sha256" to artifact.sha256)))))
        val attestation = campaign.resolve("attestation.json")
        Files.writeString(attestation, "{}")
        return RepositoryRunPlan("bc.playtest_repository_run_plan.v2", "reviewed", "reviewer",
            Instant.now().minusSeconds(60).toString(), "run-one", "better-spawns",
            mapOf("client_sha256" to "a".repeat(64), "server_sha256" to "b".repeat(64)),
            BoundFile(reference.toString(), Hashes.sha256(reference)),
            BoundFile(attestation.toString(), Hashes.sha256(attestation)), artifact,
            root.toString(), "./gradlew", listOf("--no-daemon", "--no-build-cache", "--rerun-tasks", "--console=plain", "test"),
            emptyMap(), 1000, groups,
            listOf(RepositoryReportPlan("junit", "test", "build/test-results/test/TEST-Test.xml",
                listOf("example.Test.one"), null, null, invocationTask = "test")),
            runtimeToolchainFacts = mapOf("gradle_version" to "8.8", "java_major" to "17"),
            invocationTasks = listOf("test")) to root
    }

    @Test fun `complete static input inventory fingerprints six categories without claiming a run`() {
        val (plan, root) = setup()
        assertEquals(RepositoryRunContracts.groups, RepositoryRunContracts.inventory(plan, root).keys)
    }
    @Test fun `generated directory exclusions are exact roots never arbitrary package basenames`() {
        val (plan, root) = setup()
        val legitimate = root.resolve("src/main/java/org/example/build/run/Production.java")
        Files.createDirectories(legitimate.parent)
        Files.writeString(legitimate, "class Production {}")
        assertThrows(IllegalArgumentException::class.java) { RepositoryRunContracts.inventory(plan, root) }
        val declared = plan.copy(inputs = plan.inputs + ("source" to (plan.inputs.getValue("source") +
            RepositoryInput(root.relativize(legitimate).toString(), Hashes.sha256(legitimate), Files.size(legitimate), false))))
        assertEquals(RepositoryRunContracts.groups, RepositoryRunContracts.inventory(declared, root).keys)
        val generated = root.resolve("build/generated/Transient.java")
        Files.createDirectories(generated.parent)
        Files.writeString(generated, "generated only")
        assertEquals(RepositoryRunContracts.groups, RepositoryRunContracts.inventory(declared, root).keys)
        val nested = root.resolve("submodule/build/generated/Transient.java")
        Files.createDirectories(nested.parent)
        Files.writeString(nested, "nested generated output")
        assertThrows(IllegalArgumentException::class.java) { RepositoryRunContracts.inventory(declared, root) }
        val moduleBuild = root.resolve("submodule/build.gradle.kts")
        Files.writeString(moduleBuild, "plugins {}")
        val explicitModule = declared.copy(generatedModuleRoots = listOf("submodule"), inputs = declared.inputs +
            ("configuration" to (declared.inputs.getValue("configuration") +
                RepositoryInput("submodule/build.gradle.kts", Hashes.sha256(moduleBuild), Files.size(moduleBuild), false))))
        assertEquals(RepositoryRunContracts.groups, RepositoryRunContracts.inventory(explicitModule, root).keys)
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryRunContracts.inventory(explicitModule.copy(generatedModuleRoots = listOf("unreviewed")), root)
        }
        val alias = root.resolve("src/main/java/org/example/build/Linked.java")
        Files.createSymbolicLink(alias, legitimate)
        assertThrows(IllegalArgumentException::class.java) { RepositoryRunContracts.inventory(explicitModule, root) }
    }
    @Test fun `v3 pack owner binds exact two ZIPs never one fictitious mod JAR`() {
        val (plan, root) = setup()
        val client = root.resolve("candidate/client.zip")
        val server = root.resolve("candidate/server.zip")
        Files.writeString(client, "client ZIP bytes")
        Files.writeString(server, "server ZIP bytes")
        Files.delete(root.resolve("candidate/mod.jar")) // pack owner has ZIP pair, never a residual fake JAR input
        val clientRef = BoundFile(client.toString(), Hashes.sha256(client))
        val serverRef = BoundFile(server.toString(), Hashes.sha256(server))
        val pack = plan.copy(schema = "bc.playtest_repository_run_plan.v3", repository = "better-content-modpack",
            artifact = null, artifactBinding = RepositoryArtifactBinding("pack-zip-pair", clientZip = clientRef,
                serverZip = serverRef), executionContext = "source-only", usageReport = "build/used-inputs.json",
            usageReportProducer = "src/test/Test.kt",
            inputs = plan.inputs + ("artifact" to listOf(
                RepositoryInput("candidate/client.zip", clientRef.sha256, Files.size(client), false),
                RepositoryInput("candidate/server.zip", serverRef.sha256, Files.size(server), false))))
        assertEquals(RepositoryRunContracts.groups, RepositoryRunContracts.inventory(pack, root).keys)
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryRunContracts.inventory(pack.copy(artifactBinding = pack.artifactBinding?.copy(serverZip = clientRef)), root)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryRunContracts.inventory(pack.copy(artifact = plan.artifact), root)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryRunContracts.validate(pack.copy(executionContext = "packaged-runtime"), campaign)
        }
        // A historical/fake reference cannot supply the selected pair or its attested pack_source.
        assertThrows(IllegalArgumentException::class.java) { RepositoryRunContracts.validate(pack, campaign) }
    }
    @Test fun `v3 mod owner cannot use selected ZIP pair in place of packaged JAR`() {
        val (plan, root) = setup()
        val v3 = plan.copy(schema = "bc.playtest_repository_run_plan.v3", artifactBinding =
            RepositoryArtifactBinding("mod-jar", jar = plan.artifact), executionContext = "source-only",
            usageReport = "build/used-inputs.json", usageReportProducer = "src/test/Test.kt")
        assertEquals(RepositoryRunContracts.groups, RepositoryRunContracts.inventory(v3, root).keys)
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryRunContracts.inventory(v3.copy(artifactBinding = RepositoryArtifactBinding("pack-zip-pair")), root)
        }
        assertThrows(IllegalArgumentException::class.java) { RepositoryRunContracts.validate(v3, campaign) }
    }
    @Test fun `missing production fingerprint in bundled build cannot be asserted from local tests`() {
        val (plan, _) = setup()
        assertThrows(IllegalArgumentException::class.java) { RepositoryRunContracts.validate(plan, campaign) }
    }
    @Test fun `historical v1 plan is never silently upgraded to aggregate v2 execution`() {
        val (plan, _) = setup()
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryRunContracts.validate(plan.copy(schema = "bc.playtest_repository_run_plan.v1"), campaign)
        }
    }
    @Test fun `undeclared checkout input fails before any source task can run`() {
        val (plan, root) = setup()
        Files.writeString(root.resolve("src/Hidden.kt"), "unexpected source")
        assertThrows(IllegalArgumentException::class.java) { RepositoryRunContracts.inventory(plan, root) }
    }
    @Test fun `hardlinked or changed test inputs cannot become matched fingerprints`() {
        val (plan, root) = setup()
        val file = root.resolve("src/test/Test.kt")
        val alias = root.resolve("src/test/Alias.kt")
        Files.createLink(alias, file)
        assertThrows(IllegalArgumentException::class.java) { RepositoryRunContracts.inventory(plan, root) }
        Files.delete(alias)
        Files.writeString(file, "changed test")
        assertThrows(IllegalArgumentException::class.java) { RepositoryRunContracts.inventory(plan, root) }
    }
    @Test fun `symlinked file or output directory is never an owned repository input`() {
        val (plan, root) = setup()
        val alias = root.resolve("src/Alias.kt")
        Files.createSymbolicLink(alias, root.resolve("src/Source.kt"))
        assertThrows(IllegalArgumentException::class.java) { RepositoryRunContracts.inventory(plan, root) }
        Files.delete(alias)
        Files.createDirectories(root.resolve("generated"))
        Files.createSymbolicLink(root.resolve("generated/external"), campaign)
        assertThrows(IllegalArgumentException::class.java) { RepositoryRunContracts.inventory(plan, root) }
    }
    @Test fun `Gradle run refuses cached or daemon-backed source task plans`() {
        val (plan, _) = setup()
        val unsafe = plan.copy(arguments = listOf("verifyFast"))
        assertThrows(IllegalArgumentException::class.java) { RepositoryRunContracts.validate(unsafe, campaign) }
    }
    @Test fun `claimed report tasks must be exact selected Gradle tasks with no hidden filters`() {
        val (plan, _) = setup()
        val matching = plan.copy(arguments = plan.arguments.dropLast(1) + listOf("--max-workers", "2", ":test"),
            invocationTasks = listOf(":test"), reports = plan.reports.map { it.copy(task = ":test", invocationTask = ":test") })
        assertEquals(setOf("test"), RepositoryRunContracts.requireExactTaskSelection(plan))
        assertEquals(setOf(":test"), RepositoryRunContracts.requireExactTaskSelection(matching))
        for (arguments in listOf(
            plan.arguments.dropLast(1) + "verifyFast", // aggregate is not a reviewed test task
            plan.arguments + "extraTask", plan.arguments + listOf("-x", "test"),
            plan.arguments + "-xtest", plan.arguments + "--exclude-task=test",
            plan.arguments + "--tests=example.Native.ok", plan.arguments + "-Iother.gradle",
            plan.arguments + listOf("--init-script", "replacement.gradle"),
            plan.arguments + "-p=elsewhere", plan.arguments + listOf("--max-workers", "many"),
            plan.arguments + "--dry-run", plan.arguments + "--settings-file=elsewhere.gradle"
        )) assertThrows(IllegalArgumentException::class.java) {
            RepositoryRunContracts.requireExactTaskSelection(plan.copy(arguments = arguments))
        }
    }
    @Test fun `reviewed aggregate invocation requires actual uncached report leaf plus source bound dependency`() {
        val (plan, root) = setup()
        val buildScript = root.resolve("build.gradle.kts")
        Files.writeString(buildScript, "tasks.register(\"verifyFast\") { dependsOn(tasks.named(\"test\")) }\n")
        val declared = RepositoryInput("build.gradle.kts", Hashes.sha256(buildScript), Files.size(buildScript), false)
        val link = RepositoryTaskAttribution("build.gradle.kts", declared.sha256, "independent reviewer",
            Instant.now().minusSeconds(60).toString(), "verifyFast dependsOn test in reviewed Gradle script")
        val aggregate = plan.copy(arguments = plan.arguments.dropLast(1) + "verifyFast", invocationTasks = listOf("verifyFast"),
            inputs = plan.inputs + ("configuration" to (plan.inputs.getValue("configuration") + declared)),
            reports = plan.reports.map { it.copy(invocationTask = "verifyFast", taskAttribution = link) })
        assertEquals(RepositoryRunContracts.groups, RepositoryRunContracts.inventory(aggregate, root).keys)
        assertEquals(setOf("test"), RepositoryRunContracts.requireExactTaskSelection(aggregate))
        RepositoryRunContracts.requireTaskAttribution(aggregate)
        RepositoryRunContracts.requireObservedTaskExecution("> Task :test\n> Task :verifyFast UP-TO-DATE\nBUILD SUCCESSFUL\n",
            setOf("test"), setOf("verifyFast"))
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryRunContracts.requireTaskAttribution(aggregate.copy(reports = aggregate.reports.map { it.copy(taskAttribution = null) }))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryRunContracts.requireTaskAttribution(aggregate.copy(reports = aggregate.reports.map {
                it.copy(taskAttribution = link.copy(declarationSha256 = "0".repeat(64))) }))
        }
        for (output in listOf("> Task :verifyFast\nBUILD SUCCESSFUL\n",
            "> Task :test SKIPPED\n> Task :verifyFast\nBUILD SUCCESSFUL\n", "> Task :test\nBUILD SUCCESSFUL\n")) {
            assertThrows(IllegalArgumentException::class.java) {
                RepositoryRunContracts.requireObservedTaskExecution(output, setOf("test"), setOf("verifyFast"))
            }
        }
    }
    @Test fun `observed uncached leaf task is not inferred from exit code or aggregate`() {
        RepositoryRunContracts.requireObservedTaskExecution("> Task :compileTestKotlin\n> Task :test\nBUILD SUCCESSFUL\n", setOf("test"))
        for (output in listOf("BUILD SUCCESSFUL\n", "> Task :verifyFast\nBUILD SUCCESSFUL\n",
            "> Task :test UP-TO-DATE\nBUILD SUCCESSFUL\n",
            "> Task :test SKIPPED\nBUILD SUCCESSFUL\n", "> Task :test FROM-CACHE\nBUILD SUCCESSFUL\n",
            "> Task :test\n> Task :test SKIPPED\nBUILD SUCCESSFUL\n")) {
            assertThrows(IllegalArgumentException::class.java) {
                RepositoryRunContracts.requireObservedTaskExecution(output, setOf("test"))
            }
        }
    }
    @Test fun `native plan requires per-ID registered evidence and raw log paths`() {
        val (plan, _) = setup()
        val native = plan.copy(reports = listOf(RepositoryReportPlan("forge-gametest", "headlessGameTest",
            "build/native/junit.xml", listOf("better_spawns:tests.actual"), null, null,
            invocationTask = "headlessGameTest")), invocationTasks = listOf("headlessGameTest"),
            arguments = plan.arguments.dropLast(1) + "headlessGameTest")
        assertThrows(IllegalArgumentException::class.java) { RepositoryRunContracts.validate(native, campaign) }
    }
}
