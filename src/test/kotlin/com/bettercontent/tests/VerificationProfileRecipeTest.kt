package com.bettercontent.tests

import com.bettercontent.tests.release.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

@Tag("fast")
class VerificationProfileRecipeTest {
    @TempDir lateinit var directory: Path
    private val tasks = listOf("verifyFull", "stageRuntimeJar")
    private fun mod(profiles: List<VerificationProfile>) = ActiveMod("fixture", "fixture_mod", "fixture.jar", tasks,
        verificationProfiles = profiles)

    @Test fun propertyArgumentsAreSortedAndNeverReplaceDeclaredTasks() {
        assertEquals(listOf("/source/gradlew", "--no-daemon", "clean", "-Pa=true", "-Pz=false", "verifyFull", "stageRuntimeJar"),
            providerVerificationCommand(Path.of("/source"), tasks, false, mapOf("z" to "false", "a" to "true")))
    }

    @Test fun unsafeNamesTasksAndPropertiesFailClosed() {
        assertThrows(IllegalArgumentException::class.java) { VerificationProfile("../outside", tasks) }
        assertThrows(IllegalArgumentException::class.java) { VerificationProfile("bad", listOf("--exclude-task")) }
        assertThrows(IllegalArgumentException::class.java) { VerificationProfile("bad", tasks, mapOf("bad\nkey" to "true")) }
        assertThrows(IllegalArgumentException::class.java) { VerificationProfile("bad", tasks, mapOf("key" to "true\nfalse")) }
    }

    @Test fun profilesCannotOmitDefaultCoverageSkipInitialCleanOrLeaveOptionalArtifactLast() {
        assertThrows(IllegalArgumentException::class.java) { mod(listOf(VerificationProfile("only-optional", tasks, mapOf("optional" to "true")), VerificationProfile("stage", listOf("stageRuntimeJar")))) }
        assertThrows(IllegalArgumentException::class.java) { mod(listOf(VerificationProfile("default", tasks, clean = false))) }
        assertThrows(IllegalArgumentException::class.java) { mod(listOf(VerificationProfile("default", tasks), VerificationProfile("last-optional", tasks, mapOf("optional" to "true")))) }
        assertThrows(IllegalArgumentException::class.java) { mod(listOf(VerificationProfile("default", tasks), VerificationProfile("default", tasks))) }
    }

    @Test fun profileIdentityMatchesIndependentPythonSeparatorContract() {
        val profiles = listOf(VerificationProfile("present", tasks, mapOf("z" to "false", "a" to "true")),
            VerificationProfile("default", listOf("stageRuntimeJar"), clean = false))
        assertEquals("present\u0000true\u0000verifyFull\u0001stageRuntimeJar\u0000a=true\u0001z=false\ndefault\u0000false\u0000stageRuntimeJar\u0000", verificationProfileIdentity(profiles))
        assertNotEquals(verificationProfileIdentity(profiles), verificationProfileIdentity(profiles.map { it.copy(clean = !it.clean) }))
    }

    @Test fun currentManifestRetainsEveryRequiredCompanionAndHasPropertyAbsenceSemantics() {
        val manifest = jacksonObjectMapper().readValue(Path.of(System.getProperty("bc.repo.root")).resolve("gradle/active-custom-mods.json").toFile(), ActiveModManifest::class.java)
        val profiles = manifest.mods.filter { it.verificationProfiles.isNotEmpty() }.associate { it.repository to it.verificationProfiles }
        assertEquals(setOf("better-compat-fixes", "better-spirit-commerce", "better-dimension-fonts", "better-wild-fruit-trees"), profiles.keys)
        assertEquals(listOf("colonyWithoutBees", "spoilageWithoutHeat", "backpackWithoutCurios"), profiles.getValue("better-compat-fixes").dropLast(1).map { it.properties.keys.single() })
        val fruit = profiles.getValue("better-wild-fruit-trees")
        assertTrue(fruit.first().properties.isEmpty(), "hasProperty switches must be omitted, never set false, for present fixtures")
        assertEquals(setOf("withoutTConFixture", "withoutArsFixture", "withoutWildAddonsFixture"), fruit[1].properties.keys)
        assertEquals(setOf("true"), fruit[1].properties.values.toSet())
        assertEquals(listOf("verifyFull", "headlessGameTest", "stageRuntimeJar"), profiles.getValue("better-dimension-fonts").flatMap { it.tasks })
    }

