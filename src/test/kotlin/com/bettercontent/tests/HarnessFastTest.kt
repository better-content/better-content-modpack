package com.bettercontent.tests

import com.bettercontent.tests.release.ActiveMod
import com.bettercontent.tests.release.annotateJar
import com.bettercontent.tests.release.packageResolveCommand
import com.bettercontent.tests.release.jarDeclaresMod
import com.bettercontent.tests.release.readSourceCommit
import com.bettercontent.tests.release.sourceUpdateStatus
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
import kotlin.io.path.writeText

@Tag("fast")
class HarnessFastTest {
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
        assertTrue(facade.contains("validateFreshEvidence(suite, startedAt)"))
    }

    @Test
    fun packwizHashRefreshBelongsOnlyToTheFrugalPhase() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val facade = Files.readString(root.resolve("test.main.kts"))
        val packager = Files.readString(root.resolve("package.sh"))
        val release = Files.readString(root.resolve("src/main/kotlin/com/bettercontent/tests/release/ReleasePipeline.kt"))

        assertTrue(facade.contains("run(\"packwiz\", \"refresh\")"))
        assertTrue(!packager.contains("packwiz refresh"))
        assertTrue(!release.contains("listOf(\"packwiz\", \"refresh\")"))
        assertTrue(release.contains("root.resolve(\"test.main.kts\").toString(), \"frugal\""))
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
    fun activeReleaseInventoryIsUniqueAndMatchesBundledArtifacts() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val document = jacksonObjectMapper().readTree(root.resolve("gradle/active-custom-mods.json").toFile())
        assertEquals("bc.active_custom_mods.v1", document.path("schema").asText())
        val mods = document.path("mods")
        assertEquals(33, mods.size())
        val repositories = mods.map { it.path("repository").asText() }.toSet()
        assertEquals(33, repositories.size)
        assertEquals(33, mods.map { it.path("modId").asText() }.toSet().size)
        mods.forEach { mod ->
            assertTrue(Files.isRegularFile(root.resolve("mods").resolve(mod.path("artifact").asText())))
            assertTrue(mod.path("tasks").isArray && mod.path("tasks").size() > 0)
            assertTrue(mod.path("dependsOn").let { it.isMissingNode || (it.isArray && it.all { dependency -> dependency.asText() in repositories }) })
        }
        fun dependencies(repository: String) = mods.single { it.path("repository").asText() == repository }
            .path("dependsOn").map { it.asText() }
        assertEquals(listOf("heat-sync"), dependencies("latent-chemlib"))
        assertEquals(listOf("dimension-drink"), dependencies("better-content-economy"))
        assertEquals(
            listOf("dynamic-survival-hud", "heat-sync", "latent-chemlib"),
            dependencies("better-content-fixes"),
        )
        assertEquals(listOf("world-lifecycle-manager"), dependencies("class-selector"))
        assertEquals(listOf("downed-player-revival"), dependencies("depth-director"))
        assertEquals(listOf("downed-player-revival"), dependencies("pillager-campaigns"))
        assertEquals(listOf("downed-player-revival"), dependencies("player-traces"))
        assertEquals(
            listOf(
                "arcane-chunk-loaders", "better-content-economy", "better-content-fixes",
                "dimension-drink", "downed-player-revival", "heat-sync", "pillager-campaigns",
                "player-traces", "realistic-ores", "rpg-stats", "settlement-roads",
                "systemic-salience", "water-survival", "world-lifecycle-manager",
            ),
            dependencies("better-content-threads"),
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
    fun candidateLocatorRejectsMismatchedReleaseDirectories(@TempDir root: Path) {
        zip(root.resolve("dist/a/client/better-content.zip"), mapOf("manifest.json" to "{}"))
        zip(root.resolve("dist/b/server/better-content.zip"), mapOf("server/eula.txt" to "eula=false"))

        val error = assertThrows(IllegalArgumentException::class.java) { CandidateLocator.locate(root) }
        assertTrue(error.message!!.contains("different releases"))
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
        mapper.writeValue(handoff.toFile(), mapOf(
            "schema" to "bc.pack_test_handoff.v1",
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
        assertEquals("request-1", PackTestHandoff.validate(handoff, pair))
        mapper.writeValue(handoff.toFile(), mapper.readTree(handoff.toFile()).deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().apply {
            withObject("candidate").withObject("server").put("sha256", "bad")
        })
        assertThrows(IllegalArgumentException::class.java) { PackTestHandoff.validate(handoff, pair) }
    }

    @Test
    fun dimensionSmokeDiscoversDynamicAndFontTargets(@TempDir root: Path) {
        val dimensions = root.resolve("dimensions.json")
        dimensions.writeText("""{
          "schema": "bc.dimensions.v1",
          "complete": true,
          "loaded_dimensions": ["aether:the_aether", "creatingspace:mars", "minecraft:overworld"],
          "rocket_accessible_dimensions": ["minecraft:overworld", "creatingspace:mars"]
        }""")
        val fonts = root.resolve("fonts").also { it.createDirectories() }
        fonts.resolve("aether.json").writeText("""{"enabled":true,"targetDimension":"aether:the_aether"}""")
        val targets = DimensionSmokePlan.discover(dimensions, fonts)
        assertEquals(listOf("aether:the_aether", "creatingspace:mars", "minecraft:overworld"), targets.map { it.id })
        assertEquals(setOf("creatingspace"), targets.single { it.id == "creatingspace:mars" }.sources)
        assertEquals(20.0, DimensionSmokePlan.parseOverallTps("Overall: Mean tick time: 2.1 ms. Mean TPS: 20.000"))
        assertEquals(
            listOf(1_000_000 to 1_000_000, 1_010_000 to 1_000_000, 1_000_000 to 1_010_000),
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
            if (name == "recipes.json") data.putAll(mapOf("complete" to true, "partial_count" to 0, "error_count" to 0))
            mapper.writeValue(root.resolve(name).toFile(), data)
        }
        assertEquals("snapshot-1", RuntimeSnapshotValidator.validate(root))

        mapper.writeValue(root.resolve("tags.json").toFile(), mapOf("snapshot_id" to "other"))
        assertThrows(IllegalArgumentException::class.java) { RuntimeSnapshotValidator.validate(root) }
    }

    @Test
    fun logPolicyNamesWarningsAndFatalRecords(@TempDir root: Path) {
        val clean = root.resolve("clean.log").also { it.writeText("[INFO] ready\n") }
        val accepted = root.resolve("accepted.log").also {
            it.writeText("[pool-12-thread-1/WARN] [xbigellx.realisticphysics.RealisticPhysics]: Forcing chunk load: [-79, 44]\n" +
                "[C2ME worker #5/ERROR] [net.minecraft.Util]: Detected setBlock in a far chunk [57, 66], pos: BlockPos{x=923, y=63, z=1056}, status: minecraft:features, currently generating: ResourceKey[minecraft:worldgen/placed_feature / natures_spirit:marsh_water_placed]\n" +
                "[Server thread/WARN] [net.minecraft.network.Connection]: handleDisconnection() called twice\n")
        }
        val bad = root.resolve("bad.log").also { it.writeText("[Server/WARN] unsafe\nReportedException: boom\n") }
        assertTrue(LogPolicy.findings(listOf(clean, accepted)).isEmpty())
        val findings = LogPolicy.findings(listOf(bad))
        assertEquals(2, findings.size)
        assertEquals(listOf(1, 2), findings.map { it.line })
    }

    @Test
    fun logPolicyAcceptsOnlyBoundedAdPotherDeferredTasks(@TempDir root: Path) {
        val accepted = root.resolve("accepted-deferred.log").also {
            it.writeText(
                "[13:55:28] [main/WARN] [net.minecraftforge.fml.DeferredWorkQueue]: Mod 'adpother' took 1.533 s to run a deferred task.\n" +
                    "[12:56:48] [Render thread/WARN] [net.minecraftforge.fml.DeferredWorkQueue]: Mod 'adpother' took 2.876 s to run a deferred task.\n",
            )
        }
        val rejected = root.resolve("rejected-deferred.log").also {
            it.writeText(
                "[13:55:28] [main/WARN] [net.minecraftforge.fml.DeferredWorkQueue]: Mod 'adpother' took 5.001 s to run a deferred task.\n" +
                    "[13:55:28] [main/WARN] [net.minecraftforge.fml.DeferredWorkQueue]: Mod 'othermod' took 1.533 s to run a deferred task.\n",
            )
        }

        assertTrue(LogPolicy.findings(listOf(accepted)).isEmpty())
        assertEquals(listOf(1, 2), LogPolicy.findings(listOf(rejected)).map { it.line })
    }

    @Test
    fun logPolicyAcceptsOneBoundedAdChimneysDeferredTaskPerLog(@TempDir root: Path) {
        fun warning(thread: String = "Render thread", duration: String = "7.318", mod: String = "adchimneys") =
            "[07:03:06] [$thread/WARN] [net.minecraftforge.fml.DeferredWorkQueue]: " +
                "Mod '$mod' took $duration s to run a deferred task."

        val acceptedClient = root.resolve("accepted-adchimneys-client.log").also {
            it.writeText(warning() + "\n")
        }
        val acceptedServer = root.resolve("accepted-adchimneys-server.log").also {
            it.writeText(warning(thread = "main", duration = "8.785") + "\n")
        }
        val rejected = root.resolve("rejected-adchimneys.log").also {
            it.writeText(
                warning(duration = "10.001") + "\n" +
                    warning(thread = "Server thread") + "\n" +
                    warning(mod = "othermod") + "\n",
            )
        }
        val overflow = root.resolve("overflow-adchimneys.log").also {
            it.writeText(warning() + "\n" + warning(duration = "8.785") + "\n")
        }

        assertTrue(LogPolicy.findings(listOf(acceptedClient, acceptedServer)).isEmpty())
        assertEquals(listOf(1, 2, 3), LogPolicy.findings(listOf(rejected)).map { it.line })
        assertEquals(listOf(2), LogPolicy.findings(listOf(overflow)).map { it.line })
    }

    @Test
    fun logPolicyAcceptsOnlyFourExactC2meEarlyBlockEntityWarningsPerLog(@TempDir root: Path) {
        fun warning(index: Int) =
            "[16:58:0$index] [C2ME worker #$index/WARN] [net.minecraft.server.level.WorldGenRegion]: " +
                "Tried to access a block entity before it was created. " +
                "BlockPos{x=${1_010_182 + index}, y=46, z=1000215}"

        val accepted = root.resolve("accepted-early-be.log").also {
            it.writeText((1..4).joinToString(separator = "\n", postfix = "\n", transform = ::warning))
        }
        val overflow = root.resolve("overflow-early-be.log").also {
            it.writeText((1..5).joinToString(separator = "\n", postfix = "\n", transform = ::warning))
        }
        val wrongShape = root.resolve("wrong-shape-early-be.log").also {
            it.writeText(
                warning(1).replace("C2ME worker #1", "Server thread") + "\n" +
                    warning(2).replace("net.minecraft.server.level.WorldGenRegion", "net.minecraft.Util") + "\n" +
                    warning(3).replace("before it was created", "after it was removed") + "\n",
            )
        }

        assertTrue(LogPolicy.findings(listOf(accepted)).isEmpty())
        assertEquals(listOf(5), LogPolicy.findings(listOf(overflow)).map { it.line })
        assertEquals(listOf(1, 2, 3), LogPolicy.findings(listOf(wrongShape)).map { it.line })
    }

    @Test
    fun logPolicyAcceptsOnlyOneExactRecoveredDistantHorizonsPhantomArrayPerLog(@TempDir root: Path) {
        fun warning(reference: String = "1359776d") =
            "[06:28:03] [DH-Phantom Array Recycler Thread[0]/WARN] " +
                "[DistantHorizons-DistantHorizons-com.seibel.distanthorizons.core.pooling.PhantomArrayListPool]: " +
                "Pool: [Render Reducer]. Unable to find checkout for phantom reference " +
                "[java.lang.ref.PhantomReference@$reference], arrays will need to be recreated."

        val accepted = root.resolve("accepted-dh-phantom.log").also {
            it.writeText(warning() + "\n")
        }
        val overflow = root.resolve("overflow-dh-phantom.log").also {
            it.writeText(warning() + "\n" + warning("2468ace0") + "\n")
        }
        val wrongShape = root.resolve("wrong-shape-dh-phantom.log").also {
            it.writeText(
                warning().replace("DH-Phantom Array Recycler Thread[0]", "Render thread") + "\n" +
                    warning().replace("PhantomArrayListPool", "OtherPool") + "\n" +
                    warning().replace("arrays will need to be recreated", "array was lost") + "\n",
            )
        }

        assertTrue(LogPolicy.findings(listOf(accepted)).isEmpty())
        assertEquals(listOf(2), LogPolicy.findings(listOf(overflow)).map { it.line })
        assertEquals(listOf(1, 2, 3), LogPolicy.findings(listOf(wrongShape)).map { it.line })
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
    fun coordinatorQueueAcceptsWorkspaceHandoffAndRejectsDuplicates(@TempDir state: Path) {
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
          "selector": "server",
          "candidate": {
            "client": {"path": "/candidate/client.zip", "sha256": "${"a".repeat(64)}"},
            "server": {"path": "/candidate/server.zip", "sha256": "${"b".repeat(64)}"}
          }
        }""")
        fun request(): Int = ProcessBuilder(queue.toString(), "request", handoff.toString())
            .apply { environment()["BC_PACK_TEST_STATE_ROOT"] = state.resolve("coordination").toString() }
            .start().waitFor()
        assertEquals(0, request())
        assertTrue(Files.list(state.resolve("coordination/queued")).use { it.count() } == 1L)
        assertTrue(request() != 0)
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
