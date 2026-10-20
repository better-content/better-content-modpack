package com.bettercontent.tests

import com.fasterxml.jackson.databind.JsonNode
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.Properties
import java.util.zip.ZipFile

/** Read-only, pre-mutation contracts for production candidates and fresh extracts. */
object CandidateStateContract {
    const val MINECRAFT = "1.20.1"
    const val FORGE = "47.4.22"
    const val COORDINATE = "$MINECRAFT-$FORGE"

    /** Java's parser decodes escapes, continuations and separators before put is called. */
    fun properties(text: String): Map<String, String> {
        val parsed = object : Properties() {
            override fun put(key: Any, value: Any): Any? {
                require(!containsKey(key)) { "duplicate decoded property key: $key" }
                return super.put(key, value)
            }
        }
        parsed.load(StringReader(text))
        return parsed.stringPropertyNames().associateWith { parsed.getProperty(it) }
    }

    fun levelName(properties: Map<String, String>): String {
        val name = requireNotNull(properties["level-name"]) { "missing level-name" }
        require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) && name != "." && name != "..") {
            "level-name must be a safe single basename"
        }
        return name
    }

    fun productionProperties(actual: String, authored: String): Map<String, String> {
        val expected = properties(authored)
        levelName(expected)
        val result = properties(actual)
        levelName(result)
        require(result == expected) { "candidate server.properties differs from authored production defaults" }
        return result
    }

    fun clientCoordinate(manifest: JsonNode): String {
        val mc = manifest.path("minecraft")
        require(mc.path("version").isTextual && mc.path("version").textValue() == MINECRAFT) {
            "unsupported Minecraft runtime; harness supports only $MINECRAFT"
        }
        val loaders = mc.path("modLoaders")
        require(loaders.isArray && loaders.size() == 1) { "exactly one supported Forge loader required" }
        val loader = loaders.single()
        require(loader.path("id").isTextual && loader.path("id").textValue() == "forge-$FORGE" &&
            loader.path("primary").isBoolean && loader.path("primary").booleanValue()) {
            "unsupported loader; harness supports only primary forge-$FORGE"
        }
        return COORDINATE
    }

    private val identityFiles = setOf("usercache.json", "usernamecache.json", "ops.json", "whitelist.json",
        "banned-ips.json", "banned-players.json", "usercache", "usernamecache")
    private val stateDirectories = setOf("world", "world_nether", "world_the_end", "saves",
        ".better_world_management", ".prestige")

    /** Paths are relative to server root or client overrides root, never arbitrary substring matches. */
    fun requireFreshPaths(paths: Iterable<String>, level: String = "world") {
        levelName(mapOf("level-name" to level))
        paths.forEach { raw ->
            require(!raw.startsWith('/') && '\\' !in raw && raw.split('/').none { it == "." || it == ".." }) {
                "unsafe candidate path: $raw"
            }
            val lower = raw.trimEnd('/').lowercase()
            val top = lower.substringBefore('/')
            val world = level.lowercase()
            require(top !in identityFiles && top !in stateDirectories &&
                top !in setOf(world, "${world}_nether", "${world}_the_end") &&
                lower != "generated/runtime-dumps" && !lower.startsWith("generated/runtime-dumps/")) {
                "candidate contains preexisting runtime state: $raw"
            }
        }
    }

    fun serverCoordinate(paths: Iterable<String>, launcher: String): String {
        val prefix = "libraries/net/minecraftforge/forge/"
        val coordinates = paths.filter { it.startsWith(prefix) && it.removePrefix(prefix).trimEnd('/').isNotEmpty() }
            .map { it.removePrefix(prefix).substringBefore('/') }.toSet()
        require(coordinates == setOf(COORDINATE)) { "server Forge coordinates differ from supported $COORDINATE: $coordinates" }
        require(paths.any { it == "$prefix$COORDINATE/unix_args.txt" }) { "server is missing Forge unix_args.txt" }
        val references = Regex("libraries/net/minecraftforge/forge/([^/\\s]+)/unix_args\\.txt").findAll(launcher)
            .map { it.groupValues[1] }.toList()
        require(references == listOf(COORDINATE)) { "server launcher must reference only $COORDINATE" }
        return COORDINATE
    }

    fun serverArchive(archive: Path, authoredProperties: String): String {
        val root = ZipContracts.soleTopLevelDirectory(archive)
        levelName(mapOf("level-name" to root)) // Validate the archive wrapper before any extraction.
        val properties = productionProperties(ZipContracts.entryText(archive, "$root/server.properties"), authoredProperties)
        return ZipFile(archive.toFile()).use { zip ->
            val paths = zip.entries().asSequence().map { entry ->
                require(entry.name.startsWith("$root/")) { "unexpected server entry: ${entry.name}" }
                entry.name.removePrefix("$root/")
            }.filter { it.isNotEmpty() }.toList()
            requireFreshPaths(paths, levelName(properties))
            serverCoordinate(paths, ZipContracts.entryText(archive, "$root/run-forge.sh"))
        }
    }

    fun clientArchive(archive: Path): String = ZipFile(archive.toFile()).use { zip ->
        val manifest = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(ZipContracts.entryText(archive, "manifest.json"))
        val declared = if (manifest.has("overrides")) {
            require(manifest.path("overrides").isTextual) { "client override root must be a string" }
            levelName(mapOf("level-name" to manifest.path("overrides").asText()))
        } else "overrides"
        val roots = setOf("overrides", declared)
        val paths = zip.entries().asSequence().map { it.name }.toList()
        roots.forEach { root ->
            requireFreshPaths(paths.filter { it.startsWith("$root/") }.map { it.removePrefix("$root/") })
        }
        // Also reject runtime state stored outside override roots rather than silently ignoring it.
        requireFreshPaths(paths.filterNot { path -> roots.any { path.startsWith("$it/") } })
        clientCoordinate(manifest)
    }

    fun extractedFresh(directory: Path, level: String = "world") {
        require(Files.isDirectory(directory, NOFOLLOW_LINKS)) { "missing extracted fixture: $directory" }
        val paths = Files.walk(directory).use { stream ->
            stream.filter { it != directory }.map {
                require(!Files.isSymbolicLink(it)) { "symlink in fresh fixture: $it" }
                directory.relativize(it).toString().replace('\\', '/')
            }.toList()
        }
        requireFreshPaths(paths, level)
    }

    fun extractedServer(directory: Path, authoredProperties: String): String {
        val props = productionProperties(Files.readString(directory.resolve("server.properties")), authoredProperties)
        extractedFresh(directory, levelName(props))
        val paths = Files.walk(directory).use { stream -> stream.filter { it != directory }
            .map { directory.relativize(it).toString().replace('\\', '/') }.toList() }
        return serverCoordinate(paths, Files.readString(directory.resolve("run-forge.sh")))
    }
}
