package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Duration

@Tag("fast")
class TerrainRecoveryContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
    private val mapper = jacksonObjectMapper()

    @Test
    fun `production terrain handler preserves identity routes and rejects invalid catalogues`() {
        val result = mapper.readTree(BoundedCommand.capture(root, listOf(
            "node", root.resolve("src/test/resources/terrain-recovery-contract.cjs").toString(), root.toString()
        ), Duration.ofSeconds(15)))
        assertTrue(result.path("inputs").asInt() >= 200)
        assertTrue(result.path("routes").path("crushing").asInt() > 100)
        assertTrue(result.path("routes").path("sifting").asInt() > 50)
        assertTrue(result.path("negative_controls").asInt() >= 20)
        assertTrue(result.path("existing_washing_handler_executed").asBoolean())
    }

    @Test
    fun `pinned native terrain coverage chemistry consumers and conversion certificate remain valid`() {
        // The checker declares/revalidates every consumed Packwiz archive through
        // prepare_dev_inputs.ensure_pin, including on an empty generated/cache tree.
        // It never consumes a retained audit, runtime dump or golden snapshot.
        val result = mapper.readTree(BoundedCommand.capture(root, listOf(
            "python3", "-B", root.resolve("scripts/check_terrain_recovery.py").toString()
        ), Duration.ofMinutes(10)))
        assertTrue(result.path("conversion_transitions").asInt() > 500)
        assertTrue(result.path("compound_decisions").asInt() > 100)
        assertTrue(result.path("reviewed_exclusions").asInt() > 100)
        assertEquals("hash-pinned source resources; no Minecraft acceptance", result.path("native_scope").asText())
    }
}
