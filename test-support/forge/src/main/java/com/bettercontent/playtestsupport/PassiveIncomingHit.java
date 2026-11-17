package com.bettercontent.playtestsupport;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One actual nonlethal vanilla actuallyHurt pipeline. No damage, policy, helper or tick is invoked by observer. */
final class PassiveIncomingHit {
    static final String ARM = "player.incoming_damage.arm_v2", AWAIT = "player.incoming_damage.await_v2";
    static final String TRACE = "bc.playtest_incoming_hit_trace.v2";
    final PassiveObserverBinding binding;
    final UUID sourceId;
    final String sourceType;
    final JsonObject armState;
    private final Monster sourceEntity;
    private final List<String> rows = new ArrayList<>();
    private String priorSha, failure;
    private int bytes, entries, returns, preArmorCalls, armorArgsCalls, preMagicCalls, preHookCalls, postHookCalls;
    private int enchantInvokes, enchantCalls, magicReturnBranches;
    private boolean magicActive, finished;
    private String actualMagicReturnBranch;
    private DamageSource activeDamageSource;
    private Integer actualEnchantmentProtection;
    private JsonObject beforeHit, preArmor, armorOperands, preMagic, postMagic, preDamageHook, postDamageHook, afterMethod, afterServerEnd;
    private long hitTick = -1, terminalTick = -1;

