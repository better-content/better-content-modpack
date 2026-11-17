package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.VerifiedRawEvidence
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Entirely synthetic typed v2 rows, never a real attack, approved source, or verified SHA chain. */
class IncomingHitControlDriverTest {
    private val player = "123e4567-e89b-42d3-a456-426614174000"
    private val monster = "123e4567-e89b-42d3-a456-426614174002"
    private val observer = "123e4567-e89b-42d3-a456-426614174003"
    private val trace = "a".repeat(64)
    private fun obj(text: String) = suiteMapper.readTree(text) as ObjectNode
    private fun target() = obj("""{"player_uuid":"$player","dimension":"minecraft:overworld",
        "observer_id":"$observer","source_entity_uuid":"$monster","source_entity_type":"minecraft:zombie",
        "damage_type_id":"minecraft:mob_attack","reviewed_vanilla_profile_id":"synthetic-only",
        "deadline_ticks":100,"reviewed_vanilla_only":true}""")
    private fun trusted() = IncomingHitControlDriver.AuthenticatedVanillaProfile("synthetic-only", monster,
        "minecraft:zombie", "minecraft:mob_attack", true, true)
    private fun evidence(absorption: Float = 0f): Pair<JsonNode, List<DriverObservation>> {
        val target = target()
        val before = obj("""{"uuid":"$player","dimension":"minecraft:overworld","health":20.0}""")
        val afterHealth = if (absorption >= 10f) 20f else 20f - (10f - absorption)
        val afterAbsorption = (absorption - 10f).coerceAtLeast(0f)
        val after = obj("""{"uuid":"$player","dimension":"minecraft:overworld","health":$afterHealth}""")
        val context = obj("""{"campaign_id":"synthetic-only","native_session_id":"server-sess"}""")
        val loaded = obj("""{"source_entity_uuid":"$monster","source_entity_type":"minecraft:zombie",
            "actor_uuid":"$player","dimension":"minecraft:overworld","source_loaded":true,
            "actor_health":20.0,"actor_absorption":$absorption,"server_tick":10,
            "natural_threat_proven_by_native":false}""")
        val arm = suiteMapper.createObjectNode().put("schema", "bc.playtest_passive_incoming_hit_arm.v2")
            .put("observer_id", observer).put("actor_uuid", player).put("dimension", "minecraft:overworld")
            .put("arm_request_sequence", 2).put("arm_action_ordinal", 2).put("deadline_tick", 110)
            .put("context_certified_by_native", false).apply {
                set<JsonNode>("controller_declared_arm_context", context)
                set<JsonNode>("actual_loaded_source_and_actor", loaded)
            }
        val amount = 10f
        val net = (amount - absorption).coerceAtLeast(0f)
        val head = obj("""{"actual_hurt_argument_before_ForgeHooks":10.0,"health":20.0,
            "absorption":$absorption,"source_entity_uuid":"$monster","direct_entity_uuid":"$monster"}""")
        val pre = obj("""{"actual_ForgeHooks_onLivingHurt_return":10.0,"health":20.0,
            "absorption":$absorption,"resistance_amplifier":null,"damage_type":"minecraft:mob_attack",
            "bypasses_armor":false,"bypasses_effects":false,"bypasses_resistance":false,
            "bypasses_enchantments":false,"source_entity_uuid":"$monster","direct_entity_uuid":"$monster"}""")
        val arguments = obj("""{"actual_CombatRules_amount":10.0,
            "actual_CombatRules_armor_after_hurtArmor":0.0,"actual_CombatRules_toughness_after_hurtArmor":0.0}""")
        val armor = obj("""{"actual_getDamageAfterArmorAbsorb_return":10.0}""")
        val helperInvoke = obj("""{"original_helper_callsite_reached":true}""")
        val helperReturn = obj("""{"actual_EnchantmentHelper_getDamageProtection_return":0}""")
        val branch = obj("""{"original_return_branch":"after_helper","original_return_value":10.0}""")
        val magic = obj("""{"actual_getDamageAfterMagicAbsorb_return":10.0}""")
        val preDamage = obj("""{"actual_onLivingDamage_argument":$net,"health":20.0,
            "absorption_after_original_first_setter":$afterAbsorption}""")
        val postDamage = obj("""{"actual_ForgeHooks_onLivingDamage_return":$net}""")
        val end = obj("""{"health":$afterHealth,"absorption":$afterAbsorption}""")
        val entries = listOf("actual_player_actuallyHurt_head" to head,
            "actual_ForgeHooks_onLivingHurt_return_before_armor" to pre,
            "actual_CombatRules_getDamageAfterAbsorb_arguments" to arguments,
            "actual_armor_return_before_magic" to armor,
            "actual_enchantment_protection_helper_invoke" to helperInvoke,
            "actual_enchantment_protection_helper_return" to helperReturn,
            "actual_magic_original_return_branch" to branch,
            "actual_magic_return_before_absorption" to magic,
            "actual_before_ForgeHooks_onLivingDamage" to preDamage,
            "actual_ForgeHooks_onLivingDamage_return" to postDamage,
            "actual_player_actuallyHurt_return" to end,
            "actual_player_health_absorption_at_server_tick_END" to end)
        val rows = entries.mapIndexed { index, (kind, observed) ->
            suiteMapper.createObjectNode().put("schema", "bc.playtest_incoming_hit_trace.v2")
                .put("event_index", index + 1).put("kind", kind)
                .put("tick", if (index == entries.lastIndex) 12 else 11)
                .put("native_session_id", "server-sess").put("actual_actor_uuid", player)
                .put("source_entity_uuid", monster).put("source_entity_type", "minecraft:zombie")
                .put("arm_request_sequence", 2).put("context_certified_by_native", false).apply {
                    if (index == 0) putNull("prior_row_sha256") else put("prior_row_sha256", trace)
                    set<JsonNode>("controller_declared_arm_context", context)
                    set<JsonNode>("observed", observed)
                } as JsonNode
        }
        val terminal = arm.deepCopy().put("schema", "bc.playtest_passive_incoming_hit.v2")
            .put("source_entity_uuid", monster).put("source_entity_type", "minecraft:zombie")
            .put("actual_hit_count", 1).put("actual_hurt_return_count", 1)
            .put("actual_armor_operands_call_count", 1)
            .put("actual_enchantment_helper_invoke_count", 1)
            .put("actual_enchantment_helper_return_count", 1)
            .put("actual_enchantment_protection_count", 0)
            .put("actual_magic_return_branch_count", 1).put("actual_magic_return_branch", "after_helper")
            .put("hit_tick", 11).put("terminal_tick", 12).put("observation_complete", true)
            .put("natural_threat_proven_by_native", false)
            .put("vanilla_damage_policy_proven_by_native", false)
            .put("external_damage_modifiers_proven_by_native", false)
        mapOf("arm_state" to loaded, "actual_hit_head" to head, "actual_pre_armor" to pre,
            "actual_armor_operands" to arguments, "actual_pre_magic" to armor,
            "actual_post_magic" to magic, "actual_pre_damage_hook" to preDamage,
            "actual_post_damage_hook" to postDamage, "actual_after_method" to end,
            "actual_after_server_tick_end" to end).forEach { (key, value) -> terminal.set<JsonNode>(key, value) }
        terminal.set<JsonNode>("incoming_hit_trace_file", suiteMapper.createObjectNode()
            .put("schema", "bc.playtest_incoming_hit_trace.v2")
            .put("path", "incoming-hit/000000000002.jsonl").put("bytes", 1000).put("sha256", trace)
            .put("records", rows.size).put("arm_request_sequence", 2)
            .put("actual_native_session_id", "server-sess").put("natural_threat_proven_by_native", false))
        val payloads = listOf(before, arm, terminal, after)
        val plan = IncomingHitControlDriver.plan(target)
        return target to plan.mapIndexed { index, step ->
            DriverObservation(step.name, step.action.id, step.action.source, "completed", payloads[index],
                if (index == 2) mapOf("incoming_hit" to
                    VerifiedRawEvidence(BoundFile("/synthetic-not-a-real-file", trace), rows)) else emptyMap())
        }
    }
    @Test fun `one genuine passive arm and pending await have no forged hurt spawn or tick action`() {
        val plan = IncomingHitControlDriver.plan(target())
        assertEquals(listOf("player.snapshot", "player.incoming_damage.arm_v2",
            "player.incoming_damage.await_v2", "player.snapshot"), plan.map { it.action.id })
        assertTrue(plan.none { it.action.id in setOf("player.hurt", "entity.spawn", "wait.ticks") })
        assertEquals("@controller-witness-context-v1", plan[1].action.parameters.path("witness_context").asText())
        assertEquals("@arm-request-sequence", plan[2].action.parameters.path("arm_request_sequence").asText())
        assertEquals(null, DriverRegistry.resolve(IncomingHitControlDriver.caseId))
    }
    @Test fun `complete measured health hit and fully absorbed zero are diagnostic only after A-authenticated profile`() {
        for (absorption in listOf(0f, 20f)) {
            val (p, observations) = evidence(absorption)
            assertEquals("passed", IncomingHitControlDriver.evaluateWithAuthenticatedProfile(p, observations, trusted()).state)
            assertTrue(evaluateCase(IncomingHitControlDriver, p, observations) is DriverOutcome.Fatal)
        }
    }
    @Test fun `malformed string and fractional counters ticks and flags cannot coerce into v2 witness`() {
        val alterations: List<(List<DriverObservation>) -> Unit> = listOf(
            { rows -> (rows[2].payload as ObjectNode).put("actual_hit_count", "1") },
            { rows -> (rows[2].payload as ObjectNode).put("actual_enchantment_helper_invoke_count", 1.0) },
            { rows -> (rows[2].payload as ObjectNode).put("actual_magic_return_branch_count", "1") },
            { rows -> (rows[2].payload as ObjectNode).put("observation_complete", "true") },
            { rows -> (rows[2].payload as ObjectNode).put("terminal_tick", 12.0) },
            { rows -> (rows[2].payload.path("incoming_hit_trace_file") as ObjectNode).put("records", 12.0) },
            { rows -> (rows[1].payload as ObjectNode).put("arm_request_sequence", "2") },
            { rows -> (rows[2].rawEvidence.getValue("incoming_hit").records[0] as ObjectNode)
                .put("event_index", "1") },
            { rows -> (rows[2].rawEvidence.getValue("incoming_hit").records[0] as ObjectNode)
                .put("tick", 11.0) },
            { rows -> (rows[2].rawEvidence.getValue("incoming_hit").records[4]
                .path("observed") as ObjectNode).put("original_helper_callsite_reached", "true") },
        )
        alterations.forEach { alter ->
            val (target, rows) = evidence(20f)
            alter(rows)
            assertThrows(IllegalArgumentException::class.java) {
                IncomingHitControlDriver.evaluateWithAuthenticatedProfile(target, rows, trusted())
            }
        }
    }
    @Test fun `complete stage contradiction fails but unverified profile raw and missing helper block`() {
        val (p, observed) = evidence()
        val stage = observed[2].payload.path("actual_pre_magic") as ObjectNode
        val rawStage = observed[2].rawEvidence.getValue("incoming_hit").records.single {
            it.path("kind").asText() == "actual_armor_return_before_magic" }.path("observed") as ObjectNode
        stage.put("actual_getDamageAfterArmorAbsorb_return", 9f)
        rawStage.put("actual_getDamageAfterArmorAbsorb_return", 9f)
        assertEquals("failed", IncomingHitControlDriver.evaluateWithAuthenticatedProfile(p, observed, trusted()).state)
        val unreviewed = evidence()
        assertThrows(IllegalArgumentException::class.java) {
            IncomingHitControlDriver.evaluateWithAuthenticatedProfile(unreviewed.first, unreviewed.second,
                trusted().copy(externalModifiersProvenAbsentByA = false))
        }
        val missing = evidence()
        (missing.second[2].payload as ObjectNode).put("actual_enchantment_helper_return_count", 0)
        assertThrows(IllegalArgumentException::class.java) {
            IncomingHitControlDriver.evaluateWithAuthenticatedProfile(missing.first, missing.second, trusted())
        }
    }
}
