package com.bettercontent.tests.release

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.ByteArrayInputStream
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

/** Exact ATL annotation applicability, independently verified before startup; no early-loader mixin. */
object UpstreamStartupRuntimeOverrides {
    private const val POLICY = "config/better-content-startup-runtime-overrides.json"
    private const val SUPPORT = "build/upstream-startup-overrides/"
    private const val ORIGINAL = "alltheleaks-1.1.1+1.20.1-forge.jar"
    private const val OUTPUT = "alltheleaks-1.1.1+1.20.1-forge-bc-startup.1.jar"
    private const val TARGET = "dev/uncandango/alltheleaks/leaks/common/mods/citadel/UntrackedIssue001.class"
    private const val ISSUE = "Ldev/uncandango/alltheleaks/annotation/Issue;"
    private const val BEFORE = "[2.5.4,)"
    private const val AFTER = "[2.5.4,2.6.3),(2.6.3,)"
    private const val MAX_JSON = 2 * 1024 * 1024
    private const val MAX_ARCHIVE = 256 * 1024 * 1024
    private const val MAX_ENTRY = 128 * 1024 * 1024
    private val mapper = jacksonObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
    private fun hash(data: ByteArray, algorithm: String = "SHA-256") = MessageDigest.getInstance(algorithm).digest(data)
        .joinToString("") { "%02x".format(it) }
    private fun safe(path: Path): Path {
        val absolute = path.toAbsolutePath(); require(absolute == absolute.normalize())
        var part: Path? = absolute
        while (part != null) { require(!Files.isSymbolicLink(part)) { "symlink path: $part" }; part = part.parent }
        return absolute
    }
    private fun read(path: Path, limit: Int = MAX_ARCHIVE): ByteArray {
        val p = safe(path)
        val before = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        require(before.isRegularFile && before.size() <= limit)
        val data = Files.newInputStream(p, NOFOLLOW_LINKS).use { it.readNBytes(limit + 1) }
        val after = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        require(data.size <= limit && before.size() == after.size() && before.fileKey() == after.fileKey() && before.lastModifiedTime() == after.lastModifiedTime())
        return data
    }
    private fun json(data: ByteArray): JsonNode {
        require(data.size <= MAX_JSON)
        return mapper.factory.createParser(data).use { parser ->
            val result = mapper.readTree<JsonNode>(parser); require(result != null && parser.nextToken() == null); result
        }
    }
    private fun fields(node: JsonNode, vararg keys: String) = require(node.isObject && node.fieldNames().asSequence().toSet() == keys.toSet()) { "unknown/missing fields" }
    private fun text(node: JsonNode, key: String): String = requireNotNull(node[key]).also { require(it.isTextual && it.asText().isNotEmpty()) }.asText()
    private fun sha(node: JsonNode, key: String): String = text(node, key).also { require(Regex("[0-9a-f]{64}").matches(it)) }
    private fun name(value: String) = require(value.isNotEmpty() && '\\' !in value && ':' !in value && value.none { it.code < 32 } &&
        value.removeSuffix("/").split('/').all { it.isNotEmpty() && it != "." && it != ".." }) { "unsafe archive name" }
    private fun base(value: String, suffix: String): String = value.also { name(it); require('/' !in it && it.endsWith(suffix)) }
    private fun archive(data: ByteArray): LinkedHashMap<String, ByteArray> {
        require(data.size <= MAX_ARCHIVE); val result = linkedMapOf<String, ByteArray>(); var expanded = 0L
        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break; name(entry.name)
                require(result.size < 100000 && entry.name !in result && entry.size <= MAX_ENTRY)
                val body = zip.readNBytes(MAX_ENTRY + 1); expanded += body.size
                require(body.size <= MAX_ENTRY && expanded <= 1024L * 1024 * 1024)
                result[entry.name] = body; zip.closeEntry()
            }
        }; require(result.isNotEmpty()); return result
    }

    private data class Value(val tag: Char, val index: Int = -1, val string: String = "")
    private class ClassFile(val bytes: ByteArray) {
        var position = 0
        val utf = mutableMapOf<Int, ByteArray>()
        val locations = mutableMapOf<Int, Pair<Int, Int>>()
        val references = mutableListOf<Int>()
        val strings = mutableListOf<Int>()
        val fields = mutableListOf<String>()
        val annotations = mutableListOf<Pair<String, Map<String, Value>>>()
        fun take(size: Int): ByteArray {
            require(size >= 0 && size <= bytes.size - position) { "truncated class" }
            return bytes.copyOfRange(position, position + size).also { position += size }
        }
        fun u1() = take(1)[0].toInt() and 255
        fun u2() = (u1() shl 8) or u1()
        fun u4(): Long = (u2().toLong() shl 16) or u2().toLong()
        fun string(index: Int): String = requireNotNull(utf[index]).also { require(it.all { b -> b.toInt() in 0..127 }) }.toString(Charsets.US_ASCII)
        init {
            require(bytes.size <= MAX_ENTRY && u4() == 0xcafebabeL); u2(); require(u2() in 45..65)
            val count = u2(); var index = 1
            while (index < count) {
                when (u1()) {
                    1 -> { val offset = position; val size = u2(); utf[index] = take(size); locations[index] = offset to size }
                    3, 4 -> take(4)
                    5, 6 -> { take(8); index++; require(index < count) }
                    7, 8, 16, 19, 20 -> references.add(u2())
                    9, 10, 11, 12, 17, 18 -> { references.add(u2()); references.add(u2()) }
                    15 -> { u1(); references.add(u2()) }
                    else -> error("invalid class constant")
                }; index++
            }
            u2(); u2(); u2(); repeat(u2()) { u2() }
            repeat(u2()) {
                u2(); val fieldName = u2(); val descriptor = u2(); references.add(fieldName); references.add(descriptor)
                fields.add(string(fieldName)); attributes(false)
            }
            repeat(u2()) { u2(); references.add(u2()); references.add(u2()); attributes(false) }
            attributes(true); require(position == bytes.size) { "trailing class bytes" }
        }
        private fun attributes(classLevel: Boolean) {
            repeat(u2()) {
                val name = string(u2()); val length = u4(); require(length <= bytes.size - position)
                val end = position + length.toInt()
                if (classLevel && name == "RuntimeVisibleAnnotations") {
                    repeat(u2()) { annotations.add(annotation()) }; require(position == end)
                } else take(length.toInt())
            }
        }
        private fun annotation(): Pair<String, Map<String, Value>> {
            val kind = string(u2()); val values = linkedMapOf<String, Value>()
            repeat(u2()) { val key = string(u2()); require(key !in values); values[key] = element() }
            return kind to values
        }
        private fun element(): Value {
            val tag = u1().toChar()
            return when (tag) {
                's' -> { val index = u2(); strings.add(index); Value(tag, index, string(index)) }
                'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z', 'c' -> Value(tag, u2())
                'e' -> { u2(); u2(); Value(tag) }
                '@' -> { annotation(); Value(tag) }
                '[' -> { repeat(u2()) { element() }; Value(tag) }
                else -> error("invalid annotation tag")
            }
        }
    }
    internal data class AnnotationCorrection(val bytes: ByteArray, val constantPoolIndex: Int, val lengthOffset: Int)
    internal fun expectedAnnotationClass(original: ByteArray): AnnotationCorrection {
        val parsed = ClassFile(original)
        val issue = parsed.annotations.filter { it.first == ISSUE }.single().second
        require(issue["modId"]?.tag == 's' && issue["modId"]?.string == "citadel")
        val range = requireNotNull(issue["versionRange"])
        require(range.tag == 's' && range.string == BEFORE && parsed.strings.count { it == range.index } == 1 && range.index !in parsed.references)
        val (offset, size) = parsed.locations.getValue(range.index); val replacement = AFTER.toByteArray(Charsets.US_ASCII)
        val fixed = original.copyOfRange(0, offset) + byteArrayOf((replacement.size ushr 8).toByte(), replacement.size.toByte()) +
            replacement + original.copyOfRange(offset + 2 + size, original.size)
        require(ClassFile(fixed).annotations.filter { it.first == ISSUE }.single().second["versionRange"]?.string == AFTER)
        return AnnotationCorrection(fixed, range.index, offset)
    }

    private fun policy(root: Path): JsonNode {
        val p = json(read(root.resolve(POLICY), MAX_JSON))
        fields(p, "schema", "producer", "provider_artifact", "revision", "spec", "builder", "legacy_builder", "citadel", "patch", "artifacts", "receipt", "publication", "runtime_acceptance")
        require(text(p, "schema") == "bc.startup_archive_overrides.v1" && text(p, "producer") == "better-compat-fixes" && text(p, "provider_artifact") == "better-compat-fixes-0.1.9.jar" &&
            text(p, "revision") == "bc-startup.1" && text(p, "publication") == "LOCAL_ONLY_NOT_AUTHORIZED" && text(p, "runtime_acceptance") == "NOT_EVALUATED")
        for ((key, path) in mapOf("spec" to "src/upstreamRuntime/startup-overrides-v1.json", "builder" to "scripts/build_upstream_startup_overrides.py", "legacy_builder" to "scripts/build_upstream_runtime_overrides.py")) {
            fields(p[key], "path", "sha256"); require(text(p[key], "path") == path); sha(p[key], "sha256")
        }
        require(p["artifacts"].isArray && p["artifacts"].size() == 1)
        val a = p["artifacts"][0]
        fields(a, "input", "output", "manifest", "project_id", "file_id", "input_sha1", "input_sha256", "output_sha256")
        require(text(a, "input") == ORIGINAL && text(a, "output") == OUTPUT && text(a, "manifest") == "alltheleaks.pw.toml" &&
            a["project_id"].asLong() == 1091339L && a["file_id"].asLong() == 7235989L); sha(a, "output_sha256")
        val c = p["citadel"]
        fields(c, "input", "manifest", "project_id", "file_id", "input_sha1", "input_sha256", "class", "class_sha256")
        require(text(c, "input") == "citadel-2.6.3-1.20.1.jar" && text(c, "manifest") == "citadel.pw.toml" &&
            c["project_id"].asLong() == 331936L && c["file_id"].asLong() == 7476570L && text(c, "class") == "com/github/alexthe666/citadel/server/world/CitadelServerData.class"); sha(c, "class_sha256")
        for (item in listOf(a, c)) {
            require(item["project_id"].isIntegralNumber && item["file_id"].isIntegralNumber && item["project_id"].asLong() > 0 && item["file_id"].asLong() > 0)
            require(Regex("[0-9a-f]{40}").matches(text(item, "input_sha1"))); sha(item, "input_sha256")
        }
        val patch = p["patch"]
        fields(patch, "path", "before_sha256", "annotation", "mod_id", "element", "before", "after")
        require(text(patch, "path") == TARGET && text(patch, "annotation") == ISSUE && text(patch, "mod_id") == "citadel" &&
            text(patch, "element") == "versionRange" && text(patch, "before") == BEFORE && text(patch, "after") == AFTER); sha(patch, "before_sha256")
        receipt(mapper.writeValueAsBytes(p["receipt"]), p)
        return p
    }
    private fun pins(root: Path, p: JsonNode) {
        val expected = listOf(p["artifacts"][0], p["citadel"]).associateBy { text(it, "input") }; val seen = mutableSetOf<String>()
        Files.newDirectoryStream(safe(root.resolve("mods")), "*.pw.toml").use { paths ->
            for (path in paths) {
                val values = linkedMapOf<String, String>(); val sections = mutableSetOf<String>(); var section = ""
                for (raw in read(path, MAX_JSON).toString(Charsets.UTF_8).lines()) {
                    val line = raw.trim(); if (line.isEmpty() || line.startsWith('#')) continue
                    if (line.startsWith('[')) {
                        require(Regex("\\[[A-Za-z0-9_.-]+]").matches(line) && sections.add(line)); section = line.substring(1, line.length - 1) + "."
                    } else {
                        val pair = Regex("([A-Za-z0-9_-]+)\\s*=\\s*(.+)").matchEntire(line) ?: error("invalid pin")
                        require(values.put(section + pair.groupValues[1], pair.groupValues[2].trim()) == null)
                    }
                }
                val input = values["filename"]?.removeSurrounding("\"") ?: continue
                val selected = expected.values.firstOrNull { values["update.curseforge.project-id"] == it["project_id"].asText() && values["update.curseforge.file-id"] == it["file_id"].asText() }
                require(selected == null || text(selected, "input") == input)
                val item = expected[input] ?: continue
                require(seen.add(input) && path.fileName.toString() == text(item, "manifest"))
                fun eq(key: String, value: String) = require(values[key] == value) { "original pin drift: $path $key" }
                eq("filename", mapper.writeValueAsString(input)); eq("side", "\"both\""); eq("download.hash-format", "\"sha1\"")
                eq("download.hash", mapper.writeValueAsString(text(item, "input_sha1"))); eq("download.mode", "\"metadata:curseforge\"")
                eq("update.curseforge.project-id", item["project_id"].asText()); eq("update.curseforge.file-id", item["file_id"].asText())
            }
        }; require(seen == expected.keys)
    }
    private fun original(item: JsonNode): ByteArray {
        val cache = Path.of(System.getProperty("bc.native.archive.cache", Path.of(System.getProperty("user.home"), ".cache/bc/packwiz-downloads/mods").toString()))
        return read(cache.resolve(text(item, "input"))).also { require(hash(it) == sha(item, "input_sha256") && hash(it, "SHA-1") == text(item, "input_sha1")) }
    }
    private fun citadel(p: JsonNode): ByteArray {
        val data = original(p["citadel"]); val abi = requireNotNull(archive(data)[text(p["citadel"], "class")])
        require(hash(abi) == sha(p["citadel"], "class_sha256"))
        val fields = ClassFile(abi).fields
        require("dataMap" !in fields && p["receipt"]["citadel_abi"]["field_names"].map { it.asText() } == fields)
        return data
    }
    private class Budget(var count: Int = 0, var size: Long = 0)
    private fun inventory(data: ByteArray, prefix: String = "", depth: Int = 0, budget: Budget = Budget()): LinkedHashMap<String, String> {
        require(depth <= 4); val result = linkedMapOf<String, String>()
        for ((name, body) in archive(data)) {
            budget.count++; budget.size += body.size; require(budget.count <= 100000 && budget.size <= 1024L * 1024 * 1024)
            if (name.endsWith(".class")) result[prefix + name] = hash(body)
            else if (name.endsWith(".jar")) result.putAll(inventory(body, "$prefix$name!", depth + 1, budget))
        }; return result
    }
    private fun inventoryHash(values: Map<String, String>): String {
        fun quoted(value: String): String = buildString {
            append('"'); for (c in value) when {
                c == '"' -> append("\\\""); c == '\\' -> append("\\\\")
                c.code < 32 || c.code > 126 -> append("\\u%04x".format(c.code)); else -> append(c)
            }; append('"')
        }
        return hash((if (values.isEmpty()) "{}\n" else values.entries.joinToString(",\n", "{\n", "\n}\n") { "  ${quoted(it.key)}: ${quoted(it.value)}" }).toByteArray())
    }
    private fun corrected(raw: ByteArray, fixed: ByteArray, p: JsonNode) {
        val a = p["artifacts"][0]; val row = p["receipt"]["artifacts"][0]
        require(hash(fixed) == sha(a, "output_sha256") && hash(fixed, "SHA-1") == text(row, "output_sha1"))
        val before = archive(raw); val after = archive(fixed); require(before.keys == after.keys)
        val originalClass = requireNotNull(before[TARGET]); val fixedClass = requireNotNull(after[TARGET])
        require(hash(originalClass) == sha(p["patch"], "before_sha256") && hash(fixedClass) == sha(row["annotation_patch"], "after_sha256"))
        val expected = expectedAnnotationClass(originalClass)
        require(fixedClass.contentEquals(expected.bytes)) { "change outside exact annotation UTF8 operand" }
        require(row["annotation_patch"]["constant_pool_index"].asInt() == expected.constantPoolIndex &&
            row["annotation_patch"]["utf8_length_offset"].asInt() == expected.lengthOffset)
        for ((name, body) in before) if (name != TARGET) require(body.contentEquals(after.getValue(name))) { "unreviewed class/resource/nested archive/notice change" }
        val oldClasses = inventory(raw); val newClasses = inventory(fixed)
        require(oldClasses.keys == newClasses.keys && oldClasses.size == row["class_count"].asInt() &&
            inventoryHash(oldClasses) == sha(row, "before_class_inventory_sha256") && inventoryHash(newClasses) == sha(row, "after_class_inventory_sha256"))
    }
    private fun receipt(data: ByteArray, p: JsonNode) {
        val r = json(data)
        fields(r, "schema", "revision", "spec_sha256", "builder_sha256", "legacy_builder_sha256", "python", "zlib", "citadel_abi", "artifacts", "runtime_acceptance", "publication")
        require(r == p["receipt"] && text(r, "schema") == "bc.upstream_startup_runtime_receipt.v1" && r["revision"] == p["revision"] &&
            text(r, "spec_sha256") == sha(p["spec"], "sha256") && text(r, "builder_sha256") == sha(p["builder"], "sha256") &&
            text(r, "legacy_builder_sha256") == sha(p["legacy_builder"], "sha256") && text(r, "runtime_acceptance") == "NOT_EVALUATED" &&
            text(r, "publication") == "NOT_AUTHORIZED_BY_THIS_RECEIPT")
        val c = r["citadel_abi"]
        fields(c, "input", "sha256", "class_sha256", "field_names", "dataMap_present")
        require(c["input"] == p["citadel"]["input"] && c["sha256"] == p["citadel"]["input_sha256"] && c["class_sha256"] == p["citadel"]["class_sha256"] &&
            c["dataMap_present"].isBoolean && !c["dataMap_present"].asBoolean() && c["field_names"].isArray && c["field_names"].all { it.isTextual && it.asText() != "dataMap" })
        require(r["artifacts"].isArray && r["artifacts"].size() == 1)
        val row = r["artifacts"][0]
        fields(row, "input", "output", "input_sha256", "output_sha256", "output_sha1", "annotation_patch", "class_count", "before_class_inventory_sha256", "after_class_inventory_sha256", "license", "redistribution")
        for (key in listOf("input", "output", "input_sha256", "output_sha256")) require(row[key] == p["artifacts"][0][key])
        require(row["class_count"].isIntegralNumber && row["class_count"].asInt() > 0)
        val patch = row["annotation_patch"]
        fields(patch, "path", "before_sha256", "after_sha256", "constant_pool_index", "utf8_length_offset", "before", "after", "all_other_class_bytes_invariant")
        for (key in listOf("path", "before_sha256", "before", "after")) require(patch[key] == p["patch"][key])
        sha(patch, "after_sha256")
        require(patch["all_other_class_bytes_invariant"].isBoolean && patch["all_other_class_bytes_invariant"].asBoolean() &&
            patch["constant_pool_index"].isIntegralNumber && patch["constant_pool_index"].asInt() > 0 && patch["utf8_length_offset"].isIntegralNumber && patch["utf8_length_offset"].asInt() > 0)
    }

    fun prepare(root: Path, source: Path, evidence: Path, providerJar: Path, supportHashes: Map<String, String>): PreparedRuntimeOverrides {
        val authored = read(root.resolve(POLICY), MAX_JSON); val p = policy(root); pins(root, p)
        for (key in listOf("spec", "builder", "legacy_builder")) require(hash(read(source.resolve(text(p[key], "path")), MAX_JSON)) == sha(p[key], "sha256"))
        val provider = read(providerJar); val names = listOf("receipt.json", OUTPUT)
        require(supportHashes.keys.filter { it.startsWith(SUPPORT) }.toSet() == names.map { SUPPORT + it }.toSet())
        val support = names.associateWith { name -> read(source.resolve(SUPPORT + name), if (name == "receipt.json") MAX_JSON else MAX_ARCHIVE).also {
            require(hash(it) == requireNotNull(supportHashes[SUPPORT + name])) { "qualified support drift" }
        } }
        receipt(support.getValue("receipt.json"), p); citadel(p); corrected(original(p["artifacts"][0]), support.getValue(OUTPUT), p)
        require(read(root.resolve(POLICY), MAX_JSON).contentEquals(authored)); pins(root, p); require(read(providerJar).contentEquals(provider))
        for (key in listOf("spec", "builder", "legacy_builder")) require(hash(read(source.resolve(text(p[key], "path")), MAX_JSON)) == sha(p[key], "sha256"))
        val directory = safe(evidence.resolve("startup-overrides")); require(!Files.exists(directory, NOFOLLOW_LINKS)); Files.createDirectories(safe(directory.parent)); Files.createDirectory(directory)
        for ((name, body) in support) { val path = directory.resolve(name); Files.write(path, body, CREATE_NEW); require(read(path).contentEquals(body) && path.toFile().setWritable(false, false)) }
        val a = p["artifacts"][0]
        val selection = mapper.writeValueAsBytes(mapOf("schema" to "bc.package_runtime_overrides.v1", "artifacts" to listOf(mapOf("input" to ORIGINAL, "output" to OUTPUT,
            "manifest" to text(a, "manifest"), "project_id" to a["project_id"].asLong(), "file_id" to a["file_id"].asLong()))))
        val selected = directory.resolve("selection.json"); Files.write(selected, selection, CREATE_NEW)
        require(read(selected, MAX_JSON).contentEquals(selection) && selected.toFile().setWritable(false, false) && directory.toFile().setWritable(false, false))
        val pointer = safe(root.resolve(".worklane/startup-overrides/current.path")); Files.createDirectories(safe(pointer.parent))
        require(!Files.exists(pointer, NOFOLLOW_LINKS) || Files.isRegularFile(pointer, NOFOLLOW_LINKS))
        val temporary = pointer.resolveSibling(".current-${UUID.randomUUID()}.path"); Files.writeString(temporary, "$directory\n", CREATE_NEW); safe(pointer)
        Files.move(temporary, pointer, ATOMIC_MOVE, REPLACE_EXISTING)
        return PreparedRuntimeOverrides(directory, mapOf("schema" to "bc.prepared_startup_overrides.v1", "directory" to directory.toString(), "policy_sha256" to hash(authored),
            "qualified_provider_sha256" to hash(provider), "receipt_sha256" to hash(support.getValue("receipt.json")), "support_hashes" to support.mapValues { hash(it.value) },
            "runtime_acceptance" to "NOT_EVALUATED", "publication" to "LOCAL_ONLY_NOT_AUTHORIZED"))
    }

    fun validateCandidates(root: Path, client: Path, server: Path) {
        val authored = read(root.resolve(POLICY), MAX_JSON); val p = policy(root); pins(root, p)
        val raw = original(p["artifacts"][0]); val citadel = citadel(p)
        for ((path, clientSide) in listOf(client to true, server to false)) {
            safe(path)
            ZipFile(path.toFile()).use { zip ->
                val entries = zip.entries().asSequence().toList(); require(entries.size <= 100000 && entries.map { it.name }.distinct().size == entries.size)
                entries.forEach { name(it.name) }
                fun entry(name: String, limit: Int = MAX_ARCHIVE): ByteArray {
                    val item = entries.singleOrNull { it.name == name } ?: error("missing candidate entry: $name")
                    require(!item.isDirectory && item.size in 0..limit.toLong())
                    return zip.getInputStream(item).use { it.readNBytes(limit + 1) }.also { require(it.size <= limit) }
                }
                val prefix: String
                if (clientSide) {
                    val manifest = json(entry("manifest.json", MAX_JSON)); prefix = base(text(manifest, "overrides"), "") + "/"; require(manifest["files"].isArray)
                    require(manifest["files"].all { it["projectID"]?.isIntegralNumber == true && it["fileID"]?.isIntegralNumber == true })
                    val a = p["artifacts"][0]; val c = p["citadel"]
                    require(manifest["files"].none { it["projectID"]?.asLong() == a["project_id"].asLong() && it["fileID"]?.asLong() == a["file_id"].asLong() })
                    require(manifest["files"].count { it["projectID"]?.asLong() == c["project_id"].asLong() } == 1 &&
                        manifest["files"].count { it["projectID"]?.asLong() == c["project_id"].asLong() && it["fileID"]?.asLong() == c["file_id"].asLong() } == 1) { "Citadel ABI tuple missing/duplicated/mixed" }
                } else {
                    val tops = entries.map { it.name.substringBefore('/') }.toSet(); require(tops.size == 1 && entries.all { '/' in it.name }); prefix = tops.single() + "/"
                    val citadelName = text(p["citadel"], "input")
                    require(entries.count { it.name.substringAfterLast('/') == citadelName } == 1 && entry(prefix + "mods/" + citadelName).contentEquals(citadel))
                }
                require(entry(prefix + POLICY, MAX_JSON).contentEquals(authored))
                require(entries.count { it.name.substringAfterLast('/') == OUTPUT } == 1 && entries.none { it.name.substringAfterLast('/') in setOf(ORIGINAL, "alltheleaks.pw.toml") })
                corrected(raw, entry(prefix + "mods/" + OUTPUT), p)
            }
        }; require(read(root.resolve(POLICY), MAX_JSON).contentEquals(authored))
    }
}
