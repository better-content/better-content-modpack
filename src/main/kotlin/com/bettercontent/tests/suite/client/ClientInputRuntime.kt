package com.bettercontent.tests.suite.client

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.ManagedProcess
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.drivers.DriverAction
import com.bettercontent.tests.suite.drivers.DriverStep
import com.bettercontent.tests.suite.idPattern
import com.bettercontent.tests.suite.jsonFingerprint
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.DeserializationFeature
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

internal const val MENU_FIRST_PROJECTION_MARKER = "@controller-menu-first-projection-sha256-v1"
internal const val MENU_HOVER_PROJECTION_MARKER = "@controller-menu-hover-projection-sha256-v1"
internal val authenticInputDrivers = setOf("client.input_hold", "client.input_release", "client.input_status",
    "client.input_point", "client.menu_slot.observe_v1", "client.menu_slot.point_v1")
internal val passiveObservationDrivers = setOf("player.observe_item_use.arm", "player.observe_item_use.await",
    "player.observe_onboarding.arm", "player.observe_onboarding.await")
internal const val INPUT_RUNTIME_MARKER = "@input-runtime-sha256"
internal const val OBSERVED_ACTOR_MARKER = "@observed-player-uuid"
internal const val CONTROLLER_CONTEXT_MARKER = "@controller-witness-context-v1"
internal const val ARM_SEQUENCE_MARKER = "@arm-request-sequence"
internal const val ARM_ORDINAL_MARKER = "@arm-action-ordinal"

/** The actor is the server-observed joined UUID, never an authored UUID or callback claim. */
internal fun resolveObservedActorAction(action: DriverAction, actorUuid: String?): DriverAction {
    if (action.id !in authenticInputDrivers + passiveObservationDrivers) return action
    require(actorUuid != null && runCatching { UUID.fromString(actorUuid).toString() == actorUuid }.getOrDefault(false) &&
        action.parameters.path("player_uuid").isTextual &&
        action.parameters.path("player_uuid").asText() == OBSERVED_ACTOR_MARKER) {
        "client/passive action actor must come from real server inventory"
    }
    return action.copy(parameters = action.parameters.deepCopy().apply { put("player_uuid", actorUuid) })
}

internal data class PassiveObserverArm(val observerId: String, val kind: String, val actorUuid: String,
    val requestSequence: Long, val actionOrdinal: Long,
    val context: com.bettercontent.tests.suite.checkpoints.WitnessContext)
/** Per-trial exact server allocation and arm ancestry, before request publication. */
internal fun resolvePassiveObserverAction(action: DriverAction, context: com.bettercontent.tests.suite.checkpoints.WitnessContext?,
    arms: MutableMap<String, PassiveObserverArm>): DriverAction {
    if (action.id !in passiveObservationDrivers) return action
    val truth = requireNotNull(context) { "passive observer requires controller dispatch context" }
    require(action.source == "server" && truth.source == "server" && truth.capabilityId == action.id &&
        truth.actorUuid == action.parameters.path("player_uuid").asText() &&
        action.parameters.path("witness_context").isTextual &&
        action.parameters.path("witness_context").asText() == CONTROLLER_CONTEXT_MARKER) {
        "unreviewed passive observer context or actor"
    }
    val observer = action.parameters.path("observer_id").asText()
    require(runCatching { UUID.fromString(observer).toString() == observer }.getOrDefault(false)) {
        "passive observer ID must be exact reviewed canonical UUID"
    }
    val kind = action.id.substringBeforeLast('.')
    val params = action.parameters.deepCopy().apply {
        set<com.fasterxml.jackson.databind.JsonNode>("witness_context", suiteMapper.valueToTree(truth))
    }
    if (action.id.endsWith(".arm")) {
        require(arms.isEmpty()) { "passive observer cannot replace or overlap an active arm" }
        arms[observer] = PassiveObserverArm(observer, kind, truth.actorUuid,
            truth.requestSequence, truth.actionOrdinal, truth)
    } else {
        val arm = requireNotNull(arms[observer]) { "await lacks actual earlier arm" }
        require(arm.kind == kind && arm.actorUuid == truth.actorUuid && arm.context.sameAncestry(truth) &&
            truth.actionOrdinal > arm.actionOrdinal && truth.requestSequence > arm.requestSequence &&
            params.path("arm_request_sequence").asText() == ARM_SEQUENCE_MARKER &&
            params.path("arm_action_ordinal").asText() == ARM_ORDINAL_MARKER) {
            "passive await changed arm identity or reused unreviewed sequence"
        }
        params.put("arm_request_sequence", arm.requestSequence)
        params.put("arm_action_ordinal", arm.actionOrdinal)
        arms.remove(observer)
    }
    return action.copy(parameters = params)
}

