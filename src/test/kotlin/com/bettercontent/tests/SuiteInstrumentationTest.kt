package com.bettercontent.tests

import com.bettercontent.tests.suite.BoundFile
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SuiteInstrumentationTest {
    private fun support(): Path = Files.createTempFile(Path.of("/home/dev/.tmp"), "suite-instrumentation-", ".jar").also { jar ->
        ZipOutputStream(Files.newOutputStream(jar)).use { zip ->
            listOf("META-INF/mods.toml", "META-INF/bc-playtest-support.json").forEach { entry ->
                zip.putNextEntry(ZipEntry(entry)); zip.write("fixture".toByteArray()); zip.closeEntry()
            }
        }
    }
    private val required = listOf("-Dbc.playtest.directory=/home/dev/.tmp/native",
        "-Dbc.playtest.runId=epoch-123", "-Dbc.playtest.token=${"a".repeat(64)}")
    private val emi = "b".repeat(64)

    @Test fun requiredThreeAndDeclaredClientEmiProperty() {
        val jar = support()
        try {
            val hash = Hashes.sha256(jar)
            assertEquals(required, SuiteInstrumentation(jar, hash, required, false).javaArguments)
            assertEquals(required.size + 1, SuiteInstrumentation(jar, hash,
                required + "-Dbc.playtest.emiSha256=$emi", true, emi, clientInstrumentation = true).javaArguments.size)
            assertEquals(required.size, SuiteInstrumentation(jar, hash, required.reversed(), true).javaArguments.size)
        } finally { Files.deleteIfExists(jar) }
    }

    @Test fun fixtureOnlyPolicyOverlayPreservesUnknownNamespaceGuardAndCandidateHash() {
        val jar = support()
        val root = Files.createTempDirectory(Path.of("/home/dev/.tmp"), "suite-policy-")
        try {
            Files.createDirectories(root.resolve("mods"))
            val policy = root.resolve("kubejs/config/crafting_policy.json")
            Files.createDirectories(policy.parent)
            val original = """{"schema":"bc.crafting_policy.v1","namespaces":{"minecraft":{"primary_role":"content","support_state":"not_applicable"}},"enforcement":{"enabled":true}}"""
            Files.writeString(policy, original)
            val originalHash = Hashes.sha256(policy)
            val instrumented = SuiteInstrumentation(jar, Hashes.sha256(jar), required, false)
            val receipt = instrumented.install(root)
            assertEquals(originalHash, receipt["candidate_policy_sha256"])
            assertEquals(Hashes.sha256(policy), receipt["fixture_policy_sha256"])
            assertEquals("better_playtest_native_support", receipt["fixture_policy_overlay"])
            val mapper = jacksonObjectMapper()
            val before = mapper.readTree(original)
            val after = mapper.readTree(policy.toFile())
            assertEquals(before.path("enforcement"), after.path("enforcement"))
            assertEquals(before.path("namespaces").size() + 1, after.path("namespaces").size())
            assertEquals(before.path("namespaces").path("minecraft"), after.path("namespaces").path("minecraft"))
            assertTrue(after.path("namespaces").path("unclassified_mod").isMissingNode)
            assertEquals("infrastructure", after.path("namespaces").path("better_playtest_native_support")
                .path("primary_role").asText())
            assertEquals(null, instrumented.install(root, restored = true)["candidate_policy_sha256"])
            Files.writeString(policy, original)
            assertThrows(IllegalArgumentException::class.java) { instrumented.install(root, restored = true) }
            assertThrows(IllegalArgumentException::class.java) { instrumented.install(root) }
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            Files.deleteIfExists(jar)
        }
    }

    @Test fun serverOnlyImmutablePrelaunchScopePairDoesNotRelaxThreeMailboxProperties() {
        val jar = support()
        val root = Files.createTempDirectory(Path.of("/home/dev/.tmp"), "suite-scope-")
        val evidence = Files.createDirectory(root.resolve("evidence"))
        val scope = Files.writeString(evidence.resolve("target-mutation-scope.json"), "{}\n")
        try {
            val binding = BoundFile(scope.toString(), Hashes.sha256(scope))
            val instrumented = SuiteInstrumentation(jar, Hashes.sha256(jar), required, true,
                blockMutationScope = binding)
            assertEquals(required, instrumented.javaArguments)
            val text = instrumented.jvmText()
            assertTrue(text.contains("-Dbc.playtest.blockMutationScopeFile=${scope}"))
            assertTrue(text.contains("-Dbc.playtest.blockMutationScopeSha256=${binding.sha256}"))
            assertThrows(IllegalArgumentException::class.java) {
                SuiteInstrumentation(jar, Hashes.sha256(jar), required, true,
                    clientInstrumentation = true, blockMutationScope = binding)
            }
            assertThrows(IllegalArgumentException::class.java) {
                SuiteInstrumentation(jar, Hashes.sha256(jar), required +
                    "-Dbc.playtest.blockMutationScopeFile=$scope", true, blockMutationScope = binding)
            }
            Files.writeString(scope, "changed\n")
            assertThrows(IllegalArgumentException::class.java) { instrumented.jvmText() }
        } finally {
            Files.deleteIfExists(scope); Files.deleteIfExists(evidence); Files.deleteIfExists(root)
            Files.deleteIfExists(jar)
        }
    }

    @Test fun missingDuplicateUnknownMalformedOrUndeclaredPropertiesFailClosed() {
        val jar = support()
        try {
            val hash = Hashes.sha256(jar)
            val invalid = listOf(required.dropLast(1), required + required.last(), required + "-Dbc.playtest.unknown=x",
                required + "-Dbc.playtest.emiSha256=$emi", required + "-Dbc.playtest.emiSha256=${"c".repeat(64)}",
                required.dropLast(1) + "-Dbc.playtest.token=", required.dropLast(1) + "-Dbc.playtest.token",
                required.dropLast(1) + "-Dbc.playtest.token=not-a-sha", required.dropLast(1) + "-Dbc.playtest.token=${"a".repeat(64)}\n-Dx=y",
                required.dropLast(1) + "-Dbc.playtest.token=${"a".repeat(64)}\r-Dx=y")
            invalid.forEach { arguments ->
                assertThrows(IllegalArgumentException::class.java) { SuiteInstrumentation(jar, hash, arguments, false) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                SuiteInstrumentation(jar, hash, required + "-Dbc.playtest.emiSha256=${"c".repeat(64)}", true,
                    emi, clientInstrumentation = true)
            }
            assertThrows(IllegalArgumentException::class.java) {
                SuiteInstrumentation(jar, hash, required, true, emi, clientInstrumentation = true)
            }
            assertThrows(IllegalArgumentException::class.java) {
                SuiteInstrumentation(jar, hash, required + "-Dbc.playtest.emiSha256=$emi", false, emi, clientInstrumentation = true)
            }
            assertThrows(IllegalArgumentException::class.java) {
                SuiteInstrumentation(jar, hash, required + "-Dbc.playtest.emiSha256=$emi", true, emi) // server side cannot declare EMI
            }
        } finally { Files.deleteIfExists(jar) }
    }
}
