package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.ExternalRawEvidenceDriver
import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.RawEvidenceRequest
import com.bettercontent.tests.suite.oracles.VanillaIncomingDamageOracle
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import kotlin.math.abs

/** UNREGISTERED one real hostile→real player melee diagnostic. Never forces hurt/spawn/ticks/AI.
 * A must authenticate reviewed source, provider/equipment/weapon, candidate and at most three
 * preselected real threat opportunities; neither this control nor native proves natural threat. */
internal object IncomingHitControlDriver : CaseDriver, ExternalRawEvidenceDriver {
    override val caseId = "control.combat.incoming-one-real-hit"
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private val sha = Regex("[0-9a-f]{64}")
    private val name = Regex("[a-z0-9][a-z0-9_.-]*")
    private fun integer(node: JsonNode, range: IntRange): Boolean =
        node.isIntegralNumber && node.canConvertToInt() && node.asInt() in range
    private fun long(node: JsonNode): Boolean = node.isIntegralNumber && node.canConvertToLong()
    private fun flag(node: JsonNode, value: Boolean): Boolean = node.isBoolean && node.asBoolean() == value
    private data class Target(val player: String, val dimension: String, val observer: String,
        val sourceUuid: String, val sourceType: String, val damageType: String,
        val reviewedProfileId: String, val deadline: Int)
    private fun parse(p: JsonNode): Target {
        require(p.isObject && p.fieldNames().asSequence().toSet() ==
            setOf("player_uuid", "dimension", "observer_id", "source_entity_uuid", "source_entity_type",
                "damage_type_id", "reviewed_vanilla_profile_id", "deadline_ticks", "reviewed_vanilla_only")) {
            "unreviewed incoming-hit target fields"
        }
        fun text(field: String) = p.path(field).also { require(it.isTextual) { "missing $field" } }.asText()
        val player = text("player_uuid"); val observer = text("observer_id"); val source = text("source_entity_uuid")
        val dimension = text("dimension"); val type = text("source_entity_type")
        val damage = text("damage_type_id"); val profile = text("reviewed_vanilla_profile_id")
        val ticks = p.path("deadline_ticks")
        require(listOf(player, observer, source).all { runCatching { UUID.fromString(it).toString() == it }
            .getOrDefault(false) } && player != source && listOf(dimension, type, damage).all(resource::matches) &&
            name.matches(profile) && integer(ticks, 2..200) &&
            p.path("reviewed_vanilla_only").isBoolean && p.path("reviewed_vanilla_only").asBoolean()) {
            "actual source, reviewed narrow vanilla profile or bounded opportunity missing"
        }
        return Target(player, dimension, observer, source, type, damage, profile, ticks.asInt())
    }
    override fun rawEvidenceRequests(trial: TrialRequest): List<RawEvidenceRequest> {
        require(trial.caseId == caseId && trial.provisionedTargets.isEmpty()) { "foreign incoming raw trial" }
        parse(trial.parameters)
        return listOf(RawEvidenceRequest("await-one-real-hit", "incoming_hit", "/incoming_hit_trace_file",
            maximumBytes = 1_000_000, maximumRecords = 64, maximumLineBytes = 128_000))
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val p = parse(parameters)
        val player = suiteMapper.createObjectNode().put("player_uuid", "@observed-player-uuid")
        val arm = player.deepCopy().put("dimension", p.dimension).put("observer_id", p.observer)
            .put("expected_source_entity_uuid", p.sourceUuid).put("expected_source_type", p.sourceType)
            .put("deadline_ticks", p.deadline).put("witness_context", "@controller-witness-context-v1")
        val await = player.deepCopy().put("dimension", p.dimension).put("observer_id", p.observer)
            .put("arm_request_sequence", "@arm-request-sequence").put("arm_action_ordinal", "@arm-action-ordinal")
            .put("witness_context", "@controller-witness-context-v1")
        return listOf(DriverStep("player-before", DriverAction("player.snapshot", player.deepCopy())),
            DriverStep("arm-existing-real-hostile", DriverAction("player.incoming_damage.arm_v2", arm)),
            DriverStep("await-one-real-hit", DriverAction("player.incoming_damage.await_v2", await, p.deadline + 2)),
            DriverStep("player-after", DriverAction("player.snapshot", player.deepCopy())))
    }
    /** Trusted only when A independently binds the reviewed candidate, equipment and provider
     * absence to this actual source and selected-candidate manifest before entering this method. */
    data class AuthenticatedVanillaProfile(val reviewedProfileId: String, val sourceUuid: String,
        val sourceType: String, val damageType: String, val actualWeaponBoundByA: Boolean,
        val externalModifiersProvenAbsentByA: Boolean)
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict =
        throw IllegalArgumentException("A-authenticated actual weapon/provider/profile binding required; native receipt alone is not a damage policy")
    fun evaluateWithAuthenticatedProfile(parameters: JsonNode, observations: List<DriverObservation>,
        authenticated: AuthenticatedVanillaProfile): MeasurementVerdict {
        val p = parse(parameters)
        require(authenticated.reviewedProfileId == p.reviewedProfileId &&
            authenticated.sourceUuid == p.sourceUuid && authenticated.sourceType == p.sourceType &&
            authenticated.damageType == p.damageType && authenticated.actualWeaponBoundByA &&
            authenticated.externalModifiersProvenAbsentByA) {
            "A-reviewed actual attacker weapon, vanilla equipment and external modifier absence unverified"
        }
        require(validateTerminalSequence(plan(parameters), observations) == null) {
            "missing real one-hit passive terminal"
        }
        val before = observations[0].payload; val arm = observations[1].payload
        val hit = observations[2].payload; val after = observations[3].payload
        require(before.path("uuid").asText() == p.player && after.path("uuid").asText() == p.player &&
            before.path("dimension").asText() == p.dimension && after.path("dimension").asText() == p.dimension &&
            arm.path("schema").asText() == "bc.playtest_passive_incoming_hit_arm.v2" &&
            hit.path("schema").asText() == "bc.playtest_passive_incoming_hit.v2" &&
            listOf(arm, hit).all { it.path("observer_id").asText() == p.observer &&
                it.path("actor_uuid").asText() == p.player && it.path("dimension").asText() == p.dimension &&
                it.path("context_certified_by_native").isBoolean &&
                !it.path("context_certified_by_native").asBoolean() } &&
            listOf("arm_request_sequence", "arm_action_ordinal", "controller_declared_arm_context",
                "deadline_tick").all { hit.path(it) == arm.path(it) } &&
            long(arm.path("arm_request_sequence")) && arm.path("arm_request_sequence").asLong() > 0 &&
            integer(arm.path("arm_action_ordinal"), 1..Int.MAX_VALUE) &&
            arm.path("actual_loaded_source_and_actor").let { source ->
                source.path("source_entity_uuid").asText() == p.sourceUuid &&
                    source.path("source_entity_type").asText() == p.sourceType &&
                    source.path("actor_uuid").asText() == p.player &&
                    source.path("dimension").asText() == p.dimension &&
                    flag(source.path("source_loaded"), true) &&
                    source.path("natural_threat_proven_by_native").isBoolean &&
                    !source.path("natural_threat_proven_by_native").asBoolean() &&
                    long(source.path("server_tick")) && long(arm.path("deadline_tick")) &&
                    source.path("server_tick").asLong() in 0..(Long.MAX_VALUE - p.deadline) &&
                    arm.path("deadline_tick").asLong() == source.path("server_tick").asLong() + p.deadline
            } && hit.path("arm_state") == arm.path("actual_loaded_source_and_actor") &&
            hit.path("source_entity_uuid").asText() == p.sourceUuid &&
            hit.path("source_entity_type").asText() == p.sourceType &&
            integer(hit.path("actual_hit_count"), 1..1) &&
            integer(hit.path("actual_hurt_return_count"), 1..1) &&
            integer(hit.path("actual_armor_operands_call_count"), 0..1) &&
            integer(hit.path("actual_enchantment_helper_invoke_count"), 0..1) &&
            integer(hit.path("actual_enchantment_helper_return_count"),
                hit.path("actual_enchantment_helper_invoke_count").asInt()..
                    hit.path("actual_enchantment_helper_invoke_count").asInt()) &&
            integer(hit.path("actual_magic_return_branch_count"), 1..1) &&
            hit.path("actual_magic_return_branch").asText() in setOf("bypass_effects",
                "nonpositive_before_helper", "bypass_enchantments", "after_helper") &&
            flag(hit.path("observation_complete"), true) && !hit.has("unsupported_reason") &&
            listOf("natural_threat_proven_by_native", "vanilla_damage_policy_proven_by_native",
                "external_damage_modifiers_proven_by_native").all {
                hit.path(it).isBoolean && !hit.path(it).asBoolean()
            } && long(hit.path("hit_tick")) && long(hit.path("terminal_tick")) &&
            hit.path("hit_tick").asLong() in arm.path("actual_loaded_source_and_actor")
                .path("server_tick").asLong()..hit.path("terminal_tick").asLong() &&
            hit.path("terminal_tick").asLong() <= arm.path("deadline_tick").asLong()) {
            "unsupported or incomplete actual bound remote-player hostile hit"
        }
        val pointer = hit.path("incoming_hit_trace_file")
        require(pointer.path("schema").asText() == "bc.playtest_incoming_hit_trace.v2" &&
            pointer.path("arm_request_sequence") == arm.path("arm_request_sequence") &&
            pointer.path("path").asText() ==
                "incoming-hit/${arm.path("arm_request_sequence").asLong().toString().padStart(12, '0')}.jsonl" &&
            long(pointer.path("bytes")) && pointer.path("bytes").asLong() in 1..1_000_000 &&
            integer(pointer.path("records"), 10..13) && sha.matches(pointer.path("sha256").asText("")) &&
            pointer.path("actual_native_session_id").asText("").isNotEmpty() &&
            pointer.path("natural_threat_proven_by_native").isBoolean &&
            !pointer.path("natural_threat_proven_by_native").asBoolean()) { "unbound actual incoming raw pointer" }
        val raw = observations[2].rawEvidence["incoming_hit"]
        require(raw != null && raw.file.sha256 == pointer.path("sha256").asText() &&
            raw.records.size == pointer.path("records").asInt()) { "verified raw incoming hit absent" }
        val armorCalls = hit.path("actual_armor_operands_call_count").asInt()
        val helperInvokes = hit.path("actual_enchantment_helper_invoke_count").asInt()
        val branch = hit.path("actual_magic_return_branch").asText()
        val headKinds = listOf("actual_player_actuallyHurt_head", "actual_ForgeHooks_onLivingHurt_return_before_armor") +
            (if (armorCalls == 1) listOf("actual_CombatRules_getDamageAfterAbsorb_arguments") else emptyList()) +
            listOf("actual_armor_return_before_magic") +
            (if (helperInvokes == 1) listOf("actual_enchantment_protection_helper_invoke",
                "actual_enchantment_protection_helper_return") else emptyList())
        val magicKinds = listOf("actual_magic_original_return_branch", "actual_magic_return_before_absorption")
        val tailKinds = listOf("actual_before_ForgeHooks_onLivingDamage", "actual_ForgeHooks_onLivingDamage_return",
            "actual_player_actuallyHurt_return", "actual_player_health_absorption_at_server_tick_END")
        val actualKinds = raw.records.map { it.path("kind").asText() }
        val terminalKeys = mapOf("actual_player_actuallyHurt_head" to "actual_hit_head",
            "actual_ForgeHooks_onLivingHurt_return_before_armor" to "actual_pre_armor",
            "actual_CombatRules_getDamageAfterAbsorb_arguments" to "actual_armor_operands",
            "actual_armor_return_before_magic" to "actual_pre_magic",
            "actual_magic_return_before_absorption" to "actual_post_magic",
            "actual_before_ForgeHooks_onLivingDamage" to "actual_pre_damage_hook",
            "actual_ForgeHooks_onLivingDamage_return" to "actual_post_damage_hook",
            "actual_player_actuallyHurt_return" to "actual_after_method",
            "actual_player_health_absorption_at_server_tick_END" to "actual_after_server_tick_end")
        fun observed(kind: String) = raw.records.single { it.path("kind").asText() == kind }.path("observed")
        require((actualKinds == headKinds + magicKinds + tailKinds ||
            actualKinds == headKinds + magicKinds.reversed() + tailKinds) &&
            raw.records.withIndex().all { (i, row) ->
                row.path("schema").asText() == "bc.playtest_incoming_hit_trace.v2" &&
                integer(row.path("event_index"), i + 1..i + 1) &&
                row.path("native_session_id") == pointer.path("actual_native_session_id") &&
                row.path("actual_actor_uuid").asText() == p.player &&
                row.path("source_entity_uuid").asText() == p.sourceUuid &&
                row.path("source_entity_type").asText() == p.sourceType &&
                row.path("arm_request_sequence") == arm.path("arm_request_sequence") &&
                row.path("controller_declared_arm_context") == arm.path("controller_declared_arm_context") &&
                row.path("context_certified_by_native").isBoolean &&
                !row.path("context_certified_by_native").asBoolean() &&
                long(row.path("tick")) &&
                row.path("tick").asLong() in hit.path("hit_tick").asLong()..hit.path("terminal_tick").asLong() &&
                (i == 0 && row.path("prior_row_sha256").isNull ||
                    i > 0 && sha.matches(row.path("prior_row_sha256").asText(""))) &&
                (terminalKeys[row.path("kind").asText()] == null ||
                    row.path("observed") == hit.path(terminalKeys.getValue(row.path("kind").asText())))
            } && raw.records.map { it.path("tick").asLong() }.zipWithNext().all { (a, b) -> b >= a } &&
            observed("actual_magic_original_return_branch").let {
                it.path("original_return_branch").asText() == branch &&
                    it.path("original_return_value") == hit.path("actual_post_magic")
                        .path("actual_getDamageAfterMagicAbsorb_return")
            } && (helperInvokes == 0 && hit.path("actual_enchantment_protection_count").isNull ||
                helperInvokes == 1 &&
                    observed("actual_enchantment_protection_helper_invoke")
                        .path("original_helper_callsite_reached").let { flag(it, true) } &&
                    observed("actual_enchantment_protection_helper_return")
                        .path("actual_EnchantmentHelper_getDamageProtection_return") ==
                        hit.path("actual_enchantment_protection_count")) &&
            (branch == "after_helper") == (helperInvokes == 1) &&
            hit.path("actual_pre_armor").path("bypasses_armor").isBoolean &&
            (armorCalls == 0) == hit.path("actual_pre_armor").path("bypasses_armor").asBoolean() &&
            (armorCalls == 0) == hit.path("actual_armor_operands").isNull) {
            "original Player hurt/Forge/CombatRules/helper/branch/post-hit raw row chain incomplete"
        }
        val head = hit.path("actual_hit_head"); val pre = hit.path("actual_pre_armor")
        val operands = hit.path("actual_armor_operands")
        val armor = hit.path("actual_pre_magic"); val magic = hit.path("actual_post_magic")
        val beforeDamage = hit.path("actual_pre_damage_hook"); val afterDamage = hit.path("actual_post_damage_hook")
        val method = hit.path("actual_after_method"); val end = hit.path("actual_after_server_tick_end")
        fun scalar(row: JsonNode, key: String): Float {
            val value = row.path(key)
            require(value.isNumber && value.asDouble().isFinite()) { "actual $key not finite" }
            return value.asDouble().toFloat()
        }
        val original = scalar(head, "actual_hurt_argument_before_ForgeHooks")
        val rawHurt = scalar(pre, "actual_ForgeHooks_onLivingHurt_return")
        val preDamage = scalar(beforeDamage, "actual_onLivingDamage_argument")
        val postDamage = scalar(afterDamage, "actual_ForgeHooks_onLivingDamage_return")
        val headHealth = scalar(head, "health"); val headAbsorption = scalar(head, "absorption")
        require(head.path("source_entity_uuid").asText() == p.sourceUuid &&
            head.path("direct_entity_uuid").asText() == p.sourceUuid &&
            pre.path("source_entity_uuid").asText() == p.sourceUuid &&
            pre.path("direct_entity_uuid").asText() == p.sourceUuid &&
            pre.path("damage_type").asText() == p.damageType &&
            scalar(pre, "health") == headHealth && scalar(pre, "absorption") == headAbsorption &&
            scalar(arm.path("actual_loaded_source_and_actor"), "actor_health") == headHealth &&
            scalar(arm.path("actual_loaded_source_and_actor"), "actor_absorption") == headAbsorption &&
            original == rawHurt && preDamage == postDamage &&
            armorCalls == 1 && operands.isObject &&
            scalar(operands, "actual_CombatRules_amount") == rawHurt &&
            branch == "after_helper" && helperInvokes == 1 &&
            integer(hit.path("actual_enchantment_protection_count"), 0..20)) {
            "real source/drift or unknown Forge event/provider damage modifier: vanilla profile unsupported"
        }
        val amplifier = pre.path("resistance_amplifier")
        require(amplifier.isNull || integer(amplifier, 0..3))
        val input = VanillaIncomingDamageOracle.Input(rawHurt,
            scalar(operands, "actual_CombatRules_armor_after_hurtArmor"),
            scalar(operands, "actual_CombatRules_toughness_after_hurtArmor"),
            amplifier.takeUnless { it.isNull }?.asInt(),
            headHealth, headAbsorption, sourceTagsObserved = listOf("bypasses_armor", "bypasses_effects",
                "bypasses_resistance", "bypasses_enchantments").all { pre.path(it).isBoolean },
            bypassesArmor = pre.path("bypasses_armor").asBoolean(),
            bypassesEffects = pre.path("bypasses_effects").asBoolean(),
            bypassesResistance = pre.path("bypasses_resistance").asBoolean(),
            bypassesEnchantments = pre.path("bypasses_enchantments").asBoolean(),
            enchantmentProtectionObserved = hit.path("actual_enchantment_protection_count").asInt(),
            // Only the A-authenticated candidate/equipment/provider profile authorizes this support flag.
            externalModifiersKnownAbsent = authenticated.externalModifiersProvenAbsentByA,
            forgeHurtUnmodified = true, forgeDamageUnmodified = true)
        val predicted = VanillaIncomingDamageOracle.compute(input)
        require(predicted is VanillaIncomingDamageOracle.Result.Supported) {
            "unsupported incoming vanilla profile: ${(predicted as VanillaIncomingDamageOracle.Result.Unsupported).reason}"
        }
        val consistent = abs(predicted.postArmor - scalar(armor, "actual_getDamageAfterArmorAbsorb_return")) <= 0.0001f &&
            abs(predicted.postResistance - scalar(magic, "actual_getDamageAfterMagicAbsorb_return")) <= 0.0001f &&
            abs(predicted.healthDamage - preDamage) <= 0.0001f &&
            abs(predicted.healthDamage - postDamage) <= 0.0001f &&
            abs(predicted.absorptionAfter - scalar(beforeDamage, "absorption_after_original_first_setter")) <= 0.0001f &&
            scalar(beforeDamage, "health") == headHealth &&
            abs(predicted.healthAfter - scalar(method, "health")) <= 0.0001f &&
            abs(predicted.absorptionAfter - scalar(method, "absorption")) <= 0.0001f &&
            method == end && abs(predicted.healthAfter - scalar(end, "health")) <= 0.0001f &&
            abs(predicted.absorptionAfter - scalar(end, "absorption")) <= 0.0001f &&
            abs(predicted.healthAfter - scalar(after, "health")) <= 0.0001f
        return MeasurementVerdict(if (consistent) "passed" else "failed",
            if (consistent) "one real direct hostile hit bounded vanilla damage diagnostic; source/weapon/provider and natural-threat family NOT qualified"
            else "complete same-source Player hurt pipeline contradicts independently computed vanilla armor/resistance/absorption",
            mapOf("diagnostic_only_not_natural_threat" to true, "reviewed_profile_id_unverified_by_native" to p.reviewedProfileId,
                "incoming_trace_sha256" to raw.file.sha256, "actual_damage_type" to p.damageType))
    }
}
