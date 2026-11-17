package com.bettercontent.tests.suite.oracles

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.checkpoints.CapturedSealedVanillaInventory
import com.bettercontent.tests.suite.checkpoints.CheckpointRef
import com.bettercontent.tests.suite.checkpoints.NbtValue
import com.bettercontent.tests.suite.checkpoints.SealedInventoryStack
import com.bettercontent.tests.suite.checkpoints.SealedPlayerInventory
import com.bettercontent.tests.suite.checkpoints.SealedStationSlot
import com.bettercontent.tests.suite.checkpoints.SealedTconStationInventory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** UNRUN synthetics: none proves an effective recipe/tag, physical cursor transfer or Store chain. */
class TconstructPartCraftOracleTest {
    private val hash = "a".repeat(64)
    private val actor = "123e4567-e89b-42d3-a456-426614174000"
    private fun text(value: String) = NbtValue(8, value)
    private fun count(value: Int) = NbtValue(1, value.toByte())
    private fun compound(vararg fields: Pair<String, NbtValue>) = NbtValue(10, mapOf(*fields))
    private fun stack(id: String, quantity: Int, tag: NbtValue? = null): NbtValue =
        compound(*(listOf("id" to text(id), "Count" to count(quantity)) +
            listOfNotNull(tag?.let { "tag" to it })).toTypedArray())
    private fun part(stage: String, quantity: Int) = stack("tconstruct:$stage", quantity,
        compound("Material" to text("tconstruct:flint")))
    private fun stageCost(stage: String) = if (stage == "pick_head") 2 else 1
    private fun pattern(stage: String, reusable: Boolean): String =
        if (reusable) "tconstruct:${stage}_cast" else "tconstruct:pattern"
    private fun target(stage: String = "pick_head", reusable: Boolean = false) = TconstructPartCraftTarget(
        stage, "tconstruct:tools/parts/builder/$stage", when (stage) {
            "pick_head" -> "cb87032c026b49e35a8dc885395fe8e520da4e90a7152a4e9fa05cc08b498add"
            "tool_handle" -> "4aebd49ef1cad49ab208f71650d6dcc3010931769ba4d913ff879d93c08776bf"
            else -> "20489d66c80e734633c6ef81da10d43d81e548c90341194436308f5d5f5369ba"
        }, "e9361422ab3e493c140d2702a18631192045b3103cb4794e7a9eb340635756fd", hash,
        "2dd02cade5270623cb37f7dcee6890d20effc839f0507083a0574e8367e720de",
        "ad021f5f4b166c9dfae131742847df75a91bb9fda357720b2860b6bfe160b34c",
        if (reusable) "530c7db8bbc468c5c294959c4bcb81c3821762bd92bec9ce3135ba882258d313" else null,
        pattern(stage, reusable), 0)
    private fun preparedScope(candidate: TconstructPartCraftTarget = target()) = TconstructPartPreparedSourceScope(
        BoundFile("synthetic-effective-profile-NOT-auth", hash),
        BoundFile("synthetic-earlier-input-transfer-NOT-auth", hash),
        BoundFile("synthetic-new-epoch-cache-origin-NOT-auth", hash),
        "synthetic-parent", actor, "minecraft:overworld", 1, 64, 1,
        candidate.reviewedRecipeId, candidate.reviewedRecipeSourceSha256, candidate.reviewedMaterialSourceSha256,
        candidate.reviewedProviderSha256, candidate.reviewedDefaultPatternTagSha256,
        candidate.reviewedReusablePatternTagSha256, candidate.reviewedGoldCastTagSha256)
    private fun station(stage: String, flint: Int, patterns: Int, reusable: Boolean,
        checkpointId: String = "synthetic-parent"): SealedTconStationInventory {
        val material = if (flint == 0) SealedStationSlot(0, null, null, null, null) else
            SealedStationSlot(0, "minecraft:flint", flint, stack("minecraft:flint", flint), hash)
        val id = pattern(stage, reusable)
        val pattern = if (patterns == 0) SealedStationSlot(1, null, null, null, null) else
            SealedStationSlot(1, id, patterns, stack(id, patterns), hash)
        return SealedTconStationInventory(CheckpointRef(checkpointId, BoundFile("synthetic", hash)),
            "minecraft:overworld", 1, 64, 1, "tconstruct:part_builder", BoundFile("synthetic", hash),
            listOf(material, pattern))
    }
    private fun player(stage: String, partCount: Int = 0,
        checkpointId: String = "synthetic-parent"): CapturedSealedVanillaInventory {
        val part = if (partCount == 0) null else part(stage, partCount)
        val stacks = if (part == null) emptyList() else listOf(SealedInventoryStack(0, "tconstruct:$stage", partCount,
            "bc.playtest.stack-nbt.v1", hash, "bc.playtest.stack-content.v1", hash))
        return CapturedSealedVanillaInventory(SealedPlayerInventory(checkpointId, actor, BoundFile("synthetic", hash),
            stacks, 0, stacks), if (part == null) emptyMap() else mapOf(0 to part))
    }
    private fun original(stage: String, reusable: Boolean = false): TconstructPartOriginalFacts {
        val result = part(stage, 1)
        return TconstructPartOriginalFacts(BoundFile("synthetic-original-unrun", hash), actor,
            "tconstruct:tools/parts/builder/$stage",
            "slimeknights.tconstruct.library.recipe.partbuilder.PartRecipe", "tconstruct:flint",
            "tconstruct:$stage", pattern(stage, reusable), stageCost(stage), stageCost(stage),
            reusable, if (reusable) null else 1, TconstructPartObservedLeftover("minecraft:air", 0, null),
            result, result, result, 0)
    }
    private fun verdict(stage: String = "pick_head", reusable: Boolean = false,
        preFlint: Int = stageCost(stage) + 1, postFlint: Int = preFlint - stageCost(stage),
        prePatterns: Int = 2, postPatterns: Int = prePatterns - if (reusable) 0 else 1,
        preParts: Int = 0, postParts: Int = preParts + 1,
        facts: TconstructPartOriginalFacts? = original(stage, reusable),
        scope: TconstructPartPreparedSourceScope? = preparedScope(target(stage, reusable))) =
        TconstructPartCraftOracle.evaluatePreparedStationOnly(
        target(stage, reusable), scope, station(stage, preFlint, prePatterns, reusable),
        station(stage, postFlint, postPatterns, reusable, "synthetic-child"),
        player(stage, preParts), player(stage, postParts, "synthetic-child"), facts)

