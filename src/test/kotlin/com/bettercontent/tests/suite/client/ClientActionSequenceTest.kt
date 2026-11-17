package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.drivers.DriverAction
import com.bettercontent.tests.suite.drivers.DriverStep
import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Keep action ordering/release regressions; runtime identity certificates are not gameplay tests. */
class ClientActionSequenceTest {
    @Test fun `passive observer arms before input and onboarding awaits after bounded release`() {
        fun server(id: String) = DriverStep(id, DriverAction(id, suiteMapper.createObjectNode()))
        fun input(id: String, kind: String = "onboarding") = DriverStep(id, DriverAction(id,
            suiteMapper.createObjectNode().put("hold_id", "onboard-1").put("kind", kind), source = "client"))
        val correct = listOf(server("player.observe_onboarding.arm"), input("client.input_hold"),
            input("client.input_release"), input("client.input_status"), server("player.observe_onboarding.await"))
        requireReviewedInputObserverSequence(correct)
        assertThrows(IllegalArgumentException::class.java) {
            requireReviewedInputObserverSequence(listOf(input("client.input_hold"),
                server("player.observe_onboarding.arm")) + correct.drop(2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireReviewedInputObserverSequence(correct.dropLast(1).toMutableList().apply {
                add(2, server("player.observe_onboarding.await"))
            })
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireReviewedInputObserverSequence(listOf(server("player.observe_item_use.arm"),
                input("client.input_hold", "use"), server("player.use_item"),
                input("client.input_release"), input("client.input_status"),
                server("player.observe_item_use.await")))
        }
    }

    @Test fun `physical W walking requires registered read-only route and immediate wait release status brackets`() {
        fun server(id: String) = DriverStep(id, DriverAction(id, suiteMapper.createObjectNode()))
        val hold = DriverStep("hold", DriverAction("client.input_hold", suiteMapper.createObjectNode()
            .put("kind", "walk_forward").put("hold_id", "leg0").put("max_ticks", 30)
            .put("profile", "1280x720@2"), source = "client"))
        fun input(id: String) = DriverStep(id, DriverAction(id,
            suiteMapper.createObjectNode().put("hold_id", "leg0"), source = "client"))
        val correct = listOf(server("block.snapshot"), server("player.snapshot"), hold,
            server("wait.ticks"), input("client.input_release"), input("client.input_status"),
            server("player.snapshot"), server("block.snapshot"))
        requireReviewedInputObserverSequence(correct, "control.travel.walk-route")
        assertThrows(IllegalArgumentException::class.java) {
            requireReviewedInputObserverSequence(correct, "primitive.harvest")
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireReviewedInputObserverSequence(correct.toMutableList().apply { add(3, server("player.teleport")) },
                "control.travel.walk-route")
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireReviewedInputObserverSequence(correct.toMutableList().apply { removeAt(3) },
                "control.travel.walk-route")
        }
    }

    @Test fun `one-profile inventory GUI source uses physical inventory key rather than direct callback injection`() {
        fun action(id: String, source: String = "server", parameters: com.fasterxml.jackson.databind.node.ObjectNode =
            suiteMapper.createObjectNode()) = DriverStep(id, DriverAction(id, parameters, source = source))
        val inventory = suiteMapper.createObjectNode().put("kind", "inventory")
            .put("hold_id", "gui-open").put("max_ticks", 6).put("profile", "1280x720@2")
        val held = suiteMapper.createObjectNode().put("hold_id", "gui-open")
        val correct = listOf(action("player.snapshot"), action("client.snapshot", "client"),
            action("client.input_hold", "client", inventory),
            action("client.input_release", "client", held), action("client.input_status", "client", held),
            action("client.snapshot", "client"),
            action("client.framebuffer", "client", suiteMapper.createObjectNode().put("profile", "1280x720@2")),
            action("player.snapshot"))
        requireReviewedInputObserverSequence(correct, "control.client.inventory-screen")
        assertThrows(IllegalArgumentException::class.java) {
            requireReviewedInputObserverSequence(correct, "survival.food")
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireReviewedInputObserverSequence(correct.toMutableList().apply {
                this[3] = action("client.input_key", "client")
            }, "control.client.inventory-screen")
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireReviewedInputObserverSequence(correct.toMutableList().apply {
                this[6] = action("client.framebuffer", "client",
                    suiteMapper.createObjectNode().put("profile", "1920x1080@3"))
            }, "control.client.inventory-screen")
        }
    }
}
