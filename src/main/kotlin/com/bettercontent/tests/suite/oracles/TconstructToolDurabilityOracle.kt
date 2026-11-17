package com.bettercontent.tests.suite.oracles

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.checkpoints.CapturedSealedVanillaInventory
import com.bettercontent.tests.suite.checkpoints.NbtList
import com.bettercontent.tests.suite.checkpoints.NbtValue
import com.bettercontent.tests.suite.checkpoints.SealedVanillaSlot

/** Source-supported candidate: flint head/handle/binding, intrinsic pierce plus flint jagged.
 * The ordered loaded roster and exact levels are reviewed LITERALS, not inferred from the JSON;
 * the installed JAR has random default materials and datapacks may override its definitions. */
internal data class TconstructModifierEntry(val id: String, val level: Int)
internal data class TconstructPickaxeWearTarget(
    val itemId: String, val toolSlot: Int, val reviewedMaterialsInPartOrder: List<String>,
    val reviewedDurabilityStatFloatBits: Int, val reviewedMaxDurability: Int,
    val reviewedBeforeDamage: Int, val reviewedHarvestKind: String,
    val reviewedEffectiveModifiersInOrder: List<TconstructModifierEntry>,
    val reviewedNoOtherMutationOnWear: Boolean, val reviewedProviderSha256: String
)

/** Pure conditional source model, NOT a runtime PASS/FAIL. A alone may gate a model MATCH with
 * strict raw replay, actual effective recipe/provider and direct Store parent→child authentication. */
internal enum class TconstructModelStatus { MATCH, CONTRADICTION, UNSUPPORTED }
internal data class TconstructModelVerdict(val status: TconstructModelStatus, val reason: String)

/** Proposed B→A *raw replay* facts; the producer wire is NOT frozen. A must authenticate the
 * SHA-linked native method rows, original actor/object, exact source provider and same direct
 * Store-sealed parent→child before constructing this input. No Boolean "native passed" flag. */
internal data class TconstructDamageMethodFacts(
    val trace: BoundFile, val actorUuid: String, val toolSlot: Int,
    val beforeCanonicalSha256: String, val afterCanonicalSha256: String,
    val boundMineBlockCalls: Int?, val blockBreakEffectiveModifierIds: List<String>?,
    val blockBreakHookCallModifierIds: List<String>?, val getDamageReturn: Int?, val damageHeadRequest: Int?,
    val effectiveModifierIds: List<String>?, val toolDamageHookSteps: List<TconstructDamageHookStep>?,
    val directDamageHeadRequest: Int?, val directDamageHeadMaxDurability: Int?,
    val directDamageHeadOldDamage: Int?, val setDamageHead: Int?, val setDamageReturnRaw: Int?,
    val setDamageReturnBrokenByte: Byte?, val sameOriginalItemObjectAtReturn: Boolean?
)

internal data class TconstructDamageHookStep(val modifierId: String, val input: Int, val output: Int)

/** Conditional exact flint pickaxe model ONLY. No physical v5 vanilla callback/EMPTY assumption.
 * A complete B raw replay and independently Store-verified direct parent/child are prerequisites
 * for QUALIFICATION, never established by these caller-constructible DTOs or a model MATCH. */
internal object TconstructToolDurabilityOracle {
    private fun compound(value: NbtValue?): Map<String, NbtValue>? =
        if (value?.type == 10) (value.payload as? Map<*, *>)?.takeIf {
            it.keys.all { key -> key is String } && it.values.all { entry -> entry is NbtValue }
        }?.entries?.associate { (key, tag) -> key as String to tag as NbtValue } else null
    private fun list(value: NbtValue?, type: Int): List<NbtValue>? {
        if (value?.type != 9) return null
        val payload = value.payload as? NbtList ?: return null
        return payload.values.takeIf { (payload.type == type || (it.isEmpty() && payload.type == 0)) &&
            it.size <= 64 && it.all { item -> item.type == type } }
    }
    private fun same(a: NbtValue, b: NbtValue): Boolean {
        if (a.type != b.type) return false
        return when (a.type) {
            7 -> (a.payload as? ByteArray)?.contentEquals(b.payload as? ByteArray ?: return false) == true
            11 -> (a.payload as? IntArray)?.contentEquals(b.payload as? IntArray ?: return false) == true
            12 -> (a.payload as? LongArray)?.contentEquals(b.payload as? LongArray ?: return false) == true
            9 -> {
                val left = a.payload as? NbtList ?: return false; val right = b.payload as? NbtList ?: return false
                left.type == right.type && left.values.size == right.values.size &&
                    left.values.zip(right.values).all { (x, y) -> same(x, y) }
            }
            10 -> {
                val left = compound(a) ?: return false; val right = compound(b) ?: return false
                left.keys == right.keys && left.all { (key, value) -> same(value, right.getValue(key)) }
            }
            in 1..6, 8 -> a.payload == b.payload // Float/Double are raw IEEE754 bits in NbtReader.
            else -> false
        }
    }
    private fun hash(value: String) = Regex("[0-9a-f]{64}").matches(value)
    private val flintParts = listOf("tconstruct:flint", "tconstruct:flint", "tconstruct:flint")
    private fun entries(value: NbtValue?): List<TconstructModifierEntry>? {
        return list(value, 10)?.map { item ->
            val tag = compound(item) ?: return null
            if (tag.keys != setOf("name", "level")) return null
            val id = tag["name"]?.takeIf { it.type == 8 }?.payload as? String ?: return null
            val level = tag["level"]?.takeIf { it.type == 3 }?.payload as? Int ?: return null
            TconstructModifierEntry(id, level)
        }
    }

