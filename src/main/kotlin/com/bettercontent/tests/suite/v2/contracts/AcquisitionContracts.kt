package com.bettercontent.tests.suite.v2.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

private val mapper = jacksonObjectMapper()
private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
private val digest = Regex("[a-f0-9]{64}")
private val kinds = setOf("crafted", "worldgen", "harvest", "loot", "trade", "fluid", "intentional-unobtainable")

/** This authoring gate never asserts that an item was actually acquired in a running world. */
fun validateV2Draft(root: Path, manifest: JsonNode): List<String> {
    val errors = mutableListOf<String>()
    fun fault(where: String, ok: Boolean) { if (!ok) errors += where }
    fault("schema", manifest.path("schema").asText() == "bc.playtest_manifest.v2")
    fault("authoring.state", manifest.path("authoring").path("state").asText() in setOf("draft", "reviewed"))
    val candidate = manifest.path("candidate")
    for (side in listOf("client", "server")) {
        val ref = candidate.path(side)
        validateBound(root, ref, "candidate.$side", errors)
    }
    val locks = manifest.path("artifact_locks")
    fault("artifact_locks malformed", locks.isArray)
    for (key in listOf("discovery")) if (manifest.has(key)) validateBound(root, manifest.path(key), key, errors)
    val protocol = manifest.path("protocol")
    if (!protocol.isMissingNode) {
        fault("protocol.schema", protocol.path("schema").asText() == "bc.playtest_action.v2")
        validateBound(root, protocol.path("support_capabilities"), "protocol.support_capabilities", errors)
    }
    val bindings = manifest.path("driver_bindings")
    if (!bindings.isMissingNode) fault("driver_bindings", bindings.isArray && bindings.all { it.path("case_id").asText().isNotBlank() &&
        it.path("driver_id").asText().isNotBlank() && it.path("parameters").isObject &&
        it.path("required_actions").isArray && it.path("required_actions").all { action -> action.isTextual && action.asText().isNotBlank() } })
    if (locks.isArray) {
        val paths = mutableSetOf<String>()
        locks.forEachIndexed { i, ref ->
            val path = ref.path("path").asText()
            fault("artifact_locks[$i]", validEntry(path) && digest.matches(ref.path("sha256").asText()) &&
                ref.path("sides").isArray && ref.path("sides").size() > 0 && ref.path("sides").all { it.asText() in setOf("client", "server") })
            fault("duplicate lock $path", paths.add(path))
        }
    }
    val acquisitions = manifest.path("acquisitions")
    fault("acquisitions malformed", acquisitions.isArray)
    if (!acquisitions.isArray) return errors
    val byId = mutableMapOf<String, JsonNode>()
    acquisitions.forEach { a ->
        val id = a.path("id").asText()
        fault("invalid acquisition $id", resource.matches(id) && !byId.containsKey(id))
        byId[id] = a
    }
    fun deps(values: JsonNode, location: String): List<String> {
        if (!values.isArray || values.any { !it.isTextual || !resource.matches(it.asText()) }) {
            errors += "$location must be an array of resource identifiers"; return emptyList()
        }
        val ids = values.map { it.asText() }
        fault("duplicate dependency $location", ids.distinct().size == ids.size)
        ids.forEach { fault("unknown dependency $location/$it", it in byId) }
        return ids
    }
    acquisitions.forEach { a ->
        val id = a.path("id").asText(); val stage = a.path("stage")
        fault("invalid kind/stage $id", a.path("kind").asText() in kinds && stage.isIntegralNumber && stage.asInt() >= 0)
        val coverage = a.path("coverage")
        fault("acquisition proof malformed $id", coverage.isArray && coverage.all { it.isTextual && it.asText().isNotBlank() })
        val base = deps(a.path("prerequisites"), "$id.prerequisites")
        base.forEach { dep -> fault("future or excluded dependency $id/$dep", byId[dep]?.let { it.path("stage").asInt(Int.MAX_VALUE) <= stage.asInt() && it.path("kind").asText() != "intentional-unobtainable" } == true) }
        val routes = a.path("routes")
        fault("routes missing $id", routes.isArray && (a.path("kind").asText() != "crafted" || routes.size() > 0))
        if (routes.isArray) {
            val routeIds = mutableSetOf<String>()
            routes.forEach { route ->
                val routeId = route.path("id").asText(); val routeStage = route.path("stage")
                fault("invalid route $id/$routeId", routeId.isNotBlank() && routeIds.add(routeId) &&
                    route.path("mechanism").asText().isNotBlank() && route.path("execution_case").asText().isNotBlank() &&
                    routeStage.isIntegralNumber && routeStage.asInt() >= stage.asInt())
                deps(route.path("prerequisites"), "$id/$routeId.prerequisites").forEach { dep ->
                    fault("future/excluded route prerequisite $id/$routeId/$dep", byId[dep]?.let {
                        it.path("stage").asInt(Int.MAX_VALUE) <= routeStage.asInt() && it.path("kind").asText() != "intentional-unobtainable"
                    } == true)
                }
                val groups = route.path("ingredients")
                fault("invalid ingredient groups $id/$routeId", groups.isArray && groups.all { group ->
                    group.isArray && group.size() > 0 && group.all { it.isTextual && resource.matches(it.asText()) && it.asText() in byId }
                })
                if (groups.isArray) groups.forEach { group -> if (group.isArray) group.forEach { option ->
                    val dep = byId[option.asText()]
                    fault("future/excluded ingredient $id/$routeId/${option.asText()}", dep != null &&
                        dep.path("stage").asInt(Int.MAX_VALUE) <= routeStage.asInt() && dep.path("kind").asText() != "intentional-unobtainable")
                } }
            }
        }
        if (a.path("kind").asText() == "intentional-unobtainable") {
            fault("excluded acquisition has routes $id", routes.isArray && routes.isEmpty)
            val exclusion = a.path("exclusion"); val review = exclusion.path("review"); val evidence = exclusion.path("evidence")
            fault("exclusion reason/state missing $id", exclusion.path("reason").asText().isNotBlank() &&
                review.path("state").asText() in setOf("draft", "reviewed"))
            if (review.path("state").asText() == "reviewed") fault("exclusion review incomplete $id",
                review.path("reviewer").asText().isNotBlank() &&
                runCatching { !Instant.parse(review.path("reviewed_at").asText()).isAfter(Instant.now()) }.getOrDefault(false))
            val path = evidence.path("path").asText(); val sha = evidence.path("sha256").asText()
            val file = safeRelative(root, path)
            if (review.path("state").asText() == "reviewed" || path.isNotBlank() || sha.isNotBlank())
                fault("exclusion evidence unbound $id", file != null && digest.matches(sha) &&
                    Files.isRegularFile(file) && runCatching { sha256(file!!) == sha }.getOrDefault(false))
        }
    }
    if (manifest.path("authoring").path("state").asText() == "draft") return errors
    val stages = acquisitions.map { it.path("stage").asInt() }.distinct()
    for (stage in stages) {
        val reachable = mutableSetOf<String>()
        do {
            var changed = false
            acquisitions.forEach { a ->
                val id = a.path("id").asText()
                if (id in reachable || a.path("kind").asText() == "intentional-unobtainable" || a.path("stage").asInt(Int.MAX_VALUE) > stage) return@forEach
                if (!a.path("prerequisites").all { it.asText() in reachable }) return@forEach
                val routes = a.path("routes")
                if (routes.isArray && routes.size() > 0 && routes.none { route ->
                        route.path("stage").asInt(Int.MAX_VALUE) <= stage && route.path("prerequisites").all { it.asText() in reachable } &&
                            route.path("ingredients").all { group -> group.any { it.asText() in reachable } }
                    }) return@forEach
                reachable += id; changed = true
            }
        } while (changed)
        acquisitions.filter { it.path("stage").asInt(Int.MAX_VALUE) == stage && it.path("kind").asText() != "intentional-unobtainable" }
            .forEach { fault("unreachable at stage $stage: ${it.path("id").asText()}", it.path("id").asText() in reachable) }
    }
    return errors
}

