package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Tag("fast")
class ClientPackImportFastTest {
    @TempDir lateinit var temp: Path
    private val mapper = jacksonObjectMapper()
    private val manifest = """{"name":"Better Content Modpack","author":"","version":"Build 451","minecraft":{"version":"1.20.1","modLoaders":[{"id":"forge-47.4.22","primary":true}]}}"""

    private fun candidate(): Path = temp.resolve("candidate.zip").also { file ->
        ZipOutputStream(Files.newOutputStream(file)).use { out ->
            out.putNextEntry(ZipEntry("manifest.json")); out.write(manifest.toByteArray()); out.closeEntry()
        }
    }

    private fun initialize(client: Path): Pair<Map<String, String>, List<List<String>>> {
        val candidate = candidate()
        val commands = mutableListOf<List<String>>()
        val baseline = ClientPackImport.initialize(candidate, Hashes.sha256(candidate), client, temp.resolve("client-init.log")) { cmd, cwd, _ ->
            commands += cmd
            if (cmd[1] == "init") {
                cwd.resolve("index.toml").writeText("hash-format = \"sha256\"\n")
                cwd.resolve("pack.toml").writeText("""
                    name = "Better Content Modpack"
                    version = "Build 451"
                    pack-format = "packwiz:1.1.0"
                    [index]
                    file = "index.toml"
                    hash-format = "sha256"
                    hash = "${Hashes.sha256(cwd.resolve("index.toml"))}"
                    [versions]
                    minecraft = "1.20.1"
                    forge = "47.4.22"
                """.trimIndent())
            }
        }
        return baseline to commands
    }

    @Test fun initializesOnlyWithExactManifestPinsAndValidatesBeforeImport() {
        val client = Files.createDirectory(temp.resolve("client"))
        val (baseline, commands) = initialize(client)
        assertEquals(listOf("packwiz", "init", "--name", "Better Content Modpack", "--author", "", "--version", "Build 451",
            "--mc-version", "1.20.1", "--modloader", "forge", "--forge-version", "47.4.22", "--index-file", "index.toml",
            "--pack-file", "pack.toml", "-y"), commands[0])
        assertEquals(listOf("packwiz", "list", "--pack-file", "pack.toml", "-y"), commands[1])
        assertTrue(ClientPackImport.unchanged(client, baseline))
        val source = Path.of("src/test/kotlin/com/bettercontent/tests/RuntimeFixtures.kt").readText()
        assertTrue(source.indexOf("ClientPackImport.initialize(") < source.indexOf("for (attempt in 1..3)"))
        assertTrue(source.contains("listOf(\"packwiz\", \"curseforge\", \"import\", pair.client.toString(), \"-y\")"))
        assertTrue(source.contains("!transientHandshake || !ClientPackImport.unchanged(client, initializedBaseline) || attempt == 3"))
        assertFalse(source.contains("Files.delete(index)"))
    }

    @Test fun malformedMissingUnsafeAndWrongPrimaryManifestFieldsAreRejected() {
        val good = mapper.readTree(manifest)
        for (bad in listOf(
            manifest.replace("\"author\":\"\",", ""),
            manifest.replace("\"version\":\"Build 451\"", "\"version\":451"),
            manifest.replace("Better Content Modpack", "--unsafe"),
            manifest.replace("Better Content Modpack", "bad\\nname"),
            manifest.replace("forge-47.4.22", "fabric-0.16.0"),
            manifest.replace("forge-47.4.22", "forge-latest"),
            manifest.replace("forge-47.4.22", "forge-47.4.23"),
            manifest.replace("1.20.1", "1.20.2"),
            manifest.replace("[{\"id\":\"forge-47.4.22\",\"primary\":true}]", "[{\"id\":\"forge-47.4.22\",\"primary\":true},{\"id\":\"fabric-0.16.0\",\"primary\":false}]"),
            manifest.replace("\"primary\":true", "\"primary\":\"true\""),
            manifest.replace("\"primary\":true", "\"primary\":false"),
            manifest.replace("[{\"id\":\"forge-47.4.22\",\"primary\":true}]", "[{\"id\":\"forge-47.4.22\",\"primary\":true},{\"id\":\"forge-47.4.22\",\"primary\":true}]"),
        )) assertThrows(IllegalArgumentException::class.java, { ClientPackImport.pins(mapper.readTree(bad)) }, bad)
        assertEquals(CandidateStateContract.COORDINATE, ClientPackImport.pins(good).coordinate)
        assertEquals("", ClientPackImport.pins(good).author) // The actual bound candidate's author is empty, not missing.
    }

    @Test fun existingPackIndexOrOtherFixtureContentsNeverGetReinitialized() {
        for (name in listOf("pack.toml", "index.toml", "foreign.txt")) {
            val client = Files.createDirectory(temp.resolve(name.replace('.', '-')))
            client.resolve(name).writeText("invalid existing content")
            val archive = candidate()
            assertThrows(IllegalArgumentException::class.java) {
                ClientPackImport.initialize(archive, Hashes.sha256(archive), client, temp.resolve("init.log")) { _, _, _ -> error("must not initialize existing fixture") }
            }
            assertEquals("invalid existing content", client.resolve(name).readText())
        }
    }

    @Test fun initializerFailuresWrongBindingsAndMalformedBaselinesFailClosed() {
        val archive = candidate()
        val client = Files.createDirectory(temp.resolve("client"))
        assertThrows(IllegalArgumentException::class.java) {
            ClientPackImport.initialize(archive, "0".repeat(64), client, temp.resolve("init.log")) { _, _, _ -> error("wrong binding") }
        }
        assertThrows(IllegalStateException::class.java) {
            ClientPackImport.initialize(archive, Hashes.sha256(archive), client, temp.resolve("init.log")) { _, _, _ -> error("initializer network failure") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ClientPackImport.initialize(archive, Hashes.sha256(archive), client, temp.resolve("init.log")) { _, cwd, _ ->
                cwd.resolve("pack.toml").writeText("malformed"); cwd.resolve("index.toml").writeText("")
            }
        }
    }

    @Test fun tlsRetryRequiresByteIdenticalTwoFileBaselineAndNeverDeletesIt() {
        val client = Files.createDirectory(temp.resolve("client"))
        val (baseline, _) = initialize(client)
        val pack = client.resolve("pack.toml").readText()
        val index = client.resolve("index.toml").readText()
        client.resolve("index.toml").writeText(index + "# partial import\n")
        assertFalse(ClientPackImport.unchanged(client, baseline))
        client.resolve("index.toml").writeText(index)
        client.resolve("pack.toml").writeText(pack + "# changed\n")
        assertFalse(ClientPackImport.unchanged(client, baseline))
        client.resolve("pack.toml").writeText(pack)
        Files.createDirectory(client.resolve("mods"))
        assertFalse(ClientPackImport.unchanged(client, baseline))
        assertEquals(index, client.resolve("index.toml").readText())
        assertEquals(pack, client.resolve("pack.toml").readText())
    }
}
