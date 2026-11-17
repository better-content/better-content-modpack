package com.bettercontent.tests.suite.oracles

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.checkpoints.CapturedSealedVanillaInventory
import com.bettercontent.tests.suite.checkpoints.NbtList
import com.bettercontent.tests.suite.checkpoints.NbtValue
import com.bettercontent.tests.suite.checkpoints.SealedInventoryStack
import com.bettercontent.tests.suite.checkpoints.SealedPlayerInventory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** UNRUN synthetic conditional math/typed-NBT regressions. None authenticates a recipe, raw B
 * method rows, Store parent/child or an installed TCon provider. No world/gameplay claim. */
class TconstructToolDurabilityOracleTest {
    private val hash = "a".repeat(64)
    private val afterHash = "b".repeat(64)
    private val actor = "123e4567-e89b-42d3-a456-426614174000"
    private val parts = List(3) { "tconstruct:flint" } // synthetic candidate, NOT observed assembled parts
    private val traits = listOf(TconstructModifierEntry("tconstruct:pierce", 1),
        TconstructModifierEntry("tconstruct:jagged", 3)) // order/levels synthetic, NOT frozen loaded roster
    private fun byte(value: Int) = NbtValue(1, value.toByte())
    private fun int(value: Int) = NbtValue(3, value)
    private fun string(value: String) = NbtValue(8, value)
    private fun compound(vararg fields: Pair<String, NbtValue>) = NbtValue(10, mapOf(*fields))
    private fun stack(damage: Int, broken: Int, extra: Pair<String, NbtValue>? = null): NbtValue {
        val fields = linkedMapOf<String, NbtValue>(
            "Damage" to int(damage), "tic_broken" to byte(broken),
            "tic_materials" to NbtValue(9, NbtList(8, parts.map(::string))),
            "tic_modifiers" to NbtValue(9, NbtList(10, traits.map { entry ->
                compound("name" to string(entry.id), "level" to int(entry.level)) })),
            "tic_stats" to compound("tconstruct:durability" to NbtValue(5, 130.7f.toRawBits())))
        if (extra != null) fields[extra.first] = extra.second
        return compound("id" to string("tconstruct:pickaxe"), "Count" to byte(1), "tag" to NbtValue(10, fields))
    }
    private fun captured(stack: NbtValue?, canonical: String): CapturedSealedVanillaInventory {
        val stacks = if (stack == null) emptyList() else listOf(SealedInventoryStack(0, "tconstruct:pickaxe", 1,
            "bc.playtest.stack-nbt.v1", canonical, "bc.playtest.stack-content.v1", hash))
        val inventory = SealedPlayerInventory("synthetic", actor, BoundFile("synthetic", hash),
            stacks, 0, stacks)
        return CapturedSealedVanillaInventory(inventory, if (stack == null) emptyMap() else mapOf(0 to stack))
    }
    private fun target(before: Int = 4, harvest: String = "primary") = TconstructPickaxeWearTarget(
        "tconstruct:pickaxe", 0, parts, 130.7f.toRawBits(), 130, before, harvest, traits, false, hash)
    private fun facts(before: Int = 4, harvest: String = "primary"): TconstructDamageMethodFacts {
        val request = if (harvest == "primary") 1 else 2
        val next = minOf(130, before + request)
        return TconstructDamageMethodFacts(BoundFile("synthetic-unrun-raw", hash), actor, 0, hash, afterHash,
            1, traits.map { it.id }, traits.map { it.id }, request, request,
            traits.map { it.id }, traits.map { TconstructDamageHookStep(it.id, request, request) }, request, 130, before,
            next, next, (if (next == 130) 1 else 0).toByte(), true)
    }
    private fun verdict(before: Int = 4, post: NbtValue? = stack(5, 0),
        observed: TconstructDamageMethodFacts? = facts(before), harvest: String = "primary") =
        TconstructToolDurabilityOracle.evaluate(target(before, harvest), captured(stack(before, 0), hash),
            captured(post, afterHash), observed)

    @Test fun syntheticNonemptyNoOpTraitsRetainCountOneAndUseLiteralStatTruncationAndClamp() {
        listOf(verdict(), verdict(4, stack(6, 0), facts(4, "nonprimary"), "nonprimary"),
            verdict(129, stack(130, 1), facts(129)),
            verdict(129, stack(130, 1), facts(129, "nonprimary"), "nonprimary")).forEach {
            assertEquals(TconstructModelStatus.MATCH, it.status) // synthetic MODEL only; NOT runtime qualification.
            assertTrue(it.reason.contains("not A authenticated qualification"))
        }
    }

