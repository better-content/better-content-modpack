package com.bettercontent.tests

import com.bettercontent.tests.release.UpstreamRuntimeOverrides
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Synthetic receiver controls only: no packaging, upstream download or source qualification. */
@Tag("fast")
class UpstreamRuntimeOverridesTest {
    private val mapper = jacksonObjectMapper()
    private val policyPath = "config/better-content-runtime-overrides.json"
    private val support = "build/upstream-runtime-overrides/"
    private fun hash(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { z -> entries.forEach { (name, body) ->
            z.putNextEntry(ZipEntry(name)); z.write(body); z.closeEntry()
        } }
        output.toByteArray()
    }
    private fun write(path: Path, body: ByteArray): Path {
        Files.createDirectories(path.parent); return Files.write(path, body)
    }
    private fun writeJson(path: Path, node: ObjectNode) = write(path, mapper.writeValueAsBytes(node))
    private fun retained(): Path = Files.createTempDirectory(
        Files.createDirectories(Path.of("/home/dev/.tmp/upstream-runtime-overrides-controls")), "case-")

    private inner class Fixture {
        val base = retained()
        val root = Files.createDirectory(base.resolve("root"))
        val source = Files.createDirectory(base.resolve("compat"))
        val evidence = base.resolve("evidence")
        val policy = mapper.readTree(Files.readAllBytes(TestConfig.load().root.resolve(policyPath))) as ObjectNode
        val providerEntries = linkedMapOf<String, ByteArray>()
        val providerJar = base.resolve("qualified.jar")
        val supports = linkedMapOf<String, String>()
        val outputs = linkedMapOf<String, ByteArray>()
        val client = base.resolve("client.zip")
        val server = base.resolve("server.zip")
        init {
            val contract = policy["required_compat"] as ObjectNode
            val config = mapper.createObjectNode().put("required", false).put("plugin", contract["plugin"].asText())
                .put("package", "fixture.mixin")
            val mixins = config.putArray("mixins")
            (contract["classes"] as ObjectNode).fieldNames().asSequence().toList().forEachIndexed { index, name ->
                val body = "synthetic replacement $index".toByteArray()
                (contract["classes"] as ObjectNode).put(name, hash(body))
                mixins.add(name); providerEntries["fixture/mixin/${name.replace('.', '/')}.class"] = body
            }
            val plugin = "synthetic plugin".toByteArray()
            contract.put("plugin_sha256", hash(plugin))
            providerEntries[contract["plugin"].asText().replace('.', '/') + ".class"] = plugin
            providerEntries[contract["config"].asText()] = mapper.writeValueAsBytes(config)
            providerEntries["META-INF/MANIFEST.MF"] =
                "Manifest-Version: 1.0\r\nMixinConfigs: ${contract["config"].asText()}\r\n\r\n".toByteArray()
            write(providerJar, zip(providerEntries))
            val receipt = policy["receipt"] as ObjectNode
            val compat = receipt["compat"] as ObjectNode
            for (key in listOf("classes", "plugin_sha256", "config_required", "condition")) compat.set<com.fasterxml.jackson.databind.JsonNode>(key, contract[key].deepCopy())
            // The raw producer JAR is deliberately not the stamped fixture provider archive.
            compat.put("sha256", "0".repeat(64))
            val spec = mapper.createObjectNode().put("schema", "bc.upstream_runtime_overrides.v1").put("revision", "bc-runtime.1")
            spec.set<ObjectNode>("required_compat", contract.deepCopy())
            val specArtifacts = spec.putArray("artifacts")
            policy["artifacts"].forEachIndexed { index, node ->
                val a = node as ObjectNode
                val classBytes = "unchanged class $index".toByteArray()
                val resource = "corrected resource $index".toByteArray()
                val nested = zip(linkedMapOf("Nested.class" to classBytes))
                val data = zip(linkedMapOf("nested.jar" to nested, "fixed.json" to resource))
                outputs[a["output"].asText()] = data
                a.put("output_sha256", hash(data))
                val row = receipt["artifacts"][index] as ObjectNode
                row.put("output_sha256", hash(data)).put("class_count", 1)
                val inventory = "{\n  \"nested.jar!Nested.class\": \"${hash(classBytes)}\"\n}\n".toByteArray()
                row.put("class_inventory_sha256", hash(inventory))
                val resources = row.putArray("resources")
                val r = resources.addObject().put("before_sha256", "1".repeat(64)).put("after_sha256", hash(resource))
                r.putArray("path").add("fixed.json")
                val s = specArtifacts.addObject().put("input", a["input"].asText()).put("output", a["output"].asText())
                    .put("sha256", a["input_sha256"].asText()).put("sha1", a["input_sha1"].asText())
                s.putObject("curseforge").put("project", a["project_id"].asInt()).put("file", a["file_id"].asInt())
                write(root.resolve("mods/${a["manifest"].asText()}"), """
                    name = "fixture $index"
                    filename = "${a["input"].asText()}"
                    side = "both"
                    [download]
                    hash-format = "sha1"
                    hash = "${a["input_sha1"].asText()}"
                    mode = "metadata:curseforge"
                    [update.curseforge]
                    project-id = ${a["project_id"].asLong()}
                    file-id = ${a["file_id"].asLong()}
                """.trimIndent().toByteArray())
            }
            val specBytes = mapper.writeValueAsBytes(spec)
            val builder = "synthetic builder bytes".toByteArray()
            (policy["spec"] as ObjectNode).put("sha256", hash(specBytes))
            (policy["builder"] as ObjectNode).put("sha256", hash(builder))
            receipt.put("spec_sha256", hash(specBytes)).put("builder_sha256", hash(builder))
            write(source.resolve(policy["spec"]["path"].asText()), specBytes)
            write(source.resolve(policy["builder"]["path"].asText()), builder)
            outputs.forEach { (name, data) -> write(source.resolve(support + name), data); supports[support + name] = hash(data) }
            val receiptBytes = mapper.writeValueAsBytes(receipt)
            write(source.resolve(support + "receipt.json"), receiptBytes); supports[support + "receipt.json"] = hash(receiptBytes)
            compat.put("sha256", "SOURCE_QUALIFIED_RAW_PROVIDER_SHA256")
            savePolicy()
        }
        fun savePolicy() { writeJson(root.resolve(policyPath), policy) }
        fun prepare() = UpstreamRuntimeOverrides.prepare(root, source, evidence, providerJar, supports)
        fun candidateEntries(clientSide: Boolean): LinkedHashMap<String, ByteArray> {
            val prefix = if (clientSide) "custom-overrides/" else "server-root/"
            val entries = linkedMapOf(prefix + policyPath to Files.readAllBytes(root.resolve(policyPath)))
            outputs.forEach { (name, data) -> entries[prefix + "mods/" + name] = data }
            entries[prefix + "mods/" + policy["provider_artifact"].asText()] = Files.readAllBytes(providerJar)
            if (clientSide) entries["manifest.json"] = """{"overrides":"custom-overrides","files":[]}""".toByteArray()
            return entries
        }
        fun candidates(changeClient: (LinkedHashMap<String, ByteArray>) -> Unit = {},
                       changeServer: (LinkedHashMap<String, ByteArray>) -> Unit = {}) {
            val c = candidateEntries(true); changeClient(c); write(client, zip(c))
            val s = candidateEntries(false); changeServer(s); write(server, zip(s))
        }
        fun validate() = UpstreamRuntimeOverrides.validateCandidates(root, client, server)
        fun replaceReceipt(change: (ObjectNode) -> Unit) {
            val path = source.resolve(support + "receipt.json")
            val r = mapper.readTree(Files.readAllBytes(path)) as ObjectNode
            change(r); writeJson(path, r); supports[support + "receipt.json"] = hash(Files.readAllBytes(path))
        }
    }

