package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.SuiteCase
import com.bettercontent.tests.suite.SuiteProfile
import com.bettercontent.tests.suite.v2.contracts.inspectCandidate
import com.bettercontent.tests.suite.boundInput
import com.bettercontent.tests.suite.reviewedV2Problems
import com.bettercontent.tests.suite.v2.contracts.validateV2Draft
import com.bettercontent.tests.suite.boundPair
import com.bettercontent.tests.suite.supportCapabilities
import com.bettercontent.tests.suite.repositories.validateAuthoringCatalogue
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

private val branchId = Regex("[a-z0-9][a-z0-9_.-]*")
private val sha = Regex("[0-9a-f]{64}")

/** V3 is explicitly authored; no v1/v2 execution receipt is ever converted here. */
internal fun validateV3Authoring(root: Path, m: JsonNode, purpose: ExecutionPurpose = ExecutionPurpose.QUALIFICATION,
    selectedCaseIds: Set<String> = emptySet()): List<String> {
    val errors = mutableListOf<String>()
    fun need(ok: Boolean, message: String) { if (!ok) errors += message }
    need(m.path("schema").asText() == "bc.playtest_manifest.v3", "unsupported v3 manifest")
    need(m.path("purpose").isTextual && m.path("purpose").asText() == purpose.wire, "explicit v3 purpose mismatch")
    if (m.path("schema").asText() != "bc.playtest_manifest.v3") return errors
    // Qualification retains the full v2 reviewed/discovery gate. Diagnostic only skips unrelated completeness;
    // immutable candidate/artifact identity and the whole authored DAG are still checked below.
    val authoringProjection = m.deepCopy<ObjectNode>().put("schema", "bc.playtest_manifest.v2")
    authoringProjection.remove("branches")
    if (purpose == ExecutionPurpose.QUALIFICATION) errors += reviewedV2Problems(root, authoringProjection)
    else {
        val projection = authoringProjection.deepCopy()
        projection.putArray("acquisitions") // incomplete unrelated routes cannot become selected proof
        errors += validateV2Draft(root, projection)
        val profile = runCatching { suiteMapper.treeToValue(m.path("profile"), SuiteProfile::class.java) }.getOrNull()
        need(profile != null && profile.seed == 424242L && profile.viewDistance == 10 &&
            profile.simulationDistance == 10 && profile.cohorts == listOf(8, 16, 32) &&
            profile.cohortSeconds == 600 && profile.recoverySeconds == 120 && profile.recoveryStableSeconds == 30 &&
            profile.tpsWindowSeconds == 30 && profile.minimumTps == 19.0 && profile.treeMinimum == 100 &&
            profile.treeMinimumSites == 5 && profile.treeMaximumSites == 20 && profile.treeDynamicFraction == 0.95 &&
            profile.foodSampleSize == 10 && profile.threatAttempts == 3 &&
            profile.visualProfiles == listOf("1280x720@2", "1920x1080@3") && profile.serverHeapMib == 16384 &&
            profile.hardwareProfile.isNotBlank() && profile.participantKind == "server-fake-player" &&
            profile.timingMode == "native-windows-and-boundaries", "fixed diagnostic profile absent/changed")
        need(selectedCaseIds.isNotEmpty(), "diagnostic needs explicit scoped cases")
        val cases = m.path("cases")
        need(cases.isArray && cases.map { it.path("id").asText() }.distinct().size == cases.size(), "duplicate/malformed case identities")
        val bindings = m.path("driver_bindings")
        need(bindings.isArray && bindings.map { it.path("case_id").asText() }.distinct().size == bindings.size(), "duplicate/malformed driver bindings")
        selectedCaseIds.forEach { id ->
            val c = cases.singleOrNull { it.path("id").asText() == id }
            val b = bindings.singleOrNull { it.path("case_id").asText() == id }
            need(c != null && c.path("implementation").path("state").asText() == "authored" &&
                runCatching { Files.isRegularFile(root.resolve(c.path("implementation").path("path").asText()).normalize()) &&
                    root.resolve(c.path("implementation").path("path").asText()).normalize().startsWith(root.normalize()) }.getOrDefault(false),
                "diagnostic case not authored or safely source-bound: $id")
            need(b != null && b.path("driver_id").asText() == id && b.path("required_actions").isArray &&
                !b.path("required_actions").isEmpty, "diagnostic driver/actions missing: $id")
            val domains = c?.path("required_inventory_domains")
            need(domains != null && domains.isArray && !domains.isEmpty &&
                domains.all { it.isTextual && branchId.matches(it.asText()) } &&
                domains.map { it.asText() }.distinct().size == domains.size(), "diagnostic inventory domains not reviewed: $id")
        }
        runCatching {
            val candidate = m.path("candidate")
            val client = boundInput(root, candidate.path("client"))
            val server = boundInput(root, candidate.path("server"))
            boundPair(root, m, client, server)
            // Diagnostic authoring may study a real candidate despite unrelated malformed
            // third-party resources; the strict qualification inventory still rejects them.
            val inspected = inspectCandidate(client, server, diagnostic = true)
            need(!inspected.path("qualification_eligible").asBoolean(true), "diagnostic inventory must not qualify")
            need(listOf("client", "server").all { side -> inspected.path("candidates").path("${side}_sha256").asText() ==
                candidate.path(side).path("sha256").asText() }, "diagnostic archive identity differs")
            m.path("artifact_locks").forEach { lock -> lock.path("sides").forEach { side ->
                val members = inspected.path("members").filter { it.path("side").asText() == side.asText() &&
                    it.path("path").asText() == lock.path("path").asText() }
                need(members.size == 1 && members.singleOrNull()?.path("sha256")?.asText() == lock.path("sha256").asText(),
                    "diagnostic candidate artifact lock differs: ${side.asText()}/${lock.path("path").asText()}")
            } }
            val support = boundInput(root, m.path("test_support"))
            need(Files.isSameFile(support, boundInput(root, m.path("protocol").path("support_capabilities"))),
                "support capability descriptor differs from support artifact")
            val actions = supportCapabilities(support)
            val clientActions = supportCapabilities(support, "client")
            selectedCaseIds.forEach { id ->
                val b = bindings.single { it.path("case_id").asText() == id }
                need(b.path("required_actions").all { it.asText() in actions || it.asText() in clientActions },
                    "diagnostic capability unsupported: $id")
            }
        }.onFailure { errors += "diagnostic candidate/support binding: ${it.message}" }
    }
    val launcher = m.path("tooling").path("portablemc")
    need(launcher.isObject && launcher.path("path").asText().endsWith("portablemc-4.4.1-py3-none-any.whl") &&
        sha.matches(launcher.path("sha256").asText()), "pinned portablemc wheel binding missing")
    val sandbox = m.path("tooling").path("bubblewrap")
    need(sandbox.isObject && sandbox.path("path").asText().isNotBlank() &&
        sha.matches(sandbox.path("sha256").asText()) &&
        runCatching { Files.isExecutable(boundInput(root, sandbox)) }.getOrDefault(false),
        "hash-bound executable bubblewrap sandbox missing")
    val storage = m.path("storage").path("checkpoint_budget_bytes")
    need(storage.isIntegralNumber && storage.canConvertToLong() && storage.asLong() > 0L,
        "explicit positive checkpoint storage budget missing")
    val rows = m.path("branches")
    need(rows.isArray && !rows.isEmpty, "v3 branches missing")
    if (!rows.isArray) return errors
    val specs = rows.mapIndexedNotNull { index, row -> runCatching { suiteMapper.treeToValue(row, BranchSpec::class.java) }
        .onFailure { errors += "branch[$index] malformed: ${it.message}" }.getOrNull() }
    if (specs.size != rows.size()) return errors
    errors += branchProblems(root, specs, m.path("cases"))
    errors += validateAuthoringCatalogue(root, m,
        if (purpose == ExecutionPurpose.QUALIFICATION) m.path("cases").map { it.path("id").asText() }.toSet() else selectedCaseIds,
        purpose == ExecutionPurpose.QUALIFICATION)
    return errors
}

