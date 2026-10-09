package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@Tag("fast")
class RetirementAuditTest {
    @Test fun storageDriverTargetsPropertiesAndStagingAreAbsent() {
        for(name in listOf("JournalContract.kt","JournalFixturePolicy.kt"))assertFalse(Files.exists(Path.of("src/main/kotlin/com/bettercontent/tests/$name")))
        for(name in listOf("JournalFixtureSupport.kt","JournalContractTest.kt"))assertFalse(Files.exists(Path.of("src/test/kotlin/com/bettercontent/tests/$name")))
        for(name in listOf("test.main.kts","release.main.kts","src/test/kotlin/com/bettercontent/tests/RuntimeFixtures.kt",
            "src/test/kotlin/com/bettercontent/tests/MultiplayerRuntimeTest.kt","src/test/kotlin/com/bettercontent/tests/SingleplayerRuntimeTest.kt")) {
            val s=Files.readString(Path.of(name))
            for(token in listOf("native-inventory","nativeinventorycontract","nativeinventorycheckpoint","bc.native.inventory","bc.journal.contract",
                "enableJournalContract","JournalContractValidator","JournalFixtureSupport","waitJournalCheckpoint","disconnectDedicated","setInvulnerable"))assertFalse(s.contains(token),"$name: $token")
        }
    }
    @Test fun diagnosticClassificationAddsOnlyAnInfrastructureNamespaceAndKeepsBlocking() {
        val mapper=jacksonObjectMapper();val original=Files.readString(Path.of("kubejs/config/crafting_policy.json"))
        val before=mapper.readTree(original);val after=mapper.readTree(RuntimeDiagnosticPolicy.classify(original))
        assertEquals("blocking",after.path("enforcement").path("unknown_loaded_namespaces").asText())
        assertEquals("infrastructure",after.path("namespaces").path(RuntimeDiagnosticPolicy.NAMESPACE).path("primary_role").asText())
        (after.path("namespaces") as com.fasterxml.jackson.databind.node.ObjectNode).remove(RuntimeDiagnosticPolicy.NAMESPACE)
        assertEquals(before,after)
        assertThrows(Exception::class.java) { RuntimeDiagnosticPolicy.classify(RuntimeDiagnosticPolicy.classify(original)) }
        assertThrows(Exception::class.java) { RuntimeDiagnosticPolicy.classify(original.replace("\"blocking\"","\"ignored\"")) }
    }
    private fun nested(name:String,bytes:ByteArray):ByteArray {
        val out=ByteArrayOutputStream();ZipOutputStream(out).use { it.putNextEntry(ZipEntry(name));it.write(bytes);it.closeEntry() };return out.toByteArray()
    }
    @Test fun renamedStorageAndDiagnosticSupplementsCannotEnterCandidates() {
        val root=Path.of("build/retirement-audit",UUID.randomUUID().toString());Files.createDirectories(root)
        try {
            for(id in listOf("better_journal_test_support","better_native_inventory_test_support","better_runtime_test_support")) {
                val file=root.resolve("$id.zip");Files.write(file,nested("mods/renamed.jar",nested("META-INF/mods.toml","modId=\"$id\"".toByteArray())))
                assertThrows(Exception::class.java) { RetiredInventoryArtifactExclusion.validate(file) }
            }
            for(prefix in listOf("journaltestsupport","nativeinventorytestsupport","runtimetestsupport")) {
                val file=root.resolve("$prefix.zip");Files.write(file,nested("mods/renamed.jar",nested("com/bettercontent/$prefix/Hidden.class",byteArrayOf(1))))
                assertThrows(Exception::class.java) { RetiredInventoryArtifactExclusion.validate(file) }
            }
            val good=root.resolve("production.zip");Files.write(good,nested("mods/native.jar",nested("META-INF/mods.toml","modId=\"native\"".toByteArray())))
            RetiredInventoryArtifactExclusion.validate(good)
        }finally { root.toFile().deleteRecursively() }
    }
    @Test fun diagnosticsAreSeparatedFromStorageWithoutRelaxingOrdinaryAudits() {
        val s=Files.readString(Path.of("src/test/kotlin/com/bettercontent/tests/RuntimeDiagnosticSupport.kt"))
        assertTrue(s.contains("mod_source/better-runtime-diagnostics/"));assertTrue(s.contains("bc.pack_test.diagnostics."))
        for(token in listOf("nativeinventory","JournalContract","setStackInSlot","addRecipe","setInvulnerable"))assertFalse(s.contains(token),token)
        val mp=Files.readString(Path.of("src/test/kotlin/com/bettercontent/tests/MultiplayerRuntimeTest.kt"))
        assertTrue(mp.contains("requirePlayersOnline"));assertTrue(mp.contains("server.auditLogs"));assertTrue(mp.contains("server.assertHashes"))
    }
}
