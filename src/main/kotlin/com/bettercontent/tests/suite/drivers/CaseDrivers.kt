package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.SuiteCase
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.UUID

/** An action is a native request, never a direct gameplay mutation or an assertion verdict. */
internal data class DriverAction(val id: String, val parameters: ObjectNode, val timeoutTicks: Int = 200,
    val source: String = "server") {
    init { require(source in setOf("server", "client")) { "native action source must be server or client" } }
}
internal data class DriverStep(val name: String, val action: DriverAction)
internal data class DriverObservation(val step: String, val driver: String, val source: String,
    val kind: String, val payload: JsonNode,
    val rawEvidence: Map<String, com.bettercontent.tests.suite.VerifiedRawEvidence> = emptyMap())
internal interface CaseDriver {
    val caseId: String
    /** Scale cohorts are server-only; a real observer must not silently become a ninth participant. */
    val clientRequired: Boolean get() = true
    /** Exact independently measured assertions; never infer ownership from a broad case name. */
    val assertionIds: Set<String> get() = emptySet()
    /** Fixture-provisioned items may support behavior, never natural acquisition proof. */
    fun fixtureProvisionedTargets(parameters: JsonNode): Set<String> = emptySet()
    /** Per-target independently reviewed acquisition cases; never satisfied by fixture.begin. */
    fun requiredAcquisitionCases(parameters: JsonNode): Map<String, String> = emptyMap()
    /** Validate all fields before a world or fixture is created; reject unknown fields. */
    fun plan(parameters: JsonNode): List<DriverStep>
    /** Only native terminal observations, never native self-reported assertions. */
    fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict
}

/** No catch-and-continue for a failed action, malformed observation or cleanup failure. */
internal sealed interface DriverOutcome {
    data class Verdict(val value: MeasurementVerdict) : DriverOutcome
    data class Fatal(val reason: String) : DriverOutcome
}

internal object DriverRegistry {
    private val drivers: Map<String, CaseDriver> = listOf(PlacedFoodBiteDriver, PrimitiveBreakDriver, NativeDigControlDriver, SourceAttributedDigControlDriver,
        ItemConsumptionDriver, WorldTemperateProbeDriver, WaterSipControlDriver, PaidWaterTrialControlDriver,
        WorldEntryControlDriver, DeathLifecycleControlDriver, FirstJoinDriver, TemperateFirstJoinDriver,
        OutgoingCombatDriver, FullFoodDriver, Scale8Driver, Scale16Driver, Scale32Driver,
        com.bettercontent.tests.suite.client.EmiIndexDriver,
        com.bettercontent.tests.suite.client.InventorySyncDriver).associateBy { it.caseId }
    val supportedControls: Set<String> get() = drivers.keys.filterTo(mutableSetOf()) { it.startsWith("control.") }
    /** A control does not implement the catalogue's whole-family promise. */
    val supportedCases: Set<String> get() = drivers.values.filter { it.assertionIds.isNotEmpty() && !it.caseId.startsWith("control.") }.map { it.caseId }.toSet()
    fun resolve(id: String): CaseDriver? = drivers[id]
    fun requireSupported(ids: Collection<String>) {
        require(ids.distinct().size == ids.size && ids.all { it in supportedCases }) {
            "unsupported full catalogue case drivers: ${(ids - supportedCases).sorted().joinToString()}"
        }
    }
    /** Dependencies are prerequisites, not optional ordering hints; unsupported dependencies block preflight. */
    fun ordered(cases: List<SuiteCase>, requested: Set<String>): List<SuiteCase> {
        require(requested.isNotEmpty()) { "no requested cases" }
        val catalogue = cases.associateBy { it.id }
        require(catalogue.size == cases.size && requested.all { it in catalogue }) { "unknown or duplicate case" }
        val visiting = mutableSetOf<String>(); val ordered = mutableListOf<SuiteCase>(); val seen = mutableSetOf<String>()
        fun visit(id: String) {
            require(visiting.add(id)) { "case dependency cycle at $id" }
            val item = catalogue.getValue(id)
            require(id in supportedCases && item.assertions.toSet() == requireNotNull(resolve(id)).assertionIds) {
                "full catalogue case assertion contract unsupported: $id"
            }
            item.dependencies.sorted().forEach { dependency ->
                require(dependency in catalogue) { "missing case dependency: $dependency" }
                if (dependency !in seen) visit(dependency)
            }
            visiting.remove(id)
            if (seen.add(id)) ordered += item
        }
        requested.sorted().forEach { if (it !in seen) visit(it) }
        return ordered
    }
}

