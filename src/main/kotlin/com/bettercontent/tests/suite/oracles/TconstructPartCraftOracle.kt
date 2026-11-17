package com.bettercontent.tests.suite.oracles

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.checkpoints.CapturedSealedVanillaInventory
import com.bettercontent.tests.suite.checkpoints.NbtList
import com.bettercontent.tests.suite.checkpoints.NbtValue
import com.bettercontent.tests.suite.checkpoints.SealedStationSlot
import com.bettercontent.tests.suite.checkpoints.SealedTconStationInventory
import com.bettercontent.tests.suite.checkpoints.SealedVanillaSlot
import com.bettercontent.tests.suite.checkpoints.STACK_CONTENT_HASH_ALGORITHM

/** Exactly the installed base-flint 1/1 conversion and one of three pinned part recipes.
 * These reviewed candidate literals are NOT evidence of a loaded effective recipe, tag or source. */
internal data class TconstructPartCraftTarget(
    val stage: String, val reviewedRecipeId: String, val reviewedRecipeSourceSha256: String,
    val reviewedMaterialSourceSha256: String, val reviewedProviderSha256: String,
    val reviewedDefaultPatternTagSha256: String, val reviewedReusablePatternTagSha256: String,
    val reviewedGoldCastTagSha256: String?, val reviewedSelectedPatternItemId: String,
    val reviewedPlayerOutputHotbarSlot: Int
)

/** A-authenticated scope is a PRECONDITION, not proved by this caller-constructible record.
 * A must verify effective loaded recipe/material/tags/provider, actual earlier input provenance
 * into a GENUINE separate Store-sealed prepared parent, and original same-generation cache
 * computation after reopening that parent. B v3 PRETRANSFER parent cannot populate this. */
internal data class TconstructPartPreparedSourceScope(
    val reviewedEffectiveProfile: BoundFile, val priorInputAncestry: BoundFile,
    val originalCacheOrigin: BoundFile, val preparedParentCheckpointId: String,
    val actorUuid: String, val dimension: String, val x: Int, val y: Int, val z: Int,
    val reviewedRecipeId: String, val reviewedRecipeSourceSha256: String,
    val reviewedMaterialSourceSha256: String, val reviewedProviderSha256: String,
    val reviewedDefaultPatternTagSha256: String, val reviewedReusablePatternTagSha256: String,
    val reviewedGoldCastTagSha256: String?
)

/** Original observed operands, independently replayed by A in the future; NOT a frozen B wire.
 * Null means missing observation. Cursor/packet custody must be authenticated outside this DTO;
 * raw trace identity alone never proves OS input, physical acquisition or Store ancestry. */
internal data class TconstructPartObservedLeftover(val itemId: String, val count: Int, val typed: NbtValue?)
internal data class TconstructPartOriginalFacts(
    val trace: BoundFile, val actorUuid: String, val originalRecipeId: String?,
    val originalRecipeClass: String?, val originalMaterialId: String?,
    val originalSelectedPatternId: String?, val originalPatternItemId: String?,
    val originalGetItemsUsed: Int?, val originalShrinkMaterial: Int?,
    val originalPatternReusable: Boolean?, val originalShrinkPattern: Int?,
    val originalLeftover: TconstructPartObservedLeftover?, val originalResult: NbtValue?,
    val originalOnTakeResult: NbtValue?, val originalTransferredToHotbar: NbtValue?,
    val originalTransferTargetHotbarSlot: Int?
)

/** Precommitted plan is independent of native after-snapshots; only PICKUP button0 whole-stack
 * into an EMPTY backing BE slot is supported. Menu indices/owner/click/copy chain remain A's
 * strict raw-replay duties and can never be inferred from these normalized backing indices. */
internal data class TconstructPartCommittedMove(
    val sourcePlayerSlot: Int, val destinationStationSlot: Int,
    val pickupButton: Int, val placeButton: Int, val clickType: String
)
internal data class TconstructPartRetainedLink(
    val stationSlot: Int, val originalSourceTrace: BoundFile,
    val originalSourceRowSequence: Long, val sealedParentCheckpointId: String,
    val sealedChildCheckpointId: String, val sealedChildReceipt: BoundFile
)
internal data class TconstructPartDirectPlan(
    val mode: String, val moves: List<TconstructPartCommittedMove>,
    val retainedLinks: List<TconstructPartRetainedLink>
)

/** Domain facts from original callback replay, NOT a parser for B's evolving raw wire. Null
 * actual original source-after is missing evidence, distinct from an observed EMPTY stack. */
