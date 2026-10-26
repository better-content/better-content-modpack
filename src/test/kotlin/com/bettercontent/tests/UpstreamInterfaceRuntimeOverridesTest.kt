package com.bettercontent.tests

import com.bettercontent.tests.release.UpstreamInterfaceRuntimeOverrides
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Pure synthetic controls. No upstream archives, historical evidence or native qualification required. */
@Tag("fast")
@ResourceLock("bc.native.archive.cache")
class UpstreamInterfaceRuntimeOverridesTest {
    private val mapper = jacksonObjectMapper()
    private val policyPath = "config/better-content-interface-runtime-overrides.json"
    private val support = "build/upstream-interface-overrides/"
    private val ticker = "org/valkyrienskies/mod/mixin/feature/sculk/MixinVibrationSystemTicker.class"
    private fun hash(data: ByteArray, algorithm: String = "SHA-256") = MessageDigest.getInstance(algorithm).digest(data)
        .joinToString("") { "%02x".format(it) }
    private fun write(path: Path, data: ByteArray): Path { Files.createDirectories(path.parent); return Files.write(path, data) }
    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip -> entries.forEach { (name, body) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(body); zip.closeEntry()
        } }; output.toByteArray()
    }
    private fun unzip(data: ByteArray): LinkedHashMap<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes(); zip.closeEntry() }
        }; return entries
    }
    private fun inventory(data: ByteArray, prefix: String = ""): LinkedHashMap<String, String> {
        val result = linkedMapOf<String, String>()
        unzip(data).forEach { (name, body) -> when {
            name.endsWith(".class") -> result[prefix + name] = hash(body)
            name.endsWith(".jar") -> result.putAll(inventory(body, "$prefix$name!"))
        } }; return result
    }
    private fun inventoryHash(data: ByteArray): String {
        val classes = inventory(data)
        val encoded = if (classes.isEmpty()) "{}\n" else classes.entries.joinToString(",\n", "{\n", "\n}\n") {
            "  ${mapper.writeValueAsString(it.key)}: ${mapper.writeValueAsString(it.value)}"
        }; return hash(encoded.toByteArray())
    }
    private data class SyntheticClass(val data: ByteArray, val offsets: List<Int>, val names: List<String>, val descriptors: List<String>)
    private fun syntheticClass(): SyntheticClass {
        val names = listOf("destWorldPos", "destSourcePos", "destReloadSourcePos")
        val user = "(Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem\$User;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/level/gameevent/PositionSource;"
        val descriptors = listOf("(Lnet/minecraft/world/level/gameevent/vibrations/VibrationInfo;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/phys/Vec3;", user, user)
        val output = ByteArrayOutputStream(); val offsets = mutableListOf<Int>()
        DataOutputStream(output).use { out ->
            out.writeInt(0xcafebabe.toInt()); out.writeShort(0); out.writeShort(61); out.writeShort(14)
            fun utf(s: String) { out.writeByte(1); out.writeUTF(s) }
            utf("fixture/Ticker"); out.writeByte(7); out.writeShort(1)
            utf("java/lang/Object"); out.writeByte(7); out.writeShort(3)
            for (i in 0..2) { utf(names[i]); utf(descriptors[i]) }
            utf("Code"); utf("RuntimeVisibleAnnotations"); utf("Lfixture/Annotation;")
            out.writeShort(0x0601); out.writeShort(2); out.writeShort(4); out.writeShort(0); out.writeShort(0); out.writeShort(3)
            repeat(3) { index ->
                offsets.add(output.size()); out.writeShort(10); out.writeShort(5 + index * 2); out.writeShort(6 + index * 2)
                out.writeShort(2)
                out.writeShort(11); out.writeInt(14); out.writeShort(1); out.writeShort(3); out.writeInt(2)
                out.writeByte(1); out.writeByte(176); out.writeShort(0); out.writeShort(0)
                out.writeShort(12); out.writeInt(6); out.writeShort(1); out.writeShort(13); out.writeShort(0)
            }
            out.writeShort(0)
        }
        return SyntheticClass(output.toByteArray(), offsets, names, descriptors)
    }
    private fun retained() = Files.createTempDirectory(
        Files.createDirectories(Path.of("/home/dev/.tmp/interface-runtime-controls")), "case-")

    private inner class Fixture {
        val base = retained(); val root = Files.createDirectory(base.resolve("root")); val source = Files.createDirectory(base.resolve("source"))
        val cache = Files.createDirectory(base.resolve("original-cache")); val provider = base.resolve("qualified.jar")
        val evidence = base.resolve("evidence"); val client = base.resolve("client.zip"); val server = base.resolve("server.zip")
        val policy = mapper.readTree(Files.readAllBytes(TestConfig.load().root.resolve(policyPath))) as ObjectNode
        val supports = linkedMapOf<String, String>(); val outputs = linkedMapOf<String, ByteArray>(); val originalEntries = mutableListOf<Map<String, ByteArray>>()
        val synthetic = syntheticClass()
        init {
            val transfer = policy["artifacts"][1]["interface_transfer"] as ObjectNode
            val bindings = transfer["provider_entries"] as ObjectNode
            val providerEntries = linkedMapOf<String, ByteArray>()
            for (path in bindings.fieldNames().asSequence().toList()) {
                val body = if (path.endsWith(".json")) """{"required":true,"minVersion":"0.8","package":"com.bettercontent.bettercompatfixes.mixin.vibration","compatibilityLevel":"JAVA_17","refmap":"better_compat_fixes.refmap.json","plugin":"com.bettercontent.bettercompatfixes.compat.vibration.VibrationInterfaceMixinPlugin","mixins":["VibrationTickerMixin"],"injectors":{"defaultRequire":1}}""".toByteArray()
                    else "synthetic compiled provider $path".toByteArray()
                providerEntries[path] = body; bindings.put(path, hash(body))
            }
            providerEntries["META-INF/MANIFEST.MF"] = "Manifest-Version: 1.0\r\nMixinConfigs: better_compat_fixes.vibration_interface.mixins.json\r\n\r\n".toByteArray()
            write(provider, zip(providerEntries))
            (policy["receipt"]["artifacts"][1]["interface_transfer"] as ObjectNode).set<JsonNode>("provider_entries", bindings.deepCopy())
            for (key in listOf("spec", "builder", "legacy_builder")) {
                val bytes = "synthetic $key source".toByteArray()
                write(source.resolve(policy[key]["path"].asText()), bytes)
                (policy[key] as ObjectNode).put("sha256", hash(bytes))
                (policy["receipt"] as ObjectNode).put(if (key == "spec") "spec_sha256" else if (key == "builder") "builder_sha256" else "legacy_builder_sha256", hash(bytes))
            }
            policy["artifacts"].forEachIndexed { index, node ->
                val a = node as ObjectNode; val r = policy["receipt"]["artifacts"][index] as ObjectNode
                val moved = a["resource"]["move_to_client"][0].asText()
                val oldJson = mapper.createObjectNode().put("required", index == 1).put("plugin", "fixture.Plugin")
                oldJson.putArray("mixins").add("OtherCommon").add(moved); oldJson.putArray("client").add("OtherClient")
                if (index == 1) (oldJson["mixins"] as ArrayNode).add("feature.sculk.MixinVibrationSystemTicker")
                val newJson = oldJson.deepCopy(); newJson.putArray("mixins").add("OtherCommon"); newJson.putArray("client").add("OtherClient").add(moved)
                val oldResource = mapper.writeValueAsBytes(oldJson); val newResource = mapper.writeValueAsBytes(newJson)
                val resourcePath = a["resource"]["path"].asText()
                val before = linkedMapOf(resourcePath to oldResource, "META-INF/LICENSE.txt" to "preserved notice".toByteArray())
                if (index == 0) before["nested.jar"] = zip(mapOf("Unchanged.class" to "class bytes".toByteArray()))
                else before[ticker] = synthetic.data
                originalEntries.add(before)
                val after = LinkedHashMap(before); after[resourcePath] = newResource
                val original = zip(before); val corrected = zip(after)
                write(cache.resolve(a["input"].asText()), original)
                a.put("input_sha1", hash(original, "SHA-1")).put("input_sha256", hash(original)).put("output_sha256", hash(corrected))
                val resource = a["resource"] as ObjectNode
                resource.put("before_sha256", hash(oldResource)).put("after_sha256", hash(newResource))
                (r["resources"][0] as ObjectNode).put("before_sha256", hash(oldResource)).put("after_sha256", hash(newResource))
                r.put("input_sha256", hash(original)).put("output_sha256", hash(corrected)).put("output_sha1", hash(corrected, "SHA-1"))
                    .put("class_count", inventory(original).size).put("before_class_inventory_sha256", inventoryHash(original))
                    .put("after_class_inventory_sha256", inventoryHash(corrected))
                if (index == 1) {
                    (a["interface_transfer"] as ObjectNode).put("original_class_sha256", hash(synthetic.data))
                    (r["interface_transfer"] as ObjectNode).put("original_class_sha256", hash(synthetic.data))
                }
                outputs[a["output"].asText()] = corrected
                write(root.resolve("mods/" + a["manifest"].asText()), """
                    name = "synthetic"
                    filename = "${a["input"].asText()}"
                    side = "both"
                    [download]
                    hash-format = "sha1"
                    hash = "${a["input_sha1"].asText()}"
                    mode = "metadata:curseforge"
                    [update.curseforge]
                    project-id = ${a["project_id"].asText()}
                    file-id = ${a["file_id"].asText()}
                """.trimIndent().toByteArray())
            }
            save()
        }
        fun save() {
            write(root.resolve(policyPath), mapper.writeValueAsBytes(policy))
            outputs.forEach { (name, body) -> write(source.resolve(support + name), body); supports[support + name] = hash(body) }
            val body = mapper.writeValueAsBytes(policy["receipt"]); write(source.resolve(support + "receipt.json"), body); supports[support + "receipt.json"] = hash(body)
        }
        fun <T> usingCache(block: () -> T): T {
            val previous = System.getProperty("bc.native.archive.cache")
            try { System.setProperty("bc.native.archive.cache", cache.toString()); return block() }
            finally { if (previous == null) System.clearProperty("bc.native.archive.cache") else System.setProperty("bc.native.archive.cache", previous) }
        }
        fun prepare() = usingCache { UpstreamInterfaceRuntimeOverrides.prepare(root, source, evidence, provider, supports) }
        fun entries(clientSide: Boolean): LinkedHashMap<String, ByteArray> {
            val prefix = if (clientSide) "custom/" else "server/"
            val result = linkedMapOf(prefix + policyPath to Files.readAllBytes(root.resolve(policyPath)),
                prefix + "mods/" + policy["provider_artifact"].asText() to Files.readAllBytes(provider))
            outputs.forEach { (name, body) -> result[prefix + "mods/" + name] = body }
            if (clientSide) result["manifest.json"] = """{"overrides":"custom","files":[]}""".toByteArray()
            return result
        }
        fun candidates(clientMutation: (LinkedHashMap<String, ByteArray>) -> Unit = {}, serverMutation: (LinkedHashMap<String, ByteArray>) -> Unit = {}) {
            val c = entries(true); clientMutation(c); write(client, zip(c))
            val s = entries(false); serverMutation(s); write(server, zip(s))
        }
        fun validate() = usingCache { UpstreamInterfaceRuntimeOverrides.validateCandidates(root, client, server) }
        // Rebind whole output hashes/inventories so negatives reach the structural oracle,
        // rather than merely failing at a digest check. Original pinned bytes remain unchanged.
        fun mutateCorrected(index: Int, mutation: (LinkedHashMap<String, ByteArray>) -> Unit) {
            val a = policy["artifacts"][index] as ObjectNode; val row = policy["receipt"]["artifacts"][index] as ObjectNode
            val output = a["output"].asText(); val entries = unzip(outputs.getValue(output)); mutation(entries)
            val body = zip(entries); outputs[output] = body; a.put("output_sha256", hash(body))
            row.put("output_sha256", hash(body)).put("output_sha1", hash(body, "SHA-1")).put("after_class_inventory_sha256", inventoryHash(body))
            val resource = a["resource"] as ObjectNode
            resource.put("after_sha256", hash(entries.getValue(resource["path"].asText())))
            (row["resources"][0] as ObjectNode).put("after_sha256", resource["after_sha256"].asText())
            save()
        }
    }

    @Test fun validIndependentPreparationAndPairValidationRetainSourceAndPriorSnapshots() {
        val f = Fixture(); val original = Files.readAllBytes(f.cache.resolve(f.policy["artifacts"][1]["input"].asText()))
        val prepared = f.prepare()
        assertEquals("${prepared.directory}\n", Files.readString(f.root.resolve(".worklane/interface-overrides/current.path")))
        assertEquals(3, Files.list(prepared.directory).use { it.filter { p -> p.fileName.toString() != "selection.json" }.count().toInt() })
        assertEquals(hash(Files.readAllBytes(f.provider)), prepared.provenance["qualified_provider_sha256"])
        assertEquals("NOT_EVALUATED", prepared.provenance["runtime_acceptance"])
        assertArrayEquals(original, Files.readAllBytes(f.cache.resolve(f.policy["artifacts"][1]["input"].asText())))
        assertThrows(Exception::class.java) { f.prepare() }
        f.candidates(); val before = hash(Files.readAllBytes(f.client)) to hash(Files.readAllBytes(f.server))
        f.validate(); assertEquals(before, hash(Files.readAllBytes(f.client)) to hash(Files.readAllBytes(f.server)))
        val next = f.usingCache { UpstreamInterfaceRuntimeOverrides.prepare(f.root, f.source, f.base.resolve("next"), f.provider, f.supports) }
        assertNotEquals(next.directory, prepared.directory); assertTrue(Files.exists(prepared.directory.resolve("receipt.json")))
    }

    @Test fun originalInterfaceMustRetainExactPrivateStaticFlagsAndEveryByte() {
        val input = syntheticClass(); val result = UpstreamInterfaceRuntimeOverrides.expectedInterfaceClass(input.data)
        assertArrayEquals(input.data, result)
        for (index in input.offsets.indices) for (access in listOf(1, 2, 8, 9, 0x100a)) {
            val bad = input.data.copyOf(); val offset = input.offsets[index]
            bad[offset] = (access ushr 8).toByte(); bad[offset + 1] = access.toByte()
            assertThrows(Exception::class.java) { UpstreamInterfaceRuntimeOverrides.expectedInterfaceClass(bad) }
        }
        for (bad in listOf(input.data.copyOf(20), input.data + byteArrayOf(1), input.data.copyOf().also { it[7] = 60 },
            input.data.copyOf().also { it[input.offsets.first() - 1] = 4 }))
            assertThrows(Exception::class.java) { UpstreamInterfaceRuntimeOverrides.expectedInterfaceClass(bad) }
        // Same-length method-name/descriptor mutation cannot be mistaken for the reviewed methods.
        for (needle in listOf("destSourcePos", "Lnet/minecraft/server/level/ServerLevel;")) {
            val bytes = input.data.copyOf(); val target = needle.toByteArray()
            val offset = bytes.indices.first { i -> i + target.size <= bytes.size && bytes.copyOfRange(i, i + target.size).contentEquals(target) }
            bytes[offset] = 'X'.code.toByte()
            assertThrows(Exception::class.java) { UpstreamInterfaceRuntimeOverrides.expectedInterfaceClass(bytes) }
        }
    }

    @Test fun syntheticFlagsAreNotASolutionToUnsupportedInterfaceInjectors() {
        // Native dd3a2d57 failed the third PREINJECT phase; two predicates are insufficient.
        fun prepares(flags: Int) = flags and (0x0001 or 0x1000) != 0
        fun applies(flags: Int) = flags and 0x0008 == 0 || flags and 0x0002 != 0
        assertFalse(prepares(0x000a))
        assertTrue(prepares(0x0009)); assertFalse(applies(0x0009))
        assertTrue(prepares(0x100a)); assertTrue(applies(0x100a))
        val interfaceInjectorsSupportedIn085 = false
        assertFalse(prepares(0x100a) && applies(0x100a) && interfaceInjectorsSupportedIn085)
    }

    @Test fun codeAnnotationDescriptorStaticBitAndUnknownClassChangesAreNotExempted() {
        val mutations: List<(Fixture) -> Unit> = listOf(
            { f -> f.mutateCorrected(1) { e -> e[ticker] = e.getValue(ticker).copyOf().also { it[f.synthetic.offsets[0]] = 0; it[f.synthetic.offsets[0] + 1] = 9 } } },
            { f -> f.mutateCorrected(1) { e -> e[ticker] = e.getValue(ticker).copyOf().also { it[f.synthetic.offsets[0]] = 0x10 } } },
            { f -> f.mutateCorrected(1) { e -> e[ticker] = e.getValue(ticker).copyOf().also { it[f.synthetic.offsets[0] + 22] = 0 } } },
            { f -> f.mutateCorrected(1) { e -> e[ticker] = e.getValue(ticker).copyOf().also { it[f.synthetic.offsets[0] + 37] = 12 } } },
            { f -> f.mutateCorrected(1) { e -> e[ticker] = e.getValue(ticker).copyOf().also { it[f.synthetic.offsets[0] + 5] = 8 } } },
            { f -> f.mutateCorrected(1) { e -> e[ticker] = e.getValue(ticker).copyOf().also { it[f.synthetic.offsets[0] + 1] = 1 } } },
            { f -> f.mutateCorrected(0) { e -> e["nested.jar"] = zip(mapOf("Unchanged.class" to "changed".toByteArray())) } },
            { f -> f.mutateCorrected(1) { e -> e["unreviewed.class"] = byteArrayOf(1) } },
            { f -> f.mutateCorrected(1) { e -> e["META-INF/LICENSE.txt"] = "changed notice".toByteArray() } },
            { f -> f.mutateCorrected(0) { e -> val path = f.policy["artifacts"][0]["resource"]["path"].asText()
                val node = mapper.readTree(e.getValue(path)) as ObjectNode; node.put("required", true); e[path] = mapper.writeValueAsBytes(node) } },
        )
        mutations.forEachIndexed { index, mutate -> val f = Fixture(); mutate(f)
            assertThrows(Exception::class.java, { f.prepare() }, "structural mutation $index")
            f.candidates(); assertThrows(Exception::class.java) { f.validate() }
        }
    }

    @Test fun sourceSupportReceiptOriginalCacheAndCurrentPinsMustAllMatch() {
        val mutations: List<(Fixture) -> Unit> = listOf(
            { it.supports[support + "receipt.json"] = "f".repeat(64) },
            { it.supports.remove(support + "receipt.json") },
            { f -> write(f.source.resolve(f.policy["builder"]["path"].asText()), byteArrayOf(1)) },
            { f -> write(f.source.resolve(f.policy["legacy_builder"]["path"].asText()), byteArrayOf(1)) },
            { f -> write(f.source.resolve(f.policy["spec"]["path"].asText()), byteArrayOf(1)) },
            { f -> write(f.cache.resolve(f.policy["artifacts"][0]["input"].asText()), byteArrayOf(1)) },
            { f -> (f.policy["receipt"] as ObjectNode).put("unexpected", true); f.save() },
            { f -> (f.policy["receipt"]["artifacts"][1]["class_access_patches"] as ArrayNode).addObject().put("after", 4106); f.save() },
            { f -> val e = unzip(Files.readAllBytes(f.provider)); e["com/bettercontent/bettercompatfixes/compat/vibration/VibrationWorldCoordinates.class"] = byteArrayOf(1); write(f.provider, zip(e)) },
            { f -> val e = unzip(Files.readAllBytes(f.provider)); e["META-INF/MANIFEST.MF"] = "MixinConfigs: other.json\r\n".toByteArray(); write(f.provider, zip(e)) },
            { f -> val path = f.source.resolve(support + "receipt.json"); val r = mapper.readTree(Files.readAllBytes(path)) as ObjectNode
                r.put("python", "wrong"); val body = mapper.writeValueAsBytes(r); write(path, body); f.supports[support + "receipt.json"] = hash(body) },
            { f -> val a = f.policy["artifacts"][0]; val path = f.root.resolve("mods/" + a["manifest"].asText())
                Files.writeString(path, Files.readString(path).replace(a["input_sha1"].asText(), "f".repeat(40))) },
            { f -> val a = f.policy["artifacts"][0]; Files.copy(f.root.resolve("mods/" + a["manifest"].asText()), f.root.resolve("mods/duplicate.pw.toml")) },
            { f -> val a = f.policy["artifacts"][0]; Files.move(f.root.resolve("mods/" + a["manifest"].asText()), f.base.resolve("retained-original-pin.toml")) },
        )
        mutations.forEachIndexed { index, mutate -> val f = Fixture(); mutate(f)
            assertThrows(Exception::class.java, { f.prepare() }, "source/metadata mutation $index")
            assertFalse(Files.exists(f.root.resolve(".worklane/interface-overrides/current.path")))
        }
    }

    @Test fun transferScopeAndProviderConfigRemainStrictEvenWithReboundProviderEntryHashes() {
        for (kind in 0..3) {
            val f = Fixture(); val transfer = f.policy["artifacts"][1]["interface_transfer"] as ObjectNode
            when (kind) {
                0 -> transfer.put("mixin", "other.Mixin")
                1 -> (transfer["provider_entries"] as ObjectNode).remove("com/bettercontent/bettercompatfixes/compat/vibration/VibrationWorldCoordinates.class")
                2 -> transfer.put("provider_artifact", "other.jar")
                3 -> {
                    val entries = unzip(Files.readAllBytes(f.provider)); val path = "better_compat_fixes.vibration_interface.mixins.json"
                    val cfg = mapper.readTree(entries.getValue(path)) as ObjectNode; cfg.put("required", false)
                    entries[path] = mapper.writeValueAsBytes(cfg); write(f.provider, zip(entries))
                    (transfer["provider_entries"] as ObjectNode).put(path, hash(entries.getValue(path)))
                }
            }
            (f.policy["receipt"]["artifacts"][1] as ObjectNode).set<JsonNode>("interface_transfer", transfer.deepCopy()); f.save()
            assertThrows(Exception::class.java) { f.prepare() }
            f.candidates(); assertThrows(Exception::class.java) { f.validate() }
        }
    }

    @Test fun reboundOriginalAndCorrectedHashesNeverAdmitPublicOrSyntheticOriginalHandlers() {
        for (flags in listOf(9, 0x100a, 1)) {
            val f = Fixture(); val a = f.policy["artifacts"][1] as ObjectNode; val row = f.policy["receipt"]["artifacts"][1] as ObjectNode
            val changed = f.synthetic.data.copyOf()
            for (offset in f.synthetic.offsets) { changed[offset] = (flags ushr 8).toByte(); changed[offset + 1] = flags.toByte() }
            val before = LinkedHashMap(f.originalEntries[1]); before[ticker] = changed
            val original = zip(before); val oldSha1 = a["input_sha1"].asText()
            write(f.cache.resolve(a["input"].asText()), original)
            a.put("input_sha1", hash(original, "SHA-1")).put("input_sha256", hash(original))
            row.put("input_sha256", hash(original)).put("before_class_inventory_sha256", inventoryHash(original))
            val pin = f.root.resolve("mods/" + a["manifest"].asText())
            Files.writeString(pin, Files.readString(pin).replace(oldSha1, a["input_sha1"].asText()))
            (a["interface_transfer"] as ObjectNode).put("original_class_sha256", hash(changed))
            (row["interface_transfer"] as ObjectNode).put("original_class_sha256", hash(changed))
            f.mutateCorrected(1) { it[ticker] = changed }
            assertThrows(Exception::class.java) { f.prepare() }
            f.candidates(); assertThrows(Exception::class.java) { f.validate() }
        }
    }

    @Test fun bothCandidateSidesRejectMissingDuplicateOriginalTupleAndWrongPolicy() {
        for (clientSide in listOf(true, false)) for (kind in 0..8) {
            val f = Fixture(); val prefix = if (clientSide) "custom/" else "server/"; val a = f.policy["artifacts"][0]
            val mutate: (LinkedHashMap<String, ByteArray>) -> Unit = { entries -> when (kind) {
                0 -> entries.remove(prefix + "mods/" + a["output"].asText())
                1 -> entries["duplicate/" + a["output"].asText()] = f.outputs.getValue(a["output"].asText())
                2 -> entries[prefix + "mods/" + a["input"].asText()] = byteArrayOf(1)
                3 -> entries[prefix + "mods/" + a["manifest"].asText()] = byteArrayOf(1)
                4 -> entries[prefix + policyPath] = "{}".toByteArray()
                5 -> entries[prefix + "mods/" + a["output"].asText()] = byteArrayOf(1)
                6 -> entries.remove(prefix + "mods/" + f.policy["provider_artifact"].asText())
                7 -> entries["duplicate/" + f.policy["provider_artifact"].asText()] = Files.readAllBytes(f.provider)
                8 -> entries[prefix + "mods/" + f.policy["provider_artifact"].asText()] = zip(mapOf("wrong.class" to byteArrayOf(1)))
            }; Unit }
            if (clientSide) f.candidates(clientMutation = mutate) else f.candidates(serverMutation = mutate)
            assertThrows(Exception::class.java, { f.validate() }, "candidate $clientSide mutation $kind")
        }
        val f = Fixture(); val a = f.policy["artifacts"][0]
        f.candidates(clientMutation = { it["manifest.json"] = """{"overrides":"custom","files":[{"projectID":${a["project_id"]},"fileID":${a["file_id"]}}]}""".toByteArray() })
        assertThrows(Exception::class.java) { f.validate() }
        val duplicate = Fixture()
        duplicate.candidates(clientMutation = { it["dupa"] = byteArrayOf(1); it["dupb"] = byteArrayOf(2) })
        val zipBytes = Files.readAllBytes(duplicate.client)
        val pattern = "dupb".toByteArray()
        for (offset in 0..zipBytes.size - pattern.size) {
            if (zipBytes.copyOfRange(offset, offset + pattern.size).contentEquals(pattern)) zipBytes[offset + 3] = 'a'.code.toByte()
        }
        Files.write(duplicate.client, zipBytes)
        assertThrows(Exception::class.java) { duplicate.validate() }
    }

    @Test fun symlinkAndTraversalInputsAndDuplicateJsonFailBeforePublication() {
        val mutations: List<(Fixture) -> Unit> = listOf(
            { f -> val path = f.source.resolve(support + "receipt.json"); val actual = f.base.resolve("receipt-real.json"); Files.move(path, actual); Files.createSymbolicLink(path, actual) },
            { f -> Files.createSymbolicLink(f.evidence, Files.createDirectory(f.base.resolve("outside"))) },
            { f -> val path = f.cache.resolve(f.policy["artifacts"][0]["input"].asText()); val actual = f.base.resolve("original-real.jar"); Files.move(path, actual); Files.createSymbolicLink(path, actual) },
            { f -> (f.policy["artifacts"][0] as ObjectNode).put("output", "../escape.jar"); f.save() },
            { f -> val path = f.root.resolve(policyPath); Files.writeString(path, Files.readString(path).replaceFirst("{", "{\"schema\":\"duplicate\",")) },
            { f -> val a = (f.policy["artifacts"][0] as ObjectNode).deepCopy(); (f.policy["artifacts"] as ArrayNode).set(1, a); f.save() },
        )
        for (mutate in mutations) { val f = Fixture(); mutate(f); assertThrows(Exception::class.java) { f.prepare() } }
        for (unsafe in listOf("../escape", "/absolute", "bad\\name")) {
            val f = Fixture(); f.candidates(clientMutation = { it[unsafe] = byteArrayOf(1) }); assertThrows(Exception::class.java) { f.validate() }
        }
    }
}