    private fun full41(vararg slots: Pair<Int, NbtValue>,
        checkpointId: String = "synthetic-parent"): CapturedSealedVanillaInventory {
        val typed = slots.toMap()
        val records = slots.map { (slot, raw) ->
            @Suppress("UNCHECKED_CAST")
            val fields = raw.payload as Map<String, NbtValue>
            SealedInventoryStack(slot, fields.getValue("id").payload as String,
                (fields.getValue("Count").payload as Byte).toInt(),
                "bc.playtest.stack-nbt.v1", hash, "bc.playtest.stack-content.v1", hash)
        }
        return CapturedSealedVanillaInventory(SealedPlayerInventory(checkpointId, actor,
            BoundFile("synthetic", hash), records, 0, records), typed)
    }
    private fun empty() = TconstructPartObservedLeftover("minecraft:air", 0, null)
    private fun directPlan(retained: Boolean = false) = TconstructPartDirectPlan(
        if (retained) "sealed_retained" else "fresh_whole",
        if (retained) emptyList() else listOf(TconstructPartCommittedMove(1, 0, 0, 0, "PICKUP"),
            TconstructPartCommittedMove(2, 1, 0, 0, "PICKUP")),
        if (!retained) emptyList() else (0..1).map { slot ->
            TconstructPartRetainedLink(slot, BoundFile("synthetic-prior-raw-NOT-auth", hash), slot + 1L,
                "synthetic-prior", "synthetic-parent", BoundFile("synthetic-prior-store-NOT-auth", hash))
        })
    private fun directFacts(stage: String = "pick_head", output: Int = 0,
        retained: Boolean = false): TconstructPartDirectFacts {
        val material = stack("minecraft:flint", stageCost(stage) + 1)
        val pattern = stack("tconstruct:pattern", 2)
        val moves = if (retained) emptyList() else listOf(material, pattern).mapIndexed { index, raw ->
            TconstructPartOriginalMove(index + 1, index, 0, 0, "PICKUP", raw, empty(), empty(),
                raw, raw, empty(), (index + 1L) * 10L)
        }
        return TconstructPartDirectFacts(original(stage).copy(originalTransferTargetHotbarSlot = output),
            moves, empty(), empty(), TconstructPartResultOrigin("original_calc_return", 30, 31, 40,
                if (retained) null else 20, 1))
    }
    private fun directVerdict(stage: String = "pick_head", retained: Boolean = false,
        output: Int = 0, facts: TconstructPartDirectFacts = directFacts(stage, output, retained),
        plan: TconstructPartDirectPlan = directPlan(retained)): TconstructPartModelVerdict {
        val candidate = target(stage).copy(reviewedPlayerOutputHotbarSlot = output)
        val source = TconstructPartDirectSourceScope(BoundFile("synthetic-effective-profile-NOT-auth", hash),
            "synthetic-parent", "synthetic-child", actor, "minecraft:overworld", 1, 64, 1, candidate)
        val before = if (retained) full41() else full41(1 to stack("minecraft:flint", stageCost(stage) + 1),
            2 to stack("tconstruct:pattern", 2))
        val after = full41(output to part(stage, 1), checkpointId = "synthetic-child")
        return TconstructPartCraftOracle.evaluateDirectPretransfer(candidate, source, plan,
            if (retained) station(stage, stageCost(stage) + 1, 2, false) else station(stage, 0, 0, false),
            station(stage, 1, 1, false, "synthetic-child"), before, after, facts)
    }

