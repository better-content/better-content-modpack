package com.bettercontent.tests.suite.v2.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

private val json = jacksonObjectMapper()
// Some pinned third-party data resources use Gson-style comments/trailing commas.
// Try strict JSON first and disclose every accepted extension in the inventory.
private val relaxedDataJson = ObjectMapper(JsonFactory.builder()
    .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
    .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
    .build())
private const val MAX_ENTRIES = 50_000
// The pinned TConstruct 3.11.2.166 runtime JAR has 29,731 unique entries.
// Keep a finite per-JAR cap, in addition to the byte/expansion/path guards.
private const val MAX_NESTED = 32_000
private const val MAX_DATA_BYTES = 1_048_576
private const val MAX_NESTED_JAR_BYTES = 128L * 1024 * 1024
private const val MAX_EXPANDED_JAR_BYTES = 512L * 1024 * 1024
private const val MAX_ARCHIVE_BYTES = 4L * 1024 * 1024 * 1024
// The upstream pinned Deep Void JAR contains two malformed Better Combat data members.
// These hashes pin the unchanged third-party binary and exact minimal comma-only repair payloads;
// no other malformed member, changed JAR or missing client/server overlay can qualify.
private const val PINNED_DEEP_VOID_JAR = "018c80220b13abba85ddeba05a7d4850e2b989d9a14d6f008d65e2e94000f375"
private const val DEEP_VOID_JAR_PATH = "better-content-server/mods/the_deep_void-1.98.1-forge-1.20.1.jar"
private data class DeepVoidRepair(val rawSha256: String, val overlaySha256: String)
private val deepVoidRepairs = mapOf(
    "data/the_deep_void/weapon_attributes/penitent_cross.json" to DeepVoidRepair(
        "5aea29588ea71c608238f2c6cfa09c5a9f28af9bdd58dcb57fe9ed75b8ab776d",
        "01b23aff1fdbef9420e084e09d23f8576d5c17ed67867cedcc4660695722b686"),
    "data/the_deep_void/weapon_attributes/the_catcher.json" to DeepVoidRepair(
        "f788ee1772b5d403267c8f9d7f993a1470a1ed84bcb7396b4788be3068eda81a",
        "b0a1099378e9c6c6fa7987ea2d8e7ea48630d16a12586deebce67d8016856ed7"))

/** Read-only archive inventory. Files named by this output are observations of bytes, not runtime registries.
 * The explicit diagnostic mode retains malformed resource findings but is never qualification-eligible.
 * The default qualification/readiness path still fails closed on every malformed data resource. */
