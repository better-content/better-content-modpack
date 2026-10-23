package com.bettercontent.tests

import com.bettercontent.tests.release.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class SourceVerificationControlTest {
    @TempDir lateinit var temporary: Path
    private val provider = ActiveMod("provider", "provider", "provider.jar", listOf("verifyFull", "stageRuntimeJar"))
    private val consumer = ActiveMod("consumer", "consumer", "consumer.jar", listOf("verifyFull", "stageRuntimeJar"), listOf("provider"))
    private val id = "20261023T120000Z-1"
    private fun manifest(vararg mods: ActiveMod) = ActiveModManifest("bc.active_custom_mods.v1", mods.toList())
    private fun git(repo: Path, vararg args: String): String {
        val process = ProcessBuilder(listOf("git", "-C", repo.toString()) + args).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { text }
        return text.trim()
    }
    private fun fixture(vararg names: String): Path {
        val root = temporary.resolve("pack")
        Files.createDirectories(root.resolve("gradle"))
        Files.writeString(root.resolve("gradle/active-custom-mods.json"), "synthetic inventory binding")
        Files.writeString(root.resolve("gradle/reflection-allowlist.txt"), "")
        names.forEach { name ->
            val repo = temporary.resolve("mod_source/$name")
            Files.createDirectories(repo)
            Files.writeString(repo.resolve(".gitignore"), "build/\n")
            Files.writeString(repo.resolve("input.txt"), "original\n")
            git(repo, "init", "-q")
            git(repo, "add", ".")
            git(repo, "-c", "user.name=Control", "-c", "user.email=control@example.invalid", "commit", "-qm", "control")
        }
        return root
    }
    private fun built(root: Path, staging: Path, mod: ActiveMod, update: SourceUpdate): BuiltMod {
        val jar = staging.resolve(mod.artifact)
        JarOutputStream(Files.newOutputStream(jar)).use {
            it.putNextEntry(JarEntry("META-INF/mods.toml"))
            it.write("[[mods]]\nmodId=\"${mod.modId}\"\n".toByteArray())
            it.closeEntry()
        }
        // Synthetic test seam only. Production must use common build, never this helper.
        annotateJar(jar, mod, update.localCommit)
        assertEquals(root.parent.resolve("mod_source/${mod.repository}").toRealPath(),
            temporary.resolve("mod_source/${mod.repository}").toRealPath())
        return BuiltMod(mod, update.localCommit, jar, Hashes.sha256(jar), "rebuilt")
    }
    @Test fun dependencyClosureIsOrderedAndProfilesRemainDefaultLast() {
        val profiles = listOf(VerificationProfile("native", listOf("verifyFull"), mapOf("native" to "true")),
            VerificationProfile("default", provider.tasks))
        val profiled = provider.copy(verificationProfiles = profiles)
        assertEquals(listOf(profiled, consumer), sourceVerificationClosure(manifest(consumer, profiled), listOf("consumer")))
        assertEquals(profiles, sourceVerificationClosure(manifest(profiled), listOf("provider")).single().verificationProfiles)
        assertThrows(IllegalArgumentException::class.java) { provider.copy(verificationProfiles = profiles.reversed()) }
        assertThrows(IllegalArgumentException::class.java) { sourceVerificationClosure(manifest(provider.copy(dependsOn = listOf("provider"))), listOf("provider")) }
        assertThrows(IllegalArgumentException::class.java) { sourceVerificationClosure(manifest(provider), listOf("missing")) }
        assertThrows(IllegalArgumentException::class.java) { sourceVerificationClosure(manifest(provider.copy(artifact = "../escape.jar")), listOf("provider")) }
    }
    @Test fun qualifiedStagesAndDependencyBindingsUseFreshClosureOnly() {
        val root = fixture("provider", "consumer")
        val calls = mutableListOf<String>()
        val result = verifySourceClosure(root, id, manifest(consumer, provider), listOf("consumer")) { r, _, _, s, m, u ->
            calls += m.repository
            built(r, s, m, u)
        }
        assertEquals(listOf("provider", "consumer"), calls)
        assertTrue(result.checks.values.all { it == "passed" })
        val receipt = Files.readString(root.resolve("generated/test-evidence/$id/source-verification/result.json"))
        assertTrue(receipt.contains(result.built.first().sha256))
        assertTrue(receipt.contains("PASSED_OWNING_GATES"))
        assertFalse(Files.exists(root.resolve("mods")))
        assertFalse(Files.exists(root.resolve("generated/verification-sessions")))
        assertThrows(java.nio.file.FileAlreadyExistsException::class.java) {
            verifySourceClosure(root, id, manifest(provider), listOf("provider")) { _, _, _, _, _, _ -> error("must not reuse") }
        }
    }
    @Test fun failedDependencyCannotConsumeHistoricalStage() {
        val root = fixture("provider", "consumer")
        Files.createDirectories(root.resolve("mods"))
        Files.writeString(root.resolve("mods/provider.jar"), "historical invalid bytes")
        val calls = mutableListOf<String>()
        val result = verifySourceClosure(root, id, manifest(provider, consumer), listOf("consumer")) { _, _, _, _, mod, _ ->
            calls += mod.repository
            error("owning gates failed")
        }
        assertEquals(listOf("provider"), calls)
        assertTrue(result.checks.getValue("provider").startsWith("failed"))
        assertEquals("blocked-by-provider", result.checks["consumer"])
        assertEquals("historical invalid bytes", Files.readString(root.resolve("mods/provider.jar")))
        assertTrue(result.built.isEmpty())
    }
    @Test fun dirtyAndMissingSourcesAreNotBuilt() {
        val root = fixture("provider")
        Files.writeString(temporary.resolve("mod_source/provider/input.txt"), "dirty")
        var calls = 0
        val result = verifySourceClosure(root, id, manifest(provider, consumer), listOf("consumer")) { _, _, _, _, _, _ ->
            calls++; error("not admitted")
        }
        assertEquals(0, calls)
        assertTrue(result.checks.getValue("provider").contains("dirty"))
        assertEquals("blocked-by-provider", result.checks["consumer"])
    }
    @Test fun symlinkPathsAreRejectedBeforeAdmission() {
        val root = fixture("provider")
        Files.createSymbolicLink(root.resolve("generated"), temporary)
        assertThrows(IllegalArgumentException::class.java) {
            verifySourceClosure(root, id, manifest(provider), listOf("provider")) { _, _, _, _, _, _ -> error("not admitted") }
        }
        val ordinary = temporary.resolve("mod_source/provider")
        Files.createSymbolicLink(temporary.resolve("linked-provider"), ordinary)
        assertThrows(IllegalArgumentException::class.java) { requireSourceClean(temporary.resolve("linked-provider")) }
        assertThrows(IllegalArgumentException::class.java) { requireSourceClean(temporary.resolve("missing")) }
    }
    @Test fun changedSourcesOrMissingOutputsNeverReceiveQualifiedReceipt() {
        val root = fixture("provider")
        val result = verifySourceClosure(root, id, manifest(provider), listOf("provider")) { r, _, _, s, m, u ->
            val item = built(r, s, m, u)
            Files.writeString(temporary.resolve("mod_source/provider/input.txt"), "changed inside owning gates")
            item
        }
        assertTrue(result.checks.getValue("provider").startsWith("failed"))
        assertTrue(result.built.isEmpty())
        val result2 = verifySourceClosure(root, "20261023T120001Z-1", manifest(provider), listOf("provider")) { _, _, _, _, _, _ -> error("dirty") }
        assertTrue(result2.checks.getValue("provider").startsWith("failed"))
    }
    @Test fun missingAndUnstampedOutputsAreRejected() {
        val root = fixture("provider")
        val result = verifySourceClosure(root, id, manifest(provider), listOf("provider")) { _, _, _, s, m, u ->
            BuiltMod(m, u.localCommit, s.resolve(m.artifact), "0".repeat(64), "rebuilt")
        }
        assertTrue(result.checks.getValue("provider").startsWith("failed"))
        val result2 = verifySourceClosure(root, "20261023T120001Z-1", manifest(provider), listOf("provider")) { r, _, _, s, m, u ->
            val item = built(r, s, m, u)
            // A later corruption cannot inherit the earlier owning-gate result/hash.
            Files.writeString(item.jar, "stale or corrupted stage")
            item
        }
        assertTrue(result2.checks.getValue("provider").startsWith("failed"))
    }
    private fun syntheticCommonBuild(root: Path, badLog: Boolean) {
        val repository = temporary.resolve("mod_source/provider")
        val input = temporary.resolve("synthetic-input.jar")
        JarOutputStream(Files.newOutputStream(input)).use {
            it.putNextEntry(JarEntry("META-INF/mods.toml"))
            it.write("[[mods]]\nmodId=\"provider\"\n".toByteArray())
            it.closeEntry()
        }
        val log = if (badLog) "[12:00:00] [Server thread/ERROR] [fixture/]: deliberate synthetic failure" else
            "[12:00:00] [Server thread/INFO] [fixture/]: synthetic source control"
        val wrapper = repository.resolve("gradlew")
        // This is a synthetic shell, NOT a Gradle/native invocation or an acceptance fixture.
        Files.writeString(wrapper, "#!/bin/sh\nprintf '%s\\n' \"\u0024*\" >> '${temporary.resolve("calls.log")}'\n" +
            "mkdir -p build/libs build/gametest/synthetic/logs\n" +
            "cp '$input' build/libs/provider.jar\n" +
            "printf '%s\\n' '$log' > build/gametest/synthetic/logs/latest.log\n")
        assertTrue(wrapper.toFile().setExecutable(true))
        git(repository, "add", "gradlew")
        git(repository, "-c", "user.name=Control", "-c", "user.email=control@example.invalid", "commit", "-qm", "synthetic wrapper")
        assertFalse(Files.exists(root.resolve("mods/provider.jar")))
    }
    @Test fun commonBackendRunsEveryProfileAuditsAndStampsDefaultLast() {
        val root = fixture("provider")
        syntheticCommonBuild(root, false)
        val profiled = provider.copy(verificationProfiles = listOf(
            VerificationProfile("native-profile", listOf("verifyFull"), mapOf("nativeProfile" to "true")),
            VerificationProfile("default", provider.tasks)))
        val result = verifySourceClosure(root, id, manifest(profiled), listOf("provider"))
        assertEquals(mapOf("provider" to "passed"), result.checks)
        assertEquals(listOf("--no-daemon clean -PnativeProfile=true verifyFull", "--no-daemon clean verifyFull stageRuntimeJar"),
            Files.readAllLines(temporary.resolve("calls.log")))
        assertEquals(requireSourceClean(temporary.resolve("mod_source/provider")), readSourceCommit(result.built.single().jar, profiled))
        assertTrue(Files.isRegularFile(root.resolve(".worklane/forge-cache.lock")))
        val reports = Files.walk(root.resolve("generated/test-evidence/$id/source-verification/source-profiles")).use { paths ->
            paths.filter { it.fileName.toString() == "report.json" }.toList()
        }
        assertEquals(2, reports.size)
        assertTrue(reports.all { Files.readString(it).contains("latest.log") })
    }
    @Test fun strictCommonNativeLogFailureCannotPublishStampOrQualifiedStage() {
        val root = fixture("provider")
        syntheticCommonBuild(root, true)
        val result = verifySourceClosure(root, id, manifest(provider), listOf("provider"))
        assertTrue(result.checks.getValue("provider").startsWith("failed"))
        assertTrue(result.built.isEmpty())
        assertFalse(Files.exists(root.resolve("generated/test-evidence/$id/source-jars/provider.jar")))
        assertFalse(Files.readString(root.resolve("generated/test-evidence/$id/source-verification/result.json")).contains("PASSED_OWNING_GATES"))
    }
    @Test fun lateProviderChangesInvalidateDependentQualification() {
        val root = fixture("provider", "consumer")
        val result = verifySourceClosure(root, id, manifest(provider, consumer), listOf("consumer")) { r, _, _, s, m, u ->
            val item = built(r, s, m, u)
            if (m == consumer) Files.writeString(s.resolve(provider.artifact), "corrupted after provider gate")
            item
        }
        assertTrue(result.checks.getValue("provider").startsWith("failed"))
        assertEquals("blocked-by-provider", result.checks["consumer"])
        assertFalse(Files.readString(root.resolve("generated/test-evidence/$id/source-verification/result.json")).contains("PASSED_OWNING_GATES"))
    }
    @Test fun unsafeBuildPathAndChangedInventoryCannotBeAdmitted() {
        val root = fixture("provider")
        Files.createSymbolicLink(temporary.resolve("mod_source/provider/build"), temporary)
        var called = false
        val result = verifySourceClosure(root, id, manifest(provider), listOf("provider")) { _, _, _, _, _, _ ->
            called = true; error("not admitted")
        }
        assertFalse(called)
        assertTrue(result.checks.getValue("provider").startsWith("failed"))
        assertThrows(IllegalArgumentException::class.java) {
            verifySourceClosure(root, "20261023T120001Z-1", manifest(provider), listOf("provider"), "0".repeat(64)) { _, _, _, _, _, _ ->
                error("changed inventory not admitted")
            }
        }
    }
    @Test fun productionDelegatesWithoutStandaloneAnnotationOrPackageSessionMutation() {
        val root = Path.of(System.getProperty("bc.repo.root", System.getProperty("user.dir")))
        val source = Files.readString(root.resolve("src/main/kotlin/com/bettercontent/tests/release/SourceVerification.kt"))
        assertTrue(source.contains("build(r, w, e, s, m, u, skipTests = false, forceRebuild = true)"))
        listOf("annotateJar(", "VerificationSession(", "prepareRelease(", "packageResolveCommand(", "REPLACE_EXISTING", "checkpoint(", "withForgeCacheLock(").forEach {
            assertFalse(source.contains(it), "unexpected alternate authority: $it")
        }
        val facade = Files.readString(root.resolve("test-control.main.kts"))
        assertTrue(facade.contains("qualifySourceControl"))
        assertFalse(facade.contains("Files.copy(jar, staged"))
        assertTrue(facade.indexOf("qualifySourceControl") < facade.indexOf("channel.lock().use"))
        assertTrue(facade.contains("names.filterNot { it in activeNames }"))
        val common = Files.readString(root.resolve("src/main/kotlin/com/bettercontent/tests/release/ReleasePipeline.kt"))
        listOf("mod.verificationProfiles", "auditSourceNativeLogs(", "annotateJar(staged", "supportArtifactHashes(repository, mod)").forEach {
            assertTrue(common.contains(it), "missing common owning gate: $it")
        }
    }
}
