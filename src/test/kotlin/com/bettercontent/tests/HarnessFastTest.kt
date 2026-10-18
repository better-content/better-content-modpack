package com.bettercontent.tests

import com.bettercontent.tests.release.ActiveMod
import com.bettercontent.tests.release.annotateJar
import com.bettercontent.tests.release.build
import com.bettercontent.tests.release.withForgeCacheLock
import com.bettercontent.tests.release.packageResolveCommand
import com.bettercontent.tests.release.jarDeclaresMod
import com.bettercontent.tests.release.readSourceCommit
import com.bettercontent.tests.release.sourceUpdateStatus
import com.bettercontent.tests.release.SourceUpdate
import com.bettercontent.tests.release.ReflectionAllowance
import com.bettercontent.tests.release.readReflectionAllowlist
import com.bettercontent.tests.release.sourceReflectionViolations
import com.bettercontent.tests.release.unusedReflectionAllowances
import com.bettercontent.tests.release.bytecodeReflectionViolations
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Tag("fast")
class HarnessFastTest {
    @Test
    fun sharedForgeCacheHasOnlyOneWriterAndReadersWait(@TempDir workspace: Path) {
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        val entered = java.util.concurrent.CountDownLatch(1)
        val attempted = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val secondEntered = java.util.concurrent.CountDownLatch(1)
        val artifact = workspace.resolve("mapped.jar")
        try {
            val writer = pool.submit {
                withForgeCacheLock(workspace) {
                    artifact.writeText("incomplete")
                    entered.countDown()
                    assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    artifact.writeText("complete")
                }
            }
            val reader = pool.submit {
                assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
                attempted.countDown()
                withForgeCacheLock(workspace) {
                    secondEntered.countDown()
                    assertEquals("complete", artifact.readText())
                }
            }
            assertTrue(attempted.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(!secondEntered.await(150, java.util.concurrent.TimeUnit.MILLISECONDS))
            release.countDown()
            writer.get(5, java.util.concurrent.TimeUnit.SECONDS)
            reader.get(5, java.util.concurrent.TimeUnit.SECONDS)
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun forgeCacheLockIsReleasedAfterFailure(@TempDir workspace: Path) {
        assertThrows(IllegalStateException::class.java) {
            withForgeCacheLock(workspace) { error("simulated build failure") }
        }
        assertEquals("next build", withForgeCacheLock(workspace) { "next build" })
    }

    @Test
    fun runtimeFixtureDisablesWallClockScheduledBackups(@TempDir root: Path) {
        val config = root.resolve("config").also { it.createDirectories() }.resolve("ftbbackups2.json")
        config.writeText("""{"enabled": true, "backup_cron": "0 0 */2 * * ?"}""")

        disableScheduledBackupsForRuntimeFixture(root)

        assertEquals("""{"enabled": false, "backup_cron": "0 0 */2 * * ?"}""", Files.readString(config))
        assertThrows(IllegalArgumentException::class.java) { disableScheduledBackupsForRuntimeFixture(root) }
    }

    @Test
    fun testFacadeRejectsCachedPackSuiteResultsWithoutFreshEvidence() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val build = Files.readString(root.resolve("build.gradle.kts"))
        val facade = Files.readString(root.resolve("test.main.kts"))

        assertTrue(build.contains("outputs.upToDateWhen { false }"))
        assertTrue(facade.contains("report.lastModified() < startedAt"))
        assertTrue(facade.contains("validateFreshEvidence(evidenceSuite, startedAt)"))
        assertTrue(facade.contains("BC_TEST_EVIDENCE_SUITE"))
    }

    @Test
    fun packwizHashRefreshBelongsOnlyToFreshDistPreparation() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val facade = Files.readString(root.resolve("test.main.kts"))
        val packager = Files.readString(root.resolve("package.sh"))
        val release = Files.readString(root.resolve("src/main/kotlin/com/bettercontent/tests/release/ReleasePipeline.kt"))

        assertTrue(!facade.contains("packwiz refresh"))
        assertTrue(!packager.contains("packwiz refresh"))
        assertTrue(release.contains("listOf(\"packwiz\", \"refresh\")"))
        assertTrue(release.contains("listOf(\"git\", \"diff\", \"--check\")"))
    }

    @Test
    fun releaseWarmsTheCanonicalArtifactCacheBeforeModBuilds(@TempDir root: Path) {
        val target = root.resolve("build/release-dependency-warmup/run")
        assertEquals(
            listOf(root.resolve("package.sh").toString(), "resolve", root.toString(), target.toString(), "client"),
            packageResolveCommand(root, target),
        )
    }

    @Test
    fun activeReleaseInventoryIsUniqueAndMatchesCurrentSources() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val document = jacksonObjectMapper().readTree(root.resolve("gradle/active-custom-mods.json").toFile())
        assertEquals("bc.active_custom_mods.v1", document.path("schema").asText())
        val mods = document.path("mods")
        assertTrue(mods.isArray && !mods.isEmpty, "the active provider manifest must declare providers")
        val repositories = mods.map { it.path("repository").asText() }.toSet()
        assertEquals(mods.size(), repositories.size)
        assertEquals(mods.size(), mods.map { it.path("modId").asText() }.toSet().size)
        val mixinConfigOwners = mutableMapOf<String, String>()
        mods.forEach { mod ->
            val repository = mod.path("repository").asText()
            val repositoryRoot = root.parent.resolve("mod_source").resolve(repository)
            assertTrue(Files.isDirectory(repositoryRoot), "missing active source repository $repository")
            val artifact = root.resolve("mods").resolve(mod.path("artifact").asText())
            assertTrue(mod.path("tasks").isArray && mod.path("tasks").size() > 0)
            assertTrue(mod.path("dependsOn").let { it.isMissingNode || (it.isArray && it.all { dependency -> dependency.asText() in repositories }) })
            val configs = if (Files.isRegularFile(artifact)) {
                JarFile(artifact.toFile()).use { jar ->
                    jar.entries().asSequence().map { it.name }
                        .filter { it.endsWith(".mixins.json") && '/' !in it }.toList()
                }
            } else {
                val resources = repositoryRoot.resolve("src/main/resources")
                Files.walk(resources).use { files ->
                    files.filter(Files::isRegularFile).map { it.fileName.toString() }
                        .filter { it.endsWith(".mixins.json") }.toList()
                }
            }
            configs.forEach { config ->
                val previous = mixinConfigOwners.putIfAbsent(config, repository)
                assertTrue(previous == null, "$config is owned by both $previous and $repository")
            }
        }
        fun dependencies(repository: String) = mods.single { it.path("repository").asText() == repository }
            .path("dependsOn").map { it.asText() }
        assertEquals(listOf("better-industrial-heat"), dependencies("better-chemlib-hazards"))
        assertEquals(listOf("better-compat-fixes", "better-gameplay-notices", "better-dimension-fonts", "better-celestial-events"), dependencies("better-spirit-commerce"))
        assertEquals(
            listOf("better-gameplay-notices", "better-survival-hud", "better-industrial-heat"),
            dependencies("better-compat-fixes"),
        )
        assertEquals(listOf("better-world-management"), dependencies("better-spawns"))
        assertEquals(listOf("better-compat-fixes", "better-deaths-door"), dependencies("better-cave-encounters"))
        assertEquals(listOf("better-compat-fixes", "better-gameplay-notices", "better-celestial-events"), dependencies("better-dimension-fonts"))
        assertEquals(listOf("better-deaths-door"), dependencies("better-pillager-campaigns"))
        assertEquals(listOf("better-deaths-door"), dependencies("better-player-traces"))
        assertEquals(
            listOf(
                "better-magic-chunk-anchors", "better-spirit-commerce", "better-compat-fixes",
                "better-gameplay-notices",
                "better-bumblezone-crops", "better-create-train-fuel", "better-create-kinetic-loss",
                "better-cave-encounters", "better-dimension-fonts", "better-deaths-door", "better-industrial-heat",
                "better-chemlib-hazards", "better-oc2r-create-controls", "better-oc2r-wireless-messaging", "better-pillager-campaigns",
                "better-player-traces", "better-rail-beetle", "better-ore-geology", "better-rpg-progression", "better-settlement-roads",
                "better-survival-physiology", "better-tinkers-loot-affixes", "better-drinking-water", "better-world-management",
            ),
            dependencies("better-discovery-guides"),
        )

        mods.forEach { mod ->
            val repository = mod.path("repository").asText()
            val repositoryRoot = root.parent.resolve("mod_source").resolve(repository)
            val buildText = listOf(repositoryRoot.resolve("build.gradle"), repositoryRoot.resolve("build.gradle.kts"))
                .filter(Files::isRegularFile)
                .joinToString("\n") { Files.readString(it) }
            val siblingBuildDependencies = Regex("""\.\./([a-z0-9-]+)/build/libs""")
                .findAll(buildText).map { it.groupValues[1] }.toSet()
            val missingBuildDependencies = siblingBuildDependencies - dependencies(repository).toSet()
            assertTrue(
                missingBuildDependencies.isEmpty(),
                "$repository omits release dependencies for sibling build artifacts: ${missingBuildDependencies.sorted()}",
            )
        }
    }