fun inspectCandidate(client: Path, server: Path, diagnostic: Boolean = false): JsonNode {
    val root = json.createObjectNode().put("schema", "bc.playtest_offline_inventory.v2")
    if (diagnostic) root.put("qualification_eligible", false)
    val candidates = root.putObject("candidates")
    val members = root.putArray("members")
    val invalidDataJson = root.putArray("invalid_data_json")
    val pendingPinnedRepairs = mutableSetOf<String>()
    for ((side, path) in listOf("client" to client, "server" to server)) {
        require(Files.isRegularFile(path) && Files.size(path) in 1..MAX_ARCHIVE_BYTES) { "invalid $side archive size" }
        candidates.put("${side}_sha256", hash(Files.newInputStream(path)))
        ZipFile(path.toFile()).use { zip ->
            if (side == "client") {
                val lock = zip.getEntry("better-content-artifacts.json")
                    ?: error("client archive lacks embedded artifact lock")
                require(lock.size in 1..MAX_DATA_BYTES && lock.compressedSize in 0..MAX_DATA_BYTES) { "oversized artifact lock" }
                val parsed = zip.getInputStream(lock).use { json.readTree(boundedBytes(it, MAX_DATA_BYTES.toLong())) }
                require(parsed.path("schema").asText() == "bc.client_artifacts.v1" && parsed.path("artifacts").isArray &&
                    parsed.path("artifacts").all { artifact ->
                        safeName(artifact.path("path").asText()) && artifact.path("url").asText().startsWith("https://") &&
                            artifact.path("hash_format").asText() in setOf("sha1", "sha256", "sha512") &&
                            artifact.path("hash").asText().matches(Regex(when (artifact.path("hash_format").asText()) {
                                "sha1" -> "[a-fA-F0-9]{40}"
                                "sha256" -> "[a-fA-F0-9]{64}"
                                else -> "[a-fA-F0-9]{128}"
                            }))
                    }) { "invalid embedded artifact lock" }
                root.set<JsonNode>("embedded_artifact_lock", parsed)
            }
            val entries = zip.entries().asSequence().toList()
            require(entries.size <= MAX_ENTRIES) { "archive entry count exceeds budget" }
            val seen = mutableSetOf<String>()
            for (entry in entries) {
                require(safeName(if (entry.isDirectory) entry.name.removeSuffix("/") else entry.name) && seen.add(entry.name)) { "unsafe or duplicate ZIP path: ${entry.name}" }
                if (entry.isDirectory) continue
                require(entry.size in 0..MAX_ARCHIVE_BYTES && entry.compressedSize in 0..MAX_ARCHIVE_BYTES) { "unknown or oversized ZIP member: ${entry.name}" }
                val member = members.addObject().put("side", side).put("path", entry.name).put("size", entry.size)
                zip.getInputStream(entry).use { stream ->
                    if (entry.name.endsWith(".jar", true) && (entry.name.startsWith("mods/") || "/mods/" in entry.name)) {
                        val bytes = boundedBytes(stream, MAX_NESTED_JAR_BYTES)
                        require(bytes.size.toLong() == entry.size) { "nested JAR differs from declared ZIP size: ${entry.name}" }
                        member.put("sha256", hash(bytes.inputStream()))
                        val nested = member.putArray("nested")
                        ZipInputStream(bytes.inputStream()).use { inner ->
                            val names = mutableSetOf<String>()
                            var count = 0
                            var expanded = 0L
                            while (true) {
                                val next = inner.nextEntry ?: break
                                require(++count <= MAX_NESTED && safeName(if (next.isDirectory) next.name.removeSuffix("/") else next.name) && names.add(next.name)) { "unsafe nested ZIP member" }
                                if (!next.isDirectory && relevant(next.name)) {
                                    val content = boundedBytes(inner, MAX_DATA_BYTES.toLong())
                                    expanded += content.size
                                    val record = nested.addObject().put("path", next.name).put("size", content.size)
                                        .put("sha256", hash(content.inputStream()))
                                    if (next.name.endsWith(".json", true)) {
                                        val parsed = runCatching { json.readTree(content) }.getOrElse { strictFailure ->
                                            runCatching { relaxedDataJson.readTree(content) }.getOrElse { relaxedFailure ->
                                                val pinned = deepVoidRepairs[next.name]
                                                val exactPinnedSource = side == "server" && entry.name == DEEP_VOID_JAR_PATH &&
                                                    member.path("sha256").asText() == PINNED_DEEP_VOID_JAR &&
                                                    record.path("sha256").asText() == pinned?.rawSha256
                                                if (!diagnostic && !exactPinnedSource) throw IllegalArgumentException(
                                                    "invalid data JSON ${entry.name}!/${next.name}", strictFailure)
                                                record.put("json_syntax", if (diagnostic) "invalid_unparsed_diagnostic"
                                                    else "pinned_malformed_requires_verified_effective_overlay")
                                                invalidDataJson.addObject().put("side", side).put("jar", entry.name)
                                                    .put("path", next.name).put("sha256", record.path("sha256").asText())
                                                    .put("parse_error", relaxedFailure.message.orEmpty().take(512))
                                                if (exactPinnedSource && !diagnostic) pendingPinnedRepairs.add(next.name)
                                                null
                                            }?.also { record.put("json_syntax", "comments_or_trailing_commas") }
                                        }
                                        if (parsed != null) {
                                            require(parsed.isObject || parsed.isArray) { "invalid data JSON ${entry.name}!/${next.name}" }
                                            record.set<JsonNode>("json", parsed)
                                        }
                                    }
                                    if (next.name.equals("META-INF/mods.toml", true) || next.name.equals("fabric.mod.json", true)) {
                                        record.put("metadata_text", content.toString(Charsets.UTF_8))
                                    }
                                } else if (!next.isDirectory) expanded += drainBounded(inner, MAX_NESTED_JAR_BYTES)
                                require(expanded <= MAX_EXPANDED_JAR_BYTES) { "nested JAR expanded data exceeds budget" }
                                inner.closeEntry()
                            }
                        }
                    } else {
                        member.put("sha256", hash(stream, entry.size))
                        if (relevant(entry.name)) {
                            require(entry.size <= MAX_DATA_BYTES) { "large data entry: ${entry.name}" }
                            // Reopen explicitly: the hashed stream is consumed.
                            val data = zip.getInputStream(entry).use { boundedBytes(it, MAX_DATA_BYTES.toLong()) }
                            if (entry.name.endsWith(".json", true)) {
                                val parsed = runCatching { json.readTree(data) }.getOrElse {
                                    throw IllegalArgumentException("invalid data JSON ${entry.name}", it)
                                }
                                require(parsed != null && (parsed.isObject || parsed.isArray)) { "invalid data JSON ${entry.name}" }
                                member.set<JsonNode>("json", parsed)
                            }
                        }
                    }
                }
            }
        }
    }
    if (!diagnostic && pendingPinnedRepairs.isNotEmpty()) {
        require(pendingPinnedRepairs == deepVoidRepairs.keys) {
            "pinned Deep Void repair incomplete: expected exactly two malformed upstream resources"
        }
        val verified = root.putArray("verified_effective_overrides")
        for ((rawPath, repair) in deepVoidRepairs) {
            val relative = rawPath.removePrefix("data/")
            for ((side, archive, prefix) in listOf(
                Triple("client", client, "overrides/kubejs/data/"),
                Triple("server", server, "better-content-server/kubejs/data/"))) {
                val overlayPath = prefix + relative
                ZipFile(archive.toFile()).use { zip ->
                    val entry = zip.getEntry(overlayPath)
                        ?: throw IllegalArgumentException("invalid data JSON $DEEP_VOID_JAR_PATH!/$rawPath: missing $side effective override")
                    require(entry.size in 1..MAX_DATA_BYTES && entry.compressedSize in 0..MAX_DATA_BYTES) {
                        "invalid Deep Void override size: $overlayPath"
                    }
                    val data = zip.getInputStream(entry).use { boundedBytes(it, MAX_DATA_BYTES.toLong()) }
                    require(hash(data.inputStream()) == repair.overlaySha256 &&
                        runCatching { json.readTree(data).isObject }.getOrDefault(false)) {
                        "invalid data JSON $DEEP_VOID_JAR_PATH!/$rawPath: wrong $side effective override"
                    }
                    verified.addObject().put("side", side).put("path", overlayPath)
                        .put("original_jar_sha256", PINNED_DEEP_VOID_JAR)
                        .put("original_data_sha256", repair.rawSha256)
                        .put("overlay_sha256", repair.overlaySha256)
                        .put("upstream_member_parseable", false)
                }
            }
        }
    }
    return root
}