internal data class TconstructPartOriginalMove(
    val sourcePlayerSlot: Int, val destinationStationSlot: Int,
    val pickupButton: Int, val placeButton: Int, val clickType: String,
    val sourceBefore: NbtValue?, val sourceAfterPickup: TconstructPartObservedLeftover?,
    val stationBefore: TconstructPartObservedLeftover?, val cursorAfterPickup: NbtValue?,
    val stationAfter: NbtValue?, val cursorAfterPlace: TconstructPartObservedLeftover?,
    val placeReturnRowSequence: Long?
)
internal data class TconstructPartResultOrigin(
    val branch: String, val originalCalcReturnRowSequence: Long?,
    val originalCacheAssignmentRowSequence: Long?, val resultClickRowSequence: Long?,
    val finalCurrentInputTransferRowSequence: Long?, val originalInputGeneration: Long?
)
internal data class TconstructPartDirectFacts(
    val original: TconstructPartOriginalFacts, val moves: List<TconstructPartOriginalMove>?,
    val cursorAtParent: TconstructPartObservedLeftover?,
    val cursorAtChild: TconstructPartObservedLeftover?,
    val origin: TconstructPartResultOrigin?
)
/** A must authenticate actual effective loaded source + retained-input Store/trace links. A
 * constructs this only from independently reviewed bytes, never a B native "passed" Boolean. */
internal data class TconstructPartDirectSourceScope(
    val effectiveSourceReview: BoundFile, val parentCheckpointId: String,
    val childCheckpointId: String, val actorUuid: String, val dimension: String,
    val x: Int, val y: Int, val z: Int,
    val reviewedTarget: TconstructPartCraftTarget
)

/** No runtime PASS/FAIL: A must separately authenticate the exact actual effective definitions,
 * provenance, complete original raw callback and transfer/cursor trace, and direct Store chain. */
internal enum class TconstructPartModelStatus { MATCH, CONTRADICTION, UNSUPPORTED }
internal data class TconstructPartModelVerdict(val status: TconstructPartModelStatus, val reason: String)

internal object TconstructPartCraftOracle {
    private data class SupportedStage(val cost: Int, val partItem: String, val recipeSha: String,
        val patternId: String, val castItem: String)
    private val stages = mapOf(
        "pick_head" to SupportedStage(2, "tconstruct:pick_head",
            "cb87032c026b49e35a8dc885395fe8e520da4e90a7152a4e9fa05cc08b498add",
            "tconstruct:pick_head", "tconstruct:pick_head_cast"),
        "tool_handle" to SupportedStage(1, "tconstruct:tool_handle",
            "4aebd49ef1cad49ab208f71650d6dcc3010931769ba4d913ff879d93c08776bf",
            "tconstruct:tool_handle", "tconstruct:tool_handle_cast"),
        "tool_binding" to SupportedStage(1, "tconstruct:tool_binding",
            "20489d66c80e734633c6ef81da10d43d81e548c90341194436308f5d5f5369ba",
            "tconstruct:tool_binding", "tconstruct:tool_binding_cast"))
    private const val BASE_FLINT_SHA = "e9361422ab3e493c140d2702a18631192045b3103cb4794e7a9eb340635756fd"
    private const val DEFAULT_TAG_SHA = "2dd02cade5270623cb37f7dcee6890d20effc839f0507083a0574e8367e720de"
    private const val REUSABLE_TAG_SHA = "ad021f5f4b166c9dfae131742847df75a91bb9fda357720b2860b6bfe160b34c"
    private const val GOLD_CAST_TAG_SHA = "530c7db8bbc468c5c294959c4bcb81c3821762bd92bec9ce3135ba882258d313"
    private const val SOURCE_RECIPE_CLASS = "slimeknights.tconstruct.library.recipe.partbuilder.PartRecipe"
    private fun hash(value: String) = Regex("[0-9a-f]{64}").matches(value)
    private fun compound(tag: NbtValue?): Map<String, NbtValue>? {
        if (tag?.type != 10) return null
        val values = tag.payload as? Map<*, *> ?: return null
        if (values.keys.any { it !is String } || values.values.any { it !is NbtValue }) return null
        @Suppress("UNCHECKED_CAST")
        return values as Map<String, NbtValue>
    }
    private fun same(a: NbtValue?, b: NbtValue?): Boolean {
        if (a == null || b == null) return a == null && b == null
        if (a.type != b.type) return false
        return when (a.type) {
            7 -> (a.payload as? ByteArray)?.contentEquals(b.payload as? ByteArray ?: return false) == true
            11 -> (a.payload as? IntArray)?.contentEquals(b.payload as? IntArray ?: return false) == true
            12 -> (a.payload as? LongArray)?.contentEquals(b.payload as? LongArray ?: return false) == true
            9 -> {
                val left = a.payload as? NbtList ?: return false
                val right = b.payload as? NbtList ?: return false
                left.type == right.type && left.values.size == right.values.size &&
                    left.values.zip(right.values).all { (x, y) -> same(x, y) }
            }
            10 -> {
                val left = compound(a) ?: return false
                val right = compound(b) ?: return false
                left.keys == right.keys && left.all { (key, value) -> same(value, right[key]) }
            }
            in 1..6, 8 -> a.payload == b.payload // float/double payloads are raw IEEE bits
            else -> false
        }
    }
    private fun item(tag: NbtValue?): Map<String, NbtValue>? = compound(tag)?.takeIf {
        it["id"]?.type == 8 && it["Count"]?.type == 1
    }
    private fun count(tag: NbtValue?): Int? = (item(tag)?.get("Count")?.payload as? Byte)?.toInt()
    private fun id(tag: NbtValue?): String? = item(tag)?.get("id")?.payload as? String
    private fun material(tag: NbtValue?): String? =
        compound(item(tag)?.get("tag"))?.get("Material")?.takeIf { it.type == 8 }?.payload as? String
    private fun sameExceptCount(a: NbtValue?, b: NbtValue?): Boolean {
        val left = item(a) ?: return false
        val right = item(b) ?: return false
        return left.keys == right.keys && left.filterKeys { it != "Count" }
            .all { (key, value) -> same(value, right[key]) }
    }
    private fun validSlot(slot: SealedStationSlot): Boolean = if (slot.itemId == null) {
        slot.count == null && slot.typed == null && slot.stackSha256 == null
    } else {
        slot.count?.let { it in 1..64 } == true && id(slot.typed) == slot.itemId && count(slot.typed) == slot.count &&
            slot.stackSha256?.let(::hash) == true
    }
    private fun validPlayer(player: CapturedSealedVanillaInventory): Boolean = (0..40).all { index ->
        when (val slot = player.inventory.vanillaSlot(index)) {
            is SealedVanillaSlot.Empty -> player.typedSlot(index) == null
            is SealedVanillaSlot.Nonempty -> slot.stack.count in 1..64 &&
                id(player.typedSlot(index)) == slot.stack.itemId &&
                count(player.typedSlot(index)) == slot.stack.count && hash(slot.stack.canonicalStackSha256) &&
                slot.stack.contentAlgorithm == STACK_CONTENT_HASH_ALGORITHM && hash(slot.stack.contentSha256)
        }
    }

