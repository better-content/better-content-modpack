package com.bettercontent.tests

import com.bettercontent.tests.release.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Synthetic owning-gate records exercise recipe plumbing, not provider/native acceptance. */
@Tag("fast")
class ProviderInputRecipesTest {
    @TempDir(cleanup = CleanupMode.NEVER) lateinit var temporary: Path
    private fun definition(name: String, dependencies: List<String> = emptyList(), support: List<String> = emptyList()) =
        ActiveMod(name, name.replace('-', '_'), "$name.jar", listOf("verifyFull"), dependencies, support)
    private fun output(mod: ActiveMod, mode: String = "rebuilt"): BuiltMod {
        val path = temporary.resolve("publication/${mod.artifact}")
        Files.createDirectories(path.parent)
        Files.writeString(path, "synthetic completed bytes: ${mod.repository}")
        val hashes = mod.supportArtifacts.associateWith { relative ->
            val support = temporary.resolve("mod_source/${mod.repository}/$relative")
            Files.createDirectories(support.parent)
            Files.writeString(support, "synthetic support: ${mod.repository}")
            Hashes.sha256(support)
        }
        return BuiltMod(mod, "a".repeat(40), path, Hashes.sha256(path), mode, hashes)
    }
    private fun manifest(vararg mods: ActiveMod) = ActiveModManifest("bc.active_custom_mods.v1", mods.toList())

    @Test fun entireTransitiveRuntimeAndSupportClosureIsFixedAndPathIndependent() {
        val ancestor = definition("ancestor", support = listOf("build/proof.json"))
        val parent = definition("parent", listOf("ancestor"))
        val consumer = definition("consumer", listOf("parent"))
        val definitions = manifest(consumer, ancestor, parent)
        val records = listOf(output(ancestor), output(parent)).associateBy { it.definition.repository }
        val first = providerInputView(definitions, consumer, records, temporary, temporary.resolve("views/first"))
        val second = providerInputView(definitions, consumer, records, temporary, temporary.resolve("views/second"))
        assertEquals(first.digest, second.digest, "private absolute paths must not invent different input bytes")
        for (provider in listOf(ancestor, parent)) {
            assertEquals(records.getValue(provider.repository).sha256,
                Hashes.sha256(first.directory.resolve(provider.artifact)))
            assertNotEquals(records.getValue(provider.repository).jar.toRealPath(),
                first.directory.resolve(provider.artifact).toRealPath())
        }
        assertEquals(records.getValue("ancestor").supportHashes.getValue("build/proof.json"),
            Hashes.sha256(first.directory.resolve("support/ancestor/build/proof.json")))
        Files.writeString(records.getValue("ancestor").jar, "unrelated later global publication mutation")
        Files.writeString(temporary.resolve("publication/unrelated.jar"), "partial or unrelated")
        ProviderInputs.verify(first)
        ProviderInputs.verify(second)
    }

    @Test fun incompleteAndDivergentCompletedRecordsCannotBeAdmitted() {
        val parent = definition("parent")
        val consumer = definition("consumer", listOf("parent"))
        val definitions = manifest(parent, consumer)
        assertThrows(IllegalArgumentException::class.java) {
            providerInputView(definitions, consumer, emptyMap(), temporary, temporary.resolve("missing/view"))
        }
        assertFalse(Files.exists(temporary.resolve("missing")))
        val item = output(parent)
        assertThrows(IllegalArgumentException::class.java) {
            providerInputView(definitions, consumer, mapOf("parent" to item.copy(definition = parent.copy(artifact = "other.jar"))),
                temporary, temporary.resolve("divergent/view"))
        }
        Files.writeString(item.jar, "changed since qualified record")
        assertThrows(IllegalArgumentException::class.java) {
            providerInputView(definitions, consumer, mapOf("parent" to item), temporary, temporary.resolve("mismatch/view"))
        }
    }

    @Test fun unqualifiedDiagnosticInputRequiresExplicitSeparatedMode() {
        val parent = definition("parent")
        val consumer = definition("consumer", listOf("parent"))
        val definitions = manifest(parent, consumer)
        val item = output(parent, "DEFERRED_UNQUALIFIED")
        assertThrows(IllegalArgumentException::class.java) {
            providerInputView(definitions, consumer, mapOf("parent" to item), temporary, temporary.resolve("qualified/view"))
        }
        val diagnostic = providerInputView(definitions, consumer, mapOf("parent" to item), temporary,
            temporary.resolve("diagnostic/view"), ProviderInputMode.EXPLICIT_UNTESTED)
        ProviderInputs.verify(diagnostic)
        assertEquals(ProviderInputMode.EXPLICIT_UNTESTED, diagnostic.mode)
        assertTrue(Files.readString(diagnostic.manifest).contains("\tfalse\t"))
    }

    @Test fun higherPrecedenceGradleProviderAliasesCannotEscapeTheView() {
        val compat = definition("better-compat-fixes")
        val notices = definition("better-gameplay-notices")
        val water = definition("better-drinking-water", listOf(compat.repository, notices.repository))
        val consumer = definition("consumer", listOf(water.repository))
        val definitions = manifest(compat, notices, water, consumer)
        val records = listOf(compat, notices, water).map(::output).associateBy { it.definition.repository }
        val view = providerInputView(definitions, consumer, records, temporary, temporary.resolve("properties/view"))
        assertEquals(mapOf("compatFixesRuntimeJar" to view.directory.resolve(compat.artifact).toString(),
            "gameplayNoticesRuntimeJar" to view.directory.resolve(notices.artifact).toString(),
            "drinkingWaterRuntimeJar" to view.directory.resolve(water.artifact).toString()), providerInputProperties(view))
        assertTrue(providerInputProperties(emptyProviderInputView(temporary.resolve("empty"),
            definition("leaf"), false)).isEmpty())
    }

    @Test fun cycleAndUnadmittedConsumerFailBeforeFilesystemWork() {
        val parent = definition("parent", listOf("consumer"))
        val consumer = definition("consumer", listOf("parent"))
        assertThrows(IllegalArgumentException::class.java) {
            providerInputView(manifest(parent, consumer), consumer, emptyMap(), temporary, temporary.resolve("cycle/view"))
        }
        assertFalse(Files.exists(temporary.resolve("cycle")))
        assertThrows(IllegalArgumentException::class.java) {
            providerInputView(manifest(consumer.copy(dependsOn = emptyList())), consumer, emptyMap(), temporary,
                temporary.resolve("unadmitted/view"))
        }
        assertFalse(Files.exists(temporary.resolve("unadmitted")))
    }
}