    @Test fun directWholeStackAndPriorRetainedSourceModelsAreSyntheticMatchesOnly() {
        listOf("pick_head", "tool_handle", "tool_binding").forEach { stage ->
            assertEquals(TconstructPartModelStatus.MATCH, directVerdict(stage).status)
            assertEquals(TconstructPartModelStatus.MATCH, directVerdict(stage, output = 1).status)
            assertEquals(TconstructPartModelStatus.MATCH, directVerdict(stage, retained = true).status)
        }
    }
    @Test fun directModelRejectsUnreviewedPlanAndCompleteWrongTransferWithoutNativeSelfOracle() {
        assertEquals(TconstructPartModelStatus.UNSUPPORTED, directVerdict(plan = directPlan().copy(moves = emptyList())).status)
        assertEquals(TconstructPartModelStatus.UNSUPPORTED, directVerdict(facts = directFacts().copy(
            moves = null)).status)
        val candidate = target()
        val wrongChildScope = TconstructPartDirectSourceScope(BoundFile("synthetic-profile-NOT-auth", hash),
            "synthetic-parent", "unrelated-child", actor, "minecraft:overworld", 1, 64, 1, candidate)
        assertEquals(TconstructPartModelStatus.UNSUPPORTED, TconstructPartCraftOracle.evaluateDirectPretransfer(
            candidate, wrongChildScope, directPlan(), station("pick_head", 0, 0, false),
            station("pick_head", 1, 1, false, "synthetic-child"),
            full41(1 to stack("minecraft:flint", 3), 2 to stack("tconstruct:pattern", 2)),
            full41(0 to part("pick_head", 1), checkpointId = "synthetic-child"), directFacts()).status)
        val retained = directPlan(retained = true)
        assertEquals(TconstructPartModelStatus.UNSUPPORTED, directVerdict(retained = true,
            plan = retained.copy(retainedLinks = listOf(retained.retainedLinks[0],
                retained.retainedLinks[1].copy(originalSourceRowSequence = 1L)))).status)
        assertEquals(TconstructPartModelStatus.CONTRADICTION, directVerdict(facts = directFacts().copy(
            moves = directFacts().moves!!.mapIndexed { index, move ->
                if (index == 0) move.copy(stationAfter = stack("minecraft:flint", 1)) else move })).status)
        assertEquals(TconstructPartModelStatus.CONTRADICTION, directVerdict(facts = directFacts().copy(
            original = original("pick_head").copy(originalRecipeId = "other:wrong"))).status)
        assertEquals(TconstructPartModelStatus.UNSUPPORTED, directVerdict(facts = directFacts().copy(
            origin = TconstructPartResultOrigin("cache_origin_unobserved", null, null, 40, 20, null))).status)
    }

    @Test fun allThreeBaseFlintStagesAndExistingPartCountUseIndependentDebit() {
        listOf("pick_head", "tool_handle", "tool_binding").forEach { stage ->
            assertEquals(TconstructPartModelStatus.MATCH, verdict(stage).status)
            assertEquals(TconstructPartModelStatus.MATCH, verdict(stage, preParts = 1).status)
            assertEquals(TconstructPartModelStatus.MATCH, verdict(stage, preFlint = stageCost(stage),
                postFlint = 0, prePatterns = 1, postPatterns = 0).status)
        }
        assertEquals(TconstructPartModelStatus.MATCH, verdict(reusable = true).status)
    }