    @Test fun validPreparationAndBothCandidatesAreReadOnlyAndBoundToAuthoredPolicy() {
        val f = Fixture(); val sourceBefore = Files.readAllBytes(f.source.resolve(support + "receipt.json"))
        val prepared = f.prepare()
        assertEquals(f.evidence.resolve("runtime-overrides").toAbsolutePath(), prepared.directory)
        assertEquals("${prepared.directory}\n", Files.readString(f.root.resolve(".worklane/runtime-overrides/current.path")))
        assertEquals(hash(Files.readAllBytes(f.providerJar)), prepared.provenance["qualified_provider_sha256"])
        assertNotEquals(prepared.provenance["qualified_provider_sha256"], prepared.provenance["raw_provider_sha256"])
        assertEquals("NOT_EVALUATED", prepared.provenance["runtime_acceptance"])
        assertArrayEquals(sourceBefore, Files.readAllBytes(f.source.resolve(support + "receipt.json")))
        assertFalse(Files.getPosixFilePermissions(prepared.directory).contains(PosixFilePermission.OWNER_WRITE))
        val selection = mapper.readTree(Files.readAllBytes(prepared.directory.resolve("selection.json")))
        assertEquals("bc.package_runtime_overrides.v1", selection["schema"].asText()); assertEquals(5, selection["artifacts"].size())
        f.candidates(); val before = hash(Files.readAllBytes(f.client)) to hash(Files.readAllBytes(f.server))
        f.validate()
        assertEquals(before, hash(Files.readAllBytes(f.client)) to hash(Files.readAllBytes(f.server)))
        assertThrows(Exception::class.java) { f.prepare() }
        val next = UpstreamRuntimeOverrides.prepare(f.root, f.source, f.base.resolve("next"), f.providerJar, f.supports)
        assertNotEquals(prepared.directory, next.directory)
        assertTrue(Files.exists(prepared.directory.resolve("receipt.json")))
    }