/** Only a controller-owned immutable binding may replace this exact reviewed placeholder. */
internal fun resolveInputRuntimeAction(action: DriverAction, binding: BoundFile?): DriverAction {
    if (action.id !in authenticInputDrivers) return action
    require(action.source == "client" && binding != null &&
        action.parameters.path("input_runtime_sha256").asText() == INPUT_RUNTIME_MARKER &&
        action.parameters.path("input_runtime_sha256").isTextual && binding.sha256.matches(shaPattern)) {
        "unreviewed client input runtime substitution"
    }
    return action.copy(parameters = action.parameters.deepCopy().apply {
        put("input_runtime_sha256", binding.sha256)
    })
}

/** Actual first and second rendered projections, never authored fixed hashes or item/recipe facts. */
internal fun resolveMenuSlotProjectionAction(action: DriverAction, first: String?, hovered: String?): DriverAction {
    val key = when {
        action.id == "client.menu_slot.point_v1" -> "observation_projection_sha256"
        action.id == "client.menu_slot.observe_v1" &&
            action.parameters.path("prior_observation_projection_sha256").isTextual ->
            "prior_observation_projection_sha256"
        action.id == "client.input_hold" &&
            action.parameters.path("kind").asText() == "container_slot_left" -> "hover_projection_sha256"
        else -> return action
    }
    val expectedMarker = if (key == "hover_projection_sha256") MENU_HOVER_PROJECTION_MARKER
        else MENU_FIRST_PROJECTION_MARKER
    val projection = if (key == "hover_projection_sha256") hovered else first
    require(action.parameters.path(key).isTextual &&
        action.parameters.path(key).asText() == expectedMarker &&
        projection != null && projection.matches(shaPattern)) {
        "fresh actual original-render menu-slot projection absent before reviewed OS action"
    }
    return action.copy(parameters = (action.parameters as com.fasterxml.jackson.databind.node.ObjectNode)
        .deepCopy().apply { put(key, projection) })
}

