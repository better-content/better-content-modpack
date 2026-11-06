package com.bettercontent.tests

import com.bettercontent.tests.release.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class ReleaseTierSelectionTest {
    @TempDir lateinit var directory: Path
    private val mapper = jacksonObjectMapper()

    @Test fun defaultAndEachOrderedCombinationAreExplicitAndObsoleteSoakIsRejected() {
        assertEquals(emptyList<String>(), ReleaseTierSelection.parse("none", 0).tiers)
        assertEquals(listOf("smoke"), ReleaseTierSelection.parse("smoke", 0).tiers)
        assertEquals(listOf("overworld-tele-soak", "tele-soak"), ReleaseTierSelection.parse("overworld-tele-soak,tele-soak", 0).tiers)
        assertEquals(listOf("smoke", "overworld-tele-soak", "tele-soak"), ReleaseTierSelection.parse("smoke,overworld-tele-soak,tele-soak", 0).tiers)
        listOf("", "none,smoke", "smoke,smoke", "tele-soak,smoke", "skip", "soak,").forEach {
            assertThrows(IllegalArgumentException::class.java) { ReleaseTierSelection.parse(it, 0) }
        }
        assertThrows(IllegalArgumentException::class.java) { ReleaseTierSelection.parse("soak", 0) }
        assertThrows(IllegalArgumentException::class.java) { ReleaseTierSelection.parse("smoke", 30) }
        assertThrows(IllegalArgumentException::class.java) { ReleaseTierSelection.parse("overworld-tele-soak", 60) }
    }

    private fun pair(): CandidatePair {
        val client = Files.writeString(Files.createDirectories(directory.resolve("client")).resolve("better-content.zip"), "client")
        val server = Files.writeString(Files.createDirectories(directory.resolve("server")).resolve("better-content.zip"), "server")
        return CandidatePair(directory, client, server, Hashes.sha256(client), Hashes.sha256(server))
    }

    @Test fun packageOnlyNeverBecomesSmokeReady() {
        val pair = pair()
        val provenance = Files.writeString(directory.resolve("provenance.json"), "qualified")
        val inventory = Files.writeString(directory.resolve("archive-members.json"), "inventory")
        val none = ReleaseTierSelection.parse("none", 0)
        ReleaseMarkers.packageReady(pair, "new-run", provenance, inventory, none)
        ReleaseMarkers.result(directory, "new-run", none, emptyMap(), null)
        val marker = mapper.readTree(directory.resolve("package-ready.json").toFile())
        assertEquals("packaged", marker.path("status").asText())
        assertEquals("not_run", marker.path("tiers").path("visual").asText())
        assertFalse(Files.exists(directory.resolve("ready.json")))
        assertThrows(IllegalArgumentException::class.java) { ReleaseMarkers.packageReady(pair, "new-run", provenance, inventory, none) }
    }

    @Test fun smokeOnlyReadyRecordsExactReceiptWithoutClaimingOtherTiers() {
        val pair = pair()
        val provenance = Files.writeString(directory.resolve("provenance.json"), "qualified")
        val inventory = Files.writeString(directory.resolve("archive-members.json"), "inventory")
        val smoke = ReleaseTierSelection.parse("smoke", 0)
        ReleaseMarkers.packageReady(pair, "new-run", provenance, inventory, smoke)
        val receipt = Files.writeString(directory.resolve("smoke.json"), "passed")
        ReleaseMarkers.ready(pair, "new-run", smoke, mapOf("smoke" to receipt))
        val ready = mapper.readTree(directory.resolve("ready.json").toFile())
        assertEquals(Hashes.sha256(receipt), ready.path("tiers").path("smoke").path("receipt").path("sha256").asText())
        assertEquals("not_run", ready.path("tiers").path("overworld-tele-soak").path("status").asText())
        assertEquals("not_run", ready.path("tiers").path("tele-soak").path("status").asText())
        assertEquals("not_run", ready.path("tiers").path("visual").path("status").asText())
        assertThrows(IllegalArgumentException::class.java) { ReleaseMarkers.ready(pair, "new-run", smoke, mapOf("smoke" to receipt)) }
    }

    @Test fun standaloneVisualBindingRejectsOldProofsChangedPairAndWrongMarker() {
        val pair = pair()
        val evidence = Files.createDirectories(directory.resolve("generated/test-evidence/new-run/release"))
        val provenance = evidence.resolve("provenance.json")
        val inventory = evidence.resolve("archive-members.json")
        listOf(provenance, inventory).forEach { mapper.writeValue(it.toFile(), mapOf("candidates" to pair.document())) }
        val marker = directory.resolve("package-ready.json")
        ReleaseMarkers.packageReady(pair, "new-run", provenance, inventory, ReleaseTierSelection.parse("none", 0))
        assertEquals(pair.document(), validateVisualPackageBinding(directory, marker).first.document())
        Files.writeString(inventory, "stale source/inventory proof")
        assertThrows(IllegalArgumentException::class.java) { validateVisualPackageBinding(directory, marker) }
        mapper.writeValue(inventory.toFile(), mapOf("candidates" to pair.document()))
        Files.writeString(pair.client, "changed candidate")
        assertThrows(IllegalArgumentException::class.java) { validateVisualPackageBinding(directory, marker) }
        assertFalse(Files.exists(evidence.parent.resolve("visual/receipt.json")))
    }

    @Test fun failedOrMissingRequestedTierAndChangedZipCannotCreateReady() {
        val pair = pair()
        val source = Files.writeString(directory.resolve("provenance.json"), "qualified")
        val inventory = Files.writeString(directory.resolve("archive-members.json"), "inventory")
        val selected = ReleaseTierSelection.parse("smoke,overworld-tele-soak", 0)
        ReleaseMarkers.packageReady(pair, "new-run", source, inventory, selected)
        val smoke = Files.writeString(directory.resolve("smoke.json"), "passed")
        Files.writeString(source, "mutated provenance")
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseMarkers.ready(pair, "new-run", selected, mapOf("smoke" to smoke, "overworld-tele-soak" to
                Files.writeString(directory.resolve("overworld-tele-soak.json"), "passed")))
        }
        Files.writeString(source, "qualified")
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseMarkers.ready(pair, "new-run", selected, mapOf("smoke" to smoke))
        }
        assertFalse(Files.exists(directory.resolve("ready.json")))
        val overworld = Files.writeString(directory.resolve("overworld-tele-soak.json"), "passed")
        Files.writeString(pair.client, "mutated")
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseMarkers.ready(pair, "new-run", selected, mapOf("smoke" to smoke, "overworld-tele-soak" to overworld))
        }
        assertFalse(Files.exists(directory.resolve("ready.json")))
    }
}
