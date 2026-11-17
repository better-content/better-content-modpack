package com.bettercontent.tests.suite.client

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.drivers.DriverAction
import com.bettercontent.tests.suite.drivers.DriverStep
import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.checkpoints.WitnessContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** Source regressions only; no Robot, sandbox, GUI or process launch. */
class ClientInputRuntimeTest {
    @TempDir lateinit var root: Path
    private val start = Instant.parse("2026-11-16T09:00:00Z")
    private fun xvfb() = HostInputProcess(5123, start, "/usr/bin/Xvfb",
        listOf(":88", "-screen", "0", "1280x720x24", "-auth", "/home/dev/.tmp/owned/Xauthority"))
    private fun java() = HostInputProcess(5234, start.plusSeconds(2), "/usr/bin/java",
        listOf("-Dbc.playtest.client.runId=client-${UUID.randomUUID()}", "net.minecraft.client.main.Main"))
    private fun runtime(x: HostInputProcess, j: HostInputProcess, authority: Path) = ClientInputRuntime(
        "bc.playtest_client_input_runtime.v1", "diagnostic", "campaign-${UUID.randomUUID()}",
        UUID.randomUUID().toString(), "epoch-00001", "client-${UUID.randomUUID()}",
        "a".repeat(64), "b".repeat(64), "1280x720@2", "127.0.0.1:88", authority.toString(),
        "c".repeat(64), x.pid, x.birthEpochMillis, x.executable, x.argsSha256,
        j.pid, j.birthEpochMillis, listOf(j.pid, 7))

    @Test fun `host and namespace PIDs are different identities and missing mapping fails`() {
        assertEquals(listOf(5234L, 7L), hostNamespacePids(5234) { "Name:\tjava\nNSpid:\t5234\t7\n" })
        assertThrows(IllegalArgumentException::class.java) { hostNamespacePids(5234) { "NSpid:\t5234\n" } }
        assertThrows(IllegalArgumentException::class.java) { hostNamespacePids(5234) { "NSpid:\t1234\t7\n" } }
        assertThrows(IllegalArgumentException::class.java) { hostNamespacePids(5234) { "Name:\tjava\n" } }
        val x = xvfb(); val j = java(); val r = runtime(x, j, root.resolve("Xauthority"))
        assertTrue(hostInputIdentityMatches(r, x, j, listOf(j.pid, 7), j))
        assertThrows(IllegalArgumentException::class.java) { r.copy(profile = "640x480@1") }
        assertThrows(IllegalArgumentException::class.java) { r.copy(purpose = "qualification-by-diagnostic") }
        assertThrows(IllegalArgumentException::class.java) { r.copy(epochId = "epoch-foreign") }
        assertThrows(IllegalArgumentException::class.java) { r.copy(hostClientNspid = listOf(j.pid)) }
        assertThrows(IllegalArgumentException::class.java) { r.copy(hostXvfbPid = j.pid) }
        assertFalse(hostInputIdentityMatches(r, x, j, listOf(j.pid, j.pid), j))
        assertFalse(hostInputIdentityMatches(r, x.copy(startedAt = x.startedAt.plusSeconds(1)), j,
            listOf(j.pid, 7), j))
        assertFalse(hostInputIdentityMatches(r, x.copy(arguments = x.arguments + "-unsafe"), j,
            listOf(j.pid, 7), j))
        assertFalse(hostInputIdentityMatches(r, x, j.copy(startedAt = j.startedAt.plusSeconds(1)),
            listOf(j.pid, 7), j))
    }

    @Test fun `runtime file is fixed by client mailbox and captured once with exact SHA`() {
        val mailbox = root.resolve("evidence/client-native")
        Files.createDirectories(mailbox)
        val file = mailbox.parent.resolve("client-input-runtime.json")
        val r = runtime(xvfb(), java(), root.resolve("Xauthority"))
        val encoded = suiteMapper.writeValueAsBytes(r)
        val wire = suiteMapper.readTree(encoded)
        // Independently declared native ClientInputRuntimeBinding v1 contract: no derived PID key.
        assertEquals(setOf("schema", "purpose", "campaign_id", "lineage_id", "epoch_id", "client_session_id",
            "candidate_sha256", "support_sha256", "profile", "display", "xauthority_path", "xauthority_sha256",
            "host_xvfb_pid", "host_xvfb_birth_epoch_millis", "host_xvfb_executable", "host_xvfb_args_sha256",
            "host_client_pid", "host_client_birth_epoch_millis", "host_client_nspid"),
            wire.fieldNames().asSequence().toSet())
        assertEquals(7L, r.clientNamespacePid)
        assertEquals(listOf(5234L, 7L), wire.path("host_client_nspid").map { it.asLong() })
        Files.write(file, encoded)
        val bound = BoundFile(file.toString(), Hashes.sha256(file))
        assertEquals(r, readClientInputRuntime(mailbox, bound, bound.sha256))
        assertThrows(IllegalArgumentException::class.java) {
            readClientInputRuntime(mailbox, bound, "f".repeat(64))
        }
        assertThrows(IllegalArgumentException::class.java) {
            readClientInputRuntime(mailbox, bound.copy(sha256 = "f".repeat(64)), "f".repeat(64))
        }
        assertThrows(IllegalArgumentException::class.java) {
            readClientInputRuntime(mailbox, bound.copy(path = root.resolve("foreign.json").toString()), bound.sha256)
        }
        Files.writeString(file, suiteMapper.writeValueAsString(r) + " {}")
        val trailing = BoundFile(file.toString(), Hashes.sha256(file))
        assertThrows(Exception::class.java) { readClientInputRuntime(mailbox, trailing, trailing.sha256) }
        Files.writeString(file, suiteMapper.writeValueAsString(r).dropLast(1) + ",\"profile\":\"1280x720@2\"}")
        val duplicate = BoundFile(file.toString(), Hashes.sha256(file))
        assertThrows(Exception::class.java) { readClientInputRuntime(mailbox, duplicate, duplicate.sha256) }
        for (field in listOf("client_namespace_pid", "unreviewed_claim")) {
            val injected = wire.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put(field, 7L)
            Files.write(file, suiteMapper.writeValueAsBytes(injected))
            val extra = BoundFile(file.toString(), Hashes.sha256(file))
            assertThrows(IllegalArgumentException::class.java) { readClientInputRuntime(mailbox, extra, extra.sha256) }
        }
    }

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