    private PassiveIncomingHit(PassiveObserverBinding binding, Monster source, String type, JsonObject arm) {
        this.binding = binding; sourceEntity = source; sourceId = source.getUUID(); sourceType = type; armState = arm;
    }
    static PassiveIncomingHit arm(Protocol.Request request, JsonObject p, ServerPlayer actor, long tick) {
        Protocol.only(p, "player_uuid", "dimension", "observer_id", "expected_source_entity_uuid",
                "expected_source_type", "deadline_ticks", "witness_context");
        PassiveObserverBinding bound = PassiveObserverBinding.arm(request, p, actor.getUUID(),
                actor.serverLevel().dimension().location().toString(), tick, 200);
        if (Protocol.number(p, "deadline_ticks") < 2 || actor instanceof FakePlayer ||
                actor.connection == null || actor.connection.connection == null ||
                !actor.connection.connection.isConnected() || actor.connection.connection.isMemoryConnection() ||
                !actor.gameMode.getGameModeForPlayer().isSurvival())
            throw new IllegalArgumentException("incoming hit needs real connected remote Survival actor");
        String id = Protocol.string(p, "expected_source_entity_uuid");
        if (!id.equals(UUID.fromString(id).toString())) throw new IllegalArgumentException("source UUID not canonical");
        var found = actor.serverLevel().getEntity(UUID.fromString(id)); // index lookup, never loads/generates chunk
        if (!(found instanceof Monster monster) || !monster.isAlive() ||
                actor.serverLevel().getChunkSource().getChunkNow(monster.blockPosition().getX() >> 4,
                        monster.blockPosition().getZ() >> 4) == null || actor.distanceToSqr(monster) > 32 * 32)
            throw new IllegalArgumentException("already loaded nearby living hostile source required");
        String type = ForgeRegistries.ENTITY_TYPES.getKey(monster.getType()).toString();
        if (!type.equals(Protocol.string(p, "expected_source_type")))
            throw new IllegalArgumentException("declared source entity type differs from loaded hostile");
        JsonObject state = new JsonObject(); state.addProperty("source_entity_uuid", id);
        state.addProperty("source_entity_type", type); state.addProperty("actor_uuid", actor.getUUID().toString());
        state.addProperty("dimension", bound.dimension); state.addProperty("source_loaded", true);
        state.addProperty("actor_health", actor.getHealth()); state.addProperty("actor_absorption", actor.getAbsorptionAmount());
        state.addProperty("server_tick", tick); state.addProperty("natural_threat_proven_by_native", false);
        return new PassiveIncomingHit(bound, monster, type, state);
    }
    void fail(String why) { if (failure == null) failure = why; }
    DamageSource activeSource() { return activeDamageSource; }
    boolean isActor(LivingEntity actor) { return actor instanceof ServerPlayer player && binding.actorId.equals(player.getUUID()); }
    private boolean bound(ServerPlayer actor, DamageSource source) {
        return source != null && source.getEntity() == sourceEntity && source.getDirectEntity() == sourceEntity &&
                actor.serverLevel().getEntity(sourceId) == sourceEntity && sourceEntity.isAlive() &&
                actor.serverLevel().dimension().location().toString().equals(binding.dimension);
    }
    void begin(ServerPlayer actor, DamageSource source, float originalAmount, long tick) {
        if (!isActor(actor)) return;
        entries++;
        if (entries != 1 || !bound(actor, source) || tick > binding.deadlineTick ||
                !Float.isFinite(originalAmount) || originalAmount <= 0) fail("hit_source_actor_amount_or_count_unbound");
        activeDamageSource = source; hitTick = tick;
        beforeHit = new JsonObject(); beforeHit.addProperty("actual_hurt_argument_before_ForgeHooks", originalAmount);
        beforeHit.addProperty("health", actor.getHealth()); beforeHit.addProperty("absorption", actor.getAbsorptionAmount());
        beforeHit.addProperty("source_entity_uuid", source.getEntity() == null ? (String) null : source.getEntity().getUUID().toString());
        beforeHit.addProperty("direct_entity_uuid", source.getDirectEntity() == null ? (String) null : source.getDirectEntity().getUUID().toString());
        row("actual_player_actuallyHurt_head", tick, beforeHit);
    }
    /** The fload2 local immediately BEFORE actual getDamageAfterArmorAbsorb, AFTER ForgeHooks.onLivingHurt return. */
    void beforeArmor(ServerPlayer actor, DamageSource source, float actualHurtReturn, long tick) {
        if (!isActor(actor)) return;
        preArmorCalls++;
        if (source != activeDamageSource || preArmorCalls != 1 || !Float.isFinite(actualHurtReturn) || actualHurtReturn <= 0)
            fail("actual_living_hurt_return_missing_nonpositive_or_unbound");
        preArmor = new JsonObject(); preArmor.addProperty("actual_ForgeHooks_onLivingHurt_return", actualHurtReturn);
        preArmor.addProperty("health", actor.getHealth()); preArmor.addProperty("absorption", actor.getAbsorptionAmount());
        var effect = actor.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (effect == null) preArmor.add("resistance_amplifier", JsonNull.INSTANCE);
        else preArmor.addProperty("resistance_amplifier", effect.getAmplifier());
        preArmor.addProperty("damage_type", source.typeHolder().unwrapKey()
                .map(key -> key.location().toString()).orElse("unkeyed"));
        preArmor.addProperty("bypasses_armor", source.typeHolder().is(DamageTypeTags.BYPASSES_ARMOR));
        preArmor.addProperty("bypasses_effects", source.typeHolder().is(DamageTypeTags.BYPASSES_EFFECTS));
        preArmor.addProperty("bypasses_resistance", source.typeHolder().is(DamageTypeTags.BYPASSES_RESISTANCE));
        preArmor.addProperty("bypasses_enchantments", source.typeHolder().is(DamageTypeTags.BYPASSES_ENCHANTMENTS));
        preArmor.addProperty("source_entity_uuid", source.getEntity() == null ? (String) null : source.getEntity().getUUID().toString());
        preArmor.addProperty("direct_entity_uuid", source.getDirectEntity() == null ? (String) null : source.getDirectEntity().getUUID().toString());
        if (beforeHit == null || preArmor.get("health").getAsFloat() != beforeHit.get("health").getAsFloat() ||
                preArmor.get("absorption").getAsFloat() != beforeHit.get("absorption").getAsFloat())
            fail("health_or_absorption_changed_inside_unreviewed_ForgeHurt_hook");
        row("actual_ForgeHooks_onLivingHurt_return_before_armor", tick, preArmor);
    }
    /** Original LivingEntity armor-path CombatRules arguments AFTER hurtArmor; @ModifyArgs does not set them. */
    void actualArmorOperands(ServerPlayer actor, DamageSource source, float input, float armor, float toughness, long tick) {
        if (!isActor(actor)) return;
        armorArgsCalls++;
        if (source != activeDamageSource || armorArgsCalls != 1 || preArmor == null ||
                preArmor.get("bypasses_armor").getAsBoolean() || !Float.isFinite(input) || input <= 0 ||
                !Float.isFinite(armor) || !Float.isFinite(toughness) || armor < 0 || toughness < 0)
            fail("actual_CombatRules_armor_arguments_missing_or_unbound");
        armorOperands = new JsonObject(); armorOperands.addProperty("actual_CombatRules_amount", input);
        armorOperands.addProperty("actual_CombatRules_armor_after_hurtArmor", armor);
        armorOperands.addProperty("actual_CombatRules_toughness_after_hurtArmor", toughness);
        row("actual_CombatRules_getDamageAfterAbsorb_arguments", tick, armorOperands);
    }
    void beforeMagic(ServerPlayer actor, DamageSource source, float armorReturn, long tick) {
        if (!isActor(actor)) return;
        preMagicCalls++;
        if (source != activeDamageSource || preMagicCalls != 1 || !Float.isFinite(armorReturn) || armorReturn < 0)
            fail("actual_armor_phase_return_missing_or_invalid");
        preMagic = new JsonObject(); preMagic.addProperty("actual_getDamageAfterArmorAbsorb_return", armorReturn);
        row("actual_armor_return_before_magic", tick, preMagic);
    }
    void magicBegin(ServerPlayer actor, DamageSource source) {
        if (!isActor(actor)) return;
        if (source != activeDamageSource || magicActive) fail("actual_magic_phase_unbalanced_or_unbound");
        magicActive = true;
    }
    /** Exact original inherited magic method INVOKE site; no helper invocation by the observer. */
    void enchantmentInvoke(ServerPlayer actor, DamageSource source, long tick) {
        if (!isActor(actor)) return;
        enchantInvokes++;
        if (!magicActive || source != activeDamageSource || enchantInvokes != 1)
            fail("actual_enchantment_helper_invoke_unbound_or_duplicate");
        JsonObject actual = new JsonObject(); actual.addProperty("original_helper_callsite_reached", true);
        row("actual_enchantment_protection_helper_invoke", tick, actual);
    }
    /** Four exact pinned FRETURN sites: bypass effects, early nonpositive, bypass enchant, after helper. */
    void magicReturnBranch(ServerPlayer actor, DamageSource source, String branch, float value, long tick) {
        if (!isActor(actor)) return;
        magicReturnBranches++;
        if (source != activeDamageSource || magicReturnBranches != 1 || !Float.isFinite(value) ||
                !List.of("bypass_effects", "nonpositive_before_helper", "bypass_enchantments", "after_helper").contains(branch))
            fail("actual_magic_original_return_branch_unbound_or_duplicate");
        actualMagicReturnBranch = branch;
        JsonObject actual = new JsonObject(); actual.addProperty("original_return_branch", branch);
        actual.addProperty("original_return_value", value);
        row("actual_magic_original_return_branch", tick, actual);
    }
    void enchantmentReturn(DamageSource source, int result, long tick) {
        if (!magicActive || source != activeDamageSource) return;
        enchantCalls++;
        if (enchantInvokes != 1) fail("actual_helper_return_without_original_invoke");
        if (enchantCalls != 1 || result < 0 || result > 20) fail("actual_enchantment_protection_count_unbounded");
        actualEnchantmentProtection = result;
        JsonObject actual = new JsonObject(); actual.addProperty("actual_EnchantmentHelper_getDamageProtection_return", result);
        row("actual_enchantment_protection_helper_return", tick, actual);
    }
    void magicEnd(ServerPlayer actor, DamageSource source, float actualReturn, long tick) {
        if (!isActor(actor)) return;
        if (!magicActive || source != activeDamageSource || !Float.isFinite(actualReturn) || actualReturn < 0)
            fail("actual_magic_phase_return_unpaired_or_invalid");
        magicActive = false;
        postMagic = new JsonObject(); postMagic.addProperty("actual_getDamageAfterMagicAbsorb_return", actualReturn);
        row("actual_magic_return_before_absorption", tick, postMagic);
    }
    /** Existing first local f3, just BEFORE original onLivingDamage; return untouched to caller. */
    void beforeDamageHook(ServerPlayer actor, DamageSource source, float actualPreHook, long tick) {
        if (!isActor(actor)) return;
        preHookCalls++;
        if (activeDamageSource == null || source != activeDamageSource || preHookCalls != 1 || magicActive ||
                !nonnegativeDamageStage(actualPreHook))
            fail("actual_pre_damage_hook_amount_missing_or_invalid");
        preDamageHook = new JsonObject(); preDamageHook.addProperty("actual_onLivingDamage_argument", actualPreHook);
        preDamageHook.addProperty("health", actor.getHealth());
        preDamageHook.addProperty("absorption_after_original_first_setter", actor.getAbsorptionAmount());
        row("actual_before_ForgeHooks_onLivingDamage", tick, preDamageHook);
    }
    /** Existing second local f3 AFTER original ForgeHooks.onLivingDamage, returns unchanged float. */
    void afterDamageHook(ServerPlayer actor, DamageSource source, float actualReturn, long tick) {
        if (!isActor(actor)) return;
        postHookCalls++;
        if (activeDamageSource == null || source != activeDamageSource || postHookCalls != 1 ||
                !nonnegativeDamageStage(actualReturn))
            fail("actual_post_damage_hook_return_missing_or_invalid");
        postDamageHook = new JsonObject(); postDamageHook.addProperty("actual_ForgeHooks_onLivingDamage_return", actualReturn);
        row("actual_ForgeHooks_onLivingDamage_return", tick, postDamageHook);
    }
    void returned(ServerPlayer actor, DamageSource source, long tick) {
        if (!isActor(actor)) return;
        returns++;
        if (returns != 1 || source != activeDamageSource || magicActive)
            fail("actual_hurt_return_unbound_or_duplicate");
        afterMethod = new JsonObject(); afterMethod.addProperty("health", actor.getHealth());
        afterMethod.addProperty("absorption", actor.getAbsorptionAmount());
        row("actual_player_actuallyHurt_return", tick, afterMethod);
    }
    void serverEnd(ServerPlayer actor, long tick) {
        if (finished) return;
        if (magicActive) { fail("unbalanced_magic_phase_at_server_tick_end"); magicActive = false; }
        if (tick >= binding.deadlineTick && returns != 1) fail("incoming_hit_arm_deadline_without_complete_one_hit");
        if (failure != null || returns != 1) return;
        if (actor.serverLevel().getEntity(sourceId) != sourceEntity || !sourceEntity.isAlive())
            fail("incoming_hit_bound_source_unloaded_or_dead_before_server_end");
        terminalTick = tick;
        afterServerEnd = new JsonObject(); afterServerEnd.addProperty("health", actor.getHealth());
        afterServerEnd.addProperty("absorption", actor.getAbsorptionAmount());
        if (afterMethod == null || preArmor == null || preMagic == null || postMagic == null ||
                preDamageHook == null || postDamageHook == null ||
                beforeHit == null || !completeCounts(entries, returns, preArmorCalls, armorArgsCalls,
                preMagicCalls, preHookCalls, postHookCalls, enchantInvokes, enchantCalls, magicReturnBranches,
                actualMagicReturnBranch, magicActive,
                preArmor != null && preArmor.get("bypasses_armor").getAsBoolean()) ||
                !afterMethod.equals(afterServerEnd) || !Float.isFinite(actor.getHealth()) || actor.getHealth() <= 0)
            fail("incoming_hit_missing_stage_or_post_hit_state_drift_or_lethal");
        row("actual_player_health_absorption_at_server_tick_END", tick, afterServerEnd);
        finished = true;
    }
    /** Zero can be an original fully-absorbed/resisted hit; never infer a missing callback from it. */
    static boolean nonnegativeDamageStage(float value) { return Float.isFinite(value) && value >= 0; }
    /** Capture cardinality only: never an expected armor/resistance/damage oracle. */
    static boolean completeCounts(int entries, int returns, int preArmor, int armorArgs, int preMagic,
                                  int preHook, int postHook, int helperInvokes, int helperReturns,
                                  int branchCalls, String actualBranch, boolean magicActive, boolean bypassArmor) {
        // Exact original RETURN-site marker plus INVOKE+RETURN; final zero alone NEVER proves a skipped helper.
        boolean skipped = actualBranch != null &&
                List.of("bypass_effects", "nonpositive_before_helper", "bypass_enchantments").contains(actualBranch) &&
                helperInvokes == 0 && helperReturns == 0;
        boolean invokedAndReturned = "after_helper".equals(actualBranch) && helperInvokes == 1 && helperReturns == 1;
        return entries == 1 && returns == 1 && preArmor == 1 && branchCalls == 1 &&
                armorArgs == (bypassArmor ? 0 : 1) && preMagic == 1 &&
                preHook == 1 && postHook == 1 && !magicActive && (skipped || invokedAndReturned);
    }
    boolean terminal() { return finished || failure != null; }
    JsonObject await(Protocol.Request request, JsonObject p, ServerPlayer actor, long tick) {
        Protocol.only(p, "player_uuid", "dimension", "observer_id", "arm_request_sequence", "arm_action_ordinal", "witness_context");
        binding.await(request, p, actor.getUUID(), actor.serverLevel().dimension().location().toString());
        serverEnd(actor, tick);
        JsonObject result = binding.echo(); result.addProperty("schema", "bc.playtest_passive_incoming_hit.v2");
        result.add("arm_state", armState.deepCopy());
        result.add("actual_hit_head", beforeHit == null ? JsonNull.INSTANCE : beforeHit.deepCopy());
        result.add("actual_pre_armor", preArmor == null ? JsonNull.INSTANCE : preArmor.deepCopy());
        result.add("actual_armor_operands", armorOperands == null ? JsonNull.INSTANCE : armorOperands.deepCopy());
        result.add("actual_pre_magic", preMagic == null ? JsonNull.INSTANCE : preMagic.deepCopy());
        result.add("actual_post_magic", postMagic == null ? JsonNull.INSTANCE : postMagic.deepCopy());
        result.add("actual_pre_damage_hook", preDamageHook == null ? JsonNull.INSTANCE : preDamageHook.deepCopy());
        result.add("actual_post_damage_hook", postDamageHook == null ? JsonNull.INSTANCE : postDamageHook.deepCopy());
        result.add("actual_after_method", afterMethod == null ? JsonNull.INSTANCE : afterMethod.deepCopy());
        result.add("actual_after_server_tick_end", afterServerEnd == null ? JsonNull.INSTANCE : afterServerEnd.deepCopy());
        result.addProperty("source_entity_uuid", sourceId.toString()); result.addProperty("source_entity_type", sourceType);
        result.addProperty("actual_hit_count", entries); result.addProperty("actual_hurt_return_count", returns);
        result.addProperty("actual_armor_operands_call_count", armorArgsCalls);
        result.addProperty("actual_enchantment_helper_invoke_count", enchantInvokes);
        result.addProperty("actual_enchantment_helper_return_count", enchantCalls);
        result.addProperty("actual_magic_return_branch_count", magicReturnBranches);
        if (actualMagicReturnBranch != null) result.addProperty("actual_magic_return_branch", actualMagicReturnBranch);
        else result.add("actual_magic_return_branch", JsonNull.INSTANCE);
        if (enchantCalls == 1 && actualEnchantmentProtection != null)
            result.addProperty("actual_enchantment_protection_count", actualEnchantmentProtection);
        else result.add("actual_enchantment_protection_count", JsonNull.INSTANCE);
        result.addProperty("hit_tick", hitTick); result.addProperty("terminal_tick", terminalTick);
        if (tick > binding.deadlineTick) fail("incoming_hit_await_after_arm_deadline");
        boolean complete = finished && failure == null && rows.size() <= 64 && bytes <= 1_000_000;
        result.addProperty("observation_complete", complete);
        if (!complete) result.addProperty("unsupported_reason", failure == null ? "incomplete_actual_hit_pipeline" : failure);
        result.addProperty("natural_threat_proven_by_native", false);
        result.addProperty("vanilla_damage_policy_proven_by_native", false);
        result.addProperty("external_damage_modifiers_proven_by_native", false);
        return result;
    }
    List<String> rows() { return List.copyOf(rows); }
    private void row(String kind, long tick, JsonObject observed) {
        if (rows.size() >= 64 || bytes >= 1_000_000) { fail("incoming_hit_raw_trace_cap_exceeded"); return; }
        JsonObject row = new JsonObject(); row.addProperty("schema", TRACE); row.addProperty("event_index", rows.size() + 1);
        row.addProperty("kind", kind); row.addProperty("tick", tick);
        row.addProperty("native_session_id", binding.declared.get("native_session_id").getAsString());
        row.addProperty("actual_actor_uuid", binding.actorId.toString());
        row.addProperty("source_entity_uuid", sourceId.toString()); row.addProperty("source_entity_type", sourceType);
        row.addProperty("arm_request_sequence", binding.arm.sequence());
        row.add("controller_declared_arm_context", binding.declared.deepCopy());
        row.addProperty("context_certified_by_native", false);
        if (observed != null) row.add("observed", observed.deepCopy());
        if (priorSha == null) row.add("prior_row_sha256", JsonNull.INSTANCE);
        else row.addProperty("prior_row_sha256", priorSha);
        byte[] raw = (row + "\n").getBytes(StandardCharsets.UTF_8);
        if (raw.length > 128_000 || bytes + raw.length > 1_000_000) { fail("incoming_hit_raw_row_byte_cap_exceeded"); return; }
        try { priorSha = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)); }
        catch (Exception failure) { fail("incoming_hit_raw_hash_failed"); return; }
        rows.add(row.toString()); bytes += raw.length;
    }
}