/** Deterministic, explicit topological order; ties keep authored order, not output directory order. */
internal fun orderedBranches(m: JsonNode): List<BranchSpec> {
    require(m.path("schema").asText() == "bc.playtest_manifest.v3" && m.path("branches").isArray) {
        "v3 manifest branches required"
    }
    val branches = m.path("branches").map { suiteMapper.treeToValue(it, BranchSpec::class.java) }
    val defects = branchProblems(null, branches, m.path("cases"))
    require(defects.isEmpty()) { defects.joinToString("; ") }
    val byId = branches.associateBy { it.id }
    val producers = branches.filter { it.outputCheckpoint != null }.associateBy { it.outputCheckpoint!! }
    val visiting = mutableSetOf<String>(); val visited = mutableSetOf<String>(); val sorted = mutableListOf<BranchSpec>()
    fun visit(spec: BranchSpec) {
        require(visiting.add(spec.id)) { "branch cycle: ${spec.id}" }
        val dependencies = spec.dependsOn + spec.orderingDependencies + listOfNotNull(producers[spec.inputCheckpoint]?.id)
        dependencies.distinct().forEach { dep -> if (dep !in visited) visit(requireNotNull(byId[dep])) }
        visiting.remove(spec.id)
        if (visited.add(spec.id)) sorted += spec
    }
    branches.forEach { if (it.id !in visited) visit(it) }
    return sorted
}

