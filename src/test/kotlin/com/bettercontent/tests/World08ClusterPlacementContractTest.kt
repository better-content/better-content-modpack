package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class World08ClusterPlacementContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val mapper = jacksonObjectMapper()

    @Test
    fun adventure_roster_uses_shared_seed_stable_cluster_field_and_keeps_density_baseline() {
        val files = listOf("graveyard", "gorgon_temple", "mausoleum").map {
            root.resolve("kubejs/data/iceandfire/worldgen/structure_set/$it.json")
        }
        val sets = files.map { mapper.readTree(Files.readString(it)) }
        val placements = sets.map { it["placement"] }

        assertTrue(placements.all { it["type"].asText() == "settlement_roads:clustered_spread" })
        assertTrue(placements.all { it["cluster_spacing"].asInt() == 128 })
        assertTrue(placements.all { it["cluster_radius"].asInt() == 12 })
        assertTrue(placements.all { it["baseline_spacing"].asInt() == 4096 })
        assertEquals(3, placements.map { it["salt"].asInt() }.toSet().size)
        assertEquals(
            listOf("iceandfire:graveyard", "iceandfire:gorgon_temple", "iceandfire:mausoleum").toSet(),
            sets.map { it["structures"][0]["structure"].asText() }.toSet()
        )

        val placementSource = Files.readString(root.resolve("../mod_source/settlement-roads/src/main/java/com/bettercontent/settlementroads/worldgen/ClusteredSpreadStructurePlacement.java"))
        val registrySource = Files.readString(root.resolve("../mod_source/settlement-roads/src/main/kotlin/com/bettercontent/settlementroads/worldgen/SettlementRoadsWorldgen.kt"))
        val policySource = Files.readString(root.resolve("../mod_source/settlement-roads/src/main/kotlin/com/bettercontent/settlementroads/worldgen/ClusteredSpreadPolicy.kt"))
        assertTrue(placementSource.contains("state.getLevelSeed()"))
        assertTrue(placementSource.contains("CODEC = RecordCodecBuilder.create"))
        assertTrue(placementSource.contains("SITES_PER_CLUSTER = 2"))
        assertTrue(registrySource.contains("ClusteredSpreadStructurePlacement.CODEC"))
        assertTrue(policySource.contains("expectedSitesPerCell"))
        assertTrue(policySource.contains("weightSum"))
    }
}
