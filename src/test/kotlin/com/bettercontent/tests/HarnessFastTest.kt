package com.bettercontent.tests

import com.bettercontent.tests.release.ActiveMod
import com.bettercontent.tests.release.annotateJar
import com.bettercontent.tests.release.packageResolveCommand
import com.bettercontent.tests.release.jarDeclaresMod
import com.bettercontent.tests.release.readSourceCommit
import com.bettercontent.tests.release.sourceUpdateStatus
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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
        assertEquals(34, mods.size())
        val repositories = mods.map { it.path("repository").asText() }.toSet()
        assertEquals(34, repositories.size)
        assertEquals(34, mods.map { it.path("modId").asText() }.toSet().size)
        mods.forEach { mod ->
            assertTrue(Files.isRegularFile(root.resolve("mods").resolve(mod.path("artifact").asText())))
            assertTrue(mod.path("tasks").isArray && mod.path("tasks").size() > 0)
            assertTrue(mod.path("dependsOn").let { it.isMissingNode || (it.isArray && it.all { dependency -> dependency.asText() in repositories }) })
        }
        assertEquals(listOf("heat-sync"), mods.single { it.path("repository").asText() == "latent-chemlib" }.path("dependsOn").map { it.asText() })
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
        val first = ProcessBuilder(
            lock.toString(), "run", "test", "server", "--",
            "sh", "-c", "test -f '${state.resolve("owner.json")}' && touch '$ready' && sleep 2",
        ).apply {
            environment()["BC_PACK_TEST_STATE_ROOT"] = state.toString()
            environment().remove("BC_PACK_TEST_LOCK_TOKEN")
        }.start()
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (!Files.exists(ready) && System.nanoTime() < deadline) Thread.sleep(50)
        assertTrue(Files.exists(ready), "first runner never acquired the mutex")

        val contender = ProcessBuilder(lock.toString(), "run", "test", "candidate", "--", "true")
            .apply {
                environment()["BC_PACK_TEST_STATE_ROOT"] = state.toString()
                environment().remove("BC_PACK_TEST_LOCK_TOKEN")
            }
            .start()
        assertEquals(75, contender.waitFor())
        assertEquals(0, first.waitFor())
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
