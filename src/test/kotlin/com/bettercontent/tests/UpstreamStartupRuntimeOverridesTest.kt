package com.bettercontent.tests

import com.bettercontent.tests.release.UpstreamStartupRuntimeOverrides
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

/** Synthetic receiver controls; real pinned Forge scan and Maven union controls belong to Source. */
@Tag("fast")
@ResourceLock("bc.native.archive.cache")
class UpstreamStartupRuntimeOverridesTest {
    private val mapper = jacksonObjectMapper()
    private val policyPath = "config/better-content-startup-runtime-overrides.json"
    private val support = "build/upstream-startup-overrides/"
    private val target = "dev/uncandango/alltheleaks/leaks/common/mods/citadel/UntrackedIssue001.class"
    private val before = "[2.5.4,)"; private val after = "[2.5.4,2.6.3),(2.6.3,)"
    private fun hash(data: ByteArray, algorithm: String = "SHA-256") = MessageDigest.getInstance(algorithm).digest(data).joinToString("") { "%02x".format(it) }
    private fun write(path: Path, data: ByteArray): Path { Files.createDirectories(path.parent); return Files.write(path, data) }
    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { z -> entries.forEach { (name, data) -> z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry() } }; output.toByteArray()
    }
    private fun unzip(data: ByteArray): LinkedHashMap<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(data)).use { z -> while (true) { val e = z.nextEntry ?: break; out[e.name] = z.readBytes(); z.closeEntry() } }; return out
    }
    private fun inventory(data: ByteArray, prefix: String = ""): LinkedHashMap<String, String> {
        val result = linkedMapOf<String, String>()
        for ((name, body) in unzip(data)) when {
            name.endsWith(".class") -> result[prefix + name] = hash(body)
            name.endsWith(".jar") -> result.putAll(inventory(body, "$prefix$name!"))
        }; return result
    }
    private fun inventoryHash(data: ByteArray): String {
        val values = inventory(data)
        return hash((if (values.isEmpty()) "{}\n" else values.entries.joinToString(",\n", "{\n", "\n}\n") {
            "  ${mapper.writeValueAsString(it.key)}: ${mapper.writeValueAsString(it.value)}"
        }).toByteArray())
    }
    private data class Synthetic(val bytes: ByteArray, val rangeOffset: Int)
    private fun synthetic(abi: Boolean = false): Synthetic {
        val output = ByteArrayOutputStream(); var offset = 0
        DataOutputStream(output).use { out ->
            out.writeInt(0xcafebabe.toInt()); out.writeShort(0); out.writeShort(61); out.writeShort(18)
            fun utf(value: String) { out.writeByte(1); out.writeUTF(value) }
            utf("fixture/Issue"); out.writeByte(7); out.writeShort(1)
            utf("java/lang/Object"); out.writeByte(7); out.writeShort(3)
            utf("Ldev/uncandango/alltheleaks/annotation/Issue;"); utf("modId"); utf("citadel"); utf("versionRange")
            out.writeByte(1); offset = output.size(); out.writeUTF(before)
            utf("description"); utf("fixture description"); utf("Code"); utf("<init>"); utf("()V")
            utf("server"); utf("Ljava/lang/Object;"); utf("RuntimeVisibleAnnotations")
            out.writeShort(33); out.writeShort(2); out.writeShort(4); out.writeShort(0)
            if (abi) {
                out.writeShort(1); out.writeShort(2); out.writeShort(15); out.writeShort(16); out.writeShort(0)
                out.writeShort(0); out.writeShort(0)
            } else {
                out.writeShort(0); out.writeShort(1)
                out.writeShort(1); out.writeShort(13); out.writeShort(14); out.writeShort(1)
                out.writeShort(12); out.writeInt(13); out.writeShort(0); out.writeShort(1); out.writeInt(1)
                out.writeByte(177); out.writeShort(0); out.writeShort(0)
                out.writeShort(1); out.writeShort(17); out.writeInt(21); out.writeShort(1); out.writeShort(5); out.writeShort(3)
                for ((key, value) in listOf(6 to 7, 8 to 9, 10 to 11)) { out.writeShort(key); out.writeByte('s'.code); out.writeShort(value) }
            }
        }; return Synthetic(output.toByteArray(), offset)
    }
    private fun expected(input: Synthetic): ByteArray {
        val replacement = after.toByteArray()
        return input.bytes.copyOfRange(0, input.rangeOffset) + byteArrayOf(0, replacement.size.toByte()) + replacement +
            input.bytes.copyOfRange(input.rangeOffset + 2 + before.length, input.bytes.size)
    }
    private fun retained(): Path = Files.createTempDirectory(Files.createDirectories(Path.of("/home/dev/.tmp/startup-receiver-controls")), "case-")
    private inner class Fixture {
        val base = retained(); val root = Files.createDirectory(base.resolve("root")); val source = Files.createDirectory(base.resolve("source"))
        val cache = Files.createDirectory(base.resolve("cache")); val evidence = base.resolve("evidence")
        val provider = write(base.resolve("provider.jar"), zip(mapOf("fixture.class" to byteArrayOf(1))))
        val policy = mapper.readTree(Files.readAllBytes(TestConfig.load().root.resolve(policyPath))) as ObjectNode
        val supports = linkedMapOf<String, String>(); val input = synthetic(); val abi = synthetic(true)
        val artifact = policy["artifacts"][0] as ObjectNode; val citadel = policy["citadel"] as ObjectNode
        var corrected: ByteArray; val raw: ByteArray; val rawCitadel: ByteArray
        val client = base.resolve("client.zip"); val server = base.resolve("server.zip")
        init {
            val entries = linkedMapOf(target to input.bytes, "nested.jar" to zip(mapOf("Unchanged.class" to "unchanged".toByteArray())),
                "META-INF/NOTICE" to "retained notice".toByteArray())
            raw = zip(entries); entries[target] = expected(input); corrected = zip(entries)
            rawCitadel = zip(mapOf(citadel["class"].asText() to abi.bytes))
            artifact.put("input_sha1", hash(raw, "SHA-1")).put("input_sha256", hash(raw)).put("output_sha256", hash(corrected))
            citadel.put("input_sha1", hash(rawCitadel, "SHA-1")).put("input_sha256", hash(rawCitadel)).put("class_sha256", hash(abi.bytes))
            (policy["patch"] as ObjectNode).put("before_sha256", hash(input.bytes))
            val receipt = policy["receipt"] as ObjectNode
            for (key in listOf("spec", "builder", "legacy_builder")) {
                val data = "synthetic $key".toByteArray(); write(source.resolve(policy[key]["path"].asText()), data)
                (policy[key] as ObjectNode).put("sha256", hash(data))
                receipt.put(if (key == "spec") "spec_sha256" else if (key == "builder") "builder_sha256" else "legacy_builder_sha256", hash(data))
            }
            val c = receipt["citadel_abi"] as ObjectNode
            c.put("sha256", hash(rawCitadel)).put("class_sha256", hash(abi.bytes)); c.putArray("field_names").add("server")
            val row = receipt["artifacts"][0] as ObjectNode
            row.put("input_sha256", hash(raw)).put("output_sha256", hash(corrected)).put("output_sha1", hash(corrected, "SHA-1"))
                .put("class_count", 2).put("before_class_inventory_sha256", inventoryHash(raw)).put("after_class_inventory_sha256", inventoryHash(corrected))
            (row["annotation_patch"] as ObjectNode).put("before_sha256", hash(input.bytes)).put("after_sha256", hash(expected(input)))
                .put("constant_pool_index", 9).put("utf8_length_offset", input.rangeOffset)
            write(cache.resolve(artifact["input"].asText()), raw); write(cache.resolve(citadel["input"].asText()), rawCitadel)
            for (item in listOf(artifact, citadel)) write(root.resolve("mods/" + item["manifest"].asText()), """
                name = "fixture"
                filename = "${item["input"].asText()}"
                side = "both"
                [download]
                hash-format = "sha1"
                hash = "${item["input_sha1"].asText()}"
                mode = "metadata:curseforge"
                [update.curseforge]
                project-id = ${item["project_id"].asText()}
                file-id = ${item["file_id"].asText()}
            """.trimIndent().toByteArray())
            save()
        }
        fun save() {
            write(root.resolve(policyPath), mapper.writeValueAsBytes(policy))
            val r = mapper.writeValueAsBytes(policy["receipt"]); write(source.resolve(support + "receipt.json"), r); supports[support + "receipt.json"] = hash(r)
            write(source.resolve(support + artifact["output"].asText()), corrected); supports[support + artifact["output"].asText()] = hash(corrected)
        }
        fun <T> cached(block: () -> T): T {
            val previous = System.getProperty("bc.native.archive.cache")
            try { System.setProperty("bc.native.archive.cache", cache.toString()); return block() }
            finally { if (previous == null) System.clearProperty("bc.native.archive.cache") else System.setProperty("bc.native.archive.cache", previous) }
        }
        fun prepare() = cached { UpstreamStartupRuntimeOverrides.prepare(root, source, evidence, provider, supports) }
        fun candidates(changeClient: (LinkedHashMap<String, ByteArray>) -> Unit = {}, changeServer: (LinkedHashMap<String, ByteArray>) -> Unit = {}) {
            val c = linkedMapOf("custom/" + policyPath to Files.readAllBytes(root.resolve(policyPath)), "custom/mods/" + artifact["output"].asText() to corrected,
                "manifest.json" to """{"overrides":"custom","files":[{"projectID":${citadel["project_id"]},"fileID":${citadel["file_id"]}}]}""".toByteArray())
            val s = linkedMapOf("server/" + policyPath to Files.readAllBytes(root.resolve(policyPath)), "server/mods/" + artifact["output"].asText() to corrected,
                "server/mods/" + citadel["input"].asText() to rawCitadel)
            changeClient(c); changeServer(s); write(client, zip(c)); write(server, zip(s))
        }
        fun validate() = cached { UpstreamStartupRuntimeOverrides.validateCandidates(root, client, server) }
        fun mutateCorrected(mutate: (LinkedHashMap<String, ByteArray>) -> Unit) {
            val entries = unzip(corrected); mutate(entries); corrected = zip(entries)
            artifact.put("output_sha256", hash(corrected))
            val row = policy["receipt"]["artifacts"][0] as ObjectNode
            row.put("output_sha256", hash(corrected)).put("output_sha1", hash(corrected, "SHA-1")).put("after_class_inventory_sha256", inventoryHash(corrected))
            (row["annotation_patch"] as ObjectNode).put("after_sha256", hash(entries.getValue(target))); save()
        }
    }

    @Test fun exactPreparationAndBothSidesPreserveOriginalBytesAndPublishNoVerdict() {
        val f = Fixture(); val result = f.prepare()
        assertEquals("${result.directory}\n", Files.readString(f.root.resolve(".worklane/startup-overrides/current.path")))
        assertEquals("NOT_EVALUATED", result.provenance["runtime_acceptance"]); assertEquals("LOCAL_ONLY_NOT_AUTHORIZED", result.provenance["publication"])
        assertArrayEquals(f.raw, Files.readAllBytes(f.cache.resolve(f.artifact["input"].asText())))
        assertEquals(1, mapper.readTree(Files.readAllBytes(result.directory.resolve("selection.json")))["artifacts"].size())
        assertThrows(Exception::class.java) { f.prepare() }
        f.candidates(); val hashes = hash(Files.readAllBytes(f.client)) to hash(Files.readAllBytes(f.server)); f.validate()
        assertEquals(hashes, hash(Files.readAllBytes(f.client)) to hash(Files.readAllBytes(f.server)))
    }

    @Test fun parserDerivesOnlyTheIssueUtf8OperandAndRejectsMalformedOrWrongIssue() {
        val raw = synthetic(); val result = UpstreamStartupRuntimeOverrides.expectedAnnotationClass(raw.bytes)
        assertArrayEquals(expected(raw), result.bytes); assertEquals(9, result.constantPoolIndex); assertEquals(raw.rangeOffset, result.lengthOffset)
        for (bad in listOf(raw.bytes.copyOf(20), raw.bytes + byteArrayOf(1), raw.bytes.copyOf().also { it[0] = 0 },
            raw.bytes.copyOf().also { it[raw.rangeOffset + 2] = 'X'.code.toByte() },
            raw.bytes.copyOf().also { it[it.lastIndex] = 9 }, // description shares versionRange operand
            raw.bytes.copyOf().also { it[it.size - 4] = 8 })) // duplicate versionRange annotation key
            assertThrows(Exception::class.java) { UpstreamStartupRuntimeOverrides.expectedAnnotationClass(bad) }
        val wrong = raw.bytes.copyOf(); val word = "citadel".toByteArray()
        val offset = wrong.indices.first { it + word.size <= wrong.size && wrong.copyOfRange(it, it + word.size).contentEquals(word) }
        wrong[offset] = 'X'.code.toByte()
        assertThrows(Exception::class.java) { UpstreamStartupRuntimeOverrides.expectedAnnotationClass(wrong) }
    }

    @Test fun methodCodeOtherAnnotationsClassesAndNoticesCannotBeChangedEvenWithReboundHashes() {
        val mutations: List<(LinkedHashMap<String, ByteArray>) -> Unit> = listOf(
            { it[target] = it.getValue(target).copyOf().also { b -> val offset = b.indices.first { i -> i + 5 < b.size && b[i] == 177.toByte() && b[i + 1] == 0.toByte() && b[i + 2] == 0.toByte() }; b[offset] = 0 } },
            { it[target] = it.getValue(target) + byteArrayOf(1) },
            { it["nested.jar"] = zip(mapOf("Unchanged.class" to "changed class".toByteArray())) },
            { it["META-INF/NOTICE"] = "changed notice".toByteArray() },
            { it["unknown.class"] = byteArrayOf(1) },
        )
        for ((index, mutate) in mutations.withIndex()) {
            val f = Fixture(); f.mutateCorrected(mutate)
            assertThrows(Exception::class.java, { f.prepare() }, "rebound structural mutation $index")
            f.candidates(); assertThrows(Exception::class.java) { f.validate() }
        }
    }

    @Test fun sourceReceiptSupportOriginalAndCitadelPinsAreExactNoRawProviderNormalization() {
        val mutations: List<(Fixture) -> Unit> = listOf(
            { it.supports[support + "receipt.json"] = "f".repeat(64) },
            { it.supports.remove(support + "receipt.json") },
            { it.supports[support + "unknown.jar"] = "f".repeat(64) },
            { f -> write(f.source.resolve(f.policy["builder"]["path"].asText()), byteArrayOf(1)) },
            { f -> write(f.source.resolve(f.policy["legacy_builder"]["path"].asText()), byteArrayOf(1)) },
            { f -> write(f.cache.resolve(f.citadel["input"].asText()), byteArrayOf(1)) },
            { f -> write(f.cache.resolve(f.artifact["input"].asText()), byteArrayOf(1)) },
            { f -> val path = f.source.resolve(support + "receipt.json"); val r = mapper.readTree(Files.readAllBytes(path)) as ObjectNode
                r.put("raw_provider_sha256", "f".repeat(64)); val body = mapper.writeValueAsBytes(r); write(path, body); f.supports[support + "receipt.json"] = hash(body) },
            { f -> (f.policy["receipt"]["citadel_abi"] as ObjectNode).put("dataMap_present", true); f.save() },
            { f -> (f.policy["receipt"]["artifacts"][0]["annotation_patch"] as ObjectNode).put("constant_pool_index", 1); f.save() },
            { f -> val path = f.root.resolve("mods/citadel.pw.toml"); Files.writeString(path, Files.readString(path).replace(f.citadel["input_sha1"].asText(), "f".repeat(40))) },
        )
        for (mutate in mutations) { val f = Fixture(); mutate(f); assertThrows(Exception::class.java) { f.prepare() } }
    }

    @Test fun bothCandidatesRejectOriginalDuplicateMissingAndWrongBytesOrCitadelAbi() {
        for (clientSide in listOf(true, false)) for (kind in 0..5) {
            val f = Fixture(); val prefix = if (clientSide) "custom/" else "server/"
            val mutate: (LinkedHashMap<String, ByteArray>) -> Unit = { e -> when (kind) {
                0 -> e.remove(prefix + "mods/" + f.artifact["output"].asText())
                1 -> e["duplicate/" + f.artifact["output"].asText()] = f.corrected
                2 -> e[prefix + "mods/" + f.artifact["input"].asText()] = f.raw
                3 -> e[prefix + "mods/alltheleaks.pw.toml"] = byteArrayOf(1)
                4 -> e[prefix + "mods/" + f.artifact["output"].asText()] = byteArrayOf(1)
                5 -> e[prefix + policyPath] = "{}".toByteArray()
            }; Unit }
            if (clientSide) f.candidates(changeClient = mutate) else f.candidates(changeServer = mutate)
            assertThrows(Exception::class.java) { f.validate() }
        }
        run { val f = Fixture(); f.candidates(changeServer = { it["server/mods/" + f.citadel["input"].asText()] = byteArrayOf(1) }); assertThrows(Exception::class.java) { f.validate() } }
        run { val f = Fixture(); f.candidates(changeClient = { it["manifest.json"] = """{"overrides":"custom","files":[]}""".toByteArray() }); assertThrows(Exception::class.java) { f.validate() } }
        run { val f = Fixture(); f.candidates(changeClient = { val m = mapper.readTree(it.getValue("manifest.json")) as ObjectNode
            (m["files"] as ArrayNode).addObject().put("projectID", f.artifact["project_id"].asLong()).put("fileID", f.artifact["file_id"].asLong()); it["manifest.json"] = mapper.writeValueAsBytes(m) }); assertThrows(Exception::class.java) { f.validate() } }
    }

    @Test fun duplicateJsonSymlinkAndUnsafeNamesFailClosed() {
        run { val f = Fixture(); val p = f.root.resolve(policyPath); Files.writeString(p, Files.readString(p).replaceFirst("{", "{\"schema\":\"duplicate\",")); assertThrows(Exception::class.java) { f.prepare() } }
        run { val f = Fixture(); val p = f.source.resolve(support + "receipt.json"); val actual = f.base.resolve("real-receipt"); Files.move(p, actual); Files.createSymbolicLink(p, actual); assertThrows(Exception::class.java) { f.prepare() } }
        run { val f = Fixture(); Files.createSymbolicLink(f.evidence, Files.createDirectory(f.base.resolve("outside"))); assertThrows(Exception::class.java) { f.prepare() } }
        for (unsafe in listOf("../escape", "/absolute", "bad\\name")) { val f = Fixture(); f.candidates(changeClient = { it[unsafe] = byteArrayOf(1) }); assertThrows(Exception::class.java) { f.validate() } }
    }
}