fun validateV2Reviewed(root: Path, manifest: JsonNode): List<String> {
    val errors = validateV2Draft(root, manifest).toMutableList()
    if (manifest.path("authoring").path("state").asText() != "reviewed") errors += "authoring review required"
    val review = manifest.path("authoring")
    if (review.path("reviewer").asText().isBlank() || review.path("blockers").size() != 0 ||
        runCatching { Instant.parse(review.path("reviewed_at").asText()).isAfter(Instant.now()) }.getOrDefault(true))
        errors += "authoring reviewer/time/blockers incomplete"
    if (manifest.path("conversion_pending").asBoolean(false)) errors += "v1 conversion pending explicit v2 authoring"
    if (manifest.path("acquisitions").size() == 0) errors += "reviewed acquisition ledger missing"
    if (manifest.path("artifact_locks").size() == 0) errors += "artifact locks missing"
    val declaredLocks = manifest.path("artifact_locks")
    manifest.path("repositories").filter { it.path("id").asText() != "better-content-modpack" }.forEach { repo ->
        val artifact = repo.path("artifact").asText()
        val sha = repo.path("artifact_sha256").asText()
        val expectedSides = repo.path("sides").takeIf { it.isArray }?.map { it.asText() }?.toSet().orEmpty()
        val lockedSides = declaredLocks.filter { lock ->
            lock.path("path").asText().substringAfterLast('/') == artifact && lock.path("sha256").asText() == sha
        }.flatMap { lock -> lock.path("sides").map { it.asText() } }.toSet()
        if (!digest.matches(sha) || expectedSides.isEmpty() || expectedSides.any { it !in setOf("client", "server") } ||
            lockedSides != expectedSides)
            errors += "repository artifact absent from explicitly scoped immutable locks: ${repo.path("id").asText()}"
    }
    val candidate = manifest.path("candidate")
    val client = safeRelative(root, candidate.path("client").path("path").asText())
    val server = safeRelative(root, candidate.path("server").path("path").asText())
    if (client != null && server != null && manifest.path("artifact_locks").size() > 0) {
        runCatching {
            val actual = inspectCandidate(client, server)
            for (side in listOf("client", "server")) {
                if (actual.path("candidates").path("${side}_sha256").asText() != candidate.path(side).path("sha256").asText())
                    errors += "candidate changed during offline inventory: $side"
            }
            val members = actual.path("members")
            manifest.path("artifact_locks").forEach { lock -> lock.path("sides").forEach { side ->
                val found = members.filter { it.path("side").asText() == side.asText() && it.path("path").asText() == lock.path("path").asText() }
                if (found.size != 1 || found[0].path("sha256").asText() != lock.path("sha256").asText())
                    errors += "artifact lock differs from candidate bytes: ${side.asText()}/${lock.path("path").asText()}"
            } }
        }.onFailure { errors += "candidate offline inventory invalid: ${it.message}" }
    }
    if (manifest.path("discovery").isMissingNode) errors += "discovery binding missing"
    if (manifest.path("protocol").isMissingNode) errors += "protocol binding missing"
    if (!manifest.path("driver_bindings").isArray || manifest.path("driver_bindings").size() == 0) errors += "driver bindings missing"
    val cases = manifest.path("cases").takeIf { it.isArray }?.associateBy { it.path("id").asText() }.orEmpty()
    manifest.path("acquisitions").forEach { item ->
        val id = item.path("id").asText()
        if (item.path("conversion_pending").asBoolean(false)) errors += "legacy acquisition conversion pending: $id"
        if (item.path("coverage").size() == 0) errors += "acquisition proof missing: $id"
        item.path("coverage").forEach { case ->
            if (cases[case.asText()]?.path("targets")?.any { it.asText() == id } != true)
                errors += "acquisition proof lacks explicit target: $id/${case.asText()}"
        }
        item.path("routes").forEach { route ->
            if (route.path("execution_case").asText() !in item.path("coverage").map { it.asText() } ||
                cases[route.path("execution_case").asText()]?.path("layer")?.asText() !in setOf("native", "campaign"))
                errors += "route execution lacks native/campaign coverage: $id/${route.path("id").asText()}"
        }
        if (item.path("kind").asText() == "intentional-unobtainable" && item.path("exclusion").path("review").path("state").asText() != "reviewed")
            errors += "exclusion review missing: ${item.path("id").asText()}"
    }
    return errors
}