    @Test fun `server observed actor and allocated arm sequence are the only accepted passive truth`() {
        val actor = UUID.randomUUID().toString()
        val observer = UUID.randomUUID().toString()
        val campaign = "campaign-${UUID.randomUUID()}"
        val lineage = UUID.randomUUID().toString()
        val nativeSession = "epoch-${UUID.randomUUID()}"
        fun context(capability: String, ordinal: Long, sequence: Long) = WitnessContext(
            campaign, lineage, "epoch-00001", nativeSession,
            "branch", "primitive.harvest", "trial-1", "server",
            ordinal, sequence, actor, capability, "diagnostic")
        val armId = "player.observe_item_use.arm"
        val awaitId = "player.observe_item_use.await"
        val arm = DriverAction(armId, suiteMapper.createObjectNode().put("player_uuid", OBSERVED_ACTOR_MARKER)
            .put("observer_id", observer).put("witness_context", CONTROLLER_CONTEXT_MARKER))
        val await = DriverAction(awaitId, suiteMapper.createObjectNode().put("player_uuid", OBSERVED_ACTOR_MARKER)
            .put("observer_id", observer).put("witness_context", CONTROLLER_CONTEXT_MARKER)
            .put("arm_request_sequence", ARM_SEQUENCE_MARKER).put("arm_action_ordinal", ARM_ORDINAL_MARKER))
        val active = mutableMapOf<String, PassiveObserverArm>()
        assertThrows(IllegalArgumentException::class.java) {
            resolveObservedActorAction(arm.copy(parameters = arm.parameters.deepCopy().put("player_uuid", actor)), actor)
        }
        val boundArm = resolvePassiveObserverAction(resolveObservedActorAction(arm, actor),
            context(armId, 1, 2), active)
        assertEquals(2, boundArm.parameters.path("witness_context").path("request_sequence").asInt())
        assertThrows(IllegalArgumentException::class.java) {
            resolvePassiveObserverAction(resolveObservedActorAction(arm, actor), context(armId, 2, 3), active)
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolvePassiveObserverAction(resolveObservedActorAction(await, actor),
                context(awaitId, 5, 3).copy(epochId = "epoch-00002"), active)
        }
        val boundAwait = resolvePassiveObserverAction(resolveObservedActorAction(await, actor),
            context(awaitId, 5, 3), active)
        assertEquals(2, boundAwait.parameters.path("arm_request_sequence").asInt())
        assertEquals(1, boundAwait.parameters.path("arm_action_ordinal").asInt())
        assertTrue(active.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            resolvePassiveObserverAction(resolveObservedActorAction(await, actor), context(awaitId, 6, 4), active)
        }
    }

    @Test fun `only exact reviewed marker becomes controller owned request SHA`() {
        val action = DriverAction("client.input_hold", suiteMapper.createObjectNode()
            .put("profile", "1280x720@2").put("input_runtime_sha256", INPUT_RUNTIME_MARKER), 80,
            source = "client")
        val binding = BoundFile("/home/dev/.tmp/owned/client-input-runtime.json", "e".repeat(64))
        val resolved = resolveInputRuntimeAction(action, binding)
        assertEquals(binding.sha256, resolved.parameters.path("input_runtime_sha256").asText())
        assertEquals(INPUT_RUNTIME_MARKER, action.parameters.path("input_runtime_sha256").asText())
        assertThrows(IllegalArgumentException::class.java) { resolveInputRuntimeAction(action, null) }
        assertThrows(IllegalArgumentException::class.java) {
            resolveInputRuntimeAction(action.copy(parameters = action.parameters.deepCopy().apply {
                put("input_runtime_sha256", "f".repeat(64)) }), binding)
        }
    }
}