private fun branchProblems(root: Path?, branches: List<BranchSpec>, caseRows: JsonNode): List<String> {
    val problems = mutableListOf<String>()
    fun need(ok: Boolean, text: String) { if (!ok) problems += text }
    need(branches.isNotEmpty(), "no v3 branches")
    val byId = branches.associateBy { it.id }
    need(byId.size == branches.size && branches.all { branchId.matches(it.id) && it.id != "origin" }, "duplicate/invalid branch ID")
    val producer = branches.filter { it.outputCheckpoint != null }.associateBy { it.outputCheckpoint!! }
    need(producer.size == branches.count { it.outputCheckpoint != null } &&
        producer.keys.none { it == "origin" || !branchId.matches(it) }, "duplicate/invalid output checkpoint")
    val cases = runCatching { caseRows.map { suiteMapper.treeToValue(it, SuiteCase::class.java) }.associateBy { it.id } }.getOrElse {
        problems += "malformed authored cases: ${it.message}"; return problems
    }
    val all = branches.flatMap { it.caseIds }
    need(all.size == all.toSet().size, "duplicate case execution across branches")
    need(all.toSet() == cases.values.filter { it.layer == "campaign" }.map { it.id }.toSet(), "campaign case coverage incomplete or extra")
    // A real first login must start from the player-free origin. Food may inherit Survival only
    // through that *passed and sealed* onboarding branch, never via scheduling-only edges.
    val onboarding = branches.singleOrNull { "world.onboarding" in it.caseIds }
    if ("world.onboarding" in cases || "survival.food" in cases) {
        need(onboarding != null && onboarding.caseIds == listOf("world.onboarding") &&
            onboarding.inputCheckpoint == "origin" && onboarding.outputCheckpoint != null &&
            onboarding.statePolicy == "carry-forward" && cases["world.onboarding"]?.layer == "campaign",
            "world.onboarding must seal the first real origin-login Survival state")
    }
    if ("survival.food" in cases) {
        val food = branches.singleOrNull { "survival.food" in it.caseIds }
        need(food != null && onboarding != null &&
            food.inputCheckpoint == onboarding.outputCheckpoint && onboarding.id in food.dependsOn &&
            "world.onboarding" in cases.getValue("survival.food").dependencies,
            "food must restore the verified onboarding output and depend on its case, not ordering alone")
    }
    branches.forEach { b ->
        need(b.caseIds.isNotEmpty() && b.caseIds.size == b.caseIds.distinct().size && b.caseIds.all { cases[it]?.layer == "campaign" },
            "branch ${b.id} has empty/duplicate/non-campaign cases")
        need(b.dependsOn.size == b.dependsOn.distinct().size && b.dependsOn.all { it in byId && it != b.id },
            "branch ${b.id} has unknown/duplicate/self dependency")
        need(b.orderingDependencies.size == b.orderingDependencies.distinct().size &&
            b.orderingDependencies.all { it in byId && it != b.id && it !in b.dependsOn },
            "branch ${b.id} has unknown/duplicate/self/redundant ordering dependency")
        need(b.inputCheckpoint == "origin" || b.inputCheckpoint in producer, "branch ${b.id} lacks sealed input checkpoint")
        need(b.outputCheckpoint == null || b.outputCheckpoint != b.inputCheckpoint, "branch ${b.id} outputs its own input")
        need(b.statePolicy in setOf("isolated", "carry-forward"), "unsupported state policy ${b.id}")
        if (b.statePolicy == "isolated") need(b.caseIds.size == 1 && b.isolationContract == null,
            "isolated branch ${b.id} must contain exactly one case and no carry-forward waiver")
        if (b.statePolicy == "carry-forward" && b.caseIds.size > 1) {
            need(b.isolationContract != null, "multi-case branch ${b.id} needs reviewed isolation contract")
            if (root != null && b.isolationContract != null) validateIsolationContract(root, b, b.isolationContract, problems)
        }
    }
    // Ordering-only prerequisites affect the schedule, never checkpoint ancestry or acquisition proof.
    val active = mutableSetOf<String>(); val complete = mutableSetOf<String>()
    fun orderCycle(id: String): Boolean {
        if (id in active) return true
        if (id in complete) return false
        val branch = byId[id] ?: return false
        active += id
        val dependencies = branch.dependsOn + branch.orderingDependencies +
            listOfNotNull(producer[branch.inputCheckpoint]?.id)
        if (dependencies.any(::orderCycle)) return true
        active -= id; complete += id
        return false
    }
    need(branches.none { orderCycle(it.id) }, "branch ordering/dependency cycle")
    // Walk each checkpoint's unique parent chain; ordering-only dependencies do not create ancestry.
    fun ancestry(branch: BranchSpec): Set<String>? {
        val visited = mutableSetOf<String>(); val chain = mutableSetOf<String>()
        var checkpoint = branch.inputCheckpoint
        while (checkpoint != "origin") {
            val parent = producer[checkpoint] ?: return null
            if (!visited.add(parent.id)) return null
            chain += parent.id
            checkpoint = parent.inputCheckpoint
        }
        return chain
    }
    branches.forEach { b ->
        val ancestors = ancestry(b)
        need(ancestors != null, "branch checkpoint cycle: ${b.id}")
        if (ancestors == null) return@forEach
        need(b.dependsOn.all { it in ancestors }, "dependent branch ${b.id} references a sibling, not input ancestry")
        val inherited = ancestors.flatMap { byId.getValue(it).caseIds }.toSet()
        val earlier = mutableSetOf<String>()
        b.caseIds.forEach caseLoop@{ id ->
            val c = cases[id] ?: return@caseLoop
            c.dependencies.filter { cases[it]?.layer == "campaign" }.forEach { dependency ->
                need(dependency in inherited || (b.statePolicy == "carry-forward" && dependency in earlier),
                    "branch ${b.id}/$id has no ancestral campaign proof for $dependency")
            }
            earlier += id
        }
    }
    return problems
}