/** C's pinned native v1 schema; host PID/birth are controller claims, NOT sibling PID namespace visibility. */
/** A passive observer must be armed BEFORE any real OS input; onboarding awaits AFTER bounded pulse release. */
internal fun requireReviewedInputObserverSequence(steps: List<DriverStep>, caseId: String? = null) {
    val actions = steps.map { it.action }
    if (caseId == "control.client.inventory-screen") {
        require(actions.map { it.id } == listOf("player.snapshot", "client.snapshot", "client.input_hold",
            "client.input_release", "client.input_status", "client.snapshot", "client.framebuffer",
            "player.snapshot") && actions.map { it.source } ==
            listOf("server", "client", "client", "client", "client", "client", "client", "server") &&
            actions[2].parameters.path("kind").asText() == "inventory" &&
            actions[2].parameters.path("max_ticks").asInt(-1) == 6 &&
            actions[6].parameters.path("profile") == actions[2].parameters.path("profile")) {
            "inventory GUI requires one read-only actor/client source, physical inventory key and reviewed frame"
        }
    }
    if (caseId == "control.client.emi-navigation") {
        require(actions.map { it.id } == listOf("player.snapshot", "client.snapshot", "client.input_hold",
            "client.input_release", "client.input_status", "client.input_point", "wait.ticks",
            "client.emi_hover", "client.input_hold", "client.input_release", "client.input_status",
            "client.emi_page", "client.framebuffer", "client.emi_page", "player.snapshot") &&
            actions.map { it.source } == listOf("server", "client", "client", "client", "client",
                "client", "server", "client", "client", "client", "client", "client", "client",
                "client", "server") && actions[2].parameters.path("kind").asText() == "inventory" &&
            actions[2].parameters.path("max_ticks").asInt(-1) == 6 &&
            actions[6].parameters.path("ticks").asInt(-1) == 4 &&
            actions[8].parameters.path("kind").asText() in setOf("emi_mouse_left", "emi_mouse_right") &&
            actions[8].parameters.path("max_ticks").asInt(-1) == 6 &&
            actions[8].parameters.path("hover_projection_sha256").asText() ==
                EMI_HOVER_PROJECTION_MARKER && actions[8].parameters.path("emi_jar_sha256") ==
                actions[7].parameters.path("emi_jar_sha256") &&
            actions[11].parameters == actions[13].parameters &&
            actions[5].parameters.path("profile") == actions[12].parameters.path("profile") &&
            actions[2].parameters.path("profile") == actions[5].parameters.path("profile") &&
            actions[8].parameters.path("profile") == actions[5].parameters.path("profile")) {
            "EMI GUI requires reviewed inventory-key, OS point, settled rendered hover, physical mouse and actual page/frame"
        }
    }
    if (caseId == "control.travel.walk-route") {
        val holds = actions.filter { it.id == "client.input_hold" }
        require(holds.size in 1..4 && holds.all { it.parameters.path("kind").asText() == "walk_forward" } &&
            actions.filter { it.id in authenticInputDrivers }.size == holds.size * 3 &&
            actions.all { action -> when (action.source) {
                "server" -> action.id in setOf("block.snapshot", "player.snapshot", "wait.ticks")
                "client" -> action.id in authenticInputDrivers
                else -> false
            } }) { "walk control may use only reviewed read-only server observers and physical W client actions" }
    }
    actions.forEachIndexed { index, action ->
        if (action.id != "client.input_hold") return@forEachIndexed
        val kind = action.parameters.path("kind").asText()
        val holdId = action.parameters.path("hold_id").asText()
        require(holdId.isNotBlank())
        val release = actions.indices.firstOrNull { it > index && actions[it].id == "client.input_release" &&
            actions[it].parameters.path("hold_id").asText() == holdId } ?: error("physical hold lacks reviewed release")
        val status = actions.indices.firstOrNull { it > release && actions[it].id == "client.input_status" &&
            actions[it].parameters.path("hold_id").asText() == holdId } ?: error("physical hold lacks reviewed key-up status")
        val observer = when (kind) {
            "use" -> "player.observe_item_use"
            "onboarding" -> "player.observe_onboarding"
            "inventory" -> {
                require(caseId in setOf("control.client.inventory-screen", "control.client.emi-navigation") &&
                    (caseId != "control.client.emi-navigation" || index == 2)) {
                    "inventory key input is restricted to the one-profile registered GUI control"
                }
                null
            }
            "emi_mouse_left", "emi_mouse_right" -> {
                require(caseId == "control.client.emi-navigation" && index == 8 &&
                    actions[index - 1].id == "client.emi_hover" &&
                    actions[index - 2].id == "wait.ticks" &&
                    actions[index - 3].id == "client.input_point" &&
                    release == 9 && status == 10 &&
                    actions[status + 1].id == "client.emi_page" &&
                    action.parameters.path("profile").asText() in setOf("1280x720@2", "1920x1080@3")) {
                    "EMI physical mouse requires bound prior real point, four-tick settle and independently reviewed hover"
                }
                null
            }
            "container_slot_left" -> {
                require(index >= 3 && actions[index - 3].id == "client.menu_slot.observe_v1" &&
                    actions[index - 2].id == "client.menu_slot.point_v1" &&
                    actions[index - 1].id == "client.menu_slot.observe_v1" &&
                    actions.count { it.id == "client.input_hold" } == 1 &&
                    release == index + 1 && status == release + 1 &&
                    action.parameters.path("hover_projection_sha256").isTextual &&
                    action.parameters.path("hover_projection_sha256").asText() ==
                        MENU_HOVER_PROJECTION_MARKER &&
                    action.parameters.path("max_ticks").isIntegralNumber &&
                    action.parameters.path("max_ticks").canConvertToInt() &&
                    action.parameters.path("max_ticks").asInt() in 2..8) {
                    "TCon slot click requires fresh staged original-render hover then OS press/release/status"
                }
                null
            }
            "walk_forward" -> {
                require(caseId == "control.travel.walk-route" && index > 0 && status + 1 < actions.size &&
                    actions[index - 1].id == "player.snapshot" && actions[index + 1].id == "wait.ticks" &&
                    release == index + 2 && status == release + 1 &&
                    actions[status + 1].id == "player.snapshot" &&
                    action.parameters.path("max_ticks").isIntegralNumber &&
                    action.parameters.path("max_ticks").asInt(-1) in 4..60 &&
                    action.parameters.path("profile").asText() in setOf("1280x720@2", "1920x1080@3")) {
                    "walk W input must be bracketed by real server snapshots, wait and physical release"
                }
                null
            }
            else -> error("unreviewed physical input kind")
        }
        observer?.let { prefix ->
            require(actions.count { it.id == "$prefix.arm" } == 1 &&
                actions.count { it.id == "$prefix.await" } == 1 &&
                actions.count { it.id == "client.input_hold" } == 1 &&
                actions.indices.any { it < index && actions[it].id == "$prefix.arm" } &&
                actions.indices.any { it > status && actions[it].id == "$prefix.await" }) {
                "authentic $kind input cannot race a missing passive observer arm/await"
            }
        }
        if (kind == "use") require(actions.none { it.id == "player.use_item" }) {
            "server-forced use cannot substitute for physical client use"
        }
    }
}

