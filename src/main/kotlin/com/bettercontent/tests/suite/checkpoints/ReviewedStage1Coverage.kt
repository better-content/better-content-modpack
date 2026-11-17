package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.NativeRequest
import com.bettercontent.tests.suite.idPattern
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipFile

/** A bounded, reviewed SUPPORTED path, never universal JVM/mixin/natural-proof metadata from native. */
internal data class ReviewedStage1Coverage(val profile: BoundFile, val selectorSha256: String,
    val candidateServerSha256: String, val providerModId: String, val providerJarSha256: String,
    val positiveIds: Set<String>, val negativeIds: Set<String>, val classPins: Map<String, String>) {
    init { require(selectorSha256.matches(shaPattern) && candidateServerSha256.matches(shaPattern) &&
        providerModId.matches(idPattern) && providerJarSha256.matches(shaPattern) &&
        positiveIds == stage1SafePositiveControlIds && negativeIds == stage1RequiredNegativeControlIds &&
        classPins.isNotEmpty()) }
}
private val reviewedJson = suiteMapper.copy().apply { factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION) }
private fun reviewedFields(row: JsonNode, keys: Set<String>) {
    require(row.isObject && row.fieldNames().asSequence().toSet() == keys) { "reviewed scope/control fields differ" }
}
private fun reviewedBytes(ref: BoundFile, limit: Int = 1_048_576): JsonNode {
    val file = Path.of(ref.path).toAbsolutePath().normalize()
    require(ref.path == file.toString() && ref.sha256.matches(shaPattern) &&
        Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) &&
        Files.size(file) in 2..limit.toLong())
    var parent = file.parent
    while (parent != null) { require(!Files.isSymbolicLink(parent)); parent = parent.parent }
    val bytes = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(limit + 1) }
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    require(bytes.size in 2..limit && Files.size(file) == bytes.size.toLong() &&
        digest == ref.sha256 && Hashes.sha256(file) == ref.sha256)
    val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    return reviewedJson.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text)
}
private fun reviewedBound(row: JsonNode): BoundFile {
    reviewedFields(row, setOf("path", "sha256"))
    return suiteMapper.treeToValue(row, BoundFile::class.java)
}
private fun reviewedState(row: JsonNode): Pair<String, Map<String, String>> {
    reviewedFields(row, setOf("id", "properties"))
    require(row.path("id").isTextual && row.path("id").asText().matches(resourcePattern) &&
        row.path("properties").isObject && row.path("properties").size() <= 64)
    return row.path("id").asText() to row.path("properties").fields().asSequence().associate { (key, value) ->
        require(key.matches(Regex("[a-z0-9_]{1,64}")) && value.isTextual && value.asText().length in 1..128)
        key to value.asText()
    }.toSortedMap()
}
private fun nativeJournal(epoch: SessionEpoch, expected: Path, ref: BoundFile?, cap: Int = 16_777_216): List<JsonNode> {
    require(ref != null && ref.path == expected.toAbsolutePath().normalize().toString())
    return strictCapturedLines(ref, maxBytes = cap, maxRows = 4096, maxLine = 65_536).also { rows ->
        require(rows.size >= 2 && rows.all { it.path("run_id").asText() == epoch.nativeSessionId })
    }
}
internal fun verifySelectedOriginalClassOrigins(mailbox: Path, ref: BoundFile,
    epochId: String, nativeSessionId: String, selectedServer: String, scope: BoundFile,
    classPins: JsonNode, exactControlPaths: Boolean) {
    val capsFile = mailbox.resolve("capabilities.json")
    require(Files.isRegularFile(capsFile, NOFOLLOW_LINKS) && !Files.isSymbolicLink(capsFile) &&
        Files.size(capsFile) in 2..1_048_576)
    val capabilities = strictCapturedJson(capturedEvidenceBytes(
        BoundFile(capsFile.toAbsolutePath().normalize().toString(), Hashes.sha256(capsFile)), 1_048_576))
    require(capabilities.path("run_id").asText() == nativeSessionId)
    val pointer = capabilities.path("runtime_class_origins")
    reviewedFields(pointer, setOf("schema", "native_session_id", "epoch_id", "scope_id",
        "candidate_declared_sha256", "path", "bytes", "sha256", "complete",
        "transformed_live_bytes_attested", "mixin_application_proven"))
    val config = reviewedBytes(scope)
    val scopeId = config.path("scope_id").asText()
    val file = mailbox.resolve("runtime-origins/$nativeSessionId.json").toAbsolutePath().normalize()
    require(pointer.path("schema").asText() == "bc.playtest_runtime_class_origins.v1" &&
        pointer.path("native_session_id").asText() == nativeSessionId &&
        pointer.path("epoch_id").asText() == epochId && pointer.path("scope_id").asText() == scopeId &&
        pointer.path("candidate_declared_sha256").asText() == selectedServer &&
        pointer.path("path").asText() == "runtime-origins/$nativeSessionId.json" &&
        pointer.path("bytes").isIntegralNumber && pointer.path("bytes").asLong() in 2..1_048_576 &&
        listOf("transformed_live_bytes_attested", "mixin_application_proven").all {
            key -> pointer.path(key).isBoolean && !pointer.path(key).asBoolean()
        } && pointer.path("sha256").asText().matches(shaPattern) &&
        pointer.path("bytes").asLong() == Files.size(file))
    require(ref.path == file.toString() && ref.sha256 == pointer.path("sha256").asText())
    val receipt = reviewedBytes(ref)
    reviewedFields(receipt, setOf("schema", "native_session_id", "epoch_id", "scope_id",
        "candidate_declared_sha256", "candidate_declaration_verified_by_native", "classes", "complete",
        "transformed_live_bytes_attested", "mixin_application_proven", "natural_origin_proven_by_native"))
    require(receipt.path("schema") == pointer.path("schema") &&
        receipt.path("native_session_id") == pointer.path("native_session_id") &&
        receipt.path("epoch_id") == pointer.path("epoch_id") &&
        receipt.path("scope_id") == pointer.path("scope_id") &&
        receipt.path("candidate_declared_sha256") == pointer.path("candidate_declared_sha256") &&
        receipt.path("complete") == pointer.path("complete") &&
        listOf("candidate_declaration_verified_by_native", "transformed_live_bytes_attested",
            "mixin_application_proven", "natural_origin_proven_by_native").all { key ->
            receipt.path(key).isBoolean && !receipt.path(key).asBoolean() })
    val classes = receipt.path("classes")
    require(classes.isArray && classes.size() in 12..64 &&
        classes.map { it.path("binary_name").asText() }.distinct().size == classes.size())
    classPins.forEach { pin ->
        val name = pin.path("class_internal_name").asText().replace('/', '.')
        val row = classes.single { it.path("binary_name").asText() == name }
        require(row.path("complete").isBoolean && row.path("complete").asBoolean() &&
            row.path("resource_origin_kind").asText() == "untransformed_class_entry_NOT_runtime_transform" &&
            row.path("transformed_live_bytes_attested").isBoolean &&
            !row.path("transformed_live_bytes_attested").asBoolean() &&
            (!exactControlPaths || row.path("ordinary_artifact_path").asText() == pin.path("artifact_path").asText()) &&
            row.path("artifact_sha256").asText() == pin.path("artifact_sha256").asText() &&
            row.path("untransformed_class_resource_sha256").asText() == pin.path("class_file_sha256").asText() &&
            row.path("class_loader_binary_name").asText().isNotBlank() &&
            row.path("code_source_uri").asText().startsWith("file:") &&
            row.path("class_resource_uri").asText().isNotBlank()) {
            "selected actual loader ORIGINAL class differs from reviewed provider/support/vanilla pin"
        }
    }
    require(Hashes.sha256(file) == ref.sha256 && Files.size(file) == pointer.path("bytes").asLong())
    // This only attests ORIGINAL loader entries. Per-route real controls and reviewed bounded
    // provider path still gate supported scope; never treat this as a transformed live digest.
}