    @Test fun newlyQualifiedRawProviderDigestMayVaryButOnlyThatReceiptFieldIsNormalized() {
        for (rawHash in listOf("a".repeat(64), "b".repeat(64))) {
            val f = Fixture()
            f.replaceReceipt { r -> (r["compat"] as ObjectNode).put("sha256", rawHash) }
            val prepared = f.prepare()
            assertEquals(rawHash, prepared.provenance["raw_provider_sha256"])
            assertEquals(hash(Files.readAllBytes(f.providerJar)), prepared.provenance["qualified_provider_sha256"])
            assertEquals("SOURCE_QUALIFIED_RAW_PROVIDER_SHA256", f.policy["receipt"]["compat"]["sha256"].asText())
        }
        val mutations: List<(ObjectNode) -> Unit> = listOf(
            { (it["compat"] as ObjectNode).put("sha256", "not-a-sha256") },
            { (it["compat"] as ObjectNode).put("sha256", "SOURCE_QUALIFIED_RAW_PROVIDER_SHA256") },
            { (it["compat"]["classes"] as ObjectNode).put("adpother.coldsweat.HearthBlockEntityMixin", "f".repeat(64)) },
            { (it["compat"] as ObjectNode).put("config_required", true) },
            { (it["compat"] as ObjectNode).put("plugin_sha256", "f".repeat(64)) },
            { (it["artifacts"][0] as ObjectNode).put("output_sha256", "f".repeat(64)) },
            { it.put("spec_sha256", "f".repeat(64)) },
            { it.put("python", "different") },
            { it.put("zlib", "different") },
        )
        for (mutate in mutations) {
            val f = Fixture(); f.replaceReceipt(mutate)
            assertThrows(Exception::class.java) { f.prepare() }
        }
    }

