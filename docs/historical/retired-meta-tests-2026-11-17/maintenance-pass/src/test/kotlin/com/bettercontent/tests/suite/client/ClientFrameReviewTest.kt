package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

/** Negative contract only: no simulated human approval, screenshot, or client session is evidence. */
class ClientFrameReviewTest {
    @Test fun pendingTemplateCannotApproveAnyFramebuffer() {
        val frame = CapturedClientFrame("epoch-00001", "gui.case", "single", 1,
            "1280x720@2", "a".repeat(64), "net.minecraft.client.gui.screens.inventory.InventoryScreen",
            "net.minecraft.world.inventory.InventoryMenu", "minecraft:overworld", "00000000-0000-0000-0000-000000000001")
        val pending = suiteMapper.createObjectNode().apply {
            put("schema", "bc.playtest_client_frame_review.v1")
            put("status", "pending")
        }
        assertThrows(IllegalArgumentException::class.java) {
            verifyClientFrameReview(pending, listOf(frame), "campaign-not-real", "lineage-not-real", "b".repeat(64),
                mapOf("client_sha256" to "c".repeat(64), "server_sha256" to "d".repeat(64)),
                "author", Instant.EPOCH, setOf("minecraft:inventory"), setOf("1280x720@2", "1920x1080@3"))
        }
    }
}