internal fun validateTerminalSequence(plan: List<DriverStep>, observations: List<DriverObservation>): String? {
    if (plan.map { it.name }.distinct().size != plan.size) return "duplicate planned step"
    if (observations.size != plan.size) return "missing/extra native terminal observation"
    for ((step, received) in plan.zip(observations)) {
        if (received.step != step.name || received.driver != step.action.id || received.source != step.action.source)
            return "step/driver/action source mismatch at ${step.name}"
        if (received.kind !in setOf("completed", "failed") || !received.payload.isObject)
            return "missing/malformed native terminal at ${step.name}"
        if (received.kind == "failed") return "native action failed at ${step.name}"
    }
    return null
}

internal fun evaluateCase(driver: CaseDriver, parameters: JsonNode, observations: List<DriverObservation>): DriverOutcome {
    val plan = try { driver.plan(parameters) } catch (failure: IllegalArgumentException) {
        return DriverOutcome.Fatal("invalid authored target parameters: ${failure.message}")
    }
    val problem = validateTerminalSequence(plan, observations)
    if (problem != null) return DriverOutcome.Fatal(problem)
    return try { DriverOutcome.Verdict(driver.evaluate(parameters, observations)) }
    catch (failure: RuntimeException) { DriverOutcome.Fatal("malformed independent oracle input: ${failure.message}") }
}

private data class BiteTarget(val player: String, val dimension: String, val x: Int, val y: Int, val z: Int,
    val face: String, val hand: String, val block: String, val biteProperty: String,
    val beforeBites: Int, val afterBites: Int, val beforeFood: Int, val afterFood: Int)
private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
private val faces = setOf("north", "south", "east", "west", "up", "down")
private fun exactObject(node: JsonNode, fields: Set<String>): JsonNode {
    require(node.isObject && node.fieldNames().asSequence().toSet() == fields) { "expected exactly ${fields.sorted()}" }
    return node
}
private fun text(node: JsonNode, key: String): String {
    val value = node.get(key); require(value != null && value.isTextual) { "$key must be text" }; return value.asText()
}
private fun number(node: JsonNode, key: String, range: IntRange): Int {
    val value = node.get(key); require(value != null && value.isIntegralNumber && value.canConvertToInt() && value.asInt() in range) { "$key out of range" }
    return value.asInt()
}
private fun parseBite(node: JsonNode): BiteTarget {
    exactObject(node, setOf("player_uuid", "dimension", "x", "y", "z", "face", "hand", "block_id", "bite_property",
        "before_bites", "after_bites", "before_food", "after_food"))
    val player = text(node, "player_uuid")
    require(runCatching { UUID.fromString(player).toString() }.getOrNull() == player) { "noncanonical player UUID" }
    val dimension = text(node, "dimension"); val block = text(node, "block_id")
    require(resource.matches(dimension) && resource.matches(block)) { "invalid resource ID" }
    val face = text(node, "face"); val hand = text(node, "hand")
    require(face in faces && hand in setOf("main_hand", "off_hand")) { "unsupported hand/face" }
    val property = text(node, "bite_property")
    require(Regex("[a-z0-9_]+").matches(property)) { "invalid block property" }
    val beforeBites = number(node, "before_bites", 1..64); val afterBites = number(node, "after_bites", 1..64)
    require(kotlin.math.abs(beforeBites - afterBites) == 1) { "bite expectation must change exactly one step" }
    val beforeFood = number(node, "before_food", 0..19); val afterFood = number(node, "after_food", 1..20)
    require(afterFood > beforeFood) { "food expectation must restore hunger" }
    return BiteTarget(player, dimension, number(node, "x", -30_000_000..30_000_000), number(node, "y", -2048..2048),
        number(node, "z", -30_000_000..30_000_000), face, hand, block, property, beforeBites, afterBites, beforeFood, afterFood)
}
private fun params(vararg pairs: Pair<String, Any>): ObjectNode = suiteMapper.createObjectNode().apply {
    pairs.forEach { (key, value) -> when (value) { is String -> put(key, value); is Int -> put(key, value); else -> error("unsupported parameter") } }
}