private fun relevant(name: String): Boolean = name == "fabric.mod.json" || name == "META-INF/mods.toml" ||
    (name.startsWith("data/") && name.endsWith(".json"))

private fun safeName(name: String): Boolean = name.isNotBlank() && name.length <= 512 && !name.startsWith('/') &&
    !name.contains('\\') && name.split('/').all { it.isNotBlank() && it != "." && it != ".." && !it.contains('\u0000') }

private fun boundedBytes(input: InputStream, max: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(65536)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(out.size().toLong() + count <= max) { "ZIP member exceeds read budget" }
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}

private fun drainBounded(input: InputStream, max: Long): Long {
    val buffer = ByteArray(65536); var total = 0L
    while (true) { val n = input.read(buffer); if (n < 0) return total; total += n; require(total <= max) { "nested entry exceeds read budget" } }
}

private fun hash(input: InputStream, limit: Long = MAX_ARCHIVE_BYTES): String = input.use {
    val md = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(65536); var total = 0L
    while (true) {
        val count = it.read(buffer)
        if (count < 0) break
        total += count
        require(total <= limit) { "stream exceeds declared size or budget" }
        md.update(buffer, 0, count)
    }
    require(limit == MAX_ARCHIVE_BYTES || total == limit) { "ZIP member differs from declared size" }
    md.digest().joinToString("") { byte -> "%02x".format(byte) }
}
