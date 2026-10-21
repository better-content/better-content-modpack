package com.bettercontent.tests

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag

/** Synthetic oracle controls, not native species, particle or framebuffer acceptance. */
@Tag("fast")
class NativeBeeColorReceiptTest {
    private val mapper = jacksonObjectMapper()
    private fun server(state: String, species: String, rgb: Int): ObjectNode {
        val positive = rgb >= 0
        val queen = state == "active-apiary"
        return mapper.valueToTree(mapOf(
            "state" to state, "ready" to true, "appearance_ready" to true,
            "requested_species_id" to species, "native_species_id" to species,
            "native_body_rgb" to rgb, "native_nest_rgb" to if (species.endsWith(":plains")) 0xC92D02 else rgb,
            "native_occupied" to positive, "cosmetic_active" to positive,
            "cosmetic_color_known" to positive, "cosmetic_body_rgb" to if (positive) rgb else 0,
            "shared_species_climate" to true, "native_temperature" to "NORMAL", "native_humidity" to "NORMAL",
            "native_queen_present" to queen, "native_queen_satisfied" to queen,
            "native_genome_oracle_checked" to queen, "native_genome_oracle_unchanged" to queen,
            "native_output_empty" to true, "native_dandelion_present" to true))
    }
    private fun client(positive: Boolean): ObjectNode = mapper.valueToTree(mapOf(
        "state" to if (positive) "active-apiary" else "inactive-apiary", "control" to "normal", "ready" to true,
        "native_expected_species_id" to if (positive) "complicated_bees:plains" else "invalid",
        "native_expected_rgb" to if (positive) 0xFC030F else -1,
        "native_occupied" to positive, "native_color_agreement" to true, "native_chunk_loaded" to true,
        "cosmetic_color_known" to positive, "cosmetic_body_rgb" to if (positive) 0xFC030F else 0,
        "native_ticks" to 100, "engine_creations_legacy" to 0,
        "engine_creations_tinted_v1" to if (positive) 8 else 0, "engine_creations" to if (positive) 8 else 0,
        "engine_submissions" to if (positive) 8 else 0, "emission_requests" to if (positive) 8 else 0,
        "visual_verdict" to "PENDING_LLM_FRAMEBUFFER_REVIEW"))

    @Test fun acceptsTheTwoNativeSpeciesAndInvalidationShapes() {
        for (state in listOf("occupied-nest", "active-apiary"))
            for ((species, rgb) in listOf("complicated_bees:forest" to 0x03FCE8, "complicated_bees:plains" to 0xFC030F))
                NativeBeeColorReceipt.server(server(state, species, rgb), state, species, rgb)
        for (state in listOf("empty-nest", "inactive-apiary"))
            NativeBeeColorReceipt.server(server(state, "invalid", -1), state, "invalid", -1)
        NativeBeeColorReceipt.client(client(true), "active-apiary", "normal", "complicated_bees:plains", 0xFC030F)
        NativeBeeColorReceipt.client(client(false), "inactive-apiary", "normal", "invalid", -1)
    }

    @Test fun rejectsCountsAndReadyWithoutIndependentNativeAuthority() {
        val mutations: List<(ObjectNode) -> Unit> = listOf(
            { it.remove("native_body_rgb") }, { it.put("native_body_rgb", 0xC92D02) },
            { it.put("native_species_id", "complicated_bees:forest") }, { it.put("cosmetic_body_rgb", 0xC92D02) },
            { it.put("native_nest_rgb", 0xFC030F) }, { it.put("native_queen_satisfied", false) },
            { it.put("native_genome_oracle_checked", false) }, { it.put("native_genome_oracle_unchanged", false) },
            { it.put("native_output_empty", false) }, { it.put("appearance_ready", false) },
            { it.put("shared_species_climate", false) }, { it.put("native_queen_present", "true") })
        for (mutate in mutations) {
            val row = server("active-apiary", "complicated_bees:plains", 0xFC030F).also(mutate)
            assertThrows(IllegalArgumentException::class.java) {
                NativeBeeColorReceipt.server(row, "active-apiary", "complicated_bees:plains", 0xFC030F)
            }
        }
    }

    @Test fun rejectsLegacyOnlyWrongColorEarlyOrOverflowingClientWindows() {
        val mutations: List<(ObjectNode) -> Unit> = listOf(
            { it.put("engine_creations_legacy", 8); it.put("engine_creations_tinted_v1", 0) },
            { it.put("engine_creations_tinted_v1", 0); it.put("engine_creations", 0) },
            { it.put("emission_requests", 89) }, { it.put("native_ticks", 99) },
            { it.put("native_color_agreement", false) }, { it.put("cosmetic_body_rgb", 0xC92D02) },
            { it.remove("native_expected_rgb") }, { it.put("engine_submissions", 0) },
            { it.put("visual_verdict", "PASS") })
        for (mutate in mutations) {
            val row = client(true).also(mutate)
            assertThrows(IllegalArgumentException::class.java) {
                NativeBeeColorReceipt.client(row, "active-apiary", "normal", "complicated_bees:plains", 0xFC030F)
            }
        }
    }

    @Test fun invalidationCannotKeepKnownTintOrParticles() {
        val staleServer = server("inactive-apiary", "invalid", -1).put("cosmetic_color_known", true)
        assertThrows(IllegalArgumentException::class.java) {
            NativeBeeColorReceipt.server(staleServer, "inactive-apiary", "invalid", -1)
        }
        for (field in listOf("engine_creations", "engine_creations_tinted_v1", "engine_submissions", "emission_requests", "cosmetic_body_rgb")) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeBeeColorReceipt.client(client(false).put(field, 1), "inactive-apiary", "normal", "invalid", -1)
            }
        }
    }
}
