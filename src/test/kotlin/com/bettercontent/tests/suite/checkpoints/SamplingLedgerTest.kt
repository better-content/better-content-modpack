package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

class SamplingLedgerTest {
    @TempDir lateinit var campaign: Path
    private val bindings = CheckpointBindings("campaign", UUID.randomUUID().toString(),
        mapOf("client_sha256" to "a".repeat(64), "server_sha256" to "b".repeat(64)),
        "c".repeat(64), "d".repeat(64))

    @Test fun `explicit empty ledger is valid and bound and cannot initialize twice`() {
        val ledger = SamplingLedger(campaign, bindings)
        assertThrows(IllegalArgumentException::class.java) { ledger.audit() }
        val initial = ledger.initialize()
        assertEquals(Hashes.sha256(Path.of(initial.path)), initial.sha256)
        assertTrue(ledger.verify().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { ledger.initialize() }
    }

    @Test fun `sites stay unique across branch ledger readers and threat attempts cap at three`() {
        val ledger = SamplingLedger(campaign, bindings)
        ledger.initialize()
        val first = ledger.claimSite("minecraft:overworld", 0, -4, "world.temperate")
        assertEquals("site", first.kind)
        val branch = SamplingLedger(campaign, bindings)
        assertThrows(IllegalArgumentException::class.java) { branch.claimSite("minecraft:overworld", 0, -4, "world.other") }
        assertEquals(2L, branch.claimSite("minecraft:nether", 0, -4, "world.other").sequence)
        for (attempt in 1..3) assertEquals(attempt, branch.claimThreatAttempt("threat.pillagers").attempt)
        assertThrows(IllegalArgumentException::class.java) { ledger.claimThreatAttempt("threat.pillagers") }
        assertEquals(listOf("site", "site", "threat_attempt", "threat_attempt", "threat_attempt"), ledger.verify().map { it.kind })
        assertEquals(5L, ledger.verify().last().sequence)
    }

    @Test fun `reserved footprints reject overlapping and edge-touching chunks across restored readers`() {
        val ledger = SamplingLedger(campaign, bindings)
        ledger.initialize()
        val first = ledger.claimSite("minecraft:overworld", 0, 0, "scale.56", 11)
        assertEquals(11, first.footprintRadiusChunks)
        val journal = Files.readString(campaign.resolve("sampling-ledger.jsonl"))
        assertEquals(11, suiteMapper.readTree(journal.trim()).path("footprint_radius_chunks").asInt())
        val restoredBranch = SamplingLedger(campaign, bindings)
        for ((x, z) in listOf(22 to 0, 0 to 22, 11 to 11, -22 to 0)) {
            assertThrows(IllegalArgumentException::class.java) {
                restoredBranch.claimSite("minecraft:overworld", x, z, "scale.other", 11)
            }
        }
        assertEquals(2L, restoredBranch.claimSite("minecraft:overworld", 23, 0, "scale.adjacent", 11).sequence)
        assertEquals(3L, restoredBranch.claimSite("minecraft:nether", 0, 0, "scale.other-dimension", 11).sequence)
        assertEquals(listOf(11, 11, 11), ledger.verify().map { it.footprintRadiusChunks })
    }

    @Test fun `zero radius preserves four argument calls while world edge and overflow fail closed`() {
        val ledger = SamplingLedger(campaign, bindings)
        ledger.initialize()
        assertEquals(0, ledger.claimSite("minecraft:overworld", 1_874_999, -1_874_999, "world.edge").footprintRadiusChunks)
        for ((x, z, radius) in listOf(
            Triple(Int.MAX_VALUE, 0, 1), Triple(Int.MIN_VALUE, 0, 1),
            Triple(1_874_999, 0, 1), Triple(0, -1_874_999, 1),
            Triple(0, 0, -1), Triple(0, 0, 33)
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                ledger.claimSite("minecraft:overworld", x, z, "world.invalid", radius)
            }
        }
        assertEquals(1, ledger.verify().size)
    }

    @Test fun `new branch cannot roll back a footprint or attempt budget by recreating its reader`() {
        SamplingLedger(campaign, bindings).initialize()
        SamplingLedger(campaign, bindings).claimSite("minecraft:overworld", 500, -500, "scale.first", 11)
        repeat(3) { SamplingLedger(campaign, bindings).claimThreatAttempt("threat.same") }
        assertThrows(IllegalArgumentException::class.java) {
            SamplingLedger(campaign, bindings).claimSite("minecraft:overworld", 522, -500, "scale.overlaps", 11)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SamplingLedger(campaign, bindings).claimThreatAttempt("threat.same")
        }
        assertEquals(4, SamplingLedger(campaign, bindings).verify().size)
    }

    @Test fun `pre-footprint v1 rows retain their old hash while reserving one center chunk`() {
        val ledger = SamplingLedger(campaign, bindings)
        ledger.initialize()
        ledger.claimSite("minecraft:overworld", 9, 9, "world.old")
        val journal = campaign.resolve("sampling-ledger.jsonl")
        val previousShape = suiteMapper.readTree(Files.readString(journal).trim()) as ObjectNode
        previousShape.remove("footprint_radius_chunks")
        val withoutHash = previousShape.deepCopy().put("sha256", "")
        val previousDigest = MessageDigest.getInstance("SHA-256")
            .digest(suiteMapper.writeValueAsBytes(withoutHash)).joinToString("") { "%02x".format(it) }
        previousShape.put("sha256", previousDigest)
        Files.writeString(journal, suiteMapper.writeValueAsString(previousShape) + "\n")
        Files.writeString(campaign.resolve("sampling-ledger-anchors/000000000001.sha256"), "$previousDigest\n")
        assertEquals(0, ledger.verify().single().footprintRadiusChunks)
        assertThrows(IllegalArgumentException::class.java) {
            SamplingLedger(campaign, bindings).claimSite("minecraft:overworld", 9, 9, "world.new", 1)
        }
        assertEquals(2L, ledger.claimSite("minecraft:overworld", 11, 9, "world.adjacent", 1).sequence)
    }

    @Test fun `journal change truncation anchor deletion or candidate mismatch fails closed`() {
        val ledger = SamplingLedger(campaign, bindings)
        ledger.initialize()
        ledger.claimSite("minecraft:overworld", 3, 8, "world.temperate")
        val journal = campaign.resolve("sampling-ledger.jsonl")
        val raw = Files.readString(journal)
        Files.writeString(journal, raw.replace("minecraft:overworld", "minecraft:the_end"))
        assertThrows(IllegalArgumentException::class.java) { ledger.verify() }
        Files.writeString(journal, raw)
        assertThrows(IllegalArgumentException::class.java) {
            SamplingLedger(campaign, bindings.copy(configurationSha256 = "e".repeat(64))).verify()
        }
        val anchor = campaign.resolve("sampling-ledger-anchors/000000000001.sha256")
        val originalAnchor = Files.readAllBytes(anchor)
        Files.delete(anchor)
        assertThrows(IllegalArgumentException::class.java) { ledger.verify() }
        // Isolate journal truncation from anchor deletion. Simultaneous erasure of every
        // historical commitment is indistinguishable from initialization without external evidence.
        Files.write(anchor, originalAnchor)
        assertEquals(1, ledger.verify().size)
        Files.writeString(journal, "")
        assertThrows(IllegalArgumentException::class.java) { ledger.verify() }
    }

    @Test fun `missing ledger cannot restart counters or erase sites`() {
        val ledger = SamplingLedger(campaign, bindings)
        ledger.initialize()
        ledger.claimThreatAttempt("threat.depth-director")
        Files.delete(campaign.resolve("sampling-ledger.jsonl"))
        assertThrows(IllegalArgumentException::class.java) { ledger.verify() }
        assertThrows(IllegalArgumentException::class.java) { ledger.claimThreatAttempt("threat.depth-director") }
        assertThrows(IllegalArgumentException::class.java) { ledger.initialize() }
    }
}
