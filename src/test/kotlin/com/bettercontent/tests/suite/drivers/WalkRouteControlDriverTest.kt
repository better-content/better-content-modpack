package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.jsonFingerprint
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Authored synthetic fixtures only; NO test, GUI, Robot, Minecraft, or world has been run. */
class WalkRouteControlDriverTest {
    private val actor = "11111111-1111-1111-1111-111111111111"
    private val sha = "a".repeat(64)
    private fun cell(x: Int, y: Int, z: Int) = suiteMapper.createObjectNode()
        .put("x", x).put("y", y).put("z", z)
    private fun target(): ObjectNode {
        val route = suiteMapper.createObjectNode().apply {
            set<JsonNode>("start", cell(0, 65, 0))
            set<JsonNode>("legs", suiteMapper.createArrayNode().add(suiteMapper.createObjectNode().apply {
                set<JsonNode>("target", cell(0, 65, 1)); put("wait_ticks", 4); put("hold_id", "walk-1")
            }))
        }
        return suiteMapper.createObjectNode().apply {
            put("player_uuid", actor); put("dimension", "minecraft:overworld")
            put("input_profile", "1280x720@2"); put("route_id", "reviewed-route-one")
            put("route_sha256", jsonFingerprint(route)); set<JsonNode>("route", route)
        }
    }
    private fun sample(tick: Int, z: Double) = suiteMapper.createObjectNode().apply {
        put("native_tick", tick); put("x", 0.5); put("y", 65.0); put("z", z); put("yaw", 0.0)
        put("on_ground", true)
        listOf("swimming", "in_fluid", "passenger", "sprinting", "crouching", "flying", "jump_down",
            "strafe_down", "back_down", "interact_down").forEach { put(it, false) }
    }
    private fun client(z: Double, held: Int, samples: List<JsonNode>): ObjectNode = suiteMapper.createObjectNode().apply {
        put("evidence_role", "authentic_robot_os_input_NOT_direct_callback_injection")
        put("kind", "walk_forward"); put("walk_support_scope", "ordinary_flat_ground_forward_only")
        put("player_uuid", actor); put("dimension", "minecraft:overworld"); put("profile", "1280x720@2")
        put("hold_id", "walk-1"); put("input_runtime_sha256", sha); put("held_max_ticks", 24)
        put("client_namespace_pid", 31); put("controller_claim_host_xvfb_pid", 101)
        put("controller_claim_host_xvfb_birth_epoch_millis", 1000)
        put("controller_claim_host_client_pid", 111); put("controller_claim_host_client_birth_epoch_millis", 1001)
        put("glfw_window", 44); put("x11_window", 45)
        put("client_session_id", "client-11111111-1111-1111-1111-111111111111")
        put("epoch_id", "epoch-00001"); put("purpose", "diagnostic")
        put("display", "127.0.0.1:7"); put("xauthority_sha256", sha)
        put("hold_sequence", 7); put("screen_class", null as String?)
        put("walk_start_yaw", 0.0); put("x", 0.5); put("y", 65.0); put("z", z); put("yaw", 0.0)
        put("on_ground", true); put("in_fluid", false); put("held_state_ticks", held)
        set<JsonNode>("walk_samples", suiteMapper.createArrayNode().apply { samples.forEach { add(it) } })
    }
    private fun server(z: Double, tick: Int, nanos: Int) = suiteMapper.createObjectNode().apply {
        put("uuid", actor); put("dimension", "minecraft:overworld"); put("mode", "survival")
        put("x", 0.5); put("y", 65.0); put("z", z); put("yaw", 0.0); put("pitch", 0.0)
        put("on_ground", true); put("swimming", false); put("passenger", false); put("sprinting", false)
        put("fall_distance", 0.0); put("server_tick", tick); put("server_monotonic_nanos", nanos)
    }
    private fun observed(t: ObjectNode): List<DriverObservation> {
        val samples = listOf(sample(10, 0.5), sample(11, 0.8), sample(12, 1.1),
            sample(13, 1.4), sample(14, 1.5))
        return WalkRouteControlDriver.plan(t).map { step ->
            val action = step.action
            val payload: JsonNode = when (action.id) {
                "block.snapshot" -> action.parameters.deepCopy().apply {
                    set<JsonNode>("block_state", suiteMapper.createObjectNode().apply {
                        put("id", if (action.parameters.path("y").asInt() == 64) "minecraft:stone" else "minecraft:air")
                        set<JsonNode>("properties", suiteMapper.createObjectNode())
                    })
                }
                "player.snapshot" -> if (step.name.endsWith(".before")) server(0.5, 100, 1000) else server(1.5, 106, 2000)
                "wait.ticks" -> suiteMapper.createObjectNode().put("ticks_observed", 4)
                "client.input_hold" -> client(0.5, 0, samples.take(1)).put("physical_press_issued", true)
                "client.input_release" -> client(1.5, 4, samples).put("press_callback", true)
                    .put("physical_release_issued", true)
                "client.input_status" -> client(1.5, 4, samples).put("press_callback", true)
                    .put("release_callback", true).put("physical_release_verified", true)
                    .put("physical_input_down", false)
                else -> error("unreviewed action")
            }
            DriverObservation(step.name, action.id, action.source, "completed", payload)
        }
    }
    @Test fun exactlyOnePhysicalStraightRouteMayPassSyntheticIndependentOracle() {
        val t = target(); val planned = WalkRouteControlDriver.plan(t)
        assertEquals("control.travel.walk-route", WalkRouteControlDriver.caseId)
        assertEquals("client.input_hold", planned.first { it.name == "leg0.hold" }.action.id)
        assertEquals("walk_forward", planned.first { it.name == "leg0.hold" }.action.parameters.path("kind").asText())
        assertEquals("wait.ticks", planned.first { it.name == "leg0.wait" }.action.id)
        assertEquals("passed", WalkRouteControlDriver.evaluate(t, observed(t)).state)
    }
    @Test fun absentKeyUpChangedCorridorWrongActorAndServerTeleportFailClosed() {
        val t = target(); val baseline = observed(t)
        fun changed(step: String, change: (ObjectNode) -> Unit) = baseline.map { row ->
            if (row.step == step) row.copy(payload = row.payload.deepCopy<ObjectNode>().apply(change)) else row
        }
        assertThrows(IllegalArgumentException::class.java) { WalkRouteControlDriver.evaluate(t,
            changed("leg0.status") { it.put("release_callback", false) }) }
        assertThrows(IllegalArgumentException::class.java) { WalkRouteControlDriver.evaluate(t,
            changed("leg0.post.0.65.1.-1") { (it.path("block_state") as ObjectNode).put("id", "minecraft:lava") }) }
        assertThrows(IllegalArgumentException::class.java) { WalkRouteControlDriver.evaluate(t,
            changed("leg0.before") { it.put("uuid", "other") }) }
        assertThrows(IllegalArgumentException::class.java) { WalkRouteControlDriver.evaluate(t,
            changed("leg0.after") { it.put("z", 100.0) }) }
    }
    @Test fun routeHashTurnBranchHeightChangeUnboundedWaitOrUnknownFieldsRejectedBeforeAction() {
        val t = target()
        fun invalid(change: (ObjectNode) -> Unit): ObjectNode = t.deepCopy().apply(change)
        assertThrows(IllegalArgumentException::class.java) {
            WalkRouteControlDriver.plan(invalid { it.put("route_sha256", "b".repeat(64)) })
        }
        fun changedRoute(change: (ObjectNode) -> Unit): ObjectNode = invalid { row ->
            change(row.path("route") as ObjectNode)
            row.put("route_sha256", jsonFingerprint(row.path("route")))
        }
        assertThrows(IllegalArgumentException::class.java) { WalkRouteControlDriver.plan(changedRoute { row ->
            val leg = row.path("legs")[0] as ObjectNode; leg.set<JsonNode>("target", cell(1, 65, 1))
        }) }
        assertThrows(IllegalArgumentException::class.java) { WalkRouteControlDriver.plan(changedRoute { row ->
            (row.path("legs")[0].path("target") as ObjectNode).put("y", 66)
        }) }
        assertThrows(IllegalArgumentException::class.java) { WalkRouteControlDriver.plan(changedRoute { row ->
            (row.path("legs")[0] as ObjectNode).put("wait_ticks", 100)
        }) }
        assertThrows(IllegalArgumentException::class.java) { WalkRouteControlDriver.plan(invalid { it.put("teleport", true) }) }
    }
}