private fun reviewedControl(storeRoot: Path, control: JsonNode, selectedClient: String,
    selectedServer: String, supportSha: String, providerSha: String, classPins: JsonNode,
    positive: Boolean): String {
    val common = setOf("schema", "control_id", "bindings", "input_checkpoint", "output_checkpoint",
        "epoch", "scope", "status", "actor_uuid", "case_id", "trial_id", "action_id",
        "action_sequence", "position", "expected_source_state", "expected_final_state", "runtime_origins")
    reviewedFields(control, common + if (positive) setOf("expected_transitions") else setOf("expected_negative_gap"))
    require(control.path("schema").asText() == "bc.playtest_actual_mutation_control_capture.v1")
    val id = control.path("control_id").asText()
    require(id in if (positive) stage1SafePositiveControlIds else stage1RequiredNegativeControlIds)
    val bindings = suiteMapper.treeToValue(control.path("bindings"), CheckpointBindings::class.java)
    require(bindings.purpose == ExecutionPurpose.DIAGNOSTIC.wire &&
        bindings.candidate["client_sha256"] == selectedClient &&
        bindings.candidate["server_sha256"] == selectedServer && bindings.supportSha256 == supportSha)
    val store = CheckpointStore(storeRoot, bindings)
    val input = suiteMapper.treeToValue(control.path("input_checkpoint"), CheckpointRef::class.java)
    val output = suiteMapper.treeToValue(control.path("output_checkpoint"), CheckpointRef::class.java)
    val before = store.verify(input); val after = store.verify(output)
    val epoch = suiteMapper.treeToValue(control.path("epoch"), SessionEpoch::class.java)
    require(before.bindings == bindings && after.bindings == bindings && after.parent == input &&
        after.sourceEpoch == epoch.id && after.closedEpoch == epoch.closure &&
        epoch.inputCheckpoint == input && epoch.restored?.reference == input &&
        store.verifyRestoreReceipt(epoch.restored) == epoch.restored &&
        epoch.status == "passed" && epoch.purpose == bindings.purpose &&
        epoch.campaignId == bindings.campaignId && epoch.lineageId == bindings.lineageId &&
        epoch.closure?.nativeSessionId == epoch.nativeSessionId &&
        epoch.closure.processCleanupComplete && epoch.closure.survivingPids.isEmpty()) {
        "control did not actually run and seal the reviewed disposable source epoch"
    }
    val actor = control.path("actor_uuid").asText()
    require(runCatching { UUID.fromString(actor).toString() == actor }.getOrDefault(false) &&
        control.path("case_id").asText().matches(idPattern) && control.path("trial_id").asText().matches(idPattern) &&
        control.path("action_id").asText().matches(idPattern))
    val evidence = Path.of(epoch.closure.evidence.path).toAbsolutePath().normalize().parent
    val mailbox = evidence.resolve("native")
    val requests = nativeJournal(epoch, mailbox.resolve("requests.jsonl"), epoch.nativeRequests)
    val observations = nativeJournal(epoch, mailbox.resolve("observations.jsonl"), epoch.nativeObservations)
    val actualActors = observations.filter { it.path("request_sequence").asLong(-1) == 1L &&
        it.path("kind").asText() == "completed" && it.path("driver").asText() == "inventory.discover" }
        .single().path("payload").path("player_uuids")
    require(actualActors.isArray && actualActors.size() == 1 && actualActors[0].asText() == actor)
    val actionSequence = control.path("action_sequence")
    require(actionSequence.isIntegralNumber && actionSequence.canConvertToLong() && actionSequence.asLong() >= 2)
    val actionRow = requests.single { it.path("sequence").asLong(-1) == actionSequence.asLong() }
    val action = suiteMapper.treeToValue(actionRow, NativeRequest::class.java)
    val terminal = observations.filter { it.path("request_sequence").asLong(-1) == action.sequence &&
        it.path("kind").asText() == "completed" }.single().path("payload")
    val context = if (positive) suiteMapper.treeToValue(action.parameters.path("witness_context"),
        WitnessContext::class.java) else null
    require(action.runId == epoch.nativeSessionId && action.caseId == control.path("case_id").asText() &&
        action.driver == control.path("action_id").asText())
    if (positive) require(context != null && context.trialId == control.path("trial_id").asText() &&
        context.actorUuid == actor && context.requestSequence == action.sequence &&
        context.nativeSessionId == epoch.nativeSessionId && context.epochId == epoch.id &&
        context.campaignId == bindings.campaignId && context.lineageId == bindings.lineageId &&
        context.capabilityId == action.driver && context.purpose == bindings.purpose &&
        context.source == "server" && action.parameters.path("control_id").asText() == id)
    else require(id == "state_container_exposed" && // B's single real getter is NOT eleven negative routes.
        action.driver == "block_mutation.control_expose_states_v1" &&
        action.parameters.fieldNames().asSequence().toSet() ==
            setOf("scope_id", "selector_id", "dimension", "x", "y", "z") &&
        action.parameters.path("selector_id").asText() == id)
    val pos = suiteMapper.treeToValue(control.path("position"), ScopedBlockPosition::class.java)
    val scope = reviewedBound(control.path("scope"))
    val status = reviewedBound(control.path("status"))
    val scopeNode = reviewedBytes(scope)
    require(status.path == mailbox.resolve("mutations/${scopeNode.path("scope_id").asText()}.status.json")
        .toAbsolutePath().normalize().toString()) { "control terminal is not its actual owned scope/epoch status" }
    verifySelectedOriginalClassOrigins(mailbox, reviewedBound(control.path("runtime_origins")),
        epoch.id, epoch.nativeSessionId, selectedServer, scope, classPins, exactControlPaths = true)
    val savedBefore = readSealedBlockBaseline(store, input, pos.dimension, pos.x, pos.y, pos.z)
    val savedAfter = readSealedBlockBaseline(store, output, pos.dimension, pos.x, pos.y, pos.z)
    require((savedBefore.blockId to savedBefore.properties) == reviewedState(control.path("expected_source_state")) &&
        (savedAfter.blockId to savedAfter.properties) == reviewedState(control.path("expected_final_state")))
    val providerPath = before.snapshotDirectory.let { Path.of(it).resolve("server/mods") }
    require(Files.isDirectory(providerPath, NOFOLLOW_LINKS) && before.files.any {
        it.root == "server" && it.path.startsWith("mods/") && !it.directory.orFalse() && it.sha256 == providerSha
    }) { "selected provider mod artifact absent from exact sealed disposable control source" }
    if (positive) {
        val transitions = control.path("expected_transitions").also { require(it.isArray && it.size() in 1..64) }
            .map { row ->
                reviewedFields(row, setOf("ordinal", "observed_path", "old_state", "new_state"))
                ReviewedMutationTransition(row.path("ordinal").asInt(-1), row.path("observed_path").asText(),
                    reviewedState(row.path("old_state")), reviewedState(row.path("new_state")))
            }
        replayPositiveMutationControl(store, input, output, scope, mailbox, status, bindings,
            epoch.id, epoch.nativeSessionId, id, pos, transitions, action, terminal, requireNotNull(context),
            control.path("action_id").asText(), actor)
    } else {
        // The negative must have a REAL bounded terminal/stop gap; an absent/nonexecuted test
        // or a native 'natural:false' string is not a successful negative control.
        val reason = control.path("expected_negative_gap").asText()
        require(reason.isNotBlank() && reason.length <= 256)
        val stop = reviewedBytes(status)
        val trace = mailbox.resolve("mutations/${scopeNode.path("scope_id").asText()}.jsonl")
        val declaration = terminal.path("resulting_prefix")
        require(reason == "mutable_state_container_exposed" &&
            action.parameters.path("scope_id").asText() == scopeNode.path("scope_id").asText() &&
            action.parameters.path("dimension").asText() == pos.dimension &&
            action.parameters.path("x").asInt(Int.MIN_VALUE) == pos.x &&
            action.parameters.path("y").asInt(Int.MIN_VALUE) == pos.y &&
            action.parameters.path("z").asInt(Int.MIN_VALUE) == pos.z &&
            terminal.path("schema").asText() == "bc.playtest_mutation_external_getter_negative.v1" &&
            terminal.path("selector_id").asText() == id &&
            terminal.path("selector_reviewed_by_native").asBoolean(true) == false &&
            terminal.path("scope_id").asText() == scopeNode.path("scope_id").asText() &&
            terminal.path("epoch_id").asText() == epoch.id &&
            terminal.path("native_session_id").asText() == epoch.nativeSessionId &&
            terminal.path("source_checkpoint_sha256").asText() == input.receipt.sha256 &&
            terminal.path("expected_scope_failure").asText() == reason &&
            terminal.path("real_callsite").asText() ==
                "LevelChunkSection.getStates() from native diagnostic action" &&
            terminal.path("natural_origin_proven_by_native").asBoolean(true) == false &&
            terminal.path("negative_control_certifies_candidate_provider_by_native").asBoolean(true) == false &&
            declaration.path("scope_id").asText() == scopeNode.path("scope_id").asText() &&
            declaration.path("scope_failure").asBoolean(false) &&
            declaration.path("failure_reason").asText() == reason &&
            stop.path("scope_failure").isBoolean && stop.path("scope_failure").asBoolean() &&
            stop.path("failure_reason").asText() == reason &&
            stop.path("scope_id").asText() == scopeNode.path("scope_id").asText() &&
            stop.path("source_checkpoint_id").asText() == input.id &&
            stop.path("scope_file_sha256").asText() == scope.sha256 &&
            stop.path("path").asText() == "mutations/${scopeNode.path("scope_id").asText()}.jsonl" &&
            Files.isRegularFile(trace, NOFOLLOW_LINKS) && !Files.isSymbolicLink(trace) &&
            Files.size(trace) in 0..16_777_216 && stop.path("bytes").asLong(-1) == Files.size(trace) &&
            stop.path("sha256").asText() == Hashes.sha256(trace) &&
            stop.path("stop_tick").asLong(-1) > 0 &&
            stop.path("closed_monotonic_nanos").asLong(-1) > 0 &&
            stop.path("lifecycle_context") == suiteMapper.valueToTree<JsonNode>(mapOf(
                "campaign_id" to bindings.campaignId, "lineage_id" to bindings.lineageId,
                "epoch_id" to epoch.id, "native_session_id" to epoch.nativeSessionId,
                "purpose" to bindings.purpose, "source" to "controller_prelaunch")) &&
            terminal.path("block_mutation_pre_action_prefix").path("scope_failure").isBoolean &&
            !terminal.path("block_mutation_pre_action_prefix").path("scope_failure").asBoolean() &&
            terminal.path("block_mutation_pre_action_prefix").path("scope_id") == declaration.path("scope_id") &&
            declaration.path("natural_origin_proven_by_native").isBoolean &&
            !declaration.path("natural_origin_proven_by_native").asBoolean()) {
            "negative route lacked actual matching failed native scope and strict action binding"
        }
    }
    return id
}
private fun Boolean?.orFalse() = this == true

