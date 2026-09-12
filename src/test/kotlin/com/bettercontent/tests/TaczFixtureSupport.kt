package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

internal data class TaczManifest(
    val path: Path,
    val filename: String,
    val hashFormat: String,
    val hash: String,
    val projectId: Long,
    val fileId: Long,
) {
    val ids: Pair<Long, Long> get() = projectId to fileId
}

internal object TaczFixtureSupport {
    private val quoted = Regex("(?m)^\\s*([A-Za-z][A-Za-z-]*)\\s*=\\s*\"([^\"]+)\"\\s*$")
    private val number = Regex("(?m)^\\s*([A-Za-z][A-Za-z-]*)\\s*=\\s*([0-9]+)\\s*$")

    fun readManifest(path: Path): TaczManifest {
        require(path.isRegularFile()) { "TACZ manifest is missing: $path" }
        return readManifestText(path, path.readText())
    }

    private fun readManifestText(path: Path, text: String): TaczManifest {
        fun string(name: String): String = quoted.findAll(text).firstOrNull { it.groupValues[1] == name }?.groupValues?.get(2)
            ?: error("TACZ manifest $path is missing quoted $name")
        fun numberValue(name: String): Long = number.findAll(text).firstOrNull { it.groupValues[1] == name }?.groupValues?.get(2)?.toLong()
            ?: error("TACZ manifest $path is missing numeric $name")
        return TaczManifest(
            path,
            string("filename"),
            string("hash-format"),
            string("hash").lowercase(),
            numberValue("project-id"),
            numberValue("file-id"),
        )
    }

    fun canonical(root: Path): Map<Pair<Long, Long>, TaczManifest> {
        val manifests = Files.list(root.resolve("tacz")).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".pw.toml") && it.fileName.toString() != "tacz-pre.toml" }
                .sorted().map(::readManifest).toList()
        }
        require(manifests.isNotEmpty()) { "canonical TACZ manifests are missing" }
        require(manifests.map { it.ids }.toSet().size == manifests.size) { "canonical TACZ manifests contain duplicate project/file IDs" }
        return manifests.associateBy { it.ids }
    }

    /** Replace Packwiz's transient root-level TACZ manifests with canonical tacz/ entries. */
    fun reconcileImportedRootManifests(client: Path, repositoryRoot: Path) {
        val canonical = canonical(repositoryRoot)
        val importedPaths = Files.list(client).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".pw.toml") }.sorted().toList()
        }
        require(importedPaths.size == canonical.size) {
            "expected exactly ${canonical.size} imported TACZ root manifests, found ${importedPaths.size}: $importedPaths"
        }
        val imported = importedPaths.map(::readManifest)
        require(imported.map { it.ids }.toSet().size == imported.size) { "duplicate imported TACZ project/file IDs: $importedPaths" }
        val unknown = imported.filter { it.ids !in canonical }
        require(unknown.isEmpty()) { "unknown or unmatched imported TACZ root manifests: ${unknown.map { it.path }}" }

        val tacz = client.resolve("tacz").also { it.createDirectories() }
        imported.forEach { transient ->
            val expected = canonical.getValue(transient.ids)
            require(transient.filename == expected.filename && transient.hash == expected.hash && transient.hashFormat == expected.hashFormat) {
                "imported TACZ manifest disagrees with canonical entry ${expected.path.fileName}: ${transient.path}"
            }
            val destination = tacz.resolve(expected.path.name)
            require(!Files.exists(destination)) { "duplicate canonical TACZ manifest destination: $destination" }
            Files.copy(expected.path, destination, StandardCopyOption.COPY_ATTRIBUTES)
            Files.delete(transient.path)
        }
        require(Files.list(client).use { stream -> stream.noneMatch { it.fileName.toString().endsWith(".pw.toml") } }) {
            "TACZ import left root-level manifests behind"
        }
    }

    /** Verify that resolve materialized exactly the canonical TACZ packs beside their manifests. */
    fun assertResolvedArtifacts(client: Path, repositoryRoot: Path) {
        val canonical = canonical(repositoryRoot)
        val tacz = client.resolve("tacz")
        require(Files.isDirectory(tacz)) { "client fixture is missing tacz/" }
        val manifests = Files.list(tacz).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".pw.toml") && it.fileName.toString() != "tacz-pre.toml" }
                .sorted().map(::readManifest).toList()
        }
        require(manifests.map { it.ids }.toSet() == canonical.keys) {
            "client fixture TACZ manifests do not match canonical IDs: $manifests"
        }
        val expectedArtifacts = manifests.map { it.filename }.toSet()
        val actualArtifacts = Files.list(tacz).use { stream ->
            stream.filter { it.isRegularFile() && it.fileName.toString().endsWith(".zip") }
                .map { it.fileName.toString() }.toList().toSet()
        }
        require(actualArtifacts == expectedArtifacts) {
            "client fixture TACZ artifacts do not match manifests: expected=$expectedArtifacts actual=$actualArtifacts"
        }
        manifests.forEach { manifest ->
            val artifact = tacz.resolve(manifest.filename)
            require(digest(artifact, manifest.hashFormat) == manifest.hash) {
                "client fixture TACZ artifact hash mismatch for ${manifest.path.fileName}"
            }
        }
    }

    fun serverCandidateManifests(zip: Path): Map<Pair<Long, Long>, TaczManifest> {
        val top = ZipContracts.soleTopLevelDirectory(zip)
        return ZipFile(zip.toFile()).use { archive ->
            val entries = archive.entries().asSequence()
                .filter { it.name.startsWith("$top/tacz/") && it.name.endsWith(".pw.toml") && !it.isDirectory }
                .map { entry ->
                    val text = archive.getInputStream(entry).bufferedReader().use { it.readText() }
                    readManifestText(Path.of(entry.name), text)
                }.toList()
            require(entries.map { it.ids }.toSet().size == entries.size) { "server candidate contains duplicate TACZ manifest IDs" }
            entries.associateBy { it.ids }
        }
    }

    private fun digest(path: Path, format: String): String {
        val algorithm = when (format.lowercase().replace("-", "")) {
            "sha1" -> "SHA-1"
            "sha256" -> "SHA-256"
            "sha512" -> "SHA-512"
            else -> format
        }
        val digest = MessageDigest.getInstance(algorithm)
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