    @Test fun supportRecipeSourceOutputAndPinsFailClosed() {
        val mutations: List<(Fixture) -> Unit> = listOf(
            { it.supports[support + "receipt.json"] = "f".repeat(64) },
            { it.supports.remove(support + "receipt.json") },
            { write(it.source.resolve(it.policy["builder"]["path"].asText()), "drift".toByteArray()) },
            { write(it.source.resolve(it.policy["spec"]["path"].asText()), "{}".toByteArray()) },
            { it.replaceReceipt { r -> r.put("revision", "wrong") } },
            { it.replaceReceipt { r -> r.put("unknown", true) } },
            { it.replaceReceipt { r -> (r["artifacts"][0] as ObjectNode).put("class_bytes_invariant", false) } },
            { it.replaceReceipt { r -> (r["artifacts"] as ArrayNode).remove(0) } },
            { f -> val a = f.policy["artifacts"][0]; write(f.source.resolve(support + a["output"].asText()), "wrong".toByteArray()) },
            { f -> val a = f.policy["artifacts"][0]; val path = f.root.resolve("mods/" + a["manifest"].asText());
                Files.writeString(path, Files.readString(path).replace(a["input_sha1"].asText(), "0".repeat(40))) },
            { f -> val a = f.policy["artifacts"][0]; Files.move(f.root.resolve("mods/" + a["manifest"].asText()), f.root.resolve("mods/other.pw.toml")) },
            { f -> val a = f.policy["artifacts"][0]; Files.copy(f.root.resolve("mods/" + a["manifest"].asText()), f.root.resolve("mods/duplicate.pw.toml")) },
            { f -> (f.policy["artifacts"][0] as ObjectNode).put("output", "../escape.jar"); f.savePolicy() },
            { f -> val a = f.policy["artifacts"][0].deepCopy<ObjectNode>(); (f.policy["artifacts"] as ArrayNode).set(1, a); f.savePolicy() },
        )
        mutations.forEachIndexed { index, mutate ->
            val f = Fixture(); mutate(f)
            assertThrows(Exception::class.java, { f.prepare() }, "mutation $index must fail")
            assertFalse(Files.exists(f.root.resolve(".worklane/runtime-overrides/current.path")))
        }
    }

    @Test fun actualRecursiveInventoryIsNotBorrowedFromReceiptBooleans() {
        val f = Fixture()
        val a = f.policy["artifacts"][0] as ObjectNode
        val name = a["output"].asText()
        val changed = zip(linkedMapOf("nested.jar" to zip(mapOf("Nested.class" to "changed class".toByteArray())),
            "fixed.json" to "corrected resource 0".toByteArray()))
        write(f.source.resolve(support + name), changed); f.supports[support + name] = hash(changed)
        a.put("output_sha256", hash(changed))
        (f.policy["receipt"]["artifacts"][0] as ObjectNode).put("output_sha256", hash(changed))
        f.replaceReceipt { r -> (r["artifacts"][0] as ObjectNode).put("output_sha256", hash(changed)) }
        f.savePolicy()
        assertThrows(Exception::class.java) { f.prepare() }
    }

    @Test fun providerClassPluginConfigAndManifestRegistrationAreMandatory() {
        val mutations: List<(Fixture) -> Unit> = listOf(
            { f -> val key = f.providerEntries.keys.first { it.endsWith(".class") }; f.providerEntries[key] = "wrong".toByteArray() },
            { f -> val key = f.policy["required_compat"]["plugin"].asText().replace('.', '/') + ".class"; f.providerEntries[key] = "wrong".toByteArray() },
            { f -> val key = f.policy["required_compat"]["config"].asText();
                val c = mapper.readTree(f.providerEntries.getValue(key)) as ObjectNode; c.put("required", true); f.providerEntries[key] = mapper.writeValueAsBytes(c) },
            { f -> val key = f.policy["required_compat"]["config"].asText();
                val c = mapper.readTree(f.providerEntries.getValue(key)) as ObjectNode; (c["mixins"] as ArrayNode).remove(0); f.providerEntries[key] = mapper.writeValueAsBytes(c) },
            { f -> val key = f.policy["required_compat"]["config"].asText();
                val c = mapper.readTree(f.providerEntries.getValue(key)) as ObjectNode; (c["mixins"] as ArrayNode).add(c["mixins"][0]); f.providerEntries[key] = mapper.writeValueAsBytes(c) },
            { f -> f.providerEntries["META-INF/MANIFEST.MF"] = "Manifest-Version: 1.0\r\n\r\n".toByteArray() },
        )
        mutations.forEachIndexed { index, mutate ->
            val f = Fixture(); mutate(f); write(f.providerJar, zip(f.providerEntries))
            assertThrows(Exception::class.java, { f.prepare() }, "provider mutation $index")
            f.candidates(); assertThrows(Exception::class.java) { f.validate() }
        }
    }