/**
 * Actual pre-world acceptance, not a shape-only success flag. Each reviewed control ref must
 * resolve to its OWN Store-verified disposable epoch, actor-bound action, B raw transition/stop,
 * and saved source/output. Missing runner/controls/artifacts keep the gate CLOSED.
 */
internal fun verifyReviewedStage1Coverage(selector: ReviewedTargetSelector, clientZip: Path,
    serverZip: Path, supportJar: Path): ReviewedStage1Coverage {
    require(selector.schema == "bc.playtest_target_selector.v2" &&
        selector.source == TargetSource.NATURAL_UNMODIFIED_BLOCK && selector.inputCheckpointId == "origin" &&
        selector.capabilityId == "target.observe.loaded_v1")
    val profile = requireNotNull(selector.coverageProfile)
    val node = reviewedBytes(profile)
    reviewedFields(node, setOf("schema", "reviewed_by", "reviewed_at", "selector_sha256",
        "candidate_client_zip_sha256", "candidate_server_zip_sha256", "test_support_sha256",
        "provider_mod_id", "provider_jar_sha256", "class_pins", "control_store_root",
        "positive_controls", "negative_controls", "supported_adapter_id", "supported_scope_schema",
        "unsupported_paths"))
    require(node.path("schema").asText() == "bc.playtest_reviewed_stage1_scope_profile.v1" &&
        node.path("reviewed_by").isTextual && node.path("reviewed_by").asText().length in 3..128 &&
        runCatching { !Instant.parse(node.path("reviewed_at").asText()).isAfter(Instant.now()) }.getOrDefault(false) &&
        node.path("candidate_client_zip_sha256").asText() == Hashes.sha256(clientZip) &&
        node.path("candidate_server_zip_sha256").asText() == Hashes.sha256(serverZip) &&
        node.path("test_support_sha256").asText() == Hashes.sha256(supportJar) &&
        // Exclude the profile REF itself: hashing the selector including profile SHA would
        // demand an impossible self-referential SHA-256 fixed point.
        node.path("selector_sha256").asText() ==
            com.bettercontent.tests.suite.jsonFingerprint(
                suiteMapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(selector)
                    .apply { remove("coverage_profile") }) &&
        node.path("provider_mod_id").asText() == selector.provider &&
        node.path("provider_jar_sha256").asText().matches(shaPattern) &&
        node.path("supported_adapter_id").asText() == "forge_1.20.1-47.4.22_full_chunk_palette_v1" &&
        node.path("supported_scope_schema").asText() == "bc.playtest_mutation_supported_full_chunk_palette.v1" &&
        node.path("unsupported_paths").isArray &&
        node.path("unsupported_paths").map { it.asText() } == mutationUnsupportedPaths) {
        "reviewed supported profile did not bind exact selector/selected candidate/support/provider"
    }
    val storeRoot = Path.of(node.path("control_store_root").asText()).toAbsolutePath().normalize()
    require(storeRoot.toString() == node.path("control_store_root").asText() &&
        Files.isDirectory(storeRoot, NOFOLLOW_LINKS) && !Files.isSymbolicLink(storeRoot))
    val pins = node.path("class_pins")
    require(pins.isArray && pins.size() in 12..64)
    val classes = pins.map { row ->
        reviewedFields(row, setOf("class_internal_name", "artifact_path", "artifact_sha256", "class_file_sha256"))
        val className = row.path("class_internal_name").asText()
        val artifact = Path.of(row.path("artifact_path").asText()).toAbsolutePath().normalize()
        val expectedArchive = row.path("artifact_sha256").asText()
        val expectedClass = row.path("class_file_sha256").asText()
        require(className.matches(Regex("[A-Za-z0-9_/\\$]{1,256}")) &&
            artifact.toString() == row.path("artifact_path").asText() &&
            expectedArchive.matches(shaPattern) && expectedClass.matches(shaPattern) &&
            Files.isRegularFile(artifact, NOFOLLOW_LINKS) && !Files.isSymbolicLink(artifact) &&
            Files.size(artifact) in 2..134_217_728 && Hashes.sha256(artifact) == expectedArchive)
        ZipFile(artifact.toFile()).use { zip ->
            val entry = zip.getEntry("$className.class") ?: error("reviewed provider/vanilla/mixin class entry missing")
            require(!entry.isDirectory && entry.size in 1..1_048_576)
            val classBytes = zip.getInputStream(entry).use { it.readNBytes(1_048_577) }
            require(classBytes.size.toLong() == entry.size &&
                MessageDigest.getInstance("SHA-256").digest(classBytes)
                    .joinToString("") { "%02x".format(it) } == expectedClass)
        }
        className to expectedClass
    }
    val classPins = classes.toMap()
    val required = setOf("net/minecraft/server/level/ChunkMap", "net/minecraft/world/level/chunk/ChunkAccess",
        "net/minecraft/world/level/chunk/LevelChunk", "net/minecraft/world/level/chunk/LevelChunkSection",
        "net/minecraft/world/level/chunk/PalettedContainer",
        "net/minecraft/world/level/chunk/storage/ChunkSerializer",
        "com/bettercontent/playtestsupport/BlockMutationTrace",
        "com/bettercontent/playtestsupport/BlockMutationOwnership",
        "com/bettercontent/playtestsupport/mixin/LevelChunkMutationObserverMixin",
        "com/bettercontent/playtestsupport/mixin/LevelChunkPaletteMutationObserverMixin",
        "com/bettercontent/playtestsupport/mixin/PalettedContainerMutationObserverMixin",
        "com/bettercontent/playtestsupport/mixin/ChunkAccessSectionsExposureMixin")
    require(classPins.size == classes.size && classPins.keys.containsAll(required) &&
        pins.any { it.path("artifact_sha256").asText() == node.path("test_support_sha256").asText() } &&
        pins.any { it.path("artifact_sha256").asText() == node.path("provider_jar_sha256").asText() }) {
        "candidate/provider/support class-pin roster omitted a watched source or serializer exemption"
    }
    val positive = node.path("positive_controls"); val negative = node.path("negative_controls")
    require(positive.isArray && positive.size() == stage1SafePositiveControlIds.size &&
        negative.isArray && negative.size() == stage1RequiredNegativeControlIds.size)
    val actualPositive = positive.map { ref -> reviewedControl(storeRoot, reviewedBytes(reviewedBound(ref)),
        node.path("candidate_client_zip_sha256").asText(), node.path("candidate_server_zip_sha256").asText(),
        node.path("test_support_sha256").asText(), node.path("provider_jar_sha256").asText(), pins, true) }
    val actualNegative = negative.map { ref -> reviewedControl(storeRoot, reviewedBytes(reviewedBound(ref)),
        node.path("candidate_client_zip_sha256").asText(), node.path("candidate_server_zip_sha256").asText(),
        node.path("test_support_sha256").asText(), node.path("provider_jar_sha256").asText(), pins, false) }
    require(actualPositive.toSet() == stage1SafePositiveControlIds && actualPositive.size == actualPositive.toSet().size &&
        actualNegative.toSet() == stage1RequiredNegativeControlIds && actualNegative.size == actualNegative.toSet().size &&
        Hashes.sha256(Path.of(profile.path)) == profile.sha256) {
        "actual candidate-bound positive/negative raw controls incomplete or re-used"
    }
    return ReviewedStage1Coverage(profile, node.path("selector_sha256").asText(),
        node.path("candidate_server_zip_sha256").asText(), node.path("provider_mod_id").asText(),
        node.path("provider_jar_sha256").asText(), actualPositive.toSet(), actualNegative.toSet(), classPins)
}
