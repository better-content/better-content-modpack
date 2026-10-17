package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Duration

@Tag("fast")
class TconViewerHidingContractTest {
    @Test
    fun `both viewers hide only station assembled multipart tools`() {
        val root = Path.of(System.getProperty("bc.repo.root"))
        val output = BoundedCommand.capture(root, listOf(
            "node", root.resolve("src/test/resources/tcon-viewer-hiding-contract.cjs").toString(),
            root.resolve("kubejs/client_scripts/policy/hide_completed_tcon_tools.js").toString(),
        ), Duration.ofSeconds(15))
        assertTrue(output.contains("station/anvil multipart tools hidden"))
    }
}
