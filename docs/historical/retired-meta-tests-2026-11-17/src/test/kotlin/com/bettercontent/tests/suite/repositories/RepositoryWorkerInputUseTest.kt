package com.bettercontent.tests.suite.repositories

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Pure synthetic source-contract bytes only: no agent, JVM, Forge, test worker or world run. */
class RepositoryWorkerInputUseTest {
    @TempDir lateinit var temporary: Path
    private data class Fixture(val root: Path, val evidence: Path, val plan: RepositoryRunPlan,
        val prepared: RepositoryWorkerInputUse.Prepared, val execution: RepositoryCommandExecution,
        val started: Instant, val ended: Instant, val worker: Path, val provider: Path)
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    private fun fixture(multiRelease: Boolean = false): Fixture {
        val root = Files.createTempDirectory(temporary, "repository-")
        val evidence = Files.createTempDirectory(temporary, "evidence-")
        fun file(path: Path, value: String): Path {
            Files.createDirectories(path.parent); Files.writeString(path, value); return path
        }
        val provider = root.resolve("providers/actual.jar")
        Files.createDirectories(provider.parent)
        val baseClass = "class definition fixture".toByteArray()
        val providerClass = (if (multiRelease) "versioned class definition fixture" else "class definition fixture").toByteArray()
        ZipOutputStream(Files.newOutputStream(provider)).use { out ->
            if (multiRelease) {
                out.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
                out.write("Manifest-Version: 1.0\r\nMulti-Release: true\r\n\r\n".toByteArray())
                out.closeEntry()
            }
            out.putNextEntry(ZipEntry("tiny/ProviderProof.class")); out.write(baseClass); out.closeEntry()
            if (multiRelease) {
                out.putNextEntry(ZipEntry("META-INF/versions/9/tiny/ProviderProof.class"))
                out.write(providerClass); out.closeEntry()
            }
        }
        val artifact = file(root.resolve("candidate/packaged.jar"), "selected but not loaded")
        val source = file(temporary.resolve("scripts/playtest-repository-input-use/RepositoryInputUseAgent.java"), "source")
        val init = file(temporary.resolve("scripts/playtest-repository-input-use/worker-init.gradle"), "script")
        val agent = file(temporary.resolve("observer.jar"), "compiled observer")
        val receipt = file(temporary.resolve("receipt.json"), "build receipt")
        // JRT selected entries are verified against this exact pinned runtime image, never a
        // fabricated miniature JDK. Read it; do not write to the system installation.
        val javaHome = Path.of(System.getProperty("java.home")).toRealPath()
        val java = javaHome.resolve("bin/java").toRealPath()
        val modules = javaHome.resolve("lib/modules")
        val vm = javaHome.resolve("lib/server/libjvm.so")
        val release = javaHome.resolve("release")
        val feature = Runtime.version().feature()
        val external = file(temporary.resolve("junit.jar"), "external test library")
        val emptyExternal = file(temporary.resolve("empty-resource.classpath"), "")
        fun bound(path: Path) = BoundFile(path.toString(), Hashes.sha256(path))
        fun input(path: Path) = RepositoryInput(root.relativize(path).toString(), Hashes.sha256(path),
            Files.size(path), Files.isExecutable(path))
        val plan = RepositoryRunPlan("bc.playtest_repository_run_plan.v3", "reviewed", "reviewer", Instant.now().toString(),
            "run-1", "better-spawns", mapOf("client_sha256" to "a".repeat(64), "server_sha256" to "b".repeat(64)),
            bound(source), bound(receipt), bound(artifact), root.toString(), "./gradlew",
            listOf("--no-daemon", "--no-build-cache", "--rerun-tasks", "--console=plain", "test"), emptyMap(), 60,
            mapOf("source" to emptyList(), "tests" to emptyList(), "providers" to listOf(input(provider)),
                "toolchain" to emptyList(), "configuration" to emptyList(), "artifact" to listOf(input(artifact))),
            listOf(RepositoryReportPlan("junit", "test", "build/results.xml", listOf("one"), null, null,
                invocationTask = "test")), runtimeToolchainFacts = mapOf("java_major" to feature.toString(), "gradle_version" to "8.8"),
            invocationTasks = listOf("test"), artifactBinding = RepositoryArtifactBinding("mod-jar", jar = bound(artifact)),
            executionContext = "source-only", usageReport = "build/observed-workers", usageReportProducer = source.toString(),
            useHarness = RepositoryInputUseHarness(bound(source), bound(init), bound(agent), bound(receipt), bound(java),
                listOf(bound(external), bound(emptyExternal), bound(modules), bound(vm), bound(release)),
                listOf("<controller-repository-agent>", "<controller-task-roster>")))
        val prepared = RepositoryWorkerInputUse.prepare(plan, root, evidence, "f".repeat(64))
        val now = Instant.now()
        val worker = prepared.reports.values.single()
        val stream = prepared.streams.values.single()
        val config = prepared.configs.values.single()
        val streamNonce = "00000000-0000-4000-8000-000000000001"
        val payload = suiteMapper.createObjectNode().put("schema", "bc.repository_input_use_worker.v3")
            .put("scope", "source-only-not-packaged-runtime")
            .put("observation_scope", "class-definition-stage-only-resource-access-unobserved")
            .put("status", "bounded-file-class-stage")
            .put("repository", plan.repository).put("run_id", plan.runId).put("task", ":test")
            .put("reviewed_plan_sha256", "f".repeat(64)).put("producer_sha256", bound(source).sha256)
            .put("config_sha256", config.sha256)
            .put("event_stream_path", stream.toString()).put("event_stream_nonce", streamNonce)
            .put("health_page_path", prepared.healthPages.values.single().toString())
            .put("health_nonce", prepared.healthNonces.values.single())
            .put("snapshot_stream_sequence", 3)
            .put("observed_process_pid", 77L).put("observed_process_start", now.toString())
            .put("captured_at", now.plusSeconds(1).toString())
            .put("java_executable", java.toString()).put("java_executable_sha256", bound(java).sha256)
            .put("java_version", System.getProperty("java.version"))
            .put("java_runtime_version", System.getProperty("java.runtime.version"))
            .put("java_vendor", System.getProperty("java.vendor"))
            .put("java_home", javaHome.toString())
            .put("java_vm_name", System.getProperty("java.vm.name"))
            .put("java_vm_version", System.getProperty("java.vm.version"))
            .put("java_vm_vendor", System.getProperty("java.vm.vendor"))
            .put("jar_mr_policy", System.getProperty("jdk.util.jar.enableMultiRelease", "<unset>"))
            .put("jar_version_override", System.getProperty("jdk.util.jar.version", "<unset>"))
            .put("jar_runtime_feature", JarFile.runtimeVersion().feature())
            .put("bootstrap_pre_agent", 500)
        payload.putArray("unsupported")
        payload.putArray("jvm_arguments").add("-javaagent:${agent.toRealPath()}=${config.path}@${config.sha256}")
        fun add(rows: com.fasterxml.jackson.databind.node.ArrayNode, path: Path) {
            rows.addObject().put("path", path.toString()).put("kind", "file")
                .put("sha256", Hashes.sha256(path)).put("files", 1)
                .put("size_bytes", Files.size(path))
        }
        val definitions = payload.putArray("class_definitions")
        fun definition(name: String, from: Path, content: ByteArray) {
            definitions.addObject().put("name", name).put("source", from.toString())
                .put("bytes_sha256", digest(content)).put("loader_id", 1)
                .put("selected_entry", if (multiRelease && from == provider) "META-INF/versions/9/$name" else name)
        }
        val generated = Files.createDirectories(root.resolve("build/classes/java/test"))
        val generatedClass = file(generated.resolve("tiny/WorkerMain.class"), "generated worker definition")
        val emptyResource = generated.resolve("empty.txt")
        Files.write(emptyResource, byteArrayOf()) // legitimate SHA256(empty), never dropped
        // Directory inventory: ordered relative-path NUL sha256(content) NUL, including empty.
        val directoryBytes = ("empty.txt\u0000${Hashes.sha256(emptyResource)}\u0000" +
            "tiny/WorkerMain.class\u0000${Hashes.sha256(generatedClass)}\u0000").toByteArray()
        fun addGenerated(rows: com.fasterxml.jackson.databind.node.ArrayNode) {
            rows.addObject().put("path", generated.toString()).put("kind", "directory")
                .put("sha256", digest(directoryBytes)).put("files", 2)
                .put("size_bytes", Files.size(generatedClass))
        }
        add(payload.putArray("code_sources"), provider)
        addGenerated(payload.withArray("code_sources"))
        add(payload.putArray("class_path"), provider)
        addGenerated(payload.withArray("class_path"))
        add(payload.withArray("class_path"), external)
        add(payload.withArray("class_path"), emptyExternal)
        payload.putArray("module_path")
        val taskInputs = payload.putArray("task_class_path")
        add(taskInputs, provider); addGenerated(taskInputs); add(taskInputs, external); add(taskInputs, emptyExternal)
        val absent = root.resolve("build/resources/test") // ordinary Gradle NO_SOURCE resources output
        val absentSha = digest("bc.repository_absent.v1:$absent".toByteArray(Charsets.UTF_8))
        taskInputs.addObject().put("path", absent.toString()).put("kind", "absent")
            .put("sha256", absentSha).put("files", 0).put("size_bytes", 0)
        val roster = Path.of(config.path.removeSuffix(".properties") + "-task-inputs.txt")
        val rawRoster = "policy\tTest\t1\t0\n" +
            "file\t${Hashes.sha256(provider)}\t1\t${Files.size(provider)}\t$provider\n" +
            "directory\t${digest(directoryBytes)}\t2\t${Files.size(generatedClass)}\t$generated\n" +
            "file\t${Hashes.sha256(external)}\t1\t${Files.size(external)}\t$external\n" +
            "file\t${Hashes.sha256(emptyExternal)}\t1\t0\t$emptyExternal\n" +
            "absent\t$absentSha\t0\t0\t$absent\n"
        Files.writeString(roster, rawRoster)
        payload.put("task_roster_path", roster.toString()).put("task_roster_sha256", Hashes.sha256(roster))
            .put("task_execution_policy", "Test/maxParallelForks=1/forkEvery=0")
        payload.withArray("jvm_arguments").add("-Dbc.repository.task.roster=$roster@${Hashes.sha256(roster)}")
        add(payload.putArray("instrumentation_agents"), agent)
        payload.putArray("module_access_changes")
        payload.putArray("union_sources")
        val jdk = payload.putArray("jdk_components")
        listOf(java, modules, vm, release).forEach { add(jdk, it) }
        definition("tiny/ProviderProof.class", provider, providerClass)
        definition("tiny/WorkerMain.class", generated, Files.readAllBytes(generatedClass))
        Files.writeString(worker, suiteMapper.writeValueAsString(payload))
        fun event(sequence: Int, kind: String) = suiteMapper.createObjectNode()
            .put("sequence", sequence).put("kind", kind)
        val start = event(0, "start").put("nonce", streamNonce).put("config_sha256", config.sha256)
            .put("pid", 77L).put("birth", now.toString())
        val providerEvent = event(1, "definition").put("name", "tiny/ProviderProof.class")
            .put("loader_id", 1).put("source", provider.toString()).put("bytes_sha256", digest(providerClass))
            .put("selected_entry", if (multiRelease) "META-INF/versions/9/tiny/ProviderProof.class" else "tiny/ProviderProof.class")
        val generatedEvent = event(2, "definition").put("name", "tiny/WorkerMain.class")
            .put("loader_id", 1).put("source", generated.toString())
            .put("bytes_sha256", Hashes.sha256(generatedClass)).put("selected_entry", "tiny/WorkerMain.class")
        val hook = event(3, "hook_report").put("report_sha256", Hashes.sha256(worker))
            .put("snapshot_stream_sequence", 3)
        Files.writeString(stream, listOf(start, providerEvent, generatedEvent, hook)
            .joinToString("\n") { suiteMapper.writeValueAsString(it) } + "\n")
        prepared.retainedHealth.values.single().apply {
            putLong(128, 77L); putLong(136, now.toEpochMilli())
            putLong(144, 4L); putLong(152, 4L); putInt(184, 1)
            force()
        }
        val execution = RepositoryCommandExecution(0, false, listOf(RepositoryProcessIdentity(77L, now.toString())),
            emptyList(), emptyList(), true, null, null)
        return Fixture(root, evidence, plan, prepared, execution, now, now.plusSeconds(20), worker, provider)
    }
    private fun verify(f: Fixture) = RepositoryWorkerInputUse.verify(f.plan, f.root, f.prepared,
        "f".repeat(64), f.execution, f.started, f.ended, f.evidence)
    private fun change(f: Fixture, edit: (ObjectNode) -> Unit) {
        val doc = suiteMapper.readTree(f.worker.toFile()) as ObjectNode
        edit(doc); Files.writeString(f.worker, suiteMapper.writeValueAsString(doc))
    }
    @Test fun `bounded class definitions with reviewed JVM and JDK bytes reach source-only aggregate`() {
        val f = fixture()
        assertEquals("bc.repository_input_use.v3", suiteMapper.readTree(Path.of(verify(f).path).toFile()).path("schema").asText())
    }
    @Test fun `NO_SOURCE declared resources are not used inputs and absent-to-present or empty-file drift rejects`() {
        val ordinary = fixture()
        val raw = suiteMapper.readTree(ordinary.worker.toFile())
        val declared = raw.path("task_class_path").first { it.path("kind").asText() == "absent" }
        assertEquals(0, declared.path("size_bytes").asInt())
        val empty = raw.path("class_path").first { it.path("path").asText().endsWith("empty-resource.classpath") }
        assertEquals("file", empty.path("kind").asText())
        assertEquals(0, empty.path("size_bytes").asInt())
        assertEquals(digest(byteArrayOf()), empty.path("sha256").asText())
        assertEquals("absent", declared.path("kind").asText())
        assertEquals(false, raw.path("code_sources").any {
            it.path("path").asText() == declared.path("path").asText()
        })
        assertEquals("bc.repository_input_use.v3", suiteMapper.readTree(Path.of(verify(ordinary).path).toFile())
            .path("schema").asText())
        val created = fixture()
        val previouslyAbsent = suiteMapper.readTree(created.worker.toFile()).path("task_class_path")
            .first { it.path("kind").asText() == "absent" }.path("path").asText()
        Files.createDirectories(Path.of(previouslyAbsent))
        assertThrows(IllegalArgumentException::class.java) { verify(created) }
        val changedEmpty = fixture()
        Files.writeString(changedEmpty.root.resolve("build/classes/java/test/empty.txt"), "not empty now")
        assertThrows(IllegalArgumentException::class.java) { verify(changedEmpty) }
    }
    @Test fun `standard MR JAR selects versioned real entry from exact bytes and rejects a base-entry substitution`() {
        val selected = fixture(multiRelease = true)
        assertEquals("bc.repository_input_use.v3", suiteMapper.readTree(Path.of(verify(selected).path).toFile())
            .path("schema").asText())
        val substituted = fixture(multiRelease = true)
        change(substituted) { report ->
            (report.withArray("class_definitions")[0] as ObjectNode)
                .put("selected_entry", "tiny/ProviderProof.class")
        }
        val stream = substituted.prepared.streams.values.single()
        val lines = Files.readAllLines(stream).map { suiteMapper.readTree(it) as ObjectNode }
        lines[1].put("selected_entry", "tiny/ProviderProof.class")
        lines[3].put("report_sha256", Hashes.sha256(substituted.worker))
        Files.writeString(stream, lines.joinToString("\n") { suiteMapper.writeValueAsString(it) } + "\n")
        assertThrows(IllegalArgumentException::class.java) { verify(substituted) }
    }
    @Test fun `post-hook class definition is independently consumed after owned exit but late error fails`() {
        val later = fixture()
        val stream = later.prepared.streams.values.single()
        val late = suiteMapper.createObjectNode().put("sequence", 4).put("kind", "definition")
            .put("name", "tiny/ProviderProof.class").put("loader_id", 2)
            .put("source", later.provider.toString())
            .put("bytes_sha256", digest("class definition fixture".toByteArray()))
            .put("selected_entry", "tiny/ProviderProof.class")
        Files.writeString(stream, suiteMapper.writeValueAsString(late) + "\n", java.nio.file.StandardOpenOption.APPEND)
        later.prepared.retainedHealth.values.single().apply {
            putLong(144, 5L); putLong(152, 5L); force()
        }
        assertEquals("bc.repository_input_use.v3",
            suiteMapper.readTree(Path.of(verify(later).path).toFile()).path("schema").asText())
        val failed = fixture()
        val error = suiteMapper.createObjectNode().put("sequence", 4).put("kind", "error")
            .put("reason", "simulated-late-writer-or-source-error")
        Files.writeString(failed.prepared.streams.values.single(), suiteMapper.writeValueAsString(error) + "\n",
            java.nio.file.StandardOpenOption.APPEND)
        failed.prepared.retainedHealth.values.single().apply {
            putLong(144, 5L); putLong(152, 5L); putLong(160, 1L); force()
        }
        assertThrows(IllegalStateException::class.java) { verify(failed) }
        val dropped = fixture()
        dropped.prepared.retainedHealth.values.single().apply {
            putLong(144, 5L); putLong(160, 1L); force() // a late write failed; valid stream prefix remains
        }
        assertThrows(IllegalArgumentException::class.java) { verify(dropped) }
        val wrongWorker = fixture()
        wrongWorker.prepared.retainedHealth.values.single().apply { putLong(128, 999L); force() }
        assertThrows(IllegalArgumentException::class.java) { verify(wrongWorker) }
    }
    @Test fun `extra stream and snapshot definition claims reject at their exact shape gates`() {
        val streamExtra = fixture()
        val stream = streamExtra.prepared.streams.values.single()
        val events = Files.readAllLines(stream).map { suiteMapper.readTree(it) as ObjectNode }
        events[1].put("unreviewed_provenance", true)
        Files.writeString(stream, events.joinToString("\n") { suiteMapper.writeValueAsString(it) } + "\n")
        val streamFailure = assertThrows(IllegalArgumentException::class.java) { verify(streamExtra) }
        assertEquals("unknown or missing worker event field", streamFailure.message)

        val snapshotExtra = fixture()
        change(snapshotExtra) { report ->
            (report.path("class_definitions")[0] as ObjectNode).put("unreviewed_provenance", true)
        }
        val snapshotStream = snapshotExtra.prepared.streams.values.single()
        val snapshotEvents = Files.readAllLines(snapshotStream).map { suiteMapper.readTree(it) as ObjectNode }
        // This independently authored synthetic fixture must reach the nested shape gate,
        // not accidentally pass its negative assertion on a stale hook-report digest.
        snapshotEvents.single { it.path("kind").asText() == "hook_report" }
            .put("report_sha256", Hashes.sha256(snapshotExtra.worker))
        Files.writeString(snapshotStream,
            snapshotEvents.joinToString("\n") { suiteMapper.writeValueAsString(it) } + "\n")
        val snapshotFailure = assertThrows(IllegalArgumentException::class.java) { verify(snapshotExtra) }
        assertEquals("hook snapshot is not an exact prefix of owned-worker definition events", snapshotFailure.message)
    }
    @Test fun `reviewed worker rejects an original two-fork Test profile instead of silently changing it`() {
        val unsupported = fixture()
        val report = suiteMapper.readTree(unsupported.worker.toFile()) as ObjectNode
        val roster = Path.of(report.path("task_roster_path").asText())
        Files.writeString(roster, Files.readString(roster).replaceFirst(
            "policy\tTest\t1\t0\n", "policy\tTest\t2\t0\n"))
        val newSha = Hashes.sha256(roster)
        report.put("task_roster_sha256", newSha)
            .put("task_execution_policy", "Test/maxParallelForks=2/forkEvery=0")
        val args = report.withArray("jvm_arguments")
        val oldRosterArg = args[1].asText()
        args.remove(1); args.insert(1, oldRosterArg.substringBeforeLast('@') + "@$newSha")
        Files.writeString(unsupported.worker, suiteMapper.writeValueAsString(report))
        val stream = unsupported.prepared.streams.values.single()
        val events = Files.readAllLines(stream).map { suiteMapper.readTree(it) as ObjectNode }
        events[3].put("report_sha256", Hashes.sha256(unsupported.worker))
        Files.writeString(stream, events.joinToString("\n") { suiteMapper.writeValueAsString(it) } + "\n")
        assertThrows(IllegalStateException::class.java) { verify(unsupported) }
    }
    @Test fun `two prepared worker identities never accept the other process health mapping`() {
        val first = fixture(); val second = fixture()
        val firstPage = first.prepared.healthPages.values.single()
        val secondPage = second.prepared.healthPages.values.single()
        org.junit.jupiter.api.Assertions.assertNotEquals(firstPage, secondPage)
        org.junit.jupiter.api.Assertions.assertNotEquals(first.prepared.healthNonces.values.single(),
            second.prepared.healthNonces.values.single())
        val exchanged = first.prepared.copy(retainedHealth = second.prepared.retainedHealth)
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryWorkerInputUse.verify(first.plan, first.root, exchanged, "f".repeat(64),
                first.execution, first.started, first.ended, first.evidence)
        }
    }
    @Test fun `parent PID, unknown external source, packaged artifact and skipped provider fail`() {
        val f = fixture(); change(f) { it.put("observed_process_pid", 99L) }
        assertThrows(IllegalArgumentException::class.java) { verify(f) }
        val second = fixture(); change(second) { it.putArray("code_sources") }
        assertThrows(IllegalArgumentException::class.java) { verify(second) }
        val third = fixture(); change(third) { it.withArray("class_path").addObject()
            .put("path", Path.of(requireNotNull(third.plan.artifact).path).toString())
            .put("sha256", requireNotNull(third.plan.artifact).sha256).put("kind", "file")
            .put("files", 1).put("size_bytes", Files.size(Path.of(requireNotNull(third.plan.artifact).path))) }
        assertThrows(IllegalArgumentException::class.java) { verify(third) }
    }
    @Test fun `incomplete union, changed generated bytes, unknown instrumentation and unknown field fail`() {
        val f = fixture(); change(f) { it.put("status", "incomplete").withArray("unsupported").add("non-file-code-source:union") }
        assertThrows(IllegalArgumentException::class.java) { verify(f) }
        val second = fixture(); Files.writeString(second.root.resolve("build/classes/java/test/tiny/WorkerMain.class"), "changed")
        assertThrows(IllegalArgumentException::class.java) { verify(second) }
        val third = fixture(); change(third) { it.withArray("jvm_arguments").add("-javaagent:unreviewed.jar") }
        assertThrows(IllegalArgumentException::class.java) { verify(third) }
        val fourth = fixture(); change(fourth) { it.put("unreviewed", true) }
        assertThrows(IllegalArgumentException::class.java) { verify(fourth) }
    }
    @Test fun `task roster cannot be promoted to loaded classes or forge union without bound module access`() {
        val f = fixture(); change(f) { it.putArray("task_class_path") }
        assertThrows(IllegalArgumentException::class.java) { verify(f) }
        val second = fixture(); change(second) { doc ->
            doc.withArray("union_sources").add(doc.withArray("code_sources")[0])
        }
        assertThrows(IllegalArgumentException::class.java) { verify(second) }
        val third = fixture(); change(third) { doc ->
            doc.withArray("instrumentation_agents").add(doc.withArray("instrumentation_agents")[0])
        }
        assertThrows(IllegalArgumentException::class.java) { verify(third) }
        val fourth = fixture(); change(fourth) { it.put("task_roster_sha256", "0".repeat(64)) }
        assertThrows(IllegalArgumentException::class.java) { verify(fourth) }
    }
    @Test fun `wrong report config or agent source hash, duplicate row, trailing document and oversized report fail`() {
        val f = fixture(); change(f) { it.put("config_sha256", "0".repeat(64)) }
        assertThrows(IllegalArgumentException::class.java) { verify(f) }
        val second = fixture(); change(second) { it.withArray("code_sources").add(it.withArray("code_sources")[0]) }
        assertThrows(IllegalArgumentException::class.java) { verify(second) }
        val third = fixture(); Files.writeString(third.worker, Files.readString(third.worker) + "{}")
        assertThrows(Exception::class.java) { verify(third) }
        // Producer MAX_REPORT and verifier cap are independently pinned at32MiB.
        // 8MiB+1 is malformed-but-bounded JSON, not an oversized report.
        val malformed = fixture(); Files.writeString(malformed.worker, "x".repeat(8_388_609))
        assertThrows(com.fasterxml.jackson.core.JsonParseException::class.java) { verify(malformed) }
        val fourth = fixture()
        java.nio.channels.FileChannel.open(fourth.worker, java.nio.file.StandardOpenOption.WRITE,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING).use { file ->
            file.position(33_554_432L)
            file.write(java.nio.ByteBuffer.wrap(byteArrayOf(0))) // sparse32MiB+1, no giant fixture String
        }
        assertEquals(33_554_433L, Files.size(fourth.worker))
        assertThrows(IllegalArgumentException::class.java) { verify(fourth) }
        val fifth = fixture(); Files.writeString(Path.of(fifth.prepared.configs.values.single().path), "changed")
        assertThrows(IllegalArgumentException::class.java) { verify(fifth) }
    }
}
