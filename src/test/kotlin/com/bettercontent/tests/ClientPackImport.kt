package com.bettercontent.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import kotlin.io.path.readText

/** Fresh extracted client setup only. Never repairs or overwrites an existing pack. */
internal object ClientPackImport {
    data class Pins(val name: String, val author: String, val version: String, val minecraft: String, val forge: String) {
        fun initCommand() = listOf("packwiz", "init", "--name", name, "--author", author,
            "--version", version, "--mc-version", minecraft, "--modloader", "forge", "--forge-version", forge,
            "--index-file", "index.toml", "--pack-file", "pack.toml", "-y")
    }

    fun pins(manifest: JsonNode): Pins {
        require(manifest.isObject) { "client manifest must be an object" }
        fun text(node: JsonNode, key: String, empty: Boolean = false): String {
            val value = node.path(key)
            require(value.isTextual) { "manifest $key must be a string" }
            val s = value.textValue()
            require((empty && s.isEmpty()) || (s.isNotBlank() && s.length <= 160 &&
                s.matches(Regex("[A-Za-z0-9][A-Za-z0-9 ._+()-]*")))) { "unsafe manifest $key" }
            return s
        }
        val minecraft = manifest.path("minecraft")
        require(minecraft.isObject)
        val loaders = minecraft.path("modLoaders")
        require(loaders.isArray && loaders.size() > 0)
        loaders.forEach { require(it.isObject && it.path("primary").isBoolean && it.path("id").isTextual) }
        val primary = loaders.filter { it.path("primary").booleanValue() }
        require(primary.size == 1) { "exactly one primary mod loader required" }
        val loader = text(primary.single(), "id")
        require(loader.matches(Regex("forge-[0-9]+(?:\\.[0-9]+)+"))) { "primary loader must be pinned Forge" }
        val mc = text(minecraft, "version")
        require(mc.matches(Regex("[0-9]+(?:\\.[0-9]+)+"))) { "Minecraft must be pinned" }
        return Pins(text(manifest, "name"), text(manifest, "author", empty = true), text(manifest, "version"), mc, loader.removePrefix("forge-"))
    }

    fun initialize(candidate: Path, expectedSha256: String, client: Path, log: Path,
                   run: (List<String>, Path, Path) -> Unit = { command, cwd, output -> Commands.run(command, cwd, output) }): Map<String, String> {
        require(Hashes.sha256(candidate) == expectedSha256) { "client candidate changed before initialization" }
        val pins = pins(jacksonObjectMapper().readTree(ZipContracts.entryText(candidate, "manifest.json")))
        require(Files.isDirectory(client, NOFOLLOW_LINKS) && Files.list(client).use { it.count() == 0L }) {
            "client pack initialization requires an empty fresh fixture; existing metadata is never repaired"
        }
        run(pins.initCommand(), client, log)
        // The installed Packwiz parser checks the actual initialized TOML/index, not a home-grown TOML parser.
        run(listOf("packwiz", "list", "--pack-file", "pack.toml", "-y"), client,
            log.resolveSibling(log.fileName.toString().removeSuffix(".log") + "-verify.log"))
        validateBaseline(client, pins)
        return requireNotNull(snapshot(client)) { "initializer must create only pack.toml and index.toml" }
    }

    private fun validateBaseline(client: Path, pins: Pins) {
        require(snapshot(client) != null) { "invalid initialized pack/index tree" }
        val pack = client.resolve("pack.toml").readText()
        fun section(name: String): String = if (name.isEmpty()) pack.substringBefore("[index]") else
            Regex("(?ms)^\\[${Regex.escape(name)}]\\s*\\n(.*?)(?=^\\[|\\z)").find(pack)?.groupValues?.get(1)
                ?: error("missing initialized $name section")
        fun scalar(section: String, key: String): String? {
            val matches = Regex("(?m)^${Regex.escape(key)}\\s*=\\s*\"([^\"]*)\"\\s*$").findAll(section).toList()
            require(matches.size <= 1) { "duplicate initialized $key" }
            return matches.singleOrNull()?.groupValues?.get(1)
        }
        val root = section("")
        require(scalar(root, "name") == pins.name && scalar(root, "version") == pins.version &&
            (scalar(root, "author") ?: "") == pins.author) { "initializer changed manifest identity" }
        require(scalar(section("versions"), "minecraft") == pins.minecraft &&
            scalar(section("versions"), "forge") == pins.forge) { "initializer changed manifest versions" }
        require(scalar(section("index"), "file") == "index.toml" && scalar(section("index"), "hash-format") == "sha256" &&
            scalar(section("index"), "hash") == Hashes.sha256(client.resolve("index.toml"))) { "initialized index binding mismatch" }
    }

    private fun snapshot(client: Path): Map<String, String>? {
        if (!Files.isDirectory(client, NOFOLLOW_LINKS)) return null
        val entries = Files.list(client).use { it.toList() }
        if (entries.map { it.fileName.toString() }.toSet() != setOf("pack.toml", "index.toml") ||
            entries.any { !Files.isRegularFile(it, NOFOLLOW_LINKS) }) return null
        return entries.associate { it.fileName.toString() to Hashes.sha256(it) }
    }

    fun unchanged(client: Path, baseline: Map<String, String>): Boolean =
        baseline.keys == setOf("pack.toml", "index.toml") && snapshot(client) == baseline
}