    fun evaluatePreparedStationOnly(target: TconstructPartCraftTarget,
        source: TconstructPartPreparedSourceScope?, parentStation: SealedTconStationInventory?,
        childStation: SealedTconStationInventory?, parentPlayer: CapturedSealedVanillaInventory?,
        childPlayer: CapturedSealedVanillaInventory?, facts: TconstructPartOriginalFacts?): TconstructPartModelVerdict {
        fun unsupported(why: String) = TconstructPartModelVerdict(TconstructPartModelStatus.UNSUPPORTED, why)
        fun contradiction(why: String) = TconstructPartModelVerdict(TconstructPartModelStatus.CONTRADICTION, why)
        val stage = stages[target.stage] ?: return unsupported("not a reviewed head/handle/binding part stage")
        val reusable = target.reviewedSelectedPatternItemId == stage.castItem
        if (target.reviewedRecipeId != "tconstruct:tools/parts/builder/${target.stage}" ||
            target.reviewedRecipeSourceSha256 != stage.recipeSha ||
            target.reviewedMaterialSourceSha256 != BASE_FLINT_SHA ||
            target.reviewedDefaultPatternTagSha256 != DEFAULT_TAG_SHA ||
            target.reviewedReusablePatternTagSha256 != REUSABLE_TAG_SHA ||
            (reusable && target.reviewedGoldCastTagSha256 != GOLD_CAST_TAG_SHA) ||
            (!reusable && target.reviewedGoldCastTagSha256 != null) ||
            target.reviewedSelectedPatternItemId !in setOf("tconstruct:pattern", stage.castItem) ||
            target.reviewedPlayerOutputHotbarSlot !in 0..8 || !hash(target.reviewedProviderSha256))
            return unsupported("unreviewed loaded part recipe, base-flint conversion, pattern membership or target")
        val pre = parentStation ?: return unsupported("genuine separately sealed prepared PartBuilder parent absent")
        val post = childStation ?: return unsupported("direct Store craft child PartBuilder absent")
        val beforePlayer = parentPlayer ?: return unsupported("same prepared-parent FULL41 absent")
        val afterPlayer = childPlayer ?: return unsupported("direct Store craft-child FULL41 absent")
        val reviewed = source ?: return unsupported("A authenticated effective profile/prepared input/cache-origin scope absent; B v3 pretransfer parent is ineligible")
        if (listOf(reviewed.reviewedEffectiveProfile, reviewed.priorInputAncestry,
                reviewed.originalCacheOrigin).any { !hash(it.sha256) || it.path.isBlank() } ||
            reviewed.preparedParentCheckpointId != pre.checkpoint.id ||
            reviewed.actorUuid != beforePlayer.inventory.playerUuid ||
            reviewed.dimension != pre.dimension || reviewed.x != pre.x ||
            reviewed.y != pre.y || reviewed.z != pre.z ||
            reviewed.reviewedRecipeId != target.reviewedRecipeId ||
            reviewed.reviewedRecipeSourceSha256 != target.reviewedRecipeSourceSha256 ||
            reviewed.reviewedMaterialSourceSha256 != target.reviewedMaterialSourceSha256 ||
            reviewed.reviewedProviderSha256 != target.reviewedProviderSha256 ||
            reviewed.reviewedDefaultPatternTagSha256 != target.reviewedDefaultPatternTagSha256 ||
            reviewed.reviewedReusablePatternTagSha256 != target.reviewedReusablePatternTagSha256 ||
            reviewed.reviewedGoldCastTagSha256 != target.reviewedGoldCastTagSha256)
            return unsupported("unknown or mismatched independently reviewed loaded effective source and genuine prepared-parent/cache lineage")
        if (pre.checkpoint.id == post.checkpoint.id ||
            beforePlayer.inventory.checkpointId != pre.checkpoint.id ||
            afterPlayer.inventory.checkpointId != post.checkpoint.id ||
            pre.tileId != "tconstruct:part_builder" || post.tileId != pre.tileId ||
            pre.slots.size != 2 || post.slots.size != 2 || pre.dimension != post.dimension ||
            pre.x != post.x || pre.y != post.y || pre.z != post.z ||
            beforePlayer.inventory.playerUuid != afterPlayer.inventory.playerUuid ||
            beforePlayer.inventory.savedGameModeId != 0 || afterPlayer.inventory.savedGameModeId != 0 ||
            !pre.slots.all(::validSlot) || !post.slots.all(::validSlot) ||
            !validPlayer(beforePlayer) || !validPlayer(afterPlayer))
            return unsupported("saved PartBuilder/actor scope or typed station slots not reviewed")
        val materialBefore = pre.slots[0]
        val patternBefore = pre.slots[1]
        if (materialBefore.itemId != "minecraft:flint" || materialBefore.count?.let { it in stage.cost..64 } != true ||
            item(materialBefore.typed)?.containsKey("tag") == true ||
            patternBefore.itemId != target.reviewedSelectedPatternItemId ||
            patternBefore.count?.let { it in 1..64 } != true || !hash(pre.region.sha256) || !hash(post.region.sha256))
            return unsupported("parent material or selected exact plain-pattern/cast profile not present")
        val observed = facts ?: return unsupported("original recipe/cost/cursor transfer trace missing")
        if (!hash(observed.trace.sha256) || observed.trace.path.isBlank() ||
            observed.actorUuid != beforePlayer.inventory.playerUuid ||
            observed.originalRecipeId == null || observed.originalRecipeClass == null ||
            observed.originalMaterialId == null || observed.originalSelectedPatternId == null ||
            observed.originalPatternItemId == null || observed.originalGetItemsUsed == null ||
            observed.originalShrinkMaterial == null || observed.originalPatternReusable == null ||
            (!reusable && observed.originalShrinkPattern == null) || observed.originalLeftover == null ||
            observed.originalResult == null || observed.originalOnTakeResult == null ||
            observed.originalTransferredToHotbar == null || observed.originalTransferTargetHotbarSlot == null)
            return unsupported("complete original result/onTake/cost/leftover and separate cursor-to-hotbar transfer missing")
        // Only the A-reviewed *effective* source/profile and original complete callback can reach
        // this comparison. A must not construct the scope from these native values. A surprising
        // original selection under that same supported profile is a contradiction, not absence.
        if (observed.originalRecipeId != target.reviewedRecipeId ||
            observed.originalRecipeClass != SOURCE_RECIPE_CLASS ||
            observed.originalMaterialId != "tconstruct:flint" ||
            observed.originalSelectedPatternId != stage.patternId ||
            observed.originalPatternItemId != target.reviewedSelectedPatternItemId)
            return contradiction("complete original recipe/material/pattern selection contradicts independently reviewed effective profile")
        // 1 unit of exact minecraft:flint contributes 1 unit of base flint; independent
        // positive-source arithmetic ceil(cost * needed / value) with needed=value=1.
        val reviewedNeeded = 1 // pinned exact base-flint material JSON, not a native return
        val reviewedValue = 1
        val expectedDebit = (stage.cost * reviewedNeeded + reviewedValue - 1) / reviewedValue
        val expectedPatternDebit = if (reusable) 0 else 1
        if (observed.originalGetItemsUsed != expectedDebit || observed.originalShrinkMaterial != expectedDebit ||
            observed.originalPatternReusable != reusable ||
            (reusable && observed.originalShrinkPattern != null) ||
            (!reusable && observed.originalShrinkPattern != expectedPatternDebit))
            return contradiction("complete original used/shrink/pattern branch contradicts independently pinned cost/tag rule")
        val leftover = requireNotNull(observed.originalLeftover)
        if (leftover.itemId != "minecraft:air" || leftover.count != 0 || leftover.typed != null)
            return contradiction("complete original material-value leftover is not EMPTY for base flint value1")
        val materialAfter = post.slots[0]
        val remaining = materialBefore.count!! - expectedDebit
        if ((remaining == 0 && materialAfter.itemId != null) ||
            (remaining > 0 && (materialAfter.itemId != "minecraft:flint" ||
                materialAfter.count != remaining || !sameExceptCount(materialBefore.typed, materialAfter.typed))))
            return contradiction("saved PartBuilder material slot0 does not debit reviewed paid flint units")
        val patternAfter = post.slots[1]
        val remainingPatterns = patternBefore.count!! - expectedPatternDebit
        if ((remainingPatterns == 0 && patternAfter.itemId != null) ||
            (remainingPatterns > 0 && (patternAfter.itemId != patternBefore.itemId ||
                patternAfter.count != remainingPatterns || !sameExceptCount(patternBefore.typed, patternAfter.typed))))
            return contradiction("saved PartBuilder slot1 does not conserve selected plain/reusable pattern")
        val originalResult = observed.originalResult
        val onTake = observed.originalOnTakeResult
        val transferred = observed.originalTransferredToHotbar
        if (id(originalResult) != stage.partItem || count(originalResult) != 1 ||
            material(originalResult) != "tconstruct:flint" ||
            id(onTake) != stage.partItem || count(onTake) != 1 ||
            material(onTake) != "tconstruct:flint" ||
            id(transferred) != stage.partItem || count(transferred) != 1 ||
            material(transferred) != "tconstruct:flint" ||
            observed.originalTransferTargetHotbarSlot != target.reviewedPlayerOutputHotbarSlot)
            return contradiction("complete original part result/onTake/cursor transfer contradicts Count1 base-flint output")
        if (!same(originalResult, onTake) || !same(onTake, transferred))
            return unsupported("unreviewed onCraftedBy/Forge callback or cursor-copy mutation of typed part output")
        val outputSlot = target.reviewedPlayerOutputHotbarSlot
        val playerBefore = beforePlayer.inventory.vanillaSlot(outputSlot)
        val playerAfter = afterPlayer.inventory.vanillaSlot(outputSlot)
        if (playerAfter !is SealedVanillaSlot.Nonempty)
            return contradiction("after completed transfer saved player output slot remains EMPTY")
        val typedOutput = afterPlayer.typedSlot(outputSlot)
        if (playerBefore is SealedVanillaSlot.Nonempty) {
            val prior = beforePlayer.typedSlot(outputSlot)
            if (id(prior) != stage.partItem || material(prior) != "tconstruct:flint" ||
                !sameExceptCount(prior, transferred))
                return unsupported("pre-existing saved part differs from reviewed transferred base-flint content")
        }
        val expectedPlayerCount = when (playerBefore) {
            is SealedVanillaSlot.Empty -> count(transferred) ?: return unsupported("transfer count missing")
            is SealedVanillaSlot.Nonempty -> playerBefore.stack.count +
                (count(transferred) ?: return unsupported("transfer count missing"))
        }
        if (id(typedOutput) != stage.partItem || material(typedOutput) != "tconstruct:flint" ||
            expectedPlayerCount !in 1..64 || count(typedOutput) != expectedPlayerCount ||
            !sameExceptCount(transferred, typedOutput) ||
            (playerBefore is SealedVanillaSlot.Nonempty &&
                playerBefore.stack.contentSha256 != playerAfter.stack.contentSha256))
            return contradiction("saved FULL41 output slot does not add one matching paid part with stable content hash")
        if ((0..40).any { slot -> slot != outputSlot &&
                when (val a = beforePlayer.inventory.vanillaSlot(slot)) {
                    is SealedVanillaSlot.Empty -> afterPlayer.inventory.vanillaSlot(slot) !is SealedVanillaSlot.Empty
                    is SealedVanillaSlot.Nonempty -> {
                        val b = afterPlayer.inventory.vanillaSlot(slot) as? SealedVanillaSlot.Nonempty
                        b == null || a.stack.canonicalStackSha256 != b.stack.canonicalStackSha256 ||
                            a.stack.itemId != b.stack.itemId || a.stack.count != b.stack.count ||
                            !same(beforePlayer.typedSlot(slot), afterPlayer.typedSlot(slot))
                    }
                }
            }) return contradiction("saved FULL41 has unreviewed other-slot change during isolated part craft")
        return TconstructPartModelVerdict(TconstructPartModelStatus.MATCH,
            "conditional base-flint part cost/pattern/output model match ONLY; A must authenticate source, cursor and Store")
    }