/** A real placed food use. Its source acquisition remains an independent campaign prerequisite. */
internal object PlacedFoodBiteDriver : CaseDriver {
    override val caseId = "control.food.placed-bite"
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val p = parseBite(parameters)
        val actor = params("player_uuid" to p.player)
        return listOf(
            DriverStep("before", DriverAction("player.snapshot", actor.deepCopy())),
            DriverStep("bite", DriverAction("player.interact", params("player_uuid" to p.player, "hand" to p.hand).apply {
                set<ObjectNode>("target", params("type" to "block", "dimension" to p.dimension,
                    "x" to p.x, "y" to p.y, "z" to p.z, "face" to p.face))
            })),
            DriverStep("after", DriverAction("player.snapshot", actor.deepCopy()))
        )
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val p = parseBite(parameters)
        require(observations.size == 3) { "three ordered terminal observations required" }
        val before = observations[0].payload; val action = observations[1].payload; val after = observations[2].payload
        val beforeState = action.path("before_block_state"); val afterState = action.path("after_block_state")
        require(beforeState.isObject && afterState.isObject && beforeState.path("properties").isObject && afterState.path("properties").isObject) {
            "native block states absent"
        }
        require(listOf(before, after).all { it.path("uuid").isTextual && it.path("food_level").isIntegralNumber && it.path("dimension").isTextual }) {
            "player identity/intake observation absent"
        }
        require(action.path("uuid").isTextual && action.path("interaction_result").isTextual &&
            action.path("food_before").isIntegralNumber && action.path("food_after").isIntegralNumber &&
            action.path("x").isIntegralNumber && action.path("y").isIntegralNumber && action.path("z").isIntegralNumber &&
            action.path("face").isTextual && action.path("hand").isTextual) { "interaction observation absent" }
        val stateBefore = beforeState.path("properties").path(p.biteProperty)
        val stateAfter = afterState.path("properties").path(p.biteProperty)
        require(stateBefore.isTextual && stateAfter.isTextual) { "named bite property missing" }
        val identity = listOf(before.path("uuid").asText(), action.path("uuid").asText(), after.path("uuid").asText()).all { it == p.player } &&
            listOf(before.path("dimension").asText(), after.path("dimension").asText(), beforeState.path("id").asText(), afterState.path("id").asText()).let {
                it[0] == p.dimension && it[1] == p.dimension && it[2] == p.block && it[3] == p.block
            }
        val observed = mapOf("before_food" to before.path("food_level").asInt(), "after_food" to after.path("food_level").asInt(),
            "before_bites" to stateBefore.asText(), "after_bites" to stateAfter.asText())
        val changed = identity && action.path("x").asInt() == p.x && action.path("y").asInt() == p.y &&
            action.path("z").asInt() == p.z && action.path("face").asText() == p.face && action.path("hand").asText() == p.hand &&
            before.path("food_level").asInt() == p.beforeFood && after.path("food_level").asInt() == p.afterFood &&
            stateBefore.asText() == p.beforeBites.toString() && stateAfter.asText() == p.afterBites.toString() &&
            action.path("food_before").asInt(-1) == p.beforeFood && action.path("food_after").asInt(-1) == p.afterFood &&
            action.path("interaction_result").asText() in setOf("CONSUME", "SUCCESS")
        return MeasurementVerdict(if (changed) "passed" else "failed", "independent native block bite and real player hunger delta", observed)
    }
}
