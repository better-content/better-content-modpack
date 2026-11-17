package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Same checked-in wire fixture B asserts against its production Gson serializer; NOT a 600s performance fixture. */
class ScaleWireContractConsumerTest {
    private fun fixture() = suiteMapper.readTree(Files.readAllBytes(
        Path.of("test-support/forge/src/test/resources/scale-wire-contract-v2.json")))
    @Test fun `native production receipt and sample clock sections reach Kotlin structural oracle`() {
        val doc = fixture()
        val receipt = doc.path("receipt")
        val sample = doc.path("sample")
        assertTrue(scaleReceiptClockConforms(receipt))
        assertEquals(2, receipt.path("scale_contract_version").asInt())
        assertEquals(receipt.path("last_sample_wall_nanos"), sample.path("monotonic_nanos"))
        assertEquals(receipt.path("last_sample_tick"), sample.path("tick"))
        assertEquals(receipt.path("start_wall_nanos"), sample.path("start_wall_nanos"))
        assertEquals(receipt.path("setup_completed_wall_nanos"), sample.path("setup_completed_wall_nanos"))
        assertEquals((sample.path("monotonic_nanos").asLong() - sample.path("setup_completed_wall_nanos").asLong()) / 1_000_000L,
            sample.path("observation_wall_millis").asLong())
        val broken = receipt.deepCopy<ObjectNode>().put("terminal_partial_interval_nanos", 999_999L)
        assertFalse(scaleReceiptClockConforms(broken))
        val obsolete = receipt.deepCopy<ObjectNode>().put("scale_contract_version", 1)
        assertFalse(scaleReceiptClockConforms(obsolete))
    }
}