    @Test
    fun retiredQuestContentIsAbsentAndSharedFtbModsRemain() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val retiredNamespaces = setOf("better_content_quests", "ftbquests", "ftbteams", "ftbxmodcompat", "ftbfiltersystem")
        val namespaces = jacksonObjectMapper().readTree(root.resolve("kubejs/config/crafting_policy.json").toFile())
            .path("namespaces")
        retiredNamespaces.forEach { assertTrue(!namespaces.has(it), "retired namespace remains classified: $it") }
        listOf("ftblibrary", "ftbbackups2").forEach { assertTrue(namespaces.has(it), "shared namespace is missing: $it") }

        listOf(
            "mods/better-content-quests-1.0.0.jar", "mods/ftb-quests-forge.pw.toml",
            "mods/ftb-teams-forge.pw.toml", "mods/ftb-xmod-compat.pw.toml",
            "mods/ftb-filter-system.pw.toml", "modpack questbook notes",
        ).forEach { assertTrue(!Files.exists(root.resolve(it)), "retired quest content remains: $it") }
        listOf("mods/ftb-library-forge.pw.toml", "mods/ftb-backups-2.pw.toml", "config/ftbbackups2.json")
            .forEach { assertTrue(Files.isRegularFile(root.resolve(it)), "shared FTB content is missing: $it") }