    @Test fun missingDuplicateOriginalCfAndWrongCandidateBytesAreRejectedOnEitherSide() {
        for (clientSide in listOf(true, false)) for (kind in 0..6) {
            val f = Fixture()
            val prefix = if (clientSide) "custom-overrides/" else "server-root/"
            val a = f.policy["artifacts"][0]
            val mutate: (LinkedHashMap<String, ByteArray>) -> Unit = { entries ->
                when (kind) {
                    0 -> entries.remove(prefix + "mods/" + a["output"].asText())
                    1 -> entries["duplicate/" + a["output"].asText()] = f.outputs.getValue(a["output"].asText())
                    2 -> entries[prefix + "mods/" + a["input"].asText()] = byteArrayOf(1)
                    3 -> entries[prefix + "mods/" + a["manifest"].asText()] = byteArrayOf(1)
                    4 -> entries[prefix + "mods/" + a["output"].asText()] = byteArrayOf(1)
                    5 -> entries[prefix + policyPath] = "{}".toByteArray()
                    6 -> entries.remove(prefix + "mods/" + f.policy["provider_artifact"].asText())
                }
            }
            if (clientSide) f.candidates(changeClient = mutate) else f.candidates(changeServer = mutate)
            assertThrows(Exception::class.java, { f.validate() }, "candidate side=$clientSide mutation=$kind")
        }
        val f = Fixture(); val a = f.policy["artifacts"][0]
        f.candidates(changeClient = { it["manifest.json"] =
            """{"overrides":"custom-overrides","files":[{"projectID":${a["project_id"]},"fileID":${a["file_id"]}}]}""".toByteArray() })
        assertThrows(Exception::class.java) { f.validate() }
    }

    @Test fun duplicateJsonUnsafeCandidatePathsAndSymlinkAncestorsFailClosed() {
        run {
            val f = Fixture(); val path = f.root.resolve(policyPath)
            Files.writeString(path, Files.readString(path).replaceFirst("{", "{\"schema\":\"duplicate\","))
            assertThrows(Exception::class.java) { f.prepare() }
        }
        run {
            val f = Fixture(); val path = f.source.resolve(support + "receipt.json")
            val destination = f.base.resolve("actual-receipt.json"); Files.move(path, destination); Files.createSymbolicLink(path, destination)
            assertThrows(Exception::class.java) { f.prepare() }
        }
        run {
            val f = Fixture(); Files.createSymbolicLink(f.evidence, Files.createDirectory(f.base.resolve("escape")))
            assertThrows(Exception::class.java) { f.prepare() }
        }
        run {
            val f = Fixture(); val pointerDir = f.root.resolve(".worklane/runtime-overrides")
            Files.createDirectories(pointerDir); Files.createSymbolicLink(pointerDir.resolve("current.path"), f.base.resolve("outside"))
            assertThrows(Exception::class.java) { f.prepare() }
            assertFalse(Files.exists(f.base.resolve("outside")))
        }
        for (name in listOf("../escape", "absolute/../../escape", "/absolute", "bad\\name")) {
            val f = Fixture(); f.candidates(changeClient = { it[name] = byteArrayOf(1) })
            assertThrows(Exception::class.java) { f.validate() }
        }
    }

    @Test fun packageOverridePureControlsAndC2meSideOnlySemanticContract() {
        val root = TestConfig.load().root
        val log = retained().resolve("python-controls.log")
        val script = """
            import pathlib, subprocess, sys, tomllib
            root = pathlib.Path(sys.argv[1])
            subprocess.run([sys.executable, '-B', str(root / 'scripts/test_package_runtime_overrides.py')], cwd=root, check=True)
            client = tomllib.loads((root / 'config/c2me.toml').read_text())
            server = tomllib.loads((root / 'server-config/config-overrides/c2me.toml').read_text())
            assert client['clientSideConfig']['modifyMaxVDConfig']['enabled'] == 'default'
            assert server['clientSideConfig']['modifyMaxVDConfig']['enabled'] is False
            server['clientSideConfig']['modifyMaxVDConfig']['enabled'] = client['clientSideConfig']['modifyMaxVDConfig']['enabled']
            assert server == client, 'C2ME server override has unrelated semantic changes'
        """.trimIndent()
        val process = ProcessBuilder("python3", "-B", "-c", script, root.toString()).directory(root.toFile())
            .redirectErrorStream(true).redirectOutput(log.toFile()).start()
        val finished = process.waitFor(60, TimeUnit.SECONDS)
        if (!finished) {
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS)
        }
        assertTrue(finished, "pure Python controls did not terminate; retained log $log")
        assertEquals(0, process.exitValue(), Files.readString(log))
    }
}