private val clientInputRuntimeFields = setOf("schema", "purpose", "campaign_id", "lineage_id", "epoch_id",
    "client_session_id", "candidate_sha256", "support_sha256", "profile", "display", "xauthority_path",
    "xauthority_sha256", "host_xvfb_pid", "host_xvfb_birth_epoch_millis", "host_xvfb_executable",
    "host_xvfb_args_sha256", "host_client_pid", "host_client_birth_epoch_millis", "host_client_nspid")

internal data class ClientInputRuntime(val schema: String, val purpose: String, val campaignId: String,
    val lineageId: String, val epochId: String, val clientSessionId: String, val candidateSha256: String,
    val supportSha256: String, val profile: String, val display: String,
    val xauthorityPath: String, val xauthoritySha256: String,
    val hostXvfbPid: Long, val hostXvfbBirthEpochMillis: Long,
    val hostXvfbExecutable: String, val hostXvfbArgsSha256: String,
    val hostClientPid: Long, val hostClientBirthEpochMillis: Long, val hostClientNspid: List<Long>) {
    init {
        require(schema == "bc.playtest_client_input_runtime.v1" && purpose in setOf("diagnostic", "qualification") &&
            campaignId.startsWith("campaign-") &&
            runCatching { UUID.fromString(campaignId.removePrefix("campaign-")).toString() ==
                campaignId.removePrefix("campaign-") && UUID.fromString(lineageId).toString() == lineageId }.getOrDefault(false) &&
            epochId.matches(Regex("epoch-[0-9]{5}")) && clientSessionId.matches(idPattern) &&
            listOf(candidateSha256, supportSha256, xauthoritySha256, hostXvfbArgsSha256).all(shaPattern::matches) &&
            profile in setOf("1280x720@2", "1920x1080@3") &&
            display.matches(Regex("127\\.0\\.0\\.1:[0-9]{1,5}")) &&
            Path.of(xauthorityPath).isAbsolute && Path.of(hostXvfbExecutable).isAbsolute &&
            Path.of(hostXvfbExecutable).fileName.toString() == "Xvfb" &&
            hostXvfbPid > 1 && hostClientPid > 1 && hostXvfbPid != hostClientPid &&
            hostXvfbBirthEpochMillis > 0 && hostClientBirthEpochMillis > 0 &&
            hostClientNspid.size in 2..8 && hostClientNspid.first() == hostClientPid &&
            hostClientNspid.last() > 0 && hostClientNspid.last() != hostClientPid) {
            "input runtime binding lacks genuine distinct host/namespace identity or reviewed context"
        }
    }
    @get:com.fasterxml.jackson.annotation.JsonIgnore
    val clientNamespacePid: Long get() = hostClientNspid.last()
}

internal data class HostInputProcess(val pid: Long, val startedAt: Instant,
    val executable: String, val arguments: List<String>) {
    init { require(pid > 0 && Path.of(executable).isAbsolute && arguments.isNotEmpty()) }
    val birthEpochMillis: Long get() = startedAt.toEpochMilli()
    val argsSha256: String get() = jsonFingerprint(suiteMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(arguments))
}

internal fun hostNamespacePids(hostPid: Long, readStatus: (Long) -> String = { pid ->
    Files.readString(Path.of("/proc/$pid/status")) }): List<Long> {
    require(hostPid > 0)
    val rows = readStatus(hostPid).lineSequence().filter { it.startsWith("NSpid:") }.toList()
    require(rows.size == 1) { "host client NSpid mapping unavailable or ambiguous" }
    val values = rows.single().removePrefix("NSpid:").trim().split(Regex("\\s+")).map { text ->
        text.toLongOrNull()?.takeIf { it > 0 } ?: error("malformed client NSpid mapping")
    }
    require(values.size in 2..8 && values.first() == hostPid && values.last() != hostPid) {
        "client host PID is not mapped to a separate sandbox namespace"
    }
    return values
}