        val questConfig = root.resolve("config/ftbquests")
        if (Files.exists(questConfig)) {
            Files.walk(questConfig).use { files ->
                assertTrue(files.noneMatch(Files::isRegularFile), "retired quest configuration remains")
            }
        }
        val options = Files.readString(root.resolve("options.txt"))
        assertTrue(!options.contains("key_key.ftbteams.") && !options.contains("key_key.ftbquests."))
        Files.list(root.resolve("config/rbp/block_definitions")).use { definitions ->
            definitions.filter(Files::isRegularFile).forEach { definition ->
                val text = Files.readString(definition)
                retiredNamespaces.forEach { namespace ->
                    assertTrue(!text.contains("$namespace:"), "retired block definition in $definition: $namespace")
                }
            }
        }
    }

    @Test
    fun newlyIntegratedArenaNamespacesAreClassified() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val namespaces = jacksonObjectMapper().readTree(root.resolve("kubejs/config/crafting_policy.json").toFile())
            .path("namespaces")
        assertEquals("content", namespaces.path("cataclysm").path("primary_role").asText())
        assertEquals("infrastructure", namespaces.path("lionfishapi").path("primary_role").asText())
        assertEquals("world", namespaces.path("the_deep_void").path("primary_role").asText())
        listOf("cataclysm", "lionfishapi", "the_deep_void").forEach { namespace ->
            assertTrue(namespaces.path(namespace).path("support_state").isTextual,
                "$namespace lacks a crafting policy support state")
        }
    }

    @Test
    fun reflectionAllowlistIsExactAndSourceAuditRejectsUnlistedUse(@TempDir root: Path) {
        val workspace = root.resolve("workspace")
        val repository = workspace.resolve("mod_source/example")
        val source = repository.resolve("src/main/java/example/Probe.java").also { it.parent.createDirectories() }
        source.writeText("package example; final class Probe { Class<?> load() throws Exception { return Class\n.forName(\"example.Target\"); } }")
        repository.resolve("src/test/java/example/BoundaryTest.java").also {
            it.parent.createDirectories()
            it.writeText("package example; final class BoundaryTest { String forbidden = \"Class.forName(\"; }")
        }
        assertEquals(1, sourceReflectionViolations(workspace, listOf("example"), emptySet()).size)
        val allowance = ReflectionAllowance("example", "src/main/java/example/Probe.java")
        assertTrue(sourceReflectionViolations(workspace, listOf("example"), setOf(allowance)).isEmpty())
        assertTrue(unusedReflectionAllowances(workspace, setOf(allowance)).isEmpty())
        val staleAllowance = ReflectionAllowance("example", "src/test/java/example/BoundaryTest.java")
        assertEquals(listOf(staleAllowance), unusedReflectionAllowances(workspace, setOf(staleAllowance)))

        val allowlist = root.resolve("allowlist.txt")
        allowlist.writeText("example\tsrc/main/java/example/Probe.java\n")
        assertEquals(setOf(allowance), readReflectionAllowlist(allowlist))
    }

    @Test
    fun activeReflectionAllowlistMatchesWorkspaceSources() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val workspace = root.parent
        val allowances = readReflectionAllowlist(root.resolve("gradle/reflection-allowlist.txt"))
        val customRepositories = Files.list(workspace.resolve("mod_source")).use { stream ->
            stream.filter { Files.isDirectory(it) && it.resolve(".git").toFile().isDirectory }
                .map { it.fileName.toString() }.sorted().toList()
        }
        val stale = allowances.filterNot { allowance ->
            allowance.repository in customRepositories && Files.isRegularFile(
                workspace.resolve("mod_source").resolve(allowance.repository).resolve(allowance.sourcePath),
            )
        }
        assertEquals(emptyList<ReflectionAllowance>(), stale.sortedWith(compareBy(ReflectionAllowance::repository, ReflectionAllowance::sourcePath)))
        assertEquals(emptyList<ReflectionAllowance>(), unusedReflectionAllowances(workspace, allowances))
        assertEquals(emptyList<String>(), sourceReflectionViolations(workspace, customRepositories, allowances))
    }

    @Test
    fun stagedBytecodeAuditRejectsReflectiveInvocation(@TempDir root: Path) {
        val repository = root.resolve("example")
        repository.resolve("src/main/java/example/Probe.java").also {
            it.parent.createDirectories()
            it.writeText("package example; final class Probe {}")
        }
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_FINAL or Opcodes.ACC_SUPER, "example/Probe", null, "java/lang/Object", null)
        writer.visitMethod(Opcodes.ACC_STATIC, "load", "()Ljava/lang/Class;", null, arrayOf("java/lang/ClassNotFoundException")).also { method ->
            method.visitCode()
            method.visitLdcInsn("example.Target")
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false)
            method.visitInsn(Opcodes.ARETURN)
            method.visitMaxs(1, 0)
            method.visitEnd()
        }
        writer.visitEnd()
        val jar = root.resolve("example.jar")
        ZipOutputStream(Files.newOutputStream(jar)).use { output ->
            output.putNextEntry(ZipEntry("example/Probe.class"))
            output.write(writer.toByteArray())
            output.closeEntry()
        }
        assertEquals(1, bytecodeReflectionViolations("example", repository, jar, emptySet()).size)
        val allowance = ReflectionAllowance("example", "src/main/java/example/Probe.java")
        assertTrue(bytecodeReflectionViolations("example", repository, jar, setOf(allowance)).isEmpty())
    }

    @Test
    fun releaseJarDetectionIgnoresDependencyModIds(@TempDir root: Path) {
        val jar = root.resolve("fixture.jar")
        zip(jar, mapOf("META-INF/mods.toml" to """
            modLoader="javafml"
            [[mods]]
            modId="declared_mod"
            [[dependencies.declared_mod]]
            modId="dependency_mod"
        """.trimIndent()))
        assertTrue(jarDeclaresMod(jar, "declared_mod"))
        assertTrue(!jarDeclaresMod(jar, "dependency_mod"))
    }

    @Test
    fun freshReleaseEmbedsAndReadsCustomSourceIdentity(@TempDir root: Path) {
        val mod = ActiveMod("fixture", "fixture_mod", "fixture.jar", emptyList())
        val jar = root.resolve(mod.artifact)
        zip(jar, mapOf(
            "META-INF/mods.toml" to """
                modLoader="javafml"
                [[mods]]
                modId="fixture_mod"
            """.trimIndent(),
            "fixture/data.txt" to "kept",
        ))

        annotateJar(jar, mod, "commit-1")

        assertEquals("commit-1", readSourceCommit(jar, mod))
        assertTrue(jarDeclaresMod(jar, mod.modId))
        JarFile(jar.toFile()).use { result ->
            assertTrue(result.getJarEntry("fixture/data.txt") != null)
            assertTrue(result.getJarEntry("META-INF/better-content-source.properties") != null)
        }
    }

    @Test
    fun sourceRevisionCheckDistinguishesChangedAndLegacyJars() {
        assertEquals("same", sourceUpdateStatus("commit-1", "commit-1"))
        assertEquals("changed", sourceUpdateStatus("commit-2", "commit-1"))
        assertEquals("baseline-missing", sourceUpdateStatus("commit-1", null))
    }

    @Test
    fun fullDebugRebuildsAndVerifiesSourceIdenticalJar(@TempDir workspace: Path) {
        val root = workspace.resolve("better-content-modpack").also(Files::createDirectories)
        val repository = workspace.resolve("mod_source/fixture").also(Files::createDirectories)
        val mod = ActiveMod("fixture", "fixture_mod", "fixture.jar", listOf("verifyFull", "stageRuntimeJar"))
        fun git(vararg args: String): String {
            val process = ProcessBuilder("git", *args).directory(repository.toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT).start()
            val result = process.inputStream.bufferedReader().readText().trim()
            assertEquals(0, process.waitFor())
            return result
        }
        git("init", "-q")
        // Authored source identity excludes declared build outputs, not arbitrary new files.
        Files.writeString(repository.resolve(".gitignore"), "build/\ninvoked-tasks.txt\n")
        git("add", ".gitignore")
        git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "--allow-empty", "-qm", "fixture")
        val commit = git("rev-parse", "HEAD")
        val jarContents = mapOf("META-INF/mods.toml" to "[[mods]]\nmodId=\"fixture_mod\"\n")
        val bundled = root.resolve("mods/fixture.jar")
        zip(bundled, jarContents)
        annotateJar(bundled, mod, commit)
        zip(repository.resolve("build/libs/fixture.jar"), jarContents)
        val wrapper = repository.resolve("gradlew")
        wrapper.writeText("#!/bin/sh\nprintf '%s\\n' \"\$@\" > invoked-tasks.txt\n")
        assertTrue(wrapper.toFile().setExecutable(true))
        val evidence = root.resolve("generated/test-evidence/fixture/release").also(Files::createDirectories)
        val staging = evidence.resolve("staged-jars").also(Files::createDirectories)
        val sourceUpdate = SourceUpdate("fixture", mod.artifact, commit, commit, "same")

        val rebuilt = build(root, workspace, evidence, staging, mod, sourceUpdate, skipTests = false, forceRebuild = true)

        assertEquals("rebuilt", rebuilt.mode)
        assertEquals(listOf("--no-daemon", "clean", "verifyFull", "stageRuntimeJar"),
            Files.readAllLines(repository.resolve("invoked-tasks.txt")))
        assertEquals(commit, readSourceCommit(rebuilt.jar, mod))
        assertEquals(commit, readSourceCommit(bundled, mod))

        val deployedBytes = Files.readAllBytes(bundled)
        wrapper.writeText("#!/bin/sh\nexit 7\n")
        assertThrows(IllegalArgumentException::class.java) {
            build(root, workspace, evidence, staging, mod, sourceUpdate, skipTests = false, forceRebuild = true)
        }
        assertTrue(Files.readAllBytes(bundled).contentEquals(deployedBytes))
    }

    @Test
    fun candidateLocatorRejectsPartialDeclaredRelease(@TempDir root: Path) {
        Files.writeString(root.resolve("pack.toml"), "version = \"Build 2\"\n")
        zip(root.resolve("dist/build-1/client/better-content.zip"), mapOf("manifest.json" to "{}"))
        zip(root.resolve("dist/build-2/server/better-content.zip"), mapOf("server/eula.txt" to "eula=false"))

        // Full tiers set a real binding. Discovery's synthetic fixture must neither
        // consume that binding nor mutate the parent JVM's production environment.
        val classpath = listOf(CandidateDiscoveryFixtureProbe::class.java, CandidateLocator::class.java, kotlin.Unit::class.java)
            .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }.distinct().joinToString(java.io.File.pathSeparator)
        val output = BoundedCommand.capture(root, listOf("env", "-u", "BC_CANDIDATE_BINDING", "-u", "BC_PACK_TEST_HANDOFF",
            Path.of(System.getProperty("java.home"), "bin/java").toString(), "-Dbc.repo.root=${System.getProperty("bc.repo.root")}", "-cp", classpath,
            CandidateDiscoveryFixtureProbe::class.java.name, root.toString()), Duration.ofSeconds(15))
        assertTrue(output.contains("current Build 2 candidate directories"), output)
    }

    @Test
    fun hashGuardDetectsChangedCandidate(@TempDir root: Path) {
        val file = root.resolve("candidate.zip")
        file.writeText("before")
        val before = Hashes.sha256(file)
        file.writeText("after")
        assertTrue(before != Hashes.sha256(file))
    }

    @Test
    fun immutableHandoffMustMatchCoordinatorAndCandidateHashes(@TempDir root: Path) {
        val client = root.resolve("release/client/better-content.zip").also {
            it.parent.createDirectories()
            it.writeText("client")
        }
        val server = root.resolve("release/server/better-content.zip").also {
            it.parent.createDirectories()
            it.writeText("server")
        }
        val pair = CandidatePair(root.resolve("release"), client, server, Hashes.sha256(client), Hashes.sha256(server))
        val handoff = root.resolve("handoff.json")
        val mapper = jacksonObjectMapper()
        val repository = Path.of(System.getProperty("bc.repo.root"))
        BoundedCommand.capture(repository, listOf("python3", "-B", repository.resolve("scripts/workspace_coordination.py").toString(),
            "--root", root.toString(), "task-register", "--task", "handoff-fixture", "--owner", "fixture", "--operation", "pack-test:dist"), Duration.ofSeconds(30))
        mapper.writeValue(handoff.toFile(), mapOf(
            "schema" to "bc.pack_test_handoff.v1",
            "task_id" to "handoff-fixture", "task_generation" to 1, "task_owner" to "fixture", "selector" to "dist",
            "request_id" to "request-1",
            "producer" to mapOf("agent" to "workspace_coord"),
            "authorization" to mapOf("explicit" to true),
            "callback" to mapOf("agent" to "fixture", "pane_id" to "w1:p1"),
            "modpack" to mapOf("head" to "head-1", "status" to emptyList<String>()),
            "repositories" to emptyList<Any>(),
            "validations" to emptyList<Any>(),
            "artifacts" to emptyList<Any>(),
            "dependencies" to emptyList<Any>(),
            "scenarios" to emptyList<Any>(),
            "prior_evidence" to emptyList<Any>(),
            "candidate" to mapOf(
                "client" to mapOf("path" to client.toString(), "sha256" to pair.clientSha256),
                "server" to mapOf("path" to server.toString(), "sha256" to pair.serverSha256),
            ),
        ))
        assertEquals("request-1", PackTestHandoff.validate(handoff, pair, root))
        mapper.writeValue(handoff.toFile(), mapper.readTree(handoff.toFile()).deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().apply {
            withObject("candidate").withObject("server").put("sha256", "bad")
        })
        assertThrows(IllegalArgumentException::class.java) { PackTestHandoff.validate(handoff, pair, root) }
    }

    @Test
    fun dimensionSmokeDiscoversDynamicAndFontTargets(@TempDir root: Path) {
        val dimensions = root.resolve("dimensions.json")
        dimensions.writeText("""{
          "schema": "bc.dimensions.v1",
          "complete": true,
          "loaded_dimensions": ["aether:the_aether", "ae2:spatial_storage", "bloodmagic:dungeon", "creatingspace:mars", "minecraft:overworld", "minecraft:the_end"],
          "rocket_accessible_dimensions": ["minecraft:overworld", "creatingspace:mars"]
        }""")
        val fonts = root.resolve("fonts").also { it.createDirectories() }
        fonts.resolve("aether.json").writeText("""{"enabled":true,"targetDimension":"aether:the_aether"}""")
        val targets = DimensionSmokePlan.discover(dimensions, fonts)
        assertEquals(listOf("ae2:spatial_storage", "aether:the_aether", "bloodmagic:dungeon", "creatingspace:mars", "minecraft:overworld", "minecraft:the_end"), targets.map { it.id })
        assertEquals(setOf("creatingspace"), targets.single { it.id == "creatingspace:mars" }.sources)
        assertTrue(DimensionSmokePlan.requiresFontTravel("the_bumblezone:the_bumblezone"))
        assertTrue(!DimensionSmokePlan.requiresFontTravel("minecraft:the_nether"))
        assertEquals(20.0, DimensionSmokePlan.parseOverallTps("Overall: Mean tick time: 2.1 ms. Mean TPS: 20.000"))
        assertEquals(
            listOf(100_000 to 100_000, 100_128 to 100_000, 100_000 to 100_128),
            DimensionSmokePlan.positions,
        )
    }

    @Test
    fun runtimeSnapshotRequiresCompleteConsistentDocuments(@TempDir root: Path) {
        val mapper = jacksonObjectMapper()
        val names = listOf("recipes.json", "registries.json", "tags.json", "mods.json", "loot.json", "trades.json", "worldgen.json", "dimensions.json", "lighting.json")
        mapper.writeValue(root.resolve("snapshot.json").toFile(), mapOf(
            "schema" to "bc.runtime_dump_completion.v3",
            "complete" to true,
            "evidence_state" to "complete",
            "files" to names,
            "snapshot_id" to "snapshot-1",
            "surfaces" to mapOf("recipes" to mapOf("complete_for_contract" to true)),
        ))
        names.forEach { name ->
            val data = mutableMapOf<String, Any>("snapshot_id" to "snapshot-1")
            if (name == "recipes.json") {
                val outputs = mapOf(
                    "blaze_powder_from_rod" to ("minecraft:blaze_powder" to 1),
                    "certus_quartz_dust_from_gem" to ("ae2:certus_quartz_dust" to 1),
                    "coal_dust" to ("bloodmagic:coalsand" to 4),
                    "datura" to ("occultism:datura_seeds" to 2),
                    "end_stone_dust" to ("occultism:crushed_end_stone" to 1),
                    "iesnium_dust" to ("occultism:iesnium_dust" to 2),
                    "iesnium_dust_from_ingot" to ("occultism:iesnium_dust" to 1),
                    "iesnium_dust_from_raw" to ("occultism:iesnium_dust" to 2),
                    "iesnium_dust_from_raw_block" to ("occultism:iesnium_dust" to 18),
                    "iridium_dust_from_ingot" to ("chemlib:iridium_dust" to 1),
                    "redstone_dust" to ("minecraft:redstone" to 4),
                    "tungsten_dust_from_ingot" to ("chemlib:tungsten_dust" to 1),
                )
                data.putAll(mapOf("complete" to true, "partial_count" to 0, "error_count" to 0,
                    "recipes" to outputs.map { (id, output) -> mapOf("id" to "occultism:crushing/$id",
                        "outputs" to listOf(mapOf("kind" to "item", "id" to output.first, "count" to output.second))) }))
            }
            if (name == "tags.json") data["item_tags"] = mapOf(
                "forge:ores" to listOf("minecraft:iron_ore"),
                "c:ores" to listOf("minecraft:iron_ore"))
            mapper.writeValue(root.resolve(name).toFile(), data)
        }
        assertEquals("snapshot-1", RuntimeSnapshotValidator.validate(root))

        mapper.writeValue(root.resolve("tags.json").toFile(), mapOf("snapshot_id" to "other"))
        assertThrows(IllegalArgumentException::class.java) { RuntimeSnapshotValidator.validate(root) }
    }

    @Test
    fun startupTimingsNormalizeUnitsAndPreserveRecordProvenance(@TempDir root: Path) {
        val durations = listOf("1102 ms", "1.102 s", "1.102 min", "1.102 h")
        val seconds = listOf(1.102, 1.102, 66.12, 3967.2)
        val threads = listOf("main", "Render thread", "main", "Render thread")
        val notices = durations.mapIndexed { index, duration ->
            "[12:00:00] [${threads[index]}/WARN] [net.minecraftforge.fml.DeferredWorkQueue]: " +
                "Mod 'startup_mod$index' took $duration to run a deferred task."
        }
        val log = root.resolve("startup.log").also {
            it.writeText((listOf("[12:00:00] [main/INFO] ready") + notices).joinToString("\n", postfix = "\n"))
        }

        val timings = LogPolicy.startupTimings(listOf(log))
        assertEquals(durations.size, timings.size)
        timings.forEachIndexed { index, timing ->
            assertEquals(log, timing.path)
            assertEquals(index + 2, timing.line)
            assertEquals(notices[index], timing.text)
            assertEquals("startup_mod$index", timing.mod)
            assertEquals(threads[index], timing.thread)
            assertEquals(seconds[index], timing.seconds, 1e-9)
        }
        assertTrue(LogPolicy.findings(listOf(log)).isEmpty())
    }

    @Test
    fun startupTimingsReportRepeatsOtherModsAndLongDurationsInEverySuite(@TempDir root: Path) {
        val mods = listOf("iceandfire", "thalassophobia", "ae2", "jsonthings", "adpother", "adchimneys", "adlods", "othermod")
        val notices = mods.flatMap { mod ->
            listOf("main", "Render thread").map { thread ->
                "[12:00:00] [$thread/WARN] [net.minecraftforge.fml.DeferredWorkQueue]: " +
                    "Mod '$mod' took 900.125 h to run a deferred task."
            }
        }.let { records -> records + List(5) { records.first() } }
        val paths = listOf("unscoped/startup.log", "singleplayer/client.log", "multiplayer/server.log", "target-join/client-1.log")
            .map { name -> root.resolve(name).also {
                it.parent.createDirectories()
                it.writeText(notices.joinToString("\n", postfix = "\n"))
            } }

        assertTrue(LogPolicy.findings(paths).isEmpty())
        val timings = LogPolicy.startupTimings(paths)
        assertEquals(paths.size * notices.size, timings.size)
        paths.forEach { path ->
            val records = timings.filter { it.path == path }
            assertEquals((1..notices.size).toList(), records.map { it.line })
            assertEquals(notices, records.map { it.text })
            assertEquals(mods.toSet(), records.map { it.mod }.toSet())
            records.forEach { assertEquals(900.125 * 3600.0, it.seconds, 1e-9) }
        }
    }

    @Test
    fun startupTimingsRejectMalformedNonPositiveAndNonFiniteDurationsOrUnits(@TempDir root: Path) {
        fun notice(duration: String, unit: String = "s") =
            "[12:00:00] [main/WARN] [net.minecraftforge.fml.DeferredWorkQueue]: " +
                "Mod 'example' took $duration $unit to run a deferred task."
        val invalid = listOf("", "0", "0.000", "-1", "NaN", "Infinity", "1.2.3", "9".repeat(400)).map { notice(it) } +
            listOf("", "ns", "seconds", "minutes", "hours", "MS").map { notice("1.102", it) } +
            notice("9".repeat(307), "h")
        val log = root.resolve("malformed-startup.log").also {
            it.writeText(invalid.joinToString("\n", postfix = "\n"))
        }

        assertTrue(LogPolicy.startupTimings(listOf(log)).isEmpty())
        assertTrue(LogPolicy.findings(listOf(log)).isEmpty(), "malformed timing advice is not a failed operation")
    }

    @Test
    fun startupTimingsRejectWrongLoggerThreadOrSeverity(@TempDir root: Path) {
        val notice = "[12:00:00] [main/WARN] [net.minecraftforge.fml.DeferredWorkQueue]: " +
            "Mod 'example' took 1.102 s to run a deferred task."
        val invalid = listOf(
            notice.replace("net.minecraftforge.fml.DeferredWorkQueue", "example.DeferredWorkQueue"),
            notice.replace("net.minecraftforge.fml.DeferredWorkQueue", "net.minecraftforge.fml.DeferredWorkQueue.Other"),
            notice.replace("main/WARN", "Server thread/WARN"),
            notice.replace("main/WARN", "modloading-worker-0/WARN"),
            notice.replace("main/WARN", "render thread/WARN"),
            notice.replace("main/WARN", "main/ERROR"),
            notice.replace("main/WARN", "main/FATAL"),
        )
        val log = root.resolve("wrong-startup-envelope.log").also {
            it.writeText(invalid.joinToString("\n", postfix = "\n"))
        }

        assertTrue(LogPolicy.startupTimings(listOf(log)).isEmpty())
        assertEquals(listOf(6, 7), LogPolicy.findings(listOf(log)).map { it.line })
    }

    @Test
    fun startupTimingsRequireValidModNamespacesRatherThanAModAllowlist(@TempDir root: Path) {
        fun notice(mod: String) = "[12:00:00] [Render thread/WARN] [net.minecraftforge.fml.DeferredWorkQueue]: " +
            "Mod '$mod' took 1.102 min to run a deferred task."
        val validMods = listOf("a", "new_mod9", "previously_unknown_mod")
        val valid = root.resolve("valid-startup-namespaces.log").also {
            it.writeText(validMods.joinToString("\n", postfix = "\n", transform = ::notice))
        }
        val invalidMods = listOf("", "Uppercase", "9mod", "_mod", "hyphen-mod", "namespace:mod", "mod name")
        val invalid = root.resolve("invalid-startup-namespaces.log").also {
            it.writeText(invalidMods.joinToString("\n", postfix = "\n", transform = ::notice))
        }

        assertEquals(validMods, LogPolicy.startupTimings(listOf(valid)).map { it.mod })
        assertTrue(LogPolicy.findings(listOf(valid)).isEmpty())
        assertTrue(LogPolicy.startupTimings(listOf(invalid)).isEmpty())
        assertTrue(LogPolicy.findings(listOf(invalid)).isEmpty())
    }

    @Test
    fun c2meSerializesFeaturePlacementForCataclysmStructureCompatibility() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val config = Files.readString(root.resolve("config/c2me.toml"))
        val threadedWorldGen = config.substringAfter("[threadedWorldGen]").substringBefore("[ioSystem]")

        assertTrue(threadedWorldGen.contains("enabled = \"true\""))
        assertTrue(threadedWorldGen.contains("allowThreadedFeatures = \"false\""))
    }

    @Test
    fun startupTimingNoticesDoNotHideSeparateNativeFailures(@TempDir root: Path) {
        val notice = "[12:00:00] [Render thread/WARN] [net.minecraftforge.fml.DeferredWorkQueue]: " +
            "Mod 'othermod' took 900.125 h to run a deferred task."
        val errors = listOf(
            "[12:00:00] [main/ERROR] [net.minecraftforge.fml.javafmlmod.FMLModContainer]: " +
                "java.lang.NullPointerException: Registry Object not present: example:missing",
            "[12:00:00] [main/ERROR] [net.minecraftforge.fml.javafmlmod.FMLModContainer]: " +
                "java.lang.ExceptionInInitializerError: failed",
            "[12:00:00] [main/FATAL] [net.minecraftforge.fml.ModLoader]: fatal error has been detected",
            "ReportedException: exception in server tick loop",
            "java.lang.OutOfMemoryError: Java heap space",
        )
        val log = root.resolve("native-failures-after-startup.log").also {
            it.writeText((listOf(notice) + errors).joinToString("\n", postfix = "\n"))
        }

        val timing = LogPolicy.startupTimings(listOf(log)).single()
        assertEquals(notice, timing.text)
        assertEquals(1, timing.line)
        assertEquals("othermod", timing.mod)
        val findings = LogPolicy.findings(listOf(log))
        assertEquals((2..(errors.size + 1)).toList(), findings.map { it.line })
        assertEquals(errors, findings.map { it.text })
        assertThrows(IllegalArgumentException::class.java) { LogPolicy.requireClean(listOf(log)) }
    }

    @Test
    fun extraTimingPayloadIsNotTelemetryAndDoesNotHideAnOperationFailure(@TempDir root: Path) {
        val timing = "[12:00:00] [main/WARN] [net.minecraftforge.fml.DeferredWorkQueue]: " +
            "Mod 'example' took 1.102 s to run a deferred task."
        val records = listOf("$timing extra advice", "$timing Registry Object not present: example:missing")
        val log = root.resolve("timing-payload.log").also { it.writeText(records.joinToString("\n")) }
        assertTrue(LogPolicy.startupTimings(listOf(log)).isEmpty())
        assertEquals(listOf(2), LogPolicy.findings(listOf(log)).map { it.line })
    }

    @Test
    fun warningVolumeAndIncidentalWordingDoNotDetermineCorrectness(@TempDir root: Path) {
        val warnings = listOf(
            "[12:00:00] [Render thread/WARN] [untamedwilds.UntamedWilds]: There's no species provided for the EntityType",
            "[12:00:00] [C2ME worker #5/WARN] [net.minecraft.server.level.WorldGenRegion]: Tried to access a block entity before it was created. BlockPos{x=1, y=2, z=3}",
            "[12:00:00] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine]: Unable to play empty soundEvent: minecraft:entity.salmon.ambient",
            "[12:00:00] [main/WARN] [new.Logger]: Previously unseen advisory",
        )
        val log = root.resolve("warnings.log").also {
            it.writeText(warnings.joinToString("\n", postfix = "\n").repeat(3500))
        }
        assertTrue(LogPolicy.findings(listOf(log)).isEmpty())
        log.writeText(warnings.joinToString("\n", postfix = "\n") { it.replace("/WARN]", "/ERROR]") })
        assertEquals((1..warnings.size).toList(), LogPolicy.findings(listOf(log)).map { it.line })
    }

    @Test
    fun failedOperationsBlockAtEverySeverityAndWithoutAnEnvelope(@TempDir root: Path) {
        val failures = listOf(
            "Error loading KubeJS script", "KubeJS errors found [2]", "Registry registration failed",
            "Failed to save world", "Corrupted chunk data", "java.lang.AssertionError: invariant",
            "java.lang.OutOfMemoryError: Java heap space", "ReportedException: server tick",
        )
        val records = listOf("INFO", "WARN", "ERROR", "FATAL").flatMap { severity ->
            failures.map { "[12:00:00] [Server thread/$severity] [example.Logger]: $it" }
        } + failures
        val log = root.resolve("operations.log").also { it.writeText(records.joinToString("\n")) }
        assertEquals((1..records.size).toList(), LogPolicy.findings(listOf(log)).map { it.line })
        assertThrows(IllegalArgumentException::class.java) { LogPolicy.requireClean(listOf(log)) }
    }

    @Test
    fun errorEnvelopeCannotBeHiddenByKnownWarningPayloadOrFixturePath(@TempDir root: Path) {
        val payloads = listOf(
            "Forcing chunk load: [-79, 44]",
            "Detected setBlock in a far chunk currently generating: ResourceKey[minecraft:worldgen/placed_feature / natures_spirit:marsh_water_placed]",
            "WARNING : glfwInit took 1.265897709 seconds to start.",
            "Cleanup: Invalid name parameter.",
            "handleDisconnection() called twice",
        )
        val log = root.resolve("singleplayer/fixture/client-1/logs/latest.log").also {
            it.parent.createDirectories()
            it.writeText(payloads.joinToString("\n") { message ->
                "[12:00:00] [Render thread/ERROR] [example.Logger]: $message"
            })
        }
        assertEquals(payloads.size, LogPolicy.findings(listOf(log)).size)
        assertEquals(LogPolicy.Record("WARN", "DH-LOD Builder Thread[0]", "example.Logger", "advisory"),
            LogPolicy.parseRecord("[12:00:00] [DH-LOD Builder Thread[0]/WARN] [example.Logger]: advisory"))
        log.writeText("[12:00:00] [main/INFO] [example.Logger]: quoted /ERROR] is payload, not severity")
        assertTrue(LogPolicy.findings(listOf(log)).isEmpty())
        log.writeText("[12:00:00] [main/ERROR] [example.Logger]: quoted [worker/WARN] [other.Logger]: advisory")
        assertEquals(1, LogPolicy.findings(listOf(log)).size,
            "a quoted WARN envelope must not replace the outer ERROR severity")
    }

    @Test
    fun managedProcessCapturesOutputAndStops(@TempDir root: Path) {
        val log = root.resolve("process.log")
        ManagedProcess("fixture", listOf("sh", "-c", "echo ready; while :; do sleep 1; done"), root, log).use { process ->
            process.waitForLog(Regex("ready"), Duration.ofSeconds(5), "fixture readiness")
            assertTrue(process.alive)
        }
    }

    @Test
    fun packRunnerMutexPublishesOwnerAndFailsFastForContenders(@TempDir state: Path) {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val lock = root.resolve("pack-test-lock.main.kts")
        val ready = state.resolve("ready")
        val release = state.resolve("release")
        val first = ProcessBuilder(
            lock.toString(), "run", "test", "server", "--",
            "sh", "-c", "test -f '${state.resolve("owner.json")}' && touch '$ready' && while [ ! -f '$release' ]; do sleep 0.1; done",
        ).apply {
            environment()["BC_PACK_TEST_STATE_ROOT"] = state.toString()
            environment().remove("BC_PACK_TEST_LOCK_TOKEN")
            redirectErrorStream(true)
            redirectOutput(state.resolve("holder.log").toFile())
        }.start()
        val holderTracker = ProcessTracker(first.toHandle())
        try {
            // Cold Kotlin script compilation must not expire the holder's lease.
            val deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos()
            while (!Files.exists(ready) && first.isAlive && System.nanoTime() < deadline) Thread.sleep(50)
            assertTrue(Files.exists(ready), "first runner never acquired the mutex; see holder.log")

            val contender = ProcessBuilder(lock.toString(), "run", "test", "candidate", "--", "true")
                .apply {
                    environment()["BC_PACK_TEST_STATE_ROOT"] = state.toString()
                    environment().remove("BC_PACK_TEST_LOCK_TOKEN")
                    redirectErrorStream(true)
                    redirectOutput(state.resolve("contender.log").toFile())
                }.start()
            val contenderTracker = ProcessTracker(contender.toHandle())
            try {
                assertTrue(contender.waitFor(45, java.util.concurrent.TimeUnit.SECONDS), "contender timed out; see contender.log")
                assertEquals(75, contender.exitValue())
            } finally {
                contenderTracker.stop(Duration.ofSeconds(5))
            }
        } finally {
            release.writeText("contender finished\n")
            first.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            holderTracker.stop(Duration.ofSeconds(5))
        }
        assertEquals(0, first.exitValue())
        assertTrue(Files.notExists(state.resolve("owner.json")))
    }

    @Test
    fun coordinatorQueueRejectsUnregisteredLiteralWorkspaceIdentity(@TempDir state: Path) {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val queue = root.resolve("pack-test-queue.main.kts")
        val handoff = state.resolve("handoff.json")
        handoff.writeText("""{
          "schema": "bc.pack_test_handoff.v1",
          "request_id": "request-1",
          "producer": {"agent": "workspace_coord"},
          "authorization": {"explicit": true},
          "callback": {"agent": "fixture", "pane_id": "w1:p1"},
          "modpack": {"head": "head-1", "status": []},
          "repositories": [],
          "validations": [],
          "artifacts": [],
          "dependencies": [],
          "scenarios": [],
          "prior_evidence": [],
          "selector": "dist",
          "candidate": {
            "client": {"path": "/candidate/client.zip", "sha256": "${"a".repeat(64)}"},
            "server": {"path": "/candidate/server.zip", "sha256": "${"b".repeat(64)}"}
          }
        }""")
        fun request(): Int = ProcessBuilder(queue.toString(), "request", handoff.toString())
            .apply { environment()["BC_PACK_TEST_STATE_ROOT"] = state.resolve("coordination").toString() }
            .start().waitFor()
        assertTrue(request() != 0)
        assertEquals(0L, Files.list(state.resolve("coordination/queued")).use { it.count() })
    }

    private fun zip(path: Path, entries: Map<String, String>) {
        path.parent.createDirectories()
        ZipOutputStream(Files.newOutputStream(path)).use { output ->
            entries.forEach { (name, value) ->
                output.putNextEntry(ZipEntry(name))
                output.write(value.toByteArray())
                output.closeEntry()
            }
        }
    }
}
