package com.bettercontent.tests.suite.v2.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

private val json = jacksonObjectMapper()
private const val MAX_ENTRIES = 50_000
private const val MAX_NESTED = 20_000
private const val MAX_DATA_BYTES = 1_048_576
private const val MAX_NESTED_JAR_BYTES = 128L * 1024 * 1024
private const val MAX_EXPANDED_JAR_BYTES = 512L * 1024 * 1024
private const val MAX_ARCHIVE_BYTES = 4L * 1024 * 1024 * 1024

/** Read-only archive inventory. Files named by this output are observations of bytes, not runtime registries. */
fun inspectCandidate(client: Path, server: Path): JsonNode {
    val root = json.createObjectNode().put("schema", "bc.playtest_offline_inventory.v2")
    val candidates = root.putObject("candidates")
    val members = root.putArray("members")
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
                                        val parsed = json.readTree(content)
                                        require(parsed != null && (parsed.isObject || parsed.isArray)) { "invalid data JSON ${next.name}" }
                                        record.set<JsonNode>("json", parsed)
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
                            if (entry.name.endsWith(".json", true)) json.readTree(data).also {
                                require(it != null && (it.isObject || it.isArray)) { "invalid data JSON ${entry.name}" }
                                member.set<JsonNode>("json", it)
                            }
                        }
                    }
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