internal fun captureHostInputProcess(pid: Long, owned: ManagedProcess): HostInputProcess {
    require(pid in owned.trackedPids) { "host process was not launched under owned tracker" }
    val handle = ProcessHandle.of(pid).orElseThrow { IllegalStateException("tracked input host process exited") }
    require(handle.isAlive && handle.info().startInstant().isPresent &&
        owned.processStarts[pid] == handle.info().startInstant().get()) { "host process PID reused or birth not tracked" }
    val info = handle.info()
    return HostInputProcess(pid, info.startInstant().get(),
        info.command().orElseThrow { IllegalStateException("input executable unavailable") },
        info.arguments().orElseThrow { IllegalStateException("input process argv unavailable") }.toList())
}

internal fun hostInputIdentityMatches(runtime: ClientInputRuntime, xvfb: HostInputProcess, java: HostInputProcess,
    namespace: List<Long>, originallyCapturedClient: HostInputProcess?): Boolean =
    xvfb.pid == runtime.hostXvfbPid && xvfb.birthEpochMillis == runtime.hostXvfbBirthEpochMillis &&
        xvfb.executable == runtime.hostXvfbExecutable && xvfb.argsSha256 == runtime.hostXvfbArgsSha256 &&
        java.pid == runtime.hostClientPid && java.birthEpochMillis == runtime.hostClientBirthEpochMillis &&
        (originallyCapturedClient == null || java == originallyCapturedClient) &&
        namespace == runtime.hostClientNspid

internal fun verifyHostInputRuntime(runtime: ClientInputRuntime, display: ManagedProcess, client: ManagedProcess,
    authority: Path, inspect: (Long, ManagedProcess) -> HostInputProcess = ::captureHostInputProcess,
    namespace: (Long) -> List<Long> = { hostNamespacePids(it) },
    originallyCapturedClient: HostInputProcess? = null) {
    val xvfb = inspect(runtime.hostXvfbPid, display)
    val java = inspect(runtime.hostClientPid, client)
    require(hostInputIdentityMatches(runtime, xvfb, java, namespace(runtime.hostClientPid), originallyCapturedClient) &&
        authority.toAbsolutePath().normalize().toString() == runtime.xauthorityPath &&
        Files.isRegularFile(authority, NOFOLLOW_LINKS) && !Files.isSymbolicLink(authority) &&
        Files.size(authority) in 1..4096 && Hashes.sha256(authority) == runtime.xauthoritySha256) {
        "host display/client PID birth/argv, namespace, or private cookie changed after binding"
    }
}

/** Fixed epoch-owned file; exact one bounded no-follow byte capture and strict trailing-token parsing. */
internal fun readClientInputRuntime(mailbox: Path, binding: BoundFile, expectedSha: String): ClientInputRuntime {
    val ownedMailbox = mailbox.toAbsolutePath().normalize()
    require(ownedMailbox.fileName.toString() == "client-native" &&
        Files.isDirectory(ownedMailbox, NOFOLLOW_LINKS) && !Files.isSymbolicLink(ownedMailbox) &&
        Files.isDirectory(ownedMailbox.parent, NOFOLLOW_LINKS) && !Files.isSymbolicLink(ownedMailbox.parent)) {
        "input binding mailbox is linked or not epoch-owned"
    }
    val file = ownedMailbox.parent.resolve("client-input-runtime.json")
    require(file == Path.of(binding.path).toAbsolutePath().normalize() &&
        expectedSha.matches(shaPattern) && binding.sha256 == expectedSha &&
        Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) &&
        Files.size(file) in 1..16_384) { "foreign or mutable client input runtime binding" }
    val bytes = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(16_385) }
    require(bytes.size in 1..16_384 && bytes.size.toLong() == Files.size(file)) { "input runtime changed while captured" }
    val captured = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    require(captured == expectedSha) { "input runtime bytes differ from controller SHA" }
    val strict = suiteMapper.copy().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
    val wire: com.fasterxml.jackson.databind.JsonNode = strict.reader()
        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes)
    require(wire.isObject && wire.fieldNames().asSequence().toSet() == clientInputRuntimeFields) {
        "input runtime must contain exactly the native v1 binding fields"
    }
    // JsonIgnore omits only a derived convenience getter on write; it must not let a
    // caller smuggle that field back into the sealed input. Parse only captured tokens.
    return strict.readerFor(ClientInputRuntime::class.java)
        .with(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
        .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(wire.traverse(strict))
}
