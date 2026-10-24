package com.bettercontent.tests.release

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
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
import java.util.jar.Manifest
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Reviewed v2 successor, isolated from immutable v1 policy/receiver and interface corrections.
 * Source qualification, not a runtime or publication verdict, supplies the exact support bytes. */
object UpstreamRuntimeOverridesV2 {
    private const val POLICY = "config/better-content-runtime-overrides-v2.json"
    private const val SUPPORT = "build/upstream-runtime-overrides-v2/"
    private const val RAW_PROVIDER_SHA256 = "SOURCE_QUALIFIED_RAW_PROVIDER_SHA256"
    private const val MAX_JSON = 2 * 1024 * 1024
    private const val MAX_ARCHIVE = 256 * 1024 * 1024
    private const val MAX_ENTRY = 128 * 1024 * 1024
    private const val MAX_EXPANDED = 1024L * 1024 * 1024
    private val mapper = jacksonObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
    private val digestPattern = Regex("[0-9a-f]{64}")
    // Names are an exact reviewed successor set, not an allowed suffix/version family.
    private val outputs = linkedMapOf(
        "fabric-api-0.92.6+1.11.14+1.20.1.jar" to "fabric-api-0.92.6+1.11.14+1.20.1-bc-runtime.1.jar",
        "emi_loot-0.7.9+1.20.1+forge.jar" to "emi_loot-0.7.9+1.20.1+forge-bc-runtime.1.jar",
        "yet_another_config_lib_v3-3.6.6+1.20.1-forge.jar" to "yet_another_config_lib_v3-3.6.6+1.20.1-forge-bc-runtime.1.jar",
        "relics-1.20.1-0.8.0.13.jar" to "relics-1.20.1-0.8.0.13-bc-runtime.1.jar",
        "AdPother-1.20.1-8.1.49.0-build.2294.jar" to "AdPother-1.20.1-8.1.49.0-build.2294-bc-runtime.2.jar",
    )
    private const val SUPERSEDED_ADPOTHER = "AdPother-1.20.1-8.1.49.0-build.2294-bc-runtime.1.jar"

    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    private fun safe(path: Path): Path {
        val absolute = path.toAbsolutePath()
        require(absolute == absolute.normalize()) { "non-normal path: $path" }
        var cursor: Path? = absolute
        while (cursor != null) {
            require(!Files.isSymbolicLink(cursor)) { "symlink input/destination: $cursor" }
            cursor = cursor.parent
        }
        return absolute
    }
    private fun bytes(path: Path, limit: Int = MAX_ARCHIVE): ByteArray {
        val p = safe(path)
        val before = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        require(before.isRegularFile && before.size() <= limit) { "file type/size: $p" }
        val data = Files.newInputStream(p, NOFOLLOW_LINKS).use { it.readNBytes(limit + 1) }
        val after = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        require(data.size <= limit && before.size() == after.size() && before.lastModifiedTime() == after.lastModifiedTime() &&
            before.fileKey() == after.fileKey()) { "changed input: $p" }
        return data
    }
    private fun json(data: ByteArray): JsonNode {
        require(data.size <= MAX_JSON) { "JSON size limit" }
        return mapper.factory.createParser(data).use { parser ->
            val result = mapper.readTree<JsonNode>(parser)
            require(result != null && parser.nextToken() == null) { "invalid/trailing JSON" }
            result
        }
    }
    private fun fields(node: JsonNode, vararg expected: String) {
        require(node.isObject && node.fieldNames().asSequence().toSet() == expected.toSet()) { "unknown/missing fields: $node" }
    }
    private fun text(node: JsonNode, field: String): String {
        val value = node.get(field)
        require(value != null && value.isTextual && value.asText().isNotEmpty()) { "missing text: $field" }
        return value.asText()
    }
    private fun digest(node: JsonNode, field: String): String = text(node, field).also {
        require(digestPattern.matches(it)) { "invalid digest: $field" }
    }
    private fun archiveName(name: String) {
        require(name.isNotEmpty() && !name.contains('\\') && name.none { it.code < 32 } && ':' !in name &&
            name.removeSuffix("/").split('/').all { it.isNotEmpty() && it != "." && it != ".." }) { "unsafe archive name: $name" }
    }
    private fun basename(name: String, suffix: String): String = name.also {
        archiveName(it)
        require('/' !in it && it.endsWith(suffix)) { "unsafe basename: $it" }
    }
    private fun archive(data: ByteArray): Map<String, ByteArray> {
        require(data.size <= MAX_ARCHIVE) { "archive size limit" }
        val result = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                archiveName(entry.name)
                require(result.size < 100000 && entry.name !in result) { "duplicate/too many entries" }
                require(entry.size <= MAX_ENTRY) { "entry size limit" }
                val body = zip.readNBytes(MAX_ENTRY + 1)
                total += body.size
                require(body.size <= MAX_ENTRY && total <= MAX_EXPANDED) { "expanded archive limit" }
                result[entry.name] = body
                zip.closeEntry()
            }
        }
        require(result.isNotEmpty()) { "empty/invalid archive" }
        return result
    }
    private class InventoryBudget(var count: Int = 0, var size: Long = 0)
    private fun inventory(data: ByteArray, prefix: String = "", depth: Int = 0,
                          budget: InventoryBudget = InventoryBudget()): LinkedHashMap<String, String> {
        require(depth <= 4) { "nested archive depth" }
        val result = linkedMapOf<String, String>()
        for ((name, body) in archive(data)) {
            budget.count++; budget.size += body.size
            require(budget.count <= 100000 && budget.size <= MAX_EXPANDED) { "recursive inventory budget" }
            when {
                name.endsWith(".class") -> result[prefix + name] = hash(body)
                name.endsWith(".jar") -> result.putAll(inventory(body, "$prefix$name!", depth + 1, budget))
            }
        }
        return result
    }
    // The producer's class inventory is Python json.dumps(indent=2, ensure_ascii=True) + newline.
    private fun inventoryDigest(classes: Map<String, String>): String {
        fun quoted(s: String): String = buildString {
            append('"')
            for (c in s) when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c.code < 32 || c.code > 126 -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
            append('"')
        }
        val encoded = if (classes.isEmpty()) "{}\n" else classes.entries.joinToString(",\n", "{\n", "\n}\n") {
            "  ${quoted(it.key)}: ${quoted(it.value)}"
        }
        return hash(encoded.toByteArray(Charsets.UTF_8))
    }
    private fun provider(data: ByteArray, contract: JsonNode) {
        val entries = archive(data)
        val configName = text(contract, "config")
        val registration = Manifest(ByteArrayInputStream(requireNotNull(entries["META-INF/MANIFEST.MF"])))
            .mainAttributes.getValue("MixinConfigs")?.split(',')?.map { it.trim() }.orEmpty()
        require(registration.count { it == configName } == 1) { "Compat production config not registered in manifest" }
        val config = json(requireNotNull(entries[configName]) { "missing Compat production config" })
        require(config["required"]?.isBoolean == true && config["required"] == contract["config_required"] &&
            config["plugin"]?.asText() == text(contract, "plugin")) { "Compat production registration drift" }
        val plugin = text(contract, "plugin").replace('.', '/') + ".class"
        require(hash(requireNotNull(entries[plugin])) == digest(contract, "plugin_sha256")) { "Compat plugin bytes" }
        val mixins = config["mixins"]
        require(mixins != null && mixins.isArray) { "Compat mixin registration" }
        val pkg = text(config, "package").replace('.', '/')
        contract["classes"].fields().forEachRemaining { (name, expected) ->
            require(mixins.count { it.isTextual && it.asText() == name } == 1) { "Compat replacement registration: $name" }
            require(hash(requireNotNull(entries["$pkg/${name.replace('.', '/')}.class"])) == expected.asText()) {
                "Compat replacement bytes: $name"
            }
        }
    }
    private fun policy(root: Path): JsonNode {
        val p = json(bytes(root.resolve(POLICY), MAX_JSON))
        fields(p, "schema", "producer", "provider_artifact", "revision", "spec", "builder", "required_compat",
            "artifacts", "receipt", "publication", "runtime_acceptance")
        require(text(p, "schema") == "bc.runtime_archive_overrides.v2" && text(p, "producer") == "better-compat-fixes" &&
            text(p, "provider_artifact") == "better-compat-fixes-0.1.9.jar" && text(p, "revision") == "bc-runtime.2" &&
            text(p, "publication") == "LOCAL_ONLY_NOT_AUTHORIZED" && text(p, "runtime_acceptance") == "NOT_EVALUATED") {
            "unsupported runtime override policy"
        }
        for ((field, path) in mapOf("spec" to "src/upstreamRuntime/overrides-v2.json",
            "builder" to "scripts/build_upstream_runtime_overrides_v2.py")) {
            fields(p[field], "path", "sha256")
            require(text(p[field], "path") == path)
            digest(p[field], "sha256")
        }
        val c = p["required_compat"]
        fields(c, "condition", "config", "config_required", "plugin", "plugin_sha256", "classes")
        require(c["config_required"].isBoolean && !c["config_required"].asBoolean() &&
            c["classes"].isObject && c["classes"].size() == 3)
        digest(c, "plugin_sha256")
        c["classes"].fields().forEachRemaining { (name, value) ->
            archiveName(name.replace('.', '/') + ".class")
            require(value.isTextual && digestPattern.matches(value.asText()))
        }
        require(p["artifacts"].isArray && p["artifacts"].size() == 5)
        val names = mutableSetOf<String>(); val manifests = mutableSetOf<String>(); val ids = mutableSetOf<Pair<Long, Long>>()
        for (a in p["artifacts"]) {
            fields(a, "input", "output", "manifest", "project_id", "file_id", "input_sha1", "input_sha256", "output_sha256")
            val input = basename(text(a, "input"), ".jar")
            val output = basename(text(a, "output"), ".jar")
            require(outputs[input] == output && names.add(input) && names.add(output)) { "not an exact reviewed v2 archive identity" }
            require(manifests.add(basename(text(a, "manifest"), ".pw.toml")))
            require(a["project_id"].isIntegralNumber && a["file_id"].isIntegralNumber && a["project_id"].asLong() > 0 &&
                a["file_id"].asLong() > 0 && ids.add(a["project_id"].asLong() to a["file_id"].asLong()))
            require(Regex("[0-9a-f]{40}").matches(text(a, "input_sha1")))
            digest(a, "input_sha256"); digest(a, "output_sha256")
        }
        return p
    }
    private fun pins(root: Path, p: JsonNode) {
        val expected = p["artifacts"].associateBy { text(it, "input") }
        val seen = mutableSetOf<String>()
        Files.newDirectoryStream(safe(root.resolve("mods")), "*.pw.toml").use { paths ->
            for (path in paths) {
                // Packwiz's bounded scalar subset; duplicate keys/sections fail instead of selecting a last value.
                var section = ""
                val values = linkedMapOf<String, String>(); val sections = mutableSetOf<String>()
                for (raw in bytes(path, MAX_JSON).toString(Charsets.UTF_8).lines()) {
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith('#')) continue
                    if (line.startsWith('[')) {
                        require(Regex("\\[[A-Za-z0-9_.-]+]").matches(line) && sections.add(line)) { "invalid/duplicate pin section" }
                        section = line.substring(1, line.length - 1) + "."
                    } else {
                        val match = Regex("([A-Za-z0-9_-]+)\\s*=\\s*(.+)").matchEntire(line) ?: error("invalid pin scalar")
                        require(values.put(section + match.groupValues[1], match.groupValues[2].trim()) == null) { "duplicate pin key" }
                    }
                }
                val input = values["filename"]?.removeSurrounding("\"") ?: continue
                val selectedTuple = p["artifacts"].firstOrNull {
                    values["update.curseforge.project-id"] == it["project_id"].asText() &&
                        values["update.curseforge.file-id"] == it["file_id"].asText()
                }
                require(selectedTuple == null || text(selectedTuple, "input") == input) { "selected original CF tuple has mixed filename" }
                val a = expected[input] ?: continue
                require(seen.add(input) && path.fileName.toString() == text(a, "manifest")) { "duplicate/moved original pin" }
                fun eq(key: String, value: String) = require(values[key] == value) { "original pin drift: $path $key" }
                eq("filename", mapper.writeValueAsString(input)); eq("side", "\"both\"")
                eq("download.hash-format", "\"sha1\""); eq("download.hash", mapper.writeValueAsString(text(a, "input_sha1")))
                eq("download.mode", "\"metadata:curseforge\"")
                eq("update.curseforge.project-id", a["project_id"].asText()); eq("update.curseforge.file-id", a["file_id"].asText())
            }
        }
        require(seen == expected.keys) { "missing original root pins" }
    }
    private fun receipt(data: ByteArray, p: JsonNode): JsonNode {
        val r = json(data)
        fields(r, "schema", "revision", "spec_sha256", "builder_sha256", "python", "zlib", "compat", "artifacts", "runtime_acceptance", "publication")
        // Only the whole raw provider archive varies with freshly qualified unrelated Compat code.
        // Its actual digest remains validated and recorded; no historical JAR hash is imported.
        digest(r["compat"], "sha256")
        require(p["receipt"]["compat"]["sha256"]?.asText() == RAW_PROVIDER_SHA256) { "raw provider policy placeholder missing" }
        val normalized = r.deepCopy<ObjectNode>()
        (normalized["compat"] as ObjectNode).put("sha256", RAW_PROVIDER_SHA256)
        require(normalized == p["receipt"]) { "receipt differs from reviewed recipe/output rows" }
        require(text(r, "schema") == "bc.upstream_runtime_receipt.v1" && r["revision"] == p["revision"] &&
            r["spec_sha256"].asText() == digest(p["spec"], "sha256") &&
            r["builder_sha256"].asText() == digest(p["builder"], "sha256") &&
            text(r, "runtime_acceptance") == "NOT_EVALUATED" && text(r, "publication") == "NOT_AUTHORIZED_BY_THIS_RECEIPT")
        val c = r["compat"]
        fields(c, "sha256", "classes", "plugin_sha256", "config_required", "condition", "runtime_behavior")
        digest(c, "sha256") // Raw pre-stamp provider, deliberately NOT compared to the qualified stamped archive.
        for (key in listOf("classes", "plugin_sha256", "config_required", "condition")) require(c[key] == p["required_compat"][key])
        require(text(c, "runtime_behavior") == "NOT_EVALUATED" && r["artifacts"].isArray && r["artifacts"].size() == 5)
        for ((index, a) in p["artifacts"].withIndex()) {
            val row = r["artifacts"][index]
            fields(row, "input", "output", "input_sha256", "output_sha256", "output_sha1", "resources", "class_bytes_invariant",
                "class_count", "class_inventory_sha256", "license", "redistribution")
            for (key in listOf("input", "output", "input_sha256", "output_sha256")) require(row[key] == a[key])
            require(row["class_bytes_invariant"].isBoolean && row["class_bytes_invariant"].asBoolean() &&
                row["class_count"].isIntegralNumber && row["class_count"].asInt() >= 0)
            digest(row, "class_inventory_sha256")
            require(row["resources"].isArray && row["resources"].size() in 1..6)
            val seen = mutableSetOf<String>()
            for (resource in row["resources"]) {
                fields(resource, "path", "before_sha256", "after_sha256")
                require(resource["path"].isArray && resource["path"].size() in 1..4)
                resource["path"].forEach { require(it.isTextual); archiveName(it.asText()) }
                require(seen.add(resource["path"].toString()))
                digest(resource, "before_sha256"); digest(resource, "after_sha256")
            }
        }
        return r
    }

    fun prepare(root: Path, source: Path, evidence: Path, providerJar: Path,
                supportHashes: Map<String, String>): PreparedRuntimeOverrides {
        safe(root); safe(source); safe(evidence); safe(providerJar)
        val policyBytes = bytes(root.resolve(POLICY), MAX_JSON)
        val p = policy(root)
        pins(root, p)
        for (field in listOf("spec", "builder")) {
            require(hash(bytes(source.resolve(text(p[field], "path")), MAX_JSON)) == digest(p[field], "sha256")) { "source recipe drift: $field" }
        }
        val spec = json(bytes(source.resolve(text(p["spec"], "path")), MAX_JSON))
        require(text(spec, "schema") == "bc.upstream_runtime_overrides.v2" && spec["required_compat"] == p["required_compat"] &&
            spec["revision"] == p["revision"] && spec["artifacts"].size() == 5)
        for ((index, a) in p["artifacts"].withIndex()) {
            val s = spec["artifacts"][index]
            require(s["input"] == a["input"] && s["output"] == a["output"] && s["sha256"] == a["input_sha256"] &&
                s["sha1"] == a["input_sha1"] && s["curseforge"]["project"] == a["project_id"] && s["curseforge"]["file"] == a["file_id"])
        }
        val providerBytes = bytes(providerJar)
        provider(providerBytes, p["required_compat"])
        val names = listOf("receipt.json") + p["artifacts"].map { text(it, "output") }
        require(supportHashes.keys.filter { it.startsWith(SUPPORT) }.toSet() == names.map { SUPPORT + it }.toSet()) {
            "unknown/missing v2 qualified support declaration"
        }
        val supports = names.associateWith { name ->
            val relative = SUPPORT + name
            val declared = requireNotNull(supportHashes[relative]) { "undeclared qualified support: $relative" }
            require(digestPattern.matches(declared))
            bytes(source.resolve(relative), if (name == "receipt.json") MAX_JSON else MAX_ARCHIVE).also {
                require(hash(it) == declared) { "qualified support byte drift: $relative" }
            }
        }
        val r = receipt(supports.getValue("receipt.json"), p)
        for ((index, a) in p["artifacts"].withIndex()) {
            val data = supports.getValue(text(a, "output"))
            require(hash(data) == digest(a, "output_sha256")) { "corrected output drift" }
            val classes = inventory(data)
            val row = r["artifacts"][index]
            require(classes.size == row["class_count"].asInt() && inventoryDigest(classes) == digest(row, "class_inventory_sha256")) {
                "recursive class inventory mismatch"
            }
            for (resource in row["resources"]) {
                var current = data
                for (component in resource["path"]) current = requireNotNull(archive(current)[component.asText()])
                require(hash(current) == digest(resource, "after_sha256")) { "corrected resource mismatch" }
            }
        }
        require(bytes(root.resolve(POLICY), MAX_JSON).contentEquals(policyBytes)) { "policy changed during preparation" }
        pins(root, p)
        for (field in listOf("spec", "builder")) require(
            hash(bytes(source.resolve(text(p[field], "path")), MAX_JSON)) == digest(p[field], "sha256")) { "source changed during preparation" }
        require(bytes(providerJar).contentEquals(providerBytes)) { "qualified provider changed during preparation" }
        val directory = safe(evidence.resolve("runtime-overrides-v2"))
        require(!Files.exists(directory, NOFOLLOW_LINKS)) { "snapshot already exists" }
        Files.createDirectories(safe(directory.parent)); Files.createDirectory(directory)
        for ((name, data) in supports) {
            val output = directory.resolve(name)
            Files.write(output, data, CREATE_NEW)
            require(bytes(output).contentEquals(data)) { "snapshot copy mismatch" }
            require(output.toFile().setWritable(false, false)) { "cannot seal support file" }
        }
        val selection = mapOf("schema" to "bc.package_runtime_overrides.v1", "artifacts" to p["artifacts"].map { a ->
            mapOf("input" to text(a, "input"), "output" to text(a, "output"), "manifest" to text(a, "manifest"),
                "project_id" to a["project_id"].asLong(), "file_id" to a["file_id"].asLong())
        })
        val selected = mapper.writeValueAsBytes(selection)
        Files.write(directory.resolve("selection.json"), selected, CREATE_NEW)
        require(bytes(directory.resolve("selection.json"), MAX_JSON).contentEquals(selected))
        require(directory.resolve("selection.json").toFile().setWritable(false, false) && directory.toFile().setWritable(false, false))
        val pointer = safe(root.resolve(".worklane/runtime-overrides-v2/current.path"))
        Files.createDirectories(safe(pointer.parent))
        require(!Files.exists(pointer, NOFOLLOW_LINKS) || Files.isRegularFile(pointer, NOFOLLOW_LINKS))
        val temporary = pointer.resolveSibling(".current-${UUID.randomUUID()}.path")
        Files.writeString(temporary, "$directory\n", CREATE_NEW)
        safe(pointer)
        Files.move(temporary, pointer, ATOMIC_MOVE, REPLACE_EXISTING)
        return PreparedRuntimeOverrides(directory, mapOf(
            "schema" to "bc.prepared_runtime_overrides.v2", "directory" to directory.toString(),
            "policy_sha256" to hash(policyBytes), "receipt_sha256" to hash(supports.getValue("receipt.json")),
            "qualified_provider_sha256" to hash(providerBytes), "raw_provider_sha256" to text(r["compat"], "sha256"),
            "support_hashes" to supports.mapValues { hash(it.value) },
            "publication" to "LOCAL_ONLY_NOT_AUTHORIZED", "runtime_acceptance" to "NOT_EVALUATED"))
    }

    fun validateCandidates(root: Path, client: Path, server: Path) {
        val authored = bytes(root.resolve(POLICY), MAX_JSON)
        val p = policy(root)
        pins(root, p)
        for ((path, isClient) in listOf(client to true, server to false)) {
            safe(path)
            ZipFile(path.toFile()).use { zip ->
                val entries = zip.entries().asSequence().toList()
                require(entries.size <= 100000 && entries.map { it.name }.distinct().size == entries.size) { "duplicate candidate entries" }
                entries.forEach { archiveName(it.name) }
                require(entries.none { it.name.substringAfterLast('/') == SUPERSEDED_ADPOTHER }) { "superseded v1 AdPother archive remains" }
                fun entry(name: String, limit: Int = MAX_ARCHIVE): ByteArray {
                    val item = entries.singleOrNull { it.name == name } ?: error("missing candidate entry: $name")
                    require(!item.isDirectory && item.size in 0..limit.toLong()) { "candidate entry size/type" }
                    return zip.getInputStream(item).use { it.readNBytes(limit + 1) }.also { require(it.size <= limit) }
                }
                val prefix: String
                if (isClient) {
                    val manifest = json(entry("manifest.json", MAX_JSON))
                    prefix = basename(text(manifest, "overrides"), "") + "/"
                    require(manifest["files"].isArray)
                    for (a in p["artifacts"]) require(manifest["files"].none {
                        it["projectID"]?.asLong() == a["project_id"].asLong() && it["fileID"]?.asLong() == a["file_id"].asLong()
                    }) { "original CurseForge tuple would be downloaded" }
                } else {
                    val tops = entries.map { it.name.substringBefore('/') }.toSet()
                    require(tops.size == 1 && entries.all { '/' in it.name }) { "server requires sole directory prefix" }
                    prefix = tops.single() + "/"
                }
                require(entry(prefix + POLICY, MAX_JSON).contentEquals(authored)) { "candidate policy differs from authored root" }
                for (a in p["artifacts"]) {
                    val output = text(a, "output")
                    require(entries.count { it.name.substringAfterLast('/') == output } == 1) { "missing/duplicate corrected archive" }
                    require(entries.none { it.name.substringAfterLast('/') in setOf(text(a, "input"), text(a, "manifest")) }) { "original archive/manifest still packaged" }
                    require(hash(entry(prefix + "mods/" + output)) == digest(a, "output_sha256")) { "candidate corrected archive drift" }
                }
                val providerName = text(p, "provider_artifact")
                require(entries.count { it.name.substringAfterLast('/') == providerName } == 1) { "missing/duplicate Compat provider" }
                provider(entry(prefix + "mods/" + providerName), p["required_compat"])
            }
        }
    }
}