    @Test fun typedNonemptyMultipliersAreAllowedOnlyWhenExactParentAndChildMatch() {
        val multiplier = "tic_multipliers" to compound("tconstruct:durability" to NbtValue(5, 1.0f.toRawBits()))
        val parent = captured(stack(4, 0, multiplier), hash)
        val child = captured(stack(5, 0, multiplier), afterHash)
        assertEquals(TconstructModelStatus.MATCH, TconstructToolDurabilityOracle.evaluate(
            target(), parent, child, facts()).status)
        assertEquals(TconstructModelStatus.UNSUPPORTED, TconstructToolDurabilityOracle.evaluate(
            target(), parent, captured(stack(5, 0), afterHash), facts()).status)
    }

    @Test fun missingCaptureOrUnreviewedEffectiveModifiersStayUnsupported() {
        assertEquals(TconstructModelStatus.UNSUPPORTED, verdict(observed = null).status)
        assertEquals(TconstructModelStatus.UNSUPPORTED, TconstructToolDurabilityOracle.evaluate(
            target(), captured(stack(4, 0), hash), null, facts()).status)
        assertEquals(TconstructModelStatus.UNSUPPORTED, verdict(observed = facts().copy(setDamageHead = null)).status)
        assertEquals(TconstructModelStatus.UNSUPPORTED, verdict(observed = facts().copy(
            effectiveModifierIds = listOf("tconstruct:synthetic_unknown"))).status)
        assertEquals(TconstructModelStatus.UNSUPPORTED, verdict(observed = facts().copy(
            blockBreakEffectiveModifierIds = listOf("tconstruct:stonebound"))).status)
        assertEquals(TconstructModelStatus.UNSUPPORTED, TconstructToolDurabilityOracle.evaluate(
            target().copy(reviewedMaxDurability = 131), captured(stack(4, 0), hash),
            captured(stack(5, 0), afterHash), facts()).status)
        assertEquals(TconstructModelStatus.UNSUPPORTED, TconstructToolDurabilityOracle.evaluate(
            target().copy(reviewedEffectiveModifiersInOrder = emptyList()), captured(stack(4, 0), hash),
            captured(stack(5, 0), afterHash), facts()).status)
        assertEquals(TconstructModelStatus.UNSUPPORTED, TconstructToolDurabilityOracle.evaluate(
            target().copy(reviewedMaterialsInPartOrder = listOf("tconstruct:wood", "tconstruct:flint", "tconstruct:flint")),
            captured(stack(4, 0), hash), captured(stack(5, 0), afterHash), facts()).status)
    }

    @Test fun completeKnownProfileContradictionsDoNotBecomeCaptureGapsOrVanillaEmpty() {
        assertEquals(TconstructModelStatus.CONTRADICTION, verdict(observed = facts().copy(boundMineBlockCalls = 2)).status)
        assertEquals(TconstructModelStatus.CONTRADICTION, verdict(observed = facts().copy(getDamageReturn = 0)).status)
        assertEquals(TconstructModelStatus.CONTRADICTION, verdict(observed = facts().copy(setDamageReturnRaw = 6)).status)
        assertEquals(TconstructModelStatus.CONTRADICTION, verdict(129, stack(130, 0), facts(129)).status)
        assertEquals(TconstructModelStatus.CONTRADICTION, verdict(129, null, facts(129)).status)
        assertEquals(TconstructModelStatus.CONTRADICTION, verdict(observed = facts().copy(
            toolDamageHookSteps = traits.map { TconstructDamageHookStep(it.id, 1, 2) })).status)
        assertEquals(TconstructModelStatus.CONTRADICTION, verdict(observed = facts().copy(
            blockBreakHookCallModifierIds = listOf("tconstruct:jagged"))).status)
        assertEquals(TconstructModelStatus.CONTRADICTION, verdict(observed = facts().copy(
            blockBreakHookCallModifierIds = emptyList(), toolDamageHookSteps = emptyList())).status)
        assertEquals(TconstructModelStatus.CONTRADICTION, verdict(observed = facts().copy(sameOriginalItemObjectAtReturn = false)).status)
        assertEquals(TconstructModelStatus.CONTRADICTION, verdict(observed = facts().copy(directDamageHeadMaxDurability = 131)).status)
        assertEquals(TconstructModelStatus.UNSUPPORTED, verdict(post = stack(5, 0, "unrelated" to int(1))).status)
        val changed = captured(stack(5, 0, "unrelated" to int(1)), afterHash)
        assertEquals(TconstructModelStatus.CONTRADICTION, TconstructToolDurabilityOracle.evaluate(
            target().copy(reviewedNoOtherMutationOnWear = true), captured(stack(4, 0), hash), changed,
            facts()).status)
    }
}