    private fun fixture(wrapperBody: String): Triple<Path, Path, String> {
        val root = directory.resolve("better-content-modpack").also { Files.createDirectories(it) }
        val source = directory.resolve("mod_source/fixture").also { Files.createDirectories(it) }
        Files.writeString(source.resolve(".gitignore"), "build/\nrun-gametest/\ninvoked-tasks.txt\n")
        Files.writeString(source.resolve("gradlew"), "#!/bin/sh\nset -e\nprintf '%s ' \"\$@\" >> invoked-tasks.txt\nprintf '\\n' >> invoked-tasks.txt\n$wrapperBody\n")
        assertTrue(source.resolve("gradlew").toFile().setExecutable(true))
        fun git(vararg args: String): String {
            val process = ProcessBuilder("git", *args).directory(source.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start()
            val text = process.inputStream.bufferedReader().readText().trim()
            assertEquals(0, process.waitFor())
            return text
        }
        git("init", "-q"); git("add", ".gitignore", "gradlew")
        git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-qm", "fixture")
        val input = directory.resolve("fixture-input.jar")
        JarOutputStream(Files.newOutputStream(input)).use { jar ->
            jar.putNextEntry(JarEntry("META-INF/mods.toml")); jar.write("[[mods]]\nmodId=\"fixture_mod\"\n".toByteArray()); jar.closeEntry()
        }
        Files.createDirectories(source.resolve("build")); Files.writeString(source.resolve("build/previous.txt"), "prior build")
        Files.createDirectories(source.resolve("run-gametest/world")); Files.writeString(source.resolve("run-gametest/world/level.dat"), "prior world")
        return Triple(root, source, git("rev-parse", "HEAD"))
    }

    @Test fun actualOrchestratorRunsOrderedProfilesWithRetainedPriorOutputsAndDefaultArtifactLast() {
        val (root, source, head) = fixture("mkdir -p build/libs run-gametest/world\nprintf '%s ' \"\$@\" > run-gametest/world/level.dat\ncp '${directory.resolve("fixture-input.jar")}' build/libs/fixture.jar")
        val definition = mod(listOf(VerificationProfile("optional", tasks, mapOf("optionalRuntime" to "true")), VerificationProfile("default", tasks)))
        val evidence = root.resolve("evidence").also { Files.createDirectories(it) }
        val staging = evidence.resolve("stage").also { Files.createDirectories(it) }
        val result = build(root, directory, evidence, staging, definition, SourceUpdate("fixture", "fixture.jar", head, head, "same"), false, true)
        assertTrue(Files.isRegularFile(result.jar))
        val calls = Files.readAllLines(source.resolve("invoked-tasks.txt"))
        assertEquals(2, calls.size)
        assertTrue(calls.first().contains("-PoptionalRuntime=true")); assertFalse(calls.last().contains("-PoptionalRuntime"))
        val scopes = evidence.resolve("source-profiles/fixture")
        assertEquals("prior build", Files.readString(scopes.resolve("optional/prior-source-outputs/fixture/build/previous.txt")))
        assertEquals("prior world", Files.readString(scopes.resolve("optional/prior-source-outputs/fixture/run-gametest/world/level.dat")))
        assertTrue(Files.readString(scopes.resolve("default/prior-source-outputs/fixture/run-gametest/world/level.dat")).contains("-PoptionalRuntime=true"))
        assertTrue(Files.isRegularFile(scopes.resolve("default/prior-source-outputs/fixture/build/libs/fixture.jar")))
        assertTrue(Files.readString(evidence.resolve("mod-fixture.log")).contains("verification_profile=default"))
    }

    @Test fun failedProfileCannotRunLaterDefaultOrModifyDeployedArtifact() {
        val (root, source, head) = fixture("exit 7")
        val definition = mod(listOf(VerificationProfile("fails", tasks, mapOf("optionalRuntime" to "true")), VerificationProfile("default", tasks)))
        Files.createDirectories(root.resolve("mods")); val deployed = root.resolve("mods/fixture.jar")
        Files.copy(directory.resolve("fixture-input.jar"), deployed)
        val prior = Files.readAllBytes(deployed)
        val evidence = root.resolve("evidence").also { Files.createDirectories(it) }
        val staging = evidence.resolve("stage").also { Files.createDirectories(it) }
        assertThrows(IllegalArgumentException::class.java) {
            build(root, directory, evidence, staging, definition, SourceUpdate("fixture", "fixture.jar", head, head, "same"), false, true)
        }
        assertEquals(1, Files.readAllLines(source.resolve("invoked-tasks.txt")).size)
        assertFalse(Files.exists(evidence.resolve("source-profiles/fixture/default")))
        assertEquals("prior world", Files.readString(evidence.resolve("source-profiles/fixture/fails/prior-source-outputs/fixture/run-gametest/world/level.dat")))
        assertTrue(Files.readAllBytes(deployed).contentEquals(prior))
    }
}
