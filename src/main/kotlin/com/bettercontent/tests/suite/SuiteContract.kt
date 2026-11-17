package com.bettercontent.tests.suite

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** No command in this package builds mods, packages candidates, or grants permission to run tests. */
internal val suiteMapper = jacksonObjectMapper().apply {
    propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
    enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
    enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
    enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
    disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
    disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
}
internal val shaPattern = Regex("[0-9a-f]{64}")
internal val resourcePattern = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
internal val idPattern = Regex("[a-z0-9][a-z0-9_.-]*")

internal data class BoundFile(val path: String, val sha256: String)
internal data class CandidateBinding(val client: BoundFile, val server: BoundFile,
    val provenance: BoundFile, val inventory: BoundFile)
internal data class AuthoringReview(val state: String, val reviewer: String, val reviewedAt: String,
    val blockers: List<String>)
internal data class SuiteProfile(
    val seed: Long, val viewDistance: Int, val simulationDistance: Int,
    val cohorts: List<Int>, val cohortSeconds: Int, val recoverySeconds: Int,
    val recoveryStableSeconds: Int, val tpsWindowSeconds: Int, val minimumTps: Double,
    val treeMinimum: Int, val treeMinimumSites: Int, val treeMaximumSites: Int,
    val treeDynamicFraction: Double, val foodSampleSize: Int, val threatAttempts: Int,
    val visualProfiles: List<String>, val timingMode: String, val participantKind: String,
    val serverHeapMib: Int, val hardwareProfile: String
)
internal data class RepositorySpec(val id: String, val artifact: String, val artifactSha256: String,
    val sourceSha256: String, val testsSha256: String, val providersSha256: String,
    val toolchainSha256: String, val configurationSha256: String,
    val assessment: String, val state: String, val caseIds: List<String>)
internal data class RosterSpec(val kind: String, val complete: Boolean,
    val entries: Map<String, List<String>>, val gaps: List<String>)
internal data class CraftRoute(val id: String, val mechanism: String, val executionCase: String,
    val prerequisites: List<String>, val variants: List<String>, val variantCase: String, val stage: Int)
internal data class AcquisitionSpec(val id: String, val kind: String, val stage: Int,
    val coverage: List<String>, val prerequisites: List<String>, val routes: List<CraftRoute>,
    val exclusionReason: String = "")
internal data class CaseImplementation(val state: String, val path: String, val symbol: String)
internal data class SuiteCase(val id: String, val noteIds: List<String>, val owner: String,
    val layer: String, val context: String, val assertions: List<String>, val targets: List<String>,
    val dependencies: List<String>, val stage: Int, val timeoutSeconds: Long,
    val preconditions: List<String>, val actions: List<String>, val expected: List<String>,
    val cleanup: List<String>, val evidenceKinds: List<String>, val existingTests: List<String>,
    val implementation: CaseImplementation,
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY) val domain: String = "",
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY) val requiredCapabilities: List<String> = emptyList(),
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY) val requiredInventoryDomains: List<String> = emptyList(),
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY) val targetBindings: List<JsonNode> = emptyList())
internal data class SuiteManifest(val schema: String, val revision: String,
    val authoring: AuthoringReview, val candidate: CandidateBinding, val profile: SuiteProfile,
    val testSupport: BoundFile, val repositories: List<RepositorySpec>,
    val rosters: List<RosterSpec>, val acquisitions: List<AcquisitionSpec>, val cases: List<SuiteCase>)

internal val requiredNotes = setOf(
    "temperate-biome", "spawn-selection", "pillager-lethality", "emi", "knife-straw", "tools",
    "manual-drinking", "curio-drinking", "auto-drinking", "water-acquisition", "water-purity",
    "tcon-crafting", "freezing", "overheating", "hunger", "insulation", "depth-lethality",
    "workshop", "fonts", "ars-early", "ars-glyphs", "vs-assemblies", "create-contraptions",
    "spoilage", "death-respawn", "outgoing-damage", "incoming-damage", "food-sample",
    "dynamic-trees", "crafting-backbone", "tacz", "thirst", "scale-8", "scale-16", "scale-32",
    "gui-menus", "gui-in-game", "logs", "repo-promises", "repo-scale", "repo-ci",
    "smoke", "overworld-tele-soak", "tele-soak"
)
internal val requiredRosters = setOf("roots", "tools", "foods", "biomes", "depth-bands", "workshop",
    "glyphs", "fonts", "vs-mechanisms", "create-mechanisms", "tacz", "gui", "repositories")
