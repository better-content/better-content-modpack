package com.bettercontent.tests.release

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Independent two-archive correction contract; never changes the resource-only five-archive recipe. */
object UpstreamInterfaceRuntimeOverrides {
    private const val POLICY = "config/better-content-interface-runtime-overrides.json"
    private const val SUPPORT = "build/upstream-interface-overrides/"
    private const val MAX_JSON = 2 * 1024 * 1024
    private const val MAX_ARCHIVE = 256 * 1024 * 1024
    private const val MAX_ENTRY = 128 * 1024 * 1024
    private const val TICKER = "org/valkyrienskies/mod/mixin/feature/sculk/MixinVibrationSystemTicker.class"
    private val mapper = jacksonObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
    private val shaPattern = Regex("[0-9a-f]{64}")
    private val methods = linkedMapOf(
        "destWorldPos" to "(Lnet/minecraft/world/level/gameevent/vibrations/VibrationInfo;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/phys/Vec3;",
        "destSourcePos" to "(Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem\$User;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/level/gameevent/PositionSource;",
        "destReloadSourcePos" to "(Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem\$User;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/level/gameevent/PositionSource;",
    )
    private fun hash(data: ByteArray, algorithm: String = "SHA-256") = MessageDigest.getInstance(algorithm)
        .digest(data).joinToString("") { "%02x".format(it) }
    private fun safe(path: Path): Path {
        val p = path.toAbsolutePath()
        require(p == p.normalize()) { "non-normal path: $p" }
        var current: Path? = p
        while (current != null) { require(!Files.isSymbolicLink(current)) { "symlink path: $current" }; current = current.parent }
        return p
    }
    private fun read(path: Path, limit: Int = MAX_ARCHIVE): ByteArray {
        val p = safe(path)
        val before = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        require(before.isRegularFile && before.size() <= limit) { "file size/type: $p" }
        val data = Files.newInputStream(p, NOFOLLOW_LINKS).use { it.readNBytes(limit + 1) }
        val after = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        require(data.size <= limit && before.size() == after.size() && before.fileKey() == after.fileKey() &&
            before.lastModifiedTime() == after.lastModifiedTime()) { "input drift: $p" }
        return data
    }
    private fun json(data: ByteArray): JsonNode {
        require(data.size <= MAX_JSON)
        return mapper.factory.createParser(data).use { parser ->
            val result = mapper.readTree<JsonNode>(parser)
            require(result != null && parser.nextToken() == null) { "invalid/trailing JSON" }; result
        }
    }
    private fun fields(node: JsonNode, vararg keys: String) {
        require(node.isObject && node.fieldNames().asSequence().toSet() == keys.toSet()) { "unknown/missing fields" }
    }
    private fun text(node: JsonNode, key: String): String = requireNotNull(node[key]).also {
        require(it.isTextual && it.asText().isNotEmpty()) { "missing text: $key" }
    }.asText()
    private fun sha(node: JsonNode, key: String): String = text(node, key).also { require(shaPattern.matches(it)) }
    private fun name(value: String) {
        require(value.isNotEmpty() && '\\' !in value && ':' !in value && value.none { it.code < 32 } &&
            value.removeSuffix("/").split('/').all { it.isNotEmpty() && it != "." && it != ".." }) { "unsafe archive name: $value" }
    }
    private fun base(value: String, suffix: String): String = value.also { name(it); require('/' !in it && it.endsWith(suffix)) }
    private fun archive(data: ByteArray): LinkedHashMap<String, ByteArray> {
        require(data.size <= MAX_ARCHIVE)
        val result = linkedMapOf<String, ByteArray>(); var expanded = 0L
        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                name(entry.name)
                require(result.size < 100000 && entry.name !in result && entry.size <= MAX_ENTRY)
                val body = zip.readNBytes(MAX_ENTRY + 1); expanded += body.size
                require(body.size <= MAX_ENTRY && expanded <= 1024L * 1024 * 1024)
                result[entry.name] = body; zip.closeEntry()
            }
        }
        require(result.isNotEmpty()) { "invalid/empty archive" }; return result
    }

    /** Parse structure, never scan byte patterns. Only these three U2 words may change. */
    internal fun expectedInterfaceClass(original: ByteArray): ByteArray {
        class Cursor {
            var offset = 0
            fun take(size: Int): ByteArray {
                require(size >= 0 && size <= original.size - offset) { "truncated class" }
                return original.copyOfRange(offset, offset + size).also { offset += size }
            }
            fun u1() = take(1)[0].toInt() and 255
            fun u2() = (u1() shl 8) or u1()
            fun u4(): Long = (u2().toLong() shl 16) or u2().toLong()
        }
        val c = Cursor()
        require(c.u4() == 0xcafebabeL && c.u2() == 0 && c.u2() == 61) { "expected Java17 class" }
        val count = c.u2(); require(count > 1)
        val utf = mutableMapOf<Int, String>(); var index = 1
        while (index < count) {
            when (c.u1()) {
                1 -> {
                    val length = c.u2(); val body = c.take(length)
                    val encoded = byteArrayOf((length ushr 8).toByte(), length.toByte()) + body
                    utf[index] = DataInputStream(ByteArrayInputStream(encoded)).readUTF()
                }
                3, 4 -> c.take(4)
                5, 6 -> { c.take(8); index++; require(index < count) }
                7, 8, 16, 19, 20 -> c.take(2)
                9, 10, 11, 12, 17, 18 -> c.take(4)
                15 -> c.take(3)
                else -> error("unsupported class constant")
            }
            index++
        }
        require(c.u2() and 0x0200 != 0) { "expected interface" }
        c.u2(); c.u2()
        repeat(c.u2()) { c.u2() }
        fun attributes() {
            repeat(c.u2()) {
                require(c.u2() in utf) { "invalid attribute name" }
                val size = c.u4(); require(size <= MAX_ENTRY); c.take(size.toInt())
            }
        }
        // This pinned interface has no fields and exactly three named methods.
        require(c.u2() == 0) { "unexpected interface fields" }
        require(c.u2() == 3) { "unexpected interface method population" }
        val offsets = mutableListOf<Int>(); val seen = mutableSetOf<String>()
        repeat(3) {
            val offset = c.offset
            require(c.u2() == 0x000a) { "must preserve STATIC; expected PRIVATE|STATIC" }
            val method = requireNotNull(utf[c.u2()]); val descriptor = requireNotNull(utf[c.u2()])
            require(methods[method] == descriptor && seen.add(method)) { "unexpected/duplicate method or descriptor" }
            offsets.add(offset); attributes()
        }
        attributes(); require(c.offset == original.size && seen == methods.keys) { "class trailing bytes/method drift" }
        return original.copyOf().also { fixed -> offsets.forEach { offset -> fixed[offset] = 0; fixed[offset + 1] = 9 } }
    }

    private fun policy(root: Path): JsonNode {
        val p = json(read(root.resolve(POLICY), MAX_JSON))
        fields(p, "schema", "producer", "provider_artifact", "revision", "spec", "builder", "legacy_builder", "artifacts", "receipt", "publication", "runtime_acceptance")
        require(text(p, "schema") == "bc.interface_runtime_archive_overrides.v1" && text(p, "producer") == "better-compat-fixes" &&
            text(p, "provider_artifact") == "better-compat-fixes-0.1.9.jar" && text(p, "revision") == "bc-interface.1" &&
            text(p, "publication") == "LOCAL_ONLY_NOT_AUTHORIZED" && text(p, "runtime_acceptance") == "NOT_EVALUATED")
        for ((key, path) in mapOf("spec" to "src/upstreamRuntime/interface-overrides-v1.json", "builder" to "scripts/build_upstream_interface_overrides.py", "legacy_builder" to "scripts/build_upstream_runtime_overrides.py")) {
            fields(p[key], "path", "sha256"); require(text(p[key], "path") == path); sha(p[key], "sha256")
        }
        val artifacts = p["artifacts"]; require(artifacts.isArray && artifacts.size() == 2)
        val names = mutableSetOf<String>(); val manifests = mutableSetOf<String>(); val tuples = mutableSetOf<Pair<Long, Long>>()
        for (a in artifacts) {
            fields(a, "input", "output", "manifest", "project_id", "file_id", "input_sha1", "input_sha256", "output_sha256", "resource", "class_patch")
            require(names.add(base(text(a, "input"), ".jar")) && names.add(base(text(a, "output"), "-bc-interface.1.jar")))
            require(manifests.add(base(text(a, "manifest"), ".pw.toml")))
            require(a["project_id"].isIntegralNumber && a["file_id"].isIntegralNumber && a["project_id"].asLong() > 0 && a["file_id"].asLong() > 0 &&
                tuples.add(a["project_id"].asLong() to a["file_id"].asLong()))
            require(Regex("[0-9a-f]{40}").matches(text(a, "input_sha1"))); sha(a, "input_sha256"); sha(a, "output_sha256")
            val resource = a["resource"]
            fields(resource, "path", "before_sha256", "after_sha256", "move_to_client")
            base(text(resource, "path"), ".json"); sha(resource, "before_sha256"); sha(resource, "after_sha256")
            require(resource["move_to_client"].isArray && resource["move_to_client"].size() == 1 && resource["move_to_client"][0].isTextual)
            if (!a["class_patch"].isNull) {
                fields(a["class_patch"], "path", "before_sha256", "after_sha256")
                require(text(a["class_patch"], "path") == TICKER); sha(a["class_patch"], "before_sha256"); sha(a["class_patch"], "after_sha256")
            }
        }
        require(artifacts.count { !it["class_patch"].isNull } == 1)
        receipt(mapper.writeValueAsBytes(p["receipt"]), p)
        return p
    }
    private fun pins(root: Path, p: JsonNode) {
        val expected = p["artifacts"].associateBy { text(it, "input") }; val seen = mutableSetOf<String>()
        Files.newDirectoryStream(safe(root.resolve("mods")), "*.pw.toml").use { paths ->
            for (path in paths) {
                var section = ""; val sections = mutableSetOf<String>(); val values = linkedMapOf<String, String>()
                for (raw in read(path, MAX_JSON).toString(Charsets.UTF_8).lines()) {
                    val line = raw.trim(); if (line.isEmpty() || line.startsWith('#')) continue
                    if (line.startsWith('[')) {
                        require(Regex("\\[[A-Za-z0-9_.-]+]").matches(line) && sections.add(line)); section = line.substring(1, line.length - 1) + "."
                    } else {
                        val pair = Regex("([A-Za-z0-9_-]+)\\s*=\\s*(.+)").matchEntire(line) ?: error("invalid pin scalar")
                        require(values.put(section + pair.groupValues[1], pair.groupValues[2].trim()) == null) { "duplicate pin key" }
                    }
                }
                val input = values["filename"]?.removeSurrounding("\"") ?: continue
                val selected = p["artifacts"].firstOrNull { values["update.curseforge.project-id"] == it["project_id"].asText() && values["update.curseforge.file-id"] == it["file_id"].asText() }
                require(selected == null || text(selected, "input") == input) { "mixed selected pin identity" }
                val a = expected[input] ?: continue
                require(seen.add(input) && path.fileName.toString() == text(a, "manifest"))
                fun eq(key: String, value: String) = require(values[key] == value) { "original pin drift: $path $key" }
                eq("filename", mapper.writeValueAsString(input)); eq("side", "\"both\"")
                eq("download.hash-format", "\"sha1\""); eq("download.hash", mapper.writeValueAsString(text(a, "input_sha1")))
                eq("download.mode", "\"metadata:curseforge\"")
                eq("update.curseforge.project-id", a["project_id"].asText()); eq("update.curseforge.file-id", a["file_id"].asText())
            }
        }
        require(seen == expected.keys) { "missing original pins" }
    }
    private fun original(a: JsonNode): ByteArray {
        // Same canonical download-cache namespace as existing authored native-input contracts.
        val cache = Path.of(System.getProperty("bc.native.archive.cache",
            Path.of(System.getProperty("user.home"), ".cache/bc/packwiz-downloads/mods").toString()))
        val data = read(cache.resolve(text(a, "input")))
        require(hash(data) == sha(a, "input_sha256") && hash(data, "SHA-1") == text(a, "input_sha1")) { "original cache digest mismatch; canonical resolver required" }
        return data
    }
    private class Budget(var entries: Int = 0, var expanded: Long = 0)
    private fun unchangedArchive(original: ByteArray, corrected: ByteArray, depth: Int = 0, budget: Budget = Budget()) {
        require(depth <= 4)
        val before = archive(original); val after = archive(corrected)
        require(before.keys == after.keys) { "archive entry population changed" }
        for ((name, body) in before) {
            budget.entries++; budget.expanded += body.size
            require(budget.entries <= 100000 && budget.expanded <= 1024L * 1024 * 1024)
            val fixed = after.getValue(name)
            if (name.endsWith(".jar")) unchangedArchive(body, fixed, depth + 1, budget)
            else require(body.contentEquals(fixed)) { "unreviewed archive byte change: $name" }
        }
    }
    private fun classInventory(data: ByteArray, prefix: String = "", depth: Int = 0, budget: Budget = Budget()): LinkedHashMap<String, String> {
        require(depth <= 4)
        val inventory = linkedMapOf<String, String>()
        for ((name, body) in archive(data)) {
            budget.entries++; budget.expanded += body.size
            require(budget.entries <= 100000 && budget.expanded <= 1024L * 1024 * 1024)
            when {
                name.endsWith(".class") -> inventory[prefix + name] = hash(body)
                name.endsWith(".jar") -> inventory.putAll(classInventory(body, "$prefix$name!", depth + 1, budget))
            }
        }
        return inventory
    }
    private fun inventoryHash(inventory: Map<String, String>): String {
        fun quoted(value: String): String = buildString {
            append('"')
            for (c in value) when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c.code < 32 || c.code > 126 -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
            append('"')
        }
        val encoded = if (inventory.isEmpty()) "{}\n" else inventory.entries.joinToString(",\n", "{\n", "\n}\n") {
            "  ${quoted(it.key)}: ${quoted(it.value)}"
        }
        return hash(encoded.toByteArray(Charsets.UTF_8))
    }
    private fun corrected(original: ByteArray, fixed: ByteArray, a: JsonNode, row: JsonNode) {
        require(hash(fixed) == sha(a, "output_sha256") && hash(fixed, "SHA-1") == text(row, "output_sha1")) { "corrected output digest mismatch" }
        val originalClasses = classInventory(original); val fixedClasses = classInventory(fixed)
        require(originalClasses.size == row["class_count"].asInt() && originalClasses.keys == fixedClasses.keys &&
            inventoryHash(originalClasses) == sha(row, "before_class_inventory_sha256") &&
            inventoryHash(fixedClasses) == sha(row, "after_class_inventory_sha256")) { "recursive class inventory mismatch" }
        val before = archive(original); val after = archive(fixed)
        require(before.keys == after.keys) { "archive entry population changed" }
        val resource = a["resource"]; val resourcePath = text(resource, "path")
        val oldResource = requireNotNull(before[resourcePath]); val newResource = requireNotNull(after[resourcePath])
        require(hash(oldResource) == sha(resource, "before_sha256") && hash(newResource) == sha(resource, "after_sha256"))
        val expected = json(oldResource).deepCopy<ObjectNode>()
        val common = expected["mixins"]; val client = expected["client"]
        require(common.isArray && client.isArray)
        val moved = resource["move_to_client"][0].asText()
        require(common.count { it.isTextual && it.asText() == moved } == 1 && client.none { it.asText() == moved })
        val newCommon = mapper.createArrayNode(); common.filter { it.asText() != moved }.forEach { newCommon.add(it) }
        expected.set<JsonNode>("mixins", newCommon)
        (client as com.fasterxml.jackson.databind.node.ArrayNode).add(moved)
        require(json(newResource) == expected) { "JSON change is not the exact common-to-client move" }
        val patch = a["class_patch"]
        if (!patch.isNull) {
            val path = text(patch, "path"); val originalClass = requireNotNull(before[path]); val fixedClass = requireNotNull(after[path])
            require(hash(originalClass) == sha(patch, "before_sha256") && hash(fixedClass) == sha(patch, "after_sha256"))
            val expectedClass = expectedInterfaceClass(originalClass)
            require(fixedClass.contentEquals(expectedClass)) { "class changed beyond exactly three STATIC-preserving U2 access words" }
            val derivedOffsets = originalClass.indices.filter { originalClass[it] != expectedClass[it] }.map { it - 1 }
            require(derivedOffsets.size == 3 && row["class_access_patches"][0]["edits"].map { it["offset"].asInt() } == derivedOffsets) {
                "receipt access offsets differ from independently parsed methods"
            }
        }
        val budget = Budget()
        for ((name, body) in before) {
            if (name == resourcePath || (!patch.isNull && name == text(patch, "path"))) continue
            val bodyAfter = after.getValue(name)
            if (name.endsWith(".jar")) unchangedArchive(body, bodyAfter, 1, budget)
            else require(body.contentEquals(bodyAfter)) { "unreviewed class/resource/notice byte change: $name" }
        }
    }
    private fun receipt(data: ByteArray, p: JsonNode) {
        val r = json(data)
        fields(r, "schema", "revision", "spec_sha256", "builder_sha256", "legacy_builder_sha256", "python", "zlib", "artifacts", "runtime_acceptance", "publication")
        require(r == p["receipt"]) { "receipt differs from exact reviewed template" }
        require(text(r, "schema") == "bc.upstream_interface_runtime_receipt.v1" && r["revision"] == p["revision"] &&
            text(r, "spec_sha256") == sha(p["spec"], "sha256") && text(r, "builder_sha256") == sha(p["builder"], "sha256") &&
            text(r, "legacy_builder_sha256") == sha(p["legacy_builder"], "sha256") && text(r, "runtime_acceptance") == "NOT_EVALUATED" && text(r, "publication") == "NOT_AUTHORIZED_BY_THIS_RECEIPT")
        require(r["artifacts"].isArray && r["artifacts"].size() == 2)
        for ((index, a) in p["artifacts"].withIndex()) {
            val row = r["artifacts"][index]
            fields(row, "input", "output", "input_sha256", "output_sha256", "output_sha1", "resources", "class_access_patches",
                "class_bytes_invariant", "class_count", "before_class_inventory_sha256", "after_class_inventory_sha256", "license", "redistribution")
            require(row["class_count"].isIntegralNumber && row["class_count"].asInt() > 0 && row["class_bytes_invariant"].isBoolean)
            require(row["class_bytes_invariant"].asBoolean() == a["class_patch"].isNull)
            require(row["resources"].isArray && row["resources"].size() == 1)
            val resource = row["resources"][0]
            fields(resource, "path", "before_sha256", "after_sha256")
            for (key in listOf("path", "before_sha256", "after_sha256")) require(resource[key] == a["resource"][key])
            require(row["class_access_patches"].isArray && row["class_access_patches"].size() == (if (a["class_patch"].isNull) 0 else 1))
            if (!a["class_patch"].isNull) {
                val access = row["class_access_patches"][0]
                fields(access, "path", "before_sha256", "after_sha256", "edits", "all_other_class_bytes_invariant")
                for (key in listOf("path", "before_sha256", "after_sha256")) require(access[key] == a["class_patch"][key])
                require(access["all_other_class_bytes_invariant"].isBoolean && access["all_other_class_bytes_invariant"].asBoolean())
                require(access["edits"].isArray && access["edits"].size() == 3)
                val seen = mutableSetOf<String>()
                for (edit in access["edits"]) {
                    fields(edit, "method", "descriptor", "offset", "before", "after")
                    require(methods[text(edit, "method")] == text(edit, "descriptor") && seen.add(text(edit, "method")))
                    require(edit["offset"].isIntegralNumber && edit["offset"].asInt() >= 0 && edit["before"].isIntegralNumber &&
                        edit["before"].asInt() == 10 && edit["after"].isIntegralNumber && edit["after"].asInt() == 9)
                }
            }
            for (key in listOf("input", "output", "input_sha256", "output_sha256")) require(row[key] == a[key])
        }
    }

    fun prepare(root: Path, source: Path, evidence: Path, providerJar: Path,
                supportHashes: Map<String, String>): PreparedRuntimeOverrides {
        safe(root); safe(source); safe(evidence)
        val authored = read(root.resolve(POLICY), MAX_JSON); val p = policy(root); pins(root, p)
        for (field in listOf("spec", "builder", "legacy_builder")) require(hash(read(source.resolve(text(p[field], "path")), MAX_JSON)) == sha(p[field], "sha256")) { "source recipe drift" }
        val provider = read(providerJar) // Main source gates/stamp qualify this provider; no custom replacement classes here.
        val names = listOf("receipt.json") + p["artifacts"].map { text(it, "output") }
        val data = names.associateWith { name ->
            val declared = requireNotNull(supportHashes[SUPPORT + name]) { "undeclared qualified support: $name" }
            require(shaPattern.matches(declared))
            read(source.resolve(SUPPORT + name), if (name == "receipt.json") MAX_JSON else MAX_ARCHIVE).also {
                require(hash(it) == declared) { "qualified support drift" }
            }
        }
        receipt(data.getValue("receipt.json"), p)
        for ((index, a) in p["artifacts"].withIndex()) corrected(original(a), data.getValue(text(a, "output")), a, p["receipt"]["artifacts"][index])
        require(read(root.resolve(POLICY), MAX_JSON).contentEquals(authored)); pins(root, p)
        require(read(providerJar).contentEquals(provider))
        for (field in listOf("spec", "builder", "legacy_builder")) require(hash(read(source.resolve(text(p[field], "path")), MAX_JSON)) == sha(p[field], "sha256"))
        val directory = safe(evidence.resolve("interface-overrides"))
        require(!Files.exists(directory, NOFOLLOW_LINKS)) { "immutable snapshot already exists" }
        Files.createDirectories(safe(directory.parent)); Files.createDirectory(directory)
        for ((name, body) in data) {
            val output = directory.resolve(name); Files.write(output, body, CREATE_NEW)
            require(read(output).contentEquals(body) && output.toFile().setWritable(false, false))
        }
        val selection = mapOf("schema" to "bc.package_runtime_overrides.v1", "artifacts" to p["artifacts"].map { a ->
            mapOf("input" to text(a, "input"), "output" to text(a, "output"), "manifest" to text(a, "manifest"),
                "project_id" to a["project_id"].asLong(), "file_id" to a["file_id"].asLong())
        })
        val selected = mapper.writeValueAsBytes(selection); val selectionPath = directory.resolve("selection.json")
        Files.write(selectionPath, selected, CREATE_NEW)
        require(read(selectionPath, MAX_JSON).contentEquals(selected) && selectionPath.toFile().setWritable(false, false) && directory.toFile().setWritable(false, false))
        val pointer = safe(root.resolve(".worklane/interface-overrides/current.path")); Files.createDirectories(safe(pointer.parent))
        require(!Files.exists(pointer, NOFOLLOW_LINKS) || Files.isRegularFile(pointer, NOFOLLOW_LINKS))
        val temporary = pointer.resolveSibling(".current-${UUID.randomUUID()}.path")
        Files.writeString(temporary, "$directory\n", CREATE_NEW); safe(pointer); Files.move(temporary, pointer, ATOMIC_MOVE, REPLACE_EXISTING)
        return PreparedRuntimeOverrides(directory, mapOf("schema" to "bc.prepared_interface_runtime_overrides.v1", "directory" to directory.toString(),
            "policy_sha256" to hash(authored), "qualified_provider_sha256" to hash(provider),
            "receipt_sha256" to hash(data.getValue("receipt.json")), "support_hashes" to data.mapValues { hash(it.value) },
            "publication" to "LOCAL_ONLY_NOT_AUTHORIZED", "runtime_acceptance" to "NOT_EVALUATED"))
    }

    fun validateCandidates(root: Path, client: Path, server: Path) {
        val authored = read(root.resolve(POLICY), MAX_JSON); val p = policy(root); pins(root, p)
        val originals = p["artifacts"].associate { text(it, "input") to original(it) }
        for ((path, clientSide) in listOf(client to true, server to false)) {
            safe(path)
            ZipFile(path.toFile()).use { zip ->
                val entries = zip.entries().asSequence().toList()
                require(entries.size <= 100000 && entries.map { it.name }.distinct().size == entries.size)
                entries.forEach { name(it.name) }
                fun entry(name: String, limit: Int = MAX_ARCHIVE): ByteArray {
                    val item = entries.singleOrNull { it.name == name } ?: error("missing candidate entry: $name")
                    require(!item.isDirectory && item.size in 0..limit.toLong())
                    return zip.getInputStream(item).use { it.readNBytes(limit + 1) }.also { require(it.size <= limit) }
                }
                val prefix: String
                if (clientSide) {
                    val manifest = json(entry("manifest.json", MAX_JSON)); prefix = base(text(manifest, "overrides"), "") + "/"
                    require(manifest["files"].isArray)
                    for (a in p["artifacts"]) require(manifest["files"].none { it["projectID"]?.asLong() == a["project_id"].asLong() && it["fileID"]?.asLong() == a["file_id"].asLong() }) { "original CurseForge tuple remains" }
                } else {
                    val roots = entries.map { it.name.substringBefore('/') }.toSet()
                    require(roots.size == 1 && entries.all { '/' in it.name }); prefix = roots.single() + "/"
                }
                require(entry(prefix + POLICY, MAX_JSON).contentEquals(authored)) { "candidate policy drift" }
                for ((index, a) in p["artifacts"].withIndex()) {
                    val output = text(a, "output")
                    require(entries.count { it.name.substringAfterLast('/') == output } == 1) { "duplicate/missing corrected archive" }
                    require(entries.none { it.name.substringAfterLast('/') in setOf(text(a, "input"), text(a, "manifest")) }) { "original archive/manifest remains" }
                    corrected(originals.getValue(text(a, "input")), entry(prefix + "mods/" + output), a, p["receipt"]["artifacts"][index])
                }
            }
        }
        require(read(root.resolve(POLICY), MAX_JSON).contentEquals(authored))
    }
}