    fun evaluate(target: TconstructPickaxeWearTarget, savedParent: CapturedSealedVanillaInventory?,
        savedChild: CapturedSealedVanillaInventory?, facts: TconstructDamageMethodFacts?): TconstructModelVerdict {
        fun incomplete(why: String) = TconstructModelVerdict(TconstructModelStatus.UNSUPPORTED, why)
        fun failed(why: String) = TconstructModelVerdict(TconstructModelStatus.CONTRADICTION, why)
        val modifiers = target.reviewedEffectiveModifiersInOrder
        if (target.itemId != "tconstruct:pickaxe" || target.toolSlot !in 0..8 ||
            !hash(target.reviewedProviderSha256) || target.reviewedMaterialsInPartOrder != flintParts ||
            modifiers.size != 2 || modifiers.map { it.id }.toSet() != setOf("tconstruct:jagged", "tconstruct:pierce") ||
            modifiers.any { (id, level) -> (id == "tconstruct:pierce" && level != 1) ||
                (id == "tconstruct:jagged" && level !in 1..3) } ||
            target.reviewedHarvestKind !in setOf("primary", "nonprimary") ||
            target.reviewedMaxDurability !in 1..1_000_000 ||
            target.reviewedBeforeDamage !in 0 until target.reviewedMaxDurability) {
            return incomplete("no reviewed loaded flint-part pickaxe, exact jagged/pierce roster or provider/target profile")
        }
        val stat = Float.fromBits(target.reviewedDurabilityStatFloatBits)
        if (!stat.isFinite() || stat <= 0f || stat > 1_000_000f || stat.toInt() != target.reviewedMaxDurability)
            return incomplete("reviewed raw float stat does not truncate to literal durability")
        val parent = savedParent ?: return incomplete("same-captured Store-verified parent absent")
        val pre = compound(parent.typedSlot(target.toolSlot)) ?: return incomplete("typed parent tool absent")
        val preTag = compound(pre["tag"]) ?: return incomplete("parent TCon tool tag absent")
        val materials = list(preTag["tic_materials"], 8)?.map { it.payload as? String }
        val preDamage = preTag["Damage"]?.takeIf { it.type == 3 }?.payload as? Int
        val preBroken = preTag["tic_broken"]?.takeIf { it.type == 1 }?.payload as? Byte
        val statBits = compound(preTag["tic_stats"])?.get("tconstruct:durability")
            ?.takeIf { it.type == 5 }?.payload as? Int
        val savedModifiers = entries(preTag["tic_modifiers"])
        val noUpgrades = preTag["tic_upgrades"] == null || list(preTag["tic_upgrades"], 10)?.isEmpty() == true
        if (pre["id"]?.takeIf { it.type == 8 }?.payload != target.itemId ||
            pre["Count"]?.takeIf { it.type == 1 }?.payload != 1.toByte() ||
            materials != target.reviewedMaterialsInPartOrder || statBits != target.reviewedDurabilityStatFloatBits ||
            preDamage != target.reviewedBeforeDamage || preBroken != 0.toByte() ||
            savedModifiers != modifiers || !noUpgrades ||
            (preTag["tic_multipliers"] != null && compound(preTag["tic_multipliers"]) == null) ||
            preTag["Unbreakable"] != null ||
            pre.containsKey("ForgeCaps") || pre.containsKey("ForgeData")) {
            return incomplete("saved parent is not the exact reviewed flint pickaxe with persisted nonempty traits")
        }
        val observed = facts ?: return incomplete("B actual TCon damage method replay not yet frozen/verified")
        val child = savedChild ?: return incomplete("independently Store-verified direct child absent")
        val parentSlot = parent.inventory.vanillaSlot(target.toolSlot) as? SealedVanillaSlot.Nonempty
            ?: return incomplete("parent saved typed and canonical slot disagree")
        val childSlot = child.inventory.vanillaSlot(target.toolSlot)
        if (parent.inventory.playerUuid != child.inventory.playerUuid ||
            parent.inventory.savedGameModeId != 0 || child.inventory.savedGameModeId != 0 ||
            parent.inventory.playerUuid != observed.actorUuid || observed.toolSlot != target.toolSlot ||
            !hash(observed.trace.sha256) || observed.trace.path.isBlank() ||
            observed.beforeCanonicalSha256 != parentSlot.stack.canonicalStackSha256 ||
            !hash(observed.beforeCanonicalSha256)) {
            return incomplete("raw trace/actor/selected original tool not bound to same-captured saved parent")
        }
        // Missing native method operands/call closure are an observation gap, not a game defect.
        if (observed.boundMineBlockCalls == null || observed.blockBreakEffectiveModifierIds == null ||
            observed.blockBreakHookCallModifierIds == null || observed.getDamageReturn == null ||
            observed.damageHeadRequest == null || observed.effectiveModifierIds == null ||
            observed.toolDamageHookSteps == null || observed.directDamageHeadRequest == null ||
            observed.directDamageHeadMaxDurability == null || observed.directDamageHeadOldDamage == null ||
            observed.setDamageHead == null || observed.setDamageReturnRaw == null ||
            observed.setDamageReturnBrokenByte == null ||
            observed.sameOriginalItemObjectAtReturn == null || !hash(observed.afterCanonicalSha256)) {
            return incomplete("actual bounded TCon method rows missing; no native Boolean substitution")
        }
        val expectedIds = modifiers.map { it.id }
        if (observed.blockBreakEffectiveModifierIds != expectedIds || observed.effectiveModifierIds != expectedIds)
            return incomplete("actual ordered BLOCK_BREAK/TOOL_DAMAGE roster is not reviewed installed jagged/pierce")
        if (observed.blockBreakHookCallModifierIds != expectedIds ||
            observed.toolDamageHookSteps.map { it.modifierId } != expectedIds)
            return failed("complete original loops did not invoke the default hooks once per reviewed modifier in order")
        if (observed.boundMineBlockCalls != 1)
            return failed("complete bound original mineBlock multiplicity contradicts exactly one action")
        val request = if (target.reviewedHarvestKind == "primary") 1 else 2
        if (observed.getDamageReturn != request || observed.damageHeadRequest != request ||
            observed.toolDamageHookSteps.any { it.input != request || it.output != request } ||
            observed.directDamageHeadRequest != request ||
            observed.directDamageHeadMaxDurability != target.reviewedMaxDurability ||
            observed.directDamageHeadOldDamage != target.reviewedBeforeDamage)
            return failed("complete original TCon harvest/request operands contradict literal reviewed primary/nonprimary rule")
        val expected = target.reviewedBeforeDamage + minOf(request,
            target.reviewedMaxDurability - target.reviewedBeforeDamage)
        if (observed.setDamageHead != expected || observed.setDamageReturnRaw != expected ||
            observed.setDamageReturnBrokenByte != (if (expected == target.reviewedMaxDurability) 1 else 0).toByte())
            return failed("complete original setDamage head/return contradict independent clamp arithmetic")
        if (observed.sameOriginalItemObjectAtReturn != true)
            return failed("complete TCon path replaced original item instead of retaining same broken tool")
        if (childSlot !is SealedVanillaSlot.Nonempty) return failed("observed saved TCon child EMPTY; broken TCon tools remain Count1")
        val post = compound(child.typedSlot(target.toolSlot))
            ?: return incomplete("saved child typed stack disagrees with canonical nonempty slot")
        val postTag = compound(post["tag"]) ?: return failed("saved child lost typed TCon tag")
        val next = postTag["Damage"]?.takeIf { it.type == 3 }?.payload as? Int
            ?: return failed("saved child lost raw TCon Damage INT")
        val broken = postTag["tic_broken"]?.takeIf { it.type == 1 }?.payload as? Byte
            ?: return failed("saved child lost raw tic_broken BYTE")
        if (post["id"]?.takeIf { it.type == 8 }?.payload != target.itemId ||
            post["Count"]?.takeIf { it.type == 1 }?.payload != 1.toByte() ||
            observed.afterCanonicalSha256 != childSlot.stack.canonicalStackSha256 ||
            next != expected || broken != (if (expected == target.reviewedMaxDurability) 1 else 0).toByte()) {
            return failed("complete saved child contradicts Count1 retained capped Damage/tic_broken result")
        }
        if (pre.keys != post.keys || preTag.keys != postTag.keys ||
            pre.filterKeys { it != "tag" }.any { (key, value) -> !same(value, post.getValue(key)) } ||
            preTag.filterKeys { it !in setOf("Damage", "tic_broken") }
                .any { (key, value) -> !same(value, postTag.getValue(key)) }) {
            return if (target.reviewedNoOtherMutationOnWear)
                failed("complete saved child has an unrelated typed mutation forbidden by independently reviewed source profile")
            else incomplete("unreviewed onCraftedBy/other mutation; source profile must be inspected first")
        }
        return TconstructModelVerdict(TconstructModelStatus.MATCH,
            "conditional flint pickaxe arithmetic and typed NBT model match ONLY; not A authenticated qualification")
    }
}
