package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Tag("fast")
class CandidateStateContractTest {
    @TempDir lateinit var temp: Path
    private val authored = "level-name=world\nonline-mode=true\nserver-port=25565\nlevel-type=minecraft\\:normal\n"
    private val coordinate = CandidateStateContract.COORDINATE
    private val argsPath = "libraries/net/minecraftforge/forge/$coordinate/unix_args.txt"
    private val launcher = "exec java @user_jvm_args.txt @$argsPath \"\$@\"\n"
    private val manifest = """{"minecraft":{"version":"1.20.1","modLoaders":[{"id":"forge-47.4.22","primary":true}]}}"""

    private fun zip(name: String, entries: Map<String, String>): Path = temp.resolve(name).also { file ->
        ZipOutputStream(Files.newOutputStream(file)).use { out ->
            entries.forEach { (name, text) ->
                out.putNextEntry(ZipEntry(name)); out.write(text.toByteArray()); out.closeEntry()
            }
        }
    }

    private fun serverEntries(properties: String = authored) = mapOf(
        "server/server.properties" to properties,
        "server/run-forge.sh" to launcher,
        "server/$argsPath" to "native arguments",
    )

    @Test fun javaPropertiesSemanticsNotSubstringOrLineMatching() {
        val equivalent = "# comment\nlevel-name : world\nonline-mode true\nserver-port:25565\nlevel-type = minecraft:normal\n"
        assertEquals(CandidateStateContract.properties(authored), CandidateStateContract.productionProperties(equivalent, authored))
        val escaped = "level-na\\\n me=world\nonline-mode=true\nserver-port=25565\nlevel-type=minecraft\\:normal\n"
        assertEquals(CandidateStateContract.properties(authored), CandidateStateContract.properties(escaped))
        for (duplicate in listOf(
            "online-mode=true\nonline-mode=false\n",
            "online-mode=true\nonline\\u002dmode=false\n",
            "server-port:25565\nserver-port 25566\n",
            "level-name=world\nlevel-na\\\n me=other\n",
        )) assertThrows(IllegalArgumentException::class.java) { CandidateStateContract.properties(duplicate) }
        for (wrong in listOf(authored.replace("true", "false"), authored + "extra=value\n", authored.replace("server-port=25565\n", ""),
            "# online-mode=true\n" + authored.replace("online-mode=true\n", ""))) {
            assertThrows(IllegalArgumentException::class.java) { CandidateStateContract.productionProperties(wrong, authored) }
        }
    }

    @Test fun unsafeDecodedWorldNamesFailBeforeResolution() {
        for (name in listOf("", ".", "..", "../escape", "/absolute", "nested/world", "nested\\\\world", "C:world", "world name", "world\\u002fescape", "world\\nname")) {
            assertThrows(IllegalArgumentException::class.java, {
                CandidateStateContract.levelName(CandidateStateContract.properties("level-name=$name\n"))
            }, name)
        }
        assertEquals("custom-world_2", CandidateStateContract.levelName(mapOf("level-name" to "custom-world_2")))
    }

    @Test fun archiveStateChecksAreRootScopedAndReadOnly() {
        val server = zip("clean-server.zip", serverEntries())
        val before = Hashes.sha256(server)
        assertEquals(coordinate, CandidateStateContract.serverArchive(server, authored))
        assertEquals(before, Hashes.sha256(server))
        val cleanClient = zip("clean-client.zip", mapOf("manifest.json" to manifest,
            "overrides/kubejs/data/example/structures/world.nbt" to "authored structure"))
        assertEquals(coordinate, CandidateStateContract.clientArchive(cleanClient))
        val escaping = zip("escaping-server.zip", serverEntries().mapKeys { (key, _) -> key.replaceFirst("server/", "../") })
        assertThrows(IllegalArgumentException::class.java) { CandidateStateContract.serverArchive(escaping, authored) }
        CandidateStateContract.requireFreshPaths(listOf("kubejs/data/example/structures/world.nbt", "kubejs/data/example/ops.json", "config/example/saves/settings.json"))
        val badPaths = listOf("world/level.dat", "world_nether/region/r.0.0.mca", "world_the_end/", "custom/level.dat", "custom_nether/",
            ".better_world_management/state.json", ".prestige/state.json", "saves/", "usercache.json", "usernamecache.json", "ops.json",
            "whitelist.json", "banned-players.json", "banned-ips.json", "generated/runtime-dumps/run.json")
        badPaths.forEachIndexed { index, path ->
            val props = authored.replace("level-name=world", "level-name=custom")
            val contaminated = zip("bad-server-$index.zip", serverEntries(props) + ("server/$path" to "state"))
            assertThrows(IllegalArgumentException::class.java, {
                CandidateStateContract.serverArchive(contaminated, props)
            }, path)
            val client = zip("bad-client-$index.zip", mapOf("manifest.json" to manifest, "overrides/$path" to "state"))
            // Custom server world names are not client save roots.
            if (!path.startsWith("custom")) assertThrows(IllegalArgumentException::class.java, {
                CandidateStateContract.clientArchive(client)
            }, path)
        }
        for (path in listOf("../world", "/world", "config/../world", "overrides\\world"))
            assertThrows(IllegalArgumentException::class.java) { CandidateStateContract.requireFreshPaths(listOf(path)) }
    }