internal val caseLayers = setOf("unit", "native", "campaign", "visual", "lifecycle", "logs")
internal val caseStates = setOf("passed", "failed", "blocked", "inconclusive", "not_run")

internal fun loadSuiteManifest(path: Path): SuiteManifest = suiteMapper.readValue(path.toFile(), SuiteManifest::class.java)

/** Returns all authoring defects, rather than silently filling missing contracts from current runtime. */
internal fun manifestProblems(m: SuiteManifest, root: Path, now: Instant = Instant.now()): List<String> {
    val errors = mutableListOf<String>()
    fun need(ok: Boolean, message: String) { if (!ok) errors += message }
    fun unique(values: List<String>, description: String) = need(values.distinct().size == values.size, "duplicate $description")
    need(m.schema == "bc.playtest_manifest.v1", "unsupported manifest schema")
    need(idPattern.matches(m.revision), "invalid revision")
    need(m.authoring.state == "reviewed", "authoring is not reviewed")
    need(m.authoring.reviewer.isNotBlank(), "authoring reviewer missing")
    val reviewed = runCatching { Instant.parse(m.authoring.reviewedAt) }.getOrNull()
    need(reviewed != null && !reviewed.isAfter(now), "invalid authoring review time")
    need(m.authoring.blockers.isEmpty(), "authoring blockers: ${m.authoring.blockers.joinToString("; ")}")
    listOf(m.candidate.client, m.candidate.server, m.candidate.provenance, m.candidate.inventory, m.testSupport).forEach {
        need(it.path.isNotBlank() && shaPattern.matches(it.sha256), "unbound input: ${it.path}")
    }
    val p = m.profile
    need(p.seed == 424242L, "sampling seed must be 424242")
    need(p.viewDistance == 10 && p.simulationDistance == 10, "production distances must be 10")
    need(p.cohorts == listOf(8, 16, 32), "cohorts must be 8,16,32")
    need(p.cohortSeconds == 600 && p.recoverySeconds == 120 && p.recoveryStableSeconds == 30 &&
        p.tpsWindowSeconds == 30 && p.minimumTps == 19.0, "scale timing/threshold contract changed")
    need(p.treeMinimum == 100 && p.treeMinimumSites == 5 && p.treeMaximumSites == 20 &&
        p.treeDynamicFraction == 0.95, "tree sampling contract changed")
    need(p.foodSampleSize == 10 && p.threatAttempts == 3, "food/threat sampling contract changed")
    need(p.visualProfiles == listOf("1280x720@2", "1920x1080@3"), "visual profiles missing")
    need(p.timingMode == "native-windows-and-boundaries" && p.participantKind == "server-fake-player", "unsupported timing/participant profile")
    need(p.serverHeapMib > 0 && p.hardwareProfile.isNotBlank(), "reference resource profile is not authored")
    unique(m.cases.map { it.id }, "case ID"); unique(m.repositories.map { it.id }, "repository ID")
    unique(m.acquisitions.map { it.id }, "acquisition ID"); unique(m.rosters.map { it.kind }, "roster kind")
    need(m.cases.isNotEmpty(), "empty case catalogue")
    need(m.acquisitions.isNotEmpty(), "empty acquisition ledger")
    need(m.repositories.any { it.id == "better-content-modpack" }, "pack repository missing")
    val cases = m.cases.associateBy { it.id }
    val acquisitions = m.acquisitions.associateBy { it.id }
    val repositories = m.repositories.associateBy { it.id }
    val notes = m.cases.flatMap { it.noteIds }.toSet()
    need(notes.containsAll(requiredNotes), "uncovered notes: ${(requiredNotes - notes).sorted()}")
    need(m.rosters.map { it.kind }.toSet() == requiredRosters, "required explicit rosters missing or unknown")
    m.rosters.forEach { r ->
        need(r.complete && r.gaps.isEmpty() && r.entries.isNotEmpty(), "incomplete ${r.kind} roster")
        r.entries.forEach { (entry, ids) ->
            need(entry.isNotBlank() && ids.isNotEmpty() && ids.all { it in cases }, "uncovered ${r.kind} entry $entry")
            if (r.kind in setOf("roots", "tools", "foods", "workshop", "glyphs", "fonts", "tacz"))
                need(entry in acquisitions && ids.any { entry in (cases[it]?.targets ?: emptyList()) }, "roster acquisition missing: ${r.kind}/$entry")
        }
        if (r.kind == "repositories") need(r.entries.keys == repositories.keys, "repository roster differs from scope")
    }
    m.repositories.forEach { r ->
        need(idPattern.matches(r.id) && r.state == "authored", "repository ${r.id} assessment incomplete")
        need(r.assessment.isNotBlank() && Files.isRegularFile(resolveSuitePath(root, r.assessment)), "repository ${r.id} assessment missing")
        val fingerprints = listOf(r.sourceSha256, r.testsSha256, r.providersSha256, r.toolchainSha256, r.configurationSha256)
        need(fingerprints.all { shaPattern.matches(it) }, "repository ${r.id} qualification fingerprints missing")
        if (r.id != "better-content-modpack") need(r.artifact.isNotBlank() && shaPattern.matches(r.artifactSha256), "repository ${r.id} artifact unbound")
        need(r.caseIds.isNotEmpty() && r.caseIds.all { cases[it]?.owner == r.id }, "repository ${r.id} promise coverage missing")
    }
    val owners = mutableSetOf<Triple<String, String, String>>()
    m.cases.forEach { c ->
        need(idPattern.matches(c.id), "invalid case ID ${c.id}")
        need(c.owner in repositories && c.layer in caseLayers && c.context.isNotBlank(), "invalid owner/layer/context for ${c.id}")
        need(c.noteIds.isNotEmpty() && c.noteIds.all { it in requiredNotes }, "invalid note mapping for ${c.id}")
        unique(c.assertions, "assertion in ${c.id}"); unique(c.targets, "target in ${c.id}")
        need(c.assertions.isNotEmpty(), "no assertions in ${c.id}")
        c.assertions.forEach { need(owners.add(Triple(it, c.layer, c.context)), "duplicate assertion ownership: $it/${c.layer}/${c.context}") }
        need(c.targets.all { it in acquisitions }, "unmapped acquisition target in ${c.id}")
        c.targets.forEach { target -> acquisitions[target]?.let { need(it.stage <= c.stage, "case ${c.id} uses later-stage target $target") } }
        need(c.dependencies.all { it in cases && it != c.id }, "unknown/self dependency in ${c.id}")
        need(c.stage >= 0 && c.timeoutSeconds > 0, "unbounded/invalid case ${c.id}")
        need(listOf(c.preconditions, c.actions, c.expected, c.cleanup, c.evidenceKinds).all { it.isNotEmpty() && it.all(String::isNotBlank) }, "incomplete procedure for ${c.id}")
        need(c.implementation.state == "authored" && c.implementation.symbol.isNotBlank() &&
            c.implementation.path.isNotBlank() && Files.isRegularFile(resolveSuitePath(root, c.implementation.path)), "missing implementation for ${c.id}")
        // Resource IDs in the written procedure cannot evade the acquisition ledger.
        val mentioned = (c.preconditions + c.actions + c.expected).flatMap { resourcePattern.findAll(it).map { match -> match.value }.toList() }
        need(mentioned.all { it in c.targets }, "undeclared resource identifier in procedure ${c.id}: ${mentioned.filter { it !in c.targets }}")
    }
    m.acquisitions.forEach { a ->
        need(resourcePattern.matches(a.id), "invalid acquisition ID ${a.id}")
        need(a.kind in setOf("crafted", "worldgen", "harvest", "loot", "trade", "fluid", "intentional-unobtainable"), "unclassified acquisition ${a.id}")
        need(a.stage >= 0 && a.coverage.isNotEmpty() && a.coverage.all { it in cases }, "missing acquisition proof for ${a.id}")
        need(a.coverage.all { a.id in (cases[it]?.targets ?: emptyList()) }, "acquisition proof does not declare ${a.id}")
        if (a.kind == "intentional-unobtainable") need(a.exclusionReason.isNotBlank(), "undocumented exclusion ${a.id}")
        need((a.kind == "crafted") == a.routes.isNotEmpty(), "craft route classification mismatch ${a.id}")
        unique(a.routes.map { it.id }, "route in ${a.id}")
        a.routes.forEach { route ->
            need(route.id.isNotBlank() && route.mechanism.isNotBlank() && route.executionCase in a.coverage,
                "missing actual route execution for ${a.id}/${route.id}")
            need(cases[route.executionCase]?.layer in setOf("native", "campaign"), "route ${a.id}/${route.id} lacks native execution")
            need(route.stage >= a.stage && cases[route.executionCase]?.stage == route.stage, "invalid route stage ${a.id}/${route.id}")
            need(route.variants.isEmpty() || route.variantCase in a.coverage, "uncovered recipe variants ${a.id}/${route.id}")
            route.prerequisites.forEach { dep ->
                need(dep in acquisitions, "unknown acquisition dependency $dep for ${a.id}/${route.id}")
                acquisitions[dep]?.let { need(it.stage <= route.stage && it.kind != "intentional-unobtainable", "premature/unobtainable route dependency $dep for ${a.id}/${route.id}") }
            }
        }
        a.prerequisites.forEach { dep ->
            need(dep in acquisitions, "unknown acquisition dependency $dep for ${a.id}")
            acquisitions[dep]?.let { need(it.stage <= a.stage && it.kind != "intentional-unobtainable", "premature/unobtainable dependency $dep for ${a.id}") }
        }
    }
    errors += dependencyCycles(cases.mapValues { it.value.dependencies }).map { "case dependency cycle: $it" }
    // Routes are alternatives, not an AND-union. Recycling loops are valid once another route bootstraps them.
    val reachableByStage = m.acquisitions.map { it.stage }.distinct().associateWith { reachableAcquisitions(m.acquisitions, it) }
    m.acquisitions.filter { it.kind != "intentional-unobtainable" }.forEach {
        need(it.id in reachableByStage.getValue(it.stage), "unbootstrappable acquisition at stage ${it.stage}: ${it.id}")
    }
    return errors
}