    private fun empty(value: TconstructPartObservedLeftover?): Boolean =
        value != null && value.itemId == "minecraft:air" && value.count == 0 && value.typed == null

    /** Direct genuine saved PRETRANSFER parent → POSTCRAFT child. The expected in-between
     * station state is computed ONLY from independently saved parent + precommitted move plan;
     * observed transfer after-stacks are compared to it, never promoted into expectations.
     * No transient SealedStationSlot or fabricated Store checkpoint is constructed. */
    fun evaluateDirectPretransfer(target: TconstructPartCraftTarget,
        source: TconstructPartDirectSourceScope?, plan: TconstructPartDirectPlan?,
        parentStation: SealedTconStationInventory?, childStation: SealedTconStationInventory?,
        parentPlayer: CapturedSealedVanillaInventory?, childPlayer: CapturedSealedVanillaInventory?,
        facts: TconstructPartDirectFacts?): TconstructPartModelVerdict {
        fun unsupported(why: String) = TconstructPartModelVerdict(TconstructPartModelStatus.UNSUPPORTED, why)
        fun contradiction(why: String) = TconstructPartModelVerdict(TconstructPartModelStatus.CONTRADICTION, why)
        val stage = stages[target.stage] ?: return unsupported("unreviewed part stage")
        val reusable = target.reviewedSelectedPatternItemId == stage.castItem
        if (target.reviewedRecipeId != "tconstruct:tools/parts/builder/${target.stage}" ||
            target.reviewedRecipeSourceSha256 != stage.recipeSha ||
            target.reviewedMaterialSourceSha256 != BASE_FLINT_SHA ||
            target.reviewedDefaultPatternTagSha256 != DEFAULT_TAG_SHA ||
            target.reviewedReusablePatternTagSha256 != REUSABLE_TAG_SHA ||
            (reusable && target.reviewedGoldCastTagSha256 != GOLD_CAST_TAG_SHA) ||
            (!reusable && target.reviewedGoldCastTagSha256 != null) ||
            target.reviewedSelectedPatternItemId !in setOf("tconstruct:pattern", stage.castItem) ||
            target.reviewedPlayerOutputHotbarSlot !in 0..8 || !hash(target.reviewedProviderSha256))
            return unsupported("unknown selected effective base-flint recipe/material/tag/provider profile")
        val pre = parentStation ?: return unsupported("actual pretransfer Store station parent missing")
        val post = childStation ?: return unsupported("actual postcraft Store station child missing")
        val before = parentPlayer ?: return unsupported("actual pretransfer Store FULL41 parent missing")
        val after = childPlayer ?: return unsupported("actual postcraft Store FULL41 child missing")
        val scope = source ?: return unsupported("independently A-reviewed effective source scope absent")
        val committed = plan ?: return unsupported("independently precommitted input transfer plan absent")
        if (!hash(scope.effectiveSourceReview.sha256) || scope.effectiveSourceReview.path.isBlank() ||
            scope.parentCheckpointId != pre.checkpoint.id ||
            scope.childCheckpointId != post.checkpoint.id ||
            pre.checkpoint.id == post.checkpoint.id ||
            before.inventory.checkpointId != pre.checkpoint.id ||
            after.inventory.checkpointId != post.checkpoint.id ||
            scope.reviewedTarget != target ||
            scope.actorUuid != before.inventory.playerUuid || scope.dimension != pre.dimension ||
            scope.x != pre.x || scope.y != pre.y || scope.z != pre.z ||
            pre.tileId != "tconstruct:part_builder" || post.tileId != pre.tileId ||
            pre.slots.size != 2 || post.slots.size != 2 || pre.dimension != post.dimension ||
            pre.x != post.x || pre.y != post.y || pre.z != post.z ||
            before.inventory.playerUuid != after.inventory.playerUuid ||
            before.inventory.savedGameModeId != 0 || after.inventory.savedGameModeId != 0 ||
            !hash(pre.region.sha256) || !hash(post.region.sha256) ||
            !pre.slots.all(::validSlot) || !post.slots.all(::validSlot) ||
            !validPlayer(before) || !validPlayer(after))
            return unsupported("source/Store/actor/station FULL41 scope not independently authenticated")
        val initial = committed.mode == "fresh_whole"
        val retained = committed.mode == "sealed_retained"
        if (!initial && !retained) return unsupported("mixed/split/unknown transfer profile")
        if (initial && (committed.retainedLinks.isNotEmpty() || committed.moves.size != 2 ||
                committed.moves.map { it.destinationStationSlot } != listOf(0, 1) ||
                committed.moves.map { it.sourcePlayerSlot }.toSet().size != 2 ||
                committed.moves.any { it.sourcePlayerSlot !in 0..40 || it.pickupButton != 0 ||
                    it.placeButton != 0 || it.clickType != "PICKUP" } ||
                pre.slots.any { it.itemId != null }))
            return unsupported("initial profile requires two precommitted whole-stack PICKUP0 moves into EMPTY BE slots")
        if (retained && (committed.moves.isNotEmpty() || committed.retainedLinks.size != 2 ||
                committed.retainedLinks.map { it.stationSlot } != listOf(0, 1) ||
                committed.retainedLinks.map { it.originalSourceTrace.sha256 to it.originalSourceRowSequence }.toSet().size != 2 ||
                committed.retainedLinks.any { link -> link.sealedChildCheckpointId != pre.checkpoint.id ||
                    link.sealedParentCheckpointId.isBlank() || link.originalSourceRowSequence < 1 ||
                    !hash(link.originalSourceTrace.sha256) || link.originalSourceTrace.path.isBlank() ||
                    !hash(link.sealedChildReceipt.sha256) || link.sealedChildReceipt.path.isBlank() }))
            return unsupported("retained parent has no independently authenticated prior raw and Store link per slot")
        // This local pair is a computed expectation, NOT a Sealed/Store intermediate snapshot.
        val expectedInput: List<NbtValue?> = if (initial) committed.moves.map { move ->
            before.typedSlot(move.sourcePlayerSlot)
        } else pre.slots.map { it.typed }
        val paid = expectedInput[0]
        val pattern = expectedInput[1]
        if (id(paid) != "minecraft:flint" || count(paid)?.let { it in stage.cost..64 } != true ||
            item(paid)?.containsKey("tag") == true ||
            id(pattern) != target.reviewedSelectedPatternItemId || count(pattern)?.let { it in 1..64 } != true)
            return unsupported("saved parent + authored move plan do not provide exact paid flint/pattern input")
        val observed = facts ?: return unsupported("original bounded transfer/cost/result facts absent")
        val raw = observed.original
        if (!hash(raw.trace.sha256) || raw.trace.path.isBlank() ||
            raw.actorUuid != before.inventory.playerUuid || observed.moves == null ||
            observed.moves.size != committed.moves.size || !empty(observed.cursorAtParent) ||
            !empty(observed.cursorAtChild) || observed.origin == null)
            return unsupported("original transfer, EMPTY cursor boundary or cache-origin capture incomplete")
        if (initial) {
            for ((index, expected) in committed.moves.withIndex()) {
                val actual = observed.moves[index]
                if (actual.sourceAfterPickup == null || actual.stationBefore == null ||
                    actual.cursorAfterPlace == null || actual.sourceBefore == null ||
                    actual.cursorAfterPickup == null || actual.stationAfter == null ||
                    actual.placeReturnRowSequence == null)
                    return unsupported("original packet/owner/source/move operand missing")
                if (actual.sourcePlayerSlot != expected.sourcePlayerSlot ||
                    actual.destinationStationSlot != expected.destinationStationSlot ||
                    actual.pickupButton != expected.pickupButton ||
                    actual.placeButton != expected.placeButton || actual.clickType != expected.clickType)
                    return contradiction("complete original input source/destination/PICKUP differs from precommitted physical plan")
                val fromSaved = expectedInput[index]
                if (!same(actual.sourceBefore, fromSaved) ||
                    !empty(actual.sourceAfterPickup) || !empty(actual.stationBefore) ||
                    !same(actual.cursorAfterPickup, fromSaved) ||
                    !same(actual.stationAfter, fromSaved) || !empty(actual.cursorAfterPlace))
                    return contradiction("complete original whole-stack transfer contradicts precommitted saved-parent input")
            }
        }
        val origin = observed.origin
        if (origin.branch !in setOf("original_calc_return", "observed_cache_hit") ||
            origin.originalCalcReturnRowSequence == null ||
            origin.originalCacheAssignmentRowSequence == null ||
            origin.resultClickRowSequence == null || origin.originalInputGeneration == null ||
            origin.originalInputGeneration < 0 ||
            origin.originalCalcReturnRowSequence < 1 ||
            origin.originalCalcReturnRowSequence > origin.originalCacheAssignmentRowSequence ||
            origin.originalCacheAssignmentRowSequence > origin.resultClickRowSequence ||
            (initial && (origin.finalCurrentInputTransferRowSequence == null ||
                origin.finalCurrentInputTransferRowSequence != observed.moves.last().placeReturnRowSequence ||
                origin.finalCurrentInputTransferRowSequence >= origin.originalCalcReturnRowSequence)) ||
            (retained && origin.finalCurrentInputTransferRowSequence != null))
            return unsupported("original same-generation result/cache origin or input-transfer ordering not authenticated")
        if (raw.originalRecipeId == null || raw.originalRecipeClass == null ||
            raw.originalMaterialId == null || raw.originalSelectedPatternId == null ||
            raw.originalPatternItemId == null || raw.originalGetItemsUsed == null ||
            raw.originalShrinkMaterial == null || raw.originalPatternReusable == null ||
            (!reusable && raw.originalShrinkPattern == null) || raw.originalLeftover == null ||
            raw.originalResult == null || raw.originalOnTakeResult == null ||
            raw.originalTransferredToHotbar == null || raw.originalTransferTargetHotbarSlot == null)
            return unsupported("original selected recipe/cost/result/cursor-to-hotbar capture incomplete")
        if (raw.originalRecipeId != target.reviewedRecipeId ||
            raw.originalRecipeClass != SOURCE_RECIPE_CLASS ||
            raw.originalMaterialId != "tconstruct:flint" ||
            raw.originalSelectedPatternId != stage.patternId ||
            raw.originalPatternItemId != target.reviewedSelectedPatternItemId)
            return contradiction("complete wrong original recipe/material/pattern under independently reviewed source profile")
        val reviewedNeeded = 1 // pinned exact base-flint material JSON, not original native used value
        val reviewedValue = 1
        val expectedDebit = (stage.cost * reviewedNeeded + reviewedValue - 1) / reviewedValue
        val patternDebit = if (reusable) 0 else 1
        if (raw.originalGetItemsUsed != expectedDebit || raw.originalShrinkMaterial != expectedDebit ||
            raw.originalPatternReusable != reusable || (reusable && raw.originalShrinkPattern != null) ||
            (!reusable && raw.originalShrinkPattern != patternDebit) || !empty(raw.originalLeftover))
            return contradiction("complete original cost/leftover/pattern branch contradicts source-derived rule")
        val materialRemaining = count(paid)!! - expectedDebit
        val patternRemaining = count(pattern)!! - patternDebit
        val afterMaterial = post.slots[0]
        val afterPattern = post.slots[1]
        if ((materialRemaining == 0 && afterMaterial.itemId != null) ||
            (materialRemaining > 0 && (afterMaterial.itemId != "minecraft:flint" ||
                afterMaterial.count != materialRemaining || !sameExceptCount(paid, afterMaterial.typed))) ||
            (patternRemaining == 0 && afterPattern.itemId != null) ||
            (patternRemaining > 0 && (afterPattern.itemId != target.reviewedSelectedPatternItemId ||
                afterPattern.count != patternRemaining || !sameExceptCount(pattern, afterPattern.typed))))
            return contradiction("saved direct child BE does not debit the independently expected transient paid inputs")
        val result = raw.originalResult
        val onTake = raw.originalOnTakeResult
        val transfer = raw.originalTransferredToHotbar
        if (id(result) != stage.partItem || count(result) != 1 || material(result) != "tconstruct:flint" ||
            id(onTake) != stage.partItem || count(onTake) != 1 || material(onTake) != "tconstruct:flint" ||
            id(transfer) != stage.partItem || count(transfer) != 1 || material(transfer) != "tconstruct:flint" ||
            raw.originalTransferTargetHotbarSlot != target.reviewedPlayerOutputHotbarSlot)
            return contradiction("complete original part output or cursor-to-hotbar transfer differs from reviewed Count1 flint part")
        if (!same(result, onTake) || !same(onTake, transfer))
            return unsupported("unreviewed onCraftedBy/Forge/cursor copy mutation")
        val outputSlot = target.reviewedPlayerOutputHotbarSlot
        val sourceSlots = if (initial) committed.moves.map { it.sourcePlayerSlot }.toSet() else emptySet()
        val priorOutput = before.inventory.vanillaSlot(outputSlot)
        val priorCount = if (outputSlot in sourceSlots) 0 else when (priorOutput) {
            is SealedVanillaSlot.Empty -> 0
            is SealedVanillaSlot.Nonempty -> {
                val old = before.typedSlot(outputSlot)
                if (id(old) != stage.partItem || material(old) != "tconstruct:flint" ||
                    !sameExceptCount(old, transfer)) return unsupported("prior output stack is not reviewed same-content part")
                priorOutput.stack.count
            }
        }
        val actualOutput = after.inventory.vanillaSlot(outputSlot) as? SealedVanillaSlot.Nonempty
            ?: return contradiction("saved postcraft child missing original transferred output")
        val typedOutput = after.typedSlot(outputSlot)
        if (priorCount + count(transfer)!! !in 1..64 ||
            id(typedOutput) != stage.partItem || material(typedOutput) != "tconstruct:flint" ||
            count(typedOutput) != priorCount + count(transfer)!! ||
            !sameExceptCount(typedOutput, transfer) ||
            (priorCount > 0 && priorOutput is SealedVanillaSlot.Nonempty &&
                priorOutput.stack.contentSha256 != actualOutput.stack.contentSha256))
            return contradiction("saved postcraft output slot does not contain expected part addition/content")
        if ((0..40).any { slot -> slot != outputSlot &&
                if (slot in sourceSlots) after.inventory.vanillaSlot(slot) !is SealedVanillaSlot.Empty
                else when (val previous = before.inventory.vanillaSlot(slot)) {
                    is SealedVanillaSlot.Empty -> after.inventory.vanillaSlot(slot) !is SealedVanillaSlot.Empty
                    is SealedVanillaSlot.Nonempty -> {
                        val current = after.inventory.vanillaSlot(slot) as? SealedVanillaSlot.Nonempty
                        current == null || previous.stack != current.stack ||
                            !same(before.typedSlot(slot), after.typedSlot(slot))
                    }
                }
            }) return contradiction("saved FULL41 has unreviewed source/other-slot change")
        return TconstructPartModelVerdict(TconstructPartModelStatus.MATCH,
            "conditional direct pretransfer→postcraft source model only; A must authenticate plan, originals, Store and OS")
    }
}