private fun validateBound(root: Path, ref: JsonNode, location: String, errors: MutableList<String>) {
    val path = safeRelative(root, ref.path("path").asText())
    if (path == null || !digest.matches(ref.path("sha256").asText()) ||
        !runCatching { sha256(path) == ref.path("sha256").asText() }.getOrDefault(false)) errors += "$location binding invalid"
}

private fun validEntry(path: String): Boolean = path.isNotBlank() && path.length <= 512 && !path.startsWith('/') &&
    !path.contains('\\') && path.split('/').all { it.isNotBlank() && it != "." && it != ".." && !it.contains('\u0000') }

private fun safeRelative(root: Path, name: String): Path? {
    if (name.isBlank() || name.length > 4096 || name.contains('\u0000') || name.contains('\\')) return null
    val supplied = runCatching { Path.of(name) }.getOrNull() ?: return null
    if (supplied.isAbsolute) {
        val normalized = supplied.normalize()
        if (normalized != supplied || !Files.isRegularFile(normalized)) return null
        return normalized
    }
    if (!validEntry(name)) return null
    val parent = root.toRealPath()
    val target = parent.resolve(name).normalize()
    if (!target.startsWith(parent) || !Files.isRegularFile(target) || !target.toRealPath().startsWith(parent)) return null
    return target
}