    @Test fun manifestDeclaredOverrideRootCannotHidePackagedState() {
        val custom = manifest.replaceFirst("{", "{\"overrides\":\"payload\",")
        assertEquals(coordinate, CandidateStateContract.clientArchive(zip("custom-clean.zip", mapOf(
            "manifest.json" to custom, "payload/kubejs/data/example/structures/world.nbt" to "authored"))))
        listOf("saves/", ".better_world_management/", "ops.json").forEachIndexed { index, path ->
            val candidate = zip("custom-state-$index.zip", mapOf("manifest.json" to custom, "payload/$path" to "state"))
            assertThrows(IllegalArgumentException::class.java) { CandidateStateContract.clientArchive(candidate) }
        }
        listOf("../payload", "/payload", "nested/payload").forEachIndexed { index, path ->
            val candidate = zip("unsafe-override-$index.zip", mapOf("manifest.json" to custom.replace("payload", path)))
            assertThrows(IllegalArgumentException::class.java) { CandidateStateContract.clientArchive(candidate) }
        }
    }

    @Test fun supportedRuntimeRejectsLoaderAmbiguityDriftAndServerMismatch() {
        val mapper = jacksonObjectMapper()
        assertEquals(coordinate, CandidateStateContract.clientCoordinate(mapper.readTree(manifest)))
        for (bad in listOf(manifest.replace("1.20.1", "1.20.2"), manifest.replace("47.4.22", "47.4.23"),
            manifest.replace("forge-47.4.22", "fabric-0.16.0"), manifest.replace("true", "false"),
            manifest.replace("}]", "},{\"id\":\"fabric-0.16.0\",\"primary\":false}]")))
            assertThrows(IllegalArgumentException::class.java) { CandidateStateContract.clientCoordinate(mapper.readTree(bad)) }
        assertEquals(coordinate, CandidateStateContract.serverCoordinate(listOf("libraries/net/minecraftforge/forge/", argsPath), launcher))
        for ((paths, script) in listOf(
            emptyList<String>() to launcher,
            listOf(argsPath.replace("47.4.22", "47.4.23")) to launcher,
            listOf(argsPath) to launcher.replace("47.4.22", "47.4.23"),
            listOf(argsPath, argsPath.replace("47.4.22", "47.4.23")) to launcher,
            listOf(argsPath) to "java @some-other-args.txt",
        )) assertThrows(IllegalArgumentException::class.java) { CandidateStateContract.serverCoordinate(paths, script) }
    }

    @Test fun extractedFirstLaunchChecksFindStateAndNeverDeleteIt() {
        val directory = Files.createDirectory(temp.resolve("fixture"))
        Files.writeString(directory.resolve("server.properties"), authored)
        Files.writeString(directory.resolve("run-forge.sh"), launcher)
        Files.createDirectories(directory.resolve(argsPath).parent)
        Files.writeString(directory.resolve(argsPath), "args")
        assertEquals(coordinate, CandidateStateContract.extractedServer(directory, authored))
        val state = Files.createDirectories(directory.resolve("world")).resolve("level.dat")
        Files.writeString(state, "saved state")
        assertThrows(IllegalArgumentException::class.java) { CandidateStateContract.extractedServer(directory, authored) }
        assertEquals("saved state", Files.readString(state))
        val symlinkFixture = Files.createDirectory(temp.resolve("symlink-fixture"))
        Files.createSymbolicLink(symlinkFixture.resolve("foreign"), directory)
        assertThrows(IllegalArgumentException::class.java) { CandidateStateContract.extractedFresh(symlinkFixture) }
    }

    @Test fun quickplayPassesExplicitCoordinateToStubPortablemcWithoutMinecraft() {
        // runpy + in-memory modules exercise the real Python entry point without installing/launching anything.
        val script = """
            import runpy, sys, types
            calls = []
            class Context:
                def __init__(self, main, work): calls.append(('context', str(main), str(work)))
            class Env:
                jvm_args = []
                def run(self): calls.append(('run', list(self.jvm_args)))
            class ForgeVersion:
                def __init__(self, coordinate, context): calls.append(('coordinate', coordinate))
                def set_auth_offline(self, username, uuid): calls.append(('auth', username, uuid))
                def set_quick_play_singleplayer(self, world): calls.append(('world', world))
                def install(self): return Env()
            sys.modules['portablemc'] = types.ModuleType('portablemc')
            sys.modules['portablemc.forge'] = types.SimpleNamespace(ForgeVersion=ForgeVersion)
            sys.modules['portablemc.standard'] = types.SimpleNamespace(Context=Context)
            source = sys.argv[1]
            sys.argv = [source, 'main', 'work', 'java', '-Xmx2G -Dtest="two words"', 'name', 'uuid', 'save', '9.8.7-6.5.4']
            runpy.run_path(source)
            assert ('coordinate', '9.8.7-6.5.4') in calls, calls
            assert ('world', 'save') in calls, calls
            assert ('run', ['-Xmx2G', '-Dtest=two words']) in calls, calls
            sys.argv[-1] = 'unsafe/coordinate'
            try: runpy.run_path(source)
            except ValueError: pass
            else: raise AssertionError('unsafe coordinate accepted')
            print('stub quickplay passed')
        """.trimIndent()
        val source = Path.of(System.getProperty("bc.repo.root", ".")).toAbsolutePath().resolve("src/test/resources/quickplay_world.py")
        val output = BoundedCommand.capture(temp, listOf("python3", "-B", "-c", script, source.toString()), Duration.ofSeconds(15))
        assertTrue(output.contains("stub quickplay passed"))
    }
}