private fun validateIsolationContract(root: Path, branch: BranchSpec, binding: BoundFile, problems: MutableList<String>) {
    if (!sha.matches(binding.sha256)) { problems += "isolation contract SHA missing: ${branch.id}"; return }
    val file = runCatching { boundInput(root, suiteMapper.valueToTree(binding)) }.getOrElse {
        problems += "isolation contract unbound: ${branch.id}: ${it.message}"; return
    }
    val real = file.toRealPath(); val base = root.toRealPath()
    if (!Path.of(binding.path).isAbsolute && !real.startsWith(base)) { problems += "isolation contract escaped root: ${branch.id}"; return }
    val doc = runCatching { suiteMapper.readTree(file.toFile()) }.getOrElse {
        problems += "isolation contract malformed: ${branch.id}: ${it.message}"; return
    }
    val review = doc.path("review")
    val source = doc.path("source")
    val sourcePath = runCatching { boundInput(root, source) }.getOrNull()
    val time = runCatching { Instant.parse(review.path("reviewed_at").asText()) }.getOrNull()
    val required = listOf("effects", "isolation_requirements", "restoration_requirements")
    if (doc.path("schema").asText() != "bc.playtest_isolation_contract.v3" ||
        doc.path("branch_id").asText() != branch.id || doc.path("case_ids").map { it.asText() } != branch.caseIds ||
        review.path("state").asText() != "reviewed" || review.path("reviewer").asText().isBlank() ||
        time == null || time.isAfter(Instant.now()) || sourcePath == null || !Files.isRegularFile(sourcePath) ||
        required.any { !doc.path(it).isArray || doc.path(it).isEmpty || doc.path(it).any { row -> !row.isTextual || row.asText().isBlank() } }) {
        problems += "reviewed source-bound isolation/effects contract incomplete: ${branch.id}"
    }
}