internal fun reachableAcquisitions(acquisitions: List<AcquisitionSpec>, stage: Int): Set<String> {
    val reachable = mutableSetOf<String>()
    do {
        var changed = false
        acquisitions.filter { it.stage <= stage && it.kind != "intentional-unobtainable" }.forEach { a ->
            if (a.id !in reachable && a.prerequisites.all { it in reachable } &&
                (a.kind != "crafted" || a.routes.any { route -> route.stage <= stage && route.prerequisites.all { it in reachable } })) {
                reachable += a.id; changed = true
            }
        }
    } while (changed)
    return reachable
}

internal fun dependencyCycles(graph: Map<String, List<String>>): List<String> {
    val done = mutableSetOf<String>(); val visiting = linkedSetOf<String>(); val cycles = mutableListOf<String>()
    fun visit(id: String) {
        if (id in visiting) { cycles += (visiting.toList().dropWhile { it != id } + id).joinToString(" -> "); return }
        if (id in done || id !in graph) return
        visiting += id; graph.getValue(id).forEach(::visit); visiting -= id; done += id
    }
    graph.keys.sorted().forEach(::visit)
    return cycles
}

internal fun resolveSuitePath(base: Path, value: String): Path = base.resolve(value).toAbsolutePath().normalize()
internal fun readBoundFile(base: Path, ref: BoundFile): Path {
    require(ref.path.isNotBlank() && shaPattern.matches(ref.sha256)) { "invalid file binding" }
    val path = resolveSuitePath(base, ref.path)
    require(Files.isRegularFile(path)) { "missing bound file: $path" }
    require(com.bettercontent.tests.Hashes.sha256(path) == ref.sha256) { "bound file changed: $path" }
    return path
}
internal fun manifestFingerprint(path: Path): String = com.bettercontent.tests.Hashes.sha256(path)
internal fun profileFingerprint(profile: SuiteProfile): String {
    val bytes = suiteMapper.writeValueAsBytes(profile)
    return java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