    @Test fun missingCursorTransferOrUnknownEffectiveRecipeIsUnsupportedNotAnInventedOutput() {
        assertEquals(TconstructPartModelStatus.UNSUPPORTED, verdict(facts = null).status)
        assertEquals(TconstructPartModelStatus.UNSUPPORTED, verdict(facts = original("pick_head").copy(
            originalTransferredToHotbar = null)).status)
        assertEquals(TconstructPartModelStatus.UNSUPPORTED, verdict(scope = null).status)
        assertEquals(TconstructPartModelStatus.UNSUPPORTED, TconstructPartCraftOracle.evaluatePreparedStationOnly(
            target(), preparedScope(), station("pick_head", 3, 2, false),
            station("pick_head", 1, 1, false, "synthetic-child"),
            player("pick_head"), player("pick_head", 1, "unrelated-child"), original("pick_head")).status)
        assertEquals(TconstructPartModelStatus.UNSUPPORTED, verdict(scope = preparedScope().copy(
            priorInputAncestry = BoundFile("", hash))).status)
        assertEquals(TconstructPartModelStatus.UNSUPPORTED, verdict(facts = original("pick_head").copy(
            originalOnTakeResult = stack("tconstruct:pick_head", 1,
                compound("Material" to text("tconstruct:flint"), "unknown" to text("mutation"))))).status)
        val mismatchedPrior = player("pick_head", 1).let { saved ->
            saved.copy(typedStacksBySlot = mapOf(0 to stack("tconstruct:pick_head", 1,
                compound("Material" to text("tconstruct:wood")))))
        }
        assertEquals(TconstructPartModelStatus.UNSUPPORTED, TconstructPartCraftOracle.evaluatePreparedStationOnly(
            target(), preparedScope(), station("pick_head", 3, 2, false),
            station("pick_head", 1, 1, false, "synthetic-child"),
            mismatchedPrior, player("pick_head", 2, "synthetic-child"), original("pick_head")).status)
        assertEquals(TconstructPartModelStatus.UNSUPPORTED, TconstructPartCraftOracle.evaluatePreparedStationOnly(
            target("pick_head", reusable = true).copy(reviewedGoldCastTagSha256 = null),
            preparedScope(target("pick_head", reusable = true)),
            station("pick_head", 3, 2, true), station("pick_head", 1, 2, true, "synthetic-child"),
            player("pick_head"), player("pick_head", 1, "synthetic-child"), original("pick_head", true)).status)
        assertEquals(TconstructPartModelStatus.UNSUPPORTED, TconstructPartCraftOracle.evaluatePreparedStationOnly(
            target().copy(reviewedMaterialSourceSha256 = hash), preparedScope(), station("pick_head", 3, 2, false),
            station("pick_head", 1, 1, false, "synthetic-child"), player("pick_head"),
            player("pick_head", 1, "synthetic-child"), original("pick_head")).status)
    }

    @Test fun completeSourceSupportedContradictionsAreNotRebrandedMissingCapture() {
        assertEquals(TconstructPartModelStatus.CONTRADICTION, verdict(facts = original("pick_head").copy(
            originalRecipeId = "other:pick_head")).status)
        assertEquals(TconstructPartModelStatus.CONTRADICTION, verdict(facts = original("pick_head").copy(
            originalMaterialId = "tconstruct:wood")).status)
        assertEquals(TconstructPartModelStatus.CONTRADICTION, verdict(facts = original("pick_head").copy(
            originalPatternItemId = "tconstruct:pick_head_cast")).status)
        assertEquals(TconstructPartModelStatus.CONTRADICTION, verdict(postFlint = 2).status)
        assertEquals(TconstructPartModelStatus.CONTRADICTION, verdict(postPatterns = 2).status)
        assertEquals(TconstructPartModelStatus.CONTRADICTION, verdict(postParts = 0).status)
        assertEquals(TconstructPartModelStatus.CONTRADICTION, verdict(facts = original("pick_head").copy(
            originalGetItemsUsed = 1)).status)
        assertEquals(TconstructPartModelStatus.CONTRADICTION, verdict(facts = original("pick_head").copy(
            originalLeftover = TconstructPartObservedLeftover("minecraft:flint", 1, stack("minecraft:flint", 1)))).status)
        assertEquals(TconstructPartModelStatus.CONTRADICTION, verdict(reusable = true,
            facts = original("pick_head", true).copy(originalShrinkPattern = 1)).status)
        val changedContent = player("pick_head", 2, "synthetic-child").let { saved ->
            val edited = saved.inventory.vanillaStacks.map { it.copy(contentSha256 = "b".repeat(64)) }
            saved.copy(inventory = saved.inventory.copy(stacks = edited, vanillaStacks = edited))
        }
        assertEquals(TconstructPartModelStatus.CONTRADICTION, TconstructPartCraftOracle.evaluatePreparedStationOnly(
            target(), preparedScope(), station("pick_head", 3, 2, false),
            station("pick_head", 1, 1, false, "synthetic-child"),
            player("pick_head", 1), changedContent, original("pick_head")).status)
    }
}