private fun sha256(path: Path): String {
    val md = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buf = ByteArray(65536)
        while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

/** Explicit lossless v1 snapshot; old routes are not silently reinterpreted as v2 ingredient evidence. */
fun convertV1Draft(input: JsonNode): JsonNode {
    require(input.path("schema").asText() == "bc.playtest_manifest.v1") { "only a v1 manifest may be converted" }
    val result = mapper.createObjectNode()
    result.put("schema", "bc.playtest_manifest.v2")
    result.set<JsonNode>("original_v1", input.deepCopy())
    val authoring = mapper.createObjectNode().put("state", "draft")
        .put("reviewer", "").put("reviewed_at", "")
    authoring.set<JsonNode>("blockers", mapper.valueToTree(listOf("v1 conversion requires explicit v2 acquisition review and candidate inventory")))
    result.set<JsonNode>("authoring", authoring)
    result.set<JsonNode>("candidate", input.path("candidate").deepCopy())
    for (field in listOf("revision", "profile", "test_support", "repositories", "rosters"))
        result.set<JsonNode>(field, input.path(field).deepCopy())
    val cases = input.path("cases").deepCopy<JsonNode>()
    if (cases.isArray) cases.forEach { c -> if (c is ObjectNode && c.has("state")) c.put("state", "not_run") }
    result.set<JsonNode>("cases", cases)
    result.set<JsonNode>("artifact_locks", mapper.createArrayNode())
    // V1 routes do not describe OR ingredient groups. Keep them in the immutable historical
    // snapshot, not in the active v2 acquisition ledger that discovery/qualification may consume.
    result.set<JsonNode>("acquisitions", mapper.createArrayNode())
    result.put("conversion_pending", true)
    return result
}
