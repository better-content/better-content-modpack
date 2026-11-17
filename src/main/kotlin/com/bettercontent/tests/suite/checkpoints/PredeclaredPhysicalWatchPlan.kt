package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper

private const val PHYSICAL_PRIMITIVE_CASE = "primitive.harvest"

/** Frozen from the actual reviewed manifest before the first ancestor starts, never a live target echo. */
internal data class PredeclaredPhysicalWatchPlan(val manifest: BoundFile, val caseId: String,
    val preparedInputId: String, val selectors: List<String>, val sites: List<PredeclaredWatchedBlock>) {
    init { require(caseId == PHYSICAL_PRIMITIVE_CASE && selectors.size == 2 &&
        selectors.distinct().size == 2 && sites.size == 2 && sites.map { it.position }.distinct().size == 2) }
}

/** Only an immutable authored case binding can name the origin-time correct/wrong watched sites. */
internal fun readPredeclaredPhysicalWatchPlan(manifest: BoundFile): PredeclaredPhysicalWatchPlan {
    require(manifest.sha256.matches(shaPattern))
    val m = strictCapturedJson(capturedEvidenceBytes(manifest, 67_108_864))
    val bindings = m.path("driver_bindings")
    val cases = m.path("cases")
    require(bindings.isArray && cases.isArray)
    val authored = bindings.filter { it.path("case_id").asText() == PHYSICAL_PRIMITIVE_CASE }.single()
    require(cases.count { it.path("id").asText() == PHYSICAL_PRIMITIVE_CASE } == 1 &&
        authored.path("parameters").isObject)
    val params = authored.path("parameters")
    val dimension = params.path("dimension").asText("")
    val targets = params.path("targets")
    require(dimension.matches(resourcePattern) && targets.isArray && targets.size() == 2 &&
        targets[0].path("mode").asText() == "correct" && targets[1].path("mode").asText() == "wrong")
    val branchRows = m.path("branches")
    require(branchRows.isArray) { "physical branch must be reviewed before prelaunch watch" }
    val branch = branchRows.filter { row -> row.path("case_ids").any { it.asText() == PHYSICAL_PRIMITIVE_CASE } }.single()
    require(branch.path("state_policy").asText() == "isolated" && branch.path("output_checkpoint").isNull)
    val preparedInput = branch.path("input_checkpoint").asText("")
    require(preparedInput.isNotBlank() && preparedInput != "origin")
    val selectors = mutableListOf<String>()
    val sites = targets.map { target ->
        require(target.isObject && target.path("selector_id").isTextual &&
            target.path("block_id").isTextual)
        val selector = target.path("selector_id").asText()
        require(Regex("[a-z0-9][a-z0-9_.-]*").matches(selector))
        selectors += selector
        fun coordinate(name: String, range: IntRange): Int = target.path(name).let { value ->
            require(value.isIntegralNumber && value.canConvertToInt() && value.asInt() in range)
            value.asInt()
        }
        PredeclaredWatchedBlock(ScopedBlockPosition(dimension,
            coordinate("x", -30_000_000..30_000_000), coordinate("y", -2048..2048),
            coordinate("z", -30_000_000..30_000_000)), target.path("block_id").asText())
    }
    require(sites.map { it.position }.distinct().size == 2 &&
        sites.map { it.position.dimension to (it.position.x shr 4 to (it.position.z shr 4)) }.distinct().size == 2) {
        "correct and wrong physical source sites must be distinct preauthored chunks"
    }
    val sorted = sites.zip(selectors).sortedWith(compareBy({ it.first.position.dimension },
        { it.first.position.x }, { it.first.position.y }, { it.first.position.z }))
    return PredeclaredPhysicalWatchPlan(manifest, PHYSICAL_PRIMITIVE_CASE, preparedInput,
        sorted.map { it.second }, sorted.map { it.first })
}

/** Only direct checkpoint producers, never sibling/ordering-dependency branches, carry the site. */
internal fun physicalAncestorBranchIds(plan: PredeclaredPhysicalWatchPlan): List<String> {
    require(readPredeclaredPhysicalWatchPlan(plan.manifest) == plan)
    val manifest = strictCapturedJson(capturedEvidenceBytes(plan.manifest, 67_108_864))
    val branches = manifest.path("branches").map { suiteMapper.treeToValue(it, BranchSpec::class.java) }
    val producers = branches.filter { it.outputCheckpoint != null }.associateBy { it.outputCheckpoint!! }
    require(producers.size == branches.count { it.outputCheckpoint != null })
    val ancestors = mutableListOf<BranchSpec>()
    var cursor = plan.preparedInputId
    while (cursor != "origin") {
        require(ancestors.size < 8) { "reviewed physical ancestry exceeds eight sealed epochs" }
        val next = requireNotNull(producers[cursor]) { "physical tool input lacks a direct sealed ancestor: $cursor" }
        require(next.statePolicy == "carry-forward" && next.outputCheckpoint == cursor &&
            next.caseIds.isNotEmpty() && next.caseIds.none { it == plan.caseId } &&
            ancestors.none { it.id == next.id }) { "physical ancestry contains a sibling/cycle/isolated producer" }
        ancestors += next
        cursor = next.inputCheckpoint
    }
    require(ancestors.isNotEmpty() && ancestors.last().caseIds == listOf("world.onboarding")) {
        "physical prepared tool must descend from first real onboarding, not an unobserved sibling"
    }
    val physicalParams = manifest.path("driver_bindings").single {
        it.path("case_id").asText() == plan.caseId }.path("parameters")
    val acquiredIn = physicalParams.path("tool").path("acquisition_case_id").asText("")
    require(acquiredIn.isNotBlank() && ancestors.any { acquiredIn in it.caseIds }) {
        "reviewed tool acquisition must actually be an ancestor branch"
    }
    return ancestors.asReversed().map { it.id }
}
